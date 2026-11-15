package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A dragged point of the [ColorWarper]: spoke [spoke] (0 red … 5 magenta), ring [ring] (0 inner … 2 outer), moved [hue]° and [sat]. */
data class WarpMove(val spoke: Int, val ring: Int, val hue: Float, val sat: Float)

/**
 * Resolve's Color Warper (hue–saturation): a polar grid over the chroma plane — six spokes at the primaries and secondaries
 * (红 黄 绿 青 蓝 品) and three rings (a third, two thirds and full saturation) — whose points are dragged to move the colours
 * around them: round the wheel (hue, ±[MAX_HUE]°) and in or out (saturation, ±[MAX_SAT] of full). Between points the move
 * is interpolated, the centre stays put (greys keep grey) and every colour keeps its Rec.709 luma. Part of the node's
 * correction after its hue curves, so it lands in the grade's LUT.
 */
data class ColorWarper(val moves: List<WarpMove> = emptyList()) {

    /** No point is moved. */
    val isIdentity: Boolean get() = moves.all { it.hue == 0f && it.sat == 0f }

    fun move(spoke: Int, ring: Int): WarpMove? = moves.firstOrNull { it.spoke == spoke && it.ring == ring }

    /** Point ([spoke], [ring]) moved by [hue]° and [sat] (clamped); a point back home leaves the list. */
    fun with(spoke: Int, ring: Int, hue: Float, sat: Float): ColorWarper {
        val h = hue.coerceIn(-MAX_HUE, MAX_HUE)
        val s = sat.coerceIn(-MAX_SAT, MAX_SAT)
        val rest = moves.filterNot { it.spoke == spoke && it.ring == ring }
        val next = if (h == 0f && s == 0f) rest else rest + WarpMove(spoke, ring, h, s)
        return ColorWarper(next.sortedWith(compareBy({ it.spoke }, { it.ring })))
    }

    /** A fraction [t] of the way to [to]: every point's move in between. */
    fun lerp(to: ColorWarper, t: Float): ColorWarper {
        if (this == to) return this
        var out = ColorWarper()
        for (spoke in 0 until SPOKES) for (ring in 0 until RINGS) {
            val a = move(spoke, ring)
            val b = to.move(spoke, ring)
            if (a == null && b == null) continue
            val ah = a?.hue ?: 0f
            val asat = a?.sat ?: 0f
            out = out.with(spoke, ring, ah + ((b?.hue ?: 0f) - ah) * t, asat + ((b?.sat ?: 0f) - asat) * t)
        }
        return out
    }

    /** The move at chroma angle [degrees] and radius [radius] (1 = a primary): the grid's points interpolated, nothing at the centre. */
    fun displacement(degrees: Float, radius: Float): Pair<Float, Float> {
        val u = ((degrees % 360f) + 360f) % 360f / SPOKE_DEGREES
        val i = floor(u).toInt() % SPOKES
        val fu = u - floor(u)
        val j = (i + 1) % SPOKES
        fun at(spoke: Int, ring: Int): Pair<Float, Float> = move(spoke, ring)?.let { it.hue to it.sat } ?: (0f to 0f)
        fun ringMove(ring: Int): Pair<Float, Float> {
            if (ring < 0) return 0f to 0f
            val a = at(i, ring)
            val b = at(j, ring)
            return (a.first + (b.first - a.first) * fu) to (a.second + (b.second - a.second) * fu)
        }
        // Between which rings the radius lies: the centre (no move) and the first ring, … , the last ring and beyond.
        val r = radius.coerceAtLeast(0f)
        val step = 1f / RINGS
        val outer = (r / step).coerceAtMost(RINGS.toFloat())
        val k = floor(outer).toInt()
        if (k >= RINGS) return ringMove(RINGS - 1)
        val fr = outer - k
        val lo = ringMove(k - 1)
        val hi = ringMove(k)
        return (lo.first + (hi.first - lo.first) * fr) to (lo.second + (hi.second - lo.second) * fr)
    }

    /** Warps one display-referred colour into [out]: its chroma moved, its luma kept. */
    fun apply(r: Float, g: Float, b: Float, out: FloatArray) {
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val ca = r - 0.5f * (g + b)
        val cb = HALF_SQRT3 * (g - b)
        val chroma = hypot(ca, cb)
        if (chroma < MIN_CHROMA) {
            out[0] = r; out[1] = g; out[2] = b
            return
        }
        val degrees = Math.toDegrees(atan2(cb, ca).toDouble()).toFloat()
        val (dh, ds) = displacement(degrees, chroma)
        val c2 = (chroma + ds).coerceAtLeast(0f)
        val angle = Math.toRadians((degrees + dh).toDouble())
        val a2 = (c2 * cos(angle)).toFloat()
        val b2 = (c2 * sin(angle)).toFloat()
        // Back to RGB with the same luma: the inverse of (chroma a, chroma b, luma).
        out[0] = INV[0] * a2 + INV[1] * b2 + INV[2] * y
        out[1] = INV[3] * a2 + INV[4] * b2 + INV[5] * y
        out[2] = INV[6] * a2 + INV[7] * b2 + INV[8] * y
        // A colour of the picture turned past what the display shows at its luma (full red turned toward orange) keeps the
        // new hue at less saturation, rather than being clipped per channel — which would turn it back.
        if (inRange(r) && inRange(g) && inRange(b) && y in 0f..1f) fitToRange(out, y)
    }

    private fun inRange(v: Float) = v >= -RANGE_SLACK && v <= 1f + RANGE_SLACK

    /** [c] pulled toward the grey of luma [y] just far enough for every channel to lie within 0…1. */
    private fun fitToRange(c: FloatArray, y: Float) {
        var k = 1f
        for (i in 0..2) {
            val d = c[i] - y
            if (c[i] > 1f && d > 0f) k = minOf(k, (1f - y) / d)
            if (c[i] < 0f && d < 0f) k = minOf(k, -y / d)
        }
        if (k < 1f) for (i in 0..2) c[i] = y + (c[i] - y) * k
    }

    companion object {
        const val SPOKES = 6
        const val RINGS = 3
        const val SPOKE_DEGREES = 60f
        const val MAX_HUE = 50f
        const val MAX_SAT = 0.3f
        val NEUTRAL = ColorWarper()

        /** The spokes' names, red first, going round the wheel. */
        val SPOKE_NAMES = listOf("红", "黄", "绿", "青", "蓝", "品红")
        val RING_NAMES = listOf("内圈", "中圈", "外圈")

        private val HALF_SQRT3 = (sqrt(3.0) / 2).toFloat()
        private const val RANGE_SLACK = 1e-4f
        private const val MIN_CHROMA = 1e-6f

        /** The inverse of rows (1, −½, −½), (0, √3/2, −√3/2), (0.2126, 0.7152, 0.0722), row-major. */
        private val INV: FloatArray = run {
            val m = arrayOf(
                doubleArrayOf(1.0, -0.5, -0.5),
                doubleArrayOf(0.0, sqrt(3.0) / 2, -sqrt(3.0) / 2),
                doubleArrayOf(0.2126, 0.7152, 0.0722),
            )
            val det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
                m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
                m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
            fun c(r: Int, k: Int): Double {
                val rows = (0..2).filter { it != r }
                val cols = (0..2).filter { it != k }
                val minor = m[rows[0]][cols[0]] * m[rows[1]][cols[1]] - m[rows[0]][cols[1]] * m[rows[1]][cols[0]]
                return if ((r + k) % 2 == 0) minor else -minor
            }
            // inverse[i][j] = cofactor(j, i) / det
            FloatArray(9) { idx -> (c(idx % 3, idx / 3) / det).toFloat() }
        }
    }
}
