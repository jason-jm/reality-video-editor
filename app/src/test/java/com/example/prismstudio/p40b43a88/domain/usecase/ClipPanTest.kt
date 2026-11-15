package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.data.engine.balanceLeft
import com.example.prismstudio.p40b43a88.data.engine.balanceRight
import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.KeyCurveChannel
import com.example.prismstudio.p40b43a88.domain.model.ParamKey
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.keyframesMoved
import com.example.prismstudio.p40b43a88.domain.model.panAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's inspector Pan on a sound clip: placed left or right before the track's pan, and keyed to travel across. */
class ClipPanTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()
    private val curves = KeyframeCurvesUseCase()

    /** A 120-frame sound clip panned from hard left at frame 30 to hard right at frame 90. */
    private val sweep = clipOf("S", 0, 120, track = Timeline.A1, assetId = "a")
        .copy(panKeyframes = listOf(ParamKey(30, -1f), ParamKey(90, 1f)))

    private fun rejection(block: () -> Unit) = assertThrows(EditRejected::class.java) { block() }.message

    @Test
    fun `the pan moves linearly between keyframes and holds outside them`() {
        assertEquals(-1f, sweep.panAt(0), 1e-6f)
        assertEquals(0f, sweep.panAt(60), 1e-6f)
        assertEquals(0.5f, sweep.panAt(75), 1e-6f)
        assertEquals(1f, sweep.panAt(119), 1e-6f)
        assertEquals("without keyframes the clip's pan applies", -0.3f, sweep.copy(panKeyframes = emptyList(), pan = -0.3f).panAt(60), 1e-6f)
    }

    @Test
    fun `setting the pan rounds to hundredths, keyed it changes the key at the playhead`() {
        val t = props.pan(timelineOf(clipOf("S", 0, 60, track = Timeline.A1)), "S", -0.333f)
        assertEquals(-0.33f, t.clip("S")!!.pan, 0f)
        assertEquals(1f, props.pan(t, "S", 3f).clip("S")!!.pan, 0f)
        assertEquals("只有声音片段有声像", rejection { props.pan(timelineOf(clipOf("V", 0, 60)), "V", 0.5f) })
        // Keyed: the key at the playhead (one added there, keeping the motion), not the clip's pan.
        val keyed = props.pan(timelineOf(sweep), "S", 0.2f, atFrame = 60).clip("S")!!
        assertEquals(listOf(30, 60, 90), keyed.panKeyframes.map { it.frame })
        assertEquals(0.2f, keyed.panAt(60), 1e-6f)
        assertEquals(0f, keyed.pan, 0f)
        assertEquals("把播放头移到片段内，再调整声像关键帧", rejection { props.pan(timelineOf(sweep), "S", 0.2f, atFrame = 130) })
    }

    @Test
    fun `the diamond adds a key holding the pan there, removes it, and the last leaves its value`() {
        val clip = clipOf("S", 0, 90, track = Timeline.A1).copy(pan = 0.4f)
        assertEquals(ParamKeyState.NONE, keys.panState(clip, 10))
        val one = keys.togglePan(timelineOf(clip), "S", 10)
        assertEquals("已添加声像关键帧 · 共 1 个", one.message)
        val keyed = one.timeline.clip("S")!!
        assertEquals(listOf(ParamKey(10, 0.4f)), keyed.panKeyframes)
        assertEquals(ParamKeyState.AT_KEY, keys.panState(keyed, 10))
        assertEquals(ParamKeyState.ANIMATED, keys.panState(keyed, 50))
        // Moved at frame 50: a second key; then both removed, the pan stays where the last one was.
        val moved = props.pan(one.timeline, "S", -0.6f, atFrame = 50)
        val left = keys.togglePan(moved, "S", 10)
        assertEquals("已删除声像关键帧 · 还剩 1 个", left.message)
        val none = keys.togglePan(left.timeline, "S", 50)
        assertEquals("已删除声像关键帧，声像不再变化", none.message)
        assertEquals(-0.6f, none.timeline.clip("S")!!.pan, 0f)
        assertTrue(none.timeline.clip("S")!!.panKeyframes.isEmpty())
        assertEquals("把播放头移到片段内，再添加声像关键帧", rejection { keys.togglePan(timelineOf(clip), "S", 95) })
    }

    @Test
    fun `cutting, trimming, retiming and moving keys keep the pan's travel`() {
        val pieces = SplitClipUseCase(SequentialIds())(timelineOf(sweep), 60, "S", 30).timeline.clips.sortedBy { it.start }
        for (f in 0 until 60) assertEquals("piece 2 frame $f", sweep.panAt(60 + f), pieces[1].panAt(f), 1e-5f)
        for (f in 0 until 60) assertEquals("piece 1 frame $f", sweep.panAt(f), pieces[0].panAt(f), 1e-5f)
        val trimmed = TrimClipUseCase()(timelineOf(sweep), "S", TrimEdge.START, 45, 5, false, 0, 100_000, 30).timeline.clip("S")!!
        for (f in 0 until 75) assertEquals("trimmed frame $f", sweep.panAt(45 + f), trimmed.panAt(f), 1e-5f)
        val slow = props.speed(timelineOf(sweep), "S", ClipSpeed.of(PlaybackSpeed.HALF)).clip("S")!!
        assertEquals(listOf(60, 180), slow.panKeyframes.map { it.frame })
        assertEquals(listOf(35, 95), sweep.keyframesMoved { it + 5 }.panKeyframes.map { it.frame })
    }

    @Test
    fun `paste attributes brings the pan with the volume, and the project file keeps it`() {
        val heard = clipOf("Aa", 0, 120, track = Timeline.A1, linkId = "L").copy(pan = 0.25f, panKeyframes = sweep.panKeyframes)
        val picture = clipOf("A", 0, 120, linkId = "L")
        val onto = clipOf("B", 200, 120, linkId = "M")
        val ontoSound = clipOf("Bb", 200, 120, track = Timeline.A1, linkId = "M")
        val pasted = PasteAttributesUseCase()(
            timelineOf(picture, heard, onto, ontoSound), ClipboardUseCase.Content(listOf(picture, heard)), "B", setOf(ClipAttribute.VOLUME), 30,
        ).timeline.clip("Bb")!!
        assertEquals(0.25f, pasted.pan, 0f)
        for (f in 0 until 120 step 7) assertEquals("pasted frame $f", heard.panAt(f), pasted.panAt(f), 1e-5f)

        val doc = projectOf(timelineOf(heard))
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).timeline.clip("Aa")!!
        assertEquals(heard.pan, reread.pan, 0f)
        assertEquals(heard.panKeyframes, reread.panKeyframes)
        // A centred, unkeyed clip writes no pan keys and reads back centred (as older projects do).
        val plain = ProjectJsonCodec.encode(projectOf(timelineOf(clipOf("C", 0, 30, track = Timeline.A1))))
        assertTrue("no pan keys for an unkeyed clip", !plain.contains("panKeys"))
        val centred = ProjectJsonCodec.decode(plain).timeline.clip("C")!!
        assertEquals(0f, centred.pan, 0f)
        assertTrue(centred.panKeyframes.isEmpty())
    }

    @Test
    fun `the curve editor draws the pan from −100 to 100 and edits it`() {
        assertEquals(listOf("音量", "声像"), curves.curves(sweep).map { it.label })
        val curve = curves.curve(sweep, KeyCurveChannel.Pan)!!
        assertEquals(listOf(-100f, 100f), curve.keys.map { it.value })
        assertNull("a picture has no pan curve", curves.curve(clipOf("V", 0, 30), KeyCurveChannel.Pan))
        val moved = curves.moveKey(timelineOf(sweep), "S", KeyCurveChannel.Pan, 1, 80, 40f).timeline.clip("S")!!
        assertEquals(ParamKey(80, 0.4f), moved.panKeyframes[1])
        assertEquals(0f * 100, curves.valueAt(sweep, KeyCurveChannel.Pan, 60)!!, 1e-4f)
        val added = curves.addKey(timelineOf(sweep), "S", KeyCurveChannel.Pan, 60).timeline.clip("S")!!
        assertEquals(listOf(30, 60, 90), added.panKeyframes.map { it.frame })
        val gone = curves.deleteKey(timelineOf(sweep.copy(panKeyframes = listOf(ParamKey(30, -0.5f)))), "S", KeyCurveChannel.Pan, 0).timeline.clip("S")!!
        assertEquals(-0.5f, gone.pan, 0f)
        assertEquals(KeyCurveChannel.Pan, KeyCurveChannel.of("pan"))
    }

    @Test
    fun `the mix balances by the clip's pan at each moment, and loudness counts what is left`() {
        val mix = LiveLookRegistry.ClipMix(1f, 0, 0, pan = 0f, panTimesUs = longArrayOf(1_000_000, 3_000_000), panValues = floatArrayOf(-1f, 1f))
        assertTrue(mix.panMoves)
        assertEquals(-1f, mix.panAt(0), 1e-6f)
        assertEquals(0f, mix.panAt(2_000_000), 1e-6f)
        assertEquals(1f, mix.panAt(5_000_000), 1e-6f)
        assertEquals(-0.4f, LiveLookRegistry.ClipMix(1f, 0, 0, pan = -0.4f).panAt(123), 0f)
        // A balance: the near side whole, the far side down to nothing.
        assertEquals(1f, balanceLeft(-1f), 0f)
        assertEquals(0f, balanceRight(-1f), 0f)
        assertEquals(0.5f, balanceLeft(0.5f), 1e-6f)
        assertEquals(1f, balanceRight(0.5f), 0f)
        assertEquals(1.0, panEnergy(0f), 1e-9)
        assertEquals(0.5, panEnergy(1f), 1e-9)
        assertEquals(0.625, panEnergy(-0.5f), 1e-9)
    }
}
