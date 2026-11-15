package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolve's compound clips. [create] gathers the selected clips (with their linked partners) into a timeline of their own
 * and puts one clip in their place: the picture part on the lowest video track they used, the sound part on the first
 * audio track, linked. [open] edits that timeline (every clip of it follows), [back] returns, and [decompose] puts the
 * contents back on the timeline in the clip's place. Playback and renders see [expanded]: each compound clip replaced by
 * what it shows of its timeline, its lowest track on the clip's own track and the ones above it on extra tracks beside it,
 * its sound through the clip's channel.
 */
class CompoundClipUseCase(private val ids: IdGenerator, private val timelines: TimelinesUseCase = TimelinesUseCase(ids)) {

    data class Result(val doc: ProjectDocument, val selectedClipId: String?, val message: String)

    /** What `compound-panel` says of a compound clip: its timeline's name, the tracks holding clips, its clips and length. */
    data class Info(
        val name: String,
        val videoTracks: Int,
        val audioTracks: Int,
        val clips: Int,
        val durationFrames: Int,
        val fps: Int,
        /** Another of the project's timelines played as a clip (Resolve's nested timeline), not a compound clip's own. */
        val nested: Boolean = false,
    ) {
        /** `compound-panel`'s heading. */
        val title: String get() = (if (nested) "嵌套时间线 · " else "复合片段 · ") + name

        val summary: String
            get() = listOfNotNull(
                videoTracks.takeIf { it > 0 }?.let { "$it 条视频轨" },
                audioTracks.takeIf { it > 0 }?.let { "$it 条音频轨" },
                "$clips 个片段",
                Timecode.format(durationFrames, fps),
            ).joinToString(" · ")
    }

    /** Why [clipIds] cannot become one compound clip, or null when they can. */
    fun unavailable(timeline: Timeline, clipIds: Collection<String>): String? {
        val clips = withPartners(timeline, clipIds)
        return when {
            clips.isEmpty() -> "请先在时间线上选择片段"
            clips.any { timeline.track(it.trackId)?.kind == TrackKind.SUBTITLE } -> "字幕轨上的字幕不能放进复合片段"
            clips.any { timeline.track(it.trackId)?.locked == true } -> "所选片段在已锁定的轨道上"
            else -> blocker(timeline, clips)
        }
    }

    /** [clipIds] (and their linked partners) as a new compound clip in their place, selected. */
    fun create(doc: ProjectDocument, clipIds: Collection<String>): Result {
        val t = doc.timeline
        unavailable(t, clipIds)?.let { throw EditRejected(it) }
        val clips = withPartners(t, clipIds)
        val from = clips.minOf { it.start }
        val to = clips.maxOf { it.end }
        // The tracks the clips used, top first as the timeline lists them; inside, they become V1 up / A1 down.
        val videoUsed = t.tracks.filter { tr -> tr.kind == TrackKind.VIDEO && clips.any { it.trackId == tr.id } }
        val audioUsed = t.tracks.filter { tr -> tr.kind == TrackKind.AUDIO && clips.any { it.trackId == tr.id } }
        val innerVideo = (videoUsed.size.coerceAtLeast(1) downTo 1).map { Track("v$it", "V$it", TrackKind.VIDEO) }
        val innerAudio = (1..audioUsed.size.coerceAtLeast(1)).map { Track("a$it", "A$it", TrackKind.AUDIO) }
        val onto = videoUsed.mapIndexed { i, tr -> tr.id to innerVideo[i].id }.toMap() + audioUsed.mapIndexed { i, tr -> tr.id to innerAudio[i].id }
        val inside = Timeline(innerVideo + innerAudio, clips.map { it.copy(trackId = onto.getValue(it.trackId), start = it.start - from) }, emptyList())
        val all = timelines.all(doc)
        val id = generateSequence { ids.next("cp") }.first { fresh -> all.none { it.id == fresh } }
        val name = nextName(all)
        val stored = StoredTimeline(id, name, doc.format, inside, MixerState.forTracks(inside.tracks))
        val pictureTrack = videoUsed.lastOrNull()?.id
        val soundTrack = audioUsed.firstOrNull()?.id
        val link = if (pictureTrack != null && soundTrack != null) ids.next("l") else null
        val picture = pictureTrack?.let { Clip(ids.next("c"), it, ClipKind.VIDEO, name, from, to - from, compoundId = id, linkId = link) }
        val sound = soundTrack?.let { Clip(ids.next("c"), it, ClipKind.AUDIO, name, from, to - from, compoundId = id, linkId = link) }
        val gone = clips.map { it.id }.toSet()
        val parent = t.copy(clips = t.clips.filterNot { it.id in gone } + listOfNotNull(picture, sound))
        val changed = doc.copy(timeline = parent, timelines = doc.timelines + stored, compoundIds = doc.compoundIds + id)
        return Result(changed, (picture ?: sound)?.id, "已把 ${clips.size} 个片段收进复合片段“$name”")
    }

    /**
     * Why timeline [id] (a compound clip's or another of the project's) cannot go onto the open timeline as a clip — Resolve's
     * nested timelines — or null when it can: never the open timeline, nor one that plays it (a timeline inside itself).
     */
    fun cannotNest(doc: ProjectDocument, id: String): String? {
        val stored = doc.storedTimeline(id) ?: return "找不到这条时间线"
        val inner = stored.timeline
        return when {
            id == doc.timelineId -> "不能把时间线放进它自己里面"
            holds(doc, id, doc.timelineId, mutableSetOf()) -> "“${stored.name}”里面用到了当前时间线，不能放进来"
            inner.clips.none { it.kind == ClipKind.AUDIO || inner.track(it.trackId)?.kind == TrackKind.VIDEO } -> "“${stored.name}”里没有画面或声音"
            else -> null
        }
    }

    /**
     * Timeline [id] as clips to paste ([ClipboardUseCase]): its picture on the open timeline's video destination and its
     * sound on the audio destination (A1 by default), linked, from frame 0 and as long as it is — a compound clip of it.
     */
    fun content(doc: ProjectDocument, id: String): ClipboardUseCase.Content {
        cannotNest(doc, id)?.let { throw EditRejected(it) }
        val stored = doc.storedTimeline(id) ?: throw EditRejected("找不到这条时间线")
        val inner = stored.timeline
        val length = (inner.durationFrames * doc.format.fps / stored.format.fps.coerceAtLeast(1).toDouble()).roundToInt().coerceAtLeast(1)
        val picture = inner.clips.any { it.kind != ClipKind.AUDIO && inner.track(it.trackId)?.kind == TrackKind.VIDEO }
        val sound = inner.clips.any { it.kind == ClipKind.AUDIO }
        val t = doc.timeline
        val link = if (picture && sound) ids.next("l") else null
        return ClipboardUseCase.Content(listOfNotNull(
            if (picture) Clip(ids.next("c"), t.videoTarget()?.id ?: Timeline.V1, ClipKind.VIDEO, stored.name, 0, length, compoundId = id, linkId = link) else null,
            if (sound) Clip(ids.next("c"), t.audioTarget()?.id ?: Timeline.A1, ClipKind.AUDIO, stored.name, 0, length, compoundId = id, linkId = link) else null,
        ))
    }

    /** "复合片段" for a compound clip's timeline, "嵌套时间线" for another of the project's timelines played as a clip. */
    fun kindLabel(doc: ProjectDocument, id: String?): String = when (id) {
        in doc.multicamIds -> "多机位片段"
        in doc.compoundIds -> "复合片段"
        else -> "嵌套时间线"
    }

    /** Timeline [outer] plays timeline [inner], directly or through clips inside its clips. */
    private fun holds(doc: ProjectDocument, outer: String, inner: String, seen: MutableSet<String>): Boolean {
        if (!seen.add(outer)) return false
        val clips = doc.storedTimeline(outer)?.timeline?.clips ?: return false
        return clips.any { c -> c.compoundId == inner || (c.compoundId != null && holds(doc, c.compoundId, inner, seen)) }
    }

    /** Compound clip [clipId]'s timeline, opened; [back] returns here. */
    fun open(doc: ProjectDocument, clipId: String): Result {
        val clip = doc.timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val id = clip.compoundId ?: throw EditRejected("“${clip.name}”不是复合片段")
        val all = timelines.all(doc)
        val index = all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw EditRejected("找不到复合片段“${clip.name}”的内容")
        val opened = timelines.opened(doc, all, index).copy(compoundTrail = doc.compoundTrail + doc.timelineId)
        val users = all.sumOf { tl -> tl.timeline.clips.count { it.compoundId == id && it.kind != ClipKind.AUDIO } }
        val note = if (users > 1) "，改动会用到全部 $users 个使用它的片段" else ""
        return Result(opened, null, "已打开${kindLabel(doc, id)}“${all[index].name}”$note")
    }

    /** The timeline the open compound clip was opened from, opened again. */
    fun back(doc: ProjectDocument): Result {
        val back = doc.compoundTrail.lastOrNull() ?: throw EditRejected("没有可以返回的上一级时间线")
        val all = timelines.all(doc)
        val index = all.indexOfFirst { it.id == back }.takeIf { it >= 0 } ?: throw EditRejected("上一级时间线已不存在")
        val opened = timelines.opened(doc, all, index).copy(compoundTrail = doc.compoundTrail.dropLast(1))
        return Result(opened, null, "已返回“${all[index].name}”")
    }

    /** The names along the way to the open timeline, outermost first, the open one last (`compound-trail`). */
    fun trail(doc: ProjectDocument): List<String> =
        doc.compoundTrail.mapNotNull { id -> doc.storedTimeline(id)?.name } + doc.timelineName

    /**
     * Compound clip [clipId] (and its linked part) replaced by what it shows of its timeline: each track's clips cut to
     * the clip's window, the lowest on the clip's track and the others on the tracks above (video) or below (audio) —
     * added when missing. The compound's timeline stays in the project for other clips and the media pool.
     */
    fun decompose(doc: ProjectDocument, clipId: String): Result {
        val t = doc.timeline
        val clip = t.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        val id = clip.compoundId ?: throw EditRejected("“${clip.name}”不是复合片段")
        val group = t.linkedGroup(clip).filter { it.compoundId == id }
        t.requireUnlocked(group, clip)
        val stored = doc.storedTimeline(id) ?: throw EditRejected("找不到复合片段“${clip.name}”的内容")
        val tracks = t.tracks.toMutableList()
        val channels = doc.mixer.channels.toMutableList()
        val placed = mutableListOf<Clip>()
        val newIds = mutableMapOf<String, String>()
        val newLinks = mutableMapOf<String, String>()
        val others = t.clips.filterNot { it in group }
        // A multicam clip flattens to the angle each part plays (Resolve's Flatten Multicam Clip), on the part's own track.
        val multicam = id in doc.multicamIds
        for (part in group) {
            val kind = if (part.kind == ClipKind.AUDIO) TrackKind.AUDIO else TrackKind.VIDEO
            val all = levels(stored.timeline, kind)
            val inners = if (multicam) listOfNotNull(all.getOrNull(part.angle)) else all
            val pieces = inners.map { inner -> window(part, stored, inner.id, doc.format.fps) }
            pieces.forEachIndexed { level, list ->
                if (list.isEmpty()) return@forEachIndexed
                val target = trackAt(tracks, t.track(part.trackId) ?: return@forEachIndexed, level)
                    ?: added(tracks, channels, kind) ?: throw EditRejected("${kindLabel(kind)}轨道已满，无法分解“${clip.name}”")
                if (target.locked) throw EditRejected("${target.name} 已锁定，无法分解“${clip.name}”")
                if (others.any { it.trackId == target.id && it.end > part.start && it.start < part.end }) {
                    throw EditRejected("${target.name} 在这段时间里还有其他片段，无法分解“${clip.name}”")
                }
                placed += list.map { c ->
                    c.copy(
                        id = newIds.getOrPut(c.id) { ids.next("c") },
                        trackId = target.id,
                        linkId = c.linkId?.let { link -> newLinks.getOrPut(link) { ids.next("l") } },
                    )
                }
            }
        }
        val changed = doc.copy(timeline = t.copy(tracks = tracks, clips = others + placed), mixer = doc.mixer.copy(channels = channels))
        val message = if (multicam) "已展平“${clip.name}”：换成所选机位的 ${placed.size} 个片段" else "已分解“${clip.name}”：放回 ${placed.size} 个片段"
        return Result(changed, placed.firstOrNull { it.kind != ClipKind.AUDIO }?.id ?: placed.firstOrNull()?.id, message)
    }

    /**
     * Compound clip [clip]'s contents in its window as a miniature (`timeline` part compound): one row per track holding
     * some (the top one first), each clip where it lies across the clip, 0…1; null for any other clip.
     */
    fun bars(doc: ProjectDocument, clip: Clip): List<TimelinePoolUseCase.Bar>? {
        val stored = doc.storedTimeline(clip.compoundId ?: return null) ?: return null
        val innerFps = stored.format.fps.coerceAtLeast(1)
        val from = (clip.sourceInMs * innerFps / 1000.0).roundToInt()
        val until = from + (clip.durationFrames * innerFps / doc.format.fps.coerceAtLeast(1).toDouble()).roundToInt()
        val length = (until - from).coerceAtLeast(1).toFloat()
        val kind = if (clip.kind == ClipKind.AUDIO) TrackKind.AUDIO else TrackKind.VIDEO
        val tracks = if (stored.id in doc.multicamIds) listOfNotNull(levels(stored.timeline, kind).getOrNull(clip.angle))
        else stored.timeline.tracks.filter { it.kind == kind }
        val rows = tracks
            .map { tr -> stored.timeline.clipsOn(tr.id).filter { it.end > from && it.start < until } }
            .filter { it.isNotEmpty() }
        return rows.flatMapIndexed { row, clips ->
            clips.map { TimelinePoolUseCase.Bar(row, (max(it.start, from) - from) / length, (min(it.end, until) - from) / length, it.kind) }
        }
    }

    /** What `compound-panel` shows of compound clip [clip]; null for any other clip. */
    fun info(doc: ProjectDocument, clip: Clip): Info? {
        val stored = doc.storedTimeline(clip.compoundId ?: return null) ?: return null
        val inside = stored.timeline
        fun used(kind: TrackKind) = inside.tracks.count { tr -> tr.kind == kind && inside.clips.any { it.trackId == tr.id } }
        return Info(stored.name, used(TrackKind.VIDEO), used(TrackKind.AUDIO), inside.clips.size, inside.durationFrames, stored.format.fps, stored.id !in doc.compoundIds)
    }

    /** [part]'s window of [stored]'s track [innerTrackId]: the clips it shows, cut to it and placed at its frames. */
    private fun window(part: Clip, stored: StoredTimeline, innerTrackId: String, fps: Int): List<Clip> {
        val innerFps = stored.format.fps.coerceAtLeast(1)
        val from = (part.sourceInMs * innerFps / 1000.0).roundToInt()
        val until = from + (part.durationFrames * innerFps / fps.toDouble()).roundToInt()
        return stored.timeline.clipsOn(innerTrackId).filter { it.end > from && it.start < until }.map { ic ->
            val p = ic.piece(max(ic.start, from), min(ic.end, until), innerFps)
            p.copy(
                start = part.start + ((p.start - from) * fps / innerFps.toDouble()).roundToInt(),
                durationFrames = (p.durationFrames * fps / innerFps.toDouble()).roundToInt().coerceAtLeast(1),
            )
        }
    }

    /** The parent track [level] steps above (video) or below (audio) [base], among tracks of its kind; null past the last. */
    private fun trackAt(tracks: List<Track>, base: Track, level: Int): Track? {
        val same = tracks.filter { it.kind == base.kind }
        val at = same.indexOfFirst { it.id == base.id }
        val i = if (base.kind == TrackKind.VIDEO) at - level else at + level
        return same.getOrNull(i)
    }

    /** A new track of [kind] on top of the video stack / below the audio ones, with its channel; null when there is no room. */
    private fun added(tracks: MutableList<Track>, channels: MutableList<MixerChannel>, kind: TrackKind): Track? {
        val timeline = Timeline(tracks.toList(), emptyList(), emptyList())
        if (!TracksUseCase.canAdd(timeline, kind)) return null
        val track = timeline.nextTrack(kind)
        val placed = timeline.withTrack(track).tracks
        tracks.clear()
        tracks += placed
        if (kind == TrackKind.AUDIO) channels += MixerChannel(track.id)
        return track
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
                return "${track.name} 在所选片段之间还有没选中的片段：一起选中，或先移开"
            }
        }
        return null
    }

    /** The clips with their linked partners, each once, in timeline order. */
    private fun withPartners(timeline: Timeline, clipIds: Collection<String>): List<Clip> =
        clipIds.mapNotNull(timeline::clip).flatMap { timeline.linkedGroup(it) }.distinctBy { it.id }.sortedWith(compareBy({ it.start }, { it.trackId }))

    /** "复合片段 N", one past the highest such number. */
    private fun nextName(all: List<StoredTimeline>): String {
        val highest = all.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val taken = all.map { it.name }.toSet()
        return generateSequence(highest + 1) { it + 1 }.map { "复合片段 $it" }.first { it !in taken }
    }

    private fun kindLabel(kind: TrackKind) = if (kind == TrackKind.AUDIO) "音频" else "视频"

    companion object {
        /** How deep compound clips inside compound clips play. */
        const val MAX_DEPTH = 8
        private val NUMBERED = Regex("复合片段 (\\d+)")

        /**
         * [doc] as it plays: every compound clip of the open timeline replaced by what it shows of its timeline — compound
         * clips inside it too, [MAX_DEPTH] deep, a timeline never inside itself. The lowest track of the contents lands on the
         * clip's track, the others on extra tracks right above it (video, "V1^1") or below it (audio, "A1^1", playing through a
         * copy of the clip's channel); a disabled inner track or a muted inner channel stays silent, an inner fader and the
         * clip's own volume add to the sound. A timeline without compound clips is returned as it is.
         */
        fun expanded(doc: ProjectDocument): ProjectDocument {
            if (doc.timeline.clips.none { it.isCompound }) return doc
            val (timeline, mixer) = flatten(doc, doc.timeline, doc.mixer, doc.format.fps, setOf(doc.timelineId), 0)
            return doc.copy(timeline = timeline, mixer = mixer)
        }

        @Suppress("LongParameterList")
        private fun flatten(doc: ProjectDocument, timeline: Timeline, mixer: MixerState, fps: Int, stack: Set<String>, depth: Int): Pair<Timeline, MixerState> {
            val compounds = timeline.clips.filter { it.isCompound }
            if (compounds.isEmpty()) return timeline to mixer
            val tracks = timeline.tracks.toMutableList()
            val clips = timeline.clips.filterNot { it.isCompound }.toMutableList()
            val channels = mixer.channels.toMutableList()
            for (c in compounds) {
                if (!c.enabled) continue
                val stored = doc.storedTimeline(c.compoundId) ?: continue
                if (stored.id in stack || depth >= MAX_DEPTH) continue
                val parentTrack = timeline.track(c.trackId) ?: continue
                val innerFps = stored.format.fps.coerceAtLeast(1)
                val (inner, innerMixer) = flatten(doc, stored.timeline, stored.mixer, innerFps, stack + stored.id, depth + 1)
                val kind = if (c.kind == ClipKind.AUDIO) TrackKind.AUDIO else TrackKind.VIDEO
                val from = (c.sourceInMs * innerFps / 1000.0).roundToInt()
                val until = from + (c.durationFrames * innerFps / fps.toDouble()).roundToInt()
                // A multicam clip plays one angle — its own track's — on the clip's track.
                val multicam = stored.id in doc.multicamIds
                levels(inner, kind).forEachIndexed { level, innerTrack ->
                    if (multicam && level != c.angle) return@forEachIndexed
                    if (!innerTrack.enabled || (kind == TrackKind.AUDIO && !innerMixer.isAudible(innerTrack.id))) return@forEachIndexed
                    // The inner track's fader and its bus's (a bus at the bottom silences it, as isAudible does a mute).
                    if (kind == TrackKind.AUDIO && innerMixer.laneGain(innerTrack.id) <= 0f) return@forEachIndexed
                    val gain = if (kind == TrackKind.AUDIO) innerMixer.channel(innerTrack.id).volumeDb + (innerMixer.busOf(innerTrack.id)?.volumeDb ?: 0f) + c.volumeDb else 0f
                    val target = if (level == 0 || multicam) parentTrack.id else virtualTrack(tracks, channels, mixer, parentTrack, level)
                    for (ic in inner.clipsOn(innerTrack.id)) {
                        if (!ic.enabled || ic.end <= from || ic.start >= until) continue
                        val p = ic.piece(max(ic.start, from), min(ic.end, until), innerFps)
                        // The compound clip's own fades fade what starts or ends with it; its transition and cross fade
                        // open its lowest track.
                        val head = p.start == from
                        val tail = p.end == until
                        val opens = head && (level == 0 || multicam)
                        clips += p.copy(
                            id = "${c.id}/${ic.id}",
                            trackId = target,
                            start = c.start + ((p.start - from) * fps / innerFps.toDouble()).roundToInt(),
                            durationFrames = (p.durationFrames * fps / innerFps.toDouble()).roundToInt().coerceAtLeast(1),
                            linkId = p.linkId?.let { "${c.id}/$it" },
                            volumeDb = p.volumeDb + gain,
                            volumeKeyframes = if (gain == 0f) p.volumeKeyframes else p.volumeKeyframes.map { it.copy(volumeDb = it.volumeDb + gain) },
                            fadeInSeconds = if (head && c.fadeInSeconds > 0f) c.fadeInSeconds else p.fadeInSeconds,
                            fadeInCurve = if (head && c.fadeInSeconds > 0f) c.fadeInCurve else p.fadeInCurve,
                            fadeOutSeconds = if (tail && c.fadeOutSeconds > 0f) c.fadeOutSeconds else p.fadeOutSeconds,
                            fadeOutCurve = if (tail && c.fadeOutSeconds > 0f) c.fadeOutCurve else p.fadeOutCurve,
                            transitionIn = if (opens && c.transitionIn != null) c.transitionIn else p.transitionIn,
                            crossFadeFrames = if (opens && c.crossFadeFrames > 0) c.crossFadeFrames else p.crossFadeFrames,
                            // A compound sound's pitch shifts its contents' on top of their own.
                            pitchCents = if (kind == TrackKind.AUDIO) p.pitchCents + c.pitchCents else p.pitchCents,
                        )
                    }
                }
            }
            return timeline.copy(tracks = tracks, clips = clips) to mixer.copy(channels = channels)
        }

        /** Extra track [level] above (video) or below (audio) [parent] for a compound clip's upper contents, added once. */
        private fun virtualTrack(tracks: MutableList<Track>, channels: MutableList<MixerChannel>, mixer: MixerState, parent: Track, level: Int): String {
            val id = "${parent.id}^$level"
            if (tracks.none { it.id == id }) {
                val beside = tracks.indexOfFirst { it.id == if (level == 1) parent.id else "${parent.id}^${level - 1}" }
                val track = Track(id, "${parent.name}^$level", parent.kind, enabled = parent.enabled)
                if (parent.kind == TrackKind.VIDEO) tracks.add(beside, track) else tracks.add(beside + 1, track)
                if (parent.kind == TrackKind.AUDIO) channels += mixer.channel(parent.id).copy(trackId = id)
            }
            return id
        }

        /** The tracks of [kind] of [timeline], the lowest first: V1 up, A1 down. */
        internal fun levels(timeline: Timeline, kind: TrackKind): List<Track> =
            timeline.tracks.filter { it.kind == kind }.let { if (kind == TrackKind.VIDEO) it.reversed() else it }
    }
}
