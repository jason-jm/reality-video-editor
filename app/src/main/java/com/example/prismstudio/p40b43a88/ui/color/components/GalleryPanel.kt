package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.color.StillUi
import com.example.prismstudio.p40b43a88.ui.components.rememberFrameImage
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
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
 * `gallery-panel`: Resolve's Gallery on the Color page. 抓取静帧 keeps the selected clip's picture at the
 * playhead as its grade shows it, with that grade; pick a still to wipe it against the shot on the monitor
 * (划像对比) while matching, to grade the selected clip the same way (应用调色), or to match the clip's
 * blacks, mids and whites to its picture (匹配静帧, Resolve's Shot Match). Held, a still can be deleted.
 */
@Composable
fun GalleryPanel(
    stills: List<StillUi>,
    selectedStillId: String?,
    wipe: Boolean,
    grabbing: Boolean,
    hasClip: Boolean,
    onGrab: () -> Unit,
    onSelect: (String) -> Unit,
    onApply: () -> Unit,
    onWipe: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    matching: Boolean = false,
    onMatch: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onGrab,
                enabled = hasClip && !grabbing,
                size = ButtonDefaults.Small,
                leadingIcon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null) },
                modifier = Modifier.semantics { contentDescription = "抓取静帧：播放头处的画面和调色".localized() },
            ) { Text(if (grabbing) "正在抓取…" else "抓取静帧") }
            Button(
                onClick = onApply,
                enabled = hasClip && selectedStillId != null,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("应用调色") }
            // `match-still-button`: Shot Match — the clip's levels and colour onto the still's picture.
            Button(
                onClick = onMatch,
                enabled = hasClip && selectedStillId != null && !matching,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "匹配静帧：按静帧的暗部、中间调和亮部调整当前片段".localized() },
            ) { Text(if (matching) "正在匹配…" else "匹配静帧") }
            ToggleableChip(
                label = { Text("划像对比") },
                isToggleOn = wipe,
                onClick = { onWipe(!wipe) },
                enabled = selectedStillId != null,
            )
            Spacer(Modifier.weight(1f))
            Text("${stills.size} 张静帧", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        if (stills.isEmpty()) {
            Text(
                "还没有静帧：在播放头处抓取一张，用来对比镜头、复用调色",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(stills, key = { it.id }) { still ->
                    StillTile(still, still.id == selectedStillId, onSelect = { onSelect(still.id) }, onDelete = { onDelete(still.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StillTile(still: StillUi, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val image = rememberFrameImage(still.pixels)
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    var menu by remember { mutableStateOf(false) }
    if (menu) {
        Menu(
            onDismissRequest = { menu = false },
            positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
        ) {
            MenuItem(
                title = { Text("删除静帧") },
                onClick = { menu = false; onDelete() },
                contentColors = MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.error),
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (selected) 12.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .combinedClickable(onClick = onSelect, onLongClick = { menu = true })
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics { contentDescription = labelled(still.label.localized(), still.detail.localized()) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.778f)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(PrismColors.PictureBlack) },
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(still.label, style = PrismType.caption, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text(still.detail, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
