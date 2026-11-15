package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Power window coverage (the shader computes the same) and the two grade nodes. */
class PowerWindowTest {

    private val wide = 16f / 9f

    /** The picture x (0…1) that lies [halfHeights] picture half-heights right of the centre. */
    private fun x(halfHeights: Float) = 0.5f + halfHeights / (2 * wide)

    /** The picture y (0…1, up) that lies [halfHeights] picture half-heights above the centre. */
    private fun y(halfHeights: Float) = 0.5f + halfHeights / 2

    private fun PowerWindow.at(dx: Float, dy: Float) = coverage(x(dx), y(dy), wide)

    @Test
    fun `a circle is round on a wide picture and fades across its edge`() {
        val circle = PowerWindow(size = 0.5f, softness = 0.25f)
        assertEquals(1f, circle.at(0f, 0f), 0f)
        assertEquals("half way through the soft edge", 0.5f, circle.at(0f, 0.5f), 1e-5f)
        assertEquals("as far to the side", 0.5f, circle.at(0.5f, 0f), 1e-5f)
        assertEquals("inner end of the soft edge", 1f, circle.at(0.375f, 0f), 1e-5f)
        assertEquals("outer end", 0f, circle.at(0.625f, 0f), 1e-5f)
        assertEquals(0f, circle.coverage(0.95f, 0.95f, wide), 0f)

        val hard = circle.copy(softness = 0f)
        assertEquals(1f, hard.at(0f, 0.49f), 0f)
        assertEquals(0f, hard.at(0f, 0.51f), 0f)
    }

    @Test
    fun `aspect and angle shape the window`() {
        val ellipse = PowerWindow(size = 0.5f, aspect = 2f, softness = 0f)
        assertEquals(1f, ellipse.at(0.9f, 0f), 0f)
        assertEquals(0f, ellipse.at(0f, 0.9f), 0f)
        assertEquals(0f, ellipse.copy(aspect = 1f).at(0.9f, 0f), 0f)

        val upright = ellipse.copy(angle = 90f)
        assertEquals(1f, upright.at(0f, 0.9f), 0f)
        assertEquals(0f, upright.at(0.9f, 0f), 0f)

        val square = PowerWindow(shape = WindowShape.RECTANGLE, size = 0.5f, softness = 0f)
        assertEquals("a rectangle reaches into its corners", 1f, square.at(0.45f, 0.45f), 0f)
        assertEquals(0f, square.copy(shape = WindowShape.CIRCLE).at(0.45f, 0.45f), 0f)
        assertEquals(0f, square.at(0.55f, 0f), 0f)
        assertEquals("turned 45°, its corner points up", 1f, square.copy(angle = 45f).at(0f, 0.65f), 0f)
    }

    @Test
    fun `the centre runs from edge to edge of the picture`() {
        val right = PowerWindow(centerX = 1f, size = 0.3f)
        assertEquals(1f, right.coverage(1f, 0.5f, wide), 0f)
        assertEquals(0f, right.coverage(0.5f, 0.5f, wide), 0f)
        val bottom = PowerWindow(centerY = -1f, size = 0.3f)
        assertEquals(1f, bottom.coverage(0.5f, 0f, wide), 0f)
        assertEquals(0f, bottom.coverage(0.5f, 1f, wide), 0f)
    }

    @Test
    fun `a gradient covers the side its angle points to`() {
        val sky = PowerWindow(shape = WindowShape.GRADIENT, softness = 0.25f)
        assertEquals(1f, sky.coverage(0.5f, 1f, wide), 0f)
        assertEquals(0f, sky.coverage(0.5f, 0f, wide), 0f)
        assertEquals(0.5f, sky.coverage(0.2f, 0.5f, wide), 1e-5f)
        assertEquals("smoothstep inside the fade", 0.84375f, sky.at(0f, 0.125f), 1e-5f)
        assertEquals("size does not matter", 0.84375f, sky.copy(size = 2f, aspect = 3f).at(0f, 0.125f), 1e-5f)

        val left = sky.copy(angle = 90f)
        assertEquals(1f, left.coverage(0f, 0.5f, wide), 0f)
        assertEquals(0f, left.coverage(1f, 0.5f, wide), 0f)

        assertEquals("the line moves with the centre", 0.5f, sky.copy(centerY = 0.5f).coverage(0.5f, 0.75f, wide), 1e-5f)
        val hard = sky.copy(softness = 0f)
        assertEquals(1f, hard.coverage(0.5f, 0.51f, wide), 0f)
        assertEquals(0f, hard.coverage(0.5f, 0.49f, wide), 0f)
    }

    @Test
    fun `invert corrects outside the window`() {
        val vignette = PowerWindow(size = 0.5f, softness = 0.25f, invert = true)
        assertEquals(0f, vignette.at(0f, 0f), 0f)
        assertEquals(1f, vignette.coverage(0.98f, 0.98f, wide), 0f)
        assertEquals(0.5f, vignette.at(0f, 0.5f), 1e-5f)
    }

    @Test
    fun `node 2 edits the window grade, node 1 the rest, and a window grade has no window`() {
        val window = PowerWindow(grade = ColorGrade(exposure = -1f))
        val grade = ColorGrade(saturation = 1.2f, window = window)
        assertEquals(ColorGrade(saturation = 1.2f), grade.node(GradeNode.PRIMARY))
        assertEquals(ColorGrade(exposure = -1f), grade.node(GradeNode.WINDOW))
        assertNull(ColorGrade.NEUTRAL.node(GradeNode.WINDOW))

        val brighter = grade.withNode(GradeNode.PRIMARY) { it.copy(exposure = 0.5f) }
        assertEquals(0.5f, brighter.exposure, 0f)
        assertEquals("the window stays", window, brighter.window)

        val nested = grade.withNode(GradeNode.WINDOW) { it.copy(contrast = 1.3f, window = PowerWindow()) }
        assertEquals(ColorGrade(exposure = -1f, contrast = 1.3f), nested.window!!.grade)
        assertEquals(1.2f, nested.saturation, 0f)
        assertEquals("no window: nothing to edit", ColorGrade.NEUTRAL, ColorGrade.NEUTRAL.withNode(GradeNode.WINDOW) { it.copy(exposure = 1f) })
    }

    @Test
    fun `a window whose grade is neutral leaves the clip ungraded`() {
        assertTrue(ColorGrade(window = PowerWindow()).isNeutral)
        assertFalse(ColorGrade(window = PowerWindow(grade = ColorGrade(exposure = 1f))).isNeutral)
        assertFalse(ColorGrade(exposure = 1f, window = PowerWindow()).isNeutral)
        assertTrue(ColorGrade(lookStrength = 40, window = PowerWindow()).isNeutral)
    }

    @Test
    fun `window sliders clamp and a gradient has no size or aspect`() {
        val w = PowerWindow()
        assertEquals(2f, WindowControl.SIZE.write(w, 9f).size, 0f)
        assertEquals(-1f, WindowControl.CENTER_X.write(w, -3f).centerX, 0f)
        assertEquals(0f, WindowControl.SOFTNESS.write(w, -1f).softness, 0f)
        assertEquals(-180f, WindowControl.ANGLE.read(WindowControl.ANGLE.write(w, -400f)), 0f)
        assertEquals(
            listOf(WindowControl.ANGLE, WindowControl.SOFTNESS, WindowControl.CENTER_X, WindowControl.CENTER_Y),
            WindowControl.entries.filter { it.appliesTo(WindowShape.GRADIENT) },
        )
        assertTrue(WindowControl.entries.all { it.appliesTo(WindowShape.RECTANGLE) })
    }
}
