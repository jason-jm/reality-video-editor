package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MotionEstimator
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Resolve's Detect Scene Cuts: a clip holding several shots (a finished programme, a screen recording) is cut where one shot
 * gives way to the next. A frame starts a new shot when it differs from the one before far more than its neighbours do,
 * by either of two measures: the change in its brightness histogram (blind to motion), or what is left of the difference
 * once the picture is moved by the camera's shift between the two frames (so a fast pan is not a cut, while two shots of
 * similar brightness are). A flash — the picture changing for a frame or three and coming back — is not a cut, neither where
 * it starts nor where it ends, and no shot is shorter than [MIN_SHOT] frames.
 */
class SceneCutUseCase {

    /** Why [clip] cannot be searched for cuts, or null when it can: footage played forward at one speed. */
    fun unavailable(clip: Clip, asset: MediaAsset?): String? = when {
        clip.kind != ClipKind.VIDEO || asset?.kind != MediaKind.VIDEO -> "只有视频片段可以检测场景剪切"
        clip.reverse -> "倒放的片段不能检测场景剪切"
        clip.speedSections.isNotEmpty() -> "有变速点的片段不能检测场景剪切"
        else -> null
    }

    /**
     * Scores frames one at a time as they are read ([add]), keeping only the last few: per frame, how much it differs from
     * the one before (the larger of the two measures, 0…1) and whether that change is only a flash (the picture is back
     * within [FLASH_FRAMES] frames).
     */
    class Detector(private val width: Int, private val height: Int) {
        private val estimator = MotionEstimator(width, height)
        private val scores = ArrayList<Float>()
        private val changes = ArrayList<Float>()
        private val flashes = ArrayList<Boolean>()
        /** The last FLASH_FRAMES + 2 pictures, newest last. */
        private val recent = ArrayDeque<ByteArray>()
        private var previous: FloatArray? = null
        private var previousHist: FloatArray? = null

        fun add(frame: ByteArray) {
            val index = scores.size
            val picture = FloatArray(width * height) { (frame[it].toInt() and 0xFF) / 255f }
            val hist = histogram(frame)
            val p = previous
            val ph = previousHist
            if (p != null && ph != null) {
                var histDiff = 0f
                for (b in hist.indices) histDiff += abs(hist[b] - ph[b])
                scores += max(histDiff / 2f, compensated(estimator, p, picture, width, height))
                changes += meanDifference(recent.last(), frame)
            } else {
                scores += 0f
                changes += 0f
            }
            // The end of a flash: this frame is back near a picture from before the frame it changed from.
            var returned = false
            if (index >= 2) {
                for (k in 2..FLASH_FRAMES + 1) {
                    val earlier = recent.elementAtOrNull(recent.size - k) ?: break
                    if (changes[index] > 0f && meanDifference(earlier, frame) < changes[index] * FLASH_RETURN) returned = true
                }
            }
            flashes += returned
            // Frames whose change this one undoes were flashes: it is back near the picture before them.
            for (k in 1..FLASH_FRAMES) {
                val i = index - k
                if (i < 1) break
                val before = recent.elementAtOrNull(recent.size - k - 1) ?: break
                if (changes[i] > 0f && meanDifference(before, frame) < changes[i] * FLASH_RETURN) flashes[i] = true
            }
            recent.addLast(frame)
            if (recent.size > FLASH_FRAMES + 2) recent.removeFirst()
            previous = picture
            previousHist = hist
        }

        fun scores(): FloatArray = scores.toFloatArray()
        fun flashes(): BooleanArray = flashes.toBooleanArray()
    }

    /** [frames]' shot changes ([cuts] over a [Detector] run through them). */
    fun detect(frames: LumaFrames): List<Int> {
        val d = Detector(frames.width, frames.height)
        frames.frames.forEach(d::add)
        return cuts(d.scores(), d.flashes())
    }

    /**
     * The frames that start a new shot (indices), from their [scores] and [flashes]: a frame scoring at least [MIN_SCORE]
     * and [RATIO] times the median of the [WINDOW] frames on either side, that is not a flash, and leaves every shot at
     * least [MIN_SHOT] frames long (the stronger of two close cuts wins).
     */
    fun cuts(scores: FloatArray, flashes: BooleanArray): List<Int> {
        val n = scores.size
        val candidates = (1 until n).filter { i ->
            val s = scores[i]
            if (s < MIN_SCORE || flashes[i]) return@filter false
            val around = ((i - WINDOW)..(i + WINDOW)).filter { it in 1 until n && it != i }.map { scores[it] }.sorted()
            val median = if (around.isEmpty()) 0f else around[around.size / 2]
            s >= RATIO * median + FLOOR
        }
        val kept = mutableListOf<Int>()
        for (i in candidates.sortedByDescending { scores[it] }) {
            if (i < MIN_SHOT || n - i < MIN_SHOT) continue
            if (kept.none { abs(it - i) < MIN_SHOT }) kept += i
        }
        return kept.sorted()
    }

    /**
     * The timeline frames of [clip] where its shots change, from cuts at [cuts] (indices into [frames], the part of the
     * file the clip shows) at [fps]: inside the clip, at least [MIN_SHOT] frames from its ends and from each other.
     */
    fun timelineFrames(clip: Clip, frames: LumaFrames, cuts: List<Int>, fps: Int): List<Int> {
        val speed = clip.speed.factor.toDouble()
        val out = cuts.map { i ->
            val seconds = (frames.first + i) / frames.frameRate
            clip.start + ((seconds * 1000 - clip.sourceInMs) / 1000 * fps / speed).roundToInt()
        }.filter { it >= clip.start + MIN_SHOT && it <= clip.end - MIN_SHOT }.distinct().sorted()
        val spaced = mutableListOf<Int>()
        for (f in out) if (spaced.isEmpty() || f - spaced.last() >= MIN_SHOT) spaced += f
        return spaced
    }

    /**
     * [timeline] with [clipId] (and its linked sound) cut at [at] (timeline frames, ascending) by [split] — one piece per
     * shot, the last piece selected.
     */
    fun split(timeline: Timeline, clipId: String, at: List<Int>, split: SplitClipUseCase, fps: Int): EditResult {
        if (at.isEmpty()) throw EditRejected("没有找到场景剪切：片段是一个连续的镜头")
        var t = timeline
        var id = clipId
        var selected: String? = clipId
        for (frame in at) {
            val result = split(t, frame, id, fps)
            t = result.timeline
            id = result.selectedClipId ?: id
            selected = result.selectedClipId
        }
        return EditResult(t, selected, "检测到 ${at.size} 处场景剪切，已切成 ${at.size + 1} 段")
    }

    companion object {
        /** The analysis picture's width: shots differ at a glance, so a small one will do. */
        const val WIDTH = MotionEstimator.ANALYSIS_WIDTH
        const val BINS = 32
        const val MIN_SCORE = 0.12f
        const val RATIO = 3f
        const val FLOOR = 0.02f
        const val WINDOW = 10
        const val MIN_SHOT = 8
        const val FLASH_FRAMES = 3

        /** A flash comes back to within this share of its change. */
        const val FLASH_RETURN = 0.4f

        /** The longest clip searched (two hours at 30 fps): frames are scored as they are read, a few kept. */
        const val MAX_FRAMES = 216_000
    }
}

private fun meanDifference(a: ByteArray, b: ByteArray): Float {
    var acc = 0L
    for (k in a.indices) acc += abs((a[k].toInt() and 0xFF) - (b[k].toInt() and 0xFF))
    return acc / (a.size * 255f)
}

/** The mean absolute difference (0…1) left between [a] and [b] once [b] is moved back by the shift between them, over the middle. */
private fun compensated(estimator: MotionEstimator, a: FloatArray, b: FloatArray, w: Int, h: Int): Float {
    val (sx, sy) = estimator.shift(a, b)
    val dx = sx.roundToInt()
    val dy = sy.roundToInt()
    val m = MotionEstimator.DEFAULT_RADIUS + 3
    var acc = 0.0
    var count = 0
    for (y in m until h - m) {
        val py = y - dy
        if (py < 0 || py >= h) continue
        for (x in m until w - m) {
            val px = x - dx
            if (px < 0 || px >= w) continue
            acc += abs(b[y * w + x] - a[py * w + px])
            count++
        }
    }
    return if (count == 0) 0f else (acc / count).toFloat()
}

/** [p]'s brightness histogram in [SceneCutUseCase.BINS] bins, summing to 1. */
private fun histogram(p: ByteArray): FloatArray {
    val out = FloatArray(SceneCutUseCase.BINS)
    for (v in p) out[(v.toInt() and 0xFF) * SceneCutUseCase.BINS / 256]++
    for (b in out.indices) out[b] /= p.size.toFloat()
    return out
}
