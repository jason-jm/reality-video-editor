package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import org.w3c.dom.Element
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Resolve's Import → Timeline → FCPXML: the first sequence of an FCPXML file (Final Cut Pro, Resolve, Premiere, or Prism's
 * own export) becomes a new timeline, opened. The primary storyline goes on V1; connected clips and secondary storylines
 * by lane — pictures up from V1, sound down from A1 — each moved up a track when it would overlap (three of each at most).
 * A clip's own sound lands on the sound tracks linked to its picture (unless srcEnable says only one of them), media are
 * found in the pool by file name, a time map becomes a speed, a reverse or speed points, a storyline transition a dissolve
 * aligned as it sits on the cut, titles keep their words, size and nearest colour, volume and fades stay, markers and
 * switched-off clips too. Compound and multicam clips, media not in the pool and what does not fit are left out and named.
 */
class FcpxmlImportUseCase(private val ids: IdGenerator, private val timelines: TimelinesUseCase = TimelinesUseCase(ids)) {

    data class Result(val doc: ProjectDocument, val message: String, val clips: Int, val left: List<String>)

    /** Where an element's time starts: on the timeline ([at], seconds) and in its own time ([start]). */
    private data class Frame(val at: Double, val start: Double)

    private class Pending(val clip: Clip, val wanted: Int, val sound: Boolean, val linkKey: String?)

    operator fun invoke(doc: ProjectDocument, text: String, fileName: String): Result {
        val fps = doc.format.fps.coerceAtLeast(1)
        val root = parse(text)
        if (root.tagName != "fcpxml") throw EditRejected("不是 FCPXML 文件")
        val resources = root.child("resources")?.children().orEmpty().associateBy { it.getAttribute("id") }
        val sequence = root.getElementsByTagName("sequence").item(0) as? Element ?: throw EditRejected("FCPXML 里没有时间线（sequence）")
        val project = (sequence.parentNode as? Element)?.takeIf { it.tagName == "project" }
        val tcStart = time(sequence.getAttribute("tcStart"))
        val spine = sequence.child("spine") ?: throw EditRejected("FCPXML 的时间线是空的")

        val pending = mutableListOf<Pending>()
        val markers = mutableListOf<Marker>()
        val left = mutableListOf<String>()
        val missing = linkedSetOf<String>()
        var unsupported = 0
        var looks = 0
        fun frames(sec: Double) = (sec * fps).roundToInt()

        /** One media element (asset-clip, video, audio, or a clip's inner media) placed at [at] on [lane]. */
        fun media(e: Element, ref: Element, at: Double, start: Double, duration: Double, lane: Int, name: String, enabled: Boolean, only: String?) {
            val file = mediaName(ref)
            val hasVideo = ref.getAttribute("hasVideo") != "0" && only != "audio"
            val hasAudio = (ref.getAttribute("hasAudio") == "1" || ref.getAttribute("hasVideo") == "0") && only != "video"
            val picture = if (hasVideo) find(doc, file, ref.getAttribute("name"), sound = false) else null
            val sound = if (hasAudio) find(doc, file, ref.getAttribute("name"), sound = true) else null
            if (picture == null && sound == null) {
                missing += file.ifEmpty { ref.getAttribute("name") }
                return
            }
            if (e.children().any { it.tagName in LOOKS }) looks++
            val retime = retime(e.child("timeMap"), start, duration, fps)
            val volume = e.child("adjust-volume")
            val db = volume?.getAttribute("amount")?.removeSuffix("dB")?.trim()?.toFloatOrNull() ?: 0f
            val param = volume?.child("param")
            val fadeIn = param?.child("fadeIn")?.let { time(it.getAttribute("duration")).toFloat() } ?: 0f
            val fadeOut = param?.child("fadeOut")?.let { time(it.getAttribute("duration")).toFloat() } ?: 0f
            val key = if (picture != null && sound != null) ids.next("l") else null
            fun clip(asset: MediaAsset, kind: ClipKind) = Clip(
                id = ids.next("c"),
                trackId = "",
                kind = kind,
                name = name.ifEmpty { asset.name },
                start = frames(at),
                durationFrames = frames(duration).coerceAtLeast(1),
                assetId = asset.id,
                // Rounded down, as the app stores a frame's time: the players show the first frame at or after it (R20).
                sourceInMs = if (asset.kind == MediaKind.IMAGE) 0 else floor(retime.inSec * 1000 + 1e-6).toLong().coerceAtLeast(0),
                speed = retime.speed,
                reverse = retime.reverse,
                speedSections = retime.sections,
                enabled = enabled,
                volumeDb = if (kind == ClipKind.AUDIO) db else 0f,
                fadeInSeconds = if (kind == ClipKind.AUDIO) fadeIn else 0f,
                fadeOutSeconds = if (kind == ClipKind.AUDIO) fadeOut else 0f,
            )
            picture?.let { pending += Pending(clip(it, ClipKind.VIDEO), if (lane < 0) 2 else lane + 1, false, key) }
            sound?.let { pending += Pending(clip(it, ClipKind.AUDIO), if (lane < 0) -lane else 1, true, key) }
        }

        fun title(e: Element, at: Double, duration: Double, lane: Int) {
            val words = e.child("text")?.textContent?.trim().orEmpty()
            val style = e.child("text-style-def")?.child("text-style")
            val size = style?.getAttribute("fontSize")?.toFloatOrNull()?.roundToInt()?.coerceIn(12, 400) ?: 72
            val color = style?.getAttribute("fontColor")?.let(::nearestColor) ?: TitleColor.WHITE
            val clip = Clip(
                id = ids.next("c"), trackId = "", kind = ClipKind.TITLE, name = e.getAttribute("name").ifEmpty { words.take(20) },
                start = frames(at), durationFrames = frames(duration).coerceAtLeast(1),
                title = TitleStyle(words.ifEmpty { e.getAttribute("name") }, sizePx = size, color = color),
                enabled = e.getAttribute("enabled") != "0",
            )
            pending += Pending(clip, if (lane < 0) 2 else lane + 1, false, null)
        }

        /** [e]'s markers and connected items, its own time starting at [frame]. */
        fun attached(e: Element, frame: Frame, walk: (Element, Frame, Int) -> Unit) {
            for (child in e.children()) {
                when (child.tagName) {
                    "marker", "chapter-marker" -> {
                        val at = frame.at + time(child.getAttribute("start")) - frame.start
                        markers += Marker(ids.next("mk"), frames(at), child.getAttribute("value"), note = child.getAttribute("note"),
                            durationFrames = frames(time(child.getAttribute("duration"))).takeIf { it > 1 } ?: 0)
                    }
                    // A secondary storyline: its items are in the same time as the clip it hangs from.
                    "spine" -> child.children().forEach { walk(it, frame, child.lane()) }
                    else -> if (child.hasAttribute("lane")) walk(child, frame, child.lane())
                }
            }
        }

        val transitions = mutableListOf<Pair<Double, Double>>()
        fun walk(e: Element, parent: Frame, lane: Int) {
            val at = parent.at + time(e.getAttribute("offset")) - parent.start
            val start = time(e.getAttribute("start"))
            val duration = time(e.getAttribute("duration"))
            val own = Frame(at, start)
            val enabled = e.getAttribute("enabled") != "0"
            when (e.tagName) {
                "asset-clip" -> resources[e.getAttribute("ref")]?.let { media(e, it, at, start, duration, lane, e.getAttribute("name"), enabled, e.getAttribute("srcEnable").ifEmpty { null }) }
                "video", "audio" -> resources[e.getAttribute("ref")]?.let { media(e, it, at, start, duration, lane, e.getAttribute("name"), enabled, e.tagName) }
                "clip" -> {
                    // A clip wraps its media in its own storyline: the first video (or asset-clip) and audio inside, in its time.
                    val inner = e.children().filter { !it.hasAttribute("lane") && it.tagName in setOf("video", "audio", "asset-clip") }
                    for (m in inner) {
                        val ref = resources[m.getAttribute("ref")] ?: continue
                        val innerStart = time(m.getAttribute("start")) + (start - time(m.getAttribute("offset")))
                        media(m, ref, at, innerStart, duration, lane, e.getAttribute("name"), enabled, m.tagName.takeIf { it != "asset-clip" })
                    }
                }
                "title" -> title(e, at, duration, lane)
                "gap" -> Unit
                "transition" -> if (lane == 0) transitions += at to duration
                "ref-clip", "sync-clip", "mc-clip", "multicam-clip" -> unsupported++
                else -> Unit
            }
            if (e.tagName != "transition") attached(e, own, ::walk)
        }
        spine.children().forEach { walk(it, Frame(-tcStart, 0.0), 0) }

        // Tracks: pictures from V1 up, sound from A1 down, each clip on its lane's track or the next free one above it.
        var timeline = Timeline.EMPTY
        val placed = mutableListOf<Clip>()
        val links = mutableMapOf<String, String>()
        var noRoom = 0
        for (p in pending.sortedWith(compareBy({ it.sound }, { it.wanted }, { it.clip.start }))) {
            val kind = if (p.sound) TrackKind.AUDIO else TrackKind.VIDEO
            val prefix = if (p.sound) "a" else "v"
            val track = (p.wanted.coerceIn(1, MAX)..MAX).map { "$prefix$it" }.firstOrNull { id ->
                placed.none { it.trackId == id && it.start < p.clip.end && it.end > p.clip.start }
            }
            if (track == null) {
                noRoom++
                continue
            }
            if (timeline.tracks.none { it.id == track }) timeline = timeline.withTrack(Track(track, track.uppercase(), kind))
            val clip = p.clip.copy(trackId = track)
            placed += clip
            p.linkKey?.let { links[clip.id] = it }
        }
        // A picture's own sound shares its link; so do a picture and a sound of one file over one span (separate in the file).
        val linkOf = links.toMutableMap()
        for (pic in placed.filter { it.kind == ClipKind.VIDEO && it.id !in linkOf }) {
            val snd = placed.firstOrNull { it.kind == ClipKind.AUDIO && it.id !in linkOf && it.assetId == pic.assetId && it.start == pic.start && it.durationFrames == pic.durationFrames }
                ?: continue
            val id = ids.next("l")
            linkOf[pic.id] = id
            linkOf[snd.id] = id
        }
        var clips = placed.map { c -> linkOf[c.id]?.let { c.copy(linkId = it) } ?: c }
        // Storyline transitions: a dissolve into the V1 clip at the cut it straddles, aligned as it sits on it.
        for ((at, duration) in transitions) {
            val from = frames(at)
            val length = frames(duration)
            val incoming = clips.filter { it.trackId == Timeline.V1 && it.kind != ClipKind.AUDIO }
                .minByOrNull { c -> abs(c.start - (from + length / 2)) }
                ?: continue
            if (clips.none { it.trackId == Timeline.V1 && it.end == incoming.start } || length <= 0) continue
            val alignment = when {
                abs(from - incoming.start) <= 1 -> TransitionAlignment.START
                abs(from + length - incoming.start) <= 1 -> TransitionAlignment.END
                else -> TransitionAlignment.CENTER
            }
            clips = clips.map { if (it.id == incoming.id) it.copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, length, alignment = alignment)) else it }
        }
        if (clips.isEmpty()) {
            throw EditRejected("FCPXML 里没有能在媒体池中找到的素材" + (missing.firstOrNull()?.let { "：媒体池里没有“$it”" } ?: ""))
        }
        timeline = timeline.copy(clips = clips, markers = markers.filter { it.frame >= 0 }.sortedBy { it.frame })

        if (missing.isNotEmpty()) left += "${missing.size} 个素材不在媒体池里（${missing.take(3).joinToString("、")}${if (missing.size > 3) " 等" else ""}）"
        if (unsupported > 0) left += "$unsupported 个复合或多机位片段"
        if (noRoom > 0) left += "$noRoom 个片段（重叠处每类最多 $MAX 条轨道）"
        if (looks > 0) left += "$looks 个片段的效果与变换"
        val name = project?.getAttribute("name")?.ifEmpty { null } ?: fileName.substringBeforeLast('.').ifEmpty { "导入的 FCPXML" }
        val added = timelines.add(doc, name, timeline)
        val note = if (left.isEmpty()) "" else "；未导入：" + left.joinToString("、") { it.substringBefore("（") }
        return Result(added.doc, "已从 FCPXML 导入 ${clips.size} 个片段，${added.message.removePrefix("已")}$note", clips.size, left)
    }

    /** A time map read over a clip's own time [start] … [start] + [duration]: where its source starts, at what speed(s). */
    private data class Retime(val inSec: Double, val speed: ClipSpeed = ClipSpeed.NORMAL, val reverse: Boolean = false, val sections: List<SpeedSection> = emptyList())

    private fun retime(map: Element?, start: Double, duration: Double, fps: Int): Retime {
        val points = map?.children()?.filter { it.tagName == "timept" }?.map { time(it.getAttribute("time")) to time(it.getAttribute("value")) }.orEmpty()
        if (points.size < 2 || duration <= 0) return Retime(start)
        fun value(t: Double): Double {
            val i = points.indexOfLast { it.first <= t }.coerceIn(0, points.size - 2)
            val (t0, v0) = points[i]
            val (t1, v1) = points[i + 1]
            return if (t1 == t0) v0 else v0 + (v1 - v0) * (t - t0) / (t1 - t0)
        }
        val from = value(start)
        val to = value(start + duration)
        val reverse = to < from
        val speed = (abs(to - from) / duration).toFloat().coerceIn(ClipSpeed.MIN, ClipSpeed.MAX)
        val inner = points.map { it.first }.filter { it > start && it < start + duration }
        // Speed points: the sections between the map's points, when they differ (forwards only).
        val bounds = listOf(start) + inner + (start + duration)
        val speeds = bounds.zipWithNext { a, b -> abs(value(b) - value(a)) / (b - a) }
        val sections = if (!reverse && speeds.size > 1 && speeds.any { abs(it - speeds[0]) > 1e-3 }) {
            bounds.zipWithNext().mapIndexed { i, (a, b) -> SpeedSection(((b - a) * fps).roundToInt(), ClipSpeed(speeds[i].toFloat().coerceIn(ClipSpeed.MIN, ClipSpeed.MAX))) }
        } else emptyList()
        return Retime(minOf(from, to), if (sections.isEmpty()) ClipSpeed(speed) else ClipSpeed.NORMAL, reverse, sections)
    }

    /** The pool's media for a file: by file name, then name, then name without extension — a picture or something with sound. */
    private fun find(doc: ProjectDocument, file: String, name: String, sound: Boolean): MediaAsset? {
        val fits = doc.media.filter { a -> !a.offline && if (sound) a.kind == MediaKind.AUDIO || a.hasAudio else a.kind != MediaKind.AUDIO }
        fun bare(s: String) = s.substringBeforeLast('.').lowercase()
        return fits.firstOrNull { it.name == file || it.uri.substringAfterLast('/') == file }
            ?: fits.firstOrNull { name.isNotEmpty() && it.name == name }
            ?: fits.firstOrNull { file.isNotEmpty() && bare(it.name) == bare(file) }
            ?: fits.firstOrNull { name.isNotEmpty() && bare(it.name) == bare(name) }
    }

    /** The file an asset points at: its media-rep (or src) URL's last part, decoded. */
    private fun mediaName(asset: Element): String {
        val src = asset.child("media-rep")?.getAttribute("src")?.ifEmpty { null } ?: asset.getAttribute("src")
        return runCatching { URLDecoder.decode(src.substringAfterLast('/').replace("+", "%2B"), "UTF-8") }.getOrDefault(src.substringAfterLast('/'))
    }

    private fun nearestColor(rgba: String): TitleColor {
        val v = rgba.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
        if (v.size < 3) return TitleColor.WHITE
        return TitleColor.entries.minBy { c ->
            listOf(16, 8, 0).mapIndexed { i, s -> ((c.argb shr s) and 0xFF) / 255.0 - v[i] }.sumOf { it * it }
        }
    }

    private fun parse(text: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        // No outside files: the DOCTYPE names no DTD, and none is fetched.
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        factory.isExpandEntityReferences = false
        return try {
            factory.newDocumentBuilder().parse(text.byteInputStream(Charsets.UTF_8)).documentElement
        } catch (e: Exception) {
            throw EditRejected("无法读取 FCPXML：文件不是有效的 XML")
        }
    }

    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.child(tag: String): Element? = children().firstOrNull { it.tagName == tag }
    private fun Element.lane(): Int = getAttribute("lane").toIntOrNull() ?: 0

    companion object {
        /** Tracks of each kind (as [TracksUseCase.MAX_TRACKS]). */
        private const val MAX = TracksUseCase.MAX_TRACKS
        private val LOOKS = setOf("filter-video", "adjust-transform", "adjust-crop", "adjust-blend", "adjust-colorConform", "adjust-stabilization")

        /** "0s", "100/30s", "3.5s", "1001/30000s" → seconds. */
        fun time(text: String): Double {
            val t = text.trim().removeSuffix("s")
            if (t.isEmpty()) return 0.0
            val slash = t.indexOf('/')
            return if (slash < 0) t.toDoubleOrNull() ?: 0.0
            else (t.substring(0, slash).toDoubleOrNull() ?: 0.0) / (t.substring(slash + 1).toDoubleOrNull()?.takeIf { it != 0.0 } ?: 1.0)
        }
    }
}
