package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.normalizedSections
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's speed points: a clip in sections of their own speed, through the timeline's edits and the render plan. */
class SpeedPointsTest {

    private val fps = 30
    private val retime = RetimeUseCase()
    private val video = assetOf("v", durationMs = 20_000)
    private val half = ClipSpeed.of(PlaybackSpeed.HALF)

    /** 1 s at 1×, 2 s of slow motion (1 s of source at 0.5×), 1 s at 1×: 120 frames playing 3 s of source from 2 s. */
    private val ramp = clipOf("V", 0, 120, assetId = "v", sourceInMs = 2_000).copy(
        speedSections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(60, half), SpeedSection(30, ClipSpeed.NORMAL)),
    )

    @Test
    fun `frames map to source through the sections, the edge sections' speeds beyond the clip`() {
        assertEquals(listOf(0, 30, 90, 120), ramp.sectionBounds)
        assertEquals(1_000.0, ramp.sourceOffsetMs(30, fps), 1e-6)
        assertEquals(1_500.0, ramp.sourceOffsetMs(60, fps), 1e-6)
        assertEquals(3_000.0, ramp.sourceOffsetMs(120, fps), 1e-6)
        assertEquals("past the end at the last section's speed", 4_000.0, ramp.sourceOffsetMs(150, fps), 1e-6)
        assertEquals("before the start at the first's", -500.0, ramp.sourceOffsetMs(-15, fps), 1e-6)
        assertEquals(3_000L, ramp.sourceDurationMs(fps))
        assertEquals(60.0, ramp.framesAtSourceMs(1_500.0, fps), 1e-6)
        assertEquals(105.0, ramp.framesAtSourceMs(2_500.0, fps), 1e-6)
        assertEquals(listOf(1f, 0.5f, 1f), listOf(ramp.speedAt(10), ramp.speedAt(60), ramp.speedAt(119)))
        assertEquals(1f, ramp.speedAt(500), 0f)
    }

    @Test
    fun `a window of the clip keeps the sections it crosses, one left becoming the clip's speed`() {
        assertEquals(
            ClipSpeed.NORMAL to listOf(SpeedSection(10, ClipSpeed.NORMAL), SpeedSection(60, half), SpeedSection(10, ClipSpeed.NORMAL)),
            ramp.sectionWindow(20, 80),
        )
        assertEquals("reaching back before the clip", listOf(40, 10), ramp.sectionWindow(-10, 50).second.map { it.frames })
        assertEquals("inside the slow section", half to emptyList<SpeedSection>(), ramp.sectionWindow(40, 20))
        assertEquals(ClipSpeed.NORMAL to emptyList<SpeedSection>(), ramp.sectionWindow(95, 40))
        assertEquals(
            "neighbours of one speed stay apart (a point is set before its speeds change)",
            2,
            normalizedSections(listOf(SpeedSection(10, half), SpeedSection(0, ClipSpeed.NORMAL), SpeedSection(5, half)), ClipSpeed.NORMAL).second.size,
        )
    }

    @Test
    fun `a point splits a section, a section's speed keeps its source and moves the clips after it`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("V", 0, 120, assetId = "v", sourceInMs = 2_000, linkId = "L").copy(
                    keyframes = listOf(TransformKeyframe(45, ClipTransform(scalePercent = 150)), TransformKeyframe(100, ClipTransform())),
                ),
                clipOf("A", 0, 120, track = Timeline.A1, assetId = "v", sourceInMs = 2_000, linkId = "L")
                    .copy(volumeKeyframes = listOf(VolumeKeyframe(100, -6f))),
                clipOf("N", 120, 60, assetId = "v"),
            ),
            media = listOf(video),
        )
        val one = retime.addPoint(doc, "V", 30)
        assertEquals("已添加变速点 · 片段分为 2 段", one.message)
        val two = retime.addPoint(doc.copy(timeline = one.timeline), "V", 90)
        val pointed = doc.copy(timeline = two.timeline)
        assertEquals(listOf(30, 60, 30), pointed.timeline.clip("V")!!.speedSections.map { it.frames })
        assertEquals("the sound has the same points", listOf(30, 60, 30), pointed.timeline.clip("A")!!.speedSections.map { it.frames })

        val slow = retime.setSectionSpeed(pointed, "V", 50, half)
        assertEquals("第 2 段速度 0.5×，片段时长 00:00:06:00", slow.message)
        val v = slow.timeline.clip("V")!!
        assertEquals(listOf(30, 120, 30), v.speedSections.map { it.frames })
        assertEquals(180, v.durationFrames)
        assertEquals("its source kept", 4_000L, v.sourceDurationMs(fps))
        assertEquals(listOf(30, 120, 30), slow.timeline.clip("A")!!.speedSections.map { it.frames })
        assertEquals("the next clip moves by the new length", 180, slow.timeline.clip("N")!!.start)
        // Keyframes stay with the picture: frame 45 (inside the slowed section) lands at 60, frame 100 (after it) at 160.
        assertEquals(listOf(60, 160), v.keyframes.map { it.frame })
        assertEquals(listOf(160), slow.timeline.clip("A")!!.volumeKeyframes.map { it.frame })
    }

    @Test
    fun `removing the nearest point joins two sections at the earlier speed, clearing plays the whole at 1x`() {
        val doc = projectOf(timeline = timelineOf(ramp, clipOf("N", 120, 60, assetId = "v")), media = listOf(video))
        val joined = retime.removePoint(doc, "V", 88)
        // The point at 90 joins 1 s of slow motion (60 frames at 0.5×) with 30 frames at 1×, played at 0.5×: 120 frames.
        assertEquals(listOf(30, 120), joined.timeline.clip("V")!!.speedSections.map { it.frames })
        assertEquals("已删除变速点 · 还剩 1 个", joined.message)
        assertEquals(150, joined.timeline.clip("N")!!.start)

        val cleared = retime.clear(doc, "V")
        val v = cleared.timeline.clip("V")!!
        assertFalse(v.retimed)
        assertEquals(ClipSpeed.NORMAL, v.speed)
        assertEquals("3 s of source at 1×", 90, v.durationFrames)
        assertEquals("已清除变速点，片段恢复 1×，时长 00:00:03:00", cleared.message)
        assertEquals(90, cleared.timeline.clip("N")!!.start)
    }

    @Test
    fun `speed points need a picture's own frames, a frame inside the clip, and no point already there`() {
        val still = assetOf("img", kind = MediaKind.IMAGE)
        val doc = projectOf(
            timeline = timelineOf(ramp, clipOf("S", 200, 60, assetId = "img"), clipOf("R", 300, 60, assetId = "v").copy(reverse = true)),
            media = listOf(video, still),
        )
        fun refusal(block: () -> Unit) = assertThrows(EditRejected::class.java) { block() }.message
        assertEquals("图片没有可以变速的画面", refusal { retime.addPoint(doc, "S", 10) })
        assertEquals("倒放的片段不能添加变速点：先关闭倒放", refusal { retime.addPoint(doc, "R", 10) })
        assertEquals("把播放头移到片段内（不在首尾），再添加变速点", refusal { retime.addPoint(doc, "V", 0) })
        assertEquals("这里已经有变速点", refusal { retime.addPoint(doc, "V", 30) })
        assertEquals("这一段已经是 0.5×", refusal { retime.setSectionSpeed(doc, "V", 40, half) })
        assertEquals("有变速点的片段不能倒放：先清除变速点", refusal { ClipPropertiesUseCase().reverse(doc.timeline, "V", true) })
        val locked = doc.copy(timeline = doc.timeline.copy(tracks = doc.timeline.tracks.map { it.copy(locked = it.id == Timeline.V1) }))
        assertEquals("轨道已锁定", refusal { retime.addPoint(locked, "V", 60) })
    }

    @Test
    fun `the render plan plays each section at its own speed, picture and sound`() {
        val doc = projectOf(
            timeline = timelineOf(ramp, clipOf("A", 0, 120, track = Timeline.A1, assetId = "v", sourceInMs = 2_000).copy(speedSections = ramp.speedSections)),
            media = listOf(video),
        )
        val plan = BuildRenderPlanUseCase()(doc)
        val pictures = plan.layers.single().segments
        assertEquals(listOf(0 to 30, 30 to 90, 90 to 120), pictures.map { it.startFrame to it.endFrame })
        assertEquals(listOf(2_000L, 3_000L, 4_000L), pictures.map { it.sourceInMs })
        assertEquals(listOf(1f, 0.5f, 1f), pictures.map { it.speed })
        assertTrue("keyframes count from the clip's start in every piece", pictures.all { it.clipStartFrame == 0 })
        // The sound joins its pieces under a short overlap, A/B rolled: each plays 2 frames (50 ms, in whole frames) on past
        // its point, fading out on its lane while the next fades in on the other.
        assertEquals(2, plan.audio.size)
        val sound = plan.audio.flatMap { it.segments }.filter { it.clipId != null }.sortedBy { it.startFrame }
        assertEquals(listOf(0 to 32, 30 to 92, 90 to 120), sound.map { it.startFrame to it.endFrame })
        assertEquals(listOf(0 to 2, 2 to 2, 2 to 0), sound.map { it.joinInFrames to it.joinOutFrames })
        assertEquals("the outer pieces share a lane", plan.audio[0].segments.filter { it.clipId != null }.map { it.startFrame }, listOf(0, 90))
        assertEquals(listOf(2_000L, 3_000L, 4_000L), sound.map { it.sourceInMs })
        assertEquals(listOf(0L, 1_000L, 3_000L), sound.map { it.clipOffsetMs })
        assertEquals(listOf(1f, 0.5f, 1f), sound.map { it.speed })
        // A still inside the slow section: one piece, 0.75 s into its second of source.
        val still = BuildRenderPlanUseCase()(doc, 75, 77).layers.single().segments.single()
        assertEquals(3_750L, still.sourceInMs)
        assertEquals(0, still.startFrame)
        assertEquals(0.5f, still.speed, 0f)
    }

    @Test
    fun `sections of one speed play as one piece, and a short section shortens its joins`() {
        // A point just added splits a section in two of the same speed: nothing to cut yet, picture or sound.
        val split = retime.addPoint(projectOf(timeline = timelineOf(clipOf("V", 0, 120, assetId = "v", sourceInMs = 2_000)), media = listOf(video)), "V", 40)
        val linked = projectOf(
            timeline = split.timeline.copy(clips = split.timeline.clips + clipOf("A", 0, 120, track = Timeline.A1, assetId = "v", sourceInMs = 2_000)
                .copy(speedSections = split.timeline.clips.single().speedSections)),
            media = listOf(video),
        )
        val plan = BuildRenderPlanUseCase()(linked)
        assertEquals(listOf(0 to 120), plan.layers.single().segments.map { it.startFrame to it.endFrame })
        assertEquals(listOf(0 to 120), plan.audio.single().segments.map { it.startFrame to it.endFrame })
        // A one-frame section between two others: the join into it takes just that frame, so the sound before it never
        // plays on past it; the join out of it is the usual two.
        val blip = clipOf("A", 0, 61, track = Timeline.A1, assetId = "v").copy(
            speedSections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(1, half), SpeedSection(30, ClipSpeed.NORMAL)),
        )
        val sound = BuildRenderPlanUseCase()(projectOf(timeline = timelineOf(blip), media = listOf(video))).audio
            .flatMap { it.segments }.filter { it.clipId != null }.sortedBy { it.startFrame }
        assertEquals(listOf(0 to 31, 30 to 33, 31 to 61), sound.map { it.startFrame to it.endFrame })
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 0), sound.map { it.joinInFrames to it.joinOutFrames })
    }

    @Test
    fun `a transition across a speed point runs on through both pieces, and the outgoing tail plays at the last speed`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 40)
        val slowStart = clipOf("B", 120, 90, assetId = "v", sourceInMs = 8_000).copy(
            transitionIn = dissolve, speedSections = listOf(SpeedSection(20, half), SpeedSection(70, ClipSpeed.NORMAL)),
        )
        val doc = projectOf(timeline = timelineOf(ramp, slowStart), media = listOf(video))
        val plan = BuildRenderPlanUseCase()(doc)
        val b = plan.layers.first { !it.isHandles }.segments.filter { it.clipId == "B" }
        assertEquals(listOf(120 to 140, 140 to 210), b.map { it.startFrame to it.endFrame })
        assertEquals("both pieces carry the dissolve", listOf(dissolve, dissolve), b.map { it.transition })
        // A's tail past its out point at its last section's 1×: frames 120–160 play 5–6.333 s of the source.
        val handle = plan.layers.first { it.isHandles }.segments.single { it.showsPicture }
        assertEquals(5_000L, handle.sourceInMs)
        assertEquals(1f, handle.speed, 0f)
    }

    @Test
    fun `split and trims keep every frame's source, and a whole-clip speed gives the points up`() {
        val split = SplitClipUseCase(SequentialIds())(timelineOf(ramp), atFrame = 60, selectedClipId = "V", fps = fps).timeline
        val left = split.clips.single { it.id == "V" }
        val right = split.clips.single { it.id != "V" }
        assertEquals(listOf(30, 30), left.speedSections.map { it.frames })
        assertEquals(listOf(30, 30), right.speedSections.map { it.frames })
        assertEquals(3_500L, right.sourceInMs)
        (0 until 60).forEach { f -> assertEquals("right $f", ramp.sourceOffsetMs(60 + f, fps) - 1_500.0, right.sourceOffsetMs(f, fps), 1.0) }

        val doc = projectOf(timeline = timelineOf(ramp), media = listOf(video))
        val head = TrimClipUseCase()(doc.timeline, "V", TrimEdge.START, 45, 0, false, 0, 20_000, fps).timeline.clips.single()
        assertEquals("15 frames into the slow section: 1.25 s of source in", 3_250L, head.sourceInMs)
        assertEquals(listOf(45, 30), head.speedSections.map { it.frames })
        val longer = TrimClipUseCase()(doc.timeline, "V", TrimEdge.END, 150, 0, false, 0, 20_000, fps).timeline.clips.single()
        assertEquals(listOf(30, 60, 60), longer.speedSections.map { it.frames })
        val earlier = TrimClipUseCase()(doc.copy(timeline = timelineOf(ramp.copy(start = 40))).timeline, "V", TrimEdge.START, 20, 0, false, 0, 20_000, fps)
            .timeline.clips.single()
        assertEquals("extending the head at its section's 1×", 1_333L, earlier.sourceInMs)
        assertEquals(listOf(50, 60, 30), earlier.speedSections.map { it.frames })

        val doubled = ClipPropertiesUseCase().speed(timelineOf(ramp), "V", ClipSpeed.of(PlaybackSpeed.DOUBLE)).clips.single()
        assertFalse(doubled.retimed)
        assertEquals("3 s of source at 2×", 45, doubled.durationFrames)
    }
}
