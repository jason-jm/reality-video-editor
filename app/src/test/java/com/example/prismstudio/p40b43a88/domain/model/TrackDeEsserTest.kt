package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Fairlight's De-Esser: only the high band, only where it is loud, and nothing at all while it is off. */
class TrackDeEsserTest {

    private val rate = 48_000

    /** [seconds] of [tones] (Hz to peak amplitude), stereo, through [deEsser]; the RMS of the last half, in dBFS. */
    private fun run(deEsser: TrackDeEsser, vararg tones: Pair<Double, Double>, seconds: Double = 0.4): Double {
        val gain = DeEsserGain(deEsser, rate, 2)
        val n = (rate * seconds).toInt()
        var sum = 0.0
        var count = 0
        val frame = FloatArray(2)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val x = tones.sumOf { (hz, a) -> a * sin(2 * PI * hz * t) }.toFloat()
            frame[0] = x; frame[1] = x
            gain.process(frame)
            if (i >= n / 2) { sum += frame[0].toDouble() * frame[0]; count++ }
        }
        return 20 * log10(sqrt(sum / count))
    }

    @Test
    fun `the threshold follows the amount, the band is compressed 4 to 1 up to 18 dB`() {
        assertEquals(-10.0, TrackDeEsser(amount = 0).thresholdDb, 1e-9)
        assertEquals(-25.0, TrackDeEsser(amount = 50).thresholdDb, 1e-9)
        assertEquals(-40.0, TrackDeEsser(amount = 100).thresholdDb, 1e-9)
        val d = TrackDeEsser(amount = 50)
        assertEquals(0.0, d.reductionDb(-30.0), 0.0)
        assertEquals(7.5, d.reductionDb(-15.0), 1e-9)
        assertEquals(18.0, d.reductionDb(0.0), 1e-9)
        assertEquals(0.0, TrackDeEsser(amount = 80, enabled = false).reductionDb(0.0), 0.0)
        assertEquals(0.0, TrackDeEsser.OFF.reductionDb(0.0), 0.0)
    }

    @Test
    fun `loud sibilance comes down, the voice beneath and quiet hiss stay`() {
        val strong = TrackDeEsser(amount = 100)
        val off = TrackDeEsser.OFF
        // A loud 9 kHz "s" at −6 dBFS peak: well into the band, turned down by close to the full 18 dB.
        val s = run(off, 9_000.0 to 0.5) - run(strong, 9_000.0 to 0.5)
        assertTrue("sibilance down $s dB", s in 14.0..19.0)
        // A 300 Hz voice at the same level: under the band, untouched.
        val voice = run(off, 300.0 to 0.5) - run(strong, 300.0 to 0.5)
        assertTrue("voice changed $voice dB", kotlin.math.abs(voice) < 0.1)
        // Quiet 9 kHz hiss at −52 dBFS: under the threshold, untouched.
        val hiss = run(off, 9_000.0 to 0.0025) - run(strong, 9_000.0 to 0.0025)
        assertTrue("hiss changed $hiss dB", kotlin.math.abs(hiss) < 0.1)
        // A gentler amount turns the same "s" down less.
        val gentle = run(off, 9_000.0 to 0.5) - run(TrackDeEsser(amount = 30), 9_000.0 to 0.5)
        assertTrue("gentle $gentle dB", gentle in 3.0..s - 3.0)
    }

    @Test
    fun `a track's de-esser survives the project file, written only when set`() {
        val doc = com.example.prismstudio.p40b43a88.ui.UiFixtures.document()
        val ch = doc.mixer.channel(Timeline.A1).copy(deEsser = TrackDeEsser(amount = 70, band = DeEssBand.HIGH))
        val set = doc.copy(mixer = doc.mixer.replace(ch))
        val codec = com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
        assertEquals(TrackDeEsser(amount = 70, band = DeEssBand.HIGH), codec.decode(codec.encode(set)).mixer.channel(Timeline.A1).deEsser)
        assertTrue(!codec.encode(doc).contains("deEss"))
        assertEquals(TrackDeEsser.OFF, codec.decode(codec.encode(doc)).mixer.channel(Timeline.A1).deEsser)
    }

    @Test
    fun `with nothing to turn down the sound passes sample for sample, and a higher band leaves 6 kHz alone`() {
        val gain = DeEsserGain(TrackDeEsser(amount = 100), rate, 1)
        val frame = FloatArray(1)
        for (i in 0 until 4_800) {
            val x = (0.5 * sin(2 * PI * 200.0 * i / rate)).toFloat()
            frame[0] = x
            gain.process(frame)
            assertEquals(x, frame[0], 1e-6f)
        }
        val high = TrackDeEsser(amount = 100, band = DeEssBand.HIGH)
        val low = TrackDeEsser(amount = 100, band = DeEssBand.LOW)
        val at5k = { d: TrackDeEsser -> run(TrackDeEsser.OFF, 5_000.0 to 0.5) - run(d, 5_000.0 to 0.5) }
        assertTrue("4 kHz band takes more of 5 kHz than the 8 kHz band", at5k(low) > at5k(high) + 3.0)
    }
}
