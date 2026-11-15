package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import java.util.Locale

/** Resolve's edits from the source viewer to the timeline, with their keys. */
enum class SourceEdit(val label: String, val keys: String) {
    /** At the playhead, pushing everything after it later on every unlocked track. */
    INSERT("插入", "F9"),

    /** At the playhead, over what is there. */
    OVERWRITE("覆盖", "F10"),

    /** In place of the clip under the playhead (or the timeline's In to Out) at its own length, the rest of the timeline moving to suit. */
    RIPPLE_OVERWRITE("波纹覆盖", "Shift+F10"),

    /** In place of the clip under the playhead, the source's playhead frame on the timeline's. */
    REPLACE("替换", "F11"),

    /** The marked source range retimed to fill the timeline's In to Out, over what is there. */
    FIT_TO_FILL("适配填充", "Shift+F11"),

    /** At the playhead, on the first video track above the picture target that is free there. */
    PLACE_ON_TOP("叠加", "F12"),

    /** At the end of the timeline. */
    APPEND("追加", "Shift+F12"),
}

/**
 * Three-point editing: the marked range of a media-pool item goes onto the timeline at the playhead —
 * [SourceEdit.INSERT] splits whatever plays there and pushes the rest of the timeline later on every
 * unlocked track (so picture, sound and subtitles stay in sync; a locked track stays put),
 * [SourceEdit.OVERWRITE] replaces what is there, [SourceEdit.RIPPLE_OVERWRITE] takes the place of the clip
 * under the playhead and moves the rest of the timeline by the difference in length, [SourceEdit.REPLACE]
 * swaps the media of the clip under the playhead, [SourceEdit.PLACE_ON_TOP] lays the picture over the cut on a free upper video track (its
 * sound on a free audio track), [SourceEdit.APPEND] adds it at the end. The destination controls
 * ([com.example.prismstudio.p40b43a88.domain.model.EditTargets]) say where: video goes to the picture
 * target (V1) with its sound linked on the sound target (A1), a still there for five seconds, sound-only
 * media to the sound target or, while that is automatic, the music track. Timeline In / Out marks
 * ([com.example.prismstudio.p40b43a88.domain.model.TimelineMarks]) take the playhead's place for insert,
 * overwrite and place on top, and are used up by the edit.
 */
class SourceEditUseCase(private val ids: IdGenerator, private val append: AppendMediaUseCase = AppendMediaUseCase(ids)) {

    /**
     * The marked range [inMs]–[outMs] of [asset] onto [timeline] by [mode] at frame [at]. [syncMs] is the
     * source frame a [SourceEdit.REPLACE] lines up with [at] — the source viewer's playhead; null lines the
     * media's first frame up with the replaced clip's first.
     */
    @Suppress("LongParameterList")
    operator fun invoke(
        timeline: Timeline,
        asset: MediaAsset,
        inMs: Long,
        outMs: Long,
        at: Int,
        mode: SourceEdit,
        fps: Int,
        syncMs: Long? = null,
    ): EditResult {
        if (mode == SourceEdit.APPEND) return append(timeline, asset, inMs, outMs, fps)
        if (asset.offline) throw EditRejected("素材离线，无法添加：${asset.name}")
        val start = at.coerceAtLeast(0)
        val name = asset.name.substringBeforeLast('.')
        if (mode == SourceEdit.REPLACE) return replace(timeline, asset, name, syncMs, start, fps)
        if (mode == SourceEdit.FIT_TO_FILL) return fitToFill(timeline, asset, name, inMs, outMs, fps)
        val sourceLength = when (asset.kind) {
            MediaKind.IMAGE -> Timecode.msToFrames(MediaAsset.IMAGE_DEFAULT_DURATION_MS, fps)
            else -> Timecode.msToFrames((outMs - inMs).coerceAtLeast(1), fps).coerceAtLeast(1)
        }
        if (mode == SourceEdit.RIPPLE_OVERWRITE) return rippleOverwrite(timeline, asset, name, inMs, start, sourceLength, fps)
        if (!timeline.marks.isEmpty) {
            val (markedStart, markedLength) = marked(timeline, asset, name, inMs, sourceLength, fps)
            val edited = edit(timeline.copy(marks = TimelineMarks()), asset, name, inMs, markedStart, markedLength, mode, fps)
            return edited.copy(message = edited.message + "，按时间线入出点放置")
        }
        return edit(timeline, asset, name, inMs, start, sourceLength, mode, fps)
    }

    /**
     * Resolve's Fit to Fill: the source's In to Out overwrites the timeline's In to Out, its speed set so it
     * lasts exactly that long (a still simply fills it); the picture and its linked sound both take the
     * speed. Needs both timeline marks, and uses them up.
     */
    @Suppress("LongParameterList")
    private fun fitToFill(timeline: Timeline, asset: MediaAsset, name: String, inMs: Long, outMs: Long, fps: Int): EditResult {
        val inFrame = timeline.marks.inFrame
        val outFrame = timeline.marks.outFrame
        if (inFrame == null || outFrame == null) throw EditRejected("适配填充要先在时间线上标记入点和出点")
        val length = outFrame - inFrame
        val sourceMs = (outMs - inMs).coerceAtLeast(1)
        val speed = if (asset.kind == MediaKind.IMAGE) ClipSpeed.NORMAL else ClipSpeed(sourceMs * fps / (length * 1000f))
        if (speed.factor < ClipSpeed.MIN || speed.factor > ClipSpeed.MAX) {
            throw EditRejected("要以 ${speed.label} 播放才能填满入出点，超出 ${ClipSpeed(ClipSpeed.MIN).label}–${ClipSpeed(ClipSpeed.MAX).label} 的范围")
        }
        val placed = edit(timeline.copy(marks = TimelineMarks()), asset, name, inMs, inFrame, length, SourceEdit.OVERWRITE, fps)
        val picture = placed.timeline.clip(placed.selectedClipId)
        val retimed = placed.timeline.clips.map { c ->
            if (c.id == picture?.id || (picture?.linkId != null && c.linkId == picture.linkId)) c.copy(speed = speed, speedSections = emptyList()) else c
        }
        val message = buildString {
            append("已把“$name”（${Timecode.format(Timecode.msToFrames(sourceMs, fps), fps)}）以 ${speed.label} ")
            append("适配填充到 ${Timecode.format(inFrame, fps)} → ${Timecode.format(outFrame, fps)}")
            if (placed.message.endsWith("，覆盖了原有内容")) append("，覆盖了原有内容")
        }
        return EditResult(placed.timeline.copy(clips = retimed), placed.selectedClipId, message)
    }

    /**
     * Resolve's Ripple Overwrite: [length] frames of the source from [inMs] take the place of the clip under
     * the playhead [at] on the target track — the picture target; for sound-only media the sound target — or
     * of the timeline's In to Out, which it uses up, on the destination tracks. Everything after the replaced
     * range moves by the difference in length on every unlocked track, so picture, sound and subtitles stay in
     * sync: a longer source opens a gap at the range's end on the other tracks (splitting what plays across
     * it), a shorter one cuts the end of the range out of them. A locked track stays put.
     */
    @Suppress("LongParameterList")
    private fun rippleOverwrite(timeline: Timeline, asset: MediaAsset, name: String, inMs: Long, at: Int, length: Int, fps: Int): EditResult {
        val markIn = timeline.marks.inFrame
        val markOut = timeline.marks.outFrame
        val (from, until) = when {
            markIn != null && markOut != null -> markIn to markOut
            markIn != null || markOut != null -> throw EditRejected("波纹覆盖要用时间线的入点和出点：再标记一个点，或清除标记后替换播放头处的片段")
            else -> {
                val track = (if (asset.kind == MediaKind.AUDIO) timeline.soundTarget() else timeline.videoTarget())
                    ?: throw EditRejected("没有可波纹覆盖的轨道")
                val target = timeline.clipsOn(track.id).firstOrNull { at in it }
                    ?: throw EditRejected("播放头处的 ${track.name} 上没有片段可波纹覆盖")
                target.start to target.end
            }
        }
        var result = timeline.copy(marks = TimelineMarks())
        val tracks = targetTracks(result, asset).map { track ->
            if (result.track(track.id) == null) result = result.withTrack(track)
            result.requireTrackUnlocked(track.id)
            track
        }
        val tailLinks = mutableMapOf<String, String>()
        for (track in tracks) result = result.overwritten(track.id, from, until, fps, ids, tailLinks).first
        val delta = length - (until - from)
        // The destination tracks are clear over the range, so whatever the close-up cuts lies on other tracks.
        var cutElsewhere = 0
        result = when {
            delta > 0 -> rippled(result, until, delta, fps)
            delta < 0 -> closed(result, until + delta, until, fps).let { (closedUp, cut) -> cutElsewhere = cut; closedUp }
            else -> result
        }
        val placed = clipsFor(asset, name, inMs, from, length, tracks)
        val message = buildString {
            append("已在 ${Timecode.format(from, fps)} 波纹覆盖“$name”（${Timecode.format(length, fps)}，原为 ${Timecode.format(until - from, fps)}）")
            append(
                when {
                    delta > 0 -> "，后面的片段后移 ${Timecode.format(delta, fps)}"
                    delta < 0 -> "，后面的片段前移 ${Timecode.format(-delta, fps)}"
                    else -> "，长度相同，后面的片段不动"
                },
            )
            if (cutElsewhere > 0) append("，其他轨道剪去了同样长度")
            if (markIn != null) append("，按时间线入出点替换")
        }
        return EditResult(result.copy(clips = result.clips + placed), placed.first().id, message)
    }

    /**
     * Where an edit lands with timeline marks, as start and length: In to Out when both are set (the
     * timeline decides the length, the source must last that long from its In), from In, or ending at Out.
     */
    @Suppress("LongParameterList")
    private fun marked(timeline: Timeline, asset: MediaAsset, name: String, inMs: Long, sourceLength: Int, fps: Int): Pair<Int, Int> {
        val inFrame = timeline.marks.inFrame
        val outFrame = timeline.marks.outFrame
        return when {
            inFrame != null && outFrame != null -> {
                val length = outFrame - inFrame
                val needMs = Timecode.framesToMs(length, fps)
                if (asset.kind != MediaKind.IMAGE && inMs + needMs > asset.durationMs) {
                    throw EditRejected(
                        "“$name”从源入点起只有 ${Timecode.format(Timecode.msToFrames(asset.durationMs - inMs, fps), fps)}，" +
                            "填不满时间线入出点（${Timecode.format(length, fps)}）",
                    )
                }
                inFrame to length
            }
            inFrame != null -> inFrame to sourceLength
            else -> {
                val start = outFrame!! - sourceLength
                if (start < 0) throw EditRejected("时间线出点之前放不下“$name”（需要 ${Timecode.format(sourceLength, fps)}）")
                start to sourceLength
            }
        }
    }

    /** Insert, overwrite or place on top of [length] frames of [asset] from [inMs] at frame [start]. */
    @Suppress("LongParameterList", "LongMethod")
    private fun edit(timeline: Timeline, asset: MediaAsset, name: String, inMs: Long, start: Int, length: Int, mode: SourceEdit, fps: Int): EditResult {
        return when (mode) {
            SourceEdit.PLACE_ON_TOP -> placeOnTop(timeline, asset, name, inMs, start, length, fps)
            else -> {
                var result = timeline
                val tracks = targetTracks(result, asset).map { track ->
                    if (result.track(track.id) == null) result = result.withTrack(track)
                    result.requireTrackUnlocked(track.id)
                    track
                }
                var touched = 0
                if (mode == SourceEdit.INSERT) {
                    result = rippled(result, start, length, fps)
                } else {
                    val tailLinks = mutableMapOf<String, String>()
                    for (track in tracks) {
                        val (carved, n) = result.overwritten(track.id, start, start + length, fps, ids, tailLinks)
                        result = carved
                        touched += n
                    }
                }
                val placed = clipsFor(asset, name, inMs, start, length, tracks)
                val message = buildString {
                    append("已在 ${Timecode.format(start, fps)} ${mode.label}“$name”（${Timecode.format(length, fps)}）")
                    if (mode == SourceEdit.INSERT) append("，后面的片段后移")
                    if (touched > 0) append("，覆盖了原有内容")
                }
                EditResult(result.copy(clips = result.clips + placed), placed.first().id, message)
            }
        }
    }

    /**
     * Where the item goes: the picture on the picture target (a video's sound on the sound target, A1
     * while automatic), sound-only media on the sound target (the music track while automatic).
     */
    private fun targetTracks(timeline: Timeline, asset: MediaAsset): List<Track> = when {
        asset.kind == MediaKind.AUDIO -> listOf(timeline.soundTarget())
        asset.kind == MediaKind.VIDEO && asset.hasAudio -> listOfNotNull(timeline.videoTarget(), timeline.syncSoundTarget())
        else -> listOfNotNull(timeline.videoTarget())
    }

    /**
     * Resolve's Replace: the clip under the playhead on the target track — the picture target; for
     * sound-only media the sound target — takes the source's media in its place and length, source frame
     * [syncMs] landing on the playhead [at] (null: the media's start on the clip's), and its linked sound
     * takes the source's sound alike. The rest of the clip stays (look, effects, transition, volume,
     * keyframes); speed and reverse, which were about the old media, go back to normal.
     */
    @Suppress("LongParameterList")
    private fun replace(timeline: Timeline, asset: MediaAsset, name: String, syncMs: Long?, at: Int, fps: Int): EditResult {
        val track = (if (asset.kind == MediaKind.AUDIO) timeline.soundTarget() else timeline.videoTarget())
            ?: throw EditRejected("没有可替换的轨道")
        val target = timeline.clipsOn(track.id).firstOrNull { at in it }
            ?: throw EditRejected("播放头处的 ${track.name} 上没有片段可替换")
        if (target.kind == ClipKind.TITLE) throw EditRejected("标题片段不能替换为素材")
        val group = timeline.linkedGroup(target)
        timeline.requireUnlocked(group, target)
        val withSound = asset.kind == MediaKind.VIDEO && asset.hasAudio
        val sounds = (group - target).filter { it.kind == ClipKind.AUDIO }
        val sync = syncMs ?: Timecode.framesToMs(at - target.start, fps)
        val swapped = (listOf(target) + if (withSound) sounds else emptyList()).associate { c ->
            c.id to c.copy(
                assetId = asset.id, name = name, sourceInMs = sourceIn(asset, name, c, sync, at, fps),
                speed = ClipSpeed.NORMAL, speedSections = emptyList(), reverse = false,
            )
        }
        val message = buildString {
            append("已用“$name”替换 ${track.name} 上的“${target.name}”（${Timecode.format(target.durationFrames, fps)}）")
            if (sounds.isNotEmpty()) append(if (withSound) "，声音一并替换" else "，新素材没有声音，原声保留")
        }
        return EditResult(timeline.copy(clips = timeline.clips.map { swapped[it.id] ?: it }), target.id, message)
    }

    /** Where [clip]'s new media starts for source frame [syncMs] to play at [at]; refused when the media runs out first. */
    @Suppress("LongParameterList")
    private fun sourceIn(asset: MediaAsset, name: String, clip: Clip, syncMs: Long, at: Int, fps: Int): Long {
        if (asset.kind == MediaKind.IMAGE) return 0L
        val sourceIn = syncMs - Timecode.framesToMs(at - clip.start, fps)
        val endMs = sourceIn + Timecode.framesToMs(clip.durationFrames, fps)
        if (sourceIn < 0) throw EditRejected("“$name”在源播放头之前少了 ${shortfall(-sourceIn, fps)}素材，填不满片段开头：把源播放头往后移")
        if (endMs > asset.durationMs) {
            throw EditRejected("“$name”在源播放头之后少了 ${shortfall(endMs - asset.durationMs, fps)}素材，填不满片段结尾：把源播放头往前移")
        }
        return sourceIn
    }

    /** [ms] missing, in frames under a second, else in seconds. */
    private fun shortfall(ms: Long, fps: Int): String =
        if (ms < 1_000) "${((ms * fps + 999) / 1_000).coerceAtLeast(1)} 帧" else String.format(Locale.ROOT, "%.1f 秒", ms / 1_000f)

    /** The new clips on [tracks] (picture and sound of a video linked), the picture first. */
    @Suppress("LongParameterList")
    private fun clipsFor(asset: MediaAsset, name: String, inMs: Long, start: Int, length: Int, tracks: List<Track>): List<Clip> {
        val link = if (tracks.size > 1) ids.next("l") else null
        val sourceIn = if (asset.kind == MediaKind.IMAGE) 0L else inMs
        return tracks.map { track ->
            Clip(
                id = ids.next("c"), trackId = track.id, kind = if (track.kind == TrackKind.AUDIO) ClipKind.AUDIO else ClipKind.VIDEO,
                name = name, start = start, durationFrames = length, assetId = asset.id, sourceInMs = sourceIn, linkId = link,
            )
        }
    }

    /** Every unlocked track opened at [at] by [length] frames ([Timeline.opened]). */
    private fun rippled(timeline: Timeline, at: Int, length: Int, fps: Int): Timeline = timeline.opened(at, length, fps, ids)

    /**
     * Every unlocked track closed up over [from]–[until]: what lies there is cut out (a clip across either end
     * is split there, the tails of linked clips staying linked) and everything from [until] on moves back.
     * Also returns how many clips were cut.
     */
    private fun closed(timeline: Timeline, from: Int, until: Int, fps: Int): Pair<Timeline, Int> {
        val unlocked = timeline.tracks.filterNot { it.locked }.map { it.id }.toSet()
        val tailLinks = mutableMapOf<String, String>()
        var result = timeline
        var cut = 0
        for (id in unlocked) {
            val (next, n) = result.overwritten(id, from, until, fps, ids, tailLinks)
            result = next
            cut += n
        }
        val length = until - from
        return result.copy(clips = result.clips.map { if (it.trackId in unlocked && it.start >= until) it.copy(start = it.start - length) else it }) to cut
    }

    /**
     * Picture on the first video track above the picture target that is unlocked and free over the range
     * (a new one when none is and there is room), its sound on the first such audio track (left out when
     * there is none).
     */
    @Suppress("LongParameterList")
    private fun placeOnTop(timeline: Timeline, asset: MediaAsset, name: String, inMs: Long, at: Int, length: Int, fps: Int): EditResult {
        var result = timeline
        fun free(track: Track) = !track.locked && result.clipsOn(track.id).none { it.start < at + length && it.end > at }
        fun freeTrack(kind: TrackKind, candidates: List<Track>): Track? =
            candidates.firstOrNull(::free) ?: if (TracksUseCase.canAdd(result, kind)) result.nextTrack(kind).also { result = result.withTrack(it) } else null
        val tracks = mutableListOf<Track>()
        var soundLeftOut = false
        if (asset.kind == MediaKind.AUDIO) {
            tracks += freeTrack(TrackKind.AUDIO, result.tracks.filter { it.kind == TrackKind.AUDIO })
                ?: throw EditRejected("没有空闲的音频轨道可以叠加")
        } else {
            // Bottom up from the picture target: above V1, V2 then V3 (the list runs top first).
            val base = result.videoTarget()?.id
            val above = result.tracks.filter { it.kind == TrackKind.VIDEO }.takeWhile { it.id != base }.reversed()
            tracks += freeTrack(TrackKind.VIDEO, above) ?: throw EditRejected("没有空闲的视频轨道可以叠加")
            if (asset.kind == MediaKind.VIDEO && asset.hasAudio) {
                val sound = freeTrack(TrackKind.AUDIO, result.tracks.filter { it.kind == TrackKind.AUDIO })
                if (sound != null) tracks += sound else soundLeftOut = true
            }
        }
        val placed = clipsFor(asset, name, inMs, at, length, tracks)
        val message = buildString {
            append("已叠加“$name”到 ${tracks.first().name} · ${Timecode.format(at, fps)}（${Timecode.format(length, fps)}）")
            if (soundLeftOut) append("，没有空闲音频轨道，未放置声音")
        }
        return EditResult(result.copy(clips = result.clips + placed), placed.first().id, message)
    }
}
