package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.ui.geometry.Rect
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a sound clip's volume line runs on the timeline, and what a press there takes hold of. */
class VolumeLineGeometryTest {

    // A 100-frame clip 2 px a frame wide, its box 100 px tall with the line running 4 px inside it: 92 px for 42 dB.
    private val box = Rect(0f, 0f, 200f, 100f)
    private val line = VolumeLineGeometry(box, pxPerFrame = 2f, inset = 4f)
    private val flat = clipOf("S", 0, 100, track = Timeline.A1)

    @Test
    fun `the line runs from plus 12 dB at the top to minus 30 at the bottom`() {
        assertEquals(4f, line.y(12f), 1e-4f)
        assertEquals(96f, line.y(-30f), 1e-4f)
        assertEquals("0 dB, 12/42 of the way down", 4f + 92f * 12f / 42f, line.y(0f), 1e-4f)
        assertEquals("quieter is drawn at the bottom", 96f, line.y(-60f), 0f)
        assertEquals(42f / 92f, line.dbPerPx, 1e-6f)
    }

    @Test
    fun `a press on the line takes the level, away from it or at the edges nothing`() {
        val y = line.y(0f)
        assertEquals(emptyList<Int>(), volumeGrab(flat, line, 100f, y + 5f, 6f, 10f, 28f, 172f))
        assertNull("too far from the line", volumeGrab(flat, line, 100f, y + 9f, 6f, 10f, 28f, 172f))
        assertNull("the edge belongs to trimming", volumeGrab(flat, line, 10f, y, 6f, 10f, 28f, 172f))
    }

    @Test
    fun `on keyframes a press takes the marker, the stretch's two keyframes, or the end one`() {
        val keyed = flat.copy(volumeKeyframes = listOf(VolumeKeyframe(20, -6f), VolumeKeyframe(50, 0f), VolumeKeyframe(80, -12f)))
        assertEquals("the marker", listOf(50), volumeGrab(keyed, line, line.x(50) + 3f, line.y(0f) - 3f, 6f, 10f, 28f, 172f))
        // Halfway from frame 50 (0 dB) to 80 (−12 dB) the line is at −6 dB.
        assertEquals("the stretch", listOf(50, 80), volumeGrab(keyed, line, line.x(65), line.y(-6f), 6f, 10f, 28f, 172f))
        assertEquals("before the first", listOf(20), volumeGrab(keyed, line, line.x(15), line.y(-6f), 6f, 10f, 28f, 172f))
        assertEquals("after the last", listOf(80), volumeGrab(keyed, line, line.x(84), line.y(-12f), 6f, 10f, 28f, 172f))
        assertEquals("a marker in the trim zone is still taken", listOf(5), volumeGrab(keyed.copy(volumeKeyframes = listOf(VolumeKeyframe(5, -6f))), line, line.x(5), line.y(-6f), 6f, 10f, 28f, 172f))
    }
}
