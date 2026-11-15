package com.example.prismstudio.p40b43a88.ui.workspace.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * The `jog-dial` renderer. Dragging moves the playhead one frame per 12 dp of travel (geared so
 * hand tremor never skips frames); holding the pointer past either end shuttles at 2×, 4× or 8×
 * real time depending on how far past the end it is.
 */
@Composable
fun JogDial(
    readout: String,
    fps: Int,
    onJog: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var notchOffset by remember { mutableFloatStateOf(0f) }
    val jog by rememberUpdatedState(onJog)
    val rate by rememberUpdatedState(fps)
    val track = PicoTheme.colorScheme.fillTertiary
    val notch = PicoTheme.colorScheme.labelTertiary
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .spatialHoverEffect()
            .semantics { contentDescription = "飞梭 $readout".localized() }
            .pointerInput(Unit) {
                val pxPerFrame = with(density) { 12.dp.toPx() }
                val spacing = pxPerFrame
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var carry = 0f
                    var shuttle: Job? = null
                    var shuttleSpeed = 0
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val x = change.position.x
                        val beyond = when {
                            x < 0f -> x
                            x > size.width -> x - size.width
                            else -> 0f
                        }
                        val speed = if (beyond == 0f) 0 else {
                            val past = abs(beyond) / with(density) { 1.dp.toPx() }
                            (if (past < 24f) 2 else if (past < 56f) 4 else 8) * sign(beyond).toInt()
                        }
                        if (speed != shuttleSpeed) {
                            shuttle?.cancel()
                            shuttleSpeed = speed
                            if (speed != 0) {
                                shuttle = scope.launch {
                                    var acc = 0f
                                    while (isActive) {
                                        acc += speed * rate * 0.033f
                                        val frames = acc.toInt()
                                        if (frames != 0) {
                                            jog(frames)
                                            acc -= frames
                                        }
                                        notchOffset = (notchOffset + speed * 2f) % spacing
                                        delay(33)
                                    }
                                }
                            }
                        }
                        if (speed == 0) {
                            val dx = change.position.x - last.x
                            carry += dx
                            val frames = (carry / pxPerFrame).toInt()
                            if (frames != 0) {
                                jog(frames)
                                carry -= frames * pxPerFrame
                            }
                            notchOffset = (notchOffset + dx) % spacing
                        }
                        last = change.position
                    }
                    shuttle?.cancel()
                }
            }
            .drawBehind {
                drawRect(track)
                val spacing = 12.dp.toPx()
                var x = notchOffset % spacing
                if (x < 0f) x += spacing
                val top = size.height * 0.18f
                while (x < size.width) {
                    val center = abs(x - size.width / 2f) / (size.width / 2f)
                    drawLine(notch, Offset(x, top), Offset(x, size.height - top), strokeWidth = 1.dp.toPx(), alpha = (1f - center * 0.8f))
                    x += spacing
                }
                drawRoundRect(
                    PrismColors.Playhead,
                    topLeft = Offset(size.width / 2f - 1.dp.toPx(), 4.dp.toPx()),
                    size = Size(2.dp.toPx(), size.height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            readout,
            style = PrismType.timecodeSmall,
            color = PicoTheme.colorScheme.labelPrimary,
            modifier = Modifier.semantics { contentDescription = readout.localized() },
        )
    }
}
