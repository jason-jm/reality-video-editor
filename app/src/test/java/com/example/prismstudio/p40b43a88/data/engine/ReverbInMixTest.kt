package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class ReverbInMixTest {

    private val rate = 48_000
    private val registry = LiveLookRegistry().also {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1), clipOf("T", 300, 300, track = Timeline.A1))
        val mixer = MixerState.forTracks(timeline.tracks).let { m ->
            m.replace(m.channel(Timeline.A1).copy(reverb = TrackReverb(decaySeconds = 1f, preDelayMs = 0, damping = 0, mix = 50)))
        }
        it.update(projectOf(timeline = timeline, mixer = mixer))
    }

    private fun item(clipId: String, laneStartUs: Long, link: ReverbLink, dryEndUs: Long = Long.MAX_VALUE) =
        GainFadeAudioProcessor(registry, Timeline.A1, clipId, clipOffsetUs = 0, clipDurationUs = 10_000_000, laneStartUs = laneStartUs, dryEndUs = dryEndUs, reverbLink = link)
            .also {
                it.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
                it.flush(AudioProcessor.StreamMetadata.DEFAULT)
            }

    /** [samples] (one value per frame, both sides) through [p]; the left side out. */
    private fun run(p: AudioProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(samples.size * 8).order(ByteOrder.nativeOrder())
        samples.forEach { input.putFloat(it); input.putFloat(it) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 8) { out.getFloat().also { out.getFloat() } }
    }

    private fun amplitude(x: FloatArray, hz: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in x.indices) {
            re += x[i] * cos(2 * PI * hz * i / rate)
            im -= x[i] * sin(2 * PI * hz * i / rate)
        }
        return 2 * hypot(re, im) / x.size
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    @Test
    fun `past the sound only the room rings on - what the file plays there goes unheard`() {
        val link = ReverbLink()
        val a = item("S", 0, link, dryEndUs = 100_000)
        val random = java.util.Random(3)
        // 0.1 s of noise, then the file goes on with a loud 1 kHz tone the clip does not use.
        val input = FloatArray(rate / 2) { i -> if (i < rate / 10) (random.nextGaussian() * 0.3).toFloat() else (0.5 * sin(2 * PI * 1000 * i / rate)).toFloat() }
        val out = run(a, input)
        val after = out.copyOfRange(rate / 5, rate / 2)
        assertTrue("the tail rings on (${rms(after)})", rms(after) > 0.005)
        assertTrue("no tone (${amplitude(after, 1000.0)})", amplitude(after, 1000.0) < 0.002)
        assertEquals("the dry noise at half (mix 50 %) before the room answers", input[10] * 0.5f, out[10], 1e-6f)
    }

    @Test
    fun `the next item takes the room on where the last one ended, an item elsewhere starts silent`() {
        val link = ReverbLink()
        val a = item("S", 0, link)
        run(a, FloatArray(rate / 2) { i -> if (i < rate / 10) 0.4f * sin(i * 0.3f) else 0f })
        a.queueEndOfStream()
        // An item two seconds on does not hear it…
        val elsewhere = run(item("T", 2_000_000, link), FloatArray(rate / 10))
        assertEquals(0.0, rms(elsewhere), 0.0)
        // …the one that starts at 0.5 s, where the first ended, does.
        val next = run(item("T", 500_000, link), FloatArray(rate / 5))
        assertTrue("the tail goes on into the next clip (${rms(next)})", rms(next) > 0.002)
        // Once taken, it is gone.
        assertEquals(0.0, rms(run(item("T", 500_000, link), FloatArray(rate / 10))), 0.0)
    }
}
