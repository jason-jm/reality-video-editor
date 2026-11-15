package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two window layouts within PICO's planar window limits (2700 × 1800 dp, measured on the emulator). */
class WorkspaceLayoutTest {

    @Test
    fun `the ultrawide window is PICO's widest, the standard one is the user's to size, and leaving goes back to the opening size`() {
        val wide = WorkspaceLayout.window(ultrawide = true, restoring = false)
        assertEquals(2700f, wide.minWidth, 0f)
        assertEquals("exactly PICO's widest", wide.minWidth, wide.maxWidth, 0f)
        assertTrue("never past PICO's limit", wide.maxHeight <= WorkspaceLayout.MAX_WINDOW_HEIGHT)
        val standard = WorkspaceLayout.window(ultrawide = false, restoring = false)
        assertEquals(WindowSizeRange(1280f, 2400f, 820f, 1440f), standard)
        // Back from ultrawide: the manifest's 1600 × 960 first (the standard range alone would stop at 2400).
        assertEquals(WindowSizeRange(1600f, 1600f, 960f, 960f), WorkspaceLayout.window(ultrawide = false, restoring = true))
        assertEquals("ultrawide wins over a restore in flight", wide, WorkspaceLayout.window(ultrawide = true, restoring = true))
    }

    @Test
    fun `two viewers side by side once the window is wide enough for both at a useful size`() {
        assertFalse("the opening window keeps one viewer", WorkspaceLayout.dualViewer(1600f))
        assertFalse(WorkspaceLayout.dualViewer(2199f))
        // A standard window dragged out to its widest gets them too (each about 770 dp), as does the ultrawide one.
        assertTrue(WorkspaceLayout.dualViewer(2400f))
        assertTrue(WorkspaceLayout.dualViewer(2700f))
        // Two 16:9 viewers between the media pool and the inspector (360 dp each, 24 dp gaps) fit the ultrawide height.
        val viewer = (2700f - 64f - 360f - 360f - 3 * 24f) / 2f
        assertTrue("each viewer about 920 dp wide", viewer in 880f..940f)
        assertTrue("a 16:9 picture that wide fits above the timeline", viewer * 9f / 16f < WorkspaceLayout.ULTRAWIDE.minHeight - 640f)
    }
}
