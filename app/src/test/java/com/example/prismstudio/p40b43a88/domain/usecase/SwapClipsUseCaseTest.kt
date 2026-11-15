package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Swap Clips: the selected clip and its neighbour change places, lengths and the gap between them kept. */
class SwapClipsUseCaseTest {

    private val use = SwapClipsUseCase()

    // V1: a 0–60, b 60–150 (its sound sb on A1), a 30-frame gap, c 180–210. A1: sb 60–150.
    private val timeline = timelineOf(
        clipOf("a", 0, 60), clipOf("b", 60, 90, linkId = "L"), clipOf("c", 180, 30),
        clipOf("sb", 60, 90, Timeline.A1, linkId = "L"),
    )

    private fun Timeline.spans() = clips.associate { it.id to (it.start to it.end) }

    @Test
    fun `swapping right puts the neighbour first and keeps the gap between them`() {
        val result = use(timeline, listOf("b"), right = true)
        assertEquals(mapOf("a" to (0 to 60), "c" to (60 to 90), "b" to (120 to 210), "sb" to (120 to 210)), result.timeline.spans())
        assertEquals("已将“b”与右侧的“c”交换", result.message)
        assertEquals("b", result.selectedClipId)
    }

    @Test
    fun `swapping left, and swapping back, restores the cut`() {
        val left = use(timeline, listOf("b"), right = false).timeline
        assertEquals(mapOf("b" to (0 to 90), "sb" to (0 to 90), "a" to (90 to 150), "c" to (180 to 210)), left.spans())
        assertEquals(timeline.spans(), use(left, listOf("b"), right = true).timeline.spans())
    }

    @Test
    fun `no neighbour, several clips, a locked track or a clash with the linked sound are refused`() {
        assertEquals("左侧没有可以交换的片段", assertThrows(EditRejected::class.java) { use(timeline, listOf("a"), right = false) }.message)
        assertEquals("一次只能交换一个片段", assertThrows(EditRejected::class.java) { use(timeline, listOf("a", "c"), right = true) }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.A1) it.copy(locked = true) else it })
        assertEquals("链接的片段在已锁定的轨道上", assertThrows(EditRejected::class.java) { use(locked, listOf("a"), right = true) }.message)
        // Something on A1 where b's sound would land.
        val busy = timeline.copy(clips = timeline.clips + clipOf("x", 0, 30, Timeline.A1))
        assertEquals("交换后链接的片段会和其他片段重叠", assertThrows(EditRejected::class.java) { use(busy, listOf("b"), right = false) }.message)
    }
}
