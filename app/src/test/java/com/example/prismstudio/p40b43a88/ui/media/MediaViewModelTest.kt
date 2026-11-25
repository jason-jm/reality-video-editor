package com.example.prismstudio.p40b43a88.ui.media

import org.junit.Assert.assertNull
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeDeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeSourcePlaybackRepository
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.UsageRule
import com.example.prismstudio.p40b43a88.domain.usecase.MediaSort
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.usecase.EditTargetsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.openSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import com.example.prismstudio.p40b43a88.domain.model.SourceColor
import com.example.prismstudio.p40b43a88.domain.model.ColorPrimaries
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.SourceMarks

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewModelTest {

    private val source = FakeSourcePlaybackRepository()
    private val previews = FakeMediaPreviewRepository()
    private val program = FakeProgramPlaybackRepository()
    private val ids = SequentialIds()

    /** 10 s video (300 frames at 30 fps), 8 s song, a still. */
    private val beach = UiFixtures.video("v1", name = "Beach Walk.mp4", durationMs = 10_000)
    private val song = UiFixtures.audio("au", name = "Theme Song.m4a", durationMs = 8_000)
    private val poster = UiFixtures.image("im", name = "Poster.jpg")

    private fun deviceItem(id: String, uri: String = "content://device/$id", sizeBytes: Long = 1L shl 20) = DeviceMediaItem(
        id = id,
        uri = uri,
        name = "$id.mp4",
        kind = MediaKind.VIDEO,
        durationMs = 6_000,
        sizeBytes = sizeBytes,
        relativePath = "DCIM/",
        width = 1280,
        height = 720,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.mediaStage(
        media: List<MediaAsset> = listOf(beach, song, poster),
        device: DeviceMediaRepository = FakeDeviceMediaRepository(),
    ): Pair<DefaultEditorSessionRepository, MediaViewModel> {
        val session = openSession(UiFixtures.document(media = media))
        // In the app the workspace shell feeds every document to the program monitor.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.document.collect { program.setProject(it) } }
        val model = MediaViewModel(session, device, previews, source, ids, program)
        advanceUntilIdle()
        return session to model
    }

    @Test
    fun `user bins - a new one shows, media filed in it from the inspector, renamed and deleted, each one undo step`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.NewBin)
        val id = session.document.value!!.bins.single().id
        assertEquals(id, model.state.value.userBinId)
        assertEquals(emptyList<String>(), model.state.value.items.map { it.id })
        assertEquals("媒体夹 1 · 0 个素材", model.state.value.summary)
        assertEquals("新建媒体夹", session.history.value.undoLabel)

        model.onEvent(MediaEvent.SelectBin(0))
        model.onEvent(MediaEvent.Select("au"))
        model.onEvent(MediaEvent.MoveToBin(id))
        assertEquals(id, model.state.value.selectedBinId)
        assertEquals(listOf(UserBinUi(id, "媒体夹 1", 1)), model.state.value.userBins)
        model.onEvent(MediaEvent.SelectUserBin(id))
        assertEquals(listOf("au"), model.state.value.items.map { it.id })

        model.onEvent(MediaEvent.OpenRenameBin(id))
        model.onEvent(MediaEvent.RenameBinText(""))
        assertEquals("名称不能为空", model.state.value.binRename!!.problem)
        model.onEvent(MediaEvent.RenameBinText("配乐"))
        model.onEvent(MediaEvent.ConfirmRenameBin)
        assertNull(model.state.value.binRename)
        assertEquals("配乐", model.state.value.userBins.single().name)

        model.onEvent(MediaEvent.DeleteBin(id))
        assertEquals("已删除媒体夹“配乐”，其中 1 个素材回到媒体池", model.state.value.toast)
        assertNull(model.state.value.userBinId)
        assertEquals(3, model.state.value.items.size)
        session.undo()
        assertEquals("配乐", model.state.value.userBins.single().name)
        assertEquals(id, session.document.value!!.asset("au")!!.binId)
    }

    @Test
    fun `smart bins - the sheet counts as rules change, the bin follows edits, changed and deleted as undo steps`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.NewSmartBin)
        val blank = model.state.value.smartSheet!!
        assertEquals("智能媒体夹 1", blank.rules.name)
        assertEquals("符合条件：3 个素材 · 请至少设置一个条件", blank.status)
        assertFalse(blank.canConfirm)
        model.onEvent(MediaEvent.SmartRules { it.copy(name = "未用视频", kind = MediaKind.VIDEO, usage = UsageRule.UNUSED) })
        assertEquals("符合条件：1 个素材", model.state.value.smartSheet!!.status)
        model.onEvent(MediaEvent.ConfirmSmartBin)
        val id = session.document.value!!.smartBins.single().id
        assertNull(model.state.value.smartSheet)
        assertEquals(id, model.state.value.smartBinId)
        assertEquals(listOf("v1"), model.state.value.items.map { it.id })
        assertEquals("未用视频 · 1 个素材 · 视频 · 未使用", model.state.value.summary)
        assertEquals("已新建智能媒体夹“未用视频”（1 个素材）", model.state.value.toast)
        assertEquals("新建智能媒体夹", session.history.value.undoLabel)

        // Used on the timeline, the clip leaves the bin by itself.
        model.onEvent(MediaEvent.Append)
        assertEquals(emptyList<String>(), model.state.value.items.map { it.id })
        assertEquals(listOf(SmartBinUi(id, "未用视频", 0, "视频 · 未使用")), model.state.value.smartBins)
        assertEquals("使用" to "1 次 · 时间线 1", model.state.value.metadata[3])
        assertEquals("没有符合条件的素材", model.state.value.emptyTitle)

        model.onEvent(MediaEvent.EditSmartBin(id))
        model.onEvent(MediaEvent.SmartRules { it.copy(name = "已用视频", usage = UsageRule.USED) })
        model.onEvent(MediaEvent.ConfirmSmartBin)
        assertEquals("已更新智能媒体夹“已用视频”（1 个素材）", model.state.value.toast)
        assertEquals(listOf("v1"), model.state.value.items.map { it.id })

        model.onEvent(MediaEvent.DeleteSmartBin(id))
        assertEquals("已删除智能媒体夹“已用视频”，素材仍在媒体池中", model.state.value.toast)
        assertNull(model.state.value.smartBinId)
        assertEquals(3, model.state.value.items.size)
        session.undo()
        assertEquals("已用视频", model.state.value.smartBins.single().name)
    }

    @Test
    fun `clip metadata - the sheet checks the draft, saves one undo step, and the slate, rows and search follow`() = runTest {
        val (session, model) = mediaStage()
        assertEquals("未填写", model.state.value.slateLabel)
        model.onEvent(MediaEvent.OpenMetadata)
        val sheet = model.state.value.metadataSheet!!
        assertEquals("Beach Walk.mp4", sheet.assetName)
        assertFalse(sheet.canConfirm)
        model.onEvent(MediaEvent.MetadataDraft { it.copy(take = "1".repeat(13)) })
        assertEquals("场次、镜头、条次各最多 12 个字", model.state.value.metadataSheet!!.hint)
        model.onEvent(MediaEvent.MetadataDraft { it.copy(scene = "3", take = "4", keywords = "sunset, drone", goodTake = true) })
        assertTrue(model.state.value.metadataSheet!!.canConfirm)
        model.onEvent(MediaEvent.ConfirmMetadata)
        assertNull(model.state.value.metadataSheet)
        assertEquals("已更新“Beach Walk.mp4”的场记", model.state.value.toast)
        assertEquals("编辑场记", session.history.value.undoLabel)
        assertEquals("场 3 · 条 4 · 好条", model.state.value.slateLabel)
        assertEquals("关键词" to "sunset、drone", model.state.value.metadata.last())

        model.onEvent(MediaEvent.Query("drone"))
        assertEquals(listOf("v1"), model.state.value.items.map { it.id })
        session.undo()
        assertEquals("未填写", model.state.value.slateLabel)
        assertEquals(emptyList<String>(), model.state.value.items.map { it.id })
    }

    @Test
    fun `pool views and sort - list view, sort by a column and back the other way, both views share the order`() = runTest {
        val (_, model) = mediaStage()
        assertFalse(model.state.value.listView)
        assertEquals("按导入顺序 ↑", model.state.value.order.label)
        model.onEvent(MediaEvent.SetListView(true))
        assertTrue(model.state.value.listView)
        model.onEvent(MediaEvent.SortBy(MediaSort.DURATION))
        // Poster 5 s, Theme Song 8 s, Beach Walk 10 s.
        assertEquals(listOf("im", "au", "v1"), model.state.value.items.map { it.id })
        model.onEvent(MediaEvent.SortBy(MediaSort.DURATION))
        assertEquals("按时长 ↓", model.state.value.order.label)
        assertEquals(listOf("v1", "au", "im"), model.state.value.items.map { it.id })
        model.onEvent(MediaEvent.SortBy(MediaSort.NAME))
        assertEquals(listOf("v1", "im", "au"), model.state.value.items.map { it.id })
        model.onEvent(MediaEvent.SortAscending(false))
        assertEquals(listOf("au", "im", "v1"), model.state.value.items.map { it.id })
        val beach = model.state.value.items.last()
        assertEquals(listOf("1920×1080", "—", 0), listOf(beach.resolution, beach.slate, beach.uses))
        assertEquals("—", model.state.value.items.first { it.id == "au" }.resolution)
        // The selection stays as the order changes.
        assertEquals("v1", model.state.value.selectedId)
        model.onEvent(MediaEvent.SetListView(false))
        assertEquals(listOf("au", "im", "v1"), model.state.value.items.map { it.id })
    }

    @Test
    fun `proxies - make the selected one, then every video lacking one, the chip following, and delete`() = runTest {
        val proxies = com.example.prismstudio.p40b43a88.data.repository.FakeProxyRepository()
        val session = openSession(UiFixtures.document(media = listOf(beach, song, poster, UiFixtures.video("v2", name = "City.mp4"))))
        val model = MediaViewModel(session, FakeDeviceMediaRepository(), previews, source, ids, program, proxies = proxies)
        advanceUntilIdle()
        assertEquals("无代理", model.state.value.proxyLabel)
        assertTrue(model.state.value.canProxy)
        assertEquals(2, model.state.value.proxiesMissing)
        model.onEvent(MediaEvent.GenerateProxy)
        assertEquals(listOf("v1"), proxies.requested)
        assertEquals("开始为“Beach Walk.mp4”生成代理（960×540）", model.state.value.toast)
        assertEquals("生成中 0%", model.state.value.proxyLabel)
        proxies.finish("v1", com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus.Ready("file:///p/v1.mp4", 960, 540, 2L shl 20))
        assertEquals("代理 960×540 · 2 MB", model.state.value.proxyLabel)
        assertTrue(model.state.value.hasProxy)
        model.onEvent(MediaEvent.GenerateAllProxies)
        assertEquals(listOf("v1", "v2"), proxies.requested)
        assertEquals("开始为 1 个视频生成代理，一次生成一个", model.state.value.toast)
        model.onEvent(MediaEvent.DeleteProxy)
        assertEquals(listOf("v1"), proxies.deleted)
        assertEquals("无代理", model.state.value.proxyLabel)
        // A still needs none.
        model.onEvent(MediaEvent.Select("im"))
        assertFalse(model.state.value.canProxy)
        model.onEvent(MediaEvent.GenerateProxy)
        assertEquals("图片无需代理", model.state.value.toast)
    }

    @Test
    fun `auto sync audio - the video takes the recorder's sound from then on, lined up, and can go back`() = runTest {
        // One take: the song file (a recorder) started 2.5 s before the camera (beach).
        val r = kotlin.random.Random(41)
        val events = List(300) { Triple(r.nextDouble() * 60_000, 0.2 + r.nextDouble(), 40 + r.nextDouble() * 260) }
        fun env(offset: Double, ms: Int, seed: Int): com.example.prismstudio.p40b43a88.domain.usecase.SoundEnvelope {
            val n = kotlin.random.Random(seed)
            return com.example.prismstudio.p40b43a88.domain.usecase.SoundEnvelope(5, FloatArray(ms / 5) { i ->
                val t = offset + i * 5 + 2.5
                (10 * kotlin.math.log10((1e-6 + events.sumOf { (at, l, d) -> if (t >= at) l * kotlin.math.exp(-(t - at) / d) else 0.0 }) * (0.8 + 0.4 * n.nextDouble()))).toFloat()
            })
        }
        val camera = beach
        val recorder = song.copy(durationMs = 30_000)
        val levels = com.example.prismstudio.p40b43a88.data.repository.FakeSoundEnvelopeRepository(
            mapOf(camera.uri to env(2_500.0, camera.durationMs.toInt(), 1), recorder.uri to env(0.0, 30_000, 2)),
        )
        val session = openSession(UiFixtures.document(media = listOf(camera, recorder, poster)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.document.collect { program.setProject(it) } }
        val model = MediaViewModel(session, FakeDeviceMediaRepository(), previews, source, ids, program, envelopes = levels)
        advanceUntilIdle()
        model.onEvent(MediaEvent.Select(camera.id))
        assertEquals("未同步：使用视频自带的声音", model.state.value.syncLabel)
        assertEquals(listOf(recorder.id), model.state.value.syncChoices.map { it.id })
        model.onEvent(MediaEvent.SyncAudio(recorder.id))
        advanceUntilIdle()
        val synced = session.document.value!!.asset(camera.id)!!.syncedAudio!!
        assertEquals(recorder.id, synced.assetId)
        assertEquals(2_500.0, synced.offsetMs.toDouble(), 3.0)
        assertEquals("同步音频", session.history.value.undoLabel)
        assertTrue(model.state.value.synced)
        assertTrue(model.state.value.syncLabel, model.state.value.syncLabel.startsWith("已同步「${recorder.name}」：录音比画面早开始 2."))
        // Appended now, the camera's sound clip plays the recorder's file from the matching moment.
        model.onEvent(MediaEvent.Append)
        val sound = session.document.value!!.timeline.clips.single { it.kind == com.example.prismstudio.p40b43a88.domain.model.ClipKind.AUDIO }
        assertEquals(recorder.id, sound.assetId)
        assertEquals(synced.offsetMs, sound.sourceInMs)
        model.onEvent(MediaEvent.ClearAudioSync)
        assertNull(session.document.value!!.asset(camera.id)!!.syncedAudio)
        assertEquals("取消同步音频", session.history.value.undoLabel)
    }

    @Test
    fun `the pool is published with the first asset selected and loaded in the source monitor`() = runTest {
        val (_, model) = mediaStage()

        val state = model.state.value
        assertEquals(listOf("v1", "au", "im"), state.items.map { it.id })
        assertEquals(3, state.poolCount)
        assertEquals("3 个素材 · 共 00:23", state.summary)
        assertEquals("v1", state.selectedId)
        assertEquals(beach, source.asset)
        assertEquals(300, state.sourceDurationFrames)
        assertEquals(0, state.inFrame)
        assertEquals(300, state.outFrame)
        assertEquals(
            listOf(
                "分辨率" to "1920×1080",
                "帧率" to "30 fps",
                "时长" to "00:10",
                "使用" to "未使用",
                "编码" to "H.264",
                "色彩" to "未标记（按 Rec.709）",
                "音频" to "AAC 48 kHz 立体声",
                "文件" to "Movies/Beach Walk.mp4 · 1 MB",
            ),
            state.metadata,
        )
    }

    @Test
    fun `bins and search narrow the pool without losing the selection`() = runTest {
        val (_, model) = mediaStage()

        model.onEvent(MediaEvent.SelectBin(MediaBin.AUDIO.ordinal))
        assertEquals(listOf("au"), model.state.value.items.map { it.id })
        model.onEvent(MediaEvent.SelectBin(99))
        assertEquals("the last bin: the timelines", MediaBin.TIMELINES.ordinal, model.state.value.binIndex)
        assertTrue("no media there", model.state.value.items.isEmpty())
        model.onEvent(MediaEvent.SelectBin(MediaBin.IMAGE.ordinal))
        assertEquals(listOf("im"), model.state.value.items.map { it.id })

        model.onEvent(MediaEvent.SelectBin(0))
        model.onEvent(MediaEvent.Query("BEACH"))
        assertEquals(listOf("v1"), model.state.value.items.map { it.id })
        model.onEvent(MediaEvent.Query("nothing like this"))
        assertTrue(model.state.value.items.isEmpty())

        assertEquals("the pool count ignores filters", 3, model.state.value.poolCount)
        assertEquals("v1", model.state.value.selectedId)
    }

    @Test
    fun `the timelines bin lists the project's timelines and a tap opens one for the Edit page`() = runTest {
        val (session, model) = mediaStage()
        val doc = session.document.value!!
        session.edit("准备") {
            it.copy(timelines = listOf(com.example.prismstudio.p40b43a88.domain.model.StoredTimeline(
                "tl-v", "竖屏版", doc.format.copy(width = 1080, height = 1920),
                com.example.prismstudio.p40b43a88.domain.model.Timeline.EMPTY, doc.mixer,
            )))
        }
        model.onEvent(MediaEvent.SelectBin(MediaBin.TIMELINES.ordinal))
        val state = model.state.value
        assertTrue(state.showsTimelines)
        assertEquals(listOf(doc.timelineId, "tl-v"), state.timelines.map { it.id })
        assertEquals(listOf(true, false), state.timelines.map { it.open })
        assertEquals("1080×1920 · 00:00:00:00 · 0 个片段", state.timelines[1].detail)
        assertEquals("2 条时间线 · 当前打开“${doc.timelineName}”", state.summary)

        model.onEvent(MediaEvent.OpenTimeline("tl-v"))
        assertEquals("tl-v", session.document.value!!.timelineId)
        assertEquals("切换时间线", session.history.value.undoLabel)
        assertTrue("the workspace is asked for the Edit page", model.state.value.openEditRequest)
        assertEquals("已切换到“竖屏版”", model.state.value.toast)
        model.onEvent(MediaEvent.EditOpened)
        assertFalse(model.state.value.openEditRequest)
        model.onEvent(MediaEvent.OpenTimeline("tl-v"))
        assertTrue("the open one just goes to the Edit page", model.state.value.openEditRequest)
        model.onEvent(MediaEvent.Query("竖屏"))
        assertEquals(listOf("tl-v"), model.state.value.timelines.map { it.id })
    }

    @Test
    fun `selecting an asset pauses the source monitor and loads it with its full range`() = runTest {
        val (_, model) = mediaStage()
        source.play()

        model.onEvent(MediaEvent.Select("au"))

        val state = model.state.value
        assertEquals("au", state.selectedId)
        assertEquals(song, source.asset)
        assertFalse(source.state.value.playing)
        assertEquals(240, state.sourceDurationFrames)
        assertEquals(0, state.inFrame)
        assertEquals(240, state.outFrame)
        assertEquals(0, state.sourcePositionFrames)
    }

    @Test
    fun `mark in and out record the range at the source playhead and are kept per asset`() = runTest {
        val (session, model) = mediaStage()

        model.onEvent(MediaEvent.Scrub(60))
        assertEquals(2_000L, source.state.value.positionMs)
        model.onEvent(MediaEvent.MarkIn)
        assertEquals(60, model.state.value.inFrame)
        assertEquals(300, model.state.value.outFrame)
        assertEquals("已标记入点 00:00:02:00", model.state.value.toast)

        model.onEvent(MediaEvent.Scrub(150))
        model.onEvent(MediaEvent.MarkOut)
        assertEquals(150, model.state.value.outFrame)
        assertEquals("已标记出点 00:00:05:00", model.state.value.toast)
        assertEquals("选段 00:00:03:00", model.state.value.rangeLabel)
        assertEquals("the marks are the session's, for the Edit page's source viewer too", SourceMarks(2_000, 5_000), session.sourceMarks.value["v1"])

        model.onEvent(MediaEvent.Select("au"))
        assertEquals(0 to 240, model.state.value.let { it.inFrame to it.outFrame })
        model.onEvent(MediaEvent.Select("v1"))
        assertEquals(60 to 150, model.state.value.let { it.inFrame to it.outFrame })
    }

    @Test
    fun `an out point before the in point resets the in point and an empty range is refused`() = runTest {
        val (_, model) = mediaStage()

        model.onEvent(MediaEvent.Scrub(150))
        model.onEvent(MediaEvent.MarkIn)
        model.onEvent(MediaEvent.Scrub(90))
        model.onEvent(MediaEvent.MarkOut)
        assertEquals(0 to 90, model.state.value.let { it.inFrame to it.outFrame })
        assertEquals("已标记出点 00:00:03:00", model.state.value.toast)

        model.onEvent(MediaEvent.Scrub(300))
        model.onEvent(MediaEvent.MarkIn)
        assertEquals("入点必须早于出点", model.state.value.toast)
        assertEquals("the marks are unchanged", 0 to 90, model.state.value.let { it.inFrame to it.outFrame })
    }

    @Test
    fun `stills cannot be marked`() = runTest {
        val (_, model) = mediaStage()
        model.onEvent(MediaEvent.Select("im"))

        model.onEvent(MediaEvent.MarkIn)

        assertEquals("图片素材固定为 5 秒，无需标记入出点", model.state.value.toast)
        assertEquals(0 to 150, model.state.value.let { it.inFrame to it.outFrame })
    }

    @Test
    fun `append puts the marked range on the timeline through the session and selects it`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.Scrub(60))
        model.onEvent(MediaEvent.MarkIn)
        model.onEvent(MediaEvent.Scrub(150))
        model.onEvent(MediaEvent.MarkOut)

        model.onEvent(MediaEvent.Append)

        val timeline = session.document.value!!.timeline
        val picture = timeline.clip("c-1")!!
        assertEquals(Timeline.V1, picture.trackId)
        assertEquals(ClipKind.VIDEO, picture.kind)
        assertEquals("Beach Walk", picture.name)
        assertEquals(0, picture.start)
        assertEquals(90, picture.durationFrames)
        assertEquals(2_000L, picture.sourceInMs)
        val sound = timeline.clip("c-2")!!
        assertEquals(Timeline.A1, sound.trackId)
        assertEquals("video and sound are linked", picture.linkId, sound.linkId)
        assertEquals("c-1", session.selectedClipId.value)
        assertEquals("添加到时间线", session.history.value.undoLabel)
        assertEquals("已添加到时间线：Beach Walk（00:00:03:00）", model.state.value.toast)
    }

    @Test
    fun `insert, overwrite and place on top put the marked range at the timeline's playhead and move it past`() = runTest {
        val (session, model) = mediaStage()
        val clips = { session.document.value!!.timeline.clips }
        model.onEvent(MediaEvent.Append)
        assertEquals(listOf(0, 0), clips().map { it.start })
        advanceUntilIdle()
        // The playhead at 2 s on the timeline: insert the first 3 s of the beach clip there.
        program.seekTo(2_000)
        advanceUntilIdle()
        assertEquals("插入 / 覆盖 / 波纹覆盖 / 替换 / 叠加到时间线播放头 00:00:02:00", model.state.value.editPointLabel)
        model.onEvent(MediaEvent.Scrub(90))
        model.onEvent(MediaEvent.MarkOut)
        model.onEvent(MediaEvent.Edit(SourceEdit.INSERT))
        val onV1 = clips().filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
        assertEquals("the first clip split, the new one inside, the rest later", listOf(0 to 60, 60 to 150, 150 to 390), onV1.map { it.start to it.end })
        assertEquals("插入到时间线", session.history.value.undoLabel)
        assertEquals("已在 00:00:02:00 插入“Beach Walk”（00:00:03:00），后面的片段后移", model.state.value.toast)
        assertEquals("the playhead moves past the new clip", 5_000L, program.state.value.positionMs)

        model.onEvent(MediaEvent.Edit(SourceEdit.OVERWRITE))
        val after = clips().filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
        assertEquals(listOf(0 to 60, 60 to 150, 150 to 240, 240 to 390), after.map { it.start to it.end })
        assertEquals(8_000L, program.state.value.positionMs)

        program.seekTo(1_000)
        advanceUntilIdle()
        model.onEvent(MediaEvent.Edit(SourceEdit.PLACE_ON_TOP))
        val top = clips().single { it.trackId == Timeline.V2 }
        assertEquals(30 to 120, top.start to top.end)
        assertTrue(model.state.value.toast!!.startsWith("已叠加“Beach Walk”到 V2 · 00:00:01:00"))
    }

    @Test
    fun `replace lines the source playhead up with the timeline's and leaves the playhead where it is`() = runTest {
        val city = UiFixtures.video("city", name = "City.mp4", durationMs = 10_000)
        val (session, model) = mediaStage(media = listOf(beach, city))
        val clips = { session.document.value!!.timeline.clips }
        // Beach 1 s – 4 s at the start of the timeline.
        model.onEvent(MediaEvent.Scrub(30))
        model.onEvent(MediaEvent.MarkIn)
        model.onEvent(MediaEvent.Scrub(120))
        model.onEvent(MediaEvent.MarkOut)
        model.onEvent(MediaEvent.Append)
        advanceUntilIdle()
        // City's 5 s at the timeline's 1 s: its media starts at 4 s, the clip keeps its 3 s.
        model.onEvent(MediaEvent.Select("city"))
        program.seekTo(1_000)
        advanceUntilIdle()
        model.onEvent(MediaEvent.Scrub(150))
        model.onEvent(MediaEvent.Edit(SourceEdit.REPLACE))
        val replaced = clips().sortedBy { it.trackId }
        assertEquals(listOf("city", "city"), replaced.map { it.assetId })
        assertEquals(listOf(4_000L, 4_000L), replaced.map { it.sourceInMs })
        assertEquals(listOf(0 to 90, 0 to 90), replaced.map { it.start to it.end })
        assertEquals("替换片段", session.history.value.undoLabel)
        assertEquals("已用“City”替换 V1 上的“Beach Walk”（00:00:03:00），声音一并替换", model.state.value.toast)
        assertEquals("the playhead stays", 1_000L, program.state.value.positionMs)
        // Too little media after the source playhead: refused, nothing changes.
        model.onEvent(MediaEvent.Scrub(290))
        model.onEvent(MediaEvent.Edit(SourceEdit.REPLACE))
        assertEquals("“City”在源播放头之后少了 1.7 秒素材，填不满片段结尾：把源播放头往前移", model.state.value.toast)
        assertEquals(replaced, clips().sortedBy { it.trackId })
    }

    @Test
    fun `ripple overwrite swaps the clip under the playhead at the source's length and moves the playhead past it`() = runTest {
        val city = UiFixtures.video("city", name = "City.mp4", durationMs = 10_000)
        val (session, model) = mediaStage(media = listOf(beach, city))
        val clips = { session.document.value!!.timeline.clips }
        // The whole beach clip (0–300), then the whole city clip (300–600).
        model.onEvent(MediaEvent.Append)
        model.onEvent(MediaEvent.Select("city"))
        model.onEvent(MediaEvent.Append)
        advanceUntilIdle()
        // City 2 s – 3 s for the beach clip under the playhead at 4 s: the rest of the city clip follows on at 1 s.
        program.seekTo(4_000)
        advanceUntilIdle()
        model.onEvent(MediaEvent.Scrub(60))
        model.onEvent(MediaEvent.MarkIn)
        model.onEvent(MediaEvent.Scrub(90))
        model.onEvent(MediaEvent.MarkOut)
        model.onEvent(MediaEvent.Edit(SourceEdit.RIPPLE_OVERWRITE))
        val onV1 = clips().filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
        assertEquals(listOf(0 to 30, 30 to 330), onV1.map { it.start to it.end })
        assertEquals(listOf(2_000L, 0L), onV1.map { it.sourceInMs })
        assertEquals("its sound alike", listOf(0 to 30, 30 to 330), clips().filter { it.trackId == Timeline.A1 }.sortedBy { it.start }.map { it.start to it.end })
        assertEquals("波纹覆盖到时间线", session.history.value.undoLabel)
        assertEquals("已在 00:00:00:00 波纹覆盖“City”（00:00:01:00，原为 00:00:10:00），后面的片段前移 00:00:09:00", model.state.value.toast)
        assertEquals("the playhead moves past the new clip", 1_000L, program.state.value.positionMs)
    }

    @Test
    fun `with timeline in and out marked the edits fill them, and the caption says so`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.Append)
        advanceUntilIdle()
        session.update { it.copy(timeline = it.timeline.copy(marks = TimelineMarks(60, 90))) }
        advanceUntilIdle()
        assertEquals("插入 / 覆盖 / 波纹覆盖 / 适配填充 / 叠加到时间线入点 00:00:02:00 → 出点 00:00:03:00（00:00:01:00），替换在播放头", model.state.value.editPointLabel)
        model.onEvent(MediaEvent.Edit(SourceEdit.OVERWRITE))
        val onV1 = session.document.value!!.timeline.clips.filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
        assertEquals(listOf(0 to 60, 60 to 90, 90 to 300), onV1.map { it.start to it.end })
        assertEquals("used up", TimelineMarks(), session.document.value!!.timeline.marks)
        assertTrue(model.state.value.toast!!.endsWith("，按时间线入出点放置"))
        assertEquals("the playhead moves past the new clip", 3_000L, program.state.value.positionMs)
        // Alt+X on the Media stage gives the source back its whole length.
        model.onEvent(MediaEvent.Scrub(30))
        model.onEvent(MediaEvent.MarkIn)
        model.onEvent(MediaEvent.ClearMarks)
        assertEquals(0 to 300, model.state.value.inFrame to model.state.value.outFrame)
        assertEquals("已清除源片段入出点", model.state.value.toast)
    }

    @Test
    fun `fit to fill needs the timeline marks and retimes the source range into them`() = runTest {
        val (session, model) = mediaStage()
        assertFalse(model.state.value.canFitToFill)
        model.onEvent(MediaEvent.Edit(SourceEdit.FIT_TO_FILL))
        assertEquals("适配填充要先在时间线上标记入点和出点", model.state.value.toast)
        session.update { it.copy(timeline = it.timeline.copy(marks = TimelineMarks(0, 150))) }
        advanceUntilIdle()
        assertTrue(model.state.value.canFitToFill)
        assertTrue(model.state.value.editPointLabel.startsWith("插入 / 覆盖 / 波纹覆盖 / 适配填充 / 叠加到时间线入点"))
        // The whole 10 s beach clip into 5 s: twice as fast.
        model.onEvent(MediaEvent.Edit(SourceEdit.FIT_TO_FILL))
        val placed = session.document.value!!.timeline.clips.single { it.trackId == Timeline.V1 }
        assertEquals(ClipSpeed.of(PlaybackSpeed.DOUBLE), placed.speed)
        assertEquals(0 to 150, placed.start to placed.end)
        assertEquals("适配填充", session.history.value.undoLabel)
        assertFalse("the marks are used up", model.state.value.canFitToFill)
    }

    @Test
    fun `the destination controls choose the tracks outside the edit history`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.Append)
        advanceUntilIdle()
        val state = model.state.value
        assertEquals("V1 first", listOf("V1", "V2"), state.videoTargets.map { it.label })
        assertEquals(listOf(null, Timeline.A1, Timeline.A2), state.audioTargets.map { it.id })
        assertEquals("自动", state.audioTargets.first().label)
        assertEquals(EditTargets(), state.targets)

        model.onEvent(MediaEvent.SetVideoTarget(Timeline.V2))
        model.onEvent(MediaEvent.SetAudioTarget(Timeline.A2))
        advanceUntilIdle()
        assertEquals(EditTargets(Timeline.V2, Timeline.A2), model.state.value.targets)
        assertEquals(EditTargets(Timeline.V2, Timeline.A2), session.document.value!!.timeline.targets)
        assertEquals("声音将落在 A2", model.state.value.toast)
        assertEquals("not an undo step", "添加到时间线", session.history.value.undoLabel)
        program.seekTo(2_000)
        advanceUntilIdle()
        model.onEvent(MediaEvent.Edit(SourceEdit.OVERWRITE))
        val placed = session.document.value!!.timeline.clips.filter { it.start == 60 }
        assertEquals(setOf(Timeline.V2, Timeline.A2), placed.map { it.trackId }.toSet())

        model.onEvent(MediaEvent.SetAudioTarget(null))
        advanceUntilIdle()
        assertEquals(EditTargets(Timeline.V2, null), model.state.value.targets)
        assertEquals(EditTargetsUseCase.AUTOMATIC, model.state.value.toast)
    }

    @Test
    fun `offline media can be neither appended nor played`() = runTest {
        val missing = UiFixtures.video("off", offline = true)
        val (session, model) = mediaStage(media = listOf(missing))
        assertTrue(model.state.value.nothingSelected)

        model.onEvent(MediaEvent.Append)
        model.onEvent(MediaEvent.SetPlaying(true))

        assertEquals("素材离线，无法添加：off.mp4", model.state.value.toast)
        assertTrue(session.document.value!!.timeline.clips.isEmpty())
        assertFalse(session.history.value.canUndo)
        assertFalse(source.state.value.playing)
    }

    @Test
    fun `jog pauses the source and steps from the current position within the clip`() = runTest {
        val (_, model) = mediaStage()
        model.onEvent(MediaEvent.Scrub(60))
        model.onEvent(MediaEvent.SetPlaying(true))
        assertTrue(model.state.value.playing)

        model.onEvent(MediaEvent.Jog(30))
        assertFalse(source.state.value.playing)
        assertEquals(90, model.state.value.sourcePositionFrames)

        model.onEvent(MediaEvent.Jog(-1_000))
        assertEquals(0, model.state.value.sourcePositionFrames)

        model.onEvent(MediaEvent.JumpToOut)
        assertEquals(300, model.state.value.sourcePositionFrames)
        assertEquals(10_000L, source.state.value.positionMs)
    }

    @Test
    fun `importing without permission asks for it and a granted permission lists the device`() = runTest {
        val device = FakeDeviceMediaRepository(items = listOf(deviceItem("d1"), deviceItem("d2")), granted = false)
        val (_, model) = mediaStage(device = device)

        model.onEvent(MediaEvent.OpenImport)
        var import = model.state.value.import
        assertTrue(import.visible)
        assertFalse(import.loading)
        assertTrue(import.permissionRequired)
        assertTrue(import.hasError)
        assertEquals("Reality Video Editor 需要“照片和视频”权限才能列出设备中的素材。", import.errorMessage)

        device.granted = true
        model.onEvent(MediaEvent.PermissionResult(granted = true))
        import = model.state.value.import
        assertFalse(import.permissionRequired)
        assertFalse(import.hasError)
        assertEquals(listOf("d1", "d2"), import.items.map { it.id })
        assertEquals("未选择素材", import.summary)
    }

    @Test
    fun `audio refused while videos are allowed is said in the sheet, and allowed later it is listed`() = runTest {
        val song = deviceItem("s1").copy(name = "song.m4a", kind = MediaKind.AUDIO)
        val device = FakeDeviceMediaRepository(items = listOf(deviceItem("d1"), song), refused = setOf(MediaKind.AUDIO))
        val (_, model) = mediaStage(device = device)

        model.onEvent(MediaEvent.OpenImport)
        var import = model.state.value.import
        assertFalse(import.hasError)
        assertEquals(listOf("d1"), import.items.map { it.id })
        assertEquals("没有获得访问音乐和音频的权限，这里不会列出它们", import.missingNote)

        // Allowed in the system settings: the screen sees it and the list is read again.
        device.refused = emptySet()
        model.onEvent(MediaEvent.PermissionResult(granted = true))
        import = model.state.value.import
        assertNull(import.missingNote)
        assertEquals(setOf("d1", "s1"), import.items.map { it.id }.toSet())

        // Only audio allowed, nothing on the device to list: the note says what is missing instead of "nothing here".
        device.items = emptyList()
        device.refused = setOf(MediaKind.VIDEO, MediaKind.IMAGE)
        model.onEvent(MediaEvent.PermissionResult(granted = true))
        import = model.state.value.import
        assertNull(import.error)
        assertEquals("没有获得访问视频、照片的权限，这里不会列出它们", import.missingNote)
    }

    @Test
    fun `device listing failures and an empty device are reported in the import sheet`() = runTest {
        val device = FakeDeviceMediaRepository(failure = "存储不可用")
        val (_, model) = mediaStage(device = device)

        model.onEvent(MediaEvent.OpenImport)
        assertEquals("存储不可用", model.state.value.import.errorMessage)

        device.failure = null
        model.onEvent(MediaEvent.CancelImport)
        model.onEvent(MediaEvent.PermissionResult(granted = true))
        assertFalse("the sheet is closed, nothing is browsed", model.state.value.import.visible)

        model.onEvent(MediaEvent.OpenImport)
        assertEquals("设备上还没有视频、音频或图片。", model.state.value.import.error)
    }

    @Test
    fun `picking device items toggles the selection but skips files already in the pool`() = runTest {
        val device = FakeDeviceMediaRepository(
            items = listOf(
                deviceItem("dup", uri = beach.uri),
                deviceItem("d2", sizeBytes = 2L shl 20),
                deviceItem("d3", sizeBytes = 1L shl 20),
            ),
        )
        val (_, model) = mediaStage(device = device)
        model.onEvent(MediaEvent.OpenImport)
        assertTrue(model.state.value.import.items.first { it.id == "dup" }.imported)

        model.onEvent(MediaEvent.ToggleDevice("dup"))
        assertTrue(model.state.value.import.selectedIds.isEmpty())

        model.onEvent(MediaEvent.ToggleDevice("d2"))
        model.onEvent(MediaEvent.ToggleDevice("d3"))
        var import = model.state.value.import
        assertEquals(setOf("d2", "d3"), import.selectedIds)
        assertEquals("已选择 2 项 · 3 MB", import.summary)
        assertTrue(import.items.first { it.id == "d2" }.selected)
        assertFalse(import.nothingSelected)

        model.onEvent(MediaEvent.ToggleDevice("d3"))
        import = model.state.value.import
        assertEquals(setOf("d2"), import.selectedIds)
        assertEquals("已选择 1 项 · 2 MB", import.summary)
    }

    @Test
    fun `cancel during probing discards the entire import and a second confirmation does not duplicate it`() = runTest {
        val fixture = FakeDeviceMediaRepository(items = listOf(deviceItem("first"), deviceItem("second")))
        val pending = CompletableDeferred<Unit>()
        val probes = mutableListOf<String>()
        val slow = object : DeviceMediaRepository by fixture {
            override suspend fun probe(item: DeviceMediaItem, assetId: String): MediaAsset {
                probes += item.id
                if (item.id == "second") pending.await()
                return fixture.probe(item, assetId)
            }
        }
        val (session, model) = mediaStage(media = emptyList(), device = slow)
        model.onEvent(MediaEvent.OpenImport)
        for (id in listOf("first", "second")) model.onEvent(MediaEvent.ToggleDevice(id))
        model.onEvent(MediaEvent.ConfirmImport)
        model.onEvent(MediaEvent.ConfirmImport)
        assertTrue(model.state.value.import.importing)
        model.onEvent(MediaEvent.CancelImport)
        model.onEvent(MediaEvent.OpenImport)
        model.onEvent(MediaEvent.ToggleDevice("first"))
        pending.complete(Unit)
        advanceUntilIdle()
        assertTrue("the cancelled import must not write or close the new sheet", session.document.value!!.media.isEmpty())
        assertTrue(model.state.value.import.visible)
        assertEquals(listOf("first", "second"), probes)
        assertFalse(session.history.value.canUndo)
        model.onEvent(MediaEvent.ConfirmImport)
        assertEquals(listOf("content://device/first"), session.document.value!!.media.map { it.uri })
        assertFalse(model.state.value.import.visible)
    }

    @Test
    fun `an access refresh removes selections that are no longer visible including a full denial`() = runTest {
        val fixture = FakeDeviceMediaRepository(items = listOf(deviceItem("first"), deviceItem("second")))
        val (session, model) = mediaStage(media = emptyList(), device = fixture)
        model.onEvent(MediaEvent.OpenImport)
        for (id in listOf("first", "second")) model.onEvent(MediaEvent.ToggleDevice(id))
        fixture.items = listOf(deviceItem("second"))
        model.onEvent(MediaEvent.PermissionResult(true))
        assertEquals(setOf("second"), model.state.value.import.selectedIds)
        fixture.granted = false
        model.onEvent(MediaEvent.PermissionResult(false))
        assertTrue(model.state.value.import.permissionRequired)
        assertTrue(model.state.value.import.items.isEmpty())
        assertTrue(model.state.value.import.nothingSelected)
        model.onEvent(MediaEvent.ConfirmImport)
        assertTrue(session.document.value!!.media.isEmpty())
    }

    @Test
    fun `confirming the import adds the picked files to the pool as one undoable step and selects the first`() = runTest {
        val device = FakeDeviceMediaRepository(items = listOf(deviceItem("d2"), deviceItem("d3")))
        val (session, model) = mediaStage(device = device)
        model.onEvent(MediaEvent.SelectBin(MediaBin.AUDIO.ordinal))
        model.onEvent(MediaEvent.Query("song"))
        model.onEvent(MediaEvent.OpenImport)
        model.onEvent(MediaEvent.ToggleDevice("d3"))
        model.onEvent(MediaEvent.ToggleDevice("d2"))

        model.onEvent(MediaEvent.ConfirmImport)
        advanceUntilIdle()

        val pool = session.document.value!!.media
        assertEquals(listOf("v1", "au", "im", "a-1", "a-2"), pool.map { it.id })
        assertEquals("imported in device order", listOf("content://device/d2", "content://device/d3"), pool.drop(3).map { it.uri })
        assertEquals("导入 2 个素材", session.history.value.undoLabel)
        val state = model.state.value
        assertEquals(ImportUi(), state.import)
        assertEquals("a-1", state.selectedId)
        assertEquals("a-1", source.asset?.id)
        assertEquals(0, state.binIndex)
        assertEquals("", state.query)
        assertEquals(5, state.items.size)
        assertEquals("已导入 2 个素材", state.toast)

        session.undo()
        assertEquals(3, session.document.value!!.media.size)
        assertEquals("selection falls back to the first asset", "v1", model.state.value.selectedId)
        assertEquals("the removed asset is not left in the source monitor", "v1", source.asset?.id)
    }

    @Test
    fun `the selected media's flags toggle from the source column and show on its tile`() = runTest {
        val (session, model) = mediaStage()
        model.onEvent(MediaEvent.ToggleFlag(FlagColor.GREEN))
        advanceUntilIdle()
        assertEquals(listOf(FlagColor.GREEN), session.document.value!!.media.first { it.id == "v1" }.flags)
        assertEquals(listOf(FlagColor.GREEN), model.state.value.items.first { it.id == "v1" }.flags)
        assertEquals(listOf(FlagColor.GREEN), model.state.value.selected!!.flags)
        assertEquals("绿色旗标", session.history.value.undoLabel)
        model.onEvent(MediaEvent.ToggleFlag(FlagColor.GREEN))
        advanceUntilIdle()
        assertEquals("已移除绿色旗标", model.state.value.toast)
        model.onEvent(MediaEvent.ClearFlags)
        assertEquals("所选素材没有旗标", model.state.value.toast)
    }

    @Test
    fun `an HDR file shows its colour and badge, and its input colour space can be set by hand, one undo step`() = runTest {
        val hlg = beach.copy(color = SourceColor(ColorPrimaries.BT2020, ColorTransfer.HLG, 10))
        val (session, model) = mediaStage(media = listOf(hlg, song))
        model.onEvent(MediaEvent.Select(hlg.id))
        var state = model.state.value
        assertTrue(state.items.first { it.id == hlg.id }.hdr)
        assertEquals("Rec.2100 HLG · 10 位", state.metadata.toMap()["色彩"])
        assertEquals(InputColorSpace.AUTO, state.inputColor)
        assertEquals("按文件标记：Rec.2100 HLG · 10 位。监视器和导出都按「亮度映射」色调映射到 Rec.709", state.colorNote)
        // Read as SDR by hand: no badge, no tone mapping.
        model.onEvent(MediaEvent.InputColor(InputColorSpace.REC709))
        state = model.state.value
        assertEquals("Beach Walk.mp4 的输入色彩空间：Rec.709", state.toast)
        assertEquals(InputColorSpace.REC709, session.document.value!!.asset(hlg.id)!!.inputColor)
        assertFalse(state.items.first { it.id == hlg.id }.hdr)
        assertEquals("已手动设为 Rec.709（文件标记：Rec.2100 HLG · 10 位）。直接进入 Rec.709 Gamma 2.4（SDR） 时间线", state.colorNote)
        assertEquals("输入色彩空间", session.history.value.undoLabel)
        session.undo()
        assertEquals(InputColorSpace.AUTO, session.document.value!!.asset(hlg.id)!!.inputColor)
        // An untagged file marked HLG by hand: tone mapped in the monitors as well as on export (rev 167).
        model.onEvent(MediaEvent.Select(song.id))
        val (_, plainModel) = mediaStage(media = listOf(beach, song))
        plainModel.onEvent(MediaEvent.Select(beach.id))
        plainModel.onEvent(MediaEvent.InputColor(InputColorSpace.HLG))
        assertTrue(plainModel.state.value.colorNote.contains("监视器和导出都按「亮度映射」色调映射到 Rec.709"))
        // A sound has no colour row.
        model.onEvent(MediaEvent.Select(song.id))
        assertNull(model.state.value.inputColor)
        assertNull(model.state.value.metadata.toMap()["色彩"])
    }
    @Test
    fun `offline media - counted in the banner, moved files found by name, a sound picked by hand, relinked as one undo step`() = runTest {
        val device = FakeDeviceMediaRepository(missingUris = setOf(beach.uri, song.uri), items = listOf(
            DeviceMediaItem("m1", "content://moved/beach", "Beach Walk.mp4", MediaKind.VIDEO, 10_000, beach.sizeBytes, "Movies/Trips/", 1920, 1080),
            DeviceMediaItem("m2", "content://other/clip", "Beach Walk.mp4", MediaKind.AUDIO, 10_000, 1, "Music/", 0, 0),
        ))
        val (session, model) = mediaStage(media = listOf(beach.copy(offline = true), song.copy(offline = true), poster), device = device)
        assertEquals(2, model.state.value.offlineCount)

        model.onEvent(MediaEvent.OpenRelink)
        val sheet = model.state.value.relink!!
        assertEquals(listOf("v1", "au"), sheet.rows.map { it.id })
        assertEquals("原位置：Movies/ · 视频 00:10", sheet.rows[0].was)
        assertEquals("将链接到 Movies/Trips/Beach Walk.mp4 · 同名，大小和时长一致", sheet.rows[0].status)
        assertEquals("设备中没有找到这个文件，可以手动选择", sheet.rows[1].status)
        assertEquals("找到 1 / 2 个素材的文件。素材在时间线上的片段、旗标、媒体夹和场记都会保留。", sheet.summary)
        assertEquals("重新链接 1 个", sheet.confirmLabel)
        // A file of another kind is refused; the right kind is read and taken.
        device.picked = mapOf(
            "content://picked/clip" to DeviceMediaItem("p1", "content://picked/clip", "x.mp4", MediaKind.VIDEO, 5_000, 9, "", 1280, 720),
            "content://picked/song" to DeviceMediaItem("p2", "content://picked/song", "Theme Song (master).m4a", MediaKind.AUDIO, 8_000, 9, "Music/Masters/", 0, 0),
        )
        model.onEvent(MediaEvent.RelinkPicked("au", "content://picked/clip"))
        assertEquals("类型不同：“Theme Song.m4a”是音频，所选文件是视频", model.state.value.toast)
        model.onEvent(MediaEvent.RelinkPicked("au", "content://picked/song"))
        assertEquals("所选文件：Music/Masters/Theme Song (master).m4a · 00:08", model.state.value.relink!!.rows[1].status)
        // Leaving one offline and taking it back.
        model.onEvent(MediaEvent.RelinkChoose("v1", ""))
        assertEquals("重新链接 1 个", model.state.value.relink!!.confirmLabel)
        model.onEvent(MediaEvent.RelinkChoose("v1", "content://moved/beach"))
        assertEquals("重新链接 2 个", model.state.value.relink!!.confirmLabel)

        model.onEvent(MediaEvent.ConfirmRelink)
        assertNull(model.state.value.relink)
        assertEquals("已重新链接 2 个素材", model.state.value.toast)
        assertEquals(0, model.state.value.offlineCount)
        val doc = session.document.value!!
        assertEquals(listOf("content://moved/beach", "Movies/Trips/"), doc.asset("v1")!!.let { listOf(it.uri, it.relativePath) })
        assertEquals("Theme Song (master).m4a", doc.asset("au")!!.name)
        assertTrue("the relinked tile reads its new file", "content://moved/beach" in previews.thumbnailCalls)
        assertEquals("重新链接素材", session.history.value.undoLabel)
        session.undo()
        assertEquals(2, model.state.value.offlineCount)
    }

    @Test
    fun `relink without the media permission says so and searches once it is granted`() = runTest {
        val device = FakeDeviceMediaRepository(granted = false, missingUris = setOf(beach.uri), items = listOf(
            DeviceMediaItem("m1", "content://moved/beach", "Beach Walk.mp4", MediaKind.VIDEO, 10_000, beach.sizeBytes, "Movies/Trips/", 1920, 1080),
        ))
        val (_, model) = mediaStage(media = listOf(beach.copy(offline = true)), device = device)
        model.onEvent(MediaEvent.OpenRelink)
        assertTrue(model.state.value.relink!!.permissionRequired)
        assertFalse(model.state.value.relink!!.canConfirm)
        device.granted = true
        model.onEvent(MediaEvent.PermissionResult(granted = true))
        assertTrue(model.state.value.relink!!.rows.single().found)
        model.onEvent(MediaEvent.CloseRelink)
        assertNull(model.state.value.relink)
    }

    @Test
    fun `limited access stays actionable with an empty library and granting the original file clears its offline row`() = runTest {
        val device = FakeDeviceMediaRepository(missingUris = setOf(beach.uri), limitedVisualAccess = true)
        val (session, model) = mediaStage(media = listOf(beach.copy(offline = true)), device = device)
        model.onEvent(MediaEvent.OpenImport)
        assertTrue(model.state.value.import.limitedVisualAccess)
        assertNull("an empty selected set does not mean the device has no files", model.state.value.import.error)
        model.onEvent(MediaEvent.CancelImport)
        model.onEvent(MediaEvent.OpenRelink)
        assertTrue(model.state.value.relink!!.limitedVisualAccess)
        assertEquals(1, model.state.value.relink!!.rows.size)
        device.missingUris = emptySet()
        model.onEvent(MediaEvent.PermissionResult(true))
        assertTrue(model.state.value.relink!!.rows.isEmpty())
        assertEquals(0, model.state.value.offlineCount)
        assertEquals(beach.uri, session.document.value!!.media.single().uri)
        assertEquals(false, source.asset?.offline)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `changing a media grant keeps the chosen match and also respects leave offline`() = runTest {
        val candidates = listOf("one", "two").map { id ->
            deviceItem(id, sizeBytes = beach.sizeBytes).copy(name = beach.name, durationMs = beach.durationMs)
        }
        val device = FakeDeviceMediaRepository(items = candidates, missingUris = setOf(beach.uri))
        val (_, model) = mediaStage(media = listOf(beach.copy(offline = true)), device = device)
        model.onEvent(MediaEvent.OpenRelink)
        model.onEvent(MediaEvent.RelinkChoose(beach.id, candidates.last().uri))
        model.onEvent(MediaEvent.PermissionResult(true))
        assertEquals(candidates.last().uri, model.state.value.relink!!.rows.single().choices.single { it.chosen }.uri)
        model.onEvent(MediaEvent.RelinkChoose(beach.id, ""))
        model.onEvent(MediaEvent.PermissionResult(true))
        assertFalse(model.state.value.relink!!.canConfirm)
    }
}
