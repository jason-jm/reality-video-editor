package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Resolve's keyer (the 3D Keyer / Chroma Key of a green or blue screen): the parts of a picture near one colour turn
 * clear, so the tracks beneath show through. It keys the source as it comes, before the grade (whose colours then work on
 * what is left), by chroma alone — how far a pixel's BT.709 Cb Cr lies from the key colour's — so a screen lit unevenly
 * keys as one: within [similarity] of the key the picture is gone, over the next [smoothness] it comes back, and over
 * [spill] past that the screen's tint on the subject's edges is taken out by desaturating it. Amounts 0…100 ([ChromaKeyKernel]).
 */
data class ChromaKey(
    /** The screen's colour, 0xRRGGBB. */
    val color: Int = GREEN,
    val similarity: Float = 40f,
    val smoothness: Float = 30f,
    val spill: Float = 30f,
    /**
     * Resolve's matte finesse, 0…100: [cleanBlack] clears what is nearly keyed out (a noisy screen's speckle), [cleanWhite]
     * makes what is nearly kept solid (a subject the screen tinted), [blur] softens the matte's edge over its neighbours.
     */
    val cleanBlack: Float = 0f,
    val cleanWhite: Float = 0f,
    val blur: Float = 0f,
) {
    /** Which preset the colour is, or null for a picked one. */
    val preset: KeyScreen? get() = KeyScreen.entries.firstOrNull { it.color == color }

    /** The colour as the inspector names it: 绿幕, 蓝幕 or its hex. */
    val colorLabel: String get() = preset?.label ?: "#%06X".format(color and 0xFFFFFF)

    companion object {
        /** Chroma-key green and blue (the paints' sRGB). */
        const val GREEN = 0x00B140
        const val BLUE = 0x0047BB
    }
}

/** The two screens a key starts from (Resolve's keyers start from a pick; these are the paints). */
enum class KeyScreen(val label: String, val color: Int) {
    GREEN("绿幕", ChromaKey.GREEN),
    BLUE("蓝幕", ChromaKey.BLUE),
}

/** The keyer's amounts as the inspector's sliders read and write them (0…100). */
enum class KeyControl(val label: String) {
    SIMILARITY("相似度"),
    SMOOTHNESS("平滑"),
    SPILL("溢色抑制"),
    CLEAN_BLACK("去黑"),
    CLEAN_WHITE("去白"),
    BLUR("遮罩模糊");

    /** The matte finesse's controls (Resolve's), below the key's own. */
    val finesse: Boolean get() = this == CLEAN_BLACK || this == CLEAN_WHITE || this == BLUR

    fun read(key: ChromaKey): Float = when (this) {
        SIMILARITY -> key.similarity
        SMOOTHNESS -> key.smoothness
        SPILL -> key.spill
        CLEAN_BLACK -> key.cleanBlack
        CLEAN_WHITE -> key.cleanWhite
        BLUR -> key.blur
    }

    fun write(key: ChromaKey, value: Float): ChromaKey {
        val v = value.coerceIn(0f, 100f)
        return when (this) {
            SIMILARITY -> key.copy(similarity = v)
            SMOOTHNESS -> key.copy(smoothness = v)
            SPILL -> key.copy(spill = v)
            CLEAN_BLACK -> key.copy(cleanBlack = v)
            CLEAN_WHITE -> key.copy(cleanWhite = v)
            BLUR -> key.copy(blur = v)
        }
    }
}

/**
 * The key's matte, as the shader computes it (OBS's chroma key on BT.709 Cb Cr): with d the distance from the key's Cb Cr
 * less the similarity, alpha = (d / smoothness)^1.5 (a hard edge without smoothness), and the colour fades to its own
 * luma by 1 − (d / spill)^1.5 (none without spill), each clamped to 0…1. 100 on the sliders is [MAX_SIMILARITY],
 * [MAX_SMOOTHNESS] and [MAX_SPILL] of Cb Cr distance (pure green lies 0.59 from grey, chroma-key green 0.36).
 */
object ChromaKeyKernel {
    const val MAX_SIMILARITY = 0.5f
    const val MAX_SMOOTHNESS = 0.25f
    const val MAX_SPILL = 0.4f
    /** 100 on 去黑 / 去白 clears the matte's lowest / highest half; 100 on 遮罩模糊 is this radius in pixels of a 1080-line frame. */
    const val MAX_CLEAN = 0.5f
    const val MAX_BLUR_PX = 6f

    /** BT.709 luma of r, g, b (0…1). */
    fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    /** BT.709 Cb and Cr (−0.5…0.5) of r, g, b. */
    fun cbcr(r: Float, g: Float, b: Float): Pair<Float, Float> {
        val y = luma(r, g, b)
        return (b - y) / 1.8556f to (r - y) / 1.5748f
    }

    /**
     * The shader's uniforms: [key Cb, key Cr, similarity, smoothness, spill] in Cb Cr distance, then the matte finesse
     * [clean black, clean white] as shares of the matte and the blur radius in pixels of a 1080-line frame.
     */
    fun uniforms(key: ChromaKey): FloatArray {
        val (cb, cr) = cbcr(channel(key.color, 16), channel(key.color, 8), channel(key.color, 0))
        return floatArrayOf(
            cb, cr, key.similarity / 100f * MAX_SIMILARITY, key.smoothness / 100f * MAX_SMOOTHNESS, key.spill / 100f * MAX_SPILL,
            key.cleanBlack / 100f * MAX_CLEAN, key.cleanWhite / 100f * MAX_CLEAN, key.blur / 100f * MAX_BLUR_PX,
        )
    }

    /**
     * Resolve's clean black / clean white on a matte value [alpha]: what lies under the clean black goes clear, what lies
     * over 1 − clean white goes solid, the rest is stretched between (a pixel's own value; the shader blurs first).
     */
    fun finesse(alpha: Float, u: FloatArray): Float {
        val black = u.getOrElse(5) { 0f }
        val white = u.getOrElse(6) { 0f }
        if (black <= 0f && white <= 0f) return alpha
        val span = (1f - white - black).coerceAtLeast(1e-4f)
        return ((alpha - black) / span).coerceIn(0f, 1f)
    }

    /**
     * [r], [g], [b] (0…1) keyed by [u] ([uniforms]): the colour with the spill taken out, and its alpha — as
     * floatArrayOf(r, g, b, alpha).
     */
    fun key(r: Float, g: Float, b: Float, u: FloatArray): FloatArray {
        val (cb, cr) = cbcr(r, g, b)
        val dcb = cb - u[0]
        val dcr = cr - u[1]
        val d = sqrt(dcb * dcb + dcr * dcr) - u[2]
        val alpha = finesse(if (u[3] > 0f) (d / u[3]).coerceIn(0f, 1f).pow(1.5f) else if (d >= 0f) 1f else 0f, u)
        if (u[4] <= 0f) return floatArrayOf(r, g, b, alpha)
        val keep = (d / u[4]).coerceIn(0f, 1f).pow(1.5f)
        val y = luma(r, g, b)
        return floatArrayOf(y + (r - y) * keep, y + (g - y) * keep, y + (b - y) * keep, alpha)
    }

    private fun channel(rgb: Int, shift: Int): Float = ((rgb shr shift) and 0xFF) / 255f
}
