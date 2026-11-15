package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import kotlin.math.log10
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole mix's loudness: momentary, short-term, integrated, range and peak, as Fairlight's meter reads it. */
class ProgramLoudnessUseCaseTest {

    private val meter = ProgramLoudnessUseCase()

    /** [seconds] of sound at [lufs] with sample peaks of [peak]. */
    private fun steady(lufs: Double, seconds: Int = 30, peak: Float = 0.5f) =
        LoudnessProfile(100, FloatArray(seconds * 10) { 10.0.pow((lufs + 0.691) / 10).toFloat() }, FloatArray(seconds * 10) { peak })

    /** The song on A2 from 0 for [frames] frames (30 fps), with the mixer made by [mix]. */
    private fun song(frames: Int = 300, mix: (MixerState) -> MixerState = { it }): ProjectDocument {
        val timeline = timelineOf(clipOf("S", 0, frames, track = Timeline.A2, assetId = "song"))
        return projectOf(timeline = timeline, mixer = mix(MixerState.forTracks(timeline.tracks)))
    }

    private fun MixerState.on(trackId: String, change: (com.example.prismstudio.p40b43a88.domain.model.MixerChannel) -> com.example.prismstudio.p40b43a88.domain.model.MixerChannel) =
        copy(channels = channels.map { if (it.trackId == trackId) change(it) else it })

    @Test
    fun `a steady sound reads its level everywhere once the windows are full`() {
        val m = meter(song(), mapOf("song" to steady(-20.0)))
        assertEquals(-20.0, m.integratedLufs!!, 0.01)
        assertEquals(-20.0, m.momentaryAt(5_000.0)!!, 0.01)
        assertEquals(-20.0, m.shortTermAt(5_000.0)!!, 0.01)
        // Two seconds in, the 3 s window still holds a second of silence before the start.
        assertEquals(-20.0 + 10 * log10(20.0 / 30.0), m.shortTermAt(2_000.0)!!, 0.01)
        assertEquals("no range in a steady sound", 0.0, m.rangeLu!!, 0.01)
        assertEquals(20 * log10(0.5), m.truePeakDbtp!!, 0.01)
        assertNull("after the end, silence", m.momentaryAt(12_000.0))
        val history = m.history(5_000.0, 30)
        assertEquals(300, history.size)
        assertEquals(m.shortTermAt(5_000.0)!!, history.last().toDouble(), 0.01)
        assertTrue("before the start: nothing", history.first().isNaN())
    }

    @Test
    fun `the fader, pan and master move it, a muted track is silent`() {
        val profiles = mapOf("song" to steady(-20.0))
        assertEquals(-26.0, meter(song { it.on(Timeline.A2) { c -> c.copy(volumeDb = -6f) } }, profiles).integratedLufs!!, 0.01)
        assertEquals(-23.0, meter(song { it.copy(masterVolumeDb = -3f) }, profiles).integratedLufs!!, 0.01)
        // Panned hard to one side, half the stereo energy is left.
        assertEquals(-20.0 + 10 * log10(0.5), meter(song { it.on(Timeline.A2) { c -> c.copy(pan = 1f) } }, profiles).integratedLufs!!, 0.01)
        assertNull(meter(song { it.on(Timeline.A2) { c -> c.copy(mute = true) } }, profiles).integratedLufs)
        assertNull("no profile, no reading", meter(song(), emptyMap()).integratedLufs)
    }

    @Test
    fun `ducking dips it where its envelope says, dynamics at their steady level`() {
        val ducked = song { it.on(Timeline.A2) { c -> c.copy(ducking = TrackDucking(keyTrackId = Timeline.A1, depthDb = 12f)) } }
        // Fully down from 2 s to 4 s (10 ms steps).
        val envelope = DuckEnvelope(10_000, FloatArray(1_000) { i -> if (i in 200 until 400) 1f else 0f })
        val m = meter(ducked, mapOf("song" to steady(-20.0)), mapOf(Timeline.A2 to envelope))
        assertEquals(-32.0, m.momentaryAt(3_500.0)!!, 0.01)
        assertEquals(-20.0, m.momentaryAt(6_000.0)!!, 0.01)
        assertEquals("without the envelope it does not dip", -20.0, meter(ducked, mapOf("song" to steady(-20.0))).momentaryAt(3_500.0)!!, 0.01)

        val limiter = TrackDynamics(limiter = true, ceilingDb = -12f)
        val limited = song { it.on(Timeline.A2) { c -> c.copy(dynamics = limiter) } }
        // Peaks of -6 dBFS held to -12: the whole block comes down by that much.
        val change = limiter.gainChangeDb(20 * log10(0.5))
        assertEquals(-20.0 + change, meter(limited, mapOf("song" to steady(-20.0))).integratedLufs!!, 0.05)
        assertTrue(change < -5.0)
    }

    @Test
    fun `the loudness range spans the quiet and the loud parts`() {
        val quietThenLoud = LoudnessProfile(
            100,
            FloatArray(200) { i -> 10.0.pow(((if (i < 100) -26.0 else -20.0) + 0.691) / 10).toFloat() },
            FloatArray(200) { 0.5f },
        )
        val m = meter(song(600), mapOf("song" to quietThenLoud))
        assertEquals(6.0, m.rangeLu!!, 0.05)
        assertEquals("loud second half", -20.0, m.shortTermAt(19_000.0)!!, 0.01)
        assertEquals("quiet first half", -26.0, m.shortTermAt(9_000.0)!!, 0.01)
        assertNull("too short for a range", meter(song(60), mapOf("song" to steady(-20.0))).rangeLu)
    }

    @Test
    fun `the program reading for Normalize Audio includes the ducking it is given`() {
        val ducked = song { it.on(Timeline.A2) { c -> c.copy(ducking = TrackDucking(keyTrackId = Timeline.A1, depthDb = 12f)) } }
        val envelope = DuckEnvelope(10_000, FloatArray(1_000) { 1f })
        val levels = LoudnessUseCase()
        val plain = levels.program(ducked, mapOf("song" to steady(-20.0))) as LoudnessUseCase.Reading.Measured
        val dipped = levels.program(ducked, mapOf("song" to steady(-20.0)), envelopes = mapOf(Timeline.A2 to envelope)) as LoudnessUseCase.Reading.Measured
        assertEquals(-20.0, plain.lufs.toDouble(), 0.01)
        assertEquals(-32.0, dipped.lufs.toDouble(), 0.01)
    }
}
