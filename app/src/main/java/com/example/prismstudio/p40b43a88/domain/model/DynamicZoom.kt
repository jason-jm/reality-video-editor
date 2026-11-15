package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's Dynamic Zoom: the picture pushes in (or pulls out) smoothly from the clip's first frame to its last,
 * on top of its transform — the slow move that brings a photo to life. [amountPercent] is how much bigger the
 * picture is at the zoomed end (20 = 120 %). Resolve frames the move with a start and an end rectangle; here
 * [focusX] / [focusY] say where the zoomed one sits — the side or corner the push heads for — and a pan keeps the
 * picture enlarged while its frame travels across it, from the opposite side to that one.
 */
data class DynamicZoom(
    val direction: ZoomDirection = ZoomDirection.IN,
    val amountPercent: Int = DEFAULT_AMOUNT,
    val ease: ZoomEase = ZoomEase.LINEAR,
    val focusX: ZoomFocusX = ZoomFocusX.CENTER,
    val focusY: ZoomFocusY = ZoomFocusY.CENTER,
) {
    /**
     * The extra scale [frame] timeline frames into a clip [durationFrames] long: a push in starts at 1 on the
     * first frame and reaches 1 + amount on the last; a pull out runs the other way; a pan holds 1 + amount.
     * Held outside the clip.
     */
    fun scaleAt(frame: Float, durationFrames: Int): Float {
        val zoomed = when (direction) {
            ZoomDirection.IN -> along(frame, durationFrames)
            ZoomDirection.OUT -> 1f - along(frame, durationFrames)
            ZoomDirection.PAN -> 1f
        }
        return 1f + amountPercent / 100f * zoomed
    }

    /**
     * The point of the picture that holds still while it is scaled, in halves of its width from its centre (right
     * is positive): the focus for a push or a pull, sliding from the opposite side to it for a pan.
     */
    fun anchorXAt(frame: Float, durationFrames: Int): Float = anchor(focusX.sign, frame, durationFrames)

    /** As [anchorXAt], in halves of the picture's height (up is positive). */
    fun anchorYAt(frame: Float, durationFrames: Int): Float = anchor(focusY.sign, frame, durationFrames)

    /** Whether the frame moves across the picture at all: a pan needs a side to head for. */
    val moves: Boolean get() = direction != ZoomDirection.PAN || focusX != ZoomFocusX.CENTER || focusY != ZoomFocusY.CENTER

    private fun anchor(sign: Int, frame: Float, durationFrames: Int): Float =
        if (direction == ZoomDirection.PAN) sign * (2f * along(frame, durationFrames) - 1f) else sign.toFloat()

    /** Eased progress through the clip, held before and after it. */
    private fun along(frame: Float, durationFrames: Int): Float =
        ease.apply(if (durationFrames <= 1) 1f else (frame / (durationFrames - 1)).coerceIn(0f, 1f))

    companion object {
        const val DEFAULT_AMOUNT = 20
        const val MIN_AMOUNT = 5
        const val MAX_AMOUNT = 50
    }
}

enum class ZoomDirection(val label: String) {
    IN("推近"),
    OUT("拉远"),
    PAN("平移"),
}

/** Where along the picture's width the move heads (Resolve's end rectangle, left to right). */
enum class ZoomFocusX(val label: String, val sign: Int) {
    LEFT("左", -1),
    CENTER("中", 0),
    RIGHT("右", 1),
}

/** Where along the picture's height the move heads, top to bottom. */
enum class ZoomFocusY(val label: String, val sign: Int) {
    TOP("上", 1),
    CENTER("中", 0),
    BOTTOM("下", -1),
}

/** How the move starts and ends (Resolve's Dynamic Zoom Ease): evenly, from rest, coming to rest, or both. */
enum class ZoomEase(val label: String) {
    LINEAR("线性"),
    EASE_IN("缓入"),
    EASE_OUT("缓出"),
    EASE_IN_OUT("缓入缓出"),
    ;

    /** Progress along the move at time [t] (both 0…1). */
    fun apply(t: Float): Float = when (this) {
        LINEAR -> t
        EASE_IN -> t * t
        EASE_OUT -> 1f - (1f - t) * (1f - t)
        EASE_IN_OUT -> t * t * (3f - 2f * t)
    }
}
