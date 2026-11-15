package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** How a source's code values turn into light: standard dynamic range, or one of the two HDR transfers of Rec.2100. */
enum class ColorTransfer(val label: String) {
    SDR("SDR"),
    HLG("HLG"),
    PQ("PQ"),
}

/** The primaries (and matrix) a source is coded in. */
enum class ColorPrimaries(val label: String) {
    BT601("Rec.601"),
    BT709("Rec.709"),
    BT2020("Rec.2020"),
}

/**
 * What a file says about its colour (read when it is imported): its primaries (null when untagged), its transfer and its
 * bit depth. HLG and PQ sources are HDR.
 */
data class SourceColor(val primaries: ColorPrimaries? = null, val transfer: ColorTransfer = ColorTransfer.SDR, val bitDepth: Int = 8) {
    val isHdr: Boolean get() = transfer != ColorTransfer.SDR

    /** Rec.2100 HLG · 10 位, Rec.2100 PQ（HDR10）· 10 位, Rec.709, or 未标记 for an SDR file without tags. */
    val label: String
        get() = when (transfer) {
            ColorTransfer.HLG -> "Rec.2100 HLG · $bitDepth 位"
            ColorTransfer.PQ -> "Rec.2100 PQ（HDR10）· $bitDepth 位"
            ColorTransfer.SDR -> primaries?.label?.let { if (bitDepth > 8) "$it · $bitDepth 位" else it } ?: "未标记（按 Rec.709）"
        }

    companion object {
        val UNKNOWN = SourceColor()

        /** MediaFormat's KEY_COLOR_STANDARD / KEY_COLOR_TRANSFER values (and its HEVC / VP9 / AV1 10-bit profiles). */
        fun fromMediaFormat(standard: Int?, transfer: Int?, tenBit: Boolean): SourceColor = SourceColor(
            primaries = when (standard) {
                1 -> ColorPrimaries.BT709
                2, 4 -> ColorPrimaries.BT601
                6 -> ColorPrimaries.BT2020
                else -> null
            },
            transfer = when (transfer) {
                7 -> ColorTransfer.HLG
                6 -> ColorTransfer.PQ
                else -> ColorTransfer.SDR
            },
            bitDepth = if (tenBit) 10 else 8,
        )
    }
}

/** Resolve's Input Color Space of a clip's media: from the file's tags, or set by hand (for a file tagged wrong or not at all). */
enum class InputColorSpace(val label: String, val transfer: ColorTransfer?) {
    AUTO("自动", null),
    REC709("Rec.709", ColorTransfer.SDR),
    HLG("HLG", ColorTransfer.HLG),
    PQ("PQ", ColorTransfer.PQ),
}

/** How HDR light is fitted into the Rec.709 timeline (Resolve's HDR tone mapping methods). */
enum class ToneMapping(val label: String, val note: String) {
    NONE("不映射", "参考白（203 nits）对齐 SDR 白，更亮的部分直接裁切"),
    SIMPLE("简单", "红绿蓝各自压缩高光：最稳妥，很亮的颜色会自然褪白"),
    LUMINANCE("亮度映射", "只压缩亮度、保持比例：颜色更饱满，过亮的颜色会被裁切"),
    HUE("保持色相", "压缩亮度后，超出的颜色降低饱和度：保持亮度和色相"),
}

/**
 * The project's colour management (Resolve's Project Settings › Color Management): the timeline works in Rec.709
 * (gamma 2.4) SDR, HDR sources are tone mapped into it by [toneMapping], a PQ source taken as mastered up to [pqPeakNits].
 */
data class ColorManagement(val toneMapping: ToneMapping = ToneMapping.LUMINANCE, val pqPeakNits: Int = 1000) {
    companion object {
        val DEFAULT = ColorManagement()
        val PQ_PEAKS = listOf(1000, 2000, 4000)
        const val TIMELINE_LABEL = "Rec.709 Gamma 2.4（SDR）"
    }
}

/** The transfer a clip of [this] media plays with: its own tags, unless [MediaAsset.inputColor] overrides them. */
val MediaAsset.effectiveTransfer: ColorTransfer get() = inputColor.transfer ?: color.transfer

/**
 * Whether [this] media is HDR by the input colour override alone: its file says SDR (tags a messenger stripped, a camera
 * that wrote none), so every player and frame reader takes its code values as SDR — its proxies and freeze frames too —
 * and the timeline's tone map must be applied wherever it is shown, the monitor and the scopes as well as the render.
 */
val MediaAsset.hdrByOverride: Boolean get() = color.transfer == ColorTransfer.SDR && effectiveTransfer != ColorTransfer.SDR

/**
 * [this] frame — a [transfer] source's code values read as SDR (ARGB) — tone mapped into the Rec.709 timeline by [color],
 * pixel for pixel as the render's shader does ([HdrToneMap.map]); an SDR source as it is.
 */
fun FramePixels.toneMapped(transfer: ColorTransfer, color: ColorManagement): FramePixels {
    if (transfer == ColorTransfer.SDR) return this
    val rgb = FloatArray(3)
    val out = IntArray(pixels.size)
    for (i in pixels.indices) {
        val p = pixels[i]
        rgb[0] = (p shr 16 and 0xFF) / 255f
        rgb[1] = (p shr 8 and 0xFF) / 255f
        rgb[2] = (p and 0xFF) / 255f
        val m = HdrToneMap.map(rgb, transfer, color.toneMapping, color.pqPeakNits)
        out[i] = (p and 0xFF000000.toInt()) or (byte(m[0]) shl 16) or (byte(m[1]) shl 8) or byte(m[2])
    }
    return FramePixels(width, height, out)
}

private fun byte(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

/**
 * HDR → Rec.709 (gamma 2.4) tone mapping, the arithmetic the render's shader runs per pixel: the code value's light
 * (HLG: the inverse OETF and the OOTF of a 1000-nit display; PQ: the EOTF, in nits), relative to the reference white
 * of 203 nits (BT.2408), into Rec.709 primaries, through the tone curve as [ToneMapping] says, encoded for a gamma-2.4
 * display. The curve is linear (gain [GAIN]) up to [KNEE] times reference white, then a shoulder that meets white
 * exactly at the source's peak, with the same slope at the knee.
 */
object HdrToneMap {
    const val REFERENCE_WHITE_NITS = 203f
    const val HLG_PEAK_NITS = 1000f
    const val GAIN = 0.8f
    const val KNEE = 0.6f

    /** BT.2020 → BT.709 primaries, linear light (row-major). */
    val BT2020_TO_BT709 = floatArrayOf(
        1.660491f, -0.587641f, -0.072850f,
        -0.124550f, 1.132900f, -0.008349f,
        -0.018151f, -0.100579f, 1.118730f,
    )

    /** HLG's inverse OETF: a code value (0–1) to scene light (0–1). */
    fun hlgToScene(e: Float): Float {
        val v = e.coerceIn(0f, 1f)
        return if (v <= 0.5f) v * v / 3f else ((exp(((v - HLG_C) / HLG_A).toDouble()) + HLG_B) / 12.0).toFloat()
    }

    /** PQ's EOTF: a code value (0–1) to nits. */
    fun pqToNits(e: Float): Float {
        val p = e.coerceIn(0f, 1f).toDouble().pow(1.0 / PQ_M2)
        return (10000.0 * (max(p - PQ_C1, 0.0) / (PQ_C2 - PQ_C3 * p)).pow(1.0 / PQ_M1)).toFloat()
    }

    /** The shoulder's time constant for a source peaking at [peak] times reference white (solved once per render). */
    fun shoulderTau(peak: Float): Float {
        val yk = GAIN * KNEE
        val d = (peak - KNEE).coerceAtLeast(1e-3f)
        fun slope(t: Double) = (1 - yk) / (t * (1 - exp(-d / t)))
        if (slope(1e6) >= GAIN) return 1e6f
        var lo = 1e-4
        var hi = 1e6
        repeat(100) {
            val mid = (lo + hi) / 2
            if (slope(mid) > GAIN) lo = mid else hi = mid
        }
        return ((lo + hi) / 2).toFloat()
    }

    /** The tone curve: light relative to reference white ([x]) to SDR light (0–1), white at [peak] (see [shoulderTau]). */
    fun curve(x: Float, peak: Float, tau: Float): Float {
        if (x <= KNEE) return max(x, 0f) * GAIN
        if (x >= peak) return 1f
        val yk = GAIN * KNEE
        val norm = 1 - exp(-(peak - KNEE) / tau)
        return yk + (1 - yk) * (1 - exp(-(x - KNEE) / tau)) / norm
    }

    /**
     * One pixel: [rgb] the source's code values (0–1, Rec.2020 R′G′B′), [transfer] HLG or PQ; the Rec.709 gamma-2.4
     * code values out. [pqPeakNits] is a PQ source's mastering peak (HLG peaks at 1000 nits).
     */
    fun map(rgb: FloatArray, transfer: ColorTransfer, method: ToneMapping, pqPeakNits: Int): FloatArray {
        val nits = when (transfer) {
            ColorTransfer.SDR -> return rgb.copyOf()
            ColorTransfer.PQ -> FloatArray(3) { pqToNits(rgb[it]) }
            ColorTransfer.HLG -> {
                val scene = FloatArray(3) { hlgToScene(rgb[it]) }
                val ys = 0.2627f * scene[0] + 0.6780f * scene[1] + 0.0593f * scene[2]
                val ootf = HLG_PEAK_NITS * ys.coerceAtLeast(1e-6f).pow(HLG_GAMMA - 1f)
                FloatArray(3) { scene[it] * ootf }
            }
        }
        val rel = FloatArray(3) { nits[it] / REFERENCE_WHITE_NITS }
        val m = BT2020_TO_BT709
        val lin = FloatArray(3) { i -> m[i * 3] * rel[0] + m[i * 3 + 1] * rel[1] + m[i * 3 + 2] * rel[2] }
        val peak = (if (transfer == ColorTransfer.PQ) pqPeakNits.toFloat() else HLG_PEAK_NITS) / REFERENCE_WHITE_NITS
        val tau = shoulderTau(peak)
        val out = when (method) {
            ToneMapping.NONE -> FloatArray(3) { lin[it].coerceIn(0f, 1f) }
            ToneMapping.SIMPLE -> FloatArray(3) { curve(lin[it], peak, tau) }
            ToneMapping.LUMINANCE, ToneMapping.HUE -> {
                val y = 0.2126f * lin[0] + 0.7152f * lin[1] + 0.0722f * lin[2]
                val mapped = curve(y, peak, tau)
                val ratio = if (y > 1e-6f) mapped / y else GAIN
                val scaled = FloatArray(3) { max(lin[it] * ratio, 0f) }
                if (method == ToneMapping.HUE) {
                    // Over white: toward the mapped grey just enough to bring the brightest channel to 1 (hue and
                    // brightness kept, saturation given up).
                    val top = max(scaled[0], max(scaled[1], scaled[2]))
                    if (top > 1f && top > mapped) {
                        val k = ((1f - mapped) / (top - mapped)).coerceIn(0f, 1f)
                        for (i in 0..2) scaled[i] = mapped + (scaled[i] - mapped) * k
                    }
                }
                FloatArray(3) { min(scaled[it], 1f) }
            }
        }
        return FloatArray(3) { out[it].coerceIn(0f, 1f).pow(1f / DISPLAY_GAMMA) }
    }

    const val HLG_A = 0.17883277f
    const val HLG_B = 0.28466892
    const val HLG_C = 0.55991073f
    const val HLG_GAMMA = 1.2f
    const val DISPLAY_GAMMA = 2.4f
    private const val PQ_M1 = 0.1593017578125
    private const val PQ_M2 = 78.84375
    private const val PQ_C1 = 0.8359375
    private const val PQ_C2 = 18.8515625
    private const val PQ_C3 = 18.6875
}
