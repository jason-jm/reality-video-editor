package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.panKeyframeAt
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.withKeysAt
import com.example.prismstudio.p40b43a88.domain.model.withoutKeysAt
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.keyframeAt
import com.example.prismstudio.p40b43a88.domain.model.keyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframeAt
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesWithKeyAt

/**
 * Keyframes of a picture clip's transform (scale, position, rotation, opacity), as in Resolve's
 * inspector: a keyframe holds the value at a frame, and the picture moves between keyframes —
 * linearly, or easing in and out where a keyframe says so. Frames are counted from the clip's start.
 */
class KeyframeUseCase {

    /** Adds a keyframe holding the current value at [frame] (the motion stays the same), or removes the keyframe there. */
    fun toggle(timeline: Timeline, clipId: String, frame: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面属性")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加关键帧")
        val existing = clip.keyframeAt(frame)
        val updated = if (existing != null) withoutKeys(clip, frame, TransformKeyGroup.ALL) else clip.copy(keyframes = clip.keyframesWithKeyAt(frame))
        val count = updated.keyframes.size
        val message = when {
            existing == null -> "已添加关键帧 · 共 $count 个"
            count == 0 -> "已删除关键帧，片段不再有动画"
            else -> "已删除关键帧 · 还剩 $count 个"
        }
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    /**
     * Turns easing on or off where the motion reaches ([arriving]) or leaves the keyframe at [frame]:
     * eased, the picture slows to a stop there (or starts from rest) instead of moving at an even pace.
     */
    fun ease(timeline: Timeline, clipId: String, frame: Int, arriving: Boolean): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面属性")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        val key = clip.keyframeAt(frame) ?: throw EditRejected("把播放头移到关键帧上，再设置缓入缓出")
        requireNeighbour(clip.keyframes.map { it.frame }, frame, arriving)
        val on = !key.ease.eases(arriving)
        val keyed = clip.keyframes.map {
            if (it != key) return@map it
            val shared = it.ease.with(arriving, on)
            // Every parameter keyed here eases alike, those with an ease of their own too.
            it.copy(ease = shared, paramEases = it.paramEases.mapValues { (_, e) -> e.with(arriving, on) }.filterValues { e -> e != shared })
        }
        return EditResult(timeline.replaceClip(clip.copy(keyframes = keyed)), clipId, easeMessage(arriving, on))
    }

    /**
     * Resolve's keyframe button beside a parameter: [group]'s parameters keyed at [frame] at the values they have there (their
     * motion unchanged), or their keys there removed — a parameter left without keys keeps the value it had there.
     */
    fun toggleGroup(timeline: Timeline, clipId: String, frame: Int, group: TransformKeyGroup): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面属性")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加关键帧")
        val keyedHere = clip.keyframes.any { k -> k.frame == frame && group.params.any { it in k.params } }
        val updated = if (keyedHere) withoutKeys(clip, frame, group.params) else clip.copy(keyframes = clip.keyframes.withKeysAt(frame, group.params, clip.transform))
        val count = updated.keyframes.count { k -> group.params.any { it in k.params } }
        val message = when {
            !keyedHere -> "已为${group.label}添加关键帧 · 共 $count 个"
            count == 0 -> "已删除${group.label}的关键帧，${group.label}不再变化"
            else -> "已删除${group.label}的关键帧 · 还剩 $count 个"
        }
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    /** [clip] without [params]' keys at [frame]: a parameter left without keys keeps the value it had there instead of jumping back. */
    private fun withoutKeys(clip: Clip, frame: Int, params: Set<CurveParam>): Clip {
        val held = clip.transformAt(frame)
        val rest = clip.keyframes.withoutKeysAt(frame, params, clip.transform)
        val base = params.filter { p -> rest.none { p in it.params } }.fold(clip.transform) { t, p -> p.write(t, p.read(held)) }
        return clip.copy(keyframes = rest, transform = base)
    }

    /** How [group] stands at [frame] of [clip]: no keys, animated, or keyed right there. */
    fun groupState(clip: Clip, frame: Int, group: TransformKeyGroup): ParamKeyState = when {
        clip.keyframes.any { k -> k.frame == frame && group.params.any { it in k.params } } -> ParamKeyState.AT_KEY
        clip.keyframes.any { k -> group.params.any { it in k.params } } -> ParamKeyState.ANIMATED
        else -> ParamKeyState.NONE
    }

    /** The keyframe before [frame], if any. */
    fun previous(clip: Clip, frame: Int): Int? = clip.keyframes.lastOrNull { it.frame < frame }?.frame

    /** The keyframe after [frame], if any. */
    fun next(clip: Clip, frame: Int): Int? = clip.keyframes.firstOrNull { it.frame > frame }?.frame

    /**
     * Adds a volume keyframe holding the current volume at [frame] of the sound clip [clipId], or
     * removes the one there (the last one leaves its value as the clip's volume).
     */
    fun toggleVolume(timeline: Timeline, clipId: String, frame: Int): EditResult {
        val clip = soundClip(timeline, clipId)
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加音量关键帧")
        val existing = clip.volumeKeyframeAt(frame)
        val updated = if (existing != null) {
            val rest = clip.volumeKeyframes - existing
            clip.copy(volumeKeyframes = rest, volumeDb = if (rest.isEmpty()) existing.volumeDb else clip.volumeDb)
        } else {
            clip.copy(volumeKeyframes = clip.volumeKeyframesWithKeyAt(frame))
        }
        val count = updated.volumeKeyframes.size
        val message = when {
            existing == null -> "已添加音量关键帧 · 共 $count 个"
            count == 0 -> "已删除音量关键帧，音量不再变化"
            else -> "已删除音量关键帧 · 还剩 $count 个"
        }
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    /**
     * `clip-pan-key-button`: adds a pan keyframe holding the current pan at [frame] of the sound clip [clipId], or removes
     * the one there (the last one leaves its value as the clip's pan).
     */
    fun togglePan(timeline: Timeline, clipId: String, frame: Int): EditResult {
        val clip = soundClip(timeline, clipId)
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加声像关键帧")
        val existing = clip.panKeyframeAt(frame)
        val updated = if (existing != null) {
            val rest = clip.panKeyframes - existing
            clip.copy(panKeyframes = rest, pan = if (rest.isEmpty()) existing.value else clip.pan)
        } else {
            clip.copy(panKeyframes = clip.panKeyframesWithKeyAt(frame))
        }
        val count = updated.panKeyframes.size
        val message = when {
            existing == null -> "已添加声像关键帧 · 共 $count 个"
            count == 0 -> "已删除声像关键帧，声像不再变化"
            else -> "已删除声像关键帧 · 还剩 $count 个"
        }
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    /** The pan diamond at [frame] of [clip]: no keys, keys elsewhere, or a key there. */
    fun panState(clip: Clip, frame: Int): ParamKeyState = when {
        clip.panKeyframes.isEmpty() -> ParamKeyState.NONE
        clip.panKeyframeAt(frame) != null -> ParamKeyState.AT_KEY
        else -> ParamKeyState.ANIMATED
    }

    /** [ease] for the volume keyframe at [frame] of the sound clip [clipId]: the level glides in or out. */
    fun easeVolume(timeline: Timeline, clipId: String, frame: Int, arriving: Boolean): EditResult {
        val clip = soundClip(timeline, clipId)
        val key = clip.volumeKeyframeAt(frame) ?: throw EditRejected("把播放头移到音量关键帧上，再设置缓入缓出")
        requireNeighbour(clip.volumeKeyframes.map { it.frame }, frame, arriving)
        val on = !key.ease.eases(arriving)
        val keyed = clip.volumeKeyframes.map { if (it == key) it.copy(ease = it.ease.with(arriving, on)) else it }
        return EditResult(timeline.replaceClip(clip.copy(volumeKeyframes = keyed)), clipId, easeMessage(arriving, on))
    }

    /**
     * `effect-keyframe-button`: adds a keyframe holding effect [effectId]'s amount at [frame] of picture clip [clipId] (the
     * change stays the same), or removes the one there — the last one leaves its amount as the effect's (Resolve keyframes
     * any parameter).
     */
    fun toggleEffect(timeline: Timeline, clipId: String, effectId: String, frame: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        val effect = clip.effects.firstOrNull { it.id == effectId } ?: throw EditRejected("特效不存在")
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加特效关键帧")
        val existing = effect.keyframeAt(frame)
        val updated = if (existing != null) {
            val rest = effect.keyframes - existing
            effect.copy(keyframes = rest, amount = if (rest.isEmpty()) existing.amount else effect.amount)
        } else {
            effect.copy(keyframes = effect.keyframesWithKeyAt(frame))
        }
        val count = updated.keyframes.size
        val name = effect.type.label
        val message = when {
            existing == null -> "已为${name}添加关键帧 · 共 $count 个"
            count == 0 -> "已删除${name}的关键帧，强度不再变化"
            else -> "已删除${name}的关键帧 · 还剩 $count 个"
        }
        val effects = clip.effects.map { if (it.id == effectId) updated else it }
        return EditResult(timeline.replaceClip(clip.copy(effects = effects)), clipId, message)
    }

    /** Effect [effectId]'s keyframe before [frame] of [clip], if any. */
    fun previousEffect(clip: Clip, effectId: String, frame: Int): Int? =
        clip.effects.firstOrNull { it.id == effectId }?.keyframes?.lastOrNull { it.frame < frame }?.frame

    /** Effect [effectId]'s keyframe after [frame] of [clip], if any. */
    fun nextEffect(clip: Clip, effectId: String, frame: Int): Int? =
        clip.effects.firstOrNull { it.id == effectId }?.keyframes?.firstOrNull { it.frame > frame }?.frame

    fun previousVolume(clip: Clip, frame: Int): Int? = clip.volumeKeyframes.lastOrNull { it.frame < frame }?.frame

    fun nextVolume(clip: Clip, frame: Int): Int? = clip.volumeKeyframes.firstOrNull { it.frame > frame }?.frame

    private fun soundClip(timeline: Timeline, clipId: String): Clip {
        val clip = timeline.clip(clipId) ?: throw EditRejected("所选片段没有音频")
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("所选片段没有音频")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        return clip
    }

}

/** Easing in needs motion arriving (a keyframe before), easing out motion leaving (one after). */
internal fun requireNeighbour(frames: List<Int>, frame: Int, arriving: Boolean) {
    if (arriving && frames.none { it < frame }) throw EditRejected("第一个关键帧之前没有变化，无需缓入")
    if (!arriving && frames.none { it > frame }) throw EditRejected("最后一个关键帧之后没有变化，无需缓出")
}

internal fun KeyEase.eases(arriving: Boolean) = if (arriving) easesIn else easesOut

internal fun easeMessage(arriving: Boolean, on: Boolean) = when {
    arriving && on -> "已开启缓入 · 到达此关键帧时逐渐减速"
    arriving -> "已关闭缓入"
    on -> "已开启缓出 · 离开此关键帧时逐渐加速"
    else -> "已关闭缓出"
}
