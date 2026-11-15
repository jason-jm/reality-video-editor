package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.labelled
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.usecase.MulticamUseCase
import com.example.prismstudio.p40b43a88.ui.edit.AngleUi
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.InspectorUi
import com.example.prismstudio.p40b43a88.ui.edit.MulticamUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `multicam-panel`: a multicam clip in the inspector — Resolve's multicam viewer. Its angles as tiles with their frames at
 * the playhead, the ones playing the picture and the sound lit; a tap cuts to that angle at the playhead (or, with
 * 整段切换, switches the segment under it), picture and sound together or either alone. 打开 shows the angles' timeline to
 * slip one into step; 展平 replaces the clip with the angles it plays.
 */
@Composable
fun MulticamPanel(inspector: InspectorUi, multicam: MulticamUi, onEvent: (EditEvent) -> Unit, modifier: Modifier = Modifier) {
    val enabled = !inspector.locked
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(multicam.title, style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary)
            // `multicam-summary`
            Text(multicam.summary, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
        }
        // `multicam-mode-row`: what a switch changes, and whether it cuts.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SegmentControl(Modifier.weight(1f).semantics { contentDescription = "机位切换内容".localized() }) {
                MulticamUseCase.SwitchMode.entries.forEach { mode ->
                    SegmentItem(
                        selected = mode.ordinal == multicam.mode,
                        onClick = { onEvent(EditEvent.MulticamMode(mode.ordinal)) },
                        title = { Text(mode.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ToggleableChip(
                label = { Text("整段切换") },
                isToggleOn = multicam.whole,
                onClick = { onEvent(EditEvent.MulticamWhole(!multicam.whole)) },
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "整段切换：不剪切，播放头所在的整段换机位".localized() },
            )
        }
        // `multicam-angle-grid`: two angles a row up to four, three beyond.
        val columns = if (multicam.angles.size <= 4) 2 else 3
        multicam.angles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { angle ->
                    AngleTile(angle, enabled, { onEvent(EditEvent.SwitchAngle(angle.index)) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // `multicam-note`
        Text(multicam.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // `multicam-open-button`
            Button(
                onClick = { onEvent(EditEvent.OpenCompound(inspector.clipId)) },
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = "打开多机位片段 ${multicam.name}：调整机位对齐".localized() },
            ) { Text("打开") }
            // `multicam-flatten-button`
            Button(
                onClick = { onEvent(EditEvent.DecomposeCompound(inspector.clipId)) },
                enabled = enabled,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = "展平多机位片段 ${multicam.name}".localized() },
            ) { Text("展平") }
        }
    }
}

/** `multicam-angle-tile`: the angle's frame at the playhead, its number and camera clip, and what it plays now. */
@Composable
private fun AngleTile(angle: AngleUi, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val image = angle.frame?.let { remember(it) { it.asImageBitmap() } }
    val live = angle.picture || angle.sound
    val fill = if (live) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    val playing = listOfNotNull(if (angle.picture) "画面" else null, if (angle.sound) "声音" else null).joinToString(" · ")
    Column(
        modifier = modifier
            .offset(z = if (angle.picture) 10.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics { contentDescription = spoken(labelled(angle.label.localized(), angle.name.localized()), if (playing.isEmpty()) "" else "正在播放$playing".localized()) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.778f)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(PrismColors.PictureBlack) },
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (!angle.hasPicture) {
                Text("无画面", style = PrismType.caption, color = PicoTheme.colorScheme.labelPrimaryLight)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(angle.label, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
            // `multicam-angle-playing`
            if (playing.isNotEmpty()) Text(playing, style = PrismType.caption, color = PicoTheme.colorScheme.labelPrimary)
        }
        Text(angle.name, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
