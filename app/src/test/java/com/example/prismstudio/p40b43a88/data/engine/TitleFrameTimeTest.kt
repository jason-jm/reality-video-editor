package com.example.prismstudio.p40b43a88.data.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToLong

class TitleFrameTimeTest {

    @Test
    fun `a frame's time maps back to that frame whether it was rounded down or up`() {
        for (fps in listOf(24, 25, 30, 50, 60)) {
            for (n in 0..20_000) {
                val exact = n * 1_000_000.0 / fps
                assertEquals("$fps fps frame $n truncated", n, frameAtUs(exact.toLong(), fps))
                assertEquals("$fps fps frame $n rounded", n, frameAtUs(exact.roundToLong(), fps))
            }
        }
    }

    @Test
    fun `times between frames go to the nearer one`() {
        assertEquals(0, frameAtUs(16_666, 30))
        assertEquals(1, frameAtUs(16_667, 30))
        assertEquals(-1, frameAtUs(-33_333, 30))
    }
}
