package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.SliderSpec

/**
 * SpatialUI `Slider` whose thumb keeps up with the pointer when [value] comes back through a view
 * model. The SpatialUI slider measures each drag step from the value it was last composed with,
 * so steps that arrive before the next recomposition overwrite each other and the thumb falls
 * behind the finger; it also keeps the first `onValueChange` it was composed with. This wrapper
 * adds the steps up itself, shows the running value until the drag ends, and always calls the
 * latest [onValueChange] / [onValueChangeFinished].
 */
@Composable
fun TrackingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    sliderSpec: SliderSpec = SliderDefaults.Small,
) {
    val tracker = remember { DragTracker() }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = (dragValue ?: value).coerceIn(valueRange)
    SideEffect { tracker.composed = shown }

    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val range by rememberUpdatedState(valueRange)
    val interactions = remember { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (!dragged) dragValue = null }

    Slider(
        value = shown,
        onValueChange = remember {
            { reported: Float ->
                val next = tracker.accumulate(dragValue, reported).coerceIn(range)
                dragValue = next
                change(next)
            }
        },
        modifier = modifier,
        valueRange = valueRange,
        onValueChangeFinished = remember {
            {
                dragValue = null
                finished?.invoke()
            }
        },
        enabled = enabled,
        interactionSource = interactions,
        sliderSpec = sliderSpec,
    )
}

/** Turns the slider's per-step reports, all measured from the last composed value, into a running total. */
internal class DragTracker {
    /** The value the slider was last composed with — the base of every report until it recomposes. */
    var composed = 0f

    fun accumulate(running: Float?, reported: Float): Float = (running ?: composed) + (reported - composed)
}
