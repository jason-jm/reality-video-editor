package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Render › Individual clips: one file per clip, each the clip alone with its linked sound. */
class IndividualClipsUseCaseTest {

    private val individual = IndividualClipsUseCase()

    // V1: "Beach" 0–90 (sound "Beach" linked on A1), "City" 90–210 with a dissolve in; V2: "Bars" 30–60 over them; a title
    // on V2 at 120; music on A2 throughout.
    private val beach = UiFixtures.clip("b", start = 0, frames = 90, assetId = "v1", linkId = "L", name = "Beach")
    private val beachSound = UiFixtures.clip("bs", start = 0, frames = 90, trackId = Timeline.A1, assetId = "v1", linkId = "L", name = "Beach")
    private val city = UiFixtures.clip("c", start = 90, frames = 120, assetId = "v1", name = "City")
        .copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15))
    private val bars = UiFixtures.clip("o", start = 30, frames = 30, trackId = Timeline.V2, assetId = "v1", name = "Bars")
    private val music = UiFixtures.clip("m", start = 0, frames = 210, trackId = Timeline.A2, assetId = "a1", name = "Song")

    private fun doc(marks: TimelineMarks = TimelineMarks()) = UiFixtures.document(
        name = "Trip", media = listOf(UiFixtures.video("v1"), UiFixtures.audio("a1")),
        clips = listOf(beach, beachSound, city, bars, music), markers = listOf(Marker("k", 40, "Peak")),
    ).let { it.copy(timeline = it.timeline.copy(marks = marks)) }

    @Test
    fun `an individual render needs the selected clip and leaves off linked media from disabled tracks`() {
        val base = doc()
        fun disableTrack(id: String) = base.copy(timeline = base.timeline.copy(
            tracks = base.timeline.tracks.map { if (it.id == id) it.copy(enabled = false) else it },
        ))
        assertNull(individual.solo(disableTrack(Timeline.V1), beach.id))
        assertEquals(listOf(beach.id), individual.solo(disableTrack(Timeline.A1), beach.id)?.timeline?.clips?.map { it.id })
        val disabled = base.copy(timeline = base.timeline.copy(clips = base.timeline.clips.map {
            if (it.id == beach.id) it.copy(enabled = false) else it
        }))
        assertNull("the linked sound cannot stand in for its disabled picture", individual.solo(disabled, beach.id))
    }

    @Test
    fun `every picture in timeline order gets a file, with In to Out those reaching into it, the mix alone renders sounds`() {
        val settings = doc().render
        assertEquals(listOf("b", "o", "c"), individual.clips(doc(), settings).map { it.id })
        val marked = doc(TimelineMarks(100, 150))
        assertEquals("a clip reaching into the range", listOf("c"), individual.clips(marked, settings.copy(range = RenderRange.IN_TO_OUT)).map { it.id })
        assertEquals(listOf("b", "o", "c"), individual.clips(marked, settings).map { it.id })
        assertEquals("audio-only: the sound clips", listOf("bs", "m"), individual.clips(doc(), settings.copy(includeVideo = false)).map { it.id })
        // A clip switched off, or on a track switched off, stays out.
        val off = doc().let { d -> d.copy(timeline = d.timeline.replaceClip(bars.copy(enabled = false))) }
        assertEquals(listOf("b", "c"), individual.clips(off, settings).map { it.id })
    }

    @Test
    fun `a clip's file holds the clip alone with its linked sound, from the timeline's start, no transition or markers`() {
        val beachOnly = individual.solo(doc(), "b")!!
        assertEquals(listOf("b" to 0, "bs" to 0), beachOnly.timeline.clips.map { it.id to it.start })
        assertTrue(beachOnly.timeline.markers.isEmpty())
        assertEquals(90, beachOnly.durationFrames)
        val cityOnly = individual.solo(doc(TimelineMarks(100, 150)), "c")!!
        assertEquals(listOf("c" to 0), cityOnly.timeline.clips.map { it.id to it.start })
        assertNull("the dissolve into it needs the clip before", cityOnly.timeline.clips.single().transitionIn)
        assertEquals(TimelineMarks(), cityOnly.timeline.marks)
        assertEquals(120, cityOnly.durationFrames)
        assertNull("a clip no longer there", individual.solo(doc(), "gone"))
    }

    @Test
    fun `queued, each clip is a job named after it, and the project keeps it`() {
        val d = doc().let { it.copy(render = it.render.copy(fileName = "Trip", individualClips = true)) }
        val jobs = RenderQueueUseCase(SequentialIds()).enqueueIndividual(d, individual)
        assertEquals(listOf("Trip_Beach.mp4", "Trip_Bars.mp4", "Trip_City.mp4"), jobs.map { it.fileName })
        assertEquals(listOf("b", "o", "c"), jobs.map { it.clipId })
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(d.copy(jobs = jobs)))
        assertEquals(jobs.map { it.clipId }, reread.jobs.map { it.clipId })
        assertTrue(reread.render.individualClips)
        assertNull("a timeline job has no clip", ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc().copy(jobs = RenderQueueUseCase(SequentialIds()).enqueue(emptyList(), doc().render)))).jobs.single().clipId)
    }
}
