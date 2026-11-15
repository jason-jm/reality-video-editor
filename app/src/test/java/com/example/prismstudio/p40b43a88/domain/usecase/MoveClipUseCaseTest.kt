package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assertLockedTrackUntouched
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoveClipUseCaseTest {

    private val moveClip = MoveClipUseCase()

    private val c = clipOf("C", 0, 50)
    private val a = clipOf("A", 100, 100, linkId = "L")
    private val b = clipOf("B", 300, 100)
    private val aSound = clipOf("Aa", 100, 100, track = Timeline.A1, linkId = "L")
    private val title = titleClipOf("T", 500, 100)

    /** V1: C [0,50) · A [100,200) · B [300,400); A1: A's sound [100,200); V2: title [500,600). */
    private val timeline = timelineOf(c, a, b, aSound, title)

    private fun move(
        timeline: Timeline,
        clipId: String,
        start: Int,
        track: String = Timeline.V1,
        snapping: Boolean = false,
        playhead: Int = 0,
    ): EditResult = moveClip(timeline, clipId, start, track, snapThreshold = 5, snapping = snapping, playhead = playhead, fps = 30)

    private fun rejection(block: () -> Unit): String? = assertThrows(EditRejected::class.java) { block() }.message

    private fun span(timeline: Timeline, id: String) = timeline.clip(id)!!.let { it.trackId to (it.start until it.end) }

    @Test
    fun `a clip slides freely and its linked audio moves by the same offset`() {
        val result = move(timeline, "A", 150)

        assertEquals(Timeline.V1 to (150 until 250), span(result.timeline, "A"))
        assertEquals(Timeline.A1 to (150 until 250), span(result.timeline, "Aa"))
        assertEquals(listOf(c, b, title), result.timeline.clips.filter { it.id in setOf("C", "B", "T") })
        assertEquals("A", result.selectedClipId)
        assertEquals("已移动“A”到 00:00:05:00", result.message)
    }

    @Test
    fun `on its own track a clip stops at a neighbour instead of jumping over it`() {
        val pushedRight = move(timeline, "A", 380).timeline
        assertEquals(Timeline.V1 to (200 until 300), span(pushedRight, "A"))
        assertEquals(Timeline.A1 to (200 until 300), span(pushedRight, "Aa"))

        val pushedLeft = move(timeline, "A", 10).timeline
        assertEquals(Timeline.V1 to (50 until 150), span(pushedLeft, "A"))
        assertEquals(Timeline.A1 to (50 until 150), span(pushedLeft, "Aa"))

        assertEquals(Timeline.V1 to (0 until 50), span(move(timeline, "C", -30).timeline, "C"))
    }

    @Test
    fun `a clip can move onto a free range of another picture track but not onto an occupied one`() {
        assertEquals(Timeline.V2 to (300 until 400), span(move(timeline, "B", 300, Timeline.V2).timeline, "B"))
        assertEquals(Timeline.V1 to (200 until 300), span(move(timeline, "T", 200, Timeline.V1).timeline, "T"))

        assertEquals("目标位置已被占用", rejection { move(timeline, "B", 450, Timeline.V2) })
    }

    @Test
    fun `locked tracks unknown clips and tracks of the other kind are rejected`() {
        assertEquals("轨道已锁定", rejection { move(timelineOf(c, a, b, aSound, title, locked = setOf(Timeline.V1)), "B", 250) })
        assertEquals("轨道已锁定", rejection { move(timelineOf(c, a, b, aSound, title, locked = setOf(Timeline.V2)), "B", 300, Timeline.V2) })
        assertEquals("不能移动到该轨道", rejection { move(timeline, "A", 100, Timeline.A2) })
        assertEquals("不能移动到该轨道", rejection { move(timeline, "Aa", 100, Timeline.V2) })
        assertEquals("不能移动到该轨道", rejection { move(timeline, "B", 300, "v9") })
        assertEquals("请先在时间线上选择片段", rejection { move(timeline, "ghost", 0) })
    }

    @Test
    fun `snapping pulls the start or the end of the clip onto a nearby edit point`() {
        assertEquals(Timeline.V1 to (250 until 350), span(move(timeline, "B", 253, snapping = true, playhead = 250).timeline, "B"))
        assertEquals(Timeline.V1 to (310 until 410), span(move(timeline, "B", 307, snapping = true, playhead = 410).timeline, "B"))
        assertEquals(Timeline.V1 to (253 until 353), span(move(timeline, "B", 253, snapping = false, playhead = 250).timeline, "B"))
    }

    @Test
    fun `a move that would push the linked audio onto another clip is rejected`() {
        val crowded = timelineOf(c, a, b, aSound, title, clipOf("X", 260, 20, track = Timeline.A1))

        assertEquals(Timeline.A1 to (150 until 250), span(move(crowded, "A", 150).timeline, "Aa"))
        assertEquals("关联的音频片段会与其他片段重叠", rejection { move(crowded, "A", 170) })
    }
    @Test
    fun `a linked partner on a locked track is never moved`() {
        val locked = timelineOf(c, a, b, aSound, title, locked = setOf(Timeline.A1))

        assertLockedTrackUntouched(locked, Timeline.A1) { move(locked, "A", 150).timeline }
    }
}
