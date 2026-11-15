package com.example.prismstudio.p40b43a88.ui.audio.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.DuckingControl
import com.example.prismstudio.p40b43a88.ui.audio.DuckingEditorUi
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme

/**
 * `ducking-graph`: eight seconds of the timeline around the playhead (the line in the middle). Above, the
 * key track's level (bars, −60…0 dB), bright where it passes the threshold (the dashed line); below, in
 * the accent, how far the ducked track dips (0 dB at the top of its lane, down to −30 dB), the dashed line
 * at its depth. The dip starts before the key does: the timeline is known ahead.
 */
@Composable
fun DuckingGraph(editor: DuckingEditorUi, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val accent = PicoTheme.colorScheme.interaction
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val grid = line.copy(alpha = 0.16f)
    val faint = line.copy(alpha = 0.55f)
    val heard = line.copy(alpha = 0.85f)
    val quiet = line.copy(alpha = 0.3f)
    val backing = PrismColors.PictureBlack.copy(alpha = 0.72f)
    val labelStyle = PrismType.caption.copy(color = faint)
    val d = editor.ducking
    val keyName = editor.keys.firstOrNull { it.trackId == editor.keyTrackId }?.name ?: "侧链"
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = "闪避曲线".localized() }) {
        drawRect(PrismColors.PictureBlack)
        val pad = 8.dp.toPx()
        val split = size.height * KEY_SHARE
        val keyTop = pad
        val keyBottom = split - pad / 2
        val dipTop = split + pad / 2
        val dipBottom = size.height - pad
        drawLine(grid, Offset(0f, split), Offset(size.width, split), strokeWidth = 1.dp.toPx())
        fun keyY(db: Float) = keyBottom - ((db.coerceIn(LEVEL_FLOOR, 0f) - LEVEL_FLOOR) / -LEVEL_FLOOR) * (keyBottom - keyTop)
        fun dipY(db: Float) = dipTop + (db.coerceIn(0f, DIP_RANGE) / DIP_RANGE) * (dipBottom - dipTop)
        val levels = editor.keyLevels
        val amounts = editor.amounts
        if (levels.isEmpty() || amounts.isEmpty()) {
            val text = measurer.measure("开启闪避后显示侧链电平与压低曲线".localized(), labelStyle)
            drawText(text, topLeft = Offset((size.width - text.size.width) / 2, (size.height - text.size.height) / 2))
            return@Canvas
        }
        // The key: one bar per step, lit at or above the threshold.
        val step = size.width / (levels.size - 1).coerceAtLeast(1)
        levels.forEachIndexed { i, db ->
            if (db <= LEVEL_FLOOR) return@forEachIndexed
            val top = keyY(db)
            drawRect(
                if (db >= d.thresholdDb) heard else quiet,
                topLeft = Offset(i * step, top),
                size = Size((step - 1f).coerceAtLeast(1f), keyBottom - top),
            )
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val threshold = keyY(d.thresholdDb)
        drawLine(line, Offset(0f, threshold), Offset(size.width, threshold), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        // Labels on the right sit on a dark backing: the key's bars may run under them.
        fun label(text: String, y: Float, top: Float) {
            val laid = measurer.measure(text.localized(), labelStyle)
            val at = Offset(size.width - laid.size.width - pad, (y - laid.size.height - 2.dp.toPx()).coerceAtLeast(top))
            drawRoundRect(
                backing,
                topLeft = at - Offset(4.dp.toPx(), 0f),
                size = Size(laid.size.width + 8.dp.toPx(), laid.size.height.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(laid, topLeft = at)
        }
        label("阈值 ${formatValue(d.thresholdDb, 0, false, " dB")}", threshold, keyTop)
        drawText(measurer.measure(keyName.localized(), labelStyle), topLeft = Offset(pad, keyTop))
        // The dip: filled down from 0 dB to the curve.
        val depthY = dipY(d.depthDb)
        drawLine(faint, Offset(0f, depthY), Offset(size.width, depthY), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        val curve = Path()
        val area = Path().apply { moveTo(0f, dipTop) }
        val dipStep = size.width / (amounts.size - 1).coerceAtLeast(1)
        amounts.forEachIndexed { i, amount ->
            val x = i * dipStep
            val y = dipY(d.depthDb * amount)
            if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
            area.lineTo(x, y)
        }
        area.lineTo(size.width, dipTop)
        area.close()
        drawPath(area, accent.copy(alpha = 0.3f))
        drawPath(curve, accent, style = Stroke(2.dp.toPx()))
        drawText(measurer.measure("${editor.name} 增益".localized(), labelStyle), topLeft = Offset(pad, dipTop))
        label(formatValue(-d.depthDb, 0, false, " dB"), depthY, dipTop)
        // Seconds from the playhead, and the playhead.
        val half = (DuckingEditorUi.WINDOW_MS / 2000).toInt()
        drawText(measurer.measure("−$half 秒".localized(), labelStyle), topLeft = Offset(pad, dipBottom - 16.dp.toPx()))
        val after = measurer.measure("+$half 秒".localized(), labelStyle)
        drawText(after, topLeft = Offset(size.width - after.size.width - pad, dipBottom - 16.dp.toPx()))
        drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx())
    }
}

/** The key's lane takes this share of the height. */
private const val KEY_SHARE = 0.55f
private const val LEVEL_FLOOR = -60f
private val DIP_RANGE = DuckingControl.DEPTH.max
