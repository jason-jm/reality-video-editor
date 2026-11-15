package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.ColorSwatch
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.edit.MarkerUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/**
 * `marker-index`: Resolve's marker index on the Edit page — the timeline's markers in time order (a tap
 * takes the playhead to one and picks it), and the picked marker's name, colour, note and duration, or its deletion.
 */
@Composable
fun MarkerIndex(
    markers: List<MarkerUi>,
    selected: MarkerUi?,
    onSelect: (String) -> Unit,
    onRename: (String) -> Unit,
    onRecolor: (MarkerColor) -> Unit,
    onNote: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** `marker-range-row`: the duration (seconds), and the range taken from the timeline's In / Out (true) or given to them. */
    onDuration: (Float) -> Unit = {},
    onRange: (fromMarks: Boolean) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (markers.isEmpty()) "还没有标记：按 M 或下方按钮在播放头处添加" else "${markers.size} 个标记 · Shift+↑ / ↓ 跳转",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
        )
        val list = rememberLazyListState()
        // The picked marker (from Shift+↑ / ↓, M or a tap) scrolls into view.
        LaunchedEffect(selected?.id) {
            val index = markers.indexOfFirst { it.id == selected?.id }
            if (index >= 0) list.animateScrollToItem(index)
        }
        // With a marker picked the list keeps two rows (the picked one scrolled in) and its editor scrolls in the rest: the
        // editor's rows would otherwise squeeze the list to nothing in a short column.
        LazyColumn(
            if (selected == null) Modifier.weight(1f) else Modifier.fillMaxWidth().height(minOf(PICKED_LIST_HEIGHT, MARKER_ROW_PITCH * markers.size)),
            state = list,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(markers, key = { it.id }) { marker -> MarkerRow(marker, marker.id == selected?.id) { onSelect(marker.id) } }
        }
        selected?.let {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                MarkerEditor(it, onRename, onRecolor, onNote, onDelete, onDuration, onRange)
            }
        }
    }
}

/** `marker-row`: the marker's colour, name and timecode; picked, it is filled. */
@Composable
private fun MarkerRow(marker: MarkerUi, picked: Boolean, onClick: () -> Unit) {
    val fill = if (picked) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    val dot = PrismColors.marker(marker.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .clickable(onClick = onClick)
            .semantics { this.selected = picked }
            .drawBehind { drawRect(fill) }
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = spoken(marker.label.localized(), marker.timecode, marker.note) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).drawBehind { drawRect(dot) })
        Column(Modifier.weight(1f)) {
            Text(marker.label, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (marker.note.isNotEmpty()) {
                Text(marker.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // A duration marker shows its length under its timecode.
        Column(horizontalAlignment = Alignment.End) {
            Text(marker.timecode, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            if (marker.durationSeconds > 0f) {
                Text(marker.rangeText.substringBefore(" · "), style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
        }
    }
}

/**
 * `marker-editor`: the picked marker's name (a blank name is kept while typing, never saved), colour
 * swatches with 删除标记 at their end, note, and `marker-range-row` — Resolve's duration marker: 时长 (0 = a point), 用入出点
 * (the marker moves to the timeline's In and spans to its Out) and 设为入出点 (the timeline's In / Out set to its range, which
 * the Deliver page can render alone).
 */
@Composable
private fun MarkerEditor(
    marker: MarkerUi,
    onRename: (String) -> Unit,
    onRecolor: (MarkerColor) -> Unit,
    onNote: (String) -> Unit,
    onDelete: () -> Unit,
    onDuration: (Float) -> Unit,
    onRange: (fromMarks: Boolean) -> Unit,
) {
    var name by remember(marker.id) { mutableStateOf(marker.label) }
    // An undo (or a rename elsewhere) shows through unless the field is half-way through a new name.
    LaunchedEffect(marker.label) { if (name.trim() != marker.label && name.isNotBlank()) name = marker.label }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = name,
            onValueChange = { name = it; if (it.isNotBlank()) onRename(it) },
            placeholder = { Text("标记名称") },
            singleLine = true,
            clearable = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "标记名称".localized() }.tabMovesFocus(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkerColor.entries.forEach { color ->
                ColorSwatch(PrismColors.marker(color), "标记颜色：${color.label}", color == marker.color, onClick = { onRecolor(color) })
            }
            Spacer(Modifier.weight(1f))
            // `marker-delete-button`
            IconButton(onClick = onDelete, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "删除标记", tint = PicoTheme.colorScheme.error)
            }
        }
        TextField(
            value = marker.note,
            onValueChange = onNote,
            placeholder = { Text("备注（可选）") },
            singleLine = true,
            clearable = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "标记备注".localized() }.tabMovesFocus(),
        )
        ValueSliderRow(
            label = "时长",
            value = marker.durationSeconds,
            range = 0f..marker.maxDurationSeconds.coerceAtLeast(0.1f),
            valueText = if (marker.durationSeconds > 0f) marker.rangeText.substringBefore(" · ") else "一点",
            onValueChange = onDuration,
            step = 0.1f,
            labelWidth = 40.dp,
            minSliderWidth = 80.dp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onRange(true) },
                enabled = marker.canTakeMarks,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
            ) { Text("用入出点") }
            Button(
                onClick = { onRange(false) },
                enabled = marker.durationSeconds > 0f,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
            ) { Text("设为入出点") }
            Text(
                if (marker.durationSeconds > 0f) marker.rangeText.substringAfter(" · ") else if (marker.canTakeMarks) "可用入出点设范围" else "先标记入点和出点",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The marker list's height while a marker's editor is open: its rows, two at most. */
private val PICKED_LIST_HEIGHT = 100.dp

/** A marker row and the gap after it. */
private val MARKER_ROW_PITCH = 52.dp
