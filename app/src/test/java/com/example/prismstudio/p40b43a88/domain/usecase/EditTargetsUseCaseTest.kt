package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's destination controls: where the source viewer's edits put the picture and the sound. */
class EditTargetsUseCaseTest {

    private val targets = EditTargetsUseCase()
    private val timeline = timelineOf()

    private fun rejected(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `the picture goes to a video track, the sound to an audio track or automatically`() {
        val v2 = targets.video(timeline, Timeline.V2)
        assertEquals(EditTargets(video = Timeline.V2), v2.timeline.targets)
        assertEquals("画面将落在 V2", v2.message)
        val a2 = targets.audio(v2.timeline, Timeline.A2)
        assertEquals(EditTargets(Timeline.V2, Timeline.A2), a2.timeline.targets)
        assertEquals("声音将落在 A2", a2.message)
        assertEquals(EditTargets(Timeline.V2, null), targets.audio(a2.timeline, null).timeline.targets)
        assertEquals("只能选择视频轨道作为画面目标", rejected { targets.video(timeline, Timeline.A1) })
        assertEquals("只能选择音频轨道作为声音目标", rejected { targets.audio(timeline, Timeline.V1) })
    }

    @Test
    fun `a track header patches its track, and the sound target tapped again hands sound back to automatic`() {
        val a1 = targets.toggle(timeline, Timeline.A1)
        assertEquals(Timeline.A1, a1.timeline.targets.audio)
        val auto = targets.toggle(a1.timeline, Timeline.A1)
        assertNull(auto.timeline.targets.audio)
        assertEquals(EditTargetsUseCase.AUTOMATIC, auto.message)
        assertEquals(Timeline.V2, targets.toggle(timeline, Timeline.V2).timeline.targets.video)
        assertEquals("the picture target stays on a second tap", Timeline.V1, targets.toggle(timeline, Timeline.V1).timeline.targets.video)
        val withSubtitles = timeline.withTrack(timeline.nextTrack(TrackKind.SUBTITLE))
        assertEquals("字幕轨道不能作为编辑目标", rejected { targets.toggle(withSubtitles, "st1") })
        assertEquals("轨道不存在", rejected { targets.toggle(timeline, "v9") })
    }

    @Test
    fun `a destination on a deleted track goes back to its default`() {
        val tracks = TracksUseCase()
        val doc = projectOf(timeline = timeline.copy(targets = EditTargets(Timeline.V2, Timeline.A2)))
        val removed = tracks.remove(tracks.remove(doc, Timeline.V2).doc, Timeline.A2).doc
        assertEquals(EditTargets(), removed.timeline.targets)
        // Ids of tracks that are gone some other way (an undo took the track away) read as the defaults.
        assertEquals(EditTargets(), timeline.copy(targets = EditTargets("v3", "a3")).activeTargets)
        assertEquals("a video id is no sound target", EditTargets(), timeline.copy(targets = EditTargets(Timeline.A1, Timeline.V2)).activeTargets)
    }
}
