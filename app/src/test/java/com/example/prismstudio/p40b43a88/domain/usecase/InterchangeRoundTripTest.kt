package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release round R20: the projects kept in test resources (NightRun and six from earlier iterations — several tracks,
 * dissolves, reversed clips, titles, subtitles) go out as FCPXML and EDL and come back with every clip those formats hold
 * where it was: track, start, length, media, source in point, speed, reverse, on/off. What a format cannot hold is what
 * its export listed. Reversed clips used to come back a frame late (in points rounded up: 8267 ms for 8266).
 */
class InterchangeRoundTripTest {
    private fun fixtures(): List<Pair<String, ProjectDocument>> =
        File(javaClass.getResource("/legacy-projects")!!.toURI()).listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.name }
            .map { it.name to ProjectJsonCodec.decode(it.readText()) }

    private fun key(c: Clip) = listOf(c.trackId.lowercase(), c.kind, c.start, c.durationFrames, c.assetId, c.sourceInMs, c.speed, c.reverse, c.enabled).toString()

    @Test
    fun `FCPXML - every clip but the subtitles comes back where it was`() {
        for ((name, doc) in fixtures()) {
            val exported = FcpxmlExportUseCase()(doc)
            val back = FcpxmlImportUseCase(SequentialIds())(doc, exported.text, "x.fcpxml").doc.timeline.clips.map(::key)
            val subtitles = doc.timeline.clips.filter { doc.timeline.track(it.trackId)?.kind == TrackKind.SUBTITLE }
            assertEquals(name, doc.timeline.clips.filterNot { it in subtitles }.map(::key).sorted(), back.sorted())
            if (subtitles.isNotEmpty()) assertTrue("$name: ${exported.left}", exported.left.any { "字幕" in it })
        }
    }

    @Test
    fun `EDL - V1, A1 and A2 come back where they were, other tracks listed as left out`() {
        val cmx = setOf("v1", "a1", "a2")
        for ((name, doc) in fixtures()) {
            val exported = EdlExportUseCase()(doc)
            val back = EdlImportUseCase(SequentialIds())(doc, exported.text, "x.edl").doc.timeline.clips.map(::key)
            assertEquals(name, doc.timeline.clips.filter { it.trackId.lowercase() in cmx }.map(::key).sorted(), back.sorted())
            val others = doc.timeline.clips.map { it.trackId.uppercase() }.filter { it.lowercase() !in cmx }.toSet()
            others.forEach { track -> assertTrue("$name: $track in ${exported.left}", exported.left.any { it.startsWith(track) }) }
        }
    }
}
