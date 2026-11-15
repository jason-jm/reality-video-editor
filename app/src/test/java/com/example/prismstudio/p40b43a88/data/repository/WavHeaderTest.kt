package com.example.prismstudio.p40b43a88.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The voiceover take's WAV header: a plain PCM RIFF file any player reads sample-exactly. */
class WavHeaderTest {

    @Test
    fun `a 48 kHz mono 16-bit header says so, with the sizes of what follows`() {
        val header = WavHeader.bytes(48_000, 1, 16, dataBytes = 96_000)
        assertEquals(44, header.size)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(header, at, 4, Charsets.US_ASCII)
        assertEquals(listOf("RIFF", "WAVE", "fmt ", "data"), listOf(tag(0), tag(8), tag(12), tag(36)))
        assertEquals("file size past the first 8 bytes", 36 + 96_000, b.getInt(4))
        assertEquals(16, b.getInt(16))
        assertEquals("PCM", 1, b.getShort(20).toInt())
        assertEquals("channels", 1, b.getShort(22).toInt())
        assertEquals(48_000, b.getInt(24))
        assertEquals("bytes per second", 96_000, b.getInt(28))
        assertEquals("block align", 2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(96_000, b.getInt(40))
    }

    @Test
    fun `the header written at the start of a take has no data yet`() {
        val empty = WavHeader.bytes(48_000, 1, 16, dataBytes = 0)
        assertArrayEquals(WavHeader.bytes(48_000, 1, 16, 96_000).copyOfRange(8, 40), empty.copyOfRange(8, 40))
        assertEquals(0, ByteBuffer.wrap(empty).order(ByteOrder.LITTLE_ENDIAN).getInt(40))
    }
}
