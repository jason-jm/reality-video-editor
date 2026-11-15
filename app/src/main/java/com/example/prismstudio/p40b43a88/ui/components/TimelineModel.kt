package com.example.prismstudio.p40b43a88.ui.components

import com.example.prismstudio.p40b43a88.domain.usecase.TimelineGap
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Everything the `timeline` renderer draws except the playhead (which moves every frame). */
@Immutable
data class TimelineUi(
    val tracks: List<Track> = emptyList(),
    val clips: List<Clip> = emptyList(),
    val markers: List<Marker> = emptyList(),
    val fps: Int = 30,
    val durationFrames: Int = 0,
    val selectedClipId: String? = null,
    /** Every selected clip ([selectedClipId], lifted toward the viewer, among them). */
    val selectedClipIds: Set<String> = emptySet(),
    /** The clips that are parts of multicam clips (their menu opens and flattens them as such). */
    val multicamClipIds: Set<String> = emptySet(),
    /** 多选 mode: a tap on a clip adds it to the selection or takes it out. */
    val multiSelect: Boolean = false,
    val zoom: Float = 0.35f,
    /** Audio peaks by asset id. */
    val waveforms: Map<String, Waveform> = emptyMap(),
    /** Poster frames by clip id. */
    val thumbnails: Map<String, Bitmap> = emptyMap(),
    val offlineAssetIds: Set<String> = emptySet(),
    /** Assets whose reversed copy is still being made, or could not be made. */
    val reverseMakingAssetIds: Set<String> = emptySet(),
    val reverseFailedAssetIds: Set<String> = emptySet(),
    /** Tracks that duck under another, by track id: drawn as the dip over their clips. */
    val ducks: Map<String, TrackDuck> = emptyMap(),
    /** The destination controls the track headers show (Edit page only). */
    val targets: EditTargets? = null,
    /** Timeline In / Out, drawn on the ruler (Edit page only). */
    val marks: TimelineMarks = TimelineMarks(),
    /** The marker picked in the marker index (drawn taller, outlined). */
    val selectedMarkerId: String? = null,
    /** Resolve's flags of the media, by asset id: every clip of that media shows them. */
    val flags: Map<String, List<FlagColor>> = emptyMap(),
    /** The timeline's picture shape (width / height): a clip's thumbnail shows its picture in it as its Scaling does. */
    val frameAspect: Float = 16f / 9f,
    /** Where each picture clip's picture sits in the frame (its Scaling or the project's), by clip id; absent: fitted. */
    val framings: Map<String, Framing> = emptyMap(),
    /** Where each opening transition really plays around its cut, by clip id; absent: from the cut, as long as set. */
    val transitionSpans: Map<String, TransitionSpanUi> = emptyMap(),
    /** The gap a tap on an empty lane picked (Resolve): drawn outlined; deleting it closes it. */
    val selectedGap: TimelineGap? = null,
    /** Compound clips' contents in their windows, by clip id: drawn as a miniature of their tracks. */
    val compoundBars: Map<String, List<com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase.Bar>> = emptyMap(),
    /** What each compound clip is, by clip id: 复合片段, or 嵌套时间线 for another timeline of the project (its badge). */
    val compoundLabels: Map<String, String> = emptyMap(),
) {
    /** The flags [clip] shows: its media's. */
    fun flagsOf(clip: Clip): List<FlagColor> = clip.assetId?.let(flags::get).orEmpty()

    /** Badge of a reversed [clip]: plain, still being made, or failed; null when it plays forwards. */
    fun reverseLabel(clip: Clip): String? = when {
        !clip.reverse -> null
        clip.assetId in reverseFailedAssetIds -> "倒放失败"
        clip.assetId in reverseMakingAssetIds -> "倒放生成中"
        else -> "倒放"
    }
}

/**
 * Where a clip's thumbnail goes in its frame-shaped [box] as Resolve's Scaling places the picture ([framing]; null:
 * fitted): [src] the part of the [thumbWidth] × [thumbHeight] thumbnail shown (the middle where the picture overflows
 * the frame), [dst] where it lands, from the box's top left (the middle where the picture leaves bars).
 */
data class ThumbPlacement(val src: Rect, val dst: Rect)

fun thumbPlacement(framing: Framing?, thumbWidth: Int, thumbHeight: Int, box: Size): ThumbPlacement {
    val (spanX, spanY) = framing ?: Framing.fit(thumbWidth.toFloat() / thumbHeight, box.width / box.height)
    // On each axis a span over 1 crops the thumbnail, one under 1 shrinks it inside the box.
    val srcW = thumbWidth / maxOf(spanX, 1f)
    val srcH = thumbHeight / maxOf(spanY, 1f)
    val dstW = box.width * minOf(spanX, 1f)
    val dstH = box.height * minOf(spanY, 1f)
    return ThumbPlacement(
        src = Rect((thumbWidth - srcW) / 2f, (thumbHeight - srcH) / 2f, (thumbWidth + srcW) / 2f, (thumbHeight + srcH) / 2f),
        dst = Rect((box.width - dstW) / 2f, (box.height - dstH) / 2f, (box.width + dstW) / 2f, (box.height + dstH) / 2f),
    )
}

/** A track's ducking as the timeline draws it: how deep it dips and where (see [DuckEnvelope]). */
@Immutable
class TrackDuck(val depthDb: Float, val envelope: DuckEnvelope)

/** Gesture callbacks of the timeline; `commit = false` while dragging (live preview). */
interface TimelineActions {
    fun seek(frame: Int)
    fun select(clipId: String?)
    /** A tap with Ctrl / ⌘ / Shift held, or in 多选 mode: the clip joins the selection or leaves it. */
    fun toggle(clipId: String) = select(clipId)
    /** A marquee from frame [from] to [until] over [trackIds] was let go: the clips it touches join the selection. */
    fun marquee(from: Int, until: Int, trackIds: Set<String>) = Unit
    fun move(clipId: String, start: Int, trackId: String, commit: Boolean) = Unit
    fun trim(clipId: String, edge: TrimEdge, frame: Int, commit: Boolean) = Unit
    fun fade(clipId: String, fadeIn: Boolean, seconds: Float, commit: Boolean) = Unit
    /** A fade's curve dot dragged: the fade in (or out) bent to [curve], −1…1 (see [com.example.prismstudio.p40b43a88.domain.model.ClipFades.shape]). */
    fun fadeCurve(clipId: String, fadeIn: Boolean, curve: Float, commit: Boolean) = Unit
    /**
     * A sound clip's volume line dragged [deltaDb] up from where the drag began: its one level ([keyFrames] empty) or
     * the volume keyframes at those frames from its start.
     */
    fun volume(clipId: String, keyFrames: List<Int>, deltaDb: Float, commit: Boolean) = Unit
    /** An opening transition's outer edge dragged on the timeline: [frames] long now (the clip's own limits apply). */
    fun transition(clipId: String, frames: Int, commit: Boolean) = Unit
    /** A marker's flag dragged along the ruler to [frame], or (with [end]) its duration bar's end. */
    fun marker(markerId: String, frame: Int, end: Boolean, commit: Boolean) = Unit
    /** A tap on a marker's flag: pick it (the marker index's selection) and go to it. */
    fun pickMarker(markerId: String) = Unit
    /** A tap (or a held press) on [trackId]'s empty lane at [frame]: the gap there, if any, is picked; else nothing is selected. */
    fun selectGap(trackId: String, frame: Int) = select(null)
    fun cancelDrag() = Unit
    fun viewport(widthDp: Float) = Unit
}

object TimelineScale {
    const val MIN_DP_PER_SECOND = 4f
    const val MAX_DP_PER_SECOND = 240f

    /** Zoom 0..1 maps logarithmically onto 4…240 dp per second. */
    fun dpPerSecond(zoom: Float): Float =
        MIN_DP_PER_SECOND * (MAX_DP_PER_SECOND / MIN_DP_PER_SECOND).pow(zoom.coerceIn(0f, 1f))

    fun zoomFor(dpPerSecond: Float): Float =
        (ln(dpPerSecond.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND) / MIN_DP_PER_SECOND) /
            ln(MAX_DP_PER_SECOND / MIN_DP_PER_SECOND)).coerceIn(0f, 1f)

    /** Zoom that fits [durationFrames] into [viewportDp] with a little tail room. */
    fun fitZoom(durationFrames: Int, fps: Int, viewportDp: Float): Float {
        if (durationFrames <= 0 || viewportDp <= 0f) return 0.35f
        val seconds = durationFrames.toFloat() / fps.coerceAtLeast(1)
        return zoomFor(viewportDp * 0.94f / seconds)
    }
}

/** `timeline` part transition: an opening transition's box spans [lead] frames before its clip's start, [frames] in all. */
data class TransitionSpanUi(val lead: Int, val frames: Int)

/**
 * A grabbed outer edge of a transition box: dragged, the transition's length becomes the pointer's distance from the cut —
 * twice it when [centred], its edges moving together; [before] is the left edge of one ending on the cut.
 */
data class TransitionGrab(val centred: Boolean, val before: Boolean) {
    /** The length with the edge at timeline [frame], for the transition opening [clip]. */
    fun framesAt(clip: Clip, frame: Int): Int = when {
        centred -> 2 * abs(frame - clip.start)
        before -> clip.start - frame
        else -> frame - clip.start
    }.coerceAtLeast(1)
}

/**
 * The outer edge of [clip]'s transition box (drawn over [span], at least [minWidth] px wide) within [grab] px of [x], in a
 * view [scroll]ed by that many px: the edges away from the cut only — both for a transition centred on it, the left one for
 * one ending on it, the right one otherwise — and only an edge at least [grab] from the cut, so the cut stays a trim.
 */
fun transitionGrabAt(clip: Clip, span: TransitionSpanUi, pxPerFrame: Float, scroll: Float, x: Float, grab: Float, minWidth: Float): TransitionGrab? {
    val transition = clip.transitionIn ?: return null
    val cutX = clip.start * pxPerFrame - scroll
    val left = cutX - span.lead * pxPerFrame
    val right = left + max(span.frames * pxPerFrame, minWidth)
    val centred = transition.type.showsOutgoing && transition.alignment == TransitionAlignment.CENTER
    val before = transition.type.showsOutgoing && transition.alignment == TransitionAlignment.END
    return when {
        (centred || before) && span.lead > 0 && cutX - left >= grab && abs(x - left) < grab -> TransitionGrab(centred, before = !centred)
        !before && span.frames > span.lead && right - cutX >= grab && abs(x - right) < grab -> TransitionGrab(centred, before = false)
        else -> null
    }
}
