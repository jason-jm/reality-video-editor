package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Resolve's Export → Timeline → EDL: the open timeline as a CMX 3600 edit decision list another editor can conform. CMX has
 * one picture channel and a few sound ones, so V1 goes out as V and A1 / A2 as A / A2; a dissolve into a V1 clip (or a sound
 * cross fade) becomes a D event of its length, a speed other than 1× (or reverse) an M2 line, and each event carries its
 * clip and file names as comments. The reel is AX (files have no tape), source timecode counts from each file's start and
 * record timecode from the timeline's. Titles, disabled clips and clips on other tracks stay out, said at the end.
 */
class EdlExportUseCase {

    data class Result(val text: String, val events: Int, val left: List<String>)

    operator fun invoke(doc: ProjectDocument): Result {
        val fps = doc.format.fps.coerceAtLeast(1)
        val timeline = doc.timeline
        val lines = mutableListOf("TITLE: ${doc.timelineName}", "FCM: NON-DROP FRAME", "")
        val events = mutableListOf<Event>()
        channel(doc, timeline, Timeline.V1, "V", fps, events)
        channel(doc, timeline, Timeline.A1, "A", fps, events)
        channel(doc, timeline, Timeline.A2, "A2", fps, events)
        // In record order, as a conform reads them (picture before sound at the same time).
        events.sortedWith(compareBy({ it.recordIn }, { it.order })).forEachIndexed { i, e -> lines += e.render(i + 1) }
        val left = leftOut(timeline)
        if (left.isNotEmpty()) {
            lines += ""
            left.forEach { lines += "* 未包含：$it" }
        }
        return Result(lines.joinToString("\r\n", postfix = "\r\n"), events.size, left)
    }

    /** One event: its lines once numbered. [order] keeps picture before sound at the same record time. */
    private class Event(val recordIn: Int, val order: Int, val body: (String) -> List<String>) {
        fun render(number: Int): List<String> = body(String.format(Locale.US, "%03d", number))
    }

    private fun channel(doc: ProjectDocument, timeline: Timeline, trackId: String, name: String, fps: Int, out: MutableList<Event>) {
        val clips = timeline.clipsOn(trackId).filter { it.enabled && it.kind != ClipKind.TITLE && it.assetId != null }
        val order = if (name == "V") 0 else if (name == "A") 1 else 2
        val previousOf = clips.associate { c -> c.id to clips.firstOrNull { it.end == c.start && it.id != c.id } }
        // A dissolve (picture) or cross fade (sound) from the clip before, as (frames before the cut, length).
        val fades = clips.mapNotNull { clip ->
            if (previousOf[clip.id] == null) return@mapNotNull null
            val fade = if (clip.kind == ClipKind.AUDIO) clip.crossFadeFrames.takeIf { it > 0 }?.let { 0 to minOf(it, clip.durationFrames) }
            else TransitionHandles.span(doc, clip, fps)?.takeIf { it.frames > 0 && it.outgoing != null }?.let { it.lead to it.frames }
            fade?.let { clip.id to it }
        }.toMap()
        // The clip before a centred dissolve hands over where the dissolve starts, not at the cut.
        val handsOverEarly = clips.associate { c -> c.id to (clips.firstOrNull { it.start == c.end && it.id != c.id }?.let { fades[it.id]?.first } ?: 0) }
        for (clip in clips) {
            val asset = doc.asset(clip.assetId)
            val early = handsOverEarly[clip.id] ?: 0
            val srcIn = frames(clip.sourceInMs, fps)
            val srcEnd = srcIn + frames(clip.sourceDurationMs(fps), fps)
            val srcOut = srcEnd - (early * clip.speedAt(clip.durationFrames - 1)).roundToInt()
            val recOut = clip.end - early
            val names = listOfNotNull(
                "* FROM CLIP NAME: ${clip.name}",
                asset?.let { "* SOURCE FILE: ${it.uri.substringAfterLast('/')}" },
                speedLine(clip, srcIn, fps),
            )
            val fade = fades[clip.id]
            val previous = previousOf[clip.id]
            if (fade == null || previous == null) {
                out += Event(clip.start, order) { n -> listOf(line(n, name, "C", null, srcIn, srcOut, clip.start, recOut, fps)) + names }
                continue
            }
            val (lead, length) = fade
            val recIn = clip.start - lead
            // The outgoing source where the dissolve starts, held for no time, then D into this clip from as early.
            val prevSrcEnd = frames(previous.sourceInMs, fps) + frames(previous.sourceDurationMs(fps), fps)
            val prevSrcAt = prevSrcEnd - (lead * previous.speedAt(previous.durationFrames - 1)).roundToInt()
            // Source length = record length × speed, so a conform lines up; a still has no head to lead in from, so its
            // range starts at 0 instead of before it.
            val wanted = srcIn - (lead * clip.speedAt(0)).roundToInt()
            val shift = if (wanted < 0) -wanted else 0
            val srcFrom = wanted + shift
            val srcTo = srcOut + shift
            val effect = clip.transitionIn?.type?.label?.takeIf { clip.kind != ClipKind.AUDIO }
            out += Event(recIn, order) { n ->
                listOf(
                    line(n, name, "C", null, prevSrcAt, prevSrcAt, recIn, recIn, fps),
                    line(n, name, "D", length, srcFrom, srcTo, recIn, recOut, fps),
                ) + listOfNotNull(effect?.let { "* EFFECT NAME: $it" }) + names
            }
        }
    }

    /** M2 for a speed other than 1× or a reverse: the rate in frames a second, negative backwards, from [srcIn]. */
    private fun speedLine(clip: Clip, srcIn: Int, fps: Int): String? {
        val factor = clip.speed.factor
        if (factor == 1f && !clip.reverse && !clip.retimed) return null
        val rate = fps * factor * if (clip.reverse) -1 else 1
        return String.format(Locale.US, "M2   AX       %05.1f                %s", rate, tc(srcIn, fps)) +
            if (clip.retimed) "\r\n* 片段有变速点：EDL 只能记一个速度，这里是第一段的" else ""
    }

    private fun line(n: String, channel: String, type: String, length: Int?, srcIn: Int, srcOut: Int, recIn: Int, recOut: Int, fps: Int): String =
        String.format(
            Locale.US, "%s  %-8s %-5s %-4s %3s %s %s %s %s",
            n, "AX", channel, type, length?.let { String.format(Locale.US, "%03d", it) } ?: "", tc(srcIn, fps), tc(srcOut, fps), tc(recIn, fps), tc(recOut, fps),
        )

    /** What CMX cannot hold: other tracks' clips, titles and disabled clips, counted. */
    private fun leftOut(timeline: Timeline): List<String> {
        val out = mutableListOf<String>()
        val kept = setOf(Timeline.V1, Timeline.A1, Timeline.A2)
        timeline.tracks.filter { it.id !in kept }.forEach { t ->
            val n = timeline.clipsOn(t.id).size
            if (n > 0) out += "${t.name} 上 $n 个片段（CMX 3600 只有一条画面轨和两条声音轨）"
        }
        val titles = timeline.clips.count { it.trackId in kept && it.kind == ClipKind.TITLE }
        if (titles > 0) out += "$titles 个标题（没有源文件）"
        val off = timeline.clips.count { it.trackId in kept && !it.enabled }
        if (off > 0) out += "$off 个停用的片段"
        return out
    }

    private fun frames(ms: Long, fps: Int): Int = (ms * fps / 1000.0).roundToInt()

    private fun tc(frames: Int, fps: Int): String = Timecode.format(frames.coerceAtLeast(0), fps)
}
