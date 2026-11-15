package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType

/** Applies a transition (into the clip) or an effect to the selected picture clip. */
class DecorateClipUseCase(private val ids: IdGenerator) {

    /**
     * [type] as the selected clip's opening transition, centred on the cut as Resolve places a new one (it slides to
     * the side with picture when the other has none). Over one it already has, the kind changes and its length and
     * style stay (as Resolve replaces a transition dropped on another).
     */
    fun applyTransition(timeline: Timeline, clipId: String?, type: TransitionType, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("转场只能应用到画面片段")
        timeline.requireUnlocked(listOf(clip), clip)
        clip.transitionIn?.let { return EditResult(retyped(timeline, clip, it, type), clip.id, "已将“${clip.name}”的转场换成：${type.label}") }
        val frames = minOf(fps / 2, clip.durationFrames / 2).coerceAtLeast(1)
        val updated = clip.copy(transitionIn = Transition(type, frames, alignment = TransitionAlignment.CENTER))
        return EditResult(timeline.replaceClip(updated), clip.id, "已为“${clip.name}”应用转场：${type.label}")
    }

    /** `transition-type-menu`: the selected clip's opening transition becomes the [type] kind, its length and style kept. */
    fun changeTransitionType(timeline: Timeline, clipId: String?, type: TransitionType): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val transition = clip.transitionIn ?: throw EditRejected("所选片段没有转场")
        if (transition.type == type) throw EditRejected("转场已经是${type.label}")
        timeline.requireUnlocked(listOf(clip), clip)
        return EditResult(retyped(timeline, clip, transition, type), clip.id, "转场改为：${type.label}")
    }

    /**
     * The selected clip's opening transition restyled by [change] — where it sits around the cut, its ease, direction,
     * soft edge or border (Resolve's transition inspector); softness and border stay within 0…[Transition.MAX_STYLE].
     */
    fun styleTransition(timeline: Timeline, clipId: String?, change: (Transition) -> Transition): Timeline {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val transition = clip.transitionIn ?: throw EditRejected("所选片段没有转场")
        timeline.requireUnlocked(listOf(clip), clip)
        val styled = change(transition).let {
            it.copy(
                type = transition.type,
                frames = transition.frames,
                softness = it.softness.coerceIn(0, Transition.MAX_STYLE),
                border = it.border.coerceIn(0, Transition.MAX_STYLE),
                angle = Transition.normalizedAngle(it.angle),
            )
        }
        return timeline.replaceClip(clip.copy(transitionIn = styled))
    }

    private fun retyped(timeline: Timeline, clip: Clip, transition: Transition, type: TransitionType): Timeline =
        timeline.replaceClip(clip.copy(transitionIn = transition.copy(type = type)))

    /** Sets the opening transition's length: at least one frame, at most two seconds or half the clip. */
    fun setTransitionFrames(timeline: Timeline, clipId: String?, frames: Int, fps: Int): Timeline {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val transition = clip.transitionIn ?: throw EditRejected("所选片段没有转场")
        timeline.requireUnlocked(listOf(clip), clip)
        return timeline.replaceClip(clip.copy(transitionIn = transition.copy(frames = frames.coerceIn(1, maxTransitionFrames(clip, fps)))))
    }

    fun removeTransition(timeline: Timeline, clipId: String?): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val transition = clip.transitionIn ?: throw EditRejected("所选片段没有转场")
        timeline.requireUnlocked(listOf(clip), clip)
        return EditResult(timeline.replaceClip(clip.copy(transitionIn = null)), clip.id, "已移除转场：${transition.type.label}")
    }

    fun applyEffect(timeline: Timeline, clipId: String?, type: EffectType): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("特效只能应用到画面片段")
        timeline.requireUnlocked(listOf(clip), clip)
        val updated = clip.copy(effects = clip.effects + ClipEffect(ids.next("fx"), type))
        return EditResult(timeline.replaceClip(updated), clip.id, "已应用特效：${type.label}")
    }

    fun removeEffect(timeline: Timeline, clipId: String?, effectId: String): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val effect = clip.effects.firstOrNull { it.id == effectId } ?: throw EditRejected("特效不存在")
        timeline.requireUnlocked(listOf(clip), clip)
        val updated = clip.copy(effects = clip.effects - effect)
        return EditResult(timeline.replaceClip(updated), clip.id, "已移除特效：${effect.type.label}")
    }

    companion object {
        /** Longest opening transition for [clip]: two seconds, and never more than half the clip. */
        fun maxTransitionFrames(clip: Clip, fps: Int): Int =
            minOf(2 * fps.coerceAtLeast(1), clip.durationFrames / 2).coerceAtLeast(1)
    }
}
