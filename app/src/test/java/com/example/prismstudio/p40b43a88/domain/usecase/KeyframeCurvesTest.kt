package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.CurveEase
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectKeyframe
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.KeyCurve
import com.example.prismstudio.p40b43a88.domain.model.KeyCurveChannel
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.amountAt
import com.example.prismstudio.p40b43a88.domain.model.track
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Keyframe Curve Editor: a clip's curves, their keys dragged and eased, control points that set slopes. */
class KeyframeCurvesTest {

    private val curves = KeyframeCurvesUseCase()
    private val scale = KeyCurveChannel.Transform(CurveParam.SCALE)
    private val opacity = KeyCurveChannel.Transform(CurveParam.OPACITY)

    /** A picture 60 frames long scaling 100 % → 200 % → 150 % at frames 0, 30, 59, fading in over the first stretch. */
    private val picture = clipOf("P", 0, 60).copy(
        keyframes = listOf(
            TransformKeyframe(0, ClipTransform(scalePercent = 100, opacityPercent = 0)),
            TransformKeyframe(30, ClipTransform(scalePercent = 200, opacityPercent = 100)),
            TransformKeyframe(59, ClipTransform(scalePercent = 150, opacityPercent = 100)),
        ),
        effects = listOf(ClipEffect("fx1", EffectType.GAUSSIAN_BLUR, 40, keyframes = listOf(EffectKeyframe(0, 0), EffectKeyframe(40, 80)))),
    )
    private val sound = clipOf("S", 0, 90, track = Timeline.A1).copy(volumeKeyframes = listOf(VolumeKeyframe(10, 0f), VolumeKeyframe(70, -20f)))
    private val timeline = timelineOf(picture, sound)

    @Test
    fun `a picture shows its transform settings and effects, a sound its volume and pan, each with its keys and range`() {
        val list = curves.curves(picture)
        assertEquals(CurveParam.entries.size + 1, list.size)
        assertEquals(listOf("缩放", "位置 X"), list.take(2).map { it.label })
        assertEquals("高斯模糊", list.last().label)
        assertEquals(listOf(100f, 200f, 150f), list.first().keys.map { it.value })
        assertTrue("scale moves", list.first().animated)
        assertTrue("position X holds still", !list[1].animated)
        // A sound: its volume, then its pan (rev 166).
        val (volume, pan) = curves.curves(sound)
        assertEquals("声像", pan.label)
        assertEquals(listOf("音量", "dB", "-20.0 dB"), listOf(volume.label, volume.unit, volume.format(-20f)))
        assertNull("no volume on a picture", curves.curve(picture, KeyCurveChannel.Volume))
        assertEquals(scale, KeyCurveChannel.of("t:SCALE"))
        assertEquals(KeyCurveChannel.Effect("fx1"), KeyCurveChannel.of("fx:fx1"))
    }

    @Test
    fun `control points sit a third of the way along and dragging one gives back the slope that put it there`() {
        val eased = timeline.replaceClip(picture.copy(keyframes = picture.keyframes.mapIndexed { i, k -> if (i == 0) k.copy(ease = KeyEase(1f, 2.4f)) else k }))
        val curve = curves.curve(eased.clip("P")!!, scale)!!
        val (hf, hv) = curve.handleOut(0)!!
        assertEquals(10f, hf, 1e-4f)
        assertEquals(100f + 2.4f * 100f / 3f, hv, 1e-3f)
        assertEquals(2.4f, KeyCurve.slopeFor(100f, 200f, hv)!!, 1e-4f)
        assertNull("the first key has nothing arriving", curve.handleIn(0))
        val (inF, inV) = curve.handleIn(1)!!
        assertEquals(20f, inF, 1e-4f)
        assertEquals("the arriving side, as the slope 1 leaves it", 200f - 100f / 3f, inV, 1e-3f)
        assertEquals(1f, KeyCurve.slopeFor(200f, 100f, inV)!!, 1e-4f)
        assertEquals("kept from overshooting", KeyEase.MAX_SLOPE, KeyCurve.slopeFor(100f, 200f, 400f)!!, 0f)
        assertEquals("never past an ease", 0f, KeyCurve.slopeFor(100f, 200f, 50f)!!, 0f)
        assertNull("a flat stretch has no slope", KeyCurve.slopeFor(100f, 100f, 120f))
        // The curve draws what playback plays.
        for (f in 0 until 60) assertEquals(eased.clip("P")!!.transformAt(f).scalePercent.toFloat(), curve.valueAt(f.toFloat()), 0.5f)
    }

    @Test
    fun `dragging a key moves that setting's own key in time and value, kept between its neighbours, the others' keys stay`() {
        val moved = curves.moveKey(timeline, "P", scale, 1, 20, 260.4f)
        val clip = moved.timeline.clip("P")!!
        assertEquals(listOf(0, 20, 59), clip.keyframes.track(CurveParam.SCALE).map { it.frame })
        assertEquals(260f, clip.keyframes.track(CurveParam.SCALE)[1].value, 0f)
        assertEquals("the opacity keeps its key at 30", listOf(0 to 0f, 30 to 100f, 59 to 100f), clip.keyframes.track(CurveParam.OPACITY).map { it.frame to it.value })
        assertEquals(listOf(0, 20, 30, 59), clip.keyframes.map { it.frame })
        assertEquals(listOf(CurveParam.SCALE), clip.keyframes[1].params.toList())
        // Playback follows: the scale arrives at 260 % by frame 20, the opacity still at 100 % by 30.
        assertEquals(260, clip.transformAt(20).scalePercent)
        assertEquals(100, clip.transformAt(30).opacityPercent)
        assertEquals("关键帧移到第 20 帧 · 260%", moved.message)
        val pinned = curves.moveKey(timeline, "P", scale, 1, 70, 999f).timeline.clip("P")!!.keyframes.track(CurveParam.SCALE)[1]
        assertEquals("a frame short of the next key, within the range", listOf(58, 400), listOf(pinned.frame, pinned.value.toInt()))
        assertEquals(1, curves.moveKey(timeline, "P", scale, 1, -5, 200f).timeline.clip("P")!!.keyframes.track(CurveParam.SCALE)[1].frame)
        // A volume moves in tenths of a dB; an effect's amount in whole percents.
        val volume = curves.moveKey(timeline, "S", KeyCurveChannel.Volume, 0, 5, -3.46f).timeline.clip("S")!!
        assertEquals(listOf(5, -3.5f), listOf(volume.volumeKeyframes[0].frame, volume.volumeKeyframes[0].volumeDb))
        val blur = curves.moveKey(timeline, "P", KeyCurveChannel.Effect("fx1"), 1, 45, 66.6f).timeline.clip("P")!!
        assertEquals(listOf(45, 67), blur.effects[0].keyframes[1].let { listOf(it.frame, it.amount) })
        assertEquals(67, blur.effects[0].amountAt(50))
        assertEquals("关键帧已不存在", assertThrows(EditRejected::class.java) { curves.moveKey(timeline, "P", scale, 5, 0, 0f) }.message)
    }

    @Test
    fun `slopes and ease presets set how the motion meets a key, the ends keep their outer sides even`() {
        val steep = curves.setSlope(timeline, "P", scale, 1, arriving = false, slope = 2.567f)
        assertEquals(KeyEase(1f, 2.57f), steep.timeline.clip("P")!!.keyframes[1].ease)
        assertEquals("离开斜率 2.57", steep.message)
        assertEquals(0f, curves.setSlope(timeline, "P", scale, 1, true, -1f).timeline.clip("P")!!.keyframes[1].ease.slopeIn, 0f)
        assertEquals("第一个关键帧之前没有变化，无需缓入", assertThrows(EditRejected::class.java) { curves.setSlope(timeline, "P", scale, 0, true, 0f) }.message)
        assertEquals("最后一个关键帧之后没有变化，无需缓出", assertThrows(EditRejected::class.java) { curves.setSlope(timeline, "P", scale, 2, false, 0f) }.message)
        // The opacity's key alone: the scale keyed at the same frame keeps its own ease.
        val both = curves.ease(timeline, "P", opacity, 1, CurveEase.EASE_BOTH)
        assertEquals(KeyEase(0f, 0f), both.timeline.clip("P")!!.keyframes[1].easeOf(CurveParam.OPACITY))
        assertEquals(KeyEase.LINEAR, both.timeline.clip("P")!!.keyframes[1].easeOf(CurveParam.SCALE))
        assertEquals("关键帧缓动：缓入缓出", both.message)
        // The first key keeps the pick (its arriving side moves nothing yet): the curve eases out of it.
        val first = curves.ease(timeline, "P", scale, 0, CurveEase.EASE_BOTH).timeline.clip("P")!!
        assertEquals(KeyEase(0f, 0f), first.keyframes[0].easeOf(CurveParam.SCALE))
        assertEquals(picture.transformAt(0), first.transformAt(0))
        assertTrue("slow out of the first key", first.transformAt(5).scalePercent < 100 + (200 - 100) * 5 / 30)
        // Eased into the middle key, the scale arrives slowly: past halfway in time, less than halfway in value.
        val easedIn = curves.ease(timeline, "P", scale, 1, CurveEase.EASE_IN).timeline.clip("P")!!
        assertTrue(easedIn.transformAt(25).scalePercent > 100 + (200 - 100) * 25 / 30)
        assertEquals(CurveEase.EASE_OUT, CurveEase.of(KeyEase(1f, 0f)))
        assertNull(CurveEase.of(KeyEase(1f, 2.57f)))
    }

    @Test
    fun `a key added keeps the motion, a key deleted leaves the last value, and a clip on a locked track is refused`() {
        val before = picture.transformAt(15)
        val added = curves.addKey(timeline, "P", scale, 15)
        val clip = added.timeline.clip("P")!!
        assertEquals(listOf(0, 15, 30, 59), clip.keyframes.map { it.frame })
        assertEquals(before, clip.transformAt(15))
        assertEquals("已在第 15 帧添加关键帧 · 共 4 个", added.message)
        assertEquals("这里已有关键帧", assertThrows(EditRejected::class.java) { curves.addKey(timeline, "P", scale, 30) }.message)
        assertEquals("把播放头移到片段内，再添加关键帧", assertThrows(EditRejected::class.java) { curves.addKey(timeline, "P", scale, 60) }.message)
        val volume = curves.addKey(timeline, "S", KeyCurveChannel.Volume, 40).timeline.clip("S")!!
        assertEquals(sound.volumeAt(40), volume.volumeAt(40), 1e-4f)
        val gone = curves.deleteKey(timeline, "P", scale, 1)
        assertEquals("已删除关键帧 · 还剩 2 个", gone.message)
        val single = timelineOf(picture.copy(keyframes = listOf(TransformKeyframe(10, ClipTransform(scalePercent = 130)))))
        val bare = curves.deleteKey(single, "P", scale, 0)
        assertEquals(130, bare.timeline.clip("P")!!.transform.scalePercent)
        assertEquals("已删除关键帧，缩放不再变化", bare.message)
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.V1) it.copy(locked = true) else it })
        assertEquals("轨道已锁定", assertThrows(EditRejected::class.java) { curves.moveKey(locked, "P", scale, 1, 31, 200f) }.message)
    }
}
