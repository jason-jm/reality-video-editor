package com.example.prismstudio.p40b43a88.ui.edit

import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import com.example.prismstudio.p40b43a88.ui.components.TrackUi
import com.example.prismstudio.p40b43a88.domain.usecase.WindowTrackUseCase
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.prismstudio.p40b43a88.ui.components.timelineLookKey
import com.example.prismstudio.p40b43a88.ui.components.timelineLook
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.panAt
import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import com.example.prismstudio.p40b43a88.domain.usecase.GeneratorUseCase
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.usecase.ClipMaskUseCase
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.data.repository.GeneratorRepository
import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.usecase.EdlImportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EdlExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FcpxmlExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FcpxmlImportUseCase
import com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository
import com.example.prismstudio.p40b43a88.domain.model.TimecodeEntry
import com.example.prismstudio.p40b43a88.domain.usecase.ClipDurationUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RenameUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SwapClipsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelineGap
import com.example.prismstudio.p40b43a88.domain.usecase.GapsUseCase
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.usecase.TrimToPlayheadUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.AddTransitionsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionScope
import com.example.prismstudio.p40b43a88.domain.model.SourceMarks
import com.example.prismstudio.p40b43a88.ui.components.LibraryArt
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.DocumentFailure
import com.example.prismstudio.p40b43a88.data.repository.DocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopy
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.SourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.StillFrameRepository
import com.example.prismstudio.p40b43a88.ui.audio.EqEditorUi
import com.example.prismstudio.p40b43a88.ui.color.PickOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.usecase.sourcePixelAt
import com.example.prismstudio.p40b43a88.domain.usecase.sourceMsAt
import com.example.prismstudio.p40b43a88.domain.model.eqSummary
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.amountAt
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.framingOf
import com.example.prismstudio.p40b43a88.domain.model.scalingNote
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.LibraryTab
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.model.keyframeAt
import com.example.prismstudio.p40b43a88.domain.model.subtitleStyleOf
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframeAt
import com.example.prismstudio.p40b43a88.domain.usecase.RetimeUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.AddMarkerUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.AddTitleUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.AppendMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.AudioCrossFades
import com.example.prismstudio.p40b43a88.domain.usecase.ClipAttribute
import com.example.prismstudio.p40b43a88.domain.usecase.ClipPropertiesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.DecorateClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditTargetsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MarkersUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelineMarksUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.KeyframeUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.KeyframeCurvesUseCase
import com.example.prismstudio.p40b43a88.domain.model.KeyCurveChannel
import com.example.prismstudio.p40b43a88.domain.model.CurveEase
import com.example.prismstudio.p40b43a88.domain.model.KeyCurve
import com.example.prismstudio.p40b43a88.domain.usecase.PasteAttributesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SubtitleImportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SubtitleStyleUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelineSettingsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CompoundClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MulticamUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexFilter
import com.example.prismstudio.p40b43a88.domain.usecase.TitleStyleUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ToggleClipEnabledUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TracksUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionHandles
import com.example.prismstudio.p40b43a88.domain.usecase.MoveClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ClipboardUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FreezeFrameUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ClipColorUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FlagUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.LinkClipsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MarqueeSelectUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RangeDeleteUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RippleDeleteUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEditUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SplitClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TrimClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TrimModesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.selectedGroups
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.TimelineScale
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi
import com.example.prismstudio.p40b43a88.ui.components.TransitionSpanUi
import com.example.prismstudio.p40b43a88.ui.components.keyframeUi
import com.example.prismstudio.p40b43a88.ui.components.route
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.prismstudio.p40b43a88.domain.usecase.StabilizationUseCase
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import com.example.prismstudio.p40b43a88.data.repository.StabilizationRepository
import kotlin.math.abs
import com.example.prismstudio.p40b43a88.domain.usecase.WaveformAlignUseCase
import com.example.prismstudio.p40b43a88.data.repository.SoundEnvelopeRepository
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.example.prismstudio.p40b43a88.domain.usecase.LumaFrames
import com.example.prismstudio.p40b43a88.domain.usecase.SceneCutUseCase
import com.example.prismstudio.p40b43a88.data.repository.LumaFrameRepository
import com.example.prismstudio.p40b43a88.domain.usecase.AutoSyncAudioUseCase

/** Edit stage: asset library, program / source viewer, inspector and the timeline. */
class EditViewModel(
    private val session: EditorSessionRepository,
    private val program: ProgramPlaybackRepository,
    private val source: SourcePlaybackRepository,
    private val previews: MediaPreviewRepository,
    ids: IdGenerator,
    private val reverseCopies: ReverseCopyRepository = ReverseCopyRepository.None,
    private val documents: DocumentRepository = DocumentRepository.None,
    private val stills: StillFrameRepository = StillFrameRepository.None,
    /** Where exports for other apps go (`timeline-menu-export-edl`: Download/Reality Video Editor). */
    private val downloads: DownloadsRepository = DownloadsRepository.None,
    /** Resolve's Generators: their images, made into app storage. */
    private val generators: GeneratorRepository = GeneratorRepository.None,
    /** Resolve's Stabilization: the files' camera analyses. */
    private val stabilization: StabilizationRepository = StabilizationRepository.None,
    private val stabilizer: StabilizationUseCase = StabilizationUseCase(),
    /** Resolve's Auto Align Clips: the files' sound levels, and the matching. */
    private val envelopes: SoundEnvelopeRepository = SoundEnvelopeRepository.None,
    private val waveAlign: WaveformAlignUseCase = WaveformAlignUseCase(),
    /** Resolve's Detect Scene Cuts: the clip's frames, and where its shots change. */
    private val lumaFrames: LumaFrameRepository = LumaFrameRepository.None,
    private val sceneCuts: SceneCutUseCase = SceneCutUseCase(),
    private val tracker: WindowTrackUseCase = WindowTrackUseCase(),
    private val edl: EdlExportUseCase = EdlExportUseCase(),
    private val fcpxml: FcpxmlExportUseCase = FcpxmlExportUseCase(),
    private val fcpxmlImport: FcpxmlImportUseCase = FcpxmlImportUseCase(ids),
    private val edlImport: EdlImportUseCase = EdlImportUseCase(ids),
    private val split: SplitClipUseCase = SplitClipUseCase(ids),
    private val ripple: RippleDeleteUseCase = RippleDeleteUseCase(ids),
    private val move: MoveClipUseCase = MoveClipUseCase(),
    private val gaps: GapsUseCase = GapsUseCase(),
    private val swap: SwapClipsUseCase = SwapClipsUseCase(),
    private val rename: RenameUseCase = RenameUseCase(),
    private val clipDuration: ClipDurationUseCase = ClipDurationUseCase(),
    private val trim: TrimClipUseCase = TrimClipUseCase(),
    private val trimToPlayhead: TrimToPlayheadUseCase = TrimToPlayheadUseCase(trim),
    private val append: AppendMediaUseCase = AppendMediaUseCase(ids),
    private val sourceEdits: SourceEditUseCase = SourceEditUseCase(ids, append),
    /** Resolve's Auto Sync Audio: a synced video brings its recorder's sound when edited in. */
    private val autoSync: AutoSyncAudioUseCase = AutoSyncAudioUseCase(),
    private val generatorPlacement: GeneratorUseCase = GeneratorUseCase(sourceEdits),
    private val addTitle: AddTitleUseCase = AddTitleUseCase(ids),
    private val decorate: DecorateClipUseCase = DecorateClipUseCase(ids),
    private val transitions: AddTransitionsUseCase = AddTransitionsUseCase(),
    private val addMarker: AddMarkerUseCase = AddMarkerUseCase(ids),
    private val props: ClipPropertiesUseCase = ClipPropertiesUseCase(),
    private val keyframes: KeyframeUseCase = KeyframeUseCase(),
    private val masks: ClipMaskUseCase = ClipMaskUseCase(),
    private val retime: RetimeUseCase = RetimeUseCase(),
    private val trims: TrimModesUseCase = TrimModesUseCase(),
    private val trackEdits: TracksUseCase = TracksUseCase(),
    private val destinations: EditTargetsUseCase = EditTargetsUseCase(),
    private val timelineMarks: TimelineMarksUseCase = TimelineMarksUseCase(),
    private val rangeEdits: RangeDeleteUseCase = RangeDeleteUseCase(ids),
    private val clipColors: ClipColorUseCase = ClipColorUseCase(),
    private val flagging: FlagUseCase = FlagUseCase(),
    private val marquee: MarqueeSelectUseCase = MarqueeSelectUseCase(),
    private val links: LinkClipsUseCase = LinkClipsUseCase(ids),
    private val markerEdits: MarkersUseCase = MarkersUseCase(),
    private val subtitleFiles: SubtitleImportUseCase = SubtitleImportUseCase(ids),
    private val clipboardEdits: ClipboardUseCase = ClipboardUseCase(ids),
    private val pasteAttributes: PasteAttributesUseCase = PasteAttributesUseCase(),
    private val timelineSettings: TimelineSettingsUseCase = TimelineSettingsUseCase(),
    private val timelinesEdits: TimelinesUseCase = TimelinesUseCase(ids),
    private val toggleEnabled: ToggleClipEnabledUseCase = ToggleClipEnabledUseCase(),
    private val subtitleStyles: SubtitleStyleUseCase = SubtitleStyleUseCase(),
    private val titleStyles: TitleStyleUseCase = TitleStyleUseCase(),
    private val freezes: FreezeFrameUseCase = FreezeFrameUseCase(ids),
    private val compounds: CompoundClipUseCase = CompoundClipUseCase(ids),
    private val multicams: MulticamUseCase = MulticamUseCase(ids),
    private val timelinePool: TimelinePoolUseCase = TimelinePoolUseCase(),
    private val editIndex: EditIndexUseCase = EditIndexUseCase(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where heavy work runs (tracking, thumbnails' tone maps); tests run it on their own scheduler. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private var preview: Timeline? = null
    private var clipboard: ClipboardUseCase.Content? = null
    /** What Paste Attributes pastes, kept between uses (Resolve keeps the dialog's choices too). */
    // Everything but the speed, which changes the clip's length (Resolve leaves Speed Change off too).
    private var attributeChoice: Set<ClipAttribute> = ClipAttribute.entries.toSet() - ClipAttribute.SPEED

    /** The sound clip whose own equaliser the open `eq-sheet` edits, or null while it is closed. */
    private var eqClipId: String? = null
    private val curveEdits = KeyframeCurvesUseCase()
    /** The curve the keyframe curve editor shows ([KeyCurveChannel.key]) and its selected key, for the clip they were picked on. */
    private var curveKey: String? = null
    private var curveKeyIndex: Int? = null
    private var curveClipId: String? = null
    private var zoom = 0.35f
    /** The timeline has been zoomed by hand; until then it is fitted to the panel's width when that is known. */
    private var zoomed = false
    /** The media the source monitor holds, for a replace to line up with its playhead. */
    private var sourceAssetId: String? = null
    private val thumbs = mutableMapOf<String, Bitmap>()
    /** Timeline pictures by file, second and look, shared by every clip showing them; the clips waiting for each one. */
    private val sharedThumbs = mutableMapOf<String, Bitmap>()
    private val waitingThumbs = mutableMapOf<String, MutableList<String>>()
    /** `multicam-panel`: what a tap on an angle switches, whether it switches the whole segment, and the angles' frames. */
    private var multicamMode = 0
    private var multicamWhole = false
    private val angleFrames = mutableMapOf<String, Bitmap>()
    /** Each clip's last Dynamic Zoom settings, for switching it back on. */
    private val lastZooms = mutableMapOf<String, DynamicZoom>()

    // The keyer's eyedropper (a tap on the monitor picks the screen's colour) and the clip whose matte the monitor shows.
    private var keyPicking = false
    private var keyMatteClip: String? = null
    private val assetFrames = mutableMapOf<String, Bitmap>()
    private val waves = mutableMapOf<String, Waveform>()
    private val requested = mutableSetOf<String>()
    /** Reading sound for Auto Align Clips. */
    private var aligning = false

    init {
        viewModelScope.launch { session.document.collect { publish() } }
        viewModelScope.launch { reverseCopies.copies.collect { publish() } }
        viewModelScope.launch { stabilization.analyses.collect { publish() } }
        viewModelScope.launch {
            session.selectedClipId.collect { id ->
                followSelection(id)
                if (id != keyMatteClip) endKeyViews()
                publish()
            }
        }
        // Adding or removing a clip that is not the inspector's changes only the list.
        viewModelScope.launch { session.selectedClipIds.collect { publish() } }
        viewModelScope.launch {
            program.state.collect { p ->
                val before = _state.value.playheadFrame
                _state.update {
                    it.copy(playheadFrame = Timecode.positionToFrame(p.positionMs, it.fps), playing = p.playing, previewApproximate = p.approximate, programLayers = p.layers, programComposites = p.compositesAt())
                }
                // The inspector shows an animated clip's values at the playhead.
                if (_state.value.playheadFrame != before) refreshInspector()
                updateProgramMessage()
            }
        }
        viewModelScope.launch {
            source.state.collect { p ->
                _state.update { it.copy(sourcePositionFrames = Timecode.positionToFrame(p.positionMs, it.fps), sourcePlaying = p.playing) }
            }
        }
    }

    private val doc: ProjectDocument? get() = session.document.value

    /** Every selected clip, the inspector's first. */
    private val selection: List<String> get() = session.selectedClipIds.value

    /** The gap a tap on an empty lane picked (Resolve), until something is selected or it closes. */
    private var gap: TimelineGap? = null

    /** Opens the inspector page that has something to edit for a newly selected clip. */
    private fun followSelection(id: String?) {
        val clip = id?.let { doc?.timeline?.clip(it) } ?: return
        _state.update { s ->
            when {
                clip.kind == ClipKind.AUDIO && s.inspectorTab == EditUiState.TAB_VIDEO -> s.copy(inspectorTab = EditUiState.TAB_AUDIO)
                clip.kind != ClipKind.AUDIO && clip.linkId == null && s.inspectorTab == EditUiState.TAB_AUDIO ->
                    s.copy(inspectorTab = EditUiState.TAB_VIDEO)
                else -> s
            }
        }
    }

    fun onEvent(event: EditEvent) {
        when (event) {
            is EditEvent.SelectLibraryTab -> {
                _state.update { it.copy(libraryTab = LibraryTab.entries[event.index.coerceIn(0, LibraryTab.entries.lastIndex)], libraryItemId = null) }
                publish()
            }
            is EditEvent.SelectLibraryItem -> {
                _state.update { it.copy(libraryItemId = event.id) }
                if (_state.value.viewerMode == EditUiState.VIEWER_SOURCE) loadSourceMonitor()
                publish()
            }
            EditEvent.ApplyLibraryItem -> if (_state.value.libraryTab == LibraryTab.MARKERS) addMarkerHere() else applyLibraryItem()
            is EditEvent.EditFromSource -> sourceEdit(event.mode)
            is EditEvent.ToggleTarget -> retarget(event.trackId)
            // I / O / Alt+X mark the viewer that shows: the source's media, or the timeline (Resolve).
            EditEvent.MarkIn -> if (sourceShown) sourceMark(isIn = true) else mark { t, fps -> timelineMarks.markIn(t, _state.value.playheadFrame, fps) }
            EditEvent.MarkOut -> if (sourceShown) sourceMark(isIn = false) else mark { t, fps -> timelineMarks.markOut(t, _state.value.playheadFrame, fps) }
            EditEvent.MarkClip -> mark { t, fps -> timelineMarks.markClip(t, session.selectedClipId.value, _state.value.playheadFrame, fps) }
            EditEvent.ClearMarks -> if (sourceShown) clearSourceMarks() else mark { t, _ -> timelineMarks.clear(t) }
            EditEvent.ClearSourceMarks -> clearSourceMarks()
            is EditEvent.MatchFrame -> matchFrame(event.clipId)
            is EditEvent.AddTransitions -> addTransitions(event.scope)
            is EditEvent.TrimToPlayhead -> apply(trimToPlayheadLabel(event)) { d ->
                trimToPlayhead(d.timeline, event.edge, event.ripple, _state.value.playheadFrame, selection, d::asset, d.format.fps)
            }
            is EditEvent.RequestLibraryPreview -> loadAssetFrame(event.id)
            is EditEvent.ViewerMode -> {
                _state.update { it.copy(viewerMode = event.mode.coerceIn(0, 1)) }
                program.pause()
                source.pause()
                if (event.mode == EditUiState.VIEWER_SOURCE) loadSourceMonitor()
            }
            is EditEvent.InspectorTab -> _state.update { it.copy(inspectorTab = event.index.coerceIn(0, 3)) }
            is EditEvent.SelectClip -> {
                gap = null
                session.select(event.clipId)
            }
            is EditEvent.SelectGap -> {
                gap = doc?.let { gaps.gapAt(it.timeline, event.trackId, event.frame) }
                session.select(null)
                publish()
            }
            EditEvent.DeleteGap -> deleteGap()
            EditEvent.ExportEdl -> exportEdl()
            EditEvent.ExportFcpxml -> exportFcpxml()
            is EditEvent.ImportEdl -> importEdl(event.uri)
            is EditEvent.ImportFcpxml -> importFcpxml(event.uri)
            is EditEvent.OpenRenameClip -> doc?.timeline?.clip(event.clipId ?: session.selectedClipId.value)?.let { c ->
                _state.update { it.copy(rename = renameUi(RenameUi("重命名片段", clipId = c.id, original = c.name, text = I18n.translate(c.name), hint = ""))) }
            } ?: toast("请先在时间线上选择片段")
            is EditEvent.OpenRenameTrack -> doc?.timeline?.track(event.trackId)?.let { t ->
                _state.update { it.copy(rename = renameUi(RenameUi("重命名轨道 ${t.name}", trackId = t.id, original = t.name, text = I18n.translate(t.name), hint = ""))) }
            }
            is EditEvent.RenameText -> _state.update { s -> s.copy(rename = s.rename?.let { renameUi(it.copy(text = event.text)) }) }
            EditEvent.CloseRename -> _state.update { it.copy(rename = null) }
            is EditEvent.OpenClipDuration -> doc?.let { d ->
                val c = d.timeline.clip(event.clipId ?: session.selectedClipId.value) ?: return toast("请先在时间线上选择片段")
                val tc = Timecode.format(c.durationFrames, d.format.fps)
                _state.update { it.copy(clipDuration = durationUi(ClipDurationUi(c.id, c.name, tc, tc, originalFrames = c.durationFrames))) }
            }
            is EditEvent.ClipDurationText -> _state.update { s -> s.copy(clipDuration = s.clipDuration?.let { durationUi(it.copy(text = event.text)) }) }
            is EditEvent.ClipDurationRipple -> _state.update { s -> s.copy(clipDuration = s.clipDuration?.copy(ripple = event.ripple)) }
            EditEvent.CloseClipDuration -> _state.update { it.copy(clipDuration = null) }
            is EditEvent.OpenTimecodeEntry -> _state.update { it.copy(timecodeEntry = timecodeUi(event.prefill)) }
            is EditEvent.TimecodeEntryText -> _state.update { s -> s.copy(timecodeEntry = s.timecodeEntry?.let { timecodeUi(event.text) }) }
            EditEvent.CloseTimecodeEntry -> _state.update { it.copy(timecodeEntry = null) }
            EditEvent.ConfirmTimecodeEntry -> {
                val ui = _state.value.timecodeEntry ?: return
                val entry = TimecodeEntry.parse(ui.text, doc?.format?.fps ?: 30) ?: return
                _state.update { it.copy(timecodeEntry = null) }
                // An offset moves the selected clips (Resolve); otherwise the playhead goes there.
                if (entry.relative && selection.isNotEmpty()) onEvent(EditEvent.Nudge(entry.sign * entry.frames))
                else seek(entry.target(_state.value.playheadFrame))
            }
            EditEvent.ConfirmClipDuration -> {
                val ui = _state.value.clipDuration ?: return
                val frames = ui.frames ?: return
                val done = apply("更改片段时长") { d -> clipDuration(d.timeline, ui.clipId, frames, ui.ripple, d::asset, d.format.fps) }
                if (done) _state.update { it.copy(clipDuration = null) }
            }
            EditEvent.ConfirmRename -> {
                val r = _state.value.rename ?: return
                // Left as shown, an app-made name ("定格 · …") keeps its stored form.
                val text = I18n.stored(r.text, r.original)
                val done = if (r.clipId != null) apply("重命名片段") { d -> rename.clip(d.timeline, r.clipId, text) }
                else apply("重命名轨道") { d -> rename.track(d.timeline, r.trackId ?: "", text) }
                if (done) _state.update { it.copy(rename = null) }
            }
            is EditEvent.SwapClip -> apply(if (event.right) "与右侧片段交换" else "与左侧片段交换") { d -> swap(d.timeline, selection, event.right) }
            EditEvent.DeleteAllGaps -> apply("删除所有空隙") { d -> gaps.closeAll(d.timeline, d.format.fps) }
            is EditEvent.ToggleSelected -> session.toggleSelected(event.clipId)
            is EditEvent.CurveMode -> {
                _state.update { it.copy(curveMode = event.on) }
                refreshInspector()
            }
            is EditEvent.CurvePicked -> {
                curveKey = event.key
                curveKeyIndex = null
                refreshInspector()
            }
            is EditEvent.CurveKeyPicked -> {
                curveKeyIndex = event.index
                refreshInspector()
            }
            is EditEvent.CurveKeyMoved -> {
                curveKeyIndex = event.index
                curveEdit("移动关键帧", "curve-move:${event.drag}") { t, id, ch -> curveEdits.moveKey(t, id, ch, event.index, event.frame, event.value) }
            }
            is EditEvent.CurveSlope -> {
                curveKeyIndex = event.index
                curveEdit("关键帧斜率", "curve-slope:${event.drag}") { t, id, ch -> curveEdits.setSlope(t, id, ch, event.index, event.arriving, event.slope) }
            }
            is EditEvent.CurveEasePicked -> curveChannel()?.let { ch ->
                val index = curveKeyIndex ?: return@let toast("先在曲线上点选一个关键帧")
                apply("关键帧缓动") { d -> curveEdits.ease(d.timeline, session.selectedClipId.value.orEmpty(), ch, index, event.ease) }
            }
            EditEvent.CurveAddKey -> curveChannel()?.let { ch ->
                val id = session.selectedClipId.value ?: return@let
                val document = doc ?: return@let
                val frame = playheadIn(document.timeline, id)
                if (apply("添加关键帧") { d -> curveEdits.addKey(d.timeline, id, ch, frame) }) {
                    curveKeyIndex = doc?.timeline?.clip(id)?.let { c -> curveEdits.curve(c, ch)?.keys?.indexOfFirst { it.frame == frame } }?.takeIf { it >= 0 }
                    refreshInspector()
                }
            }
            EditEvent.CurveDeleteKey -> curveChannel()?.let { ch ->
                val index = curveKeyIndex ?: return@let toast("先在曲线上点选一个关键帧")
                if (apply("删除关键帧") { d -> curveEdits.deleteKey(d.timeline, session.selectedClipId.value.orEmpty(), ch, index) }) {
                    curveKeyIndex = null
                    refreshInspector()
                }
            }
            is EditEvent.MultiSelect -> {
                _state.update { it.copy(multiSelect = event.on) }
                publish()
                toast(if (event.on) "多选已开启：点按片段加入或移出选择，在空白处拖动可框选" else "多选已关闭")
            }
            is EditEvent.SelectInMarquee -> doc?.let { d ->
                val before = selection
                val after = marquee.select(d.timeline, event.from, event.until, event.trackIds, before)
                if (after.size == before.size) return toast("框内没有可加入的片段")
                session.selectAll(after)
                toast("已框选 ${after.size - before.size} 个片段")
            }
            EditEvent.SelectAll -> doc?.let { d -> selectGroups(d.timeline.clips) }
            is EditEvent.SelectForward -> doc?.let { d ->
                val from = event.clipId?.let { d.timeline.clip(it)?.start } ?: _state.value.playheadFrame
                val forward = d.timeline.clips.filter { it.start >= from }
                if (forward.isEmpty()) toast("播放头之后没有片段") else selectGroups(forward)
            }
            is EditEvent.Seek -> seek(event.frame)
            is EditEvent.Move -> moveClip(event)
            is EditEvent.Trim -> trimClip(event)
            is EditEvent.TransitionDrag -> transitionDrag(event)
            is EditEvent.FadeDrag -> fadeDrag(event)
            is EditEvent.FadeCurveDrag -> fadeCurveDrag(event)
            is EditEvent.VolumeLine -> volumeLine(event)
            EditEvent.CancelDrag -> {
                preview = null
                publish()
            }
            is EditEvent.Zoom -> {
                zoom = event.zoom.coerceIn(0f, 1f)
                zoomed = true
                publish()
            }
            EditEvent.FitTimeline -> {
                val document = doc ?: return
                zoom = TimelineScale.fitZoom(document.durationFrames, document.format.fps, _state.value.viewportDp)
                publish()
            }
            is EditEvent.Viewport -> {
                _state.update { it.copy(viewportDp = event.widthDp) }
                // A project opens showing its whole timeline (as the Audio page does), until the editor zooms.
                val document = doc
                if (!zoomed && document != null && event.widthDp > 0f) {
                    zoom = TimelineScale.fitZoom(document.durationFrames, document.format.fps, event.widthDp)
                    publish()
                }
            }
            EditEvent.Split -> apply("分割片段") { d -> split(d.timeline, _state.value.playheadFrame, selection, d.format.fps) }
            EditEvent.RippleDelete -> {
                // No clip selected: the picked gap, else the In–Out range, as in Resolve.
                val ids = selection.ifEmpty { return if (liveGap() != null) deleteGap() else if (hasRange()) extractRange() else toast(NOTHING_TO_DELETE) }
                apply("波纹删除") { d -> ripple(d.timeline, ids, d.format.fps) }
            }
            is EditEvent.SetClipColor -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                apply(if (event.color == null) "清除片段颜色" else "片段颜色") { d -> clipColors.set(d.timeline, ids, event.color) }
            }
            is EditEvent.ToggleFlag -> flag("${event.color.label}色旗标") { d -> flagging.toggle(d, flagging.mediaOf(d, selection), event.color) }
            EditEvent.ClearFlags -> flag("清除旗标") { d -> flagging.clear(d, flagging.mediaOf(d, selection)) }
            is EditEvent.SelectClipColor -> doc?.let { d ->
                try {
                    val ids = clipColors.select(d.timeline, event.color)
                    session.selectAll(ids)
                    toast("已选择 ${ids.size} 个${event.color.label}色片段")
                } catch (e: EditRejected) {
                    toast(e.message ?: "没有这种颜色的片段")
                }
            }
            EditEvent.LiftRange -> liftRange()
            EditEvent.ExtractRange -> extractRange()
            EditEvent.CopyClip -> copyClip()
            EditEvent.CutClip -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                apply("剪切片段") { d -> clipboardEdits.cut(d.timeline, ids).let { (content, result) -> keep(content); result } }
            }
            EditEvent.PasteClips -> {
                val content = clipboard ?: return toast("剪贴板是空的")
                val at = _state.value.playheadFrame
                // As in Resolve the playhead moves past the pasted clips, so pastes follow one another.
                if (apply("粘贴片段") { d -> clipboardEdits.paste(d.timeline, content, at, d.format.fps) }) seek(at + content.durationFrames)
            }
            EditEvent.RippleCut -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                apply("波纹剪切") { d -> clipboardEdits.rippleCut(d.timeline, ids, d.format.fps).let { (content, result) -> keep(content); result } }
            }
            EditEvent.PasteInsert -> {
                val content = clipboard ?: return toast("剪贴板是空的")
                val at = _state.value.playheadFrame
                if (apply("插入粘贴") { d -> clipboardEdits.pasteInsert(d.timeline, content, at, d.format.fps) }) seek(at + content.durationFrames)
            }
            EditEvent.DuplicateClip -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                apply("创建副本") { d -> clipboardEdits.duplicate(d.timeline, ids, d.format.fps) }
            }
            EditEvent.FreezeFrame -> freezeFrame()
            EditEvent.ToggleLink -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                val action = doc?.timeline?.let { links.actionFor(it, ids) }
                apply(action?.label ?: "链接片段") { d -> links.toggle(d.timeline, ids) }
            }
            EditEvent.ToggleClipEnabled -> {
                val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
                val timeline = doc?.timeline ?: return
                // Several: off while any still plays, else all back on.
                val enabled = ids.any { timeline.clip(it)?.enabled == true }
                apply(if (enabled) "停用片段" else "启用片段") { d -> toggleEnabled(d.timeline, ids) }
            }
            is EditEvent.SubtitleStyleValue -> inspect("字幕样式：${event.control.label}", "subtitle-${event.control}") { t, id ->
                subtitleStyles(t, id) { event.control.write(it, event.value) }
            }
            is EditEvent.SubtitleStyleColor -> inspect("字幕样式：颜色", null) { t, id -> subtitleStyles(t, id) { it.copy(color = event.color) } }
            is EditEvent.SubtitleStyleFlag -> inspect("字幕样式：${event.flag.label}", null) { t, id ->
                subtitleStyles(t, id) {
                    when (event.flag) {
                        SubtitleFlag.BOLD -> it.copy(bold = event.on)
                        SubtitleFlag.OUTLINE -> it.copy(outline = event.on)
                        SubtitleFlag.BOX -> it.copy(box = event.on)
                    }
                }
            }
            EditEvent.ResetSubtitleStyle -> inspect("重置字幕样式", null) { t, id -> subtitleStyles(t, id) { SubtitleStyle.DEFAULT } }
            is EditEvent.TitleStyleColor -> inspect("标题样式：颜色", null) { t, id -> titleStyles(t, id) { it.copy(color = event.color) } }
            is EditEvent.TitleStyleWeight -> inspect("标题样式：字重", null) { t, id -> titleStyles(t, id) { it.copy(weight = event.weight) } }
            is EditEvent.TitleStyleFlag -> inspect("标题样式：${event.flag.label}", null) { t, id ->
                titleStyles(t, id) {
                    when (event.flag) {
                        TitleFlag.OUTLINE -> it.copy(outline = event.on)
                        TitleFlag.SHADOW -> it.copy(shadow = event.on)
                        TitleFlag.BOX -> it.copy(box = event.on)
                    }
                }
            }
            is EditEvent.TitleBoxOpacity -> inspect("标题样式：背景", "title-box-opacity") { t, id ->
                titleStyles(t, id) { it.copy(boxOpacityPercent = event.value.roundToInt()) }
            }
            is EditEvent.TitleStyleAnimation -> inspect("标题样式：动画", null) { t, id -> titleStyles(t, id) { it.copy(animation = event.animation) } }
            EditEvent.ResetTitleStyle -> inspect("重置标题样式", null) { t, id -> titleStyles(t, id) { it.reset() } }
            EditEvent.OpenTimelineSettings -> doc?.let { d ->
                _state.update { it.copy(timelineSettings = timelineSettingsUi(d, FormatPreset.of(d.format)?.ordinal ?: 0, d.format.scaling, I18n.translate(d.timelineName))) }
            }
            is EditEvent.TimelineSettingsName -> editTimelineSettings { d, ui -> timelineSettingsUi(d, ui.formatIndex, ui.scaling, event.name) }
            is EditEvent.OpenTimeline -> switchTimeline("切换时间线") { d -> timelinesEdits.open(d, event.id) }
            EditEvent.NewTimeline -> switchTimeline("新建时间线") { d -> timelinesEdits.create(d) }
            EditEvent.DuplicateTimeline -> switchTimeline("复制时间线") { d -> timelinesEdits.duplicate(d) }
            EditEvent.DeleteTimeline -> switchTimeline("删除时间线") { d -> timelinesEdits.delete(d) }
            EditEvent.CloseTimelineSettings -> _state.update { it.copy(timelineSettings = null) }
            is EditEvent.TimelineSettingsFormat -> editTimelineSettings { d, ui -> timelineSettingsUi(d, event.index, ui.scaling, ui.name) }
            is EditEvent.TimelineSettingsScaling -> editTimelineSettings { d, ui -> timelineSettingsUi(d, ui.formatIndex, event.scaling, ui.name) }
            EditEvent.ApplyTimelineSettings -> applyTimelineSettings()
            EditEvent.OpenPasteAttributes -> openPasteAttributes()
            EditEvent.ClosePasteAttributes -> _state.update { it.copy(pasteAttributes = null) }
            is EditEvent.PasteAttribute -> {
                attributeChoice = if (event.on) attributeChoice + event.attribute else attributeChoice - event.attribute
                _state.update { s ->
                    s.copy(pasteAttributes = s.pasteAttributes?.let { ui ->
                        ui.copy(options = ui.options.map { it.copy(selected = it.attribute in attributeChoice) })
                    })
                }
            }
            EditEvent.ConfirmPasteAttributes -> {
                val content = clipboard ?: return
                val ids = selection.ifEmpty { return }
                val chosen = attributeChoice
                if (apply("粘贴属性") { d -> pasteAttributes(d.timeline, content, ids, chosen, d.format.fps, stillIds(d)) }) {
                    _state.update { it.copy(pasteAttributes = null) }
                }
            }
            EditEvent.DeleteClip -> {
                val ids = selection.ifEmpty { return if (liveGap() != null) deleteGap() else if (hasRange()) liftRange() else toast(NOTHING_TO_DELETE) }
                apply("删除片段") { d -> clipboardEdits.delete(d.timeline, ids) }
            }
            EditEvent.AddMarker -> addMarkerHere()
            is EditEvent.SelectMarker -> selectMarker(event.id)
            is EditEvent.MarkerDrag -> markerDrag(event)
            is EditEvent.RenameMarker -> marker("重命名标记", "marker-name") { t, id -> markerEdits.rename(t, id, event.name) }
            is EditEvent.RecolorMarker -> marker("标记颜色", null) { t, id -> markerEdits.recolor(t, id, event.color) }
            is EditEvent.NoteMarker -> marker("标记备注", "marker-note") { t, id -> markerEdits.annotate(t, id, event.note) }
            EditEvent.DeleteMarker -> deleteMarker()
            is EditEvent.MarkerDuration -> doc?.let { d ->
                val frames = (event.seconds * d.format.fps).roundToInt()
                marker("标记时长", "marker-duration") { t, id -> markerEdits.setDuration(t, id, frames, maxOf(t.durationFrames, d.durationFrames)) }
            }
            EditEvent.MarkerRangeFromMarks -> marker("标记范围：用入出点", null) { t, id -> markerEdits.rangeFromMarks(t, id) }
            EditEvent.MarksFromMarkerRange -> {
                marker("入出点：用标记范围", null) { t, id -> markerEdits.marksFromRange(t, id) }
                doc?.timeline?.marks?.inFrame?.let { seek(it) }
            }
            EditEvent.PreviousMarker -> doc?.let { d -> markerEdits.previous(d.timeline, _state.value.playheadFrame)?.let { selectMarker(it.id) } }
            EditEvent.NextMarker -> doc?.let { d -> markerEdits.next(d.timeline, _state.value.playheadFrame)?.let { selectMarker(it.id) } }
            is EditEvent.SetSnapping -> {
                _state.update { it.copy(snapping = event.enabled) }
                toast(if (event.enabled) "吸附已开启" else "吸附已关闭")
            }
            // A subtitle track shown hides the other languages (TracksUseCase.setEnabled).
            is EditEvent.TrackEnabled -> editTracks(if (event.enabled) "显示轨道" else "隐藏轨道") { t -> trackEdits.setEnabled(t, event.trackId, event.enabled) }
            is EditEvent.SubtitleLanguage -> {
                try {
                    editTracks("字幕语言：${event.language.label}") { t -> trackEdits.setLanguage(t, event.trackId, event.language) }
                } catch (e: EditRejected) {
                    toast(e.message ?: "无法更换语言")
                }
            }
            is EditEvent.AddTrack -> changeTracks("添加轨道") { d -> trackEdits.add(d, event.kind) }
            is EditEvent.RemoveTrack -> changeTracks("删除轨道") { d -> trackEdits.remove(d, event.trackId) }
            is EditEvent.TrackLocked -> editTracks(if (event.locked) "锁定轨道" else "解锁轨道") { t ->
                t.copy(tracks = t.tracks.map { if (it.id == event.trackId) it.copy(locked = event.locked) else it })
            }
            is EditEvent.TitleText -> inspect("修改标题文字", "title-text") { t, id -> props.title(t, id, text = event.text) }
            is EditEvent.TitleSize -> inspect("修改字号", "title-size") { t, id -> props.title(t, id, sizePx = event.size.roundToInt()) }
            is EditEvent.ImportSubtitles -> importSubtitles(event.uri)
            EditEvent.NextSubtitle -> session.selectedClipId.value?.let { id ->
                apply("添加字幕") { d -> addTitle.nextSubtitle(d.timeline, id, d.format.fps) }
                // The playhead follows to the new subtitle, ready for the next line.
                session.selectedClipId.value?.takeIf { it != id }?.let { next -> doc?.timeline?.clip(next)?.let { seek(it.start) } }
            }
            // A keyed clip records the change at the playhead (frames from the clip's start).
            is EditEvent.Scale -> inspect("缩放", "scale") { t, id -> props.transform(t, id, playheadIn(t, id)) { it.copy(scalePercent = event.value.roundToInt()) } }
            is EditEvent.PositionX -> inspect("位置 X", "pos-x") { t, id -> props.transform(t, id, playheadIn(t, id)) { it.copy(positionX = event.value.roundToInt()) } }
            is EditEvent.PositionY -> inspect("位置 Y", "pos-y") { t, id -> props.transform(t, id, playheadIn(t, id)) { it.copy(positionY = event.value.roundToInt()) } }
            is EditEvent.Rotation -> inspect("旋转", "rotation") { t, id -> props.transform(t, id, playheadIn(t, id)) { it.copy(rotationDegrees = event.value.roundToInt()) } }
            is EditEvent.Opacity -> inspect("不透明度", "opacity") { t, id -> props.transform(t, id, playheadIn(t, id)) { it.copy(opacityPercent = event.value.roundToInt()) } }
            is EditEvent.Composite -> inspect("合成模式：${event.mode.label}", null) { t, id -> props.composite(t, id, event.mode) }
            is EditEvent.ChromaKeyOn -> {
                inspect(if (event.on) "开启抠像" else "关闭抠像", null) { t, id -> props.chromaKey(t, id) { if (event.on) it ?: ChromaKey() else null } }
                if (!event.on) endKeyViews()
            }
            is EditEvent.ChromaKeyScreen -> inspect("抠像：${event.screen.label}", null) { t, id ->
                props.chromaKey(t, id) { (it ?: ChromaKey()).copy(color = event.screen.color) }
            }
            is EditEvent.ChromaKeyAmount -> inspect("抠像：${event.control.label}", "chroma-key-${event.control.name}") { t, id ->
                props.chromaKey(t, id) { event.control.write(it ?: throw EditRejected("先开启抠像"), event.value) }
            }
            is EditEvent.ChromaKeyEyedropper -> keyEyedropper(event.active)
            is EditEvent.PickKeyColor -> pickKeyColor(event.u, event.v)
            is EditEvent.ChromaKeyMatte -> keyMatte(event.on)
            is EditEvent.Retime -> inspect("变速处理：${event.process.label}", null) { t, id -> props.retime(t, id, event.process) }
            is EditEvent.Scaling -> inspect("缩放方式：${event.scaling?.label ?: "按项目"}", null) { t, id -> props.scaling(t, id, event.scaling) }
            is EditEvent.Crop -> inspect("裁切${event.control.label}", "crop-${event.control.name}") { t, id ->
                props.transform(t, id, playheadIn(t, id)) { event.control.write(it, event.value.roundToInt()) }
            }
            is EditEvent.MaskShape -> maskEdit(event.shape?.let { "遮罩形状：${it.label}" } ?: "移除遮罩") { t, id -> masks.shape(t, id, event.shape) }
            is EditEvent.MaskInvert -> maskEdit("反转遮罩") { t, id -> masks.invert(t, id, event.on) }
            is EditEvent.MaskControl -> maskGeometry("遮罩${event.control.label}", "mask-${event.control.name}") { event.control.write(it, event.value) }
            is EditEvent.MoveMask -> maskGeometry("移动遮罩", "mask-move") {
                it.copy(centerX = (it.centerX + event.dx).coerceIn(-1f, 1f), centerY = (it.centerY + event.dy).coerceIn(-1f, 1f))
            }
            EditEvent.ToggleMaskKey -> session.selectedClipId.value?.let { id ->
                apply("遮罩关键帧") { d -> masks.toggleKey(d.timeline, id, playheadIn(d.timeline, id)) }
            }
            EditEvent.TrackMask -> trackMask()
            EditEvent.CancelMaskTrack -> maskTrackJob?.cancel()
            is EditEvent.SetMaskTrackMotion -> {
                maskTrackMotion = event.motion
                refreshInspector()
            }
            EditEvent.AlignByWaveform -> alignByWaveform()
            is EditEvent.DetectSceneCuts -> detectSceneCuts(event.clipId)
            EditEvent.CreateCompound -> createCompound()
            EditEvent.CreateMulticam -> createMulticam()
            is EditEvent.SwitchAngle -> switchAngle(event.index)
            is EditEvent.MulticamMode -> {
                multicamMode = event.mode.coerceIn(0, MulticamUseCase.SwitchMode.entries.lastIndex)
                publish()
            }
            is EditEvent.MulticamWhole -> {
                multicamWhole = event.on
                publish()
            }
            is EditEvent.OpenCompound -> openCompound(event.clipId)
            EditEvent.LeaveCompound -> leaveCompound()
            is EditEvent.DecomposeCompound -> decomposeCompound(event.clipId)
            is EditEvent.StabilizeOn -> inspect(if (event.on) "开启稳定" else "关闭稳定", null) { t, id ->
                // Off keeps the settings for switching back on.
                val current = t.clip(id)?.stabilization
                val next = if (event.on) (current ?: ClipStabilization.DEFAULT).copy(enabled = true) else current?.copy(enabled = false)
                props.stabilization(t, id, next, session.document.value?.asset(t.clip(id)?.assetId))
            }
            is EditEvent.StabilizeSmooth -> stabilize("稳定平滑度", "stab-smooth") { it.copy(smooth = event.value.roundToInt()) }
            is EditEvent.StabilizeStrength -> stabilize("稳定强度", "stab-strength") { it.copy(strength = event.value.roundToInt()) }
            is EditEvent.StabilizeZoom -> stabilize(if (event.on) "稳定：放大遮边" else "稳定：不放大", null) { it.copy(zoom = event.on) }
            EditEvent.RetryStabilize -> {
                val doc = session.document.value
                val clip = session.selectedClipId.value?.let { doc?.timeline?.clip(it) }
                doc?.asset(clip?.assetId)?.let { stabilization.request(listOf(it), retryFailed = true) }
            }
            is EditEvent.DynamicZoomOn -> inspect(if (event.on) "开启动态缩放" else "关闭动态缩放", null) { t, id ->
                // Switching it off remembers the settings, so switching it back on restores them.
                t.clip(id)?.dynamicZoom?.let { lastZooms[id] = it }
                props.dynamicZoom(t, id, if (event.on) lastZooms[id] ?: DynamicZoom() else null)
            }
            is EditEvent.DynamicZoomDirection -> zoom("动态缩放方向", null) {
                // A pan needs a side to head for: from a centred focus it goes left to right.
                val changed = it.copy(direction = event.direction)
                if (changed.moves) changed else changed.copy(focusX = ZoomFocusX.RIGHT)
            }
            is EditEvent.DynamicZoomAmount -> zoom("动态缩放幅度", "zoom-amount") { it.copy(amountPercent = event.percent.roundToInt()) }
            is EditEvent.DynamicZoomEase -> zoom("动态缩放缓动", null) { it.copy(ease = event.ease) }
            is EditEvent.DynamicZoomFocusX -> zoom("动态缩放焦点", null) { it.copy(focusX = event.focus) }
            is EditEvent.DynamicZoomFocusY -> zoom("动态缩放焦点", null) { it.copy(focusY = event.focus) }
            EditEvent.ResetCrop -> inspect("重置裁切", null) { t, id ->
                props.transform(t, id, playheadIn(t, id)) { c -> CropControl.entries.fold(c) { acc, control -> control.write(acc, 0) } }
            }
            EditEvent.ToggleKeyframe -> session.selectedClipId.value?.let { id ->
                apply("关键帧") { d -> keyframes.toggle(d.timeline, id, playheadIn(d.timeline, id)) }
            }
            is EditEvent.ToggleParamKey -> session.selectedClipId.value?.let { id ->
                apply("${event.group.label}关键帧") { d -> keyframes.toggleGroup(d.timeline, id, playheadIn(d.timeline, id), event.group) }
            }
            EditEvent.PreviousKeyframe -> jumpToKeyframe(next = false)
            EditEvent.NextKeyframe -> jumpToKeyframe(next = true)
            is EditEvent.ToggleEase -> session.selectedClipId.value?.let { id ->
                apply(if (event.arriving) "缓入" else "缓出") { d -> keyframes.ease(d.timeline, id, playheadIn(d.timeline, id), event.arriving) }
            }
            is EditEvent.Volume -> inspectAudio("音量", "volume") { t, id -> props.volume(t, id, event.db, playheadIn(t, id)) }
            EditEvent.ToggleVolumeKeyframe -> toggleVolumeKeyframe()
            is EditEvent.Pan -> inspectAudio("声像", "pan") { t, id -> props.pan(t, id, event.pan, playheadIn(t, id)) }
            EditEvent.TogglePanKey -> togglePanKeyframe()
            is EditEvent.ToggleVolumeEase -> toggleVolumeEase(event.arriving)
            is EditEvent.Nudge -> session.selectedClipId.value?.let { id ->
                val ids = selection
                apply("微移片段") { d ->
                    val clip = d.timeline.clip(id) ?: throw EditRejected("请先在时间线上选择片段")
                    if (ids.size > 1) {
                        move.group(d.timeline, ids, id, clip.start + event.frames, 0, false, _state.value.playheadFrame, d.format.fps)
                    } else {
                        move(d.timeline, id, clip.start + event.frames, clip.trackId, 0, false, _state.value.playheadFrame, d.format.fps)
                    }
                }
            }
            is EditEvent.JogMode -> {
                _state.update { it.copy(jogMode = event.index.coerceIn(0, EditUiState.JOG_MODES.lastIndex)) }
                publish()
            }
            is EditEvent.TrimJog -> when (_state.value.jogMode) {
                EditUiState.JOG_SLIP -> trimJog("滑移", "slip") { d, id -> trims.slip(d, id, event.frames) }
                EditUiState.JOG_ROLL -> trimJog("滚动剪辑点", "roll") { d, id -> trims.roll(d, id, event.frames) }
                EditUiState.JOG_SLIDE -> trimJog("滑动片段", "slide") { d, id -> trims.slide(d, id, event.frames) }
                else -> Unit
            }
            EditEvent.PreviousVolumeKeyframe -> jumpToVolumeKeyframe(next = false)
            EditEvent.NextVolumeKeyframe -> jumpToVolumeKeyframe(next = true)
            is EditEvent.FadeIn -> inspectAudio("淡入", "fade-in") { t, id -> props.fades(t, id, doc?.format?.fps ?: 30, fadeIn = event.seconds) }
            is EditEvent.FadeOut -> inspectAudio("淡出", "fade-out") { t, id -> props.fades(t, id, doc?.format?.fps ?: 30, fadeOut = event.seconds) }
            is EditEvent.FadeInCurve -> inspectAudio("淡入曲线", "fade-in-curve") { t, id -> props.fadeCurves(t, id, fadeIn = event.curve) }
            is EditEvent.FadeOutCurve -> inspectAudio("淡出曲线", "fade-out-curve") { t, id -> props.fadeCurves(t, id, fadeOut = event.curve) }
            EditEvent.OpenClipEq -> {
                val document = doc ?: return
                val target = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return toast("所选片段没有音频")
                eqClipId = target.id
                publish()
            }
            EditEvent.CloseClipEq -> {
                eqClipId = null
                publish()
            }
            is EditEvent.ClipEq -> clipEq("${event.control.band}${event.control.label}", "clip-eq-${event.control}") { event.control.write(it, event.value) }
            is EditEvent.ClipEqDrag -> clipEq(event.band.label, "clip-eq-drag-${event.band}") { event.band.drag(it, event.hz, event.db) }
            is EditEvent.ClipEqLowCut -> clipEq(if (event.on) "开启低切" else "关闭低切", null) { it.copy(lowCut = event.on) }
            is EditEvent.ClipEqEnabled -> clipEq(if (event.on) "启用" else "旁通", null) { it.copy(enabled = event.on) }
            is EditEvent.ClipEqPreset -> clipEq("预设 ${event.preset.label}", null) { event.preset.eq }
            EditEvent.ResetClipEq -> clipEq("重置", null) { TrackEq.FLAT }
            is EditEvent.PictureFadeIn -> inspect("画面淡入", "picture-fade-in") { t, id -> pictureFades(t, id, fadeIn = event.seconds) }
            is EditEvent.PictureFadeOut -> inspect("画面淡出", "picture-fade-out") { t, id -> pictureFades(t, id, fadeOut = event.seconds) }
            is EditEvent.PictureFadeInCurve -> inspect("画面淡入曲线", "picture-fade-in-curve") { t, id -> pictureFadeCurves(t, id, fadeIn = event.curve) }
            is EditEvent.PictureFadeOutCurve -> inspect("画面淡出曲线", "picture-fade-out-curve") { t, id -> pictureFadeCurves(t, id, fadeOut = event.curve) }
            is EditEvent.CrossFade -> inspectAudio("交叉淡化", "cross-fade") { t, id -> props.crossFade(t, id, event.frames.roundToInt(), doc?.format?.fps ?: 30) }
            is EditEvent.IndexView -> { _state.update { it.copy(indexView = event.view.coerceIn(EditUiState.INDEX_EDITS, EditUiState.INDEX_MARKERS)) }; publish() }
            is EditEvent.IndexFilter -> { _state.update { it.copy(indexFilter = event.filter) }; publish() }
            is EditEvent.PickIndexRow -> doc?.timeline?.clip(event.clipId)?.let { clip ->
                session.select(clip.id)
                seek(clip.start)
            }
            is EditEvent.Channels -> inspectAudio("声道：${event.channels.label}", "channels") { t, id -> props.channels(t, id, event.channels) }
            is EditEvent.Pitch -> inspectAudio("音调", "pitch") { t, id -> props.pitch(t, id, (event.semitones * 100f).roundToInt()) }
            is EditEvent.Speed -> changeSpeed(PlaybackSpeed.entries[event.index.coerceIn(0, PlaybackSpeed.entries.lastIndex)])
            EditEvent.AddSpeedPoint -> session.selectedClipId.value?.let { id ->
                apply("添加变速点") { d -> retime.addPoint(d, id, playheadIn(d.timeline, id)) }
            }
            EditEvent.RemoveSpeedPoint -> session.selectedClipId.value?.let { id ->
                apply("删除变速点") { d -> retime.removePoint(d, id, playheadIn(d.timeline, id)) }
            }
            EditEvent.ClearSpeedPoints -> session.selectedClipId.value?.let { id -> apply("清除变速点") { d -> retime.clear(d, id) } }
            is EditEvent.SpeedRamp -> session.selectedClipId.value?.let { id -> speedRamp(id, event.seconds) }
            is EditEvent.Reverse -> {
                inspect("倒放", null) { t, id -> props.reverse(t, id, event.reverse) }
                if (event.reverse) startReverseCopy()
            }
            is EditEvent.EffectEnabled -> inspect("切换特效", null) { t, id -> props.effect(t, id, event.effectId, enabled = event.enabled) }
            is EditEvent.EffectAmount -> inspect("特效强度", "fx-${event.effectId}") { t, id ->
                props.effect(t, id, event.effectId, amount = event.amount.roundToInt(), atFrame = playheadIn(t, id))
            }
            is EditEvent.ToggleEffectKeyframe -> session.selectedClipId.value?.let { id ->
                apply("特效关键帧") { d -> keyframes.toggleEffect(d.timeline, id, event.effectId, playheadIn(d.timeline, id)) }
            }
            is EditEvent.PreviousEffectKeyframe -> jumpToEffectKeyframe(event.effectId, next = false)
            is EditEvent.NextEffectKeyframe -> jumpToEffectKeyframe(event.effectId, next = true)
            is EditEvent.RemoveEffect -> apply("移除特效") { d -> decorate.removeEffect(d.timeline, session.selectedClipId.value, event.effectId) }
            is EditEvent.Place -> inspect("画面布局：${event.placement.label}", null) { t, id -> props.place(t, id, event.placement, playheadIn(t, id)) }
            is EditEvent.MoveTrack -> session.selectedClipId.value?.let { id ->
                val several = selection.takeIf { it.size > 1 }
                apply("移动到其他轨道") { d ->
                    if (several != null) move.groupToAdjacentTrack(d.timeline, several, id, event.up, d.format.fps)
                    else move.toAdjacentTrack(d.timeline, id, event.up, d.format.fps)
                }
            }
            is EditEvent.TransitionSeconds -> doc?.let { d ->
                val frames = (event.seconds * d.format.fps).roundToInt()
                inspect("转场时长", "transition-length") { t, id -> decorate.setTransitionFrames(t, id, frames, d.format.fps) }
            }
            EditEvent.RemoveTransition -> apply("移除转场") { d -> decorate.removeTransition(d.timeline, session.selectedClipId.value) }
            is EditEvent.TransitionKind -> apply("转场类型") { d -> decorate.changeTransitionType(d.timeline, session.selectedClipId.value, event.type) }
            is EditEvent.TransitionAlign -> styleTransition("转场对齐", null) { it.copy(alignment = event.alignment) }
            is EditEvent.TransitionEasing -> styleTransition("转场缓动", null) { it.copy(ease = event.ease) }
            is EditEvent.TransitionReverse -> styleTransition("转场方向", null) { it.copy(reverse = event.reverse) }
            is EditEvent.TransitionSoftness -> styleTransition("转场柔化", "transition-softness") { it.copy(softness = event.value) }
            is EditEvent.TransitionBorder -> styleTransition("转场描边", "transition-border") { it.copy(border = event.value) }
            is EditEvent.TransitionBorderColor -> styleTransition("描边颜色", null) { it.copy(borderColor = event.color) }
            is EditEvent.TransitionColor -> styleTransition("转场颜色", null) { it.copy(color = event.color) }
            is EditEvent.TransitionAngle -> styleTransition("转场角度", "transition-angle") { it.copy(angle = event.degrees, reverse = false) }
            is EditEvent.ProgramSurface -> program.route(event.change)
            is EditEvent.SourceSurface -> source.route(event.change)
            is EditEvent.SourcePlaying -> if (event.playing) source.play() else source.pause()
            is EditEvent.SourceJog -> {
                source.pause()
                val s = _state.value
                source.seekTo(Timecode.frameToSeekMs((s.sourcePositionFrames + event.deltaFrames).coerceAtLeast(0), s.fps))
            }
            EditEvent.ToastShown -> _state.update { it.copy(toast = null) }
        }
    }

    // ---- editing helpers ---------------------------------------------------------------

    /** Applies [edit] as one undoable step; false when it was refused (the reason is shown). */
    private fun apply(label: String, edit: (ProjectDocument) -> EditResult): Boolean {
        val document = doc ?: return false
        try {
            val result = edit(document)
            session.edit(label) { it.copy(timeline = result.timeline) }
            val several = result.selection
            if (several != null) session.selectAll(several) else session.select(result.selectedClipId)
            toast(result.message)
            return true
        } catch (e: EditRejected) {
            toast(e.message ?: "无法完成编辑")
            return false
        }
    }

    /** Resolve's Add Transition: at the selected clips' ends, else at the cut nearest the playhead. */
    private fun addTransitions(scope: TransitionScope) {
        val ids = selection
        apply("添加${scope.label}") { d ->
            val fps = d.format.fps
            if (ids.isNotEmpty()) transitions.add(d.timeline, transitions.selectionCuts(d.timeline, ids, scope), scope, fps, within = ids)
            else transitions.add(d.timeline, setOfNotNull(transitions.nearestCut(d.timeline, _state.value.playheadFrame, scope)), scope, fps)
        }
    }

    /** Resolve's Export → Timeline → EDL: the open timeline as CMX 3600 in Download/Reality Video Editor. */
    private fun exportEdl() {
        val document = doc ?: return
        val result = edl(document)
        if (result.events == 0) return toast("时间线的 V1、A1、A2 上没有可以写进 EDL 的片段")
        viewModelScope.launch {
            val saved = downloads.save("${document.timelineName}.edl", EDL_MIME, result.text.toByteArray())
            val left = if (result.left.isEmpty()) "" else "；未包含：" + result.left.joinToString("、") { it.substringBefore("（") }
            toast(saved?.let { "已导出 EDL（${result.events} 个事件）到 $it$left" } ?: "未能保存 EDL 到“下载”文件夹")
        }
    }

    /** Resolve's Export → Timeline → FCPXML: the open timeline as FCPXML 1.10 in Download/Reality Video Editor. */
    private fun exportFcpxml() {
        val document = doc ?: return
        val result = fcpxml(document)
        if (result.clips == 0) return toast("时间线上没有可以写进 FCPXML 的片段")
        viewModelScope.launch {
            val saved = downloads.save("${document.timelineName}.fcpxml", FcpxmlExportUseCase.MIME, result.text.toByteArray())
            val left = if (result.left.isEmpty()) "" else "；未包含：" + result.left.joinToString("、") { it.substringBefore("（") }
            toast(saved?.let { "已导出 FCPXML（${result.clips} 个片段）到 $it$left" } ?: "未能保存 FCPXML 到“下载”文件夹")
        }
    }

    /** Resolve's Import → Timeline → EDL: the picked file's events as a new timeline, opened (one undo step). */
    private fun importEdl(uri: String) {
        viewModelScope.launch {
            val file = try {
                documents.read(uri, MAX_SUBTITLE_BYTES)
            } catch (e: DocumentFailure) {
                return@launch toast(e.message ?: "无法读取文件")
            }
            val text = file.bytes.decodeToString().removePrefix("\uFEFF")
            var message = ""
            try {
                session.edit("导入 EDL") { d -> edlImport(d, text, file.name).also { message = it.message }.doc }
            } catch (e: EditRejected) {
                return@launch toast(e.message ?: "无法导入 EDL")
            }
            session.select(null)
            seek(0)
            toast(message)
        }
    }

    /** Resolve's Import → Timeline → FCPXML: the picked file's first sequence as a new timeline, opened (one undo step). */
    private fun importFcpxml(uri: String) {
        viewModelScope.launch {
            val file = try {
                documents.read(uri, MAX_FCPXML_BYTES)
            } catch (e: DocumentFailure) {
                return@launch toast(e.message ?: "无法读取文件")
            }
            val text = file.bytes.decodeToString().removePrefix("\uFEFF")
            var message = ""
            try {
                session.edit("导入 FCPXML") { d -> fcpxmlImport(d, text, file.name).also { message = it.message }.doc }
            } catch (e: EditRejected) {
                return@launch toast(e.message ?: "无法导入 FCPXML")
            }
            session.select(null)
            seek(0)
            toast(message)
        }
    }

    /** The picked gap while nothing is selected and it is still a gap of the timeline shown. */
    private fun liveGap(timeline: Timeline? = preview ?: doc?.timeline): TimelineGap? {
        val g = gap ?: return null
        if (selection.isNotEmpty() || timeline == null) return null
        return g.takeIf { gaps.gapAt(timeline, it.trackId, it.start) == it }
    }

    private fun deleteGap() {
        val g = liveGap() ?: return toast("请先点选时间线上的空隙")
        if (apply("删除空隙") { d -> gaps.close(d.timeline, g, d.format.fps) }) gap = null
    }

    /** The timecode sheet for [text]: what it would do, or why it cannot. */
    private fun timecodeUi(text: String): TimecodeEntryUi {
        val fps = doc?.format?.fps ?: 30
        val at = _state.value.playheadFrame
        val tc = { f: Int -> Timecode.format(f, fps) }
        val base = TimecodeEntryUi(text, tc(at))
        val stripped = text.trim().trimStart('+', '-', '＋', '－')
        if (stripped.isEmpty()) return base.copy(reading = "", problem = null)
        val entry = TimecodeEntry.parse(text, fps)
            ?: return base.copy(problem = "数字从右往左按帧、秒、分、时填，如 15、1:00、+15、-1:00:00")
        return when {
            entry.relative && selection.isNotEmpty() -> base.copy(
                reading = "所选 ${selection.size} 个片段${if (entry.sign > 0) "右移" else "左移"} ${tc(entry.frames)}",
                confirmLabel = "移动",
            )
            entry.relative -> base.copy(reading = "播放头${if (entry.sign > 0) "前进" else "后退"} ${tc(entry.frames)}，到 ${tc(entry.target(at))}")
            else -> base.copy(reading = "播放头跳到 ${tc(entry.target(at))}")
        }
    }

    /** [ui] with the typed length read: its frames and how it reads, or what is wrong with it. */
    private fun durationUi(ui: ClipDurationUi): ClipDurationUi {
        val fps = doc?.format?.fps ?: 30
        val frames = ClipDurationUseCase.parse(ui.text, fps)
        return ui.copy(
            frames = frames,
            reading = frames?.let { "${Timecode.format(it, fps)} · ${formatSeconds(it, fps)}，输入时间码（时:分:秒:帧）或秒数" } ?: "",
            problem = when {
                frames == null -> "输入时间码（如 00:00:03:00 或 3:00）或秒数（如 2.5）"
                frames < TrimClipUseCase.MIN_FRAMES -> "片段至少 ${TrimClipUseCase.MIN_FRAMES} 帧"
                else -> null
            },
        )
    }

    /** [ui] with its hint and the problem with its name worked out. */
    private fun renameUi(ui: RenameUi): RenameUi {
        val what = if (ui.clipId != null) "片段" else "轨道"
        val max = if (ui.clipId != null) RenameUseCase.MAX_CLIP else RenameUseCase.MAX_TRACK
        val taken = ui.trackId != null && doc?.timeline?.tracks?.any { it.id != ui.trackId && it.name == ui.text.trim() } == true
        return ui.copy(
            hint = if (ui.clipId != null) "最多 $max 个字，时间线上的片段标签显示这个名称" else "最多 $max 个字，不能与其他轨道同名",
            problem = rename.problem(ui.text, what) ?: if (taken) "已有名为“${ui.text.trim()}”的轨道" else null,
        )
    }

    /** The project's stills: they take no speed from Paste Attributes. */
    private fun stillIds(d: ProjectDocument): Set<String> = d.media.filter { it.kind == MediaKind.IMAGE }.map { it.id }.toSet()

    private fun trimToPlayheadLabel(event: EditEvent.TrimToPlayhead): String =
        (if (event.ripple) "波纹修剪" else "修剪") + (if (event.edge == TrimEdge.START) "开头" else "结尾") + "到播放头"

    private fun copyClip() {
        val ids = selection.ifEmpty { return toast("请先在时间线上选择片段") }
        val document = doc ?: return
        val content = try {
            clipboardEdits.copy(document.timeline, ids)
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法复制")
        }
        keep(content)
        val name = content.clips.first().name
        toast(
            when {
                content.groups > 1 -> "已复制 ${content.groups} 个片段"
                content.clips.size > 1 -> "已复制“$name”及链接的片段"
                else -> "已复制“$name”"
            },
        )
    }

    /** Selects [clips] — each linked picture and sound once, by its picture — in timeline order. */
    private fun selectGroups(clips: List<Clip>) {
        val heads = clips.sortedWith(compareBy({ it.start }, { it.kind == ClipKind.AUDIO })).distinctBy { it.linkId ?: it.id }
        session.selectAll(heads.map { it.id })
        if (heads.size > 1) toast("已选择 ${heads.size} 个片段")
    }

    /** Captures the frame at the playhead, then holds it in the selected clip (one undoable step). */
    private fun freezeFrame() {
        val document = doc ?: return
        val at = _state.value.playheadFrame
        val request = try {
            freezes.request(document, session.selectedClipId.value, at)
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法定格")
        }
        // Decoding one exact frame can take a few seconds.
        toast("正在生成定格帧…")
        viewModelScope.launch {
            val still = stills.capture(request.asset, request.sourceMs, request.stillId, request.stillName)
                ?: return@launch toast("无法读取这一帧")
            val current = doc ?: return@launch
            val fps = current.format.fps
            val result = try {
                freezes.insert(current, request, still, at, FreezeFrameUseCase.HOLD_SECONDS * fps)
            } catch (e: EditRejected) {
                return@launch toast(e.message ?: "无法定格")
            }
            session.edit("定格帧") { result.doc }
            session.select(result.selectedClipId)
            toast(result.message)
        }
    }

    /** Opens the paste-attributes sheet for the copied clip and the selected one, when they share something. */
    private fun openPasteAttributes() {
        val content = clipboard ?: return toast("剪贴板是空的：先复制一个片段")
        val document = doc ?: return
        val id = session.selectedClipId.value ?: return toast("请先在时间线上选择要粘贴到的片段")
        val ids = selection
        val pairing = try {
            pasteAttributes.pairing(document.timeline, content, id, stillIds(document))
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法粘贴属性")
        }
        val available = if (ids.size > 1) pasteAttributes.available(document.timeline, content, ids, stillIds(document)) else pasteAttributes.available(pairing)
        if (available.isEmpty()) return toast("复制的片段没有可以粘贴到所选片段的属性")
        val source = pairing.sourcePicture ?: pairing.sourceSound ?: return
        val groups = document.timeline.selectedGroups(ids).size
        val target = document.timeline.clip(id)?.let { if (groups > 1) it.copy(name = "$groups 个片段") else it } ?: return
        _state.update {
            it.copy(
                pasteAttributes = PasteAttributesUi(
                    sourceName = source.name,
                    targetName = target.name,
                    options = ClipAttribute.entries.map { a -> AttributeOptionUi(a, a in available, a in attributeChoice) },
                ),
            )
        }
    }

    private fun keep(content: ClipboardUseCase.Content) {
        clipboard = content
        _state.update { it.copy(canPaste = true) }
    }

    /** Reads a picked .srt, .vtt or .ass file and puts its cues on the subtitle track (one undoable edit). */
    private fun importSubtitles(uri: String) {
        viewModelScope.launch {
            val file = try {
                documents.read(uri, MAX_SUBTITLE_BYTES)
            } catch (e: DocumentFailure) {
                return@launch toast(e.message ?: "无法读取文件")
            }
            val cues = subtitleFiles.parse(subtitleFiles.decode(file.bytes))
            apply("导入字幕") { d -> subtitleFiles.import(d.timeline, cues, d.format.fps, file.name) }
            _state.update { it.copy(inspectorTab = EditUiState.TAB_VIDEO) }
        }
    }

    /** Resolve's flags on the selected clips' media: one undo step, the use case's message as the toast. */
    private fun flag(label: String, change: (ProjectDocument) -> FlagUseCase.Result) {
        if (selection.isEmpty()) return toast("请先在时间线上选择片段")
        var message = ""
        try {
            session.edit(label) { d -> change(d).also { message = it.message }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法标记旗标")
        }
        toast(message)
    }

    /** `timeline-settings-sheet` showing [name], [formatIndex] (a [FormatPreset]) and [scaling] against [document]'s settings. */
    private fun timelineSettingsUi(document: ProjectDocument, formatIndex: Int, scaling: ClipScaling, name: String): TimelineSettingsUi {
        val preset = FormatPreset.entries[formatIndex.coerceIn(0, FormatPreset.entries.lastIndex)]
        return TimelineSettingsUi(
            name = name,
            formats = FormatPreset.entries.map { it.label },
            formatIndex = preset.ordinal,
            fpsLabel = "${document.format.fps} fps",
            scaling = scaling,
            note = timelineSettings.followers(document, preset, scaling),
            changed = !preset.matches(document.format) || scaling != document.format.scaling || I18n.stored(name, document.timelineName).trim() != document.timelineName,
        )
    }

    /**
     * Opens, makes, duplicates or deletes a timeline ([change]) as one undoable step: the selection belonged to the
     * timeline that was open, and the playhead stays where it was as far as the timeline now open reaches.
     */
    private fun switchTimeline(label: String, change: (ProjectDocument) -> TimelinesUseCase.Result) {
        var message = ""
        try {
            session.edit(label) { d -> change(d).also { message = it.message }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法切换时间线")
        }
        session.select(null)
        seek(_state.value.playheadFrame)
        toast(message)
    }

    private fun editTimelineSettings(change: (ProjectDocument, TimelineSettingsUi) -> TimelineSettingsUi) {
        val document = doc ?: return
        _state.update { s -> s.copy(timelineSettings = s.timelineSettings?.let { change(document, it) }) }
    }

    /** The sheet's raster and scaling onto the project, one undoable step; the sheet closes. */
    private fun applyTimelineSettings() {
        val ui = _state.value.timelineSettings ?: return
        var message = ""
        try {
            session.edit("时间线设置") { d ->
                timelineSettings(d, FormatPreset.entries[ui.formatIndex], ui.scaling, I18n.stored(ui.name, d.timelineName)).also { message = it.message }.doc
            }
        } catch (e: EditRejected) {
            message = e.message ?: "无法修改时间线设置"
        }
        _state.update { it.copy(timelineSettings = null) }
        toast(message)
    }

    /** Adds or deletes a track; a selection whose clip went with a deleted track is dropped. */
    private fun changeTracks(label: String, change: (ProjectDocument) -> TracksUseCase.Result) {
        var message = ""
        try {
            session.edit(label) { d -> change(d).also { message = it.message }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法修改轨道")
        }
        val selected = session.selectedClipId.value
        if (selected != null && doc?.timeline?.clip(selected) == null) session.select(null)
        toast(message)
    }

    private fun editTracks(label: String, change: (Timeline) -> Timeline) {
        session.edit(label) { it.copy(timeline = change(it.timeline)) }
    }

    private fun inspect(label: String, key: String?, change: (Timeline, String) -> Timeline) {
        val id = session.selectedClipId.value ?: return
        try {
            session.edit(label, key?.let { "$it:$id" }) { it.copy(timeline = change(it.timeline, id)) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改")
        }
    }

    /** A mask edit of the selected clip that may change nothing (then no undo step and no message). */
    private fun maskEdit(label: String, edit: (Timeline, String) -> EditResult?) {
        val id = session.selectedClipId.value ?: return
        val document = doc ?: return
        val result = try {
            edit(document.timeline, id)
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法修改遮罩")
        } ?: return
        apply(label) { result }
    }

    /** Moves or reshapes the selected clip's mask; a keyed mask changes at the playhead, its edits there one step per frame. */
    private fun maskGeometry(label: String, key: String, change: (PowerWindow) -> PowerWindow) {
        val id = session.selectedClipId.value ?: return
        val clip = doc?.timeline?.clip(id) ?: return
        val keyed = clip.mask?.keyframes?.isNotEmpty() == true
        val at = if (keyed) "@${_state.value.playheadFrame - clip.start}" else ""
        inspect(label, key + at) { t, clipId -> masks.adjust(t, clipId, playheadIn(t, clipId), change) }
    }

    /** Restyles the selected clip's opening transition (Resolve's transition inspector). */
    private fun styleTransition(label: String, key: String?, change: (Transition) -> Transition) =
        inspect(label, key) { t, id -> decorate.styleTransition(t, id, change) }

    /**
     * Resolve's Auto Align Clips (Based on Waveform): reads the sound of the selected clips' files, then moves every other
     * selected clip (with its linked partners) into step with the one tapped last (the selection's lead) — one undo step, "按波形对齐". Clips that
     * cannot be matched stay and are named in the toast.
     */
    private fun alignByWaveform() {
        if (aligning) return
        val document = doc ?: return
        val groups = document.timeline.selectedGroups(selection)
        if (groups.size < 2) return toast("选择至少两个片段：最后点选的片段作为参照")
        val assets = document.media.associateBy { it.id }
        val ids = selection
        aligning = true
        publish()
        viewModelScope.launch {
            try {
                val wanted = waveAlign.wanted(groups, assets)
                val levels = wanted.mapNotNull { a -> envelopes.envelope(a)?.let { a.uri to it } }.toMap()
                var result: WaveformAlignUseCase.Result? = null
                session.edit("按波形对齐", null) { d ->
                    val fresh = d.timeline.selectedGroups(ids)
                    val r = waveAlign.align(d.timeline, fresh, d.media.associateBy { it.id }, levels, d.format.fps)
                    result = r
                    d.copy(timeline = r.timeline)
                }
                result?.let { r ->
                    val moved = r.moved.joinToString("，") { m -> "「${m.name}」${if (m.deltaMs >= 0) "后移" else "前移"} ${"%.3f".format(abs(m.deltaMs) / 1000.0)} 秒" }
                    toast("已按波形对齐：$moved" + if (r.skipped.isEmpty()) "" else "；" + r.skipped.joinToString("；"))
                }
            } catch (e: EditRejected) {
                toast(e.message ?: "无法对齐")
            } finally {
                aligning = false
                publish()
            }
        }
    }

    /**
     * Resolve's Detect Scene Cuts: reads the frames [clipId] shows (small, scored as they come), then cuts the clip and its
     * linked sound where its shots change — one undo step ("检测场景剪切"). A clip that is one shot stays as it is.
     */
    /** Resolve's New Compound Clip: the selected clips (and their linked sound) as one clip in their place — one undo step. */
    private fun createCompound() {
        val chosen = selection.ifEmpty { listOfNotNull(session.selectedClipId.value) }
        var result: CompoundClipUseCase.Result? = null
        try {
            session.edit("新建复合片段") { d -> compounds.create(d, chosen).also { result = it }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法新建复合片段")
        }
        session.select(result?.selectedClipId)
        result?.message?.let(::toast)
    }

    private fun createMulticam() {
        val chosen = selection.ifEmpty { listOfNotNull(session.selectedClipId.value) }
        var result: MulticamUseCase.Result? = null
        try {
            session.edit("新建多机位片段") { d -> multicams.create(d, chosen).also { result = it }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法新建多机位片段")
        }
        session.select(result?.selectedClipId)
        result?.message?.let(::toast)
    }

    /**
     * Angle [index] of the selected multicam clip at the playhead: cut to it there, or the whole segment under the playhead
     * switched, picture and sound or either alone — `multicam-panel`'s settings. One undo step each.
     */
    private fun switchAngle(index: Int) {
        val document = doc ?: return
        val selected = session.selectedClipId.value?.let(document.timeline::clip)
        if (!multicams.isMulticam(document, selected)) return toast("先在时间线上选择一个多机位片段")
        val frame = _state.value.playheadFrame
        val part = multicams.partAt(document.timeline, selected!!, frame)
        var result: MulticamUseCase.Result? = null
        try {
            session.edit("切换机位") { d ->
                multicams.switch(d, part.id, index, frame, MulticamUseCase.SwitchMode.entries[multicamMode], cut = !multicamWhole).also { result = it }.doc
            }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法切换机位")
        }
        session.select(result?.selectedClipId)
        result?.message?.let(::toast)
    }

    /** `multicam-panel` for [clip], or null when it is not a multicam clip's part; asks for the angles' frames it lacks. */
    private fun multicamUi(document: ProjectDocument, clip: Clip): MulticamUi? {
        if (!multicams.isMulticam(document, clip)) return null
        val stored = document.storedTimeline(clip.compoundId) ?: return null
        val frame = _state.value.playheadFrame
        val angles = multicams.angles(document, clip)
        val (picture, sound) = multicams.playing(document, clip, frame)
        val sources = multicams.sources(document, clip, frame)
        val inside = frame >= clip.start && frame < clip.end
        val tiles = angles.map { a ->
            val src = sources.getOrNull(a.index)
            val asset = src?.let { document.asset(it.assetId) }
            val key = asset?.let { "${it.id}@${src.sourceMs / ANGLE_FRAME_MS}" }
            if (asset != null && key != null && asset.kind != MediaKind.AUDIO && !angleFrames.containsKey(key) && requested.add("angle:$key")) {
                viewModelScope.launch {
                    previews.thumbnail(asset.uri, asset.kind, src.sourceMs / ANGLE_FRAME_MS * ANGLE_FRAME_MS, ANGLE_FRAME_WIDTH)?.let {
                        angleFrames[key] = it
                        publish()
                    }
                }
            }
            AngleUi(a.index, a.name, picture == a.index, sound == a.index, a.hasPicture, a.hasSound, key?.let(angleFrames::get))
        }
        val mode = MulticamUseCase.SwitchMode.entries[multicamMode]
        val playingText = listOfNotNull(
            picture?.let { "画面：机位 ${it + 1}" },
            sound?.let { "声音：机位 ${it + 1}" },
        ).joinToString(" · ")
        val note = when {
            !inside -> "播放头不在这个多机位片段上：点按机位会切换整段（${mode.label}）"
            multicamWhole -> "点按机位：播放头所在的整段切到该机位（${mode.label}）"
            else -> "点按机位：在播放头处剪切，之后切到该机位（${mode.label}）；播放时也可以点按"
        }
        return MulticamUi(stored.name, "${angles.size} 个机位 · $playingText", tiles, multicamMode, multicamWhole, note)
    }

    /** Each compound clip opened: the playhead outside it and the frame it opened on inside (for the way back). */
    private val compoundReturns = ArrayDeque<Pair<Int, Int>>()

    /** Compound clip [clipId]'s timeline opened, the playhead on the frame it showed (one undo step, like any switch). */
    private fun openCompound(clipId: String) {
        val document = doc ?: return
        val clip = document.timeline.clip(clipId) ?: return
        val fps = document.format.fps.coerceAtLeast(1)
        val outside = _state.value.playheadFrame
        val inside = (outside - clip.start).coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0)) +
            (clip.sourceInMs * fps / 1000.0).roundToInt()
        // The way back is kept per level; one left by the timeline menu meanwhile is forgotten.
        if (compoundReturns.size != document.compoundTrail.size) compoundReturns.clear()
        switchTimeline("打开复合片段") { d -> compounds.open(d, clipId).let { TimelinesUseCase.Result(it.doc, it.message) } }
        if (doc?.timelineId == document.timelineId) return
        compoundReturns.addLast(outside to inside)
        seek(inside)
    }

    /** Back to the timeline the compound clip was opened from, the playhead moved as far as it moved inside. */
    private fun leaveCompound() {
        val document = doc ?: return
        val now = _state.value.playheadFrame
        val known = compoundReturns.size == document.compoundTrail.size
        switchTimeline("返回上一级") { d -> compounds.back(d).let { TimelinesUseCase.Result(it.doc, it.message) } }
        if (doc?.timelineId == document.timelineId) return
        val (outside, inside) = (if (known) compoundReturns.removeLastOrNull() else null) ?: return compoundReturns.clear()
        seek((outside + now - inside).coerceAtLeast(0))
    }

    /** Resolve's Decompose in Place: the compound clip's contents back on the timeline in its place — one undo step. */
    private fun decomposeCompound(clipId: String) {
        var result: CompoundClipUseCase.Result? = null
        try {
            session.edit("分解复合片段") { d -> compounds.decompose(d, clipId).also { result = it }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法分解复合片段")
        }
        session.select(result?.selectedClipId)
        result?.message?.let(::toast)
    }

    /** A mask tracking run (null when none) and what it says it is doing. */
    private var maskTrackJob: Job? = null
    private var maskTrackStatus: String? = null

    /** What the mask tracker follows besides the position (Resolve's Zoom / Rotate; both on, as Resolve's are). */
    private var maskTrackMotion = TrackMotion.ALL

    private fun setMaskTrackStatus(status: String?) {
        maskTrackStatus = status
        refreshInspector()
    }

    /**
     * Resolve's Tracker on the selected picture's mask: reads the frames the clip shows, follows the picture under the mask
     * from the playhead to both ends and keys the mask along the path — one undo step ("跟踪遮罩"), its keyframes replaced
     * (size, aspect, angle and softness kept). A cancelled run changes nothing.
     */
    private fun trackMask() {
        if (maskTrackJob?.isActive == true) return
        val document = doc ?: return
        val clip = document.timeline.clip(session.selectedClipId.value) ?: return toast("请先在时间线上选择片段")
        val asset = document.asset(clip.assetId)
        tracker.maskUnavailable(clip, asset)?.let { return toast(it) }
        val mask = clip.mask ?: return
        val fps = document.format.fps
        val start = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
        val motion = maskTrackMotion
        maskTrackJob = viewModelScope.launch {
            setMaskTrackStatus("正在读取画面 0%")
            try {
                val frames = lumaFrames.read(asset!!, clip.sourceInMs, clip.sourceInMs + clip.sourceDurationMs(fps), WindowTrackUseCase.WIDTH) { p ->
                    setMaskTrackStatus("正在读取画面 ${(p * 100).roundToInt()}%")
                }
                val keys = withContext(compute) {
                    tracker.keyframes(mask, clip, start, frames, fps, motion) { p ->
                        viewModelScope.launch { setMaskTrackStatus("正在跟踪 ${(p * 100).roundToInt()}%") }
                    }
                }
                session.edit("跟踪遮罩", null) { d ->
                    val current = d.timeline.clip(clip.id) ?: return@edit d
                    val m = current.mask ?: return@edit d
                    d.copy(timeline = d.timeline.replaceClip(current.copy(mask = m.copy(keyframes = keys))))
                }
                val moved = (tracker.travel(keys) * 100).roundToInt()
                toast("已跟踪遮罩：${keys.size} 个关键帧，最多移动画面的 $moved%${tracker.motionNote(keys, motion)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Mask tracking failed", e)
                toast("无法跟踪：读取画面时出错")
            } finally {
                setMaskTrackStatus(null)
            }
        }
    }

    private fun detectSceneCuts(clipId: String) {
        if (_state.value.sceneCutting) return
        val document = doc ?: return
        val clip = document.timeline.clip(clipId) ?: return
        val asset = document.asset(clip.assetId)
        sceneCuts.unavailable(clip, asset)?.let { return toast(it) }
        val fps = document.format.fps
        if (clip.durationFrames > SceneCutUseCase.MAX_FRAMES) return toast("片段太长：先切短再检测")
        _state.update { it.copy(sceneCutting = true) }
        toast("正在检测「${clip.name}」的场景剪切…")
        viewModelScope.launch {
            try {
                var detector: SceneCutUseCase.Detector? = null
                val stream = lumaFrames.stream(asset!!, clip.sourceInMs, clip.sourceInMs + clip.sourceDurationMs(fps), SceneCutUseCase.WIDTH) { frame ->
                    // The first frame tells the picture's size (90 high for 16:9, more for portrait).
                    val d = detector ?: SceneCutUseCase.Detector(SceneCutUseCase.WIDTH, frame.size / SceneCutUseCase.WIDTH).also { detector = it }
                    d.add(frame)
                }
                val d = detector ?: throw IllegalStateException("no frames")
                val frames = LumaFrames(stream.width, stream.height, stream.frameRate, stream.first, emptyList())
                val at = sceneCuts.timelineFrames(clip, frames, sceneCuts.cuts(d.scores(), d.flashes()), fps)
                var message = ""
                session.edit("检测场景剪切", null) { doc0 ->
                    val r = sceneCuts.split(doc0.timeline, clipId, at, split, fps)
                    message = r.message
                    doc0.copy(timeline = r.timeline)
                }
                toast(message)
            } catch (e: EditRejected) {
                toast(e.message ?: "没有找到场景剪切")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Scene cut detection failed", e)
                toast("无法检测场景剪切：读取画面时出错")
            } finally {
                _state.update { it.copy(sceneCutting = false) }
            }
        }
    }

    /** Changes the selected clip's stabilization, which must be on. */
    private fun stabilize(label: String, key: String?, change: (ClipStabilization) -> ClipStabilization) = inspect(label, key) { t, id ->
        val current = t.clip(id)?.stabilization?.takeIf { it.enabled } ?: throw EditRejected("先开启稳定")
        props.stabilization(t, id, change(current), session.document.value?.asset(t.clip(id)?.assetId))
    }

    /**
     * The `stabilize-section` of [clip] (whose file is [asset]): its settings, the analysis's progress or result in words,
     * and the camera and steadied paths across the clip for the diagram once its file is analysed.
     */
    private fun stabilizationUi(clip: Clip, asset: MediaAsset?, fps: Int): StabilizationUi {
        val unavailable = stabilizer.unavailable(clip, asset)
        val analysis = asset?.let { stabilization.analyses.value[it.uri] }
        val ui = StabilizationUi(
            unavailable = unavailable,
            settings = clip.stabilization,
            status = if (unavailable == null) stabilizer.status(clip, analysis, fps) else "",
            analysing = clip.stabilization?.enabled == true && (analysis == null || analysis is CameraAnalysis.Analysing),
            failed = analysis is CameraAnalysis.Failed,
            zoomLabel = stabilizer.zoomLabel(clip, analysis, fps),
        )
        val path = (analysis as? CameraAnalysis.Ready)?.path
        if (unavailable != null || clip.stabilization?.enabled != true || path == null) return ui
        val samples = stabilizer.samples(clip, path, fps)
        // At most DIAGRAM_POINTS points across the clip.
        val n = samples.x.size
        val picks = (0 until minOf(n, DIAGRAM_POINTS)).map { if (n <= DIAGRAM_POINTS) it else it * (n - 1) / (DIAGRAM_POINTS - 1) }
        return ui.copy(
            cameraX = picks.map { samples.cameraX[it] },
            cameraY = picks.map { samples.cameraY[it] },
            steadyX = picks.map { samples.cameraX[it] + samples.x[it] },
            steadyY = picks.map { samples.cameraY[it] + samples.y[it] },
        )
    }

    /** Changes the selected clip's Dynamic Zoom, which must be on. */
    private fun zoom(label: String, key: String?, change: (DynamicZoom) -> DynamicZoom) = inspect(label, key) { t, id ->
        val current = t.clip(id)?.dynamicZoom ?: throw EditRejected("先开启动态缩放")
        props.dynamicZoom(t, id, change(current))
    }

    /** The open clip EQ sheet's sound: its equaliser changed by [change] ("片段均衡器：[what]"), one undo step per [key]'s burst. */
    private fun clipEq(what: String, key: String?, change: (TrackEq) -> TrackEq) {
        val id = eqClipId ?: return
        try {
            session.edit("片段均衡器：$what", key?.let { "$it:$id" }) { it.copy(timeline = props.eq(it.timeline, id, change)) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改均衡器")
        }
    }

    /** The picture's own fade handles (its sound keeps its own, in the audio tab). */
    private fun pictureFades(timeline: Timeline, clipId: String, fadeIn: Float? = null, fadeOut: Float? = null): Timeline {
        if (timeline.clip(clipId)?.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面")
        return props.fades(timeline, clipId, doc?.format?.fps ?: 30, fadeIn = fadeIn, fadeOut = fadeOut)
    }

    /** `key-eyedropper-button`: arms the keyer's eyedropper — the next tap on the monitor picks the screen's colour. */
    private fun keyEyedropper(active: Boolean) {
        val clip = doc?.timeline?.clip(session.selectedClipId.value)
        if (active && clip?.chromaKey == null) return toast("先开启抠像")
        if (active && clip != null && _state.value.playheadFrame !in clip) return toast("把播放头移到所选片段上，再在画面上吸取幕布颜色")
        keyPicking = active
        publish()
    }

    /**
     * `key-pick-overlay`: the colour the selected clip's own picture has at the tapped point (u, v), read from its source
     * frame at the playhead — before the grade, as the key works — becomes the key's colour. One pick ends the eyedropper.
     */
    private fun pickKeyColor(u: Float, v: Float) {
        val document = doc ?: return
        val clip = document.timeline.clip(session.selectedClipId.value) ?: return
        keyPicking = false
        publish()
        val asset = document.asset(clip.assetId)?.takeUnless { it.offline } ?: return toast("素材离线，无法吸取颜色")
        viewModelScope.launch {
            val fps = document.format.fps
            val offset = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
            val picture = previews.framePixels(asset.uri, asset.kind, clip.sourceMsAt(offset, fps), KEY_PICK_WIDTH, exact = true)
            val aspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1)
            val pixel = picture?.let { sourcePixelAt(it, u, v, aspect, document.framingOf(clip, it.width.toFloat() / it.height.coerceAtLeast(1))) }
            if (pixel == null) return@launch toast(if (picture == null) "无法读取这一帧的颜色" else "请点在画面上")
            val rgb = pixel and 0xFFFFFF
            inspect("抠像：吸取颜色", null) { t, id -> props.chromaKey(t, id) { (it ?: ChromaKey()).copy(color = rgb) } }
            toast("已按吸取的颜色 ${"#%06X".format(rgb)} 抠像")
        }
    }

    /** `key-matte-chip`: the monitor shows the selected clip's key as its matte — white stays, black keys out. */
    private fun keyMatte(on: Boolean) {
        val id = session.selectedClipId.value
        val keyed = doc?.timeline?.clip(id)?.chromaKey != null
        if (on && !keyed) toast("先开启抠像")
        keyMatteClip = if (on && keyed) id else null
        program.setKeyMatte(keyMatteClip)
        publish()
    }

    /** Ends the eyedropper and the matte view (another clip selected, or the key switched off). */
    private fun endKeyViews() {
        keyPicking = false
        if (keyMatteClip != null) {
            keyMatteClip = null
            program.setKeyMatte(null)
        }
    }

    /** What a tap on the monitor picks from while the keyer's eyedropper is on and the selected clip shows at the playhead. */
    private fun keyPickFor(clip: Clip?, playhead: Int): PickOverlayUi? {
        if (!keyPicking || clip == null || playhead !in clip) return null
        val mirrored = clip.effects.any { it.enabled && it.type == EffectType.MIRROR }
        return PickOverlayUi(clip.transformAt(playhead - clip.start), mirrored, "吸取：点击画面中幕布的颜色")
    }

    /** How the picture's own fades bend (its sound's bend apart, in the audio tab). */
    private fun pictureFadeCurves(timeline: Timeline, clipId: String, fadeIn: Float? = null, fadeOut: Float? = null): Timeline {
        if (timeline.clip(clipId)?.kind == ClipKind.AUDIO) throw EditRejected("音频片段没有画面")
        return props.fadeCurves(timeline, clipId, fadeIn = fadeIn, fadeOut = fadeOut)
    }

    private fun inspectAudio(label: String, key: String, change: (Timeline, String) -> Timeline) {
        val document = doc ?: return
        val target = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return toast("所选片段没有音频")
        try {
            session.edit(label, "$key:${target.id}") { it.copy(timeline = change(it.timeline, target.id)) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改")
        }
    }

    /**
     * `speed-ramp-row`: the point nearest the playhead smoothed over [seconds] — one undo step for a whole slider drag, the
     * value shown beside the slider rather than in a message at every step.
     */
    private fun speedRamp(id: String, seconds: Float) {
        val document = doc ?: return
        val frames = (seconds * document.format.fps).roundToInt()
        try {
            val result = retime.setRamp(document, id, playheadIn(document.timeline, id), frames)
            session.edit("平滑变速", "speed-ramp:$id") { it.copy(timeline = result.timeline) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法平滑变速")
        }
    }

    private fun changeSpeed(speed: PlaybackSpeed) {
        val id = session.selectedClipId.value ?: return
        // With speed points, the speed belongs to the section under the playhead (Resolve's section speed menu).
        if (doc?.timeline?.clip(id)?.retimed == true) {
            apply("变速 ${speed.label}") { d -> retime.setSectionSpeed(d, id, playheadIn(d.timeline, id), ClipSpeed.of(speed)) }
            return
        }
        try {
            session.edit("变速 ${speed.label}") { it.copy(timeline = props.speed(it.timeline, id, ClipSpeed.of(speed))) }
            val clip = doc?.timeline?.clip(id)
            if (clip != null) toast("播放速度 ${speed.label}，片段时长 ${Timecode.format(clip.durationFrames, doc!!.format.fps)}")
        } catch (e: EditRejected) {
            toast(e.message ?: "无法变速")
        }
    }

    private fun snapThreshold(fps: Int): Int =
        (16f / TimelineScale.dpPerSecond(zoom) * fps).roundToInt().coerceAtLeast(1)

    private fun moveClip(event: EditEvent.Move) {
        val document = doc ?: return
        val fps = document.format.fps
        val s = _state.value
        // Dragging one of several selected clips moves them all — across tracks too, by the lanes it moves.
        val group = selection.takeIf { it.size > 1 && event.clipId in it }
        try {
            val result = if (group != null) {
                move.group(document.timeline, group, event.clipId, event.start, snapThreshold(fps), s.snapping, s.playheadFrame, fps, event.trackId)
            } else {
                move(document.timeline, event.clipId, event.start, event.trackId, snapThreshold(fps), s.snapping, s.playheadFrame, fps)
            }
            if (event.commit) {
                preview = null
                if (result.timeline != document.timeline) session.edit("移动片段") { it.copy(timeline = result.timeline) }
                if (group != null) session.selectAll(listOf(event.clipId) + (group - event.clipId)) else session.select(event.clipId)
            } else {
                preview = result.timeline
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法移动")
            }
        }
        publish()
    }

    private fun trimClip(event: EditEvent.Trim) {
        val document = doc ?: return
        val fps = document.format.fps
        val s = _state.value
        val clip = document.timeline.clip(event.clipId) ?: return
        val sourceDuration = document.sourceLengthMs(clip)
        try {
            val result = trim(document.timeline, event.clipId, event.edge, event.frame, snapThreshold(fps), s.snapping, s.playheadFrame, sourceDuration, fps)
            if (event.commit) {
                preview = null
                if (result.timeline != document.timeline) session.edit("修剪片段") { it.copy(timeline = result.timeline) }
                // Trimming one of several selected clips keeps the others selected.
                if (event.clipId !in selection || selection.size < 2) session.select(event.clipId)
            } else {
                preview = result.timeline
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法修剪")
            }
        }
        publish()
    }

    /**
     * Dragging a transition's edge on the timeline (Resolve): its length follows the pointer — the timeline and the
     * inspector show it live — within the clip's limits, and it is one undo step when let go, the clip selected.
     */
    /**
     * `timeline` part fadeHandles: the selected clip's fade handle dragged, shown on the timeline as it goes and one undo
     * step when let go — a picture's opacity or a sound's level, each clip by its own handles.
     */
    private fun fadeDrag(event: EditEvent.FadeDrag) {
        val document = doc ?: return
        try {
            val result = props.fades(
                document.timeline, event.clipId, document.format.fps,
                fadeIn = if (event.fadeIn) event.seconds else null,
                fadeOut = if (event.fadeIn) null else event.seconds,
            )
            if (event.commit) {
                preview = null
                val picture = document.timeline.clip(event.clipId)?.kind != ClipKind.AUDIO
                val label = (if (picture) "画面" else "") + if (event.fadeIn) "淡入" else "淡出"
                if (result != document.timeline) session.edit(label) { it.copy(timeline = result) }
                session.select(event.clipId)
            } else {
                preview = result
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法调整淡化")
            }
        }
        publish()
    }

    /**
     * `timeline` part fadeCurveDot: the selected clip's fade bent by its dot, shown on the timeline (and heard or seen) as it
     * goes and one undo step when let go.
     */
    private fun fadeCurveDrag(event: EditEvent.FadeCurveDrag) {
        val document = doc ?: return
        try {
            val result = props.fadeCurves(
                document.timeline, event.clipId,
                fadeIn = if (event.fadeIn) event.curve else null,
                fadeOut = if (event.fadeIn) null else event.curve,
            )
            if (event.commit) {
                preview = null
                val picture = document.timeline.clip(event.clipId)?.kind != ClipKind.AUDIO
                val label = (if (picture) "画面" else "") + if (event.fadeIn) "淡入曲线" else "淡出曲线"
                if (result != document.timeline) session.edit(label) { it.copy(timeline = result) }
                session.select(event.clipId)
            } else {
                preview = result
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法调整淡化曲线")
            }
        }
        publish()
    }

    private fun transitionDrag(event: EditEvent.TransitionDrag) {
        val document = doc ?: return
        try {
            val result = decorate.setTransitionFrames(document.timeline, event.clipId, event.frames, document.format.fps)
            if (event.commit) {
                preview = null
                if (result != document.timeline) session.edit("转场时长") { it.copy(timeline = result) }
                session.select(event.clipId)
            } else {
                preview = result
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法调整转场")
            }
        }
        publish()
    }

    /**
     * Dragging a sound's volume line: shown and heard live — the program monitor mixes the dragged level while the
     * session waits — and one undo step when let go.
     */
    private fun volumeLine(event: EditEvent.VolumeLine) {
        val document = doc ?: return
        try {
            val result = props.volumeLine(document.timeline, event.clipId, event.keyFrames, event.deltaDb)
            if (event.commit) {
                preview = null
                if (result != document.timeline) session.edit(if (event.keyFrames.isEmpty()) "调整音量" else "调整音量关键帧") { it.copy(timeline = result) }
                else program.setProject(document)
            } else {
                preview = result
                program.setProject(document.copy(timeline = result))
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                program.setProject(document)
                toast(e.message ?: "无法调整音量")
            }
        }
        publish()
    }

    private fun applyLibraryItem() {
        val s = _state.value
        val itemId = s.libraryItemId ?: return
        when (s.libraryTab) {
            LibraryTab.MEDIA -> {
                if (itemId.startsWith(TIMELINE_ITEM)) return nestTimeline(itemId.removePrefix(TIMELINE_ITEM), SourceEdit.APPEND)
                val asset = doc?.asset(itemId) ?: return
                apply("添加到时间线") { d -> synced(d, append(d.timeline, asset, 0, asset.durationMs, d.format.fps)) }
            }
            LibraryTab.TITLES -> {
                val template = TitleTemplate.entries.firstOrNull { "title:${it.name}" == itemId } ?: return
                apply("添加标题") { d -> addTitle(d.timeline, template, s.playheadFrame, d.format.fps) }
                _state.update { it.copy(inspectorTab = EditUiState.TAB_VIDEO) }
            }
            LibraryTab.TRANSITIONS -> {
                val type = TransitionType.entries.firstOrNull { "transition:${it.name}" == itemId } ?: return
                apply("应用转场") { d -> decorate.applyTransition(d.timeline, session.selectedClipId.value, type, d.format.fps) }
            }
            LibraryTab.MARKERS -> Unit
            LibraryTab.GENERATORS -> Generator.entries.firstOrNull { "generator:${it.name}" == itemId }?.let(::addGenerator)
            LibraryTab.EFFECTS -> {
                val type = EffectType.entries.firstOrNull { "effect:${it.name}" == itemId } ?: return
                apply("应用特效") { d -> decorate.applyEffect(d.timeline, session.selectedClipId.value, type) }
                _state.update { it.copy(inspectorTab = EditUiState.TAB_EFFECTS) }
            }
        }
    }

    /**
     * The library's selected media, whole, onto the timeline; edits at the playhead move it past the new clip.
     * A replace lines up the source monitor's playhead with the timeline's when the source view shows that
     * media (else the media's start with the clip's) and leaves the playhead where it is.
     */
    private fun sourceEdit(mode: SourceEdit) {
        val s = _state.value
        // A timeline of the project goes on as a clip of its own (a nested timeline).
        s.libraryItemId?.takeIf { s.libraryTab == LibraryTab.MEDIA && it.startsWith(TIMELINE_ITEM) }?.let {
            return nestTimeline(it.removePrefix(TIMELINE_ITEM), mode)
        }
        val asset = (if (s.libraryTab == LibraryTab.MEDIA) doc?.asset(s.libraryItemId) else null)
            ?: return toast("先在左侧媒体池中选择素材")
        val at = s.playheadFrame
        val syncMs = Timecode.framesToMs(s.sourcePositionFrames, s.fps).takeIf { s.viewerMode == EditUiState.VIEWER_SOURCE && sourceAssetId == asset.id }
        // The source viewer's marks (Resolve's three-point edit): only the marked part goes onto the timeline.
        val marks = session.sourceMarks.value[asset.id]?.takeIf { asset.kind != MediaKind.IMAGE }
        val label = when (mode) {
            SourceEdit.APPEND -> "添加到时间线"
            SourceEdit.REPLACE -> "替换片段"
            SourceEdit.FIT_TO_FILL -> "适配填充"
            else -> "${mode.label}到时间线"
        }
        val done = apply(label) { d -> synced(d, sourceEdits(d.timeline, asset, marks?.inMs ?: 0, marks?.outMs ?: asset.durationMs, at, mode, d.format.fps, syncMs)) }
        if (done && mode != SourceEdit.APPEND && mode != SourceEdit.REPLACE) {
            session.selectedClipId.value?.let { id -> doc?.timeline?.clip(id)?.let { seek(it.end) } }
        }
    }

    /** [result] (media placed on [d]'s timeline) with a synced video's new sound clips taken from its recorder's file. */
    private fun synced(d: ProjectDocument, result: EditResult): EditResult =
        result.copy(timeline = autoSync.resynced(d.timeline, result.timeline, d.media, d.format.fps))

    /** A generator's image (made once per size) on top at the playhead, the pool and the timeline in one undo step. */
    private fun addGenerator(generator: Generator) {
        val document = doc ?: return
        val width = document.format.width
        val height = document.format.height
        val id = Generator.assetId(generator, width, height)
        val at = _state.value.playheadFrame
        viewModelScope.launch {
            val asset = doc?.asset(id) ?: generators.create(generator, width, height, id) ?: return@launch toast("无法生成${generator.label}")
            val current = doc ?: return@launch
            val result = try {
                generatorPlacement.place(current, generator, asset, at)
            } catch (e: EditRejected) {
                return@launch toast(e.message ?: "无法添加生成器")
            }
            session.edit("添加生成器") { result.doc }
            session.select(result.edit.selectedClipId)
            toast(result.edit.message)
        }
    }

    /** M / 在播放头添加标记: a marker at the playhead, picked in the index. */
    private fun addMarkerHere() {
        val frame = _state.value.playheadFrame
        if (apply("添加标记") { d -> addMarker(d.timeline, frame, session.selectedClipId.value, d.format.fps) }) {
            doc?.timeline?.markers?.firstOrNull { it.frame == frame.coerceAtLeast(0) }?.let { m -> _state.update { it.copy(selectedMarkerId = m.id) } }
            publish()
        }
    }

    /** Picks marker [id] in the index and takes the playhead to it. */
    /**
     * `timeline` part markerDrag: a marker's flag moved along the ruler, or its duration bar's end — previewed while the
     * pointer moves, one undo step ("移动标记" / "标记时长") when let go, and picked in the marker index.
     */
    private fun markerDrag(event: EditEvent.MarkerDrag) {
        val document = doc ?: return
        val timeline = document.timeline
        val marker = timeline.markers.firstOrNull { it.id == event.id } ?: return
        val longest = maxOf(timeline.durationFrames, document.durationFrames)
        try {
            val result = if (event.end) markerEdits.setDuration(timeline, marker.id, event.frame - marker.frame, longest)
            else markerEdits.move(timeline, marker.id, event.frame, longest)
            if (event.commit) {
                preview = null
                if (result != timeline) session.edit(if (event.end) "标记时长" else "移动标记") { it.copy(timeline = result) }
                _state.update { it.copy(selectedMarkerId = marker.id) }
            } else {
                preview = result
            }
        } catch (e: EditRejected) {
            if (event.commit) {
                preview = null
                toast(e.message ?: "无法移动标记")
            }
        }
        publish()
    }

    private fun selectMarker(id: String) {
        val marker = doc?.timeline?.markers?.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(selectedMarkerId = id) }
        seek(marker.frame)
        publish()
    }

    /** Changes the picked marker (typing into its name or note collapses into one step per field). */
    /** A length of [frames] in seconds, as the marker index shows it ("4.0 秒", "0.53 秒" for under a second). */
    private fun formatSeconds(frames: Int, fps: Int): String {
        val seconds = frames.toFloat() / fps.coerceAtLeast(1)
        return String.format(Locale.US, if (seconds < 1f) "%.2f 秒" else "%.1f 秒", seconds)
    }

    private fun marker(label: String, key: String?, change: (Timeline, String) -> Timeline) {
        val id = _state.value.selectedMarkerId ?: return
        try {
            session.edit(label, key?.let { "$it:$id" }) { it.copy(timeline = change(it.timeline, id)) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改标记")
        }
    }

    private fun deleteMarker() {
        val id = _state.value.selectedMarkerId ?: return
        val document = doc ?: return
        try {
            val (timeline, message) = markerEdits.remove(document.timeline, id)
            session.edit("删除标记") { it.copy(timeline = timeline) }
            _state.update { it.copy(selectedMarkerId = null) }
            toast(message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法删除标记")
        }
    }

    /** Timeline In / Out: kept with the project, outside the edit history (Resolve's marks are not edits). */
    private fun hasRange(): Boolean = doc?.timeline?.marks?.let { it.inFrame != null && it.outFrame != null } == true

    /** Lifts the In–Out range (the gap stays, marked for an edit that fills it); the playhead goes to the In. */
    private fun liftRange() = rangeDelete("删除入点至出点") { t, fps -> rangeEdits.lift(t, fps) }

    /** Extracts the In–Out range (the gap closes, the marks go); the playhead goes to the join. */
    private fun extractRange() = rangeDelete("波纹删除入点至出点") { t, fps -> rangeEdits.extract(t, fps) }

    private fun rangeDelete(label: String, edit: (Timeline, Int) -> EditResult) {
        val inFrame = doc?.timeline?.marks?.inFrame
        if (apply(label) { d -> edit(d.timeline, d.format.fps) } && inFrame != null) seek(inFrame)
    }

    /** The viewer shows the source monitor's media. */
    private val sourceShown: Boolean get() = _state.value.viewerMode == EditUiState.VIEWER_SOURCE && sourceAssetId != null

    /** `source-marks-row`: the source viewer's In or Out at its playhead, kept for that media (Resolve's source marks). */
    private fun sourceMark(isIn: Boolean) {
        val document = doc ?: return
        val asset = document.asset(sourceAssetId) ?: return
        if (asset.kind == MediaKind.IMAGE) return toast("图片素材固定为 5 秒，无需标记入出点")
        val s = _state.value
        val atMs = Timecode.framesToMs(s.sourcePositionFrames, s.fps).coerceIn(0, asset.durationMs)
        val current = session.sourceMarks.value[asset.id]
        val marks = if (isIn) SourceMarks.withIn(current, atMs, asset.durationMs) else SourceMarks.withOut(current, atMs)
            ?: return toast("入点必须早于出点")
        session.setSourceMarks(asset.id, marks)
        publish()
        toast(if (isIn) "已标记源入点 ${Timecode.format(s.sourcePositionFrames, s.fps)}" else "已标记源出点 ${Timecode.format(s.sourcePositionFrames, s.fps)}")
    }

    private fun clearSourceMarks() {
        val id = sourceAssetId ?: return
        if (session.sourceMarks.value[id] == null) return
        session.setSourceMarks(id, null)
        publish()
        toast("已清除源片段入出点")
    }

    /**
     * `match-frame-button` (F): Resolve's Match Frame — the clip under the playhead (the selected one when it is there,
     * else the topmost picture, else a sound) opens in the source viewer at the same frame of its media, marked In to
     * Out over what the clip uses, and is picked in the media pool, so an edit from the source takes that part again.
     */
    private fun matchFrame(clipId: String? = null) {
        val document = doc ?: return
        val timeline = document.timeline
        val held = clipId?.let(timeline::clip)
        if (held != null && held.assetId == null) return toast("标题没有源素材")
        val at = held?.let { c -> _state.value.playheadFrame.takeIf { it in c } ?: c.start } ?: _state.value.playheadFrame
        val order = timeline.tracks.map { it.id }
        val clip = held ?: timeline.clip(session.selectedClipId.value)?.takeIf { at in it && it.assetId != null }
            ?: timeline.clips.filter { at in it && it.assetId != null && it.enabled }
                .sortedWith(compareBy({ it.kind == ClipKind.AUDIO }, { order.indexOf(it.trackId) })).firstOrNull()
            ?: return toast("播放头处没有可以匹配的片段")
        val asset = document.asset(clip.assetId) ?: return toast("找不到这个片段的素材")
        if (asset.offline) return toast("素材离线，无法匹配帧")
        val fps = document.format.fps
        val atMs = clip.sourceMsAt(at - clip.start, fps).coerceIn(0, asset.durationMs)
        if (asset.kind != MediaKind.IMAGE) {
            // The clip's window into its media (a reversed clip plays the same window backwards).
            val range = clip.sourceInMs.coerceAtLeast(0) to (clip.sourceInMs + clip.sourceDurationMs(fps)).coerceAtMost(asset.durationMs)
            if (range.second > range.first) session.setSourceMarks(asset.id, SourceMarks(range.first, range.second))
        }
        program.pause()
        _state.update { it.copy(libraryTab = LibraryTab.MEDIA, libraryItemId = asset.id, viewerMode = EditUiState.VIEWER_SOURCE) }
        loadSourceMonitor()
        source.seekTo(atMs)
        _state.update { it.copy(sourcePositionFrames = Timecode.nearestFrame(atMs, fps)) }
        publish()
        toast("匹配帧：${asset.name} ${Timecode.format(Timecode.nearestFrame(atMs, fps), fps)}")
    }

    private fun mark(change: (Timeline, Int) -> TimelineMarksUseCase.Result) {
        val document = doc ?: return
        try {
            val result = change(document.timeline, document.format.fps)
            session.update { it.copy(timeline = it.timeline.copy(marks = result.timeline.marks)) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法标记入出点")
        }
    }

    /** The track header's destination control: kept with the project, outside the edit history. */
    private fun retarget(trackId: String) {
        val document = doc ?: return
        try {
            val result = destinations.toggle(document.timeline, trackId)
            session.update { it.copy(timeline = it.timeline.copy(targets = result.timeline.targets)) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法设置编辑目标")
        }
    }

    private fun seek(frame: Int) {
        val document = doc ?: return
        val fps = document.format.fps
        val clamped = frame.coerceIn(0, document.durationFrames.coerceAtLeast(0))
        program.seekTo(Timecode.frameToSeekMs(clamped, fps))
        _state.update { it.copy(playheadFrame = clamped) }
        updateProgramMessage()
    }

    private fun loadSourceMonitor() {
        val document = doc ?: return
        val s = _state.value
        val asset = (if (s.libraryTab == LibraryTab.MEDIA) document.asset(s.libraryItemId) else null)
            ?: document.asset(document.timeline.clip(session.selectedClipId.value)?.assetId)
        source.load(asset, document.colorManagement)
        sourceAssetId = asset?.id
        _state.update {
            it.copy(
                sourceName = asset?.name,
                sourceIsVideo = asset?.kind == MediaKind.VIDEO && !asset.offline,
                sourceStill = asset?.let { a -> assetFrames[a.id] }?.takeIf { asset.kind == MediaKind.IMAGE },
                sourceAspect = asset?.takeIf { a -> a.width > 0 && a.height > 0 }?.let { a -> a.width.toFloat() / a.height } ?: (16f / 9f),
            )
        }
        asset?.let { loadAssetFrame(it.id) }
    }

    // ---- presentation ------------------------------------------------------------------

    private fun publish() {
        val document = doc ?: return
        val timeline = preview ?: document.timeline
        val fps = document.format.fps
        val selectedId = session.selectedClipId.value
        val selected = timeline.clip(selectedId)
        val copies = reverseCopies.copies.value
        requestPreviews(document, timeline)
        _state.update { s ->
            s.copy(
                footageClipIds = timeline.clips.filter { sceneCuts.unavailable(it, document.asset(it.assetId)) == null }.map { it.id }.toSet(),
                libraryItems = libraryItems(s.libraryTab, document),
                markers = timeline.markers.sortedBy { it.frame }.map { m ->
                    val longest = (maxOf(timeline.durationFrames, document.durationFrames) - m.frame).coerceAtLeast(0)
                    MarkerUi(
                        m.id, m.label, Timecode.format(m.frame, fps), m.color, m.note,
                        durationSeconds = m.durationFrames.toFloat() / fps,
                        maxDurationSeconds = longest.toFloat() / fps,
                        rangeText = if (m.hasDuration) "${formatSeconds(m.durationFrames, fps)} · 到 ${Timecode.format(m.endFrame, fps)}" else "一点",
                        canTakeMarks = timeline.marks.inFrame != null && timeline.marks.outFrame != null,
                    )
                },
                selectedMarkerId = s.selectedMarkerId?.takeIf { id -> timeline.markers.any { it.id == id } },
                gapText = liveGap(timeline)?.let { formatSeconds(it.frames, fps) },
                timeline = TimelineUi(
                    selectedGap = liveGap(timeline),
                    compoundBars = timeline.clips.filter { it.isCompound }.mapNotNull { c -> compounds.bars(document, c)?.let { c.id to it } }.toMap(),
                    multicamClipIds = timeline.clips.filter { multicams.isMulticam(document, it) }.map { it.id }.toSet(),
                    compoundLabels = timeline.clips.filter { it.isCompound }.associate { c ->
                        // A multicam part says which angle it plays (Resolve labels the clip with its angle).
                        c.id to if (multicams.isMulticam(document, c)) {
                            multicams.angles(document, c).getOrNull(c.angle)?.let { "${it.label} · ${it.name}" } ?: "机位 ${c.angle + 1}"
                        } else compounds.kindLabel(document, c.compoundId)
                    },
                    tracks = timeline.tracks,
                    clips = timeline.clips,
                    markers = timeline.markers,
                    fps = fps,
                    durationFrames = maxOf(timeline.durationFrames, document.durationFrames),
                    selectedClipId = selectedId,
                    selectedClipIds = selection.toSet(),
                    multiSelect = s.multiSelect,
                    zoom = zoom,
                    waveforms = waves.toMap(),
                    thumbnails = thumbs.toMap(),
                    frameAspect = document.format.width.toFloat() / document.format.height,
                    framings = timeline.clips.filter { it.kind == ClipKind.VIDEO }.mapNotNull { c ->
                        val thumb = thumbs[c.id]
                        document.framingOf(c, thumb?.let { it.width.toFloat() / it.height.coerceAtLeast(1) })?.let { c.id to it }
                    }.toMap(),
                    transitionSpans = timeline.clips.filter { it.transitionIn != null }.mapNotNull { c ->
                        TransitionHandles.span(document, c, fps)?.let { c.id to TransitionSpanUi(it.lead, it.frames) }
                    }.toMap(),
                    offlineAssetIds = document.media.filter { it.offline }.map { it.id }.toSet(),
                    reverseMakingAssetIds = copies.filterValues { it is ReverseCopy.Making }.keys,
                    reverseFailedAssetIds = copies.filterValues { it is ReverseCopy.Failed }.keys,
                    targets = timeline.activeTargets,
                    marks = timeline.marks,
                    selectedMarkerId = s.selectedMarkerId,
                    flags = document.media.filter { it.flags.isNotEmpty() }.associate { it.id to it.flags },
                ),
                inspector = selected?.let { inspectorFor(it, timeline, fps) },
                curves = curvesFor(timeline, fps),
                selection = selectionUi(timeline),
                // While marks are set the header names what its range buttons act on, leaving them room.
                summary = TimelineMarksUseCase.label(timeline.marks, fps)
                    ?: "${document.format.width}×${document.format.height} · $fps fps · ${Timecode.format(document.durationFrames, fps)}",
                timelineName = document.timelineName,
                compoundTrail = compounds.trail(document).dropLast(1),
                editIndex = editIndexRows(document, s.indexFilter, selectedId),
                editIndexSummary = editIndex.summary(document, editIndex.rows(document, s.indexFilter), s.indexFilter),
                // Compound clips' timelines are opened from their clips or the media pool, not the timeline menu.
                timelineEntries = document.timelineEntries.filterNot { it.compound }.map { e ->
                    TimelineEntryUi(e.id, e.name, e.open, "${e.format.width}×${e.format.height} · ${Timecode.format(e.durationFrames, e.format.fps)}")
                },
                hasMarks = !timeline.marks.isEmpty,
                hasRange = timeline.marks.inFrame != null && timeline.marks.outFrame != null,
                linkAction = links.actionFor(timeline, selection),
                // The clip EQ sheet follows its clip; it closes once that clip is gone.
                clipEq = eqClipId?.let { id -> timeline.clip(id)?.let { EqEditorUi(id, "片段 · ${it.name}", it.eq, CLIP_EQ_APPLIES_TO) } }
                    .also { if (it == null) eqClipId = null },
                keyPick = keyPickFor(selected, s.playheadFrame),
                sourceMarks = sourceAssetId?.let { id -> session.sourceMarks.value[id] }?.let { m ->
                    "入点 ${Timecode.format(Timecode.nearestFrame(m.inMs, fps), fps)} · 出点 ${Timecode.format(Timecode.nearestFrame(m.outMs, fps), fps)} · " +
                        formatSeconds(Timecode.nearestFrame(m.durationMs, fps), fps)
                },
                canAddVideoTrack = TracksUseCase.canAdd(timeline, TrackKind.VIDEO),
                canAddAudioTrack = TracksUseCase.canAdd(timeline, TrackKind.AUDIO),
                fps = fps,
                pictureAspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1),
                programEmpty = document.timeline.clips.isEmpty(),
                jogReadout = jogReadout(timeline, fps),
            )
        }
        updateProgramMessage()
    }

    /** Several clips selected: how many (a picture with its sound once), what they are, what the batch switch does. */
    private fun selectionUi(timeline: Timeline): SelectionUi? {
        val groups = timeline.selectedGroups(selection)
        if (groups.size < 2) return null
        val clips = groups.flatten()
        val detail = listOf(ClipKind.VIDEO to "画面", ClipKind.AUDIO to "声音", ClipKind.TITLE to "标题")
            .mapNotNull { (kind, label) -> clips.count { it.kind == kind }.takeIf { it > 0 }?.let { "$it 个$label" } }
            .joinToString(" · ")
        val assets = doc?.media.orEmpty().associateBy { it.id }
        val reference = groups.first()
        val referenceSound = waveAlign.soundOf(reference, assets)
        return SelectionUi(
            count = groups.size,
            detail = detail,
            allDisabled = groups.none { it.first().enabled },
            locked = clips.any { timeline.track(it.trackId)?.locked == true },
            alignNote = when {
                aligning -> "正在读取声音并比较…"
                referenceSound == null -> "最后点选的「${reference.first().name}」没有声音：最后点选一个有声音的片段作参照"
                else -> "以最后点选的「${reference.first().name}」为参照，按声音把其余片段移到同步的位置"
            },
            canAlign = referenceSound != null && !aligning,
            aligning = aligning,
            compoundNote = compounds.unavailable(timeline, selection) ?: "把所选的 ${groups.size} 组片段收进一个复合片段，放在原来的位置",
            canCompound = compounds.unavailable(timeline, selection) == null,
            multicamNote = multicams.note(timeline, selection),
            canMulticam = multicams.unavailable(timeline, selection) == null,
        )
    }

    private fun refreshInspector() {
        val document = doc ?: return
        val timeline = preview ?: document.timeline
        val inspector = timeline.clip(session.selectedClipId.value)?.let { inspectorFor(it, timeline, document.format.fps) }
        val curves = curvesFor(timeline, document.format.fps)
        if (inspector != _state.value.inspector || curves != _state.value.curves) _state.update { it.copy(inspector = inspector, curves = curves) }
    }

    /** The curve the editor shows on the selected clip (see [curvesFor]). */
    private fun curveChannel(): KeyCurveChannel? {
        val clip = doc?.timeline?.clip(session.selectedClipId.value) ?: return null
        return pickCurve(curveEdits.curves(clip))?.channel
    }

    /** The picked curve if the clip has it, else its first animated one, else its first with keys, else its first. */
    private fun pickCurve(curves: List<KeyCurve>): KeyCurve? =
        curves.firstOrNull { it.channel.key == curveKey } ?: curves.firstOrNull { it.animated } ?: curves.firstOrNull { it.keys.isNotEmpty() } ?: curves.firstOrNull()

    /** A drag on the curve editor's graph: one undo step per drag ([key]), told only when it is refused. */
    private fun curveEdit(label: String, key: String, edit: (Timeline, String, KeyCurveChannel) -> EditResult) {
        val id = session.selectedClipId.value ?: return
        val channel = curveChannel() ?: return
        try {
            session.edit(label, "$key:$id") { it.copy(timeline = edit(it.timeline, id, channel).timeline) }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改关键帧")
        }
        refreshInspector()
    }

    /** `keyframe-curve-panel` for the selected clip while the curve mode is on. */
    private fun curvesFor(timeline: Timeline, fps: Int): CurvesUi? {
        if (!_state.value.curveMode) return null
        val clip = timeline.clip(session.selectedClipId.value)
            ?: return CurvesUi(hint = "在时间线上选择一个片段，这里显示它的关键帧曲线：缩放、位置、旋转、不透明度、裁切、特效强度或音量随时间的变化")
        if (clip.id != curveClipId) {
            curveClipId = clip.id
            curveKeyIndex = null
        }
        val curves = curveEdits.curves(clip)
        val curve = pickCurve(curves) ?: return CurvesUi(clipName = clip.name, hint = "这个片段没有可以做关键帧的属性")
        val local = _state.value.playheadFrame - clip.start
        val inside = local in 0 until clip.durationFrames
        val at = local.coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0))
        val index = curveKeyIndex?.takeIf { it in curve.keys.indices }.also { curveKeyIndex = it }
        val key = index?.let { curve.keys[it] }
        val last = curve.keys.lastIndex
        val readout = when {
            key != null -> buildString {
                append("关键帧 ${index + 1}/${curve.keys.size} · ${Timecode.format(clip.start + key.frame, fps)} · ${curve.format(key.value)}")
                val preset = presetOf(curve, index)
                if (preset == null && curve.keys.size > 1) {
                    if (index > 0) append(" · 进入斜率 ${String.format(java.util.Locale.US, "%.2f", key.ease.slopeIn)}")
                    if (index < last) append(" · 离开斜率 ${String.format(java.util.Locale.US, "%.2f", key.ease.slopeOut)}")
                }
            }
            curve.keys.isEmpty() -> "${curve.label}没有关键帧：把播放头移到片段内，点「添加关键帧」"
            else -> "点曲线上的关键帧选中它：拖动改时间和数值，拖动两侧的控制点改快慢"
        }
        return CurvesUi(
            clipName = clip.name,
            rows = curves.map { c ->
                CurveRowUi(c.channel.key, c.label, c.format(curveEdits.valueAt(clip, c.channel, at) ?: c.base), c.keys.size, c.animated, c.channel == curve.channel)
            },
            curve = curve,
            clipStart = clip.start,
            durationFrames = clip.durationFrames,
            fps = fps,
            selectedKey = index,
            readout = readout,
            ease = index?.let { presetOf(curve, it) },
            canEase = key != null && curve.keys.size > 1,
            canAdd = inside && curve.keys.none { it.frame == local },
            canDelete = key != null,
            sharedNote = if (curve.channel is KeyCurveChannel.Transform && curve.keys.isNotEmpty()) "每项数值的关键帧各自独立：移动时间或改缓动只影响这条曲线" else null,
        )
    }

    /**
     * The preset key [index] of [curve] was given (an exact match), else the one it acts like — its first key's arriving side
     * and its last's leaving side move nothing.
     */
    private fun presetOf(curve: KeyCurve, index: Int): CurveEase? {
        val ease = curve.keys[index].ease
        return CurveEase.of(ease) ?: CurveEase.entries.firstOrNull { p ->
            (index == 0 || p.ease.slopeIn == ease.slopeIn) && (index == curve.keys.lastIndex || p.ease.slopeOut == ease.slopeOut)
        }
    }

    /** The playhead in frames from [clipId]'s start (outside 0 until its length when it is elsewhere). */
    private fun playheadIn(timeline: Timeline, clipId: String): Int =
        _state.value.playheadFrame - (timeline.clip(clipId)?.start ?: 0)

    private var lastTrimRefusal: Pair<String, Long>? = null

    /**
     * One jog step of a slip or roll: consecutive steps on the same clip make one undo step, and a
     * refusal at the media's or a neighbour's limit is said once rather than on every step.
     */
    private fun trimJog(label: String, key: String, edit: (ProjectDocument, String) -> EditResult) {
        val document = doc ?: return
        val id = session.selectedClipId.value ?: return toast("先在时间线上选择要修剪的片段")
        try {
            val result = edit(document, id)
            session.edit(label, "$key:$id") { it.copy(timeline = result.timeline) }
        } catch (e: EditRejected) {
            val message = e.message ?: "无法修剪"
            val now = clock()
            val last = lastTrimRefusal
            if (last == null || last.first != message || now - last.second > TRIM_REFUSAL_QUIET_MS) toast(message)
            lastTrimRefusal = message to now
        }
    }

    private fun jogReadout(timeline: Timeline, fps: Int): String {
        val clip = timeline.clip(session.selectedClipId.value)
        return when (_state.value.jogMode) {
            EditUiState.JOG_SLIP -> clip?.let { "入点 " + Timecode.format(((it.sourceInMs * fps + 500) / 1000L).toInt(), fps) } ?: "选择片段后滑移"
            EditUiState.JOG_ROLL -> clip?.let { "剪辑点 " + Timecode.format(it.end, fps) } ?: "选择片段后滚动"
            EditUiState.JOG_SLIDE -> clip?.let { "起点 " + Timecode.format(it.start, fps) } ?: "选择片段后滑动"
            else -> ""
        }
    }

    /** Volume keyframes live on the sound clip (the selection itself or its linked sound); the selection stays. */
    private fun toggleVolumeKeyframe() {
        val document = doc ?: return
        val target = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return toast("所选片段没有音频")
        try {
            val result = keyframes.toggleVolume(document.timeline, target.id, _state.value.playheadFrame - target.start)
            session.edit("音量关键帧") { it.copy(timeline = result.timeline) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改")
        }
    }

    private fun togglePanKeyframe() {
        val document = doc ?: return
        val target = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return toast("所选片段没有音频")
        try {
            val result = keyframes.togglePan(document.timeline, target.id, _state.value.playheadFrame - target.start)
            session.edit("声像关键帧") { it.copy(timeline = result.timeline) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改")
        }
    }

    private fun toggleVolumeEase(arriving: Boolean) {
        val document = doc ?: return
        val target = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return toast("所选片段没有音频")
        try {
            val result = keyframes.easeVolume(document.timeline, target.id, _state.value.playheadFrame - target.start, arriving)
            session.edit(if (arriving) "音量缓入" else "音量缓出") { it.copy(timeline = result.timeline) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法修改")
        }
    }

    private fun jumpToVolumeKeyframe(next: Boolean) {
        val document = doc ?: return
        val clip = props.audioTarget(document.timeline, session.selectedClipId.value) ?: return
        val local = _state.value.playheadFrame - clip.start
        val target = (if (next) keyframes.nextVolume(clip, local) else keyframes.previousVolume(clip, local)) ?: return
        seek(clip.start + target)
    }

    private fun jumpToEffectKeyframe(effectId: String, next: Boolean) {
        val clip = doc?.timeline?.clip(session.selectedClipId.value) ?: return
        val local = _state.value.playheadFrame - clip.start
        val target = (if (next) keyframes.nextEffect(clip, effectId, local) else keyframes.previousEffect(clip, effectId, local)) ?: return
        seek(clip.start + target)
    }

    private fun jumpToKeyframe(next: Boolean) {
        val clip = doc?.timeline?.clip(session.selectedClipId.value) ?: return
        val local = _state.value.playheadFrame - clip.start
        val target = (if (next) keyframes.next(clip, local) else keyframes.previous(clip, local)) ?: return
        seek(clip.start + target)
    }

    private fun updateProgramMessage() {
        val document = doc ?: return
        val frame = _state.value.playheadFrame
        val offline = document.timeline.clips.any { c ->
            c.kind == ClipKind.VIDEO && frame in c && document.asset(c.assetId)?.offline == true &&
                document.timeline.track(c.trackId)?.enabled == true
        }
        val message = when {
            document.timeline.clips.isEmpty() -> "时间线为空"
            offline -> "素材离线：原文件已被移动或删除"
            else -> null
        }
        if (message != _state.value.programMessage) _state.update { it.copy(programMessage = message) }
    }

    /** What the selected sound's cross fade does (`cross-fade-note`). */
    private fun crossFadeNote(audio: Clip, fps: Int): String {
        val document = doc ?: return ""
        val before = AudioCrossFades.outgoing(document, audio) ?: return "前面没有紧挨着的声音片段，无法交叉淡化"
        val cover = AudioCrossFades.coverage(document, audio, fps)
        val seconds = { frames: Int -> String.format(Locale.US, "%.2f 秒", frames / fps.toFloat()) }
        return when {
            cover == null -> "与“${before.name}”之间直接切换；拖动可交叉淡化（等功率）"
            cover.frames == 0 -> "“${before.name}”之后没有素材了，只能直接切换"
            cover.shortened -> "“${before.name}”之后的素材只够 ${seconds(cover.frames)}，交叉淡化按此长度进行"
            else -> "从“${before.name}”交叉淡化 ${seconds(cover.frames)}（等功率，前一段在切点后延续）"
        }
    }

    private fun inspectorFor(clip: Clip, timeline: Timeline, fps: Int): InspectorUi {
        val audio = props.audioTarget(timeline, clip.id)
        // An animated clip shows its values at the playhead (held at the clip's ends outside it).
        val local = _state.value.playheadFrame - clip.start
        val inside = local in 0 until clip.durationFrames
        val shown = clip.transformAt(local.coerceIn(0, clip.durationFrames - 1))
        return InspectorUi(
            clipId = clip.id,
            kind = clip.kind,
            name = clip.name,
            locked = timeline.track(clip.trackId)?.locked == true,
            enabled = clip.enabled,
            compound = doc?.let { d -> compounds.info(d, clip) }?.let { CompoundUi(it.name, it.summary, it.title) },
            multicam = doc?.let { d -> multicamUi(d, clip) },
            titleText = clip.title?.text.orEmpty(),
            isSubtitle = clip.title?.template == TitleTemplate.SUBTITLE,
            subtitleStyle = timeline.subtitleStyleOf(clip),
            subtitleTrackName = timeline.track(clip.trackId)?.name.orEmpty(),
            titleSize = clip.title?.sizePx ?: 72,
            titleStyle = clip.title?.takeIf { it.template != TitleTemplate.SUBTITLE },
            titleAnimationNote = clip.title?.let { it.animation.describe(it.characters(), clip.durationFrames, fps) }.orEmpty(),
            scale = shown.scalePercent,
            positionX = shown.positionX,
            positionY = shown.positionY,
            rotation = shown.rotationDegrees,
            opacity = shown.opacityPercent,
            crop = CropControl.entries.map { it.read(shown) },
            mask = clip.mask?.at(local.coerceIn(0, clip.durationFrames - 1)),
            maskKey = masks.keyState(clip, if (inside) local else -1),
            maskBlocked = clip.mask?.keyframes?.isNotEmpty() == true && !inside,
            maskHint = maskHint(clip, inside),
            maskTrack = TrackUi(tracker.maskUnavailable(clip, doc?.asset(clip.assetId)), maskTrackStatus != null, maskTrackStatus.orEmpty(), maskTrackMotion),
            // The mask belongs to the picture before the mirror effect turns its source around: never mirrored.
            maskOverlay = clip.mask?.takeIf { inside && clip.kind == ClipKind.VIDEO }?.let { WindowOverlayUi(it.at(local), clip.transformAt(local), mirrored = false) },
            dynamicZoom = clip.dynamicZoom,
            composite = clip.composite,
            keyable = clip.kind == ClipKind.VIDEO,
            chromaKey = clip.chromaKey,
            keyPicking = keyPicking,
            keyMatte = keyMatteClip == clip.id && clip.chromaKey != null,
            pictureFades = clip.kind != ClipKind.AUDIO && ClipFades.takes(clip),
            pictureFadeIn = clip.fadeInSeconds,
            pictureFadeOut = clip.fadeOutSeconds,
            maxPictureFade = props.maxFade(clip, fps),
            pictureFadeInCurve = clip.fadeInCurve,
            pictureFadeOutCurve = clip.fadeOutCurve,
            audioClipId = audio?.id,
            volumeDb = audio?.volumeAt((_state.value.playheadFrame - audio.start).coerceIn(0, audio.durationFrames - 1)) ?: 0f,
            pan = audio?.panAt((_state.value.playheadFrame - audio.start).coerceIn(0, audio.durationFrames - 1)) ?: 0f,
            panText = panText(audio?.panAt((_state.value.playheadFrame - audio.start).coerceIn(0, audio.durationFrames - 1)) ?: 0f),
            panKey = audio?.let { keyframes.panState(it, _state.value.playheadFrame - it.start) } ?: ParamKeyState.NONE,
            panBlocked = audio != null && audio.panKeyframes.isNotEmpty() && _state.value.playheadFrame - audio.start !in 0 until audio.durationFrames,
            fadeIn = audio?.fadeInSeconds ?: 0f,
            fadeOut = audio?.fadeOutSeconds ?: 0f,
            maxFade = audio?.let { props.maxFade(it, fps) } ?: 0f,
            fadeInCurve = audio?.fadeInCurve ?: 0f,
            fadeOutCurve = audio?.fadeOutCurve ?: 0f,
            channels = audio?.channels ?: AudioChannels.STEREO,
            channelsNote = audio?.let { a ->
                val file = doc?.asset(a.assetId)
                // A mono file plays the same on both sides: there is nothing to map.
                if (file != null && (file.audioDescription.contains("单声道") || file.audioDescription.contains("mono", ignoreCase = true))) {
                    "单声道素材：左右两边本来就相同"
                } else {
                    a.channels.note
                }
            }.orEmpty(),
            pitchSemitones = (audio?.pitchCents ?: 0) / 100f,
            pitchNote = props.pitchNote(audio?.pitchCents ?: 0),
            crossFadeFrames = audio?.crossFadeFrames ?: 0,
            maxCrossFadeFrames = audio?.let { props.maxCrossFade(it, fps) } ?: 0,
            eqSummary = audio?.let { eqSummary(it.eq) } ?: "平直",
            eqActive = audio?.eq?.isActive == true,
            canCrossFade = audio != null && doc?.let { AudioCrossFades.outgoing(it, audio) } != null,
            crossFadeNote = audio?.let { crossFadeNote(it, fps) }.orEmpty(),
            crossFadeText = String.format(Locale.US, "%.2f 秒", (audio?.crossFadeFrames ?: 0) / fps.coerceAtLeast(1).toFloat()),
            speedIndex = sectionSpeed(clip, local).preset?.let(PlaybackSpeed.entries::indexOf) ?: -1,
            speedLabel = sectionSpeed(clip, local).label,
            speedPoints = speedPointsUi(clip, local, inside),
            reverse = clip.reverse,
            effects = clip.effects.map { effect ->
                EffectRowUi(
                    effect.id,
                    effect.type.label,
                    effect.enabled,
                    effect.amountAt(local.coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0))),
                    keys = keyframeUi(
                        frames = effect.keyframes.map { it.frame },
                        key = effect.keyframeAt(local)?.takeIf { inside }?.ease,
                        local = local,
                        inside = inside,
                        hint = effectKeyHint(effect, local, inside),
                    ),
                )
            },
            placementIndex = Placement.of(shown)?.ordinal ?: -1,
            trackMoveLabel = otherVideoTrack(timeline, clip)?.let { "移到 ${it.second.name}" },
            trackMoveUp = otherVideoTrack(timeline, clip)?.first ?: true,
            transitionName = clip.transitionIn?.type?.label,
            transitionSeconds = (clip.transitionIn?.frames ?: 0) / fps.toFloat(),
            transitionMaxSeconds = DecorateClipUseCase.maxTransitionFrames(clip, fps) / fps.toFloat(),
            transitionNote = transitionNote(clip, fps),
            transition = clip.transitionIn,
            stabilization = if (clip.kind == ClipKind.VIDEO) stabilizationUi(clip, doc?.asset(clip.assetId), fps) else null,
            reverseStatus = reverseStatus(clip),
            reverseFailed = clip.reverse && reverseCopies.copies.value[clip.assetId] is ReverseCopy.Failed,
            transformKeys = keyframeUi(
                frames = clip.keyframes.map { it.frame },
                key = clip.keyframeAt(local)?.takeIf { inside }?.ease,
                local = local,
                inside = inside,
                hint = when {
                    clip.keyframes.isEmpty() -> "点数值右侧的关键帧按钮，让这一项随时间变化；全部添加则一次记下所有数值"
                    !inside -> "播放头不在片段内：移进片段再调整动画"
                    clip.keyframeAt(local) != null -> "有关键帧的数值调整后记在此处 · 可设缓入缓出"
                    else -> "有关键帧的数值调整后记在播放头处，其余数值整段改变"
                },
            ),
            paramKeys = TransformKeyGroup.entries.associateWith { keyframes.groupState(clip, if (inside) local else -1, it) },
            volumeKeys = audio?.let { volumeKeys(it) } ?: KeyframeUi(),
            canFreeze = clip.kind == ClipKind.VIDEO && doc?.asset(clip.assetId)?.let { it.kind != MediaKind.IMAGE && !it.offline } == true,
            retime = clip.retime,
            canRetime = clip.kind == ClipKind.VIDEO && doc?.asset(clip.assetId)?.kind == MediaKind.VIDEO,
            scaling = clip.scaling,
            scalingNote = doc?.let { d ->
                val asset = d.asset(clip.assetId)
                scalingNote(clip.scaling, d.format.scaling, asset?.width ?: 0, asset?.height ?: 0, d.format.width, d.format.height)
            }.orEmpty(),
        )
    }

    /** A pan as the inspector says it: 居中, or the side and how far, in hundredths (Resolve's −100…100). */
    private fun panText(pan: Float): String {
        val amount = (abs(pan) * 100).roundToInt()
        return when {
            amount == 0 -> "居中"
            pan < 0f -> "左 $amount"
            else -> "右 $amount"
        }
    }

    private fun volumeKeys(audio: Clip): KeyframeUi {
        val local = _state.value.playheadFrame - audio.start
        val inside = local in 0 until audio.durationFrames
        return keyframeUi(
            frames = audio.volumeKeyframes.map { it.frame },
            key = audio.volumeKeyframeAt(local)?.takeIf { inside }?.ease,
            local = local,
            inside = inside,
            hint = when {
                audio.volumeKeyframes.isEmpty() -> "添加关键帧后，音量可以随时间变化"
                !inside -> "播放头不在片段内：移进片段再调整音量"
                audio.volumeKeyframeAt(local) != null -> "音量变化：调整会记录在此关键帧 · 可设缓入缓出"
                else -> "音量变化：调整会记录在播放头处的关键帧"
            },
        )
    }

    /**
     * Why a dissolve, push or wipe plays shorter than set or away from where its alignment puts it (a source runs out
     * before or after the cut), or reveals the layers beneath (no clip before it); null when it plays as set.
     */
    private fun transitionNote(clip: Clip, fps: Int): String? {
        val transition = clip.transitionIn ?: return null
        if (!transition.type.showsOutgoing) return null
        val span = TransitionHandles.span(doc ?: return null, clip, fps) ?: return null
        if (span.outgoing == null) {
            return if (transition.alignment == TransitionAlignment.START) "前面没有相邻片段，转场从下层画面（或黑场）显现。"
            else "前面没有相邻片段：转场从剪辑点开始，从下层画面（或黑场）显现。"
        }
        val around = "剪辑点前 ${seconds(span.lead, fps)} 秒、后 ${seconds(span.tail, fps)} 秒"
        return when {
            // A dip needs no handles centred, so only a clip before it too short to fade out in shortens it.
            transition.type.dips && span.frames <= 0 -> "前一片段太短，无法过渡，按硬切播放。"
            transition.type.dips && span.shortened -> "前一片段太短，转场缩短为 ${seconds(span.frames, fps)} 秒（$around）。"
            span.frames <= 0 -> when (transition.alignment) {
                TransitionAlignment.START -> "前一片段出点后没有余量素材，转场无法显现它，按硬切播放。"
                TransitionAlignment.END -> "本片段入点前没有余量素材，转场无法提前开始，按硬切播放。"
                TransitionAlignment.CENTER -> "前一片段出点后和本片段入点前都没有余量素材，按硬切播放。"
            }
            span.shortened -> when (transition.alignment) {
                TransitionAlignment.START ->
                    "前一片段出点后只剩 ${seconds(span.tail, fps)} 秒素材，转场缩短为 ${seconds(span.frames, fps)} 秒。"
                TransitionAlignment.END ->
                    "本片段入点前只剩 ${seconds(span.lead, fps)} 秒可用，转场缩短为 ${seconds(span.frames, fps)} 秒。"
                TransitionAlignment.CENTER -> "两侧余量都不够，转场缩短为 ${seconds(span.frames, fps)} 秒（$around）。"
            }
            span.shifted && span.lead < span.wantedLead -> "本片段入点前余量不够，转场移向剪辑点后（$around）。"
            span.shifted -> "前一片段出点后余量不够，转场移向剪辑点前（$around）。"
            else -> null
        }
    }

    /** The speed the inspector shows: the clip's, or with speed points the section's under the playhead. */
    private fun sectionSpeed(clip: Clip, local: Int): ClipSpeed =
        if (clip.retimed) ClipSpeed(clip.speedAt(local.coerceIn(0, clip.durationFrames - 1))) else clip.speed

    /** `speed-points-block`: the clip's speed points — its sections' speeds, the one under the playhead, what can be done. */
    private fun speedPointsUi(clip: Clip, local: Int, inside: Boolean): SpeedPointsUi {
        val bounds = clip.sectionBounds
        val section = bounds.indexOfLast { it <= local.coerceIn(0, clip.durationFrames - 1) }.coerceIn(0, bounds.size - 2)
        val image = doc?.asset(clip.assetId)?.kind == MediaKind.IMAGE
        val possible = clip.kind == ClipKind.VIDEO || clip.kind == ClipKind.AUDIO
        val usable = possible && !image && !clip.reverse
        // `speed-ramp-row`: the point nearest the playhead, as 删除最近的 picks it.
        val fps = doc?.format?.fps ?: 30
        val point = if (clip.retimed) (1 until bounds.lastIndex).minBy { kotlin.math.abs(bounds[it] - local) } else 0
        val ramp = if (point > 0) clip.rampAt(point) else 0
        val room = if (point > 0 && usable) clip.rampRoom(point) else 0
        val sections = clip.speedSections
        val change = if (point > 0) "${sections[point - 1].speed.label} → ${sections[point].speed.label}" else ""
        val ramps = SpeedPointsUi(
            rampPoint = point,
            rampSeconds = ramp / fps.toFloat(),
            rampMaxSeconds = room / fps.toFloat(),
            rampText = if (ramp == 0) "立即" else "${RetimeUseCase.secondsLabel(ramp, fps)} 秒",
            rampNote = when {
                point == 0 -> ""
                !clip.changesSpeedAt(point) -> "最近的第 $point 个变速点两侧速度相同（${sections[point].speed.label}），不需要平滑过渡"
                ramp == 0 -> "最近的第 $point 个变速点（$change）立即变速；拖动滑块，让速度在一段时间内平滑过渡"
                ramp > room -> "第 $point 个变速点（$change）的过渡有一部分在片段之外，剪短前的画面不变"
                else -> "第 $point 个变速点（$change）：速度在 ${RetimeUseCase.secondsLabel(ramp, fps)} 秒内平滑过渡，变速点在过渡中间，片段时长不变"
            },
        )
        return ramps.copy(
            sections = if (clip.retimed) clip.speedSections.map { it.speed.label } else emptyList(),
            current = if (clip.retimed) section + 1 else 0,
            canAdd = usable && local in 1 until clip.durationFrames && local !in bounds,
            canRemove = usable && clip.retimed,
            hint = when {
                !possible || image -> "图片和标题没有可以变速的画面"
                clip.reverse -> "倒放的片段不能添加变速点"
                !clip.retimed -> "在播放头处添加变速点，把片段分成几段，各段可以设置自己的速度"
                !inside -> "播放头不在片段内：移进片段，速度改的是播放头所在的一段"
                else -> "上面的速度只改第 ${section + 1} 段（播放头所在），其余各段不变"
            },
        )
    }

    /** `effect-keyframe-hint`: how a keyframed effect's amount changes, said only once it has keyframes. */
    private fun effectKeyHint(effect: ClipEffect, local: Int, inside: Boolean): String = when {
        effect.keyframes.isEmpty() -> ""
        !inside -> "${effect.keyframes.size} 个关键帧 · 播放头不在片段内，移进片段再调整强度"
        effect.keyframeAt(local) != null -> "${effect.keyframes.size} 个关键帧 · 调整会记录在此关键帧"
        else -> "${effect.keyframes.size} 个关键帧 · 调整强度会在播放头处添加关键帧"
    }

    private fun seconds(frames: Int, fps: Int): String = String.format(Locale.US, "%.1f", frames / fps.toFloat())

    private fun reverseStatus(clip: Clip): String? {
        if (!clip.reverse) return null
        return when (val copy = reverseCopies.copies.value[clip.assetId]) {
            is ReverseCopy.Making -> "正在生成倒放素材… ${(copy.progress * 100).roundToInt()}%"
            is ReverseCopy.Failed -> "倒放素材生成失败：${copy.reason}。关闭再打开倒放可重试。"
            else -> null
        }
    }

    /**
     * A clip was just reversed: make (or retry) the reversed copy of its media right away and say
     * whether the preview already plays backwards.
     */
    private fun startReverseCopy() {
        val document = doc ?: return
        val clip = document.timeline.clip(session.selectedClipId.value) ?: return
        val asset = document.asset(clip.assetId)?.takeIf { it.kind != MediaKind.IMAGE && !it.offline } ?: return
        reverseCopies.request(listOf(asset), retryFailed = true)
        toast(
            if (reverseCopies.copies.value[asset.id] is ReverseCopy.Ready) {
                "已设为倒放"
            } else {
                "已设为倒放：正在后台生成倒放素材，完成后预览自动更新"
            },
        )
    }

    /** The neighbouring video track a picture clip can move to (above first), with the direction. */
    private fun otherVideoTrack(timeline: Timeline, clip: Clip): Pair<Boolean, Track>? {
        if (clip.kind == ClipKind.AUDIO) return null
        val video = timeline.tracks.filter { it.kind == TrackKind.VIDEO }
        val index = video.indexOfFirst { it.id == clip.trackId }
        if (index < 0) return null
        video.getOrNull(index - 1)?.let { return true to it }
        video.getOrNull(index + 1)?.let { return false to it }
        return null
    }

    private fun libraryItems(tab: LibraryTab, document: ProjectDocument): List<LibraryItemUi> = when (tab) {
        LibraryTab.MEDIA -> document.media.map { asset ->
            LibraryItemUi(asset.id, asset.name, if (asset.offline) "素材离线" else assetSubtitle(asset), asset.poster, assetFrames[asset.id], null, asset.flags)
        } + timelineItems(document)
        LibraryTab.TRANSITIONS -> TransitionType.entries.map {
            LibraryItemUi("transition:${it.name}", it.label, "时长 0.5 秒", PosterColors.NEUTRAL, null, null, art = it.art())
        }
        LibraryTab.TITLES -> TitleTemplate.entries.map {
            val detail = if (it == TitleTemplate.SUBTITLE) "2 秒 · 字幕轨" else "3 秒 · ${it.sizePx} px"
            LibraryItemUi("title:${it.name}", it.label, detail, PosterColors.NEUTRAL, null, null, art = it.art())
        }
        LibraryTab.EFFECTS -> EffectType.entries.map {
            LibraryItemUi("effect:${it.name}", it.label, "强度 50%", PosterColors.NEUTRAL, null, null, art = it.art())
        }
        // The marker index has rows of its own (EditUiState.markers).
        LibraryTab.MARKERS -> emptyList()
        LibraryTab.GENERATORS -> Generator.entries.map {
            LibraryItemUi("generator:${it.name}", it.label, it.detail, generatorPoster(it), generatorTile(it), null)
        }
    }

    /** `edit-index`: the open timeline's events as rows, the selected clip's picked. */
    private fun editIndexRows(document: ProjectDocument, filter: EditIndexFilter, selectedId: String?): List<EditIndexRowUi> {
        val fps = document.format.fps
        return editIndex.rows(document, filter).map { r ->
            EditIndexRowUi(
                clipId = r.clipId,
                number = String.format(Locale.US, "%03d", r.number),
                name = r.name,
                track = r.track,
                times = "${Timecode.format(r.recordIn, fps)} – ${Timecode.format(r.recordOut, fps)}",
                source = r.source,
                duration = formatSeconds(r.frames, fps),
                kind = r.kind,
                enabled = r.enabled,
                picked = r.clipId == selectedId,
            )
        }
    }

    /**
     * The project's other timelines after its media (Resolve's media pool holds both): compound clips' and the rest, each in
     * miniature — to edit in as a clip of the open timeline (a nested timeline). The open one is left out.
     */
    private fun timelineItems(document: ProjectDocument): List<LibraryItemUi> = timelinePool(document).filterNot { it.open }.map { card ->
        val badge = when {
            card.id in document.multicamIds -> "多机位"
            card.compound -> "复合片段"
            else -> "时间线"
        }
        LibraryItemUi(
            "$TIMELINE_ITEM${card.id}", card.name, "$badge · ${Timecode.format(card.durationFrames, card.format.fps)} · ${card.clips} 个片段",
            PosterColors.NEUTRAL, null, null, miniature = MiniatureUi(card.rows, card.bars, badge),
        )
    }

    /**
     * Timeline [id] onto the open one as a clip (Resolve's nested timelines; a compound clip again): appended at the end,
     * inserted at the playhead (the rest moving later) or over what is there — one undo step, the new clip selected.
     */
    private fun nestTimeline(id: String, mode: SourceEdit) {
        if (mode !in NESTABLE) return toast("时间线可以插入、覆盖或追加到时间线上")
        val s = _state.value
        var result: EditResult? = null
        var name = ""
        try {
            session.edit("${mode.label}时间线") { d ->
                val content = compounds.content(d, id)
                name = "${compounds.kindLabel(d, id)}“${d.storedTimeline(id)?.name}”"
                val fps = d.format.fps
                val r = when (mode) {
                    SourceEdit.INSERT -> clipboardEdits.pasteInsert(d.timeline, content, s.playheadFrame, fps)
                    SourceEdit.OVERWRITE -> clipboardEdits.paste(d.timeline, content, s.playheadFrame, fps)
                    else -> clipboardEdits.paste(d.timeline, content, d.timeline.durationFrames, fps)
                }
                result = r
                d.copy(timeline = r.timeline)
            }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法放入这条时间线")
        }
        val placed = result ?: return
        session.select(placed.selectedClipId)
        val at = doc?.timeline?.clip(placed.selectedClipId)?.start ?: 0
        toast("已把$name${mode.label}到 ${Timecode.format(at, s.fps)}")
    }

    /** The generators' library pictures, drawn once. */
    private val generatorTiles = mutableMapOf<Generator, Bitmap?>()
    private fun generatorTile(generator: Generator): Bitmap? = generatorTiles.getOrPut(generator) {
        Bitmap.createBitmap(generator.pixels(TILE_W, TILE_H), TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
    }

    /** The tile's colours where no picture is drawn: top, middle and bottom of the generator. */
    private fun generatorPoster(generator: Generator): PosterColors {
        val px = generator.pixels(TILE_W, TILE_H)
        fun at(row: Int) = px[row * TILE_W + TILE_W / 2].toLong() and 0xFFFFFFFFL
        return PosterColors(at(TILE_H / 6), at(TILE_H / 2), at(TILE_H * 5 / 6))
    }

    private fun assetSubtitle(asset: MediaAsset) = when (asset.kind) {
        MediaKind.IMAGE -> "图片 · 5 秒"
        MediaKind.AUDIO -> "音频 · ${asset.durationLabel}"
        MediaKind.VIDEO -> "视频 · ${asset.durationLabel}"
    }

    private fun loadAssetFrame(assetId: String) {
        val asset = doc?.asset(assetId) ?: return
        val color = doc?.colorManagement ?: ColorManagement.DEFAULT
        if (asset.kind == MediaKind.AUDIO || !requested.add("asset:$assetId:${asset.uri}:${timelineLookKey(asset, color)}")) return
        viewModelScope.launch {
            val at = if (asset.kind == MediaKind.VIDEO) minOf(1_000L, asset.durationMs / 2) else 0L
            previews.thumbnail(asset.uri, asset.kind, at, 320)?.let { raw ->
                val it = withContext(compute) { timelineLook(raw, asset, color) }
                assetFrames[assetId] = it
                if (_state.value.viewerMode == EditUiState.VIEWER_SOURCE && asset.kind == MediaKind.IMAGE) {
                    _state.update { s -> s.copy(sourceStill = it) }
                }
                publish()
            }
        }
    }

    private fun requestPreviews(document: ProjectDocument, timeline: Timeline) {
        // File by file, in file order (release round R17): the frame extractor reopens its decoder whenever the file
        // changes, and a timeline alternating between files reopened it for nearly every picture.
        for (clip in timeline.clips.sortedWith(compareBy({ document.asset(it.assetId)?.uri.orEmpty() }, { it.sourceInMs }))) {
            val asset = document.asset(clip.assetId) ?: continue
            when (clip.kind) {
                ClipKind.VIDEO -> {
                    // The asset's file is part of the key: a replace keeps the clip's id but changes its media, a relink its file.
                    val look = timelineLookKey(asset, document.colorManagement)
                    // Clips showing the same file within the same second share one picture (release round R17): a
                    // 30-minute timeline of 900 clips decoded and drew 900 of them, and the system killed the app at 2.7 GB.
                    val at = clip.sourceInMs / THUMB_STEP_MS * THUMB_STEP_MS + THUMB_OFFSET_MS
                    val shared = "${asset.uri}:$at:$look"
                    if (!requested.add("clip:${clip.id}:$shared")) continue
                    val ready = sharedThumbs[shared]
                    if (ready != null) {
                        thumbs[clip.id] = ready
                        continue
                    }
                    // Every clip waiting for this picture gets it at once, and the page redraws once for them all: a
                    // redraw per clip (900 on a 30-minute timeline) grew the app by 2 GB while the pictures came in.
                    val waiting = waitingThumbs.getOrPut(shared) { mutableListOf() }
                    waiting += clip.id
                    if (waiting.size > 1) continue
                    viewModelScope.launch {
                        val picture = previews.thumbnail(asset.uri, asset.kind, at, 192)
                            ?.let { raw -> withContext(compute) { timelineLook(raw, asset, document.colorManagement) } }
                        val clips = waitingThumbs.remove(shared).orEmpty()
                        if (picture != null) {
                            sharedThumbs[shared] = picture
                            clips.forEach { thumbs[it] = picture }
                            publish()
                        }
                    }
                }
                ClipKind.AUDIO -> {
                    if (!requested.add("wave:${asset.id}:${asset.uri}")) continue
                    viewModelScope.launch {
                        previews.waveform(asset.uri)?.let {
                            waves[asset.id] = it
                            publish()
                        }
                    }
                }
                ClipKind.TITLE -> Unit
            }
        }
    }

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    /** The line under the mask's sliders: what it does, how it moves, or why it cannot change here. */
    private fun maskHint(clip: Clip, inside: Boolean): String {
        val mask = clip.mask ?: return "选择形状后，片段只在形状内显示，形状外露出下层轨道"
        val n = mask.keyframes.size
        return when {
            n == 0 -> "在监视器上拖动可移动遮罩 · 点菱形按钮添加关键帧，让遮罩随时间移动"
            !inside -> "播放头不在片段内：移进片段再调整遮罩"
            else -> "$n 个关键帧 · 拖动或调整会记在播放头处"
        }
    }

    private companion object {
        const val TAG = "PrismEdit"
        /** Points the stabilization diagram draws across a clip. */
        const val DIAGRAM_POINTS = 120
        /** A generator tile's picture, 16:9. */
        const val TILE_W = 160

        /** `multicam-angle-tile` frames: one a second of each angle, this wide. */
        const val ANGLE_FRAME_MS = 1_000L
        /** Timeline pictures are taken on whole seconds of the file (a little in, past a black first frame), so clips share them. */
        const val THUMB_STEP_MS = 1_000L
        const val THUMB_OFFSET_MS = 200L
        const val ANGLE_FRAME_WIDTH = 240

        /** A library tile's id prefix for a timeline of the project (rev 146). */
        const val TIMELINE_ITEM = "timeline:"

        /** The edits a timeline goes onto another with. */
        val NESTABLE = setOf(SourceEdit.INSERT, SourceEdit.OVERWRITE, SourceEdit.APPEND)
        const val TILE_H = 90
        /** EDLs go out as plain bytes, so MediaStore keeps the .edl name. */
        const val EDL_MIME = "application/octet-stream"
        /** What the clip EQ sheet says it shapes. */
        const val CLIP_EQ_APPLIES_TO = "只作用于这个片段，在轨道的均衡器之前"
        /** Large enough for a feature film's subtitles, small enough to refuse a wrong pick quickly. */
        const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024
        /** An FCPXML of a long edit with many media runs to a few megabytes. */
        const val MAX_FCPXML_BYTES = 8 * 1024 * 1024
        /** A repeated trim refusal (the dial keeps turning at a limit) is shown again only after this pause. */
        const val TRIM_REFUSAL_QUIET_MS = 2_000L
        const val NOTHING_TO_DELETE = "请先在时间线上选择片段，或标记入点和出点"
        /** The width the keyer's eyedropper reads a frame at: enough for a screen's colour, quick to read. */
        const val KEY_PICK_WIDTH = 192
    }
}

/** The library tile's illustration of a transition (the design's glyphs). */
internal fun TransitionType.art(): LibraryArt = when (this) {
    TransitionType.CROSS_DISSOLVE -> LibraryArt.DISSOLVE
    TransitionType.DIP_TO_BLACK -> LibraryArt.DIP_BLACK
    TransitionType.DIP_TO_WHITE -> LibraryArt.DIP_WHITE
    TransitionType.PUSH -> LibraryArt.PUSH
    TransitionType.WIPE -> LibraryArt.WIPE
    TransitionType.ZOOM_BLUR -> LibraryArt.ZOOM
    TransitionType.IRIS -> LibraryArt.IRIS
    TransitionType.CLOCK_WIPE -> LibraryArt.CLOCK
    TransitionType.BARN_DOOR -> LibraryArt.BARN
    TransitionType.BLUR_DISSOLVE -> LibraryArt.BLUR_DISSOLVE
    TransitionType.DIP_TO_COLOR -> LibraryArt.DIP_COLOR
}

internal fun TitleTemplate.art(): LibraryArt = when (this) {
    TitleTemplate.CENTER -> LibraryArt.TITLE_CENTER
    TitleTemplate.LOWER_THIRD -> LibraryArt.LOWER_THIRD
    TitleTemplate.ROLL -> LibraryArt.ROLL
    TitleTemplate.CREDITS -> LibraryArt.CREDITS
    TitleTemplate.SUBTITLE -> LibraryArt.SUBTITLE
}

internal fun EffectType.art(): LibraryArt = when (this) {
    EffectType.GAUSSIAN_BLUR -> LibraryArt.BLUR
    EffectType.SHARPEN -> LibraryArt.SHARPEN
    EffectType.VIGNETTE -> LibraryArt.VIGNETTE
    EffectType.FILM_GRAIN -> LibraryArt.GRAIN
    EffectType.MIRROR -> LibraryArt.MIRROR
    EffectType.MOSAIC -> LibraryArt.MOSAIC
    EffectType.CHROMATIC -> LibraryArt.CHROMA
    EffectType.GLOW -> LibraryArt.GLOW
}
