package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.NoiseGate
import com.example.prismstudio.p40b43a88.domain.model.NoiseReductionControl
import com.example.prismstudio.p40b43a88.ui.audio.NoiseReductionEditorUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
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
 * `noise-reduction-sheet`: one track's noise reduction — Fairlight's Noise Reduction in its automatic mode. Each file's own
 * noise floor is measured once; in every band, sound within the threshold of it is turned down by the reduction, sound
 * clearly above it passes. The graph shows the floor of the file under the playhead and, above it, the line under which
 * sound counts as noise. 启用 switches it on (first in the track's chain, before the EQ); 重置 switches it off.
 */
@Composable
fun NoiseReductionSheet(
    editor: NoiseReductionEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onReset: () -> Unit,
    onChange: (NoiseReductionControl, Float) -> Unit,
) {
    val nr = editor.noiseReduction
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("降噪 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(checked = nr.enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = "启用降噪".localized() })
                Text(
                    if (nr.enabled) "作用于这条轨道的所有片段，在均衡器之前" else "未开启：打开后按每个文件自己的噪声底压低噪声",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onReset,
                    enabled = nr != com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction.OFF,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            NoiseFloorGraph(editor, Modifier.fillMaxWidth().height(180.dp))
            Text(editor.status, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            NoiseReductionControl.entries.forEach { control ->
                val value = control.read(nr)
                ValueSliderRow(
                    label = control.label,
                    value = value,
                    range = control.min..control.max,
                    valueText = formatValue(value, 1, false, " dB"),
                    onValueChange = { onChange(control, it) },
                    enabled = nr.enabled,
                    step = 0.5f,
                    labelWidth = 72.dp,
                )
            }
            Text(
                when {
                    !nr.enabled -> "降噪量：噪声压低多少；阈值：比噪声底高多少以内算噪声"
                    nr.reductionDb == 0f -> "降噪量为 0：声音不变"
                    else -> "比噪声底高 ${formatValue(nr.thresholdDb, 1, false, "")} dB 以内的声音压低 ${formatValue(nr.reductionDb, 1, false, "")} dB，" +
                        "再高 ${NoiseGate.KNEE_DB.toInt()} dB 以上的声音不受影响"
                },
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
            )
        }
    }
}

/**
 * `noise-floor-graph`: the floor of the file under the playhead from 50 Hz to 20 kHz (logarithmic), over −100 to −20 dB (white
 * noise of RMS r reads 20·log10 r in every band), filled; the line above it at the threshold — sound under it counts as noise
 * and is turned down — dashed while the noise reduction is off.
 */
@Composable
private fun NoiseFloorGraph(editor: NoiseReductionEditorUi, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val accent = PicoTheme.colorScheme.interaction
    val grid = line.copy(alpha = 0.16f)
    val faint = line.copy(alpha = 0.55f)
    val labelStyle = PrismType.caption.copy(color = faint)
    val description = if (editor.floorDb.isEmpty()) "噪声谱：还没有测量" else "噪声谱：噪声底和阈值线"
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = description.localized() }) {
        drawRect(PrismColors.PictureBlack)
        val x = { hz: Double -> (size.width * ln(hz / LOW_HZ) / ln(HIGH_HZ / LOW_HZ)).toFloat() }
        val y = { db: Float -> size.height * (TOP_DB - db) / (TOP_DB - BOTTOM_DB) }
        for (hz in listOf(100.0, 1_000.0, 10_000.0)) drawLine(grid, Offset(x(hz), 0f), Offset(x(hz), size.height), strokeWidth = 1.dp.toPx())
        for (db in listOf(-80f, -60f, -40f)) drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = 1.dp.toPx())
        listOf(100.0 to "100", 1_000.0 to "1k", 10_000.0 to "10k").forEach { (hz, text) ->
            drawText(measurer.measure(text, labelStyle), topLeft = Offset(x(hz) + 4.dp.toPx(), size.height - 18.dp.toPx()))
        }
        listOf(-40f to "−40 dB", -80f to "−80 dB").forEach { (db, text) ->
            drawText(measurer.measure(text, labelStyle), topLeft = Offset(4.dp.toPx(), y(db) - 16.dp.toPx()))
        }
        if (editor.floorDb.isEmpty() || editor.floorStepHz <= 0f) return@Canvas
        val levels = editor.floorDb
        val at = { hz: Double ->
            val bin = (hz / editor.floorStepHz).coerceIn(0.0, levels.lastIndex.toDouble())
            val i = bin.toInt().coerceAtMost(levels.lastIndex - 1)
            val t = (bin - i).toFloat()
            levels[i] * (1 - t) + levels[i + 1] * t
        }
        val steps = (size.width / 3f).toInt().coerceAtLeast(2)
        val floor = Path()
        val threshold = Path()
        for (i in 0..steps) {
            val hz = LOW_HZ * (HIGH_HZ / LOW_HZ).pow(i.toDouble() / steps)
            val px = size.width * i / steps
            val db = at(hz).coerceIn(BOTTOM_DB, TOP_DB)
            if (i == 0) floor.moveTo(px, y(db)) else floor.lineTo(px, y(db))
            val over = (db + editor.noiseReduction.thresholdDb).coerceIn(BOTTOM_DB, TOP_DB)
            if (i == 0) threshold.moveTo(px, y(over)) else threshold.lineTo(px, y(over))
        }
        val fill = Path().apply {
            addPath(floor)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, line, alpha = 0.22f)
        drawPath(floor, faint, style = Stroke(1.5f.dp.toPx()))
        val on = editor.noiseReduction.isActive
        drawPath(
            threshold,
            if (on) accent else faint,
            style = Stroke(
                2.dp.toPx(),
                pathEffect = if (on) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            ),
        )
    }
}

private const val LOW_HZ = 50.0
private const val HIGH_HZ = 20_000.0
private const val TOP_DB = -20f
private const val BOTTOM_DB = -100f
