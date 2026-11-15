package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Appends the marked range of a media-pool item at the end of its destination tracks
 * ([com.example.prismstudio.p40b43a88.domain.model.EditTargets]): video on the picture target, V1 (with
 * its sound linked on the sound target, A1 while automatic), stills there for five seconds, sound-only
 * media on the sound target — automatically the music track A2 (another lower audio track when A2 was
 * deleted, or a new one when A1 is the only one).
 */
class AppendMediaUseCase(private val ids: IdGenerator) {

    operator fun invoke(timeline: Timeline, asset: MediaAsset, inMs: Long, outMs: Long, fps: Int): EditResult {
        if (asset.offline) throw EditRejected("素材离线，无法添加：${asset.name}")
        if (asset.kind == MediaKind.AUDIO) return appendSound(timeline, asset, inMs, outMs, fps)
        val name = asset.name.substringBeforeLast('.')
        val clips = mutableListOf<Clip>()
        val selected: Clip
        when (asset.kind) {
            MediaKind.VIDEO -> {
                val picture = pictureTrack(timeline)
                val sound = if (asset.hasAudio) timeline.syncSoundTarget() else null
                timeline.requireTrackUnlocked(picture)
                sound?.let { timeline.requireTrackUnlocked(it.id) }
                val length = Timecode.msToFrames((outMs - inMs).coerceAtLeast(1), fps).coerceAtLeast(1)
                val start = maxOf(end(timeline, picture), sound?.let { end(timeline, it.id) } ?: 0)
                val link = if (sound != null) ids.next("l") else null
                selected = Clip(
                    id = ids.next("c"), trackId = picture, kind = ClipKind.VIDEO, name = name,
                    start = start, durationFrames = length, assetId = asset.id, sourceInMs = inMs, linkId = link,
                )
                clips += selected
                if (sound != null) {
                    clips += selected.copy(id = ids.next("c"), trackId = sound.id, kind = ClipKind.AUDIO)
                }
            }
            MediaKind.IMAGE -> {
                val picture = pictureTrack(timeline)
                timeline.requireTrackUnlocked(picture)
                selected = Clip(
                    id = ids.next("c"), trackId = picture, kind = ClipKind.VIDEO, name = name,
                    start = end(timeline, picture),
                    durationFrames = Timecode.msToFrames(MediaAsset.IMAGE_DEFAULT_DURATION_MS, fps),
                    assetId = asset.id,
                )
                clips += selected
            }
            MediaKind.AUDIO -> error("handled by appendSound")
        }
        return EditResult(
            timeline = timeline.copy(clips = timeline.clips + clips),
            selectedClipId = selected.id,
            message = "已添加到时间线：$name（${Timecode.format(selected.durationFrames, fps)}）",
        )
    }

    /** The picture target's id (V1 unless another video track was chosen). */
    private fun pictureTrack(timeline: Timeline): String = timeline.videoTarget()?.id ?: Timeline.V1

    /** Sound-only media goes to the sound target: the music track, A2, while automatic. */
    private fun appendSound(timeline: Timeline, asset: MediaAsset, inMs: Long, outMs: Long, fps: Int): EditResult {
        val track = timeline.soundTarget()
        val created = timeline.track(track.id) == null
        val base = if (created) timeline.withTrack(track) else timeline
        base.requireTrackUnlocked(track.id)
        val name = asset.name.substringBeforeLast('.')
        val length = Timecode.msToFrames((outMs - inMs).coerceAtLeast(1), fps).coerceAtLeast(1)
        val clip = Clip(
            id = ids.next("c"), trackId = track.id, kind = ClipKind.AUDIO, name = name,
            start = end(base, track.id), durationFrames = length, assetId = asset.id, sourceInMs = inMs,
        )
        return EditResult(
            timeline = base.copy(clips = base.clips + clip),
            selectedClipId = clip.id,
            message = "已添加到时间线：$name（${Timecode.format(length, fps)}）" + if (created) "（新建轨道 ${track.name}）" else "",
        )
    }

    private fun end(timeline: Timeline, trackId: String): Int =
        timeline.clipsOn(trackId).maxOfOrNull { it.end } ?: 0
}
