package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale
import kotlin.math.pow

/** A clip's loudness as heard, and Normalize Audio Levels. */
class LoudnessUseCaseTest {

    private val loudness = LoudnessUseCase()
    private val fps = 30

    /** 20 s of sound at [lufs] with a sample peak of [truePeakDbtp] (100 ms blocks). */
    private fun steady(lufs: Double = -20.0, truePeakDbtp: Double = -6.0, seconds: Int = 20) = LoudnessProfile(
        100,
        FloatArray(seconds * 10) { energyOf(lufs) },
        FloatArray(seconds * 10) { 10.0.pow(truePeakDbtp / 20).toFloat() },
    )

    private fun energyOf(lufs: Double) = 10.0.pow((lufs + 0.691) / 10).toFloat()

    private fun sound(frames: Int = 90, sourceInMs: Long = 0) = clipOf("S", 0, frames, track = Timeline.A1, sourceInMs = sourceInMs)

    private fun measured(clip: Clip, profile: LoudnessProfile = steady()) =
        loudness.measure(clip, profile, fps) as LoudnessUseCase.Reading.Measured

    @Test
    fun `a clip measures its source under it, with its level applied`() {
        assertEquals(-20f, measured(sound()).lufs, 0.01f)
        assertEquals(-6f, measured(sound()).truePeakDbtp, 0.01f)
        assertEquals(-26f, measured(sound().copy(volumeDb = -6f)).lufs, 0.01f)
        assertEquals(0f, measured(sound().copy(volumeDb = 6f)).truePeakDbtp, 0.01f)

        // Only the source stretch under the clip counts: a loud second half of the file.
        val split = LoudnessProfile(100, FloatArray(200) { if (it < 100) energyOf(-30.0) else energyOf(-15.0) }, FloatArray(200) { 0.5f })
        assertEquals(-30f, measured(sound(frames = 60, sourceInMs = 1_000), split).lufs, 0.01f)
        assertEquals(-15f, measured(sound(frames = 60, sourceInMs = 12_000), split).lufs, 0.01f)
    }

    @Test
    fun `keyframes, fades, speed and reverse shape what is heard`() {
        // The first half of the file is loud, the second quiet.
        val split = LoudnessProfile(100, FloatArray(200) { if (it < 100) energyOf(-15.0) else energyOf(-35.0) }, FloatArray(200) { 0.5f })
        // 20 s clip whose level drops to -20 dB halfway (frame 300).
        val ducked = sound(frames = 600).copy(volumeKeyframes = listOf(VolumeKeyframe(299, 0f), VolumeKeyframe(300, -20f)))
        // Windows straddling the drop count too (gated values from a reference computation).
        assertEquals("loud part at full level", -15.066f, measured(ducked, split).lufs, 0.01f)
        assertEquals("reversed: the loud part plays ducked", -35f, measured(ducked.copy(reverse = true), split).lufs, 0.01f)

        assertEquals("a fade-in lowers the level a little", -20.776f, measured(sound().copy(fadeInSeconds = 1f)).lufs, 0.01f)

        // 5 s from 5 s into a file whose first 10 s are quiet: at 2x the clip reaches into the loud half.
        val halves = LoudnessProfile(100, FloatArray(200) { if (it < 100) energyOf(-30.0) else energyOf(-15.0) }, FloatArray(200) { 0.5f })
        assertEquals(-30f, measured(sound(frames = 150, sourceInMs = 5_000), halves).lufs, 0.01f)
        assertEquals(-15.128f, measured(sound(frames = 150, sourceInMs = 5_000).copy(speed = ClipSpeed.of(PlaybackSpeed.DOUBLE)), halves).lufs, 0.01f)
    }

    @Test
    fun `too short and silent clips have no reading`() {
        assertEquals(LoudnessUseCase.Reading.TooShort, loudness.measure(sound(frames = 10), steady(), fps))
        assertEquals(LoudnessUseCase.Reading.Silent, loudness.measure(sound(), steady(lufs = -90.0), fps))
        assertEquals("a muted clip is silent", LoudnessUseCase.Reading.Silent, loudness.measure(sound().copy(volumeDb = -60f), steady(), fps))
    }

    /** Sound clips on the audio tracks of a project whose media "a" and "b" have [profiles]. */
    private fun mix(vararg clips: Clip, disabled: Set<String> = emptySet(), mixer: (MixerState) -> MixerState = { it }): ProjectDocument {
        val timeline = timelineOf(*clips, disabled = disabled)
        return projectOf(timeline = timeline, media = listOf(assetOf("a"), assetOf("b")), mixer = mixer(MixerState.forTracks(timeline.tracks)))
    }

    private val profiles = mapOf("a" to steady(), "b" to steady())
    private val music = clipOf("S", 0, 300, track = Timeline.A1, assetId = "a")
    private val voice = clipOf("T", 0, 300, track = Timeline.A2, assetId = "b")

    private fun program(doc: ProjectDocument, from: Int? = null, until: Int? = null, sounds: Map<String, LoudnessProfile> = profiles) =
        (loudness.program(doc, sounds, from, until) as LoudnessUseCase.Reading.Measured)

    @Test
    fun `the program adds the audible clips under each moment, through faders, pan and the master`() {
        assertEquals(-20f, program(mix(music)).lufs, 0.01f)
        assertEquals("two uncorrelated sounds add up", -16.99f, program(mix(music, voice)).lufs, 0.01f)
        assertEquals("peaks add up as an upper bound (2 × -6 dBFS)", 0.02f, program(mix(music, voice)).truePeakDbtp, 0.01f)

        assertEquals("muted", -20f, program(mix(music, voice) { it.replace(it.channel(Timeline.A2).copy(mute = true)) }).lufs, 0.01f)
        assertEquals("solo", -20f, program(mix(music, voice) { it.replace(it.channel(Timeline.A1).copy(solo = true)) }).lufs, 0.01f)
        assertEquals("disabled track", -20f, program(mix(music, voice, disabled = setOf(Timeline.A2))).lufs, 0.01f)
        assertEquals("fader", -26f, program(mix(music) { it.replace(it.channel(Timeline.A1).copy(volumeDb = -6f)) }).lufs, 0.01f)
        assertEquals("master", -26f, program(mix(music) { it.copy(masterVolumeDb = -6f) }).lufs, 0.01f)
        assertEquals("hard pan keeps one side", -23.01f, program(mix(music) { it.replace(it.channel(Timeline.A1).copy(pan = -1f)) }).lufs, 0.01f)
    }

    @Test
    fun `the program is measured over the render range only`() {
        val quiet = mapOf("a" to steady(), "b" to steady(lufs = -30.0))
        val first = clipOf("S", 0, 150, track = Timeline.A1, assetId = "a")
        val second = clipOf("T", 150, 150, track = Timeline.A1, assetId = "b")
        assertEquals(-30f, program(mix(first, second), from = 150, until = 300, sounds = quiet).lufs, 0.01f)
        assertEquals(-20f, program(mix(first, second), from = 0, until = 150, sounds = quiet).lufs, 0.01f)
        assertEquals(LoudnessUseCase.Reading.Silent, loudness.program(mix(first), quiet, 150, 300))
        assertEquals("a clip without a profile is silent", LoudnessUseCase.Reading.Silent, loudness.program(mix(music), emptyMap()))
    }

    @Test
    fun `normalizing an export moves the master by the program's distance to the target, at most 24 dB up`() {
        val doc = mix(music, voice)
        val normalized = loudness.normalizedForExport(doc, profiles, -14f, null, null)!!
        assertEquals(-14f + 16.99f, normalized.mixer.masterVolumeDb, 0.01f)
        assertEquals(-14f, program(normalized).lufs, 0.01f)
        assertEquals("clips and faders stay as they are", doc.timeline, normalized.timeline)

        val faint = loudness.normalizedForExport(mix(music), mapOf("a" to steady(lufs = -60.0)), -14f, null, null)!!
        assertEquals(24f, faint.mixer.masterVolumeDb, 0f)
        assertEquals(null, loudness.normalizedForExport(mix(music), emptyMap(), -14f, null, null))
    }

    @Test
    fun `normalizing shifts the level and every volume keyframe by one gain`() {
        val keyed = sound().copy(volumeDb = -3f, volumeKeyframes = listOf(VolumeKeyframe(0, -3f), VolumeKeyframe(60, -9f)))
        val quietPeaks = steady(truePeakDbtp = -20.0)
        val before = measured(keyed, quietPeaks).lufs
        val result = loudness.normalize(timelineOf(keyed), "S", quietPeaks, -14f, fps)
        val after = result.timeline.clips.single()

        val gain = -14f - before
        assertEquals(-3f + gain, after.volumeDb, 0.01f)
        assertEquals(listOf(-3f + gain, -9f + gain), after.volumeKeyframes.map { it.volumeDb })
        assertEquals(-14f, measured(after, quietPeaks).lufs, 0.01f)
        assertEquals(String.format(Locale.US, "已标准化到 -14.0 LUFS · 增益 +%.1f dB", gain), result.message)
    }

    @Test
    fun `normalizing warns of clipping, stops at the level range and refuses what it cannot do`() {
        val hot = loudness.normalize(timelineOf(sound()), "S", steady(truePeakDbtp = -3.0), -14f, fps)
        assertEquals("已标准化到 -14.0 LUFS · 增益 +6.0 dB · 真峰值 +3.0 dBTP，可能削波", hot.message)

        val faint = loudness.normalize(timelineOf(sound()), "S", steady(lufs = -40.0, truePeakDbtp = -30.0), -14f, fps)
        assertEquals(12f, faint.timeline.clips.single().volumeDb, 0f)
        assertEquals("增益已到极限：-28.0 LUFS（目标 -14.0）", faint.message)

        assertEquals("响度已是 -20.0 LUFS", assertThrows(EditRejected::class.java) { loudness.normalize(timelineOf(sound()), "S", steady(), -20f, fps) }.message)
        assertThrows(EditRejected::class.java) { loudness.normalize(timelineOf(sound(), locked = setOf(Timeline.A1)), "S", steady(), -14f, fps) }
        assertEquals("所选片段没有音频", assertThrows(EditRejected::class.java) { loudness.normalize(timelineOf(clipOf("V", 0, 90)), "V", steady(), -14f, fps) }.message)
        assertEquals("片段太短，无法测量响度（至少 0.4 秒）", assertThrows(EditRejected::class.java) { loudness.normalize(timelineOf(sound(frames = 6)), "S", steady(), -14f, fps) }.message)
    }
}
