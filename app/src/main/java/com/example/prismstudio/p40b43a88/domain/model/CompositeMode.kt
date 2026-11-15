package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Resolve's Composite Mode: how a picture clip combines with the picture of the tracks beneath it, on straight colours
 * 0…1 ([blend], the W3C compositing formulas — per channel for most modes, on the whole colour for [HUE], [SATURATION],
 * [COLOR] and [LUMINOSITY]); the clip's alpha (opacity, crop, the picture's edges) then mixes that over what is beneath
 * ([over]). Over nothing (the bottom picture) a clip shows as [NORMAL]. The render's shader mirrors these formulas mode
 * by mode, by [ordinal]: keep the order and add at the end (projects store names); the menu shows [GROUPS].
 */
enum class CompositeMode(val label: String) {
    NORMAL("正常"),
    DARKEN("变暗"),
    MULTIPLY("正片叠底"),
    LIGHTEN("变亮"),
    SCREEN("滤色"),
    ADD("相加"),
    OVERLAY("叠加"),
    SOFT_LIGHT("柔光"),
    DIFFERENCE("差值"),
    COLOR_DODGE("颜色减淡"),
    COLOR_BURN("颜色加深"),
    HARD_LIGHT("强光"),
    EXCLUSION("排除"),
    HUE("色相"),
    SATURATION("饱和度"),
    COLOR("颜色"),
    LUMINOSITY("明度"),
    ;

    /** Whether the mode works channel by channel ([blend] of one channel applies). */
    val separable: Boolean get() = this !in COMPONENT

    /** One channel of a [separable] mode: [b] the picture beneath, [s] the clip's own. */
    fun blend(b: Float, s: Float): Float = when (this) {
        NORMAL -> s
        DARKEN -> min(b, s)
        MULTIPLY -> b * s
        LIGHTEN -> max(b, s)
        SCREEN -> screen(b, s)
        ADD -> min(1f, b + s)
        OVERLAY -> hardLight(s, b)
        SOFT_LIGHT -> if (s < 0.5f) b - (1f - 2f * s) * b * (1f - b) else b + (2f * s - 1f) * (softLightD(b) - b)
        DIFFERENCE -> abs(b - s)
        COLOR_DODGE -> if (b <= 0f) 0f else if (s >= 1f) 1f else min(1f, b / (1f - s))
        COLOR_BURN -> if (b >= 1f) 1f else if (s <= 0f) 0f else 1f - min(1f, (1f - b) / s)
        HARD_LIGHT -> hardLight(b, s)
        EXCLUSION -> b + s - 2f * b * s
        HUE, SATURATION, COLOR, LUMINOSITY -> error("$name blends whole colours")
    }

    /** The mode's colour for RGB [b] beneath and [s] the clip's own. */
    fun blend(b: FloatArray, s: FloatArray): FloatArray = when (this) {
        HUE -> setLum(setSat(s, sat(b)), lum(b))
        SATURATION -> setLum(setSat(b, sat(s)), lum(b))
        COLOR -> setLum(s, lum(b))
        LUMINOSITY -> setLum(b, lum(s))
        else -> FloatArray(3) { blend(b[it], s[it]) }
    }

    /** One channel of a [separable] mode's clip ([s], alpha [a]) composited over [b]. */
    fun over(b: Float, s: Float, a: Float): Float = b + (blend(b, s) - b) * a

    /** The clip's RGB [s] at alpha [a] composited over [b]. */
    fun over(b: FloatArray, s: FloatArray, a: Float): FloatArray {
        val mixed = blend(b, s)
        return FloatArray(3) { b[it] + (mixed[it] - b[it]) * a }
    }

    companion object {
        /** The menu: Photoshop's (and Resolve's) groups — normal, darken, lighten, contrast, inversion, component. */
        val GROUPS: List<List<CompositeMode>> = listOf(
            listOf(NORMAL),
            listOf(DARKEN, MULTIPLY, COLOR_BURN),
            listOf(LIGHTEN, SCREEN, COLOR_DODGE, ADD),
            listOf(OVERLAY, SOFT_LIGHT, HARD_LIGHT),
            listOf(DIFFERENCE, EXCLUSION),
            listOf(HUE, SATURATION, COLOR, LUMINOSITY),
        )

        private val COMPONENT = setOf(HUE, SATURATION, COLOR, LUMINOSITY)
    }
}

private fun screen(b: Float, s: Float) = b + s - b * s

private fun hardLight(b: Float, s: Float) = if (s <= 0.5f) 2f * b * s else screen(b, 2f * s - 1f)

private fun softLightD(b: Float): Float = if (b <= 0.25f) ((16f * b - 12f) * b + 4f) * b else sqrt(b)

/** W3C's luminosity of a colour. */
private fun lum(c: FloatArray) = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]

/** A colour pulled back inside 0…1 toward its own luminosity. */
private fun clipColor(c: FloatArray): FloatArray {
    val l = lum(c)
    val n = c.min()
    val x = c.max()
    var out = c
    if (n < 0f) out = FloatArray(3) { l + (out[it] - l) * l / max(l - n, EPSILON) }
    if (x > 1f) out = FloatArray(3) { l + (out[it] - l) * (1f - l) / max(x - l, EPSILON) }
    return out
}

private fun setLum(c: FloatArray, l: Float): FloatArray {
    val d = l - lum(c)
    return clipColor(FloatArray(3) { c[it] + d })
}

private fun sat(c: FloatArray) = c.max() - c.min()

/**
 * [c] with saturation [s]: its largest channel at [s], its smallest at 0, the middle one in proportion. A colour less
 * than a code value from grey counts as grey (W3C tests mx > mn, which turns a decoded grey's float noise into a
 * saturated colour of any hue).
 */
private fun setSat(c: FloatArray, s: Float): FloatArray {
    val mx = c.max()
    val mn = c.min()
    return if (mx - mn > GREY_SPREAD) FloatArray(3) { (c[it] - mn) * s / (mx - mn) } else FloatArray(3)
}

private const val EPSILON = 1e-6f

/** How far apart a colour's channels may lie and still count as grey: one 8-bit code value. */
private const val GREY_SPREAD = 1f / 255f
