package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.easedProgress
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Keyframe easing (Resolve's ease in / ease out): the curve, keeping it through edits, and playback. */
class KeyframeEaseTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()
    private val fps = 30

    private val from = ClipTransform(scalePercent = 20, positionX = -900, positionY = 500, rotationDegrees = -180, opacityPercent = 0)
    private val to = ClipTransform(scalePercent = 380, positionX = 900, positionY = -500, rotationDegrees = 180, opacityPercent = 100)

    /** 120 frames that move from [from] at frame 0 to [to] at frame 60, eased as [leave] / [arrive] say. */
    private fun moving(leave: Boolean, arrive: Boolean): Clip = clipOf("A", 30, 120).copy(
        keyframes = listOf(
            TransformKeyframe(0, from, KeyEase.LINEAR.with(arriving = false, on = leave)),
            TransformKeyframe(60, to, KeyEase.LINEAR.with(arriving = true, on = arrive)),
        ),
    )

    @Test
    fun `easing slows the motion next to a keyframe, both sides together make an S curve`() {
        assertEquals("linear", 25, moving(leave = false, arrive = false).transformAt(15).opacityPercent)
        assertEquals("starts from rest", 11, moving(leave = true, arrive = false).transformAt(15).opacityPercent)
        assertEquals("slows into the second keyframe", 30, moving(leave = false, arrive = true).transformAt(15).opacityPercent)
        assertEquals("ease in and out", 16, moving(leave = true, arrive = true).transformAt(15).opacityPercent)
        assertEquals("symmetric", 50, moving(leave = true, arrive = true).transformAt(30).opacityPercent)
        assertEquals("ends exactly on the keyframe", to, moving(leave = true, arrive = true).transformAt(60))

        for (leave in listOf(false, true)) for (arrive in listOf(false, true)) {
            val values = (0..60).map { moving(leave, arrive).transformAt(it).positionX }
            assertTrue("never moves backwards ($leave, $arrive)", values.zipWithNext().all { (a, b) -> b >= a })
        }
        assertEquals(0f, easedProgress(0f, 0f, 0f), 0f)
        assertEquals(1f, easedProgress(1f, 0f, 0f), 1e-6f)
        assertEquals(0.37f, easedProgress(0.37f, 1f, 1f), 0f)
    }

    @Test
    fun `adding keyframes inside an eased stretch leaves the motion as it was`() {
        val eased = moving(leave = true, arrive = true)
        var clip = eased
        for (frame in listOf(17, 41, 52)) {
            val before = clip
            // Toggling adds a keyframe; so does keying one parameter on its own (rev 162: the scale's key alone at 52).
            val timeline = if (frame == 52) keys.toggleGroup(timelineOf(before), "A", frame, TransformKeyGroup.SCALE).timeline else keys.toggle(timelineOf(before), "A", frame).timeline
            clip = timeline.clips.single()
            (0 until 120).forEach { f -> assertClose("frame $f after adding $frame", before.transformAt(f), clip.transformAt(f)) }
        }
        assertEquals(listOf(0, 17, 41, 52, 60), clip.keyframes.map { it.frame })
        assertTrue("the outer keyframes still come to rest", clip.keyframes.first().ease.easesOut && clip.keyframes.last().ease.easesIn)
        assertTrue("a split-born keyframe neither rests nor moves evenly", clip.keyframes[1].ease.let { !it.easesIn && it.slopeIn != 1f })
    }

    @Test
    fun `split, trim and speed through an eased stretch keep every frame`() {
        val eased = moving(leave = true, arrive = false)
        val split = SplitClipUseCase(SequentialIds())(timelineOf(eased), atFrame = 30 + 23, selectedClipId = "A", fps = fps).timeline
        val left = split.clips.single { it.id == "A" }
        val right = split.clips.single { it.id != "A" }
        (0 until 23).forEach { f -> assertClose("left $f", eased.transformAt(f), left.transformAt(f)) }
        (0 until 97).forEach { f -> assertClose("right $f", eased.transformAt(23 + f), right.transformAt(f)) }

        val doc = projectOf(timeline = timelineOf(eased.copy(assetId = "v", sourceInMs = 2_000)), media = listOf(assetOf("v", durationMs = 60_000)))
        val head = TrimClipUseCase()(doc.timeline, "A", TrimEdge.START, 30 + 12, 0, false, 0, 60_000, fps).timeline.clips.single()
        (0 until 108).forEach { f -> assertClose("head-trimmed $f", eased.transformAt(12 + f), head.transformAt(f)) }

        // A stretch with both ends inside the same eased curve.
        val inner = eased.copy(keyframes = eased.keyframesFor(12, 33), durationFrames = 33)
        (0 until 33).forEach { f -> assertClose("inner $f", eased.transformAt(12 + f), inner.transformAt(f)) }

        val slow = props.speed(timelineOf(moving(leave = true, arrive = true)), "A", ClipSpeed.of(PlaybackSpeed.HALF)).clips.single()
        assertEquals("speed keeps the easing", listOf(true, true), listOf(slow.keyframes[0].ease.easesOut, slow.keyframes[1].ease.easesIn))
    }

    @Test
    fun `easing is switched per side at a keyframe and needs motion on that side`() {
        val timeline = timelineOf(moving(leave = false, arrive = false))

        assertEquals("第一个关键帧之前没有变化，无需缓入", assertThrows(EditRejected::class.java) { keys.ease(timeline, "A", 0, arriving = true) }.message)
        assertEquals("最后一个关键帧之后没有变化，无需缓出", assertThrows(EditRejected::class.java) { keys.ease(timeline, "A", 60, arriving = false) }.message)
        assertEquals("把播放头移到关键帧上，再设置缓入缓出", assertThrows(EditRejected::class.java) { keys.ease(timeline, "A", 30, arriving = true) }.message)
        assertThrows(EditRejected::class.java) { keys.ease(timelineOf(moving(false, false), locked = setOf(Timeline.V1)), "A", 0, arriving = false) }

        val out = keys.ease(timeline, "A", 0, arriving = false)
        assertEquals("已开启缓出 · 离开此关键帧时逐渐加速", out.message)
        assertEquals(KeyEase(slopeIn = 1f, slopeOut = 0f), out.timeline.clips.single().keyframes[0].ease)
        val inToo = keys.ease(out.timeline, "A", 60, arriving = true)
        assertEquals("已开启缓入 · 到达此关键帧时逐渐减速", inToo.message)
        assertEquals("已关闭缓出", keys.ease(inToo.timeline, "A", 0, arriving = false).message)

        // Changing the value at an eased keyframe keeps its easing.
        val edited = props.transform(inToo.timeline, "A", atFrame = 60) { it.copy(scalePercent = 100) }.clips.single()
        assertEquals(100, edited.keyframes[1].transform.scalePercent)
        assertTrue(edited.keyframes[1].ease.easesIn)
    }

    @Test
    fun `volume keyframes ease too, and keep their levels when keys are added or the clip is split`() {
        val swell = clipOf("S", 0, 120, track = Timeline.A1).copy(
            volumeKeyframes = listOf(VolumeKeyframe(30, -30f, KeyEase(slopeOut = 0f)), VolumeKeyframe(90, 0f, KeyEase(slopeIn = 0f))),
        )
        assertEquals("smoothstep a quarter of the way", -30f + 30f * 0.15625f, swell.volumeAt(45), 0.01f)

        val added = keys.toggleVolume(timelineOf(swell), "S", 50).timeline.clips.single()
        (0 until 120).forEach { f -> assertEquals("added $f", swell.volumeAt(f), added.volumeAt(f), 0.02f) }

        val split = SplitClipUseCase(SequentialIds())(timelineOf(swell), atFrame = 70, selectedClipId = "S", fps = fps).timeline
        val right = split.clips.single { it.id != "S" }
        (0 until 50).forEach { f -> assertEquals("right $f", swell.volumeAt(70 + f), right.volumeAt(f), 0.02f) }

        val toggled = keys.easeVolume(timelineOf(swell), "S", 90, arriving = true)
        assertEquals("已关闭缓入", toggled.message)
        assertThrows(EditRejected::class.java) { keys.easeVolume(timelineOf(swell), "S", 90, arriving = false) }
        assertThrows(EditRejected::class.java) { keys.easeVolume(timelineOf(clipOf("V", 0, 60)), "V", 0, arriving = false) }
    }

    @Test
    fun `the preview, export and mixer follow the same eased curves`() {
        val picture = moving(leave = true, arrive = true).copy(assetId = "v")
        val sound = clipOf("S", 30, 120, track = Timeline.A1).copy(
            volumeKeyframes = listOf(VolumeKeyframe(0, -30f, KeyEase(slopeOut = 0f)), VolumeKeyframe(60, 0f, KeyEase(slopeIn = 0f))),
        )
        val registry = LiveLookRegistry()
        registry.update(projectOf(timeline = timelineOf(picture, sound), media = listOf(assetOf("v"))))

        val look = registry.videoLook("A")!!
        val out = FloatArray(LiveLookRegistry.TRANSFORM_VALUES)
        for (f in listOf(5, 15, 30, 45, 59)) {
            look.transformAt(f.toFloat(), out)
            assertEquals("scale at $f", picture.transformAt(f).scalePercent / 100f, out[0], 0.01f)
            assertEquals("opacity at $f", picture.transformAt(f).opacityPercent / 100f, out[4], 0.01f)
        }
        val mix = registry.clipMix("S")!!
        for (f in listOf(0, 10, 15, 30, 50, 60)) {
            assertEquals("gain at $f", LiveLookRegistry.dbToGain(sound.volumeAt(f)), mix.gainAt(f * 1_000_000L / fps), 1e-3f)
        }
    }

    private fun assertClose(message: String, expected: ClipTransform, actual: ClipTransform) {
        val pairs = listOf(
            expected.scalePercent to actual.scalePercent,
            expected.positionX to actual.positionX,
            expected.positionY to actual.positionY,
            expected.rotationDegrees to actual.rotationDegrees,
            expected.opacityPercent to actual.opacityPercent,
        )
        assertTrue("$message: $expected vs $actual", pairs.all { (a, b) -> abs(a - b) <= 1 })
    }
}
