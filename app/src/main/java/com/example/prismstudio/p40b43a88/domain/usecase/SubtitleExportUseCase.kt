package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import java.util.Locale

/**
 * The subtitle track as a SubRip (.srt) or WebVTT (.vtt) file for the rendered range: one numbered cue per subtitle on
 * a shown subtitle track, timed from the start of the range, as Resolve's Deliver › Export Subtitle.
 */
class SubtitleExportUseCase {

    /** The SRT text for frames [rangeStart] until [rangeEnd] (null: the whole timeline); null when there are no subtitles. */
    fun srt(doc: ProjectDocument, rangeStart: Int? = null, rangeEnd: Int? = null): String? = text(doc, SubtitleFileFormat.SRT, rangeStart, rangeEnd)

    /** One delivered subtitle file: its track's language and text. */
    data class File(val language: SubtitleLanguage, val trackName: String, val text: String)

    /**
     * Every subtitle track with subtitles in the range as a file of its own (Resolve's Export Subtitle, one per language),
     * the shown one first — hidden tracks too: hiding a language only keeps it out of the picture.
     */
    fun files(doc: ProjectDocument, format: SubtitleFileFormat, rangeStart: Int? = null, rangeEnd: Int? = null): List<File> =
        doc.timeline.tracks.filter { it.kind == TrackKind.SUBTITLE }.sortedByDescending { it.enabled }
            .mapNotNull { tr -> text(doc, format, rangeStart, rangeEnd, setOf(tr.id))?.let { File(tr.subtitleLanguage, tr.name, it) } }

    /**
     * `subtitle-delivery-note`: which language the render burns in (the shown subtitle track, or none when all are hidden)
     * and, with [exportFiles], which files go beside it; null when the range has no subtitles.
     */
    fun note(doc: ProjectDocument, format: SubtitleFileFormat, exportFiles: Boolean, rangeStart: Int? = null, rangeEnd: Int? = null): String? {
        val files = files(doc, format, rangeStart, rangeEnd)
        if (files.isEmpty()) return null
        val shown = doc.timeline.tracks.firstOrNull { it.kind == TrackKind.SUBTITLE && it.enabled }
        val burned = shown?.let { tr -> files.firstOrNull { it.trackName == tr.name } }
        return buildString {
            append("画面烧录：").append(burned?.let { "${it.language.label}（${it.trackName}）" } ?: "无（字幕轨都已隐藏）")
            if (exportFiles) {
                append("；字幕文件：").append(files.joinToString("、") { it.language.short })
                append(if (files.size > 1) "，各一个 .${format.extension}" else " .${format.extension}")
            } else if (files.size > 1) append("；打开「字幕文件」可把 ${files.size} 种语言各存一个文件")
        }
    }

    /** A delivered file's name: Name.srt alone, Name.en.srt (its language's tag) when there are several. */
    fun fileName(base: String, file: File, several: Boolean, format: SubtitleFileFormat): String =
        if (several) "$base.${file.language.code}.${format.extension}" else "$base.${format.extension}"

    /**
     * The subtitle file in [format]: SRT as above, or WebVTT — its WEBVTT header, the same numbered cues with a full stop
     * before the milliseconds. [trackIds] limits it to those tracks (default: the shown subtitle tracks).
     */
    fun text(doc: ProjectDocument, format: SubtitleFileFormat, rangeStart: Int? = null, rangeEnd: Int? = null, trackIds: Set<String>? = null): String? {
        val fps = doc.format.fps.coerceAtLeast(1)
        val start = rangeStart ?: 0
        val end = rangeEnd ?: doc.durationFrames
        val tracks = trackIds ?: doc.timeline.tracks.filter { it.kind == TrackKind.SUBTITLE && it.enabled }.map { it.id }.toSet()
        val cues = doc.timeline.clips
            .filter { it.kind == ClipKind.TITLE && it.enabled && it.trackId in tracks && it.title?.template == TitleTemplate.SUBTITLE }
            .filter { it.end > start && it.start < end && it.title!!.text.isNotBlank() }
            .sortedBy { it.start }
        if (cues.isEmpty()) return null
        val vtt = format == SubtitleFileFormat.WEBVTT
        return buildString {
            if (vtt) append("WEBVTT\n\n")
            cues.forEachIndexed { i, clip ->
                val from = (maxOf(clip.start, start) - start) * 1000L / fps
                val until = (minOf(clip.end, end) - start) * 1000L / fps
                append(i + 1).append('\n')
                append(time(from, vtt)).append(" --> ").append(time(until, vtt)).append('\n')
                // A blank line ends a cue in both formats, so a subtitle's own blank lines are dropped.
                append(clip.title!!.text.trim().replace("\r", "").lines().filter { it.isNotBlank() }.joinToString("\n")).append("\n\n")
            }
        }
    }

    /** SRT time: hours:minutes:seconds,milliseconds (WebVTT: a full stop before the milliseconds). */
    private fun time(ms: Long, vtt: Boolean): String =
        String.format(Locale.US, if (vtt) "%02d:%02d:%02d.%03d" else "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
}
