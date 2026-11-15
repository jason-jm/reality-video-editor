package com.example.prismstudio.p40b43a88.domain.model

/** Produces unique ids for clips, markers, effects and jobs. */
fun interface IdGenerator {
    fun next(prefix: String): String
}

/**
 * Outcome of a timeline edit: the new timeline, the clip to select and a short status message; an edit
 * of several clips names them all in [selection] (the first is [selectedClipId]).
 */
data class EditResult(
    val timeline: Timeline,
    val selectedClipId: String?,
    val message: String,
    val selection: List<String>? = null,
)

/** Why an edit could not be applied (shown to the user as a toast). */
class EditRejected(message: String) : Exception(message)

enum class TrimEdge { START, END }

/** Asset-library categories in the Edit stage. */
enum class LibraryTab(val label: String, val actionLabel: String) {
    MEDIA("媒体", "添加到时间线"),
    TRANSITIONS("转场", "应用转场到所选片段"),
    TITLES("标题", "添加标题到时间线"),
    EFFECTS("特效", "应用到所选片段"),
    /** Resolve's Generators: solid colours, gradients and colour bars, as picture clips on top at the playhead. */
    GENERATORS("生成", "添加到播放头处"),
    /**
     * Resolve's index (rev 148, was 标记): the Edit Index of the timeline's clips, and the marker index — the timeline's
     * markers to jump to, name, colour, annotate and delete.
     */
    MARKERS("索引", "在播放头添加标记"),
}

/** Title layouts, each with the size, weight, box and entrance a new title of it starts with. */
enum class TitleTemplate(
    val label: String,
    val defaultText: String,
    val sizePx: Int,
    val weight: TitleWeight,
    /** The lower third's band is its box. */
    val box: Boolean = false,
    val animation: TitleAnimation = TitleAnimation.FADE,
) {
    CENTER("居中大标题", "标题", 96, TitleWeight.BOLD),
    LOWER_THIRD("下三分之一", "人物 · 地点", 56, TitleWeight.MEDIUM, box = true),
    ROLL("滚动字幕", "滚动字幕", 48, TitleWeight.BOLD),
    CREDITS("片尾字幕", "导演 · 剪辑", 56, TitleWeight.REGULAR),
    /** A line of dialogue on the subtitle track: bottom-centred on a dark box, no fade, wrapped to the frame. */
    SUBTITLE("对白字幕", "在此输入字幕", 44, TitleWeight.REGULAR, animation = TitleAnimation.NONE),
}
