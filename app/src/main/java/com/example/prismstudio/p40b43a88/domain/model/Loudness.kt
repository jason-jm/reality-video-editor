package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A media file's sound as loudness blocks (ITU-R BS.1770): K-weighted energy (channel-weighted mean
 * square) and true peak (BS.1770-4 Annex 2, linear) for every [blockMs] of the file, measured once. The
 * energy is what the stereo mix plays — a mono file sounds on both channels — so any clip's loudness
 * follows from the blocks under it.
 */
class LoudnessProfile(
    val blockMs: Int,
    val energy: FloatArray,
    val truePeak: FloatArray,
    /** A two-channel file's sides apart, so a clip's channel mapping is measured as it plays; null for other files. */
    val stereo: StereoBlocks? = null,
) {
    val blocks: Int get() = energy.size

    /**
     * Block [j]'s energy as the mix plays it with [channels] (Resolve's clip attributes): one side on both doubles that
     * side's, a mono fold is the mean of the two (their correlation counted); a file without [stereo] keeps its own.
     */
    fun energyOf(j: Int, channels: AudioChannels): Float {
        val st = stereo ?: return energy[j]
        return when (channels) {
            AudioChannels.STEREO, AudioChannels.SWAP -> energy[j]
            AudioChannels.LEFT -> 2f * st.left[j]
            AudioChannels.RIGHT -> 2f * st.right[j]
            AudioChannels.MONO -> ((st.left[j] + st.right[j] + 2f * st.cross[j]) / 2f).coerceAtLeast(0f)
        }
    }

    /** Block [j]'s true peak with [channels]: the side that plays, or the mono fold's own. */
    fun peakOf(j: Int, channels: AudioChannels): Float {
        val st = stereo ?: return truePeak[j]
        return when (channels) {
            AudioChannels.STEREO, AudioChannels.SWAP -> truePeak[j]
            AudioChannels.LEFT -> st.leftPeak[j]
            AudioChannels.RIGHT -> st.rightPeak[j]
            AudioChannels.MONO -> st.monoPeak[j]
        }
    }
}

/**
 * A two-channel file's loudness blocks side by side: the left and right K-weighted mean squares, the mean of their
 * product (what a mono fold adds or cancels), and the true peaks of each side and of the fold.
 */
class StereoBlocks(
    val left: FloatArray,
    val right: FloatArray,
    val cross: FloatArray,
    val leftPeak: FloatArray,
    val rightPeak: FloatArray,
    val monoPeak: FloatArray,
) {
    /** These blocks followed by [other]'s. */
    operator fun plus(other: StereoBlocks) = StereoBlocks(
        left + other.left, right + other.right, cross + other.cross, leftPeak + other.leftPeak, rightPeak + other.rightPeak, monoPeak + other.monoPeak,
    )
}

/**
 * True peak (ITU-R BS.1770-4 Annex 2): the largest magnitude of the signal oversampled 4× — the peaks a
 * DAC or a lossy encoder reconstructs between the samples, which a sample peak misses by up to 3 dB. The
 * interpolator is a 48-tap Kaiser-windowed sinc split into four 12-tap phases (the structure of the
 * Recommendation's example filter), each phase normalized to unity gain at DC.
 */
class TruePeakMeter {
    private val history = DoubleArray(TAPS)
    private var next = 0

    /** Feeds sample [x] and returns the largest magnitude among it and the four points interpolated before it. */
    fun process(x: Double): Double {
        history[next] = x
        next = (next + 1) % TAPS
        var peak = abs(x)
        val newest = if (next == 0) TAPS - 1 else next - 1
        for (phase in PHASES) {
            // y[4m + p] = Σ h[4k + p] · x[m − k]: coefficient k meets the sample k steps before the newest.
            var acc = 0.0
            var i = newest
            for (k in 0 until TAPS) {
                acc += phase[k] * history[i]
                i = if (i == 0) TAPS - 1 else i - 1
            }
            if (abs(acc) > peak) peak = abs(acc)
        }
        return peak
    }

    companion object {
        const val OVERSAMPLING = 4
        const val TAPS = 12

        /** The four phases of the interpolator, each [TAPS] coefficients long. */
        val PHASES: Array<DoubleArray> = run {
            val length = OVERSAMPLING * TAPS
            val centre = (length - 1) / 2.0
            val prototype = DoubleArray(length) { n ->
                val t = (n - centre) / OVERSAMPLING
                val sinc = if (t == 0.0) 1.0 else sin(PI * t) / (PI * t)
                sinc * kaiser(n, length, KAISER_BETA)
            }
            Array(OVERSAMPLING) { phase ->
                val taps = DoubleArray(TAPS) { k -> prototype[k * OVERSAMPLING + phase] }
                val sum = taps.sum()
                DoubleArray(TAPS) { taps[it] / sum }
            }
        }

        private const val KAISER_BETA = 5.0

        private fun kaiser(n: Int, length: Int, beta: Double): Double {
            val r = 2.0 * n / (length - 1) - 1.0
            return besselI0(beta * sqrt(1.0 - r * r)) / besselI0(beta)
        }

        /** The modified Bessel function of the first kind, order 0 (power series). */
        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            while (term > 1e-12 * sum) {
                term *= (x / (2.0 * k)) * (x / (2.0 * k))
                sum += term
                k++
            }
            return sum
        }
    }
}

/**
 * Builds a [LoudnessProfile] from decoded PCM: each channel is K-weighted, squared and averaged per
 * block, then the channels are summed with their BS.1770 weights.
 */
class LoudnessAnalyzer(sampleRate: Int, private val channels: Int, private val blockMs: Int = BLOCK_MS) {
    private val filters = Array(channels) { KWeighting(sampleRate) }
    private val peaks = Array(channels) { TruePeakMeter() }
    private val weights = DoubleArray(channels) { channelWeight(it, channels) }
    private val framesPerBlock = (sampleRate.toLong() * blockMs / 1000).toInt().coerceAtLeast(1)
    private var energy = FloatArray(256)
    private var peak = FloatArray(256)
    private var count = 0
    private var sum = 0.0
    private var maxAbs = 0f
    private var frames = 0

    // A two-channel file's sides apart (StereoBlocks): sums and peaks of the block, and the blocks so far.
    private val stereo = channels == 2
    private val monoPeakMeter = TruePeakMeter()
    private var sumL = 0.0
    private var sumR = 0.0
    private var sumLR = 0.0
    private val sidePeak = FloatArray(3)
    private var sides = Array(6) { FloatArray(if (stereo) 256 else 0) }

    /** Adds [frameCount] frames of interleaved samples (−1..1) from [samples]. */
    fun add(samples: FloatArray, frameCount: Int) {
        var i = 0
        repeat(frameCount) {
            var weighted = 0.0
            val first = i
            for (c in 0 until channels) {
                val x = samples[i++]
                val y = filters[c].process(x.toDouble())
                weighted += weights[c] * y * y
                val a = peaks[c].process(x.toDouble()).toFloat()
                if (a > maxAbs) maxAbs = a
                if (stereo) {
                    if (c == 0) { sumL += y * y; lastY = y } else { sumR += y * y; sumLR += lastY * y }
                    if (a > sidePeak[c]) sidePeak[c] = a
                }
            }
            if (stereo) {
                val m = monoPeakMeter.process((samples[first] + samples[first + 1]) * 0.5).toFloat()
                if (m > sidePeak[2]) sidePeak[2] = m
            }
            sum += weighted
            if (++frames == framesPerBlock) closeBlock()
        }
    }

    /** The left side's K-weighted sample of the frame being read (for the sides' product). */
    private var lastY = 0.0

    fun finish(): LoudnessProfile {
        if (frames > 0) closeBlock()
        val st = if (stereo) StereoBlocks(
            sides[0].copyOf(count), sides[1].copyOf(count), sides[2].copyOf(count), sides[3].copyOf(count), sides[4].copyOf(count), sides[5].copyOf(count),
        ) else null
        return LoudnessProfile(blockMs, energy.copyOf(count), peak.copyOf(count), st)
    }

    private fun closeBlock() {
        if (count == energy.size) {
            energy = energy.copyOf(count * 2)
            peak = peak.copyOf(count * 2)
            if (stereo) sides = Array(6) { sides[it].copyOf(count * 2) }
        }
        energy[count] = (sum / frames).toFloat()
        peak[count] = maxAbs
        if (stereo) {
            sides[0][count] = (sumL / frames).toFloat()
            sides[1][count] = (sumR / frames).toFloat()
            sides[2][count] = (sumLR / frames).toFloat()
            for (k in 0 until 3) sides[3 + k][count] = sidePeak[k]
            sumL = 0.0; sumR = 0.0; sumLR = 0.0
            sidePeak.fill(0f)
        }
        count++
        sum = 0.0
        maxAbs = 0f
        frames = 0
    }

    companion object {
        const val BLOCK_MS = 100

        /** BS.1770 channel weights; a mono file is heard on both channels of the stereo mix. */
        fun channelWeight(index: Int, channels: Int): Double = when {
            channels == 1 -> 2.0
            channels <= 3 -> 1.0
            index == 3 -> 0.0 // LFE
            index >= 4 -> 1.41 // surrounds
            else -> 1.0
        }
    }
}

/**
 * The BS.1770 K-weighting filter at [sampleRate]: a high-shelf pre-filter followed by the RLB high-pass,
 * as two direct-form-I biquads (coefficients from the analogue prototypes, so any rate works).
 */
class KWeighting(sampleRate: Int) {
    /** b0, b1, b2, a1, a2 of the shelf and of the high-pass. */
    val shelf: DoubleArray
    val highPass: DoubleArray
    private val state = DoubleArray(8)

    init {
        val rate = sampleRate.toDouble()
        var k = tan(PI * SHELF_HZ / rate)
        val vh = 10.0.pow(SHELF_GAIN_DB / 20.0)
        val vb = vh.pow(0.4996667741545416)
        var a0 = 1.0 + k / SHELF_Q + k * k
        shelf = doubleArrayOf(
            (vh + vb * k / SHELF_Q + k * k) / a0,
            2.0 * (k * k - vh) / a0,
            (vh - vb * k / SHELF_Q + k * k) / a0,
            2.0 * (k * k - 1.0) / a0,
            (1.0 - k / SHELF_Q + k * k) / a0,
        )
        k = tan(PI * HIGH_PASS_HZ / rate)
        a0 = 1.0 + k / HIGH_PASS_Q + k * k
        highPass = doubleArrayOf(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / HIGH_PASS_Q + k * k) / a0)
    }

    fun process(x: Double): Double {
        val s = state
        val y1 = shelf[0] * x + shelf[1] * s[0] + shelf[2] * s[1] - shelf[3] * s[2] - shelf[4] * s[3]
        s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y1
        val y2 = highPass[0] * y1 + highPass[1] * s[4] + highPass[2] * s[5] - highPass[3] * s[6] - highPass[4] * s[7]
        s[5] = s[4]; s[4] = y1; s[7] = s[6]; s[6] = y2
        return y2
    }

    private companion object {
        const val SHELF_HZ = 1681.974450955533
        const val SHELF_GAIN_DB = 3.999843853973347
        const val SHELF_Q = 0.7071752369554196
        const val HIGH_PASS_HZ = 38.13547087602444
        const val HIGH_PASS_Q = 0.5003270373238773
    }
}

/** Loudness (LUFS) of a K-weighted mean square. */
fun lufsOf(meanSquare: Double): Double = -0.691 + 10.0 * log10(meanSquare)

/**
 * BS.1770 integrated loudness (LUFS) of consecutive 100 ms block energies: 400 ms windows overlapping
 * by 75 %, an absolute gate at −70 LUFS and a relative gate 10 LU under the loudness of the windows
 * that pass it. Null when there is no full window or every window is below the absolute gate.
 */
fun gatedLoudness(energy: DoubleArray): Double? {
    if (energy.size < WINDOW_BLOCKS) return null
    val windows = DoubleArray(energy.size - WINDOW_BLOCKS + 1) { k ->
        var sum = 0.0
        for (j in k until k + WINDOW_BLOCKS) sum += energy[j]
        sum / WINDOW_BLOCKS
    }
    val audible = windows.filter { lufsOf(it) > ABSOLUTE_GATE_LUFS }
    if (audible.isEmpty()) return null
    val relativeGate = lufsOf(audible.average()) - RELATIVE_GATE_LU
    return lufsOf(audible.filter { lufsOf(it) > relativeGate }.average())
}

/**
 * EBU Tech 3342 loudness range (LU) of consecutive 100 ms block energies: the short-term (3 s) loudness
 * every block, gated at −70 LUFS and then 20 LU under the loudness of what passed, and the spread between
 * its 10th and 95th percentiles. Null without a full short-term window above the gates.
 */
fun loudnessRange(energy: DoubleArray): Double? {
    if (energy.size < SHORT_TERM_BLOCKS) return null
    var sum = 0.0
    for (j in 0 until SHORT_TERM_BLOCKS) sum += energy[j]
    val windows = DoubleArray(energy.size - SHORT_TERM_BLOCKS + 1)
    windows[0] = sum / SHORT_TERM_BLOCKS
    for (k in 1 until windows.size) {
        sum += energy[k + SHORT_TERM_BLOCKS - 1] - energy[k - 1]
        windows[k] = maxOf(sum, 0.0) / SHORT_TERM_BLOCKS
    }
    val audible = windows.filter { it > 0.0 && lufsOf(it) > ABSOLUTE_GATE_LUFS }
    if (audible.isEmpty()) return null
    val relativeGate = lufsOf(audible.average()) - RANGE_RELATIVE_GATE_LU
    val levels = audible.map(::lufsOf).filter { it > relativeGate }.sorted()
    if (levels.isEmpty()) return null
    return percentile(levels, 0.95) - percentile(levels, 0.10)
}

/** Linear-interpolated [p] percentile of ascending [sorted]. */
private fun percentile(sorted: List<Double>, p: Double): Double {
    val x = p * (sorted.size - 1)
    val i = x.toInt().coerceAtMost(sorted.size - 1)
    val j = (i + 1).coerceAtMost(sorted.size - 1)
    return sorted[i] + (sorted[j] - sorted[i]) * (x - i)
}

/** 400 ms gating windows of 100 ms blocks. */
const val WINDOW_BLOCKS = 4

/** 3 s short-term windows of 100 ms blocks. */
const val SHORT_TERM_BLOCKS = 30
private const val RANGE_RELATIVE_GATE_LU = 20.0
private const val ABSOLUTE_GATE_LUFS = -70.0
private const val RELATIVE_GATE_LU = 10.0
