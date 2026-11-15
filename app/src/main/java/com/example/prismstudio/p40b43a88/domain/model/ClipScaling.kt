package com.example.prismstudio.p40b43a88.domain.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolve's Scaling: how a picture whose shape or size differs from the timeline's (a phone's 9:16 video, a 4:3 photo, a
 * 720p clip on a 1080p timeline) fills the frame, before the clip's own transform. Each clip chooses one or follows the
 * project's setting for mismatched media ([ProjectFormat.scaling]). Same-shaped pictures look the same fitted, filled
 * or stretched.
 */
enum class ClipScaling(val label: String) {
    /** The whole picture inside the frame, bars where the shapes differ (Resolve: Scale entire image to fit). */
    FIT("适应"),

    /** The frame filled, the overflow cropped off evenly (Scale full frame with crop). */
    FILL("填充"),

    /** The frame filled by stretching the picture to its edges (Stretch frame to all corners). */
    STRETCH("拉伸"),

    /** The picture at its own pixel size, centred: a smaller one leaves bars, a larger one shows its middle (Center crop with no resizing). */
    CROP("原尺寸"),
    ;

    /** Where a [pictureWidth] × [pictureHeight] picture sits in a [frameWidth] × [frameHeight] timeline; unknown sizes fill it. */
    fun framing(pictureWidth: Int, pictureHeight: Int, frameWidth: Int, frameHeight: Int): Framing {
        if (pictureWidth <= 0 || pictureHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return Framing.FULL
        if (this == CROP) return Framing(pictureWidth.toFloat() / frameWidth, pictureHeight.toFloat() / frameHeight)
        return Framing.byShape(this, pictureWidth.toFloat() / pictureHeight, frameWidth.toFloat() / frameHeight)
    }
}

/**
 * A clip's picture in the frame: centred, [spanX] frame widths wide and [spanY] frame heights high — below 1 it leaves
 * bars (see-through over the tracks beneath, black at the bottom), above 1 the frame crops it.
 */
data class Framing(val spanX: Float, val spanY: Float) {
    /** The picture's part inside the frame, placed: what remains once its overflow is cut off. */
    val inFrame: Framing get() = Framing(min(spanX, 1f), min(spanY, 1f))

    companion object {
        val FULL = Framing(1f, 1f)

        /** [scaling] by shape alone, for a picture of [pictureAspect] (width / height) in a frame of [frameAspect]; CROP, which needs pixel sizes, fits. */
        fun byShape(scaling: ClipScaling, pictureAspect: Float, frameAspect: Float): Framing {
            val ratio = pictureAspect / frameAspect
            return when (scaling) {
                ClipScaling.FIT, ClipScaling.CROP -> Framing(min(1f, ratio), min(1f, 1f / ratio))
                ClipScaling.FILL -> Framing(max(1f, ratio), max(1f, 1f / ratio))
                ClipScaling.STRETCH -> FULL
            }
        }

        /** The default placement: fitted. */
        fun fit(pictureAspect: Float, frameAspect: Float): Framing = byShape(ClipScaling.FIT, pictureAspect, frameAspect)
    }
}

/** The Scaling [clip] shows with: its own, or the project's for mismatched media. */
fun ProjectDocument.scalingOf(clip: Clip): ClipScaling = clip.scaling ?: format.scaling

/**
 * Where [clip]'s picture sits in the frame. [pictureAspect], the decoded picture's own shape when at hand, places it by
 * shape (a recorded size can disagree with what decodes); at its own size the media's recorded size counts, and without
 * one the picture fits, as it renders. Null when nothing tells the picture's shape.
 */
fun ProjectDocument.framingOf(clip: Clip, pictureAspect: Float? = null): Framing? {
    val scaling = scalingOf(clip)
    val sized = asset(clip.assetId)?.takeIf { it.width > 0 && it.height > 0 }
    val frameAspect = format.width.toFloat() / format.height.coerceAtLeast(1)
    if (scaling == ClipScaling.CROP && sized != null) return scaling.framing(sized.width, sized.height, format.width, format.height)
    val aspect = pictureAspect ?: sized?.let { it.width.toFloat() / it.height } ?: return null
    return Framing.byShape(scaling, aspect, frameAspect)
}

/**
 * `clip-scaling-note`: what [scaling] — the clip's own, or null to follow [projectScaling] — does to a [pictureWidth] ×
 * [pictureHeight] picture in a [frameWidth] × [frameHeight] timeline: the bars, the crop or the stretch it makes.
 */
@Suppress("LongParameterList")
fun scalingNote(
    scaling: ClipScaling?,
    projectScaling: ClipScaling,
    pictureWidth: Int,
    pictureHeight: Int,
    frameWidth: Int,
    frameHeight: Int,
): String {
    val note = modeNote(scaling ?: projectScaling, pictureWidth, pictureHeight, frameWidth, frameHeight)
    return if (scaling == null) "按项目设置（${projectScaling.label}）：$note" else note
}

private fun modeNote(scaling: ClipScaling, pictureWidth: Int, pictureHeight: Int, frameWidth: Int, frameHeight: Int): String {
    if (pictureWidth <= 0 || pictureHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
        return "画面比例与时间线不同时：适应留黑边，填充裁去多出的部分，拉伸会变形，原尺寸不缩放"
    }
    if (scaling == ClipScaling.CROP) return cropNote(pictureWidth, pictureHeight, frameWidth, frameHeight)
    val ratio = pictureWidth.toFloat() / pictureHeight / (frameWidth.toFloat() / frameHeight)
    if (abs(ratio - 1f) < SAME_SHAPE) return "素材与时间线的画面比例相同，适应、填充、拉伸看起来一样"
    val narrow = ratio < 1f
    val head = "素材 $pictureWidth×$pictureHeight 比画面${if (narrow) "窄" else "宽"}："
    val factor = if (narrow) 1f / ratio else ratio
    return head + when (scaling) {
        ClipScaling.FIT -> "保留整个画面，${if (narrow) "左右" else "上下"}留黑边"
        ClipScaling.FILL -> "铺满画面，裁去${if (narrow) "上下" else "左右"}各 ${((1f - 1f / factor) / 2f * 100f).roundToInt()}%"
        else -> "铺满画面，${if (narrow) "横向拉宽" else "纵向拉高"} ${String.format(Locale.US, "%.1f", factor).removeSuffix(".0")} 倍"
    }
}

/** Resolve's Center crop with no resizing, said for one picture: bars where it is smaller than the frame, crops where larger. */
private fun cropNote(pictureWidth: Int, pictureHeight: Int, frameWidth: Int, frameHeight: Int): String {
    if (pictureWidth == frameWidth && pictureHeight == frameHeight) return "素材与时间线同为 $frameWidth×$frameHeight，原尺寸正好铺满画面"
    val size = "素材 $pictureWidth×$pictureHeight"
    fun cut(picture: Int, frame: Int) = ((picture - frame) / 2f / picture * 100f).roundToInt()
    return when {
        pictureWidth <= frameWidth && pictureHeight <= frameHeight -> "$size 比画面小：按原尺寸居中，${barSides(pictureWidth < frameWidth, pictureHeight < frameHeight)}"
        pictureWidth >= frameWidth && pictureHeight >= frameHeight -> buildList {
            if (pictureWidth > frameWidth) add("左右各 ${cut(pictureWidth, frameWidth)}%")
            if (pictureHeight > frameHeight) add("上下各 ${cut(pictureHeight, frameHeight)}%")
        }.joinToString("、", prefix = "$size 比画面大：按原尺寸居中，裁去")
        pictureWidth < frameWidth -> "$size 按原尺寸居中：左右留黑边，裁去上下各 ${cut(pictureHeight, frameHeight)}%"
        else -> "$size 按原尺寸居中：上下留黑边，裁去左右各 ${cut(pictureWidth, frameWidth)}%"
    }
}

private fun barSides(sides: Boolean, ends: Boolean): String = when {
    sides && ends -> "四周留黑边"
    sides -> "左右留黑边"
    else -> "上下留黑边"
}

/** Shapes within 1 % count as the same. */
private const val SAME_SHAPE = 0.01f
