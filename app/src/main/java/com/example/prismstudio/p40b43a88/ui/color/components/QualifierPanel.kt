package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.QualifierControl
import com.example.prismstudio.p40b43a88.ui.color.PickMode
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.sliderLabelWidth
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip

/**
 * `qualifier-panel`: Resolve's HSL qualifier for node 2 — pick a colour on the monitor (吸取颜色), add
 * colours to the selection or take them out (加选 / 减选, Resolve's picker add / subtract), show the
 * selection as a matte (显示选区), take everything but it (反转), and tune the hue, saturation and
 * luminance ranges. The strip under the buttons shows which hues are selected.
 */
@Composable
fun QualifierPanel(
    qualifier: HslQualifier?,
    /** What the next tap on the monitor does, or null while the eyedropper is off. */
    eyedropper: PickMode?,
    highlight: Boolean,
    enabled: Boolean,
    onEyedropper: (PickMode?) -> Unit,
    onHighlight: (Boolean) -> Unit,
    onInvert: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onChange: (QualifierControl, Float) -> Unit,
    modifier: Modifier = Modifier,
    /** The matte finesse page instead of the ranges. */
    finesse: Boolean = false,
    onPage: (Boolean) -> Unit = {},
    /** 柔化: 加选 / 减选 work on the soft edge (Resolve's feather pickers). */
    feather: Boolean = false,
    onFeather: (Boolean) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 12 dp apart so the row fits the panel at the window's narrowest (about 660 dp).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The monitor says what a tap does while one of these is on.
            ToggleableChip(
                label = { Text("吸取颜色") },
                isToggleOn = eyedropper == PickMode.PICK,
                onClick = { onEyedropper(if (eyedropper == PickMode.PICK) null else PickMode.PICK) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
            )
            // `qualifier-add-button` / `qualifier-subtract-button`: they refine a qualifier, and stay on for the next colour.
            listOf(
                Triple(PickMode.ADD, "加选", "加选：点击画面中的颜色加入选区"),
                Triple(PickMode.SUBTRACT, "减选", "减选：点击画面中的颜色移出选区"),
            ).forEach { (mode, label, description) ->
                ToggleableChip(
                    label = { Text(label) },
                    isToggleOn = eyedropper == mode,
                    onClick = { onEyedropper(if (eyedropper == mode) null else mode) },
                    enabled = enabled && qualifier != null,
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = description.localized() },
                )
            }
            // `qualifier-feather-button`: Resolve's feather add / subtract — with it on, 加选 / 减选 grow or pull back the
            // selection's soft edge to take a colour half in, or leave it out, and the ranges stay.
            ToggleableChip(
                label = { Text("柔化") },
                isToggleOn = feather,
                onClick = { onFeather(!feather) },
                enabled = enabled && qualifier != null,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "柔化：加选、减选改为调整选区的柔和边缘".localized() },
            )
            LabeledSwitch("显示选区", highlight, onHighlight, enabled && qualifier != null, "显示选区：选中部分保留颜色，其余显示为灰色")
            LabeledSwitch("反转", qualifier?.invert == true, onInvert, enabled && qualifier != null, "反转限定器：调整未选中的颜色")
            // `qualifier-page-segment`: the ranges, or Resolve's matte finesse.
            SegmentControl(Modifier.width(136.dp).semantics { contentDescription = "限定器页面".localized() }) {
                listOf("范围" to false, "精修" to true).forEach { (label, page) ->
                    SegmentItem(selected = finesse == page, onClick = { onPage(page) }, title = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onRemove,
                    enabled = enabled && qualifier != null,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("清除") }
            }
        }
        // Without a qualifier the controls show the defaults a picked one starts near.
        val shown = qualifier ?: HslQualifier()
        if (finesse) {
            // `qualifier-finesse-block`: one slider a row, each with what it does.
            val finesseWidth = sliderLabelWidth(QualifierControl.entries.filter { it.finesse }.map { it.label }, 72.dp)
            QualifierControl.entries.filter { it.finesse }.forEach { control ->
                ValueSliderRow(
                    label = control.label,
                    value = control.read(shown),
                    range = control.min..control.max,
                    valueText = valueText(control, control.read(shown)),
                    onValueChange = { onChange(control, it) },
                    enabled = enabled && qualifier != null,
                    step = control.step,
                    labelWidth = finesseWidth,
                )
            }
            Text(
                if (qualifier == null) "先吸取颜色建立限定器，再精修它的选区。" else "净化黑场去掉零星的弱选区，净化白场补满选区里的空洞，模糊半径柔化选区边缘。打开“显示选区”查看效果。",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
            )
            return@Column
        }
        HueStrip(qualifier, Modifier.fillMaxWidth().height(12.dp))
        if (qualifier == null) {
            Text(
                "点“吸取颜色”，再点画面里要调整的颜色：节点 2 的调色将只作用于相近的颜色。",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
            )
        }
        val groups = QualifierControl.entries.filterNot { it.finesse }.groupBy { it.group }
        // Columns as wide as their longest words in the language shown ("Luminance", "Softness").
        val groupWidth = sliderLabelWidth(groups.keys.toList(), 48.dp)
        val controlWidth = sliderLabelWidth(groups.values.flatten().map { it.label }, 32.dp, 64.dp)
        groups.forEach { (group, controls) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(group, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(groupWidth), fit = true)
                controls.forEach { control ->
                    ValueSliderRow(
                        label = control.label,
                        value = control.read(shown),
                        range = control.min..control.max,
                        valueText = valueText(control, control.read(shown)),
                        onValueChange = { onChange(control, it) },
                        enabled = enabled && qualifier != null,
                        step = control.step,
                        minSliderWidth = 48.dp,
                        labelWidth = controlWidth,
                        valueWidth = 44.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = description.localized() })
    }
}

/** The hue circle unrolled (0–360°), dimmed outside the qualifier's hue range — all lit without one. */
@Composable
private fun HueStrip(qualifier: HslQualifier?, modifier: Modifier) {
    val dim = PrismColors.PictureBlack
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).semantics { contentDescription = "色相范围".localized() }) {
        val slices = 90
        val w = size.width / slices
        for (i in 0 until slices) {
            val hue = (i + 0.5f) * 360f / slices
            val x = i * w
            drawRect(Color.hsv(hue, 0.85f, 0.95f), topLeft = Offset(x, 0f), size = Size(w + 1f, size.height))
            val q = qualifier ?: continue
            // The hue key of a fully saturated, mid-bright colour of this hue.
            val key = q.copy(satLow = 0f, satHigh = 1f, lumLow = 0f, lumHigh = 1f).let { flat ->
                val c = Color.hsv(hue, 1f, 1f)
                flat.key(c.red, c.green, c.blue)
            }
            if (key < 1f) drawRect(dim.copy(alpha = 0.85f * (1f - key)), topLeft = Offset(x, 0f), size = Size(w + 1f, size.height))
        }
    }
}

private fun valueText(control: QualifierControl, v: Float): String = when (control) {
    QualifierControl.HUE_CENTER, QualifierControl.HUE_WIDTH, QualifierControl.HUE_SOFT -> formatValue(v, 0, false, "°")
    QualifierControl.CLEAN_BLACK, QualifierControl.CLEAN_WHITE, QualifierControl.BLUR -> formatValue(v, 0, false)
    else -> formatValue(v * 100f, 0, false, "%")
}
