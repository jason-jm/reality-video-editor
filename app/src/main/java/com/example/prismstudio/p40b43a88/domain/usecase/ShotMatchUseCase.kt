package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Resolve's Shot Match against a Gallery still: node 1's Lift, Gamma and Gain set so that the current
 * clip's blacks, mid-tones and whites (0.5th, 50th and 99.5th percentiles, channel by channel) land where
 * the still's are — matching its levels and its colour at each end and in the middle. What the Gain puck
 * cannot reach (about ±20 % per channel) is added to the white balance, as Auto Color does. The rest of
 * the node (exposure, contrast, saturation, curves, a look or LUT) stays: the wheels are solved through
 * it, the whole grade measured and the aim corrected until the clip's percentiles meet the still's.
 */
class ShotMatchUseCase(private val engine: BuildGradeLutUseCase = BuildGradeLutUseCase()) {

    /** The grade to set, and what to tell the user. */
    data class Result(val grade: ColorGrade, val message: String)

    /**
     * [grade] (the clip's node 1) with the wheels that match [source] (the clip's ungraded picture) to
     * [target] (the still's graded picture); refused when either picture is flat.
     */
    fun match(source: FramePixels, target: FramePixels, grade: ColorGrade): Result {
        val sample = sample(source)
        val want = percentiles(sample(target)) ?: throw EditRejected("静帧太平或几乎是单色，无法匹配")
        val have = percentiles(sample) ?: throw EditRejected("画面太平或几乎是单色，无法匹配")
        // Aim at the still's points, then nudge the aim by what the rest of the node does to them.
        val aim = Array(3) { c -> want[c].copyOf() }
        var best = grade
        var bestError = Float.MAX_VALUE
        var clamped = false
        for (pass in 0 until PASSES) {
            val (candidate, held) = solve(grade, have, aim)
            val got = percentiles(graded(sample, candidate)) ?: break
            var error = 0f
            for (c in 0 until 3) for (k in 0 until 3) {
                val miss = want[c][k] - got[c][k]
                error = maxOf(error, abs(miss))
                aim[c][k] = (aim[c][k] + miss).coerceIn(0f, 1f)
            }
            if (error < bestError) {
                bestError = error
                best = candidate
                clamped = held
            }
            if (error < GOOD_ENOUGH) break
        }
        val message = buildString {
            append("已匹配到静帧：暗部、中间调、亮部按通道对齐")
            if (clamped || bestError > CLOSE) append("，部分差异超出色轮范围，已尽量接近")
        }
        return Result(best, message)
    }

    /** Node 1 with the wheels that take [have] (per channel: black, middle, white) to [aim]; true when a puck was held at the rim. */
    private fun solve(grade: ColorGrade, have: Array<FloatArray>, aim: Array<FloatArray>): Pair<ColorGrade, Boolean> {
        val lift = FloatArray(3)
        val gain = FloatArray(3)
        val gamma = FloatArray(3)
        for (c in 0 until 3) {
            val (l, g, a) = channel(have[c][0], have[c][1], have[c][2], aim[c][0], aim[c][1], aim[c][2])
            lift[c] = l; gain[c] = g; gamma[c] = a
        }
        val liftLevel = luma(lift)
        val gainLevel = luma(gain)
        val gammaLevel = luma(gamma)
        val pucks = listOf(lift to liftLevel, gain to gainLevel, gamma to gammaLevel).map { (values, level) ->
            AutoColorUseCase.puck(FloatArray(3) { values[it] - level })
        }
        // The Gain puck's remainder goes to the white balance, on top of the node's own; the ring keeps white.
        val puckOnly = AutoColorUseCase.wheel(gainLevel - 1f, pucks[1])
        val push = BuildGradeLutUseCase.wheelDelta(puckOnly)
        val (dt, di) = AutoColorUseCase.whiteBalance(FloatArray(3) { gain[it] / (1f + puckOnly.master + push[it]) })
        val temperature = (grade.temperature + dt).coerceIn(-100f, 100f)
        val tint = (grade.tint + di).coerceIn(-100f, 100f)
        val added = AutoColorUseCase.balanceFactors(temperature - grade.temperature, tint - grade.tint)
        val ring = (gainLevel - luma(FloatArray(3) { push[it] * added[it] })) / luma(added) - 1f
        val held = hypot(pucks[0][0], pucks[0][1]) > WheelValue.MAX_BALANCE || hypot(pucks[2][0], pucks[2][1]) > WheelValue.MAX_BALANCE ||
            abs(dt) >= 100f || abs(di) >= 100f
        return grade.copy(
            offset = WheelValue.NEUTRAL,
            lift = AutoColorUseCase.wheel(2f * liftLevel, pucks[0]),
            gain = puckOnly.copy(master = ring.coerceIn(-1f, 1f)),
            gamma = AutoColorUseCase.wheel(gammaLevel, pucks[2]),
            temperature = temperature,
            tint = tint,
        ) to held
    }

    private fun graded(pixels: FramePixels, grade: ColorGrade): FramePixels {
        val out = FloatArray(3)
        val graded = IntArray(pixels.pixels.size) { i ->
            val p = pixels.pixels[i]
            engine.apply(grade, ((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f, out)
            BuildGradeLutUseCase.argb(out)
        }
        return FramePixels(pixels.width, pixels.height, graded)
    }

    companion object {
        private const val BINS = 256
        private val POINTS = doubleArrayOf(0.005, 0.5, 0.995)
        /** Each channel must span this much (black to white) for its points to mean anything. */
        private const val MIN_RANGE = 0.08f
        private const val PASSES = 6
        private const val GOOD_ENOUGH = 0.004f
        /** Still this far off after the passes: the match is only approximate. */
        private const val CLOSE = 0.02f
        private const val MAX_SAMPLES = 40_000

        /** Every n-th pixel, so a frame of any size measures in about [MAX_SAMPLES]. */
        internal fun sample(frame: FramePixels): FramePixels {
            val step = (frame.pixels.size / MAX_SAMPLES).coerceAtLeast(1)
            if (step == 1) return frame
            val picked = IntArray(frame.pixels.size / step) { frame.pixels[it * step] }
            return FramePixels(picked.size, 1, picked)
        }

        /** Per channel the black, middle and white points (0…1), or null when a channel is flat. */
        internal fun percentiles(frame: FramePixels): Array<FloatArray>? {
            if (frame.pixels.isEmpty()) return null
            val histograms = Array(3) { IntArray(BINS) }
            for (p in frame.pixels) {
                histograms[0][(p shr 16) and 0xFF]++
                histograms[1][(p shr 8) and 0xFF]++
                histograms[2][p and 0xFF]++
            }
            val points = Array(3) { c -> FloatArray(3) { k -> point(histograms[c], frame.pixels.size, POINTS[k]) } }
            return points.takeIf { it.all { p -> p[2] - p[0] >= MIN_RANGE } }
        }

        private fun point(histogram: IntArray, total: Int, fraction: Double): Float {
            val target = fraction * total
            var seen = 0L
            for (bin in histogram.indices) {
                seen += histogram[bin]
                if (seen >= target) return bin / (BINS - 1f)
            }
            return 1f
        }

        /**
         * One channel's Lift (L), Gain (G) and Gamma (a) taking black [b], middle [m] and white [w] to [tb], [tm],
         * [tw] in the engine's order — c ↦ (c + L(1 − c))·G, then c^(1 / (1 + a)). With a fixed, the two ends fix
         * L and G (aiming at the targets raised to 1 + a); the middle then fixes a. A few rounds settle all three.
         */
        internal fun channel(b: Float, m: Float, w: Float, tb: Float, tm: Float, tw: Float): Triple<Float, Float, Float> {
            var a = 0f
            var l = 0f
            var g = 1f
            repeat(ROUNDS) {
                val e = 1f + a
                val lo = tb.coerceAtLeast(0f).pow(e)
                val hi = tw.coerceIn(lo + 1e-3f, 1f).pow(e)
                val r = lo / hi
                val denominator = 1f - b - r + r * w
                l = if (abs(denominator) < 1e-6f) 0f else ((r * w - b) / denominator).coerceIn(-0.5f, 0.5f)
                g = (hi / (w * (1f - l) + l)).coerceIn(0f, 2f)
                val middle = ((m * (1f - l) + l) * g).coerceIn(1e-3f, 0.999f)
                val aim = tm.coerceIn(1e-3f, 0.999f)
                a = (ln(middle) / ln(aim) - 1f).coerceIn(MIN_GAMMA, MAX_GAMMA)
            }
            return Triple(l, g, a)
        }

        private const val ROUNDS = 12
        private const val MIN_GAMMA = -0.9f
        private const val MAX_GAMMA = 1f

        private fun luma(v: FloatArray) = BuildGradeLutUseCase.luma(v[0], v[1], v[2])
    }
}
