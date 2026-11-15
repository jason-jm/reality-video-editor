package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.keyframesMoved
import com.example.prismstudio.p40b43a88.domain.model.retimed
import com.example.prismstudio.p40b43a88.domain.model.track
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.withKeysAt
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve keys each transform parameter on its own: a key on 缩放 leaves 位置 alone, and each moves between its own keys. */
class ParamKeyframeTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()

    private fun Timeline.a(): Clip = clip("A")!!

    /** Scale keyed 100 → 200 over frames 10–50, position keyed 300 → −300 over frames 70–90, each on its own. */
    private fun keyedApart(): Timeline {
        var t = timelineOf(clipOf("A", 0, 100))
        t = keys.toggleGroup(t, "A", 10, TransformKeyGroup.SCALE).timeline
        t = props.transform(t, "A", 50) { it.copy(scalePercent = 200) }
        t = props.transform(t, "A", 50) { it.copy(positionX = 300) }
        t = keys.toggleGroup(t, "A", 70, TransformKeyGroup.POSITION).timeline
        return props.transform(t, "A", 90) { it.copy(positionX = -300) }
    }

    @Test
    fun `a key on one parameter leaves the others alone, and each moves between its own keys`() {
        val t = keyedApart()
        val clip = t.a()
        assertEquals(listOf(10, 50), clip.keyframes.track(CurveParam.SCALE).map { it.frame })
        assertEquals(listOf(100f, 200f), clip.keyframes.track(CurveParam.SCALE).map { it.value })
        assertEquals(listOf(70, 90), clip.keyframes.track(CurveParam.POSITION_X).map { it.frame })
        assertEquals("Y is keyed with X", listOf(70, 90), clip.keyframes.track(CurveParam.POSITION_Y).map { it.frame })
        assertTrue("rotation was never keyed", clip.keyframes.track(CurveParam.ROTATION).isEmpty())
        // Position had no keys when it was set at frame 50: it changed outright, from the first frame on.
        assertEquals(300, clip.transformAt(0).positionX)
        assertEquals(150, clip.transformAt(30).scalePercent)
        assertEquals("scale holds after its last key while position moves", 200, clip.transformAt(80).scalePercent)
        assertEquals(0, clip.transformAt(80).positionX)
        assertEquals(-300, clip.transformAt(99).positionX)
        assertEquals("position holds before its first key while scale moves", 300, clip.transformAt(30).positionX)
    }

    @Test
    fun `each group's button shows whether it is keyed here, animated, or still`() {
        val clip = keyedApart().a()
        assertEquals(ParamKeyState.AT_KEY, keys.groupState(clip, 10, TransformKeyGroup.SCALE))
        assertEquals(ParamKeyState.ANIMATED, keys.groupState(clip, 70, TransformKeyGroup.SCALE))
        assertEquals(ParamKeyState.AT_KEY, keys.groupState(clip, 70, TransformKeyGroup.POSITION))
        assertEquals(ParamKeyState.ANIMATED, keys.groupState(clip, 10, TransformKeyGroup.POSITION))
        assertEquals(ParamKeyState.NONE, keys.groupState(clip, 10, TransformKeyGroup.OPACITY))
        assertEquals(ParamKeyState.NONE, keys.groupState(clip, 10, TransformKeyGroup.CROP))
    }

    @Test
    fun `removing a group's keys keeps the others, and a parameter left without keys keeps the value it had there`() {
        var t = keyedApart()
        val first = keys.toggleGroup(t, "A", 10, TransformKeyGroup.SCALE)
        assertEquals("已删除缩放的关键帧 · 还剩 1 个", first.message)
        t = first.timeline
        assertEquals("one scale key left: it holds", 200, t.a().transformAt(0).scalePercent)
        val last = keys.toggleGroup(t, "A", 50, TransformKeyGroup.SCALE)
        assertEquals("已删除缩放的关键帧，缩放不再变化", last.message)
        t = last.timeline
        assertTrue(t.a().keyframes.track(CurveParam.SCALE).isEmpty())
        assertEquals("the scale it had there stays", 200, t.a().transform.scalePercent)
        assertEquals(200, t.a().transformAt(5).scalePercent)
        assertEquals("position still moves", listOf(70, 90), t.a().keyframes.map { it.frame })
        assertEquals(0, t.a().transformAt(80).positionX)
        // Adding says so, counting that group's keys.
        assertEquals("已为旋转添加关键帧 · 共 1 个", keys.toggleGroup(t, "A", 20, TransformKeyGroup.ROTATION).message)
        assertThrows(EditRejected::class.java) { keys.toggleGroup(t, "A", 100, TransformKeyGroup.SCALE) }
    }

    @Test
    fun `the keyframe button of the whole transform removes every key there, and its ease reaches every parameter`() {
        var t = keyedApart()
        // Frame 70 keys only position: removing it leaves position with its frame-90 key.
        t = keys.toggle(t, "A", 70).timeline
        assertEquals(listOf(10, 50, 90), t.a().keyframes.map { it.frame })
        assertEquals(-300, t.a().transformAt(60).positionX)
        // Adding keys everything there.
        t = keys.toggle(t, "A", 30).timeline
        val at30 = t.a().keyframes.first { it.frame == 30 }
        assertEquals(TransformKeyGroup.ALL, at30.params)
        assertEquals("the motion is unchanged", 150, t.a().transformAt(30).scalePercent)
        assertEquals(175, t.a().transformAt(40).scalePercent)
        // An ease of its own on scale there (the curve editor's) eases along with the shared one.
        t = t.replaceClip(t.a().copy(keyframes = t.a().keyframes.map { if (it.frame == 30) it.copy(paramEases = mapOf(CurveParam.SCALE to KeyEase(slopeIn = 0.5f))) else it }))
        t = keys.ease(t, "A", 30, arriving = true).timeline
        val eased = t.a().keyframes.first { it.frame == 30 }
        assertEquals(0f, eased.easeOf(CurveParam.POSITION_X).slopeIn, 0f)
        assertEquals(0f, eased.easeOf(CurveParam.SCALE).slopeIn, 0f)
    }

    @Test
    fun `an edit at the playhead keys only the animated parameters it changes`() {
        var t = keyedApart()
        // Opacity is still: changing it at frame 30 changes it everywhere, adding no key.
        t = props.transform(t, "A", 30) { it.copy(opacityPercent = 40) }
        assertEquals(listOf(10, 50, 70, 90), t.a().keyframes.map { it.frame })
        assertEquals(40, t.a().transformAt(95).opacityPercent)
        // Scale is animated: changing it at frame 30 adds a scale key there and nothing else.
        t = props.transform(t, "A", 30) { it.copy(scalePercent = 120) }
        assertEquals(listOf(10, 30, 50), t.a().keyframes.track(CurveParam.SCALE).map { it.frame })
        assertEquals(listOf(70, 90), t.a().keyframes.track(CurveParam.POSITION_X).map { it.frame })
        assertEquals(setOf(CurveParam.SCALE), t.a().keyframes.first { it.frame == 30 }.params)
        assertEquals(120, t.a().transformAt(30).scalePercent)
        // Without a frame, an animated parameter cannot change (a still one can).
        assertThrows(EditRejected::class.java) { props.transform(t, "A") { it.copy(scalePercent = 90) } }
        assertEquals(15, props.transform(t, "A") { it.copy(rotationDegrees = 15) }.a().transformAt(60).rotationDegrees)
    }

    @Test
    fun `cutting, retiming and moving keep each parameter's motion`() {
        val clip = keyedApart().a()
        // The stretch from frame 30, 50 frames long: every frame as it was.
        val piece = clip.copy(keyframes = clip.keyframesFor(30, 50), durationFrames = 50)
        for (f in 0 until 50) assertEquals("frame $f", clip.transformAt(30 + f), piece.transformAt(f))
        assertEquals("position's own keys stay apart from scale's", setOf(CurveParam.POSITION_X, CurveParam.POSITION_Y), piece.keyframes.first { it.frame == 40 }.params)
        // Twice as long: each key twice as far in.
        val slow = clip.keyframes.retimed(100, 200)
        assertEquals(listOf(20, 100), slow.track(CurveParam.SCALE).map { it.frame })
        assertEquals(listOf(140, 180), slow.track(CurveParam.POSITION_X).map { it.frame })
        val moved = clip.keyframesMoved { it + 5 }
        assertEquals(listOf(15, 55, 75, 95), moved.keyframes.map { it.frame })
        assertEquals(setOf(CurveParam.SCALE), moved.keyframes.first().params)
    }

    @Test
    fun `keyframes that key everything work as they always did`() {
        val small = ClipTransform(scalePercent = 50, positionX = -400)
        val big = ClipTransform(scalePercent = 150, positionX = 400)
        val whole = listOf(TransformKeyframe(20, small), TransformKeyframe(60, big))
        assertTrue(whole.all { it.whole })
        val clip = clipOf("W", 0, 100).copy(keyframes = whole)
        assertEquals(ClipTransform(scalePercent = 100, positionX = 0), clip.transformAt(40))
        // Keying everything at frame 40 keeps them whole, holding the value there.
        val more = whole.withKeysAt(40, TransformKeyGroup.ALL, ClipTransform())
        assertTrue(more.all { it.whole })
        assertEquals(ClipTransform(scalePercent = 100, positionX = 0), more[1].transform)
        // Keying one group of them makes per-parameter keys: the others keep their two keys and the motion is the same.
        val scaleOnly = whole.withKeysAt(40, setOf(CurveParam.SCALE), ClipTransform())
        assertEquals(listOf(20, 40, 60), scaleOnly.track(CurveParam.SCALE).map { it.frame })
        assertEquals(listOf(20, 60), scaleOnly.track(CurveParam.POSITION_X).map { it.frame })
        for (f in listOf(0, 30, 40, 50, 80)) assertEquals("frame $f", clip.transformAt(f), clip.copy(keyframes = scaleOnly).transformAt(f))
    }
}
