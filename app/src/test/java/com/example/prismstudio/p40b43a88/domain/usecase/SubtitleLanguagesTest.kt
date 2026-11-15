package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.EN
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.JA
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.KO
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.RU
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.ZH_HANS
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.ZH_HANT
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Subtitle tracks per language (Resolve): which language a file holds, one shown at a time, a file each on delivery. */
class SubtitleLanguagesTest {

    private val tracks = TracksUseCase()
    private val import = SubtitleImportUseCase(SequentialIds())
    private val export = SubtitleExportUseCase()
    private val fps = 30

    private fun sub(id: String, track: String, start: Int, text: String) = Clip(
        id = id, trackId = track, kind = ClipKind.TITLE, name = "字幕：$text", start = start, durationFrames = 30,
        title = TitleStyle(text, 44, TitleTemplate.SUBTITLE),
    )

    /** ST2 English (hidden) above ST1 Chinese (shown), a line each, over a picture. */
    private val two = timelineOf(clipOf("V", 0, 300), sub("zh", "st1", 0, "你好"), sub("en", "st2", 30, "Hello")).let {
        it.copy(tracks = listOf(Track("st2", "ST2", TrackKind.SUBTITLE, enabled = false, language = EN), Track("st1", "ST1", TrackKind.SUBTITLE, language = ZH_HANS)) + it.tracks)
    }

    @Test
    fun `a file's language comes from a tag in its name, else from its script`() {
        assertEquals(EN, SubtitleLanguage.detect("Movie.2024.en.srt", "你好"))
        assertEquals(ZH_HANS, SubtitleLanguage.detect("片名_chs.ass", "Hello"))
        assertEquals(ZH_HANT, SubtitleLanguage.detect("show.zh-TW.vtt", ""))
        assertEquals(JA, SubtitleLanguage.detect("a.jpn.srt", ""))
        assertEquals("the last tag wins", EN, SubtitleLanguage.detect("film.zh.en.srt", ""))
        assertEquals(ZH_HANS, SubtitleLanguage.detect("x.srt", "我们这个时候说话"))
        assertEquals(ZH_HANT, SubtitleLanguage.detect("x.srt", "我們這個時候說話"))
        assertEquals("kana means Japanese, even among kanji", JA, SubtitleLanguage.detect("x.srt", "今日はいい天気ですね"))
        assertEquals(KO, SubtitleLanguage.detect("x.srt", "안녕하세요"))
        assertEquals(RU, SubtitleLanguage.detect("x.srt", "Привет, мир"))
        assertEquals(EN, SubtitleLanguage.detect("x.srt", "Hello there"))
        assertNull(SubtitleLanguage.detect("x.srt", "123 456"))
    }

    @Test
    fun `a new subtitle track takes the next language and shows alone, one track per language up to four`() {
        val doc = projectOf(timeline = two)
        val added = tracks.addSubtitle(doc, null)
        val st3 = added.doc.timeline.tracks.first()
        assertEquals(listOf("st3", ZH_HANT, true), listOf(st3.id, st3.language, st3.enabled))
        assertEquals("the others hide", listOf(false, false), added.doc.timeline.tracks.filter { it.kind == TrackKind.SUBTITLE && it.id != "st3" }.map { it.enabled })
        assertEquals("已添加字幕轨 ST3（繁體中文），显示在画面上", added.message)
        assertEquals("已有English字幕轨", assertThrows(EditRejected::class.java) { tracks.addSubtitle(doc, EN) }.message)
        val four = tracks.addSubtitle(added.doc, JA).doc
        assertEquals("最多 4 条字幕轨（每种语言一条）", assertThrows(EditRejected::class.java) { tracks.addSubtitle(four, null) }.message)
        // Showing a track hides the others; hiding leaves none shown.
        val shown = tracks.setEnabled(two, "st2", true)
        assertEquals(listOf(true, false), shown.tracks.take(2).map { it.enabled })
        assertEquals(listOf(false, false), tracks.setEnabled(shown, "st2", false).tracks.take(2).map { it.enabled })
        // A language once.
        assertEquals("ST2 已经是English字幕", assertThrows(EditRejected::class.java) { tracks.setLanguage(two, "st1", EN) }.message)
        assertEquals(JA, tracks.setLanguage(two, "st1", JA).track("st1")!!.language)
        assertEquals("只有字幕轨有语言", assertThrows(EditRejected::class.java) { tracks.setLanguage(two, Timeline.V1, JA) }.message)
    }

    @Test
    fun `an imported file goes to its language's track - a new one if none - and that track shows`() {
        val cues = listOf(SubtitleImportUseCase.Cue(1_000, 2_000, "Good morning"), SubtitleImportUseCase.Cue(3_000, 4_000, "Good night"))
        val en = import.import(two, cues, fps, "film.srt")
        assertEquals("English lands on ST2, which shows", listOf(true, false), en.timeline.tracks.take(2).map { it.enabled })
        assertEquals(listOf("Good morning", "Good night"), en.timeline.clipsOn("st2").map { it.title!!.text })
        assertEquals("已导入 2 条English字幕（film.srt），替换了原有 1 条", en.message)
        assertEquals("the Chinese line stays", listOf("你好"), en.timeline.clipsOn("st1").map { it.title!!.text })
        val ja = import.import(two, listOf(SubtitleImportUseCase.Cue(0, 1_000, "こんにちは")), fps, "film.srt")
        assertEquals(listOf("st3", JA), ja.timeline.tracks.first().let { listOf(it.id, it.language) })
        assertEquals("已导入 1 条日本語字幕（film.srt），新建字幕轨 ST3", ja.message)
    }

    @Test
    fun `each language is delivered as its own file, the shown one first, and the note says what is burned in`() {
        val files = export.files(projectOf(timeline = two), SubtitleFileFormat.SRT)
        assertEquals(listOf(ZH_HANS, EN), files.map { it.language })
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello\n\n", files[1].text)
        assertEquals(listOf("Film.zh-Hans.srt", "Film.en.srt"), files.map { export.fileName("Film", it, several = true, format = SubtitleFileFormat.SRT) })
        assertEquals("Film.vtt", export.fileName("Film", files[0], several = false, format = SubtitleFileFormat.WEBVTT))
        val doc = projectOf(timeline = two)
        assertEquals("画面烧录：简体中文（ST1）；字幕文件：简中、EN，各一个 .srt", export.note(doc, SubtitleFileFormat.SRT, exportFiles = true))
        assertEquals("画面烧录：简体中文（ST1）；打开「字幕文件」可把 2 种语言各存一个文件", export.note(doc, SubtitleFileFormat.SRT, exportFiles = false))
        val hidden = projectOf(timeline = tracks.setEnabled(two, "st1", false))
        assertEquals("画面烧录：无（字幕轨都已隐藏）；字幕文件：EN、简中，各一个 .srt", export.note(hidden, SubtitleFileFormat.SRT, exportFiles = true))
        assertNull(export.note(projectOf(timeline = timelineOf(clipOf("V", 0, 30))), SubtitleFileFormat.SRT, true))
        // Only the shown language burns into the picture.
        assertEquals(listOf("zh"), BuildRenderPlanUseCase()(doc).titles.map { it.clipId })
    }

    @Test
    fun `a track's language survives a round trip, an older subtitle track reads as Simplified Chinese`() {
        val doc = projectOf(timeline = two)
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc))
        assertEquals(listOf(EN, ZH_HANS), reread.timeline.tracks.take(2).map { it.language })
        val old = org.json.JSONObject(ProjectJsonCodec.encode(doc))
        old.getJSONObject("timeline").getJSONArray("tracks").getJSONObject(1).remove("lang")
        val track = ProjectJsonCodec.decode(old.toString()).timeline.track("st1")!!
        assertNull(track.language)
        assertEquals(ZH_HANS, track.subtitleLanguage)
        // A new subtitle goes on the track on screen.
        val shownEn = tracks.setEnabled(two, "st2", true)
        val added = AddTitleUseCase(SequentialIds())(shownEn, TitleTemplate.SUBTITLE, 200, fps)
        assertEquals("st2", added.timeline.clip(added.selectedClipId)!!.trackId)
    }
}
