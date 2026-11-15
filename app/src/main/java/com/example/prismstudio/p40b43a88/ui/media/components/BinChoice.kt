package com.example.prismstudio.p40b43a88.ui.media.components

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
import com.example.prismstudio.p40b43a88.ui.media.UserBinUi
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
 * `source-bin-chip`: the bin the selected media is filed in (Resolve: drag it into a bin); the menu files it in another,
 * takes it out to the root, or makes a new bin first.
 */
@Composable
fun BinChoice(bins: List<UserBinUi>, selected: String?, onMove: (String?) -> Unit, onNewBin: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = bins.firstOrNull { it.id == selected }?.name ?: "未放入媒体夹"
    Box {
        ButtonChip(
            label = { Text(name) },
            onClick = { open = true },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "媒体夹：$name，点按放入其他媒体夹".localized() },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                val picked = MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                val plain = MenuItemDefaults.menuItemColors()
                val colors = { on: Boolean -> if (on) picked else plain }
                MenuItem(title = { Text("不放入媒体夹") }, onClick = { open = false; onMove(null) }, contentColors = colors(selected == null))
                bins.forEach { bin ->
                    MenuItem(
                        title = { Text(bin.name) },
                        subtitle = { Text("${bin.count} 个素材") },
                        onClick = { open = false; onMove(bin.id) },
                        contentColors = colors(bin.id == selected),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuItem(title = { Text("新建媒体夹") }, onClick = { open = false; onNewBin() })
            }
        }
    }
}
