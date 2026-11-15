package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.GradeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeScopesUseCaseTest {

    private val scopes = ComputeScopesUseCase()

    private fun flat(argb: Long, width: Int = 4, height: Int = 2) = FramePixels(width, height, IntArray(width * height) { argb.toInt() })

    /** A plane where only [hotCells] are lit (at full density). */
    private fun expectedPlane(size: Int, vararg hotCells: Int) = FloatArray(size).also { plane -> hotCells.forEach { plane[it] = 1f } }

    @Test
    fun `waveform draws white on the top row and black on the bottom row of every column`() {
        val levels = ComputeScopesUseCase.LEVELS

        val white = scopes(flat(0xFFFFFFFF), ScopeMode.WAVEFORM)
        assertEquals(ScopeMode.WAVEFORM, white.mode)
        assertEquals(4, white.columns)
        assertEquals(levels, white.rows)
        assertEquals(1, white.planes.size)
        assertArrayEquals(expectedPlane(4 * levels, 0, 1, 2, 3), white.planes[0], 0f)

        val black = scopes(flat(0xFF000000), ScopeMode.WAVEFORM)
        val bottom = (levels - 1) * 4
        assertArrayEquals(expectedPlane(4 * levels, bottom, bottom + 1, bottom + 2, bottom + 3), black.planes[0], 0f)
    }

    @Test
    fun `waveform columns are capped for wide frames`() {
        val wide = scopes(flat(0xFF808080, width = 400, height = 1), ScopeMode.WAVEFORM)

        assertEquals(ComputeScopesUseCase.MAX_COLUMNS, wide.columns)
        assertEquals(ComputeScopesUseCase.MAX_COLUMNS * ComputeScopesUseCase.LEVELS, wide.planes[0].size)
    }

    @Test
    fun `parade separates the red green and blue channels`() {
        val levels = ComputeScopesUseCase.LEVELS
        val parade = scopes(flat(0xFFFF0000), ScopeMode.PARADE)

        assertEquals(ScopeMode.PARADE, parade.mode)
        assertEquals(3, parade.planes.size)
        val bottom = (levels - 1) * 4
        assertArrayEquals(expectedPlane(4 * levels, 0, 1, 2, 3), parade.planes[0], 0f)
        assertArrayEquals(expectedPlane(4 * levels, bottom, bottom + 1, bottom + 2, bottom + 3), parade.planes[1], 0f)
        assertArrayEquals(expectedPlane(4 * levels, bottom, bottom + 1, bottom + 2, bottom + 3), parade.planes[2], 0f)
    }

    @Test
    fun `vectorscope puts neutral grey in the centre and pure red towards the top`() {
        val size = ComputeScopesUseCase.VECTOR_SIZE

        val grey = scopes(flat(0xFF808080), ScopeMode.VECTORSCOPE)
        assertEquals(ScopeMode.VECTORSCOPE, grey.mode)
        assertEquals(size, grey.columns)
        assertEquals(size, grey.rows)
        assertArrayEquals(expectedPlane(size * size, 48 * size + 48), grey.planes[0], 0f)

        val red = scopes(flat(0xFFFF0000), ScopeMode.VECTORSCOPE)
        assertArrayEquals(expectedPlane(size * size, 0 * size + 37), red.planes[0], 0f)
    }

    @Test
    fun `histogram bins each channel and normalises to the busiest bin`() {
        val pixels = IntArray(6) { if (it < 4) 0xFF808080.toInt() else 0xFF000000.toInt() }
        val histogram = scopes(FramePixels(3, 2, pixels), ScopeMode.HISTOGRAM)

        assertEquals(ScopeMode.HISTOGRAM, histogram.mode)
        assertEquals(ComputeScopesUseCase.BINS, histogram.columns)
        assertEquals(1, histogram.rows)
        val expected = FloatArray(ComputeScopesUseCase.BINS).also { it[32] = 1f; it[0] = 0.5f }
        histogram.planes.forEach { assertArrayEquals(expected, it, 1e-6f) }
    }

    @Test
    fun `grading returns the same frame for a neutral grade and greys pixels without saturation`() {
        val red = flat(0xFFFF0000, width = 2, height = 1)

        assertSame(red, scopes.grade(red, ColorGrade.NEUTRAL))
        assertSame(red, scopes.grade(red, ColorGrade(lookStrength = 20)))

        val grey = scopes.grade(red, ColorGrade(saturation = 0f))
        assertEquals(2, grey.width)
        assertEquals(1, grey.height)
        assertArrayEquals(IntArray(2) { 0xFF363636.toInt() }, grey.pixels)
    }

    private val grey = 0xFF808080.toInt()
    private val black = 0xFF000000.toInt()
    /** Node 2 turns what it covers black. */
    private fun darkWindow(window: PowerWindow) = ColorGrade(window = window.copy(grade = ColorGrade(gain = WheelValue(master = -1f))))

    @Test
    fun `a window grades the pixels it covers`() {
        val frame = flat(0xFF808080, width = 16, height = 9)
        val spot = darkWindow(PowerWindow(size = 0.3f, softness = 0f))

        val graded = scopes.grade(frame, spot, 16f / 9f)
        assertEquals("centre", black, graded.pixels[4 * 16 + 8])
        assertEquals("corner", grey, graded.pixels[0])
        assertEquals(grey, graded.pixels[8 * 16 + 15])

        val outside = scopes.grade(frame, darkWindow(PowerWindow(size = 0.3f, softness = 0f, invert = true)), 16f / 9f)
        assertEquals(grey, outside.pixels[4 * 16 + 8])
        assertEquals(black, outside.pixels[0])

        assertSame("a neutral window changes nothing", frame, scopes.grade(frame, ColorGrade(window = PowerWindow()), 16f / 9f))
    }

    @Test
    fun `the window sits in the picture the clip is fitted into`() {
        // A 4:3 source pillarboxed in a 16:9 timeline: a window at the timeline's right edge misses it.
        val narrow = flat(0xFF808080, width = 12, height = 9)
        val edge = darkWindow(PowerWindow(centerX = 0.95f, size = 0.2f, softness = 0f))

        assertTrue(scopes.grade(narrow, edge, 16f / 9f).pixels.all { it == grey })
        assertEquals("in a 4:3 timeline it covers the right edge", black, scopes.grade(narrow, edge, 4f / 3f).pixels[4 * 12 + 11])
    }

    @Test
    fun `a qualifier limits node 2 to the colours node 1 hands it`() {
        // Green left half, red right half; node 2 (whole frame) blackens the greens only.
        val green = 0xFF19B233.toInt()
        val red = 0xFFB21919.toInt()
        val frame = FramePixels(4, 1, intArrayOf(green, green, red, red))
        val greens = HslQualifier.picked(0x19 / 255f, 0xB2 / 255f, 0x33 / 255f)
        val grade = darkWindow(PowerWindow(shape = WindowShape.FULL, qualifier = greens))

        val graded = scopes.grade(frame, grade, 4f)

        assertEquals(listOf(black, black, red, red), graded.pixels.toList())
        val inverted = scopes.grade(frame, darkWindow(PowerWindow(shape = WindowShape.FULL, qualifier = greens.copy(invert = true))), 4f)
        assertEquals(listOf(green, green, black, black), inverted.pixels.toList())
        // Node 1 turns everything grey first: node 2's greens are gone.
        val desaturated = grade.withNode(GradeNode.PRIMARY) { it.copy(saturation = 0f) }
        assertTrue(scopes.grade(frame, desaturated, 4f).pixels.none { it == black })
    }

    @Test
    fun `matte finesse blurs the qualifier's matte across an edge and cleans its faint parts`() {
        // 200 × 100: green left half, red right half; node 2 blackens the greens. At blur 100 the radius is
        // 2 % of the height: 2 px.
        val green = 0xFF19B233.toInt()
        val red = 0xFFB21919.toInt()
        val frame = FramePixels(200, 100, IntArray(200 * 100) { if (it % 200 < 100) green else red })
        val greens = HslQualifier.picked(0x19 / 255f, 0xB2 / 255f, 0x33 / 255f)
        val sharp = scopes.grade(frame, darkWindow(PowerWindow(shape = WindowShape.FULL, qualifier = greens)), 2f)
        val soft = scopes.grade(frame, darkWindow(PowerWindow(shape = WindowShape.FULL, qualifier = greens.copy(blur = 100f))), 2f)
        val row = 50 * 200
        assertEquals(listOf(black, black, red, red), listOf(98, 99, 100, 101).map { sharp.pixels[row + it] })
        // Far from the edge nothing changes; at it, the matte is part-way on both sides.
        assertEquals(listOf(black, red), listOf(10, 190).map { soft.pixels[row + it] })
        val redAt = { x: Int -> (soft.pixels[row + x] shr 16) and 0xFF }
        assertTrue("a red pixel by the edge is darkened a little", redAt(100) in 1 until 0xB2)
        assertTrue("a green pixel by the edge keeps some of its colour", (soft.pixels[row + 99] shr 8) and 0xFF > 0)
        assertEquals("beyond the radius the red is untouched", red, soft.pixels[row + 103])

        // Clean black drops a weak selection, clean white fills a strong one.
        val q = HslQualifier(hueCenter = 120f, hueWidth = 10f, hueSoft = 40f, satLow = 0f, lumLow = 0f)
        val weak = q.key(0.6f, 0.8f, 0.2f)
        assertTrue(weak in 0.05f..0.45f)
        assertEquals(0f, q.copy(cleanBlack = 100f).key(0.6f, 0.8f, 0.2f), 0f)
        // A colour 15° off the centre is selected at about 0.96; clean white makes that whole.
        assertTrue(q.key(0.2f, 0.8f, 0.35f) in 0.9f..0.99f)
        assertEquals(1f, q.copy(cleanWhite = 100f).key(0.2f, 0.8f, 0.35f), 0f)
    }

    @Test
    fun `the eyedropper reads the source pixel a picture point shows, averaged over 3 × 3`() {
        // 12 × 6 source (2:1) letterboxed in a 16:9 picture: green left, red right.
        val green = 0xFF00C800.toInt()
        val red = 0xFFC80000.toInt()
        val frame = FramePixels(12, 6, IntArray(72) { i -> if (i % 12 < 6) green else red })
        assertEquals(green, scopes.sourcePixel(frame, 0.2f, 0.5f, 16f / 9f))
        assertEquals(red, scopes.sourcePixel(frame, 0.8f, 0.4f, 16f / 9f))
        assertEquals("the letterbox bar above the picture", null, scopes.sourcePixel(frame, 0.5f, 0.97f, 16f / 9f))
        // On the seam the 3 × 3 block mixes both.
        val seam = scopes.sourcePixel(frame, 0.5f, 0.5f, 16f / 9f)!!
        assertTrue((seam shr 16) and 0xFF in 1..199 && (seam shr 8) and 0xFF in 1..199)
    }

    @Test
    fun `filled and stretched pictures map picture points and framing as the viewer shows them`() {
        // 6 × 12 portrait source in a 16:9 picture, green top half, red bottom half.
        val green = 0xFF00C800.toInt()
        val red = 0xFFC80000.toInt()
        val frame = FramePixels(6, 12, IntArray(72) { i -> if (i / 6 < 6) green else red })
        // Fitted, the sides of the picture are bars; filled, the picture's middle third of the height shows, edge to edge.
        val fill = Framing.byShape(ClipScaling.FILL, 0.5f, 16f / 9f)
        assertEquals(null, scopes.sourcePixel(frame, 0.1f, 0.5f, 16f / 9f))
        assertEquals(green, scopes.sourcePixel(frame, 0.1f, 0.9f, 16f / 9f, fill))
        assertEquals(red, scopes.sourcePixel(frame, 0.9f, 0.1f, 16f / 9f, fill))
        assertEquals(green, scopes.sourcePixel(frame, 0.05f, 0.95f, 16f / 9f, Framing.FULL))
        // Framed for a fill, only the centre rows remain (the raster's 16:9 shape from the 6-pixel width): 6 × 3.
        val framed = scopes.framed(frame, fill)
        assertEquals(6, framed.width)
        assertEquals(3, framed.height)
        assertTrue(scopes.framed(frame, null) === frame)
        assertTrue(scopes.framed(frame, Framing.byShape(ClipScaling.FIT, 0.5f, 16f / 9f)) === frame)
    }

    @Test
    fun `a picture at its own size maps points and framing by its size against the raster`() {
        // 6 × 12 source shown at its own size, half the raster's width and twice its height: the middle half of its rows.
        val top = 0xFF00C800.toInt()
        val bottom = 0xFFC80000.toInt()
        val frame = FramePixels(6, 12, IntArray(72) { i -> if (i / 6 < 6) top else bottom })
        val native = Framing(0.5f, 2f)
        assertEquals("beside the picture: a bar", null, scopes.sourcePixel(frame, 0.2f, 0.5f, 16f / 9f, native))
        assertEquals(top, scopes.sourcePixel(frame, 0.5f, 0.9f, 16f / 9f, native))
        assertEquals(bottom, scopes.sourcePixel(frame, 0.5f, 0.1f, 16f / 9f, native))
        val framed = scopes.framed(frame, native)
        assertEquals(6, framed.width)
        assertEquals(6, framed.height)
    }

    @Test
    fun `a keyed grade measures the frame as the GPU dissolves it, window composites included`() {
        val frame = FramePixels(2, 1, intArrayOf(0xFF406080.toInt(), 0xFFC0A060.toInt()))
        val warm = ColorGrade(temperature = 30f)
        val dark = ColorGrade(exposure = -1.5f)
        val window = PowerWindow(WindowShape.FULL, grade = ColorGrade(saturation = 0f))
        val keyed = ColorGrade(window = window, keyframes = listOf(GradeKeyframe(0, warm), GradeKeyframe(40, dark)))
        val a = scopes.grade(frame, warm.copy(window = window))
        val b = scopes.grade(frame, dark.copy(window = window))
        val mid = scopes.grade(frame, keyed, clipFrame = 10f)
        for (i in 0..1) for (shift in intArrayOf(16, 8, 0)) {
            val expected = ((a.pixels[i] shr shift) and 0xFF) * 0.75f + ((b.pixels[i] shr shift) and 0xFF) * 0.25f
            assertEquals(expected, ((mid.pixels[i] shr shift) and 0xFF).toFloat(), 1.01f)
        }
        assertArrayEquals("at a keyframe: its look", b.pixels, scopes.grade(frame, keyed, clipFrame = 40f).pixels)
    }
}
