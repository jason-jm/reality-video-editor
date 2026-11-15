package com.example.prismstudio.p40b43a88.ui.workspace.components

import com.example.prismstudio.p40b43a88.ui.components.projectDisplayName
import androidx.compose.foundation.layout.Arrangement
import com.example.prismstudio.p40b43a88.ui.components.LanguageMenuButton
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.pico.spatial.ui.design.ToggleIconButton
import com.pico.spatial.ui.design.ToggleIconButtonDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TitleAlignment
import com.pico.spatial.ui.design.TitleBar
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/**
 * `workspace-title-bar`: back to projects, project name + autosave status, the history menu (Resolve's History
 * window: back through several steps at once, or forward again), undo / redo.
 */
@Composable
fun WorkspaceTitleBar(
    projectName: String,
    saveStatus: String,
    canUndo: Boolean,
    canRedo: Boolean,
    undoLabel: String?,
    redoLabel: String?,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    /** The latest steps undo takes back (newest first) and redo brings back (next first). */
    undoHistory: List<String> = emptyList(),
    redoHistory: List<String> = emptyList(),
    onUndoSteps: (Int) -> Unit = {},
    onRedoSteps: (Int) -> Unit = {},
    /** `ultrawide-toggle`: the ultrawide layout (the window at PICO's widest, the timeline across it). */
    ultrawide: Boolean = false,
    onUltrawide: (Boolean) -> Unit = {},
    /** `language-button`: the interface language. */
    language: AppLanguage? = null,
    languages: List<AppLanguage> = AppLanguage.selectable,
    onLanguage: (AppLanguage?) -> Unit = {},
) {
    var historyOpen by remember { mutableStateOf(false) }
    TitleBar(
        modifier = modifier.fillMaxWidth(),
        titleAlignment = TitleAlignment.Center,
        leadingActions = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_left), contentDescription = "返回项目库")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    projectDisplayName(projectName), translate = false,
                    style = PrismType.title,
                    color = PicoTheme.colorScheme.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(saveStatus, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
            }
        },
        trailingActions = {
            LanguageMenuButton(language, languages, onLanguage)
            // `ultrawide-toggle`: as large as the history, undo and redo buttons beside it.
            ToggleIconButton(checked = ultrawide, onCheckedChange = onUltrawide, size = ToggleIconButtonDefaults.Regular) {
                Icon(painterResource(R.drawable.ic_ultrawide), contentDescription = if (ultrawide) "恢复标准布局" else "超宽布局：时间轴铺满加宽的窗口")
            }
            // `history-button`: the menu anchors to this box, below its end.
            Box {
                IconButton(onClick = { historyOpen = true }, enabled = undoHistory.isNotEmpty() || redoHistory.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = "历史记录")
                }
                if (historyOpen) {
                    HistoryMenu(
                        undoHistory,
                        redoHistory,
                        onUndoSteps = { historyOpen = false; onUndoSteps(it) },
                        onRedoSteps = { historyOpen = false; onRedoSteps(it) },
                        onDismiss = { historyOpen = false },
                    )
                }
            }
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = undoLabel?.let { "撤销：$it" } ?: "撤销")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(painterResource(R.drawable.ic_redo), contentDescription = redoLabel?.let { "重做：$it" } ?: "重做")
            }
        },
    )
}

/**
 * `history-menu`: the steps undo can take back, newest first — a tap goes back through that one, so every newer
 * step is undone with it — and, under a divider, the steps redo can bring back.
 */
@Composable
private fun HistoryMenu(
    undoHistory: List<String>,
    redoHistory: List<String>,
    onUndoSteps: (Int) -> Unit,
    onRedoSteps: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Menu(
        onDismissRequest = onDismiss,
        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignEnd(), VerticalPlacement.below()),
    ) {
        undoHistory.forEachIndexed { index, label ->
            HistoryItem(label, if (index == 0) "撤销" else "撤销 ${index + 1} 步") { onUndoSteps(index + 1) }
        }
        if (undoHistory.isNotEmpty() && redoHistory.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        redoHistory.forEachIndexed { index, label ->
            HistoryItem(label, if (index == 0) "重做" else "重做 ${index + 1} 步", dimmed = true) { onRedoSteps(index + 1) }
        }
    }
}

@Composable
private fun HistoryItem(label: String, action: String, dimmed: Boolean = false, onClick: () -> Unit) {
    MenuItem(
        title = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        trailingIcon = { Text(action, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) },
        // Undone steps read quieter than the ones in the edit.
        contentColors = if (dimmed) MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelSecondary) else MenuItemDefaults.menuItemColors(),
    )
}
