package com.example.prismstudio.p40b43a88.ui.audio.components

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.audio.VoiceoverUi
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
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
import com.example.prismstudio.p40b43a88.ui.components.BackIgnoringSheet
import com.pico.spatial.ui.design.windows.Sheet
import kotlin.math.log10

/**
 * `voiceover-sheet`: Resolve's Fairlight recording for a narration — the track it goes on (an existing audio track, or
 * a new one), whether the program is heard while recording (off by default: the headset's speakers would reach the
 * microphone), then 开始录音: the program plays from the playhead while the take's time and the microphone's level
 * show; 停止并保存 puts the take on the timeline where it started.
 */
@Composable
fun VoiceoverSheet(
    ui: VoiceoverUi,
    onTarget: (String?) -> Unit,
    onProgramSound: (Boolean) -> Unit,
    onRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    // Back is ignored mid-take. The title-bar close saves; only the explicit discard button cancels.
    CountsAsOpenSheet()
    Sheet(
        modifier = Modifier.fillMaxWidth(),
        properties = if (ui.recording) BackIgnoringSheet else BackDismissSheet,
        onDismissRequest = onDismiss,
        title = { Text("录制旁白") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("放到", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                FlowRow(Modifier.semantics { contentDescription = "旁白放到哪条音轨".localized() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.targets.forEach { target ->
                        ToggleableChip(
                            label = { Text(target.label) },
                            isToggleOn = target.id == ui.targetId,
                            onClick = { onTarget(target.id) },
                            enabled = !ui.recording,
                            chipSize = ChipsDefaults.Small,
                        )
                    }
                }
                Text(ui.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("录音时播放节目声音", style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
                    Text("戴耳机时再打开：外放的声音会被麦克风录进去", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
                }
                Switch(
                    checked = ui.programSound,
                    onCheckedChange = onProgramSound,
                    enabled = !ui.recording,
                    modifier = Modifier.semantics { contentDescription = "录音时播放节目声音".localized() },
                )
            }
            Text(ui.startLabel, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            if (!ui.recording) Text(RECORD_FORMAT, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            ui.recordingError?.let { message ->
                Text(message, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (ui.microphoneDenied) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "需要麦克风权限才能录制旁白：请在系统设置里允许 ${PRODUCT_NAME} 使用麦克风",
                        style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(onClick = onOpenSettings, size = ButtonDefaults.Small, colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary,
                    )) { Text("去设置") }
                }
            }
            if (ui.recording) {
                Text("离开音频页或关闭窗口时，会保存已录的旁白。", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.elapsedLabel, style = PrismType.title, color = PrismColors.MeterClip)
                    LevelMeter(ui.level, Modifier.weight(1f).height(12.dp).semantics { contentDescription = "麦克风电平".localized() })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                Button(
                    onClick = onClose,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text(if (ui.recording) "放弃这段" else "取消") }
                Button(onClick = if (ui.recording) onStop else onRecord, size = ButtonDefaults.Regular) {
                    Text(if (ui.recording) "停止并保存" else "开始录音")
                }
            }
        }
    }
}

/** The microphone's level as a bar on a dB scale (−60…0 dBFS), safe / hot / clipping as the mixer's meters colour it. */
@Composable
private fun LevelMeter(level: Float, modifier: Modifier) {
    val track = PicoTheme.colorScheme.fillTertiary
    Canvas(modifier) {
        val db = if (level <= 0f) FLOOR_DB else (20f * log10(level)).coerceIn(FLOOR_DB, 0f)
        val share = (db - FLOOR_DB) / -FLOOR_DB
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(track, size = size, cornerRadius = radius)
        val color = when {
            db > -3f -> PrismColors.MeterClip
            db > -12f -> PrismColors.MeterHot
            else -> PrismColors.MeterSafe
        }
        if (share > 0f) drawRoundRect(color, size = Size(size.width * share, size.height), cornerRadius = radius)
    }
}

private const val FLOOR_DB = -60f

/** What a take is recorded as (see the recorder repository): uncompressed, so it lines up with the picture to the frame. */
private const val RECORD_FORMAT = "录成不压缩的 WAV 文件（48 kHz · 16 位 · 单声道），和画面逐帧对齐"
