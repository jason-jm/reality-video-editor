package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleStyleTest {

    @Test
    fun `a new title takes its template's weight, box and entrance`() {
        assertEquals(
            listOf(TitleWeight.BOLD, false, TitleAnimation.FADE),
            TitleStyle("标题", 96, TitleTemplate.CENTER).let { listOf(it.weight, it.box, it.animation) },
        )
        assertEquals(
            listOf(TitleWeight.MEDIUM, true, TitleAnimation.FADE),
            TitleStyle("人物 · 地点", 56, TitleTemplate.LOWER_THIRD).let { listOf(it.weight, it.box, it.animation) },
        )
        assertEquals(TitleWeight.REGULAR, TitleStyle("导演", 56, TitleTemplate.CREDITS).weight)
        assertEquals(TitleAnimation.NONE, TitleStyle("对白", 44, TitleTemplate.SUBTITLE).animation)
        assertEquals(listOf(TitleColor.WHITE, false, true, 55), TitleStyle("x").let { listOf(it.color, it.outline, it.shadow, it.boxOpacityPercent) })
    }

    @Test
    fun `reset keeps the words, size and template and drops the rest of the look`() {
        val styled = TitleStyle(
            "夜跑", 120, TitleTemplate.LOWER_THIRD, TitleColor.YELLOW, TitleWeight.HEAVY,
            outline = true, shadow = false, box = false, boxOpacityPercent = 20, animation = TitleAnimation.TYPEWRITER,
        )
        assertFalse(styled.isTemplateLook)
        assertEquals(TitleStyle("夜跑", 120, TitleTemplate.LOWER_THIRD), styled.reset())
        assertTrue(styled.reset().isTemplateLook)
    }

    @Test
    fun `titles break at line breaks and dots, subtitles only at line breaks, and characters count code points`() {
        assertEquals(listOf("人物", "地点"), TitleStyle("人物 · 地点").lines())
        assertEquals(4, TitleStyle("人物 · 地点").characters())
        assertEquals(listOf("第 1 句 · 第 2 句"), TitleStyle("第 1 句 · 第 2 句", template = TitleTemplate.SUBTITLE).lines())
        assertEquals(listOf(" "), TitleStyle("  \n ").lines())
        assertEquals("an emoji is one character", 3, TitleStyle("跑🏃步").characters())
    }

    @Test
    fun `a fade comes in and goes out over a third of a second, as titles always did`() {
        // 90 frames at 30 fps: a 10-frame ramp at each end.
        val alpha = { frame: Int -> TitleAnimation.FADE.motion(frame, 90, 30, 4).alpha }
        assertEquals(0.1f, alpha(0), 1e-6f)
        assertEquals(0.5f, alpha(4), 1e-6f)
        assertEquals(1f, alpha(9), 1e-6f)
        assertEquals(1f, alpha(45), 1e-6f)
        assertEquals(0.5f, alpha(85), 1e-6f)
        assertEquals(0.1f, alpha(89), 1e-6f)
        assertEquals(TitleMotion(alpha = 1f), TitleAnimation.FADE.motion(45, 90, 30, 4))
    }

    @Test
    fun `a rise floats up 60 px into place over half a second, easing out, then fades out`() {
        val rise = { frame: Int -> TitleAnimation.RISE.motion(frame, 90, 30, 4) }
        // Frame 0 is the first of 15: p = 1/15, eased 1 − (14/15)³.
        val p = 1f / 15f
        assertEquals(p, rise(0).alpha, 1e-6f)
        assertEquals((1f - p) * (1f - p) * (1f - p) * 60f, rise(0).dropPx, 1e-4f)
        // Past halfway in time (8 of 15 frames) only a tenth of the way is left.
        assertEquals(7f / 15f * (7f / 15f) * (7f / 15f) * 60f, rise(7).dropPx, 1e-4f)
        assertEquals(TitleMotion(), rise(14))
        assertEquals(TitleMotion(alpha = 0.5f), rise(85))
    }

    @Test
    fun `a zoom grows from 80 percent into place over half a second`() {
        val zoom = { frame: Int -> TitleAnimation.ZOOM.motion(frame, 90, 30, 4) }
        val p = 1f / 15f
        assertEquals(0.8f + 0.2f * (1f - (1f - p) * (1f - p) * (1f - p)), zoom(0).scale, 1e-6f)
        assertEquals(1f, zoom(14).scale, 1e-6f)
        assertEquals(0f, zoom(0).dropPx, 0f)
        assertEquals(1f, zoom(14).alpha, 1e-6f)
    }

    @Test
    fun `the typewriter types 15 characters a second and is done by 60 percent of the title at the latest`() {
        val typed = { frame: Int, length: Int, chars: Int -> TitleAnimation.TYPEWRITER.motion(frame, length, 30, chars).characters }
        // 4 characters at 15 a second: 8 frames.
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 4), (0..8).map { typed(it, 90, 4) })
        assertEquals(8, TitleAnimation.typingFrames(4, 90, 30))
        // 100 characters in a 2-second title: 36 frames (60 %), not 200.
        assertEquals(36, TitleAnimation.typingFrames(100, 60, 30))
        assertEquals(100, typed(35, 60, 100))
        assertEquals(3, typed(0, 60, 100))
        assertEquals("no fade in while typing", 1f, TitleAnimation.TYPEWRITER.motion(0, 90, 30, 4).alpha, 0f)
        assertEquals(0.5f, TitleAnimation.TYPEWRITER.motion(85, 90, 30, 4).alpha, 1e-6f)
    }

    @Test
    fun `no animation cuts in and out`() {
        assertEquals(listOf(TitleMotion.STILL), (0 until 90).map { TitleAnimation.NONE.motion(it, 90, 30, 4) }.distinct())
    }

    @Test
    fun `each entrance describes itself for the inspector`() {
        assertEquals("直接出现，直接消失", TitleAnimation.NONE.describe(4, 90, 30))
        assertEquals("开头 0.3 秒渐显，结尾 0.3 秒渐隐", TitleAnimation.FADE.describe(4, 90, 30))
        assertEquals("开头 0.5 秒自下而上浮现，结尾 0.3 秒渐隐", TitleAnimation.RISE.describe(4, 90, 30))
        assertEquals("开头 0.5 秒由小放大，结尾 0.3 秒渐隐", TitleAnimation.ZOOM.describe(4, 90, 30))
        assertEquals("逐字打出，12 个字 0.8 秒打完，结尾 0.3 秒渐隐", TitleAnimation.TYPEWRITER.describe(12, 90, 30))
    }
}
