package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placement presets, opening-transition length and moving clips between video tracks. */
class LayerEditingTest {

    private val props = ClipPropertiesUseCase()
    private val decorate = DecorateClipUseCase(SequentialIds())
    private val move = MoveClipUseCase()
    private val fps = 30

    @Test
    fun `a corner preset makes a 35 percent inset picture and full frame restores it`() {
        val tilted = clipOf("A", 0, 90).copy(transform = ClipTransform(scalePercent = 80, rotationDegrees = 12, opacityPercent = 60))
        val timeline = timelineOf(tilted)

        val corner = props.place(timeline, "A", Placement.TOP_RIGHT).clip("A")!!.transform
        assertEquals(ClipTransform(scalePercent = 35, positionX = 576, positionY = 303, rotationDegrees = 0, opacityPercent = 60), corner)
        assertEquals("the preset is recognised again", Placement.TOP_RIGHT, Placement.of(corner))

        val full = props.place(timelineOf(tilted.copy(transform = corner)), "A", Placement.FULL).clip("A")!!.transform
        assertEquals(ClipTransform(opacityPercent = 60), full)
        assertEquals(Placement.FULL, Placement.of(full))
        assertNull("a hand-tuned transform is no preset", Placement.of(ClipTransform(scalePercent = 50)))
    }

    @Test
    fun `presets stay inside the frame and only apply to picture clips on unlocked tracks`() {
        Placement.entries.forEach { p ->
            val halfWidth = 1920 * p.scalePercent / 200
            val halfHeight = 1080 * p.scalePercent / 200
            assertTrue("$p overflows horizontally", kotlin.math.abs(p.positionX) + halfWidth <= 960)
            assertTrue("$p overflows vertically", kotlin.math.abs(p.positionY) + halfHeight <= 540)
        }
        assertThrows(EditRejected::class.java) { props.place(timelineOf(clipOf("S", 0, 90, track = Timeline.A1)), "S", Placement.TOP_LEFT) }
        assertThrows(EditRejected::class.java) {
            props.place(timelineOf(clipOf("A", 0, 90), locked = setOf(Timeline.V1)), "A", Placement.TOP_LEFT)
        }
    }

    @Test
    fun `a transition's length is kept between one frame and two seconds or half the clip`() {
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val long = timelineOf(clipOf("A", 0, 300).copy(transitionIn = dissolve))
        val short = timelineOf(clipOf("B", 0, 40).copy(transitionIn = dissolve))

        assertEquals(24, decorate.setTransitionFrames(long, "A", 24, fps).clip("A")!!.transitionIn!!.frames)
        assertEquals("two seconds at most", 60, decorate.setTransitionFrames(long, "A", 500, fps).clip("A")!!.transitionIn!!.frames)
        assertEquals("never more than half the clip", 20, decorate.setTransitionFrames(short, "B", 500, fps).clip("B")!!.transitionIn!!.frames)
        assertEquals(1, decorate.setTransitionFrames(long, "A", 0, fps).clip("A")!!.transitionIn!!.frames)
        assertEquals(TransitionType.CROSS_DISSOLVE, decorate.setTransitionFrames(long, "A", 24, fps).clip("A")!!.transitionIn!!.type)
    }

    @Test
    fun `removing a transition leaves a plain cut, and clips without one are refused`() {
        val timeline = timelineOf(clipOf("A", 0, 90).copy(transitionIn = Transition(TransitionType.WIPE, 15)), clipOf("B", 90, 90))

        val result = decorate.removeTransition(timeline, "A")

        assertNull(result.timeline.clip("A")!!.transitionIn)
        assertEquals("已移除转场：划像", result.message)
        assertThrows(EditRejected::class.java) { decorate.removeTransition(timeline, "B") }
        assertThrows(EditRejected::class.java) { decorate.setTransitionFrames(timeline, "B", 10, fps) }
    }

    @Test
    fun `a clip moves to the video track above or below at the same time, its sound stays put`() {
        val timeline = timelineOf(
            clipOf("A", 30, 60, linkId = "L"),
            clipOf("As", 30, 60, track = Timeline.A1, linkId = "L"),
        )

        val up = move.toAdjacentTrack(timeline, "A", up = true, fps = fps)
        assertEquals(Timeline.V2, up.timeline.clip("A")!!.trackId)
        assertEquals(30, up.timeline.clip("A")!!.start)
        assertEquals("linked sound keeps its track and time", timeline.clip("As"), up.timeline.clip("As"))
        assertEquals("已将“A”移到 V2", up.message)

        val down = move.toAdjacentTrack(up.timeline, "A", up = false, fps = fps)
        assertEquals(Timeline.V1, down.timeline.clip("A")!!.trackId)
    }

    @Test
    fun `there is no track above V2 or below V1, and an occupied spot is refused`() {
        val timeline = timelineOf(clipOf("A", 0, 60), clipOf("B", 30, 60, track = Timeline.V2))

        assertThrows(EditRejected::class.java) { move.toAdjacentTrack(timeline, "B", up = true, fps = fps) }
        assertThrows(EditRejected::class.java) { move.toAdjacentTrack(timeline, "A", up = false, fps = fps) }
        assertThrows("B overlaps A's time on V2", EditRejected::class.java) { move.toAdjacentTrack(timeline, "A", up = true, fps = fps) }
    }
}
