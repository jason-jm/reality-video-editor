package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Resolve's Auto Color: node 1's Lift and Gain set from the picture's black and white points, channel
 * by channel — the darkest tones (0.5th percentile) go to black and the brightest (99.5th) to white, so
 * the levels open up and a colour cast at either end comes out in one move. The Gain puck reaches about
 * ±20 % per channel; what a stronger cast needs beyond it goes to the white balance (色温 / 色调, applied
 * after the wheels), the ring keeping white at white. Offset returns to neutral (the solution starts from
 * the source); gamma, exposure, contrast and the rest stay as they were.
 */
class AutoColorUseCase {

    /** The wheels and white balance to set, and what to tell the user. */
    data class Result(val lift: WheelValue, val gain: WheelValue, val temperature: Float, val tint: Float, val message: String) {
        fun applyTo(grade: ColorGrade): ColorGrade =
            grade.copy(offset = WheelValue.NEUTRAL, lift = lift, gain = gain, temperature = temperature, tint = tint)
    }

    /** The correction for [frame], the clip's ungraded picture; refused for a flat or single-colour picture. */
    fun analyse(frame: FramePixels): Result {
        val histograms = Array(3) { IntArray(BINS) }
        for (argb in frame.pixels) {
            histograms[0][(argb shr 16) and 0xFF]++
            histograms[1][(argb shr 8) and 0xFF]++
            histograms[2][argb and 0xFF]++
        }
        val total = frame.pixels.size
        if (total == 0) throw EditRejected("还没有读到画面，稍后再试")
        val black = FloatArray(3) { percentile(histograms[it], total, LOW) }
        val white = FloatArray(3) { percentile(histograms[it], total, HIGH) }
        if ((0 until 3).any { white[it] - black[it] < MIN_RANGE }) throw EditRejected("画面太平或几乎是单色，无法自动调色")
        // Lift takes each black point to 0 (white anchored), then Gain takes the lifted white point to 1.
        val lift = FloatArray(3) { c -> -black[c].coerceAtMost(MAX_BLACK) / (1f - black[c].coerceAtMost(MAX_BLACK)) }
        val gain = FloatArray(3) { c -> 1f / (white[c] + lift[c] * (1f - white[c])) }
        val liftLevel = luma(lift)
        val gainLevel = luma(gain)
        val liftPuck = puck(FloatArray(3) { lift[it] - liftLevel })
        val gainPuck = puck(FloatArray(3) { gain[it] - gainLevel })
        val liftWheel = wheel(2f * liftLevel, liftPuck)
        val puckOnly = wheel(gainLevel - 1f, gainPuck)
        // What the Gain wheel cannot reach goes to the white balance; the ring then keeps white at white.
        val push = BuildGradeLutUseCase.wheelDelta(puckOnly)
        val reached = FloatArray(3) { 1f + puckOnly.master + push[it] }
        val (temperature, tint) = whiteBalance(FloatArray(3) { gain[it] / reached[it] })
        val balance = balanceFactors(temperature, tint)
        val ring = (gainLevel - luma(FloatArray(3) { push[it] * balance[it] })) / luma(balance) - 1f
        val gainWheel = puckOnly.copy(master = ring.coerceIn(-1f, 1f))
        val pushed = BuildGradeLutUseCase.wheelDelta(gainWheel)
        val shortfall = (0 until 3).maxOf { c -> abs((1f + gainWheel.master + pushed[c]) * balance[c] / gain[c] - 1f) }
        val cast = hypot(liftPuck[0], liftPuck[1]) + hypot(gainPuck[0], gainPuck[1]) > CAST_THRESHOLD
        val clamped = shortfall > SHORTFALL || hypot(liftPuck[0], liftPuck[1]) > WheelValue.MAX_BALANCE
        val message = buildString {
            append("已自动调色：黑位 ${percent(luma(black))} → 0，白位 ${percent(luma(white))} → 100")
            when {
                clamped -> append("，偏色较重，已尽量校正")
                cast -> append("，并校正了偏色")
            }
        }
        return Result(liftWheel, gainWheel, temperature, tint, message)
    }

    companion object {
        private const val BINS = 256
        private const val LOW = 0.005
        private const val HIGH = 0.995
        /** Each channel must span at least this much for its points to mean anything. */
        private const val MIN_RANGE = 0.08f
        /** A black point higher than this is a light picture, not a raised black. */
        private const val MAX_BLACK = 0.5f
        /** Puck travel (sum of both wheels) above which the correction counts as removing a cast. */
        private const val CAST_THRESHOLD = 0.02f
        /** A channel this far (relative) from its white after the correction: the cast was beyond reach. */
        private const val SHORTFALL = 0.03f

        /**
         * 色温 / 色调 (−100…100) whose channel factors stand in the ratios of [residual] (per-channel gain still
         * wanted): the white balance multiplies red by 1 + 0.18 t + 0.09 i, green by 1 − 0.18 i and blue by
         * 1 − 0.18 t + 0.09 i (t, i = 色温, 色调 / 100; see [BuildGradeLutUseCase.apply]). Solved exactly for the
         * red / green and blue / green ratios, then held to the sliders' range.
         */
        internal fun whiteBalance(residual: FloatArray): Pair<Float, Float> {
            val a = residual[0] / residual[1]
            val b = residual[2] / residual[1]
            val v = (a + b - 2f) / (2f + 2f * (a + b)) // 0.09 i
            val u = (a - b) * (1f - 2f * v) / 2f // 0.18 t
            val temperature = (u / 0.18f * 100f).coerceIn(-100f, 100f)
            val tint = (v / 0.09f * 100f).coerceIn(-100f, 100f)
            // Round to the sliders' step; a cast too small to see leaves them at zero.
            return Math.round(temperature).toFloat() to Math.round(tint).toFloat()
        }

        /** The white balance's per-channel factors for [temperature] and [tint] (−100…100). */
        internal fun balanceFactors(temperature: Float, tint: Float): FloatArray {
            val t = temperature / 100f
            val i = tint / 100f
            return floatArrayOf(1f + 0.18f * t + 0.09f * i, 1f - 0.18f * i, 1f - 0.18f * t + 0.09f * i)
        }

        /** The value (0…1) below which [fraction] of the pixels lie. */
        private fun percentile(histogram: IntArray, total: Int, fraction: Double): Float {
            val target = fraction * total
            var seen = 0L
            for (bin in histogram.indices) {
                seen += histogram[bin]
                if (seen >= target) return bin / (BINS - 1f)
            }
            return 1f
        }

        private fun luma(v: FloatArray) = BuildGradeLutUseCase.luma(v[0], v[1], v[2])

        private fun percent(v: Float) = (v * 100f + 0.5f).toInt().toString()

        /**
         * The puck (x, y) that [BuildGradeLutUseCase.wheelDelta] maps to [push], a per-channel change with no
         * Rec.709 luma: wheelDelta is linear — 0.5·(x cos φ + y sin φ) per channel (φ = 0°, 120°, 240°) with
         * its luma removed — so (x, y) solves M·(x, y) = push, by least squares (exact for a zero-luma push).
         */
        internal fun puck(push: FloatArray): FloatArray {
            val columns = listOf(WheelValue(1f, 0f, 0f), WheelValue(0f, 1f, 0f)).map(BuildGradeLutUseCase::wheelDelta)
            var a = 0.0; var b = 0.0; var d = 0.0; var px = 0.0; var py = 0.0
            for (c in 0 until 3) {
                val mx = columns[0][c].toDouble()
                val my = columns[1][c].toDouble()
                a += mx * mx; b += mx * my; d += my * my
                px += mx * push[c]; py += my * push[c]
            }
            val det = a * d - b * b
            return floatArrayOf(((d * px - b * py) / det).toFloat(), ((a * py - b * px) / det).toFloat())
        }

        /** A wheel with ring [master] (held to the ring's −1…1) and puck [puck] (held inside the wheel). */
        internal fun wheel(master: Float, puck: FloatArray): WheelValue {
            val r = hypot(puck[0], puck[1])
            val k = if (r > WheelValue.MAX_BALANCE) WheelValue.MAX_BALANCE / r else 1f
            return WheelValue(puck[0] * k, puck[1] * k, master.coerceIn(-1f, 1f))
        }
    }
}
