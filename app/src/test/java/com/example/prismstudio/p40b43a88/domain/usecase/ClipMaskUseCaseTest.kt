package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.frozenAt
import com.example.prismstudio.p40b43a88.domain.model.keyframesMoved
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A picture's mask: the picture inside a shape, the tracks beneath outside it — Resolve's window on the alpha output. */
class ClipMaskUseCaseTest {

    private val masks = ClipMaskUseCase()

    private fun Timeline.a() = clip("A")!!

    private fun rejection(block: () -> Unit) = assertThrows(EditRejected::class.java) { block() }.message

    /** A 100-frame picture with a circle mask keyed from the left (frame 10) to the right (frame 70). */
    private fun keyed(): Timeline {
        var t = masks.shape(timelineOf(clipOf("A", 0, 100)), "A", WindowShape.CIRCLE)!!.timeline
        t = masks.adjust(t, "A", 10) { it.copy(centerX = -0.5f) }
        t = masks.toggleKey(t, "A", 10).timeline
        return masks.adjust(t, "A", 70) { it.copy(centerX = 0.5f) }
    }

    @Test
    fun `a shape adds a mask, another reshapes it keeping its geometry, none removes it`() {
        val added = masks.shape(timelineOf(clipOf("A", 0, 100)), "A", WindowShape.CIRCLE)!!
        assertEquals("已添加圆形遮罩：形状之外露出下层轨道", added.message)
        assertEquals(ClipMaskUseCase.newMask(WindowShape.CIRCLE), added.timeline.a().mask)
        val moved = masks.adjust(added.timeline, "A", 0) { WindowControl.CENTER_X.write(it, 0.4f) }
        val rect = masks.shape(moved, "A", WindowShape.RECTANGLE)!!
        assertEquals("遮罩形状：矩形", rect.message)
        assertEquals(WindowShape.RECTANGLE, rect.timeline.a().mask!!.shape)
        assertEquals("the geometry stays", 0.4f, rect.timeline.a().mask!!.centerX, 0f)
        assertNull("the same shape changes nothing", masks.shape(rect.timeline, "A", WindowShape.RECTANGLE))
        val gone = masks.shape(rect.timeline, "A", null)!!
        assertEquals("已移除遮罩", gone.message)
        assertNull(gone.timeline.a().mask)
        assertNull("nothing to remove", masks.shape(gone.timeline, "A", null))
        // A new rectangle takes the frame's shape; a new gradient a wide soft edge.
        assertEquals(16f / 9f, ClipMaskUseCase.newMask(WindowShape.RECTANGLE).aspect, 1e-6f)
        assertEquals(0.3f, ClipMaskUseCase.newMask(WindowShape.GRADIENT).softness, 0f)
    }

    @Test
    fun `only pictures on unlocked tracks take a mask, and it needs a shape`() {
        assertEquals("只有画面片段可以加遮罩", rejection { masks.shape(timelineOf(titleClipOf("T", 0, 30)), "T", WindowShape.CIRCLE) })
        assertEquals("只有画面片段可以加遮罩", rejection { masks.shape(timelineOf(clipOf("S", 0, 30, track = Timeline.A1)), "S", WindowShape.CIRCLE) })
        assertEquals("轨道已锁定", rejection { masks.shape(timelineOf(clipOf("A", 0, 30), locked = setOf(Timeline.V1)), "A", WindowShape.CIRCLE) })
        assertEquals("遮罩需要一个形状", rejection { masks.shape(timelineOf(clipOf("A", 0, 30)), "A", WindowShape.FULL) })
        assertEquals("先为片段选择遮罩形状", rejection { masks.adjust(timelineOf(clipOf("A", 0, 30)), "A", 0) { it } })
        assertEquals("先为片段选择遮罩形状", rejection { masks.toggleKey(timelineOf(clipOf("A", 0, 30)), "A", 0) })
    }

    @Test
    fun `a still mask changes throughout, a keyed one at the playhead, and the button follows`() {
        val t = keyed()
        val mask = t.a().mask!!
        assertEquals(listOf(10, 70), mask.keyframes.map { it.frame })
        assertEquals(-0.5f, mask.at(0).centerX, 1e-6f)
        assertEquals(0f, mask.at(40).centerX, 1e-6f)
        assertEquals(0.5f, mask.at(99).centerX, 1e-6f)
        assertEquals(ParamKeyState.AT_KEY, masks.keyState(t.a(), 70))
        assertEquals(ParamKeyState.ANIMATED, masks.keyState(t.a(), 40))
        assertEquals(ParamKeyState.NONE, masks.keyState(clipOf("B", 0, 10), 0))
        // Keyed, the playhead must be inside the clip.
        assertEquals("播放头不在片段内：移进片段再调整遮罩", rejection { masks.adjust(t, "A", 120) { it.copy(size = 1f) } })
        // Removing the keys leaves the mask where it was.
        val one = masks.toggleKey(t, "A", 10)
        assertEquals("已删除遮罩关键帧 · 还剩 1 个", one.message)
        val none = masks.toggleKey(one.timeline, "A", 70)
        assertEquals("已删除遮罩关键帧，遮罩不再移动", none.message)
        assertEquals(0.5f, none.timeline.a().mask!!.centerX, 1e-6f)
        assertEquals("已添加遮罩关键帧 · 共 3 个", masks.toggleKey(t, "A", 40).message)
    }

    @Test
    fun `inverting shows the picture outside the shape`() {
        val t = masks.shape(timelineOf(clipOf("A", 0, 100)), "A", WindowShape.CIRCLE)!!.timeline
        val inverted = masks.invert(t, "A", true)!!
        assertEquals("已反转遮罩：显示形状之外的画面", inverted.message)
        assertTrue(inverted.timeline.a().mask!!.invert)
        assertNull(masks.invert(inverted.timeline, "A", true))
        assertFalse(masks.invert(inverted.timeline, "A", false)!!.timeline.a().mask!!.invert)
        // The coverage the shader computes: inside the centred circle shows, its corner does not; inverted the other way.
        val m = t.a().mask!!
        assertEquals(1f, m.coverage(0.5f, 0.5f, 16f / 9f), 1e-6f)
        assertEquals(0f, m.coverage(0.02f, 0.02f, 16f / 9f), 1e-6f)
        assertEquals(0f, m.copy(invert = true).coverage(0.5f, 0.5f, 16f / 9f), 1e-6f)
    }

    @Test
    fun `cutting, trimming, retiming, moving and freezing keep the mask's motion`() {
        val t = keyed()
        val clip = t.a()
        // A cut at frame 40: the second piece starts halfway, its mask where it was there.
        val pieces = SplitClipUseCase(SequentialIds())(t, 40, "A", 30).timeline.clips.sortedBy { it.start }
        assertEquals(listOf(10, 39), pieces[0].mask!!.keyframes.map { it.frame })
        for (f in 0 until 60) assertEquals("piece 2 frame $f", clip.mask!!.at(40 + f).centerX, pieces[1].mask!!.at(f).centerX, 1e-4f)
        // Trimming 20 frames off the head.
        val trimmed = TrimClipUseCase()(t, "A", TrimEdge.START, 20, 5, false, 0, 100_000, 30).timeline.clip("A")!!
        for (f in 0 until 80) assertEquals("trimmed frame $f", clip.mask!!.at(20 + f).centerX, trimmed.mask!!.at(f).centerX, 1e-4f)
        // Half speed: twice as long, each key twice as far in.
        val slow = ClipPropertiesUseCase().speed(t, "A", ClipSpeed.of(PlaybackSpeed.HALF)).clip("A")!!
        assertEquals(listOf(20, 140), slow.mask!!.keyframes.map { it.frame })
        assertEquals(listOf(15, 75), clip.keyframesMoved { it + 5 }.mask!!.keyframes.map { it.frame })
        // A held frame keeps the mask as it stood.
        val held = clip.mask!!.frozenAt(40)
        assertTrue(held.keyframes.isEmpty())
        assertEquals(0f, held.centerX, 1e-6f)
        assertEquals(PowerWindow::class, held::class)
    }
}
