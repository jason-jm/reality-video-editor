package com.example.prismstudio.p40b43a88.domain

import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import org.junit.Assert.assertEquals

/** Deterministic [IdGenerator]: hands out "prefix-1", "prefix-2", … from one shared counter. */
class SequentialIds : IdGenerator {
    private var counter = 0
    override fun next(prefix: String): String = "$prefix-${++counter}"
}

/** A media clip; its kind follows the track (audio on `a*` tracks, picture otherwise). */
fun clipOf(
    id: String,
    start: Int,
    duration: Int,
    track: String = Timeline.V1,
    assetId: String? = "media",
    sourceInMs: Long = 0,
    linkId: String? = null,
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    kind: ClipKind = if (track.startsWith("a")) ClipKind.AUDIO else ClipKind.VIDEO,
): Clip = Clip(
    id = id,
    trackId = track,
    kind = kind,
    name = id,
    start = start,
    durationFrames = duration,
    assetId = assetId,
    sourceInMs = sourceInMs,
    speed = ClipSpeed.of(speed),
    linkId = linkId,
)

fun titleClipOf(id: String, start: Int, duration: Int, track: String = Timeline.V2, text: String = "Title"): Clip = Clip(
    id = id,
    trackId = track,
    kind = ClipKind.TITLE,
    name = "标题：$text",
    start = start,
    durationFrames = duration,
    title = TitleStyle(text),
)

/** The four default tracks (V2, V1, A1, A2), optionally locked or disabled, holding [clips]. */
fun timelineOf(
    vararg clips: Clip,
    locked: Set<String> = emptySet(),
    disabled: Set<String> = emptySet(),
    markers: List<Marker> = emptyList(),
): Timeline = Timeline(
    tracks = Timeline.defaultTracks().map { it.copy(locked = it.id in locked, enabled = it.id !in disabled) },
    clips = clips.toList(),
    markers = markers,
)

fun assetOf(
    id: String,
    kind: MediaKind = MediaKind.VIDEO,
    durationMs: Long = 10_000,
    name: String = "$id.mp4",
    hasAudio: Boolean = kind != MediaKind.IMAGE,
    offline: Boolean = false,
    sizeBytes: Long = 1L shl 20,
    poster: PosterColors = PosterColors.NEUTRAL,
): MediaAsset = MediaAsset(
    id = id,
    uri = "content://media/$id",
    name = name,
    kind = kind,
    durationMs = durationMs,
    width = if (kind == MediaKind.AUDIO) 0 else 1920,
    height = if (kind == MediaKind.AUDIO) 0 else 1080,
    frameRate = if (kind == MediaKind.VIDEO) 30f else 0f,
    videoCodec = if (kind == MediaKind.VIDEO) "H.264" else "—",
    audioDescription = if (hasAudio) "AAC 48 kHz 立体声" else "无音频",
    hasAudio = hasAudio,
    sizeBytes = sizeBytes,
    relativePath = "DCIM/Camera/",
    poster = poster,
    offline = offline,
)

fun projectOf(
    timeline: Timeline = Timeline.EMPTY,
    media: List<MediaAsset> = emptyList(),
    fps: Int = 30,
    width: Int = 1920,
    height: Int = 1080,
    mixer: MixerState = MixerState.forTracks(timeline.tracks),
    id: String = "p-demo",
    name: String = "Demo",
    modifiedAtMillis: Long = 1_000L,
    jobs: List<RenderJob> = emptyList(),
): ProjectDocument = ProjectDocument(
    id = id,
    name = name,
    format = ProjectFormat(width, height, fps),
    createdAtMillis = 500L,
    modifiedAtMillis = modifiedAtMillis,
    media = media,
    timeline = timeline,
    mixer = mixer,
    render = RenderSettings(fileName = name),
    jobs = jobs,
)

/**
 * Locking a track must protect it: [edit] either refuses with [EditRejected] or leaves every clip on
 * [lockedTrackId] exactly as it was in [before].
 */
fun assertLockedTrackUntouched(before: Timeline, lockedTrackId: String, edit: () -> Timeline) {
    val after = try {
        edit()
    } catch (_: EditRejected) {
        return
    }
    assertEquals("clips on locked track $lockedTrackId", before.clipsOn(lockedTrackId), after.clipsOn(lockedTrackId))
}
