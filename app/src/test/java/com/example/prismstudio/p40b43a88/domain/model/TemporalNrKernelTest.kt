package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

/** Resolve's Temporal NR: a recursive blend towards the frame before where nothing moved, none where something did. */
class TemporalNrKernelTest {

    @Test
    fun `strength sets the blend and the threshold, and the weight falls off with the difference`() {
        assertNull(TemporalNrKernel.uniforms(0))
        assertArrayEquals(floatArrayOf(0.8f, 0.06f), TemporalNrKernel.uniforms(100), 1e-6f)
        assertArrayEquals(floatArrayOf(0.4f, 0.036f), TemporalNrKernel.uniforms(50), 1e-6f)
        assertEquals(0.8f, TemporalNrKernel.weight(0.8f, 0.06f, 0f), 0f)
        assertEquals(0.8f, TemporalNrKernel.weight(0.8f, 0.06f, -0.03f), 0f)
        assertEquals(0.4f, TemporalNrKernel.weight(0.8f, 0.06f, 0.045f), 1e-6f)
        assertEquals(0f, TemporalNrKernel.weight(0.8f, 0.06f, 0.06f), 0f)
        assertEquals(0f, TemporalNrKernel.weight(0.8f, 0.06f, 0.5f), 0f)
    }

    @Test
    fun `on a still picture the grain settles to about a third, and the first frame passes untouched`() {
        val w = 32
        val h = 32
        val random = Random(7)
        val frames = List(60) { FloatArray(w * h) { (0.5 + 0.04 * random.nextGaussian()).toFloat() } }
        assertArrayEquals(frames[0], TemporalNrKernel.step(frames[0], null, w, h, 100), 0f)
        fun temporalStd(seq: List<FloatArray>): Double {
            var total = 0.0
            for (p in 0 until w * h) {
                val v = seq.map { it[p].toDouble() }
                val mean = v.average()
                total += v.sumOf { (it - mean) * (it - mean) } / v.size
            }
            return sqrt(total / (w * h))
        }
        var previous: FloatArray? = null
        val out = frames.map { f -> TemporalNrKernel.step(f, previous, w, h, 100).also { previous = it } }
        val ratio = temporalStd(out.drop(20)) / temporalStd(frames.drop(20))
        // The full blend alone would leave √(0.2 / 1.8) ≈ 0.33; grain now and then crosses half the threshold.
        assertTrue("ratio $ratio", ratio in 0.3..0.45)
        val half = run {
            var p: FloatArray? = null
            frames.map { f -> TemporalNrKernel.step(f, p, w, h, 50).also { p = it } }
        }
        val halfRatio = temporalStd(half.drop(20)) / temporalStd(frames.drop(20))
        assertTrue("half $halfRatio", halfRatio > ratio + 0.1 && halfRatio < 0.9)
    }

    @Test
    fun `where the picture moves the frame shows as it is, with no trail`() {
        val w = 24
        val h = 8
        val before = FloatArray(w * h) { i -> if (i % w < 8) 0.2f else 0.8f }
        val after = FloatArray(w * h) { i -> if (i % w < 14) 0.2f else 0.8f }
        val out = TemporalNrKernel.step(after, before, w, h, 100)
        // Columns 9–12 changed from bright to dark, their 3 × 3 neighbours too: they show the new frame exactly.
        for (y in 0 until h) for (x in 9..12) assertEquals("x $x", after[y * w + x], out[y * w + x], 0f)
        // Far from the move nothing changed, so the blend leaves it as it was.
        for (y in 0 until h) assertEquals(0.8f, out[y * w + 20], 0f)
    }
}
