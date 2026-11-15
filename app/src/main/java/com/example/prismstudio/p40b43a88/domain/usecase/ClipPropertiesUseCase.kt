package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.retimedPan
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.keyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.withKeysAt
import com.example.prismstudio.p40b43a88.domain.model.retimedEffects
import com.example.prismstudio.p40b43a88.domain.model.retimed
import com.example.prismstudio.p40b43a88.domain.model.retimedVolume
import com.example.prismstudio.p40b43a88.domain.model.VolumeLine
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.retimedKeys
import kotlin.math.roundToInt

/**
 * Inspector edits on one clip. Speed changes resize the clip around its source range and ripple
 * the clips after it (on its track and its linked partner's track); fades never exceed half the
 * clip or five seconds.
 */
class ClipPropertiesUseCase {

    /**
     * Changes a picture clip's transform. As in Resolve each parameter is keyed on its own: a parameter whose group
     * ([TransformKeyGroup]) has keys changes at [atFrame] (frames from the clip's start, inside the clip) — the group's key
     * there, added if needed without changing its motion around it; a parameter without keys changes outright, whatever the
     * others do.
     */
    fun transform(timeline: Timeline, clipId: String, atFrame: Int? = null, update: (ClipTransform) -> ClipTransform): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面属性")
        if (clip.keyframes.isEmpty()) return timeline.replaceClip(clip.copy(transform = clamp(update(clip.transform))))
        val frame = atFrame?.takeIf { it in 0 until clip.durationFrames }
        val now = if (frame != null) clip.transformAt(frame) else clip.transform
        val target = clamp(update(now))
        val changed = CurveParam.entries.filter { it.read(target) != it.read(now) }
        if (changed.isEmpty()) return timeline
        // Keys go to the inspector's groups, as its keyframe buttons do: changing X of an animated position keys Y there too.
        val animated = changed.map { TransformKeyGroup.of(it) }.distinct()
            .filter { g -> clip.keyframes.any { k -> g.params.any { it in k.params } } }
            .flatMap { it.params }.toSet()
        var base = clip.transform
        (changed - animated).forEach { base = it.write(base, it.read(target)) }
        if (animated.isEmpty()) return timeline.replaceClip(clip.copy(transform = base))
        if (frame == null) throw EditRejected("把播放头移到片段内，再调整关键帧")
        // The keys there keep their easing; new ones are added without changing the motion around them.
        val keyed = clip.keyframes.withKeysAt(frame, animated, base).map { k ->
            if (k.frame != frame) k else k.copy(transform = animated.fold(k.transform) { t, p -> p.write(t, p.read(target)) })
        }
        return timeline.replaceClip(clip.copy(transform = base, keyframes = keyed))
    }

    private fun clamp(t: ClipTransform) = ClipTransform(
        scalePercent = t.scalePercent.coerceIn(10, 400),
        positionX = t.positionX.coerceIn(-960, 960),
        positionY = t.positionY.coerceIn(-540, 540),
        rotationDegrees = t.rotationDegrees.coerceIn(-180, 180),
        opacityPercent = t.opacityPercent.coerceIn(0, 100),
        cropLeft = t.cropLeft.coerceIn(0, CropControl.LEFT.max),
        cropRight = t.cropRight.coerceIn(0, CropControl.RIGHT.max),
        cropTop = t.cropTop.coerceIn(0, CropControl.TOP.max),
        cropBottom = t.cropBottom.coerceIn(0, CropControl.BOTTOM.max),
        cropSoftness = t.cropSoftness.coerceIn(0, CropControl.SOFTNESS.max),
    )

    /** Resolve's Dynamic Zoom on a picture clip (titles draw their own text), or off with null; the amount 5–50 %. */
    fun dynamicZoom(timeline: Timeline, clipId: String, zoom: DynamicZoom?): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以设置动态缩放")
        val clamped = zoom?.let { it.copy(amountPercent = it.amountPercent.coerceIn(DynamicZoom.MIN_AMOUNT, DynamicZoom.MAX_AMOUNT)) }
        return timeline.replaceClip(clip.copy(dynamicZoom = clamped))
    }

    /**
     * Resolve's Stabilization on a footage clip played forward, or off with null; smooth and strength 0–100. Turning it
     * off without losing the settings is [ClipStabilization.enabled].
     */
    fun stabilization(timeline: Timeline, clipId: String, settings: ClipStabilization?, asset: MediaAsset?): Timeline {
        val clip = editable(timeline, clipId)
        if (settings != null) StabilizationUseCase().unavailable(clip, asset)?.let { throw EditRejected(it) }
        val clamped = settings?.copy(smooth = settings.smooth.coerceIn(0, 100), strength = settings.strength.coerceIn(0, 100))
        return timeline.replaceClip(clip.copy(stabilization = clamped))
    }

    /**
     * Resolve's keyer on a picture clip (footage or a still): [change] applied to its key (null = off, and null turns it
     * off). Titles draw their own text over the finished picture, so they have none.
     */
    fun chromaKey(timeline: Timeline, clipId: String, change: (ChromaKey?) -> ChromaKey?): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以抠像")
        return timeline.replaceClip(clip.copy(chromaKey = change(clip.chromaKey)))
    }

    /** Resolve's Composite Mode for a picture clip; titles draw over the finished picture, so they have none. */
    fun composite(timeline: Timeline, clipId: String, mode: CompositeMode): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以设置合成模式")
        return timeline.replaceClip(clip.copy(composite = mode))
    }

    /** Resolve's Retime Process for a picture clip: the frames a slowed clip or slow footage lacks repeat or blend. */
    fun retime(timeline: Timeline, clipId: String, process: RetimeProcess): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以设置变速处理")
        return timeline.replaceClip(clip.copy(retime = process))
    }

    /**
     * Resolve's Scaling for a picture clip: fit, fill, stretch or keep at its own size a picture of another shape or
     * size; null follows the project's setting for mismatched media.
     */
    fun scaling(timeline: Timeline, clipId: String, scaling: ClipScaling?): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以设置缩放方式")
        return timeline.replaceClip(clip.copy(scaling = scaling))
    }

    /** Puts a picture clip full frame or into a picture-in-picture corner (rotation is reset). */
    fun place(timeline: Timeline, clipId: String, placement: Placement, atFrame: Int? = null): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段可以设置画面布局")
        return transform(timeline, clipId, atFrame) {
            it.copy(scalePercent = placement.scalePercent, positionX = placement.positionX, positionY = placement.positionY, rotationDegrees = 0)
        }
    }

    fun title(timeline: Timeline, clipId: String, text: String? = null, sizePx: Int? = null): Timeline {
        val clip = editable(timeline, clipId)
        val style = clip.title ?: throw EditRejected("所选片段不是标题")
        val next = TitleStyle(
            text = text ?: style.text,
            sizePx = (sizePx ?: style.sizePx).coerceIn(24, 160),
            template = style.template,
        )
        val kind = if (style.template == TitleTemplate.SUBTITLE) "字幕" else "标题"
        val name = if (text != null) "$kind：${text.ifBlank { "（空）" }}" else clip.name
        return timeline.replaceClip(clip.copy(title = next, name = name))
    }

    /** Volume and fades apply to the clip itself when it is audio, otherwise to its linked audio. */
    fun audioTarget(timeline: Timeline, clipId: String?): Clip? {
        val clip = timeline.clip(clipId) ?: return null
        if (clip.kind == ClipKind.AUDIO) return clip
        val link = clip.linkId ?: return null
        return timeline.clips.firstOrNull { it.linkId == link && it.kind == ClipKind.AUDIO }
    }

    /**
     * Sets a clip's volume. A clip with volume keyframes changes (or gains) the keyframe at [atFrame]
     * (frames from the clip's start, inside the clip); otherwise its one volume changes.
     */
    fun volume(timeline: Timeline, clipId: String, db: Float, atFrame: Int? = null): Timeline {
        val clip = editable(timeline, clipId)
        val value = db.coerceIn(-60f, 12f)
        if (clip.volumeKeyframes.isEmpty()) return timeline.replaceClip(clip.copy(volumeDb = value))
        val frame = atFrame?.takeIf { it in 0 until clip.durationFrames }
            ?: throw EditRejected("把播放头移到片段内，再调整音量关键帧")
        val keyed = clip.volumeKeyframesWithKeyAt(frame).map { if (it.frame == frame) it.copy(volumeDb = value) else it }
        return timeline.replaceClip(clip.copy(volumeKeyframes = keyed))
    }

    /**
     * Sets a sound clip's pan (Resolve's inspector Pan), −1 left … +1 right in hundredths, as [volume] sets its level: a
     * clip with pan keyframes changes (or gains) the one at [atFrame].
     */
    fun pan(timeline: Timeline, clipId: String, value: Float, atFrame: Int? = null): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有声音片段有声像")
        val v = (value.coerceIn(-1f, 1f) * 100).roundToInt() / 100f
        if (clip.panKeyframes.isEmpty()) return timeline.replaceClip(clip.copy(pan = v))
        val frame = atFrame?.takeIf { it in 0 until clip.durationFrames }
            ?: throw EditRejected("把播放头移到片段内，再调整声像关键帧")
        val keyed = clip.panKeyframesWithKeyAt(frame).map { if (it.frame == frame) it.copy(value = v) else it }
        return timeline.replaceClip(clip.copy(panKeyframes = keyed))
    }

    /**
     * Drags a sound clip's volume line [deltaDb] up from where the drag began, on the line's scale ([VolumeLine]): the
     * clip's one level, or the volume keyframes at [keyFrames] (frames from the clip's start) — a level below the line
     * starts from its bottom, as it is drawn there. Levels land on tenths of a dB.
     */
    fun volumeLine(timeline: Timeline, clipId: String, keyFrames: List<Int>, deltaDb: Float): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有音频片段有音量线")
        fun moved(db: Float) = ((db.coerceAtLeast(VolumeLine.MIN_DB) + deltaDb).coerceIn(VolumeLine.MIN_DB, VolumeLine.MAX_DB) * 10).roundToInt() / 10f
        if (keyFrames.isEmpty()) {
            if (clip.volumeKeyframes.isNotEmpty()) return timeline
            return timeline.replaceClip(clip.copy(volumeDb = moved(clip.volumeDb)))
        }
        val keys = clip.volumeKeyframes.map { if (it.frame in keyFrames) it.copy(volumeDb = moved(it.volumeDb)) else it }
        return timeline.replaceClip(clip.copy(volumeKeyframes = keys))
    }

    /**
     * A clip's fade handles (Resolve's): a sound's level or a picture's opacity from nothing over [fadeIn] seconds from
     * its start, and to nothing over [fadeOut] seconds to its end, each within [maxFade]; null keeps that one.
     */
    fun fades(timeline: Timeline, clipId: String, fps: Int, fadeIn: Float? = null, fadeOut: Float? = null): Timeline {
        val clip = editable(timeline, clipId)
        if (!ClipFades.takes(clip)) throw EditRejected("字幕随对白出现和消失，没有淡入淡出")
        val max = maxFade(clip, fps)
        return timeline.replaceClip(
            clip.copy(
                fadeInSeconds = (fadeIn ?: clip.fadeInSeconds).coerceIn(0f, max),
                fadeOutSeconds = (fadeOut ?: clip.fadeOutSeconds).coerceIn(0f, max),
            ),
        )
    }

    fun maxFade(clip: Clip, fps: Int): Float = ClipFades.max(clip, fps)

    /**
     * How a clip's fades bend (Resolve's fade curve dot): [fadeIn] and [fadeOut] −1…1 (0 linear, see [ClipFades.shape]);
     * null keeps one. A curve stays with its fade while the fade is shortened to nothing and comes back with it.
     */
    fun fadeCurves(timeline: Timeline, clipId: String, fadeIn: Float? = null, fadeOut: Float? = null): Timeline {
        val clip = editable(timeline, clipId)
        if (!ClipFades.takes(clip)) throw EditRejected("字幕随对白出现和消失，没有淡入淡出")
        return timeline.replaceClip(
            clip.copy(
                fadeInCurve = (fadeIn ?: clip.fadeInCurve).coerceIn(-1f, 1f),
                fadeOutCurve = (fadeOut ?: clip.fadeOutCurve).coerceIn(-1f, 1f),
            ),
        )
    }

    /** A sound clip's own equaliser (Resolve's clip EQ), before its track's: [change] applied to it. */
    fun eq(timeline: Timeline, clipId: String, change: (TrackEq) -> TrackEq): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有声音片段有均衡器")
        return timeline.replaceClip(clip.copy(eq = change(clip.eq)))
    }

    /** A sound clip's pitch ([Clip.pitchCents]), within an octave either way. */
    fun pitch(timeline: Timeline, clipId: String, cents: Int): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有声音片段可以变调")
        return timeline.replaceClip(clip.copy(pitchCents = cents.coerceIn(-Clip.MAX_PITCH_CENTS, Clip.MAX_PITCH_CENTS)))
    }

    /** `clip-pitch-note`: what the pitch shift does — how far, the interval it makes and that the length stays. */
    fun pitchNote(cents: Int): String {
        if (cents == 0) return "原调：拖动以升高或降低音调，长度和速度不变"
        val semis = Math.abs(cents) / 100f
        val way = if (cents > 0) "升高" else "降低"
        val amount = if (cents % 100 == 0) "${Math.abs(cents) / 100} 个半音" else String.format(java.util.Locale.US, "%.1f 个半音", semis)
        val interval = if (cents % 100 == 0) INTERVALS[Math.abs(cents) / 100]?.let { "（$it）" }.orEmpty() else ""
        val ratio = String.format(java.util.Locale.US, "%.3f", Clip.pitchRatio(cents))
        return "$way$amount$interval · 频率 ×$ratio · 长度和速度不变"
    }

    /** A sound clip's channel mapping ([Clip.channels], Resolve's clip attributes). */
    fun channels(timeline: Timeline, clipId: String, channels: AudioChannels): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有声音片段可以设置声道")
        return timeline.replaceClip(clip.copy(channels = channels))
    }

    /** A sound clip's cross fade from the sound right before it ([Clip.crossFadeFrames]), within [maxCrossFade]. */
    fun crossFade(timeline: Timeline, clipId: String, frames: Int, fps: Int): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("只有声音片段可以交叉淡化")
        return timeline.replaceClip(clip.copy(crossFadeFrames = frames.coerceIn(0, maxCrossFade(clip, fps))))
    }

    /** The longest cross fade a sound clip takes: two seconds, and never more than the clip. */
    fun maxCrossFade(clip: Clip, fps: Int): Int = minOf(MAX_CROSS_FADE_SECONDS * fps.coerceAtLeast(1), clip.durationFrames)

    /**
     * Changes playback speed: the same source range now lasts `old / factor` frames. Later clips on
     * the affected tracks move by the length difference so nothing overlaps.
     */
    fun speed(timeline: Timeline, clipId: String, speed: ClipSpeed): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind == ClipKind.TITLE) throw EditRejected("标题片段不能变速")
        if (clip.isCompound) throw EditRejected(COMPOUND_SPEED)
        if (clip.speed == speed && !clip.retimed) return timeline
        val members = timeline.linkedGroup(clip)
        timeline.requireUnlocked(members, clip)
        // Speed points give way: the whole clip plays at the one speed, its source kept.
        val sourceFrames = if (clip.retimed) clip.speedSections.sumOf { it.frames * it.speed.factor.toDouble() }.toFloat()
        else clip.durationFrames * clip.speed.factor
        val newFrames = (sourceFrames / speed.factor).toInt().coerceAtLeast(TrimClipUseCase.MIN_FRAMES)
        val delta = newFrames - clip.durationFrames
        val tracks = members.map { it.trackId }.toSet()
        val memberIds = members.map { it.id }.toSet()
        val updated = timeline.clips.map { other ->
            when {
                other.id in memberIds ->
                    other.copy(
                        speed = speed,
                        speedSections = emptyList(),
                        durationFrames = newFrames,
                        keyframes = other.keyframes.retimed(other.durationFrames, newFrames),
                        effects = other.effects.retimedEffects(other.durationFrames, newFrames),
                        volumeKeyframes = other.volumeKeyframes.retimedVolume(other.durationFrames, newFrames),
                        panKeyframes = other.panKeyframes.retimedPan(other.durationFrames, newFrames),
                        grade = other.grade.retimedKeys(other.durationFrames, newFrames),
                        mask = other.mask?.retimedKeys(other.durationFrames, newFrames),
                    )
                other.trackId in tracks && other.start >= clip.end -> other.copy(start = (other.start + delta).coerceAtLeast(0))
                else -> other
            }
        }
        return timeline.copy(clips = updated)
    }

    fun reverse(timeline: Timeline, clipId: String, reverse: Boolean): Timeline {
        val clip = editable(timeline, clipId)
        if (clip.kind == ClipKind.TITLE) throw EditRejected("标题片段不能倒放")
        if (clip.isCompound) throw EditRejected("复合片段不能倒放：打开后为里面的片段倒放")
        val group = timeline.linkedGroup(clip)
        if (reverse && group.any { it.retimed }) throw EditRejected("有变速点的片段不能倒放：先清除变速点")
        timeline.requireUnlocked(group, clip)
        val members = group.map { it.id }.toSet()
        return timeline.copy(clips = timeline.clips.map { if (it.id in members) it.copy(reverse = reverse) else it })
    }

    /**
     * Effect [effectId] switched on or off, or set to [amount]. A keyframed effect takes the amount at [atFrame] (frames
     * into the clip), into the keyframe there or a new one that leaves the change around it as it was (auto-keying, as
     * Resolve's inspector does once a parameter has keyframes).
     */
    fun effect(timeline: Timeline, clipId: String, effectId: String, enabled: Boolean? = null, amount: Int? = null, atFrame: Int? = null): Timeline {
        val clip = editable(timeline, clipId)
        val effects = clip.effects.map { effect ->
            when {
                effect.id != effectId -> effect
                amount == null || effect.keyframes.isEmpty() ->
                    effect.copy(enabled = enabled ?: effect.enabled, amount = (amount ?: effect.amount).coerceIn(0, 100))
                else -> {
                    val frame = atFrame?.takeIf { it in 0 until clip.durationFrames }
                        ?: throw EditRejected("把播放头移到片段内，再调整特效关键帧")
                    val keyed = effect.keyframesWithKeyAt(frame).map { if (it.frame == frame) it.copy(amount = amount.coerceIn(0, 100)) else it }
                    effect.copy(enabled = enabled ?: effect.enabled, keyframes = keyed)
                }
            }
        }
        return timeline.replaceClip(clip.copy(effects = effects))
    }

    private fun editable(timeline: Timeline, clipId: String): Clip {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        return clip
    }

    private companion object {
        const val MAX_CROSS_FADE_SECONDS = 2

        /** The intervals whole semitones make, up to the octave. */
        val INTERVALS = mapOf(
            1 to "小二度", 2 to "大二度", 3 to "小三度", 4 to "大三度", 5 to "纯四度", 6 to "三全音",
            7 to "纯五度", 8 to "小六度", 9 to "大六度", 10 to "小七度", 11 to "大七度", 12 to "八度",
        )
    }
}

/** Why a compound clip keeps its speed: its contents are retimed inside. */
internal const val COMPOUND_SPEED = "复合片段不能变速：打开后为里面的片段变速"
