package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sqrt

class ReverbTankTest {

    private val rate = 44_100

    /** The room's answer to a click: [seconds] of the left side, only the room heard (mix 100 %). */
    private fun impulse(reverb: TrackReverb, seconds: Double = 5.0): FloatArray {
        val tank = ReverbTank(rate).apply { tune(reverb.copy(mix = 100)) }
        val f = FloatArray(2)
        return FloatArray((seconds * rate).toInt()) { i ->
            f[0] = if (i == 0) 1f else 0f
            f[1] = f[0]
            tank.process(f)
            f[0]
        }
    }

    /** RT60 from the backward-integrated energy (Schroeder), fitted between −5 and −35 dB (T30). */
    private fun rt60(h: FloatArray): Double {
        val edc = DoubleArray(h.size)
        var acc = 0.0
        for (i in h.indices.reversed()) {
            acc += h[i].toDouble() * h[i]
            edc[i] = acc
        }
        val db = { i: Int -> 10 * log10(edc[i] / edc[0]) }
        val a = h.indices.first { db(it) <= -5.0 }
        val b = h.indices.first { db(it) <= -35.0 }
        return 2.0 * (b - a) / rate
    }

    /**
     * Three RBJ band-passes (Q 4) around [hz]: narrow and steep enough that the slower lows in a single filter's skirts do
     * not take over the late tail.
     */
    private fun bandPass(x: FloatArray, hz: Double): FloatArray = bandPass1(bandPass1(bandPass1(x, hz), hz), hz)

    private fun bandPass1(x: FloatArray, hz: Double): FloatArray {
        val w = 2 * PI * hz / rate
        val alpha = kotlin.math.sin(w) / (2 * 4.0)
        val a0 = 1 + alpha
        val b0 = alpha / a0
        val b2 = -alpha / a0
        val a1 = -2 * kotlin.math.cos(w) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        return FloatArray(x.size) { i ->
            val y = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x[i].toDouble(); y2 = y1; y1 = y
            y.toFloat()
        }
    }

    @Test
    fun `the room falls 60 dB in its decay time, whatever the time`() {
        for (decay in listOf(0.6f, 2f, 4.5f)) {
            val measured = rt60(impulse(TrackReverb(decaySeconds = decay, damping = 0, preDelayMs = 0), seconds = decay * 1.6 + 1))
            assertEquals("RT60 $decay s", decay.toDouble(), measured, decay * 0.1)
        }
    }

    @Test
    fun `the damping takes the highs down sooner, as decayAt says`() {
        val r = TrackReverb(decaySeconds = 2.2f, damping = 60, preDelayMs = 0)
        val h = impulse(r, seconds = 4.0)
        val high = rt60(bandPass(h, 8_000.0))
        val low = rt60(bandPass(h, 500.0))
        assertEquals("the lows keep the decay time", 2.2, low, 0.25)
        assertEquals("the highs fall as predicted", r.decayAt(8_000.0), high, r.decayAt(8_000.0) * 0.2)
        assertTrue("well under the decay time", r.decayAt(8_000.0) < 1.5)
        assertEquals("no damping, one decay time", 2.2, TrackReverb(decaySeconds = 2.2f, damping = 0).decayAt(8_000.0), 1e-6)
    }

    @Test
    fun `the pre-delay holds the room back by exactly that long`() {
        val first = { pre: Int -> impulse(TrackReverb(decaySeconds = 1f, preDelayMs = pre), seconds = 0.5).indexOfFirst { it != 0f } }
        assertEquals("the shortest comb answers first", 1116, first(0))
        assertEquals(1116 + 4_410, first(100))
    }

    @Test
    fun `a steady sound's room is about as loud as the sound, short room or long`() {
        val random = java.util.Random(7)
        for (decay in listOf(0.5f, 4f)) {
            val tank = ReverbTank(rate).apply { tune(TrackReverb(decaySeconds = decay, damping = 0, mix = 100)) }
            val f = FloatArray(2)
            var inE = 0.0
            var outE = 0.0
            for (i in 0 until 6 * rate) {
                val x = (random.nextGaussian() * 0.1).toFloat()
                f[0] = x; f[1] = x
                tank.process(f)
                if (i >= 3 * rate) {
                    inE += x.toDouble() * x
                    outE += f[0].toDouble() * f[0]
                }
            }
            assertEquals("decay $decay s: wet / dry level (dB)", 0.0, 10 * log10(outE / inE), 2.0)
        }
    }

    @Test
    fun `the mix crossfades dry to room, and none leaves the sound as it is`() {
        val dry = ReverbTank(rate).apply { tune(TrackReverb(mix = 0)) }
        val f = FloatArray(2)
        for (i in 0 until 3000) {
            val x = kotlin.math.sin(i * 0.05).toFloat()
            f[0] = x; f[1] = -x
            dry.process(f)
            assertEquals(x, f[0], 0f)
            assertEquals(-x, f[1], 0f)
        }
        // Half: the dry sound at half level until the room answers (after the shortest comb).
        val half = ReverbTank(rate).apply { tune(TrackReverb(mix = 50, preDelayMs = 0)) }
        f[0] = 0.8f; f[1] = 0.8f
        half.process(f)
        assertEquals(0.4f, f[0], 1e-6f)
    }

    @Test
    fun `a room picked sets everything, moving a control leaves it, and settings are held in range`() {
        val hall = TrackReverb.OFF.copy(enabled = false).withRoom(ReverbRoom.HALL)
        assertEquals(TrackReverb(true, 2.2f, 25, 40, 25), hall)
        assertEquals(ReverbRoom.HALL, hall.room)
        assertNull(hall.copy(mix = 26).room)
        assertTrue(hall.isActive)
        assertTrue(!TrackReverb.OFF.isActive)
        assertEquals(TrackReverb(true, 6f, 200, 100, 100), TrackReverb(true, 60f, 900, 140, 300).clamped())
        assertEquals(0.3f, TrackReverb(decaySeconds = 0.01f).clamped().decaySeconds, 0f)
        // A mono-sized check of the tank at another rate: its combs scale, so the decay holds.
        val at48 = ReverbTank(48_000).apply { tune(TrackReverb(decaySeconds = 1f, damping = 0, preDelayMs = 0, mix = 100)) }
        val f = FloatArray(2)
        var e = 0.0
        var late = 0.0
        for (i in 0 until 48_000) {
            f[0] = if (i == 0) 1f else 0f; f[1] = f[0]
            at48.process(f)
            e += f[0] * f[0]
            if (i > 24_000) late += f[0] * f[0]
        }
        assertTrue("half a second in, well down (${10 * log10(late / e)} dB)", 10 * log10(late / e) < -25)
        assertTrue(sqrt(e) > 0)
    }
}
