package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Resolve's Spatial NR (the Color page's Motion Effects palette): noise smoothed within each frame while edges stay, before
 * node 1's correction. Luma and chroma are smoothed apart, each over differences up to its threshold (0…100; 0 leaves that
 * part alone): a difference well within it counts as noise and is averaged away, one well past it is detail and stays.
 * [radius] sets how far around each pixel it looks; [amount] (0…100 %) how much of the smoothed picture shows. It belongs to
 * the grade, so copying a grade or grabbing a still carries it, but it is not keyframed and no LUT can hold it.
 */
data class SpatialNoiseReduction(
    val lumaThreshold: Float = 0f,
    val chromaThreshold: Float = 0f,
    val radius: NrRadius = NrRadius.MEDIUM,
    val amount: Float = 100f,
) {
    /** It changes the picture: some threshold and some amount. */
    val isActive: Boolean get() = amount > 0f && (lumaThreshold > 0f || chromaThreshold > 0f)

    companion object {
        val OFF = SpatialNoiseReduction()
    }
}

/** How far round each pixel the noise reduction looks: 1, 2 or 3 pixels of a 1080-line frame each way. */
enum class NrRadius(val label: String, val taps: Int) {
    SMALL("小", 1),
    MEDIUM("中", 2),
    LARGE("大", 3),
}

/** The noise reduction's sliders, as the Color page's 降噪 palette shows them. */
enum class SpatialNrControl(val label: String, val max: Float, val unit: String) {
    LUMA("亮度阈值", 100f, ""),
    CHROMA("色度阈值", 100f, ""),
    AMOUNT("强度", 100f, "%");

    fun read(nr: SpatialNoiseReduction): Float = when (this) {
        LUMA -> nr.lumaThreshold
        CHROMA -> nr.chromaThreshold
        AMOUNT -> nr.amount
    }

    /** [nr] with this control at [value], held to 0…[max] in whole steps. */
    fun write(nr: SpatialNoiseReduction, value: Float): SpatialNoiseReduction {
        val v = value.coerceIn(0f, max).roundToInt().toFloat()
        return when (this) {
            LUMA -> nr.copy(lumaThreshold = v)
            CHROMA -> nr.copy(chromaThreshold = v)
            AMOUNT -> nr.copy(amount = v)
        }
    }
}

/**
 * The filter the clip shader runs (ClipLookEffect's `denoised`), here for tests: a bilateral filter in BT.709 Y′CbCr over
 * (2 r + 1)² pixels, weighted by a Gaussian in distance (sigma 0.5 + r / 2 pixels) times a Gaussian in the luma difference
 * (for luma) or in the chroma difference (for chroma), whose sigmas grow with the thresholds; then [SpatialNoiseReduction.amount]
 * of it mixed over the picture. Edges clamp.
 */
object SpatialNrKernel {
    /** The luma difference (0…1) one sigma of the range Gaussian spans at threshold 100. */
    const val LUMA_SIGMA = 0.15f

    /** The same for chroma (Cb, Cr: −0.5…0.5). */
    const val CHROMA_SIGMA = 0.10f

    fun lumaSigma(nr: SpatialNoiseReduction): Float = nr.lumaThreshold / 100f * LUMA_SIGMA

    fun chromaSigma(nr: SpatialNoiseReduction): Float = nr.chromaThreshold / 100f * CHROMA_SIGMA

    fun spatialSigma(radius: Int): Float = 0.5f + 0.5f * radius

    /** What the shader is given: luma sigma, chroma sigma, radius, amount (0…1); null when it changes nothing. */
    fun uniforms(nr: SpatialNoiseReduction): FloatArray? =
        if (!nr.isActive) null else floatArrayOf(lumaSigma(nr), chromaSigma(nr), nr.radius.taps.toFloat(), nr.amount / 100f)

    /** [rgb] (interleaved, 0…1, [width] × [height]) through the noise reduction. */
    fun filter(rgb: FloatArray, width: Int, height: Int, nr: SpatialNoiseReduction): FloatArray {
        val u = uniforms(nr) ?: return rgb.copyOf()
        val (sl, sc, rf, amount) = u.toList()
        val r = rf.toInt()
        val invS = 1f / (2f * spatialSigma(r) * spatialSigma(r))
        val invL = if (sl > 0f) 1f / (2f * sl * sl) else 0f
        val invC = if (sc > 0f) 1f / (2f * sc * sc) else 0f
        val ycc = FloatArray(rgb.size)
        for (p in 0 until width * height) toYcc(rgb, p * 3, ycc)
        val out = FloatArray(rgb.size)
        val q = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val c = (y * width + x) * 3
            var sumL = 0f; var wL = 0f; var sumCb = 0f; var sumCr = 0f; var wC = 0f
            for (j in -r..r) for (i in -r..r) {
                val n = ((y + j).coerceIn(0, height - 1) * width + (x + i).coerceIn(0, width - 1)) * 3
                val s = (i * i + j * j) * invS
                val dl = ycc[n] - ycc[c]
                val dcb = ycc[n + 1] - ycc[c + 1]
                val dcr = ycc[n + 2] - ycc[c + 2]
                val wl = if (sl > 0f) exp(-s - dl * dl * invL) else if (i == 0 && j == 0) 1f else 0f
                val wc = if (sc > 0f) exp(-s - (dcb * dcb + dcr * dcr) * invC) else if (i == 0 && j == 0) 1f else 0f
                sumL += wl * ycc[n]; wL += wl
                sumCb += wc * ycc[n + 1]; sumCr += wc * ycc[n + 2]; wC += wc
            }
            q[0] = sumL / wL; q[1] = sumCb / wC; q[2] = sumCr / wC
            fromYcc(q, out, c)
            for (k in 0..2) out[c + k] = rgb[c + k] + (out[c + k] - rgb[c + k]) * amount
        }
        return out
    }

    private fun toYcc(rgb: FloatArray, o: Int, out: FloatArray) {
        val y = 0.2126f * rgb[o] + 0.7152f * rgb[o + 1] + 0.0722f * rgb[o + 2]
        out[o] = y
        out[o + 1] = (rgb[o + 2] - y) / 1.8556f
        out[o + 2] = (rgb[o] - y) / 1.5748f
    }

    private fun fromYcc(v: FloatArray, out: FloatArray, o: Int) {
        val r = v[0] + 1.5748f * v[2]
        val b = v[0] + 1.8556f * v[1]
        out[o] = r
        out[o + 1] = (v[0] - 0.2126f * r - 0.0722f * b) / 0.7152f
        out[o + 2] = b
    }
}
