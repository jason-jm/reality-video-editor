package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import com.example.prismstudio.p40b43a88.domain.usecase.MulticamUseCase.SwitchMode
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Resolve's multicam clips: made from camera clips in step, played one angle at a time, cut between, flattened. */
class MulticamUseCaseTest {

    private val multicams = MulticamUseCase(SequentialIds())

    // V3: C 15–315 (no sound) · V2: B 30–330 with Bb on A2 · V1: A 0–300 with Aa on A1. 30 fps.
    private val tracks = listOf(Track("v3", "V3", TrackKind.VIDEO)) + Timeline.defaultTracks()
    private val cams = Timeline(
        tracks,
        listOf(
            clipOf("A", 0, 300, assetId = "camA", sourceInMs = 2000, linkId = "LA"),
            clipOf("Aa", 0, 300, track = Timeline.A1, assetId = "camA", sourceInMs = 2000, linkId = "LA"),
            clipOf("B", 30, 300, track = Timeline.V2, assetId = "camB", linkId = "LB"),
            clipOf("Bb", 30, 300, track = Timeline.A2, assetId = "camB", linkId = "LB"),
            clipOf("C", 15, 300, track = "v3", assetId = "camC", sourceInMs = 500),
        ),
        emptyList(),
    )
    private val doc = projectOf(timeline = cams, media = listOf(assetOf("camA", durationMs = 20_000), assetOf("camB", durationMs = 20_000), assetOf("camC", durationMs = 20_000)))

    private fun rejection(block: () -> Unit): String? = try {
        block(); fail("expected a refusal"); null
    } catch (e: EditRejected) {
        e.message
    }

    /** The multicam clip made of A, B and C: the document and its picture and sound parts' ids. */
    private fun made(): Triple<ProjectDocument, String, String> {
        val d = multicams.create(doc, listOf("A", "B", "C")).doc
        return Triple(d, d.timeline.clips.single { it.kind == ClipKind.VIDEO }.id, d.timeline.clips.single { it.kind == ClipKind.AUDIO }.id)
    }

    /** What plays on [track] of [d]: (source clip, start, end, source ms) for each flattened clip there. */
    private fun plays(d: ProjectDocument, track: String) =
        CompoundClipUseCase.expanded(d).timeline.clipsOn(track).map { listOf(it.id.substringAfter('/'), it.start, it.end, it.sourceInMs) }

    @Test
    fun `camera clips become the angles of one multicam clip in their place, angle 1 the lowest track's`() {
        val result = multicams.create(doc, listOf("A", "B", "C"))
        val d = result.doc
        assertEquals("已用 3 个机位新建多机位片段“多机位 1”：在检查器里点按机位即可切换", result.message)
        val id = d.multicamIds.single()
        assertTrue("a compound clip underneath", id in d.compoundIds)
        val inside = d.storedTimeline(id)!!.timeline
        assertEquals(listOf("V3", "V2", "V1", "A1", "A2", "A3"), inside.tracks.map { it.name })
        assertEquals(listOf("A" to 0, "B" to 30, "C" to 15), listOf("v1", "v2", "v3").map { tr -> inside.clipsOn(tr).single().let { it.id to it.start } })
        assertEquals(listOf("Aa", "Bb"), listOf("a1", "a2").map { inside.clipsOn(it).single().id })
        // In their place: the picture on V1 and the sound on A1, linked, over all of them (0–330), playing angle 1.
        val picture = d.timeline.clips.single { it.kind == ClipKind.VIDEO }
        val sound = d.timeline.clips.single { it.kind == ClipKind.AUDIO }
        assertEquals(listOf(Timeline.V1, 0, 330, 0), listOf(picture.trackId, picture.start, picture.end, picture.angle))
        assertEquals(listOf(Timeline.A1, 0, 330), listOf(sound.trackId, sound.start, sound.end))
        assertEquals(picture.linkId, sound.linkId)
        assertEquals(listOf("A", "B", "C"), multicams.angles(d, picture).map { it.name })
        assertEquals(listOf(true, true, false), multicams.angles(d, picture).map { it.hasSound })
        assertEquals("多机位片段", CompoundClipUseCase(SequentialIds()).kindLabel(d, id))
        assertEquals("3 个机位：按轨道从下往上依次为机位 1、2…，保持现在的对齐", multicams.note(cams, listOf("A", "B", "C")))
    }

    @Test
    fun `a multicam clip plays only its angle, on its own track`() {
        val (d, _, _) = made()
        assertEquals(listOf(listOf("A", 0, 300, 2000L)), plays(d, Timeline.V1))
        assertEquals(listOf(listOf("Aa", 0, 300, 2000L)), plays(d, Timeline.A1))
        assertTrue("the other angles are not played", CompoundClipUseCase.expanded(d).timeline.clips.none { it.id.endsWith("/B") || it.id.endsWith("/C") })
    }

    @Test
    fun `a tap cuts at the playhead - picture and sound together, then picture alone, then sound alone`() {
        val (d0, pictureId, _) = made()
        val both = multicams.switch(d0, pictureId, 1, 90, SwitchMode.BOTH, cut = true)
        assertEquals("在 00:00:03:00 把画面和声音切到机位 2（B）", both.message)
        val d1 = both.doc
        val pictures = d1.timeline.clips.filter { it.kind == ClipKind.VIDEO }.sortedBy { it.start }
        val sounds = d1.timeline.clips.filter { it.kind == ClipKind.AUDIO }.sortedBy { it.start }
        assertEquals(listOf(0 to 0, 90 to 1), pictures.map { it.start to it.angle })
        assertEquals(listOf(0 to 0, 90 to 1), sounds.map { it.start to it.angle })
        assertEquals("cut together, linked piece by piece", pictures.map { it.linkId }, sounds.map { it.linkId })
        assertTrue(pictures[0].linkId != pictures[1].linkId)
        assertEquals(pictures[1].id, both.selectedClipId)
        // B from its own frame 60 (2 s in) on at the cut.
        assertEquals(listOf(listOf("A", 0, 90, 2000L), listOf("B", 90, 330, 2000L)), plays(d1, Timeline.V1))
        assertEquals(listOf(listOf("Aa", 0, 90, 2000L), listOf("Bb", 90, 330, 2000L)), plays(d1, Timeline.A1))
        // Picture only at 200: the picture cuts to C, the sound plays on whole — and the two are no longer linked.
        val d2 = multicams.switch(d1, pictures[1].id, 2, 200, SwitchMode.PICTURE, cut = true).doc
        assertEquals(listOf(0 to 0, 90 to 1, 200 to 2), d2.timeline.clips.filter { it.kind == ClipKind.VIDEO }.sortedBy { it.start }.map { it.start to it.angle })
        val sounds2 = d2.timeline.clips.filter { it.kind == ClipKind.AUDIO }.sortedBy { it.start }
        assertEquals(listOf(0 to 90, 90 to 330), sounds2.map { it.start to it.end })
        assertNull(sounds2[1].linkId)
        assertTrue(d2.timeline.clips.filter { it.start >= 90 }.all { it.linkId == null })
        assertEquals("C's camera clip ends at 315: nothing after it", listOf("C", 200, 315, 6666L), plays(d2, Timeline.V1).last())
        // Sound alone at 260, from the picture part there: the sound part at the playhead is found by time.
        val c = d2.timeline.clips.single { it.kind == ClipKind.VIDEO && it.start == 200 }
        val d3 = multicams.switch(d2, c.id, 0, 260, SwitchMode.SOUND, cut = true).doc
        assertEquals(listOf(0 to 0, 90 to 1, 260 to 0), d3.timeline.clips.filter { it.kind == ClipKind.AUDIO }.sortedBy { it.start }.map { it.start to it.angle })
        assertEquals("the picture is left alone", 3, d3.timeline.clips.count { it.kind == ClipKind.VIDEO })
    }

    @Test
    fun `switching the whole segment cuts nothing, and the angle playing already is refused`() {
        val (d0, pictureId, soundId) = made()
        val whole = multicams.switch(d0, soundId, 1, 120, SwitchMode.BOTH, cut = false)
        assertEquals("整段画面和声音切到机位 2（B）", whole.message)
        assertEquals(listOf(1, 1), whole.doc.timeline.clips.map { it.angle })
        assertEquals("已经是机位 2", rejection { multicams.switch(whole.doc, pictureId, 1, 120, SwitchMode.BOTH, cut = true) })
        // At the clip's first frame a tap switches the whole clip.
        assertEquals(2, multicams.switch(d0, pictureId, 2, 0, SwitchMode.PICTURE, cut = true).doc.timeline.clip(pictureId)!!.angle)
        assertEquals("没有机位 4", rejection { multicams.switch(d0, pictureId, 3, 50, SwitchMode.BOTH, cut = true) })
    }

    @Test
    fun `each angle's frame at the playhead, none where an angle has not started`() {
        val (d, pictureId, _) = made()
        val picture = d.timeline.clip(pictureId)!!
        assertEquals(
            listOf(MulticamUseCase.AngleSource("camA", 5333), MulticamUseCase.AngleSource("camB", 2333), MulticamUseCase.AngleSource("camC", 3333)),
            multicams.sources(d, picture, 100),
        )
        assertEquals(listOf(MulticamUseCase.AngleSource("camA", 2333), null, null), multicams.sources(d, picture, 10))
        assertEquals(0 to 0, multicams.playing(d, picture, 100))
    }

    @Test
    fun `flattening puts back the angle each part plays`() {
        val (d0, pictureId, _) = made()
        val cut = multicams.switch(d0, pictureId, 1, 90, SwitchMode.BOTH, cut = true)
        val flat = CompoundClipUseCase(SequentialIds()).decompose(cut.doc, cut.selectedClipId!!)
        assertEquals("已展平“多机位 1”：换成所选机位的 2 个片段", flat.message)
        val placed = flat.doc.timeline.clips.filterNot { it.isCompound }
        assertEquals(setOf("camB"), placed.map { it.assetId }.toSet())
        assertEquals(setOf(Timeline.V1, Timeline.A1), placed.map { it.trackId }.toSet())
        assertEquals(listOf(90, 90), placed.map { it.start })
    }

    @Test
    fun `what cannot become a multicam clip, and the angles survive a round trip`() {
        assertEquals("选择至少两个画面片段：每个是一个机位（先对齐）", multicams.unavailable(cams, listOf("A")))
        val loose = cams.copy(clips = cams.clips + clipOf("S", 0, 30, track = Timeline.A2, assetId = "camC"))
        assertEquals("单独的声音片段不能做成机位：先把它链接到一个画面", multicams.unavailable(loose, listOf("A", "B", "S")))
        val titled = cams.copy(clips = cams.clips + titleClipOf("T", 0, 30))
        assertEquals("标题和字幕不能做成机位", multicams.unavailable(titled, listOf("A", "T")))
        val (d0, pictureId, _) = made()
        val d1 = multicams.switch(d0, pictureId, 2, 90, SwitchMode.PICTURE, cut = true).doc
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(d1))
        assertEquals(d1.multicamIds, reread.multicamIds)
        assertEquals(d1.timeline.clips.map { it.angle }, reread.timeline.clips.map { it.angle })
        assertEquals("多机位 · 机位 3", EditIndexUseCase().rows(d1).first { it.kind == ClipKind.VIDEO && it.recordIn == 90 }.source)
    }
}
