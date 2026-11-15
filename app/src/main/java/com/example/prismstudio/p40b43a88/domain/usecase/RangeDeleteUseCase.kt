package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks

/**
 * Resolve's delete with timeline In / Out: what lies between the marks on every unlocked track — picture,
 * sound and subtitles — is cut out. [lift] leaves the gap, still framed by the marks for an edit that fills
 * it; [extract] closes it, the later clips on those tracks moving back, and drops the marks. Clips across a
 * mark are split there (the tails of linked clips stay linked); locked tracks keep everything.
 */
class RangeDeleteUseCase(private val ids: IdGenerator) {

    fun lift(timeline: Timeline, fps: Int): EditResult {
        val (from, until) = range(timeline)
        val (cleared, touched) = cleared(timeline, from, until, fps)
        if (touched == 0) throw EditRejected("入点至出点之间没有片段")
        return EditResult(cleared, null, "已删除 ${span(from, until, fps)} 的内容，留下空隙")
    }

    fun extract(timeline: Timeline, fps: Int): EditResult {
        val (from, until) = range(timeline)
        val (cleared, touched) = cleared(timeline, from, until, fps)
        val length = until - from
        val unlocked = timeline.tracks.filterNot { it.locked }.map { it.id }.toSet()
        var moved = 0
        val closed = cleared.clips.map { c ->
            if (c.trackId in unlocked && c.start >= until) c.copy(start = c.start - length).also { moved++ } else c
        }
        if (touched == 0 && moved == 0) throw EditRejected("入点至出点之间和之后都没有片段")
        return EditResult(
            cleared.copy(clips = closed, marks = TimelineMarks()),
            null,
            "已波纹删除 ${span(from, until, fps)}，后面的片段前移 ${Timecode.format(length, fps)}",
        )
    }

    private fun range(timeline: Timeline): Pair<Int, Int> {
        val from = timeline.marks.inFrame
        val until = timeline.marks.outFrame
        if (from == null || until == null) throw EditRejected("先在时间线上标记入点和出点")
        if (timeline.tracks.all { it.locked }) throw EditRejected("所有轨道都已锁定")
        return from to until
    }

    /** The range cleared on every unlocked track, and how many clips were cut or removed. */
    private fun cleared(timeline: Timeline, from: Int, until: Int, fps: Int): Pair<Timeline, Int> {
        val tailLinks = mutableMapOf<String, String>()
        var result = timeline
        var touched = 0
        for (track in timeline.tracks.filterNot { it.locked }) {
            val (next, count) = result.overwritten(track.id, from, until, fps, ids, tailLinks)
            result = next
            touched += count
        }
        return result to touched
    }

    private fun span(from: Int, until: Int, fps: Int) = "入点 ${Timecode.format(from, fps)} 至出点 ${Timecode.format(until, fps)}"
}
