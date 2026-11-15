package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectDocumentTest {

    private val camColours = PosterColors(0xFF101010, 0xFF202020, 0xFF303030)
    private val cam = assetOf("cam", durationMs = 8_000, poster = camColours)
    private val bRoll = assetOf("broll", durationMs = 6_000)
    private val still = assetOf("still", kind = MediaKind.IMAGE, name = "still.png")
    private val song = assetOf("song", kind = MediaKind.AUDIO, name = "song.m4a", poster = PosterColors(1, 2, 3))

    @Test
    fun `the summary counts picture clips and reports length format and poster colours`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("V", 0, 90, assetId = "cam", linkId = "L"),
                clipOf("S", 0, 90, track = Timeline.A1, assetId = "cam", linkId = "L"),
                titleClipOf("T", 10, 30),
            ),
            media = listOf(cam),
            fps = 25,
            id = "p-7",
            name = "Trip",
            modifiedAtMillis = 42L,
        )

        val summary = doc.summary()

        assertEquals("p-7", summary.id)
        assertEquals("Trip", summary.name)
        assertEquals(ProjectFormat(1920, 1080, 25), summary.format)
        assertEquals(90, summary.durationFrames)
        assertEquals(90, doc.durationFrames)
        assertEquals(2, summary.clipCount)
        assertEquals(42L, summary.modifiedAtMillis)
        assertEquals(camColours, summary.poster)
    }

    @Test
    fun `the poster frame comes from the earliest video clip half a second into its source`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("later", 100, 50, assetId = "broll"),
                clipOf("first", 0, 100, assetId = "cam", sourceInMs = 2_000),
                clipOf("sound", 0, 100, track = Timeline.A1, assetId = "song"),
                titleClipOf("T", 0, 30),
            ),
            media = listOf(song, bRoll, cam),
        )

        assertEquals(PosterSource(cam.uri, MediaKind.VIDEO, 2_500), doc.summary().posterSource)

        val stillFirst = projectOf(timeline = timelineOf(clipOf("img", 0, 150, assetId = "still")), media = listOf(still))
        assertEquals(PosterSource(still.uri, MediaKind.IMAGE, 500), stillFirst.summary().posterSource)
    }

    @Test
    fun `without a usable clip the poster frame falls back to the first picture asset`() {
        assertEquals(PosterSource(cam.uri, MediaKind.VIDEO, 1_000), projectOf(media = listOf(song, cam)).summary().posterSource)

        val shortClip = assetOf("short", durationMs = 1_200)
        assertEquals(PosterSource(shortClip.uri, MediaKind.VIDEO, 600), projectOf(media = listOf(shortClip)).summary().posterSource)

        val dangling = projectOf(timeline = timelineOf(clipOf("V", 0, 50, assetId = "gone")), media = listOf(song, cam))
        assertEquals(PosterSource(cam.uri, MediaKind.VIDEO, 1_000), dangling.summary().posterSource)

        assertNull(projectOf(media = listOf(song)).summary().posterSource)
    }

    @Test
    fun `poster colours fall back to the first asset and then to neutral`() {
        assertEquals(PosterColors(1, 2, 3), projectOf(media = listOf(song, cam)).summary().poster)
        assertEquals(PosterColors.NEUTRAL, projectOf().summary().poster)
    }

    @Test
    fun `asset lookup finds media by id`() {
        val doc = projectOf(media = listOf(cam, song))

        assertEquals(song, doc.asset("song"))
        assertNull(doc.asset("ghost"))
        assertNull(doc.asset(null))
    }
}
