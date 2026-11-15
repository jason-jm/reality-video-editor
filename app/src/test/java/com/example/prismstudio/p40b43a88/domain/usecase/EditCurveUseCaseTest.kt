package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditCurveUseCaseTest {

    private val curve = EditCurveUseCase()
    private val points = listOf(CurvePoint(0f, 0f), CurvePoint(0.4f, 0.4f), CurvePoint(0.7f, 0.7f), CurvePoint(1f, 1f))

    @Test
    fun `add inserts a point in x order and keeps it inside the curve`() {
        val (added, index) = curve.add(ToneCurves.IDENTITY, 0.5f, 1.4f)
        assertEquals(listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 1f), CurvePoint(1f, 1f)), added)
        assertEquals(1, index)

        val (nearStart, startIndex) = curve.add(added, -0.2f, -0.5f)
        assertEquals(CurvePoint(EditCurveUseCase.MIN_GAP, 0f), nearStart[1])
        assertEquals(1, startIndex)
        assertEquals(4, nearStart.size)
    }

    @Test
    fun `add refuses points too close to another one or beyond the point limit`() {
        val (crowded, index) = curve.add(points, 0.42f, 0.1f)
        assertSame(points, crowded)
        assertEquals(-1, index)

        val full = listOf(CurvePoint(0f, 0f)) + (1..6).map { CurvePoint(it / 10f, it / 10f) } + CurvePoint(1f, 1f)
        assertEquals(EditCurveUseCase.MAX_POINTS, full.size)
        assertEquals(full to -1, curve.add(full, 0.85f, 0.5f))
    }

    @Test
    fun `end points move vertically only and inner points stay between their neighbours`() {
        assertEquals(CurvePoint(0f, 0.2f), curve.move(points, 0, 0.3f, 0.2f)[0])
        assertEquals(CurvePoint(1f, 1f), curve.move(points, 3, 0.5f, 1.5f)[3])

        val pushedRight = curve.move(points, 1, 0.9f, 0.5f)
        assertEquals(0.7f - EditCurveUseCase.MIN_GAP, pushedRight[1].x, 1e-6f)
        assertEquals(0.5f, pushedRight[1].y, 0f)

        assertEquals(CurvePoint(EditCurveUseCase.MIN_GAP, 0f), curve.move(points, 1, -1f, -1f)[1])
        assertEquals(points.drop(2), curve.move(points, 1, 0.3f, 0.3f).drop(2))
        assertSame(points, curve.move(points, 9, 0.5f, 0.5f))
    }

    @Test
    fun `only inner points can be removed and reset restores the identity curve`() {
        assertSame(points, curve.remove(points, 0))
        assertSame(points, curve.remove(points, points.lastIndex))
        assertEquals(listOf(CurvePoint(0f, 0f), CurvePoint(0.7f, 0.7f), CurvePoint(1f, 1f)), curve.remove(points, 1))
        assertEquals(ToneCurves.IDENTITY, curve.reset())
    }

    @Test
    fun `point lookup returns the nearest point inside the touch radius`() {
        assertEquals(1, curve.pointAt(points, 0.41f, 0.38f, 0.05f))
        assertEquals(2, curve.pointAt(points, 0.6f, 0.62f, 0.12f))
        assertEquals(-1, curve.pointAt(points, 0.55f, 0.55f, 0.05f))
    }
    @Test
    fun `dragging a point that is packed between its neighbours never throws`() {
        var curvePoints = ToneCurves.IDENTITY
        curvePoints = curve.add(curvePoints, 0f, 0.1f).first // clamped to x = MIN_GAP
        curvePoints = curve.add(curvePoints, 0.2f, 0.3f).first
        curvePoints = curve.add(curvePoints, 0.4f, 0.5f).first
        curvePoints = curve.move(curvePoints, 2, -1f, 0.3f) // dragged hard left: x = 0.03 + 0.03
        curvePoints = curve.move(curvePoints, 3, -1f, 0.5f) // dragged hard left: x = 0.06 + 0.03 = 0.089999996f

        val moved = curve.move(curvePoints, 2, 0.06f, 0.35f)

        assertEquals(0.35f, moved[2].y, 0f)
        assertTrue(moved[1].x < moved[2].x && moved[2].x < moved[3].x)
    }
}
