package com.example.prismstudio.p40b43a88.ui.deliver.components

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.deliver.DeliverUiState
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Option
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField

/**
 * `render-settings-column`: presets, file name, codec / resolution / rate / quality, audio with its
 * loudness normalization, and range.
 */
@Composable
fun RenderSettingsColumn(
    state: DeliverUiState,
    onPreset: (RenderPreset) -> Unit,
    onFileName: (String) -> Unit,
    onCodec: (Int) -> Unit,
    onResolution: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onQuality: (Int) -> Unit,
    onAudio: (Boolean) -> Unit,
    onVideo: (Boolean) -> Unit,
    onLoudness: (Int) -> Unit,
    onSubtitles: (Boolean) -> Unit,
    onSubtitleFormat: (Int) -> Unit,
    onAudioFormat: (Int) -> Unit = {},
    onWavRate: (Int) -> Unit = {},
    onWavBits: (Int) -> Unit = {},
    onToneMapping: (ToneMapping) -> Unit = {},
    onPqPeak: (Int) -> Unit = {},
    onRange: (Int) -> Unit,
    onAddJob: () -> Unit,
    modifier: Modifier = Modifier,
    /** `add-all-timelines-button`: one job per timeline (shown when the project has several). */
    onAddAll: () -> Unit = {},
    /** `render-mode-segment`: one file, or one per clip (Resolve's Individual clips). */
    onRenderMode: (Boolean) -> Unit = {},
) {
    // The settings scroll; 加入渲染队列 stays in reach below them (it used to scroll out of the window).
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("渲染预设", style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary)
            state.presets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        Option(
                            selected = item.selected,
                            onSelectChange = { onPreset(item.preset) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text(item.preset.label, maxLines = 1) }
                    }
                }
            }
            // Tab from the file name goes on to 导出画面 (release round R12): in Compose's own order the field came last, so
            // the keyboard went from it back to the top and never reached the settings below.
            val afterFileName = remember { FocusRequester() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("文件名", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TextField(
                    value = state.settings.fileName,
                    onValueChange = onFileName,
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "文件名".localized() }
                        .focusProperties { next = afterFileName }.tabMovesFocus(),
                )
            }
            // `deliver-timeline-row`: the open timeline is what 加入渲染队列 renders; the Edit page's menu switches it.
            SettingRow("时间线") {
                Text(state.timelineLabel, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
            }
            // `video-row`: Resolve's Export Video; off, only the mix is rendered and the picture's settings step aside.
            SettingRow("导出画面") {
                Switch(
                    checked = state.settings.includeVideo,
                    onCheckedChange = onVideo,
                    modifier = Modifier.focusRequester(afterFileName).semantics { contentDescription = "导出画面".localized() },
                )
            }
            if (state.settings.includeVideo) {
                SettingRow("编码") { Segments(listOf("H.264", "H.265"), state.codecIndex, onCodec) }
                SettingRow("分辨率") { Segments(listOf("720p", "1080p", "4K"), state.resolutionIndex, onResolution) }
                SettingRow("帧率") { Segments(DeliverUiState.FRAME_RATES.map { "$it" }, state.fpsIndex, onFps) }
                // `fps-note`: renders are always a constant rate; what that does with the timeline's.
                state.frameRateNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
                SettingRow("画质") { Segments(listOf("标准", "高", "最佳"), state.qualityIndex, onQuality) }
                // `deliver-hdr-note` / `tone-mapping-row` / `pq-peak-row`: Resolve's colour management on delivery — HDR clips
                // tone mapped into the Rec.709 timeline (a project setting, shown while the timeline has HDR clips).
                state.hdrNote?.let { note ->
                    Text(note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
                    SettingRow("色调映射") { Segments(ToneMapping.entries.map { it.label }, state.colorManagement.toneMapping.ordinal, { onToneMapping(ToneMapping.entries[it]) }) }
                    Text(state.colorManagement.toneMapping.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
                    if (state.pqPresent) {
                        SettingRow("PQ 峰值") {
                            Segments(ColorManagement.PQ_PEAKS.map { "$it nits" }, ColorManagement.PQ_PEAKS.indexOf(state.colorManagement.pqPeakNits), onPqPeak)
                        }
                    }
                }
            } else {
                // `audio-format-row`: the mix alone as AAC or lossless WAV (Resolve's Wave), a WAV at 44.1 / 48 kHz, 16 / 24 bits.
                SettingRow("音频格式") { Segments(AudioExportFormat.entries.map { it.label }, state.settings.audioFormat.ordinal, onAudioFormat) }
                if (state.settings.audioFormat == AudioExportFormat.WAV) {
                    SettingRow("采样率") {
                        Segments(RenderSettings.WAV_RATES.map(RenderSettings::wavRateLabel), RenderSettings.WAV_RATES.indexOf(state.settings.wavSampleRate), onWavRate)
                    }
                    SettingRow("位深") { Segments(RenderSettings.WAV_BITS.map { "$it 位" }, RenderSettings.WAV_BITS.indexOf(state.settings.wavBits), onWavBits) }
                }
                // `audio-format-note`
                Text(state.settings.audioFormatNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            SettingRow("导出音频") {
                // The mix alone always has its sound.
                Switch(
                    checked = state.settings.includeAudio,
                    onCheckedChange = onAudio,
                    enabled = state.settings.includeVideo,
                    modifier = Modifier.semantics { contentDescription = "导出音频".localized() },
                )
            }
            // `loudness-row`: Resolve's Normalize Audio — the export's master gain brings the program to a target (LUFS).
            SettingRow("响度") { Segments(DeliverUiState.LOUDNESS_OPTIONS, state.loudnessIndex, onLoudness) }
            state.loudnessNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
            // `srt-row`: subtitles are burned in either way; this also saves them as Download/Reality Video Editor/<name>.srt or .vtt
            // (`subtitle-format-segment`, Resolve's Export Subtitle formats).
            SettingRow("字幕文件") {
                // The format choice comes in left of the switch, so the switch stays where it was pressed (release round
                // R10: it used to slide left, and a second press to switch it off chose a format instead). The switch is
                // measured first; the segments, which fill what they are given, take the rest.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.settings.exportSubtitles) {
                        Box(Modifier.weight(1f, fill = false)) {
                            Segments(SubtitleFileFormat.entries.map { it.label }, state.settings.subtitleFormat.ordinal, onSubtitleFormat)
                        }
                    }
                    Switch(checked = state.settings.exportSubtitles, onCheckedChange = onSubtitles, modifier = Modifier.semantics { contentDescription = "导出字幕文件".localized() })
                }
            }
            // `subtitle-delivery-note`: the language burned in, and one file per language when 字幕文件 is on.
            state.subtitleNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
            SettingRow("渲染范围") { Segments(listOf("整条时间线", "入点至出点"), state.rangeIndex, onRange) }
            state.rangeNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
            // `render-mode-segment`: Resolve's Render — Single clip / Individual clips (one file per clip in the range).
            SettingRow("渲染方式") { Segments(listOf("单个文件", "单独片段"), state.renderModeIndex) { onRenderMode(it == 1) } }
            // `render-mode-note`
            state.individualNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) }
        }
        // `render-offline-note`: offline media in the render (release R5) — said in the error colour above the button.
        state.offlineNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAddJob, size = ButtonDefaults.Regular, modifier = Modifier.weight(1f)) { Text("加入渲染队列") }
            if (state.severalTimelines) {
                Button(
                    onClick = onAddAll,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "全部时间线加入渲染队列".localized() },
                ) { Text("全部时间线加入") }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed label column: segment controls fill their slot and would squeeze a weighted label.
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, modifier = Modifier.width(98.dp), fit = true, fitLines = 2)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SegmentControl {
        options.forEachIndexed { index, label ->
            SegmentItem(selected = index == selected, onClick = { onSelect(index) }, title = { Text(label, maxLines = 1) })
        }
    }
}
