package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.StabilizationUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import kotlin.math.abs

/**
 * `stabilize-section`: Resolve's Stabilization — the camera's shake taken out of the footage. Switched on, the clip's file
 * is analysed in the background (the status says how far); 平滑 sets how long a stretch of the camera's path is averaged
 * into the steady one, 强度 how much of the shake goes, 放大 enlarges the picture just enough to hide the moving edges.
 * The diagram draws the camera's path across the clip, thin, and the steadied one the picture follows, thick.
 */
@Composable
internal fun StabilizationSection(stab: StabilizationUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    val can = enabled && stab.unavailable == null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("稳定", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text(stab.unavailable ?: "分析摄像机的抖动，逐帧反向移动画面去掉它", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        Switch(
            checked = stab.on,
            onCheckedChange = { onEvent(EditEvent.StabilizeOn(it)) },
            enabled = can,
            modifier = Modifier.semantics { contentDescription = "稳定".localized() },
        )
    }
    if (!stab.on || stab.unavailable != null) return
    ValueSliderRow("平滑", stab.smooth.toFloat(), 0f..100f, "${stab.smooth}", { onEvent(EditEvent.StabilizeSmooth(it)) }, enabled = can, step = 1f, labelWidth = 56.dp)
    ValueSliderRow("强度", stab.strength.toFloat(), 0f..100f, "${stab.strength}%", { onEvent(EditEvent.StabilizeStrength(it)) }, enabled = can, step = 1f, labelWidth = 56.dp)
    // `stabilize-zoom-row`: the zoom that hides the edges the moving frame would show.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("放大", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(56.dp), fit = true)
        Switch(
            checked = stab.zoom,
            onCheckedChange = { onEvent(EditEvent.StabilizeZoom(it)) },
            enabled = can,
            modifier = Modifier.semantics { contentDescription = "稳定时放大遮住边缘".localized() },
        )
        Text("遮住画面移动时露出的边缘", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
        Text(stab.zoomLabel, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StabilizationDiagram(stab, Modifier.width(112.dp).height(63.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // `stabilize-status-text`
            Text(stab.status, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            Text("细线是摄像机的路径，粗线是稳定后画面走的路径；上为水平，下为垂直", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            if (stab.failed) {
                Button(
                    onClick = { onEvent(EditEvent.RetryStabilize) },
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                    modifier = Modifier.semantics { contentDescription = "重新分析摄像机运动".localized() },
                ) { Text("重新分析") }
            }
        }
    }
}

/**
 * `stabilize-path`: across the clip, the camera's path (thin) and the steadied path (thick, cyan) — horizontal above the
 * middle line, vertical below — each half scaled to its own swing. Empty but for the line until the file is analysed.
 */
@Composable
internal fun StabilizationDiagram(stab: StabilizationUi, modifier: Modifier = Modifier) {
    val camera = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.5f)
    val grid = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.14f)
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).semantics { contentDescription = ("稳定路径：" + stab.status).localized() }) {
        drawRect(PrismColors.PictureBlack)
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        val halfHeight = size.height / 2
        plot(stab.cameraX, stab.steadyX, 0f, halfHeight, camera)
        plot(stab.cameraY, stab.steadyY, halfHeight, halfHeight, camera)
    }
}

/** One component's camera path (thin) and steadied path (thick) in the band from [top], [height] tall. */
private fun DrawScope.plot(camera: List<Float>, steady: List<Float>, top: Float, height: Float, cameraColor: Color) {
    if (camera.size < 2 || steady.size != camera.size) return
    val mid = (camera.max() + camera.min()) / 2
    val swing = maxOf(camera.maxOf { abs(it - mid) }, steady.maxOf { abs(it - mid) }, MIN_SWING)
    val pad = 4.dp.toPx()
    val scale = (height / 2 - pad) / swing
    fun line(values: List<Float>, color: Color, width: Float) {
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = top + height / 2 + (v - mid) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width))
    }
    line(camera, cameraColor, 1.dp.toPx())
    line(steady, PrismColors.MarkerCyan, 2.dp.toPx())
}

/** The least swing a band is scaled to (a share of the picture), so a steady shot does not draw its noise large. */
private const val MIN_SWING = 0.004f
