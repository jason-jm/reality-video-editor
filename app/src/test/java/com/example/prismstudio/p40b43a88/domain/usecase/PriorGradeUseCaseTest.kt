package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.GradeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.GradeVersion
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Apply Grade from One / Two Clips Prior: the grade of the picture clip one or two before, in timeline order. */
class PriorGradeUseCaseTest {

    private val use = PriorGradeUseCase()
    private val warm = ColorGrade(temperature = 30f)
    private val mono = ColorGrade(saturation = 0f)

    // Timeline order: a (V1 0), b (V2 30), sound s (A1 40, skipped), c (V1 60), d (V1 120).
    private val timeline = timelineOf(
        clipOf("c", 60, 60), clipOf("a", 0, 60).copy(grade = warm), clipOf("b", 30, 60, Timeline.V2).copy(grade = mono),
        clipOf("s", 40, 60, Timeline.A1), clipOf("d", 120, 30),
    )

    @Test
    fun `one prior and two prior follow timeline order across tracks, sound left out`() {
        val one = use(timeline, "c", 1)
        assertEquals(mono, one.timeline.clip("c")!!.grade)
        assertEquals("已应用前一个片段“b”的调色", one.message)
        assertEquals("c", one.selectedClipId)
        val two = use(timeline, "c", 2)
        assertEquals(warm, two.timeline.clip("c")!!.grade)
        assertEquals("已应用前第 2 个片段“a”的调色", two.message)
    }

    @Test
    fun `keyframes are cut to the clip's length and its versions stay, the grade landing on the one shown`() {
        val keyed = ColorGrade(exposure = 0.5f, keyframes = listOf(GradeKeyframe(0, ColorGrade()), GradeKeyframe(50, ColorGrade(exposure = 1f))))
        val versions = listOf(GradeVersion("版本 1", warm), GradeVersion("版本 2", ColorGrade()))
        val t = timeline.replaceClip(timeline.clip("c")!!.copy(grade = keyed))
            .replaceClip(timeline.clip("d")!!.copy(gradeVersions = versions, gradeVersion = 1))
        val d = use(t, "d", 1).timeline.clip("d")!!
        assertEquals("keyframes kept, cut at the 30-frame clip's end", true, d.grade.keyframes.isNotEmpty() && d.grade.keyframes.all { it.frame <= 30 })
        assertEquals(0, d.grade.keyframes.first().frame)
        assertEquals(versions, d.gradeVersions)
        assertEquals(1, d.gradeVersion)
    }

    @Test
    fun `no clip that far back, a sound clip, nothing selected or a locked track are refused`() {
        assertEquals("前面没有片段", assertThrows(EditRejected::class.java) { use(timeline, "a", 1) }.message)
        assertEquals("前面没有第 2 个片段", assertThrows(EditRejected::class.java) { use(timeline, "b", 2) }.message)
        assertEquals("只有画面片段可以调色", assertThrows(EditRejected::class.java) { use(timeline, "s", 1) }.message)
        assertEquals("请先选择片段", assertThrows(EditRejected::class.java) { use(timeline, null, 1) }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V1) it.copy(locked = true) else it })
        assertThrows(EditRejected::class.java) { use(locked, "c", 1) }
    }
}
