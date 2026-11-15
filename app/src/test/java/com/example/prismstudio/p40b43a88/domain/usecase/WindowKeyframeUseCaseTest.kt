package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowGeometry
import com.example.prismstudio.p40b43a88.domain.model.WindowKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.geometry
import com.example.prismstudio.p40b43a88.domain.model.retimedKeys
import com.example.prismstudio.p40b43a88.domain.model.windowAt
import com.example.prismstudio.p40b43a88.domain.model.frozenAt
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic keyframes of the power window: editing them, and how they follow the clip through edits. */
class WindowKeyframeUseCaseTest {

    private val keys = WindowKeyframeUseCase()

    private fun geometry(x: Float, size: Float = 0.5f, angle: Float = 0f) = WindowGeometry(x, 0f, size, 1f, angle, 0.2f)

    /** A circle moving from the left (frame 10) to the right (frame 50). */
    private val moving = PowerWindow(
        keyframes = listOf(WindowKeyframe(10, geometry(-0.5f)), WindowKeyframe(50, geometry(0.5f, size = 0.9f, angle = 40f))),
    )

    private fun rejection(block: () -> Unit): String? = assertThrows(EditRejected::class.java) { block() }.message

    /** The same geometry, give or take float rounding. */
    private fun assertSame(expected: WindowGeometry, actual: WindowGeometry) {
        val e = listOf(expected.centerX, expected.centerY, expected.size, expected.aspect, expected.angle, expected.softness)
        val a = listOf(actual.centerX, actual.centerY, actual.size, actual.aspect, actual.angle, actual.softness)
        e.zip(a).forEach { (x, y) -> assertEquals("$expected vs $actual", x, y, 1e-4f) }
    }

    @Test
    fun `the window moves between keyframes and holds outside them`() {
        assertEquals(-0.5f, moving.at(0).centerX, 0f)
        assertEquals(0f, moving.at(30).centerX, 1e-6f)
        assertEquals(0.7f, moving.at(30).size, 1e-6f)
        assertEquals(20f, moving.at(30).angle, 1e-5f)
        assertEquals(0.5f, moving.at(80).centerX, 0f)
        assertEquals("only the geometry animates", WindowShape.CIRCLE, moving.at(30).shape)

        val eased = moving.copy(keyframes = moving.keyframes.mapIndexed { i, k -> if (i == 1) k.copy(ease = KeyEase(slopeIn = 0f)) else k })
        assertTrue("easing in, it is further along half way", eased.at(30).centerX > 0f)
        assertEquals(0.5f, eased.at(50).centerX, 0f)

        val grade = ColorGrade(window = moving)
        assertEquals(moving.at(30).copy(keyframes = moving.keyframes), grade.windowAt(30).window)
        assertEquals("without keyframes nothing changes", ColorGrade(window = PowerWindow()), ColorGrade(window = PowerWindow()).windowAt(30))
    }

    @Test
    fun `a keyframe is added where the window stands, and the last one removed leaves it there`() {
        val first = keys.toggle(PowerWindow(centerX = 0.2f), frame = 12, clipFrames = 90)
        assertEquals(listOf(WindowKeyframe(12, PowerWindow(centerX = 0.2f).geometry)), first.window.keyframes)
        assertEquals("已添加窗口关键帧 · 共 1 个", first.message)

        val added = keys.toggle(moving, frame = 30, clipFrames = 90)
        assertEquals(listOf(10, 30, 50), added.window.keyframes.map { it.frame })
        assertEquals("the motion does not change", moving.at(20).geometry, added.window.at(20).geometry)
        assertEquals("已添加窗口关键帧 · 共 3 个", added.message)

        val removed = keys.toggle(added.window, frame = 30, clipFrames = 90)
        assertEquals(moving.keyframes, removed.window.keyframes)
        assertEquals("已删除窗口关键帧 · 还剩 2 个", removed.message)

        val last = keys.toggle(keys.toggle(moving, 10, 90).window, 50, 90)
        assertTrue(last.window.keyframes.isEmpty())
        assertEquals("the window stays where the last keyframe held it", geometry(0.5f, size = 0.9f, angle = 40f), last.window.geometry)
        assertEquals("已删除窗口关键帧，窗口不再移动", last.message)
    }

    @Test
    fun `keyframes need the playhead inside the clip and a window with a shape`() {
        assertEquals("把播放头移到片段内，再添加窗口关键帧", rejection { keys.toggle(moving, frame = 90, clipFrames = 90) })
        assertEquals("把播放头移到片段内，再添加窗口关键帧", rejection { keys.toggle(moving, frame = -1, clipFrames = 90) })
        assertEquals("先选择窗口形状，再添加关键帧", rejection { keys.toggle(PowerWindow(shape = WindowShape.FULL), 0, 90) })
        assertEquals("播放头不在片段内：移进片段再调整窗口", rejection { keys.adjust(moving, 95, 90) { it.copy(size = 1f) } })
    }

    @Test
    fun `adjusting a keyed window records the change at the playhead`() {
        val still = keys.adjust(PowerWindow(), 40, 90) { it.copy(centerX = 0.3f) }
        assertEquals("without keyframes the whole window moves", PowerWindow(centerX = 0.3f), still)

        val atKey = keys.adjust(moving, 50, 90) { it.copy(centerX = it.centerX + 0.1f) }
        assertEquals(listOf(10, 50), atKey.keyframes.map { it.frame })
        assertEquals(0.6f, atKey.at(50).centerX, 1e-6f)
        assertEquals(geometry(-0.5f), atKey.keyframes[0].geometry)

        val between = keys.adjust(moving, 30, 90) { it.copy(size = 0.2f) }
        assertEquals("a keyframe is added at the playhead", listOf(10, 30, 50), between.keyframes.map { it.frame })
        assertEquals(0.2f, between.at(30).size, 0f)
        assertEquals("it moved from where the window was", 0f, between.at(30).centerX, 1e-6f)
        assertEquals(geometry(0.5f, size = 0.9f, angle = 40f), between.keyframes[2].geometry)
    }

    @Test
    fun `easing needs motion on that side, and the buttons find the neighbouring keyframes`() {
        val eased = keys.ease(moving, frame = 50, arriving = true)
        assertEquals(KeyEase(slopeIn = 0f), eased.window.keyframes[1].ease)
        assertEquals("已开启缓入 · 到达此关键帧时逐渐减速", eased.message)
        assertEquals(KeyEase.LINEAR, keys.ease(eased.window, 50, arriving = true).window.keyframes[1].ease)
        assertEquals("最后一个关键帧之后没有变化，无需缓出", rejection { keys.ease(moving, 50, arriving = false) })
        assertEquals("把播放头移到窗口关键帧上，再设置缓入缓出", rejection { keys.ease(moving, 30, arriving = true) })

        assertEquals(10, keys.previous(moving, 30))
        assertEquals(null, keys.previous(moving, 10))
        assertEquals(50, keys.next(moving, 10))
        assertEquals(null, keys.next(moving, 50))
    }

    @Test
    fun `splitting, slowing and freezing a clip keep the window where it was on every frame`() {
        val clip = clipOf("A", 0, 90).copy(grade = ColorGrade(window = moving))
        val result = SplitClipUseCase(SequentialIds())(timelineOf(clip), atFrame = 30, selectedClipId = "A", fps = 30)
        val (left, right) = result.timeline.clips
        for (f in 0 until 30) assertSame(moving.at(f).geometry, left.grade.window!!.at(f).geometry)
        for (f in 30 until 90) assertSame(moving.at(f).geometry, right.grade.window!!.at(f - 30).geometry)
        assertEquals(listOf(0, 20), right.grade.window!!.keyframes.map { it.frame })

        val slower = ColorGrade(window = moving).retimedKeys(oldFrames = 90, newFrames = 180)
        assertEquals(listOf(20, 100), slower.window!!.keyframes.map { it.frame })

        val held = ColorGrade(window = moving).frozenAt(30)
        assertTrue(held.window!!.keyframes.isEmpty())
        assertEquals(moving.at(30).geometry, held.window!!.geometry)
    }
}
