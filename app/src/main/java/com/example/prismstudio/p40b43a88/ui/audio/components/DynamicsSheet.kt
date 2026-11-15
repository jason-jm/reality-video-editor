package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.DynamicsControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicsPreset
import com.example.prismstudio.p40b43a88.ui.audio.DynamicsEditorUi
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

/**
 * `dynamics-sheet`: one track's dynamics — Fairlight's Dynamics cut down to a compressor and a limiter, with its presets.
 * The transfer curve shows what they do to each level and where the track plays now; the meter beside
 * it how far they turn it down. 启用 off bypasses both without losing them; 重置 switches them off.
 */
@Composable
fun DynamicsSheet(
    editor: DynamicsEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onCompressor: (Boolean) -> Unit,
    onLimiter: (Boolean) -> Unit,
    onReset: () -> Unit,
    onPreset: (DynamicsPreset) -> Unit,
    onChange: (DynamicsControl, Float) -> Unit,
    onGate: (Boolean) -> Unit = {},
) {
    val d = editor.dynamics
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("动态 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(checked = d.enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = "启用动态：关闭即旁通".localized() })
                Text(
                    when {
                        d.isOff -> "未开启：打开门限、压缩器或限制器"
                        !d.enabled -> "已旁通"
                        else -> "作用于这条轨道的所有片段，在均衡器之后、推子之前"
                    },
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onReset,
                    enabled = !d.isOff,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            PresetMenuRow(
                chipLabel = editor.preset?.label ?: if (d.isOff) "无" else "自定义",
                description = "动态预设",
                options = DynamicsPreset.entries.map { it.label },
                selected = editor.preset?.ordinal,
                hint = "选一个预设替换门限、压缩器和限制器的设置，再按需微调",
            ) { onPreset(DynamicsPreset.entries[it]) }
            Row(Modifier.fillMaxWidth().height(GRAPH_HEIGHT), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DynamicsGraph(d, editor.inputDb, Modifier.weight(1f).height(GRAPH_HEIGHT))
                ReductionMeter(editor.reductionDb.takeIf { d.isActive } ?: 0f)
            }
            // Compact words where they have them ("Comp", "Thresh"): two sliders a row leave little room for labels.
            val titleWidth = sliderLabelWidth(listOf("门限", "压缩器", "限制器"), 56.dp, 72.dp, COMPACT)
            val labelWidth = sliderLabelWidth(DynamicsControl.entries.map { it.label }, 28.dp, 56.dp, COMPACT)
            // Fairlight's Expander/Gate, first in the chain: below the threshold the track goes down 4:1, by at most 范围.
            SectionRow(titleWidth, "门限", d.gate, onGate, "扩展器 / 门限") {
                ControlRow(DynamicsControl.GATE_THRESHOLD, d.gateThresholdDb, d.gate, onChange, labelWidth)
                ControlRow(DynamicsControl.GATE_RANGE, d.gateRangeDb, d.gate, onChange, labelWidth)
            }
            SectionRow(titleWidth, null, null, {}, null) {
                ControlRow(DynamicsControl.GATE_HOLD, d.gateHoldMs, d.gate, onChange, labelWidth)
                ControlRow(DynamicsControl.GATE_RELEASE, d.gateReleaseMs, d.gate, onChange, labelWidth)
            }
            SectionRow(titleWidth, "压缩器", d.compressor, onCompressor, "压缩器") {
                ControlRow(DynamicsControl.THRESHOLD, d.thresholdDb, d.compressor, onChange, labelWidth)
                ControlRow(DynamicsControl.RATIO, d.ratio, d.compressor, onChange, labelWidth)
            }
            SectionRow(titleWidth, null, null, {}, null) {
                ControlRow(DynamicsControl.ATTACK, d.attackMs, d.compressor, onChange, labelWidth)
                ControlRow(DynamicsControl.RELEASE, d.releaseMs, d.compressor, onChange, labelWidth)
            }
            SectionRow(titleWidth, null, null, {}, null) {
                ControlRow(DynamicsControl.MAKEUP, d.makeupDb, d.compressor, onChange, labelWidth)
                Spacer(Modifier.weight(1f))
            }
            SectionRow(titleWidth, "限制器", d.limiter, onLimiter, "限制器") {
                ControlRow(DynamicsControl.CEILING, d.ceilingDb, d.limiter, onChange, labelWidth)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A section's row: its title and switch (or the space they take), then its controls. */
@Composable
private fun SectionRow(
    titleWidth: Dp,
    title: String?,
    on: Boolean?,
    onToggle: (Boolean) -> Unit,
    description: String?,
    content: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.orEmpty(), style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(titleWidth), fit = true, context = COMPACT)
        if (on != null) {
            Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = (description.orEmpty()).localized() })
        } else {
            Spacer(Modifier.width(SWITCH_WIDTH))
        }
        content()
    }
}

/** A control; attack and release slide on a logarithmic scale (equal travel per doubling). */
@Composable
private fun RowScope.ControlRow(
    control: DynamicsControl,
    value: Float,
    enabled: Boolean,
    onChange: (DynamicsControl, Float) -> Unit,
    labelWidth: Dp,
) {
    ValueSliderRow(
        label = control.label,
        value = control.position(value),
        range = if (control.logScale) 0f..1f else control.min..control.max,
        valueText = valueText(control, value),
        onValueChange = { onChange(control, control.valueAt(it)) },
        enabled = enabled,
        step = when {
            control.logScale -> 0f
            else -> 0.1f
        },
        minSliderWidth = 48.dp,
        labelWidth = labelWidth,
        labelContext = COMPACT,
        valueWidth = 56.dp,
        modifier = Modifier.weight(1f),
    )
}

/** `dynamics-reduction-meter`: how far the dynamics turn the track down at the playhead, 0 at the top to −24 dB. */
@Composable
private fun ReductionMeter(reductionDb: Float) {
    val fill = PicoTheme.colorScheme.interaction
    val track = PicoTheme.colorScheme.fillTertiary
    Column(Modifier.width(56.dp).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("衰减", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, fit = true, context = COMPACT)
        Canvas(Modifier.width(16.dp).weight(1f).clip(RoundedCornerShape(8.dp)).semantics { contentDescription = "增益衰减".localized() }) {
            drawRect(track)
            val depth = (-reductionDb / REDUCTION_RANGE_DB).coerceIn(0f, 1f) * size.height
            if (depth > 0f) drawRect(fill, topLeft = Offset.Zero, size = Size(size.width, depth))
        }
        Text(
            if (reductionDb > -0.05f) "0 dB" else formatValue(reductionDb, 1, true, " dB"),
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun valueText(control: DynamicsControl, v: Float): String = when (control) {
    DynamicsControl.RATIO -> formatValue(v, 1, false, ":1")
    DynamicsControl.ATTACK, DynamicsControl.RELEASE, DynamicsControl.GATE_RELEASE -> formatValue(v, if (v < 10f) 1 else 0, false, " ms")
    DynamicsControl.GATE_HOLD -> formatValue(v, 0, false, " ms")
    DynamicsControl.GATE_RANGE -> formatValue(v, 0, false, " dB")
    DynamicsControl.MAKEUP -> formatValue(v, 1, true, " dB")
    else -> formatValue(v, 1, false, " dB")
}

private val SWITCH_WIDTH = 52.dp

/** The catalog context of the sheet's compact words. */
private const val COMPACT = "compact"
/** The transfer graph, a little shorter since the gate joined the sheet. */
private val GRAPH_HEIGHT = 168.dp
private const val REDUCTION_RANGE_DB = 24f
