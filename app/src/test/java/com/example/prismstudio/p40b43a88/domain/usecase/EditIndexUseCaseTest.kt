package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Resolve's Edit Index: the timeline's clips as numbered events in timeline order, filtered, with their sources. */
class EditIndexUseCaseTest {

    private val index = EditIndexUseCase()
    private val doc = projectOf(
        timeline = timelineOf(
            clipOf("B", 60, 30, assetId = "v", sourceInMs = 2000),
            clipOf("A", 0, 60, assetId = "v", linkId = "L"),
            clipOf("Aa", 0, 60, track = Timeline.A1, assetId = "v", linkId = "L"),
            titleClipOf("T", 0, 45),
            clipOf("N", 90, 30, assetId = null).copy(compoundId = "tl-2"),
        ),
        media = listOf(assetOf("v")),
    )

    @Test
    fun `events are numbered by where they start, the top track first, each with its source`() {
        val rows = index.rows(doc)
        assertEquals(listOf("T", "A", "Aa", "B", "N"), rows.map { it.clipId })
        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it.number })
        assertEquals(listOf("V2", "V1", "A1", "V1", "V1"), rows.map { it.track })
        assertEquals(listOf("标题", "源 00:00:00:00", "源 00:00:00:00", "源 00:00:02:00", "嵌套时间线"), rows.map { it.source })
        assertEquals(60 to 90, rows[3].recordIn to rows[3].recordOut)
        assertEquals("5 个片段 · 画面 4 · 声音 1", index.summary(doc, rows, EditIndexFilter.ALL))
    }

    @Test
    fun `the filter keeps pictures or sounds, numbering what it keeps`() {
        val sounds = index.rows(doc, EditIndexFilter.SOUND)
        assertEquals(listOf("Aa"), sounds.map { it.clipId })
        assertEquals(1, sounds.single().number)
        assertEquals(listOf("T", "A", "B", "N"), index.rows(doc, EditIndexFilter.PICTURE).map { it.clipId })
        val silent = doc.copy(timeline = timelineOf(clipOf("A", 0, 60, assetId = "v")))
        assertEquals("没有声音片段", index.summary(silent, index.rows(silent, EditIndexFilter.SOUND), EditIndexFilter.SOUND))
        val empty = doc.copy(timeline = timelineOf())
        assertEquals("时间线上还没有片段", index.summary(empty, index.rows(empty), EditIndexFilter.ALL))
    }
}
