package com.example.prismstudio.p40b43a88.ui.color

import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNrControl
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurveChannel
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.GradeWheel
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.LutInfo
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.PrimaryControl
import com.example.prismstudio.p40b43a88.domain.model.ColorWarper
import com.example.prismstudio.p40b43a88.domain.model.MixerInput
import com.example.prismstudio.p40b43a88.domain.model.MixerOutput
import com.example.prismstudio.p40b43a88.domain.model.MixerSwap
import com.example.prismstudio.p40b43a88.domain.model.QualifierControl
import com.example.prismstudio.p40b43a88.domain.model.ScopeData
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange

/**
 * `strip-thumb-menu`: what a clip thumbnail's long-press menu offers — the clip being graded's paste and its grade
 * versions (the press selects the clip first).
 */
data class StripMenuUi(val canPaste: Boolean = false, val versions: List<String> = emptyList(), val version: Int = 0)

data class GradeClipUi(
    val id: String,
    val index: String,
    val name: String,
    val poster: PosterColors,
    /** Thumbnail with the clip's current grade applied. */
    val pixels: FramePixels?,
    val graded: Boolean,
    /** Resolve's clip colour, drawn as a bar along the thumbnail's foot. */
    val color: ClipColor? = null,
    /** Resolve's flags on the clip's media, at the thumbnail's top right. */
    val flags: List<FlagColor> = emptyList(),
)

/** A picture clip on the mini timeline: its lane (0 = the top picture track), its frames, how it reads there. */
data class MiniClipUi(
    val id: String,
    val lane: Int,
    val start: Int,
    val end: Int,
    val color: ClipColor? = null,
    /** The clip being graded, drawn lifted. */
    val selected: Boolean = false,
    /** Hidden by the clip filter, switched off, or on a track that is off: drawn faint. */
    val dimmed: Boolean = false,
)

/** Resolve's Color page mini timeline (`color-mini-timeline`): every picture track, top one first, across the whole program. */
data class MiniTimelineUi(val lanes: Int = 0, val durationFrames: Int = 0, val clips: List<MiniClipUi> = emptyList()) {
    companion object {
        /**
         * [document]'s picture tracks and [clips] on them (all its picture clips by default): [selectedId] lifted, clips
         * outside [kept] (when given), switched off or on a track that is off drawn faint.
         */
        fun of(
            document: ProjectDocument,
            clips: List<Clip> = document.timeline.clips.filter { it.kind == ClipKind.VIDEO },
            kept: Set<String>? = null,
            selectedId: String? = null,
        ): MiniTimelineUi {
            val lanes = document.timeline.tracks.filter { it.kind == TrackKind.VIDEO }
            return MiniTimelineUi(
                lanes = lanes.size,
                durationFrames = document.timeline.durationFrames,
                clips = clips.mapNotNull { clip ->
                    val lane = lanes.indexOfFirst { it.id == clip.trackId }.takeIf { it >= 0 } ?: return@mapNotNull null
                    MiniClipUi(
                        id = clip.id, lane = lane, start = clip.start, end = clip.end, color = clip.color,
                        selected = clip.id == selectedId,
                        dimmed = (kept != null && clip.id !in kept) || !clip.enabled || !lanes[lane].enabled,
                    )
                },
            )
        }
    }
}

/**
 * A choice of the clip filter menu (`clip-filter-option` / `clip-filter-color-option` / `clip-filter-flag-option`): what
 * it keeps, how many.
 */
data class ClipFilterOptionUi(val filter: ClipFilter, val count: Int, val selected: Boolean) {
    val label: String get() = filter.label
    val countLabel: String get() = "$count 个"
    val color: ClipColor? get() = (filter as? ClipFilter.Colored)?.color

    /** A flag filter's colour (null for 已标旗标片段 too: it shows a plain flag). */
    val flag: FlagColor? get() = (filter as? ClipFilter.Flagged)?.color
    val isFlagged: Boolean get() = filter is ClipFilter.Flagged
}

/** A tile of the LUT 风格 gallery: a built-in look, or ([lut]) an imported LUT, previewed on the current frame. */
data class LookPreviewUi(val look: Look, val pixels: FramePixels?, val lut: LutInfo? = null) {
    val key: String get() = lut?.id ?: look.id
    val label: String get() = lut?.name ?: look.label
}

/** A Gallery still's tile (`still-gallery`): its label, where it was grabbed, its picture. */
data class StillUi(val id: String, val label: String, val detail: String, val pixels: FramePixels?)

/**
 * The window drawn over the program monitor while it is edited: its geometry, and how the clip shows
 * its picture at the playhead (the window moves with the picture).
 */

/**
 * How the clip shows its picture at the playhead, so a tap on the monitor finds the source point it shows
 * (eyedropper), and what the tap will do.
 */
data class PickOverlayUi(val transform: ClipTransform, val mirrored: Boolean, val hint: String = PickMode.PICK.hint)

/**
 * What the qualifier's eyedropper does with the colour it is tapped on: Resolve's picker (a new qualifier
 * around it), picker add (the ranges grow to take it in) and picker subtract (they shrink to leave it out).
 * Adding and subtracting stay on for the next colour.
 */
enum class PickMode(val hint: String) {
    PICK("点击画面中要调整的颜色"),
    ADD("点击画面中要加入选区的颜色"),
    SUBTRACT("点击画面中要移出选区的颜色"),
    ;

    /** What a tap does with 柔化 on: Resolve's feather add / subtract work on the selection's soft edge. */
    fun hint(feather: Boolean): String = when {
        !feather || this == PICK -> hint
        this == ADD -> "点击画面中的颜色，让它落在选区的柔和边缘里"
        else -> "点击柔和边缘里的颜色，把它移出边缘"
    }
}

/** `serial-node-card`: a serial node (03 on) — whether it changes the picture and whether it is switched on. */
data class SerialNodeUi(val node: GradeNode, val graded: Boolean, val enabled: Boolean) {
    /** "整个画面" / "已停用" under its name. */
    val summary: String get() = if (enabled) "整个画面" else "已停用 · 画面跳过"
}

data class ColorUiState(
    /** The program monitor shows a flattened approximation of composited overlays / transitions. */
    val previewApproximate: Boolean = false,
    /** Picture layers the program monitor stacks. */
    val programLayers: Int = 1,
    /** Each program layer's composite mode at the playhead (Resolve's Composite Mode), bottom first. */
    val programComposites: List<CompositeMode> = emptyList(),
    val clips: List<GradeClipUi> = emptyList(),
    val selectedClipId: String? = null,
    /** Resolve's Clips filter (`clip-filter-chip`): which of [clips] the strip shows. */
    val clipFilter: ClipFilter = ClipFilter.All,
    /** The strip's clips under [clipFilter], numbered in timeline order as without it. */
    val stripClips: List<GradeClipUi> = emptyList(),
    /** `clip-filter-count`: "6 个片段", or "2 / 6 个片段" while the filter narrows the strip. */
    val clipCountLabel: String = "",
    /** The filter menu: 全部 / 已调色 / 未调色 (`clip-filter-options`), then the clip colours in use (`clip-filter-color-options`). */
    val clipFilterOptions: List<ClipFilterOptionUi> = emptyList(),
    val clipColorFilters: List<ClipFilterOptionUi> = emptyList(),
    /** Resolve's Flagged Clips: any flag, then each flag colour in use. */
    val clipFlagFilters: List<ClipFilterOptionUi> = emptyList(),
    /** The strip's line when the filter keeps none of the timeline's clips. */
    val stripMessage: String? = null,
    /** The mini timeline under the strip: picture tracks and their clips, the clip being graded lifted. */
    val miniTimeline: MiniTimelineUi = MiniTimelineUi(),
    /** The node the wheels, sliders, curves and looks edit. */
    val node: GradeNode = GradeNode.PRIMARY,
    /** The selected node's grade, as the controls show it. */
    val grade: ColorGrade = ColorGrade.NEUTRAL,
    /** The selected clip's power window (node 2) as it stands at the playhead, or null. */
    val window: PowerWindow? = null,
    /** The window's dynamic keyframes, and where the playhead stands among them (`window-keyframe-row`). */
    val windowKeys: KeyframeUi = KeyframeUi(),
    /** Resolve's Tracker (`window-track-row`): why the window cannot be tracked (null: it can), and the run's progress. */
    val trackUnavailable: String? = null,
    val tracking: Boolean = false,
    val trackStatus: String = "",
    /** What the window tracker follows besides the position (`window-track-zoom-chip` / `-rotate-chip`). */
    val trackMotion: TrackMotion = TrackMotion.ALL,
    /** Node 1's dynamic keyframes (its correction dissolving between them), for `node-keyframes`. */
    val gradeKeys: KeyframeUi = KeyframeUi(),
    /** Node 2's name: what limits it — 窗口, 限定器 or both. */
    val secondaryLabel: String = GradeNode.WINDOW.label,
    /** `serial-node-card`s: the selected clip's serial nodes 03 on, and whether another fits (`add-node-card`). */
    val serialNodes: List<SerialNodeUi> = emptyList(),
    val canAddNode: Boolean = false,
    /** The monitor picks a colour for the qualifier on the next tap (`qualifier-pick-button`). */
    val eyedropper: Boolean = false,
    /** What that tap does: pick, add or subtract (`qualifier-add-button`, `qualifier-subtract-button`). */
    val pickMode: PickMode = PickMode.PICK,
    /** 柔化: 加选 / 减选 grow or pull back the selection's soft edge instead of its ranges (Resolve's feather pickers). */
    val featherPick: Boolean = false,
    /** The monitor shows node 2's selection as a matte (`qualifier-highlight-switch`). */
    val highlight: Boolean = false,
    /** The qualifier palette's page (`qualifier-page-segment`): the hue / saturation / luminance ranges, or matte finesse. */
    val qualifierFinesse: Boolean = false,
    val pickOverlay: PickOverlayUi? = null,
    /** Node 1 changes the picture (the node strip marks it). */
    val primaryGraded: Boolean = false,
    val windowOverlay: WindowOverlayUi? = null,
    val readouts: Map<GradeWheel, String> = GradeWheel.entries.associateWith { "R 0.00  G 0.00  B 0.00" },
    val scopeMode: ScopeMode = ScopeMode.WAVEFORM,
    val scope: ScopeData? = null,
    val adjustTab: Int = 0,
    /** The Color Warper's picked point (rev 149): spoke 0 red … 5 magenta, ring 0 inner … 2 outer. */
    val warperSpoke: Int = 0,
    val warperRing: Int = ColorWarper.RINGS - 1,
    /** `spatial-nr-panel`: the clip's Spatial NR (node 01's, whichever node is selected). */
    val spatialNr: SpatialNoiseReduction = SpatialNoiseReduction.OFF,
    /** Resolve's Mid/Detail of the selected clip, −100…100 (`grade-mid-detail-row`). */
    val midDetail: Float = 0f,
    /** Resolve's Temporal NR of the selected clip, 0…100 (`grade-temporal-nr-row`). */
    val temporalNr: Int = 0,
    val curveChannel: CurveChannel = CurveChannel.LUMA,
    /** The hue curve shown in the curves palette, or null for the custom YRGB curve of [curveChannel]. */
    val hueCurve: HueCurve? = null,
    val looks: List<LookPreviewUi> = Look.entries.map { LookPreviewUi(it, null) },
    /** A `.cube` file is being read into the LUT library (`lut-import-tile`). */
    val lutImporting: Boolean = false,
    /** The selected node names an imported LUT the library does not hold: its name (`lut-missing-note`). */
    val lutMissing: String? = null,
    /** The selected clip's grade is being baked into a LUT (`lut-generate-tile`). */
    val lutGenerating: Boolean = false,
    /** A library LUT waiting for the colourist to confirm its removal (`remove-lut` confirmation). */
    val lutRemoval: LutInfo? = null,
    /** The Gallery's stills, oldest first (`still-gallery`), and the one picked. */
    val stills: List<StillUi> = emptyList(),
    val selectedStillId: String? = null,
    /** A still is being grabbed at the playhead (`gallery-grab-button`). */
    val grabbing: Boolean = false,
    /** Shot Match against the picked still is being worked out. */
    val matching: Boolean = false,
    /** The monitor wipes the picked still (left) against the picture (right) (`gallery-wipe-toggle`). */
    val wipe: Boolean = false,
    /** The picked still's picture for the wipe, and its label. */
    val wipeImage: FramePixels? = null,
    val wipeLabel: String = "",
    val compare: Boolean = false,
    val clipboardEmpty: Boolean = true,
    /** `grade-version-chip`: the selected clip's local grade versions (Resolve's Local Versions) and the one shown. */
    val gradeVersions: List<String> = emptyList(),
    val gradeVersion: Int = 0,
    val playheadFrame: Int = 0,
    val fps: Int = 30,
    val pictureAspect: Float = 16f / 9f,
    val programMessage: String? = null,
    val toast: String? = null,
) {
    val hasClip: Boolean get() = selectedClipId != null
    /** The filter narrows the strip: the chip lights (Resolve's line under Clips). */
    val clipFiltered: Boolean get() = clipFilter != ClipFilter.All
    val primaries: List<PrimaryControl> get() = PrimaryControl.entries

    companion object {
        const val TAB_PRIMARIES = 0
        const val TAB_CURVES = 1
        const val TAB_LOOKS = 2
        const val TAB_WINDOW = 3
        const val TAB_QUALIFIER = 4
        const val TAB_GALLERY = 5
        const val TAB_NOISE = 6

        /** Resolve's RGB Mixer palette (rev 145). */
        const val TAB_MIXER = 7

        /** Resolve's Color Warper palette (rev 149). */
        const val TAB_WARPER = 8
    }
}

sealed interface ColorEvent {
    data class SelectClip(val clipId: String) : ColorEvent
    /** The strip shows only the clips [filter] keeps; a clip it hides gives the grade to the strip's first. */
    data class SetClipFilter(val filter: ClipFilter) : ColorEvent
    /** Resolve's Previous / Next Clip (↑ / ↓, the deck's ⏮ / ⏭ on this page): the strip's neighbour, from its first frame. */
    data class StepClip(val forward: Boolean) : ColorEvent
    /** The mini timeline: the playhead to [frame]; a press on a clip ([clipId]) grades that clip, a V2 one over V1 too. */
    data class Scrub(val frame: Int, val clipId: String? = null) : ColorEvent
    /** Edit [node]; node 2 without a window gets a circle window. */
    data class SelectNode(val node: GradeNode) : ColorEvent
    /** `add-node-card` (Resolve's Add Serial Node, Alt+S): a serial node after the last one, selected. */
    data object AddSerialNode : ColorEvent
    /** `node-enable-button` (Resolve's Enable / Disable Node, Ctrl+D): [node] passes the picture through, or grades again. */
    data class ToggleNode(val node: GradeNode) : ColorEvent
    /** `node-delete-button`: [node] removed, the nodes after it moving up a place. */
    data class DeleteNode(val node: GradeNode) : ColorEvent
    /** null removes the window. */
    data class SetWindowShape(val shape: WindowShape?) : ColorEvent
    data class Window(val control: WindowControl, val value: Float) : ColorEvent
    data class SetWindowInvert(val invert: Boolean) : ColorEvent
    /** Adds a window keyframe at the playhead, or removes the one there. */
    data object ToggleWindowKeyframe : ColorEvent
    data object PreviousWindowKeyframe : ColorEvent
    data object NextWindowKeyframe : ColorEvent

    /** `window-track-button`: follow the picture under the window through the clip, keying the window along it. */
    data object TrackWindow : ColorEvent

    /** `window-track-cancel-button`: stop a tracking run; the window stays as it was. */
    data object CancelTrack : ColorEvent

    /** `window-track-zoom-chip` / `window-track-rotate-chip`: whether tracking keys the window's size and angle too. */
    data class SetTrackMotion(val motion: TrackMotion) : ColorEvent
    /** Eases the window into ([arriving]) or out of the keyframe at the playhead, or back. */
    data class ToggleWindowEase(val arriving: Boolean) : ColorEvent
    /** Adds a node 1 keyframe (the whole correction) at the playhead, or removes the one there. */
    data object ToggleGradeKeyframe : ColorEvent
    data object PreviousGradeKeyframe : ColorEvent
    data object NextGradeKeyframe : ColorEvent
    /** Eases the dissolve into ([arriving]) or out of node 1's keyframe at the playhead, or back. */
    data class ToggleGradeEase(val arriving: Boolean) : ColorEvent
    /** The next tap on the monitor picks the qualifier's colour, or adds it to or subtracts it from the selection. */
    data class SetEyedropper(val active: Boolean, val mode: PickMode = PickMode.PICK) : ColorEvent
    data class SetFeatherPick(val on: Boolean) : ColorEvent
    /** A tap at [u], [v] (0…1 from the left / bottom of the clip's picture, before its transform). */
    data class PickColor(val u: Float, val v: Float) : ColorEvent
    data class Qualifier(val control: QualifierControl, val value: Float) : ColorEvent
    data class SetQualifierInvert(val invert: Boolean) : ColorEvent
    data class SetHighlight(val enabled: Boolean) : ColorEvent
    /** The qualifier palette's page: false the ranges, true matte finesse. */
    data class QualifierPage(val finesse: Boolean) : ColorEvent
    data object RemoveQualifier : ColorEvent
    /** Moves the window by [dx], [dy] in window units (−1…1 across the picture). */
    data class MoveWindow(val dx: Float, val dy: Float) : ColorEvent
    data class WheelBalance(val wheel: GradeWheel, val x: Float, val y: Float) : ColorEvent
    data class WheelMaster(val wheel: GradeWheel, val value: Float) : ColorEvent
    data class Primary(val control: PrimaryControl, val value: Float) : ColorEvent

    /** `rgb-mixer-panel`: [input]'s amount in [output]'s mix (null: the grey's mix in 单色). */
    data class MixerValue(val output: MixerOutput?, val input: MixerInput, val value: Float) : ColorEvent
    data class MixerPreserve(val on: Boolean) : ColorEvent
    data class MixerMonochrome(val on: Boolean) : ColorEvent
    data class MixerSwapChannels(val swap: MixerSwap) : ColorEvent
    data object ResetMixer : ColorEvent

    /** `color-warper-panel`: pick point ([spoke], [ring]) of the warper's grid. */
    data class WarperSelect(val spoke: Int, val ring: Int) : ColorEvent

    /** Point ([spoke], [ring]) moved [hue]° round the wheel and [sat] in or out (a drag is one undo step). */
    data class WarperMove(val spoke: Int, val ring: Int, val hue: Float, val sat: Float) : ColorEvent
    data object ResetWarperPoint : ColorEvent
    data object ResetWarper : ColorEvent
    data class Scope(val index: Int) : ColorEvent
    data class AdjustTab(val index: Int) : ColorEvent
    data class Channel(val index: Int) : ColorEvent
    data class CurveAdd(val x: Float, val y: Float) : ColorEvent
    data class CurveMove(val index: Int, val x: Float, val y: Float) : ColorEvent
    data class CurveRemove(val index: Int) : ColorEvent
    data object ResetCurve : ColorEvent
    /** A curve picked in `curve-channel-segment`: 0–3 the custom channels, then the hue curves in [HueCurve] order. */
    data class PickCurve(val index: Int) : ColorEvent
    /** Edits of the current hue curve: a tap adds a point (with its anchors), a drag moves one, a double tap removes it. */
    data class HueCurveAdd(val x: Float) : ColorEvent
    data class HueCurveMove(val index: Int, val x: Float, val y: Float) : ColorEvent
    data class HueCurveRemove(val index: Int) : ColorEvent
    data class SelectLook(val look: Look) : ColorEvent
    /** Puts the library's LUT [id] in the selected node's look slot, at full strength. */
    data class SelectLut(val id: String) : ColorEvent
    /** Reads the `.cube` file at [uri] into the LUT library and applies it to the selected node. */
    data class ImportLut(val uri: String) : ColorEvent
    /** Bakes the selected clip's grade at the playhead into a 33-point LUT: library + Download/Reality Video Editor. */
    data object GenerateLut : ColorEvent

    /** `spatial-nr-panel`: Resolve's Spatial NR on the clip — a threshold or the amount, the radius, back to off. */
    data class SpatialNr(val control: SpatialNrControl, val value: Float) : ColorEvent
    data class SpatialNrRadius(val radius: NrRadius) : ColorEvent
    data object ResetSpatialNr : ColorEvent
    /** Asks to take the library's LUT [id] out (held on its tile); [ConfirmRemoveLut] does it. */
    data class RequestRemoveLut(val id: String) : ColorEvent
    data object ConfirmRemoveLut : ColorEvent
    data object CancelRemoveLut : ColorEvent
    data class LookStrength(val value: Float) : ColorEvent
    data class SetCompare(val enabled: Boolean) : ColorEvent
    /** Resolve's Grab Still: the selected clip's graded picture at the playhead, with its grade, into the Gallery. */
    data object GrabStill : ColorEvent
    data class SelectStill(val id: String) : ColorEvent
    /** Grades the selected clip as the picked still was. */
    data object ApplyStill : ColorEvent
    /** Resolve's Shot Match: node 1's wheels set so the clip's blacks, mids and whites match the picked still's. */
    data object MatchStill : ColorEvent
    /** Wipes the picked still against the picture on the monitor, or stops. */
    data class SetWipe(val enabled: Boolean) : ColorEvent
    data class DeleteStill(val id: String) : ColorEvent
    data object CopyGrade : ColorEvent
    data object PasteGrade : ColorEvent
    data object ResetGrade : ColorEvent
    /** Resolve's Mid/Detail (−100…100), node 1's, for the whole clip (`grade-mid-detail-row`). */
    data class MidDetail(val value: Float) : ColorEvent
    data class TemporalNr(val value: Float) : ColorEvent
    /** Local grade versions: a new one copied from the one shown, another taken up, the shown one deleted. */
    data object NewGradeVersion : ColorEvent
    data class SelectGradeVersion(val index: Int) : ColorEvent
    /** Shift+Alt+[ / ]: the version before / after the one shown. */
    data class StepGradeVersion(val delta: Int) : ColorEvent
    data object DeleteGradeVersion : ColorEvent
    /** Resolve's Apply Grade from One Clip Prior (=) / Two Clips Prior (−). */
    data class ApplyPriorGrade(val steps: Int) : ColorEvent
    /** Resolve's Auto Color (Shift+Alt+C): node 1's Lift and Gain from the picture's black and white points. */
    data object AutoColor : ColorEvent
    data class ProgramSurface(val change: SurfaceChange) : ColorEvent
    data object ToastShown : ColorEvent
}
