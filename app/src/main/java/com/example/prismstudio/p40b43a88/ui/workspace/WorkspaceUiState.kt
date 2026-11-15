package com.example.prismstudio.p40b43a88.ui.workspace

import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage

/** The workspace shell: project title, history, stage navigation and the program transport. */
data class WorkspaceUiState(
    val projectName: String = "",
    val saveStatus: String = "",
    val stage: WorkspaceStage = WorkspaceStage.EDIT,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    /** `history-menu`: the latest steps undo takes back (newest first) and redo brings back (next first). */
    val undoHistory: List<String> = emptyList(),
    val redoHistory: List<String> = emptyList(),
    val fps: Int = 30,
    val playheadFrame: Int = 0,
    val durationFrames: Int = 0,
    val playing: Boolean = false,
    /** `loop-toggle`: Resolve's Loop (Ctrl+/) — what plays starts again at its end. */
    val loop: Boolean = false,
    /** `playback-proxies-item`: Resolve's Proxy Handling — the monitors play footage's proxies — and how many there are. */
    val useProxies: Boolean = false,
    val proxyNote: String = "",
    /**
     * `ultrawide-toggle`: the ultrawide layout — the window at its widest (2700 dp, PICO's limit), the timeline across it,
     * two viewers above. A preference of this headset, not of the project.
     */
    val ultrawide: Boolean = false,
    /** `language-button`: the interface language picked (null = the system's) and the languages offered. */
    val language: AppLanguage? = null,
    val languages: List<AppLanguage> = AppLanguage.selectable,
    /** What a ranged playback (/ or Alt+/) is playing, while it plays. */
    val playRange: String? = null,
    val previewError: String? = null,
    val toast: String? = null,
) {
    val timecode: String get() = Timecode.format(playheadFrame, fps)
}

sealed interface WorkspaceEvent {
    data class SelectStage(val stage: WorkspaceStage) : WorkspaceEvent
    data object Undo : WorkspaceEvent
    data object Redo : WorkspaceEvent
    /** Resolve's History window: back through [steps] steps at once (or forward again), one change. */
    data class UndoSteps(val steps: Int) : WorkspaceEvent
    data class RedoSteps(val steps: Int) : WorkspaceEvent
    data class SetPlaying(val playing: Boolean) : WorkspaceEvent
    /** Resolve's Play Around Current Frame (/), Play In to Out (Alt+/) and Loop (Ctrl+/). */
    data object PlayAround : WorkspaceEvent
    data object PlayInToOut : WorkspaceEvent
    data class SetLoop(val on: Boolean) : WorkspaceEvent
    /** Resolve's Proxy Handling: prefer proxies (the project's setting, not an undo step) or the camera originals. */
    data class SetUseProxies(val on: Boolean) : WorkspaceEvent
    data class Seek(val frame: Int) : WorkspaceEvent
    data class Jog(val deltaFrames: Int) : WorkspaceEvent
    data object PreviousEdit : WorkspaceEvent
    data object NextEdit : WorkspaceEvent
    data object ToastShown : WorkspaceEvent
    /** `ultrawide-toggle`: the ultrawide layout on or off. */
    data class SetUltrawide(val on: Boolean) : WorkspaceEvent
    /** `language-button`: the interface language (null = the system's). */
    data class SetLanguage(val language: AppLanguage?) : WorkspaceEvent
}
