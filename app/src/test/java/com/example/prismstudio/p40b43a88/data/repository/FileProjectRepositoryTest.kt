package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileProjectRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `unreadable files remain visible beside healthy projects until repaired or removed`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectRepository(root, Dispatchers.Unconfined)
        val good = UiFixtures.document(id = "good")
        repo.save(good)
        File(root, "empty.json").writeText("")
        File(root, "wrong-id.json").writeText(File(root, "good.json").readText())
        File(root, "partial.tmp").writeText("unfinished")
        repo.refresh()
        assertEquals(listOf(good.summary()), repo.library.value.summaries)
        assertEquals(listOf("empty", "wrong-id"), repo.library.value.unreadableIds)
        assertNull(repo.load("wrong-id"))

        repo.save(good.copy(id = "empty"))
        assertEquals(listOf("wrong-id"), repo.library.value.unreadableIds)
        repo.delete("wrong-id")
        assertTrue(repo.library.value.unreadableIds.isEmpty())
        assertEquals(setOf("good", "empty"), repo.library.value.summaries.map { it.id }.toSet())
    }

    @Test
    fun `failed removal keeps the indexed project and a retry removes it`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectRepository(root, Dispatchers.Unconfined)
        val project = UiFixtures.document()
        repo.save(project)
        val target = File(root, "${project.id}.json")
        val original = File(root, "original")
        assertTrue(target.renameTo(original))
        assertTrue(target.mkdir())
        val occupied = File(target, "child").apply { writeText("keep") }
        assertTrue(runCatching { repo.delete(project.id) }.exceptionOrNull() is IOException)
        assertEquals(project.id, repo.library.value.summaries.single().id)
        assertTrue(occupied.delete())
        assertTrue(target.delete())
        assertTrue(original.renameTo(target))
        repo.delete(project.id)
        repo.delete(project.id)
        assertTrue(repo.library.value.summaries.isEmpty())
        assertFalse(target.exists())
    }

    @Test
    fun `a rejected replacement leaves the target intact and does not publish a new summary`() = runTest {
        val root = folder.newFolder()
        val repo = FileProjectRepository(root, Dispatchers.Unconfined)
        val project = UiFixtures.document()
        repo.save(project)
        val target = File(root, "${project.id}.json")
        val original = File(root, "previous")
        assertTrue(target.renameTo(original))
        assertTrue(target.mkdir())
        val sentinel = File(target, "keep").apply { writeText("original target") }

        assertTrue(runCatching { repo.save(project.copy(name = "changed")) }.exceptionOrNull() is IOException)
        assertEquals("original target", sentinel.readText())
        assertEquals(project.name, repo.library.value.summaries.single().name)
        assertFalse(root.listFiles()!!.any { it.extension == "tmp" })

        assertTrue(target.deleteRecursively())
        assertTrue(original.renameTo(target))
        repo.save(project.copy(name = "changed"))
        assertEquals("changed", repo.load(project.id)!!.name)
    }
}
