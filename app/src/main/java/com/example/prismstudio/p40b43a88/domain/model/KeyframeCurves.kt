package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A transform setting the keyframe curve editor draws (Resolve's Keyframe Curve Editor), with the inspector's range. A
 * transform keyframe holds every setting at its frame, so its time and easing belong to all of them together.
 */
enum class CurveParam(val label: String, val unit: String, val min: Int, val max: Int) {
    SCALE("缩放", "%", 10, 400),
    POSITION_X("位置 X", "", -960, 960),
    POSITION_Y("位置 Y", "", -540, 540),
    ROTATION("旋转", "°", -180, 180),
    OPACITY("不透明度", "%", 0, 100),
    CROP_LEFT("裁切 左", "", 0, ClipTransform.REFERENCE_WIDTH),
    CROP_RIGHT("裁切 右", "", 0, ClipTransform.REFERENCE_WIDTH),
    CROP_TOP("裁切 上", "", 0, ClipTransform.REFERENCE_HEIGHT),
    CROP_BOTTOM("裁切 下", "", 0, ClipTransform.REFERENCE_HEIGHT),
    CROP_SOFTNESS("裁切柔化", "", 0, 100),
    ;

    fun read(t: ClipTransform): Int = when (this) {
        SCALE -> t.scalePercent
        POSITION_X -> t.positionX
        POSITION_Y -> t.positionY
        ROTATION -> t.rotationDegrees
        OPACITY -> t.opacityPercent
        CROP_LEFT -> t.cropLeft
        CROP_RIGHT -> t.cropRight
        CROP_TOP -> t.cropTop
        CROP_BOTTOM -> t.cropBottom
        CROP_SOFTNESS -> t.cropSoftness
    }

    fun write(t: ClipTransform, value: Int): ClipTransform {
        val v = value.coerceIn(min, max)
        return when (this) {
            SCALE -> t.copy(scalePercent = v)
            POSITION_X -> t.copy(positionX = v)
            POSITION_Y -> t.copy(positionY = v)
            ROTATION -> t.copy(rotationDegrees = v)
            OPACITY -> t.copy(opacityPercent = v)
            CROP_LEFT -> t.copy(cropLeft = v)
            CROP_RIGHT -> t.copy(cropRight = v)
            CROP_TOP -> t.copy(cropTop = v)
            CROP_BOTTOM -> t.copy(cropBottom = v)
            CROP_SOFTNESS -> t.copy(cropSoftness = v)
        }
    }
}

/** What one curve animates: a transform setting, a sound clip's volume or pan, or an effect's amount. [key] names it in the UI. */
sealed class KeyCurveChannel(val key: String) {
    data class Transform(val param: CurveParam) : KeyCurveChannel("t:${param.name}")
    data object Volume : KeyCurveChannel("volume")

    /** A sound clip's pan: stored −1…1, drawn −100 (left) … 100 (right), as Resolve's inspector shows it. */
    data object Pan : KeyCurveChannel("pan")
    data class Effect(val effectId: String) : KeyCurveChannel("fx:$effectId")

    companion object {
        fun of(key: String): KeyCurveChannel? = when {
            key == "volume" -> Volume
            key == "pan" -> Pan
            key.startsWith("t:") -> CurveParam.entries.firstOrNull { it.name == key.removePrefix("t:") }?.let(::Transform)
            key.startsWith("fx:") -> Effect(key.removePrefix("fx:"))
            else -> null
        }
    }
}

/** A keyframe as a curve sees it: where (frames into the clip), the value there and how the motion meets it. */
data class CurveKey(val frame: Int, val value: Float, val ease: KeyEase)

/**
 * One curve: its keys (sorted), the value it holds without keys ([base]), its range and how many decimals its values
 * carry (volumes in tenths of a dB, the rest whole). Between two keys the value follows [easedProgress] — a cubic in the
 * value, linear in time — so each side of a key is a Bézier segment whose control points ([handleOut], [handleIn]) sit a
 * third of the way along in time; dragging one sets that side's slope.
 */
data class KeyCurve(
    val channel: KeyCurveChannel,
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val decimals: Int,
    val keys: List<CurveKey>,
    val base: Float,
) {
    val animated: Boolean get() = keys.size > 1 && keys.any { abs(it.value - keys.first().value) > 1e-4f }

    fun valueAt(frame: Float): Float {
        if (keys.isEmpty()) return base
        if (frame <= keys.first().frame) return keys.first().value
        if (frame >= keys.last().frame) return keys.last().value
        val i = keys.indexOfLast { it.frame <= frame }
        val a = keys[i]
        val b = keys[i + 1]
        val t = (frame - a.frame) / (b.frame - a.frame)
        return a.value + (b.value - a.value) * easedProgress(t, a.ease.slopeOut, b.ease.slopeIn)
    }

    /** Key [index]'s leaving control point (frame, value), or null for the last key. */
    fun handleOut(index: Int): Pair<Float, Float>? {
        val a = keys.getOrNull(index) ?: return null
        val b = keys.getOrNull(index + 1) ?: return null
        return a.frame + (b.frame - a.frame) / 3f to a.value + a.ease.slopeOut * (b.value - a.value) / 3f
    }

    /** Key [index]'s arriving control point (frame, value), or null for the first key. */
    fun handleIn(index: Int): Pair<Float, Float>? {
        val b = keys.getOrNull(index) ?: return null
        val a = keys.getOrNull(index - 1) ?: return null
        return b.frame - (b.frame - a.frame) / 3f to b.value - b.ease.slopeIn * (b.value - a.value) / 3f
    }

    /** [value] rounded the way this curve stores values. */
    fun rounded(value: Float): Float = if (decimals == 0) value.roundToInt().toFloat() else (value * 10).roundToInt() / 10f

    /** A value as the editor shows it: 120%, −3.5 dB, 45°. */
    fun format(value: Float): String {
        val v = rounded(value)
        val text = if (decimals == 0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
        return if (unit.isEmpty()) text else if (unit == "dB") "$text dB" else "$text$unit"
    }

    companion object {
        /**
         * The slope a control point dragged to [handleValue] gives the side of key [from] that faces [to]: the Bézier
         * point a third of the way along is from + slope · (to − from) / 3, so slope = 3 · (handle − from) / (to − from),
         * kept within 0 (an ease) and [KeyEase.MAX_SLOPE] (as steep as the curve goes without overshooting). A flat
         * segment has no slope to set: null.
         */
        fun slopeFor(from: Float, to: Float, handleValue: Float): Float? {
            if (abs(to - from) < 1e-6f) return null
            return (3f * (handleValue - from) / (to - from)).coerceIn(0f, KeyEase.MAX_SLOPE)
        }
    }
}

/** Ease presets for a key in the curve editor (Resolve's keyframe interpolation menu). */
enum class CurveEase(val label: String, val ease: KeyEase) {
    LINEAR("线性", KeyEase(1f, 1f)),
    EASE_IN("缓入", KeyEase(0f, 1f)),
    EASE_OUT("缓出", KeyEase(1f, 0f)),
    EASE_BOTH("缓入缓出", KeyEase(0f, 0f)),
    ;

    companion object {
        /** The preset [ease] matches, or null for slopes of its own (set by dragging a control point). */
        fun of(ease: KeyEase): CurveEase? = entries.firstOrNull { it.ease == ease }
    }
}
