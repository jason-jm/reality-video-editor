package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import kotlin.math.abs
import kotlin.math.floor

/**
 * Editing a hue (or luma vs sat) curve: a tap adds a point on the curve with an anchor a sixth of the way
 * (60° of hue) either side — unless a point already bounds that side — so dragging it bends only that
 * stretch, as Resolve's colour presets do. Adding never changes the curve; only moving does. Points keep
 * [MIN_GAP] from their neighbours; a hue curve's x wraps round.
 */
class EditHueCurveUseCase {

    /** [points] with a point at [x] (and its anchors), and the index of that point; an existing point within reach is picked instead. */
    fun add(points: List<CurvePoint>, x: Float, periodic: Boolean): Pair<List<CurvePoint>, Int> {
        val cx = place(x, periodic)
        val near = points.indices.firstOrNull { gap(points[it].x, cx, periodic) < MIN_GAP }
        if (near != null) return points to near
        if (points.size >= MAX_POINTS) return points to -1
        val spline = HueSpline(points, periodic)
        val added = mutableListOf(CurvePoint(cx, spline.eval(cx)))
        for (side in listOf(-ANCHOR, ANCHOR)) {
            if (points.size + added.size >= MAX_POINTS) break
            val ax = cx + side
            if (!periodic && (ax < 0f || ax > 1f)) continue
            val at = place(ax, periodic)
            // A point between the new one and this anchor already holds the rest of the curve in place.
            val bounded = points.any { p -> between(p.x, cx, side, periodic) }
            if (!bounded) added += CurvePoint(at, spline.eval(at))
        }
        val next = (points + added).sortedBy { it.x }
        return next to next.indexOfFirst { it.x == cx }
    }

    /** Point [index] moved to ([x], [y]); it stays between its neighbours (a hue curve's first and last within the turn). */
    fun move(points: List<CurvePoint>, index: Int, x: Float, y: Float, periodic: Boolean): List<CurvePoint> {
        if (index !in points.indices) return points
        val low = if (index > 0) points[index - 1].x + MIN_GAP else 0f
        val high = if (index < points.lastIndex) points[index + 1].x - MIN_GAP else if (periodic) 1f - MIN_GAP else 1f
        val nx = if (low <= high) x.coerceIn(low, high) else points[index].x
        return points.mapIndexed { i, p -> if (i == index) CurvePoint(nx, y.coerceIn(0f, 1f)) else p }
    }

    /** Point [index] removed; with none left the curve is flat. */
    fun remove(points: List<CurvePoint>, index: Int): List<CurvePoint> = points.filterIndexed { i, _ -> i != index }

    private fun place(x: Float, periodic: Boolean): Float =
        if (periodic) (x - floor(x)).let { if (it >= 1f - MIN_GAP / 2) 0f else it } else x.coerceIn(0f, 1f)

    /** Distance between two x positions, round the turn for a hue curve. */
    private fun gap(a: Float, b: Float, periodic: Boolean): Float {
        val d = abs(a - b)
        return if (periodic) minOf(d, 1f - d) else d
    }

    /** Whether [x] lies between [from] and [from] + [side], or at that anchor (round the turn for a hue curve). */
    private fun between(x: Float, from: Float, side: Float, periodic: Boolean): Boolean {
        var d = x - from
        if (periodic) d -= floor(d + 0.5f) // -0.5 ≤ d < 0.5
        return if (side > 0) d > 0f && d < side + MIN_GAP else d < 0f && d > side - MIN_GAP
    }

    companion object {
        const val MAX_POINTS = 12
        const val MIN_GAP = 0.02f
        /** Where the anchors go: a sixth of the curve, 60° of hue. */
        const val ANCHOR = 1f / 6f
    }
}
