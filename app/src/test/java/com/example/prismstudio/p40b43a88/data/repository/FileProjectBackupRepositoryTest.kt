package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Backups on disk: one file per backup beside a small description, listed newest first, pruned and removed with the project. */
class FileProjectBackupRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `an absent backup folder is empty and an unreadable directory reports a failure`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectBackupRepository(root, Dispatchers.Unconfined)
        assertTrue(repo.list("p-1").isEmpty())
        File(root, "p-1").writeText("not a directory")
        assertTrue(runCatching { repo.list("p-1") }.exceptionOrNull() is IOException)
    }

    @Test
    fun `a damaged optional index falls back to the backup and cannot turn a saved snapshot into a failure`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectBackupRepository(root, Dispatchers.Unconfined)
        val project = UiFixtures.document(id = "p-trip", name = "Trip", fps = 24)
        val first = repo.write(project, 1_000)
        File(root, "p-trip/1000.meta").writeText("999,bad,120,30")
        assertEquals(first, repo.list(project.id).single())

        assertTrue(File(root, "p-trip/2000.meta").mkdir())
        val second = repo.write(project.copy(name = "Trip 2"), 2_000)
        assertEquals(listOf(second, first), repo.list(project.id))
        assertEquals("Trip 2", repo.read(project.id, 2_000)?.name)
    }

    @Test
    fun `invalid project folder names never resolve to a different library`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectBackupRepository(root, Dispatchers.Unconfined)
        val project = UiFixtures.document()
        repo.write(project, 1_000)
        for (id in listOf("", ".", "..")) {
            assertTrue(runCatching { repo.list(id) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(project, repo.read(project.id, 1_000))
    }

    private val doc = UiFixtures.document(
        id = "p-1", name = "Trip", media = listOf(UiFixtures.video("v1")),
        clips = listOf(UiFixtures.clip("c1", start = 0, frames = 90, assetId = "v1"), UiFixtures.clip("c2", start = 90, frames = 60, assetId = "v1")),
    )

    @Test
    fun `backups are written, listed newest first with what they hold, read back, pruned and deleted`() = runTest {
        val dir = folder.newFolder("backups")
        val repo = FileProjectBackupRepository(dir, Dispatchers.Unconfined)
        repo.write(doc, 1_000)
        repo.write(doc.copy(name = "Trip 2"), 2_000)
        val list = repo.list("p-1")
        assertEquals(listOf(2_000L, 1_000L), list.map { it.takenAtMillis })
        assertEquals(listOf(2, 150, 30), list.first().let { listOf(it.clips, it.durationFrames, it.fps) })
        assertTrue(list.first().sizeBytes > 0)
        assertEquals("Trip 2", repo.read("p-1", 2_000)!!.name)
        assertNull(repo.read("p-1", 3_000))
        // Without its description a backup is still listed (read from the project file).
        File(dir, "p-1/1000.meta").delete()
        assertEquals(2, repo.list("p-1").first { it.takenAtMillis == 1_000L }.clips)

        repo.prune("p-1", setOf(2_000))
        assertEquals(listOf(2_000L), repo.list("p-1").map { it.takenAtMillis })
        assertEquals(listOf("2000.json", "2000.meta"), File(dir, "p-1").list()!!.sorted())
        repo.deleteAll("p-1")
        assertTrue(repo.list("p-1").isEmpty())
        assertTrue(!File(dir, "p-1").exists())
    }
}
