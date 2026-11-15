package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.usecase.MediaSort
import com.example.prismstudio.p40b43a88.domain.usecase.SortMediaUseCase
import com.example.prismstudio.p40b43a88.ui.components.FlagMarks
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.media.MediaTileUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/**
 * `pool-tools-row`: Resolve's media pool sort and views — the order on a chip (its menu: the columns, then 升序 / 降序) and
 * 缩略图 / 列表.
 */
@Composable
fun MediaPoolTools(
    order: SortMediaUseCase.Order,
    listView: Boolean,
    onSortBy: (MediaSort) -> Unit,
    onAscending: (Boolean) -> Unit,
    onListView: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("排序", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        var open by remember { mutableStateOf(false) }
        Box {
            ButtonChip(
                label = { Text(order.label) },
                onClick = { open = true },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "排序：${order.label}，点按更改".localized() },
            )
            if (open) {
                Menu(
                    onDismissRequest = { open = false },
                    positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                ) {
                    val picked = MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                    val plain = MenuItemDefaults.menuItemColors()
                    MediaSort.entries.forEach { sort ->
                        MenuItem(
                            title = { Text(sort.label) },
                            onClick = { open = false; onSortBy(sort) },
                            contentColors = if (sort == order.by) picked else plain,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MenuItem(title = { Text("升序 ↑") }, onClick = { open = false; onAscending(true) }, contentColors = if (order.ascending) picked else plain)
                    MenuItem(title = { Text("降序 ↓") }, onClick = { open = false; onAscending(false) }, contentColors = if (!order.ascending) picked else plain)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SegmentControl(Modifier.width(180.dp).semantics { contentDescription = "媒体池视图".localized() }) {
            listOf(false to "缩略图", true to "列表").forEach { (list, label) ->
                SegmentItem(selected = listView == list, onClick = { onListView(list) }, title = { Text(label) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A list view column: its sort, title and width (null: takes the rest). */
private data class PoolColumn(val sort: MediaSort, val title: String, val width: Dp?)

private val COLUMNS = listOf(
    PoolColumn(MediaSort.NAME, "名称", null),
    PoolColumn(MediaSort.KIND, "类型", 48.dp),
    PoolColumn(MediaSort.DURATION, "时长", 64.dp),
    PoolColumn(MediaSort.RESOLUTION, "分辨率", 92.dp),
    PoolColumn(MediaSort.SLATE, "场记", 96.dp),
    PoolColumn(MediaSort.USAGE, "使用", 48.dp),
)

/**
 * `media-list`: Resolve's media pool list view — a row per piece of media (thumbnail, name, kind, duration, resolution,
 * slate, uses, flags); a column title sorts by it, a second tap the other way round.
 */
@Composable
fun MediaPoolList(
    items: List<MediaTileUi>,
    selectedId: String?,
    order: SortMediaUseCase.Order,
    onSelect: (String) -> Unit,
    onSortBy: (MediaSort) -> Unit,
    onNeedPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // `media-list-header`: the column titles; the sorted one carries ↑ / ↓.
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(64.dp))
            COLUMNS.forEach { c ->
                val mark = if (order.by == c.sort) if (order.ascending) " ↑" else " ↓" else ""
                Text(
                    c.title + mark,
                    style = PrismType.caption,
                    color = if (order.by == c.sort) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                    maxLines = 1,
                    modifier = cell(c.width)
                        .clip(RoundedCornerShape(6.dp))
                        .spatialHoverEffect()
                        .clickable { onSortBy(c.sort) }
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "按${c.sort.label}排序".localized() },
                )
            }
            Spacer(Modifier.width(56.dp))
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(items, key = { it.id }) { tile ->
                LaunchedEffect(tile.id) { onNeedPreview(tile.id) }
                ListRow(tile, selected = tile.id == selectedId, onClick = { onSelect(tile.id) })
            }
        }
    }
}

private fun RowScope.cell(width: Dp?): Modifier = if (width == null) Modifier.weight(1f) else Modifier.width(width)

@Composable
private fun ListRow(tile: MediaTileUi, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else androidx.compose.ui.graphics.Color.Transparent
    val kind = when (tile.kind) {
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (tile.offline) 0.6f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = spoken(
                tile.name.localized(), kind.localized(), tile.duration,
                (if (tile.uses > 0) "使用 ${tile.uses} 次" else "未使用").localized(),
                if (tile.offline) "素材离线".localized() else "",
            ) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(64.dp).height(36.dp)) {
            if (tile.kind == MediaKind.AUDIO) AudioPoster(tile.waveform, Modifier.fillMaxSize())
            else PosterArt(tile.poster, tile.frame, radius = 6.dp, modifier = Modifier.fillMaxSize())
        }
        val primary = PicoTheme.colorScheme.labelPrimary
        val quiet = PicoTheme.colorScheme.labelSecondary
        Text(
            tile.name + if (tile.goodTake) " ★" else "",
            style = PrismType.label,
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(kind, style = PrismType.caption, color = quiet, maxLines = 1, modifier = Modifier.width(48.dp))
        Text(if (tile.kind == MediaKind.IMAGE) "—" else tile.duration, style = PrismType.caption, color = quiet, maxLines = 1, modifier = Modifier.width(64.dp))
        Text(tile.resolution, style = PrismType.caption, color = quiet, maxLines = 1, modifier = Modifier.width(92.dp))
        Text(tile.slate, style = PrismType.caption, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
        Text(if (tile.uses > 0) "${tile.uses}" else "—", style = PrismType.caption, color = quiet, maxLines = 1, modifier = Modifier.width(48.dp))
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            if (tile.offline) Text("离线", style = PrismType.caption, color = PicoTheme.colorScheme.error)
            else FlagMarks(tile.flags)
        }
    }
}
