package com.example.prismstudio.p40b43a88.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names written to the device's storage (release round R14: an over-long one failed the render at its end). */
class FileNamesTest {

    @Test
    fun `characters file systems refuse and control characters become underscores`() {
        assertEquals("测试_文件_名_____", FileNames.safe("测试/文件:名*?<>|"))
        assertEquals("a_b", FileNames.safe("a\nb"))
        assertEquals("NightRun_B站", FileNames.safe("NightRun_B站"))
    }

    @Test
    fun `a long name is cut to the byte budget without splitting a character`() {
        val cut = FileNames.safe("长".repeat(100))
        assertTrue(cut.toByteArray().size <= FileNames.MAX_BYTES)
        assertEquals("长".repeat(60), cut)
        // An emoji is two chars: never cut in half.
        val emoji = FileNames.safe("😀".repeat(60))
        assertTrue(emoji.toByteArray().size <= FileNames.MAX_BYTES)
        assertEquals(0, emoji.length % 2)
    }

    @Test
    fun `blank names and trailing dots and spaces are tidied`() {
        assertEquals("Reality Video Editor", FileNames.safe("   "))
        assertEquals("Reality Video Editor", FileNames.safe("..."))
        assertEquals("片子", FileNames.safe(" 片子. "))
    }

    @Test
    fun `a whole file name keeps its extension`() {
        assertEquals("时间线 1.edl", FileNames.safeWithExtension("时间线 1.edl"))
        val long = FileNames.safeWithExtension("长".repeat(100) + ".fcpxml")
        assertTrue(long.endsWith(".fcpxml"))
        assertTrue(long.toByteArray().size <= FileNames.MAX_BYTES + ".fcpxml".length)
        assertEquals("a_b.srt", FileNames.safeWithExtension("a/b.srt"))
        assertEquals("v1.0 final", FileNames.safeWithExtension("v1.0 final"))
    }
}
