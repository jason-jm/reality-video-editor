package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.HueCurves

/**
 * A [HueCurves] curve through its points: the same never-overshooting spline as [ToneSpline], flat (0.5)
 * without points and held level beyond the outermost ones. A hue curve wraps round — red at x = 0 is red
 * at x = 1 — so its points are repeated a turn either side before the spline is fitted.
 */
class HueSpline(points: List<CurvePoint>, private val periodic: Boolean) {
    private val spline: ToneSpline? = when {
        points.isEmpty() -> null
        !periodic -> ToneSpline(points)
        else -> {
            val sorted = points.sortedBy { it.x }
            ToneSpline((-1..1).flatMap { turn -> sorted.map { CurvePoint(it.x + turn, it.y) } })
        }
    }

    fun eval(x: Float): Float {
        val s = spline ?: return HueCurves.FLAT
        return s.eval(if (periodic) x - kotlin.math.floor(x) else x)
    }
}
