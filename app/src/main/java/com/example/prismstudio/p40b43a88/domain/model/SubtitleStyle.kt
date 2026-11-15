package com.example.prismstudio.p40b43a88.domain.model

/** Subtitle text colours that read on any picture (ARGB). */
enum class SubtitleColor(val label: String, val argb: Int) {
    WHITE("白", 0xFFFFFFFF.toInt()),
    YELLOW("黄", 0xFFFFD84D.toInt()),
    CYAN("青", 0xFF7FE3FF.toInt()),
    GREEN("绿", 0xFF8BF28B.toInt()),
}

/**
 * How a subtitle track shows its lines — Resolve's subtitle Track Style. One style for every subtitle on
 * the track: text size (on a 1080-line picture), colour, weight, an outline, the dark box behind each
 * line and its opacity, and how far above the bottom of the picture the last line sits. The defaults
 * are the look subtitles always had.
 */
data class SubtitleStyle(
    val sizePx: Int = TitleTemplate.SUBTITLE.sizePx,
    val color: SubtitleColor = SubtitleColor.WHITE,
    val bold: Boolean = false,
    val outline: Boolean = false,
    val box: Boolean = true,
    val boxOpacityPercent: Int = 60,
    val marginPercent: Int = 8,
) {
    companion object {
        val DEFAULT = SubtitleStyle()
    }
}

/** The track style's sliders and their ranges. */
enum class SubtitleStyleControl(val label: String, val min: Int, val max: Int) {
    SIZE("字号", 24, 120),
    BOX_OPACITY("背景", 0, 100),
    MARGIN("距底", 2, 40);

    fun read(s: SubtitleStyle): Int = when (this) {
        SIZE -> s.sizePx
        BOX_OPACITY -> s.boxOpacityPercent
        MARGIN -> s.marginPercent
    }

    fun write(s: SubtitleStyle, value: Float): SubtitleStyle {
        val v = Math.round(value).coerceIn(min, max)
        return when (this) {
            SIZE -> s.copy(sizePx = v)
            BOX_OPACITY -> s.copy(boxOpacityPercent = v)
            MARGIN -> s.copy(marginPercent = v)
        }
    }
}

/** The track style of [clip] when it is a subtitle (a subtitle-template title), else null. */
fun Timeline.subtitleStyleOf(clip: Clip): SubtitleStyle? =
    if (clip.title?.template != TitleTemplate.SUBTITLE) null else track(clip.trackId)?.subtitleStyle ?: SubtitleStyle.DEFAULT
