package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's Mid/Detail as the shader draws it (ClipLookEffect's `detailed`): the local mean over a 5 × 5 grid of taps
 * [STEP_LINES] lines of a 1080-line frame apart, binomially weighted ([WEIGHTS] each way — a plain average turns fine
 * regular texture into its negative when softening), and the pixel's difference from it weighted to the midtones
 * (4 Y (1 − Y), Rec.709 luma) — added [GAIN] × amount (positive: texture stands out) or faded towards the mean
 * (negative: smoother skin and skies). Edges and texture at that scale move; flat areas and pure black or white do not.
 */
object MidDetailKernel {
    /** Tap spacing, in lines of a 1080-line frame. */
    const val STEP_LINES = 3f

    /** How strongly +100 lifts the local difference. */
    const val GAIN = 1.5f

    /** The taps' weights along each axis (their outer product sums to 256). */
    private val WEIGHTS = floatArrayOf(1f, 4f, 6f, 4f, 1f)

    /** What the shader is given: the amount −1…1 (0: off). */
    fun uniform(midDetail: Float): Float = (midDetail / 100f).coerceIn(-1f, 1f)

    /** [rgb] (interleaved, 0…1, [width] × [height]) through Mid/Detail [midDetail], taps [stepPx] pixels apart. */
    fun filter(rgb: FloatArray, width: Int, height: Int, midDetail: Float, stepPx: Int): FloatArray {
        val amount = uniform(midDetail)
        if (amount == 0f) return rgb.copyOf()
        val out = FloatArray(rgb.size)
        for (y in 0 until height) for (x in 0 until width) {
            val c = (y * width + x) * 3
            var mr = 0f; var mg = 0f; var mb = 0f
            for (j in -2..2) for (i in -2..2) {
                val n = ((y + j * stepPx).coerceIn(0, height - 1) * width + (x + i * stepPx).coerceIn(0, width - 1)) * 3
                val k = WEIGHTS[i + 2] * WEIGHTS[j + 2]
                mr += k * rgb[n]; mg += k * rgb[n + 1]; mb += k * rgb[n + 2]
            }
            mr /= 256f; mg /= 256f; mb /= 256f
            val luma = (0.2126f * rgb[c] + 0.7152f * rgb[c + 1] + 0.0722f * rgb[c + 2]).coerceIn(0f, 1f)
            val w = 4f * luma * (1f - luma)
            val mean = floatArrayOf(mr, mg, mb)
            for (k in 0..2) {
                val v = rgb[c + k]
                out[c + k] = if (amount > 0f) v + (v - mean[k]) * amount * GAIN * w else v + (mean[k] - v) * (-amount) * w
            }
        }
        return out
    }
}
