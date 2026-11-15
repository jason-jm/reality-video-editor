package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.keysFor

/**
 * Resolve's Apply Grade from One Clip Prior (=) and Two Clips Prior (−): the clip being graded takes the whole grade of
 * the picture clip one or two before it in timeline order — window, keyframes (timed from the clip's start, cut where it
 * ends) and spatial NR included. Its versions stay; the grade lands on the version shown.
 */
class PriorGradeUseCase {

    operator fun invoke(timeline: Timeline, clipId: String?, steps: Int): EditResult {
        require(steps >= 1)
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先选择片段")
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以调色")
        val order = order(timeline)
        val at = order.indexOfFirst { it.id == clip.id }
        val prior = order.getOrNull(at - steps)
            ?: throw EditRejected(if (steps == 1) "前面没有片段" else "前面没有第 $steps 个片段")
        timeline.requireUnlocked(listOf(clip), clip)
        val updated = clip.copy(grade = prior.grade.keysFor(0, clip.durationFrames))
        val which = if (steps == 1) "前一个片段" else "前第 $steps 个片段"
        return EditResult(timeline.replaceClip(updated), clip.id, "已应用${which}“${prior.name}”的调色")
    }

    companion object {
        /** The Color page's clip order: picture clips by start, then track. */
        fun order(timeline: Timeline) =
            timeline.clips.filter { it.kind == ClipKind.VIDEO && !it.isCompound }.sortedWith(compareBy({ it.start }, { it.trackId }))
    }
}
