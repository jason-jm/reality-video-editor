package com.example.prismstudio.p40b43a88.ui.workspace

import com.example.prismstudio.p40b43a88.ui.deliver.DeliverEvent
import com.example.prismstudio.p40b43a88.ui.deliver.DeliverViewModelFactory
import com.example.prismstudio.p40b43a88.ui.deliver.DeliverViewModel
import com.example.prismstudio.p40b43a88.ui.edit.components.PlaybackAction
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionScope
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.automation.AutomationBridge
import com.example.prismstudio.p40b43a88.ui.audio.AudioScreen
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.ui.color.ColorEvent
import com.example.prismstudio.p40b43a88.ui.color.ColorScreen
import com.example.prismstudio.p40b43a88.ui.color.ColorUiState
import com.example.prismstudio.p40b43a88.ui.color.ColorViewModel
import com.example.prismstudio.p40b43a88.ui.color.ColorViewModelFactory
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import com.example.prismstudio.p40b43a88.ui.deliver.DeliverScreen
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.EditScreen
import com.example.prismstudio.p40b43a88.ui.edit.EditUiState
import com.example.prismstudio.p40b43a88.ui.edit.EditViewModel
import com.example.prismstudio.p40b43a88.ui.edit.EditViewModelFactory
import com.example.prismstudio.p40b43a88.ui.media.MediaEvent
import com.example.prismstudio.p40b43a88.ui.media.MediaScreen
import com.example.prismstudio.p40b43a88.ui.media.MediaUiState
import com.example.prismstudio.p40b43a88.ui.media.MediaViewModel
import com.example.prismstudio.p40b43a88.ui.media.MediaViewModelFactory
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage
import com.example.prismstudio.p40b43a88.ui.workspace.components.DeckTools
import com.example.prismstudio.p40b43a88.ui.workspace.components.StageTabBar
import com.example.prismstudio.p40b43a88.ui.workspace.components.WorkspaceTitleBar
import com.pico.spatial.ui.design.windows.SnackbarHost

/**
 * The open-project workspace: title bar and the current stage page, with the stage TabBar and the
 * Jog Deck Toolbar as window-level ornaments. Stage ViewModels live as long as the project is open.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WorkspaceScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val workspace: WorkspaceViewModel = viewModel(factory = remember(context) { WorkspaceViewModelFactory(context) })
        val media: MediaViewModel = viewModel(factory = remember(context) { MediaViewModelFactory(context) })
        val edit: EditViewModel = viewModel(factory = remember(context) { EditViewModelFactory(context) })
        val color: ColorViewModel = viewModel(factory = remember(context) { ColorViewModelFactory(context) })
        val deliver: DeliverViewModel = viewModel(factory = remember(context) { DeliverViewModelFactory(context) })
        val state by workspace.state.collectAsStateWithLifecycle()
        val mediaState by media.state.collectAsStateWithLifecycle()
        val editState by edit.state.collectAsStateWithLifecycle()
        val colorState by color.state.collectAsStateWithLifecycle()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val shortcuts = ShortcutDispatcher(
            state, mediaState, editState, colorState, workspace::onEvent, media::onEvent, edit::onEvent, color::onEvent, deliver::onEvent,
        )
        val latestShortcuts by rememberUpdatedState(shortcuts)
        val close by rememberUpdatedState(onClose)
        LaunchedEffect(Unit) {
            AutomationBridge.commands.collect { command ->
                val parts = command.split(' ')
                when (parts.firstOrNull()) {
                    "key" -> parts.getOrNull(1)?.let { name ->
                        latestShortcuts.handle(
                            KeyboardShortcuts.resolve(android.view.KeyEvent.keyCodeFromString(name), "shift" in parts, "ctrl" in parts, "alt" in parts),
                        )
                    }
                    "back" -> close()
                }
            }
        }
        SnackbarHost {
            ToastEffect(state.toast) { workspace.onEvent(WorkspaceEvent.ToastShown) }
            Box(
                modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && shortcuts.handle(
                            KeyboardShortcuts.resolve(
                                event.nativeKeyEvent.keyCode,
                                shift = event.isShiftPressed,
                                ctrl = event.isCtrlPressed || event.isMetaPressed,
                                alt = event.isAltPressed,
                            ),
                        )
                    },
            ) {
                Column(Modifier.fillMaxSize()) {
                    WorkspaceTitleBar(
                        projectName = state.projectName,
                        saveStatus = state.saveStatus,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        undoLabel = state.undoLabel,
                        redoLabel = state.redoLabel,
                        onBack = onClose,
                        onUndo = { workspace.onEvent(WorkspaceEvent.Undo) },
                        onRedo = { workspace.onEvent(WorkspaceEvent.Redo) },
                        undoHistory = state.undoHistory,
                        redoHistory = state.redoHistory,
                        onUndoSteps = { workspace.onEvent(WorkspaceEvent.UndoSteps(it)) },
                        onRedoSteps = { workspace.onEvent(WorkspaceEvent.RedoSteps(it)) },
                        ultrawide = state.ultrawide,
                        onUltrawide = { workspace.onEvent(WorkspaceEvent.SetUltrawide(it)) },
                        language = state.language,
                        languages = state.languages,
                        onLanguage = { workspace.onEvent(WorkspaceEvent.SetLanguage(it)) },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (state.stage) {
                            WorkspaceStage.MEDIA -> MediaScreen(onOpenEdit = { workspace.onEvent(WorkspaceEvent.SelectStage(WorkspaceStage.EDIT)) })
                            WorkspaceStage.EDIT -> EditScreen(loop = state.loop, useProxies = state.useProxies, proxyNote = state.proxyNote) { action ->
                                workspace.onEvent(
                                    when (action) {
                                        PlaybackAction.AROUND -> WorkspaceEvent.PlayAround
                                        PlaybackAction.IN_TO_OUT -> WorkspaceEvent.PlayInToOut
                                        PlaybackAction.LOOP -> WorkspaceEvent.SetLoop(!state.loop)
                                        PlaybackAction.PROXIES -> WorkspaceEvent.SetUseProxies(!state.useProxies)
                                    },
                                )
                            }
                            WorkspaceStage.COLOR -> ColorScreen()
                            WorkspaceStage.AUDIO -> AudioScreen()
                            WorkspaceStage.DELIVER -> DeliverScreen()
                        }
                    }
                }
                StageTabBar(state.stage) { workspace.onEvent(WorkspaceEvent.SelectStage(it)) }
                DeckTools(
                    workspace = state,
                    media = mediaState,
                    edit = editState,
                    color = colorState,
                    onWorkspace = workspace::onEvent,
                    onMedia = media::onEvent,
                    onEdit = edit::onEvent,
                    onColor = color::onEvent,
                )
            }
        }
    }
}

/** Routes a keyboard [ShortcutAction] to the monitor and stage that own it. */
private class ShortcutDispatcher(
    private val workspace: WorkspaceUiState,
    private val media: MediaUiState,
    private val edit: EditUiState,
    private val color: ColorUiState,
    private val onWorkspace: (WorkspaceEvent) -> Unit,
    private val onMedia: (MediaEvent) -> Unit,
    private val onEdit: (EditEvent) -> Unit,
    private val onColor: (ColorEvent) -> Unit,
    private val onDeliver: (DeliverEvent) -> Unit,
) {
    private val inMedia get() = workspace.stage == WorkspaceStage.MEDIA
    private val sourceInEdit get() = workspace.stage == WorkspaceStage.EDIT && edit.viewerMode == EditUiState.VIEWER_SOURCE

    /**
     * Resolve's edit keys: the Media stage's marked range, or the Edit stage's selected media-pool item,
     * onto the timeline; null where they mean nothing.
     */
    private fun sourceEdit(mode: SourceEdit): Unit? = when (workspace.stage) {
        WorkspaceStage.MEDIA -> onMedia(if (mode == SourceEdit.APPEND) MediaEvent.Append else MediaEvent.Edit(mode))
        WorkspaceStage.EDIT -> onEdit(EditEvent.EditFromSource(mode))
        else -> null
    }

    /** I / O / Alt+X: the source's marks on the Media stage, the timeline's on the Edit stage; null elsewhere. */
    private fun programShown() = !inMedia && !sourceInEdit

    private fun marks(media: MediaEvent, edit: EditEvent, deliver: DeliverEvent): Unit? = when (workspace.stage) {
        WorkspaceStage.MEDIA -> onMedia(media)
        WorkspaceStage.EDIT -> onEdit(edit)
        // The Deliver timeline's marks: what 入点至出点 renders.
        WorkspaceStage.DELIVER -> onDeliver(deliver)
        else -> null
    }

    /** Resolve's nudge: the slip / roll trim in those dial modes, else the selected clip by [frames]. */
    private fun nudge(frames: Int): Boolean {
        if (workspace.stage != WorkspaceStage.EDIT) return false
        onEdit(if (edit.jogMode == EditUiState.JOG_PLAYHEAD) EditEvent.Nudge(frames) else EditEvent.TrimJog(frames))
        return true
    }

    fun handle(action: ShortcutAction?): Boolean {
        when (action ?: return false) {
            ShortcutAction.STAGE_MEDIA -> onWorkspace(WorkspaceEvent.SelectStage(WorkspaceStage.MEDIA))
            ShortcutAction.STAGE_EDIT -> onWorkspace(WorkspaceEvent.SelectStage(WorkspaceStage.EDIT))
            ShortcutAction.STAGE_COLOR -> onWorkspace(WorkspaceEvent.SelectStage(WorkspaceStage.COLOR))
            ShortcutAction.STAGE_AUDIO -> onWorkspace(WorkspaceEvent.SelectStage(WorkspaceStage.AUDIO))
            ShortcutAction.STAGE_DELIVER -> onWorkspace(WorkspaceEvent.SelectStage(WorkspaceStage.DELIVER))
            ShortcutAction.PLAY_PAUSE -> play(!playing())
            ShortcutAction.PLAY -> play(true)
            ShortcutAction.PAUSE -> play(false)
            ShortcutAction.STEP_BACK -> jog(-1)
            ShortcutAction.STEP_FORWARD -> jog(1)
            ShortcutAction.SECOND_BACK -> jog(-workspace.fps)
            ShortcutAction.SECOND_FORWARD -> jog(workspace.fps)
            // ↑ / ↓: the source's marks on the Media stage, Resolve's Previous / Next Clip on the Color stage, else the edit points.
            ShortcutAction.PREVIOUS_EDIT -> when {
                inMedia -> onMedia(MediaEvent.JumpToIn)
                workspace.stage == WorkspaceStage.COLOR -> onColor(ColorEvent.StepClip(forward = false))
                else -> onWorkspace(WorkspaceEvent.PreviousEdit)
            }
            ShortcutAction.NEXT_EDIT -> when {
                inMedia -> onMedia(MediaEvent.JumpToOut)
                workspace.stage == WorkspaceStage.COLOR -> onColor(ColorEvent.StepClip(forward = true))
                else -> onWorkspace(WorkspaceEvent.NextEdit)
            }
            ShortcutAction.GO_TO_START -> if (inMedia || sourceInEdit) jog(-FAR_FRAMES) else onWorkspace(WorkspaceEvent.Seek(0))
            ShortcutAction.GO_TO_END -> if (inMedia || sourceInEdit) jog(FAR_FRAMES) else onWorkspace(WorkspaceEvent.Seek(workspace.durationFrames))
            ShortcutAction.MARK_IN -> marks(MediaEvent.MarkIn, EditEvent.MarkIn, DeliverEvent.MarkIn) ?: return false
            ShortcutAction.MARK_OUT -> marks(MediaEvent.MarkOut, EditEvent.MarkOut, DeliverEvent.MarkOut) ?: return false
            ShortcutAction.CLEAR_MARKS -> marks(MediaEvent.ClearMarks, EditEvent.ClearMarks, DeliverEvent.ClearMarks) ?: return false
            ShortcutAction.MARK_CLIP -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.MarkClip) else return false
            // The program monitor's ranged playback, wherever it is the monitor (not the Media page's source).
            ShortcutAction.PLAY_AROUND -> if (programShown()) onWorkspace(WorkspaceEvent.PlayAround) else return false
            ShortcutAction.PLAY_IN_TO_OUT -> if (programShown()) onWorkspace(WorkspaceEvent.PlayInToOut) else return false
            ShortcutAction.TOGGLE_LOOP -> onWorkspace(WorkspaceEvent.SetLoop(!workspace.loop))
            ShortcutAction.APPEND -> sourceEdit(SourceEdit.APPEND) ?: return false
            ShortcutAction.INSERT_EDIT -> sourceEdit(SourceEdit.INSERT) ?: return false
            ShortcutAction.OVERWRITE_EDIT -> sourceEdit(SourceEdit.OVERWRITE) ?: return false
            ShortcutAction.RIPPLE_OVERWRITE_EDIT -> sourceEdit(SourceEdit.RIPPLE_OVERWRITE) ?: return false
            ShortcutAction.REPLACE_EDIT -> sourceEdit(SourceEdit.REPLACE) ?: return false
            ShortcutAction.FIT_TO_FILL_EDIT -> sourceEdit(SourceEdit.FIT_TO_FILL) ?: return false
            ShortcutAction.PLACE_ON_TOP -> sourceEdit(SourceEdit.PLACE_ON_TOP) ?: return false
            ShortcutAction.SPLIT -> onEdit(EditEvent.Split)
            // F: Resolve's Match Frame, on the Edit stage.
            ShortcutAction.MATCH_FRAME -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.MatchFrame()) else return false
            // Ctrl+T / Alt+T / Shift+T: Resolve's Add Transition, on the Edit stage.
            ShortcutAction.ADD_TRANSITION -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.AddTransitions(TransitionScope.BOTH)) else return false
            ShortcutAction.ADD_VIDEO_TRANSITION -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.AddTransitions(TransitionScope.VIDEO)) else return false
            ShortcutAction.ADD_AUDIO_TRANSITION -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.AddTransitions(TransitionScope.AUDIO)) else return false
            // Shift+[ / ] (Ctrl: ripple): Resolve's trims of the clips under the playhead to it, on the Edit stage.
            ShortcutAction.TRIM_START_TO_PLAYHEAD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.TrimToPlayhead(TrimEdge.START, ripple = false)) else return false
            ShortcutAction.TRIM_END_TO_PLAYHEAD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.TrimToPlayhead(TrimEdge.END, ripple = false)) else return false
            ShortcutAction.RIPPLE_START_TO_PLAYHEAD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.TrimToPlayhead(TrimEdge.START, ripple = true)) else return false
            ShortcutAction.RIPPLE_END_TO_PLAYHEAD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.TrimToPlayhead(TrimEdge.END, ripple = true)) else return false
            // Ctrl+Shift+, / .: Resolve's Swap Clips, on the Edit stage.
            ShortcutAction.SWAP_LEFT -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.SwapClip(right = false)) else return false
            ShortcutAction.SWAP_RIGHT -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.SwapClip(right = true)) else return false
            // Ctrl+D: Resolve's Change Clip Duration, on the Edit stage.
            // Ctrl/⌘+D: the clip's duration on the Edit stage, the selected serial node on or off on the Color stage (Resolve's).
            ShortcutAction.CLIP_DURATION -> when {
                workspace.stage == WorkspaceStage.EDIT -> onEdit(EditEvent.OpenClipDuration())
                workspace.stage == WorkspaceStage.COLOR && color.node.isSerial -> onColor(ColorEvent.ToggleNode(color.node))
                else -> return false
            }
            ShortcutAction.ADD_SERIAL_NODE -> if (workspace.stage == WorkspaceStage.COLOR && color.canAddNode) onColor(ColorEvent.AddSerialNode) else return false
            // = / + / −: Resolve's typed timecode (an offset with a sign), on the Edit stage.
            // On the Color stage = and − are Resolve's Apply Grade from One / Two Clips Prior.
            ShortcutAction.TIMECODE_ENTRY -> when (workspace.stage) {
                WorkspaceStage.EDIT -> onEdit(EditEvent.OpenTimecodeEntry())
                WorkspaceStage.COLOR -> onColor(ColorEvent.ApplyPriorGrade(1))
                else -> return false
            }
            ShortcutAction.TIMECODE_FORWARD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.OpenTimecodeEntry("+")) else return false
            ShortcutAction.TIMECODE_BACK -> when (workspace.stage) {
                WorkspaceStage.EDIT -> onEdit(EditEvent.OpenTimecodeEntry("-"))
                WorkspaceStage.COLOR -> onColor(ColorEvent.ApplyPriorGrade(2))
                else -> return false
            }
            ShortcutAction.ADD_MARKER -> onEdit(EditEvent.AddMarker)
            ShortcutAction.RIPPLE_DELETE -> onEdit(EditEvent.RippleDelete)
            ShortcutAction.DELETE -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.DeleteClip) else return false
            ShortcutAction.COPY -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.CopyClip) else return false
            ShortcutAction.CUT -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.CutClip) else return false
            ShortcutAction.RIPPLE_CUT -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.RippleCut) else return false
            ShortcutAction.PASTE -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.PasteClips) else return false
            ShortcutAction.PASTE_INSERT -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.PasteInsert) else return false
            ShortcutAction.PASTE_ATTRIBUTES -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.OpenPasteAttributes) else return false
            ShortcutAction.TOGGLE_CLIP_ENABLED -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.ToggleClipEnabled) else return false
            ShortcutAction.TOGGLE_LINK -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.ToggleLink) else return false
            ShortcutAction.TOGGLE_SNAPPING -> onEdit(EditEvent.SetSnapping(!edit.snapping))
            ShortcutAction.DESELECT -> onEdit(EditEvent.SelectClip(null))
            ShortcutAction.SELECT_ALL -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.SelectAll) else return false
            ShortcutAction.SELECT_FORWARD -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.SelectForward(null)) else return false
            ShortcutAction.UNDO -> onWorkspace(WorkspaceEvent.Undo)
            ShortcutAction.REDO -> onWorkspace(WorkspaceEvent.Redo)
            ShortcutAction.CLIP_TRACK_UP -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.MoveTrack(up = true)) else return false
            ShortcutAction.CLIP_TRACK_DOWN -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.MoveTrack(up = false)) else return false
            ShortcutAction.TOGGLE_COMPARE ->
                if (workspace.stage == WorkspaceStage.COLOR && color.hasClip) onColor(ColorEvent.SetCompare(!color.compare)) else return false
            ShortcutAction.AUTO_COLOR -> if (workspace.stage == WorkspaceStage.COLOR && color.hasClip) onColor(ColorEvent.AutoColor) else return false
            ShortcutAction.NEW_GRADE_VERSION -> if (workspace.stage == WorkspaceStage.COLOR) onColor(ColorEvent.NewGradeVersion) else return false
            ShortcutAction.PREVIOUS_GRADE_VERSION -> if (workspace.stage == WorkspaceStage.COLOR) onColor(ColorEvent.StepGradeVersion(-1)) else return false
            ShortcutAction.NEXT_GRADE_VERSION -> if (workspace.stage == WorkspaceStage.COLOR) onColor(ColorEvent.StepGradeVersion(1)) else return false
            ShortcutAction.PREVIOUS_MARKER -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.PreviousMarker) else return false
            ShortcutAction.NEXT_MARKER -> if (workspace.stage == WorkspaceStage.EDIT) onEdit(EditEvent.NextMarker) else return false
            ShortcutAction.TRIM_MODE -> if (workspace.stage == WorkspaceStage.EDIT) {
                onEdit(EditEvent.JogMode((edit.jogMode + 1) % EditUiState.JOG_MODES.size))
            } else {
                return false
            }
            ShortcutAction.NUDGE_BACK -> return nudge(-1)
            ShortcutAction.NUDGE_FORWARD -> return nudge(1)
            ShortcutAction.NUDGE_BACK_FAST -> return nudge(-NUDGE_FAST_FRAMES)
            ShortcutAction.NUDGE_FORWARD_FAST -> return nudge(NUDGE_FAST_FRAMES)
        }
        return true
    }

    private fun playing() = when {
        inMedia -> media.playing
        sourceInEdit -> edit.sourcePlaying
        else -> workspace.playing
    }

    private fun play(on: Boolean) = when {
        inMedia -> onMedia(MediaEvent.SetPlaying(on))
        sourceInEdit -> onEdit(EditEvent.SourcePlaying(on))
        else -> onWorkspace(WorkspaceEvent.SetPlaying(on))
    }

    private fun jog(frames: Int) = when {
        inMedia -> onMedia(MediaEvent.Jog(frames))
        sourceInEdit -> onEdit(EditEvent.SourceJog(frames))
        else -> onWorkspace(WorkspaceEvent.Jog(frames))
    }
}

/** Far enough to reach either end of any clip; seeks clamp to the media. */
private const val FAR_FRAMES = 1_000_000

/** Frames a Shift+, / Shift+. nudge moves. */
private const val NUDGE_FAST_FRAMES = 5
