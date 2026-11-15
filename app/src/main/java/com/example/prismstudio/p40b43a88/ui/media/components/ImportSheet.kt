package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.EmptyMessage
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.media.DeviceTileUi
import com.example.prismstudio.p40b43a88.ui.media.ImportUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/** `import-sheet`: pick several device files; shows a permission / error state when listing fails. */
@Composable
fun ImportSheet(
    state: ImportUi,
    onToggle: (String) -> Unit,
    onNeedPreview: (String) -> Unit,
    onGrantPermission: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onChangeSelection: () -> Unit = onGrantPermission,
) {
    // As wide as the SDK allows: Sheet clamps to its maximum width token (≈600 dp), and the grid
    // fills the resulting content width (≈536 dp).
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onCancel,
        title = { Text("从设备导入") },
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(state.summary, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onCancel,
                        size = ButtonDefaults.Regular,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PicoTheme.colorScheme.fillTertiary,
                            contentColor = PicoTheme.colorScheme.labelPrimary,
                        ),
                    ) { Text("取消") }
                    Button(onClick = onConfirm, enabled = !state.nothingSelected, size = ButtonDefaults.Regular) {
                        Text(if (state.importing) "正在导入…" else "导入")
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "选择要导入的视频、音频或图片。素材以引用方式加入媒体池，不会复制文件。",
                style = PrismType.label,
                color = PicoTheme.colorScheme.labelTertiary,
            )
            if (state.limitedVisualAccess && !state.loading) LimitedMediaAccessRow(!state.importing, onChangeSelection)
            // `import-missing-row`: some kinds were refused (audio, say, while videos were allowed): say which, and open the
            // app's system settings to allow them — the list refreshes once they are.
            state.missingNote?.let { note ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onOpenSettings,
                        size = ButtonDefaults.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PicoTheme.colorScheme.fillTertiary,
                            contentColor = PicoTheme.colorScheme.labelPrimary,
                        ),
                    ) { Text("去设置") }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxWidth().height(CONTENT_HEIGHT), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                }
                state.hasError -> EmptyMessage(
                    title = "无法读取设备媒体",
                    body = state.errorMessage,
                    modifier = Modifier.fillMaxWidth().height(CONTENT_HEIGHT),
                ) {
                    if (state.permissionRequired) {
                        Button(onClick = onGrantPermission, size = ButtonDefaults.Regular) { Text("重新授权") }
                    }
                }
                else -> DeviceMediaGrid(state.items, onToggle, onNeedPreview, !state.importing, Modifier.fillMaxWidth().height(CONTENT_HEIGHT))
            }
        }
    }
}

@Composable
internal fun LimitedMediaAccessRow(enabled: Boolean, onChangeSelection: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("照片和视频的访问范围受限。", style = PrismType.label,
            color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
        Button(onClick = onChangeSelection, enabled = enabled, size = ButtonDefaults.Small) { Text("更改选择") }
    }
}

/** `import-device-grid` renderer: five-column multi-select grid of device media. */
@Composable
private fun DeviceMediaGrid(
    items: List<DeviceTileUi>,
    onToggle: (String) -> Unit,
    onNeedPreview: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(items, key = { it.id }) { tile ->
            LaunchedEffect(tile.id) { onNeedPreview(tile.id) }
            DeviceTile(tile, enabled = enabled, onClick = { onToggle(tile.id) })
        }
    }
}

@Composable
private fun DeviceTile(tile: DeviceTileUi, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val fill = if (tile.selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (tile.selected) 8.dp else 0.dp)
            .alpha(if (tile.imported) 0.55f else 1f)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(enabled = enabled && !tile.imported, role = Role.Checkbox, onClick = onClick)
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics {
                selected = tile.selected
                contentDescription = spoken(tile.name, if (tile.imported) "已在媒体池".localized() else "")
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.778f)) {
            if (tile.kind == MediaKind.AUDIO) {
                AudioPoster(null, Modifier.fillMaxSize())
            } else {
                PosterArt(PosterColors.NEUTRAL, tile.frame, radius = 8.dp, modifier = Modifier.fillMaxSize())
            }
            PosterBadge(tile.badge, error = false, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            if (tile.selected) CheckMark(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp))
            if (tile.imported) PosterBadge("已导入", error = false, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Text(
            tile.name,
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
        )
    }
}

/** The selection mark (`check` part): interaction disc with a light tick. */
@Composable
private fun CheckMark(modifier: Modifier) {
    val disc = PicoTheme.colorScheme.interaction
    val tick = PicoTheme.colorScheme.labelPrimaryLight
    Canvas(modifier) {
        drawCircle(disc)
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.36f)
        }
        drawPath(path, tick, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val CONTENT_HEIGHT = 440.dp
