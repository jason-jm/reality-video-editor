package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.geometry
import com.example.prismstudio.p40b43a88.domain.model.keyframeAt
import com.example.prismstudio.p40b43a88.domain.model.keyframesWithKeyAt
import com.example.prismstudio.p40b43a88.domain.model.withGeometry

/**
 * Dynamic keyframes of node 2's power window, as in Resolve: a keyframe holds the window's position,
 * size, aspect, angle and soft edge at a frame, and the window moves between keyframes — evenly, or
 * easing in and out — so it can follow a moving subject. Frames count from the clip's start, as the
 * transform's keyframes do; the shape, inversion, qualifier and grade do not animate.
 */
class WindowKeyframeUseCase {

    /**
     * Adds a keyframe holding the window as it is at [frame] (the motion stays the same), or removes the one there. [noun]
     * names it in the messages (a clip's mask is a window too).
     */
    fun toggle(window: PowerWindow, frame: Int, clipFrames: Int, noun: String = "窗口"): WindowEdit {
        if (window.shape == WindowShape.FULL) throw EditRejected("先选择${noun}形状，再添加关键帧")
        if (frame !in 0 until clipFrames) throw EditRejected("把播放头移到片段内，再添加${noun}关键帧")
        val existing = window.keyframeAt(frame)
        val updated = if (existing != null) {
            val rest = window.keyframes - existing
            // Without keyframes the window stays where it was here, instead of jumping back.
            if (rest.isEmpty()) window.withGeometry(existing.geometry).copy(keyframes = rest) else window.copy(keyframes = rest)
        } else {
            window.copy(keyframes = window.keyframesWithKeyAt(frame))
        }
        val count = updated.keyframes.size
        val message = when {
            existing == null -> "已添加${noun}关键帧 · 共 $count 个"
            count == 0 -> "已删除${noun}关键帧，${noun}不再移动"
            else -> "已删除${noun}关键帧 · 还剩 $count 个"
        }
        return WindowEdit(updated, message)
    }

    /**
     * Turns easing on or off where the window arrives at ([arriving]) or leaves the keyframe at
     * [frame]: eased, it slows to a stop there (or starts from rest) instead of moving evenly.
     */
    fun ease(window: PowerWindow, frame: Int, arriving: Boolean): WindowEdit {
        val key = window.keyframeAt(frame) ?: throw EditRejected("把播放头移到窗口关键帧上，再设置缓入缓出")
        requireNeighbour(window.keyframes.map { it.frame }, frame, arriving)
        val on = !key.ease.eases(arriving)
        val keyed = window.keyframes.map { if (it == key) it.copy(ease = it.ease.with(arriving, on)) else it }
        return WindowEdit(window.copy(keyframes = keyed), easeMessage(arriving, on))
    }

    /**
     * [window] with its geometry changed by [change] at [frame]: a window without keyframes changes
     * throughout; one with keyframes changes (or gains) the keyframe at [frame], keeping its easing.
     */
    fun adjust(window: PowerWindow, frame: Int, clipFrames: Int, noun: String = "窗口", change: (PowerWindow) -> PowerWindow): PowerWindow {
        if (window.keyframes.isEmpty()) return change(window)
        if (frame !in 0 until clipFrames) throw EditRejected("播放头不在片段内：移进片段再调整$noun")
        val changed = change(window.at(frame)).geometry
        return window.copy(keyframes = window.keyframesWithKeyAt(frame).map { if (it.frame == frame) it.copy(geometry = changed) else it })
    }

    /** The window keyframe before [frame], if any. */
    fun previous(window: PowerWindow, frame: Int): Int? = window.keyframes.lastOrNull { it.frame < frame }?.frame

    /** The window keyframe after [frame], if any. */
    fun next(window: PowerWindow, frame: Int): Int? = window.keyframes.firstOrNull { it.frame > frame }?.frame
}

/** A window after a keyframe edit, and what to tell the colourist. */
data class WindowEdit(val window: PowerWindow, val message: String)
