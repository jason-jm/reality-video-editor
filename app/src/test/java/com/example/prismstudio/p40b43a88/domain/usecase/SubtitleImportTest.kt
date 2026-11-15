package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.Charset

/** Reading .srt files onto the subtitle track. */
class SubtitleImportTest {

    private val srt = SubtitleImportUseCase(SequentialIds())
    private val fps = 30

    @Test
    fun `cues are read with their timing, lines and without formatting`() {
        val text = "1\r\n00:00:01,500 --> 00:00:03,000 X1:10 X2:20\r\n<i>你好，</i>{\\an8}\r\n世界\r\n\r\n" +
            "2\r\n00:04.25 --> 00:00:06.5\r\n<font color=\"#ff0\">Second</font>\r\n\r\n" +
            "3\r\n00:00:07,000 --> 00:00:07,000\r\nzero length\r\n\r\n" +
            "4\r\n00:00:08,000 --> 00:00:09,000\r\n\r\n"
        assertEquals(
            listOf(
                SubtitleImportUseCase.Cue(1_500, 3_000, "你好，\n世界"),
                SubtitleImportUseCase.Cue(4_250, 6_500, "Second"),
            ),
            srt.parse(text),
        )
        // Our own export reads back unchanged.
        val exported = "1\n00:00:00,000 --> 00:00:02,000\nNight run begins\n\n2\n00:00:02,000 --> 00:00:04,000\nInto the city\n\n"
        assertEquals(listOf("Night run begins", "Into the city"), srt.parse(exported).map { it.text })
    }

    @Test
    fun `files are decoded by their byte order mark, as UTF-8 when valid, else as GB18030`() {
        val line = "00:00:01,000 --> 00:00:02,000\n中文字幕\n"
        assertEquals(line, srt.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + line.toByteArray(Charsets.UTF_8)))
        assertEquals(line, srt.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + line.toByteArray(Charsets.UTF_16LE)))
        assertEquals(line, srt.decode(line.toByteArray(Charsets.UTF_8)))
        assertEquals("GBK files are common for Chinese subtitles", line, srt.decode(line.toByteArray(Charset.forName("GBK"))))
    }

    @Test
    fun `cues replace the subtitle track's subtitles on the frame grid, overlaps cut at the next cue`() {
        val old = Clip(
            id = "old", trackId = "st1", kind = ClipKind.TITLE, name = "字幕：旧", start = 0, durationFrames = 30,
            title = TitleStyle("旧", 44, TitleTemplate.SUBTITLE),
        )
        val timeline = timelineOf(old, clipOf("V", 0, 300)).let { it.copy(tracks = listOf(Track("st1", "ST1", TrackKind.SUBTITLE)) + it.tracks) }
        val cues = listOf(
            SubtitleImportUseCase.Cue(1_500, 4_000, "一"),
            SubtitleImportUseCase.Cue(3_000, 5_010, "二"),
        )
        val result = srt.import(timeline, cues, fps, "demo.srt")

        val subs = result.timeline.clips.filter { it.trackId == "st1" }
        assertEquals(listOf(listOf<Any>(45, 45, "一"), listOf<Any>(90, 60, "二")), subs.map { listOf(it.start, it.durationFrames, it.title!!.text) })
        assertEquals("Chinese cues onto the (Chinese) track there", "已导入 2 条简体中文字幕（demo.srt），替换了原有 1 条", result.message)
        assertEquals("the picture stays", 1, result.timeline.clips.count { it.id == "V" })

        val fresh = srt.import(timelineOf(), cues, fps, "demo.srt")
        assertEquals("st1", fresh.timeline.tracks.first().id)
        assertEquals("已导入 2 条简体中文字幕（demo.srt），新建字幕轨 ST1", fresh.message)
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.ZH_HANS, fresh.timeline.tracks.first().language)
        assertEquals("文件里没有可识别的字幕", assertThrows(EditRejected::class.java) { srt.import(timelineOf(), emptyList(), fps, "x.srt") }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == "st1") it.copy(locked = true) else it })
        assertThrows(EditRejected::class.java) { srt.import(locked, cues, fps, "demo.srt") }
        assertEquals(1, projectOf(timeline = result.timeline).timeline.tracks.count { it.kind == TrackKind.SUBTITLE })
    }

    @Test
    fun `a SubStation Alpha file gives its dialogue - fields by its Format line, centiseconds, line breaks, no overrides`() {
        val ass = """
            [Script Info]
            Title: 测试
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour
            Style: Default,Arial,48,&H00FFFFFF

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,注释不算
            Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\an8}你好，\N世界
            Dialogue: 0,0:00:04.00,0:00:05.25,Default,旁白,0,0,0,,一句话, 带逗号{\i1}和斜体{\i0}
            Dialogue: 0,0:01:02.07,0:01:03.00,Default,,0,0,0,,{\pos(100,200)}
        """.trimIndent()
        assertEquals(
            listOf(
                SubtitleImportUseCase.Cue(1_500, 3_000, "你好，\n世界"),
                SubtitleImportUseCase.Cue(4_000, 5_250, "一句话, 带逗号和斜体"),
            ),
            srt.parse(ass),
        )
        // An SSA v4 file names its first field Marked; the fields are found by name.
        val ssa = "[Events]\nFormat: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: Marked=0,0:00:02.00,0:00:02.50,*Default,,0000,0000,0000,,旧格式"
        assertEquals(listOf(SubtitleImportUseCase.Cue(2_000, 2_500, "旧格式")), srt.parse(ssa))
    }

    @Test
    fun `a WebVTT file gives its cues without the header, notes, styles, identifiers, cue settings, tags or entities`() {
        val vtt = """
            WEBVTT - 测试

            STYLE
            ::cue { color: yellow }

            NOTE 这是注释，
            不是字幕

            intro
            00:01.500 --> 00:03.000 align:start position:10%
            <v 主持人>大家好 &amp; 欢迎</v>

            00:00:04.000 --> 00:00:05.000
            <c.yellow>第二</c>句 <00:00:04.500>有时间标记 &lt;3
        """.trimIndent()
        assertEquals(
            listOf(
                SubtitleImportUseCase.Cue(1_500, 3_000, "大家好 & 欢迎"),
                SubtitleImportUseCase.Cue(4_000, 5_000, "第二句 有时间标记 <3"),
            ),
            srt.parse(vtt),
        )
    }
}
