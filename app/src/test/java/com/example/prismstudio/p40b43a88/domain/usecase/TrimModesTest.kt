package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Jog-wheel slip and roll: the timeline keeps its length while content or cut points move. */
class TrimModesTest {

    private val trims = TrimModesUseCase()
    private val video = assetOf("v", durationMs = 20_000)

    @Test
    fun `slip shows another part of the source in the same place, with its linked sound`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 30, 90, assetId = "v", sourceInMs = 4_000, linkId = "L"),
                clipOf("As", 30, 90, track = Timeline.A1, assetId = "v", sourceInMs = 4_000, linkId = "L"),
            ),
            media = listOf(video),
        )

        val later = trims.slip(doc, "A", 30).timeline
        assertEquals(5_000, later.clip("A")!!.sourceInMs)
        assertEquals(5_000, later.clip("As")!!.sourceInMs)
        assertEquals("the clip stays put", 30, later.clip("A")!!.start)
        assertEquals("滑移 +30 帧", trims.slip(doc, "A", 30).message)

        // 4 s of media before the in point (120 frames), 13 s after the out point (390 frames).
        assertEquals(0, trims.slip(doc, "A", -1_000).timeline.clip("A")!!.sourceInMs)
        assertEquals(17_000, trims.slip(doc, "A", 1_000).timeline.clip("A")!!.sourceInMs)
        assertThrows(EditRejected::class.java) { trims.slip(projectOf(timeline = trims.slip(doc, "A", -1_000).timeline, media = listOf(video)), "A", -1) }
    }

    @Test
    fun `stills and titles have nothing to slip`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("S", 0, 60, assetId = "img"), titleClipOf("T", 0, 60)),
            media = listOf(assetOf("img", kind = MediaKind.IMAGE)),
        )

        assertThrows(EditRejected::class.java) { trims.slip(doc, "S", 5) }
        assertThrows(EditRejected::class.java) { trims.slip(doc, "T", 5) }
    }

    @Test
    fun `roll moves the cut between a clip and the next one, keeping everything after it in place`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", sourceInMs = 0),
                clipOf("B", 90, 90, assetId = "v", sourceInMs = 3_000),
                clipOf("C", 180, 30, assetId = "v"),
            ),
            media = listOf(video),
        )

        val later = trims.roll(doc, "A", 30)
        assertEquals(120, later.timeline.clip("A")!!.durationFrames)
        assertEquals(listOf<Any>(120, 60, 4_000L), later.timeline.clip("B")!!.let { listOf(it.start, it.durationFrames, it.sourceInMs) })
        assertEquals("C does not move", 180, later.timeline.clip("C")!!.start)
        assertEquals("滚动剪辑点 +30 帧", later.message)

        // B has 3 s (90 frames) of media before its in point, A must keep 5 frames: −85 at most.
        val earlier = trims.roll(doc, "A", -200).timeline
        assertEquals(5, earlier.clip("A")!!.durationFrames)
        assertEquals(listOf<Any>(5, 175, 167L), earlier.clip("B")!!.let { listOf(it.start, it.durationFrames, it.sourceInMs) })
    }

    @Test
    fun `linked sound rolls with its picture, and a clip without a neighbour cannot roll`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", linkId = "LA"),
                clipOf("As", 0, 90, track = Timeline.A1, assetId = "v", linkId = "LA"),
                clipOf("B", 90, 90, assetId = "v", sourceInMs = 6_000, linkId = "LB"),
                clipOf("Bs", 90, 90, track = Timeline.A1, assetId = "v", sourceInMs = 6_000, linkId = "LB"),
            ),
            media = listOf(video),
        )

        val rolled = trims.roll(doc, "As", 15).timeline
        listOf("A", "As").forEach { assertEquals(it, 105, rolled.clip(it)!!.durationFrames) }
        listOf("B", "Bs").forEach { assertEquals(it, 105, rolled.clip(it)!!.start) }
        assertThrows(EditRejected::class.java) { trims.roll(doc, "B", 10) }
    }

    @Test
    fun `an unlinked clip on a partner's track limits the roll`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", linkId = "LA"),
                clipOf("As", 0, 90, track = Timeline.A1, assetId = "v", linkId = "LA"),
                clipOf("B", 90, 90, assetId = "v", sourceInMs = 6_000),
                clipOf("X", 100, 50, track = Timeline.A1, assetId = "v"),
            ),
            media = listOf(video),
        )

        assertEquals("As may only grow until X at 100", 100, trims.roll(doc, "A", 40).timeline.clip("A")!!.end)
    }

    @Test
    fun `rolling there and back lands on the original cut although in points are whole milliseconds`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 90, assetId = "v"), clipOf("B", 90, 90, assetId = "v")),
            media = listOf(video),
        )
        var timeline = trims.roll(doc, "A", 2).timeline
        timeline = trims.roll(projectOf(timeline = timeline, media = listOf(video)), "A", -1).timeline
        timeline = trims.roll(projectOf(timeline = timeline, media = listOf(video)), "A", -1).timeline

        assertEquals(listOf<Any>(90, 0L), timeline.clip("B")!!.let { listOf(it.start, it.sourceInMs) })
    }

    @Test
    fun `keyframes stay with the picture through a roll`() {
        val keyed = clipOf("B", 90, 90, assetId = "v", sourceInMs = 6_000)
            .copy(keyframes = listOf(TransformKeyframe(0, ClipTransform(scalePercent = 100)), TransformKeyframe(60, ClipTransform(scalePercent = 160))))
        val doc = projectOf(timeline = timelineOf(clipOf("A", 0, 90, assetId = "v"), keyed), media = listOf(video))

        val rolled = trims.roll(doc, "A", 30).timeline.clip("B")!!

        (0 until 60).forEach { f -> assertEquals("frame $f", keyed.transformAt(30 + f).scalePercent.toFloat(), rolled.transformAt(f).scalePercent.toFloat(), 1f) }
    }

    @Test
    fun `slide moves a clip between its neighbours, which give and take, and nothing else moves`() {
        // A 0–90 (media to 20 s), B 90–150 (the slider, linked sound Bs), C 150–240 (in at 5 s), D 240–270.
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", linkId = "LA"),
                clipOf("As", 0, 90, track = Timeline.A1, assetId = "v", linkId = "LA"),
                clipOf("B", 90, 60, assetId = "v", sourceInMs = 2_000, linkId = "LB"),
                clipOf("Bs", 90, 60, track = Timeline.A1, assetId = "v", sourceInMs = 2_000, linkId = "LB"),
                clipOf("C", 150, 90, assetId = "v", sourceInMs = 5_000, linkId = "LC"),
                clipOf("Cs", 150, 90, track = Timeline.A1, assetId = "v", sourceInMs = 5_000, linkId = "LC"),
                clipOf("D", 240, 30, assetId = "v"),
            ),
            media = listOf(video),
        )

        val later = trims.slide(doc, "B", 15)
        val t = later.timeline
        assertEquals("滑动 +15 帧", later.message)
        listOf("B", "Bs").forEach { assertEquals(it, listOf<Any>(105, 60, 2_000L), t.clip(it)!!.let { c -> listOf(c.start, c.durationFrames, c.sourceInMs) }) }
        listOf("A", "As").forEach { assertEquals(it, 105, t.clip(it)!!.durationFrames) }
        listOf("C", "Cs").forEach { assertEquals(it, listOf<Any>(165, 75, 5_500L), t.clip(it)!!.let { c -> listOf(c.start, c.durationFrames, c.sourceInMs) }) }
        assertEquals("D stays", 240, t.clip("D")!!.start)
        assertEquals("the timeline keeps its length", doc.timeline.durationFrames, t.durationFrames)

        // Later: C must keep 5 frames (85). Earlier: A must keep 5 frames, C has 5 s (150 frames) before its in: −85.
        assertEquals(175, trims.slide(doc, "B", 500).timeline.clip("B")!!.start)
        val earliest = trims.slide(doc, "Bs", -500).timeline
        assertEquals(5, earliest.clip("A")!!.durationFrames)
        assertEquals(listOf<Any>(65, 175, 2_167L), earliest.clip("C")!!.let { listOf(it.start, it.durationFrames, it.sourceInMs) })
    }

    @Test
    fun `a gap beside the clip takes up the slide, the media of a neighbour limits it`() {
        val doc = projectOf(
            timeline = timelineOf(
                // A ends its media at 20 s: 10 frames to spare after its out point.
                clipOf("A", 0, 90, assetId = "v", sourceInMs = 16_667),
                clipOf("B", 90, 30, assetId = "v"),
                clipOf("E", 150, 30, assetId = "v"),
            ),
            media = listOf(video),
        )
        // B's right is a 30-frame gap before E; A has 10 frames of media left.
        assertEquals(100, trims.slide(doc, "B", 40).timeline.clip("B")!!.start)
        val slid = trims.slide(doc, "B", 10).timeline
        assertEquals("the gap before E shrinks to 20 frames", 20, slid.clip("E")!!.start - slid.clip("B")!!.end)
        val isolated = projectOf(timeline = timelineOf(clipOf("E", 150, 30, assetId = "v")), media = listOf(video))
        assertEquals("a lone clip slides back to the start", 0, trims.slide(isolated, "E", -400).timeline.clip("E")!!.start)
        val locked = projectOf(timeline = doc.timeline.copy(tracks = doc.timeline.tracks.map { it.copy(locked = it.id == Timeline.V1) }), media = listOf(video))
        assertThrows(EditRejected::class.java) { trims.slide(locked, "B", 5) }
        val pinned = projectOf(timeline = timelineOf(clipOf("A", 0, 5, assetId = "v"), clipOf("B", 5, 30, assetId = "v"), clipOf("C", 35, 5, assetId = "v")), media = listOf(video))
        assertEquals("片段不能再往前滑了", assertThrows(EditRejected::class.java) { trims.slide(pinned, "B", -1) }.message)
    }

    @Test
    fun `a neighbour's linked sound follows its edit point, as far as its own track allows`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", linkId = "LA"),
                clipOf("As", 0, 90, track = Timeline.A1, assetId = "v", linkId = "LA"),
                // The slider has no sound of its own; a sound effect sits 20 frames after As on A1.
                clipOf("S", 90, 60, assetId = "img"),
                clipOf("Fx", 110, 30, track = Timeline.A1, assetId = "v"),
            ),
            media = listOf(video, assetOf("img", kind = MediaKind.IMAGE)),
        )
        val slid = trims.slide(doc, "S", 50).timeline
        assertEquals("As may only grow into the 20 frames before Fx", 110, slid.clip("A")!!.end)
        assertEquals(110, slid.clip("As")!!.end)
        assertEquals(110, slid.clip("S")!!.start)
    }

    @Test
    fun `rolls and slides keep reversed clips' frames in their places`() {
        // R1 0–90 and R2 90–180 play 2–5 s and 8–11 s of the source backwards; S slides between them.
        val r1 = clipOf("R1", 0, 90, assetId = "v", sourceInMs = 2_000).copy(reverse = true)
        val r2 = clipOf("R2", 90, 90, assetId = "v", sourceInMs = 8_000).copy(reverse = true)
        val doc = projectOf(timeline = timelineOf(r1, r2), media = listOf(video))
        // The source frame shown (to the nearest frame: positions are whole milliseconds, a third of a ms either way).
        val shown = { t: Timeline, id: String, frame: Int -> (t.clip(id)!!.sourceAt(frame, 30, video) * 30 + 500) / 1000 }

        val rolled = trims.roll(doc, "R1", 15).timeline
        assertEquals(listOf(0 to 105, 105 to 180), listOf("R1", "R2").map { rolled.clip(it)!!.let { c -> c.start to c.end } })
        for (f in 0 until 90) assertEquals("R1 frame $f", shown(doc.timeline, "R1", f), shown(rolled, "R1", f))
        for (f in 105 until 180) assertEquals("R2 frame $f", shown(doc.timeline, "R2", f), shown(rolled, "R2", f))
        assertEquals("R2 keeps its in point", 8_000L, rolled.clip("R2")!!.sourceInMs)
        // R1 can grow only by the 2 s (60 frames) before its in point.
        assertEquals(150, trims.roll(doc, "R1", 500).timeline.clip("R1")!!.end)
        // R2 can start earlier only by the 4 s after its window... but R1 must keep 5 frames: −85.
        val back = trims.roll(doc, "R1", -40).timeline
        for (f in 90 until 180) assertEquals("R2 frame $f", shown(doc.timeline, "R2", f), shown(back, "R2", f))

        val slid = trims.slide(projectOf(timeline = timelineOf(r1, clipOf("S", 90, 30, assetId = "v"), r2.copy(start = 120)), media = listOf(video)), "S", 10).timeline
        assertEquals(listOf(0 to 100, 100 to 130, 130 to 210), listOf("R1", "S", "R2").map { slid.clip(it)!!.let { c -> c.start to c.end } })
        for (f in 0 until 90) assertEquals("R1 frame $f", shown(timelineOf(r1), "R1", f), shown(slid, "R1", f))
        for (f in 130 until 210) assertEquals("R2 frame $f", shown(timelineOf(r2.copy(start = 120)), "R2", f), shown(slid, "R2", f))
    }

    private fun Timeline.clip(id: String) = clips.firstOrNull { it.id == id }
}
