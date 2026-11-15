package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.keyframeAt

/**
 * A picture clip's mask (Edit inspector › 遮罩): the picture shows inside a circle, rectangle or gradient (outside it when
 * inverted) and is clear elsewhere, so the tracks beneath show — Resolve does this with a window feeding the node tree's
 * alpha output. The mask is a [PowerWindow] in the picture's own frame (it moves with the transform); its geometry can be
 * keyed like a grade window's, frames counting from the clip's start.
 */
class ClipMaskUseCase(private val windowKeys: WindowKeyframeUseCase = WindowKeyframeUseCase()) {

    /**
     * Picks the mask's shape: a first shape adds a mask (centred, a soft-edged circle or a frame-shaped rectangle, a
     * gradient across the middle), another shape keeps its geometry, null removes it. Null when nothing changes.
     */
    fun shape(timeline: Timeline, clipId: String, shape: WindowShape?): EditResult? {
        val clip = picture(timeline, clipId)
        if (shape == WindowShape.FULL) throw EditRejected("遮罩需要一个形状")
        val current = clip.mask
        val (mask, message) = when {
            shape == null -> if (current == null) return null else null to "已移除遮罩"
            current == null -> newMask(shape) to "已添加${shape.label}遮罩：形状之外露出下层轨道"
            current.shape == shape -> return null
            else -> current.copy(shape = shape) to "遮罩形状：${shape.label}"
        }
        return EditResult(timeline.replaceClip(clip.copy(mask = mask)), clipId, message)
    }

    /**
     * The mask reshaped or moved by [change] at [frame] (frames from the clip's start): a mask without keyframes changes
     * throughout; a keyed one changes (or gains) its keyframe there.
     */
    fun adjust(timeline: Timeline, clipId: String, frame: Int, change: (PowerWindow) -> PowerWindow): Timeline {
        val clip = picture(timeline, clipId)
        val mask = clip.mask ?: throw EditRejected("先为片段选择遮罩形状")
        return timeline.replaceClip(clip.copy(mask = windowKeys.adjust(mask, frame, clip.durationFrames, NOUN, change)))
    }

    /** Shows the picture outside the shape instead of inside it ([on]), or inside again; null when it already does. */
    fun invert(timeline: Timeline, clipId: String, on: Boolean): EditResult? {
        val clip = picture(timeline, clipId)
        val mask = clip.mask ?: throw EditRejected("先为片段选择遮罩形状")
        if (mask.invert == on) return null
        val message = if (on) "已反转遮罩：显示形状之外的画面" else "遮罩恢复为显示形状之内的画面"
        return EditResult(timeline.replaceClip(clip.copy(mask = mask.copy(invert = on))), clipId, message)
    }

    /** Keys the mask's geometry at [frame] as it stands there (the motion unchanged), or removes the keyframe there. */
    fun toggleKey(timeline: Timeline, clipId: String, frame: Int): EditResult {
        val clip = picture(timeline, clipId)
        val mask = clip.mask ?: throw EditRejected("先为片段选择遮罩形状")
        val edit = windowKeys.toggle(mask, frame, clip.durationFrames, NOUN)
        return EditResult(timeline.replaceClip(clip.copy(mask = edit.window)), clipId, edit.message)
    }

    /** How the mask's keyframe button stands at [frame] of [clip]: no keys, keys elsewhere, or a key right there. */
    fun keyState(clip: Clip, frame: Int): ParamKeyState {
        val mask = clip.mask ?: return ParamKeyState.NONE
        return when {
            mask.keyframeAt(frame) != null -> ParamKeyState.AT_KEY
            mask.keyframes.isNotEmpty() -> ParamKeyState.ANIMATED
            else -> ParamKeyState.NONE
        }
    }

    private fun picture(timeline: Timeline, clipId: String): Clip {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以加遮罩")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        return clip
    }

    companion object {
        const val NOUN = "遮罩"

        /** A new mask: centred, its edge softened a little so it does not alias; a rectangle takes the frame's shape. */
        fun newMask(shape: WindowShape): PowerWindow = when (shape) {
            WindowShape.RECTANGLE -> PowerWindow(shape = shape, size = 0.6f, aspect = 16f / 9f, softness = 0.04f)
            WindowShape.GRADIENT -> PowerWindow(shape = shape, softness = 0.3f)
            else -> PowerWindow(shape = shape, size = 0.6f, softness = 0.06f)
        }
    }
}
