package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlay
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Button
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.prismstudio.p40b43a88.ui.components.APPROXIMATE_PREVIEW_NOTE
import com.example.prismstudio.p40b43a88.ui.components.MonitorContent
import com.example.prismstudio.p40b43a88.ui.components.MonitorView
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange
import com.example.prismstudio.p40b43a88.ui.color.components.PickOverlay
import com.example.prismstudio.p40b43a88.ui.edit.EditUiState
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text

/** `edit-viewer-column`: source / timeline switch, timecode and the viewer at reference size. */
@Composable
fun ViewerColumn(
    state: EditUiState,
    onMode: (Int) -> Unit,
    onProgramSurface: (SurfaceChange) -> Unit,
    onSourceSurface: (SurfaceChange) -> Unit,
    modifier: Modifier = Modifier,
    /** A tap on the program monitor while the keyer's eyedropper is on: the point of the selected clip's picture (u, v). */
    onPickKey: (Float, Float) -> Unit = { _, _ -> },
    /** `mask-overlay` dragged: the selected clip's mask moved by (dx, dy) window units. */
    onMoveMask: (Float, Float) -> Unit = { _, _ -> },
    /** `match-frame-button`: Resolve's Match Frame. */
    onMatchFrame: () -> Unit = {},
    /** `program-timecode`: a tap types a timecode (Resolve). */
    onTimecode: () -> Unit = {},
    /** `source-marks-row`'s 清除. */
    onClearSourceMarks: () -> Unit = {},
    /** `playback-menu-button`: the program monitor's ranged playback and Loop (shown with the timeline viewer). */
    loop: Boolean = false,
    onPlayback: ((PlaybackAction) -> Unit)? = null,
    /** The playback menu's 使用代理播放 (Resolve's Proxy Handling) and its note. */
    useProxies: Boolean = false,
    proxyNote: String = "",
    /** Resolve's dual viewer (the ultrawide window): the source and the timeline side by side. */
    dual: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SegmentControl(Modifier.width(240.dp)) {
                listOf("源片段", "时间线").forEachIndexed { index, label ->
                    SegmentItem(
                        selected = index == state.viewerMode,
                        onClick = { onMode(index) },
                        title = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onPlayback != null && state.viewerMode != EditUiState.VIEWER_SOURCE) {
                    PlaybackMenu(loop = loop, hasRange = state.hasRange, enabled = !state.programEmpty, onAction = onPlayback, useProxies = useProxies, proxyNote = proxyNote)
                }
                // `match-frame-button`: Resolve's Match Frame (F) — the clip under the playhead in the source viewer.
                Button(
                    onClick = onMatchFrame,
                    enabled = !state.programEmpty,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                    modifier = Modifier.semantics { contentDescription = "匹配帧".localized() },
                ) { Text("匹配帧") }
                Text(
                    state.programTimecode,
                    style = PrismType.timecode,
                    color = PicoTheme.colorScheme.labelPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .spatialHoverEffect()
                        .clickable(onClick = onTimecode)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics { contentDescription = "时间码 ${state.programTimecode}，点按输入".localized() },
                )
            }
        }
        if (dual) {
            // Resolve's dual viewer: the source on the left, the timeline on the right; the switch above picks which one the
            // transport and keys drive.
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ViewerCaption("源片段", state.sourceName ?: "未选择", state.viewerMode == EditUiState.VIEWER_SOURCE)
                    SourceMonitor(state, onSourceSurface, onClearSourceMarks, Modifier.weight(1f, fill = false))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ViewerCaption("时间线", state.timelineName, state.viewerMode != EditUiState.VIEWER_SOURCE)
                    ProgramMonitor(state, onProgramSurface, onPickKey, onMoveMask, Modifier.weight(1f, fill = false))
                }
            }
        } else if (state.viewerMode == EditUiState.VIEWER_SOURCE) {
            SourceMonitor(state, onSourceSurface, onClearSourceMarks, Modifier.weight(1f, fill = false))
        } else {
            ProgramMonitor(state, onProgramSurface, onPickKey, onMoveMask, Modifier.weight(1f, fill = false))
        }
    }
}

/** A dual viewer's title: which viewer it is, what it shows, and whether the transport and keys drive it. */
@Composable
private fun ViewerCaption(title: String, subject: String, active: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = PrismType.label, color = if (active) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelSecondary)
        Text(subject, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (active) Text("播放控制", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
    }
}

/** The source viewer and, under it, `source-marks-row`: the source's marks (I / O), which the edit buttons put on the timeline. */
@Composable
private fun SourceMonitor(state: EditUiState, onSourceSurface: (SurfaceChange) -> Unit, onClearSourceMarks: () -> Unit, modifier: Modifier) {
    MonitorView(
        content = MonitorContent(
            pictureAspect = state.sourceAspect,
            video = state.sourceIsVideo,
            still = state.sourceStill,
            message = if (state.sourceName == null) "在素材库中选择一个媒体，或在时间线上选择片段" else null,
        ),
        onSurface = onSourceSurface,
        modifier = modifier,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.sourceMarks ?: "未标记入出点：编辑时使用整段素材。按 I / O 标记源片段",
            style = PrismType.caption,
            color = if (state.sourceMarks != null) PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.labelTertiary,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onClearSourceMarks,
            enabled = state.sourceMarks != null,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) { Text("清除") }
    }
}

/** The timeline viewer, with the mask outline and the keyer's eyedropper over its picture. */
@Composable
private fun ProgramMonitor(
    state: EditUiState,
    onProgramSurface: (SurfaceChange) -> Unit,
    onPickKey: (Float, Float) -> Unit,
    onMoveMask: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    MonitorView(
        content = MonitorContent(
            pictureAspect = state.pictureAspect,
            video = !state.programEmpty,
            message = state.programMessage,
            messageIsError = state.programMessage?.startsWith("素材离线") == true,
            note = if (state.previewApproximate) APPROXIMATE_PREVIEW_NOTE else null,
            layers = state.programLayers,
            composites = state.programComposites,
        ),
        onSurface = onProgramSurface,
        showSafeArea = !state.programEmpty,
        modifier = modifier,
        pictureOverlay = {
            // `mask-overlay`: the selected clip's mask while the video inspector shows, moved by a drag.
            state.inspector?.maskOverlay?.takeIf { state.inspectorTab == EditUiState.TAB_VIDEO }?.let { overlay ->
                WindowOverlay(overlay, state.pictureAspect, "遮罩：拖动以移动", onMoveMask)
            }
            // `key-pick-overlay`: the keyer's eyedropper picks the screen's colour off the monitor (on top: a tap picks).
            state.keyPick?.let { overlay -> PickOverlay(overlay, state.pictureAspect, onPickKey) }
        },
    )
}
