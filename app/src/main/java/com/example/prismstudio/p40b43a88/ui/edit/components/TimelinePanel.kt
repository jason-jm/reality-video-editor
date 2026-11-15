package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.CurvesUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.ui.components.TimelineActions
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi
import com.example.prismstudio.p40b43a88.ui.components.TimelineView
import com.example.prismstudio.p40b43a88.ui.components.TrackHeaders
import com.example.prismstudio.p40b43a88.ui.components.TrackingSlider
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SliderDefaults
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip

/**
 * `timeline-panel`: summary, track and zoom controls, track headers and the timeline itself. Lanes share the panel's
 * height (72 dp each down to 40 dp); more tracks than that holds scroll up and down under the ruler, headers and lanes
 * together (`timeline` part laneScroll).
 */
@Composable
fun TimelinePanel(
    timeline: TimelineUi,
    playheadFrame: Int,
    summary: String,
    playing: Boolean,
    actions: TimelineActions,
    canAddVideoTrack: Boolean,
    canAddAudioTrack: Boolean,
    onZoom: (Float) -> Unit,
    onFit: () -> Unit,
    onTrackEnabled: (String, Boolean) -> Unit,
    onTrackLocked: (String, Boolean) -> Unit,
    onAddTrack: (TrackKind) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onImportSubtitles: () -> Unit,
    modifier: Modifier = Modifier,
    clipMenu: (@Composable (clipId: String?, onDismiss: () -> Unit) -> Unit)? = null,
    /** `multi-select-chip`: taps add clips to the selection or take them out (for hands and rays). */
    multiSelect: Boolean = false,
    onMultiSelect: (Boolean) -> Unit = {},
    /** `track-target-chip`: a tap on a track's name makes it the destination of the source viewer's edits. */
    onTarget: ((String) -> Unit)? = null,
    onRenameTrack: ((String) -> Unit)? = null,
    /** `subtitle-language-chip`: a subtitle track's language, and a new subtitle track for another. */
    onSubtitleLanguage: ((String, com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage) -> Unit)? = null,
    onAddSubtitleTrack: (() -> Unit)? = null,
    /** `clear-marks-button`: shown while timeline In / Out are set. */
    hasMarks: Boolean = false,
    onClearMarks: () -> Unit = {},
    /** `lift-range-button` / `extract-range-button`: shown while both marks are set. */
    hasRange: Boolean = false,
    onLiftRange: () -> Unit = {},
    onExtractRange: () -> Unit = {},
    /** `timeline-settings-button`: the timeline's raster and how media of another shape fill it. */
    onTimelineSettings: () -> Unit = {},
    /** `timeline-menu-button`: the project's timelines — open another, new, duplicate, delete. */
    timelineMenu: @Composable () -> Unit = {},
    /** `curve-mode-chip` / `keyframe-curve-panel`: the selected clip's keyframe curves in place of the tracks (null: off). */
    curves: CurvesUi? = null,
    onCurveMode: (Boolean) -> Unit = {},
    onCurve: (EditEvent) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                timelineMenu()
                Text(summary, style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
                SecondaryButton("时间线设置", enabled = true, onClick = onTimelineSettings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // `lift-range-button` / `extract-range-button`: Resolve's delete of the In–Out range on every
                // unlocked track, leaving the gap or closing it (Backspace / Delete with no clip selected).
                if (hasRange) {
                    SecondaryButton("删除区间", enabled = true, onClick = onLiftRange)
                    SecondaryButton("波纹删除区间", enabled = true, onClick = onExtractRange)
                }
                // `clear-marks-button`: Alt+X for hands and rays.
                if (hasMarks) SecondaryButton("清除入出点", enabled = true, onClick = onClearMarks)
                // `curve-mode-chip`: Resolve's Keyframe Curve Editor for the selected clip, in place of the tracks.
                ToggleableChip(
                    label = { Text("曲线") },
                    isToggleOn = curves != null,
                    onClick = { onCurveMode(curves == null) },
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "关键帧曲线编辑器".localized() },
                )
                if (curves == null) ToggleableChip(
                    label = { Text("多选") },
                    isToggleOn = multiSelect,
                    onClick = { onMultiSelect(!multiSelect) },
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "多选：点按片段加入或移出选择，在空白处拖动可框选".localized() },
                )
                // The tracks' tools step aside while the curves are shown.
                if (curves == null) {
                    // `add-video-track-button` / `add-audio-track-button`
                    SecondaryButton("添加视频轨", enabled = canAddVideoTrack) { onAddTrack(TrackKind.VIDEO) }
                    SecondaryButton("添加音频轨", enabled = canAddAudioTrack) { onAddTrack(TrackKind.AUDIO) }
                    // `import-subtitles-button`: an .srt, .vtt or .ass file onto the subtitle track (system document picker).
                    SecondaryButton("导入字幕", enabled = true, onClick = onImportSubtitles)
                    Text("缩放", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                    TrackingSlider(
                        value = timeline.zoom,
                        onValueChange = onZoom,
                        valueRange = 0f..1f,
                        sliderSpec = SliderDefaults.Small,
                        modifier = Modifier.width(200.dp).semantics { contentDescription = "时间线缩放".localized() },
                    )
                    SecondaryButton("适配全片", enabled = true, onClick = onFit)
                }
            }
        }
        if (curves != null) {
            KeyframeCurvePanel(curves, playheadFrame, onCurve, Modifier.weight(1f).fillMaxWidth())
            return@Column
        }
        val laneScroll = rememberScrollState()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val laneHeight = ((maxHeight - RULER) / timeline.tracks.size.coerceAtLeast(1)).coerceIn(MIN_LANE, MAX_LANE)
            Row(Modifier.fillMaxSize()) {
                TrackHeaders(
                    tracks = timeline.tracks,
                    rowHeight = laneHeight,
                    onTrackEnabled = onTrackEnabled,
                    onTrackLocked = onTrackLocked,
                    onRemove = onRemoveTrack,
                    modifier = Modifier.width(248.dp).fillMaxHeight().padding(top = RULER),
                    targets = timeline.targets,
                    onTarget = onTarget,
                    onRename = onRenameTrack,
                    onLanguage = onSubtitleLanguage,
                    onAddSubtitle = onAddSubtitleTrack,
                    scrollState = laneScroll,
                )
                TimelineView(
                    ui = timeline,
                    playheadFrame = playheadFrame,
                    actions = actions,
                    emptyMessage = "从媒体池或素材库添加素材到时间线",
                    trackHeight = laneHeight,
                    followPlayhead = playing,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    clipMenu = clipMenu,
                    allowMarquee = true,
                    selectedFadeHandles = true,
                    markersEditable = true,
                    laneScroll = laneScroll,
                )
            }
        }
    }
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        size = ButtonDefaults.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.fillTertiary,
            contentColor = PicoTheme.colorScheme.labelPrimary,
        ),
    ) { Text(text) }
}

private val RULER = 32.dp
private val MIN_LANE = 40.dp
private val MAX_LANE = 72.dp
