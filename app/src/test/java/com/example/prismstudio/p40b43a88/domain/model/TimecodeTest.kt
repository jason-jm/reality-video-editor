package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimecodeTest {

    @Test
    fun `format renders hours minutes seconds and frames`() {
        assertEquals("00:00:00:00", Timecode.format(0, 30))
        assertEquals("00:00:00:29", Timecode.format(29, 30))
        assertEquals("00:00:01:00", Timecode.format(30, 30))
        assertEquals("01:02:05:07", Timecode.format((3_600 + 2 * 60 + 5) * 25 + 7, 25))
    }

    @Test
    fun `seeking to a frame lands at or before its time, so a player shows it, and reads back the same frame`() {
        for (fps in listOf(24, 25, 30, 50, 60, 120)) {
            for (frame in 0..3_000) {
                val ms = Timecode.frameToSeekMs(frame, fps)
                val timeUs = frame * 1_000_000.0 / fps
                // The first frame at or after the position is this one, never the next.
                assertTrue("frame $frame at $fps fps seeks to ${ms} ms", ms * 1000 <= timeUs && (frame + 1) * 1_000_000.0 / fps > ms * 1000)
                assertEquals("frame $frame at $fps fps", frame, Timecode.positionToFrame(ms, fps))
            }
        }
        // Frame 247 at 30 fps shows at 8 233.3 ms: 8 234 ms (rounded up, as before rev 98) is past it, and showed frame 248.
        assertEquals(8_233L, Timecode.frameToSeekMs(247, 30))
        assertEquals(3_366L, Timecode.frameToSeekMs(101, 30))
        // Playing, a position reads as the frame it falls in.
        assertEquals(247, Timecode.positionToFrame(8_250, 30))
        assertEquals(247, Timecode.positionToFrame(8_265, 30))
        assertEquals(248, Timecode.positionToFrame(8_267, 30))
    }

    @Test
    fun `format never shows negative time and survives a zero frame rate`() {
        assertEquals("00:00:00:00", Timecode.format(-5, 30))
        assertEquals("00:01:30:00", Timecode.format(90, 0))
    }

    @Test
    fun `short durations show whole minutes and seconds`() {
        assertEquals("02:14", Timecode.shortDuration(134_999))
        assertEquals("00:00", Timecode.shortDuration(-1_000))
        assertEquals("62:05", Timecode.shortDuration(3_725_000))
    }

    @Test
    fun `frames and milliseconds convert at the project rate`() {
        assertEquals(1_500L, Timecode.framesToMs(45, 30))
        assertEquals(45, Timecode.msToFrames(1_500, 30))
        assertEquals(59, Timecode.msToFrames(1_999, 30))
        assertEquals(10_000L, Timecode.framesToMs(10, 0))
        assertEquals(2, Timecode.msToFrames(2_500, 0))
    }
}
