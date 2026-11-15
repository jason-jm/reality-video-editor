package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * A track's dynamics, Fairlight's Dynamics: an expander/gate, a compressor and a limiter, in that order. They act after
 * the track's EQ and before its fader, on every clip of the track. The gate turns down what falls below
 * [gateThresholdDb] (its RMS level over 10 ms) — 4:1 below it (a downward expander), by at most [gateRangeDb] — opening at once, holding open for
 * [gateHoldMs] after the level last reached the threshold and closing over [gateReleaseMs]: the room tone and spill
 * between words go quiet. The compressor turns down what rises above [thresholdDb] by [ratio] (with a soft knee), clamping
 * down over [attackMs] and letting go over [releaseMs], then adds [makeupDb]; the limiter keeps every peak under
 * [ceilingDb]. [enabled] off bypasses all three without losing them.
 */
data class TrackDynamics(
    val enabled: Boolean = true,
    val compressor: Boolean = false,
    val thresholdDb: Float = -20f,
    val ratio: Float = 4f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 150f,
    val makeupDb: Float = 0f,
    val limiter: Boolean = false,
    val ceilingDb: Float = -1f,
    val gate: Boolean = false,
    val gateThresholdDb: Float = -50f,
    val gateRangeDb: Float = 30f,
    val gateHoldMs: Float = 50f,
    val gateReleaseMs: Float = 150f,
) {
    /** None of the gate, the compressor and the limiter is on. */
    val isOff: Boolean get() = !gate && !compressor && !limiter

    /** It changes the sound: switched on with a gate, a compressor or a limiter. */
    val isActive: Boolean get() = enabled && !isOff

    /** The gate's gain change (dB, never positive) for a steady level of [inputDb]: 4:1 below the threshold, at most the range. */
    fun gating(inputDb: Double): Double {
        val under = gateThresholdDb - inputDb
        return if (under <= 0) 0.0 else -min(gateRangeDb.toDouble(), under * (GATE_RATIO - 1))
    }

    /** The compressor's gain change (dB, never positive) for a steady level of [inputDb], before make-up. */
    fun compression(inputDb: Double): Double {
        val over = inputDb - thresholdDb
        val slope = 1.0 / ratio - 1.0
        return when {
            2 * over < -KNEE_DB -> 0.0
            2 * abs(over) <= KNEE_DB -> slope * (over + KNEE_DB / 2).pow(2) / (2 * KNEE_DB)
            else -> slope * over
        }
    }

    /** The steady output level for a steady input level of [inputDb]: what the transfer graph draws. */
    fun outputDb(inputDb: Double): Double {
        if (!enabled) return inputDb
        var level = inputDb
        if (gate) level += gating(inputDb)
        if (compressor) level += compression(level) + makeupDb
        if (limiter) level = min(level, ceilingDb.toDouble())
        return level
    }

    /** The steady gain change (dB) at [inputDb]: negative where it turns the track down. */
    fun gainChangeDb(inputDb: Double): Double = outputDb(inputDb) - inputDb

    companion object {
        val OFF = TrackDynamics()

        /** Width of the compressor's soft knee around the threshold. */
        const val KNEE_DB = 6.0

        /** How fast the limiter lets go after a peak. */
        const val LIMITER_RELEASE_MS = 50.0

        /** The gate's slope below its threshold (a downward expander: 1 dB down becomes 4). */
        const val GATE_RATIO = 4.0

        /** How fast the gate opens. */
        const val GATE_ATTACK_MS = 1.0
    }
}

/**
 * [dynamics] running at [sampleRate], one sample frame at a time: a peak detector (the loudest channel,
 * so stereo stays in place) whose gain change follows the attack and release in dB, then the limiter,
 * which clamps at once and lets go over [TrackDynamics.LIMITER_RELEASE_MS].
 */
class DynamicsGain(private val dynamics: TrackDynamics, sampleRate: Int) {
    private val attack = coefficient(dynamics.attackMs.toDouble(), sampleRate)
    private val release = coefficient(dynamics.releaseMs.toDouble(), sampleRate)
    private val limiterRelease = coefficient(TrackDynamics.LIMITER_RELEASE_MS, sampleRate)
    private val gateAttack = coefficient(TrackDynamics.GATE_ATTACK_MS, sampleRate)
    private val gateRelease = coefficient(dynamics.gateReleaseMs.toDouble(), sampleRate)
    private val holdFrames = (dynamics.gateHoldMs / 1000.0 * sampleRate).toLong()
    private var reduction = 0.0
    private var limiting = 0.0
    private val levelSmoothing = 1 - coefficient(GATE_LEVEL_MS, sampleRate)
    private var gating = 0.0
    /** Sample frames since the level last reached the gate's threshold. */
    private var sinceOpen = Long.MAX_VALUE / 2
    /**
     * The gate's level: the mean square over about [GATE_LEVEL_MS] — what a listener hears as loud, not single peaks (room
     * tone's peaks stand 10 dB above its level and would hold the gate open), and smooth through a waveform's zero crossings.
     */
    private var meanSquare = 0.0

    /** The gain for a sample frame whose loudest channel reaches [peak] (full scale = 1). */
    fun gain(peak: Double): Double {
        if (!dynamics.isActive) return 1.0
        val inputDb = 20 * log10(max(peak, SILENCE))
        var gainDb = 0.0
        if (dynamics.gate) {
            meanSquare += (peak * peak - meanSquare) * levelSmoothing
            val levelDb = 10 * log10(max(meanSquare, SILENCE * SILENCE))
            // Open while the level reaches the threshold and for the hold after; then down 4:1 below it, at most the range.
            if (levelDb >= dynamics.gateThresholdDb) sinceOpen = 0 else sinceOpen++
            val gateTarget = if (sinceOpen <= holdFrames) 0.0 else dynamics.gating(levelDb)
            val k = if (gateTarget > gating) gateAttack else gateRelease
            gating = k * gating + (1 - k) * gateTarget
            gainDb += gating
        }
        if (dynamics.compressor) {
            val target = dynamics.compression(inputDb + gainDb)
            val k = if (target < reduction) attack else release
            reduction = k * reduction + (1 - k) * target
            gainDb += reduction + dynamics.makeupDb
        }
        if (dynamics.limiter) {
            val over = min(0.0, dynamics.ceilingDb - (inputDb + gainDb))
            limiting = if (over < limiting) over else limiterRelease * limiting + (1 - limiterRelease) * over
            gainDb += limiting
        }
        return 10.0.pow(gainDb / 20)
    }

    /** Forgets the envelope (a seek or a new stream). */
    fun reset() {
        reduction = 0.0
        limiting = 0.0
        gating = 0.0
        meanSquare = 0.0
        sinceOpen = Long.MAX_VALUE / 2
    }

    private fun coefficient(ms: Double, sampleRate: Int): Double = exp(-1.0 / (ms / 1000.0 * sampleRate.coerceAtLeast(1)))

    private companion object {
        const val SILENCE = 1e-9

        /** The span the gate's level (an RMS) is taken over. */
        const val GATE_LEVEL_MS = 10.0
    }
}

/**
 * `dynamics-preset-menu`: Fairlight's dynamics presets — gate, compressor and limiter settings for common jobs. Picking one
 * replaces the settings and switches the dynamics on; the sheet names the preset they still match, or 自定义.
 */
enum class DynamicsPreset(val label: String, val dynamics: TrackDynamics) {
    /** Dialogue held steady, its peaks caught. */
    VOICE("人声压缩", TrackDynamics(compressor = true, thresholdDb = -24f, ratio = 3f, attackMs = 10f, releaseMs = 150f, makeupDb = 4f, limiter = true, ceilingDb = -1f)),

    /** A light hold that keeps the life in a performance. */
    GENTLE("柔和压缩", TrackDynamics(compressor = true, thresholdDb = -18f, ratio = 2f, attackMs = 30f, releaseMs = 300f, makeupDb = 2f)),

    /** A voice made dense and forward, as podcasts sound. */
    HEAVY("强力压缩", TrackDynamics(compressor = true, thresholdDb = -30f, ratio = 6f, attackMs = 5f, releaseMs = 100f, makeupDb = 8f, limiter = true, ceilingDb = -1f)),

    BROADCAST("广播", TrackDynamics(compressor = true, thresholdDb = -20f, ratio = 4f, attackMs = 10f, releaseMs = 200f, makeupDb = 6f, limiter = true, ceilingDb = -2f)),

    /** Music held together, gently. */
    MUSIC("音乐整合", TrackDynamics(compressor = true, thresholdDb = -14f, ratio = 1.5f, attackMs = 30f, releaseMs = 250f, makeupDb = 1f)),

    /** Only the peaks stopped at -1 dB. */
    LIMITER("峰值限制", TrackDynamics(limiter = true, ceilingDb = -1f)),

    /** The room tone and spill between lines taken 20 dB down, the lines themselves untouched. */
    DIALOGUE_GATE("对白门限", TrackDynamics(gate = true, gateThresholdDb = -45f, gateRangeDb = 20f, gateHoldMs = 60f, gateReleaseMs = 150f));

    companion object {
        /** The preset [dynamics] match (on or bypassed alike), or null for settings of their own. */
        fun of(dynamics: TrackDynamics): DynamicsPreset? = entries.firstOrNull { it.dynamics == dynamics.copy(enabled = true) }
    }
}

/** The dynamics sheet's sliders (and their ranges); attack and release move on a logarithmic scale. */
enum class DynamicsControl(val section: String, val label: String, val min: Float, val max: Float, val logScale: Boolean) {
    THRESHOLD("压缩器", "阈值", -50f, 0f, false),
    RATIO("压缩器", "比率", 1f, 20f, false),
    ATTACK("压缩器", "起音", 0.1f, 100f, true),
    RELEASE("压缩器", "释放", 10f, 2000f, true),
    MAKEUP("压缩器", "补偿", 0f, 24f, false),
    CEILING("限制器", "上限", -20f, 0f, false),
    GATE_THRESHOLD("门限", "阈值", -80f, 0f, false),
    GATE_RANGE("门限", "范围", 0f, 80f, false),
    GATE_HOLD("门限", "保持", 0f, 500f, false),
    GATE_RELEASE("门限", "释放", 10f, 2000f, true);

    fun read(d: TrackDynamics): Float = when (this) {
        THRESHOLD -> d.thresholdDb
        RATIO -> d.ratio
        ATTACK -> d.attackMs
        RELEASE -> d.releaseMs
        MAKEUP -> d.makeupDb
        CEILING -> d.ceilingDb
        GATE_THRESHOLD -> d.gateThresholdDb
        GATE_RANGE -> d.gateRangeDb
        GATE_HOLD -> d.gateHoldMs
        GATE_RELEASE -> d.gateReleaseMs
    }

    /** [d] with this control at [value]: levels to 0.1 dB, the ratio to 0.1, times to 0.1 ms under 10 ms, else whole ms. */
    fun write(d: TrackDynamics, value: Float): TrackDynamics {
        val v = value.coerceIn(min, max)
        val tenth = round(v * 10f) / 10f
        val ms = if (v < 10f) tenth else round(v)
        return when (this) {
            THRESHOLD -> d.copy(thresholdDb = tenth)
            RATIO -> d.copy(ratio = tenth)
            ATTACK -> d.copy(attackMs = ms)
            RELEASE -> d.copy(releaseMs = ms)
            MAKEUP -> d.copy(makeupDb = tenth)
            CEILING -> d.copy(ceilingDb = tenth)
            GATE_THRESHOLD -> d.copy(gateThresholdDb = tenth)
            GATE_RANGE -> d.copy(gateRangeDb = tenth)
            GATE_HOLD -> d.copy(gateHoldMs = round(v))
            GATE_RELEASE -> d.copy(gateReleaseMs = ms)
        }
    }

    /** Slider position (0..1) of [value] on a logarithmic control. */
    fun position(value: Float): Float = if (!logScale) value else (ln(value / min) / ln(max / min))

    /** The value at slider position [t] (0..1) of a logarithmic control. */
    fun valueAt(t: Float): Float = if (!logScale) t else min * (max / min).pow(t)
}
