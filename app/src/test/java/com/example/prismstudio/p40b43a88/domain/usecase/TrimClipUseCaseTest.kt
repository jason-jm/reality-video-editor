package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assertLockedTrackUntouched
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrimClipUseCaseTest {

    private val trimClip = TrimClipUseCase()

    private val previous = clipOf("P", 0, 60)
    /** 2 s into a 10 s source: 60 frames of handle before the in point, 240 frames of media after it. */
    private val clip = clipOf("A", 100, 100, sourceInMs = 2_000)
    private val next = clipOf("N", 260, 40)
    private val timeline = timelineOf(previous, clip, next)

    private fun trim(
        timeline: Timeline,
        edge: TrimEdge,
        frame: Int,
        clipId: String = "A",
        snapping: Boolean = false,
        playhead: Int = 0,
        sourceDurationMs: Long? = 10_000,
    ): EditResult = trimClip(timeline, clipId, edge, frame, 5, snapping, playhead, sourceDurationMs, fps = 30)

    private fun span(result: EditResult, id: String = "A") = result.timeline.clip(id)!!.let { it.start until it.end }

    @Test
    fun `a reversed clip keeps the frames that stay in their places, whichever edge moves`() {
        // A plays 2–5.33 s of a 10 s source backwards: its first frame is the window's end.
        val reversed = timelineOf(previous, clip.copy(reverse = true), next)
        val asset = assetOf("media", durationMs = 10_000)
        // The source frame shown (to the nearest frame: positions are whole milliseconds).
        val shown = { t: Timeline, frame: Int -> (t.clip("A")!!.sourceAt(frame, 30, asset) * 30 + 500) / 1000 }

        val longer = trim(reversed, TrimEdge.END, 230).timeline
        assertEquals("lengthening shows what came before the window", 1_000L, longer.clip("A")!!.sourceInMs)
        for (f in 100 until 200) assertEquals("frame $f", shown(reversed, f), shown(longer, f))
        // The new frames carry on backwards from where the clip ended: one frame on in the reversed copy.
        assertEquals(1L, shown(longer, 200) - shown(longer, 199))
        // At most the 60 frames of media before the in point (the neighbour is at 260 too).
        assertEquals(100 until 260, span(trim(reversed, TrimEdge.END, 300)))
        assertEquals(0L, trim(reversed, TrimEdge.END, 300).timeline.clip("A")!!.sourceInMs)

        val shorter = trim(reversed, TrimEdge.END, 150).timeline
        for (f in 100 until 150) assertEquals("frame $f", shown(reversed, f), shown(shorter, f))

        val later = trim(reversed, TrimEdge.START, 130).timeline
        assertEquals("a reversed clip's in point stays when its start moves", 2_000L, later.clip("A")!!.sourceInMs)
        for (f in 130 until 200) assertEquals("frame $f", shown(reversed, f), shown(later, f))
        val earlier = trim(reversed, TrimEdge.START, 70).timeline
        for (f in 100 until 200) assertEquals("frame $f", shown(reversed, f), shown(earlier, f))
    }

    @Test
    fun `trimming the end shortens or lengthens the clip and keeps the in point`() {
        val shorter = trim(timeline, TrimEdge.END, 180)
        assertEquals(100 until 180, span(shorter))
        assertEquals(2_000L, shorter.timeline.clip("A")!!.sourceInMs)
        assertEquals("A", shorter.selectedClipId)
        assertEquals("已修剪“A”，时长 00:00:02:20", shorter.message)

        assertEquals(100 until 230, span(trim(timeline, TrimEdge.END, 230)))
    }

    @Test
    fun `trimming the start moves the source in point with the edge`() {
        val later = trim(timeline, TrimEdge.START, 130)
        assertEquals(130 until 200, span(later))
        assertEquals(3_000L, later.timeline.clip("A")!!.sourceInMs)

        val earlier = trim(timeline, TrimEdge.START, 70)
        assertEquals(70 until 200, span(earlier))
        assertEquals(1_000L, earlier.timeline.clip("A")!!.sourceInMs)

        val fast = timelineOf(previous, clip.copy(speed = ClipSpeed.of(PlaybackSpeed.DOUBLE)), next)
        assertEquals(4_000L, trim(fast, TrimEdge.START, 130).timeline.clip("A")!!.sourceInMs)
    }

    @Test
    fun `an edge stops at its neighbours and at the ends of the source media`() {
        val blockedLeft = trim(timeline, TrimEdge.START, 20)
        assertEquals(60 until 200, span(blockedLeft))
        assertEquals(666L, blockedLeft.timeline.clip("A")!!.sourceInMs)

        val sourceStart = trim(timelineOf(clip, next), TrimEdge.START, 0)
        assertEquals(40 until 200, span(sourceStart))
        assertEquals(0L, sourceStart.timeline.clip("A")!!.sourceInMs)

        assertEquals(100 until 260, span(trim(timeline, TrimEdge.END, 400)))
        assertEquals(100 until 340, span(trim(timelineOf(previous, clip), TrimEdge.END, 400)))
        assertEquals(100 until 400, span(trim(timelineOf(previous, clip), TrimEdge.END, 400, sourceDurationMs = null)))

        // Titles have no source media, so only the neighbours limit them.
        val title = timelineOf(titleClipOf("T", 100, 100))
        assertEquals(10 until 200, span(trim(title, TrimEdge.START, 10, clipId = "T"), "T"))
    }

    @Test
    fun `a clip always keeps at least the minimum number of frames`() {
        val minimum = TrimClipUseCase.MIN_FRAMES

        assertEquals(100 until 100 + minimum, span(trim(timeline, TrimEdge.END, 101)))
        assertEquals(200 - minimum until 200, span(trim(timeline, TrimEdge.START, 199)))
    }

    @Test
    fun `linked partners receive the same edge change`() {
        val linked = timelineOf(previous, clip.copy(linkId = "L"), clipOf("Aa", 100, 100, track = Timeline.A1, sourceInMs = 2_000, linkId = "L"))

        val shortened = trim(linked, TrimEdge.END, 180)
        assertEquals(100 until 180, span(shortened, "A"))
        assertEquals(100 until 180, span(shortened, "Aa"))

        val later = trim(linked, TrimEdge.START, 130)
        assertEquals(130 until 200, span(later, "Aa"))
        assertEquals(3_000L, later.timeline.clip("Aa")!!.sourceInMs)
    }

    @Test
    fun `snapping pulls the edge to a nearby edit point but ignores the clip's own linked partner`() {
        assertEquals(100 until 183, span(trim(timeline, TrimEdge.END, 181, snapping = true, playhead = 183)))

        val linked = timelineOf(previous, clip.copy(linkId = "L"), next, clipOf("Aa", 100, 100, track = Timeline.A1, linkId = "L"))
        assertEquals(100 until 198, span(trim(linked, TrimEdge.END, 198, snapping = true, playhead = 0)))
    }

    @Test
    fun `clips on locked tracks and unknown clips are rejected`() {
        val locked = timelineOf(previous, clip, next, locked = setOf(Timeline.V1))

        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { trim(locked, TrimEdge.END, 150) }.message)
        assertEquals("请先在时间线上选择片段", assertThrows(EditRejected::class.java) { trim(timeline, TrimEdge.END, 150, clipId = "ghost") }.message)
    }
    @Test
    fun `linked partners stay in sync when the partner's track limits the trim`() {
        // A1 has a clip right before the audio partner; V1 has room, so only the partner is blocked.
        val linked = timelineOf(
            clip.copy(linkId = "L"),
            clipOf("X", 50, 50, track = Timeline.A1),
            clipOf("Aa", 100, 100, track = Timeline.A1, sourceInMs = 2_000, linkId = "L"),
        )

        val result = trim(linked, TrimEdge.START, 70)

        val video = result.timeline.clip("A")!!
        val audio = result.timeline.clip("Aa")!!
        assertEquals(video.start until video.end, audio.start until audio.end)
        assertEquals(video.sourceInMs, audio.sourceInMs)
    }
    @Test
    fun `a linked partner on a locked track is never trimmed`() {
        val audioLocked = timelineOf(
            previous,
            clip.copy(linkId = "L"),
            clipOf("Aa", 100, 100, track = Timeline.A1, sourceInMs = 2_000, linkId = "L"),
            locked = setOf(Timeline.A1),
        )

        assertLockedTrackUntouched(audioLocked, Timeline.A1) { trim(audioLocked, TrimEdge.END, 180).timeline }
    }
}
