package com.example.prismstudio.p40b43a88.ui.color

import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.DocumentFailure
import com.example.prismstudio.p40b43a88.data.repository.DocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository
import com.example.prismstudio.p40b43a88.data.repository.InMemoryLutRepository
import com.example.prismstudio.p40b43a88.data.repository.LutRepository
import com.example.prismstudio.p40b43a88.data.repository.PickedDocument
import com.example.prismstudio.p40b43a88.data.repository.FakeMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.GalleryRepository
import com.example.prismstudio.p40b43a88.data.repository.InMemoryGalleryRepository
import com.example.prismstudio.p40b43a88.domain.usecase.BuildGradeLutUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ShotMatchUseCase
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.SpatialNrControl
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurveChannel
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.GradeWheel
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.LutRef
import com.example.prismstudio.p40b43a88.domain.usecase.CubeLutParser
import com.example.prismstudio.p40b43a88.domain.usecase.cubeText
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.PrimaryControl
import com.example.prismstudio.p40b43a88.domain.model.ChannelMix
import com.example.prismstudio.p40b43a88.domain.model.MixerInput
import com.example.prismstudio.p40b43a88.domain.model.MixerOutput
import com.example.prismstudio.p40b43a88.domain.model.MixerSwap
import com.example.prismstudio.p40b43a88.domain.model.RgbMixer
import com.example.prismstudio.p40b43a88.domain.model.WarpMove
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.openSession
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.QualifierControl
import kotlin.math.roundToInt
import com.example.prismstudio.p40b43a88.domain.usecase.LumaFrames
import com.example.prismstudio.p40b43a88.data.repository.FakeLumaFrameRepository
import com.example.prismstudio.p40b43a88.data.repository.LumaFrameRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ColorViewModelTest {

    private val program = FakeProgramPlaybackRepository()
    private val previews = FakeMediaPreviewRepository()
    private val clock = TestClock()

    private val first = UiFixtures.video("v1")
    private val second = UiFixtures.video("v2")

    // Two graded picture clips on V1 (0–90, 90–180); a title and a sound clip that the Color stage ignores.
    private fun project(media: List<MediaAsset> = listOf(first, second)) = UiFixtures.document(
        media = media,
        clips = listOf(
            UiFixtures.clip("c2", start = 90, frames = 90, assetId = "v2"),
            UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1"),
            UiFixtures.clip("ttl", start = 0, frames = 60, trackId = Timeline.V2, kind = ClipKind.TITLE),
            UiFixtures.clip("snd", start = 0, frames = 90, trackId = Timeline.A1, assetId = "v1"),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.colorStage(
        doc: ProjectDocument = project(),
        luts: LutRepository = InMemoryLutRepository(),
        documents: DocumentRepository = DocumentRepository.None,
        downloads: DownloadsRepository = DownloadsRepository.None,
        gallery: GalleryRepository = InMemoryGalleryRepository(),
        lumaFrames: LumaFrameRepository = LumaFrameRepository.None,
    ): Pair<DefaultEditorSessionRepository, ColorViewModel> {
        val session = openSession(doc, clock = clock)
        // In the app the workspace shell feeds every document to the program monitor.
        program.setProject(session.document.value)
        // Scope maths runs on the injected dispatcher; keep it on the test scheduler.
        val model = ColorViewModel(
            session, program, previews, luts, documents, downloads, gallery, SequentialIds(),
            compute = StandardTestDispatcher(testScheduler),
            lumaFrames = lumaFrames,
        )
        advanceUntilIdle()
        return session to model
    }

    private fun DefaultEditorSessionRepository.grade(clipId: String): ColorGrade =
        document.value!!.timeline.clip(clipId)!!.grade

    @Test
    fun `shot match sets node 1 so the clip meets the picked still`() = runTest {
        val gallery = InMemoryGalleryRepository()
        val (session, model) = colorStage(gallery = gallery)
        // A colourful card for both clips, dark enough that grading it warm clips nothing.
        previews.frame = FramePixels(64, 36, IntArray(64 * 36) { i ->
            val x = (i % 64) / 63f
            val y = (i / 64) / 35f
            fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()
            val v = 0.1f + 0.5f * y
            (0xFF shl 24) or (c(v * (0.8f + 0.2f * x)) shl 16) or (c(v * 0.9f) shl 8) or c(v * (1f - 0.2f * x))
        })
        // c1 graded warmer than the Gain puck alone can make it, kept as still 1.
        val warm = ColorGrade(gain = WheelValue(0.5f, 0f, 0f), temperature = 100f)
        session.edit("调色") { d -> d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("c1")!!.copy(grade = warm))) }
        model.onEvent(ColorEvent.GrabStill)
        advanceUntilIdle()
        val still = session.document.value!!.stills.single()
        model.onEvent(ColorEvent.SelectClip("c2"))
        model.onEvent(ColorEvent.MatchStill)
        advanceUntilIdle()
        val matched = session.grade("c2")
        val stored = gallery.load(session.document.value!!.id, still.image, 320)!!
        val expected = ShotMatchUseCase(BuildGradeLutUseCase()).match(previews.frame!!, stored, ColorGrade.NEUTRAL).grade
        assertEquals("every wheel and the white balance arrive", expected, matched)
        assertTrue("the white balance takes what the puck cannot: ${matched.temperature}", matched.temperature > 10f)
        assertEquals("镜头匹配", session.history.value.undoLabel)
        assertTrue(model.state.value.toast!!, model.state.value.toast!!.startsWith("已匹配到静帧 1："))
        assertFalse(model.state.value.matching)
    }

    @Test
    fun `the current clip follows the playhead into another clip, and stays in a gap`() = runTest {
        val (_, model) = colorStage()
        assertEquals("c1", model.state.value.selectedClipId)
        program.seekTo(4_000)
        advanceUntilIdle()
        assertEquals("c2", model.state.value.selectedClipId)
        program.seekTo(10_000)
        advanceUntilIdle()
        assertEquals("past the last clip the current one stays", "c2", model.state.value.selectedClipId)
    }

    @Test
    fun `auto colour sets node 1's lift and gain from the picture at the playhead`() = runTest {
        val (session, model) = colorStage()
        // The fake's flat grey frame has no black or white point.
        model.onEvent(ColorEvent.AutoColor)
        advanceUntilIdle()
        assertEquals("画面太平或几乎是单色，无法自动调色", model.state.value.toast)
        assertEquals(ColorGrade.NEUTRAL, session.grade("c1"))
        // A dull warm ramp: tones 0.15…0.75, red up and blue down.
        previews.frame = FramePixels(256, 1, IntArray(256) { i ->
            val v = 0.15f + 0.6f * i / 255f
            fun c(x: Float) = (x.coerceIn(0f, 1f) * 255f).roundToInt()
            (0xFF shl 24) or (c(v + 0.06f) shl 16) or (c(v) shl 8) or c(v - 0.06f)
        })
        model.onEvent(ColorEvent.AutoColor)
        advanceUntilIdle()
        val grade = session.grade("c1")
        assertTrue("lift lowers the blacks", grade.lift.master < -0.2f)
        assertTrue("gain raises the whites", grade.gain.master > 0.2f)
        assertTrue("the cast moves the pucks", hypot(grade.gain.x, grade.gain.y) > 0.05f)
        assertEquals("自动调色", session.history.value.undoLabel)
        assertTrue(model.state.value.toast!!, model.state.value.toast!!.startsWith("已自动调色：黑位 16 → 0，白位 76 → 100"))
    }

    @Test
    fun `a clip whose media was replaced gets its new picture in the strip`() = runTest {
        // c1 starts 5 s in: v2 has no picture of that second yet (c2's is from its own start).
        val (session, _) = colorStage(project().let { d -> d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("c1")!!.copy(sourceInMs = 5_000))) })
        val thumbsOfSecond = { previews.frameCalls.count { it == second.uri to false } }
        assertEquals(1, thumbsOfSecond())
        // A replace keeps the clip's id and source in point; only its media changes.
        session.edit("替换片段") { d -> d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("c1")!!.copy(assetId = "v2"))) }
        advanceUntilIdle()
        assertEquals("c1 now shows v2's picture", 2, thumbsOfSecond())
    }

    @Test
    fun `clips starting in the same second of a file share one strip picture`() = runTest {
        val doc = project().let { d ->
            val c1 = d.timeline.clip("c1")!!
            d.copy(timeline = d.timeline.copy(clips = d.timeline.clips + (1..20).map { c1.copy(id = "copy$it", start = 1_000 + it * 10, durationFrames = 10, sourceInMs = c1.sourceInMs + 300) }))
        }
        val (_, model) = colorStage(doc)
        val firstFile = doc.asset(doc.timeline.clip("c1")!!.assetId)!!.uri
        assertEquals("one picture for c1 and its 20 copies", 1, previews.frameCalls.count { it == firstFile to false })
        assertTrue(model.state.value.clips.all { it.pixels != null })
    }

    @Test
    fun `lists picture clips in timeline order and selects the clip under the playhead`() = runTest {
        val (_, model) = colorStage()

        val state = model.state.value
        assertEquals(listOf("c1", "c2"), state.clips.map { it.id })
        assertEquals(listOf("01", "02"), state.clips.map { it.index })
        assertEquals("c1", state.selectedClipId)
        assertEquals(ColorGrade.NEUTRAL, state.grade)
        assertTrue(state.clips.none { it.graded })
        assertTrue("thumbnails are decoded for the strip", state.clips.all { it.pixels != null })
        assertEquals("R 0.00  G 0.00  B 0.00", state.readouts.getValue(GradeWheel.LIFT))
        assertNull(state.programMessage)
    }

    @Test
    fun `a timeline without picture clips says so`() = runTest {
        val (_, model) = colorStage(UiFixtures.document())

        val state = model.state.value
        assertTrue(state.clips.isEmpty())
        assertFalse(state.hasClip)
        assertEquals("时间线上还没有画面片段", state.programMessage)
        assertNull(state.scope)
    }

    @Test
    fun `wheel balance is clamped to the maximum puck radius`() = runTest {
        val (session, model) = colorStage()

        model.onEvent(ColorEvent.WheelBalance(GradeWheel.LIFT, 0.6f, 0.8f))

        val lift = session.grade("c1").lift
        assertEquals(0.3f, lift.x, 1e-4f)
        assertEquals(0.4f, lift.y, 1e-4f)
        assertTrue(hypot(lift.x, lift.y) <= WheelValue.MAX_BALANCE + 1e-4f)
        assertEquals(lift, model.state.value.grade.lift)
        assertEquals("调色：暗部 Lift", session.history.value.undoLabel)

        model.onEvent(ColorEvent.WheelBalance(GradeWheel.GAIN, 0.1f, -0.2f))
        assertEquals("inside the radius the puck is kept as is", WheelValue(0.1f, -0.2f, 0f), session.grade("c1").gain)
    }

    @Test
    fun `wheel master is limited to -1 to 1`() = runTest {
        val (session, model) = colorStage()

        model.onEvent(ColorEvent.WheelMaster(GradeWheel.GAMMA, 2.5f))
        model.onEvent(ColorEvent.WheelMaster(GradeWheel.OFFSET, -3f))

        assertEquals(1f, session.grade("c1").gamma.master, 0f)
        assertEquals(-1f, session.grade("c1").offset.master, 0f)
        assertEquals("R 1.00  G 1.00  B 1.00", model.state.value.readouts.getValue(GradeWheel.GAMMA))
        assertEquals("调色：整体 Offset", session.history.value.undoLabel)
    }

    @Test
    fun `primary sliders write clamped values and mark the clip as graded`() = runTest {
        val (session, model) = colorStage()

        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 5f))
        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 1.25f))
        model.onEvent(ColorEvent.Primary(PrimaryControl.HUE, -500f))

        val grade = session.grade("c1")
        assertEquals(3f, grade.exposure, 0f)
        assertEquals(1.25f, grade.saturation, 0f)
        assertEquals(-180f, grade.hue, 0f)
        assertEquals(grade, model.state.value.grade)
        assertEquals(listOf(true, false), model.state.value.clips.map { it.graded })
        assertEquals("调色：色相", session.history.value.undoLabel)
    }

    @Test
    fun `the RGB Mixer palette mixes, swaps, turns grey and resets the selected clip, each change one undo step`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_MIXER))
        assertEquals(ColorUiState.TAB_MIXER, model.state.value.adjustTab)
        // A drag on one slider is one step; the amounts stop at Resolve's −2…3.
        model.onEvent(ColorEvent.MixerValue(MixerOutput.RED, MixerInput.GREEN, 0.3f))
        model.onEvent(ColorEvent.MixerValue(MixerOutput.RED, MixerInput.GREEN, 9f))
        assertEquals(ChannelMix(1f, 3f, 0f), session.grade("c1").rgbMixer.red)
        assertEquals("RGB 混合器：红色输出 G", session.history.value.undoLabel)
        assertEquals(session.grade("c1"), model.state.value.grade)
        model.onEvent(ColorEvent.MixerSwapChannels(MixerSwap.RED_GREEN))
        assertEquals(ChannelMix(1f, 3f, 0f), session.grade("c1").rgbMixer.green)
        model.onEvent(ColorEvent.MixerMonochrome(true))
        model.onEvent(ColorEvent.MixerValue(null, MixerInput.BLUE, 0.5f))
        assertEquals(ChannelMix(0.2126f, 0.7152f, 0.5f), session.grade("c1").rgbMixer.mono)
        assertEquals("RGB 混合器：灰度 B", session.history.value.undoLabel)
        model.onEvent(ColorEvent.MixerPreserve(false))
        assertFalse(session.grade("c1").rgbMixer.preserveLuminance)
        model.onEvent(ColorEvent.ResetMixer)
        assertEquals(RgbMixer.NEUTRAL, session.grade("c1").rgbMixer)
        assertEquals("重置 RGB 混合器", session.history.value.undoLabel)
        session.undo()
        assertTrue(session.grade("c1").rgbMixer.monochrome)
    }

    @Test
    fun `the Color Warper moves a picked point in one undo step per drag, and resets one point or all`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_WARPER))
        assertEquals(ColorUiState.TAB_WARPER, model.state.value.adjustTab)
        assertEquals(0 to 2, model.state.value.warperSpoke to model.state.value.warperRing)
        // A drag on the green inner point: several moves, one step; past the limits it stops at ±50° / ±0.3.
        model.onEvent(ColorEvent.WarperMove(2, 0, 10f, 0f))
        model.onEvent(ColorEvent.WarperMove(2, 0, 80f, -0.5f))
        assertEquals(WarpMove(2, 0, 50f, -0.3f), session.grade("c1").warper.move(2, 0))
        assertEquals(2 to 0, model.state.value.warperSpoke to model.state.value.warperRing)
        assertEquals("色彩扭曲器：绿 · 内圈", session.history.value.undoLabel)
        model.onEvent(ColorEvent.WarperSelect(0, 2))
        model.onEvent(ColorEvent.WarperMove(0, 2, -20f, 0.1f))
        model.onEvent(ColorEvent.ResetWarperPoint)
        assertEquals(null, session.grade("c1").warper.move(0, 2))
        assertEquals("色彩扭曲器：重置红 · 外圈", session.history.value.undoLabel)
        model.onEvent(ColorEvent.ResetWarper)
        assertTrue(session.grade("c1").warper.isIdentity)
        session.undo()
        assertEquals(WarpMove(2, 0, 50f, -0.3f), session.grade("c1").warper.move(2, 0))
    }

    @Test
    fun `a continuous drag on one control is a single undo step`() = runTest {
        val (session, model) = colorStage()

        model.onEvent(ColorEvent.WheelBalance(GradeWheel.LIFT, 0.1f, 0f))
        model.onEvent(ColorEvent.WheelBalance(GradeWheel.LIFT, 0.2f, 0f))
        model.onEvent(ColorEvent.WheelBalance(GradeWheel.LIFT, 0.3f, 0f))
        model.onEvent(ColorEvent.Primary(PrimaryControl.CONTRAST, 1.2f))

        session.undo()
        assertEquals(1f, session.grade("c1").contrast, 0f)
        assertEquals(0.3f, session.grade("c1").lift.x, 1e-6f)
        session.undo()
        assertEquals(WheelValue.NEUTRAL, session.grade("c1").lift)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `the qualifier's finesse page refines the matte of node 2's qualifier`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.QualifierPage(true))
        assertTrue(model.state.value.qualifierFinesse)
        // Without a qualifier there is nothing to refine.
        model.onEvent(ColorEvent.Qualifier(QualifierControl.BLUR, 40f))
        assertNull(session.grade("c1").window?.qualifier)
        session.edit("限定器") { d ->
            d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.map {
                if (it.id == "c1") it.copy(grade = it.grade.copy(window = PowerWindow(shape = WindowShape.FULL, qualifier = HslQualifier()))) else it
            }))
        }
        model.onEvent(ColorEvent.Qualifier(QualifierControl.CLEAN_BLACK, 25f))
        model.onEvent(ColorEvent.Qualifier(QualifierControl.BLUR, 60f))
        val q = session.grade("c1").window!!.qualifier!!
        assertEquals(listOf(25f, 0f, 60f), listOf(q.cleanBlack, q.cleanWhite, q.blur))
        assertEquals("限定器：模糊半径", session.history.value.undoLabel)
        model.onEvent(ColorEvent.QualifierPage(false))
        assertFalse(model.state.value.qualifierFinesse)
    }

    @Test
    fun `the hue curves are edited in their own mode, a tap adding its anchors, and reset one at a time`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.PickCurve(4 + HueCurve.HUE_SAT.ordinal))
        assertEquals(HueCurve.HUE_SAT, model.state.value.hueCurve)

        model.onEvent(ColorEvent.HueCurveAdd(4f / 6f))
        val added = session.grade("c1").hueCurves.hueSat
        assertEquals(3, added.size)
        assertEquals(listOf(0.5f, 0.5f, 0.5f), added.map { it.y })
        assertEquals("色相对饱和度：添加控制点", session.history.value.undoLabel)

        model.onEvent(ColorEvent.HueCurveMove(1, 4f / 6f, 0.2f))
        model.onEvent(ColorEvent.HueCurveMove(1, 4f / 6f, 0f))
        assertEquals(0f, session.grade("c1").hueCurves.hueSat[1].y, 0f)
        // Release round R10: the press that added the point and the drag that follows are one step to undo.
        assertEquals("色相对饱和度：添加控制点", session.history.value.undoLabel)
        session.undo()
        assertEquals("adding and dragging is one undo step", emptyList<CurvePoint>(), session.grade("c1").hueCurves.hueSat)
        session.redo()
        assertEquals(0f, session.grade("c1").hueCurves.hueSat[1].y, 0f)
        assertEquals(session.grade("c1").hueCurves, model.state.value.grade.hueCurves)
        model.onEvent(ColorEvent.HueCurveMove(1, 4f / 6f, 0.3f))
        assertEquals("a later drag is its own step", "色相对饱和度：调整曲线", session.history.value.undoLabel)

        model.onEvent(ColorEvent.PickCurve(4 + HueCurve.HUE_LUM.ordinal))
        model.onEvent(ColorEvent.HueCurveAdd(0f))
        model.onEvent(ColorEvent.ResetCurve)
        assertEquals("reset clears only the curve shown", emptyList<CurvePoint>(), session.grade("c1").hueCurves.hueLum)
        assertEquals(3, session.grade("c1").hueCurves.hueSat.size)
        model.onEvent(ColorEvent.HueCurveRemove(0))
        model.onEvent(ColorEvent.PickCurve(CurveChannel.RED.ordinal))
        assertNull(model.state.value.hueCurve)
        assertEquals(CurveChannel.RED, model.state.value.curveChannel)
        model.onEvent(ColorEvent.ResetCurve)
        assertEquals("the custom curves reset apart", 3, session.grade("c1").hueCurves.hueSat.size)
    }

    @Test
    fun `curve points are added, moved and removed on the selected channel only`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.Channel(CurveChannel.RED.ordinal))
        assertEquals(CurveChannel.RED, model.state.value.curveChannel)

        model.onEvent(ColorEvent.CurveAdd(0.5f, 0.6f))
        assertEquals(listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.6f), CurvePoint(1f, 1f)), session.grade("c1").curves.red)
        assertEquals("添加曲线控制点", session.history.value.undoLabel)

        model.onEvent(ColorEvent.CurveMove(1, 0.4f, 0.7f))
        assertEquals("the drag after the press that added the point is the same step", "添加曲线控制点", session.history.value.undoLabel)
        session.undo()
        assertEquals(ToneCurves.IDENTITY, session.grade("c1").curves.red)
        session.redo()
        model.onEvent(ColorEvent.CurveMove(0, 0.5f, 0.2f))
        assertEquals(
            "end points only move vertically",
            listOf(CurvePoint(0f, 0.2f), CurvePoint(0.4f, 0.7f), CurvePoint(1f, 1f)),
            session.grade("c1").curves.red,
        )
        assertEquals("调整曲线", session.history.value.undoLabel)

        model.onEvent(ColorEvent.CurveRemove(0))
        assertEquals("end points cannot be removed", 3, session.grade("c1").curves.red.size)
        model.onEvent(ColorEvent.CurveRemove(1))
        assertEquals(listOf(CurvePoint(0f, 0.2f), CurvePoint(1f, 1f)), session.grade("c1").curves.red)
        assertEquals("删除曲线控制点", session.history.value.undoLabel)

        model.onEvent(ColorEvent.ResetCurve)
        assertEquals(ToneCurves.IDENTITY, session.grade("c1").curves.red)
        assertEquals("重置曲线", session.history.value.undoLabel)
        assertEquals("other channels are untouched", ToneCurves.IDENTITY, session.grade("c1").curves.luma)

        model.onEvent(ColorEvent.Channel(9))
        assertEquals(CurveChannel.BLUE, model.state.value.curveChannel)
    }

    @Test
    fun `looks and look strength are part of the clip grade`() = runTest {
        val (session, model) = colorStage()

        model.onEvent(ColorEvent.SelectLook(Look.TEAL_ORANGE))
        assertEquals(Look.TEAL_ORANGE, session.grade("c1").look)
        assertEquals("LUT 风格：青橙电影", session.history.value.undoLabel)

        model.onEvent(ColorEvent.LookStrength(150f))
        assertEquals(100, session.grade("c1").lookStrength)
        model.onEvent(ColorEvent.LookStrength(-4f))
        assertEquals(0, session.grade("c1").lookStrength)
        assertEquals("LUT 强度", session.history.value.undoLabel)
        assertEquals(session.grade("c1"), model.state.value.grade)
    }

    @Test
    fun `spatial noise reduction is part of the clip grade, on node 01 whatever node or keyframes, and resets to off`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_NOISE))
        assertEquals(ColorUiState.TAB_NOISE, model.state.value.adjustTab)
        model.onEvent(ColorEvent.SpatialNr(SpatialNrControl.LUMA, 40f))
        model.onEvent(ColorEvent.SpatialNr(SpatialNrControl.CHROMA, 125f))
        model.onEvent(ColorEvent.SpatialNrRadius(NrRadius.LARGE))
        val nr = SpatialNoiseReduction(lumaThreshold = 40f, chromaThreshold = 100f, radius = NrRadius.LARGE)
        assertEquals(nr, session.grade("c1").spatialNr)
        assertEquals(nr, model.state.value.spatialNr)
        assertEquals("降噪半径：大", session.history.value.undoLabel)
        // A keyed node 1 keeps one noise reduction: changing it adds no keyframe.
        model.onEvent(ColorEvent.ToggleGradeKeyframe)
        val keyframes = session.grade("c1").keyframes.size
        model.onEvent(ColorEvent.SpatialNr(SpatialNrControl.AMOUNT, 60f))
        assertEquals(60f, session.grade("c1").spatialNr.amount, 0f)
        assertEquals(keyframes, session.grade("c1").keyframes.size)
        assertEquals("降噪：强度", session.history.value.undoLabel)
        model.onEvent(ColorEvent.ResetSpatialNr)
        assertEquals(SpatialNoiseReduction.OFF, session.grade("c1").spatialNr)
        assertEquals("重置降噪", session.history.value.undoLabel)
    }

    @Test
    fun `mid detail is the clip grade's, one value whatever the keyframes, coalesced while dragging`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.MidDetail(30f))
        model.onEvent(ColorEvent.MidDetail(45f))
        assertEquals(45f, session.grade("c1").midDetail, 0f)
        assertEquals(45f, model.state.value.midDetail, 0f)
        assertEquals("中间调细节", session.history.value.undoLabel)
        session.undo()
        assertEquals("one drag, one undo step", 0f, session.grade("c1").midDetail, 0f)
        // A keyed node 1 keeps one Mid/Detail: it is not keyframed and survives the keyframe.
        model.onEvent(ColorEvent.MidDetail(-40f))
        model.onEvent(ColorEvent.ToggleGradeKeyframe)
        val keys = session.grade("c1").keyframes.size
        model.onEvent(ColorEvent.MidDetail(-60f))
        assertEquals(-60f, session.grade("c1").midDetail, 0f)
        assertEquals(keys, session.grade("c1").keyframes.size)
        assertEquals(-60f, session.grade("c1").at(10).midDetail, 0f)
        model.onEvent(ColorEvent.MidDetail(300f))
        assertEquals(100f, session.grade("c1").midDetail, 0f)
    }

    @Test
    fun `temporal noise reduction is the clip grade's, one value whatever the keyframes, coalesced while dragging`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.TemporalNr(20f))
        model.onEvent(ColorEvent.TemporalNr(60.4f))
        assertEquals(60, session.grade("c1").temporalNr)
        assertEquals(60, model.state.value.temporalNr)
        assertEquals("时域降噪", session.history.value.undoLabel)
        session.undo()
        assertEquals("one drag, one undo step", 0, session.grade("c1").temporalNr)
        model.onEvent(ColorEvent.TemporalNr(40f))
        model.onEvent(ColorEvent.ToggleGradeKeyframe)
        model.onEvent(ColorEvent.TemporalNr(70f))
        assertEquals(70, session.grade("c1").at(10).temporalNr)
        model.onEvent(ColorEvent.TemporalNr(300f))
        assertEquals(100, session.grade("c1").temporalNr)
    }

    /** A channel-rotating LUT (red comes out blue), a broken file, and nothing else. */
    private val swapCube = cubeText(2, title = "Swap") { r, g, b -> floatArrayOf(g, b, r) }
    private val lutFiles = object : DocumentRepository {
        override suspend fun read(uri: String, maxBytes: Int): PickedDocument = when (uri) {
            "content://swap" -> PickedDocument("swap.cube", swapCube.toByteArray())
            "content://bad" -> PickedDocument("bad.cube", "LUT_3D_SIZE 2\n".toByteArray())
            else -> throw DocumentFailure("无法读取文件：gone.cube")
        }
    }

    @Test
    fun `an imported LUT joins the gallery and lands on the selected clip at full strength`() = runTest {
        val luts = InMemoryLutRepository()
        val (session, model) = colorStage(luts = luts, documents = lutFiles)
        model.onEvent(ColorEvent.SelectLook(Look.TEAL_ORANGE))

        model.onEvent(ColorEvent.ImportLut("content://swap"))
        advanceUntilIdle()
        val id = luts.library.value.single().id
        assertEquals(LutRef(id, "Swap"), session.grade("c1").lut)
        assertEquals("the LUT takes the look's place", Look.NONE, session.grade("c1").look)
        assertEquals(100, session.grade("c1").lookStrength)
        assertEquals("LUT：Swap", session.history.value.undoLabel)
        assertEquals("已导入 LUT：Swap（3D · 2³），已应用到“c1”", model.state.value.toast)
        assertFalse(model.state.value.lutImporting)
        val tile = model.state.value.looks.last()
        assertEquals(id, tile.lut?.id)
        assertEquals("Swap", tile.label)
        assertNotNull("previewed on the frame", tile.pixels)
        assertNull(model.state.value.lutMissing)

        // Picking it again keeps a lowered strength; a built-in look takes the slot back.
        model.onEvent(ColorEvent.LookStrength(40f))
        model.onEvent(ColorEvent.SelectLut(id))
        assertEquals(40, session.grade("c1").lookStrength)
        model.onEvent(ColorEvent.SelectLook(Look.MONO))
        assertNull(session.grade("c1").lut)
        assertEquals(Look.MONO, session.grade("c1").look)

        model.onEvent(ColorEvent.ImportLut("content://swap"))
        advanceUntilIdle()
        assertEquals("LUT 已在库中：Swap，已应用到“c1”", model.state.value.toast)
        assertEquals(1, luts.library.value.size)
        model.onEvent(ColorEvent.ImportLut("content://bad"))
        advanceUntilIdle()
        assertEquals("无法导入 LUT：LUT 没有数据", model.state.value.toast)
        model.onEvent(ColorEvent.ImportLut("content://gone"))
        advanceUntilIdle()
        assertEquals("无法读取文件：gone.cube", model.state.value.toast)
        assertFalse(model.state.value.lutImporting)
    }

    @Test
    fun `a grabbed still joins the gallery, grades another clip and wipes against the picture`() = runTest {
        val gallery = InMemoryGalleryRepository()
        val (session, model) = colorStage(gallery = gallery)
        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 0.3f))
        model.onEvent(ColorEvent.GrabStill)
        advanceUntilIdle()
        val still = session.document.value!!.stills.single()
        assertEquals("静帧 1", still.label)
        assertEquals("c1", still.clipName)
        assertEquals(0.3f, still.grade.saturation, 0f)
        assertEquals("已抓取静帧 1：c1 · 00:00:00:00", model.state.value.toast)
        assertEquals("抓取静帧", session.history.value.undoLabel)
        assertEquals(still.id, model.state.value.selectedStillId)
        assertEquals(listOf("c1 · 00:00:00:00"), model.state.value.stills.map { it.detail })
        assertNotNull("its tile shows its picture", model.state.value.stills.single().pixels)
        val picture = gallery.load(session.document.value!!.id, still.image, 960)!!
        assertEquals("16:9, letterboxed", 960 to 540, picture.width to picture.height)
        assertFalse(model.state.value.grabbing)

        model.onEvent(ColorEvent.SelectClip("c2"))
        model.onEvent(ColorEvent.ApplyStill)
        assertEquals(0.3f, session.grade("c2").saturation, 0f)
        assertEquals("应用静帧 1的调色", session.history.value.undoLabel)
        assertEquals("已把静帧 1的调色应用到“c2”", model.state.value.toast)

        // The wipe and before/after share the split: one at a time.
        model.onEvent(ColorEvent.SetCompare(true))
        model.onEvent(ColorEvent.SetWipe(true))
        advanceUntilIdle()
        assertTrue(model.state.value.wipe)
        assertFalse(model.state.value.compare)
        assertNull(program.compareClipId)
        assertEquals(960, model.state.value.wipeImage?.width)
        assertEquals("静帧 1", model.state.value.wipeLabel)
        model.onEvent(ColorEvent.SetCompare(true))
        assertFalse(model.state.value.wipe)
        assertNull(model.state.value.wipeImage)

        model.onEvent(ColorEvent.DeleteStill(still.id))
        assertTrue(session.document.value!!.stills.isEmpty())
        assertNull(model.state.value.selectedStillId)
        assertEquals("已删除静帧 1", model.state.value.toast)
        session.undo()
        assertEquals("undo brings it back", listOf(still), session.document.value!!.stills)
        model.onEvent(ColorEvent.SetWipe(false))
        model.onEvent(ColorEvent.SelectClip("c1"))
        assertEquals(still.id, model.state.value.selectedStillId)
    }

    @Test
    fun `nothing to grab or apply is explained`() = runTest {
        val (_, model) = colorStage(project(media = listOf(first.copy(offline = true), second)))
        model.onEvent(ColorEvent.GrabStill)
        advanceUntilIdle()
        assertEquals("素材离线，无法抓取静帧", model.state.value.toast)
        model.onEvent(ColorEvent.ApplyStill)
        assertEquals("先在画廊中选择一张静帧", model.state.value.toast)
        model.onEvent(ColorEvent.SetWipe(true))
        assertEquals("先抓取或选择一张静帧", model.state.value.toast)
        assertFalse(model.state.value.wipe)
    }

    /** Remembers what was saved to Download/Reality Video Editor. */
    private class SavedFiles : DownloadsRepository {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String {
            files[fileName] = bytes
            return "Download/Reality Video Editor/$fileName"
        }
    }

    @Test
    fun `the clip's grade becomes a LUT in the library and in Download`() = runTest {
        val luts = InMemoryLutRepository()
        val saved = SavedFiles()
        val (session, model) = colorStage(luts = luts, downloads = saved)
        model.onEvent(ColorEvent.GenerateLut)
        advanceUntilIdle()
        assertEquals("当前片段没有调色，无需生成 LUT", model.state.value.toast)
        assertTrue(luts.library.value.isEmpty())

        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 0f))
        model.onEvent(ColorEvent.GenerateLut)
        advanceUntilIdle()
        val made = luts.library.value.single()
        assertEquals("c1 调色", made.name)
        assertEquals("3D · 33³", made.detail)
        assertEquals("已生成 LUT“c1 调色”（3D · 33³）：已加入 LUT 库，并保存到 Download/Reality Video Editor/c1 调色.cube", model.state.value.toast)
        assertFalse(model.state.value.lutGenerating)
        val file = CubeLutParser.parse(String(saved.files.getValue("c1 调色.cube")), "c1 调色.cube")
        assertEquals("the file is the LUT the library holds", made.id, file.id)
        val out = FloatArray(3).also { file.lut.apply(1f, 0f, 0f, it) }
        assertEquals("a saturation-0 grade turns red grey", out[0], out[1], 1e-5f)
        assertEquals("the grade is not changed by it", 0f, session.grade("c1").saturation, 0f)

        model.onEvent(ColorEvent.GenerateLut)
        advanceUntilIdle()
        assertEquals("LUT 库里已有同样的 LUT：c1 调色，并保存到 Download/Reality Video Editor/c1 调色.cube", model.state.value.toast)
    }

    @Test
    fun `a LUT is taken out of the library only once confirmed, and grades naming it show it missing`() = runTest {
        val luts = InMemoryLutRepository()
        val (session, model) = colorStage(luts = luts, documents = lutFiles)
        model.onEvent(ColorEvent.ImportLut("content://swap"))
        advanceUntilIdle()
        val id = luts.library.value.single().id

        model.onEvent(ColorEvent.RequestRemoveLut(id))
        assertEquals("Swap", model.state.value.lutRemoval?.name)
        model.onEvent(ColorEvent.CancelRemoveLut)
        assertNull(model.state.value.lutRemoval)
        assertEquals(1, luts.library.value.size)

        model.onEvent(ColorEvent.RequestRemoveLut(id))
        model.onEvent(ColorEvent.ConfirmRemoveLut)
        advanceUntilIdle()
        assertTrue(luts.library.value.isEmpty())
        assertNull(model.state.value.lutRemoval)
        assertEquals("已从 LUT 库移除“Swap”；当前片段的调色显示 LUT 缺失", model.state.value.toast)
        assertEquals("Swap", model.state.value.lutMissing)
        assertEquals("the grade still names it", id, session.grade("c1").lut?.id)
        assertTrue("its tile is gone", model.state.value.looks.none { it.lut != null })
    }

    @Test
    fun `a grade naming a LUT the library lacks says so until that LUT is imported`() = runTest {
        val id = CubeLutParser.parse(swapCube, "swap.cube").id
        val doc = project().let { d ->
            d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("c1")!!.copy(grade = ColorGrade(lut = LutRef(id, "Swap"), lookStrength = 100))))
        }
        val (session, model) = colorStage(doc, documents = lutFiles)
        assertEquals("Swap", model.state.value.lutMissing)
        assertEquals(id, model.state.value.grade.lut?.id)

        model.onEvent(ColorEvent.ImportLut("content://swap"))
        advanceUntilIdle()
        assertNull(model.state.value.lutMissing)
        assertEquals(LutRef(id, "Swap"), session.grade("c1").lut)
    }

    @Test
    fun `local grade versions — a new one copies the grade, switching shows each version's work, each change one undo step`() = runTest {
        val (session, model) = colorStage()
        assertEquals(listOf("版本 1"), model.state.value.gradeVersions)
        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 1f))
        val first = session.grade("c1")

        model.onEvent(ColorEvent.NewGradeVersion)
        assertEquals(listOf("版本 1", "版本 2"), model.state.value.gradeVersions)
        assertEquals(1, model.state.value.gradeVersion)
        assertEquals("已新建版本 2（复制自版本 1）", model.state.value.toast)
        assertEquals("新建调色版本", session.history.value.undoLabel)

        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 0f))
        val second = session.grade("c1")
        model.onEvent(ColorEvent.SelectGradeVersion(0))
        assertEquals(first, session.grade("c1"))
        assertEquals(first.exposure, model.state.value.grade.exposure)
        assertEquals("切换调色版本", session.history.value.undoLabel)
        model.onEvent(ColorEvent.StepGradeVersion(-1))
        assertEquals("已经是第一个调色版本", model.state.value.toast)
        model.onEvent(ColorEvent.StepGradeVersion(1))
        assertEquals(second, session.grade("c1"))
        model.onEvent(ColorEvent.StepGradeVersion(1))
        assertEquals("已经是最后一个调色版本", model.state.value.toast)

        model.onEvent(ColorEvent.DeleteGradeVersion)
        assertEquals(listOf("版本 1"), model.state.value.gradeVersions)
        assertEquals(first, session.grade("c1"))
        model.onEvent(ColorEvent.DeleteGradeVersion)
        assertEquals("片段只有一个调色版本", model.state.value.toast)
        session.undo()
        assertEquals(listOf("版本 1", "版本 2"), model.state.value.gradeVersions)
        assertEquals(second, session.grade("c1"))
        // The other clip has its own versions.
        model.onEvent(ColorEvent.SelectClip("c2"))
        assertEquals(listOf("版本 1"), model.state.value.gradeVersions)
    }

    @Test
    fun `= and − apply the grade of the clip one or two before, one undo step`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 1f))
        model.onEvent(ColorEvent.ApplyPriorGrade(1))
        assertEquals("前面没有片段", model.state.value.toast)
        model.onEvent(ColorEvent.SelectClip("c2"))
        model.onEvent(ColorEvent.ApplyPriorGrade(1))
        assertEquals(session.grade("c1"), session.grade("c2"))
        assertEquals("已应用前一个片段“c1”的调色", model.state.value.toast)
        assertEquals("应用前一片段的调色", session.history.value.undoLabel)
        model.onEvent(ColorEvent.ApplyPriorGrade(2))
        assertEquals("前面没有第 2 个片段", model.state.value.toast)
    }

    @Test
    fun `a copied grade is pasted onto another clip and reset returns that clip to neutral`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.PasteGrade)
        assertFalse("nothing copied yet", session.history.value.canUndo)

        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 1f))
        model.onEvent(ColorEvent.WheelMaster(GradeWheel.GAIN, 0.3f))
        model.onEvent(ColorEvent.CopyGrade)
        assertFalse(model.state.value.clipboardEmpty)
        assertEquals("已复制“c1”的调色", model.state.value.toast)

        model.onEvent(ColorEvent.SelectClip("c2"))
        model.onEvent(ColorEvent.PasteGrade)
        assertEquals(session.grade("c1"), session.grade("c2"))
        assertEquals("粘贴调色", session.history.value.undoLabel)
        assertEquals(session.grade("c1"), model.state.value.grade)

        model.onEvent(ColorEvent.ResetGrade)
        assertEquals(ColorGrade.NEUTRAL, session.grade("c2"))
        assertEquals("重置调色", session.history.value.undoLabel)
        assertEquals(listOf(true, false), model.state.value.clips.map { it.graded })

        model.onEvent(ColorEvent.ToastShown)
        assertNull(model.state.value.toast)
    }

    @Test
    fun `selecting a clip away from the playhead moves the program monitor to its start`() = runTest {
        val (_, model) = colorStage()

        model.onEvent(ColorEvent.SelectClip("c2"))

        assertEquals("c2", model.state.value.selectedClipId)
        assertEquals(3_000L, program.state.value.positionMs)
        assertEquals(90, model.state.value.playheadFrame)

        model.onEvent(ColorEvent.SelectClip("no-such-clip"))
        assertEquals("c2", model.state.value.selectedClipId)
    }

    @Test
    fun `compare shows the selected clip ungraded and follows the selection`() = runTest {
        val (_, model) = colorStage()

        model.onEvent(ColorEvent.SetCompare(true))
        assertTrue(model.state.value.compare)
        assertEquals("c1", program.compareClipId)

        model.onEvent(ColorEvent.SelectClip("c2"))
        assertEquals("c2", program.compareClipId)

        model.onEvent(ColorEvent.SetCompare(false))
        assertNull(program.compareClipId)
        model.onEvent(ColorEvent.SelectClip("c1"))
        assertNull("compare stays off", program.compareClipId)
    }

    @Test
    fun `scopes are measured on the graded frame after a short debounce`() = runTest {
        val (_, model) = colorStage()
        assertEquals(ScopeMode.WAVEFORM, model.state.value.scope?.mode)
        assertTrue("look previews are rendered", model.state.value.looks.all { it.pixels != null })

        model.onEvent(ColorEvent.Scope(ScopeMode.HISTOGRAM.ordinal))
        runCurrent()
        assertEquals(ScopeMode.HISTOGRAM, model.state.value.scopeMode)
        assertEquals("not re-measured before the debounce", ScopeMode.WAVEFORM, model.state.value.scope?.mode)

        advanceUntilIdle()
        assertEquals(ScopeMode.HISTOGRAM, model.state.value.scope?.mode)

        model.onEvent(ColorEvent.Scope(-1))
        advanceUntilIdle()
        assertEquals(ScopeMode.WAVEFORM, model.state.value.scope?.mode)
    }

    @Test
    fun `offline media under the playhead clears the scopes`() = runTest {
        val (_, model) = colorStage(project(media = listOf(first.copy(offline = true), second)))
        assertNull(model.state.value.scope)

        model.onEvent(ColorEvent.SelectClip("c2"))
        advanceUntilIdle()
        assertNotNull("the online clip is measured", model.state.value.scope)

        model.onEvent(ColorEvent.SelectClip("c1"))
        advanceUntilIdle()
        assertNull("offline media cannot be measured", model.state.value.scope)
        assertTrue(model.state.value.looks.all { it.pixels == null })
    }

    @Test
    fun `node 2 adds a circle window whose grade the controls then edit`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 1.2f))

        model.onEvent(ColorEvent.SelectNode(GradeNode.WINDOW))
        val state = model.state.value
        assertEquals(GradeNode.WINDOW, state.node)
        assertEquals(ColorUiState.TAB_WINDOW, state.adjustTab)
        assertEquals(PowerWindow(), session.grade("c1").window)
        assertEquals(PowerWindow(), state.window)
        assertEquals("node 2 starts neutral", ColorGrade.NEUTRAL, state.grade)
        assertEquals("已添加圆形窗口：节点 2 的调色只作用于窗口内", state.toast)
        assertEquals("添加窗口", session.history.value.undoLabel)

        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, -1f))
        model.onEvent(ColorEvent.WheelMaster(GradeWheel.GAIN, 0.2f))
        val grade = session.grade("c1")
        assertEquals(ColorGrade(exposure = -1f, gain = WheelValue(master = 0.2f)), grade.window!!.grade)
        assertEquals("node 1 is untouched", ColorGrade(saturation = 1.2f), grade.primary)
        assertEquals(grade.window!!.grade, model.state.value.grade)
        assertEquals("R 0.20  G 0.20  B 0.20", model.state.value.readouts.getValue(GradeWheel.GAIN))
        assertEquals("窗口 · 调色：亮部 Gain", session.history.value.undoLabel)
        assertTrue(model.state.value.primaryGraded)

        model.onEvent(ColorEvent.SelectNode(GradeNode.PRIMARY))
        assertEquals(ColorGrade(saturation = 1.2f), model.state.value.grade)
        model.onEvent(ColorEvent.SelectLook(Look.MONO))
        assertEquals(Look.MONO, session.grade("c1").look)
        assertEquals(Look.NONE, session.grade("c1").window!!.grade.look)
        assertEquals("LUT 风格：黑白", session.history.value.undoLabel)
    }

    /** Green left half, red right half: a 2:1 source letterboxed in the 16:9 project. */
    private val greenRed = FramePixels(12, 6, IntArray(72) { i -> if (i % 12 < 6) 0xFF19B233.toInt() else 0xFFB21919.toInt() })

    @Test
    fun `the eyedropper picks node 2's colour on the monitor, and the qualifier palette tunes, shows and clears it`() = runTest {
        previews.frame = greenRed
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_QUALIFIER))
        model.onEvent(ColorEvent.SetEyedropper(true))
        assertTrue(model.state.value.eyedropper)
        assertNotNull("the monitor takes the next tap", model.state.value.pickOverlay)

        model.onEvent(ColorEvent.PickColor(0.2f, 0.5f))
        advanceUntilIdle()

        val window = session.grade("c1").window!!
        assertEquals("a clip without node 2 gets one on the whole frame", WindowShape.FULL, window.shape)
        val q = window.qualifier!!
        assertEquals(HslQualifier.hue(0x19 / 255f, 0xB2 / 255f, 0x33 / 255f), q.hueCenter, 0.01f)
        val state = model.state.value
        assertFalse("one pick per press", state.eyedropper)
        assertNull(state.pickOverlay)
        assertEquals(GradeNode.WINDOW, state.node)
        assertEquals("限定器", state.secondaryLabel)
        assertEquals("已按所选颜色（色相 ${q.hueCenter.roundToInt()}°）建立限定器：节点 2 的调色只作用于这些颜色", state.toast)
        assertEquals("吸取颜色", session.history.value.undoLabel)

        model.onEvent(ColorEvent.Qualifier(QualifierControl.HUE_WIDTH, 40f))
        assertEquals(40f, session.grade("c1").window!!.qualifier!!.hueWidth)
        assertEquals("限定器：色相范围", session.history.value.undoLabel)
        model.onEvent(ColorEvent.SetQualifierInvert(true))
        assertTrue(session.grade("c1").window!!.qualifier!!.invert)
        model.onEvent(ColorEvent.SetHighlight(true))
        assertEquals("c1", program.highlightClipId)

        // A window shape joins the qualifier; removing it keeps the qualifier on the whole frame.
        model.onEvent(ColorEvent.SetWindowShape(WindowShape.CIRCLE))
        assertEquals("窗口 · 限定器", model.state.value.secondaryLabel)
        model.onEvent(ColorEvent.SetWindowShape(null))
        assertEquals(WindowShape.FULL, session.grade("c1").window!!.shape)
        assertEquals("已移除窗口，限定器保留在全画面", model.state.value.toast)
        // Clearing the qualifier keeps node 2 and its grade.
        model.onEvent(ColorEvent.RemoveQualifier)
        assertNull(session.grade("c1").window!!.qualifier)
        assertEquals("窗口", model.state.value.secondaryLabel)
        assertEquals("已移除限定器", model.state.value.toast)
        model.onEvent(ColorEvent.SetHighlight(false))
        assertNull(program.highlightClipId)
    }

    @Test
    fun `picker add and subtract refine the qualifier and stay on for the next colour`() = runTest {
        previews.frame = greenRed
        val (session, model) = colorStage()
        val red = floatArrayOf(0xB2 / 255f, 0x19 / 255f, 0x19 / 255f)
        val qualifier = { session.grade("c1").window!!.qualifier!! }
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_QUALIFIER))
        model.onEvent(ColorEvent.SetEyedropper(true, PickMode.ADD))
        assertFalse("adding needs a qualifier to add to", model.state.value.eyedropper)
        model.onEvent(ColorEvent.SetEyedropper(true))
        model.onEvent(ColorEvent.PickColor(0.2f, 0.5f))
        advanceUntilIdle()
        assertEquals(0f, qualifier().key(red[0], red[1], red[2]), 0f)

        model.onEvent(ColorEvent.SetEyedropper(true, PickMode.ADD))
        assertEquals(PickMode.ADD.hint, model.state.value.pickOverlay!!.hint)
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals("the red joins the green", 1f, qualifier().key(red[0], red[1], red[2]), 1e-4f)
        assertEquals(1f, qualifier().key(0x19 / 255f, 0xB2 / 255f, 0x33 / 255f), 1e-4f)
        assertEquals("限定器：加选颜色", session.history.value.undoLabel)
        assertEquals("已加选：选区扩大到包含这个颜色", model.state.value.toast)
        assertTrue("the picker stays on for the next colour", model.state.value.eyedropper)
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals("这个颜色已在选区里", model.state.value.toast)

        model.onEvent(ColorEvent.SetEyedropper(true, PickMode.SUBTRACT))
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals("the red leaves again", 0f, qualifier().key(red[0], red[1], red[2]), 0f)
        assertEquals("限定器：减选颜色", session.history.value.undoLabel)
        assertEquals("已减选：选区不再包含这个颜色", model.state.value.toast)
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals("这个颜色不在选区里", model.state.value.toast)

        // Without a qualifier there is nothing to subtract from: the picker stops.
        model.onEvent(ColorEvent.RemoveQualifier)
        advanceUntilIdle()
        assertFalse(model.state.value.eyedropper)
        assertNull(model.state.value.pickOverlay)
    }

    @Test
    fun `with 柔化 on, add and subtract feather the selection's edge to take a colour half in and out again`() = runTest {
        // Green on the left; on the right a teal-green at 165°, just past the picked green's hue range.
        previews.frame = FramePixels(12, 6, IntArray(72) { i -> if (i % 12 < 6) 0xFF19B233.toInt() else 0xFF1AB38C.toInt() })
        val (session, model) = colorStage()
        val teal = floatArrayOf(0x1A / 255f, 0xB3 / 255f, 0x8C / 255f)
        val qualifier = { session.grade("c1").window!!.qualifier!! }
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_QUALIFIER))
        model.onEvent(ColorEvent.SetEyedropper(true))
        model.onEvent(ColorEvent.PickColor(0.2f, 0.5f))
        advanceUntilIdle()
        val picked = qualifier()
        assertTrue("partly selected, in the soft edge", picked.key(teal[0], teal[1], teal[2]) in 0.05f..0.45f)

        model.onEvent(ColorEvent.SetFeatherPick(true))
        model.onEvent(ColorEvent.SetEyedropper(true, PickMode.ADD))
        assertEquals("点击画面中的颜色，让它落在选区的柔和边缘里", model.state.value.pickOverlay!!.hint)
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        // The edge lands on a whole degree, rounded up: at least half in.
        assertTrue("half in", qualifier().key(teal[0], teal[1], teal[2]) in 0.5f..0.6f)
        assertEquals("only the soft edge grew", picked.copy(hueSoft = qualifier().hueSoft), qualifier())
        assertEquals("限定器：柔化加选", session.history.value.undoLabel)
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals("这个颜色已在选区的柔和边缘里", model.state.value.toast)

        model.onEvent(ColorEvent.SetEyedropper(true, PickMode.SUBTRACT))
        model.onEvent(ColorEvent.PickColor(0.8f, 0.5f))
        advanceUntilIdle()
        assertEquals(0f, qualifier().key(teal[0], teal[1], teal[2]), 1e-4f)
        assertEquals("限定器：柔化减选", session.history.value.undoLabel)
        model.onEvent(ColorEvent.PickColor(0.2f, 0.5f))
        advanceUntilIdle()
        assertEquals("the green picked is in the core", "这个颜色在选区的核心里，请用减选", model.state.value.toast)
        model.onEvent(ColorEvent.SetFeatherPick(false))
        assertEquals(PickMode.SUBTRACT.hint, model.state.value.pickOverlay!!.hint)
    }

    @Test
    fun `a tap off the picture picks nothing, and leaving the qualifier palette puts the eyedropper away`() = runTest {
        previews.frame = greenRed
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_QUALIFIER))
        model.onEvent(ColorEvent.SetEyedropper(true))

        model.onEvent(ColorEvent.PickColor(0.5f, 0.99f))
        advanceUntilIdle()

        assertNull("the letterbox bar is not the picture", session.grade("c1").window)
        assertEquals("请点在画面上", model.state.value.toast)
        model.onEvent(ColorEvent.SetEyedropper(true))
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_WINDOW))
        assertFalse(model.state.value.eyedropper)
    }

    @Test
    fun `the window palette shapes, inverts, moves and removes the window`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.Window(WindowControl.SIZE, 1f))
        model.onEvent(ColorEvent.MoveWindow(0.1f, 0f))
        assertFalse("no window yet: nothing to change", session.history.value.canUndo)

        model.onEvent(ColorEvent.SetWindowShape(WindowShape.GRADIENT))
        assertEquals(PowerWindow(shape = WindowShape.GRADIENT), session.grade("c1").window)
        assertEquals(GradeNode.WINDOW, model.state.value.node)
        assertEquals("已添加渐变窗口：节点 2 的调色只作用于窗口内", model.state.value.toast)

        model.onEvent(ColorEvent.SetWindowShape(WindowShape.RECTANGLE))
        assertEquals(WindowShape.RECTANGLE, session.grade("c1").window!!.shape)
        assertEquals("窗口形状：矩形", session.history.value.undoLabel)

        model.onEvent(ColorEvent.Window(WindowControl.SIZE, 1.2f))
        model.onEvent(ColorEvent.Window(WindowControl.SIZE, 5f))
        assertEquals(2f, session.grade("c1").window!!.size, 0f)
        assertEquals("窗口：大小", session.history.value.undoLabel)

        model.onEvent(ColorEvent.SetWindowInvert(true))
        assertTrue(session.grade("c1").window!!.invert)
        assertEquals("窗口：反转", session.history.value.undoLabel)

        model.onEvent(ColorEvent.MoveWindow(0.3f, -0.2f))
        model.onEvent(ColorEvent.MoveWindow(0.9f, 0f))
        assertEquals("the centre stays on the picture", 1f, session.grade("c1").window!!.centerX, 0f)
        assertEquals(-0.2f, session.grade("c1").window!!.centerY, 1e-6f)
        assertEquals("移动窗口", session.history.value.undoLabel)
        session.undo()
        assertEquals("a drag is one undo step", PowerWindow(WindowShape.RECTANGLE, size = 2f, invert = true), session.grade("c1").window)
        session.redo()

        model.onEvent(ColorEvent.SetWindowShape(null))
        assertNull(session.grade("c1").window)
        assertEquals(GradeNode.PRIMARY, model.state.value.node)
        assertEquals("已移除窗口", model.state.value.toast)
        assertEquals("移除窗口", session.history.value.undoLabel)
    }

    @Test
    fun `the window outline shows while it is edited over the clip at the playhead`() = runTest {
        val placed = UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1").copy(
            transform = ClipTransform(scalePercent = 50, positionX = 480),
            effects = listOf(ClipEffect("fx", EffectType.MIRROR, 100, true)),
        )
        val doc = project().let { it.copy(timeline = it.timeline.replaceClip(placed)) }
        val (_, model) = colorStage(doc)
        assertNull(model.state.value.windowOverlay)

        model.onEvent(ColorEvent.SelectNode(GradeNode.WINDOW))
        val overlay = model.state.value.windowOverlay!!
        assertEquals(PowerWindow(), overlay.window)
        assertEquals("drawn where the clip shows its picture", ClipTransform(scalePercent = 50, positionX = 480), overlay.transform)
        assertTrue(overlay.mirrored)

        model.onEvent(ColorEvent.SelectNode(GradeNode.PRIMARY))
        assertNotNull("the window palette is open", model.state.value.windowOverlay)
        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_PRIMARIES))
        assertNull(model.state.value.windowOverlay)

        model.onEvent(ColorEvent.AdjustTab(ColorUiState.TAB_WINDOW))
        assertNotNull(model.state.value.windowOverlay)
        program.seekTo(4_000L)
        advanceUntilIdle()
        assertNull("the monitor shows another clip", model.state.value.windowOverlay)
    }

    @Test
    fun `the tracker keys the window along the subject it covers, one undo step`() = runTest {
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
        val (session, model) = colorStage(lumaFrames = source)
        assertEquals("先画一个窗口，再跟踪", model.state.value.trackUnavailable)
        model.onEvent(ColorEvent.TrackWindow)
        assertEquals("先画一个窗口，再跟踪", model.state.value.toast)

        model.onEvent(ColorEvent.SetWindowShape(WindowShape.CIRCLE))
        // Put the window on the disc at frame 0.
        val (x0, y0) = subject(0)
        model.onEvent(ColorEvent.Window(WindowControl.CENTER_X, (x0 / (w - 1) * 2 - 1).toFloat()))
        model.onEvent(ColorEvent.Window(WindowControl.CENTER_Y, (1 - y0 / (h - 1) * 2).toFloat()))
        model.onEvent(ColorEvent.Window(WindowControl.SIZE, 0.15f))
        assertNull(model.state.value.trackUnavailable)
        model.onEvent(ColorEvent.TrackWindow)
        advanceUntilIdle()
        assertEquals("the clip's stretch of its file", listOf(0L to 3000L), source.asked)
        val keys = session.grade("c1").window!!.keyframes
        assertEquals("跟踪窗口", session.history.value.undoLabel)
        assertTrue("${keys.size} keyframes", keys.size in 2..20)
        assertTrue(model.state.value.toast!!.startsWith("已跟踪窗口：${keys.size} 个关键帧"))
        assertFalse(model.state.value.tracking)
        val (x89, y89) = subject(89)
        val last = session.grade("c1").window!!.at(89)
        assertEquals((x89 / (w - 1) * 2 - 1).toFloat(), last.centerX, 0.012f)
        assertEquals((1 - y89 / (h - 1) * 2).toFloat(), last.centerY, 0.012f)
        session.undo()
        assertTrue("one undo step takes the track away", session.grade("c1").window!!.keyframes.isEmpty())
    }

    @Test
    fun `window keyframes follow a subject - moving a keyed window records it at the playhead`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.SetWindowShape(WindowShape.CIRCLE))
        assertEquals(0, model.state.value.windowKeys.count)
        assertEquals("添加关键帧后，窗口可以随时间移动，跟住移动的主体", model.state.value.windowKeys.hint)

        // Frame 10: key the window where it is.
        program.seekTo(Timecode.frameToSeekMs(10, 30))
        advanceUntilIdle()
        model.onEvent(ColorEvent.ToggleWindowKeyframe)
        assertEquals(listOf(10), session.grade("c1").window!!.keyframes.map { it.frame })
        assertEquals("已添加窗口关键帧 · 共 1 个", model.state.value.toast)
        assertEquals("窗口关键帧", session.history.value.undoLabel)
        assertTrue(model.state.value.windowKeys.atKeyframe)

        // Frame 70: dragging the window adds a keyframe there; the first one stays put.
        program.seekTo(Timecode.frameToSeekMs(70, 30))
        advanceUntilIdle()
        assertFalse(model.state.value.windowKeys.atKeyframe)
        model.onEvent(ColorEvent.MoveWindow(0.6f, 0.2f))
        val window = session.grade("c1").window!!
        assertEquals(listOf(10, 70), window.keyframes.map { it.frame })
        assertEquals(0f, window.keyframes[0].geometry.centerX, 0f)
        assertEquals(0.6f, window.keyframes[1].geometry.centerX, 1e-6f)
        assertEquals("the palette shows the window at the playhead", 0.6f, model.state.value.window!!.centerX, 1e-6f)
        assertEquals(0.6f, model.state.value.windowOverlay!!.window.centerX, 1e-6f)

        // Half way between them the window is half way across, and the panel follows the playhead.
        program.seekTo(Timecode.frameToSeekMs(40, 30))
        advanceUntilIdle()
        assertEquals(0.3f, model.state.value.window!!.centerX, 1e-5f)
        assertEquals(0.3f, model.state.value.windowOverlay!!.window.centerX, 1e-5f)
        assertEquals(2, model.state.value.windowKeys.count)
        assertTrue(model.state.value.windowKeys.hasPrevious && model.state.value.windowKeys.hasNext)

        model.onEvent(ColorEvent.PreviousWindowKeyframe)
        advanceUntilIdle()
        assertEquals(10, model.state.value.playheadFrame)
        model.onEvent(ColorEvent.ToggleWindowEase(arriving = false))
        assertEquals(0f, session.grade("c1").window!!.keyframes[0].ease.slopeOut, 0f)
        assertTrue(model.state.value.windowKeys.easeOut)
        model.onEvent(ColorEvent.ToggleWindowEase(arriving = true))
        assertEquals("第一个关键帧之前没有变化，无需缓入", model.state.value.toast)

        model.onEvent(ColorEvent.NextWindowKeyframe)
        advanceUntilIdle()
        assertEquals(70, model.state.value.playheadFrame)
        model.onEvent(ColorEvent.ToggleWindowKeyframe)
        model.onEvent(ColorEvent.PreviousWindowKeyframe)
        advanceUntilIdle()
        model.onEvent(ColorEvent.ToggleWindowKeyframe)
        assertEquals("已删除窗口关键帧，窗口不再移动", model.state.value.toast)
        assertTrue(session.grade("c1").window!!.keyframes.isEmpty())

        // With the playhead past the clips (the current clip follows it into a clip, not into a gap), c1's keyed window cannot change.
        session.undo()
        program.seekTo(Timecode.frameToSeekMs(200, 30))
        advanceUntilIdle()
        assertTrue(model.state.value.windowKeys.blocked)
        model.onEvent(ColorEvent.Window(WindowControl.SIZE, 1f))
        assertEquals("播放头不在片段内：移进片段再调整窗口", model.state.value.toast)
        assertEquals(1, session.grade("c1").window!!.keyframes.size)
    }

    @Test
    fun `grade keyframes - node 1 changes at the playhead and the controls follow the dissolve`() = runTest {
        val (session, model) = colorStage()
        assertEquals("添加关键帧后，调色可以随时间变化，跟上光线", model.state.value.gradeKeys.hint)

        // Frame 10: warm, keyed there.
        program.seekTo(Timecode.frameToSeekMs(10, 30))
        advanceUntilIdle()
        model.onEvent(ColorEvent.Primary(PrimaryControl.TEMPERATURE, 20f))
        model.onEvent(ColorEvent.ToggleGradeKeyframe)
        assertEquals(listOf(10), session.grade("c1").keyframes.map { it.frame })
        assertEquals("已添加调色关键帧：再到别处调整，调色会随时间变化", model.state.value.toast)
        assertEquals("调色关键帧", session.history.value.undoLabel)
        assertTrue(model.state.value.gradeKeys.atKeyframe)

        // Frame 70: cooling the picture adds a keyframe there; the first stays warm.
        program.seekTo(Timecode.frameToSeekMs(70, 30))
        advanceUntilIdle()
        model.onEvent(ColorEvent.Primary(PrimaryControl.TEMPERATURE, -20f))
        val keys = session.grade("c1").keyframes
        assertEquals(listOf(10, 70), keys.map { it.frame })
        assertEquals(listOf(20f, -20f), keys.map { it.grade.temperature })
        assertTrue("the node card says it changes", model.state.value.primaryGraded)

        // Half way the controls read half way, and the keyframe controls know where they are.
        program.seekTo(Timecode.frameToSeekMs(40, 30))
        advanceUntilIdle()
        assertEquals(0f, model.state.value.grade.temperature, 1e-4f)
        assertTrue(model.state.value.gradeKeys.hasPrevious && model.state.value.gradeKeys.hasNext)
        model.onEvent(ColorEvent.PreviousGradeKeyframe)
        advanceUntilIdle()
        assertEquals(10, model.state.value.playheadFrame)
        assertEquals(20f, model.state.value.grade.temperature, 1e-4f)
        model.onEvent(ColorEvent.ToggleGradeEase(arriving = false))
        assertEquals(0f, session.grade("c1").keyframes[0].ease.slopeOut, 0f)
        assertTrue(model.state.value.gradeKeys.easeOut)
        model.onEvent(ColorEvent.NextGradeKeyframe)
        advanceUntilIdle()
        assertEquals(70, model.state.value.playheadFrame)

        // Past the clips (in a gap the current clip stays c1), c1's keyed node 1 cannot change.
        program.seekTo(Timecode.frameToSeekMs(200, 30))
        advanceUntilIdle()
        assertTrue(model.state.value.gradeKeys.blocked)
        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 1f))
        assertEquals("播放头不在片段内：移进片段再调色", model.state.value.toast)
        assertEquals(listOf(0f, 0f), session.grade("c1").keyframes.map { it.grade.exposure })

        // Pasting the keyed grade onto the other clip keeps the keyframes' timing.
        model.onEvent(ColorEvent.CopyGrade)
        model.onEvent(ColorEvent.SelectClip("c2"))
        model.onEvent(ColorEvent.PasteGrade)
        assertEquals(listOf(10, 70), session.grade("c2").keyframes.map { it.frame })
    }

    @Test
    fun `copy, paste and reset take the window along, and a clip without one is edited on node 1`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.SelectNode(GradeNode.WINDOW))
        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, -2f))
        model.onEvent(ColorEvent.CopyGrade)

        model.onEvent(ColorEvent.SelectClip("c2"))
        assertEquals(GradeNode.PRIMARY, model.state.value.node)
        assertNull(model.state.value.window)

        model.onEvent(ColorEvent.PasteGrade)
        assertEquals(session.grade("c1"), session.grade("c2"))
        assertEquals(ColorGrade(exposure = -2f), session.grade("c2").window!!.grade)
        assertEquals(listOf(true, true), model.state.value.clips.map { it.graded })

        model.onEvent(ColorEvent.SelectNode(GradeNode.WINDOW))
        model.onEvent(ColorEvent.ResetGrade)
        assertEquals(ColorGrade.NEUTRAL, session.grade("c2"))
        assertEquals(GradeNode.PRIMARY, model.state.value.node)
    }

    @Test
    fun `the clip filter narrows the strip, keeps its numbering and hands the grade on from a hidden clip`() = runTest {
        val (session, model) = colorStage()
        // c1 (0–90) labelled red and graded, c2 (90–180) neither.
        session.edit("标记") { d ->
            d.copy(timeline = d.timeline.replaceClip(d.timeline.clip("c1")!!.copy(color = ClipColor.RED, grade = ColorGrade(temperature = 20f))))
        }
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(listOf(ClipColor.RED, null), stripClips.map { it.color })
            assertEquals("2 个片段", clipCountLabel)
            assertFalse(clipFiltered)
        }
        model.onEvent(ColorEvent.SelectClip("c1"))
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Ungraded))
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(listOf("c2"), stripClips.map { it.id })
            assertEquals("02", stripClips.single().index)
            // c1 is hidden: the strip's first clip is graded now, the playhead at its start.
            assertEquals("c2", selectedClipId)
            assertEquals(90, playheadFrame)
            assertEquals("1 / 2 个片段", clipCountLabel)
            assertTrue(clipFiltered)
            assertEquals(listOf("全部片段" to 2, "已调色片段" to 1, "未调色片段" to 1), clipFilterOptions.map { it.label to it.count })
            assertEquals(listOf(false, false, true), clipFilterOptions.map { it.selected })
            assertEquals(listOf("红色片段" to 1), clipColorFilters.map { it.label to it.count })
            // The monitor still has both clips to show.
            assertEquals(2, clips.size)
        }
        // A colour no clip carries empties the strip and says how to get back; the clip being graded stays.
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Colored(ClipColor.PINK)))
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(emptyList<GradeClipUi>(), stripClips)
            assertEquals("没有粉色片段，可在筛选中改回全部片段", stripMessage)
            assertEquals("c2", selectedClipId)
            assertEquals(listOf("红色片段" to 1, "粉色片段" to 0), clipColorFilters.map { it.label to it.count })
            assertEquals(listOf(false, true), clipColorFilters.map { it.selected })
        }
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.All))
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(listOf("c1", "c2"), stripClips.map { it.id })
            assertNull(stripMessage)
            assertEquals("c2", selectedClipId)
        }
    }

    @Test
    fun `flagged media shows its flags on the strip and the flagged clips filter keeps its clips`() = runTest {
        val (session, model) = colorStage()
        session.edit("旗标") { d -> d.copy(media = d.media.map { if (it.id == "v2") it.copy(flags = listOf(FlagColor.YELLOW)) else it }) }
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(listOf(emptyList(), listOf(FlagColor.YELLOW)), stripClips.map { it.flags })
            assertEquals(listOf("已标旗标片段" to 1, "黄色旗标片段" to 1), clipFlagFilters.map { it.label to it.count })
        }
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Flagged(FlagColor.YELLOW)))
        advanceUntilIdle()
        with(model.state.value) {
            assertEquals(listOf("c2"), stripClips.map { it.id })
            assertEquals("c2", selectedClipId)
            assertEquals(listOf(false, true), clipFlagFilters.map { it.selected })
        }
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Flagged(null)))
        advanceUntilIdle()
        assertEquals(listOf("c2"), model.state.value.stripClips.map { it.id })
    }

    @Test
    fun `previous and next clip walk the strip from each clip's first frame and follow the filter`() = runTest {
        val (session, model) = colorStage()
        // The strip: c1 (0–90, red), top (60–150 on V2), c2 (90–180), c3 (180–270, red).
        session.edit("片段") { d ->
            val c1 = d.timeline.clip("c1")!!
            val red = c1.copy(color = ClipColor.RED)
            d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.map { if (it.id == "c1") red else it } +
                c1.copy(id = "top", start = 60, trackId = Timeline.V2) + red.copy(id = "c3", start = 180)))
        }
        program.seekTo(Timecode.frameToSeekMs(70, 30))
        advanceUntilIdle()
        assertEquals("c1", model.state.value.selectedClipId)
        // The playhead is inside the V2 clip already: the step still starts it from its first frame.
        model.onEvent(ColorEvent.StepClip(forward = true))
        assertEquals("top", model.state.value.selectedClipId)
        assertEquals(Timecode.frameToSeekMs(60, 30), program.state.value.positionMs)
        model.onEvent(ColorEvent.StepClip(forward = true))
        assertEquals("c2", model.state.value.selectedClipId)
        assertEquals(Timecode.frameToSeekMs(90, 30), program.state.value.positionMs)
        model.onEvent(ColorEvent.StepClip(forward = false))
        model.onEvent(ColorEvent.StepClip(forward = false))
        assertEquals("c1", model.state.value.selectedClipId)
        assertEquals(0L, program.state.value.positionMs)
        model.onEvent(ColorEvent.StepClip(forward = false))
        assertEquals("已是片段条中的第一个片段", model.state.value.toast)
        assertEquals("c1", model.state.value.selectedClipId)
        // Red only: the strip is c1, c3, so the next clip after c1 is c3.
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Colored(ClipColor.RED)))
        model.onEvent(ColorEvent.StepClip(forward = true))
        assertEquals("c3", model.state.value.selectedClipId)
        assertEquals(Timecode.frameToSeekMs(180, 30), program.state.value.positionMs)
        model.onEvent(ColorEvent.StepClip(forward = true))
        assertEquals("已是片段条中的最后一个片段", model.state.value.toast)
    }

    @Test
    fun `the mini timeline draws the picture tracks, and a press grades the clip it lands on, a V2 clip too`() = runTest {
        val (session, model) = colorStage()
        // A V2 clip over c1 (60–150), switched off; c2 labelled red.
        session.edit("片段") { d ->
            val c1 = d.timeline.clip("c1")!!
            d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.map { if (it.id == "c2") it.copy(color = ClipColor.RED) else it } +
                c1.copy(id = "top", start = 60, trackId = Timeline.V2, enabled = false)))
        }
        advanceUntilIdle()
        with(model.state.value.miniTimeline) {
            assertEquals(2, lanes)
            assertEquals(180, durationFrames)
            // V2 is the top lane; the title on V2 is no picture clip.
            assertEquals(listOf(Triple("c1", 1, true), Triple("top", 0, false), Triple("c2", 1, false)), clips.map { Triple(it.id, it.lane, it.selected) })
            assertEquals(listOf(false, true, false), clips.map { it.dimmed })
            assertEquals(ClipColor.RED, clips.last().color)
        }
        // A press on the V2 clip at frame 100 grades it, though c2 lies under the playhead there too.
        model.onEvent(ColorEvent.Scrub(100, "top"))
        assertEquals("top", model.state.value.selectedClipId)
        assertEquals(Timecode.frameToSeekMs(100, 30), program.state.value.positionMs)
        assertEquals(100, model.state.value.playheadFrame)
        assertTrue(model.state.value.miniTimeline.clips.single { it.id == "top" }.selected)
        // Dragging on: once the playhead leaves the pressed clip, the clip under it is graded.
        model.onEvent(ColorEvent.Scrub(160))
        assertEquals("c2", model.state.value.selectedClipId)
        model.onEvent(ColorEvent.Scrub(500))
        assertEquals(179, model.state.value.playheadFrame)
        // The clip filter dims what it hides.
        model.onEvent(ColorEvent.SetClipFilter(ClipFilter.Colored(ClipColor.RED)))
        assertEquals(listOf(true, true, false), model.state.value.miniTimeline.clips.map { it.dimmed })
    }
    @Test
    fun `serial nodes - added after node 02, graded on their own, switched off and on, deleted, each one undo step`() = runTest {
        val (session, model) = colorStage()
        model.onEvent(ColorEvent.Primary(PrimaryControl.EXPOSURE, 1f))
        model.onEvent(ColorEvent.AddSerialNode)
        assertEquals(GradeNode.serial(0), model.state.value.node)
        assertEquals("添加节点 03", session.history.value.undoLabel)
        assertEquals("已添加节点 03：调整整个画面，在前面的节点之后", model.state.value.toast)
        assertEquals(listOf("03"), model.state.value.serialNodes.map { it.node.number })
        // The sliders now grade node 03; node 01 keeps its own.
        model.onEvent(ColorEvent.Primary(PrimaryControl.SATURATION, 0.5f))
        val grade = session.grade("c1")
        assertEquals(0.5f, grade.serial.single().grade.saturation, 0f)
        assertEquals(1f, grade.saturation, 0f)
        assertEquals(1f, grade.exposure, 0f)
        assertEquals("节点 03 · 调色：饱和度", session.history.value.undoLabel)
        assertEquals(0.5f, model.state.value.grade.saturation, 0f)
        assertTrue(model.state.value.serialNodes.single().graded)
        // Switched off: kept, but the picture skips it.
        model.onEvent(ColorEvent.ToggleNode(GradeNode.serial(0)))
        assertFalse(session.grade("c1").serial.single().enabled)
        assertEquals("已停用 · 画面跳过", model.state.value.serialNodes.single().summary)
        assertEquals("停用节点 03", session.history.value.undoLabel)
        model.onEvent(ColorEvent.ToggleNode(GradeNode.serial(0)))
        assertTrue(session.grade("c1").serial.single().enabled)
        model.onEvent(ColorEvent.ToggleNode(GradeNode.PRIMARY))
        assertEquals("节点 01、02 不能停用：可以重置它们的调色", model.state.value.toast)
        // Up to nodes 03–06.
        repeat(3) { model.onEvent(ColorEvent.AddSerialNode) }
        assertEquals(4, session.grade("c1").serial.size)
        assertFalse(model.state.value.canAddNode)
        model.onEvent(ColorEvent.AddSerialNode)
        assertEquals("最多 6 个节点：节点 01、02 和串行节点 03–06", model.state.value.toast)
        // Deleting node 04 moves 05 and 06 up a place and selects 03.
        model.onEvent(ColorEvent.DeleteNode(GradeNode.serial(1)))
        assertEquals(3, session.grade("c1").serial.size)
        assertEquals(GradeNode.serial(0), model.state.value.node)
        assertEquals("删除节点 04", session.history.value.undoLabel)
        // Undone back to node 01 alone (eight steps: delete, three adds, two switches, the saturation, the add), the selection
        // falls back to it.
        repeat(8) { session.undo() }
        assertEquals(emptyList<com.example.prismstudio.p40b43a88.domain.model.SerialNode>(), session.grade("c1").serial)
        assertEquals(GradeNode.PRIMARY, model.state.value.node)
        assertTrue(model.state.value.canAddNode)
    }
}
