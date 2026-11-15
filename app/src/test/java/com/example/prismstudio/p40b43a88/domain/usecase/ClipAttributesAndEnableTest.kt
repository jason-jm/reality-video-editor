package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.VideoSegment
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WindowGeometry
import com.example.prismstudio.p40b43a88.domain.model.WindowKeyframe
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey

/** Resolve's Disable Clip (D) and Paste Attributes (Alt+V). */
class ClipAttributesAndEnableTest {

    private val toggle = ToggleClipEnabledUseCase()
    private val paste = PasteAttributesUseCase()

    private val picture = clipOf("A", 0, 90, linkId = "L")
    private val sound = clipOf("Aa", 0, 90, track = Timeline.A1, linkId = "L")

    private fun rejection(block: () -> Unit): String? = assertThrows(EditRejected::class.java) { block() }.message

    @Test
    fun `D switches a clip and its linked sound off and on again`() {
        val off = toggle(timelineOf(picture, sound), "A")
        assertEquals(listOf(false, false), off.timeline.clips.map { it.enabled })
        assertEquals("已停用“A”及链接的片段：不播放、不渲染", off.message)
        val on = toggle(off.timeline, "Aa")
        assertTrue(on.timeline.clips.all { it.enabled })
        assertEquals("已启用“Aa”及链接的片段", on.message)
        assertEquals("轨道已锁定", rejection { toggle(timelineOf(picture, sound, locked = setOf(Timeline.V1)), "A") })
    }

    @Test
    fun `a disabled clip neither plays nor renders, and the timeline keeps its length`() {
        val doc = projectOf(
            timeline = timelineOf(
                clipOf("A", 0, 60, assetId = "v"),
                clipOf("B", 60, 30, assetId = "v").copy(enabled = false),
                clipOf("S", 0, 90, track = Timeline.A1, assetId = "v").copy(enabled = false),
                titleClipOf("T", 0, 30).copy(enabled = false),
            ),
            media = listOf(assetOf("v", durationMs = 20_000)),
        )
        val plan = BuildRenderPlanUseCase()(doc)
        assertEquals(90, plan.durationFrames)
        assertEquals(listOf(VideoSegment("A", "v", false, 0, 60, 0, 1f, null), VideoSegment(null, null, false, 60, 90, 0, 1f, null)), plan.layers.single().segments)
        assertTrue("its sound is silent", plan.audio.isEmpty())
        assertTrue("its title is not drawn", plan.titles.isEmpty())
    }

    @Test
    fun `what can be pasted depends on what was copied and where it goes`() {
        val copied = ClipboardUseCase.Content(listOf(picture, sound))
        val onto = clipOf("B", 100, 60, linkId = "M")
        val ontoSound = clipOf("Bb", 100, 60, track = Timeline.A1, linkId = "M")
        val title = titleClipOf("T", 200, 30)
        val timeline = timelineOf(picture, sound, onto, ontoSound, title)
        assertEquals(ClipAttribute.entries.toSet(), paste.available(paste.pairing(timeline, copied, "B")))
        assertEquals("a title takes the transform and the fades", setOf(ClipAttribute.TRANSFORM, ClipAttribute.FADES), paste.available(paste.pairing(timeline, copied, "T")))
        val soundOnly = ClipboardUseCase.Content(listOf(clipOf("X", 0, 30, track = Timeline.A1)))
        assertEquals("a sound's speed goes onto the sound, its picture along", setOf(ClipAttribute.VOLUME, ClipAttribute.EQ, ClipAttribute.PITCH, ClipAttribute.FADES, ClipAttribute.SPEED),
            paste.available(paste.pairing(timeline, soundOnly, "B")))
        assertEquals(emptySet<ClipAttribute>(), paste.available(paste.pairing(timeline, soundOnly, "T")))
        assertEquals("没有可以粘贴到这个片段的属性", rejection { paste(timeline, soundOnly, "T", ClipAttribute.entries.toSet(), 30) })
    }

    @Test
    fun `the speed goes onto the clip and its sound, its length follows and the tracks after it move`() {
        val slow = picture.copy(speed = ClipSpeed.of(PlaybackSpeed.HALF), reverse = true, retime = RetimeProcess.FRAME_BLEND)
        val slowSound = sound.copy(speed = ClipSpeed.of(PlaybackSpeed.HALF), reverse = true)
        val onto = clipOf("B", 100, 60, linkId = "M")
        val ontoSound = clipOf("Bb", 100, 60, track = Timeline.A1, linkId = "M")
        val after = clipOf("C", 160, 30)
        val afterSound = clipOf("Cc", 160, 30, track = Timeline.A1)
        val content = ClipboardUseCase.Content(listOf(slow, slowSound))
        val result = paste(timelineOf(slow, slowSound, onto, ontoSound, after, afterSound), content, "B", setOf(ClipAttribute.SPEED, ClipAttribute.FADES), 30)
        val b = result.timeline.clip("B")!!
        assertEquals("half speed: the same media over twice the frames", 120, b.durationFrames)
        assertEquals(PlaybackSpeed.HALF.factor, b.speed.factor, 0f)
        assertEquals(true to RetimeProcess.FRAME_BLEND, b.reverse to b.retime)
        assertEquals("its sound with it", 120 to true, result.timeline.clip("Bb")!!.let { it.durationFrames to it.reverse })
        assertEquals("the rest moves by 60", 220 to 220, result.timeline.clip("C")!!.start to result.timeline.clip("Cc")!!.start)
        assertEquals("已将“A”的淡入淡出、变速粘贴到“B”", result.message)
        // A clip with speed points has no one speed to give.
        val ramped = ClipboardUseCase.Content(listOf(picture.copy(speedSections = listOf(SpeedSection(45, ClipSpeed.NORMAL), SpeedSection(45, ClipSpeed.of(PlaybackSpeed.HALF))))))
        assertTrue(ClipAttribute.SPEED !in paste.available(paste.pairing(timelineOf(onto), ramped, "B")))
        assertTrue("a still takes no speed", ClipAttribute.SPEED !in paste.available(paste.pairing(timelineOf(onto), content, "B", stills = setOf("media"))))
    }

    @Test
    fun `chosen attributes go onto the picture and its sound, keyframes cut where the shorter clip ends`() {
        val keyed = picture.copy(
            fadeInSeconds = 1f,
            fadeOutSeconds = 0.25f,
            dynamicZoom = DynamicZoom(ZoomDirection.OUT, 30, ZoomEase.EASE_IN),
            composite = CompositeMode.SCREEN,
            scaling = ClipScaling.FILL,
            transform = ClipTransform(scalePercent = 50),
            keyframes = listOf(TransformKeyframe(0, ClipTransform(scalePercent = 50)), TransformKeyframe(80, ClipTransform(scalePercent = 130))),
            effects = listOf(ClipEffect("fx", EffectType.VIGNETTE, 40, true)),
            chromaKey = ChromaKey(color = ChromaKey.BLUE, similarity = 55f),
            mask = PowerWindow(shape = WindowShape.RECTANGLE, keyframes = listOf(WindowKeyframe(0, WindowGeometry(0f, 0f, 0.5f, 1f, 0f, 0.1f)), WindowKeyframe(80, WindowGeometry(0.5f, 0f, 0.5f, 1f, 0f, 0.1f)))),
            grade = ColorGrade(
                saturation = 0.5f,
                window = PowerWindow(keyframes = listOf(WindowKeyframe(0, WindowGeometry(0f, 0f, 0.5f, 1f, 0f, 0.2f)), WindowKeyframe(80, WindowGeometry(0.8f, 0f, 0.5f, 1f, 0f, 0.2f)))),
            ),
        )
        val loud = sound.copy(
            volumeDb = -6f, volumeKeyframes = listOf(VolumeKeyframe(10, -12f), VolumeKeyframe(70, 0f)), fadeInSeconds = 2f, fadeOutSeconds = 0.5f,
            fadeInCurve = 0.5f, fadeOutCurve = -0.25f,
        )
        val onto = clipOf("B", 100, 40, linkId = "M")
        val ontoSound = clipOf("Bb", 100, 40, track = Timeline.A1, linkId = "M")
        val content = ClipboardUseCase.Content(listOf(keyed, loud))
        val result = paste(timelineOf(keyed, loud, onto, ontoSound), content, "B", setOf(ClipAttribute.TRANSFORM, ClipAttribute.GRADE, ClipAttribute.FADES), 30)

        val b = result.timeline.clip("B")!!
        assertEquals(listOf(0, 39), b.keyframes.map { it.frame })
        assertEquals("every frame keeps its value", keyed.transformAt(39), b.transformAt(39))
        assertEquals("dynamic zoom comes with the transform", keyed.dynamicZoom, b.dynamicZoom)
        assertEquals("so does the composite mode", CompositeMode.SCREEN, b.composite)
        assertEquals("and the scaling", ClipScaling.FILL, b.scaling)
        assertEquals("and the mask, its keys fitted to the clip", listOf(0, 39), b.mask!!.keyframes.map { it.frame })
        assertEquals(WindowShape.RECTANGLE, b.mask!!.shape)
        assertTrue("effects were not chosen", b.effects.isEmpty())
        assertEquals(0.5f, b.grade.saturation, 0f)
        assertEquals(listOf(0, 39), b.grade.window!!.keyframes.map { it.frame })
        assertEquals("the picture takes the picture's fades, within half its 40 frames", 40 / 30f / 2, b.fadeInSeconds, 1e-6f)
        assertEquals(0.25f, b.fadeOutSeconds, 0f)
        val bb = result.timeline.clip("Bb")!!
        assertEquals("volume was not chosen", 0f, bb.volumeDb, 0f)
        assertEquals("the sound takes the sound's fades, which fit half the 40-frame clip", 40 / 30f / 2, bb.fadeInSeconds, 1e-6f)
        assertEquals(0.5f, bb.fadeOutSeconds, 0f)
        assertEquals("the fades come with their curves", 0.5f to -0.25f, bb.fadeInCurve to bb.fadeOutCurve)
        assertEquals("the picture's straight ones with the picture's", 0f to 0f, b.fadeInCurve to b.fadeOutCurve)
        assertEquals("已将“A”的画面变换、调色、淡入淡出粘贴到“B”", result.message)
        assertEquals("B", result.selectedClipId)

        // The sound's own equaliser goes onto the sound.
        val eqd = paste(timelineOf(keyed, loud.copy(eq = EqPreset.PODCAST.eq), onto, ontoSound), ClipboardUseCase.Content(listOf(keyed, loud.copy(eq = EqPreset.PODCAST.eq))), "B", setOf(ClipAttribute.EQ), 30)
        assertEquals(EqPreset.PODCAST.eq, eqd.timeline.clip("Bb")!!.eq)
        assertEquals("已将“A”的均衡器粘贴到“B”", eqd.message)
        // And its pitch.
        val pitched = paste(timelineOf(keyed, loud.copy(pitchCents = -300), onto, ontoSound), ClipboardUseCase.Content(listOf(keyed, loud.copy(pitchCents = -300))), "B", setOf(ClipAttribute.PITCH), 30)
        assertEquals(-300, pitched.timeline.clip("Bb")!!.pitchCents)
        assertEquals("已将“A”的音调粘贴到“B”", pitched.message)
        val withVolume = paste(timelineOf(keyed, loud, onto, ontoSound), content, "Bb", setOf(ClipAttribute.VOLUME, ClipAttribute.EFFECTS), 30)
        assertEquals(-6f, withVolume.timeline.clip("Bb")!!.volumeDb, 0f)
        assertEquals(listOf(10, 39), withVolume.timeline.clip("Bb")!!.volumeKeyframes.map { it.frame })
        assertEquals("the picture of a selected sound takes the picture attributes", keyed.effects, withVolume.timeline.clip("B")!!.effects)
        assertEquals("the keyer comes with the effects", keyed.chromaKey, withVolume.timeline.clip("B")!!.chromaKey)
        assertEquals("but not with the transform or the grade", null, b.chromaKey)
        assertEquals("轨道已锁定", rejection { paste(timelineOf(keyed, loud, onto, ontoSound, locked = setOf(Timeline.V1)), content, "B", setOf(ClipAttribute.TRANSFORM), 30) })
        assertFalse(paste.available(paste.pairing(timelineOf(onto), ClipboardUseCase.Content(listOf(keyed)), "B")).contains(ClipAttribute.VOLUME))
    }
}
