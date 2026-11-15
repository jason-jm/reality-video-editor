package com.example.prismstudio.p40b43a88.ui.components

import kotlin.math.floor
import kotlin.math.max

/**
 * `timeline` part laneScroll: where the lanes sit under the fixed ruler once they no longer fit the view. They scroll up and
 * down together with the track headers (Resolve's timeline scrolls its tracks; here one stack, video above audio).
 * Screen y counts from the view's top; content y from the first lane's top. [offset] is how far the lanes are scrolled,
 * within 0..[maxOffset].
 */
data class LaneGeometry(val rulerPx: Float, val trackPx: Float, val offset: Float, val viewHeight: Float, val count: Int) {

    /** The first lane's top on screen: the ruler's foot, or above it (hidden under the ruler) once scrolled. */
    val origin: Float get() = rulerPx - offset

    /** Every lane's height together. */
    val contentPx: Float get() = count * trackPx

    /** The lanes' visible height under the ruler (all of them until the view is measured). */
    val visiblePx: Float get() = if (viewHeight <= 0f) contentPx else (viewHeight - rulerPx).coerceAtLeast(0f)

    val maxOffset: Float get() = max(0f, contentPx - visiblePx)

    /** More lanes than the view holds. */
    val scrollable: Boolean get() = maxOffset > 0.5f

    /** Lane [lane]'s top on screen. */
    fun top(lane: Int): Float = origin + lane * trackPx

    /** The lane under screen [y] (below the ruler); past the last lane it is [count] or more. */
    fun laneAt(y: Float): Int = floor((y - origin) / trackPx).toInt()

    /** Screen [y] as content y, which a marquee keeps while the lanes scroll under it. */
    fun toContent(y: Float): Float = y - origin

    fun toScreen(contentY: Float): Float = contentY + origin

    /** Whether lane [lane] shows at least partly under the ruler. */
    fun shows(lane: Int): Boolean = top(lane) + trackPx > rulerPx && (viewHeight <= 0f || top(lane) < viewHeight)

    /**
     * The offset that shows lane [lane] whole, scrolled the least way (to its top above, its bottom below), or null when it
     * already shows whole or nothing scrolls.
     */
    fun reveal(lane: Int): Float? {
        if (lane !in 0 until count || !scrollable) return null
        val top = lane * trackPx
        val bottom = top + trackPx
        return when {
            top < offset - 0.5f -> top
            bottom > offset + visiblePx + 0.5f -> (bottom - visiblePx).coerceAtMost(maxOffset)
            else -> null
        }
    }

    /**
     * How far a drag resting at screen [y] by the lanes' top (the ruler counts) or the view's bottom scrolls them on a tick:
     * a quarter of how deep into the [edge] band it is, up negative, at most half the band.
     */
    fun edgePush(y: Float, edge: Float): Float {
        if (!scrollable) return 0f
        val push = when {
            y > viewHeight - edge -> y - (viewHeight - edge)
            y < rulerPx + edge -> y - (rulerPx + edge)
            else -> 0f
        } * EDGE_RATE
        return push.coerceIn(-edge * 0.5f, edge * 0.5f)
    }

    /** `timeline` part laneScrollbar: the thumb's top and height along the right edge of the lanes, or null when all fit. */
    fun thumb(minPx: Float): Pair<Float, Float>? {
        if (!scrollable) return null
        val height = max(minPx, visiblePx * visiblePx / contentPx).coerceAtMost(visiblePx)
        return rulerPx + (visiblePx - height) * (offset / maxOffset).coerceIn(0f, 1f) to height
    }

    companion object {
        private const val EDGE_RATE = 0.25f

        /** The lanes scrolled by [rawOffset], kept within what [count] lanes of [trackPx] need in a view [viewHeight] tall. */
        fun of(rulerPx: Float, trackPx: Float, rawOffset: Float, viewHeight: Float, count: Int): LaneGeometry {
            val unscrolled = LaneGeometry(rulerPx, trackPx, 0f, viewHeight, count)
            return unscrolled.copy(offset = rawOffset.coerceIn(0f, unscrolled.maxOffset))
        }
    }
}
