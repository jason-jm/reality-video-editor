package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.ColorWarper
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Resolve's Color Warper (hue–saturation): grid points moved round and across the chroma plane, luma kept. */
class ColorWarperTest {

    private val out = FloatArray(3)
    private fun warp(w: ColorWarper, r: Float, g: Float, b: Float) = FloatArray(3).also { w.apply(r, g, b, it) }
    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
    private fun hue(c: FloatArray) = Math.toDegrees(atan2(sqrt(3.0) / 2 * (c[1] - c[2]), (c[0] - 0.5 * (c[1] + c[2])).toDouble())).toFloat()
    private fun chroma(c: FloatArray) = hypot(c[0] - 0.5f * (c[1] + c[2]), (sqrt(3.0) / 2 * (c[1] - c[2])).toFloat())

    @Test
    fun `untouched it changes nothing, and it puts every colour back where it was`() {
        assertTrue(ColorWarper.NEUTRAL.isIdentity)
        for (c in listOf(floatArrayOf(0.8f, 0.3f, 0.1f), floatArrayOf(0.2f, 0.6f, 0.9f), floatArrayOf(0.5f, 0.5f, 0.5f))) {
            assertArrayEquals(c, warp(ColorWarper.NEUTRAL, c[0], c[1], c[2]), 1e-5f)
        }
        assertTrue(ColorGrade(warper = ColorWarper().with(0, 2, 0f, 0f)).isNeutral)
    }

    @Test
    fun `the red point on the outer ring turns pure red toward orange, keeping its luma, and leaves yellow alone`() {
        val w = ColorWarper().with(0, 2, 30f, 0f)
        val red = warp(w, 1f, 0f, 0f)
        assertEquals(30f, hue(red), 0.01f)
        assertEquals(0.2126f, luma(red), 1e-5f)
        // Orange as saturated as red is beyond the display at red's luma: the hue is kept, the saturation gives way.
        assertTrue(red.all { it in -1e-5f..1.00001f })
        assertTrue(chroma(red) < 1f)
        // A paler red turns as far, all of its saturation kept.
        val pale = warp(w, 0.8f, 0.4f, 0.4f)
        assertEquals(w.displacement(0f, 0.4f).first, hue(pale), 0.01f)
        assertEquals(0.4f, chroma(pale), 1e-4f)
        // Yellow sits on its own spoke: nothing of red's move reaches it; a grey stays grey.
        assertArrayEquals(floatArrayOf(1f, 1f, 0f), warp(w, 1f, 1f, 0f), 1e-5f)
        assertArrayEquals(floatArrayOf(0.4f, 0.4f, 0.4f), warp(w, 0.4f, 0.4f, 0.4f), 1e-6f)
        // Half way to yellow the move is half; half way to the centre it is half of what the middle ring takes (none).
        assertEquals(15f, w.displacement(30f, 1f).first, 1e-4f)
        assertEquals(0f, w.displacement(0f, 2f / 3f).first, 1e-4f)
        assertEquals(15f, w.displacement(0f, 5f / 6f).first, 1e-4f)
    }

    @Test
    fun `saturation moves in and out, moves are clamped and dissolve between keyframes`() {
        val w = ColorWarper().with(2, 2, 0f, -0.25f)
        assertEquals(0.75f, chroma(warp(w, 0f, 1f, 0f)), 1e-4f)
        val clamped = ColorWarper().with(1, 1, 90f, 0.9f).move(1, 1)!!
        assertEquals(ColorWarper.MAX_HUE to ColorWarper.MAX_SAT, clamped.hue to clamped.sat)
        // Back home, a point leaves the list.
        assertTrue(w.with(2, 2, 0f, 0f).moves.isEmpty())
        val half = ColorWarper().lerp(ColorWarper().with(0, 2, 20f, 0.1f), 0.5f).move(0, 2)!!
        assertEquals(10f, half.hue, 1e-5f)
        assertEquals(0.05f, half.sat, 1e-5f)
    }

    @Test
    fun `the grade's LUT holds it and the project file keeps only the moved points`() {
        val grade = ColorGrade(warper = ColorWarper().with(0, 2, 30f, 0f))
        BuildGradeLutUseCase().apply(grade, 1f, 0f, 0f, out)
        assertEquals(30f, hue(out), 0.05f)
        val plain = projectOf(timeline = timelineOf(clipOf("c", 0, 30)))
        assertFalse(ProjectJsonCodec.encode(plain).contains("\"warper\""))
        val graded = plain.copy(timeline = plain.timeline.copy(clips = plain.timeline.clips.map { it.copy(grade = grade) }))
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(graded))
        assertEquals(grade.warper, back.timeline.clips.single().grade.warper)
    }
}
