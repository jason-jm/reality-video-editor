package com.example.prismstudio.p40b43a88.ui.color

import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.domain.model.toneMapped
import com.example.prismstudio.p40b43a88.domain.model.hdrByOverride
import com.example.prismstudio.p40b43a88.domain.usecase.PriorGradeUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TrackMotion
import com.example.prismstudio.p40b43a88.ui.components.WindowOverlayUi
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.RgbMixer
import com.example.prismstudio.p40b43a88.domain.model.ColorWarper
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.usecase.GradeVersionsUseCase
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.DocumentFailure
import com.example.prismstudio.p40b43a88.data.repository.DocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.GalleryRepository
import com.example.prismstudio.p40b43a88.data.repository.InMemoryGalleryRepository
import com.example.prismstudio.p40b43a88.data.repository.InMemoryLutRepository
import com.example.prismstudio.p40b43a88.data.repository.LutRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.framingOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFilter
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurveChannel
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.SerialNode
import com.example.prismstudio.p40b43a88.domain.model.GradeWheel
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.gradeKeyframeAt
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.keyframeAt
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.windowAt
import com.example.prismstudio.p40b43a88.domain.usecase.AutoColorUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ShotMatchUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.BuildGradeLutUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ComputeScopesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CubeLutWriter
import com.example.prismstudio.p40b43a88.domain.usecase.EditCurveUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditHueCurveUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ClipFilterUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.GalleryUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.GenerateLutUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.GradeEdit
import com.example.prismstudio.p40b43a88.domain.usecase.GradeKeyframeUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.LutFormatException
import com.example.prismstudio.p40b43a88.domain.usecase.WindowEdit
import com.example.prismstudio.p40b43a88.domain.usecase.WindowKeyframeUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.sourceMsAt
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.keyframeUi
import com.example.prismstudio.p40b43a88.ui.components.route
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.example.prismstudio.p40b43a88.domain.usecase.WindowTrackUseCase
import com.example.prismstudio.p40b43a88.data.repository.LumaFrameRepository

/**
 * Color stage: primary wheels and sliders, curves, creative looks, a power window (node 2, graded with
 * the same controls), graded clip strip and scopes measured on the graded frame at the playhead.
 * Grades apply live to the program monitor.
 */
class ColorViewModel(
    private val session: EditorSessionRepository,
    private val program: ProgramPlaybackRepository,
    private val previews: MediaPreviewRepository,
    private val luts: LutRepository = InMemoryLutRepository(),
    private val documents: DocumentRepository = DocumentRepository.None,
    private val downloads: DownloadsRepository = DownloadsRepository.None,
    private val gallery: GalleryRepository = InMemoryGalleryRepository(),
    private val ids: IdGenerator = IdGenerator { prefix -> "$prefix-${UUID.randomUUID().toString().take(12)}" },
    private val stills: GalleryUseCase = GalleryUseCase(),
    private val scopes: ComputeScopesUseCase = ComputeScopesUseCase(BuildGradeLutUseCase(luts = luts)),
    private val curves: EditCurveUseCase = EditCurveUseCase(),
    private val hueCurveEdits: EditHueCurveUseCase = EditHueCurveUseCase(),
    private val lut: BuildGradeLutUseCase = BuildGradeLutUseCase(luts = luts),
    private val windowKeys: WindowKeyframeUseCase = WindowKeyframeUseCase(),
    private val gradeKeys: GradeKeyframeUseCase = GradeKeyframeUseCase(),
    private val generate: GenerateLutUseCase = GenerateLutUseCase(BuildGradeLutUseCase(luts = luts)),
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val autoColors: AutoColorUseCase = AutoColorUseCase(),
    private val shots: ShotMatchUseCase = ShotMatchUseCase(BuildGradeLutUseCase(luts = luts)),
    private val clipFilters: ClipFilterUseCase = ClipFilterUseCase(),
    private val gradeVersions: GradeVersionsUseCase = GradeVersionsUseCase(),
    private val priorGrade: PriorGradeUseCase = PriorGradeUseCase(),
    /** The frames Resolve's window Tracker follows the picture through. */
    private val lumaFrames: LumaFrameRepository = LumaFrameRepository.None,
    private val tracker: WindowTrackUseCase = WindowTrackUseCase(),
) : ViewModel() {

    private val _state = MutableStateFlow(ColorUiState())
    private var trackJob: Job? = null
    val state: StateFlow<ColorUiState> = _state.asStateFlow()

    private var clipboard: ColorGrade? = null
    private var frame: FramePixels? = null
    private var frameKey: String? = null
    private var playing = false
    private var analysisJob: Job? = null
    private val thumbSources = mutableMapOf<String, FramePixels>()
    /** Strip pictures by file and second, shared by the clips that start there, and the clips waiting for one being read. */
    private val sharedThumbs = mutableMapOf<String, FramePixels>()
    private val waitingThumbs = mutableMapOf<String, MutableList<String>>()
    /** Gallery tile pictures by still id (null: unreadable), and those being read. */
    private val stillThumbs = mutableMapOf<String, FramePixels?>()
    private val stillsRequested = mutableSetOf<String>()
    /** Graded strip thumbnails by clip, with the grade and scaling they were made for. */
    private val gradedThumbs = mutableMapOf<String, Pair<Pair<ColorGrade, Framing?>, FramePixels>>()
    private val requested = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            session.document.collect {
                publish()
                analyse(refetch = false)
            }
        }
        // A LUT added to the library gets a tile (and a missing one stops being missing).
        viewModelScope.launch {
            luts.library.drop(1).collect {
                publish()
                analyse(refetch = false)
            }
        }
        viewModelScope.launch {
            program.state.collect { p ->
                val frameAt = Timecode.positionToFrame(p.positionMs, _state.value.fps)
                val moved = frameAt != _state.value.playheadFrame
                playing = p.playing
                _state.update { it.copy(playheadFrame = frameAt, previewApproximate = p.approximate, programLayers = p.layers, programComposites = p.compositesAt()) }
                if (moved) {
                    // As in Resolve the current clip is the one under the playhead: moving into another clip selects it.
                    followPlayhead(frameAt)
                    // A keyed window moves with the playhead (its values, keyframe state and outline), and a
                    // keyed node 1 shows its correction there.
                    val clip = selectedClip()
                    _state.update {
                        it.copy(
                            window = clip?.grade?.window?.at(frameAt - clip.start),
                            windowKeys = windowKeyUi(clip, frameAt),
                            gradeKeys = gradeKeyUi(clip, frameAt),
                            windowOverlay = overlayFor(clip, it),
                            pickOverlay = pickOverlayFor(clip, it),
                        )
                    }
                    if (clip?.grade?.keyframes?.isNotEmpty() == true) publish()
                    analyse(refetch = true)
                }
            }
        }
    }

    private val doc: ProjectDocument? get() = session.document.value

    fun onEvent(event: ColorEvent) {
        when (event) {
            is ColorEvent.SelectClip -> selectClip(event.clipId)
            is ColorEvent.SetClipFilter -> setClipFilter(event.filter)
            is ColorEvent.StepClip -> stepClip(event.forward)
            is ColorEvent.Scrub -> scrub(event.frame, event.clipId)
            is ColorEvent.SelectNode -> selectNode(event.node)
            ColorEvent.AddSerialNode -> addSerialNode()
            is ColorEvent.ToggleNode -> toggleNode(event.node)
            is ColorEvent.DeleteNode -> deleteNode(event.node)
            is ColorEvent.SetWindowShape -> setWindowShape(event.shape)
            is ColorEvent.Window -> windowGeometry("窗口：${event.control.label}", "window-${event.control}") { event.control.write(it, event.value) }
            ColorEvent.ToggleWindowKeyframe -> windowKeyframe("窗口关键帧") { w, local, frames -> windowKeys.toggle(w, local, frames) }
            is ColorEvent.ToggleWindowEase -> windowKeyframe(if (event.arriving) "窗口关键帧：缓入" else "窗口关键帧：缓出") { w, local, _ ->
                windowKeys.ease(w, local, event.arriving)
            }
            ColorEvent.PreviousWindowKeyframe -> seekWindowKeyframe { w, local -> windowKeys.previous(w, local) }
            ColorEvent.TrackWindow -> trackWindow()
            ColorEvent.CancelTrack -> trackJob?.cancel()
            is ColorEvent.SetTrackMotion -> _state.update { it.copy(trackMotion = event.motion) }
            ColorEvent.NextWindowKeyframe -> seekWindowKeyframe { w, local -> windowKeys.next(w, local) }
            ColorEvent.ToggleGradeKeyframe -> gradeKeyframe("调色关键帧") { g, local, frames -> gradeKeys.toggle(g, local, frames) }
            is ColorEvent.ToggleGradeEase -> gradeKeyframe(if (event.arriving) "调色关键帧：缓入" else "调色关键帧：缓出") { g, local, _ ->
                gradeKeys.ease(g, local, event.arriving)
            }
            ColorEvent.PreviousGradeKeyframe -> seekGradeKeyframe { g, local -> gradeKeys.previous(g, local) }
            ColorEvent.NextGradeKeyframe -> seekGradeKeyframe { g, local -> gradeKeys.next(g, local) }
            is ColorEvent.SetWindowInvert -> window(if (event.invert) "窗口：反转" else "窗口：取消反转", null) { it.copy(invert = event.invert) }
            is ColorEvent.SetEyedropper -> _state.update { s ->
                // Adding and subtracting refine a qualifier, so they need one.
                val usable = selectedClip()?.let { event.mode == PickMode.PICK || it.grade.window?.qualifier != null } == true
                s.copy(eyedropper = event.active && usable, pickMode = event.mode).let { it.copy(pickOverlay = pickOverlayFor(selectedClip(), it)) }
            }
            is ColorEvent.SetFeatherPick -> _state.update { s ->
                s.copy(featherPick = event.on).let { it.copy(pickOverlay = pickOverlayFor(selectedClip(), it)) }
            }
            is ColorEvent.PickColor -> pickColor(event.u, event.v)
            is ColorEvent.Qualifier -> qualifier(
                if (event.control.finesse) "限定器：${event.control.label}" else "限定器：${event.control.group}${event.control.label}",
                "qualifier-${event.control}",
            ) {
                event.control.write(it, event.value)
            }
            is ColorEvent.SetQualifierInvert -> qualifier(if (event.invert) "限定器：反转" else "限定器：取消反转", null) { it.copy(invert = event.invert) }
            is ColorEvent.SetHighlight -> {
                _state.update { it.copy(highlight = event.enabled) }
                program.setHighlight(if (event.enabled) _state.value.selectedClipId else null)
            }
            ColorEvent.RemoveQualifier -> if (selectedClip()?.grade?.window?.qualifier != null) {
                window("移除限定器", null) { it.copy(qualifier = null) }
                // Nothing is left to add to or subtract from.
                _state.update {
                    val armed = it.eyedropper && it.pickMode == PickMode.PICK
                    it.copy(toast = "已移除限定器", eyedropper = armed, pickOverlay = it.pickOverlay.takeIf { armed })
                }
            }
            is ColorEvent.MoveWindow -> windowGeometry("移动窗口", "window-move") {
                it.copy(centerX = (it.centerX + event.dx).coerceIn(-1f, 1f), centerY = (it.centerY + event.dy).coerceIn(-1f, 1f))
            }
            is ColorEvent.WheelBalance -> grade("调色：${event.wheel.label}", "wheel-${event.wheel}") { g ->
                val r = hypot(event.x, event.y)
                val k = if (r > WheelValue.MAX_BALANCE) WheelValue.MAX_BALANCE / r else 1f
                g.withWheel(event.wheel, g.wheel(event.wheel).copy(x = event.x * k, y = event.y * k))
            }
            is ColorEvent.WheelMaster -> grade("调色：${event.wheel.label}", "master-${event.wheel}") { g ->
                g.withWheel(event.wheel, g.wheel(event.wheel).copy(master = event.value.coerceIn(-1f, 1f)))
            }
            is ColorEvent.Primary -> grade("调色：${event.control.label}", "primary-${event.control}") { g -> event.control.write(g, event.value) }
            // Resolve's RGB Mixer (rev 145): a slider's drag is one undo step, as the primaries'.
            is ColorEvent.MixerValue -> {
                val what = event.output?.label ?: "灰度"
                grade("RGB 混合器：$what ${event.input.label}", "mixer-${event.output}-${event.input}") { g ->
                    val m = g.rgbMixer
                    val value = event.value.coerceIn(RgbMixer.MIN, RgbMixer.MAX)
                    g.copy(rgbMixer = event.output?.let { o -> m.withOutput(o, m.output(o).with(event.input, value)) } ?: m.copy(mono = m.mono.with(event.input, value)))
                }
            }
            is ColorEvent.MixerPreserve -> grade(if (event.on) "RGB 混合器：保持亮度" else "RGB 混合器：不保持亮度", null) { g ->
                g.copy(rgbMixer = g.rgbMixer.copy(preserveLuminance = event.on))
            }
            is ColorEvent.MixerMonochrome -> grade(if (event.on) "RGB 混合器：单色" else "RGB 混合器：彩色", null) { g ->
                g.copy(rgbMixer = g.rgbMixer.copy(monochrome = event.on))
            }
            is ColorEvent.MixerSwapChannels -> grade("RGB 混合器：${event.swap.label}", null) { g -> g.copy(rgbMixer = g.rgbMixer.swapped(event.swap)) }
            ColorEvent.ResetMixer -> grade("重置 RGB 混合器", null) { g -> g.copy(rgbMixer = RgbMixer.NEUTRAL) }
            // Resolve's Color Warper (rev 149): a drag on one point is one undo step.
            is ColorEvent.WarperSelect -> _state.update {
                it.copy(warperSpoke = event.spoke.coerceIn(0, ColorWarper.SPOKES - 1), warperRing = event.ring.coerceIn(0, ColorWarper.RINGS - 1))
            }
            is ColorEvent.WarperMove -> {
                val s = event.spoke.coerceIn(0, ColorWarper.SPOKES - 1)
                val r = event.ring.coerceIn(0, ColorWarper.RINGS - 1)
                _state.update { it.copy(warperSpoke = s, warperRing = r) }
                grade("色彩扭曲器：${warperPointName(s, r)}", "warper-$s-$r") { g -> g.copy(warper = g.warper.with(s, r, event.hue, event.sat)) }
            }
            ColorEvent.ResetWarperPoint -> _state.value.let { st ->
                grade("色彩扭曲器：重置${warperPointName(st.warperSpoke, st.warperRing)}", null) { g ->
                    g.copy(warper = g.warper.with(st.warperSpoke, st.warperRing, 0f, 0f))
                }
            }
            ColorEvent.ResetWarper -> grade("重置色彩扭曲器", null) { g -> g.copy(warper = ColorWarper.NEUTRAL) }
            is ColorEvent.Scope -> {
                _state.update { it.copy(scopeMode = ScopeMode.entries[event.index.coerceIn(0, ScopeMode.entries.lastIndex)]) }
                analyse(refetch = false)
            }
            is ColorEvent.AdjustTab -> _state.update { s ->
                val tab = event.index.coerceIn(0, ColorUiState.TAB_WARPER)
                // The eyedropper belongs to the qualifier palette.
                s.copy(adjustTab = tab, eyedropper = s.eyedropper && tab == ColorUiState.TAB_QUALIFIER)
                    .let { it.copy(windowOverlay = overlayFor(selectedClip(), it), pickOverlay = pickOverlayFor(selectedClip(), it)) }
            }
            is ColorEvent.Channel -> _state.update { it.copy(curveChannel = CurveChannel.entries[event.index.coerceIn(0, 3)], hueCurve = null) }
            // A press that adds a point and drags it is one step to undo (release round R10): the add carries the key of
            // the moves that follow it.
            is ColorEvent.CurveAdd -> {
                val index = currentGrade()?.curves?.get(_state.value.curveChannel)?.let { curves.add(it, event.x, event.y).second } ?: -1
                editCurve("添加曲线控制点", index.takeIf { it >= 0 }?.let { "curve-$it" }) { points -> curves.add(points, event.x, event.y).first }
            }
            is ColorEvent.CurveMove -> editCurve("调整曲线", "curve-${event.index}") { points -> curves.move(points, event.index, event.x, event.y) }
            is ColorEvent.CurveRemove -> editCurve("删除曲线控制点", null) { points -> curves.remove(points, event.index) }
            ColorEvent.ResetCurve ->
                if (_state.value.hueCurve != null) editHueCurve("重置曲线", null) { _, _ -> emptyList() } else editCurve("重置曲线", null) { curves.reset() }
            is ColorEvent.QualifierPage -> _state.update { it.copy(qualifierFinesse = event.finesse) }
            is ColorEvent.PickCurve -> _state.update { s ->
                val channels = CurveChannel.entries.size
                if (event.index < channels) s.copy(hueCurve = null, curveChannel = CurveChannel.entries[event.index.coerceAtLeast(0)])
                else s.copy(hueCurve = HueCurve.entries.getOrNull(event.index - channels) ?: HueCurve.entries.last())
            }
            is ColorEvent.HueCurveAdd -> {
                val curve = _state.value.hueCurve
                val index = curve?.let { c -> currentGrade()?.hueCurves?.get(c)?.let { hueCurveEdits.add(it, event.x, c.periodic).second } } ?: -1
                editHueCurve("添加控制点", index.takeIf { it >= 0 }?.let { "hue-curve-$it" }) { points, c -> hueCurveEdits.add(points, event.x, c.periodic).first }
            }
            is ColorEvent.HueCurveMove -> editHueCurve("调整曲线", "hue-curve-${event.index}") { points, curve ->
                hueCurveEdits.move(points, event.index, event.x, event.y, curve.periodic)
            }
            is ColorEvent.HueCurveRemove -> editHueCurve("删除控制点", null) { points, _ -> hueCurveEdits.remove(points, event.index) }
            is ColorEvent.SelectLook -> grade("LUT 风格：${event.look.label}", null) { g -> g.copy(look = event.look, lut = null) }
            is ColorEvent.SelectLut -> selectLut(event.id)
            is ColorEvent.ImportLut -> importLut(event.uri)
            ColorEvent.GenerateLut -> generateLut()
            is ColorEvent.SpatialNr -> spatialNr("降噪：${event.control.label}", "nr-${event.control}") { event.control.write(it, event.value) }
            is ColorEvent.SpatialNrRadius -> spatialNr("降噪半径：${event.radius.label}", null) { it.copy(radius = event.radius) }
            ColorEvent.ResetSpatialNr -> spatialNr("重置降噪", null) { SpatialNoiseReduction.OFF }
            is ColorEvent.RequestRemoveLut -> _state.update { s -> s.copy(lutRemoval = luts.library.value.firstOrNull { it.id == event.id }) }
            ColorEvent.ConfirmRemoveLut -> removeLut()
            ColorEvent.CancelRemoveLut -> _state.update { it.copy(lutRemoval = null) }
            is ColorEvent.LookStrength -> grade("LUT 强度", "look-strength") { g -> g.copy(lookStrength = event.value.toInt().coerceIn(0, 100)) }
            is ColorEvent.SetCompare -> {
                // Before/after and the Gallery wipe share the split: one at a time.
                _state.update { it.copy(compare = event.enabled, wipe = it.wipe && !event.enabled, wipeImage = if (event.enabled) null else it.wipeImage) }
                program.setCompare(if (event.enabled) _state.value.selectedClipId else null, 0.5f)
            }
            ColorEvent.GrabStill -> grabStill()
            is ColorEvent.SelectStill -> {
                _state.update { it.copy(selectedStillId = event.id) }
                publish()
                if (_state.value.wipe) loadWipe()
            }
            ColorEvent.ApplyStill -> applyStill()
            ColorEvent.MatchStill -> matchStill()
            is ColorEvent.SetWipe -> setWipe(event.enabled)
            is ColorEvent.DeleteStill -> doc?.stills?.firstOrNull { it.id == event.id }?.let { still ->
                session.edit("删除${still.label}") { d -> stills.remove(d, still.id) }
                _state.update { it.copy(toast = "已删除${still.label}") }
            }
            ColorEvent.CopyGrade -> selectedClip()?.let { clip ->
                clipboard = clip.grade
                _state.update { it.copy(clipboardEmpty = false, toast = "已复制“${clip.name}”的调色") }
            }
            // Copy, paste and reset take the whole grade, window node included.
            // Keyframes keep their timing from the clip's start, cut where a shorter clip ends.
            ColorEvent.PasteGrade -> clipboard?.let { copied ->
                val frames = selectedClip()?.durationFrames ?: return
                wholeGrade("粘贴调色", null) { copied.keysFor(0, frames) }
            }
            ColorEvent.ResetGrade -> wholeGrade("重置调色", null) { ColorGrade.NEUTRAL }
            is ColorEvent.MidDetail -> wholeGrade("中间调细节", "mid-detail") { it.copy(midDetail = event.value.coerceIn(-100f, 100f)) }
            is ColorEvent.TemporalNr -> wholeGrade("时域降噪", "temporal-nr") { it.copy(temporalNr = event.value.roundToInt().coerceIn(0, 100)) }
            ColorEvent.NewGradeVersion -> versionEdit("新建调色版本") { t, id -> gradeVersions.create(t, id) }
            is ColorEvent.SelectGradeVersion -> versionEdit("切换调色版本") { t, id -> gradeVersions.select(t, id, event.index) }
            is ColorEvent.StepGradeVersion -> {
                val state = _state.value
                val target = state.gradeVersion + event.delta
                when {
                    !state.hasClip -> _state.update { it.copy(toast = "先选择一个画面片段") }
                    target < 0 -> _state.update { it.copy(toast = "已经是第一个调色版本") }
                    target > state.gradeVersions.lastIndex -> _state.update { it.copy(toast = "已经是最后一个调色版本") }
                    else -> versionEdit("切换调色版本") { t, id -> gradeVersions.select(t, id, target) }
                }
            }
            is ColorEvent.ApplyPriorGrade ->
                versionEdit(if (event.steps == 1) "应用前一片段的调色" else "应用前两个片段的调色") { t, id -> priorGrade(t, id, event.steps) }
            ColorEvent.DeleteGradeVersion -> versionEdit("删除调色版本") { t, id -> gradeVersions.delete(t, id) }
            ColorEvent.AutoColor -> autoColor()
            is ColorEvent.ProgramSurface -> program.route(event.change)
            ColorEvent.ToastShown -> _state.update { it.copy(toast = null) }
        }
    }

    private fun selectedClip(): Clip? = doc?.timeline?.clip(_state.value.selectedClipId)

    /** One change to the selected clip's grade (or its versions), one undo step; what was done (or why not) is toasted. */
    private fun versionEdit(label: String, change: (Timeline, String) -> EditResult) {
        val clip = selectedClip() ?: return _state.update { it.copy(toast = "先选择一个画面片段") }
        var message: String? = null
        try {
            session.edit(label) { d -> change(d.timeline, clip.id).let { r -> message = r.message; d.copy(timeline = r.timeline) } }
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        _state.update { it.copy(toast = message) }
    }

    /** An imported LUT in the selected node's look slot; a newly chosen one starts at full strength, as Resolve applies LUTs. */
    private fun selectLut(id: String) {
        val info = luts.library.value.firstOrNull { it.id == id } ?: return
        grade("LUT：${info.name}", null) { g -> if (g.lut?.id == id) g else g.copy(look = Look.NONE, lut = info.ref, lookStrength = 100) }
    }

    /**
     * Resolve's Generate LUT: the selected clip's grade as it stands at the playhead, as a 33-point `.cube`
     * in the library (any project can use it) and in Download/Reality Video Editor (other apps and devices can).
     */
    private fun generateLut() {
        val clip = selectedClip() ?: return
        if (_state.value.lutGenerating) return
        val local = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
        viewModelScope.launch {
            _state.update { it.copy(lutGenerating = true) }
            val toast = try {
                val name = "${clip.name} 调色".take(MAX_LUT_NAME)
                val (generated, bytes) = withContext(compute) {
                    generate(clip.grade, local).let { it to CubeLutWriter.write(it.lut, name).toByteArray() }
                }
                val stored = luts.import("$name.cube", bytes)
                val saved = downloads.save("$name.cube", CUBE_MIME, bytes)
                val where = saved?.let { "，并保存到 $it" } ?: "（未能保存到“下载”文件夹）"
                val notes = listOfNotNull(
                    "按播放头处的调色".takeIf { generated.keyed },
                    "窗口只作用于画面的一部分，未写入".takeIf { generated.windowLeftOut },
                ).joinToString("") { "；$it" }
                if (stored.alreadyThere) {
                    "LUT 库里已有同样的 LUT：${stored.info.name}$where$notes"
                } else {
                    "已生成 LUT“${stored.info.name}”（${stored.info.detail}）：已加入 LUT 库$where$notes"
                }
            } catch (e: EditRejected) {
                e.message
            } catch (e: IOException) {
                "无法保存 LUT：存储空间不足或不可写"
            } finally {
                _state.update { it.copy(lutGenerating = false) }
            }
            _state.update { it.copy(toast = toast) }
        }
    }

    /**
     * Resolve's Grab Still: the selected clip's picture at the playhead as its grade shows it (window node
     * included), fitted into the timeline's aspect, stored with the grade frozen there. One undo step.
     */
    private fun grabStill() {
        val document = doc ?: return
        val clip = selectedClip() ?: return _state.update { it.copy(toast = "先选择一个画面片段") }
        val asset = document.asset(clip.assetId)
        if (asset == null || asset.offline) return _state.update { it.copy(toast = "素材离线，无法抓取静帧") }
        if (_state.value.grabbing) return
        val fps = document.format.fps
        val playhead = _state.value.playheadFrame
        val local = (playhead - clip.start).coerceIn(0, clip.durationFrames - 1)
        val aspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1)
        val width = GalleryUseCase.STILL_WIDTH
        val height = (width / aspect).roundToInt().coerceAtLeast(1)
        viewModelScope.launch {
            _state.update { it.copy(grabbing = true) }
            val toast = try {
                val raw = previews.framePixels(asset.uri, asset.kind, clip.sourceMsAt(local, fps), width, exact = true)
                if (raw == null) {
                    "无法读取这一帧"
                } else {
                    Log.i(TAG, "Grab still: ${clip.name} @$local decoded ${raw.width}x${raw.height}, picture ${width}x$height")
                    val framing = document.framingOf(clip, raw.width.toFloat() / raw.height.coerceAtLeast(1))
                    val picture = withContext(compute) {
                        // The part the frame shows, placed as the viewer shows it (Resolve's Scaling).
                        val framed = scopes.framed(raw, framing)
                        val shown = framing?.inFrame
                        GalleryUseCase.fitted(scopes.grade(framed, clip.grade.windowAt(local), aspect, local.toFloat(), shown), width, height, shown)
                    }
                    val id = ids.next("still")
                    val name = "$id.jpg"
                    if (!gallery.save(document.id, name, picture)) {
                        "无法保存静帧：存储空间不足或不可写"
                    } else {
                        stillThumbs[id] = downsample(picture, THUMB_STEP)
                        val timecode = Timecode.format(playhead, fps)
                        var label = ""
                        session.edit("抓取静帧") { d ->
                            val still = stills.grab(d, d.timeline.clip(clip.id) ?: clip, local, id, name, width, height, timecode)
                            label = still.label
                            stills.add(d, still)
                        }
                        _state.update { it.copy(selectedStillId = id) }
                        publish()
                        if (_state.value.wipe) loadWipe()
                        "已抓取$label：${clip.name} · $timecode"
                    }
                }
            } finally {
                _state.update { it.copy(grabbing = false) }
            }
            _state.update { it.copy(toast = toast) }
        }
    }

    /** Grades the selected clip as the picked still was (Resolve's Apply Grade). */
    private fun applyStill() {
        val clip = selectedClip() ?: return _state.update { it.copy(toast = "先选择一个画面片段") }
        val still = doc?.stills?.firstOrNull { it.id == _state.value.selectedStillId } ?: return _state.update { it.copy(toast = "先在画廊中选择一张静帧") }
        try {
            session.edit("应用${still.label}的调色") { d -> stills.apply(d, still.id, clip.id) }
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        _state.update { it.copy(toast = "已把${still.label}的调色应用到“${clip.name}”") }
    }

    /** Wipes the picked still against the picture: it fills the monitor's left half; before/after goes off. */
    private fun setWipe(enabled: Boolean) {
        if (enabled && doc?.stills?.none { it.id == _state.value.selectedStillId } != false) {
            return _state.update { it.copy(toast = "先抓取或选择一张静帧") }
        }
        if (enabled && _state.value.compare) program.setCompare(null, 0.5f)
        _state.update { it.copy(wipe = enabled, compare = it.compare && !enabled, wipeImage = if (enabled) it.wipeImage else null) }
        if (enabled) loadWipe()
    }

    /** Reads the picked still's picture for the wipe. */
    private fun loadWipe() {
        val document = doc ?: return
        val still = document.stills.firstOrNull { it.id == _state.value.selectedStillId } ?: return
        viewModelScope.launch {
            val picture = gallery.load(document.id, still.image, GalleryUseCase.STILL_WIDTH)
            if (_state.value.wipe && _state.value.selectedStillId == still.id) {
                _state.update { it.copy(wipeImage = picture, wipeLabel = still.label) }
                if (picture == null) _state.update { it.copy(toast = "静帧图片缺失：${still.label}") }
            }
        }
    }

    /** Asks for a still's tile picture once. */
    private fun requestStillThumb(projectId: String, id: String, image: String) {
        if (id in stillThumbs || !stillsRequested.add(id)) return
        viewModelScope.launch {
            stillThumbs[id] = gallery.load(projectId, image, STILL_THUMB_WIDTH)
            stillsRequested.remove(id)
            publish()
        }
    }

    /** Takes the LUT awaiting confirmation out of the library; grades naming it grade without it, said missing. */
    private fun removeLut() {
        val info = _state.value.lutRemoval ?: return
        _state.update { it.copy(lutRemoval = null) }
        viewModelScope.launch {
            luts.remove(info.id)
            program.refreshLooks()
            val used = selectedClip()?.grade?.let { g -> g.lut?.id == info.id || g.window?.grade?.lut?.id == info.id } == true
            _state.update { it.copy(toast = "已从 LUT 库移除“${info.name}”" + if (used) "；当前片段的调色显示 LUT 缺失" else "") }
        }
    }

    /** Reads a picked `.cube` file into the library and, with a clip selected, applies it to the selected node. */
    private fun importLut(uri: String) {
        viewModelScope.launch {
            _state.update { it.copy(lutImporting = true) }
            val imported = try {
                val file = documents.read(uri, MAX_LUT_BYTES)
                luts.import(file.name, file.bytes)
            } catch (e: DocumentFailure) {
                null.also { _state.update { s -> s.copy(toast = e.message ?: "无法读取文件") } }
            } catch (e: LutFormatException) {
                null.also { _state.update { s -> s.copy(toast = "无法导入 LUT：${e.message}") } }
            } catch (e: IOException) {
                null.also { _state.update { s -> s.copy(toast = "无法保存 LUT：存储空间不足或不可写") } }
            } finally {
                _state.update { it.copy(lutImporting = false) }
            }
            imported ?: return@launch
            val info = imported.info
            val clip = selectedClip()
            if (clip != null) selectLut(info.id)
            // A grade that already named this LUT (missing until now) grades with it at once.
            program.refreshLooks()
            val applied = clip?.let { "，已应用到“${it.name}”" }.orEmpty()
            val toast = if (imported.alreadyThere) "LUT 已在库中：${info.name}$applied" else "已导入 LUT：${info.name}（${info.detail}）$applied"
            _state.update { it.copy(toast = toast) }
        }
    }

    /**
     * Makes the picture clip under [frame] (the one the monitor and scopes show) the current clip when the
     * playhead has left the current one. In a gap the current clip stays.
     */
    private fun followPlayhead(frame: Int) {
        val document = doc ?: return
        val current = selectedClip()
        if (current != null && frame in current) return
        val under = videoClips(document).firstOrNull { frame in it && document.timeline.track(it.trackId)?.enabled == true } ?: return
        if (under.id != current?.id) selectClip(under.id)
    }

    /** Resolve's Clips filter: the strip narrows to what [filter] keeps, and a hidden current clip gives way to its first. */
    private fun setClipFilter(filter: ClipFilter) {
        val document = doc ?: return
        _state.update { it.copy(clipFilter = filter) }
        val current = _state.value.selectedClipId
        val target = clipFilters.selectionFor(videoClips(document), filter, current, flagsOf(document))
        if (target != null && target != current) selectClip(target) else publish()
    }

    /**
     * Resolve's Previous / Next Clip on the Color page: the strip's clip before / after the current one (the filter's
     * strip), graded from its first frame; at an end of the strip it says so.
     */
    private fun stepClip(forward: Boolean) {
        val document = doc ?: return
        val clips = videoClips(document)
        val filter = _state.value.clipFilter
        val flags = flagsOf(document)
        val target = clipFilters.step(clips, filter, _state.value.selectedClipId, forward, flags)
            ?: return _state.update { it.copy(toast = clipFilters.stepRefusal(clips, filter, forward, flags)) }
        selectClip(target.id, fromStart = true)
    }

    /**
     * The mini timeline (Resolve's): the playhead to [frame], playback paused. A press on a clip grades that clip first,
     * so a V2 clip over V1 can be taken; otherwise the clip under the playhead is graded, as whenever it moves.
     */
    private fun scrub(frame: Int, clipId: String?) {
        val document = doc ?: return
        val at = frame.coerceIn(0, (document.timeline.durationFrames - 1).coerceAtLeast(0))
        program.pause()
        document.timeline.clip(clipId)?.takeIf { at in it && it.id != _state.value.selectedClipId }?.let { selectClip(it.id, seek = false) }
        program.seekTo(Timecode.frameToSeekMs(at, document.format.fps))
    }

    private fun selectClip(clipId: String, fromStart: Boolean = false, seek: Boolean = true) {
        val document = doc ?: return
        val clip = document.timeline.clip(clipId) ?: return
        _state.update { it.copy(selectedClipId = clipId) }
        if (_state.value.compare) program.setCompare(clipId, 0.5f)
        if (_state.value.highlight) program.setHighlight(clipId)
        if (seek && (_state.value.playheadFrame !in clip || (fromStart && _state.value.playheadFrame != clip.start))) {
            program.seekTo(Timecode.frameToSeekMs(clip.start, document.format.fps))
            _state.update { it.copy(playheadFrame = clip.start) }
        }
        publish()
        analyse(refetch = true)
    }

    /**
     * Changes the selected node's grade (labels name node 2 when it is the one). A keyed node 1 changes at
     * the playhead — the keyframe there, added if needed; with the playhead outside the clip it cannot.
     */
    private fun grade(label: String, key: String?, change: (ColorGrade) -> ColorGrade) {
        val node = _state.value.node
        val named = when {
            node == GradeNode.WINDOW -> "窗口 · $label"
            node.isSerial -> "节点 ${node.number} · $label"
            else -> label
        }
        val clip = selectedClip() ?: return
        // Only node 1 is keyframed: the window node and the serial nodes change outright.
        if (node != GradeNode.PRIMARY || clip.grade.keyframes.isEmpty()) {
            wholeGrade(named, key?.let { "$it-$node" }) { g -> g.withNode(node, change) }
            return
        }
        val local = _state.value.playheadFrame - clip.start
        try {
            gradeKeys.adjust(clip.grade, local, clip.durationFrames, change)
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        // Keyed edits at different frames are separate steps.
        wholeGrade(named, key?.let { "$it-$node@$local" }) { g -> gradeKeys.adjust(g, local, clip.durationFrames, change) }
    }

    /** Applies a keyframe edit of the selected clip's node 1 at the playhead (refused edits explain why). */
    private fun gradeKeyframe(label: String, edit: (ColorGrade, Int, Int) -> GradeEdit) {
        val clip = selectedClip() ?: return
        val local = _state.value.playheadFrame - clip.start
        val result = try {
            edit(clip.grade, local, clip.durationFrames)
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        wholeGrade(label, null) { result.grade }
        _state.update { it.copy(toast = result.message) }
    }

    /** Moves the playhead to the node 1 keyframe [find] picks from the playhead, if there is one. */
    private fun seekGradeKeyframe(find: (ColorGrade, Int) -> Int?) {
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val frame = find(clip.grade, _state.value.playheadFrame - clip.start) ?: return
        program.seekTo(Timecode.frameToSeekMs(clip.start + frame, document.format.fps))
    }

    /** Node 1's keyframe controls for [clip] with the playhead at [frame]. */
    private fun gradeKeyUi(clip: Clip?, frame: Int): KeyframeUi {
        clip ?: return KeyframeUi()
        val local = frame - clip.start
        val inside = local in 0 until clip.durationFrames
        val keys = clip.grade.keyframes
        val key = clip.grade.gradeKeyframeAt(local)?.takeIf { inside }
        return keyframeUi(
            frames = keys.map { it.frame },
            key = key?.ease,
            local = local,
            inside = inside,
            hint = when {
                keys.isEmpty() -> "添加关键帧后，调色可以随时间变化，跟上光线"
                !inside -> "播放头不在片段内：移进片段再调色"
                key != null -> "调整会记录在此关键帧 · 可设缓入缓出"
                else -> "调整节点 01，会在播放头处添加关键帧"
            },
        )
    }

    private fun wholeGrade(label: String, key: String?, change: (ColorGrade) -> ColorGrade) {
        val clip = selectedClip() ?: return
        session.edit(label, key?.let { "$it:${clip.id}" }) { d ->
            val current = d.timeline.clip(clip.id) ?: return@edit d
            d.copy(timeline = d.timeline.replaceClip(current.copy(grade = change(current.grade))))
        }
    }

    /** Changes the selected clip's Spatial NR: node 01's, not keyframed, whichever node is selected. */
    private fun spatialNr(label: String, key: String?, change: (SpatialNoiseReduction) -> SpatialNoiseReduction) =
        wholeGrade(label, key) { g -> g.copy(spatialNr = change(g.spatialNr)) }

    /** Changes the selected clip's window, if it has one. */
    private fun window(label: String, key: String?, change: (PowerWindow) -> PowerWindow) {
        if (selectedClip()?.grade?.window == null) return
        wholeGrade(label, key) { g -> g.copy(window = g.window?.let(change)) }
    }

    /**
     * Moves or reshapes the selected clip's window. A keyed window changes at the playhead (the
     * keyframe there, added if needed); with the playhead outside the clip it cannot change.
     */
    private fun windowGeometry(label: String, key: String?, change: (PowerWindow) -> PowerWindow) {
        val clip = selectedClip() ?: return
        val current = clip.grade.window ?: return
        val local = _state.value.playheadFrame - clip.start
        try {
            windowKeys.adjust(current, local, clip.durationFrames, change = change)
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        // Keyed edits at different frames are separate steps.
        val at = if (current.keyframes.isEmpty()) "" else "@$local"
        window(label, key?.let { it + at }) { w -> windowKeys.adjust(w, local, clip.durationFrames, change = change) }
    }

    /** Applies a keyframe edit of the selected clip's window at the playhead (refused edits explain why). */
    private fun windowKeyframe(label: String, edit: (PowerWindow, Int, Int) -> WindowEdit) {
        val clip = selectedClip() ?: return
        val current = clip.grade.window ?: return
        val local = _state.value.playheadFrame - clip.start
        val result = try {
            edit(current, local, clip.durationFrames)
        } catch (e: EditRejected) {
            return _state.update { it.copy(toast = e.message) }
        }
        window(label, null) { result.window }
        _state.update { it.copy(toast = result.message) }
    }

    /**
     * Resolve's Tracker: reads the frames the selected clip shows, follows the picture under its window from the playhead
     * to both ends, and keys the window along the path — one undo step ("跟踪窗口"), replacing its keyframes. The
     * status says how far reading and following are; a cancelled run changes nothing.
     */
    private fun trackWindow() {
        if (trackJob?.isActive == true) return
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val asset = document.asset(clip.assetId)
        tracker.unavailable(clip, asset)?.let { reason -> return _state.update { it.copy(toast = reason) } }
        val window = clip.grade.window ?: return
        val fps = document.format.fps
        if (clip.durationFrames > WindowTrackUseCase.MAX_FRAMES) return _state.update { it.copy(toast = "片段太长：先切短再跟踪") }
        val start = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
        val motion = _state.value.trackMotion
        trackJob = viewModelScope.launch {
            _state.update { it.copy(tracking = true, trackStatus = "正在读取画面 0%") }
            try {
                val frames = lumaFrames.read(asset!!, clip.sourceInMs, clip.sourceInMs + clip.sourceDurationMs(fps), WindowTrackUseCase.WIDTH) { p ->
                    _state.update { it.copy(trackStatus = "正在读取画面 ${(p * 100).roundToInt()}%") }
                }
                val keys = withContext(compute) {
                    tracker.keyframes(window, clip, start, frames, fps, motion) { p ->
                        _state.update { it.copy(trackStatus = "正在跟踪 ${(p * 100).roundToInt()}%") }
                    }
                }
                session.edit("跟踪窗口", null) { d ->
                    val current = d.timeline.clip(clip.id) ?: return@edit d
                    val w = current.grade.window ?: return@edit d
                    d.copy(timeline = d.timeline.replaceClip(current.copy(grade = current.grade.copy(window = w.copy(keyframes = keys)))))
                }
                val moved = (tracker.travel(keys) * 100).roundToInt()
                _state.update { it.copy(toast = "已跟踪窗口：${keys.size} 个关键帧，最多移动画面的 $moved%${tracker.motionNote(keys, motion)}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tracking failed", e)
                _state.update { it.copy(toast = "无法跟踪：读取画面时出错") }
            } finally {
                _state.update { it.copy(tracking = false, trackStatus = "") }
            }
        }
    }

    /** Moves the playhead to the window keyframe [find] picks from the playhead, if there is one. */
    private fun seekWindowKeyframe(find: (PowerWindow, Int) -> Int?) {
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val window = clip.grade.window ?: return
        val frame = find(window, _state.value.playheadFrame - clip.start) ?: return
        program.seekTo(Timecode.frameToSeekMs(clip.start + frame, document.format.fps))
    }

    /** The window keyframe controls of [clip] with the playhead at [frame]. */
    private fun windowKeyUi(clip: Clip?, frame: Int): KeyframeUi {
        val window = clip?.grade?.window?.takeUnless { it.shape == WindowShape.FULL } ?: return KeyframeUi()
        val local = frame - clip.start
        val inside = local in 0 until clip.durationFrames
        val key = window.keyframeAt(local)?.takeIf { inside }
        return keyframeUi(
            frames = window.keyframes.map { it.frame },
            key = key?.ease,
            local = local,
            inside = inside,
            hint = when {
                window.keyframes.isEmpty() -> "添加关键帧后，窗口可以随时间移动，跟住移动的主体"
                !inside -> "播放头不在片段内：移进片段再调整窗口"
                key != null -> "调整会记录在此关键帧 · 可设缓入缓出"
                else -> "移动或调整窗口，会在播放头处添加关键帧"
            },
        )
    }

    /**
     * Resolve's Auto Color: the selected clip's picture at the playhead, ungraded, sets node 1's Lift and
     * Gain (keyed node 1: at the playhead), and node 1 becomes the selected node.
     */
    private fun autoColor() {
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val asset = document.asset(clip.assetId)?.takeUnless { it.offline }
            ?: return _state.update { it.copy(toast = "素材离线，无法自动调色") }
        if (_state.value.node != GradeNode.PRIMARY) selectNode(GradeNode.PRIMARY)
        viewModelScope.launch {
            val fps = document.format.fps
            val offset = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
            val picture = previews.framePixels(asset.uri, asset.kind, clip.sourceMsAt(offset, fps), FRAME_WIDTH, exact = true)
                ?: return@launch _state.update { it.copy(toast = "无法读取这一帧的画面") }
            val result = try {
                withContext(compute) { autoColors.analyse(picture) }
            } catch (e: EditRejected) {
                return@launch _state.update { it.copy(toast = e.message) }
            }
            val before = selectedClip()?.grade
            grade("自动调色", null) { g -> result.applyTo(g) }
            // A keyed node 1 with the playhead outside the clip refuses, and says why.
            if (selectedClip()?.grade != before) _state.update { it.copy(toast = result.message) }
        }
    }

    /**
     * Resolve's Shot Match against the picked Gallery still: node 1's wheels (keyed node 1: at the playhead) set
     * so the clip's picture at the playhead takes the still's blacks, mid-tones and whites, through the rest of
     * node 1 as it stands there; node 1 becomes the selected node.
     */
    private fun matchStill() {
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val still = document.stills.firstOrNull { it.id == _state.value.selectedStillId }
            ?: return _state.update { it.copy(toast = "先在画廊里选一张静帧") }
        val asset = document.asset(clip.assetId)?.takeUnless { it.offline }
            ?: return _state.update { it.copy(toast = "素材离线，无法匹配") }
        if (_state.value.matching) return
        if (_state.value.node != GradeNode.PRIMARY) selectNode(GradeNode.PRIMARY)
        _state.update { it.copy(matching = true) }
        viewModelScope.launch {
            try {
                val fps = document.format.fps
                val offset = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
                val picture = previews.framePixels(asset.uri, asset.kind, clip.sourceMsAt(offset, fps), FRAME_WIDTH, exact = true)
                    ?: return@launch _state.update { it.copy(toast = "无法读取这一帧的画面") }
                val reference = gallery.load(document.id, still.image, MATCH_WIDTH)
                    ?: return@launch _state.update { it.copy(toast = "静帧的图片已丢失，无法匹配") }
                val node1 = clip.grade.at(offset).primary
                val result = withContext(compute) { shots.match(picture, reference, node1) }
                val before = selectedClip()?.grade
                grade("镜头匹配", null) { g ->
                    val m = result.grade
                    g.copy(offset = m.offset, lift = m.lift, gamma = m.gamma, gain = m.gain, temperature = m.temperature, tint = m.tint)
                }
                // A keyed node 1 with the playhead outside the clip refuses, and says why.
                if (selectedClip()?.grade != before) _state.update { it.copy(toast = result.message.replaceFirst("静帧", still.label)) }
            } catch (e: EditRejected) {
                _state.update { it.copy(toast = e.message) }
            } finally {
                _state.update { it.copy(matching = false) }
            }
        }
    }

    /** Resolve's Add Serial Node (Alt+S): a neutral whole-picture node after the last, selected for grading. */
    private fun addSerialNode() {
        val clip = selectedClip() ?: return
        val count = clip.grade.serial.size
        if (count >= GradeNode.MAX_SERIAL) {
            return _state.update { it.copy(toast = "最多 ${GradeNode.MAX_SERIAL + 2} 个节点：节点 01、02 和串行节点 03–0${GradeNode.MAX_SERIAL + 2}") }
        }
        val node = GradeNode.serial(count)
        wholeGrade("添加节点 ${node.number}", null) { g -> g.copy(serial = g.serial + SerialNode()) }
        // Its corrections are the wheels', sliders', curves' and looks': back to them from the window or qualifier tab.
        val tab = _state.value.adjustTab.takeUnless { it == ColorUiState.TAB_WINDOW || it == ColorUiState.TAB_QUALIFIER } ?: ColorUiState.TAB_PRIMARIES
        _state.update { it.copy(node = node, adjustTab = tab, toast = "已添加节点 ${node.number}：调整整个画面，在前面的节点之后") }
        publish()
        analyse(refetch = false)
    }

    /** Resolve's Enable / Disable Node (Ctrl+D) for a serial node: the picture skips it, its settings kept. */
    private fun toggleNode(node: GradeNode) {
        val clip = selectedClip() ?: return
        val current = clip.grade.serial.getOrNull(node.serialIndex)?.takeIf { node.isSerial }
            ?: return _state.update { it.copy(toast = "节点 01、02 不能停用：可以重置它们的调色") }
        val on = !current.enabled
        wholeGrade(if (on) "启用节点 ${node.number}" else "停用节点 ${node.number}", null) { g ->
            g.copy(serial = g.serial.mapIndexed { i, n -> if (i == node.serialIndex) n.copy(enabled = on) else n })
        }
        _state.update { it.copy(toast = if (on) "已启用节点 ${node.number}" else "已停用节点 ${node.number}：画面跳过这个节点，设置保留") }
        publish()
        analyse(refetch = false)
    }

    /** A serial node removed; the ones after it move up a place, and the one before it is selected. */
    private fun deleteNode(node: GradeNode) {
        val clip = selectedClip() ?: return
        if (!node.isSerial || node.serialIndex !in clip.grade.serial.indices) return
        wholeGrade("删除节点 ${node.number}", null) { g -> g.copy(serial = g.serial.filterIndexed { i, _ -> i != node.serialIndex }) }
        val before = when {
            node.serialIndex > 0 -> GradeNode.serial(node.serialIndex - 1)
            clip.grade.window != null -> GradeNode.WINDOW
            else -> GradeNode.PRIMARY
        }
        _state.update { it.copy(node = before, toast = "已删除节点 ${node.number}") }
        publish()
        analyse(refetch = false)
    }

    private fun selectNode(node: GradeNode) {
        val clip = selectedClip() ?: return
        if (node == GradeNode.WINDOW && clip.grade.window == null) {
            addWindow(PowerWindow())
        } else {
            _state.update { it.copy(node = node) }
        }
        publish()
        // The look previews show the selected node.
        analyse(refetch = false)
    }

    /** Resolve's Window palette: picking a shape adds the window (node 2), 无 removes it (a qualifier stays, on the whole frame). */
    private fun setWindowShape(shape: WindowShape?) {
        val current = selectedClip()?.grade?.window
        when {
            shape == null -> if (current?.qualifier != null) {
                if (current.shape != WindowShape.FULL) {
                    window("移除窗口", null) { it.copy(shape = WindowShape.FULL) }
                    _state.update { it.copy(toast = "已移除窗口，限定器保留在全画面") }
                }
            } else if (current != null) {
                wholeGrade("移除窗口", null) { g -> g.copy(window = null) }
                _state.update { it.copy(node = GradeNode.PRIMARY, toast = "已移除窗口") }
            }
            current == null -> addWindow(PowerWindow(shape = shape))
            current.shape != shape -> window("窗口形状：${shape.label}", null) { it.copy(shape = shape) }
        }
        publish()
    }

    private fun addWindow(window: PowerWindow) {
        if (selectedClip() == null) return
        wholeGrade("添加窗口", null) { g -> g.copy(window = window) }
        _state.update {
            it.copy(
                node = GradeNode.WINDOW,
                adjustTab = ColorUiState.TAB_WINDOW,
                toast = "已添加${window.shape.label}窗口：节点 2 的调色只作用于窗口内",
            )
        }
    }

    /** Changes the selected clip's qualifier, if it has one. */
    private fun qualifier(label: String, key: String?, change: (HslQualifier) -> HslQualifier) {
        if (selectedClip()?.grade?.window?.qualifier == null) return
        window(label, key) { w -> w.copy(qualifier = w.qualifier?.let(change)) }
    }

    /**
     * Resolve's qualifier eyedropper: the colour node 2 receives (the primary-graded source) at the
     * tapped point of the selected clip becomes the qualifier's centre. A clip without node 2 gets one on
     * the whole frame.
     */
    private fun pickColor(u: Float, v: Float) {
        val document = doc ?: return
        val clip = selectedClip() ?: return
        val mode = _state.value.pickMode
        // A pick ends the eyedropper; adding and subtracting stay on for the next colour, as Resolve's pickers do.
        if (mode == PickMode.PICK) _state.update { it.copy(eyedropper = false, pickOverlay = null) }
        val asset = document.asset(clip.assetId)?.takeUnless { it.offline } ?: return
        viewModelScope.launch {
            val fps = document.format.fps
            val offset = (_state.value.playheadFrame - clip.start).coerceIn(0, clip.durationFrames - 1)
            val picture = previews.framePixels(asset.uri, asset.kind, clip.sourceMsAt(offset, fps), FRAME_WIDTH, exact = true)
            val aspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1)
            val source = picture?.let { scopes.sourcePixel(it, u, v, aspect, document.framingOf(clip, it.width.toFloat() / it.height.coerceAtLeast(1))) }
            if (source == null) {
                _state.update { it.copy(toast = if (picture == null) "无法读取这一帧的颜色" else "请点在画面上") }
                return@launch
            }
            val node1 = clip.grade.copy(window = null)
            val p = withContext(compute) { scopes.grade(FramePixels(1, 1, intArrayOf(source)), node1, clipFrame = offset.toFloat()).pixels[0] }
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            if (mode != PickMode.PICK) return@launch refineQualifier(mode, r, g, b)
            val picked = HslQualifier.picked(r, g, b)
            wholeGrade("吸取颜色", null) { g ->
                // A new colour keeps the matte finesse already set.
                val old = g.window?.qualifier
                val refined = old?.let { picked.copy(cleanBlack = it.cleanBlack, cleanWhite = it.cleanWhite, blur = it.blur) } ?: picked
                g.copy(window = (g.window ?: PowerWindow(shape = WindowShape.FULL)).copy(qualifier = refined))
            }
            val what = if (picked.hueWidth >= HslQualifier.FULL_HUE) "所选的灰色" else "所选颜色（色相 ${picked.hueCenter.roundToInt()}°）"
            _state.update {
                it.copy(node = GradeNode.WINDOW, adjustTab = ColorUiState.TAB_QUALIFIER, toast = "已按${what}建立限定器：节点 2 的调色只作用于这些颜色")
            }
            publish()
        }
    }

    /**
     * Resolve's picker add / subtract: the qualifier's ranges grow to take in the colour [r], [g], [b] node 2
     * receives, or shrink to leave it out (one undo step each); the eyedropper stays on for the next colour.
     */
    private fun refineQualifier(mode: PickMode, r: Float, g: Float, b: Float) {
        val q = selectedClip()?.grade?.window?.qualifier ?: return _state.update { it.copy(toast = "先吸取颜色建立限定器") }
        if (_state.value.featherPick) return featherQualifier(q, mode == PickMode.ADD, r, g, b)
        val adding = mode == PickMode.ADD
        val refined = if (adding) q.adding(r, g, b) else q.subtracting(r, g, b)
        if (refined == null) {
            val selected = q.rangeKey(r, g, b).let { if (q.invert) 1f - it else it }
            val why = when {
                adding -> if (selected >= 0.98f) "这个颜色已在选区里" else "这个颜色无法加入选区"
                selected <= 0.02f -> "这个颜色不在选区里"
                else -> "这个颜色在选区正中，无法只减去它"
            }
            return _state.update { it.copy(toast = why) }
        }
        qualifier(if (adding) "限定器：加选颜色" else "限定器：减选颜色", null) { refined }
        _state.update { it.copy(toast = if (adding) "已加选：选区扩大到包含这个颜色" else "已减选：选区不再包含这个颜色") }
        publish()
    }

    /**
     * Resolve's feather add / subtract (柔化 with 加选 / 减选): the soft edge grows until the colour [r], [g], [b] node 2
     * receives is about half selected, or is pulled back past it; the ranges themselves stay (one undo step each).
     */
    private fun featherQualifier(q: HslQualifier, adding: Boolean, r: Float, g: Float, b: Float) {
        val refined = if (adding) q.featheringIn(r, g, b) else q.featheringOut(r, g, b)
        if (refined == null) {
            val k = q.rangeKey(r, g, b)
            val why = when {
                adding && k >= 0.98f -> "这个颜色已在选区里"
                adding && k >= 0.5f -> "这个颜色已在选区的柔和边缘里"
                adding && HslQualifier.chroma(r, g, b) < 0.08f -> "灰色没有色相可以柔化，请用加选"
                adding -> "这个颜色离选区太远，柔化够不到它，请用加选"
                k <= 0.02f -> "这个颜色不在选区里"
                else -> "这个颜色在选区的核心里，请用减选"
            }
            return _state.update { it.copy(toast = why) }
        }
        qualifier(if (adding) "限定器：柔化加选" else "限定器：柔化减选", null) { refined }
        _state.update { it.copy(toast = if (adding) "已柔化加选：这个颜色约一半被选中，选区边缘更柔和" else "已柔化减选：这个颜色移出了选区的柔和边缘") }
        publish()
    }

    /** What a tap on the monitor picks from, while the eyedropper is on and the selected clip shows at the playhead. */
    private fun pickOverlayFor(clip: Clip?, state: ColorUiState): PickOverlayUi? {
        if (!state.eyedropper || clip == null || state.playheadFrame !in clip) return null
        val mirrored = clip.effects.any { it.enabled && it.type == EffectType.MIRROR }
        return PickOverlayUi(clip.transformAt(state.playheadFrame - clip.start), mirrored, state.pickMode.hint(state.featherPick))
    }

    /** The window outline while it is edited (node 2, or the window palette) over a clip the monitor shows. */
    private fun overlayFor(clip: Clip?, state: ColorUiState): WindowOverlayUi? {
        val window = clip?.grade?.window?.takeUnless { it.shape == WindowShape.FULL } ?: return null
        if (state.node != GradeNode.WINDOW && state.adjustTab != ColorUiState.TAB_WINDOW) return null
        if (state.playheadFrame !in clip) return null
        val mirrored = clip.effects.any { it.enabled && it.type == EffectType.MIRROR }
        val local = state.playheadFrame - clip.start
        return WindowOverlayUi(window.at(local), clip.transformAt(local), mirrored)
    }

    /** The grade of the node being edited on the selected clip. */
    private fun currentGrade(): ColorGrade? = selectedClip()?.grade?.node(_state.value.node)

    private fun editHueCurve(label: String, key: String?, change: (List<CurvePoint>, HueCurve) -> List<CurvePoint>) {
        val curve = _state.value.hueCurve ?: return
        grade("${curve.label}：$label", key?.let { "$it-$curve" }) { g -> g.copy(hueCurves = g.hueCurves.with(curve, change(g.hueCurves[curve], curve))) }
    }

    private fun editCurve(label: String, key: String?, change: (List<CurvePoint>) -> List<CurvePoint>) {
        val channel = _state.value.curveChannel
        grade(label, key?.let { "$it-$channel" }) { g -> g.copy(curves = g.curves.with(channel, change(g.curves[channel]))) }
    }

    private fun publish() {
        val document = doc ?: return
        val fps = document.format.fps
        val clips = videoClips(document)
        val selected = clips.firstOrNull { it.id == _state.value.selectedClipId }
            ?: clips.firstOrNull { _state.value.playheadFrame in it } ?: clips.firstOrNull()
        clips.forEach(::requestThumb)
        val stored = selected?.grade ?: ColorGrade.NEUTRAL
        // A keyed node 1 shows its correction at the playhead.
        val clipGrade = selected?.let { stored.at(_state.value.playheadFrame - it.start) } ?: stored
        // Node 2 exists only with a window, a serial node only while it is there: otherwise node 1 is edited.
        val requested = _state.value.node
        val node = when {
            requested == GradeNode.WINDOW && clipGrade.window == null -> GradeNode.PRIMARY
            requested.isSerial && requested.serialIndex !in clipGrade.serial.indices -> GradeNode.PRIMARY
            else -> requested
        }
        val shown = clipGrade.node(node) ?: ColorGrade.NEUTRAL
        val library = luts.library.value
        document.stills.forEach { requestStillThumb(document.id, it.id, it.image) }
        // The picked still stays picked while it exists; otherwise the newest.
        val picked = _state.value.selectedStillId?.takeIf { id -> document.stills.any { it.id == id } } ?: document.stills.lastOrNull()?.id
        val wipeGone = _state.value.wipe && picked == null
        val strip = clips.mapIndexed { i, clip ->
            GradeClipUi(
                id = clip.id,
                index = String.format(Locale.US, "%02d", i + 1),
                name = clip.name,
                poster = document.asset(clip.assetId)?.poster ?: PosterColors.NEUTRAL,
                pixels = gradedThumb(clip),
                graded = !clip.grade.isNeutral,
                color = clip.color,
                flags = document.asset(clip.assetId)?.flags.orEmpty(),
            )
        }
        _state.update { s ->
            // The filter narrows the strip only: numbering, the monitor and the clip being graded follow the timeline.
            val flags = flagsOf(document)
            val kept = clipFilters.apply(clips, s.clipFilter, flags).map { it.id }.toSet()
            val options = clipFilters.options(clips, s.clipFilter, flags)
            s.copy(
                stills = document.stills.map { StillUi(it.id, it.label, "${it.clipName} · ${it.timecode}", stillThumbs[it.id]) },
                selectedStillId = picked,
                wipe = s.wipe && !wipeGone,
                wipeImage = if (wipeGone) null else s.wipeImage,
                lutMissing = shown.lut?.takeIf { ref -> library.none { it.id == ref.id } }?.name,
                clips = strip,
                stripClips = strip.filter { it.id in kept },
                clipCountLabel = clipFilters.countLabel(kept.size, clips.size, s.clipFilter),
                clipFilterOptions = options.general.map { ClipFilterOptionUi(it.filter, it.count, it.filter == s.clipFilter) },
                clipColorFilters = options.colors.map { ClipFilterOptionUi(it.filter, it.count, it.filter == s.clipFilter) },
                clipFlagFilters = options.flags.map { ClipFilterOptionUi(it.filter, it.count, it.filter == s.clipFilter) },
                stripMessage = if (clips.isNotEmpty() && kept.isEmpty()) clipFilters.emptyMessage(s.clipFilter) else null,
                miniTimeline = miniTimeline(document, clips, kept, selected?.id),
                selectedClipId = selected?.id,
                gradeVersions = selected?.let { c -> gradeVersions.versions(c).map { it.name } }.orEmpty(),
                gradeVersion = selected?.let(gradeVersions::active) ?: 0,
                node = node,
                grade = shown,
                spatialNr = stored.spatialNr,
                midDetail = stored.midDetail,
                temporalNr = stored.temporalNr,
                window = selected?.let { clipGrade.window?.at(s.playheadFrame - it.start) },
                windowKeys = windowKeyUi(selected, s.playheadFrame),
                trackUnavailable = if (selected == null) "先选择一个片段" else tracker.unavailable(selected, document.asset(selected.assetId)),
                gradeKeys = gradeKeyUi(selected, s.playheadFrame),
                secondaryLabel = secondaryLabel(clipGrade.window),
                serialNodes = clipGrade.serial.mapIndexed { i, n -> SerialNodeUi(GradeNode.serial(i), !n.grade.isNeutral, n.enabled) },
                canAddNode = selected != null && clipGrade.serial.size < GradeNode.MAX_SERIAL,
                primaryGraded = !clipGrade.primary.isNeutral || stored.keyframes.isNotEmpty(),
                readouts = GradeWheel.entries.associateWith { w -> readout(shown.wheel(w)) },
                fps = fps,
                pictureAspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1),
                programMessage = if (clips.isEmpty()) "时间线上还没有画面片段" else null,
            ).let {
                // Adding and subtracting need a qualifier: without one (removed, undone, another clip) they stop.
                val armed = it.eyedropper && (it.pickMode == PickMode.PICK || selected?.grade?.window?.qualifier != null)
                it.copy(eyedropper = armed).let { a -> a.copy(windowOverlay = overlayFor(selected, a), pickOverlay = pickOverlayFor(selected, a)) }
            }
        }
    }

    /**
     * The mini timeline: the picture tracks top to bottom as on the Edit page, the strip's clips on them; clips the
     * filter hides, switched off or on a track that is off read faint.
     */
    /** Resolve's flags of the media, by asset id (a clip shows its media's). */
    private fun flagsOf(document: ProjectDocument) = document.media.filter { it.flags.isNotEmpty() }.associate { it.id to it.flags }

    private fun miniTimeline(document: ProjectDocument, clips: List<Clip>, kept: Set<String>, selectedId: String?): MiniTimelineUi =
        MiniTimelineUi.of(document, clips, kept, selectedId)

    /** Node 2 named after what limits it. */
    private fun secondaryLabel(window: PowerWindow?): String = when {
        window?.qualifier == null -> GradeNode.WINDOW.label
        window.shape == WindowShape.FULL -> "限定器"
        else -> "窗口 · 限定器"
    }

    // A compound clip is graded inside: opened, its clips are the ones listed.
    private fun videoClips(document: ProjectDocument) =
        document.timeline.clips.filter { it.kind == ClipKind.VIDEO && !it.isCompound }.sortedWith(compareBy({ it.start }, { it.trackId }))

    /** "红 · 外圈": the warper point's spoke and ring. */
    private fun warperPointName(spoke: Int, ring: Int) = "${ColorWarper.SPOKE_NAMES[spoke]} · ${ColorWarper.RING_NAMES[ring]}"

    private fun readout(value: WheelValue): String {
        val rgb = lut.wheelReadout(value)
        return String.format(Locale.US, "R %.2f  G %.2f  B %.2f", rgb[0], rgb[1], rgb[2])
    }

    private fun requestThumb(clip: Clip) {
        val asset = doc?.asset(clip.assetId) ?: return
        // The asset's file is part of the key: a replace keeps the clip's id but changes its media, a relink its file.
        val key = "${clip.id}@${asset.uri}@${clip.sourceInMs / 500}"
        if (!requested.add(key)) return
        // One picture per file and second, shared by every clip starting there: a long timeline cut from a few files kept
        // a 128-px picture per clip, 26–44 MB on a 30-minute, 900-shot timeline (R21).
        val at = clip.sourceInMs / THUMB_SHARE_MS * THUMB_SHARE_MS
        val shared = "${asset.uri}@$at@${asset.hdrByOverride}"
        sharedThumbs[shared]?.let { picture ->
            thumbSources[clip.id] = picture
            gradedThumbs.remove(clip.id)
            return
        }
        val waiting = waitingThumbs.getOrPut(shared) { mutableListOf() }
        waiting += clip.id
        if (waiting.size > 1) return
        viewModelScope.launch {
            val raw = previews.framePixels(asset.uri, asset.kind, at + 200, THUMB_WIDTH, exact = false)
            val clips = waitingThumbs.remove(shared).orEmpty()
            raw ?: return@launch
            val color = doc?.colorManagement ?: ColorManagement.DEFAULT
            val picture = if (asset.hdrByOverride) raw.toneMapped(asset.effectiveTransfer, color) else raw
            sharedThumbs[shared] = picture
            clips.forEach { id ->
                thumbSources[id] = picture
                gradedThumbs.remove(id)
            }
            publish()
        }
    }

    /** Graded thumbnail, recomputed only when the clip's grade changed. */
    private fun gradedThumb(clip: Clip): FramePixels? {
        val source = thumbSources[clip.id] ?: return null
        val cached = gradedThumbs[clip.id]
        val framing = doc?.framingOf(clip, source.width.toFloat() / source.height.coerceAtLeast(1))
        if (cached != null && cached.first == clip.grade to framing) return cached.second
        val graded = scopes.grade(source, clip.grade.windowAt(0), _state.value.pictureAspect, framing = framing)
        gradedThumbs[clip.id] = (clip.grade to framing) to graded
        return graded
    }

    /** Re-measures the graded frame at the playhead (debounced; refetches the frame when it moved). */
    private fun analyse(refetch: Boolean) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            delay(if (refetch) (if (playing) 180L else 90L) else 50L)
            val document = doc ?: return@launch
            val fps = document.format.fps
            val at = _state.value.playheadFrame
            val clip = videoClips(document).firstOrNull { at in it && document.timeline.track(it.trackId)?.enabled == true }
                ?: selectedClip()
            val asset = clip?.let { document.asset(it.assetId) }
            val library = luts.library.value
            if (clip == null || asset == null || asset.offline) {
                _state.update { it.copy(scope = null, looks = Look.entries.map { l -> LookPreviewUi(l, null) } + library.map { LookPreviewUi(Look.NONE, null, it) }) }
                return@launch
            }
            val offset = (at - clip.start).coerceIn(0, clip.durationFrames - 1)
            val sourceMs = clip.sourceInMs + if (clip.retimed) clip.sourceOffsetMs(offset, fps).toLong() else (offset * 1000L * clip.speed.factor / fps).toLong()
            val key = "${asset.uri}@${sourceMs / 40}"
            if (key != frameKey || frame == null) {
                frame = previews.framePixels(asset.uri, asset.kind, sourceMs, FRAME_WIDTH, exact = !playing)
                frameKey = key
            }
            val raw = frame ?: return@launch
            val mode = _state.value.scopeMode
            // A keyed window where it stands at the playhead; a keyed node 1 dissolving there.
            val grade = clip.grade.windowAt(offset)
            val still = grade.at(offset)
            val node = _state.value.node
            val aspect = document.format.width.toFloat() / document.format.height.coerceAtLeast(1)
            val framing = document.framingOf(clip, raw.width.toFloat() / raw.height.coerceAtLeast(1))
            val inFrame = framing?.inFrame
            val (scope, looks) = withContext(compute) {
                // The scopes read what the viewer shows: of a filled clip, or one larger than the frame, the middle only — HDR
                // by the input colour override tone mapped into the timeline first, as the monitor shows it.
                val source = if (asset.hdrByOverride) raw.toneMapped(asset.effectiveTransfer, document.colorManagement) else raw
                val shown = scopes.framed(source, framing)
                val scopeData = scopes(scopes.grade(shown, grade, aspect, offset.toFloat(), inFrame), mode)
                val small = downsample(shown, 2)
                // Each look, then each imported LUT, as the selected node would apply it.
                val strength = maxOf(still.node(node)?.lookStrength ?: 0, 60)
                scopeData to Look.entries.map { look ->
                    LookPreviewUi(look, scopes.grade(small, still.withNode(node) { it.copy(look = look, lut = null, lookStrength = strength) }, aspect, framing = inFrame))
                } + library.map { info ->
                    LookPreviewUi(Look.NONE, scopes.grade(small, still.withNode(node) { it.copy(look = Look.NONE, lut = info.ref, lookStrength = strength) }, aspect, framing = inFrame), info)
                }
            }
            _state.update { it.copy(scope = scope, looks = looks) }
        }
    }

    private fun downsample(f: FramePixels, factor: Int): FramePixels {
        val w = (f.width / factor).coerceAtLeast(1)
        val h = (f.height / factor).coerceAtLeast(1)
        val out = IntArray(w * h) { i -> f.pixels[((i / w) * factor).coerceAtMost(f.height - 1) * f.width + ((i % w) * factor).coerceAtMost(f.width - 1)] }
        return FramePixels(w, h, out)
    }

    private companion object {
        const val TAG = "PrismColor"
        const val FRAME_WIDTH = 192
        const val THUMB_WIDTH = 128
        const val THUMB_SHARE_MS = 1_000L

        /** Gallery tiles read their still at this width; a fresh grab keeps every [THUMB_STEP]th pixel. */
        const val STILL_THUMB_WIDTH = 240
        const val THUMB_STEP = 4

        /** Shot Match reads the still at this width: plenty for its percentiles. */
        const val MATCH_WIDTH = 320

        /** A 65³ `.cube` file is about 8 MB of text. */
        const val MAX_LUT_BYTES = 16 shl 20
        const val MAX_LUT_NAME = 40

        /** `.cube` has no registered type; a generic one keeps the extension as given. */
        const val CUBE_MIME = "application/octet-stream"
    }
}
