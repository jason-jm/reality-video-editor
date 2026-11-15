package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.foundation.rememberScrollState
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Button
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.TimelineActions
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi
import com.example.prismstudio.p40b43a88.ui.components.TimelineView
import com.example.prismstudio.p40b43a88.ui.components.TrackHeaders
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/** `audio-timeline-column`: tall audio lanes with waveforms and draggable fade handles. */
@Composable
fun AudioTimelineColumn(
    timeline: TimelineUi,
    playheadFrame: Int,
    playing: Boolean,
    actions: TimelineActions,
    onTrackLocked: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** `voiceover-button`: record a narration against the playing timeline. */
    onVoiceover: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "音轨 · 拖动片段两端的淡化手柄调整淡入淡出",
                style = PrismType.label,
                color = PicoTheme.colorScheme.labelTertiary,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onVoiceover,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "录制旁白：对着播放的节目录一段解说".localized() },
            ) { Text("录制旁白") }
        }
        val laneScroll = rememberScrollState()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Lanes share the column's height (Fairlight-style tall waveforms) within 96–280 dp; more scroll.
            val laneHeight = ((maxHeight - RULER_HEIGHT) / timeline.tracks.size.coerceAtLeast(1)).coerceIn(96.dp, 280.dp)
            Row(Modifier.fillMaxSize()) {
                TrackHeaders(
                    tracks = timeline.tracks,
                    rowHeight = laneHeight,
                    onTrackEnabled = null,
                    onTrackLocked = onTrackLocked,
                    modifier = Modifier.width(120.dp).fillMaxHeight().padding(top = RULER_HEIGHT),
                    scrollState = laneScroll,
                )
                TimelineView(
                    ui = timeline,
                    playheadFrame = playheadFrame,
                    actions = actions,
                    emptyMessage = "时间线上还没有音频片段",
                    trackHeight = laneHeight,
                    rulerHeight = RULER_HEIGHT,
                    showFadeHandles = true,
                    allowEditing = false,
                    volumeEditing = true,
                    followPlayhead = playing,
                    description = "音频时间线",
                    laneScroll = laneScroll,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

private val RULER_HEIGHT = 32.dp
