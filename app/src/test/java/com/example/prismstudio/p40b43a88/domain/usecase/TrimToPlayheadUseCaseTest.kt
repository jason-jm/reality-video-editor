package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Trim Start / End and Ripple Start / End to Playhead. */
class TrimToPlayheadUseCaseTest {

    private val use = TrimToPlayheadUseCase()
    private val media = assetOf("media", durationMs = 10_000)
    private val assets = { id: String? -> media.takeIf { id == "media" } }

    // V1: a 0–90 (its sound sa on A1, linked), b 90–150. A2: the song m 0–300.
    private val timeline = timelineOf(
        clipOf("a", 0, 90, linkId = "L"), clipOf("b", 90, 60),
        clipOf("sa", 0, 90, Timeline.A1, linkId = "L"), clipOf("m", 0, 300, Timeline.A2),
    )

    private fun Timeline.span(id: String) = clip(id)!!.let { listOf(it.start, it.end) }

    @Test
    fun `Shift+bracket trims every clip under the playhead to it, leaving a gap`() {
        val result = use(timeline, TrimEdge.START, false, 30, emptyList(), assets, 30)
        assertEquals(listOf(30, 90), result.timeline.span("a"))
        assertEquals(1_000L, result.timeline.clip("a")!!.sourceInMs)
        assertEquals(listOf(30, 90), result.timeline.span("sa"))
        assertEquals(listOf(30, 300), result.timeline.span("m"))
        assertEquals("b stays", listOf(90, 150), result.timeline.span("b"))
        assertEquals("已将 3 个片段的开头修剪到播放头", result.message)
        val end = use(timeline, TrimEdge.END, false, 60, emptyList(), assets, 30).timeline
        assertEquals(listOf(0, 60), end.span("a"))
        assertEquals(listOf(90, 150), end.span("b"))
    }

    @Test
    fun `the ripple versions close the gap on each trimmed track`() {
        val start = use(timeline, TrimEdge.START, true, 30, listOf("a"), assets, 30)
        assertEquals("the head goes, the clip stays put", listOf(0, 60), start.timeline.span("a"))
        assertEquals(1_000L, start.timeline.clip("a")!!.sourceInMs)
        assertEquals("its linked sound too", listOf(0, 60), start.timeline.span("sa"))
        assertEquals("the rest of V1 moves up", listOf(60, 120), start.timeline.span("b"))
        assertEquals("the song, not selected, is left alone", listOf(0, 300), start.timeline.span("m"))
        assertEquals("已将 2 个片段的开头波纹修剪到播放头，后面的片段前移 00:00:01:00", start.message)
        assertEquals("a", start.selectedClipId)
        val end = use(timeline, TrimEdge.END, true, 120, listOf("b"), assets, 30).timeline
        assertEquals(listOf(90, 120), end.span("b"))
        assertEquals(listOf(0, 90), end.span("a"))
    }

    @Test
    fun `locked tracks, a playhead on no clip, and a clip that cannot shrink further are refused or skipped`() {
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.A2) it.copy(locked = true) else it })
        assertEquals(listOf(0, 300), use(locked, TrimEdge.START, false, 30, emptyList(), assets, 30).timeline.span("m"))
        val e = assertThrows(EditRejected::class.java) {
            use(timeline.copy(clips = timeline.clips.filter { it.id == "b" }), TrimEdge.END, false, 30, emptyList(), assets, 30)
        }
        assertEquals("播放头不在任何片段上", e.message)
        val onlySong = locked.copy(clips = locked.clips.filter { it.id == "m" })
        assertEquals("播放头下的片段都在已锁定的轨道上",
            assertThrows(EditRejected::class.java) { use(onlySong, TrimEdge.START, false, 30, emptyList(), assets, 30) }.message)
        // b is 60 frames; the shortest a trim leaves is 5, so the start stops at 145.
        assertEquals(listOf(145, 150), use(timeline, TrimEdge.START, false, 148, listOf("b"), assets, 30).timeline.span("b"))
    }
}
