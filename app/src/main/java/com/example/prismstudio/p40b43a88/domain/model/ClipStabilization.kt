package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolve's Stabilization (the Edit inspector's, Translation mode): the camera's shake is taken out of the footage by
 * moving each frame against it. The clip's file is analysed once into a [CameraPath] — where its picture content sits
 * in every frame; [smooth] (0…100) sets how long a stretch of that path is averaged into the steady one the picture
 * follows instead (from a twelfth of a second to two seconds), [strength] (0…100) how much of the difference is taken
 * out, and [zoom] enlarges the picture just enough that the moving frame never shows its edges, by at most [MAX_ZOOM].
 * Off ([enabled] false) keeps the settings; footage only.
 */
data class ClipStabilization(
    val enabled: Boolean = true,
    val smooth: Int = 50,
    val strength: Int = 100,
    val zoom: Boolean = true,
) {
    /** The smoothing's spread in seconds (a Gaussian's sigma): 1/12 s at 0 up to 2 s at 100. */
    val sigmaSeconds: Double get() = 1.0 / 12 + smooth.coerceIn(0, 100) / 100.0 * (2.0 - 1.0 / 12)

    companion object {
        val DEFAULT = ClipStabilization()

        /** The most the picture is enlarged to hide the moving edges (Resolve's Cropping Ratio 0.8). */
        const val MAX_ZOOM = 1.25f
    }
}

/**
 * Where a file's picture content sits in each of its frames, relative to its first: [x] and [y] in fractions of the
 * picture's width and height (right and down positive), one entry per frame at [frameRate]. A camera panning right
 * moves the content left, so its x falls.
 */
class CameraPath(val frameRate: Double, val x: FloatArray, val y: FloatArray) {
    init {
        require(x.size == y.size) { "x and y differ in length" }
    }

    val frames: Int get() = x.size

    override fun equals(other: Any?): Boolean =
        other is CameraPath && other.frameRate == frameRate && other.x.contentEquals(x) && other.y.contentEquals(y)

    override fun hashCode(): Int = 31 * (31 * frameRate.hashCode() + x.contentHashCode()) + y.contentHashCode()
}

/**
 * How a stabilized clip moves each frame: [dx] / [dy] per file frame (fractions of the picture, the content goes there),
 * and the one [scale] that keeps the moved frame's edges out of view (1 without zoom). Built for the part of the file
 * the clip shows, so the zoom only answers for the shake the clip plays.
 */
class StabilizationPlan(val frameRate: Double, val firstFrame: Int, val dx: FloatArray, val dy: FloatArray, val scale: Float) {
    /** The move at file time [sourceSeconds], held at the ends and eased between frames. */
    fun offsetAt(sourceSeconds: Double): Pair<Float, Float> {
        if (dx.isEmpty()) return 0f to 0f
        val f = (sourceSeconds * frameRate - firstFrame).coerceIn(0.0, (dx.size - 1).toDouble())
        val i = f.toInt().coerceAtMost(dx.size - 1)
        val j = min(i + 1, dx.size - 1)
        val t = (f - i).toFloat()
        return (dx[i] + (dx[j] - dx[i]) * t) to (dy[i] + (dy[j] - dy[i]) * t)
    }

    /** The largest move in either direction, in fractions of the picture (how far its edges travel). */
    val largestMove: Float get() = max(dx.maxOfOrNull { abs(it) } ?: 0f, dy.maxOfOrNull { abs(it) } ?: 0f)

    companion object {
        /**
         * The plan for [settings] over file frames [fromFrame] until [untilFrame] of [path]. The steady path is the
         * camera path smoothed by a Gaussian (see [smoothed]: a steady pan stays steady to the file's ends); each frame
         * moves by (steady − path) · strength. With zoom the picture grows until the largest move
         * is hidden (1 + 2 · move, the moves being fractions of the picture); past [ClipStabilization.MAX_ZOOM] the
         * moves are scaled back to what that zoom hides.
         */
        fun of(settings: ClipStabilization, path: CameraPath, fromFrame: Int = 0, untilFrame: Int = path.frames): StabilizationPlan {
            val from = fromFrame.coerceIn(0, path.frames)
            val until = untilFrame.coerceIn(from, path.frames)
            val sigma = settings.sigmaSeconds * path.frameRate
            val share = settings.strength.coerceIn(0, 100) / 100f
            val sx = smoothed(path.x, sigma)
            val sy = smoothed(path.y, sigma)
            val dx = FloatArray(until - from) { (sx[from + it] - path.x[from + it]) * share }
            val dy = FloatArray(until - from) { (sy[from + it] - path.y[from + it]) * share }
            val largest = max(dx.maxOfOrNull { abs(it) } ?: 0f, dy.maxOfOrNull { abs(it) } ?: 0f)
            if (!settings.zoom) return StabilizationPlan(path.frameRate, from, dx, dy, 1f)
            val wanted = 1f + 2f * largest
            if (wanted <= ClipStabilization.MAX_ZOOM) return StabilizationPlan(path.frameRate, from, dx, dy, wanted)
            // The zoom cannot hide all of it: take out only as much as MAX_ZOOM hides.
            val k = (ClipStabilization.MAX_ZOOM - 1f) / 2f / largest
            for (i in dx.indices) { dx[i] *= k; dy[i] *= k }
            return StabilizationPlan(path.frameRate, from, dx, dy, ClipStabilization.MAX_ZOOM)
        }

        /**
         * [values] smoothed by a Gaussian of [sigma] samples. Beyond its ends the series is continued by point reflection
         * through the mean of its first (last) few values, set at their middle — about sigma of them, an odd number — so a
         * steady drift carries on rather than levelling off (holding the ends would bend a pan near them), and the shake
         * of the very first or last frame does not pull the steady path toward it.
         */
        fun smoothed(values: FloatArray, sigma: Double): FloatArray {
            if (values.size < 2 || sigma <= 0.0) return values.copyOf()
            val n = values.size
            val radius = ceil(3 * sigma).toInt()
            val weights = DoubleArray(2 * radius + 1) { exp(-0.5 * ((it - radius) / sigma).let { d -> d * d }) }
            val total = weights.sum()
            val k = (sigma.roundToInt() or 1).coerceIn(1, if (n % 2 == 1) n else n - 1)
            val half = (k - 1) / 2
            var head = 0.0
            var tail = 0.0
            for (i in 0 until k) { head += values[i]; tail += values[n - 1 - i] }
            head /= k
            tail /= k
            fun clamped(i: Int) = values[i.coerceIn(0, n - 1)].toDouble()
            fun at(i: Int): Double = when {
                i < 0 -> 2 * head - clamped(2 * half - i)
                i >= n -> 2 * tail - clamped(2 * (n - 1 - half) - i)
                else -> values[i].toDouble()
            }
            return FloatArray(n) { i ->
                var acc = 0.0
                for (j in -radius..radius) acc += weights[j + radius] * at(i + j)
                (acc / total).toFloat()
            }
        }
    }
}

/**
 * Finds how far picture content moved between two frames of a file, from small luma pictures ([width] × [height], one
 * value per pixel, row by row): the shift that best lines the previous frame up with the current one (least mean
 * absolute difference over the middle of the picture), searched coarse to fine on a half-size copy then refined, with
 * a parabola through the neighbouring differences for the fraction of a pixel. Shifts beyond [searchRadius] pixels (at
 * full size) are not found.
 */
class MotionEstimator(private val width: Int, private val height: Int, private val searchRadius: Int = DEFAULT_RADIUS) {

    /** The content's shift from [previous] to [current], in pixels (right and down positive). */
    fun shift(previous: FloatArray, current: FloatArray): Pair<Float, Float> {
        require(previous.size == width * height && current.size == width * height) { "frames must be $width × $height" }
        // Coarse: half size, half the radius.
        val hw = width / 2
        val hh = height / 2
        val (cx, cy) = if (hw >= 16 && hh >= 16) {
            val p = half(previous)
            val c = half(current)
            val (bx, by) = best(p, c, hw, hh, 0, 0, (searchRadius + 1) / 2)
            bx * 2 to by * 2
        } else 0 to 0
        // Fine: full size around the coarse answer.
        val (fx, fy) = best(previous, current, width, height, cx, cy, 2)
        // The fraction: squared differences, which a parabola fits near the best shift (absolute ones form a V).
        val e = { dx: Int, dy: Int -> cost(previous, current, width, height, dx, dy, squared = true) }
        val sx = fx + parabola(e(fx - 1, fy), e(fx, fy), e(fx + 1, fy))
        val sy = fy + parabola(e(fx, fy - 1), e(fx, fy), e(fx, fy + 1))
        return sx to sy
    }

    /** The whole-pixel shift within [radius] of ([x0], [y0]) with the least difference. */
    private fun best(p: FloatArray, c: FloatArray, w: Int, h: Int, x0: Int, y0: Int, radius: Int): Pair<Int, Int> {
        var bx = x0
        var by = y0
        var least = Double.MAX_VALUE
        for (dy in y0 - radius..y0 + radius) for (dx in x0 - radius..x0 + radius) {
            val e = cost(p, c, w, h, dx, dy)
            if (e < least) { least = e; bx = dx; by = dy }
        }
        return bx to by
    }

    /**
     * The mean absolute (or [squared]) difference between [c] and [p] moved by ([dx], [dy]) — c(x, y) against
     * p(x − dx, y − dy) — over the picture inset by the search margin, so every shift compares the same pixels of [c].
     */
    private fun cost(p: FloatArray, c: FloatArray, w: Int, h: Int, dx: Int, dy: Int, squared: Boolean = false): Double {
        // The search margin, at this picture's scale: every shift tried reads inside the picture.
        val m = (searchRadius + 3) * w / width
        var acc = 0.0
        var n = 0
        for (y in m until h - m) {
            val py = y - dy
            if (py < 0 || py >= h) continue
            val row = y * w
            val prow = py * w
            for (x in m until w - m) {
                val px = x - dx
                if (px < 0 || px >= w) continue
                val d = c[row + x] - p[prow + px]
                acc += if (squared) (d * d).toDouble() else abs(d).toDouble()
                n++
            }
        }
        return if (n == 0) Double.MAX_VALUE else acc / n
    }

    private fun half(f: FloatArray): FloatArray {
        val hw = width / 2
        val hh = height / 2
        return FloatArray(hw * hh) { i ->
            val x = (i % hw) * 2
            val y = (i / hw) * 2
            (f[y * width + x] + f[y * width + x + 1] + f[(y + 1) * width + x] + f[(y + 1) * width + x + 1]) / 4f
        }
    }

    /** The vertex of the parabola through three equally spaced costs, as an offset from the middle one (−0.5…0.5). */
    private fun parabola(a: Double, b: Double, c: Double): Float {
        val d = a - 2 * b + c
        if (d <= 1e-12 || a == Double.MAX_VALUE || c == Double.MAX_VALUE) return 0f
        return (0.5 * (a - c) / d).coerceIn(-0.5, 0.5).toFloat()
    }

    companion object {
        const val DEFAULT_RADIUS = 12

        /** The analysis picture's size for a [w] × [h] file: [ANALYSIS_WIDTH] wide, its shape kept (even). */
        fun analysisSize(w: Int, h: Int): Pair<Int, Int> {
            if (w <= 0 || h <= 0) return ANALYSIS_WIDTH to ANALYSIS_WIDTH * 9 / 16
            val ah = (ANALYSIS_WIDTH.toDouble() * h / w).roundToInt().let { it + (it and 1) }.coerceAtLeast(16)
            return ANALYSIS_WIDTH to ah
        }

        const val ANALYSIS_WIDTH = 160

        /**
         * The camera path of a file whose frames, as [width] × [height] luma pictures, are [frames] (in order, at
         * [frameRate]): the content's shift from frame to frame, added up, in fractions of the picture.
         */
        fun path(frames: List<FloatArray>, width: Int, height: Int, frameRate: Double): CameraPath {
            val estimator = MotionEstimator(width, height)
            val x = FloatArray(frames.size)
            val y = FloatArray(frames.size)
            for (i in 1 until frames.size) {
                val (sx, sy) = estimator.shift(frames[i - 1], frames[i])
                x[i] = x[i - 1] + sx / width
                y[i] = y[i - 1] + sy / height
            }
            return CameraPath(frameRate, x, y)
        }
    }
}

/** Where a file's camera analysis stands (see the stabilization repository). */
sealed interface CameraAnalysis {
    /** Being analysed: [progress] 0…1 of its frames read. */
    data class Analysing(val progress: Float) : CameraAnalysis

    /** Analysed: its camera [path]. */
    data class Ready(val path: CameraPath) : CameraAnalysis

    /** Could not be analysed, and why (in words for the inspector). */
    data class Failed(val reason: String) : CameraAnalysis
}
