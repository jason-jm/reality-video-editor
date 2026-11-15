package com.example.prismstudio.p40b43a88.ui.media

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.ui.edit.components.RenameSheet
import com.example.prismstudio.p40b43a88.ui.media.components.SmartBinSheet
import com.example.prismstudio.p40b43a88.ui.media.components.MetadataSheet
import com.example.prismstudio.p40b43a88.ui.media.components.MediaPoolList
import com.example.prismstudio.p40b43a88.ui.media.components.MediaPoolTools
import com.example.prismstudio.p40b43a88.ui.media.components.TimelinePoolGrid
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.ui.media.components.RelinkSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.ui.components.EmptyMessage
import com.example.prismstudio.p40b43a88.ui.media.components.BinNavigation
import com.example.prismstudio.p40b43a88.ui.media.components.ImportButton
import com.example.prismstudio.p40b43a88.ui.media.components.ImportSheet
import com.example.prismstudio.p40b43a88.ui.media.components.MediaPoolGrid
import com.example.prismstudio.p40b43a88.ui.media.components.MediaPoolHeader
import com.example.prismstudio.p40b43a88.ui.media.components.SourceColumn
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect

private val VISUAL_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
)
private val MEDIA_PERMISSIONS = VISUAL_PERMISSIONS + Manifest.permission.READ_MEDIA_AUDIO

/** The Media stage page (state wiring, permission requests, toasts). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaScreen(modifier: Modifier = Modifier, onOpenEdit: () -> Unit = {}) {
    val context = LocalContext.current
    val model: MediaViewModel = viewModel(factory = remember(context) { MediaViewModelFactory(context) })
    val state by model.state.collectAsStateWithLifecycle()
    // A timeline opened from the pool: bring up the Edit page.
    LaunchedEffect(state.openEditRequest) {
        if (state.openEditRequest) {
            model.onEvent(MediaEvent.EditOpened)
            onOpenEdit()
        }
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    fun grants() = MEDIA_PERMISSIONS.filter { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun refresh() = model.onEvent(MediaEvent.PermissionResult(grants().isNotEmpty()))
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val request = { permissionRequest.launch(MEDIA_PERMISSIONS) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.import.visible || state.relink != null) refresh()
    }
    // Prompt on the first import or relink that needs permission. Later prompts follow an explicit tap.
    LaunchedEffect(state.import.permissionRequired, state.relink?.permissionRequired) {
        if (!state.import.permissionRequired && state.relink?.permissionRequired != true) return@LaunchedEffect
        if (!asked) {
            asked = true
            request()
        }
    }
    // System settings can change grants without resuming a spatial window. The launcher callback also catches
    // reselection, where the granted permission names stay the same but the visible files change.
    val sheetOpen = state.import.visible || state.relink != null
    LaunchedEffect(sheetOpen) {
        if (!sheetOpen) return@LaunchedEffect
        var last = grants()
        while (isActive) {
            delay(1000)
            val current = grants()
            if (current != last) {
                last = current
                refresh()
            }
        }
    }
    ToastEffect(state.toast) { model.onEvent(MediaEvent.ToastShown) }
    // `relink-pick-button`: the system's file picker for one offline media; the read access is kept for later sessions.
    var relinkFor by rememberSaveable { mutableStateOf<String?>(null) }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkFor
        relinkFor = null
        if (uri != null && id != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            model.onEvent(MediaEvent.RelinkPicked(id, uri.toString()))
        }
    }
    MediaContent(
        state, model::onEvent, onGrantPermission = request, modifier = modifier,
        onChangeSelection = { permissionRequest.launch(VISUAL_PERMISSIONS) },
        onOpenSettings = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { model.onEvent(MediaEvent.ShowMessage("请在系统设置的应用权限里允许 ${PRODUCT_NAME} 访问")) }
        },
        onPickRelink = { id ->
            relinkFor = id
            val kind = state.relink?.rows?.firstOrNull { it.id == id }?.kind
            relinkPicker.launch(arrayOf(when (kind) { MediaKind.AUDIO -> "audio/*"; MediaKind.IMAGE -> "image/*"; else -> "video/*" }))
        },
    )
}

@Composable
fun MediaContent(
    state: MediaUiState,
    onEvent: (MediaEvent) -> Unit,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier,
    onPickRelink: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onChangeSelection: () -> Unit = onGrantPermission,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 1440.dp
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!compact) {
                BinNavigation(
                    state.bins, state.binIndex, { onEvent(MediaEvent.SelectBin(it)) }, Modifier.width(220.dp),
                    userBins = state.userBins,
                    selectedUserBin = state.userBinId,
                    onSelectUserBin = { onEvent(MediaEvent.SelectUserBin(it)) },
                    onNewBin = { onEvent(MediaEvent.NewBin) },
                    onRenameBin = { onEvent(MediaEvent.OpenRenameBin(it)) },
                    onDeleteBin = { onEvent(MediaEvent.DeleteBin(it)) },
                    smartBins = state.smartBins,
                    selectedSmartBin = state.smartBinId,
                    onSelectSmartBin = { onEvent(MediaEvent.SelectSmartBin(it)) },
                    onNewSmartBin = { onEvent(MediaEvent.NewSmartBin) },
                    onEditSmartBin = { onEvent(MediaEvent.EditSmartBin(it)) },
                    onDeleteSmartBin = { onEvent(MediaEvent.DeleteSmartBin(it)) },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaPoolHeader(
                    query = state.query,
                    summary = state.summary,
                    onQuery = { onEvent(MediaEvent.Query(it)) },
                    onImport = { onEvent(MediaEvent.OpenImport) },
                    onRenameBin = state.userBinId?.let { id -> { onEvent(MediaEvent.OpenRenameBin(id)) } },
                    onDeleteBin = state.userBinId?.let { id -> { onEvent(MediaEvent.DeleteBin(id)) } },
                    onEditSmartBin = state.smartBinId?.let { id -> { onEvent(MediaEvent.EditSmartBin(id)) } },
                    onDeleteSmartBin = state.smartBinId?.let { id -> { onEvent(MediaEvent.DeleteSmartBin(id)) } },
                    smartRules = state.smartBins.firstOrNull { it.id == state.smartBinId }?.rules,
                    offlineCount = state.offlineCount,
                    onRelink = { onEvent(MediaEvent.OpenRelink) },
                )
                if (!state.showsTimelines && !state.isEmpty) {
                    MediaPoolTools(
                        order = state.order,
                        listView = state.listView,
                        onSortBy = { onEvent(MediaEvent.SortBy(it)) },
                        onAscending = { onEvent(MediaEvent.SortAscending(it)) },
                        onListView = { onEvent(MediaEvent.SetListView(it)) },
                    )
                }
                if (state.showsTimelines) {
                    // `timeline-pool-grid`: the project's timelines; a tap opens one on the Edit page.
                    TimelinePoolGrid(
                        tiles = state.timelines,
                        columns = if (compact) 3 else 4,
                        onOpen = { onEvent(MediaEvent.OpenTimeline(it)) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (state.isEmpty) {
                    EmptyMessage(
                        title = "媒体池是空的",
                        body = "导入设备里的视频、音频和图片，它们会出现在这里。",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { ImportButton { onEvent(MediaEvent.OpenImport) } }
                } else if (state.items.isEmpty()) {
                    EmptyMessage(
                        title = state.emptyTitle,
                        body = state.emptyBody,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else if (state.listView) {
                    MediaPoolList(
                        items = state.items,
                        selectedId = state.selectedId,
                        order = state.order,
                        onSelect = { onEvent(MediaEvent.Select(it)) },
                        onSortBy = { onEvent(MediaEvent.SortBy(it)) },
                        onNeedPreview = { onEvent(MediaEvent.RequestThumbnail(it)) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MediaPoolGrid(
                        items = state.items,
                        selectedId = state.selectedId,
                        columns = if (compact) 3 else 4,
                        onSelect = { onEvent(MediaEvent.Select(it)) },
                        onNeedPreview = { onEvent(MediaEvent.RequestThumbnail(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SourceColumn(
                state = state,
                onSurface = { onEvent(MediaEvent.SourceSurface(it)) },
                onScrub = { onEvent(MediaEvent.Scrub(it)) },
                modifier = Modifier.width(560.dp),
                onVideoTarget = { onEvent(MediaEvent.SetVideoTarget(it)) },
                onAudioTarget = { onEvent(MediaEvent.SetAudioTarget(it)) },
                onToggleFlag = { onEvent(MediaEvent.ToggleFlag(it)) },
                onClearFlags = { onEvent(MediaEvent.ClearFlags) },
                onMoveToBin = { onEvent(MediaEvent.MoveToBin(it)) },
                onInputColor = { onEvent(MediaEvent.InputColor(it)) },
                onNewBin = { onEvent(MediaEvent.NewBin) },
                onOpenMetadata = { onEvent(MediaEvent.OpenMetadata) },
                onGenerateProxy = { onEvent(MediaEvent.GenerateProxy) },
                onGenerateAllProxies = { onEvent(MediaEvent.GenerateAllProxies) },
                onDeleteProxy = { onEvent(MediaEvent.DeleteProxy) },
                onSyncAudio = { onEvent(MediaEvent.SyncAudio(it)) },
                onClearSync = { onEvent(MediaEvent.ClearAudioSync) },
                onRelink = { onEvent(MediaEvent.OpenRelink) },
            )
        }
        state.binRename?.let { ui ->
            RenameSheet(
                ui = ui,
                onText = { onEvent(MediaEvent.RenameBinText(it)) },
                onConfirm = { onEvent(MediaEvent.ConfirmRenameBin) },
                onClose = { onEvent(MediaEvent.CloseRenameBin) },
            )
        }
        state.metadataSheet?.let { ui ->
            MetadataSheet(
                ui = ui,
                onDraft = { onEvent(MediaEvent.MetadataDraft(it)) },
                onConfirm = { onEvent(MediaEvent.ConfirmMetadata) },
                onClose = { onEvent(MediaEvent.CloseMetadata) },
            )
        }
        state.smartSheet?.let { ui ->
            SmartBinSheet(
                ui = ui,
                onRules = { onEvent(MediaEvent.SmartRules(it)) },
                onConfirm = { onEvent(MediaEvent.ConfirmSmartBin) },
                onClose = { onEvent(MediaEvent.CloseSmartBin) },
            )
        }
        state.relink?.let { ui ->
            RelinkSheet(
                ui = ui,
                onChoose = { id, uri -> onEvent(MediaEvent.RelinkChoose(id, uri)) },
                onPick = onPickRelink,
                onGrantPermission = onGrantPermission,
                onChangeSelection = onChangeSelection,
                onConfirm = { onEvent(MediaEvent.ConfirmRelink) },
                onClose = { onEvent(MediaEvent.CloseRelink) },
            )
        }
        if (state.import.visible) {
            ImportSheet(
                state = state.import,
                onToggle = { onEvent(MediaEvent.ToggleDevice(it)) },
                onNeedPreview = { onEvent(MediaEvent.RequestDeviceThumbnail(it)) },
                onGrantPermission = onGrantPermission,
                onChangeSelection = onChangeSelection,
                onCancel = { onEvent(MediaEvent.CancelImport) },
                onConfirm = { onEvent(MediaEvent.ConfirmImport) },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}
