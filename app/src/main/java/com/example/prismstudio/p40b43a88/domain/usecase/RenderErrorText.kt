package com.example.prismstudio.p40b43a88.domain.usecase

/**
 * What a failed render says (release round R5): plain words for what went wrong and what to try, Media3's error name kept
 * at the end for support. [code] is Media3's ExportException code (families by thousands: 2xxx reading and writing files,
 * 3xxx decoding, 4xxx encoding, 5xxx picture processing, 6xxx sound processing, 7xxx writing the video file); [format]
 * names the render's codec, resolution and frame rate ("H.264 1080p 30 fps").
 */
object RenderErrorText {
    fun describe(code: Int, codeName: String, format: String): String = when (code) {
        ENCODER_INIT_FAILED, ENCODING_FORMAT_UNSUPPORTED -> "编码器不支持 $format，请降低分辨率或帧率后重试。"
        IO_FILE_NOT_FOUND, IO_NO_PERMISSION -> "有素材无法读取，请检查原文件是否仍在设备上。"
        DECODER_INIT_FAILED, DECODING_FORMAT_UNSUPPORTED -> "有素材的编码格式无法解码，请替换该素材后重试。"
        MUXING_TIMEOUT -> "设备处理过慢，渲染中断。请关闭其他应用，或降低分辨率后重试。"
        else -> when (code / 1000) {
            2 -> "读取素材或写入文件时出错：请确认素材仍在设备上、设备还有空间，然后重试（$codeName）。"
            3 -> "解码素材时出错：请重试；如果反复出现，试着替换出错的素材（$codeName）。"
            4 -> "编码视频时出错：请重试；如果反复出现，试着降低分辨率或换用 H.264（$codeName）。"
            5 -> "处理画面时出错：请重试；如果反复出现，试着关掉部分特效或降低分辨率（$codeName）。"
            6 -> "处理声音时出错：请重试；如果反复出现，试着关掉部分音频效果（$codeName）。"
            7 -> "写入视频文件时出错：请确认设备还有空间后重试（$codeName）。"
            else -> "渲染失败，请重试（$codeName）。"
        }
    }

    const val IO_FILE_NOT_FOUND = 2005
    const val IO_NO_PERMISSION = 2006
    const val DECODER_INIT_FAILED = 3001
    const val DECODING_FORMAT_UNSUPPORTED = 3003
    const val ENCODER_INIT_FAILED = 4001
    const val ENCODING_FORMAT_UNSUPPORTED = 4003
    const val MUXING_TIMEOUT = 7002
}
