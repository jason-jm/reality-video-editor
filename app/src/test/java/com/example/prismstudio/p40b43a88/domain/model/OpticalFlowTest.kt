package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Optical Flow retime: motion found between two frames, the in-between frame made along it instead of a cross-fade. */
class OpticalFlowTest {

    private val w = 128
    private val h = 96

    /** Smooth, non-repeating noise (0…1): a value per 8 px cell from an integer hash, bilinear between cells. */
    private fun noise(x: Float, y: Float, seed: Int): Float {
        fun hash(i: Int, j: Int): Float {
            var v = i * 374761393 + j * 668265263 + seed * 1442695041
            v = (v xor (v ushr 13)) * 1274126177
            return ((v xor (v ushr 16)) and 0xFFFF) / 65535f
        }
        val gx = x / 8f
        val gy = y / 8f
        val i = kotlin.math.floor(gx).toInt()
        val j = kotlin.math.floor(gy).toInt()
        val fx = gx - i
        val fy = gy - j
        val top = hash(i, j) + (hash(i + 1, j) - hash(i, j)) * fx
        val bottom = hash(i, j + 1) + (hash(i + 1, j + 1) - hash(i, j + 1)) * fx
        return top + (bottom - top) * fy
    }

    /**
     * A steady background of soft noise (0.30–0.50) with a brighter 32 px square of its own noise (0.55–0.95) whose top-left
     * corner is at [squareX], [squareY]: the square's texture moves with it.
     */
    private fun frame(squareX: Int, squareY: Int = 32, gain: Float = 1f): FramePixels = FramePixels(w, h, IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val inside = x in squareX until squareX + 32 && y in squareY until squareY + 32
        val v = if (inside) 0.55f + 0.4f * noise((x - squareX).toFloat(), (y - squareY).toFloat(), 7) else 0.3f + 0.2f * noise(x.toFloat(), y.toFloat(), 3)
        val c = ((v * gain) * 255f).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    })

    private fun mae(a: FramePixels, b: FramePixels): Float =
        a.pixels.indices.map { abs((a.pixels[it] and 0xFF) - (b.pixels[it] and 0xFF)) / 255f }.average().toFloat()

    private fun blend(a: FramePixels, b: FramePixels, t: Float) = FramePixels(w, h, IntArray(w * h) { i ->
        val c = (((a.pixels[i] and 0xFF) * (1 - t) + (b.pixels[i] and 0xFF) * t) + 0.5f).toInt()
        (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    })

    @Test
    fun `the motion of a moving square is found, the still background stays still`() {
        val flow = OpticalFlow.estimate(frame(40), frame(56))
        assertEquals(OpticalFlow.FINE, flow.scale)
        assertEquals(w / 4, flow.width)
        val (sx, sy) = flow.at(56f, 48f)
        assertEquals("the square moved 16 px right", 16f, sx, 3f)
        assertEquals(0f, sy, 2f)
        val (bx, by) = flow.at(8f, 8f)
        assertEquals("a corner of the background", 0f, bx, 1f)
        assertEquals(0f, by, 1f)
        // Up and down too.
        val (_, vy) = OpticalFlow.estimate(frame(48, squareY = 24), frame(48, squareY = 36)).at(64f, 46f)
        assertEquals(12f, vy, 3f)
    }

    @Test
    fun `the in-between frame shows one square halfway, where a cross-fade shows two faint ones`() {
        val a = frame(40)
        val b = frame(56)
        val flow = OpticalFlow.estimate(a, b)
        val truth = frame(48)
        val made = OpticalFlow.interpolate(a, b, flow, 0.5f)
        val flowError = mae(made, truth)
        val blendError = mae(blend(a, b, 0.5f), truth)
        println("optical flow ${"%.4f".format(flowError)} vs frame blend ${"%.4f".format(blendError)}")
        assertTrue("optical flow ($flowError) much closer to the true frame than frame blend ($blendError)", flowError < blendError * 0.5f)
        // A quarter of the way: the square 4 px on.
        val quarter = OpticalFlow.interpolate(a, b, flow, 0.25f)
        assertTrue(mae(quarter, frame(44)) < mae(blend(a, b, 0.25f), frame(44)) * 0.6f)
    }

    @Test
    fun `still frames and a fade pass through as a cross-fade, the ends are the frames themselves`() {
        val a = frame(40)
        val still = OpticalFlow.estimate(a, a)
        assertTrue(still.dx.all { abs(it) < 0.5f } && still.dy.all { abs(it) < 0.5f })
        assertEquals(0f, mae(OpticalFlow.interpolate(a, a, still, 0.37f), a), 0.004f)
        // Brighter overall, nothing moving: no motion, a plain cross-fade.
        val brighter = frame(40, gain = 1.2f)
        val fade = OpticalFlow.estimate(a, brighter)
        assertTrue("a fade is not motion", fade.dx.all { abs(it) < 2f })
        assertEquals(0f, mae(OpticalFlow.interpolate(a, brighter, fade, 0.5f), blend(a, brighter, 0.5f)), 0.01f)
        // t = 0 is the first frame, t = 1 the second.
        val b = frame(56)
        val flow = OpticalFlow.estimate(a, b)
        assertEquals(0f, mae(OpticalFlow.interpolate(a, b, flow, 0f), a), 0.01f)
        assertEquals(0f, mae(OpticalFlow.interpolate(a, b, flow, 1f), b), 0.01f)
    }

    @Test
    fun `planes sample like a GPU and shrink by block means`() {
        val p = OpticalFlow.Plane(2, 1, floatArrayOf(0f, 1f))
        assertEquals(0f, p.sample(0.5f, 0.5f), 0f)
        assertEquals(0.5f, p.sample(1f, 0.5f), 1e-6f)
        assertEquals("clamped past the edge", 1f, p.sample(5f, 0.5f), 0f)
        val q = OpticalFlow.downsample(OpticalFlow.Plane(4, 2, floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)), 2)
        assertEquals(listOf(2.5f, 4.5f), q.values.toList())
        assertEquals(0.5f, OpticalFlow.smoothstep(0f, 1f, 0.5f), 0f)
    }
}
