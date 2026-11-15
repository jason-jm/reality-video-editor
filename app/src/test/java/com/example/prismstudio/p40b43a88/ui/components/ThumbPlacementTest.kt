package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.Framing
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbPlacementTest {

    private val box = Size(160f, 90f)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }

    @Test
    fun `a phone video fitted shows whole, narrow, between bars`() {
        // 90×160 in a 160×90 box: 90 / 160 of the box high is all of it, 50.625 wide.
        val place = thumbPlacement(null, 90, 160, box)
        assertRect(Rect(0f, 0f, 90f, 160f), place.src)
        assertRect(Rect(54.6875f, 0f, 105.3125f, 90f), place.dst)
    }

    @Test
    fun `a phone video filling shows its middle across the box`() {
        val place = thumbPlacement(ClipScaling.FILL.framing(90, 160, 160, 90), 90, 160, box)
        // The middle 90 × 50.625 of the thumbnail.
        assertRect(Rect(0f, 54.6875f, 90f, 105.3125f), place.src)
        assertRect(Rect(0f, 0f, 160f, 90f), place.dst)
    }

    @Test
    fun `stretching fills the box with the whole thumbnail`() {
        val place = thumbPlacement(Framing.FULL, 90, 160, box)
        assertRect(Rect(0f, 0f, 90f, 160f), place.src)
        assertRect(Rect(0f, 0f, 160f, 90f), place.dst)
    }

    @Test
    fun `a four by three photo fitted leaves side bars and filling crops top and bottom`() {
        val fit = thumbPlacement(ClipScaling.FIT.framing(1600, 1200, 1920, 1080), 160, 120, box)
        assertRect(Rect(20f, 0f, 140f, 90f), fit.dst)
        val fill = thumbPlacement(ClipScaling.FILL.framing(1600, 1200, 1920, 1080), 160, 120, box)
        assertRect(Rect(0f, 15f, 160f, 105f), fill.src)
        assertRect(Rect(0f, 0f, 160f, 90f), fill.dst)
    }

    @Test
    fun `a 720p clip at its own size sits in the middle two thirds`() {
        val place = thumbPlacement(ClipScaling.CROP.framing(1280, 720, 1920, 1080), 160, 90, box)
        assertRect(Rect(0f, 0f, 160f, 90f), place.src)
        assertRect(Rect(26.666666f, 15f, 133.33333f, 75f), place.dst)
    }

    @Test
    fun `a picture of the frame's shape looks the same fitted, filled or stretched`() {
        listOf(ClipScaling.FIT, ClipScaling.FILL, ClipScaling.STRETCH).forEach { scaling ->
            val place = thumbPlacement(scaling.framing(1920, 1080, 1920, 1080), 160, 90, box)
            assertRect(Rect(0f, 0f, 160f, 90f), place.src)
            assertRect(Rect(0f, 0f, 160f, 90f), place.dst)
        }
    }
}
