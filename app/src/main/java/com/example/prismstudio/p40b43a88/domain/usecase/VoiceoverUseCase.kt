package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/** A finished microphone recording: where it was written and how long it runs. */
data class VoiceTake(val uri: String, val durationMs: Long, val sizeBytes: Long)

/**
 * `voiceover-sheet`: Resolve's Fairlight recording — a narration recorded against the playing timeline lands where the
 * recording started, on the chosen audio track (or a new one below the others), over whatever that track had there.
 * The take becomes media of the project ("旁白 N"), so it can be used again; the placing is one undoable step.
 */
class VoiceoverUseCase(private val ids: IdGenerator) {

    data class Result(val doc: ProjectDocument, val clipId: String?, val message: String)

    /** A finished file always enters project media, even if its original timeline or track now rejects the edit. */
    fun save(doc: ProjectDocument, take: VoiceTake, timelineId: String, at: Int, startFps: Int, trackId: String?): Result {
        val owner = doc.viewing(timelineId) ?: return keepInMedia(doc, take, "找不到这条时间线")
        val frame = (at.toLong() * owner.format.fps / startFps).toInt()
        val placed = try {
            place(owner, take, frame, trackId)
        } catch (e: EditRejected) {
            return keepInMedia(doc, take, e.message ?: "无法放上时间线")
        }
        if (timelineId == doc.timelineId) return placed
        val updated = doc.copy(
            media = placed.doc.media,
            timelines = doc.timelines.map { if (it.id == timelineId) it.copy(timeline = placed.doc.timeline) else it },
        )
        return Result(updated, null, placed.message)
    }

    private fun keepInMedia(doc: ProjectDocument, take: VoiceTake, reason: String): Result {
        val asset = asset(doc, take)
        return Result(doc.copy(media = doc.media + asset), null, "旁白已保存到素材库：$reason")
    }

    /** [take] placed at frame [at] on [trackId], or on a new audio track when it is null. */
    fun place(doc: ProjectDocument, take: VoiceTake, at: Int, trackId: String?): Result {
        val fps = doc.format.fps
        val frames = Timecode.msToFrames(take.durationMs, fps)
        if (frames <= 0) throw EditRejected("录音太短，没有可以放上时间线的声音")
        var timeline = doc.timeline
        val track = if (trackId == null) {
            if (!TracksUseCase.canAdd(timeline, TrackKind.AUDIO)) throw EditRejected("音频轨已满：请选一条已有的音频轨")
            timeline.nextTrack(TrackKind.AUDIO).also { timeline = timeline.withTrack(it) }
        } else {
            timeline.track(trackId)?.takeIf { it.kind == TrackKind.AUDIO } ?: throw EditRejected("找不到这条音频轨")
        }
        timeline.requireTrackUnlocked(track.id)
        val asset = asset(doc, take)
        val name = asset.name.removeSuffix(".wav")
        val start = at.coerceAtLeast(0)
        val (carved, touched) = timeline.overwritten(track.id, start, start + frames, fps, ids, mutableMapOf())
        val clip = Clip(
            id = ids.next("c"),
            trackId = track.id,
            kind = ClipKind.AUDIO,
            name = name,
            start = start,
            durationFrames = frames,
            assetId = asset.id,
        )
        val message = buildString {
            append("已录制“$name”（${Timecode.format(frames, fps)}），放在 ${track.name} 的 ${Timecode.format(start, fps)}")
            if (touched > 0) append("，覆盖了原有声音")
        }
        return Result(doc.copy(media = doc.media + asset, timeline = carved.copy(clips = carved.clips + clip)), clip.id, message)
    }

    private fun asset(doc: ProjectDocument, take: VoiceTake): MediaAsset =
        MediaAsset(
            id = ids.next("a"),
            uri = take.uri,
            name = "旁白 ${nextNumber(doc)}.wav",
            kind = MediaKind.AUDIO,
            durationMs = take.durationMs,
            width = 0,
            height = 0,
            frameRate = 0f,
            videoCodec = "—",
            audioDescription = AUDIO_DESCRIPTION,
            hasAudio = true,
            sizeBytes = take.sizeBytes,
            relativePath = RELATIVE_PATH,
        )

    /** One past the highest "旁白 N" among the project's media. */
    private fun nextNumber(doc: ProjectDocument): Int =
        (doc.media.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1

    companion object {
        /** What the recorder writes (see the recorder repository): uncompressed, so every sample is where it was heard. */
        const val AUDIO_DESCRIPTION = "PCM 48 kHz 16 位单声道"

        /** Where takes live inside the app's files. */
        const val RELATIVE_PATH = "recordings/"

        /** Takes before rev 88 were AAC (.m4a). */
        private val NUMBERED = Regex("旁白 (\\d+)\\.(?:wav|m4a)")
    }
}
