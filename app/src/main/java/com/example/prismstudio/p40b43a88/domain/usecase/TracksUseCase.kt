package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Adding and deleting timeline tracks, as Resolve's track header menu does. New video tracks go on
 * top of the stack (they cover the ones below), new audio tracks at the bottom. V1 and A1 are the base
 * tracks media is appended to and stay; any other track can be deleted together with its clips.
 */
class TracksUseCase {

    /** A changed project and what to tell the user. */
    data class Result(val doc: ProjectDocument, val message: String)

    fun add(doc: ProjectDocument, kind: TrackKind): Result {
        if (kind == TrackKind.SUBTITLE) return addSubtitle(doc, null)
        val timeline = doc.timeline
        val count = timeline.tracks.count { it.kind == kind }
        if (count >= maxTracks(kind)) throw EditRejected("最多 ${maxTracks(kind)} 条${kind.label}轨道")
        val track = timeline.nextTrack(kind)
        return Result(doc.copy(timeline = timeline.withTrack(track)), "已添加轨道 ${track.name}")
    }

    /**
     * A new subtitle track in [language] (the first not yet used when null), on top and shown — the others hidden, as only
     * one subtitle track shows (and is burned in) at a time.
     */
    fun addSubtitle(doc: ProjectDocument, language: SubtitleLanguage?): Result {
        val timeline = doc.timeline
        val subtitles = timeline.tracks.filter { it.kind == TrackKind.SUBTITLE }
        if (subtitles.size >= MAX_SUBTITLE_TRACKS) throw EditRejected("最多 $MAX_SUBTITLE_TRACKS 条字幕轨（每种语言一条）")
        val used = subtitles.map { it.subtitleLanguage }.toSet()
        val lang = language ?: SubtitleLanguage.entries.first { it !in used }
        if (lang in used) throw EditRejected("已有${lang.label}字幕轨")
        val track = timeline.nextTrack(TrackKind.SUBTITLE).copy(language = lang)
        val added = showOnly(timeline.withTrack(track), track.id)
        return Result(doc.copy(timeline = added), "已添加字幕轨 ${track.name}（${lang.label}），显示在画面上")
    }

    /** Subtitle track [trackId] in [language]; one track per language. */
    fun setLanguage(timeline: Timeline, trackId: String, language: SubtitleLanguage): Timeline {
        val track = timeline.track(trackId)?.takeIf { it.kind == TrackKind.SUBTITLE } ?: throw EditRejected("只有字幕轨有语言")
        if (track.locked) throw EditRejected("轨道已锁定")
        val other = timeline.tracks.firstOrNull { it.kind == TrackKind.SUBTITLE && it.id != trackId && it.subtitleLanguage == language }
        if (other != null) throw EditRejected("${other.name} 已经是${language.label}字幕")
        return timeline.copy(tracks = timeline.tracks.map { if (it.id == trackId) it.copy(language = language) else it })
    }

    /**
     * Track [trackId] shown or hidden. A subtitle track shown hides the other subtitle tracks: one language at a time is on
     * screen and burned into the render (Resolve).
     */
    fun setEnabled(timeline: Timeline, trackId: String, enabled: Boolean): Timeline {
        val track = timeline.track(trackId) ?: return timeline
        if (enabled && track.kind == TrackKind.SUBTITLE) return showOnly(timeline, trackId)
        return timeline.copy(tracks = timeline.tracks.map { if (it.id == trackId) it.copy(enabled = enabled) else it })
    }

    private fun showOnly(timeline: Timeline, trackId: String): Timeline = timeline.copy(
        tracks = timeline.tracks.map { if (it.kind == TrackKind.SUBTITLE) it.copy(enabled = it.id == trackId) else it },
    )

    fun remove(doc: ProjectDocument, trackId: String): Result {
        val timeline = doc.timeline
        val track = timeline.track(trackId) ?: throw EditRejected("轨道不存在")
        if (trackId == Timeline.V1 || trackId == Timeline.A1) throw EditRejected("${track.name} 是基础轨道，不能删除")
        if (track.locked) throw EditRejected("轨道已锁定")
        val removed = timeline.clipsOn(trackId).size
        val updated = doc.copy(
            timeline = timeline.copy(
                tracks = timeline.tracks - track,
                clips = timeline.clips.filter { it.trackId != trackId },
                // A destination on the deleted track goes back to its default (V1, automatic sound).
                targets = timeline.targets.let { t ->
                    t.copy(video = if (t.video == trackId) Timeline.V1 else t.video, audio = t.audio?.takeUnless { it == trackId })
                },
            ),
            // A channel left behind could still be soloed and silence every other track.
            mixer = doc.mixer.copy(channels = doc.mixer.channels.filter { it.trackId != trackId }),
        )
        val message = if (removed == 0) "已删除轨道 ${track.name}" else "已删除轨道 ${track.name} 及其中 $removed 个片段"
        return Result(updated, message)
    }

    companion object {
        /**
         * Video / audio tracks per kind. The lanes scroll once they no longer fit; the monitor stacks up to four picture
         * layers live (more show the top one, flagged approximate) and a render composites them all, a pass a layer.
         */
        const val MAX_TRACKS = 8

        /** Subtitle tracks: one per language (Resolve), this many. */
        const val MAX_SUBTITLE_TRACKS = 4

        fun maxTracks(kind: TrackKind): Int = if (kind == TrackKind.SUBTITLE) MAX_SUBTITLE_TRACKS else MAX_TRACKS

        fun canAdd(timeline: Timeline, kind: TrackKind): Boolean = timeline.tracks.count { it.kind == kind } < maxTracks(kind)

        fun canRemove(track: Track): Boolean = track.id != Timeline.V1 && track.id != Timeline.A1
    }
}

private val TrackKind.label: String
    get() = when (this) {
        TrackKind.VIDEO -> "视频"
        TrackKind.AUDIO -> "音频"
        TrackKind.SUBTITLE -> "字幕"
    }

/** The next free track of [kind]: V3 after V2, A3 after A2, ST1 (numbering past the highest in use). */
fun Timeline.nextTrack(kind: TrackKind): Track {
    val prefix = when (kind) {
        TrackKind.VIDEO -> "V"
        TrackKind.AUDIO -> "A"
        TrackKind.SUBTITLE -> "ST"
    }
    val numbers = tracks.filter { it.kind == kind }.mapNotNull { it.name.removePrefix(prefix).toIntOrNull() }
    var n = (numbers.maxOrNull() ?: 0) + 1
    while (tracks.any { it.id == "${prefix.lowercase()}$n" }) n++
    return Track("${prefix.lowercase()}$n", "$prefix$n", kind)
}

/**
 * This timeline with [track] added: a subtitle track above everything, a video track on top of the
 * video stack, an audio track below the audio ones.
 */
fun Timeline.withTrack(track: Track): Timeline = copy(
    tracks = when (track.kind) {
        TrackKind.SUBTITLE -> listOf(track) + tracks
        TrackKind.VIDEO -> tracks.indexOfFirst { it.kind != TrackKind.SUBTITLE }.let { at ->
            val i = if (at < 0) tracks.size else at
            tracks.subList(0, i) + track + tracks.subList(i, tracks.size)
        }
        TrackKind.AUDIO -> tracks + track
    },
)
