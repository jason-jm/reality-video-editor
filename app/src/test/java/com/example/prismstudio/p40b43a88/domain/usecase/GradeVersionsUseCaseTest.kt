package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.GradeVersion
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's local grade versions: a new one copies the one shown, switching keeps each version's work, delete shows the one before. */
class GradeVersionsUseCaseTest {

    private val use = GradeVersionsUseCase()
    private val warm = ColorGrade(temperature = 30f)
    private val mono = ColorGrade(saturation = 0f)
    private val timeline = timelineOf(clipOf("a", 0, 60).copy(grade = warm), clipOf("s", 0, 60, Timeline.A1))

    private fun Timeline.a() = clip("a")!!
    private fun Timeline.names() = use.versions(a()).map { it.name }

    @Test
    fun `a clip without versions has one, unnamed in the project, holding its grade`() {
        assertEquals(listOf(GradeVersion("版本 1", warm)), use.versions(timeline.a()))
        assertEquals(0, use.active(timeline.a()))
    }

    @Test
    fun `a new version copies the one shown and is shown`() {
        val r = use.create(timeline, "a")
        assertEquals(listOf("版本 1", "版本 2"), r.timeline.names())
        assertEquals(1, use.active(r.timeline.a()))
        assertEquals(warm, r.timeline.a().grade)
        assertEquals("已新建版本 2（复制自版本 1）", r.message)
        assertEquals("a", r.selectedClipId)
    }

    @Test
    fun `switching keeps what was done to the version left, and switching back brings it again`() {
        val two = use.create(timeline, "a").timeline
        // Grade version 2 differently, then go back to version 1.
        val graded = two.replaceClip(two.a().copy(grade = mono))
        val back = use.select(graded, "a", 0)
        assertEquals(warm, back.timeline.a().grade)
        assertEquals("已切换到版本 1", back.message)
        assertEquals(listOf(warm, mono), use.versions(back.timeline.a()).map { it.grade })
        val again = use.select(back.timeline, "a", 1).timeline
        assertEquals(mono, again.a().grade)
    }

    @Test
    fun `deleting the shown version shows the one before it, and numbering goes on after the highest`() {
        var t = use.create(timeline, "a").timeline
        t = t.replaceClip(t.a().copy(grade = mono))
        t = use.create(t, "a").timeline // 版本 3, a copy of mono
        assertEquals(listOf("版本 1", "版本 2", "版本 3"), t.names())
        t = use.select(t, "a", 1).timeline
        val r = use.delete(t, "a")
        assertEquals(listOf("版本 1", "版本 3"), r.timeline.names())
        assertEquals(0, use.active(r.timeline.a()))
        assertEquals(warm, r.timeline.a().grade)
        assertEquals("已删除版本 2，显示版本 1", r.message)
        assertEquals(listOf("版本 1", "版本 3", "版本 4"), use.create(r.timeline, "a").timeline.names())
        // Deleting the first shows the next one, which becomes first.
        val first = use.delete(r.timeline, "a").timeline
        assertEquals(listOf("版本 3"), first.names())
        assertEquals(mono, first.a().grade)
    }

    @Test
    fun `one version cannot go, the shown one cannot be taken up again, and sound, missing or locked clips are refused`() {
        assertEquals("片段只有一个调色版本", assertThrows(EditRejected::class.java) { use.delete(timeline, "a") }.message)
        val two = use.create(timeline, "a").timeline
        assertEquals("已经是版本 2", assertThrows(EditRejected::class.java) { use.select(two, "a", 1) }.message)
        assertEquals("没有这个调色版本", assertThrows(EditRejected::class.java) { use.select(two, "a", 5) }.message)
        assertEquals("只有画面片段有调色版本", assertThrows(EditRejected::class.java) { use.create(timeline, "s") }.message)
        assertEquals("请先选择片段", assertThrows(EditRejected::class.java) { use.create(timeline, null) }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V1) it.copy(locked = true) else it })
        assertThrows(EditRejected::class.java) { use.create(locked, "a") }
    }

    @Test
    fun `a clip holds at most eight versions`() {
        var t = timeline
        repeat(GradeVersionsUseCase.MAX - 1) { t = use.create(t, "a").timeline }
        assertEquals(GradeVersionsUseCase.MAX, t.names().size)
        assertEquals("一个片段最多 8 个调色版本", assertThrows(EditRejected::class.java) { use.create(t, "a") }.message)
    }
}
