package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's timeline In / Out: what the source edits fill and 入点至出点 renders. */
class TimelineMarksUseCaseTest {

    private val marks = TimelineMarksUseCase()
    private val timeline = timelineOf()

    private fun rejected(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `mark clip takes the selected clip, else the one under the playhead on V1 before V2, then sound`() {
        val t = timelineOf(
            clipOf("v1a", 0, 90), clipOf("v2a", 30, 30, Timeline.V2), clipOf("snd", 100, 50, Timeline.A1), clipOf("v1b", 90, 60),
        )
        val sel = marks.markClip(t, "v2a", 0, 30)
        assertEquals(TimelineMarks(30, 60), sel.timeline.marks)
        assertEquals("已按“v2a”标记入出点 00:00:01:00 → 00:00:02:00", sel.message)
        assertEquals(TimelineMarks(0, 90), marks.markClip(t, null, 45, 30).timeline.marks)
        assertEquals(TimelineMarks(90, 150), marks.markClip(t, null, 90, 30).timeline.marks)
        assertEquals(TimelineMarks(100, 150), marks.markClip(timelineOf(clipOf("snd", 100, 50, Timeline.A1)), null, 120, 30).timeline.marks)
        assertEquals("播放头下没有片段", rejected { marks.markClip(t, null, 400, 30) })
    }

    @Test
    fun `in and out mark the range, a mark on the wrong side of the other drops it`() {
        val withIn = marks.markIn(timeline, 60, 30)
        assertEquals(TimelineMarks(60, null), withIn.timeline.marks)
        assertEquals("已标记时间线入点 00:00:02:00", withIn.message)
        val both = marks.markOut(withIn.timeline, 150, 30)
        assertEquals(TimelineMarks(60, 150), both.timeline.marks)
        val late = marks.markIn(both.timeline, 150, 30)
        assertEquals(TimelineMarks(150, null), late.timeline.marks)
        assertEquals("已标记时间线入点 00:00:05:00，原出点不在它之后，已清除", late.message)
        val early = marks.markOut(both.timeline, 45, 30)
        assertEquals(TimelineMarks(null, 45), early.timeline.marks)
        assertEquals("已标记时间线出点 00:00:01:15，原入点不在它之前，已清除", early.message)
        assertEquals("出点不能在时间线开头", rejected { marks.markOut(timeline, 0, 30) })
        assertEquals(TimelineMarks(), marks.clear(both.timeline).timeline.marks)
        assertEquals("时间线上没有入出点", rejected { marks.clear(timeline) })
    }

    @Test
    fun `the marked range runs from in or the start to out or the end`() {
        assertEquals(60 until 150, TimelineMarks(60, 150).range(300))
        assertEquals(60 until 300, TimelineMarks(60, null).range(300))
        assertEquals(0 until 150, TimelineMarks(null, 150).range(300))
        assertNull(TimelineMarks().range(300))
        assertNull("in past the end", TimelineMarks(400, null).range(300))
        assertEquals("入点 00:00:02:00 → 出点 00:00:05:00（00:00:03:00）", TimelineMarksUseCase.label(TimelineMarks(60, 150), 30))
        assertEquals("出点 00:00:05:00", TimelineMarksUseCase.label(TimelineMarks(null, 150), 30))
        assertNull(TimelineMarksUseCase.label(TimelineMarks(), 30))
    }
}
