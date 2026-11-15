package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panAt
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.LoudnessAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.WINDOW_BLOCKS
import com.example.prismstudio.p40b43a88.domain.model.gatedLoudness
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Loudness of a sound clip as the mix plays it (ITU-R BS.1770 integrated loudness, from the source's
 * [LoudnessProfile] under the clip with its level, volume keyframes and fades applied), and Resolve's
 * Normalize Audio Levels: one gain change that brings the clip to a target loudness. Track faders and
 * the master are mix decisions and stay out of it.
 */
class LoudnessUseCase {

    sealed interface Reading {
        data class Measured(val lufs: Float, val truePeakDbtp: Float) : Reading
        /** Shorter than one 400 ms gating window. */
        data object TooShort : Reading
        /** Every window is below the −70 LUFS gate. */
        data object Silent : Reading
    }

    fun measure(clip: Clip, profile: LoudnessProfile, fps: Int): Reading {
        val msPerFrame = 1000.0 / fps.coerceAtLeast(1)
        val speed = clip.speed.factor.toDouble()
        val sourceStart = clip.sourceInMs.toDouble()
        val sourceLength = if (clip.retimed) clip.sourceOffsetMs(clip.durationFrames, fps) else clip.durationFrames * msPerFrame * speed
        val block = profile.blockMs.toDouble()
        val energy = ArrayList<Double>()
        var peak = 0.0
        // Every block whose centre lies under the clip, weighted by the clip's gain at that moment.
        var j = ceil(sourceStart / block - 0.5).toInt().coerceAtLeast(0)
        while (j < profile.blocks) {
            val intoSource = (j + 0.5) * block - sourceStart
            if (intoSource >= sourceLength) break
            // Speed points (forward clips only) map the source back through their sections.
            val clipMs = when {
                clip.retimed -> clip.framesAtSourceMs(intoSource, fps) * msPerFrame
                else -> (if (clip.reverse) sourceLength - intoSource else intoSource) / speed
            }
            val gain = gainAt(clip, clipMs, msPerFrame)
            // As the mix plays it: the clip's channel mapping counted (one side on both, a mono fold).
            energy += profile.energyOf(j, clip.channels) * gain * gain
            peak = max(peak, profile.peakOf(j, clip.channels) * gain)
            j++
        }
        if (energy.size < WINDOW_BLOCKS) return Reading.TooShort
        val lufs = gatedLoudness(energy.toDoubleArray()) ?: return Reading.Silent
        return Reading.Measured(lufs.toFloat(), (20 * log10(peak)).toFloat())
    }

    /**
     * The mixed program's loudness between [rangeStart] and [rangeEnd] (timeline frames; null: the whole
     * timeline) as the export plays it (see [programBlocks]: clip gains, each track's dynamics at their
     * steady level and its ducking dip from [envelopes], faders, balance pan, the master). Clips add up as
     * uncorrelated sounds; the peak is an upper bound (their peaks added). Clips whose media has no entry
     * in [profiles] are silent.
     */
    fun program(
        doc: ProjectDocument,
        profiles: Map<String, LoudnessProfile>,
        rangeStart: Int? = null,
        rangeEnd: Int? = null,
        envelopes: Map<String, DuckEnvelope> = emptyMap(),
    ): Reading {
        val msPerFrame = 1000.0 / doc.format.fps.coerceAtLeast(1)
        val from = (rangeStart ?: 0) * msPerFrame
        val until = (rangeEnd ?: doc.durationFrames) * msPerFrame
        val (energy, peak) = programBlocks(doc, profiles, envelopes, from, until)
        if (energy.size < WINDOW_BLOCKS) return Reading.TooShort
        val lufs = gatedLoudness(energy) ?: return Reading.Silent
        return Reading.Measured(lufs.toFloat(), (20 * log10(peak.max())).toFloat())
    }

    /**
     * [doc] with its master moved by the gain that brings the program (frames [rangeStart] until
     * [rangeEnd]) to [targetLufs] — at most [MAX_RENDER_GAIN_DB] up — for an export with Normalize Audio;
     * null when there is nothing to measure.
     */
    fun normalizedForExport(
        doc: ProjectDocument,
        profiles: Map<String, LoudnessProfile>,
        targetLufs: Float,
        rangeStart: Int?,
        rangeEnd: Int?,
        envelopes: Map<String, DuckEnvelope> = emptyMap(),
    ): ProjectDocument? {
        val reading = program(doc, profiles, rangeStart, rangeEnd, envelopes) as? Reading.Measured ?: return null
        val gain = (targetLufs - reading.lufs).coerceAtMost(MAX_RENDER_GAIN_DB)
        return doc.copy(mixer = doc.mixer.copy(masterVolumeDb = doc.mixer.masterVolumeDb + gain))
    }

    /**
     * Shifts the sound clip [clipId]'s level and all its volume keyframes by one amount so the clip
     * measures [targetLufs] (within the −60…+12 dB level range), and says where it ended up.
     */
    fun normalize(timeline: Timeline, clipId: String, profile: LoudnessProfile, targetLufs: Float, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先选择音频片段")
        if (clip.kind != ClipKind.AUDIO) throw EditRejected("所选片段没有音频")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        val before = when (val reading = measure(clip, profile, fps)) {
            is Reading.Measured -> reading
            Reading.TooShort -> throw EditRejected("片段太短，无法测量响度（至少 0.4 秒）")
            Reading.Silent -> throw EditRejected("片段几乎无声，无法标准化")
        }
        val delta = targetLufs - before.lufs
        if (abs(delta) < SAME_LU) throw EditRejected("响度已是 ${number(before.lufs)} LUFS")
        val updated = clip.copy(
            volumeDb = level(clip.volumeDb + delta),
            volumeKeyframes = clip.volumeKeyframes.map { it.copy(volumeDb = level(it.volumeDb + delta)) },
        )
        val after = measure(updated, profile, fps) as? Reading.Measured ?: before
        val message = buildString {
            if (abs(after.lufs - targetLufs) <= SAME_LU) {
                append("已标准化到 ${number(targetLufs)} LUFS · 增益 ${signed(after.lufs - before.lufs)} dB")
            } else {
                append("增益已到极限：${number(after.lufs)} LUFS（目标 ${number(targetLufs)}）")
            }
            if (after.truePeakDbtp > TRUE_PEAK_LIMIT_DBTP) append(" · 真峰值 ${signed(after.truePeakDbtp)} dBTP，可能削波")
        }
        return EditResult(timeline.replaceClip(updated), clipId, message)
    }

    private fun gainOf(db: Float): Double = if (db <= MIN_DB) 0.0 else 10.0.pow(db / 20.0)

    private fun gainAt(clip: Clip, clipMs: Double, msPerFrame: Double): Double = clipGainAt(clip, clipMs, msPerFrame)

    private fun level(db: Float) = db.coerceIn(MIN_DB, MAX_DB)

    private fun number(v: Float) = String.format(Locale.US, "%.1f", v)

    private fun signed(v: Float) = (if (v > 0) "+" else "") + number(v)

    companion object {
        /** Loudness targets offered for normalizing: streaming (YouTube), podcasts, broadcast (EBU R128). */
        val TARGETS_LUFS = listOf(-14f, -16f, -23f)
        /** The most an export's Normalize Audio raises the master. */
        const val MAX_RENDER_GAIN_DB = 24f
        private const val MIN_DB = -60f
        private const val MAX_DB = 12f
        private const val BLOCK_MS = LoudnessAnalyzer.BLOCK_MS.toDouble()
        private const val SAME_LU = 0.1f
        /**
         * True peaks above this warn of clipping: −1 dBTP is the streaming services' ceiling (AES TD1004), the
         * headroom a lossy encoder's overshoot needs.
         */
        const val TRUE_PEAK_LIMIT_DBTP = -1f
    }
}

/** The clip's gain [clipMs] into it: its (keyed) level times its fade ramps, as the mixer applies them. */
internal fun clipGainAt(clip: Clip, clipMs: Double, msPerFrame: Double): Double {
    val frame = (clipMs / msPerFrame).toInt().coerceIn(0, clip.durationFrames - 1)
    val db = clip.volumeAt(frame)
    var gain = if (db <= -60f) 0.0 else 10.0.pow(db / 20.0)
    val fadeIn = clip.fadeInSeconds * 1000.0
    val fadeOut = clip.fadeOutSeconds * 1000.0
    var ramp = 1.0
    if (fadeIn > 0 && clipMs < fadeIn) ramp = ClipFades.shape((clipMs / fadeIn).toFloat(), clip.fadeInCurve).toDouble()
    val untilEnd = clip.durationFrames * msPerFrame - clipMs
    if (fadeOut > 0 && untilEnd < fadeOut) ramp = minOf(ramp, ClipFades.shape((untilEnd / fadeOut).toFloat(), clip.fadeOutCurve).toDouble())
    gain *= ramp.coerceIn(0.0, 1.0)
    return gain
}

/**
 * Adds [clip]'s sound to the timeline's 100 ms blocks counted from [fromMs]: for every block whose centre
 * lies under the clip, the source block there (speed and reverse followed) with the clip's gain times
 * [scale] — its energy, times [energyScale], into [energy], its true peak into [peak].
 */
/** A balance pan turns one side down: the share of a stereo sound's energy that is left at [pan] (−1…1). */
internal fun panEnergy(pan: Float): Double = ((1.0 - abs(pan.coerceIn(-1f, 1f).toDouble())).pow(2) + 1.0) / 2.0

internal fun addClipBlocks(
    clip: Clip,
    profile: LoudnessProfile,
    msPerFrame: Double,
    fromMs: Double,
    scale: Double,
    energyScale: Double,
    energy: DoubleArray,
    peak: DoubleArray?,
) {
    val block = LoudnessAnalyzer.BLOCK_MS.toDouble()
    val clipStart = clip.start * msPerFrame
    val clipEnd = clip.end * msPerFrame
    val speed = clip.speed.factor.toDouble()
    val fps = (1000.0 / msPerFrame).roundToInt().coerceAtLeast(1)
    val sourceLength = if (clip.retimed) clip.sourceOffsetMs(clip.durationFrames, fps) else clip.durationFrames * msPerFrame * speed
    val first = floor((clipStart - fromMs) / block).toInt().coerceAtLeast(0)
    val last = ceil((clipEnd - fromMs) / block).toInt().coerceAtMost(energy.size)
    for (b in first until last) {
        val centre = fromMs + (b + 0.5) * block
        if (centre < clipStart || centre >= clipEnd) continue
        val clipMs = centre - clipStart
        val intoSource = if (clip.retimed) clip.sourceOffsetMs(clipMs / msPerFrame, fps) else clipMs * speed
        val sourceMs = clip.sourceInMs + if (clip.reverse) sourceLength - intoSource else intoSource
        val j = floor(sourceMs / profile.blockMs).toInt()
        if (j !in 0 until profile.blocks) continue
        val gain = clipGainAt(clip, clipMs, msPerFrame) * scale
        // The clip's pan turns one side down (its peak side stays whole).
        val pan = if (clip.pan == 0f && clip.panKeyframes.isEmpty()) 0f else clip.panAt((clipMs / msPerFrame).toInt())
        energy[b] += profile.energyOf(j, clip.channels) * gain * gain * energyScale * panEnergy(pan)
        if (peak != null) peak[b] += profile.peakOf(j, clip.channels) * gain
    }
}
