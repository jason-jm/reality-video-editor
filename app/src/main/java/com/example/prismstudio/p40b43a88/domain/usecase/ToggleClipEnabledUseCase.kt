package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Resolve's Disable Clip (D): a disabled clip keeps its place on the timeline but neither plays nor
 * renders — a quick way to compare with and without a shot, a title or a sound. Its linked partner
 * follows, so picture and sound switch together.
 */
class ToggleClipEnabledUseCase {

    operator fun invoke(timeline: Timeline, clipId: String): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val group = timeline.linkedGroup(clip)
        timeline.requireUnlocked(group, clip)
        val enabled = !clip.enabled
        val ids = group.map { it.id }.toSet()
        val updated = timeline.copy(clips = timeline.clips.map { if (it.id in ids) it.copy(enabled = enabled) else it })
        val partner = if (group.size > 1) "及链接的片段" else ""
        val message = if (enabled) "已启用“${clip.name}”$partner" else "已停用“${clip.name}”$partner：不播放、不渲染"
        return EditResult(updated, clipId, message)
    }

    /**
     * The selected clips (with their partners) switched together: off when any of them still plays,
     * else all back on. The selection stays.
     */
    operator fun invoke(timeline: Timeline, clipIds: List<String>): EditResult {
        if (clipIds.size == 1) return invoke(timeline, clipIds.first())
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected("请先在时间线上选择片段")
        timeline.requireUnlocked(groups)
        val enabled = groups.none { it.first().enabled }
        val ids = groups.flatten().map { it.id }.toSet()
        val updated = timeline.copy(clips = timeline.clips.map { if (it.id in ids) it.copy(enabled = enabled) else it })
        val message = if (enabled) "已启用 ${groups.size} 个片段" else "已停用 ${groups.size} 个片段：不播放、不渲染"
        return EditResult(updated, clipIds.first(), message, clipIds)
    }
}
