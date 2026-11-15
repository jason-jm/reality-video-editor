package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Generators: what each image holds. */
class GeneratorTest {

    private fun IntArray.at(x: Int, y: Int, w: Int) = this[y * w + x] and 0xFFFFFF

    @Test
    fun `a solid is one colour everywhere`() {
        val px = Generator.BLACK.pixels(16, 9)
        assertTrue(px.all { it == 0xFF000000.toInt() })
        assertTrue(Generator.GREEN.pixels(4, 4).all { it and 0xFFFFFF == 0x00B140 })
    }

    @Test
    fun `a gradient runs from its top colour to its bottom colour`() {
        val w = 8
        val h = 101
        val px = Generator.SUNSET.pixels(w, h)
        assertEquals(0xF39A3D, px.at(3, 0, w))
        assertEquals(0x5B2A86, px.at(3, h - 1, w))
        // Half way: the average of the two, channel by channel.
        assertEquals(0xA7, px.at(3, 50, w) shr 16 and 0xFF)
        assertEquals(px.at(0, 50, w), px.at(w - 1, 50, w))
    }

    @Test
    fun `colour bars are seven 75 percent bars over the reverse strip and the bottom row`() {
        val w = 700
        val h = 120
        val px = Generator.BARS.pixels(w, h)
        val top = listOf(0xBFBFBF, 0xBFBF00, 0x00BFBF, 0x00BF00, 0xBF00BF, 0xBF0000, 0x0000BF)
        top.forEachIndexed { i, c -> assertEquals("bar $i", c, px.at(i * 100 + 50, 10, w)) }
        assertEquals("reverse strip under yellow", 0x131313, px.at(150, 85, w))
        assertEquals("reverse strip under the grey bar", 0x0000BF, px.at(50, 85, w))
        assertEquals("−I", 0x00214C, px.at(20, 110, w))
        assertEquals("100% white", 0xFFFFFF, px.at(150, 110, w))
        assertEquals("PLUGE: blacker than black first", 0x090909, px.at(w * 5 / 6 + 5, 110, w))
    }

    @Test
    fun `one image per generator and size`() {
        assertEquals("g-night-1920x1080", Generator.assetId(Generator.NIGHT, 1920, 1080))
        assertEquals("夜空渐变 1080×1920", Generator.NIGHT.assetName(1080, 1920))
    }
}
