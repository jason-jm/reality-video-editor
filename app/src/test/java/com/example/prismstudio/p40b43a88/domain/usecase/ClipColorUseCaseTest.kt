package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ClipColorUseCaseTest {

    private val colors = ClipColorUseCase()
    // A (picture) with its sound As, then B; a title T above.
    private val timeline = timelineOf(
        clipOf("A", 0, 60, linkId = "L"),
        clipOf("As", 0, 60, track = Timeline.A1, linkId = "L"),
        clipOf("B", 60, 60),
        titleClipOf("T", 30, 60),
    )

    @Test
    fun `a clip and its linked sound take the colour, the selection stays`() {
        val result = colors.set(timeline, listOf("A"), ClipColor.RED)
        assertEquals(listOf(ClipColor.RED, ClipColor.RED, null, null), listOf("A", "As", "B", "T").map { result.timeline.clip(it)!!.color })
        assertEquals("已将片段标为红色", result.message)
        assertEquals(listOf("A"), result.selection)
        val several = colors.set(result.timeline, listOf("B", "T"), ClipColor.TEAL)
        assertEquals("已将 2 个片段标为青色", several.message)
        assertEquals(ClipColor.TEAL, several.timeline.clip("T")!!.color)
        val cleared = colors.set(several.timeline, listOf("As"), null)
        assertNull(cleared.timeline.clip("A")!!.color)
        assertEquals("已清除片段颜色", cleared.message)
    }

    @Test
    fun `every clip of a colour is selected once per linked group, in timeline order`() {
        val coloured = colors.set(colors.set(timeline, listOf("B"), ClipColor.LIME).timeline, listOf("A"), ClipColor.LIME).timeline
        assertEquals(listOf("A", "B"), colors.select(coloured, ClipColor.LIME))
        assertEquals("时间线上没有粉色的片段", assertThrows(EditRejected::class.java) { colors.select(coloured, ClipColor.PINK) }.message)
    }

    @Test
    fun `nothing selected or a locked track is refused`() {
        assertEquals("请先在时间线上选择片段", assertThrows(EditRejected::class.java) { colors.set(timeline, emptyList(), ClipColor.RED) }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { it.copy(locked = it.id == Timeline.A1) })
        assertEquals("链接的片段在已锁定的轨道上", assertThrows(EditRejected::class.java) { colors.set(locked, listOf("A"), ClipColor.RED) }.message)
    }
}
