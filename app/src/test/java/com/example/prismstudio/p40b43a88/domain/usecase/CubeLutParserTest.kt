package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A `.cube` 3D LUT of [size] points per axis doing [f], red changing fastest as the format says. */
internal fun cubeText(size: Int, title: String? = null, header: String = "", f: (Float, Float, Float) -> FloatArray): String = buildString {
    title?.let { appendLine("TITLE \"$it\"") }
    append(header)
    appendLine("LUT_3D_SIZE $size")
    for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
        val out = f(r / (size - 1f), g / (size - 1f), b / (size - 1f))
        appendLine("${out[0]} ${out[1]} ${out[2]}")
    }
}

/** Reading Resolve / Adobe `.cube` LUTs and looking colours up through them. */
class CubeLutParserTest {

    private fun CubeLut.at(r: Float, g: Float, b: Float): FloatArray = FloatArray(3).also { apply(r, g, b, it) }

    private fun refused(text: String): String? = try {
        CubeLutParser.parse(text, "x.cube")
        null
    } catch (e: LutFormatException) {
        e.message
    }

    @Test
    fun `entries run red fastest and colours between them are interpolated`() {
        // Channels rotate: red comes out as blue, green as red, blue as green.
        val swap = CubeLutParser.parse(cubeText(2, title = "Swap") { r, g, b -> floatArrayOf(g, b, r) }, "swap.cube")
        assertEquals("Swap", swap.name)
        assertTrue(swap.lut.threeD)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), swap.lut.at(1f, 0f, 0f), 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), swap.lut.at(0f, 1f, 0f), 0f)
        assertArrayEquals(floatArrayOf(0.5f, 0.75f, 0.25f), swap.lut.at(0.25f, 0.5f, 0.75f), 1e-6f)

        // A curve on red sampled at 0, ½, 1: trilinear between the points (exact at them).
        val square = CubeLutParser.parse(cubeText(3) { r, g, b -> floatArrayOf(r * r, g, b) }, "sq.cube").lut
        assertEquals(0.25f, square.at(0.5f, 0f, 0f)[0], 1e-6f)
        assertEquals("half way between 0 and ¼", 0.125f, square.at(0.25f, 0f, 0f)[0], 1e-6f)
        assertEquals("outside the domain clamps", 1f, square.at(1.4f, 0f, 0f)[0], 0f)
        assertEquals(0.3f, square.at(0.5f, 0.3f, 0.9f)[1], 1e-6f)
    }

    @Test
    fun `comments, blank lines, CRLF, a byte-order mark, domains and Resolve keywords`() {
        val data = cubeText(2) { r, g, b -> floatArrayOf(r, g, b) }.lines().drop(1).joinToString("\r\n")
        val text = "# made by hand\r\nTITLE \"Doubled\"\r\nLUT_IN_VIDEO_RANGE\r\nDOMAIN_MIN 0 0 0\r\nDOMAIN_MAX 2 2 2\r\nLUT_3D_SIZE 2\r\n\r\n$data\r\n"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()
        val parsed = CubeLutParser.parse(CubeLutParser.decode(bytes), "d.cube")
        assertEquals("Doubled", parsed.name)
        assertEquals("an input of 1 sits half way through a 0…2 domain", 0.5f, parsed.lut.at(1f, 0f, 0f)[0], 1e-6f)

        val ranged = CubeLutParser.parse("LUT_3D_INPUT_RANGE 0 4\n" + cubeText(2) { r, g, b -> floatArrayOf(r, g, b) }, "ranged.cube")
        assertEquals(0.25f, ranged.lut.at(1f, 1f, 1f)[2], 1e-6f)
        assertEquals("no title: the file's name", "ranged", ranged.name)
    }

    @Test
    fun `a 1D LUT bends each channel along its curve`() {
        val parsed = CubeLutParser.parse("TITLE \"Lift\"\nLUT_1D_SIZE 3\n0.1 0 0\n0.6 0.5 0.25\n1 1 1\n", "lift.cube")
        assertFalse(parsed.lut.threeD)
        assertEquals(3, parsed.lut.size)
        assertArrayEquals(floatArrayOf(0.35f, 0.5f, 0.625f), parsed.lut.at(0.25f, 0.5f, 0.75f), 1e-6f)
    }

    @Test
    fun `broken files are refused with what is wrong`() {
        assertEquals("不是有效的 .cube LUT：缺少 LUT_3D_SIZE 或 LUT_1D_SIZE", refused("0 0 0\n1 1 1\n"))
        assertEquals("不是有效的 .cube LUT：缺少 LUT_3D_SIZE 或 LUT_1D_SIZE", refused("TITLE \"empty\"\n"))
        assertEquals("暂不支持同时含 1D 和 3D 表的 LUT", refused("LUT_1D_SIZE 2\nLUT_3D_SIZE 2\n0 0 0\n"))
        assertEquals("LUT 过大：最多支持 65³，这个是 129³", refused("LUT_3D_SIZE 129\n0 0 0\n"))
        assertEquals("LUT 数据不完整：应为 8 行，只有 2 行", refused("LUT_3D_SIZE 2\n0 0 0\n1 0 0\n"))
        assertEquals("数据行数过多：应为 2 行", refused("LUT_1D_SIZE 2\n0 0 0\n1 1 1\n1 1 1\n"))
        assertEquals("第 3 行不是三个数值：0.5 0.5", refused("LUT_1D_SIZE 2\n0 0 0\n0.5 0.5\n"))
        assertEquals("第 2 行不是三个数值：NaN 0 0", refused("LUT_1D_SIZE 2\nNaN 0 0\n1 1 1\n"))
        assertEquals("第 4 行：数据之后不能再有设置项", refused("LUT_1D_SIZE 2\n0 0 0\n1 1 1\nTITLE \"late\"\n"))
        assertEquals("DOMAIN_MAX 必须大于 DOMAIN_MIN", refused("DOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 0 1\nLUT_1D_SIZE 2\n0 0 0\n1 1 1\n"))
        assertEquals("第 1 行：LUT_3D_SIZE 不是整数", refused("LUT_3D_SIZE big\n"))
        assertEquals("LUT 没有数据", refused("LUT_3D_SIZE 2\n"))
    }

    @Test
    fun `the id comes from what the LUT does, not its title or layout`() {
        val warm: (Float, Float, Float) -> FloatArray = { r, g, b -> floatArrayOf(r * 0.9f + 0.1f, g, b * 0.8f) }
        val a = CubeLutParser.parse(cubeText(3, title = "Warm") { r, g, b -> warm(r, g, b) }, "warm.cube")
        val b = CubeLutParser.parse("# another copy\n" + cubeText(3, title = "Warm v2") { r, g, b -> warm(r, g, b) }.replace(" ", "   "), "copy.cube")
        assertEquals(a.id, b.id)
        assertTrue(a.id.matches(Regex("lut-[0-9a-f]{12}")))
        val c = CubeLutParser.parse(cubeText(3) { r, g, b -> floatArrayOf(r, g, b) }, "id.cube")
        assertNotEquals(a.id, c.id)
    }
}
