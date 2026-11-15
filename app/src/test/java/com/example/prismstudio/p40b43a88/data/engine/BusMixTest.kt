package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.MixerBus
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A submix bus in the engine: Media3 sums every lane at once, so the bus's stages run on each member lane — exact because
 * they are linear: the bus EQ on each lane adds up to the EQ of the lanes' sum, the bus fader and mute scale every lane.
 */
class BusMixTest {

    private val rate = 48_000
    private val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1), clipOf("T", 0, 300, track = Timeline.A2))
    private val eq = EqPreset.PODCAST.eq

    private fun registry(mixer: MixerState) = LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }

    /** Both tracks on bus 1, changed by [bus]. */
    private fun onBus(bus: (MixerBus) -> MixerBus = { it }) = MixerState.forTracks(timeline.tracks).let { m ->
        m.copy(buses = listOf(bus(MixerBus("b1", "总线 1", eq = eq))), channels = m.channels.map { it.copy(bus = "b1") })
    }

    /** [samples] (both sides the same) through track [trackId]'s lane in [registry]; the left side out. */
    private fun lane(registry: LiveLookRegistry, trackId: String, clipId: String, samples: FloatArray): FloatArray {
        val p = GainFadeAudioProcessor(registry, trackId, clipId, clipOffsetUs = 0, clipDurationUs = 10_000_000)
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(samples.size * 8).order(ByteOrder.nativeOrder())
        samples.forEach { input.putFloat(it); input.putFloat(it) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 8) { out.getFloat().also { out.getFloat() } }
    }

    private val dialogue = FloatArray(rate / 4) { i -> (0.3 * sin(2 * PI * 180.0 * i / rate) + 0.2 * sin(2 * PI * 3_100.0 * i / rate)).toFloat() }
    private val music = FloatArray(rate / 4) { i -> (0.25 * sin(2 * PI * 420.0 * i / rate) + 0.1 * sin(2 * PI * 7_000.0 * i / rate + 1.0)).toFloat() }

    @Test
    fun `the bus EQ on each lane adds up to the EQ of the summed sound`() {
        val r = registry(onBus())
        val summed = lane(r, Timeline.A1, "S", dialogue).zip(lane(r, Timeline.A2, "T", music)) { a, b -> a + b }
        // The same EQ on one track carrying both sounds, no bus.
        val plain = MixerState.forTracks(timeline.tracks).let { it.replace(it.channel(Timeline.A1).copy(eq = eq)) }
        val reference = lane(registry(plain), Timeline.A1, "S", FloatArray(dialogue.size) { dialogue[it] + music[it] })
        assertEquals(reference.size, summed.size)
        val worst = summed.indices.maxOf { abs(summed[it] - reference[it]) }
        assertEquals("sample for sample", 0f, worst, 1e-5f)
        // And it does change the sound.
        val dry = FloatArray(dialogue.size) { dialogue[it] + music[it] }
        assertTrue(dry.indices.maxOf { abs(dry[it] - reference[it]) } > 0.01f)
    }

    @Test
    fun `the bus fader scales every lane, a muted bus silences them, a soloed one silences the rest`() {
        val half = lane(registry(onBus { it.copy(eq = com.example.prismstudio.p40b43a88.domain.model.TrackEq.FLAT, volumeDb = -6.0206f) }), Timeline.A1, "S", dialogue)
        assertEquals(dialogue[1000] / 2, half[1000], 1e-4f)
        assertEquals(0f, lane(registry(onBus { it.copy(mute = true) }), Timeline.A2, "T", music).maxOf { abs(it) }, 0f)
        // Only A1 on a soloed bus: A2, going straight out, falls silent.
        val soloed = MixerState.forTracks(timeline.tracks).let { m ->
            m.copy(buses = listOf(MixerBus("b1", "总线 1", solo = true)), channels = m.channels.map { if (it.trackId == Timeline.A1) it.copy(bus = "b1") else it })
        }
        assertEquals(0f, lane(registry(soloed), Timeline.A2, "T", music).maxOf { abs(it) }, 0f)
        assertEquals(music[1000], lane(registry(soloed), Timeline.A1, "S", music)[1000], 1e-5f)
    }

    /** [samples] (both sides the same) through [p], then the end of its input; the left side out. */
    private fun item(p: GainFadeAudioProcessor, samples: FloatArray): FloatArray {
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(samples.size * 8).order(ByteOrder.nativeOrder())
        samples.forEach { input.putFloat(it); input.putFloat(it) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())
        val left = FloatArray(out.remaining() / 8) { out.getFloat().also { out.getFloat() } }
        p.queueEndOfStream()
        return left
    }

    @Test
    fun `at a cut the bus EQ carries on from the item before, as a bus hearing the lane without a break`() {
        // Two clips back to back on A1 (0.5 s each at 30 fps), the track on bus 1 with its EQ.
        val cut = timelineOf(clipOf("S", 0, 15, track = Timeline.A1), clipOf("T", 15, 15, track = Timeline.A1))
        val mixer = MixerState.forTracks(cut.tracks).let { m ->
            m.copy(buses = listOf(MixerBus("b1", "总线 1", eq = eq)), channels = m.channels.map { if (it.trackId == Timeline.A1) it.copy(bus = "b1") else it })
        }
        val r = LiveLookRegistry().also { it.update(projectOf(timeline = cut, mixer = mixer)) }
        val half = rate / 2
        val sound = FloatArray(rate) { i -> (0.4 * sin(2 * PI * 95.0 * i / rate) + 0.2 * sin(2 * PI * 5_000.0 * i / rate)).toFloat() }
        fun pieces(link: ReverbLink?): FloatArray {
            val a = item(GainFadeAudioProcessor(r, Timeline.A1, "S", 0, 500_000, laneStartUs = 0, reverbLink = link), sound.copyOfRange(0, half))
            val b = item(GainFadeAudioProcessor(r, Timeline.A1, "T", 0, 500_000, laneStartUs = 500_000, reverbLink = link), sound.copyOfRange(half, rate))
            return a + b
        }
        val unbroken = item(GainFadeAudioProcessor(r, Timeline.A1, "S", 0, 1_000_000), sound)
        val linked = pieces(ReverbLink())
        assertEquals("handed on: no seam", 0f, linked.indices.maxOf { abs(linked[it] - unbroken[it]) }, 1e-5f)
        val cold = pieces(null)
        assertTrue("starting over cold leaves a seam", cold.indices.maxOf { abs(cold[it] - unbroken[it]) } > 0.01f)
    }
}
