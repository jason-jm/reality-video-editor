package com.example.prismstudio.p40b43a88.ui.projects.components

import com.example.prismstudio.p40b43a88.ui.components.projectDisplayName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.tr
import com.example.prismstudio.p40b43a88.ui.projects.ProjectCardUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/** `project-gallery` renderer: poster cards in a [columns]-column grid; the selected card lifts. */
@Composable
fun ProjectGallery(
    items: List<ProjectCardUi>,
    unreadableIds: List<String>,
    selectedId: String?,
    columns: Int,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRecover: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    // The selected project in view: a copy or a restored backup is the newest, so the first card, and a keyed grid kept
    // the card that was first in place — the new one, selected, sat above the top (R19).
    LaunchedEffect(selectedId) {
        val index = items.indexOfFirst { it.id == selectedId }
        if (index < 0) return@LaunchedEffect
        val shown = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (shown == null || shown.offset.y < 0) grid.animateScrollToItem(index)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { card ->
            ProjectCard(card, selected = card.id == selectedId, onSelect = { onSelect(card.id) }, onOpen = { onOpen(card.id) })
        }
        items(unreadableIds, key = { it }) { id ->
            val fill = PicoTheme.colorScheme.fillTertiary
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).drawBehind { drawRect(fill) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("项目文件无法读取", style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
                Text("$id.json", translate = false, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Button(onClick = { onRecover(id) }, size = ButtonDefaults.Small) { Text("查看备份") }
            }
        }
    }
}

@Composable
private fun ProjectCard(card: ProjectCardUi, selected: Boolean, onSelect: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (selected) 16.dp else 0.dp)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(role = Role.Button, onClickLabel = tr("选择项目")) { if (selected) onOpen() else onSelect() }
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(12.dp)
            .semantics { contentDescription = spoken(com.example.prismstudio.p40b43a88.domain.i18n.I18n.projectName(card.name), card.meta.localized(), card.modified.localized()) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosterArt(card.poster, card.frame, radius = 16.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1.778f))
        Text(
            projectDisplayName(card.name), translate = false,
            style = PrismType.body,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
        )
        Text(
            "${card.meta} · ${card.modified}",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}
