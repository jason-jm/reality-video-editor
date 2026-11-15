package com.example.prismstudio.p40b43a88.domain.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Display-time translation: exact phrases, composed messages matched to templates, glued phrases, plurals. */
class I18nTest {

    private val table = Catalog.parse(
        """
        # comment
        缩放	Scale
        位置	Position
        圆形	Circle
        窗口外	Outside the window
        已为{0}添加关键帧 · 共 {1} 个	Added a {0} keyframe · {1,plural,one{# keyframe}other{# keyframes}} in all
        已删除{0}的关键帧，{0}不再变化	Removed the {0} keyframe; {0} no longer changes
         秒	 s
        片段：	Clip:
        第一行\n第二行	Line one\nline two
        """.trimIndent(),
    )

    @After
    fun reset() {
        I18n.language = AppLanguage.ZH_HANS
        I18n.install(AppLanguage.EN, I18n.catalog(AppLanguage.EN).let { Catalog.parse("") })
    }

    private fun en(text: String): String {
        I18n.install(AppLanguage.EN, table)
        return I18n.translate(text, AppLanguage.EN)
    }

    @Test
    fun `chinese is the source and passes through, text without chinese too`() {
        I18n.install(AppLanguage.EN, table)
        assertEquals("已为缩放添加关键帧 · 共 3 个", I18n.translate("已为缩放添加关键帧 · 共 3 个", AppLanguage.ZH_HANS))
        assertEquals("00:00:12:05", I18n.translate("00:00:12:05", AppLanguage.EN))
        assertEquals("My clip.mp4", I18n.translate("My clip.mp4", AppLanguage.EN))
    }

    @Test
    fun `exact phrases, templates with their values translated, and plurals`() {
        assertEquals("Scale", en("缩放"))
        assertEquals("Added a Scale keyframe · 3 keyframes in all", en("已为缩放添加关键帧 · 共 3 个"))
        assertEquals("Added a Position keyframe · 1 keyframe in all", en("已为位置添加关键帧 · 共 1 个"))
        assertEquals("a value used twice", "Removed the Scale keyframe; Scale no longer changes", en("已删除缩放的关键帧，缩放不再变化"))
        assertEquals("escapes", "Line one\nline two", en("第一行\n第二行"))
    }

    @Test
    fun `glued phrases and fragments are translated piece by piece`() {
        assertEquals("Circle · Outside the window", en("圆形 · 窗口外"))
        assertEquals("a suffix fragment", "2.5 s", en("2.5 秒"))
        assertEquals("a prefix fragment", "Clip:3", en("片段：3"))
        assertEquals("surrounding blanks kept", "  Scale ", en("  缩放 "))
        assertEquals("latin punctuation", "Scale, Position", en("缩放，位置"))
        assertEquals("a run of chinese in mixed text", "1920×1080 Circle", en("1920×1080 圆形"))
    }

    @Test
    fun `what has no translation stays as it is and is noted for QA`() {
        I18n.missing.clear()
        assertEquals("未知的文字", en("未知的文字"))
        assertTrue("未知的文字" in I18n.missing)
    }

    @Test
    fun `the pseudo-locale is english accented and a third longer`() {
        I18n.install(AppLanguage.EN, table)
        val p = I18n.translate("位置", AppLanguage.PSEUDO)
        assertTrue(p, p.startsWith("[Ƥöšîţîöñ".take(2)) || p.startsWith("[P"))
        assertTrue(p, p.contains("ö") && p.endsWith("]") && p.length > "Position".length + 2)
    }

    @Test
    fun `names the app makes are formatted in the language`() {
        val t = Catalog.parse("时间线 {0}\tTimeline {0}")
        I18n.install(AppLanguage.EN, t)
        assertEquals("Timeline 2", I18n.format("时间线 {0}", 2, language = AppLanguage.EN))
        assertEquals("时间线 2", I18n.format("时间线 {0}", 2, language = AppLanguage.ZH_HANS))
    }
}
