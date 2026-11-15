package com.example.prismstudio.p40b43a88.ui.projects

import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import com.example.prismstudio.p40b43a88.data.repository.FakeProjectBackupRepository
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProjectBackupRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectBackupRepository
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.usecase.CreateProjectUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectLabelsUseCase
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.TestClock
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.UiFixtures.NOW
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    @get:Rule val folder = TemporaryFolder()
    private val clock = TestClock()
    private val ids = SequentialIds()

    private val alpha = UiFixtures.document(id = "p-a", name = "Alpha Cut", modifiedAt = NOW - 3 * DAY)
    private val beach = UiFixtures.document(
        id = "p-b",
        name = "Beach Travel",
        modifiedAt = NOW - HOUR,
        media = listOf(UiFixtures.video("v1")),
        clips = listOf(
            UiFixtures.clip("c1", start = 0, frames = 300, assetId = "v1", linkId = "l1"),
            UiFixtures.clip("c2", start = 0, frames = 300, trackId = Timeline.A1, assetId = "v1", linkId = "l1"),
        ),
    )
    private val city = UiFixtures.document(id = "p-c", name = "City Vlog", modifiedAt = NOW - DAY)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.library(
        projects: ProjectRepository = FakeProjectRepository(listOf(alpha, beach, city)),
        session: EditorSessionRepository = DefaultEditorSessionRepository(projects, backgroundScope, clock),
        backups: ProjectBackupRepository = ProjectBackupRepository.None,
    ) = ProjectsViewModel(
        projects = projects,
        session = session,
        previews = FakeMediaPreviewRepository(),
        create = CreateProjectUseCase(ids, clock),
        backups = backups,
        backupRules = ProjectBackupsUseCase(ids, ZoneOffset.UTC),
        labels = ProjectLabelsUseCase(ZoneOffset.UTC),
        clock = clock,
    )

    @Test
    fun `about - the version and the notices by project, a license's text in place of the list, and back`() = runTest {
        val notices = object : com.example.prismstudio.p40b43a88.data.repository.LicenseNoticesRepository {
            override fun libraries() = "androidx.media3\tmedia3-common\t1.9.3\tapache-2.0\nandroidx.media3\tmedia3-effect\t1.9.3\tapache-2.0\n" +
                "com.pico.spatial.core\tcore\t6.1.9\tpico-sdk\norg.checkerframework\tchecker-qual\t3.43.0\tmit\n"
            override fun text(license: com.example.prismstudio.p40b43a88.domain.model.OssLicense) =
                if (license == com.example.prismstudio.p40b43a88.domain.model.OssLicense.PICO_SDK) null else "TEXT ${license.id}"
        }
        val projects = FakeProjectRepository(listOf(alpha))
        val model = ProjectsViewModel(
            projects = projects,
            session = DefaultEditorSessionRepository(projects, backgroundScope, clock),
            previews = FakeMediaPreviewRepository(),
            create = CreateProjectUseCase(ids, clock),
            clock = clock,
            notices = notices,
            version = "1.0.0 (10000)",
        )
        model.onEvent(ProjectsEvent.OpenAbout)
        val about = model.state.value.about!!
        assertEquals("1.0.0 (10000)", about.version)
        assertEquals(4, about.componentCount)
        assertEquals(listOf("PICO Spatial SDK", "AndroidX Media3", "Checker Framework qualifiers"), about.families.map { it.name })
        assertEquals("media3-common 1.9.3 · media3-effect 1.9.3", about.families[1].components)
        assertEquals(2, about.families[1].count)
        assertEquals("PICO 开发者协议", about.families[0].licenseLabel)
        assertEquals("licenses with a text to show", listOf(com.example.prismstudio.p40b43a88.domain.model.OssLicense.APACHE_2, com.example.prismstudio.p40b43a88.domain.model.OssLicense.MIT), about.licenses)

        model.onEvent(ProjectsEvent.ShowLicense(com.example.prismstudio.p40b43a88.domain.model.OssLicense.APACHE_2))
        assertEquals("Apache License 2.0", model.state.value.about!!.licenseTitle)
        assertEquals("TEXT apache-2.0", model.state.value.about!!.licenseText)
        model.onEvent(ProjectsEvent.ShowLicense(null))
        assertFalse(model.state.value.about!!.showingText)
        model.onEvent(ProjectsEvent.CloseAbout)
        assertNull(model.state.value.about)
    }

    @Test
    fun `project backups - listed newest first, one taken now, one restored as a new project, all gone with the project`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val backups = FakeProjectBackupRepository()
        backups.write(beach.copy(name = "Beach Travel"), NOW - 2 * HOUR)
        val model = ProjectsViewModel(
            projects = projects,
            session = DefaultEditorSessionRepository(projects, backgroundScope, clock),
            previews = FakeMediaPreviewRepository(),
            create = CreateProjectUseCase(ids, clock),
            backups = backups,
            backupRules = ProjectBackupsUseCase(ids, ZoneOffset.UTC),
            labels = ProjectLabelsUseCase(ZoneOffset.UTC),
            clock = clock,
        )
        advanceUntilIdle()
        model.onEvent(ProjectsEvent.Select("p-b"))
        model.onEvent(ProjectsEvent.OpenBackups)
        advanceUntilIdle()
        val sheet = model.state.value.backups!!
        assertEquals("Beach Travel", sheet.projectName)
        assertEquals(listOf("10月4日 07:30"), sheet.rows.map { it.title })
        assertEquals("2 小时前 · 1 个片段 · 00:00:10:00 · 1 KB", sheet.rows.single().detail)

        model.onEvent(ProjectsEvent.BackupNow)
        advanceUntilIdle()
        assertEquals("已备份“Beach Travel”", model.state.value.toast)
        assertEquals(listOf(NOW, NOW - 2 * HOUR), model.state.value.backups!!.rows.map { it.takenAtMillis })

        model.onEvent(ProjectsEvent.RestoreBackup(NOW - 2 * HOUR))
        advanceUntilIdle()
        val restored = projects.library.value.summaries.single { it.name == "Beach Travel（备份 10-04 07:30）" }
        assertEquals(restored.id, model.state.value.selectedId)
        assertNull(model.state.value.backups)
        assertEquals("已恢复为新项目：Beach Travel（备份 10-04 07:30）", model.state.value.toast)
        assertNotNull("the original stays", projects.load("p-b"))

        model.onEvent(ProjectsEvent.Select("p-b"))
        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()
        assertTrue("its backups go with it", backups.list("p-b").isEmpty())
    }

    @Test
    fun `lists projects most recently modified first and selects the newest`() = runTest {
        val model = library()
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.loading)
        assertFalse(state.isEmpty)
        assertEquals(listOf("Beach Travel", "City Vlog", "Alpha Cut"), state.items.map { it.name })
        assertEquals(3, state.totalCount)
        assertEquals("3 个项目 · 按最近修改排序", state.summary)
        assertEquals("p-b", state.selectedId)
        assertEquals("Beach Travel", state.selected?.name)
    }

    @Test
    fun `cards and the detail panel show format, duration and relative modification labels`() = runTest {
        val model = library()
        advanceUntilIdle()

        val items = model.state.value.items
        assertEquals("1080p · 30 fps · 00:10", items[0].meta)
        assertEquals("1080p · 30 fps · 00:00", items[2].meta)
        assertEquals("今天 08:30", items[0].modified)
        assertEquals("昨天 09:30", items[1].modified)
        assertEquals("10月1日", items[2].modified)
        assertEquals(
            listOf(
                "时间线格式" to "1920×1080 横屏",
                "帧率" to "30 fps",
                "时长" to "00:00:10:00",
                "片段" to "1 个片段",
                "修改时间" to "今天 08:30",
            ),
            model.state.value.selected?.rows,
        )
    }

    @Test
    fun `search filters by name and moves the selection to a visible project`() = runTest {
        val model = library()
        model.onEvent(ProjectsEvent.Select("p-a"))
        assertEquals("p-a", model.state.value.selectedId)

        model.onEvent(ProjectsEvent.Query("BEACH"))
        var state = model.state.value
        assertEquals(listOf("p-b"), state.items.map { it.id })
        assertEquals("找到 1 个项目 · 共 3 个", state.summary)
        assertEquals(3, state.totalCount)
        assertEquals("p-b", state.selectedId)

        model.onEvent(ProjectsEvent.Query("no such project"))
        state = model.state.value
        assertTrue(state.items.isEmpty())
        assertNull(state.selected)
        assertFalse("a search without hits is not the empty library", state.isEmpty)
        assertEquals("找到 0 个项目 · 共 3 个", state.summary)

        model.onEvent(ProjectsEvent.Query(""))
        assertEquals(3, model.state.value.items.size)
        assertEquals("3 个项目 · 按最近修改排序", model.state.value.summary)
    }

    @Test
    fun `an empty library reports the empty state once loaded`() = runTest {
        val model = library(projects = FakeProjectRepository())
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.loading)
        assertTrue(state.isEmpty)
        assertFalse(state.hasSelection)
        assertEquals("0 个项目 · 按最近修改排序", state.summary)
    }

    @Test
    fun `the new project form opens with defaults and clamps its fields`() = runTest {
        val model = library()

        model.onEvent(ProjectsEvent.StartNew)
        assertEquals(NewProjectUi(visible = true), model.state.value.newProject)
        assertEquals(30, model.state.value.newProject.let { it.frameRates[it.fpsIndex] })

        model.onEvent(ProjectsEvent.NewName("x".repeat(50)))
        model.onEvent(ProjectsEvent.NewFormat(99))
        model.onEvent(ProjectsEvent.NewFps(-3))
        val form = model.state.value.newProject
        assertEquals(40, form.name.length)
        assertEquals(FormatPreset.entries.lastIndex, form.formatIndex)
        assertEquals(0, form.fpsIndex)

        model.onEvent(ProjectsEvent.NewFps(10))
        assertEquals(ProjectFormat.FRAME_RATES.lastIndex, model.state.value.newProject.fpsIndex)

        model.onEvent(ProjectsEvent.CancelNew)
        assertFalse(model.state.value.newProject.visible)
    }

    @Test
    fun `Create starts the new project in the session with the chosen format`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val session = DefaultEditorSessionRepository(projects, backgroundScope, clock)
        val model = library(projects, session)

        model.onEvent(ProjectsEvent.StartNew)
        model.onEvent(ProjectsEvent.NewName("  Summer Trip  "))
        model.onEvent(ProjectsEvent.NewFormat(FormatPreset.UHD_LANDSCAPE.ordinal))
        model.onEvent(ProjectsEvent.NewFps(3))
        model.onEvent(ProjectsEvent.Create)
        advanceUntilIdle()

        val opened = session.document.value!!
        assertEquals("p-1", opened.id)
        assertEquals("Summer Trip", opened.name)
        assertEquals(ProjectFormat(3840, 2160, 60), opened.format)
        assertNotNull("the new project is stored", projects.load("p-1"))
        val state = model.state.value
        assertEquals("the form is reset", NewProjectUi(), state.newProject)
        assertFalse(state.busy)
        assertNull(state.toast)
        assertEquals(4, state.totalCount)
        assertEquals("Summer Trip", state.items.first().name)
    }

    @Test
    fun `a project that cannot be saved keeps the form open and explains why`() = runTest {
        val projects = FailingSaveRepository(FakeProjectRepository(listOf(alpha)))
        val session = DefaultEditorSessionRepository(projects, backgroundScope, clock)
        val model = library(projects, session)

        model.onEvent(ProjectsEvent.StartNew)
        model.onEvent(ProjectsEvent.NewName("Doomed"))
        model.onEvent(ProjectsEvent.Create)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("无法创建项目，请检查存储空间后重试", state.toast)
        assertFalse(state.busy)
        assertTrue(state.newProject.visible)
        assertEquals("Doomed", state.newProject.name)
        assertNull(session.document.value)
    }

    @Test
    fun `Create is ignored while a project is still being created`() = runTest {
        val stored = FakeProjectRepository()
        val gated = GatedSaveRepository(stored)
        val session = DefaultEditorSessionRepository(gated, backgroundScope, clock)
        val model = library(gated, session)

        model.onEvent(ProjectsEvent.StartNew)
        model.onEvent(ProjectsEvent.Create)
        runCurrent()
        assertTrue(model.state.value.busy)
        model.onEvent(ProjectsEvent.Create)
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.state.value.busy)
        assertEquals(listOf("p-1"), stored.library.value.summaries.map { it.id })
        assertEquals("p-1", session.document.value?.id)
    }

    @Test
    fun `OpenSelected opens the selected project in the session`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val session = DefaultEditorSessionRepository(projects, backgroundScope, clock)
        val model = library(projects, session)

        model.onEvent(ProjectsEvent.Select("p-c"))
        model.onEvent(ProjectsEvent.OpenSelected)
        advanceUntilIdle()

        assertEquals("p-c", session.document.value?.id)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.toast)
    }

    @Test
    fun `an unreadable project is neither opened nor duplicated and the user is told`() = runTest {
        val projects = UnreadableRepository(FakeProjectRepository(listOf(alpha, beach)))
        val session = DefaultEditorSessionRepository(projects, backgroundScope, clock)
        val model = library(projects, session)

        model.onEvent(ProjectsEvent.OpenSelected)
        advanceUntilIdle()
        assertNull(session.document.value)
        assertEquals("无法打开项目：文件已损坏或不存在", model.state.value.toast)
        assertFalse(model.state.value.busy)

        model.onEvent(ProjectsEvent.ToastShown)
        assertNull(model.state.value.toast)
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()
        assertEquals("无法读取项目，副本未创建", model.state.value.toast)
        assertEquals(2, model.state.value.totalCount)
    }

    @Test
    fun `a copy or a restored backup that cannot be saved is explained, not a crash`() = runTest {
        val projects = FailingSaveRepository(FakeProjectRepository(listOf(alpha, beach)))
        val backups = FakeProjectBackupRepository()
        backups.write(beach, NOW - 2 * HOUR)
        val model = ProjectsViewModel(
            projects = projects,
            session = DefaultEditorSessionRepository(projects, backgroundScope, clock),
            previews = FakeMediaPreviewRepository(),
            create = CreateProjectUseCase(ids, clock),
            backups = backups,
            backupRules = ProjectBackupsUseCase(ids, ZoneOffset.UTC),
            labels = ProjectLabelsUseCase(ZoneOffset.UTC),
            clock = clock,
        )
        advanceUntilIdle()
        model.onEvent(ProjectsEvent.Select("p-b"))
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()
        assertEquals("无法创建副本，请检查存储空间后重试", model.state.value.toast)
        assertEquals(2, model.state.value.totalCount)

        model.onEvent(ProjectsEvent.OpenBackups)
        advanceUntilIdle()
        model.onEvent(ProjectsEvent.RestoreBackup(NOW - 2 * HOUR))
        advanceUntilIdle()
        assertEquals("无法恢复备份，请检查存储空间后重试", model.state.value.toast)
        assertEquals(2, model.state.value.totalCount)
        assertNotNull("the sheet stays open to try again", model.state.value.backups)
    }

    @Test
    fun `Duplicate stores a copy of the selected project and names it in a toast`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val model = library(projects)

        model.onEvent(ProjectsEvent.Select("p-b"))
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("已创建副本：Beach Travel 副本", state.toast)
        assertEquals(4, state.totalCount)
        assertEquals("the copy is the most recently modified project", "Beach Travel 副本", state.items.first().name)
        val copy = projects.load("p-1")!!
        assertEquals(beach.timeline, copy.timeline)
        assertEquals(clock.now, copy.modifiedAtMillis)
    }

    // duplicate() publishes right after projects.save(), but the cached summaries are only refreshed by the
    // projects-flow collector. With a repository that emits before save() returns (FakeProjectRepository on
    // Main.immediate) that collector has not run yet, publish() cannot see the copy and re-selects the first
    // visible project. FileProjectRepository's IO hop happens to let the collector run first on a device.
    @Test
    fun `Duplicate selects the new copy`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val model = library(projects)

        model.onEvent(ProjectsEvent.Select("p-b"))
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()

        assertEquals("p-1", model.state.value.selectedId)
        assertEquals("Beach Travel 副本", model.state.value.selected?.name)
    }

    @Test
    fun `two clicks during a slow duplicate write one copy and respect the latest selection`() = runTest {
        val store = FakeProjectRepository(listOf(beach, city))
        val gated = GatedSaveRepository(store)
        val model = library(gated)
        model.onEvent(ProjectsEvent.Select(beach.id))
        model.onEvent(ProjectsEvent.Duplicate)
        model.onEvent(ProjectsEvent.Duplicate)
        model.onEvent(ProjectsEvent.Select(city.id))
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, store.saveCount)
        assertEquals(city.id, model.state.value.selectedId)
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()
        assertEquals(2, store.saveCount)
        assertEquals("City Vlog 副本", model.state.value.selected?.name)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `a pending manual backup writes once and a restore can start after it finishes`() = runTest {
        val store = FakeProjectRepository(listOf(beach))
        val snapshots = FakeProjectBackupRepository()
        snapshots.write(beach, NOW - HOUR)
        val gate = CompletableDeferred<Unit>()
        val backups = object : ProjectBackupRepository by snapshots {
            override suspend fun write(document: ProjectDocument, takenAtMillis: Long): ProjectBackup {
                gate.await()
                return snapshots.write(document, takenAtMillis)
            }
        }
        val model = library(store, backups = backups)
        model.onEvent(ProjectsEvent.OpenBackups)
        model.onEvent(ProjectsEvent.BackupNow)
        model.onEvent(ProjectsEvent.BackupNow)
        model.onEvent(ProjectsEvent.RestoreBackup(NOW - HOUR))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(beach.id to (NOW - HOUR), beach.id to NOW), snapshots.written)
        assertEquals(0, store.saveCount)
        assertEquals(NOW, model.state.value.backups!!.rows.first().takenAtMillis)
        model.onEvent(ProjectsEvent.RestoreBackup(NOW))
        advanceUntilIdle()
        assertEquals(1, store.saveCount)
        assertNull(model.state.value.backups)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `a pending restore writes one project and leaves a later library selection in place`() = runTest {
        val store = FakeProjectRepository(listOf(beach, city))
        val gated = GatedSaveRepository(store)
        val backups = FakeProjectBackupRepository()
        backups.write(beach, NOW)
        val model = library(gated, backups = backups)
        model.onEvent(ProjectsEvent.Select(beach.id))
        model.onEvent(ProjectsEvent.OpenBackups)
        model.onEvent(ProjectsEvent.RestoreBackup(NOW))
        model.onEvent(ProjectsEvent.RestoreBackup(NOW))
        model.onEvent(ProjectsEvent.CloseBackups)
        model.onEvent(ProjectsEvent.Select(city.id))
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, store.saveCount)
        assertEquals(3, store.library.value.summaries.size)
        assertEquals(city.id, model.state.value.selectedId)
        assertNull(model.state.value.backups)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `copy and backup explain a storage read exception and allow the next attempt`() = runTest {
        val store = FakeProjectRepository(listOf(beach))
        var unavailable = true
        val projects = object : ProjectRepository by store {
            override suspend fun load(id: String): ProjectDocument? {
                if (unavailable) throw IOException("unreadable")
                return store.load(id)
            }
        }
        val snapshots = FakeProjectBackupRepository()
        val model = library(projects, backups = snapshots)
        model.onEvent(ProjectsEvent.Duplicate)
        advanceUntilIdle()
        assertEquals("无法读取项目，副本未创建", model.state.value.toast)
        assertEquals(0, store.saveCount)
        model.onEvent(ProjectsEvent.OpenBackups)
        model.onEvent(ProjectsEvent.BackupNow)
        advanceUntilIdle()
        assertEquals("无法读取项目，未备份", model.state.value.toast)
        assertTrue(snapshots.written.isEmpty())
        unavailable = false
        model.onEvent(ProjectsEvent.BackupNow)
        advanceUntilIdle()
        assertEquals(listOf(beach.id to NOW), snapshots.written)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `deleting asks for confirmation, removes the project and selects the next one`() = runTest {
        val projects = FakeProjectRepository(listOf(alpha, beach, city))
        val model = library(projects)
        model.onEvent(ProjectsEvent.Select("p-b"))

        model.onEvent(ProjectsEvent.RequestDelete)
        assertEquals(beach.id, model.state.value.deleteTarget?.id)
        model.onEvent(ProjectsEvent.DismissDelete)
        assertNull(model.state.value.deleteTarget)
        assertNotNull("dismissing keeps the project", projects.load("p-b"))

        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()

        val state = model.state.value
        assertNull(state.deleteTarget)
        assertEquals("已删除“Beach Travel”", state.toast)
        assertNull(projects.load("p-b"))
        assertEquals(listOf("p-c", "p-a"), state.items.map { it.id })
        assertEquals("p-c", state.selectedId)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `confirmation stays bound to its project even when a filter changes the selection`() = runTest {
        val store = FakeProjectRepository(listOf(beach, city))
        val model = library(store)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        assertNotNull(store.load(beach.id))
        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.Query("City"))
        assertEquals(city.id, model.state.value.selectedId)
        assertEquals(beach.name, model.state.value.deleteTarget?.name)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()
        assertNull(store.load(beach.id))
        assertNotNull(store.load(city.id))
        assertEquals(city.id, model.state.value.selectedId)
    }

    @Test
    fun `failed deletion preserves project and backups and the next confirmation can retry`() = runTest {
        val store = FakeProjectRepository(listOf(beach))
        val backups = FakeProjectBackupRepository()
        backups.write(beach, NOW)
        var rejected = true
        val projects = object : ProjectRepository by store {
            override suspend fun delete(id: String) {
                if (rejected) throw IOException("read only")
                store.delete(id)
            }
        }
        val model = library(projects, backups = backups)
        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()
        assertEquals(beach.id, model.state.value.selectedId)
        assertEquals("无法删除“Beach Travel”，项目和备份均已保留", model.state.value.toast)
        assertNotNull(backups.read(beach.id, NOW))
        assertFalse(model.state.value.busy)

        rejected = false
        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()
        assertNull(store.load(beach.id))
        assertTrue(backups.list(beach.id).isEmpty())
    }

    @Test
    fun `failed backup cleanup reports partial deletion`() = runTest {
        val store = FakeProjectRepository(listOf(beach))
        val backups = object : ProjectBackupRepository by FakeProjectBackupRepository() {
            override suspend fun deleteAll(projectId: String) { throw IOException("read only") }
        }
        val model = library(store, backups = backups)
        model.onEvent(ProjectsEvent.RequestDelete)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()
        assertNull(store.load(beach.id))
        assertEquals("已删除“Beach Travel”，部分备份未能清理", model.state.value.toast)
    }

    @Test
    fun `unavailable library and backup lists show retry and recover without reopening the app`() = runTest {
        val store = FakeProjectRepository(listOf(beach))
        var unavailable = true
        val projects = object : ProjectRepository by store {
            override suspend fun refresh() { if (unavailable) throw IOException("unavailable") else store.refresh() }
        }
        val backupStore = FakeProjectBackupRepository()
        backupStore.write(beach, NOW)
        var failBackups = true
        val backups = object : ProjectBackupRepository by backupStore {
            override suspend fun list(projectId: String) = if (failBackups) throw IOException("unavailable") else backupStore.list(projectId)
        }
        val model = library(projects, backups = backups)
        assertTrue(model.state.value.loadFailed)
        assertFalse(model.state.value.isEmpty)
        unavailable = false
        model.onEvent(ProjectsEvent.RetryLoad)
        advanceUntilIdle()
        assertFalse(model.state.value.loadFailed)
        assertEquals(beach.id, model.state.value.selectedId)
        model.onEvent(ProjectsEvent.OpenBackups)
        assertTrue(model.state.value.backups!!.loadFailed)
        failBackups = false
        model.onEvent(ProjectsEvent.RetryBackups)
        advanceUntilIdle()
        assertFalse(model.state.value.backups!!.loadFailed)
        assertEquals(NOW, model.state.value.backups!!.rows.single().takenAtMillis)
    }

    @Test
    fun `a library of unreadable files restores the chosen version beside the preserved original`() = runTest {
        val files = folder.newFolder()
        val store = FileProjectRepository(files, Dispatchers.Unconfined)
        val backups = FileProjectBackupRepository(folder.newFolder(), Dispatchers.Unconfined)
        val original = File(files, "${beach.id}.json").apply { writeText("{truncated") }
        File(files, "${city.id}.json").writeText("")
        backups.write(beach, NOW - HOUR)
        backups.write(beach.copy(name = "Later cut"), NOW)
        val model = library(store, backups = backups)

        assertFalse(model.state.value.isEmpty)
        assertNull(model.state.value.selected)
        assertEquals(listOf(beach.id, city.id), model.state.value.unreadableIds)
        assertEquals(2, model.state.value.totalCount)
        model.onEvent(ProjectsEvent.OpenRecovery(city.id))
        assertTrue(model.state.value.backups!!.rows.isEmpty())
        assertFalse(model.state.value.backups!!.loadFailed)
        model.onEvent(ProjectsEvent.CloseBackups)
        model.onEvent(ProjectsEvent.Query("P-B.JSON"))
        assertEquals(listOf(beach.id), model.state.value.unreadableIds)
        model.onEvent(ProjectsEvent.OpenRecovery(beach.id))
        assertTrue(model.state.value.backups!!.recoveryOnly)
        assertEquals(listOf(NOW, NOW - HOUR), model.state.value.backups!!.rows.map { it.takenAtMillis })
        model.onEvent(ProjectsEvent.BackupNow)
        assertNull(model.state.value.toast)

        model.onEvent(ProjectsEvent.RestoreBackup(NOW - HOUR))
        advanceUntilIdle()
        val state = model.state.value
        assertNull(state.backups)
        assertEquals("", state.query)
        assertEquals(3, state.totalCount)
        val restored = store.load(state.selectedId!!)!!
        assertTrue(restored.name.startsWith("Beach Travel（备份 "))
        assertEquals(beach.timeline, restored.timeline)
        assertEquals("{truncated", original.readText())
        assertEquals(beach, backups.read(beach.id, NOW - HOUR))
        assertEquals(listOf(beach.id, city.id), state.unreadableIds)
    }

    @Test
    fun `a mixed library remains usable and rescan picks up an externally repaired project`() = runTest {
        val files = folder.newFolder()
        val store = FileProjectRepository(files, Dispatchers.Unconfined)
        store.save(beach)
        store.save(city)
        val file = File(files, "${beach.id}.json")
        val original = file.readText()
        file.writeText("")
        val model = library(store)
        assertEquals(listOf(city.id), model.state.value.items.map { it.id })
        assertEquals(listOf(beach.id), model.state.value.unreadableIds)
        model.onEvent(ProjectsEvent.Query("City"))
        assertEquals("找到 1 个项目 · 共 2 个", model.state.value.summary)
        assertTrue(model.state.value.unreadableIds.isEmpty())
        file.writeText(original)
        model.onEvent(ProjectsEvent.RetryLoad)
        model.onEvent(ProjectsEvent.Query(""))
        assertTrue(model.state.value.unreadableIds.isEmpty())
        assertEquals(setOf(beach.id, city.id), model.state.value.items.map { it.id }.toSet())
        model.onEvent(ProjectsEvent.OpenRecovery(beach.id))
        assertNull(model.state.value.backups)
    }

    @Test
    fun `RequestDelete without a selected project does not ask for confirmation`() = runTest {
        val model = library(projects = FakeProjectRepository())

        model.onEvent(ProjectsEvent.RequestDelete)
        assertNull(model.state.value.deleteTarget)
        model.onEvent(ProjectsEvent.ConfirmDelete)
        advanceUntilIdle()

        assertNull(model.state.value.toast)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

/** Every save fails, like a full disk. */
private class FailingSaveRepository(delegate: FakeProjectRepository) : ProjectRepository by delegate {
    override suspend fun save(document: ProjectDocument) {
        throw IOException("No space left on device")
    }
}

/** Lists projects whose files can no longer be read. */
private class UnreadableRepository(delegate: FakeProjectRepository) : ProjectRepository by delegate {
    override suspend fun load(id: String): ProjectDocument? = null
}

/** Holds every save until [gate] opens. */
private class GatedSaveRepository(private val delegate: FakeProjectRepository) : ProjectRepository by delegate {
    val gate = CompletableDeferred<Unit>()

    override suspend fun save(document: ProjectDocument) {
        gate.await()
        delegate.save(document)
    }
}
