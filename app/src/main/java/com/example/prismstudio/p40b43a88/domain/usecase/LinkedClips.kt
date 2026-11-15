package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/** The clip together with its linked sound or picture; structural edits move them as one. */
internal fun Timeline.linkedGroup(clip: Clip): List<Clip> =
    if (clip.linkId == null) listOf(clip) else clips.filter { it.linkId == clip.linkId }

/**
 * Locks win: an edit is refused when the clip's track, or the track of any linked partner it would
 * carry along, is locked — so picture and sound never drift apart behind a lock.
 */
internal fun Timeline.requireUnlocked(group: List<Clip>, primary: Clip) {
    if (track(primary.trackId)?.locked == true) throw EditRejected("轨道已锁定")
    if (group.any { track(it.trackId)?.locked == true }) throw EditRejected("链接的片段在已锁定的轨道上")
}

/** Refuses adding clips to a locked track. */
internal fun Timeline.requireTrackUnlocked(trackId: String) {
    if (track(trackId)?.locked == true) throw EditRejected("目标轨道已锁定")
}

/**
 * The selected clips [clipIds] as groups (each with its linked partner, the selected clip first), each
 * group once and in selection order; ids no longer on the timeline are left out.
 */
internal fun Timeline.selectedGroups(clipIds: List<String>): List<List<Clip>> {
    val seen = mutableSetOf<String>()
    return clipIds.mapNotNull { id ->
        val clip = clip(id)
        if (clip == null || clip.id in seen) return@mapNotNull null
        val group = listOf(clip) + (linkedGroup(clip) - clip)
        seen += group.map { it.id }
        group
    }
}

/** Refuses when any of [groups] would touch a locked track. */
internal fun Timeline.requireUnlocked(groups: List<List<Clip>>) = groups.forEach { requireUnlocked(it, it.first()) }
