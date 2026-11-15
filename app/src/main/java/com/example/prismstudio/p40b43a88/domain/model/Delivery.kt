package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import kotlin.math.roundToInt

data class MixerChannel(
    val trackId: String,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val pan: Float = 0f,
    val volumeDb: Float = 0f,
    /** The track's equaliser (before the fader). */
    val eq: TrackEq = TrackEq.FLAT,
    /** The track's compressor and limiter (after the EQ, before the fader). */
    val dynamics: TrackDynamics = TrackDynamics.OFF,
    /** The track's ducking under another track (after the dynamics, with the fader). */
    val ducking: TrackDucking = TrackDucking.OFF,
    /** The track's noise reduction (first, before the EQ). */
    val noiseReduction: TrackNoiseReduction = TrackNoiseReduction.OFF,
    /** The track's de-esser (after the EQ, before the dynamics). */
    val deEsser: TrackDeEsser = TrackDeEsser.OFF,
    /** The track's reverb (last, after the fader and pan). */
    val reverb: TrackReverb = TrackReverb.OFF,
    /** The submix bus the track's output goes to ([MixerBus.id]); null: straight to the main output. */
    val bus: String? = null,
)

/**
 * A submix bus (Fairlight's submix buses): the tracks routed to it summed, through its equaliser and fader, into the main
 * output. Mute and solo act on all of its tracks. Every stage is linear, so the engine applies them on each member lane
 * (the EQ after the track's own processing, the fader with the track's) and their sum is the bus's exactly; a compressor
 * would hear each track alone instead of the sum, so a bus has none.
 */
data class MixerBus(
    val id: String,
    val name: String,
    val volumeDb: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val eq: TrackEq = TrackEq.FLAT,
) {
    /** The bus in short (B1), as a track strip shows where it goes. */
    val short: String get() = "B" + id.removePrefix("b")
}

data class MixerState(val channels: List<MixerChannel>, val masterVolumeDb: Float = 0f, val buses: List<MixerBus> = emptyList()) {
    fun channel(trackId: String): MixerChannel = channels.firstOrNull { it.trackId == trackId } ?: MixerChannel(trackId)
    fun replace(channel: MixerChannel): MixerState =
        copy(channels = channels.map { if (it.trackId == channel.trackId) channel else it })

    fun bus(busId: String?): MixerBus? = busId?.let { id -> buses.firstOrNull { it.id == id } }

    /** The bus [trackId] goes to, or null for the main output (a bus that is gone counts as the main output). */
    fun busOf(trackId: String): MixerBus? = bus(channel(trackId).bus)

    /** The tracks routed to [busId], in channel order. */
    fun membersOf(busId: String): List<String> = channels.filter { it.bus == busId }.map { it.trackId }

    /**
     * Whether [trackId] is heard given mute / solo: its own mute and its bus's silence it; while anything is soloed — a track
     * or a bus — only the soloed tracks and the tracks of soloed buses play.
     */
    fun isAudible(trackId: String): Boolean {
        val anySolo = channels.any { it.solo } || buses.any { it.solo }
        val ch = channel(trackId)
        val bus = busOf(trackId)
        return !ch.mute && bus?.mute != true && (!anySolo || ch.solo || bus?.solo == true)
    }

    /** [trackId]'s fader gain into the main output (linear): its own fader times its bus's, either at −60 dB silent. */
    fun laneGain(trackId: String): Float = faderGain(channel(trackId).volumeDb) * (busOf(trackId)?.let { faderGain(it.volumeDb) } ?: 1f)

    companion object {
        /** Submix buses at most (the strip list has room for three below the tracks' view). */
        const val MAX_BUSES = 3
        /** A fader at its bottom is silence. */
        const val FADER_FLOOR_DB = -60f

        fun forTracks(tracks: List<Track>) = MixerState(
            tracks.filter { it.kind == TrackKind.AUDIO }.map { MixerChannel(it.id) },
        )

        fun faderGain(db: Float): Float = if (db <= FADER_FLOOR_DB) 0f else Math.pow(10.0, db / 20.0).toFloat()
    }
}

enum class VideoCodec(val label: String, val mimeType: String) {
    H264("H.264", "video/avc"),
    H265("H.265", "video/hevc"),
}

enum class RenderResolution(val label: String, val shortSide: Int) {
    HD_720("720p", 720),
    HD_1080("1080p", 1080),
    UHD_4K("4K", 2160),
}

enum class RenderQuality(val label: String, val bitsPerPixel: Float) {
    STANDARD("标准", 0.07f),
    HIGH("高", 0.1f),
    BEST("最佳", 0.14f),
}

enum class RenderRange(val label: String) {
    WHOLE_TIMELINE("整条时间线"), IN_TO_OUT("入点至出点");

    /** Freeze the actual In and exclusive Out, including a missing endpoint. Unmarked renders follow the whole timeline. */
    fun frameLimits(timeline: Timeline): TimelineMarks {
        if (this == WHOLE_TIMELINE) return TimelineMarks()
        val frames = timeline.marks.range(timeline.durationFrames) ?: return TimelineMarks()
        return TimelineMarks(frames.first, frames.last + 1)
    }
}

enum class RenderPreset(
    val label: String,
    val suffix: String,
    val codec: VideoCodec,
    val resolution: RenderResolution,
    val fps: Int,
    val quality: RenderQuality,
) {
    BILIBILI_1080("B站 1080p", "B站", VideoCodec.H264, RenderResolution.HD_1080, 30, RenderQuality.HIGH),
    DOUYIN_VERTICAL("抖音竖屏", "抖音", VideoCodec.H264, RenderResolution.HD_1080, 30, RenderQuality.HIGH),
    YOUTUBE_4K("YouTube 4K", "4K", VideoCodec.H265, RenderResolution.UHD_4K, 30, RenderQuality.BEST),
    MASTER_HEVC("母版 H.265", "母版", VideoCodec.H265, RenderResolution.UHD_4K, 30, RenderQuality.BEST),
}

/** The file the subtitle track is saved as beside a render (Resolve's Export Subtitle formats). */
enum class SubtitleFileFormat(val label: String, val extension: String, val mimeType: String) {
    SRT("SRT", "srt", "application/x-subrip"),
    WEBVTT("WebVTT", "vtt", "text/vtt"),
}

/** An audio-only export's file: AAC in .m4a, or lossless PCM in .wav (Resolve's Wave). */
enum class AudioExportFormat(val label: String, val extension: String, val mimeType: String) {
    AAC("AAC", "m4a", "audio/mp4"),
    WAV("WAV", "wav", "audio/wav"),
}

data class RenderSettings(
    val fileName: String,
    val preset: RenderPreset? = RenderPreset.BILIBILI_1080,
    val codec: VideoCodec = VideoCodec.H264,
    val resolution: RenderResolution = RenderResolution.HD_1080,
    val fps: Int = 30,
    val quality: RenderQuality = RenderQuality.HIGH,
    val includeAudio: Boolean = true,
    val range: RenderRange = RenderRange.WHOLE_TIMELINE,
    /** Normalize Audio: the export's master gain brings the program to this loudness; null leaves the mix as it is. */
    val loudnessTargetLufs: Float? = null,
    /** Also save the subtitle track as a file (subtitles are burned into the picture either way), in [subtitleFormat]. */
    val exportSubtitles: Boolean = false,
    /** Resolve's Export Video: off, only the mix is rendered — an AAC .m4a in Music/Reality Video Editor. */
    val includeVideo: Boolean = true,
    val subtitleFormat: SubtitleFileFormat = SubtitleFileFormat.SRT,
    /** The mix alone: AAC (.m4a) or WAV (.wav, lossless). */
    val audioFormat: AudioExportFormat = AudioExportFormat.AAC,
    /** A WAV export's sample rate (Hz) and bit depth. */
    val wavSampleRate: Int = 48_000,
    val wavBits: Int = 24,
    /**
     * Resolve's Render › Individual clips: each clip in the render range becomes its own file — the clip alone (with its
     * linked sound), graded and transformed, from its first frame to its last — instead of one file of the timeline.
     */
    val individualClips: Boolean = false,
) {
    /** The file's extension: a movie, or the mix alone. */
    val extension: String get() = if (includeVideo) "mp4" else audioFormat.extension

    /** The mix alone as a lossless WAV. */
    val wav: Boolean get() = !includeVideo && audioFormat == AudioExportFormat.WAV

    /** `audio-format-note`: what an audio-only file holds and where it goes. */
    val audioFormatNote: String
        get() = if (audioFormat == AudioExportFormat.AAC) "只导出声音：AAC $AUDIO_ONLY_KBPS kbps 立体声 .m4a，保存到“音乐/${PRODUCT_NAME}”。"
        else buildList {
            add("无损 PCM 立体声 .wav，每分钟约 ${wavBytes(60.0, wavSampleRate, wavBits) / 1_000_000} MB，保存到“音乐/${PRODUCT_NAME}”。")
            add("${wavRateLabel(wavSampleRate)} 的声音逐采样原样写入，其他采样率的声音转换一次。")
            if (wavBits == 24) add("24 位按 16 位的混音精度封装（样本 ×256），用于要求 24 位的交付。")
        }.joinToString("\n")

    /** `render-audio-format` words: what an audio-only file holds. */
    val audioOnlyLabel: String
        get() = if (audioFormat == AudioExportFormat.WAV) "WAV ${wavRateLabel(wavSampleRate)} ${wavBits} 位" else "AAC $AUDIO_ONLY_KBPS kbps"

    val detailLabel: String
        get() = (if (includeVideo) "${codec.label} · ${resolution.label} · $fps fps · ${quality.label}" else "仅音频 · $audioOnlyLabel") + when {
            !includeAudio -> " · 无音频"
            loudnessTargetLufs != null -> " · ${loudnessTargetLufs.roundToInt()} LUFS"
            else -> ""
        } + if (exportSubtitles) " · ${subtitleFormat.label}" else ""

    companion object {
        /** AAC bitrate of an audio-only export, kbps (a movie's sound stays at the encoder's default). */
        const val AUDIO_ONLY_KBPS = 256

        /** A WAV export's sample rates and bit depths. */
        val WAV_RATES = listOf(44_100, 48_000)
        val WAV_BITS = listOf(16, 24)

        /** 48 kHz, 44.1 kHz. */
        fun wavRateLabel(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f)

        /** Bytes a WAV of [seconds] takes: stereo PCM at [rate] Hz and [bits] bits, and its header. */
        fun wavBytes(seconds: Double, rate: Int, bits: Int): Long = (seconds * rate).toLong() * 2 * bits / 8 + 44
    }
}

enum class RenderStatus(val label: String) {
    QUEUED("排队中"),
    RENDERING("渲染中"),
    DONE("已完成"),
    FAILED("失败"),
    PARTIAL("部分完成"),
    ;

    val mediaSaved: Boolean get() = this == DONE || this == PARTIAL
}

data class RenderJob(
    val id: String,
    val fileName: String,
    val settings: RenderSettings,
    val status: RenderStatus = RenderStatus.QUEUED,
    val progress: Float = 0f,
    val outputUri: String? = null,
    val outputBytes: Long = 0,
    val errorMessage: String? = null,
    /** The timeline the job renders (Resolve binds a job to the timeline it was added from), and its name then. */
    val timelineId: String = FIRST_TIMELINE_ID,
    val timelineName: String = FIRST_TIMELINE_NAME,
    /** An individual clip's job: that clip of the timeline alone (see [RenderSettings.individualClips]); null renders the timeline. */
    val clipId: String? = null,
    /** Timeline frame limits for an In-to-Out job. Empty marks mean the whole timeline. Older jobs only recorded the mode. */
    val queuedRange: TimelineMarks? = null,
    /** The partially completed job whose settings this retry uses. Its published file remains available. */
    val retryOf: String? = null,
) {
    val cancellable: Boolean get() = status == RenderStatus.QUEUED || status == RenderStatus.RENDERING
    val statusLabel: String
        get() = if (status == RenderStatus.RENDERING) "渲染中 ${(progress * 100).toInt()}%" else status.label
    val detailLabel: String
        get() = settings.detailLabel + if (status.mediaSaved && outputBytes > 0) " · ${Bytes.label(outputBytes)}" else ""
}
