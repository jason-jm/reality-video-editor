package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderResolution
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class EstimateRenderUseCaseTest {

    private val estimate = EstimateRenderUseCase()
    private val hd = ProjectFormat(1920, 1080, 30)

    @Test
    fun `a WAV's size is its PCM - rate times channels times depth - and its header`() {
        val wav = RenderSettings(fileName = "x", includeVideo = false, audioFormat = com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat.WAV)
        val five = estimate(wav, ProjectFormat(1920, 1080, 30), 150)
        assertEquals(5L * 48_000 * 2 * 3 + 44, five.bytes)
        assertEquals("预计 1 MB · 00:00:05:00 · 仅音频 WAV 48 kHz 24 位", five.label)
        assertEquals(10_584_044L, RenderSettings.wavBytes(60.0, 44_100, 16))
        assertEquals("24 位按 16 位的混音精度封装（样本 ×256），用于要求 24 位的交付。", wav.audioFormatNote.lines().last())
    }

    @Test
    fun `output size scales the short side to the chosen resolution and keeps dimensions even`() {
        assertEquals(3840 to 2160, estimate.outputSize(RenderSettings("x", resolution = RenderResolution.UHD_4K), hd))
        assertEquals(720 to 1280, estimate.outputSize(RenderSettings("x", resolution = RenderResolution.HD_720), ProjectFormat(1080, 1920, 30)))
        assertEquals(1282 to 720, estimate.outputSize(RenderSettings("x", resolution = RenderResolution.HD_720), ProjectFormat(1925, 1080, 30)))
    }

    @Test
    fun `video bitrate follows pixels frame rate quality and codec efficiency`() {
        val h264 = RenderSettings("x", codec = VideoCodec.H264, fps = 30, quality = RenderQuality.HIGH)
        assertEquals(6_220_800, estimate.videoBitrate(h264, 1920, 1080))

        val hevc = RenderSettings("x", codec = VideoCodec.H265, fps = 30, quality = RenderQuality.BEST)
        assertEquals(20_901_888, estimate.videoBitrate(hevc, 3840, 2160))
    }

    @Test
    fun `the frame rate note says the render is a constant rate and what it does with the timeline's`() {
        assertEquals("恒定 30 fps，与时间线相同；慢放和低帧率素材重复前一帧补齐", estimate.frameRateNote(RenderSettings("x", fps = 30), hd))
        assertEquals("高于时间线（30 fps）：每秒渲染 60 帧，素材帧不够时重复前一帧", estimate.frameRateNote(RenderSettings("x", fps = 60), hd))
        assertEquals(
            "低于时间线（30 fps）：每秒渲染 24 帧，按时刻取画面，舍去其余 6 帧",
            estimate.frameRateNote(RenderSettings("x", fps = 24), hd),
        )
        assertEquals("恒定 25 fps，与时间线相同；慢放和低帧率素材重复前一帧补齐", estimate.frameRateNote(RenderSettings("x", fps = 25), ProjectFormat(1920, 1080, 25)))
        assertEquals("the mix alone has no picture", null, estimate.frameRateNote(RenderSettings("x", fps = 30, includeVideo = false), hd))
    }

    @Test
    fun `the mix alone is estimated at the AAC bitrate, and a preset turns the picture back on`() {
        val audio = estimate(RenderSettings("x", includeVideo = false), hd, durationFrames = 300)
        assertEquals(320_000L, audio.bytes)
        assertEquals("预计 312 KB · 00:00:10:00 · 仅音频 AAC 256 kbps", audio.label)
        assertEquals(true, estimate.applyPreset(RenderSettings("x", includeVideo = false), RenderPreset.YOUTUBE_4K, "Trip").includeVideo)
    }

    @Test
    fun `the estimate turns the timeline length into bytes including the audio track`() {
        val result = estimate(RenderSettings("x"), hd, durationFrames = 300)

        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(6_220_800, result.videoBitrate)
        assertEquals((6_220_800L + EstimateRenderUseCase.AUDIO_BITRATE) * 10 / 8, result.bytes)
        assertEquals("预计 7 MB · 00:00:10:00 · H.264 1080p 30 fps", result.label)

        val silent = estimate(RenderSettings("x", includeAudio = false), hd, durationFrames = 300)
        assertEquals(6_220_800L * 10 / 8, silent.bytes)
    }

    @Test
    fun `an empty timeline has nothing to render`() {
        val result = estimate(RenderSettings("x"), hd, durationFrames = 0)

        assertEquals(0L, result.bytes)
        assertEquals("时间线为空，暂无可渲染内容", result.label)
    }

    @Test
    fun `a preset copies its encoding settings and names the file after the project`() {
        val current = RenderSettings("old", preset = null, includeAudio = false, range = RenderRange.IN_TO_OUT)

        val applied = estimate.applyPreset(current, RenderPreset.YOUTUBE_4K, "Summer · Trip  2024")

        assertEquals(
            RenderSettings(
                fileName = "Summer_Trip_2024_4K",
                preset = RenderPreset.YOUTUBE_4K,
                codec = VideoCodec.H265,
                resolution = RenderResolution.UHD_4K,
                fps = 30,
                quality = RenderQuality.BEST,
                includeAudio = false,
                range = RenderRange.IN_TO_OUT,
            ),
            applied,
        )
    }

    @Test
    fun `a render asks for twice its file plus a margin before it starts`() {
        val estimator = EstimateRenderUseCase()
        // 8 Mb/s picture + 192 kb/s sound for 100 s = 102.4 MB a copy, two copies, 200 MB margin.
        assertEquals(2 * 102_400_000L + 200_000_000L, estimator.spaceNeeded(8_000_000, includeAudio = true, seconds = 100.0))
        assertEquals("no sound, no sound bytes", 2 * 100_000_000L + 200_000_000L, estimator.spaceNeeded(8_000_000, includeAudio = false, seconds = 100.0))
    }
}
