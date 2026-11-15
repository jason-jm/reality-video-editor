package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateProjectUseCaseTest {

    private var now = 1_700_000_000_000L
    private val create = CreateProjectUseCase(SequentialIds()) { now }

    @Test
    fun `a new project is empty with four default tracks a neutral mixer and the Bilibili preset`() {
        val doc = create("  Summer Trip  ", FormatPreset.UHD_LANDSCAPE, 25)

        assertEquals("p-1", doc.id)
        assertEquals("Summer Trip", doc.name)
        assertEquals(ProjectFormat(3840, 2160, 25), doc.format)
        assertEquals(now, doc.createdAtMillis)
        assertEquals(now, doc.modifiedAtMillis)
        assertTrue(doc.media.isEmpty())
        assertEquals(Timeline.EMPTY, doc.timeline)
        assertEquals(listOf("v2", "v1", "a1", "a2"), doc.timeline.tracks.map { it.id })
        assertEquals(MixerState(listOf(MixerChannel(Timeline.A1), MixerChannel(Timeline.A2))), doc.mixer)
        assertEquals(RenderSettings(fileName = "Summer_Trip", fps = 25), doc.render)
        assertEquals(RenderPreset.BILIBILI_1080, doc.render.preset)
        assertTrue(doc.jobs.isEmpty())
    }

    @Test
    fun `a blank name falls back to the default project name`() {
        val doc = create("   ", FormatPreset.HD_PORTRAIT, 30)

        assertEquals(CreateProjectUseCase.DEFAULT_NAME, doc.name)
        assertEquals(CreateProjectUseCase.DEFAULT_NAME, doc.render.fileName)
        assertEquals(ProjectFormat(1080, 1920, 30), doc.format)
    }

    @Test
    fun `duplicate keeps the edit but gets a new id copy suffix fresh timestamps and an empty render queue`() {
        val source = projectOf(
            timeline = timelineOf(clipOf("A", 0, 90, assetId = "v")),
            media = listOf(assetOf("v")),
            id = "p-source",
            name = "Trip",
            jobs = listOf(RenderJob("j-1", "Trip.mp4", RenderSettings("Trip"))),
        )
        now = 1_800_000_000_000L

        val copy = create.duplicate(source)

        assertEquals("p-1", copy.id)
        assertEquals("Trip 副本", copy.name)
        assertEquals(now, copy.createdAtMillis)
        assertEquals(now, copy.modifiedAtMillis)
        assertTrue(copy.jobs.isEmpty())
        assertEquals(source.copy(id = copy.id, name = copy.name, createdAtMillis = now, modifiedAtMillis = now, jobs = emptyList()), copy)
    }
}
