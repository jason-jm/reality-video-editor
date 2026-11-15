package com.example.prismstudio.p40b43a88.ui.color.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.ui.components.TrackUi
import com.example.prismstudio.p40b43a88.ui.components.TrackRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.ui.components.KeyframeAction
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.sliderLabelWidth
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip

/**
 * `window-panel`: Resolve's Window palette for node 2 — the shape (无 removes the window), whether it
 * corrects inside or outside (反转), its dynamic keyframes, and its size, aspect, angle, soft edge and
 * position (as they stand at the playhead).
 */
@Composable
fun WindowPanel(
    window: PowerWindow?,
    keys: KeyframeUi,
    enabled: Boolean,
    onShape: (WindowShape?) -> Unit,
    onInvert: (Boolean) -> Unit,
    onChange: (WindowControl, Float) -> Unit,
    onKeyframe: (KeyframeAction) -> Unit,
    modifier: Modifier = Modifier,
    track: TrackUi = TrackUi(),
    onTrack: () -> Unit = {},
    onCancelTrack: () -> Unit = {},
    onTrackMotion: (TrackMotion) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SegmentControl(Modifier.weight(1f)) {
                SegmentItem(selected = window == null, onClick = { onShape(null) }, title = { Text("无") })
                WindowShape.entries.forEach { shape ->
                    SegmentItem(selected = window?.shape == shape, onClick = { onShape(shape) }, title = { Text(shape.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("反转", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(
                    checked = window?.invert == true,
                    onCheckedChange = onInvert,
                    enabled = enabled && window != null,
                    modifier = Modifier.semantics { contentDescription = "反转窗口：调整窗口外".localized() },
                )
            }
        }
        // The full frame (a qualifier alone) has no geometry to animate.
        val geometric = enabled && window != null && window.shape != WindowShape.FULL
        KeyframeRow(keys, geometric, onKeyframe)
        TrackRow(
            track, enabled, onTrack, onCancelTrack,
            buttonLabel = "跟踪窗口",
            buttonDescription = "跟踪窗口：从播放头向前和向后跟随画面",
            idleHint = "从播放头向前、向后跟随窗口里的画面，沿途添加关键帧",
            rowDescription = "窗口跟踪",
            onMotion = onTrackMotion,
            // The palette has a fixed height: the chips share the row, so both slider rows still fit below.
            inlineMotion = true,
        )
        // Without a window the sliders show the defaults a new one starts from.
        val shown = window ?: PowerWindow()
        val labelWidth = sliderLabelWidth(WindowControl.entries.map { it.label }, 56.dp, 88.dp)
        WindowControl.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { control ->
                    ValueSliderRow(
                        label = control.label,
                        value = control.read(shown),
                        range = control.min..control.max,
                        valueText = valueText(control, control.read(shown)),
                        onValueChange = { onChange(control, it) },
                        // Keyed, the window changes at the playhead, which must be inside the clip.
                        enabled = enabled && window != null && control.appliesTo(shown.shape) && !keys.blocked,
                        step = control.step,
                        minSliderWidth = 56.dp,
                        labelWidth = labelWidth,
                        valueWidth = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** What the window keyframe row asks for. */
/**
 * `window-keyframe-row`: Resolve's dynamic keyframes for the window — jump between them, add or remove
 * one at the playhead, and ease the motion into or out of the one there. Once keyed, moving or
 * reshaping the window records it at the playhead, so it can follow a moving subject.
 */
@Composable
private fun KeyframeRow(keys: KeyframeUi, enabled: Boolean, onAction: (KeyframeAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = "窗口关键帧".localized() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("关键帧", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        IconButton(onClick = { onAction(KeyframeAction.PREVIOUS) }, enabled = enabled && keys.hasPrevious, size = IconButtonDefaults.Small) {
            Icon(painterResource(R.drawable.ic_player_previous), contentDescription = "上一个窗口关键帧")
        }
        Button(
            onClick = { onAction(KeyframeAction.TOGGLE) },
            enabled = enabled && keys.playheadInside,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) { Text(if (keys.atKeyframe) "删除关键帧" else "添加关键帧") }
        IconButton(onClick = { onAction(KeyframeAction.NEXT) }, enabled = enabled && keys.hasNext, size = IconButtonDefaults.Small) {
            Icon(painterResource(R.drawable.ic_player_next), contentDescription = "下一个窗口关键帧")
        }
        if (keys.count > 0) {
            Text("${keys.count} 个", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
            ToggleableChip(
                label = { Text("缓入") },
                isToggleOn = keys.easeIn,
                onClick = { onAction(KeyframeAction.EASE_IN) },
                enabled = enabled && keys.canEaseIn,
                chipSize = ChipsDefaults.Small,
            )
            ToggleableChip(
                label = { Text("缓出") },
                isToggleOn = keys.easeOut,
                onClick = { onAction(KeyframeAction.EASE_OUT) },
                enabled = enabled && keys.canEaseOut,
                chipSize = ChipsDefaults.Small,
            )
        }
        Text(
            if (enabled) keys.hint else "选择窗口形状后，可以添加关键帧",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun valueText(control: WindowControl, v: Float): String = when (control) {
    WindowControl.SIZE, WindowControl.ASPECT -> formatValue(v, 2, false)
    WindowControl.ANGLE -> formatValue(v, 0, true, "°")
    WindowControl.SOFTNESS -> formatValue(v * 100f, 0, false, "%")
    WindowControl.CENTER_X, WindowControl.CENTER_Y -> formatValue(v, 2, true)
}
