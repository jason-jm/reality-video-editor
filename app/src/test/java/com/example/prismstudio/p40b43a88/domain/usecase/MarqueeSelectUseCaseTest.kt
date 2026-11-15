package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Test

class MarqueeSelectUseCaseTest {

    private val marquee = MarqueeSelectUseCase()
    // A (picture) with its sound As at 0–60, then B at 60–120; a title T on V2 at 30–90; music M on A2 at 0–150.
    private val timeline = timelineOf(
        clipOf("A", 0, 60, linkId = "L"),
        clipOf("As", 0, 60, track = Timeline.A1, linkId = "L"),
        clipOf("B", 60, 60),
        titleClipOf("T", 30, 60),
        clipOf("M", 0, 150, track = Timeline.A2),
    )
    private val all = setOf(Timeline.V2, Timeline.V1, Timeline.A1, Timeline.A2)

    @Test
    fun `the box takes the clips it touches on its tracks, in timeline order, a linked pair once`() {
        // Frames 50–70 over V2 and V1: the end of A, the start of B, and T across it.
        assertEquals(listOf("A", "T", "B"), marquee.select(timeline, 50, 70, setOf(Timeline.V2, Timeline.V1), emptyList()))
        // Down to the sound tracks too: A's sound comes with A (once), the music is its own clip.
        assertEquals(listOf("A", "M", "T", "B"), marquee.select(timeline, 50, 70, all, emptyList()))
        // Over the sound alone the pair is selected by its sound.
        assertEquals(listOf("As"), marquee.select(timeline, 10, 20, setOf(Timeline.A1), emptyList()))
    }

    @Test
    fun `a clip must overlap the box by part of a frame`() {
        // A ends at 60 and B starts there: a box from 60 to 61 touches B only.
        assertEquals(listOf("B"), marquee.select(timeline, 60, 61, setOf(Timeline.V1), emptyList()))
        assertEquals(listOf("A"), marquee.select(timeline, 59, 60, setOf(Timeline.V1), emptyList()))
        assertEquals("past the last clip nothing is touched", emptyList<String>(), marquee.select(timeline, 150, 200, all, emptyList()))
    }

    @Test
    fun `the box gathers onto the selection and skips locked tracks`() {
        // B already selected: the box adds A and T, keeps B first (the clip the inspector shows).
        assertEquals(listOf("B", "A", "T"), marquee.select(timeline, 0, 100, setOf(Timeline.V2, Timeline.V1), listOf("B")))
        // A selected by its sound: the box over A adds nothing for that pair.
        assertEquals(listOf("As", "B"), marquee.select(timeline, 0, 100, setOf(Timeline.V1), listOf("As")))
        // Nothing behind a lock: with V1 locked only the title is taken.
        val locked = timeline.copy(tracks = timeline.tracks.map { it.copy(locked = it.id == Timeline.V1) })
        assertEquals(listOf("T"), marquee.select(locked, 0, 100, setOf(Timeline.V2, Timeline.V1), emptyList()))
        assertEquals(
            listOf("T"),
            MarqueeSelectUseCase.touched(locked.clips, locked.tracks, 0, 100, setOf(Timeline.V2, Timeline.V1)).map { it.id },
        )
    }
}
