package com.example.prismstudio.p40b43a88.ui.audio

import com.example.prismstudio.p40b43a88.domain.model.DuckingPreset
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi
import kotlin.math.roundToInt
import com.example.prismstudio.p40b43a88.domain.model.DuckingControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicsControl
import com.example.prismstudio.p40b43a88.domain.model.EqBand
import com.example.prismstudio.p40b43a88.domain.model.EqControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicsPreset
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.NoiseReductionControl
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.ReverbRoom
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MeterLevelsUseCase

data class MixerChannelUi(
    val trackId: String,
    val name: String,
    val mute: Boolean,
    val solo: Boolean,
    val pan: Float,
    val volumeDb: Float,
    val levelDb: Float,
    /** The track's EQ changes the sound (its `mixer-eq-chip` is lit). */
    val eqActive: Boolean = false,
    /** The track's dynamics change the sound (its `channel-dynamics-chip` is lit). */
    val dynamicsActive: Boolean = false,
    /** The track ducks under another (its `channel-ducking-chip` is lit). */
    val duckingActive: Boolean = false,
    /** The track's noise reduction is on (its `channel-noise-chip` is lit). */
    val noiseActive: Boolean = false,
    /** The track's de-esser is on (its `channel-deesser-chip` is lit). */
    val deEsserActive: Boolean = false,
    /** The effects behind the strip's `channel-effects-chip`, in its menu's order (see [channelEffects]). */
    val effects: List<ChannelEffectUi> = emptyList(),
    /** The submix bus the track goes to (`channel-output-chip`), null for the main output. */
    val busId: String? = null,
    /** Where it goes in short under its name: B1, or null for the main output. */
    val busShort: String? = null,
) {
    /** How many of [effects] change the sound; the chip is lit while any does. */
    val effectCount: Int get() = effects.count { it.active }

    /** The chip's label: 效果, or 效果 2 while two change the sound. */
    val effectsLabel: String get() = if (effectCount == 0) "效果" else "效果 $effectCount"
}

/** A track effect behind the strip's 效果 chip (Fairlight's Effects slots), each opening its own sheet. */
enum class ChannelEffect(val title: String) {
    DUCKING("闪避"),
    NOISE("降噪"),
    DE_ESSER("齿音消除"),
    REVERB("混响"),
}

/** One row of the `channel-effects-menu`: the effect, whether it changes the sound, and what it does, in words. */
data class ChannelEffectUi(val effect: ChannelEffect, val active: Boolean, val summary: String)

/**
 * A track's effects for its strip's menu. [keyName] is the name of the track it ducks under (null when it does not duck
 * under an existing sound track); ducking is active only with one.
 */
fun channelEffects(
    ducking: TrackDucking,
    keyName: String?,
    noise: TrackNoiseReduction,
    deEsser: com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser,
    reverb: TrackReverb = TrackReverb.OFF,
): List<ChannelEffectUi> = listOf(
    ChannelEffectUi(
        ChannelEffect.DUCKING,
        ducking.isActive && keyName != null,
        when {
            !ducking.isOn -> "未开启：另一条轨道有声音时自动压低本轨"
            keyName == null -> "键轨道已不存在：不起作用"
            ducking.depthDb <= 0f -> "深度为 0：声音不变"
            else -> "$keyName 有声音时压低 ${ducking.depthDb.roundToInt()} dB"
        },
    ),
    ChannelEffectUi(
        ChannelEffect.NOISE,
        noise.isActive,
        when {
            !noise.enabled -> "未开启：按测得的底噪逐频段压低噪声"
            noise.reductionDb <= 0f -> "压低量为 0：声音不变"
            else -> "底噪压低 ${noise.reductionDb.roundToInt()} dB"
        },
    ),
    ChannelEffectUi(
        ChannelEffect.DE_ESSER,
        deEsser.isActive,
        when {
            !deEsser.enabled -> "未启用：只压低齿音频段"
            deEsser.amount == 0 -> "强度为 0：声音不变"
            else -> "${deEsser.band.label} 以上 · 强度 ${deEsser.amount}"
        },
    ),
    ChannelEffectUi(
        ChannelEffect.REVERB,
        reverb.isActive,
        when {
            !reverb.enabled -> "未启用：给声音加上房间的回响"
            reverb.mix == 0 -> "干湿比为 0：声音不变"
            else -> "${reverb.room?.label ?: "自定义"} · ${seconds(reverb.decaySeconds)} 秒 · 湿声 ${reverb.mix} %"
        },
    ),
)

/** Seconds to one decimal. */
private fun seconds(s: Float): String = String.format(java.util.Locale.US, "%.1f", s)

/** `reverb-sheet`: one track's reverb (Fairlight FX Reverb), how fast it decays low and high, and what it does. */
data class ReverbEditorUi(
    val trackId: String,
    val name: String,
    val reverb: TrackReverb,
) {
    /** The highs' decay (8 kHz), shorter than [TrackReverb.decaySeconds] by the damping. */
    val highDecaySeconds: Float get() = reverb.decayAt(HIGH_HZ).toFloat()

    /** `rv-legend-text`: the two lines of `rv-graph`. */
    val legend: String get() = "实线：中低频 ${seconds(reverb.decaySeconds)} 秒衰减 60 dB · 虚线：8 kHz ${seconds(highDecaySeconds)} 秒"

    /** What the settings do, in words. */
    val note: String
        get() = when {
            !reverb.enabled -> "未启用：打开后声音带上房间的回响，片段后面有空隙时尾音延续到空隙里"
            reverb.mix == 0 -> "干湿比为 0：声音不变；调高后听到房间的回响"
            else -> "在推子和声像之后；片段后面有空隙时，尾音延续进去（最长 ${seconds(TrackReverb.TAIL_SECONDS)} 秒，素材要有余量）"
        }

    companion object {
        const val HIGH_HZ = 8_000.0
    }
}

/** `deesser-sheet`: one track's de-esser (Fairlight's De-Esser) and what it is doing to the band right now. */
data class DeEsserEditorUi(
    val trackId: String,
    val name: String,
    val deEsser: com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser,
) {
    /** What the settings do, in words. */
    val note: String
        get() = when {
            !deEsser.enabled -> "未启用：打开后只压低齿音频段，人声主体不受影响"
            deEsser.amount == 0 -> "强度为 0：声音不变"
            else -> "${deEsser.band.label} 以上的频段超过 ${deEsser.thresholdDb.toInt()} dBFS 时按 4:1 压低，最多 ${com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser.MAX_REDUCTION_DB.toInt()} dB；在均衡器之后、动态之前"
        }
}

/**
 * The noise reduction sheet of one track (`noise-reduction-sheet`): its settings, and the noise floor it works against —
 * that of the file under the playhead on the track (else its first clip's), in dB per band from [floorFromHz] up at
 * [floorStepHz] apart; empty while it is being measured or when the track has no sound. [status] says which file it is.
 */
data class NoiseReductionEditorUi(
    val trackId: String,
    val name: String,
    val noiseReduction: TrackNoiseReduction,
    val floorDb: List<Float> = emptyList(),
    val floorStepHz: Float = 0f,
    val status: String = "",
)

/** The EQ sheet of one track (`eq-sheet`). */
data class EqEditorUi(
    val trackId: String,
    val name: String,
    val eq: TrackEq,
    /** What it shapes, while it does: a track's sound, or (the Edit page's clip EQ) one clip's, before its track's. */
    val appliesTo: String = "作用于这条轨道的所有片段，在推子之前",
) {
    /** The preset the curve matches, or null for one of its own ([EqPreset.of]). */
    val preset: EqPreset? get() = EqPreset.of(eq)
}

/**
 * The dynamics sheet of one track (`dynamics-sheet`), with what its dynamics hear at the playhead
 * ([inputDb], before them) and how far they turn it down there ([reductionDb], never positive).
 */
data class DynamicsEditorUi(
    val trackId: String,
    val name: String,
    val dynamics: TrackDynamics,
    val inputDb: Float = MeterLevelsUseCase.FLOOR_DB,
    val reductionDb: Float = 0f,
) {
    /** The preset the settings match, or null for settings of their own ([DynamicsPreset.of]). */
    val preset: DynamicsPreset? get() = DynamicsPreset.of(dynamics)
}

/** A track the ducking can be keyed from (`ducking-key-segment`). */
data class DuckKeyUi(val trackId: String, val name: String)

/**
 * The ducking sheet of one track (`ducking-sheet`): its settings, the tracks it can be keyed from and
 * the chosen one that exists ([keyTrackId]), and every [STEP_MS] over the [WINDOW_MS] around the playhead
 * the key's level ([keyLevels], dB) and how far the track is dipped ([amounts], 0..1 of the depth) —
 * empty while it is off; [keyLevelDb] and [duckDb] are those at the playhead.
 */
data class DuckingEditorUi(
    val trackId: String,
    val name: String,
    val ducking: TrackDucking,
    val keys: List<DuckKeyUi>,
    val keyTrackId: String?,
    val status: String,
    val keyLevels: List<Float> = emptyList(),
    val amounts: List<Float> = emptyList(),
    val keyLevelDb: Float = DuckingUseCase.FLOOR_DB,
    val duckDb: Float = 0f,
) {
    /** `ducking-preset-row`: the preset the settings match, or null. */
    val preset: DuckingPreset? get() = DuckingPreset.of(ducking)

    /** The switch: on with an existing key track. */
    val on: Boolean get() = ducking.enabled && keyTrackId != null

    companion object {
        /** The graph shows this much of the timeline, the playhead in the middle. */
        const val WINDOW_MS = 8_000L
        const val STEP_MS = 50L
    }
}

/** The selected sound clip; with volume keyframes [gainDb] is the level at the playhead. */
data class AudioClipUi(
    val clipId: String,
    val name: String,
    val gainDb: Float,
    val locked: Boolean,
    /** Keyed volume with the playhead outside the clip: the level cannot be adjusted. */
    val gainBlocked: Boolean = false,
    /** `audio-clip-eq-row`: what the clip's own equaliser does ([eqSummary]) and whether it changes the sound. */
    val eqSummary: String = "平直",
    val eqActive: Boolean = false,
)

/** `audio-loudness-block`: the selected sound clip's loudness as the mix plays it. */
data class LoudnessUi(
    /** "-23.9 LUFS · 真峰值 -20.6 dBTP", or why there is no reading. */
    val readout: String = NO_SELECTION,
    val canNormalize: Boolean = false,
) {
    companion object {
        const val NO_SELECTION = "选择音频片段后测量响度"
    }
}

/**
 * `program-loudness-meter`: Fairlight's loudness meter for the whole mix, at the playhead — momentary
 * (400 ms), short-term (3 s), the program's integrated loudness and loudness range, and its peak.
 */
data class ProgramLoudnessUi(
    /** LUFS readings, "—" when silent or not measured. */
    val momentary: String = NO_READING,
    val shortTerm: String = NO_READING,
    val integrated: String = NO_READING,
    /** "6.2 LU". */
    val range: String = NO_READING,
    /** The program's true peak, "-1.3 dBTP". */
    val peak: String = NO_READING,
    /** Short-term loudness over the [HISTORY_SECONDS] up to the playhead, one per 100 ms, oldest first (NaN: silent). */
    val history: List<Float> = emptyList(),
    /** Momentary loudness over the same span. */
    val momentaryHistory: List<Float> = emptyList(),
    /** The reference line: the loudness target chosen on the page. */
    val targetLufs: Float = -14f,
    /** The short-term loudness is more than 1 LU over the target. */
    val hot: Boolean = false,
    /** The program's true peak is above −1 dBTP. */
    val clipping: Boolean = false,
    /** Why the readings are missing or partial ("正在测量节目响度…"), or what they leave out. */
    val note: String? = null,
) {
    companion object {
        const val NO_READING = "—"
        const val HISTORY_SECONDS = 30
    }
}

/**
 * `bus-strip`: a submix bus — its fader, mute, solo, meter after the fader, whether its EQ changes the sound and the tracks
 * that go to it ([members]).
 */
data class MixerBusUi(
    val busId: String,
    val name: String,
    val short: String,
    val mute: Boolean,
    val solo: Boolean,
    val volumeDb: Float,
    val levelDb: Float,
    val eqActive: Boolean,
    val members: String,
)

data class AudioUiState(
    /** The program monitor shows a flattened approximation of composited overlays / transitions. */
    val previewApproximate: Boolean = false,
    /** Picture layers the program monitor stacks. */
    val programLayers: Int = 1,
    /** Each program layer's composite mode at the playhead (Resolve's Composite Mode), bottom first. */
    val programComposites: List<CompositeMode> = emptyList(),
    val timeline: TimelineUi = TimelineUi(),
    val playheadFrame: Int = 0,
    val playing: Boolean = false,
    val fps: Int = 30,
    val selection: AudioClipUi? = null,
    val channels: List<MixerChannelUi> = emptyList(),
    /** `mixer-view-segment`: 0 the tracks' strips, 1 the submix buses'. */
    val mixerView: Int = 0,
    val buses: List<MixerBusUi> = emptyList(),
    /** Fewer buses than [com.example.prismstudio.p40b43a88.domain.model.MixerState.MAX_BUSES]: one more can be added. */
    val canAddBus: Boolean = true,
    val masterDb: Float = 0f,
    val masterLevelDb: Float = -60f,
    val pictureAspect: Float = 16f / 9f,
    val programEmpty: Boolean = true,
    val toast: String? = null,
    val loudness: LoudnessUi = LoudnessUi(),
    /** Index into [LoudnessUseCase.TARGETS_LUFS]. */
    val loudnessTarget: Int = 0,
    val programLoudness: ProgramLoudnessUi = ProgramLoudnessUi(),
    /** The open EQ sheet, or null. */
    val eqEditor: EqEditorUi? = null,
    /** The open dynamics sheet, or null. */
    val dynamicsEditor: DynamicsEditorUi? = null,
    /** The open noise reduction sheet (`noise-reduction-sheet`). */
    val noiseEditor: NoiseReductionEditorUi? = null,
    /** The open de-esser sheet (`deesser-sheet`). */
    val deEsserEditor: DeEsserEditorUi? = null,
    /** The open reverb sheet (`reverb-sheet`). */
    val reverbEditor: ReverbEditorUi? = null,
    /** The open ducking sheet, or null. */
    val duckingEditor: DuckingEditorUi? = null,
    /** The open `voiceover-sheet`, or null. */
    val voiceover: VoiceoverUi? = null,
) {
    val nothingSelected: Boolean get() = selection == null

    companion object {
        val LOUDNESS_TARGET_LABELS: List<String> = LoudnessUseCase.TARGETS_LUFS.map { "${it.roundToInt()} LUFS" }
    }
}

/** A track a narration can go on in `voiceover-sheet` ([id] null: a new audio track below the others). */
data class VoiceTargetUi(val id: String?, val label: String)

/**
 * `voiceover-sheet`: where the take goes ([targetId], null for a new track), whether the program is heard while it
 * records, and the take as it records — [elapsedLabel] and the microphone's [level] (0…1).
 */
data class VoiceoverUi(
    val targets: List<VoiceTargetUi>,
    val targetId: String?,
    val startLabel: String,
    val programSound: Boolean = false,
    val recording: Boolean = false,
    val elapsedLabel: String = "00:00",
    val level: Float = 0f,
    val note: String = "",
    val microphoneDenied: Boolean = false,
    val recordingError: String? = null,
)

sealed interface AudioEvent {
    data class SelectClip(val clipId: String?) : AudioEvent
    data class Seek(val frame: Int) : AudioEvent
    data class Fade(val clipId: String, val fadeIn: Boolean, val seconds: Float, val commit: Boolean) : AudioEvent
    /** A fade's curve dot dragged on the timeline: the fade bent to [curve], −1…1. */
    data class FadeCurve(val clipId: String, val fadeIn: Boolean, val curve: Float, val commit: Boolean) : AudioEvent
    /** A volume line drag on the timeline (see [com.example.prismstudio.p40b43a88.ui.components.TimelineActions.volume]). */
    data class VolumeLine(val clipId: String, val keyFrames: List<Int>, val deltaDb: Float, val commit: Boolean) : AudioEvent
    data class TrackLocked(val trackId: String, val locked: Boolean) : AudioEvent
    data class Mute(val trackId: String, val mute: Boolean) : AudioEvent
    data class Solo(val trackId: String, val solo: Boolean) : AudioEvent
    data class Pan(val trackId: String, val pan: Float) : AudioEvent
    data class Volume(val trackId: String, val db: Float) : AudioEvent
    data class MasterVolume(val db: Float) : AudioEvent
    data class ClipGain(val db: Float) : AudioEvent
    data class Viewport(val widthDp: Float) : AudioEvent
    data class ProgramSurface(val change: SurfaceChange) : AudioEvent
    data class LoudnessTarget(val index: Int) : AudioEvent
    data class OpenEq(val trackId: String) : AudioEvent
    /** `mixer-view-segment`: the tracks' strips (0) or the buses' (1). */
    data class MixerView(val index: Int) : AudioEvent
    /** `channel-output-menu`: the track goes to bus [busId], or to the main output for null. */
    data class TrackOutput(val trackId: String, val busId: String?) : AudioEvent
    /** A new submix bus (`add-bus-button`); with [trackId] that track goes to it (`channel-output-menu`'s 新建总线). */
    data class AddBus(val trackId: String? = null) : AudioEvent
    data class RemoveBus(val busId: String) : AudioEvent
    data class BusMute(val busId: String, val mute: Boolean) : AudioEvent
    data class BusSolo(val busId: String, val solo: Boolean) : AudioEvent
    data class BusVolume(val busId: String, val db: Float) : AudioEvent
    /** The bus's equaliser in the EQ sheet. */
    data class OpenBusEq(val busId: String) : AudioEvent
    /** `audio-clip-eq-row`'s 编辑: the selected clip's own equaliser in the same sheet (Resolve's clip EQ, as on the Edit page). */
    data object OpenClipEq : AudioEvent
    data object CloseEq : AudioEvent
    data class Eq(val control: EqControl, val value: Float) : AudioEvent
    /** A handle on the EQ curve dragged to [hz], [db]. */
    data class EqDrag(val band: EqBand, val hz: Float, val db: Float) : AudioEvent
    data class EqLowCut(val on: Boolean) : AudioEvent
    data class EqEnabled(val on: Boolean) : AudioEvent
    /** One of Fairlight's EQ presets picked from `eq-preset-menu`: the curve becomes it, switched on. */
    data class EqPresetPicked(val preset: EqPreset) : AudioEvent
    /** One of Fairlight's dynamics presets picked from `dynamics-preset-menu`: the settings become it, switched on. */
    data class DynamicsPresetPicked(val preset: DynamicsPreset) : AudioEvent
    data object ResetEq : AudioEvent
    data class OpenDynamics(val trackId: String) : AudioEvent

    /** `noise-reduction-sheet`: open [trackId]'s, close it, switch it, set a control, reset. */
    data class OpenNoiseReduction(val trackId: String) : AudioEvent
    data object CloseNoiseReduction : AudioEvent
    data class NoiseReductionEnabled(val on: Boolean) : AudioEvent
    data class NoiseReduction(val control: NoiseReductionControl, val value: Float) : AudioEvent
    data object ResetNoiseReduction : AudioEvent
    /** `deesser-sheet`: open [trackId]'s, close it, switch it, set the amount or the band, reset. */
    data class OpenDeEsser(val trackId: String) : AudioEvent
    data object CloseDeEsser : AudioEvent
    data class DeEsserEnabled(val on: Boolean) : AudioEvent
    data class DeEsserAmount(val value: Float) : AudioEvent
    data class DeEsserBandPicked(val band: com.example.prismstudio.p40b43a88.domain.model.DeEssBand) : AudioEvent
    data object ResetDeEsser : AudioEvent
    /** `reverb-sheet`: open [trackId]'s, close it, switch it, pick a room, set a control, reset. */
    data class OpenReverb(val trackId: String) : AudioEvent
    data object CloseReverb : AudioEvent
    data class ReverbEnabled(val on: Boolean) : AudioEvent
    data class ReverbRoomPicked(val room: ReverbRoom) : AudioEvent
    data class ReverbDecay(val seconds: Float) : AudioEvent
    data class ReverbPreDelay(val ms: Float) : AudioEvent
    data class ReverbDamping(val percent: Float) : AudioEvent
    data class ReverbMix(val percent: Float) : AudioEvent
    data object ResetReverb : AudioEvent
    data object CloseDynamics : AudioEvent
    data class Dynamics(val control: DynamicsControl, val value: Float) : AudioEvent
    data class DynamicsCompressor(val on: Boolean) : AudioEvent
    data class DynamicsLimiter(val on: Boolean) : AudioEvent

    /** `dyn-gate-switch`: Fairlight's Expander/Gate on or off (its settings kept). */
    data class DynamicsGate(val on: Boolean) : AudioEvent
    data class DynamicsEnabled(val on: Boolean) : AudioEvent
    data object ResetDynamics : AudioEvent
    data class OpenDucking(val trackId: String) : AudioEvent
    data object CloseDucking : AudioEvent
    data class Ducking(val control: DuckingControl, val value: Float) : AudioEvent
    data class DuckingEnabled(val on: Boolean) : AudioEvent
    /** `ducking-preset-row`: a starting point for the depth, threshold, attack and release (the key kept). */
    data class DuckingPresetPicked(val preset: DuckingPreset) : AudioEvent
    data class DuckingKey(val trackId: String) : AudioEvent
    data object ResetDucking : AudioEvent
    /** Resolve's Normalize Audio Levels for the selected sound clip, to the chosen target. */
    data object NormalizeLoudness : AudioEvent
    data object ToastShown : AudioEvent
    data object OpenVoiceover : AudioEvent
    data object CloseVoiceover : AudioEvent
    /** The audio page or its window closed. Keep a take already being recorded. */
    data object LeaveVoiceover : AudioEvent
    data class VoiceoverTarget(val trackId: String?) : AudioEvent
    data class VoiceoverProgramSound(val on: Boolean) : AudioEvent
    data object StartVoiceover : AudioEvent
    data object StopVoiceover : AudioEvent
    /** The microphone permission was refused. */
    data object VoiceoverDenied : AudioEvent
    data object VoiceoverPermissionRestored : AudioEvent
    data object VoiceoverSettingsUnavailable : AudioEvent
}
