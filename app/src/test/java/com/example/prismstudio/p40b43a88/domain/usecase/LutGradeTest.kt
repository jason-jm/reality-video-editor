package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.LutRef
import com.example.prismstudio.p40b43a88.domain.model.LutSource
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An imported LUT in a node's look slot: graded on every surface, missing ones change nothing. */
class LutGradeTest {

    /** A library whose LUTs can arrive later, as an import does. */
    private class Library : LutSource {
        val cubes = mutableMapOf<String, CubeLut>()
        override var generation = 0
        override fun cube(id: String): CubeLut? = cubes[id]
    }

    private val swap = CubeLutParser.parse(cubeText(2, title = "Swap") { r, g, b -> floatArrayOf(g, b, r) }, "swap.cube")
    private val ref = LutRef(swap.id, swap.name)
    private val library = Library().apply { cubes[swap.id] = swap.lut }
    private val grader = BuildGradeLutUseCase(luts = library)

    private fun graded(grade: ColorGrade, r: Float, g: Float, b: Float): FloatArray = FloatArray(3).also { grader.apply(grade, r, g, b, it) }

    @Test
    fun `the node's LUT grades at the end of the node, blended by strength`() {
        val full = ColorGrade(lut = ref, lookStrength = 100)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), graded(full, 1f, 0f, 0f), 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f, 0.75f, 0.25f), graded(full, 0.25f, 0.5f, 0.75f), 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f, 0f, 0.5f), graded(full.copy(lookStrength = 50), 1f, 0f, 0f), 1e-6f)
        // The node's corrections come first: the LUT sees the lowered red.
        assertArrayEquals(floatArrayOf(0f, 0f, 0.5f), graded(full.copy(gain = full.gain.copy(master = -0.5f)), 1f, 0f, 0f), 1e-6f)
        assertTrue(full.hasLook)
        assertFalse("a LUT is a grade", full.isNeutral)
    }

    @Test
    fun `a LUT missing from the library leaves the picture as it is until it is imported`() {
        val missing = Library()
        val grader = BuildGradeLutUseCase(luts = missing)
        val grade = ColorGrade(lut = ref, lookStrength = 100)
        val out = FloatArray(3)
        grader.apply(grade, 0.8f, 0.2f, 0.1f, out)
        assertArrayEquals(floatArrayOf(0.8f, 0.2f, 0.1f), out, 1e-6f)
        missing.cubes[swap.id] = swap.lut
        missing.generation++
        grader.apply(grade, 0.8f, 0.2f, 0.1f, out)
        assertArrayEquals("the import is picked up", floatArrayOf(0.2f, 0.1f, 0.8f), out, 1e-6f)
    }

    @Test
    fun `keyframes switch LUTs half way, and the baked cube holds the LUT at its points`() {
        val a = ColorGrade(look = Look.MONO)
        val b = ColorGrade(lut = ref, lookStrength = 100)
        assertNull(a.lerp(b, 0.4f).lut)
        assertEquals(ref, a.lerp(b, 0.6f).lut)
        val cube = BuildGradeLutUseCase(size = 5, luts = library)(b)
        assertEquals("pure red comes out blue", 0xFF0000FF.toInt(), cube[4][0][0])
        assertEquals(0xFFFF0000.toInt(), cube[0][4][0])
    }

    @Test
    fun `the program monitor re-bakes a grade whose LUT arrived`() {
        val later = Library()
        val registry = LiveLookRegistry(later)
        val doc = projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(grade = ColorGrade(lut = ref, lookStrength = 100))))
        registry.update(doc)
        val n = LiveLookRegistry.LUT_SIZE
        val red = ((n - 1) * n + 0) * n + 0
        val before = registry.videoLook("V")!!.lutPixels!![red]
        assertEquals("missing: red stays red", 0xFFFF0000.toInt(), before)
        assertFalse("nothing new without an import", registry.update(doc))

        later.cubes[swap.id] = swap.lut
        later.generation++
        assertTrue(registry.update(doc))
        val after = registry.videoLook("V")!!.lutPixels!![red]
        assertNotEquals(before, after)
        assertEquals(0xFF0000FF.toInt(), after)
    }
}
