package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.NoiseGate
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfileAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.log10

/** A lane's noise reduction on a sound item: the item keeps its length and place; noise goes down, live from the mix. */
class NoiseReductionAudioProcessorTest {

    private val rate = 48_000

    private fun registry(nr: TrackNoiseReduction): LiveLookRegistry {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1))
        val mixer = MixerState.forTracks(timeline.tracks).let { m -> m.replace(m.channel(Timeline.A1).copy(noiseReduction = nr)) }
        return LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }
    }

    /** [samples] (stereo 16-bit, the same on both sides) through [p] in chunks of [chunk] frames, then the end: the left channel. */
    private fun run(p: NoiseReductionAudioProcessor, samples: ShortArray, chunk: Int = 777): ShortArray {
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        val out = ArrayList<Short>(samples.size)
        fun drain() {
            val o = p.getOutput().order(ByteOrder.nativeOrder())
            while (o.remaining() >= 4) {
                out += o.getShort()
                o.getShort()
            }
        }
        var at = 0
        while (at < samples.size) {
            val n = minOf(chunk, samples.size - at)
            val input = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            repeat(n) { input.putShort(samples[at + it]); input.putShort(samples[at + it]) }
            input.flip()
            p.queueInput(input)
            drain()
            at += n
        }
        p.queueEndOfStream()
        drain()
        assertTrue(p.isEnded)
        return out.toShortArray()
    }

    private val noise = Random(3).let { r -> ShortArray(rate * 2) { (r.nextGaussian() * 330).toInt().toShort() } }
    private val profile: NoiseProfile = NoiseProfileAnalyzer(rate, 1).also { a -> a.add(FloatArray(noise.size) { noise[it] / 32768f }, noise.size) }.finish()!!

    private fun rmsDb(x: ShortArray, from: Int, until: Int): Double {
        var sum = 0.0
        for (i in from until until) sum += x[i].toDouble() * x[i]
        return 10 * log10(sum / (until - from))
    }

    @Test
    fun `switched off it gives back the item frame for frame, as long as it came, in any chunks`() {
        val ramp = ShortArray(5_000) { (it * 6 - 15_000).toShort() }
        val out = run(NoiseReductionAudioProcessor(registry(TrackNoiseReduction.OFF), Timeline.A1, profile), ramp)
        assertEquals(ramp.size, out.size)
        ramp.indices.forEach { assertEquals("frame $it", ramp[it].toDouble(), out[it].toDouble(), 1.0) }
        assertEquals("shorter than its latency too", 300, run(NoiseReductionAudioProcessor(registry(TrackNoiseReduction.OFF), Timeline.A1, profile), ShortArray(300) { 1000 }).size)
    }

    @Test
    fun `on, the lane's setting turns the noise down`() {
        val out = run(NoiseReductionAudioProcessor(registry(TrackNoiseReduction(enabled = true, reductionDb = 18f)), Timeline.A1, profile), noise)
        assertEquals(noise.size, out.size)
        assertEquals(-18.0, rmsDb(out, rate / 2, rate * 3 / 2) - rmsDb(noise, rate / 2, rate * 3 / 2), 2.5)
    }

    @Test
    fun `it leaves the latency of its transform out of the item`() {
        assertEquals(NoiseGate.FFT_SIZE * 3 / 4, NoiseGate.LATENCY)
        // A single click comes out where it went in.
        val click = ShortArray(6_000).also { it[2_500] = 20_000 }
        val out = run(NoiseReductionAudioProcessor(registry(TrackNoiseReduction.OFF), Timeline.A1, profile), click)
        assertEquals(2_500, out.indices.maxBy { kotlin.math.abs(out[it].toInt()) })
    }
}
