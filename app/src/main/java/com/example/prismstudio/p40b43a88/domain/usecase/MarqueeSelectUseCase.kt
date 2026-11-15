package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track

/**
 * Resolve's rubber-band selection: a box dragged over empty timeline from frame `from` to `until`
 * across a run of tracks takes in every clip it touches — on unlocked tracks only, as Resolve selects
 * nothing behind a lock. In 多选 mode the box gathers: what it touches joins the selection.
 */
class MarqueeSelectUseCase {

    /**
     * [current] followed by the clips the box touches, one per linked group (a group already selected is
     * not added again), in timeline order with a picture before its sound.
     */
    fun select(timeline: Timeline, from: Int, until: Int, trackIds: Set<String>, current: List<String>): List<String> {
        val groups = current.mapNotNull { timeline.clip(it) }.mapTo(mutableSetOf()) { it.linkId ?: it.id }
        return current + touched(timeline.clips, timeline.tracks, from, until, trackIds)
            .filter { groups.add(it.linkId ?: it.id) }
            .map { it.id }
    }

    companion object {
        /**
         * The clips a box from frame [from] to [until] over the tracks [trackIds] touches — each overlapping
         * it by at least part of a frame — on unlocked tracks, in timeline order: by start, a picture before
         * its sound, then from the top track down. The timeline draws these lit while the box is dragged.
         */
        fun touched(clips: List<Clip>, tracks: List<Track>, from: Int, until: Int, trackIds: Set<String>): List<Clip> {
            val order = tracks.filter { it.id in trackIds && !it.locked }.withIndex().associate { it.value.id to it.index }
            return clips.filter { it.trackId in order && it.start < until && it.end > from }
                .sortedWith(compareBy({ it.start }, { it.kind == ClipKind.AUDIO }, { order[it.trackId] }))
        }
    }
}
