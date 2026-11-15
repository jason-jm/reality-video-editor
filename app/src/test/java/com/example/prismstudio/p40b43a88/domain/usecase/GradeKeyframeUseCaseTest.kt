package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.dissolveAt
import com.example.prismstudio.p40b43a88.domain.model.frozenAt
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.retimedKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic keyframes on node 1: the whole correction at frames of the clip, dissolving between them. */
class GradeKeyframeUseCaseTest {

    private val keys = GradeKeyframeUseCase()
    private val warm = ColorGrade(exposure = 0.5f, temperature = 20f)
    private val cool = ColorGrade(exposure = -1f, temperature = -20f)

    /** Keyed at 10 (warm) and 70 (cool) on a 90-frame clip. */
    private fun keyed(): ColorGrade = keys.adjust(keys.toggle(warm, 10, 90).grade, 70, 90) { cool }

    private fun rejected(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `the first keyframe holds the correction there, and removing the last keeps it`() {
        val one = keys.toggle(warm, 10, 90)
        assertEquals(listOf(10), one.grade.keyframes.map { it.frame })
        assertEquals(warm, one.grade.keyframes.single().grade)
        assertEquals("已添加调色关键帧：再到别处调整，调色会随时间变化", one.message)
        assertEquals("把播放头移到片段内，再添加调色关键帧", rejected { keys.toggle(warm, 90, 90) })

        val two = keyed()
        assertEquals(listOf(10, 70), two.keyframes.map { it.frame })
        val left = keys.toggle(two, 70, 90)
        assertEquals("已删除调色关键帧 · 还剩 1 个", left.message)
        val none = keys.toggle(left.grade, 10, 90)
        assertEquals("已删除调色关键帧，调色不再变化", none.message)
        assertEquals("the correction stays as it was there", warm, none.grade)
    }

    @Test
    fun `the noise reduction is not keyframed - keyed node 1 keeps it everywhere, and removing the last keyframe too`() {
        val nr = SpatialNoiseReduction(lumaThreshold = 30f, chromaThreshold = 20f)
        val keyedWithNr = keyed().copy(spatialNr = nr)
        assertEquals(nr, keyedWithNr.at(40).spatialNr)
        assertEquals(nr, keyedWithNr.frozenAt(80).spatialNr)
        // A keyframe added or changed holds the colour only.
        val third = keys.toggle(keyedWithNr, 40, 90).grade
        assertTrue(third.keyframes.all { it.grade.spatialNr == SpatialNoiseReduction.OFF })
        assertTrue(keys.adjust(keyedWithNr, 70, 90) { it.copy(exposure = 0f) }.keyframes.all { it.grade.spatialNr == SpatialNoiseReduction.OFF })
        val none = keys.toggle(keys.toggle(keyedWithNr, 70, 90).grade, 10, 90).grade
        assertEquals("the last keyframe gone, the noise reduction stays", nr, none.spatialNr)
        assertEquals(warm, none.colorOnly)
    }

    @Test
    fun `a keyed grade changes at the playhead, adding a keyframe that keeps the look there`() {
        val g = keyed()
        val half = g.at(40)
        assertEquals(-0.25f, half.exposure, 1e-5f)
        assertEquals(0f, half.temperature, 1e-5f)
        assertEquals("held before the first keyframe", warm, g.at(0).primary)
        assertEquals("and after the last", cool, g.at(89).primary)

        val edited = keys.adjust(g, 40, 90) { it.copy(saturation = 1.5f) }
        assertEquals(listOf(10, 40, 70), edited.keyframes.map { it.frame })
        val mid = edited.keyframes[1].grade
        assertEquals(-0.25f, mid.exposure, 1e-5f)
        assertEquals(1.5f, mid.saturation, 0f)
        assertEquals("the others stay", listOf(warm, cool), listOf(edited.keyframes[0].grade, edited.keyframes[2].grade))
        assertEquals("播放头不在片段内：移进片段再调色", rejected { keys.adjust(g, 95, 90) { it } })
        assertEquals("without keyframes the correction changes throughout", 1.5f, keys.adjust(warm, 40, 90) { it.copy(saturation = 1.5f) }.saturation, 0f)

        val windowed = g.copy(window = PowerWindow(grade = ColorGrade(exposure = 1f)))
        assertEquals("node 2 is not keyed", windowed.window, keys.adjust(windowed, 40, 90) { it.copy(tint = 5f) }.window)
        assertEquals(keys.previous(g, 40), 10)
        assertEquals(keys.next(g, 40), 70)
        assertNull(keys.next(g, 70))
    }

    @Test
    fun `the picture dissolves from one keyframe's look to the next, eased where they say`() {
        val g = keyed()
        val mid = g.dissolveAt(40f)!!
        assertEquals(warm, mid.from)
        assertEquals(cool, mid.to)
        assertEquals(0.5f, mid.mix, 1e-6f)
        assertEquals("before the first: its look", 0f, g.dissolveAt(5f)!!.mix, 0f)
        assertEquals(cool, g.dissolveAt(80f)!!.from)
        assertNull("a still grade does not dissolve", warm.dissolveAt(40f))

        val eased = keys.ease(g, 10, arriving = false)
        assertEquals("已开启缓出 · 离开此关键帧时逐渐加速", eased.message)
        assertEquals("leaving the first keyframe slowly", 0.375f, eased.grade.dissolveAt(40f)!!.mix, 1e-5f)
        assertEquals("第一个关键帧之前没有变化，无需缓入", rejected { keys.ease(g, 10, arriving = true) })
        assertEquals("把播放头移到调色关键帧上，再设置缓入缓出", rejected { keys.ease(g, 40, arriving = true) })
    }

    @Test
    fun `settings blend in between, a look and a reshaped curve switch half way`() {
        val a = ColorGrade(lift = WheelValue(0.2f, 0f, -0.2f), look = Look.MONO, lookStrength = 40)
        val b = ColorGrade(
            lift = WheelValue(0f, 0.2f, 0.2f),
            look = Look.TEAL_ORANGE,
            lookStrength = 80,
            curves = ToneCurves(luma = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.6f), CurvePoint(1f, 1f))),
        )
        val quarter = a.lerp(b, 0.25f)
        assertEquals(0.15f, quarter.lift.x, 1e-6f)
        assertEquals(0.05f, quarter.lift.y, 1e-6f)
        assertEquals(-0.1f, quarter.lift.master, 1e-6f)
        assertEquals(50, quarter.lookStrength)
        assertEquals(Look.MONO, quarter.look)
        assertEquals(ToneCurves.IDENTITY, quarter.curves.luma)
        val most = a.lerp(b, 0.75f)
        assertEquals(Look.TEAL_ORANGE, most.look)
        assertEquals(b.curves.luma, most.curves.luma)
        val c = ColorGrade(curves = ToneCurves(luma = listOf(CurvePoint(0f, 0.2f), CurvePoint(1f, 0.8f))))
        assertEquals("same points: moved in between", CurvePoint(0f, 0.1f), ColorGrade().lerp(c, 0.5f).curves.luma[0])
    }

    @Test
    fun `split, trim, speed and freeze carry the keyframes with the picture`() {
        val g = keyed()
        // Frames 20–59 of the clip: cut keys at both ends, counted from the new start.
        val piece = g.keysFor(20, 40)
        assertEquals(listOf(0, 39), piece.keyframes.map { it.frame })
        assertEquals(g.at(20).exposure, piece.at(0).exposure, 1e-5f)
        assertEquals(g.at(59).exposure, piece.at(39).exposure, 1e-5f)
        assertEquals("twice as long at half speed", listOf(20, 140), g.retimedKeys(90, 180).keyframes.map { it.frame })
        val held = g.frozenAt(40)
        assertTrue(held.keyframes.isEmpty())
        assertEquals(-0.25f, held.exposure, 1e-5f)
        assertFalse("keyed is never neutral", ColorGrade(keyframes = keys.toggle(ColorGrade(), 0, 10).grade.keyframes).isNeutral)
    }
}
