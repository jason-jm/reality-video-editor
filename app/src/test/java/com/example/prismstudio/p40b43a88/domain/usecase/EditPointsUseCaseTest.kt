package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Test

class EditPointsUseCaseTest {

    private val editPoints = EditPointsUseCase()

    /** Edit points: 0, 50, 100, 120, 150, 200; a marker sits at 130. */
    private val timeline = timelineOf(
        clipOf("A", 0, 100),
        clipOf("B", 150, 50),
        clipOf("Aa", 0, 100, track = Timeline.A1),
        titleClipOf("T", 50, 70),
        markers = listOf(Marker("m", 130, "标记 1")),
    )

    @Test
    fun `edit points are the distinct sorted clip edges plus the timeline start`() {
        assertEquals(listOf(0, 50, 100, 120, 150, 200), editPoints.editPoints(timeline))
        assertEquals(listOf(0), editPoints.editPoints(Timeline.EMPTY))
    }

    @Test
    fun `previous and next walk the edit points and stop at the ends`() {
        assertEquals(50, editPoints.previous(timeline, 100))
        assertEquals(100, editPoints.previous(timeline, 101))
        assertEquals(0, editPoints.previous(timeline, 0))
        assertEquals(120, editPoints.next(timeline, 100))
        assertEquals(200, editPoints.next(timeline, 199))
        assertEquals(200, editPoints.next(timeline, 200))
        assertEquals(260, editPoints.next(timeline, 260))
    }

    @Test
    fun `snapping pulls a frame to the nearest clip edge marker or playhead within the threshold`() {
        assertEquals(100, editPoints.snap(timeline, 97, threshold = 5, playhead = 400))
        assertEquals(130, editPoints.snap(timeline, 128, threshold = 3, playhead = 400))
        assertEquals(300, editPoints.snap(timeline, 288, threshold = 15, playhead = 300))
        assertEquals(0, editPoints.snap(timeline, 2, threshold = 3, playhead = 400))
        assertEquals(140, editPoints.snap(timeline, 140, threshold = 3, playhead = 400))
    }

    @Test
    fun `snapping ignores the edges of the clips being dragged`() {
        val dragged = setOf("A", "Aa")

        assertEquals(97, editPoints.snap(timeline, 97, threshold = 5, playhead = 400, excludeIds = dragged))
        assertEquals(50, editPoints.snap(timeline, 52, threshold = 5, playhead = 400, excludeIds = dragged))
    }
}
