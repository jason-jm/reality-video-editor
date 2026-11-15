package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transform keyframes: interpolation, editing, and keeping the animation through cuts. */
class KeyframeTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()
    private val fps = 30

    private val small = ClipTransform(scalePercent = 50, positionX = -400, opacityPercent = 0)
    private val big = ClipTransform(scalePercent = 150, positionX = 400, opacityPercent = 100)

    /** 100 frames that grow from [small] at frame 20 to [big] at frame 60. */
    private val animated = clipOf("A", 30, 100).copy(keyframes = listOf(TransformKeyframe(20, small), TransformKeyframe(60, big)))

    @Test
    fun `values move linearly between keyframes and hold outside them`() {
        assertEquals(small, animated.transformAt(0))
        assertEquals(small, animated.transformAt(20))
        assertEquals(ClipTransform(scalePercent = 100, positionX = 0, opacityPercent = 50), animated.transformAt(40))
        assertEquals(big, animated.transformAt(99))
        assertEquals("without keyframes the one transform applies", ClipTransform(), clipOf("B", 0, 10).transformAt(5))
    }

    @Test
    fun `toggling adds a keyframe holding the current value, and removing the last one keeps that value`() {
        val timeline = timelineOf(clipOf("A", 30, 100).copy(transform = small))

        val added = keys.toggle(timeline, "A", 10)
        assertEquals(listOf(TransformKeyframe(10, small)), added.timeline.clip("A")!!.keyframes)
        assertEquals("已添加关键帧 · 共 1 个", added.message)

        val second = props.transform(added.timeline, "A", atFrame = 70) { it.copy(scalePercent = 200) }
        assertEquals(small.copy(scalePercent = 200), second.clip("A")!!.transformAt(70))
        assertEquals(small.copy(scalePercent = 125), second.clip("A")!!.transformAt(40))

        val removed = keys.toggle(keys.toggle(second, "A", 10).timeline, "A", 70)
        assertTrue(removed.timeline.clip("A")!!.keyframes.isEmpty())
        assertEquals("the picture keeps the last keyed value", small.copy(scalePercent = 200), removed.timeline.clip("A")!!.transform)
        assertEquals("已删除关键帧，片段不再有动画", removed.message)
    }

    @Test
    fun `a keyed clip is only edited at a frame inside it, and keyframes need an unlocked picture clip`() {
        val timeline = timelineOf(animated)

        assertThrows(EditRejected::class.java) { props.transform(timeline, "A", atFrame = 100) { it.copy(scalePercent = 80) } }
        assertThrows(EditRejected::class.java) { props.transform(timeline, "A", atFrame = null) { it.copy(scalePercent = 80) } }
        assertThrows(EditRejected::class.java) { keys.toggle(timeline, "A", -1) }
        assertThrows(EditRejected::class.java) { keys.toggle(timelineOf(clipOf("S", 0, 60, track = Timeline.A1)), "S", 5) }
        assertThrows(EditRejected::class.java) { keys.toggle(timelineOf(animated, locked = setOf(Timeline.V1)), "A", 5) }
        assertEquals("presets are keyed too", 35, props.place(timeline, "A", Placement.TOP_LEFT, atFrame = 40).clip("A")!!.keyframes.single { it.frame == 40 }.transform.scalePercent)
    }

    @Test
    fun `previous and next walk the keyframes`() {
        assertNull(keys.previous(animated, 20))
        assertEquals(20, keys.previous(animated, 40))
        assertEquals(60, keys.next(animated, 20))
        assertNull(keys.next(animated, 60))
    }

    @Test
    fun `a split keeps every frame's value on both halves`() {
        val timeline = timelineOf(animated)
        val split = SplitClipUseCase(SequentialIds())(timeline, atFrame = 30 + 40, selectedClipId = "A", fps = fps).timeline

        val left = split.clip("A")!!
        val right = split.clips.single { it.id != "A" }
        (0 until 40).forEach { f -> assertClose("left frame $f", animated.transformAt(f), left.transformAt(f)) }
        (0 until 60).forEach { f -> assertClose("right frame $f", animated.transformAt(40 + f), right.transformAt(f)) }
    }

    @Test
    fun `trimming either edge keeps the animation on the picture it belongs to`() {
        val trim = TrimClipUseCase()
        val doc = projectOf(timeline = timelineOf(animated.copy(assetId = "v", sourceInMs = 5_000)), media = listOf(assetOf("v", durationMs = 60_000)))

        fun trimmed(edge: TrimEdge, frame: Int) =
            trim(doc.timeline, "A", edge, frame, snapThreshold = 0, snapping = false, playhead = 0, sourceDurationMs = 60_000, fps = fps)
                .timeline.clip("A")!!

        val head = trimmed(TrimEdge.START, 30 + 30)
        assertEquals(70, head.durationFrames)
        (0 until 70).forEach { f -> assertClose("head-trimmed frame $f", animated.transformAt(30 + f), head.transformAt(f)) }

        val tail = trimmed(TrimEdge.END, 30 + 40)
        assertEquals(40, tail.durationFrames)
        (0 until 40).forEach { f -> assertClose("tail-trimmed frame $f", animated.transformAt(f), tail.transformAt(f)) }
    }

    @Test
    fun `a speed change stretches the keyframes with the clip`() {
        val doubled = props.speed(timelineOf(animated), "A", ClipSpeed.of(PlaybackSpeed.HALF)).clip("A")!!

        assertEquals(200, doubled.durationFrames)
        assertEquals(listOf(40, 120), doubled.keyframes.map { it.frame })
    }

    @Test
    fun `title cues animate from their clip's start`() {
        val title = titleClipOf("T", 30, 90).copy(title = TitleStyle("Hi"), keyframes = animated.keyframes)
        val cue = BuildRenderPlanUseCase()(projectOf(timeline = timelineOf(title)), rangeStart = 40).titles.single()

        assertEquals(-10, cue.clipStartFrame)
        assertEquals(title.transformAt(40), cue.transformAt(30))
    }

    @Test
    fun `segments remember where their clip starts, also when the range begins inside it`() {
        val doc = projectOf(timeline = timelineOf(animated.copy(assetId = "v")), media = listOf(assetOf("v", durationMs = 60_000)))

        val segment = BuildRenderPlanUseCase()(doc, rangeStart = 50).layers.single().segments.single { it.clipId == "A" }

        assertEquals(0, segment.startFrame)
        assertEquals(-20, segment.clipStartFrame)
    }

    private fun Timeline.clip(id: String): Clip? = clips.firstOrNull { it.id == id }

    /** Keyframes added at a cut hold whole units, so frames after it may differ by one unit. */
    private fun assertClose(message: String, expected: ClipTransform, actual: ClipTransform) {
        val pairs = listOf(
            expected.scalePercent to actual.scalePercent,
            expected.positionX to actual.positionX,
            expected.positionY to actual.positionY,
            expected.rotationDegrees to actual.rotationDegrees,
            expected.opacityPercent to actual.opacityPercent,
        )
        assertTrue("$message: $expected vs $actual", pairs.all { (a, b) -> kotlin.math.abs(a - b) <= 1 })
    }
}
