package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** BS.1770 loudness measurement against EBU Tech 3341-style reference signals. */
class LoudnessAnalyzerTest {

    @Test
    fun `the K-weighting matches the BS1770 coefficients at 48 kHz`() {
        val k = KWeighting(48_000)
        assertArrayEquals(doubleArrayOf(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585), k.shelf, 1e-8)
        assertArrayEquals(doubleArrayOf(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621), k.highPass, 1e-8)
    }

    @Test
    fun `a 1 kHz stereo sine at -23 dBFS measures -23 LUFS at 48 and 44_1 kHz`() {
        for (rate in listOf(48_000, 44_100)) {
            val profile = analyze(rate, channels = 2, listOf(-23.0 to 20.0))
            assertEquals("$rate Hz", -23.0, gatedLoudness(profile.energy.toDoubles())!!, 0.1)
            assertEquals("true peak of a low tone: its level", 10.0.pow(-23 / 20.0), profile.truePeak.max().toDouble(), 1e-3)
        }
    }

    @Test
    fun `a mono file is heard on both channels of the stereo mix`() {
        val profile = analyze(48_000, channels = 1, listOf(-23.0 to 10.0))
        assertEquals(-23.0, gatedLoudness(profile.energy.toDoubles())!!, 0.1)
    }

    @Test
    fun `quiet passages under the relative gate do not lower the reading`() {
        // EBU 3341 case 3, shortened: -36 / -23 / -36 dBFS.
        val profile = analyze(48_000, channels = 2, listOf(-36.0 to 5.0, -23.0 to 30.0, -36.0 to 5.0))
        assertEquals(-23.0, gatedLoudness(profile.energy.toDoubles())!!, 0.1)
    }

    @Test
    fun `silence and stretches under one gating window have no loudness`() {
        assertNull(gatedLoudness(analyze(48_000, 2, listOf(-200.0 to 2.0)).energy.toDoubles()))
        assertNull(gatedLoudness(DoubleArray(3) { 0.01 }))
        assertEquals(100, analyze(48_000, 2, listOf(-23.0 to 1.05)).blockMs)
        assertEquals("a partial last block is kept", 11, analyze(48_000, 2, listOf(-23.0 to 1.05)).blocks)
    }

    /** Stereo (or mono) 997 Hz sine segments of (level in dBFS peak, seconds), fed in uneven chunks. */
    private fun analyze(rate: Int, channels: Int, segments: List<Pair<Double, Double>>): LoudnessProfile {
        val analyzer = LoudnessAnalyzer(rate, channels)
        val chunk = 1_237
        val buffer = FloatArray(chunk * channels)
        var n = 0L
        for ((db, seconds) in segments) {
            val amplitude = if (db < -150) 0.0 else 10.0.pow(db / 20.0)
            var left = (seconds * rate).toLong()
            while (left > 0) {
                val frames = minOf(left, chunk.toLong()).toInt()
                for (f in 0 until frames) {
                    val v = (amplitude * sin(2 * PI * 997.0 * (n + f) / rate)).toFloat()
                    for (c in 0 until channels) buffer[f * channels + c] = v
                }
                analyzer.add(buffer, frames)
                n += frames
                left -= frames
            }
        }
        return analyzer.finish()
    }

    private fun FloatArray.toDoubles() = DoubleArray(size) { this[it].toDouble() }
}
