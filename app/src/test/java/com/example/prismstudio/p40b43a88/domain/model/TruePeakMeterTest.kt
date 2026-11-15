package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BS.1770-4 Annex 2 true peak: the peaks between the samples. */
class TruePeakMeterTest {

    private fun db(x: Double) = 20 * log10(x)

    /** The largest reading over [seconds] of a [hz] sine at [rate], amplitude [amplitude], starting at [phase]. */
    private fun readings(hz: Double, rate: Int, amplitude: Double, phase: Double, seconds: Double = 0.5): Pair<Double, Double> {
        val meter = TruePeakMeter()
        var samplePeak = 0.0
        var truePeak = 0.0
        val n = (rate * seconds).toInt()
        for (i in 0 until n) {
            val x = amplitude * sin(2 * PI * hz * i / rate + phase)
            val tp = meter.process(x)
            // Past the interpolator's start-up.
            if (i > TruePeakMeter.TAPS) {
                samplePeak = max(samplePeak, abs(x))
                truePeak = max(truePeak, tp)
            }
        }
        return samplePeak to truePeak
    }

    @Test
    fun `a sine at a quarter of the rate peaks 3 dB above its samples`() {
        // Every sample lands at ±0.707 of the crest: −3.01 dB sample peak, 0 dB true peak.
        val (sample, truePeak) = readings(hz = 12_000.0, rate = 48_000, amplitude = 1.0, phase = PI / 4)
        assertEquals(-3.01, db(sample), 0.01)
        assertTrue("true peak ${db(truePeak)} dB", db(truePeak) in -0.4..0.1)
    }

    @Test
    fun `a low tone reads its own level`() {
        val (sample, truePeak) = readings(hz = 997.0, rate = 48_000, amplitude = 0.5, phase = 0.3)
        assertEquals(db(sample), db(truePeak), 0.05)
        assertEquals(-6.02, db(truePeak), 0.05)
        val (_, at44) = readings(hz = 997.0, rate = 44_100, amplitude = 0.5, phase = 0.3)
        assertEquals(-6.02, db(at44), 0.05)
    }

    @Test
    fun `the interpolator passes a steady level unchanged and its phases mirror each other`() {
        val meter = TruePeakMeter()
        var last = 0.0
        repeat(200) { last = meter.process(0.5) }
        assertEquals(0.5, last, 1e-9)
        TruePeakMeter.PHASES.forEachIndexed { p, phase ->
            assertEquals(1.0, phase.sum(), 1e-12)
            val mirror = TruePeakMeter.PHASES[TruePeakMeter.OVERSAMPLING - 1 - p]
            for (k in 0 until TruePeakMeter.TAPS) assertEquals(phase[k], mirror[TruePeakMeter.TAPS - 1 - k], 1e-12)
        }
    }
}
