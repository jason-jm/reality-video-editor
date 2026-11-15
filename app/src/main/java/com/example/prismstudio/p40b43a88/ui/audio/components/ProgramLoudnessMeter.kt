package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.Canvas
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.ui.audio.ProgramLoudnessUi
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/**
 * `program-loudness-meter`: Fairlight's loudness meter across the top of the audio lanes. On the left the
 * readings at the playhead — 瞬时 M (400 ms), 短期 S (3 s) and 整体 I (the whole program, gated) in LUFS,
 * with the loudness range and peak — and on the right `loudness-history`, the last 30 seconds of
 * short-term loudness against the page's loudness target (dashed).
 */
@Composable
fun ProgramLoudnessMeter(ui: ProgramLoudnessUi, modifier: Modifier = Modifier) {
    val description = labelled(
        "节目响度".localized(),
        spoken(
            "瞬时 ${ui.momentary}".localized(), "短期 ${ui.shortTerm}".localized(), "整体 ${ui.integrated} LUFS".localized(),
            "响度范围 ${ui.range}".localized(), "真峰值 ${ui.peak}".localized(),
        ),
    )
    Row(
        modifier.height(METER_HEIGHT).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(336.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("节目响度 · LUFS", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
                Text("目标 ${formatValue(ui.targetLufs, 0, false)}", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reading("瞬时 M", ui.momentary, PicoTheme.colorScheme.labelPrimary, Modifier.weight(1f))
                Reading("短期 S", ui.shortTerm, if (ui.hot) PrismColors.MeterHot else PicoTheme.colorScheme.labelPrimary, Modifier.weight(1f))
                Reading("整体 I", ui.integrated, PicoTheme.colorScheme.labelPrimary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("响度范围 ${ui.range}", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
                Text(
                    "真峰值 ${ui.peak}",
                    style = PrismType.caption,
                    color = if (ui.clipping) PrismColors.MeterClip else PicoTheme.colorScheme.labelTertiary,
                    maxLines = 1,
                )
            }
            ui.note?.let {
                Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LoudnessHistory(ui, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Reading(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(label, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
        Text(value, style = PrismType.title, color = color, maxLines = 1)
    }
}

/**
 * `loudness-history`: −40…0 LUFS bottom to top over the last 30 s (the right edge is the playhead); the
 * short-term line with its area (amber where it runs over the target by more than 1 LU), the momentary
 * line faint behind it, the target dashed.
 */
@Composable
private fun LoudnessHistory(ui: ProgramLoudnessUi, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val grid = line.copy(alpha = 0.16f)
    val faint = line.copy(alpha = 0.55f)
    val momentaryColor = line.copy(alpha = 0.3f)
    val backing = PrismColors.PictureBlack.copy(alpha = 0.72f)
    val labelStyle = PrismType.caption.copy(color = faint)
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = "最近 30 秒的短期响度".localized() }) {
        drawRect(PrismColors.PictureBlack)
        val pad = 6.dp.toPx()
        val top = pad
        val bottom = size.height - pad
        fun y(lufs: Float) = bottom - ((lufs.coerceIn(FLOOR, CEILING) - FLOOR) / (CEILING - FLOOR)) * (bottom - top)
        for (level in GRID) {
            val gy = y(level)
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            drawText(measurer.measure(formatValue(level, 0, false), labelStyle), topLeft = Offset(pad, gy - 16.dp.toPx()))
        }
        val history = ui.history
        if (history.isEmpty()) {
            val text = measurer.measure((ui.note ?: "播放或拖动播放头查看响度").localized(), labelStyle)
            drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (size.height - text.size.height) / 2))
            return@Canvas
        }
        val step = size.width / (history.size - 1).coerceAtLeast(1)
        // Momentary first, faint: it swings with every word or beat.
        val momentary = Path()
        var drawing = false
        ui.momentaryHistory.forEachIndexed { i, v ->
            if (v.isNaN()) { drawing = false; return@forEachIndexed }
            val p = Offset(i * step, y(v))
            if (drawing) momentary.lineTo(p.x, p.y) else momentary.moveTo(p.x, p.y)
            drawing = true
        }
        drawPath(momentary, momentaryColor, style = Stroke(1.dp.toPx()))
        // Short-term: an area down to the floor, coloured by where it stands against the target.
        val hot = ui.targetLufs + 1f
        for (i in 0 until history.size - 1) {
            val a = history[i]
            val b = history[i + 1]
            if (a.isNaN() || b.isNaN()) continue
            val colour = if (maxOf(a, b) > hot) PrismColors.MeterHot else PrismColors.MeterSafe
            val area = Path().apply {
                moveTo(i * step, bottom)
                lineTo(i * step, y(a))
                lineTo((i + 1) * step, y(b))
                lineTo((i + 1) * step, bottom)
                close()
            }
            drawPath(area, colour.copy(alpha = 0.28f))
            drawLine(colour, Offset(i * step, y(a)), Offset((i + 1) * step, y(b)), strokeWidth = 2.dp.toPx())
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val ty = y(ui.targetLufs)
        drawLine(line, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        val label = measurer.measure("目标 ${formatValue(ui.targetLufs, 0, false)}".localized(), labelStyle)
        val at = Offset(size.width - label.size.width - pad, (ty - label.size.height - 2.dp.toPx()).coerceAtLeast(top))
        drawRoundRect(
            backing,
            topLeft = at - Offset(4.dp.toPx(), 0f),
            size = Size(label.size.width + 8.dp.toPx(), label.size.height.toFloat()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawText(label, topLeft = at)
        val span = measurer.measure("${ProgramLoudnessUi.HISTORY_SECONDS} 秒前".localized(), labelStyle)
        drawText(span, topLeft = Offset(pad, bottom - span.size.height))
        drawLine(line, Offset(size.width - 1.dp.toPx(), 0f), Offset(size.width - 1.dp.toPx(), size.height), strokeWidth = 2.dp.toPx())
    }
}

private val METER_HEIGHT = 128.dp
private const val FLOOR = -40f
private const val CEILING = 0f
private val GRID = listOf(-10f, -20f, -30f)
