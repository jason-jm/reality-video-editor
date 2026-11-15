package com.example.prismstudio.p40b43a88.ui.audio.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.prismstudio.p40b43a88.domain.model.EqBand
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * `eq-graph`: the EQ's response from 20 Hz to 20 kHz (logarithmic) over ±18 dB, as Fairlight draws
 * it, with a numbered handle per band. Dragging a handle moves its band: across for the frequency, up
 * and down for the gain (the low cut only moves across, and switches itself on).
 */
@Composable
fun EqGraph(eq: TrackEq, onDrag: (EqBand, Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(eq)
    val drag by rememberUpdatedState(onDrag)
    var active by remember { mutableStateOf<EqBand?>(null) }
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val accent = PicoTheme.colorScheme.interaction
    val grid = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.16f)
    val faint = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.55f)
    val labelStyle = PrismType.caption.copy(color = faint)
    val handleStyle = PrismType.caption.copy(color = PrismColors.PictureBlack)
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "均衡器曲线：拖动编号点调整频段".localized() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val axes = EqAxes(size.width.toFloat(), size.height.toFloat())
                    // The nearest handle within reach of the press.
                    val band = EqBand.entries
                        .map { it to axes.point(it.frequencyOf(current).toDouble(), it.gainOf(current).toDouble()) }
                        .minByOrNull { (_, p) -> hypot(p.x - down.position.x, p.y - down.position.y) }
                        ?.takeIf { (_, p) -> hypot(p.x - down.position.x, p.y - down.position.y) < 32.dp.toPx() }
                        ?.first ?: return@awaitEachGesture
                    active = band
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        drag(band, axes.hz(c.position.x).toFloat(), axes.db(c.position.y).toFloat())
                    }
                    active = null
                }
            },
    ) {
        val axes = EqAxes(size.width, size.height)
        drawRect(PrismColors.PictureBlack)
        for (hz in GRID_HZ) {
            val x = axes.x(hz.toDouble())
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        for (db in listOf(-12, -6, 0, 6, 12)) {
            val y = axes.y(db.toDouble())
            drawLine(if (db == 0) faint else grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        LABEL_HZ.forEach { (hz, text) -> label(measurer, text, labelStyle, Offset(axes.x(hz.toDouble()) + 4.dp.toPx(), size.height - 18.dp.toPx())) }
        listOf(12 to "+12", -12 to "−12").forEach { (db, text) -> label(measurer, text, labelStyle, Offset(4.dp.toPx(), axes.y(db.toDouble()) - 16.dp.toPx())) }
        // The response, 2 px per point.
        val path = Path()
        val steps = (size.width / 2f).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = axes.y(current.responseDb(axes.hz(x)))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, if (current.enabled) line else faint, style = Stroke(2.dp.toPx()))
        EqBand.entries.forEachIndexed { i, band ->
            val p = axes.point(band.frequencyOf(current).toDouble(), band.gainOf(current).toDouble())
            val off = band == EqBand.LOW_CUT && !current.lowCut
            val fill = when {
                band == active -> accent
                off -> faint
                else -> line
            }
            drawCircle(fill, radius = 11.dp.toPx(), center = p, alpha = if (off) 0.6f else 1f)
            val text = measurer.measure("${i + 1}", handleStyle)
            drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
        }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: androidx.compose.ui.text.TextStyle, at: Offset) {
    drawText(measurer.measure(text.localized(), style), topLeft = at)
}

/** Frequency (log) and gain axes of a graph [width] × [height]. */
private class EqAxes(private val width: Float, private val height: Float) {
    private val span = ln(TrackEq.MAX_HZ / TrackEq.MIN_HZ)

    fun x(hz: Double): Float = (ln(hz / TrackEq.MIN_HZ) / span * width).toFloat()
    fun hz(x: Float): Double = TrackEq.MIN_HZ * (TrackEq.MAX_HZ / TrackEq.MIN_HZ).pow((x / width).toDouble().coerceIn(0.0, 1.0))
    fun y(db: Double): Float = (height / 2f - db.coerceIn(-RANGE_DB, RANGE_DB) / RANGE_DB * height / 2f).toFloat()
    fun db(y: Float): Double = ((height / 2f - y) / (height / 2f) * RANGE_DB).coerceIn(-RANGE_DB, RANGE_DB)
    fun point(hz: Double, db: Double): Offset = Offset(x(hz), y(db))

    private companion object {
        const val RANGE_DB = 18.0
    }
}

private val GRID_HZ = listOf(50, 100, 200, 500, 1000, 2000, 5000, 10000)
private val LABEL_HZ = listOf(100 to "100", 1000 to "1k", 10000 to "10k")
