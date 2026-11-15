package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.log10

/** A small decoded frame as ARGB pixels (row-major), used for scopes and look previews. */
class FramePixels(val width: Int, val height: Int, val pixels: IntArray)

/** Peak amplitude (0..1) for every [bucketMs] of a source file's audio. */
class Waveform(val bucketMs: Int, val peaks: FloatArray) {
    val durationMs: Long get() = peaks.size.toLong() * bucketMs

    /** Highest peak between [fromMs] and [toMs] of the source. */
    fun peak(fromMs: Long, toMs: Long): Float {
        if (peaks.isEmpty()) return 0f
        val a = (fromMs / bucketMs).toInt().coerceIn(0, peaks.lastIndex)
        val b = (toMs / bucketMs).toInt().coerceIn(a, peaks.lastIndex)
        var max = 0f
        for (i in a..b) if (peaks[i] > max) max = peaks[i]
        return max
    }

    companion object {
        /** Quietest level a waveform still shows; everything below draws as silence. */
        const val DISPLAY_FLOOR_DB = -48f

        /** Drawn height (0..1) of [peak] after [gainDb], on a dB scale from [DISPLAY_FLOOR_DB] to 0 dBFS. */
        fun displayLevel(peak: Float, gainDb: Float = 0f): Float {
            if (peak <= 0f) return 0f
            val db = 20f * log10(peak) + gainDb
            return ((db - DISPLAY_FLOOR_DB) / -DISPLAY_FLOOR_DB).coerceIn(0f, 1f)
        }
    }
}
