package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.layout.Arrangement
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.Menu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.FlagToggles
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.ui.components.MetadataList
import com.example.prismstudio.p40b43a88.ui.components.MonitorContent
import com.example.prismstudio.p40b43a88.ui.components.MonitorView
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange
import com.example.prismstudio.p40b43a88.ui.media.MediaUiState
import com.example.prismstudio.p40b43a88.ui.media.TargetChoiceUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text

/**
 * `source-column`: source viewer, scrubber with marks, in / range / out timecodes, where the deck's edits
 * land and on which tracks, metadata.
 */
@Composable
fun SourceColumn(
    state: MediaUiState,
    onSurface: (SurfaceChange) -> Unit,
    onScrub: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onVideoTarget: (String) -> Unit = {},
    onAudioTarget: (String?) -> Unit = {},
    onToggleFlag: (FlagColor) -> Unit = {},
    onClearFlags: () -> Unit = {},
    onMoveToBin: (String?) -> Unit = {},
    onNewBin: () -> Unit = {},
    onOpenMetadata: () -> Unit = {},
    onGenerateProxy: () -> Unit = {},
    onGenerateAllProxies: () -> Unit = {},
    onDeleteProxy: () -> Unit = {},
    onSyncAudio: (String) -> Unit = {},
    onClearSync: () -> Unit = {},
    /** `source-color-segment`: Resolve's Input Color Space for the selected video. */
    onInputColor: (InputColorSpace) -> Unit = {},
    /** `source-relink-row`: the selected media is offline — the relink sheet. */
    onRelink: () -> Unit = {},
) {
    val asset = state.selected
    val content = MonitorContent(
        pictureAspect = state.pictureAspect,
        video = asset != null && asset.kind == MediaKind.VIDEO && !asset.offline,
        still = state.sourceStill.takeIf { asset?.kind == MediaKind.IMAGE },
        message = when {
            asset == null -> "选择一个素材进行预览"
            asset.offline -> "素材离线：原文件已被移动或删除"
            asset.kind == MediaKind.AUDIO -> "音频素材：${asset.name}"
            else -> null
        },
        messageIsError = asset?.offline == true,
    )
    // Scrolls: the metadata's last rows can run under the deck.
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonitorView(content = content, onSurface = onSurface, modifier = Modifier.fillMaxWidth())
        // `source-relink-row`: where the file was, and the way back to it.
        if (asset?.offline == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "原位置：${asset.relativePath.ifEmpty { "—" }}${asset.name}",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                com.pico.spatial.ui.design.Button(
                    onClick = onRelink,
                    size = com.pico.spatial.ui.design.ButtonDefaults.Small,
                    colors = com.pico.spatial.ui.design.ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重新链接…") }
            }
        }
        RangeScrubber(
            durationFrames = state.sourceDurationFrames,
            positionFrame = state.sourcePositionFrames,
            inFrame = state.inFrame,
            outFrame = state.outFrame,
            onScrub = onScrub,
            enabled = !state.nothingSelected,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(state.inLabel, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
            Text(state.rangeLabel, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text(state.outLabel, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // `source-edit-point`: where the deck's 插入 / 覆盖 / 波纹覆盖 / 替换 / 叠加 land (追加 goes to the end).
            Text(state.editPointLabel, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            // `edit-target-row`: Resolve's destination controls — the tracks the picture and the sound land on.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("画面", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TargetSegments(state.videoTargets, state.targets.video, "画面落在的视频轨道") { id -> id?.let(onVideoTarget) }
                Spacer(Modifier.width(16.dp))
                Text("声音", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TargetSegments(state.audioTargets, state.targets.audio, "声音落在的音频轨道（自动：原声到 A1，音乐到 A2）", onAudioTarget)
            }
        }
        // `source-flag-row`: Resolve's flags on the selected media — every clip made from it shows them.
        if (asset != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("旗标", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                FlagToggles(asset.flags, onToggle = onToggleFlag, onClear = onClearFlags)
            }
            // `source-bin-row`: the user bin it is filed in.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("媒体夹", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                BinChoice(state.userBins, state.selectedBinId, onMove = onMoveToBin, onNewBin = onNewBin)
            }
            // `source-proxy-row`: Resolve's proxy media for the selected footage; the chip's menu makes or deletes it.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("代理", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                ProxyChoice(state, onGenerateProxy, onGenerateAllProxies, onDeleteProxy)
            }
            // `source-sync-row`: Resolve's Auto Sync Audio — the video's sound from a separate recorder's file, lined up by waveform.
            if (state.selected?.kind == com.example.prismstudio.p40b43a88.domain.model.MediaKind.VIDEO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("同步", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                    SyncChoice(state, onSyncAudio, onClearSync)
                }
            }
            // `source-color-row`: Resolve's Input Color Space — the file's tags (自动) or set by hand, and what it does.
            state.inputColor?.let { input ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("色彩", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                    // 320 dp and one line: at 280 the device wrapped "Rec.709" onto two.
                    SegmentControl(Modifier.width(360.dp).semantics { contentDescription = "输入色彩空间".localized() }) {
                        InputColorSpace.entries.forEach { space ->
                            SegmentItem(selected = space == input, onClick = { onInputColor(space) }, title = { Text(space.label, maxLines = 1) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text(state.colorNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 2)
            }
            // `source-slate-row`: Resolve's clip metadata in short — scene, shot, take, Good Take; the chip opens the sheet.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("场记", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                ButtonChip(
                    label = { Text(state.slateLabel) },
                    onClick = onOpenMetadata,
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = spoken(labelled("场记".localized(), state.slateLabel.localized()), "点按编辑场次、镜头、条次、关键词和备注".localized()) },
                )
            }
        }
        MetadataList(state.metadata)
    }
}

/** One destination control: a segment per track ([TargetChoiceUi.id] null: automatic). */
@Composable
private fun TargetSegments(choices: List<TargetChoiceUi>, selected: String?, description: String, onSelect: (String?) -> Unit) {
    if (choices.isEmpty()) return
    SegmentControl(Modifier.width(TARGET_SEGMENT * choices.size).semantics { contentDescription = description.localized() }) {
        choices.forEach { choice ->
            SegmentItem(
                selected = choice.id == selected,
                onClick = { onSelect(choice.id) },
                title = { Text(choice.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val TARGET_SEGMENT = 76.dp

/**
 * `source-sync-chip`: what the selected video's sound comes from — its own, or a recorder's file synced by waveform; its
 * menu (`source-sync-menu`) says how the files line up, lists the pool's sound files to sync with, and 取消同步.
 */
@Composable
private fun SyncChoice(state: MediaUiState, onSync: (String) -> Unit, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = state.syncChoices.firstOrNull { it.selected }
    val label = when {
        state.syncing -> "比较声音中…"
        current != null -> "录音 · ${current.name.substringBeforeLast('.')}"
        else -> "自带声音"
    }
    Box {
        ButtonChip(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { if (!state.syncing) open = true },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = spoken(labelled("同步音频".localized(), state.syncLabel.localized()), "点按选择录音文件".localized()) },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                val dim = MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelTertiary)
                val plain = MenuItemDefaults.menuItemColors()
                // `source-sync-state`: where the sound comes from now, and how the files line up.
                MenuItem(title = { Text(state.syncUnavailable ?: state.syncLabel) }, onClick = { open = false }, contentColors = dim)
                if (state.syncUnavailable == null) {
                    if (state.syncChoices.isEmpty()) {
                        MenuItem(title = { Text("媒体池里没有音频文件：先导入录音") }, onClick = { open = false }, contentColors = dim)
                    }
                    state.syncChoices.forEach { choice ->
                        MenuItem(
                            title = { Text(choice.name) },
                            subtitle = { Text(if (choice.selected) "正在使用 · 点按重新比较" else "按波形对齐后使用它的声音") },
                            onClick = { open = false; onSync(choice.id) },
                            contentColors = plain,
                        )
                    }
                    MenuItem(
                        title = { Text("取消同步") },
                        subtitle = { Text("之后放到时间线的片段使用视频自带的声音") },
                        onClick = { open = false; onClear() },
                        contentColors = if (state.synced) plain else dim,
                    )
                }
            }
        }
    }
}

/** `source-proxy-chip`: the selected media's proxy; its menu makes it (or every video's), or deletes it. */
@Composable
private fun ProxyChoice(state: MediaUiState, onGenerate: () -> Unit, onGenerateAll: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ButtonChip(
            label = { Text(state.proxyLabel) },
            onClick = { open = true },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = spoken(labelled("代理".localized(), state.proxyLabel.localized()), "点按生成或删除代理".localized()) },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                val dim = MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelTertiary)
                val plain = MenuItemDefaults.menuItemColors()
                MenuItem(
                    title = { Text(if (state.hasProxy) "重新生成代理" else "生成代理") },
                    subtitle = { Text(if (state.canProxy) "短边 540 像素，只含画面；渲染始终用原片" else state.proxyLabel) },
                    onClick = { open = false; onGenerate() },
                    contentColors = if (state.canProxy && !state.proxyMaking) plain else dim,
                )
                MenuItem(
                    title = { Text("为全部视频生成代理") },
                    subtitle = { Text(if (state.proxiesMissing > 0) "还有 ${state.proxiesMissing} 个视频没有代理，一次生成一个" else "媒体池里的视频都已有代理") },
                    onClick = { open = false; onGenerateAll() },
                    contentColors = if (state.proxiesMissing > 0) plain else dim,
                )
                MenuItem(
                    title = { Text("删除代理") },
                    subtitle = { Text("释放空间；需要时可以再生成") },
                    onClick = { open = false; onDelete() },
                    contentColors = if (state.hasProxy || state.proxyMaking) plain else dim,
                )
            }
        }
    }
}
