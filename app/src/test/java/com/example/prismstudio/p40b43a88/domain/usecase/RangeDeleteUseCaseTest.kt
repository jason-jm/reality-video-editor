package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RangeDeleteUseCaseTest {

    private val edits = RangeDeleteUseCase(SequentialIds())

    // V1: A 0–150 (its sound X on A1, linked) then B 150–300; V2: the title T 100–160; A2: music M 0–300. In 60, Out 120.
    private val base = timelineOf(
        clipOf("A", 0, 150, linkId = "L"),
        clipOf("X", 0, 150, track = Timeline.A1, linkId = "L"),
        clipOf("B", 150, 150),
        titleClipOf("T", 100, 60),
        clipOf("M", 0, 300, track = Timeline.A2, assetId = "song"),
    ).copy(marks = TimelineMarks(60, 120))

    private fun Timeline.spans(track: String) = clips.filter { it.trackId == track }.sortedBy { it.start }.map { it.start to it.end }

    @Test
    fun `lift cuts the range out of every unlocked track and leaves the gap, still marked`() {
        val result = edits.lift(base, 30)
        val t = result.timeline
        assertEquals(listOf(0 to 60, 120 to 150, 150 to 300), t.spans(Timeline.V1))
        assertEquals(listOf(0 to 60, 120 to 150), t.spans(Timeline.A1))
        assertEquals(listOf(120 to 160), t.spans(Timeline.V2))
        assertEquals(listOf(0 to 60, 120 to 300), t.spans(Timeline.A2))
        assertEquals(TimelineMarks(60, 120), t.marks)
        assertEquals("已删除 入点 00:00:02:00 至出点 00:00:04:00 的内容，留下空隙", result.message)
        // The tail picks up the source where it was; the title keeps its id; the linked tails stay linked to each other.
        val tail = t.clips.first { it.trackId == Timeline.V1 && it.start == 120 }
        assertEquals(4_000L, tail.sourceInMs)
        assertEquals("T", t.clips.first { it.trackId == Timeline.V2 }.id)
        val soundTail = t.clips.first { it.trackId == Timeline.A1 && it.start == 120 }
        assertEquals(tail.linkId, soundTail.linkId)
        assertNotEquals("L", tail.linkId)
        assertEquals("L", t.clip("A")!!.linkId)
    }

    @Test
    fun `extract closes the gap on the unlocked tracks and drops the marks`() {
        val result = edits.extract(base, 30)
        val t = result.timeline
        assertEquals(listOf(0 to 60, 60 to 90, 90 to 240), t.spans(Timeline.V1))
        assertEquals(listOf(0 to 60, 60 to 90), t.spans(Timeline.A1))
        assertEquals(listOf(60 to 100), t.spans(Timeline.V2))
        assertEquals(listOf(0 to 60, 60 to 240), t.spans(Timeline.A2))
        assertEquals(TimelineMarks(), t.marks)
        assertEquals("已波纹删除 入点 00:00:02:00 至出点 00:00:04:00，后面的片段前移 00:00:02:00", result.message)
    }

    @Test
    fun `a locked track keeps its clips where they are`() {
        val locked = base.copy(tracks = base.tracks.map { if (it.id == Timeline.A2) it.copy(locked = true) else it })
        assertEquals(listOf(0 to 300), edits.lift(locked, 30).timeline.spans(Timeline.A2))
        val extracted = edits.extract(locked, 30).timeline
        assertEquals(listOf(0 to 300), extracted.spans(Timeline.A2))
        assertEquals(listOf(0 to 60, 60 to 90, 90 to 240), extracted.spans(Timeline.V1))
    }

    @Test
    fun `an empty range can still be closed, and edits without both marks or tracks are refused`() {
        val gap = timelineOf(clipOf("A", 0, 60), clipOf("B", 120, 60)).copy(marks = TimelineMarks(60, 120))
        assertEquals("入点至出点之间没有片段", assertThrows(EditRejected::class.java) { edits.lift(gap, 30) }.message)
        assertEquals(listOf(0 to 60, 60 to 120), edits.extract(gap, 30).timeline.spans(Timeline.V1))
        val nothingAfter = timelineOf(clipOf("A", 0, 60)).copy(marks = TimelineMarks(90, 120))
        assertEquals("入点至出点之间和之后都没有片段", assertThrows(EditRejected::class.java) { edits.extract(nothingAfter, 30) }.message)
        for (marks in listOf(TimelineMarks(), TimelineMarks(60, null), TimelineMarks(null, 120))) {
            assertEquals("先在时间线上标记入点和出点", assertThrows(EditRejected::class.java) { edits.lift(base.copy(marks = marks), 30) }.message)
        }
        val allLocked = base.copy(tracks = base.tracks.map { it.copy(locked = true) })
        assertEquals("所有轨道都已锁定", assertThrows(EditRejected::class.java) { edits.extract(allLocked, 30) }.message)
    }
}
