package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Resolve's Swap Clips (Ctrl+Shift+, / .): the selected clip changes places with the next clip before or after it on its
 * track. The two keep their lengths and the gap between them, so nothing else on the track moves; each takes its linked
 * sound or picture along, as long as that lands nowhere already taken.
 */
class SwapClipsUseCase {

    operator fun invoke(timeline: Timeline, clipIds: List<String>, right: Boolean): EditResult {
        val groups = timeline.selectedGroups(clipIds)
        if (groups.isEmpty()) throw EditRejected("请先在时间线上选择片段")
        if (groups.size > 1) throw EditRejected("一次只能交换一个片段")
        val clip = groups.first().first()
        val group = groups.first()
        val side = if (right) "右侧" else "左侧"
        val others = timeline.clipsOn(clip.trackId).filter { it.id !in group.map { g -> g.id } }
        val neighbour = if (right) others.filter { it.start >= clip.end }.minByOrNull { it.start }
        else others.filter { it.end <= clip.start }.maxByOrNull { it.end }
        neighbour ?: throw EditRejected("${side}没有可以交换的片段")
        val partners = timeline.linkedGroup(neighbour)
        timeline.requireUnlocked(group, clip)
        timeline.requireUnlocked(partners, neighbour)
        // Lengths and the gap between them stay: the pair just changes order.
        val (clipShift, neighbourShift) = if (right) (neighbour.end - clip.end) to (clip.start - neighbour.start)
        else (neighbour.start - clip.start) to (clip.end - neighbour.end)
        val shifts = group.associate { it.id to clipShift } + partners.associate { it.id to neighbourShift }
        val swapped = timeline.copy(clips = timeline.clips.map { c -> shifts[c.id]?.let { c.copy(start = c.start + it) } ?: c })
        val moved = swapped.clips.filter { it.id in shifts }
        val clash = moved.any { m -> m.start < 0 || swapped.clipsOn(m.trackId).any { o -> o.id != m.id && o.start < m.end && o.end > m.start } }
        if (clash) throw EditRejected("交换后链接的片段会和其他片段重叠")
        return EditResult(swapped, clip.id, "已将“${clip.name}”与${side}的“${neighbour.name}”交换")
    }
}
