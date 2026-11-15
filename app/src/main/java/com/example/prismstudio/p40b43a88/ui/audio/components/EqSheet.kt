package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.layout.Arrangement
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.EqControl
import com.example.prismstudio.p40b43a88.ui.audio.EqEditorUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.sliderLabelWidth
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet
import kotlin.math.ln
import kotlin.math.pow

/**
 * `eq-sheet`: one track's equaliser — Fairlight's track EQ with four bands. A preset (`eq-preset-menu`) sets the whole
 * curve; the curve shows the response and its numbered handles drag the bands; the rows below set each band exactly.
 * 启用 off bypasses the EQ without losing it; 重置 flattens it.
 */
@Composable
fun EqSheet(
    editor: EqEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onLowCut: (Boolean) -> Unit,
    onReset: () -> Unit,
    onPreset: (EqPreset) -> Unit,
    onChange: (EqControl, Float) -> Unit,
    onDrag: (com.example.prismstudio.p40b43a88.domain.model.EqBand, Float, Float) -> Unit,
) {
    val eq = editor.eq
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("均衡器 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(
                    checked = eq.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = "启用均衡器：关闭即旁通".localized() },
                )
                Text(
                    if (eq.isFlat) "平直：不改变声音" else if (eq.enabled) editor.appliesTo else "已旁通",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onReset,
                    enabled = !eq.isFlat,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            PresetMenuRow(
                chipLabel = editor.preset?.label ?: if (eq.isFlat) "无" else "自定义",
                description = "均衡器预设",
                options = EqPreset.entries.map { it.label },
                selected = editor.preset?.ordinal,
                hint = "选一个预设替换整条曲线，再按需微调",
            ) { onPreset(EqPreset.entries[it]) }
            EqGraph(eq, onDrag, Modifier.fillMaxWidth().height(180.dp))
            // Label columns as wide as the words need in the language shown ("Frequency", "1 Low cut").
            val titleWidth = sliderLabelWidth(listOf("1 低切", "2 低频", "3 中频", "4 高频"), 56.dp, 72.dp)
            val labelWidth = sliderLabelWidth(EqControl.entries.map { it.label }, 28.dp, 48.dp)
            BandRow(titleWidth, "1 低切") {
                Switch(checked = eq.lowCut, onCheckedChange = onLowCut, modifier = Modifier.semantics { contentDescription = "低切".localized() })
                ControlRow(EqControl.LOW_CUT_HZ, eq.lowCutHz, enabled = eq.lowCut, onChange, labelWidth)
            }
            BandRow(titleWidth, "2 低频") {
                ControlRow(EqControl.LOW_HZ, eq.lowHz, true, onChange, labelWidth)
                ControlRow(EqControl.LOW_DB, eq.lowDb, true, onChange, labelWidth)
            }
            BandRow(titleWidth, "3 中频") {
                ControlRow(EqControl.MID_HZ, eq.midHz, true, onChange, labelWidth)
                ControlRow(EqControl.MID_DB, eq.midDb, true, onChange, labelWidth)
            }
            // The bell's width gets a row of its own: three sliders do not fit the sheet.
            BandRow(titleWidth, "") {
                ControlRow(EqControl.MID_Q, eq.midQ, true, onChange, labelWidth)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            BandRow(titleWidth, "4 高频") {
                ControlRow(EqControl.HIGH_HZ, eq.highHz, true, onChange, labelWidth)
                ControlRow(EqControl.HIGH_DB, eq.highDb, true, onChange, labelWidth)
            }
        }
    }
}

@Composable
private fun BandRow(titleWidth: Dp, title: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(titleWidth), fit = true)
        content()
    }
}

/** A band control; frequencies slide on a logarithmic scale (equal travel per octave). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ControlRow(
    control: EqControl,
    value: Float,
    enabled: Boolean,
    onChange: (EqControl, Float) -> Unit,
    labelWidth: Dp,
) {
    val log = control.logScale
    val span = ln(control.max / control.min)
    ValueSliderRow(
        label = control.label,
        value = if (log) ln(value / control.min) / span else value,
        range = if (log) 0f..1f else control.min..control.max,
        valueText = valueText(control, value),
        onValueChange = { t -> onChange(control, if (log) control.min * (control.max / control.min).pow(t) else t) },
        enabled = enabled,
        step = if (log) 0f else if (control == EqControl.MID_Q) 0.01f else 0.1f,
        minSliderWidth = 48.dp,
        labelWidth = labelWidth,
        valueWidth = 56.dp,
        modifier = Modifier.weight(1f),
    )
}

private fun valueText(control: EqControl, v: Float): String = when (control) {
    EqControl.MID_Q -> formatValue(v, 2, false)
    EqControl.LOW_DB, EqControl.MID_DB, EqControl.HIGH_DB -> formatValue(v, 1, true, " dB")
    else -> if (v >= 1000f) formatValue(v / 1000f, if (v >= 10000f) 1 else 2, false, " kHz") else formatValue(v, 0, false, " Hz")
}
