package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.ui.semantics.selected
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.media.TimelineTileUi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.ui.components.FlagMarks
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.components.TimelineMiniature
import com.example.prismstudio.p40b43a88.ui.media.MediaTileUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SearchField
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/** `media-pool-header`: search, pool summary and the import action. */
@Composable
fun MediaPoolHeader(
    query: String,
    summary: String,
    onQuery: (String) -> Unit,
    onImport: () -> Unit,
    /** `user-bin-actions`: with a user bin showing, 重命名 and 删除媒体夹 beside the count (a press held on the bin works too). */
    onRenameBin: (() -> Unit)? = null,
    onDeleteBin: (() -> Unit)? = null,
    /** `smart-bin-actions`: with a smart bin showing, 编辑条件 and 删除智能媒体夹, then its rules. */
    onEditSmartBin: (() -> Unit)? = null,
    onDeleteSmartBin: (() -> Unit)? = null,
    smartRules: String? = null,
    /** `offline-banner`: media whose files went missing, and `relink-button` (Resolve's Relink Media). */
    offlineCount: Int = 0,
    onRelink: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(
            value = query,
            onValueChange = onQuery,
            onSearch = onQuery,
            placeholder = { Text("搜索素材") },
            modifier = Modifier.width(300.dp).tabMovesFocus(),
        )
        Text(summary, style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, modifier = Modifier.weight(1f))
        ImportButton(onImport)
    }
    if (offlineCount > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$offlineCount 个素材离线：原文件已被移动、改名或删除", style = PrismType.label, color = PicoTheme.colorScheme.error, maxLines = 1)
            Button(
                onClick = onRelink,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                modifier = Modifier.semantics { contentDescription = "重新链接离线素材：在设备中查找或手动选择文件".localized() },
            ) { Text("重新链接…") }
        }
    }
    // The open user bin's own actions, on a row of their own (the header has no room beside the search).
    if (onRenameBin != null || onDeleteBin != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val quiet = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary)
            onRenameBin?.let { Button(onClick = it, size = ButtonDefaults.Small, colors = quiet) { Text("重命名") } }
            onDeleteBin?.let { Button(onClick = it, size = ButtonDefaults.Small, colors = quiet) { Text("删除媒体夹") } }
            Text("媒体夹里的素材仍在媒体池中；删除媒体夹不会删除素材", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
        }
    }
    if (onEditSmartBin != null || onDeleteSmartBin != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val quiet = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary)
            onEditSmartBin?.let { Button(onClick = it, size = ButtonDefaults.Small, colors = quiet) { Text("编辑条件") } }
            onDeleteSmartBin?.let { Button(onClick = it, size = ButtonDefaults.Small, colors = quiet) { Text("删除智能媒体夹") } }
            Text("按条件自动收集：${smartRules.orEmpty()}", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
        }
    }
    }
}

@Composable
fun ImportButton(onImport: () -> Unit) {
    Button(
        onClick = onImport,
        size = ButtonDefaults.Regular,
        leadingIcon = { Icon(painterResource(R.drawable.ic_folder_plus), contentDescription = null) },
    ) { Text("导入媒体") }
}

/** `media-grid` renderer: poster tiles with kind, duration and offline status. */
@Composable
fun MediaPoolGrid(
    items: List<MediaTileUi>,
    selectedId: String?,
    columns: Int,
    onSelect: (String) -> Unit,
    onNeedPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        items(items, key = { it.id }) { tile ->
            LaunchedEffect(tile.id) { onNeedPreview(tile.id) }
            MediaTile(tile, selected = tile.id == selectedId, onClick = { onSelect(tile.id) })
        }
    }
}

@Composable
private fun MediaTile(tile: MediaTileUi, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (selected) 12.dp else 0.dp)
            .alpha(if (tile.offline) 0.6f else 1f)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(8.dp)
            .semantics { contentDescription = spoken(tile.name.localized(), tile.duration, if (tile.offline) "素材离线".localized() else "") },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.778f)) {
            if (tile.kind == MediaKind.AUDIO) {
                AudioPoster(tile.waveform, Modifier.fillMaxSize())
            } else {
                PosterArt(tile.poster, tile.frame, radius = 12.dp, modifier = Modifier.fillMaxSize())
            }
            PosterBadge(
                text = if (tile.kind == MediaKind.IMAGE) "图片" else tile.duration,
                error = false,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
            if (tile.offline) PosterBadge("素材离线", error = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            // `hdrBadge` part: HLG / PQ media, tone mapped into the Rec.709 timeline on export.
            else if (tile.hdr) PosterBadge("HDR", error = false, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            // `flags` part: Resolve's flags on the media.
            FlagMarks(tile.flags, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            tile.name,
            style = PrismType.label,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

/** Duration / offline badge drawn on a poster (`durationBadge`, `offlineBadge` parts). */
@Composable
fun PosterBadge(text: String, error: Boolean, modifier: Modifier = Modifier) {
    val fill = if (error) PicoTheme.colorScheme.error else PrismColors.PictureBlack
    Text(
        text,
        style = PrismType.caption,
        color = PicoTheme.colorScheme.labelPrimaryLight,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .drawBehind { drawRect(fill, alpha = if (error) 1f else 0.72f) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** `audioPoster`: audio-clip green well with the file's real waveform. */
@Composable
fun AudioPoster(waveform: Waveform?, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        drawRect(PrismColors.AudioClip)
        val bars = 48
        val step = size.width / bars
        val mid = size.height / 2f
        for (i in 0 until bars) {
            val peak = waveform?.let { w ->
                val from = w.durationMs * i / bars
                w.peak(from, from + w.durationMs / bars)
            } ?: (0.25f + 0.2f * kotlin.math.sin(i * 0.7f).let { it * it })
            val h = (Waveform.displayLevel(peak) * size.height * 0.42f).coerceAtLeast(1.5f)
            drawLine(PrismColors.Waveform, Offset(i * step + step / 2, mid - h), Offset(i * step + step / 2, mid + h), strokeWidth = step * 0.55f, alpha = 0.85f)
        }
    }
}

/**
 * `timeline-pool-grid`: the 时间线 bin — a card per timeline with a miniature of its tracks (picture blue, sound green,
 * titles violet, on picture black), its length, raster and clip count, and 已打开 on the open one; a tap opens it.
 */
@Composable
fun TimelinePoolGrid(tiles: List<TimelineTileUi>, columns: Int, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        items(tiles, key = { it.id }) { tile -> TimelineTile(tile, onClick = { onOpen(tile.id) }) }
    }
}

@Composable
private fun TimelineTile(tile: TimelineTileUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill = if (tile.open) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (tile.open) 12.dp else 0.dp)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .drawBehind { drawRect(fill) }
            .padding(8.dp)
            .semantics { contentDescription = spoken(
                "${(if (tile.compound) "复合片段" else "时间线").localized()} ${tile.name.localized()}",
                tile.detail.localized(),
                if (tile.open) "已打开".localized() else "",
            ) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.778f)) {
            TimelineMiniature(tile.rows, tile.bars, Modifier.fillMaxSize())
            if (tile.open) PosterBadge("已打开", error = false, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            // A compound clip's timeline (Resolve lists compound clips in the pool): open it to edit every clip of it.
            if (tile.compound) PosterBadge("复合片段", error = false, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            tile.name,
            style = PrismType.label,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            tile.detail,
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            // Raster, length and clip count: two lines on a narrow card rather than cutting the count off.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

/** `timelineMiniature`: the timeline's tracks as rows, each clip a block where it lies. */
