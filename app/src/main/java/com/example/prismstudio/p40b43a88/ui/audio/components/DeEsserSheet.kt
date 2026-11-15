package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.DeEssBand
import com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser
import com.example.prismstudio.p40b43a88.ui.audio.DeEsserEditorUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `deesser-sheet`: one track's de-esser — Fairlight's De-Esser. 强度 lowers the threshold the high band must rise above to
 * be turned down; 频段 is where that band starts (sibilance sits around 5–9 kHz). It acts on every clip of the track, after
 * its EQ and before its dynamics; 重置 switches it off.
 */
@Composable
fun DeEsserSheet(
    editor: DeEsserEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onAmount: (Float) -> Unit,
    onBand: (DeEssBand) -> Unit,
    onReset: () -> Unit,
) {
    val d = editor.deEsser
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("齿音消除 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(checked = d.enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = "启用齿音消除".localized() })
                Text(
                    if (d.isActive) "作用于这条轨道的所有片段" else "调高强度后，“嘶”“丝”这类齿音会被压低",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onReset,
                    enabled = d != TrackDeEsser.OFF,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            ValueSliderRow(
                label = "强度",
                value = d.amount.toFloat(),
                range = 0f..100f,
                valueText = if (d.amount == 0) "关" else "${d.amount}",
                onValueChange = onAmount,
                enabled = d.enabled,
                step = 1f,
                labelWidth = 72.dp,
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("频段", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp), fit = true)
                SegmentControl(Modifier.width(300.dp).semantics { contentDescription = "齿音频段".localized() }) {
                    DeEssBand.entries.forEach { band ->
                        SegmentItem(
                            selected = band == d.band,
                            onClick = { if (d.enabled) onBand(band) },
                            title = { Text("${band.label} 以上") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(editor.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
    }
}
