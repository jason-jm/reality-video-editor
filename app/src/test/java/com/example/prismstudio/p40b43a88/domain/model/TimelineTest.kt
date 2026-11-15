package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private val late = clipOf("late", 200, 50)
    private val early = clipOf("early", 0, 100)
    private val sound = clipOf("sound", 50, 250, track = Timeline.A2)
    private val timeline = timelineOf(late, early, sound, titleClipOf("title", 20, 40))

    @Test
    fun `duration is the end of the last clip on any track`() {
        assertEquals(0, Timeline.EMPTY.durationFrames)
        assertEquals(300, timeline.durationFrames)
    }

    @Test
    fun `clip and track lookups tolerate unknown and null ids`() {
        assertEquals(early, timeline.clip("early"))
        assertNull(timeline.clip("ghost"))
        assertNull(timeline.clip(null))
        assertEquals(TrackKind.AUDIO, timeline.track(Timeline.A2)?.kind)
        assertNull(timeline.track(null))
    }

    @Test
    fun `clipsOn lists one track ordered by start`() {
        assertEquals(listOf(early, late), timeline.clipsOn(Timeline.V1))
        assertEquals(listOf(sound), timeline.clipsOn(Timeline.A2))
        assertTrue(timeline.clipsOn(Timeline.A1).isEmpty())
    }

    @Test
    fun `replaceClip swaps only the clip with the same id and keeps the order`() {
        val moved = early.copy(start = 120)

        val updated = timeline.replaceClip(moved)

        assertEquals(listOf("late", "early", "sound", "title"), updated.clips.map { it.id })
        assertEquals(moved, updated.clip("early"))
        assertEquals(timeline.clips.filter { it.id != "early" }, updated.clips.filter { it.id != "early" })
    }

    @Test
    fun `the default layout stacks V2 above V1 then the two audio tracks`() {
        val tracks = Timeline.defaultTracks()

        assertEquals(listOf(Timeline.V2, Timeline.V1, Timeline.A1, Timeline.A2), tracks.map { it.id })
        assertEquals(listOf(TrackKind.VIDEO, TrackKind.VIDEO, TrackKind.AUDIO, TrackKind.AUDIO), tracks.map { it.kind })
        assertTrue(tracks.all { it.enabled && !it.locked })
        assertTrue(Timeline.EMPTY.clips.isEmpty() && Timeline.EMPTY.markers.isEmpty())
    }
}
