package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.AudioLane
import com.example.prismstudio.p40b43a88.domain.model.AudioSegment
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleCue
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionRole
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.VideoLayer
import com.example.prismstudio.p40b43a88.domain.model.VideoSegment
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildRenderPlanUseCaseTest {

    private val buildPlan = BuildRenderPlanUseCase()
    private val video = assetOf("v", durationMs = 20_000)
    private val still = assetOf("img", kind = MediaKind.IMAGE, name = "img.png")

    private fun gap(a: Int, b: Int) = VideoSegment(null, null, false, a, b, 0, 1f, null)

    @Test
    fun `each video track with clips is its own layer, top track first, and V1 plays on underneath V2`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 100, assetId = "v", sourceInMs = 1_000).copy(transitionIn = dissolve),
                clipOf("B", 30, 30, track = Timeline.V2, assetId = "img"),
            ),
            media = listOf(video, still),
        )

        val plan = buildPlan(doc)

        assertEquals(
            listOf(
                VideoLayer(Timeline.V2, listOf(gap(0, 30), VideoSegment("B", "img", true, 30, 60, 0, 1f, null), gap(60, 100))),
                // A opens the timeline: its dissolve has no outgoing clip, so there is no handles layer.
                VideoLayer(Timeline.V1, listOf(VideoSegment("A", "v", false, 0, 100, 1_000, 1f, dissolve))),
            ),
            plan.layers,
        )
        assertEquals(listOf(30 until 60), plan.layers[0].visibleRanges)
        assertEquals(listOf(30, 100, 1920, 1080, 0, 100), listOf(plan.fps, plan.durationFrames, plan.width, plan.height, plan.startFrame, plan.endFrame))
        assertTrue(plan.audio.isEmpty())
    }

    @Test
    fun `a clip's composite mode rides on its segments`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 100, assetId = "v"),
                clipOf("B", 30, 30, track = Timeline.V2, assetId = "v").copy(composite = CompositeMode.SCREEN),
            ),
            media = listOf(video),
        )
        val plan = buildPlan(doc)
        assertEquals(listOf(CompositeMode.NORMAL, CompositeMode.SCREEN, CompositeMode.NORMAL), plan.layers[0].segments.map { it.composite })
        assertEquals(listOf(CompositeMode.NORMAL), plan.layers[1].segments.map { it.composite })
    }

    @Test
    fun `a clip's retime process rides on its segments`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 50, assetId = "v").copy(retime = RetimeProcess.FRAME_BLEND),
                clipOf("B", 50, 50, assetId = "v"),
            ),
            media = listOf(video),
        )
        assertEquals(listOf(RetimeProcess.FRAME_BLEND, RetimeProcess.NEAREST), buildPlan(doc).layers.single().segments.map { it.retime })
        val scaled = projectOf(timeline = timelineOf(clipOf("A", 0, 50, assetId = "v").copy(scaling = ClipScaling.FILL), clipOf("B", 50, 50, assetId = "v")), media = listOf(video))
        assertEquals(listOf(ClipScaling.FILL, ClipScaling.FIT), buildPlan(scaled).layers.single().segments.map { it.scaling })
    }

    @Test
    fun `temporal noise reduction rides on a footage clip's segments and reshapes the composition, never a still's`() {
        val still = assetOf("s").copy(kind = MediaKind.IMAGE)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 50, assetId = "v").copy(grade = ColorGrade(temporalNr = 40)),
                clipOf("B", 50, 50, assetId = "v"),
                clipOf("C", 100, 50, assetId = "s").copy(grade = ColorGrade(temporalNr = 40)),
            ),
            media = listOf(video, still),
        )
        assertEquals(listOf(true, false, false), buildPlan(doc).layers.single().segments.map { it.temporalNr })
        // Switching it on changes what the preview builds; its strength does not.
        val off = projectOf(timeline = timelineOf(clipOf("A", 0, 50, assetId = "v"), clipOf("B", 50, 50, assetId = "v")), media = listOf(video))
        val on = projectOf(timeline = timelineOf(clipOf("A", 0, 50, assetId = "v").copy(grade = ColorGrade(temporalNr = 10)), clipOf("B", 50, 50, assetId = "v")), media = listOf(video))
        val stronger = projectOf(timeline = timelineOf(clipOf("A", 0, 50, assetId = "v").copy(grade = ColorGrade(temporalNr = 90)), clipOf("B", 50, 50, assetId = "v")), media = listOf(video))
        assertTrue(buildPlan(off).structureSignature() != buildPlan(on).structureSignature())
        assertEquals(buildPlan(on).structureSignature(), buildPlan(stronger).structureSignature())
    }

    @Test
    fun `a clip following the project takes the project's scaling, and at its own size the plan carries its framing`() {
        val phone = assetOf("phone").copy(width = 1080, height = 1920)
        val unsized = assetOf("unsized").copy(width = 0, height = 0)
        val base = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 30, assetId = "phone"),
                clipOf("B", 30, 30, assetId = "phone").copy(scaling = ClipScaling.FIT),
                clipOf("C", 60, 30, assetId = "unsized"),
            ),
            media = listOf(phone, unsized),
        )
        val doc = base.copy(format = base.format.copy(scaling = ClipScaling.CROP))
        val segments = buildPlan(doc).layers.single().segments
        // A follows the project at its own size: half the width, 1.78 of the height; B fits as it asks; C's size is
        // unknown, so it cannot be placed by pixels and fits.
        assertEquals(listOf(ClipScaling.CROP, ClipScaling.FIT, ClipScaling.FIT), segments.map { it.scaling })
        assertEquals(0.5625f, segments[0].framing.spanX, 1e-5f)
        assertEquals(1.7777778f, segments[0].framing.spanY, 1e-5f)
        assertEquals(0.31640625f, segments[1].framing.spanX, 1e-5f)
    }

    @Test
    fun `disabled tracks are skipped and title clips never cover the picture`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("B", 30, 30, track = Timeline.V2, assetId = "v"),
                titleClipOf("T2", 0, 30, track = Timeline.V2),
                clipOf("A", 0, 60, assetId = "v"),
                titleClipOf("T1", 60, 30, track = Timeline.V1),
                disabled = setOf(Timeline.V2),
            ),
            media = listOf(video),
        )

        val plan = buildPlan(doc)

        assertEquals(listOf(VideoLayer(Timeline.V1, listOf(VideoSegment("A", "v", false, 0, 60, 0, 1f, null), gap(60, 90)))), plan.layers)
        assertEquals(listOf(TitleCue("T1", 60, 90, TitleStyle("Title"), ClipTransform())), plan.titles)
    }

    @Test
    fun `gaps render black and offline or still media are flagged on their segments`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 30, assetId = "off"), clipOf("C", 45, 30, assetId = "img")),
            media = listOf(assetOf("off", offline = true), still),
        )

        val plan = buildPlan(doc)

        val layer = plan.layers.single()
        assertEquals(
            listOf(
                VideoSegment("A", null, false, 0, 30, 0, 1f, null),
                gap(30, 45),
                VideoSegment("C", "img", true, 45, 75, 0, 1f, null),
            ),
            layer.segments,
        )
        assertEquals("the offline clip shows no picture", listOf(45 until 75), layer.visibleRanges)
    }

    @Test
    fun `a title cut by the range keeps its whole clip's span for its entrance, exit and roll`() {
        val doc = projectOf(timeline = timelineOf(clipOf("A", 0, 200, assetId = "v"), titleClipOf("T", 30, 90)), media = listOf(video))
        val cue = buildPlan(doc, rangeStart = 60, rangeEnd = 100).titles.single()
        // Rendered over 0–40 of the range; the clip spans −30…60 there, so it is mid-way, not fading in at 0.
        assertEquals(listOf(0, 40, -30, 60), listOf(cue.startFrame, cue.endFrame, cue.clipStartFrame, cue.clipEndFrame))
    }

    @Test
    fun `a title fades by its handles over its whole clip, and a subtitle never does`() {
        val title = titleClipOf("T", 30, 90).copy(fadeInSeconds = 1f, fadeOutSeconds = 0.5f)
        val subtitle = titleClipOf("S", 30, 90, track = "s1").let { it.copy(title = it.title!!.copy(template = TitleTemplate.SUBTITLE), fadeInSeconds = 1f) }
        val tracks = Timeline.defaultTracks() + Track("s1", "S1", TrackKind.SUBTITLE)
        val doc = projectOf(timeline = Timeline(tracks, listOf(clipOf("A", 0, 200, assetId = "v"), title, subtitle), emptyList()), media = listOf(video))
        val cue = buildPlan(doc, rangeStart = 45, rangeEnd = 200).titles.first { it.clipId == "T" }
        assertEquals(listOf(30f, 15f), listOf(cue.fadeInFrames, cue.fadeOutFrames))
        // The clip spans −15…75 of the range: half faded in at 0, whole from 15 to 60, then down to a fifteenth on its last frame.
        assertEquals(0.5f, cue.fadeAt(0), 1e-6f)
        assertEquals(1f, cue.fadeAt(15), 0f)
        assertEquals(1f, cue.fadeAt(60), 0f)
        assertEquals(8f / 15, cue.fadeAt(67), 1e-6f)
        assertEquals(1f / 15, cue.fadeAt(74), 1e-6f)
        assertEquals(0f, buildPlan(doc).titles.first { it.clipId == "S" }.fadeInFrames, 0f)
    }

    @Test
    fun `a sub range is rebased to frame zero and clipped on every lane`() {
        val wipe = Transition(TransitionType.WIPE, 10)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 100, assetId = "v"),
                clipOf("B", 100, 100, assetId = "v", sourceInMs = 500).copy(transitionIn = wipe),
                clipOf("X", 0, 200, track = Timeline.A1, assetId = "v"),
                titleClipOf("T", 80, 40),
            ),
            media = listOf(video),
        )

        val plan = buildPlan(doc, rangeStart = 90, rangeEnd = 130)

        assertEquals(listOf(90, 130, 40), listOf(plan.startFrame, plan.endFrame, plan.durationFrames))
        assertEquals(
            listOf(
                VideoLayer(
                    Timeline.V1,
                    listOf(VideoSegment("A", "v", false, 0, 10, 3_000, 1f, null, clipStartFrame = -90), VideoSegment("B", "v", false, 10, 40, 500, 1f, wipe)),
                ),
                // The wipe reveals A continuing past its out point (3.333 s into the source) for 10 frames.
                VideoLayer(
                    "${Timeline.V1}-handles",
                    listOf(gap(0, 10), VideoSegment("A", "v", false, 10, 20, 3_333, 1f, null, clipStartFrame = -90), gap(20, 40)),
                    isHandles = true,
                ),
            ),
            plan.layers,
        )
        assertEquals(listOf(AudioLane(Timeline.A1, listOf(AudioSegment("X", "v", 0, 40, 3_000, 1f, 3_000, 6_666)))), plan.audio)
        assertEquals(listOf(TitleCue("T", 0, 30, TitleStyle("Title"), ClipTransform(), clipStartFrame = -10)), plan.titles)
    }

    @Test
    fun `out of bounds ranges are clamped to the timeline and an inverted range is empty`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 200, assetId = "v"), clipOf("X", 0, 200, track = Timeline.A1, assetId = "v")),
            media = listOf(video),
        )

        val whole = buildPlan(doc, rangeStart = -20, rangeEnd = 10_000)
        assertEquals(0, whole.startFrame)
        assertEquals(200, whole.endFrame)
        assertEquals(listOf(VideoLayer(Timeline.V1, listOf(VideoSegment("A", "v", false, 0, 200, 0, 1f, null)))), whole.layers)

        val inverted = buildPlan(doc, rangeStart = 160, rangeEnd = 120)
        assertEquals(160, inverted.startFrame)
        assertTrue(inverted.isEmpty)
        assertTrue(inverted.layers.isEmpty())
        assertTrue(inverted.audio.isEmpty())
    }

    @Test
    fun `a sound's pitch rides on its audio segments and changes the plan's structure`() {
        val clips = listOf(clipOf("Y", 0, 30, track = Timeline.A1, assetId = "music").copy(pitchCents = -500))
        val doc = projectOf(timeline = Timeline(Timeline.defaultTracks(), clips, emptyList()), media = listOf(assetOf("music", kind = MediaKind.AUDIO)))
        val plan = buildPlan(doc)
        assertEquals(listOf(-500), plan.audio.single().segments.map { it.pitchCents })
        val flat = buildPlan(doc.copy(timeline = doc.timeline.replaceClip(clips.single().copy(pitchCents = 0))))
        assertTrue("a pitch change rebuilds the preview", plan.structureSignature() != flat.structureSignature())
    }

    @Test
    fun `a track's reverb rings on into the silence after each sound, as far as its file goes on`() {
        val clips = listOf(
            clipOf("P", 0, 120, assetId = "v"),
            clipOf("X", 10, 30, track = Timeline.A1, assetId = "v", sourceInMs = 1_000),
            // 1.1 s of music, its first second used: a tenth of a second (three frames, one kept in hand) to ring on in.
            clipOf("Y", 60, 30, track = Timeline.A1, assetId = "short"),
        )
        val tracks = Timeline.defaultTracks()
        val mixer = com.example.prismstudio.p40b43a88.domain.model.MixerState.forTracks(tracks).let { m ->
            m.replace(m.channel(Timeline.A1).copy(reverb = com.example.prismstudio.p40b43a88.domain.model.TrackReverb(mix = 30)))
        }
        val media = listOf(assetOf("v", durationMs = 20_000), assetOf("short", kind = MediaKind.AUDIO, durationMs = 1_100))
        val doc = projectOf(timeline = Timeline(tracks, clips, emptyList()), media = media, mixer = mixer)

        val lane = buildPlan(doc).audio.single()
        assertEquals(listOf(null to (0 to 10), "X" to (10 to 60), "Y" to (60 to 92)), lane.segments.map { it.clipId to (it.startFrame to it.endFrame) })
        assertEquals("the whole gap, the silence gone", listOf(0, 20, 2), lane.segments.map { it.tailFrames })
        assertEquals("the source plays on under the tail", 1_000L, lane.segments[1].sourceInMs)
        // Off (mix 0): the clips keep their lengths and the silence stays.
        val dry = buildPlan(doc.copy(mixer = mixer.replace(mixer.channel(Timeline.A1).copy(reverb = com.example.prismstudio.p40b43a88.domain.model.TrackReverb.OFF))))
        assertEquals(listOf(10 to 40, 40 to 60, 60 to 90), dry.audio.single().segments.drop(1).map { it.startFrame to it.endFrame })
        assertTrue("switching it on rebuilds the sound", dry.structureSignature() != buildPlan(doc).structureSignature())
    }

    @Test
    fun `each enabled audio track becomes its own lane with silence between clips`() {
        val tracks = Timeline.defaultTracks().map { if (it.id == Timeline.A2) it.copy(enabled = false) else it } +
            Track("a3", "A3", TrackKind.AUDIO)
        val clips = listOf(
            clipOf("X", 10, 30, track = Timeline.A1, assetId = "v", sourceInMs = 1_000, speed = PlaybackSpeed.DOUBLE),
            clipOf("Y", 60, 30, track = Timeline.A1, assetId = "music"),
            clipOf("Z", 0, 30, track = Timeline.A2, assetId = "music"),
        )
        val doc = projectOf(timeline = Timeline(tracks, clips, emptyList()), media = listOf(video, assetOf("music", kind = MediaKind.AUDIO)))

        val plan = buildPlan(doc, rangeStart = 20)

        assertEquals(
            listOf(
                AudioLane(
                    Timeline.A1,
                    listOf(
                        AudioSegment("X", "v", 0, 20, 1_666, 2f, 333, 1_000),
                        AudioSegment(null, null, 20, 40, 0, 1f, 0, 0),
                        AudioSegment("Y", "music", 40, 70, 0, 1f, 0, 1_000),
                    ),
                ),
            ),
            plan.audio,
        )
        assertTrue("no picture clips, no picture layers", plan.layers.isEmpty())
    }

    @Test
    fun `a dissolve between adjacent clips puts the outgoing clip's handle under the incoming clip`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v", sourceInMs = 8_000).copy(transitionIn = dissolve)),
            media = listOf(video),
        )

        val layers = buildPlan(doc).layers

        assertEquals(listOf(Timeline.V1, "${Timeline.V1}-handles"), layers.map { it.id })
        assertFalse(layers[0].isHandles)
        assertEquals(listOf(gap(0, 60), VideoSegment("A", "v", false, 60, 75, 2_000, 1f, null, clipStartFrame = 0), gap(75, 120)), layers[1].segments)
    }

    @Test
    fun `a transition lasts as long as the outgoing source, stills always extend and dips slide to fit`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        fun plan(outgoingAsset: String, transition: Transition, media: List<com.example.prismstudio.p40b43a88.domain.model.MediaAsset>) = buildPlan(
            projectOf(
                timeline = timelineOf(
                    clipOf("A", 0, 60, assetId = outgoingAsset, sourceInMs = 2_000),
                    clipOf("B", 60, 60, assetId = "v").copy(transitionIn = transition),
                ),
                media = media,
            ),
        )
        fun handle(plan: com.example.prismstudio.p40b43a88.domain.model.RenderPlan) = plan.layers.firstOrNull { it.isHandles }
        fun opening(plan: com.example.prismstudio.p40b43a88.domain.model.RenderPlan) = plan.layers.first().segments.single { it.clipId == "B" }.transition

        // A plays 2.0–4.0 s of a 4.2 s file: 0.2 s (6 frames) are left, so the dissolve takes 6 frames.
        val short = plan("short", dissolve, listOf(assetOf("short", durationMs = 4_200), video))
        assertEquals(listOf(60 until 66), handle(short)?.visibleRanges)
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 6), opening(short))
        // Used to the very end: nothing to reveal — a cut, without a handles layer.
        val used = plan("short", dissolve, listOf(assetOf("short", durationMs = 4_000), video))
        assertEquals(null, handle(used))
        assertEquals(null, opening(used))
        // A still can be held as long as the transition needs.
        val held = plan("img", dissolve, listOf(still, video))
        assertEquals(listOf(60 until 75), handle(held)?.visibleRanges)
        assertEquals(dissolve, opening(held))
        // A dip shows one picture at a time: with nothing past A's out point it slides to centre on the cut, A's own last
        // 7 frames fading out beneath, at its full length.
        val dip = plan("short", Transition(TransitionType.DIP_TO_BLACK, 15), listOf(assetOf("short", durationMs = 4_000), video))
        assertEquals(listOf(53 until 60), handle(dip)?.visibleRanges)
        assertEquals(Transition(TransitionType.DIP_TO_BLACK, 15), opening(dip))
    }

    @Test
    fun `a range starting partway through a transition keeps it, counted from the clip's start`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v").copy(transitionIn = Transition(TransitionType.WIPE, 30))),
            media = listOf(video),
        )
        // A still at frame 72 renders from 72: B is 12 frames into its 30-frame wipe, A still plays beneath it.
        val plan = BuildRenderPlanUseCase()(doc, 72, 74)
        val b = plan.layers.first { !it.isHandles }.segments.first { it.clipId == "B" }
        assertEquals(Transition(TransitionType.WIPE, 30), b.transition)
        assertEquals(-12, b.clipStartFrame)
        assertEquals("A", plan.layers.first { it.isHandles }.segments.first { it.showsPicture }.clipId)
        // Past the transition there is nothing left of it.
        assertEquals(null, BuildRenderPlanUseCase()(doc, 95, 97).layers.single().segments.first { it.clipId == "B" }.transition)
    }

    /** A uses 2–4 s of the 20 s file, B starts 8 s into it: handles on both sides of the cut at 60. */
    private fun aligned(transition: Transition, bSourceIn: Long = 8_000, aStart: Int = 0, aFrames: Int = 60, aAsset: String = "v") = projectOf(
        timeline = timelineOf(
            clipOf("A", aStart, aFrames, assetId = aAsset, sourceInMs = 2_000),
            clipOf("B", aStart + aFrames, 60, assetId = "v", sourceInMs = bSourceIn).copy(transitionIn = transition),
        ),
        media = listOf(video, assetOf("short", durationMs = 4_200)),
    )

    @Test
    fun `a centred transition starts half its length before the cut, B shown early from before its in point`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 30, alignment = TransitionAlignment.CENTER)
        val plan = buildPlan(aligned(dissolve))
        assertEquals(
            listOf(
                VideoSegment("A", "v", false, 0, 45, 2_000, 1f, null),
                // B from frame 45: 15 frames (0.5 s) before 8 s, the dissolve counting from there.
                VideoSegment("B", "v", false, 45, 120, 7_500, 1f, dissolve, clipStartFrame = 60, transitionLead = 15),
            ),
            plan.layers.first { !it.isHandles }.segments,
        )
        // Beneath it, A's last 15 frames and then 15 past its out point, one continuous run of its source.
        assertEquals(
            listOf(gap(0, 45), VideoSegment("A", "v", false, 45, 75, 3_500, 1f, null, clipStartFrame = 0), gap(75, 120)),
            plan.layers.first { it.isHandles }.segments,
        )
    }

    @Test
    fun `a transition ending on the edit plays wholly before the cut and a starting one after it`() {
        val wipe = Transition(TransitionType.WIPE, 30, alignment = TransitionAlignment.END)
        val ending = buildPlan(aligned(wipe))
        assertEquals(
            listOf(VideoSegment("A", "v", false, 0, 30, 2_000, 1f, null), VideoSegment("B", "v", false, 30, 120, 7_000, 1f, wipe, clipStartFrame = 60, transitionLead = 30)),
            ending.layers.first { !it.isHandles }.segments,
        )
        assertEquals(listOf(30 until 60), ending.layers.first { it.isHandles }.visibleRanges)
        val starting = buildPlan(aligned(wipe.copy(alignment = TransitionAlignment.START)))
        assertEquals(listOf(0 until 60, 60 until 120), starting.layers.first { !it.isHandles }.segments.map { it.startFrame until it.endFrame })
        assertEquals(listOf(60 until 90), starting.layers.first { it.isHandles }.visibleRanges)
    }

    @Test
    fun `a centred transition slides to the side with picture, an anchored one is cut short`() {
        val center = Transition(TransitionType.CROSS_DISSOLVE, 30, alignment = TransitionAlignment.CENTER)
        fun span(doc: com.example.prismstudio.p40b43a88.domain.model.ProjectDocument) = TransitionHandles.span(doc, doc.timeline.clip("B")!!, 30)!!
        // B from the start of its file: nothing before its in point, so the whole dissolve plays after the cut.
        val noHead = span(aligned(center, bSourceIn = 0))
        assertEquals(listOf(0, 30, true), listOf(noHead.lead, noHead.tail, noHead.shifted))
        // A running out 6 frames past its out point (2–4 s of 4.2 s): 24 frames before the cut, 6 after.
        val shortTail = span(aligned(center, aAsset = "short"))
        assertEquals(listOf(24, 6, true, false), listOf(shortTail.lead, shortTail.tail, shortTail.shifted, shortTail.shortened))
        // Both short: what each side has. Ending on the edit with 9 frames before B's in point: 9 frames.
        val both = span(aligned(center, bSourceIn = 300, aAsset = "short"))
        assertEquals(listOf(9, 6, true), listOf(both.lead, both.tail, both.shortened))
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER), both.effective)
        val end = span(aligned(center.copy(alignment = TransitionAlignment.END), bSourceIn = 300))
        assertEquals(listOf(9, 0, true), listOf(end.lead, end.tail, end.shortened))
    }

    @Test
    fun `before the cut a transition takes at most half the clip before it and none of its own opening`() {
        val center = Transition(TransitionType.CROSS_DISSOLVE, 30, alignment = TransitionAlignment.CENTER)
        // A lasts 20 frames: at most 10 before the cut, so 10 + 20.
        val short = aligned(center, aFrames = 20)
        val span = TransitionHandles.span(short, short.timeline.clip("B")!!, 30)!!
        assertEquals(listOf(10, 20), listOf(span.lead, span.tail))
        // A opening with a 40-frame dissolve of its own (into a still before it) leaves B 20 frames of its 60.
        val chained = projectOf(
            timeline = timelineOf(
                clipOf("Z", 0, 60, assetId = "img"),
                clipOf("A", 60, 60, assetId = "v", sourceInMs = 2_000).copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 40)),
                clipOf("B", 120, 90, assetId = "v", sourceInMs = 8_000).copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 45, alignment = TransitionAlignment.END)),
            ),
            media = listOf(video, still),
        )
        assertEquals(20, TransitionHandles.span(chained, chained.timeline.clip("B")!!, 30)!!.lead)
        val handles = buildPlan(chained).layers.first { it.isHandles }.segments.filter { it.showsPicture }
        assertEquals("one picture at a time beneath", listOf(60 until 100, 100 until 120), handles.map { it.startFrame until it.endFrame })
    }

    @Test
    fun `a still inside the lead keeps the transition, and the zoom blur or a lone clip start at the cut`() {
        val center = Transition(TransitionType.WIPE, 30, alignment = TransitionAlignment.CENTER)
        val still = buildPlan(aligned(center), 50, 52)
        val b = still.layers.first { !it.isHandles }.segments.single { it.clipId == "B" }
        assertEquals(listOf(0, 2, 10, 15), listOf(b.startFrame, b.endFrame, b.clipStartFrame, b.transitionLead))
        assertEquals(center, b.transition)
        assertEquals("A", still.layers.first { it.isHandles }.segments.single().clipId)

        val zoom = buildPlan(aligned(Transition(TransitionType.ZOOM_BLUR, 30, alignment = TransitionAlignment.CENTER)))
        assertTrue(zoom.layers.none { it.isHandles })
        assertEquals(0, zoom.layers.single().segments.single { it.clipId == "B" }.transitionLead)

        val lone = projectOf(timeline = timelineOf(clipOf("B", 60, 60, assetId = "v", sourceInMs = 8_000).copy(transitionIn = center)), media = listOf(video))
        val alone = buildPlan(lone).layers.single().segments.single { it.clipId == "B" }
        assertEquals(listOf(60, 0), listOf(alone.startFrame, alone.transitionLead))
    }

    @Test
    fun `a centred dip fades the outgoing clip out beneath the cut and the incoming one up from it, without handles`() {
        val dip = Transition(TransitionType.DIP_TO_BLACK, 30, alignment = TransitionAlignment.CENTER)
        val plan = buildPlan(aligned(dip))
        assertEquals(
            listOf(
                VideoSegment("A", "v", false, 0, 45, 2_000, 1f, null),
                gap(45, 60),
                // B from the middle — the cut — fading up, its dip counted from frame 45.
                VideoSegment("B", "v", false, 60, 120, 8_000, 1f, dip, clipStartFrame = 60, transitionLead = 15),
            ),
            plan.layers.first { !it.isHandles }.segments,
        )
        assertEquals(
            listOf(
                gap(0, 45),
                // A's own last 15 frames fade out: its dip starts 45 frames into it.
                VideoSegment(
                    "A", "v", false, 45, 60, 3_500, 1f, dip, clipStartFrame = 0, transitionLead = -45, transitionRole = TransitionRole.OUTGOING,
                    transitionOwnerId = "B",
                ),
                gap(60, 120),
            ),
            plan.layers.first { it.isHandles }.segments,
        )
    }

    @Test
    fun `a colour dip plans as the black one does, its colour left to the shader`() {
        val black = Transition(TransitionType.DIP_TO_BLACK, 30, alignment = TransitionAlignment.CENTER)
        val colour = black.copy(type = TransitionType.DIP_TO_COLOR, color = TitleColor.CYAN)
        fun shape(plan: RenderPlan) = plan.layers.map { layer -> layer.segments.map { listOf(it.clipId, it.startFrame, it.endFrame, it.sourceInMs, it.transitionLead, it.transitionRole) } }
        assertEquals(shape(buildPlan(aligned(black))), shape(buildPlan(aligned(colour))))
    }

    @Test
    fun `a dip starting on the edit fades A out past its out point, and slides toward the cut when A runs short`() {
        val dip = Transition(TransitionType.DIP_TO_WHITE, 30)
        val starting = buildPlan(aligned(dip))
        assertEquals(listOf(0 until 60, 75 until 120), starting.layers.first { !it.isHandles }.segments.filter { it.showsPicture }.map { it.startFrame until it.endFrame })
        assertEquals(8_500, starting.layers.first { !it.isHandles }.segments.single { it.clipId == "B" }.sourceInMs)
        assertEquals(listOf(60 until 75), starting.layers.first { it.isHandles }.visibleRanges)
        fun span(doc: com.example.prismstudio.p40b43a88.domain.model.ProjectDocument) = TransitionHandles.span(doc, doc.timeline.clip("B")!!, 30)!!
        // 6 frames past A's out point (2–4 s of 4.2 s): the middle 6 frames after the cut, 9 before it, still 30 frames.
        val short = span(aligned(dip, aAsset = "short"))
        assertEquals(listOf(9, 21, 6, 1), listOf(short.lead, short.tail, short.midpoint, if (short.shifted) 1 else 0))
        // Ending on the edit: B shows from 15 frames before its in point; with only 9 there the middle moves 6 frames later.
        val ending = span(aligned(dip.copy(alignment = TransitionAlignment.END)))
        assertEquals(listOf(30, -15), listOf(ending.lead, ending.midpoint))
        val thin = span(aligned(dip.copy(alignment = TransitionAlignment.END), bSourceIn = 300))
        assertEquals(listOf(24, -9, 30), listOf(thin.lead, thin.midpoint, thin.frames))
        // A clip before it of 10 frames gives at most 5 before the cut: the middle moves 10 frames past it, onto A's tail.
        val brief = span(aligned(dip.copy(alignment = TransitionAlignment.CENTER), aFrames = 10, aAsset = "short"))
        assertEquals(listOf(5, 25, 10, 1), listOf(brief.lead, brief.tail, brief.midpoint, if (brief.shifted) 1 else 0))
        // ...and used to the end of its file it has nothing to fade out in after the cut: a 10-frame dip, its middle there.
        val used = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 10, assetId = "end", sourceInMs = 2_000),
                clipOf("B", 10, 60, assetId = "v", sourceInMs = 8_000).copy(transitionIn = dip.copy(alignment = TransitionAlignment.CENTER)),
            ),
            media = listOf(video, assetOf("end", durationMs = 2_333)),
        )
        val tiny = span(used)
        assertEquals(listOf(5, 5, 0, 1), listOf(tiny.lead, tiny.tail, tiny.midpoint, if (tiny.shortened) 1 else 0))
    }

    @Test
    fun `a still inside a dip's first half renders the outgoing clip fading out, with no clip above it`() {
        val dip = Transition(TransitionType.DIP_TO_BLACK, 30, alignment = TransitionAlignment.CENTER)
        val plan = buildPlan(aligned(dip), 50, 52)
        assertEquals(listOf("${Timeline.V1}-handles"), plan.layers.map { it.id })
        val a = plan.layers.single().segments.single()
        assertEquals(listOf("A", "0..2", "-50", "-45"), listOf(a.clipId, "${a.startFrame}..${a.endFrame}", "${a.clipStartFrame}", "${a.transitionLead}"))
        assertEquals(TransitionRole.OUTGOING, a.transitionRole)
        assertEquals(listOf(plan), plan.stackingPasses())
    }

    @Test
    fun `a dip with nothing before it fades its clip up over its whole length`() {
        val lone = projectOf(
            timeline = timelineOf(clipOf("B", 60, 60, assetId = "v").copy(transitionIn = Transition(TransitionType.DIP_TO_BLACK, 30, alignment = TransitionAlignment.CENTER))),
            media = listOf(video),
        )
        val b = buildPlan(lone).layers.single().segments.single { it.clipId == "B" }
        assertEquals(listOf(60, 0), listOf(b.startFrame, b.transitionLead))
        assertEquals(TransitionRole.ALONE, b.transitionRole)
    }

    @Test
    fun `the iris, clock wipe, barn door and blur dissolve reveal the outgoing clip, the zoom blur does not`() {
        listOf(TransitionType.IRIS, TransitionType.CLOCK_WIPE, TransitionType.BARN_DOOR, TransitionType.BLUR_DISSOLVE).forEach { type ->
            val doc = projectOf(
                timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v").copy(transitionIn = Transition(type, 20))),
                media = listOf(video),
            )
            val handles = buildPlan(doc).layers.firstOrNull { it.isHandles }
            assertEquals("$type keeps A playing under B", "A", handles?.segments?.firstOrNull { it.showsPicture }?.clipId)
        }
        val zoom = projectOf(
            timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v").copy(transitionIn = Transition(TransitionType.ZOOM_BLUR, 20))),
            media = listOf(video),
        )
        assertTrue(buildPlan(zoom).layers.none { it.isHandles })
    }

    @Test
    fun `a reversed outgoing clip runs out at its source start, counting the frame a rounded position would skip`() {
        // A plays 0.266–8.266 s of a 15 s file backwards: the reversed copy's 6734–14734 ms. The copy's
        // frames from 14733.3 ms on follow A's last one: with the edge tolerance that is 8 frames, not 7.
        val fifteen = assetOf("f", durationMs = 15_000)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 240, assetId = "f", sourceInMs = 266).copy(reverse = true),
                clipOf("B", 240, 120, assetId = "v").copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 45)),
            ),
            media = listOf(fifteen, video),
        )

        val plan = buildPlan(doc)

        assertEquals(
            listOf(VideoSegment("A", "f", false, 240, 248, 14_734, 1f, null, reversed = true, clipStartFrame = 0)),
            plan.layers.single { it.isHandles }.segments.filter { it.showsPicture },
        )
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 8), plan.layers.first().segments.single { it.clipId == "B" }.transition)
        val span = TransitionHandles.span(doc, doc.timeline.clip("B")!!, 30)!!
        assertEquals(Triple(45, 8, 8), Triple(span.wanted, span.tailLeft, span.frames))
    }
}
