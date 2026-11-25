package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.frozenAt

/**
 * Freeze frame (CapCut's 定格, Premiere's frame hold): the clip is split at the playhead and a still of
 * that frame plays for a hold in between. The rest of the clip — with its linked sound — and what follows
 * on those tracks move later by the hold, so picture and sound stay in sync; other tracks keep their
 * timing. The still keeps the clip's placement at that frame, its grade and effects, so it looks like the
 * paused clip. The frame is captured first ([request]), then put in ([insert]).
 */
class FreezeFrameUseCase(private val ids: IdGenerator) {

    /** What to capture: the [clip]'s source [asset] at [sourceMs], saved as the still asset [stillId]. */
    data class Request(val clip: Clip, val asset: MediaAsset, val sourceMs: Long, val stillId: String, val stillName: String)

    data class Result(val doc: ProjectDocument, val selectedClipId: String, val message: String)

    fun request(doc: ProjectDocument, clipId: String?, atFrame: Int): Request {
        val clip = clipId?.let { doc.timeline.clip(it) } ?: throw EditRejected("请先在时间线上选择视频片段")
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只能为视频片段定格")
        val asset = doc.asset(clip.assetId) ?: throw EditRejected("找不到片段的素材")
        if (asset.kind == MediaKind.IMAGE) throw EditRejected("图片本身就是静帧，无需定格")
        if (asset.offline) throw EditRejected("素材离线，无法定格")
        if (atFrame !in clip) throw EditRejected("请把播放头移到所选片段上")
        doc.timeline.requireUnlocked(doc.timeline.linkedGroup(clip), clip)
        val fps = doc.format.fps
        return Request(
            clip = clip,
            asset = asset,
            sourceMs = clip.sourceMsAt(atFrame - clip.start, fps),
            stillId = ids.next("a"),
            stillName = "定格 · ${clip.name} · ${Timecode.format(atFrame, fps)}",
        )
    }

    /** [captured] (the captured frame) held for [holdFrames] at [atFrame] in the requested clip. */
    fun insert(doc: ProjectDocument, request: Request, captured: MediaAsset, atFrame: Int, holdFrames: Int): Result {
        // A captured H.264 still contains the source code values. Carry the transfer across the cut.
        val still = when (request.asset.effectiveTransfer) {
            ColorTransfer.HLG -> captured.copy(inputColor = InputColorSpace.HLG)
            ColorTransfer.PQ -> captured.copy(inputColor = InputColorSpace.PQ)
            ColorTransfer.SDR -> captured
        }
        val timeline = doc.timeline
        // The project may have changed while the frame was being captured.
        val clip = timeline.clip(request.clip.id) ?: throw EditRejected("片段已不在时间线上")
        if (atFrame !in clip) throw EditRejected("请把播放头移到所选片段上")
        val group = timeline.linkedGroup(clip)
        timeline.requireUnlocked(group, clip)
        val fps = doc.format.fps
        val tracks = group.map { it.trackId }.toSet()
        val tailLinks = mutableMapOf<String, String>()
        val shifted = timeline.clips.flatMap { c ->
            when {
                c.trackId !in tracks || c.end <= atFrame -> listOf(c)
                c.start >= atFrame -> listOf(c.copy(start = c.start + holdFrames))
                // Under the playhead: split, the tail after the hold (linked to its partner's tail).
                else -> listOf(
                    c.piece(c.start, atFrame, fps),
                    c.piece(atFrame, c.end, fps).copy(
                        id = ids.next(if (c.kind == ClipKind.TITLE) "t" else "c"),
                        start = atFrame + holdFrames,
                        linkId = c.linkId?.let { tailLinks.getOrPut(it) { ids.next("l") } },
                    ),
                )
            }
        }
        val frozen = Clip(
            id = ids.next("c"),
            trackId = clip.trackId,
            kind = ClipKind.VIDEO,
            name = "定格 · ${clip.name}",
            start = atFrame,
            durationFrames = holdFrames,
            assetId = still.id,
            transform = clip.transformAt(atFrame - clip.start),
            grade = clip.grade.frozenAt(atFrame - clip.start),
            mask = clip.mask?.frozenAt(atFrame - clip.start),
            effects = clip.effects.frozenAt(atFrame - clip.start).map { it.copy(id = ids.next("fx")) },
        )
        val updated = doc.copy(media = doc.media + still, timeline = timeline.copy(clips = shifted + frozen))
        val seconds = holdFrames.toFloat() / fps
        val length = if (seconds % 1f == 0f) "${seconds.toInt()} 秒" else String.format(java.util.Locale.US, "%.1f 秒", seconds)
        return Result(updated, frozen.id, "已在 ${Timecode.format(atFrame, fps)} 定格 $length，后面的片段顺延")
    }

    companion object {
        /** The hold a freeze frame gets (CapCut / Premiere use 2–3 s); trim the still to change it. */
        const val HOLD_SECONDS = 2
    }
}

/**
 * The source time (ms) of the frame shown [frame] frames into the clip: its window plays forward, or
 * backwards when reversed.
 */
internal fun Clip.sourceMsAt(frame: Int, fps: Int): Long {
    val msPerFrame = 1000.0 * speed.factor / fps.coerceAtLeast(1)
    val f = frame.coerceIn(0, (durationFrames - 1).coerceAtLeast(0))
    // Speed points (forward clips only) map the frame through their sections.
    val into = when {
        reverse -> (durationFrames - 1 - f) * msPerFrame
        retimed -> sourceOffsetMs(f, fps)
        else -> f * msPerFrame
    }
    return sourceInMs + into.toLong()
}
