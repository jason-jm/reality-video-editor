package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.MuxerException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavExportTest {

    private fun le(bytes: ByteArray, at: Int, n: Int): Long = (0 until n).fold(0L) { acc, i -> acc or ((bytes[at + i].toLong() and 0xFF) shl (8 * i)) }

    private fun pcm(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply { samples.forEach { putShort(it) }; flip() }

    @Test
    fun `the header says RIFF WAVE PCM with the rate, channels, depth and lengths`() {
        val h = WavMuxer.header(48_000, 2, 24, 1_440_000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 1_440_000L, le(h, 4, 4))
        assertEquals("WAVEfmt ", String(h, 8, 8, Charsets.US_ASCII))
        assertEquals(listOf(16L, 1L, 2L, 48_000L, 288_000L, 6L, 24L), listOf(le(h, 16, 4), le(h, 20, 2), le(h, 22, 2), le(h, 24, 4), le(h, 28, 4), le(h, 32, 2), le(h, 34, 2)))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        assertEquals(1_440_000L, le(h, 40, 4))
    }

    @Test
    fun `the muxer writes the PCM after its header, sized when it closes, and takes nothing but PCM`() {
        val file = File.createTempFile("wav153", ".wav")
        val muxer = WavMuxer(file.path)
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(44_100).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        assertEquals(0, muxer.addTrack(format))
        listOf(pcm16Copy(pcm(1, -1, 1000, -1000)), pcm16Copy(pcm(32767, -32768))).forEachIndexed { i, b -> muxer.writeSampleData(0, b, BufferInfo(i * 1000L, b.remaining(), 0)) }
        muxer.close()
        val bytes = file.readBytes()
        assertEquals(44 + 12, bytes.size)
        assertEquals(12L, le(bytes, 40, 4))
        assertEquals(listOf(1L, 0xFFFFL, 1000L, (-1000 and 0xFFFF).toLong(), 32767L, 0x8000L), (0 until 6).map { le(bytes, 44 + 2 * it, 2) })
        assertThrows(MuxerException::class.java) { WavMuxer(file.path).addTrack(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()) }
        assertEquals(listOf(MimeTypes.AUDIO_RAW), WavMuxer.Factory().getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO))
        assertEquals(emptyList<String>(), WavMuxer.Factory().getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO))
        file.delete()
    }

    @Test
    fun `24 bits are the 16-bit samples times 256, packed little-endian`() {
        val out = pcm16To24(pcm(1, -1, 32767, -32768, 0))
        val got = ByteArray(out.remaining()).also { out.get(it) }
        assertArrayEquals(
            byteArrayOf(0, 1, 0, 0, -1, -1, 0, -1, 127, 0, 0, -128, 0, 0, 0),
            got,
        )
    }
}
