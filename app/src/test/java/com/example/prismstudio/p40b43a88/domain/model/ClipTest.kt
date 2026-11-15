package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.clipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTest {

    @Test
    fun `a clip covers its start frame but not its end frame`() {
        val clip = clipOf("A", 10, 20)

        assertEquals(30, clip.end)
        assertTrue(10 in clip)
        assertTrue(29 in clip)
        assertFalse(30 in clip)
        assertFalse(9 in clip)
    }

    @Test
    fun `source duration accounts for the playback speed`() {
        assertEquals(2_000L, clipOf("A", 0, 60).sourceDurationMs(30))
        assertEquals(4_000L, clipOf("A", 0, 60, speed = PlaybackSpeed.DOUBLE).sourceDurationMs(30))
        assertEquals(500L, clipOf("A", 0, 60, speed = PlaybackSpeed.QUARTER).sourceDurationMs(30))
    }
}
