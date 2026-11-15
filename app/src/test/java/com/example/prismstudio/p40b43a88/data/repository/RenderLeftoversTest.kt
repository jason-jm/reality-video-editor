package com.example.prismstudio.p40b43a88.data.repository

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

/** A render the app was killed in leaves its working files in the cache; the next run's first render clears them (R18). */
class RenderLeftoversTest {

    private fun file(dir: File, name: String, modified: Long) = File(dir, name).apply {
        writeText("x")
        setLastModified(modified)
    }

    @Test
    fun `old working files go, the reusable pieces and this run's files stay`() {
        val dir = Files.createTempDirectory("renders").toFile()
        val started = 2_000_000_000_000L
        val old = started - 60_000L
        file(dir, "NightRun (23).mp4", old)
        file(dir, "stack-0.mp4", old)
        file(dir, "Mix.m4a", old)
        file(dir, "clear.png", old)
        file(dir, "lead-in-48000.wav", old)
        file(dir, "NightRun.mp4", started + 1_000L)
        assertEquals(3, sweepRenderLeftovers(dir, started))
        assertEquals(listOf("NightRun.mp4", "clear.png", "lead-in-48000.wav"), dir.list()!!.sorted())
        dir.deleteRecursively()
    }

    @Test
    fun `a missing cache folder is no error`() {
        assertEquals(0, sweepRenderLeftovers(File("/nonexistent/renders"), System.currentTimeMillis()))
    }
}
