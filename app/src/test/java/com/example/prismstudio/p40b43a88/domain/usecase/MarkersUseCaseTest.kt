package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's marker index: names, colours, notes, deletion and stepping between markers. */
class MarkersUseCaseTest {

    private val markers = MarkersUseCase()
    private val timeline = timelineOf(markers = listOf(Marker("a", 30, "标记 1"), Marker("b", 90, "标记 2"), Marker("c", 150, "标记 3")))

    private fun rejected(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `a marker is renamed, coloured and annotated`() {
        val named = markers.rename(timeline, "b", "  开场  ")
        assertEquals("开场", named.markers[1].label)
        assertEquals("标记名称不能为空", rejected { markers.rename(timeline, "b", "   ") })
        assertEquals(MarkersUseCase.MAX_NAME, markers.rename(timeline, "b", "名".repeat(60)).markers[1].label.length)
        assertEquals(MarkerColor.RED, markers.recolor(timeline, "b", MarkerColor.RED).markers[1].color)
        assertEquals("音乐在此进入", markers.annotate(timeline, "b", "音乐在此进入").markers[1].note)
        assertEquals("", markers.annotate(timeline, "b", "").markers[1].note)
        assertEquals("标记已删除", rejected { markers.recolor(timeline, "gone", MarkerColor.RED) })
    }

    @Test
    fun `a marker drags along the ruler with its length, held to the timeline and off another marker's frame`() {
        val moved = markers.move(timeline, "b", 120, 300)
        assertEquals(listOf("a" to 30, "b" to 120, "c" to 150), moved.markers.map { it.id to it.frame })
        assertEquals("past another one it takes its place in time order", listOf("a", "c", "b"), markers.move(timeline, "b", 200, 300).markers.map { it.id })
        val long = markers.setDuration(timeline, "b", 60, 300)
        assertEquals("its 60 frames stay inside the 300", 240, markers.move(long, "b", 299, 300).markers.single { it.id == "b" }.frame)
        assertEquals(0, markers.move(timeline, "b", -40, 300).markers.first().frame)
        assertEquals("那里已有另一个标记", rejected { markers.move(timeline, "b", 150, 300) })
    }

    @Test
    fun `a duration marker spans a range, held to the timeline, taken from In and Out or handed to them`() {
        val long = markers.setDuration(timeline, "b", 120, 300)
        assertEquals(120, long.markers[1].durationFrames)
        assertEquals(210, long.markers[1].endFrame)
        assertEquals("never past the end", 210, markers.setDuration(timeline, "b", 999, 300).markers[1].durationFrames)
        assertEquals("a point again", 0, markers.setDuration(long, "b", -5, 300).markers[1].durationFrames)
        // From the timeline's In and Out: the marker moves to the In.
        val marked = timeline.copy(marks = TimelineMarks(200, 260))
        val taken = markers.rangeFromMarks(marked, "b").markers.single { it.id == "b" }
        assertEquals(200 to 60, taken.frame to taken.durationFrames)
        assertEquals("先在时间线上标记入点和出点", rejected { markers.rangeFromMarks(timeline, "b") })
        assertEquals("入点处已有另一个标记", rejected { markers.rangeFromMarks(timeline.copy(marks = TimelineMarks(150, 200)), "b") })
        // To the timeline's In and Out, for the Deliver page's 入点至出点.
        assertEquals(TimelineMarks(90, 210), markers.marksFromRange(long, "b").marks)
        assertEquals("这个标记没有范围：先设置时长或用入出点", rejected { markers.marksFromRange(timeline, "b") })
    }

    @Test
    fun `a marker is deleted, and the markers either side of a frame are found`() {
        val (left, message) = markers.remove(timeline, "b")
        assertEquals(listOf("a", "c"), left.markers.map { it.id })
        assertEquals("已删除“标记 2”", message)
        assertEquals("b", markers.previous(timeline, 100)?.id)
        assertEquals("a", markers.previous(timeline, 90)?.id)
        assertEquals("c", markers.next(timeline, 90)?.id)
        assertNull(markers.previous(timeline, 30))
        assertNull(markers.next(timeline, 150))
    }

    @Test
    fun `a new marker is numbered past the highest, so a deleted number is not given again`() {
        val add = AddMarkerUseCase(SequentialIds())
        val (left, _) = markers.remove(timeline, "a")
        val added = add(left, 200, null, 30)
        assertEquals("标记 4", added.timeline.markers.last().label)
        assertEquals(MarkerColor.BLUE, added.timeline.markers.last().color)
    }
}
