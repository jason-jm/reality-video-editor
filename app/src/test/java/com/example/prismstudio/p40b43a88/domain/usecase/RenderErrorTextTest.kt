package com.example.prismstudio.p40b43a88.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/** Release round R5: a failed render says what went wrong in plain words, Media3's error name kept for support. */
class RenderErrorTextTest {
    @Test
    fun `known failures say what to do, the rest by family, none with the bare code alone`() {
        assertEquals("编码器不支持 H.265 4K 60 fps，请降低分辨率或帧率后重试。", RenderErrorText.describe(4003, "ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED", "H.265 4K 60 fps"))
        assertEquals("设备处理过慢，渲染中断。请关闭其他应用，或降低分辨率后重试。", RenderErrorText.describe(7002, "ERROR_CODE_MUXING_TIMEOUT", ""))
        assertEquals("有素材无法读取，请检查原文件是否仍在设备上。", RenderErrorText.describe(2005, "ERROR_CODE_IO_FILE_NOT_FOUND", ""))
        assertEquals(
            "处理画面时出错：请重试；如果反复出现，试着关掉部分特效或降低分辨率（ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED）。",
            RenderErrorText.describe(5001, "ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED", ""),
        )
        assertEquals("写入视频文件时出错：请确认设备还有空间后重试（ERROR_CODE_MUXING_FAILED）。", RenderErrorText.describe(7001, "ERROR_CODE_MUXING_FAILED", ""))
        assertEquals("读取素材或写入文件时出错：请确认素材仍在设备上、设备还有空间，然后重试（ERROR_CODE_IO_UNSPECIFIED）。", RenderErrorText.describe(2000, "ERROR_CODE_IO_UNSPECIFIED", ""))
        assertEquals("渲染失败，请重试（ERROR_CODE_UNSPECIFIED）。", RenderErrorText.describe(1000, "ERROR_CODE_UNSPECIFIED", ""))
    }
}
