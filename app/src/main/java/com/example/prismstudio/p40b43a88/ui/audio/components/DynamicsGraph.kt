package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme

/**
 * `dynamics-graph`: the dynamics' transfer curve, as Fairlight draws it — input level across, output
 * level up, both −60…0 dB, the unity line faint behind it. The knee sits at the threshold; the limiter's
 * ceiling is a dashed line; the dot is what the track plays at the playhead.
 */
@Composable
fun DynamicsGraph(dynamics: TrackDynamics, inputDb: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val accent = PicoTheme.colorScheme.interaction
    val grid = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.16f)
    val faint = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.55f)
    val labelStyle = PrismType.caption.copy(color = faint)
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = "动态传输曲线".localized() }) {
        fun x(db: Double) = ((db - FLOOR) / -FLOOR * size.width).toFloat()
        fun y(db: Double) = (size.height - (db.coerceIn(FLOOR, 0.0) - FLOOR) / -FLOOR * size.height).toFloat()
        drawRect(PrismColors.PictureBlack)
        for (db in -48..-12 step 12) {
            drawLine(grid, Offset(x(db.toDouble()), 0f), Offset(x(db.toDouble()), size.height), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, y(db.toDouble())), Offset(size.width, y(db.toDouble())), strokeWidth = 1.dp.toPx())
        }
        drawLine(grid, Offset(x(FLOOR), y(FLOOR)), Offset(x(0.0), y(0.0)), strokeWidth = 1.dp.toPx())
        listOf(-48 to "−48", -24 to "−24").forEach { (db, text) ->
            drawText(measurer.measure(text, labelStyle), topLeft = Offset(x(db.toDouble()) + 4.dp.toPx(), size.height - 18.dp.toPx()))
        }
        drawText(measurer.measure("0 dB", labelStyle), topLeft = Offset(size.width - 34.dp.toPx(), 4.dp.toPx()))
        if (dynamics.limiter && dynamics.enabled) {
            val ceiling = y(dynamics.ceilingDb.toDouble())
            drawLine(
                faint, Offset(0f, ceiling), Offset(size.width, ceiling),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        // The curve, 2 px per point.
        val path = Path()
        val steps = (size.width / 2f).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val inDb = FLOOR - FLOOR * i / steps
            val px = size.width * i / steps
            val py = y(dynamics.outputDb(inDb))
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, if (dynamics.isActive) line else faint, style = Stroke(2.dp.toPx()))
        if (dynamics.compressor && dynamics.enabled) {
            val knee = dynamics.thresholdDb.toDouble()
            drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(knee), y(dynamics.outputDb(knee))))
        }
        if (inputDb > FLOOR) {
            val at = inputDb.toDouble().coerceAtMost(0.0)
            drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x(at), y(dynamics.outputDb(at))))
        }
    }
}

private const val FLOOR = -60.0
