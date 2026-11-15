package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's audio cross fade: the sound before a cut plays on past it, fading out as the next one fades in. */
class AudioCrossFadesTest {

    private val fps = 30
    // Two songs on A2 (each 10 s long): A from 0 s of its file for 4 s, then B; A has 6 s of song left after its out point.
    private val song = assetOf("s", kind = MediaKind.AUDIO, durationMs = 10_000)
    private val a = clipOf("A", 0, 120, track = Timeline.A2, assetId = "s")
    private val b = clipOf("B", 120, 90, track = Timeline.A2, assetId = "s", sourceInMs = 2_000).copy(crossFadeFrames = 15)

    private fun doc(vararg clips: com.example.prismstudio.p40b43a88.domain.model.Clip) = projectOf(timeline = timelineOf(*clips), media = listOf(song))

    @Test
    fun `the sound ending at the cut plays on under the fade, as long as its file lasts`() {
        val cover = AudioCrossFades.coverage(doc(a, b), b, fps)!!
        assertEquals("A" to 15, cover.outgoing.id to cover.frames)
        // A used its file to 9.5 s: half a second (15 frames) is left, so a 30-frame fade shrinks to 15.
        val late = a.copy(sourceInMs = 5_500)
        val long = AudioCrossFades.coverage(doc(late, b.copy(crossFadeFrames = 30)), b.copy(crossFadeFrames = 30), fps)!!
        assertEquals(15 to true, long.frames to long.shortened)
        val used = a.copy(sourceInMs = 6_000)
        assertEquals("nothing left: a cut", 0, AudioCrossFades.coverage(doc(used, b), b, fps)!!.frames)
    }

    @Test
    fun `there is nothing to fade from without a sound ending right at the cut`() {
        assertNull("no fade set", AudioCrossFades.coverage(doc(a, b.copy(crossFadeFrames = 0)), b.copy(crossFadeFrames = 0), fps))
        val apart = b.copy(start = 130)
        assertNull("a gap before it", AudioCrossFades.coverage(doc(a, apart), apart, fps))
        assertNull("the sound before it is off", AudioCrossFades.coverage(doc(a.copy(enabled = false), b), b, fps))
        assertNull("a picture has no cross fade", AudioCrossFades.outgoing(doc(a, b), clipOf("V", 120, 90)))
    }

    @Test
    fun `the plan plays the outgoing sound on in one piece and fades the incoming one in on a second lane`() {
        val plan = BuildRenderPlanUseCase()(doc(a, b))
        val lanes = plan.audio.filter { it.trackId == Timeline.A2 }
        assertEquals("A and B roll", 2, lanes.size)
        // Split at the cut, A's sound would restart there: its decoder starts afresh and drops out for a moment.
        val outgoing = lanes[0].segments.single()
        assertEquals("A", outgoing.clipId)
        assertEquals("on past its end for the fade", 0 to 135, outgoing.startFrame to outgoing.endFrame)
        assertEquals(0L to 4_000L, outgoing.clipOffsetMs to outgoing.clipDurationMs)
        assertEquals(500L, outgoing.crossOutMs)
        val incoming = lanes[1].segments.single { it.clipId == "B" }
        assertEquals(120 to 210, incoming.startFrame to incoming.endFrame)
        assertEquals(500L, incoming.crossInMs)
        assertEquals("silence until the cut", listOf<String?>(null, "B"), lanes[1].segments.map { it.clipId })
        assertEquals("one lane without fades", 1, BuildRenderPlanUseCase()(doc(a, b.copy(crossFadeFrames = 0))).audio.count { it.trackId == Timeline.A2 })
    }

    @Test
    fun `cross fades in a row take turns on the two lanes`() {
        // A → B → C, B fading from A and C from B: B plays on under C's fade, so C goes back to A's lane.
        val c = clipOf("C", 210, 60, track = Timeline.A2, assetId = "s", sourceInMs = 7_000).copy(crossFadeFrames = 30)
        val lanes = BuildRenderPlanUseCase()(doc(a, b, c)).audio.filter { it.trackId == Timeline.A2 }
        assertEquals(listOf(listOf("A", "C"), listOf("B")), lanes.map { lane -> lane.segments.mapNotNull { it.clipId } })
        val first = lanes[0].segments
        assertEquals("A to 4.5 s, silence until C", listOf(0 to 135, 135 to 210, 210 to 270), first.map { it.startFrame to it.endFrame })
        assertEquals("B plays its 30 frames on into C", 120 to 240, lanes[1].segments.last().let { it.startFrame to it.endFrame })
        assertEquals(1_000L, lanes[1].segments.last().crossOutMs)
        // A render that starts inside A's fade picks A up where the fade is.
        val late = BuildRenderPlanUseCase()(doc(a, b), rangeStart = 125).audio.filter { it.trackId == Timeline.A2 }
        val tail = late[0].segments.single()
        assertEquals("A" to (0 to 10), tail.clipId to (tail.startFrame to tail.endFrame))
        assertEquals("5 frames into the fade, past A's end", 4_166L, tail.clipOffsetMs)
        assertEquals("B straight away on the other lane", "B" to 0, late[1].segments.first().let { it.clipId to it.startFrame })
    }

    @Test
    fun `a split keeps the fade on the piece that starts with it, and a paste drops it`() {
        val split = SplitClipUseCase(com.example.prismstudio.p40b43a88.domain.SequentialIds())(timelineOf(a, b), 150, "B", fps).timeline
        assertEquals(listOf(15, 0), split.clipsOn(Timeline.A2).filter { it.start >= 120 }.map { it.crossFadeFrames })
        val clipboard = ClipboardUseCase(com.example.prismstudio.p40b43a88.domain.SequentialIds())
        val pasted = clipboard.paste(timelineOf(a, b), clipboard.copy(timelineOf(a, b), "B"), 300, fps)
        assertEquals(0, pasted.timeline.clip(pasted.selectedClipId)!!.crossFadeFrames)
    }
}
