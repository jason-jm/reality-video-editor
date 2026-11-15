package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.pico.spatial.ui.design.PicoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A window (a grade's or a clip's mask) as the monitor shows it: through [transform], mirrored or not, at the playhead. */
data class WindowOverlayUi(val window: PowerWindow, val transform: ClipTransform, val mirrored: Boolean)

/**
 * The `window-overlay` renderer (and the Edit monitor's `mask-overlay`): the power window over the monitor's picture — its edge (solid), the
 * inner and outer ends of its soft edge (dashed) and its centre — placed through the clip's transform
 * at the playhead, as the shader places the picture. Dragging on the picture moves the window.
 */
@Composable
fun BoxScope.WindowOverlay(overlay: WindowOverlayUi, pictureAspect: Float, label: String = "窗口：拖动以移动", onMove: (Float, Float) -> Unit) {
    val current by rememberUpdatedState(overlay)
    val move by rememberUpdatedState(onMove)
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val shade = line.copy(alpha = 0.6f)
    Canvas(
        Modifier
            .matchParentSize()
            .semantics { contentDescription = label.localized() }
            .pointerInput(pictureAspect) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if (!dragging && (c.position - down.position).getDistance() < viewConfiguration.touchSlop) continue
                        dragging = true
                        c.consume()
                        val (dx, dy) = PictureMapping(current.transform, current.mirrored, pictureAspect, size.height.toFloat())
                            .toWindowDelta(c.position - last)
                        last = c.position
                        move(dx, dy)
                    }
                }
            },
    ) {
        val map = PictureMapping(current.transform, current.mirrored, pictureAspect, size.height)
        val w = current.window
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        clipRect {
            when (w.shape) {
                WindowShape.GRADIENT -> {
                    // The fade's middle and its two ends, across the picture.
                    for ((offset, solid) in listOf(0f to true, -w.softness to false, w.softness to false)) {
                        if (!solid && w.softness <= 0f) continue
                        drawPath(map.path(w, listOf(-8f to offset, 8f to offset)), if (solid) line else shade,
                            style = Stroke(if (solid) 2.dp.toPx() else 1.5.dp.toPx(), pathEffect = if (solid) null else dash))
                    }
                    // The side the window covers.
                    val toward = if (w.invert) -0.25f else 0.25f
                    drawPath(map.path(w, listOf(0f to 0f, 0f to toward)), line, style = Stroke(2.dp.toPx()))
                }
                else -> {
                    for ((scale, solid) in listOf(1f to true, 1f - w.softness to false, 1f + w.softness to false)) {
                        if (!solid && (w.softness <= 0f || scale <= 0f)) continue
                        drawPath(map.path(w, edge(w.shape, scale * w.size * w.aspect, scale * w.size), closed = true), if (solid) line else shade,
                            style = Stroke(if (solid) 2.dp.toPx() else 1.5.dp.toPx(), pathEffect = if (solid) null else dash))
                    }
                }
            }
            drawCircle(line, radius = 6.dp.toPx(), center = map.toScreen(w, 0f, 0f), style = Stroke(2.dp.toPx()))
        }
    }
}

/** The outline of a circle (ellipse) or rectangle with half-extents [halfWidth] × [halfHeight], in window axes. */
private fun edge(shape: WindowShape, halfWidth: Float, halfHeight: Float): List<Pair<Float, Float>> =
    if (shape == WindowShape.RECTANGLE) {
        listOf(-halfWidth to -halfHeight, halfWidth to -halfHeight, halfWidth to halfHeight, -halfWidth to halfHeight)
    } else {
        List(64) { i ->
            val t = 2 * PI.toFloat() * i / 64
            halfWidth * cos(t) to halfHeight * sin(t)
        }
    }

/**
 * Window points to monitor pixels and back: window axes → picture half-heights (y up) → the clip's
 * transform (mirror, scale, rotation, position, as ClipLookEffect samples) → the picture box.
 */
internal class PictureMapping(transform: ClipTransform, private val mirrored: Boolean, private val aspect: Float, private val pictureHeightPx: Float) {
    private val scale = (transform.scalePercent / 100f).coerceAtLeast(0.01f)
    private val rotation = transform.rotationDegrees * PI.toFloat() / 180f
    // Position offsets in frame heights (authored on a 1920 × 1080 raster).
    private val offsetX = transform.positionX / 1920f * aspect
    private val offsetY = transform.positionY / 1080f

    /** The monitor pixel of the point ([u], [v]) in the window's own axes (picture half-heights). */
    fun toScreen(w: PowerWindow, u: Float, v: Float): Offset {
        val a = w.angle * PI.toFloat() / 180f
        // Window axes → picture, in frame heights from the picture centre.
        var x = (w.centerX * aspect + u * cos(a) - v * sin(a)) / 2f
        val y = (w.centerY + u * sin(a) + v * cos(a)) / 2f
        if (mirrored) x = -x
        // The picture as the clip shows it: rotate by −rotation, scale, then offset.
        val c = cos(rotation)
        val s = sin(rotation)
        val ox = offsetX + scale * (c * x + s * y)
        val oy = offsetY + scale * (-s * x + c * y)
        return Offset(pictureHeightPx * aspect / 2f + ox * pictureHeightPx, pictureHeightPx / 2f - oy * pictureHeightPx)
    }

    fun path(w: PowerWindow, points: List<Pair<Float, Float>>, closed: Boolean = false): Path = Path().apply {
        points.forEachIndexed { i, (u, v) ->
            val p = toScreen(w, u, v)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }

    /**
     * The point of the clip's own picture (0…1 from its left / bottom edge, before the transform) that
     * shows at the monitor pixel [screen] — the inverse of [toScreen]'s placement. May fall outside 0…1.
     */
    fun toPicture(screen: Offset): Offset {
        val ox = (screen.x - pictureHeightPx * aspect / 2f) / pictureHeightPx - offsetX
        val oy = (pictureHeightPx / 2f - screen.y) / pictureHeightPx - offsetY
        val c = cos(rotation)
        val s = sin(rotation)
        var x = (c * ox - s * oy) / scale
        val y = (s * ox + c * oy) / scale
        if (mirrored) x = -x
        return Offset(x / aspect + 0.5f, y + 0.5f)
    }

    /** A monitor drag [delta] (pixels) as a move of the window centre (window units). */
    fun toWindowDelta(delta: Offset): Pair<Float, Float> {
        val ox = delta.x / pictureHeightPx / scale
        val oy = -delta.y / pictureHeightPx / scale
        val c = cos(rotation)
        val s = sin(rotation)
        var x = c * ox - s * oy
        val y = s * ox + c * oy
        if (mirrored) x = -x
        return (2f * x / aspect) to (2f * y)
    }
}
