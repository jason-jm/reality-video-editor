package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.ui.media.SmartBinUi
import com.example.prismstudio.p40b43a88.ui.media.UserBinUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SideNavigation
import com.pico.spatial.ui.design.SideNavigationItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/**
 * `bin-navigation`: media-pool bins (all / video / audio / stills / timelines), then the user's own bins (Resolve's bins:
 * `user-bin-item`, each with its count; a press held on one opens 重命名… / 删除媒体夹) and `new-bin-item` (新建媒体夹),
 * then the smart bins (Resolve's Smart Bins: `smart-bin-item`, what it finds now; held: 编辑条件… / 删除智能媒体夹) and
 * `new-smart-bin-item` (新建智能媒体夹).
 */
@Composable
fun BinNavigation(
    bins: List<MediaBin>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    userBins: List<UserBinUi> = emptyList(),
    selectedUserBin: String? = null,
    onSelectUserBin: (String) -> Unit = {},
    onNewBin: () -> Unit = {},
    onRenameBin: (String) -> Unit = {},
    onDeleteBin: (String) -> Unit = {},
    smartBins: List<SmartBinUi> = emptyList(),
    selectedSmartBin: String? = null,
    onSelectSmartBin: (String) -> Unit = {},
    onNewSmartBin: () -> Unit = {},
    onEditSmartBin: (String) -> Unit = {},
    onDeleteSmartBin: (String) -> Unit = {},
) {
    SideNavigation(
        modifier = modifier.fillMaxHeight(),
        header = { Text("媒体池") },
    ) {
        bins.forEachIndexed { index, bin ->
            val selected = selectedUserBin == null && selectedSmartBin == null && index == selectedIndex
            // The bin shown says so to a screen reader too (release round R9).
            SideNavigationItem(
                selected = selected,
                modifier = Modifier.spatialHoverEffect().selectable(selected = selected, onClick = { onSelect(index) }),
            ) {
                Text(bin.label, maxLines = 1)
            }
        }
        Text("我的媒体夹", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
        userBins.forEach { bin ->
            var menu by remember { mutableStateOf(false) }
            // The menu anchors to its direct parent.
            Box {
                SideNavigationItem(
                    selected = bin.id == selectedUserBin,
                    modifier = Modifier
                        .spatialHoverEffect()
                        .combinedClickable(onClick = { onSelectUserBin(bin.id) }, onLongClick = { menu = true })
                        .semantics {
                            selected = bin.id == selectedUserBin
                            contentDescription = spoken("媒体夹 ${bin.name}".localized(), "${bin.count} 个素材".localized(), "长按重命名或删除".localized())
                        },
                ) {
                    Text("${bin.name}（${bin.count}）", maxLines = 1)
                }
                if (menu) {
                    Menu(
                        onDismissRequest = { menu = false },
                        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                    ) {
                        MenuItem(title = { Text("重命名…") }, onClick = { menu = false; onRenameBin(bin.id) })
                        MenuItem(
                            title = { Text("删除媒体夹") },
                            subtitle = { Text(if (bin.count > 0) "其中 ${bin.count} 个素材回到媒体池" else "媒体夹是空的") },
                            onClick = { menu = false; onDeleteBin(bin.id) },
                        )
                    }
                }
            }
        }
        SideNavigationItem(
            selected = false,
            modifier = Modifier.spatialHoverEffect().clickable(onClick = onNewBin).semantics { contentDescription = "新建媒体夹".localized() },
        ) {
            Text("＋ 新建媒体夹", maxLines = 1, color = PicoTheme.colorScheme.labelSecondary)
        }
        Text("智能媒体夹", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
        smartBins.forEach { bin ->
            var menu by remember { mutableStateOf(false) }
            Box {
                SideNavigationItem(
                    selected = bin.id == selectedSmartBin,
                    modifier = Modifier
                        .spatialHoverEffect()
                        .combinedClickable(onClick = { onSelectSmartBin(bin.id) }, onLongClick = { menu = true })
                        .semantics {
                            selected = bin.id == selectedSmartBin
                            contentDescription = spoken(
                                "智能媒体夹 ${bin.name}".localized(), bin.rules.localized(), "${bin.count} 个素材".localized(), "长按编辑条件或删除".localized(),
                            )
                        },
                ) {
                    Text("${bin.name}（${bin.count}）", maxLines = 1)
                }
                if (menu) {
                    Menu(
                        onDismissRequest = { menu = false },
                        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                    ) {
                        MenuItem(title = { Text("编辑条件…") }, subtitle = { Text(bin.rules) }, onClick = { menu = false; onEditSmartBin(bin.id) })
                        MenuItem(title = { Text("删除智能媒体夹") }, subtitle = { Text("素材仍在媒体池中") }, onClick = { menu = false; onDeleteSmartBin(bin.id) })
                    }
                }
            }
        }
        SideNavigationItem(
            selected = false,
            modifier = Modifier.spatialHoverEffect().clickable(onClick = onNewSmartBin).semantics { contentDescription = "新建智能媒体夹".localized() },
        ) {
            Text("＋ 新建智能媒体夹", maxLines = 1, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
}
