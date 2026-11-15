package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Mid/Detail as the shader draws it: midtone local contrast up (positive) or smoothed (negative). */
class MidDetailKernelTest {

    /** Vertical stripes 4 px wide, grey [low] and [high], 32 × 8. */
    private fun stripes(low: Float, high: Float): FloatArray {
        val w = 32
        val h = 8
        return FloatArray(w * h * 3) { i -> if (((i / 3) % w / 4) % 2 == 0) low else high }
    }

    /** The light stripes' mean less the dark stripes' (by where the stripes are in [stripes]). */
    private fun spread(rgb: FloatArray): Float {
        val light = (0 until rgb.size / 3).filter { ((it % 32) / 4) % 2 == 1 }.map { rgb[it * 3] }
        val dark = (0 until rgb.size / 3).filter { ((it % 32) / 4) % 2 == 0 }.map { rgb[it * 3] }
        return light.average().toFloat() - dark.average().toFloat()
    }

    @Test
    fun `off changes nothing and a flat picture stays flat whatever the amount`() {
        val pic = stripes(0.4f, 0.6f)
        assertArrayEquals(pic, MidDetailKernel.filter(pic, 32, 8, 0f, 2), 0f)
        val flat = FloatArray(32 * 8 * 3) { 0.5f }
        assertArrayEquals(flat, MidDetailKernel.filter(flat, 32, 8, 100f, 2), 1e-6f)
        assertArrayEquals(flat, MidDetailKernel.filter(flat, 32, 8, -100f, 2), 1e-6f)
    }

    @Test
    fun `positive amounts spread midtone texture, negative ones pull it towards the local mean`() {
        val pic = stripes(0.4f, 0.6f)
        val before = spread(pic)
        val up = spread(MidDetailKernel.filter(pic, 32, 8, 100f, 2))
        val down = spread(MidDetailKernel.filter(pic, 32, 8, -100f, 2))
        assertTrue("+100 widens the stripes' difference ($before → $up)", up > before * 1.5f)
        assertTrue("−100 narrows it towards the local mean ($before → $down)", down < before * 0.7f && down > 0f)
        assertTrue("+50 lies between", spread(MidDetailKernel.filter(pic, 32, 8, 50f, 2)) in before..up)
    }

    @Test
    fun `softening fine regular texture keeps light stripes lighter than dark ones`() {
        // 6 px stripes with taps 3 px apart: a plain average would flip them; the binomial weights do not.
        val w = 48
        val pic = FloatArray(w * 8 * 3) { i -> if (((i / 3) % w / 6) % 2 == 0) 100f / 255 else 150f / 255 }
        val soft = MidDetailKernel.filter(pic, w, 8, -100f, 3)
        val light = (0 until w).filter { pic[(4 * w + it) * 3] > 0.5f }.map { soft[(4 * w + it) * 3] }
        val dark = (0 until w).filter { pic[(4 * w + it) * 3] < 0.5f }.map { soft[(4 * w + it) * 3] }
        assertTrue("light ${light.min()} > dark ${dark.max()}", light.min() > dark.max())
    }

    @Test
    fun `black and white are left alone, the midtones move most`() {
        val pic = stripes(0f, 1f)
        assertArrayEquals(pic, MidDetailKernel.filter(pic, 32, 8, 100f, 2), 1e-6f)
        val mid = spread(MidDetailKernel.filter(stripes(0.45f, 0.55f), 32, 8, 100f, 2)) / 0.1f
        val dark = spread(MidDetailKernel.filter(stripes(0.05f, 0.15f), 32, 8, 100f, 2)) / 0.1f
        assertTrue("midtones gain more than shadows ($mid vs $dark)", mid > dark)
    }

    @Test
    fun `the shader is given the amount as a fraction`() {
        assertEquals(0.5f, MidDetailKernel.uniform(50f), 0f)
        assertEquals(-1f, MidDetailKernel.uniform(-140f), 0f)
    }
}
