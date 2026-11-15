package com.example.prismstudio.p40b43a88.ui.projects.components

import com.example.prismstudio.p40b43a88.ui.components.trFormat
import com.example.prismstudio.p40b43a88.ui.components.projectDisplayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.projects.BackupsUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `project-backups-sheet`: Resolve's Project Backups — the project's backups newest first, each restorable as a new project
 * beside the original (which stays as it is), and 立即备份 for one now. The policy is said under the title.
 */
@Composable
fun ProjectBackupsSheet(
    ui: BackupsUi,
    busy: Boolean,
    onBackupNow: () -> Unit,
    onRestore: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text(trFormat("项目备份 · {0}", projectDisplayName(ui.projectName)), translate = false) },
    ) {
        Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.recoveryOnly) {
                Text("恢复会创建一个新项目，原文件会保留。", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ProjectBackupsUseCase.POLICY, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onBackupNow,
                        enabled = !busy && !ui.loading && !ui.loadFailed,
                        size = ButtonDefaults.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PicoTheme.colorScheme.fillTertiary,
                            contentColor = PicoTheme.colorScheme.labelPrimary,
                        ),
                    ) { Text("立即备份") }
                }
            }
            if (ui.loading) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                }
            } else if (ui.loadFailed) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Text("无法读取项目备份", style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
                    Button(onClick = onRetry) { Text("重试") }
                }
            } else if (ui.rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (ui.recoveryOnly) "此项目没有可用备份" else "还没有备份：打开项目编辑时会自动备份，或点“立即备份”",
                        style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary,
                    )
                }
            } else {
                val list = rememberLazyListState()
                // A backup just taken is the newest, the list's first row: a keyed list keeps the row that was first in
                // place, so the new one sat above the top, out of sight (R19).
                val newest = ui.rows.firstOrNull()?.takenAtMillis
                LaunchedEffect(newest) { if (newest != null) list.scrollToItem(0) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(ui.rows, key = { it.takenAtMillis }) { row ->
                        // `backup-row`: when, what it held, and 恢复为新项目.
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
                                Text(row.detail, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
                            }
                            Button(onClick = { onRestore(row.takenAtMillis) }, enabled = !busy, size = ButtonDefaults.Small) {
                                Text("恢复为新项目")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("正在保存…", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
                }
                Button(
                    onClick = onClose,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("关闭") }
            }
        }
    }
}
