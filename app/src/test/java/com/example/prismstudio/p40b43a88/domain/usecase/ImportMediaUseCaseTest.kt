package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMediaUseCaseTest {

    private val importer = ImportMediaUseCase()

    private fun item(id: String, sizeBytes: Long) =
        DeviceMediaItem(id, "content://media/$id", "$id.mp4", MediaKind.VIDEO, 10_000, sizeBytes, "DCIM/", 1920, 1080)

    private val first = item("d1", 3L shl 20)
    private val second = item("d2", 1L shl 20)
    private val third = item("d3", 2L shl 20)
    private val device = listOf(first, second, third)

    /** The pool already holds the second device file (matched by uri, whatever its asset id). */
    private val pool = listOf(assetOf("d2"))

    @Test
    fun `media already in the pool is recognised by its uri`() {
        assertTrue(importer.isImported(second, pool))
        assertFalse(importer.isImported(first, pool))
        assertFalse(importer.isImported(second, emptyList()))
    }

    @Test
    fun `toggling picks and unpicks items but never already imported ones`() {
        val picked = importer.toggle(emptySet(), first, pool)
        assertEquals(setOf("d1"), picked)
        assertEquals(emptySet<String>(), importer.toggle(picked, first, pool))
        assertEquals(setOf("d1"), importer.toggle(picked, second, pool))
    }

    @Test
    fun `pending keeps device order and skips media that is already imported`() {
        assertEquals(listOf(first, third), importer.pending(device, setOf("d3", "d2", "d1"), pool))
    }

    @Test
    fun `summary describes how many items and bytes are selected`() {
        assertEquals("未选择素材", importer.summary(device, emptySet()))
        assertEquals("已选择 2 项 · 5 MB", importer.summary(device, setOf("d1", "d3")))
    }
}
