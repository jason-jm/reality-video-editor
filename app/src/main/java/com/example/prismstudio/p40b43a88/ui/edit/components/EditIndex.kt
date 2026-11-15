package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexFilter
import com.example.prismstudio.p40b43a88.ui.edit.EditIndexRowUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/**
 * `edit-index`: Resolve's Edit Index — the timeline's clips as numbered edit events in timeline order (`edit-index-filter`:
 * all, pictures, sounds), each its track, record in–out, source and length; a tap takes the playhead there and selects the
 * clip, whose row stays picked (scrolled into view).
 */
@Composable
fun EditIndex(
    rows: List<EditIndexRowUi>,
    summary: String,
    filter: EditIndexFilter,
    onFilter: (EditIndexFilter) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentControl(Modifier.fillMaxWidth()) {
            EditIndexFilter.entries.forEach { f ->
                SegmentItem(selected = f == filter, onClick = { onFilter(f) }, title = { Text(f.label, maxLines = 1) }, modifier = Modifier.weight(1f))
            }
        }
        // `edit-index-summary`
        Text(summary, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        val list = rememberLazyListState()
        val picked = rows.indexOfFirst { it.picked }
        LaunchedEffect(picked) { if (picked >= 0) list.animateScrollToItem(picked) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.clipId }) { row -> EditIndexRow(row) { onPick(row.clipId) } }
        }
    }
}

/** One edit event: number, the clip's kind colour and name over its track and record in–out; its length and source at the end. */
@Composable
private fun EditIndexRow(row: EditIndexRowUi, onClick: () -> Unit) {
    val fill = if (row.picked) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    val kind = when (row.kind) {
        ClipKind.VIDEO -> PrismColors.VideoClip
        ClipKind.AUDIO -> PrismColors.AudioClip
        ClipKind.TITLE -> PrismColors.TitleClip
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .drawBehind { drawRect(fill) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(if (row.enabled) 1f else 0.5f)
            .semantics { contentDescription = spoken("${row.number} ${row.name.localized()}", row.track.localized(), row.times, row.duration.localized()) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.number, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        Box(Modifier.size(width = 4.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).drawBehind { drawRect(kind) })
        Column(Modifier.weight(1f)) {
            Text(row.name, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${row.track} · ${row.times}", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.duration, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
            Text(row.source, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
        }
    }
}
