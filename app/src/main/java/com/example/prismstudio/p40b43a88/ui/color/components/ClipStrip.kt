package com.example.prismstudio.p40b43a88.ui.color.components

import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.listed
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.ui.color.StripMenuUi
import com.example.prismstudio.p40b43a88.ui.color.ColorEvent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.ui.color.ClipFilterOptionUi
import com.example.prismstudio.p40b43a88.ui.color.GradeClipUi
import com.example.prismstudio.p40b43a88.ui.components.FlagMarks
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.components.rememberFrameImage
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `clip-strip-group`: Resolve's Clips filter beside the graded clip strip. The chip names the filter, lit while
 * one narrows the strip, and opens its menu above it; the count under it says how many clips the strip shows.
 */
@Composable
fun ClipStripPanel(
    clips: List<GradeClipUi>,
    selectedId: String?,
    filterLabel: String,
    filtered: Boolean,
    countLabel: String,
    filterOptions: List<ClipFilterOptionUi>,
    colorFilters: List<ClipFilterOptionUi>,
    message: String?,
    onSelect: (String) -> Unit,
    onFilter: (ClipFilter) -> Unit,
    modifier: Modifier = Modifier,
    flagFilters: List<ClipFilterOptionUi> = emptyList(),
    menu: StripMenuUi = StripMenuUi(),
    onMenu: ((ColorEvent) -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ClipFilterHead(filterLabel, filtered, countLabel, filterOptions, colorFilters, flagFilters, onFilter)
        ClipStrip(clips, selectedId, onSelect, Modifier.weight(1f).fillMaxHeight(), message, menu, onMenu)
    }
}

/** `clip-filter-head`: the filter chip (`clip-filter-chip`) with its menu, and the count (`clip-filter-count`). */
@Composable
private fun ClipFilterHead(
    label: String,
    filtered: Boolean,
    count: String,
    options: List<ClipFilterOptionUi>,
    colors: List<ClipFilterOptionUi>,
    flags: List<ClipFilterOptionUi>,
    onFilter: (ClipFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.width(144.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The menu anchors to its direct parent.
        Box {
            ToggleableChip(
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                isToggleOn = filtered,
                onClick = { open = true },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "片段筛选：$label".localized() },
            )
            if (open) {
                ClipFilterMenu(
                    options = options,
                    colors = colors,
                    flags = flags,
                    onPick = { filter ->
                        open = false
                        onFilter(filter)
                    },
                    onDismiss = { open = false },
                )
            }
        }
        Text(count, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
    }
}

/**
 * `clip-filter-menu`: 全部片段 / 已调色片段 / 未调色片段 with their counts, a choice per clip colour in use, then
 * Resolve's Flagged Clips — 已标旗标片段 and a choice per flag colour in use. It opens above the chip (the strip sits
 * in the lower half of the window); the current filter reads as picked.
 */
@Composable
private fun ClipFilterMenu(
    options: List<ClipFilterOptionUi>,
    colors: List<ClipFilterOptionUi>,
    flags: List<ClipFilterOptionUi>,
    onPick: (ClipFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    Menu(
        onDismissRequest = onDismiss,
        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
    ) {
        options.forEach { option -> FilterItem(option) { onPick(option.filter) } }
        if (colors.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        colors.forEach { option -> FilterItem(option, colorDot = true) { onPick(option.filter) } }
        if (flags.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        flags.forEach { option -> FilterItem(option) { onPick(option.filter) } }
    }
}

@Composable
private fun FilterItem(option: ClipFilterOptionUi, colorDot: Boolean = false, onClick: () -> Unit) {
    val colors = if (option.selected) {
        MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
    } else {
        MenuItemDefaults.menuItemColors()
    }
    // The count trails the label, as the clip menu's keys do.
    val count: @Composable () -> Unit = { Text(option.countLabel, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
    val dot = option.color
    if (option.isFlagged) {
        // `clip-filter-flag-icon`: the flag in its colour, or plain for any flag.
        val tint = option.flag?.let(PrismColors::flag) ?: PicoTheme.colorScheme.labelSecondary
        MenuItem(
            title = { Text(option.label) },
            onClick = onClick,
            leadingIcon = { Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) },
            trailingIcon = count,
            contentColors = colors,
        )
    } else if (colorDot && dot != null) {
        MenuItem(
            title = { Text(option.label) },
            onClick = onClick,
            // `clip-filter-color-dot`: the clip colour itself, as the timeline draws it.
            leadingIcon = { Canvas(Modifier.size(16.dp)) { drawCircle(PrismColors.clip(dot)) } },
            trailingIcon = count,
            contentColors = colors,
        )
    } else {
        MenuItem(title = { Text(option.label) }, onClick = onClick, trailingIcon = count, contentColors = colors)
    }
}

/**
 * The `graded-clip-strip` renderer: the picture clips the filter keeps, in order; thumbnails carry their grade,
 * and a coloured clip its clip colour along the foot. It scrolls to keep the clip being graded in view.
 */
@Composable
fun ClipStrip(
    clips: List<GradeClipUi>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    menu: StripMenuUi = StripMenuUi(),
    onMenu: ((ColorEvent) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // The strip follows the clip being graded (a tap, ↑ / ↓, the playhead): just far enough to show it whole.
    val selectedIndex = clips.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex, clips.size) {
        if (selectedIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        when {
            item == null -> listState.animateScrollToItem(selectedIndex)
            item.offset < info.viewportStartOffset -> listState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
            item.offset + item.size > info.viewportEndOffset ->
                listState.animateScrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
        }
    }
    if (clips.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(message ?: "在剪辑页把视频加到时间线后，可以在这里逐个调色。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        }
        return
    }
    LazyRow(modifier, state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items(clips, key = { it.id }) { clip ->
            var open by remember { mutableStateOf(false) }
            // The menu anchors to its direct parent.
            Box {
                StripThumb(
                    clip,
                    clip.id == selectedId,
                    onClick = { onSelect(clip.id) },
                    onLongClick = onMenu?.let {
                        {
                            onSelect(clip.id)
                            open = true
                        }
                    },
                )
                if (open && onMenu != null) {
                    StripThumbMenu(clip, menu, onDismiss = { open = false }) { event ->
                        open = false
                        onMenu(event)
                    }
                }
            }
        }
    }
}

/**
 * `strip-thumb-menu`: Resolve's thumbnail menu on the Color page — a press held on a thumbnail selects the clip and
 * offers the grade of the clip one or two before it (= / −), copy, paste and reset, and the clip's grade versions.
 * It opens above the thumbnail (the strip sits in the window's lower half).
 */
@Composable
private fun StripThumbMenu(clip: GradeClipUi, menu: StripMenuUi, onDismiss: () -> Unit, onEvent: (ColorEvent) -> Unit) {
    val number = clip.index.toIntOrNull() ?: 1
    val dim = PicoTheme.colorScheme.labelTertiary
    val on = PicoTheme.colorScheme.labelPrimary
    Menu(
        onDismissRequest = onDismiss,
        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
    ) {
        // They still answer when there is no such clip (前面没有片段), dimmed.
        MenuItem(title = { Text("应用前一片段的调色（=）", color = if (number > 1) on else dim) }, onClick = { onEvent(ColorEvent.ApplyPriorGrade(1)) })
        MenuItem(title = { Text("应用前两个片段的调色（−）", color = if (number > 2) on else dim) }, onClick = { onEvent(ColorEvent.ApplyPriorGrade(2)) })
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        MenuItem(title = { Text("复制调色") }, onClick = { onEvent(ColorEvent.CopyGrade) })
        MenuItem(title = { Text("粘贴调色", color = if (menu.canPaste) on else dim) }, onClick = { if (menu.canPaste) onEvent(ColorEvent.PasteGrade) else onDismiss() })
        MenuItem(title = { Text("重置调色") }, onClick = { onEvent(ColorEvent.ResetGrade) })
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (menu.versions.size > 1) {
            menu.versions.forEachIndexed { i, name ->
                val shown = i == menu.version
                MenuItem(
                    title = { Text(name) },
                    subtitle = { Text(if (shown) "正在显示" else "切换到此版本") },
                    onClick = { if (shown) onDismiss() else onEvent(ColorEvent.SelectGradeVersion(i)) },
                    contentColors = if (shown) {
                        MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                    } else {
                        MenuItemDefaults.menuItemColors()
                    },
                )
            }
        }
        MenuItem(title = { Text("新建调色版本（Ctrl+Shift+N）") }, onClick = { onEvent(ColorEvent.NewGradeVersion) })
        if (menu.versions.size > 1) MenuItem(title = { Text("删除当前调色版本") }, onClick = { onEvent(ColorEvent.DeleteGradeVersion) })
    }
}

@Composable
private fun StripThumb(clip: GradeClipUi, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val image = rememberFrameImage(clip.pixels)
    val bar = PicoTheme.colorScheme.interaction
    Column(
        modifier = Modifier
            .width(128.dp)
            .offset(z = if (selected) 12.dp else 0.dp)
            .clip(RoundedCornerShape(8.dp))
            .spatialHoverEffect()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                this.selected = selected
                contentDescription = spoken(
                    // The template filled as it is: an app-made name ("定格 · …") holds the separators a translated value may not.
                    I18n.format("片段 {0} {1}", clip.index, clip.name.localized()),
                    clip.color?.let { "${it.label}色".localized() } ?: "",
                    if (clip.graded) "已调色".localized() else "",
                    if (clip.flags.isEmpty()) "" else labelled("旗标".localized(), listed(clip.flags.map { "${it.label}色".localized() })),
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(128.dp, 72.dp)) {
            if (image != null) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            } else {
                PosterArt(clip.poster, null, radius = 8.dp, modifier = Modifier.fillMaxSize())
            }
            // The clip colour (the strip's `colorBar` part) along the foot; the thumbnail's corners round it off.
            clip.color?.let { color ->
                Canvas(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))) {
                    val height = COLOR_BAR.toPx()
                    drawRect(PrismColors.clip(color), topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
                }
            }
            if (clip.graded) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp)) { drawCircle(PrismColors.MarkerFlag) }
            }
            // The strip's `flags` part: Resolve's flags on the clip's media (the graded dot keeps the other corner).
            FlagMarks(clip.flags, Modifier.align(Alignment.TopStart).padding(4.dp), size = 12.dp)
        }
        Text(
            "${clip.index}  ${clip.name}",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            if (selected) drawRoundRect(bar, cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
}

private val COLOR_BAR = 4.dp
