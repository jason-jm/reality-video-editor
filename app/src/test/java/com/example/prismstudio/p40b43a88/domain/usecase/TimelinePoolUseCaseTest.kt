package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** The media pool's 时间线 bin: a card per timeline with a miniature of its tracks. */
class TimelinePoolUseCaseTest {

    private val use = TimelinePoolUseCase()

    @Test
    fun `every timeline in order, with its clips as rows and spans of its length`() {
        val open = timelineOf(clipOf("a", 0, 60), clipOf("b", 60, 60), clipOf("s", 30, 90, Timeline.A2))
        val base = projectOf(open)
        val doc = base.copy(timelines = listOf(StoredTimeline("tl-2", "副本", base.format, Timeline.EMPTY, base.mixer)))
        val cards = use(doc)
        assertEquals(listOf(doc.timelineId, "tl-2"), cards.map { it.id })
        val first = cards.first()
        assertEquals(true to 3, first.open to first.clips)
        assertEquals("V2, V1, A1, A2", 4, first.rows)
        assertEquals(TimelinePoolUseCase.Bar(1, 0f, 0.5f, ClipKind.VIDEO), first.bars[0])
        assertEquals(TimelinePoolUseCase.Bar(3, 0.25f, 1f, ClipKind.AUDIO), first.bars[2])
        assertEquals(0 to 0, cards[1].clips to cards[1].durationFrames)
        assertEquals(listOf("tl-2"), use(doc, "副本").map { it.id })
        assertEquals("2 条时间线 · 当前打开“${doc.timelineName}”", use.summary(doc))
    }
}
