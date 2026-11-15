package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AddTitleUseCaseTest {

    private val addTitle = AddTitleUseCase(SequentialIds())

    @Test
    fun `places a three second title built from the template on V2 at the playhead`() {
        val result = addTitle(Timeline.EMPTY, TitleTemplate.LOWER_THIRD, atFrame = 45, fps = 25)

        val title = result.timeline.clips.single()
        assertEquals("t-1", title.id)
        assertEquals(Timeline.V2, title.trackId)
        assertEquals(ClipKind.TITLE, title.kind)
        assertEquals(45, title.start)
        assertEquals(75, title.durationFrames)
        assertEquals(TitleStyle("人物 · 地点", 56, TitleTemplate.LOWER_THIRD), title.title)
        assertEquals("标题：人物 · 地点", title.name)
        assertEquals(title.id, result.selectedClipId)
        assertEquals("已在 00:00:01:20 添加标题", result.message)
    }

    @Test
    fun `skips occupied V2 clips and gaps that are too short for the title`() {
        // V2 holds [0, 50) and [60, 200): the 10-frame gap cannot take a 90-frame title.
        val timeline = timelineOf(titleClipOf("t-a", 0, 50), titleClipOf("t-b", 60, 140), clipOf("v", 0, 300))

        val result = addTitle(timeline, TitleTemplate.CENTER, atFrame = 10, fps = 30)

        val added = result.timeline.clip(result.selectedClipId)!!
        assertEquals(200, added.start)
        val others = result.timeline.clipsOn(Timeline.V2).filter { it.id != added.id }
        assertFalse(others.any { added.start < it.end && added.end > it.start })
    }

    @Test
    fun `uses the playhead when the V2 slot there is exactly free and ignores other tracks`() {
        val timeline = timelineOf(titleClipOf("t-a", 0, 100), titleClipOf("t-b", 190, 60), clipOf("v", 0, 400))

        val result = addTitle(timeline, TitleTemplate.CREDITS, atFrame = 100, fps = 30)

        assertEquals(100, result.timeline.clip(result.selectedClipId)!!.start)
    }

    @Test
    fun `a negative playhead starts the title at frame zero`() {
        val result = addTitle(Timeline.EMPTY, TitleTemplate.ROLL, atFrame = -40, fps = 30)

        assertEquals(0, result.timeline.clips.single().start)
    }
}
