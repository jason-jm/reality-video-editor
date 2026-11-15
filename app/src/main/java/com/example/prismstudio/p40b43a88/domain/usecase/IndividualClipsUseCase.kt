package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks

/**
 * Resolve's Deliver › Render › Individual clips: one file per clip instead of one of the timeline. Each file holds the clip
 * alone — graded, transformed, with its effects, its fades and its linked sound — from its first frame to its last; what
 * plays over or under it (titles, other tracks, the transition into it) stays out.
 */
class IndividualClipsUseCase {

    /**
     * The clips [settings] gives a file each on [doc]'s open timeline: its pictures (its sound clips for an audio-only
     * render) on tracks that are on, the ones switched off left out, in timeline order (the upper track first at one moment); with In to Out, those that reach
     * into the marked range (each still whole).
     */
    fun clips(doc: ProjectDocument, settings: RenderSettings): List<Clip> {
        val timeline = doc.timeline
        val kind = if (settings.includeVideo) ClipKind.VIDEO else ClipKind.AUDIO
        val marked = if (settings.range == RenderRange.IN_TO_OUT) timeline.marks.range(doc.durationFrames) else null
        val order = timeline.tracks.map { it.id }
        return timeline.clips
            .filter { it.kind == kind && it.enabled && timeline.track(it.trackId)?.enabled != false }
            .filter { marked == null || (it.start <= marked.last && it.end > marked.first) }
            .sortedWith(compareBy<Clip> { it.start }.thenBy { order.indexOf(it.trackId) })
    }

    /**
     * [doc] with only [clipId] and the clips linked to it (a picture's sound), moved to start at the timeline's start, no
     * transition into them and no marks or markers. A deleted or disabled target cannot be exported as an individual clip.
     */
    fun solo(doc: ProjectDocument, clipId: String): ProjectDocument? {
        val timeline = doc.timeline
        val clip = timeline.clip(clipId) ?: return null
        fun active(member: Clip) = member.enabled && timeline.track(member.trackId)?.enabled != false
        if (!active(clip)) return null
        val group = timeline.linkedGroup(clip).filter(::active)
        val shift = group.minOf { it.start }
        val clips = group.map { it.copy(start = it.start - shift, transitionIn = null) }
        return doc.copy(timeline = timeline.copy(clips = clips, markers = emptyList(), marks = TimelineMarks()))
    }
}
