package com.example.prismstudio.p40b43a88.ui.projects

import com.example.prismstudio.p40b43a88.ui.projects.components.ProjectBackupsSheet
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.data.automation.AutomationBridge
import com.example.prismstudio.p40b43a88.ui.components.EmptyMessage
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import com.example.prismstudio.p40b43a88.ui.projects.components.AboutSheet
import com.example.prismstudio.p40b43a88.ui.projects.components.DeleteProjectDialog
import com.example.prismstudio.p40b43a88.ui.projects.components.LibraryTitleBar
import com.example.prismstudio.p40b43a88.ui.projects.components.NewProjectSheet
import com.example.prismstudio.p40b43a88.ui.projects.components.ProjectDetailPanel
import com.example.prismstudio.p40b43a88.ui.projects.components.ProjectGallery
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.windows.SnackbarHost

/** The project library page (state wiring only). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember(context) { ProjectsViewModelFactory(context) }
    val model: ProjectsViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AutomationBridge.commands.collect { command ->
            when (command) {
                "open" -> model.onEvent(ProjectsEvent.OpenSelected)
                "new" -> model.onEvent(ProjectsEvent.StartNew)
                "create" -> model.onEvent(ProjectsEvent.Create)
            }
        }
    }
    SnackbarHost {
        ToastEffect(state.toast) { model.onEvent(ProjectsEvent.ToastShown) }
        ProjectsContent(state, model::onEvent, modifier)
    }
}

@Composable
fun ProjectsContent(state: ProjectsUiState, onEvent: (ProjectsEvent) -> Unit, modifier: Modifier = Modifier) {
    // The PICO window opens outside touch mode, so Compose gave its first focusable — the search field — the focus and
    // the keyboard came up on every launch. The library takes the focus first; the field is focused when tapped.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(modifier.fillMaxSize().focusRequester(focus).focusable()) {
        val compact = maxWidth < 1440.dp
        Column(Modifier.fillMaxSize()) {
            LibraryTitleBar(
                query = state.query,
                onQuery = { onEvent(ProjectsEvent.Query(it)) },
                onNewProject = { onEvent(ProjectsEvent.StartNew) },
                language = state.language,
                languages = state.languages,
                onLanguage = { onEvent(ProjectsEvent.SetLanguage(it)) },
                onAbout = { onEvent(ProjectsEvent.OpenAbout) },
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!state.loading && !state.loadFailed) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.summary, style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
                            if (state.unreadableIds.isNotEmpty()) {
                                Button(onClick = { onEvent(ProjectsEvent.RetryLoad) }, size = ButtonDefaults.Small) { Text("重新读取") }
                            }
                        }
                    }
                    if (state.loading) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(40.dp))
                        }
                    } else if (state.loadFailed) {
                        EmptyMessage(
                            title = "无法读取项目库",
                            body = "请检查设备存储后重试。",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            Button(onClick = { onEvent(ProjectsEvent.RetryLoad) }) { Text("重试") }
                        }
                    } else if (state.isEmpty) {
                        EmptyMessage(
                            title = "还没有项目",
                            body = "新建一个项目，导入设备里的视频，开始第一次剪辑。",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { onEvent(ProjectsEvent.StartNew) },
                                size = ButtonDefaults.Regular,
                                leadingIcon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null) },
                            ) { Text("新建项目") }
                        }
                    } else if (state.items.isEmpty() && state.unreadableIds.isEmpty() && state.query.isNotBlank()) {
                        EmptyMessage(
                            title = "没有匹配的项目",
                            body = "换个关键词试试，或清空搜索查看全部项目。",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            Button(onClick = { onEvent(ProjectsEvent.Query("")) }) { Text("清空搜索") }
                        }
                    } else {
                        ProjectGallery(
                            items = state.items,
                            unreadableIds = state.unreadableIds,
                            selectedId = state.selectedId,
                            columns = if (compact) 2 else 3,
                            onSelect = { onEvent(ProjectsEvent.Select(it)) },
                            onOpen = { onEvent(ProjectsEvent.OpenSelected) },
                            onRecover = { onEvent(ProjectsEvent.OpenRecovery(it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                state.selected?.takeUnless { state.loading || state.loadFailed }?.let { detail ->
                    ProjectDetailPanel(
                        detail = detail,
                        busy = state.busy,
                        onOpen = { onEvent(ProjectsEvent.OpenSelected) },
                        onDuplicate = { onEvent(ProjectsEvent.Duplicate) },
                        onDelete = { onEvent(ProjectsEvent.RequestDelete) },
                        modifier = Modifier.width(420.dp).padding(top = 34.dp),
                        onBackups = { onEvent(ProjectsEvent.OpenBackups) },
                    )
                }
            }
        }
        if (state.newProject.visible) {
            NewProjectSheet(
                form = state.newProject,
                busy = state.busy,
                onName = { onEvent(ProjectsEvent.NewName(it)) },
                onFormat = { onEvent(ProjectsEvent.NewFormat(it)) },
                onFps = { onEvent(ProjectsEvent.NewFps(it)) },
                onCancel = { onEvent(ProjectsEvent.CancelNew) },
                onCreate = { onEvent(ProjectsEvent.Create) },
            )
        }
        state.backups?.let { ui ->
            ProjectBackupsSheet(
                ui = ui,
                busy = state.busy,
                onRetry = { onEvent(ProjectsEvent.RetryBackups) },
                onBackupNow = { onEvent(ProjectsEvent.BackupNow) },
                onRestore = { onEvent(ProjectsEvent.RestoreBackup(it)) },
                onClose = { onEvent(ProjectsEvent.CloseBackups) },
            )
        }
        state.about?.let { ui ->
            AboutSheet(ui = ui, onShowLicense = { onEvent(ProjectsEvent.ShowLicense(it)) }, onClose = { onEvent(ProjectsEvent.CloseAbout) })
        }
        state.deleteTarget?.let { target ->
            DeleteProjectDialog(
                projectName = target.name,
                onConfirm = { onEvent(ProjectsEvent.ConfirmDelete) },
                onDismiss = { onEvent(ProjectsEvent.DismissDelete) },
            )
        }
    }
}
