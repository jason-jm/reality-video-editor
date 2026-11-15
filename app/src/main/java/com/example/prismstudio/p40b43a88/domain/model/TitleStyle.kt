package com.example.prismstudio.p40b43a88.domain.model

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A title's words and look — Resolve's Text title inspector: size (on a 1080-line picture), colour, font
 * weight, an outline, a drop shadow, a box behind the text and its opacity, and how it comes in and goes.
 * A new title takes its template's weight, box and entrance. A subtitle is drawn in its track's style
 * ([SubtitleStyle]); only its words and size are read from here.
 */
data class TitleStyle(
    val text: String,
    val sizePx: Int = 72,
    val template: TitleTemplate = TitleTemplate.CENTER,
    val color: TitleColor = TitleColor.WHITE,
    val weight: TitleWeight = template.weight,
    val outline: Boolean = false,
    val shadow: Boolean = true,
    val box: Boolean = template.box,
    val boxOpacityPercent: Int = BOX_OPACITY,
    val animation: TitleAnimation = template.animation,
) {
    /** The same words at the same size in the template's own look. */
    fun reset(): TitleStyle = TitleStyle(text, sizePx, template)

    /** Nothing changed from the template's look. */
    val isTemplateLook: Boolean get() = this == reset()

    /** The lines drawn: a title breaks at line breaks and at "·", a subtitle only at line breaks. */
    fun lines(): List<String> {
        val breaks = if (template == TitleTemplate.SUBTITLE) charArrayOf('\n') else charArrayOf('\n', '·')
        return text.split(*breaks).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(" ") }
    }

    /** Characters on those lines (what the typewriter types). */
    fun characters(): Int = lines().sumOf { it.codePointCount(0, it.length) }

    companion object {
        /** The box's (and the lower third's band's) opacity, %. */
        const val BOX_OPACITY = 55
    }
}

/** Title text colours (ARGB): white first, colours that read on most pictures, and black for light ones. */
enum class TitleColor(val label: String, val argb: Int) {
    WHITE("白", 0xFFFFFFFF.toInt()),
    YELLOW("黄", 0xFFFFD84D.toInt()),
    AMBER("琥珀", 0xFFF2B33D.toInt()),
    RED("红", 0xFFF2543D.toInt()),
    CYAN("青", 0xFF7FE3FF.toInt()),
    GREEN("绿", 0xFF8BF28B.toInt()),
    BLACK("黑", 0xFF141414.toInt());

    /** Dark text: its outline and box are light instead of dark. */
    val dark: Boolean get() = this == BLACK
}

/** Weights of the system typeface; PICO Sans, which draws Chinese, has each of them. */
enum class TitleWeight(val label: String, val value: Int) {
    LIGHT("细", 300),
    REGULAR("常规", 400),
    MEDIUM("中", 500),
    BOLD("粗", 700),
    HEAVY("特粗", 900),
}

/**
 * How a title comes in. Resolve animates titles with Fusion templates; these are the usual entrances.
 * Every one but [NONE] fades out over the title's last third of a second.
 */
enum class TitleAnimation(val label: String) {
    /** Cuts in and out. */
    NONE("无"),
    /** Fades in over a third of a second — how titles always came in. */
    FADE("渐显"),
    /** Floats up 60 px into place over half a second as it fades in. */
    RISE("上浮"),
    /** Grows from 80 % into place over half a second as it fades in. */
    ZOOM("放大"),
    /** Typed a character at a time (a typewriter), 15 a second, and all typed by 60 % of the title at the latest. */
    TYPEWRITER("逐字");

    /** The look [frame] frames into a title [length] frames long that shows [characters] characters. */
    fun motion(frame: Int, length: Int, fps: Int, characters: Int): TitleMotion {
        val out = ((length - frame).toFloat() / fadeFrames(fps)).coerceIn(0f, 1f)
        return when (this) {
            NONE -> TitleMotion.STILL
            FADE -> TitleMotion(alpha = minOf(((frame + 1f) / fadeFrames(fps)).coerceIn(0f, 1f), out))
            RISE, ZOOM -> {
                val p = ((frame + 1f) / moveFrames(fps)).coerceIn(0f, 1f)
                // Ease out: quick at first, settling into place.
                val eased = 1f - (1f - p) * (1f - p) * (1f - p)
                if (this == RISE) {
                    TitleMotion(alpha = minOf(p, out), dropPx = (1f - eased) * RISE_PX)
                } else {
                    TitleMotion(alpha = minOf(p, out), scale = ZOOM_FROM + (1f - ZOOM_FROM) * eased)
                }
            }
            TYPEWRITER -> {
                val typing = typingFrames(characters, length, fps)
                TitleMotion(alpha = out, characters = ceil(characters * (frame + 1f) / typing).toInt().coerceIn(0, characters))
            }
        }
    }

    /** What the title does, for the inspector. */
    fun describe(characters: Int, length: Int, fps: Int): String {
        val fadeOut = "，结尾 ${seconds(fadeFrames(fps), fps)} 秒渐隐"
        return when (this) {
            NONE -> "直接出现，直接消失"
            FADE -> "开头 ${seconds(fadeFrames(fps), fps)} 秒渐显$fadeOut"
            RISE -> "开头 ${seconds(moveFrames(fps), fps)} 秒自下而上浮现$fadeOut"
            ZOOM -> "开头 ${seconds(moveFrames(fps), fps)} 秒由小放大$fadeOut"
            TYPEWRITER -> "逐字打出，$characters 个字 ${seconds(typingFrames(characters, length, fps), fps)} 秒打完$fadeOut"
        }
    }

    companion object {
        /** How far below its place (px on a 1080-line picture) a rising title starts. */
        const val RISE_PX = 60f
        /** The scale a zooming title starts at. */
        const val ZOOM_FROM = 0.8f
        /** Characters typed per second. */
        const val TYPING_RATE = 15
        /** Share of the title by which the typing is done, however long the text. */
        const val TYPING_SHARE = 0.6f

        /** Frames of a fade in or out: a third of a second. */
        fun fadeFrames(fps: Int): Int = (fps / 3).coerceAtLeast(1)

        /** Frames of a rise or a zoom: half a second. */
        fun moveFrames(fps: Int): Int = (fps / 2).coerceAtLeast(1)

        /** Frames the typewriter takes to type [characters] characters into a title [length] frames long. */
        fun typingFrames(characters: Int, length: Int, fps: Int): Int =
            minOf(ceil(characters * fps / TYPING_RATE.toFloat()).toInt(), floor(length * TYPING_SHARE).toInt()).coerceAtLeast(1)

        private fun seconds(frames: Int, fps: Int) = String.format(Locale.ROOT, "%.1f", frames.toFloat() / fps.coerceAtLeast(1))
    }
}

/**
 * A title's look at one frame of its entrance or exit: opacity, how far below its place it sits (px on a
 * 1080-line picture), its scale, and how many characters show.
 */
data class TitleMotion(
    val alpha: Float = 1f,
    val dropPx: Float = 0f,
    val scale: Float = 1f,
    val characters: Int = Int.MAX_VALUE,
) {
    companion object {
        val STILL = TitleMotion()
    }
}
