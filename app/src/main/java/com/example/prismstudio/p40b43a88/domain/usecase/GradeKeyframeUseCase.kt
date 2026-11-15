package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.gradeKeyframeAt
import com.example.prismstudio.p40b43a88.domain.model.gradeKeyframesWithKeyAt

/**
 * Resolve's dynamic keyframes on node 1, the primary correction: a keyframe holds the whole correction —
 * wheels, sliders, curves, look — at a frame, and the picture dissolves from one keyframe's look to the
 * next, evenly or easing in and out, so a grade can follow the light (a sunset, a passing cloud, day into
 * dusk). Frames count from the clip's start; node 2 (window and qualifier) keeps its own grade.
 */
class GradeKeyframeUseCase {

    /** Adds a keyframe holding node 1 as it is at [frame], or removes the one there. */
    fun toggle(grade: ColorGrade, frame: Int, clipFrames: Int): GradeEdit {
        if (frame !in 0 until clipFrames) throw EditRejected("把播放头移到片段内，再添加调色关键帧")
        val existing = grade.gradeKeyframeAt(frame)
        val updated = if (existing != null) {
            val rest = grade.keyframes - existing
            // Without keyframes node 1 keeps the correction it had here, instead of jumping back (and its noise reduction).
            if (rest.isEmpty()) existing.grade.copy(window = grade.window, spatialNr = grade.spatialNr, midDetail = grade.midDetail, temporalNr = grade.temporalNr) else grade.copy(keyframes = rest)
        } else {
            grade.copy(keyframes = grade.gradeKeyframesWithKeyAt(frame))
        }
        val count = updated.keyframes.size
        val message = when {
            existing == null && count == 1 -> "已添加调色关键帧：再到别处调整，调色会随时间变化"
            existing == null -> "已添加调色关键帧 · 共 $count 个"
            count == 0 -> "已删除调色关键帧，调色不再变化"
            else -> "已删除调色关键帧 · 还剩 $count 个"
        }
        return GradeEdit(updated, message)
    }

    /**
     * Turns easing on or off where the look arrives at ([arriving]) or leaves the keyframe at [frame]:
     * eased, the dissolve slows into it (or starts slowly) instead of changing evenly.
     */
    fun ease(grade: ColorGrade, frame: Int, arriving: Boolean): GradeEdit {
        val key = grade.gradeKeyframeAt(frame) ?: throw EditRejected("把播放头移到调色关键帧上，再设置缓入缓出")
        requireNeighbour(grade.keyframes.map { it.frame }, frame, arriving)
        val on = !key.ease.eases(arriving)
        val keyed = grade.keyframes.map { if (it == key) it.copy(ease = it.ease.with(arriving, on)) else it }
        return GradeEdit(grade.copy(keyframes = keyed), easeMessage(arriving, on))
    }

    /**
     * [grade] with node 1 changed by [change] at [frame]: without keyframes it changes throughout; with
     * keyframes the keyframe at [frame] changes (or is added, keeping the dissolve around it).
     */
    fun adjust(grade: ColorGrade, frame: Int, clipFrames: Int, change: (ColorGrade) -> ColorGrade): ColorGrade {
        if (grade.keyframes.isEmpty()) return grade.withNode(GradeNode.PRIMARY, change)
        if (frame !in 0 until clipFrames) throw EditRejected("播放头不在片段内：移进片段再调色")
        val changed = change(grade.at(frame).primary).primary.colorOnly
        return grade.copy(keyframes = grade.gradeKeyframesWithKeyAt(frame).map { if (it.frame == frame) it.copy(grade = changed) else it })
    }

    /** The node 1 keyframe before [frame], if any. */
    fun previous(grade: ColorGrade, frame: Int): Int? = grade.keyframes.lastOrNull { it.frame < frame }?.frame

    /** The node 1 keyframe after [frame], if any. */
    fun next(grade: ColorGrade, frame: Int): Int? = grade.keyframes.firstOrNull { it.frame > frame }?.frame
}

/** A grade after a keyframe edit, and what to tell the colourist. */
data class GradeEdit(val grade: ColorGrade, val message: String)
