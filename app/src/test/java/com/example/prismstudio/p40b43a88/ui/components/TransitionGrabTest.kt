package com.example.prismstudio.p40b43a88.ui.components

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's transition edges on the timeline: which edge a press takes, and the length a drag of it sets. */
class TransitionGrabTest {

    // 2 px a frame, the view scrolled 100 px: the cut at frame 60 is at x = 20; a 10 px grab, boxes at least 14 px wide.
    private val ppf = 2f
    private val scroll = 100f
    private val grab = 10f

    private fun clip(alignment: TransitionAlignment, type: TransitionType = TransitionType.CROSS_DISSOLVE, frames: Int = 30) =
        clipOf("B", 60, 90).copy(transitionIn = Transition(type, frames, alignment = alignment))

    private fun at(clip: com.example.prismstudio.p40b43a88.domain.model.Clip, span: TransitionSpanUi, x: Float) =
        transitionGrabAt(clip, span, ppf, scroll, x, grab, minWidth = 14f)

    @Test
    fun `a centred transition gives both edges, which set twice the distance from the cut`() {
        val centred = clip(TransitionAlignment.CENTER)
        val span = TransitionSpanUi(lead = 15, frames = 30)
        // Its box runs from x = -10 to 50 around the cut at 20.
        val left = at(centred, span, -6f)
        val right = at(centred, span, 54f)
        assertEquals(TransitionGrab(centred = true, before = false), left)
        assertEquals(TransitionGrab(centred = true, before = false), right)
        assertEquals("the edge at frame 40: 20 frames from the cut, both sides", 40, right!!.framesAt(centred, 80))
        assertEquals(40, left!!.framesAt(centred, 40))
        assertNull("the middle of the box is not an edge", at(centred, span, 20f))
        assertNull("nor the clip beyond the grab", at(centred, span, 70f))
    }

    @Test
    fun `a transition starting on the cut gives its right edge, one ending on it its left, and the cut stays a trim`() {
        val after = clip(TransitionAlignment.START)
        assertEquals(TransitionGrab(centred = false, before = false), at(after, TransitionSpanUi(0, 30), 80f))
        assertNull("its left edge is the cut", at(after, TransitionSpanUi(0, 30), 20f))
        assertEquals(45, TransitionGrab(false, false).framesAt(after, 105))

        val before = clip(TransitionAlignment.END)
        assertEquals(TransitionGrab(centred = false, before = true), at(before, TransitionSpanUi(30, 30), -40f))
        assertNull("its right edge is the cut", at(before, TransitionSpanUi(30, 30), 20f))
        assertEquals(30, TransitionGrab(false, true).framesAt(before, 30))
        assertEquals("never shorter than a frame", 1, TransitionGrab(false, true).framesAt(before, 90))

        // A centred transition two frames long: its edges lie 2 px from the cut, inside the trim's reach — no grab.
        assertNull(at(clip(TransitionAlignment.CENTER, frames = 2), TransitionSpanUi(1, 2), 18f))
    }

    @Test
    fun `the zoom blur covers the cut from it whatever its alignment, so only its right edge drags`() {
        val zoom = clip(TransitionAlignment.CENTER, TransitionType.ZOOM_BLUR)
        assertEquals(TransitionGrab(centred = false, before = false), at(zoom, TransitionSpanUi(0, 30), 80f))
    }
}
