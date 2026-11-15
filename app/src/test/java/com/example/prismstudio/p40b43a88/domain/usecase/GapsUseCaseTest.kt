package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's gaps: picked by a tap, closed by deleting them, all at once by Delete Gaps. */
class GapsUseCaseTest {

    private val use = GapsUseCase()

    // V1: a 0–60, b 90–150 (a 30-frame gap), c 180–240. A1: sa 0–60, sb 90–150 (b's sound, linked).
    private val timeline = timelineOf(
        clipOf("a", 0, 60), clipOf("b", 90, 60, linkId = "L"), clipOf("c", 180, 60),
        clipOf("sa", 0, 60, Timeline.A1), clipOf("sb", 90, 60, Timeline.A1, linkId = "L"),
    )

    private fun Timeline.starts() = clips.associate { it.id to it.start }

    @Test
    fun `a tap between clips picks the gap, none on a clip or past the last one`() {
        assertEquals(TimelineGap(Timeline.V1, 60, 90), use.gapAt(timeline, Timeline.V1, 75))
        assertEquals(TimelineGap(Timeline.V1, 150, 180), use.gapAt(timeline, Timeline.V1, 150))
        assertNull(use.gapAt(timeline, Timeline.V1, 30))
        assertNull("past the last clip there is no gap", use.gapAt(timeline, Timeline.V1, 300))
        assertEquals("before the first clip", TimelineGap(Timeline.V1, 0, 30), use.gapAt(timelineOf(clipOf("x", 30, 30)), Timeline.V1, 5))
        assertEquals(listOf(TimelineGap(Timeline.V1, 60, 90), TimelineGap(Timeline.V1, 150, 180)), use.gapsOn(timeline, Timeline.V1))
    }

    @Test
    fun `closing a gap moves every track that is free there, linked clips together`() {
        val result = use.close(timeline, TimelineGap(Timeline.V1, 60, 90), 30)
        assertEquals(mapOf("a" to 0, "b" to 60, "c" to 150, "sa" to 0, "sb" to 60), result.timeline.starts())
        assertEquals("已删除 1 秒空隙，后面的片段前移", result.message)
    }

    @Test
    fun `a track with something in the gap stays, unless that would split a linked pair`() {
        val bed = timeline.copy(clips = timeline.clips + clipOf("m", 0, 300, Timeline.A2))
        val result = use.close(bed, TimelineGap(Timeline.V1, 150, 180), 30)
        assertEquals("c moves, the music bed stays", 150, result.timeline.clip("c")!!.start)
        assertEquals(0, result.timeline.clip("m")!!.start)
        assertEquals("已删除 1 秒空隙，后面的片段前移（A2 在这段有内容，保持不动）", result.message)
        // A1 has sound in V1's first gap: b would move without its linked sound.
        val covered = timeline.copy(clips = timeline.clips + clipOf("fill", 60, 30, Timeline.A1))
        val e = assertThrows(EditRejected::class.java) { use.close(covered, TimelineGap(Timeline.V1, 60, 90), 30) }
        assertEquals("其他轨道在这段空隙里有内容，删除它会让“b”和链接的片段错位", e.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V1) it.copy(locked = true) else it })
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { use.close(locked, TimelineGap(Timeline.V1, 60, 90), 30) }.message)
    }

    @Test
    fun `Delete Gaps closes every gap that can close`() {
        val result = use.closeAll(timeline, 30)
        assertEquals(mapOf("a" to 0, "b" to 60, "c" to 120, "sa" to 0, "sb" to 60), result.timeline.starts())
        assertEquals("已删除 2 处空隙，共 2 秒", result.message)
        assertThrows(EditRejected::class.java) { use.closeAll(result.timeline, 30) }
    }
}
