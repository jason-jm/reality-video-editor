package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assertLockedTrackUntouched
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RippleDeleteUseCaseTest {

    private val rippleDelete = RippleDeleteUseCase(SequentialIds())

    private val clips = arrayOf(
        clipOf("A", 0, 100, linkId = "L"),
        clipOf("B", 100, 60),
        clipOf("D", 200, 60),
        clipOf("Aa", 0, 100, track = Timeline.A1, linkId = "L"),
        clipOf("E", 120, 60, track = Timeline.A1),
        titleClipOf("T", 130, 60),
    )
    private val timeline = timelineOf(*clips)

    private fun starts(timeline: Timeline) = timeline.clips.associate { it.id to it.start }

    @Test
    fun `removes the clip with its linked audio and closes the gap on every track clear over it`() {
        val result = rippleDelete(timeline, "A", fps = 30)

        // V2 has nothing over A's span either, so its title moves up with the shots it sits on.
        assertEquals(mapOf("B" to 0, "D" to 100, "E" to 20, "T" to 30), starts(result.timeline))
        assertNull(result.selectedClipId)
        assertEquals("已波纹删除“A”，后续片段前移 00:00:03:10", result.message)
    }

    @Test
    fun `only clips after the removed one move up`() {
        assertEquals(mapOf("A" to 0, "D" to 140, "Aa" to 0, "E" to 120, "T" to 130), starts(rippleDelete(timeline, "B", 30).timeline))
        assertEquals(mapOf("A" to 0, "B" to 100, "Aa" to 0, "E" to 120, "T" to 130), starts(rippleDelete(timeline, "D", 30).timeline))
    }

    @Test
    fun `an unlinked clip's ripple keeps later pictures with their sound, and a music bed across it stays`() {
        // V1: a freeze frame X, then a picture P with its sound Ps on A1; A2: music under both; ST1: a subtitle on P.
        val x = clipOf("X", 0, 60)
        val p = clipOf("P", 60, 60, linkId = "Q")
        val ps = clipOf("Ps", 60, 60, track = Timeline.A1, linkId = "Q")
        val music = clipOf("M", 0, 200, track = Timeline.A2)
        val sub = titleClipOf("S", 70, 30, track = Timeline.V2)
        val result = rippleDelete(timelineOf(x, p, ps, music, sub), "X", fps = 30).timeline
        assertEquals("the picture and its sound move together", 0 to 0, result.clip("P")!!.start to result.clip("Ps")!!.start)
        assertEquals("the title stays on its shot", 10, result.clip("S")!!.start)
        assertEquals("the music runs on", 0 to 200, result.clip("M")!!.let { it.start to it.end })
        // A locked track keeps its place.
        val locked = rippleDelete(timelineOf(x, p, ps, music, sub, locked = setOf(Timeline.V2)), "X", fps = 30).timeline
        assertEquals(70, locked.clip("S")!!.start)
    }

    @Test
    fun `a track with something across the span loses that stretch, so what follows stays in sync`() {
        // V1: P1, the shot X to delete, then P2 with its sound P2s on A1; A1 also has an ambience under P1 and X.
        val p1 = clipOf("P1", 0, 60)
        val x = clipOf("X", 60, 60)
        val p2 = clipOf("P2", 150, 60, linkId = "Q")
        val amb = clipOf("Amb", 0, 150, track = Timeline.A1)
        val p2s = clipOf("P2s", 150, 60, track = Timeline.A1, linkId = "Q")
        val result = rippleDelete(timelineOf(p1, x, p2, amb, p2s), "X", fps = 30)
        val t = result.timeline
        assertEquals("P2 keeps its sound", 90 to 90, t.clip("P2")!!.start to t.clip("P2s")!!.start)
        assertEquals("the ambience loses X's two seconds", listOf(0 to 60, 60 to 90), t.clipsOn(Timeline.A1).filter { it.name == "Amb" }.map { it.start to it.end })
        assertEquals("已波纹删除“X”，后续片段前移 00:00:02:00，其他轨道剪去了同样长度", result.message)
    }

    @Test
    fun `clips on locked tracks and unknown clips are rejected`() {
        val locked = timelineOf(*clips, locked = setOf(Timeline.V1))

        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { rippleDelete(locked, "B", 30) }.message)
        assertEquals("请先在时间线上选择片段", assertThrows(EditRejected::class.java) { rippleDelete(timeline, "ghost", 30) }.message)
    }
    @Test
    fun `a linked partner on a locked track is neither deleted nor shifted`() {
        val audioLocked = timelineOf(*clips, locked = setOf(Timeline.A1))

        assertLockedTrackUntouched(audioLocked, Timeline.A1) { rippleDelete(audioLocked, "A", 30).timeline }
    }
}
