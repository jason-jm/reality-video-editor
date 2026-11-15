package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowTrackUseCaseTest {
    private val tracker = WindowTrackUseCase()
    private val w = 320
    private val h = 180

    /** Where the subject is in frame [i]: a loop across the picture, in pixels. */
    private fun subject(i: Int) = (100.0 + 120 * sin(i / 40.0)) to (90.0 + 50 * cos(i / 25.0))

    /** A textured background (still) with a bright soft-edged disc of radius 12 px moving along [subject]. */
    private fun frames(n: Int, first: Int = 0): LumaFrames {
        val r = Random(5)
        val background = FloatArray(w * h) { 60f + 40f * r.nextFloat() }
        return LumaFrames(w, h, 30.0, first, List(n) { i ->
            val (sx, sy) = subject(first + i)
            ByteArray(w * h) { k ->
                val x = k % w
                val y = k / w
                val d2 = (x - sx) * (x - sx) + (y - sy) * (y - sy)
                // The disc carries a texture of its own (moving with it), so its middle can be lined up too.
                val disc = 200.0 * exp(-d2 / (2 * 8.0 * 8.0)) * (0.8 + 0.2 * sin((x - sx) * 0.9) * cos((y - sy) * 1.1))
                (background[k] + disc).coerceIn(0.0, 255.0).toInt().toByte()
            }
        })
    }

    @Test
    fun `the patch is followed forward and back from where tracking starts`() {
        val f = frames(120)
        val (x, y) = subject(40)
        val path = tracker.track(f, 40, x.toFloat(), y.toFloat(), 10, 10)
        var worst = 0.0
        for (i in 0 until 120) {
            val (ex, ey) = subject(i)
            worst = maxOf(worst, abs(path[2 * i] - ex), abs(path[2 * i + 1] - ey))
        }
        assertTrue("worst error $worst px", worst < 1.0)
    }

    @Test
    fun `the window is keyed along the tracked path, as few keyframes as stay close`() {
        val f = frames(150)
        // The clip shows file frames 30…129 (1 s in, 100 frames); the window sits on the subject at clip frame 20.
        val clip = UiFixtures.clip("vA", start = 0, frames = 100, assetId = "v1").copy(sourceInMs = 1000)
        val (sx, sy) = subject(50)
        val window = PowerWindow(shape = WindowShape.CIRCLE, centerX = (sx / (w - 1) * 2 - 1).toFloat(), centerY = (1 - sy / (h - 1) * 2).toFloat(),
            size = 0.15f, grade = ColorGrade.NEUTRAL.copy(exposure = 1f))
        val keys = tracker.keyframes(window, clip, 20, f, 30)
        assertEquals(0, keys.first().frame)
        assertEquals(99, keys.last().frame)
        assertTrue("${keys.size} keyframes for 100 frames", keys.size in 4..40)
        val keyed = window.copy(keyframes = keys)
        for (frame in listOf(0, 20, 37, 64, 99)) {
            val (ex, ey) = subject(30 + frame)
            val at = keyed.at(frame)
            val (px, py) = tracker.toPixels(at.centerX, at.centerY, f)
            assertEquals("x at $frame", ex, px.toDouble(), 1.5)
            assertEquals("y at $frame", ey, py.toDouble(), 1.5)
            assertEquals("the size is kept", 0.15f, at.size, 1e-6f)
        }
    }

    @Test
    fun `a mask tracks on footage played forward, with a shape, not too long`() {
        val asset = com.example.prismstudio.p40b43a88.domain.assetOf("v1")
        val clip = UiFixtures.clip("vA", start = 0, frames = 100, assetId = "v1")
        val masked = clip.copy(mask = PowerWindow(shape = WindowShape.CIRCLE))
        assertEquals("先为片段选择遮罩形状", tracker.maskUnavailable(clip, asset))
        assertNull(tracker.maskUnavailable(masked, asset))
        assertEquals("倒放的片段不能跟踪遮罩", tracker.maskUnavailable(masked.copy(reverse = true), asset))
        assertEquals("只有视频片段可以跟踪遮罩", tracker.maskUnavailable(masked, asset.copy(kind = com.example.prismstudio.p40b43a88.domain.model.MediaKind.IMAGE)))
        assertEquals("片段太长：先切短再跟踪", tracker.maskUnavailable(masked.copy(durationFrames = WindowTrackUseCase.MAX_FRAMES + 1), asset))
    }

    @Test
    fun `simplifying keeps the ends and the corners of the path`() {
        val cx = FloatArray(61) { i -> if (i <= 30) i * 0.01f else 0.3f - (i - 30) * 0.01f }
        val cy = FloatArray(61)
        assertEquals(listOf(0, 30, 60), tracker.simplify(cx, cy))
        assertEquals(listOf(0, 1), tracker.simplify(FloatArray(2), FloatArray(2)))
    }

    @Test
    fun `only a shaped window on footage played forward can be tracked`() {
        val footage = UiFixtures.video("v1")
        val clip = UiFixtures.clip("vA", start = 0, frames = 30, assetId = "v1")
        val windowed = clip.copy(grade = clip.grade.copy(window = PowerWindow()))
        assertNull(tracker.unavailable(windowed, footage))
        assertEquals("先画一个窗口，再跟踪", tracker.unavailable(clip, footage))
        assertEquals("全画面不需要跟踪：先选择圆形、矩形或渐变",
            tracker.unavailable(clip.copy(grade = clip.grade.copy(window = PowerWindow(shape = WindowShape.FULL))), footage))
        assertEquals("倒放的片段不能跟踪窗口", tracker.unavailable(windowed.copy(reverse = true), footage))
        assertEquals("只有视频片段可以跟踪窗口", tracker.unavailable(windowed, UiFixtures.image("im")))
    }
}
