package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/**
 * A track's ducking — Fairlight's ducker, keyed from another track: while the sound on [keyTrackId] (the
 * dialogue) is at or above [thresholdDb], this track (the music) dips by [depthDb]. The timeline is
 * edited, not live, so the key is known ahead and the dip is placed the way an editor would key the
 * music's volume: it starts going down [attackMs] before the dialogue so the music is down by the first
 * word, stays down through pauses up to [HOLD_MS], and comes back over [releaseMs] after the last word.
 *
 * The key is the key track's clips as they play before its fader — clip gain, volume keyframes and
 * fades, but not the fader, mute or solo, so soloing the music still plays its dips. [enabled] off
 * bypasses the ducking without losing it.
 */
data class TrackDucking(
    val enabled: Boolean = true,
    val keyTrackId: String? = null,
    val depthDb: Float = 12f,
    val thresholdDb: Float = -40f,
    val attackMs: Float = 200f,
    val releaseMs: Float = 800f,
) {
    /** Switched on with a key track chosen. */
    val isOn: Boolean get() = enabled && keyTrackId != null

    /** It changes the sound (as long as its key track exists). */
    val isActive: Boolean get() = isOn && depthDb > 0f

    companion object {
        val OFF = TrackDucking()

        /** Pauses in the key up to this long keep the track down (between words, between sentences). */
        const val HOLD_MS = 400
    }
}

/** The ducking sheet's sliders (and their ranges); attack and release move on a logarithmic scale. */
enum class DuckingControl(val label: String, val min: Float, val max: Float, val logScale: Boolean) {
    DEPTH("深度", 0f, 30f, false),
    THRESHOLD("阈值", -60f, -10f, false),
    ATTACK("起音", 10f, 1000f, true),
    RELEASE("释放", 50f, 5000f, true);

    fun read(d: TrackDucking): Float = when (this) {
        DEPTH -> d.depthDb
        THRESHOLD -> d.thresholdDb
        ATTACK -> d.attackMs
        RELEASE -> d.releaseMs
    }

    /** [d] with this control at [value]: levels to 0.1 dB, times to whole milliseconds. */
    fun write(d: TrackDucking, value: Float): TrackDucking {
        val v = value.coerceIn(min, max)
        return when (this) {
            DEPTH -> d.copy(depthDb = round(v * 10f) / 10f)
            THRESHOLD -> d.copy(thresholdDb = round(v * 10f) / 10f)
            ATTACK -> d.copy(attackMs = round(v))
            RELEASE -> d.copy(releaseMs = round(v))
        }
    }

    /** Slider position (0..1) of [value] on a logarithmic control; the value itself otherwise. */
    fun position(value: Float): Float = if (!logScale) value else ln(value / min) / ln(max / min)

    /** The value at slider position [t] (0..1) of a logarithmic control; [t] itself otherwise. */
    fun valueAt(t: Float): Float = if (!logScale) t else min * (max / min).pow(t)
}

/**
 * How far a ducked track is down over the timeline: an amount every [stepUs] from the timeline's start
 * (0 = not at all, 1 = the full depth), linear in between; after the last one it is up.
 */
class DuckEnvelope(val stepUs: Long, val amounts: FloatArray) {

    fun amountAt(timeUs: Long): Float {
        if (amounts.isEmpty() || timeUs < 0) return 0f
        val x = timeUs.toDouble() / stepUs
        val i = floor(x).toInt()
        if (i >= amounts.lastIndex) return 0f
        val a = amounts[i]
        return a + (amounts[i + 1] - a) * (x - i).toFloat()
    }

    /** The gain at [timeUs] for a dip of [depthDb]: the dip in dB follows the amount. */
    fun gainAt(timeUs: Long, depthDb: Float): Float {
        if (depthDb <= 0f) return 1f
        val amount = amountAt(timeUs)
        return if (amount <= 0f) 1f else 10f.pow(-depthDb * amount / 20f)
    }
}

/**
 * Starting points for the ducking sheet (as its EQ and dynamics have): how deep, from how quiet a key, how fast down and
 * back up. A preset replaces those four and keeps the switch and the key track.
 */
enum class DuckingPreset(val label: String, val depthDb: Float, val thresholdDb: Float, val attackMs: Float, val releaseMs: Float) {
    /** Music out of the way of on-camera speech, quickly. */
    DIALOGUE("对白", 15f, -45f, 80f, 600f),

    /** A narrator over music: the sheet's own starting point. */
    VOICE_OVER("旁白", 12f, -40f, 200f, 800f),

    /** Just a nudge under the voice. */
    SUBTLE("轻微", 6f, -40f, 300f, 1200f),

    /** The music all but gone while anyone speaks. */
    DEEP("深度压低", 24f, -45f, 50f, 1500f),

    /** A bed under talk that stays down through the pauses and comes back slowly. */
    PODCAST("播客垫乐", 18f, -50f, 150f, 2000f);

    /** [d] with this preset's four settings, its switch and key kept. */
    fun apply(d: TrackDucking): TrackDucking = d.copy(depthDb = depthDb, thresholdDb = thresholdDb, attackMs = attackMs, releaseMs = releaseMs)

    companion object {
        /** The preset [d]'s settings match, or null for settings of their own. */
        fun of(d: TrackDucking): DuckingPreset? =
            entries.firstOrNull { it.depthDb == d.depthDb && it.thresholdDb == d.thresholdDb && it.attackMs == d.attackMs && it.releaseMs == d.releaseMs }
    }
}
