package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Names in the timeline (Resolve's clip name and track name): a clip's name is what its label on the timeline and the
 * inspector show, at most [MAX_CLIP] characters; a track's is its header's, at most [MAX_TRACK] and unlike every other
 * track's, since the mixer, the ducking keys and the messages name tracks by it. Spaces around a name are dropped.
 */
class RenameUseCase {

    fun clip(timeline: Timeline, clipId: String, name: String): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val wanted = valid(name, MAX_CLIP, "片段")
        if (wanted == clip.name) throw EditRejected("名称没有变化")
        timeline.requireUnlocked(listOf(clip), clip)
        return EditResult(timeline.replaceClip(clip.copy(name = wanted)), clip.id, "片段已重命名为“$wanted”")
    }

    fun track(timeline: Timeline, trackId: String, name: String): EditResult {
        val track = timeline.track(trackId) ?: throw EditRejected("找不到这条轨道")
        val wanted = valid(name, MAX_TRACK, "轨道")
        if (wanted == track.name) throw EditRejected("名称没有变化")
        if (timeline.tracks.any { it.id != trackId && it.name == wanted }) throw EditRejected("已有名为“$wanted”的轨道")
        return EditResult(
            timeline.copy(tracks = timeline.tracks.map { if (it.id == trackId) it.copy(name = wanted) else it }),
            null,
            "轨道 ${track.name} 已重命名为“$wanted”",
        )
    }

    /** What is wrong with [name] for a [what] (片段 / 轨道) name, or null when it can be used. */
    fun problem(name: String, what: String): String? {
        val trimmed = name.trim()
        val max = if (what == "轨道") MAX_TRACK else MAX_CLIP
        return when {
            trimmed.isEmpty() -> "${what}名称不能为空"
            trimmed.length > max -> "${what}名称最多 $max 个字"
            else -> null
        }
    }

    private fun valid(name: String, max: Int, what: String): String {
        problem(name, what)?.let { throw EditRejected(it) }
        return name.trim().also { check(it.length <= max) }
    }

    companion object {
        const val MAX_CLIP = 40
        const val MAX_TRACK = 6
    }
}
