package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleAnimation
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TitleStyleUseCaseTest {

    private val styles = TitleStyleUseCase()
    private val timeline = timelineOf(titleClipOf("t", 0, 90, text = "夜跑"), clipOf("v", 0, 90))

    @Test
    fun `changes the title's look and nothing else`() {
        val changed = styles(timeline, "t") {
            it.copy(color = TitleColor.YELLOW, weight = TitleWeight.HEAVY, outline = true, box = true, animation = TitleAnimation.TYPEWRITER)
        }
        assertEquals(
            TitleStyle("夜跑", color = TitleColor.YELLOW, weight = TitleWeight.HEAVY, outline = true, box = true, animation = TitleAnimation.TYPEWRITER),
            changed.clip("t")!!.title,
        )
        assertEquals(timeline.clip("t")!!.copy(title = changed.clip("t")!!.title), changed.clip("t"))
    }

    @Test
    fun `the words, size and template stay, and the box opacity is held to 0 to 100`() {
        val changed = styles(timeline, "t") { it.copy(text = "别的", sizePx = 10, template = TitleTemplate.ROLL, boxOpacityPercent = 140) }
        assertEquals(TitleStyle("夜跑", boxOpacityPercent = 100), changed.clip("t")!!.title)
        assertEquals(0, styles(timeline, "t") { it.copy(boxOpacityPercent = -5) }.clip("t")!!.title!!.boxOpacityPercent)
    }

    @Test
    fun `subtitles, other clips, locked tracks and missing clips are refused`() {
        val subtitle = titleClipOf("s", 0, 60).let { it.copy(title = TitleStyle("对白", 44, TitleTemplate.SUBTITLE)) }
        val withSubtitle = timeline.copy(clips = timeline.clips + subtitle)
        assertEquals("字幕的样式在轨道样式里调整", assertThrows(EditRejected::class.java) { styles(withSubtitle, "s") { it } }.message)
        assertEquals("所选片段不是标题", assertThrows(EditRejected::class.java) { styles(timeline, "v") { it } }.message)
        assertEquals("请先在时间线上选择标题", assertThrows(EditRejected::class.java) { styles(timeline, "nope") { it } }.message)
        val locked = timelineOf(titleClipOf("t", 0, 90), locked = setOf(Timeline.V2))
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { styles(locked, "t") { it.copy(outline = true) } }.message)
    }
}
