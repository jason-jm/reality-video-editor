package com.example.prismstudio.p40b43a88.ui.edit.components

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
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.TimelineEntryUi
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
 * `timeline-menu-button` / `timeline-menu`: Resolve's timelines in a project. The chip names the open timeline; its
 * menu lists every timeline (raster and length beside each, the open one picked) to open another, then makes a new
 * one, duplicates the open one or deletes it — each one undoable step. With one timeline left, 删除 answers why not.
 */
@Composable
fun TimelineMenu(
    name: String,
    entries: List<TimelineEntryUi>,
    onEvent: (EditEvent) -> Unit,
    onImportEdl: () -> Unit = {},
    onImportFcpxml: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    // The menu anchors to its direct parent.
    Box {
        ButtonChip(
            label = { Text(name) },
            onClick = { open = true },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "时间线：$name，点按切换、新建或复制".localized() },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
            ) {
                val act = { event: EditEvent ->
                    open = false
                    onEvent(event)
                }
                entries.forEach { entry ->
                    MenuItem(
                        title = { Text(entry.name) },
                        onClick = { if (entry.open) open = false else act(EditEvent.OpenTimeline(entry.id)) },
                        subtitle = { Text(entry.detail) },
                        contentColors = if (entry.open) {
                            MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                        } else {
                            MenuItemDefaults.menuItemColors()
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuItem(title = { Text("新建时间线") }, onClick = { act(EditEvent.NewTimeline) })
                MenuItem(title = { Text("复制当前时间线") }, onClick = { act(EditEvent.DuplicateTimeline) })
                // `timeline-menu-export-edl`: Resolve's Export → Timeline → EDL, into Download/Reality Video Editor.
                MenuItem(
                    title = { Text("导出 EDL…") },
                    subtitle = { Text("CMX 3600：V1、A1、A2，保存到“下载”") },
                    onClick = { act(EditEvent.ExportEdl) },
                )
                // `timeline-menu-export-fcpxml`: Resolve's Export → Timeline → FCPXML (1.10) — every track, into Download/Reality Video Editor.
                MenuItem(
                    title = { Text("导出 FCPXML…") },
                    subtitle = { Text("1.10：多轨、转场、标题、变速与标记，保存到“下载”") },
                    onClick = { act(EditEvent.ExportFcpxml) },
                )
                // `timeline-menu-import-edl`: Resolve's Import → Timeline → EDL — a new timeline from the file's events.
                MenuItem(
                    title = { Text("导入 EDL…") },
                    subtitle = { Text("按源文件名匹配媒体池，生成一条新时间线") },
                    onClick = {
                        open = false
                        onImportEdl()
                    },
                )
                // `timeline-menu-import-fcpxml`: Resolve's Import → Timeline → FCPXML — the file's first sequence as a new timeline.
                MenuItem(
                    title = { Text("导入 FCPXML…") },
                    subtitle = { Text("Final Cut、Resolve 或本应用导出的文件，按文件名匹配媒体池") },
                    onClick = {
                        open = false
                        onImportFcpxml()
                    },
                )
                // With one timeline left it still answers (项目至少要有一条时间线), dimmed.
                MenuItem(
                    title = {
                        Text(
                            "删除当前时间线",
                            color = if (entries.size > 1) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                        )
                    },
                    onClick = { act(EditEvent.DeleteTimeline) },
                )
            }
        }
    }
}

/**
 * `compound-return-chip`: inside a compound clip (Resolve's breadcrumb over its timeline), back to the timeline it was
 * opened from — named on the chip; the whole way in is read out.
 */
@Composable
fun CompoundReturnChip(trail: List<String>, onEvent: (EditEvent) -> Unit) {
    val back = trail.lastOrNull() ?: return
    ButtonChip(
        label = { Text(back) },
        onClick = { onEvent(EditEvent.LeaveCompound) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_left), contentDescription = null, modifier = Modifier.size(16.dp)) },
        chipSize = ChipsDefaults.Small,
        modifier = Modifier.semantics { contentDescription = "返回“$back”（${trail.joinToString(" › ")} 里的复合片段）".localized() },
    )
}
