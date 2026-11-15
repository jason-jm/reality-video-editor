package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Generate LUT: a clip's grade baked into a cube, with what a cube cannot hold left out. */
class GenerateLutUseCaseTest {

    private val grader = BuildGradeLutUseCase()
    private val generate = GenerateLutUseCase(grader)

    private fun CubeLut.at(r: Float, g: Float, b: Float): FloatArray = FloatArray(3).also { apply(r, g, b, it) }
    private fun graded(grade: ColorGrade, r: Float, g: Float, b: Float): FloatArray = FloatArray(3).also { grader.apply(grade, r, g, b, it) }

    private fun refused(grade: ColorGrade): String? = try {
        generate(grade, 0, size = 5)
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `node 1 goes into the cube point for point`() {
        val grade = ColorGrade(exposure = 0.5f, saturation = 0.4f, temperature = 30f)
        val made = generate(grade, 0, size = 5)
        assertEquals(5, made.lut.size)
        assertTrue(made.lut.threeD)
        assertFalse(made.windowLeftOut)
        // Lattice points hold the grade exactly; red runs fastest.
        assertArrayEquals(graded(grade, 0.25f, 0.5f, 0.75f), made.lut.at(0.25f, 0.5f, 0.75f), 1e-6f)
        assertArrayEquals(graded(grade, 1f, 0f, 0f), made.lut.data.copyOfRange(4 * 3, 4 * 3 + 3), 1e-6f)
        assertEquals("a 33-point cube by default", 33, generate(grade, 0).lut.size)
    }

    @Test
    fun `a full-frame node 2 goes in with its qualifier, a shaped window is left out`() {
        val primary = ColorGrade(contrast = 1.2f)
        val darker = ColorGrade(exposure = -1f)
        val full = primary.copy(window = PowerWindow(WindowShape.FULL, grade = darker))
        val made = generate(full, 0, size = 5)
        assertFalse(made.windowLeftOut)
        val inner = graded(primary, 0.5f, 0.75f, 0.25f)
        assertArrayEquals(graded(darker, inner[0], inner[1], inner[2]), made.lut.at(0.5f, 0.75f, 0.25f), 1e-6f)

        // Keyed on green: a grey stays as node 1 left it, a green is darkened.
        val keyed = primary.copy(window = PowerWindow(WindowShape.FULL, grade = darker, qualifier = HslQualifier(hueCenter = 120f, hueWidth = 60f)))
        val greyed = generate(keyed, 0, size = 5).lut
        assertArrayEquals(graded(primary, 0.5f, 0.5f, 0.5f), greyed.at(0.5f, 0.5f, 0.5f), 1e-6f)
        val green = graded(primary, 0f, 1f, 0f)
        assertArrayEquals(graded(darker, green[0], green[1], green[2]), greyed.at(0f, 1f, 0f), 1e-6f)

        val shaped = primary.copy(window = PowerWindow(WindowShape.CIRCLE, grade = darker))
        val left = generate(shaped, 0, size = 5)
        assertTrue(left.windowLeftOut)
        assertArrayEquals(graded(primary, 0.5f, 0.75f, 0.25f), left.lut.at(0.5f, 0.75f, 0.25f), 1e-6f)
    }

    @Test
    fun `nothing to bake is refused with why`() {
        assertEquals("当前片段没有调色，无需生成 LUT", refused(ColorGrade()))
        assertEquals(
            "节点 02 的窗口只作用于画面的一部分，无法写进 LUT；节点 01 没有调色",
            refused(ColorGrade(window = PowerWindow(WindowShape.RECTANGLE, grade = ColorGrade(saturation = 0f)))),
        )
        // The noise reduction works on neighbouring pixels: no LUT holds it, alone it is refused, with colour it is left out.
        val nr = SpatialNoiseReduction(lumaThreshold = 40f)
        assertEquals("降噪无法写进 LUT；当前片段没有其他调色", refused(ColorGrade(spatialNr = nr)))
        assertEquals("中间调细节无法写进 LUT；当前片段没有其他调色", refused(ColorGrade(midDetail = 40f)))
        assertEquals("降噪和中间调细节无法写进 LUT；当前片段没有其他调色", refused(ColorGrade(spatialNr = nr, midDetail = -20f)))
        assertEquals("时域降噪无法写进 LUT；当前片段没有其他调色", refused(ColorGrade(temporalNr = 50)))
        assertEquals("降噪、时域降噪和中间调细节无法写进 LUT；当前片段没有其他调色", refused(ColorGrade(spatialNr = nr, temporalNr = 30, midDetail = 10f)))
        assertEquals(ColorGrade(temperature = 20f), ColorGrade(temperature = 20f, temporalNr = 80).colorOnly)
        assertArrayEquals(generate(ColorGrade(temperature = 20f), 0, size = 5).lut.at(0.5f, 0.75f, 0.25f), generate(ColorGrade(temperature = 20f, midDetail = 60f), 0, size = 5).lut.at(0.5f, 0.75f, 0.25f), 0f)
        val warm = ColorGrade(temperature = 20f)
        assertArrayEquals(generate(warm, 0, size = 5).lut.at(0.5f, 0.75f, 0.25f), generate(warm.copy(spatialNr = nr), 0, size = 5).lut.at(0.5f, 0.75f, 0.25f), 0f)
    }

    @Test
    fun `a keyed grade bakes the dissolve as it stands at the frame`() {
        val keys = GradeKeyframeUseCase()
        val warm = ColorGrade(exposure = 0.5f, temperature = 20f)
        val cool = ColorGrade(exposure = -1f, temperature = -20f)
        val keyed = keys.adjust(keys.toggle(warm, 10, 90).grade, 70, 90) { cool }
        val made = generate(keyed, 40, size = 5)
        assertTrue(made.keyed)
        val a = graded(warm, 0.5f, 0.25f, 0.75f)
        val b = graded(cool, 0.5f, 0.25f, 0.75f)
        assertArrayEquals("half way between the two keyframes' looks", FloatArray(3) { (a[it] + b[it]) / 2 }, made.lut.at(0.5f, 0.25f, 0.75f), 1e-5f)
        assertArrayEquals("held after the last", b, generate(keyed, 85, size = 5).lut.at(0.5f, 0.25f, 0.75f), 1e-6f)
    }

    @Test
    fun `the cube file reads back as the same LUT`() {
        val made = generate(ColorGrade(saturation = 1.6f, hue = 20f), 0, size = 9).lut
        val text = CubeLutWriter.write(made, "My \"grade\"")
        assertTrue(text.startsWith("TITLE \"My 'grade'\"\n# Created by Reality Video Editor\nLUT_3D_SIZE 9\nDOMAIN_MIN 0.000000 0.000000 0.000000\n"))
        val back = CubeLutParser.parse(text, "x.cube")
        assertEquals("My 'grade'", back.name)
        assertArrayEquals(made.data, back.lut.data, 1e-6f)
        assertEquals("written twice, the same id", back.id, CubeLutParser.parse(CubeLutWriter.write(back.lut, "other"), "y.cube").id)
    }
}
