package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Fairlight-style noise reduction: the spectral gate, its file noise floors and its settings. */
class NoiseReductionTest {

    private val rate = 48_000

    @Test
    fun `the FFT finds a tone in its bin and comes back to the samples it started from`() {
        val fft = Fft(1024)
        val re = FloatArray(1024) { sin(2 * PI * 32 * it / 1024).toFloat() }
        val im = FloatArray(1024)
        val original = re.copyOf()
        fft.transform(re, im)
        val magnitudes = FloatArray(512) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(32, magnitudes.indices.maxBy { magnitudes[it] })
        assertEquals(512f, magnitudes[32], 0.01f)
        fft.transform(re, im, inverse = true)
        original.indices.forEach { assertEquals("sample $it", original[it], re[it], 1e-5f) }
    }

    /** Runs [samples] (mono) through a gate of [settings] against [profile]: the output, with the gate's latency taken off. */
    private fun gate(samples: FloatArray, profile: NoiseProfile, settings: TrackNoiseReduction, speed: Float = 1f): FloatArray {
        val gate = NoiseGate(rate, 1, profile, speed)
        val out = ArrayList<Float>(samples.size + NoiseGate.LATENCY + NoiseGate.HOP)
        gate.process(samples, samples.size, settings) { out += it[0] }
        val tail = FloatArray(NoiseGate.LATENCY + NoiseGate.HOP)
        gate.process(tail, tail.size, settings) { out += it[0] }
        return FloatArray(samples.size) { out[it + NoiseGate.LATENCY] }
    }

    private fun noise(count: Int, rms: Double, seed: Long = 7): FloatArray {
        val random = Random(seed)
        return FloatArray(count) { (random.nextGaussian() * rms).toFloat() }
    }

    private fun rmsDb(x: FloatArray, from: Int, until: Int): Double {
        var sum = 0.0
        for (i in from until until) sum += x[i] * x[i]
        return 10 * log10(sum / (until - from))
    }

    private fun profileOf(samples: FloatArray): NoiseProfile = NoiseProfileAnalyzer(rate, 1).also { it.add(samples, samples.size) }.finish()!!

    @Test
    fun `switched off, the gate gives back exactly what came in`() {
        val input = FloatArray(20_000) { (sin(2 * PI * 440 * it / rate) * 0.3).toFloat() } + noise(10_000, 0.01)
        val flat = NoiseProfile(rate, FloatArray(NoiseGate.FFT_SIZE / 2 + 1) { 1e-6f })
        val out = gate(input, flat, TrackNoiseReduction.OFF)
        input.indices.forEach { assertEquals("frame $it", input[it], out[it], 1e-4f) }
    }

    @Test
    fun `noise is turned down by the reduction while a tone above it passes`() {
        // Two seconds: white noise at -40 dBFS throughout, a 1 kHz tone at -20 dBFS in the second half.
        val background = noise(2 * rate, 0.01)
        val input = FloatArray(2 * rate) { background[it] + if (it >= rate) (sin(2 * PI * 1000 * it / rate) * 0.1 * sqrt(2.0)).toFloat() else 0f }
        val profile = profileOf(background.copyOf(rate))
        val out = gate(input, profile, TrackNoiseReduction(enabled = true, reductionDb = 12f, thresholdDb = 6f))
        val noiseBefore = rmsDb(input, rate / 4, rate * 3 / 4)
        val noiseAfter = rmsDb(out, rate / 4, rate * 3 / 4)
        assertEquals("the noise alone: 12 dB down", -12.0, noiseAfter - noiseBefore, 2.0)
        // The tone's band passes; the noise around it in other bands is still turned down, so the whole stays within 0.6 dB.
        val toneBefore = rmsDb(input, rate * 5 / 4, rate * 7 / 4)
        val toneAfter = rmsDb(out, rate * 5 / 4, rate * 7 / 4)
        assertEquals(toneBefore, toneAfter, 0.6)
    }

    @Test
    fun `a file's floor sits near its noise's power, from its quietest frames`() {
        // White noise of RMS 0.01: each sqrt-Hann bin's power averages 0.0001 * 1024 / 2.
        val profile = profileOf(noise(3 * rate, 0.01))
        val expectedDb = 10 * log10(0.0001 * 1024 / 2)
        val levels = profile.levelsDb()
        listOf(20, 100, 250, 400).forEach { k -> assertTrue("bin $k at ${levels[k]} dB", levels[k] in (expectedDb - 4)..(expectedDb + 1)) }
        assertEquals(48_000, profile.sampleRate)
        assertEquals(profile.floor[21], profile.powerAt(21.0 * rate / 1024), 1e-9f)
        assertNull("digital silence has no floor", NoiseProfileAnalyzer(rate, 2).also { it.add(FloatArray(20_000), 10_000) }.finish())
        // Mostly silence with a few clicks: no noise to take away, so the clicks are not taken for its floor.
        val clicks = FloatArray(6 * rate).also { x -> listOf(1, 2, 3, 4).forEach { s -> for (i in 0 until 960) x[s * rate + i] = (0.5 * sin(2 * PI * 1000 * i / rate)).toFloat() } }
        val clickFloor = profileOf(clicks)
        assertTrue("floored at nothing: ${clickFloor.levelsDb()[21]}", clickFloor.levelsDb().all { it < -130f })
        // A pre-roll of silence before a noisy recording does not floor it at nothing.
        val noisy = FloatArray(rate / 2) + noise(3 * rate, 0.01)
        assertTrue(profileOf(noisy).levelsDb()[100] in (expectedDb - 4)..(expectedDb + 1))
    }

    @Test
    fun `a slowed clip's noise is looked for an octave lower when it plays at half speed`() {
        // A floor that is high below 4 kHz only: at 0.5× that noise plays below 2 kHz, so a 3 kHz tone at the floor's
        // level passes there untouched (its band is clean at half speed) but is cut at full speed.
        val floor = FloatArray(NoiseGate.FFT_SIZE / 2 + 1) { k -> if (k * rate / 1024.0 < 4000) 500f else 1e-9f }
        val profile = NoiseProfile(rate, floor)
        val tone = FloatArray(rate) { (sin(2 * PI * 3000 * it / rate) * 0.05).toFloat() }
        val settings = TrackNoiseReduction(enabled = true, reductionDb = 20f, thresholdDb = 6f)
        val full = rmsDb(gate(tone, profile, settings, speed = 1f), rate / 2, rate)
        val half = rmsDb(gate(tone, profile, settings, speed = 0.5f), rate / 2, rate)
        assertEquals(rmsDb(tone, rate / 2, rate), half, 0.5)
        assertTrue("cut at 1×: $full", full < rmsDb(tone, rate / 2, rate) - 15)
    }

    @Test
    fun `the gate has a soft knee above the threshold, and the settings step in half dBs within their ranges`() {
        val on = TrackNoiseReduction(enabled = true, reductionDb = 12f, thresholdDb = 6f)
        assertEquals(-12f, NoiseGate.gateDb(0f, on), 0f)
        assertEquals(-12f, NoiseGate.gateDb(6f, on), 0f)
        assertEquals(-6f, NoiseGate.gateDb(9f, on), 1e-6f)
        assertEquals(0f, NoiseGate.gateDb(12f, on), 0f)
        assertTrue(on.isActive)
        assertFalse(TrackNoiseReduction.OFF.isActive)
        assertFalse(on.copy(reductionDb = 0f).isActive)
        assertEquals(30f, NoiseReductionControl.REDUCTION.write(on, 99f).reductionDb, 0f)
        assertEquals(7.5f, NoiseReductionControl.THRESHOLD.write(on, 7.4f).thresholdDb, 0f)
        assertEquals(0f, NoiseReductionControl.THRESHOLD.write(on, -3f).thresholdDb, 0f)
    }
}
