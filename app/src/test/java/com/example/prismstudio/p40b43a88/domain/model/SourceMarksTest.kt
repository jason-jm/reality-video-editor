package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's source marks: a new In or Out keeps the other mark when it still fits, else falls back to the media's end or start. */
class SourceMarksTest {
    @Test
    fun `an In keeps the Out after it, an Out keeps the In before it, and an empty range is refused`() {
        assertEquals(SourceMarks(2_000, 8_000), SourceMarks.withIn(null, 2_000, 8_000))
        assertEquals(SourceMarks(1_000, 5_000), SourceMarks.withIn(SourceMarks(0, 5_000), 1_000, 8_000))
        assertEquals("an In past the Out runs to the end", SourceMarks(6_000, 8_000), SourceMarks.withIn(SourceMarks(0, 5_000), 6_000, 8_000))
        assertEquals(SourceMarks(2_000, 5_000), SourceMarks.withOut(SourceMarks(2_000, 8_000), 5_000))
        assertEquals("an Out before the In starts it at the start", SourceMarks(0, 1_000), SourceMarks.withOut(SourceMarks(2_000, 8_000), 1_000))
        assertNull("an In at the very end marks nothing", SourceMarks.withIn(null, 8_000, 8_000))
        assertNull(SourceMarks.withOut(null, 0))
        assertEquals(3_000L, SourceMarks(2_000, 5_000).durationMs)
        assertThrows(IllegalArgumentException::class.java) { SourceMarks(5_000, 5_000) }
    }
}
