package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adding and deleting tracks, and where titles and music go when their track is gone. */
class TracksUseCaseTest {

    private val tracks = TracksUseCase()

    private fun Timeline.without(vararg ids: String) = copy(tracks = tracks.filter { it.id !in ids })

    @Test
    fun `new video tracks go on top, new audio tracks at the bottom, eight of each at most`() {
        val v3 = tracks.add(projectOf(timeline = timelineOf()), TrackKind.VIDEO)
        assertEquals("已添加轨道 V3", v3.message)
        assertEquals(listOf("v3", "v2", "v1", "a1", "a2"), v3.doc.timeline.tracks.map { it.id })

        val a3 = tracks.add(v3.doc, TrackKind.AUDIO)
        assertEquals(listOf("v3", "v2", "v1", "a1", "a2", "a3"), a3.doc.timeline.tracks.map { it.id })
        assertEquals("A3", a3.doc.timeline.track("a3")!!.name)
        assertTrue("room for more than the window shows: the lanes scroll", TracksUseCase.canAdd(a3.doc.timeline, TrackKind.VIDEO))
        var doc = a3.doc
        repeat(5) { doc = tracks.add(tracks.add(doc, TrackKind.VIDEO).doc, TrackKind.AUDIO).doc }
        assertEquals(listOf("v8", "v7", "v6", "v5", "v4", "v3", "v2", "v1"), doc.timeline.tracks.filter { it.kind == TrackKind.VIDEO }.map { it.id })
        assertEquals("A8", doc.timeline.tracks.last().name)
        assertEquals("最多 8 条视频轨道", assertThrows(EditRejected::class.java) { tracks.add(doc, TrackKind.VIDEO) }.message)
        assertEquals("最多 8 条音频轨道", assertThrows(EditRejected::class.java) { tracks.add(doc, TrackKind.AUDIO) }.message)
        assertFalse(TracksUseCase.canAdd(doc.timeline, TrackKind.VIDEO))
    }

    @Test
    fun `numbering continues past the highest track in use`() {
        val withoutV2 = timelineOf().without(Timeline.V2)
        assertEquals("V2", withoutV2.nextTrack(TrackKind.VIDEO).name)
        val gap = tracks.add(projectOf(timeline = withoutV2), TrackKind.VIDEO).doc.timeline
        val third = tracks.add(projectOf(timeline = gap), TrackKind.VIDEO).doc.timeline
        assertEquals(listOf("v3", "v2", "v1"), third.tracks.filter { it.kind == TrackKind.VIDEO }.map { it.id })
    }

    @Test
    fun `deleting a track takes its clips and its mixer channel, V1 and A1 stay`() {
        val timeline = timelineOf(
            clipOf("A", 0, 30, track = Timeline.A2),
            clipOf("B", 30, 30, track = Timeline.A2),
            clipOf("C", 0, 30),
        )
        val soloed = MixerState.forTracks(timeline.tracks).let { it.replace(it.channel(Timeline.A2).copy(solo = true)) }
        val doc = projectOf(timeline = timeline, mixer = soloed)
        assertFalse(doc.mixer.isAudible(Timeline.A1))

        val result = tracks.remove(doc, Timeline.A2)
        assertEquals("已删除轨道 A2 及其中 2 个片段", result.message)
        assertEquals(listOf("C"), result.doc.timeline.clips.map { it.id })
        assertTrue("a soloed track that is gone silences nothing", result.doc.mixer.isAudible(Timeline.A1))
        assertEquals("已删除轨道 V2", tracks.remove(doc, Timeline.V2).message)

        assertEquals("V1 是基础轨道，不能删除", assertThrows(EditRejected::class.java) { tracks.remove(doc, Timeline.V1) }.message)
        assertThrows(EditRejected::class.java) { tracks.remove(doc, Timeline.A1) }
        assertThrows(EditRejected::class.java) { tracks.remove(projectOf(timeline = timelineOf(locked = setOf(Timeline.V2))), Timeline.V2) }
        assertFalse(TracksUseCase.canRemove(timeline.track(Timeline.V1)!!))
    }

    @Test
    fun `titles and music find another upper track, or make one, when theirs was deleted`() {
        val title = AddTitleUseCase(SequentialIds())
        val onlyV1 = title(timelineOf().without(Timeline.V2), TitleTemplate.entries.first(), 0, 30)
        assertEquals(listOf("v2", "v1", "a1", "a2"), onlyV1.timeline.tracks.map { it.id })
        assertEquals("v2", onlyV1.timeline.clips.single().trackId)
        assertTrue(onlyV1.message.endsWith("（新建轨道 V2）"))

        val withV3 = timelineOf().without(Timeline.V2).withTrack(timelineOf().nextTrack(TrackKind.VIDEO))
        assertEquals("v3", title(withV3, TitleTemplate.entries.first(), 0, 30).timeline.clips.single().trackId)

        val append = AppendMediaUseCase(SequentialIds())
        val song = assetOf("m", kind = MediaKind.AUDIO, durationMs = 10_000)
        val noA2 = append(timelineOf().without(Timeline.A2), song, 0, 4_000, 30)
        assertEquals(listOf("v2", "v1", "a1", "a2"), noA2.timeline.tracks.map { it.id })
        assertEquals("a2", noA2.timeline.clips.single().trackId)
        assertEquals("on A2 as before", Timeline.A2, append(timelineOf(), song, 0, 4_000, 30).timeline.clips.single().trackId)
    }
}
