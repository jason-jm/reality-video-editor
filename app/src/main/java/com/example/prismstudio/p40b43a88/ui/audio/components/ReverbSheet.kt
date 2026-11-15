package com.example.prismstudio.p40b43a88.ui.audio.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.ReverbRoom
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.ui.audio.ReverbEditorUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet
import java.util.Locale

/**
 * `reverb-sheet`: one track's reverb — Fairlight FX Reverb. A room sets everything at once; 混响时间 is how long the room
 * takes to fall 60 dB, 预延迟 the wait before it answers, 高频衰减 how much sooner the highs die, 干湿比 how much of what is
 * heard is the room. The graph draws the fall, the highs dashed. It acts on every clip of the track after the fader and pan;
 * 重置 switches it off.
 */
@Composable
fun ReverbSheet(
    editor: ReverbEditorUi,
    onClose: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRoom: (ReverbRoom) -> Unit,
    onDecay: (Float) -> Unit,
    onPreDelay: (Float) -> Unit,
    onDamping: (Float) -> Unit,
    onMix: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val r = editor.reverb
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("混响 · ${editor.name}") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // `rv-header-row`
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启用", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(checked = r.enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = "启用混响".localized() })
                Text(
                    if (r.isActive) "作用于这条轨道的所有片段" else "选一个房间，或调高干湿比",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onReset,
                    enabled = r != TrackReverb.OFF,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("重置") }
            }
            // `rv-room-row`: the rooms; the one the settings are stays lit (none once a control moves off it).
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("房间", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.widthIn(min = 72.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReverbRoom.entries.forEach { room ->
                        ToggleableChip(
                            label = { Text(room.label) },
                            isToggleOn = r.room == room,
                            onClick = { onRoom(room) },
                            chipSize = ChipsDefaults.Small,
                            modifier = Modifier.semantics { contentDescription = "混响房间：${room.label}".localized() },
                        )
                    }
                }
            }
            ValueSliderRow(
                "混响时间", r.decaySeconds, TrackReverb.MIN_DECAY_SECONDS..TrackReverb.MAX_DECAY_SECONDS,
                String.format(Locale.US, "%.1f 秒", r.decaySeconds), onDecay, enabled = r.enabled, step = 0.1f,
            )
            ValueSliderRow(
                "预延迟", r.preDelayMs.toFloat(), 0f..TrackReverb.MAX_PRE_DELAY_MS.toFloat(), "${r.preDelayMs} 毫秒", onPreDelay,
                enabled = r.enabled, step = 1f,
            )
            ValueSliderRow("高频衰减", r.damping.toFloat(), 0f..100f, "${r.damping} %", onDamping, enabled = r.enabled, step = 1f)
            ValueSliderRow(
                "干湿比", r.mix.toFloat(), 0f..100f, if (r.mix == 0) "关" else "${r.mix} %", onMix, enabled = r.enabled, step = 1f,
            )
            ReverbDecayGraph(r, Modifier.fillMaxWidth().height(96.dp))
            // `rv-legend-text`
            Text(editor.legend, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            // `rv-note-text`
            Text(editor.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
    }
}

/**
 * `rv-graph`: the room's fall over [TrackReverb.TAIL_SECONDS], drawn as Fairlight's graphs are — flat through the pre-delay,
 * then down 60 dB in the decay time (solid) and, dashed, in the highs' (8 kHz) shorter one; a grid line every 20 dB and
 * every second.
 */
@Composable
private fun ReverbDecayGraph(reverb: TrackReverb, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val grid = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.16f)
    val faint = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.55f)
    val labelStyle = PrismType.caption.copy(color = faint)
    val r = reverb.clamped()
    val description = String.format(Locale.US, "混响衰减曲线：%.1f 秒衰减 60 dB", r.decaySeconds)
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = description.localized() }) {
        val span = TrackReverb.TAIL_SECONDS
        val floorDb = 72f
        fun x(s: Float) = s / span * size.width
        fun y(db: Float) = db / floorDb * size.height
        drawRect(PrismColors.PictureBlack)
        for (db in listOf(20f, 40f, 60f)) drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = 1.dp.toPx())
        var s = 1f
        while (s < span) {
            drawLine(grid, Offset(x(s), 0f), Offset(x(s), size.height), strokeWidth = 1.dp.toPx())
            s += 1f
        }
        drawText(measurer.measure("−60 dB", labelStyle), topLeft = Offset(4.dp.toPx(), y(60f) - 18.dp.toPx()))
        drawText(measurer.measure("6 秒".localized(), labelStyle), topLeft = Offset(x(6f) + 4.dp.toPx(), size.height - 18.dp.toPx()))
        val pre = r.preDelayMs / 1000f
        val alpha = if (reverb.isActive) 1f else 0.55f
        fun fall(decay: Float, effect: PathEffect?) {
            val stop = minOf(pre + decay * floorDb / 60f, span)
            val width = 2.dp.toPx()
            drawLine(line, Offset(0f, y(0f) + width / 2), Offset(x(pre), y(0f) + width / 2), strokeWidth = width, pathEffect = effect, alpha = alpha)
            drawLine(line, Offset(x(pre), y(0f) + width / 2), Offset(x(stop), y((stop - pre) / decay * 60f)), strokeWidth = width, pathEffect = effect, alpha = alpha)
        }
        fall(reverb.decayAt(ReverbEditorUi.HIGH_HZ).toFloat(), PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        fall(r.decaySeconds, null)
    }
}
