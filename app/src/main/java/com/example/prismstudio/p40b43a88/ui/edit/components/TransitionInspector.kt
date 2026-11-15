package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.ui.components.ColorSwatch
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.InspectorUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import kotlin.math.roundToInt

/**
 * `transition-block`: Resolve's transition inspector for the clip's opening transition — its kind (changed in place,
 * length and style kept), length, where it sits around the cut (for those showing both pictures) and ease; the
 * directional kinds' direction, or a wipe's angle; a wipe's soft edge and the band along it in a colour; a colour dip's
 * colour. Then why it plays shorter than set or away from its alignment, and 移除转场.
 */
@Composable
fun TransitionInspector(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    val transition = inspector.transition ?: return
    val enabled = !inspector.locked
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("入场转场", style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
        TransitionTypeRow(transition.type, enabled, onEvent)
        val max = maxOf(inspector.transitionMaxSeconds, 0.2f)
        ValueSliderRow(
            "时长",
            inspector.transitionSeconds.coerceIn(0.1f, max),
            0.1f..max,
            formatValue(inspector.transitionSeconds, 1, false, " 秒"),
            { onEvent(EditEvent.TransitionSeconds(it)) },
            enabled = enabled,
            step = 0.1f,
            labelWidth = LABEL_WIDTH,
        )
        // Where it sits around the cut: any transition showing the outgoing picture can lead in (Resolve's Alignment).
        if (transition.type.showsOutgoing) {
            Segments("对齐", TransitionAlignment.entries, transition.alignment, { it.label }, enabled) { onEvent(EditEvent.TransitionAlign(it)) }
        }
        Segments("缓动", TransitionEase.entries, transition.ease, { it.label }, enabled) { onEvent(EditEvent.TransitionEasing(it)) }
        transition.type.directions?.let { (forward, back) ->
            Segments("方向", listOf(false, true), transition.reverse, { if (it) back else forward }, enabled) {
                onEvent(EditEvent.TransitionReverse(it))
            }
        }
        if (transition.type.angled) {
            ValueSliderRow(
                "角度",
                transition.travelAngle.toFloat(),
                -Transition.HALF_TURN.toFloat()..Transition.HALF_TURN.toFloat(),
                angleLabel(transition.travelAngle),
                { onEvent(EditEvent.TransitionAngle(it.roundToInt())) },
                enabled = enabled,
                step = 1f,
                labelWidth = LABEL_WIDTH,
            )
        }
        if (transition.type.edged) EdgeRows(transition, enabled, onEvent)
        if (transition.type.colored) {
            Swatches("颜色", "转场颜色", transition.color, enabled) { onEvent(EditEvent.TransitionColor(it)) }
        }
        inspector.transitionNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary) }
        Button(
            onClick = { onEvent(EditEvent.RemoveTransition) },
            enabled = enabled,
            size = ButtonDefaults.Regular,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) { Text("移除转场") }
    }
}

/** A wipe's angle as the slider reads it: where the incoming picture comes from (to the nearest eighth of a turn), then degrees. */
internal fun angleLabel(degrees: Int): String {
    val from = listOf("左", "左上", "上", "右上", "右", "右下", "下", "左下")[Math.floorMod(Math.round(degrees / 45f), 8)]
    return "从$from $degrees°"
}

/** `transition-softness-row`, `transition-border-row` and, with a border, `transition-border-colors`. */
@Composable
private fun EdgeRows(transition: Transition, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    val span = 0f..Transition.MAX_STYLE.toFloat()
    ValueSliderRow(
        "柔化",
        transition.softness.toFloat(),
        span,
        transition.softness.toString(),
        { onEvent(EditEvent.TransitionSoftness(it.toInt())) },
        enabled = enabled,
        step = 1f,
        labelWidth = LABEL_WIDTH,
    )
    ValueSliderRow(
        "描边",
        transition.border.toFloat(),
        span,
        if (transition.border == 0) "无" else transition.border.toString(),
        { onEvent(EditEvent.TransitionBorder(it.toInt())) },
        enabled = enabled,
        step = 1f,
        labelWidth = LABEL_WIDTH,
    )
    if (transition.border == 0) return
    Swatches("描边色", "描边颜色", transition.borderColor, enabled) { onEvent(EditEvent.TransitionBorderColor(it)) }
}

/** `transition-border-colors` / `transition-color-row`: the palette's swatches under [label], [current] reading as picked. */
@Composable
private fun Swatches(label: String, description: String, current: TitleColor, enabled: Boolean, onPick: (TitleColor) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = description.localized() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(LABEL_WIDTH))
        TitleColor.entries.forEach { color ->
            ColorSwatch(
                PrismColors.title(color),
                "$description：${color.label}",
                current == color,
                onClick = { onPick(color) },
                plate = PrismColors.titlePlate(color),
                enabled = enabled,
            )
        }
    }
}

/**
 * `transition-type-menu`: the chip names the kind and opens the kinds in Resolve's groups — dissolves, motion, wipes —
 * the current one reading as picked; picking another changes it in place.
 */
@Composable
private fun TransitionTypeRow(type: TransitionType, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("类型", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(LABEL_WIDTH))
        // The menu anchors to its direct parent.
        Box {
            ButtonChip(
                label = { Text(type.label) },
                onClick = { open = true },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "转场类型：${type.label}".localized() },
            )
            if (open) {
                // Ten kinds outgrow the menu's height (the menu scrolls only with a scroll state): opened on one far down,
                // the list scrolls to it.
                val scroll = rememberScrollState()
                var list by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var current by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val itemAbove = with(LocalDensity.current) { ITEM_ABOVE.toPx() }
                LaunchedEffect(list, current) {
                    val from = list?.takeIf { it.isAttached } ?: return@LaunchedEffect
                    val item = current?.takeIf { it.isAttached } ?: return@LaunchedEffect
                    scroll.scrollTo((from.localPositionOf(item, Offset.Zero).y - itemAbove).roundToInt().coerceAtLeast(0))
                }
                Menu(
                    onDismissRequest = { open = false },
                    positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                    scrollState = scroll,
                ) {
                    // The column gives the current kind a place to be measured from (each MenuItem wraps itself).
                    Column(Modifier.onPlaced { list = it }) {
                        TransitionType.GROUPS.forEachIndexed { group, kinds ->
                            if (group > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            kinds.forEach { option ->
                                MenuItem(
                                    title = { Text(option.label) },
                                    modifier = if (option == type) Modifier.onPlaced { current = it } else Modifier,
                                    onClick = {
                                        open = false
                                        if (option != type) onEvent(EditEvent.TransitionKind(option))
                                    },
                                    contentColors = if (option == type) {
                                        MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                                    } else {
                                        MenuItemDefaults.menuItemColors()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "换成其他转场，时长和样式不变",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun <T> Segments(label: String, options: List<T>, selected: T, name: (T) -> String, enabled: Boolean, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(LABEL_WIDTH))
        SegmentControl(Modifier.weight(1f).semantics { contentDescription = "转场$label".localized() }) {
            options.forEach { option ->
                SegmentItem(
                    selected = option == selected,
                    onClick = { if (enabled && option != selected) onPick(option) },
                    title = { Text(name(option)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The labels' column, as the inspector's other rows. */
private val LABEL_WIDTH = 56.dp

/** How much of the list shows above the current kind when the menu opens scrolled to it: about one item. */
private val ITEM_ABOVE = 48.dp
