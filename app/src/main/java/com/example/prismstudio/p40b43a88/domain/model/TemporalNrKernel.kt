package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs

/**
 * Resolve's Temporal NR as the shader draws it (TemporalNrEffect): each frame is blended towards the previous frame's
 * result — a recursive average over several frames, so noise that differs frame to frame cancels — by a weight that falls
 * to nothing where the picture has really changed. The change is judged on the local mean of the luma difference (3 × 3
 * pixels, signed), in which grain averages away and movement does not: below half the [threshold] the blend is full, above
 * it none, smoothly between. At 100 the blend is [MAX_BLEND] (still areas settle to about a third of their noise) and the
 * threshold [MAX_THRESHOLD]; both scale with the strength. A jump in time (a seek, a cut into the clip) starts afresh.
 */
object TemporalNrKernel {
    /** The weight on the previous result at full strength, where nothing moves. */
    const val MAX_BLEND = 0.8f

    /** Where movement stops the blend (luma 0…1, local mean difference), at full strength; half of it at none. */
    const val MAX_THRESHOLD = 0.06f
    private const val MIN_THRESHOLD = 0.012f

    /** What the shader is given for [strength] 0…100: the blend and the threshold; null when off. */
    fun uniforms(strength: Int): FloatArray? {
        if (strength <= 0) return null
        val s = (strength / 100f).coerceIn(0f, 1f)
        return floatArrayOf(MAX_BLEND * s, MIN_THRESHOLD + (MAX_THRESHOLD - MIN_THRESHOLD) * s)
    }

    /** The weight on the previous result where the local mean luma difference is [difference]. */
    fun weight(blend: Float, threshold: Float, difference: Float): Float {
        val d = abs(difference)
        val lo = threshold * 0.5f
        val t = ((d - lo) / (threshold - lo)).coerceIn(0f, 1f)
        return blend * (1f - t * t * (3f - 2f * t))
    }

    /**
     * One frame through the filter: [current] and the [previous] result (luma planes, 0…1, [width] × [height]) give this
     * frame's result. With [previous] null (the first frame, or after a jump) the frame passes unchanged.
     */
    fun step(current: FloatArray, previous: FloatArray?, width: Int, height: Int, strength: Int): FloatArray {
        val u = uniforms(strength) ?: return current.copyOf()
        if (previous == null) return current.copyOf()
        val out = FloatArray(current.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (j in -1..1) for (i in -1..1) {
                val n = (y + j).coerceIn(0, height - 1) * width + (x + i).coerceIn(0, width - 1)
                sum += current[n] - previous[n]
            }
            val c = y * width + x
            val w = weight(u[0], u[1], sum / 9f)
            out[c] = current[c] + (previous[c] - current[c]) * w
        }
        return out
    }
}
