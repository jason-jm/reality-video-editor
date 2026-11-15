package com.example.prismstudio.p40b43a88.data.engine

import com.example.prismstudio.p40b43a88.domain.model.MidDetailKernel
import com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.model.TemporalNrKernel
import com.example.prismstudio.p40b43a88.domain.model.CameraPath
import com.example.prismstudio.p40b43a88.domain.usecase.StabilizationUseCase
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.track
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.SpatialNrKernel
import com.example.prismstudio.p40b43a88.domain.model.ChromaKeyKernel
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.LutSource
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.subtitleStyleOf
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.easedProgress
import com.example.prismstudio.p40b43a88.domain.usecase.BuildGradeLutUseCase
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Per-clip look and mix parameters that the GPU shader and the audio processors read on every
 * frame/buffer. Updating the registry changes the picture and sound immediately, without rebuilding
 * the player — grading, transform sliders and mixer faders stay real-time.
 */
class LiveLookRegistry(private val luts: LutSource = LutSource.NONE) {
    private val lutBuilder = BuildGradeLutUseCase(LUT_SIZE, luts)
    private val stabilizer = StabilizationUseCase()

    class VideoLook(
        /**
         * `LUT_SIZE` × `LUT_SIZE²` ARGB pixels (x = blue, y = red·N + green), null for identity. Equal
         * grades share one array, so a changed array means a changed LUT.
         */
        val lutPixels: IntArray?,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val rotationRadians: Float,
        val opacity: Float,
        /** blur, sharpen, vignette, grain, mirror, mosaic, chromatic, glow — each 0..1. */
        val fx: FloatArray,
        /**
         * Each transform value's own keys (Resolve keys parameters apart), in [transformAt]'s order — scale, offsetX,
         * offsetY, rotationRadians, opacity, crop left, right, top, bottom, crop softness (crop as in [crop]) — as runs of
         * [frame, value, slopeIn, slopeOut] (see [KeyEase]); a value without keys (null) holds still. Null: nothing keyed.
         */
        val keyframes: Array<FloatArray?>? = null,
        /** The window node's composite (primary, then the window's grade) as a LUT like [lutPixels]; null = no window. */
        val windowLutPixels: IntArray? = null,
        /** Serial nodes 03 on, in turn, as a LUT like [lutPixels] applied after node 2; null = none active. */
        val postLutPixels: IntArray? = null,
        /**
         * The window's [shape (1 circle, 2 rectangle, 3 gradient, 4 full frame), centerX, centerY, size,
         * aspect, angle (radians), softness, invert (1 = yes)] (see [PowerWindow.coverage]); null = no window.
         */
        val window: FloatArray? = null,
        /**
         * The window node's qualifier as [hueCenter, hueWidth, hueSoft (degrees), invert (1 = yes),
         * satLow, satHigh, satSoft, 0, lumLow, lumHigh, lumSoft, 0, matte black point, matte white point,
         * blur radius (share of the source's height), 0] (see [HslQualifier.key]); null = none.
         */
        val qualifier: FloatArray? = null,
        /**
         * The window's keyframes as runs of [frame, centerX, centerY, size, aspect, angle (radians),
         * softness, slopeIn, slopeOut]; null = it stays where [window] says.
         */
        val windowKeys: FloatArray? = null,
        /** The clip's mask ([Clip.mask]) as [window] lays a window out; null = none (the whole picture shows). */
        val mask: FloatArray? = null,
        /** The mask's keyframes as [windowKeys] lays them out; null = it stays where [mask] says. */
        val maskKeys: FloatArray? = null,
        /**
         * Node 1's keyframes as runs of [frame, slopeIn, slopeOut]; [keyLuts] holds their LUTs and
         * [keyWindowLuts] their window composites, in the same order; null = a still grade ([lutPixels]).
         */
        val gradeKeys: FloatArray? = null,
        val keyLuts: List<IntArray>? = null,
        val keyWindowLuts: List<IntArray>? = null,
        /**
         * Cropping as [left, right, top, bottom] — shares of the frame's width and height each edge is cut
         * back — and the softness as a share of its height (see [ClipTransform]).
         */
        val crop: FloatArray = NO_CROP,
        /** Dynamic Zoom over the clip's [clipFrames] (see [DynamicZoom.scaleAt]); null = off. */
        val zoom: DynamicZoom? = null,
        val clipFrames: Int = 0,
        /** The fade handles: frames the picture takes to fade up from nothing and down to nothing (see [ClipFades]). */
        val fadeInFrames: Float = 0f,
        val fadeOutFrames: Float = 0f,
        /** How the fades bend, −1…1 ([ClipFades.shape]). */
        val fadeInCurve: Float = 0f,
        val fadeOutCurve: Float = 0f,
        /** Resolve's keyer as [key Cb, key Cr, similarity, smoothness, spill] ([ChromaKeyKernel.uniforms]); null = off. */
        val chromaKey: FloatArray? = null,
        /** Spatial NR before node 1 as [luma sigma, chroma sigma, radius, amount] ([SpatialNrKernel.uniforms]); null = off. */
        val noiseReduction: FloatArray? = null,
        /** Mid/Detail after the noise reduction, −1…1 ([MidDetailKernel.uniform]); 0 = off. */
        val midDetail: Float = 0f,
        /** Temporal NR as [blend, threshold] ([TemporalNrKernel.uniforms]); null = off. */
        val temporalNr: FloatArray? = null,
        /**
         * The opening transition's style as [ease slope at its start, at its end, reverse (1 = yes), edge softness and
         * border width (shares of the frame's height), border red, green, blue (0…1), a wipe's travel angle (radians,
         * clockwise from rightwards; its reverse folded in)] (see [Transition]); its kind and length belong to the
         * composition. NO_TRANSITION_STYLE without one.
         */
        val transitionStyle: FloatArray = NO_TRANSITION_STYLE,
        /**
         * Keyframed effects, one run each: [slot in [fx], then per keyframe frame, amount (0..1), slopeIn, slopeOut] (see
         * [KeyEase]); added to [fx] at every frame. Null = every effect holds its amount.
         */
        val fxKeys: List<FloatArray>? = null,
        /**
         * Stabilization ([StabilizationPlan]): where the steadied picture puts the source at each clip frame 0…clip length,
         * x and y in shares of the picture (right and down), and the zoom that hides the moving edges; null = off.
         */
        val stabX: FloatArray? = null,
        val stabY: FloatArray? = null,
        val stabScale: Float = 1f,
    ) {
        /**
         * The stabilization [clipFrame] frames into the clip as [out] = [x, y, scale] (eased between frames, held past the
         * ends); [0, 0, 1] without it.
         */
        fun stabAt(clipFrame: Float, out: FloatArray) {
            val xs = stabX
            val ys = stabY
            if (xs == null || ys == null || xs.isEmpty()) {
                out[0] = 0f; out[1] = 0f; out[2] = 1f
                return
            }
            val f = clipFrame.coerceIn(0f, (xs.size - 1).toFloat())
            val i = f.toInt().coerceAtMost(xs.size - 1)
            val j = minOf(i + 1, xs.size - 1)
            val t = f - i
            out[0] = xs[i] + (xs[j] - xs[i]) * t
            out[1] = ys[i] + (ys[j] - ys[i]) * t
            out[2] = stabScale
        }

        /** The effects' amounts [clipFrame] frames into the clip, keyframed ones interpolated, as [fx] holds them. */
        fun fxAt(clipFrame: Float, out: FloatArray) {
            fx.copyInto(out)
            val keys = fxKeys ?: return
            for (run in keys) {
                val slot = run[0].toInt()
                out[slot] = (out[slot] + keyedAmount(run, clipFrame)).coerceIn(0f, 1f)
            }
            // The mirror is on or off.
            if (out[MIRROR] > 0f) out[MIRROR] = 1f
        }

        /** The scale Dynamic Zoom adds [clipFrame] frames into the clip (1 without it). */
        fun zoomAt(clipFrame: Float): Float = zoom?.scaleAt(clipFrame, clipFrames) ?: 1f

        /** How much of the picture its fade handles let show [clipFrame] frames into the clip, over its opacity (0…1). */
        fun fadeAt(clipFrame: Float): Float =
            if (fadeInFrames <= 0f && fadeOutFrames <= 0f) 1f
            else ClipFades.factor(clipFrame, clipFrames.toFloat(), fadeInFrames, fadeOutFrames, fadeInCurve, fadeOutCurve)

        /**
         * Dynamic Zoom on top of [xform] — scale, x and y offset (shares of the frame), rotation, as [transformAt]
         * writes them — [clipFrame] frames into the clip, for a frame [aspect] wide to its height. The picture is
         * scaled about its anchor ([DynamicZoom.anchorXAt]): the transform puts that point at s·R(−θ)·a + t, so
         * keeping it there moves the picture by −(zoom − 1)·s·R(−θ)·a. Its crop goes with it.
         */
        fun applyZoom(clipFrame: Float, xform: FloatArray, aspect: Float) {
            val z = zoom ?: return
            val zoomBy = z.scaleAt(clipFrame, clipFrames)
            val ax = z.anchorXAt(clipFrame, clipFrames) * 0.5f * aspect
            val ay = z.anchorYAt(clipFrame, clipFrames) * 0.5f
            val scale = xform[0]
            val c = cos(xform[3])
            val s = sin(xform[3])
            xform[1] -= (zoomBy - 1f) * scale * (c * ax + s * ay) / aspect
            xform[2] -= (zoomBy - 1f) * scale * (-s * ax + c * ay)
            xform[0] = scale * zoomBy
        }

        /**
         * Which keyframes grade the picture [clipFrame] frames into the clip and how far it has dissolved
         * from one to the next: [out] = [from index, to index, mix (eased)]; all 0 for a still grade.
         */
        fun gradeSpanAt(clipFrame: Float, out: FloatArray) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f
            val k = gradeKeys
            if (k == null || k.isEmpty() || clipFrame <= k[0]) return
            val n = k.size / GRADE_KEY_STRIDE
            var i = 0
            while (i + 1 < n && k[(i + 1) * GRADE_KEY_STRIDE] <= clipFrame) i++
            out[0] = i.toFloat()
            out[1] = i.toFloat()
            if (i + 1 >= n) return
            val a = i * GRADE_KEY_STRIDE
            val b = a + GRADE_KEY_STRIDE
            out[1] = (i + 1).toFloat()
            out[2] = easedProgress((clipFrame - k[a]) / (k[b] - k[a]), k[a + 2], k[b + 1])
        }

        /** Writes the window's parameters (as [window]) [clipFrame] frames into the clip into [out] (8 floats). */
        fun windowAt(clipFrame: Float, out: FloatArray) {
            shapeAt(window ?: return, windowKeys, clipFrame, out)
        }

        /** Writes the mask's parameters (as [mask]) [clipFrame] frames into the clip into [out] (8 floats). */
        fun maskAt(clipFrame: Float, out: FloatArray) {
            shapeAt(mask ?: return, maskKeys, clipFrame, out)
        }

        /** A window's or mask's parameters [base] as they stand [clipFrame] frames in, through its keyframes [keys]. */
        private fun shapeAt(base: FloatArray, keys: FloatArray?, clipFrame: Float, out: FloatArray) {
            base.copyInto(out)
            val k = keys
            if (k == null || k.isEmpty()) return
            val n = k.size / WINDOW_KEY_STRIDE
            var i = 0
            while (i + 1 < n && k[(i + 1) * WINDOW_KEY_STRIDE] <= clipFrame) i++
            val a = i * WINDOW_KEY_STRIDE
            // Geometry sits at out[1..6]; the shape (out[0]) and inversion (out[7]) do not animate.
            if (i + 1 >= n || clipFrame <= k[a]) {
                for (j in 0 until 6) out[1 + j] = k[a + 1 + j]
                return
            }
            val b = a + WINDOW_KEY_STRIDE
            val t = easedProgress((clipFrame - k[a]) / (k[b] - k[a]), k[a + 8], k[b + 7])
            for (j in 0 until 6) out[1 + j] = k[a + 1 + j] + (k[b + 1 + j] - k[a + 1 + j]) * t
        }

        /**
         * Writes scale, offsets, rotation, opacity and the crop (as [crop]) [clipFrame] frames into the clip into
         * [out] ([TRANSFORM_VALUES] floats).
         */
        fun transformAt(clipFrame: Float, out: FloatArray) {
            out[0] = scale; out[1] = offsetX; out[2] = offsetY; out[3] = rotationRadians; out[4] = opacity
            crop.copyInto(out, 5)
            val tracks = keyframes ?: return
            for (j in 0 until TRANSFORM_VALUES) {
                val k = tracks[j] ?: continue
                val n = k.size / PARAM_KEY_STRIDE
                var i = 0
                while (i + 1 < n && k[(i + 1) * PARAM_KEY_STRIDE] <= clipFrame) i++
                val a = i * PARAM_KEY_STRIDE
                if (i + 1 >= n || clipFrame <= k[a]) {
                    out[j] = k[a + 1]
                    continue
                }
                val b = a + PARAM_KEY_STRIDE
                val t = easedProgress((clipFrame - k[a]) / (k[b] - k[a]), k[a + 3], k[b + 2])
                out[j] = k[a + 1] + (k[b + 1] - k[a + 1]) * t
            }
        }
    }

    /** A title's live text, transform, keyframes and (a subtitle's) track style; its timing comes from the render plan. */
    class TitleLook(
        val style: TitleStyle,
        val transform: ClipTransform,
        val keyframes: List<TransformKeyframe>,
        val subtitle: SubtitleStyle? = null,
        /** The fade handles, in frames (see [ClipFades]); subtitles have none. */
        val fadeInFrames: Float = 0f,
        val fadeOutFrames: Float = 0f,
        /** How they bend ([ClipFades.shape]). */
        val fadeInCurve: Float = 0f,
        val fadeOutCurve: Float = 0f,
    )

    class ClipMix(
        val gain: Float,
        val fadeInUs: Long,
        val fadeOutUs: Long,
        /** How the fades bend, −1…1 ([ClipFades.shape]). */
        val fadeInCurve: Float = 0f,
        val fadeOutCurve: Float = 0f,
        /** Volume keyframes: times (µs from the clip's start) and their levels (dB); null = fixed [gain]. */
        val keyTimesUs: LongArray? = null,
        val keyDb: FloatArray? = null,
        /** Per volume keyframe its slopeIn and slopeOut (see [KeyEase]); null = linear. */
        val keyEase: FloatArray? = null,
        /** Where the clip starts on the timeline (µs): the lane's ducking runs on timeline time. */
        val startUs: Long = 0L,
        /** The clip's own EQ while it changes the sound, before the track's; null = none. */
        val eq: TrackEq? = null,
        /** How the file's left and right channels reach the mix, before the EQ (Resolve's clip attributes). */
        val channels: AudioChannels = AudioChannels.STEREO,
        /** The clip's pan, −1…1 (a balance before the track's); with [panTimesUs], its keyframes (µs from the clip's start). */
        val pan: Float = 0f,
        val panTimesUs: LongArray? = null,
        val panValues: FloatArray? = null,
        /** Per pan keyframe its slopeIn and slopeOut (see [KeyEase]); null = linear. */
        val panEase: FloatArray? = null,
    ) {
        /** Clip gain [timeUs] into the clip: interpolated in dB between volume keyframes, eased where they say so. */
        fun gainAt(timeUs: Long): Float {
            val times = keyTimesUs ?: return gain
            val db = keyDb ?: return gain
            return dbToGain(interpolate(times, db, keyEase, timeUs))
        }

        /** Whether the pan changes over the clip (else [pan] holds throughout). */
        val panMoves: Boolean get() = panTimesUs != null

        /** Clip pan [timeUs] into the clip: interpolated between pan keyframes, eased where they say so. */
        fun panAt(timeUs: Long): Float {
            val times = panTimesUs ?: return pan
            val values = panValues ?: return pan
            return interpolate(times, values, panEase, timeUs)
        }

        private fun interpolate(times: LongArray, values: FloatArray, ease: FloatArray?, timeUs: Long): Float {
            if (timeUs <= times[0]) return values[0]
            val last = times.lastIndex
            if (timeUs >= times[last]) return values[last]
            var i = 0
            while (i + 1 < last && times[i + 1] <= timeUs) i++
            val linear = (timeUs - times[i]).toFloat() / (times[i + 1] - times[i])
            val t = ease?.let { easedProgress(linear, it[2 * i + 1], it[2 * i + 2]) } ?: linear
            return values[i] + (values[i + 1] - values[i]) * t
        }
    }

    /**
     * A track's fader gain and pan, its equaliser and dynamics when they change the sound (null otherwise),
     * and how deep its ducking dips (dB, 0 = none; the envelope comes from [duck]).
     */
    class LaneMix(
        val gain: Float,
        val pan: Float,
        val eq: TrackEq? = null,
        val dynamics: TrackDynamics? = null,
        val duckDepthDb: Float = 0f,
        /** The lane's noise reduction while it is on (its settings apply at once; switching it on rebuilds the sound). */
        val noiseReduction: TrackNoiseReduction? = null,
        /** The lane's de-esser while it is on (live). */
        val deEsser: TrackDeEsser? = null,
        /** The lane's reverb while it is on (live; the room's tail is planned into the silence after each clip). */
        val reverb: TrackReverb? = null,
        /** The equaliser of the submix bus the track goes to, while it changes the sound (last, after the reverb). */
        val busEq: TrackEq? = null,
    )

    /** What a clip's look is made from; [lutGeneration] re-bakes grades whose imported LUT arrived or went. */
    private data class LookKey(val grade: ColorGrade, val clip: Clip, val fps: Int, val lutGeneration: Int, val stab: CameraPath? = null)

    private val looks = ConcurrentHashMap<String, VideoLook>()
    private val lookKeys = ConcurrentHashMap<String, LookKey>()
    /** Baked LUTs by primary grade, and by (primary, window grade) for window composites. */
    private val lutCache = ConcurrentHashMap<Any, IntArray>()
    private val clipMixes = ConcurrentHashMap<String, ClipMix>()
    private val laneMixes = ConcurrentHashMap<String, LaneMix>()
    private val titleLooks = ConcurrentHashMap<String, TitleLook>()
    private val ducks = ConcurrentHashMap<String, DuckEnvelope>()
    @Volatile private var fps = 30
    @Volatile private var lutGeneration = 0

    /** Clip whose left half shows the ungraded source (color-page compare), or null. */
    @Volatile var compareClipId: String? = null

    /** Horizontal split position (0..1) of the compare view. */
    @Volatile var compareSplit: Float = 0.5f

    /** Clip whose window node shows as a matte: its selection in colour, the rest flat grey (color page), or null. */
    @Volatile var highlightClipId: String? = null

    /** Clip whose keyer shows its matte, white where the picture stays (the Edit page's matte view), or null. */
    @Volatile var keyMatteClipId: String? = null

    fun videoLook(clipId: String): VideoLook? = looks[clipId]
    fun clipMix(clipId: String): ClipMix? = clipMixes[clipId]
    fun laneMix(trackId: String): LaneMix? = laneMixes[trackId]
    fun titleLook(clipId: String): TitleLook? = titleLooks[clipId]

    /** The analysed camera paths by file uri (see [setStabilization]). */
    @Volatile private var paths: Map<String, CameraPath> = emptyMap()

    /**
     * The camera paths of the files analysed so far, by uri (analysed off the main thread, so they arrive apart from
     * [update]); a stabilized clip whose file has none yet plays unsteadied. Takes effect at the next [update].
     */
    fun setStabilization(paths: Map<String, CameraPath>) {
        this.paths = paths
    }

    /** [trackId]'s ducking envelope over the timeline, or null (see [setDucks]). */
    fun duck(trackId: String): DuckEnvelope? = ducks[trackId]

    /**
     * The ducking envelopes by track (they come from the media's loudness, measured off the main thread,
     * so they arrive apart from [update]); tracks left out stop ducking.
     */
    fun setDucks(envelopes: Map<String, DuckEnvelope>) {
        ducks.putAll(envelopes)
        (ducks.keys - envelopes.keys).forEach { ducks.remove(it) }
    }

    /** Timeline frame rate the keyframes are counted in. */
    val frameRate: Int get() = fps

    /** Recomputes every changed look and mix from [doc]. Returns true when anything visible changed. */
    fun update(doc: ProjectDocument): Boolean {
        var changed = false
        val live = mutableSetOf<String>()
        fps = doc.format.fps.coerceAtLeast(1)
        // LUTs baked before the library changed may name an imported LUT that was missing then.
        val generation = luts.generation
        if (generation != lutGeneration) {
            lutGeneration = generation
            lutCache.clear()
        }
        val titles = mutableSetOf<String>()
        for (clip in doc.timeline.clips) {
            when (clip.kind) {
                ClipKind.VIDEO -> {
                    live += clip.id
                    // A stabilized clip follows its file's camera path over the part of the file it shows.
                    val path = clip.stabilization?.takeIf { it.enabled && !clip.reverse }?.let { paths[doc.asset(clip.assetId)?.uri] }
                    // A trim changes nothing the shader reads — unless Dynamic Zoom spreads its move over the clip's length, or
                    // a fade out ends at its end, or stabilization follows the frames it shows.
                    val frames = if (clip.dynamicZoom != null || clip.fadeOutSeconds > 0f || path != null) clip.durationFrames else 1
                    val sourceIn = if (path != null) clip.sourceInMs else 0
                    val key = LookKey(clip.grade, clip.copy(start = 0, trackId = "", name = "", sourceInMs = sourceIn, durationFrames = frames), fps, generation, path)
                    if (lookKeys[clip.id] != key) {
                        lookKeys[clip.id] = key
                        looks[clip.id] = lookFor(clip, path)
                        changed = true
                    }
                }
                ClipKind.AUDIO -> {
                    val keys = clip.volumeKeyframes
                    clipMixes[clip.id] = ClipMix(
                        gain = dbToGain(clip.volumeDb),
                        fadeInUs = (clip.fadeInSeconds * 1_000_000).toLong(),
                        fadeOutUs = (clip.fadeOutSeconds * 1_000_000).toLong(),
                        fadeInCurve = clip.fadeInCurve,
                        fadeOutCurve = clip.fadeOutCurve,
                        keyTimesUs = if (keys.isEmpty()) null else LongArray(keys.size) { keys[it].frame * 1_000_000L / fps },
                        keyDb = if (keys.isEmpty()) null else FloatArray(keys.size) { keys[it].volumeDb },
                        keyEase = if (keys.all { it.ease == KeyEase.LINEAR }) {
                            null
                        } else {
                            FloatArray(keys.size * 2) { i -> keys[i / 2].ease.let { if (i % 2 == 0) it.slopeIn else it.slopeOut } }
                        },
                        startUs = clip.start * 1_000_000L / fps,
                        eq = clip.eq.takeIf { it.isActive },
                        channels = clip.channels,
                        pan = clip.pan,
                        panTimesUs = clip.panKeyframes.takeIf { it.isNotEmpty() }?.let { k -> LongArray(k.size) { k[it].frame * 1_000_000L / fps } },
                        panValues = clip.panKeyframes.takeIf { it.isNotEmpty() }?.let { k -> FloatArray(k.size) { k[it].value } },
                        panEase = clip.panKeyframes.takeIf { k -> k.isNotEmpty() && k.any { it.ease != KeyEase.LINEAR } }
                            ?.let { k -> FloatArray(k.size * 2) { i -> k[i / 2].ease.let { if (i % 2 == 0) it.slopeIn else it.slopeOut } } },
                    )
                }
                ClipKind.TITLE -> clip.title?.let { style ->
                    titles += clip.id
                    val previous = titleLooks[clip.id]
                    val subtitle = doc.timeline.subtitleStyleOf(clip)
                    val fadeIn = if (ClipFades.takes(clip)) clip.fadeInSeconds * fps else 0f
                    val fadeOut = if (ClipFades.takes(clip)) clip.fadeOutSeconds * fps else 0f
                    if (previous == null || previous.style != style || previous.transform != clip.transform ||
                        previous.keyframes != clip.keyframes || previous.subtitle != subtitle ||
                        previous.fadeInFrames != fadeIn || previous.fadeOutFrames != fadeOut ||
                        previous.fadeInCurve != clip.fadeInCurve || previous.fadeOutCurve != clip.fadeOutCurve
                    ) {
                        titleLooks[clip.id] = TitleLook(style, clip.transform, clip.keyframes, subtitle, fadeIn, fadeOut, clip.fadeInCurve, clip.fadeOutCurve)
                        changed = true
                    }
                }
            }
        }
        (looks.keys - live).forEach { looks.remove(it); lookKeys.remove(it); changed = true }
        (titleLooks.keys - titles).forEach { titleLooks.remove(it); changed = true }
        val master = dbToGain(doc.mixer.masterVolumeDb)
        doc.timeline.tracks.filter { it.kind == TrackKind.AUDIO }.forEach { track ->
            val channel = doc.mixer.channel(track.id)
            val audible = doc.mixer.isAudible(track.id)
            // Ducking dips only with its key track there (a deleted key leaves the track as it is).
            val ducking = channel.ducking
            val keyed = ducking.isActive && ducking.keyTrackId != track.id &&
                doc.timeline.track(ducking.keyTrackId.orEmpty())?.kind == TrackKind.AUDIO
            // A submix bus's fader folds into its tracks' (exact: the bus sums them, then scales).
            laneMixes[track.id] = LaneMix(
                if (audible) doc.mixer.laneGain(track.id) * master else 0f,
                channel.pan,
                channel.eq.takeIf { it.isActive },
                channel.dynamics.takeIf { it.isActive },
                if (keyed) ducking.depthDb else 0f,
                channel.noiseReduction.takeIf { it.isActive },
                channel.deEsser.takeIf { it.isActive },
                channel.reverb.takeIf { it.isActive },
                doc.mixer.busOf(track.id)?.eq?.takeIf { it.isActive },
            )
        }
        if (lutCache.size > 64) lutCache.clear()
        return changed
    }

    private fun lookFor(clip: Clip, path: CameraPath? = null): VideoLook {
        val stab = path?.let { stabilization(clip, it) }
        val fx = FloatArray(8)
        val fxKeys = mutableListOf<FloatArray>()
        clip.effects.filter { it.enabled }.forEach { effect ->
            val index = when (effect.type) {
                EffectType.GAUSSIAN_BLUR -> 0
                EffectType.SHARPEN -> 1
                EffectType.VIGNETTE -> 2
                EffectType.FILM_GRAIN -> 3
                EffectType.MIRROR -> MIRROR
                EffectType.MOSAIC -> 5
                EffectType.CHROMATIC -> 6
                EffectType.GLOW -> 7
            }
            if (effect.keyframes.isEmpty()) {
                fx[index] = (fx[index] + effect.amount / 100f).coerceIn(0f, 1f)
            } else {
                fxKeys += FloatArray(1 + effect.keyframes.size * FX_KEY_STRIDE).also { run ->
                    run[0] = index.toFloat()
                    effect.keyframes.forEachIndexed { i, k ->
                        val o = 1 + i * FX_KEY_STRIDE
                        run[o] = k.frame.toFloat()
                        run[o + 1] = k.amount / 100f
                        run[o + 2] = k.ease.slopeIn
                        run[o + 3] = k.ease.slopeOut
                    }
                }
            }
        }
        if (fx[MIRROR] > 0f) fx[MIRROR] = 1f
        val t = clip.transform
        // The LUTs hold the colour; the noise reduction runs in the shader before them.
        val primary = clip.grade.primary.colorOnly
        // A window with a neutral grade changes nothing, but its matte can still be highlighted.
        val window = clip.grade.window
        // Keyed node 1: a LUT for every keyframe's correction (and its window composite); the shader dissolves between neighbours.
        val keys = clip.grade.keyframes.takeIf { it.isNotEmpty() }?.map { it.copy(grade = it.grade.colorOnly) }
        val keyLuts = keys?.map { primaryLut(it.grade) }
        val lutPixels = keyLuts?.first() ?: if (primary.isNeutral) null else lutCache.getOrPut(primary) { bake(primary, null) }
        return VideoLook(
            lutPixels = lutPixels,
            scale = t.scalePercent / 100f,
            offsetX = t.positionX / REFERENCE_WIDTH,
            offsetY = t.positionY / REFERENCE_HEIGHT,
            rotationRadians = (t.rotationDegrees * PI / 180.0).toFloat(),
            opacity = t.opacityPercent / 100f,
            fx = fx,
            keyframes = if (clip.keyframes.isEmpty()) null else Array(TRANSFORM_VALUES) { j -> paramTrack(clip, CurveParam.entries[j]) },
            windowLutPixels = window?.let { w -> if (keys != null) windowLut(keys.first().grade, w) else windowLut(primary, w) },
            postLutPixels = clip.grade.activeSerial.takeIf { it.isNotEmpty() }?.let { serial -> lutCache.getOrPut(SerialKey(serial)) { bakeChain(serial) } },
            window = window?.let(::windowParams),
            qualifier = window?.qualifier?.let(::qualifierParams),
            windowKeys = window?.let(::windowKeyRuns),
            // The full frame masks nothing.
            mask = clip.mask?.takeIf { it.shape != WindowShape.FULL }?.let(::windowParams),
            maskKeys = clip.mask?.takeIf { it.shape != WindowShape.FULL }?.let(::windowKeyRuns),
            gradeKeys = keys?.let { list ->
                FloatArray(list.size * GRADE_KEY_STRIDE).also { out ->
                    list.forEachIndexed { i, k ->
                        out[i * GRADE_KEY_STRIDE] = k.frame.toFloat()
                        out[i * GRADE_KEY_STRIDE + 1] = k.ease.slopeIn
                        out[i * GRADE_KEY_STRIDE + 2] = k.ease.slopeOut
                    }
                }
            },
            keyLuts = keyLuts,
            keyWindowLuts = if (window == null) null else keys?.map { windowLut(it.grade, window) },
            crop = if (t.hasCropSettings) cropParams(t) else NO_CROP,
            zoom = clip.dynamicZoom,
            clipFrames = clip.durationFrames,
            fadeInFrames = clip.fadeInSeconds * fps,
            fadeOutFrames = clip.fadeOutSeconds * fps,
            fadeInCurve = clip.fadeInCurve,
            fadeOutCurve = clip.fadeOutCurve,
            noiseReduction = SpatialNrKernel.uniforms(clip.grade.spatialNr),
            midDetail = MidDetailKernel.uniform(clip.grade.midDetail),
            temporalNr = TemporalNrKernel.uniforms(clip.grade.temporalNr),
            chromaKey = clip.chromaKey?.let(ChromaKeyKernel::uniforms),
            transitionStyle = clip.transitionIn?.let(::transitionParams) ?: NO_TRANSITION_STYLE,
            fxKeys = fxKeys.takeIf { it.isNotEmpty() },
            stabX = stab?.first,
            stabY = stab?.second,
            stabScale = stab?.third ?: 1f,
        )
    }

    /** [clip]'s stabilization along [path], frame by frame ([StabilizationUseCase.samples]): x, y and the zoom. */
    private fun stabilization(clip: Clip, path: CameraPath): Triple<FloatArray, FloatArray, Float> {
        val samples = stabilizer.samples(clip, path, fps)
        return Triple(samples.x, samples.y, samples.scale)
    }

    /** [t]'s style as the shader takes it (see [VideoLook.transitionStyle]). */
    private fun transitionParams(t: Transition): FloatArray {
        // A colour dip has no edge: its colour takes the border colour's place.
        val color = if (t.type.colored) t.color.argb else t.borderColor.argb
        return floatArrayOf(
            t.ease.slopeStart,
            t.ease.slopeEnd,
            if (t.reverse && t.type.directional) 1f else 0f,
            if (t.type.edged) t.softness / Transition.MAX_STYLE.toFloat() * Transition.SOFTNESS_SPAN else 0f,
            if (t.type.edged) t.border / Transition.MAX_STYLE.toFloat() * Transition.BORDER_SPAN else 0f,
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            if (t.type.angled) Math.toRadians(t.travelAngle.toDouble()).toFloat() else 0f,
        )
    }

    /**
     * [param]'s keys of [clip] as runs of [frame, value, slopeIn, slopeOut], the value in the shader's unit (scale and
     * opacity as fractions, offsets and crops as shares of the reference raster, rotation in radians); null without keys.
     */
    private fun paramTrack(clip: Clip, param: CurveParam): FloatArray? {
        val track = clip.keyframes.track(param).takeIf { it.isNotEmpty() } ?: return null
        return FloatArray(track.size * PARAM_KEY_STRIDE).also { out ->
            track.forEachIndexed { i, k ->
                val o = i * PARAM_KEY_STRIDE
                out[o] = k.frame.toFloat()
                out[o + 1] = when (param) {
                    CurveParam.SCALE, CurveParam.OPACITY -> k.value / 100f
                    CurveParam.POSITION_X, CurveParam.CROP_LEFT, CurveParam.CROP_RIGHT -> k.value / REFERENCE_WIDTH
                    CurveParam.POSITION_Y, CurveParam.CROP_TOP, CurveParam.CROP_BOTTOM -> k.value / REFERENCE_HEIGHT
                    CurveParam.ROTATION -> (k.value * PI / 180.0).toFloat()
                    CurveParam.CROP_SOFTNESS -> k.value / 100f * ClipTransform.CROP_SOFTNESS_SPAN
                }
                out[o + 2] = k.ease.slopeIn
                out[o + 3] = k.ease.slopeOut
            }
        }
    }

    /** [t]'s crop as the shader takes it: [left, right, top, bottom] shares of the frame, softness as a share of its height. */
    private fun cropParams(t: ClipTransform): FloatArray = floatArrayOf(
        t.cropLeft / REFERENCE_WIDTH,
        t.cropRight / REFERENCE_WIDTH,
        t.cropTop / REFERENCE_HEIGHT,
        t.cropBottom / REFERENCE_HEIGHT,
        t.cropSoftness / 100f * ClipTransform.CROP_SOFTNESS_SPAN,
    )

    /** Node 1's LUT for [grade] (identity for a neutral one); equal grades share one array. */
    private fun primaryLut(grade: ColorGrade): IntArray = lutCache.getOrPut(grade) { bake(grade, null) }

    /** Node 2's composite after node 1's [primary]: [window]'s grade on top, or node 1 alone when that grade is neutral. */
    private fun windowLut(primary: ColorGrade, window: PowerWindow): IntArray =
        if (window.grade.isNeutral) primaryLut(primary) else lutCache.getOrPut(primary to window.grade) { bake(primary, window.grade) }

    private fun qualifierParams(q: HslQualifier): FloatArray = floatArrayOf(
        q.hueCenter, q.hueWidth, q.hueSoft, if (q.invert) 1f else 0f,
        q.satLow, q.satHigh, q.satSoft, 0f,
        q.lumLow, q.lumHigh, q.lumSoft, 0f,
        // Matte finesse: clean black / white as the matte's black and white points, and the blur radius.
        q.cleanBlack / 100f * HslQualifier.MAX_CLEAN, 1f - q.cleanWhite / 100f * HslQualifier.MAX_CLEAN, q.blurRadius, 0f,
    )

    /** [w]'s keyframes as [VideoLook.windowKeys] lays them out; null without any. */
    private fun windowKeyRuns(w: PowerWindow): FloatArray? = w.keyframes.takeIf { it.isNotEmpty() }?.let { keys ->
        FloatArray(keys.size * WINDOW_KEY_STRIDE).also { out ->
            keys.forEachIndexed { i, k ->
                val o = i * WINDOW_KEY_STRIDE
                val g = k.geometry
                out[o] = k.frame.toFloat()
                out[o + 1] = g.centerX
                out[o + 2] = g.centerY
                out[o + 3] = g.size
                out[o + 4] = g.aspect
                out[o + 5] = (g.angle * PI / 180.0).toFloat()
                out[o + 6] = g.softness
                out[o + 7] = k.ease.slopeIn
                out[o + 8] = k.ease.slopeOut
            }
        }
    }

    private fun windowParams(w: PowerWindow): FloatArray = floatArrayOf(
        (w.shape.ordinal + 1).toFloat(),
        w.centerX,
        w.centerY,
        w.size,
        w.aspect,
        (w.angle * PI / 180.0).toFloat(),
        w.softness,
        if (w.invert) 1f else 0f,
    )

    /** The LUT cache's key for a serial chain (a list of grades would clash with nothing, but say what it is). */
    private data class SerialKey(val grades: List<ColorGrade>)

    private fun bakeChain(grades: List<ColorGrade>): IntArray {
        val cube = lutBuilder.chain(grades)
        val n = cube.size
        val pixels = IntArray(n * n * n)
        for (r in 0 until n) for (g in 0 until n) for (b in 0 until n) pixels[(r * n + g) * n + b] = cube[r][g][b]
        return pixels
    }

    private fun bake(grade: ColorGrade, then: ColorGrade?): IntArray {
        val cube = lutBuilder(grade, then)
        val n = cube.size
        val pixels = IntArray(n * n * n)
        for (r in 0 until n) for (g in 0 until n) for (b in 0 until n) pixels[(r * n + g) * n + b] = cube[r][g][b]
        return pixels
    }

    companion object {
        const val LUT_SIZE = 33

        /** What [VideoLook.transformAt] writes: scale, offsets, rotation, opacity and the five crop values. */
        const val TRANSFORM_VALUES = 10

        /** A parameter key in [VideoLook.keyframes]: frame, value, slopeIn, slopeOut. */
        private const val PARAM_KEY_STRIDE = 4
        private val NO_CROP = FloatArray(5)

        /** A steady transition without direction, edge or border (white, were it drawn). */
        val NO_TRANSITION_STYLE = floatArrayOf(1f, 1f, 0f, 0f, 0f, 1f, 1f, 1f, 0f)

        /** The mirror's slot in [VideoLook.fx]. */
        private const val MIRROR = 4

        /** A keyframed effect's values per keyframe in [VideoLook.fxKeys]: frame, amount, slopeIn, slopeOut. */
        private const val FX_KEY_STRIDE = 4

        /** A keyframed effect's [run] (see [VideoLook.fxKeys]) [clipFrame] frames into the clip, held outside its keyframes. */
        private fun keyedAmount(run: FloatArray, clipFrame: Float): Float {
            val n = (run.size - 1) / FX_KEY_STRIDE
            var i = 0
            while (i + 1 < n && run[1 + (i + 1) * FX_KEY_STRIDE] <= clipFrame) i++
            val a = 1 + i * FX_KEY_STRIDE
            if (i + 1 >= n || clipFrame <= run[a]) return run[a + 1]
            val b = a + FX_KEY_STRIDE
            val t = easedProgress((clipFrame - run[a]) / (run[b] - run[a]), run[a + 3], run[b + 2])
            return run[a + 1] + (run[b + 1] - run[a + 1]) * t
        }
        private const val WINDOW_KEY_STRIDE = 9
        private const val GRADE_KEY_STRIDE = 3

        /** Transform offsets are authored against a 1920×1080 raster (±960 / ±540 = half a frame). */
        private const val REFERENCE_WIDTH = 1920f
        private const val REFERENCE_HEIGHT = 1080f

        fun dbToGain(db: Float): Float = if (db <= -60f) 0f else 10f.pow(db / 20f)
    }
}
