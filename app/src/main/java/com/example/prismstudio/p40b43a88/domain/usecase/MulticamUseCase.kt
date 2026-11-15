package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Resolve's multicam clips. [create] makes the selected camera clips — each picture with its linked sound, already in step
 * (as they lie: Auto Align Clips lines them up by their sound) — angles of one clip: angle n on the multicam timeline's
 * Vn and A_n, the clip in their place. Its parts each play one angle ([Clip.angle]); [switch] cuts to another angle at the
 * playhead, or switches the whole segment, picture and sound together or either alone. It is a compound clip underneath:
 * it opens to slip an angle into step, and flattens to the angles it plays.
 */
class MulticamUseCase(private val ids: IdGenerator, private val timelines: TimelinesUseCase = TimelinesUseCase(ids)) {

    /** What a switch changes: Resolve's Video & Audio / Video only / Audio only. */
    enum class SwitchMode(val label: String) { BOTH("画面+声音"), PICTURE("仅画面"), SOUND("仅声音") }

    /** Angle [index] of a multicam clip: the name of its camera clip, and whether it has a picture and a sound. */
    data class Angle(val index: Int, val name: String, val hasPicture: Boolean, val hasSound: Boolean) {
        val label: String get() = "机位 ${index + 1}"
    }

    data class Result(val doc: ProjectDocument, val selectedClipId: String?, val message: String)

    /** Why [clipIds] (with their linked sound) cannot become a multicam clip, or null when they can. */
    fun unavailable(timeline: Timeline, clipIds: Collection<String>): String? {
        val clips = withPartners(timeline, clipIds)
        val pictures = clips.filter { it.kind != ClipKind.AUDIO }
        val sounds = clips.filter { it.kind == ClipKind.AUDIO }
        return when {
            clips.isEmpty() -> "请先在时间线上选择片段"
            clips.any { it.isCompound } -> "复合片段和多机位片段不能做成机位"
            clips.any { it.kind == ClipKind.TITLE || timeline.track(it.trackId)?.kind == TrackKind.SUBTITLE } -> "标题和字幕不能做成机位"
            pictures.size < 2 -> "选择至少两个画面片段：每个是一个机位（先对齐）"
            pictures.size > Clip.MAX_ANGLES -> "最多 ${Clip.MAX_ANGLES} 个机位"
            sounds.any { s -> pictures.none { it.linkId != null && it.linkId == s.linkId } } -> "单独的声音片段不能做成机位：先把它链接到一个画面"
            pictures.any { p -> sounds.count { it.linkId != null && it.linkId == p.linkId } > 1 } -> "每个机位最多带一条声音"
            clips.any { timeline.track(it.trackId)?.locked == true } -> "所选片段在已锁定的轨道上"
            else -> blocker(timeline, clips)
        }
    }

    /** `selection-multicam-note`: what [create] would make of [clipIds], or why it cannot. */
    fun note(timeline: Timeline, clipIds: Collection<String>): String {
        unavailable(timeline, clipIds)?.let { return it }
        val pictures = withPartners(timeline, clipIds).count { it.kind != ClipKind.AUDIO }
        return "$pictures 个机位：按轨道从下往上依次为机位 1、2…，保持现在的对齐"
    }

    /** [clipIds] as angles of a new multicam clip in their place, selected (angle 1 playing). */
    fun create(doc: ProjectDocument, clipIds: Collection<String>): Result {
        val t = doc.timeline
        unavailable(t, clipIds)?.let { throw EditRejected(it) }
        val clips = withPartners(t, clipIds)
        val from = clips.minOf { it.start }
        val to = clips.maxOf { it.end }
        val videoTracks = CompoundClipUseCase.levels(t, TrackKind.VIDEO).map { it.id }
        // Angle 1 is the lowest track's picture, then up the tracks (and along a track, the earlier first).
        val pictures = clips.filter { it.kind != ClipKind.AUDIO }.sortedWith(compareBy({ videoTracks.indexOf(it.trackId) }, { it.start }))
        val sounds = clips.filter { it.kind == ClipKind.AUDIO }
        val n = pictures.size
        val inner = buildList {
            pictures.forEachIndexed { k, p ->
                add(p.copy(trackId = "v${k + 1}", start = p.start - from))
                sounds.firstOrNull { it.linkId != null && it.linkId == p.linkId }?.let { add(it.copy(trackId = "a${k + 1}", start = it.start - from)) }
            }
        }
        val tracks = (n downTo 1).map { Track("v$it", "V$it", TrackKind.VIDEO) } + (1..n).map { Track("a$it", "A$it", TrackKind.AUDIO) }
        val inside = Timeline(tracks, inner, emptyList())
        val all = timelines.all(doc)
        val id = generateSequence { ids.next("mc") }.first { fresh -> all.none { it.id == fresh } }
        val name = nextName(all)
        val stored = StoredTimeline(id, name, doc.format, inside, MixerState.forTracks(inside.tracks))
        val pictureTrack = t.tracks.last { tr -> tr.kind == TrackKind.VIDEO && pictures.any { it.trackId == tr.id } }.id
        val soundTrack = t.tracks.firstOrNull { tr -> tr.kind == TrackKind.AUDIO && sounds.any { it.trackId == tr.id } }?.id
        val link = soundTrack?.let { ids.next("l") }
        val picture = Clip(ids.next("c"), pictureTrack, ClipKind.VIDEO, name, from, to - from, compoundId = id, linkId = link)
        val sound = soundTrack?.let { Clip(ids.next("c"), it, ClipKind.AUDIO, name, from, to - from, compoundId = id, linkId = link) }
        val gone = clips.map { it.id }.toSet()
        val parent = t.copy(clips = t.clips.filterNot { it.id in gone } + listOfNotNull(picture, sound))
        val changed = doc.copy(
            timeline = parent,
            timelines = doc.timelines + stored,
            compoundIds = doc.compoundIds + id,
            multicamIds = doc.multicamIds + id,
        )
        return Result(changed, picture.id, "已用 $n 个机位新建多机位片段“$name”：在检查器里点按机位即可切换")
    }

    /** Whether [clip] is a part of a multicam clip. */
    fun isMulticam(doc: ProjectDocument, clip: Clip?): Boolean = clip?.compoundId != null && clip.compoundId in doc.multicamIds

    /** [clip]'s multicam clip's angles, angle 1 first; empty for any other clip. */
    fun angles(doc: ProjectDocument, clip: Clip): List<Angle> {
        if (!isMulticam(doc, clip)) return emptyList()
        val stored = doc.storedTimeline(clip.compoundId) ?: return emptyList()
        val inside = stored.timeline
        val video = CompoundClipUseCase.levels(inside, TrackKind.VIDEO)
        val audio = CompoundClipUseCase.levels(inside, TrackKind.AUDIO)
        return video.mapIndexed { i, tr ->
            val first = inside.clipsOn(tr.id).firstOrNull()
            val soundTrack = audio.getOrNull(i)
            Angle(i, first?.name ?: "机位 ${i + 1}", first != null, soundTrack != null && inside.clipsOn(soundTrack.id).isNotEmpty())
        }
    }

    /**
     * The part of [clip]'s multicam clip at [frame] on [clip]'s track — the one a switch acts on — else [clip] itself. A cut
     * leaves several parts; the playhead decides which is switched.
     */
    fun partAt(timeline: Timeline, clip: Clip, frame: Int): Clip =
        timeline.clipsOn(clip.trackId).firstOrNull { it.compoundId == clip.compoundId && frame >= it.start && frame < it.end } ?: clip

    /**
     * The multicam part [clipId] — with the clip's other part at [frame] (its picture or its sound), by [mode] — switched to
     * [angle]. With [cut], a part [frame] lies inside is cut there and the piece after the playhead switched (Resolve's click
     * in the multicam viewer); a part starting at [frame], or every part without [cut], switches whole (its Option-click).
     * Linked parts cut together stay linked piece by piece; a part cut alone (picture or sound only) is unlinked from the
     * other, whose span it no longer shares — and that other part plays on unbroken.
     */
    @Suppress("LongParameterList")
    fun switch(doc: ProjectDocument, clipId: String, angle: Int, frame: Int, mode: SwitchMode, cut: Boolean): Result {
        val t = doc.timeline
        val clip = t.clip(clipId) ?: throw EditRejected("请先选择多机位片段")
        if (!isMulticam(doc, clip)) throw EditRejected("“${clip.name}”不是多机位片段")
        val target = angles(doc, clip).getOrNull(angle) ?: throw EditRejected("没有机位 ${angle + 1}")
        val inClip = frame >= clip.start && frame < clip.end
        // The clip's part of each kind: itself, its linked part, or (after a cut of one kind alone) the part of the same
        // multicam clip at the playhead.
        fun part(picture: Boolean): Clip? {
            if ((clip.kind != ClipKind.AUDIO) == picture) return clip
            val linked = t.linkedGroup(clip).firstOrNull { it.id != clip.id && it.compoundId == clip.compoundId && (it.kind != ClipKind.AUDIO) == picture }
            if (linked != null || !inClip) return linked
            return t.clips.firstOrNull { it.compoundId == clip.compoundId && (it.kind != ClipKind.AUDIO) == picture && frame >= it.start && frame < it.end }
        }
        val parts = listOfNotNull(
            if (mode != SwitchMode.SOUND) part(true) else null,
            if (mode != SwitchMode.PICTURE) part(false) else null,
        )
        if (parts.isEmpty()) throw EditRejected(if (mode == SwitchMode.SOUND) "这个多机位片段没有声音部分" else "这个多机位片段没有画面部分")
        t.requireUnlocked(parts, clip)
        if (parts.all { it.angle == angle }) throw EditRejected("已经是${target.label}")
        val what = when (mode) {
            SwitchMode.BOTH -> "画面和声音"
            SwitchMode.PICTURE -> "画面"
            SwitchMode.SOUND -> "声音"
        }
        val fps = doc.format.fps.coerceAtLeast(1)
        val splitting = parts.filter { cut && frame > it.start && frame < it.end }
        val group = parts.flatMap { t.linkedGroup(it) }.distinctBy { it.id }
        val together = splitting.isNotEmpty() && splitting.map { it.id }.toSet() == group.map { it.id }.toSet()
        val newLink = if (together) splitting.first().linkId?.let { ids.next("l") } else null
        val unlink = if (splitting.isNotEmpty() && !together) group.map { it.id }.toSet() else emptySet()
        val replaced = mutableMapOf<String, List<Clip>>()
        var selected: String? = null
        for (p in parts) {
            val keepLink = if (p.id in unlink) null else p.linkId
            val pieces = if (p in splitting) {
                val left = p.piece(p.start, frame, fps).copy(linkId = keepLink)
                val right = p.piece(frame, p.end, fps).copy(id = ids.next("c"), angle = angle, linkId = if (together) newLink else null)
                listOf(left, right)
            } else {
                listOf(p.copy(angle = angle, linkId = keepLink))
            }
            replaced[p.id] = pieces
            if (selected == null || p.kind != ClipKind.AUDIO) selected = pieces.last().id
        }
        // A linked part left out of the switch, unlinked from the piece it no longer matches.
        for (g in group) if (g.id in unlink && g.id !in replaced) replaced[g.id] = listOf(g.copy(linkId = null))
        val clips = t.clips.flatMap { replaced[it.id] ?: listOf(it) }
        val message = if (splitting.isEmpty()) "整段${what}切到${target.label}（${target.name}）"
        else "在 ${Timecode.format(frame, fps)} 把${what}切到${target.label}（${target.name}）"
        return Result(doc.copy(timeline = t.copy(clips = clips)), selected, message)
    }

    /** Where angle [Angle.index] stands at timeline [frame]: its camera clip's file and the time in it. */
    data class AngleSource(val assetId: String, val sourceMs: Long)

    /**
     * Each angle's picture at timeline [frame] in [clip]'s multicam clip (held at the clip's ends outside it), angle 1 first;
     * null where an angle has no picture then — for `multicam-panel`'s tiles.
     */
    fun sources(doc: ProjectDocument, clip: Clip, frame: Int): List<AngleSource?> {
        if (!isMulticam(doc, clip)) return emptyList()
        val stored = doc.storedTimeline(clip.compoundId) ?: return emptyList()
        val fps = doc.format.fps.coerceAtLeast(1)
        val innerFps = stored.format.fps.coerceAtLeast(1)
        val local = (frame - clip.start).coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0))
        val inner = Math.round(clip.sourceInMs * innerFps / 1000.0 + local * innerFps / fps.toDouble()).toInt()
        return CompoundClipUseCase.levels(stored.timeline, TrackKind.VIDEO).map { tr ->
            stored.timeline.clipsOn(tr.id).firstOrNull { inner >= it.start && inner < it.end && it.assetId != null }
                ?.let { ic -> AngleSource(ic.assetId!!, ic.sourceMsAt(inner - ic.start, innerFps)) }
        }
    }

    /**
     * What the multicam clip plays at [frame] where [clip] lies: the angles of its picture and sound parts there (null where
     * it has none), for `multicam-panel`'s lit tiles.
     */
    fun playing(doc: ProjectDocument, clip: Clip, frame: Int): Pair<Int?, Int?> {
        val t = doc.timeline
        val at = if (frame >= clip.start && frame < clip.end) frame else clip.start
        fun angleOf(picture: Boolean): Int? {
            if ((clip.kind != ClipKind.AUDIO) == picture) return partAt(t, clip, at).angle
            return (t.linkedGroup(clip).firstOrNull { it.id != clip.id && it.compoundId == clip.compoundId && (it.kind != ClipKind.AUDIO) == picture }
                ?.takeIf { at >= it.start && at < it.end }
                ?: t.clips.firstOrNull { it.compoundId == clip.compoundId && (it.kind != ClipKind.AUDIO) == picture && at >= it.start && at < it.end })?.angle
        }
        return angleOf(true) to angleOf(false)
    }

    /** Why the clips cannot be replaced by one clip on their lowest video / first audio track, or null. */
    private fun blocker(timeline: Timeline, clips: List<Clip>): String? {
        val from = clips.minOf { it.start }
        val to = clips.maxOf { it.end }
        val chosen = clips.map { it.id }.toSet()
        val lowestVideo = timeline.tracks.lastOrNull { tr -> tr.kind == TrackKind.VIDEO && clips.any { it.trackId == tr.id } }
        val firstAudio = timeline.tracks.firstOrNull { tr -> tr.kind == TrackKind.AUDIO && clips.any { it.trackId == tr.id } }
        for (track in listOfNotNull(lowestVideo, firstAudio)) {
            if (timeline.clipsOn(track.id).any { it.id !in chosen && it.end > from && it.start < to }) {
                return "${track.name} 在机位之间还有没选中的片段：一起选中，或先移开"
            }
        }
        return null
    }

    private fun withPartners(timeline: Timeline, clipIds: Collection<String>): List<Clip> =
        clipIds.mapNotNull(timeline::clip).flatMap { timeline.linkedGroup(it) }.distinctBy { it.id }.sortedWith(compareBy({ it.start }, { it.trackId }))

    /** "多机位 N", one past the highest such number. */
    private fun nextName(all: List<StoredTimeline>): String {
        val highest = all.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val taken = all.map { it.name }.toSet()
        return generateSequence(highest + 1) { it + 1 }.map { "多机位 $it" }.first { it !in taken }
    }

    private companion object {
        val NUMBERED = Regex("多机位 (\\d+)")
    }
}
