package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import android.view.Surface
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null,
    /** The monitor shows a flattened approximation; overlays and transitions are composited on export. */
    val approximate: Boolean = false,
    /** Picture layers the monitor stacks (one view each, bottom first). */
    val layers: Int = 1,
    /** Per view (bottom first): where its picture composites in a mode other than normal (Resolve's Composite Mode). */
    val layerComposites: List<List<CompositeSpan>> = emptyList(),
) {
    /** Each view's composite mode at [positionMs]: normal where no span applies, and always for the bottom view. */
    fun compositesAt(positionMs: Long = this.positionMs): List<CompositeMode> = List(layers) { view ->
        layerComposites.getOrNull(view)?.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.mode ?: CompositeMode.NORMAL
    }
}

/** A stretch of a monitor layer (ms of the program) whose picture composites in [mode]. */
data class CompositeSpan(val startMs: Long, val endMs: Long, val mode: CompositeMode)

/**
 * Each monitor view's stretches in a composite mode other than normal (Resolve's Composite Mode), bottom view first —
 * the plan lists its layers top first. The bottom view never blends, and a flattened preview shows none.
 */
internal fun RenderPlan.layerComposites(views: Int, flattened: Boolean): List<List<CompositeSpan>> = List(views) { view ->
    val segments = if (view == 0 || flattened) emptyList() else layers.getOrNull(views - 1 - view)?.segments.orEmpty()
    segments.filter { it.showsPicture && it.composite != CompositeMode.NORMAL }
        .map { CompositeSpan(it.startFrame * 1000L / fps, it.endFrame * 1000L / fps, it.composite) }
}

/** Common transport of a monitor. */
interface MonitorPlayback {
    val state: StateFlow<PlaybackState>

    /** Routes the picture of [layer] (0 = bottom view) into [surface] ([width]×[height] px). */
    fun attachSurface(surface: Surface, width: Int, height: Int, layer: Int = 0)

    /** Stops rendering [layer] into [surface] if it is the one attached (a view was destroyed). */
    fun detachSurface(surface: Surface, layer: Int = 0)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

/** The program monitor: plays the edited timeline with grades, effects, titles and the mix. */
interface ProgramPlaybackRepository : MonitorPlayback {
    /** Loads (or live-updates) the timeline of [document]; null clears the monitor. */
    fun setProject(document: ProjectDocument?)

    /** Shows the left part of [clipId] ungraded, split at [split] (0..1); null turns compare off. */
    fun setCompare(clipId: String?, split: Float = 0.5f)

    /** Shows [clipId]'s window node as a matte (selection in colour, the rest grey); null shows the grade. */
    fun setHighlight(clipId: String?)

    /** Shows [clipId]'s keyer as its matte on the monitor (the Edit page's matte view); null = the picture. */
    fun setKeyMatte(clipId: String?) = Unit

    /** Grades again with what the LUT library holds now (a LUT a grade names was just imported). */
    fun refreshLooks() {}

    /** Silences the monitor's sound without touching the mix (a narration recorded over the program). */
    fun setMonitorMuted(muted: Boolean) {}
}

/** The source monitor: plays one media-pool clip as recorded. */
interface SourcePlaybackRepository : MonitorPlayback {
    /** Plays [asset] in the source monitor; HDR by the input colour override is tone mapped by [color], as in the timeline. */
    fun load(asset: MediaAsset?, color: ColorManagement = ColorManagement.DEFAULT)
}
