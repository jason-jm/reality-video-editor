package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/** An empty stretch of [trackId] between two clips (or before its first), frames [start] until [end]. */
data class TimelineGap(val trackId: String, val start: Int, val end: Int) {
    val frames: Int get() = end - start
}

/**
 * Resolve's gaps: a tap on the empty lane between two clips selects that gap, and deleting it closes it — the rest of the
 * timeline moves up by the gap's length. Every unlocked track with nothing in the gap's span moves, so pictures, titles and
 * sounds stay in step; a track with something there (a music bed under the cut) stays put. A move that would pull a clip
 * away from its linked partner is refused. Delete Gaps closes every gap that can close, from the last one back.
 */
class GapsUseCase {

    /** The gap on [trackId] that [frame] lies in, or null on a clip or past the track's last clip. */
    fun gapAt(timeline: Timeline, trackId: String, frame: Int): TimelineGap? {
        val clips = timeline.clipsOn(trackId)
        if (clips.any { frame >= it.start && frame < it.end }) return null
        val next = clips.filter { it.start > frame }.minOfOrNull { it.start } ?: return null
        val start = clips.filter { it.end <= frame }.maxOfOrNull { it.end } ?: 0
        return TimelineGap(trackId, start, next)
    }

    /** [gap] closed, as one edit; refused when its track is locked or a linked clip would be pulled out of step. */
    fun close(timeline: Timeline, gap: TimelineGap, fps: Int): EditResult {
        val (closed, stayed) = closing(timeline, gap)
        val stay = if (stayed.isEmpty()) "" else "（${stayed.joinToString("、")} 在这段有内容，保持不动）"
        return EditResult(closed, null, "已删除 ${seconds(gap.frames, fps)} 秒空隙，后面的片段前移$stay")
    }

    /** Resolve's Delete Gaps: every gap that can close, closed from the last one back, as one edit. */
    fun closeAll(timeline: Timeline, fps: Int): EditResult {
        var updated = timeline
        var count = 0
        var frames = 0
        while (true) {
            val gaps = updated.tracks.filter { !it.locked }.flatMap { track -> gapsOn(updated, track.id) }.sortedByDescending { it.start }
            val next = gaps.firstNotNullOfOrNull { gap -> runCatching { closing(updated, gap).first to gap }.getOrNull() } ?: break
            updated = next.first
            count++
            frames += next.second.frames
        }
        if (count == 0) throw EditRejected("时间线上没有可以删除的空隙")
        return EditResult(updated, null, "已删除 $count 处空隙，共 ${seconds(frames, fps)} 秒")
    }

    /** Every gap on [trackId], earliest first. */
    fun gapsOn(timeline: Timeline, trackId: String): List<TimelineGap> {
        val clips = timeline.clipsOn(trackId)
        var end = 0
        val gaps = mutableListOf<TimelineGap>()
        for (clip in clips) {
            if (clip.start > end) gaps += TimelineGap(trackId, end, clip.start)
            end = maxOf(end, clip.end)
        }
        return gaps
    }

    /** The timeline with [gap] closed, and the names of the tracks that stayed because they have something in its span. */
    private fun closing(timeline: Timeline, gap: TimelineGap): Pair<Timeline, List<String>> {
        val own = timeline.track(gap.trackId) ?: throw EditRejected("找不到这条轨道")
        if (own.locked) throw EditRejected("轨道已锁定")
        val free = { trackId: String -> timeline.clipsOn(trackId).none { it.start < gap.end && it.end > gap.start } }
        val moving = timeline.tracks.filter { !it.locked && free(it.id) }.map { it.id }.toSet()
        val moved = timeline.clips.filter { it.trackId in moving && it.start >= gap.end }
        val movedIds = moved.map { it.id }.toSet()
        // A clip moving while its linked partner stays would play out of step.
        val torn = moved.firstOrNull { clip -> clip.linkId != null && timeline.clips.any { it.linkId == clip.linkId && it.id !in movedIds } }
        if (torn != null) throw EditRejected("其他轨道在这段空隙里有内容，删除它会让“${torn.name}”和链接的片段错位")
        val stayed = timeline.tracks.filter { it.id !in moving && timeline.clipsOn(it.id).any { c -> c.end > gap.end } }.map { it.name }
        val closed = timeline.copy(clips = timeline.clips.map { if (it.id in movedIds) it.copy(start = it.start - gap.frames) else it })
        return closed to stayed
    }

    private fun seconds(frames: Int, fps: Int): String = String.format(java.util.Locale.US, "%.2f", frames / fps.coerceAtLeast(1).toFloat())
        .trimEnd('0').trimEnd('.')
}
