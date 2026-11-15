package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/** What Resolve's Link Clips (Ctrl+Alt+L) does with a selection. */
enum class LinkAction(val label: String) {
    LINK("链接片段"),
    UNLINK("取消链接"),
}

/**
 * Resolve's Link Clips: unlinked, a clip's picture and sound move and trim apart — the split edits (J and L
 * cuts) where the sound leads or trails the cut; linked again, one picture clip and one sound clip move, trim
 * and delete as one. The link is all that changes: no clip moves.
 */
class LinkClipsUseCase(private val ids: IdGenerator) {

    /** Unlink when any of [clipIds] is linked; link one unlinked picture clip with one unlinked sound clip; else nothing. */
    fun actionFor(timeline: Timeline, clipIds: List<String>): LinkAction? {
        val clips = clipIds.mapNotNull { timeline.clip(it) }.distinctBy { it.id }
        return when {
            clips.any { it.linkId != null } -> LinkAction.UNLINK
            clips.size == 2 && clips.count { it.kind == ClipKind.VIDEO } == 1 && clips.count { it.kind == ClipKind.AUDIO } == 1 -> LinkAction.LINK
            else -> null
        }
    }

    fun toggle(timeline: Timeline, clipIds: List<String>): EditResult = when (actionFor(timeline, clipIds)) {
        LinkAction.UNLINK -> unlink(timeline, clipIds)
        LinkAction.LINK -> link(timeline, clipIds)
        null -> throw EditRejected(if (clipIds.isEmpty()) "请先在时间线上选择片段" else "选择一个画面片段和一个声音片段来链接")
    }

    /** Every link among [clipIds] undone, partners included; the selection stays. */
    fun unlink(timeline: Timeline, clipIds: List<String>): EditResult {
        val links = clipIds.mapNotNull { timeline.clip(it)?.linkId }.toSet()
        if (links.isEmpty()) throw EditRejected("选中的片段没有链接")
        val freed = timeline.clips.filter { it.linkId in links }
        timeline.requireUnlocked(freed.map { listOf(it) })
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { if (it.linkId in links) it.copy(linkId = null) else it }),
            selectedClipId = clipIds.first(),
            message = if (links.size > 1) "已取消 ${links.size} 组链接" else "已取消链接：画面和声音可以分别移动和修剪",
            selection = clipIds,
        )
    }

    /** The one picture clip and one sound clip of [clipIds], neither linked yet, made a pair. */
    fun link(timeline: Timeline, clipIds: List<String>): EditResult {
        if (actionFor(timeline, clipIds) != LinkAction.LINK) throw EditRejected("选择一个画面片段和一个声音片段来链接")
        val pair = clipIds.mapNotNull { timeline.clip(it) }.distinctBy { it.id }
        timeline.requireUnlocked(pair.map { listOf(it) })
        val link = ids.next("l")
        val paired = pair.map { it.id }.toSet()
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { if (it.id in paired) it.copy(linkId = link) else it }),
            selectedClipId = clipIds.first(),
            message = "已链接片段：画面和声音一起移动和修剪",
            selection = clipIds,
        )
    }
}
