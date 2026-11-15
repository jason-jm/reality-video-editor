package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panAt
import com.example.prismstudio.p40b43a88.domain.model.panKeyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.ParamKey
import com.example.prismstudio.p40b43a88.domain.model.track
import com.example.prismstudio.p40b43a88.domain.model.transformKeyframes
import com.example.prismstudio.p40b43a88.domain.model.withKeysAt
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.KeyCurveChannel
import com.example.prismstudio.p40b43a88.domain.model.CurveEase
import com.example.prismstudio.p40b43a88.domain.model.CurveKey
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.KeyCurve
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.VolumeLine
import com.example.prismstudio.p40b43a88.domain.model.amountAt
import com.example.prismstudio.p40b43a88.domain.model.keyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesWithKeyAt
import kotlin.math.roundToInt

/**
 * Resolve's Keyframe Curve Editor for one clip: each animatable value as a curve — a picture's transform settings and its
 * effects' amounts, a sound's volume — whose keys can be dragged in time and value, whose sides take an ease preset or a
 * slope set by dragging a control point, and which can gain or lose keys. Each transform setting has keys of its own (as in
 * Resolve): moving, easing, adding or deleting one touches that setting's curve only. Frames are counted from the clip's
 * start.
 */
class KeyframeCurvesUseCase {

    /** Every curve [clip] has, in the editor's order: a sound's volume and pan; a picture's transform settings, then its effects. */
    fun curves(clip: Clip): List<KeyCurve> =
        if (clip.kind == ClipKind.AUDIO) listOf(curve(clip, KeyCurveChannel.Volume)!!, curve(clip, KeyCurveChannel.Pan)!!)
        else CurveParam.entries.map { curve(clip, KeyCurveChannel.Transform(it))!! } + clip.effects.mapNotNull { curve(clip, KeyCurveChannel.Effect(it.id)) }

    /** [clip]'s curve for [channel], or null when it has none (an effect gone, a volume on a picture). */
    fun curve(clip: Clip, channel: KeyCurveChannel): KeyCurve? = when (channel) {
        is KeyCurveChannel.Transform -> {
            if (clip.kind == ClipKind.AUDIO) null
            else KeyCurve(
                channel, channel.param.label, channel.param.unit, channel.param.min.toFloat(), channel.param.max.toFloat(), 0,
                clip.keyframes.track(channel.param).map { CurveKey(it.frame, it.value, it.ease) },
                channel.param.read(clip.transform).toFloat(),
            )
        }
        KeyCurveChannel.Volume -> {
            if (clip.kind != ClipKind.AUDIO) null
            else KeyCurve(
                channel, "音量", "dB", VolumeLine.MIN_DB, VolumeLine.MAX_DB, 1,
                clip.volumeKeyframes.map { CurveKey(it.frame, it.volumeDb, it.ease) }, clip.volumeDb,
            )
        }
        KeyCurveChannel.Pan -> {
            if (clip.kind != ClipKind.AUDIO) null
            else KeyCurve(
                channel, "声像", "", -PAN_SCALE, PAN_SCALE, 0,
                clip.panKeyframes.map { CurveKey(it.frame, it.value * PAN_SCALE, it.ease) }, clip.pan * PAN_SCALE,
            )
        }
        is KeyCurveChannel.Effect -> clip.effects.firstOrNull { it.id == channel.effectId }?.let { e ->
            KeyCurve(channel, e.type.label, "", 0f, 100f, 0, e.keyframes.map { CurveKey(it.frame, it.amount.toFloat(), it.ease) }, e.amount.toFloat())
        }
    }

    /**
     * Key [index] of [channel] dragged to [frame] and [value]: in time no further than a frame short of its neighbours (and
     * within the clip), the value within the curve's range.
     */
    fun moveKey(timeline: Timeline, clipId: String, channel: KeyCurveChannel, index: Int, frame: Int, value: Float): EditResult {
        val clip = editable(timeline, clipId)
        val curve = curve(clip, channel) ?: throw EditRejected(NO_CURVE)
        val key = curve.keys.getOrNull(index) ?: throw EditRejected(NO_KEY)
        val lo = curve.keys.getOrNull(index - 1)?.frame?.plus(1) ?: 0
        val hi = curve.keys.getOrNull(index + 1)?.frame?.minus(1) ?: (clip.durationFrames - 1)
        val f = frame.coerceIn(lo, hi.coerceAtLeast(lo))
        val v = curve.rounded(value.coerceIn(curve.min, curve.max))
        val moved = when (channel) {
            // Its own key only: the other parameters' keys stay where they are (Resolve keys each parameter apart).
            is KeyCurveChannel.Transform -> clip.withTrack(channel.param, clip.keyframes.track(channel.param).mapIndexed { i, k ->
                if (i == index) k.copy(frame = f, value = v.roundToInt().toFloat()) else k
            })
            KeyCurveChannel.Volume -> clip.copy(volumeKeyframes = clip.volumeKeyframes.mapIndexed { i, k -> if (i == index) k.copy(frame = f, volumeDb = v) else k })
            KeyCurveChannel.Pan -> clip.copy(panKeyframes = clip.panKeyframes.mapIndexed { i, k -> if (i == index) k.copy(frame = f, value = v / PAN_SCALE) else k })
            is KeyCurveChannel.Effect -> clip.copy(effects = clip.effects.map { e ->
                if (e.id != channel.effectId) e else e.copy(keyframes = e.keyframes.mapIndexed { i, k -> if (i == index) k.copy(frame = f, amount = v.roundToInt()) else k })
            })
        }
        val message = if (f != key.frame && v != key.value) "关键帧移到第 $f 帧 · ${curve.format(v)}"
        else if (f != key.frame) "关键帧移到第 $f 帧" else "${curve.label}：${curve.format(v)}"
        return EditResult(timeline.replaceClip(moved), clipId, message)
    }

    /**
     * Key [index]'s side facing the previous key ([arriving]) or the next set to [slope] (0 eases, 1 is even,
     * [KeyEase.MAX_SLOPE] the steepest), as dragging its control point does.
     */
    fun setSlope(timeline: Timeline, clipId: String, channel: KeyCurveChannel, index: Int, arriving: Boolean, slope: Float): EditResult {
        val clip = editable(timeline, clipId)
        val curve = curve(clip, channel) ?: throw EditRejected(NO_CURVE)
        val key = curve.keys.getOrNull(index) ?: throw EditRejected(NO_KEY)
        if (arriving && index == 0) throw EditRejected("第一个关键帧之前没有变化，无需缓入")
        if (!arriving && index == curve.keys.lastIndex) throw EditRejected("最后一个关键帧之后没有变化，无需缓出")
        val s = (slope * 100).roundToInt().coerceIn(0, (KeyEase.MAX_SLOPE * 100).roundToInt()) / 100f
        val ease = if (arriving) key.ease.copy(slopeIn = s) else key.ease.copy(slopeOut = s)
        val side = if (arriving) "进入" else "离开"
        return EditResult(timeline.replaceClip(withEase(clip, channel, index, ease)), clipId, "${side}斜率 ${String.format(java.util.Locale.US, "%.2f", s)}")
    }

    /**
     * Key [index] eased as [preset] says on both its sides. The first key's arriving side and the last's leaving one move
     * nothing, but keep the pick: the chip stays on it, and it takes effect once a key is added beyond.
     */
    fun ease(timeline: Timeline, clipId: String, channel: KeyCurveChannel, index: Int, preset: CurveEase): EditResult {
        val clip = editable(timeline, clipId)
        val curve = curve(clip, channel) ?: throw EditRejected(NO_CURVE)
        curve.keys.getOrNull(index) ?: throw EditRejected(NO_KEY)
        if (curve.keys.size < 2) throw EditRejected("只有一个关键帧，没有变化可以缓动")
        val ease = preset.ease
        return EditResult(timeline.replaceClip(withEase(clip, channel, index, ease)), clipId, "关键帧缓动：${preset.label}")
    }

    /** A key on [channel] at [frame] holding the value there (the motion stays the same). */
    fun addKey(timeline: Timeline, clipId: String, channel: KeyCurveChannel, frame: Int): EditResult {
        val clip = editable(timeline, clipId)
        val curve = curve(clip, channel) ?: throw EditRejected(NO_CURVE)
        if (frame !in 0 until clip.durationFrames) throw EditRejected("把播放头移到片段内，再添加关键帧")
        if (curve.keys.any { it.frame == frame }) throw EditRejected("这里已有关键帧")
        val keyed = when (channel) {
            is KeyCurveChannel.Transform -> clip.copy(keyframes = clip.keyframes.withKeysAt(frame, setOf(channel.param), clip.transform))
            KeyCurveChannel.Volume -> clip.copy(volumeKeyframes = clip.volumeKeyframesWithKeyAt(frame))
            KeyCurveChannel.Pan -> clip.copy(panKeyframes = clip.panKeyframesWithKeyAt(frame))
            is KeyCurveChannel.Effect -> clip.copy(effects = clip.effects.map { e ->
                if (e.id != channel.effectId) e else e.copy(keyframes = e.keyframesWithKeyAt(frame))
            })
        }
        val count = curve(keyed, channel)!!.keys.size
        return EditResult(timeline.replaceClip(keyed), clipId, "已在第 $frame 帧添加关键帧 · 共 $count 个")
    }

    /** Key [index] of [channel] removed; the last one leaves its value as the setting's own (it does not jump back). */
    fun deleteKey(timeline: Timeline, clipId: String, channel: KeyCurveChannel, index: Int): EditResult {
        val clip = editable(timeline, clipId)
        val curve = curve(clip, channel) ?: throw EditRejected(NO_CURVE)
        curve.keys.getOrNull(index) ?: throw EditRejected(NO_KEY)
        val updated = when (channel) {
            is KeyCurveChannel.Transform -> {
                val track = clip.keyframes.track(channel.param)
                val rest = track.filterIndexed { i, _ -> i != index }
                // The last key gone: the parameter keeps its value instead of jumping back.
                val kept = if (rest.isEmpty()) clip.copy(transform = channel.param.write(clip.transform, track[index].value.roundToInt())) else clip
                kept.withTrack(channel.param, rest)
            }
            KeyCurveChannel.Volume -> {
                val gone = clip.volumeKeyframes[index]
                val rest = clip.volumeKeyframes.filterIndexed { i, _ -> i != index }
                clip.copy(volumeKeyframes = rest, volumeDb = if (rest.isEmpty()) gone.volumeDb else clip.volumeDb)
            }
            KeyCurveChannel.Pan -> {
                val gone = clip.panKeyframes[index]
                val rest = clip.panKeyframes.filterIndexed { i, _ -> i != index }
                clip.copy(panKeyframes = rest, pan = if (rest.isEmpty()) gone.value else clip.pan)
            }
            is KeyCurveChannel.Effect -> clip.copy(effects = clip.effects.map { e ->
                if (e.id != channel.effectId) e else {
                    val gone = e.keyframes[index]
                    val rest = e.keyframes.filterIndexed { i, _ -> i != index }
                    e.copy(keyframes = rest, amount = if (rest.isEmpty()) gone.amount else e.amount)
                }
            })
        }
        val left = curve(updated, channel)!!.keys.size
        val message = if (left == 0) "已删除关键帧，${curve.label}不再变化" else "已删除关键帧 · 还剩 $left 个"
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    /** [channel]'s value [frame] frames into [clip], as playback has it. */
    fun valueAt(clip: Clip, channel: KeyCurveChannel, frame: Int): Float? = when (channel) {
        is KeyCurveChannel.Transform -> curve(clip, channel)?.valueAt(frame.toFloat())
        KeyCurveChannel.Volume -> if (clip.kind == ClipKind.AUDIO) clip.volumeAt(frame) else null
        KeyCurveChannel.Pan -> if (clip.kind == ClipKind.AUDIO) clip.panAt(frame) * PAN_SCALE else null
        is KeyCurveChannel.Effect -> clip.effects.firstOrNull { it.id == channel.effectId }?.amountAt(frame)?.toFloat()
    }

    private fun withEase(clip: Clip, channel: KeyCurveChannel, index: Int, ease: KeyEase): Clip = when (channel) {
        is KeyCurveChannel.Transform -> clip.withTrack(channel.param, clip.keyframes.track(channel.param).mapIndexed { i, k -> if (i == index) k.copy(ease = ease) else k })
        KeyCurveChannel.Volume -> clip.copy(volumeKeyframes = clip.volumeKeyframes.mapIndexed { i, k -> if (i == index) k.copy(ease = ease) else k })
        KeyCurveChannel.Pan -> clip.copy(panKeyframes = clip.panKeyframes.mapIndexed { i, k -> if (i == index) k.copy(ease = ease) else k })
        is KeyCurveChannel.Effect -> clip.copy(effects = clip.effects.map { e ->
            if (e.id != channel.effectId) e else e.copy(keyframes = e.keyframes.mapIndexed { i, k -> if (i == index) k.copy(ease = ease) else k })
        })
    }

    /** This clip with [param]'s keys replaced by [track], the other parameters' kept. */
    private fun Clip.withTrack(param: CurveParam, track: List<ParamKey>): Clip {
        val tracks = CurveParam.entries.associateWith { if (it == param) track else keyframes.track(it) }.filterValues { it.isNotEmpty() }
        return copy(keyframes = transformKeyframes(tracks, transform))
    }

    private fun editable(timeline: Timeline, clipId: String): Clip {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        return clip
    }

    private companion object {
        /** The pan curve draws −1…1 as −100…100. */
        const val PAN_SCALE = 100f
        const val NO_CURVE = "这个片段没有这条曲线"
        const val NO_KEY = "关键帧已不存在"
    }
}
