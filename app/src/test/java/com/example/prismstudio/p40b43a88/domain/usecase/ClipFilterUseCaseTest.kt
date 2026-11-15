package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipFilterUseCaseTest {

    private val filters = ClipFilterUseCase()
    private val warm = ColorGrade(temperature = 20f)

    // In strip order: A graded red, B red, C graded teal, D plain.
    private val clips = listOf(
        clipOf("A", 0, 60).copy(color = ClipColor.RED, grade = warm),
        clipOf("B", 60, 60).copy(color = ClipColor.RED),
        clipOf("C", 120, 60).copy(color = ClipColor.TEAL, grade = warm),
        clipOf("D", 180, 60),
    )

    private fun ids(filter: ClipFilter) = filters.apply(clips, filter).map { it.id }

    @Test
    fun `a filter keeps every clip, the graded or ungraded ones, or one clip colour, in order`() {
        assertEquals(listOf("A", "B", "C", "D"), ids(ClipFilter.All))
        assertEquals(listOf("A", "C"), ids(ClipFilter.Graded))
        assertEquals(listOf("B", "D"), ids(ClipFilter.Ungraded))
        assertEquals(listOf("A", "B"), ids(ClipFilter.Colored(ClipColor.RED)))
        assertEquals(emptyList<String>(), ids(ClipFilter.Colored(ClipColor.PINK)))
        // A keyframed grade still changes the picture, so the clip counts as graded.
        val keyed = clipOf("K", 0, 60).copy(grade = ColorGrade().copy(keyframes = warmKeys()))
        assertEquals(listOf("K"), filters.apply(listOf(keyed), ClipFilter.Graded).map { it.id })
    }

    @Test
    fun `the menu counts each choice and lists only the clip colours in use, plus the current one`() {
        val options = filters.options(clips, ClipFilter.All)
        assertEquals(listOf(ClipFilter.All to 4, ClipFilter.Graded to 2, ClipFilter.Ungraded to 2), options.general.map { it.filter to it.count })
        assertEquals(listOf(ClipColor.RED to 2, ClipColor.TEAL to 1), options.colors.map { (it.filter as ClipFilter.Colored).color to it.count })
        // A colour filter nobody matches any more (its clips were recoloured) stays in palette order, at 0.
        val stale = filters.options(clips, ClipFilter.Colored(ClipColor.PINK)).colors
        assertEquals(listOf(ClipColor.RED to 2, ClipColor.TEAL to 1, ClipColor.PINK to 0), stale.map { (it.filter as ClipFilter.Colored).color to it.count })
        assertEquals(emptyList<ClipFilterUseCase.Option>(), filters.options(clips.map { it.copy(color = null) }, ClipFilter.All).colors)
    }

    @Test
    fun `the clip being graded stays while shown, else the first kept clip, else it stays`() {
        assertEquals("A", filters.selectionFor(clips, ClipFilter.Graded, "A"))
        assertEquals("C", filters.selectionFor(clips, ClipFilter.Colored(ClipColor.TEAL), "A"))
        assertEquals("B", filters.selectionFor(clips, ClipFilter.Ungraded, "C"))
        assertEquals("A", filters.selectionFor(clips, ClipFilter.Colored(ClipColor.PINK), "A"))
        assertEquals("A", filters.selectionFor(clips, ClipFilter.Graded, null))
        assertNull(filters.selectionFor(emptyList(), ClipFilter.Graded, null))
    }

    @Test
    fun `previous and next clip walk the filter's strip, a hidden current clip counting from its own place`() {
        fun step(filter: ClipFilter, from: String?, forward: Boolean) = filters.step(clips, filter, from, forward)?.id
        assertEquals("B", step(ClipFilter.All, "A", forward = true))
        assertEquals("C", step(ClipFilter.Graded, "A", forward = true))
        assertNull(step(ClipFilter.Graded, "C", forward = true))
        assertNull(step(ClipFilter.All, "A", forward = false))
        // C is not red: back from it is the last red clip before it, forward finds none.
        assertEquals("B", step(ClipFilter.Colored(ClipColor.RED), "C", forward = false))
        assertNull(step(ClipFilter.Colored(ClipColor.RED), "C", forward = true))
        assertEquals("D", step(ClipFilter.Ungraded, "C", forward = true))
        // No current clip: the strip's first going forward, its last going back.
        assertEquals("A", step(ClipFilter.Graded, null, forward = true))
        assertEquals("C", step(ClipFilter.Graded, null, forward = false))
        assertEquals("已是片段条中的最后一个片段", filters.stepRefusal(clips, ClipFilter.Graded, forward = true))
        assertEquals("已是片段条中的第一个片段", filters.stepRefusal(clips, ClipFilter.All, forward = false))
        assertEquals("片段条中没有片段", filters.stepRefusal(clips, ClipFilter.Colored(ClipColor.PINK), forward = true))
    }

    @Test
    fun `labels, the count under the chip and the empty strip's line`() {
        assertEquals(listOf("全部片段", "已调色片段", "未调色片段", "红色片段", "青柠色片段", "藏青色片段"),
            listOf(ClipFilter.All, ClipFilter.Graded, ClipFilter.Ungraded, ClipFilter.Colored(ClipColor.RED),
                ClipFilter.Colored(ClipColor.LIME), ClipFilter.Colored(ClipColor.NAVY)).map { it.label })
        assertEquals("6 个片段", filters.countLabel(6, 6, ClipFilter.All))
        assertEquals("2 / 6 个片段", filters.countLabel(2, 6, ClipFilter.Colored(ClipColor.RED)))
        assertEquals("0 / 6 个片段", filters.countLabel(0, 6, ClipFilter.Graded))
        assertEquals("没有已调色片段，可在筛选中改回全部片段", filters.emptyMessage(ClipFilter.Graded))
        assertEquals("没有粉色片段，可在筛选中改回全部片段", filters.emptyMessage(ClipFilter.Colored(ClipColor.PINK)))
    }

    private fun warmKeys() = GradeKeyframeUseCase().toggle(warm, 0, 60).grade.keyframes

    @Test
    fun `flagged clips are those whose media carries a flag, any or one colour, and the menu lists them`() {
        // A and B play media "a" (blue and red flags), C plays "c" (red), D plays unflagged "media".
        val flagged = listOf(
            clipOf("A", 0, 60, assetId = "a"),
            clipOf("B", 60, 60, assetId = "a"),
            clipOf("C", 120, 60, assetId = "c"),
            clipOf("D", 180, 60),
        )
        val marks = mapOf("a" to listOf(FlagColor.BLUE, FlagColor.RED), "c" to listOf(FlagColor.RED))
        val ids = { filter: ClipFilter -> filters.apply(flagged, filter, marks).map { it.id } }
        assertEquals(listOf("A", "B", "C"), ids(ClipFilter.Flagged(null)))
        assertEquals(listOf("A", "B", "C"), ids(ClipFilter.Flagged(FlagColor.RED)))
        assertEquals(listOf("A", "B"), ids(ClipFilter.Flagged(FlagColor.BLUE)))
        assertEquals(emptyList<String>(), ids(ClipFilter.Flagged(FlagColor.GREEN)))
        assertEquals("without flags nothing is flagged", emptyList<String>(), filters.apply(flagged, ClipFilter.Flagged(null)).map { it.id })
        assertEquals("已标旗标片段", ClipFilter.Flagged(null).label)
        assertEquals("红色旗标片段", ClipFilter.Flagged(FlagColor.RED).label)
        // 已标旗标片段 always, then the flag colours in use in palette order (and the current one).
        val menu = filters.options(flagged, ClipFilter.Flagged(FlagColor.PURPLE), marks).flags
        assertEquals(listOf(null to 3, FlagColor.BLUE to 2, FlagColor.RED to 3, FlagColor.PURPLE to 0), menu.map { (it.filter as ClipFilter.Flagged).color to it.count })
        assertEquals(listOf(null to 0), filters.options(flagged, ClipFilter.All).flags.map { (it.filter as ClipFilter.Flagged).color to it.count })
        // ↓ walks the flagged strip.
        assertEquals("C", filters.step(flagged, ClipFilter.Flagged(FlagColor.RED), "B", forward = true, flags = marks)?.id)
        assertNull(filters.step(flagged, ClipFilter.Flagged(FlagColor.BLUE), "B", forward = true, flags = marks))
    }
}
