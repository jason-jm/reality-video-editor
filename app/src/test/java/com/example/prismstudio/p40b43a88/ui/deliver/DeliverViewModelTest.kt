package com.example.prismstudio.p40b43a88.ui.deliver

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeRenderRepository
import com.example.prismstudio.p40b43a88.data.repository.LoudnessRepository
import com.example.prismstudio.p40b43a88.data.repository.RenderOutput
import com.example.prismstudio.p40b43a88.data.repository.RenderRepository
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderResolution
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinesUseCase
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.clearViewModel
import com.example.prismstudio.p40b43a88.ui.openSession
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import com.example.prismstudio.p40b43a88.domain.model.SourceColor
import com.example.prismstudio.p40b43a88.domain.model.ColorPrimaries
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliverViewModelTest {

    private val program = FakeProgramPlaybackRepository()
    private val ids = SequentialIds()
    private val clock = TestClock()

    private val footage = UiFixtures.video("v1", durationMs = 20_000)

    /** "Summer Trip": one 10 s clip (300 frames at 30 fps), default B站 1080p settings, file "Summer_Trip". */
    private fun project(
        markers: List<Marker> = emptyList(),
        clips: List<Clip> = listOf(UiFixtures.clip("c1", start = 0, frames = 300, assetId = "v1")),
        marks: TimelineMarks = TimelineMarks(),
    ) = UiFixtures.document(name = "Summer Trip", media = listOf(footage), clips = clips, markers = markers)
        .let { it.copy(timeline = it.timeline.copy(marks = marks)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.deliverStage(
        doc: ProjectDocument = project(),
        render: RenderRepository = FakeRenderRepository(),
    ): Pair<DefaultEditorSessionRepository, DeliverViewModel> {
        val session = openSession(doc, clock = clock)
        program.setProject(session.document.value)
        val model = DeliverViewModel(session, program, render, ids)
        advanceUntilIdle()
        return session to model
    }

    private val DefaultEditorSessionRepository.jobs: List<RenderJob> get() = document.value!!.jobs

    @Test
    fun `the Deliver timeline shades what renders, a press moves the playhead, and I or O set the range to In to Out`() = runTest {
        val clips = listOf(UiFixtures.clip("c1", start = 0, frames = 300, assetId = "v1"), UiFixtures.clip("c2", start = 60, frames = 90, assetId = "v1", trackId = Timeline.V2))
        val (session, model) = deliverStage(project(clips = clips))
        val s = model.state.value
        assertEquals(2, s.miniTimeline.lanes)
        assertEquals(listOf("c1" to 1, "c2" to 0), s.miniTimeline.clips.map { it.id to it.lane }.sortedBy { it.first })
        assertEquals(0 until 300, s.renderRange)
        assertEquals("渲染 00:00:00:00 → 00:00:10:00（整条时间线，00:00:10:00）", s.rangeLabel)
        assertEquals(false, s.hasMarks)

        model.onEvent(DeliverEvent.Seek(90))
        assertEquals(90, model.state.value.playheadFrame)
        assertEquals(3_000L, program.state.value.positionMs)
        model.onEvent(DeliverEvent.MarkIn)
        assertEquals(RenderRange.IN_TO_OUT, session.document.value!!.render.range)
        assertEquals("已标记时间线入点 00:00:03:00，渲染入点至出点", model.state.value.toast)
        model.onEvent(DeliverEvent.Seek(150))
        model.onEvent(DeliverEvent.MarkOut)
        assertEquals(TimelineMarks(90, 150), session.document.value!!.timeline.marks)
        assertEquals(90 until 150, model.state.value.renderRange)
        assertEquals("渲染 00:00:03:00 → 00:00:05:00（入点至出点，00:00:02:00）", model.state.value.rangeLabel)
        assertEquals("marks stay out of the edit history", false, session.history.value.canUndo)

        model.onEvent(DeliverEvent.ClearMarks)
        assertEquals(RenderRange.WHOLE_TIMELINE, session.document.value!!.render.range)
        assertEquals(0 until 300, model.state.value.renderRange)
        model.onEvent(DeliverEvent.ClearMarks)
        assertEquals("时间线上没有入出点", model.state.value.toast)
    }

    @Test
    fun `export current frame renders the frame at the playhead as a still named after the project`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(render = render)
        program.seekTo(4_000)
        model.onEvent(DeliverEvent.ExportStill)
        assertTrue("the button waits while the still renders", model.state.value.exportingStill)
        advanceUntilIdle()
        assertEquals(listOf("Summer Trip_00-00-04-00" to 120), render.stills)
        assertEquals("已导出当前帧：Summer Trip_00-00-04-00.png，保存在“图片/Reality Video Editor”", model.state.value.toast)
        assertFalse(model.state.value.exportingStill)

        // Past the end the last frame is taken; a failure says why.
        program.seekTo(60_000)
        render.failWith = "磁盘已满"
        model.onEvent(DeliverEvent.ExportStill)
        advanceUntilIdle()
        assertEquals("磁盘已满", model.state.value.toast)
        render.failWith = null
        model.onEvent(DeliverEvent.ExportStill)
        advanceUntilIdle()
        assertEquals("Summer Trip_00-00-09-29" to 299, render.stills.last())
    }

    @Test
    fun `an empty timeline has no frame to export`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(doc = project(clips = emptyList()), render = render)
        model.onEvent(DeliverEvent.ExportStill)
        assertEquals("时间线为空，没有可导出的画面", model.state.value.toast)
        assertTrue(render.stills.isEmpty())
    }

    @Test
    fun `a still export keeps the render queue waiting until the user starts it again`() = runTest {
        val render = FakeRenderRepository(stepMs = 80)
        val (session, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.ExportStill)
        model.onEvent(DeliverEvent.RenderAll)
        assertTrue(model.state.value.exportingStill)
        assertFalse(model.state.value.rendering)
        assertEquals(RenderStatus.QUEUED, session.jobs.single().status)
        advanceUntilIdle()
        assertTrue(render.rendered.isEmpty())

        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(RenderStatus.DONE, session.jobs.single().status)
        assertEquals(1, render.rendered.size)
    }

    @Test
    fun `missing subtitle files leave the movie accessible and the partial result survives reload`() = runTest {
        val renderer = object : RenderRepository {
            override suspend fun render(document: ProjectDocument, settings: RenderSettings, rangeStart: Int?, rangeEnd: Int?, onProgress: (Float) -> Unit) =
                RenderOutput("content://movie", 123, missingSubtitles = listOf("Trip.en.srt"))
        }
        val (session, model) = deliverStage(render = renderer)
        model.onEvent(DeliverEvent.ExportSubtitles(true))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        val job = session.jobs.single()
        assertEquals(RenderStatus.PARTIAL, job.status)
        assertTrue(model.state.value.jobs.single().error!!.contains("Trip.en.srt"))
        assertTrue(model.state.value.lastRenderSummary!!.contains("1 个字幕文件保存失败"))
        model.onEvent(DeliverEvent.Open(job.id))
        assertEquals("content://movie", model.state.value.openUri)
        val codec = com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
        assertEquals(job, codec.decode(codec.encode(session.document.value!!)).jobs.single())
    }

    @Test
    fun `a partial movie retains its file while one new job retries its saved settings and range`() = runTest {
        var calls = 0
        val renderer = object : RenderRepository {
            override suspend fun render(document: ProjectDocument, settings: RenderSettings, rangeStart: Int?, rangeEnd: Int?, onProgress: (Float) -> Unit): RenderOutput {
                calls++
                assertEquals(15, rangeStart)
                assertEquals(45, rangeEnd)
                return if (calls == 1) RenderOutput("content://partial", 90, missingSubtitles = listOf("captions.srt"), offlineMedia = listOf("Bars.mp4"))
                else RenderOutput("content://recovered", 180)
            }
        }
        val (session, model) = deliverStage(project(marks = TimelineMarks(15, 45)), renderer)
        model.onEvent(DeliverEvent.Range(1)); model.onEvent(DeliverEvent.AddJob); model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        val partial = session.jobs.single()
        assertEquals(RenderStatus.PARTIAL, partial.status)
        assertTrue(partial.errorMessage!!.contains("Bars.mp4")); assertTrue(partial.errorMessage!!.contains("captions.srt"))
        assertTrue(model.state.value.lastRenderSummary!!.contains("1 份成片缺少素材"))
        assertEquals("重新渲染", model.state.value.jobs.single().retryLabel)
        model.onEvent(DeliverEvent.ClearMarks)
        model.onEvent(DeliverEvent.Retry(partial.id)); model.onEvent(DeliverEvent.Retry(partial.id))
        assertEquals(2, session.jobs.size)
        val copy = session.jobs.last()
        assertEquals(partial.id, copy.retryOf); assertNull(copy.outputUri)
        assertEquals(partial.queuedRange, copy.queuedRange)
        assertEquals(partial.settings, copy.settings.copy(fileName = partial.settings.fileName))
        assertNotEquals(partial.fileName, copy.fileName)
        val codec = com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
        assertEquals(session.jobs, codec.decode(codec.encode(session.document.value!!)).jobs)
        model.onEvent(DeliverEvent.RenderAll); advanceUntilIdle()
        assertEquals(listOf(RenderStatus.PARTIAL, RenderStatus.DONE), session.jobs.map { it.status })
        assertEquals(partial, session.jobs.first()); assertEquals("content://recovered", session.jobs.last().outputUri)
        assertNull(model.state.value.jobs.first().retryLabel)
        model.onEvent(DeliverEvent.Open(partial.id)); assertEquals("content://partial", model.state.value.openUri)
    }

    @Test
    fun `publishes the delivery settings, the selected preset and a size estimate`() = runTest {
        val (_, model) = deliverStage()

        val state = model.state.value
        assertEquals(RenderSettings(fileName = "Summer_Trip", fps = 30), state.settings)
        assertEquals(listOf(RenderPreset.BILIBILI_1080), state.presets.filter { it.selected }.map { it.preset })
        // 1920×1080 × 30 fps × 0.1 bpp + 192 kb/s audio over 10 s ≈ 7.6 MiB.
        assertEquals("预计 7 MB · 00:00:10:00 · H.264 1080p 30 fps", state.estimate)
        assertEquals("恒定 30 fps，与时间线相同；慢放和低帧率素材重复前一帧补齐", state.frameRateNote)
        assertNull(state.rangeNote)
        assertFalse(state.hasJobs)
        assertTrue(state.nothingQueued)
        assertFalse(state.programEmpty)
    }

    /** Measures every file at -20 LUFS with a -12 dBTP true peak. */
    private class SteadyLoudness : LoudnessRepository {
        override suspend fun profile(uri: String) =
            LoudnessProfile(100, FloatArray(200) { 10.0.pow((-20 + 0.691) / 10).toFloat() }, FloatArray(200) { 0.25f })
    }

    @Test
    fun `the program loudness shows with the settings, and normalize audio says what the export will do`() = runTest {
        val doc = project(
            clips = listOf(
                UiFixtures.clip("c1", start = 0, frames = 300, assetId = "v1", linkId = "L"),
                UiFixtures.clip("s1", start = 0, frames = 300, trackId = Timeline.A1, assetId = "v1", linkId = "L"),
            ),
        )
        val session = openSession(doc, clock = clock)
        program.setProject(session.document.value)
        val model = DeliverViewModel(session, program, FakeRenderRepository(), ids, loudness = SteadyLoudness())
        advanceUntilIdle()
        assertEquals("节目响度 -20.0 LUFS · 真峰值 ≤ -12.0 dBTP", model.state.value.loudnessNote)
        assertEquals(0, model.state.value.loudnessIndex)

        model.onEvent(DeliverEvent.Loudness(1))
        assertEquals(-14f, session.document.value!!.render.loudnessTargetLufs)
        assertEquals(1, model.state.value.loudnessIndex)
        assertEquals("节目响度 -20.0 LUFS · 真峰值 ≤ -12.0 dBTP\n导出时整体 +6.0 dB → -14.0 LUFS", model.state.value.loudnessNote)

        model.onEvent(DeliverEvent.AddJob)
        assertTrue(session.jobs.single().detailLabel.endsWith(" · -14 LUFS"))

        model.onEvent(DeliverEvent.IncludeAudio(false))
        assertEquals("不导出音频", model.state.value.loudnessNote)
        model.onEvent(DeliverEvent.Loudness(0))
        assertNull(session.document.value!!.render.loudnessTargetLufs)
    }

    @Test
    fun `the subtitle file switch and its format are delivery settings named in the job`() = runTest {
        val (session, model) = deliverStage()
        model.onEvent(DeliverEvent.ExportSubtitles(true))
        assertTrue(session.document.value!!.render.exportSubtitles)
        model.onEvent(DeliverEvent.AddJob)
        assertTrue(session.jobs.single().detailLabel.endsWith(" · SRT"))
        model.onEvent(DeliverEvent.SubtitleFormat(1))
        assertEquals(SubtitleFileFormat.WEBVTT, session.document.value!!.render.subtitleFormat)
        model.onEvent(DeliverEvent.AddJob)
        assertTrue(session.jobs.last().detailLabel.endsWith(" · WebVTT"))
    }

    @Test
    fun `a timeline without sound has no program loudness`() = runTest {
        val session = openSession(project(), clock = clock)
        val model = DeliverViewModel(session, program, FakeRenderRepository(), ids, loudness = SteadyLoudness())
        advanceUntilIdle()
        assertEquals("时间线上没有声音", model.state.value.loudnessNote)
    }

    @Test
    fun `an empty timeline has no estimate and cannot be queued`() = runTest {
        val (_, model) = deliverStage(project(clips = emptyList()))
        assertEquals("时间线为空，暂无可渲染内容", model.state.value.estimate)
        assertTrue(model.state.value.programEmpty)

        model.onEvent(DeliverEvent.AddJob)

        assertEquals("时间线为空，无法加入渲染队列", model.state.value.toast)
        assertFalse(model.state.value.hasJobs)
    }

    @Test
    fun `choosing a preset applies its codec, raster and a file name derived from the project`() = runTest {
        val (session, model) = deliverStage()

        model.onEvent(DeliverEvent.SelectPreset(RenderPreset.YOUTUBE_4K))

        val state = model.state.value
        val settings = state.settings
        assertEquals(RenderPreset.YOUTUBE_4K, settings.preset)
        assertEquals("Summer_Trip_4K", settings.fileName)
        assertEquals(VideoCodec.H265, settings.codec)
        assertEquals(RenderResolution.UHD_4K, settings.resolution)
        assertEquals(RenderQuality.BEST, settings.quality)
        assertEquals(listOf(RenderPreset.YOUTUBE_4K), state.presets.filter { it.selected }.map { it.preset })
        assertEquals("the settings are stored with the project", settings, session.document.value!!.render)
        assertFalse("delivery settings are not part of the edit history", session.history.value.canUndo)
    }

    @Test
    fun `changing codec, resolution, frame rate or quality by hand drops the preset`() = runTest {
        val (_, model) = deliverStage()

        model.onEvent(DeliverEvent.Codec(1))
        assertEquals(VideoCodec.H265, model.state.value.settings.codec)
        assertNull(model.state.value.settings.preset)
        assertTrue(model.state.value.presets.none { it.selected })

        model.onEvent(DeliverEvent.SelectPreset(RenderPreset.BILIBILI_1080))
        model.onEvent(DeliverEvent.Resolution(0))
        assertEquals(RenderResolution.HD_720, model.state.value.settings.resolution)
        assertNull(model.state.value.settings.preset)

        model.onEvent(DeliverEvent.SelectPreset(RenderPreset.BILIBILI_1080))
        model.onEvent(DeliverEvent.Fps(99))
        assertEquals(60, model.state.value.settings.fps)
        assertEquals(3, model.state.value.fpsIndex)
        assertNull(model.state.value.settings.preset)
        assertEquals("高于时间线（30 fps）：每秒渲染 60 帧，素材帧不够时重复前一帧", model.state.value.frameRateNote)

        model.onEvent(DeliverEvent.SelectPreset(RenderPreset.BILIBILI_1080))
        model.onEvent(DeliverEvent.Quality(-1))
        assertEquals(RenderQuality.STANDARD, model.state.value.settings.quality)
        assertNull(model.state.value.settings.preset)
    }

    @Test
    fun `export video off renders the mix alone, which keeps its sound`() = runTest {
        val (session, model) = deliverStage()
        model.onEvent(DeliverEvent.IncludeAudio(false))
        model.onEvent(DeliverEvent.IncludeVideo(false))
        val settings = model.state.value.settings
        assertFalse(settings.includeVideo)
        assertTrue("the mix alone has its sound", settings.includeAudio)
        assertNull("an audio export is no preset", settings.preset)
        assertTrue(model.state.value.estimate.endsWith("仅音频 AAC 256 kbps"))
        assertNull("no picture, no frame rate", model.state.value.frameRateNote)
        model.onEvent(DeliverEvent.IncludeAudio(false))
        assertEquals("只导出音频时不能关闭声音", model.state.value.toast)
        assertTrue(model.state.value.settings.includeAudio)
        model.onEvent(DeliverEvent.AddJob)
        assertTrue("queued as the mix alone", session.jobs.single().fileName.endsWith(".m4a"))
        assertEquals("播放音频", model.state.value.jobs.single().openLabel)
        assertFalse(session.document.value!!.render.includeVideo)
        model.onEvent(DeliverEvent.IncludeVideo(true))
        assertTrue(model.state.value.settings.includeVideo)
    }

    @Test
    fun `the mix alone as WAV - its rate and depth, the note, the estimate and the file`() = runTest {
        val (session, model) = deliverStage()
        model.onEvent(DeliverEvent.IncludeVideo(false))
        model.onEvent(DeliverEvent.AudioFormat(1))
        val s = model.state.value.settings
        assertEquals(listOf("wav", "48 kHz", "24"), listOf(s.extension, RenderSettings.wavRateLabel(s.wavSampleRate), "${s.wavBits}"))
        assertEquals("仅音频 · WAV 48 kHz 24 位", s.detailLabel)
        assertTrue(model.state.value.estimate.endsWith("仅音频 WAV 48 kHz 24 位"))
        model.onEvent(DeliverEvent.WavRate(0))
        model.onEvent(DeliverEvent.WavBits(0))
        assertEquals(44_100 to 16, model.state.value.settings.wavSampleRate to model.state.value.settings.wavBits)
        assertEquals(
            "无损 PCM 立体声 .wav，每分钟约 10 MB，保存到“音乐/Reality Video Editor”。\n44.1 kHz 的声音逐采样原样写入，其他采样率的声音转换一次。",
            model.state.value.settings.audioFormatNote,
        )
        model.onEvent(DeliverEvent.WavBits(9))
        assertEquals("held in range", 24, model.state.value.settings.wavBits)
        model.onEvent(DeliverEvent.AddJob)
        assertTrue(session.jobs.single().fileName.endsWith(".wav"))
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat.WAV, session.document.value!!.render.audioFormat)
    }

    @Test
    fun `file name, audio and range edits keep the preset`() = runTest {
        val (_, model) = deliverStage()

        model.onEvent(DeliverEvent.FileName("x".repeat(80)))
        model.onEvent(DeliverEvent.IncludeAudio(false))
        model.onEvent(DeliverEvent.Range(7))

        val settings = model.state.value.settings
        assertEquals(60, settings.fileName.length)
        assertFalse(settings.includeAudio)
        assertEquals(RenderRange.IN_TO_OUT, settings.range)
        assertEquals(RenderPreset.BILIBILI_1080, settings.preset)
    }

    @Test
    fun `AddJob queues the current settings and numbers a repeated file name`() = runTest {
        val (session, model) = deliverStage()

        model.onEvent(DeliverEvent.AddJob)
        assertEquals("已加入渲染队列：Summer_Trip.mp4", model.state.value.toast)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)

        val jobs = model.state.value.jobs
        assertEquals(listOf("j-1", "j-2", "j-3"), jobs.map { it.id })
        assertEquals(listOf("Summer_Trip.mp4", "Summer_Trip (2).mp4", "Summer_Trip (3).mp4"), jobs.map { it.name })
        assertTrue(jobs.all { it.status == RenderStatus.QUEUED && it.statusLabel == "排队中" && it.cancellable })
        assertEquals("H.264 · 1080p · 30 fps · 高", jobs[0].detail)
        assertEquals("Summer_Trip (2)", session.jobs[1].settings.fileName)
        assertFalse(model.state.value.nothingQueued)
        assertFalse("the queue is not part of the edit history", session.history.value.canUndo)
    }
    @Test
    fun `the AddJob confirmation names the file that was actually queued`() = runTest {
        val (_, model) = deliverStage()

        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)

        assertEquals("Summer_Trip (2).mp4", model.state.value.jobs.last().name)
        assertEquals("已加入渲染队列：Summer_Trip (2).mp4", model.state.value.toast)
    }

    @Test
    fun `AddJob asks for a file name when it is blank`() = runTest {
        val (_, model) = deliverStage()

        model.onEvent(DeliverEvent.FileName("   "))
        model.onEvent(DeliverEvent.AddJob)

        assertEquals("请先填写文件名", model.state.value.toast)
        assertFalse(model.state.value.hasJobs)
    }

    @Test
    fun `individual clips queue one job per clip, each rendering that clip alone from its first frame`() = runTest {
        val clips = listOf(
            UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1", name = "Beach"),
            UiFixtures.clip("c2", start = 90, frames = 150, assetId = "v1", name = "City"),
        )
        val render = FakeRenderRepository()
        val (session, model) = deliverStage(project(clips = clips), render = render)
        assertNull("one file: no note", model.state.value.individualNote)
        model.onEvent(DeliverEvent.RenderMode(true))
        assertEquals(1, model.state.value.renderModeIndex)
        assertEquals("范围内的 2 个片段各存一个文件（共 8 秒）：只含片段本身和链接的声音，文件名后加片段名", model.state.value.individualNote)
        model.onEvent(DeliverEvent.AddJob)
        assertEquals("已把 2 个片段加入渲染队列，每个片段一个文件", model.state.value.toast)
        assertEquals(listOf("Summer_Trip_Beach.mp4", "Summer_Trip_City.mp4"), session.jobs.map { it.fileName })
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf(RenderStatus.DONE, RenderStatus.DONE), model.state.value.jobs.map { it.status })
        assertEquals(listOf(listOf("c1" to 0), listOf("c2" to 0)), render.documents.map { d -> d.timeline.clips.map { it.id to it.start } })
        assertEquals(listOf(90, 150), render.documents.map { it.durationFrames })
        // A clip deleted before its turn fails its job by name.
        model.onEvent(DeliverEvent.AddJob)
        session.update { d -> d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.filterNot { it.id == "c2" })) }
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals("失败：片段已不在时间线上", session.jobs.last().errorMessage)
    }

    @Test
    fun `RenderAll renders the queue in order and publishes each output`() = runTest {
        val render = FakeRenderRepository()
        val (session, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        program.play()

        model.onEvent(DeliverEvent.RenderAll)
        assertFalse("rendering pauses the program monitor", program.state.value.playing)
        assertTrue(model.state.value.rendering)
        assertTrue(model.state.value.nothingQueued)

        advanceTimeBy(25)
        var jobs = model.state.value.jobs
        assertEquals(RenderStatus.RENDERING, jobs[0].status)
        assertEquals(0.5f, jobs[0].progress, 0f)
        assertEquals("渲染中 50%", jobs[0].statusLabel)
        assertEquals(RenderStatus.QUEUED, jobs[1].status)

        advanceUntilIdle()
        jobs = model.state.value.jobs
        assertEquals(listOf(RenderStatus.DONE, RenderStatus.DONE), jobs.map { it.status })
        assertEquals(listOf("Summer_Trip", "Summer_Trip (2)"), render.rendered)
        assertEquals(
            listOf("content://fake/Summer_Trip.mp4", "content://fake/Summer_Trip (2).mp4"),
            session.jobs.map { it.outputUri },
        )
        assertEquals("H.264 · 1080p · 30 fps · 高 · 1 MB", jobs[0].detail)
        assertFalse(jobs[0].cancellable)
        assertFalse(model.state.value.rendering)
        assertEquals("已保存 2 个视频到 Movies/Reality Video Editor", model.state.value.lastRenderSummary)
    }

    @Test
    fun `an audio render names Music and a mixed batch reports both folders and subtitle outcomes`() = runTest {
        val renderer = object : RenderRepository {
            override suspend fun render(document: ProjectDocument, settings: RenderSettings, rangeStart: Int?, rangeEnd: Int?, onProgress: (Float) -> Unit): RenderOutput =
                if (settings.exportSubtitles) RenderOutput("content://movie", 100, subtitleUris = listOf("content://english"), missingSubtitles = listOf("Trip.fr.srt"))
                else RenderOutput("content://audio", 80)
        }
        val (_, model) = deliverStage(render = renderer)
        model.onEvent(DeliverEvent.IncludeVideo(false))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals("已保存 1 个音频文件到 Music/Reality Video Editor", model.state.value.lastRenderSummary)

        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.IncludeVideo(true))
        model.onEvent(DeliverEvent.ExportSubtitles(true))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        val summary = requireNotNull(model.state.value.lastRenderSummary)
        assertEquals(
            listOf("1 个字幕文件保存失败，请检查存储空间", "已保存 1 个视频到 Movies/Reality Video Editor", "已保存 1 个音频文件到 Music/Reality Video Editor", "已保存 1 个字幕文件到 Download/Reality Video Editor"),
            summary.lines(),
        )
    }

    @Test
    fun `a batch with only failures shows the count and points to the queue`() = runTest {
        val (_, model) = deliverStage(render = FakeRenderRepository(failWith = "编码器不可用"))
        repeat(2) { model.onEvent(DeliverEvent.AddJob) }
        model.onEvent(DeliverEvent.ToastShown)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals("2 个任务失败，请查看渲染队列", model.state.value.lastRenderSummary)
    }

    @Test
    fun `RenderAll while rendering does not start a second render loop`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)

        model.onEvent(DeliverEvent.RenderAll)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)

        assertTrue(model.state.value.rendering)
        assertEquals("jobs render one at a time", listOf(RenderStatus.RENDERING, RenderStatus.QUEUED), model.state.value.jobs.map { it.status })
        advanceUntilIdle()
        assertEquals(listOf("Summer_Trip", "Summer_Trip (2)"), render.rendered)
    }

    @Test
    fun `Render all with nothing queued says why, and while rendering that new jobs follow`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals("队列里没有等待渲染的任务，先点“加入渲染队列”", model.state.value.toast)
        assertFalse(model.state.value.rendering)

        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)
        model.onEvent(DeliverEvent.RenderAll)
        assertEquals("正在渲染，新加入的任务会接着渲染", model.state.value.toast)
        advanceUntilIdle()
        assertEquals(listOf("Summer_Trip"), render.rendered)
    }

    @Test
    fun `each job renders the timeline it was queued from, whichever is open when the queue runs`() = runTest {
        val render = RecordingRenderRepository()
        val landscape = project()
        val vertical = StoredTimeline(
            "tl-v", "竖屏版", landscape.format.copy(width = 1080, height = 1920, scaling = ClipScaling.FILL),
            landscape.timeline.copy(clips = listOf(UiFixtures.clip("c9", start = 0, frames = 90, assetId = "v1"))), landscape.mixer,
        )
        val (session, model) = deliverStage(doc = landscape.copy(timelines = listOf(vertical)), render = render)
        assertEquals("时间线 1 · 1920×1080", model.state.value.timelineLabel)
        assertTrue(model.state.value.severalTimelines)
        model.onEvent(DeliverEvent.AddJob)
        // Open the vertical one and queue it too; then go back before rendering.
        session.edit("切换时间线") { d -> TimelinesUseCase(SequentialIds()).open(d, "tl-v").doc }
        advanceUntilIdle()
        assertEquals("竖屏版 · 1080×1920", model.state.value.timelineLabel)
        model.onEvent(DeliverEvent.AddJob)
        session.edit("切换时间线") { d -> TimelinesUseCase(SequentialIds()).open(d, "tl-1").doc }
        advanceUntilIdle()
        assertEquals(listOf("tl-1", "tl-v"), session.jobs.map { it.timelineId })
        assertEquals(
            listOf("时间线 1 · H.264 · 1080p · 30 fps · 高", "竖屏版 · H.264 · 1080p · 30 fps · 高"),
            model.state.value.jobs.map { it.detail },
        )
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf(1920 to 1080, 1080 to 1920), render.documents.map { it.format.width to it.format.height })
        assertEquals(listOf(300, 90), render.documents.map { it.durationFrames })
        assertEquals("the open timeline stays open", "tl-1", session.document.value!!.timelineId)
    }

    @Test
    fun `all timelines go on the queue at once, named after them, and a deleted timeline's job fails`() = runTest {
        val render = RecordingRenderRepository()
        val landscape = project()
        val empty = StoredTimeline("tl-e", "空白", landscape.format, Timeline.EMPTY, landscape.mixer)
        val vertical = StoredTimeline("tl-v", "竖屏 版", landscape.format.copy(width = 1080, height = 1920), landscape.timeline, landscape.mixer)
        val (session, model) = deliverStage(doc = landscape.copy(timelines = listOf(vertical, empty)), render = render)
        model.onEvent(DeliverEvent.AddAllTimelines)
        assertEquals("已把 2 条时间线加入渲染队列", model.state.value.toast)
        assertEquals(listOf("Summer_Trip_时间线_1.mp4", "Summer_Trip_竖屏_版.mp4"), session.jobs.map { it.fileName })
        // The vertical timeline goes before the render starts: its job fails, the other renders.
        session.edit("删除") { d -> d.copy(timelines = d.timelines.filterNot { it.id == "tl-v" }) }
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf(RenderStatus.DONE, RenderStatus.FAILED), session.jobs.map { it.status })
        assertEquals("失败：时间线“竖屏 版”已删除", session.jobs[1].errorMessage)
        assertEquals("竖屏 版（已删除） · H.264 · 1080p · 30 fps · 高", model.state.value.jobs[1].detail)
        assertEquals(1, render.documents.size)
    }

    @Test
    fun `a failed render is marked failed, the queue moves on and the job can be retried`() = runTest {
        val render = FakeRenderRepository(failWith = "编码器不可用")
        val (_, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.ToastShown)

        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(45) // the first job fails after its fourth 10 ms step
        render.failWith = null
        advanceUntilIdle()

        var jobs = model.state.value.jobs
        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.DONE), jobs.map { it.status })
        assertEquals("失败：编码器不可用", jobs[0].error)
        assertEquals("失败", jobs[0].statusLabel)
        assertNull(jobs[1].error)
        assertEquals("1 个任务失败，请查看渲染队列\n已保存 1 个视频到 Movies/Reality Video Editor", model.state.value.lastRenderSummary)

        model.onEvent(DeliverEvent.Retry("j-1"))
        jobs = model.state.value.jobs
        assertEquals(RenderStatus.QUEUED, jobs[0].status)
        assertNull(jobs[0].error)
        assertEquals("已重新加入队列", model.state.value.toast)

        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf(RenderStatus.DONE, RenderStatus.DONE), model.state.value.jobs.map { it.status })
        assertEquals("已保存 1 个视频到 Movies/Reality Video Editor", model.state.value.lastRenderSummary)
        model.onEvent(DeliverEvent.ToastShown)
        assertEquals("已保存 1 个视频到 Movies/Reality Video Editor", model.state.value.lastRenderSummary)
    }

    @Test
    fun `finished renders can be cleared off the queue, failed and queued ones stay`() = runTest {
        val render = FakeRenderRepository(failWith = "编码器不可用")
        val (session, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(45)
        render.failWith = null
        advanceUntilIdle()
        model.onEvent(DeliverEvent.AddJob)
        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.DONE, RenderStatus.QUEUED), model.state.value.jobs.map { it.status })
        assertTrue(model.state.value.hasDone)

        model.onEvent(DeliverEvent.ClearDone)
        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.QUEUED), model.state.value.jobs.map { it.status })
        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.QUEUED), session.document.value!!.jobs.map { it.status })
        assertEquals("已清除 1 条渲染记录，已导出的文件保留在原位置", model.state.value.toast)
        assertNull(model.state.value.lastRenderSummary)
        assertFalse(model.state.value.hasDone)
        model.onEvent(DeliverEvent.ToastShown)
        model.onEvent(DeliverEvent.ClearDone)
        assertNull("nothing to clear, nothing said", model.state.value.toast)
    }

    @Test
    fun `cancelling the running job stops the render and removes it, the rest stays queued`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)

        model.onEvent(DeliverEvent.Cancel("j-1"))
        assertEquals("已取消", model.state.value.toast)
        advanceUntilIdle()

        assertEquals(listOf("j-2"), model.state.value.jobs.map { it.id })
        assertEquals(RenderStatus.QUEUED, model.state.value.jobs.single().status)
        assertFalse(model.state.value.rendering)
        assertTrue("nothing was published", render.rendered.isEmpty())
    }

    @Test
    fun `cancelling a queued job leaves the running render alone`() = runTest {
        val render = FakeRenderRepository()
        val (_, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)

        model.onEvent(DeliverEvent.Cancel("j-2"))
        advanceUntilIdle()

        assertEquals(listOf("j-1"), model.state.value.jobs.map { it.id })
        assertEquals(RenderStatus.DONE, model.state.value.jobs.single().status)
        assertEquals(listOf("Summer_Trip"), render.rendered)
    }

    @Test
    fun `in-to-out renders the timeline's in to out, each job keeps its own range`() = runTest {
        val render = RecordingRenderRepository()
        // Markers no longer make the range: the timeline's In / Out do.
        val markers = listOf(Marker("m-1", 0, "标记 1"), Marker("m-2", 200, "标记 2"))
        val (_, model) = deliverStage(project(markers = markers, marks = TimelineMarks(30, 120)), render)
        model.onEvent(DeliverEvent.AddJob)

        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))

        assertEquals("范围：00:00:01:00 → 00:00:04:00（时间线入点到出点）", model.state.value.rangeNote)
        assertEquals("预计 2 MB · 00:00:03:00 · H.264 1080p 30 fps", model.state.value.estimate)

        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf<Pair<Int?, Int?>>(null to null, 30 to 120), render.ranges)
    }

    @Test
    fun `queued mark ranges survive clearing the marks and reopening the project`() = runTest {
        val render = RecordingRenderRepository()
        val (session, model) = deliverStage(project(marks = TimelineMarks(30, 120)), render)
        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.ClearMarks)
        model.onEvent(DeliverEvent.Seek(150))
        model.onEvent(DeliverEvent.MarkIn)
        model.onEvent(DeliverEvent.Seek(210))
        model.onEvent(DeliverEvent.MarkOut)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.ClearMarks)

        val saved = ProjectJsonCodec.encode(requireNotNull(session.document.value))
        val (_, reopened) = deliverStage(ProjectJsonCodec.decode(saved), render)
        assertEquals(listOf(
            "入点 00:00:01:00 → 出点 00:00:04:00（00:00:03:00）",
            "入点 00:00:05:00 → 出点 00:00:07:00（00:00:02:00）",
        ), reopened.state.value.jobs.map { it.rangeLabel })
        reopened.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()

        assertEquals(listOf(30 to 120, 150 to 210), render.ranges)
    }

    @Test
    fun `a disabled individual clip fails its job while the next picture still renders`() = runTest {
        val clips = listOf(
            UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1"),
            UiFixtures.clip("c2", start = 90, frames = 60, assetId = "v1"),
        )
        val render = RecordingRenderRepository()
        val (session, model) = deliverStage(project(clips = clips), render)
        model.onEvent(DeliverEvent.RenderMode(true))
        model.onEvent(DeliverEvent.AddJob)
        session.edit("停用片段") { it.copy(timeline = it.timeline.copy(clips = listOf(clips[0].copy(enabled = false), clips[1]))) }
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.DONE), session.jobs.map { it.status })
        assertTrue(session.jobs.first().errorMessage.orEmpty().contains("停用"))
        assertEquals(listOf("c2"), render.documents.single().timeline.clips.map { it.id })
        assertEquals("1 个任务失败，请查看渲染队列\n已保存 1 个视频到 Movies/Reality Video Editor", model.state.value.lastRenderSummary)
    }

    @Test
    fun `enqueue all keeps the distinct marks of each timeline even if they are later cleared`() = runTest {
        val render = RecordingRenderRepository()
        val primary = project(marks = TimelineMarks(30, 90))
        val second = StoredTimeline("tl-other", "Second", primary.format, primary.timeline.copy(marks = TimelineMarks(outFrame = 60)), primary.mixer)
        val (session, model) = deliverStage(primary.copy(timelines = listOf(second)), render)
        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))
        model.onEvent(DeliverEvent.AddAllTimelines)
        model.onEvent(DeliverEvent.ClearMarks)
        session.edit("切换时间线") { d -> TimelinesUseCase(SequentialIds()).open(d, second.id).doc }
        model.onEvent(DeliverEvent.ClearMarks)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()

        assertEquals(listOf(30 to 90, 0 to 60), render.ranges)
    }

    @Test
    fun `a shortened timeline fails its now invalid range and the next whole timeline job still runs`() = runTest {
        val render = RecordingRenderRepository()
        val (session, model) = deliverStage(project(marks = TimelineMarks(30, 120)), render)
        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))
        model.onEvent(DeliverEvent.AddJob)
        val shorter = project(clips = listOf(UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1")))
        session.edit("修剪") { it.copy(timeline = shorter.timeline) }
        model.onEvent(DeliverEvent.Range(RenderRange.WHOLE_TIMELINE.ordinal))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()

        assertEquals(listOf(RenderStatus.FAILED, RenderStatus.DONE), session.jobs.map { it.status })
        assertTrue(session.jobs.first().errorMessage.orEmpty().contains("排队时的导出范围超出当前时间线"))
        assertEquals(listOf<Pair<Int?, Int?>>(null to null), render.ranges)

        session.edit("恢复片段") { it.copy(timeline = project().timeline) }
        model.onEvent(DeliverEvent.Retry(session.jobs.first().id))
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(30 to 120, render.ranges.last())
    }

    @Test
    fun `unmarked fallback and a single In point are fixed when added to the queue`() = runTest {
        val render = RecordingRenderRepository()
        val (session, model) = deliverStage(project(), render)
        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.Seek(180))
        model.onEvent(DeliverEvent.MarkIn)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.Seek(200))
        model.onEvent(DeliverEvent.MarkOut)
        val extended = project(clips = listOf(UiFixtures.clip("c1", start = 0, frames = 360, assetId = "v1")))
        session.edit("延长片段") { it.copy(timeline = extended.timeline.copy(marks = it.timeline.marks)) }
        val saved = ProjectJsonCodec.encode(requireNotNull(session.document.value))
        val (_, reopened) = deliverStage(ProjectJsonCodec.decode(saved), render)
        assertEquals("整条时间线", reopened.state.value.jobs.first().rangeLabel)
        reopened.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf<Pair<Int?, Int?>>(null to null, 180 to 300), render.ranges)
    }

    @Test
    fun `in-to-out with one mark runs to the end, with none renders the whole timeline`() = runTest {
        val outOnly = RecordingRenderRepository()
        val (_, marked) = deliverStage(project(marks = TimelineMarks(outFrame = 90)), outOnly)
        marked.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))
        assertEquals("范围：00:00:00:00 → 00:00:03:00（时间线入点到出点）", marked.state.value.rangeNote)
        marked.onEvent(DeliverEvent.AddJob)
        marked.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf<Pair<Int?, Int?>>(0 to 90), outOnly.ranges)

        val render = RecordingRenderRepository()
        val (_, model) = deliverStage(project(markers = listOf(Marker("m-1", 30, "标记 1"))), render)

        model.onEvent(DeliverEvent.Range(RenderRange.IN_TO_OUT.ordinal))

        assertEquals("时间线上没有入出点，将渲染整条时间线：在剪辑页按 I / O 或用飞梭台的入点、出点键标记", model.state.value.rangeNote)
        assertEquals("预计 7 MB · 00:00:10:00 · H.264 1080p 30 fps", model.state.value.estimate)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        assertEquals(listOf<Pair<Int?, Int?>>(null to null), render.ranges)
    }

    @Test
    fun `a finished output can be opened until the request is handled`() = runTest {
        val (_, model) = deliverStage()
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.Open("j-1"))
        assertNull("nothing rendered yet", model.state.value.openUri)

        model.onEvent(DeliverEvent.Cancel("j-2"))
        model.onEvent(DeliverEvent.RenderAll)
        advanceUntilIdle()
        model.onEvent(DeliverEvent.Open("j-1"))
        assertEquals("content://fake/Summer_Trip.mp4", model.state.value.openUri)

        model.onEvent(DeliverEvent.OpenHandled)
        assertNull(model.state.value.openUri)
        model.onEvent(DeliverEvent.OpenFailed())
        assertEquals("没有可播放视频的应用，可在系统文件管理器的 Movies 目录查找", model.state.value.toast)
        // Release round R9: a render the app may no longer hand out (left by an earlier install) says so instead of closing the app.
        model.onEvent(DeliverEvent.ToastShown)
        model.onEvent(DeliverEvent.OpenFailed(unreachable = true))
        assertEquals("打不开这个文件：它可能已被移动或删除，或是之前安装的 Reality Video Editor 渲染的", model.state.value.toast)
    }

    @Test
    fun `clearing the ViewModel during a render puts the interrupted job back in the queue`() = runTest {
        // Like Media3RenderRepository, the render suspends on another dispatcher, so the cancellation lands later.
        val render = DispatcherHoppingRenderRepository(StandardTestDispatcher(testScheduler))
        val (session, model) = deliverStage(render = render)
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)
        assertEquals(RenderStatus.RENDERING, session.jobs.single().status)

        clearViewModel(model)
        advanceUntilIdle()

        val job = session.jobs.single()
        assertEquals(RenderStatus.QUEUED, job.status)
        assertNull(job.errorMessage)
        assertEquals(0f, job.progress, 0f)
    }

    // ViewModel.clear() closes viewModelScope before onCleared(). When the render's suspension point resumes
    // in place on cancellation (FakeRenderRepository's delay on Main.immediate), renderAll's finally block has
    // already reset runningId by the time onCleared() looks at it, and the job is left RENDERING for good.
    @Test
    fun `clearing the ViewModel puts the job back in the queue even when the render stops at once`() = runTest {
        val (session, model) = deliverStage(render = FakeRenderRepository())
        model.onEvent(DeliverEvent.AddJob)
        model.onEvent(DeliverEvent.RenderAll)
        advanceTimeBy(15)
        assertEquals(RenderStatus.RENDERING, session.jobs.single().status)

        clearViewModel(model)
        advanceUntilIdle()

        assertEquals(RenderStatus.QUEUED, session.jobs.single().status)
    }

    @Test
    fun `HDR clips show the tone mapping rows, whose choices are project settings`() = runTest {
        val pq = footage.copy(color = SourceColor(ColorPrimaries.BT2020, ColorTransfer.PQ, 10))
        val (session, model) = deliverStage(UiFixtures.document(name = "Summer Trip", media = listOf(pq), clips = listOf(UiFixtures.clip("c1", start = 0, frames = 300, assetId = "v1"))))
        var state = model.state.value
        assertEquals("含 HDR 素材（PQ 1 段）：时间线为 Rec.709 Gamma 2.4（SDR），导出时按「亮度映射」色调映射，PQ 母版峰值按 1000 nits", state.hdrNote)
        assertTrue(state.pqPresent)
        model.onEvent(DeliverEvent.ToneMap(ToneMapping.HUE))
        model.onEvent(DeliverEvent.PqPeak(2))
        assertEquals(ColorManagement(ToneMapping.HUE, 4000), session.document.value!!.colorManagement)
        state = model.state.value
        assertEquals("含 HDR 素材（PQ 1 段）：时间线为 Rec.709 Gamma 2.4（SDR），导出时按「保持色相」色调映射，PQ 母版峰值按 4000 nits", state.hdrNote)
        assertEquals("PQ 峰值 4000 nits", session.history.value.undoLabel)
        // An SDR timeline shows none of it.
        val (_, plain) = deliverStage()
        assertNull(plain.state.value.hdrNote)
    }
}

/** Records the frame range of every render, then renders like [FakeRenderRepository]. */
private class RecordingRenderRepository(private val delegate: RenderRepository = FakeRenderRepository()) : RenderRepository {
    val ranges = mutableListOf<Pair<Int?, Int?>>()
    val documents = mutableListOf<ProjectDocument>()

    override suspend fun render(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
    ): RenderOutput {
        ranges += rangeStart to rangeEnd
        documents += document
        return delegate.render(document, settings, rangeStart, rangeEnd, onProgress)
    }
}

/** Renders on [dispatcher] (the way Media3RenderRepository hops to Dispatchers.Main for the export). */
private class DispatcherHoppingRenderRepository(
    private val dispatcher: CoroutineDispatcher,
    private val delegate: RenderRepository = FakeRenderRepository(),
) : RenderRepository {
    override suspend fun render(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
    ): RenderOutput = withContext(dispatcher) { delegate.render(document, settings, rangeStart, rangeEnd, onProgress) }
}
