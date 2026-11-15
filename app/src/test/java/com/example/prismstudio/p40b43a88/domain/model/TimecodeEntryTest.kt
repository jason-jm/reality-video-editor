package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's typed timecode: digits fill from the right, a sign makes an offset. */
class TimecodeEntryTest {

    private fun frames(text: String) = TimecodeEntry.parse(text, 30)?.let { it.sign to it.frames }

    @Test
    fun `digits fill frames, seconds, minutes and hours from the right`() {
        assertEquals(0 to 15, frames("15"))
        assertEquals("one second", 0 to 30, frames("100"))
        assertEquals("one minute", 0 to 1800, frames("10000"))
        assertEquals("1:02:03:04", 0 to ((3600 + 2 * 60 + 3) * 30 + 4), frames("1020304"))
        assertEquals(0 to 30, frames("1:00"))
        assertEquals(0 to 1830, frames("1:01:00"))
        assertEquals(0 to 75, frames("2.5秒"))
        assertEquals(0 to 75, frames("2.5s"))
    }

    @Test
    fun `a sign makes an offset, and fields past their range carry over`() {
        assertEquals(1 to 15, frames("+15"))
        assertEquals(-1 to 30, frames("-1:00"))
        assertEquals("90 frames = 3 s", 1 to 90, frames("+90"))
        assertEquals(-1 to 30, frames("－100"))
        val back = TimecodeEntry.parse("-1:00", 30)!!
        assertEquals(70, back.target(100))
        assertEquals("never before the start", 0, back.target(10))
        assertEquals(15, TimecodeEntry.parse("15", 30)!!.target(500))
    }

    @Test
    fun `anything else is refused`() {
        assertNull(frames(""))
        assertNull(frames("+"))
        assertNull(frames("1:a"))
        assertNull(frames("1::2"))
        assertNull(frames("1:2:3:4:5"))
        assertNull(frames("123456789"))
        assertNull(frames("abc"))
    }
}
