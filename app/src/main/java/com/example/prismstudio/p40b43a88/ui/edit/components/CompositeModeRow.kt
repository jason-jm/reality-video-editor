package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import kotlin.math.roundToInt

/**
 * `composite-mode-row`: Resolve's Composite Mode beside the opacity — how the picture combines with the tracks beneath
 * it. The chip names the mode and opens `composite-mode-menu` — seventeen modes in Photoshop's groups (darken, lighten,
 * contrast, inversion, component) with dividers, the current one reading as picked; over nothing (the bottom picture) a
 * clip shows as 正常.
 */
@Composable
fun CompositeModeRow(mode: CompositeMode, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("合成模式", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp), fit = true)
        // The menu anchors to its direct parent.
        Box {
            ButtonChip(
                label = { Text(mode.label) },
                onClick = { open = true },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "合成模式：${mode.label}".localized() },
            )
            if (open) {
                // Seventeen modes outgrow the window: opened on one far down (the component group), the list scrolls to it.
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
                    // Photoshop's (and Resolve's) groups, a divider between them. The column gives the current mode a place
                    // to be measured from (each MenuItem wraps itself).
                    Column(Modifier.onPlaced { list = it }) {
                        CompositeMode.GROUPS.forEachIndexed { group, modes ->
                            if (group > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            modes.forEach { option ->
                                MenuItem(
                                    title = { Text(option.label) },
                                    modifier = if (option == mode) Modifier.onPlaced { current = it } else Modifier,
                                    onClick = {
                                        open = false
                                        onEvent(EditEvent.Composite(option))
                                    },
                                    contentColors = if (option == mode) {
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
            "与下方轨道的画面混合",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** How much of the list shows above the current mode when the menu opens scrolled to it: about one item. */
private val ITEM_ABOVE = 48.dp
