package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Fairlight FX Reverb's rooms: each sets the decay, pre-delay, damping and mix at once. */
enum class ReverbRoom(val label: String, val decaySeconds: Float, val preDelayMs: Int, val damping: Int, val mix: Int) {
    ROOM("小房间", 0.6f, 8, 50, 20),
    HALL("大厅", 2.2f, 25, 40, 25),
    CHURCH("教堂", 4.5f, 45, 30, 30),
    PLATE("板式", 1.4f, 0, 15, 25),
}

/**
 * A track's reverb (Fairlight FX Reverb): the sound rings on in a room — [decaySeconds] is the time it takes to fall
 * 60 dB (RT60), [preDelayMs] the wait before the room answers, [damping] how much faster the highs die away (0–100 %),
 * [mix] how much of what is heard is the room (0 % dry … 100 % only the room; 0 = off). It acts on every clip of the
 * track after its dynamics and fader; where silence follows a clip the tail rings on into it.
 */
data class TrackReverb(
    val enabled: Boolean = true,
    val decaySeconds: Float = 1.5f,
    val preDelayMs: Int = 20,
    val damping: Int = 40,
    val mix: Int = 0,
) {
    val isActive: Boolean get() = enabled && mix > 0

    /** The room these settings are, if any. */
    val room: ReverbRoom?
        get() = ReverbRoom.entries.firstOrNull {
            abs(it.decaySeconds - decaySeconds) < 0.01f && it.preDelayMs == preDelayMs && it.damping == damping && it.mix == mix
        }

    fun withRoom(room: ReverbRoom): TrackReverb =
        TrackReverb(enabled = true, decaySeconds = room.decaySeconds, preDelayMs = room.preDelayMs, damping = room.damping, mix = room.mix)

    /**
     * How long a sound at [hz] takes to fall 60 dB: [decaySeconds] for low sounds; higher up the damping low-pass in each
     * comb's loop takes a little more off every pass — about 32 passes a second at the combs' mean length.
     */
    fun decayAt(hz: Double, sampleRate: Int = 44_100): Double {
        val r = clamped()
        val d = r.damping / 100.0 * 0.4
        if (d <= 0.0) return r.decaySeconds.toDouble()
        val w = 2 * Math.PI * hz / sampleRate
        val h = (1 - d) / sqrt(1 - 2 * d * kotlin.math.cos(w) + d * d)
        // The combs' lengths follow the rate, so the passes a second do not.
        val passes = 44_100.0 / MEAN_COMB_44K
        val dbPerSecond = 60.0 / r.decaySeconds - 20 * kotlin.math.log10(h) * passes
        return 60.0 / dbPerSecond
    }

    /** Each setting held in its range. */
    fun clamped(): TrackReverb = copy(
        decaySeconds = decaySeconds.coerceIn(MIN_DECAY_SECONDS, MAX_DECAY_SECONDS),
        preDelayMs = preDelayMs.coerceIn(0, MAX_PRE_DELAY_MS),
        damping = damping.coerceIn(0, 100),
        mix = mix.coerceIn(0, 100),
    )

    companion object {
        val OFF = TrackReverb()
        const val MIN_DECAY_SECONDS = 0.3f
        const val MAX_DECAY_SECONDS = 6f
        const val MAX_PRE_DELAY_MS = 200

        /**
         * How far a clip's sound may ring on into the silence after it: the longest decay and pre-delay, whatever the
         * settings, so that changing them plays at once instead of rebuilding the sound.
         */
        const val TAIL_SECONDS = 6.5f

        /** The combs' mean length at 44.1 kHz (samples). */
        const val MEAN_COMB_44K = 1378.0
    }
}

/**
 * [TrackReverb] at [sampleRate], one stereo frame at a time: the frame's two sides summed to one input, delayed by the
 * pre-delay, into eight damped feedback combs per side (Schroeder–Moorer, Freeverb's tunings; the right side's a little
 * longer, so a centred sound comes back wide) and four true allpasses in series (Freeverb's own add ~3.7 dB each). Each comb feeds back 10^(−3·d / RT60) for its
 * delay d, so every comb falls 60 dB in the decay time; a one-pole low-pass in the loop takes the highs down faster
 * ([TrackReverb.damping]). The input is scaled so that a steady sound's room is as loud as the sound, whatever the decay.
 * The tank keeps ringing across buffers and is handed from one item to the next (see `ReverbLink`).
 */
class ReverbTank(val sampleRate: Int) {
    private val scale = sampleRate / 44_100.0
    private val left = COMBS.map { Comb((it * scale).roundToInt().coerceAtLeast(1)) }
    private val right = COMBS.map { Comb(((it + SPREAD) * scale).roundToInt().coerceAtLeast(1)) }
    private val leftPasses = ALLPASSES.map { AllPass((it * scale).roundToInt().coerceAtLeast(1)) }
    private val rightPasses = ALLPASSES.map { AllPass(((it + SPREAD) * scale).roundToInt().coerceAtLeast(1)) }
    private val pre = FloatArray((TrackReverb.MAX_PRE_DELAY_MS * sampleRate / 1000.0).roundToInt() + 1)
    private var preIndex = 0
    private var preFrames = 0
    private var inputGain = 0f
    private var dry = 1f
    private var wet = 0f
    private var settings: TrackReverb? = null

    /** Takes [reverb]'s settings; what is ringing rings on. */
    fun tune(reverb: TrackReverb) {
        if (reverb == settings) return
        settings = reverb
        val r = reverb.clamped()
        val damp = r.damping / 100f * MAX_DAMP
        var energy = 0.0
        for (comb in left + right) {
            val g = 10.0.pow(-3.0 * comb.length / (r.decaySeconds * sampleRate))
            comb.feedback = g.toFloat()
            comb.damp = damp
        }
        // A side's eight combs, each giving 1 / (1 − g²) of white noise's energy back (the allpasses keep it).
        for (comb in left) energy += 1.0 / (1.0 - comb.feedback.toDouble() * comb.feedback)
        inputGain = (1.0 / sqrt(energy)).toFloat()
        preFrames = (r.preDelayMs * sampleRate / 1000.0).roundToInt().coerceIn(0, pre.size - 1)
        wet = r.mix / 100f
        dry = 1f - wet
    }

    /** [frame]'s first two samples (left, right) through the room, in place. */
    fun process(frame: FloatArray) {
        val x = (frame[0] + frame[1]) * 0.5f
        pre[preIndex] = x
        var read = preIndex - preFrames
        if (read < 0) read += pre.size
        val input = pre[read] * inputGain
        preIndex = if (preIndex + 1 == pre.size) 0 else preIndex + 1
        var l = 0f
        var r = 0f
        for (c in left) l += c.process(input)
        for (c in right) r += c.process(input)
        for (p in leftPasses) l = p.process(l)
        for (p in rightPasses) r = p.process(r)
        frame[0] = frame[0] * dry + l * wet
        frame[1] = frame[1] * dry + r * wet
    }

    /** Silence: nothing rings any more. */
    fun clear() {
        pre.fill(0f)
        preIndex = 0
        left.forEach { it.clear() }
        right.forEach { it.clear() }
        leftPasses.forEach { it.clear() }
        rightPasses.forEach { it.clear() }
    }

    private class Comb(val length: Int) {
        private val buffer = FloatArray(length)
        private var index = 0
        private var store = 0f
        var feedback = 0f
        var damp = 0f

        fun process(input: Float): Float {
            val y = buffer[index]
            store = y * (1f - damp) + store * damp
            if (abs(store) < DENORMAL) store = 0f
            buffer[index] = input + store * feedback
            index = if (index + 1 == length) 0 else index + 1
            return y
        }

        fun clear() {
            buffer.fill(0f)
            store = 0f
            index = 0
        }
    }

    private class AllPass(val length: Int) {
        private val buffer = FloatArray(length)
        private var index = 0

        /** A true Schroeder allpass, (−g + z^−N) / (1 − g·z^−N): it smears the echoes without changing their energy. */
        fun process(input: Float): Float {
            val b = buffer[index]
            val out = b - ALLPASS_FEEDBACK * input
            var next = input + ALLPASS_FEEDBACK * out
            if (abs(next) < DENORMAL) next = 0f
            buffer[index] = next
            index = if (index + 1 == length) 0 else index + 1
            return out
        }

        fun clear() {
            buffer.fill(0f)
            index = 0
        }
    }

    private companion object {
        /** Freeverb's comb and allpass lengths at 44.1 kHz, and the right side's extra. */
        val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASSES = intArrayOf(556, 441, 341, 225)
        const val SPREAD = 23
        const val ALLPASS_FEEDBACK = 0.5f
        const val MAX_DAMP = 0.4f
        const val DENORMAL = 1e-20f
    }
}
