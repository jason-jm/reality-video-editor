package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ClipPitchTest {

    private val rate = 44_100

    /** One second of a [hz] sine at half scale through [p], drained to the end. */
    private fun shift(p: AudioProcessor, hz: Double): ShortArray {
        p.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val out = ArrayList<Short>()
        fun drain() {
            val o = p.getOutput().order(ByteOrder.nativeOrder())
            while (o.remaining() >= 2) out += o.getShort()
        }
        val block = 1024
        var n = 0
        while (n < rate) {
            val count = minOf(block, rate - n)
            val input = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
            repeat(count) { i -> input.putShort((16_000 * sin(2 * PI * hz * (n + i) / rate)).toInt().toShort()) }
            input.flip()
            while (input.hasRemaining()) {
                p.queueInput(input)
                drain()
            }
            n += count
        }
        p.queueEndOfStream()
        repeat(100) { drain() }
        return out.toShortArray()
    }

    /** The strongest frequency (to 1 Hz) between [from] and [to] in the middle half of [s], Hann windowed. */
    private fun peakHz(s: ShortArray, from: Int, to: Int): Int {
        val a = s.size / 4
        val len = s.size / 2
        val w = DoubleArray(len) { i -> (0.5 - 0.5 * cos(2 * PI * i / (len - 1))) * s[a + i] }
        return (from..to).maxBy { hz ->
            var re = 0.0
            var im = 0.0
            for (i in 0 until len) {
                val ph = 2 * PI * hz * i / rate
                re += w[i] * cos(ph)
                im -= w[i] * sin(ph)
            }
            hypot(re, im)
        }
    }

    @Test
    fun `pitch is an octave either way, the ratio doubling each 1200 cents`() {
        assertEquals(1f, Clip.pitchRatio(0), 0f)
        assertEquals(2f, Clip.pitchRatio(1200), 1e-6f)
        assertEquals(0.5f, Clip.pitchRatio(-1200), 1e-6f)
        assertEquals(1.4983f, Clip.pitchRatio(700), 1e-4f)
        assertNull("no pitch, no processor", CompositionFactory.pitchShifter(0))
    }

    @Test
    fun `Sonic shifts a tone up an octave or a fifth and down an octave, keeping its length`() {
        for ((cents, expected) in listOf(1200 to 880, 700 to 659, -1200 to 220)) {
            val out = shift(CompositionFactory.pitchShifter(cents)!!, 440.0)
            assertTrue("$cents cents: one second in, one second out (${out.size})", abs(out.size - rate) <= rate / 100)
            assertEquals("$cents cents", expected.toDouble(), peakHz(out, 150, 1000).toDouble(), 2.0)
        }
    }
}
