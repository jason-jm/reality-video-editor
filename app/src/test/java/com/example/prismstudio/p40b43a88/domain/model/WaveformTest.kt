package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class WaveformTest {

    /** Five 20 ms buckets: 0..19, 20..39, 40..59, 60..79, 80..99 ms. */
    private val wave = Waveform(20, floatArrayOf(0.1f, 0.5f, 0.2f, 0.9f, 0.3f))

    private fun amplitude(db: Float) = 10f.pow(db / 20f)

    @Test
    fun `peak returns the loudest bucket inside the requested source range`() {
        assertEquals(0.5f, wave.peak(0, 39), 0f)
        assertEquals(0.9f, wave.peak(40, 79), 0f)
        assertEquals(0.9f, wave.peak(60, 60), 0f)
        assertEquals(0.2f, wave.peak(45, 55), 0f)
        assertEquals(100L, wave.durationMs)
    }

    @Test
    fun `peak clamps ranges that run outside the source and an empty waveform is silent`() {
        assertEquals(0.1f, wave.peak(-100, 10), 0f)
        assertEquals(0.3f, wave.peak(500, 900), 0f)
        assertEquals(0.9f, wave.peak(-50, 10_000), 0f)
        assertEquals(0f, Waveform(20, FloatArray(0)).peak(0, 1_000), 0f)
    }

    @Test
    fun `display level maps the peak onto a dB scale from the floor to full scale`() {
        assertEquals(1f, Waveform.displayLevel(1f), 1e-6f)
        assertEquals(0.874571f, Waveform.displayLevel(0.5f), 1e-5f)
        assertEquals(0.5f, Waveform.displayLevel(amplitude(-24f)), 1e-5f)
        assertEquals(0f, Waveform.displayLevel(amplitude(Waveform.DISPLAY_FLOOR_DB)), 1e-5f)
        assertEquals(0f, Waveform.displayLevel(amplitude(-60f)), 0f)
        assertEquals(0f, Waveform.displayLevel(0f), 0f)
        assertEquals(0f, Waveform.displayLevel(-0.5f), 0f)
    }

    @Test
    fun `display level applies the gain before clamping`() {
        assertEquals(1f, Waveform.displayLevel(0.5f, gainDb = 6.0206f), 1e-4f)
        assertEquals(1f, Waveform.displayLevel(1f, gainDb = 12f), 0f)
        assertEquals(0.5f, Waveform.displayLevel(1f, gainDb = -24f), 1e-6f)
        assertEquals(0.75f, Waveform.displayLevel(amplitude(-24f), gainDb = 12f), 1e-5f)
        assertEquals(0f, Waveform.displayLevel(1f, gainDb = -60f), 0f)
    }
}
