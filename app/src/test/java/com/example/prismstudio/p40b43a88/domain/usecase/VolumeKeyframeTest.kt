package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Volume keyframes: a clip's level over time (interpolated in dB), kept through cuts. */
class VolumeKeyframeTest {

    private val keys = KeyframeUseCase()
    private val props = ClipPropertiesUseCase()
    private val fps = 30

    /** A 120-frame sound clip ducked from 0 dB at frame 30 to −24 dB at frame 90. */
    private val ducked = clipOf("S", 0, 120, track = Timeline.A1, assetId = "a")
        .copy(volumeKeyframes = listOf(VolumeKeyframe(30, 0f), VolumeKeyframe(90, -24f)))

    @Test
    fun `the level moves linearly in dB between keyframes and holds outside them`() {
        assertEquals(0f, ducked.volumeAt(0), 1e-4f)
        assertEquals(-12f, ducked.volumeAt(60), 1e-4f)
        assertEquals(-24f, ducked.volumeAt(119), 1e-4f)
        assertEquals("without keyframes the clip volume applies", -6f, ducked.copy(volumeKeyframes = emptyList(), volumeDb = -6f).volumeAt(60), 1e-4f)
    }

    @Test
    fun `toggling adds the current level, setting a keyed level writes a keyframe, removing the last keeps it`() {
        val timeline = timelineOf(clipOf("S", 0, 120, track = Timeline.A1, assetId = "a").copy(volumeDb = -3f))

        val added = keys.toggleVolume(timeline, "S", 10)
        assertEquals(listOf(VolumeKeyframe(10, -3f)), added.timeline.clips.single().volumeKeyframes)

        val ducking = props.volume(added.timeline, "S", -30f, atFrame = 100)
        assertEquals(listOf(10 to -3f, 100 to -30f), ducking.clips.single().volumeKeyframes.map { it.frame to it.volumeDb })
        assertThrows(EditRejected::class.java) { props.volume(added.timeline, "S", -30f, atFrame = 200) }

        val cleared = keys.toggleVolume(keys.toggleVolume(ducking, "S", 10).timeline, "S", 100)
        assertTrue(cleared.timeline.clips.single().volumeKeyframes.isEmpty())
        assertEquals(-30f, cleared.timeline.clips.single().volumeDb, 1e-4f)
        assertThrows("pictures have no volume keyframes", EditRejected::class.java) { keys.toggleVolume(timelineOf(clipOf("V", 0, 60)), "V", 5) }
    }

    @Test
    fun `split, trim and speed keep every frame's level`() {
        val split = SplitClipUseCase(SequentialIds())(timelineOf(ducked), atFrame = 60, selectedClipId = "S", fps = fps).timeline
        val left = split.clips.single { it.id == "S" }
        val right = split.clips.single { it.id != "S" }
        (0 until 60).forEach { f -> assertEquals("left $f", ducked.volumeAt(f), left.volumeAt(f), 0.01f) }
        (0 until 60).forEach { f -> assertEquals("right $f", ducked.volumeAt(60 + f), right.volumeAt(f), 0.01f) }

        val doc = projectOf(timeline = timelineOf(ducked.copy(sourceInMs = 2_000)), media = listOf(assetOf("a", durationMs = 60_000)))
        val head = TrimClipUseCase()(doc.timeline, "S", TrimEdge.START, 45, 0, false, 0, 60_000, fps).timeline.clips.single()
        (0 until 75).forEach { f -> assertEquals("trimmed $f", ducked.volumeAt(45 + f), head.volumeAt(f), 0.01f) }

        val slow = props.speed(timelineOf(ducked), "S", ClipSpeed.of(PlaybackSpeed.HALF)).clips.single()
        assertEquals(listOf(60, 180), slow.volumeKeyframes.map { it.frame })
    }

    @Test
    fun `the mixer turns keyed levels into gain sample by sample`() {
        val mix = LiveLookRegistry.ClipMix(1f, 0, 0, keyTimesUs = longArrayOf(0, 1_000_000), keyDb = floatArrayOf(0f, -20f))

        assertEquals(1f, mix.gainAt(0), 1e-4f)
        assertEquals(10f.pow(-10f / 20f), mix.gainAt(500_000), 1e-4f)
        assertEquals(0.1f, mix.gainAt(5_000_000), 1e-4f)
        assertEquals("fixed gain without keyframes", 0.5f, LiveLookRegistry.ClipMix(0.5f, 0, 0).gainAt(123), 1e-6f)
    }
}
