package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Resolve's destination controls: the tracks the source viewer's edits (insert, overwrite, replace,
 * place on top, append) put the picture and the sound on. The picture target is a video track (V1 until
 * another is chosen); the sound target an audio track, or automatic.
 */
class EditTargetsUseCase {

    /** A changed timeline and what to tell the user. */
    data class Result(val timeline: Timeline, val message: String)

    /** Pictures go to video track [trackId]. */
    fun video(timeline: Timeline, trackId: String): Result {
        val track = timeline.track(trackId)?.takeIf { it.kind == TrackKind.VIDEO } ?: throw EditRejected("只能选择视频轨道作为画面目标")
        return Result(timeline.copy(targets = timeline.targets.copy(video = track.id)), "画面将落在 ${track.name}")
    }

    /** Sound goes to audio track [trackId]; null: automatically — a video's own sound on A1, sound-only media on the music track. */
    fun audio(timeline: Timeline, trackId: String?): Result {
        if (trackId == null) return Result(timeline.copy(targets = timeline.targets.copy(audio = null)), AUTOMATIC)
        val track = timeline.track(trackId)?.takeIf { it.kind == TrackKind.AUDIO } ?: throw EditRejected("只能选择音频轨道作为声音目标")
        return Result(timeline.copy(targets = timeline.targets.copy(audio = track.id)), "声音将落在 ${track.name}")
    }

    /**
     * The track header's destination control: a video track becomes the picture target; an audio track
     * the sound target or, when it already is, hands the sound back to automatic.
     */
    fun toggle(timeline: Timeline, trackId: String): Result {
        val track = timeline.track(trackId) ?: throw EditRejected("轨道不存在")
        return when (track.kind) {
            TrackKind.VIDEO -> video(timeline, track.id)
            TrackKind.AUDIO -> audio(timeline, track.id.takeUnless { timeline.audioTarget()?.id == it })
            TrackKind.SUBTITLE -> throw EditRejected("字幕轨道不能作为编辑目标")
        }
    }

    companion object {
        const val AUTOMATIC = "声音目标改为自动：视频的原声到 A1，纯音频到音乐轨 A2"
    }
}

/** The track sound-only media goes to: the sound target, else the music track — A2, another audio track than A1, or a new one. */
internal fun Timeline.soundTarget(): Track = audioTarget()
    ?: track(Timeline.A2)
    ?: tracks.firstOrNull { it.kind == TrackKind.AUDIO && it.id != Timeline.A1 }
    ?: nextTrack(TrackKind.AUDIO)

/** The track a video's own sound goes to: the sound target, else A1. */
internal fun Timeline.syncSoundTarget(): Track? = audioTarget() ?: track(Timeline.A1)
