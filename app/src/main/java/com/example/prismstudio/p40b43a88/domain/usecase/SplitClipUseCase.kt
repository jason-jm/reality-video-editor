package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.effectsFor
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.maskFor

/**
 * Splits at the playhead. When selected clips are under the playhead only they (and their linked
 * partners) are cut; otherwise every unlocked clip under the playhead is cut. The right-hand parts of
 * the cut selected clips become the selection.
 */
class SplitClipUseCase(private val ids: IdGenerator) {

    operator fun invoke(timeline: Timeline, atFrame: Int, selectedClipId: String?, fps: Int): EditResult =
        invoke(timeline, atFrame, listOfNotNull(selectedClipId), fps)

    operator fun invoke(timeline: Timeline, atFrame: Int, selectedClipIds: List<String>, fps: Int): EditResult {
        val unlocked = timeline.tracks.filterNot { it.locked }.map { it.id }.toSet()
        val underPlayhead = timeline.clips.filter { atFrame > it.start && atFrame < it.end && it.trackId in unlocked }
        if (underPlayhead.isEmpty()) throw EditRejected("播放头下没有可分割的片段")
        val selectedUnder = selectedClipIds.mapNotNull { id -> underPlayhead.firstOrNull { it.id == id } }
        val selected = selectedUnder.firstOrNull()
        val targets = if (selected != null) {
            selectedUnder.flatMap { withPartner(timeline, it) }.distinct().filter { it in underPlayhead }
        } else {
            underPlayhead
        }

        val newLinks = mutableMapOf<String, String>()
        val rightIds = mutableMapOf<String, String>()
        var clips = timeline.clips
        for (clip in targets) {
            val rightId = ids.next("c")
            rightIds[clip.id] = rightId
            val rightLink = clip.linkId?.let { link -> newLinks.getOrPut(link) { ids.next("l") } }
            val left = clip.piece(clip.start, atFrame, fps)
            val right = clip.piece(atFrame, clip.end, fps).copy(id = rightId, linkId = rightLink)
            clips = clips.flatMap { if (it.id == clip.id) listOf(left, right) else listOf(it) }
        }
        val newSelection = rightIds[selected?.id ?: targets.first().id]
        return EditResult(
            timeline = timeline.copy(clips = clips),
            selectedClipId = newSelection,
            message = "已在 ${Timecode.format(atFrame, fps)} 分割 ${targets.size} 个片段",
            selection = if (selectedUnder.size > 1) selectedUnder.mapNotNull { rightIds[it.id] } else null,
        )
    }

    private fun withPartner(timeline: Timeline, clip: Clip): List<Clip> =
        if (clip.linkId == null) listOf(clip) else timeline.clips.filter { it.linkId == clip.linkId }
}

/**
 * The part of this clip between timeline frames [from] and [until] (kept inside it): its source,
 * keyframes and volume keyframes follow, and a fade or in-transition stays only on the piece that keeps
 * that end of the clip. Id and link are the clip's; callers give a new piece its own.
 */
internal fun Clip.piece(from: Int, until: Int, fps: Int): Clip {
    val a = from.coerceIn(start, end)
    val b = until.coerceIn(a, end)
    val offset = a - start
    // A reversed clip plays its source window backwards: a piece's window begins as far into the
    // clip's as the piece ends before the clip's end.
    val sourceOffset = if (reverse) end - b else offset
    // Speed points (forward clips only) map frames through their sections; the piece keeps the sections it crosses.
    val (pieceSpeed, sections) = sectionWindow(offset, b - a)
    return copy(
        start = a,
        durationFrames = b - a,
        sourceInMs = sourceInMs + if (retimed) sourceOffsetMs(offset, fps).toLong() else (sourceOffset * 1000L * speed.factor / fps).toLong(),
        speed = pieceSpeed,
        speedSections = sections,
        keyframes = keyframesFor(offset, b - a),
        effects = effectsFor(offset, b - a),
        volumeKeyframes = volumeKeyframesFor(offset, b - a),
        panKeyframes = panKeyframesFor(offset, b - a),
        grade = grade.keysFor(offset, b - a),
        mask = maskFor(offset, b - a),
        fadeInSeconds = if (a == start) fadeInSeconds else 0f,
        fadeOutSeconds = if (b == end) fadeOutSeconds else 0f,
        fadeInCurve = if (a == start) fadeInCurve else 0f,
        fadeOutCurve = if (b == end) fadeOutCurve else 0f,
        transitionIn = if (a == start) transitionIn else null,
        crossFadeFrames = if (a == start) crossFadeFrames else 0,
    )
}
