package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a de-esser listens: the high band from this frequency up (sibilance sits around 5–9 kHz). */
enum class DeEssBand(val label: String, val hz: Double) {
    LOW("4 kHz", 4_000.0),
    MID("6 kHz", 6_000.0),
    HIGH("8 kHz", 8_000.0),
}

/**
 * A track's de-esser (Fairlight's De-Esser): it splits off the high band from [band] up, follows that band's level, and
 * turns the band alone down where it rises above the threshold — "s" and "sh" lose their hiss, the voice beneath stays.
 * [amount] (0…100) lowers the threshold from −10 dBFS to −40 dBFS; above it the band is compressed [RATIO]:1, by at most
 * [MAX_REDUCTION_DB]. It acts after the track's EQ and before its dynamics; [enabled] off bypasses it without losing it.
 */
data class TrackDeEsser(
    val enabled: Boolean = true,
    val amount: Int = 0,
    val band: DeEssBand = DeEssBand.MID,
) {
    val isActive: Boolean get() = enabled && amount > 0

    /** Where the band starts being turned down (dBFS, peak level of the band). */
    val thresholdDb: Double get() = -10.0 - 0.3 * amount.coerceIn(0, 100)

    /** How much the band is turned down (dB, positive) while its level holds at [levelDb]. */
    fun reductionDb(levelDb: Double): Double {
        if (!isActive) return 0.0
        val over = levelDb - thresholdDb
        return if (over <= 0) 0.0 else min(MAX_REDUCTION_DB, over * (1 - 1 / RATIO))
    }

    companion object {
        val OFF = TrackDeEsser()
        const val RATIO = 4.0
        const val MAX_REDUCTION_DB = 18.0
        const val ATTACK_MS = 1.0
        const val RELEASE_MS = 60.0
    }
}

/**
 * [deEsser] running at [sampleRate] over [channels] channels, one sample frame at a time: a fourth-order low-pass
 * (Linkwitz–Riley: two Butterworth stages) per channel keeps everything under the band, the band is what is left
 * (x − low), and it is scaled by the gain the band's level calls for — low + band · gain, so with no reduction the sound
 * passes exactly. (A high-pass band subtracted from the input would not do: its phase shift near the cut-off cancels most of
 * the reduction.) The level is heard through a separate second-order high-pass — a peak follower on the loudest channel,
 * fast attack, slower release — since the subtracted band leaks a little of a loud voice (the low-pass's phase lag).
 */
class DeEsserGain(private val deEsser: TrackDeEsser, sampleRate: Int, private val channels: Int) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    // Two low-pass stages' memories per channel.
    private val s1x1 = DoubleArray(channels)
    private val s1x2 = DoubleArray(channels)
    private val s1y1 = DoubleArray(channels)
    private val s1y2 = DoubleArray(channels)
    private val s2x1 = DoubleArray(channels)
    private val s2x2 = DoubleArray(channels)
    private val s2y1 = DoubleArray(channels)
    private val s2y2 = DoubleArray(channels)
    private val band = DoubleArray(channels)
    // The detector's high-pass, its coefficients and memories.
    private val h0: Double
    private val h1: Double
    private val h2: Double
    private val hx1 = DoubleArray(channels)
    private val hx2 = DoubleArray(channels)
    private val hy1 = DoubleArray(channels)
    private val hy2 = DoubleArray(channels)
    private val attack = coefficient(TrackDeEsser.ATTACK_MS, sampleRate)
    private val release = coefficient(TrackDeEsser.RELEASE_MS, sampleRate)
    private var level = 0.0

    init {
        // RBJ cookbook low-pass, Q = 1/√2 (Butterworth), cut-off well under Nyquist.
        val f = min(deEsser.band.hz, sampleRate * 0.45)
        val w = 2 * PI * f / sampleRate.coerceAtLeast(1)
        val alpha = sin(w) / (2 * (1 / sqrt(2.0)))
        val c = cos(w)
        val a0 = 1 + alpha
        b0 = (1 - c) / 2 / a0
        b1 = (1 - c) / a0
        b2 = (1 - c) / 2 / a0
        a1 = -2 * c / a0
        a2 = (1 - alpha) / a0
        // The high-pass of the same cut-off and Q shares a1, a2.
        h0 = (1 + c) / 2 / a0
        h1 = -(1 + c) / a0
        h2 = (1 + c) / 2 / a0
    }

    /** The gain applied to the band just now (1 = none), for meters and tests. */
    var bandGain: Double = 1.0
        private set

    /** De-esses one sample [frame] (full scale = [fullScale]) in place. */
    fun process(frame: FloatArray, fullScale: Double = 1.0) {
        if (!deEsser.isActive) return
        var peak = 0.0
        for (ch in 0 until channels) {
            val x = frame[ch].toDouble()
            val m = b0 * x + b1 * s1x1[ch] + b2 * s1x2[ch] - a1 * s1y1[ch] - a2 * s1y2[ch]
            s1x2[ch] = s1x1[ch]; s1x1[ch] = x
            s1y2[ch] = s1y1[ch]; s1y1[ch] = m
            val low = b0 * m + b1 * s2x1[ch] + b2 * s2x2[ch] - a1 * s2y1[ch] - a2 * s2y2[ch]
            s2x2[ch] = s2x1[ch]; s2x1[ch] = m
            s2y2[ch] = s2y1[ch]; s2y1[ch] = low
            band[ch] = x - low
            val heard = h0 * x + h1 * hx1[ch] + h2 * hx2[ch] - a1 * hy1[ch] - a2 * hy2[ch]
            hx2[ch] = hx1[ch]; hx1[ch] = x
            hy2[ch] = hy1[ch]; hy1[ch] = heard
            peak = max(peak, abs(heard) / fullScale)
        }
        val k = if (peak > level) attack else release
        level = k * level + (1 - k) * peak
        val reduction = deEsser.reductionDb(20 * log10(max(level, SILENCE)))
        bandGain = 10.0.pow(-reduction / 20)
        if (reduction <= 0.0) return
        for (ch in 0 until channels) frame[ch] = (frame[ch] - band[ch] * (1 - bandGain)).toFloat()
    }

    /** Forgets the filters and the level (a seek or a new stream). */
    fun reset() {
        listOf(s1x1, s1x2, s1y1, s1y2, s2x1, s2x2, s2y1, s2y2, hx1, hx2, hy1, hy2).forEach { it.fill(0.0) }
        level = 0.0
        bandGain = 1.0
    }

    private fun coefficient(ms: Double, sampleRate: Int): Double = exp(-1.0 / (ms / 1000.0 * sampleRate.coerceAtLeast(1)))

    private companion object {
        const val SILENCE = 1e-9
    }
}
