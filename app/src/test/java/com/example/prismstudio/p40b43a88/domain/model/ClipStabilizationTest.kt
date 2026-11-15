package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStabilizationTest {

    private val w = 160
    private val h = 90

    /** A textured scene larger than the frame, sampled with bilinear filtering at a sub-pixel offset. */
    private class Scene(seed: Int) {
        val size = 256
        val v = FloatArray(size * size)

        init {
            val r = Random(seed)
            val raw = FloatArray(size * size) { r.nextFloat() }
            // A little blur, so the texture has structure at the analysis scale.
            for (y in 0 until size) for (x in 0 until size) {
                var acc = 0f
                for (k in -1..1) for (j in -1..1) acc += raw[((y + k).mod(size)) * size + (x + j).mod(size)]
                v[y * size + x] = acc / 9f
            }
        }

        fun at(x: Double, y: Double): Float {
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = (x - x0).toFloat()
            val fy = (y - y0).toFloat()
            fun p(a: Int, b: Int) = v[b.mod(size) * size + a.mod(size)]
            return (p(x0, y0) * (1 - fx) + p(x0 + 1, y0) * fx) * (1 - fy) + (p(x0, y0 + 1) * (1 - fx) + p(x0 + 1, y0 + 1) * fx) * fy
        }
    }

    /** The frame showing [scene] with its content moved by ([cx], [cy]) pixels (right and down). */
    private fun frame(scene: Scene, cx: Double, cy: Double) = FloatArray(w * h) { i ->
        scene.at(40.0 + i % w - cx, 40.0 + i / w - cy)
    }

    @Test
    fun `a shift between two frames is found to a tenth of a pixel`() {
        val scene = Scene(7)
        val estimator = MotionEstimator(w, h)
        val a = frame(scene, 0.0, 0.0)
        for ((sx, sy) in listOf(0.0 to 0.0, 3.3 to -1.6, -7.5 to 4.25, 11.2 to -9.7, 0.4 to 0.0)) {
            val (ex, ey) = estimator.shift(a, frame(scene, sx, sy))
            assertEquals("x for $sx", sx, ex.toDouble(), 0.12)
            assertEquals("y for $sy", sy, ey.toDouble(), 0.12)
        }
    }

    @Test
    fun `the camera path adds up the shifts as fractions of the picture`() {
        val scene = Scene(3)
        val moves = listOf(0.0 to 0.0, 2.0 to 1.0, 4.5 to 1.5, 3.0 to -2.0, 6.0 to 0.0)
        val path = MotionEstimator.path(moves.map { (x, y) -> frame(scene, x, y) }, w, h, 30.0)
        assertEquals(5, path.frames)
        moves.forEachIndexed { i, (x, y) ->
            assertEquals(x / w, path.x[i].toDouble(), 0.002)
            assertEquals(y / h, path.y[i].toDouble(), 0.003)
        }
    }

    @Test
    fun `smoothing keeps a steady pan and takes the shake out of it`() {
        val n = 120
        val r = Random(11)
        val pan = FloatArray(n) { it * 0.002f }
        val shake = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.02f }
        val path = CameraPath(30.0, FloatArray(n) { pan[it] + shake[it] }, FloatArray(n))
        val plan = StabilizationPlan.of(ClipStabilization(smooth = 50), path)
        // What the picture shows after the move: the path plus the move — close to the pan, the shake mostly gone.
        var before = 0.0
        var after = 0.0
        for (i in 20 until n - 20) {
            before += abs(path.x[i] - pan[i])
            after += abs(path.x[i] + plan.dx[i] - pan[i])
        }
        assertTrue("shake ${before / (n - 40)} → ${after / (n - 40)}", after < before * 0.25)
        assertTrue("zoom hides the largest move", plan.scale >= 1f + 2f * plan.largestMove - 1e-5f && plan.scale <= ClipStabilization.MAX_ZOOM)
    }

    @Test
    fun `the shake at the clip's ends does not pull the steady path`() {
        val n = 180
        val pan = FloatArray(n) { it * 0.001f }
        // A ±1 % shake, well off zero on the last frame.
        val path = CameraPath(30.0, FloatArray(n) { pan[it] + 0.01f * sin(it * 17.0 / 30).toFloat() }, FloatArray(n))
        val plan = StabilizationPlan.of(ClipStabilization(), path)
        assertTrue("the moves stay near the shake's size: ${plan.largestMove}", plan.largestMove < 0.013f)
        for (i in 0 until n) assertEquals("frame $i shows the pan", pan[i], path.x[i] + plan.dx[i], 0.003f)
    }

    @Test
    fun `strength scales the move and zoom off leaves the scale at one`() {
        val path = CameraPath(30.0, FloatArray(60) { if (it % 2 == 0) 0.01f else -0.01f }, FloatArray(60) { 0.004f * sin(it * 1.3).toFloat() })
        val full = StabilizationPlan.of(ClipStabilization(strength = 100, zoom = false), path)
        val half = StabilizationPlan.of(ClipStabilization(strength = 50, zoom = false), path)
        assertEquals(1f, full.scale)
        for (i in 0 until 60) assertEquals(full.dx[i] / 2, half.dx[i], 1e-6f)
        assertEquals(0f, StabilizationPlan.of(ClipStabilization(strength = 0), path).largestMove, 0f)
    }

    @Test
    fun `a shake too big to hide is taken out only as far as the most zoom hides`() {
        val path = CameraPath(30.0, FloatArray(30) { if (it == 15) 0.4f else 0f }, FloatArray(30))
        val plan = StabilizationPlan.of(ClipStabilization(smooth = 100), path)
        assertEquals(ClipStabilization.MAX_ZOOM, plan.scale)
        assertEquals((ClipStabilization.MAX_ZOOM - 1f) / 2f, plan.largestMove, 1e-5f)
    }

    @Test
    fun `the plan covers the frames the clip shows and eases between them`() {
        val path = CameraPath(25.0, FloatArray(100) { it * 0.001f + if (it % 3 == 0) 0.006f else 0f }, FloatArray(100))
        val whole = StabilizationPlan.of(ClipStabilization(), path)
        val part = StabilizationPlan.of(ClipStabilization(), path, 40, 60)
        assertEquals(20, part.dx.size)
        assertEquals(40, part.firstFrame)
        assertEquals(whole.dx[45], part.dx[5], 1e-6f)
        val (mid, _) = part.offsetAt(45.5 / 25.0)
        assertEquals((part.dx[5] + part.dx[6]) / 2, mid, 1e-6f)
        assertEquals("held before the clip's first frame", part.dx[0], part.offsetAt(0.0).first, 0f)
        assertTrue("less shake in the part, less zoom", part.scale <= whole.scale)
    }

    @Test
    fun `the analysis picture keeps the file's shape`() {
        assertEquals(160 to 90, MotionEstimator.analysisSize(1920, 1080))
        assertEquals(160 to 284, MotionEstimator.analysisSize(1080, 1920))
        assertEquals(160 to 120, MotionEstimator.analysisSize(640, 480))
    }
}
