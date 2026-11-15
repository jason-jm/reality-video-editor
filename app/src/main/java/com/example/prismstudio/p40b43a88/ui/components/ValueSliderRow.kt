package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SliderDefaults
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A labelled `Slider` row: 72 dp label, growing slider (min width [minSliderWidth]) and a 72 dp
 * right-aligned value, then [trailing] (a parameter's keyframe button). Values snap to [step] when it is positive.
 */
@Composable
fun ValueSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    step: Float = 0f,
    minSliderWidth: Dp = 100.dp,
    labelWidth: Dp = 92.dp,
    onValueChangeFinished: (() -> Unit)? = null,
    valueWidth: Dp = 72.dp,
    trailing: (@Composable () -> Unit)? = null,
    /** Where the label has its own words ([tr]): "compact" in the dense audio sheets ("Thresh"). */
    labelContext: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(labelWidth), fit = true, fitLines = 2, context = labelContext)
        TrackingSlider(
            value = value.coerceIn(range),
            onValueChange = { raw -> onValueChange(snap(raw, range, step)) },
            valueRange = range,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            sliderSpec = SliderDefaults.Small,
            modifier = Modifier.weight(1f).widthIn(min = minSliderWidth).semantics { contentDescription = label.localized() },
        )
        Text(
            valueText,
            style = PrismType.label,
            color = PicoTheme.colorScheme.labelPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(valueWidth),
            fit = true,
        )
        trailing?.invoke()
    }
}

fun snap(raw: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    if (step <= 0f) return raw.coerceIn(range)
    val steps = ((raw - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range)
}

/** Formats a slider value with a fixed number of decimals and an optional explicit sign. */
fun formatValue(value: Float, decimals: Int, signed: Boolean, suffix: String = ""): String {
    val text = String.format(Locale.US, "%.${decimals}f", if (abs(value) < 0.5f * Math.pow(10.0, -decimals.toDouble()).toFloat()) 0f else value)
    val sign = if (signed && value > 0f && text.trim('0', '.').isNotEmpty()) "+" else ""
    return "$sign$text$suffix"
}

/**
 * The label column for a panel of [ValueSliderRow]s: [min], or as wide as the longest of [labels] in the language shown
 * (up to [max]; a longer one fits itself), so the panel's sliders stay lined up in any language.
 */
@Composable
fun sliderLabelWidth(labels: List<String>, min: Dp, max: Dp = min * 2, context: String? = null): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val language = LocalAppLanguage.current
    val style = PrismType.label
    return remember(labels, language, min, max, style, context) {
        val widest = labels.maxOfOrNull { measurer.measure(I18n.translate(it, context, language), style).size.width } ?: 0
        with(density) { widest.toDp() }.coerceIn(min, max.coerceAtLeast(min))
    }
}
