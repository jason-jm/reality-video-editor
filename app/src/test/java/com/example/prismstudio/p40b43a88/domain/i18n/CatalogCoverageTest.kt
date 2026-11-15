package com.example.prismstudio.p40b43a88.domain.i18n

import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every Chinese text in the code has its translation: devtools/i18n/msgids.tsv (extract.py, checked by the validator)
 * against each language's table, placeholders kept, no Chinese left in Latin-script tables.
 */
class CatalogCoverageTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "devtools/i18n/msgids.tsv").exists() }
    private val msgids = File(root, "devtools/i18n/msgids.tsv").readLines().map { Catalog.unescape(it.substringBefore('\t')) }.filter { it.isNotEmpty() }
    private val exempt = File(root, "devtools/i18n/exempt.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map(Catalog::unescape).toSet()

    private fun table(tag: String): Map<String, String> =
        File(root, "app/src/main/resources/i18n/$tag.tsv").readLines()
            .filter { '\t' in it && !it.startsWith("#") }
            .associate { Catalog.unescape(it.substringBefore('\t')) to Catalog.unescape(it.substringAfter('\t')) }

    @Test
    fun `the product name survives translation inside a sentence in every app language`() {
        I18n.install(AppLanguage.EN, Catalog(table("en")))
        val name = "Reality Video Editor"
        for (language in AppLanguage.entries) {
            assertEquals(name, I18n.translate(name, language))
            assertTrue(name in I18n.translate("关于 $name", language))
            assertTrue(name in I18n.translate("请在系统设置的应用权限里允许 $name 访问", language))
        }
        assertEquals("About $name", I18n.translate("关于 $name", AppLanguage.EN))
    }

    @Test
    fun `english covers every text of the code and keeps its placeholders`() {
        val en = table("en")
        val missing = msgids.filter { it !in exempt && it !in en }
        assertTrue("untranslated: ${missing.take(20)}", missing.isEmpty())
        val placeholder = Regex("""\{(\d+)[,}]""")
        val broken = en.filter { (src, tr) -> placeholder.findAll(src).map { it.groupValues[1] }.toSet() != placeholder.findAll(tr).map { it.groupValues[1] }.toSet() }
        assertTrue("placeholders differ: ${broken.keys.take(10)}", broken.isEmpty())
        val chinese = en.filter { (src, tr) -> I18n.HAN.containsMatchIn(tr) && tr != src }
        assertTrue("chinese left: ${chinese.values.take(10)}", chinese.isEmpty())
    }

    @Test
    fun `composed messages from the code read as english sentences`() {
        val en = Catalog.parse(File(root, "app/src/main/resources/i18n/en.tsv").readText())
        I18n.install(AppLanguage.EN, en)
        val wav = RenderSettings(fileName = "test", includeVideo = false, audioFormat = AudioExportFormat.WAV)
        for ((rate, bits) in listOf(44_100 to 16, 48_000 to 24)) {
            val note = I18n.translate(wav.copy(wavSampleRate = rate, wavBits = bits).audioFormatNote, AppLanguage.EN)
            assertTrue(note, !I18n.HAN.containsMatchIn(note) && "Music/Reality Video Editor" in note && RenderSettings.wavRateLabel(rate) in note)
            assertEquals(bits == 24, "24-bit" in note)
        }
        val summary = listOf("1 个任务失败，请查看渲染队列", "1 个字幕文件保存失败，请检查存储空间",
            "已保存 1 个视频到 Movies/Reality Video Editor", "已保存 1 个音频文件到 Music/Reality Video Editor", "已保存 1 个字幕文件到 Download/Reality Video Editor")
        assertEquals(
            listOf("Failed tasks (1). See the render queue.", "Subtitle files that could not be saved (1). Check available storage.",
                "Videos saved to Movies/Reality Video Editor (1)", "Audio files saved to Music/Reality Video Editor (1)", "Subtitles saved to Download/Reality Video Editor (1)"),
            I18n.translate(summary.joinToString("\n"), AppLanguage.EN).lines(),
        )
        // KeyframeUseCase.toggleGroup, ClipMaskUseCase.shape, a toast with a clip's name, a glued badge row.
        assertEquals("Added a Zoom keyframe · 3 in all", I18n.translate("已为缩放添加关键帧 · 共 3 个", AppLanguage.EN))
        assertEquals("Added a Circle mask: the tracks below show outside the shape", I18n.translate("已添加圆形遮罩：形状之外露出下层轨道", AppLanguage.EN))
        assertEquals("Deleted “sample_bars.mp4”, leaving a gap", I18n.translate("已删除“sample_bars.mp4”，保留空隙", AppLanguage.EN))
        assertEquals("Disabled · Offline · Color", I18n.translate("已停用 · 离线 · 调色", AppLanguage.EN))
        // Messages the code glues from pieces: a fragment template and what follows, lines said apart, a phrase with
        // its punctuation, a number before the words.
        assertEquals("Estimated 11 MB · 00:00:34:00 · H.264 720p 30 fps", I18n.translate("预计 11 MB · 00:00:34:00 · H.264 720p 30 fps", AppLanguage.EN))
        assertEquals("Once · Timeline 1", I18n.translate("1 次 · 时间线 1", AppLanguage.EN))
        assertEquals("3 times · Timeline 1 ×2", I18n.translate("3 次 · 时间线 1 ×2", AppLanguage.EN))
        assertEquals(
            "Program loudness -18.7 LUFS · true peak ≤ -9.2 dBTP\nOverall +4.7 dB on export → -14.0 LUFS",
            I18n.translate("节目响度 -18.7 LUFS · 真峰值 ≤ -9.2 dBTP\n导出时整体 +4.7 dB → -14.0 LUFS", AppLanguage.EN),
        )
        assertEquals("01 Primary", I18n.translate("01 主校色", AppLanguage.EN))
        assertEquals("03  Freeze · sample_fractal_720p", I18n.translate("03  定格 · sample_fractal_720p", AppLanguage.EN))
        assertEquals(
            "Burned into the picture: 简体中文 (ST1); Subtitle file: CHS .srt",
            I18n.translate("画面烧录：简体中文（ST1）；字幕文件：简中 .srt", AppLanguage.EN),
        )
        assertEquals("Titles", I18n.translate("标题", "tab", AppLanguage.EN))
        assertEquals("Title", I18n.translate("标题", AppLanguage.EN))
        // A template whose value is a whole sentence with its own commas.
        assertEquals(
            "By project setting (Fit): Media and timeline have the same shape: Fit, Fill and Stretch look the same",
            I18n.translate("按项目设置（适应）：素材与时间线的画面比例相同，适应、填充、拉伸看起来一样", AppLanguage.EN),
        )
        assertEquals("plural form", "3 clips", I18n.translate("3 个片段", AppLanguage.EN))
        assertEquals("1 clip", I18n.translate("1 个片段", AppLanguage.EN))
        assertEquals("names the app made", "Timeline 2", I18n.translate("时间线 2", AppLanguage.EN))
        // OfflineRenderUseCase.note: clip names (the user's words) glued with 、, and what the render does (release R5).
        assertEquals(
            "2 clips have offline media (Beach、Bars): black picture in the render. Relink on the Media page first",
            I18n.translate("2 个片段的素材离线（Beach、Bars），渲染时画面是黑场：先到媒体页重新链接", AppLanguage.EN),
        )
        assertEquals(
            "1 clip has offline media (Beach): black picture, no sound in the render. Relink on the Media page first",
            I18n.translate("1 个片段的素材离线（Beach），渲染时画面是黑场、没有声音：先到媒体页重新链接", AppLanguage.EN),
        )
        // ImportUi.missingNote: one kind or two glued with 、 (release R2).
        assertEquals("Not allowed to read music and audio, so they are not listed here", I18n.translate("没有获得访问音乐和音频的权限，这里不会列出它们", AppLanguage.EN))
        assertEquals("Not allowed to read Video, photos, so they are not listed here", I18n.translate("没有获得访问视频、照片的权限，这里不会列出它们", AppLanguage.EN))
        // WindowTrackUseCase.motionNote glued after the tracking toast: none, one or both phrases (rev 170).
        assertEquals(
            "Tracked the mask: 14 keyframes, moving up to 12% of the picture",
            I18n.translate("已跟踪遮罩：14 个关键帧，最多移动画面的 12%", AppLanguage.EN),
        )
        assertEquals(
            "Tracked the window: 9 keyframes, moving up to 30% of the picture, turning up to 24°",
            I18n.translate("已跟踪窗口：9 个关键帧，最多移动画面的 30%，最多转动 24°", AppLanguage.EN),
        )
        assertEquals(
            "Tracked the mask: 39 keyframes, moving up to 30% of the picture, size changing up to 35%, turning up to 24°",
            I18n.translate("已跟踪遮罩：39 个关键帧，最多移动画面的 30%，大小最多变化 35%，最多转动 24°", AppLanguage.EN),
        )
        I18n.install(AppLanguage.EN, Catalog.parse(""))
    }

    @Test
    fun `a new title's words, an edited name's stored form, follow the language shown`() {
        val timeline = com.example.prismstudio.p40b43a88.domain.timelineOf()
        val add = com.example.prismstudio.p40b43a88.domain.usecase.AddTitleUseCase(com.example.prismstudio.p40b43a88.domain.SequentialIds())
        val template = com.example.prismstudio.p40b43a88.domain.model.TitleTemplate.LOWER_THIRD
        try {
            I18n.language = AppLanguage.EN
            assertEquals("Name · Place", add(timeline, template, 0, 30).timeline.clips.single().title!!.text)
            // A name left as shown keeps its stored form; a changed one is what was typed.
            assertEquals("时间线 1", I18n.stored("Timeline 1 ", "时间线 1"))
            assertEquals("Edit 2", I18n.stored("Edit 2", "时间线 1"))
        } finally {
            I18n.language = AppLanguage.ZH_HANS
        }
        assertEquals("人物 · 地点", add(timeline, template, 0, 30).timeline.clips.single().title!!.text)
    }
}
