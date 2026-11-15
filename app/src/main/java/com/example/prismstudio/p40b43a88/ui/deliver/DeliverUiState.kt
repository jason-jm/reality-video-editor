package com.example.prismstudio.p40b43a88.ui.deliver

import com.example.prismstudio.p40b43a88.ui.color.MiniTimelineUi
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat

import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange
import kotlin.math.roundToInt

data class PresetUi(val preset: RenderPreset, val selected: Boolean)

data class RenderJobUi(
    val id: String,
    val name: String,
    val status: RenderStatus,
    val statusLabel: String,
    val progress: Float,
    val detail: String,
    val error: String?,
    val cancellable: Boolean,
    /** What its finished file opens in: the gallery, or (the mix alone) a music player. */
    val openLabel: String = "在相册中查看",
    val rangeLabel: String? = null,
    val retryLabel: String? = null,
)

data class DeliverUiState(
    /** The program monitor shows a flattened approximation of composited overlays / transitions. */
    val previewApproximate: Boolean = false,
    /** Picture layers the program monitor stacks. */
    val programLayers: Int = 1,
    /** Each program layer's composite mode at the playhead (Resolve's Composite Mode), bottom first. */
    val programComposites: List<CompositeMode> = emptyList(),
    val settings: RenderSettings = RenderSettings(fileName = ""),
    val presets: List<PresetUi> = RenderPreset.entries.map { PresetUi(it, false) },
    val estimate: String = "",
    /** What the chosen frame rate does with the timeline's (`fps-note`): always a constant rate; null for the mix alone. */
    val frameRateNote: String? = null,
    /** `subtitle-delivery-note`: the language burned in and the subtitle files saved (SubtitleExportUseCase.note). */
    val subtitleNote: String? = null,
    /** `deliver-hdr-note` with the tone mapping rows: the timeline has HDR clips (a PQ one: [pqPresent]). */
    val hdrNote: String? = null,
    val pqPresent: Boolean = false,
    val colorManagement: com.example.prismstudio.p40b43a88.domain.model.ColorManagement = com.example.prismstudio.p40b43a88.domain.model.ColorManagement.DEFAULT,
    val rangeNote: String? = null,
    /** `render-mode-note`: with 单独片段, how many clips get a file each and what each holds; null for one file. */
    val individualNote: String? = null,
    /** `render-offline-note`: clips in the render whose media is offline, rendering black or silent (null: none). */
    val offlineNote: String? = null,
    val jobs: List<RenderJobUi> = emptyList(),
    val lastRenderSummary: String? = null,
    val rendering: Boolean = false,
    val pictureAspect: Float = 16f / 9f,
    val programEmpty: Boolean = true,
    /** A still is being rendered (`export-still-button` waits). */
    val exportingStill: Boolean = false,
    val openUri: String? = null,
    val toast: String? = null,
    /** The program's loudness, and what Normalize Audio will do with it (`loudness-note`). */
    val loudnessNote: String? = null,
    /** The open timeline, which 加入渲染队列 renders (`deliver-timeline-value`): its name and raster. */
    val timelineLabel: String = "",
    /** The project has more than one timeline: jobs name theirs, and they can all be queued at once. */
    val severalTimelines: Boolean = false,
    /**
     * `deliver-mini-timeline`: Resolve's Deliver page timeline — the picture tracks, the playhead, and the frames a render
     * takes ([renderRange], inclusive) shaded; [rangeLabel] says them in timecode.
     */
    val miniTimeline: MiniTimelineUi = MiniTimelineUi(),
    val renderRange: IntRange? = null,
    val rangeLabel: String = "",
    val playheadFrame: Int = 0,
    val fps: Int = 30,
    val hasMarks: Boolean = false,
) {
    val codecIndex: Int get() = settings.codec.ordinal
    val resolutionIndex: Int get() = settings.resolution.ordinal
    val fpsIndex: Int get() = FRAME_RATES.indexOf(settings.fps).coerceAtLeast(0)
    val qualityIndex: Int get() = settings.quality.ordinal
    val rangeIndex: Int get() = settings.range.ordinal

    /** `render-mode-segment`: 单个文件 (0) or 单独片段 (1). */
    val renderModeIndex: Int get() = if (settings.individualClips) 1 else 0
    /** 0 = leave the mix as it is, then the [LoudnessUseCase.TARGETS_LUFS]. */
    val loudnessIndex: Int get() = settings.loudnessTargetLufs?.let { LoudnessUseCase.TARGETS_LUFS.indexOf(it) + 1 } ?: 0
    val hasJobs: Boolean get() = jobs.isNotEmpty()
    val nothingQueued: Boolean get() = jobs.none { it.status == RenderStatus.QUEUED } || rendering || exportingStill
    /** Finished renders that `clear-done-button` can take off the queue. */
    val hasDone: Boolean get() = jobs.any { it.status.mediaSaved }

    companion object {
        val FRAME_RATES = listOf(24, 25, 30, 60)
        val LOUDNESS_OPTIONS = listOf("不调整") + LoudnessUseCase.TARGETS_LUFS.map { "${it.roundToInt()}" }
    }
}

sealed interface DeliverEvent {
    data class SelectPreset(val preset: RenderPreset) : DeliverEvent
    data class FileName(val text: String) : DeliverEvent
    data class Codec(val index: Int) : DeliverEvent
    data class Resolution(val index: Int) : DeliverEvent
    data class Fps(val index: Int) : DeliverEvent
    data class Quality(val index: Int) : DeliverEvent
    data class IncludeAudio(val include: Boolean) : DeliverEvent
    /** Resolve's Export Video: off renders the mix alone (`video-row`). */
    data class IncludeVideo(val include: Boolean) : DeliverEvent
    /** Resolve's Export › Current Frame as Still (`export-still-button`): the frame at the playhead as a PNG. */
    data object ExportStill : DeliverEvent
    data class Range(val index: Int) : DeliverEvent

    /** `render-mode-segment`: Resolve's Render — one file of the timeline, or one per clip ([individual]). */
    data class RenderMode(val individual: Boolean) : DeliverEvent
    /** The Deliver timeline: a press moves the playhead (the frame 导出当前帧 takes); I / O / Alt+X mark what renders. */
    data class Seek(val frame: Int) : DeliverEvent
    data object MarkIn : DeliverEvent
    data object MarkOut : DeliverEvent
    data object ClearMarks : DeliverEvent
    /** Normalize Audio: 0 off, else the index into [LoudnessUseCase.TARGETS_LUFS] plus one. */
    data class Loudness(val index: Int) : DeliverEvent
    data class ExportSubtitles(val export: Boolean) : DeliverEvent
    /** The subtitle file's format, by [SubtitleFileFormat] order (SRT, WebVTT). */
    data class SubtitleFormat(val index: Int) : DeliverEvent
    /** `audio-format-segment` / `wav-rate-segment` / `wav-bits-segment`: the mix alone as AAC or WAV, and a WAV's rate and depth. */
    data class AudioFormat(val index: Int) : DeliverEvent
    data class WavRate(val index: Int) : DeliverEvent
    data class WavBits(val index: Int) : DeliverEvent
    /** `tone-mapping-row`: how HDR clips are fitted into the Rec.709 timeline (a project setting). */
    data class ToneMap(val method: com.example.prismstudio.p40b43a88.domain.model.ToneMapping) : DeliverEvent
    /** `pq-peak-row`: the peak a PQ master is taken as graded to. */
    data class PqPeak(val index: Int) : DeliverEvent
    data object AddJob : DeliverEvent
    /** One job per timeline of the project (`add-all-timelines-button`). */
    data object AddAllTimelines : DeliverEvent
    data object RenderAll : DeliverEvent
    /** Takes the finished renders off the queue (their files stay in the gallery). */
    data object ClearDone : DeliverEvent
    data class Cancel(val jobId: String) : DeliverEvent
    data class Retry(val jobId: String) : DeliverEvent
    data class Open(val jobId: String) : DeliverEvent
    data object OpenHandled : DeliverEvent
    /** Nothing could open the output: no app for it, or ([unreachable]) a file the app may no longer hand out. */
    data class OpenFailed(val unreachable: Boolean = false) : DeliverEvent
    data class ProgramSurface(val change: SurfaceChange) : DeliverEvent
    data object ToastShown : DeliverEvent
}
