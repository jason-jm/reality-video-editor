package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.usecase.BuildRenderPlanUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlanTest {

    private val buildPlan = BuildRenderPlanUseCase()
    private val video = assetOf("v", durationMs = 20_000)
    private val still = assetOf("img", kind = MediaKind.IMAGE, name = "img.png")

    @Test
    fun `flattening keeps the top-most clip and resumes the clip underneath at the right source time`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 100, assetId = "v", sourceInMs = 1_000).copy(transitionIn = dissolve),
                clipOf("B", 30, 30, track = Timeline.V2, assetId = "img"),
            ),
            media = listOf(video, still),
        )
        val plan = buildPlan(doc)
        assertTrue(plan.isLayered)

        val flat = plan.flattened()

        assertFalse(flat.isLayered)
        assertEquals(
            listOf(
                VideoSegment("A", "v", false, 0, 30, 1_000, 1f, dissolve),
                VideoSegment("B", "img", true, 30, 60, 0, 1f, null),
                // A resumes inside itself: its keyframes still count from frame 0.
                VideoSegment("A", "v", false, 60, 100, 3_000, 1f, null, clipStartFrame = 0),
            ),
            flat.layers.single().segments,
        )
        assertEquals(plan.audio, flat.audio)
        assertEquals(plan.titles, flat.titles)
    }

    @Test
    fun `a transition's style leaves the player as it is, its kind and length do not`() {
        fun signature(transition: Transition) = buildPlan(
            projectOf(
                timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v", sourceInMs = 8_000).copy(transitionIn = transition)),
                media = listOf(video),
            ),
        ).structureSignature()
        val wipe = Transition(TransitionType.WIPE, 15)
        val base = signature(wipe)
        assertEquals(
            "ease, direction, edge and border are read live",
            base,
            signature(wipe.copy(ease = TransitionEase.IN_OUT, reverse = true, softness = 40, border = 20, borderColor = TitleColor.RED)),
        )
        assertTrue(base != signature(wipe.copy(type = TransitionType.IRIS)))
        assertTrue(base != signature(wipe.copy(frames = 20)))
        assertTrue("where it sits around the cut moves the pictures", base != signature(wipe.copy(alignment = TransitionAlignment.CENTER)))
    }

    @Test
    fun `flattening drops transition handles and leaves single-layer plans as they are`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("B", 60, 60, assetId = "v").copy(transitionIn = dissolve)),
            media = listOf(video),
        )
        val plan = buildPlan(doc)
        assertEquals("track plus handles", 2, plan.layers.size)

        val flat = plan.flattened()

        assertEquals(
            listOf(VideoSegment("A", "v", false, 0, 60, 0, 1f, null), VideoSegment("B", "v", false, 60, 120, 0, 1f, dissolve)),
            flat.layers.single().segments,
        )
        assertSame("nothing to flatten", flat, flat.flattened())
    }

    @Test
    fun `each layer plays alone with titles on the top layer and the sound on the bottom one`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 100, assetId = "v"),
                clipOf("B", 30, 30, track = Timeline.V2, assetId = "img"),
                clipOf("X", 0, 100, track = Timeline.A1, assetId = "v"),
                com.example.prismstudio.p40b43a88.domain.titleClipOf("T", 10, 20, track = Timeline.V2),
            ),
            media = listOf(video, still),
        )
        val plan = buildPlan(doc)
        assertEquals(listOf(Timeline.V2, Timeline.V1), plan.layers.map { it.id })

        val top = plan.forLayer(0)
        val bottom = plan.forLayer(1)

        assertEquals(listOf(Timeline.V2), top.layers.map { it.id })
        assertEquals(plan.titles, top.titles)
        assertTrue("sound plays once, on the bottom layer", top.audio.isEmpty())
        assertEquals(listOf(Timeline.V1), bottom.layers.map { it.id })
        assertEquals(plan.audio, bottom.audio)
        assertTrue(bottom.titles.isEmpty())
        assertFalse(top.isLayered || bottom.isLayered)
        assertEquals(plan.durationFrames, top.durationFrames)
    }

    @Test
    fun `a layered picture renders one layer per pass, bottom first, with sound and titles on the last pass`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 60, assetId = "v"),
                clipOf("B", 60, 60, assetId = "v").copy(transitionIn = dissolve),
                clipOf("P", 30, 30, track = Timeline.V2, assetId = "img"),
                clipOf("X", 0, 120, track = Timeline.A1, assetId = "v"),
                com.example.prismstudio.p40b43a88.domain.titleClipOf("T", 10, 20, track = Timeline.V2),
            ),
            media = listOf(video, still),
        )
        val plan = buildPlan(doc)
        assertEquals(listOf(Timeline.V2, Timeline.V1, "${Timeline.V1}-handles"), plan.layers.map { it.id })

        val passes = plan.stackingPasses()

        assertEquals(listOf("${Timeline.V1}-handles", Timeline.V1, Timeline.V2), passes.map { it.layers.single().id })
        assertTrue(passes.dropLast(1).all { it.audio.isEmpty() && it.titles.isEmpty() })
        assertEquals(plan.audio, passes.last().audio)
        assertEquals(plan.titles, passes.last().titles)
        assertTrue(passes.all { it.durationFrames == plan.durationFrames && !it.isLayered })
    }

    @Test
    fun `layers without a picture get no pass and a single-layer plan is its own pass`() {
        val doc = projectOf(timeline = timelineOf(clipOf("A", 0, 60, assetId = "v"), clipOf("O", 0, 60, track = Timeline.V2, assetId = "gone")), media = listOf(video))
        val plan = buildPlan(doc)
        assertEquals("the offline clip keeps its layer", 2, plan.layers.size)

        val passes = plan.stackingPasses()

        assertEquals(listOf(Timeline.V1), passes.map { it.layers.single().id })
        assertEquals(plan.audio, passes.single().audio)
        val single = plan.forLayer(1)
        assertSame(single, single.stackingPasses().single())
    }

    @Test
    fun `a segment knows the source time it shows at any frame, at any speed`() {
        val segment = VideoSegment("A", "v", false, 30, 90, 2_000, 2f, null)

        assertEquals(2_000, segment.sourceAtFrame(30, 30))
        assertEquals(4_000, segment.sourceAtFrame(60, 30))
        assertTrue(segment.showsPicture)
        assertFalse(segment.copy(assetId = null).showsPicture)
    }
    /** A layer over a 300-frame plan: [spans] (clip id, from, until) with gaps between. */
    private fun layer(id: String, vararg spans: Triple<String, Int, Int>, handles: Boolean = false): VideoLayer {
        val segments = mutableListOf<VideoSegment>()
        var at = 0
        for ((clip, from, until) in spans) {
            if (from > at) segments += VideoSegment(null, null, false, at, from, 0, 1f, null)
            segments += VideoSegment(clip, "v", false, from, until, 0, 1f, null)
            at = until
        }
        if (at < 300) segments += VideoSegment(null, null, false, at, 300, 0, 1f, null)
        return VideoLayer(id, segments, handles)
    }

    private fun planOf(vararg layers: VideoLayer) = RenderPlan(30, 1920, 1080, 0, 300, layers.toList(), emptyList(), emptyList())

    @Test
    fun `more layers than the monitor stacks share players where the fewest frames hide, the base picture kept apart`() {
        // Five picture-in-pictures over the whole first 90 frames, a full-frame base under them (V6 … V1).
        val pips = (6 downTo 2).map { layer("v$it", Triple("P$it", 0, 90)) }
        val stacked = planOf(*(pips + layer("v1", Triple("B", 0, 300))).toTypedArray())
        val (grouped, hidden) = stacked.grouped(4)
        assertEquals(4, grouped.layers.size)
        assertEquals("two pictures hidden for 90 frames each", 180, hidden)
        assertEquals("the base and the two lowest overlays keep their own players", listOf("v4", "v3", "v2", "v1"), grouped.layers.map { it.id })
        assertEquals("the top one wins in the shared player", "P6", grouped.layers[0].segments.first().clipId)
        assertSame("few enough already", stacked, stacked.grouped(6).first)
        assertEquals(0, stacked.grouped(6).second)
    }

    @Test
    fun `layers that never overlap in time share a player exactly, handles dropped from a shared one count as hidden`() {
        // B-roll on V2…V6 one after another over the base: four players show it all.
        val broll = (6 downTo 2).map { n -> layer("v$n", Triple("R$n", (6 - n) * 50, (6 - n) * 50 + 40)) }
        val plan = planOf(*(broll + layer("v1", Triple("B", 0, 300))).toTypedArray())
        val (grouped, hidden) = plan.grouped(4)
        assertEquals(0, hidden)
        val shared = grouped.layers.first()
        assertEquals(listOf("R6", "R5", "R4"), shared.segments.mapNotNull { it.clipId })
        assertEquals(listOf(0, 40, 50, 90, 100, 140), shared.segments.filter { it.clipId != null }.flatMap { listOf(it.startFrame, it.endFrame) })
        // A dissolve's handles under V1: kept on their own while there is room, else the cheapest thing to give up.
        val withHandles = planOf(layer("v3", Triple("C", 0, 90)), layer("v2", Triple("D", 0, 90)), layer("v1", Triple("A", 0, 300)),
            layer("h1", Triple("H", 100, 110), handles = true))
        assertEquals(0, withHandles.grouped(4).second)
        val (three, lost) = withHandles.grouped(3)
        assertEquals("the 10 handle frames rather than 90 of a picture", 10, lost)
        assertEquals(listOf("v3", "v2", "v1"), three.layers.map { it.id })
        assertEquals(1, withHandles.grouped(1).first.layers.size)
    }
}
