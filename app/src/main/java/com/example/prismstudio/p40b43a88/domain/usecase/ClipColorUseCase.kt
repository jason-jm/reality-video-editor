package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Resolve's clip colours: the selected clips — each with its linked partner, so picture and sound carry
 * the same label — given a colour or back to their kind's; and every clip of a colour selected at once.
 */
class ClipColorUseCase {

    fun set(timeline: Timeline, clipIds: List<String>, color: ClipColor?): EditResult {
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected("请先在时间线上选择片段")
        timeline.requireUnlocked(groups)
        val ids = groups.flatten().map { it.id }.toSet()
        val count = groups.size
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips.map { if (it.id in ids) it.copy(color = color) else it }),
            selectedClipId = clipIds.first(),
            message = when {
                color == null -> if (count > 1) "已清除 $count 个片段的颜色" else "已清除片段颜色"
                count > 1 -> "已将 $count 个片段标为${color.label}色"
                else -> "已将片段标为${color.label}色"
            },
            selection = clipIds,
        )
    }

    /** Every clip of [color], one per linked group (picture before its sound), in timeline order. */
    fun select(timeline: Timeline, color: ClipColor): List<String> =
        timeline.clips.filter { it.color == color }
            .sortedWith(compareBy({ it.start }, { it.kind == ClipKind.AUDIO }))
            .distinctBy { it.linkId ?: it.id }
            .map { it.id }
            .ifEmpty { throw EditRejected("时间线上没有${color.label}色的片段") }
}
