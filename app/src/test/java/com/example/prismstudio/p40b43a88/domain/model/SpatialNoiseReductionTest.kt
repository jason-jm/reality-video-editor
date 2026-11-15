package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

/** Resolve's Spatial NR: the bilateral filter the clip shader runs (SpatialNrKernel mirrors it) and its settings. */
class SpatialNoiseReductionTest {

    private val w = 48
    private val h = 32

    /** A [w] × [h] picture: [colour] at each pixel ([at] gives it by x, y), plus Gaussian noise of [sigma] on each channel. */
    private fun picture(sigma: Double, seed: Long = 9, at: (Int, Int) -> FloatArray): FloatArray {
        val random = Random(seed)
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val c = at(x, y)
            for (k in 0..2) out[(y * w + x) * 3 + k] = (c[k] + random.nextGaussian() * sigma).toFloat()
        }
        return out
    }

    private fun luma(p: FloatArray, i: Int) = 0.2126f * p[i * 3] + 0.7152f * p[i * 3 + 1] + 0.0722f * p[i * 3 + 2]
    private fun cb(p: FloatArray, i: Int) = (p[i * 3 + 2] - luma(p, i)) / 1.8556f

    /** Standard deviation of [f] over the inner pixels (away from the clamped borders) where [keep] holds. */
    private fun std(p: FloatArray, keep: (Int, Int) -> Boolean = { _, _ -> true }, f: (FloatArray, Int) -> Float): Double {
        val values = (4 until h - 4).flatMap { y -> (4 until w - 4).filter { x -> keep(x, y) }.map { x -> f(p, y * w + x).toDouble() } }
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private val grey = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val medium = SpatialNoiseReduction(lumaThreshold = 40f, chromaThreshold = 40f)

    @Test
    fun `off, or with both thresholds at 0, the picture comes back as it went in`() {
        val p = picture(0.03) { _, _ -> grey }
        assertArrayEquals(p, SpatialNrKernel.filter(p, w, h, SpatialNoiseReduction.OFF), 0f)
        assertArrayEquals(p, SpatialNrKernel.filter(p, w, h, medium.copy(lumaThreshold = 0f, chromaThreshold = 0f)), 0f)
        assertArrayEquals(p, SpatialNrKernel.filter(p, w, h, medium.copy(amount = 0f)), 0f)
        assertNull(SpatialNrKernel.uniforms(SpatialNoiseReduction.OFF))
        assertFalse(medium.copy(amount = 0f).isActive)
        assertTrue(medium.copy(chromaThreshold = 0f).isActive)
    }

    @Test
    fun `noise on a flat picture is smoothed several times over, its level unchanged`() {
        // Noise of 6 levels (8-bit) on mid grey.
        val p = picture(6.0 / 255) { _, _ -> grey }
        val out = SpatialNrKernel.filter(p, w, h, medium)
        val before = std(p) { q, i -> luma(q, i) }
        val after = std(out) { q, i -> luma(q, i) }
        assertTrue("luma noise $before -> $after", after < before / 2.5)
        assertTrue("chroma noise", std(out) { q, i -> cb(q, i) } < std(p) { q, i -> cb(q, i) } / 2.5)
        val mean = (0 until w * h).map { luma(out, it) }.average()
        assertEquals("the level stays", 0.5, mean, 0.003)
    }

    @Test
    fun `an edge past the threshold stays sharp while the noise either side of it goes`() {
        // Dark (0.15) left of x = 24, light (0.8) from it: a jump of 166 levels, noise of 6.
        val p = picture(6.0 / 255) { x, _ -> if (x < 24) floatArrayOf(0.15f, 0.15f, 0.15f) else floatArrayOf(0.8f, 0.8f, 0.8f) }
        val out = SpatialNrKernel.filter(p, w, h, medium.copy(radius = NrRadius.LARGE))
        for (y in 4 until h - 4) {
            assertTrue("row $y left of the edge", luma(out, y * w + 23) < 0.2f)
            assertTrue("row $y right of the edge", luma(out, y * w + 24) > 0.75f)
        }
        val left = { x: Int, _: Int -> x < 20 }
        assertTrue(std(out, left) { q, i -> luma(q, i) } < std(p, left) { q, i -> luma(q, i) } / 2.5)
    }

    @Test
    fun `chroma alone smooths the colour and leaves the luma exactly as it was`() {
        val orange = floatArrayOf(0.85f, 0.5f, 0.2f)
        val p = picture(6.0 / 255) { _, _ -> orange }
        val out = SpatialNrKernel.filter(p, w, h, SpatialNoiseReduction(lumaThreshold = 0f, chromaThreshold = 50f))
        for (i in 0 until w * h) assertEquals("pixel $i luma", luma(p, i), luma(out, i), 1e-5f)
        assertTrue(std(out) { q, i -> cb(q, i) } < std(p) { q, i -> cb(q, i) } / 2.5)
    }

    @Test
    fun `a larger radius smooths more, and the amount mixes the smoothed picture over the original`() {
        val p = picture(6.0 / 255) { _, _ -> grey }
        val noise = { nr: SpatialNoiseReduction -> std(SpatialNrKernel.filter(p, w, h, nr)) { q, i -> luma(q, i) } }
        val small = noise(medium.copy(radius = NrRadius.SMALL))
        val large = noise(medium.copy(radius = NrRadius.LARGE))
        assertTrue("small $small, large $large", large < small * 0.7)
        val full = SpatialNrKernel.filter(p, w, h, medium)
        val half = SpatialNrKernel.filter(p, w, h, medium.copy(amount = 50f))
        for (i in p.indices) assertEquals(p[i] + (full[i] - p[i]) * 0.5f, half[i], 1e-5f)
    }

    @Test
    fun `the thresholds set the range sigmas, and the sliders step in whole values within 0 to 100`() {
        assertEquals(0.06f, SpatialNrKernel.lumaSigma(medium), 1e-6f)
        assertEquals(0.04f, SpatialNrKernel.chromaSigma(medium), 1e-6f)
        assertArrayEquals(floatArrayOf(0.06f, 0.04f, 2f, 1f), SpatialNrKernel.uniforms(medium), 1e-6f)
        assertEquals(100f, SpatialNrControl.LUMA.write(medium, 140f).lumaThreshold, 0f)
        assertEquals(0f, SpatialNrControl.CHROMA.write(medium, -3f).chromaThreshold, 0f)
        assertEquals(37f, SpatialNrControl.AMOUNT.write(medium, 36.6f).amount, 0f)
        assertEquals(1.5f, SpatialNrKernel.spatialSigma(2), 0f)
        assertTrue(abs(SpatialNrKernel.spatialSigma(3) - 2f) < 1e-6f)
    }
}
