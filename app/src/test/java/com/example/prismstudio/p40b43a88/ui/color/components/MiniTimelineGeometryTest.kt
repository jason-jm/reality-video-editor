package com.example.prismstudio.p40b43a88.ui.color.components

import com.example.prismstudio.p40b43a88.ui.color.MiniClipUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniTimelineGeometryTest {

    // 100 frames across 1000 px; two lanes of at most 14 px, 3 px apart, 2 px clear of the edges, in 40 px.
    private val geometry = MiniTimelineGeometry(1000f, 40f, 2, 100, 14f, 3f, 2f)

    @Test
    fun `lanes sit centred at their full height, or share the height when there are more`() {
        assertEquals(14f, geometry.laneHeight)
        assertEquals(4.5f, geometry.laneTop(0))
        assertEquals(21.5f, geometry.laneTop(1))
        assertEquals(10f, MiniTimelineGeometry(1000f, 40f, 3, 100, 14f, 3f, 2f).laneHeight)
    }

    @Test
    fun `the whole program spans the width`() {
        assertEquals(500f, geometry.x(50))
        assertEquals(25, geometry.frameAt(250f))
        assertEquals(0, geometry.frameAt(-10f))
        assertEquals(99, geometry.frameAt(1200f))
        assertEquals(0, MiniTimelineGeometry(1000f, 40f, 2, 0, 14f, 3f, 2f).frameAt(500f))
    }

    @Test
    fun `a press takes the clip on its lane, the nearest lane from a gap or an edge`() {
        val clips = listOf(MiniClipUi("top", lane = 0, start = 20, end = 60), MiniClipUi("low", lane = 1, start = 0, end = 100))
        assertEquals("top", geometry.clipAt(clips, 300f, 10f)?.id)
        assertEquals("low", geometry.clipAt(clips, 300f, 30f)?.id)
        assertNull(geometry.clipAt(clips, 700f, 10f))
        assertEquals("top", geometry.clipAt(clips, 300f, 19.5f)?.id)
        assertEquals("low", geometry.clipAt(clips, 300f, 39f)?.id)
        assertNull(MiniTimelineGeometry(1000f, 40f, 0, 0, 14f, 3f, 2f).clipAt(clips, 1f, 1f))
    }
}
