package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A sound item's chain as Media3 runs it (noise reduction, then the lined-up mix): every frame arrives once and the item
 * keeps its length — also when the pipeline says the input has ended while output is still waiting downstream, as it does
 * (the reused output buffer once lost a step's frames and the held tail there: rev 97).
 */
class AudioPipelineTest {

    private val rate = 48_000

    private fun registry(nr: TrackNoiseReduction): LiveLookRegistry {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1))
        val mixer = MixerState.forTracks(timeline.tracks).let { m -> m.replace(m.channel(Timeline.A1).copy(noiseReduction = nr)) }
        return LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }
    }

    /**
     * Runs [samples] (stereo 16-bit, the same on both sides) through [processors] in [chunk]-frame buffers, reading output
     * only every other step and not at all after the last one: the end comes while output still waits.
     */
    private fun run(processors: List<AudioProcessor>, samples: ShortArray, chunk: Int): ShortArray {
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(processors))
        pipeline.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val out = ArrayList<Short>()
        fun read() {
            while (true) {
                val o = pipeline.output.order(ByteOrder.nativeOrder())
                if (!o.hasRemaining()) return
                while (o.remaining() >= 4) {
                    out += o.getShort()
                    o.getShort()
                }
            }
        }
        var at = 0
        var step = 0
        while (at < samples.size) {
            val n = minOf(chunk, samples.size - at)
            val input = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            repeat(n) { input.putShort(samples[at + it]); input.putShort(samples[at + it]) }
            input.flip()
            while (input.hasRemaining()) {
                pipeline.queueInput(input)
                if (input.hasRemaining()) read()
            }
            // Output left waiting every other step, as a busy consumer leaves it, and after the last.
            at += n
            if (step++ % 2 == 1 && at < samples.size) read()
        }
        pipeline.queueEndOfStream()
        var guard = 0
        while (!pipeline.isEnded && guard++ < 10_000) read()
        return out.toShortArray()
    }

    private fun aligned(key: String, lengthUs: Long): AudioAlignment {
        val starts = AudioStarts().also { it.record(key, 1_000_000) }
        // The sound the item plays from is 100 frames (2083 µs) into what was decoded.
        return AudioAlignment(starts, key, wantedUs = 1_002_083, primingUs = 0, windowStartUs = 900_000, speed = 1f, lengthUs = lengthUs)
    }

    @Test
    fun `noise reduction then the lined-up mix give the item exactly its length, its click where it belongs`() {
        val flat = NoiseProfile(rate, FloatArray(513) { 1e-12f })
        for (chunk in listOf(256, 1000, 4096)) for (on in listOf(false, true)) {
            val click = ShortArray(30_000).also { it[12_100] = 20_000 }
            val nr = TrackNoiseReduction(enabled = on, reductionDb = 12f)
            val chain = listOf(
                NoiseReductionAudioProcessor(registry(nr), Timeline.A1, flat),
                GainFadeAudioProcessor(registry(nr), Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000, alignment = aligned("c$chunk$on", 500_000)),
            )
            val out = run(chain, click, chunk)
            assertEquals("chunks of $chunk, noise reduction $on: 0.5 s", 24_000, out.size)
            assertEquals("the click 12 000 frames in", 12_000, out.indices.maxBy { kotlin.math.abs(out[it].toInt()) })
        }
    }

    @Test
    fun `an item whose sound runs short is filled to its length through the whole chain`() {
        val flat = NoiseProfile(rate, FloatArray(513) { 1e-12f })
        val short = ShortArray(10_000) { 500 }
        val chain = listOf(
            NoiseReductionAudioProcessor(registry(TrackNoiseReduction.OFF), Timeline.A1, flat),
            GainFadeAudioProcessor(registry(TrackNoiseReduction.OFF), Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000, alignment = aligned("short", 500_000)),
        )
        val out = run(chain, short, 777)
        assertEquals(24_000, out.size)
        assertEquals("its sound, then silence", 500, out[9_000].toInt())
        assertEquals(0, out[20_000].toInt())
    }
}
