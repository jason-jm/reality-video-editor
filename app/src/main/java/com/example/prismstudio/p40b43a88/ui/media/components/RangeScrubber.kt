package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.pico.spatial.ui.design.PicoTheme
import kotlin.math.roundToInt

/**
 * `source-scrubber` (range-scrubber renderer): the whole 56 dp strip scrubs the source clip; the
 * marked in / out range is tinted and its ends carry marker-flag handles.
 */
@Composable
fun RangeScrubber(
    durationFrames: Int,
    positionFrame: Int,
    inFrame: Int,
    outFrame: Int,
    onScrub: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track = PicoTheme.colorScheme.fillTertiary
    val scrub by rememberUpdatedState(onScrub)
    val total by rememberUpdatedState(durationFrames.coerceAtLeast(1))
    Canvas(
        modifier
            .semantics { contentDescription = "源片段刮擦条".localized() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun frame(x: Float) = (x / size.width * total).roundToInt().coerceIn(0, total)
                    scrub(frame(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        scrub(frame(change.position.x))
                    }
                }
            },
    ) {
        val barTop = size.height * 0.3f
        val barHeight = size.height * 0.4f
        drawRoundRect(track, topLeft = Offset(0f, barTop), size = Size(size.width, barHeight), cornerRadius = CornerRadius(8.dp.toPx()))
        if (durationFrames <= 0) return@Canvas
        fun x(frame: Int) = frame.toFloat() / durationFrames * size.width
        val a = x(inFrame)
        val b = x(outFrame)
        drawRoundRect(PrismColors.InOutRange, topLeft = Offset(a, barTop), size = Size((b - a).coerceAtLeast(0f), barHeight), cornerRadius = CornerRadius(8.dp.toPx()))
        val handleW = 4.dp.toPx()
        drawRoundRect(PrismColors.MarkerFlag, topLeft = Offset(a - handleW / 2, barTop - 6.dp.toPx()), size = Size(handleW, barHeight + 12.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(PrismColors.MarkerFlag, topLeft = Offset(b - handleW / 2, barTop - 6.dp.toPx()), size = Size(handleW, barHeight + 12.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
        val p = x(positionFrame)
        drawRoundRect(PrismColors.Playhead, topLeft = Offset(p - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height), cornerRadius = CornerRadius(1.dp.toPx()))
    }
}
