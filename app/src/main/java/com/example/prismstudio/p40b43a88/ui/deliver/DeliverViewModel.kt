package com.example.prismstudio.p40b43a88.ui.deliver

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.domain.usecase.OfflineRenderUseCase
import kotlin.math.roundToInt
import com.example.prismstudio.p40b43a88.domain.usecase.IndividualClipsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ColorManagementUseCase
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.usecase.TimelineMarksUseCase
import com.example.prismstudio.p40b43a88.ui.color.MiniTimelineUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.LoudnessRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.RenderFailure
import com.example.prismstudio.p40b43a88.data.repository.RenderRepository
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderResolution
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec
import com.example.prismstudio.p40b43a88.domain.usecase.EstimateRenderUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RenderQueueUseCase
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.components.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Deliver stage: render settings and presets, size estimate and the sequential render queue. */
class DeliverViewModel(
    private val session: EditorSessionRepository,
    private val program: ProgramPlaybackRepository,
    private val render: RenderRepository,
    ids: IdGenerator,
    private val loudness: LoudnessRepository = LoudnessRepository.None,
    private val queue: RenderQueueUseCase = RenderQueueUseCase(ids),
    private val estimator: EstimateRenderUseCase = EstimateRenderUseCase(),
    private val subtitleExport: com.example.prismstudio.p40b43a88.domain.usecase.SubtitleExportUseCase = com.example.prismstudio.p40b43a88.domain.usecase.SubtitleExportUseCase(),
    private val colors: ColorManagementUseCase = ColorManagementUseCase(),
    private val levels: LoudnessUseCase = LoudnessUseCase(),
    private val ducking: DuckingUseCase = DuckingUseCase(),
    private val marks: TimelineMarksUseCase = TimelineMarksUseCase(),
    private val individual: IndividualClipsUseCase = IndividualClipsUseCase(),
    private val offline: OfflineRenderUseCase = OfflineRenderUseCase(),
) : ViewModel() {

    private val _state = MutableStateFlow(DeliverUiState())
    val state: StateFlow<DeliverUiState> = _state.asStateFlow()

    /** Whether the output last asked to open has a picture (where its file is kept, when nothing opens it). */
    private var openedVideo = true
    private var renderJob: Job? = null
    private var stillJob: Job? = null
    private var runningId: String? = null
    /** Loudness profiles by asset id (null: no decodable sound), and the assets being measured. */
    private val profiles = mutableMapOf<String, LoudnessProfile?>()
    private val measuring = mutableSetOf<String>()

    init {
        viewModelScope.launch { session.document.collect { publish() } }
        viewModelScope.launch {
            program.state.collect { p ->
                _state.update {
                    it.copy(
                        previewApproximate = p.approximate, programLayers = p.layers, programComposites = p.compositesAt(),
                        playheadFrame = Timecode.positionToFrame(p.positionMs, it.fps),
                    )
                }
            }
        }
    }

    private val doc: ProjectDocument? get() = session.document.value

    fun onEvent(event: DeliverEvent) {
        when (event) {
            is DeliverEvent.SelectPreset -> settings { s -> estimator.applyPreset(s, event.preset, doc?.name.orEmpty()) }
            is DeliverEvent.FileName -> settings { it.copy(fileName = event.text.take(60), preset = it.preset) }
            is DeliverEvent.Codec -> settings { it.copy(codec = VideoCodec.entries[event.index.coerceIn(0, 1)], preset = null) }
            is DeliverEvent.Resolution -> settings { it.copy(resolution = RenderResolution.entries[event.index.coerceIn(0, 2)], preset = null) }
            is DeliverEvent.Fps -> settings { it.copy(fps = DeliverUiState.FRAME_RATES[event.index.coerceIn(0, 3)], preset = null) }
            is DeliverEvent.Quality -> settings { it.copy(quality = RenderQuality.entries[event.index.coerceIn(0, 2)], preset = null) }
            is DeliverEvent.IncludeAudio -> {
                // The mix alone needs its sound.
                if (!event.include && !_state.value.settings.includeVideo) return toast("只导出音频时不能关闭声音")
                settings { it.copy(includeAudio = event.include) }
            }
            is DeliverEvent.IncludeVideo -> settings {
                if (event.include) it.copy(includeVideo = true) else it.copy(includeVideo = false, includeAudio = true, preset = null)
            }
            is DeliverEvent.Range -> settings { it.copy(range = RenderRange.entries[event.index.coerceIn(0, 1)]) }
            is DeliverEvent.RenderMode -> settings { it.copy(individualClips = event.individual) }
            is DeliverEvent.Seek -> doc?.let { d ->
                program.pause()
                val frame = event.frame.coerceIn(0, d.durationFrames.coerceAtLeast(0))
                program.seekTo(Timecode.frameToSeekMs(frame, d.format.fps))
                _state.update { it.copy(playheadFrame = frame) }
            }
            DeliverEvent.MarkIn -> mark(RenderRange.IN_TO_OUT) { t, fps -> marks.markIn(t, _state.value.playheadFrame, fps) }
            DeliverEvent.MarkOut -> mark(RenderRange.IN_TO_OUT) { t, fps -> marks.markOut(t, _state.value.playheadFrame, fps) }
            DeliverEvent.ClearMarks -> mark(RenderRange.WHOLE_TIMELINE) { t, _ -> marks.clear(t) }
            is DeliverEvent.ExportSubtitles -> settings { it.copy(exportSubtitles = event.export) }
            is DeliverEvent.SubtitleFormat ->
                settings { it.copy(subtitleFormat = SubtitleFileFormat.entries[event.index.coerceIn(0, SubtitleFileFormat.entries.lastIndex)]) }
            is DeliverEvent.AudioFormat ->
                settings { it.copy(audioFormat = AudioExportFormat.entries[event.index.coerceIn(0, AudioExportFormat.entries.lastIndex)]) }
            is DeliverEvent.WavRate -> settings { it.copy(wavSampleRate = RenderSettings.WAV_RATES[event.index.coerceIn(0, RenderSettings.WAV_RATES.lastIndex)]) }
            is DeliverEvent.WavBits -> settings { it.copy(wavBits = RenderSettings.WAV_BITS[event.index.coerceIn(0, RenderSettings.WAV_BITS.lastIndex)]) }
            is DeliverEvent.ToneMap -> session.edit("色调映射：${event.method.label}") { d -> d.copy(colorManagement = d.colorManagement.copy(toneMapping = event.method)) }
            is DeliverEvent.PqPeak -> {
                val nits = ColorManagement.PQ_PEAKS[event.index.coerceIn(0, ColorManagement.PQ_PEAKS.lastIndex)]
                session.edit("PQ 峰值 $nits nits") { d -> d.copy(colorManagement = d.colorManagement.copy(pqPeakNits = nits)) }
            }
            is DeliverEvent.Loudness -> settings {
                val targets = LoudnessUseCase.TARGETS_LUFS
                it.copy(loudnessTargetLufs = if (event.index <= 0) null else targets[(event.index - 1).coerceAtMost(targets.lastIndex)])
            }
            DeliverEvent.AddJob -> addJob()
            DeliverEvent.AddAllTimelines -> addAllTimelines()
            DeliverEvent.RenderAll -> renderAll()
            DeliverEvent.ExportStill -> exportStill()
            DeliverEvent.ClearDone -> {
                val done = doc?.jobs?.count { it.status.mediaSaved } ?: 0
                if (done > 0) {
                    session.update { d -> d.copy(jobs = queue.clearDone(d.jobs)) }
                    _state.update { it.copy(lastRenderSummary = null, toast = "已清除 $done 条渲染记录，已导出的文件保留在原位置") }
                }
            }
            is DeliverEvent.Cancel -> cancel(event.jobId)
            is DeliverEvent.Retry -> {
                var added = false
                session.update { d ->
                    val jobs = queue.retry(d.jobs, event.jobId)
                    added = jobs != d.jobs
                    d.copy(jobs = jobs)
                }
                if (added) toast("已重新加入队列")
            }
            is DeliverEvent.Open -> doc?.jobs?.firstOrNull { it.id == event.jobId }?.let { job ->
                val uri = job.outputUri ?: return@let
                openedVideo = job.settings.includeVideo
                _state.update { it.copy(openUri = uri) }
            }
            DeliverEvent.OpenHandled -> _state.update { it.copy(openUri = null) }
            // Release round R9: a render left by an earlier install (or moved, or deleted) is not the app's to open.
            is DeliverEvent.OpenFailed -> toast(
                if (event.unreachable) {
                    "打不开这个文件：它可能已被移动或删除，或是之前安装的 ${PRODUCT_NAME} 渲染的"
                } else if (openedVideo) {
                    "没有可播放视频的应用，可在系统文件管理器的 Movies 目录查找"
                } else {
                    "没有可播放音频的应用，可在系统文件管理器的 Music 目录查找"
                },
            )
            is DeliverEvent.ProgramSurface -> program.route(event.change)
            DeliverEvent.ToastShown -> _state.update { it.copy(toast = null) }
        }
    }

    /**
     * Resolve's Deliver timeline marks: kept with the timeline (outside the edit history, as on the Edit page); marking
     * turns the render range to 入点至出点, clearing back to 整条时间线.
     */
    private fun mark(range: RenderRange, change: (Timeline, Int) -> TimelineMarksUseCase.Result) {
        val d = doc ?: return
        val result = try {
            change(d.timeline, d.format.fps)
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法标记入出点")
        }
        session.update { it.copy(timeline = it.timeline.copy(marks = result.timeline.marks), render = it.render.copy(range = range)) }
        toast(result.message + if (range == RenderRange.IN_TO_OUT) "，渲染入点至出点" else "，渲染整条时间线")
    }

    private fun settings(change: (RenderSettings) -> RenderSettings) {
        session.update { d -> d.copy(render = change(d.render)) }
    }

    private fun addJob() {
        val d = doc ?: return
        if (d.durationFrames <= 0) return toast("时间线为空，无法加入渲染队列")
        if (d.render.fileName.isBlank()) return toast("请先填写文件名")
        if (d.render.individualClips) return addIndividualClips()
        var queuedName = ""
        session.update { current ->
            val jobs = queue.enqueue(current)
            queuedName = jobs.last().fileName
            current.copy(jobs = jobs)
        }
        toast("已加入渲染队列：$queuedName${offlineWarning(d)}")
    }

    /** Said after queueing when the render holds offline media: "。2 个片段的素材离线，渲染出来会是黑场". */
    private fun offlineWarning(d: ProjectDocument): String {
        val (start, end) = range(d, d.render)
        val clips = offline.clips(d, d.render, start, end)
        return if (clips.isEmpty()) "" else "。注意：${clips.size} 个片段的素材离线，渲染时画面是黑场或没有声音"
    }

    /** Resolve's Individual clips: one job per clip in the render range, each rendering that clip alone. */
    private fun addIndividualClips() {
        var added = 0
        session.update { current ->
            val jobs = queue.enqueueIndividual(current, individual)
            added = jobs.size - current.jobs.size
            current.copy(jobs = jobs)
        }
        toast(if (added > 0) "已把 $added 个片段加入渲染队列，每个片段一个文件${doc?.let(::offlineWarning).orEmpty()}" else "渲染范围里没有片段")
    }

    /** Every timeline with something on it, one job each (Resolve: a job per timeline, all rendered in one go). */
    private fun addAllTimelines() {
        val d = doc ?: return
        if (d.render.fileName.isBlank()) return toast("请先填写文件名")
        var added = 0
        session.update { current ->
            val jobs = queue.enqueueAll(current)
            added = jobs.size - current.jobs.size
            current.copy(jobs = jobs)
        }
        toast(if (added > 0) "已把 $added 条时间线加入渲染队列" else "时间线都是空的，没有可渲染的内容")
    }

    private fun renderAll() {
        if (stillJob?.isActive == true) return toast("正在导出当前帧，完成后再渲染队列")
        if (renderJob?.isActive == true) return toast("正在渲染，新加入的任务会接着渲染")
        if (doc?.jobs?.let(queue::next) == null) return toast("队列里没有等待渲染的任务，先点“加入渲染队列”")
        program.pause()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(rendering = true, lastRenderSummary = null) }
            var videos = 0
            var audioFiles = 0
            var failed = 0
            var incompleteMedia = 0
            var subtitleFiles = 0
            var missingSubtitles = 0
            while (true) {
                val d = doc ?: break
                val next = queue.next(d.jobs) ?: break
                runningId = next.id
                // The job renders its own timeline, whichever is open now — or one clip of it alone.
                val timeline = d.viewing(next.timelineId)
                val source = if (next.clipId == null) timeline else timeline?.let { individual.solo(it, next.clipId) }
                if (source == null) {
                    val why = when {
                        timeline == null -> "时间线“${next.timelineName}”已删除"
                        timeline.timeline.clip(next.clipId) == null -> "片段已不在时间线上"
                        else -> "片段或所在轨道已停用，启用后可重试"
                    }
                    session.update { it.copy(jobs = queue.fail(it.jobs, next.id, why)) }
                    failed++
                    runningId = null
                    continue
                }
                session.update { it.copy(jobs = queue.start(it.jobs, next.id)) }
                // An individual clip renders whole, whatever the range picked which clips.
                val (start, end) = when {
                    next.clipId != null -> null to null
                    next.queuedRange != null -> next.queuedRange.inFrame to next.queuedRange.outFrame
                    else -> range(source, next.settings)
                }
                try {
                    if (next.queuedRange != null && end != null && end > source.durationFrames) {
                        throw RenderFailure("排队时的导出范围超出当前时间线，请重新标记范围并加入队列")
                    }
                    val output = render.render(source, next.settings, start, end) { p ->
                        session.update { it.copy(jobs = queue.progress(it.jobs, next.id, p)) }
                    }
                    val warning = buildList {
                        if (output.offlineMedia.isNotEmpty()) {
                            add("成片缺少素材：${output.offlineMedia.joinToString(", ")}")
                            add("请恢复读取权限或在媒体页重新链接后重试。")
                        }
                        if (output.missingSubtitles.isNotEmpty()) add("媒体已保存，字幕文件未保存：${output.missingSubtitles.joinToString(", ")}")
                    }.joinToString("\n").ifEmpty { null }
                    session.update { it.copy(jobs = queue.complete(it.jobs, next.id, output.uri, output.bytes, warning)) }
                    if (next.settings.includeVideo) videos++ else audioFiles++
                    if (output.offlineMedia.isNotEmpty()) incompleteMedia++
                    subtitleFiles += output.subtitleUris.size
                    missingSubtitles += output.missingSubtitles.size
                } catch (e: CancellationException) {
                    // Interrupted by the app (stage closed): put the job back. A user cancel already removed it.
                    session.update { it.copy(jobs = queue.requeue(it.jobs, next.id)) }
                    throw e
                } catch (e: Exception) {
                    session.update { it.copy(jobs = queue.fail(it.jobs, next.id, e.message ?: e.javaClass.simpleName)) }
                    failed++
                } finally {
                    runningId = null
                }
            }
            val summary = buildList {
                if (failed > 0) add("$failed 个任务失败，请查看渲染队列")
                if (incompleteMedia > 0) add("$incompleteMedia 份成片缺少素材，请查看渲染队列")
                if (missingSubtitles > 0) add("$missingSubtitles 个字幕文件保存失败，请检查存储空间")
                if (videos > 0) add("已保存 $videos 个视频到 Movies/${PRODUCT_NAME}")
                if (audioFiles > 0) add("已保存 $audioFiles 个音频文件到 Music/${PRODUCT_NAME}")
                if (subtitleFiles > 0) add("已保存 $subtitleFiles 个字幕文件到 Download/${PRODUCT_NAME}")
            }.joinToString("\n")
            _state.update { it.copy(rendering = false, lastRenderSummary = summary.ifEmpty { null }) }
        }
        renderJob?.invokeOnCompletion { _state.update { it.copy(rendering = false) } }
    }

    /**
     * The frame at the playhead, rendered as an export draws it, saved as `<project>_<timecode>.png` in
     * Pictures/Reality Video Editor; one at a time, and not while the queue renders.
     */
    private fun exportStill() {
        val d = doc ?: return
        if (d.durationFrames <= 0) return toast("时间线为空，没有可导出的画面")
        if (renderJob?.isActive == true || stillJob?.isActive == true) return toast("正在渲染，完成后再导出当前帧")
        program.pause()
        val fps = d.format.fps
        val frame = Timecode.positionToFrame(program.state.value.positionMs, fps).coerceIn(0, d.durationFrames - 1)
        val name = "${d.name.ifBlank { PRODUCT_NAME }}_${Timecode.format(frame, fps).replace(':', '-')}"
        stillJob = viewModelScope.launch {
            _state.update { it.copy(exportingStill = true) }
            try {
                val output = render.renderStill(d, d.render.copy(fileName = name), frame)
                toast(buildList {
                    add("已导出当前帧：$name.png，保存在“图片/${PRODUCT_NAME}”")
                    if (output.offlineMedia.isNotEmpty()) {
                        add("静帧缺少素材：${output.offlineMedia.joinToString(", ")}")
                        add("请恢复读取权限或在媒体页重新链接后重试。")
                    }
                }.joinToString("\n"))
            } catch (e: RenderFailure) {
                toast(e.message ?: "无法导出当前帧")
            } finally {
                _state.update { it.copy(exportingStill = false) }
            }
        }
    }

    private fun cancel(jobId: String) {
        if (jobId == runningId) renderJob?.cancel()
        session.update { it.copy(jobs = queue.cancel(it.jobs, jobId)) }
        toast("已取消")
    }

    /** Frames to render: everything, or the timeline's In / Out marks for 入点至出点 (one mark alone: to the end / from the start). */
    /** What 单独片段 will queue: how many files, of which clips, holding what. */
    private fun individualNote(d: ProjectDocument, settings: RenderSettings): String {
        val clips = individual.clips(d, settings)
        if (clips.isEmpty()) return "渲染范围里没有${if (settings.includeVideo) "画面" else "声音"}片段"
        val seconds = clips.sumOf { it.durationFrames } / d.format.fps.coerceAtLeast(1).toFloat()
        return "范围内的 ${clips.size} 个片段各存一个文件（共 ${formatSeconds(seconds)}）：只含片段本身和链接的声音，文件名后加片段名"
    }

    private fun formatSeconds(s: Float): String = if (s < 60f) "${s.roundToInt()} 秒" else "${(s / 60).toInt()} 分 ${(s % 60).roundToInt()} 秒"

    private fun range(d: ProjectDocument, settings: RenderSettings): Pair<Int?, Int?> {
        val limits = settings.range.frameLimits(d.timeline)
        return limits.inFrame to limits.outFrame
    }

    /** What the shading on the Deliver timeline means: "渲染 00:00:00:00 → 00:00:34:00（整条时间线，34 秒）". */
    private fun rangeLabel(d: ProjectDocument, start: Int?, end: Int?): String {
        if (d.durationFrames <= 0) return "时间线为空"
        val fps = d.format.fps
        val from = start ?: 0
        val until = end ?: d.durationFrames
        val which = if (start == null) "整条时间线" else "入点至出点"
        return "渲染 ${Timecode.format(from, fps)} → ${Timecode.format(until, fps)}（$which，${Timecode.format(until - from, fps)}）"
    }

    private fun publish() {
        val d = doc ?: return
        val settings = d.render
        val (start, end) = range(d, settings)
        // Individual clips: the files together run as long as their clips.
        val frames = if (settings.individualClips) individual.clips(d, settings).sumOf { it.durationFrames } else (end ?: d.durationFrames) - (start ?: 0)
        // Compound clips' timelines are not rendered on their own: they do not make a project of several timelines.
        val several = d.timelineEntries.count { !it.compound } > 1
        val retried = d.jobs.filter { it.status != RenderStatus.FAILED }.mapNotNullTo(mutableSetOf()) { it.retryOf }
        val rangeNote = when {
            settings.range != RenderRange.IN_TO_OUT -> null
            start == null -> "时间线上没有入出点，将渲染整条时间线：在剪辑页按 I / O 或用飞梭台的入点、出点键标记"
            else -> "范围：${Timecode.format(start, d.format.fps)} → ${Timecode.format(end ?: 0, d.format.fps)}（时间线入点到出点）"
        }
        _state.update { s ->
            s.copy(
                settings = settings,
                presets = s.presets.map { it.copy(selected = it.preset == settings.preset) },
                estimate = estimator(settings, d.format, frames).label,
                frameRateNote = estimator.frameRateNote(settings, d.format),
                subtitleNote = subtitleExport.note(d, settings.subtitleFormat, settings.exportSubtitles, start, end),
                hdrNote = colors.note(d),
                pqPresent = ColorTransfer.PQ in colors.hdrClips(d),
                colorManagement = d.colorManagement,
                rangeNote = rangeNote,
                individualNote = if (!settings.individualClips) null else individualNote(d, settings),
                offlineNote = offline.note(offline.clips(d, settings, start, end)),
                loudnessNote = loudnessNote(d, settings, start, end),
                jobs = d.jobs.map { job ->
                    // With several timelines a job names the one it renders (as it is called now).
                    val timeline = d.storedTimeline(job.timelineId)
                    val name = timeline?.name ?: "${job.timelineName}（已删除）"
                    RenderJobUi(
                        id = job.id,
                        name = job.fileName,
                        status = job.status,
                        statusLabel = job.statusLabel,
                        progress = job.progress,
                        detail = if (several) "$name · ${job.detailLabel}" else job.detailLabel,
                        rangeLabel = job.queuedRange?.let { limits ->
                            if (limits.isEmpty) RenderRange.WHOLE_TIMELINE.label
                            else timeline?.let { TimelineMarksUseCase.label(limits, it.format.fps) }
                        },
                        error = job.errorMessage,
                        cancellable = job.cancellable,
                        openLabel = if (job.settings.includeVideo) "在相册中查看" else "播放音频",
                        retryLabel = when {
                            job.status == RenderStatus.FAILED -> "重试"
                            job.status == RenderStatus.PARTIAL && job.id !in retried -> "重新渲染"
                            else -> null
                        },
                    )
                },
                pictureAspect = d.format.width.toFloat() / d.format.height.coerceAtLeast(1),
                programEmpty = d.timeline.clips.isEmpty(),
                timelineLabel = "${d.timelineName} · ${d.format.width}×${d.format.height}",
                severalTimelines = several,
                miniTimeline = MiniTimelineUi.of(d),
                renderRange = if (d.durationFrames > 0) (start ?: 0) until (end ?: d.durationFrames) else null,
                rangeLabel = rangeLabel(d, start, end),
                fps = d.format.fps,
                hasMarks = !d.timeline.marks.isEmpty,
            )
        }
    }

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    /** The program's loudness in the render range and what Normalize Audio does to it; measures files on first look. */
    private fun loudnessNote(d: ProjectDocument, settings: RenderSettings, start: Int?, end: Int?): String {
        if (!settings.includeAudio) return "不导出音频"
        val sounds = d.timeline.clips.filter { it.kind == ClipKind.AUDIO }
            .mapNotNull { d.asset(it.assetId) }
            .filter { !it.offline }
            .distinctBy { it.id }
        if (sounds.isEmpty()) return "时间线上没有声音"
        val waiting = sounds.filter { it.id !in profiles }
        if (waiting.isNotEmpty()) {
            waiting.filter { measuring.add(it.id) }.forEach { asset ->
                viewModelScope.launch {
                    profiles[asset.id] = loudness.profile(asset.uri)
                    measuring.remove(asset.id)
                    publish()
                }
            }
            return "正在测量节目响度…"
        }
        val ready = profiles.mapNotNull { (id, profile) -> profile?.let { id to it } }.toMap()
        // Ducking dips the mix: the reading includes the dips (their key tracks are measured above).
        val envelopes = ducking.analyses(d, ready).mapValues { it.value.envelope }
        val reading = levels.program(d, ready, start, end, envelopes) as? LoudnessUseCase.Reading.Measured ?: return "节目几乎无声"
        val measured = "节目响度 ${formatValue(reading.lufs, 1, false)} LUFS · 真峰值 ≤ ${formatValue(reading.truePeakDbtp, 1, false)} dBTP"
        val target = settings.loudnessTargetLufs ?: return measured
        val gain = (target - reading.lufs).coerceAtMost(LoudnessUseCase.MAX_RENDER_GAIN_DB)
        val peakAfter = reading.truePeakDbtp + gain
        val clipping = if (peakAfter > LoudnessUseCase.TRUE_PEAK_LIMIT_DBTP) "，真峰值可达 ${formatValue(peakAfter, 1, false)} dBTP，超过 -1 dBTP 可能削波" else ""
        return "$measured\n导出时整体 ${formatValue(gain, 1, true)} dB → ${formatValue(reading.lufs + gain, 1, false)} LUFS$clipping"
    }

    override fun onCleared() {
        renderJob?.cancel()
        // viewModelScope is usually cancelled first and renderAll re-queues the job itself; this covers a render still running.
        runningId?.let { id -> session.update { it.copy(jobs = queue.requeue(it.jobs, id)) } }
    }
}
