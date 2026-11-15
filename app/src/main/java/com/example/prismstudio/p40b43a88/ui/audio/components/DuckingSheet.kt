package com.example.prismstudio.p40b43a88.ui.audio.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.DuckingPreset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.DuckingControl
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.ui.audio.DuckingEditorUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.sliderLabelWidth
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `ducking-sheet`: one track's ducking under another — the music dipping while the dialogue speaks, as a
 * Fairlight ducker keyed from the dialogue track would. The key chips pick the track it listens to
 * (choosing one switches it on); the graph shows the key and the dip around the playhead; 启用 off
 * bypasses it without losing it; 重置 switches it off.
 */
@Composable
fun DuckingSheet(
    editor: DuckingEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onKey: (String) -> Unit,
    onReset: () -> Unit,
    onChange: (DuckingControl, Float) -> Unit,
    onPreset: (DuckingPreset) -> Unit = {},
) {
    val d = editor.ducking
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("闪避 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(
                    checked = editor.on,
                    onCheckedChange = onEnabled,
                    enabled = editor.keys.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "启用闪避：关闭即旁通".localized() },
                )
                Text(editor.status, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
                Button(
                    onClick = onReset,
                    enabled = d != TrackDucking.OFF,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("侧链", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(56.dp), fit = true)
                // A chip per track it can listen to; the lit one is the key (none lit: off).
                editor.keys.forEach { key ->
                    ToggleableChip(
                        label = { Text(key.name) },
                        isToggleOn = key.trackId == editor.keyTrackId && d.enabled,
                        onClick = { onKey(key.trackId) },
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "侧链轨道 ${key.name}".localized() },
                    )
                }
                Text(
                    readout(editor),
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            PresetMenuRow(
                chipLabel = editor.preset?.label ?: "自定义",
                description = "闪避预设",
                options = DuckingPreset.entries.map { it.label },
                selected = editor.preset?.ordinal,
                hint = "替换四项设置，保留侧链；未开启时一并开启",
            ) { onPreset(DuckingPreset.entries[it]) }
            DuckingGraph(editor, Modifier.fillMaxWidth().height(180.dp))
            val labelWidth = sliderLabelWidth(DuckingControl.entries.map { it.label }, 28.dp, 56.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlRow(DuckingControl.DEPTH, d.depthDb, editor.on, onChange, labelWidth)
                ControlRow(DuckingControl.THRESHOLD, d.thresholdDb, editor.on, onChange, labelWidth)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlRow(DuckingControl.ATTACK, d.attackMs, editor.on, onChange, labelWidth)
                ControlRow(DuckingControl.RELEASE, d.releaseMs, editor.on, onChange, labelWidth)
            }
        }
    }
}

/** What the key plays and how far the track is down at the playhead. */
private fun readout(editor: DuckingEditorUi): String {
    if (!editor.on) return ""
    val key = if (editor.keyLevelDb <= DuckingUseCase.FLOOR_DB) "侧链无声" else "侧链 ${formatValue(editor.keyLevelDb, 1, false, " dB")}"
    val dip = if (editor.duckDb > -0.05f) "未压低" else "压低 ${formatValue(-editor.duckDb, 1, false, " dB")}"
    return "$key · $dip"
}

/** A control; attack and release slide on a logarithmic scale (equal travel per doubling). */
@Composable
private fun RowScope.ControlRow(
    control: DuckingControl,
    value: Float,
    enabled: Boolean,
    onChange: (DuckingControl, Float) -> Unit,
    labelWidth: Dp,
) {
    ValueSliderRow(
        label = control.label,
        value = control.position(value),
        range = if (control.logScale) 0f..1f else control.min..control.max,
        valueText = valueText(control, value),
        onValueChange = { onChange(control, control.valueAt(it)) },
        enabled = enabled,
        step = if (control.logScale) 0f else 0.5f,
        minSliderWidth = 48.dp,
        labelWidth = labelWidth,
        valueWidth = 56.dp,
        modifier = Modifier.weight(1f),
    )
}

private fun valueText(control: DuckingControl, v: Float): String = when (control) {
    DuckingControl.ATTACK, DuckingControl.RELEASE -> if (v < 1000f) formatValue(v, 0, false, " ms") else formatValue(v / 1000f, 2, false, " s")
    else -> formatValue(v, 1, false, " dB")
}
