package com.example.prismstudio.p40b43a88.ui.audio

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.domain.model.eqSummary
import android.os.SystemClock
import com.example.prismstudio.p40b43a88.data.repository.VoiceRecorderRepository
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.usecase.TracksUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.VoiceoverUseCase
import java.util.Locale
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.LoudnessRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopy
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.usecase.ClipPropertiesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MeterLevelsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProgramLoudness
import com.example.prismstudio.p40b43a88.domain.usecase.ProgramLoudnessUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.BusesUseCase
import com.example.prismstudio.p40b43a88.domain.model.MixerBus
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.ui.components.TimelineScale
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi
import com.example.prismstudio.p40b43a88.ui.components.TrackDuck
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.components.route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.NoiseGate
import com.example.prismstudio.p40b43a88.data.repository.NoiseProfileRepository

/** Audio stage: audio lanes with fade handles, the mixer and live peak meters. */
class AudioViewModel(
    private val session: EditorSessionRepository,
    private val program: ProgramPlaybackRepository,
    private val previews: MediaPreviewRepository,
    private val reverseCopies: ReverseCopyRepository = ReverseCopyRepository.None,
    private val loudness: LoudnessRepository = LoudnessRepository.None,
    private val meters: MeterLevelsUseCase = MeterLevelsUseCase(),
    private val props: ClipPropertiesUseCase = ClipPropertiesUseCase(),
    private val levels: LoudnessUseCase = LoudnessUseCase(),
    private val ducking: DuckingUseCase = DuckingUseCase(),
    private val programLevels: ProgramLoudnessUseCase = ProgramLoudnessUseCase(),
    /** The microphone for narrations (`voiceover-sheet`), and the ids their media and clips get. */
    private val voice: VoiceRecorderRepository = VoiceRecorderRepository.None,
    /** Each file's noise floor, which the noise reduction sheet shows (`noise-reduction-sheet`). */
    private val noiseProfiles: NoiseProfileRepository = NoiseProfileRepository.None,
    ids: IdGenerator = IdGenerator { prefix -> "$prefix-${java.util.UUID.randomUUID().toString().take(12)}" },
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val voiceovers = VoiceoverUseCase(ids)
    private val busEdits = BusesUseCase()
    /** Where the take being recorded started (timeline frame) and when (clock ms); its level meter's ticker. */
    private data class TakePosition(val timelineId: String, val frame: Int, val fps: Int)
    private var takePosition: TakePosition? = null
    private val finishBeforeSave: () -> Unit = { stopVoiceover() }
    private var takeStartedAt = 0L
    private var takeTicker: Job? = null

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    private var preview: Timeline? = null
    private var zoom: Float? = null
    /** The track whose EQ sheet is open. */
    private var eqTrackId: String? = null
    /** The clip whose own EQ the sheet edits instead of a track's ([AudioEvent.OpenClipEq]); null for a track's. */
    private var eqClipId: String? = null
    /** The submix bus whose EQ the sheet edits ([AudioEvent.OpenBusEq]). */
    private var eqBusId: String? = null
    private var dynamicsTrackId: String? = null
    private var duckingTrackId: String? = null
    /** The track whose noise reduction sheet is open. */
    private var noiseTrackId: String? = null
    private var deEsserTrackId: String? = null
    private var reverbTrackId: String? = null
    /** The ducking analyses by ducked track, and what they were worked out from (see [DuckingUseCase.inputs]). */
    private var analyses: Map<String, DuckingUseCase.Analysis> = emptyMap()
    private var duckInputs: List<Any?>? = null
    private val waves = mutableMapOf<String, Waveform>()
    private val requested = mutableSetOf<String>()
    /** Loudness profiles by asset id (null: no decodable sound), and the assets being measured. */
    private val profiles = mutableMapOf<String, LoudnessProfile?>()
    private val measuring = mutableSetOf<String>()
    /** The program loudness and what it was measured from (the document, the media measured, the ducking). */
    private var programMeasure: ProgramLoudness? = null
    private var programNote: String? = null
    private var programFrom: Triple<ProjectDocument, Int, Map<String, DuckingUseCase.Analysis>>? = null

    init {
        viewModelScope.launch { session.document.collect { publish() } }
        viewModelScope.launch { session.selectedClipId.collect { publish() } }
        viewModelScope.launch { reverseCopies.copies.collect { publish() } }
        // A noise floor measured (or being measured) shows on the open noise reduction sheet.
        viewModelScope.launch { noiseProfiles.profiles.collect { if (noiseTrackId != null) updateMeters() } }
        viewModelScope.launch { noiseProfiles.measuring.collect { if (noiseTrackId != null) updateMeters() } }
        viewModelScope.launch {
            program.state.collect { p ->
                val before = _state.value.playheadFrame
                _state.update {
                    it.copy(playheadFrame = Timecode.positionToFrame(p.positionMs, it.fps), playing = p.playing, previewApproximate = p.approximate, programLayers = p.layers, programComposites = p.compositesAt())
                }
                // A keyed clip's gain follows the playhead.
                if (_state.value.playheadFrame != before) refreshSelection()
                updateMeters()
            }
        }
    }

    private val doc: ProjectDocument? get() = session.document.value

    fun onEvent(event: AudioEvent) {
        when (event) {
            is AudioEvent.SelectClip -> session.select(event.clipId)
            is AudioEvent.Seek -> doc?.let { d ->
                val frame = event.frame.coerceIn(0, d.durationFrames.coerceAtLeast(0))
                program.seekTo(Timecode.frameToSeekMs(frame, d.format.fps))
                _state.update { it.copy(playheadFrame = frame) }
                updateMeters()
            }
            is AudioEvent.Fade -> fade(event)
            is AudioEvent.FadeCurve -> fadeCurve(event)
            is AudioEvent.VolumeLine -> volumeLine(event)
            is AudioEvent.TrackLocked -> session.edit(if (event.locked) "锁定轨道" else "解锁轨道") { d ->
                d.copy(timeline = d.timeline.copy(tracks = d.timeline.tracks.map { if (it.id == event.trackId) it.copy(locked = event.locked) else it }))
            }
            is AudioEvent.Mute -> mix(if (event.mute) "静音" else "取消静音", null, event.trackId) { it.copy(mute = event.mute) }
            is AudioEvent.Solo -> mix(if (event.solo) "独奏" else "取消独奏", null, event.trackId) { it.copy(solo = event.solo) }
            is AudioEvent.Pan -> mix("声像", "pan", event.trackId) { it.copy(pan = event.pan.coerceIn(-1f, 1f)) }
            is AudioEvent.Volume -> mix("推子", "volume", event.trackId) { it.copy(volumeDb = event.db.coerceIn(-60f, 12f)) }
            is AudioEvent.MasterVolume -> session.edit("主输出推子", "mixer:master") { d ->
                d.copy(mixer = d.mixer.copy(masterVolumeDb = event.db.coerceIn(-60f, 12f)))
            }
            is AudioEvent.ClipGain -> {
                val id = _state.value.selection?.clipId ?: return
                try {
                    session.edit("片段增益", "gain:$id") { d ->
                        val start = d.timeline.clip(id)?.start ?: 0
                        d.copy(timeline = props.volume(d.timeline, id, event.db, _state.value.playheadFrame - start))
                    }
                } catch (e: EditRejected) {
                    toast(e.message ?: "无法修改")
                }
            }
            is AudioEvent.Viewport -> if (zoom == null) {
                val d = doc ?: return
                zoom = TimelineScale.fitZoom(d.durationFrames, d.format.fps, event.widthDp)
                publish()
            }
            is AudioEvent.ProgramSurface -> program.route(event.change)
            is AudioEvent.LoudnessTarget -> {
                _state.update { it.copy(loudnessTarget = event.index.coerceIn(0, LoudnessUseCase.TARGETS_LUFS.lastIndex)) }
                updateMeters()
            }
            AudioEvent.NormalizeLoudness -> normalize()
            AudioEvent.ToastShown -> _state.update { it.copy(toast = null) }
            AudioEvent.OpenVoiceover -> openVoiceover()
            AudioEvent.CloseVoiceover -> closeVoiceover()
            AudioEvent.LeaveVoiceover -> {
                stopVoiceover()
                _state.update { it.copy(voiceover = null) }
            }
            is AudioEvent.VoiceoverTarget -> editIdleVoiceover { it.copy(targetId = event.trackId, note = voiceNote(event.trackId)) }
            is AudioEvent.VoiceoverProgramSound -> editIdleVoiceover { it.copy(programSound = event.on) }
            AudioEvent.StartVoiceover -> startVoiceover()
            AudioEvent.StopVoiceover -> stopVoiceover()
            AudioEvent.VoiceoverDenied -> editIdleVoiceover { it.copy(microphoneDenied = true, recordingError = null) }
            AudioEvent.VoiceoverPermissionRestored -> editIdleVoiceover { it.copy(microphoneDenied = false) }
            AudioEvent.VoiceoverSettingsUnavailable -> toast("请在系统设置的应用权限里允许 ${PRODUCT_NAME} 访问")
            is AudioEvent.OpenEq -> {
                // One sheet at a time.
                reverbTrackId = null
                dynamicsTrackId = null
                deEsserTrackId = null
                duckingTrackId = null
                eqClipId = null
                eqBusId = null
                eqTrackId = event.trackId
                updateMeters()
            }
            is AudioEvent.OpenBusEq -> {
                reverbTrackId = null
                dynamicsTrackId = null
                deEsserTrackId = null
                duckingTrackId = null
                noiseTrackId = null
                eqClipId = null
                eqTrackId = null
                eqBusId = event.busId
                updateMeters()
            }
            is AudioEvent.MixerView -> {
                _state.update { it.copy(mixerView = event.index.coerceIn(0, 1)) }
            }
            is AudioEvent.TrackOutput -> buses("轨道输出") { d ->
                val name = d.timeline.track(event.trackId)?.name ?: event.trackId
                busEdits.assign(d.mixer, event.trackId, name, event.busId)
            }
            is AudioEvent.AddBus -> buses("添加总线") { d ->
                busEdits.add(d.mixer, event.trackId, event.trackId?.let { d.timeline.track(it)?.name })
            }
            is AudioEvent.RemoveBus -> buses("删除总线") { d -> busEdits.remove(d.mixer, event.busId, trackNames(d)) }
            is AudioEvent.BusMute -> bus(if (event.mute) "总线静音" else "取消总线静音", null, event.busId) { it.copy(mute = event.mute) }
            is AudioEvent.BusSolo -> bus(if (event.solo) "总线独奏" else "取消总线独奏", null, event.busId) { it.copy(solo = event.solo) }
            is AudioEvent.BusVolume -> bus("总线推子", "volume", event.busId) { it.copy(volumeDb = event.db) }
            AudioEvent.OpenClipEq -> {
                reverbTrackId = null
                val id = _state.value.selection?.clipId ?: return toast("先在时间线上选择一个声音片段")
                dynamicsTrackId = null
                duckingTrackId = null
                noiseTrackId = null
                deEsserTrackId = null
                eqTrackId = null
                eqBusId = null
                eqClipId = id
                updateMeters()
            }
            AudioEvent.CloseEq -> {
                eqTrackId = null
                eqBusId = null
                eqClipId = null
                _state.update { it.copy(eqEditor = null) }
            }
            is AudioEvent.Eq -> eq("均衡器：${event.control.band}${event.control.label}", "eq-${event.control}") { event.control.write(it, event.value) }
            is AudioEvent.EqDrag -> eq("均衡器：${event.band.label}", "eq-drag-${event.band}") { event.band.drag(it, event.hz, event.db) }
            is AudioEvent.EqLowCut -> eq(if (event.on) "均衡器：开启低切" else "均衡器：关闭低切", null) { it.copy(lowCut = event.on) }
            is AudioEvent.EqEnabled -> eq(if (event.on) "均衡器：启用" else "均衡器：旁通", null) { it.copy(enabled = event.on) }
            is AudioEvent.EqPresetPicked -> eq("均衡器预设：${event.preset.label}", null) { event.preset.eq }
            AudioEvent.ResetEq -> eq("重置均衡器", null) { TrackEq.FLAT }
            is AudioEvent.OpenDynamics -> {
                reverbTrackId = null
                eqTrackId = null
                eqBusId = null
                duckingTrackId = null
                noiseTrackId = null
                deEsserTrackId = null
                dynamicsTrackId = event.trackId
                updateMeters()
            }
            is AudioEvent.OpenNoiseReduction -> {
                reverbTrackId = null
                eqTrackId = null
                eqBusId = null
                dynamicsTrackId = null
                duckingTrackId = null
                deEsserTrackId = null
                noiseTrackId = event.trackId
                // The sheet shows the floor of the track's files: measure them now.
                doc?.let { d -> noiseProfiles.request(trackAssets(d, event.trackId)) }
                updateMeters()
            }
            is AudioEvent.OpenDeEsser -> {
                reverbTrackId = null
                eqTrackId = null
                eqBusId = null
                eqClipId = null
                dynamicsTrackId = null
                duckingTrackId = null
                noiseTrackId = null
                deEsserTrackId = event.trackId
                updateMeters()
            }
            AudioEvent.CloseDeEsser -> {
                deEsserTrackId = null
                _state.update { it.copy(deEsserEditor = null) }
            }
            is AudioEvent.DeEsserEnabled -> deEss(if (event.on) "齿音消除：开启" else "齿音消除：关闭", null) { it.copy(enabled = event.on) }
            is AudioEvent.DeEsserAmount -> deEss("齿音消除：强度", "deess-amount") { it.copy(amount = event.value.roundToInt().coerceIn(0, 100)) }
            is AudioEvent.DeEsserBandPicked -> deEss("齿音消除：频段 ${event.band.label}", null) { it.copy(band = event.band) }
            AudioEvent.ResetDeEsser -> deEss("重置齿音消除", null) { com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser.OFF }
            is AudioEvent.OpenReverb -> {
                eqTrackId = null
                eqBusId = null
                eqClipId = null
                dynamicsTrackId = null
                duckingTrackId = null
                noiseTrackId = null
                deEsserTrackId = null
                reverbTrackId = event.trackId
                updateMeters()
            }
            AudioEvent.CloseReverb -> {
                reverbTrackId = null
                _state.update { it.copy(reverbEditor = null) }
            }
            is AudioEvent.ReverbEnabled -> reverb(if (event.on) "混响：开启" else "混响：关闭", null) { it.copy(enabled = event.on) }
            // A room switches the reverb on with its settings.
            is AudioEvent.ReverbRoomPicked -> reverb("混响：${event.room.label}", null) { it.withRoom(event.room) }
            is AudioEvent.ReverbDecay -> reverb("混响：混响时间", "rv-decay") { it.copy(decaySeconds = (event.seconds * 10).roundToInt() / 10f) }
            is AudioEvent.ReverbPreDelay -> reverb("混响：预延迟", "rv-pre") { it.copy(preDelayMs = event.ms.roundToInt()) }
            is AudioEvent.ReverbDamping -> reverb("混响：高频衰减", "rv-damp") { it.copy(damping = event.percent.roundToInt()) }
            is AudioEvent.ReverbMix -> reverb("混响：干湿比", "rv-mix") { it.copy(mix = event.percent.roundToInt()) }
            AudioEvent.ResetReverb -> reverb("重置混响", null) { TrackReverb.OFF }
            AudioEvent.CloseNoiseReduction -> {
                noiseTrackId = null
                _state.update { it.copy(noiseEditor = null) }
            }
            is AudioEvent.NoiseReduction -> noise("降噪：${event.control.label}", "nr-${event.control}") { event.control.write(it, event.value) }
            is AudioEvent.NoiseReductionEnabled -> noise(if (event.on) "降噪：开启" else "降噪：关闭", null) { it.copy(enabled = event.on) }
            AudioEvent.ResetNoiseReduction -> noise("重置降噪", null) { TrackNoiseReduction.OFF }
            AudioEvent.CloseDynamics -> {
                dynamicsTrackId = null
                _state.update { it.copy(dynamicsEditor = null) }
            }
            is AudioEvent.Dynamics -> dynamics("动态：${event.control.section}${event.control.label}", "dyn-${event.control}") {
                event.control.write(it, event.value)
            }
            is AudioEvent.DynamicsCompressor -> dynamics(if (event.on) "动态：开启压缩器" else "动态：关闭压缩器", null) { it.copy(compressor = event.on) }
            is AudioEvent.DynamicsLimiter -> dynamics(if (event.on) "动态：开启限制器" else "动态：关闭限制器", null) { it.copy(limiter = event.on) }
            is AudioEvent.DynamicsGate -> dynamics(if (event.on) "动态：开启门限" else "动态：关闭门限", null) { it.copy(gate = event.on) }
            is AudioEvent.DynamicsEnabled -> dynamics(if (event.on) "动态：启用" else "动态：旁通", null) { it.copy(enabled = event.on) }
            AudioEvent.ResetDynamics -> dynamics("重置动态", null) { TrackDynamics.OFF }
            is AudioEvent.DynamicsPresetPicked -> dynamics("动态预设：${event.preset.label}", null) { event.preset.dynamics }
            is AudioEvent.OpenDucking -> {
                reverbTrackId = null
                eqTrackId = null
                eqBusId = null
                dynamicsTrackId = null
                deEsserTrackId = null
                duckingTrackId = event.trackId
                updateMeters()
            }
            AudioEvent.CloseDucking -> {
                duckingTrackId = null
                _state.update { it.copy(duckingEditor = null) }
            }
            is AudioEvent.Ducking -> duck("闪避：${event.control.label}", "duck-${event.control}") { event.control.write(it, event.value) }
            is AudioEvent.DuckingEnabled -> duckingSwitch(event.on)
            is AudioEvent.DuckingPresetPicked -> duckingSwitch(true, "闪避预设：${event.preset.label}") { event.preset.apply(it) }
            is AudioEvent.DuckingKey -> {
                val trackId = duckingTrackId ?: return
                val key = doc?.timeline?.track(event.trackId)?.takeIf { it.kind == TrackKind.AUDIO && it.id != trackId } ?: return
                duck("闪避：侧链 ${key.name}", null) { it.copy(enabled = true, keyTrackId = key.id) }
            }
            AudioEvent.ResetDucking -> duck("重置闪避", null) { TrackDucking.OFF }
        }
    }

    /** Changes the open ducking sheet's track ducking (one undo step per drag of a control). */
    private fun duck(label: String, key: String?, change: (TrackDucking) -> TrackDucking) {
        val trackId = duckingTrackId ?: return
        mix(label, key, trackId) { it.copy(ducking = change(it.ducking)) }
    }

    /**
     * On: keyed from the chosen track when it is still there, else from the first other sound track, with [also] applied
     * (a preset switches it on too). Off: bypassed.
     */
    private fun duckingSwitch(on: Boolean, label: String = "闪避：开启", also: (TrackDucking) -> TrackDucking = { it }) {
        val trackId = duckingTrackId ?: return
        val d = doc ?: return
        if (!on) return duck("闪避：关闭", null) { it.copy(enabled = false) }
        val others = d.timeline.tracks.filter { it.kind == TrackKind.AUDIO && it.id != trackId }
        if (others.isEmpty()) return toast(NO_KEY_TRACK)
        duck(label, null) { s ->
            also(s).copy(enabled = true, keyTrackId = s.keyTrackId?.takeIf { k -> others.any { it.id == k } } ?: others.first().id)
        }
    }

    /** Changes the open noise reduction sheet's track settings (one undo step per drag of a control). */
    private fun noise(label: String, key: String?, change: (TrackNoiseReduction) -> TrackNoiseReduction) {
        val trackId = noiseTrackId ?: return
        mix(label, key, trackId) { it.copy(noiseReduction = change(it.noiseReduction)) }
    }

    /** Changes the open de-esser sheet's track settings (one undo step per drag of the amount). */
    private fun deEss(label: String, key: String?, change: (com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser) -> com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser) {
        val trackId = deEsserTrackId ?: return
        mix(label, key, trackId) { it.copy(deEsser = change(it.deEsser)) }
    }

    /** Changes the open reverb sheet's track settings, held in range (one undo step per drag of a control). */
    private fun reverb(label: String, key: String?, change: (TrackReverb) -> TrackReverb) {
        val trackId = reverbTrackId ?: return
        mix(label, key, trackId) { it.copy(reverb = change(it.reverb).clamped()) }
    }

    /** The sound files of [trackId]'s clips. */
    private fun trackAssets(d: ProjectDocument, trackId: String): List<MediaAsset> {
        val ids = d.timeline.clipsOn(trackId).mapNotNull { it.assetId }.toSet()
        return d.media.filter { it.id in ids }
    }

    /**
     * The noise reduction sheet of [track]: its settings and the floor of the file under the playhead on it (else its
     * first clip's), with which file that is, or what keeps it from showing.
     */
    private fun noiseEditor(d: ProjectDocument, track: Track): NoiseReductionEditorUi {
        val settings = d.mixer.channel(track.id).noiseReduction
        val clips = d.timeline.clipsOn(track.id).filter { it.assetId != null }
        val frame = _state.value.playheadFrame
        val under = clips.firstOrNull { frame in it }
        val clip = under ?: clips.firstOrNull()
        val asset = clip?.assetId?.let(d::asset)
        val profile = asset?.let(noiseProfiles::profile)
        val where = if (under != null) "播放头所在的片段" else "轨道上的第一个片段"
        val status = when {
            asset == null -> "这条轨道上还没有声音片段"
            profile != null -> "噪声底来自“${asset.name}”（$where）：每个文件各自测量，压低的是接近它的声音"
            asset.uri in noiseProfiles.measuring.value -> "正在测量“${asset.name}”的噪声底…"
            else -> "还没有“${asset.name}”的噪声底：打开降噪后会自动测量"
        }
        return NoiseReductionEditorUi(
            track.id,
            track.name,
            settings,
            floorDb = profile?.levelsDb()?.map { it - FLOOR_NORMALIZATION_DB } ?: emptyList(),
            floorStepHz = profile?.let { it.sampleRate.toFloat() / NoiseGate.FFT_SIZE } ?: 0f,
            status = status,
        )
    }

    /** Changes the open dynamics sheet's track dynamics (one undo step per drag of a control). */
    private fun dynamics(label: String, key: String?, change: (TrackDynamics) -> TrackDynamics) {
        val trackId = dynamicsTrackId ?: return
        mix(label, key, trackId) { it.copy(dynamics = change(it.dynamics)) }
    }

    /** Changes the open EQ sheet's EQ — a clip's own, or a track's (one undo step per drag of a control). */
    private fun eq(label: String, key: String?, change: (TrackEq) -> TrackEq) {
        eqClipId?.let { id ->
            // The Edit page's names for the clip EQ's steps.
            try {
                session.edit("片段" + label, key?.let { "clip-eq:$it:$id" }) { d -> d.copy(timeline = props.eq(d.timeline, id, change)) }
            } catch (e: EditRejected) {
                toast(e.message ?: "无法修改均衡器")
            }
            return
        }
        eqBusId?.let { id ->
            bus(label, key?.let { "eq-$it" }, id) { it.copy(eq = change(it.eq)) }
            return
        }
        val trackId = eqTrackId ?: return
        mix(label, key, trackId) { it.copy(eq = change(it.eq)) }
    }

    /** Changes bus [busId] (one undo step per drag of a control, by [key]). */
    private fun bus(label: String, key: String?, busId: String, change: (MixerBus) -> MixerBus) {
        try {
            session.edit(label, key?.let { "bus:$it:$busId" }) { d -> d.copy(mixer = busEdits.update(d.mixer, busId, change)) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改总线")
        }
    }

    /** A routing edit (add, remove, assign), told in a toast; refused ones say why. */
    private fun buses(label: String, edit: (ProjectDocument) -> BusesUseCase.Result) {
        val d = doc ?: return
        try {
            val result = edit(d)
            if (result.mixer != d.mixer) session.edit(label) { it.copy(mixer = result.mixer) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改总线")
        }
    }

    private fun trackNames(d: ProjectDocument): Map<String, String> = d.timeline.tracks.associate { it.id to it.name }

    private fun mix(label: String, key: String?, trackId: String, change: (MixerChannel) -> MixerChannel) {
        session.edit(label, key?.let { "mixer:$it:$trackId" }) { d ->
            val channel = d.mixer.channel(trackId)
            val mixer = if (d.mixer.channels.any { it.trackId == trackId }) d.mixer.replace(change(channel))
            else d.mixer.copy(channels = d.mixer.channels + change(channel))
            d.copy(mixer = mixer)
        }
    }

    private fun fade(event: AudioEvent.Fade) {
        val d = doc ?: return
        val fps = d.format.fps
        try {
            val updated = props.fades(
                d.timeline, event.clipId, fps,
                fadeIn = if (event.fadeIn) event.seconds else null,
                fadeOut = if (event.fadeIn) null else event.seconds,
            )
            if (event.commit) {
                preview = null
                session.edit(if (event.fadeIn) "淡入" else "淡出") { it.copy(timeline = updated) }
                session.select(event.clipId)
            } else {
                preview = updated
                publish()
            }
        } catch (e: EditRejected) {
            preview = null
            if (event.commit) toast(e.message ?: "无法修改淡化")
            publish()
        }
    }

    /** A sound's fade bent by its curve dot, as on the Edit page: heard live, one undo step when let go. */
    private fun fadeCurve(event: AudioEvent.FadeCurve) {
        val d = doc ?: return
        try {
            val updated = props.fadeCurves(
                d.timeline, event.clipId,
                fadeIn = if (event.fadeIn) event.curve else null,
                fadeOut = if (event.fadeIn) null else event.curve,
            )
            if (event.commit) {
                preview = null
                if (updated != d.timeline) session.edit(if (event.fadeIn) "淡入曲线" else "淡出曲线") { it.copy(timeline = updated) }
                else program.setProject(d)
                session.select(event.clipId)
            } else {
                preview = updated
                program.setProject(d.copy(timeline = updated))
                publish()
            }
        } catch (e: EditRejected) {
            preview = null
            program.setProject(d)
            if (event.commit) toast(e.message ?: "无法调整淡化曲线")
            publish()
        }
    }

    /** Dragging a sound's volume line, as on the Edit page: shown and heard live, one undo step when let go. */
    private fun volumeLine(event: AudioEvent.VolumeLine) {
        val d = doc ?: return
        try {
            val updated = props.volumeLine(d.timeline, event.clipId, event.keyFrames, event.deltaDb)
            if (event.commit) {
                preview = null
                if (updated != d.timeline) session.edit(if (event.keyFrames.isEmpty()) "调整音量" else "调整音量关键帧") { it.copy(timeline = updated) }
                else program.setProject(d)
                session.select(event.clipId)
            } else {
                preview = updated
                program.setProject(d.copy(timeline = updated))
                publish()
            }
        } catch (e: EditRejected) {
            preview = null
            program.setProject(d)
            if (event.commit) toast(e.message ?: "无法调整音量")
            publish()
        }
    }

    private fun publish() {
        val d = doc ?: return
        refreshDucking(d)
        val timeline = preview ?: d.timeline
        val fps = d.format.fps
        val audioTracks = timeline.tracks.filter { it.kind == TrackKind.AUDIO }
        val selected = selectedSound(timeline)
        timeline.clips.filter { it.kind == ClipKind.AUDIO }.forEach { clip ->
            val asset = d.asset(clip.assetId) ?: return@forEach
            if (requested.add(asset.id + "@" + asset.uri)) {
                viewModelScope.launch {
                    previews.waveform(asset.uri)?.let {
                        waves[asset.id] = it
                        publish()
                        updateMeters()
                    }
                }
            }
        }
        _state.update { s ->
            s.copy(
                timeline = TimelineUi(
                    tracks = audioTracks,
                    clips = timeline.clips.filter { c -> audioTracks.any { it.id == c.trackId } },
                    markers = timeline.markers,
                    fps = fps,
                    durationFrames = maxOf(timeline.durationFrames, d.durationFrames),
                    selectedClipId = selected?.id,
                    zoom = zoom ?: 0.35f,
                    waveforms = waves.toMap(),
                    offlineAssetIds = d.media.filter { it.offline }.map { it.id }.toSet(),
                    flags = d.media.filter { it.flags.isNotEmpty() }.associate { it.id to it.flags },
                    reverseMakingAssetIds = reverseCopies.copies.value.filterValues { it is ReverseCopy.Making }.keys,
                    reverseFailedAssetIds = reverseCopies.copies.value.filterValues { it is ReverseCopy.Failed }.keys,
                    ducks = analyses.mapNotNull { (trackId, analysis) ->
                        d.mixer.channel(trackId).ducking.takeIf { it.isActive }?.let { trackId to TrackDuck(it.depthDb, analysis.envelope) }
                    }.toMap(),
                ),
                fps = fps,
                selection = selected?.let { selectionFor(it, timeline) },
                loudness = loudnessFor(selected, d, timeline),
                masterDb = d.mixer.masterVolumeDb,
                pictureAspect = d.format.width.toFloat() / d.format.height.coerceAtLeast(1),
                programEmpty = d.timeline.clips.isEmpty(),
            )
        }
        updateMeters()
    }

    /**
     * Works the ducking out again when what it depends on changed; the key tracks' media are measured
     * first (in the background, once per file) and count as silent until then.
     */
    private fun refreshDucking(d: ProjectDocument) {
        val keyAssets = ducking.keyAssetIds(d)
        d.media.filter { it.id in keyAssets && !it.offline }.forEach(::requestProfile)
        val inputs = ducking.inputs(d, keyAssets.filter { it in profiles }.toSet())
        if (inputs == duckInputs) return
        duckInputs = inputs
        analyses = ducking.analyses(d, profiles.mapNotNull { (id, profile) -> profile?.let { id to it } }.toMap())
    }

    /** Measures [asset]'s loudness in the background (once per file); the page updates when it is known. */
    private fun requestProfile(asset: MediaAsset) {
        if (asset.id in profiles || !measuring.add(asset.id)) return
        viewModelScope.launch {
            profiles[asset.id] = loudness.profile(asset.uri)
            measuring.remove(asset.id)
            publish()
        }
    }

    /**
     * Measures the program again when the document, the media measured so far or the ducking changed
     * (asking for every sounding file's loudness profile the first time).
     */
    private fun refreshProgram(d: ProjectDocument) {
        val sounds = d.timeline.clips.filter { it.kind == ClipKind.AUDIO && it.enabled }
            .mapNotNull { d.asset(it.assetId) }
            .filter { !it.offline }
            .distinctBy { it.id }
        sounds.forEach(::requestProfile)
        val measured = sounds.count { it.id in profiles }
        val from = programFrom
        if (from != null && from.first === d && from.second == measured && from.third === analyses) return
        programFrom = Triple(d, measured, analyses)
        val ready = profiles.mapNotNull { (id, profile) -> profile?.let { id to it } }.toMap()
        programMeasure = if (sounds.isEmpty()) null else programLevels(d, ready, analyses.mapValues { it.value.envelope })
        val equalised = d.timeline.tracks.any { it.kind == TrackKind.AUDIO && d.mixer.isAudible(it.id) && d.mixer.channel(it.id).eq.isActive }
        programNote = when {
            sounds.isEmpty() -> "时间线上没有声音"
            measured < sounds.size -> "正在测量节目响度…"
            equalised -> "EQ 不计入响度读数"
            else -> null
        }
    }

    /** The program loudness meter at the playhead. */
    private fun programLoudnessUi(): ProgramLoudnessUi {
        val target = LoudnessUseCase.TARGETS_LUFS[_state.value.loudnessTarget]
        val m = programMeasure ?: return ProgramLoudnessUi(targetLufs = target, note = programNote)
        val ms = _state.value.playheadFrame * 1000.0 / _state.value.fps.coerceAtLeast(1)
        val shortTerm = m.shortTermAt(ms)
        fun lufs(v: Double?) = v?.let { formatValue(it.toFloat(), 1, false) } ?: ProgramLoudnessUi.NO_READING
        return ProgramLoudnessUi(
            momentary = lufs(m.momentaryAt(ms)),
            shortTerm = lufs(shortTerm),
            integrated = lufs(m.integratedLufs),
            range = m.rangeLu?.let { formatValue(it.toFloat(), 1, false, " LU") } ?: ProgramLoudnessUi.NO_READING,
            peak = m.truePeakDbtp?.let { formatValue(it.toFloat(), 1, false, " dBTP") } ?: ProgramLoudnessUi.NO_READING,
            history = m.history(ms, ProgramLoudnessUi.HISTORY_SECONDS).toList(),
            momentaryHistory = m.history(ms, ProgramLoudnessUi.HISTORY_SECONDS, momentary = true).toList(),
            targetLufs = target,
            hot = shortTerm != null && shortTerm > target + HOT_LU,
            clipping = (m.truePeakDbtp ?: Double.NEGATIVE_INFINITY) > LoudnessUseCase.TRUE_PEAK_LIMIT_DBTP,
            note = programNote,
        )
    }

    private fun updateMeters() {
        val d = doc ?: return
        refreshDucking(d)
        refreshProgram(d)
        val levels = meters(d, _state.value.playheadFrame, waves, analyses.mapValues { it.value.envelope })
        val audioTracks = d.timeline.tracks.filter { it.kind == TrackKind.AUDIO }
        _state.update { s ->
            s.copy(
                channels = audioTracks.map { track ->
                    val ch = d.mixer.channel(track.id)
                    MixerChannelUi(
                        track.id, track.name, ch.mute, ch.solo, ch.pan, ch.volumeDb,
                        levels.channels[track.id] ?: MeterLevelsUseCase.FLOOR_DB, eqActive = ch.eq.isActive,
                        dynamicsActive = ch.dynamics.isActive,
                        duckingActive = ch.ducking.isActive && ducking.keyOf(d, track.id) != null,
                        noiseActive = ch.noiseReduction.isActive,
                        deEsserActive = ch.deEsser.isActive,
                        effects = channelEffects(
                            ch.ducking, ducking.keyOf(d, track.id)?.let { d.timeline.track(it)?.name }, ch.noiseReduction, ch.deEsser, ch.reverb,
                        ),
                        busId = d.mixer.busOf(track.id)?.id,
                        busShort = d.mixer.busOf(track.id)?.short,
                    )
                },
                buses = d.mixer.buses.map { b ->
                    MixerBusUi(
                        b.id, b.name, b.short, b.mute, b.solo, b.volumeDb,
                        levels.buses[b.id] ?: MeterLevelsUseCase.FLOOR_DB,
                        eqActive = b.eq.isActive,
                        members = busEdits.membersLabel(d.mixer, b.id, trackNames(d)),
                    )
                },
                canAddBus = d.mixer.buses.size < MixerState.MAX_BUSES,
                masterLevelDb = levels.master,
                programLoudness = programLoudnessUi(),
                // The sheet closes with its track (a deleted track).
                eqEditor = eqClipId?.let { id -> (preview ?: d.timeline).clip(id) }
                    ?.let { clip -> EqEditorUi(clip.id, "片段 · ${clip.name}", clip.eq, CLIP_EQ_APPLIES_TO) }
                    ?: eqBusId?.let { id -> d.mixer.bus(id) }?.let { b -> EqEditorUi(b.id, b.name, b.eq, BUS_EQ_APPLIES_TO) }
                    ?: eqTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }
                        ?.let { track -> EqEditorUi(track.id, track.name, d.mixer.channel(track.id).eq) },
                dynamicsEditor = dynamicsTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }?.let { track ->
                    DynamicsEditorUi(
                        track.id,
                        track.name,
                        d.mixer.channel(track.id).dynamics,
                        inputDb = levels.inputs[track.id] ?: MeterLevelsUseCase.FLOOR_DB,
                        reductionDb = levels.reductions[track.id] ?: 0f,
                    )
                },
                duckingEditor = duckingTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }?.let { track ->
                    duckingEditor(d, track, audioTracks, levels)
                },
                noiseEditor = noiseTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }?.let { track -> noiseEditor(d, track) },
                deEsserEditor = deEsserTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }
                    ?.let { track -> DeEsserEditorUi(track.id, track.name, d.mixer.channel(track.id).deEsser) },
                reverbEditor = reverbTrackId?.let { id -> audioTracks.firstOrNull { it.id == id } }
                    ?.let { track -> ReverbEditorUi(track.id, track.name, d.mixer.channel(track.id).reverb) },
            )
        }
    }

    /** The ducking sheet of [track]: its settings, its key choices and the key and dip around the playhead. */
    private fun duckingEditor(d: ProjectDocument, track: Track, audioTracks: List<Track>, levels: MeterLevelsUseCase.Levels): DuckingEditorUi {
        val settings = d.mixer.channel(track.id).ducking
        val keys = audioTracks.filter { it.id != track.id }.map { DuckKeyUi(it.id, it.name) }
        val keyId = settings.keyTrackId?.takeIf { k -> keys.any { it.trackId == k } }
        val keyName = keys.firstOrNull { it.trackId == keyId }?.name.orEmpty()
        val analysis = analyses[track.id]
        val analysing = analysis != null && ducking.keyAssetIds(d).any { it in measuring }
        val status = when {
            keys.isEmpty() -> NO_KEY_TRACK
            keyId == null -> "未开启：打开后，侧链轨道发声时这条轨道自动压低"
            !settings.enabled -> "已旁通"
            analysing -> "正在分析 $keyName 的响度…"
            else -> "$keyName 高于阈值时压低这条轨道，在推子之前"
        }
        val fps = d.format.fps.coerceAtLeast(1)
        val playheadMs = _state.value.playheadFrame * 1000L / fps
        val start = playheadMs - DuckingEditorUi.WINDOW_MS / 2
        val points = (DuckingEditorUi.WINDOW_MS / DuckingEditorUi.STEP_MS).toInt() + 1
        fun levelAt(ms: Long) = if (ms < 0) DuckingUseCase.FLOOR_DB
        else analysis?.levels?.getOrNull((ms / DuckingUseCase.BLOCK_MS).toInt()) ?: DuckingUseCase.FLOOR_DB
        return DuckingEditorUi(
            trackId = track.id,
            name = track.name,
            ducking = settings,
            keys = keys,
            keyTrackId = keyId,
            status = status,
            keyLevels = if (analysis == null) emptyList() else List(points) { levelAt(start + it * DuckingEditorUi.STEP_MS) },
            amounts = if (analysis == null) {
                emptyList()
            } else {
                List(points) { i -> (start + i * DuckingEditorUi.STEP_MS).let { ms -> if (ms < 0) 0f else analysis.envelope.amountAt(ms * 1000) } }
            },
            keyLevelDb = levelAt(playheadMs),
            duckDb = levels.ducks[track.id] ?: 0f,
        )
    }

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    // ---- voiceover (Resolve's Fairlight recording) --------------------------------------------------

    private fun editIdleVoiceover(change: (VoiceoverUi) -> VoiceoverUi) = _state.update { s ->
        val form = s.voiceover
        if (form == null || form.recording) s else s.copy(voiceover = change(form))
    }

    /** `voiceover-sheet` on the open timeline's audio tracks; a new track is the default while one can be added. */
    private fun openVoiceover() {
        if (_state.value.voiceover != null) return
        val d = doc ?: return
        if (_state.value.playing) program.pause()
        val audio = d.timeline.tracks.filter { it.kind == TrackKind.AUDIO }
        val canAdd = TracksUseCase.canAdd(d.timeline, TrackKind.AUDIO)
        val targets = audio.map { VoiceTargetUi(it.id, it.name) } + if (canAdd) listOf(VoiceTargetUi(null, "新建音轨")) else emptyList()
        val target = if (canAdd) null else audio.lastOrNull()?.id
        val at = _state.value.playheadFrame
        _state.update {
            it.copy(
                voiceover = VoiceoverUi(
                    targets = targets,
                    targetId = target,
                    startLabel = "从播放头 ${Timecode.format(at, d.format.fps)} 开始，录音时节目从这里播放",
                    note = voiceNote(target),
                ),
            )
        }
    }

    /** Where a take on [trackId] goes and what it covers. */
    private fun voiceNote(trackId: String?): String {
        val d = doc ?: return ""
        if (trackId == null) return "放在新建的音频轨上，不影响已有的声音"
        val name = d.timeline.track(trackId)?.name ?: return ""
        return "放在 $name 上：录音覆盖这条轨在这段时间里原有的声音"
    }

    private fun startVoiceover() {
        val v = _state.value.voiceover ?: return
        if (v.recording) return
        val d = doc ?: return
        if (v.targetId != null && d.timeline.track(v.targetId)?.locked == true) return toast("所选音频轨已锁定，先解锁再录音")
        if (!voice.start()) {
            val message = recordingError(voice.failure)
            return editIdleVoiceover { it.copy(recordingError = message, microphoneDenied = false) }
        }
        takePosition = TakePosition(d.timelineId, _state.value.playheadFrame, d.format.fps)
        takeStartedAt = clock()
        session.beforeNextFlush(finishBeforeSave)
        program.setMonitorMuted(!v.programSound)
        program.play()
        _state.update { it.copy(voiceover = v.copy(recording = true, microphoneDenied = false, recordingError = null, elapsedLabel = "00:00", level = 0f)) }
        takeTicker?.cancel()
        takeTicker = viewModelScope.launch {
            var level = 0f
            while (isActive) {
                delay(TAKE_TICK_MS)
                if (voice.failure != null) {
                    stopVoiceover()
                    return@launch
                }
                // The meter falls back slowly from each peak, as level meters do.
                level = maxOf(voice.level(), level * LEVEL_FALLBACK)
                val seconds = ((clock() - takeStartedAt) / 1000L).toInt()
                _state.update { s ->
                    s.copy(voiceover = s.voiceover?.copy(elapsedLabel = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), level = level))
                }
            }
        }
    }

    /** Ends the take and puts it on the timeline (one undoable step), selected. */
    private fun stopVoiceover() {
        val v = _state.value.voiceover ?: return
        val position = takePosition ?: return
        session.removeBeforeFlush(finishBeforeSave)
        takePosition = null
        takeTicker?.cancel()
        program.pause()
        program.setMonitorMuted(false)
        val take = voice.stop()
        _state.update { it.copy(voiceover = null) }
        if (take == null) {
            val failure = voice.failure ?: return toast("录音太短，没有保存")
            val message = recordingError(failure)
            _state.update { it.copy(voiceover = v.copy(recording = false, recordingError = message, elapsedLabel = "00:00", level = 0f)) }
            return
        }
        var result: VoiceoverUseCase.Result? = null
        session.edit("录制旁白") { d ->
            voiceovers.save(d, take, position.timelineId, position.frame, position.fps, v.targetId).also { result = it }.doc
        }
        result?.let {
            it.clipId?.let(session::select)
            toast(it.message)
        }
    }

    private fun recordingError(failure: VoiceRecorderRepository.Failure?) = when (failure) {
        VoiceRecorderRepository.Failure.STORAGE -> "无法保存录音，请检查设备存储空间后重试。"
        else -> "无法使用麦克风：请检查麦克风权限，或麦克风是否被占用"
    }

    /** Closes the sheet; a take being recorded is thrown away. */
    private fun closeVoiceover() {
        if (_state.value.voiceover?.recording == true) {
            session.removeBeforeFlush(finishBeforeSave)
            takePosition = null
            takeTicker?.cancel()
            program.pause()
            program.setMonitorMuted(false)
            voice.cancel()
            toast("已取消录音")
        }
        _state.update { it.copy(voiceover = null) }
    }

    override fun onCleared() {
        stopVoiceover()
        super.onCleared()
    }

    /** The selected sound clip, or the sound linked to the selected picture. */
    private fun selectedSound(timeline: Timeline): Clip? {
        val selectedId = session.selectedClipId.value
        return timeline.clip(selectedId)?.takeIf { it.kind == ClipKind.AUDIO } ?: props.audioTarget(timeline, selectedId)
    }

    /** The clip's loudness as heard; the first look at a file measures it in the background. */
    private fun loudnessFor(clip: Clip?, d: ProjectDocument, timeline: Timeline): LoudnessUi {
        clip ?: return LoudnessUi()
        val asset = d.asset(clip.assetId) ?: return LoudnessUi("片段没有可测量的音频")
        if (asset.offline) return LoudnessUi("素材离线，无法测量")
        if (asset.id !in profiles) {
            requestProfile(asset)
            return LoudnessUi("正在测量响度…")
        }
        val profile = profiles[asset.id] ?: return LoudnessUi("无法读取音频")
        val locked = timeline.track(clip.trackId)?.locked == true
        return when (val reading = levels.measure(clip, profile, d.format.fps)) {
            is LoudnessUseCase.Reading.Measured -> LoudnessUi(
                "${formatValue(reading.lufs, 1, false)} LUFS · 真峰值 ${formatValue(reading.truePeakDbtp, 1, false)} dBTP",
                canNormalize = !locked,
            )
            LoudnessUseCase.Reading.TooShort -> LoudnessUi("片段太短，无法测量（至少 0.4 秒）")
            LoudnessUseCase.Reading.Silent -> LoudnessUi("片段几乎无声")
        }
    }

    private fun normalize() {
        val d = doc ?: return
        val clip = selectedSound(d.timeline) ?: return toast("请先选择音频片段")
        val profile = profiles[clip.assetId] ?: return toast(if (clip.assetId in measuring) "响度还在测量中" else "无法读取音频")
        try {
            val target = LoudnessUseCase.TARGETS_LUFS[_state.value.loudnessTarget]
            val result = levels.normalize(d.timeline, clip.id, profile, target, d.format.fps)
            session.edit("响度标准化") { it.copy(timeline = result.timeline) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法标准化")
        }
    }

    private fun refreshSelection() {
        val timeline = preview ?: doc?.timeline ?: return
        val selection = timeline.clip(session.selectedClipId.value)?.takeIf { s -> _state.value.selection?.clipId == s.id }?.let { selectionFor(it, timeline) }
        if (selection != null && selection != _state.value.selection) _state.update { it.copy(selection = selection) }
    }

    /** Keyed volume shows (and is adjusted at) the level under the playhead. */
    private fun selectionFor(clip: Clip, timeline: Timeline): AudioClipUi {
        val local = _state.value.playheadFrame - clip.start
        val inside = local in 0 until clip.durationFrames
        return AudioClipUi(
            clipId = clip.id,
            name = clip.name,
            gainDb = clip.volumeAt(local.coerceIn(0, clip.durationFrames - 1)),
            locked = timeline.track(clip.trackId)?.locked == true,
            gainBlocked = clip.volumeKeyframes.isNotEmpty() && !inside,
            eqSummary = eqSummary(clip.eq),
            eqActive = clip.eq.isActive,
        )
    }

    private companion object {
        /** What the clip EQ sheet says it shapes (as the Edit page's). */
        const val CLIP_EQ_APPLIES_TO = "只作用于这个片段，在轨道的均衡器之前"
        const val BUS_EQ_APPLIES_TO = "作用于输出到这条总线的所有轨道，在各轨道的处理之后"
        const val NO_KEY_TRACK = "需要另一条音频轨道作为侧链"
        /** The short-term reading turns hot this far over the target. */
        const val HOT_LU = 1.0

        /**
         * A bin's power over its window's energy: white noise of RMS r then reads 20·log10 r in every band, so the floor
         * reads as a level ([NoiseProfile] powers are of the sqrt-Hann [NoiseGate.FFT_SIZE]-point spectrum).
         */
        val FLOOR_NORMALIZATION_DB = (10 * kotlin.math.log10(NoiseGate.FFT_SIZE / 2.0)).toFloat()
    }
}

/** How often the take's time and level are shown while it records. */
private const val TAKE_TICK_MS = 100L

/** How much of the last level the meter keeps each tick when the input falls. */
private const val LEVEL_FALLBACK = 0.8f
