package com.example.prismstudio.p40b43a88.ui.deliver

import androidx.compose.foundation.layout.height
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.IconButton
import androidx.compose.ui.res.painterResource
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.color.components.ColorMiniTimeline
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.ui.components.APPROXIMATE_PREVIEW_NOTE
import com.example.prismstudio.p40b43a88.ui.components.MonitorContent
import com.example.prismstudio.p40b43a88.ui.components.MonitorView
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import com.example.prismstudio.p40b43a88.ui.deliver.components.RenderQueueColumn
import com.example.prismstudio.p40b43a88.ui.deliver.components.RenderSettingsColumn
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/** The Deliver stage page (state wiring, toasts and opening finished renders). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DeliverScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model: DeliverViewModel = viewModel(factory = remember(context) { DeliverViewModelFactory(context) })
    val state by model.state.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { model.onEvent(DeliverEvent.ToastShown) }
    LaunchedEffect(state.openUri) {
        val uri = state.openUri ?: return@LaunchedEffect
        model.onEvent(DeliverEvent.OpenHandled)
        // A movie, or the mix alone (audio/mp4): the library knows which.
        val type = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull() ?: "video/mp4"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Read access is handed over when the app may (it made the file). A render left by an earlier install is not the
        // app's to hand over — that threw and closed the app (release round R9) — so it is asked for without it: the
        // gallery reads the media library itself.
        for (candidate in listOf(Intent(intent).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), intent)) {
            try {
                context.startActivity(candidate)
                return@LaunchedEffect
            } catch (e: ActivityNotFoundException) {
                model.onEvent(DeliverEvent.OpenFailed(unreachable = false))
                return@LaunchedEffect
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to hand the render to a viewer", e)
            }
        }
        model.onEvent(DeliverEvent.OpenFailed(unreachable = true))
    }
    DeliverContent(state, model::onEvent, modifier)
}

@Composable
fun DeliverContent(state: DeliverUiState, onEvent: (DeliverEvent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 1440.dp
        Row(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RenderSettingsColumn(
                state = state,
                onPreset = { onEvent(DeliverEvent.SelectPreset(it)) },
                onFileName = { onEvent(DeliverEvent.FileName(it)) },
                onCodec = { onEvent(DeliverEvent.Codec(it)) },
                onResolution = { onEvent(DeliverEvent.Resolution(it)) },
                onFps = { onEvent(DeliverEvent.Fps(it)) },
                onQuality = { onEvent(DeliverEvent.Quality(it)) },
                onAudio = { onEvent(DeliverEvent.IncludeAudio(it)) },
                onVideo = { onEvent(DeliverEvent.IncludeVideo(it)) },
                onLoudness = { onEvent(DeliverEvent.Loudness(it)) },
                onSubtitles = { onEvent(DeliverEvent.ExportSubtitles(it)) },
                onSubtitleFormat = { onEvent(DeliverEvent.SubtitleFormat(it)) },
                onAudioFormat = { onEvent(DeliverEvent.AudioFormat(it)) },
                onWavRate = { onEvent(DeliverEvent.WavRate(it)) },
                onWavBits = { onEvent(DeliverEvent.WavBits(it)) },
                onToneMapping = { onEvent(DeliverEvent.ToneMap(it)) },
                onPqPeak = { onEvent(DeliverEvent.PqPeak(it)) },
                onRange = { onEvent(DeliverEvent.Range(it)) },
                onRenderMode = { onEvent(DeliverEvent.RenderMode(it)) },
                onAddJob = { onEvent(DeliverEvent.AddJob) },
                onAddAll = { onEvent(DeliverEvent.AddAllTimelines) },
                modifier = Modifier.width(420.dp).fillMaxHeight(),
            )
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonitorView(
                    content = MonitorContent(
                        pictureAspect = state.pictureAspect,
                        video = !state.programEmpty,
                        message = if (state.programEmpty) "时间线为空" else null,
                        note = if (state.previewApproximate) APPROXIMATE_PREVIEW_NOTE else null,
                        layers = state.programLayers,
                        composites = state.programComposites,
                    ),
                    onSurface = { onEvent(DeliverEvent.ProgramSurface(it)) },
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.estimate, style = PrismType.body, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
                    // `export-still-button`: the highlighted frame as the export draws it.
                    Button(
                        onClick = { onEvent(DeliverEvent.ExportStill) },
                        enabled = !state.programEmpty && !state.exportingStill && !state.rendering,
                        size = ButtonDefaults.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PicoTheme.colorScheme.fillTertiary,
                            contentColor = PicoTheme.colorScheme.labelPrimary,
                        ),
                    ) { Text(if (state.exportingStill) "正在导出…" else "导出当前帧") }
                }
                if (!state.programEmpty) DeliverTimeline(state, onEvent)
            }
            RenderQueueColumn(
                jobs = state.jobs,
                lastRenderSummary = state.lastRenderSummary,
                renderAllReady = !state.nothingQueued,
                onRenderAll = { onEvent(DeliverEvent.RenderAll) },
                onCancel = { onEvent(DeliverEvent.Cancel(it)) },
                onOpen = { onEvent(DeliverEvent.Open(it)) },
                onRetry = { onEvent(DeliverEvent.Retry(it)) },
                modifier = Modifier.width(if (compact) 340.dp else 380.dp).fillMaxHeight(),
                clearDoneEnabled = state.hasDone,
                onClearDone = { onEvent(DeliverEvent.ClearDone) },
            )
        }
    }
}

/**
 * `deliver-timeline-group`: Resolve's Deliver page timeline — the picture tracks with the playhead (a press moves it, for
 * 导出当前帧) and what the render takes shaded; I / O mark its range here too (`deliver-mark-in` / `-mark-out`, 清除).
 */
@Composable
private fun DeliverTimeline(state: DeliverUiState, onEvent: (DeliverEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.rangeLabel, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEvent(DeliverEvent.MarkIn) }, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_mark_in), contentDescription = "在播放头标记入点（I）")
            }
            IconButton(onClick = { onEvent(DeliverEvent.MarkOut) }, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_mark_out), contentDescription = "在播放头标记出点（O）")
            }
            Button(
                onClick = { onEvent(DeliverEvent.ClearMarks) },
                enabled = state.hasMarks,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("清除入出点") }
        }
        ColorMiniTimeline(
            ui = state.miniTimeline,
            playheadFrame = state.playheadFrame,
            fps = state.fps,
            onScrub = { frame, _ -> onEvent(DeliverEvent.Seek(frame)) },
            range = state.renderRange,
            description = "交付时间线，${state.rangeLabel}",
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )
    }
}

private const val TAG = "PrismDeliver"
