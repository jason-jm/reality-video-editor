package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Resolve's fade handles. A clip fades in from nothing over its first [Clip.fadeInSeconds] and out to nothing over its
 * last [Clip.fadeOutSeconds]: a sound in level, a picture in opacity — to black, or to what the tracks beneath show. Each
 * fade is linear in time unless its curve ([Clip.fadeInCurve], [Clip.fadeOutCurve]; Resolve's fade curve dot) bends it,
 * see [shape]. A linked picture and sound fade apart, each by its own handles.
 */
object ClipFades {

    /** The longest fade: [MAX_SECONDS], and never more than half the clip, so a fade in and a fade out at most meet. */
    fun max(clip: Clip, fps: Int): Float = minOf(MAX_SECONDS, clip.durationFrames / 2f / fps.coerceAtLeast(1))

    /**
     * How much of the clip shows [frame] frames into it (a frame's time, counted from the clip's start) for a clip
     * [lengthFrames] long fading in over [inFrames] and out over [outFrames]: nothing at its very start and end, all of it
     * between the fades. Outside the clip (the frames a transition shows past its cuts) the fades hold at nothing.
     */
    fun factor(frame: Float, lengthFrames: Float, inFrames: Float, outFrames: Float, inCurve: Float = 0f, outCurve: Float = 0f): Float {
        var f = 1f
        if (inFrames > 0f) f = minOf(f, shape(frame / inFrames, inCurve))
        if (outFrames > 0f) f = minOf(f, shape((lengthFrames - frame) / outFrames, outCurve))
        return f.coerceIn(0f, 1f)
    }

    /** [clip]'s fade [frame] frames into it, at [fps]. */
    fun factor(clip: Clip, frame: Float, fps: Int): Float =
        factor(frame, clip.durationFrames.toFloat(), clip.fadeInSeconds * fps, clip.fadeOutSeconds * fps, clip.fadeInCurve, clip.fadeOutCurve)

    /**
     * Resolve's fade curve: how far a fade has come [t] (0…1) of the way through it, counted from its silent end — a fade
     * in from the clip's start, a fade out back from its end — when bent by [curve] (−1…1). A quadratic Bézier from (0, 0)
     * to (1, 1) whose control point slides along the other diagonal, from (1, 0) through the middle (0: a straight line,
     * linear in time) to (0, 1). Above 0 it bows up: a fade in comes up sooner, a fade out holds on longer and drops at
     * the end; below 0 it bows down. The curve's middle, the dot the timeline shows, sits at [dot]; and
     * shape(t, −c) = 1 − shape(1 − t, c).
     */
    fun shape(t: Float, curve: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val c = curve.coerceIn(-1f, 1f)
        if (c == 0f) return t
        // The Bézier's x(s) = c·s² + (1 − c)·s solved for s, in the form that also holds at c = 1 (where x = s²).
        val b = 1f - c
        val s = 2f * t / (b + sqrt(b * b + 4f * c * t))
        return (s + c * s * (1f - s)).coerceIn(0f, 1f)
    }

    /**
     * Where a fade bent by [curve] has the middle of its curve, the dot that bends it: a share of the fade's length from
     * its silent end, and of the way up to full.
     */
    fun dot(curve: Float): Pair<Float, Float> {
        val c = curve.coerceIn(-1f, 1f)
        return (0.5f - c / 4f) to (0.5f + c / 4f)
    }

    /** [curve] as the inspector and the timeline read it: 线性, 上弯 40%, 下弯 40%. */
    fun curveLabel(curve: Float): String {
        val percent = (curve.coerceIn(-1f, 1f) * 100).roundToInt()
        return when {
            percent == 0 -> "线性"
            percent > 0 -> "上弯 $percent%"
            else -> "下弯 ${-percent}%"
        }
    }

    /** Whether [clip] has fade handles: sound, pictures and titles — not subtitles, which cut in and out with the dialogue. */
    fun takes(clip: Clip): Boolean = clip.kind != ClipKind.TITLE || clip.title?.template != TitleTemplate.SUBTITLE

    /** Whether [clip] fades at all. */
    fun fades(clip: Clip): Boolean = takes(clip) && (clip.fadeInSeconds > 0f || clip.fadeOutSeconds > 0f)

    const val MAX_SECONDS = 5f
}
