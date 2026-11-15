package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's tracker Zoom and Rotate: the shape's size and angle follow a subject that grows and turns, as well as moves. */
class WindowTrackMotionTest {
    private val tracker = WindowTrackUseCase()
    private val w = 320
    private val h = 180

    /** The subject in frame [i]: centre (pixels), size against frame 0, and its turn (degrees, counter-clockwise on screen). */
    private data class Pose(val x: Double, val y: Double, val scale: Double, val degrees: Double)

    private fun moving(i: Int, n: Int): Pose {
        val t = i.toDouble() / (n - 1)
        return Pose(120.0 + 60 * t, 95.0 - 20 * t, 1.0 + 0.4 * t, 30.0 * t)
    }

    /**
     * A still noisy background and a subject of radius 34 px (at scale 1) whose own texture has no symmetry — bars, a
     * bright quarter and a dark spot — posed by [pose] in each frame. Each pixel averages 3 × 3 samples, as a camera's
     * picture is smooth: point-sampled hard edges shift by up to half a pixel as the subject grows, which reads as a
     * smaller size.
     */
    private fun frames(n: Int, pose: (Int) -> Pose): LumaFrames {
        val r = Random(11)
        val background = FloatArray(w * h) { 70f + 50f * r.nextFloat() }
        return LumaFrames(w, h, 30.0, 0, List(n) { i ->
            val p = pose(i)
            val rad = p.degrees * PI / 180
            val c = cos(rad)
            val s = sin(rad)
            ByteArray(w * h) { k ->
                var acc = 0.0
                for (sy in -1..1) for (sx in -1..1) {
                    val px = k % w + sx / 3.0 - p.x
                    val py = k / w + sy / 3.0 - p.y
                    // Into the subject's own axes: undo the turn (counter-clockwise on screen = clockwise in y-down
                    // pixels) and the size.
                    val lx = (px * c - py * s) / p.scale
                    val ly = (px * s + py * c) / p.scale
                    acc += if (hypot(lx, ly) > 34) background[k].toDouble() else {
                        var t = 130 + 45 * sin(lx / 3.5) + 35 * cos((lx + 2 * ly) / 5.0)
                        if (lx > 4 && ly < -4) t += 50
                        if (hypot(lx + 12, ly - 14) < 7) t -= 80
                        t
                    }
                }
                (acc / 9).coerceIn(0.0, 255.0).toInt().toByte()
            }
        })
    }

    private fun windowOn(p: Pose, size: Float, angle: Float = 0f) = PowerWindow(
        shape = WindowShape.RECTANGLE,
        centerX = (p.x / (w - 1) * 2 - 1).toFloat(),
        centerY = (1 - p.y / (h - 1) * 2).toFloat(),
        size = size,
        angle = angle,
    )

    @Test
    fun `with Zoom and Rotate the window grows and turns with the subject, keyed from where it stood`() {
        val n = 60
        val f = frames(n) { moving(it, n) }
        val clip = UiFixtures.clip("vA", start = 0, frames = n, assetId = "v1")
        // The window over the subject's middle at clip frame 0, turned 10° already: the turn adds to that.
        val window = windowOn(moving(0, n), 0.3f, angle = 10f)
        val keys = tracker.keyframes(window, clip, 0, f, 30, TrackMotion.ALL)
        val keyed = window.copy(keyframes = keys)
        for (frame in listOf(0, 15, 30, 45, 59)) {
            val p = moving(frame, n)
            val at = keyed.at(frame)
            val (px, py) = tracker.toPixels(at.centerX, at.centerY, f)
            assertEquals("x at $frame", p.x, px.toDouble(), 1.5)
            assertEquals("y at $frame", p.y, py.toDouble(), 1.5)
            assertEquals("size at $frame", 0.3 * p.scale, at.size.toDouble(), 0.3 * 0.03)
            assertEquals("angle at $frame", 10 + p.degrees, at.angle.toDouble(), 2.0)
        }
        // Every change here is steady, so few keys hold it.
        assertTrue("${keys.size} keyframes", keys.size in 2..12)
        val note = tracker.motionNote(keys, TrackMotion.ALL)
        assertTrue(note, Regex("，大小最多变化 (39|40|41|42)%，最多转动 (29|30|31)°").matches(note))
        val grew = keys.maxOf { it.geometry.size } / keys.minOf { it.geometry.size }
        assertEquals(1.4, grew.toDouble(), 0.05)
    }

    @Test
    fun `tracked backwards from a later frame it shrinks and turns back`() {
        val n = 60
        val f = frames(n) { moving(it, n) }
        val clip = UiFixtures.clip("vA", start = 0, frames = n, assetId = "v1")
        val p40 = moving(40, n)
        val window = windowOn(p40, (0.3 * p40.scale).toFloat())
        val keyed = window.copy(keyframes = tracker.keyframes(window, clip, 40, f, 30, TrackMotion.ALL))
        val at0 = keyed.at(0)
        assertEquals("size at 0", 0.3, at0.size.toDouble(), 0.3 * 0.03)
        assertEquals("angle at 0", -p40.degrees, at0.angle.toDouble(), 2.0)
        assertEquals("size at 40, where it started", 0.3 * p40.scale, keyed.at(40).size.toDouble(), 0.3 * 0.02)
    }

    @Test
    fun `Zoom alone leaves the angle, Rotate alone the size, and a subject that only moves keys neither`() {
        val n = 60
        val clip = UiFixtures.clip("vA", start = 0, frames = n, assetId = "v1")
        val window = windowOn(moving(0, n), 0.3f, angle = 10f)
        // Each option on a subject that does just that (one that also turned would not match a patch that cannot turn).
        val growing = frames(n) { i -> moving(i, n).copy(degrees = 0.0) }
        val zoomed = window.copy(keyframes = tracker.keyframes(window, clip, 0, growing, 30, TrackMotion(zoom = true)))
        assertEquals(10f, zoomed.at(59).angle, 1e-6f)
        assertEquals(0.3 * 1.4, zoomed.at(59).size.toDouble(), 0.3 * 0.03)
        val turning = frames(n) { i -> moving(i, n).copy(scale = 1.0) }
        val turned = window.copy(keyframes = tracker.keyframes(window, clip, 0, turning, 30, TrackMotion(rotate = true)))
        assertEquals(0.3f, turned.at(59).size, 1e-6f)
        assertEquals(40.0, turned.at(59).angle.toDouble(), 2.0)

        // Only moving: the size and angle read as unchanged (below the noise floors), as Pan / Tilt alone would key them.
        val still = frames(n) { i -> moving(i, n).copy(scale = 1.0, degrees = 0.0) }
        val keys = tracker.keyframes(window, clip, 0, still, 30, TrackMotion.ALL)
        assertTrue(keys.all { it.geometry.size == 0.3f && it.geometry.angle == 10f })
        assertEquals("", tracker.motionNote(keys, TrackMotion.ALL))
        val plain = tracker.keyframes(window, clip, 0, still, 30)
        assertEquals("as many keys as position alone needs", plain.size.toDouble(), keys.size.toDouble(), 2.0)
    }

    @Test
    fun `size and angle keep a key wherever a straight line between keys would stray`() {
        val n = 41
        val cx = FloatArray(n)
        val cy = FloatArray(n)
        // A size that rises then falls (a key at the top), and an angle that holds then turns (a key where it starts).
        val sizes = FloatArray(n) { 0.5f + 0.1f * (1 - abs(it - 20) / 20f) }
        val angles = FloatArray(n) { if (it < 10) 0f else (it - 10) * 2f }
        assertEquals(listOf(0, 40), tracker.simplify(cx, cy))
        assertEquals(listOf(0, 20, 40), tracker.simplify(cx, cy, sizes = sizes))
        assertEquals(listOf(0, 10, 40), tracker.simplify(cx, cy, angles = angles))
    }
}
