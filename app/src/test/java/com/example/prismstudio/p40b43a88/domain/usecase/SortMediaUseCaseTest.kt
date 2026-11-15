package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.usecase.SortMediaUseCase.Order
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's media pool sort: by a column, either way round, names and slates in natural order, ties as imported. */
class SortMediaUseCaseTest {

    private val sort = SortMediaUseCase()
    private val media = listOf(
        UiFixtures.video("a", name = "Shot 10.mp4", durationMs = 4_000).copy(metadata = ClipMetadata(scene = "3", shot = "2A", take = "1")),
        UiFixtures.audio("b", name = "music.m4a", durationMs = 9_000),
        UiFixtures.video("c", name = "shot 2.mp4", durationMs = 4_000).copy(metadata = ClipMetadata(scene = "3", shot = "2", take = "4")),
        UiFixtures.image("d", name = "Poster.jpg"),
        UiFixtures.video("e", name = "Shot 2B.mp4", durationMs = 12_000).copy(metadata = ClipMetadata(scene = "10")),
    )

    private fun ids(order: Order, usage: Map<String, Int> = emptyMap()) = sort(media, order, usage).joinToString("") { it.id }

    @Test
    fun `natural order reads digit runs as numbers and ignores case`() {
        assertTrue(SortMediaUseCase.natural("shot 2", "Shot 10") < 0)
        assertTrue(SortMediaUseCase.natural("3", "3A") < 0)
        assertTrue(SortMediaUseCase.natural("007", "8") < 0)
        assertEquals(0, SortMediaUseCase.natural("Take", "take"))
        assertTrue(SortMediaUseCase.natural("", "1") < 0)
    }

    @Test
    fun `each column sorts both ways, ties keep the import order`() {
        assertEquals("abcde", ids(Order()))
        assertEquals("edcba", ids(Order(ascending = false)))
        // music, Poster, shot 2, Shot 2B, Shot 10.
        assertEquals("bdcea", ids(Order(MediaSort.NAME)))
        assertEquals("aecdb", ids(Order(MediaSort.NAME, ascending = false)))
        assertEquals("acebd", ids(Order(MediaSort.KIND)))
        // 4 s (a, c as imported), the 5 s still, 9 s, 12 s.
        assertEquals("acdbe", ids(Order(MediaSort.DURATION)))
        assertEquals("ebdac", ids(Order(MediaSort.DURATION, ascending = false)))
        assertEquals("baced", ids(Order(MediaSort.RESOLUTION)))
        assertEquals("daceb", ids(Order(MediaSort.RESOLUTION, ascending = false)))
        val usage = mapOf("a" to 3, "b" to 1, "d" to 5)
        assertEquals("cebad", ids(Order(MediaSort.USAGE), usage))
        assertEquals("dabce", ids(Order(MediaSort.USAGE, ascending = false), usage))
    }

    @Test
    fun `the slate sorts scene, shot, take, and media without one stay last`() {
        // Scene 3 shot 2 < scene 3 shot 2A < scene 10; b and d have no slate.
        assertEquals("caebd", ids(Order(MediaSort.SLATE)))
        assertEquals("eacbd", ids(Order(MediaSort.SLATE, ascending = false)))
        assertEquals("按场记 ↓", Order(MediaSort.SLATE, ascending = false).label)
        assertEquals("按导入顺序 ↑", Order().label)
    }
}
