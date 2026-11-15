package com.example.prismstudio.p40b43a88.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lanes under the fixed ruler: scrolled once they no longer fit, hit where they are drawn, kept in view. */
class LaneGeometryTest {

    // A 32 px ruler over a view 432 px tall: 400 px of lanes in view. Twelve 40 px lanes need 480 px, 80 px more.
    private fun lanes(offset: Float, count: Int = 12) = LaneGeometry.of(rulerPx = 32f, trackPx = 40f, rawOffset = offset, viewHeight = 432f, count = count)

    @Test
    fun `lanes that fit do not scroll, more than fit scroll by what is left over`() {
        val seven = lanes(50f, count = 7)
        assertFalse(seven.scrollable)
        assertEquals("kept still whatever was asked", 0f, seven.offset, 0f)
        assertNull(seven.thumb(24f))
        val twelve = lanes(500f)
        assertTrue(twelve.scrollable)
        assertEquals(80f, twelve.maxOffset, 0f)
        assertEquals("kept within what they need", 80f, twelve.offset, 0f)
        assertEquals(-48f, twelve.origin, 0f)
        assertEquals(0f, lanes(-20f).offset, 0f)
        // Unmeasured, nothing scrolls yet and every lane counts as shown.
        val unmeasured = LaneGeometry.of(32f, 40f, 30f, 0f, 12)
        assertFalse(unmeasured.scrollable)
        assertTrue(unmeasured.shows(11))
    }

    @Test
    fun `a press hits the lane drawn under it, scrolled or not`() {
        assertEquals(0, lanes(0f).laneAt(33f))
        assertEquals(1, lanes(0f).laneAt(72f))
        val scrolled = lanes(60f)
        // 60 px down: lane 1 is 20 px under the ruler, lane 2 starts at 92.
        assertEquals(52f, scrolled.top(2), 0f)
        assertEquals(1, scrolled.laneAt(33f))
        assertEquals(2, scrolled.laneAt(52f))
        assertEquals(11, scrolled.laneAt(431f))
        assertFalse("lane 0 is wholly under the ruler", scrolled.shows(0))
        assertTrue(scrolled.shows(1))
        assertTrue(scrolled.shows(11))
        assertFalse(lanes(0f).shows(11))
        // A marquee keeps its corners in content y: the same lane whatever the scroll does after.
        val content = scrolled.toContent(100f)
        assertEquals(160f - 32f, content, 0f)
        assertEquals(100f, scrolled.toScreen(content), 0f)
        assertEquals("unscrolled, the same spot sits 60 px lower", 160f, lanes(0f).toScreen(content), 0f)
        assertEquals(3, (content / 40f).toInt())
    }

    @Test
    fun `picking a clip off screen scrolls its lane whole into view, the least way`() {
        assertNull("already whole", lanes(0f).reveal(3))
        assertEquals("below: its bottom at the view's foot", 80f, lanes(0f).reveal(11)!!, 0f)
        assertEquals("partly cut off at the foot", 40f, lanes(0f).reveal(10)!!, 0f)
        assertEquals("above: its top at the ruler", 40f, lanes(80f).reveal(1)!!, 0f)
        assertEquals(0f, lanes(80f).reveal(0)!!, 0f)
        assertNull(lanes(0f).reveal(-1))
        assertNull(lanes(0f, count = 7).reveal(6))
    }

    @Test
    fun `a drag resting by the top or the bottom scrolls the lanes on, harder the deeper, never past half the band`() {
        val g = lanes(40f)
        assertEquals(0f, g.edgePush(200f, 32f), 0f)
        assertEquals("16 px into the bottom band", 4f, g.edgePush(432f - 16f, 32f), 1e-4f)
        assertEquals("over the ruler", -12f, g.edgePush(16f, 32f), 1e-4f)
        assertEquals("capped", 16f, g.edgePush(600f, 32f), 0f)
        assertEquals("nothing to scroll", 0f, lanes(0f, count = 7).edgePush(431f, 32f), 0f)
    }

    @Test
    fun `the scrollbar's thumb is as long as the share in view and runs top to bottom`() {
        val (top0, height) = lanes(0f).thumb(24f)!!
        assertEquals(32f, top0, 0f)
        assertEquals(400f * 400f / 480f, height, 1e-3f)
        val (topEnd, _) = lanes(80f).thumb(24f)!!
        assertEquals(432f, topEnd + height, 1e-3f)
        // Many lanes: never shorter than the least length.
        assertEquals(24f, LaneGeometry.of(32f, 40f, 0f, 432f, 200).thumb(24f)!!.second, 0f)
    }
}
