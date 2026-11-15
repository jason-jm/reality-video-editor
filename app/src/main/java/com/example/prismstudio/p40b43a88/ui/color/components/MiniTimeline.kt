package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.ui.color.MiniClipUi
import com.example.prismstudio.p40b43a88.ui.color.MiniTimelineUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlin.math.floor
import kotlin.math.min

/**
 * `color-mini-timeline`: Resolve's Color page mini timeline — every picture track, the top one first, across the whole
 * program: clips in their clip colour, the clip being graded lifted, clips the clip filter hides faint, and the playhead.
 * A press moves the playhead there and grades the clip under it (a V2 clip over V1 too); dragging scrubs.
 */
@Composable
fun ColorMiniTimeline(
    ui: MiniTimelineUi,
    playheadFrame: Int,
    fps: Int,
    onScrub: (frame: Int, clipId: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** The Deliver page's render range (frames, inclusive): a band behind the lanes edged like the timeline's In / Out. */
    range: IntRange? = null,
    description: String = "迷你时间线",
) {
    // The gesture outlives recompositions (every scrub moves the playhead): it reads the latest clips and callback.
    val latest by rememberUpdatedState(ui)
    val scrub by rememberUpdatedState(onScrub)
    val lane = PicoTheme.colorScheme.fillTertiary
    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .spatialHoverEffect()
            .semantics { contentDescription = "$description，播放头 ${Timecode.format(playheadFrame, fps)}".localized() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geometry = MiniTimelineGeometry(
                        size.width.toFloat(), size.height.toFloat(), latest.lanes, latest.durationFrames,
                        LANE_MAX.toPx(), LANE_GAP.toPx(), MARGIN.toPx(),
                    )
                    var frame = geometry.frameAt(down.position.x)
                    scrub(frame, geometry.clipAt(latest.clips, down.position.x, down.position.y)?.id)
                    down.consume()
                    // A scrub follows the pointer from the first move: no touch slop to cross.
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val next = geometry.frameAt(change.position.x)
                        if (next != frame) {
                            frame = next
                            scrub(frame, null)
                        }
                        change.consume()
                    }
                }
            },
    ) {
        val geometry = MiniTimelineGeometry(size.width, size.height, ui.lanes, ui.durationFrames, LANE_MAX.toPx(), LANE_GAP.toPx(), MARGIN.toPx())
        val radius = CornerRadius(3.dp.toPx())
        if (range != null && ui.durationFrames > 0) {
            val from = geometry.x(range.first)
            val to = geometry.x(range.last + 1)
            val edge = RANGE_EDGE.toPx()
            drawRect(PrismColors.InOutRange, topLeft = Offset(from, 0f), size = Size(to - from, size.height))
            drawRect(PrismColors.MarkerFlag, topLeft = Offset(from, 0f), size = Size(edge, size.height))
            drawRect(PrismColors.MarkerFlag, topLeft = Offset(to - edge, 0f), size = Size(edge, size.height))
        }
        for (i in 0 until ui.lanes) {
            drawRoundRect(lane, topLeft = Offset(0f, geometry.laneTop(i)), size = Size(size.width, geometry.laneHeight), cornerRadius = radius)
        }
        // The clip being graded last, lifted above its lane.
        ui.clips.sortedBy { it.selected }.forEach { clip ->
            val base = clip.color?.let(PrismColors::clip) ?: PrismColors.VideoClip
            val lift = if (clip.selected) MARGIN.toPx() else 0f
            val left = geometry.x(clip.start)
            val right = (geometry.x(clip.end) - 1f).coerceAtLeast(left + 1f)
            drawRoundRect(
                if (clip.selected) lerp(base, Color.White, SELECTED_LIGHTEN) else base,
                topLeft = Offset(left, geometry.laneTop(clip.lane) - lift),
                size = Size(right - left, geometry.laneHeight + 2 * lift),
                cornerRadius = radius,
                alpha = if (clip.dimmed) DIMMED_ALPHA else 1f,
            )
        }
        if (ui.durationFrames > 0) {
            val width = PLAYHEAD.toPx()
            val x = geometry.x(playheadFrame.coerceIn(0, ui.durationFrames))
            drawRoundRect(PrismColors.Playhead, topLeft = Offset(x - width / 2, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(width / 2))
        }
    }
}

/**
 * Where things sit on the mini timeline: the whole program across [width], [lanes] picture tracks centred in [height]
 * (each at most [maxLane] tall, [gap] apart, [margin] clear of the edges for the lifted clip), and what a press takes.
 */
internal class MiniTimelineGeometry(
    private val width: Float,
    height: Float,
    private val lanes: Int,
    private val durationFrames: Int,
    maxLane: Float,
    private val gap: Float,
    margin: Float,
) {
    val laneHeight: Float = if (lanes <= 0) 0f else min(maxLane, (height - 2 * margin - (lanes - 1) * gap) / lanes).coerceAtLeast(1f)
    private val top = (height - (lanes * laneHeight + (lanes - 1).coerceAtLeast(0) * gap)) / 2f

    fun laneTop(lane: Int): Float = top + lane * (laneHeight + gap)

    fun x(frame: Int): Float = if (durationFrames <= 0) 0f else frame.toFloat() / durationFrames * width

    fun frameAt(x: Float): Int = if (durationFrames <= 0) 0 else (x / width * durationFrames).toInt().coerceIn(0, durationFrames - 1)

    /** The lane under [y]; a press in a gap or past the edges takes the nearest one. */
    fun laneAt(y: Float): Int? = if (lanes <= 0) null else floor((y - top + gap / 2) / (laneHeight + gap)).toInt().coerceIn(0, lanes - 1)

    /** The clip a press at ([x], [y]) lands on: the one on that lane at that frame, or null over an empty stretch. */
    fun clipAt(clips: List<MiniClipUi>, x: Float, y: Float): MiniClipUi? {
        val lane = laneAt(y) ?: return null
        val frame = frameAt(x)
        return clips.firstOrNull { it.lane == lane && frame >= it.start && frame < it.end }
    }
}

private val LANE_MAX = 14.dp
private val LANE_GAP = 3.dp
private val MARGIN = 2.dp
private val PLAYHEAD = 2.dp
private val RANGE_EDGE = 2.dp
private const val SELECTED_LIGHTEN = 0.28f
private const val DIMMED_ALPHA = 0.35f
