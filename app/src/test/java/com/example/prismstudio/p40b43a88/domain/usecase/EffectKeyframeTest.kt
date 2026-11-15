package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectKeyframe
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.amountAt
import com.example.prismstudio.p40b43a88.domain.model.frozenAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Effect keyframes: an effect's amount over the clip (Resolve keyframes any parameter), kept through cuts. */
class EffectKeyframeTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()
    private val fps = 30

    /** A 120-frame picture clip whose vignette rises from 0 at frame 30 to 100 at frame 90, beside a fixed blur of 20. */
    private val vignette = ClipEffect("fx-v", EffectType.VIGNETTE, 50, keyframes = listOf(EffectKeyframe(30, 0), EffectKeyframe(90, 100)))
    private val blur = ClipEffect("fx-b", EffectType.GAUSSIAN_BLUR, 20)
    private val clip = clipOf("V", 0, 120, assetId = "v").copy(effects = listOf(vignette, blur))

    @Test
    fun `the amount moves between keyframes, eased where a keyframe says, and holds outside them`() {
        assertEquals(0, vignette.amountAt(0))
        assertEquals(50, vignette.amountAt(60))
        assertEquals(100, vignette.amountAt(119))
        assertEquals("without keyframes the amount applies", 20, blur.amountAt(60))
        val eased = vignette.copy(keyframes = listOf(EffectKeyframe(30, 0, KeyEase(slopeOut = 0f)), EffectKeyframe(90, 100)))
        assertTrue("easing out of the first keyframe starts slowly", eased.amountAt(45) < vignette.amountAt(45))
    }

    @Test
    fun `toggling adds the amount there, a keyed change writes a keyframe, removing the last keeps its amount`() {
        val timeline = timelineOf(clipOf("V", 0, 120, assetId = "v").copy(effects = listOf(blur)))
        val added = keys.toggleEffect(timeline, "V", "fx-b", 10)
        assertEquals(listOf(EffectKeyframe(10, 20)), added.timeline.clips.single().effects.single().keyframes)
        assertEquals("已为高斯模糊添加关键帧 · 共 1 个", added.message)

        val ramp = props.effect(added.timeline, "V", "fx-b", amount = 80, atFrame = 100)
        assertEquals(listOf(10 to 20, 100 to 80), ramp.clips.single().effects.single().keyframes.map { it.frame to it.amount })
        assertThrows(EditRejected::class.java) { props.effect(added.timeline, "V", "fx-b", amount = 80, atFrame = 200) }
        // Switching the effect off leaves its keyframes as they are.
        assertEquals(2, props.effect(ramp, "V", "fx-b", enabled = false).clips.single().effects.single().keyframes.size)

        val cleared = keys.toggleEffect(keys.toggleEffect(ramp, "V", "fx-b", 10).timeline, "V", "fx-b", 100)
        val effect = cleared.timeline.clips.single().effects.single()
        assertTrue(effect.keyframes.isEmpty())
        assertEquals(80, effect.amount)
        assertEquals("已删除高斯模糊的关键帧，强度不再变化", cleared.message)

        assertEquals("特效不存在", assertThrows(EditRejected::class.java) { keys.toggleEffect(timeline, "V", "fx-404", 10) }.message)
        assertEquals("把播放头移到片段内，再添加特效关键帧", assertThrows(EditRejected::class.java) { keys.toggleEffect(timeline, "V", "fx-b", 130) }.message)
        val locked = timelineOf(clipOf("V", 0, 120, assetId = "v").copy(effects = listOf(blur)), locked = setOf(Timeline.V1))
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { keys.toggleEffect(locked, "V", "fx-b", 10) }.message)
    }

    @Test
    fun `a keyframe added between two keeps the change, and the arrows find the neighbours`() {
        val timeline = timelineOf(clip)
        val middle = keys.toggleEffect(timeline, "V", "fx-v", 60).timeline.clips.single()
        assertEquals(listOf(30 to 0, 60 to 50, 90 to 100), middle.effects.first().keyframes.map { it.frame to it.amount })
        assertEquals(30, keys.previousEffect(middle, "fx-v", 60))
        assertEquals(90, keys.nextEffect(middle, "fx-v", 60))
        assertNull(keys.nextEffect(middle, "fx-b", 0))
    }

    @Test
    fun `split, trim, speed and a held frame keep every frame's amount`() {
        val split = SplitClipUseCase(SequentialIds())(timelineOf(clip), atFrame = 60, selectedClipId = "V", fps = fps).timeline
        val left = split.clips.single { it.id == "V" }.effects.first()
        val right = split.clips.single { it.id != "V" }.effects.first()
        // Amounts are whole percents: a keyframe added at a cut is rounded, so a frame may differ by one.
        (0 until 60).forEach { f -> assertEquals("left $f", vignette.amountAt(f).toFloat(), left.amountAt(f).toFloat(), 1f) }
        (0 until 60).forEach { f -> assertEquals("right $f", vignette.amountAt(60 + f).toFloat(), right.amountAt(f).toFloat(), 1f) }

        val doc = projectOf(timeline = timelineOf(clip.copy(sourceInMs = 2_000)), media = listOf(assetOf("v", durationMs = 60_000)))
        val head = TrimClipUseCase()(doc.timeline, "V", TrimEdge.START, 45, 0, false, 0, 60_000, fps).timeline.clips.single().effects.first()
        (0 until 75).forEach { f -> assertEquals("trimmed $f", vignette.amountAt(45 + f).toFloat(), head.amountAt(f).toFloat(), 1f) }

        val slow = props.speed(timelineOf(clip), "V", ClipSpeed.of(PlaybackSpeed.HALF)).clips.single()
        assertEquals(listOf(60, 180), slow.effects.first().keyframes.map { it.frame })

        val held = clip.effects.frozenAt(60)
        assertEquals(listOf(50, 20), held.map { it.amount })
        assertTrue(held.all { it.keyframes.isEmpty() })
    }

    @Test
    fun `the shader reads a keyed effect's amount at every frame, added to the fixed ones`() {
        val registry = LiveLookRegistry()
        registry.update(projectOf(timeline = timelineOf(clip)))
        val look = registry.videoLook("V")!!
        val fx = FloatArray(8)
        look.fxAt(60f, fx)
        // Slots: blur, sharpen, vignette, grain, mirror, mosaic, chromatic, glow.
        assertArrayEquals(floatArrayOf(0.2f, 0f, 0.5f, 0f, 0f, 0f, 0f, 0f), fx, 1e-6f)
        look.fxAt(75f, fx)
        assertEquals(0.75f, fx[2], 1e-6f)
        look.fxAt(0f, fx)
        assertEquals("held before the first keyframe", 0f, fx[2], 1e-6f)
        val mirrored = clipOf("V", 0, 120, assetId = "v").copy(
            effects = listOf(ClipEffect("m", EffectType.MIRROR, 50, keyframes = listOf(EffectKeyframe(0, 0), EffectKeyframe(60, 40)))),
        )
        registry.update(projectOf(timeline = timelineOf(mirrored)))
        registry.videoLook("V")!!.fxAt(30f, fx)
        assertEquals("the mirror is on or off", 1f, fx[4], 0f)
    }
}
