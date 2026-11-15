package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Resolve's timeline In / Out (I / O, Alt+X clears): the range the source viewer's edits fill and
 * 入点至出点 renders. A new mark that would leave Out at or before In drops the other one.
 */
class TimelineMarksUseCase {

    /** A changed timeline and what to tell the user. */
    data class Result(val timeline: Timeline, val message: String)

    fun markIn(timeline: Timeline, frame: Int, fps: Int): Result {
        val at = frame.coerceAtLeast(0)
        val out = timeline.marks.outFrame?.takeIf { it > at }
        val dropped = timeline.marks.outFrame != null && out == null
        return Result(
            timeline.copy(marks = TimelineMarks(at, out)),
            "已标记时间线入点 ${Timecode.format(at, fps)}" + if (dropped) "，原出点不在它之后，已清除" else "",
        )
    }

    fun markOut(timeline: Timeline, frame: Int, fps: Int): Result {
        if (frame <= 0) throw EditRejected("出点不能在时间线开头")
        val inFrame = timeline.marks.inFrame?.takeIf { it < frame }
        val dropped = timeline.marks.inFrame != null && inFrame == null
        return Result(
            timeline.copy(marks = TimelineMarks(inFrame, frame)),
            "已标记时间线出点 ${Timecode.format(frame, fps)}" + if (dropped) "，原入点不在它之前，已清除" else "",
        )
    }

    /**
     * Resolve's Mark Clip (X): In and Out around a clip — the selected one, else the clip under the playhead on the lowest
     * picture track that has one (then the sound tracks).
     */
    fun markClip(timeline: Timeline, selectedId: String?, frame: Int, fps: Int): Result {
        val clip = timeline.clip(selectedId)
            // Tracks are listed top first: V1 is the last picture track.
            ?: (timeline.tracks.filter { it.kind == TrackKind.VIDEO }.reversed() + timeline.tracks.filter { it.kind == TrackKind.AUDIO })
                .firstNotNullOfOrNull { track -> timeline.clipsOn(track.id).firstOrNull { frame in it.start until it.end } }
            ?: throw EditRejected("播放头下没有片段")
        return Result(
            timeline.copy(marks = TimelineMarks(clip.start, clip.end)),
            "已按“${clip.name}”标记入出点 ${Timecode.format(clip.start, fps)} → ${Timecode.format(clip.end, fps)}",
        )
    }

    fun clear(timeline: Timeline): Result {
        if (timeline.marks.isEmpty) throw EditRejected("时间线上没有入出点")
        return Result(timeline.copy(marks = TimelineMarks()), "已清除时间线入出点")
    }

    companion object {
        /** The marks for the timeline summary: "入点 … → 出点 …（长度）", one side alone, or null. */
        fun label(marks: TimelineMarks, fps: Int): String? {
            val inFrame = marks.inFrame
            val outFrame = marks.outFrame
            return when {
                inFrame != null && outFrame != null ->
                    "入点 ${Timecode.format(inFrame, fps)} → 出点 ${Timecode.format(outFrame, fps)}（${Timecode.format(outFrame - inFrame, fps)}）"
                inFrame != null -> "入点 ${Timecode.format(inFrame, fps)}"
                outFrame != null -> "出点 ${Timecode.format(outFrame, fps)}"
                else -> null
            }
        }
    }
}
