package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.SubtitleColor
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyleControl
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The subtitle track: placing subtitles, their rules on the timeline, and SRT export. */
class SubtitlesTest {

    private val titles = AddTitleUseCase(SequentialIds())
    private val fps = 30

    private fun subtitle(id: String, start: Int, frames: Int, text: String, track: String = "st1") = Clip(
        id = id, trackId = track, kind = ClipKind.TITLE, name = "字幕：$text", start = start, durationFrames = frames,
        title = TitleStyle(text, 44, TitleTemplate.SUBTITLE),
    )

    private fun withSubtitles(vararg clips: Clip, enabled: Boolean = true) =
        timelineOf(*clips).let { it.copy(tracks = listOf(Track("st1", "ST1", TrackKind.SUBTITLE, enabled = enabled)) + it.tracks) }

    @Test
    fun `the first subtitle makes the subtitle track on top, the next ones follow line by line`() {
        val first = titles(timelineOf(), TitleTemplate.SUBTITLE, 45, fps)
        assertEquals(listOf("st1", "v2", "v1", "a1", "a2"), first.timeline.tracks.map { it.id })
        assertEquals("已在 00:00:01:15 添加字幕（新建轨道 ST1）", first.message)
        val cue = first.timeline.clip(first.selectedClipId)!!
        assertEquals(listOf<Any>("st1", 45, 60), listOf(cue.trackId, cue.start, cue.durationFrames))

        val next = titles.nextSubtitle(first.timeline, cue.id, fps)
        assertEquals(105, next.timeline.clip(next.selectedClipId)!!.start)
        assertEquals("已在 00:00:03:15 添加下一条字幕", next.message)
        assertEquals("one subtitle track", 1, next.timeline.tracks.count { it.kind == TrackKind.SUBTITLE })
        assertThrows(EditRejected::class.java) { titles.nextSubtitle(timelineOf(clipOf("V", 0, 30)), "V", fps) }

        // Titles still go to V2, and a video track added later stays under the subtitle track.
        assertEquals(Timeline.V2, titles(first.timeline, TitleTemplate.CENTER, 0, fps).timeline.clips.last().trackId)
        val withV3 = TracksUseCase().add(projectOf(timeline = first.timeline), TrackKind.VIDEO).doc.timeline
        assertEquals(listOf("st1", "v3", "v2", "v1", "a1", "a2"), withV3.tracks.map { it.id })
        assertTrue("one subtitle track per language, up to four", TracksUseCase.canAdd(withV3, TrackKind.SUBTITLE))
    }

    @Test
    fun `only titles sit on the subtitle track, and titles move between it and the video tracks`() {
        val st = Track("st1", "ST1", TrackKind.SUBTITLE)
        assertTrue(st.accepts(ClipKind.TITLE))
        assertFalse(st.accepts(ClipKind.VIDEO))
        assertFalse(st.accepts(ClipKind.AUDIO))

        val timeline = withSubtitles(subtitle("S", 0, 60, "你好"), clipOf("V", 100, 60, track = Timeline.V2))
        val move = MoveClipUseCase()
        assertThrows(EditRejected::class.java) { move(timeline, "V", 100, "st1", 0, false, 0, fps) }
        assertThrows(EditRejected::class.java) { move.toAdjacentTrack(timeline, "V", up = true, fps = fps) }
        assertEquals(Timeline.V2, move.toAdjacentTrack(timeline, "S", up = false, fps = fps).timeline.clip("S")!!.trackId)
    }

    @Test
    fun `subtitles are burned in while their track is shown`() {
        val plan = BuildRenderPlanUseCase()
        assertEquals(listOf("S"), plan(projectOf(timeline = withSubtitles(subtitle("S", 0, 60, "你好")))).titles.map { it.clipId })
        assertTrue(plan(projectOf(timeline = withSubtitles(subtitle("S", 0, 60, "你好"), enabled = false))).titles.isEmpty())
        assertTrue("not a picture layer", plan(projectOf(timeline = withSubtitles(subtitle("S", 0, 60, "你好")))).layers.isEmpty())
    }

    @Test
    fun `the subtitle track exports as numbered SRT cues timed from the render range`() {
        val export = SubtitleExportUseCase()
        val doc = projectOf(
            timeline = withSubtitles(
                subtitle("B", 105, 45, "第二句"),
                subtitle("A", 45, 60, "第一句\n两行"),
                subtitle("E", 160, 20, "   "),
                clipOf("V", 0, 300),
            ),
        )
        assertEquals(
            "1\n00:00:01,500 --> 00:00:03,500\n第一句\n两行\n\n2\n00:00:03,500 --> 00:00:05,000\n第二句\n\n",
            export.srt(doc),
        )
        assertEquals("1\n00:00:00,000 --> 00:00:00,500\n第一句\n两行\n\n2\n00:00:00,500 --> 00:00:01,000\n第二句\n\n", export.srt(doc, 90, 120))
        assertNull("no subtitles, no file", export.srt(projectOf(timeline = timelineOf(clipOf("V", 0, 30)))))
        assertNull(export.srt(projectOf(timeline = withSubtitles(subtitle("A", 0, 30, "x"), enabled = false))))
        // WebVTT: its header, the same cues with a full stop before the milliseconds.
        assertEquals(
            "WEBVTT\n\n1\n00:00:01.500 --> 00:00:03.500\n第一句\n两行\n\n2\n00:00:03.500 --> 00:00:05.000\n第二句\n\n",
            export.text(doc, SubtitleFileFormat.WEBVTT),
        )
        // A blank line inside a subtitle would end its cue early: it is dropped.
        val spaced = projectOf(timeline = withSubtitles(subtitle("A", 0, 30, "上\n\n下")))
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\n上\n下\n\n", export.srt(spaced))
    }

    @Test
    fun `a subtitle track has one style for all its lines, carried into the burn-in`() {
        val timeline = withSubtitles(subtitle("S", 0, 60, "你好"), subtitle("S2", 60, 60, "再见"), titleClipOf("T", 0, 30))
        val styled = SubtitleStyleUseCase()(timeline, "S2") { SubtitleStyleControl.SIZE.write(it.copy(color = SubtitleColor.YELLOW, outline = true), 200f) }
        val style = styled.track("st1")!!.subtitleStyle
        assertEquals(SubtitleStyle(sizePx = 120, color = SubtitleColor.YELLOW, outline = true), style)
        val cues = BuildRenderPlanUseCase()(projectOf(timeline = styled)).titles.associateBy { it.clipId }
        assertEquals("every subtitle on the track", style, cues.getValue("S").subtitle)
        assertEquals(style, cues.getValue("S2").subtitle)
        assertNull("a title keeps its own look", cues.getValue("T").subtitle)
        assertEquals("请先选择一条字幕", assertThrows(EditRejected::class.java) { SubtitleStyleUseCase()(styled, "T") { it } }.message)
        val locked = styled.copy(tracks = styled.tracks.map { if (it.id == "st1") it.copy(locked = true) else it })
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { SubtitleStyleUseCase()(locked, "S") { it } }.message)
        assertEquals(2, SubtitleStyleControl.MARGIN.write(style, -5f).marginPercent)
    }
}
