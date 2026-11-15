package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Resolve's media pool lists the project's timelines beside its media: the `时间线` bin shows each as a card — its name,
 * raster, length and clip count, which one is open, and a miniature of its tracks ([TimelineCard.bars]); a tap opens it.
 * Compound clips' timelines are among them, badged.
 */
class TimelinePoolUseCase {

    /** One clip in a card's miniature: its row (top track first), where it lies (0…1 of the timeline) and its kind. */
    data class Bar(val row: Int, val from: Float, val until: Float, val kind: ClipKind)

    data class TimelineCard(
        val id: String,
        val name: String,
        val open: Boolean,
        val format: ProjectFormat,
        val durationFrames: Int,
        val clips: Int,
        val rows: Int,
        val bars: List<Bar>,
        /** A compound clip's timeline (Resolve lists compound clips in the pool). */
        val compound: Boolean = false,
    )

    /** Every timeline in the project's order, those whose name holds [query]. */
    operator fun invoke(doc: ProjectDocument, query: String = ""): List<TimelineCard> {
        val q = query.trim()
        val stored = doc.timelines.associateBy { it.id }
        return doc.timelineEntries.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }.map { e ->
            val timeline = if (e.open) doc.timeline else stored[e.id]?.timeline ?: Timeline.EMPTY
            card(e.id, e.name, e.open, e.format, timeline).copy(compound = e.compound)
        }
    }

    /** The bin's summary line: timelines and compound clips counted apart. */
    fun summary(doc: ProjectDocument): String {
        val compounds = doc.timelineEntries.count { it.compound }
        val regular = doc.timelineEntries.size - compounds
        val also = if (compounds > 0) " · $compounds 个复合片段" else ""
        return "$regular 条时间线$also · 当前打开“${doc.timelineName}”"
    }

    private fun card(id: String, name: String, open: Boolean, format: ProjectFormat, timeline: Timeline): TimelineCard {
        // Rows as the Edit page stacks them, subtitle tracks left out (their cues are too short to read at this size).
        val tracks = timeline.tracks.filter { it.kind != TrackKind.SUBTITLE }
        val length = timeline.durationFrames.coerceAtLeast(1).toFloat()
        val bars = timeline.clips.mapNotNull { c ->
            val row = tracks.indexOfFirst { it.id == c.trackId }.takeIf { it >= 0 } ?: return@mapNotNull null
            Bar(row, c.start / length, c.end / length, c.kind)
        }
        return TimelineCard(id, name, open, format, timeline.durationFrames, timeline.clips.size, tracks.size, bars)
    }
}
