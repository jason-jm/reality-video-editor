package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.SelectionUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/**
 * `multi-selection-panel`: the inspector while several clips are selected — how many and what they are,
 * and what applies to all of them at once (the same commands as the clip menu and the keys). One clip's
 * properties come back when a single clip is selected again.
 */
@Composable
fun MultiSelectionPanel(selection: SelectionUi, canPaste: Boolean, onEvent: (EditEvent) -> Unit, modifier: Modifier = Modifier) {
    // The batch commands, Auto Align and New Compound Clip outgrow the inspector: the panel scrolls.
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("已选择 ${selection.count} 个片段", style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary)
            Text(selection.detail, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
        }
        if (selection.locked) {
            Text("部分片段在已锁定的轨道上：会改动它们的编辑将被拒绝", style = PrismType.caption, color = PicoTheme.colorScheme.error)
        }
        BatchRow(
            (if (selection.allDisabled) "启用全部" else "停用全部") to EditEvent.ToggleClipEnabled,
            "创建副本" to EditEvent.DuplicateClip,
            onEvent = onEvent,
        )
        BatchRow("复制" to EditEvent.CopyClip, "剪切" to EditEvent.CutClip, onEvent = onEvent)
        BatchRow("粘贴属性…" to EditEvent.OpenPasteAttributes, "删除" to EditEvent.DeleteClip, onEvent = onEvent, firstEnabled = canPaste)
        BatchRow("波纹删除" to EditEvent.RippleDelete, "取消选择" to EditEvent.SelectClip(null), onEvent = onEvent)
        AlignRow(selection, onEvent)
        CompoundRow(selection, onEvent)
        MulticamRow(selection, onEvent)
        Text(
            "拖动其中任一片段可一起移动或换轨道，, 和 . 键一起微移，Ctrl+↑ / ↓ 一起上下换轨；按住 Ctrl / Cmd 点按片段，或开启时间线的「多选」，可加入或移出选择；多选时在空白处拖动可框选。",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
        )
    }
}

/**
 * `selection-align-row`: Resolve's Auto Align Clips (Based on Waveform) — the other selected clips move into step with the
 * one tapped last, by their sound (a recorder's track with the camera's, say). The note names the reference.
 */
@Composable
private fun AlignRow(selection: SelectionUi, onEvent: (EditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("按波形对齐", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(EditEvent.AlignByWaveform) },
                enabled = selection.canAlign,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "按波形自动对齐所选片段".localized() },
            ) { Text(if (selection.aligning) "正在对齐…" else "自动对齐") }
            // `selection-align-note`
            Text(selection.alignNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * `selection-compound-row`: Resolve's New Compound Clip — the selected clips (and their linked sound) gathered into one clip
 * in their place, to move, trim and reuse as one; the note says what it makes or why it cannot.
 */
@Composable
private fun CompoundRow(selection: SelectionUi, onEvent: (EditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("复合片段", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(EditEvent.CreateCompound) },
                enabled = selection.canCompound,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "把所选片段新建为复合片段".localized() },
            ) { Text("新建复合片段") }
            // `selection-compound-note`
            Text(selection.compoundNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * `selection-multicam-row`: Resolve's New Multicam Clip — the selected camera clips (each with its linked sound), as they lie
 * in step, become the angles of one clip in their place: angle 1 the lowest track's. The note says what it makes or why not.
 */
@Composable
private fun MulticamRow(selection: SelectionUi, onEvent: (EditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("多机位", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(EditEvent.CreateMulticam) },
                enabled = selection.canMulticam,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "把所选片段新建为多机位片段".localized() },
            ) { Text("新建多机位片段") }
            // `selection-multicam-note`
            Text(selection.multicamNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
        }
    }
}

/** Two batch commands side by side. */
@Composable
private fun BatchRow(
    first: Pair<String, EditEvent>,
    second: Pair<String, EditEvent>,
    onEvent: (EditEvent) -> Unit,
    firstEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(first to firstEnabled, second to true).forEach { (command, enabled) ->
            Button(
                onClick = { onEvent(command.second) },
                enabled = enabled,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.weight(1f),
            ) { Text(command.first) }
        }
    }
}
