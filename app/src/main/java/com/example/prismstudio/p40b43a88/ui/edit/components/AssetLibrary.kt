package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.ui.semantics.selected
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.ui.components.LibraryArtView
import com.example.prismstudio.p40b43a88.ui.edit.EditIndexRowUi
import com.example.prismstudio.p40b43a88.ui.edit.EditUiState
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexFilter
import com.example.prismstudio.p40b43a88.ui.components.TimelineMiniature
import com.example.prismstudio.p40b43a88.ui.media.components.PosterBadge
import com.example.prismstudio.p40b43a88.ui.edit.MarkerUi
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.LibraryTab
import com.example.prismstudio.p40b43a88.ui.components.FlagMarks
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.edit.LibraryItemUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.labelWeight
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `edit-library-column`: media / transitions / titles / effects / markers (the marker index), plus the
 * contextual action — on the markers tab, a marker at the playhead.
 */
@Composable
fun AssetLibrary(
    tab: LibraryTab,
    items: List<LibraryItemUi>,
    selectedId: String?,
    actionLabel: String,
    actionEnabled: Boolean,
    onTab: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onApply: () -> Unit,
    onNeedPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
    markers: List<MarkerUi> = emptyList(),
    selectedMarker: MarkerUi? = null,
    onSelectMarker: (String) -> Unit = {},
    onRenameMarker: (String) -> Unit = {},
    onRecolorMarker: (MarkerColor) -> Unit = {},
    onNoteMarker: (String) -> Unit = {},
    onDeleteMarker: () -> Unit = {},
    onMarkerDuration: (Float) -> Unit = {},
    onMarkerRange: (fromMarks: Boolean) -> Unit = {},
    /** The 索引 tab (rev 148): the edit index ([EditUiState.INDEX_EDITS]) or the marker index. */
    indexView: Int = EditUiState.INDEX_EDITS,
    onIndexView: (Int) -> Unit = {},
    editIndex: List<EditIndexRowUi> = emptyList(),
    editIndexSummary: String = "",
    indexFilter: EditIndexFilter = EditIndexFilter.ALL,
    onIndexFilter: (EditIndexFilter) -> Unit = {},
    onPickIndexRow: (String) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentControl(Modifier.fillMaxWidth()) {
            LibraryTab.entries.forEachIndexed { index, entry ->
                // Each tab as wide as its name in the language shown ("Transitions" needs twice "Media"'s room).
                SegmentItem(
                    selected = entry == tab,
                    onClick = { onTab(index) },
                    title = { Text(entry.label, context = "tab") },
                    modifier = Modifier.weight(labelWeight(entry.label, "tab")),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                )
            }
        }
        if (tab == LibraryTab.MARKERS) {
            // `index-view-segment`: Resolve's Edit Index, or the markers.
            SegmentControl(Modifier.fillMaxWidth()) {
                listOf("片段", "标记").forEachIndexed { i, label ->
                    SegmentItem(selected = i == indexView, onClick = { onIndexView(i) }, title = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (tab == LibraryTab.MARKERS && indexView == EditUiState.INDEX_EDITS) {
            EditIndex(editIndex, editIndexSummary, indexFilter, onIndexFilter, onPickIndexRow, Modifier.weight(1f).fillMaxWidth())
        } else if (tab == LibraryTab.MARKERS) {
            MarkerIndex(
                markers = markers,
                selected = selectedMarker,
                onSelect = onSelectMarker,
                onRename = onRenameMarker,
                onRecolor = onRecolorMarker,
                onNote = onNoteMarker,
                onDelete = onDeleteMarker,
                onDuration = onMarkerDuration,
                onRange = onMarkerRange,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("媒体池里还没有素材", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    if (tab == LibraryTab.MEDIA) LaunchedEffect(item.id) { onNeedPreview(item.id) }
                    LibraryTile(item, selected = item.id == selectedId, onClick = { onSelect(item.id) })
                }
            }
        }
        Button(
            onClick = onApply,
            enabled = actionEnabled,
            size = ButtonDefaults.Regular,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(actionLabel, maxLines = 1) }
    }
}

@Composable
private fun LibraryTile(item: LibraryItemUi, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (selected) 12.dp else 0.dp)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics { contentDescription = spoken(item.title.localized(), item.subtitle.localized()) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PosterArt(item.poster, item.frame, radius = 12.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1.778f)) {
            // A transition, title or effect shows what it does on the sample pictures (the design's glyphs).
            item.art?.let { LibraryArtView(it, Modifier.matchParentSize()) }
            // `library-timeline-tile` (rev 146): a timeline of the project in miniature, badged 复合片段 or 时间线.
            item.miniature?.let { m ->
                TimelineMiniature(m.rows, m.bars, Modifier.matchParentSize())
                PosterBadge(m.badge, error = false, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            // `flags` part: Resolve's flags on the media.
            FlagMarks(item.flags, Modifier.align(Alignment.TopEnd).padding(6.dp))
            item.glyph?.let {
                Text(
                    it,
                    style = PrismType.label,
                    color = PicoTheme.colorScheme.labelPrimaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
                )
            }
        }
        Text(item.title, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
