package com.example.prismstudio.p40b43a88.ui.audio

import com.example.prismstudio.p40b43a88.data.repository.NoiseProfileRepository
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.NoiseReductionControl
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.domain.usecase.VoiceTake
import com.example.prismstudio.p40b43a88.data.repository.VoiceRecorderRepository
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.LoudnessRepository
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MeterLevelsUseCase
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.components.TimelineScale
import com.example.prismstudio.p40b43a88.ui.openSession
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.DuckingControl
import com.example.prismstudio.p40b43a88.domain.model.DuckingPreset
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.EqControl
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.DynamicsPreset
import com.example.prismstudio.p40b43a88.domain.model.DynamicsControl
import com.example.prismstudio.p40b43a88.domain.model.EqBand

@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelTest {

    private val program = FakeProgramPlaybackRepository()

    /** Every source reads a constant 0.5 peak, i.e. about -6 dBFS. */
    private val previews = FakeMediaPreviewRepository()
    private val clock = TestClock()
    private val halfScaleDb = 20f * log10(0.5f)
    private val floorDb = MeterLevelsUseCase.FLOOR_DB

    private val footage = UiFixtures.video("v1")
    private val song = UiFixtures.audio("mu")

    // vA (V1) and its sound aA (A1) 0–90, the song on A2 30–150, a title on V2. 150 frames at 30 fps.
    private val clips = listOf(
        UiFixtures.clip("vA", start = 0, frames = 90, assetId = "v1", linkId = "L1"),
        UiFixtures.clip("aA", start = 0, frames = 90, trackId = Timeline.A1, assetId = "v1", linkId = "L1"),
        UiFixtures.clip("mus", start = 30, frames = 120, trackId = Timeline.A2, assetId = "mu"),
        UiFixtures.clip("ttl", start = 0, frames = 60, trackId = Timeline.V2, kind = ClipKind.TITLE),
    )

    private fun project(mixer: MixerState = MixerState.forTracks(Timeline.defaultTracks())) =
        UiFixtures.document(media = listOf(footage, song), clips = clips, mixer = mixer)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.audioStage(
        doc: ProjectDocument = project(),
        voice: VoiceRecorderRepository = VoiceRecorderRepository.None,
    ): Pair<DefaultEditorSessionRepository, AudioViewModel> {
        val session = openSession(doc, clock = clock)
        // In the app the workspace shell feeds every document to the program monitor.
        program.setProject(session.document.value)
        val model = AudioViewModel(session, program, previews, voice = voice, ids = SequentialIds(), clock = clock)
        advanceUntilIdle()
        return session to model
    }

    private fun DefaultEditorSessionRepository.clip(id: String): Clip = document.value!!.timeline.clip(id)!!

    /** Hands out [profiles] by uri once [gate] opens, recording what was asked. */
    private class FakeLoudness(private val profiles: Map<String, LoudnessProfile?>) : LoudnessRepository {
        val gate = CompletableDeferred<Unit>()
        val asked = mutableListOf<String>()
        override suspend fun profile(uri: String): LoudnessProfile? {
            asked += uri
            gate.await()
            return profiles[uri]
        }
    }

    /** 20 s at -20 LUFS with a 0.5 (-6 dBFS) sample peak. */
    private val steady = LoudnessProfile(100, FloatArray(200) { 10.0.pow((-20 + 0.691) / 10).toFloat() }, FloatArray(200) { 0.5f })

    private suspend fun TestScope.measuredStage(loudness: LoudnessRepository): Pair<DefaultEditorSessionRepository, AudioViewModel> {
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = AudioViewModel(session, program, previews, loudness = loudness)
        advanceUntilIdle()
        return session to model
    }

    @Test
    fun `the selected sound is measured once per file and its loudness follows the clip level`() = runTest {
        val loudness = FakeLoudness(mapOf(song.uri to steady))
        val (_, model) = measuredStage(loudness)
        assertEquals(LoudnessUi(), model.state.value.loudness)

        model.onEvent(AudioEvent.SelectClip("mus"))
        assertEquals("正在测量响度…", model.state.value.loudness.readout)
        assertFalse(model.state.value.loudness.canNormalize)
        model.onEvent(AudioEvent.NormalizeLoudness)
        assertEquals("响度还在测量中", model.state.value.toast)

        loudness.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(LoudnessUi("-20.0 LUFS · 真峰值 -6.0 dBTP", canNormalize = true), model.state.value.loudness)

        model.onEvent(AudioEvent.ClipGain(-6f))
        assertEquals("-26.0 LUFS · 真峰值 -12.0 dBTP", model.state.value.loudness.readout)
        model.onEvent(AudioEvent.SelectClip(null))
        model.onEvent(AudioEvent.SelectClip("mus"))
        // The program meter measures every sounding file too — each once.
        assertEquals("measured once", listOf(footage.uri, song.uri).sorted(), loudness.asked.sorted())
    }

    @Test
    fun `the program loudness meter reads the whole mix at the playhead`() = runTest {
        val loudness = FakeLoudness(mapOf(song.uri to steady, footage.uri to steady))
        val (session, model) = measuredStage(loudness)
        assertEquals("正在测量节目响度…", model.state.value.programLoudness.note)
        assertEquals(ProgramLoudnessUi.NO_READING, model.state.value.programLoudness.integrated)

        loudness.gate.complete(Unit)
        advanceUntilIdle()
        // aA (A1) plays 0–3 s and the song (A2) 1–5 s, both at -20 LUFS; at 2 s both sound.
        program.seekTo(2_000)
        advanceUntilIdle()
        val ui = model.state.value.programLoudness
        assertNull(ui.note)
        assertEquals("two sounds add 3 dB", "-17.0", ui.momentary)
        assertEquals("a second of silence before the start in the 3 s window", "-20.0", ui.shortTerm)
        assertEquals("-18.5", ui.integrated)
        assertEquals("0.0 dBTP", ui.peak)
        assertTrue("two 0.5 peaks reach full scale", ui.clipping)
        assertEquals(-14f, ui.targetLufs, 0f)
        assertFalse(ui.hot)
        assertEquals(300, ui.history.size)
        assertEquals(-20f, ui.history.last(), 0.05f)

        model.onEvent(AudioEvent.Volume(Timeline.A2, -6f))
        assertEquals("the song's fader moves it", "-19.0", model.state.value.programLoudness.momentary)
        model.onEvent(AudioEvent.LoudnessTarget(2))
        assertEquals(-23f, model.state.value.programLoudness.targetLufs, 0f)
        assertTrue("over the -23 target", model.state.value.programLoudness.hot)
        assertEquals("one undo step for the fader", "推子", session.history.value.undoLabel)
    }

    @Test
    fun `normalizing brings the selected sound to the chosen target in one undo step`() = runTest {
        val loudness = FakeLoudness(mapOf(footage.uri to steady)).apply { gate.complete(Unit) }
        val (session, model) = measuredStage(loudness)
        model.onEvent(AudioEvent.SelectClip("vA"))

        model.onEvent(AudioEvent.LoudnessTarget(2))
        model.onEvent(AudioEvent.NormalizeLoudness)

        assertEquals("the picture's linked sound", -3f, session.clip("aA").volumeDb, 0.01f)
        assertEquals("已标准化到 -23.0 LUFS · 增益 -3.0 dB", model.state.value.toast)
        assertEquals("响度标准化", session.history.value.undoLabel)
        assertEquals("-23.0 LUFS · 真峰值 -9.0 dBTP", model.state.value.loudness.readout)

        model.onEvent(AudioEvent.NormalizeLoudness)
        assertEquals("响度已是 -23.0 LUFS", model.state.value.toast)
        model.onEvent(AudioEvent.LoudnessTarget(9))
        assertEquals(LoudnessUseCase.TARGETS_LUFS.lastIndex, model.state.value.loudnessTarget)
    }

    @Test
    fun `a file without readable sound says so and cannot be normalized`() = runTest {
        val loudness = FakeLoudness(mapOf(song.uri to null)).apply { gate.complete(Unit) }
        val (session, model) = measuredStage(loudness)
        model.onEvent(AudioEvent.SelectClip("mus"))

        assertEquals(LoudnessUi("无法读取音频"), model.state.value.loudness)
        model.onEvent(AudioEvent.NormalizeLoudness)
        assertEquals("无法读取音频", model.state.value.toast)
        assertFalse(session.history.value.canUndo)

        model.onEvent(AudioEvent.SelectClip("ttl"))
        assertEquals("a title has no sound", LoudnessUi(), model.state.value.loudness)
    }

    private fun AudioViewModel.channel(trackId: String) = state.value.channels.first { it.trackId == trackId }

    @Test
    fun `a keyed clip gain shows the level under the playhead and records changes there`() = runTest {
        val keyed = project().let { d ->
            d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("mus")!!.copy(volumeKeyframes = listOf(VolumeKeyframe(0, 0f), VolumeKeyframe(60, -12f)))))
        }
        val (session, model) = audioStage(keyed)
        model.onEvent(AudioEvent.SelectClip("mus"))

        model.onEvent(AudioEvent.Seek(30 + 30))
        assertEquals(-6f, model.state.value.selection!!.gainDb, 0.01f)

        model.onEvent(AudioEvent.Seek(30 + 90))
        model.onEvent(AudioEvent.ClipGain(-3f))
        assertEquals(listOf(0 to 0f, 60 to -12f, 90 to -3f), session.clip("mus").volumeKeyframes.map { it.frame to it.volumeDb })

        model.onEvent(AudioEvent.Seek(10))
        assertTrue("the song starts at 30", model.state.value.selection!!.gainBlocked)
    }

    @Test
    fun `the audio page shows only audio tracks, their clips and one mixer strip per track`() = runTest {
        val (_, model) = audioStage()

        val state = model.state.value
        assertEquals(listOf(Timeline.A1, Timeline.A2), state.timeline.tracks.map { it.id })
        assertEquals(listOf("aA", "mus"), state.timeline.clips.map { it.id })
        assertEquals(150, state.timeline.durationFrames)
        assertEquals(listOf("A1", "A2"), state.channels.map { it.name })
        assertEquals(0f, state.masterDb, 0f)
        assertFalse(state.programEmpty)
        assertTrue(state.nothingSelected)
        assertEquals(setOf("v1", "mu"), state.timeline.waveforms.keys)
    }

    @Test
    fun `selecting a picture clip selects its linked sound`() = runTest {
        val (session, model) = audioStage()

        model.onEvent(AudioEvent.SelectClip("vA"))
        assertEquals("vA", session.selectedClipId.value)
        assertEquals(AudioClipUi("aA", "aA", 0f, false), model.state.value.selection)
        assertEquals("aA", model.state.value.timeline.selectedClipId)

        model.onEvent(AudioEvent.SelectClip("ttl"))
        assertNull("a title has no sound", model.state.value.selection)

        model.onEvent(AudioEvent.SelectClip("mus"))
        model.onEvent(AudioEvent.SelectClip(null))
        assertTrue(model.state.value.nothingSelected)
    }

    @Test
    fun `dragging a fade previews it and the drop records one fade capped at half the clip`() = runTest {
        val (session, model) = audioStage()

        model.onEvent(AudioEvent.Fade("mus", fadeIn = true, seconds = 1f, commit = false))
        assertEquals(1f, model.state.value.timeline.clips.first { it.id == "mus" }.fadeInSeconds, 0f)
        assertEquals(0f, session.clip("mus").fadeInSeconds, 0f)
        assertFalse(session.history.value.canUndo)

        model.onEvent(AudioEvent.Fade("mus", fadeIn = true, seconds = 10f, commit = true))
        assertEquals(2f, session.clip("mus").fadeInSeconds, 1e-4f)
        assertEquals(2f, model.state.value.timeline.clips.first { it.id == "mus" }.fadeInSeconds, 1e-4f)
        assertEquals("淡入", session.history.value.undoLabel)
        assertEquals("mus", session.selectedClipId.value)

        model.onEvent(AudioEvent.Fade("mus", fadeIn = false, seconds = 0.5f, commit = true))
        assertEquals(0.5f, session.clip("mus").fadeOutSeconds, 0f)
        assertEquals("淡出", session.history.value.undoLabel)
    }

    @Test
    fun `a fade's curve dot drags on the Audio page too - shown as it goes, one step when let go`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.Fade("mus", fadeIn = true, seconds = 1f, commit = true))
        model.onEvent(AudioEvent.FadeCurve("mus", fadeIn = true, curve = 0.5f, commit = false))
        assertEquals(0.5f, model.state.value.timeline.clips.first { it.id == "mus" }.fadeInCurve, 0f)
        assertEquals(0f, session.clip("mus").fadeInCurve, 0f)
        model.onEvent(AudioEvent.FadeCurve("mus", fadeIn = true, curve = 0.7f, commit = true))
        assertEquals(0.7f, session.clip("mus").fadeInCurve, 0f)
        assertEquals("淡入曲线", session.history.value.undoLabel)
        session.undo()
        assertEquals(0f, session.clip("mus").fadeInCurve, 0f)
        assertEquals("the fade itself stays", 1f, session.clip("mus").fadeInSeconds, 1e-4f)
    }

    @Test
    fun `the selected sound's own equaliser opens in the EQ sheet on the Audio page too, apart from its track's`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.OpenClipEq)
        assertEquals("先在时间线上选择一个声音片段", model.state.value.toast)
        model.onEvent(AudioEvent.SelectClip("mus"))
        assertEquals("平直", model.state.value.selection!!.eqSummary)
        model.onEvent(AudioEvent.OpenClipEq)
        val sheet = model.state.value.eqEditor!!
        assertEquals("mus" to "片段 · ${session.clip("mus").name}", sheet.trackId to sheet.name)
        assertEquals("只作用于这个片段，在轨道的均衡器之前", sheet.appliesTo)
        model.onEvent(AudioEvent.EqPresetPicked(EqPreset.VOICE))
        assertEquals(EqPreset.VOICE.eq, session.clip("mus").eq)
        assertEquals("片段均衡器预设：人声清晰", session.history.value.undoLabel)
        model.onEvent(AudioEvent.Eq(EqControl.MID_DB, 4f))
        model.onEvent(AudioEvent.Eq(EqControl.MID_DB, 5f))
        assertEquals(5f, session.clip("mus").eq.midDb, 0f)
        assertEquals(5f, model.state.value.eqEditor!!.eq.midDb, 0f)
        session.undo()
        assertEquals("the slider's burst is one step", EqPreset.VOICE.eq, session.clip("mus").eq)
        val track = session.clip("mus").trackId
        assertEquals("the track keeps its own", TrackEq.FLAT, session.document.value!!.mixer.channel(track).eq)
        assertTrue(model.state.value.selection!!.eqActive)
        assertEquals("预设：人声清晰", model.state.value.selection!!.eqSummary)
        // The track's EQ button opens the track's sheet instead.
        model.onEvent(AudioEvent.OpenEq(track))
        assertEquals(track, model.state.value.eqEditor!!.trackId)
        model.onEvent(AudioEvent.CloseEq)
        assertNull(model.state.value.eqEditor)
    }

    @Test
    fun `the volume line drags on the Audio page too, one step when let go`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.SelectClip("mus"))
        model.onEvent(AudioEvent.VolumeLine("mus", emptyList(), -4f, commit = false))
        assertEquals(-4f, model.state.value.timeline.clips.first { it.id == "mus" }.volumeDb, 0f)
        assertEquals("heard while dragging", -4f, program.document!!.timeline.clip("mus")!!.volumeDb, 0f)
        assertFalse(session.history.value.canUndo)
        model.onEvent(AudioEvent.VolumeLine("mus", emptyList(), -4f, commit = true))
        assertEquals(-4f, session.clip("mus").volumeDb, 0f)
        assertEquals("调整音量", session.history.value.undoLabel)
    }

    @Test
    fun `fades on a locked track are refused, with a toast only when dropped`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.TrackLocked(Timeline.A2, true))
        assertTrue(session.document.value!!.timeline.track(Timeline.A2)!!.locked)
        assertEquals("锁定轨道", session.history.value.undoLabel)

        model.onEvent(AudioEvent.Fade("mus", fadeIn = true, seconds = 1f, commit = false))
        assertNull(model.state.value.toast)
        assertEquals(0f, model.state.value.timeline.clips.first { it.id == "mus" }.fadeInSeconds, 0f)

        model.onEvent(AudioEvent.Fade("mus", fadeIn = true, seconds = 1f, commit = true))
        assertEquals("轨道已锁定", model.state.value.toast)
        assertEquals(0f, session.clip("mus").fadeInSeconds, 0f)

        model.onEvent(AudioEvent.ToastShown)
        assertNull(model.state.value.toast)
    }

    @Test
    fun `faders, pan and the master fader are clamped to their ranges`() = runTest {
        val (session, model) = audioStage()

        model.onEvent(AudioEvent.Volume(Timeline.A1, 20f))
        assertEquals(12f, model.channel(Timeline.A1).volumeDb, 0f)
        model.onEvent(AudioEvent.Volume(Timeline.A1, -90f))
        assertEquals(-60f, session.document.value!!.mixer.channel(Timeline.A1).volumeDb, 0f)
        assertEquals("推子", session.history.value.undoLabel)

        model.onEvent(AudioEvent.Pan(Timeline.A2, -3f))
        assertEquals(-1f, model.channel(Timeline.A2).pan, 0f)
        assertEquals("声像", session.history.value.undoLabel)

        model.onEvent(AudioEvent.MasterVolume(-75f))
        assertEquals(-60f, session.document.value!!.mixer.masterVolumeDb, 0f)
        assertEquals(-60f, model.state.value.masterDb, 0f)
        assertEquals("主输出推子", session.history.value.undoLabel)
    }

    @Test
    fun `a dynamics preset sets the compressor and limiter in one step and is named until a setting moves`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.OpenDynamics(Timeline.A1))
        assertEquals(null, model.state.value.dynamicsEditor!!.preset)
        model.onEvent(AudioEvent.DynamicsPresetPicked(DynamicsPreset.VOICE))
        val dyn = { session.document.value!!.mixer.channel(Timeline.A1).dynamics }
        assertEquals(DynamicsPreset.VOICE.dynamics, dyn())
        assertEquals("动态预设：人声压缩", session.history.value.undoLabel)
        assertEquals(DynamicsPreset.VOICE, model.state.value.dynamicsEditor!!.preset)
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.MAKEUP, 6f))
        assertEquals("自定义 once a setting moved", null, model.state.value.dynamicsEditor!!.preset)
        session.undo()
        session.undo()
        assertEquals(TrackDynamics.OFF, dyn())
    }

    @Test
    fun `an EQ preset sets the whole curve in one step and is named until a band moves`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.OpenEq(Timeline.A1))
        assertEquals("flat: no preset", null, model.state.value.eqEditor!!.preset)
        model.onEvent(AudioEvent.EqEnabled(false))
        model.onEvent(AudioEvent.EqPresetPicked(EqPreset.TELEPHONE))
        val eq = { session.document.value!!.mixer.channel(Timeline.A1).eq }
        assertEquals("it switches the EQ on", EqPreset.TELEPHONE.eq, eq())
        assertEquals("均衡器预设：电话音", session.history.value.undoLabel)
        assertEquals(EqPreset.TELEPHONE, model.state.value.eqEditor!!.preset)
        assertTrue(model.channel(Timeline.A1).eqActive)
        model.onEvent(AudioEvent.Eq(EqControl.MID_DB, 4f))
        assertEquals("自定义 once a band moved", null, model.state.value.eqEditor!!.preset)
        session.undo()
        session.undo()
        assertEquals("each was one step", false, eq().enabled)
    }

    @Test
    fun `a track's EQ sheet edits its bands, drags its curve, bypasses and resets, one undo step each`() = runTest {
        val (session, model) = audioStage()
        assertFalse(model.channel(Timeline.A1).eqActive)

        model.onEvent(AudioEvent.OpenEq(Timeline.A1))
        assertEquals(EqEditorUi(Timeline.A1, "A1", TrackEq.FLAT), model.state.value.eqEditor)

        model.onEvent(AudioEvent.Eq(EqControl.HIGH_DB, 3f))
        model.onEvent(AudioEvent.Eq(EqControl.HIGH_DB, 4.5f))
        val mixer = { session.document.value!!.mixer.channel(Timeline.A1).eq }
        assertEquals(4.5f, mixer().highDb)
        assertEquals("均衡器：高频增益", session.history.value.undoLabel)
        assertTrue("the strip's EQ chip lights", model.channel(Timeline.A1).eqActive)
        assertEquals(mixer(), model.state.value.eqEditor!!.eq)

        model.onEvent(AudioEvent.EqDrag(EqBand.MID, 2000f, -6f))
        assertEquals(2000f to -6f, mixer().midHz to mixer().midDb)
        assertEquals("均衡器：中频", session.history.value.undoLabel)
        model.onEvent(AudioEvent.EqLowCut(true))
        assertTrue(mixer().lowCut)
        model.onEvent(AudioEvent.EqEnabled(false))
        assertFalse("bypassed", model.channel(Timeline.A1).eqActive)
        assertEquals("均衡器：旁通", session.history.value.undoLabel)
        model.onEvent(AudioEvent.ResetEq)
        assertEquals(TrackEq.FLAT, mixer())
        assertEquals("other tracks keep theirs", TrackEq.FLAT, session.document.value!!.mixer.channel(Timeline.A2).eq)

        model.onEvent(AudioEvent.CloseEq)
        assertNull(model.state.value.eqEditor)
        model.onEvent(AudioEvent.Eq(EqControl.LOW_DB, 6f))
        assertEquals("a closed sheet edits nothing", TrackEq.FLAT, mixer())
    }

    @Test
    fun `a track's dynamics sheet switches the compressor and limiter, sets them and reads the reduction`() = runTest {
        val (session, model) = audioStage()
        assertFalse(model.channel(Timeline.A1).dynamicsActive)
        model.onEvent(AudioEvent.OpenEq(Timeline.A1))
        model.onEvent(AudioEvent.OpenDynamics(Timeline.A1))
        assertNull("one sheet at a time", model.state.value.eqEditor)
        val editor = model.state.value.dynamicsEditor!!
        assertEquals(TrackDynamics.OFF, editor.dynamics)
        assertEquals(0f, editor.reductionDb, 0f)

        val dyn = { session.document.value!!.mixer.channel(Timeline.A1).dynamics }
        model.onEvent(AudioEvent.DynamicsCompressor(true))
        assertTrue("the strip's DYN chip lights", model.channel(Timeline.A1).dynamicsActive)
        assertEquals("动态：开启压缩器", session.history.value.undoLabel)
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.THRESHOLD, -30f))
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.THRESHOLD, -36.04f))
        assertEquals(-36f, dyn().thresholdDb, 0f)
        assertEquals("动态：压缩器阈值", session.history.value.undoLabel)
        session.undo()
        assertEquals("a drag is one undo step", -20f, dyn().thresholdDb, 0f)
        session.redo()
        assertTrue("the playhead's sound is over the threshold: turned down", model.state.value.dynamicsEditor!!.reductionDb < 0f)
        assertEquals(dyn(), model.state.value.dynamicsEditor!!.dynamics)

        model.onEvent(AudioEvent.DynamicsLimiter(true))
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.CEILING, -3f))
        assertEquals(true to -3f, dyn().limiter to dyn().ceilingDb)
        // Fairlight's Expander/Gate, first in the chain.
        model.onEvent(AudioEvent.DynamicsGate(true))
        assertEquals("动态：开启门限", session.history.value.undoLabel)
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.GATE_THRESHOLD, -38.04f))
        model.onEvent(AudioEvent.Dynamics(DynamicsControl.GATE_HOLD, 81.6f))
        assertEquals(Triple(true, -38f, 82f), Triple(dyn().gate, dyn().gateThresholdDb, dyn().gateHoldMs))
        assertEquals("动态：门限保持", session.history.value.undoLabel)
        model.onEvent(AudioEvent.DynamicsEnabled(false))
        assertFalse("bypassed", model.channel(Timeline.A1).dynamicsActive)
        assertEquals(0f, model.state.value.dynamicsEditor!!.reductionDb, 0f)
        model.onEvent(AudioEvent.ResetDynamics)
        assertEquals(TrackDynamics.OFF, dyn())
        assertEquals("重置动态", session.history.value.undoLabel)

        model.onEvent(AudioEvent.CloseDynamics)
        assertNull(model.state.value.dynamicsEditor)
        model.onEvent(AudioEvent.DynamicsCompressor(true))
        assertEquals("a closed sheet edits nothing", TrackDynamics.OFF, dyn())
    }

    /** Noise floors handed out by file once [ready]; what was asked for is kept. */
    private class FakeNoiseProfiles : NoiseProfileRepository {
        val known = MutableStateFlow<Map<String, NoiseProfile>>(emptyMap())
        override val profiles: StateFlow<Map<String, NoiseProfile>> = known
        override val measuring: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        val asked = mutableListOf<String>()
        override fun request(assets: Collection<MediaAsset>) {
            asked += assets.map { it.uri }
        }
        override suspend fun await(assets: Collection<MediaAsset>) = Unit
    }

    @Test
    fun `a track's de-esser sheet sets the amount and band as undo steps, lights its chip and says what it does`() = runTest {
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = AudioViewModel(session, program, previews)
        advanceUntilIdle()
        val ess = { session.document.value!!.mixer.channel(Timeline.A1).deEsser }
        model.onEvent(AudioEvent.OpenDynamics(Timeline.A1))
        model.onEvent(AudioEvent.OpenDeEsser(Timeline.A1))
        assertNull("one sheet at a time", model.state.value.dynamicsEditor)
        assertEquals("强度为 0：声音不变", model.state.value.deEsserEditor!!.note)
        assertFalse(model.channel(Timeline.A1).deEsserActive)
        model.onEvent(AudioEvent.DeEsserAmount(40f))
        model.onEvent(AudioEvent.DeEsserAmount(60.4f))
        assertEquals(60, ess().amount)
        assertTrue("the strip's DS chip lights", model.channel(Timeline.A1).deEsserActive)
        assertEquals("the strip's 效果 chip counts it", "效果 1", model.channel(Timeline.A1).effectsLabel)
        assertEquals("6 kHz 以上 · 强度 60", model.channel(Timeline.A1).effects.single { it.effect == ChannelEffect.DE_ESSER }.summary)
        assertEquals("齿音消除：强度", session.history.value.undoLabel)
        assertEquals("6 kHz 以上的频段超过 -28 dBFS 时按 4:1 压低，最多 18 dB；在均衡器之后、动态之前", model.state.value.deEsserEditor!!.note)
        session.undo()
        assertEquals("one drag, one undo step", 0, ess().amount)
        model.onEvent(AudioEvent.DeEsserAmount(50f))
        model.onEvent(AudioEvent.DeEsserBandPicked(com.example.prismstudio.p40b43a88.domain.model.DeEssBand.HIGH))
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.DeEssBand.HIGH, ess().band)
        model.onEvent(AudioEvent.DeEsserEnabled(false))
        assertFalse(model.channel(Timeline.A1).deEsserActive)
        assertEquals("未启用：打开后只压低齿音频段，人声主体不受影响", model.state.value.deEsserEditor!!.note)
        model.onEvent(AudioEvent.ResetDeEsser)
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser.OFF, ess())
        model.onEvent(AudioEvent.CloseDeEsser)
        assertNull(model.state.value.deEsserEditor)
    }

    @Test
    fun `a track's reverb sheet picks a room, sets its controls as undo steps and joins the effects menu`() = runTest {
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = AudioViewModel(session, program, previews)
        advanceUntilIdle()
        val rv = { session.document.value!!.mixer.channel(Timeline.A1).reverb }
        val row = { model.channel(Timeline.A1).effects.single { it.effect == ChannelEffect.REVERB } }
        assertEquals("干湿比为 0：声音不变", row().summary)
        model.onEvent(AudioEvent.OpenDeEsser(Timeline.A1))
        model.onEvent(AudioEvent.OpenReverb(Timeline.A1))
        assertNull("one sheet at a time", model.state.value.deEsserEditor)
        assertEquals("干湿比为 0：声音不变；调高后听到房间的回响", model.state.value.reverbEditor!!.note)
        model.onEvent(AudioEvent.ReverbRoomPicked(com.example.prismstudio.p40b43a88.domain.model.ReverbRoom.HALL))
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.ReverbRoom.HALL, rv().room)
        assertEquals("大厅 · 2.2 秒 · 湿声 25 %", row().summary)
        assertEquals("效果 1", model.channel(Timeline.A1).effectsLabel)
        assertEquals("混响：大厅", session.history.value.undoLabel)
        assertEquals("实线：中低频 2.2 秒衰减 60 dB · 虚线：8 kHz 1.0 秒", model.state.value.reverbEditor!!.legend)
        model.onEvent(AudioEvent.ReverbDecay(3.04f))
        model.onEvent(AudioEvent.ReverbDecay(3.46f))
        assertEquals(3.5f, rv().decaySeconds, 0f)
        assertEquals("自定义 · 3.5 秒 · 湿声 25 %", row().summary)
        session.undo()
        assertEquals("one drag, one undo step", 2.2f, rv().decaySeconds, 0f)
        model.onEvent(AudioEvent.ReverbPreDelay(500f))
        assertEquals("held in range", 200, rv().preDelayMs)
        model.onEvent(AudioEvent.ReverbDamping(70.4f))
        model.onEvent(AudioEvent.ReverbMix(0f))
        assertFalse(rv().isActive)
        assertEquals("效果", model.channel(Timeline.A1).effectsLabel)
        model.onEvent(AudioEvent.ReverbMix(40f))
        model.onEvent(AudioEvent.ReverbEnabled(false))
        assertEquals("未启用：给声音加上房间的回响", row().summary)
        model.onEvent(AudioEvent.ResetReverb)
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.TrackReverb.OFF, rv())
        model.onEvent(AudioEvent.CloseReverb)
        assertNull(model.state.value.reverbEditor)
    }

    @Test
    fun `a track's noise reduction sheet switches it, sets it, shows its file's floor and lights its chip`() = runTest {
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val floors = FakeNoiseProfiles()
        val model = AudioViewModel(session, program, previews, noiseProfiles = floors)
        advanceUntilIdle()
        assertFalse(model.channel(Timeline.A2).noiseActive)
        model.onEvent(AudioEvent.OpenDynamics(Timeline.A2))
        model.onEvent(AudioEvent.OpenNoiseReduction(Timeline.A2))
        assertNull("one sheet at a time", model.state.value.dynamicsEditor)
        assertEquals("the track's files are measured for the graph", listOf(song.uri), floors.asked)
        var editor = model.state.value.noiseEditor!!
        assertEquals(TrackNoiseReduction.OFF, editor.noiseReduction)
        assertTrue(editor.floorDb.isEmpty())
        assertEquals("还没有“${song.name}”的噪声底：打开降噪后会自动测量", editor.status)

        // The floor arrives: white noise of RMS 0.01 reads -40 dB in every band.
        floors.known.value = mapOf(song.uri to NoiseProfile(48_000, FloatArray(513) { 0.0001f * 512 }))
        advanceUntilIdle()
        editor = model.state.value.noiseEditor!!
        assertEquals(513, editor.floorDb.size)
        assertEquals(-40f, editor.floorDb[100], 0.01f)
        assertEquals(46.875f, editor.floorStepHz, 0.001f)
        assertEquals("噪声底来自“${song.name}”（轨道上的第一个片段）：每个文件各自测量，压低的是接近它的声音", editor.status)

        val nr = { session.document.value!!.mixer.channel(Timeline.A2).noiseReduction }
        model.onEvent(AudioEvent.NoiseReductionEnabled(true))
        assertTrue("the strip's NR chip lights", model.channel(Timeline.A2).noiseActive)
        assertEquals("降噪：开启", session.history.value.undoLabel)
        model.onEvent(AudioEvent.NoiseReduction(NoiseReductionControl.REDUCTION, 15f))
        model.onEvent(AudioEvent.NoiseReduction(NoiseReductionControl.REDUCTION, 18.2f))
        assertEquals(18f, nr().reductionDb, 0f)
        session.undo()
        assertEquals("a drag is one undo step", 12f, nr().reductionDb, 0f)
        model.onEvent(AudioEvent.NoiseReduction(NoiseReductionControl.THRESHOLD, 9f))
        assertEquals(nr(), model.state.value.noiseEditor!!.noiseReduction)
        model.onEvent(AudioEvent.ResetNoiseReduction)
        assertEquals(TrackNoiseReduction.OFF, nr())
        assertEquals("重置降噪", session.history.value.undoLabel)
        model.onEvent(AudioEvent.CloseNoiseReduction)
        assertNull(model.state.value.noiseEditor)
        model.onEvent(AudioEvent.NoiseReductionEnabled(true))
        assertEquals("a closed sheet edits nothing", TrackNoiseReduction.OFF, nr())
    }

    @Test
    fun `a track's ducking sheet keys it from another track, shows the dip and lights its chip`() = runTest {
        // The dialogue (aA on A1) speaks 0–3 s at -20 LUFS; the song (A2) plays 1–5 s.
        val loudness = FakeLoudness(mapOf(footage.uri to steady, song.uri to steady))
        val (session, model) = measuredStage(loudness)
        assertFalse(model.channel(Timeline.A2).duckingActive)
        model.onEvent(AudioEvent.OpenDynamics(Timeline.A2))
        model.onEvent(AudioEvent.OpenDucking(Timeline.A2))
        assertNull("one sheet at a time", model.state.value.dynamicsEditor)
        var editor = model.state.value.duckingEditor!!
        assertEquals(listOf(DuckKeyUi(Timeline.A1, "A1")), editor.keys)
        assertFalse(editor.on)
        assertEquals("未开启：打开后，侧链轨道发声时这条轨道自动压低", editor.status)
        assertTrue("nothing to draw while off", editor.keyLevels.isEmpty() && editor.amounts.isEmpty())

        val duck = { session.document.value!!.mixer.channel(Timeline.A2).ducking }
        model.onEvent(AudioEvent.DuckingEnabled(true))
        assertEquals("keyed from the other sound track", Timeline.A1, duck().keyTrackId)
        assertEquals("闪避：开启", session.history.value.undoLabel)
        assertTrue("the strip's chip lights", model.channel(Timeline.A2).duckingActive)
        editor = model.state.value.duckingEditor!!
        assertTrue(editor.on)
        assertEquals("正在分析 A1 的响度…", editor.status)
        // The key's media, and (for the program meter) every other sounding file — each once.
        assertEquals(listOf(footage.uri, song.uri).sorted(), loudness.asked.sorted())

        loudness.gate.complete(Unit)
        advanceUntilIdle()
        editor = model.state.value.duckingEditor!!
        assertEquals("A1 高于阈值时压低这条轨道，在推子之前", editor.status)
        assertEquals("8 s every 50 ms", 161, editor.keyLevels.size)
        assertEquals(161, editor.amounts.size)
        assertEquals("before the timeline: silence", DuckingUseCase.FLOOR_DB, editor.keyLevels.first(), 0f)
        assertEquals("the playhead (0 s): the dialogue", -20f, editor.keyLevelDb, 0.01f)
        assertEquals(1f, editor.amounts[80], 1e-4f)
        assertEquals("already down: the dialogue starts at 0", -12f, editor.duckDb, 1e-3f)

        model.onEvent(AudioEvent.Ducking(DuckingControl.DEPTH, 20f))
        model.onEvent(AudioEvent.Ducking(DuckingControl.DEPTH, 18.04f))
        assertEquals(18f, duck().depthDb, 0f)
        assertEquals("闪避：深度", session.history.value.undoLabel)
        session.undo()
        assertEquals("a drag is one undo step", 12f, duck().depthDb, 0f)
        session.redo()
        model.onEvent(AudioEvent.Seek(60))
        assertEquals("2 s: the song under the dialogue", -18f, model.state.value.duckingEditor!!.duckDb, 1e-3f)
        assertEquals(-18f, model.channel(Timeline.A2).levelDb - halfScaleDb, 0.01f)
        model.onEvent(AudioEvent.Seek(140))
        assertEquals("4.7 s: back up 0.8 s after the dialogue", 0f, model.state.value.duckingEditor!!.duckDb, 0f)

        model.onEvent(AudioEvent.DuckingEnabled(false))
        assertFalse("bypassed", model.channel(Timeline.A2).duckingActive)
        assertEquals("已旁通", model.state.value.duckingEditor!!.status)
        assertEquals("it keeps its key", Timeline.A1, duck().keyTrackId)
        model.onEvent(AudioEvent.DuckingKey(Timeline.A1))
        assertTrue("choosing a key switches it on", duck().enabled)
        assertEquals("闪避：侧链 A1", session.history.value.undoLabel)
        model.onEvent(AudioEvent.DuckingKey(Timeline.A2))
        assertEquals("never keyed from itself", Timeline.A1, duck().keyTrackId)
        model.onEvent(AudioEvent.ResetDucking)
        assertEquals(TrackDucking.OFF, duck())
        assertEquals("重置闪避", session.history.value.undoLabel)
        assertEquals("the reset settings are the narrator preset's", DuckingPreset.VOICE_OVER, model.state.value.duckingEditor!!.preset)
        model.onEvent(AudioEvent.DuckingPresetPicked(DuckingPreset.DIALOGUE))
        assertEquals("a preset switches it on, keyed from the other track", Timeline.A1, duck().keyTrackId)
        assertTrue(duck().enabled)
        assertEquals(15f to 80f, duck().depthDb to duck().attackMs)
        assertEquals("闪避预设：对白", session.history.value.undoLabel)
        assertEquals(DuckingPreset.DIALOGUE, model.state.value.duckingEditor!!.preset)
        model.onEvent(AudioEvent.Ducking(DuckingControl.DEPTH, 16f))
        assertNull("a control moved off the preset", model.state.value.duckingEditor!!.preset)
        session.undo()
        session.undo()
        assertEquals("the preset is one undo step", TrackDucking.OFF, duck())
        model.onEvent(AudioEvent.DuckingEnabled(false))
        model.onEvent(AudioEvent.DuckingKey(Timeline.A1))
        model.onEvent(AudioEvent.DuckingEnabled(false))
        model.onEvent(AudioEvent.DuckingPresetPicked(DuckingPreset.SUBTLE))
        assertTrue("a bypassed ducking comes back on with the preset", duck().enabled)
        assertEquals(6f, duck().depthDb, 0f)
        model.onEvent(AudioEvent.ResetDucking)

        model.onEvent(AudioEvent.CloseDucking)
        assertNull(model.state.value.duckingEditor)
        model.onEvent(AudioEvent.DuckingEnabled(true))
        assertEquals("a closed sheet edits nothing", TrackDucking.OFF, duck())
    }

    @Test
    fun `mute and solo are undoable mixer edits`() = runTest {
        val (session, model) = audioStage()

        model.onEvent(AudioEvent.Mute(Timeline.A1, true))
        assertTrue(model.channel(Timeline.A1).mute)
        assertEquals("静音", session.history.value.undoLabel)

        model.onEvent(AudioEvent.Solo(Timeline.A2, true))
        assertTrue(model.channel(Timeline.A2).solo)
        assertEquals("独奏", session.history.value.undoLabel)

        session.undo()
        assertFalse(model.channel(Timeline.A2).solo)
        session.undo()
        assertFalse(model.channel(Timeline.A1).mute)
    }

    @Test
    fun `a mixer change on a track without a channel adds the channel`() = runTest {
        val (session, model) = audioStage(project(mixer = MixerState(emptyList())))

        model.onEvent(AudioEvent.Mute(Timeline.A1, true))

        assertEquals(listOf(MixerChannel(Timeline.A1, mute = true)), session.document.value!!.mixer.channels)
        assertTrue(model.channel(Timeline.A1).mute)
    }

    @Test
    fun `clip gain edits the selected sound and is clamped to +12 dB`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.ClipGain(6f))
        assertFalse("nothing selected, nothing changed", session.history.value.canUndo)

        model.onEvent(AudioEvent.SelectClip("mus"))
        model.onEvent(AudioEvent.ClipGain(20f))

        assertEquals(12f, session.clip("mus").volumeDb, 0f)
        assertEquals(12f, model.state.value.selection!!.gainDb, 0f)
        assertEquals("片段增益", session.history.value.undoLabel)
    }

    @Test
    fun `meters read the source peaks at the playhead through the mix`() = runTest {
        val (_, model) = audioStage()

        model.onEvent(AudioEvent.Seek(60))
        assertEquals(60, model.state.value.playheadFrame)
        assertEquals(2_000L, program.state.value.positionMs)
        assertEquals(halfScaleDb, model.channel(Timeline.A1).levelDb, 0.01f)
        assertEquals(halfScaleDb, model.channel(Timeline.A2).levelDb, 0.01f)
        assertEquals("two half-scale sources sum to full scale", 0f, model.state.value.masterLevelDb, 0.01f)

        model.onEvent(AudioEvent.Mute(Timeline.A1, true))
        assertEquals(floorDb, model.channel(Timeline.A1).levelDb, 0f)
        assertEquals(halfScaleDb, model.state.value.masterLevelDb, 0.01f)

        model.onEvent(AudioEvent.Seek(10_000))
        assertEquals("seeking stops at the end of the timeline", 150, model.state.value.playheadFrame)
        assertEquals(floorDb, model.channel(Timeline.A2).levelDb, 0f)
        assertEquals(floorDb, model.state.value.masterLevelDb, 0f)
    }

    @Test
    fun `the first viewport width fits the timeline zoom and later ones keep it`() = runTest {
        val (_, model) = audioStage()

        model.onEvent(AudioEvent.Viewport(600f))
        val fitted = TimelineScale.fitZoom(150, 30, 600f)
        assertEquals(fitted, model.state.value.timeline.zoom, 1e-6f)

        model.onEvent(AudioEvent.Viewport(1_200f))
        assertEquals(fitted, model.state.value.timeline.zoom, 1e-6f)
    }

    /** A microphone that records whatever [take] says, at [level]. */
    private class FakeVoice(var take: VoiceTake? = VoiceTake("file:///rec/vo-1.wav", 2_500, 240_044), var startOk: Boolean = true) :
        VoiceRecorderRepository {
        var recording = false
        var cancelled = false
        override var failure: VoiceRecorderRepository.Failure? = null
        var level = 0.5f
        override fun start(): Boolean = startOk.also { recording = it }
        override fun level(): Float = if (recording) level else 0f
        override fun stop(): VoiceTake? = take.also { recording = false }
        override fun cancel() {
            recording = false
            cancelled = true
        }
    }

    @Test
    fun `a narration records against the playing program and lands on a new track where it started`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.Seek(30))
        model.onEvent(AudioEvent.OpenVoiceover)
        val sheet = model.state.value.voiceover!!
        assertEquals(listOf("A1", "A2", "新建音轨"), sheet.targets.map { it.label })
        assertNull("a new track by default", sheet.targetId)
        assertEquals("从播放头 00:00:01:00 开始，录音时节目从这里播放", sheet.startLabel)
        assertEquals("放在新建的音频轨上，不影响已有的声音", sheet.note)
        model.onEvent(AudioEvent.StartVoiceover)
        assertTrue(voice.recording)
        assertTrue("the program plays", program.state.value.playing)
        assertTrue("without its sound by default", program.monitorMuted)
        clock.now += 1_200
        advanceTimeBy(250)
        assertEquals("00:01", model.state.value.voiceover!!.elapsedLabel)
        assertTrue(model.state.value.voiceover!!.level > 0f)
        model.onEvent(AudioEvent.StopVoiceover)
        assertFalse(program.state.value.playing)
        assertFalse(program.monitorMuted)
        assertNull(model.state.value.voiceover)
        val doc = session.document.value!!
        val take = doc.timeline.clips.single { it.name == "旁白 1" }
        assertEquals("a3", take.trackId)
        assertEquals(30, take.start)
        assertEquals(75, take.durationFrames)
        assertEquals("旁白 1.wav", doc.asset(take.assetId)!!.name)
        assertEquals(take.id, session.selectedClipId.value)
        assertEquals("录制旁白", session.history.value.undoLabel)
        assertEquals("已录制“旁白 1”（00:00:02:15），放在 A3 的 00:00:01:00", model.state.value.toast)
    }

    @Test
    fun `a storage error releases the microphone and leaves the same form ready to retry`() = runTest {
        val voice = FakeVoice(startOk = false)
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.VoiceoverTarget(Timeline.A2))
        voice.failure = VoiceRecorderRepository.Failure.STORAGE
        model.onEvent(AudioEvent.StartVoiceover)
        assertEquals("无法保存录音，请检查设备存储空间后重试。", model.state.value.voiceover!!.recordingError)
        assertFalse(model.state.value.voiceover!!.microphoneDenied)
        voice.startOk = true
        voice.failure = null
        model.onEvent(AudioEvent.StartVoiceover)
        assertNull(model.state.value.voiceover!!.recordingError)
        // A write error during capture is observed by the meter's next tick, without waiting for a Stop tap.
        val usableTake = voice.take
        voice.take = null
        voice.failure = VoiceRecorderRepository.Failure.STORAGE
        advanceTimeBy(250)
        assertFalse(voice.recording)
        assertFalse(program.monitorMuted)
        assertFalse(program.state.value.playing)
        assertEquals(Timeline.A2, model.state.value.voiceover!!.targetId)
        assertEquals("无法保存录音，请检查设备存储空间后重试。", model.state.value.voiceover!!.recordingError)
        assertFalse(session.history.value.canUndo)
        voice.take = usableTake
        voice.failure = null
        model.onEvent(AudioEvent.StartVoiceover)
        model.onEvent(AudioEvent.StopVoiceover)
        assertEquals(Timeline.A2, session.document.value!!.timeline.clips.single { it.name == "旁白 1" }.trackId)
    }

    @Test
    fun `closing the project finishes the narration before the last save and preserves the clip on reopen`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        val projectId = session.document.value!!.id
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.VoiceoverTarget(Timeline.A2))
        model.onEvent(AudioEvent.StartVoiceover)
        try {
            assertTrue(session.close())
            assertFalse("Closing the project left the microphone active", voice.recording)
            assertTrue(session.open(projectId))
            assertEquals(Timeline.A2, session.document.value!!.timeline.clips.single { it.name == "旁白 1" }.trackId)
        } finally {
            model.onEvent(AudioEvent.CloseVoiceover)
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `background flush saves the active narration only once`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.StartVoiceover)
        try {
            session.flush()
            session.flush()
            model.onEvent(AudioEvent.StopVoiceover)
            assertFalse(voice.recording)
            assertEquals(1, session.document.value!!.media.count { it.name.startsWith("旁白") })
            assertEquals(listOf("录制旁白"), session.historyLabels.value.undo)
            assertFalse(session.saveState.value.pending)
        } finally {
            model.onEvent(AudioEvent.CloseVoiceover)
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `a track locked during a take keeps the WAV in project media without overwriting locked clips`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        val original = session.document.value!!.timeline.clipsOn(Timeline.A2)
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.VoiceoverTarget(Timeline.A2))
        model.onEvent(AudioEvent.StartVoiceover)
        model.onEvent(AudioEvent.TrackLocked(Timeline.A2, true))
        model.onEvent(AudioEvent.StopVoiceover)
        val saved = session.document.value!!
        assertEquals(original, saved.timeline.clipsOn(Timeline.A2))
        assertEquals(voice.take!!.uri, saved.media.single { it.name == "旁白 1.wav" }.uri)
        assertTrue(model.state.value.toast!!.contains("素材"))
        assertTrue(model.state.value.toast!!.contains("锁定"))
        assertEquals("录制旁白", session.undo())
        assertTrue(session.document.value!!.timeline.track(Timeline.A2)!!.locked)
    }

    @Test
    fun `delayed form changes and a duplicate open preserve the active narration and its original track`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.VoiceoverTarget(Timeline.A2))
        model.onEvent(AudioEvent.VoiceoverProgramSound(true))
        model.onEvent(AudioEvent.StartVoiceover)
        try {
            val recording = model.state.value.voiceover!!
            for (event in listOf(AudioEvent.VoiceoverTarget(Timeline.A1), AudioEvent.VoiceoverProgramSound(false), AudioEvent.OpenVoiceover)) {
                model.onEvent(event)
                assertEquals("The active recording changed after $event", recording, model.state.value.voiceover)
            }
            model.onEvent(AudioEvent.StopVoiceover)
            assertFalse(voice.recording)
            assertEquals(Timeline.A2, session.document.value!!.timeline.clips.single { it.name == "旁白 1" }.trackId)
        } finally {
            model.onEvent(AudioEvent.CloseVoiceover)
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `a narration on an existing track covers what it had, and closing while recording throws the take away`() = runTest {
        val voice = FakeVoice()
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.Seek(60))
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.VoiceoverTarget(Timeline.A2))
        assertEquals("放在 A2 上：录音覆盖这条轨在这段时间里原有的声音", model.state.value.voiceover!!.note)
        model.onEvent(AudioEvent.VoiceoverProgramSound(true))
        model.onEvent(AudioEvent.StartVoiceover)
        assertFalse("the program is heard when asked", program.monitorMuted)
        model.onEvent(AudioEvent.StopVoiceover)
        val a2 = session.document.value!!.timeline.clipsOn(Timeline.A2)
        assertEquals(listOf(30 to 30, 60 to 75, 135 to 15), a2.map { it.start to it.durationFrames })
        assertTrue(model.state.value.toast!!.endsWith("，覆盖了原有声音"))
        // A second take, given up: nothing changes.
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.StartVoiceover)
        model.onEvent(AudioEvent.CloseVoiceover)
        assertTrue(voice.cancelled)
        assertEquals("已取消录音", model.state.value.toast)
        assertEquals(1, session.document.value!!.timeline.clips.count { it.name.startsWith("旁白") })
    }

    @Test
    fun `no microphone, no permission or too short a take says why`() = runTest {
        val voice = FakeVoice(startOk = false)
        val (session, model) = audioStage(voice = voice)
        model.onEvent(AudioEvent.OpenVoiceover)
        model.onEvent(AudioEvent.StartVoiceover)
        assertEquals("无法使用麦克风：请检查麦克风权限，或麦克风是否被占用", model.state.value.voiceover!!.recordingError)
        assertFalse(model.state.value.voiceover!!.recording)
        model.onEvent(AudioEvent.VoiceoverDenied)
        assertTrue(model.state.value.voiceover!!.microphoneDenied)
        model.onEvent(AudioEvent.VoiceoverPermissionRestored)
        assertFalse(model.state.value.voiceover!!.microphoneDenied)
        assertFalse("granting access in Settings does not start recording", voice.recording)
        voice.startOk = true
        voice.take = null
        model.onEvent(AudioEvent.StartVoiceover)
        model.onEvent(AudioEvent.StopVoiceover)
        assertEquals("录音太短，没有保存", model.state.value.toast)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `submix buses - a track goes to a new bus, the bus strip shows its tracks and meter, its EQ opens in the sheet, deleting hands the track back`() = runTest {
        val (session, model) = audioStage()
        model.onEvent(AudioEvent.MixerView(1))
        assertEquals(1, model.state.value.mixerView)
        model.onEvent(AudioEvent.AddBus(Timeline.A2))
        assertEquals("已添加总线 1，A2 输出到它", model.state.value.toast)
        val mixer = { session.document.value!!.mixer }
        assertEquals("b1", mixer().channel(Timeline.A2).bus)
        assertEquals(listOf("B1", null), listOf(Timeline.A2, Timeline.A1).map { model.channel(it).busShort })
        val strip = model.state.value.buses.single()
        assertEquals(listOf("总线 1", "A2"), listOf(strip.name, strip.members))
        model.onEvent(AudioEvent.TrackOutput(Timeline.A1, "b1"))
        assertEquals("A1 输出到总线 1", model.state.value.toast)
        assertEquals("A1、A2", model.state.value.buses.single().members)
        // The fader is one undo step per drag; mute and solo are steps of their own.
        model.onEvent(AudioEvent.BusVolume("b1", -3f))
        model.onEvent(AudioEvent.BusVolume("b1", -6f))
        assertEquals(-6f, mixer().bus("b1")!!.volumeDb)
        assertEquals("总线推子", session.history.value.undoLabel)
        model.onEvent(AudioEvent.BusMute("b1", true))
        assertTrue(model.state.value.buses.single().mute)
        assertEquals("a muted bus reads the floor", floorDb, model.state.value.buses.single().levelDb)
        session.undo()
        session.undo()
        assertEquals(0f, mixer().bus("b1")!!.volumeDb)
        // Its EQ in the sheet.
        model.onEvent(AudioEvent.OpenBusEq("b1"))
        assertEquals(listOf("总线 1", "作用于输出到这条总线的所有轨道，在各轨道的处理之后"), model.state.value.eqEditor!!.let { listOf(it.name, it.appliesTo) })
        model.onEvent(AudioEvent.EqPresetPicked(EqPreset.VOICE))
        assertEquals(EqPreset.VOICE.eq, mixer().bus("b1")!!.eq)
        assertEquals("the track's own EQ is untouched", false, mixer().channel(Timeline.A1).eq.isActive)
        assertTrue(model.state.value.buses.single().eqActive)
        model.onEvent(AudioEvent.OpenEq(Timeline.A1))
        assertEquals("A1", model.state.value.eqEditor!!.name)
        // Three at most; deleting hands the tracks back to the main output.
        model.onEvent(AudioEvent.AddBus())
        model.onEvent(AudioEvent.AddBus())
        assertFalse(model.state.value.canAddBus)
        model.onEvent(AudioEvent.AddBus())
        assertEquals("最多 3 条总线", model.state.value.toast)
        model.onEvent(AudioEvent.RemoveBus("b1"))
        assertEquals("已删除总线 1，A1、A2 回到主输出", model.state.value.toast)
        assertEquals(listOf(null, null), listOf(Timeline.A1, Timeline.A2).map { model.channel(it).busId })
        assertEquals(listOf("总线 2", "总线 3"), model.state.value.buses.map { it.name })
    }
}
