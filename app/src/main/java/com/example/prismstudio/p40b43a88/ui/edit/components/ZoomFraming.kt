package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.pico.spatial.ui.design.PicoTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The part of the picture Dynamic Zoom shows on the clip's first frame ([last] false) or its last, as
 * [left, top, right, bottom] shares of the picture from its top-left corner: scaled by z about the anchor, the
 * frame sees 1/z of the picture, its centre (z − 1)/z of the way from the middle to the anchor.
 */
internal fun zoomFrame(zoom: DynamicZoom, last: Boolean): FloatArray {
    val frame = if (last) 1f else 0f
    val z = zoom.scaleAt(frame, 2)
    val cx = (z - 1f) * zoom.anchorXAt(frame, 2) * 0.5f / z
    val cy = (z - 1f) * zoom.anchorYAt(frame, 2) * 0.5f / z
    val half = 0.5f / z
    return floatArrayOf(0.5f + cx - half, 0.5f - cy - half, 0.5f + cx + half, 0.5f - cy + half)
}

/** Dynamic Zoom's move in words: where it heads and by how much. */
internal fun zoomMotionText(zoom: DynamicZoom): String {
    val to = place(zoom.focusX.sign, zoom.focusY.sign)
    val by = "放大 ${zoom.amountPercent}%"
    return when (zoom.direction) {
        ZoomDirection.IN -> "从全画面推近到$to，$by"
        ZoomDirection.OUT -> "从$to（$by）拉远到全画面"
        ZoomDirection.PAN ->
            if (!zoom.moves) "$by，水平和垂直都在中间时画面不移动"
            else "$by，从${place(-zoom.focusX.sign, -zoom.focusY.sign)}平移到$to"
    }
}

private fun place(x: Int, y: Int): String = when {
    x == 0 && y == 0 -> "中心"
    x == 0 -> if (y > 0) "上方" else "下方"
    y == 0 -> if (x > 0) "右侧" else "左侧"
    else -> (if (y > 0) "上" else "下").let { v -> (if (x > 0) "右" else "左") + v + "角" }
}

/**
 * `zoom-framing-diagram`: Resolve's Dynamic Zoom boxes over the picture — green the first frame's framing, red the
 * last's — with the way the frame travels between them.
 */
@Composable
fun ZoomFramingDiagram(zoom: DynamicZoom, modifier: Modifier = Modifier) {
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val grid = line.copy(alpha = 0.14f)
    val arrow = line.copy(alpha = 0.8f)
    val start = zoomFrame(zoom, last = false)
    val end = zoomFrame(zoom, last = true)
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).semantics { contentDescription = ("动态缩放起止画面：" + zoomMotionText(zoom)).localized() }) {
        drawRect(PrismColors.PictureBlack)
        for (i in 1..2) {
            drawLine(grid, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), strokeWidth = 1.dp.toPx())
        }
        val stroke = 2.dp.toPx()
        // Start under end: a push from the full picture leaves the green box on the edge, the red one inside it.
        box(start, PrismColors.MarkerGreen, stroke)
        box(end, PrismColors.MarkerRed, stroke)
        val from = Offset((start[0] + start[2]) / 2 * size.width, (start[1] + start[3]) / 2 * size.height)
        val to = Offset((end[0] + end[2]) / 2 * size.width, (end[1] + end[3]) / 2 * size.height)
        val length = hypot(to.x - from.x, to.y - from.y)
        if (length > 6.dp.toPx()) {
            drawLine(arrow, from, to, strokeWidth = 1.5.dp.toPx())
            val angle = atan2(to.y - from.y, to.x - from.x)
            val head = 6.dp.toPx()
            for (side in listOf(-1, 1)) {
                val a = angle + Math.PI.toFloat() + side * HEAD_ANGLE
                drawLine(arrow, to, Offset(to.x + head * cos(a), to.y + head * sin(a)), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

/** A framing box drawn just inside its edges so a full-picture one stays visible. */
private fun DrawScope.box(frame: FloatArray, color: Color, stroke: Float) {
    val inset = stroke / 2
    val left = (frame[0] * size.width).coerceAtLeast(inset)
    val top = (frame[1] * size.height).coerceAtLeast(inset)
    val right = (frame[2] * size.width).coerceAtMost(size.width - inset)
    val bottom = (frame[3] * size.height).coerceAtMost(size.height - inset)
    drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top), style = Stroke(stroke))
}

/** Half the opening of the arrowhead, in radians. */
private const val HEAD_ANGLE = 0.45f
