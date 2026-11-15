package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import com.example.prismstudio.p40b43a88.domain.model.VideoLayer
import com.example.prismstudio.p40b43a88.domain.model.VideoSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `each view composites in its mode while its span plays, normal elsewhere and for the bottom view`() {
        val state = PlaybackState(
            positionMs = 1_500,
            layers = 3,
            layerComposites = listOf(
                emptyList(),
                listOf(CompositeSpan(1_000, 2_000, CompositeMode.SCREEN)),
                listOf(CompositeSpan(0, 1_000, CompositeMode.MULTIPLY)),
            ),
        )
        assertEquals(listOf(CompositeMode.NORMAL, CompositeMode.SCREEN, CompositeMode.NORMAL), state.compositesAt())
        assertEquals(listOf(CompositeMode.NORMAL, CompositeMode.NORMAL, CompositeMode.MULTIPLY), state.compositesAt(500))
        assertEquals(listOf(CompositeMode.NORMAL, CompositeMode.NORMAL, CompositeMode.NORMAL), state.compositesAt(2_000))
        assertEquals(listOf(CompositeMode.NORMAL, CompositeMode.NORMAL), PlaybackState(layers = 2).compositesAt())
    }

    @Test
    fun `a plan's top-first layers give the bottom-first views their spans, the bottom one and a flattened preview none`() {
        fun segment(clip: String?, from: Int, to: Int, mode: CompositeMode = CompositeMode.NORMAL) =
            VideoSegment(clip, clip?.let { "asset" }, false, from, to, 0, 1f, null, composite = mode)
        val plan = RenderPlan(
            fps = 30, width = 1920, height = 1080, startFrame = 0, endFrame = 90,
            layers = listOf(
                VideoLayer("v3", listOf(segment(null, 0, 60), segment("C", 60, 90, CompositeMode.MULTIPLY))),
                VideoLayer("v2", listOf(segment("B", 0, 30, CompositeMode.SCREEN), segment(null, 30, 90, CompositeMode.ADD))),
                VideoLayer("v1", listOf(segment("A", 0, 90, CompositeMode.DIFFERENCE))),
            ),
            audio = emptyList(), titles = emptyList(),
        )
        assertEquals(
            listOf(emptyList(), listOf(CompositeSpan(0, 1_000, CompositeMode.SCREEN)), listOf(CompositeSpan(2_000, 3_000, CompositeMode.MULTIPLY))),
            plan.layerComposites(views = 3, flattened = false),
        )
        assertEquals(listOf(emptyList<CompositeSpan>()), plan.layerComposites(views = 1, flattened = true))
    }
}
