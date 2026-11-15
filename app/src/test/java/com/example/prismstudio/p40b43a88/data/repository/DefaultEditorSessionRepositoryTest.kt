package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEditorSessionRepositoryTest {

    /** Fake wall clock: tests move it by hand to control coalescing and timestamps. */
    private var now = 10_000L

    private val original = projectOf(
        timeline = timelineOf(clipOf("c1", 0, 100), clipOf("c2", 100, 50)),
        id = "p-1",
        name = "Demo",
        modifiedAtMillis = 5_000L,
    )

    private fun TestScope.sessionOver(projects: ProjectRepository) = DefaultEditorSessionRepository(
        projects = projects,
        scope = backgroundScope,
        clock = { now },
        autosaveDelayMs = AUTOSAVE_MS,
        coalesceWindowMs = 1_500,
    )

    private fun rename(name: String): (ProjectDocument) -> ProjectDocument = { it.copy(name = name) }

    private fun dropClip(id: String): (ProjectDocument) -> ProjectDocument =
        { d -> d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.filter { it.id != id })) }

    private val DefaultEditorSessionRepository.doc: ProjectDocument get() = document.value!!

    @Test
    fun `autosaves back the project up when the last backup is ten minutes old, thinning older ones`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val backups = FakeProjectBackupRepository()
        val session = DefaultEditorSessionRepository(
            projects = projects, scope = backgroundScope, clock = { now }, autosaveDelayMs = AUTOSAVE_MS, coalesceWindowMs = 1_500,
            backups = backups, backupPolicy = com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase({ it }),
        )
        session.open("p-1")
        assertTrue("opening alone backs nothing up", backups.written.isEmpty())

        session.edit("重命名", null, rename("A"))
        advanceTimeBy(AUTOSAVE_MS + 1)
        runCurrent()
        assertEquals("the first autosave backs up", listOf("p-1" to now), backups.written)
        assertEquals("A", backups.read("p-1", now)!!.name)

        now += 5 * 60_000L
        session.edit("重命名", null, rename("B"))
        advanceTimeBy(AUTOSAVE_MS + 1)
        runCurrent()
        assertEquals("five minutes later: saved, not backed up", 1, backups.written.size)
        assertEquals("B", projects.load("p-1")!!.name)

        now += 5 * 60_000L
        session.edit("重命名", null, rename("C"))
        advanceTimeBy(AUTOSAVE_MS + 1)
        runCurrent()
        assertEquals(2, backups.written.size)
        assertEquals("C", backups.read("p-1", now)!!.name)

        // Reopened, the session reads when the last backup was rather than backing up again at once.
        session.open("p-1")
        now += 60_000L
        session.edit("重命名", null, rename("D"))
        advanceTimeBy(AUTOSAVE_MS + 1)
        runCurrent()
        assertEquals(2, backups.written.size)
    }

    @Test
    fun `opening a stored project starts with a clean history and no selection`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val session = sessionOver(projects)

        assertTrue(session.open("p-1"))

        assertEquals(original, session.document.value)
        assertEquals(HistoryState(), session.history.value)
        assertEquals(SaveState(savedAtMillis = 5_000L), session.saveState.value)
        assertNull(session.selectedClipId.value)
        assertEquals(0, projects.saveCount)
    }

    @Test
    fun `a render cut off when the app was killed is queued again when the project opens`() = runTest {
        // Release round R14: the job stayed "rendering" at its last percentage for good.
        val jobs = listOf(
            RenderJob("j-1", "Done", RenderSettings(fileName = "Done"), status = RenderStatus.DONE, progress = 1f),
            RenderJob("j-2", "Cut off", RenderSettings(fileName = "Cut off"), status = RenderStatus.RENDERING, progress = 0.59f),
            RenderJob("j-3", "Waiting", RenderSettings(fileName = "Waiting")),
        )
        val session = sessionOver(FakeProjectRepository(listOf(original.copy(jobs = jobs))))

        assertTrue(session.open("p-1"))

        val opened = session.document.value!!.jobs
        assertEquals(listOf(RenderStatus.DONE, RenderStatus.QUEUED, RenderStatus.QUEUED), opened.map { it.status })
        assertEquals(0f, opened[1].progress, 0f)
        assertEquals(1f, opened[0].progress, 0f)
    }

    @Test
    fun `opening a project that cannot be read fails and leaves nothing open`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        assertFalse(session.open("missing"))

        assertNull(session.document.value)
        assertEquals(HistoryState(), session.history.value)
    }

    @Test
    fun `leaving the app writes an edit still waiting for its autosave, and nothing when all is saved`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val session = sessionOver(projects)
        session.open("p-1")
        val saves = projects.saveCount

        session.edit("重命名", transform = rename("Flushed"))
        session.flush()
        assertEquals("written at once, before the autosave is due", "Flushed", projects.load("p-1")!!.name)
        assertEquals(saves + 1, projects.saveCount)
        advanceTimeBy(AUTOSAVE_MS + 1)
        assertEquals("the autosave it replaced does not write again", saves + 1, projects.saveCount)
        session.flush()
        assertEquals("nothing pending: nothing written", saves + 1, projects.saveCount)
        session.close()
        session.flush()
        assertEquals("no project open", saves + 1, projects.saveCount)
    }

    @Test
    fun `starting a new project saves it and opens it`() = runTest {
        val projects = FakeProjectRepository()
        val session = sessionOver(projects)

        session.start(original)

        assertEquals(1, projects.saveCount)
        assertEquals(original, projects.load("p-1"))
        assertEquals(original, session.document.value)
        assertEquals(HistoryState(), session.history.value)
    }

    @Test
    fun `edits can be undone and redone in order and report their labels`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        session.edit("重命名", transform = rename("First"))
        now += 5_000
        session.edit("删除片段", transform = dropClip("c2"))
        assertEquals(HistoryState(canUndo = true, undoLabel = "删除片段"), session.history.value)
        assertEquals(now, session.doc.modifiedAtMillis)

        assertEquals("删除片段", session.undo())
        assertEquals("First", session.doc.name)
        assertNotNull(session.doc.timeline.clip("c2"))
        assertEquals(HistoryState(canUndo = true, canRedo = true, undoLabel = "重命名", redoLabel = "删除片段"), session.history.value)

        assertEquals("重命名", session.undo())
        assertEquals("Demo", session.doc.name)
        assertEquals(original.timeline, session.doc.timeline)
        assertEquals(HistoryState(canRedo = true, redoLabel = "重命名"), session.history.value)
        assertNull(session.undo())

        assertEquals("重命名", session.redo())
        assertEquals("First", session.doc.name)
        assertEquals("删除片段", session.redo())
        assertNull(session.doc.timeline.clip("c2"))
        assertNull(session.redo())
        assertEquals(HistoryState(canUndo = true, undoLabel = "删除片段"), session.history.value)
    }

    @Test
    fun `the history goes back through several steps at once and forward again, and lists them`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")
        session.edit("重命名", transform = rename("First"))
        now += 5_000
        session.edit("删除片段", transform = dropClip("c2"))
        now += 5_000
        session.edit("再次重命名", transform = rename("Second"))
        assertEquals(HistoryLabels(undo = listOf("再次重命名", "删除片段", "重命名")), session.historyLabels.value)

        // Back through 删除片段: the rename stays, the clip is back.
        assertEquals(listOf("再次重命名", "删除片段"), session.undo(2))
        assertEquals("First", session.doc.name)
        assertNotNull(session.doc.timeline.clip("c2"))
        assertEquals(HistoryLabels(undo = listOf("重命名"), redo = listOf("删除片段", "再次重命名")), session.historyLabels.value)
        assertEquals(HistoryState(canUndo = true, canRedo = true, undoLabel = "重命名", redoLabel = "删除片段"), session.history.value)

        // Forward one step, then the rest; more steps than there are just go to the end.
        assertEquals(listOf("删除片段"), session.redo(1))
        assertNull(session.doc.timeline.clip("c2"))
        assertEquals(listOf("再次重命名"), session.redo(5))
        assertEquals("Second", session.doc.name)
        assertEquals(emptyList<String>(), session.redo(1))
        assertEquals(listOf("再次重命名", "删除片段", "重命名"), session.undo(10))
        assertEquals(original.name, session.doc.name)
        assertEquals(original.timeline, session.doc.timeline)
    }

    @Test
    fun `the history keeps 200 steps, fewer on a very long timeline`() = runTest {
        assertEquals(200, DefaultEditorSessionRepository.historyDepth(13))
        assertEquals(200, DefaultEditorSessionRepository.historyDepth(1000))
        assertEquals(151, DefaultEditorSessionRepository.historyDepth(1320))
        assertEquals(20, DefaultEditorSessionRepository.historyDepth(50_000))
        val long = projectOf(timeline = timelineOf(*(0 until 4000).map { clipOf("c$it", it * 10, 10) }.toTypedArray()), id = "p-1", name = "Long")
        val session = sessionOver(FakeProjectRepository(listOf(long)))
        session.open("p-1")
        repeat(60) { i ->
            now += 2_000
            session.edit("重命名 $i", null, rename("N$i"))
        }
        var undone = 0
        while (session.undo() != null) undone++
        assertEquals("4000 clips: 200 000 / 4000 = 50 steps", 50, undone)
        assertEquals("N9", session.doc.name)
    }

    @Test
    fun `an edit that changes nothing is not recorded or saved`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        session.edit("无变化") { it }

        assertSame(original, session.document.value)
        assertEquals(HistoryState(), session.history.value)
        assertFalse(session.saveState.value.pending)
    }

    @Test
    fun `edits with the same coalesce key inside the window collapse into one step`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        now = 20_000
        session.edit("曝光", "exposure", rename("1"))
        now = 21_000
        session.edit("曝光", "exposure", rename("2"))
        now = 22_400 // 1.4 s after the previous drag step: still the same gesture
        session.edit("曝光", "exposure", rename("3"))

        assertEquals("3", session.doc.name)
        assertEquals("曝光", session.undo())
        assertEquals("Demo", session.doc.name)
        assertFalse(session.history.value.canUndo)
    }

    @Test
    fun `edits outside the window with another key or without a key stay separate steps`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        now = 20_000
        session.edit("曝光", "exposure", rename("1"))
        now = 21_501 // the window has passed
        session.edit("曝光", "exposure", rename("2"))
        now = 21_600
        session.edit("对比度", "contrast", rename("3"))
        now = 21_700
        session.edit("重命名", null, rename("4"))
        now = 21_800
        session.edit("重命名", null, rename("5"))

        val names = generateSequence { session.undo()?.let { session.doc.name } }.toList()
        assertEquals(listOf("4", "3", "2", "1", "Demo"), names)
    }

    @Test
    fun `a new edit clears the redo history`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")
        session.edit("A", transform = rename("A"))
        session.undo()
        assertTrue(session.history.value.canRedo)

        session.edit("B", transform = rename("B"))

        assertFalse(session.history.value.canRedo)
        assertNull(session.redo())
        assertEquals("B", session.doc.name)
    }

    @Test
    fun `undo and redo keep the current render queue and delivery settings`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")
        session.edit("重命名", transform = rename("First"))
        val job = RenderJob("j-1", "First.mp4", RenderSettings("First"))

        session.update { it.copy(jobs = listOf(job), render = it.render.copy(fileName = "Export")) }
        assertEquals(HistoryState(canUndo = true, undoLabel = "重命名"), session.history.value)

        session.undo()
        assertEquals("Demo", session.doc.name)
        assertEquals(listOf(job), session.doc.jobs)
        assertEquals("Export", session.doc.render.fileName)

        val done = job.copy(status = RenderStatus.DONE, progress = 1f)
        session.update { it.copy(jobs = listOf(done)) }
        session.redo()
        assertEquals("First", session.doc.name)
        assertEquals(listOf(done), session.doc.jobs)
        assertEquals("Export", session.doc.render.fileName)
    }

    @Test
    fun `undo and redo keep the edit destinations as they are now`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")
        session.edit("删除片段", transform = dropClip("c2"))
        val targets = EditTargets(video = Timeline.V2, audio = Timeline.A2)
        val marks = TimelineMarks(30, 60)
        session.update { it.copy(timeline = it.timeline.copy(targets = targets, marks = marks)) }
        session.undo()
        assertEquals(2, session.doc.timeline.clips.size)
        assertEquals(targets, session.doc.timeline.targets)
        assertEquals("the In / Out marks stay too", marks, session.doc.timeline.marks)
        session.redo()
        assertEquals(1, session.doc.timeline.clips.size)
        assertEquals(targets, session.doc.timeline.targets)
        assertEquals(marks, session.doc.timeline.marks)
    }

    @Test
    fun `the selection only holds existing clips and is cleared when its clip disappears`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        session.select("c2")
        assertEquals("c2", session.selectedClipId.value)
        session.select("ghost")
        assertNull(session.selectedClipId.value)

        session.select("c1")
        session.edit("删除片段", transform = dropClip("c2"))
        assertEquals("c1", session.selectedClipId.value)

        session.undo()
        session.select("c2")
        session.edit("删除片段", transform = dropClip("c2"))
        assertNull(session.selectedClipId.value)

        session.select("c1")
        session.close()
        assertNull(session.selectedClipId.value)
    }

    @Test
    fun `autosave waits for a quiet period and then saves the latest document once`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val session = sessionOver(projects)
        session.open("p-1")

        session.edit("A", transform = rename("A"))
        assertTrue(session.saveState.value.pending)
        advanceTimeBy(AUTOSAVE_MS - 1)
        assertEquals(0, projects.saveCount)

        session.edit("B", transform = rename("B"))
        advanceTimeBy(AUTOSAVE_MS - 1)
        assertEquals(0, projects.saveCount)

        now = 42_000
        advanceTimeBy(2)
        assertEquals(1, projects.saveCount)
        assertEquals("B", projects.load("p-1")?.name)
        assertEquals(SaveState(savedAtMillis = 42_000, pending = false, failed = false), session.saveState.value)
    }

    @Test
    fun `changes outside the history are autosaved too`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val session = sessionOver(projects)
        session.open("p-1")
        val job = RenderJob("j-1", "Demo.mp4", RenderSettings("Demo"))

        session.update { it.copy(jobs = listOf(job)) }

        assertEquals(HistoryState(), session.history.value)
        assertTrue(session.saveState.value.pending)
        advanceTimeBy(AUTOSAVE_MS + 1)
        assertEquals(listOf(job), projects.load("p-1")?.jobs)
        assertFalse(session.saveState.value.pending)
    }

    @Test
    fun `a failed save is reported and the next edit retries it`() = runTest {
        val store = FakeProjectRepository(listOf(original))
        val flaky = FlakyProjectRepository(store)
        val session = sessionOver(flaky)
        session.open("p-1")

        session.edit("A", transform = rename("A"))
        advanceTimeBy(AUTOSAVE_MS + 1)
        assertEquals(SaveState(savedAtMillis = 5_000L, pending = false, failed = true), session.saveState.value)
        assertEquals("Demo", store.load("p-1")?.name)

        flaky.failing = false
        now = 30_000
        session.edit("B", transform = rename("B"))
        assertTrue(session.saveState.value.pending)
        advanceTimeBy(AUTOSAVE_MS + 1)
        assertEquals(SaveState(savedAtMillis = 30_000, pending = false, failed = false), session.saveState.value)
        assertEquals("B", store.load("p-1")?.name)
    }

    @Test
    fun `closing flushes a pending autosave at once and resets the session`() = runTest {
        val projects = FakeProjectRepository(listOf(original))
        val session = sessionOver(projects)
        session.open("p-1")
        session.edit("A", transform = rename("A"))

        session.close()

        assertEquals(1, projects.saveCount)
        assertEquals("A", projects.load("p-1")?.name)
        assertNull(session.document.value)
        assertEquals(HistoryState(), session.history.value)
        advanceTimeBy(AUTOSAVE_MS * 2) // the cancelled debounce never writes again
        assertEquals(1, projects.saveCount)
    }

    @Test
    fun `failed close and project switches keep edits and history until a successful retry`() = runTest {
        val other = original.copy(id = "other")
        val store = FakeProjectRepository(listOf(original, other))
        val flaky = FlakyProjectRepository(store)
        val session = sessionOver(flaky)
        assertTrue(session.open(original.id))
        session.edit("Rename", transform = rename("Edited"))

        assertFalse(session.close())
        assertFalse(session.open(other.id))
        assertTrue(runCatching { session.start(other.copy(id = "new")) }.exceptionOrNull() is IOException)
        assertNull(store.load("new"))
        assertEquals("Edited", session.document.value!!.name)
        assertTrue(session.history.value.canUndo)
        assertTrue(session.saveState.value.failed)
        assertEquals(original, store.load(original.id))

        flaky.failing = false
        assertTrue(session.close())
        assertTrue(session.open(original.id))
        assertEquals("Edited", session.document.value!!.name)
    }

    @Test
    fun `a new edit during the final write keeps the session open`() = runTest {
        val slow = SlowProjectRepository(original, saveMs = 50)
        val session = sessionOver(slow)
        session.open(original.id)
        session.edit("A", transform = rename("A"))
        val closing = async { session.close() }
        runCurrent()
        session.edit("B", transform = rename("B"))
        assertFalse(closing.await())
        assertEquals("B", session.document.value!!.name)
        assertTrue(session.close())
        assertEquals("B", slow.load(original.id)!!.name)
    }
    @Test
    fun `a save superseded by a newer edit is not reported as failed`() = runTest {
        val slow = SlowProjectRepository(original, saveMs = 50)
        val session = sessionOver(slow)
        session.open("p-1")
        session.edit("A", transform = rename("A"))
        advanceTimeBy(AUTOSAVE_MS + 20) // the autosave of "A" is in the middle of writing

        session.edit("B", transform = rename("B"))
        runCurrent()

        assertFalse("superseded save reported as failed", session.saveState.value.failed)
        assertTrue("the save of B is still pending", session.saveState.value.pending)
    }
    @Test
    fun `closing soon after an edit that interrupted a save still writes that edit`() = runTest {
        val slow = SlowProjectRepository(original, saveMs = 50)
        val session = sessionOver(slow)
        session.open("p-1")
        session.edit("A", transform = rename("A"))
        advanceTimeBy(AUTOSAVE_MS + 20) // the autosave of "A" is in the middle of writing
        session.edit("B", transform = rename("B"))
        advanceTimeBy(100) // the user leaves the project before B's own autosave is due

        session.close()

        assertEquals("B", slow.load("p-1")?.name)
    }

    /** Delegates to a [FakeProjectRepository] but can be told to fail every save. */
    private class FlakyProjectRepository(private val store: FakeProjectRepository) : ProjectRepository by store {
        var failing = true

        override suspend fun save(document: ProjectDocument) {
            if (failing) throw IOException("磁盘已满")
            store.save(document)
        }
    }

    /** A store whose writes take [saveMs] of (virtual) time, like real file I/O. */
    private class SlowProjectRepository(initial: ProjectDocument, private val saveMs: Long) : ProjectRepository {
        private val stored = mutableMapOf(initial.id to initial)
        override val library: StateFlow<ProjectLibrary> = MutableStateFlow(ProjectLibrary())

        override suspend fun refresh() = Unit

        override suspend fun load(id: String): ProjectDocument? = stored[id]

        override suspend fun save(document: ProjectDocument) {
            delay(saveMs)
            stored[document.id] = document
        }

        override suspend fun delete(id: String) {
            stored.remove(id)
        }
    }

    @Test
    fun `several clips can be selected, the inspector's first, and edits drop the ones they remove`() = runTest {
        val session = sessionOver(FakeProjectRepository(listOf(original)))
        session.open("p-1")

        session.select("c1")
        session.toggleSelected("c2")
        assertEquals("the newest addition is the inspector's", listOf("c2", "c1"), session.selectedClipIds.value)
        assertEquals("c2", session.selectedClipId.value)
        session.toggleSelected("c2")
        assertEquals(listOf("c1"), session.selectedClipIds.value)
        assertEquals("c1", session.selectedClipId.value)
        session.toggleSelected("gone")
        assertEquals("only clips on the timeline", listOf("c1"), session.selectedClipIds.value)

        session.selectAll(listOf("c2", "c1", "c2", "gone"))
        assertEquals(listOf("c2", "c1"), session.selectedClipIds.value)
        session.edit("删除片段", transform = dropClip("c2"))
        assertEquals("a removed clip leaves the selection", listOf("c1"), session.selectedClipIds.value)
        assertEquals("c1", session.selectedClipId.value)
        session.select(null)
        assertEquals(emptyList<String>(), session.selectedClipIds.value)
        assertNull(session.selectedClipId.value)
    }

    private companion object {
        const val AUTOSAVE_MS = 700L
    }
}
