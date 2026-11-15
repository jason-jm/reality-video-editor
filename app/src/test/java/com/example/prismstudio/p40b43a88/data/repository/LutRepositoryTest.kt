package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.usecase.CubeLutParser
import com.example.prismstudio.p40b43a88.domain.usecase.LutFormatException
import com.example.prismstudio.p40b43a88.domain.usecase.cubeText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The headset's LUT library: imported once, found again by id after a restart. */
@OptIn(ExperimentalCoroutinesApi::class)
class LutRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private val warm = cubeText(3, title = "Warm") { r, g, b -> floatArrayOf(r * 0.9f + 0.1f, g, b * 0.8f) }

    @Test
    fun `an import is stored once, survives a restart and is found by id`() = runTest {
        val dir = folder.newFolder("luts")
        val io = UnconfinedTestDispatcher(testScheduler)
        val repo = FileLutRepository(dir, backgroundScope, io)
        val start = repo.generation

        val first = repo.import("warm.cube", warm.toByteArray())
        assertFalse(first.alreadyThere)
        assertEquals("Warm", first.info.name)
        assertEquals("3D · 3³", first.info.detail)
        assertEquals(listOf(first.info), repo.library.value)
        assertTrue("grades bake again", repo.generation > start)
        val again = repo.import("copy.cube", ("# same LUT\n" + warm).toByteArray())
        assertTrue(again.alreadyThere)
        assertEquals(first.info, again.info)
        assertEquals("one file per LUT", 1, dir.listFiles()!!.count { it.name.endsWith(".plut") })

        val reopened = FileLutRepository(dir, backgroundScope, io)
        assertEquals(listOf(first.info), reopened.library.value)
        assertArrayEquals(CubeLutParser.parse(warm, "warm.cube").lut.data, reopened.cube(first.info.id)!!.data, 0f)
        assertNull(reopened.cube("lut-000000000000"))
    }

    @Test
    fun `a broken file is refused and the library stays as it was`() = runTest {
        val repo = FileLutRepository(folder.newFolder("luts"), backgroundScope, UnconfinedTestDispatcher(testScheduler))
        val message = try {
            repo.import("bad.cube", "LUT_3D_SIZE 2\n0 0 0\n".toByteArray())
            null
        } catch (e: LutFormatException) {
            e.message
        }
        assertEquals("LUT 数据不完整：应为 8 行，只有 1 行", message)
        assertTrue(repo.library.value.isEmpty())

        val memory = InMemoryLutRepository()
        val stored = memory.import("warm.cube", warm.toByteArray())
        assertEquals(listOf(stored.info), memory.library.value)
        assertTrue(memory.import("warm.cube", warm.toByteArray()).alreadyThere)
        assertEquals(1, memory.generation)
        memory.remove(stored.info.id)
        assertTrue(memory.library.value.isEmpty())
        assertNull(memory.cube(stored.info.id))
        assertEquals(2, memory.generation)
    }

    @Test
    fun `a removed LUT is gone from the library, the disk and the cache`() = runTest {
        val dir = folder.newFolder("luts")
        val io = UnconfinedTestDispatcher(testScheduler)
        val repo = FileLutRepository(dir, backgroundScope, io)
        val info = repo.import("warm.cube", warm.toByteArray()).info
        assertNotNull(repo.cube(info.id))
        val before = repo.generation
        repo.remove(info.id)
        assertTrue(repo.library.value.isEmpty())
        assertNull(repo.cube(info.id))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".plut") })
        assertTrue("grades bake again without it", repo.generation > before)
        repo.remove("lut-000000000000")
        assertTrue(FileLutRepository(dir, backgroundScope, io).library.value.isEmpty())
    }
}
