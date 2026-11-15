package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme

/**
 * Resolve's Tracker for a window or a mask: why it cannot run (null: it can), a run's progress, and what besides the
 * position it follows ([motion]: Zoom, Rotate).
 */
data class TrackUi(
    val unavailable: String? = null,
    val tracking: Boolean = false,
    val status: String = "",
    val motion: TrackMotion = TrackMotion.ALL,
)

/**
 * `window-track-row` / `mask-track-row`: Resolve's Tracker (Pan / Tilt, Zoom, Rotate) — [buttonLabel] follows the picture
 * under the shape from the playhead to both ends of the clip and keys the shape along it (its keyframes replaced); while
 * it runs the row says how far it is and 取消 stops it, leaving the shape as it was. `…-track-zoom-chip` /
 * `…-track-rotate-chip` choose whether the shape's size and angle follow the subject too ([onMotion]): at the row's end
 * when [inlineMotion] (the Color palette, wide but of a fixed height), else on a row of their own (the scrolling inspector).
 */
@Composable
fun TrackRow(
    track: TrackUi,
    enabled: Boolean,
    onTrack: () -> Unit,
    onCancel: () -> Unit,
    buttonLabel: String,
    buttonDescription: String,
    idleHint: String,
    rowDescription: String,
    onMotion: (TrackMotion) -> Unit = {},
    inlineMotion: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = rowDescription.localized() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("跟踪", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
            val colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary)
            if (track.tracking) {
                Button(onClick = onCancel, size = ButtonDefaults.Small, colors = colors, modifier = Modifier.semantics { contentDescription = "取消跟踪".localized() }) { Text("取消") }
                // `window-track-status` / `mask-track-status`
                Text(track.status, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
            } else {
                // Never disabled (a disabled SpatialUI button can draw its label as blocks, rev 169): when it cannot run it
                // is greyed, and a press has the page say why.
                val ready = enabled && track.unavailable == null
                Button(
                    onClick = { if (enabled) onTrack() },
                    size = ButtonDefaults.Small,
                    colors = if (ready) colors else ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelTertiary),
                    modifier = Modifier.semantics { contentDescription = buttonDescription.localized() },
                ) { Text(buttonLabel) }
                Text(
                    track.unavailable ?: idleHint,
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
            if (inlineMotion) MotionChips(track.motion, onMotion)
        }
        if (!inlineMotion) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { MotionChips(track.motion, onMotion) }
    }
}

/** `…-track-motion-label` and the Zoom / Rotate chips. */
@Composable
private fun MotionChips(motion: TrackMotion, onMotion: (TrackMotion) -> Unit) {
    Text("同时跟随", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    ToggleableChip(
        label = { Text("缩放") },
        isToggleOn = motion.zoom,
        onClick = { onMotion(motion.copy(zoom = !motion.zoom)) },
        chipSize = ChipsDefaults.Small,
        modifier = Modifier.semantics { contentDescription = "跟踪时大小跟随画面缩放".localized() },
    )
    ToggleableChip(
        label = { Text("旋转") },
        isToggleOn = motion.rotate,
        onClick = { onMotion(motion.copy(rotate = !motion.rotate)) },
        chipSize = ChipsDefaults.Small,
        modifier = Modifier.semantics { contentDescription = "跟踪时角度跟随画面旋转".localized() },
    )
}
