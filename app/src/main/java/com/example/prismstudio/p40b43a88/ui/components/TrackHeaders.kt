package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.usecase.TracksUseCase
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ToggleIconButton
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/**
 * Track header rows: name, output (eye / speaker) and lock toggles, and — with [onRemove] — a delete
 * button on the tracks that can go (`track-delete-button`; not V1 / A1).
 */
@Composable
fun TrackHeaders(
    tracks: List<Track>,
    rowHeight: Dp,
    onTrackEnabled: ((String, Boolean) -> Unit)?,
    onTrackLocked: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((String) -> Unit)? = null,
    /** The destination controls (Edit page): where the source viewer's edits land now; a tap on a track's name patches it. */
    targets: EditTargets? = null,
    onTarget: ((String) -> Unit)? = null,
    /** `track-name-rename`: a press held on a track's name renames it (Resolve: double-click the name). */
    onRename: ((String) -> Unit)? = null,
    /** `subtitle-language-chip`: a subtitle track's language — a tap picks another, or adds a track for one more. */
    onLanguage: ((String, SubtitleLanguage) -> Unit)? = null,
    onAddSubtitle: (() -> Unit)? = null,
    /** `timeline` part laneScroll: the rows scroll with the lanes beside them (a drag on the headers scrolls both). */
    scrollState: ScrollState? = null,
) {
    Column(if (scrollState == null) modifier else modifier.verticalScroll(scrollState)) {
        tracks.forEach { track ->
            Row(
                modifier = Modifier.height(rowHeight).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.kind == TrackKind.SUBTITLE && onLanguage != null) {
                    LanguageChip(track, tracks, onLanguage, onAddSubtitle, onRename)
                } else if (targets != null && onTarget != null && track.kind != TrackKind.SUBTITLE) {
                    TargetChip(track, targets, onTarget, onRename)
                } else {
                    Text(
                        track.name,
                        style = PrismType.label,
                        color = PicoTheme.colorScheme.labelSecondary,
                        maxLines = 1,
                        modifier = Modifier.width(48.dp).let { m ->
                            if (onRename == null) m else m.combinedClickable(onClick = {}, onLongClick = { onRename(track.id) })
                        },
                    )
                }
                if (onTrackEnabled != null) {
                    val video = track.kind != TrackKind.AUDIO
                    ToggleIconButton(checked = track.enabled, onCheckedChange = { onTrackEnabled(track.id, it) }) {
                        Icon(
                            painterResource(if (video) R.drawable.ic_eye else R.drawable.ic_volume_loud),
                            contentDescription = if (video) "轨道显示 ${track.name}" else "轨道声音 ${track.name}",
                        )
                    }
                }
                ToggleIconButton(checked = track.locked, onCheckedChange = { onTrackLocked(track.id, it) }) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = "锁定轨道 ${track.name}")
                }
                if (onRemove != null && TracksUseCase.canRemove(track)) {
                    IconButton(onClick = { onRemove(track.id) }, enabled = !track.locked, size = IconButtonDefaults.Small) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "删除轨道 ${track.name}")
                    }
                }
            }
        }
    }
}

/**
 * `subtitle-language-chip`: a subtitle track's language in short (简中, EN …), filled with the subtitle colour while the
 * track is the one on screen. A tap opens the languages — the track's own lit, those other tracks use named and dimmed —
 * after 新建字幕轨 for one more language; a press held renames the track.
 */
@Composable
private fun LanguageChip(
    track: Track,
    tracks: List<Track>,
    onLanguage: (String, SubtitleLanguage) -> Unit,
    onAddSubtitle: (() -> Unit)?,
    onRename: ((String) -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    val language = track.subtitleLanguage
    val fill = if (track.enabled) PrismColors.TitleClip else Color.Transparent
    val label = if (track.enabled) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelSecondary
    val usedBy = tracks.filter { it.kind == TrackKind.SUBTITLE && it.id != track.id }.associate { it.subtitleLanguage to it.name }
    Box(
        modifier = Modifier
            .size(48.dp, 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .spatialHoverEffect()
            .combinedClickable(onClick = { open = true }, onLongClick = onRename?.let { { it(track.id) } })
            .drawBehind { drawRect(fill) }
            .semantics { contentDescription = "${track.name} 字幕语言：${language.label}，点按更换".localized() },
        contentAlignment = Alignment.Center,
    ) {
        Text(language.short, style = PrismType.label, color = label, maxLines = 1)
        if (open) {
            // Above the chip: subtitle tracks top the stack, in the window's lower half — opened below, the ten rows ran
            // off the screen and hid 新建字幕轨.
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                // First: the menu's height is capped (it scrolls), and a last row was cut off under its edge.
                if (onAddSubtitle != null && tracks.count { it.kind == TrackKind.SUBTITLE } < TracksUseCase.MAX_SUBTITLE_TRACKS) {
                    MenuItem(title = { Text("新建字幕轨（另一种语言）") }, onClick = { open = false; onAddSubtitle() })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                SubtitleLanguage.entries.forEach { lang ->
                    val other = usedBy[lang]
                    MenuItem(
                        title = { Text(lang.label) },
                        onClick = {
                            open = false
                            if (lang != language && other == null) onLanguage(track.id, lang)
                        },
                        subtitle = other?.let { { Text("$it 在用") } },
                        contentColors = when {
                            lang == language -> MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                            other != null -> MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelTertiary)
                            else -> MenuItemDefaults.menuItemColors()
                        },
                    )
                }
            }
        }
    }
}

/**
 * `track-target-chip`: Resolve's destination control — the track's name, filled with its clips' colour
 * while the source viewer's edits put the picture (or the sound) on it. A tap makes the track the target;
 * on the sound target it hands the sound back to automatic.
 */
@Composable
private fun TargetChip(track: Track, targets: EditTargets, onTarget: (String) -> Unit, onRename: ((String) -> Unit)?) {
    val video = track.kind == TrackKind.VIDEO
    val on = track.id == if (video) targets.video else targets.audio
    val fill = when {
        !on -> Color.Transparent
        video -> PrismColors.VideoClip
        else -> PrismColors.AudioClip
    }
    val label = if (on) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelSecondary
    Box(
        modifier = Modifier
            .size(48.dp, 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .spatialHoverEffect()
            .combinedClickable(onClick = { onTarget(track.id) }, onLongClick = onRename?.let { { it(track.id) } })
            .drawBehind { drawRect(fill) }
            .semantics {
                contentDescription = when {
                    on && video -> "${track.name}：画面目标轨道"
                    on -> "${track.name}：声音目标轨道，再点恢复自动"
                    video -> "把 ${track.name} 设为画面目标轨道"
                    else -> "把 ${track.name} 设为声音目标轨道"
                }.localized()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(track.name, style = PrismType.label, color = label, maxLines = 1)
    }
}
