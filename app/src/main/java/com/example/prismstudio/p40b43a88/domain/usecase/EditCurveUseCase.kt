package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import kotlin.math.abs

/**
 * Curve editing rules: the two end points stay at x = 0 and x = 1, inner points keep a minimum
 * horizontal gap from their neighbours, and a curve holds at most [MAX_POINTS] points.
 */
class EditCurveUseCase {

    /** Index of the point within [radius] (normalised units) of ([x], [y]), or -1. */
    fun pointAt(points: List<CurvePoint>, x: Float, y: Float, radius: Float): Int =
        points.indices.minByOrNull { i -> distance(points[i], x, y) }
            ?.takeIf { distance(points[it], x, y) <= radius } ?: -1

    /** Adds a point on the curve at [x] (returns the same list when full or too close to another). */
    fun add(points: List<CurvePoint>, x: Float, y: Float): Pair<List<CurvePoint>, Int> {
        if (points.size >= MAX_POINTS) return points to -1
        val cx = x.coerceIn(MIN_GAP, 1f - MIN_GAP)
        if (points.any { abs(it.x - cx) < MIN_GAP }) return points to -1
        val next = (points + CurvePoint(cx, y.coerceIn(0f, 1f))).sortedBy { it.x }
        return next to next.indexOfFirst { it.x == cx }
    }

    /** Moves point [index]; end points move vertically only, inner points stay between neighbours. */
    fun move(points: List<CurvePoint>, index: Int, x: Float, y: Float): List<CurvePoint> {
        if (index !in points.indices) return points
        val ny = y.coerceIn(0f, 1f)
        val nx = when (index) {
            0 -> 0f
            points.lastIndex -> 1f
            else -> {
                // Float rounding can leave neighbours slightly closer than 2 × MIN_GAP: then stay centred.
                val low = points[index - 1].x + MIN_GAP
                val high = points[index + 1].x - MIN_GAP
                if (low <= high) x.coerceIn(low, high) else (points[index - 1].x + points[index + 1].x) / 2f
            }
        }
        return points.mapIndexed { i, p -> if (i == index) CurvePoint(nx, ny) else p }
    }

    /** Removes an inner point (end points cannot be removed). */
    fun remove(points: List<CurvePoint>, index: Int): List<CurvePoint> =
        if (index <= 0 || index >= points.lastIndex) points else points.filterIndexed { i, _ -> i != index }

    fun reset(): List<CurvePoint> = ToneCurves.IDENTITY

    private fun distance(p: CurvePoint, x: Float, y: Float) = maxOf(abs(p.x - x), abs(p.y - y))

    companion object {
        const val MAX_POINTS = 8
        const val MIN_GAP = 0.03f
    }
}
