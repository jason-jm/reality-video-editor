package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.normalizedSections
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's speed ramps: a speed point smoothed so the speed eases from one section's into the next. */
class SpeedRampTest {

    private val fps = 30
    private val retime = RetimeUseCase()
    private val video = assetOf("v", durationMs = 20_000)
    private val half = ClipSpeed.of(PlaybackSpeed.HALF)
    private val sections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(60, half), SpeedSection(30, ClipSpeed.NORMAL))

    /** 1 s at 1×, 2 s at 0.5×, 1 s at 1× from 2 s of source; without ramps, and with both points eased over 1 s. */
    private val sudden = clipOf("V", 0, 120, assetId = "v", sourceInMs = 2_000).copy(speedSections = sections)
    private val eased = sudden.copy(speedSections = sections.mapIndexed { i, s -> if (i == 0) s else s.copy(rampFrames = 30) })

    @Test
    fun `a ramp plays the same source as the sudden change, so only frames inside it move`() {
        // The ramps run over frames 15–45 and 75–105; elsewhere every frame shows what it did.
        (0..15).plus(45..75).plus(105..140).forEach { f ->
            assertEquals("frame $f", sudden.sourceOffsetMs(f, fps), eased.sourceOffsetMs(f, fps), 1e-6)
        }
        assertEquals("the clip plays as much source", sudden.sourceDurationMs(fps), eased.sourceDurationMs(fps))
        // At the slowing point the speed has eased only halfway down, so the picture is behind the sudden change by
        // 0.09375 × 30 frames × 0.5 = 1.40625 frames of source.
        assertEquals((30 - 1.40625) * 1000.0 / fps, eased.sourceOffsetMs(30, fps), 1e-6)
        // Where it speeds up again the picture is ahead: 30 + 60 × 0.5 frames of source, plus the same 1.40625.
        assertEquals((60 + 1.40625) * 1000.0 / fps, eased.sourceOffsetMs(90, fps), 1e-6)
    }

    @Test
    fun `the speed eases smoothly through the ramp and the source only grows`() {
        assertEquals(1f, eased.rampedSpeedAt(15.0), 1e-6f)
        assertEquals(0.75f, eased.rampedSpeedAt(30.0), 1e-6f)
        assertEquals(0.5f, eased.rampedSpeedAt(45.0), 1e-6f)
        assertEquals(0.75f, eased.rampedSpeedAt(90.0), 1e-6f)
        assertEquals("a section's own speed outside the ramps", 0.5f, eased.rampedSpeedAt(60.0), 0f)
        // The source's rate of change is the eased speed, frame by frame, and never falls back.
        var last = eased.sourceOffsetMs(-5.0, fps)
        var at = -5.0
        while (at < 125.0) {
            at += 0.25
            val now = eased.sourceOffsetMs(at, fps)
            assertTrue("grows at $at", now > last)
            val slope = (eased.sourceOffsetMs(at + 1e-4, fps) - eased.sourceOffsetMs(at - 1e-4, fps)) / 2e-4 * fps / 1000.0
            assertEquals("speed at $at", eased.rampedSpeedAt(at).toDouble(), slope, 1e-4)
            last = now
        }
    }

    @Test
    fun `source positions map back to frames through the ramps`() {
        listOf(0.0, 10.5, 20.0, 29.9, 30.0, 37.25, 60.0, 80.0, 90.0, 104.0, 119.0, -6.0, 130.0).forEach { f ->
            assertEquals("frame $f", f, eased.framesAtSourceMs(eased.sourceOffsetMs(f, fps), fps), 1e-6)
        }
        // Very unequal speeds move a moment far from where a sudden change puts it: still found.
        val steep = clipOf("S", 0, 120, assetId = "v").copy(
            speedSections = listOf(SpeedSection(60, ClipSpeed(10f)), SpeedSection(60, ClipSpeed(0.1f), rampFrames = 60)),
        )
        listOf(31.0, 45.0, 60.0, 75.0, 89.0).forEach { f ->
            assertEquals("steep $f", f, steep.framesAtSourceMs(steep.sourceOffsetMs(f, fps), fps), 1e-6)
        }
    }

    @Test
    fun `a ramp keeps within halfway to the next change of speed, and points of one speed do not count`() {
        assertEquals(listOf(30, 30), listOf(eased.rampAt(1), eased.rampAt(2)))
        assertEquals("room: up to the next point, which is 60 frames on, or the clip's start", 60, eased.rampRoom(1))
        assertEquals(60, eased.rampRoom(2))
        val greedy = sudden.copy(speedSections = sections.mapIndexed { i, s -> if (i == 0) s else s.copy(rampFrames = 200) })
        assertEquals("ramps never meet", listOf(60, 60), listOf(greedy.rampAt(1), greedy.rampAt(2)))
        assertEquals(0, eased.rampAt(0))
        // A point between two sections of one speed changes nothing, so it neither ramps nor squeezes its neighbour's ramp.
        val split = eased.copy(
            speedSections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(10, half, 30), SpeedSection(50, half, 12), SpeedSection(30, ClipSpeed.NORMAL, 30)),
        )
        assertEquals(listOf(30, 0, 30), listOf(split.rampAt(1), split.rampAt(2), split.rampAt(3)))
        assertEquals(0, split.rampRoom(2))
        (0..120).forEach { f -> assertEquals("frame $f", eased.sourceOffsetMs(f, fps), split.sourceOffsetMs(f, fps), 1e-6) }
        assertEquals(
            "the first section has no point before it",
            listOf(0, 30),
            normalizedSections(listOf(SpeedSection(10, half, 8), SpeedSection(5, ClipSpeed.NORMAL, 30)), ClipSpeed.NORMAL).second.map { it.rampFrames },
        )
    }

    @Test
    fun `the nearest point is smoothed for the clip and its sound, and nothing else moves`() {
        val doc = projectOf(
            timeline = timelineOf(
                sudden.copy(linkId = "L", keyframes = listOf(TransformKeyframe(40, ClipTransform(scalePercent = 150)))),
                clipOf("A", 0, 120, track = Timeline.A1, assetId = "v", sourceInMs = 2_000, linkId = "L").copy(speedSections = sections),
                clipOf("N", 120, 60, assetId = "v"),
            ),
            media = listOf(video),
        )
        val smoothed = retime.setRamp(doc, "V", 35, 30)
        assertEquals("第 1 个变速点在 1 秒内平滑过渡", smoothed.message)
        val v = smoothed.timeline.clip("V")!!
        assertEquals(listOf(0, 30, 0), v.speedSections.map { it.rampFrames })
        assertEquals("its sound too", listOf(0, 30, 0), smoothed.timeline.clip("A")!!.speedSections.map { it.rampFrames })
        assertEquals(120, v.durationFrames)
        assertEquals(listOf(40), v.keyframes.map { it.frame })
        assertEquals(120, smoothed.timeline.clip("N")!!.start)

        val most = retime.setRamp(doc.copy(timeline = smoothed.timeline), "V", 100, 500)
        assertEquals("as long as there is room", "第 2 个变速点在 2 秒内平滑过渡", most.message)
        assertEquals(listOf(0, 30, 60), most.timeline.clip("V")!!.speedSections.map { it.rampFrames })
        assertEquals("each half within halfway to the other point", listOf(30, 60), most.timeline.clip("V")!!.let { listOf(it.rampAt(1), it.rampAt(2)) })
        val again = retime.setRamp(doc.copy(timeline = smoothed.timeline), "V", 20, 0)
        assertEquals("第 1 个变速点改为立即变速", again.message)
        assertEquals(listOf(0, 0, 0), again.timeline.clip("V")!!.speedSections.map { it.rampFrames })
        assertEquals("0.13", RetimeUseCase.secondsLabel(4, fps))

        fun refusal(block: () -> Unit) = assertThrows(EditRejected::class.java) { block() }.message
        val even = doc.copy(timeline = timelineOf(sudden.copy(speedSections = listOf(SpeedSection(30, half), SpeedSection(30, half)), durationFrames = 60)))
        assertEquals("第 1 个变速点两侧速度相同，不需要平滑过渡", refusal { retime.setRamp(even, "V", 30, 15) })
        assertEquals("片段没有变速点", refusal { retime.setRamp(doc, "N", 30, 15) })
    }

    @Test
    fun `a ramp stays through the clip's other speed edits`() {
        val doc = projectOf(timeline = timelineOf(eased, clipOf("N", 120, 60, assetId = "v")), media = listOf(video))
        // A point added inside a ramp, between sections of one speed: the picture plays exactly as before.
        val added = retime.addPoint(doc, "V", 40).timeline.clip("V")!!
        assertEquals(listOf(0, 30, 0, 30), added.speedSections.map { it.rampFrames })
        (0..120).forEach { f -> assertEquals("frame $f", eased.sourceOffsetMs(f, fps), added.sourceOffsetMs(f, fps), 1e-6) }
        // The slow section at 0.25×: twice as long, its ramps kept.
        val slower = retime.setSectionSpeed(doc, "V", 60, ClipSpeed.of(PlaybackSpeed.QUARTER)).timeline.clip("V")!!
        assertEquals(listOf(30, 120, 30), slower.speedSections.map { it.frames })
        assertEquals(listOf(0, 30, 30), slower.speedSections.map { it.rampFrames })
        // The last point removed: its two sections join, the first point's ramp stays on.
        val joined = retime.removePoint(doc, "V", 95).timeline.clip("V")!!
        assertEquals(listOf(0, 30), joined.speedSections.map { it.rampFrames })
        // The first section made shorter than half its ramp: the ramp shrinks to fit inside the clip.
        val quick = retime.setSectionSpeed(doc, "V", 10, ClipSpeed.of(PlaybackSpeed.QUADRUPLE)).timeline.clip("V")!!
        assertEquals(listOf(8, 60, 30), quick.speedSections.map { it.frames })
        assertEquals(listOf(0, 16, 30), quick.speedSections.map { it.rampFrames })
    }

    @Test
    fun `a split inside a ramp keeps every frame of the piece holding its point`() {
        val split = SplitClipUseCase(SequentialIds())(timelineOf(eased), atFrame = 25, selectedClipId = "V", fps = fps).timeline
        val right = split.clips.single { it.id != "V" }
        assertEquals(listOf(5, 60, 30), right.speedSections.map { it.frames })
        assertEquals("its ramp reaches back before the piece and plays on as it was", 30, right.rampAt(1))
        val start = eased.sourceOffsetMs(25, fps)
        (0 until 95).forEach { f -> assertEquals("right $f", eased.sourceOffsetMs(25 + f, fps) - start, right.sourceOffsetMs(f, fps), 1e-6) }
        assertEquals((2_000 + start).toLong(), right.sourceInMs)
    }

    @Test
    fun `the render plan steps through a ramp, every step starting on the curve`() {
        val clip = sudden.copy(start = 10, speedSections = sections.mapIndexed { i, s -> if (i == 1) s.copy(rampFrames = 30) else s })
        val doc = projectOf(
            timeline = timelineOf(clip, clipOf("A", 10, 120, track = Timeline.A1, assetId = "v", sourceInMs = 2_000).copy(speedSections = clip.speedSections)),
            media = listOf(video),
        )
        val plan = BuildRenderPlanUseCase()(doc)
        val pictures = plan.layers.single().segments.filter { it.clipId == "V" }
        // Ten steps of three frames over the ramp (frames 25–55 of the timeline), then a sudden change at 100.
        assertEquals(
            listOf(10 to 25) + (25 until 55 step 3).map { it to it + 3 } + listOf(55 to 100, 100 to 130),
            pictures.map { it.startFrame to it.endFrame },
        )
        pictures.forEach { p ->
            val a = p.startFrame - clip.start
            val b = p.endFrame - clip.start
            assertEquals("starts on the curve at $a", (2_000 + clip.sourceOffsetMs(a, fps)).toLong(), p.sourceInMs)
            assertEquals("plays the curve's source to $b", clip.sourceOffsetMs(b, fps) - clip.sourceOffsetMs(a, fps), p.speed * (b - a) * 1000.0 / fps, 1e-3)
        }
        val steps = pictures.drop(1).take(10).map { it.speed }
        assertTrue("slowing all the way: $steps", steps.zipWithNext().all { (x, y) -> y < x } && steps.first() < 1f && steps.last() > 0.5f)
        assertEquals("the sections either side keep their own speeds", listOf(1f, 0.5f, 1f), listOf(pictures[0].speed, pictures[11].speed, pictures[12].speed))
        // The sound takes longer steps (five of six frames), each joining the next under the usual short overlap.
        val sound = plan.audio.flatMap { it.segments }.filter { it.clipId == "A" }.sortedBy { it.startFrame }
        assertEquals(listOf(10, 25, 31, 37, 43, 49, 55, 100), sound.map { it.startFrame })
        assertEquals(listOf(0, 2, 2, 2, 2, 2, 2, 2), sound.map { it.joinInFrames })
        assertEquals(2, plan.audio.size)
        sound.forEach { s -> assertEquals((2_000 + clip.sourceOffsetMs(s.startFrame - clip.start, fps)).toLong(), s.sourceInMs) }
    }
}
