package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Clip and track names (Resolve's clip name and track name). */
class RenameUseCaseTest {

    private val use = RenameUseCase()
    private val timeline = timelineOf(clipOf("a", 0, 60), clipOf("sa", 0, 60, Timeline.A1))

    @Test
    fun `a clip takes the new name, trimmed, within 40 characters`() {
        val result = use.clip(timeline, "a", "  开场 · 江边  ")
        assertEquals("开场 · 江边", result.timeline.clip("a")!!.name)
        assertEquals("片段已重命名为“开场 · 江边”", result.message)
        assertEquals("a", result.selectedClipId)
        assertEquals("片段名称不能为空", assertThrows(EditRejected::class.java) { use.clip(timeline, "a", "   ") }.message)
        assertEquals("片段名称最多 40 个字", assertThrows(EditRejected::class.java) { use.clip(timeline, "a", "x".repeat(41)) }.message)
        assertEquals("名称没有变化", assertThrows(EditRejected::class.java) { use.clip(timeline, "a", "a") }.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V1) it.copy(locked = true) else it })
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { use.clip(locked, "a", "b") }.message)
    }

    @Test
    fun `a track takes a short name no other track has`() {
        val result = use.track(timeline, Timeline.A1, "对白")
        assertEquals("对白", result.timeline.track(Timeline.A1)!!.name)
        assertEquals("轨道 A1 已重命名为“对白”", result.message)
        assertNull("the selection is left alone", result.selectedClipId)
        assertEquals("已有名为“V1”的轨道", assertThrows(EditRejected::class.java) { use.track(timeline, Timeline.A1, "V1") }.message)
        assertEquals("轨道名称最多 6 个字", assertThrows(EditRejected::class.java) { use.track(timeline, Timeline.A1, "背景音乐与音效") }.message)
        assertEquals("轨道名称最多 6 个字", use.problem("背景音乐与音效", "轨道"))
        assertNull(use.problem("音乐", "轨道"))
    }
}
