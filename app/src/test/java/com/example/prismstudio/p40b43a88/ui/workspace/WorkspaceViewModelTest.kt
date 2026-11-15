package com.example.prismstudio.p40b43a88.ui.workspace

import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeDeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopy
import com.example.prismstudio.p40b43a88.data.repository.FakeSourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectRepository
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectLabelsUseCase
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.clearViewModel
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage
import com.example.prismstudio.p40b43a88.ui.openSession
import java.io.IOException
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {

    private val program = FakeProgramPlaybackRepository()
    private val source = FakeSourcePlaybackRepository()
    private val clock = TestClock()

    private val v1 = UiFixtures.video("v1")
    private val v2 = UiFixtures.video("v2")

    /** Edit points at 0, 30, 60, 90 and 240 (the title on V2 spans 30–60). */
    private val project = UiFixtures.document(
        name = "Harbour Film",
        media = listOf(v1, v2),
        clips = listOf(
            UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1"),
            UiFixtures.clip("c2", start = 90, frames = 150, assetId = "v2"),
            UiFixtures.clip("t1", start = 30, frames = 30, trackId = Timeline.V2, kind = ClipKind.TITLE),
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

    private fun workspace(
        session: DefaultEditorSessionRepository,
        deviceMedia: DeviceMediaRepository = FakeDeviceMediaRepository(),
        copies: FakeReverseCopyRepository = FakeReverseCopyRepository(),
    ) = WorkspaceViewModel(session, program, source, deviceMedia, copies, labels = ProjectLabelsUseCase(ZoneOffset.UTC))

    @Test
    fun `reports when a reversed copy finishes or fails, whichever stage is open`() = runTest {
        val session = openSession(project, clock = clock)
        val copies = FakeReverseCopyRepository()
        val model = workspace(session, copies = copies)
        advanceUntilIdle()

        copies.set("v1", ReverseCopy.Ready("file:///old.mp4"))
        assertNull("a copy found on disk is not news", model.state.value.toast)

        copies.set("v2", ReverseCopy.Making(0.3f))
        copies.set("v2", ReverseCopy.Ready("file:///v2.mp4"))
        assertEquals("倒放素材已生成：v2.mp4", model.state.value.toast)

        model.onEvent(WorkspaceEvent.ToastShown)
        copies.set("v1", ReverseCopy.Making(0f))
        copies.set("v1", ReverseCopy.Failed("解码或编码出错"))
        assertEquals("倒放素材生成失败：v1.mp4（解码或编码出错）", model.state.value.toast)
    }

    @Test
    fun `opening a project with media shows the Edit stage and loads the program monitor`() = runTest {
        val session = openSession(project, clock = clock)

        val model = workspace(session)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(WorkspaceStage.EDIT, state.stage)
        assertEquals("Harbour Film", state.projectName)
        assertEquals(30, state.fps)
        assertEquals(240, state.durationFrames)
        assertSame(session.document.value, program.document)
        assertEquals(8_000L, program.state.value.durationMs)
    }

    @Test
    fun `slash plays around the playhead and comes back, Alt+slash plays In to Out, a pause ends it`() = runTest {
        val session = openSession(project, clock = clock)
        val model = workspace(session)
        advanceUntilIdle()
        model.onEvent(WorkspaceEvent.Seek(120))

        model.onEvent(WorkspaceEvent.PlayAround)
        assertEquals(60, model.state.value.playheadFrame)
        assertTrue(program.state.value.playing)
        assertEquals("播放头前后 2 秒", model.state.value.playRange)
        assertEquals("播放播放头前后 2 秒", model.state.value.toast)
        program.seekTo(5_000) // frame 150: still inside
        assertTrue(program.state.value.playing)
        program.seekTo(6_040) // past 180: stops and goes back to 120
        assertFalse(program.state.value.playing)
        assertEquals(120, model.state.value.playheadFrame)
        assertNull(model.state.value.playRange)

        model.onEvent(WorkspaceEvent.PlayInToOut)
        assertEquals("先标记时间线入点和出点（I / O）", model.state.value.toast)
        session.update { d -> d.copy(timeline = d.timeline.copy(marks = TimelineMarks(30, 90))) }
        model.onEvent(WorkspaceEvent.PlayInToOut)
        assertEquals(30, model.state.value.playheadFrame)
        model.onEvent(WorkspaceEvent.SetPlaying(false))
        assertNull("paused by hand", model.state.value.playRange)
        program.play()
        program.seekTo(3_100)
        assertTrue("no range left: plays on past Out", program.state.value.playing)
    }

    @Test
    fun `proxy handling is the project's setting, not an undo step, and says how many proxies there are`() = runTest {
        val session = openSession(project, clock = clock)
        val proxies = com.example.prismstudio.p40b43a88.data.repository.FakeProxyRepository()
        val model = WorkspaceViewModel(session, program, source, FakeDeviceMediaRepository(), FakeReverseCopyRepository(), labels = ProjectLabelsUseCase(ZoneOffset.UTC), proxies = proxies)
        advanceUntilIdle()
        val videos = session.document.value!!.media.count { it.kind == com.example.prismstudio.p40b43a88.domain.model.MediaKind.VIDEO }
        assertEquals("0 / $videos 个视频有代理；在媒体页为素材生成代理", model.state.value.proxyNote)
        val undoBefore = session.history.value.undoLabel
        model.onEvent(WorkspaceEvent.SetUseProxies(true))
        assertTrue(session.document.value!!.useProxies)
        assertTrue(model.state.value.useProxies)
        assertEquals("已开启代理播放：还没有代理，在媒体页为素材生成", model.state.value.toast)
        assertEquals(undoBefore, session.history.value.undoLabel)
        val first = session.document.value!!.media.first { it.kind == com.example.prismstudio.p40b43a88.domain.model.MediaKind.VIDEO }
        proxies.finish(first.id, com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus.Ready("file:///p.mp4", 960, 540, 1))
        assertEquals("已开：1 / $videos 个视频用代理播放，渲染始终用原片", model.state.value.proxyNote)
        model.onEvent(WorkspaceEvent.SetUseProxies(false))
        assertFalse(session.document.value!!.useProxies)
        assertEquals("已改回原始素材播放", model.state.value.toast)
    }

    @Test
    fun `the ultrawide layout is the headset's preference, not an undo step, and says what it does`() = runTest {
        val session = openSession(project, clock = clock)
        val layout = com.example.prismstudio.p40b43a88.data.repository.LayoutPreferencesRepository.InMemory()
        val model = WorkspaceViewModel(session, program, source, FakeDeviceMediaRepository(), FakeReverseCopyRepository(), labels = ProjectLabelsUseCase(ZoneOffset.UTC), layout = layout)
        advanceUntilIdle()
        assertFalse(model.state.value.ultrawide)
        val undoBefore = session.history.value.undoLabel
        model.onEvent(WorkspaceEvent.SetUltrawide(true))
        assertTrue(layout.ultrawide.value)
        assertTrue(model.state.value.ultrawide)
        assertEquals("超宽布局：窗口加宽到 2700 dp，时间轴铺满底部，上方并排两个监视器", model.state.value.toast)
        assertEquals("not part of the project's history", undoBefore, session.history.value.undoLabel)
        model.onEvent(WorkspaceEvent.ToastShown)
        model.onEvent(WorkspaceEvent.SetUltrawide(true))
        assertEquals("already on: nothing to say", null, model.state.value.toast)
        model.onEvent(WorkspaceEvent.SetUltrawide(false))
        assertFalse(model.state.value.ultrawide)
        assertEquals("已恢复标准布局", model.state.value.toast)
        // A workspace opened later (another project) starts in the layout the headset keeps.
        layout.setUltrawide(true)
        val next = WorkspaceViewModel(session, program, source, FakeDeviceMediaRepository(), FakeReverseCopyRepository(), labels = ProjectLabelsUseCase(ZoneOffset.UTC), layout = layout)
        advanceUntilIdle()
        assertTrue(next.state.value.ultrawide)
    }

    @Test
    fun `loop starts In to Out again, or the timeline when it runs out`() = runTest {
        val session = openSession(project, clock = clock)
        val model = workspace(session)
        advanceUntilIdle()
        model.onEvent(WorkspaceEvent.SetLoop(true))
        assertTrue(model.state.value.loop)
        assertEquals("循环播放：开", model.state.value.toast)

        model.onEvent(WorkspaceEvent.SetPlaying(true))
        program.seekTo(7_990)
        program.pause() // the player stops at the timeline's end
        assertTrue(program.state.value.playing)
        assertEquals(0, model.state.value.playheadFrame)

        session.update { d -> d.copy(timeline = d.timeline.copy(marks = TimelineMarks(30, 90))) }
        program.seekTo(3_000)
        assertEquals(30, model.state.value.playheadFrame)
        assertTrue(program.state.value.playing)
        model.onEvent(WorkspaceEvent.SetLoop(false))
        model.onEvent(WorkspaceEvent.SetPlaying(false))
    }

    @Test
    fun `a project without media opens on the Media stage`() = runTest {
        val session = openSession(UiFixtures.document(name = "Blank"), clock = clock)

        val model = workspace(session)
        advanceUntilIdle()

        assertEquals(WorkspaceStage.MEDIA, model.state.value.stage)
    }

    @Test
    fun `every document change reaches the program monitor and the shell`() = runTest {
        val session = openSession(project, clock = clock)
        val model = workspace(session)
        model.onEvent(WorkspaceEvent.SelectStage(WorkspaceStage.COLOR))

        session.edit("重命名") { it.copy(name = "Harbour Film v2") }
        session.edit("删除片段") { d -> d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.filterNot { it.id == "c2" })) }
        advanceUntilIdle()

        assertSame(session.document.value, program.document)
        assertEquals(90, program.document?.durationFrames)
        assertEquals("Harbour Film v2", model.state.value.projectName)
        assertEquals(90, model.state.value.durationFrames)
        assertEquals("edits do not reset the stage", WorkspaceStage.COLOR, model.state.value.stage)
    }

    @Test
    fun `switching stage pauses both monitors, re-selecting the current stage does nothing`() = runTest {
        val model = workspace(openSession(project, clock = clock))
        program.play()
        source.play()
        assertTrue(model.state.value.playing)

        model.onEvent(WorkspaceEvent.SelectStage(WorkspaceStage.DELIVER))

        assertEquals(WorkspaceStage.DELIVER, model.state.value.stage)
        assertFalse(program.state.value.playing)
        assertFalse(source.state.value.playing)
        assertFalse(model.state.value.playing)

        program.play()
        model.onEvent(WorkspaceEvent.SelectStage(WorkspaceStage.DELIVER))
        assertTrue(program.state.value.playing)
    }

    @Test
    fun `the history menu lists the steps and goes back or forward through several at once`() = runTest {
        var t = 0L
        val session = openSession(project, clock = { t })
        val model = workspace(session)
        listOf("添加标记", "移动标记", "删除标记").forEachIndexed { i, label ->
            t += 10_000
            session.edit(label) { d -> d.copy(timeline = d.timeline.copy(markers = listOf(Marker("m-1", 45 + i, "标记 $i")))) }
        }
        assertEquals(listOf("删除标记", "移动标记", "添加标记"), model.state.value.undoHistory)
        assertEquals(emptyList<String>(), model.state.value.redoHistory)

        model.onEvent(WorkspaceEvent.UndoSteps(2))
        assertEquals("已撤销 2 步，回到“移动标记”之前", model.state.value.toast)
        assertEquals(45, session.document.value!!.timeline.markers.single().frame)
        assertEquals(listOf("添加标记"), model.state.value.undoHistory)
        assertEquals(listOf("移动标记", "删除标记"), model.state.value.redoHistory)

        model.onEvent(WorkspaceEvent.RedoSteps(2))
        assertEquals("已重做 2 步，到“删除标记”之后", model.state.value.toast)
        assertEquals(47, session.document.value!!.timeline.markers.single().frame)
        model.onEvent(WorkspaceEvent.UndoSteps(1))
        assertEquals("已撤销：删除标记", model.state.value.toast)
    }

    @Test
    fun `undo and redo go through the session and toast the edit label`() = runTest {
        val session = openSession(project, clock = clock)
        val model = workspace(session)

        model.onEvent(WorkspaceEvent.Undo)
        assertNull("nothing to undo yet", model.state.value.toast)

        session.edit("添加标记") { d -> d.copy(timeline = d.timeline.copy(markers = listOf(Marker("m-1", 45, "标记 1")))) }
        assertTrue(model.state.value.canUndo)
        assertEquals("添加标记", model.state.value.undoLabel)

        model.onEvent(WorkspaceEvent.Undo)
        assertEquals("已撤销：添加标记", model.state.value.toast)
        assertTrue(session.document.value!!.timeline.markers.isEmpty())
        assertFalse(model.state.value.canUndo)
        assertTrue(model.state.value.canRedo)
        assertEquals("添加标记", model.state.value.redoLabel)

        model.onEvent(WorkspaceEvent.ToastShown)
        assertNull(model.state.value.toast)

        model.onEvent(WorkspaceEvent.Redo)
        assertEquals("已重做：添加标记", model.state.value.toast)
        assertEquals(1, session.document.value!!.timeline.markers.size)
        assertFalse(model.state.value.canRedo)
    }

    @Test
    fun `play, seek and jog drive the program transport within the timeline`() = runTest {
        val model = workspace(openSession(project, clock = clock))

        model.onEvent(WorkspaceEvent.SetPlaying(true))
        assertTrue(program.state.value.playing)
        assertTrue(model.state.value.playing)
        model.onEvent(WorkspaceEvent.SetPlaying(false))
        assertFalse(program.state.value.playing)

        model.onEvent(WorkspaceEvent.Seek(45))
        assertEquals(1_500L, program.state.value.positionMs)
        assertEquals(45, model.state.value.playheadFrame)
        assertEquals("00:00:01:15", model.state.value.timecode)

        model.onEvent(WorkspaceEvent.Seek(-20))
        assertEquals(0, model.state.value.playheadFrame)
        assertEquals(0L, program.state.value.positionMs)

        model.onEvent(WorkspaceEvent.Seek(10_000))
        assertEquals(240, model.state.value.playheadFrame)
        assertEquals(8_000L, program.state.value.positionMs)

        program.play()
        model.onEvent(WorkspaceEvent.Jog(-15))
        assertFalse("jogging pauses playback", program.state.value.playing)
        assertEquals(225, model.state.value.playheadFrame)
        assertEquals(7_500L, program.state.value.positionMs)
    }

    @Test
    fun `single-frame steps move the playhead by exactly one frame at 30 fps`() = runTest {
        val model = workspace(openSession(project, clock = clock))
        model.onEvent(WorkspaceEvent.Seek(100))

        model.onEvent(WorkspaceEvent.Jog(1))
        assertEquals("101 frames is 3366.7 ms, so the seek must not truncate to 3366 ms", 101, model.state.value.playheadFrame)
        model.onEvent(WorkspaceEvent.Jog(1))
        assertEquals(102, model.state.value.playheadFrame)
        model.onEvent(WorkspaceEvent.Jog(-1))
        assertEquals(101, model.state.value.playheadFrame)
    }

    @Test
    fun `previous and next edit jump between clip boundaries`() = runTest {
        val model = workspace(openSession(project, clock = clock))

        model.onEvent(WorkspaceEvent.NextEdit)
        assertEquals(30, model.state.value.playheadFrame)
        model.onEvent(WorkspaceEvent.NextEdit)
        assertEquals(60, model.state.value.playheadFrame)

        model.onEvent(WorkspaceEvent.Seek(100))
        model.onEvent(WorkspaceEvent.PreviousEdit)
        assertEquals(90, model.state.value.playheadFrame)
        assertEquals(3_000L, program.state.value.positionMs)
        model.onEvent(WorkspaceEvent.PreviousEdit)
        assertEquals(60, model.state.value.playheadFrame)

        model.onEvent(WorkspaceEvent.Seek(240))
        model.onEvent(WorkspaceEvent.NextEdit)
        assertEquals("no edit point after the end", 240, model.state.value.playheadFrame)
    }

    @Test
    fun `missing source files are marked offline outside the undo history and reported`() = runTest {
        val session = openSession(project, clock = clock)
        val device = FakeDeviceMediaRepository(missingUris = setOf(v2.uri))

        val model = workspace(session, device)
        advanceUntilIdle()

        val media = session.document.value!!.media.associateBy { it.id }
        assertFalse(media.getValue("v1").offline)
        assertTrue(media.getValue("v2").offline)
        assertEquals("1 个素材无法读取，请检查读取权限或在媒体页重新链接。", model.state.value.toast)
        assertFalse("offline detection is not an undoable edit", session.history.value.canUndo)
        assertTrue(program.document!!.media.first { it.id == "v2" }.offline)
    }

    @Test
    fun `a late file check preserves a relink and unchecked assets added before it returns`() = runTest {
        val ready = CompletableDeferred<Boolean>()
        val device = object : DeviceMediaRepository by FakeDeviceMediaRepository() {
            override suspend fun isAvailable(uri: String) = if (uri == v1.uri) ready.await() else true
        }
        val session = openSession(project, clock = clock)
        val model = workspace(session, device)
        val newAsset = UiFixtures.video("new").copy(offline = true)
        session.update { d -> d.copy(media = listOf(v1.copy(uri = "content://relinked"), v2, newAsset)) }
        ready.complete(false)
        advanceUntilIdle()
        assertEquals(listOf(false, false, true), session.document.value!!.media.map { it.offline })
        assertNull(model.state.value.toast)
    }

    @Test
    fun `closing and reopening the same project checks its media again`() = runTest {
        val session = openSession(project, clock = clock)
        val device = FakeDeviceMediaRepository(missingUris = setOf(v2.uri))
        workspace(session, device)
        advanceUntilIdle()
        assertTrue(session.document.value!!.asset(v2.id)!!.offline)
        assertTrue(session.close())
        advanceUntilIdle()
        device.missingUris = emptySet()
        assertTrue(session.open(project.id))
        advanceUntilIdle()
        assertFalse(session.document.value!!.asset(v2.id)!!.offline)
    }

    @Test
    fun `media that is available again is marked online without a toast`() = runTest {
        val stale = project.copy(media = listOf(v1.copy(offline = true), v2))
        val session = openSession(stale, clock = clock)

        val model = workspace(session, FakeDeviceMediaRepository())
        advanceUntilIdle()

        assertTrue(session.document.value!!.media.none { it.offline })
        assertNull(model.state.value.toast)
    }

    @Test
    fun `the save status follows the debounced autosave`() = runTest {
        val session = openSession(project, clock = clock)
        val model = workspace(session)
        assertEquals("已自动保存 · 09:30", model.state.value.saveStatus)

        clock.now += 60_000
        session.edit("重命名") { it.copy(name = "Renamed") }
        assertEquals("正在保存…", model.state.value.saveStatus)

        advanceTimeBy(701)
        assertEquals("已自动保存 · 09:31", model.state.value.saveStatus)
    }

    @Test
    fun `a failed autosave is reported in the save status`() = runTest {
        val projects = FailingSaveProjects(FakeProjectRepository(listOf(project)))
        val session = openSession(project, projects = projects, clock = clock)
        val model = workspace(session)

        session.edit("重命名") { it.copy(name = "Renamed") }
        advanceTimeBy(701)

        assertEquals("保存失败 · 检查存储后可再次返回重试", model.state.value.saveStatus)
    }

    @Test
    fun `clearing the workspace pauses and unloads both monitors`() = runTest {
        val model = workspace(openSession(project, clock = clock))
        source.load(v1)
        program.play()
        source.play()

        clearViewModel(model)

        assertNull(program.document)
        assertFalse(program.state.value.playing)
        assertNull(source.asset)
        assertFalse(source.state.value.playing)
    }
}

/** Loads fine but every save fails. */
private class FailingSaveProjects(delegate: FakeProjectRepository) : ProjectRepository by delegate {
    override suspend fun save(document: ProjectDocument) {
        throw IOException("read-only storage")
    }
}
