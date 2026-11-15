package com.example.prismstudio.p40b43a88.ui.edit

import com.example.prismstudio.p40b43a88.ui.edit.components.PlaybackAction
import com.example.prismstudio.p40b43a88.domain.model.WorkspaceLayout
import com.example.prismstudio.p40b43a88.ui.edit.components.TimecodeEntrySheet
import com.example.prismstudio.p40b43a88.ui.edit.components.ClipDurationSheet
import com.example.prismstudio.p40b43a88.ui.edit.components.RenameSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.ui.audio.components.EqSheet
import com.example.prismstudio.p40b43a88.ui.components.TimelineActions
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import com.example.prismstudio.p40b43a88.ui.edit.components.AssetLibrary
import com.example.prismstudio.p40b43a88.ui.edit.components.ClipMenu
import com.example.prismstudio.p40b43a88.ui.edit.components.PasteAttributesSheet
import com.example.prismstudio.p40b43a88.ui.edit.components.CompoundReturnChip
import com.example.prismstudio.p40b43a88.ui.edit.components.TimelineMenu
import com.example.prismstudio.p40b43a88.ui.edit.components.TimelineSettingsSheet
import com.example.prismstudio.p40b43a88.ui.edit.components.InspectorPanel
import com.example.prismstudio.p40b43a88.ui.edit.components.TimelinePanel
import com.example.prismstudio.p40b43a88.ui.edit.components.ViewerColumn

/** The Edit stage page (state wiring and toasts). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditScreen(
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    useProxies: Boolean = false,
    proxyNote: String = "",
    onPlayback: ((PlaybackAction) -> Unit)? = null,
) {
    val context = LocalContext.current
    val model: EditViewModel = viewModel(factory = remember(context) { EditViewModelFactory(context) })
    val state by model.state.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { model.onEvent(EditEvent.ToastShown) }
    EditContent(state, model::onEvent, modifier, loop, onPlayback, useProxies, proxyNote)
}

@Composable
fun EditContent(
    state: EditUiState,
    onEvent: (EditEvent) -> Unit,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    onPlayback: ((PlaybackAction) -> Unit)? = null,
    useProxies: Boolean = false,
    proxyNote: String = "",
) {
    val dispatch by rememberUpdatedState(onEvent)
    // The system document picker for 导入字幕; a cancelled pick changes nothing.
    val edlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dispatch(EditEvent.ImportEdl(uri.toString()))
    }
    val fcpxmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dispatch(EditEvent.ImportFcpxml(uri.toString()))
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dispatch(EditEvent.ImportSubtitles(uri.toString()))
    }
    val actions = remember {
        object : TimelineActions {
            override fun seek(frame: Int) = dispatch(EditEvent.Seek(frame))
            override fun select(clipId: String?) = dispatch(EditEvent.SelectClip(clipId))
            override fun toggle(clipId: String) = dispatch(EditEvent.ToggleSelected(clipId))
            override fun marquee(from: Int, until: Int, trackIds: Set<String>) =
                dispatch(EditEvent.SelectInMarquee(from, until, trackIds))
            override fun move(clipId: String, start: Int, trackId: String, commit: Boolean) =
                dispatch(EditEvent.Move(clipId, start, trackId, commit))
            override fun trim(clipId: String, edge: TrimEdge, frame: Int, commit: Boolean) =
                dispatch(EditEvent.Trim(clipId, edge, frame, commit))
            override fun volume(clipId: String, keyFrames: List<Int>, deltaDb: Float, commit: Boolean) =
                dispatch(EditEvent.VolumeLine(clipId, keyFrames, deltaDb, commit))
            override fun transition(clipId: String, frames: Int, commit: Boolean) =
                dispatch(EditEvent.TransitionDrag(clipId, frames, commit))
            override fun fade(clipId: String, fadeIn: Boolean, seconds: Float, commit: Boolean) =
                dispatch(EditEvent.FadeDrag(clipId, fadeIn, seconds, commit))
            override fun fadeCurve(clipId: String, fadeIn: Boolean, curve: Float, commit: Boolean) =
                dispatch(EditEvent.FadeCurveDrag(clipId, fadeIn, curve, commit))
            override fun marker(markerId: String, frame: Int, end: Boolean, commit: Boolean) =
                dispatch(EditEvent.MarkerDrag(markerId, frame, end, commit))
            override fun pickMarker(markerId: String) = dispatch(EditEvent.SelectMarker(markerId))
            override fun selectGap(trackId: String, frame: Int) = dispatch(EditEvent.SelectGap(trackId, frame))
            override fun cancelDrag() = dispatch(EditEvent.CancelDrag)
            override fun viewport(widthDp: Float) = dispatch(EditEvent.Viewport(widthDp))
        }
    }
    state.timecodeEntry?.let { ui ->
        TimecodeEntrySheet(
            ui = ui,
            onText = { onEvent(EditEvent.TimecodeEntryText(it)) },
            onConfirm = { onEvent(EditEvent.ConfirmTimecodeEntry) },
            onClose = { onEvent(EditEvent.CloseTimecodeEntry) },
        )
    }
    state.clipDuration?.let { ui ->
        ClipDurationSheet(
            ui = ui,
            onText = { onEvent(EditEvent.ClipDurationText(it)) },
            onRipple = { onEvent(EditEvent.ClipDurationRipple(it)) },
            onConfirm = { onEvent(EditEvent.ConfirmClipDuration) },
            onClose = { onEvent(EditEvent.CloseClipDuration) },
        )
    }
    state.rename?.let { ui ->
        RenameSheet(
            ui = ui,
            onText = { onEvent(EditEvent.RenameText(it)) },
            onConfirm = { onEvent(EditEvent.ConfirmRename) },
            onClose = { onEvent(EditEvent.CloseRename) },
        )
    }
    state.timelineSettings?.let { ui ->
        TimelineSettingsSheet(
            ui = ui,
            onName = { onEvent(EditEvent.TimelineSettingsName(it)) },
            onFormat = { onEvent(EditEvent.TimelineSettingsFormat(it)) },
            onScaling = { onEvent(EditEvent.TimelineSettingsScaling(it)) },
            onApply = { onEvent(EditEvent.ApplyTimelineSettings) },
            onClose = { onEvent(EditEvent.CloseTimelineSettings) },
        )
    }
    // The selected sound's own equaliser, in the sheet the Audio page edits a track's in.
    state.clipEq?.let { editor ->
        EqSheet(
            editor = editor,
            onClose = { onEvent(EditEvent.CloseClipEq) },
            onEnabled = { onEvent(EditEvent.ClipEqEnabled(it)) },
            onLowCut = { onEvent(EditEvent.ClipEqLowCut(it)) },
            onReset = { onEvent(EditEvent.ResetClipEq) },
            onPreset = { onEvent(EditEvent.ClipEqPreset(it)) },
            onChange = { control, value -> onEvent(EditEvent.ClipEq(control, value)) },
            onDrag = { band, hz, db -> onEvent(EditEvent.ClipEqDrag(band, hz, db)) },
        )
    }
    state.pasteAttributes?.let { ui ->
        PasteAttributesSheet(
            ui = ui,
            onToggle = { attribute, on -> onEvent(EditEvent.PasteAttribute(attribute, on)) },
            onPaste = { onEvent(EditEvent.ConfirmPasteAttributes) },
            onClose = { onEvent(EditEvent.ClosePasteAttributes) },
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 1440.dp
        val dual = WorkspaceLayout.dualViewer(maxWidth.value)
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.weight(1f).fillMaxWidth().heightIn(min = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (!compact) {
                    AssetLibrary(
                        tab = state.libraryTab,
                        items = state.libraryItems,
                        selectedId = state.libraryItemId,
                        actionLabel = state.libraryActionLabel,
                        actionEnabled = state.libraryActionEnabled,
                        onTab = { onEvent(EditEvent.SelectLibraryTab(it)) },
                        onSelect = { onEvent(EditEvent.SelectLibraryItem(it)) },
                        onApply = { onEvent(EditEvent.ApplyLibraryItem) },
                        onNeedPreview = { onEvent(EditEvent.RequestLibraryPreview(it)) },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        markers = state.markers,
                        selectedMarker = state.selectedMarker,
                        onSelectMarker = { onEvent(EditEvent.SelectMarker(it)) },
                        onRenameMarker = { onEvent(EditEvent.RenameMarker(it)) },
                        onRecolorMarker = { onEvent(EditEvent.RecolorMarker(it)) },
                        onNoteMarker = { onEvent(EditEvent.NoteMarker(it)) },
                        onDeleteMarker = { onEvent(EditEvent.DeleteMarker) },
                        onMarkerDuration = { onEvent(EditEvent.MarkerDuration(it)) },
                        onMarkerRange = { fromMarks -> onEvent(if (fromMarks) EditEvent.MarkerRangeFromMarks else EditEvent.MarksFromMarkerRange) },
                        indexView = state.indexView,
                        onIndexView = { onEvent(EditEvent.IndexView(it)) },
                        editIndex = state.editIndex,
                        editIndexSummary = state.editIndexSummary,
                        indexFilter = state.indexFilter,
                        onIndexFilter = { onEvent(EditEvent.IndexFilter(it)) },
                        onPickIndexRow = { onEvent(EditEvent.PickIndexRow(it)) },
                    )
                }
                ViewerColumn(
                    state = state,
                    onMode = { onEvent(EditEvent.ViewerMode(it)) },
                    onProgramSurface = { onEvent(EditEvent.ProgramSurface(it)) },
                    onSourceSurface = { onEvent(EditEvent.SourceSurface(it)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onPickKey = { u, v -> onEvent(EditEvent.PickKeyColor(u, v)) },
                    onMoveMask = { dx, dy -> onEvent(EditEvent.MoveMask(dx, dy)) },
                    onMatchFrame = { onEvent(EditEvent.MatchFrame()) },
                    onTimecode = { onEvent(EditEvent.OpenTimecodeEntry()) },
                    onClearSourceMarks = { onEvent(EditEvent.ClearSourceMarks) },
                    loop = loop,
                    onPlayback = onPlayback,
                    useProxies = useProxies,
                    proxyNote = proxyNote,
                    dual = dual,
                )
                InspectorPanel(
                    tab = state.inspectorTab,
                    inspector = state.inspector,
                    onTab = { onEvent(EditEvent.InspectorTab(it)) },
                    onEvent = onEvent,
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    selection = state.selection,
                    canPaste = state.canPaste,
                )
            }
            TimelinePanel(
                timeline = state.timeline,
                playheadFrame = state.playheadFrame,
                summary = state.summary,
                playing = state.playing,
                actions = actions,
                canAddVideoTrack = state.canAddVideoTrack,
                canAddAudioTrack = state.canAddAudioTrack,
                onZoom = { onEvent(EditEvent.Zoom(it)) },
                onFit = { onEvent(EditEvent.FitTimeline) },
                onTrackEnabled = { id, enabled -> onEvent(EditEvent.TrackEnabled(id, enabled)) },
                onTrackLocked = { id, locked -> onEvent(EditEvent.TrackLocked(id, locked)) },
                onAddTrack = { onEvent(EditEvent.AddTrack(it)) },
                onRemoveTrack = { onEvent(EditEvent.RemoveTrack(it)) },
                onSubtitleLanguage = { id, lang -> onEvent(EditEvent.SubtitleLanguage(id, lang)) },
                onAddSubtitleTrack = { onEvent(EditEvent.AddTrack(com.example.prismstudio.p40b43a88.domain.model.TrackKind.SUBTITLE)) },
                onImportSubtitles = { subtitlePicker.launch(SUBTITLE_TYPES) },
                onTimelineSettings = { onEvent(EditEvent.OpenTimelineSettings) },
                timelineMenu = {
                    CompoundReturnChip(state.compoundTrail, onEvent)
                    TimelineMenu(state.timelineName, state.timelineEntries, onEvent, onImportEdl = { edlPicker.launch(EDL_TYPES) }, onImportFcpxml = { fcpxmlPicker.launch(FCPXML_TYPES) })
                },
                multiSelect = state.multiSelect,
                onMultiSelect = { onEvent(EditEvent.MultiSelect(it)) },
                curves = state.curves,
                onCurveMode = { onEvent(EditEvent.CurveMode(it)) },
                onCurve = onEvent,
                onTarget = { onEvent(EditEvent.ToggleTarget(it)) },
                onRenameTrack = { onEvent(EditEvent.OpenRenameTrack(it)) },
                hasMarks = state.hasMarks,
                onClearMarks = { onEvent(EditEvent.ClearMarks) },
                hasRange = state.hasRange,
                onLiftRange = { onEvent(EditEvent.LiftRange) },
                onExtractRange = { onEvent(EditEvent.ExtractRange) },
                modifier = Modifier.fillMaxWidth().height(368.dp),
                clipMenu = { clipId, dismiss ->
                    ClipMenu(
                        onClip = clipId != null,
                        canPaste = state.canPaste,
                        clipEnabled = state.selection?.let { !it.allDisabled }
                            ?: state.timeline.clips.firstOrNull { it.id == clipId }?.enabled ?: true,
                        onEvent = onEvent,
                        onDismiss = dismiss,
                        clipId = clipId,
                        gapText = state.gapText,
                        selected = state.selection?.count ?: 1,
                        clipColor = state.timeline.clips.firstOrNull { it.id == clipId }?.color,
                        link = state.linkAction,
                        // Titles have no media to flag.
                        clipFlags = state.timeline.clips.firstOrNull { it.id == clipId }?.takeIf { it.assetId != null }?.let(state.timeline::flagsOf),
                        canDetectCuts = clipId in state.footageClipIds && !state.sceneCutting,
                        compound = state.timeline.clips.firstOrNull { it.id == clipId }?.isCompound == true,
                        multicam = clipId in state.timeline.multicamClipIds,
                    )
                },
            )
        }
    }
}

/** SubRip files; some pickers label them as plain text. */
/** SubRip, WebVTT and SubStation Alpha files — the last two often come without a type of their own. */
/** EDLs come as .edl files of no registered type, or as text. */
private val EDL_TYPES = arrayOf("application/octet-stream", "text/*", "application/x-edl")
/** FCPXML files come as XML, as plain bytes (how Prism saves them), or untyped. */
private val FCPXML_TYPES = arrayOf("application/octet-stream", "application/xml", "text/xml", "text/*")

private val SUBTITLE_TYPES = arrayOf("application/x-subrip", "text/*", "application/x-ssa", "application/octet-stream")
