package com.example.prismstudio.p40b43a88.ui.audio

import com.example.prismstudio.p40b43a88.ui.audio.components.VoiceoverSheet
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.ui.audio.components.AudioTimelineColumn
import com.example.prismstudio.p40b43a88.ui.audio.components.DuckingSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.DynamicsSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.EqSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.NoiseReductionSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.DeEsserSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.ReverbSheet
import com.example.prismstudio.p40b43a88.ui.audio.components.MixerPanel
import com.example.prismstudio.p40b43a88.ui.audio.components.ProgramLoudnessMeter
import com.example.prismstudio.p40b43a88.ui.components.APPROXIMATE_PREVIEW_NOTE
import com.example.prismstudio.p40b43a88.ui.components.MonitorContent
import com.example.prismstudio.p40b43a88.ui.components.MonitorView
import com.example.prismstudio.p40b43a88.ui.components.TimelineActions
import com.example.prismstudio.p40b43a88.ui.components.ToastEffect
import kotlinx.coroutines.delay

/** The Audio stage page (state wiring and toasts). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model: AudioViewModel = viewModel(factory = remember(context) { AudioViewModelFactory(context) })
    DisposableEffect(model) { onDispose { model.onEvent(AudioEvent.LeaveVoiceover) } }
    val state by model.state.collectAsStateWithLifecycle()
    ToastEffect(state.toast) { model.onEvent(AudioEvent.ToastShown) }
    AudioContent(state, model::onEvent, modifier)
}

@Composable
fun AudioContent(state: AudioUiState, onEvent: (AudioEvent) -> Unit, modifier: Modifier = Modifier) {
    val dispatch by rememberUpdatedState(onEvent)
    val actions = remember {
        object : TimelineActions {
            override fun seek(frame: Int) = dispatch(AudioEvent.Seek(frame))
            override fun select(clipId: String?) = dispatch(AudioEvent.SelectClip(clipId))
            override fun fade(clipId: String, fadeIn: Boolean, seconds: Float, commit: Boolean) =
                dispatch(AudioEvent.Fade(clipId, fadeIn, seconds, commit))
            override fun fadeCurve(clipId: String, fadeIn: Boolean, curve: Float, commit: Boolean) =
                dispatch(AudioEvent.FadeCurve(clipId, fadeIn, curve, commit))
            override fun volume(clipId: String, keyFrames: List<Int>, deltaDb: Float, commit: Boolean) =
                dispatch(AudioEvent.VolumeLine(clipId, keyFrames, deltaDb, commit))
            override fun viewport(widthDp: Float) = dispatch(AudioEvent.Viewport(widthDp))
        }
    }
    state.voiceover?.let { ui ->
        val context = LocalContext.current
        fun canRecord() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var requesting by rememberSaveable { mutableStateOf(false) }
        // Closing the form unregisters its launcher: a delayed result cannot start a different take.
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (requesting) {
                requesting = false
                dispatch(if (granted) AudioEvent.StartVoiceover else AudioEvent.VoiceoverDenied)
            }
        }
        LaunchedEffect(ui.microphoneDenied) {
            if (ui.microphoneDenied) {
                // PICO can keep this spatial window resumed alongside Settings.
                while (!canRecord()) delay(1000)
                dispatch(AudioEvent.VoiceoverPermissionRestored)
            }
        }
        VoiceoverSheet(
            ui = ui,
            onTarget = { onEvent(AudioEvent.VoiceoverTarget(it)) },
            onProgramSound = { onEvent(AudioEvent.VoiceoverProgramSound(it)) },
            onRecord = {
                if (!requesting) {
                    if (canRecord()) onEvent(AudioEvent.StartVoiceover) else {
                        requesting = true
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onOpenSettings = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { onEvent(AudioEvent.VoiceoverSettingsUnavailable) }
            },
            onStop = { onEvent(AudioEvent.StopVoiceover) },
            onDismiss = { onEvent(AudioEvent.LeaveVoiceover) },
            onClose = { onEvent(AudioEvent.CloseVoiceover) },
        )
    }
    state.eqEditor?.let { editor ->
        EqSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseEq) },
            onEnabled = { onEvent(AudioEvent.EqEnabled(it)) },
            onLowCut = { onEvent(AudioEvent.EqLowCut(it)) },
            onReset = { onEvent(AudioEvent.ResetEq) },
            onPreset = { onEvent(AudioEvent.EqPresetPicked(it)) },
            onChange = { c, v -> onEvent(AudioEvent.Eq(c, v)) },
            onDrag = { band, hz, db -> onEvent(AudioEvent.EqDrag(band, hz, db)) },
        )
    }
    state.dynamicsEditor?.let { editor ->
        DynamicsSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseDynamics) },
            onEnabled = { onEvent(AudioEvent.DynamicsEnabled(it)) },
            onCompressor = { onEvent(AudioEvent.DynamicsCompressor(it)) },
            onLimiter = { onEvent(AudioEvent.DynamicsLimiter(it)) },
            onGate = { onEvent(AudioEvent.DynamicsGate(it)) },
            onReset = { onEvent(AudioEvent.ResetDynamics) },
            onPreset = { onEvent(AudioEvent.DynamicsPresetPicked(it)) },
            onChange = { c, v -> onEvent(AudioEvent.Dynamics(c, v)) },
        )
    }
    state.noiseEditor?.let { editor ->
        NoiseReductionSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseNoiseReduction) },
            onEnabled = { onEvent(AudioEvent.NoiseReductionEnabled(it)) },
            onReset = { onEvent(AudioEvent.ResetNoiseReduction) },
            onChange = { c, v -> onEvent(AudioEvent.NoiseReduction(c, v)) },
        )
    }
    state.deEsserEditor?.let { editor ->
        DeEsserSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseDeEsser) },
            onEnabled = { onEvent(AudioEvent.DeEsserEnabled(it)) },
            onAmount = { onEvent(AudioEvent.DeEsserAmount(it)) },
            onBand = { onEvent(AudioEvent.DeEsserBandPicked(it)) },
            onReset = { onEvent(AudioEvent.ResetDeEsser) },
        )
    }
    state.reverbEditor?.let { editor ->
        ReverbSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseReverb) },
            onEnabled = { onEvent(AudioEvent.ReverbEnabled(it)) },
            onRoom = { onEvent(AudioEvent.ReverbRoomPicked(it)) },
            onDecay = { onEvent(AudioEvent.ReverbDecay(it)) },
            onPreDelay = { onEvent(AudioEvent.ReverbPreDelay(it)) },
            onDamping = { onEvent(AudioEvent.ReverbDamping(it)) },
            onMix = { onEvent(AudioEvent.ReverbMix(it)) },
            onReset = { onEvent(AudioEvent.ResetReverb) },
        )
    }
    state.duckingEditor?.let { editor ->
        DuckingSheet(
            editor = editor,
            onClose = { onEvent(AudioEvent.CloseDucking) },
            onEnabled = { onEvent(AudioEvent.DuckingEnabled(it)) },
            onKey = { onEvent(AudioEvent.DuckingKey(it)) },
            onReset = { onEvent(AudioEvent.ResetDucking) },
            onChange = { c, v -> onEvent(AudioEvent.Ducking(c, v)) },
            onPreset = { onEvent(AudioEvent.DuckingPresetPicked(it)) },
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 1440.dp
        Row(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Fairlight's meter bridge sits over the lanes: the whole mix's loudness at the playhead.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgramLoudnessMeter(state.programLoudness, Modifier.fillMaxWidth())
                AudioTimelineColumn(
                    timeline = state.timeline,
                    playheadFrame = state.playheadFrame,
                    playing = state.playing,
                    actions = actions,
                    onTrackLocked = { id, locked -> onEvent(AudioEvent.TrackLocked(id, locked)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onVoiceover = { onEvent(AudioEvent.OpenVoiceover) },
                )
            }
            Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) {
                    MonitorView(
                        content = MonitorContent(
                            pictureAspect = state.pictureAspect,
                            video = !state.programEmpty,
                            message = if (state.programEmpty) "时间线为空" else null,
                            note = if (state.previewApproximate) APPROXIMATE_PREVIEW_NOTE else null,
                            layers = state.programLayers,
                            composites = state.programComposites,
                        ),
                        onSurface = { onEvent(AudioEvent.ProgramSurface(it)) },
                        modifier = Modifier.width(600.dp),
                    )
                }
                // The mixer scrolls in what the monitor leaves: on the device its last rows were squeezed (the 标准化 label
                // clipped to its bottom strokes), and three strips of tracks or buses need more.
                MixerPanel(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    channels = state.channels,
                    masterDb = state.masterDb,
                    masterLevelDb = state.masterLevelDb,
                    selection = state.selection,
                    loudness = state.loudness,
                    loudnessTarget = state.loudnessTarget,
                    onMute = { id, v -> onEvent(AudioEvent.Mute(id, v)) },
                    onSolo = { id, v -> onEvent(AudioEvent.Solo(id, v)) },
                    onPan = { id, v -> onEvent(AudioEvent.Pan(id, v)) },
                    onVolume = { id, v -> onEvent(AudioEvent.Volume(id, v)) },
                    onMaster = { onEvent(AudioEvent.MasterVolume(it)) },
                    onClipGain = { onEvent(AudioEvent.ClipGain(it)) },
                    onLoudnessTarget = { onEvent(AudioEvent.LoudnessTarget(it)) },
                    onNormalize = { onEvent(AudioEvent.NormalizeLoudness) },
                    onEq = { onEvent(AudioEvent.OpenEq(it)) },
                    onDynamics = { onEvent(AudioEvent.OpenDynamics(it)) },
                    onNoise = { onEvent(AudioEvent.OpenNoiseReduction(it)) },
                    onDeEsser = { onEvent(AudioEvent.OpenDeEsser(it)) },
                    onReverb = { onEvent(AudioEvent.OpenReverb(it)) },
                    onDucking = { onEvent(AudioEvent.OpenDucking(it)) },
                    onClipEq = { onEvent(AudioEvent.OpenClipEq) },
                    view = state.mixerView,
                    buses = state.buses,
                    canAddBus = state.canAddBus,
                    onView = { onEvent(AudioEvent.MixerView(it)) },
                    onOutput = { id, bus -> onEvent(AudioEvent.TrackOutput(id, bus)) },
                    onAddBus = { onEvent(AudioEvent.AddBus(it)) },
                    onRemoveBus = { onEvent(AudioEvent.RemoveBus(it)) },
                    onBusMute = { id, v -> onEvent(AudioEvent.BusMute(id, v)) },
                    onBusSolo = { id, v -> onEvent(AudioEvent.BusSolo(id, v)) },
                    onBusVolume = { id, v -> onEvent(AudioEvent.BusVolume(id, v)) },
                    onBusEq = { onEvent(AudioEvent.OpenBusEq(it)) },
                )
            }
        }
    }
}
