package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks

/**
 * Resolve's ranged playback on the program monitor: Play Around Current Frame (/) — from a pre-roll before the playhead to
 * a post-roll after it, then back where it was; Play In to Out (Alt+/) — the marked range, stopping on its Out; and Loop
 * (Ctrl+/) — at the end of what plays (the range, else In to Out when both are marked, else the timeline) it starts again.
 * [step] is asked each time the playhead moves while playing.
 */
class PlayRangeUseCase {

    /** Frames [from] until [to] (exclusive); a stop lands on [returnTo] when set, else on [to]. */
    data class Range(val from: Int, val to: Int, val returnTo: Int? = null, val label: String)

    sealed interface Step {
        data object Continue : Step
        data class Restart(val frame: Int) : Step
        data class Stop(val frame: Int) : Step
    }

    fun around(playhead: Int, durationFrames: Int, fps: Int, preRollSeconds: Int = PRE_ROLL_S, postRollSeconds: Int = POST_ROLL_S): Range {
        if (durationFrames <= 0) throw EditRejected("时间线是空的")
        val at = playhead.coerceIn(0, durationFrames)
        val from = (at - preRollSeconds * fps).coerceAtLeast(0)
        val to = (at + postRollSeconds * fps).coerceAtMost(durationFrames)
        if (to <= from) throw EditRejected("时间线是空的")
        return Range(from, to, returnTo = at, label = "播放头前后 ${preRollSeconds} 秒")
    }

    fun inToOut(marks: TimelineMarks, durationFrames: Int): Range {
        val inFrame = marks.inFrame
        val outFrame = marks.outFrame
        if (inFrame == null || outFrame == null) throw EditRejected("先标记时间线入点和出点（I / O）")
        val to = outFrame.coerceAtMost(durationFrames)
        if (to <= inFrame) throw EditRejected("入点到出点之间没有画面")
        return Range(inFrame, to, label = "入点到出点")
    }

    /** What loops when nothing narrower plays: In to Out when both are marked, else the whole timeline. */
    fun loopRange(marks: TimelineMarks, durationFrames: Int): Range? {
        val marked = marks.inFrame != null && marks.outFrame != null
        val range = if (marked) runCatching { inToOut(marks, durationFrames) }.getOrNull() else null
        return range ?: if (durationFrames > 0) Range(0, durationFrames, label = "整条时间线") else null
    }

    /**
     * After the playhead reached [frame] ([playing] now, [wasPlaying] before): carry on, start again from the range's
     * beginning (looping), or stop. Playback that stopped at the timeline's end counts as reaching it.
     */
    fun step(range: Range?, loop: Boolean, marks: TimelineMarks, durationFrames: Int, frame: Int, playing: Boolean, wasPlaying: Boolean): Step {
        val active = range ?: if (loop) loopRange(marks, durationFrames) else null
        active ?: return Step.Continue
        if (!playing && !wasPlaying) return Step.Continue
        val endedAtEnd = wasPlaying && !playing && frame >= durationFrames - 1
        val reached = frame >= active.to || (endedAtEnd && active.to >= durationFrames - 1)
        if (!reached) return Step.Continue
        return if (loop) Step.Restart(active.from) else Step.Stop(active.returnTo ?: active.to)
    }

    companion object {
        /** Resolve's default playback pre-roll and post-roll. */
        const val PRE_ROLL_S = 2
        const val POST_ROLL_S = 2
    }
}
