package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BuildGradeLutUseCaseTest {

    private val grader = BuildGradeLutUseCase()
    private val out = FloatArray(3)

    @Test
    fun `a window composite bakes the primary node, then the window's grade`() {
        val primary = ColorGrade(exposure = 0.5f, curves = ToneCurves(luma = listOf(CurvePoint(0f, 0.1f), CurvePoint(1f, 0.9f))))
        val window = ColorGrade(saturation = 0.3f, curves = ToneCurves(red = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.7f), CurvePoint(1f, 1f))))
        // One builder alternates between the two nodes' curves; the expectation uses one per node.
        val composite = BuildGradeLutUseCase(size = 5)(primary, window)
        val first = BuildGradeLutUseCase()
        val second = BuildGradeLutUseCase()
        val mid = FloatArray(3)
        for (r in 0 until 5) for (g in 0 until 5) for (b in 0 until 5) {
            first.apply(primary, r / 4f, g / 4f, b / 4f, mid)
            second.apply(window, mid[0], mid[1], mid[2], out)
            assertEquals("cube[$r][$g][$b]", BuildGradeLutUseCase.argb(out), composite[r][g][b])
        }

        // The window is spatial: grading a colour applies the primary node only.
        val windowed = primary.copy(window = PowerWindow(grade = ColorGrade(exposure = -2f)))
        assertEquals(BuildGradeLutUseCase(size = 5)(primary).map { it.map(IntArray::toList) }, BuildGradeLutUseCase(size = 5)(windowed).map { it.map(IntArray::toList) })
    }

    @Test
    fun `contrast turns around the pivot - the pivot level stays, the rest spreads from it`() {
        fun at(v: Float, g: ColorGrade): Float { grader.apply(g, v, v, v, out); return out[0] }
        val resolve = ColorGrade(contrast = 1.5f)
        assertEquals("Resolve's 0.435 stays", 0.435f, at(0.435f, resolve), 1e-5f)
        val low = ColorGrade(contrast = 1.5f, pivot = 0.2f)
        assertEquals("a low pivot stays", 0.2f, at(0.2f, low), 1e-5f)
        // Moving the pivot down brightens the same contrast: 0.5 lands at 0.2 + 0.3 × 1.5 = 0.65 instead of 0.5325.
        assertEquals(0.5325f, at(0.5f, resolve), 1e-4f)
        assertEquals(0.65f, at(0.5f, low), 1e-4f)
        assertEquals("the pivot alone changes nothing", 0.3f, at(0.3f, ColorGrade(pivot = 0.8f)), 1e-6f)
    }

    @Test
    fun `color boost lifts dull colours far more than vivid ones, and greys not at all`() {
        val boost = ColorGrade(colorBoost = 100f)
        fun chroma(r: Float, g: Float, b: Float, grade: ColorGrade): Float {
            grader.apply(grade, r, g, b, out)
            return maxOf(out[0], out[1], out[2]) - minOf(out[0], out[1], out[2])
        }
        val dull = chroma(0.55f, 0.5f, 0.45f, boost) / 0.1f
        val vivid = chroma(0.9f, 0.3f, 0.2f, boost) / 0.7f
        assertTrue("a dull colour nearly doubles ($dull)", dull > 1.8f)
        assertEquals("past the fade a vivid colour stays", 1f, vivid, 1e-4f)
        assertEquals("grey stays grey", 0f, chroma(0.5f, 0.5f, 0.5f, boost), 1e-6f)
        assertTrue("negative boost mutes the dull colour", chroma(0.55f, 0.5f, 0.45f, ColorGrade(colorBoost = -100f)) < 0.01f)
    }

    @Test
    fun `a neutral grade bakes an identity lookup table of the requested size`() {
        val lut = BuildGradeLutUseCase(size = 5)(ColorGrade.NEUTRAL)

        val levels = intArrayOf(0, 64, 128, 191, 255)
        assertEquals(5, lut.size)
        for (r in 0 until 5) {
            assertEquals(5, lut[r].size)
            for (g in 0 until 5) {
                assertEquals(5, lut[r][g].size)
                for (b in 0 until 5) {
                    val expected = (0xFF shl 24) or (levels[r] shl 16) or (levels[g] shl 8) or levels[b]
                    assertEquals("cube[$r][$g][$b]", expected, lut[r][g][b])
                }
            }
        }
    }

    @Test
    fun `a full strength mono look bakes a grey table while zero strength changes nothing`() {
        val mono = BuildGradeLutUseCase(size = 9)(ColorGrade(look = Look.MONO, lookStrength = 100))
        for (plane in mono) for (row in plane) for (argb in row) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            assertTrue("grey expected, got #%06X".format(argb and 0xFFFFFF), r == g && g == b)
        }

        val off = BuildGradeLutUseCase(size = 9)(ColorGrade(look = Look.MONO, lookStrength = 0))
        val identity = BuildGradeLutUseCase(size = 9)(ColorGrade.NEUTRAL)
        for (r in off.indices) for (g in off[r].indices) assertArrayEquals(identity[r][g], off[r][g])
    }

    @Test
    fun `gain scales the signal and the result is clamped to the displayable range`() {
        grader.apply(ColorGrade(gain = WheelValue(master = 1f)), 0.8f, 0.4f, 0.1f, out)

        assertArrayEquals(floatArrayOf(1f, 0.8f, 0.2f), out, 1e-6f)
    }

    @Test
    fun `exposure is applied in linear light`() {
        grader.apply(ColorGrade(exposure = 1f), 0.5f, 0.5f, 0.5f, out)
        assertArrayEquals(floatArrayOf(0.6852f, 0.6852f, 0.6852f), out, 1e-3f)

        grader.apply(ColorGrade(exposure = -1f), 0.5f, 0.5f, 0.5f, out)
        assertArrayEquals(floatArrayOf(0.3649f, 0.3649f, 0.3649f), out, 1e-3f)
    }

    @Test
    fun `contrast pivots around the Resolve pivot`() {
        grader.apply(ColorGrade(contrast = 2f), 0.435f, 0.535f, 0.335f, out)

        assertArrayEquals(floatArrayOf(0.435f, 0.635f, 0.235f), out, 1e-5f)
    }

    @Test
    fun `zero saturation turns a colour into its Rec 709 luma grey`() {
        grader.apply(ColorGrade(saturation = 0f), 0.2f, 0.6f, 0.9f, out)

        val luma = BuildGradeLutUseCase.luma(0.2f, 0.6f, 0.9f)
        assertEquals(0.53662f, luma, 1e-5f)
        assertArrayEquals(floatArrayOf(luma, luma, luma), out, 1e-5f)
    }

    @Test
    fun `a balance puck pushes towards the hue under it on the wheel ring`() {
        fun push(degrees: Double): FloatArray {
            val radians = Math.toRadians(degrees)
            return BuildGradeLutUseCase.wheelDelta(WheelValue(x = (0.5 * cos(radians)).toFloat(), y = (0.5 * sin(radians)).toFloat()))
        }

        fun strongest(rgb: FloatArray) = rgb.indices.maxByOrNull { rgb[it] }
        assertEquals("red at 0°", 0, strongest(push(0.0)))
        assertEquals("green at 120°", 1, strongest(push(120.0)))
        assertEquals("blue at 240°", 2, strongest(push(240.0)))
        assertEquals(0.375f, push(0.0)[0] - push(0.0)[1], 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), BuildGradeLutUseCase.wheelDelta(WheelValue.NEUTRAL), 0f)
        // Zero-luma: the balance changes colour, never Rec.709 brightness.
        for (degrees in listOf(0.0, 30.0, 75.0, 120.0, 200.0, 240.0, 333.0)) {
            val p = push(degrees)
            assertEquals(0f, BuildGradeLutUseCase.luma(p[0], p[1], p[2]), 1e-6f)
        }
    }

    @Test
    fun `the wheel readout adds the master ring to the balance push`() {
        val readout = grader.wheelReadout(WheelValue(x = 0.5f, y = 0f, master = 0.1f))

        // master 0.1 + zero-luma push (0.25, -0.125, -0.125) - luma -0.045275
        assertArrayEquals(floatArrayOf(0.395275f, 0.020275f, 0.020275f), readout, 1e-5f)
    }

    @Test
    fun `curves pass through their control points and hold the end values outside them`() {
        val points = listOf(CurvePoint(1f, 1f), CurvePoint(0f, 0.1f), CurvePoint(0.5f, 0.8f)) // unsorted on purpose

        assertEquals(0.8f, BuildGradeLutUseCase.evalCurve(points, 0.5f), 1e-6f)
        assertEquals(0.1f, BuildGradeLutUseCase.evalCurve(points, 0f), 1e-6f)
        assertEquals(0.1f, BuildGradeLutUseCase.evalCurve(points, -0.3f), 1e-6f)
        assertEquals(1f, BuildGradeLutUseCase.evalCurve(points, 1.4f), 1e-6f)
        val rising = (0..10).map { BuildGradeLutUseCase.evalCurve(points, 0.5f + it * 0.05f) }
        assertTrue(rising.zipWithNext().all { (a, b) -> b >= a })
        // A single point is not a curve: values pass straight through.
        assertEquals(0.37f, BuildGradeLutUseCase.evalCurve(listOf(CurvePoint(0.2f, 0.9f)), 0.37f), 0f)
    }
    @Test
    fun `the identity curve and control points on the diagonal leave values unchanged`() {
        for (x in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            assertEquals("IDENTITY at $x", x, BuildGradeLutUseCase.evalCurve(ToneCurves.IDENTITY, x), 1e-4f)
        }
        // Adding a control point on the drawn (identity) curve must not change the picture.
        val diagonal = ToneCurves(luma = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 1f)))
        grader.apply(ColorGrade(curves = diagonal), 0.2f, 0.4f, 0.8f, out)
        assertArrayEquals(floatArrayOf(0.2f, 0.4f, 0.8f), out, 1e-4f)
    }
}
