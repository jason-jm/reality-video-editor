package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ReverseMapping
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reversed clips read the asset's reversed copy, which mirrors the source around its duration. */
class ReversePlanTest {

    private val buildPlan = BuildRenderPlanUseCase()
    private val video = assetOf("v", durationMs = 20_000)

    @Test
    fun `a reversed clip and its linked sound start at the mirror image of their out point`() {
        // 3 s from 4 s into a 20 s source: forward 4–7 s, so the reversed copy plays 13–16 s.
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 90, assetId = "v", sourceInMs = 4_000, linkId = "L").copy(reverse = true),
                clipOf("As", 0, 90, track = Timeline.A1, assetId = "v", sourceInMs = 4_000, linkId = "L").copy(reverse = true),
            ),
            media = listOf(video),
        )

        val plan = buildPlan(doc)
        val picture = plan.layers.single().segments.single()
        val sound = plan.audio.single().segments.single()

        assertTrue(picture.reversed)
        assertEquals(13_000, picture.sourceInMs)
        assertTrue(sound.reversed)
        assertEquals(13_000, sound.sourceInMs)
        assertEquals(setOf("v"), plan.reversedAssetIds())
    }

    @Test
    fun `a range starting inside a reversed clip moves forward through the reversed copy`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 90, assetId = "v", sourceInMs = 4_000).copy(reverse = true)),
            media = listOf(video),
        )

        val segment = buildPlan(doc, rangeStart = 30).layers.single().segments.single()

        assertEquals("one second later in the copy = one second earlier in the source", 14_000, segment.sourceInMs)
        assertEquals(segment.sourceInMs + 1_000, segment.sourceAtFrame(segment.startFrame + 30, 30))
    }

    @Test
    fun `speed changes widen the mirrored span`() {
        val doc = projectOf(
            timeline = timelineOf(clipOf("A", 0, 90, assetId = "v", sourceInMs = 4_000, speed = PlaybackSpeed.DOUBLE).copy(reverse = true)),
            media = listOf(video),
        )

        assertEquals("forward 4–10 s, so 10–16 s in the copy", 10_000, buildPlan(doc).layers.single().segments.single().sourceInMs)
    }

    @Test
    fun `the handle of a reversed outgoing clip continues backwards, as far as the source before its in point`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        fun planWith(sourceInMs: Long) = buildPlan(
            projectOf(
                timeline = timelineOf(
                    clipOf("A", 0, 90, assetId = "v", sourceInMs = sourceInMs).copy(reverse = true),
                    clipOf("B", 90, 90, assetId = "v", sourceInMs = 10_000).copy(transitionIn = dissolve),
                ),
                media = listOf(video),
            ),
        )

        val handle = planWith(4_000).layers.single { it.isHandles }.segments.single { it.clipId == "A" }
        assertTrue(handle.reversed)
        assertEquals("continues right after the clip's 13–16 s stretch of the copy", 16_000, handle.sourceInMs)
        assertEquals(15, handle.frames)

        val short = planWith(200)
        assertEquals("only 200 ms of source lie before the in point", 6, short.layers.single { it.isHandles }.segments.single { it.clipId == "A" }.frames)
        assertEquals("so the dissolve takes 6 frames", 6, short.layers.first().segments.single { it.clipId == "B" }.transition?.frames)
    }

    @Test
    fun `stills and forward clips never ask for a reversed copy`() {
        val still = assetOf("img", kind = MediaKind.IMAGE, name = "img.png")
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("S", 0, 60, assetId = "img").copy(reverse = true),
                clipOf("F", 60, 60, assetId = "v", sourceInMs = 1_000),
            ),
            media = listOf(still, video),
        )

        val plan = buildPlan(doc)

        assertFalse(plan.layers.single().segments.any { it.reversed })
        assertEquals(1_000, plan.layers.single().segments.single { it.clipId == "F" }.sourceInMs)
        assertTrue(plan.reversedAssetIds().isEmpty())
    }

    @Test
    fun `mirroring a span twice gives it back`() {
        val mirrored = ReverseMapping.mirrorStart(20_000, 4_000, 3_000)

        assertEquals(13_000, mirrored)
        assertEquals(4_000, ReverseMapping.mirrorStart(20_000, mirrored, 3_000))
        assertEquals("never before the start of the copy", 0, ReverseMapping.mirrorStart(5_000, 4_000, 3_000))
    }
}
