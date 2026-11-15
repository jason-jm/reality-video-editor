package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/**
 * `eq-preset-row` / `dynamics-preset-row`: 预设 and a chip naming the preset the settings match — [chipLabel], 自定义 or 无 as
 * the sheet decides — that opens [options], the [selected] one reading as picked; [onPick] gets the picked index.
 */
@Composable
fun PresetMenuRow(chipLabel: String, description: String, options: List<String>, selected: Int?, hint: String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("预设", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(56.dp), fit = true)
        // The menu anchors to its direct parent.
        Box {
            ButtonChip(
                label = { Text(chipLabel) },
                onClick = { open = true },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "$description：$chipLabel".localized() },
            )
            if (open) {
                Menu(
                    onDismissRequest = { open = false },
                    positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                ) {
                    options.forEachIndexed { i, option ->
                        MenuItem(
                            title = { Text(option) },
                            onClick = {
                                open = false
                                onPick(i)
                            },
                            contentColors = if (i == selected) {
                                MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                            } else {
                                MenuItemDefaults.menuItemColors()
                            },
                        )
                    }
                }
            }
        }
        Text(hint, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
    }
}
