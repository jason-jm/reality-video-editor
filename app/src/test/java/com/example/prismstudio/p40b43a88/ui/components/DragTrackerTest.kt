package com.example.prismstudio.p40b43a88.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DragTrackerTest {

    @Test
    fun `steps reported before the slider recomposes add up instead of overwriting each other`() {
        val tracker = DragTracker().apply { composed = 0.5f }

        val first = tracker.accumulate(null, 0.6f)
        val second = tracker.accumulate(first, 0.7f) // still measured from 0.5: a 0.2 step

        assertEquals(0.6f, first, 1e-6f)
        assertEquals(0.8f, second, 1e-6f)
    }

    @Test
    fun `after a recomposition steps are measured from the running value`() {
        val tracker = DragTracker().apply { composed = 0.5f }
        val running = tracker.accumulate(tracker.accumulate(null, 0.6f), 0.7f)

        tracker.composed = running
        assertEquals(0.75f, tracker.accumulate(running, 0.75f), 1e-6f)
        assertEquals("a backwards step", 0.7f, tracker.accumulate(running, 0.7f), 1e-6f)
    }
}
