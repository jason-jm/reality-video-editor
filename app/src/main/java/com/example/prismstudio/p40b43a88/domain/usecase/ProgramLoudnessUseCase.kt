package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.LoudnessAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SHORT_TERM_BLOCKS
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.WINDOW_BLOCKS
import com.example.prismstudio.p40b43a88.domain.model.gatedLoudness
import com.example.prismstudio.p40b43a88.domain.model.loudnessRange
import com.example.prismstudio.p40b43a88.domain.model.lufsOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * Fairlight's loudness meter for the whole mix. The program as the export plays it, in BS.1770 100 ms
 * blocks over the timeline (see [programBlocks]), and from them the momentary (400 ms) and short-term
 * (3 s) loudness at any moment, the integrated loudness and EBU loudness range of the whole program, and
 * its true peak.
 */
class ProgramLoudnessUseCase {

    operator fun invoke(
        doc: ProjectDocument,
        profiles: Map<String, LoudnessProfile>,
        envelopes: Map<String, DuckEnvelope> = emptyMap(),
    ): ProgramLoudness {
        val untilMs = doc.durationFrames * 1000.0 / doc.format.fps.coerceAtLeast(1)
        val (energy, peak) = programBlocks(doc, profiles, envelopes, 0.0, untilMs)
        return ProgramLoudness(energy, peak)
    }
}

/** The mixed program's 100 ms blocks from the timeline's start ([energy] K-weighted mean squares, [peak] true peaks). */
class ProgramLoudness(val energy: DoubleArray, private val peak: DoubleArray) {
    /** Integrated loudness of the whole program (LUFS); null when silent or shorter than 400 ms. */
    val integratedLufs: Double? = gatedLoudness(energy)

    /** Loudness range (LU); null without 3 s of sound. */
    val rangeLu: Double? = loudnessRange(energy)

    /** The highest true peak (dBTP, an upper bound: clips' peaks add up); null when silent. */
    val truePeakDbtp: Double? = peak.maxOrNull()?.takeIf { it > 0.0 }?.let { 20.0 * log10(it) }

    /** Loudness of the 400 ms up to [ms] on the timeline (silence before the start); null below −70 LUFS. */
    fun momentaryAt(ms: Double): Double? = windowEnding(ms, WINDOW_BLOCKS)

    /** Loudness of the 3 s up to [ms]; null below −70 LUFS. */
    fun shortTermAt(ms: Double): Double? = windowEnding(ms, SHORT_TERM_BLOCKS)

    /**
     * [n] readings, one per 100 ms block, of the [seconds] up to [ms] — the short-term loudness, or the
     * momentary with [momentary] — oldest first; NaN where it is silent.
     */
    fun history(ms: Double, seconds: Int, momentary: Boolean = false): FloatArray {
        val n = seconds * BLOCKS_PER_SECOND
        val end = lastBlock(ms)
        val window = if (momentary) WINDOW_BLOCKS else SHORT_TERM_BLOCKS
        return FloatArray(n) { i -> (windowAt(end - (n - 1 - i), window) ?: Double.NaN).toFloat() }
    }

    private fun windowEnding(ms: Double, blocks: Int): Double? = windowAt(lastBlock(ms), blocks)

    /** The block holding [ms] (or the one before, at a block boundary). */
    private fun lastBlock(ms: Double): Int = ceil(ms / BLOCK_MS).toInt() - 1

    private fun windowAt(end: Int, blocks: Int): Double? {
        if (end < 0) return null
        var sum = 0.0
        for (b in (end - blocks + 1).coerceAtLeast(0)..end) if (b < energy.size) sum += energy[b]
        if (sum <= 0.0) return null
        return lufsOf(sum / blocks).takeIf { it > FLOOR_LUFS }
    }

    companion object {
        private const val BLOCK_MS = LoudnessAnalyzer.BLOCK_MS.toDouble()
        const val BLOCKS_PER_SECOND = 10
        /** Below the BS.1770 absolute gate a meter reads nothing. */
        const val FLOOR_LUFS = -70.0
    }
}

/**
 * The mixed program between [fromMs] and [untilMs] (timeline time) in 100 ms blocks, as the export plays
 * it: every audible sound clip on an enabled track with its gain and fades; on each track its dynamics at
 * their steady level for the block's peak (as the meters show them), its ducking dip from [envelopes]
 * when ducking is on, its fader and balance pan; then the master. Clips add up as uncorrelated sounds;
 * peaks add (an upper bound). EQ is not included; media without a profile in [profiles] is silent.
 */
internal fun programBlocks(
    doc: ProjectDocument,
    profiles: Map<String, LoudnessProfile>,
    envelopes: Map<String, DuckEnvelope>,
    fromMs: Double,
    untilMs: Double,
): Pair<DoubleArray, DoubleArray> {
    val block = LoudnessAnalyzer.BLOCK_MS.toDouble()
    val msPerFrame = 1000.0 / doc.format.fps.coerceAtLeast(1)
    val count = ceil((untilMs - fromMs) / block).toInt().coerceAtLeast(0)
    val energy = DoubleArray(count)
    val peak = DoubleArray(count)
    val trackEnergy = DoubleArray(count)
    val trackPeak = DoubleArray(count)
    val master = gainOf(doc.mixer.masterVolumeDb)
    for (track in doc.timeline.tracks) {
        if (track.kind != TrackKind.AUDIO || !track.enabled || !doc.mixer.isAudible(track.id)) continue
        trackEnergy.fill(0.0)
        trackPeak.fill(0.0)
        var sounding = false
        for (clip in doc.timeline.clipsOn(track.id)) {
            if (clip.kind != ClipKind.AUDIO || !clip.enabled) continue
            val profile = clip.assetId?.let { profiles[it] } ?: continue
            addClipBlocks(clip, profile, msPerFrame, fromMs, 1.0, 1.0, trackEnergy, trackPeak)
            sounding = true
        }
        if (!sounding) continue
        val channel = doc.mixer.channel(track.id)
        // The track's fader and its submix bus's.
        val lane = doc.mixer.laneGain(track.id) * master
        // A balance pan turns one side down: the share of the stereo energy that is left.
        val panEnergy = panEnergy(channel.pan)
        val dynamics = channel.dynamics.takeIf { it.isActive }
        val duck = envelopes[track.id]?.takeIf { channel.ducking.isActive }
        for (b in 0 until count) {
            if (trackEnergy[b] <= 0.0 && trackPeak[b] <= 0.0) continue
            var gain = lane
            if (dynamics != null && trackPeak[b] > 0.0) gain *= 10.0.pow(dynamics.gainChangeDb(20.0 * log10(trackPeak[b])) / 20.0)
            if (duck != null) gain *= duck.gainAt(((fromMs + (b + 0.5) * block) * 1000).toLong(), channel.ducking.depthDb).toDouble()
            energy[b] += trackEnergy[b] * gain * gain * panEnergy
            peak[b] += trackPeak[b] * gain
        }
    }
    return energy to peak
}

private fun gainOf(db: Float): Double = if (db <= -60f) 0.0 else 10.0.pow(db / 20.0)
