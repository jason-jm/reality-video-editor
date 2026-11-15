package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import kotlin.math.abs

/**
 * Moves a clip in time (and optionally to another track of the same kind). On its own track the clip
 * slides until it meets a neighbour — it never jumps over one; a drop onto an occupied range of
 * another track is rejected. Linked partners move by the same time offset. A selection moves as one
 * ([group]), across tracks too.
 */
class MoveClipUseCase(private val editPoints: EditPointsUseCase = EditPointsUseCase()) {

    @Suppress("LongParameterList")
    operator fun invoke(
        timeline: Timeline,
        clipId: String,
        requestedStart: Int,
        targetTrackId: String,
        snapThreshold: Int,
        snapping: Boolean,
        playhead: Int,
        fps: Int,
    ): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val target = timeline.track(targetTrackId) ?: throw EditRejected("不能移动到该轨道")
        if (target.locked) throw EditRejected("轨道已锁定")
        timeline.requireUnlocked(timeline.linkedGroup(clip), clip)
        if (!target.accepts(clip.kind)) throw EditRejected("不能移动到该轨道")

        val partners = clip.linkId?.let { link -> timeline.clips.filter { it.linkId == link && it.id != clip.id } }.orEmpty()
        val exclude = (partners.map { it.id } + clip.id).toSet()
        val length = clip.durationFrames
        var start = requestedStart.coerceAtLeast(0)
        if (snapping) {
            val snappedStart = editPoints.snap(timeline, start, snapThreshold, playhead, exclude)
            val snappedEnd = editPoints.snap(timeline, start + length, snapThreshold, playhead, exclude)
            start = when {
                snappedStart != start -> snappedStart
                snappedEnd != start + length -> snappedEnd - length
                else -> start
            }
        }
        val others = timeline.clipsOn(target.id).filterNot { it.id in exclude }
        if (target.id == clip.trackId) {
            val rightLimit = others.filter { it.start >= clip.end }.minOfOrNull { it.start } ?: Int.MAX_VALUE
            val leftLimit = others.filter { it.end <= clip.start }.maxOfOrNull { it.end } ?: 0
            start = if (rightLimit - length < leftLimit) clip.start else start.coerceIn(leftLimit, rightLimit - length)
        } else if (others.any { start < it.end && start + length > it.start }) {
            throw EditRejected("目标位置已被占用")
        }
        val delta = start - clip.start
        val movedPartners = partners.map { it.copy(start = it.start + delta) }
        for (p in movedPartners) {
            val clash = timeline.clipsOn(p.trackId).filterNot { it.id in exclude }
                .any { p.start < it.end && p.end > it.start }
            if (clash || p.start < 0) throw EditRejected("关联的音频片段会与其他片段重叠")
        }
        val moved = clip.copy(start = start, trackId = target.id)
        val updated = timeline.clips.map { c ->
            when (c.id) {
                moved.id -> moved
                else -> movedPartners.firstOrNull { it.id == c.id } ?: c
            }
        }
        return EditResult(
            timeline = timeline.copy(clips = updated),
            selectedClipId = clip.id,
            message = "已移动“${clip.name}”到 ${Timecode.format(start, fps)}",
        )
    }

    /**
     * Moves the selected clips [clipIds] (with their partners) by one offset: [leadId] — the clip being
     * dragged — asks for [requestedStart] (snapping like a single clip) and the lane of [targetTrackId]. On
     * their own tracks the group slides until any of them meets a clip that is not moving, never jumping
     * over one. A lane change moves every one of them on a track of the lead's kind by as many lanes, as far
     * as the outermost allows, as Resolve moves a selection between tracks; linked sound or picture of the
     * other kind stays on its track. A drop onto a clip that stays, or onto a locked track, is refused.
     */
    @Suppress("LongParameterList")
    fun group(
        timeline: Timeline,
        clipIds: List<String>,
        leadId: String,
        requestedStart: Int,
        snapThreshold: Int,
        snapping: Boolean,
        playhead: Int,
        fps: Int,
        targetTrackId: String? = null,
    ): EditResult {
        val lead = timeline.clip(leadId) ?: throw EditRejected("请先在时间线上选择片段")
        val groups = timeline.selectedGroups(listOf(leadId) + clipIds)
        timeline.requireUnlocked(groups)
        val moving = groups.flatten()
        val ids = moving.map { it.id }.toSet()
        var start = requestedStart
        if (snapping) {
            val length = lead.durationFrames
            val snappedStart = editPoints.snap(timeline, start, snapThreshold, playhead, ids)
            val snappedEnd = editPoints.snap(timeline, start + length, snapThreshold, playhead, ids)
            start = when {
                snappedStart != start -> snappedStart
                snappedEnd != start + length -> snappedEnd - length
                else -> start
            }
        }
        val lanes = timeline.track(lead.trackId)?.kind?.let { kind -> timeline.tracks.filter { it.kind == kind } }.orEmpty()
        val shifting = moving.filter { c -> lanes.any { it.id == c.trackId } }
        val shift = laneShift(lanes, shifting, lead, targetTrackId)
        if (shift != 0) return acrossTracks(timeline, groups, moving, lanes, shifting, shift, start - lead.start, fps, clipIds, leadId)
        // How far the group may go: no clip before frame 0 or past a neighbour that stays.
        var lowest = Int.MIN_VALUE
        var highest = Int.MAX_VALUE
        for (c in moving) {
            val others = timeline.clipsOn(c.trackId).filterNot { it.id in ids }
            lowest = maxOf(lowest, (others.filter { it.end <= c.start }.maxOfOrNull { it.end } ?: 0) - c.start)
            others.filter { it.start >= c.end }.minOfOrNull { it.start }?.let { highest = minOf(highest, it - c.end) }
        }
        val delta = if (lowest > highest) 0 else (start - lead.start).coerceIn(lowest, highest)
        val moved = moving.associate { it.id to it.copy(start = it.start + delta) }
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { moved[it.id] ?: it }),
            selectedClipId = leadId,
            message = "已移动 ${groups.size} 个片段 ${if (delta >= 0) "+" else "-"}${Timecode.format(abs(delta), fps)}",
            selection = clipIds,
        )
    }

    /**
     * The selected clips [clipIds] one track of their kind up ([up]) or down, at the same time — the precise
     * alternative to dragging a selection between lanes; [leadId] (the clip the inspector shows) sets the kind.
     */
    fun groupToAdjacentTrack(timeline: Timeline, clipIds: List<String>, leadId: String, up: Boolean, fps: Int): EditResult {
        val lead = timeline.clip(leadId) ?: throw EditRejected("请先在时间线上选择片段")
        val lanes = timeline.track(lead.trackId)?.kind?.let { kind -> timeline.tracks.filter { it.kind == kind } }.orEmpty()
        val index = lanes.indexOfFirst { it.id == lead.trackId }
        val target = lanes.getOrNull(if (up) index - 1 else index + 1)
            ?: throw EditRejected(if (up) "已经在最上面的轨道" else "已经在最下面的轨道")
        val result = group(timeline, clipIds, leadId, lead.start, snapThreshold = 0, snapping = false, playhead = 0, fps = fps, targetTrackId = target.id)
        if (result.timeline.clip(leadId)?.trackId == lead.trackId) {
            throw EditRejected(if (up) "最上面的轨道上已有选中的片段" else "最下面的轨道上已有选中的片段")
        }
        return result
    }

    /** Lanes from [lead]'s to [targetTrackId]'s among [lanes], as far as the outermost of [shifting] can go. */
    private fun laneShift(lanes: List<Track>, shifting: List<Clip>, lead: Clip, targetTrackId: String?): Int {
        val from = lanes.indexOfFirst { it.id == lead.trackId }
        val to = lanes.indexOfFirst { it.id == targetTrackId }
        if (from < 0 || to < 0 || shifting.isEmpty()) return 0
        val indices = shifting.map { c -> lanes.indexOfFirst { it.id == c.trackId } }
        return (to - from).coerceIn(-indices.min(), lanes.lastIndex - indices.max())
    }

    /** The group [shift] lanes over and [requestedDelta] frames on, refused where it would land on a clip that stays. */
    @Suppress("LongParameterList")
    private fun acrossTracks(
        timeline: Timeline,
        groups: List<List<Clip>>,
        moving: List<Clip>,
        lanes: List<Track>,
        shifting: List<Clip>,
        shift: Int,
        requestedDelta: Int,
        fps: Int,
        clipIds: List<String>,
        leadId: String,
    ): EditResult {
        val ids = moving.map { it.id }.toSet()
        val shifted = shifting.map { it.id }.toSet()
        // No clip before the timeline's start.
        val delta = maxOf(requestedDelta, -moving.minOf { it.start })
        val moved = moving.associate { c ->
            val track = if (c.id in shifted) lanes[lanes.indexOfFirst { it.id == c.trackId } + shift] else timeline.track(c.trackId)!!
            if (track.locked) throw EditRejected("目标轨道已锁定")
            c.id to c.copy(start = c.start + delta, trackId = track.id)
        }
        for (c in moved.values) {
            if (timeline.clipsOn(c.trackId).any { it.id !in ids && c.start < it.end && c.end > it.start }) throw EditRejected("目标位置已被占用")
        }
        val where = if (shift < 0) "上移" else "下移"
        val time = if (delta == 0) "" else "，${if (delta > 0) "+" else "-"}${Timecode.format(abs(delta), fps)}"
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { moved[it.id] ?: it }),
            selectedClipId = leadId,
            message = "已将 ${groups.size} 个片段$where ${abs(shift)} 条轨道$time",
            selection = clipIds,
        )
    }

    /**
     * Moves the clip to the track of the same kind just above ([up]) or below it, at the same time —
     * the precise alternative to dragging between lanes. Linked sound stays where it is in time.
     */
    fun toAdjacentTrack(timeline: Timeline, clipId: String, up: Boolean, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val sameKind = timeline.tracks.filter { it.accepts(clip.kind) }
        val index = sameKind.indexOfFirst { it.id == clip.trackId }
        val target = sameKind.getOrNull(if (up) index - 1 else index + 1)
            ?: throw EditRejected(if (up) "已经在最上面的轨道" else "已经在最下面的轨道")
        val moved = invoke(timeline, clipId, clip.start, target.id, snapThreshold = 0, snapping = false, playhead = 0, fps = fps)
        return moved.copy(message = "已将“${clip.name}”移到 ${target.name}")
    }
}
