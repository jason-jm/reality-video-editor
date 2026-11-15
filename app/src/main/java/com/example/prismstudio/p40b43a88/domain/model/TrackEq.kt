package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A track's equaliser, Fairlight's track EQ cut down to four bands: a low cut (12 dB / octave
 * high-pass), a low shelf, a mid bell and a high shelf. It sits before the fader, on every clip of the
 * track. Frequencies are in Hz, gains in dB; [enabled] off bypasses it without losing the settings.
 */
data class TrackEq(
    val enabled: Boolean = true,
    val lowCut: Boolean = false,
    val lowCutHz: Float = 80f,
    val lowHz: Float = 120f,
    val lowDb: Float = 0f,
    val midHz: Float = 1000f,
    val midDb: Float = 0f,
    val midQ: Float = 1f,
    val highHz: Float = 8000f,
    val highDb: Float = 0f,
) {
    /** Nothing to do: no low cut and every band at 0 dB. */
    val isFlat: Boolean get() = !lowCut && lowDb == 0f && midDb == 0f && highDb == 0f

    /** It changes the sound: switched on and not flat. */
    val isActive: Boolean get() = enabled && !isFlat

    /** The biquads it runs at [sampleRate], in order (flat bands left out). */
    fun stages(sampleRate: Int): List<Biquad> = buildList {
        if (lowCut) add(Biquad.highPass(lowCutHz.toDouble(), sampleRate))
        if (lowDb != 0f) add(Biquad.lowShelf(lowHz.toDouble(), lowDb.toDouble(), sampleRate))
        if (midDb != 0f) add(Biquad.peak(midHz.toDouble(), midDb.toDouble(), midQ.toDouble(), sampleRate))
        if (highDb != 0f) add(Biquad.highShelf(highHz.toDouble(), highDb.toDouble(), sampleRate))
    }

    /** The EQ's gain in dB at [hz] (0 when bypassed): what the curve on the EQ sheet draws. */
    fun responseDb(hz: Double, sampleRate: Int = DEFAULT_RATE): Double =
        if (!enabled) 0.0 else stages(sampleRate).sumOf { it.magnitudeDb(hz, sampleRate) }

    companion object {
        val FLAT = TrackEq()
        const val DEFAULT_RATE = 48_000
        const val MIN_HZ = 20.0
        const val MAX_HZ = 20_000.0
    }
}

/**
 * One second-order IIR section, coefficients normalised by a0 (Robert Bristow-Johnson's Audio EQ
 * Cookbook): y = b0·x + b1·x₁ + b2·x₂ − a1·y₁ − a2·y₂.
 */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** |H| in dB at [hz]. */
    fun magnitudeDb(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.7071067811865476

        private fun omega(hz: Double, sampleRate: Int): Pair<Double, Double> {
            val w = 2 * PI * hz.coerceIn(1.0, sampleRate * 0.49) / sampleRate
            return cos(w) to sin(w)
        }

        private fun normalised(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun highPass(hz: Double, sampleRate: Int, q: Double = BUTTERWORTH_Q): Biquad {
            val (c, s) = omega(hz, sampleRate)
            val alpha = s / (2 * q)
            return normalised((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun peak(hz: Double, gainDb: Double, q: Double, sampleRate: Int): Biquad {
            val (c, s) = omega(hz, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val alpha = s / (2 * q.coerceAtLeast(0.05))
            return normalised(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        /** Shelf slope S = 1 (the steepest without overshoot). */
        fun lowShelf(hz: Double, gainDb: Double, sampleRate: Int): Biquad {
            val (c, s) = omega(hz, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val alpha = s / 2 * sqrt(2.0)
            val k = 2 * sqrt(a) * alpha
            return normalised(
                a * ((a + 1) - (a - 1) * c + k), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - k),
                (a + 1) + (a - 1) * c + k, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - k,
            )
        }

        fun highShelf(hz: Double, gainDb: Double, sampleRate: Int): Biquad {
            val (c, s) = omega(hz, sampleRate)
            val a = 10.0.pow(gainDb / 40)
            val alpha = s / 2 * sqrt(2.0)
            val k = 2 * sqrt(a) * alpha
            return normalised(
                a * ((a + 1) + (a - 1) * c + k), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - k),
                (a + 1) - (a - 1) * c + k, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - k,
            )
        }
    }
}

/** The EQ sheet's sliders (and their ranges); frequencies move on a logarithmic scale. */
/**
 * `eq-preset-menu`: Fairlight's EQ presets — common shapes for dialogue and music, made of the four bands. Picking one
 * replaces the curve and switches the EQ on; the sheet names the preset the curve still matches, or 自定义 once a band
 * is moved.
 */
/**
 * What [eq] does, in a few words: 已旁通, 平直, a preset's name, or its bands ("低切 80 Hz · 中频 +3 dB @ 3000 Hz").
 */
fun eqSummary(eq: TrackEq): String {
    if (eq.isFlat) return "平直"
    if (!eq.enabled) return "已旁通"
    EqPreset.of(eq)?.let { return "预设：${it.label}" }
    fun db(v: Float) = (if (v > 0f) "+" else "") + (if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)) + " dB"
    return buildList {
        if (eq.lowCut) add("低切 ${eq.lowCutHz.toInt()} Hz")
        if (eq.lowDb != 0f) add("低频 ${db(eq.lowDb)}")
        if (eq.midDb != 0f) add("中频 ${db(eq.midDb)} @ ${eq.midHz.toInt()} Hz")
        if (eq.highDb != 0f) add("高频 ${db(eq.highDb)}")
    }.joinToString(" · ")
}

enum class EqPreset(val label: String, val eq: TrackEq) {
    /** Dialogue: rumble cut, a little less boom, presence and air. */
    VOICE("人声清晰", TrackEq(lowCut = true, lowCutHz = 80f, lowHz = 150f, lowDb = -2f, midHz = 3000f, midDb = 3f, midQ = 1f, highHz = 10_000f, highDb = 2f)),

    /** A close microphone: warmth kept, the boxy middle taken down, crisp top. */
    PODCAST("播客人声", TrackEq(lowCut = true, lowCutHz = 70f, lowHz = 120f, lowDb = 2f, midHz = 350f, midDb = -3f, midQ = 1.2f, highHz = 6000f, highDb = 3f)),

    /** Wind, handling and traffic below the voice: the low cut alone. */
    RUMBLE("去低频噪声", TrackEq(lowCut = true, lowCutHz = 120f)),

    WARM("温暖", TrackEq(lowHz = 150f, lowDb = 3f, highHz = 9000f, highDb = -2f)),

    BRIGHT("明亮", TrackEq(lowHz = 120f, lowDb = -1f, highHz = 6000f, highDb = 4f)),

    /** Tape and preamp hiss: the top turned down. */
    DE_HISS("减弱嘶声", TrackEq(highHz = 7000f, highDb = -8f)),

    /** A phone line's narrow band: lows cut, a honky middle, highs gone. */
    TELEPHONE("电话音", TrackEq(lowCut = true, lowCutHz = 400f, midHz = 1500f, midDb = 6f, midQ = 0.7f, highHz = 3500f, highDb = -15f));

    companion object {
        /** The preset [eq] matches (on or bypassed alike), or null for a curve of its own. */
        fun of(eq: TrackEq): EqPreset? = entries.firstOrNull { it.eq == eq.copy(enabled = true) }
    }
}

enum class EqControl(val band: String, val label: String, val min: Float, val max: Float, val logScale: Boolean) {
    LOW_CUT_HZ("低切", "频率", 20f, 500f, true),
    LOW_HZ("低频", "频率", 40f, 500f, true),
    LOW_DB("低频", "增益", -15f, 15f, false),
    MID_HZ("中频", "频率", 200f, 8000f, true),
    MID_DB("中频", "增益", -15f, 15f, false),
    MID_Q("中频", "Q", 0.3f, 5f, false),
    HIGH_HZ("高频", "频率", 2000f, 16000f, true),
    HIGH_DB("高频", "增益", -15f, 15f, false);

    fun read(eq: TrackEq): Float = when (this) {
        LOW_CUT_HZ -> eq.lowCutHz
        LOW_HZ -> eq.lowHz
        LOW_DB -> eq.lowDb
        MID_HZ -> eq.midHz
        MID_DB -> eq.midDb
        MID_Q -> eq.midQ
        HIGH_HZ -> eq.highHz
        HIGH_DB -> eq.highDb
    }

    /** [eq] with this control at [value]: frequencies to whole Hz, gains to 0.1 dB, Q to 0.01. */
    fun write(eq: TrackEq, value: Float): TrackEq {
        val v = value.coerceIn(min, max)
        val hz = kotlin.math.round(v)
        val db = kotlin.math.round(v * 10f) / 10f
        return when (this) {
            LOW_CUT_HZ -> eq.copy(lowCutHz = hz)
            LOW_HZ -> eq.copy(lowHz = hz)
            LOW_DB -> eq.copy(lowDb = db)
            MID_HZ -> eq.copy(midHz = hz)
            MID_DB -> eq.copy(midDb = db)
            MID_Q -> eq.copy(midQ = kotlin.math.round(v * 100f) / 100f)
            HIGH_HZ -> eq.copy(highHz = hz)
            HIGH_DB -> eq.copy(highDb = db)
        }
    }
}

/** The EQ curve's draggable bands: across moves the frequency, up and down the gain (the low cut has none). */
enum class EqBand(val label: String, val frequency: EqControl, val gain: EqControl?) {
    LOW_CUT("低切", EqControl.LOW_CUT_HZ, null),
    LOW("低频", EqControl.LOW_HZ, EqControl.LOW_DB),
    MID("中频", EqControl.MID_HZ, EqControl.MID_DB),
    HIGH("高频", EqControl.HIGH_HZ, EqControl.HIGH_DB);

    fun frequencyOf(eq: TrackEq): Float = frequency.read(eq)

    /** The handle's height: the band's gain, or the curve's level at the low cut's corner. */
    fun gainOf(eq: TrackEq): Float = gain?.read(eq) ?: eq.responseDb(eq.lowCutHz.toDouble()).toFloat()

    /** [eq] with this band dragged to [hz] and [db] (each clamped to its control's range). */
    fun drag(eq: TrackEq, hz: Float, db: Float): TrackEq {
        val moved = frequency.write(eq, hz)
        return gain?.write(moved, db) ?: moved.copy(lowCut = true)
    }
}
