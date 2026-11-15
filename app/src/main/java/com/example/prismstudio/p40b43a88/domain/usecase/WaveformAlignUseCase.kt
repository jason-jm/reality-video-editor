package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** A file's sound level every [bucketMs] milliseconds, in dB (full scale = 0), from its first sample. */
class SoundEnvelope(val bucketMs: Int, val levels: FloatArray) {
    val durationMs: Long get() = levels.size.toLong() * bucketMs
}

/**
 * Resolve's Auto Align Clips (Based on Waveform): the selected clips line up by their sound with the reference (the first group —
 * the clip tapped last) — a camera's scratch track and a recorder's sound of the same take, say. Each clip's sound is the clip itself or, for a
 * picture, its linked sound (else the sound in its own file). The comparison works on the rises in level (the level minus
 * its one-second average), so different microphones, gains and room tone still match; the best lag of the normalised
 * cross-correlation is refined between buckets. A clip that moves takes its linked partners along; sound alone moves to
 * the millisecond (its in point takes the part of a frame), pictures by whole frames.
 */
class WaveformAlignUseCase {

    /** Where [other]'s sound matches the [fromMs]…[untilMs] stretch of [reference]: reference time − other time, and how well (0…1). */
    data class Match(val lagMs: Double, val score: Float)

    /** One clip group's outcome: its name and how far it moved (ms, later positive), or why it did not. */
    data class Moved(val name: String, val deltaMs: Long)

    data class Result(val timeline: Timeline, val moved: List<Moved>, val skipped: List<String>)

    /**
     * The best match of [other] against [reference] between [fromMs] and [untilMs] of the reference file, or null when
     * none is convincing (too little sound in common, or no lag stands out).
     */
    fun match(reference: SoundEnvelope, fromMs: Long, untilMs: Long, other: SoundEnvelope): Match? {
        require(reference.bucketMs == other.bucketMs) { "envelopes of different resolutions" }
        val b = reference.bucketMs
        val first = (fromMs / b).toInt().coerceIn(0, reference.levels.size)
        val last = ((untilMs + b - 1) / b).toInt().coerceIn(first, reference.levels.size)
        val x = rises(reference.levels).copyOfRange(first, last)
        val y = rises(other.levels)
        if (x.size < MIN_BUCKETS || y.size < MIN_BUCKETS) return null
        val minOverlap = min(MIN_OVERLAP_MS / b, max(MIN_BUCKETS, min(x.size, y.size) / 2))
        // Σ x[i]·y[i + k] for every lag k (x's bucket i meets y's bucket i + k), by FFT.
        val raw = crossCorrelation(x, y)
        // Normalised over each lag's overlap (prefix sums of squares).
        val px = prefixSquares(x)
        val py = prefixSquares(y)
        val kMin = -(x.size - minOverlap)
        val kMax = y.size - minOverlap
        if (kMin > kMax) return null
        val score = FloatArray(kMax - kMin + 1)
        for (k in kMin..kMax) {
            val i0 = max(0, -k)
            val i1 = min(x.size, y.size - k)
            if (i1 - i0 < minOverlap) continue
            val ex = px[i1] - px[i0]
            val ey = py[i1 + k] - py[i0 + k]
            if (ex <= 0 || ey <= 0) continue
            score[k - kMin] = (raw(k) / sqrt(ex * ey)).toFloat()
        }
        var best = 0
        for (i in score.indices) if (score[i] > score[best]) best = i
        val top = score[best]
        // The runner-up away from the peak: a clear winner, or no match.
        var second = 0f
        for (i in score.indices) if (abs(i - best) > PEAK_GAP && score[i] > second) second = score[i]
        if (top < MIN_SCORE || top - second < MIN_MARGIN) return null
        val frac = if (best in 1 until score.size - 1) parabola(score[best - 1], top, score[best + 1]) else 0.0
        val k = best + kMin + frac
        // x's bucket i is reference time first·b + i·b; it meets y's bucket i + k, other time (i + k)·b.
        return Match(lagMs = first * b - k * b, score = top)
    }

    /** The clip whose sound stands for [group] (a picture with its linked sound, or a clip alone), or null for a title. */
    fun soundOf(group: List<Clip>, assets: Map<String, MediaAsset>): Clip? =
        group.firstOrNull { it.kind == ClipKind.AUDIO && assets[it.assetId] != null }
            ?: group.firstOrNull { it.kind == ClipKind.VIDEO && assets[it.assetId]?.let { a -> a.kind == MediaKind.VIDEO && a.hasAudio } == true }

    /** Why [clip] cannot be compared by its sound, or null. */
    private fun unusable(clip: Clip): String? = when {
        clip.reverse -> "「${clip.name}」倒放，无法按声音对齐"
        clip.speedSections.isNotEmpty() || !clip.speed.isNormal -> "「${clip.name}」变速了，无法按声音对齐"
        else -> null
    }

    /** The files whose sound [groups] (the first being the reference) need measuring. */
    fun wanted(groups: List<List<Clip>>, assets: Map<String, MediaAsset>): List<MediaAsset> =
        groups.mapNotNull { soundOf(it, assets) }.mapNotNull { assets[it.assetId] }.distinctBy { it.uri }

    /**
     * [timeline] with every group of [groups] after the first lined up with the first by sound, [envelopes] by file uri.
     * Groups that cannot be matched stay put and are named in [Result.skipped]. Throws [EditRejected] when nothing can
     * move, or a move would land on a clip that stays.
     */
    fun align(timeline: Timeline, groups: List<List<Clip>>, assets: Map<String, MediaAsset>, envelopes: Map<String, SoundEnvelope>, fps: Int): Result {
        if (groups.size < 2) throw EditRejected("选择至少两个片段：最后点选的片段作为参照")
        val frameMs = 1000.0 / fps
        val refSound = soundOf(groups.first(), assets) ?: throw EditRejected("参照片段「${groups.first().first().name}」没有声音")
        unusable(refSound)?.let { throw EditRejected(it) }
        val refEnvelope = envelopes[assets.getValue(refSound.assetId!!).uri] ?: throw EditRejected("读不出参照片段的声音")
        val refStartMs = refSound.start * frameMs
        val refFrom = refSound.sourceInMs
        val refUntil = refFrom + (refSound.durationFrames * frameMs).roundToLong()
        var t = timeline
        val moved = mutableListOf<Moved>()
        val skipped = mutableListOf<String>()
        val movingIds = groups.drop(1).flatten().map { it.id }.toSet()
        for (group in groups.drop(1)) {
            val sound = soundOf(group, assets)
            val name = group.first().name
            if (sound == null) { skipped += "「$name」没有声音"; continue }
            val problem = unusable(sound)
            if (problem != null) { skipped += problem; continue }
            val envelope = envelopes[assets.getValue(sound.assetId!!).uri]
            val match = envelope?.let { match(refEnvelope, refFrom, refUntil, it) }
            if (match == null) { skipped += "「$name」找不到与参照相同的声音"; continue }
            // Lined up when (start − in point) of this sound = (start − in point) of the reference + lag, in ms.
            val wantedOffset = refStartMs - refSound.sourceInMs + match.lagMs
            val currentOffset = sound.start * frameMs - sound.sourceInMs
            val deltaMs = wantedOffset - currentOffset
            val pictureInGroup = group.any { it.kind == ClipKind.VIDEO }
            val deltaFrames = floor(deltaMs / frameMs + if (pictureInGroup) 0.5 else 0.0).toInt()
            // Sound alone takes the rest of the move (a part of a frame) into its in point.
            val restMs = if (pictureInGroup) 0L else (deltaFrames * frameMs - deltaMs).roundToLong()
            t = shift(t, group, deltaFrames, restMs, movingIds, frameMs)
            moved += Moved(name, if (pictureInGroup) (deltaFrames * frameMs).roundToLong() else deltaMs.roundToLong())
        }
        if (moved.isEmpty()) throw EditRejected(skipped.firstOrNull() ?: "没有可以对齐的片段")
        return Result(t, moved, skipped)
    }

    /**
     * [group] moved [frames] on, its in points [restMs] later (sound alone: the part of a frame); a head pushed before the
     * timeline's start is trimmed. Refused where it would land on a clip that is not moving.
     */
    private fun shift(t: Timeline, group: List<Clip>, frames: Int, restMs: Long, moving: Set<String>, frameMs: Double): Timeline {
        var out = t
        for (clip in group) {
            var start = clip.start + frames
            var duration = clip.durationFrames
            var sourceIn = clip.sourceInMs + restMs
            // An in point before the file's start (the part of a frame) starts a frame later instead.
            if (sourceIn < 0) { start += 1; duration -= 1; sourceIn += frameMs.roundToLong() }
            if (start < 0) { val cut = -start; start = 0; duration -= cut; sourceIn += (cut * frameMs).roundToLong() }
            if (duration <= 0) throw EditRejected("对齐后「${clip.name}」会完全移出时间线开头")
            val placed = clip.copy(start = start, durationFrames = duration, sourceInMs = sourceIn)
            out.clips.firstOrNull { it.trackId == clip.trackId && it.id !in moving && it.id != clip.id && it.start < placed.end && placed.start < it.end }
                ?.let { throw EditRejected("对齐后「${clip.name}」会与「${it.name}」重叠：先挪开它") }
            out = out.replaceClip(placed)
        }
        return out
    }

    /** The level's rises: each bucket minus the mean of the second around it, rises only (onsets carry the timing). */
    fun rises(levels: FloatArray): DoubleArray {
        val n = levels.size
        val half = RISE_WINDOW / 2
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + levels[i]
        return DoubleArray(n) { i ->
            val a = max(0, i - half)
            val b = min(n, i + half + 1)
            max(0.0, levels[i] - (prefix[b] - prefix[a]) / (b - a))
        }
    }

    private fun prefixSquares(v: DoubleArray): DoubleArray {
        val out = DoubleArray(v.size + 1)
        for (i in v.indices) out[i + 1] = out[i] + v[i] * v[i]
        return out
    }

    /** Σ x[i]·y[i + k] as a function of k (−x.size < k < y.size), computed with one FFT size. */
    private fun crossCorrelation(x: DoubleArray, y: DoubleArray): (Int) -> Double {
        var size = 1
        while (size < x.size + y.size) size = size shl 1
        val ar = DoubleArray(size)
        val ai = DoubleArray(size)
        val br = DoubleArray(size)
        val bi = DoubleArray(size)
        x.copyInto(ar)
        y.copyInto(br)
        fft(ar, ai, false)
        fft(br, bi, false)
        // conj(X)·Y gives Σ x[i]·y[i + k] at index k (negative k wrapped to the end).
        for (i in 0 until size) {
            val r = ar[i] * br[i] + ai[i] * bi[i]
            val im = ar[i] * bi[i] - ai[i] * br[i]
            ar[i] = r
            ai[i] = im
        }
        fft(ar, ai, true)
        return { k -> ar[if (k >= 0) k else size + k] }
    }

    /** In-place radix-2 FFT of ([re], [im]); [inverse] divides by the size. */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = 2 * PI / len * if (inverse) 1 else -1
            val wr = cos(angle)
            val wi = sin(angle)
            var start = 0
            while (start < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = start + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                start += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    private fun parabola(a: Float, b: Float, c: Float): Double {
        val d = a - 2 * b + c
        if (d >= -1e-9f) return 0.0
        return (0.5 * (a - c) / d).coerceIn(-0.5, 0.5)
    }

    companion object {
        /** The envelope's resolution. */
        const val BUCKET_MS = 5

        /** The span a rise is measured against (buckets: one second). */
        const val RISE_WINDOW = 200

        /** At least this much sound in common (ms), or half the shorter, whichever is less. */
        const val MIN_OVERLAP_MS = 3_000
        const val MIN_BUCKETS = 40

        /** How well the best lag must match, and by how much it must beat any other more than [PEAK_GAP] buckets away. */
        const val MIN_SCORE = 0.3f
        const val MIN_MARGIN = 0.08f
        const val PEAK_GAP = 20
    }
}
