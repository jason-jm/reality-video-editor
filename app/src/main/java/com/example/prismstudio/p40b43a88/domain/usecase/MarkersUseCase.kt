package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks

/**
 * Resolve's marker index: a marker's name, colour, note and duration changed, the marker deleted, and the markers
 * either side of a frame (Shift+↑ / Shift+↓). A duration marker spans a range: taken from the timeline's In and Out, or
 * handed to them (to render just that stretch).
 */
class MarkersUseCase {

    fun rename(timeline: Timeline, id: String, name: String): Timeline {
        val label = name.trim().take(MAX_NAME)
        if (label.isEmpty()) throw EditRejected("标记名称不能为空")
        return change(timeline, id) { it.copy(label = label) }
    }

    fun recolor(timeline: Timeline, id: String, color: MarkerColor): Timeline = change(timeline, id) { it.copy(color = color) }

    fun annotate(timeline: Timeline, id: String, note: String): Timeline = change(timeline, id) { it.copy(note = note.take(MAX_NOTE)) }

    /** Marker [id] spanning [frames] (0 = a point), never past the end of [timelineFrames]. */
    fun setDuration(timeline: Timeline, id: String, frames: Int, timelineFrames: Int): Timeline = change(timeline, id) { m ->
        m.copy(durationFrames = frames.coerceIn(0, (timelineFrames - m.frame).coerceAtLeast(0)))
    }

    /**
     * Marker [id] dragged along the ruler to [frame], keeping its length within [timelineFrames]; refused where another
     * marker already sits (two markers on one frame could not be told apart).
     */
    fun move(timeline: Timeline, id: String, frame: Int, timelineFrames: Int): Timeline {
        val marker = find(timeline, id)
        val to = frame.coerceIn(0, (timelineFrames - marker.durationFrames).coerceAtLeast(0))
        if (timeline.markers.any { it.id != marker.id && it.frame == to }) throw EditRejected("那里已有另一个标记")
        return timeline.copy(markers = timeline.markers.map { if (it.id == marker.id) it.copy(frame = to) else it }.sortedBy { it.frame })
    }

    /** Marker [id] moved to the timeline's In and spanning to its Out (Resolve's duration marker from a marked range). */
    fun rangeFromMarks(timeline: Timeline, id: String): Timeline {
        val inFrame = timeline.marks.inFrame
        val outFrame = timeline.marks.outFrame
        if (inFrame == null || outFrame == null) throw EditRejected("先在时间线上标记入点和出点")
        val marker = find(timeline, id)
        if (timeline.markers.any { it.id != marker.id && it.frame == inFrame }) throw EditRejected("入点处已有另一个标记")
        val moved = timeline.markers.map { if (it.id == marker.id) it.copy(frame = inFrame, durationFrames = outFrame - inFrame) else it }
        return timeline.copy(markers = moved.sortedBy { it.frame })
    }

    /** The timeline's In and Out set to marker [id]'s range, so the Deliver page renders just that stretch. */
    fun marksFromRange(timeline: Timeline, id: String): Timeline {
        val marker = find(timeline, id)
        if (!marker.hasDuration) throw EditRejected("这个标记没有范围：先设置时长或用入出点")
        return timeline.copy(marks = TimelineMarks(marker.frame, marker.endFrame))
    }

    /** [timeline] without marker [id], and what to tell the user. */
    fun remove(timeline: Timeline, id: String): Pair<Timeline, String> {
        val marker = find(timeline, id)
        return timeline.copy(markers = timeline.markers - marker) to "已删除“${marker.label}”"
    }

    /** The last marker before [frame], or null. */
    fun previous(timeline: Timeline, frame: Int): Marker? = timeline.markers.filter { it.frame < frame }.maxByOrNull { it.frame }

    /** The first marker after [frame], or null. */
    fun next(timeline: Timeline, frame: Int): Marker? = timeline.markers.filter { it.frame > frame }.minByOrNull { it.frame }

    private fun find(timeline: Timeline, id: String): Marker = timeline.markers.firstOrNull { it.id == id } ?: throw EditRejected("标记已删除")

    private fun change(timeline: Timeline, id: String, update: (Marker) -> Marker): Timeline {
        val marker = find(timeline, id)
        return timeline.copy(markers = timeline.markers.map { if (it.id == marker.id) update(it) else it })
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_NOTE = 200
    }
}
