package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.usecase.BuildGradeLutUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ComputeScopesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.GenerateLutUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's serial nodes after node 2: whole-picture corrections in turn, switched off or on, on every surface. */
class SerialNodesTest {

    private val grader = BuildGradeLutUseCase()
    private val darker = ColorGrade(gain = WheelValue(master = -0.5f))
    private val warmer = ColorGrade(temperature = 40f)
    private val graded = ColorGrade(exposure = 0.5f, serial = listOf(SerialNode(darker), SerialNode(warmer, enabled = false)))

    @Test
    fun `nodes are numbered 01 on, serial ones read and change their own grade only`() {
        assertEquals(listOf("01", "02", "03", "06"), listOf(GradeNode.PRIMARY, GradeNode.WINDOW, GradeNode.serial(0), GradeNode.serial(3)).map { it.number })
        assertEquals(listOf("主校色", "窗口", "校色"), listOf(GradeNode(0), GradeNode(1), GradeNode(2)).map { it.label })
        assertEquals(darker, graded.node(GradeNode.serial(0)))
        assertNull("not there", graded.node(GradeNode.serial(2)))
        val changed = graded.withNode(GradeNode.serial(1)) { it.copy(saturation = 1.4f, spatialNr = SpatialNoiseReduction(10f, 10f)) }
        assertEquals("its colour only", SpatialNoiseReduction.OFF, changed.serial[1].grade.spatialNr)
        assertEquals(1.4f, changed.serial[1].grade.saturation, 0f)
        assertEquals("node 1 untouched", 0.5f, changed.exposure, 0f)
        assertSame("a missing node changes nothing", graded, graded.withNode(GradeNode.serial(3)) { it.copy(saturation = 2f) })
        // Node 1 keeps the serial nodes; its own grade has none.
        assertEquals(graded.serial, graded.withNode(GradeNode.PRIMARY) { it.copy(contrast = 1.2f) }.serial)
        assertTrue(graded.primary.serial.isEmpty())
    }

    @Test
    fun `a switched-off or neutral node changes nothing, the grade is neutral without active ones`() {
        assertEquals(listOf(darker), graded.activeSerial)
        assertFalse(ColorGrade(serial = listOf(SerialNode(darker))).isNeutral)
        assertTrue(ColorGrade(serial = listOf(SerialNode(darker, enabled = false), SerialNode())).isNeutral)
        // Keyframes on node 1 leave the serial nodes in place.
        val keyed = graded.copy(keyframes = listOf(GradeKeyframe(0, ColorGrade(exposure = 0f)), GradeKeyframe(30, ColorGrade(exposure = 1f))))
        assertEquals(graded.serial, keyed.at(15).serial)
        assertEquals(0.5f, keyed.at(15).exposure, 0.02f)
    }

    @Test
    fun `scopes, thumbnails and the GPU's post LUT grade the nodes in turn after nodes 01 and 02`() {
        val grey = 0xFF808080.toInt()
        val frame = FramePixels(2, 1, IntArray(2) { grey })
        val out = ComputeScopesUseCase(grader).grade(frame, ColorGrade(serial = listOf(SerialNode(darker))))
        val expected = FloatArray(3).also { grader.apply(darker, 128 / 255f, 128 / 255f, 128 / 255f, it) }
        assertEquals((expected[0] * 255f).toInt().toFloat(), ((out.pixels[0] shr 16) and 0xFF).toFloat(), 2f)
        assertTrue("darker than the source", ((out.pixels[0] shr 16) and 0xFF) < 128)
        // Switched off: the picture passes through.
        assertSame(frame, ComputeScopesUseCase(grader).grade(frame, ColorGrade(serial = listOf(SerialNode(darker, enabled = false)))))
        // The post LUT holds the nodes in turn: darker, then warmer.
        val chain = grader.chain(listOf(darker, warmer))
        val step = FloatArray(3)
        grader.apply(darker, 1f, 1f, 1f, step)
        grader.apply(warmer, step[0], step[1], step[2], step)
        val white = chain.last().last().last()
        assertEquals(((step[0].coerceIn(0f, 1f)) * 255f), ((white shr 16) and 0xFF).toFloat(), 1.5f)
        assertEquals(((step[2].coerceIn(0f, 1f)) * 255f), (white and 0xFF).toFloat(), 1.5f)
        val cube = grader.serialCube(listOf(darker, warmer))!!
        val looked = FloatArray(3).also { cube.apply(1f, 1f, 1f, it) }
        assertEquals(step[0].coerceIn(0f, 1f), looked[0], 1e-4f)
        assertNull(grader.serialCube(emptyList()))
    }

    @Test
    fun `a generated LUT holds the serial nodes, even when node 1 has no correction`() {
        val lut = GenerateLutUseCase(grader)(ColorGrade(serial = listOf(SerialNode(darker))), 0).lut
        val white = FloatArray(3).also { lut.apply(1f, 1f, 1f, it) }
        val expected = FloatArray(3).also { grader.apply(darker, 1f, 1f, 1f, it) }
        assertEquals(expected[0].coerceIn(0f, 1f), white[0], 0.01f)
    }

    @Test
    fun `serial nodes and their switches survive a round trip, at most four kept`() {
        val clip = clipOf("c", 0, 30).copy(grade = graded)
        val doc = projectOf(timeline = timelineOf(clip))
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).timeline.clip("c")!!.grade
        assertEquals(graded.serial, reread.serial)
        val crowded = doc.copy(timeline = timelineOf(clip.copy(grade = ColorGrade(serial = List(6) { SerialNode(darker) }))))
        assertEquals(GradeNode.MAX_SERIAL, ProjectJsonCodec.decode(ProjectJsonCodec.encode(crowded)).timeline.clip("c")!!.grade.serial.size)
        // A project from before serial nodes reads with none.
        assertEquals(emptyList<SerialNode>(), ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc.copy(timeline = timelineOf(clipOf("c", 0, 30))))).timeline.clip("c")!!.grade.serial)
    }
}
