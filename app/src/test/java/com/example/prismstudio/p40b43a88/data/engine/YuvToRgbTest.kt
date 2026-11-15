package com.example.prismstudio.p40b43a88.data.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The underlay of a stacked render must come back in the colours it was encoded with. */
class YuvToRgbTest {

    private fun rgb(conversion: YuvToRgb, y: Int, cb: Int, cr: Int) = conversion.rgb(y / 255f, cb / 255f, cr / 255f)

    @Test
    fun `limited-range BT·709 maps video black, white and the primaries`() {
        val bt709 = YuvToRgb.of(YuvToRgb.STANDARD_BT709, YuvToRgb.RANGE_LIMITED, 1080)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), rgb(bt709, 16, 128, 128), 0.002f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), rgb(bt709, 235, 128, 128), 0.002f)
        // BT.709 red, green and blue at 8 bits (Y′, Cb, Cr).
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), rgb(bt709, 63, 102, 240), 0.01f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), rgb(bt709, 173, 42, 26), 0.01f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), rgb(bt709, 32, 240, 118), 0.01f)
    }

    @Test
    fun `a decoded frame turns into ARGB pixels, chroma blended between its samples`() {
        val bt709 = YuvToRgb.of(YuvToRgb.STANDARD_BT709, YuvToRgb.RANGE_LIMITED, 1080)
        // 4 × 2: luma white | black | red | red; one chroma sample per 2 × 2 block (neutral, then red).
        val frame = YuvVideoReader.Frame().apply {
            resize(4, 2)
            byteArrayOf(235.toByte(), 16, 63, 63, 235.toByte(), 16, 63, 63).copyInto(y)
            byteArrayOf(128.toByte(), 102).copyInto(u)
            byteArrayOf(128.toByte(), 240.toByte()).copyInto(v)
        }
        val px = bt709.argb(frame)
        assertEquals(8, px.size)
        assertEquals("white", 0xFFFFFFFF.toInt(), px[0])
        // The edge pixel takes the red block's chroma at a quarter: black luma, a little red.
        assertEquals("black with a quarter of the red chroma: R 50", 0xFF320000.toInt(), px[1])
        // The last column sits on the red block's own sample: red.
        val red = px[3]
        assertEquals(0xFF, red shr 16 and 0xFF)
        assertTrue((red shr 8 and 0xFF) <= 2 && (red and 0xFF) <= 2)
    }

    @Test
    fun `an unstated standard follows the picture size and full range skips the footroom`() {
        // BT.601 red is Y′ 81, Cb 90, Cr 240; read as BT.709 it would turn orange.
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), rgb(YuvToRgb.of(null, null, 480), 81, 90, 240), 0.01f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), rgb(YuvToRgb.of(null, null, 720), 63, 102, 240), 0.01f)
        val full = YuvToRgb.of(YuvToRgb.STANDARD_BT709, YuvToRgb.RANGE_FULL, 1080)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), rgb(full, 0, 128, 128), 0.003f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), rgb(full, 255, 128, 128), 0.003f)
    }
}
