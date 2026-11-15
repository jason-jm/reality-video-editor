package com.example.prismstudio.p40b43a88.ui.edit

import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.track
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionScope
import com.example.prismstudio.p40b43a88.ui.edit.components.angleLabel

import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.LibraryArt
import com.example.prismstudio.p40b43a88.ui.components.TransitionSpanUi
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.EqControl
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.DocumentFailure
import com.example.prismstudio.p40b43a88.data.repository.DocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeSourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.PickedDocument
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopy
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.StillFrameRepository
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.LibraryTab
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleAnimation
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.components.TimelineScale
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.openSession
import java.nio.charset.Charset
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt
import org.junit.Before
import com.example.prismstudio.p40b43a88.domain.model.CurveEase
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyleControl
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.SubtitleColor
import com.example.prismstudio.p40b43a88.domain.usecase.ClipAttribute
import com.example.prismstudio.p40b43a88.domain.usecase.LinkAction
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.KeyControl
import com.example.prismstudio.p40b43a88.domain.model.KeyScreen
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.model.SourceMarks
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.domain.model.CameraPath
import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import com.example.prismstudio.p40b43a88.data.repository.FakeStabilizationRepository
import com.example.prismstudio.p40b43a88.data.repository.StabilizationRepository
import com.example.prismstudio.p40b43a88.domain.usecase.SoundEnvelope
import com.example.prismstudio.p40b43a88.data.repository.FakeSoundEnvelopeRepository
import com.example.prismstudio.p40b43a88.data.repository.SoundEnvelopeRepository
import com.example.prismstudio.p40b43a88.domain.usecase.LumaFrames
import com.example.prismstudio.p40b43a88.data.repository.FakeLumaFrameRepository
import com.example.prismstudio.p40b43a88.data.repository.LumaFrameRepository
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexFilter
import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline

@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModelTest {

    private val program = FakeProgramPlaybackRepository()
    private val source = FakeSourcePlaybackRepository()
    private val previews = FakeMediaPreviewRepository()
    private val ids = SequentialIds()
    private val clock = TestClock()

    private val footage = UiFixtures.video("v1", durationMs = 20_000)
    private val song = UiFixtures.audio("au", durationMs = 8_000)
    private val still = UiFixtures.image("im")

    // V1: vA 0–90 (sound linked on A1 as aA) then the still vB 90–150; A2: the song 0–120. 150 frames at 30 fps.
    private val vA = UiFixtures.clip("vA", start = 0, frames = 90, assetId = "v1", linkId = "L1")
    private val aA = UiFixtures.clip("aA", start = 0, frames = 90, trackId = Timeline.A1, assetId = "v1", linkId = "L1")
    private val vB = UiFixtures.clip("vB", start = 90, frames = 60, assetId = "im")
    private val mus = UiFixtures.clip("mus", start = 0, frames = 120, trackId = Timeline.A2, assetId = "au")

    private fun project(media: List<MediaAsset> = listOf(footage, song, still)) =
        UiFixtures.document(media = media, clips = listOf(vA, aA, vB, mus))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.editStage(
        doc: ProjectDocument = project(),
        copies: ReverseCopyRepository = ReverseCopyRepository.None,
        stills: StillFrameRepository = StillFrameRepository.None,
        stabilization: StabilizationRepository = StabilizationRepository.None,
        envelopes: SoundEnvelopeRepository = SoundEnvelopeRepository.None,
        lumaFrames: LumaFrameRepository = LumaFrameRepository.None,
    ): Pair<DefaultEditorSessionRepository, EditViewModel> {
        val session = openSession(doc, clock = clock)
        // In the app the workspace shell feeds every document to the program monitor.
        program.setProject(session.document.value)
        val model = EditViewModel(
            session, program, source, previews, ids, copies, stills = stills, stabilization = stabilization, envelopes = envelopes, lumaFrames = lumaFrames, clock = clock,
            compute = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        return session to model
    }

    private fun DefaultEditorSessionRepository.clip(id: String): Clip = document.value!!.timeline.clip(id)!!

    @Test
    fun `the timeline opens fitted to its panel until the editor zooms`() = runTest {
        val (session, model) = editStage()
        val doc = session.document.value!!
        assertEquals("before the panel is measured", 0.35f, model.state.value.timeline.zoom, 0f)
        model.onEvent(EditEvent.Viewport(1200f))
        val fitted = TimelineScale.fitZoom(doc.durationFrames, doc.format.fps, 1200f)
        assertEquals(fitted, model.state.value.timeline.zoom, 1e-6f)
        model.onEvent(EditEvent.Viewport(800f))
        assertEquals("a resize refits", TimelineScale.fitZoom(doc.durationFrames, doc.format.fps, 800f), model.state.value.timeline.zoom, 1e-6f)
        model.onEvent(EditEvent.Zoom(0.6f))
        model.onEvent(EditEvent.Viewport(1200f))
        assertEquals("a hand-set zoom stays", 0.6f, model.state.value.timeline.zoom, 1e-6f)
    }

    @Test
    fun `publishes the timeline, the media library and the program summary`() = runTest {
        val (_, model) = editStage()

        val state = model.state.value
        assertEquals(listOf("vA", "aA", "vB", "mus"), state.timeline.clips.map { it.id })
        assertEquals(Timeline.defaultTracks(), state.timeline.tracks)
        assertEquals(150, state.timeline.durationFrames)
        assertEquals("1920×1080 · 30 fps · 00:00:05:00", state.summary)
        assertEquals("时间线 1", state.timelineName)
        assertFalse(state.programEmpty)
        assertNull(state.programMessage)
        assertTrue(state.nothingSelected)
        assertEquals(listOf("视频 · 00:20", "音频 · 00:08", "图片 · 5 秒"), state.libraryItems.map { it.subtitle })
        assertEquals("audio lanes get their waveforms", setOf("v1", "au"), state.timeline.waveforms.keys)
    }

    @Test
    fun `an empty timeline tells the program monitor so`() = runTest {
        val (_, model) = editStage(UiFixtures.document())

        assertTrue(model.state.value.programEmpty)
        assertEquals("时间线为空", model.state.value.programMessage)
    }

    @Test
    fun `selecting an audio clip while the video tab is open switches the inspector to the audio tab`() = runTest {
        val (session, model) = editStage()
        assertEquals(EditUiState.TAB_VIDEO, model.state.value.inspectorTab)

        model.onEvent(EditEvent.SelectClip("mus"))

        assertEquals("mus", session.selectedClipId.value)
        assertEquals(EditUiState.TAB_AUDIO, model.state.value.inspectorTab)
        assertEquals(ClipKind.AUDIO, model.state.value.inspector?.kind)
        assertEquals("mus", model.state.value.timeline.selectedClipId)
    }

    @Test
    fun `the audio tab is kept for clips with sound and left for clips without`() = runTest {
        val (_, model) = editStage()
        model.onEvent(EditEvent.InspectorTab(EditUiState.TAB_AUDIO))

        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals("linked sound is edited on the audio tab", EditUiState.TAB_AUDIO, model.state.value.inspectorTab)
        assertEquals("aA", model.state.value.inspector?.audioClipId)

        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals(EditUiState.TAB_VIDEO, model.state.value.inspectorTab)
        assertFalse(model.state.value.inspector!!.hasAudio)

        model.onEvent(EditEvent.InspectorTab(2))
        model.onEvent(EditEvent.SelectClip("mus"))
        assertEquals("the speed tab applies to every clip", 2, model.state.value.inspectorTab)

        model.onEvent(EditEvent.SelectClip(null))
        assertTrue(model.state.value.nothingSelected)
    }

    @Test
    fun `split cuts the selected clip and its linked sound at the playhead and can be undone`() = runTest {
        val (session, model) = editStage()
        val original = session.document.value!!.timeline
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(30))
        assertEquals(30, model.state.value.playheadFrame)

        model.onEvent(EditEvent.Split)

        val timeline = session.document.value!!.timeline
        assertEquals(listOf("vA", "c-1", "aA", "c-2", "vB", "mus"), timeline.clips.map { it.id })
        assertEquals(30, timeline.clip("vA")!!.durationFrames)
        val right = timeline.clip("c-1")!!
        assertEquals(30, right.start)
        assertEquals(60, right.durationFrames)
        assertEquals(1_000L, right.sourceInMs)
        assertEquals("the right halves stay linked", right.linkId, timeline.clip("c-2")!!.linkId)
        assertEquals("the unselected song under the playhead is not cut", 120, timeline.clip("mus")!!.durationFrames)
        assertEquals("c-1", session.selectedClipId.value)
        assertEquals("分割片段", session.history.value.undoLabel)
        assertEquals("已在 00:00:01:00 分割 2 个片段", model.state.value.toast)

        session.undo()
        assertEquals(original, session.document.value!!.timeline)
    }

    @Test
    fun `Ctrl+T dissolves the cut nearest the playhead, or the selected clips' ends, in one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.Seek(120))
        model.onEvent(EditEvent.AddTransitions(TransitionScope.BOTH))
        val dissolve = session.clip("vB").transitionIn!!
        assertEquals(TransitionType.CROSS_DISSOLVE to 15, dissolve.type to dissolve.frames)
        assertEquals("添加转场", session.history.value.undoLabel)
        assertEquals("已在 00:00:03:00 添加 1 个交叉叠化", model.state.value.toast)
        assertNull("nothing selected afterwards", session.selectedClipId.value)
        model.onEvent(EditEvent.AddTransitions(TransitionScope.BOTH))
        assertEquals("这里的剪辑点已经有转场", model.state.value.toast)
        session.undo()
        assertNull(session.clip("vB").transitionIn)

        model.onEvent(EditEvent.SelectClip("mus"))
        model.onEvent(EditEvent.AddTransitions(TransitionScope.AUDIO))
        assertEquals("the song has nothing next to it", "所选片段的两端没有紧挨着的片段，无法添加转场", model.state.value.toast)
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.AddTransitions(TransitionScope.VIDEO))
        assertEquals("vA's out point is vB's cut", 15, session.clip("vB").transitionIn!!.frames)
        assertEquals("添加视频转场", session.history.value.undoLabel)
        assertEquals("vA", session.selectedClipId.value)
    }

    @Test
    fun `Shift+bracket trims the clips under the playhead to it, the ripple version closing the gap, each one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.TrimToPlayhead(TrimEdge.START, ripple = false))
        assertEquals(30, session.clip("vA").start)
        assertEquals("the song under it too", 30, session.clip("mus").start)
        assertEquals("修剪开头到播放头", session.history.value.undoLabel)
        assertEquals("已将 3 个片段的开头修剪到播放头", model.state.value.toast)
        session.undo()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.TrimToPlayhead(TrimEdge.START, ripple = true))
        assertEquals(listOf(0, 60), session.clip("vA").let { listOf(it.start, it.end) })
        assertEquals("the still moves up", 60, session.clip("vB").start)
        assertEquals("the song is not selected", 0, session.clip("mus").start)
        assertEquals("波纹修剪开头到播放头", session.history.value.undoLabel)
        model.onEvent(EditEvent.Seek(140))
        model.onEvent(EditEvent.TrimToPlayhead(TrimEdge.END, ripple = false))
        assertEquals("播放头不在任何片段上", model.state.value.toast)
    }

    @Test
    fun `a tap on an empty lane picks the gap and Delete closes it, one undo step`() = runTest {
        val (session, model) = editStage()
        // Make a gap on V1: the still vB moves from 90 to 120.
        model.onEvent(EditEvent.Move("vB", 120, Timeline.V1, true))
        model.onEvent(EditEvent.SelectGap(Timeline.V1, 100))
        assertNull(session.selectedClipId.value)
        assertEquals(com.example.prismstudio.p40b43a88.domain.usecase.TimelineGap(Timeline.V1, 90, 120), model.state.value.timeline.selectedGap)
        assertEquals("1.0 秒", model.state.value.gapText)
        model.onEvent(EditEvent.DeleteClip)
        assertEquals("the still is back at 90", 90, session.clip("vB").start)
        assertEquals("删除空隙", session.history.value.undoLabel)
        assertNull(model.state.value.timeline.selectedGap)
        session.undo()
        assertEquals(120, session.clip("vB").start)

        model.onEvent(EditEvent.SelectGap(Timeline.V1, 100))
        model.onEvent(EditEvent.SelectClip("vA"))
        assertNull("selecting a clip drops the gap", model.state.value.timeline.selectedGap)
        model.onEvent(EditEvent.SelectGap(Timeline.V1, 30))
        assertNull("on a clip there is no gap", model.state.value.timeline.selectedGap)
        assertNull(session.selectedClipId.value)
        model.onEvent(EditEvent.DeleteAllGaps)
        assertEquals(90, session.clip("vB").start)
        assertEquals("删除所有空隙", session.history.value.undoLabel)
    }

    @Test
    fun `Ctrl+Shift+period swaps the selected clip with the next one, its sound along, one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.SwapClip(right = true))
        assertEquals("the still first", 0, session.clip("vB").start)
        assertEquals(60, session.clip("vA").start)
        assertEquals("its linked sound along", 60, session.clip("aA").start)
        assertEquals("与右侧片段交换", session.history.value.undoLabel)
        assertEquals("vA", session.selectedClipId.value)
        session.undo()
        assertEquals(0, session.clip("vA").start)
        model.onEvent(EditEvent.SwapClip(right = false))
        assertEquals("左侧没有可以交换的片段", model.state.value.toast)
    }

    @Test
    fun `the rename sheet names a clip or a track, checking the name as it is typed`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.OpenRenameClip("vA"))
        var sheet = model.state.value.rename!!
        assertEquals("重命名片段" to "vA", sheet.title to sheet.original)
        assertEquals(false, sheet.canConfirm)
        model.onEvent(EditEvent.RenameText("开场"))
        assertEquals(true, model.state.value.rename!!.canConfirm)
        model.onEvent(EditEvent.ConfirmRename)
        assertEquals("开场", session.clip("vA").name)
        assertEquals("重命名片段", session.history.value.undoLabel)
        assertNull(model.state.value.rename)

        model.onEvent(EditEvent.OpenRenameTrack(Timeline.A2))
        assertEquals("重命名轨道 A2", model.state.value.rename!!.title)
        model.onEvent(EditEvent.RenameText("A1"))
        sheet = model.state.value.rename!!
        assertEquals("已有名为“A1”的轨道", sheet.problem)
        assertEquals(false, sheet.canConfirm)
        model.onEvent(EditEvent.RenameText("音乐"))
        model.onEvent(EditEvent.ConfirmRename)
        assertEquals("音乐", session.document.value!!.timeline.track(Timeline.A2)!!.name)
        assertEquals("重命名轨道", session.history.value.undoLabel)
        session.undo()
        assertEquals("A2", session.document.value!!.timeline.track(Timeline.A2)!!.name)
        model.onEvent(EditEvent.OpenRenameTrack(Timeline.A2))
        model.onEvent(EditEvent.CloseRename)
        assertNull(model.state.value.rename)
    }

    @Test
    fun `Ctrl+D sets a clip's length from a typed timecode, rippling the track`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.OpenClipDuration())
        var sheet = model.state.value.clipDuration!!
        assertEquals("00:00:02:00", sheet.original)
        assertEquals(false, sheet.canConfirm)
        model.onEvent(EditEvent.ClipDurationText("abc"))
        assertEquals("输入时间码（如 00:00:03:00 或 3:00）或秒数（如 2.5）", model.state.value.clipDuration!!.problem)
        model.onEvent(EditEvent.ClipDurationText("3.5"))
        sheet = model.state.value.clipDuration!!
        assertEquals(105, sheet.frames)
        assertEquals("00:00:03:15 · 3.5 秒，输入时间码（时:分:秒:帧）或秒数", sheet.reading)
        model.onEvent(EditEvent.ConfirmClipDuration)
        assertEquals("the still has no length limit", 105, session.clip("vB").durationFrames)
        assertEquals("更改片段时长", session.history.value.undoLabel)
        assertNull(model.state.value.clipDuration)
        session.undo()
        assertEquals(60, session.clip("vB").durationFrames)
    }

    @Test
    fun `a typed timecode jumps the playhead, an offset moves the selected clips`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.OpenTimecodeEntry())
        assertEquals("00:00:01:00", model.state.value.timecodeEntry!!.current)
        model.onEvent(EditEvent.TimecodeEntryText("300"))
        assertEquals("播放头跳到 00:00:03:00", model.state.value.timecodeEntry!!.reading)
        model.onEvent(EditEvent.ConfirmTimecodeEntry)
        assertEquals(90, model.state.value.playheadFrame)
        assertNull(model.state.value.timecodeEntry)

        model.onEvent(EditEvent.OpenTimecodeEntry("-"))
        assertEquals("only the sign: nothing to do yet", false, model.state.value.timecodeEntry!!.canConfirm)
        model.onEvent(EditEvent.TimecodeEntryText("-1:00"))
        assertEquals("播放头后退 00:00:01:00，到 00:00:02:00", model.state.value.timecodeEntry!!.reading)
        model.onEvent(EditEvent.ConfirmTimecodeEntry)
        assertEquals(60, model.state.value.playheadFrame)

        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.OpenTimecodeEntry("+"))
        model.onEvent(EditEvent.TimecodeEntryText("+15"))
        val sheet = model.state.value.timecodeEntry!!
        assertEquals("所选 1 个片段右移 00:00:00:15" to "移动", sheet.reading to sheet.confirmLabel)
        model.onEvent(EditEvent.ConfirmTimecodeEntry)
        assertEquals(105, session.clip("vB").start)
        assertEquals("the playhead stays", 60, model.state.value.playheadFrame)
        model.onEvent(EditEvent.OpenTimecodeEntry())
        model.onEvent(EditEvent.TimecodeEntryText("1::2"))
        assertEquals("数字从右往左按帧、秒、分、时填，如 15、1:00、+15、-1:00:00", model.state.value.timecodeEntry!!.problem)
        model.onEvent(EditEvent.CloseTimecodeEntry)
        assertNull(model.state.value.timecodeEntry)
    }

    @Test
    fun `the timeline menu exports the open timeline as an EDL into Download`() = runTest {
        val saved = mutableListOf<Triple<String, String, String>>()
        val downloads = object : com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository {
            override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String {
                saved += Triple(fileName, mimeType, bytes.decodeToString())
                return "Download/Reality Video Editor/$fileName"
            }
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, stills = StillFrameRepository.None, downloads = downloads, clock = clock)
        advanceUntilIdle()
        model.onEvent(EditEvent.ExportEdl)
        advanceUntilIdle()
        val (name, mime, text) = saved.single()
        assertEquals("${session.document.value!!.timelineName}.edl" to "application/octet-stream", name to mime)
        assertTrue(text.startsWith("TITLE: "))
        assertEquals("vA, vB on V1; aA on A1; the song on A2", 4, text.lines().count { it.matches(Regex("^\\d{3} .*")) && " C " in it })
        assertEquals("已导出 EDL（4 个事件）到 Download/Reality Video Editor/$name", model.state.value.toast)
    }

    @Test
    fun `the timeline menu exports the open timeline as FCPXML into Download`() = runTest {
        val saved = mutableListOf<Triple<String, String, String>>()
        val downloads = object : com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository {
            override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String {
                saved += Triple(fileName, mimeType, bytes.decodeToString())
                return "Download/Reality Video Editor/$fileName"
            }
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, stills = StillFrameRepository.None, downloads = downloads, clock = clock)
        advanceUntilIdle()
        model.onEvent(EditEvent.ExportFcpxml)
        advanceUntilIdle()
        val (name, mime, text) = saved.single()
        assertEquals("${session.document.value!!.timelineName}.fcpxml" to "application/octet-stream", name to mime)
        assertTrue(text.contains("<fcpxml version=\"1.10\">"))
        val clips = session.document.value!!.timeline.clips.size
        assertEquals("已导出 FCPXML（$clips 个片段）到 Download/Reality Video Editor/$name", model.state.value.toast)
    }

    @Test
    fun `a generator from the library goes on top at the playhead with its image, one undo step`() = runTest {
        val made = mutableListOf<String>()
        val generators = object : com.example.prismstudio.p40b43a88.data.repository.GeneratorRepository {
            override suspend fun create(generator: Generator, width: Int, height: Int, id: String): MediaAsset {
                made += id
                return MediaAsset(id, "file:///g/$id.png", generator.assetName(width, height), MediaKind.IMAGE, 5_000, width, height, 0f, "PNG", "无音频", false, 1, "generators/")
            }
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, generators = generators, clock = clock)
        advanceUntilIdle()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.GENERATORS.ordinal))
        assertEquals(Generator.entries.map { it.label }, model.state.value.libraryItems.map { it.title })
        assertEquals("添加到播放头处", model.state.value.libraryActionLabel)
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.SelectLibraryItem("generator:BARS"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        advanceUntilIdle()
        val doc = session.document.value!!
        val clip = doc.timeline.clips.single { it.assetId == "g-bars-1920x1080" }
        assertEquals(Timeline.V2 to 30, clip.trackId to clip.start)
        assertEquals(listOf("g-bars-1920x1080"), made)
        assertEquals("添加生成器", session.history.value.undoLabel)
        assertEquals(true, model.state.value.toast!!.startsWith("已添加SMPTE 彩条"))
        session.undo()
        assertEquals("undo takes the image out of the pool too", null, session.document.value!!.asset("g-bars-1920x1080"))
    }

    @Test
    fun `a picked EDL becomes a new timeline, opened, one undo step`() = runTest {
        val edl = "TITLE: 导入测试\r\nFCM: NON-DROP FRAME\r\n\r\n001  AX       V     C        00:00:01:00 00:00:02:00 00:00:00:00 00:00:01:00\r\n* FROM CLIP NAME: 海边\r\n* SOURCE FILE: ${footage.uri.substringAfterLast('/')}\r\n"
        val documents = object : DocumentRepository {
            override suspend fun read(uri: String, maxBytes: Int): PickedDocument =
                if (uri == "content://bad") throw DocumentFailure("无法读取文件") else PickedDocument("cut.edl", ("\uFEFF" + edl).toByteArray())
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, documents = documents)
        advanceUntilIdle()
        val before = session.document.value!!.timelineName
        model.onEvent(EditEvent.ImportEdl("content://ok"))
        advanceUntilIdle()
        val doc = session.document.value!!
        assertEquals("导入测试", doc.timelineName)
        val clip = doc.timeline.clips.single()
        assertEquals(listOf(footage.id, "海边"), listOf(clip.assetId, clip.name))
        assertEquals(0 to 30, clip.start to clip.durationFrames)
        assertEquals(1000L, clip.sourceInMs)
        assertEquals("导入 EDL", session.history.value.undoLabel)
        assertEquals("已从 EDL 导入 1 个片段，新建并打开“导入测试”", model.state.value.toast)
        session.undo()
        assertEquals(before, session.document.value!!.timelineName)
        model.onEvent(EditEvent.ImportEdl("content://bad"))
        advanceUntilIdle()
        assertEquals("无法读取文件", model.state.value.toast)
    }

    @Test
    fun `a picked FCPXML becomes a new timeline, opened, one undo step`() = runTest {
        val file = footage.uri.substringAfterLast('/')
        val xml = """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE fcpxml><fcpxml version="1.10"><resources>
            <format id="r1" frameDuration="1/30s" width="1920" height="1080"/>
            <asset id="r2" name="$file" start="0s" duration="10s" hasVideo="1" format="r1"><media-rep kind="original-media" src="file:///v/$file"/></asset>
            </resources><library><event name="E"><project name="FCP 粗剪"><sequence format="r1" duration="1s" tcStart="0s"><spine>
            <asset-clip ref="r2" offset="0s" name="海边" start="1s" duration="1s"/></spine></sequence></project></event></library></fcpxml>"""
        val documents = object : DocumentRepository {
            override suspend fun read(uri: String, maxBytes: Int): PickedDocument =
                if (uri == "content://bad") PickedDocument("bad.fcpxml", "not xml".toByteArray()) else PickedDocument("cut.fcpxml", xml.toByteArray())
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, documents = documents)
        advanceUntilIdle()
        val before = session.document.value!!.timelineName
        model.onEvent(EditEvent.ImportFcpxml("content://ok"))
        advanceUntilIdle()
        val doc = session.document.value!!
        assertEquals("FCP 粗剪", doc.timelineName)
        val clip = doc.timeline.clips.single()
        assertEquals(listOf<Any>(footage.id, "海边", 0, 30, 1000L), listOf(clip.assetId, clip.name, clip.start, clip.durationFrames, clip.sourceInMs))
        assertEquals("导入 FCPXML", session.history.value.undoLabel)
        assertEquals("已从 FCPXML 导入 1 个片段，新建并打开“FCP 粗剪”", model.state.value.toast)
        session.undo()
        assertEquals(before, session.document.value!!.timelineName)
        model.onEvent(EditEvent.ImportFcpxml("content://bad"))
        advanceUntilIdle()
        assertEquals("无法读取 FCPXML：文件不是有效的 XML", model.state.value.toast)
    }

    @Test
    fun `split with nothing under the playhead is refused`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.Seek(150))

        model.onEvent(EditEvent.Split)

        assertEquals("播放头下没有可分割的片段", model.state.value.toast)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `ripple delete needs a selection, then removes the clip with its sound and closes the gap`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.RippleDelete)
        assertEquals("请先在时间线上选择片段，或标记入点和出点", model.state.value.toast)

        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.RippleDelete)

        val timeline = session.document.value!!.timeline
        assertEquals(listOf("vB", "mus"), timeline.clips.map { it.id })
        assertEquals(0, timeline.clip("vB")!!.start)
        assertEquals("other tracks keep their timing", 0, timeline.clip("mus")!!.start)
        assertNull(session.selectedClipId.value)
        assertNull(model.state.value.inspector)
        assertEquals("波纹删除", session.history.value.undoLabel)
        assertEquals("已波纹删除“vA”，后续片段前移 00:00:03:00", model.state.value.toast)
    }

    @Test
    fun `markers are numbered, dropped at the playhead and never doubled on one frame`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.Seek(45))

        model.onEvent(EditEvent.AddMarker)
        assertEquals(listOf(Marker("m-1", 45, "标记 1")), session.document.value!!.timeline.markers)
        assertEquals("已在 00:00:01:15 添加标记 1", model.state.value.toast)
        assertEquals("添加标记", session.history.value.undoLabel)

        model.onEvent(EditEvent.AddMarker)
        assertEquals("该位置已有标记", model.state.value.toast)
        assertEquals(1, session.document.value!!.timeline.markers.size)

        model.onEvent(EditEvent.Seek(15))
        model.onEvent(EditEvent.AddMarker)
        assertEquals(listOf(15 to "标记 2", 45 to "标记 1"), session.document.value!!.timeline.markers.map { it.frame to it.label })
        assertEquals(2, model.state.value.timeline.markers.size)
    }

    @Test
    fun `inspector sliders edit the selected clip within limits and one drag is one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))

        model.onEvent(EditEvent.Scale(150f))
        model.onEvent(EditEvent.Scale(180.4f))
        model.onEvent(EditEvent.Scale(1_000f))

        assertEquals(400, session.clip("vA").transform.scalePercent)
        assertEquals(400, model.state.value.inspector?.scale)
        assertEquals("缩放", session.history.value.undoLabel)
        session.undo()
        assertEquals(100, session.clip("vA").transform.scalePercent)
        assertFalse("the whole drag was a single step", session.history.value.canUndo)

        model.onEvent(EditEvent.Opacity(-5f))
        model.onEvent(EditEvent.Rotation(200f))
        model.onEvent(EditEvent.PositionX(12.6f))
        val transform = session.clip("vA").transform
        assertEquals(0, transform.opacityPercent)
        assertEquals(180, transform.rotationDegrees)
        assertEquals(13, transform.positionX)
    }

    @Test
    fun `crop sliders cut the selected picture's edges, one drag per edge is one step, and reset clears them`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertFalse(model.state.value.inspector!!.hasCrop)

        model.onEvent(EditEvent.Crop(CropControl.LEFT, 200f))
        model.onEvent(EditEvent.Crop(CropControl.LEFT, 480.4f))
        model.onEvent(EditEvent.Crop(CropControl.SOFTNESS, 250f))
        val transform = session.clip("vA").transform
        assertEquals(480 to 100, transform.cropLeft to transform.cropSoftness)
        assertEquals(listOf(480, 0, 0, 0, 100), model.state.value.inspector!!.crop)
        assertTrue(model.state.value.inspector!!.hasCrop)
        assertEquals("裁切柔化", session.history.value.undoLabel)
        session.undo()
        assertEquals("the left edge's drag is its own step", 480 to 0, session.clip("vA").transform.let { it.cropLeft to it.cropSoftness })

        model.onEvent(EditEvent.ResetCrop)
        assertEquals(ClipTransform(), session.clip("vA").transform)
        assertEquals("重置裁切", session.history.value.undoLabel)
        assertFalse(model.state.value.inspector!!.hasCrop)
    }

    @Test
    fun `dynamic zoom switches on with its defaults, takes its settings, and comes back with them`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertNull(model.state.value.inspector!!.dynamicZoom)
        model.onEvent(EditEvent.DynamicZoomAmount(30f))
        assertEquals("先开启动态缩放", model.state.value.toast)

        model.onEvent(EditEvent.DynamicZoomOn(true))
        assertEquals(DynamicZoom(), session.clip("vA").dynamicZoom)
        assertEquals("开启动态缩放", session.history.value.undoLabel)
        model.onEvent(EditEvent.DynamicZoomDirection(ZoomDirection.OUT))
        model.onEvent(EditEvent.DynamicZoomAmount(32.6f))
        model.onEvent(EditEvent.DynamicZoomEase(ZoomEase.EASE_IN_OUT))
        val set = DynamicZoom(ZoomDirection.OUT, 33, ZoomEase.EASE_IN_OUT)
        assertEquals(set, session.clip("vA").dynamicZoom)
        assertEquals(set, model.state.value.inspector!!.dynamicZoom)

        model.onEvent(EditEvent.DynamicZoomOn(false))
        assertNull(session.clip("vA").dynamicZoom)
        model.onEvent(EditEvent.DynamicZoomOn(true))
        assertEquals("switched back on with its settings", set, session.clip("vA").dynamicZoom)

        // A pan from a centred focus heads right; the focus rows then place it.
        model.onEvent(EditEvent.DynamicZoomDirection(ZoomDirection.PAN))
        assertEquals(ZoomFocusX.RIGHT, session.clip("vA").dynamicZoom!!.focusX)
        model.onEvent(EditEvent.DynamicZoomFocusY(ZoomFocusY.BOTTOM))
        model.onEvent(EditEvent.DynamicZoomFocusX(ZoomFocusX.LEFT))
        assertEquals(set.copy(direction = ZoomDirection.PAN, focusX = ZoomFocusX.LEFT, focusY = ZoomFocusY.BOTTOM), session.clip("vA").dynamicZoom)
        assertEquals("动态缩放焦点", session.history.value.undoLabel)
    }

    @Test
    fun `detect scene cuts cuts a clip of several shots and its sound where they change, one undo step`() = runTest {
        // vA (90 frames of v1 from its start) holds three shots of 30 frames: three unrelated textures.
        val shot = { seed: Int ->
            val r = kotlin.random.Random(seed)
            val coarse = FloatArray(10 * 10) { r.nextFloat() }
            ByteArray(160 * 90) { i ->
                val x = (i % 160) / 18f; val y = (i / 160) / 10f
                (40 + 180 * coarse[(y.toInt().coerceAtMost(9)) * 10 + x.toInt().coerceAtMost(9)]).toInt().toByte()
            }
        }
        val frames = LumaFrames(160, 90, 30.0, 0, List(90) { i -> shot(1 + i / 30) })
        val source = FakeLumaFrameRepository(frames)
        val (session, model) = editStage(lumaFrames = source)
        assertTrue("vA is footage", "vA" in model.state.value.footageClipIds)
        assertFalse("vB is a still", "vB" in model.state.value.footageClipIds)
        model.onEvent(EditEvent.DetectSceneCuts("vA"))
        advanceUntilIdle()
        val timeline = session.document.value!!.timeline
        assertEquals(listOf(0, 30, 60), timeline.clips.filter { it.trackId == Timeline.V1 && it.start < 90 }.map { it.start }.sorted())
        assertEquals("the linked sound is cut too", 3, timeline.clips.count { it.trackId == Timeline.A1 })
        assertEquals("检测场景剪切", session.history.value.undoLabel)
        assertEquals("检测到 2 处场景剪切，已切成 3 段", model.state.value.toast)
        assertFalse(model.state.value.sceneCutting)
        session.undo()
        assertEquals(90, session.document.value!!.timeline.clip("vA")!!.durationFrames)
        model.onEvent(EditEvent.DetectSceneCuts("vB"))
        assertEquals("只有视频片段可以检测场景剪切", model.state.value.toast)
    }

    @Test
    fun `subtitle tracks per language - one added, one shown at a time, a language changed, a duplicate refused`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.AddTrack(TrackKind.SUBTITLE))
        model.onEvent(EditEvent.AddTrack(TrackKind.SUBTITLE))
        val subs = { session.document.value!!.timeline.tracks.filter { it.kind == TrackKind.SUBTITLE } }
        assertEquals(listOf("st2" to com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.ZH_HANT, "st1" to com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.ZH_HANS),
            subs().map { it.id to it.language })
        assertEquals("the newest shows", listOf(true, false), subs().map { it.enabled })
        model.onEvent(EditEvent.TrackEnabled("st1", true))
        assertEquals("showing one hides the other", listOf(false, true), subs().map { it.enabled })
        model.onEvent(EditEvent.SubtitleLanguage("st2", com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.EN))
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.EN, subs().first().language)
        assertEquals("字幕语言：English", session.history.value.undoLabel)
        model.onEvent(EditEvent.SubtitleLanguage("st1", com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.EN))
        assertEquals("ST2 已经是English字幕", model.state.value.toast)
    }

    @Test
    fun `multicam - two cameras made one clip, cut at the playhead, a segment switched whole, picture alone`() = runTest {
        val cams = listOf(
            UiFixtures.clip("c1", start = 0, frames = 120, assetId = "v1", linkId = "K1"),
            UiFixtures.clip("c1a", start = 0, frames = 120, trackId = Timeline.A1, assetId = "v1", linkId = "K1"),
            UiFixtures.clip("c2", start = 0, frames = 120, trackId = Timeline.V2, assetId = "v1", linkId = "K2"),
            UiFixtures.clip("c2a", start = 0, frames = 120, trackId = Timeline.A2, assetId = "v1", linkId = "K2"),
        )
        val (session, model) = editStage(UiFixtures.document(media = listOf(footage), clips = cams))
        model.onEvent(EditEvent.SelectClip("c1"))
        model.onEvent(EditEvent.ToggleSelected("c2"))
        assertTrue(model.state.value.selection!!.canMulticam)
        assertEquals("2 个机位：按轨道从下往上依次为机位 1、2…，保持现在的对齐", model.state.value.selection!!.multicamNote)
        model.onEvent(EditEvent.CreateMulticam)
        assertEquals("新建多机位片段", session.history.value.undoLabel)
        val made = session.document.value!!
        val picture = made.timeline.clips.single { it.kind == ClipKind.VIDEO }
        assertTrue(picture.id in model.state.value.timeline.multicamClipIds)
        assertEquals("机位 1 · c1", model.state.value.timeline.compoundLabels[picture.id])
        val panel = model.state.value.inspector!!.multicam!!
        assertEquals("多机位 · 多机位 1", panel.title)
        assertEquals("2 个机位 · 画面：机位 1 · 声音：机位 1", panel.summary)
        assertEquals(listOf(true, false), panel.angles.map { it.picture })
        // A tap at 00:00:01:15 cuts there to angle 2, picture and sound.
        model.onEvent(EditEvent.Seek(45))
        model.onEvent(EditEvent.SwitchAngle(1))
        assertEquals("切换机位", session.history.value.undoLabel)
        assertEquals("在 00:00:01:15 把画面和声音切到机位 2（c2）", model.state.value.toast)
        assertEquals(listOf(0 to 0, 45 to 1), session.document.value!!.timeline.clips.filter { it.kind == ClipKind.VIDEO }.sortedBy { it.start }.map { it.start to it.angle })
        assertEquals(listOf(false, true), model.state.value.inspector!!.multicam!!.angles.map { it.picture })
        // 整段切换: the segment under the playhead back to angle 1, no cut.
        model.onEvent(EditEvent.MulticamWhole(true))
        model.onEvent(EditEvent.Seek(90))
        assertEquals("点按机位：播放头所在的整段切到该机位（画面+声音）", model.state.value.inspector!!.multicam!!.note)
        model.onEvent(EditEvent.SwitchAngle(0))
        assertEquals(2, session.document.value!!.timeline.clips.count { it.kind == ClipKind.VIDEO })
        assertEquals(setOf(0), session.document.value!!.timeline.clips.map { it.angle }.toSet())
        // Picture only, cutting: the sound plays on whole.
        model.onEvent(EditEvent.MulticamWhole(false))
        model.onEvent(EditEvent.MulticamMode(1))
        model.onEvent(EditEvent.SwitchAngle(1))
        val after = session.document.value!!.timeline.clips
        assertEquals(3 to 2, after.count { it.kind == ClipKind.VIDEO } to after.count { it.kind == ClipKind.AUDIO })
        assertEquals("在 00:00:03:00 把画面切到机位 2（c2）", model.state.value.toast)
        session.undo()
        assertEquals("one tap, one undo step", 2, session.document.value!!.timeline.clips.count { it.kind == ClipKind.VIDEO })
    }

    @Test
    fun `compound clips - made from the selection, opened at the playhead, left, decomposed, each one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.ToggleSelected("vB"))
        val ui = model.state.value.selection!!
        assertTrue(ui.canCompound)
        assertEquals("把所选的 2 组片段收进一个复合片段，放在原来的位置", ui.compoundNote)
        model.onEvent(EditEvent.CreateCompound)
        val made = session.document.value!!
        assertEquals("新建复合片段", session.history.value.undoLabel)
        assertEquals("已把 3 个片段收进复合片段“复合片段 1”", model.state.value.toast)
        val picture = made.timeline.clips.single { it.isCompound && it.trackId == Timeline.V1 }
        assertEquals(0 to 150, picture.start to picture.durationFrames)
        assertEquals(setOf(picture.id, "mus"), made.timeline.clips.filterNot { it.isCompound && it.trackId == Timeline.A1 }.map { it.id }.toSet())
        // The inspector shows the compound panel; the timeline draws its contents.
        val inspector = model.state.value.inspector!!
        assertEquals(CompoundUi("复合片段 1", "1 条视频轨 · 1 条音频轨 · 3 个片段 · 00:00:05:00"), inspector.compound)
        assertEquals(2, model.state.value.timeline.compoundBars.getValue(picture.id).size)
        // Opened at the playhead: the frame it showed, the timeline menu still listing the project's timelines only.
        model.onEvent(EditEvent.Seek(45))
        model.onEvent(EditEvent.OpenCompound(picture.id))
        assertEquals("复合片段 1", model.state.value.timelineName)
        assertEquals(listOf(FIRST_TIMELINE_NAME), model.state.value.compoundTrail)
        assertEquals(listOf(FIRST_TIMELINE_NAME), model.state.value.timelineEntries.map { it.name })
        assertEquals(45, model.state.value.playheadFrame)
        assertEquals(setOf("vA", "aA", "vB"), session.document.value!!.timeline.clips.map { it.id }.toSet())
        // Moved on 15 frames inside, the playhead comes back 15 frames on outside.
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.LeaveCompound)
        assertEquals(FIRST_TIMELINE_NAME, model.state.value.timelineName)
        assertEquals(emptyList<String>(), model.state.value.compoundTrail)
        assertEquals(60, model.state.value.playheadFrame)
        // Decomposed: the clips back under new ids, picture and sound still linked.
        model.onEvent(EditEvent.DecomposeCompound(picture.id))
        assertEquals("分解复合片段", session.history.value.undoLabel)
        val back = session.document.value!!.timeline
        assertTrue(back.clips.none { it.isCompound })
        assertEquals(listOf(0, 0, 0, 90), back.clips.map { it.start }.sorted())
        session.undo()
        assertTrue(session.document.value!!.timeline.clip(picture.id)!!.isCompound)
    }

    @Test
    fun `another timeline of the project is edited in from the library as a nested clip`() = runTest {
        val base = project()
        val other = Timeline(Timeline.defaultTracks(), listOf(UiFixtures.clip("x1", start = 0, frames = 60, assetId = "v1")), emptyList())
        val (session, model) = editStage(base.copy(timelines = listOf(StoredTimeline("tl-2", "时间线 2", base.format, other, MixerState.forTracks(other.tracks)))))
        val tile = model.state.value.libraryItems.single { it.id == "timeline:tl-2" }
        assertEquals("时间线 · 00:00:02:00 · 1 个片段", tile.subtitle)
        assertEquals("时间线", tile.miniature!!.badge)
        model.onEvent(EditEvent.SelectLibraryItem("timeline:tl-2"))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.EditFromSource(SourceEdit.INSERT))
        val t = session.document.value!!.timeline
        val nested = t.clips.single { it.compoundId == "tl-2" }
        assertEquals(30 to 60, nested.start to nested.durationFrames)
        assertEquals("the clip after the playhead moves on", 150, t.clip("vB")!!.start)
        assertEquals("插入时间线", session.history.value.undoLabel)
        assertEquals("已把嵌套时间线“时间线 2”插入到 00:00:01:00", model.state.value.toast)
        assertEquals("嵌套时间线 · 时间线 2", model.state.value.inspector!!.compound!!.title)
        assertEquals("嵌套时间线", model.state.value.timeline.compoundLabels[nested.id])
        model.onEvent(EditEvent.EditFromSource(SourceEdit.REPLACE))
        assertEquals("时间线可以插入、覆盖或追加到时间线上", model.state.value.toast)
    }

    @Test
    fun `a picture's sound takes a channel mapping from the inspector, kept in the project file`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(AudioChannels.STEREO, model.state.value.inspector!!.channels)
        model.onEvent(EditEvent.Channels(AudioChannels.LEFT))
        assertEquals("the linked sound takes it", AudioChannels.LEFT, session.document.value!!.timeline.clip("aA")!!.channels)
        assertEquals(AudioChannels.LEFT, model.state.value.inspector!!.channels)
        assertEquals(AudioChannels.LEFT.note, model.state.value.inspector!!.channelsNote)
        assertEquals("声道：仅左", session.history.value.undoLabel)
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(session.document.value!!))
        assertEquals(AudioChannels.LEFT, reread.timeline.clip("aA")!!.channels)
        assertEquals(AudioChannels.STEREO, reread.timeline.clip("mus")!!.channels)
        // A still has no sound to map.
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.Channels(AudioChannels.MONO))
        assertEquals("所选片段没有音频", model.state.value.toast)
        session.undo()
        assertEquals(AudioChannels.STEREO, session.document.value!!.timeline.clip("aA")!!.channels)
    }

    @Test
    fun `a picture's sound takes a pitch from the inspector in tenths of a semitone, undone in one step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(0f, model.state.value.inspector!!.pitchSemitones, 0f)
        model.onEvent(EditEvent.Pitch(3.04f))
        model.onEvent(EditEvent.Pitch(7f))
        assertEquals("the linked sound takes it", 700, session.document.value!!.timeline.clip("aA")!!.pitchCents)
        assertEquals(7f, model.state.value.inspector!!.pitchSemitones, 0f)
        assertEquals("升高7 个半音（纯五度） · 频率 ×1.498 · 长度和速度不变", model.state.value.inspector!!.pitchNote)
        assertEquals("音调", session.history.value.undoLabel)
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.Pitch(2f))
        assertEquals("所选片段没有音频", model.state.value.toast)
        session.undo()
        assertEquals("the drag is one step", 0, session.document.value!!.timeline.clip("aA")!!.pitchCents)
    }

    @Test
    fun `the edit index lists the events, filters them, and takes the playhead to the one tapped`() = runTest {
        val (session, model) = editStage()
        val rows = model.state.value.editIndex
        assertEquals(listOf("vA", "aA", "mus", "vB"), rows.map { it.clipId })
        assertEquals(listOf("001", "002", "003", "004"), rows.map { it.number })
        assertEquals("00:00:03:00 – 00:00:05:00", rows.last().times)
        assertEquals("4 个片段 · 画面 2 · 声音 2", model.state.value.editIndexSummary)
        model.onEvent(EditEvent.IndexFilter(EditIndexFilter.SOUND))
        assertEquals(listOf("aA", "mus"), model.state.value.editIndex.map { it.clipId })
        model.onEvent(EditEvent.IndexFilter(EditIndexFilter.ALL))
        model.onEvent(EditEvent.PickIndexRow("vB"))
        assertEquals("vB", session.selectedClipId.value)
        assertEquals(90, model.state.value.playheadFrame)
        assertEquals(listOf(false, false, false, true), model.state.value.editIndex.map { it.picked })
        model.onEvent(EditEvent.IndexView(EditUiState.INDEX_MARKERS))
        assertEquals(EditUiState.INDEX_MARKERS, model.state.value.indexView)
    }

    @Test
    fun `the selected clips line up with the one tapped last by their sound, one undo step`() = runTest {
        // The camera's sound (vA's file) and the song share a take — syllable-like events every ~0.2 s, each fading at its
        // own pace — the song's file starting 1.5 s into it.
        val r = kotlin.random.Random(21)
        val events = List(200) { Triple(r.nextDouble() * 40_000, 0.2 + r.nextDouble(), 40 + r.nextDouble() * 260) }
        val power = { t: Double -> 1e-6 + events.sumOf { (at, loud, decay) -> if (t >= at) loud * kotlin.math.exp(-(t - at) / decay) else 0.0 } }
        fun env(offset: Double, seed: Int): SoundEnvelope {
            val n = kotlin.random.Random(seed)
            return SoundEnvelope(5, FloatArray(4_000) { i -> (10 * kotlin.math.log10(power(offset + i * 5 + 2.5) * (0.8 + 0.4 * n.nextDouble()))).toFloat() })
        }
        val levels = FakeSoundEnvelopeRepository(mapOf(footage.uri to env(0.0, 1), song.uri to env(1_500.0, 2)))
        val (session, model) = editStage(envelopes = levels)
        // The clip tapped last is the reference.
        model.onEvent(EditEvent.SelectClip("mus"))
        model.onEvent(EditEvent.ToggleSelected("vA"))
        val ui = model.state.value.selection!!
        assertTrue(ui.canAlign)
        assertEquals("以最后点选的「${session.document.value!!.timeline.clip("vA")!!.name}」为参照，按声音把其余片段移到同步的位置", ui.alignNote)
        model.onEvent(EditEvent.AlignByWaveform)
        advanceUntilIdle()
        val mus = session.document.value!!.timeline.clip("mus")!!
        // vA at 0 from its file's start: the song is in step when (start − in point) = 0 + 1500 ms.
        assertEquals(model.state.value.toast, 1_500.0, mus.start * 1000.0 / 30 - mus.sourceInMs, 3.0)
        assertEquals("按波形对齐", session.history.value.undoLabel)
        assertTrue(model.state.value.toast!!, model.state.value.toast!!.startsWith("已按波形对齐：「${mus.name}」后移 1."))
        assertFalse(model.state.value.selection!!.aligning)
        session.undo()
        assertEquals(0, session.document.value!!.timeline.clip("mus")!!.start)
    }

    @Test
    fun `the inspector stabilizes footage, shows its analysis and keeps the settings while off`() = runTest {
        val analyses = FakeStabilizationRepository()
        val (session, model) = editStage(stabilization = analyses)
        model.onEvent(EditEvent.SelectClip("vA"))
        val off = model.state.value.inspector!!.stabilization!!
        assertFalse(off.on)
        assertNull(off.unavailable)
        model.onEvent(EditEvent.StabilizeSmooth(70f))
        assertEquals("先开启稳定", model.state.value.toast)

        model.onEvent(EditEvent.StabilizeOn(true))
        assertEquals(ClipStabilization(), session.clip("vA").stabilization)
        assertEquals("开启稳定", session.history.value.undoLabel)
        assertEquals("正在分析摄像机运动 0%", model.state.value.inspector!!.stabilization!!.status)
        assertTrue(model.state.value.inspector!!.stabilization!!.analysing)
        // One drag of a slider is one undo step.
        model.onEvent(EditEvent.StabilizeSmooth(60f))
        model.onEvent(EditEvent.StabilizeSmooth(72.4f))
        model.onEvent(EditEvent.StabilizeStrength(80f))
        model.onEvent(EditEvent.StabilizeZoom(false))
        val set = ClipStabilization(smooth = 72, strength = 80, zoom = false)
        assertEquals(set, session.clip("vA").stabilization)
        assertEquals("稳定：不放大", session.history.value.undoLabel)
        // Zoom, strength, then the whole smooth drag as one step.
        session.undo(); session.undo(); session.undo()
        assertEquals("the smooth drag was one step", ClipStabilization(), session.clip("vA").stabilization)
        session.redo(); session.redo(); session.redo()

        // The file's path arrives: the status says what it found, and the diagram has both paths.
        val uri = footage.uri
        analyses.set(uri, CameraAnalysis.Analysing(0.5f))
        assertEquals("正在分析摄像机运动 50%", model.state.value.inspector!!.stabilization!!.status)
        val path = CameraPath(30.0, FloatArray(600) { if (it % 2 == 0) 0.004f else -0.004f }, FloatArray(600))
        analyses.set(uri, CameraAnalysis.Ready(path))
        val ready = model.state.value.inspector!!.stabilization!!
        assertTrue(ready.status, ready.status.startsWith("画面最多移动 ") && ready.status.endsWith("，边缘会露出"))
        assertEquals("不放大", ready.zoomLabel)
        assertEquals(91, ready.cameraX.size)
        assertFalse(ready.analysing)
        analyses.set(uri, CameraAnalysis.Failed("无法读取这个视频的画面"))
        assertTrue(model.state.value.inspector!!.stabilization!!.failed)
        model.onEvent(EditEvent.RetryStabilize)
        assertEquals(listOf(uri), analyses.requests)

        model.onEvent(EditEvent.StabilizeOn(false))
        assertEquals(set.copy(enabled = false), session.clip("vA").stabilization)
        model.onEvent(EditEvent.StabilizeOn(true))
        assertEquals("switched back on with its settings", set, session.clip("vA").stabilization)

        // A still cannot be stabilized.
        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("只有视频片段可以稳定", model.state.value.inspector!!.stabilization!!.unavailable)
        model.onEvent(EditEvent.StabilizeOn(true))
        assertEquals("只有视频片段可以稳定", model.state.value.toast)
        assertNull(session.clip("vB").stabilization)
    }

    @Test
    fun `the inspector sets the selected picture's composite mode, one undo step each`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(CompositeMode.NORMAL, model.state.value.inspector!!.composite)
        model.onEvent(EditEvent.Composite(CompositeMode.SCREEN))
        assertEquals(CompositeMode.SCREEN, session.clip("vA").composite)
        assertEquals(CompositeMode.SCREEN, model.state.value.inspector!!.composite)
        assertEquals("合成模式：滤色", session.history.value.undoLabel)
        model.onEvent(EditEvent.Composite(CompositeMode.MULTIPLY))
        assertEquals("合成模式：正片叠底", session.history.value.undoLabel)
        assertEquals(CompositeMode.MULTIPLY, session.clip("vA").composite)
    }

    @Test
    fun `flags go on the selected clips' media, every clip of it shows them, one undo step each`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.ToggleFlag(FlagColor.RED))
        assertEquals(listOf(FlagColor.RED), session.document.value!!.media.first { it.id == "v1" }.flags)
        assertEquals("红色旗标", session.history.value.undoLabel)
        assertEquals("已为素材加上红色旗标", model.state.value.toast)
        with(model.state.value) {
            // vA and its sound aA play v1: both show the flag; the library tile too.
            assertEquals(listOf(FlagColor.RED), timeline.flagsOf(timeline.clips.first { it.id == "aA" }))
            assertEquals(listOf(FlagColor.RED), libraryItems.first { it.id == "v1" }.flags)
        }
        model.onEvent(EditEvent.ToggleFlag(FlagColor.BLUE))
        model.onEvent(EditEvent.ClearFlags)
        assertEquals(emptyList<FlagColor>(), session.document.value!!.media.first { it.id == "v1" }.flags)
        assertEquals("清除旗标", session.history.value.undoLabel)
        session.undo()
        assertEquals(listOf(FlagColor.BLUE, FlagColor.RED), session.document.value!!.media.first { it.id == "v1" }.flags)
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.ToggleFlag(FlagColor.RED))
        assertEquals("请先在时间线上选择片段", model.state.value.toast)
    }

    @Test
    fun `the inspector sets the selected picture's scaling and says what it does`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        // A clip follows the project until it chooses; the footage is 1920 × 1080 like the timeline.
        assertEquals(null, model.state.value.inspector!!.scaling)
        assertEquals("按项目设置（适应）：素材与时间线的画面比例相同，适应、填充、拉伸看起来一样", model.state.value.inspector!!.scalingNote)
        model.onEvent(EditEvent.Scaling(ClipScaling.FILL))
        assertEquals(ClipScaling.FILL, session.clip("vA").scaling)
        assertEquals("缩放方式：填充", session.history.value.undoLabel)
        model.onEvent(EditEvent.Scaling(null))
        assertEquals(null, session.clip("vA").scaling)
        assertEquals("缩放方式：按项目", session.history.value.undoLabel)
        // The 4000 × 3000 still is narrower than 16:9.
        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("按项目设置（适应）：素材 4000×3000 比画面窄：保留整个画面，左右留黑边", model.state.value.inspector!!.scalingNote)
        model.onEvent(EditEvent.Scaling(ClipScaling.CROP))
        assertEquals("素材 4000×3000 比画面大：按原尺寸居中，裁去左右各 26%、上下各 32%", model.state.value.inspector!!.scalingNote)
    }

    @Test
    fun `the timeline menu makes, duplicates, opens and deletes timelines, each one undoable step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(listOf("时间线 1"), model.state.value.timelineEntries.map { it.name })
        // A duplicate opens beside the original with the same cut under new ids; the selection is dropped.
        model.onEvent(EditEvent.DuplicateTimeline)
        val doc = session.document.value!!
        assertEquals("时间线 1 副本", doc.timelineName)
        assertEquals("复制时间线", session.history.value.undoLabel)
        assertEquals("已复制为“时间线 1 副本”", model.state.value.toast)
        assertEquals(null, session.selectedClipId.value)
        assertEquals(doc.timelines.single().timeline.clips.map { it.name }, doc.timeline.clips.map { it.name })
        assertTrue(doc.timeline.clips.none { c -> doc.timelines.single().timeline.clips.any { it.id == c.id } })
        assertEquals(listOf("时间线 1" to false, "时间线 1 副本" to true), model.state.value.timelineEntries.map { it.name to it.open })
        // Renamed in the settings sheet, and made vertical in the same step.
        model.onEvent(EditEvent.OpenTimelineSettings)
        assertEquals("时间线 1 副本", model.state.value.timelineSettings!!.name)
        model.onEvent(EditEvent.TimelineSettingsName("竖屏版"))
        model.onEvent(EditEvent.TimelineSettingsFormat(2))
        model.onEvent(EditEvent.ApplyTimelineSettings)
        assertEquals("时间线改名为“竖屏版”，时间线改为 1080×1920 竖屏", model.state.value.toast)
        assertEquals("竖屏版", model.state.value.timelineName)
        // Back to the original: its own raster comes back with it.
        val first = model.state.value.timelineEntries.first { it.name == "时间线 1" }
        assertEquals("1920×1080 · 00:00:05:00", first.detail)
        model.onEvent(EditEvent.OpenTimeline(first.id))
        assertEquals("时间线 1", session.document.value!!.timelineName)
        assertEquals(1920, session.document.value!!.format.width)
        assertEquals("已切换到“时间线 1”", model.state.value.toast)
        // A new one is empty, on the open raster, named after the highest number.
        model.onEvent(EditEvent.NewTimeline)
        assertEquals("时间线 2", session.document.value!!.timelineName)
        assertTrue(session.document.value!!.timeline.clips.isEmpty())
        assertEquals(listOf("时间线 1", "竖屏版", "时间线 2"), model.state.value.timelineEntries.map { it.name })
        // Deleting opens the one before; the last one cannot go.
        model.onEvent(EditEvent.DeleteTimeline)
        assertEquals("已删除“时间线 2”，打开“竖屏版”", model.state.value.toast)
        model.onEvent(EditEvent.DeleteTimeline)
        model.onEvent(EditEvent.DeleteTimeline)
        assertEquals("项目至少要有一条时间线", model.state.value.toast)
        assertEquals(listOf("时间线 1"), model.state.value.timelineEntries.map { it.name })
        // Undo brings the deleted timeline back, open.
        session.undo()
        assertEquals(listOf("时间线 1", "竖屏版"), session.document.value!!.timelineEntries.map { it.name })
    }

    @Test
    fun `the timeline settings change the raster and the project's scaling in one undoable step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.OpenTimelineSettings)
        val opened = model.state.value.timelineSettings!!
        assertEquals(listOf("1080p 横屏", "4K 横屏", "1080p 竖屏", "1080p 方形"), opened.formats)
        assertEquals(0, opened.formatIndex)
        assertEquals("30 fps", opened.fpsLabel)
        assertEquals(ClipScaling.FIT, opened.scaling)
        assertFalse("nothing to apply yet", opened.changed)
        model.onEvent(EditEvent.TimelineSettingsFormat(2))
        model.onEvent(EditEvent.TimelineSettingsScaling(ClipScaling.FILL))
        val edited = model.state.value.timelineSettings!!
        assertTrue(edited.changed)
        assertTrue(edited.note, edited.note.contains("按项目设置缩放"))
        model.onEvent(EditEvent.ApplyTimelineSettings)
        assertEquals(null, model.state.value.timelineSettings)
        val format = session.document.value!!.format
        assertEquals(listOf(1080, 1920), listOf(format.width, format.height))
        assertEquals(ClipScaling.FILL, format.scaling)
        assertEquals("时间线设置", session.history.value.undoLabel)
        assertEquals("时间线改为 1080×1920 竖屏，比例不同的素材填充", model.state.value.toast)
        assertTrue(model.state.value.summary, model.state.value.summary.contains("1080×1920"))
        // Cancelling leaves the project alone.
        model.onEvent(EditEvent.OpenTimelineSettings)
        assertEquals(2, model.state.value.timelineSettings!!.formatIndex)
        model.onEvent(EditEvent.TimelineSettingsFormat(0))
        model.onEvent(EditEvent.CloseTimelineSettings)
        assertEquals(1080, session.document.value!!.format.width)
    }

    @Test
    fun `the inspector sets the selected footage's retime process and a still has no frames to make`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        val inspector = model.state.value.inspector!!
        assertEquals(RetimeProcess.NEAREST, inspector.retime)
        assertTrue(inspector.canRetime)
        assertEquals("慢放和低帧率素材缺的帧重复前一帧", inspector.retimeNote)
        model.onEvent(EditEvent.Retime(RetimeProcess.FRAME_BLEND))
        assertEquals(RetimeProcess.FRAME_BLEND, session.clip("vA").retime)
        assertEquals("变速处理：帧混合", session.history.value.undoLabel)
        assertEquals("慢放和低帧率素材缺的帧由前后两帧按时间混合，动作更连贯", model.state.value.inspector!!.retimeNote)
        model.onEvent(EditEvent.SelectClip("vB"))
        assertFalse(model.state.value.inspector!!.canRetime)
        assertEquals("图片没有可以补出的中间帧", model.state.value.inspector!!.retimeNote)
    }

    @Test
    fun `dragging a sound's volume line shows it live and records one step when let go`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("aA"))
        model.onEvent(EditEvent.VolumeLine("aA", emptyList(), 6f, commit = false))
        assertEquals(6f, model.state.value.timeline.clips.first { it.id == "aA" }.volumeDb, 0f)
        assertEquals("heard while dragging", 6f, program.document!!.timeline.clip("aA")!!.volumeDb, 0f)
        assertEquals("nothing recorded while dragging", 0f, session.clip("aA").volumeDb, 0f)
        model.onEvent(EditEvent.VolumeLine("aA", emptyList(), 4.5f, commit = true))
        assertEquals(4.5f, session.clip("aA").volumeDb, 0f)
        assertEquals("调整音量", session.history.value.undoLabel)
        // A drag let go where it began changes nothing, and the monitor hears the clip as it was again.
        model.onEvent(EditEvent.VolumeLine("aA", emptyList(), 2f, commit = false))
        model.onEvent(EditEvent.VolumeLine("aA", emptyList(), 0f, commit = true))
        assertEquals(4.5f, program.document!!.timeline.clip("aA")!!.volumeDb, 0f)
        model.onEvent(EditEvent.VolumeLine("vA", emptyList(), 3f, commit = true))
        assertEquals("只有音频片段有音量线", model.state.value.toast)
    }

    @Test
    fun `slider edits on another clip or after a pause are separate undo steps`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Scale(150f))
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.Scale(150f))
        clock.now += 2_000
        model.onEvent(EditEvent.Scale(200f))

        session.undo()
        assertEquals(150, session.clip("vB").transform.scalePercent)
        session.undo()
        assertEquals(100, session.clip("vB").transform.scalePercent)
        assertEquals(150, session.clip("vA").transform.scalePercent)
        session.undo()
        assertEquals(100, session.clip("vA").transform.scalePercent)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `volume and fades of a picture clip edit its linked sound, limited to the clip`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))

        model.onEvent(EditEvent.Volume(-6f))
        model.onEvent(EditEvent.FadeIn(9f))

        val sound = session.clip("aA")
        assertEquals(-6f, sound.volumeDb, 0f)
        assertEquals("fades stop at half the clip", 1.5f, sound.fadeInSeconds, 1e-4f)
        assertEquals(0f, session.clip("vA").volumeDb, 0f)
        val inspector = model.state.value.inspector!!
        assertEquals("aA", inspector.audioClipId)
        assertEquals(-6f, inspector.volumeDb, 0f)
        assertEquals(1.5f, inspector.fadeIn, 1e-4f)
        assertEquals(1.5f, inspector.maxFade, 1e-4f)
        assertEquals("淡入", session.history.value.undoLabel)

        model.onEvent(EditEvent.Volume(40f))
        assertEquals(12f, session.clip("aA").volumeDb, 0f)
    }

    @Test
    fun `a picture fades by its own handles in the video tab and on the timeline, apart from its sound`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.PictureFadeIn(9f))
        model.onEvent(EditEvent.PictureFadeOut(0.5f))
        assertEquals("fades stop at half the clip", 1.5f, session.clip("vA").fadeInSeconds, 1e-4f)
        assertEquals(0.5f, session.clip("vA").fadeOutSeconds, 0f)
        assertEquals("the sound keeps its own", 0f, session.clip("aA").fadeInSeconds, 0f)
        val inspector = model.state.value.inspector!!
        assertTrue(inspector.pictureFades)
        assertEquals(listOf(1.5f, 0.5f, 1.5f), listOf(inspector.pictureFadeIn, inspector.pictureFadeOut, inspector.maxPictureFade).map { (it * 1e4f).roundToInt() / 1e4f })
        assertEquals("the audio tab shows the sound's", 0f, inspector.fadeIn, 0f)
        assertEquals("画面淡出", session.history.value.undoLabel)

        // The timeline's handle shows as it is dragged and lands as one undo step.
        model.onEvent(EditEvent.FadeDrag("vA", fadeIn = false, seconds = 1f, commit = false))
        assertEquals("not yet in the project", 0.5f, session.clip("vA").fadeOutSeconds, 0f)
        assertEquals("but on the timeline", 1f, model.state.value.timeline.clips.single { it.id == "vA" }.fadeOutSeconds, 0f)
        model.onEvent(EditEvent.FadeDrag("vA", fadeIn = false, seconds = 1.2f, commit = true))
        assertEquals(1.2f, session.clip("vA").fadeOutSeconds, 0f)
        assertEquals("画面淡出", session.history.value.undoLabel)
        session.undo()
        assertEquals(0.5f, session.clip("vA").fadeOutSeconds, 0f)
        // A sound's handle on the Edit page fades the sound, and selects it.
        model.onEvent(EditEvent.FadeDrag("aA", fadeIn = true, seconds = 0.4f, commit = true))
        assertEquals(0.4f, session.clip("aA").fadeInSeconds, 0f)
        assertEquals("淡入", session.history.value.undoLabel)
        assertEquals("aA", session.selectedClipId.value)
        assertFalse("a sound has no picture fades", model.state.value.inspector!!.pictureFades)
    }

    @Test
    fun `a picture keys out its screen - on, a screen or a picked colour, its amounts, the matte view - one undo step each`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertTrue(model.state.value.inspector!!.keyable)
        model.onEvent(EditEvent.ChromaKeyEyedropper(true))
        assertEquals("先开启抠像", model.state.value.toast)
        model.onEvent(EditEvent.ChromaKeyOn(true))
        assertEquals(ChromaKey(), session.clip("vA").chromaKey)
        assertEquals("开启抠像", session.history.value.undoLabel)
        assertEquals("the sound has no picture to key", null, session.clip("aA").chromaKey)
        model.onEvent(EditEvent.ChromaKeyScreen(KeyScreen.BLUE))
        assertEquals(ChromaKey.BLUE, session.clip("vA").chromaKey!!.color)
        assertEquals("抠像：蓝幕", session.history.value.undoLabel)
        model.onEvent(EditEvent.ChromaKeyAmount(KeyControl.SIMILARITY, 55f))
        model.onEvent(EditEvent.ChromaKeyAmount(KeyControl.SIMILARITY, 60f))
        assertEquals(60f, session.clip("vA").chromaKey!!.similarity, 0f)
        assertEquals("抠像：相似度", session.history.value.undoLabel)
        session.undo()
        assertEquals("the slider's drag is one step", 40f, session.clip("vA").chromaKey!!.similarity, 0f)
        model.onEvent(EditEvent.ChromaKeyAmount(KeyControl.CLEAN_BLACK, 25f))
        assertEquals(25f to "抠像：去黑", session.clip("vA").chromaKey!!.cleanBlack to session.history.value.undoLabel)
        session.undo()
        // The eyedropper: armed with the playhead on the clip, a tap picks the source's colour there and ends it.
        previews.frame = FramePixels(4, 2, IntArray(8) { 0xFF3A9D5C.toInt() })
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.ChromaKeyEyedropper(true))
        assertNotNull(model.state.value.keyPick)
        assertTrue(model.state.value.inspector!!.keyPicking)
        model.onEvent(EditEvent.PickKeyColor(0.5f, 0.5f))
        advanceUntilIdle()
        assertEquals(0x3A9D5C, session.clip("vA").chromaKey!!.color)
        assertEquals("抠像：吸取颜色", session.history.value.undoLabel)
        assertEquals("#3A9D5C", model.state.value.inspector!!.chromaKey!!.colorLabel)
        assertNull("one pick ends it", model.state.value.keyPick)
        // The matte view: on the monitor only, and gone with another selection.
        model.onEvent(EditEvent.ChromaKeyMatte(true))
        assertEquals("vA", program.keyMatteClipId)
        assertTrue(model.state.value.inspector!!.keyMatte)
        model.onEvent(EditEvent.SelectClip("vB"))
        assertNull(program.keyMatteClipId)
        assertFalse(model.state.value.inspector!!.keyMatte)
        // Switched off; a sound has none.
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.ChromaKeyMatte(true))
        model.onEvent(EditEvent.ChromaKeyOn(false))
        assertNull(session.clip("vA").chromaKey)
        assertNull("switching it off ends the matte view", program.keyMatteClipId)
        model.onEvent(EditEvent.SelectClip("mus"))
        assertFalse(model.state.value.inspector!!.keyable)
    }

    @Test
    fun `a fade bends by its curve - the inspector's sliders and the timeline's dot, each clip's apart, one undo step a drag`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.PictureFadeIn(1f))
        model.onEvent(EditEvent.PictureFadeInCurve(0.4f))
        model.onEvent(EditEvent.PictureFadeInCurve(0.6f))
        assertEquals(0.6f, session.clip("vA").fadeInCurve, 0f)
        assertEquals("画面淡入曲线", session.history.value.undoLabel)
        assertEquals(0.6f, model.state.value.inspector!!.pictureFadeInCurve, 0f)
        assertEquals("the sound keeps its own", 0f, session.clip("aA").fadeInCurve, 0f)
        session.undo()
        assertEquals("the slider's drag is one step", 0f, session.clip("vA").fadeInCurve, 0f)
        assertEquals("the fade itself stays", 1f, session.clip("vA").fadeInSeconds, 0f)
        // The sound's, in the audio tab.
        model.onEvent(EditEvent.FadeOut(1f))
        model.onEvent(EditEvent.FadeOutCurve(-2f))
        assertEquals("held to −1", -1f, session.clip("aA").fadeOutCurve, 0f)
        assertEquals("淡出曲线", session.history.value.undoLabel)
        assertEquals(-1f, model.state.value.inspector!!.fadeOutCurve, 0f)
        // The timeline's dot shows as it is dragged and lands as one undo step.
        model.onEvent(EditEvent.FadeCurveDrag("vA", fadeIn = false, curve = 0.3f, commit = false))
        assertEquals("not yet in the project", 0f, session.clip("vA").fadeOutCurve, 0f)
        assertEquals("but on the timeline", 0.3f, model.state.value.timeline.clips.single { it.id == "vA" }.fadeOutCurve, 0f)
        model.onEvent(EditEvent.FadeCurveDrag("vA", fadeIn = false, curve = 0.5f, commit = true))
        assertEquals(0.5f, session.clip("vA").fadeOutCurve, 0f)
        assertEquals("画面淡出曲线", session.history.value.undoLabel)
        model.onEvent(EditEvent.FadeCurveDrag("aA", fadeIn = true, curve = -0.25f, commit = true))
        assertEquals(-0.25f, session.clip("aA").fadeInCurve, 0f)
        assertEquals("淡入曲线", session.history.value.undoLabel)
        assertEquals("aA", session.selectedClipId.value)
    }

    @Test
    fun `a picture's sound has its own equaliser, edited in the EQ sheet from the audio tab, one undo step a drag`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals("平直", model.state.value.inspector!!.eqSummary)
        model.onEvent(EditEvent.OpenClipEq)
        val sheet = model.state.value.clipEq!!
        assertEquals("aA" to "片段 · ${session.clip("aA").name}", sheet.trackId to sheet.name)
        assertEquals("只作用于这个片段，在轨道的均衡器之前", sheet.appliesTo)
        model.onEvent(EditEvent.ClipEqPreset(EqPreset.VOICE))
        assertEquals(EqPreset.VOICE.eq, session.clip("aA").eq)
        assertEquals("片段均衡器：预设 人声清晰", session.history.value.undoLabel)
        model.onEvent(EditEvent.ClipEq(EqControl.MID_DB, 5f))
        model.onEvent(EditEvent.ClipEq(EqControl.MID_DB, 6f))
        assertEquals(6f, session.clip("aA").eq.midDb, 0f)
        assertEquals("片段均衡器：中频增益", session.history.value.undoLabel)
        assertEquals(6f, model.state.value.clipEq!!.eq.midDb, 0f)
        assertTrue(model.state.value.inspector!!.eqActive)
        assertEquals("the picture keeps none", TrackEq.FLAT, session.clip("vA").eq)
        session.undo()
        assertEquals("the slider's burst is one step", EqPreset.VOICE.eq, session.clip("aA").eq)
        model.onEvent(EditEvent.ClipEqEnabled(false))
        assertEquals("已旁通", model.state.value.inspector!!.eqSummary)
        model.onEvent(EditEvent.ResetClipEq)
        assertEquals(TrackEq.FLAT, session.clip("aA").eq)
        model.onEvent(EditEvent.CloseClipEq)
        assertNull(model.state.value.clipEq)
    }

    @Test
    fun `audio controls on a clip without sound only explain why`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))

        model.onEvent(EditEvent.Volume(-3f))

        assertEquals("所选片段没有音频", model.state.value.toast)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `speed changes resize the clip with its sound and ripple later clips on those tracks`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))

        model.onEvent(EditEvent.Speed(PlaybackSpeed.DOUBLE.ordinal))

        assertEquals(45, session.clip("vA").durationFrames)
        assertEquals(ClipSpeed.of(PlaybackSpeed.DOUBLE), session.clip("aA").speed)
        assertEquals(45, session.clip("aA").durationFrames)
        assertEquals(45, session.clip("vB").start)
        assertEquals("A2 is not affected", 0 to 120, session.clip("mus").let { it.start to it.durationFrames })
        assertEquals("变速 2×", session.history.value.undoLabel)
        assertEquals("播放速度 2×，片段时长 00:00:01:15", model.state.value.toast)
        assertEquals(PlaybackSpeed.DOUBLE.ordinal, model.state.value.inspector?.speedIndex)
    }

    @Test
    fun `reverse flags the clip with its sound and starts making the reversed copy of its media`() = runTest {
        val copies = FakeReverseCopyRepository()
        val (session, model) = editStage(copies = copies)
        model.onEvent(EditEvent.SelectClip("vA"))

        model.onEvent(EditEvent.Reverse(true))

        assertTrue(session.clip("vA").reverse)
        assertTrue(session.clip("aA").reverse)
        assertTrue(model.state.value.inspector!!.reverse)
        assertEquals("倒放", session.history.value.undoLabel)
        assertEquals(listOf(listOf("v1") to true), copies.requests)
        assertEquals("已设为倒放：正在后台生成倒放素材，完成后预览自动更新", model.state.value.toast)
        assertEquals("正在生成倒放素材… 0%", model.state.value.inspector!!.reverseStatus)
        assertEquals(setOf("v1"), model.state.value.timeline.reverseMakingAssetIds)
        assertEquals("倒放生成中", model.state.value.timeline.reverseLabel(session.clip("aA")))
    }

    @Test
    fun `the inspector follows the reversed copy until it is ready, and a failed copy is retried`() = runTest {
        val copies = FakeReverseCopyRepository()
        val (session, model) = editStage(copies = copies)
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Reverse(true))

        copies.set("v1", ReverseCopy.Making(0.42f))
        assertEquals("正在生成倒放素材… 42%", model.state.value.inspector!!.reverseStatus)

        copies.set("v1", ReverseCopy.Failed("解码或编码出错"))
        assertTrue(model.state.value.inspector!!.reverseFailed)
        assertEquals("倒放素材生成失败：解码或编码出错。关闭再打开倒放可重试。", model.state.value.inspector!!.reverseStatus)
        assertEquals("倒放失败", model.state.value.timeline.reverseLabel(session.clip("vA")))

        model.onEvent(EditEvent.Reverse(false))
        model.onEvent(EditEvent.Reverse(true))
        assertEquals(listOf("v1") to true, copies.requests.last())
        assertEquals(ReverseCopy.Making(0f), copies.copies.value["v1"])

        copies.set("v1", ReverseCopy.Ready("file:///copy.mp4"))
        assertNull(model.state.value.inspector!!.reverseStatus)
        assertFalse(model.state.value.inspector!!.reverseFailed)
        assertEquals("倒放", model.state.value.timeline.reverseLabel(session.clip("vA")))
    }

    @Test
    fun `a reversed copy that already exists is used at once, and stills need none`() = runTest {
        val copies = FakeReverseCopyRepository().apply { set("v1", ReverseCopy.Ready("file:///copy.mp4")) }
        val (session, model) = editStage(copies = copies)

        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Reverse(true))
        assertEquals("已设为倒放", model.state.value.toast)
        assertNull(model.state.value.inspector!!.reverseStatus)

        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.Reverse(true))
        assertTrue(session.clip("vB").reverse)
        assertEquals("only the footage was asked for", listOf(listOf("v1") to true), copies.requests)
    }

    @Test
    fun `track toggles are undoable edits and a locked track refuses clip edits`() = runTest {
        val (session, model) = editStage()

        model.onEvent(EditEvent.TrackEnabled(Timeline.V2, false))
        assertFalse(session.document.value!!.timeline.track(Timeline.V2)!!.enabled)
        assertEquals("隐藏轨道", session.history.value.undoLabel)

        model.onEvent(EditEvent.TrackLocked(Timeline.V1, true))
        assertTrue(session.document.value!!.timeline.track(Timeline.V1)!!.locked)
        assertEquals("锁定轨道", session.history.value.undoLabel)

        model.onEvent(EditEvent.SelectClip("vA"))
        assertTrue(model.state.value.inspector!!.locked)
        model.onEvent(EditEvent.Scale(150f))
        assertEquals("轨道已锁定", model.state.value.toast)
        assertEquals(100, session.clip("vA").transform.scalePercent)

        model.onEvent(EditEvent.ToastShown)
        assertNull(model.state.value.toast)
        model.onEvent(EditEvent.RippleDelete)
        assertEquals("轨道已锁定", model.state.value.toast)
        assertEquals(4, session.document.value!!.timeline.clips.size)
        assertEquals("锁定轨道", session.history.value.undoLabel)
    }

    @Test
    fun `the titles tab adds a title on V2 at the playhead and opens the video inspector`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.InspectorTab(EditUiState.TAB_AUDIO))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TITLES.ordinal))
        assertEquals(TitleTemplate.entries.map { "title:${it.name}" }, model.state.value.libraryItems.map { it.id })
        model.onEvent(EditEvent.Seek(30))

        model.onEvent(EditEvent.SelectLibraryItem("title:LOWER_THIRD"))
        assertTrue(model.state.value.libraryActionEnabled)
        model.onEvent(EditEvent.ApplyLibraryItem)

        val title = session.clip("t-1")
        assertEquals(Timeline.V2, title.trackId)
        assertEquals(ClipKind.TITLE, title.kind)
        assertEquals(30 to 90, title.start to title.durationFrames)
        assertEquals(TitleStyle("人物 · 地点", 56, TitleTemplate.LOWER_THIRD), title.title)
        assertEquals("t-1", session.selectedClipId.value)
        assertEquals(EditUiState.TAB_VIDEO, model.state.value.inspectorTab)
        assertTrue(model.state.value.inspector!!.isTitle)
        assertEquals("添加标题", session.history.value.undoLabel)
        assertEquals("已在 00:00:01:00 添加标题", model.state.value.toast)

        model.onEvent(EditEvent.TitleText("Hello"))
        model.onEvent(EditEvent.TitleSize(500f))
        val edited = session.clip("t-1")
        assertEquals("Hello", edited.title?.text)
        assertEquals("标题：Hello", edited.name)
        assertEquals(160, edited.title?.sizePx)
        assertEquals("Hello", model.state.value.inspector?.titleText)
    }

    @Test
    fun `the effects tab applies an effect that can then be tuned and removed`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.EFFECTS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("effect:VIGNETTE"))
        assertTrue(model.state.value.libraryActionEnabled)

        model.onEvent(EditEvent.ApplyLibraryItem)

        assertEquals(listOf(ClipEffect("fx-1", EffectType.VIGNETTE)), session.clip("vA").effects)
        assertEquals(EditUiState.TAB_EFFECTS, model.state.value.inspectorTab)
        assertEquals(listOf(EffectRowUi("fx-1", "暗角", true, 50, KeyframeUi(playheadInside = true))), model.state.value.inspector?.effects)
        assertEquals("已应用特效：暗角", model.state.value.toast)

        model.onEvent(EditEvent.EffectAmount("fx-1", 130f))
        model.onEvent(EditEvent.EffectEnabled("fx-1", false))
        assertEquals(ClipEffect("fx-1", EffectType.VIGNETTE, amount = 100, enabled = false), session.clip("vA").effects.single())

        model.onEvent(EditEvent.RemoveEffect("fx-1"))
        assertTrue(session.clip("vA").effects.isEmpty())
        assertEquals("已移除特效：暗角", model.state.value.toast)
        assertEquals("移除特效", session.history.value.undoLabel)
    }

    @Test
    fun `an effect is keyframed at the playhead, ramps with a change elsewhere, and the arrows walk its keyframes`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.EFFECTS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("effect:GAUSSIAN_BLUR"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        model.onEvent(EditEvent.Seek(10))
        model.onEvent(EditEvent.ToggleEffectKeyframe("fx-1"))
        assertEquals("已为高斯模糊添加关键帧 · 共 1 个", model.state.value.toast)
        assertEquals("特效关键帧", session.history.value.undoLabel)

        model.onEvent(EditEvent.Seek(70))
        model.onEvent(EditEvent.EffectAmount("fx-1", 90f))
        assertEquals(listOf(10 to 50, 70 to 90), session.clip("vA").effects.single().keyframes.map { it.frame to it.amount })

        model.onEvent(EditEvent.Seek(40))
        val row = model.state.value.inspector!!.effects.single()
        assertEquals("half-way from 50 to 90", 70, row.amount)
        assertEquals(listOf(2, 0), listOf(row.keys.count, if (row.keys.atKeyframe) 1 else 0))
        assertEquals("2 个关键帧 · 调整强度会在播放头处添加关键帧", row.keys.hint)

        model.onEvent(EditEvent.NextEffectKeyframe("fx-1"))
        assertEquals(70, model.state.value.playheadFrame)
        model.onEvent(EditEvent.PreviousEffectKeyframe("fx-1"))
        assertEquals(10, model.state.value.playheadFrame)
        assertTrue(model.state.value.inspector!!.effects.single().keys.atKeyframe)
        assertEquals("2 个关键帧 · 调整会记录在此关键帧", model.state.value.inspector!!.effects.single().keys.hint)
    }

    @Test
    fun `speed points split the clip and the speed chips then set the section under the playhead`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.AddSpeedPoint)
        assertEquals("已添加变速点 · 片段分为 2 段", model.state.value.toast)
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.AddSpeedPoint)
        assertEquals("on a point the section after it is under the playhead", 3, model.state.value.inspector!!.speedPoints.current)
        model.onEvent(EditEvent.Seek(45))
        val points = model.state.value.inspector!!.speedPoints
        assertEquals(listOf("1×", "1×", "1×"), points.sections)
        assertEquals(2, points.current)
        assertEquals("上面的速度只改第 2 段（播放头所在），其余各段不变", points.hint)

        model.onEvent(EditEvent.Speed(PlaybackSpeed.entries.indexOf(PlaybackSpeed.HALF)))
        assertEquals(listOf(30, 60, 30), session.clip("vA").speedSections.map { it.frames })
        assertEquals("its sound with it", listOf(30, 60, 30), session.clip("aA").speedSections.map { it.frames })
        assertEquals("第 2 段速度 0.5×，片段时长 00:00:04:00", model.state.value.toast)
        assertEquals("vB moves on by the second of slow motion", 120, session.clip("vB").start)
        assertEquals("0.5×", model.state.value.inspector!!.speedLabel)

        model.onEvent(EditEvent.ClearSpeedPoints)
        assertFalse(session.clip("vA").retimed)
        assertEquals("清除变速点", session.history.value.undoLabel)
        assertEquals(90, session.clip("vA").durationFrames)
    }

    @Test
    fun `the ramp slider smooths the speed point nearest the playhead in one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.AddSpeedPoint)
        model.onEvent(EditEvent.Seek(45))
        val even = model.state.value.inspector!!.speedPoints
        assertEquals(1, even.rampPoint)
        assertEquals("nothing to smooth while both sides play at 1×", 0f, even.rampMaxSeconds, 0f)
        assertEquals("最近的第 1 个变速点两侧速度相同（1×），不需要平滑过渡", even.rampNote)

        model.onEvent(EditEvent.Speed(PlaybackSpeed.entries.indexOf(PlaybackSpeed.HALF)))
        val sudden = model.state.value.inspector!!.speedPoints
        assertEquals("立即", sudden.rampText)
        assertEquals("room up to the clip's start either side of the point: 2 s", 2f, sudden.rampMaxSeconds, 1e-6f)
        assertEquals("最近的第 1 个变速点（1× → 0.5×）立即变速；拖动滑块，让速度在一段时间内平滑过渡", sudden.rampNote)
        val length = session.clip("vA").durationFrames

        model.onEvent(EditEvent.SpeedRamp(0.3f))
        model.onEvent(EditEvent.SpeedRamp(0.5f))
        assertEquals(listOf(0, 15), session.clip("vA").speedSections.map { it.rampFrames })
        assertEquals("its sound with it", listOf(0, 15), session.clip("aA").speedSections.map { it.rampFrames })
        assertEquals("a ramp keeps the clip's length", length, session.clip("vA").durationFrames)
        val ramped = model.state.value.inspector!!.speedPoints
        assertEquals(0.5f, ramped.rampSeconds, 1e-6f)
        assertEquals("0.5 秒", ramped.rampText)
        assertEquals("第 1 个变速点（1× → 0.5×）：速度在 0.5 秒内平滑过渡，变速点在过渡中间，片段时长不变", ramped.rampNote)
        assertEquals("平滑变速", session.history.value.undoLabel)
        session.undo()
        assertEquals("the whole drag undone at once", listOf(0, 0), session.clip("vA").speedSections.map { it.rampFrames })
    }

    @Test
    fun `effects cannot be applied to a sound clip`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("mus"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.EFFECTS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("effect:GLOW"))
        assertFalse(model.state.value.libraryActionEnabled)

        model.onEvent(EditEvent.ApplyLibraryItem)

        assertEquals("特效只能应用到画面片段", model.state.value.toast)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `the transitions tab applies a half-second transition into the selected clip, centred on the cut`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:CROSS_DISSOLVE"))

        model.onEvent(EditEvent.ApplyLibraryItem)

        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER), session.clip("vB").transitionIn)
        assertEquals("应用转场", session.history.value.undoLabel)
        assertEquals("已为“vB”应用转场：交叉溶解", model.state.value.toast)
    }

    @Test
    fun `the library shows every transition, title and effect with its illustration, the new transitions included`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        val transitions = model.state.value.libraryItems
        assertEquals(11, transitions.size)
        assertEquals("the colour dip sits with the other dips", listOf("黑场过渡", "白场闪白", "颜色过渡"), transitions.subList(1, 4).map { it.title })
        assertEquals(LibraryArt.DIP_COLOR, transitions[3].art)
        assertEquals(listOf("圆形划像", "时钟划像", "对开门", "模糊溶解"), transitions.takeLast(4).map { it.title })
        assertEquals(listOf(LibraryArt.IRIS, LibraryArt.CLOCK, LibraryArt.BARN, LibraryArt.BLUR_DISSOLVE), transitions.takeLast(4).map { it.art })
        assertTrue("no placeholder text on illustrated tiles", transitions.all { it.glyph == null && it.art != null })
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TITLES.ordinal))
        assertTrue(model.state.value.libraryItems.all { it.art != null })
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.EFFECTS.ordinal))
        assertEquals(LibraryArt.MOSAIC, model.state.value.libraryItems.first { it.title == "马赛克" }.art)
        // A new transition goes on like the others.
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:IRIS"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        assertEquals(Transition(TransitionType.IRIS, 15, alignment = TransitionAlignment.CENTER), session.clip("vB").transitionIn)
        assertEquals("已为“vB”应用转场：圆形划像", model.state.value.toast)
    }

    @Test
    fun `the inspector tunes and removes the opening transition of the selected clip`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:CROSS_DISSOLVE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        val inspector = model.state.value.inspector!!
        assertEquals("交叉溶解", inspector.transitionName)
        assertEquals(0.5f, inspector.transitionSeconds, 1e-6f)
        assertEquals("vB lasts 2 s, so at most 1 s", 1f, inspector.transitionMaxSeconds, 1e-6f)

        model.onEvent(EditEvent.TransitionSeconds(0.8f))
        model.onEvent(EditEvent.TransitionSeconds(5f))
        assertEquals("clamped to half the clip", 30, session.clip("vB").transitionIn!!.frames)
        assertEquals("one drag is one undo step", "转场时长", session.history.value.undoLabel)

        model.onEvent(EditEvent.RemoveTransition)
        assertEquals(null, session.clip("vB").transitionIn)
        assertEquals(null, model.state.value.inspector!!.transitionName)
        assertEquals("已移除转场：交叉溶解", model.state.value.toast)
    }

    @Test
    fun `a colour dip from the library starts amber, and its colour is picked in the inspector`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        assertTrue("the library lists it", model.state.value.libraryItems.any { it.id == "transition:DIP_TO_COLOR" && it.title == "颜色过渡" })
        model.onEvent(EditEvent.SelectLibraryItem("transition:DIP_TO_COLOR"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        assertEquals(TitleColor.AMBER, session.clip("vB").transitionIn!!.color)
        model.onEvent(EditEvent.TransitionColor(TitleColor.RED))
        assertEquals(TitleColor.RED, session.clip("vB").transitionIn!!.color)
        assertEquals(TitleColor.RED, model.state.value.inspector!!.transition!!.color)
        assertEquals("转场颜色", session.history.value.undoLabel)
        // Changing the kind keeps the colour for a way back.
        model.onEvent(EditEvent.TransitionKind(TransitionType.DIP_TO_BLACK))
        model.onEvent(EditEvent.TransitionKind(TransitionType.DIP_TO_COLOR))
        assertEquals(TitleColor.RED, session.clip("vB").transitionIn!!.color)
    }

    @Test
    fun `dragging a transition's edge on the timeline previews its length live and commits one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:CROSS_DISSOLVE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        val before = session.clip("vB").transitionIn!!.frames
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.TransitionDrag("vB", 20, commit = false))
        assertEquals("not yet in the project", before, session.clip("vB").transitionIn!!.frames)
        assertEquals("but on the timeline", 20, model.state.value.timeline.clips.single { it.id == "vB" }.transitionIn!!.frames)
        model.onEvent(EditEvent.TransitionDrag("vB", 24, commit = false))
        model.onEvent(EditEvent.TransitionDrag("vB", 26, commit = true))
        assertEquals(26, session.clip("vB").transitionIn!!.frames)
        assertEquals("转场时长", session.history.value.undoLabel)
        assertEquals("the clip it opens is selected", "vB", session.selectedClipId.value)
        session.undo()
        assertEquals("the whole drag is one step", before, session.clip("vB").transitionIn!!.frames)
        model.onEvent(EditEvent.TransitionDrag("vB", 900, commit = true))
        assertEquals("held to half the clip", 30, session.clip("vB").transitionIn!!.frames)
    }

    @Test
    fun `a wipe's angle turns it, one drag is one undo step, and an old reversed wipe reads 180 degrees`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:WIPE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        model.onEvent(EditEvent.TransitionReverse(true))
        assertEquals("the old reverse reads as half a turn", 180, model.state.value.inspector!!.transition!!.travelAngle)
        model.onEvent(EditEvent.TransitionAngle(30))
        model.onEvent(EditEvent.TransitionAngle(45))
        val wipe = session.clip("vB").transitionIn!!
        assertEquals(listOf(45, 45), listOf(wipe.angle, wipe.travelAngle))
        assertFalse("the angle takes over the reverse", wipe.reverse)
        assertEquals("转场角度", session.history.value.undoLabel)
        session.undo()
        assertEquals("one drag was one step", 180, session.clip("vB").transitionIn!!.travelAngle)
        model.onEvent(EditEvent.TransitionAngle(270))
        assertEquals("held to -179..180", -90, session.clip("vB").transitionIn!!.angle)
        assertEquals(listOf("从左 0°", "从左上 45°", "从上 90°", "从右 180°", "从下 -90°", "从左下 -45°", "从右 -170°"),
            listOf(0, 45, 90, 180, -90, -45, -170).map { angleLabel(it) })
    }

    @Test
    fun `the transition inspector changes the kind in place and shapes ease, direction, edge and border`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:WIPE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        model.onEvent(EditEvent.TransitionSeconds(0.8f))

        model.onEvent(EditEvent.TransitionEasing(TransitionEase.IN_OUT))
        model.onEvent(EditEvent.TransitionReverse(true))
        assertEquals("转场方向", session.history.value.undoLabel)
        model.onEvent(EditEvent.TransitionSoftness(10))
        model.onEvent(EditEvent.TransitionSoftness(25))
        assertEquals("one softness drag is one undo step", "转场柔化", session.history.value.undoLabel)
        model.onEvent(EditEvent.TransitionBorder(30))
        model.onEvent(EditEvent.TransitionBorderColor(TitleColor.YELLOW))
        val wipe = Transition(
            TransitionType.WIPE, 24, TransitionEase.IN_OUT, reverse = true, softness = 25, border = 30, borderColor = TitleColor.YELLOW,
            alignment = TransitionAlignment.CENTER,
        )
        assertEquals(wipe, session.clip("vB").transitionIn)
        assertEquals(wipe, model.state.value.inspector!!.transition)

        model.onEvent(EditEvent.TransitionKind(TransitionType.CLOCK_WIPE))
        assertEquals("length and style stay", wipe.copy(type = TransitionType.CLOCK_WIPE), session.clip("vB").transitionIn)
        assertEquals("转场改为：时钟划像", model.state.value.toast)
        assertEquals("转场类型", session.history.value.undoLabel)
        assertEquals("时钟划像", model.state.value.inspector!!.transitionName)

        session.undo()
        assertEquals(wipe, session.clip("vB").transitionIn)

        // Undo walks back each setting: colour, border, the softness drag, direction, ease.
        repeat(4) { session.undo() }
        assertEquals(Transition(TransitionType.WIPE, 24, TransitionEase.IN_OUT, alignment = TransitionAlignment.CENTER), session.clip("vB").transitionIn)
    }

    @Test
    fun `alignment places a new transition around the cut, and the timeline draws it where it plays`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TRANSITIONS.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("transition:CROSS_DISSOLVE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        // Centred on the cut at 90: 7 of its 15 frames before it (vB is a still, vA has 17 s of source left).
        assertEquals(TransitionSpanUi(7, 15), model.state.value.timeline.transitionSpans["vB"])
        assertEquals(null, model.state.value.inspector!!.transitionNote)

        model.onEvent(EditEvent.TransitionAlign(TransitionAlignment.END))
        assertEquals("转场对齐", session.history.value.undoLabel)
        assertEquals(TransitionAlignment.END, session.clip("vB").transitionIn!!.alignment)
        assertEquals(TransitionSpanUi(15, 15), model.state.value.timeline.transitionSpans["vB"])
        model.onEvent(EditEvent.TransitionAlign(TransitionAlignment.START))
        assertEquals(TransitionSpanUi(0, 15), model.state.value.timeline.transitionSpans["vB"])
    }

    @Test
    fun `the note says when a source makes a centred transition slide, or an anchored one shorten`() = runTest {
        fun into(alignment: TransitionAlignment) = vB.copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = alignment))
        // vA uses 0–3 s of a 3.2 s file: 6 frames after the cut, so the centred dissolve leans before it.
        val short = UiFixtures.video("v1", durationMs = 3_200)
        val (_, model) = editStage(UiFixtures.document(media = listOf(short, song, still), clips = listOf(vA, aA, into(TransitionAlignment.CENTER), mus)))
        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("前一片段出点后余量不够，转场移向剪辑点前（剪辑点前 0.3 秒、后 0.2 秒）。", model.state.value.inspector!!.transitionNote)
        assertEquals(TransitionSpanUi(9, 15), model.state.value.timeline.transitionSpans["vB"])
        // No clip before it: whatever the alignment, it starts at the cut.
        val (_, alone) = editStage(UiFixtures.document(media = listOf(footage, song, still), clips = listOf(into(TransitionAlignment.END).copy(start = 120), mus)))
        alone.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("前面没有相邻片段：转场从剪辑点开始，从下层画面（或黑场）显现。", alone.state.value.inspector!!.transitionNote)
        assertEquals(TransitionSpanUi(0, 15), alone.state.value.timeline.transitionSpans["vB"])
    }

    @Test
    fun `the transition explains when it is cut short by the clip before it, or has no clip before it`() = runTest {
        fun dissolveInto(clip: Clip) = clip.copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15))
        // vA uses 0–3 s of a 3.2 s file: only 6 frames are left for the 15-frame dissolve.
        val short = UiFixtures.video("v1", durationMs = 3_200)
        val (_, model) = editStage(UiFixtures.document(media = listOf(short, song, still), clips = listOf(vA, aA, dissolveInto(vB), mus)))
        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("前一片段出点后只剩 0.2 秒素材，转场缩短为 0.2 秒。", model.state.value.inspector!!.transitionNote)

        val used = UiFixtures.video("v1", durationMs = 3_000)
        val (_, cut) = editStage(UiFixtures.document(media = listOf(used, song, still), clips = listOf(vA, aA, dissolveInto(vB), mus)))
        cut.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("前一片段出点后没有余量素材，转场无法显现它，按硬切播放。", cut.state.value.inspector!!.transitionNote)

        val (_, roomy) = editStage(UiFixtures.document(media = listOf(footage, song, still), clips = listOf(dissolveInto(vA), aA, dissolveInto(vB), mus)))
        roomy.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("17 s of source left: the transition plays as set", null, roomy.state.value.inspector!!.transitionNote)
        roomy.onEvent(EditEvent.SelectClip("vA"))
        assertEquals("前面没有相邻片段，转场从下层画面（或黑场）显现。", roomy.state.value.inspector!!.transitionNote)

        val dip = vB.copy(transitionIn = Transition(TransitionType.DIP_TO_BLACK, 15))
        val (_, dipped) = editStage(UiFixtures.document(media = listOf(used, song, still), clips = listOf(vA, aA, dip, mus)))
        dipped.onEvent(EditEvent.SelectClip("vB"))
        assertEquals(
            "a dip slides to where the clip before it still has picture",
            "前一片段出点后余量不够，转场移向剪辑点前（剪辑点前 0.2 秒、后 0.3 秒）。",
            dipped.state.value.inspector!!.transitionNote,
        )
    }

    @Test
    fun `layout presets place the picture and the clip can be lifted to V2 and back`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        assertEquals("full frame to start with", Placement.FULL.ordinal, model.state.value.inspector!!.placementIndex)
        assertEquals("移到 V2", model.state.value.inspector!!.trackMoveLabel)

        model.onEvent(EditEvent.Place(Placement.BOTTOM_LEFT))
        assertEquals(ClipTransform(scalePercent = 35, positionX = -576, positionY = -303), session.clip("vB").transform)
        assertEquals(Placement.BOTTOM_LEFT.ordinal, model.state.value.inspector!!.placementIndex)

        model.onEvent(EditEvent.MoveTrack(up = true))
        assertEquals(Timeline.V2, session.clip("vB").trackId)
        assertEquals(90, session.clip("vB").start)
        assertEquals("移到 V1", model.state.value.inspector!!.trackMoveLabel)
        assertEquals("vB", session.selectedClipId.value)

        model.onEvent(EditEvent.MoveTrack(up = false))
        assertEquals(Timeline.V1, session.clip("vB").trackId)
    }

    @Test
    fun `the media tab appends the whole asset after the last clip on its track`() = runTest {
        val (session, model) = editStage()

        model.onEvent(EditEvent.SelectLibraryItem("au"))
        model.onEvent(EditEvent.ApplyLibraryItem)

        val added = session.clip("c-1")
        assertEquals(Timeline.A2, added.trackId)
        assertEquals(120 to 240, added.start to added.durationFrames)
        assertEquals("au", added.assetId)
        assertEquals("c-1", session.selectedClipId.value)
        assertEquals("添加到时间线", session.history.value.undoLabel)
        assertEquals("已添加到时间线：au（00:00:08:00）", model.state.value.toast)
        assertEquals(360, model.state.value.timeline.durationFrames)
    }

    @Test
    fun `dragging a clip previews the move and only the drop is recorded`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SetSnapping(false))
        assertFalse(model.state.value.snapping)
        assertEquals("吸附已关闭", model.state.value.toast)

        model.onEvent(EditEvent.Move("vB", start = 200, trackId = Timeline.V1, commit = false))
        assertEquals(200, model.state.value.timeline.clips.first { it.id == "vB" }.start)
        assertEquals(90, session.clip("vB").start)
        assertFalse(session.history.value.canUndo)

        model.onEvent(EditEvent.CancelDrag)
        assertEquals(90, model.state.value.timeline.clips.first { it.id == "vB" }.start)

        model.onEvent(EditEvent.Move("vB", start = 200, trackId = Timeline.V1, commit = true))
        assertEquals(200, session.clip("vB").start)
        assertEquals("移动片段", session.history.value.undoLabel)
        assertEquals("vB", session.selectedClipId.value)

        model.onEvent(EditEvent.Move("mus", start = 0, trackId = Timeline.V1, commit = true))
        assertEquals("不能移动到该轨道", model.state.value.toast)
        assertEquals(Timeline.A2, session.clip("mus").trackId)
    }

    @Test
    fun `subtitles come from the titles library and are typed line by line`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TITLES.ordinal))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.SelectLibraryItem("title:SUBTITLE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        assertEquals("已在 00:00:01:00 添加字幕（新建轨道 ST1）", model.state.value.toast)
        assertEquals("st1", session.document.value!!.timeline.tracks.first().id)
        val first = session.selectedClipId.value!!
        assertTrue(model.state.value.inspector!!.isSubtitle)

        model.onEvent(EditEvent.TitleText("Hello"))
        assertEquals("字幕：Hello", session.clip(first).name)
        model.onEvent(EditEvent.NextSubtitle)
        val second = session.selectedClipId.value!!
        assertEquals(session.clip(first).end, session.clip(second).start)
        assertEquals("the playhead follows to the new line", session.clip(second).start, model.state.value.playheadFrame)
        assertNull("a subtitle has no track move", model.state.value.inspector!!.trackMoveLabel)
    }

    @Test
    fun `the subtitle inspector edits its track's style for every subtitle there`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TITLES.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("title:SUBTITLE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        assertEquals(SubtitleStyle.DEFAULT, model.state.value.inspector!!.subtitleStyle)
        assertEquals("ST1", model.state.value.inspector!!.subtitleTrackName)
        val style = { session.document.value!!.timeline.track("st1")!!.subtitleStyle }

        model.onEvent(EditEvent.SubtitleStyleValue(SubtitleStyleControl.SIZE, 60f))
        model.onEvent(EditEvent.SubtitleStyleValue(SubtitleStyleControl.SIZE, 72.4f))
        assertEquals(72, style().sizePx)
        assertEquals("字幕样式：字号", session.history.value.undoLabel)
        session.undo()
        assertEquals("a drag is one undo step", 44, style().sizePx)
        session.redo()
        model.onEvent(EditEvent.SubtitleStyleColor(SubtitleColor.YELLOW))
        model.onEvent(EditEvent.SubtitleStyleFlag(SubtitleFlag.BOX, false))
        assertEquals(SubtitleColor.YELLOW to false, style().color to style().box)
        assertEquals("字幕样式：背景框", session.history.value.undoLabel)
        assertEquals(style(), model.state.value.inspector!!.subtitleStyle)
        model.onEvent(EditEvent.ResetSubtitleStyle)
        assertEquals(SubtitleStyle.DEFAULT, style())

        model.onEvent(EditEvent.SelectClip("vA"))
        assertNull("other clips have no track style", model.state.value.inspector!!.subtitleStyle)
    }

    @Test
    fun `the title inspector edits the title's own look and says what its entrance does`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.TITLES.ordinal))
        model.onEvent(EditEvent.SelectLibraryItem("title:CENTER"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        val id = session.selectedClipId.value!!
        val inspector = { model.state.value.inspector!! }
        assertEquals(TitleStyle("标题", 96, TitleTemplate.CENTER), inspector().titleStyle)
        assertEquals("开头 0.3 秒渐显，结尾 0.3 秒渐隐", inspector().titleAnimationNote)

        model.onEvent(EditEvent.TitleStyleColor(TitleColor.YELLOW))
        model.onEvent(EditEvent.TitleStyleWeight(TitleWeight.HEAVY))
        model.onEvent(EditEvent.TitleStyleFlag(TitleFlag.OUTLINE, true))
        model.onEvent(EditEvent.TitleStyleFlag(TitleFlag.BOX, true))
        model.onEvent(EditEvent.TitleBoxOpacity(30f))
        model.onEvent(EditEvent.TitleBoxOpacity(70.4f))
        assertEquals("标题样式：背景", session.history.value.undoLabel)
        session.undo()
        assertEquals("a drag is one undo step", 55, session.clip(id).title!!.boxOpacityPercent)
        session.redo()
        model.onEvent(EditEvent.TitleStyleAnimation(TitleAnimation.TYPEWRITER))
        assertEquals(
            TitleStyle("标题", 96, TitleTemplate.CENTER, TitleColor.YELLOW, TitleWeight.HEAVY, outline = true, box = true, boxOpacityPercent = 70, animation = TitleAnimation.TYPEWRITER),
            session.clip(id).title,
        )
        assertEquals(session.clip(id).title, inspector().titleStyle)
        assertEquals("逐字打出，2 个字 0.1 秒打完，结尾 0.3 秒渐隐", inspector().titleAnimationNote)
        assertEquals("标题样式：动画", session.history.value.undoLabel)
        model.onEvent(EditEvent.ResetTitleStyle)
        assertEquals(TitleStyle("标题", 96, TitleTemplate.CENTER), session.clip(id).title)

        model.onEvent(EditEvent.SelectLibraryItem("title:SUBTITLE"))
        model.onEvent(EditEvent.ApplyLibraryItem)
        assertNull("a subtitle's look is its track's", inspector().titleStyle)
        model.onEvent(EditEvent.TitleStyleColor(TitleColor.RED))
        assertEquals("字幕的样式在轨道样式里调整", model.state.value.toast)
    }

    @Test
    fun `a picked SRT file lands on the subtitle track, unreadable files are explained`() = runTest {
        val file = "1\n00:00:01,000 --> 00:00:02,500\n第一句\n\n2\n00:00:03,000 --> 00:00:04,000\n第二句\n"
        val documents = object : DocumentRepository {
            override suspend fun read(uri: String, maxBytes: Int): PickedDocument =
                if (uri == "content://bad") throw DocumentFailure("文件过大：big.srt") else PickedDocument("film.srt", file.toByteArray(Charset.forName("GBK")))
        }
        val session = openSession(project(), clock = clock)
        program.setProject(session.document.value)
        val model = EditViewModel(session, program, source, previews, ids, documents = documents)
        advanceUntilIdle()

        model.onEvent(EditEvent.ImportSubtitles("content://ok"))
        advanceUntilIdle()
        assertEquals("已导入 2 条简体中文字幕（film.srt），新建字幕轨 ST1", model.state.value.toast)
        assertEquals(listOf("第一句", "第二句"), session.document.value!!.timeline.clips.filter { it.trackId == "st1" }.map { it.title!!.text })
        assertEquals("导入字幕", session.history.value.undoLabel)

        model.onEvent(EditEvent.ImportSubtitles("content://bad"))
        advanceUntilIdle()
        assertEquals("文件过大：big.srt", model.state.value.toast)
    }

    @Test
    fun `tracks are added and deleted from the timeline header, as undoable edits`() = runTest {
        val (session, model) = editStage()

        model.onEvent(EditEvent.AddTrack(TrackKind.VIDEO))
        assertEquals("已添加轨道 V3", model.state.value.toast)
        assertEquals(listOf("v3", "v2", "v1", "a1", "a2"), session.document.value!!.timeline.tracks.map { it.id })
        model.onEvent(EditEvent.AddTrack(TrackKind.AUDIO))
        assertTrue("room for eight of each (rev 158)", model.state.value.canAddVideoTrack)
        repeat(5) { model.onEvent(EditEvent.AddTrack(TrackKind.VIDEO)); model.onEvent(EditEvent.AddTrack(TrackKind.AUDIO)) }
        assertFalse(model.state.value.canAddVideoTrack)
        assertFalse(model.state.value.canAddAudioTrack)
        assertEquals(16, model.state.value.timeline.tracks.size)
        model.onEvent(EditEvent.AddTrack(TrackKind.VIDEO))
        assertEquals("最多 8 条视频轨道", model.state.value.toast)
        repeat(10) { session.undo() }
        assertEquals(6, session.document.value!!.timeline.tracks.size)

        model.onEvent(EditEvent.SelectClip("mus"))
        model.onEvent(EditEvent.RemoveTrack(Timeline.A2))
        assertEquals("已删除轨道 A2 及其中 1 个片段", model.state.value.toast)
        assertNull("the selection went with its track", session.selectedClipId.value)
        assertEquals("删除轨道", session.history.value.undoLabel)
        session.undo()
        assertEquals(Timeline.A2, session.clip("mus").trackId)

        model.onEvent(EditEvent.RemoveTrack(Timeline.V1))
        assertEquals("V1 是基础轨道，不能删除", model.state.value.toast)
    }

    @Test
    fun `keyframes are added at the playhead and the inspector follows the animation`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(10))

        model.onEvent(EditEvent.ToggleKeyframe)
        assertEquals(listOf(10), session.clip("vA").keyframes.map { it.frame })
        assertEquals("已添加关键帧 · 共 1 个", model.state.value.toast)
        assertTrue(model.state.value.inspector!!.transformKeys.atKeyframe)
        assertEquals("有关键帧的数值调整后记在此处 · 可设缓入缓出", model.state.value.inspector!!.transformKeys.hint)

        // With keyframes, a slider change at another frame becomes a second keyframe.
        model.onEvent(EditEvent.Seek(70))
        model.onEvent(EditEvent.Scale(200f))
        assertEquals(listOf(10 to 100, 70 to 200), session.clip("vA").keyframes.map { it.frame to it.transform.scalePercent })
        assertEquals(2, model.state.value.inspector!!.transformKeys.count)

        model.onEvent(EditEvent.Seek(40))
        assertEquals("halfway between the keyframes", 150, model.state.value.inspector!!.scale)
        assertFalse(model.state.value.inspector!!.transformKeys.atKeyframe)
        assertTrue(model.state.value.inspector!!.transformKeys.hasPrevious)
        assertTrue(model.state.value.inspector!!.transformKeys.hasNext)

        model.onEvent(EditEvent.NextKeyframe)
        assertEquals(70, model.state.value.playheadFrame)
        model.onEvent(EditEvent.PreviousKeyframe)
        assertEquals(10, model.state.value.playheadFrame)
    }

    @Test
    fun `a parameter's own keyframe button keys it alone, and the other buttons show how theirs stand`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(10))
        assertEquals(ParamKeyState.NONE, model.state.value.inspector!!.paramKeys[TransformKeyGroup.SCALE])

        model.onEvent(EditEvent.ToggleParamKey(TransformKeyGroup.SCALE))
        assertEquals("已为缩放添加关键帧 · 共 1 个", model.state.value.toast)
        assertEquals(setOf(CurveParam.SCALE), session.clip("vA").keyframes.single().params)
        assertEquals(ParamKeyState.AT_KEY, model.state.value.inspector!!.paramKeys[TransformKeyGroup.SCALE])
        assertEquals(ParamKeyState.NONE, model.state.value.inspector!!.paramKeys[TransformKeyGroup.POSITION])

        // Scale is animated: changing it at frame 70 keys it there. Position is not: it moves everywhere, keying nothing.
        model.onEvent(EditEvent.Seek(70))
        assertEquals(ParamKeyState.ANIMATED, model.state.value.inspector!!.paramKeys[TransformKeyGroup.SCALE])
        model.onEvent(EditEvent.Scale(200f))
        model.onEvent(EditEvent.PositionX(300f))
        assertEquals(listOf(10, 70), session.clip("vA").keyframes.track(CurveParam.SCALE).map { it.frame })
        assertTrue(session.clip("vA").keyframes.track(CurveParam.POSITION_X).isEmpty())
        model.onEvent(EditEvent.Seek(10))
        assertEquals("position changed outright", 300, model.state.value.inspector!!.positionX)
        assertEquals(100, model.state.value.inspector!!.scale)

        // Undo takes back one button press at a time.
        model.onEvent(EditEvent.Seek(70))
        model.onEvent(EditEvent.ToggleParamKey(TransformKeyGroup.SCALE))
        assertEquals("已删除缩放的关键帧 · 还剩 1 个", model.state.value.toast)
        session.undo()
        assertEquals(listOf(10, 70), session.clip("vA").keyframes.track(CurveParam.SCALE).map { it.frame })
    }

    @Test
    fun `the tracker keys a mask along the subject it covers, one undo step`() = runTest {
        // 90 frames of a bright disc drifting right 1 px a frame (and down 0.5) over a textured background, 320 × 180.
        val w = 320
        val h = 180
        val r = kotlin.random.Random(9)
        val back = FloatArray(w * h) { 50f + 40f * r.nextFloat() }
        val subject = { i: Int -> (80.0 + i) to (70.0 + i * 0.5) }
        val frames = LumaFrames(w, h, 30.0, 0, List(90) { i ->
            val (sx, sy) = subject(i)
            ByteArray(w * h) { k ->
                val x = k % w - sx
                val y = k / w - sy
                (back[k] + 190 * kotlin.math.exp(-(x * x + y * y) / 128.0) * (0.8 + 0.2 * kotlin.math.sin(x) * kotlin.math.cos(y))).coerceIn(0.0, 255.0).toInt().toByte()
            }
        })
        val source = FakeLumaFrameRepository(frames)
        val (session, model) = editStage(lumaFrames = source)
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(0))
        assertEquals("先为片段选择遮罩形状", model.state.value.inspector!!.maskTrack.unavailable)
        model.onEvent(EditEvent.TrackMask)
        assertEquals("先为片段选择遮罩形状", model.state.value.toast)

        // A circle mask on the disc at frame 0.
        model.onEvent(EditEvent.MaskShape(WindowShape.CIRCLE))
        val (x0, y0) = subject(0)
        model.onEvent(EditEvent.MaskControl(WindowControl.CENTER_X, (x0 / (w - 1) * 2 - 1).toFloat()))
        model.onEvent(EditEvent.MaskControl(WindowControl.CENTER_Y, (1 - y0 / (h - 1) * 2).toFloat()))
        model.onEvent(EditEvent.MaskControl(WindowControl.SIZE, 0.15f))
        assertNull(model.state.value.inspector!!.maskTrack.unavailable)
        model.onEvent(EditEvent.TrackMask)
        advanceUntilIdle()
        assertEquals("the clip's stretch of its file", listOf(0L to 3000L), source.asked)
        val mask = session.clip("vA").mask!!
        assertEquals("跟踪遮罩", session.history.value.undoLabel)
        assertTrue("${mask.keyframes.size} keyframes", mask.keyframes.size in 2..20)
        assertTrue(model.state.value.toast!!.startsWith("已跟踪遮罩：${mask.keyframes.size} 个关键帧"))
        assertFalse("the run is over", model.state.value.inspector!!.maskTrack.tracking)
        val (x89, y89) = subject(89)
        val last = mask.at(89)
        assertEquals((x89 / (w - 1) * 2 - 1).toFloat(), last.centerX, 0.012f)
        assertEquals((1 - y89 / (h - 1) * 2).toFloat(), last.centerY, 0.012f)
        assertEquals("its size is kept", 0.15f, last.size, 1e-6f)
        session.undo()
        assertTrue("one undo step takes the track away", session.clip("vA").mask!!.keyframes.isEmpty())

        // A still's mask has nothing to follow.
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.MaskShape(WindowShape.CIRCLE))
        assertEquals("只有视频片段可以跟踪遮罩", model.state.value.inspector!!.maskTrack.unavailable)
    }

    @Test
    fun `the mask tracker's Zoom and Rotate chips show and reach the tracker`() = runTest {
        // A textured blob in the middle of the picture growing to 1.3 times its size over 60 frames.
        val w = 320
        val h = 180
        val r = kotlin.random.Random(4)
        val back = FloatArray(w * h) { 50f + 40f * r.nextFloat() }
        val frames = LumaFrames(w, h, 30.0, 0, List(60) { i ->
            val s = 1 + 0.3 * i / 59
            ByteArray(w * h) { k ->
                val x = (k % w - 160) / s
                val y = (k / w - 90) / s
                val v = if (x * x + y * y > 30.0 * 30.0) back[k].toDouble() else 130 + 50 * kotlin.math.sin(x / 3.0) + 40 * kotlin.math.cos((x + 2 * y) / 5.0) + (if (x > 3 && y < -3) 40 else 0)
                v.coerceIn(0.0, 255.0).toInt().toByte()
            }
        })
        val (session, model) = editStage(lumaFrames = FakeLumaFrameRepository(frames))
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(0))
        model.onEvent(EditEvent.MaskShape(WindowShape.CIRCLE))
        model.onEvent(EditEvent.MaskControl(WindowControl.CENTER_X, (160.0 / (w - 1) * 2 - 1).toFloat()))
        model.onEvent(EditEvent.MaskControl(WindowControl.CENTER_Y, (1 - 90.0 / (h - 1) * 2).toFloat()))
        model.onEvent(EditEvent.MaskControl(WindowControl.SIZE, 0.25f))
        // Both on at first, as Resolve's are: the mask grows with the blob.
        assertEquals(TrackMotion.ALL, model.state.value.inspector!!.maskTrack.motion)
        model.onEvent(EditEvent.TrackMask)
        advanceUntilIdle()
        assertEquals(0.25f * 1.3f, session.clip("vA").mask!!.at(59).size, 0.25f * 0.04f)
        assertTrue(model.state.value.toast!!, model.state.value.toast!!.contains("，大小最多变化"))

        // Both off (that track undone): the position alone (the blob stays put), the size as it was keyed — not at all.
        session.undo()
        model.onEvent(EditEvent.SetMaskTrackMotion(TrackMotion()))
        assertEquals(TrackMotion(), model.state.value.inspector!!.maskTrack.motion)
        model.onEvent(EditEvent.TrackMask)
        advanceUntilIdle()
        assertEquals(0.25f, session.clip("vA").mask!!.at(59).size, 1e-6f)
        assertFalse(model.state.value.toast!!.contains("大小"))
    }

    @Test
    fun `a mask is picked, drawn on the monitor, dragged there and keyed at the playhead`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(10))
        assertNull(model.state.value.inspector!!.mask)
        assertNull(model.state.value.inspector!!.maskOverlay)

        model.onEvent(EditEvent.MaskShape(WindowShape.CIRCLE))
        assertEquals("已添加圆形遮罩：形状之外露出下层轨道", model.state.value.toast)
        assertEquals(WindowShape.CIRCLE, session.clip("vA").mask!!.shape)
        val overlay = model.state.value.inspector!!.maskOverlay!!
        assertFalse("the mask is never mirrored", overlay.mirrored)
        assertEquals(session.clip("vA").mask, overlay.window)

        // One drag (several moves) is one undo step; a still mask moves throughout.
        model.onEvent(EditEvent.MoveMask(0.1f, 0f))
        model.onEvent(EditEvent.MoveMask(0.1f, 0.05f))
        assertEquals(0.2f, session.clip("vA").mask!!.centerX, 1e-6f)
        assertEquals(0.05f, session.clip("vA").mask!!.centerY, 1e-6f)
        session.undo()
        assertEquals(0f, session.clip("vA").mask!!.centerX, 1e-6f)

        // Keyed at frame 10, a slider at frame 50 adds a second keyframe there.
        model.onEvent(EditEvent.ToggleMaskKey)
        assertEquals("已添加遮罩关键帧 · 共 1 个", model.state.value.toast)
        assertEquals(ParamKeyState.AT_KEY, model.state.value.inspector!!.maskKey)
        model.onEvent(EditEvent.Seek(50))
        assertEquals(ParamKeyState.ANIMATED, model.state.value.inspector!!.maskKey)
        model.onEvent(EditEvent.MaskControl(WindowControl.SIZE, 1.2f))
        assertEquals(listOf(10, 50), session.clip("vA").mask!!.keyframes.map { it.frame })
        assertEquals("2 个关键帧 · 拖动或调整会记在播放头处", model.state.value.inspector!!.maskHint)
        model.onEvent(EditEvent.Seek(30))
        assertEquals("halfway", (0.6f + 1.2f) / 2, model.state.value.inspector!!.mask!!.size, 1e-4f)

        // 反转 shows the outside; 无 removes the mask and its overlay.
        model.onEvent(EditEvent.MaskInvert(true))
        assertTrue(session.clip("vA").mask!!.invert)
        model.onEvent(EditEvent.MaskShape(null))
        assertEquals("已移除遮罩", model.state.value.toast)
        assertNull(session.clip("vA").mask)
        assertNull(model.state.value.inspector!!.maskOverlay)
    }

    @Test
    fun `the ease chips follow the keyframe at the playhead and switch its easing`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(10))
        model.onEvent(EditEvent.ToggleKeyframe)
        var keys = model.state.value.inspector!!.transformKeys
        assertFalse("nothing arrives at or leaves a lone keyframe", keys.canEaseIn || keys.canEaseOut)

        model.onEvent(EditEvent.Seek(70))
        model.onEvent(EditEvent.Scale(200f))
        keys = model.state.value.inspector!!.transformKeys
        assertTrue(keys.canEaseIn)
        assertFalse("the last keyframe has nothing to ease out to", keys.canEaseOut)

        model.onEvent(EditEvent.ToggleEase(arriving = true))
        assertEquals("已开启缓入 · 到达此关键帧时逐渐减速", model.state.value.toast)
        assertTrue(model.state.value.inspector!!.transformKeys.easeIn)
        assertTrue(session.clip("vA").keyframes.last().ease.easesIn)
        assertEquals("缓入", session.history.value.undoLabel)
        assertEquals("vA", session.selectedClipId.value)

        model.onEvent(EditEvent.Seek(40))
        assertEquals("ahead of the even pace, slowing into frame 70", 163, model.state.value.inspector!!.scale)
        assertFalse("between keyframes there is nothing to ease", model.state.value.inspector!!.transformKeys.canEaseIn)

        model.onEvent(EditEvent.ToggleEase(arriving = true))
        assertEquals("把播放头移到关键帧上，再设置缓入缓出", model.state.value.toast)
    }

    @Test
    fun `volume easing goes on the linked sound and keeps the picture selected`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(0))
        model.onEvent(EditEvent.ToggleVolumeKeyframe)
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.Volume(-20f))
        model.onEvent(EditEvent.Seek(0))

        model.onEvent(EditEvent.ToggleVolumeEase(arriving = false))

        assertTrue(session.clip("aA").volumeKeyframes.first().ease.easesOut)
        assertEquals("vA", session.selectedClipId.value)
        assertTrue(model.state.value.inspector!!.volumeKeys.easeOut)
        assertEquals("音量缓出", session.history.value.undoLabel)
        assertEquals("音量变化：调整会记录在此关键帧 · 可设缓入缓出", model.state.value.inspector!!.volumeKeys.hint)
    }

    @Test
    fun `an animated clip cannot be adjusted while the playhead is outside it`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(20))
        model.onEvent(EditEvent.ToggleKeyframe)

        model.onEvent(EditEvent.Seek(120))
        val inspector = model.state.value.inspector!!
        assertFalse(inspector.transformKeys.playheadInside)
        assertTrue(inspector.transformKeys.blocked)
        assertEquals("播放头不在片段内：移进片段再调整动画", inspector.transformKeys.hint)

        model.onEvent(EditEvent.Scale(300f))
        assertEquals("把播放头移到片段内，再调整关键帧", model.state.value.toast)
        assertEquals(listOf(20 to 100), session.clip("vA").keyframes.map { it.frame to it.transform.scalePercent })
    }

    @Test
    fun `volume keyframes go on the linked sound, keep the picture selected and follow the playhead`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(0))

        model.onEvent(EditEvent.ToggleVolumeKeyframe)
        assertEquals(listOf(0 to 0f), session.clip("aA").volumeKeyframes.map { it.frame to it.volumeDb })
        assertEquals("vA", session.selectedClipId.value)
        assertEquals("已添加音量关键帧 · 共 1 个", model.state.value.toast)

        // Ducking: a keyed level at another frame becomes a second keyframe.
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.Volume(-20f))
        assertEquals(listOf(0 to 0f, 60 to -20f), session.clip("aA").volumeKeyframes.map { it.frame to it.volumeDb })

        model.onEvent(EditEvent.Seek(30))
        val inspector = model.state.value.inspector!!
        assertEquals(-10f, inspector.volumeDb, 0.01f)
        assertEquals(2, inspector.volumeKeys.count)
        assertTrue(inspector.volumeKeys.hasPrevious)
        assertTrue(inspector.volumeKeys.hasNext)
        assertEquals("音量变化：调整会记录在播放头处的关键帧", inspector.volumeKeys.hint)

        model.onEvent(EditEvent.NextVolumeKeyframe)
        assertEquals(60, model.state.value.playheadFrame)
        model.onEvent(EditEvent.Seek(120))
        assertTrue("the sound clip ends at 90", model.state.value.inspector!!.volumeKeys.blocked)
    }

    @Test
    fun `in slip mode the dial slides the source under the clip, as one undo step`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.JogMode(EditUiState.JOG_SLIP))
        assertEquals("入点 00:00:00:00", model.state.value.jogReadout)

        model.onEvent(EditEvent.TrimJog(30))
        model.onEvent(EditEvent.TrimJog(15))

        assertEquals(1_500, session.clip("vA").sourceInMs)
        assertEquals("the linked sound slips too", 1_500, session.clip("aA").sourceInMs)
        assertEquals(0, session.clip("vA").start)
        assertEquals("入点 00:00:01:15", model.state.value.jogReadout)
        assertEquals("滑移", session.history.value.undoLabel)
        session.undo()
        assertEquals("both steps undo together", 0, session.clip("vA").sourceInMs)
    }

    @Test
    fun `in roll mode the dial moves the cut after the clip, and a limit is said once`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.JogMode(EditUiState.JOG_ROLL))
        assertEquals("剪辑点 00:00:03:00", model.state.value.jogReadout)

        model.onEvent(EditEvent.TrimJog(10))
        assertEquals(100, session.clip("vA").end)
        assertEquals(listOf(100, 50), session.clip("vB").let { listOf(it.start, it.durationFrames) })
        assertEquals("剪辑点 00:00:03:10", model.state.value.jogReadout)

        model.onEvent(EditEvent.JogMode(EditUiState.JOG_SLIP))
        model.onEvent(EditEvent.TrimJog(-5))
        assertEquals("素材前面没有更多内容", model.state.value.toast)
        model.onEvent(EditEvent.ToastShown)
        model.onEvent(EditEvent.TrimJog(-5))
        assertNull("the same refusal stays quiet for a moment", model.state.value.toast)
        clock.now += 3_000
        model.onEvent(EditEvent.TrimJog(-5))
        assertEquals("素材前面没有更多内容", model.state.value.toast)
    }

    @Test
    fun `in slide mode the dial moves the clip between its neighbours, whose linked sound follows`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.JogMode(EditUiState.JOG_SLIDE))
        assertEquals("起点 00:00:03:00", model.state.value.jogReadout)

        model.onEvent(EditEvent.TrimJog(-6))
        model.onEvent(EditEvent.TrimJog(-4))
        assertEquals(listOf(80, 140), session.clip("vB").let { listOf(it.start, it.end) })
        assertEquals("the clip before gives way", 80, session.clip("vA").end)
        assertEquals("and its linked sound with it", 80, session.clip("aA").end)
        assertEquals("起点 00:00:02:20", model.state.value.jogReadout)
        assertEquals("滑动片段", session.history.value.undoLabel)
        session.undo()
        assertEquals("both steps undo together", 90, session.clip("vB").start)
    }

    @Test
    fun `the clip menu's colours label the selected clips, and a colour selects all of its clips`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SetClipColor(ClipColor.ORANGE))
        assertEquals("请先在时间线上选择片段", model.state.value.toast)
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.SetClipColor(ClipColor.ORANGE))
        assertEquals(ClipColor.ORANGE to ClipColor.ORANGE, session.clip("vA").color to session.clip("aA").color)
        assertEquals("片段颜色", session.history.value.undoLabel)
        assertEquals("vA", session.selectedClipId.value)
        model.onEvent(EditEvent.SelectClip("mus"))
        model.onEvent(EditEvent.SetClipColor(ClipColor.ORANGE))
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.SelectClipColor(ClipColor.ORANGE))
        assertEquals(listOf("vA", "mus"), session.selectedClipIds.value)
        assertEquals("已选择 2 个橙色片段", model.state.value.toast)
        model.onEvent(EditEvent.SetClipColor(null))
        assertEquals("both are cleared together", null to null, session.clip("vA").color to session.clip("mus").color)
        assertEquals("清除片段颜色", session.history.value.undoLabel)
    }

    @Test
    fun `a nudge moves the selected clip a few frames without snapping`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("mus"))

        model.onEvent(EditEvent.Nudge(1))
        model.onEvent(EditEvent.Nudge(5))

        assertEquals(6, session.clip("mus").start)
        assertEquals("微移片段", session.history.value.undoLabel)
    }

    @Test
    fun `seeking stays on the timeline and drives the program monitor`() = runTest {
        val (_, model) = editStage()

        model.onEvent(EditEvent.Seek(9_999))
        assertEquals(150, model.state.value.playheadFrame)
        assertEquals(5_000L, program.state.value.positionMs)

        model.onEvent(EditEvent.Seek(-5))
        assertEquals(0, model.state.value.playheadFrame)
        assertEquals(0L, program.state.value.positionMs)

        program.play()
        assertTrue(model.state.value.playing)
    }

    @Test
    fun `the marker index adds, picks, edits, steps between and deletes markers`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.MARKERS.ordinal))
        assertTrue("the add button needs no item", model.state.value.libraryActionEnabled)
        assertEquals("在播放头添加标记", model.state.value.libraryActionLabel)
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.ApplyLibraryItem)
        model.onEvent(EditEvent.Seek(120))
        model.onEvent(EditEvent.AddMarker)
        val s = model.state.value
        assertEquals(listOf("00:00:02:00", "00:00:04:00"), s.markers.map { it.timecode })
        assertEquals("the new one is picked", s.markers[1].id, s.selectedMarkerId)
        // Edit the picked one: typing collapses into one step per field.
        model.onEvent(EditEvent.RenameMarker("高"))
        model.onEvent(EditEvent.RenameMarker("高潮"))
        model.onEvent(EditEvent.RecolorMarker(MarkerColor.RED))
        model.onEvent(EditEvent.NoteMarker("鼓点进入"))
        val edited = session.document.value!!.timeline.markers.single { it.frame == 120 }
        assertEquals(Triple("高潮", MarkerColor.RED, "鼓点进入"), Triple(edited.label, edited.color, edited.note))
        assertEquals("标记备注", session.history.value.undoLabel)
        // Shift+↑ / ↓ step between markers and pick them.
        model.onEvent(EditEvent.PreviousMarker)
        assertEquals(60, model.state.value.playheadFrame)
        assertEquals(s.markers[0].id, model.state.value.selectedMarkerId)
        model.onEvent(EditEvent.NextMarker)
        assertEquals(120, model.state.value.playheadFrame)
        assertEquals(s.markers[1].id, model.state.value.timeline.selectedMarkerId)
        model.onEvent(EditEvent.DeleteMarker)
        assertEquals(listOf(60), session.document.value!!.timeline.markers.map { it.frame })
        assertEquals("已删除“高潮”", model.state.value.toast)
        assertNull(model.state.value.selectedMarkerId)
    }

    @Test
    fun `markers drag along the ruler - the flag moves, a duration's end stretches - shown live, one step when let go`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.AddMarker)
        model.onEvent(EditEvent.Seek(90))
        model.onEvent(EditEvent.AddMarker)
        val markers = { session.document.value!!.timeline.markers }
        val first = markers().first { it.frame == 30 }.id
        model.onEvent(EditEvent.MarkerDrag(first, 45, end = false, commit = false))
        assertEquals("not yet in the project", 30, markers().first { it.id == first }.frame)
        assertEquals("but on the ruler", 45, model.state.value.timeline.markers.first { it.id == first }.frame)
        model.onEvent(EditEvent.MarkerDrag(first, 50, end = false, commit = true))
        assertEquals(50, markers().first { it.id == first }.frame)
        assertEquals("移动标记", session.history.value.undoLabel)
        assertEquals("picked in the index", first, model.state.value.selectedMarkerId)
        // The end of its duration: the slider gave it 1 s, the drag takes it to frame 110.
        model.onEvent(EditEvent.MarkerDuration(1f))
        model.onEvent(EditEvent.MarkerDrag(first, 110, end = true, commit = true))
        assertEquals(60, markers().first { it.id == first }.durationFrames)
        assertEquals("标记时长", session.history.value.undoLabel)
        // Onto the other marker's frame: refused, nothing moves.
        model.onEvent(EditEvent.MarkerDrag(first, 90, end = false, commit = true))
        assertEquals("那里已有另一个标记", model.state.value.toast)
        assertEquals(50, markers().first { it.id == first }.frame)
        session.undo()
        assertEquals("undo takes back the end's drag, to the slider's 1 s", 30, markers().first { it.id == first }.durationFrames)
    }

    @Test
    fun `a duration marker takes its range from the slider or from In and Out, and hands it to In and Out`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryTab(LibraryTab.MARKERS.ordinal))
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.AddMarker)
        assertEquals("一点", model.state.value.markers.single().rangeText)
        assertFalse("no In / Out yet", model.state.value.markers.single().canTakeMarks)
        model.onEvent(EditEvent.MarkerDuration(1.5f))
        model.onEvent(EditEvent.MarkerDuration(1.02f))
        val marker = session.document.value!!.timeline.markers.single()
        assertEquals("whole frames", 31, marker.durationFrames)
        assertEquals("1.0 秒 · 到 00:00:03:01", model.state.value.markers.single().rangeText)
        assertEquals("标记时长", session.history.value.undoLabel)
        val end = session.document.value!!.durationFrames
        model.onEvent(EditEvent.MarkerDuration(999f))
        assertEquals("held to the timeline's end", end - 60, session.document.value!!.timeline.markers.single().durationFrames)
        session.undo()
        assertEquals("the slider's drag is one step", 0, session.document.value!!.timeline.markers.single().durationFrames)
        // In and Out make a range for it, and it hands its range back to them.
        session.edit("标记") { d -> d.copy(timeline = d.timeline.copy(marks = TimelineMarks(30, 75))) }
        assertTrue(model.state.value.markers.single().canTakeMarks)
        model.onEvent(EditEvent.MarkerRangeFromMarks)
        val ranged = session.document.value!!.timeline.markers.single()
        assertEquals(30 to 45, ranged.frame to ranged.durationFrames)
        session.edit("清除") { d -> d.copy(timeline = d.timeline.copy(marks = TimelineMarks())) }
        model.onEvent(EditEvent.MarksFromMarkerRange)
        assertEquals(TimelineMarks(30, 75), session.document.value!!.timeline.marks)
        assertEquals("入出点：用标记范围", session.history.value.undoLabel)
        assertEquals("the playhead goes to the In", 30, model.state.value.playheadFrame)
    }

    @Test
    fun `a speed between the presets shows in the readout with no preset chip on`() = runTest {
        val (session, model) = editStage()
        session.edit("适配填充") { d -> d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("vA")!!.copy(speed = ClipSpeed(4f / 3f)))) }
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(-1, model.state.value.inspector?.speedIndex)
        assertEquals("1.33×", model.state.value.inspector?.speedLabel)
        model.onEvent(EditEvent.Speed(PlaybackSpeed.NORMAL.ordinal))
        assertEquals(PlaybackSpeed.NORMAL.ordinal, model.state.value.inspector?.speedIndex)
        assertEquals("1×", model.state.value.inspector?.speedLabel)
    }

    @Test
    fun `timeline in and out are marked at the playhead, shown and cleared, outside the history`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.MarkIn)
        model.onEvent(EditEvent.Seek(120))
        model.onEvent(EditEvent.MarkOut)
        val s = model.state.value
        assertEquals(TimelineMarks(30, 120), s.timeline.marks)
        assertTrue(s.hasMarks)
        assertEquals("while marked, the header names the range", "入点 00:00:01:00 → 出点 00:00:04:00（00:00:03:00）", s.summary)
        assertEquals("已标记时间线出点 00:00:04:00", s.toast)
        assertFalse("not an undo step", session.history.value.canUndo)
        model.onEvent(EditEvent.ClearMarks)
        assertEquals(TimelineMarks(), model.state.value.timeline.marks)
        assertFalse(model.state.value.hasMarks)
        model.onEvent(EditEvent.ClearMarks)
        assertEquals("时间线上没有入出点", model.state.value.toast)
    }

    @Test
    fun `with no clip selected the delete keys lift or extract the In-Out range, and the header buttons always do`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.DeleteClip)
        assertEquals("请先在时间线上选择片段，或标记入点和出点", model.state.value.toast)
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.MarkIn)
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.MarkOut)
        assertTrue(model.state.value.hasRange)
        model.onEvent(EditEvent.Seek(100))
        val spans = { track: String -> session.document.value!!.timeline.clips.filter { it.trackId == track }.sortedBy { it.start }.map { it.start to it.end } }

        // Backspace: lifted on V1, A1 and A2; the marks stay round the gap; the playhead goes to the In.
        model.onEvent(EditEvent.DeleteClip)
        assertEquals(listOf(0 to 30, 60 to 90, 90 to 150), spans(Timeline.V1))
        assertEquals(listOf(0 to 30, 60 to 90), spans(Timeline.A1))
        assertEquals(listOf(0 to 30, 60 to 120), spans(Timeline.A2))
        assertEquals("删除入点至出点", session.history.value.undoLabel)
        assertEquals(TimelineMarks(30, 60), model.state.value.timeline.marks)
        assertEquals(30, model.state.value.playheadFrame)
        session.undo()

        // Delete: extracted; the marks go.
        model.onEvent(EditEvent.RippleDelete)
        assertEquals(listOf(0 to 30, 30 to 60, 60 to 120), spans(Timeline.V1))
        assertEquals(listOf(0 to 30, 30 to 90), spans(Timeline.A2))
        assertEquals("波纹删除入点至出点", session.history.value.undoLabel)
        assertFalse(model.state.value.hasRange)
        assertTrue(model.state.value.toast!!.startsWith("已波纹删除 入点 00:00:01:00 至出点 00:00:02:00"))
        session.undo()

        // Marks are outside the history: undo brings the clips back, not the marks the extract used up.
        assertEquals(TimelineMarks(), session.document.value!!.timeline.marks)
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.MarkIn)
        model.onEvent(EditEvent.Seek(60))
        model.onEvent(EditEvent.MarkOut)
        // A selected clip wins over the range for the keys; the header buttons act on the range regardless.
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.DeleteClip)
        assertNull(session.document.value!!.timeline.clip("vB"))
        session.undo()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.ExtractRange)
        assertEquals(listOf(0 to 30, 30 to 60, 60 to 120), spans(Timeline.V1))
    }

    @Test
    fun `the track headers patch the destinations, and a replace from the source view lines up its playhead`() = runTest {
        val (session, model) = editStage()
        assertEquals(EditTargets(), model.state.value.timeline.targets)
        model.onEvent(EditEvent.ToggleTarget(Timeline.A2))
        assertEquals(EditTargets(audio = Timeline.A2), model.state.value.timeline.targets)
        assertEquals("声音将落在 A2", model.state.value.toast)
        model.onEvent(EditEvent.ToggleTarget(Timeline.A2))
        assertEquals("a second tap: automatic again", EditTargets(), model.state.value.timeline.targets)
        model.onEvent(EditEvent.ToggleTarget(Timeline.V2))
        assertEquals(Timeline.V2, session.document.value!!.timeline.targets.video)
        assertFalse("not an undo step", session.history.value.canUndo)
        model.onEvent(EditEvent.ToggleTarget(Timeline.V1))

        // vA (0–90) takes the footage held at 6 s in the source view, lined up with the playhead's 1 s.
        model.onEvent(EditEvent.SelectLibraryItem("v1"))
        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_SOURCE))
        model.onEvent(EditEvent.SourceJog(180))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.EditFromSource(SourceEdit.REPLACE))
        val clips = session.document.value!!.timeline.clips
        assertEquals(listOf(5_000L, 5_000L), clips.filter { it.id in setOf("vA", "aA") }.map { it.sourceInMs })
        assertEquals("替换片段", session.history.value.undoLabel)
        assertTrue(model.state.value.toast!!.startsWith("已用“v1”替换 V1 上的"))
        assertEquals("the playhead stays", 30, model.state.value.playheadFrame)
        // From the timeline view the media's start goes to the clip's.
        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_TIMELINE))
        model.onEvent(EditEvent.EditFromSource(SourceEdit.REPLACE))
        assertEquals(0L, session.document.value!!.timeline.clip("vA")!!.sourceInMs)
    }

    @Test
    fun `the source viewer pauses the program and shows the media of the selection`() = runTest {
        val (_, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vA"))
        program.play()

        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_SOURCE))

        assertFalse(program.state.value.playing)
        assertEquals(footage, source.asset)
        assertEquals(EditUiState.VIEWER_SOURCE, model.state.value.viewerMode)
        assertEquals("v1.mp4", model.state.value.sourceName)
        assertTrue(model.state.value.sourceIsVideo)

        model.onEvent(EditEvent.SelectLibraryItem("im"))
        assertEquals(still, source.asset)
        assertFalse(model.state.value.sourceIsVideo)
        assertEquals(4f / 3f, model.state.value.sourceAspect, 1e-4f)

        model.onEvent(EditEvent.SourcePlaying(true))
        assertTrue(model.state.value.sourcePlaying)
        model.onEvent(EditEvent.SourceJog(30))
        assertFalse(source.state.value.playing)
        assertEquals(1_000L, source.state.value.positionMs)
        assertEquals("00:00:01:00", model.state.value.programTimecode)
    }

    @Test
    fun `an offline clip under the playhead is reported on the program monitor`() = runTest {
        val (_, model) = editStage(project(media = listOf(footage.copy(offline = true), song, still)))

        assertEquals("素材离线：原文件已被移动或删除", model.state.value.programMessage)
        assertEquals(setOf("v1"), model.state.value.timeline.offlineAssetIds)
        assertEquals("素材离线", model.state.value.libraryItems.first { it.id == "v1" }.subtitle)

        model.onEvent(EditEvent.Seek(100))
        assertNull("the still under the playhead is online", model.state.value.programMessage)
    }

    @Test
    fun `copy and paste put the clip with its sound at the playhead, and the playhead moves past it`() = runTest {
        val (session, model) = editStage()
        assertFalse(model.state.value.canPaste)
        model.onEvent(EditEvent.PasteClips)
        assertEquals("剪贴板是空的", model.state.value.toast)
        model.onEvent(EditEvent.CopyClip)
        assertEquals("请先在时间线上选择片段", model.state.value.toast)

        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.CopyClip)
        assertTrue(model.state.value.canPaste)
        assertEquals("已复制“vA”及链接的片段", model.state.value.toast)
        assertFalse("copying changes nothing", session.history.value.canUndo)

        model.onEvent(EditEvent.Seek(150))
        model.onEvent(EditEvent.PasteClips)
        val pasted = session.document.value!!.timeline.clips.filter { it.start == 150 }
        assertEquals(listOf(Timeline.V1, Timeline.A1), pasted.map { it.trackId })
        assertEquals("the copies are linked to each other, not to the original", 1, pasted.map { it.linkId }.distinct().size)
        assertTrue(pasted.first().linkId != "L1")
        assertEquals(pasted.first().id, session.selectedClipId.value)
        assertEquals("粘贴片段", session.history.value.undoLabel)
        assertEquals("已粘贴 2 个片段到 00:00:05:00", model.state.value.toast)
        assertEquals("the playhead moves past the paste", 240, model.state.value.playheadFrame)

        model.onEvent(EditEvent.PasteClips)
        val onV1 = session.document.value!!.timeline.clips.filter { it.trackId == Timeline.V1 }
        assertEquals("pastes follow one another", listOf(0, 90, 150, 240), onV1.map { it.start }.sorted())
    }

    @Test
    fun `D switches the selected clip off and on, and paste attributes pastes the chosen ones`() = runTest {
        val (session, model) = editStage()
        val clip = { id: String -> session.document.value!!.timeline.clip(id)!! }
        model.onEvent(EditEvent.ToggleClipEnabled)
        assertEquals("请先在时间线上选择片段", model.state.value.toast)
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.ToggleClipEnabled)
        assertEquals(false to false, clip("vA").enabled to clip("aA").enabled)
        assertEquals("停用片段", session.history.value.undoLabel)
        assertFalse(model.state.value.inspector!!.enabled)
        model.onEvent(EditEvent.ToggleClipEnabled)
        assertTrue(clip("vA").enabled)
        assertEquals("启用片段", session.history.value.undoLabel)

        model.onEvent(EditEvent.OpenPasteAttributes)
        assertEquals("剪贴板是空的：先复制一个片段", model.state.value.toast)
        model.onEvent(EditEvent.Place(Placement.TOP_RIGHT))
        model.onEvent(EditEvent.CopyClip)
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.OpenPasteAttributes)
        val sheet = model.state.value.pasteAttributes!!
        assertEquals("vA" to "vB", sheet.sourceName to sheet.targetName)
        assertEquals(
            "a picture without sound takes its fades, but no volume, equaliser or pitch, and a still no speed",
            listOf(true, true, true, false, false, false, true, false),
            sheet.options.map { it.available },
        )
        assertFalse("the speed is off until chosen: it changes the clip's length", sheet.options.last().selected)
        model.onEvent(EditEvent.PasteAttribute(ClipAttribute.EFFECTS, false))
        assertFalse(model.state.value.pasteAttributes!!.options.first { it.attribute == ClipAttribute.EFFECTS }.selected)
        model.onEvent(EditEvent.ConfirmPasteAttributes)
        assertNull(model.state.value.pasteAttributes)
        assertEquals(clip("vA").transform, clip("vB").transform)
        assertEquals("粘贴属性", session.history.value.undoLabel)
        assertEquals("已将“vA”的画面变换、调色、淡入淡出粘贴到“vB”", model.state.value.toast)

        model.onEvent(EditEvent.OpenPasteAttributes)
        assertFalse("the choice is kept", model.state.value.pasteAttributes!!.options.first { it.attribute == ClipAttribute.EFFECTS }.selected)
        model.onEvent(EditEvent.ClosePasteAttributes)
        assertNull(model.state.value.pasteAttributes)
    }

    @Test
    fun `duplicate follows the original, delete leaves the gap and cut lifts the clip into the clipboard`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.DuplicateClip)
        val copy = session.document.value!!.timeline.clips.single { it.start == 150 }
        assertEquals("vB", copy.name)
        assertEquals(copy.id, session.selectedClipId.value)
        assertEquals("创建副本", session.history.value.undoLabel)
        assertEquals("已创建副本：“vB”（00:00:05:00）", model.state.value.toast)

        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.DeleteClip)
        val afterDelete = session.document.value!!.timeline
        assertEquals(listOf("vB", "mus", copy.id), afterDelete.clips.map { it.id })
        assertEquals("the gap stays", 90, afterDelete.clip("vB")!!.start)
        assertEquals("删除片段", session.history.value.undoLabel)
        assertEquals("已删除“vA”及链接的片段，保留空隙", model.state.value.toast)

        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.CutClip)
        assertEquals(listOf("mus", copy.id), session.document.value!!.timeline.clips.map { it.id })
        assertTrue(model.state.value.canPaste)
        assertEquals("剪切片段", session.history.value.undoLabel)
        assertEquals("已剪切“vB”", model.state.value.toast)

        model.onEvent(EditEvent.Seek(0))
        model.onEvent(EditEvent.PasteClips)
        assertEquals("the cut clip comes back where the playhead is", 0, session.document.value!!.timeline.clips.single { it.name == "vB" && it.trackId == Timeline.V1 && it.id != copy.id }.start)
    }

    @Test
    fun `a sound right after another can cross fade from it, and the inspector says how`() = runTest {
        // A second song clip right after the first on A2: the first has 4 s of its file left past its out point.
        val next = UiFixtures.clip("mus2", start = 120, frames = 60, trackId = Timeline.A2, assetId = "au")
        val (session, model) = editStage(UiFixtures.document(media = listOf(footage, song, still), clips = listOf(vA, aA, vB, mus, next)))
        model.onEvent(EditEvent.SelectClip("vA"))
        assertFalse("A1 has no sound before vA's", model.state.value.inspector!!.canCrossFade)
        assertEquals("前面没有紧挨着的声音片段，无法交叉淡化", model.state.value.inspector!!.crossFadeNote)

        model.onEvent(EditEvent.SelectClip("mus2"))
        assertTrue(model.state.value.inspector!!.canCrossFade)
        assertEquals(60, model.state.value.inspector!!.maxCrossFadeFrames)
        model.onEvent(EditEvent.CrossFade(15.4f))
        assertEquals(15, session.clip("mus2").crossFadeFrames)
        assertEquals("交叉淡化", session.history.value.undoLabel)
        assertEquals("0.50 秒", model.state.value.inspector!!.crossFadeText)
        assertTrue(model.state.value.inspector!!.crossFadeNote, model.state.value.inspector!!.crossFadeNote.endsWith("交叉淡化 0.50 秒（等功率，前一段在切点后延续）"))
    }

    @Test
    fun `ripple cut closes the gap and paste insert makes room for the clip at the playhead`() = runTest {
        val (session, model) = editStage()
        val timeline = { session.document.value!!.timeline }
        model.onEvent(EditEvent.PasteInsert)
        assertEquals("剪贴板是空的", model.state.value.toast)

        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.RippleCut)
        assertEquals("the still closes up", 0 to 60, timeline().clip("vB")!!.let { it.start to it.end })
        assertEquals("the music had nothing cut", 0 to 120, timeline().clip("mus")!!.let { it.start to it.end })
        assertTrue(model.state.value.canPaste)
        assertEquals("波纹剪切", session.history.value.undoLabel)
        assertEquals("已波纹剪切“vA”，后面的片段前移", model.state.value.toast)

        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.PasteInsert)
        assertEquals(listOf(0 to 30, 30 to 120, 120 to 150), timeline().clipsOn(Timeline.V1).map { it.start to it.end })
        assertEquals(listOf(30 to 120), timeline().clipsOn(Timeline.A1).map { it.start to it.end })
        assertEquals(listOf(0 to 30, 120 to 210), timeline().clipsOn(Timeline.A2).map { it.start to it.end })
        assertEquals("插入粘贴", session.history.value.undoLabel)
        assertEquals("已插入粘贴 2 个片段到 00:00:01:00，后面的片段后移 00:00:03:00", model.state.value.toast)
        assertEquals("the playhead moves past the pasted clips", 120, model.state.value.playheadFrame)
    }

    @Test
    fun `several selected clips switch off, move and nudge together, and the inspector says what is selected`() = runTest {
        val (session, model) = editStage()
        val clip = { id: String -> session.document.value!!.timeline.clip(id)!! }
        model.onEvent(EditEvent.SelectClip("vB"))
        assertNull("one clip: its properties", model.state.value.selection)
        model.onEvent(EditEvent.ToggleSelected("vA"))
        assertEquals(listOf("vA", "vB"), session.selectedClipIds.value)
        assertEquals(SelectionUi(2, "2 个画面 · 1 个声音", allDisabled = false, locked = false), model.state.value.selection!!.copy(alignNote = "", canAlign = false, compoundNote = "", canCompound = false, multicamNote = "", canMulticam = false))
        assertEquals(setOf("vA", "vB"), model.state.value.timeline.selectedClipIds)

        model.onEvent(EditEvent.MultiSelect(true))
        assertTrue(model.state.value.multiSelect && model.state.value.timeline.multiSelect)
        assertEquals("多选已开启：点按片段加入或移出选择，在空白处拖动可框选", model.state.value.toast)

        model.onEvent(EditEvent.ToggleClipEnabled)
        assertEquals(listOf(false, false, false), listOf("vA", "aA", "vB").map { clip(it).enabled })
        assertEquals("停用片段", session.history.value.undoLabel)
        assertTrue("the selection stays", model.state.value.selection!!.allDisabled)
        model.onEvent(EditEvent.ToggleClipEnabled)
        assertTrue(listOf("vA", "aA", "vB").all { clip(it).enabled })

        // Dragging one of them moves both, with vA's sound (snapping off: the exact offset).
        model.onEvent(EditEvent.SetSnapping(false))
        model.onEvent(EditEvent.Move("vB", 100, Timeline.V1, commit = true))
        assertEquals(listOf(10, 10, 100), listOf("vA", "aA", "vB").map { clip(it).start })
        assertEquals("移动片段", session.history.value.undoLabel)
        assertEquals(listOf("vB", "vA"), session.selectedClipIds.value)
        model.onEvent(EditEvent.Nudge(-5))
        assertEquals(listOf(5, 5, 95), listOf("vA", "aA", "vB").map { clip(it).start })
        // Up a track together; vA's sound stays on its own track.
        model.onEvent(EditEvent.MoveTrack(up = true))
        assertEquals("已将 2 个片段上移 1 条轨道", model.state.value.toast)
        assertEquals(listOf(Timeline.V2, Timeline.A1, Timeline.V2), listOf("vA", "aA", "vB").map { clip(it).trackId })
        assertEquals("移动到其他轨道", session.history.value.undoLabel)
        model.onEvent(EditEvent.MoveTrack(up = true))
        assertEquals("已经在最上面的轨道", model.state.value.toast)
        model.onEvent(EditEvent.Trim("vB", TrimEdge.END, 140, commit = true))
        assertEquals(140, clip("vB").end)
        assertEquals("trimming one keeps the others", 2, session.selectedClipIds.value.size)

        model.onEvent(EditEvent.SelectClip(null))
        assertNull(model.state.value.selection)
        assertEquals(emptySet<String>(), model.state.value.timeline.selectedClipIds)
    }

    @Test
    fun `link clips frees a picture from its sound for a split edit, and pairs a picture with a sound`() = runTest {
        val (session, model) = editStage()
        val clip = { id: String -> session.document.value!!.timeline.clip(id)!! }
        model.onEvent(EditEvent.SelectClip("vA"))
        assertEquals(LinkAction.UNLINK, model.state.value.linkAction)
        model.onEvent(EditEvent.ToggleLink)
        assertNull(clip("vA").linkId)
        assertNull(clip("aA").linkId)
        assertEquals("取消链接", session.history.value.undoLabel)
        assertEquals("已取消链接：画面和声音可以分别移动和修剪", model.state.value.toast)
        assertNull("one picture alone: nothing to link", model.state.value.linkAction)
        // Apart, the picture cuts at 60 while its sound runs on to 90 — an L cut.
        model.onEvent(EditEvent.SetSnapping(false))
        model.onEvent(EditEvent.Trim("vA", TrimEdge.END, 60, commit = true))
        assertEquals(listOf(60, 90), listOf("vA", "aA").map { clip(it).end })

        // The picture vB and the music become a pair.
        model.onEvent(EditEvent.SelectClip("vB"))
        model.onEvent(EditEvent.ToggleSelected("mus"))
        assertEquals(LinkAction.LINK, model.state.value.linkAction)
        model.onEvent(EditEvent.ToggleLink)
        assertNotNull(clip("vB").linkId)
        assertEquals(clip("vB").linkId, clip("mus").linkId)
        assertEquals("链接片段", session.history.value.undoLabel)
        assertEquals("已链接片段：画面和声音一起移动和修剪", model.state.value.toast)
        session.undo()
        assertNull(clip("vB").linkId)
    }

    @Test
    fun `a marquee gathers the clips it touches onto the selection, a linked pair once`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.MultiSelect(true))
        model.onEvent(EditEvent.SelectClip("vB"))
        // Frames 30–100 over V1 and A1: vA joins vB (with its sound, counted once); vB is not added twice.
        model.onEvent(EditEvent.SelectInMarquee(30, 100, setOf(Timeline.V1, Timeline.A1)))
        assertEquals(listOf("vB", "vA"), session.selectedClipIds.value)
        assertEquals("已框选 1 个片段", model.state.value.toast)
        assertEquals(SelectionUi(2, "2 个画面 · 1 个声音", allDisabled = false, locked = false), model.state.value.selection!!.copy(alignNote = "", canAlign = false, compoundNote = "", canCompound = false, multicamNote = "", canMulticam = false))
        model.onEvent(EditEvent.SelectInMarquee(0, 10, setOf(Timeline.A2)))
        assertEquals(listOf("vB", "vA", "mus"), session.selectedClipIds.value)
        // A box with nothing new in it leaves the selection as it is.
        model.onEvent(EditEvent.SelectInMarquee(200, 260, setOf(Timeline.V1)))
        assertEquals("框内没有可加入的片段", model.state.value.toast)
        assertEquals(listOf("vB", "vA", "mus"), session.selectedClipIds.value)
    }

    @Test
    fun `copy, paste, select forward, select all, ripple delete and delete act on the whole selection`() = runTest {
        val (session, model) = editStage()
        val clips = { session.document.value!!.timeline.clips }
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.ToggleSelected("mus"))
        model.onEvent(EditEvent.CopyClip)
        assertEquals("已复制 2 个片段", model.state.value.toast)
        model.onEvent(EditEvent.Seek(150))
        model.onEvent(EditEvent.PasteClips)
        val pasted = clips().filter { it.start == 150 }
        assertEquals(setOf(Timeline.V1, Timeline.A1, Timeline.A2), pasted.map { it.trackId }.toSet())
        assertEquals("the copies come back selected (a linked pair once)", 2, session.selectedClipIds.value.size)
        assertEquals("the playhead moves past the paste", 270, model.state.value.playheadFrame)

        model.onEvent(EditEvent.SelectForward(null))
        assertEquals("播放头之后没有片段", model.state.value.toast)
        model.onEvent(EditEvent.SelectForward("vB"))
        assertEquals("vB and the three pasted clips, the pair once", 3, session.selectedClipIds.value.size)
        assertEquals("已选择 3 个片段", model.state.value.toast)
        model.onEvent(EditEvent.RippleDelete)
        assertEquals(listOf("vA", "aA", "mus"), clips().map { it.id })
        assertEquals("波纹删除", session.history.value.undoLabel)

        model.onEvent(EditEvent.SelectAll)
        assertEquals(listOf("vA", "mus"), session.selectedClipIds.value)
        model.onEvent(EditEvent.DeleteClip)
        assertTrue(clips().isEmpty())
        assertEquals("已删除 2 个片段，保留空隙", model.state.value.toast)
        assertEquals(emptyList<String>(), session.selectedClipIds.value)
    }

    @Test
    fun `the edit keys put the selected pool item at the playhead on the Edit stage too`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.EditFromSource(SourceEdit.INSERT))
        assertEquals("先在左侧媒体池中选择素材", model.state.value.toast)
        model.onEvent(EditEvent.SelectLibraryItem("au"))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.EditFromSource(SourceEdit.OVERWRITE))
        val placed = session.document.value!!.timeline.clip(session.selectedClipId.value)!!
        assertEquals(Timeline.A2 to 30, placed.trackId to placed.start)
        assertEquals("the whole item: 8 s", 240, placed.durationFrames)
        assertEquals("覆盖到时间线", session.history.value.undoLabel)
        assertEquals("the playhead moves past it", 270, model.state.value.playheadFrame)
    }

    @Test
    fun `the source viewer's marks put only the marked part on the timeline - I and O mark the source while it shows`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.SelectLibraryItem("au"))
        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_SOURCE))
        assertNull(model.state.value.sourceMarks)
        source.seekTo(2_000)
        model.onEvent(EditEvent.MarkIn)
        source.seekTo(5_000)
        model.onEvent(EditEvent.MarkOut)
        assertEquals(SourceMarks(2_000, 5_000), session.sourceMarks.value["au"])
        assertEquals("入点 00:00:02:00 · 出点 00:00:05:00 · 3.0 秒", model.state.value.sourceMarks)
        assertEquals("已标记源出点 00:00:05:00", model.state.value.toast)
        assertNull("the timeline keeps its own marks", session.document.value!!.timeline.marks.inFrame)
        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_TIMELINE))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.EditFromSource(SourceEdit.OVERWRITE))
        val placed = session.document.value!!.timeline.clip(session.selectedClipId.value)!!
        assertEquals("the marked 3 s, from 2 s into the song", 90 to 2_000L, placed.durationFrames to placed.sourceInMs)
        // I in the timeline view marks the timeline again.
        model.onEvent(EditEvent.MarkIn)
        assertEquals(placed.end, session.document.value!!.timeline.marks.inFrame)
        model.onEvent(EditEvent.ViewerMode(EditUiState.VIEWER_SOURCE))
        model.onEvent(EditEvent.ClearSourceMarks)
        assertNull(session.sourceMarks.value["au"])
        assertNull(model.state.value.sourceMarks)
    }

    @Test
    fun `match frame opens the clip under the playhead in the source viewer at the same frame, marked as the clip uses it`() = runTest {
        val doc = project().let { d -> d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("vA")!!.copy(sourceInMs = 4_000))) }
        val (session, model) = editStage(doc)
        model.onEvent(EditEvent.Seek(45))
        model.onEvent(EditEvent.MatchFrame())
        val s = model.state.value
        assertEquals(EditUiState.VIEWER_SOURCE to "v1", s.viewerMode to s.libraryItemId)
        assertEquals(LibraryTab.MEDIA, s.libraryTab)
        assertEquals("the picture over its sound: 1.5 s in after its 4 s head", 5_500L, source.state.value.positionMs)
        assertEquals(SourceMarks(4_000, 7_000), session.sourceMarks.value["v1"])
        assertEquals("匹配帧：v1.mp4 00:00:05:15", s.toast)
        assertEquals("入点 00:00:04:00 · 出点 00:00:07:00 · 3.0 秒", s.sourceMarks)
        // From a clip's menu: a clip the playhead is not over matches at its first frame; a still has no marks.
        model.onEvent(EditEvent.MatchFrame("vB"))
        assertEquals("im" to 0L, model.state.value.libraryItemId to source.state.value.positionMs)
        assertNull(session.sourceMarks.value["im"])
        model.onEvent(EditEvent.Seek(140))
        model.onEvent(EditEvent.SelectClip(null))
        model.onEvent(EditEvent.Seek(200))
        model.onEvent(EditEvent.MatchFrame())
        assertEquals("播放头处没有可以匹配的片段", model.state.value.toast)
    }

    /** Hands back an image asset for every frame asked for, or nothing when [fail]. */
    private class FakeStills(private val fail: Boolean = false) : StillFrameRepository {
        val captured = mutableListOf<Pair<String, Long>>()
        override suspend fun capture(source: MediaAsset, timeMs: Long, id: String, name: String): MediaAsset? {
            captured += source.id to timeMs
            return if (fail) null else UiFixtures.image(id).copy(name = name)
        }
    }

    @Test
    fun `freeze frame captures the playhead frame and holds it for two seconds inside the clip`() = runTest {
        val stills = FakeStills()
        val (session, model) = editStage(stills = stills)
        model.onEvent(EditEvent.FreezeFrame)
        assertEquals("请先在时间线上选择视频片段", model.state.value.toast)
        model.onEvent(EditEvent.SelectClip("vB"))
        assertFalse("a still image cannot freeze", model.state.value.inspector!!.canFreeze)

        model.onEvent(EditEvent.SelectClip("vA"))
        assertTrue(model.state.value.inspector!!.canFreeze)
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.FreezeFrame)
        advanceUntilIdle()

        assertEquals(listOf("v1" to 1_000L), stills.captured)
        val doc = session.document.value!!
        val frozen = doc.timeline.clips.single { it.name == "定格 · vA" }
        assertEquals(30 until 90, frozen.start until frozen.end)
        assertTrue("the still joins the media", doc.media.any { it.id == frozen.assetId })
        assertEquals("the next clip moves later", 150, doc.timeline.clip("vB")!!.start)
        assertEquals("the music keeps its timing", 0, doc.timeline.clip("mus")!!.start)
        assertEquals(frozen.id, session.selectedClipId.value)
        assertEquals("定格帧", session.history.value.undoLabel)
        assertEquals("已在 00:00:01:00 定格 2 秒，后面的片段顺延", model.state.value.toast)

        session.undo()
        assertEquals(project().timeline, session.document.value!!.timeline)
        assertEquals(project().media, session.document.value!!.media)
    }

    @Test
    fun `a frame that cannot be read is explained and changes nothing`() = runTest {
        val (session, model) = editStage(stills = FakeStills(fail = true))
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(30))
        model.onEvent(EditEvent.FreezeFrame)
        advanceUntilIdle()

        assertEquals("无法读取这一帧", model.state.value.toast)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `the curve editor shows the selected clip's curves, drags a key as one undo step, eases it, adds and deletes keys`() = runTest {
        val (session, model) = editStage()
        model.onEvent(EditEvent.CurveMode(true))
        assertTrue(model.state.value.curves!!.hint!!.startsWith("在时间线上选择一个片段"))
        model.onEvent(EditEvent.SelectClip("vA"))
        model.onEvent(EditEvent.Seek(10))
        model.onEvent(EditEvent.ToggleKeyframe)
        model.onEvent(EditEvent.Seek(70))
        model.onEvent(EditEvent.Scale(200f))
        var curves = model.state.value.curves!!
        assertEquals("the moving curve comes first", "缩放", curves.curve!!.label)
        assertEquals(CurveRowUi("t:SCALE", "缩放", "200%", 2, true, true), curves.rows.first())
        assertEquals("a key at the playhead already", false, curves.canAdd)
        model.onEvent(EditEvent.CurveKeyPicked(0))
        assertEquals("关键帧 1/2 · 00:00:00:10 · 100%", model.state.value.curves!!.readout)
        // Two moves of one drag make one undo step.
        model.onEvent(EditEvent.CurveKeyMoved(0, 20, 120f, drag = 1))
        model.onEvent(EditEvent.CurveKeyMoved(0, 25, 130f, drag = 1))
        // The scale's own key moved (rev 162): the other settings keep theirs at frame 10.
        val scaleKeys = { session.clip("vA").keyframes.track(CurveParam.SCALE).map { it.frame to it.value.toInt() } }
        assertEquals(listOf(25 to 130, 70 to 200), scaleKeys())
        assertEquals(listOf(10, 25, 70), session.clip("vA").keyframes.map { it.frame })
        assertEquals("移动关键帧", session.history.value.undoLabel)
        session.undo()
        assertEquals(listOf(10 to 100, 70 to 200), scaleKeys())
        // A preset, then a slope of its own from a control point — the scale's alone.
        model.onEvent(EditEvent.CurveEasePicked(CurveEase.EASE_OUT))
        assertEquals("关键帧缓动：缓出", model.state.value.toast)
        assertEquals(CurveEase.EASE_OUT, model.state.value.curves!!.ease)
        model.onEvent(EditEvent.CurveSlope(0, arriving = false, slope = 2f, drag = 2))
        curves = model.state.value.curves!!
        assertEquals(KeyEase(1f, 2f), session.clip("vA").keyframes[0].easeOf(CurveParam.SCALE))
        assertEquals("the opacity's ease untouched", KeyEase.LINEAR, session.clip("vA").keyframes[0].easeOf(CurveParam.OPACITY))
        assertNull(curves.ease)
        assertTrue(curves.readout.endsWith("离开斜率 2.00"))
        // A key at the playhead, selected; deleted again.
        model.onEvent(EditEvent.Seek(40))
        model.onEvent(EditEvent.CurveAddKey)
        assertEquals("已在第 40 帧添加关键帧 · 共 3 个", model.state.value.toast)
        assertEquals(1, model.state.value.curves!!.selectedKey)
        model.onEvent(EditEvent.CurveDeleteKey)
        assertEquals(listOf(10, 70), session.clip("vA").keyframes.map { it.frame })
        assertNull(model.state.value.curves!!.selectedKey)
        model.onEvent(EditEvent.CurvePicked("t:OPACITY"))
        assertEquals("不透明度", model.state.value.curves!!.curve!!.label)
        model.onEvent(EditEvent.CurveMode(false))
        assertNull(model.state.value.curves)
    }
}
