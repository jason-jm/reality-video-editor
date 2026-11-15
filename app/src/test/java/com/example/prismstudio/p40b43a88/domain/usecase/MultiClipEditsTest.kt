package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edits of several selected clips at once: each with its linked partner, one undo step. */
class MultiClipEditsTest {

    private val fps = 30
    private val ids = SequentialIds()
    private val clipboard = ClipboardUseCase(ids)

    // V1: A (linked to Aa on A1) 0–30, B 30–60, C 90–120; A1: Aa 0–30, Y 60–120.
    private val a = clipOf("A", 0, 30, linkId = "L")
    private val aa = clipOf("Aa", 0, 30, track = Timeline.A1, linkId = "L")
    private val b = clipOf("B", 30, 30)
    private val c = clipOf("C", 90, 30)
    private val y = clipOf("Y", 60, 60, track = Timeline.A1)
    private val timeline = timelineOf(a, aa, b, c, y)

    private fun Timeline.at(id: String): Clip = clip(id)!!

    @Test
    fun `copy keeps the selected clips' places relative to one another, partners included`() {
        val content = clipboard.copy(timeline, listOf("C", "A"))
        assertEquals(2, content.groups)
        assertEquals(listOf("C", "A", "Aa"), content.clips.map { it.id })
        assertEquals(listOf(90, 0, 0), content.clips.map { it.start })

        val pasted = clipboard.paste(timeline, content, 300, fps)
        val placed = pasted.timeline.clips.filter { it.start >= 300 }
        assertEquals(listOf(390, 300, 300), placed.map { it.start })
        assertEquals("the copies come back selected, a linked pair once", 2, pasted.selection!!.size)
        assertEquals(placed.first().id, pasted.selection!!.first())
    }

    @Test
    fun `delete lifts every selected clip with its partner and leaves the gaps`() {
        val result = clipboard.delete(timeline, listOf("A", "C"))
        assertEquals(listOf("B", "Y"), result.timeline.clips.map { it.id })
        assertEquals(30, result.timeline.at("B").start)
        assertEquals("已删除 2 个片段，保留空隙", result.message)
        assertNull(result.selectedClipId)
    }

    @Test
    fun `ripple delete closes each removed clip's span on every track clear over it`() {
        val result = RippleDeleteUseCase(SequentialIds())(timeline, listOf("A", "B"), fps)
        val t = result.timeline
        assertEquals("C moves back by A and B", 30, t.at("C").start)
        assertEquals("Y keeps its place against C: A1 was clear over both spans", 0, t.at("Y").start)
        assertEquals("已波纹删除 2 个片段，后续片段最多前移 00:00:02:00", result.message)
        assertFalse(t.clips.any { it.id in setOf("A", "Aa", "B") })
    }

    @Test
    fun `disable switches all off while any plays, then all back on`() {
        val toggle = ToggleClipEnabledUseCase()
        val off = toggle(timeline, listOf("A", "C"))
        assertEquals(listOf(false, false, false), listOf("A", "Aa", "C").map { off.timeline.at(it).enabled })
        assertTrue("others stay", off.timeline.at("B").enabled)
        assertEquals(listOf("A", "C"), off.selection)
        val mixed = timeline.copy(clips = timeline.clips.map { if (it.id == "A") it.copy(enabled = false) else it })
        assertEquals("one still plays: all off", listOf(false, false), listOf("A", "C").map { toggle(mixed, listOf("A", "C")).timeline.at(it).enabled })
        val on = toggle(off.timeline, listOf("A", "C"))
        assertTrue(listOf("A", "Aa", "C").all { on.timeline.at(it).enabled })
    }

    @Test
    fun `a group moves together until one of it meets a clip that stays`() {
        val move = MoveClipUseCase()
        // B and C: C can go right freely, B only until... nothing; left B meets A (delta ≥ 0).
        val right = move.group(timeline, listOf("B", "C"), "B", 75, 0, false, 0, fps)
        assertEquals(75, right.timeline.at("B").start)
        assertEquals(135, right.timeline.at("C").start)
        assertEquals(listOf("B", "C"), right.selection)
        val left = move.group(timeline, listOf("B", "C"), "B", 10, 0, false, 0, fps)
        assertEquals("B stops at A", 30, left.timeline.at("B").start)
        assertEquals(90, left.timeline.at("C").start)
        // A with its partner and C: Aa meets Y at 60, so the group goes at most 30 frames right.
        val far = move.group(timeline, listOf("A", "C"), "C", 200, 0, false, 0, fps)
        assertEquals("B (not selected) stops A at once", 0, far.timeline.at("A").start)
        assertEquals(90, far.timeline.at("C").start)
        val lockedTimeline = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.A1) it.copy(locked = true) else it })
        assertEquals(
            "a partner on a locked track",
            "链接的片段在已锁定的轨道上",
            runCatching { move.group(lockedTimeline, listOf("A", "C"), "C", 100, 0, false, 0, fps) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `a group dragged to another lane moves there together, as far as its outermost clip allows`() {
        val move = MoveClipUseCase()
        // B and C dragged by B up to V2: both go, at the same times; A1 is not a picture track.
        val up = move.group(timeline, listOf("B", "C"), "B", 30, 0, false, 0, fps, targetTrackId = Timeline.V2)
        assertEquals(listOf(Timeline.V2, Timeline.V2), listOf("B", "C").map { up.timeline.at(it).trackId })
        assertEquals(listOf(30, 90), listOf("B", "C").map { up.timeline.at(it).start })
        assertEquals("已将 2 个片段上移 1 条轨道", up.message)
        assertEquals(listOf("B", "C"), up.selection)
        // With a time offset too; A's sound stays on A1 and moves in time with A.
        val later = move.group(timeline, listOf("A", "C"), "A", 150, 0, false, 0, fps, targetTrackId = Timeline.V2)
        assertEquals(Timeline.V2 to 150, later.timeline.at("A").let { it.trackId to it.start })
        assertEquals(Timeline.A1 to 150, later.timeline.at("Aa").let { it.trackId to it.start })
        assertEquals(Timeline.V2 to 240, later.timeline.at("C").let { it.trackId to it.start })
        assertEquals("已将 2 个片段上移 1 条轨道，+00:00:05:00", later.message)
        // One of them on V2 already: the group cannot go up, so it only moves in time.
        val title = clipOf("T", 200, 30, track = Timeline.V2)
        val stacked = timelineOf(a, aa, b, c, y, title)
        val held = move.group(stacked, listOf("B", "T"), "B", 30, 0, false, 0, fps, targetTrackId = Timeline.V2)
        assertEquals(listOf(Timeline.V1, Timeline.V2), listOf("B", "T").map { held.timeline.at(it).trackId })
        // A clip that stays in the way, or a locked lane, refuses the drop.
        val busy = timelineOf(a, aa, b, c, y, clipOf("X", 100, 10, track = Timeline.V2))
        assertEquals("目标位置已被占用", runCatching { move.group(busy, listOf("B", "C"), "B", 30, 0, false, 0, fps, Timeline.V2) }.exceptionOrNull()?.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V2) it.copy(locked = true) else it })
        assertEquals("目标轨道已锁定", runCatching { move.group(locked, listOf("B", "C"), "B", 30, 0, false, 0, fps, Timeline.V2) }.exceptionOrNull()?.message)
        // The keys: a track up and back down; past the top there is nowhere to go.
        val keyed = move.groupToAdjacentTrack(timeline, listOf("B", "C"), "B", up = true, fps = fps)
        assertEquals(listOf(Timeline.V2, Timeline.V2), listOf("B", "C").map { keyed.timeline.at(it).trackId })
        assertEquals("已经在最上面的轨道", runCatching { move.groupToAdjacentTrack(keyed.timeline, listOf("B", "C"), "B", up = true, fps = fps) }.exceptionOrNull()?.message)
        assertEquals(Timeline.V1, move.groupToAdjacentTrack(keyed.timeline, listOf("B", "C"), "B", up = false, fps = fps).timeline.at("C").trackId)
        assertEquals(
            "最上面的轨道上已有选中的片段",
            runCatching { move.groupToAdjacentTrack(stacked, listOf("B", "T"), "B", up = true, fps = fps) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `split cuts the selected clips under the playhead and selects their right halves`() {
        val v2 = clipOf("T", 0, 60, track = Timeline.V2)
        val result = SplitClipUseCase(ids)(timelineOf(a, aa, b, v2, y), 15, listOf("A", "T"), fps)
        val t = result.timeline
        assertEquals("A, its partner and T are cut; B and Y are not under the playhead", 8, t.clips.size)
        assertEquals(15, t.at("A").end)
        assertEquals(15, t.at("T").end)
        assertEquals(2, result.selection!!.size)
        assertTrue(result.selection!!.all { id -> t.at(id).start == 15 })
    }

    @Test
    fun `paste attributes goes onto every selected clip that can take it`() {
        val pip = clipOf("P", 200, 30).copy(transform = ClipTransform(scalePercent = 35))
        val z = clipOf("Z", 0, 30, track = Timeline.A2)
        val withPip = timelineOf(a, aa, b, c, y, pip, z)
        val content = clipboard.copy(withPip, "P")
        val use = PasteAttributesUseCase()
        // A picture's fades go onto pictures too (rev 98).
        assertEquals(setOf(ClipAttribute.TRANSFORM, ClipAttribute.EFFECTS, ClipAttribute.GRADE, ClipAttribute.FADES, ClipAttribute.SPEED), use.available(withPip, content, listOf("B", "Y")))
        val result = use(withPip, content, listOf("B", "C", "Y"), setOf(ClipAttribute.TRANSFORM), fps)
        assertEquals(35, result.timeline.at("B").transform.scalePercent)
        assertEquals(35, result.timeline.at("C").transform.scalePercent)
        assertTrue(result.message.endsWith("（1 个不适用或已锁定，未改动）"))
        assertEquals(
            "none can take it",
            "所选片段都不能接受这些属性",
            runCatching { use(withPip, content, listOf("Y", "Z"), setOf(ClipAttribute.TRANSFORM), fps) }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `one selected clip keeps the single-clip edits and messages`() {
        assertEquals(clipboard.delete(timeline, "C"), clipboard.delete(timeline, listOf("C")))
        assertEquals(RippleDeleteUseCase(SequentialIds())(timeline, "B", fps), RippleDeleteUseCase(SequentialIds())(timeline, listOf("B"), fps))
        assertEquals(1, clipboard.copy(timeline, listOf("A")).groups)
        try {
            clipboard.delete(timeline, listOf("gone"))
        } catch (e: EditRejected) {
            assertEquals("请先在时间线上选择片段", e.message)
        }
    }
}
