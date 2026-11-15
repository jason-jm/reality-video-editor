package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Bytes
import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec

/** Output size, bitrate and file-size estimate for the Deliver stage. */
class EstimateRenderUseCase {

    data class Estimate(val width: Int, val height: Int, val videoBitrate: Int, val bytes: Long, val label: String)

    operator fun invoke(settings: RenderSettings, format: ProjectFormat, durationFrames: Int): Estimate {
        val seconds = durationFrames.toDouble() / format.fps.coerceAtLeast(1)
        if (!settings.includeVideo) {
            // The mix alone.
            val bytes = if (settings.audioFormat == AudioExportFormat.WAV) RenderSettings.wavBytes(seconds, settings.wavSampleRate, settings.wavBits)
            else (RenderSettings.AUDIO_ONLY_KBPS * 1000L * seconds / 8).toLong()
            val label = if (durationFrames <= 0) "时间线为空，暂无可渲染内容"
            else "预计 ${Bytes.label(bytes)} · ${Timecode.format(durationFrames, format.fps)} · 仅音频 ${settings.audioOnlyLabel}"
            return Estimate(0, 0, 0, bytes, label)
        }
        val (w, h) = outputSize(settings, format)
        val bitrate = videoBitrate(settings, w, h)
        val audioBitrate = if (settings.includeAudio) AUDIO_BITRATE else 0
        val bytes = ((bitrate + audioBitrate) * seconds / 8).toLong()
        val label = if (durationFrames <= 0) {
            "时间线为空，暂无可渲染内容"
        } else {
            "预计 ${Bytes.label(bytes)} · ${Timecode.format(durationFrames, format.fps)} · " +
                "${settings.codec.label} ${settings.resolution.label} ${settings.fps} fps"
        }
        return Estimate(w, h, bitrate, bytes, label)
    }

    /**
     * What the delivery's frame rate makes of the timeline's (`fps-note`). A render always comes out at a constant
     * rate, Resolve's Nearest retime: each frame shows the picture at its time, the frame before again where a
     * source has nothing new (a slowed clip, footage slower than the output); null for the mix alone.
     */
    fun frameRateNote(settings: RenderSettings, format: ProjectFormat): String? {
        if (!settings.includeVideo) return null
        val timeline = format.fps
        val output = settings.fps
        return when {
            output == timeline -> "恒定 $output fps，与时间线相同；慢放和低帧率素材重复前一帧补齐"
            output > timeline -> "高于时间线（$timeline fps）：每秒渲染 $output 帧，素材帧不够时重复前一帧"
            else -> "低于时间线（$timeline fps）：每秒渲染 $output 帧，按时刻取画面，舍去其余 ${timeline - output} 帧"
        }
    }

    /** Scales the project raster so its short side matches the chosen resolution (even dimensions). */
    fun outputSize(settings: RenderSettings, format: ProjectFormat): Pair<Int, Int> {
        val short = minOf(format.width, format.height).coerceAtLeast(1)
        val scale = settings.resolution.shortSide.toDouble() / short
        val w = (format.width * scale).toInt() and 1.inv()
        val h = (format.height * scale).toInt() and 1.inv()
        return w to h
    }

    fun videoBitrate(settings: RenderSettings, width: Int, height: Int): Int {
        val codecFactor = if (settings.codec == VideoCodec.H265) 0.6 else 1.0
        return (width.toDouble() * height * settings.fps * settings.quality.bitsPerPixel * codecFactor).toInt()
    }

    /**
     * Free space a render needs before it starts (release round R5): the finished file is written twice — in the render
     * cache, then into the gallery — at [videoBitrate] with the sound for [seconds], and [margin] more for the device.
     */
    fun spaceNeeded(videoBitrate: Int, includeAudio: Boolean, seconds: Double, margin: Long = SPACE_MARGIN_BYTES): Long =
        ((videoBitrate + if (includeAudio) AUDIO_BITRATE else 0) / 8.0 * seconds * 2).toLong() + margin

    /** Settings implied by choosing [preset] for a project called [projectName]. */
    fun applyPreset(current: RenderSettings, preset: RenderPreset, projectName: String): RenderSettings =
        current.copy(
            preset = preset,
            fileName = "${projectName.replace(Regex("\\s*·\\s*|\\s+"), "_")}_${preset.suffix}",
            codec = preset.codec,
            resolution = preset.resolution,
            fps = preset.fps,
            quality = preset.quality,
            // A preset is a movie.
            includeVideo = true,
        )

    companion object {
        const val AUDIO_BITRATE = 192_000
        /** Room kept free for the device itself when a render checks the space it needs. */
        const val SPACE_MARGIN_BYTES = 200_000_000L
    }
}
