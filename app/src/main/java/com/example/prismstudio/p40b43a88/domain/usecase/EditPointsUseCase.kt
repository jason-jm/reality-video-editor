package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Timeline
import kotlin.math.abs

/** Edit-point navigation and snapping shared by the transport and the timeline gestures. */
class EditPointsUseCase {

    fun editPoints(timeline: Timeline): List<Int> =
        (listOf(0) + timeline.clips.flatMap { listOf(it.start, it.end) }).distinct().sorted()

    fun previous(timeline: Timeline, frame: Int): Int = editPoints(timeline).lastOrNull { it < frame } ?: 0

    fun next(timeline: Timeline, frame: Int): Int = editPoints(timeline).firstOrNull { it > frame } ?: frame

    /**
     * Snaps [frame] to the nearest clip edge, marker or the playhead within [threshold] frames,
     * ignoring the clips in [excludeIds] (the clip being dragged and its linked partner).
     */
    fun snap(
        timeline: Timeline,
        frame: Int,
        threshold: Int,
        playhead: Int,
        excludeIds: Set<String> = emptySet(),
    ): Int {
        val candidates = buildList {
            add(playhead)
            add(0)
            timeline.markers.forEach { add(it.frame) }
            timeline.clips.filterNot { it.id in excludeIds }.forEach { add(it.start); add(it.end) }
        }
        val best = candidates.minByOrNull { abs(it - frame) } ?: return frame
        return if (abs(best - frame) <= threshold) best else frame
    }
}
