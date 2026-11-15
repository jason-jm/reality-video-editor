package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ColorWarper
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * `color-warper-panel`: Resolve's Color Warper (hue–saturation) — the chroma wheel with its grid of six spokes and three rings;
 * a point dragged round the wheel turns the colours near it, in or out changes their saturation, the grid bending to show it.
 * Beside it the picked point's two moves as sliders, 重置此点 and 全部重置.
 */
@Composable
fun ColorWarperPanel(
    warper: ColorWarper,
    spoke: Int,
    ring: Int,
    enabled: Boolean,
    onSelect: (spoke: Int, ring: Int) -> Unit,
    onMove: (spoke: Int, ring: Int, hue: Float, sat: Float) -> Unit,
    onResetPoint: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val move = warper.move(spoke, ring)
    val hue = move?.hue ?: 0f
    val sat = move?.sat ?: 0f
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        WarpWheel(warper, spoke, ring, enabled, onSelect, onMove, Modifier.fillMaxHeight().aspectRatio(1f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // `warper-point-title`
            Text("${ColorWarper.SPOKE_NAMES[spoke]} · ${ColorWarper.RING_NAMES[ring]}", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            ValueSliderRow(
                "色相", hue, -ColorWarper.MAX_HUE..ColorWarper.MAX_HUE, formatValue(hue, 0, true, "°"), { onMove(spoke, ring, it, sat) },
                enabled = enabled, step = 1f, labelWidth = 56.dp, valueWidth = 56.dp,
            )
            ValueSliderRow(
                "饱和度", sat * 100f, -ColorWarper.MAX_SAT * 100f..ColorWarper.MAX_SAT * 100f, formatValue(sat * 100f, 0, true),
                { onMove(spoke, ring, hue, it / 100f) },
                enabled = enabled, step = 1f, labelWidth = 56.dp, valueWidth = 56.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonChip(label = { Text("重置此点") }, onClick = onResetPoint, enabled = enabled && move != null, chipSize = ChipsDefaults.Small)
                ButtonChip(label = { Text("全部重置") }, onClick = onResetAll, enabled = enabled && !warper.isIdentity, chipSize = ChipsDefaults.Small)
            }
            // `warper-hint`
            Text(
                "拖动网格上的点：沿圆周改变附近颜色的色相，向内外改变饱和度；亮度不变，灰色不受影响。",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
            )
        }
    }
}

/** `warper-wheel`: the chroma plane, its warped grid and points; a tap picks the nearest point, a drag moves it. */
@Composable
private fun WarpWheel(
    warper: ColorWarper,
    spoke: Int,
    ring: Int,
    enabled: Boolean,
    onSelect: (Int, Int) -> Unit,
    onMove: (Int, Int, Float, Float) -> Unit,
    modifier: Modifier,
) {
    val current by rememberUpdatedState(warper)
    val select by rememberUpdatedState(onSelect)
    val moveTo by rememberUpdatedState(onMove)
    Canvas(
        modifier
            .semantics { contentDescription = "色彩扭曲器网格：拖动点改变色相和饱和度".localized() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // One gesture: the press picks the nearest point (within reach), moving the pointer carries it. A single
                // handler, so no detector waits on another's touch slop or consumption.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val picked = nearest(current, down.position, w, h) ?: return@awaitEachGesture
                    select(picked.first, picked.second)
                    down.consume()
                    val slop = viewConfiguration.touchSlop / 4f
                    var moving = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!moving && (change.position - down.position).getDistance() < slop) continue
                        moving = true
                        change.consume()
                        val (deg, radius) = polar(change.position, w, h)
                        var dh = deg - picked.first * ColorWarper.SPOKE_DEGREES
                        while (dh > 180f) dh -= 360f
                        while (dh < -180f) dh += 360f
                        moveTo(picked.first, picked.second, dh, radius - (picked.second + 1f) / ColorWarper.RINGS)
                    }
                }
            },
    ) {
        val c = center
        val outer = size.minDimension / 2f - WHEEL_MARGIN_PX(size.width, size.height)
        // The hues round the wheel (red to the right, going anticlockwise as the chroma plane does), fading to grey at its centre.
        // The sweep runs clockwise on screen from 3 o'clock: hue 360 − angle, as the grading wheels draw it.
        val hues = (0..6).map { i -> Color.hsv(((360 - i * 60) % 360).toFloat(), 0.7f, 0.9f) }
        drawCircle(Brush.sweepGradient(hues, c), outer, c, alpha = if (enabled) 0.9f else 0.4f)
        val grey = Color.hsv(0f, 0f, 0.54f)
        drawCircle(Brush.radialGradient(listOf(grey, grey.copy(alpha = 0f)), c, outer), outer, c)
        fun point(s: Int, r: Int): Offset {
            val m = warper.move(s, r)
            val deg = s * ColorWarper.SPOKE_DEGREES + (m?.hue ?: 0f)
            val rad = Math.toRadians(deg.toDouble())
            val radius = ((r + 1f) / ColorWarper.RINGS + (m?.sat ?: 0f)).coerceAtLeast(0f) * outer
            return Offset(c.x + (radius * cos(rad)).toFloat(), c.y - (radius * sin(rad)).toFloat())
        }
        val line = Color.White.copy(alpha = 0.7f)
        for (r in 0 until ColorWarper.RINGS) {
            val path = Path()
            for (s in 0..ColorWarper.SPOKES) {
                val p = point(s % ColorWarper.SPOKES, r)
                if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, line, style = Stroke(width = 1.5f.dp.toPx()))
        }
        for (s in 0 until ColorWarper.SPOKES) {
            var from = c
            for (r in 0 until ColorWarper.RINGS) {
                val p = point(s, r)
                drawLine(line, from, p, strokeWidth = 1.5f.dp.toPx())
                from = p
            }
        }
        for (s in 0 until ColorWarper.SPOKES) for (r in 0 until ColorWarper.RINGS) {
            val p = point(s, r)
            val picked = s == spoke && r == ring
            val moved = warper.move(s, r) != null
            drawCircle(Color.Black.copy(alpha = 0.35f), (if (picked) 9f else 6f).dp.toPx() + 1.5f.dp.toPx(), p)
            drawCircle(if (moved || picked) Color.White else Color.White.copy(alpha = 0.75f), (if (picked) 9f else 6f).dp.toPx(), p)
        }
    }
}

/** A point on the canvas as chroma angle (degrees, red 0, anticlockwise) and radius (1 = the outer ring). */
private fun polar(p: Offset, w: Float, h: Float): Pair<Float, Float> {
    val outer = minOf(w, h) / 2f - WHEEL_MARGIN_PX(w, h)
    val dx = p.x - w / 2f
    val dy = h / 2f - p.y
    var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (deg < 0f) deg += 360f
    return deg to hypot(dx, dy) / outer
}

/** The grid point nearest [p] within reach (a quarter of the wheel's radius), as (spoke, ring). */
private fun nearest(warper: ColorWarper, p: Offset, w: Float, h: Float): Pair<Int, Int>? {
    val outer = minOf(w, h) / 2f - WHEEL_MARGIN_PX(w, h)
    var best: Pair<Int, Int>? = null
    var bestD = outer * 0.25f
    for (s in 0 until ColorWarper.SPOKES) for (r in 0 until ColorWarper.RINGS) {
        val m = warper.move(s, r)
        val rad = Math.toRadians((s * ColorWarper.SPOKE_DEGREES + (m?.hue ?: 0f)).toDouble())
        val radius = ((r + 1f) / ColorWarper.RINGS + (m?.sat ?: 0f)) * outer
        val d = hypot(p.x - (w / 2f + radius * cos(rad)).toFloat(), p.y - (h / 2f - radius * sin(rad)).toFloat())
        if (d < bestD) { bestD = d; best = s to r }
    }
    return best
}

/** The wheel's margin inside its square: a twentieth of the shorter side (room for the points on the outer ring). */
@Suppress("FunctionName")
private fun WHEEL_MARGIN_PX(w: Float, h: Float): Float = minOf(w, h) * 0.05f
