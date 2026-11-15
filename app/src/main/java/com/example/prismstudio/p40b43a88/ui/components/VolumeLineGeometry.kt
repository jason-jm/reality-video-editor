package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.ui.geometry.Rect
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.VolumeLine
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where a sound clip's volume line runs in its box on the timeline: [VolumeLine.MAX_DB] at [inset] below the box's
 * top, [VolumeLine.MIN_DB] as far above its bottom, and a frame at the middle of its own stretch.
 */
internal class VolumeLineGeometry(private val box: Rect, private val pxPerFrame: Float, inset: Float) {
    private val top = box.top + inset
    private val bottom = box.bottom - inset

    fun y(db: Float): Float =
        top + (1f - (db.coerceIn(VolumeLine.MIN_DB, VolumeLine.MAX_DB) - VolumeLine.MIN_DB) / RANGE) * (bottom - top)

    fun x(frame: Int): Float = box.left + (frame + 0.5f) * pxPerFrame

    fun frameAt(x: Float): Int = ((x - box.left) / pxPerFrame - 0.5f).roundToInt()

    /** The dB a drag moves the line per pixel, up being louder. */
    val dbPerPx: Float get() = RANGE / (bottom - top).coerceAtLeast(1f)

    private companion object {
        const val RANGE = VolumeLine.MAX_DB - VolumeLine.MIN_DB
    }
}

/**
 * What a press at ([x], [y]) takes hold of on [clip]'s volume line, as Resolve's audio overlay does: null when it
 * misses the line; empty for a clip's one level; otherwise the volume keyframes (frames from the clip's start) a drag
 * moves — the marker under the press, the two either side of the stretch it is on, or the end one past them. Markers
 * are taken anywhere on the clip; the line itself only between [from] and [to], leaving the edges to trimming.
 */
internal fun volumeGrab(
    clip: Clip,
    line: VolumeLineGeometry,
    x: Float,
    y: Float,
    lineSlop: Float,
    markerSlop: Float,
    from: Float,
    to: Float,
): List<Int>? {
    val keys = clip.volumeKeyframes
    keys.firstOrNull { abs(line.x(it.frame) - x) <= markerSlop && abs(line.y(it.volumeDb) - y) <= markerSlop }?.let { return listOf(it.frame) }
    if (x < from || x > to) return null
    val frame = line.frameAt(x).coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0))
    if (abs(line.y(clip.volumeAt(frame)) - y) > lineSlop) return null
    if (keys.isEmpty()) return emptyList()
    val after = keys.indexOfFirst { it.frame > frame }
    return when {
        after == 0 -> listOf(keys.first().frame)
        after < 0 -> listOf(keys.last().frame)
        else -> listOf(keys[after - 1].frame, keys[after].frame)
    }
}
