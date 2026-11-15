package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddMarkerUseCaseTest {

    private val addMarker = AddMarkerUseCase(SequentialIds())

    @Test
    fun `adds a numbered marker at the playhead keeping markers sorted and the selection`() {
        val timeline = timelineOf(markers = listOf(Marker("m-old", 90, "标记 1")))

        val result = addMarker(timeline, frame = 30, selectedClipId = "c-selected", fps = 30)

        assertEquals(listOf(30, 90), result.timeline.markers.map { it.frame })
        assertEquals(Marker("m-1", 30, "标记 2"), result.timeline.markers.first())
        assertEquals("c-selected", result.selectedClipId)
        assertEquals("已在 00:00:01:00 添加标记 2", result.message)
    }

    @Test
    fun `refuses a second marker on the same frame`() {
        val timeline = timelineOf(markers = listOf(Marker("m-old", 45, "标记 1")))

        val error = assertThrows(EditRejected::class.java) { addMarker(timeline, 45, null, 30) }

        assertEquals("该位置已有标记", error.message)
    }

    @Test
    fun `a negative playhead drops the marker at frame zero`() {
        val result = addMarker(Timeline.EMPTY, frame = -12, selectedClipId = null, fps = 30)

        assertEquals(0, result.timeline.markers.single().frame)
        assertThrows(EditRejected::class.java) { addMarker(result.timeline, -3, null, 30) }
    }
}
