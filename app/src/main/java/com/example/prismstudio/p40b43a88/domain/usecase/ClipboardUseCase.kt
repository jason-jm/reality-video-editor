package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Resolve's clipboard edits on the timeline. Copy takes the selected clip with its linked partner (or
 * every selected clip, keeping their places relative to one another); Paste puts the copy at the
 * playhead on the tracks it came from as new clips, over whatever is there (a paste overwrites, as in
 * Resolve), and Paste Insert pushes the rest of the timeline later to make room; Duplicate pastes right
 * after the original; Delete lifts the clips and their partners off the timeline and leaves the gaps
 * (Ripple Delete closes them); Cut and Ripple Cut copy first.
 */
class ClipboardUseCase(private val ids: IdGenerator, private val ripple: RippleDeleteUseCase = RippleDeleteUseCase(ids)) {

    /**
     * Copied clips, the selected one first and each with its linked partner after it; starts count from
     * the earliest of them. [groups] is how many clips were copied (a picture with its sound is one).
     */
    data class Content(val clips: List<Clip>, val groups: Int = 1) {
        val durationFrames: Int get() = clips.maxOf { it.end }
    }

    fun copy(timeline: Timeline, clipId: String): Content {
        val clip = timeline.clip(clipId) ?: throw EditRejected(NO_CLIP)
        val group = listOf(clip) + (timeline.linkedGroup(clip) - clip)
        val first = group.minOf { it.start }
        return Content(group.map { it.copy(start = it.start - first) })
    }

    /** Copies the selected clips [clipIds] (each with its partner), keeping their places relative to one another. */
    fun copy(timeline: Timeline, clipIds: List<String>): Content {
        if (clipIds.size == 1) return copy(timeline, clipIds.first())
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected(NO_CLIP)
        val clips = groups.flatten()
        val first = clips.minOf { it.start }
        return Content(clips.map { it.copy(start = it.start - first) }, groups.size)
    }

    /** Copies the selected clips and lifts them off the timeline, leaving the gaps. */
    fun cut(timeline: Timeline, clipIds: List<String>): Pair<Content, EditResult> {
        if (clipIds.size == 1) return cut(timeline, clipIds.first())
        val content = copy(timeline, clipIds)
        val lifted = delete(timeline, clipIds)
        return content to lifted.copy(message = "已剪切 ${content.groups} 个片段")
    }

    /** Lifts the selected clips (with their partners) off the timeline and leaves the gaps. */
    fun delete(timeline: Timeline, clipIds: List<String>): EditResult {
        if (clipIds.size == 1) return delete(timeline, clipIds.first())
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected(NO_CLIP)
        timeline.requireUnlocked(groups)
        val gone = groups.flatten().map { it.id }.toSet()
        return EditResult(timeline.copy(clips = timeline.clips.filterNot { it.id in gone }), null, "已删除 ${groups.size} 个片段，保留空隙")
    }

    /** A copy of the selected clips right after the last of them, over what is there; the copies are selected. */
    fun duplicate(timeline: Timeline, clipIds: List<String>, fps: Int): EditResult {
        if (clipIds.size == 1) return duplicate(timeline, clipIds.first(), fps)
        val content = copy(timeline, clipIds)
        val after = timeline.selectedGroups(clipIds).flatten().maxOf { it.end }
        return paste(timeline, content, after, fps).copy(message = "已创建 ${content.groups} 个片段的副本（${Timecode.format(after, fps)}）")
    }

    /**
     * Resolve's Ripple Cut (Ctrl+Shift+X): copies the selected clips (with their partners) and removes them,
     * closing the gaps as Ripple Delete does.
     */
    fun rippleCut(timeline: Timeline, clipIds: List<String>, fps: Int): Pair<Content, EditResult> {
        val content = copy(timeline, clipIds)
        val closed = if (clipIds.size == 1) ripple(timeline, clipIds.first(), fps) else ripple(timeline, clipIds, fps)
        val what = if (content.groups > 1) " ${content.groups} 个片段" else "“${content.clips.first().name}”"
        return content to closed.copy(message = "已波纹剪切$what，后面的片段前移")
    }

    /**
     * Resolve's Paste Insert (Ctrl+Shift+V): [content] at frame [at] with room made for it — every unlocked track
     * opens there by the copy's length (what plays across [at] is split), as an insert edit does; the first
     * pasted clip is selected.
     */
    fun pasteInsert(timeline: Timeline, content: Content, at: Int, fps: Int): EditResult {
        if (content.clips.isEmpty()) throw EditRejected(EMPTY)
        val start = at.coerceAtLeast(0)
        var result = timeline
        for (clip in content.clips) {
            val track = targetTrack(result, clip)
            if (result.track(track.id) == null) result = result.withTrack(track)
            result.requireTrackUnlocked(track.id)
        }
        val length = content.durationFrames
        val opened = result.opened(start, length, fps, ids)
        val placed = paste(opened, content, start, fps)
        return placed.copy(
            message = "已插入粘贴 ${content.clips.size} 个片段到 ${Timecode.format(start, fps)}，后面的片段后移 ${Timecode.format(length, fps)}",
        )
    }

    /** Copies [clipId] (with its partner) and lifts it off the timeline. */
    fun cut(timeline: Timeline, clipId: String): Pair<Content, EditResult> {
        val content = copy(timeline, clipId)
        val lifted = delete(timeline, clipId)
        return content to lifted.copy(message = "已剪切“${content.clips.first().name}”")
    }

    fun delete(timeline: Timeline, clipId: String): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected(NO_CLIP)
        val group = timeline.linkedGroup(clip)
        timeline.requireUnlocked(group, clip)
        val gone = group.map { it.id }.toSet()
        val message = if (group.size > 1) "已删除“${clip.name}”及链接的片段，保留空隙" else "已删除“${clip.name}”，保留空隙"
        return EditResult(timeline.copy(clips = timeline.clips.filterNot { it.id in gone }), null, message)
    }

    /** [content] at frame [at], over what is there; the first pasted clip is selected. */
    fun paste(timeline: Timeline, content: Content, at: Int, fps: Int): EditResult {
        if (content.clips.isEmpty()) throw EditRejected(EMPTY)
        var result = timeline
        val links = mutableMapOf<String, String>()
        val pasted = content.clips.map { clip ->
            val track = targetTrack(result, clip)
            if (result.track(track.id) == null) result = result.withTrack(track)
            result.requireTrackUnlocked(track.id)
            clip.copy(
                id = newId(clip),
                trackId = track.id,
                start = at.coerceAtLeast(0) + clip.start,
                // An in-transition (or cross fade) belonged to the clip's old neighbour.
                transitionIn = null,
                crossFadeFrames = 0,
                linkId = clip.linkId?.let { links.getOrPut(it) { ids.next("l") } },
            )
        }
        var overwritten = 0
        val tailLinks = mutableMapOf<String, String>()
        for (clip in pasted) {
            val (carved, touched) = result.overwritten(clip.trackId, clip.start, clip.end, fps, ids, tailLinks)
            result = carved
            overwritten += touched
        }
        val message = buildString {
            append("已粘贴 ${pasted.size} 个片段到 ${Timecode.format(pasted.first().start, fps)}")
            if (overwritten > 0) append("，覆盖了原有内容")
        }
        // Several copied clips come back selected together: the first of each linked group.
        val selection = if (content.groups > 1) pasted.distinctBy { it.linkId ?: it.id }.map { it.id } else null
        return EditResult(result.copy(clips = result.clips + pasted), pasted.first().id, message, selection)
    }

    /** A copy of [clipId] (with its partner) right after it, over what is there. */
    fun duplicate(timeline: Timeline, clipId: String, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected(NO_CLIP)
        val content = copy(timeline, clipId)
        val after = timeline.linkedGroup(clip).maxOf { it.end }
        return paste(timeline, content, after, fps).copy(message = "已创建副本：“${clip.name}”（${Timecode.format(after, fps)}）")
    }

    /** The clip's own track if it still takes it, else the base track of its kind (a subtitle track is made). */
    private fun targetTrack(timeline: Timeline, clip: Clip): Track {
        timeline.track(clip.trackId)?.takeIf { it.accepts(clip.kind) }?.let { return it }
        return when {
            clip.kind == ClipKind.AUDIO -> timeline.track(Timeline.A1)
            clip.title?.template == TitleTemplate.SUBTITLE -> timeline.tracks.firstOrNull { it.kind == TrackKind.SUBTITLE }
            else -> timeline.track(Timeline.V1)
        } ?: timeline.nextTrack(if (clip.kind == ClipKind.AUDIO) TrackKind.AUDIO else if (clip.kind == ClipKind.TITLE) TrackKind.SUBTITLE else TrackKind.VIDEO)
    }

    private fun newId(clip: Clip) = ids.next(if (clip.kind == ClipKind.TITLE) "t" else "c")

    private companion object {
        const val NO_CLIP = "请先在时间线上选择片段"
        const val EMPTY = "剪贴板是空的"
    }
}

/**
 * Every unlocked track opened at [at] by [length] frames (an insert): a clip playing across [at] is split there
 * (linked right halves stay linked to each other) and everything from [at] on moves later. A locked track stays.
 */
internal fun Timeline.opened(at: Int, length: Int, fps: Int, ids: IdGenerator): Timeline {
    val unlocked = tracks.filterNot { it.locked }.map { it.id }.toSet()
    val links = mutableMapOf<String, String>()
    val split = clips.flatMap { c ->
        if (c.trackId !in unlocked || at <= c.start || at >= c.end) return@flatMap listOf(c)
        val right = c.piece(at, c.end, fps).copy(
            id = ids.next(if (c.kind == ClipKind.TITLE) "t" else "c"),
            linkId = c.linkId?.let { links.getOrPut(it) { ids.next("l") } },
        )
        listOf(c.piece(c.start, at, fps), right)
    }
    return copy(clips = split.map { if (it.trackId in unlocked && it.start >= at) it.copy(start = it.start + length) else it })
}

/**
 * [trackId] cleared between [from] and [until] (Resolve's overwrite): clips there are cut back, split
 * or removed; returns how many were touched. A clip split around the range keeps its head; its tail is
 * a new clip, linked to the tail of its partner when that was split too ([tailLinks]).
 */
internal fun Timeline.overwritten(
    trackId: String,
    from: Int,
    until: Int,
    fps: Int,
    ids: IdGenerator,
    tailLinks: MutableMap<String, String>,
): Pair<Timeline, Int> {
    var touched = 0
    val kept = clips.flatMap { c ->
        if (c.trackId != trackId || c.end <= from || c.start >= until) return@flatMap listOf(c)
        touched++
        buildList {
            if (c.start < from) add(c.piece(c.start, from, fps))
            if (c.end > until) {
                val tail = c.piece(until, c.end, fps)
                // Split around the range: the tail is a new clip (a head-cut clip keeps its id).
                add(
                    if (c.start < from) {
                        tail.copy(id = ids.next(if (c.kind == ClipKind.TITLE) "t" else "c"), linkId = c.linkId?.let { tailLinks.getOrPut(it) { ids.next("l") } })
                    } else {
                        tail
                    },
                )
            }
        }
    }
    return copy(clips = kept) to touched
}
