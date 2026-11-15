package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.HueCurves
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HueCurvesTest {

    private val edits = EditHueCurveUseCase()
    private val grader = BuildGradeLutUseCase()
    private val out = FloatArray(3)
    private val blue = 4f / 6f
    private val sixth = 1f / 6f

    /** A curve with one point at [x] pushed to [y] and its anchors a sixth either side. */
    private fun bent(x: Float, y: Float, periodic: Boolean = true): List<CurvePoint> {
        val (points, index) = edits.add(emptyList(), x, periodic)
        return edits.move(points, index, x, y, periodic)
    }

    private fun assertXs(expected: List<Float>, points: List<CurvePoint>) {
        assertEquals(expected.size, points.size)
        expected.zip(points).forEach { (x, p) -> assertEquals(x, p.x, 1e-5f) }
    }

    private fun graded(grade: ColorGrade, r: Float, g: Float, b: Float): FloatArray {
        grader.apply(grade, r, g, b, out)
        return out.copyOf()
    }

    @Test
    fun `no points is flat, one point is level, and a hue curve wraps round from magenta to red`() {
        assertEquals(0.5f, HueSpline(emptyList(), true).eval(0.3f), 0f)
        assertEquals(0.8f, HueSpline(listOf(CurvePoint(0.4f, 0.8f)), true).eval(0.9f), 1e-6f)
        val wrapped = HueSpline(listOf(CurvePoint(0.05f, 0.9f), CurvePoint(0.5f, 0.5f), CurvePoint(0.95f, 0.9f)), true)
        assertEquals("red sits between the points either side of it", 0.9f, wrapped.eval(0f), 1e-6f)
        assertEquals(wrapped.eval(0.999f), wrapped.eval(0.001f), 1e-3f)
        assertEquals(wrapped.eval(0.25f), wrapped.eval(1.25f), 0f)
        val luma = HueSpline(listOf(CurvePoint(0.3f, 0.2f), CurvePoint(0.6f, 0.7f)), false)
        assertEquals("held level beyond the outermost points", listOf(0.2f, 0.7f), listOf(luma.eval(0f), luma.eval(1f)))
        // Never overshoots between points.
        val samples = (0..100).map { wrapped.eval(it / 100f) }
        assertTrue(samples.all { it in 0.5f..0.9f })
    }

    @Test
    fun `a tap adds the point with an anchor a sixth either side, without changing the curve`() {
        val (points, index) = edits.add(emptyList(), blue, true)
        assertXs(listOf(0.5f, blue, blue + sixth), points)
        assertEquals(listOf(0.5f, 0.5f, 0.5f), points.map { it.y })
        assertEquals(1, index)
        // Round the turn: red's anchors are magenta (5/6) and yellow (1/6).
        assertXs(listOf(0f, sixth, 5 * sixth), edits.add(emptyList(), 0f, true).first)
        // An existing point nearby is picked; a point already bounding a side means no anchor there.
        val bentBlue = edits.move(points, 1, blue, 0f, true)
        assertEquals(bentBlue to 1, edits.add(bentBlue, blue + 0.01f, true))
        // At 0.4 the right side is already held by the point at 0.5: only the left anchor is new.
        val (more, at) = edits.add(bentBlue, 0.4f, true)
        assertXs(listOf(0.4f - sixth, 0.4f, 0.5f, blue, blue + sixth), more)
        val before = HueSpline(bentBlue, true)
        val after = HueSpline(more, true)
        assertTrue("adding never changes the curve", (0..60).all { kotlin.math.abs(before.eval(it / 60f) - after.eval(it / 60f)) < 1e-4f })
        assertEquals(0.4f, more[at].x, 1e-6f)
        // Luma vs sat does not wrap: no anchor past white.
        assertXs(listOf(0.95f - sixth, 0.95f), edits.add(emptyList(), 0.95f, false).first)
    }

    @Test
    fun `points move between their neighbours, the curve holds twelve, and removing the last leaves it flat`() {
        val points = bent(blue, 0.1f)
        val moved = edits.move(points, 1, 0.1f, 1.4f, true)
        assertEquals(CurvePoint(0.5f + EditHueCurveUseCase.MIN_GAP, 1f), moved[1])
        var full = emptyList<CurvePoint>()
        for (i in 0 until 20) full = edits.add(full, i / 20f, true).first
        assertEquals(EditHueCurveUseCase.MAX_POINTS, full.size)
        var cleared = points
        repeat(3) { cleared = edits.remove(cleared, 0) }
        assertTrue(cleared.isEmpty())
        assertTrue(HueCurves(hueSat = listOf(CurvePoint(0.2f, 0.5f))).isFlat)
        assertFalse(HueCurves(hueSat = points).isFlat)
    }

    @Test
    fun `hue vs sat greys the blues alone`() {
        val grade = ColorGrade(hueCurves = HueCurves(hueSat = bent(blue, 0f)))
        assertArrayEquals("pure blue becomes its luma grey", floatArrayOf(0.0722f, 0.0722f, 0.0722f), graded(grade, 0f, 0f, 1f), 1e-4f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), graded(grade, 1f, 0f, 0f), 1e-6f)
        assertArrayEquals("magenta sits on the anchor", floatArrayOf(1f, 0f, 1f), graded(grade, 1f, 0f, 1f), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), graded(grade, 0f, 1f, 1f), 1e-6f)
        assertArrayEquals(floatArrayOf(0.4f, 0.4f, 0.4f), graded(grade, 0.4f, 0.4f, 0.4f), 1e-6f)
        // A violet between blue and magenta is greyed part of the way.
        val violet = graded(grade, 0.6f, 0.2f, 1f)
        assertEquals("half way to the anchor, half the saturation", 0.4f, violet[2] - violet[1], 0.01f)
    }

    @Test
    fun `hue vs hue turns red toward yellow and leaves green`() {
        val grade = ColorGrade(hueCurves = HueCurves(hueHue = bent(0f, 1f)))
        val turned = graded(grade, 1f, 0f, 0f)
        assertArrayEquals("+60° takes red to yellow", floatArrayOf(2f / 3f, 2f / 3f, 0f), turned, 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), graded(grade, 0f, 1f, 0f), 1e-6f)
    }

    @Test
    fun `hue vs lum darkens the blues by up to a stop and never a grey`() {
        val grade = ColorGrade(hueCurves = HueCurves(hueLum = bent(blue, 0f)))
        assertArrayEquals("a stop down", floatArrayOf(0f, 0f, 0.5f), graded(grade, 0f, 0f, 1f), 1e-5f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), graded(grade, 0.5f, 0.5f, 0.5f), 1e-6f)
        // A nearly grey blue (chroma 0.05) takes only part of the push.
        val faint = graded(grade, 0.5f, 0.5f, 0.55f)
        assertTrue(faint[2] in 0.3f..0.54f)
    }

    @Test
    fun `lum vs sat greys the highlights and keeps the shadows`() {
        val curve = bent(0.8f, 0f, periodic = false)
        val grade = ColorGrade(hueCurves = HueCurves(lumSat = curve))
        val dark = graded(grade, 0.5f, 0.1f, 0.1f)
        assertArrayEquals(floatArrayOf(0.5f, 0.1f, 0.1f), dark, 1e-6f)
        val (r, g, b) = Triple(0.95f, 0.78f, 0.6f)
        val y = BuildGradeLutUseCase.luma(r, g, b)
        val s = 2f * HueSpline(curve, false).eval(y)
        assertArrayEquals(floatArrayOf(y + (r - y) * s, y + (g - y) * s, y + (b - y) * s), graded(grade, r, g, b), 1e-5f)
        assertTrue(s < 0.3f)
    }

    @Test
    fun `a keyframe dissolve blends the hue curves point by point`() {
        val a = ColorGrade(hueCurves = HueCurves(hueSat = bent(blue, 0f)))
        val b = ColorGrade(hueCurves = HueCurves(hueSat = bent(blue, 1f)))
        assertEquals(0.5f, a.lerp(b, 0.5f).hueCurves.hueSat[1].y, 1e-6f)
        val other = ColorGrade(hueCurves = HueCurves(hueSat = listOf(CurvePoint(0.1f, 0.9f))))
        assertEquals("different point counts switch half way", other.hueCurves, a.lerp(other, 0.6f).hueCurves)
    }
}
