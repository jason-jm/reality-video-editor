package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.AudioLane
import com.example.prismstudio.p40b43a88.domain.model.AudioSegment
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import com.example.prismstudio.p40b43a88.domain.model.ReverseMapping
import com.example.prismstudio.p40b43a88.domain.model.TitleCue
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.model.TransitionRole
import com.example.prismstudio.p40b43a88.domain.model.VideoLayer
import com.example.prismstudio.p40b43a88.domain.model.VideoSegment
import com.example.prismstudio.p40b43a88.domain.model.subtitleStyleOf
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Turns the multi-track timeline into a [RenderPlan]. Each enabled video track holding picture clips
 * becomes a layer (top track first) that is composited with its clips' opacity and transform, so a
 * scaled-down V2 clip shows V1 around it. Under a track whose clips open with a dissolve, push or
 * wipe, a handles layer carries the outgoing clip beneath the transition so it reveals it — past the cut for as
 * long as that clip has source left, and before it when the transition leads in (the incoming clip then shows early
 * from its head handles); the sources bound the transition. Audio tracks stay separate lanes so they can be mixed.
 */
class BuildRenderPlanUseCase {

    /** [rangeStart] inclusive and [rangeEnd] exclusive, in timeline frames; null means everything. */
    operator fun invoke(project: ProjectDocument, rangeStart: Int? = null, rangeEnd: Int? = null): RenderPlan {
        val fps = project.format.fps.coerceAtLeast(1)
        // A disabled clip plays and renders as if it were not there; the timeline keeps its length.
        val total = project.timeline.durationFrames
        val doc = if (project.timeline.clips.all { it.enabled }) project
        else project.copy(timeline = project.timeline.copy(clips = project.timeline.clips.filter { it.enabled }))
        val start = (rangeStart ?: 0).coerceIn(0, total)
        val end = (rangeEnd ?: total).coerceIn(start, total)
        return RenderPlan(
            fps = fps,
            width = doc.format.width,
            height = doc.format.height,
            startFrame = start,
            endFrame = end,
            layers = if (end > start) videoLayers(doc, start, end, fps) else emptyList(),
            audio = if (end > start) audioLanes(doc, start, end, fps) else emptyList(),
            titles = titles(doc, start, end),
        )
    }

    private fun videoLayers(doc: ProjectDocument, start: Int, end: Int, fps: Int): List<VideoLayer> {
        val layers = mutableListOf<VideoLayer>()
        for (track in doc.timeline.tracks.filter { it.kind == TrackKind.VIDEO && it.enabled }) {
            val clips = doc.timeline.clipsOn(track.id).filter { it.kind == ClipKind.VIDEO }
            // Where each opening transition plays around its cut (Resolve's alignment): a clip whose transition leads in
            // shows from that many frames before its start, and the clip before it hands over those last frames, which
            // then play on the handles layer beneath it.
            val spans = clips.mapNotNull { c -> TransitionHandles.span(doc, c, fps)?.let { c.id to it } }.toMap()
            val handOver = spans.values.filter { it.lead > 0 && it.outgoing != null }.associate { it.outgoing!!.id to it.lead }
            // A dip shows its clip from its middle on: until then the outgoing clip fades out on the handles layer.
            fun from(c: Clip) = spans[c.id]?.let { span -> span.midpoint?.let { c.start + it } ?: (c.start - span.lead) } ?: c.start
            fun to(c: Clip) = c.end - (handOver[c.id] ?: 0)
            val inRange = clips.filter { to(it) > start && from(it) < end }
            if (inRange.isNotEmpty()) {
                layers += VideoLayer(track.id, fill(inRange.flatMap { clipSegments(doc, it, from(it), to(it), start, end, fps, spans[it.id]) }, start, end))
            }
            // Also on its own: a range inside a dip's first half has only the outgoing clip fading out, beneath nothing.
            val handles = spans.flatMap { (incomingId, span) -> handleSegments(doc, incomingId, span, start, end, fps) }
            if (handles.isNotEmpty()) layers += VideoLayer("${track.id}-handles", fill(handles, start, end), isHandles = true)
        }
        return layers
    }

    /**
     * [clip] between timeline frames [from] and [to], clipped to the plan range: one segment, or one per speed section it
     * crosses (Resolve's speed points: each at its own speed — the first reaching back before the clip, the last on past
     * it); none outside the range. [opening] is where its opening transition plays when these are the clip's own segments.
     */
    @Suppress("LongParameterList")
    private fun clipSegments(
        doc: ProjectDocument,
        clip: Clip,
        from: Int,
        to: Int,
        start: Int,
        end: Int,
        fps: Int,
        opening: TransitionHandles.Span? = null,
    ): List<VideoSegment> {
        val a = maxOf(from, start)
        val b = minOf(to, end)
        if (b <= a) return emptyList()
        return sectionPieces(clip, a, b, fps, RAMP_STEP_MS).map { clipSegment(doc, clip, it, start, fps, opening) }
    }

    /** [clip]'s picture over [piece] (timeline frames inside the plan range), at the piece's speed. */
    @Suppress("LongParameterList")
    private fun clipSegment(doc: ProjectDocument, clip: Clip, piece: Piece, start: Int, fps: Int, opening: TransitionHandles.Span?): VideoSegment {
        val (a, b) = piece
        val asset = doc.asset(clip.assetId)?.takeUnless { it.offline }
        // The clip's own Scaling or the project's; at its own size it needs the picture's, else it fits.
        val chosen = clip.scaling ?: doc.format.scaling
        val sized = asset != null && asset.width > 0 && asset.height > 0
        val scaling = if (chosen == ClipScaling.CROP && !sized) ClipScaling.FIT else chosen
        return VideoSegment(
            clipId = clip.id,
            assetId = asset?.id,
            isImage = asset?.kind == MediaKind.IMAGE,
            startFrame = a - start,
            endFrame = b - start,
            sourceInMs = clip.sourceAt(a, fps, asset),
            speed = piece.speed,
            // The opening transition belongs to the clip's own segment, also when the plan starts partway through it (an
            // In / Out render, a still): it then plays from where it has got to (see clipStartFrame and transitionLead).
            transition = opening?.effective?.takeIf { a < clip.start - opening.lead + it.frames },
            reversed = clip.playsReversed(asset),
            clipStartFrame = clip.start - start,
            transitionLead = opening?.takeIf { it.effective != null }?.lead ?: 0,
            // A dip with nothing before it fades the clip up over its whole length.
            transitionRole = if (opening != null && opening.transition.type.dips && opening.outgoing == null) TransitionRole.ALONE
            else TransitionRole.INCOMING,
            composite = clip.composite,
            retime = clip.retime,
            scaling = scaling,
            framing = scaling.framing(asset?.width ?: 0, asset?.height ?: 0, doc.format.width, doc.format.height),
            // A still does not change frame to frame: nothing to average.
            temporalNr = clip.grade.temporalNr > 0 && asset?.kind == MediaKind.VIDEO,
        )
    }

    /**
     * The outgoing clip's picture under an opening transition for as long as it really lasts ([TransitionHandles]): its
     * last frames when the transition leads in before the cut, then its source past its out point.
     */
    @Suppress("LongParameterList")
    private fun handleSegments(doc: ProjectDocument, incomingId: String, span: TransitionHandles.Span, start: Int, end: Int, fps: Int): List<VideoSegment> {
        val outgoing = span.outgoing?.takeIf { span.frames > 0 } ?: return emptyList()
        val cut = outgoing.end
        // A dip needs the outgoing picture only up to its middle, and fades it out there — its transition starts inside it
        // and is styled as the incoming clip's.
        val midpoint = span.midpoint ?: return clipSegments(doc, outgoing, cut - span.lead, cut + span.tail, start, end, fps)
        return clipSegments(doc, outgoing, cut - span.lead, cut + midpoint, start, end, fps).map {
            it.copy(
                transition = span.effective,
                transitionLead = span.lead - outgoing.durationFrames,
                transitionRole = TransitionRole.OUTGOING,
                transitionOwnerId = incomingId,
            )
        }
    }

    /** Timeline frames [a] until [b] of a clip, played at one [speed]. */
    private data class Piece(val a: Int, val b: Int, val speed: Float)

    /**
     * Timeline frames [a] until [b] cut where [clip]'s speed changes (its first section reaching back before it, its last
     * on past it); one piece without speed points. Neighbouring sections of one speed play on as one stretch of source —
     * a point just added changes nothing until its section's speed does. A ramp (Resolve's smoothed speed point) is cut
     * into steps of about [stepMs], each at the speed that plays exactly the source the eased curve plays over it, so
     * every step starts on the frame the curve shows there.
     */
    private fun sectionPieces(clip: Clip, a: Int, b: Int, fps: Int, stepMs: Int): List<Piece> {
        if (clip.speedSections.isEmpty()) return listOf(Piece(a, b, clip.speed.factor))
        val bounds = clip.sectionBounds
        val step = maxOf(1, Math.round(stepMs * fps / 1000.0).toInt())
        val ramps = clip.ramps().map { (clip.start + it.from) to (clip.start + it.from + it.frames) }
        val cuts = sortedSetOf<Int>()
        for (k in 1 until bounds.lastIndex) {
            if (!clip.changesSpeedAt(k)) continue
            val ramp = clip.rampAt(k)
            if (ramp == 0) {
                cuts += clip.start + bounds[k]
                continue
            }
            val from = clip.start + bounds[k] - ramp / 2.0
            val steps = maxOf(1, Math.round(ramp.toDouble() / step).toInt())
            for (j in 0..steps) cuts += floor(from + j * ramp.toDouble() / steps + 0.5).toInt()
        }
        val inside = cuts.filter { it > a && it < b }
        return (listOf(a) + inside).zip(inside + b).map { (pa, pb) ->
            val ramped = ramps.any { (from, to) -> pa < to && pb > from }
            val speed = if (!ramped) clip.speedAt(pa - clip.start)
            else ((clip.sourceOffsetMs(pb - clip.start, fps) - clip.sourceOffsetMs(pa - clip.start, fps)) * fps / (1000.0 * (pb - pa))).toFloat()
            Piece(pa, pb, speed)
        }
    }

    /** Sorted, non-overlapping [segments] with gaps added so they cover the whole plan. */
    private fun fill(segments: List<VideoSegment?>, start: Int, end: Int): List<VideoSegment> {
        val out = mutableListOf<VideoSegment>()
        var cursor = 0
        for (segment in segments.filterNotNull().sortedBy { it.startFrame }) {
            if (segment.startFrame > cursor) out += gap(cursor, segment.startFrame)
            out += segment
            cursor = segment.endFrame
        }
        if (cursor < end - start) out += gap(cursor, end - start)
        return out
    }

    private fun gap(a: Int, b: Int) = VideoSegment(null, null, false, a, b, 0, 1f, null)

    private fun audioLanes(doc: ProjectDocument, start: Int, end: Int, fps: Int): List<AudioLane> =
        doc.timeline.tracks.filter { it.kind == TrackKind.AUDIO && it.enabled }.flatMap { track -> trackLanes(doc, track.id, start, end, fps) }

    /**
     * [trackId]'s sound clips in the range, with silences between them. A sound that cross-fades into the next one
     * plays on past its out point for as long as the fade really lasts ([AudioCrossFades]), in one piece: split at
     * the cut, the decoder would restart there and drop out. The clip it overlaps then takes a second lane, as in
     * A/B roll editing.
     */
    private fun trackLanes(doc: ProjectDocument, trackId: String, start: Int, end: Int, fps: Int): List<AudioLane> {
        val clips = doc.timeline.clipsOn(trackId).filter { it.kind == ClipKind.AUDIO }
        // How far each sound plays on past its end, under the fade into the clip after it.
        val tails = clips.mapNotNull { incoming -> AudioCrossFades.coverage(doc, incoming, fps)?.let { it.outgoing.id to it.frames } }.toMap()
        val lanes = mutableListOf<MutableList<AudioSegment>>()
        val cursors = mutableListOf<Int>()
        var played = start
        for (clip in clips) {
            val a = maxOf(clip.start, start, played)
            val b = minOf(clip.end + (tails[clip.id] ?: 0), end)
            played = maxOf(played, minOf(clip.end, end))
            if (b <= a) continue
            val asset = doc.asset(clip.assetId)?.takeUnless { it.offline }
            // Speed points cut the sound where the speed changes. As at a cross fade, the cut must not fall inside the
            // sound — the next piece's decoder would start up there and drop out — so the piece before plays on past the
            // point on its lane, fading out under the next piece's fade-in on another ([JOIN_MS]). Fades and gains run
            // on clip time, so they carry across.
            val pieces = sectionPieces(clip, a, b, fps, AUDIO_RAMP_STEP_MS)
            for ((k, piece) in pieces.withIndex()) {
                val (pa, pb) = piece
                val joinIn = if (k > 0) joinFrames(piece, fps) else 0
                val joinOut = if (k < pieces.lastIndex) joinFrames(pieces[k + 1], fps) else 0
                var lane = cursors.indexOfFirst { it <= pa }
                if (lane < 0) {
                    lanes += mutableListOf<AudioSegment>()
                    cursors += start
                    lane = lanes.lastIndex
                }
                if (pa > cursors[lane]) lanes[lane] += silence(cursors[lane] - start, pa - start)
                lanes[lane] += AudioSegment(
                    clipId = clip.id,
                    assetId = asset?.id,
                    startFrame = pa - start,
                    endFrame = pb + joinOut - start,
                    sourceInMs = clip.sourceAt(pa, fps, asset),
                    speed = piece.speed,
                    clipOffsetMs = (pa - clip.start) * 1000L / fps,
                    clipDurationMs = clip.durationFrames * 1000L / fps,
                    reversed = clip.playsReversed(asset),
                    crossInMs = (AudioCrossFades.coverage(doc, clip, fps)?.frames ?: 0) * 1000L / fps,
                    crossOutMs = (tails[clip.id] ?: 0) * 1000L / fps,
                    joinInFrames = joinIn,
                    joinOutFrames = joinOut,
                    pitchCents = clip.pitchCents,
                )
                cursors[lane] = pb + joinOut
            }
        }
        val reverb = doc.mixer.channel(trackId).reverb
        if (!reverb.isActive) return lanes.map { AudioLane(trackId, it) }
        val most = ceil(TrackReverb.TAIL_SECONDS * fps).toInt()
        return lanes.map { AudioLane(trackId, withReverbTails(doc, it, end - start, most, fps)) }
    }

    /**
     * The track's reverb rings on past each sound into the silence after it (or to the range's end): the sound's segment
     * grows into it by up to [most] frames — as far as its file goes on, its source playing unheard — and the silence
     * shrinks. A piece that hands over to the next at a speed point, or plays reversed, keeps its length.
     */
    private fun withReverbTails(doc: ProjectDocument, segments: List<AudioSegment>, length: Int, most: Int, fps: Int): List<AudioSegment> {
        val out = segments.toMutableList()
        for (i in out.indices) {
            val s = out[i]
            if (s.clipId == null || s.assetId == null || s.reversed || s.joinOutFrames > 0) continue
            val asset = doc.asset(s.assetId) ?: continue
            val next = out.getOrNull(i + 1)
            val room = when {
                next == null -> length - s.endFrame
                next.clipId == null -> next.endFrame - next.startFrame
                else -> 0
            }
            // The file must go on under the tail (a frame kept in hand): an item reading past its end would come out short.
            val sourceEndMs = s.sourceInMs + (s.endFrame - s.startFrame) * 1000.0 / fps * s.speed
            val sourceFrames = floor((asset.durationMs - sourceEndMs) / s.speed * fps / 1000.0).toInt() - 1
            val tail = minOf(room, most, sourceFrames)
            if (tail <= 0) continue
            out[i] = s.copy(endFrame = s.endFrame + tail, tailFrames = tail)
            if (next != null) out[i + 1] = next.copy(startFrame = next.startFrame + tail)
        }
        return out.filterNot { it.clipId == null && it.endFrame <= it.startFrame }
    }

    /** A speed point's join into [piece]: [JOIN_MS] in whole frames, never longer than the piece. */
    private fun joinFrames(piece: Piece, fps: Int): Int =
        minOf(ceil(JOIN_MS * fps / 1000.0).toInt(), piece.b - piece.a)

    private fun titles(doc: ProjectDocument, start: Int, end: Int): List<TitleCue> {
        // Titles on video tracks and subtitles on subtitle tracks, while their track is shown.
        val visible = doc.timeline.tracks.filter { it.kind != TrackKind.AUDIO && it.enabled }.map { it.id }.toSet()
        return doc.timeline.clips
            .filter { it.kind == ClipKind.TITLE && it.trackId in visible && it.title != null && it.end > start && it.start < end }
            .sortedBy { it.start }
            .map {
                val fades = ClipFades.takes(it)
                TitleCue(
                    it.id, maxOf(it.start, start) - start, minOf(it.end, end) - start, it.title!!, it.transform, it.start - start, it.keyframes,
                    subtitle = doc.timeline.subtitleStyleOf(it),
                    clipEndFrame = it.end - start,
                    fadeInFrames = if (fades) it.fadeInSeconds * doc.format.fps else 0f,
                    fadeOutFrames = if (fades) it.fadeOutSeconds * doc.format.fps else 0f,
                    fadeInCurve = it.fadeInCurve,
                    fadeOutCurve = it.fadeOutCurve,
                )
            }
    }

    private fun silence(a: Int, b: Int) = AudioSegment(null, null, a, b, 0, 1f, 0, 0)

    companion object {
        /**
         * How long a speed point's join overlaps the sound either side: the decoder of the piece after it takes some
         * 10 ms of source to start up (longer when slowed), all under the fade-in.
         */
        const val JOIN_MS = 50

        /**
         * A ramp's steps for the picture: a tenth of a second keeps the speed changing smoothly to the eye — each step
         * starts on the curve and strays well under a frame from it in between — without too many pieces to decode.
         */
        const val RAMP_STEP_MS = 100

        /**
         * A ramp's steps for the sound: longer, since every step joins the next under a short cross fade; still within a
         * few ms of the picture's curve.
         */
        const val AUDIO_RAMP_STEP_MS = 200
    }
}

/**
 * Source position (ms) that plays at timeline [frame] inside this clip — past its out point too. A
 * reversed clip reads the asset's reversed copy, where its out point comes first.
 */
internal fun Clip.sourceAt(frame: Int, fps: Int, asset: MediaAsset?): Long {
    // Through the speed sections, if any (the first one's speed before the clip, the last one's past it).
    val offset = sourceOffsetMs(frame - start, fps).toLong()
    if (asset == null || !playsReversed(asset)) return sourceInMs + offset
    return ReverseMapping.mirrorStart(asset.durationMs, sourceInMs, sourceDurationMs(fps)) + offset
}

/** Whether the clip plays [asset]'s reversed copy (stills have no direction). */
internal fun Clip.playsReversed(asset: MediaAsset?): Boolean = reverse && asset != null && asset.kind != MediaKind.IMAGE
