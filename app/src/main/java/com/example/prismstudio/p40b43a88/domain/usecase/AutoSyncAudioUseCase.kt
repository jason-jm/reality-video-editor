package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SyncedAudio
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Resolve's Auto Sync Audio (Based on Waveform), on the Media page: a video's own sound — a camera's scratch track — is
 * matched against a separate recorder's file, and from then on the video brings the recorder's sound, lined up, whenever
 * it is edited into the timeline (clips already there keep theirs). The match is [WaveformAlignUseCase]'s.
 */
class AutoSyncAudioUseCase(private val align: WaveformAlignUseCase = WaveformAlignUseCase()) {

    /** The pool's files whose sound [video] can take: online sound files. */
    fun candidates(video: MediaAsset, media: List<MediaAsset>): List<MediaAsset> =
        if (video.kind != MediaKind.VIDEO) emptyList() else media.filter { it.kind == MediaKind.AUDIO && !it.offline && it.id != video.id }

    /** Why [video] cannot be synced, or null. */
    fun unavailable(video: MediaAsset?): String? = when {
        video == null || video.kind != MediaKind.VIDEO -> "只有视频素材可以同步音频"
        !video.hasAudio -> "这个视频没有自带声音，无法按波形同步"
        video.offline -> "素材离线，无法同步"
        else -> null
    }

    /** [audio]'s offset against [video] from their envelopes (audio time − video time), or null when they share no sound. */
    fun offset(video: MediaAsset, videoSound: SoundEnvelope, audioSound: SoundEnvelope): Long? =
        align.match(videoSound, 0, video.durationMs, audioSound)?.let { (-it.lagMs).roundToLong() }

    /** [doc] with [videoId]'s sound taken from [synced] (null: its own again). */
    fun apply(doc: ProjectDocument, videoId: String, synced: SyncedAudio?): ProjectDocument {
        if (doc.asset(videoId) == null) throw EditRejected("素材已不在媒体池中")
        return doc.copy(media = doc.media.map { if (it.id == videoId) it.copy(syncedAudio = synced) else it })
    }

    /** The source column's line for [video]: what its sound is synced to, and how the two files start. */
    fun label(video: MediaAsset, media: List<MediaAsset>): String {
        val sync = video.syncedAudio ?: return "未同步：使用视频自带的声音"
        val audio = media.firstOrNull { it.id == sync.assetId } ?: return "同步的音频已不在媒体池中"
        val seconds = "%.3f".format(abs(sync.offsetMs) / 1000.0)
        return when {
            sync.offsetMs > 0 -> "已同步「${audio.name}」：录音比画面早开始 $seconds 秒"
            sync.offsetMs < 0 -> "已同步「${audio.name}」：录音比画面晚开始 $seconds 秒"
            else -> "已同步「${audio.name}」：两者同时开始"
        }
    }

    /**
     * [after] (the timeline once an edit placed media) with every sound clip it newly made from a synced video — one not
     * in [before] — taking the synced file instead, lined up: in point moved by the offset, start and end pulled in where
     * the recorder's file does not cover the picture (a sound clip it would not reach at all is dropped).
     */
    fun resynced(before: Timeline, after: Timeline, media: List<MediaAsset>, fps: Int): Timeline {
        val old = before.clips.map { it.id }.toSet()
        val assets = media.associateBy { it.id }
        val frameMs = 1000.0 / fps
        val clips = after.clips.mapNotNull { clip ->
            if (clip.id in old || clip.kind != ClipKind.AUDIO) return@mapNotNull clip
            val sync = assets[clip.assetId]?.syncedAudio ?: return@mapNotNull clip
            val audio = assets[sync.assetId]?.takeIf { !it.offline } ?: return@mapNotNull clip
            lined(clip, audio, sync, frameMs)
        }
        return after.copy(clips = clips)
    }

    /** [clip] (a synced video's sound) as the same stretch of [audio]: null when the file does not reach it. */
    private fun lined(clip: Clip, audio: MediaAsset, sync: SyncedAudio, frameMs: Double): Clip? {
        val speed = clip.speed.factor.toDouble()
        var start = clip.start
        var length = clip.durationFrames
        var sourceIn = clip.sourceInMs + sync.offsetMs.toDouble()
        if (sourceIn < 0) {
            val skip = ceil(-sourceIn / (frameMs * speed) - EPSILON).toInt()
            start += skip
            length -= skip
            sourceIn += skip * frameMs * speed
        }
        val reach = floor((audio.durationMs - sourceIn) / (frameMs * speed) + EPSILON).toInt()
        length = minOf(length, reach)
        if (length <= 0) return null
        return clip.copy(
            assetId = audio.id,
            name = audio.name.substringBeforeLast('.'),
            start = start,
            durationFrames = length,
            sourceInMs = sourceIn.roundToLong(),
        )
    }

    private companion object {
        /** Frame counts worked out in floating point land a hair off whole numbers (3800 ms / 33.3 ms = 113.99…). */
        const val EPSILON = 1e-6
    }
}
