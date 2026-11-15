package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Resolve's Export → Timeline → FCPXML (1.10): the open timeline for Final Cut Pro, DaVinci Resolve or Premiere. FCPXML is
 * magnetic, so V1 becomes the primary storyline — its clips in order, gaps where it is empty — and every other track's clips
 * hang off it as connected clips: video tracks above V1 on lanes 1, 2 …, sound tracks below it on lanes −1, −2 …. V1's
 * transitions go in the storyline (as cross dissolves), a speed, a reverse or speed points as a time map, markers on the
 * storyline item under them, titles as Basic Titles with their words, size and colour, a sound clip's level and fades as
 * its volume, and disabled clips stay, switched off. Pictures carry only their picture (srcEnable="video") and sound clips
 * only their sound, as the timeline splits them. Media point at their files by path (file:///storage/emulated/0/…), for the
 * other editor to relink. What FCPXML cannot carry — grades, effects, transforms, keyframed levels, sound cross fades,
 * transitions off V1, subtitles — is counted in [Result.left].
 */
class FcpxmlExportUseCase {

    data class Result(val text: String, val clips: Int, val left: List<String>)

    /** A storyline item: a V1 clip or a gap, where it sits ([offset], [duration]) and its own time at its start ([start]). */
    private class Item(val offset: Int, val duration: Int, val start: Int, val clip: Clip?) {
        val children = mutableListOf<Pair<Int, String>>()
    }

    operator fun invoke(doc: ProjectDocument): Result {
        val fps = doc.format.fps.coerceAtLeast(1)
        val timeline = doc.timeline
        val x = Xml()
        val ids = Ids()
        fun t(frames: Int) = if (frames <= 0) "0s" else "$frames/${fps}s"
        fun frames(ms: Double) = (ms * fps / 1000.0).roundToInt()

        val formats = linkedMapOf<Triple<Int, Int, Int>, String>()
        fun format(w: Int, h: Int, rate: Int) = formats.getOrPut(Triple(w, h, rate)) { ids.next() }
        val sequenceFormat = format(doc.format.width, doc.format.height, fps)

        val exported = timeline.clips.filter { c -> c.kind == ClipKind.TITLE || doc.asset(c.assetId) != null }
            .filter { c -> timeline.tracks.firstOrNull { it.id == c.trackId }?.kind != TrackKind.SUBTITLE }
        val assets = linkedMapOf<String, String>()
        exported.mapNotNull { doc.asset(it.assetId) }.distinctBy { it.id }.forEach { assets[it.id] = ids.next() }
        val titleEffect = if (exported.any { it.kind == ClipKind.TITLE }) ids.next() else null
        val v1 = timeline.clipsOn(Timeline.V1).filter { it in exported }.sortedBy { it.start }
        val dissolve = if (v1.any { it.transitionIn != null }) ids.next() else null
        val assetFormats = doc.media.filter { it.id in assets && it.kind != MediaKind.AUDIO }.associate { a ->
            a.id to format(a.width.coerceAtLeast(1), a.height.coerceAtLeast(1), if (a.kind == MediaKind.IMAGE) 0 else a.frameRate.roundToInt().coerceAtLeast(1))
        }

        // The storyline: V1's clips in order, gaps between, to the timeline's end.
        val spine = mutableListOf<Item>()
        var cursor = 0
        for (c in v1) {
            if (c.start > cursor) spine += Item(cursor, c.start - cursor, 0, null)
            spine += Item(c.start, c.durationFrames, localStart(c, fps), c)
            cursor = maxOf(cursor, c.end)
        }
        if (timeline.durationFrames > cursor) spine += Item(cursor, timeline.durationFrames - cursor, 0, null)
        fun parentAt(frame: Int): Item? = spine.lastOrNull { it.offset <= frame } ?: spine.firstOrNull()

        // Connected clips: lanes up from V1 for picture tracks, down from it for sound.
        val videoTracks = timeline.tracks.filter { it.kind == TrackKind.VIDEO }
        val v1Index = videoTracks.indexOfFirst { it.id == Timeline.V1 }
        val audioTracks = timeline.tracks.filter { it.kind == TrackKind.AUDIO }
        val laneOf = videoTracks.mapIndexed { i, tr -> tr.id to (v1Index - i) }.toMap() + audioTracks.mapIndexed { i, tr -> tr.id to -(i + 1) }.toMap()
        var titleStyles = 0
        for (c in exported.filter { it.trackId != Timeline.V1 }.sortedBy { it.start }) {
            val lane = laneOf[c.trackId] ?: continue
            val parent = parentAt(c.start) ?: continue
            val offset = parent.start + (c.start - parent.offset)
            val body = Xml()
            if (c.kind == ClipKind.TITLE) {
                titleStyles++
                title(body, c, lane, t(offset), t(c.durationFrames), titleEffect!!, "ts$titleStyles")
            } else {
                clip(body, doc, c, lane, t(offset), assets.getValue(c.assetId!!), assetFormats[c.assetId], fps, ::t, ::frames)
            }
            parent.children += offset to body.text()
        }
        for (m in timeline.markers.sortedBy { it.frame }) {
            val parent = parentAt(m.frame) ?: continue
            val start = parent.start + (m.frame - parent.offset)
            val attrs = mutableListOf("start" to t(start), "duration" to t(maxOf(1, m.durationFrames)), "value" to m.label.ifEmpty { "标记" })
            if (m.note.isNotEmpty()) attrs += "note" to m.note
            parent.children += start to Xml().apply { empty("marker", attrs) }.text()
        }

        x.raw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        x.raw("<!DOCTYPE fcpxml>")
        x.open("fcpxml", listOf("version" to "1.10"))
        x.open("resources")
        for ((key, id) in formats) {
            val (w, h, rate) = key
            val attrs = mutableListOf("id" to id)
            if (rate > 0) attrs += "frameDuration" to "1/${rate}s" else attrs += "name" to "FFVideoFormatRateUndefined"
            attrs += listOf("width" to "$w", "height" to "$h", "colorSpace" to "1-1-1 (Rec. 709)")
            x.empty("format", attrs)
        }
        for ((assetId, id) in assets) {
            val a = doc.asset(assetId)!!
            val attrs = mutableListOf("id" to id, "name" to a.name, "start" to "0s",
                "duration" to if (a.kind == MediaKind.IMAGE) "0s" else t(frames(a.durationMs.toDouble())),
                "hasVideo" to if (a.kind == MediaKind.AUDIO) "0" else "1")
            assetFormats[a.id]?.let { attrs += "format" to it }
            if (a.hasAudio || a.kind == MediaKind.AUDIO) attrs += listOf("hasAudio" to "1", "audioSources" to "1", "audioChannels" to "2", "audioRate" to "48000")
            x.open("asset", attrs)
            x.empty("media-rep", listOf("kind" to "original-media", "src" to fileUrl(a)))
            x.close("asset")
        }
        titleEffect?.let { x.empty("effect", listOf("id" to it, "name" to "Basic Title", "uid" to BASIC_TITLE)) }
        dissolve?.let { x.empty("effect", listOf("id" to it, "name" to "Cross Dissolve", "uid" to CROSS_DISSOLVE)) }
        x.close("resources")
        x.open("library")
        x.open("event", listOf("name" to doc.name))
        x.open("project", listOf("name" to doc.timelineName))
        x.open("sequence", listOf("format" to sequenceFormat, "duration" to t(timeline.durationFrames), "tcStart" to "0s", "tcFormat" to "NDF",
            "audioLayout" to "stereo", "audioRate" to "48k"))
        x.open("spine")
        var mapped = 0
        for ((i, item) in spine.withIndex()) {
            val c = item.clip
            // A dissolve into this clip from the one just before it, in the storyline between them.
            if (c != null && i > 0 && spine[i - 1].clip?.end == c.start) {
                val span = TransitionHandles.span(doc, c, fps)
                if (span != null && span.frames > 0 && span.outgoing != null) {
                    if (span.transition.type != TransitionType.CROSS_DISSOLVE) mapped++
                    x.open("transition", listOf("name" to "Cross Dissolve", "offset" to t(c.start - span.lead), "duration" to t(span.frames)))
                    x.empty("filter-video", listOf("ref" to dissolve!!, "name" to "Cross Dissolve"))
                    x.close("transition")
                }
            }
            val children = item.children.sortedBy { it.first }.joinToString("") { it.second }
            when {
                c == null -> x.element("gap", listOf("name" to "Gap", "offset" to t(item.offset), "start" to "0s", "duration" to t(item.duration)), children)
                c.kind == ClipKind.TITLE -> {
                    titleStyles++
                    x.raw(Xml().apply { title(this, c, null, t(c.start), t(c.durationFrames), titleEffect!!, "ts$titleStyles", children) }.text())
                }
                else -> x.raw(Xml().apply { clip(this, doc, c, null, t(c.start), assets.getValue(c.assetId!!), assetFormats[c.assetId], fps, ::t, ::frames, children) }.text())
            }
        }
        x.close("spine")
        x.close("sequence")
        x.close("project")
        x.close("event")
        x.close("library")
        x.close("fcpxml")
        return Result(x.text(), exported.size, leftOut(doc, timeline, exported, mapped))
    }

    /** A clip's own time at its start: its source in point, or 0 when a time map plays it (speed, reverse, speed points). */
    private fun localStart(c: Clip, fps: Int): Int =
        if (c.kind == ClipKind.TITLE || retimed(c)) 0 else (c.sourceInMs * fps / 1000.0).roundToInt()

    private fun retimed(c: Clip) = !c.speed.isNormal || c.reverse || c.retimed

    private fun clip(
        x: Xml, doc: ProjectDocument, c: Clip, lane: Int?, offset: String, ref: String, format: String?, fps: Int,
        t: (Int) -> String, frames: (Double) -> Int, children: String = "",
    ) {
        val asset = doc.asset(c.assetId)!!
        val attrs = mutableListOf("ref" to ref)
        lane?.let { attrs += "lane" to "$it" }
        attrs += listOf("offset" to offset, "name" to c.name, "start" to t(localStart(c, fps)), "duration" to t(c.durationFrames))
        format?.let { attrs += "format" to it }
        if (asset.kind == MediaKind.VIDEO && asset.hasAudio) attrs += "srcEnable" to if (c.kind == ClipKind.AUDIO) "audio" else "video"
        attrs += "tcFormat" to "NDF"
        if (!c.enabled) attrs += "enabled" to "0"
        val outer = x
        val x = Xml()
        if (retimed(c)) {
            // Clip time → media time at the clip's ends and at every speed point.
            val inMs = c.sourceInMs.toDouble()
            val totalMs = c.sourceOffsetMs(c.durationFrames, fps)
            val points = (listOf(0) + c.speedSections.runningFold(0) { acc, s -> acc + s.frames }.drop(1).dropLast(1) + c.durationFrames).distinct()
            x.open("timeMap")
            for (p in points) {
                val ms = c.sourceOffsetMs(p, fps)
                val media = if (c.reverse) inMs + totalMs - ms else inMs + ms
                x.empty("timept", listOf("time" to t(p), "value" to t(frames(media)), "interp" to "linear"))
            }
            x.close("timeMap")
        }
        if (c.kind == ClipKind.AUDIO && (c.volumeDb != 0f || c.fadeInSeconds > 0f || c.fadeOutSeconds > 0f)) {
            val amount = String.format(Locale.US, "%.1fdB", c.volumeDb)
            if (c.fadeInSeconds > 0f || c.fadeOutSeconds > 0f) {
                x.open("adjust-volume", listOf("amount" to amount))
                x.open("param", listOf("name" to "amount"))
                if (c.fadeInSeconds > 0f) x.empty("fadeIn", listOf("type" to "easeIn", "duration" to t((c.fadeInSeconds * fps).roundToInt())))
                if (c.fadeOutSeconds > 0f) x.empty("fadeOut", listOf("type" to "easeOut", "duration" to t((c.fadeOutSeconds * fps).roundToInt())))
                x.close("param")
                x.close("adjust-volume")
            } else {
                x.empty("adjust-volume", listOf("amount" to amount))
            }
        }
        x.raw(children)
        outer.element("asset-clip", attrs, x.text())
    }

    private fun title(x: Xml, c: Clip, lane: Int?, offset: String, duration: String, ref: String, styleId: String, children: String = "") {
        val style = c.title
        val attrs = mutableListOf("ref" to ref)
        lane?.let { attrs += "lane" to "$it" }
        attrs += listOf("offset" to offset, "name" to c.name, "start" to "0s", "duration" to duration)
        if (!c.enabled) attrs += "enabled" to "0"
        x.open("title", attrs)
        x.open("text")
        x.element("text-style", listOf("ref" to styleId), Xml.escape(style?.text ?: c.name))
        x.close("text")
        x.open("text-style-def", listOf("id" to styleId))
        val argb = style?.color?.argb ?: -1
        val rgb = listOf(16, 8, 0).joinToString(" ") { s -> String.format(Locale.US, "%.4f", ((argb shr s) and 0xFF) / 255.0).trimEnd('0').trimEnd('.') }
        val styleAttrs = mutableListOf("font" to "PingFang SC", "fontSize" to "${style?.sizePx ?: 72}", "fontColor" to "$rgb 1", "alignment" to "center")
        if ((style?.weight ?: TitleWeight.REGULAR).value >= TitleWeight.BOLD.value) styleAttrs += "bold" to "1"
        x.empty("text-style", styleAttrs)
        x.close("text-style-def")
        x.raw(children)
        x.close("title")
    }

    /** What FCPXML cannot carry, counted. */
    private fun leftOut(doc: ProjectDocument, timeline: Timeline, exported: List<Clip>, mappedTransitions: Int): List<String> {
        val out = mutableListOf<String>()
        val graded = exported.count { it.grade != ColorGrade.NEUTRAL }
        if (graded > 0) out += "$graded 个片段的调色"
        val looks = exported.count { it.effects.isNotEmpty() || it.transform != ClipTransform() || it.keyframes.isNotEmpty() || it.chromaKey != null || it.dynamicZoom != null }
        if (looks > 0) out += "$looks 个片段的画面变换与效果"
        val keyed = exported.count { it.volumeKeyframes.isNotEmpty() }
        if (keyed > 0) out += "$keyed 个片段的音量关键帧"
        val crossFades = exported.count { it.kind == ClipKind.AUDIO && it.crossFadeFrames > 0 }
        if (crossFades > 0) out += "$crossFades 处声音交叉淡化"
        val offV1 = exported.count { it.trackId != Timeline.V1 && it.transitionIn != null }
        if (offV1 > 0) out += "V1 以外的 $offV1 个转场"
        if (mappedTransitions > 0) out += "$mappedTransitions 个转场按交叉溶解写出"
        val subtitles = timeline.clips.count { c -> timeline.tracks.firstOrNull { it.id == c.trackId }?.kind == TrackKind.SUBTITLE }
        if (subtitles > 0) out += "字幕轨上的 $subtitles 条字幕（可用“导出字幕”）"
        val missing = timeline.clips.count { it.kind != ClipKind.TITLE && it.assetId != null && doc.asset(it.assetId) == null }
        if (missing > 0) out += "$missing 个找不到素材的片段"
        return out
    }

    /** Ids r1, r2 … for the resources. */
    private class Ids {
        private var n = 0
        fun next() = "r${++n}"
    }

    /** A small XML writer: two-space indents, attribute values escaped. */
    private class Xml {
        private val sb = StringBuilder()
        private var depth = 0

        fun raw(s: String) {
            if (s.isEmpty()) return
            if (s.startsWith("<") && !s.startsWith("<?") && !s.startsWith("<!")) s.lines().filter { it.isNotEmpty() }.forEach { line(it) } else line(s)
        }

        fun open(name: String, attrs: List<Pair<String, String>> = emptyList()) {
            line("<$name${attributes(attrs)}>")
            depth++
        }

        fun close(name: String) {
            depth--
            line("</$name>")
        }

        fun empty(name: String, attrs: List<Pair<String, String>>) = line("<$name${attributes(attrs)}/>")

        /** An element with text (already escaped) or child markup. */
        fun element(name: String, attrs: List<Pair<String, String>>, content: String) {
            when {
                content.isEmpty() -> empty(name, attrs)
                content.startsWith("<") -> {
                    open(name, attrs)
                    raw(content)
                    close(name)
                }
                else -> line("<$name${attributes(attrs)}>$content</$name>")
            }
        }

        fun text(): String = sb.toString()

        private fun line(s: String) {
            repeat(depth) { sb.append("  ") }
            sb.append(s).append('\n')
        }

        private fun attributes(attrs: List<Pair<String, String>>) = attrs.joinToString("") { (k, v) -> " $k=\"${escape(v)}\"" }

        companion object {
            fun escape(s: String): String = buildString {
                for (ch in s) when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    // Line breaks as references, so re-indenting a fragment cannot reach into a title's words.
                    '\n' -> append("&#10;")
                    '\t' -> append("&#9;")
                    else -> if (ch < ' ') append(' ') else append(ch)
                }
            }
        }
    }

    companion object {
        /** Saved as bytes: the Downloads collection would add .xml to a name whose type says XML. */
        const val MIME = "application/octet-stream"
        private const val BASIC_TITLE = ".../Titles.localized/Bumper:Opener.localized/Basic Title.localized/Basic Title.moti"
        private const val CROSS_DISSOLVE = "FxPlug:4731E73A-8DAC-4113-9A30-AE85B1761265"

        /** The media's file as a URL: its own when it is a file (stills, generators), else shared storage by folder and name. */
        fun fileUrl(a: MediaAsset): String =
            if (a.uri.startsWith("file:")) a.uri
            else "file://" + URI(null, null, "/storage/emulated/0/" + a.relativePath.trimStart('/') + a.name, null).toASCIIString()
    }
}
