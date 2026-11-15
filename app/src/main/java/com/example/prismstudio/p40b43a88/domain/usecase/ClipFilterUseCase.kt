package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.domain.model.FlagColor

/**
 * Resolve's Color page Clips filter over the timeline's picture clips, in strip order: the clips a filter keeps,
 * the menu's choices with their counts, the caption under the filter chip, and which clip to grade once the
 * strip has narrowed. `flags` are the media's flags by asset id (a clip shows its media's).
 */
class ClipFilterUseCase {

    /** A menu choice: what it keeps and how many clips that is. */
    data class Option(val filter: ClipFilter, val count: Int)

    /** The menu: 全部 / 已调色 / 未调色, the clip colours in use, then 已标旗标 and the flag colours in use. */
    data class Options(val general: List<Option>, val colors: List<Option>, val flags: List<Option> = emptyList())

    fun apply(clips: List<Clip>, filter: ClipFilter, flags: Map<String, List<FlagColor>> = emptyMap()): List<Clip> =
        clips.filter { filter.keeps(it, flags) }

    /**
     * Colours (and flag colours) in palette order, only those some clip carries — [current] stays listed while it
     * matches nothing; 已标旗标片段 is always offered.
     */
    fun options(clips: List<Clip>, current: ClipFilter, flags: Map<String, List<FlagColor>> = emptyMap()): Options {
        fun option(filter: ClipFilter) = Option(filter, clips.count { filter.keeps(it, flags) })
        return Options(
            general = listOf(ClipFilter.All, ClipFilter.Graded, ClipFilter.Ungraded).map(::option),
            colors = ClipColor.entries.map { option(ClipFilter.Colored(it)) }.filter { it.count > 0 || it.filter == current },
            flags = listOf(option(ClipFilter.Flagged(null))) +
                FlagColor.entries.map { option(ClipFilter.Flagged(it)) }.filter { it.count > 0 || it.filter == current },
        )
    }

    /**
     * The clip to grade under [filter]: [selectedId] while the strip still shows it, else the strip's first clip.
     * When the filter keeps nothing the current clip stays.
     */
    fun selectionFor(clips: List<Clip>, filter: ClipFilter, selectedId: String?, flags: Map<String, List<FlagColor>> = emptyMap()): String? {
        val shown = apply(clips, filter, flags)
        return if (shown.isEmpty() || shown.any { it.id == selectedId }) selectedId else shown.first().id
    }

    /**
     * Resolve's Previous / Next Clip on the Color page (↑ / ↓): the clip [filter] keeps just before / after [currentId] in
     * strip order — a clip the filter hides counts from its own place — or the strip's last / first without a current
     * clip. Null at that end of the strip.
     */
    fun step(clips: List<Clip>, filter: ClipFilter, currentId: String?, forward: Boolean, flags: Map<String, List<FlagColor>> = emptyMap()): Clip? {
        val at = clips.indexOfFirst { it.id == currentId }
        val shown = clips.withIndex().filter { filter.keeps(it.value, flags) }
        val hit = when {
            at < 0 -> if (forward) shown.firstOrNull() else shown.lastOrNull()
            forward -> shown.firstOrNull { it.index > at }
            else -> shown.lastOrNull { it.index < at }
        }
        return hit?.value
    }

    /** Why [step] went nowhere. */
    fun stepRefusal(clips: List<Clip>, filter: ClipFilter, forward: Boolean, flags: Map<String, List<FlagColor>> = emptyMap()): String = when {
        apply(clips, filter, flags).isEmpty() -> "片段条中没有片段"
        forward -> "已是片段条中的最后一个片段"
        else -> "已是片段条中的第一个片段"
    }

    /** `clip-filter-count`: "6 个片段", or "2 / 6 个片段" while a filter narrows the strip. */
    fun countLabel(shown: Int, total: Int, filter: ClipFilter): String =
        if (filter == ClipFilter.All) "$total 个片段" else "$shown / $total 个片段"

    /** The strip's line when [filter] keeps no clip of a timeline that has some. */
    fun emptyMessage(filter: ClipFilter): String = "没有${filter.label}，可在筛选中改回全部片段"

    private fun ClipFilter.keeps(clip: Clip, flags: Map<String, List<FlagColor>>) = matches(clip, clip.assetId?.let(flags::get).orEmpty())
}
