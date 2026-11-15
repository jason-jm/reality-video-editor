package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType

/** Which cuts Resolve's Add Transition reaches: picture and sound (Ctrl+T), picture only (Alt+T) or sound only (Shift+T). */
enum class TransitionScope(val label: String, val picture: Boolean, val sound: Boolean) {
    BOTH("转场", true, true),
    VIDEO("视频转场", true, false),
    AUDIO("音频交叉淡化", false, true),
}

/**
 * Resolve's Add Transition (Ctrl+T): the standard transition — a cross dissolve on picture, a +3 dB cross fade on sound,
 * half a second (or half the shorter clip) centred on the cut — at every cut it reaches. A cut is a clip that starts
 * where another clip on its track ends. With clips selected it reaches the cuts at their ends; with none, the cut nearest
 * the playhead, on every track that cuts there. A cut that already has one keeps it; locked tracks are left alone.
 */
class AddTransitionsUseCase {

    /** The cut frames [clipIds]' ends make on their own tracks (a selected clip's out point is the next clip's cut). */
    fun selectionCuts(timeline: Timeline, clipIds: Collection<String>, scope: TransitionScope): Set<Int> =
        clipIds.mapNotNull(timeline::clip).filter { inScope(timeline, it, scope) }.flatMap { clip ->
            listOfNotNull(clip.start.takeIf { previous(timeline, clip) != null }, clip.end.takeIf { next(timeline, clip) != null })
        }.toSet()

    /** The cut nearest [frame] on the tracks [scope] reaches (the earlier one on a tie), or null when there is none. */
    fun nearestCut(timeline: Timeline, frame: Int, scope: TransitionScope): Int? =
        timeline.clips.filter { inScope(timeline, it, scope) && previous(timeline, it) != null }
            .map { it.start }.distinct().minWithOrNull(compareBy<Int>({ kotlin.math.abs(it - frame) }, { it }))

    /**
     * The standard transition at each of [cuts] on the tracks [scope] reaches, as one edit. Only clips in [within] when it
     * is given (a selection's own cuts: the clip itself, or the one after it).
     */
    fun add(timeline: Timeline, cuts: Set<Int>, scope: TransitionScope, fps: Int, within: Collection<String>? = null): EditResult {
        if (cuts.isEmpty()) throw EditRejected(if (within != null) "所选片段的两端没有紧挨着的片段，无法添加转场" else "时间线上没有剪辑点，无法添加转场")
        val reach = within?.mapNotNull(timeline::clip)?.flatMap { listOfNotNull(it, next(timeline, it)) }?.map { it.id }?.toSet()
        val incoming = timeline.clips.filter { clip ->
            clip.start in cuts && inScope(timeline, clip, scope) && previous(timeline, clip) != null && (reach == null || clip.id in reach)
        }
        val unlocked = incoming.filter { timeline.track(it.trackId)?.locked != true }
        if (unlocked.isEmpty() && incoming.isNotEmpty()) throw EditRejected("剪辑点所在的轨道已锁定")
        val fresh = unlocked.filter { if (it.kind == ClipKind.AUDIO) it.crossFadeFrames <= 0 else it.transitionIn == null }
        if (fresh.isEmpty()) throw EditRejected("这里的剪辑点已经有转场")
        var updated = timeline
        for (clip in fresh) {
            val before = previous(timeline, clip) ?: continue
            val frames = minOf(fps.coerceAtLeast(1) / 2, clip.durationFrames / 2, before.durationFrames / 2).coerceAtLeast(1)
            updated = updated.replaceClip(
                if (clip.kind == ClipKind.AUDIO) clip.copy(crossFadeFrames = frames)
                else clip.copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, frames, alignment = TransitionAlignment.CENTER)),
            )
        }
        val pictures = fresh.count { it.kind != ClipKind.AUDIO }
        val sounds = fresh.size - pictures
        val what = listOfNotNull(pictures.takeIf { it > 0 }?.let { "$it 个交叉叠化" }, sounds.takeIf { it > 0 }?.let { "$it 个交叉淡化" }).joinToString("、")
        val where = if (cuts.size == 1) "在 ${Timecode.format(cuts.first(), fps)} " else ""
        // A selection stays as it was; without one nothing gets selected, so the next Ctrl+T again works at the playhead.
        return EditResult(updated, within?.firstOrNull(), "已${where}添加 $what", selection = within?.toList())
    }

    private fun inScope(timeline: Timeline, clip: Clip, scope: TransitionScope): Boolean = when (timeline.track(clip.trackId)?.kind) {
        TrackKind.VIDEO -> scope.picture && clip.kind != ClipKind.AUDIO
        TrackKind.AUDIO -> scope.sound && clip.kind == ClipKind.AUDIO
        else -> false
    }

    /** The clip on [clip]'s track that ends where it starts. */
    private fun previous(timeline: Timeline, clip: Clip): Clip? =
        timeline.clipsOn(clip.trackId).firstOrNull { it.id != clip.id && it.end == clip.start }

    /** The clip on [clip]'s track that starts where it ends. */
    private fun next(timeline: Timeline, clip: Clip): Clip? =
        timeline.clipsOn(clip.trackId).firstOrNull { it.id != clip.id && it.start == clip.end }
}
