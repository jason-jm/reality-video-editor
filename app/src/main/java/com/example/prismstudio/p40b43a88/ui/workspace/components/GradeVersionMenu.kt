package com.example.prismstudio.p40b43a88.ui.workspace.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.color.ColorEvent
import com.example.prismstudio.p40b43a88.ui.color.ColorUiState
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

/**
 * `grade-version-chip`: Resolve's Local Versions — the selected clip's grade versions, the one shown named on the chip. The
 * menu takes up another version (what was done to this one stays with it), starts a new one as a copy of the one shown, or
 * deletes the one shown. It opens above the deck.
 */
@Composable
fun GradeVersionMenu(color: ColorUiState, onColor: (ColorEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val versions = color.gradeVersions
    val name = versions.getOrNull(color.gradeVersion) ?: "版本 1"
    // The menu anchors to its direct parent.
    Box {
        ButtonChip(
            // The name only: the deck's row is full; the menu lists them all.
            label = { Text(name) },
            onClick = { open = true },
            enabled = color.hasClip,
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "调色版本：$name，共 ${versions.size.coerceAtLeast(1)} 个，点按切换、新建或删除".localized() },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                val act = { event: ColorEvent ->
                    open = false
                    onColor(event)
                }
                versions.forEachIndexed { i, version ->
                    val shown = i == color.gradeVersion
                    MenuItem(
                        title = { Text(version) },
                        subtitle = { Text(if (shown) "正在显示" else "切换到此版本（Shift+Alt+[ ]）") },
                        onClick = { if (shown) open = false else act(ColorEvent.SelectGradeVersion(i)) },
                        contentColors = if (shown) {
                            MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                        } else {
                            MenuItemDefaults.menuItemColors()
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuItem(
                    title = { Text("新建版本") },
                    subtitle = { Text("复制当前版本的调色，另试一种（Ctrl+Shift+N）") },
                    onClick = { act(ColorEvent.NewGradeVersion) },
                )
                // With one version it still answers (片段只有一个调色版本), dimmed.
                MenuItem(
                    title = {
                        Text(
                            "删除当前版本",
                            color = if (versions.size > 1) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                        )
                    },
                    onClick = { act(ColorEvent.DeleteGradeVersion) },
                )
            }
        }
    }
}
