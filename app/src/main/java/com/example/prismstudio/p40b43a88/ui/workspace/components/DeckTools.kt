package com.example.prismstudio.p40b43a88.ui.workspace.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.ui.color.ColorEvent
import com.example.prismstudio.p40b43a88.ui.color.ColorUiState
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.EditUiState
import com.example.prismstudio.p40b43a88.ui.media.MediaEvent
import com.example.prismstudio.p40b43a88.ui.media.MediaUiState
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage
import com.example.prismstudio.p40b43a88.ui.workspace.WorkspaceEvent
import com.example.prismstudio.p40b43a88.ui.workspace.WorkspaceUiState
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.labelWeight
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.ToggleIconButton

/**
 * Routes the Jog Deck to the monitor the current stage drives: the source monitor in Media (and in
 * Edit's source view), the program monitor elsewhere, and shows that stage's edit keys.
 */
@Composable
fun DeckTools(
    workspace: WorkspaceUiState,
    media: MediaUiState,
    edit: EditUiState,
    color: ColorUiState,
    onWorkspace: (WorkspaceEvent) -> Unit,
    onMedia: (MediaEvent) -> Unit,
    onEdit: (EditEvent) -> Unit,
    onColor: (ColorEvent) -> Unit,
) {
    val program = DeckTransport(workspace.playing, workspace.timecode, workspace.fps)
    val stage = workspace.stage
    val source = stage == WorkspaceStage.EDIT && edit.viewerMode == EditUiState.VIEWER_SOURCE
    // Speed Editor modes: on the Edit stage the dial can slip or roll the selected clip instead.
    val trimming = stage == WorkspaceStage.EDIT && !source && edit.jogMode != EditUiState.JOG_PLAYHEAD
    val transport = when {
        stage == WorkspaceStage.MEDIA -> DeckTransport(media.playing, media.sourceTimecode, media.fps)
        source -> DeckTransport(edit.sourcePlaying, edit.programTimecode, edit.fps)
        trimming -> program.copy(readout = edit.jogReadout)
        else -> program
    }
    // One deck for every stage: the ornament window stays put and only its keys change.
    JogDeck(
        transport = transport,
        loop = workspace.loop.takeIf { stage != WorkspaceStage.MEDIA && !source },
        onLoop = { onWorkspace(WorkspaceEvent.SetLoop(it)) },
        onPlay = {
            when {
                stage == WorkspaceStage.MEDIA -> onMedia(MediaEvent.SetPlaying(it))
                source -> onEdit(EditEvent.SourcePlaying(it))
                else -> onWorkspace(WorkspaceEvent.SetPlaying(it))
            }
        },
        // ⏮ / ⏭: the source's marks on the Media stage, Resolve's Previous / Next Clip on the Color stage, else the edit points.
        onPrevious = {
            when (stage) {
                WorkspaceStage.MEDIA -> onMedia(MediaEvent.JumpToIn)
                WorkspaceStage.COLOR -> onColor(ColorEvent.StepClip(forward = false))
                else -> onWorkspace(WorkspaceEvent.PreviousEdit)
            }
        },
        onNext = {
            when (stage) {
                WorkspaceStage.MEDIA -> onMedia(MediaEvent.JumpToOut)
                WorkspaceStage.COLOR -> onColor(ColorEvent.StepClip(forward = true))
                else -> onWorkspace(WorkspaceEvent.NextEdit)
            }
        },
        previousLabel = when (stage) {
            WorkspaceStage.MEDIA -> "跳到入点"
            WorkspaceStage.COLOR -> "上一个片段"
            else -> "上一编辑点"
        },
        nextLabel = when (stage) {
            WorkspaceStage.MEDIA -> "跳到出点"
            WorkspaceStage.COLOR -> "下一个片段"
            else -> "下一编辑点"
        },
        onJog = {
            when {
                stage == WorkspaceStage.MEDIA -> onMedia(MediaEvent.Jog(it))
                source -> onEdit(EditEvent.SourceJog(it))
                trimming -> onEdit(EditEvent.TrimJog(it))
                else -> onWorkspace(WorkspaceEvent.Jog(it))
            }
        },
        showDivider = stage == WorkspaceStage.MEDIA || stage == WorkspaceStage.EDIT || stage == WorkspaceStage.COLOR,
    ) {
        when (stage) {
            WorkspaceStage.MEDIA -> MediaKeys(media, onMedia)
            WorkspaceStage.EDIT -> EditKeys(edit, onEdit)
            WorkspaceStage.COLOR -> ColorKeys(color, onColor)
            WorkspaceStage.AUDIO, WorkspaceStage.DELIVER -> Unit
        }
    }
}

@Composable
private fun MediaKeys(media: MediaUiState, onMedia: (MediaEvent) -> Unit) {
    IconButton(onClick = { onMedia(MediaEvent.MarkIn) }, enabled = !media.nothingSelected) {
        Icon(painterResource(R.drawable.ic_mark_in), contentDescription = "标记入点")
    }
    IconButton(onClick = { onMedia(MediaEvent.MarkOut) }, enabled = !media.nothingSelected) {
        Icon(painterResource(R.drawable.ic_mark_out), contentDescription = "标记出点")
    }
    // Resolve's edit keys: the marked range at the timeline's playhead (F9 / F10 / F12), the clip there
    // replaced (F11) or ripple-overwritten at the range's own length (Shift+F10), the range retimed into the
    // timeline's In / Out (Shift+F11), or the range at the end.
    val quiet = ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.fillTertiary,
        contentColor = PicoTheme.colorScheme.labelPrimary,
    )
    val modes = listOf(
        SourceEdit.INSERT, SourceEdit.OVERWRITE, SourceEdit.RIPPLE_OVERWRITE, SourceEdit.REPLACE, SourceEdit.FIT_TO_FILL, SourceEdit.PLACE_ON_TOP,
    )
    modes.forEach { mode ->
        Button(
            onClick = { onMedia(MediaEvent.Edit(mode)) },
            // `fit-to-fill-button` needs the timeline's In and Out.
            enabled = !media.nothingSelected && (mode != SourceEdit.FIT_TO_FILL || media.canFitToFill),
            size = ButtonDefaults.Regular,
            colors = quiet,
            modifier = Modifier.semantics {
                contentDescription = when (mode) {
                    SourceEdit.REPLACE -> "替换时间线播放头处的片段（${mode.keys}）：源播放头对齐时间线播放头，片段长度不变"
                    // `ripple-overwrite-button`
                    SourceEdit.RIPPLE_OVERWRITE ->
                        "波纹覆盖（${mode.keys}）：源片段的入点到出点替换时间线播放头处的片段（或时间线入点到出点），后面的片段随长度变化前后移动"
                    SourceEdit.FIT_TO_FILL -> "适配填充（${mode.keys}）：源片段的入点到出点变速，正好填满时间线的入点到出点"
                    else -> "${mode.label}到时间线播放头（${mode.keys}）"
                }.localized()
            },
        ) { Text(mode.label) }
    }
    Button(
        onClick = { onMedia(MediaEvent.Append) },
        enabled = !media.nothingSelected,
        size = ButtonDefaults.Regular,
        leadingIcon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null) },
        modifier = Modifier.semantics { contentDescription = "追加到时间线末尾（Shift+F12）".localized() },
    ) { Text("追加") }
}

@Composable
private fun EditKeys(edit: EditUiState, onEdit: (EditEvent) -> Unit) {
    // `jog-mode-segment`: what the dial does — move the playhead, or slip, roll or slide the selected clip.
    SegmentControl(Modifier.width(288.dp).semantics { contentDescription = "飞梭模式".localized() }) {
        EditUiState.JOG_MODES.forEachIndexed { index, label ->
            SegmentItem(
                selected = index == edit.jogMode,
                onClick = { onEdit(EditEvent.JogMode(index)) },
                title = { Text(label) },
                modifier = Modifier.weight(labelWeight(label)),
            )
        }
    }
    // `timeline-mark-in-button` / `timeline-mark-out-button`: the timeline's In / Out at the playhead (I / O).
    IconButton(onClick = { onEdit(EditEvent.MarkIn) }) {
        Icon(painterResource(R.drawable.ic_mark_in), contentDescription = "标记时间线入点（I）")
    }
    IconButton(onClick = { onEdit(EditEvent.MarkOut) }) {
        Icon(painterResource(R.drawable.ic_mark_out), contentDescription = "标记时间线出点（O）")
    }
    IconButton(onClick = { onEdit(EditEvent.Split) }) {
        Icon(painterResource(R.drawable.ic_scissors), contentDescription = "在播放头处分割")
    }
    // With no clip selected it ripple-deletes the In–Out range, as Delete does.
    val range = edit.hasRange && edit.nothingSelected && edit.selection == null
    IconButton(onClick = { onEdit(EditEvent.RippleDelete) }, enabled = !edit.nothingSelected || edit.selection != null || edit.hasRange) {
        Icon(painterResource(R.drawable.ic_ripple_delete), contentDescription = if (range) "波纹删除入点至出点" else "波纹删除")
    }
    IconButton(onClick = { onEdit(EditEvent.AddMarker) }) {
        Icon(painterResource(R.drawable.ic_marker), contentDescription = "添加标记")
    }
    ToggleIconButton(checked = edit.snapping, onCheckedChange = { onEdit(EditEvent.SetSnapping(it)) }) {
        Icon(painterResource(R.drawable.ic_magnet), contentDescription = "吸附")
    }
}

@Composable
private fun ColorKeys(color: ColorUiState, onColor: (ColorEvent) -> Unit) {
    val quiet = ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.fillTertiary,
        contentColor = PicoTheme.colorScheme.labelPrimary,
    )
    ToggleIconButton(checked = color.compare, onCheckedChange = { onColor(ColorEvent.SetCompare(it)) }, enabled = color.hasClip) {
        Icon(painterResource(R.drawable.ic_rectangle_split_2x1_fill), contentDescription = "调色前后对比")
    }
    // `auto-color-button`: Resolve's Auto Color (Shift+Alt+C) — node 1's black and white points, cast removed.
    Button(
        onClick = { onColor(ColorEvent.AutoColor) },
        enabled = color.hasClip,
        size = ButtonDefaults.Regular,
        colors = quiet,
        modifier = Modifier.semantics { contentDescription = "自动调色（Shift+Alt+C）：按画面的黑位、白位设定暗部与亮部，并校正偏色".localized() },
    ) { Text("自动调色") }
    GradeVersionMenu(color, onColor)
    Button(onClick = { onColor(ColorEvent.CopyGrade) }, enabled = color.hasClip, size = ButtonDefaults.Regular, colors = quiet) { Text("复制调色") }
    Button(onClick = { onColor(ColorEvent.PasteGrade) }, enabled = !color.clipboardEmpty && color.hasClip, size = ButtonDefaults.Regular, colors = quiet) { Text("粘贴调色") }
    Button(onClick = { onColor(ColorEvent.ResetGrade) }, enabled = color.hasClip, size = ButtonDefaults.Regular, colors = quiet) { Text("重置") }
}
