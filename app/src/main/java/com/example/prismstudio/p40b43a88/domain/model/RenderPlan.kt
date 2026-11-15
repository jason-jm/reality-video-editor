package com.example.prismstudio.p40b43a88.domain.model

/**
 * A playable description of the timeline: picture [layers] composited top-down (each enabled video
 * track, plus the outgoing-clip handles a transition reveals), one lane per audio track and the
 * title cues drawn on top. Frames are relative to [startFrame], so a sub-range render starts at zero.
 */
data class RenderPlan(
    val fps: Int,
    val width: Int,
    val height: Int,
    val startFrame: Int,
    val endFrame: Int,
    /** Top layer first; every layer spans the whole plan, with gaps where it shows nothing. */
    val layers: List<VideoLayer>,
    val audio: List<AudioLane>,
    val titles: List<TitleCue>,
) {
    val durationFrames: Int get() = endFrame - startFrame
    val isEmpty: Boolean get() = durationFrames <= 0

    /** More than one picture layer: overlays or transition handles have to be composited. */
    val isLayered: Boolean get() = layers.size > 1

    /**
     * Picture layer [index] (0 = top) on its own, for a per-layer preview player: titles ride on the
     * top layer and the sound on the bottom one, so each is played exactly once.
     */
    fun forLayer(index: Int): RenderPlan = copy(
        layers = listOf(layers[index]),
        audio = if (index == layers.lastIndex) audio else emptyList(),
        titles = if (index == 0) titles else emptyList(),
    )

    /**
     * The render passes of the picture, bottom layer first: each pass decodes one layer and draws it
     * over the previous pass's result, so no pass composites two decoded layers at once (Media3 then
     * shares one GL context between their decoder surfaces, where frames can cross between layers).
     * Layers that never show a picture are skipped; the sound and titles ride on the last pass. A
     * single-layer plan is its own only pass.
     */
    fun stackingPasses(): List<RenderPlan> {
        if (!isLayered) return listOf(this)
        val shown = layers.filter { layer -> layer.segments.any { it.showsPicture } }.ifEmpty { listOf(layers.last()) }
        return shown.reversed().mapIndexed { index, layer ->
            val last = index == shown.lastIndex
            copy(layers = listOf(layer), audio = if (last) audio else emptyList(), titles = if (last) titles else emptyList())
        }
    }

    /**
     * The picture as one opaque layer: in every instant the top-most track clip wins and transition
     * handles are dropped. Used where real compositing is unavailable (live preview of layered edits).
     */
    fun flattened(): RenderPlan = if (!isLayered) this else copy(layers = listOf(flatten(layers)))

    /**
     * The picture in at most [maxLayers] layers, for a monitor that stacks only so many players: neighbouring layers
     * share one ([flatten]: the top one wins where they overlap) where that hides the fewest frames of picture, so layers
     * that never overlap in time share one for nothing; between equal choices the lower layers stay apart (the base
     * picture usually sits at the bottom, overlays above it). Returns the plan and the frames of picture it hides
     * (0: the monitor shows exactly what a render makes).
     */
    fun grouped(maxLayers: Int): Pair<RenderPlan, Int> {
        val n = layers.size
        val k = maxLayers.coerceAtLeast(1)
        if (n <= k) return this to 0
        val cost = Array(n) { i -> IntArray(n) { j -> if (j <= i) 0 else hiddenFrames(layers.subList(i, j + 1)) } }
        // best[g][j]: the least hidden frames with the top j layers in g groups; cut[g][j]: where the last of them starts.
        val none = Int.MAX_VALUE / 2
        val best = Array(k + 1) { IntArray(n + 1) { none } }
        val cut = Array(k + 1) { IntArray(n + 1) }
        best[0][0] = 0
        for (g in 1..k) for (j in g..n) for (i in g - 1 until j) {
            if (best[g - 1][i] >= none) continue
            val c = best[g - 1][i] + cost[i][j - 1]
            // `<=`: the later start wins a tie, keeping the lower group the smaller.
            if (c <= best[g][j]) {
                best[g][j] = c
                cut[g][j] = i
            }
        }
        val groups = ArrayDeque<IntRange>()
        var j = n
        for (g in k downTo 1) {
            val i = cut[g][j]
            groups.addFirst(i until j)
            j = i
        }
        val merged = groups.map { r -> if (r.first == r.last) layers[r.first] else flatten(layers.subList(r.first, r.last + 1)) }
        return copy(layers = merged) to best[k][n]
    }

    /** What [flatten] hides of [group]: every picture under another at the same instant, and the handles (dropped). */
    private fun hiddenFrames(group: List<VideoLayer>): Int {
        val handles = group.filter { it.isHandles }.sumOf { layer -> layer.segments.filter { it.clipId != null }.sumOf { it.frames } }
        val steps = sortedMapOf<Int, Int>()
        group.filterNot { it.isHandles }.forEach { layer ->
            layer.segments.filter { it.clipId != null }.forEach {
                steps.merge(it.startFrame, 1, Int::plus)
                steps.merge(it.endFrame, -1, Int::plus)
            }
        }
        var hidden = 0
        var depth = 0
        var at = 0
        for ((frame, step) in steps) {
            if (depth > 1) hidden += (depth - 1) * (frame - at)
            depth += step
            at = frame
        }
        return handles + hidden
    }

    /** [group] (top first) as one layer: in every instant its top-most track clip wins; its transition handles are dropped. */
    private fun flatten(group: List<VideoLayer>): VideoLayer {
        val tracks = group.filterNot { it.isHandles }
        val cuts = sortedSetOf(0, durationFrames)
        tracks.forEach { layer -> layer.segments.forEach { cuts += it.startFrame; cuts += it.endFrame } }
        val bounds = cuts.filter { it in 0..durationFrames }
        val merged = mutableListOf<VideoSegment>()
        for (i in 0 until bounds.size - 1) {
            val a = bounds[i]
            val b = bounds[i + 1]
            val top = tracks.firstNotNullOfOrNull { layer ->
                layer.segments.firstOrNull { it.clipId != null && a >= it.startFrame && a < it.endFrame }
            }
            val last = merged.lastOrNull()
            if (last != null && last.clipId == top?.clipId && last.endFrame == a &&
                (top == null || last.sourceInMs == top.sourceAtFrame(last.startFrame, fps))
            ) {
                merged[merged.lastIndex] = last.copy(endFrame = b)
                continue
            }
            merged += top?.copy(
                startFrame = a,
                endFrame = b,
                sourceInMs = top.sourceAtFrame(a, fps),
                transition = top.transition.takeIf { a == top.startFrame },
            ) ?: VideoSegment(null, null, false, a, b, 0, 1f, null)
        }
        return VideoLayer(tracks.lastOrNull()?.id ?: group.last().id, merged)
    }

    /** Timing and media identity only — two plans with the same signature can share a player. */
    fun structureSignature(): List<Any?> = listOf(
        fps, width, height, startFrame, endFrame,
        layers.map { layer ->
            layer.id to layer.segments.map {
                // A transition's kind and length shape the composition; its style is read live, like the look.
                listOf(
                    it.clipId, it.assetId, it.startFrame, it.endFrame, it.sourceInMs, it.speed,
                    it.transition?.type, it.transition?.frames, it.transitionLead, it.transitionRole, it.transitionOwnerId,
                    it.reversed, it.clipStartFrame, it.temporalNr,
                )
            }
        },
        audio.map { lane ->
            lane.trackId to lane.segments.map {
                listOf(it.clipId, it.assetId, it.startFrame, it.endFrame, it.sourceInMs, it.speed, it.reversed, it.joinInFrames, it.joinOutFrames, it.pitchCents, it.tailFrames)
            }
        },
        titles.map { listOf(it.clipId, it.startFrame, it.endFrame) },
    )

    /** Assets some segment plays backwards: they need a reversed copy (see [ReverseMapping]). */
    fun reversedAssetIds(): Set<String> =
        (layers.flatMap { it.segments }.filter { it.reversed }.mapNotNull { it.assetId } +
            audio.flatMap { it.segments }.filter { it.reversed }.mapNotNull { it.assetId }).toSet()
}

/**
 * Source positions are whole milliseconds and frame times are not (a 30 fps frame sits at 6733.3 ms):
 * a window opening at 6734 ms would skip that frame and start a frame late. Source windows open — and
 * close — this much early, so a position rounded up past a frame keeps it.
 */
const val SOURCE_EDGE_MS = 1L

/**
 * A reversed copy mirrors its source around the source's duration: source time t plays at
 * duration − t. The span [startMs, startMs + spanMs) of one side therefore starts at
 * [mirrorStart] on the other, in both directions.
 */
object ReverseMapping {
    fun mirrorStart(durationMs: Long, startMs: Long, spanMs: Long): Long = (durationMs - startMs - spanMs).coerceAtLeast(0)
}

/**
 * One picture layer: a video track, or the handles under one ([isHandles]) — the outgoing clip
 * continuing past its cut so a dissolve, push or wipe into the next clip reveals real picture.
 */
data class VideoLayer(val id: String, val segments: List<VideoSegment>, val isHandles: Boolean = false) {
    /** Plan-relative frame ranges where this layer shows a picture. */
    val visibleRanges: List<IntRange>
        get() = segments.filter { it.showsPicture }.map { it.startFrame until it.endFrame }
}

/** One stretch of picture; [clipId] null (or an offline asset) shows nothing on this layer. */
data class VideoSegment(
    val clipId: String?,
    val assetId: String?,
    val isImage: Boolean,
    val startFrame: Int,
    val endFrame: Int,
    val sourceInMs: Long,
    val speed: Float,
    /** The clip's opening transition, present when this segment begins before it is over. */
    val transition: Transition?,
    /** Plays the asset's reversed copy; [sourceInMs] is then a position in that copy. */
    val reversed: Boolean = false,
    /** Plan frame where the clip starts (before [startFrame] when the segment begins inside it), for keyframes. */
    val clipStartFrame: Int = startFrame,
    /** How the picture combines with the layers beneath it (Resolve's Composite Mode). */
    val composite: CompositeMode = CompositeMode.NORMAL,
    /** How the frames the footage lacks are made (Resolve's Retime Process). */
    val retime: RetimeProcess = RetimeProcess.NEAREST,
    /** How a picture of another shape or size fills the frame (Resolve's Scaling, the project's when the clip follows it). */
    val scaling: ClipScaling = ClipScaling.FIT,
    /** Where that puts the picture in the frame (what CROP needs: the picture's size against the timeline's). */
    val framing: Framing = Framing.FULL,
    /**
     * How many frames of [transition] play before the clip's start (Resolve's alignment): the transition counts from
     * there. Negative for the outgoing clip's part of a dip, whose transition starts inside it.
     */
    val transitionLead: Int = 0,
    /** Which side of [transition] this picture plays: the incoming clip, the outgoing one fading out (a dip), or alone. */
    val transitionRole: TransitionRole = TransitionRole.INCOMING,
    /** The clip whose opening [transition] this is, when not this segment's own (the incoming clip of a dip): its style. */
    val transitionOwnerId: String? = null,
    /** Resolve's Temporal NR is on: a pass that remembers the frame before (its strength is read live). */
    val temporalNr: Boolean = false,
) {
    val frames: Int get() = endFrame - startFrame
    val showsPicture: Boolean get() = clipId != null && assetId != null

    /** Source position (ms) shown at plan [frame] inside this segment. */
    fun sourceAtFrame(frame: Int, fps: Int): Long =
        sourceInMs + ((frame - startFrame) * 1000.0 * speed / fps.coerceAtLeast(1)).toLong()
}

data class AudioLane(val trackId: String, val segments: List<AudioSegment>)

/** One stretch of sound on a lane; [clipId] null means silence. */
data class AudioSegment(
    val clipId: String?,
    val assetId: String?,
    val startFrame: Int,
    val endFrame: Int,
    val sourceInMs: Long,
    val speed: Float,
    /** How far (ms of timeline) this segment starts into its clip, for fades that span a cut. */
    val clipOffsetMs: Long,
    val clipDurationMs: Long,
    /** Plays the asset's reversed copy; [sourceInMs] is then a position in that copy. */
    val reversed: Boolean = false,
    /** The clip's equal-power cross fade-in over its first [crossInMs] ([com.example.prismstudio.p40b43a88.domain.usecase.AudioCrossFades]). */
    val crossInMs: Long = 0,
    /**
     * How long the clip plays on past its end, under the cross fade into the next clip: it fades out with equal power
     * over that time, in the same segment so its sound runs on unbroken through the cut.
     */
    val crossOutMs: Long = 0,
    /**
     * A speed point's join, where a clip's sound changes speed: this piece fades in with equal power over its first
     * [joinInFrames] (its decoder starting up mid-sound is hidden under the fade), while the piece before plays on past
     * the point for its [joinOutFrames] — included in its frames, on another lane — fading out. Cut there instead,
     * the sound would drop out for tens of milliseconds at every point.
     */
    val joinInFrames: Int = 0,
    val joinOutFrames: Int = 0,
    /** The clip's pitch shift in cents ([Clip.pitchCents]); 0 = none. */
    val pitchCents: Int = 0,
    /**
     * Frames at the end past the sound, into the silence after it, where only its track's reverb rings on ([TrackReverb]):
     * the source plays on unheard, so the item keeps its exact length.
     */
    val tailFrames: Int = 0,
) {
    val frames: Int get() = endFrame - startFrame
}

data class TitleCue(
    val clipId: String,
    val startFrame: Int,
    val endFrame: Int,
    val style: TitleStyle,
    val transform: ClipTransform,
    /** Plan frame where the title clip starts, and its keyframes (frames from that start). */
    val clipStartFrame: Int = startFrame,
    val keyframes: List<TransformKeyframe> = emptyList(),
    /** A subtitle's track style; null for other titles. */
    val subtitle: SubtitleStyle? = null,
    /** Plan frame where the title clip ends: its entrance, exit and roll run over the whole clip, not just the rendered part. */
    val clipEndFrame: Int = endFrame,
    /** The fade handles, in frames (see [ClipFades]); subtitles have none. */
    val fadeInFrames: Float = 0f,
    val fadeOutFrames: Float = 0f,
    /** How they bend ([ClipFades.shape]). */
    val fadeInCurve: Float = 0f,
    val fadeOutCurve: Float = 0f,
) {
    /** The title's transform at plan [frame]. */
    fun transformAt(frame: Int): ClipTransform = keyframedTransform(transform, keyframes, frame - clipStartFrame)

    /** How much of the title its fade handles let show at plan [frame], over its opacity (0…1). */
    fun fadeAt(frame: Int): Float =
        ClipFades.factor((frame - clipStartFrame).toFloat(), (clipEndFrame - clipStartFrame).toFloat(), fadeInFrames, fadeOutFrames, fadeInCurve, fadeOutCurve)
}

/**
 * The side of a transition a segment plays. A dip (Resolve's Dip to Color) shows one picture at a time: the outgoing clip
 * fades to the colour by the middle, the incoming one up from it — or, with nothing before it, over its whole length.
 */
enum class TransitionRole { INCOMING, OUTGOING, ALONE }
