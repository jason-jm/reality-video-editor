package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Change Clip Duration (Ctrl+D). */
class ClipDurationUseCaseTest {

    private val use = ClipDurationUseCase()
    private val video = assetOf("media", durationMs = 4_000)
    private val still = assetOf("still", kind = MediaKind.IMAGE)
    private val assets = { id: String? -> listOf(video, still).firstOrNull { it.id == id } }

    // V1: the still s 0–60, then v 60–120 (its sound sv on A1, linked), a gap, w 150–180.
    private val timeline = timelineOf(
        clipOf("s", 0, 60, assetId = "still"), clipOf("v", 60, 60, linkId = "L"), clipOf("w", 150, 30),
        clipOf("sv", 60, 60, Timeline.A1, linkId = "L"),
    )

    private fun Timeline.spans() = clips.associate { it.id to (it.start to it.end) }

    @Test
    fun `typed durations read as timecodes or seconds`() {
        assertEquals(90, ClipDurationUseCase.parse("00:00:03:00", 30))
        assertEquals(90, ClipDurationUseCase.parse("3:00", 30))
        assertEquals(105, ClipDurationUseCase.parse("3:15", 30))
        assertEquals(1815, ClipDurationUseCase.parse("1:00:15", 30))
        assertEquals(75, ClipDurationUseCase.parse("2.5", 30))
        assertEquals(75, ClipDurationUseCase.parse("2.5 秒", 30))
        assertNull("frames past the rate", ClipDurationUseCase.parse("3:30", 30))
        assertNull(ClipDurationUseCase.parse("abc", 30))
        assertNull(ClipDurationUseCase.parse("", 30))
    }

    @Test
    fun `with ripple the rest of the track follows, linked sound too`() {
        val longer = use(timeline, "s", 90, true, assets, 30)
        assertEquals(mapOf("s" to (0 to 90), "v" to (90 to 150), "w" to (180 to 210), "sv" to (60 to 120)), longer.timeline.spans())
        assertEquals("“s”的时长改为 00:00:03:00", longer.message)
        val shorter = use(timeline, "v", 30, true, assets, 30).timeline
        assertEquals(mapOf("s" to (0 to 60), "v" to (60 to 90), "w" to (120 to 150), "sv" to (60 to 90)), shorter.spans())
    }

    @Test
    fun `without ripple it stops at the next clip, and the media limits it`() {
        val stopped = use(timeline, "v", 120, false, assets, 30)
        assertEquals("up to w at 150", 60 to 150, stopped.timeline.clip("v")!!.let { it.start to it.end })
        assertEquals("“v”的时长改为 00:00:03:00（素材或后面的片段只够 00:00:03:00）", stopped.message)
        // The 4 s file: v from its start can run 120 frames at most.
        val media = use(timeline, "v", 200, true, assets, 30)
        assertEquals(120, media.timeline.clip("v")!!.durationFrames)
        assertEquals("w moves by what v really grew", 210 to 240, media.timeline.clip("w")!!.let { it.start to it.end })
        assertEquals("片段至少 5 帧", assertThrows(EditRejected::class.java) { use(timeline, "v", 3, true, assets, 30) }.message)
        assertEquals("时长没有变化", assertThrows(EditRejected::class.java) { use(timeline, "v", 60, true, assets, 30) }.message)
        val butted = timeline.replaceClip(timeline.clip("w")!!.copy(start = 120))
        assertEquals("片段无法再加长：素材或后面的片段挡住了",
            assertThrows(EditRejected::class.java) { use(butted, "v", 90, false, assets, 30) }.message)
    }
}
