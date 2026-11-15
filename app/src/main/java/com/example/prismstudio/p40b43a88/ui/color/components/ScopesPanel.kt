package com.example.prismstudio.p40b43a88.ui.color.components

import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.ScopeData
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** `scopes-panel`: scope picker and the `video-scope` display. */
@Composable
fun ScopesPanel(mode: ScopeMode, scope: ScopeData?, onMode: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentControl(Modifier.fillMaxWidth()) {
            ScopeMode.entries.forEachIndexed { index, entry ->
                SegmentItem(selected = entry == mode, onClick = { onMode(index) }, title = { Text(entry.label, maxLines = 1) }, modifier = Modifier.weight(1f))
            }
        }
        ScopeDisplay(mode, scope, Modifier.weight(1f).fillMaxWidth())
    }
}

/** The `video-scope` renderer on a 0–100 IRE graticule. */
@Composable
fun ScopeDisplay(mode: ScopeMode, scope: ScopeData?, modifier: Modifier = Modifier) {
    val graticule = PicoTheme.colorScheme.labelQuaternary
    val measurer = rememberTextMeasurer()
    val labelStyle = PrismType.timecodeSmall.copy(color = PicoTheme.colorScheme.labelTertiary)
    val image = remember(scope) { scope?.let(::scopeImage) }
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "示波器：${mode.label}".localized() },
    ) {
        drawRect(PrismColors.PictureBlack)
        val pad = 28.dp.toPx()
        val plot = Size(size.width - pad - 12.dp.toPx(), size.height - 2 * 12.dp.toPx())
        val origin = Offset(pad, 12.dp.toPx())
        when (mode) {
            ScopeMode.WAVEFORM, ScopeMode.PARADE -> {
                listOf(0, 25, 50, 75, 100).forEach { ire ->
                    val y = origin.y + plot.height * (1f - ire / 100f)
                    drawLine(graticule, Offset(origin.x, y), Offset(origin.x + plot.width, y), strokeWidth = 1f)
                    drawText(measurer, "$ire", topLeft = Offset(4.dp.toPx(), y - 8.dp.toPx()), style = labelStyle)
                }
                image?.let { drawScaled(it, origin, plot) }
            }
            ScopeMode.VECTORSCOPE -> drawVectorscope(image, graticule)
            ScopeMode.HISTOGRAM -> {
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                    val x = origin.x + plot.width * f
                    drawLine(graticule, Offset(x, origin.y), Offset(x, origin.y + plot.height), strokeWidth = 1f)
                }
                scope?.takeIf { it.mode == ScopeMode.HISTOGRAM }?.let { drawHistogram(it, origin, plot) }
            }
        }
        if (scope == null) {
            drawText(measurer, "暂无画面".localized(), topLeft = Offset(size.width / 2f - 28.dp.toPx(), size.height / 2f - 8.dp.toPx()), style = labelStyle)
        }
    }
}

private fun DrawScope.drawScaled(image: ImageBitmap, origin: Offset, plot: Size) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
        dstSize = IntSize(plot.width.roundToInt(), plot.height.roundToInt()),
    )
}

private fun DrawScope.drawVectorscope(image: ImageBitmap?, graticule: Color) {
    val side = minOf(size.width, size.height) - 24.dp.toPx()
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = side / 2f
    drawCircle(graticule, radius = radius, center = center, style = Stroke(1f))
    drawCircle(graticule, radius = radius * 0.5f, center = center, style = Stroke(1f))
    drawLine(graticule, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 1f)
    drawLine(graticule, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 1f)
    // 75 % colour targets (R, Mg, B, Cy, G, Yl) in Cb/Cr space.
    listOf(
        floatArrayOf(0.75f, 0f, 0f), floatArrayOf(0.75f, 0f, 0.75f), floatArrayOf(0f, 0f, 0.75f),
        floatArrayOf(0f, 0.75f, 0.75f), floatArrayOf(0f, 0.75f, 0f), floatArrayOf(0.75f, 0.75f, 0f),
    ).forEach { rgb ->
        val y = 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]
        val cb = (rgb[2] - y) / 1.8556f
        val cr = (rgb[0] - y) / 1.5748f
        val p = Offset(center.x + cb * 2f * radius, center.y - cr * 2f * radius)
        val box = 8.dp.toPx()
        drawRect(graticule, topLeft = Offset(p.x - box / 2, p.y - box / 2), size = Size(box, box), style = Stroke(1.5f))
    }
    // Skin-tone line (≈123°).
    val angle = (123.0 * PI / 180.0).toFloat()
    drawLine(PrismColors.MarkerFlag, center, Offset(center.x + cos(angle) * radius, center.y - sin(angle) * radius), strokeWidth = 1.5f, alpha = 0.8f)
    image?.let {
        drawImage(
            it,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(it.width, it.height),
            dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
        )
    }
}

private fun DrawScope.drawHistogram(scope: ScopeData, origin: Offset, plot: Size) {
    val colors = listOf(PrismColors.ScopeRed, PrismColors.ScopeGreen, PrismColors.ScopeBlue)
    scope.planes.forEachIndexed { index, plane ->
        val path = Path().apply {
            moveTo(origin.x, origin.y + plot.height)
            plane.forEachIndexed { i, v ->
                lineTo(origin.x + plot.width * i / (plane.size - 1).coerceAtLeast(1), origin.y + plot.height * (1f - v))
            }
            lineTo(origin.x + plot.width, origin.y + plot.height)
            close()
        }
        drawPath(path, colors[index % 3], alpha = 0.35f)
        drawPath(path, colors[index % 3], style = Stroke(1.5f), alpha = 0.9f)
    }
}

/** Renders density planes into an image: luma white-green, parade R | G | B, vectorscope luma. */
private fun scopeImage(scope: ScopeData): ImageBitmap? {
    val planes = scope.planes
    if (planes.isEmpty() || scope.mode == ScopeMode.HISTOGRAM) return null
    val parade = scope.mode == ScopeMode.PARADE
    val width = scope.columns * if (parade) planes.size else 1
    val pixels = IntArray(width * scope.rows)
    val tints = if (parade) listOf(PrismColors.ScopeRed, PrismColors.ScopeGreen, PrismColors.ScopeBlue) else listOf(PrismColors.ScopeLuma)
    planes.forEachIndexed { p, plane ->
        val tint = tints[p % tints.size].toArgb()
        val r = (tint shr 16) and 0xFF
        val g = (tint shr 8) and 0xFF
        val b = tint and 0xFF
        for (row in 0 until scope.rows) for (col in 0 until scope.columns) {
            val v = plane[row * scope.columns + col]
            if (v <= 0f) continue
            val a = (40 + v * 215).roundToInt().coerceIn(0, 255)
            pixels[row * width + p * scope.columns + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(pixels, width, scope.rows, Bitmap.Config.ARGB_8888).asImageBitmap()
}
