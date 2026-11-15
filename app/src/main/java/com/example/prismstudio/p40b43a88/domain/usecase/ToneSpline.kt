package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import kotlin.math.sqrt

/**
 * A tone curve through control points as a monotone cubic (Fritsch–Carlson) spline: smooth, never
 * overshoots between points, and exact for straight lines — so the untouched curve is the identity.
 */
class ToneSpline(points: List<CurvePoint>) {
    private val xs: FloatArray
    private val ys: FloatArray
    private val slopes: FloatArray

    init {
        val sorted = points.sortedBy { it.x }
        val n = sorted.size
        xs = FloatArray(n) { sorted[it].x }
        ys = FloatArray(n) { sorted[it].y }
        slopes = FloatArray(n)
        if (n >= 2) {
            val secant = FloatArray(n - 1) { i ->
                val dx = xs[i + 1] - xs[i]
                if (dx <= 0f) 0f else (ys[i + 1] - ys[i]) / dx
            }
            slopes[0] = secant[0]
            slopes[n - 1] = secant[n - 2]
            for (i in 1 until n - 1) {
                slopes[i] = if (secant[i - 1] * secant[i] <= 0f) 0f else (secant[i - 1] + secant[i]) / 2f
            }
            for (i in 0 until n - 1) {
                if (secant[i] == 0f) {
                    slopes[i] = 0f
                    slopes[i + 1] = 0f
                    continue
                }
                val a = slopes[i] / secant[i]
                val b = slopes[i + 1] / secant[i]
                val s = a * a + b * b
                if (s > 9f) {
                    val tau = 3f / sqrt(s)
                    slopes[i] = tau * a * secant[i]
                    slopes[i + 1] = tau * b * secant[i]
                }
            }
        }
    }

    fun eval(x: Float): Float {
        val n = xs.size
        if (n < 2) return x
        if (x <= xs[0]) return ys[0]
        if (x >= xs[n - 1]) return ys[n - 1]
        var i = 0
        while (i < n - 2 && x > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        if (h <= 0f) return ys[i]
        val t = (x - xs[i]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * slopes[i] +
            (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * slopes[i + 1]
    }
}
