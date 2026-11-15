package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * `timeline-menu`: Resolve's timelines in a project — open another, make a new one (empty, on the open one's raster),
 * duplicate the open one (to make, say, its vertical version) or delete it; `timeline-settings-sheet` names it. The
 * open timeline is the document's [ProjectDocument.timeline], [ProjectDocument.format] and [ProjectDocument.mixer]; the
 * rest wait in [ProjectDocument.timelines]. Each change is one undoable step.
 */
class TimelinesUseCase(private val ids: IdGenerator) {

    data class Result(val doc: ProjectDocument, val message: String)

    /** Opens [id], putting the open timeline away. */
    fun open(doc: ProjectDocument, id: String): Result {
        if (id == doc.timelineId) throw EditRejected("“${doc.timelineName}”已经打开")
        val all = all(doc)
        val index = all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw EditRejected("找不到这条时间线")
        return Result(opened(doc, all, index), "已切换到“${all[index].name}”")
    }

    /** A new, empty timeline on the open one's raster (its default tracks), opened. */
    fun create(doc: ProjectDocument): Result {
        val all = all(doc)
        val empty = Timeline.EMPTY
        val fresh = StoredTimeline(newId(all), nextName(all), doc.format, empty, MixerState.forTracks(empty.tracks))
        val list = all + fresh
        return Result(opened(doc, list, list.lastIndex), "已新建“${fresh.name}”")
    }

    /**
     * [timeline] as a new timeline named [name] (numbered when taken) on the open one's raster, opened — an imported
     * edit list, say.
     */
    fun add(doc: ProjectDocument, name: String, timeline: Timeline): Result {
        val all = all(doc)
        val taken = all.map { it.name }.toSet()
        val free = generateSequence(1) { it + 1 }.map { if (it == 1) name else "$name ($it)" }.first { it !in taken }
        val fresh = StoredTimeline(newId(all), free, doc.format, timeline, MixerState.forTracks(timeline.tracks))
        val list = all + fresh
        return Result(opened(doc, list, list.lastIndex), "已新建并打开“$free”")
    }

    /** A copy of the open timeline — its clips under new ids, its raster and mixer — beside it, opened. */
    fun duplicate(doc: ProjectDocument): Result {
        if (doc.inCompound) throw EditRejected("复合片段的时间线不能复制：回到上一级后复制复合片段")
        val all = all(doc)
        val at = doc.timelineIndex.coerceIn(0, all.lastIndex)
        val copy = StoredTimeline(newId(all), copyName(doc.timelineName, all), doc.format, renewed(doc.timeline), doc.mixer)
        val list = all.toMutableList().apply { add(at + 1, copy) }
        return Result(opened(doc, list, at + 1), "已复制为“${copy.name}”")
    }

    /** Deletes the open timeline and opens the one before it (the next when it was the first); the last one stays. */
    fun delete(doc: ProjectDocument): Result {
        if (doc.inCompound) throw EditRejected("复合片段的时间线不能在这里删除：回到上一级后删除复合片段")
        val all = all(doc)
        if (all.count { it.id !in doc.compoundIds } <= 1) throw EditRejected("项目至少要有一条时间线")
        // A compound clip's contents may hold a clip of another timeline (Resolve's nested timelines).
        if (all.any { t -> t.timeline.clips.any { it.compoundId == doc.timelineId } }) {
            throw EditRejected("“${doc.timelineName}”被复合片段使用，不能删除")
        }
        val at = doc.timelineIndex.coerceIn(0, all.lastIndex)
        val list = all.filterIndexed { i, _ -> i != at }
        // The one before it (the next when it was the first) that is not a compound clip's.
        val regular = list.indices.filter { list[it].id !in doc.compoundIds }
        val next = regular.lastOrNull { it < at } ?: regular.first()
        return Result(opened(doc, list, next), "已删除“${doc.timelineName}”，打开“${list[next].name}”")
    }

    /** Every timeline in order, the open one among them. */
    internal fun all(doc: ProjectDocument): List<StoredTimeline> = doc.timelines.toMutableList().apply {
        add(doc.timelineIndex.coerceIn(0, size), StoredTimeline(doc.timelineId, doc.timelineName, doc.format, doc.timeline, doc.mixer))
    }

    /**
     * [doc] with [list]'s timeline at [index] open and the rest put away; a compound clip's way back is forgotten (the
     * timeline menu opened it, not the clip).
     */
    internal fun opened(doc: ProjectDocument, list: List<StoredTimeline>, index: Int): ProjectDocument {
        val open = list[index]
        return doc.copy(
            compoundTrail = emptyList(),
            timelineId = open.id,
            timelineName = open.name,
            format = open.format,
            timeline = open.timeline,
            mixer = open.mixer,
            timelines = list.filterIndexed { i, _ -> i != index },
            timelineIndex = index,
        )
    }

    /** [timeline] with new clip, link and marker ids, so the copy and the original never share one. */
    private fun renewed(timeline: Timeline): Timeline {
        val links = mutableMapOf<String, String>()
        return timeline.copy(
            clips = timeline.clips.map { c ->
                c.copy(id = ids.next(c.id.substringBefore('-').ifEmpty { "c" }), linkId = c.linkId?.let { links.getOrPut(it) { ids.next("l") } })
            },
            markers = timeline.markers.map { it.copy(id = ids.next("m")) },
        )
    }

    /** An id no timeline of the project has (the first one's is fixed). */
    private fun newId(all: List<StoredTimeline>): String = generateSequence { ids.next("tl") }.first { id -> all.none { it.id == id } }

    /** "时间线 N", one past the highest such number (and free). */
    private fun nextName(all: List<StoredTimeline>): String {
        val highest = all.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val taken = all.map { it.name }.toSet()
        return generateSequence(highest + 1) { it + 1 }.map { "时间线 $it" }.first { it !in taken }
    }

    /** "<name> 副本", numbered when taken. */
    private fun copyName(name: String, all: List<StoredTimeline>): String {
        val taken = all.map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) "$name 副本" else "$name 副本 $it" }.first { it !in taken }
    }

    private companion object {
        val NUMBERED = Regex("时间线 (\\d+)")
    }
}
