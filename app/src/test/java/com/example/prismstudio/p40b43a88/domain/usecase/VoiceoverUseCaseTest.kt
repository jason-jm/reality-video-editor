package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** A recorded narration placed on the timeline: media of the project, a clip where the recording started. */
class VoiceoverUseCaseTest {

    private val voiceover = VoiceoverUseCase(SequentialIds())
    private val take = VoiceTake("file:///rec/vo.wav", 2_000, 192_044)
    private val doc = projectOf(
        timeline = timelineOf(clipOf("M", 0, 120, track = Timeline.A2, assetId = "song")),
        media = listOf(assetOf("song", kind = MediaKind.AUDIO)),
    )

    private fun rejection(block: () -> Unit): String? = try {
        block(); fail("expected a refusal"); null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `a take on a new track becomes media and a clip where it started`() {
        val result = voiceover.place(doc, take, 45, null)
        val d = result.doc
        val asset = d.media.last()
        assertEquals("旁白 1.wav", asset.name)
        assertEquals(MediaKind.AUDIO, asset.kind)
        assertEquals(2_000L, asset.durationMs)
        assertEquals("recordings/", asset.relativePath)
        val clip = d.timeline.clip(result.clipId)!!
        assertEquals(listOf("a3", 45, 60, asset.id), listOf(clip.trackId, clip.start, clip.durationFrames, clip.assetId))
        assertEquals(TrackKind.AUDIO, d.timeline.track("a3")!!.kind)
        assertEquals("已录制“旁白 1”（00:00:02:00），放在 A3 的 00:00:01:15", result.message)
        // The next take is numbered on (three audio tracks now: it goes on one of them).
        assertEquals("旁白 2.wav", voiceover.place(d, take, 200, "a3").doc.media.last().name)
    }

    @Test
    fun `takes are uncompressed WAV, numbered on after the AAC takes of earlier versions`() {
        val older = doc.copy(media = doc.media + assetOf("old", kind = MediaKind.AUDIO, name = "旁白 3.m4a"))
        val result = voiceover.place(older, take, 0, null)
        val asset = result.doc.media.last()
        assertEquals("旁白 4.wav", asset.name)
        assertEquals("PCM 48 kHz 16 位单声道", asset.audioDescription)
        assertEquals("the clip plays the take from its first sample", 0L, result.doc.timeline.clip(result.clipId)!!.sourceInMs)
    }

    @Test
    fun `saving to a timeline that is no longer open preserves the foreground timeline and converts the start frame`() {
        val switched = TimelinesUseCase(SequentialIds()).create(doc).doc
        val result = voiceover.save(switched, take, doc.timelineId, 60, 60, Timeline.A2)
        assertEquals(switched.timelineId, result.doc.timelineId)
        assertEquals(switched.timeline, result.doc.timeline)
        assertEquals(null, result.clipId)
        val clip = result.doc.storedTimeline(doc.timelineId)!!.timeline.clips.single { it.name == "旁白 1" }
        assertEquals(Timeline.A2, clip.trackId)
        assertEquals(doc.format.fps, clip.start)
        assertEquals(take.uri, result.doc.asset(clip.assetId)?.uri)
    }

    @Test
    fun `saving a take whose timeline was removed keeps its media without editing the current timeline`() {
        val result = voiceover.save(doc, take, "deleted", 30, 30, Timeline.A2)
        assertEquals(doc.timeline, result.doc.timeline)
        assertEquals(null, result.clipId)
        assertEquals(take.uri, result.doc.media.last().uri)
        assertEquals("旁白已保存到素材库：找不到这条时间线", result.message)
    }

    @Test
    fun `a take on an existing track covers what was there`() {
        val result = voiceover.place(doc, take, 30, Timeline.A2)
        val a2 = result.doc.timeline.clipsOn(Timeline.A2)
        assertEquals(listOf(0 to 30, 30 to 60, 90 to 30), a2.map { it.start to it.durationFrames })
        assertEquals("已录制“旁白 1”（00:00:02:00），放在 A2 的 00:00:01:00，覆盖了原有声音", result.message)
    }

    @Test
    fun `a locked or full set of tracks, a missing track and an empty take are refused`() {
        val locked = doc.copy(timeline = doc.timeline.copy(tracks = doc.timeline.tracks.map { if (it.id == Timeline.A2) it.copy(locked = true) else it }))
        assertEquals(true, rejection { voiceover.place(locked, take, 0, Timeline.A2) }?.contains("锁定"))
        var tracks = doc.timeline
        while (TracksUseCase.canAdd(tracks, TrackKind.AUDIO)) tracks = tracks.withTrack(tracks.nextTrack(TrackKind.AUDIO))
        val full = doc.copy(timeline = tracks)
        assertEquals("音频轨已满：请选一条已有的音频轨", rejection { voiceover.place(full, take, 0, null) })
        assertEquals("找不到这条音频轨", rejection { voiceover.place(doc, take, 0, Timeline.V1) })
        assertEquals("录音太短，没有可以放上时间线的声音", rejection { voiceover.place(doc, take.copy(durationMs = 10), 0, null) })
    }
}
