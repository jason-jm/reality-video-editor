package com.example.prismstudio.p40b43a88.domain.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Picture, sound, or subtitles (Resolve's subtitle track: title clips burned in at the bottom, exportable as SRT). */
enum class TrackKind { VIDEO, AUDIO, SUBTITLE }

data class Track(
    val id: String,
    val name: String,
    val kind: TrackKind,
    val enabled: Boolean = true,
    val locked: Boolean = false,
    /** How a subtitle track shows its lines (other tracks ignore it). */
    val subtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT,
    /** A subtitle track's language (Resolve: one subtitle track per language); null on other tracks. */
    val language: SubtitleLanguage? = null,
) {
    /** A subtitle track's language, Simplified Chinese for one made before languages. */
    val subtitleLanguage: SubtitleLanguage get() = language ?: SubtitleLanguage.DEFAULT

    /** Whether clips of [clip] kind may sit on this track: sound on audio tracks, titles on subtitle tracks, the rest on video tracks. */
    fun accepts(clip: ClipKind): Boolean = when (kind) {
        TrackKind.AUDIO -> clip == ClipKind.AUDIO
        TrackKind.VIDEO -> clip != ClipKind.AUDIO
        TrackKind.SUBTITLE -> clip == ClipKind.TITLE
    }
}

enum class ClipKind { VIDEO, AUDIO, TITLE }

/** How a sound clip's left and right channels reach the mix (`clip-channels-row`). */
enum class AudioChannels(val label: String, val note: String) {
    STEREO("立体声", "左右声道保持原样"),
    SWAP("互换", "左右互换：左声道放到右边，右声道放到左边"),
    LEFT("仅左", "只用左声道，两边都听到它（适合只录在左声道的领夹麦）"),
    RIGHT("仅右", "只用右声道，两边都听到它（适合只录在右声道的领夹麦）"),
    MONO("单声道", "左右声道混合为一路，两边相同，各为原来的一半");

    /** [frame]'s first two samples (left, right) remapped in place; other channels and a mono frame stay. */
    fun map(frame: FloatArray, channels: Int) {
        if (channels < 2 || this == STEREO) return
        val l = frame[0]
        val r = frame[1]
        when (this) {
            SWAP -> { frame[0] = r; frame[1] = l }
            LEFT -> frame[1] = l
            RIGHT -> frame[0] = r
            MONO -> { val m = (l + r) * 0.5f; frame[0] = m; frame[1] = m }
            STEREO -> Unit
        }
    }
}

/** The speed presets the inspector offers. */
enum class PlaybackSpeed(val factor: Float, val label: String) {
    QUARTER(0.25f, "0.25×"),
    HALF(0.5f, "0.5×"),
    NORMAL(1f, "1×"),
    DOUBLE(2f, "2×"),
    QUADRUPLE(4f, "4×"),
}

/**
 * A clip's playback speed: source time per timeline time — 1 as shot, 2 twice as fast. One of the
 * [PlaybackSpeed] presets, or any value in [MIN]…[MAX] (Fit to Fill retimes a source range to a gap).
 */
@JvmInline
value class ClipSpeed(val factor: Float) {
    /** The preset this speed is, if it is one. */
    val preset: PlaybackSpeed? get() = PlaybackSpeed.entries.firstOrNull { abs(it.factor - factor) < PRESET_TOLERANCE }

    val isNormal: Boolean get() = preset == PlaybackSpeed.NORMAL

    /** "1×", "0.25×", "1.33×". */
    val label: String
        get() = preset?.label ?: (String.format(Locale.ROOT, "%.2f", factor).trimEnd('0').trimEnd('.') + "×")

    companion object {
        val NORMAL = ClipSpeed(1f)
        const val MIN = 0.1f
        const val MAX = 10f
        private const val PRESET_TOLERANCE = 1e-4f

        fun of(preset: PlaybackSpeed) = ClipSpeed(preset.factor)
    }
}

/**
 * One stretch of a clip at its own speed — Resolve's Retime Controls: speed points split a clip into sections, each playing
 * its source at its own speed. [frames] of the timeline; a clip's sections add up to its length. [rampFrames] smooths the
 * speed point before the section (Resolve's speed ramp): over that many frames, centred on the point, the speed eases from
 * the section before's into this one's; 0 changes it at once. The first section has no point before it.
 */
data class SpeedSection(val frames: Int, val speed: ClipSpeed, val rampFrames: Int = 0)

/**
 * The shape of a speed ramp (`speed-ramp-row`): smoothstep, so the speed leaves one section's and settles into the next's
 * without a jolt. Being symmetric, a ramp centred on its point plays exactly the source a sudden change there would — the
 * clip's length and every frame outside the ramp stay where they were.
 */
internal object SpeedRamp {
    /** The share of the change made [u] (0..1) of the way through the ramp. */
    fun eased(u: Double): Double = u * u * (3 - 2 * u)

    /**
     * How much further into the source (in units of the ramp's length × the speed change) the ramp has played than a
     * sudden change at its middle would have, [u] of the way through it: 0 at both ends.
     */
    fun lead(u: Double): Double {
        val t = u.coerceIn(0.0, 1.0)
        // ∫₀ᵗ eased = t³ − t⁴/2; a sudden change has made (t − ½) of it past the middle.
        return t * t * t - t * t * t * t / 2 - maxOf(0.0, t - 0.5)
    }
}

data class ClipTransform(
    val scalePercent: Int = 100,
    val positionX: Int = 0,
    val positionY: Int = 0,
    val rotationDegrees: Int = 0,
    val opacityPercent: Int = 100,
    /**
     * Resolve's Cropping: how far each edge of the picture is cut back, in pixels of the 1920×1080 reference
     * raster the picture is fitted into. The crop belongs to the picture, so it scales, moves and turns with it;
     * what is cut away shows the layers beneath (black at the bottom).
     */
    val cropLeft: Int = 0,
    val cropRight: Int = 0,
    val cropTop: Int = 0,
    val cropBottom: Int = 0,
    /** How gently the cut edges fade in, 0–100: 100 fades over a tenth of the frame's height, inside the cut. */
    val cropSoftness: Int = 0,
) {
    val isCropped: Boolean get() = cropLeft > 0 || cropRight > 0 || cropTop > 0 || cropBottom > 0

    /** Any crop setting away from its default (softness alone included). */
    val hasCropSettings: Boolean get() = isCropped || cropSoftness > 0

    companion object {
        /** The reference raster transforms and crops are authored against. */
        const val REFERENCE_WIDTH = 1920
        const val REFERENCE_HEIGHT = 1080

        /** The share of the frame's height a crop softness of 100 fades over. */
        const val CROP_SOFTNESS_SPAN = 0.1f
    }
}

/** The inspector's 裁切 controls: one edge of [ClipTransform]'s crop each, and the softness. */
enum class CropControl(val label: String, val max: Int) {
    LEFT("左侧", ClipTransform.REFERENCE_WIDTH),
    RIGHT("右侧", ClipTransform.REFERENCE_WIDTH),
    TOP("顶部", ClipTransform.REFERENCE_HEIGHT),
    BOTTOM("底部", ClipTransform.REFERENCE_HEIGHT),
    SOFTNESS("柔化", 100),
    ;

    fun read(t: ClipTransform): Int = when (this) {
        LEFT -> t.cropLeft
        RIGHT -> t.cropRight
        TOP -> t.cropTop
        BOTTOM -> t.cropBottom
        SOFTNESS -> t.cropSoftness
    }

    fun write(t: ClipTransform, value: Int): ClipTransform = when (this) {
        LEFT -> t.copy(cropLeft = value)
        RIGHT -> t.copy(cropRight = value)
        TOP -> t.copy(cropTop = value)
        BOTTOM -> t.copy(cropBottom = value)
        SOFTNESS -> t.copy(cropSoftness = value)
    }
}

/**
 * One-tap picture layouts (positions on the 1920×1080 reference raster, +Y up): full frame, or a
 * 35 % picture-in-picture inset 48 px from a corner.
 */
enum class Placement(val label: String, val scalePercent: Int, val positionX: Int, val positionY: Int) {
    FULL("全屏", 100, 0, 0),
    TOP_LEFT("左上", PIP_SCALE, -PIP_X, PIP_Y),
    TOP_RIGHT("右上", PIP_SCALE, PIP_X, PIP_Y),
    BOTTOM_LEFT("左下", PIP_SCALE, -PIP_X, -PIP_Y),
    BOTTOM_RIGHT("右下", PIP_SCALE, PIP_X, -PIP_Y),
    ;

    fun matches(t: ClipTransform): Boolean =
        t.scalePercent == scalePercent && t.positionX == positionX && t.positionY == positionY && t.rotationDegrees == 0

    companion object {
        /** The preset [t] is exactly in, if any. */
        fun of(t: ClipTransform): Placement? = entries.firstOrNull { it.matches(t) }
    }
}

private const val PIP_SCALE = 35
/** 960 − 35 % of 1920 / 2 − 48 px margin. */
private const val PIP_X = 576
/** 540 − 35 % of 1080 / 2 − 48 px margin. */
private const val PIP_Y = 303

/** A clip's opening transition; projects store the names, the render's shader the order (add at the end). */
enum class TransitionType(val label: String) {
    CROSS_DISSOLVE("交叉溶解"),
    DIP_TO_BLACK("黑场过渡"),
    DIP_TO_WHITE("白场闪白"),

    /** Resolve's Dip To Color Dissolve: a dip through a colour picked for it ([Transition.color]). Projects store kinds by name. */
    DIP_TO_COLOR("颜色过渡"),
    PUSH("推入"),
    WIPE("划像"),
    ZOOM_BLUR("缩放模糊"),

    /** Resolve's Iris: the incoming picture opens as a circle from the centre to the corners. */
    IRIS("圆形划像"),

    /** Resolve's Clock Wipe: a hand sweeps clockwise from twelve o'clock, uncovering the incoming picture. */
    CLOCK_WIPE("时钟划像"),

    /** Resolve's Barn Door (split): the incoming picture opens from the centre line out to both sides. */
    BARN_DOOR("对开门"),

    /** Resolve's Blur Dissolve: the incoming picture fades in as it comes into focus. */
    BLUR_DISSOLVE("模糊溶解"),
    ;

    /** Both pictures show at once while it plays: it uncovers the outgoing one (a dip shows one at a time). */
    val reveals: Boolean get() = !dips && this != ZOOM_BLUR

    /** Resolve's Dip to Color: the outgoing picture fades to a colour by the middle, the incoming one up from it. */
    val dips: Boolean get() = this == DIP_TO_BLACK || this == DIP_TO_WHITE || this == DIP_TO_COLOR

    /** Its colour is picked ([Transition.color]); the other dips pass through black or white. */
    val colored: Boolean get() = this == DIP_TO_COLOR

    /** Its edge travels at any angle ([Transition.angle], Resolve's Edge Wipe Angle), which also turns it round. */
    val angled: Boolean get() = this == WIPE

    /**
     * The outgoing picture plays in part of it — all of it for the revealing kinds, its first half for a dip — so it can
     * sit anywhere around the cut ([TransitionAlignment]); the zoom blur covers the cut with the incoming picture.
     */
    val showsOutgoing: Boolean get() = reveals || dips

    /** It moves one way, which Resolve's Reverse turns round. */
    val directional: Boolean get() = directions != null

    /** It uncovers the incoming picture along an edge, which can be soft and carry a coloured band. */
    val edged: Boolean get() = this == WIPE || this == IRIS || this == CLOCK_WIPE || this == BARN_DOOR

    /** Its two directions as the inspector names them, forward first; null when it has none. */
    val directions: Pair<String, String>?
        get() = when (this) {
            PUSH -> "从右推入" to "从左推入"
            IRIS -> "从中心展开" to "向中心收拢"
            CLOCK_WIPE -> "顺时针" to "逆时针"
            BARN_DOOR -> "从中间打开" to "从两侧合拢"
            else -> null
        }

    companion object {
        /** The inspector menu's groups, as Resolve's library sorts them: dissolves, motion, wipes. */
        val GROUPS: List<List<TransitionType>> = listOf(
            listOf(CROSS_DISSOLVE, BLUR_DISSOLVE, DIP_TO_BLACK, DIP_TO_WHITE, DIP_TO_COLOR),
            listOf(PUSH, ZOOM_BLUR),
            listOf(WIPE, BARN_DOOR, IRIS, CLOCK_WIPE),
        )
    }
}

/**
 * Resolve's transition Ease: how the change speeds up and slows down — a Hermite curve leaving its start at
 * [slopeStart] and reaching its end at [slopeEnd] (see [easedProgress]); 1 and 1 is a steady change.
 */
enum class TransitionEase(val label: String, val slopeStart: Float, val slopeEnd: Float) {
    LINEAR("线性", 1f, 1f),

    /** Starts slowly and speeds up. */
    IN("缓入", 0f, 1f),

    /** Slows down into its end. */
    OUT("缓出", 1f, 0f),
    IN_OUT("缓入缓出", 0f, 0f),
    ;

    /** How far the change has gone at [t] (0…1) of its length. */
    fun apply(t: Float): Float = easedProgress(t.coerceIn(0f, 1f), slopeStart, slopeEnd)
}

/**
 * Resolve's transition Alignment: where a transition that shows the outgoing picture sits around the cut. Before the cut
 * the incoming clip shows early, from before its in point (its head handles); after it the outgoing clip runs on past
 * its out point (its tail handles). Ordered as on the timeline: before, centred, after.
 */
enum class TransitionAlignment(val label: String) {
    /** Resolve's End on Edit: the transition ends at the cut. */
    END("剪辑点前"),

    /** Resolve's Center on Edit (its default): half before the cut, half after. */
    CENTER("居中"),

    /** Resolve's Start on Edit: the transition starts at the cut (projects from before alignments were added). */
    START("剪辑点后"),
    ;

    /** How many of [frames] lie before the cut. */
    fun lead(frames: Int): Int = when (this) {
        END -> frames
        CENTER -> frames / 2
        START -> 0
    }
}

/**
 * A clip's opening transition: its kind and length, and — as Resolve's transition inspector shapes it — where it sits
 * around the cut ([alignment]), its [ease], the other way round ([reverse], for the directional kinds) and, along a
 * wipe's edge, a soft width ([softness]) and a band of [borderColor] ([border]).
 */
data class Transition(
    val type: TransitionType,
    val frames: Int,
    val ease: TransitionEase = TransitionEase.LINEAR,
    val reverse: Boolean = false,
    /** The edge's soft width, 0–100 (100 = a quarter of the frame's height). */
    val softness: Int = 0,
    /** The band along the edge, 0–100 (100 = a twentieth of the frame's height); 0 = none. */
    val border: Int = 0,
    val borderColor: TitleColor = TitleColor.WHITE,
    val alignment: TransitionAlignment = TransitionAlignment.START,
    /** The colour a [TransitionType.DIP_TO_COLOR] passes through (kept when the kind changes, like the rest of the style). */
    val color: TitleColor = DIP_COLOR,
    /**
     * Which way a [TransitionType.WIPE]'s edge travels, in degrees clockwise from rightwards: 0 left to right, 90 top to
     * bottom, ±180 right to left. A wipe's [reverse] (from before angles) turns it round by half a turn.
     */
    val angle: Int = 0,
) {
    /** The wipe's angle with [reverse] folded in, −179…180. */
    val travelAngle: Int get() = normalizedAngle(angle + if (reverse) HALF_TURN else 0)

    companion object {
        const val MAX_STYLE = 100

        /** A new colour dip's colour: warm, and neither of the plain dips' black or white. */
        val DIP_COLOR = TitleColor.AMBER

        const val HALF_TURN = 180

        /** [degrees] as −179…180. */
        fun normalizedAngle(degrees: Int): Int = Math.floorMod(degrees + HALF_TURN - 1, 2 * HALF_TURN) - HALF_TURN + 1

        /** [softness] 100 as a share of the frame's height. */
        const val SOFTNESS_SPAN = 0.25f

        /** [border] 100 as a share of the frame's height. */
        const val BORDER_SPAN = 0.05f
    }
}

enum class EffectType(val label: String) {
    GAUSSIAN_BLUR("高斯模糊"),
    SHARPEN("锐化"),
    VIGNETTE("暗角"),
    FILM_GRAIN("胶片颗粒"),
    MIRROR("镜像"),
    MOSAIC("马赛克"),
    CHROMATIC("色差"),
    GLOW("发光"),
}

/** An effect's amount [frame] timeline frames after its clip starts (Resolve keyframes any parameter). */
data class EffectKeyframe(val frame: Int, val amount: Int, val ease: KeyEase = KeyEase.LINEAR)

data class ClipEffect(
    val id: String,
    val type: EffectType,
    val amount: Int = 50,
    val enabled: Boolean = true,
    /** Animates [amount] over the clip (frames from its start, sorted); empty = one fixed amount. */
    val keyframes: List<EffectKeyframe> = emptyList(),
)

/**
 * A placed piece of media. Timeline positions are frames at the project rate; the source in point is
 * kept in milliseconds so it maps directly onto media clipping configuration.
 */
data class Clip(
    val id: String,
    val trackId: String,
    val kind: ClipKind,
    val name: String,
    val start: Int,
    val durationFrames: Int,
    val assetId: String? = null,
    val sourceInMs: Long = 0,
    val speed: ClipSpeed = ClipSpeed.NORMAL,
    val reverse: Boolean = false,
    val transform: ClipTransform = ClipTransform(),
    /** Animates [transform] over the clip (frames from its start, sorted); empty = one fixed transform. */
    val keyframes: List<TransformKeyframe> = emptyList(),
    val volumeDb: Float = 0f,
    /** Animates [volumeDb] over the clip (frames from its start, sorted); empty = one fixed volume. */
    val volumeKeyframes: List<VolumeKeyframe> = emptyList(),
    /**
     * Resolve's clip pan, −1 (left) … 0 (centre) … +1 (right), before the track's: a balance on a stereo sound (the far
     * side turned down), a placement on a mono one. Sound only.
     */
    val pan: Float = 0f,
    /** Animates [pan] over the clip (frames from its start, sorted); empty = one fixed pan. */
    val panKeyframes: List<ParamKey> = emptyList(),
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    /**
     * Resolve's fade curve dot: how the fade in and the fade out bend, −1…1 — 0 linear in time, above it bowed up (in
     * sooner, out later), below it bowed down ([ClipFades.shape]).
     */
    val fadeInCurve: Float = 0f,
    val fadeOutCurve: Float = 0f,
    /** Resolve's clip equaliser: a sound clip's own EQ, before its track's ([TrackEq]; flat = none). Sound only. */
    val eq: TrackEq = TrackEq.FLAT,
    val transitionIn: Transition? = null,
    val effects: List<ClipEffect> = emptyList(),
    val grade: ColorGrade = ColorGrade.NEUTRAL,
    /**
     * Resolve's local grade versions: every version of the clip's grade, [grade] being the live one of version
     * [gradeVersion] (its entry here is refreshed whenever another version is taken up). Empty: one version, unnamed.
     */
    val gradeVersions: List<GradeVersion> = emptyList(),
    val gradeVersion: Int = 0,
    val title: TitleStyle? = null,
    val linkId: String? = null,
    /** Off (Resolve's Disable Clip): the clip keeps its place but neither plays nor renders. */
    val enabled: Boolean = true,
    /** Resolve's clip colour: a label the editor gives the clip (good take, to fix …); null shows its kind's colour. */
    val color: ClipColor? = null,
    /** Resolve's Dynamic Zoom over the whole clip, on top of [transform]; null = off. Pictures only. */
    val dynamicZoom: DynamicZoom? = null,
    /** Resolve's keyer: a green or blue screen in the picture keyed out, the tracks beneath showing through; null = off. Footage and stills only. */
    val chromaKey: ChromaKey? = null,
    /**
     * The picture's mask: shown inside the shape (outside it when inverted), clear elsewhere so the tracks beneath show —
     * what Resolve does with a window feeding the node tree's alpha output. Its geometry belongs to the picture as the
     * clip frames it (like the crop), so it moves, scales and turns with the transform; its keyframes count from the
     * clip's start. The window's grade and qualifier are unused. Null = none. Footage and stills only.
     */
    val mask: PowerWindow? = null,
    /** Resolve's Composite Mode: how the picture combines with the tracks beneath it. Pictures only. */
    val composite: CompositeMode = CompositeMode.NORMAL,
    /** Resolve's Retime Process: how the frames a slowed clip or slow footage lacks are made. Pictures only. */
    val retime: RetimeProcess = RetimeProcess.NEAREST,
    /**
     * Resolve's Scaling: how a picture of another shape or size than the timeline fills the frame; null follows the
     * project's setting for mismatched media ([ProjectFormat.scaling]). Pictures only.
     */
    val scaling: ClipScaling? = null,
    /**
     * A sound clip's cross fade from the sound that ends where it starts (Resolve's Cross Fade +3 dB), in frames: that
     * sound plays on past the cut, fading out as this one fades in. 0 = a cut. See `AudioCrossFades`.
     */
    val crossFadeFrames: Int = 0,
    /**
     * Resolve's clip attributes for sound: how the file's two channels reach the mix — as they are, swapped, one channel on
     * both sides (a lavalier recorded on one side only), or the two mixed to mono. Sound only; a mono file is the same on
     * both sides whatever it says.
     */
    val channels: AudioChannels = AudioChannels.STEREO,
    /**
     * Resolve's clip pitch: the sound shifted up or down by this many cents (100 a semitone, ±[MAX_PITCH_CENTS]) at its own
     * length and speed. Sound only; 0 = as recorded.
     */
    val pitchCents: Int = 0,
    /**
     * Resolve's speed points: the clip in sections of their own speed, adding up to [durationFrames] — two or more; empty =
     * [speed] throughout. Forward clips only: a reversed clip has none.
     */
    val speedSections: List<SpeedSection> = emptyList(),
    /**
     * Resolve's Stabilization: the camera's shake taken out of the footage, from its file's analysed camera path; null =
     * off. Footage only, forward clips only (a reversed clip plays a reversed copy).
     */
    val stabilization: ClipStabilization? = null,
    /**
     * Resolve's compound clip: instead of a file the clip plays timeline [compoundId] of the project — its picture tracks
     * when it sits on a video track, its sound tracks on an audio track — from [sourceInMs] into it. Null: a file or a title.
     */
    val compoundId: String? = null,
    /**
     * The angle a multicam clip's part plays (0 = angle 1): its inner video track V(n+1), or for the sound part A(n+1).
     * Cutting between angles splits the clip; each piece keeps its own.
     */
    val angle: Int = 0,
) {
    val end: Int get() = start + durationFrames

    /** A compound clip ([compoundId]). */
    val isCompound: Boolean get() = compoundId != null

    companion object {
        /** An octave either way. */
        const val MAX_PITCH_CENTS = 1200

        /** A multicam clip's most angles. */
        const val MAX_ANGLES = 9

        /** The frequency ratio [cents] shift makes: 2^(cents / 1200). */
        fun pitchRatio(cents: Int): Float = Math.pow(2.0, cents / 1200.0).toFloat()

        /** The timeline's badge for a sound shifted [cents]: 音调 +7, 音调 −3.5. */
        fun pitchBadge(cents: Int): String {
            val a = Math.abs(cents)
            val amount = if (a % 100 == 0) "${a / 100}" else String.format(java.util.Locale.US, "%.1f", a / 100f)
            return "音调 " + (if (cents > 0) "+" else "−") + amount
        }
    }
    operator fun contains(frame: Int): Boolean = frame >= start && frame < end
    fun sourceDurationMs(fps: Int): Long =
        if (speedSections.isEmpty()) (durationFrames * 1000L * speed.factor / fps).toLong() else sourceOffsetMs(durationFrames, fps).toLong()

    /** Speed points split it into sections of their own speed. */
    val retimed: Boolean get() = speedSections.isNotEmpty()

    /** The speed [frame] frames into the clip: its section's — the first section's before the clip, the last's after it. */
    fun speedAt(frame: Int): Float {
        if (speedSections.isEmpty()) return speed.factor
        var at = 0
        for (section in speedSections) {
            at += section.frames
            if (frame < at) return section.speed.factor
        }
        return speedSections.last().speed.factor
    }

    /** The speed at the clip's start (and before it). */
    val headSpeed: Float get() = speedAt(0)

    /** The speed at the clip's end (and past it). */
    val tailSpeed: Float get() = speedSections.lastOrNull()?.speed?.factor ?: speed.factor

    /**
     * Source ms (forward from the in point) the first [frames] frames of the clip play — negative before its start, at
     * the first section's speed, and past its end at the last's.
     */
    fun sourceOffsetMs(frames: Int, fps: Int): Double = sourceOffsetMs(frames.toDouble(), fps)

    /** [sourceOffsetMs] for a fractional number of [frames] (a moment between frames). */
    fun sourceOffsetMs(frames: Double, fps: Int): Double {
        val f = fps.coerceAtLeast(1)
        if (speedSections.isEmpty()) return frames * 1000.0 * speed.factor / f
        return offsetFrames(frames, ramps()) * 1000.0 / f
    }

    /** The source frames the first [frames] frames play: the sections' speeds as sudden changes, plus what each ramp plays ahead of that. */
    private fun offsetFrames(frames: Double, ramps: List<RampSpan>): Double =
        suddenOffsetFrames(frames) + ramps.sumOf { it.leadFrames(frames) - it.leadFrames(0.0) }

    /** The source frames the first [frames] frames play with every speed point a sudden change. */
    private fun suddenOffsetFrames(frames: Double): Double {
        if (frames <= 0) return frames * headSpeed
        var left = frames
        var played = 0.0
        for ((i, section) in speedSections.withIndex()) {
            val take = if (i == speedSections.lastIndex) left else minOf(left, section.frames.toDouble())
            played += take * section.speed.factor
            left -= take
            if (left <= 0.0) break
        }
        return played
    }

    /** The (fractional) frames into the clip at which [ms] of source past its in point plays: [sourceOffsetMs] inverted. */
    fun framesAtSourceMs(ms: Double, fps: Int): Double {
        val f = fps.coerceAtLeast(1)
        if (speedSections.isEmpty()) return ms * f / (1000.0 * speed.factor)
        val source = ms * f / 1000.0
        val sudden = suddenFramesAt(source)
        val ramps = ramps()
        if (ramps.isEmpty()) return sudden
        // The ramps move the moment a little from where sudden changes put it: bracket it there (the source only grows
        // with time), widening for very unequal speeds, then halve.
        val reach = ramps.sumOf { it.frames.toDouble() } + 1.0
        var lo = sudden - reach
        var hi = sudden + reach
        var widen = 0
        while (offsetFrames(lo, ramps) > source && widen++ < INVERSE_STEPS) lo -= reach * widen
        widen = 0
        while (offsetFrames(hi, ramps) < source && widen++ < INVERSE_STEPS) hi += reach * widen
        repeat(INVERSE_STEPS) {
            val mid = (lo + hi) / 2
            if (offsetFrames(mid, ramps) < source) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** [suddenOffsetFrames] inverted: the frames at which [source] frames have played. */
    private fun suddenFramesAt(source: Double): Double {
        if (source <= 0) return source / headSpeed
        var left = source
        var frames = 0.0
        for ((i, section) in speedSections.withIndex()) {
            val span = section.frames * section.speed.factor.toDouble()
            if (i == speedSections.lastIndex || left <= span) return frames + left / section.speed.factor
            left -= span
            frames += section.frames
        }
        return frames
    }

    /**
     * The speed playing at [frames] (fractional) into the clip: its section's, eased through a ramp — what a frame shows
     * moves at this speed. Before the clip it is the first section's (or its ramp's), past the end the last's.
     */
    fun rampedSpeedAt(frames: Double): Float {
        val nominal = speedAt(floor(frames).toInt())
        val ramp = ramps().firstOrNull { frames > it.from && frames < it.from + it.frames } ?: return nominal
        return (ramp.before + (ramp.after - ramp.before) * SpeedRamp.eased((frames - ramp.from) / ramp.frames)).toFloat()
    }

    /** Where each section starts, in frames into the clip (with the clip's length last); [0, length] without speed points. */
    val sectionBounds: List<Int>
        get() = if (speedSections.isEmpty()) listOf(0, durationFrames) else speedSections.runningFold(0) { at, s -> at + s.frames }

    /** The speed point before section [k] changes the speed (a point between two sections of one speed does nothing). */
    fun changesSpeedAt(k: Int): Boolean =
        k in 1..speedSections.lastIndex && speedSections[k].speed.factor != speedSections[k - 1].speed.factor

    /**
     * The ramp of the speed point before section [k] as it plays (`speed-ramp-row`): its own, but never reaching past
     * halfway to the next point that changes the speed on either side, so ramps never meet; 0 for the first section and
     * where the speed does not change. A trim may leave a ramp reaching past the clip's end: it plays on as it was.
     */
    fun rampAt(k: Int): Int {
        if (!changesSpeedAt(k)) return 0
        val bounds = sectionBounds
        var ramp = speedSections[k].rampFrames
        (k - 1 downTo 1).firstOrNull { changesSpeedAt(it) }?.let { ramp = minOf(ramp, bounds[k] - bounds[it]) }
        (k + 1..speedSections.lastIndex).firstOrNull { changesSpeedAt(it) }?.let { ramp = minOf(ramp, bounds[it] - bounds[k]) }
        return ramp.coerceAtLeast(0)
    }

    /**
     * The longest ramp the speed point before section [k] can take inside the clip: halfway to the next point that
     * changes the speed either side, or to the clip's end; 0 where the speed does not change.
     */
    fun rampRoom(k: Int): Int {
        if (!changesSpeedAt(k)) return 0
        val bounds = sectionBounds
        val left = (k - 1 downTo 1).firstOrNull { changesSpeedAt(it) }?.let { bounds[k] - bounds[it] } ?: (2 * bounds[k])
        val right = (k + 1..speedSections.lastIndex).firstOrNull { changesSpeedAt(it) }?.let { bounds[it] - bounds[k] }
            ?: (2 * (durationFrames - bounds[k]))
        return minOf(left, right).coerceAtLeast(0)
    }

    /** Each ramp that plays: where it starts (frames into the clip, half its length before its point) and its speeds. */
    internal fun ramps(): List<RampSpan> {
        if (speedSections.size < 2) return emptyList()
        val bounds = sectionBounds
        return (1..speedSections.lastIndex).mapNotNull { k ->
            val ramp = rampAt(k)
            if (ramp <= 0) null
            else RampSpan(bounds[k] - ramp / 2.0, ramp, speedSections[k - 1].speed.factor.toDouble(), speedSections[k].speed.factor.toDouble())
        }
    }

    /**
     * The speed and sections of the stretch [from] until [from] + [length] of this clip — for a clip cut out of it (split,
     * trim): the sections there, the first reaching back before the clip and the last on past it, one left becoming the
     * clip's speed.
     */
    fun sectionWindow(from: Int, length: Int): Pair<ClipSpeed, List<SpeedSection>> {
        if (speedSections.isEmpty() || length <= 0) return speed to emptyList()
        val until = from + length
        val out = mutableListOf<SpeedSection>()
        var at = 0
        speedSections.forEachIndexed { i, section ->
            val a = if (i == 0) minOf(at, from) else at
            val b = if (i == speedSections.lastIndex) maxOf(at + section.frames, until) else at + section.frames
            val lo = maxOf(a, from)
            val hi = minOf(b, until)
            if (hi > lo) out += SpeedSection(hi - lo, section.speed, section.rampFrames)
            at += section.frames
        }
        return normalizedSections(out, speed)
    }
}

/**
 * [sections] as a clip holds them: empty ones dropped (neighbours of one speed stay apart — a speed point is set before
 * its sections' speeds change); a single section left is the clip's speed ([fallback] when none is left), and with more
 * the clip's speed field holds the first one's. The first section has no point before it, so no ramp.
 */
fun normalizedSections(sections: List<SpeedSection>, fallback: ClipSpeed): Pair<ClipSpeed, List<SpeedSection>> {
    val kept = sections.filter { it.frames > 0 }
    return when (kept.size) {
        0 -> fallback to emptyList()
        1 -> kept.single().speed to emptyList()
        else -> kept.first().speed to listOf(kept.first().copy(rampFrames = 0)) + kept.drop(1)
    }
}

/** A ramp as it plays: from [from] frames into the clip, [frames] long, easing from speed [before] to [after]. */
internal data class RampSpan(val from: Double, val frames: Int, val before: Double, val after: Double) {
    /** Source frames the ramp has played ahead of a sudden change at its middle, by [at] frames into the clip. */
    fun leadFrames(at: Double): Double = frames * (after - before) * SpeedRamp.lead((at - from) / frames)
}

/** Halvings that place a moment of source inside a ramp (to well under a millionth of a frame). */
private const val INVERSE_STEPS = 60

/** Marker colours: part of Resolve's marker palette, blue (its default) first. */
enum class MarkerColor(val label: String) {
    BLUE("蓝"),
    CYAN("青"),
    GREEN("绿"),
    YELLOW("黄"),
    RED("红"),
    PINK("粉"),
    PURPLE("紫"),
}

/**
 * Resolve's clip colours, the labels an editor sorts takes with; dark enough for the clip's white name on
 * the timeline.
 */
enum class ClipColor(val label: String) {
    RED("红"),
    ORANGE("橙"),
    YELLOW("黄"),
    LIME("青柠"),
    TEAL("青"),
    NAVY("藏青"),
    PINK("粉"),
    BROWN("棕"),
}

/** A timeline marker (Resolve's): a named, coloured point with an optional note for whoever reviews the cut. */
data class Marker(
    val id: String,
    val frame: Int,
    val label: String,
    val color: MarkerColor = MarkerColor.BLUE,
    val note: String = "",
    /** Resolve's duration marker: it spans [frame] until [frame] + this; 0 = a point. */
    val durationFrames: Int = 0,
) {
    /** Where its range ends (its frame for a point). */
    val endFrame: Int get() = frame + durationFrames

    val hasDuration: Boolean get() = durationFrames > 0
}

/**
 * Resolve's destination controls: the tracks the source viewer's edits land on. [video] takes the
 * picture; [audio] the sound — null leaves it automatic: a video's own sound on A1, sound-only media on
 * the music track (A2).
 */
data class EditTargets(val video: String = Timeline.V1, val audio: String? = null)

/**
 * Resolve's timeline In / Out marks, in frames: the range [inFrame, outFrame) — the Out frame itself is
 * not in it; either may be unset. The source viewer's edits land in the range, 入点至出点 renders it.
 */
data class TimelineMarks(val inFrame: Int? = null, val outFrame: Int? = null) {
    val isEmpty: Boolean get() = inFrame == null && outFrame == null

    /** The frames marked on a timeline [durationFrames] long: In (else the start) to Out (else the end); null when unmarked or empty. */
    fun range(durationFrames: Int): IntRange? {
        if (isEmpty) return null
        val from = inFrame ?: 0
        val until = outFrame ?: durationFrames
        return if (until > from) from until until else null
    }
}

data class Timeline(
    val tracks: List<Track>,
    val clips: List<Clip>,
    val markers: List<Marker>,
    val targets: EditTargets = EditTargets(),
    val marks: TimelineMarks = TimelineMarks(),
) {
    val durationFrames: Int get() = clips.maxOfOrNull { it.end } ?: 0
    fun clip(id: String?): Clip? = clips.firstOrNull { it.id == id }
    fun track(id: String?): Track? = tracks.firstOrNull { it.id == id }

    /** The video track edits put pictures on: the chosen one while it exists, else V1. */
    fun videoTarget(): Track? = track(targets.video)?.takeIf { it.kind == TrackKind.VIDEO } ?: track(V1)

    /** The audio track chosen for the source's sound; null while automatic (or once the chosen one is gone). */
    fun audioTarget(): Track? = track(targets.audio)?.takeIf { it.kind == TrackKind.AUDIO }

    /** [targets] as they apply: a destination on a track that is gone falls back to its default. */
    val activeTargets: EditTargets get() = EditTargets(videoTarget()?.id ?: V1, audioTarget()?.id)
    fun clipsOn(trackId: String): List<Clip> = clips.filter { it.trackId == trackId }.sortedBy { it.start }
    fun replaceClip(updated: Clip): Timeline = copy(clips = clips.map { if (it.id == updated.id) updated else it })

    companion object {
        const val V2 = "v2"
        const val V1 = "v1"
        const val A1 = "a1"
        const val A2 = "a2"

        fun defaultTracks(): List<Track> = listOf(
            Track(V2, "V2", TrackKind.VIDEO),
            Track(V1, "V1", TrackKind.VIDEO),
            Track(A1, "A1", TrackKind.AUDIO),
            Track(A2, "A2", TrackKind.AUDIO),
        )

        val EMPTY = Timeline(defaultTracks(), emptyList(), emptyList())
    }
}

object Timecode {
    /** HH:MM:SS:FF at [fps]. */
    fun format(frames: Int, fps: Int): String {
        val safeFps = fps.coerceAtLeast(1)
        val f = frames.coerceAtLeast(0)
        val totalSeconds = f / safeFps
        return "%02d:%02d:%02d:%02d".format(
            totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60, f % safeFps,
        )
    }

    /** MM:SS for short media durations. */
    fun shortDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    fun framesToMs(frames: Int, fps: Int): Long = frames * 1000L / fps.coerceAtLeast(1)

    /**
     * Playback position to seek to for [frame]: its own time, rounded down to the millisecond. A player shows the first
     * frame at or after the position it seeks to, so rounding up — 8 234 ms for frame 247 at 30 fps, whose time is
     * 8 233.3 ms — showed the next frame wherever a frame's time is not a whole millisecond, two frames in three at 30 fps:
     * a paused monitor repeated one frame and skipped the next (rev 98). Read back with [positionToFrame].
     */
    fun frameToSeekMs(frame: Int, fps: Int): Long = frame * 1000L / fps.coerceAtLeast(1)

    /**
     * The frame a playback position shows. A position [frameToSeekMs] gave lies up to a millisecond before its frame's
     * time, so it is read a millisecond on, and is that frame again; a playing position reads as the frame it falls in
     * (at most a millisecond early).
     */
    fun positionToFrame(positionMs: Long, fps: Int): Int = ((positionMs + 1) * fps.coerceAtLeast(1) / 1000L).toInt()
    fun msToFrames(ms: Long, fps: Int): Int = (ms * fps.coerceAtLeast(1) / 1000L).toInt()

    /** The frame nearest [ms] — [framesToMs]'s truncated times come back to their frames. */
    fun nearestFrame(ms: Long, fps: Int): Int = ((ms * fps.coerceAtLeast(1) + 500L) / 1000L).toInt()
}

/** One of a clip's local grade versions (Resolve's Local Versions): its name and the grade it holds. */
data class GradeVersion(val name: String, val grade: ColorGrade)
