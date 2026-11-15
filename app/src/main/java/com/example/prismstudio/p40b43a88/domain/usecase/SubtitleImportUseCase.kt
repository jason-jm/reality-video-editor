package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt

/**
 * Subtitle files onto the subtitle track, as Resolve's File › Import › Subtitle — SubRip (.srt), WebVTT (.vtt) and
 * SubStation Alpha (.ass / .ssa), told apart by their content: the cues replace the track's subtitles (one undoable
 * edit), timed on the timeline's frame grid; overlapping cues are cut where the next one starts.
 */
class SubtitleImportUseCase(private val ids: IdGenerator) {

    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    /** The text of an .srt file: a byte-order mark decides; else UTF-8 when the bytes are valid UTF-8, else GB18030. */
    fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> strict(bytes, Charsets.UTF_8) ?: String(bytes, Charset.forName("GB18030"))
    }

    /** The cues of a subtitle file's text, whichever of the formats it is; cues without timing or text are skipped. */
    fun parse(text: String): List<Cue> = if (ASS_EVENTS.containsMatchIn(text)) parseAss(text) else parseTimed(text)

    /**
     * SubStation Alpha: the [Events] section's Format line names the fields and each Dialogue line fills them, its Text
     * last (commas and all); times are hours:minutes:seconds.centiseconds, \N breaks a line and {\…} overrides are dropped.
     */
    private fun parseAss(text: String): List<Cue> {
        var events = false
        var fields = DEFAULT_ASS_FIELDS
        val cues = mutableListOf<Cue>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                events = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!events) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val values = line.substringAfter(':').split(',', limit = fields.size)
            val at = { name: String -> fields.indexOf(name).takeIf { it >= 0 }?.let { values.getOrNull(it) } }
            val start = at("start")?.let(::assMillis) ?: continue
            val end = at("end")?.let(::assMillis) ?: continue
            val content = (at("text") ?: continue).replace(ASS, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            if (content.isNotEmpty() && end > start) cues += Cue(start, end, content)
        }
        return cues.sortedBy { it.startMs }
    }

    /** SRT and WebVTT cues: a timing line, then the text up to a blank line; formatting tags and entities are resolved. */
    private fun parseTimed(text: String): List<Cue> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val cues = mutableListOf<Cue>()
        var i = 0
        while (i < lines.size) {
            val timing = TIMING.find(lines[i])
            if (timing == null) {
                i++
                continue
            }
            val (a, b) = timing.destructured
            val body = mutableListOf<String>()
            i++
            while (i < lines.size && lines[i].isNotBlank() && TIMING.find(lines[i]) == null) {
                body += clean(lines[i])
                i++
            }
            // A cue number left at the end of the body belongs to the next cue.
            if (body.isNotEmpty() && body.last().trim().all { it.isDigit() } && i < lines.size && TIMING.find(lines[i]) != null) body.removeAt(body.lastIndex)
            val content = body.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            val start = millis(a)
            val end = millis(b)
            if (content.isNotEmpty() && end > start) cues += Cue(start, end, content)
        }
        return cues.sortedBy { it.startMs }
    }

    /**
     * Puts [cues] on the subtitle track of their language ([SubtitleLanguage.detect]: the file's name, else its script) in
     * place of its subtitles, making that track when there is none and room for it ([TracksUseCase.MAX_SUBTITLE_TRACKS];
     * otherwise the shown subtitle track takes them). The track it lands on is the one shown.
     */
    fun import(timeline: Timeline, cues: List<Cue>, fps: Int, fileName: String): EditResult {
        if (cues.isEmpty()) throw EditRejected("文件里没有可识别的字幕")
        val subtitles = timeline.tracks.filter { it.kind == TrackKind.SUBTITLE }
        val detected = SubtitleLanguage.detect(fileName, cues.joinToString("\n") { it.text })
        val same = detected?.let { lang -> subtitles.firstOrNull { it.subtitleLanguage == lang } }
        val room = subtitles.size < TracksUseCase.MAX_SUBTITLE_TRACKS
        val existing = same ?: if (!room || (detected == null && subtitles.isNotEmpty())) subtitles.firstOrNull { it.enabled } ?: subtitles.firstOrNull() else null
        val track = existing ?: timeline.nextTrack(TrackKind.SUBTITLE).copy(language = detected ?: SubtitleLanguage.DEFAULT)
        val added = if (existing == null) timeline.withTrack(track) else timeline
        added.requireTrackUnlocked(track.id)
        // The imported language is the one on screen (and burned in): the other subtitle tracks hide.
        val base = added.copy(tracks = added.tracks.map { if (it.kind == TrackKind.SUBTITLE) it.copy(enabled = it.id == track.id) else it })
        val replaced = base.clipsOn(track.id).count { it.title?.template == TitleTemplate.SUBTITLE }
        val frames = cues.map { frameOf(it.startMs, fps) to frameOf(it.endMs, fps) }
        val clips = cues.mapIndexedNotNull { i, cue ->
            val start = frames[i].first
            // Cut where the next cue starts: one subtitle on screen at a time.
            val end = minOf(frames[i].second, frames.getOrNull(i + 1)?.first ?: Int.MAX_VALUE)
            if (end <= start) return@mapIndexedNotNull null
            Clip(
                id = ids.next("t"),
                trackId = track.id,
                kind = ClipKind.TITLE,
                name = "字幕：${cue.text.lineSequence().first()}",
                start = start,
                durationFrames = end - start,
                title = TitleStyle(cue.text, TitleTemplate.SUBTITLE.sizePx, TitleTemplate.SUBTITLE),
            )
        }
        if (clips.isEmpty()) throw EditRejected("文件里没有可识别的字幕")
        val kept = base.clips.filterNot { it.trackId == track.id && it.title?.template == TitleTemplate.SUBTITLE }
        val language = base.track(track.id)!!.subtitleLanguage
        val message = buildString {
            append("已导入 ${clips.size} 条${language.label}字幕（$fileName）")
            if (replaced > 0) append("，替换了原有 $replaced 条")
            if (existing == null) append("，新建字幕轨 ${track.name}")
            else if (detected != null && detected != language) append("，字幕轨已满：放在 ${track.name}（${language.label}）")
        }
        return EditResult(base.copy(clips = kept + clips), clips.first().id, message)
    }

    private fun frameOf(ms: Long, fps: Int): Int = (ms * fps / 1000.0).roundToInt()

    /** Drops HTML-like tags (<i>, <font …>, WebVTT's <v Name> and <00:00:01.000>), ASS overrides ({\an8}) and entities. */
    private fun clean(line: String): String = line.replace(TAGS, "").replace(TIME_TAGS, "").replace(ASS, "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lrm;", "").replace("&rlm;", "").replace("&amp;", "&")

    /** h:mm:ss.cc as milliseconds (centiseconds, or milliseconds when three digits are given). */
    private fun assMillis(stamp: String): Long? {
        val m = ASS_STAMP.matchEntire(stamp.trim()) ?: return null
        val (h, min, s, frac) = m.destructured
        return ((h.toLong() * 60 + min.toLong()) * 60 + s.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
    }

    private fun millis(stamp: String): Long {
        val m = STAMP.matchEntire(stamp.trim()) ?: return 0
        val (h, min, s, ms) = m.destructured
        return ((h.ifEmpty { "0" }.toLong() * 60 + min.toLong()) * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').take(3).toLong()
    }

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private companion object {
        /** 00:00:01,500 --> 00:00:03,000 (hours optional, "," or "." before the milliseconds, position tags after). */
        val TIMING = Regex("""^\s*((?:\d{1,2}:)?\d{1,2}:\d{1,2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{1,2}[,.]\d{1,3})""")
        val STAMP = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
        val TAGS = Regex("""</?[a-zA-Z][^>]*>""")
        val ASS = Regex("""\{\\[^}]*\}""")
        /** WebVTT's karaoke timestamps inside a cue. */
        val TIME_TAGS = Regex("""<\d{1,2}(?::\d{2}){1,2}\.\d{3}>""")
        val ASS_EVENTS = Regex("""(?im)^\s*\[Events\]\s*$""")
        val ASS_STAMP = Regex("""(\d{1,2}):(\d{1,2}):(\d{1,2})[.:](\d{1,3})""")
        /** ASS v4+ Dialogue fields when a file has no Format line. */
        val DEFAULT_ASS_FIELDS = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
    }
}
