package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppendMediaUseCaseTest {

    private val append = AppendMediaUseCase(SequentialIds())
    private val beach = assetOf("beach", name = "Beach.mov", durationMs = 10_000)

    @Test
    fun `video with sound lands on V1 with a linked audio partner on A1`() {
        val result = append(Timeline.EMPTY, beach, inMs = 1_000, outMs = 3_000, fps = 30)

        val video = Clip(
            id = "c-2", trackId = Timeline.V1, kind = ClipKind.VIDEO, name = "Beach", start = 0, durationFrames = 60,
            assetId = "beach", sourceInMs = 1_000, linkId = "l-1",
        )
        assertEquals(listOf(video, video.copy(id = "c-3", trackId = Timeline.A1, kind = ClipKind.AUDIO)), result.timeline.clips)
        assertEquals("c-2", result.selectedClipId)
        assertEquals("已添加到时间线：Beach（00:00:02:00）", result.message)
    }

    @Test
    fun `video with sound starts after whichever of V1 and A1 ends later`() {
        val timeline = timelineOf(clipOf("v", 0, 90), clipOf("a", 0, 120, track = Timeline.A1))

        val result = append(timeline, beach, 0, 1_000, 30)

        val added = result.timeline.clipsOn(Timeline.V1).last()
        assertEquals(120, added.start)
        assertEquals(120, result.timeline.clipsOn(Timeline.A1).last().start)
    }

    @Test
    fun `silent video only needs room on V1 and gets no link`() {
        val silent = assetOf("drone", name = "drone.mp4", hasAudio = false)
        val timeline = timelineOf(clipOf("v", 0, 90), clipOf("a", 0, 120, track = Timeline.A1))

        val result = append(timeline, silent, 0, 2_000, 30)

        val added = result.timeline.clip(result.selectedClipId)!!
        assertEquals(90, added.start)
        assertNull(added.linkId)
        assertEquals(3, result.timeline.clips.size)
    }

    @Test
    fun `stills go to the end of V1 for five seconds whatever range was marked`() {
        val still = assetOf("poster", kind = MediaKind.IMAGE, name = "poster.png")
        val timeline = timelineOf(clipOf("v", 0, 40))

        val result = append(timeline, still, inMs = 300, outMs = 400, fps = 24)

        val added = result.timeline.clip(result.selectedClipId)!!
        assertEquals(Timeline.V1, added.trackId)
        assertEquals(ClipKind.VIDEO, added.kind)
        assertEquals(40, added.start)
        assertEquals(120, added.durationFrames)
        assertEquals(0L, added.sourceInMs)
        assertEquals("poster", added.assetId)
    }

    @Test
    fun `audio goes to the end of A2 using the marked range`() {
        val music = assetOf("song", kind = MediaKind.AUDIO, name = "song.final.m4a")
        val timeline = timelineOf(clipOf("a2", 0, 40, track = Timeline.A2), clipOf("v", 0, 500))

        val result = append(timeline, music, inMs = 2_000, outMs = 5_000, fps = 30)

        val added = result.timeline.clip(result.selectedClipId)!!
        assertEquals(Timeline.A2, added.trackId)
        assertEquals(ClipKind.AUDIO, added.kind)
        assertEquals(40, added.start)
        assertEquals(90, added.durationFrames)
        assertEquals(2_000L, added.sourceInMs)
        assertEquals("song.final", added.name)
    }

    @Test
    fun `an empty marked range still yields a one frame clip`() {
        val result = append(Timeline.EMPTY, beach, inMs = 4_000, outMs = 4_000, fps = 30)

        assertEquals(1, result.timeline.clip(result.selectedClipId)!!.durationFrames)
    }

    @Test
    fun `offline media is refused`() {
        val error = assertThrows(EditRejected::class.java) {
            append(Timeline.EMPTY, beach.copy(offline = true), 0, 1_000, 30)
        }
        assertEquals("素材离线，无法添加：Beach.mov", error.message)
    }
}
