package com.example.prismstudio.p40b43a88.ui.color

import com.example.prismstudio.p40b43a88.ui.color.StripMenuUi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.example.prismstudio.p40b43a88.ui.color.components.SerialNodeActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.ui.color.components.SpatialNrPanel
import com.example.prismstudio.p40b43a88.ui.color.components.ClipStripPanel
import com.example.prismstudio.p40b43a88.ui.color.components.ColorMiniTimeline
import com.example.prismstudio.p40b43a88.ui.color.components.CurvesPanel
import com.example.prismstudio.p40b43a88.ui.color.components.GalleryPanel
import com.example.prismstudio.p40b43a88.ui.color.components.RgbMixerPanel
import com.example.prismstudio.p40b43a88.ui.color.components.ColorWarperPanel
import com.example.prismstudio.p40b43a88.ui.color.components.LooksPanel
import com.example.prismstudio.p40b43a88.ui.color.components.NodeKeyframes
import com.example.prismstudio.p40b43a88.ui.color.components.NodeStrip
import com.example.prismstudio.p40b43a88.ui.color.components.PickOverlay
import com.example.prismstudio.p40b43a88.ui.color.components.PrimariesPanel
import com.example.prismstudio.p40b43a88.ui.color.components.QualifierPanel
import com.example.prismstudio.p40b43a88.ui.color.components.RemoveLutDialog
import com.example.prismstudio.p40b43a88.ui.color.components.ScopesPanel
import com.example.prismstudio.p40b43a88.ui.color.components.WheelsRow
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlay
import com.example.prismstudio.p40b43a88.ui.components.TrackUi
import com.example.prismstudio.p40b43a88.ui.color.components.WindowPanel
import com.example.prismstudio.p40b43a88.ui.components.APPROXIMATE_PREVIEW_NOTE
import com.example.prismstudio.p40b43a88.ui.components.KeyframeAction
import com.example.prismstudio.p40b43a88.ui.components.MonitorContent
import com.example.prismstudio.p40b43a88.ui.components.MonitorView
import com.example.prismstudio.p40b43a88.ui.components.rememberFrameImage
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.labelWeight
import com.example.prismstudio.p40b43a88.ui.components.Text

/** The Color stage page (state wiring and toasts). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ColorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model: ColorViewModel = viewModel(factory = remember(context) { ColorViewModelFactory(context) })
    val state by model.state.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { model.onEvent(ColorEvent.ToastShown) }
    // The before/after split belongs to the Color stage; the program monitor is shared with the others.
    DisposableEffect(model) {
        onDispose {
            model.onEvent(ColorEvent.SetCompare(false))
            model.onEvent(ColorEvent.SetWipe(false))
            model.onEvent(ColorEvent.SetHighlight(false))
            model.onEvent(ColorEvent.SetEyedropper(false))
        }
    }
    // `.cube` has no registered type: any file can be picked, and the import says what is wrong with it.
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.onEvent(ColorEvent.ImportLut(uri.toString()))
    }
    ColorContent(state, model::onEvent, modifier, onImportLut = { lutPicker.launch(arrayOf("*/*")) })
}

@Composable
fun ColorContent(
    state: ColorUiState,
    onEvent: (ColorEvent) -> Unit,
    modifier: Modifier = Modifier,
    onImportLut: () -> Unit = {},
) {
    state.lutRemoval?.let { lut ->
        RemoveLutDialog(lut.name, onConfirm = { onEvent(ColorEvent.ConfirmRemoveLut) }, onDismiss = { onEvent(ColorEvent.CancelRemoveLut) })
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 1440.dp
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.weight(1f).fillMaxWidth().heightIn(min = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                MonitorView(
                    content = MonitorContent(
                        pictureAspect = state.pictureAspect,
                        video = state.clips.isNotEmpty(),
                        message = state.programMessage,
                        note = if (state.previewApproximate) APPROXIMATE_PREVIEW_NOTE else null,
                        layers = state.programLayers,
                        composites = state.programComposites,
                    ),
                    onSurface = { onEvent(ColorEvent.ProgramSurface(it)) },
                    // Before/after, or the Gallery wipe: the picked still left of the split.
                    compareSplit = if (state.compare || (state.wipe && state.wipeImage != null)) 0.5f else null,
                    beforeLabel = if (state.wipe) state.wipeLabel else "调色前",
                    afterLabel = if (state.wipe) "当前画面" else "调色后",
                    wipe = if (state.wipe) rememberFrameImage(state.wipeImage) else null,
                    modifier = Modifier.fillMaxHeight(),
                    pictureOverlay = {
                        state.windowOverlay?.let { overlay ->
                            WindowOverlay(overlay, state.pictureAspect) { dx, dy -> onEvent(ColorEvent.MoveWindow(dx, dy)) }
                        }
                        // The eyedropper sits on top: while it is on, a tap picks rather than moves the window.
                        state.pickOverlay?.let { overlay ->
                            PickOverlay(overlay, state.pictureAspect) { u, v -> onEvent(ColorEvent.PickColor(u, v)) }
                        }
                    },
                )
                ScopesPanel(state.scopeMode, state.scope, { onEvent(ColorEvent.Scope(it)) }, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ClipStripPanel(
                    clips = state.stripClips,
                    selectedId = state.selectedClipId,
                    filterLabel = state.clipFilter.label,
                    filtered = state.clipFiltered,
                    countLabel = state.clipCountLabel,
                    filterOptions = state.clipFilterOptions,
                    colorFilters = state.clipColorFilters,
                    flagFilters = state.clipFlagFilters,
                    message = state.stripMessage,
                    onSelect = { onEvent(ColorEvent.SelectClip(it)) },
                    onFilter = { onEvent(ColorEvent.SetClipFilter(it)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    menu = StripMenuUi(canPaste = !state.clipboardEmpty, versions = state.gradeVersions, version = state.gradeVersion),
                    onMenu = onEvent,
                )
                NodeStrip(
                    node = state.node,
                    primaryGraded = state.primaryGraded,
                    window = state.window,
                    secondaryLabel = state.secondaryLabel,
                    enabled = state.hasClip,
                    onSelect = { onEvent(ColorEvent.SelectNode(it)) },
                    modifier = Modifier.widthIn(max = NODE_STRIP_MAX).fillMaxHeight().padding(vertical = 4.dp),
                    primaryKeyed = state.gradeKeys.count > 0,
                    serialNodes = state.serialNodes,
                    canAdd = state.canAddNode,
                    onAdd = { onEvent(ColorEvent.AddSerialNode) },
                )
                // The selected node's keyframes: node 1's correction dissolving, or node 2's window moving; a serial node
                // (03 on) has none — its switch and delete take their place.
                val windowNode = state.node == GradeNode.WINDOW
                val serialNode = state.serialNodes.firstOrNull { it.node == state.node }
                if (serialNode != null) SerialNodeActions(
                    ui = serialNode,
                    enabled = state.hasClip,
                    onToggle = { onEvent(ColorEvent.ToggleNode(serialNode.node)) },
                    onDelete = { onEvent(ColorEvent.DeleteNode(serialNode.node)) },
                    modifier = Modifier.width(360.dp).fillMaxHeight().padding(vertical = 4.dp),
                ) else NodeKeyframes(
                    title = if (windowNode) "节点 02 关键帧 · 窗口位置" else "节点 01 关键帧 · 调色",
                    keys = if (windowNode) state.windowKeys else state.gradeKeys,
                    enabled = state.hasClip && (!windowNode || state.window?.shape != WindowShape.FULL),
                    onAction = { action ->
                        onEvent(
                            when (action) {
                                KeyframeAction.PREVIOUS -> if (windowNode) ColorEvent.PreviousWindowKeyframe else ColorEvent.PreviousGradeKeyframe
                                KeyframeAction.TOGGLE -> if (windowNode) ColorEvent.ToggleWindowKeyframe else ColorEvent.ToggleGradeKeyframe
                                KeyframeAction.NEXT -> if (windowNode) ColorEvent.NextWindowKeyframe else ColorEvent.NextGradeKeyframe
                                KeyframeAction.EASE_IN -> if (windowNode) ColorEvent.ToggleWindowEase(true) else ColorEvent.ToggleGradeEase(true)
                                KeyframeAction.EASE_OUT -> if (windowNode) ColorEvent.ToggleWindowEase(false) else ColorEvent.ToggleGradeEase(false)
                            },
                        )
                    },
                    modifier = Modifier.width(360.dp).fillMaxHeight().padding(vertical = 4.dp),
                )
            }
            // Resolve's mini timeline under the thumbnails: the whole program, every picture track, the playhead.
            ColorMiniTimeline(
                ui = state.miniTimeline,
                playheadFrame = state.playheadFrame,
                fps = state.fps,
                onScrub = { frame, clipId -> onEvent(ColorEvent.Scrub(frame, clipId)) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
            Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                WheelsRow(
                    grade = state.grade,
                    readouts = state.readouts,
                    showOffset = !compact,
                    enabled = state.hasClip,
                    onBalance = { w, x, y -> onEvent(ColorEvent.WheelBalance(w, x, y)) },
                    onMaster = { w, v -> onEvent(ColorEvent.WheelMaster(w, v)) },
                )
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SegmentControl(Modifier.fillMaxWidth()) {
                        // 细节 (rev 128, was 降噪): the spatial treatments — Spatial NR and Mid/Detail.
                        // 混合器 (rev 145): Resolve's RGB Mixer.
                        // 扭曲器 (rev 149): Resolve's Color Warper.
                        listOf("一级校色", "曲线", "LUT 风格", "窗口", "限定器", "画廊", "细节", "混合器", "扭曲器").forEachIndexed { index, label ->
                            SegmentItem(
                                selected = index == state.adjustTab,
                                onClick = { onEvent(ColorEvent.AdjustTab(index)) },
                                title = { Text(label) },
                                modifier = Modifier.weight(labelWeight(label)),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            )
                        }
                    }
                    when (state.adjustTab) {
                        ColorUiState.TAB_PRIMARIES -> PrimariesPanel(state.grade, state.hasClip, { c, v -> onEvent(ColorEvent.Primary(c, v)) }, Modifier.fillMaxWidth())
                        ColorUiState.TAB_NOISE -> SpatialNrPanel(
                            nr = state.spatialNr,
                            enabled = state.hasClip,
                            onChange = { c, v -> onEvent(ColorEvent.SpatialNr(c, v)) },
                            onRadius = { onEvent(ColorEvent.SpatialNrRadius(it)) },
                            onReset = { onEvent(ColorEvent.ResetSpatialNr) },
                            midDetail = state.midDetail,
                            onMidDetail = { onEvent(ColorEvent.MidDetail(it)) },
                            temporalNr = state.temporalNr,
                            onTemporalNr = { onEvent(ColorEvent.TemporalNr(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ColorUiState.TAB_CURVES -> CurvesPanel(
                            curves = state.grade.curves,
                            channel = state.curveChannel,
                            hueCurves = state.grade.hueCurves,
                            hueCurve = state.hueCurve,
                            enabled = state.hasClip,
                            onPick = { onEvent(ColorEvent.PickCurve(it)) },
                            onReset = { onEvent(ColorEvent.ResetCurve) },
                            onAdd = { x, y -> onEvent(ColorEvent.CurveAdd(x, y)) },
                            onMove = { i, x, y -> onEvent(ColorEvent.CurveMove(i, x, y)) },
                            onRemove = { onEvent(ColorEvent.CurveRemove(it)) },
                            onHueAdd = { onEvent(ColorEvent.HueCurveAdd(it)) },
                            onHueMove = { i, x, y -> onEvent(ColorEvent.HueCurveMove(i, x, y)) },
                            onHueRemove = { onEvent(ColorEvent.HueCurveRemove(it)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        ColorUiState.TAB_LOOKS -> LooksPanel(
                            looks = state.looks,
                            selected = state.grade.look,
                            selectedLut = state.grade.lut?.id,
                            strength = state.grade.lookStrength,
                            enabled = state.hasClip,
                            importing = state.lutImporting,
                            missingLut = state.lutMissing,
                            onSelect = { item -> onEvent(item.lut?.let { ColorEvent.SelectLut(it.id) } ?: ColorEvent.SelectLook(item.look)) },
                            onStrength = { onEvent(ColorEvent.LookStrength(it)) },
                            onImport = onImportLut,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            generating = state.lutGenerating,
                            onGenerate = { onEvent(ColorEvent.GenerateLut) },
                            onRemove = { onEvent(ColorEvent.RequestRemoveLut(it)) },
                        )
                        ColorUiState.TAB_WINDOW -> WindowPanel(
                            window = state.window,
                            keys = state.windowKeys,
                            track = TrackUi(state.trackUnavailable, state.tracking, state.trackStatus, state.trackMotion),
                            onTrack = { onEvent(ColorEvent.TrackWindow) },
                            onTrackMotion = { onEvent(ColorEvent.SetTrackMotion(it)) },
                            onCancelTrack = { onEvent(ColorEvent.CancelTrack) },
                            enabled = state.hasClip,
                            onShape = { onEvent(ColorEvent.SetWindowShape(it)) },
                            onInvert = { onEvent(ColorEvent.SetWindowInvert(it)) },
                            onChange = { c, v -> onEvent(ColorEvent.Window(c, v)) },
                            onKeyframe = { action ->
                                onEvent(
                                    when (action) {
                                        KeyframeAction.PREVIOUS -> ColorEvent.PreviousWindowKeyframe
                                        KeyframeAction.TOGGLE -> ColorEvent.ToggleWindowKeyframe
                                        KeyframeAction.NEXT -> ColorEvent.NextWindowKeyframe
                                        KeyframeAction.EASE_IN -> ColorEvent.ToggleWindowEase(arriving = true)
                                        KeyframeAction.EASE_OUT -> ColorEvent.ToggleWindowEase(arriving = false)
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ColorUiState.TAB_WARPER -> ColorWarperPanel(
                            warper = state.grade.warper,
                            spoke = state.warperSpoke,
                            ring = state.warperRing,
                            enabled = state.hasClip,
                            onSelect = { s, r -> onEvent(ColorEvent.WarperSelect(s, r)) },
                            onMove = { s, r, h, v -> onEvent(ColorEvent.WarperMove(s, r, h, v)) },
                            onResetPoint = { onEvent(ColorEvent.ResetWarperPoint) },
                            onResetAll = { onEvent(ColorEvent.ResetWarper) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        ColorUiState.TAB_MIXER -> RgbMixerPanel(
                            mixer = state.grade.rgbMixer,
                            enabled = state.hasClip,
                            onValue = { o, i, v -> onEvent(ColorEvent.MixerValue(o, i, v)) },
                            onPreserve = { onEvent(ColorEvent.MixerPreserve(it)) },
                            onMonochrome = { onEvent(ColorEvent.MixerMonochrome(it)) },
                            onSwap = { onEvent(ColorEvent.MixerSwapChannels(it)) },
                            onReset = { onEvent(ColorEvent.ResetMixer) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ColorUiState.TAB_GALLERY -> GalleryPanel(
                            stills = state.stills,
                            selectedStillId = state.selectedStillId,
                            wipe = state.wipe,
                            grabbing = state.grabbing,
                            hasClip = state.hasClip,
                            onGrab = { onEvent(ColorEvent.GrabStill) },
                            onSelect = { onEvent(ColorEvent.SelectStill(it)) },
                            onApply = { onEvent(ColorEvent.ApplyStill) },
                            matching = state.matching,
                            onMatch = { onEvent(ColorEvent.MatchStill) },
                            onWipe = { onEvent(ColorEvent.SetWipe(it)) },
                            onDelete = { onEvent(ColorEvent.DeleteStill(it)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        else -> QualifierPanel(
                            qualifier = state.window?.qualifier,
                            eyedropper = state.pickMode.takeIf { state.eyedropper },
                            highlight = state.highlight,
                            enabled = state.hasClip,
                            onEyedropper = { onEvent(ColorEvent.SetEyedropper(it != null, it ?: state.pickMode)) },
                            onHighlight = { onEvent(ColorEvent.SetHighlight(it)) },
                            onInvert = { onEvent(ColorEvent.SetQualifierInvert(it)) },
                            onRemove = { onEvent(ColorEvent.RemoveQualifier) },
                            onChange = { c, v -> onEvent(ColorEvent.Qualifier(c, v)) },
                            modifier = Modifier.fillMaxWidth(),
                            finesse = state.qualifierFinesse,
                            onPage = { onEvent(ColorEvent.QualifierPage(it)) },
                            feather = state.featherPick,
                            onFeather = { onEvent(ColorEvent.SetFeatherPick(it)) },
                        )
                    }
                }
            }
        }
    }
}

/** The node strip's widest: 01, 02, three serial nodes and ＋ in view; more scroll. */
private val NODE_STRIP_MAX = 680.dp
