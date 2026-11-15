package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.effectsFor
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesFor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.maskFor

/**
 * Resolve's jog-wheel trims (Speed Editor SLIP / ROLL, and the trim tool's slide): edits that keep the
 * timeline's length. A slip shows a different part of the source inside the same clip; a roll moves the
 * cut between a clip and the clip right after it; a slide moves the clip between its neighbours. Linked
 * picture and sound are trimmed together. Each call asks for [deltaFrames] and applies as much of it as
 * the media and the neighbours allow.
 */
class TrimModesUseCase {

    /** Slides the selected clip's source window by [deltaFrames] timeline frames (later content for positive). */
    fun slip(doc: ProjectDocument, clipId: String, deltaFrames: Int): EditResult {
        val timeline = doc.timeline
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.TITLE) throw EditRejected("标题没有可滑移的素材")
        if (doc.asset(clip.assetId)?.kind == MediaKind.IMAGE) throw EditRejected("静态图片没有可滑移的素材")
        val group = timeline.linkedGroup(clip)
        timeline.requireUnlocked(group, clip)
        val fps = doc.format.fps.coerceAtLeast(1)
        // Every member must keep its source window inside its media.
        var low = Double.NEGATIVE_INFINITY
        var high = Double.POSITIVE_INFINITY
        for (member in group) {
            val msPerFrame = 1000.0 * member.headSpeed / fps
            val duration = doc.sourceLengthMs(member) ?: continue
            low = maxOf(low, -(member.sourceInMs + MS_TOLERANCE) / msPerFrame)
            high = minOf(high, (duration - member.sourceInMs - member.sourceDurationMs(fps) + MS_TOLERANCE) / msPerFrame)
        }
        val applied = clamp(deltaFrames, low, high)
        if (applied == 0) throw EditRejected(if (deltaFrames > 0) "素材后面没有更多内容" else "素材前面没有更多内容")
        val ids = group.map { it.id }.toSet()
        val updated = timeline.clips.map { c ->
            if (c.id !in ids) c else c.copy(sourceInMs = shiftedSourceIn(c, applied, fps))
        }
        return EditResult(timeline.copy(clips = updated), clipId, "滑移 ${signed(applied)} 帧")
    }

    /**
     * Moves the cut at the end of [clipId] by [deltaFrames] (later for positive), on every track where
     * the clip or its linked partners meet the next clip there: the earlier clip gets longer and the
     * later one shorter (or the other way round), so nothing after the cut moves.
     */
    fun roll(doc: ProjectDocument, clipId: String, deltaFrames: Int): EditResult {
        val timeline = doc.timeline
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val cut = clip.end
        val next = timeline.clipsOn(clip.trackId).firstOrNull { it.start == cut && it.id != clip.id }
            ?: throw EditRejected("所选片段后面没有紧邻的片段，无法滚动剪辑点")
        val members = (timeline.linkedGroup(clip) + timeline.linkedGroup(next)).distinctBy { it.id }
        timeline.requireUnlocked(members, clip)
        val fps = doc.format.fps.coerceAtLeast(1)
        val ending = members.filter { it.end == cut }
        val starting = members.filter { it.start == cut }
        var low = Double.NEGATIVE_INFINITY
        var high = Double.POSITIVE_INFINITY
        for (a in ending) {
            // Shorter down to the minimum; longer up to its media and the next clip not in the roll.
            low = maxOf(low, (TrimClipUseCase.MIN_FRAMES - a.durationFrames).toDouble())
            high = minOf(high, roomAtEnd(doc, a, fps))
            timeline.clipsOn(a.trackId).firstOrNull { it.start >= cut && it.id != a.id && it !in starting }
                ?.let { blocker -> high = minOf(high, (blocker.start - cut).toDouble()) }
        }
        for (b in starting) {
            high = minOf(high, (b.durationFrames - TrimClipUseCase.MIN_FRAMES).toDouble())
            low = maxOf(low, -roomAtStart(doc, b, fps))
            timeline.clipsOn(b.trackId).lastOrNull { it.end <= cut && it.id != b.id && it !in ending }
                ?.let { blocker -> low = maxOf(low, (blocker.end - cut).toDouble()) }
        }
        val applied = clamp(deltaFrames, low, high)
        if (applied == 0) throw EditRejected(if (deltaFrames > 0) "剪辑点不能再往后了" else "剪辑点不能再往前了")
        val endingIds = ending.map { it.id }.toSet()
        val startingIds = starting.map { it.id }.toSet()
        val updated = timeline.clips.map { c ->
            when (c.id) {
                in endingIds -> endMoved(doc, c, applied, fps)
                in startingIds -> startMoved(doc, c, applied, fps)
                else -> c
            }
        }
        return EditResult(timeline.copy(clips = updated), clipId, "滚动剪辑点 ${signed(applied)} 帧")
    }

    /**
     * Resolve's slide: moves [clipId] (with its linked partners) [deltaFrames] along its track between its
     * neighbours — the clip keeps its content and length; the clip right before it ends that much later
     * (or earlier) and the clip right after it starts that much later (or earlier), showing more or less
     * of their media — so nothing else on the timeline moves. With a gap on a side instead of a clip, the
     * gap takes up the change, as far as the next clip there (or the timeline's start).
     */
    fun slide(doc: ProjectDocument, clipId: String, deltaFrames: Int): EditResult {
        val timeline = doc.timeline
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val sliders = timeline.linkedGroup(clip)
        val sliderIds = sliders.map { it.id }.toSet()
        val fps = doc.format.fps.coerceAtLeast(1)
        val left = mutableListOf<Clip>()
        val right = mutableListOf<Clip>()
        var low = Double.NEGATIVE_INFINITY
        var high = Double.POSITIVE_INFINITY
        for (s in sliders) {
            val others = timeline.clipsOn(s.trackId).filter { it.id !in sliderIds }
            val l = others.firstOrNull { it.end == s.start }
            // A gap before: as far back as the clip before it, or the timeline's start.
            if (l != null) left += l else low = maxOf(low, ((others.filter { it.end <= s.start }.maxOfOrNull { it.end } ?: 0) - s.start).toDouble())
            val r = others.firstOrNull { it.start == s.end }
            if (r != null) right += r else others.filter { it.start >= s.end }.minOfOrNull { it.start }?.let { high = minOf(high, (it - s.end).toDouble()) }
        }
        // A neighbour's linked partners at the same edit point follow it, as linked selection does in Resolve.
        val before = left.flatMap { l -> timeline.linkedGroup(l).filter { it.end == l.end } }.distinctBy { it.id }
        val after = right.flatMap { r -> timeline.linkedGroup(r).filter { it.start == r.start } }.distinctBy { it.id }
        val moving = sliderIds + before.map { it.id } + after.map { it.id }
        for (b in before) {
            // Shorter down to the minimum; longer up to its media and whatever follows it on its track.
            low = maxOf(low, (TrimClipUseCase.MIN_FRAMES - b.durationFrames).toDouble())
            high = minOf(high, roomAtEnd(doc, b, fps))
            timeline.clipsOn(b.trackId).firstOrNull { it.start >= b.end && it.id !in moving }?.let { high = minOf(high, (it.start - b.end).toDouble()) }
        }
        for (a in after) {
            high = minOf(high, (a.durationFrames - TrimClipUseCase.MIN_FRAMES).toDouble())
            low = maxOf(low, -roomAtStart(doc, a, fps))
            timeline.clipsOn(a.trackId).lastOrNull { it.end <= a.start && it.id !in moving }?.let { low = maxOf(low, (it.end - a.start).toDouble()) }
        }
        timeline.requireUnlocked(sliders + before + after, clip)
        val applied = clamp(deltaFrames, low, high)
        if (applied == 0) throw EditRejected(if (deltaFrames > 0) "片段不能再往后滑了" else "片段不能再往前滑了")
        val beforeIds = before.map { it.id }.toSet()
        val afterIds = after.map { it.id }.toSet()
        val updated = timeline.clips.map { c ->
            when (c.id) {
                in sliderIds -> c.copy(start = c.start + applied)
                in beforeIds -> endMoved(doc, c, applied, fps)
                in afterIds -> startMoved(doc, c, applied, fps)
                else -> c
            }
        }
        return EditResult(timeline.copy(clips = updated), clipId, "滑动 ${signed(applied)} 帧")
    }

    /** Whether [clip] plays its source window backwards: a reversed clip that is not a still. */
    private fun backwards(doc: ProjectDocument, clip: Clip): Boolean =
        clip.reverse && doc.asset(clip.assetId)?.let { it.kind != MediaKind.IMAGE } == true

    /**
     * Frames [clip]'s end can gain: the media after its source window — before it for a reversed clip,
     * whose last frame is its in point.
     */
    private fun roomAtEnd(doc: ProjectDocument, clip: Clip, fps: Int): Double =
        if (backwards(doc, clip)) sourceBefore(doc, clip, fps) else sourceAfter(doc, clip, fps)

    /** Frames [clip]'s start can move earlier: the media before its window — after it for a reversed clip. */
    private fun roomAtStart(doc: ProjectDocument, clip: Clip, fps: Int): Double =
        if (backwards(doc, clip)) sourceAfter(doc, clip, fps) else sourceBefore(doc, clip, fps)

    /** [c] with its end [delta] frames later; the frames that stay keep their places (a reversed clip's first frame too). */
    private fun endMoved(doc: ProjectDocument, c: Clip, delta: Int, fps: Int): Clip = c.copy(
        durationFrames = c.durationFrames + delta,
        speed = c.sectionWindow(0, c.durationFrames + delta).first,
        speedSections = c.sectionWindow(0, c.durationFrames + delta).second,
        sourceInMs = if (backwards(doc, c)) c.keepingWindowEnd(c.durationFrames + delta, fps) else c.sourceInMs,
        keyframes = c.keyframesFor(0, c.durationFrames + delta),
        effects = c.effectsFor(0, c.durationFrames + delta),
        volumeKeyframes = c.volumeKeyframesFor(0, c.durationFrames + delta),
        panKeyframes = c.panKeyframesFor(0, c.durationFrames + delta),
        grade = c.grade.keysFor(0, c.durationFrames + delta),
        mask = c.maskFor(0, c.durationFrames + delta),
        fadeOutSeconds = c.fadeOutSeconds.coerceAtMost((c.durationFrames + delta) / 2f / fps),
    )

    /** [c] with its start [delta] frames later; a reversed clip starts at its window's end, so its in point stays. */
    private fun startMoved(doc: ProjectDocument, c: Clip, delta: Int, fps: Int): Clip = c.copy(
        start = c.start + delta,
        durationFrames = c.durationFrames - delta,
        sourceInMs = when {
            backwards(doc, c) -> c.sourceInMs
            // Speed points move the in point through their sections.
            c.retimed -> (c.sourceInMs + c.sourceOffsetMs(delta, fps)).roundToLong().coerceAtLeast(0)
            else -> shiftedSourceIn(c, delta, fps)
        },
        speed = c.sectionWindow(delta, c.durationFrames - delta).first,
        speedSections = c.sectionWindow(delta, c.durationFrames - delta).second,
        keyframes = c.keyframesFor(delta, c.durationFrames - delta),
        effects = c.effectsFor(delta, c.durationFrames - delta),
        volumeKeyframes = c.volumeKeyframesFor(delta, c.durationFrames - delta),
        panKeyframes = c.panKeyframesFor(delta, c.durationFrames - delta),
        grade = c.grade.keysFor(delta, c.durationFrames - delta),
        mask = c.maskFor(delta, c.durationFrames - delta),
        fadeInSeconds = c.fadeInSeconds.coerceAtMost((c.durationFrames - delta) / 2f / fps),
    )

    /** [clip]'s in point [frames] timeline frames later, on the frame grid so repeated steps never drift. */
    private fun shiftedSourceIn(clip: Clip, frames: Int, fps: Int): Long {
        val msPerFrame = 1000.0 * clip.headSpeed / fps
        return (Math.round(clip.sourceInMs / msPerFrame + frames) * msPerFrame).roundToLong().coerceAtLeast(0)
    }

    /** Timeline frames of media left after [clip]'s out point (unbounded for stills and titles). */
    private fun sourceAfter(doc: ProjectDocument, clip: Clip, fps: Int): Double {
        val length = doc.sourceLengthMs(clip) ?: return Double.POSITIVE_INFINITY
        val msPerFrame = 1000.0 * clip.tailSpeed / fps
        return (length - clip.sourceInMs - clip.sourceDurationMs(fps) + MS_TOLERANCE) / msPerFrame
    }

    /** Timeline frames of media before [clip]'s in point (unbounded for stills and titles). */
    private fun sourceBefore(doc: ProjectDocument, clip: Clip, fps: Int): Double {
        doc.sourceLengthMs(clip) ?: return Double.POSITIVE_INFINITY
        return (clip.sourceInMs + MS_TOLERANCE) / (1000.0 * clip.headSpeed / fps)
    }

    /** [delta] limited to [low]..[high] whole frames, rounding towards zero (ms→frame noise forgiven). */
    private fun clamp(delta: Int, low: Double, high: Double): Int {
        if (low > high + EPSILON) return 0
        val lo = if (low.isInfinite()) Int.MIN_VALUE.toDouble() else low
        val hi = if (high.isInfinite()) Int.MAX_VALUE.toDouble() else high
        val limited = delta.toDouble().coerceIn(lo, maxOf(lo, hi))
        return (if (limited >= 0) floor(limited + EPSILON) else -floor(abs(limited) + EPSILON)).toInt()
    }

    private fun signed(frames: Int) = if (frames > 0) "+$frames" else "$frames"

    private companion object {
        const val EPSILON = 1e-6
        /** In points are stored in whole ms, up to half a ms off the frame grid: allow for that at media bounds. */
        const val MS_TOLERANCE = 1.0
    }
}
