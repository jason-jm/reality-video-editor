package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import kotlin.math.abs

/**
 * Resolve's Import → Timeline → EDL: a CMX 3600 edit decision list becomes a new timeline, opened. Each event's source is
 * found in the media pool by its "* SOURCE FILE:" comment, else its "* FROM CLIP NAME:", else its reel name; V lands on V1,
 * A / A2 on A1 / A2 (B on V1 and A1, AA on both sound tracks). A D (or wipe) event is a dissolve starting at its edit point
 * — a cross fade on sound — and an M2 line the clip's speed (negative: reversed). Picture and sound of one source over the
 * same span are linked. Events whose source is not in the pool, other channels, and overlaps are left out and named.
 */
class EdlImportUseCase(private val ids: IdGenerator, private val timelines: TimelinesUseCase = TimelinesUseCase(ids)) {

    data class Result(val doc: ProjectDocument, val message: String, val clips: Int, val left: List<String>)

    /** One event as read: its lines' channel, kind (C or a transition of [length] frames), times and comments. */
    data class Event(
        val number: String,
        val reel: String,
        val channel: String,
        val transition: Boolean,
        val length: Int,
        val srcIn: Int,
        val srcOut: Int,
        val recIn: Int,
        val recOut: Int,
        val clipName: String? = null,
        val sourceFile: String? = null,
        val rate: Double? = null,
    )

    /** The events of [text] with their comments, and its TITLE. */
    fun parse(text: String, fps: Int): Pair<String?, List<Event>> {
        var title: String? = null
        val events = mutableListOf<Event>()
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("TITLE:")) title = line.removePrefix("TITLE:").trim().ifEmpty { null }
            EVENT.matchEntire(line)?.let { m ->
                val (n, reel, channel, kind, length, si, so, ri, ro) = m.destructured
                events += Event(n, reel, channel.uppercase(), kind != "C", length.toIntOrNull() ?: 0, tc(si, fps), tc(so, fps), tc(ri, fps), tc(ro, fps))
                return@let
            }
            val last = events.lastOrNull() ?: continue
            // Comments and M2 belong to every line of the event they follow.
            fun update(change: (Event) -> Event) = events.indices.filter { events[it].number == last.number }.forEach { events[it] = change(events[it]) }
            when {
                line.startsWith("* FROM CLIP NAME:") -> line.substringAfter(':').trim().let { v -> update { it.copy(clipName = v) } }
                line.startsWith("* SOURCE FILE:") -> line.substringAfter(':').trim().let { v -> update { it.copy(sourceFile = v) } }
                else -> M2.matchEntire(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { r -> update { it.copy(rate = r) } }
            }
        }
        return title to events
    }

    operator fun invoke(doc: ProjectDocument, text: String, fileName: String): Result {
        val fps = doc.format.fps.coerceAtLeast(1)
        val (title, events) = parse(text, fps)
        if (events.isEmpty()) throw EditRejected("文件里没有可以识别的 EDL 事件")
        val left = mutableListOf<String>()
        var timeline = Timeline.EMPTY
        // The zero-length line before a transition (the outgoing held at its start) makes no clip.
        for (e in events.filter { it.recOut > it.recIn }) {
            val tracks = when (e.channel) {
                "V" -> listOf(Timeline.V1)
                "A", "A1" -> listOf(Timeline.A1)
                "A2" -> listOf(Timeline.A2)
                "AA" -> listOf(Timeline.A1, Timeline.A2)
                "B" -> listOf(Timeline.V1, Timeline.A1)
                else -> {
                    left += "事件 ${e.number}：${e.channel} 通道没有对应的轨道"
                    continue
                }
            }
            if (e.reel == "BL" || e.reel == "BLK") continue
            for (trackId in tracks) {
                val sound = trackId != Timeline.V1
                val asset = find(doc, e, sound)
                if (asset == null) {
                    left += "事件 ${e.number}：媒体池里没有“${e.sourceFile ?: e.clipName ?: e.reel}”"
                    continue
                }
                val clips = timeline.clipsOn(trackId)
                if (clips.any { it.start < e.recOut && it.end > e.recIn }) {
                    left += "事件 ${e.number}：与前面的片段重叠"
                    continue
                }
                val rate = e.rate?.let { it / fps }?.takeIf { abs(it) in ClipSpeed.MIN.toDouble()..ClipSpeed.MAX.toDouble() }
                val follows = clips.any { it.end == e.recIn }
                val frames = e.recOut - e.recIn
                val clip = Clip(
                    id = ids.next("c"),
                    trackId = trackId,
                    kind = if (sound) ClipKind.AUDIO else ClipKind.VIDEO,
                    name = e.clipName ?: asset.name,
                    start = e.recIn,
                    durationFrames = frames,
                    assetId = asset.id,
                    // Rounded down, as the app stores a frame's time (frame 248 at 30 fps is 8266 ms): the players show the first
                    // frame at or after it, and a rounded-up 8267 was the next frame (R20).
                    sourceInMs = if (asset.kind == MediaKind.IMAGE) 0 else e.srcIn * 1000L / fps,
                    speed = rate?.let { ClipSpeed(abs(it).toFloat()) } ?: ClipSpeed.NORMAL,
                    reverse = rate != null && rate < 0,
                    transitionIn = if (e.transition && follows && !sound) Transition(TransitionType.CROSS_DISSOLVE, minOf(e.length, frames), alignment = TransitionAlignment.START) else null,
                    crossFadeFrames = if (e.transition && follows && sound) minOf(e.length, frames) else 0,
                )
                timeline = timeline.copy(clips = timeline.clips + clip)
            }
        }
        if (timeline.clips.isEmpty()) throw EditRejected("EDL 里没有能在媒体池中找到的素材：${left.firstOrNull() ?: ""}")
        timeline = linked(timeline)
        val name = title ?: fileName.substringBeforeLast('.').ifEmpty { "导入的 EDL" }
        val added = timelines.add(doc, name, timeline)
        val note = if (left.isEmpty()) "" else "；${left.size} 个未导入"
        return Result(added.doc, "已从 EDL 导入 ${timeline.clips.size} 个片段，${added.message.removePrefix("已")}$note", timeline.clips.size, left)
    }

    /** The pool's media for [e]: by file name, then clip name, then reel — a picture for V, something with sound for A. */
    private fun find(doc: ProjectDocument, e: Event, sound: Boolean): MediaAsset? {
        val fits = doc.media.filter { a -> !a.offline && if (sound) a.kind == MediaKind.AUDIO || a.hasAudio else a.kind != MediaKind.AUDIO }
        fun file(a: MediaAsset) = a.uri.substringAfterLast('/')
        return e.sourceFile?.let { f -> fits.firstOrNull { file(it) == f || it.name == f } }
            ?: e.clipName?.let { n -> fits.firstOrNull { it.name == n || it.name.substringBeforeLast('.') == n } }
            ?: e.reel.takeIf { it != "AX" }?.let { r -> fits.firstOrNull { it.name.substringBeforeLast('.').equals(r, ignoreCase = true) } }
    }

    /** Picture and sound of one source over one span, linked. */
    private fun linked(timeline: Timeline): Timeline {
        val links = mutableMapOf<String, String>()
        val pictures = timeline.clips.filter { it.kind == ClipKind.VIDEO }
        for (p in pictures) {
            val s = timeline.clips.firstOrNull { it.kind == ClipKind.AUDIO && it.assetId == p.assetId && it.start == p.start && it.durationFrames == p.durationFrames && it.id !in links }
                ?: continue
            val id = ids.next("l")
            links[p.id] = id
            links[s.id] = id
        }
        return timeline.copy(clips = timeline.clips.map { c -> links[c.id]?.let { c.copy(linkId = it) } ?: c })
    }

    private fun tc(text: String, fps: Int): Int {
        val (h, m, s, f) = text.split(':', ';').map { it.toInt() }
        return ((h * 60 + m) * 60 + s) * fps + f
    }

    private companion object {
        val EVENT = Regex("""^(\d{3,})\s+(\S+)\s+(\S+)\s+(C|D|W\d{3})\s+(\d{3})?\s*(\d\d:\d\d:\d\d[:;]\d\d)\s+(\d\d:\d\d:\d\d[:;]\d\d)\s+(\d\d:\d\d:\d\d[:;]\d\d)\s+(\d\d:\d\d:\d\d[:;]\d\d)\s*$""")
        val M2 = Regex("""^M2\s+\S+\s+(-?\d+(?:\.\d+)?)\s+\d\d:\d\d:\d\d[:;]\d\d\s*$""")
    }
}
