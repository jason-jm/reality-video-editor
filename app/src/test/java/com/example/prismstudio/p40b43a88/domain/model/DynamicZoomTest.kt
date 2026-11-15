package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Resolve's Dynamic Zoom: the extra scale over a clip, from its first frame to its last. */
class DynamicZoomTest {

    @Test
    fun `a push in grows from the first frame to the last, a pull out shrinks back, and both hold outside`() {
        val push = DynamicZoom(ZoomDirection.IN, 20, ZoomEase.LINEAR)
        assertEquals(1f, push.scaleAt(0f, 61), 1e-6f)
        assertEquals(1.1f, push.scaleAt(30f, 61), 1e-6f)
        assertEquals(1.2f, push.scaleAt(60f, 61), 1e-6f)
        assertEquals("held before the clip", 1f, push.scaleAt(-5f, 61), 1e-6f)
        assertEquals("held after it (a transition's handle)", 1.2f, push.scaleAt(90f, 61), 1e-6f)
        val pull = push.copy(direction = ZoomDirection.OUT)
        assertEquals(1.2f, pull.scaleAt(0f, 61), 1e-6f)
        assertEquals(1f, pull.scaleAt(60f, 61), 1e-6f)
        assertEquals("a one-frame clip shows the end", 1.2f, push.scaleAt(0f, 1), 1e-6f)
    }

    @Test
    fun `the eases start from rest, come to rest, or both`() {
        fun at(ease: ZoomEase, t: Float) = DynamicZoom(ZoomDirection.IN, 50, ease).scaleAt(t * 100, 101)
        assertEquals("linear", 1.125f, at(ZoomEase.LINEAR, 0.25f), 1e-5f)
        assertEquals("ease in starts slowly", 1f + 0.5f * 0.0625f, at(ZoomEase.EASE_IN, 0.25f), 1e-5f)
        assertEquals("ease out starts fast", 1f + 0.5f * 0.4375f, at(ZoomEase.EASE_OUT, 0.25f), 1e-5f)
        assertEquals("ease in and out is symmetric", 1.25f, at(ZoomEase.EASE_IN_OUT, 0.5f), 1e-5f)
        for (ease in ZoomEase.entries) {
            assertEquals("$ease starts at 1", 1f, at(ease, 0f), 1e-6f)
            assertEquals("$ease ends at the amount", 1.5f, at(ease, 1f), 1e-6f)
        }
    }

    @Test
    fun `a push heads for its focus, a pull leaves from it, and a pan slides across the enlarged picture`() {
        val push = DynamicZoom(ZoomDirection.IN, 20, ZoomEase.LINEAR, ZoomFocusX.RIGHT, ZoomFocusY.TOP)
        assertEquals("the top-right corner holds still", 1f to 1f, push.anchorXAt(0f, 61) to push.anchorYAt(60f, 61))
        val pull = push.copy(direction = ZoomDirection.OUT, focusX = ZoomFocusX.LEFT, focusY = ZoomFocusY.BOTTOM)
        assertEquals(-1f to -1f, pull.anchorXAt(30f, 61) to pull.anchorYAt(30f, 61))
        val pan = DynamicZoom(ZoomDirection.PAN, 20, ZoomEase.LINEAR, ZoomFocusX.RIGHT)
        assertEquals("enlarged throughout", listOf(1.2f, 1.2f, 1.2f), listOf(0f, 30f, 60f).map { pan.scaleAt(it, 61) })
        assertEquals("from the left side to the right", listOf(-1f, 0f, 1f), listOf(0f, 30f, 60f).map { pan.anchorXAt(it, 61) })
        assertEquals("level", 0f, pan.anchorYAt(15f, 61), 0f)
        assertEquals(true, pan.moves)
        assertEquals("a centred pan stays put", false, pan.copy(focusX = ZoomFocusX.CENTER).moves)
        assertEquals(true, DynamicZoom().moves)
    }
}
