package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Removes clips (with their linked partners) and closes the gaps while keeping the timeline in sync, as Resolve's
 * ripple delete does with every track's sync lock on: on every unlocked track with clips after a removed clip's
 * span, those clips move back by its length — so later pictures keep their sound and titles and subtitles stay with
 * their shots — and whatever lies across the span there (an ambience under the shot) loses that stretch. A track
 * with nothing after the span (a music bed running on) is left whole, as is a locked one.
 */
class RippleDeleteUseCase(private val ids: IdGenerator) {

    operator fun invoke(timeline: Timeline, clipId: String, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val removed = timeline.linkedGroup(clip)
        timeline.requireUnlocked(removed, clip)
        val (closed, cut) = closed(timeline, removed, clip, fps)
        return EditResult(
            timeline = closed,
            selectedClipId = null,
            message = "已波纹删除“${clip.name}”，后续片段前移 ${Timecode.format(clip.durationFrames, fps)}" + cutNote(cut),
        )
    }

    /** The selected clips removed together, each closed up as one ripple delete, the latest first. */
    operator fun invoke(timeline: Timeline, clipIds: List<String>, fps: Int): EditResult {
        if (clipIds.size == 1) return invoke(timeline, clipIds.first(), fps)
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected("请先在时间线上选择片段")
        timeline.requireUnlocked(groups)
        var result = timeline
        var cut = 0
        // Latest first: each span is still where it was when its turn comes.
        for (group in groups.sortedByDescending { it.first().start }) {
            val (closed, n) = closed(result, group, group.first(), fps)
            result = closed
            cut += n
        }
        val moved = timeline.clips.mapNotNull { before -> result.clip(before.id)?.let { before.start - it.start } }.maxOrNull() ?: 0
        return EditResult(
            timeline = result,
            selectedClipId = null,
            message = "已波纹删除 ${groups.size} 个片段，后续片段最多前移 ${Timecode.format(moved, fps)}" + cutNote(cut),
        )
    }

    /**
     * [timeline] without [group], closed over [lead]'s span on every unlocked track with clips after it (what lies
     * across the span there is cut out first); also returns how many clips were cut.
     */
    private fun closed(timeline: Timeline, group: List<Clip>, lead: Clip, fps: Int): Pair<Timeline, Int> {
        val gone = group.map { it.id }.toSet()
        var result = timeline.copy(clips = timeline.clips.filterNot { it.id in gone })
        val tailLinks = mutableMapOf<String, String>()
        val moving = mutableSetOf<String>()
        var cut = 0
        for (track in timeline.tracks.filterNot { it.locked }) {
            val clips = result.clipsOn(track.id)
            // Nothing after the span: nothing there to keep in step, and what runs across it stays whole.
            if (clips.none { it.start >= lead.end }) continue
            if (clips.any { it.start < lead.end && it.end > lead.start }) {
                val (carved, n) = result.overwritten(track.id, lead.start, lead.end, fps, ids, tailLinks)
                result = carved
                cut += n
            }
            moving += track.id
        }
        val length = lead.durationFrames
        return result.copy(clips = result.clips.map { if (it.trackId in moving && it.start >= lead.end) it.copy(start = it.start - length) else it }) to cut
    }

    private fun cutNote(cut: Int) = if (cut > 0) "，其他轨道剪去了同样长度" else ""
}
