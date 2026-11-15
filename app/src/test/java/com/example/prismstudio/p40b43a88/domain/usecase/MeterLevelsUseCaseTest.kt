package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterLevelsUseCaseTest {

    private val meter = MeterLevelsUseCase()
    private val floor = MeterLevelsUseCase.FLOOR_DB
    private val halfDb = -6.0206f

    /** Ten seconds of source audio at a constant peak. */
    private fun steady(peak: Float) = Waveform(20, FloatArray(500) { peak })

    private val waves = mapOf("full" to steady(1f), "half" to steady(0.5f))

    private fun docOf(vararg clips: Clip, mixer: MixerState? = null, disabled: Set<String> = emptySet()): ProjectDocument {
        val timeline = timelineOf(*clips, disabled = disabled)
        return projectOf(timeline = timeline, mixer = mixer ?: MixerState.forTracks(timeline.tracks))
    }

    private fun mixer(vararg channels: MixerChannel, masterDb: Float = 0f) = MixerState(channels.toList(), masterDb)

    private val fullOnA1 = clipOf("X", 0, 300, track = Timeline.A1, assetId = "full")
    private val fullOnA2 = clipOf("Y", 0, 300, track = Timeline.A2, assetId = "full")

    @Test
    fun `a full scale source reads 0 dB and tracks without sound read the floor`() {
        val levels = meter(docOf(fullOnA1, clipOf("V", 0, 300, assetId = "full")), 30, waves)

        assertEquals(setOf(Timeline.A1, Timeline.A2), levels.channels.keys)
        assertEquals(0f, levels.channels.getValue(Timeline.A1), 1e-4f)
        assertEquals(floor, levels.channels.getValue(Timeline.A2), 0f)
        assertEquals(0f, levels.master, 1e-4f)
    }

    @Test
    fun `half amplitude reads about minus six dB and the master sums the channels`() {
        val doc = docOf(fullOnA1.copy(assetId = "half"), fullOnA2.copy(assetId = "half"))

        val levels = meter(doc, 60, waves)

        assertEquals(halfDb, levels.channels.getValue(Timeline.A1), 1e-3f)
        assertEquals(halfDb, levels.channels.getValue(Timeline.A2), 1e-3f)
        assertEquals(0f, levels.master, 1e-3f)
    }

    @Test
    fun `clip gain fader and master volume all scale the reading`() {
        val doc = docOf(
            fullOnA1.copy(volumeDb = -6f),
            mixer = mixer(MixerChannel(Timeline.A1, volumeDb = -6f), MixerChannel(Timeline.A2), masterDb = 6f),
        )

        val levels = meter(doc, 30, waves)

        assertEquals(-12f, levels.channels.getValue(Timeline.A1), 0.01f)
        assertEquals(-6f, levels.master, 0.01f)
    }

    @Test
    fun `mute solo disabled tracks and floor volume silence a channel`() {
        val soloA1 = meter(docOf(fullOnA1, fullOnA2, mixer = mixer(MixerChannel(Timeline.A1, solo = true), MixerChannel(Timeline.A2))), 30, waves)
        assertEquals(0f, soloA1.channels.getValue(Timeline.A1), 1e-4f)
        assertEquals(floor, soloA1.channels.getValue(Timeline.A2), 0f)
        assertEquals(0f, soloA1.master, 1e-4f)

        val muteA1 = meter(docOf(fullOnA1, fullOnA2, mixer = mixer(MixerChannel(Timeline.A1, mute = true), MixerChannel(Timeline.A2))), 30, waves)
        assertEquals(floor, muteA1.channels.getValue(Timeline.A1), 0f)
        assertEquals(0f, muteA1.channels.getValue(Timeline.A2), 1e-4f)

        val disabled = meter(docOf(fullOnA1, fullOnA2, disabled = setOf(Timeline.A2)), 30, waves)
        assertEquals(floor, disabled.channels.getValue(Timeline.A2), 0f)

        val floored = meter(docOf(fullOnA1.copy(volumeDb = floor)), 30, waves)
        assertEquals(floor, floored.channels.getValue(Timeline.A1), 0f)
        assertEquals(floor, floored.master, 0f)
    }

    @Test
    fun `fades scale the level near the clip edges`() {
        val doc = docOf(fullOnA1.copy(fadeInSeconds = 2f, fadeOutSeconds = 1f))

        assertEquals(halfDb, meter(doc, 30, waves).channels.getValue(Timeline.A1), 1e-3f)
        assertEquals(0f, meter(doc, 150, waves).channels.getValue(Timeline.A1), 1e-4f)
        assertEquals(halfDb, meter(doc, 285, waves).channels.getValue(Timeline.A1), 1e-3f)
        assertEquals(floor, meter(doc, 300, waves).channels.getValue(Timeline.A1), 0f)
    }

    @Test
    fun `the meter reads the source under the playhead honouring the in point and speed`() {
        // Loud only between 2 s and 4 s of the source.
        val burst = Waveform(20, FloatArray(500) { bucket -> if (bucket in 100 until 200) 1f else 0.1f })
        val sources = mapOf("burst" to burst)
        val normal = docOf(clipOf("X", 0, 300, track = Timeline.A1, assetId = "burst", sourceInMs = 2_000))
        val fast = docOf(clipOf("X", 0, 300, track = Timeline.A1, assetId = "burst", sourceInMs = 2_000, speed = PlaybackSpeed.DOUBLE))

        assertEquals(0f, meter(normal, 15, sources).channels.getValue(Timeline.A1), 1e-4f) // source 2.5 s
        assertEquals(0f, meter(normal, 45, sources).channels.getValue(Timeline.A1), 1e-4f) // source 3.5 s
        assertEquals(-20f, meter(normal, 75, sources).channels.getValue(Timeline.A1), 0.01f) // source 4.5 s
        assertEquals(-20f, meter(fast, 45, sources).channels.getValue(Timeline.A1), 0.01f) // source 5 s at 2x
        assertEquals(floor, meter(normal, 15, emptyMap()).channels.getValue(Timeline.A1), 0f) // no decoded peaks yet
    }

    @Test
    fun `track dynamics turn the reading down by their steady curve, before the fader`() {
        val comp = TrackDynamics(compressor = true, thresholdDb = -20f, ratio = 4f)
        val doc = docOf(fullOnA1, mixer = mixer(MixerChannel(Timeline.A1, volumeDb = -6f, dynamics = comp), MixerChannel(Timeline.A2)))
        val levels = meter(doc, 30, waves)
        // A full-scale source is 20 dB over: 15 dB down, then the fader.
        assertEquals(0f, levels.inputs.getValue(Timeline.A1), 1e-4f)
        assertEquals(-15f, levels.reductions.getValue(Timeline.A1), 1e-4f)
        assertEquals(-21f, levels.channels.getValue(Timeline.A1), 1e-3f)
        val bypassed = meter(docOf(fullOnA1, mixer = mixer(MixerChannel(Timeline.A1, dynamics = comp.copy(enabled = false)))), 30, waves)
        assertEquals(0f, bypassed.reductions.getValue(Timeline.A1), 0f)
        assertEquals(0f, bypassed.channels.getValue(Timeline.A1), 1e-4f)
    }

    @Test
    fun `a disabled sound clip is silent on the meters`() {
        val levels = meter(docOf(fullOnA1.copy(enabled = false)), 30, waves)
        assertEquals(floor, levels.channels.getValue(Timeline.A1), 0f)
    }

    @Test
    fun `a ducked track reads its dip at the playhead, and reports it while silent too`() {
        // A2 ducks 12 dB under A1; the envelope is fully down for the first second, then up.
        val duck = TrackDucking(keyTrackId = Timeline.A1, depthDb = 12f)
        val doc = docOf(fullOnA1, fullOnA2, mixer = mixer(MixerChannel(Timeline.A1), MixerChannel(Timeline.A2, ducking = duck)))
        val envelopes = mapOf(Timeline.A2 to DuckEnvelope(500_000, floatArrayOf(1f, 1f, 1f, 0f, 0f)))
        val down = meter(doc, 15, waves, envelopes)
        assertEquals(-12f, down.channels.getValue(Timeline.A2), 1e-3f)
        assertEquals("the key itself is untouched", 0f, down.channels.getValue(Timeline.A1), 1e-4f)
        assertEquals(-12f, down.ducks.getValue(Timeline.A2), 1e-4f)
        assertEquals("on the way back up (1.2 s: 60 % down)", -7.2f, meter(doc, 36, waves, envelopes).ducks.getValue(Timeline.A2), 1e-3f)
        assertEquals(0f, meter(doc, 60, waves, envelopes).channels.getValue(Timeline.A2), 1e-4f)
        val bypassed = docOf(fullOnA1, fullOnA2, mixer = mixer(MixerChannel(Timeline.A1), MixerChannel(Timeline.A2, ducking = duck.copy(enabled = false))))
        assertEquals("bypassed", 0f, meter(bypassed, 15, waves, envelopes).channels.getValue(Timeline.A2), 1e-4f)
        val silent = meter(docOf(fullOnA1, mixer = mixer(MixerChannel(Timeline.A1), MixerChannel(Timeline.A2, ducking = duck))), 15, waves, envelopes)
        assertEquals("nothing sounds there, but the dip is known", -12f, silent.ducks.getValue(Timeline.A2), 1e-4f)
    }
}
