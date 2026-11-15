package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.GradeWheel
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import com.example.prismstudio.p40b43a88.ui.components.TrackingSlider
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SliderDefaults
import com.example.prismstudio.p40b43a88.ui.components.Text
import java.util.Locale
import kotlin.math.hypot

/** `wheels-row`: Lift / Gamma / Gain (/ Offset) groups. */
@Composable
fun WheelsRow(
    grade: ColorGrade,
    readouts: Map<GradeWheel, String>,
    showOffset: Boolean,
    enabled: Boolean,
    onBalance: (GradeWheel, Float, Float) -> Unit,
    onMaster: (GradeWheel, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        GradeWheel.entries.filter { showOffset || it != GradeWheel.OFFSET }.forEach { wheel ->
            Column(
                modifier = Modifier.width(168.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(wheel.label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, textAlign = TextAlign.Center)
                ColorWheel(
                    value = grade.wheel(wheel),
                    label = wheel.label,
                    enabled = enabled,
                    onChange = { x, y -> onBalance(wheel, x, y) },
                    modifier = Modifier.size(152.dp),
                )
                TrackingSlider(
                    value = grade.wheel(wheel).master,
                    onValueChange = { onMaster(wheel, it) },
                    valueRange = -1f..1f,
                    enabled = enabled,
                    sliderSpec = SliderDefaults.Small,
                    modifier = Modifier.width(152.dp).semantics { contentDescription = "${wheel.label} 亮度".localized() },
                )
                Text(
                    readouts[wheel].orEmpty(),
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The `color-wheel` renderer: a hue ring with crosshair; drag the puck (56 dp hit area) toward a
 * hue — distance from the centre is strength. Double-tap re-centres the puck.
 */
@Composable
fun ColorWheel(
    value: WheelValue,
    label: String,
    enabled: Boolean,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val crosshair = PicoTheme.colorScheme.labelQuaternary
    val puck = PicoTheme.colorScheme.labelPrimaryLight
    val ring = remember {
        // Screen angles run clockwise; balance angles are counter-clockwise from red at 0°.
        Brush.sweepGradient((0..12).map { i -> Color.hsv(((360 - i * 30) % 360).toFloat(), 0.85f, 0.95f) })
    }
    Canvas(
        modifier
            .semantics {
                contentDescription = label.localized()
                stateDescription = String.format(Locale.US, "x %.2f, y %.2f", value.x, value.y)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { change(0f, 0f) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val radius = size.width / 2f - 14.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    fun emit(p: Offset) {
                        var x = (p.x - center.x) / radius * WheelValue.MAX_BALANCE
                        var y = -(p.y - center.y) / radius * WheelValue.MAX_BALANCE
                        val r = hypot(x, y)
                        if (r > WheelValue.MAX_BALANCE) {
                            x *= WheelValue.MAX_BALANCE / r
                            y *= WheelValue.MAX_BALANCE / r
                        }
                        change(x, y)
                    }
                    val puckAt = Offset(center.x + value.x / WheelValue.MAX_BALANCE * radius, center.y - value.y / WheelValue.MAX_BALANCE * radius)
                    val grabbed = (down.position - puckAt).getDistance() <= 28.dp.toPx()
                    val grabOffset = if (grabbed) puckAt - down.position else Offset.Zero
                    if (!grabbed) emit(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        emit(c.position + grabOffset)
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.width / 2f
        val ringWidth = 10.dp.toPx()
        drawCircle(ring, radius = outer - ringWidth / 2f, center = center, style = Stroke(ringWidth), alpha = 0.9f)
        val inner = outer - 14.dp.toPx()
        drawLine(crosshair, Offset(center.x - inner, center.y), Offset(center.x + inner, center.y), strokeWidth = 1f)
        drawLine(crosshair, Offset(center.x, center.y - inner), Offset(center.x, center.y + inner), strokeWidth = 1f)
        drawCircle(crosshair, radius = inner, center = center, style = Stroke(1f))
        val p = Offset(center.x + value.x / WheelValue.MAX_BALANCE * inner, center.y - value.y / WheelValue.MAX_BALANCE * inner)
        drawCircle(puck, radius = 12.dp.toPx(), center = p, alpha = if (enabled) 1f else 0.4f)
        drawCircle(crosshair, radius = 12.dp.toPx(), center = p, style = Stroke(1.5f))
    }
}
