package com.example.prismstudio.p40b43a88.ui.components

import com.example.prismstudio.p40b43a88.domain.model.KeyEase

/** Keyframes of one animated property: how many, and where the playhead stands among them. */
data class KeyframeUi(
    val count: Int = 0,
    val playheadInside: Boolean = false,
    val atKeyframe: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val hint: String = "",
    /** The keyframe at the playhead eases in / out. */
    val easeIn: Boolean = false,
    val easeOut: Boolean = false,
    /** At a keyframe with motion arriving (a keyframe before it) / leaving (one after it). */
    val canEaseIn: Boolean = false,
    val canEaseOut: Boolean = false,
) {
    /** Animated, but the playhead is outside the clip: the values cannot be adjusted. */
    val blocked: Boolean get() = count > 0 && !playheadInside
}

/** Keyframe controls for keyframes at [frames]; [key] is the easing of the one at the playhead ([local]), if any. */
fun keyframeUi(frames: List<Int>, key: KeyEase?, local: Int, inside: Boolean, hint: String) = KeyframeUi(
    count = frames.size,
    playheadInside = inside,
    atKeyframe = key != null,
    hasPrevious = frames.any { it < local },
    hasNext = frames.any { it > local },
    hint = hint,
    easeIn = key?.easesIn == true,
    easeOut = key?.easesOut == true,
    canEaseIn = key != null && frames.any { it < local },
    canEaseOut = key != null && frames.any { it > local },
)

/** What the keyframe controls ask for: jump to the previous / next keyframe, add or remove one, ease in / out. */
enum class KeyframeAction { PREVIOUS, TOGGLE, NEXT, EASE_IN, EASE_OUT }
