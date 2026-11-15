package com.example.prismstudio.p40b43a88.ui.color.components

import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.usecase.EditCurveUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditHueCurveUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/** A drag that starts on an empty stretch of a curve bends the point it added, never an old one (release round R10). */
class CurveGestureTest {

    private val straight = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

    @Test
    fun `until the added point is drawn nothing is picked - the black point used to be bent`() {
        assertEquals(-1, addedAt(straight, straight, 0.5f))
    }

    @Test
    fun `the added point is found once the list shows it`() {
        val (next, index) = EditCurveUseCase().add(straight, 0.5f, 0.5f)
        assertEquals(index, addedAt(next, straight, 0.5f))
        assertEquals(1, addedAt(next, straight, 0.5f))
    }

    @Test
    fun `a press at the very edge finds the point the rules moved inward`() {
        val (next, index) = EditCurveUseCase().add(straight, 0f, 0f)
        assertEquals(1, index)
        // The black point at x 0 is nearer the press than the new point at 0.03: it is not the one added.
        assertEquals(index, addedAt(next, straight, 0f))
    }

    @Test
    fun `on a hue curve the tap's point is picked, not its anchors`() {
        val flat = listOf(CurvePoint(0.1f, 0.5f), CurvePoint(0.9f, 0.5f))
        val (next, index) = EditHueCurveUseCase().add(flat, 0.5f, periodic = true)
        assertEquals(index, addedAt(next, flat, 0.5f))
    }
}
