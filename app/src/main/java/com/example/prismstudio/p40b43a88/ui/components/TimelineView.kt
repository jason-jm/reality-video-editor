package com.example.prismstudio.p40b43a88.ui.components

import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.Marker
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.SpeedRamp
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase
import com.example.prismstudio.p40b43a88.domain.model.easedProgress
import com.example.prismstudio.p40b43a88.domain.usecase.MarqueeSelectUseCase
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.pico.spatial.ui.foundation.layout.offset as offsetZ

private sealed interface TimelineHit {
    data object Ruler : TimelineHit
    data class Body(val clip: Clip) : TimelineHit
    data class Edge(val clip: Clip, val edge: TrimEdge) : TimelineHit
    data class Fade(val clip: Clip, val fadeIn: Boolean) : TimelineHit
    /** The dot in the middle of [clip]'s fade (Resolve's fade curve dot): dragging it [unitPx] bends the fade all the way. */
    data class FadeCurve(val clip: Clip, val fadeIn: Boolean, val unitPx: Float) : TimelineHit
    /** A marker's flag on the ruler, or (with [end]) its duration bar's end. */
    data class MarkerGrab(val marker: Marker, val end: Boolean) : TimelineHit
    /** The selected sound's volume line: its one level ([keyFrames] empty) or those volume keyframes. */
    data class Volume(val clip: Clip, val keyFrames: List<Int>, val dbPerPx: Float) : TimelineHit
    /** An outer edge of [clip]'s opening transition box ([transitionGrabAt]). */
    data class TransitionEdge(val clip: Clip, val grab: TransitionGrab) : TimelineHit {
        fun framesAt(frame: Int): Int = grab.framesAt(clip, frame)
    }
    data object Empty : TimelineHit
}

/** A transition edge being dragged: the clip it opens, and where the pointer is (for the readout). */
private data class TransitionDrag(val clipId: String, val at: Offset)

/** A marker (or its duration's end) being dragged: which, and where the pointer is (for the readout). */
private data class MarkerDragUi(val markerId: String, val end: Boolean, val at: Offset)

/** How near a marker's flag, or its duration bar's end, a press on the ruler grabs it. */
private val MARKER_GRAB = 10.dp

/** A fade handle (or its curve dot) being dragged: the clip, which of its fades, and where the pointer is (for the readout). */
private data class FadeDrag(val clipId: String, val fadeIn: Boolean, val at: Offset, val curve: Boolean = false)

/**
 * How a clip's fades show: not at all, shaded over its ends, or shaded with handles to drag — the Audio page's inside the
 * clip's top, the Edit page's selected clip's on its top edge (Resolve's corner handles), clear of its name.
 */
private enum class FadeMarks { NONE, SHADE, HANDLES, EDGE_HANDLES }

/**
 * How near the selected clip's fade handle (the Edit page's) a press grabs it: across, and down from the top of its lane —
 * the rest of the clip's ends still trims.
 */
private val FADE_GRAB_X = 16.dp
private val FADE_GRAB_Y = 24.dp

/** A fade's curve dot shows (and is grabbed, this near) once the fade is this long on screen. */
private val FADE_DOT_MIN = 20.dp
private val FADE_DOT_GRAB = 12.dp

/** A curve this near straight snaps to straight. */
private const val CURVE_SNAP = 0.05f

/** How near a transition box's outer edge a press grabs it, and how far from the cut that edge must lie to be grabbed. */
private val TRANSITION_EDGE_HIT = 10.dp

/** A volume line being dragged: the clip, what of its line, and where the pointer is (for the readout). */
private data class VolumeDrag(val clipId: String, val keyFrames: List<Int>, val at: Offset)

/** How far inside a sound clip's box its volume line runs, top and bottom. */
private val VOLUME_INSET = 4.dp

/** How far a press on a volume line moves before it drags the line (the platform's touch slop is far larger). */
private val VOLUME_DRAG_SLOP = 12.dp

/** Room round the lifted, selected clip's layer for what it draws past its edges (its fade handles). */
private val LIFT_MARGIN = 10.dp

/** `timeline` part laneScrollbar: the thumb's width and least length. */
private val LANE_THUMB_WIDTH = 4.dp
private val LANE_THUMB_MIN = 24.dp

/** How often a marquee resting by either end of the view scrolls it on. */
private const val MARQUEE_TICK_MS = 32L

/** A marquee being dragged: its corners in content px (x from the timeline's start, y from the first lane's top). */
private data class MarqueeBox(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    /** The frames it covers part of, and the tracks of the lanes from its top to its bottom. */
    fun span(pxPerFrame: Float, trackPx: Float, tracks: List<Track>): Triple<Int, Int, Set<String>> {
        val from = floor(min(x0, x1) / pxPerFrame).toInt().coerceAtLeast(0)
        val until = ceil(max(x0, x1) / pxPerFrame).toInt()
        val top = floor(min(y0, y1) / trackPx).toInt().coerceAtLeast(0)
        val bottom = floor(max(y0, y1) / trackPx).toInt().coerceAtMost(tracks.lastIndex)
        return Triple(from, until, if (top > bottom) emptySet() else tracks.subList(top, bottom + 1).map { it.id }.toSet())
    }
}

private class TimelinePalette(
    val ruler: Color,
    val divider: Color,
    val lane: Color,
    val transition: Color,
    val transitionMark: Color,
    val onClip: Color,
    val error: Color,
    /** The edge of a ducking dip. */
    val duck: Color,
    /** The marquee's fill and outline. */
    val marquee: Color,
)

/**
 * The `timeline` renderer: ruler, track lanes, clips (with poster frames, waveforms, transitions,
 * fades, badges), markers and the playhead. Tap a clip to select it (it lifts toward the viewer); with
 * Ctrl / ⌘ / Shift held, or in 多选 mode ([TimelineUi.multiSelect]), a tap adds it to the selection or
 * takes it out. Drag a body to move (one of several selected clips moves them all), drag either edge
 * to trim, drag fade handles, tap or drag the ruler to seek and drag empty lanes to scroll — or, with
 * [allowMarquee] in 多选 mode (or with a modifier held), draw a marquee: the clips it touches light up
 * and join the selection when it is let go, and near either end the view scrolls on under it. With
 * [clipMenu], holding a press on a clip (or an empty lane) opens that menu there — Resolve's
 * right-click menu for ray and hand input; on a selected clip it keeps the whole selection. Every sound clip shows
 * its volume line (Resolve's audio overlay); with [volumeEditing], dragging the selected one's line up or down sets
 * its level, or moves the volume keyframes it holds, while a sideways drag from it still moves the clip.
 */
@Composable
fun TimelineView(
    ui: TimelineUi,
    playheadFrame: Int,
    actions: TimelineActions,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 72.dp,
    rulerHeight: Dp = 32.dp,
    showWaveforms: Boolean = true,
    showFadeHandles: Boolean = false,
    /**
     * The selected clip's fade handles over the top of its ends (the Edit page): a picture's, a title's or a sound's,
     * each its own; every clip's fades are shaded.
     */
    selectedFadeHandles: Boolean = false,
    allowEditing: Boolean = true,
    followPlayhead: Boolean = false,
    description: String = "时间线",
    /** The menu for a held press: on a clip (its id, selected first) or an empty lane (null). */
    clipMenu: (@Composable (clipId: String?, onDismiss: () -> Unit) -> Unit)? = null,
    /** Resolve's rubber-band selection on empty lanes while gathering clips (see [TimelineActions.marquee]). */
    allowMarquee: Boolean = false,
    /** Whether the selected sound clip's volume line can be dragged ([TimelineActions.volume]). */
    volumeEditing: Boolean = allowEditing,
    /** Markers drag along the ruler and a tap on one picks it ([TimelineActions.marker], [TimelineActions.pickMarker]). */
    markersEditable: Boolean = false,
    /**
     * `timeline` part laneScroll: the lanes' vertical scroll, shared with the [TrackHeaders] beside them (their column scrolls
     * with it); null keeps every lane where it is.
     */
    laneScroll: ScrollState? = null,
    mediaDrop: TimelineMediaDrop? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val captionStyle = PrismType.timecodeSmall
    val clipLabelStyle = PrismType.caption.copy(color = PicoTheme.colorScheme.labelPrimaryLight)
    val palette = TimelinePalette(
        ruler = PicoTheme.colorScheme.labelTertiary,
        divider = PicoTheme.colorScheme.dividerLine,
        lane = PicoTheme.colorScheme.fillTertiary,
        transition = PicoTheme.colorScheme.fillSecondary,
        transitionMark = PicoTheme.colorScheme.labelPrimary,
        onClip = PicoTheme.colorScheme.labelPrimaryLight,
        error = PicoTheme.colorScheme.error,
        duck = PicoTheme.colorScheme.interaction,
        marquee = PicoTheme.colorScheme.interaction,
    )
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var viewHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollPx by remember { mutableFloatStateOf(0f) }
    // Where a held press opened the clip menu, and on which clip.
    var menuAt by remember { mutableStateOf<Pair<String?, Offset>?>(null) }
    var marquee by remember { mutableStateOf<MarqueeBox?>(null) }
    var volumeDrag by remember { mutableStateOf<VolumeDrag?>(null) }
    var transitionDrag by remember { mutableStateOf<TransitionDrag?>(null) }
    var fadeDrag by remember { mutableStateOf<FadeDrag?>(null) }
    var markerDrag by remember { mutableStateOf<MarkerDragUi?>(null) }
    val menuEnabled = clipMenu != null && allowEditing
    val fps = ui.fps.coerceAtLeast(1)
    val pxPerFrame = with(density) { TimelineScale.dpPerSecond(ui.zoom).dp.toPx() } / fps
    val rulerPx = with(density) { rulerHeight.toPx() }
    val trackPx = with(density) { trackHeight.toPx() }
    val contentPx = (ui.durationFrames + fps * 8) * pxPerFrame
    val maxScroll = max(0f, contentPx - viewportPx)
    val scroll = scrollPx.coerceIn(0f, maxScroll)
    val thumbs = remember(ui.thumbnails) { ui.thumbnails.mapValues { it.value.asImageBitmap() } }
    val lanes = LaneGeometry.of(rulerPx, trackPx, (laneScroll?.value ?: 0).toFloat(), viewHeightPx, ui.tracks.size)

    // Keep the playhead in place on screen when zooming.
    var lastPxPerFrame by remember { mutableFloatStateOf(pxPerFrame) }
    LaunchedEffect(pxPerFrame) {
        if (lastPxPerFrame != pxPerFrame) {
            val screenX = playheadFrame * lastPxPerFrame - scrollPx
            scrollPx = (playheadFrame * pxPerFrame - screenX).coerceAtLeast(0f)
            lastPxPerFrame = pxPerFrame
        }
    }
    SideEffect {
        if (followPlayhead && viewportPx > 0f) {
            val x = playheadFrame * pxPerFrame - scrollPx.coerceIn(0f, maxScroll)
            if (x > viewportPx - 48f || x < 0f) scrollPx = (playheadFrame * pxPerFrame - viewportPx * 0.2f).coerceIn(0f, maxScroll)
        }
    }

    val latestUi by rememberUpdatedState(ui)
    val latestActions by rememberUpdatedState(actions)
    val geometry by rememberUpdatedState(Triple(pxPerFrame, scroll, trackPx))
    val latestMaxScroll by rememberUpdatedState(maxScroll)

    fun frameAt(x: Float): Int {
        val (ppf, sc, _) = geometry
        return ((x + sc) / ppf).roundToInt().coerceAtLeast(0)
    }

    /** The lanes as they sit now (scrolled since the gesture began, perhaps). */
    fun lanesNow(): LaneGeometry = LaneGeometry.of(rulerPx, geometry.third, (laneScroll?.value ?: 0).toFloat(), viewHeightPx, latestUi.tracks.size)

    fun laneAt(y: Float): Int = lanesNow().laneAt(y)

    var canvasCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (mediaDrop != null) {
        DisposableEffect(mediaDrop, rulerPx) {
            mediaDrop.locate = locate@{ windowPosition, kind ->
                val canvas = canvasCoordinates?.takeIf { it.isAttached } ?: return@locate null
                val p = canvas.windowToLocal(windowPosition)
                if (p.x < 0 || p.x >= canvas.size.width || p.y < rulerPx || p.y >= canvas.size.height) return@locate null
                val track = latestUi.tracks.getOrNull(laneAt(p.y)) ?: return@locate null
                if (track.locked || track.kind != kind) return@locate null
                TimelineMediaDrop.Destination(track.id, frameAt(p.x))
            }
            onDispose { mediaDrop.locate = null; mediaDrop.destination = null }
        }
    }

    /** The lanes scrolled by [delta] px (down positive), kept within what they need. */
    fun scrollLanes(delta: Float) {
        val state = laneScroll ?: return
        val now = lanesNow()
        val target = (now.offset + delta).coerceIn(0f, now.maxOffset)
        if (target != now.offset) state.dispatchRawDelta(target - state.value)
    }

    // A clip picked off screen (by keys, the inspector, a search) scrolls its lane into view.
    LaunchedEffect(ui.selectedClipId, laneScroll) {
        val state = laneScroll ?: return@LaunchedEffect
        val lane = latestUi.tracks.indexOfFirst { t -> t.id == latestUi.clips.firstOrNull { it.id == latestUi.selectedClipId }?.trackId }
        lanesNow().reveal(lane)?.let { state.animateScrollTo(it.roundToInt()) }
    }

    /** How [clip]'s fades show: with handles on the Audio page's sounds and the Edit page's selected clip, else shaded. */
    fun fadeMarks(clip: Clip, lifted: Boolean, locked: Boolean): FadeMarks = when {
        showFadeHandles && clip.kind == ClipKind.AUDIO -> FadeMarks.HANDLES
        !selectedFadeHandles -> FadeMarks.NONE
        lifted && allowEditing && !locked && ClipFades.takes(clip) -> FadeMarks.EDGE_HANDLES
        ClipFades.fades(clip) -> FadeMarks.SHADE
        else -> FadeMarks.NONE
    }

    /** [clip]'s fade curve dot under [position], drawn in [rect], if one shows there. */
    fun fadeCurveAt(clip: Clip, rect: Rect, position: Offset, ppf: Float): TimelineHit.FadeCurve? {
        val minWidth = with(density) { FADE_DOT_MIN.toPx() }
        val grab = with(density) { FADE_DOT_GRAB.toPx() }
        for (fadeIn in listOf(true, false)) {
            val dot = fadeCurveDot(clip, rect, fadeIn, ppf, fps, minWidth) ?: continue
            if ((position - dot).getDistance() < grab) return TimelineHit.FadeCurve(clip, fadeIn, rect.height / 2f)
        }
        return null
    }

    /** `timeline` part markerDrag: the duration bar's end (first: a short bar ends by its flag) or the flag under [position]. */
    fun markerAt(position: Offset): TimelineHit.MarkerGrab? {
        if (!markersEditable || !allowEditing) return null
        val (ppf, sc, _) = geometry
        val grab = with(density) { MARKER_GRAB.toPx() }
        val barTop = rulerPx - with(density) { 12.dp.toPx() }
        val flagTop = rulerPx - with(density) { 22.dp.toPx() }
        latestUi.markers.filter { it.hasDuration }.firstOrNull { abs(position.x - (it.endFrame * ppf - sc)) < grab && position.y > barTop }
            ?.let { return TimelineHit.MarkerGrab(it, end = true) }
        return latestUi.markers.firstOrNull { abs(position.x - (it.frame * ppf - sc)) < grab && position.y > flagTop }
            ?.let { TimelineHit.MarkerGrab(it, end = false) }
    }

    /** An empty lane at [position]: its gap is picked (the actions fall back to clearing the selection). */
    fun gapPick(position: Offset) {
        val track = if (position.y >= rulerPx) latestUi.tracks.getOrNull(laneAt(position.y)) else null
        if (track == null) latestActions.select(null) else latestActions.selectGap(track.id, frameAt(position.x))
    }

    fun hitTest(position: Offset): TimelineHit {
        if (position.y < rulerPx) return markerAt(position) ?: TimelineHit.Ruler
        val lane = laneAt(position.y)
        val track = latestUi.tracks.getOrNull(lane) ?: return TimelineHit.Empty
        val (ppf, sc, tp) = geometry
        val laneTop = lanesNow().top(lane)
        val near = with(density) { 10.dp.toPx() }
        val edgeMax = with(density) { 28.dp.toPx() }
        val handleRadius = with(density) { 28.dp.toPx() }
        val clips = latestUi.clips.filter { it.trackId == track.id }
        // The selected clip's fade handles come first, over the top of its ends.
        if (selectedFadeHandles && allowEditing && !track.locked) {
            clips.firstOrNull { it.id == latestUi.selectedClipId && ClipFades.takes(it) }?.let { clip ->
                fadeCurveAt(clip, clipBounds(clip, laneTop, ppf, sc, tp), position, ppf)?.let { return it }
                val x0 = clip.start * ppf - sc
                val x1 = clip.end * ppf - sc
                val grabX = with(density) { FADE_GRAB_X.toPx() }
                if (position.y < laneTop + with(density) { FADE_GRAB_Y.toPx() } && position.x > x0 - grabX && position.x < x1 + grabX) {
                    val mid = (x0 + x1) / 2
                    if (abs(position.x - (x0 + clip.fadeInSeconds * fps * ppf)) < grabX && position.x <= mid) return TimelineHit.Fade(clip, true)
                    if (abs(position.x - (x1 - clip.fadeOutSeconds * fps * ppf)) < grabX && position.x > mid) return TimelineHit.Fade(clip, false)
                }
            }
        }
        // A transition box's outer edges come first: each lies away from the cut, inside a clip's trim zone when short.
        if (allowEditing && !track.locked) {
            val grab = with(density) { TRANSITION_EDGE_HIT.toPx() }
            val minWidth = with(density) { 14.dp.toPx() }
            for (clip in clips) {
                val transition = clip.transitionIn ?: continue
                val span = latestUi.transitionSpans[clip.id] ?: TransitionSpanUi(0, transition.frames)
                transitionGrabAt(clip, span, ppf, sc, position.x, grab, minWidth)?.let { return TimelineHit.TransitionEdge(clip, it) }
            }
        }
        for (clip in clips) {
            val x0 = clip.start * ppf - sc
            val x1 = clip.end * ppf - sc
            if (showFadeHandles && clip.kind == ClipKind.AUDIO) {
                fadeCurveAt(clip, clipBounds(clip, laneTop, ppf, sc, tp), position, ppf)?.let { return it }
                val hy = laneTop + with(density) { 14.dp.toPx() }
                val fin = x0 + clip.fadeInSeconds * fps * ppf
                val fout = x1 - clip.fadeOutSeconds * fps * ppf
                if (abs(position.y - hy) < handleRadius) {
                    if (abs(position.x - fin) < handleRadius && position.x < (x0 + x1) / 2 + near) return TimelineHit.Fade(clip, true)
                    if (abs(position.x - fout) < handleRadius && position.x > (x0 + x1) / 2 - near) return TimelineHit.Fade(clip, false)
                }
            }
            if (position.x < x0 - near || position.x > x1 + near) continue
            val zone = minOf(edgeMax, (x1 - x0) / 3f)
            // The selected sound's volume line is taken before its body and edges (its markers anywhere on it).
            if (volumeEditing && clip.kind == ClipKind.AUDIO && clip.id == latestUi.selectedClipId && !track.locked) {
                val line = VolumeLineGeometry(clipBounds(clip, laneTop, ppf, sc, tp), ppf, with(density) { VOLUME_INSET.toPx() })
                val slop = with(density) { 6.dp.toPx() }
                val markerSlop = with(density) { 10.dp.toPx() }
                volumeGrab(clip, line, position.x, position.y, slop, markerSlop, x0 + zone, x1 - zone)
                    ?.let { return TimelineHit.Volume(clip, it, line.dbPerPx) }
            }
            return when {
                position.x <= x0 + zone -> TimelineHit.Edge(clip, TrimEdge.START)
                position.x >= x1 - zone -> TimelineHit.Edge(clip, TrimEdge.END)
                else -> TimelineHit.Body(clip)
            }
        }
        return TimelineHit.Empty
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(if (mediaDrop != null) Modifier.onGloballyPositioned { canvasCoordinates = it } else Modifier)
            .onSizeChanged {
                viewportPx = it.width.toFloat()
                viewHeightPx = it.height.toFloat()
                latestActions.viewport(with(density) { it.width.toDp().value })
            }
            .semantics { contentDescription = description.localized() }
            .pointerInput(showFadeHandles, selectedFadeHandles, allowEditing, menuEnabled, allowMarquee, volumeEditing) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    marquee = null
                    volumeDrag = null
                    transitionDrag = null
                    fadeDrag = null
                    markerDrag = null
                    val keys = currentEvent.keyboardModifiers
                    val additive = latestUi.multiSelect || keys.isCtrlPressed || keys.isMetaPressed || keys.isShiftPressed
                    val first = hitTest(down.position)
                    // What the drag does: a sideways drag from a volume line moves the clip instead (see beginDrag).
                    var hit = first
                    // While gathering clips a drag from an empty lane draws a marquee instead of scrolling.
                    val marqueeing = allowMarquee && additive && first == TimelineHit.Empty
                    val anchorX = down.position.x + geometry.second
                    val anchorY = lanesNow().toContent(down.position.y)
                    // A drag from an empty lane pans the way it set off: along the time, or through the lanes.
                    var panLanes: Boolean? = null
                    if (first == TimelineHit.Ruler) latestActions.seek(frameAt(down.position.x))
                    var dragging = false
                    var released = false
                    var last = down.position
                    // PICO's touch slop is 32 dp, most of a volume line's height: a press on the line starts its drag
                    // sooner, and the level follows the pointer from where the drag began, never jumping by the slop.
                    val slop = if (first is TimelineHit.Volume || first is TimelineHit.FadeCurve) min(viewConfiguration.touchSlop, VOLUME_DRAG_SLOP.toPx())
                    else viewConfiguration.touchSlop
                    var dragFrom = down.position
                    fun beginDrag(p: Offset) {
                        dragging = true
                        dragFrom = p
                        val h = hit
                        // The line only goes up and down: a sideways drag from it moves the clip.
                        if (h is TimelineHit.Volume && allowEditing && abs(p.x - down.position.x) > abs(p.y - down.position.y)) {
                            hit = TimelineHit.Body(h.clip)
                        }
                    }
                    fun dragTo(p: Offset) {
                        when (val hit = hit) {
                            TimelineHit.Ruler -> latestActions.seek(frameAt(p.x))
                            TimelineHit.Empty -> if (marqueeing) {
                                // Near either end the view scrolls on, so the box reaches clips not yet shown.
                                val edge = 32.dp.toPx()
                                val push = when {
                                    p.x > viewportPx - edge -> p.x - (viewportPx - edge)
                                    p.x < edge -> p.x - edge
                                    else -> 0f
                                } * 0.25f
                                if (push != 0f) scrollPx = (scrollPx.coerceIn(0f, latestMaxScroll) + push).coerceIn(0f, latestMaxScroll)
                                // By the top or the bottom the lanes scroll on the same way.
                                scrollLanes(lanesNow().edgePush(p.y, edge))
                                marquee = MarqueeBox(anchorX, anchorY, p.x + scrollPx.coerceIn(0f, latestMaxScroll), lanesNow().toContent(p.y))
                            } else {
                                val lanesPan = panLanes ?: (lanesNow().scrollable && abs(p.y - down.position.y) > abs(p.x - down.position.x)).also { panLanes = it }
                                if (lanesPan) scrollLanes(-(p.y - last.y))
                                else scrollPx = (scrollPx.coerceIn(0f, latestMaxScroll) - (p.x - last.x)).coerceIn(0f, latestMaxScroll)
                            }
                            is TimelineHit.Body -> if (allowEditing) {
                                // Carried by the top or the bottom of the lanes, the clip scrolls them on to the lanes beyond.
                                scrollLanes(lanesNow().edgePush(p.y, 32.dp.toPx()))
                                val dx = ((p.x - down.position.x) / geometry.first).roundToInt()
                                val laneTrack = latestUi.tracks.getOrNull(laneAt(p.y).coerceIn(0, latestUi.tracks.lastIndex))
                                val sameKind = laneTrack?.accepts(hit.clip.kind) == true
                                latestActions.move(hit.clip.id, hit.clip.start + dx, if (sameKind) laneTrack!!.id else hit.clip.trackId, false)
                            }
                            is TimelineHit.Edge -> if (allowEditing) latestActions.trim(hit.clip.id, hit.edge, frameAt(p.x), false)
                            is TimelineHit.Fade -> {
                                latestActions.fade(hit.clip.id, hit.fadeIn, fadeSeconds(hit, frameAt(p.x), fps), false)
                                fadeDrag = FadeDrag(hit.clip.id, hit.fadeIn, p)
                            }
                            is TimelineHit.FadeCurve -> {
                                latestActions.fadeCurve(hit.clip.id, hit.fadeIn, curveAt(hit, p.y - dragFrom.y), false)
                                fadeDrag = FadeDrag(hit.clip.id, hit.fadeIn, p, curve = true)
                            }
                            is TimelineHit.MarkerGrab -> {
                                // The marker keeps its distance from the pointer: it moves by the frames the pointer does.
                                val delta = frameAt(p.x) - frameAt(down.position.x)
                                latestActions.marker(hit.marker.id, (if (hit.end) hit.marker.endFrame else hit.marker.frame) + delta, hit.end, false)
                                markerDrag = MarkerDragUi(hit.marker.id, hit.end, p)
                            }
                            is TimelineHit.Volume -> {
                                latestActions.volume(hit.clip.id, hit.keyFrames, -(p.y - dragFrom.y) * hit.dbPerPx, false)
                                volumeDrag = VolumeDrag(hit.clip.id, hit.keyFrames, p)
                            }
                            is TimelineHit.TransitionEdge -> {
                                latestActions.transition(hit.clip.id, hit.framesAt(frameAt(p.x)), false)
                                transitionDrag = TransitionDrag(hit.clip.id, p)
                            }
                        }
                        last = p
                    }
                    // A press held still on a clip or an empty lane opens the clip menu.
                    if (menuEnabled && (first is TimelineHit.Body || first is TimelineHit.Edge || first is TimelineHit.Volume || first is TimelineHit.TransitionEdge ||
                            first == TimelineHit.Empty)
                    ) {
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (!released && !dragging) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    released = true
                                } else if ((change.position - down.position).getDistance() > slop) {
                                    beginDrag(change.position)
                                    change.consume()
                                    dragTo(change.position)
                                }
                            }
                        } == null
                        if (held) {
                            // Held on an empty lane: the gap there is picked first, for the menu's 删除空隙.
                            if (first == TimelineHit.Empty) gapPick(down.position)
                            val clipId = (first as? TimelineHit.Body)?.clip?.id ?: (first as? TimelineHit.Edge)?.clip?.id
                                ?: (first as? TimelineHit.Volume)?.clip?.id ?: (first as? TimelineHit.TransitionEdge)?.clip?.id
                            // On a clip of the selection the menu acts on all of it.
                            if (clipId != null && clipId !in latestUi.selectedClipIds) latestActions.select(clipId)
                            menuAt = clipId to down.position
                            // The rest of this press belongs to the menu.
                            while (awaitPointerEvent().changes.any { it.id == down.id && it.pressed }) Unit
                            return@awaitEachGesture
                        }
                    }
                    while (!released) {
                        // While a marquee rests by either end the view keeps scrolling under it, a step a tick; so do the lanes
                        // under a clip carried to their top or bottom.
                        val ticking = dragging && (marqueeing || (hit is TimelineHit.Body && allowEditing && lanesNow().edgePush(last.y, 32.dp.toPx()) != 0f))
                        val event = if (ticking) withTimeoutOrNull(MARQUEE_TICK_MS) { awaitPointerEvent() } else awaitPointerEvent()
                        if (event == null) {
                            dragTo(last)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging && (change.position - down.position).getDistance() > slop) beginDrag(change.position)
                        if (dragging) {
                            change.consume()
                            dragTo(change.position)
                        }
                    }
                    if (!dragging) {
                        val tapped = (first as? TimelineHit.Body)?.clip ?: (first as? TimelineHit.Edge)?.clip ?: (first as? TimelineHit.Fade)?.clip
                            ?: (first as? TimelineHit.FadeCurve)?.clip ?: (first as? TimelineHit.Volume)?.clip ?: (first as? TimelineHit.TransitionEdge)?.clip
                        when {
                            first is TimelineHit.MarkerGrab -> latestActions.pickMarker(first.marker.id)
                            tapped != null && additive -> latestActions.toggle(tapped.id)
                            tapped != null -> latestActions.select(tapped.id)
                            // An empty spot clears the selection, unless clips are being gathered.
                            first == TimelineHit.Empty && !additive -> gapPick(down.position)
                        }
                    } else {
                        when (val hit = hit) {
                            is TimelineHit.Body -> if (allowEditing) {
                                val dx = ((last.x - down.position.x) / geometry.first).roundToInt()
                                val laneTrack = latestUi.tracks.getOrNull(laneAt(last.y).coerceIn(0, latestUi.tracks.lastIndex))
                                val sameKind = laneTrack?.accepts(hit.clip.kind) == true
                                latestActions.move(hit.clip.id, hit.clip.start + dx, if (sameKind) laneTrack!!.id else hit.clip.trackId, true)
                            }
                            is TimelineHit.Edge -> if (allowEditing) latestActions.trim(hit.clip.id, hit.edge, frameAt(last.x), true)
                            is TimelineHit.Fade -> latestActions.fade(hit.clip.id, hit.fadeIn, fadeSeconds(hit, frameAt(last.x), fps), true)
                            is TimelineHit.FadeCurve -> latestActions.fadeCurve(hit.clip.id, hit.fadeIn, curveAt(hit, last.y - dragFrom.y), true)
                            is TimelineHit.MarkerGrab -> {
                                val delta = frameAt(last.x) - frameAt(down.position.x)
                                latestActions.marker(hit.marker.id, (if (hit.end) hit.marker.endFrame else hit.marker.frame) + delta, hit.end, true)
                            }
                            is TimelineHit.Volume -> latestActions.volume(hit.clip.id, hit.keyFrames, -(last.y - dragFrom.y) * hit.dbPerPx, true)
                            is TimelineHit.TransitionEdge -> latestActions.transition(hit.clip.id, hit.framesAt(frameAt(last.x)), true)
                            TimelineHit.Empty -> marquee?.let { box ->
                                val (from, until, trackIds) = box.span(geometry.first, geometry.third, latestUi.tracks)
                                if (trackIds.isNotEmpty() && until > from) latestActions.marquee(from, until, trackIds)
                            }
                            else -> Unit
                        }
                    }
                    marquee = null
                    volumeDrag = null
                    transitionDrag = null
                    fadeDrag = null
                    markerDrag = null
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta
                            val amount = (if (abs(delta.x) > abs(delta.y)) delta.x else delta.y) * 48f
                            // The wheel runs along the time; with Shift (or over the track headers) through the lanes.
                            if (event.keyboardModifiers.isShiftPressed && abs(delta.y) >= abs(delta.x)) scrollLanes(delta.y * 48f)
                            else scrollPx = (scrollPx.coerceIn(0f, latestMaxScroll) + amount).coerceIn(0f, latestMaxScroll)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        // The clips a marquee being dragged touches are drawn as selected already.
        val box = marquee
        val marqueeIds = box?.let {
            val (from, until, trackIds) = it.span(pxPerFrame, trackPx, ui.tracks)
            MarqueeSelectUseCase.touched(ui.clips, ui.tracks, from, until, trackIds).mapTo(mutableSetOf()) { clip -> clip.id }
        } ?: emptySet()
        Canvas(Modifier.fillMaxSize()) {
            drawRuler(ui, pxPerFrame, scroll, rulerPx, measurer, captionStyle, palette)
            // Linked sound / picture of the selection is highlighted too (it moves and trims with it).
            val selectedLinks = ui.clips.filter { it.id == ui.selectedClipId || it.id in ui.selectedClipIds || it.id in marqueeIds }
                .mapNotNull { it.linkId }.toSet()
            // The lanes scroll under the ruler: drawn below its foot only.
            clipRect(top = rulerPx) { ui.tracks.forEachIndexed { index, track ->
                if (!lanes.shows(index)) return@forEachIndexed
                val top = lanes.top(index)
                drawRect(palette.lane, topLeft = Offset(0f, top + 2f), size = Size(size.width, trackPx - 4f), alpha = 0.35f)
                drawLine(palette.divider, Offset(0f, top + trackPx), Offset(size.width, top + trackPx), strokeWidth = 1f)
                // `timeline` part gap: the picked gap, tinted and outlined in the accent.
                ui.selectedGap?.takeIf { it.trackId == track.id }?.let { gap ->
                    val left = gap.start * pxPerFrame - scroll
                    val corner = CornerRadius(6.dp.toPx())
                    val gapSize = Size((gap.end - gap.start) * pxPerFrame, trackPx - 8f)
                    drawRoundRect(palette.marquee, topLeft = Offset(left, top + 4f), size = gapSize, cornerRadius = corner, alpha = 0.16f)
                    drawRoundRect(palette.marquee, topLeft = Offset(left, top + 4f), size = gapSize, cornerRadius = corner, style = Stroke(2.dp.toPx()))
                }
                ui.clips.filter { it.trackId == track.id && it.id != ui.selectedClipId }.forEach { clip ->
                    val rect = clipBounds(clip, top, pxPerFrame, scroll, trackPx)
                    if (rect.right < 0f || rect.left > size.width) return@forEach
                    // The rest of a multiple selection is marked in place; only the inspector's clip lifts.
                    drawClip(
                        clip, rect, selected = clip.id in ui.selectedClipIds || clip.id in marqueeIds, enabled = track.enabled && clip.enabled, locked = track.locked,
                        linked = clip.linkId != null && clip.linkId in selectedLinks,
                        thumb = thumbs[clip.id], waveform = clip.assetId?.let(ui.waveforms::get),
                        showWaveform = showWaveforms, fades = fadeMarks(clip, lifted = false, locked = track.locked), offline = clip.assetId in ui.offlineAssetIds,
                        reverseLabel = ui.reverseLabel(clip), duck = ui.ducks[clip.trackId], flags = ui.flagsOf(clip),
                        pxPerFrame = pxPerFrame, fps = fps, measurer = measurer, labelStyle = clipLabelStyle, palette = palette,
                        frameAspect = ui.frameAspect, framing = ui.framings[clip.id], transitionSpan = ui.transitionSpans[clip.id],
                        inner = ui.compoundBars[clip.id], compoundLabel = ui.compoundLabels[clip.id],
                    )
                }
                // The lifted, selected clip's transition where it leads in over the clip before it.
                ui.clips.firstOrNull { it.id == ui.selectedClipId && it.trackId == track.id }?.let { lifted ->
                    val span = ui.transitionSpans[lifted.id]?.takeIf { it.lead > 0 } ?: return@let
                    val rect = clipBounds(lifted, top, pxPerFrame, scroll, trackPx)
                    clipRect(right = rect.left) { drawTransitionBox(lifted, rect, span, pxPerFrame, palette) }
                }
            } }
            ui.markers.forEach { marker ->
                val x = marker.frame * pxPerFrame - scroll
                val xEnd = marker.endFrame * pxPerFrame - scroll
                if (xEnd < -12f || x > size.width + 12f) return@forEach
                // `timeline` part markerRange: Resolve's duration marker — a bar along the ruler's foot to where it ends, and a
                // faint line down the lanes there.
                if (marker.hasDuration) {
                    val color = PrismColors.marker(marker.color)
                    val bar = 5.dp.toPx()
                    drawRoundRect(
                        color,
                        topLeft = Offset(x, rulerPx - bar - 1.dp.toPx()),
                        size = Size(xEnd - x, bar),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        alpha = if (marker.id == ui.selectedMarkerId) 0.95f else 0.7f,
                    )
                    drawLine(color, Offset(xEnd, rulerPx), Offset(xEnd, size.height), strokeWidth = 1f, alpha = 0.3f)
                    // `timeline` part markerEndHandle: the grip that drags the duration's end (the Edit page's ruler).
                    if (markersEditable) {
                        val grip = Size(4.dp.toPx(), 12.dp.toPx())
                        drawRoundRect(color, topLeft = Offset(xEnd - grip.width / 2, rulerPx - grip.height), size = grip, cornerRadius = CornerRadius(2.dp.toPx()))
                        drawRoundRect(palette.onClip, topLeft = Offset(xEnd - grip.width / 2, rulerPx - grip.height), size = grip,
                            cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()), alpha = 0.8f)
                    }
                }
                if (x < -12f) return@forEach
                // The marker picked in the index stands taller, outlined.
                val picked = marker.id == ui.selectedMarkerId
                val half = (if (picked) 8 else 6).dp.toPx()
                val top = rulerPx - (if (picked) 18 else 14).dp.toPx()
                val flag = Path().apply {
                    moveTo(x - half, top)
                    lineTo(x + half, top)
                    lineTo(x + half, rulerPx - 6.dp.toPx())
                    lineTo(x, rulerPx - 1.dp.toPx())
                    lineTo(x - half, rulerPx - 6.dp.toPx())
                    close()
                }
                val color = PrismColors.marker(marker.color)
                drawPath(flag, color)
                if (picked) drawPath(flag, palette.onClip, style = Stroke(width = 2.dp.toPx()))
                drawLine(color, Offset(x, rulerPx), Offset(x, size.height), strokeWidth = if (picked) 2.dp.toPx() else 1f, alpha = 0.45f)
            }
            drawMarks(ui.marks, pxPerFrame, scroll, rulerPx)
            mediaDrop?.destination?.let { drop ->
                val lane = ui.tracks.indexOfFirst { it.id == drop.trackId }
                if (lane >= 0) {
                    val x = drop.frame * pxPerFrame - scroll
                    val top = lanes.top(lane)
                    drawLine(palette.marquee, Offset(x, maxOf(rulerPx, top)), Offset(x, top + trackPx), strokeWidth = 4.dp.toPx())
                }
            }
            box?.let {
                val left = min(it.x0, it.x1) - scroll
                val top = max(lanes.toScreen(min(it.y0, it.y1)), rulerPx)
                val area = Size(max(it.x0, it.x1) - scroll - left, lanes.toScreen(max(it.y0, it.y1)) - top)
                if (area.height > 0f) {
                    drawRect(palette.marquee, Offset(left, top), area, alpha = 0.14f)
                    drawRect(palette.marquee, Offset(left, top), area, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            // `timeline` part laneScrollbar: where the lanes in view sit among them all, while they do not all fit.
            lanes.thumb(LANE_THUMB_MIN.toPx())?.let { (thumbTop, thumbHeight) ->
                val width = LANE_THUMB_WIDTH.toPx()
                drawRoundRect(
                    palette.ruler, topLeft = Offset(size.width - width - 2.dp.toPx(), thumbTop + 2.dp.toPx()),
                    size = Size(width, thumbHeight - 4.dp.toPx()), cornerRadius = CornerRadius(width / 2), alpha = 0.6f,
                )
            }
        }
        val selected = ui.clips.firstOrNull { it.id == ui.selectedClipId }
        val selectedLane = ui.tracks.indexOfFirst { it.id == selected?.trackId }
        if (selected != null && selectedLane >= 0 && lanes.shows(selectedLane)) {
            val track = ui.tracks[selectedLane]
            val rect = clipBounds(selected, lanes.top(selectedLane), pxPerFrame, scroll, trackPx)
            // The lifted layer clips to its box: a margin round the clip keeps its fade handles whole on its edges; a lane
            // scrolled partly under the ruler is cut at its foot.
            val margin = with(density) { LIFT_MARGIN.toPx() }
            val boxTop = max(rect.top - margin, rulerPx)
            val boxBottom = if (viewHeightPx > 0f) min(rect.bottom + margin, viewHeightPx) else rect.bottom + margin
            if (rect.right > 0f && rect.left < viewportPx && boxBottom > boxTop) {
                val visibleLeft = max(rect.left, -8f)
                val visibleRight = minOf(rect.right, viewportPx + 8f)
                Box(
                    Modifier
                        .offset { IntOffset((visibleLeft - margin).roundToInt(), boxTop.roundToInt()) }
                        .size(with(density) { (visibleRight - visibleLeft + 2 * margin).toDp() }, with(density) { (boxBottom - boxTop).toDp() })
                        .offsetZ(z = 12.dp),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val local = Rect(rect.left - visibleLeft + margin, rect.top - boxTop, rect.right - visibleLeft + margin, rect.top - boxTop + rect.height)
                        drawClip(
                            selected, local, selected = true, enabled = track.enabled && selected.enabled, locked = track.locked,
                            thumb = thumbs[selected.id], waveform = selected.assetId?.let(ui.waveforms::get),
                            showWaveform = showWaveforms, fades = fadeMarks(selected, lifted = true, locked = track.locked), offline = selected.assetId in ui.offlineAssetIds,
                            reverseLabel = ui.reverseLabel(selected), duck = ui.ducks[selected.trackId], flags = ui.flagsOf(selected),
                            pxPerFrame = pxPerFrame, fps = fps, measurer = measurer, labelStyle = clipLabelStyle, palette = palette,
                            frameAspect = ui.frameAspect, framing = ui.framings[selected.id], transitionSpan = ui.transitionSpans[selected.id],
                            leadOutside = false, inner = ui.compoundBars[selected.id], compoundLabel = ui.compoundLabels[selected.id],
                        )
                    }
                }
            }
        }
        volumeDrag?.let { drag ->
            val clip = ui.clips.firstOrNull { it.id == drag.clipId } ?: return@let
            val levels = drag.keyFrames.mapNotNull { f -> clip.volumeKeyframes.firstOrNull { it.frame == f }?.volumeDb }
                .ifEmpty { listOf(clip.volumeDb) }
            VolumeReadout(levels, drag.at)
        }
        transitionDrag?.let { drag ->
            val frames = ui.clips.firstOrNull { it.id == drag.clipId }?.transitionIn?.frames ?: return@let
            DragReadout("转场 ${formatValue(frames.toFloat() / fps, 1, false)} 秒", drag.at)
        }
        markerDrag?.let { drag ->
            val marker = ui.markers.firstOrNull { it.id == drag.markerId } ?: return@let
            DragReadout(
                if (drag.end) "标记时长 ${formatValue(marker.durationFrames.toFloat() / fps, 1, false)} 秒" else "标记 ${Timecode.format(marker.frame, fps)}",
                drag.at,
            )
        }
        fadeDrag?.let { drag ->
            val clip = ui.clips.firstOrNull { it.id == drag.clipId } ?: return@let
            val what = (if (clip.kind == ClipKind.AUDIO) "" else "画面") + if (drag.fadeIn) "淡入" else "淡出"
            if (drag.curve) {
                DragReadout("${what}曲线 ${ClipFades.curveLabel(if (drag.fadeIn) clip.fadeInCurve else clip.fadeOutCurve)}", drag.at)
            } else {
                val seconds = if (drag.fadeIn) clip.fadeInSeconds else clip.fadeOutSeconds
                DragReadout("$what ${formatValue(seconds, 2, false)} 秒", drag.at)
            }
        }
        PlayheadLayer(playheadFrame, pxPerFrame, scroll, rulerPx)
        if (ui.clips.none { clip -> ui.tracks.any { it.id == clip.trackId } }) {
            Box(
                Modifier
                    .offset { IntOffset(0, lanes.origin.roundToInt()) }
                    .size(with(density) { viewportPx.toDp() }, with(density) { (ui.tracks.size * trackPx).toDp() }),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyMessage, style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
            }
        }
        // The menu's anchor: the point where the press was held.
        val menu = menuAt
        if (menu != null && clipMenu != null) {
            Box(Modifier.offset { IntOffset(menu.second.x.roundToInt(), menu.second.y.roundToInt()) }.size(1.dp)) {
                clipMenu(menu.first) { menuAt = null }
            }
        }
    }
}

/**
 * The curve a fade's dot gives dragged [dy] px down from where the drag began: up bows the fade up, half the clip's height
 * bends it all the way, and near straight snaps to straight.
 */
private fun curveAt(hit: TimelineHit.FadeCurve, dy: Float): Float {
    val from = if (hit.fadeIn) hit.clip.fadeInCurve else hit.clip.fadeOutCurve
    val curve = (from - dy / hit.unitPx.coerceAtLeast(1f)).coerceIn(-1f, 1f)
    return if (abs(curve) < CURVE_SNAP) 0f else (curve * 100f).roundToInt() / 100f
}

/**
 * `timeline` part fadeCurveDot: where [clip]'s fade in (or out) has the middle of its curve in [rect] (see [ClipFades.dot]),
 * or null while the fade is shorter on screen than [minWidth].
 */
private fun fadeCurveDot(clip: Clip, rect: Rect, fadeIn: Boolean, pxPerFrame: Float, fps: Int, minWidth: Float): Offset? {
    val width = (if (fadeIn) clip.fadeInSeconds else clip.fadeOutSeconds) * fps * pxPerFrame
    if (width < minWidth || width <= 0f) return null
    val (along, up) = ClipFades.dot(if (fadeIn) clip.fadeInCurve else clip.fadeOutCurve)
    return Offset(if (fadeIn) rect.left + width * along else rect.right - width * along, rect.bottom - rect.height * up)
}

private fun fadeSeconds(hit: TimelineHit.Fade, frame: Int, fps: Int): Float {
    val clip = hit.clip
    val frames = if (hit.fadeIn) frame - clip.start else clip.end - frame
    return (frames.toFloat() / fps).coerceIn(0f, ClipFades.max(clip, fps))
}

private fun clipBounds(clip: Clip, laneTop: Float, pxPerFrame: Float, scroll: Float, trackPx: Float): Rect {
    val inset = 4f * (trackPx / 72f).coerceAtLeast(1f)
    return Rect(
        left = clip.start * pxPerFrame - scroll,
        top = laneTop + inset,
        right = clip.end * pxPerFrame - scroll,
        bottom = laneTop + trackPx - inset,
    )
}

@Composable
private fun PlayheadLayer(frame: Int, pxPerFrame: Float, scroll: Float, rulerPx: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val x = frame * pxPerFrame - scroll
        if (x < -8f || x > size.width + 8f) return@Canvas
        drawLine(PrismColors.Playhead, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        drawRoundRect(
            PrismColors.Playhead,
            topLeft = Offset(x - 6.dp.toPx(), 2.dp.toPx()),
            size = Size(12.dp.toPx(), rulerPx - 10.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

/**
 * Timeline In / Out: the marked range lit on the ruler, and at each mark a bracket on the ruler (its arm
 * pointing into the range) with a line down through the tracks.
 */
private fun DrawScope.drawMarks(marks: TimelineMarks, pxPerFrame: Float, scroll: Float, rulerPx: Float) {
    if (marks.isEmpty) return
    val inX = marks.inFrame?.let { it * pxPerFrame - scroll }
    val outX = marks.outFrame?.let { it * pxPerFrame - scroll }
    if (inX != null && outX != null) drawRect(PrismColors.InOutRange, topLeft = Offset(inX, 0f), size = Size(outX - inX, rulerPx))
    val stroke = 2.dp.toPx()
    val arm = 8.dp.toPx()
    for ((x, direction) in listOfNotNull(inX?.let { it to 1f }, outX?.let { it to -1f })) {
        if (x < -arm || x > size.width + arm) continue
        drawLine(PrismColors.MarkerFlag, Offset(x, 0f), Offset(x, rulerPx), strokeWidth = stroke)
        drawLine(PrismColors.MarkerFlag, Offset(x, stroke / 2), Offset(x + direction * arm, stroke / 2), strokeWidth = stroke)
        drawLine(PrismColors.MarkerFlag, Offset(x, rulerPx - stroke / 2), Offset(x + direction * arm, rulerPx - stroke / 2), strokeWidth = stroke)
        drawLine(PrismColors.MarkerFlag, Offset(x, rulerPx), Offset(x, size.height), strokeWidth = 1.dp.toPx(), alpha = 0.7f)
    }
}

private fun DrawScope.drawRuler(
    ui: TimelineUi,
    pxPerFrame: Float,
    scroll: Float,
    rulerPx: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    palette: TimelinePalette,
) {
    val fps = ui.fps.coerceAtLeast(1)
    val pxPerSecond = pxPerFrame * fps
    val minSpacing = 88.dp.toPx()
    val major = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600).firstOrNull { it * pxPerSecond >= minSpacing } ?: 3600
    val minor = major / 5f
    val firstSecond = (scroll / pxPerSecond).toInt()
    val lastSecond = ((scroll + size.width) / pxPerSecond).toInt() + 1
    var s = (firstSecond / major) * major.toFloat()
    while (s <= lastSecond) {
        val x = s * pxPerSecond - scroll
        drawLine(palette.ruler, Offset(x, rulerPx - 12.dp.toPx()), Offset(x, rulerPx), strokeWidth = 1.dp.toPx())
        val total = s.toInt()
        val label = if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60) else "%02d:%02d".format(total / 60, total % 60)
        drawText(measurer.measure(label, style.copy(color = palette.ruler)), topLeft = Offset(x + 4.dp.toPx(), 2.dp.toPx()))
        if (minor * pxPerSecond >= 8.dp.toPx()) {
            for (i in 1 until 5) {
                val mx = (s + i * minor) * pxPerSecond - scroll
                drawLine(palette.ruler, Offset(mx, rulerPx - 5.dp.toPx()), Offset(mx, rulerPx), strokeWidth = 1f, alpha = 0.6f)
            }
        }
        s += major
    }
    drawLine(palette.divider, Offset(0f, rulerPx), Offset(size.width, rulerPx), strokeWidth = 1f)
}

/**
 * A sound clip's volume line (`timeline` part volumeLine, Resolve's audio overlay): its level on the −30…+12 dB scale,
 * loud at the top — flat at its one volume, or through its volume keyframes, curving where a keyframe eases, with a
 * marker per keyframe. The selected clip's line, the one a drag takes, is drawn stronger.
 */
private fun DrawScope.drawVolumeLine(clip: Clip, rect: Rect, pxPerFrame: Float, palette: TimelinePalette, emphasized: Boolean) {
    val line = VolumeLineGeometry(rect, pxPerFrame, VOLUME_INSET.toPx())
    val alpha = if (emphasized) 0.95f else 0.55f
    val width = (if (emphasized) 2f else 1.5f).dp.toPx()
    val keys = clip.volumeKeyframes
    if (keys.isEmpty()) {
        val y = line.y(clip.volumeDb)
        drawLine(palette.onClip, Offset(rect.left, y), Offset(rect.right, y), strokeWidth = width, alpha = alpha)
        return
    }
    val path = Path().apply {
        moveTo(rect.left, line.y(keys.first().volumeDb))
        lineTo(line.x(keys.first().frame), line.y(keys.first().volumeDb))
        keys.zipWithNext { a, b ->
            val eased = a.ease.slopeOut != 1f || b.ease.slopeIn != 1f
            val steps = if (eased) CURVE_STEPS else 1
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                val db = a.volumeDb + (b.volumeDb - a.volumeDb) * easedProgress(t, a.ease.slopeOut, b.ease.slopeIn)
                lineTo(line.x(a.frame) + (line.x(b.frame) - line.x(a.frame)) * t, line.y(db))
            }
        }
        lineTo(rect.right, line.y(keys.last().volumeDb))
    }
    drawPath(path, palette.onClip, alpha = alpha, style = Stroke(width = width))
    val r = 4.dp.toPx()
    keys.forEach { key ->
        val x = line.x(key.frame)
        if (x < rect.left || x > rect.right) return@forEach
        val glyph = keyGlyph(x, line.y(key.volumeDb), r, key.ease)
        drawPath(glyph, palette.onClip)
        drawPath(glyph, PrismColors.PictureBlack, alpha = 0.6f, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * `timeline` part volumeReadout: the level a volume line drag has reached, by the pointer — one value, or the two
 * keyframes either side of a dragged stretch. The timeline draws it like its other marks, lifted above the selected clip.
 */
@Composable
private fun VolumeReadout(levels: List<Float>, at: Offset) {
    val text = levels.joinToString(" / ") { formatValue(it, 1, true) } + " dB"
    DragReadout(text, at, "音量 $text")
}

/** What a drag sets, in a plate beside the pointer. */
@Composable
private fun DragReadout(source: String, at: Offset, sourceDescription: String = source) {
    val text = tr(source)
    val description = tr(sourceDescription)
    val measurer = rememberTextMeasurer()
    val style = PrismType.timecodeSmall.copy(color = PicoTheme.colorScheme.labelPrimaryLight)
    Canvas(Modifier.fillMaxSize().offsetZ(z = 16.dp).semantics { contentDescription = description.localized() }) {
        val laid = measurer.measure(text, style)
        val padX = 8.dp.toPx()
        val padY = 4.dp.toPx()
        val corner = Offset(at.x + 16.dp.toPx(), at.y - 36.dp.toPx())
        drawRoundRect(
            PrismColors.PictureBlack,
            topLeft = corner,
            size = Size(laid.size.width + 2 * padX, laid.size.height + 2 * padY),
            cornerRadius = CornerRadius(6.dp.toPx()),
            alpha = 0.82f,
        )
        drawText(laid, topLeft = Offset(corner.x + padX, corner.y + padY))
    }
}

/** Keyframe markers along the bottom edge of an animated clip. */
private fun DrawScope.drawKeyframes(clip: Clip, rect: Rect, pxPerFrame: Float, palette: TimelinePalette) {
    val r = 4.dp.toPx()
    val y = rect.bottom - r - 3.dp.toPx()
    // The transform's keyframes with their easing, then the effects' where the transform has none.
    val marks = clip.keyframes.map { it.frame to it.ease } +
        clip.effects.flatMap { e -> e.keyframes.map { it.frame to it.ease } }.filter { (f, _) -> clip.keyframes.none { it.frame == f } }.distinctBy { it.first }
    marks.forEach { (frame, ease) ->
        val x = rect.left + (frame + 0.5f) * pxPerFrame
        if (x < rect.left + r || x > rect.right - r) return@forEach
        val glyph = keyGlyph(x, y, r, ease)
        drawPath(glyph, palette.onClip)
        drawPath(glyph, PrismColors.PictureBlack, alpha = 0.6f, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * A keyframe marker of radius [r] at ([x], [y]): a diamond whose left half is round where the motion
 * eases in and whose right half is round where it eases out.
 */
private fun keyGlyph(x: Float, y: Float, r: Float, ease: KeyEase): Path = Path().apply {
    val box = Rect(x - r, y - r, x + r, y + r)
    moveTo(x, y - r)
    if (ease.easesIn) {
        arcTo(box, 270f, -180f, false)
    } else {
        lineTo(x - r, y)
        lineTo(x, y + r)
    }
    if (ease.easesOut) {
        arcTo(box, 90f, -180f, false)
    } else {
        lineTo(x + r, y)
        lineTo(x, y - r)
    }
    close()
}

/** Line segments per eased stretch of a volume line. */
private const val CURVE_STEPS = 16
/** How far toward white a selected clip with a clip colour is drawn. */
private const val SELECTED_LIGHTEN = 0.28f

/** The width each flag takes on a clip, dp. */
private const val FLAG_STEP = 10

/**
 * Resolve's flags in a row from [topLeft]: each a pole and a notched cloth (ic_flag's shape, 10 dp tall) in its
 * colour, over a dark outline so it reads on any clip colour.
 */
private fun DrawScope.drawFlags(flags: List<FlagColor>, topLeft: Offset, alpha: Float) {
    val unit = 10.dp.toPx() / 20f
    flags.forEachIndexed { i, color ->
        val x = topLeft.x + i * FLAG_STEP.dp.toPx() - 3 * unit
        val y = topLeft.y - 2 * unit
        // ic_flag's 24-unit geometry: pole 4–6 × 2–22, cloth 6–20 × 3–14 with the notch at 16.5.
        val flag = Path().apply {
            moveTo(x + 4 * unit, y + 2 * unit); lineTo(x + 6 * unit, y + 2 * unit); lineTo(x + 6 * unit, y + 3 * unit)
            lineTo(x + 20 * unit, y + 3 * unit); lineTo(x + 16.5f * unit, y + 8.5f * unit); lineTo(x + 20 * unit, y + 14 * unit)
            lineTo(x + 6 * unit, y + 14 * unit); lineTo(x + 6 * unit, y + 22 * unit); lineTo(x + 4 * unit, y + 22 * unit); close()
        }
        drawPath(flag, PrismColors.PictureBlack, alpha = 0.55f * alpha, style = Stroke(width = 1.5f.dp.toPx()))
        drawPath(flag, PrismColors.flag(color), alpha = alpha)
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawClip(
    clip: Clip,
    rect: Rect,
    selected: Boolean,
    enabled: Boolean,
    locked: Boolean,
    linked: Boolean = false,
    thumb: ImageBitmap?,
    waveform: Waveform?,
    showWaveform: Boolean,
    fades: FadeMarks,
    offline: Boolean,
    reverseLabel: String?,
    pxPerFrame: Float,
    fps: Int,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    palette: TimelinePalette,
    duck: TrackDuck? = null,
    /** Resolve's flags on the clip's media, drawn at its top right. */
    flags: List<FlagColor> = emptyList(),
    /** The timeline's picture shape: the thumbnail shows the clip's picture in it as its Scaling places it ([framing]). */
    frameAspect: Float = 16f / 9f,
    framing: Framing? = null,
    /** Where the clip's opening transition plays around its cut (before its start when it leads in); null: from the cut. */
    transitionSpan: TransitionSpanUi? = null,
    /** Draws the part of that transition before the clip's start here (false for the lifted, selected clip). */
    leadOutside: Boolean = true,
    /** A compound clip's contents in its window, drawn as a miniature of its tracks. */
    inner: List<TimelinePoolUseCase.Bar>? = null,
    /** A compound clip's badge: 复合片段, or 嵌套时间线 for another timeline played as a clip. */
    compoundLabel: String? = null,
) {
    val highlighted = selected || linked
    // A clip colour (Resolve's label) replaces its kind's; selected, it lightens as the kinds' colours do.
    val label = clip.color?.let(PrismColors::clip)
    val fill = when {
        label != null -> if (highlighted) lerp(label, Color.White, SELECTED_LIGHTEN) else label
        clip.kind == ClipKind.VIDEO -> if (highlighted) PrismColors.VideoClipSelected else PrismColors.VideoClip
        clip.kind == ClipKind.AUDIO -> if (highlighted) PrismColors.AudioClipSelected else PrismColors.AudioClip
        else -> if (highlighted) PrismColors.TitleClipSelected else PrismColors.TitleClip
    }
    val alpha = if (enabled) 1f else 0.35f
    val radius = CornerRadius(8.dp.toPx())
    val pad = 8.dp.toPx()
    drawRoundRect(fill, topLeft = rect.topLeft, size = rect.size, cornerRadius = radius, alpha = alpha)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        var textLeft = rect.left + pad
        if (clip.kind == ClipKind.VIDEO && thumb != null && rect.width > 72.dp.toPx()) {
            val h = rect.height - 2 * 6.dp.toPx()
            val w = h * frameAspect
            if (rect.width > w + 3 * pad) {
                val box = Rect(Offset(rect.left + 6.dp.toPx(), rect.top + 6.dp.toPx()), Size(w, h))
                val place = thumbPlacement(framing, thumb.width, thumb.height, box.size)
                // Fitted pictures of another shape leave bars, black as in the program.
                if (place.dst.width < w - 0.5f || place.dst.height < h - 0.5f) {
                    drawRect(PrismColors.PictureBlack, topLeft = box.topLeft, size = box.size, alpha = alpha)
                }
                drawImage(
                    thumb,
                    srcOffset = IntOffset(place.src.left.roundToInt(), place.src.top.roundToInt()),
                    srcSize = IntSize(place.src.width.roundToInt().coerceAtLeast(1), place.src.height.roundToInt().coerceAtLeast(1)),
                    dstOffset = IntOffset((box.left + place.dst.left).roundToInt(), (box.top + place.dst.top).roundToInt()),
                    dstSize = IntSize(place.dst.width.roundToInt().coerceAtLeast(1), place.dst.height.roundToInt().coerceAtLeast(1)),
                    alpha = alpha,
                )
                textLeft = rect.left + 6.dp.toPx() + w + pad
            }
        }
        if (clip.kind == ClipKind.AUDIO && showWaveform && waveform != null) {
            drawWaveform(clip, rect, waveform, pxPerFrame, fps, alpha)
        }
        if (clip.kind == ClipKind.AUDIO && duck != null) drawDuck(clip, rect, duck, pxPerFrame, fps, palette)
        drawTransitionBox(clip, rect, transitionSpan, pxPerFrame, palette)
        // `timeline` part speedPoints: Resolve's speed points — a line where each speed section meets the next.
        if (clip.retimed) drawSpeedPoints(clip, rect, pxPerFrame, palette, alpha)
        // `timeline` part crossFade: an audio cross fade, its two ramps crossing over its length (a dissolve has one).
        if (clip.kind == ClipKind.AUDIO && clip.crossFadeFrames > 0) {
            val w = max(clip.crossFadeFrames * pxPerFrame, 14.dp.toPx())
            drawRoundRect(palette.transition, topLeft = rect.topLeft, size = Size(w, rect.height), cornerRadius = CornerRadius(6.dp.toPx()))
            drawLine(palette.transitionMark, Offset(rect.left, rect.bottom), Offset(rect.left + w, rect.top), strokeWidth = 1.5f.dp.toPx())
            drawLine(palette.transitionMark, Offset(rect.left, rect.top), Offset(rect.left + w, rect.bottom), strokeWidth = 1.5f.dp.toPx())
        }
        if (fades != FadeMarks.NONE) drawFades(clip, rect, pxPerFrame, fps, palette)
        if (clip.kind != ClipKind.AUDIO && (clip.keyframes.isNotEmpty() || clip.effects.any { it.keyframes.isNotEmpty() })) {
            drawKeyframes(clip, rect, pxPerFrame, palette)
        }
        if (clip.kind == ClipKind.AUDIO) drawVolumeLine(clip, rect, pxPerFrame, palette, emphasized = selected)
        val badges = buildList {
            if (clip.isCompound) add(compoundLabel ?: "复合片段")
            if (!clip.enabled) add("已停用")
            if (offline) add("离线")
            // Speed points: each section's speed in order (Resolve's retime bar), else the clip's speed when not 1×.
            if (clip.retimed) add(speedBadge(clip)) else if (!clip.speed.isNormal) add(clip.speed.label)
            reverseLabel?.let(::add)
            if (clip.effects.isNotEmpty()) add("fx")
            // A sound's own equaliser (Resolve's clip EQ).
            if (clip.kind == ClipKind.AUDIO && clip.eq.isActive) add("EQ")
            // A sound shifted in pitch (Resolve's clip pitch).
            if (clip.kind == ClipKind.AUDIO && clip.pitchCents != 0) add(Clip.pitchBadge(clip.pitchCents))
            if (clip.grade != ColorGrade.NEUTRAL) add("调色")
        }.joinToString(" · ").localized()
        // Names the app made ("定格 · …", "复合片段 1") read in the language shown; the user's own pass through.
        val name = clip.name.localized()
        // `timeline` part flags: the media's flags at the clip's top right, when the name keeps some room beside them.
        val flagStep = FLAG_STEP.dp.toPx()
        val flagsWidth = if (flags.isNotEmpty() && rect.right - textLeft - pad > flags.size * flagStep + 32.dp.toPx()) flags.size * flagStep else 0f
        if (flagsWidth > 0f) drawFlags(flags, Offset(rect.right - pad - flagsWidth, rect.top + 7.dp.toPx()), alpha)
        val available = rect.right - textLeft - pad - flagsWidth - if (flagsWidth > 0f) 4.dp.toPx() else 0f
        // `timeline` part compound: the contents' clips where they lie in the clip, a row per track, right of the label.
        if (inner != null) {
            val labelWidth = maxOf(
                measurer.measure(name, labelStyle).size.width,
                if (badges.isNotEmpty() && rect.height > 40.dp.toPx()) measurer.measure(badges, labelStyle).size.width else 0,
            )
            drawCompoundBars(inner, rect, textLeft + labelWidth + 12.dp.toPx(), palette, alpha)
        }
        if (available > 24.dp.toPx()) {
            drawText(
                measurer, name, topLeft = Offset(textLeft, rect.top + 6.dp.toPx()),
                style = labelStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
                size = Size(available, 18.dp.toPx()),
            )
            if (badges.isNotEmpty() && rect.height > 40.dp.toPx()) {
                drawText(
                    measurer, badges, topLeft = Offset(textLeft, rect.bottom - 22.dp.toPx()),
                    style = labelStyle.copy(color = if (offline) palette.error else palette.onClip),
                    overflow = TextOverflow.Ellipsis, maxLines = 1, size = Size(available, 18.dp.toPx()),
                )
            }
        }
        if (locked) {
            var x = rect.left - rect.height
            while (x < rect.right) {
                drawLine(palette.divider, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = 1f)
                x += 10.dp.toPx()
            }
        }
    }
    // A transition leading in before the cut lies over the clip before it (the lifted, selected clip leaves that part to
    // the timeline beneath it).
    if (leadOutside && (transitionSpan?.lead ?: 0) > 0) clipRect(right = rect.left) { drawTransitionBox(clip, rect, transitionSpan, pxPerFrame, palette) }
    if (selected) {
        drawRoundRect(palette.onClip, topLeft = Offset(rect.left, rect.top), size = Size(4.dp.toPx(), rect.height), cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(palette.onClip, topLeft = Offset(rect.right - 4.dp.toPx(), rect.top), size = Size(4.dp.toPx(), rect.height), cornerRadius = CornerRadius(2.dp.toPx()))
    }
    if (fades == FadeMarks.HANDLES || fades == FadeMarks.EDGE_HANDLES) drawFadeHandles(clip, rect, pxPerFrame, fps, palette, fades)
}

/**
 * `timeline` part speedPoints: Resolve's speed points — a line where each speed section meets the next. A smoothed point
 * (a ramp) shows its eased change over the ramp's length instead: a lit band with an S across it, rising where the clip
 * speeds up and falling where it slows, from the slower speed's height near the bottom to the faster's near the top.
 */
private fun DrawScope.drawSpeedPoints(clip: Clip, rect: Rect, pxPerFrame: Float, palette: TimelinePalette, alpha: Float) {
    val bounds = clip.sectionBounds
    val inset = 3.dp.toPx()
    val stroke = 2.dp.toPx()
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        for (k in 1 until bounds.lastIndex) {
            val x = rect.left + bounds[k] * pxPerFrame
            val ramp = clip.rampAt(k)
            if (ramp == 0) {
                drawLine(palette.onClip, Offset(x, rect.top + inset), Offset(x, rect.bottom - inset), strokeWidth = stroke, alpha = 0.85f * alpha)
                continue
            }
            val faster = clip.speedSections[k].speed.factor > clip.speedSections[k - 1].speed.factor
            val w = ramp * pxPerFrame
            val from = x - w / 2
            val (y0, y1) = if (faster) (rect.bottom - inset) to (rect.top + inset) else (rect.top + inset) to (rect.bottom - inset)
            drawRect(palette.onClip, topLeft = Offset(from, rect.top), size = Size(w, rect.height), alpha = 0.16f * alpha)
            val curve = Path()
            val steps = maxOf(RAMP_CURVE_STEPS, (w / 3.dp.toPx()).toInt())
            for (i in 0..steps) {
                val u = i / steps.toDouble()
                val y = y0 + (y1 - y0) * SpeedRamp.eased(u).toFloat()
                if (i == 0) curve.moveTo(from, y) else curve.lineTo(from + w * u.toFloat(), y)
            }
            drawPath(curve, palette.onClip, alpha = 0.9f * alpha, style = Stroke(width = stroke))
        }
    }
}

/** Points a ramp's curve is drawn through, at the least. */
private const val RAMP_CURVE_STEPS = 8

/**
 * A compound clip's contents in [rect]: each track a thin row (the top track first), centred in the clip's height, each
 * clip a bar where it lies in the clip's window — drawn from [left] on (the label keeps its room).
 */
private fun DrawScope.drawCompoundBars(bars: List<TimelinePoolUseCase.Bar>, rect: Rect, left: Float, palette: TimelinePalette, alpha: Float) {
    if (bars.isEmpty()) return
    val pad = 8.dp.toPx()
    val rows = bars.maxOf { it.row } + 1
    val gap = 2.dp.toPx()
    val rowHeight = ((rect.height - 2 * pad - gap * (rows - 1)) / rows).coerceAtMost(6.dp.toPx())
    if (rowHeight < 2.dp.toPx() || rect.right - pad - left < 16.dp.toPx()) return
    val top = rect.center.y - (rows * rowHeight + (rows - 1) * gap) / 2
    val span = rect.width - 2 * pad
    for (bar in bars) {
        val y = top + bar.row * (rowHeight + gap)
        val x0 = maxOf(left, rect.left + pad + bar.from.coerceIn(0f, 1f) * span)
        val x1 = rect.left + pad + bar.until.coerceIn(0f, 1f) * span - 1f
        if (x1 - x0 < 1f) continue
        drawRoundRect(palette.onClip, topLeft = Offset(x0, y), size = Size(x1 - x0, rowHeight), cornerRadius = CornerRadius(rowHeight / 2), alpha = alpha * 0.55f)
    }
}

/** The clip's sections' speeds on its badge — "变速 1× ～ 0.25× → 1×" — a ～ where a ramp smooths the change. */
private fun speedBadge(clip: Clip): String = buildString {
    append("变速 ")
    clip.speedSections.forEachIndexed { k, section ->
        if (k > 0) append(if (clip.rampAt(k) > 0) " ～ " else " → ")
        append(section.speed.label)
    }
}

/**
 * `timeline` part transition: the clip's opening transition over the cut as it really plays — centred on it, ending at it
 * or starting at it (Resolve's alignment) — a box with its rising diagonal.
 */
private fun DrawScope.drawTransitionBox(clip: Clip, rect: Rect, span: TransitionSpanUi?, pxPerFrame: Float, palette: TimelinePalette) {
    val transition = clip.transitionIn ?: return
    val left = rect.left - (span?.lead ?: 0) * pxPerFrame
    val w = max((span?.frames ?: transition.frames) * pxPerFrame, 14.dp.toPx())
    drawRoundRect(palette.transition, topLeft = Offset(left, rect.top), size = Size(w, rect.height), cornerRadius = CornerRadius(6.dp.toPx()))
    drawLine(palette.transitionMark, Offset(left, rect.bottom), Offset(left + w, rect.top), strokeWidth = 1.5f.dp.toPx())
}

private fun DrawScope.drawWaveform(clip: Clip, rect: Rect, waveform: Waveform, pxPerFrame: Float, fps: Int, alpha: Float) {
    val top = rect.top + 24.dp.toPx()
    val height = rect.bottom - 4.dp.toPx() - top
    if (height <= 4f) return
    val mid = top + height / 2f
    val step = 2.dp.toPx()
    var x = maxOf(rect.left, 0f)
    val end = minOf(rect.right, size.width)
    while (x < end) {
        // Through the clip's speed sections, if any.
        val from = clip.sourceInMs + clip.sourceOffsetMs(((x - rect.left) / pxPerFrame).toDouble(), fps).toLong()
        val until = clip.sourceInMs + clip.sourceOffsetMs(((x + step - rect.left) / pxPerFrame).toDouble(), fps).toLong()
        val peak = waveform.peak(from, maxOf(until, from + 1))
        val h = (Waveform.displayLevel(peak, clip.volumeDb) * height / 2f).coerceAtLeast(0.5f)
        drawLine(PrismColors.Waveform, Offset(x, mid - h), Offset(x, mid + h), strokeWidth = step * 0.6f, alpha = 0.85f * alpha)
        x += step
    }
}

/**
 * Where the track's ducking dips this clip: a veil hanging from the top of the waveform as deep as the
 * dip (the full height is −30 dB), edged in the accent; nothing where the track is not dipped.
 */
private fun DrawScope.drawDuck(clip: Clip, rect: Rect, duck: TrackDuck, pxPerFrame: Float, fps: Int, palette: TimelinePalette) {
    val top = rect.top + 24.dp.toPx()
    val height = rect.bottom - 4.dp.toPx() - top
    if (height <= 4f || duck.depthDb <= 0f) return
    val usPerPx = 1_000_000.0 / fps / pxPerFrame
    val startUs = clip.start * 1_000_000L / fps
    val step = 2.dp.toPx()
    val veil = Path()
    val edge = Path()
    var open = false
    var x = maxOf(rect.left, 0f)
    val end = minOf(rect.right, size.width)
    while (x <= end) {
        val dip = duck.depthDb * duck.envelope.amountAt(startUs + ((x - rect.left) * usPerPx).toLong())
        val y = top + (dip / DUCK_RANGE_DB).coerceIn(0f, 1f) * height
        if (dip > 0.05f) {
            if (!open) {
                veil.moveTo(x, top)
                edge.moveTo(x, top)
                open = true
            }
            veil.lineTo(x, y)
            edge.lineTo(x, y)
        } else if (open) {
            veil.lineTo(x, top)
            veil.close()
            edge.lineTo(x, top)
            open = false
        }
        x += step
    }
    if (open) {
        veil.lineTo(end, top)
        veil.close()
    }
    drawPath(veil, PrismColors.PictureBlack, alpha = 0.35f)
    drawPath(edge, palette.duck, style = Stroke(width = 1.5f.dp.toPx()))
}

/** A ducking dip as deep as this reaches the bottom of a clip's waveform. */
private const val DUCK_RANGE_DB = 30f

/**
 * `timeline` part fadeHandles: a clip's fades — a shaded corner over each faded end, above the curve its level or opacity
 * climbs along (drawn inside the clip; its handles come after it, [drawFadeHandles]).
 */
private fun DrawScope.drawFades(clip: Clip, rect: Rect, pxPerFrame: Float, fps: Int, palette: TimelinePalette) {
    val fin = clip.fadeInSeconds * fps * pxPerFrame
    val fout = clip.fadeOutSeconds * fps * pxPerFrame
    if (fin > 0f) drawFade(rect, fin, clip.fadeInCurve, fadeIn = true, palette)
    if (fout > 0f) drawFade(rect, fout, clip.fadeOutCurve, fadeIn = false, palette)
}

/**
 * One fade [width] px long, from silence at the clip's edge (its foot) to full at the fade's far end (its top), along its
 * curve: the quadratic Bézier of [ClipFades.shape] — an affine map keeps it one — with the corner above it shaded.
 */
private fun DrawScope.drawFade(rect: Rect, width: Float, curve: Float, fadeIn: Boolean, palette: TimelinePalette) {
    val edge = if (fadeIn) rect.left else rect.right
    val toward = if (fadeIn) width else -width
    val c = curve.coerceIn(-1f, 1f)
    val line = Path().apply {
        moveTo(edge, rect.bottom)
        quadraticTo(edge + toward * (0.5f - 0.5f * c), rect.bottom - rect.height * (0.5f + 0.5f * c), edge + toward, rect.top)
    }
    val shade = Path().apply {
        addPath(line)
        lineTo(edge, rect.top)
        close()
    }
    drawPath(shade, PrismColors.PictureBlack, alpha = 0.35f)
    drawPath(line, palette.onClip, style = Stroke(1.5f.dp.toPx()))
}

/**
 * The handles where a clip's fades begin, whole over the clip's edges: inside its top (the Audio page's), or on its top
 * edge, clear of its name ([FadeMarks.EDGE_HANDLES], the Edit page's selected clip — Resolve's corner handles).
 */
private fun DrawScope.drawFadeHandles(clip: Clip, rect: Rect, pxPerFrame: Float, fps: Int, palette: TimelinePalette, marks: FadeMarks) {
    val edge = marks == FadeMarks.EDGE_HANDLES
    val handle = (if (edge) 7 else 8).dp.toPx()
    val hy = rect.top + (if (edge) 2 else 10).dp.toPx()
    for (x in listOf(rect.left + clip.fadeInSeconds * fps * pxPerFrame, rect.right - clip.fadeOutSeconds * fps * pxPerFrame)) {
        drawCircle(palette.onClip, radius = handle, center = Offset(x, hy))
        drawCircle(PrismColors.PictureBlack, radius = handle, center = Offset(x, hy), style = Stroke(1.dp.toPx()), alpha = 0.4f)
    }
    // `timeline` part fadeCurveDot: Resolve's fade curve dot in the middle of each fade long enough to show it — drag it up
    // or down to bend the fade.
    for (fadeIn in listOf(true, false)) {
        val dot = fadeCurveDot(clip, rect, fadeIn, pxPerFrame, fps, FADE_DOT_MIN.toPx()) ?: continue
        drawCircle(PrismColors.PictureBlack, radius = 5.5f.dp.toPx(), center = dot, alpha = 0.45f)
        drawCircle(palette.onClip, radius = 4.dp.toPx(), center = dot)
    }
}
