package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ChannelMix
import com.example.prismstudio.p40b43a88.domain.model.MixerInput
import com.example.prismstudio.p40b43a88.domain.model.MixerOutput
import com.example.prismstudio.p40b43a88.domain.model.MixerSwap
import com.example.prismstudio.p40b43a88.domain.model.RgbMixer
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import java.util.Locale

/**
 * `rgb-mixer-panel`: Resolve's RGB Mixer palette — the red, green and blue outputs side by side, each a mix of the three
 * inputs (or, in 单色, the grey's mix), then 保持亮度, 单色, the channel swaps and 重置.
 */
@Composable
fun RgbMixerPanel(
    mixer: RgbMixer,
    enabled: Boolean,
    onValue: (MixerOutput?, MixerInput, Float) -> Unit,
    onPreserve: (Boolean) -> Unit,
    onMonochrome: (Boolean) -> Unit,
    onSwap: (MixerSwap) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (mixer.monochrome) {
                // `mixer-mono-group`: one mix makes the grey.
                MixGroup("灰度", mixer.mono, enabled, { i, v -> onValue(null, i, v) }, Modifier.weight(1f))
                // `mixer-mono-note`
                Text(
                    monoNote(mixer),
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelSecondary,
                    modifier = Modifier.weight(2f).align(Alignment.CenterVertically),
                )
            } else {
                MixerOutput.entries.forEach { output ->
                    MixGroup(output.label, mixer.output(output), enabled, { i, v -> onValue(output, i, v) }, Modifier.weight(1f))
                }
            }
        }
        // `mixer-options-row`
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToggleableChip(
                label = { Text("保持亮度") },
                isToggleOn = mixer.preserveLuminance,
                onClick = { onPreserve(!mixer.preserveLuminance) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "保持亮度：混合只改变颜色，不改变亮度".localized() },
            )
            ToggleableChip(
                label = { Text("单色") },
                isToggleOn = mixer.monochrome,
                onClick = { onMonochrome(!mixer.monochrome) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "单色：按灰度的混合输出黑白画面".localized() },
            )
            Spacer(Modifier.weight(1f))
            // `mixer-swap-*`: two outputs trade their mixes.
            MixerSwap.entries.forEach { swap ->
                ButtonChip(
                    label = { Text(swap.label) },
                    onClick = { onSwap(swap) },
                    enabled = enabled && !mixer.monochrome,
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "交换${swap.a.label}和${swap.b.label}".localized() },
                )
            }
            ButtonChip(
                label = { Text("重置") },
                onClick = onReset,
                enabled = enabled && mixer != RgbMixer.NEUTRAL,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "重置 RGB 混合器".localized() },
            )
        }
    }
}

/** `mixer-output-*`: one output's three sliders, R / G / B. */
@Composable
private fun MixGroup(title: String, mix: ChannelMix, enabled: Boolean, onValue: (MixerInput, Float) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
        MixerInput.entries.forEach { input ->
            ValueSliderRow(
                label = input.label,
                value = mix[input],
                range = RgbMixer.MIN..RgbMixer.MAX,
                valueText = formatValue(mix[input], 2, false),
                onValueChange = { onValue(input, it) },
                enabled = enabled,
                step = RgbMixer.STEP,
                minSliderWidth = 56.dp,
                labelWidth = 14.dp,
                valueWidth = 40.dp,
                modifier = Modifier.semantics { contentDescription = "${title.localized()} ${input.label.localized()}" },
            )
        }
    }
}

/** What the grey is made of, and whether it keeps the picture's brightness. */
internal fun monoNote(mixer: RgbMixer): String {
    val m = mixer.mono
    val mix = String.format(Locale.US, "灰度 = %.2f·R + %.2f·G + %.2f·B", m.r, m.g, m.b)
    return if (mixer.preserveLuminance) "$mix，再除以三者之和（%s），灰色保持原来的亮度".format(String.format(Locale.US, "%.2f", m.sum))
    else "$mix；未保持亮度：三者之和不是 1 时画面变亮或变暗"
}
