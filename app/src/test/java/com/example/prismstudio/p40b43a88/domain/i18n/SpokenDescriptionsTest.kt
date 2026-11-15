package com.example.prismstudio.p40b43a88.domain.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/** Accessibility descriptions (semantics contentDescription) read in the language shown, part by part (release round R8). */
class SpokenDescriptionsTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "devtools/i18n/msgids.tsv").exists() }

    @Before
    fun english() {
        I18n.install(AppLanguage.EN, Catalog.parse(File(root, "app/src/main/resources/i18n/en.tsv").readText()))
        I18n.language = AppLanguage.EN
    }

    @After
    fun chinese() {
        I18n.language = AppLanguage.ZH_HANS
    }

    @Test
    fun `parts are translated alone and joined with latin punctuation`() {
        // ClipStrip: a clip's own name stays as it is, the flags are listed.
        val clip = spoken(
            "片段 3 夜跑开始".localized(), "已调色".localized(),
            labelled("旗标".localized(), listed(listOf("红色".localized(), "蓝色".localized()))),
        )
        assertEquals("Clip 3 夜跑开始, graded, Flags: Red, Blue", clip)
        // BinNavigation, MediaPoolList, KeyframeCurvePanel, NodeStrip: counted phrases and the parts added in R8.
        assertEquals("Bin 素材, 12 media items, long-press to rename or delete",
            spoken("媒体夹 素材".localized(), "12 个素材".localized(), "长按重命名或删除".localized()))
        assertEquals("used 1 time", "使用 1 次".localized())
        assertEquals("3 keyframes", "3 个关键帧".localized())
        assertEquals("Node 2 Primary", "节点 2 主校色".localized())
        assertEquals("Angle 1: 夜跑", labelled("机位 1".localized(), "夜跑"))
        // An app-made name holding separators fills the template as it is (ClipStrip); the loudness meter part by part.
        assertEquals("Clip 03 Freeze · sample_fractal_720p", I18n.format("片段 {0} {1}", "03", "定格 · sample_fractal_720p".localized()))
        assertEquals("Program loudness: Momentary -12.7, Short-term -12.5, Integrated -18.7 LUFS, Loudness range 15.2 LU, True peak -9.2 dBTP",
            labelled("节目响度".localized(), spoken("瞬时 -12.7".localized(), "短期 -12.5".localized(), "整体 -18.7 LUFS".localized(),
                "响度范围 15.2 LU".localized(), "真峰值 -9.2 dBTP".localized())))
        assertEquals("Proxy: Proxy 960×540 · 2 MB, tap to make or delete the proxy",
            spoken(labelled("代理".localized(), "代理 960×540 · 2 MB".localized()), "点按生成或删除代理".localized()))
    }

    @Test
    fun `whole descriptions from the code read in english`() {
        assertEquals("Color warper grid: drag the points to change hue and saturation", "色彩扭曲器网格：拖动点改变色相和饱和度".localized())
        assertEquals("Timecode 01:00:12:05, tap to enter", "时间码 01:00:12:05，点按输入".localized())
        assertEquals("Grade version: Version 1, 2 in all, tap to switch, create or delete", "调色版本：版本 1，共 2 个，点按切换、新建或删除".localized())
    }

    @Test
    fun `chinese keeps its own punctuation`() {
        I18n.language = AppLanguage.ZH_HANS
        assertEquals("片段 3，已调色，旗标：红色、蓝色", spoken("片段 3", "", "已调色", labelled("旗标", listed(listOf("红色", "蓝色")))))
    }
}
