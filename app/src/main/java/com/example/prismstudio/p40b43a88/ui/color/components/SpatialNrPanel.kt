package com.example.prismstudio.p40b43a88.ui.color.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.SpatialNrControl
import com.example.prismstudio.p40b43a88.domain.model.SpatialNrKernel
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.sliderLabelWidth
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import kotlin.math.roundToInt

/**
 * `spatial-nr-panel`: Resolve's Spatial NR (the Motion Effects palette) for the clip, before node 01 — 亮度阈值 and 色度阈值
 * (how big a difference still counts as noise; 0 leaves that part alone), 半径 小 / 中 / 大 and 强度. The note says what the
 * settings smooth; the compare view's left half shows the picture before it.
 */
@Composable
fun SpatialNrPanel(
    nr: SpatialNoiseReduction,
    enabled: Boolean,
    onChange: (SpatialNrControl, Float) -> Unit,
    onRadius: (NrRadius) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    /** `grade-mid-detail-row`: Resolve's Mid/Detail (−100…100), after the noise reduction. */
    midDetail: Float = 0f,
    onMidDetail: (Float) -> Unit = {},
    /** `grade-temporal-nr-row`: Resolve's Temporal NR (0…100), frames averaged where nothing moves. */
    temporalNr: Int = 0,
    onTemporalNr: (Float) -> Unit = {},
) {
    // One label column for the panel's sliders, as wide as "Chroma threshold" needs in the language shown.
    val labelWidth = sliderLabelWidth(SpatialNrControl.entries.map { it.label } + listOf("中间调细节", "时域降噪"), 72.dp, 120.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("空间降噪", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text(
                if (nr.isActive) "节点 01 之前 · 对比视图左侧是降噪前" else "关：把亮度或色度阈值调高即开启",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onReset,
                enabled = enabled && nr != SpatialNoiseReduction.OFF,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("重置") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NrSlider(SpatialNrControl.LUMA, nr, enabled, onChange, labelWidth, Modifier.weight(1f))
            NrSlider(SpatialNrControl.CHROMA, nr, enabled, onChange, labelWidth, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("半径", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp), fit = true)
                SegmentControl(Modifier.weight(1f).semantics { contentDescription = "降噪半径".localized() }) {
                    NrRadius.entries.forEach { radius ->
                        SegmentItem(
                            selected = radius == nr.radius,
                            onClick = { if (enabled) onRadius(radius) },
                            title = { Text(radius.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            NrSlider(SpatialNrControl.AMOUNT, nr, enabled, onChange, labelWidth, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ValueSliderRow(
                label = "中间调细节",
                value = midDetail,
                range = -100f..100f,
                valueText = formatValue(midDetail, 0, true),
                onValueChange = onMidDetail,
                enabled = enabled,
                step = 1f,
                minSliderWidth = 110.dp,
                labelWidth = labelWidth,
                modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    midDetail > 0f -> "中间调的纹理更突出（降噪之后）"
                    midDetail < 0f -> "中间调更柔和：皮肤、天空更平滑"
                    else -> "正值让纹理更突出，负值让中间调更柔和"
                },
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ValueSliderRow(
                label = "时域降噪",
                value = temporalNr.toFloat(),
                range = 0f..100f,
                valueText = if (temporalNr == 0) "关" else "$temporalNr",
                onValueChange = onTemporalNr,
                enabled = enabled,
                step = 1f,
                minSliderWidth = 110.dp,
                labelWidth = labelWidth,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (temporalNr > 0) "画面静止处平均前后几帧的噪点，运动处保持原样" else "关：调高后，静止处的噪点按前后帧平均掉",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        Text(note(nr), style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

@Composable
private fun NrSlider(
    control: SpatialNrControl,
    nr: SpatialNoiseReduction,
    enabled: Boolean,
    onChange: (SpatialNrControl, Float) -> Unit,
    labelWidth: Dp,
    modifier: Modifier,
) {
    val value = control.read(nr)
    ValueSliderRow(
        label = control.label,
        value = value,
        range = 0f..control.max,
        valueText = "${value.roundToInt()}${control.unit}",
        onValueChange = { onChange(control, it) },
        enabled = enabled,
        step = 1f,
        minSliderWidth = 110.dp,
        labelWidth = labelWidth,
        modifier = modifier,
    )
}

/** What the settings smooth: within about one sigma of each range Gaussian, in 8-bit levels. */
internal fun note(nr: SpatialNoiseReduction): String {
    if (!nr.isActive) return "亮度阈值：明暗起伏多大以内算噪点；色度阈值：色彩斑点多大以内算噪点。半径越大越平滑，也越慢"
    val luma = (SpatialNrKernel.lumaSigma(nr) * 255).roundToInt()
    val chroma = (SpatialNrKernel.chromaSigma(nr) * 255).roundToInt()
    val parts = buildList {
        if (nr.lumaThreshold > 0f) add("亮度起伏约 $luma 级以内")
        if (nr.chromaThreshold > 0f) add("色彩斑点约 $chroma 级以内")
    }
    return "${parts.joinToString("、")}的被抹平，更大的边缘和细节保留（8 位色阶）；" +
        "半径 ${nr.radius.label}：周围 ${nr.radius.taps} 像素" + if (nr.amount < 100f) "；保留 ${(100 - nr.amount).roundToInt()}% 原画面" else ""
}
