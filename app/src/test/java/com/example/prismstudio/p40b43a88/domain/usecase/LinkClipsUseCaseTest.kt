package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkClipsUseCaseTest {

    private val links = LinkClipsUseCase(SequentialIds())
    // V1: A 0–60 linked to its sound As on A1, B 60–120; A2: music M 0–120; V2: a title T.
    private val timeline = timelineOf(
        clipOf("A", 0, 60, linkId = "L"),
        clipOf("As", 0, 60, track = Timeline.A1, linkId = "L"),
        clipOf("B", 60, 60),
        clipOf("M", 0, 120, track = Timeline.A2),
        titleClipOf("T", 0, 30),
    )

    @Test
    fun `the action follows the selection`() {
        assertEquals(LinkAction.UNLINK, links.actionFor(timeline, listOf("A")))
        assertEquals("selected by its sound too", LinkAction.UNLINK, links.actionFor(timeline, listOf("As", "B")))
        assertEquals(LinkAction.LINK, links.actionFor(timeline, listOf("B", "M")))
        assertNull("two pictures", links.actionFor(timeline, listOf("B", "T")))
        assertNull("a picture alone", links.actionFor(timeline, listOf("B")))
        assertNull("a title has no sound to link", links.actionFor(timeline, listOf("T", "M")))
    }

    @Test
    fun `unlinked, picture and sound trim apart — an L cut — and link again as one`() {
        val apart = links.unlink(timeline, listOf("A"))
        assertNull(apart.timeline.clip("A")!!.linkId)
        assertNull(apart.timeline.clip("As")!!.linkId)
        assertEquals("已取消链接：画面和声音可以分别移动和修剪", apart.message)
        assertEquals(listOf("A"), apart.selection)
        // The sound runs on under B while the picture cuts at 60.
        val trimmed = TrimClipUseCase()(apart.timeline, "As", TrimEdge.END, 90, 0, false, 0, null, 30).timeline
        assertEquals(0 until 60, trimmed.clip("A")!!.let { it.start until it.end })
        assertEquals(0 until 90, trimmed.clip("As")!!.let { it.start until it.end })

        val paired = links.toggle(trimmed, listOf("B", "M"))
        assertEquals("l-1", paired.timeline.clip("B")!!.linkId)
        assertEquals("l-1", paired.timeline.clip("M")!!.linkId)
        assertEquals("已链接片段：画面和声音一起移动和修剪", paired.message)
        assertEquals("unlinking both pairs at once", "已取消 2 组链接", links.toggle(links.link(trimmed, listOf("A", "As")).timeline.let { t ->
            links.link(t, listOf("B", "M")).timeline
        }, listOf("A", "B")).message)
    }

    @Test
    fun `locks and selections that cannot be linked are refused`() {
        val locked = timeline.copy(tracks = timeline.tracks.map { it.copy(locked = it.id == Timeline.A1) })
        assertEquals("轨道已锁定", runCatching { links.unlink(locked, listOf("A")) }.exceptionOrNull()?.message)
        assertEquals("选择一个画面片段和一个声音片段来链接", runCatching { links.toggle(timeline, listOf("B", "T")) }.exceptionOrNull()?.message)
        assertEquals("请先在时间线上选择片段", runCatching { links.toggle(timeline, emptyList()) }.exceptionOrNull()?.message)
    }
}
