package com.example.prismstudio.p40b43a88.ui.workspace

import com.example.prismstudio.p40b43a88.data.repository.ProxyRepository
import com.example.prismstudio.p40b43a88.ui.components.languageMessage
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.data.repository.LanguageRepository
import com.example.prismstudio.p40b43a88.data.repository.LayoutPreferencesRepository
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyMediaUseCase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.usecase.PlayRangeUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopy
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.SourcePlaybackRepository
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.attempt
import com.example.prismstudio.p40b43a88.domain.usecase.EditPointsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectLabelsUseCase
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Shell of the open project: feeds the program monitor with every document change, owns stage
 * navigation, undo / redo and the program transport (play, seek, jog, edit points).
 */
class WorkspaceViewModel(
    private val session: EditorSessionRepository,
    private val program: ProgramPlaybackRepository,
    private val source: SourcePlaybackRepository,
    private val deviceMedia: DeviceMediaRepository,
    private val reverseCopies: ReverseCopyRepository = ReverseCopyRepository.None,
    private val editPoints: EditPointsUseCase = EditPointsUseCase(),
    private val labels: ProjectLabelsUseCase = ProjectLabelsUseCase(),
    private val playRanges: PlayRangeUseCase = PlayRangeUseCase(),
    private val proxies: ProxyRepository = ProxyRepository.None,
    private val proxyRules: ProxyMediaUseCase = ProxyMediaUseCase(),
    private val layout: LayoutPreferencesRepository = LayoutPreferencesRepository.InMemory(),
    private val languages: LanguageRepository = LanguageRepository.InMemory(),
    private val languageChoices: List<AppLanguage> = AppLanguage.selectable,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()
    private var openedId: String? = null
    private var offlineCheck: Job? = null
    /** The ranged playback under way (/ or Alt+/), and whether the program was playing at the last position. */
    private var range: PlayRangeUseCase.Range? = null
    private var wasPlaying = false

    init {
        viewModelScope.launch { layout.ultrawide.collect { on -> _state.update { it.copy(ultrawide = on) } } }
        viewModelScope.launch { languages.language.collect { chosen -> _state.update { it.copy(language = chosen, languages = languageChoices) } } }
        viewModelScope.launch {
            session.document.collect { doc ->
                program.setProject(doc)
                if (doc?.id != openedId) {
                    offlineCheck?.cancel()
                    openedId = doc?.id
                    if (doc != null) {
                        _state.update { it.copy(stage = if (doc.media.isEmpty()) WorkspaceStage.MEDIA else WorkspaceStage.EDIT) }
                        offlineCheck = checkOffline(doc)
                    }
                }
                _state.update {
                    it.copy(
                        projectName = doc?.name.orEmpty(),
                        fps = doc?.format?.fps ?: 30,
                        durationFrames = doc?.durationFrames ?: 0,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(session.document, proxies.proxies) { d, p -> d to p }.collect { (doc, statuses) ->
                _state.update {
                    it.copy(useProxies = doc?.useProxies == true, proxyNote = doc?.let { d -> proxyRules.handlingNote(d, statuses) }.orEmpty())
                }
            }
        }
        viewModelScope.launch {
            session.historyLabels.collect { labels ->
                _state.update { it.copy(undoHistory = labels.undo.take(HISTORY_SHOWN), redoHistory = labels.redo.take(REDO_SHOWN)) }
            }
        }
        viewModelScope.launch {
            combine(session.history, session.saveState) { h, s -> h to s }.collect { (h, s) ->
                _state.update {
                    it.copy(
                        canUndo = h.canUndo,
                        canRedo = h.canRedo,
                        undoLabel = h.undoLabel,
                        redoLabel = h.redoLabel,
                        saveStatus = labels.saveStatus(s.savedAtMillis, s.pending, s.failed),
                    )
                }
            }
        }
        viewModelScope.launch {
            // Reversed copies finish in the background, whichever stage is open: report the outcome.
            var previous = reverseCopies.copies.value
            reverseCopies.copies.collect { now ->
                val doc = session.document.value
                now.forEach { (id, copy) ->
                    if (previous[id] !is ReverseCopy.Making) return@forEach
                    val name = doc?.asset(id)?.name ?: return@forEach
                    when (copy) {
                        is ReverseCopy.Ready -> toast("倒放素材已生成：$name")
                        is ReverseCopy.Failed -> toast("倒放素材生成失败：$name（${copy.reason}）")
                        is ReverseCopy.Making -> Unit
                    }
                }
                previous = now
            }
        }
        viewModelScope.launch {
            program.state.collect { p ->
                followRange(Timecode.positionToFrame(p.positionMs, _state.value.fps), p.playing)
                _state.update {
                    it.copy(
                        playheadFrame = Timecode.positionToFrame(p.positionMs, it.fps),
                        playing = p.playing,
                        previewError = p.error,
                    )
                }
            }
        }
    }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            is WorkspaceEvent.SelectStage -> {
                if (event.stage == _state.value.stage) return
                program.pause()
                source.pause()
                _state.update { it.copy(stage = event.stage) }
            }
            WorkspaceEvent.Undo -> session.undo()?.let { label -> toast("已撤销：$label") }
            WorkspaceEvent.Redo -> session.redo()?.let { label -> toast("已重做：$label") }
            is WorkspaceEvent.UndoSteps -> session.undo(event.steps).takeIf { it.isNotEmpty() }?.let { labels ->
                toast(if (labels.size == 1) "已撤销：${labels.single()}" else "已撤销 ${labels.size} 步，回到“${labels.last()}”之前")
            }
            is WorkspaceEvent.RedoSteps -> session.redo(event.steps).takeIf { it.isNotEmpty() }?.let { labels ->
                toast(if (labels.size == 1) "已重做：${labels.single()}" else "已重做 ${labels.size} 步，到“${labels.last()}”之后")
            }
            is WorkspaceEvent.SetPlaying -> if (event.playing) program.play() else program.pause()
            WorkspaceEvent.PlayAround -> startRange { s -> playRanges.around(s.playheadFrame, s.durationFrames, s.fps) }
            WorkspaceEvent.PlayInToOut -> startRange { s -> playRanges.inToOut(marks(), s.durationFrames) }
            is WorkspaceEvent.SetLoop -> {
                _state.update { it.copy(loop = event.on) }
                toast(if (event.on) "循环播放：开" else "循环播放：关")
            }
            is WorkspaceEvent.SetLanguage -> if (event.language != languages.language.value) {
                languages.setLanguage(event.language)
                toast(languageMessage(event.language))
            }
            is WorkspaceEvent.SetUltrawide -> if (event.on != layout.ultrawide.value) {
                layout.setUltrawide(event.on)
                toast(if (event.on) "超宽布局：窗口加宽到 2700 dp，时间轴铺满底部，上方并排两个监视器" else "已恢复标准布局")
            }
            is WorkspaceEvent.SetUseProxies -> session.document.value?.let { doc ->
                session.update { it.copy(useProxies = event.on) }
                val ready = proxyRules.readyCount(doc, proxies.proxies.value)
                toast(
                    when {
                        !event.on -> "已改回原始素材播放"
                        ready == 0 -> "已开启代理播放：还没有代理，在媒体页为素材生成"
                        else -> "已开启代理播放：$ready 个视频用代理，渲染仍用原片"
                    },
                )
            }
            is WorkspaceEvent.Seek -> seekFrame(event.frame)
            is WorkspaceEvent.Jog -> {
                program.pause()
                seekFrame(_state.value.playheadFrame + event.deltaFrames)
            }
            WorkspaceEvent.PreviousEdit -> session.document.value?.let { doc ->
                seekFrame(editPoints.previous(doc.timeline, _state.value.playheadFrame))
            }
            WorkspaceEvent.NextEdit -> session.document.value?.let { doc ->
                seekFrame(editPoints.next(doc.timeline, _state.value.playheadFrame))
            }
            WorkspaceEvent.ToastShown -> _state.update { it.copy(toast = null) }
        }
    }

    private fun marks(): TimelineMarks = session.document.value?.timeline?.marks ?: TimelineMarks()

    private fun startRange(make: (WorkspaceUiState) -> PlayRangeUseCase.Range) {
        val r = try {
            make(_state.value)
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法播放")
        }
        source.pause()
        range = r
        seekFrame(r.from)
        program.play()
        _state.update { it.copy(playRange = r.label) }
        toast("播放${r.label}" + if (_state.value.loop) "（循环）" else "")
    }

    /** Each position while playing: a loop starts again, a ranged playback stops (back where / began); a pause ends it. */
    private fun followRange(frame: Int, playing: Boolean) {
        val s = _state.value
        val was = wasPlaying
        wasPlaying = playing
        when (val step = playRanges.step(range, s.loop, marks(), s.durationFrames, frame, playing, was)) {
            PlayRangeUseCase.Step.Continue -> if (range != null && was && !playing) endRange()
            is PlayRangeUseCase.Step.Restart -> {
                seekFrame(step.frame)
                if (!playing) program.play()
            }
            is PlayRangeUseCase.Step.Stop -> {
                endRange()
                program.pause()
                seekFrame(step.frame)
            }
        }
    }

    private fun endRange() {
        range = null
        _state.update { it.copy(playRange = null) }
    }

    private fun seekFrame(frame: Int) {
        val s = _state.value
        val clamped = frame.coerceIn(0, s.durationFrames.coerceAtLeast(0))
        program.seekTo(Timecode.frameToSeekMs(clamped, s.fps))
        _state.update { it.copy(playheadFrame = clamped) }
    }

    /** Marks media whose file disappeared since the last session (not part of the undo history). */
    private fun checkOffline(doc: ProjectDocument) = viewModelScope.launch {
        val checked = doc.media.map { it.uri }.distinct().mapNotNull { uri ->
            attempt { deviceMedia.isAvailable(uri) }.getOrNull()?.let { uri to it }
        }.toMap()
        var missing = 0
        session.update { current ->
            if (current.id != doc.id) return@update current
            missing = current.media.count { checked[it.uri] == false }
            current.withMediaAvailability(checked)
        }
        if (missing > 0) toast("$missing 个素材无法读取，请检查读取权限或在媒体页重新链接。")
    }

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    private companion object {
        /** Steps the history menu lists: undo, and redo below them. */
        const val HISTORY_SHOWN = 12
        const val REDO_SHOWN = 5
    }

    override fun onCleared() {
        program.pause()
        source.pause()
        program.setProject(null)
        source.load(null)
    }
}
