package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.effectsFor
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.maskFor

/**
 * Trims one edge of a clip with snapping. The edge cannot cross a neighbour on the same track, cannot
 * reveal media before the source start or past its end, and keeps at least [MIN_FRAMES]. Linked
 * partners receive the same edge change. The frames that stay keep their places: a reversed clip, which
 * plays its source window from the end back to the start, gains or loses at its end what lies before the
 * window, and at its start what lies after it.
 */
class TrimClipUseCase(private val editPoints: EditPointsUseCase = EditPointsUseCase()) {

    @Suppress("LongParameterList")
    operator fun invoke(
        timeline: Timeline,
        clipId: String,
        edge: TrimEdge,
        requestedFrame: Int,
        snapThreshold: Int,
        snapping: Boolean,
        playhead: Int,
        sourceDurationMs: Long?,
        fps: Int,
    ): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val group = timeline.linkedGroup(clip)
        timeline.requireUnlocked(group, clip)
        val exclude = group.map { it.id }.toSet()
        val frame = if (snapping) editPoints.snap(timeline, requestedFrame, snapThreshold, playhead, exclude) else requestedFrame

        // Linked partners receive the same edge change: the move is limited by every member's room.
        val requested = frame - if (edge == TrimEdge.START) clip.start else clip.end
        var low = Int.MIN_VALUE
        var high = Int.MAX_VALUE
        for (member in group) {
            val room = room(timeline, member, edge, exclude, sourceDurationMs, fps)
            low = maxOf(low, room.first)
            high = minOf(high, room.last)
        }
        val delta = if (low > high) 0 else requested.coerceIn(low, high)
        val byId = group.associate { it.id to shift(it, edge, delta, fps, it.reverse && sourceDurationMs != null) }
        val trimmed = byId.getValue(clip.id)
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { byId[it.id] ?: it }),
            selectedClipId = clip.id,
            message = "已修剪“${clip.name}”，时长 ${Timecode.format(trimmed.durationFrames, fps)}",
        )
    }

    /** The edge moves [clip] allows, as a delta range that always contains 0. */
    @Suppress("LongParameterList")
    private fun room(timeline: Timeline, clip: Clip, edge: TrimEdge, exclude: Set<String>, sourceDurationMs: Long?, fps: Int): IntRange {
        val others = timeline.clipsOn(clip.trackId).filterNot { it.id in exclude }
        val framesPerMs = fps / 1000.0 / clip.speed.factor
        val backwards = clip.reverse && sourceDurationMs != null
        // Frames of media before and after the clip's source window — at the speed of the section at that end. In points
        // are whole ms, a frame's worth cut short (333 ms for 10 frames at 30 fps): a ms of tolerance gets the frame back.
        val before = ((clip.sourceInMs + MS_TOLERANCE) * fps / 1000.0 / clip.headSpeed).toInt()
        val after = sourceDurationMs?.let {
            ((it - clip.sourceInMs - clip.sourceDurationMs(fps) + MS_TOLERANCE) * fps / 1000.0 / clip.tailSpeed).toInt().coerceAtLeast(0)
        }
        return when (edge) {
            TrimEdge.START -> {
                val leftNeighbour = others.filter { it.end <= clip.start }.maxOfOrNull { it.end } ?: 0
                val sourceLimit = when {
                    clip.assetId == null && clip.compoundId == null -> 0
                    backwards -> clip.start - (after ?: 0)
                    else -> clip.start - before
                }
                val lowest = minOf(maxOf(leftNeighbour, sourceLimit, 0), clip.start)
                val highest = maxOf(lowest, clip.end - MIN_FRAMES)
                (lowest - clip.start)..(highest - clip.start)
            }
            TrimEdge.END -> {
                val rightNeighbour = others.filter { it.start >= clip.end }.minOfOrNull { it.start } ?: Int.MAX_VALUE
                val sourceLimit = when {
                    sourceDurationMs == null || (clip.assetId == null && clip.compoundId == null) -> Int.MAX_VALUE
                    backwards -> clip.end + before
                    clip.retimed -> clip.end + (after ?: 0)
                    else -> clip.start + ((sourceDurationMs - clip.sourceInMs + MS_TOLERANCE) * framesPerMs).toInt()
                }
                val highest = maxOf(minOf(rightNeighbour, sourceLimit), clip.end)
                val lowest = minOf(clip.start + MIN_FRAMES, clip.end)
                val upper = if (highest == Int.MAX_VALUE) Int.MAX_VALUE else highest - clip.end
                (lowest - clip.end)..upper
            }
        }
    }

    private fun shift(clip: Clip, edge: TrimEdge, delta: Int, fps: Int, backwards: Boolean): Clip {
        if (delta == 0) return clip
        val framesPerMs = fps / 1000.0 / clip.speed.factor
        return when (edge) {
            // Keyframes stay with the picture they animate; speed sections keep their frames, the edge one giving or taking.
            TrimEdge.START -> clip.copy(
                start = clip.start + delta,
                durationFrames = clip.durationFrames - delta,
                // A reversed clip starts at its window's end: that end moves, its in point stays.
                sourceInMs = when {
                    backwards -> clip.sourceInMs
                    clip.retimed -> (clip.sourceInMs + clip.sourceOffsetMs(delta, fps)).toLong().coerceAtLeast(0)
                    else -> (clip.sourceInMs + delta / framesPerMs).toLong().coerceAtLeast(0)
                },
                speed = clip.sectionWindow(delta, clip.durationFrames - delta).first,
                speedSections = clip.sectionWindow(delta, clip.durationFrames - delta).second,
                keyframes = clip.keyframesFor(delta, clip.durationFrames - delta),
                effects = clip.effectsFor(delta, clip.durationFrames - delta),
                volumeKeyframes = clip.volumeKeyframesFor(delta, clip.durationFrames - delta),
                panKeyframes = clip.panKeyframesFor(delta, clip.durationFrames - delta),
                grade = clip.grade.keysFor(delta, clip.durationFrames - delta),
                mask = clip.maskFor(delta, clip.durationFrames - delta),
            )
            TrimEdge.END -> clip.copy(
                durationFrames = clip.durationFrames + delta,
                speed = clip.sectionWindow(0, clip.durationFrames + delta).first,
                speedSections = clip.sectionWindow(0, clip.durationFrames + delta).second,
                // A reversed clip ends at its in point: lengthening it shows what came before. Its window's
                // end — its first frame — stays exactly where it was.
                sourceInMs = if (backwards) clip.keepingWindowEnd(clip.durationFrames + delta, fps) else clip.sourceInMs,
                keyframes = clip.keyframesFor(0, clip.durationFrames + delta),
                effects = clip.effectsFor(0, clip.durationFrames + delta),
                volumeKeyframes = clip.volumeKeyframesFor(0, clip.durationFrames + delta),
                panKeyframes = clip.panKeyframesFor(0, clip.durationFrames + delta),
                grade = clip.grade.keysFor(0, clip.durationFrames + delta),
                mask = clip.maskFor(0, clip.durationFrames + delta),
            )
        }
    }

    companion object {
        const val MIN_FRAMES = 5

        /** How far ms rounding may leave an in point short of its frame. */
        private const val MS_TOLERANCE = 1.0
    }
}

/** The in point that keeps this clip's source window ending where it does at [durationFrames] long (a reversed clip's first frame). */
internal fun Clip.keepingWindowEnd(durationFrames: Int, fps: Int): Long =
    (sourceInMs + sourceDurationMs(fps) - copy(durationFrames = durationFrames).sourceDurationMs(fps)).coerceAtLeast(0)
