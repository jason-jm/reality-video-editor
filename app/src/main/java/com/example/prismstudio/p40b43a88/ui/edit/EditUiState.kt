package com.example.prismstudio.p40b43a88.ui.edit

import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.ui.components.TrackUi
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionScope
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.ui.audio.EqEditorUi
import com.example.prismstudio.p40b43a88.ui.color.PickOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.model.KeyControl
import com.example.prismstudio.p40b43a88.domain.model.KeyScreen
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.EqBand
import com.example.prismstudio.p40b43a88.domain.model.EqControl
import com.example.prismstudio.p40b43a88.ui.components.LibraryArt
import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.usecase.EditIndexFilter
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.SubtitleColor
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyleControl
import com.example.prismstudio.p40b43a88.domain.model.TitleAnimation
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.domain.usecase.ClipAttribute
import com.example.prismstudio.p40b43a88.domain.usecase.MulticamUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.LibraryTab
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.usecase.LinkAction
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange
import com.example.prismstudio.p40b43a88.ui.components.TimelineUi

data class LibraryItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val poster: PosterColors,
    val frame: Bitmap?,
    /** Short visual label drawn on the tile for non-media items (transition / title / effect). */
    val glyph: String?,
    /** Resolve's flags on the media (`flags` part on the tile). */
    val flags: List<FlagColor> = emptyList(),
    /** The illustration a transition, title or effect tile shows (the design's glyph); null for media. */
    val art: LibraryArt? = null,
    /**
     * A timeline of the project in the media tab (rev 146, Resolve's nested timelines): its tracks in miniature and what
     * it is (复合片段 / 时间线); null for media.
     */
    val miniature: MiniatureUi? = null,
)

/** A timeline drawn small ([com.example.prismstudio.p40b43a88.ui.components.TimelineMiniature]) with its badge. */
data class MiniatureUi(val rows: Int, val bars: List<com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase.Bar>, val badge: String)

/**
 * A clip's speed points (Resolve's Retime Controls): its sections' speeds in order (empty without speed points), the one
 * under the playhead (1-based, 0 = none), whether a point can be added there or one removed, and what to know. The ramp
 * row (`speed-ramp-row`) smooths the point nearest the playhead ([rampPoint], 1-based): its ramp and the longest it can
 * take, in seconds (0 longest = it cannot have one), what the value reads and what the ramp does.
 */
data class SpeedPointsUi(
    val sections: List<String> = emptyList(),
    val current: Int = 0,
    val canAdd: Boolean = false,
    val canRemove: Boolean = false,
    val hint: String = "",
    val rampPoint: Int = 0,
    val rampSeconds: Float = 0f,
    val rampMaxSeconds: Float = 0f,
    val rampText: String = "",
    val rampNote: String = "",
)

/** An applied effect: its amount at the playhead, and its keyframes (`effect-keyframe-row`). */
data class EffectRowUi(val id: String, val name: String, val enabled: Boolean, val amount: Int, val keys: KeyframeUi = KeyframeUi())

/** Inspector values of the selected clip. */
/** A row of the marker index. */
/** `edit-index` row: an edit event — its number, clip, track, record in–out, source and length, picked when its clip is selected. */
data class EditIndexRowUi(
    val clipId: String,
    val number: String,
    val name: String,
    val track: String,
    val times: String,
    val source: String,
    val duration: String,
    val kind: ClipKind,
    val enabled: Boolean,
    val picked: Boolean,
)

data class MarkerUi(
    val id: String,
    val label: String,
    val timecode: String,
    val color: MarkerColor,
    val note: String,
    /** `marker-range-row`: a duration marker's length (seconds, 0 = a point), its longest, and where it runs ("到 00:00:16:00"). */
    val durationSeconds: Float = 0f,
    val maxDurationSeconds: Float = 0f,
    val rangeText: String = "一点",
    /** The timeline has an In and an Out to take the range from. */
    val canTakeMarks: Boolean = false,
)

data class InspectorUi(
    val clipId: String,
    val kind: ClipKind,
    val name: String,
    val locked: Boolean,
    /** Off (Disable Clip): the inspector says so and offers 启用. */
    val enabled: Boolean = true,
    /** A subtitle's track style and track name (`subtitle-style-block`); null for other clips. */
    val subtitleStyle: SubtitleStyle? = null,
    val subtitleTrackName: String = "",
    val titleText: String,
    val titleSize: Int,
    /** A title's look (`title-style-block`) and what its entrance does; null for subtitles and other clips. */
    val titleStyle: TitleStyle? = null,
    val titleAnimationNote: String = "",
    /** A subtitle: text and size only (it sits at the bottom of the frame), and a quick 下一条字幕. */
    val isSubtitle: Boolean = false,
    val scale: Int,
    val positionX: Int,
    val positionY: Int,
    val rotation: Int,
    val opacity: Int,
    /** The crop as shown (`crop-section`): one value per [CropControl], in its order. */
    val crop: List<Int> = List(CropControl.entries.size) { 0 },
    /**
     * `mask-section`: the picture's mask as it stands at the playhead (null = none), its keyframe button, whether a keyed
     * mask cannot change here (the playhead outside the clip), and the line under its sliders.
     */
    val mask: PowerWindow? = null,
    val maskKey: ParamKeyState = ParamKeyState.NONE,
    val maskBlocked: Boolean = false,
    val maskHint: String = "",
    /** `mask-track-row`: Resolve's Tracker on the mask — why it cannot run, and a run's progress. */
    val maskTrack: TrackUi = TrackUi(),
    /** `mask-overlay`: the mask on the monitor (dragged to move it) while the playhead is in the clip; null otherwise. */
    val maskOverlay: WindowOverlayUi? = null,
    /** Resolve's Dynamic Zoom (`zoom-section`); null = off. */
    val dynamicZoom: DynamicZoom? = null,
    /** Resolve's Composite Mode of the picture (`composite-mode-button`). */
    val composite: CompositeMode = CompositeMode.NORMAL,
    /**
     * `picture-fade-rows`: the picture's own fade handles (seconds), apart from its sound's, and the longest they go;
     * false for subtitles, which have none.
     */
    val pictureFades: Boolean = false,
    val pictureFadeIn: Float = 0f,
    val pictureFadeOut: Float = 0f,
    val maxPictureFade: Float = 0f,
    /** `key-section`: Resolve's keyer — whether the clip can have one (footage and stills), and its key (null = off). */
    val keyable: Boolean = false,
    val chromaKey: ChromaKey? = null,
    /** `key-eyedropper-chip` armed, and `key-matte-chip`: the monitor shows the key's matte (Resolve's highlight). */
    val keyPicking: Boolean = false,
    val keyMatte: Boolean = false,
    /** `picture-fade-curve-rows`: how the picture's fades bend, −1…1 ([ClipFades.shape]). */
    val pictureFadeInCurve: Float = 0f,
    val pictureFadeOutCurve: Float = 0f,
    /** Clip whose volume / fades the audio tab edits (itself or its linked audio); null = none. */
    val audioClipId: String?,
    val volumeDb: Float,
    /** `clip-pan-row`: the sound's pan at the playhead, −1…1, as the row writes it (左 30 / 居中 / 右 30). */
    val pan: Float = 0f,
    val panText: String = "居中",
    /** `clip-pan-key-button`: its keyframe diamond; a keyed pan is adjusted at the playhead, which must be inside the clip. */
    val panKey: ParamKeyState = ParamKeyState.NONE,
    val panBlocked: Boolean = false,
    val fadeIn: Float,
    val fadeOut: Float,
    val maxFade: Float,
    /** `fade-curve-rows`: how the sound's fades bend, −1…1. */
    val fadeInCurve: Float = 0f,
    val fadeOutCurve: Float = 0f,
    /** `clip-eq-row`: what the sound's own equaliser does ([eqSummary]) and whether it changes the sound. */
    val eqSummary: String = "平直",
    val eqActive: Boolean = false,
    /** `clip-channels-row`: how the sound's left and right channels reach the mix, and whether its file has two to map. */
    val channels: AudioChannels = AudioChannels.STEREO,
    val channelsNote: String = "",
    /** `clip-pitch-row`: the sound's pitch shift in semitones (cents / 100) and what it does. */
    val pitchSemitones: Float = 0f,
    val pitchNote: String = "",
    /** `cross-fade-row`: the sound clip's cross fade (frames), its longest, whether a sound ends where it starts, and what it does. */
    val crossFadeFrames: Int = 0,
    val maxCrossFadeFrames: Int = 0,
    val canCrossFade: Boolean = false,
    val crossFadeNote: String = "",
    val crossFadeText: String = "0.00 秒",
    /** Index of the clip's speed in [PlaybackSpeed.entries]; -1 for a speed between the presets (Fit to Fill). */
    val speedIndex: Int,
    /** The clip's speed as shown: "1×", "1.33×". */
    val speedLabel: String = "1×",
    val reverse: Boolean,
    /** Resolve's speed points (`speed-points-block`). */
    val speedPoints: SpeedPointsUi = SpeedPointsUi(),
    val effects: List<EffectRowUi>,
    /** Index in [Placement.entries] when the picture sits exactly in a preset, else -1. */
    val placementIndex: Int = -1,
    /** Where "move to the other track" would take the clip ("移到 V2"), or null when it cannot move. */
    val trackMoveLabel: String? = null,
    val trackMoveUp: Boolean = true,
    /** The clip's opening transition, if any. */
    val transitionName: String? = null,
    val transitionSeconds: Float = 0f,
    val transitionMaxSeconds: Float = 0f,
    /** Why the transition plays shorter than set or reveals the layers beneath (`transition-handle-note`); null when it plays as set. */
    val transitionNote: String? = null,
    /** The opening transition as the inspector shapes it — kind, ease, direction, edge and border; null without one. */
    val transition: Transition? = null,
    /** `stabilize-section`: Resolve's Stabilization for the selected clip; null for a title. */
    val stabilization: StabilizationUi? = null,
    /** Progress or failure of the reversed copy a reversed clip plays; null once it is ready. */
    val reverseStatus: String? = null,
    val reverseFailed: Boolean = false,
    /** Keyframes of the transform (视频) and of the volume (音频). */
    val transformKeys: KeyframeUi = KeyframeUi(),
    /** Each transform group's own keyframe button (Resolve's diamond beside a parameter) at the playhead. */
    val paramKeys: Map<TransformKeyGroup, ParamKeyState> = emptyMap(),
    val volumeKeys: KeyframeUi = KeyframeUi(),
    /** A video clip whose source can be read: it can hold a frame (`freeze-frame-button`). */
    val canFreeze: Boolean = false,
    /** Resolve's Retime Process of the picture (`retime-row`). */
    val retime: RetimeProcess = RetimeProcess.NEAREST,
    /** A clip of video footage: it has frames to repeat or blend (a still has none). */
    val canRetime: Boolean = false,
    /**
     * Resolve's Scaling of the picture (`clip-scaling-row`), null while it follows the project's setting, and what it
     * does to this clip (`clip-scaling-note`).
     */
    val scaling: ClipScaling? = null,
    val scalingNote: String = "",
    /** A compound clip: `compound-panel` instead of the tabs. */
    val compound: CompoundUi? = null,
    /** A multicam clip: `multicam-panel` instead of the tabs. */
    val multicam: MulticamUi? = null,
) {
    val isTitle: Boolean get() = kind == ClipKind.TITLE
    val hasPicture: Boolean get() = kind != ClipKind.AUDIO

    /** Any crop setting away from none: 重置 has something to clear. */
    val hasCrop: Boolean get() = crop.any { it != 0 }
    val hasAudio: Boolean get() = audioClipId != null
    val canChangeSpeed: Boolean get() = kind != ClipKind.TITLE

    /** `retime-note`: what the chosen process does with the frames the footage lacks. */
    val retimeNote: String
        get() = when {
            !canRetime -> "图片没有可以补出的中间帧"
            retime == RetimeProcess.FRAME_BLEND -> "慢放和低帧率素材缺的帧由前后两帧按时间混合，动作更连贯"
            retime == RetimeProcess.OPTICAL_FLOW -> "按前后两帧之间的运动补出中间帧：运动的物体清晰不重影；遮挡处改为混合"
            else -> "慢放和低帧率素材缺的帧重复前一帧"
        }
}

/** `curve-param-row`: one curve of the clip — its name, its value at the playhead, its key count; [selected] on the graph. */
data class CurveRowUi(val key: String, val label: String, val value: String, val keyCount: Int, val animated: Boolean, val selected: Boolean)

/**
 * `keyframe-curve-panel`: the selected clip's curves ([rows]) and the one on the graph ([curve]), the clip's place and
 * length (the graph spans it), the selected key and what the toolbar can do; [hint] stands in for all of it without a clip.
 */
data class CurvesUi(
    val clipName: String = "",
    val rows: List<CurveRowUi> = emptyList(),
    val curve: com.example.prismstudio.p40b43a88.domain.model.KeyCurve? = null,
    val clipStart: Int = 0,
    val durationFrames: Int = 0,
    val fps: Int = 30,
    val selectedKey: Int? = null,
    val readout: String = "",
    /** The selected key's ease preset (null: none selected, or slopes of its own). */
    val ease: com.example.prismstudio.p40b43a88.domain.model.CurveEase? = null,
    val canEase: Boolean = false,
    val canAdd: Boolean = false,
    val canDelete: Boolean = false,
    /** Only for the transform's curves: a key's time and easing belong to the whole transform. */
    val sharedNote: String? = null,
    val hint: String? = null,
)

data class EditUiState(
    /** The program monitor shows a flattened approximation of composited overlays / transitions. */
    val previewApproximate: Boolean = false,
    /** Picture layers the program monitor stacks. */
    val programLayers: Int = 1,
    /** Each program layer's composite mode at the playhead (Resolve's Composite Mode), bottom first. */
    val programComposites: List<CompositeMode> = emptyList(),
    val libraryTab: LibraryTab = LibraryTab.MEDIA,
    val libraryItems: List<LibraryItemUi> = emptyList(),
    val libraryItemId: String? = null,
    /**
     * The 索引 tab (rev 148): which list shows — [INDEX_EDITS] (Resolve's Edit Index) or [INDEX_MARKERS] — and the edit
     * index's filter, events and summary.
     */
    val indexView: Int = INDEX_EDITS,
    val indexFilter: EditIndexFilter = EditIndexFilter.ALL,
    val editIndex: List<EditIndexRowUi> = emptyList(),
    val editIndexSummary: String = "",
    /** `marker-index`: the timeline's markers in time order, and the one picked to edit. */
    val markers: List<MarkerUi> = emptyList(),
    val selectedMarkerId: String? = null,
    val viewerMode: Int = VIEWER_TIMELINE,
    val inspectorTab: Int = 0,
    val inspector: InspectorUi? = null,
    val timeline: TimelineUi = TimelineUi(),
    val summary: String = "",
    /** Timeline In / Out are set: the timeline header offers 清除入出点. */
    val hasMarks: Boolean = false,
    /**
     * Both marks are set: the header offers 删除区间 / 波纹删除区间, and with no clip selected Backspace / Delete
     * (and the deck's ripple delete) act on the range, as in Resolve.
     */
    val hasRange: Boolean = false,
    /** Fewer than the most tracks of each kind: `add-video-track-button` / `add-audio-track-button` are enabled. */
    val canAddVideoTrack: Boolean = true,
    val canAddAudioTrack: Boolean = true,
    /** Something was copied or cut: the clip menu's 粘贴 works. */
    val canPaste: Boolean = false,
    /** Footage clips (a video file played forward without speed points): their menu offers 检测场景剪切. */
    val footageClipIds: Set<String> = emptySet(),
    /** Searching a clip for its scene cuts (the menu item waits). */
    val sceneCutting: Boolean = false,
    /** The picked gap's length ("1.5 秒"), for the empty lane menu's 删除空隙; null when no gap is picked. */
    val gapText: String? = null,
    /** What the clip menu's link item (`clip-menu-link`) and Ctrl+Alt+L do with the selection, or null. */
    val linkAction: LinkAction? = null,
    /** The open `paste-attributes-sheet`, or null. */
    val pasteAttributes: PasteAttributesUi? = null,
    /** `eq-sheet` for the selected sound clip's own equaliser (Resolve's clip EQ); null while closed. */
    val clipEq: EqEditorUi? = null,
    /** `key-pick-overlay`: while the keyer's eyedropper is on, a tap on the monitor picks the screen's colour; else null. */
    val keyPick: PickOverlayUi? = null,
    /** `source-marks-row`: the source viewer's marks on its media ("入点 … · 出点 … · 3.0 秒"); null while it is unmarked. */
    val sourceMarks: String? = null,
    /** The open `timeline-settings-sheet`, or null. */
    val timelineSettings: TimelineSettingsUi? = null,
    /** `rename-sheet`: a clip's or a track's name being edited, or null. */
    val rename: RenameUi? = null,
    /** `clip-duration-sheet`: the selected clip's length being set (Ctrl+D), or null. */
    val clipDuration: ClipDurationUi? = null,
    /** `timecode-entry-sheet`: a timecode being typed, or null. */
    val timecodeEntry: TimecodeEntryUi? = null,
    /** The open timeline's name (`timeline-menu-button`) and every timeline of the project (`timeline-menu`). */
    val timelineName: String = FIRST_TIMELINE_NAME,
    val timelineEntries: List<TimelineEntryUi> = emptyList(),
    /**
     * `compound-trail`: inside a compound clip, the timelines it was opened from, outermost first (the open one is
     * [timelineName]); `compound-return-chip` goes back to the last. Empty on a timeline of the project.
     */
    val compoundTrail: List<String> = emptyList(),
    /** The timeline's 多选 mode: a tap adds a clip to the selection or takes it out (for hands and rays). */
    val multiSelect: Boolean = false,
    /** `curve-mode-chip`: the timeline panel shows the selected clip's keyframe curves instead of the tracks. */
    val curveMode: Boolean = false,
    /** `keyframe-curve-panel` while [curveMode] is on. */
    val curves: CurvesUi? = null,
    /** Several clips selected: the inspector shows `multi-selection-panel` instead of one clip's properties. */
    val selection: SelectionUi? = null,
    val snapping: Boolean = true,
    /** What the deck's jog dial does on the Edit stage: move the playhead, slip or roll (Speed Editor modes). */
    val jogMode: Int = JOG_PLAYHEAD,
    /** The dial's readout in slip / roll mode (source in point or cut position); empty in playhead mode. */
    val jogReadout: String = "",
    val playheadFrame: Int = 0,
    val fps: Int = 30,
    val playing: Boolean = false,
    val pictureAspect: Float = 16f / 9f,
    val programEmpty: Boolean = true,
    val programMessage: String? = null,
    val sourceName: String? = null,
    val sourceIsVideo: Boolean = false,
    val sourceStill: Bitmap? = null,
    val sourceAspect: Float = 16f / 9f,
    val sourcePositionFrames: Int = 0,
    val sourcePlaying: Boolean = false,
    val viewportDp: Float = 0f,
    val toast: String? = null,
) {
    val nothingSelected: Boolean get() = inspector == null
    val programTimecode: String
        get() = Timecode.format(if (viewerMode == VIEWER_SOURCE) sourcePositionFrames else playheadFrame, fps)
    val libraryActionLabel: String get() = libraryTab.actionLabel
    val libraryActionEnabled: Boolean
        get() = libraryTab == LibraryTab.MARKERS || libraryItemId != null && when (libraryTab) {
            LibraryTab.MEDIA, LibraryTab.TITLES, LibraryTab.GENERATORS -> true
            LibraryTab.TRANSITIONS, LibraryTab.EFFECTS -> inspector?.hasPicture == true && inspector.locked.not()
            LibraryTab.MARKERS -> true
        }
    /** The marker picked in the index, if it still exists. */
    val selectedMarker: MarkerUi? get() = markers.firstOrNull { it.id == selectedMarkerId }
    val speeds: List<PlaybackSpeed> get() = PlaybackSpeed.entries

    companion object {
        const val VIEWER_SOURCE = 0
        const val VIEWER_TIMELINE = 1

        /** The 索引 tab's lists (rev 148). */
        const val INDEX_EDITS = 0
        const val INDEX_MARKERS = 1
        const val TAB_VIDEO = 0
        const val TAB_AUDIO = 1
        const val TAB_EFFECTS = 3
        const val JOG_PLAYHEAD = 0
        const val JOG_SLIP = 1
        const val JOG_ROLL = 2
        const val JOG_SLIDE = 3
        val JOG_MODES = listOf("播放头", "滑移", "滚动", "滑动")
    }
}

sealed interface EditEvent {
    data class SelectLibraryTab(val index: Int) : EditEvent
    data class SelectLibraryItem(val id: String) : EditEvent
    data object ApplyLibraryItem : EditEvent
    /**
     * The selected media-pool item, whole, onto the timeline at the playhead (F9 / F10 / F12) or its end, or
     * in place of the clip there (F11).
     */
    data class EditFromSource(val mode: SourceEdit) : EditEvent

    /**
     * `timeline` part markerDrag: marker [id]'s flag dragged along the ruler to [frame], or (with [end]) its duration bar's
     * end to there — shown as it goes, one undo step when let go ([commit]).
     */
    data class MarkerDrag(val id: String, val frame: Int, val end: Boolean, val commit: Boolean) : EditEvent

    /** `source-marks-row`'s 清除: the source viewer's media edits whole again. */
    data object ClearSourceMarks : EditEvent

    /**
     * `match-frame-button` (F): Resolve's Match Frame — the clip under the playhead opens in the source viewer at that frame;
     * from a clip's menu ([clipId]) that clip, at the playhead when it is over it, else at its first frame.
     */
    data class MatchFrame(val clipId: String? = null) : EditEvent

    /** Ctrl+T (Alt+T picture, Shift+T sound): the standard transition at the selected clips' ends, or the cut nearest the playhead. */
    data class AddTransitions(val scope: TransitionScope) : EditEvent

    /** Shift+[ / ] (Ctrl: ripple): the start or end of the clips under the playhead (the selected ones first) trimmed to it. */
    data class TrimToPlayhead(val edge: TrimEdge, val ripple: Boolean) : EditEvent

    /** A tap on an empty lane: the gap on [trackId] at [frame] is picked (the clip selection cleared); none there clears it all. */
    data class SelectGap(val trackId: String, val frame: Int) : EditEvent

    /** The picked gap closed (also Delete / Backspace while it is picked). */
    data object DeleteGap : EditEvent

    /** Resolve's Delete Gaps: every gap that can close. */
    data object DeleteAllGaps : EditEvent

    /** Ctrl+Shift+, / . (Resolve's Swap Clips): the selected clip changes places with its neighbour before / after it. */
    data class SwapClip(val right: Boolean) : EditEvent

    /** `clip-menu-rename`: the selected clip's (or [clipId]'s) name in the rename sheet. */
    data class OpenRenameClip(val clipId: String? = null) : EditEvent

    /** A press held on a track's name in its header: its name in the rename sheet. */
    data class OpenRenameTrack(val trackId: String) : EditEvent
    data class RenameText(val text: String) : EditEvent
    data object ConfirmRename : EditEvent
    data object CloseRename : EditEvent

    /** Ctrl+D / `clip-menu-duration`: Resolve's Change Clip Duration on the selected (or [clipId]) clip. */
    data class OpenClipDuration(val clipId: String? = null) : EditEvent
    data class ClipDurationText(val text: String) : EditEvent
    data class ClipDurationRipple(val ripple: Boolean) : EditEvent
    data object ConfirmClipDuration : EditEvent
    data object CloseClipDuration : EditEvent

    /** Tapping the viewer's timecode, or + / − (prefilled): Resolve's typed timecode. */
    data class OpenTimecodeEntry(val prefill: String = "") : EditEvent
    data class TimecodeEntryText(val text: String) : EditEvent
    data object ConfirmTimecodeEntry : EditEvent
    data object CloseTimecodeEntry : EditEvent

    /** `timeline-menu-export-edl`: Resolve's Export → Timeline → EDL. */
    data object ExportEdl : EditEvent
    /** Resolve's Export → Timeline → FCPXML: the open timeline as FCPXML 1.10 in Download/Reality Video Editor. */
    data object ExportFcpxml : EditEvent

    /** `timeline-menu-import-edl`: the picked EDL as a new timeline. */
    data class ImportEdl(val uri: String) : EditEvent
    /** Resolve's Import → Timeline → FCPXML: the picked file's first sequence as a new timeline, opened. */
    data class ImportFcpxml(val uri: String) : EditEvent
    /** `track-target-chip`: track [trackId] becomes the picture / sound destination (the sound one again: automatic). */
    data class ToggleTarget(val trackId: String) : EditEvent
    /** Timeline In / Out at the playhead (I / O, the deck's mark keys); Alt+X or 清除入出点 clears them. */
    data object MarkIn : EditEvent
    data object MarkOut : EditEvent
    data object ClearMarks : EditEvent
    /** Resolve's Mark Clip (X): the timeline's In / Out around the selected clip, else the one under the playhead. */
    data object MarkClip : EditEvent
    /** Resolve's clip colour on the selected clips (null: back to their kind's), and every clip of a colour selected. */
    data class SetClipColor(val color: ClipColor?) : EditEvent
    data class SelectClipColor(val color: ClipColor) : EditEvent
    /** Cuts the In–Out range out of every unlocked track: leaving the gap (lift), or closing it (extract). */
    data object LiftRange : EditEvent
    data object ExtractRange : EditEvent
    data class RequestLibraryPreview(val id: String) : EditEvent
    data class ViewerMode(val mode: Int) : EditEvent
    data class InspectorTab(val index: Int) : EditEvent
    data class SelectClip(val clipId: String?) : EditEvent
    /** Adds the clip to the selection or takes it out (a tap with Ctrl / ⌘ held, or in 多选 mode). */
    data class ToggleSelected(val clipId: String) : EditEvent
    /** The timeline's 多选 mode on or off. */
    data class MultiSelect(val on: Boolean) : EditEvent
    /** A marquee dragged over empty lanes (多选 mode, or Ctrl / ⌘ / Shift held): the clips it touches join the selection. */
    data class SelectInMarquee(val from: Int, val until: Int, val trackIds: Set<String>) : EditEvent
    /** Every clip on the timeline (Ctrl / ⌘+A). */
    data object SelectAll : EditEvent
    /** Every clip from [clipId]'s start on (the clip menu), or from the playhead (Y), on all tracks. */
    data class SelectForward(val clipId: String?) : EditEvent
    data class Seek(val frame: Int) : EditEvent
    data class Move(val clipId: String, val start: Int, val trackId: String, val commit: Boolean) : EditEvent
    data class Trim(val clipId: String, val edge: TrimEdge, val frame: Int, val commit: Boolean) : EditEvent
    /** A volume line drag on the timeline (see [com.example.prismstudio.p40b43a88.ui.components.TimelineActions.volume]). */
    data class VolumeLine(val clipId: String, val keyFrames: List<Int>, val deltaDb: Float, val commit: Boolean) : EditEvent
    data object CancelDrag : EditEvent
    data class Zoom(val zoom: Float) : EditEvent
    data object FitTimeline : EditEvent
    data class Viewport(val widthDp: Float) : EditEvent
    data object Split : EditEvent
    data object RippleDelete : EditEvent

    /** `clip-menu-detect-scene-cuts`: Resolve's Detect Scene Cuts — cut [clipId] (and its linked sound) where its shots change. */
    data class DetectSceneCuts(val clipId: String) : EditEvent

    /** `selection-align-button`: line the selected clips up with the one tapped last, by their sound. */
    data object AlignByWaveform : EditEvent

    /** `index-view-segment`: the 索引 tab's list — edits ([EditUiState.INDEX_EDITS]) or markers. */
    data class IndexView(val view: Int) : EditEvent

    /** `edit-index-filter`: which clips the edit index lists. */
    data class IndexFilter(val filter: EditIndexFilter) : EditEvent

    /** `edit-index` row: the playhead to the event's start, its clip selected. */
    data class PickIndexRow(val clipId: String) : EditEvent

    /** `clip-channels-row`: the selected sound's channel mapping (Resolve's clip attributes). */
    data class Channels(val channels: AudioChannels) : EditEvent

    /** `clip-pitch-row`: the selected sound's pitch, in semitones (Resolve's clip pitch). */
    data class Pitch(val semitones: Float) : EditEvent

    /** `selection-compound-button` / `clip-menu-new-compound`: Resolve's New Compound Clip from the selected clips. */
    data object CreateCompound : EditEvent

    /** `selection-multicam-button` / `clip-menu-new-multicam`: Resolve's New Multicam Clip from the selected camera clips. */
    data object CreateMulticam : EditEvent

    /** `multicam-angle-tile`: angle [index] — cut to it at the playhead, or switch the segment (by the panel's settings). */
    data class SwitchAngle(val index: Int) : EditEvent

    /** `multicam-mode-segment`: what a switch changes ([MulticamUseCase.SwitchMode] ordinal). */
    data class MulticamMode(val mode: Int) : EditEvent

    /** `multicam-whole-chip`: switch the whole segment under the playhead instead of cutting there. */
    data class MulticamWhole(val on: Boolean) : EditEvent

    /** `compound-open-button` / `clip-menu-open-compound`: edit compound clip [clipId]'s timeline. */
    data class OpenCompound(val clipId: String) : EditEvent

    /** `compound-decompose-button` / `clip-menu-decompose`: Resolve's Decompose in Place. */
    data class DecomposeCompound(val clipId: String) : EditEvent

    /** `compound-return-chip`: back to the timeline the open compound clip was opened from. */
    data object LeaveCompound : EditEvent
    /** Resolve's Copy / Cut: the selected clip with its linked partner onto the clipboard (Cut lifts it). */
    data object CopyClip : EditEvent
    data object CutClip : EditEvent

    /** Resolve's Ripple Cut (Ctrl+Shift+X): cut, closing the gaps. */
    data object RippleCut : EditEvent
    /** The clipboard at the playhead, over what is there; the playhead moves past it. */
    data object PasteClips : EditEvent

    /** Resolve's Paste Insert (Ctrl+Shift+V): paste at the playhead, the rest of the timeline moving later. */
    data object PasteInsert : EditEvent
    /** A copy of the selected clip right after it. */
    data object DuplicateClip : EditEvent
    /** Lifts the selected clip off the timeline and leaves its gap (Ripple Delete closes it). */
    data object DeleteClip : EditEvent
    /** Holds the frame at the playhead for two seconds inside the selected clip; the rest moves later. */
    data object FreezeFrame : EditEvent
    /** Resolve's Disable Clip (D) on the selected clip and its linked partner, or back. */
    data object ToggleClipEnabled : EditEvent
    /** Resolve's Link Clips (Ctrl+Alt+L): the selection unlinked, or one picture and one sound clip linked. */
    data object ToggleLink : EditEvent
    /** The selected subtitle's track style: a slider, the colour, a switch, or back to the defaults. */
    data class SubtitleStyleValue(val control: SubtitleStyleControl, val value: Float) : EditEvent
    data class SubtitleStyleColor(val color: SubtitleColor) : EditEvent
    data class SubtitleStyleFlag(val flag: SubtitleFlag, val on: Boolean) : EditEvent
    data object ResetSubtitleStyle : EditEvent
    /** The selected title's look: colour, weight, a switch, the box's opacity, the entrance, or back to its template's. */
    data class TitleStyleColor(val color: TitleColor) : EditEvent
    data class TitleStyleWeight(val weight: TitleWeight) : EditEvent
    data class TitleStyleFlag(val flag: TitleFlag, val on: Boolean) : EditEvent
    data class TitleBoxOpacity(val value: Float) : EditEvent
    data class TitleStyleAnimation(val animation: TitleAnimation) : EditEvent
    data object ResetTitleStyle : EditEvent
    /** Resolve's Paste Attributes (Alt+V): opens the sheet that picks what to paste onto the selected clip. */
    data object OpenPasteAttributes : EditEvent
    data object ClosePasteAttributes : EditEvent
    data class PasteAttribute(val attribute: ClipAttribute, val on: Boolean) : EditEvent
    data object ConfirmPasteAttributes : EditEvent
    data object AddMarker : EditEvent
    /** The marker index: pick a marker (the playhead goes to it), change the picked one, or step between markers (Shift+↑ / ↓). */
    data class SelectMarker(val id: String) : EditEvent
    data class RenameMarker(val name: String) : EditEvent
    data class RecolorMarker(val color: MarkerColor) : EditEvent
    data class NoteMarker(val note: String) : EditEvent
    data object DeleteMarker : EditEvent

    /** `marker-range-row`: the picked marker's duration (seconds, 0 = a point), taken from In / Out, or handed to them. */
    data class MarkerDuration(val seconds: Float) : EditEvent
    data object MarkerRangeFromMarks : EditEvent
    data object MarksFromMarkerRange : EditEvent
    data object PreviousMarker : EditEvent
    data object NextMarker : EditEvent
    data class SetSnapping(val enabled: Boolean) : EditEvent
    data class TrackEnabled(val trackId: String, val enabled: Boolean) : EditEvent
    data class TrackLocked(val trackId: String, val locked: Boolean) : EditEvent
    data class AddTrack(val kind: TrackKind) : EditEvent
    /** `subtitle-language-chip`: subtitle track [trackId] in [language]. */
    data class SubtitleLanguage(val trackId: String, val language: com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage) : EditEvent
    /** Adds the subtitle that follows the selected one and selects it. */
    data object NextSubtitle : EditEvent
    /** An .srt file the user picked (a document uri) to put on the subtitle track. */
    data class ImportSubtitles(val uri: String) : EditEvent
    /** Deletes a track other than V1 / A1, with its clips. */
    data class RemoveTrack(val trackId: String) : EditEvent
    data class TitleText(val text: String) : EditEvent
    data class TitleSize(val size: Float) : EditEvent
    data class Scale(val value: Float) : EditEvent
    data class PositionX(val value: Float) : EditEvent
    data class PositionY(val value: Float) : EditEvent
    data class Rotation(val value: Float) : EditEvent
    data class Opacity(val value: Float) : EditEvent
    /** Resolve's Composite Mode: how the selected picture combines with the tracks beneath it. */
    data class Composite(val mode: CompositeMode) : EditEvent

    /** Resolve's Retime Process: how the selected picture makes the frames its footage lacks. */
    data class Retime(val process: RetimeProcess) : EditEvent

    /** Resolve's Scaling: how the selected picture of another shape fills the frame. */
    /** null: follow the project's setting for mismatched media. */
    data class Scaling(val scaling: ClipScaling?) : EditEvent
    data object OpenTimelineSettings : EditEvent
    data object CloseTimelineSettings : EditEvent
    data class TimelineSettingsFormat(val index: Int) : EditEvent
    data class TimelineSettingsScaling(val scaling: ClipScaling) : EditEvent
    data object ApplyTimelineSettings : EditEvent
    data class TimelineSettingsName(val name: String) : EditEvent
    data class OpenTimeline(val id: String) : EditEvent
    data object NewTimeline : EditEvent
    data object DuplicateTimeline : EditEvent
    data object DeleteTimeline : EditEvent

    /** Resolve's flags: [color] on (or off) the media of the selected clips (`clip-flag-row`). */
    data class ToggleFlag(val color: FlagColor) : EditEvent

    /** Every flag off the media of the selected clips. */
    data object ClearFlags : EditEvent

    /** Resolve's Cropping: one edge (or the softness) of the selected picture's crop, at the playhead when keyed. */
    data class Crop(val control: CropControl, val value: Float) : EditEvent

    /** `crop-reset-button`: the crop back to none. */
    data object ResetCrop : EditEvent
    /** `mask-shape-segment`: the picture's mask shape (null = none). */
    data class MaskShape(val shape: WindowShape?) : EditEvent
    /** A `mask-section` slider: one value of the mask's geometry (at the playhead when it is keyed). */
    data class MaskControl(val control: WindowControl, val value: Float) : EditEvent
    /** `mask-invert-switch`: the picture shows outside the shape. */
    data class MaskInvert(val on: Boolean) : EditEvent
    /** `mask-key-button`: the mask keyed at the playhead, or its keyframe there removed. */
    data object ToggleMaskKey : EditEvent

    /** `mask-track-button` / `mask-track-cancel-button`: the mask follows the picture under it from the playhead; or stop. */
    data object TrackMask : EditEvent
    data object CancelMaskTrack : EditEvent
    /** `mask-track-zoom-chip` / `mask-track-rotate-chip`: whether tracking keys the mask's size and angle too. */
    data class SetMaskTrackMotion(val motion: TrackMotion) : EditEvent
    /** `mask-overlay` dragged: the mask's centre moved by ([dx], [dy]) in window units. */
    data class MoveMask(val dx: Float, val dy: Float) : EditEvent

    /** `stabilize-switch`: Resolve's Stabilization on (its settings kept from before, else the defaults) or off. */
    data class StabilizeOn(val on: Boolean) : EditEvent
    data class StabilizeSmooth(val value: Float) : EditEvent
    data class StabilizeStrength(val value: Float) : EditEvent
    data class StabilizeZoom(val on: Boolean) : EditEvent

    /** `stabilize-retry-button`: analyse the clip's file again after a failure. */
    data object RetryStabilize : EditEvent

    /** `zoom-switch`: Resolve's Dynamic Zoom on (with its last settings, else 推近 20 %) or off. */
    data class DynamicZoomOn(val on: Boolean) : EditEvent
    data class DynamicZoomDirection(val direction: ZoomDirection) : EditEvent
    data class DynamicZoomAmount(val percent: Float) : EditEvent
    data class DynamicZoomEase(val ease: ZoomEase) : EditEvent
    data class DynamicZoomFocusX(val focus: ZoomFocusX) : EditEvent
    data class DynamicZoomFocusY(val focus: ZoomFocusY) : EditEvent
    data class Volume(val db: Float) : EditEvent

    /** `clip-pan-row` / `clip-pan-key-button`: the sound's pan (−1…1), and its keyframe at the playhead. */
    data class Pan(val pan: Float) : EditEvent
    data object TogglePanKey : EditEvent
    data class FadeIn(val seconds: Float) : EditEvent
    data class FadeOut(val seconds: Float) : EditEvent

    /** `fade-curve-rows`: how the sound's fade in / fade out bends (Resolve's fade curve dot), −1…1. */
    data class FadeInCurve(val curve: Float) : EditEvent
    data class FadeOutCurve(val curve: Float) : EditEvent

    /** `clip-eq-row` / its sheet: the selected sound's own equaliser, edited as the Audio page edits a track's. */
    data object OpenClipEq : EditEvent
    data object CloseClipEq : EditEvent
    data class ClipEq(val control: EqControl, val value: Float) : EditEvent
    data class ClipEqDrag(val band: EqBand, val hz: Float, val db: Float) : EditEvent
    data class ClipEqLowCut(val on: Boolean) : EditEvent
    data class ClipEqEnabled(val on: Boolean) : EditEvent
    data class ClipEqPreset(val preset: EqPreset) : EditEvent
    data object ResetClipEq : EditEvent

    /** `picture-fade-rows`: the selected picture's fade handles, apart from its sound's. */
    data class PictureFadeIn(val seconds: Float) : EditEvent
    data class PictureFadeOut(val seconds: Float) : EditEvent

    /**
     * `key-section`: Resolve's keyer on the selected picture — on or off, a screen's colour, the eyedropper (a tap at
     * [PickKeyColor]'s u, v of the picture picks the screen's colour), its amounts, and the matte view on the monitor.
     */
    data class ChromaKeyOn(val on: Boolean) : EditEvent
    data class ChromaKeyScreen(val screen: KeyScreen) : EditEvent
    data class ChromaKeyEyedropper(val active: Boolean) : EditEvent
    data class PickKeyColor(val u: Float, val v: Float) : EditEvent
    data class ChromaKeyAmount(val control: KeyControl, val value: Float) : EditEvent
    data class ChromaKeyMatte(val on: Boolean) : EditEvent

    /** `picture-fade-curve-rows`: how the picture's fades bend, apart from its sound's. */
    data class PictureFadeInCurve(val curve: Float) : EditEvent
    data class PictureFadeOutCurve(val curve: Float) : EditEvent

    /** `cross-fade-row`: the sound clip's cross fade from the sound right before it, in frames. */
    data class CrossFade(val frames: Float) : EditEvent
    data class Speed(val index: Int) : EditEvent

    /** `speed-point-add-button` / `speed-point-remove-button` / `speed-points-clear-button`: Resolve's speed points. */
    data object AddSpeedPoint : EditEvent
    data object RemoveSpeedPoint : EditEvent
    data object ClearSpeedPoints : EditEvent

    /** `speed-ramp-row`: the speed point nearest the playhead smoothed over [seconds] (0 = a sudden change). */
    data class SpeedRamp(val seconds: Float) : EditEvent
    /** `curve-mode-chip`: the keyframe curve editor in place of the tracks, or the tracks back. */
    data class CurveMode(val on: Boolean) : EditEvent
    /** `curve-param-row`: the curve the graph shows. */
    data class CurvePicked(val key: String) : EditEvent
    /** A key tapped on the graph (null: none). */
    data class CurveKeyPicked(val index: Int?) : EditEvent
    /** Key [index] dragged to [frame] (from the clip's start) and [value]; one undo step per drag ([drag]). */
    data class CurveKeyMoved(val index: Int, val frame: Int, val value: Float, val drag: Int) : EditEvent
    /** Key [index]'s control point on its arriving ([arriving]) or leaving side dragged to [slope]; one undo step per drag. */
    data class CurveSlope(val index: Int, val arriving: Boolean, val slope: Float, val drag: Int) : EditEvent
    /** `curve-ease-chip`: an ease preset for the selected key. */
    data class CurveEasePicked(val ease: com.example.prismstudio.p40b43a88.domain.model.CurveEase) : EditEvent
    data object CurveAddKey : EditEvent
    data object CurveDeleteKey : EditEvent
    data object ToggleKeyframe : EditEvent
    /** `param-key-button`: [group]'s parameters keyed at the playhead, or their keys there removed. */
    data class ToggleParamKey(val group: TransformKeyGroup) : EditEvent
    data object PreviousKeyframe : EditEvent
    data object NextKeyframe : EditEvent
    data object ToggleVolumeKeyframe : EditEvent
    /** Ease in ([arriving]) or ease out at the transform keyframe at the playhead, on or off. */
    data class ToggleEase(val arriving: Boolean) : EditEvent
    data class ToggleVolumeEase(val arriving: Boolean) : EditEvent
    data object PreviousVolumeKeyframe : EditEvent
    data object NextVolumeKeyframe : EditEvent
    /** Moves the selected clip by [frames] on its track, without snapping. */
    data class Nudge(val frames: Int) : EditEvent
    data class JogMode(val index: Int) : EditEvent
    /** The jog dial turned by [frames] in slip or roll mode. */
    data class TrimJog(val frames: Int) : EditEvent
    data class Reverse(val reverse: Boolean) : EditEvent
    data class EffectEnabled(val effectId: String, val enabled: Boolean) : EditEvent
    data class EffectAmount(val effectId: String, val amount: Float) : EditEvent

    /** `effect-keyframe-button`: a keyframe of the effect's amount at the playhead, added or removed. */
    data class ToggleEffectKeyframe(val effectId: String) : EditEvent

    /** `effect-keyframe-prev-button` / `effect-keyframe-next-button`: the playhead to the effect's keyframe before or after it. */
    data class PreviousEffectKeyframe(val effectId: String) : EditEvent
    data class NextEffectKeyframe(val effectId: String) : EditEvent
    data class RemoveEffect(val effectId: String) : EditEvent
    data class Place(val placement: Placement) : EditEvent
    data class MoveTrack(val up: Boolean) : EditEvent
    data class TransitionSeconds(val seconds: Float) : EditEvent
    data object RemoveTransition : EditEvent

    /** `transition-type-menu`: the opening transition becomes another kind, its length and style kept. */
    data class TransitionKind(val type: TransitionType) : EditEvent

    /** `transition-alignment-control`: Resolve's Alignment — where a transition showing both pictures sits around the cut. */
    data class TransitionAlign(val alignment: TransitionAlignment) : EditEvent

    /** `transition-ease-control`: Resolve's Ease — how the change speeds up and slows down. */
    data class TransitionEasing(val ease: TransitionEase) : EditEvent

    /** `transition-direction-control`: the directional kinds the other way round (Resolve's Reverse). */
    data class TransitionReverse(val reverse: Boolean) : EditEvent

    /** `transition-softness-row`: the wipe's soft edge, 0–100. */
    data class TransitionSoftness(val value: Int) : EditEvent

    /** `transition-border-row`: the band along the wipe's edge, 0–100 (0 = none), and its colour. */
    data class TransitionBorder(val value: Int) : EditEvent
    data class TransitionBorderColor(val color: TitleColor) : EditEvent
    data class TransitionColor(val color: TitleColor) : EditEvent
    /** A transition's outer edge dragged on the timeline to [frames]; [commit] when it is let go. */
    data class TransitionDrag(val clipId: String, val frames: Int, val commit: Boolean) : EditEvent

    /**
     * `timeline` part fadeHandles: the selected clip's fade handle dragged to [seconds] of fade in (or out), shown live and
     * one undo step when let go.
     */
    data class FadeDrag(val clipId: String, val fadeIn: Boolean, val seconds: Float, val commit: Boolean) : EditEvent

    /**
     * `timeline` part fadeCurveDot: the dot in the middle of the selected clip's fade dragged up or down — the fade bent
     * to [curve] (−1…1), shown live and one undo step when let go.
     */
    data class FadeCurveDrag(val clipId: String, val fadeIn: Boolean, val curve: Float, val commit: Boolean) : EditEvent
    /** A wipe's angle in degrees (its old reverse folded in, so the slider says where it really goes). */
    data class TransitionAngle(val degrees: Int) : EditEvent
    data class ProgramSurface(val change: SurfaceChange) : EditEvent
    data class SourceSurface(val change: SurfaceChange) : EditEvent
    data class SourcePlaying(val playing: Boolean) : EditEvent
    data class SourceJog(val deltaFrames: Int) : EditEvent
    data object ToastShown : EditEvent
}

/**
 * Several clips selected (`multi-selection-panel`): how many (a picture with its linked sound counts
 * once), what they are, whether they all rest disabled (the switch then reads 启用) and whether any
 * sits on a locked track.
 */
data class SelectionUi(
    val count: Int,
    val detail: String,
    val allDisabled: Boolean,
    val locked: Boolean,
    /** `selection-align-row`: Resolve's Auto Align Clips — what it lines up to (the clip tapped last), or why it cannot. */
    val alignNote: String = "",
    val canAlign: Boolean = false,
    /** Reading the clips' sound for the alignment: the button waits and the note says so. */
    val aligning: Boolean = false,
    /** `selection-compound-row`: what New Compound Clip makes of the selection, or why it cannot. */
    val compoundNote: String = "",
    val canCompound: Boolean = false,
    /** `selection-multicam-row`: what New Multicam Clip makes of the selection (angles as they lie), or why it cannot. */
    val multicamNote: String = "",
    val canMulticam: Boolean = false,
)

/**
 * `multicam-panel`: the inspector of a multicam clip — its angles at the playhead (which plays the picture, which the sound),
 * what a tap switches ([mode], a [MulticamUseCase.SwitchMode] ordinal) and whether it cuts or switches the whole segment.
 */
data class MulticamUi(
    val name: String,
    val summary: String,
    val angles: List<AngleUi>,
    val mode: Int = 0,
    val whole: Boolean = false,
    val note: String = "",
) {
    val title: String get() = "多机位 · $name"
}

/** One `multicam-angle-tile`: angle [index]'s camera clip, its frame at the playhead, and whether it plays now. */
data class AngleUi(
    val index: Int,
    val name: String,
    val picture: Boolean,
    val sound: Boolean,
    val hasPicture: Boolean,
    val hasSound: Boolean,
    val frame: Bitmap? = null,
) {
    val label: String get() = "机位 ${index + 1}"
}

/** `compound-panel`: the inspector of a compound clip — its timeline's name and contents. */
data class CompoundUi(val name: String, val summary: String, val title: String = "复合片段 · $name")

/** One attribute in the paste-attributes sheet: whether the copied clip can give it here, and whether it is chosen. */
data class AttributeOptionUi(val attribute: ClipAttribute, val available: Boolean, val selected: Boolean)

/** `paste-attributes-sheet`: what is pasted from [sourceName] onto [targetName]. */
data class PasteAttributesUi(val sourceName: String, val targetName: String, val options: List<AttributeOptionUi>) {
    val canPaste: Boolean get() = options.any { it.available && it.selected }
}

/** A timeline in `timeline-menu`: its name, whether it is open, and its raster and length. */
data class TimelineEntryUi(val id: String, val name: String, val open: Boolean, val detail: String)

/**
 * `timeline-settings-sheet` as it is being edited: the raster ([FormatPreset] by index into [formats]), the frame rate
 * (fixed), how mismatched media fill the frame, and what that does to the clips following the project ([note]).
 */
/**
 * `rename-sheet`: the [clipId]'s or [trackId]'s name as typed ([text]), its name before ([original]), what a name may be
 * ([hint]) and what is wrong with this one ([problem]); 确定 needs a usable change.
 */
/**
 * `clip-duration-sheet`: [clipId]'s length as typed ([text]), what it reads as ([reading]) or why it cannot be used
 * ([problem]), its length before ([original]) and whether the track ripples.
 */
/**
 * `timecode-entry-sheet`: the typed [text], the playhead's place ([current]), what it does ([reading]) or why it cannot
 * ([problem]), and the button's word — 跳转 for the playhead, 移动 for the selected clips.
 */
data class TimecodeEntryUi(
    val text: String,
    val current: String,
    val reading: String = "",
    val problem: String? = null,
    val confirmLabel: String = "跳转",
) {
    val canConfirm: Boolean get() = problem == null && reading.isNotEmpty()
}

data class ClipDurationUi(
    val clipId: String,
    val name: String,
    val original: String,
    val text: String,
    val ripple: Boolean = true,
    val frames: Int? = null,
    val originalFrames: Int = 0,
    val reading: String = "",
    val problem: String? = null,
) {
    val canConfirm: Boolean get() = problem == null && frames != null && frames != originalFrames
}

data class RenameUi(
    val title: String,
    val clipId: String? = null,
    val trackId: String? = null,
    val original: String,
    val text: String,
    val hint: String,
    val problem: String? = null,
) {
    val canConfirm: Boolean get() = problem == null && text.trim() != original
}

data class TimelineSettingsUi(
    /** The open timeline's name as typed. */
    val name: String,
    val formats: List<String>,
    val formatIndex: Int,
    val fpsLabel: String,
    val scaling: ClipScaling,
    val note: String,
    /** Something differs from the project's settings: 应用 has work to do. */
    val changed: Boolean,
)

/** The subtitle track style's switches. */
enum class SubtitleFlag(val label: String) { BOLD("粗体"), OUTLINE("描边"), BOX("背景框") }

/** A title's switches. */
enum class TitleFlag(val label: String) { OUTLINE("描边"), SHADOW("阴影"), BOX("背景框") }

/**
 * `stabilize-section`: Resolve's Stabilization for the selected clip — its settings, where its file's camera analysis
 * stands, and for the `stabilize-path` diagram the camera path across the clip and the steadied path the picture follows.
 */
data class StabilizationUi(
    /** Why the clip cannot be stabilized (a still, a reversed clip); null when it can. */
    val unavailable: String? = null,
    val settings: com.example.prismstudio.p40b43a88.domain.model.ClipStabilization? = null,
    val status: String = "",
    val analysing: Boolean = false,
    val failed: Boolean = false,
    /** Beside the 放大 switch: 112%, 自动 (not analysed yet) or 不放大. */
    val zoomLabel: String = "",
    /** Sampled across the clip, shares of the picture (right and down); empty until analysed. */
    val cameraX: List<Float> = emptyList(),
    val cameraY: List<Float> = emptyList(),
    val steadyX: List<Float> = emptyList(),
    val steadyY: List<Float> = emptyList(),
) {
    val on: Boolean get() = settings?.enabled == true
    val smooth: Int get() = settings?.smooth ?: com.example.prismstudio.p40b43a88.domain.model.ClipStabilization.DEFAULT.smooth
    val strength: Int get() = settings?.strength ?: com.example.prismstudio.p40b43a88.domain.model.ClipStabilization.DEFAULT.strength
    val zoom: Boolean get() = settings?.zoom ?: true
}
