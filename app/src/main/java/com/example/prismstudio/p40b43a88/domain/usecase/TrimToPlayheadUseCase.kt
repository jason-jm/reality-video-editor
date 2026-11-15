package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge

/**
 * Resolve's Trim Start / Trim End (Shift+[ / Shift+]) and Ripple Start / End to Playhead (Ctrl+Shift+[ / ]): the clips the
 * playhead is inside — the selected ones when any is, else every one under it on an unlocked track — have their start or
 * end moved to the playhead, as a trim would ([TrimClipUseCase]: linked partners with them, within the media and the
 * neighbours). A plain trim leaves a gap; a ripple one closes it, moving the rest of each trimmed track up by as much.
 */
class TrimToPlayheadUseCase(private val trim: TrimClipUseCase = TrimClipUseCase()) {

    @Suppress("LongParameterList")
    operator fun invoke(
        timeline: Timeline,
        edge: TrimEdge,
        ripple: Boolean,
        playhead: Int,
        selection: Collection<String>,
        assets: (String?) -> MediaAsset?,
        fps: Int,
    ): EditResult {
        val inside = timeline.clips.filter { playhead > it.start && playhead < it.end }
        val picked = inside.filter { it.id in selection }
        val targets = picked.ifEmpty { inside.filter { timeline.track(it.trackId)?.locked != true } }
        if (targets.isEmpty()) {
            throw EditRejected(if (inside.isEmpty()) "播放头不在任何片段上" else "播放头下的片段都在已锁定的轨道上")
        }
        var updated = timeline
        for (clip in targets) {
            val current = updated.clip(clip.id) ?: continue
            val sourceMs = assets(current.assetId)?.takeIf { it.kind != MediaKind.IMAGE }?.durationMs
            updated = trim(updated, current.id, edge, playhead, 0, false, playhead, sourceMs, fps).timeline
        }
        // What each clip lost, by its track: a ripple trim moves the rest of that track up by it.
        val lost = timeline.clips.mapNotNull { before ->
            val after = updated.clip(before.id) ?: return@mapNotNull null
            if (after == before) null else Triple(before, after, before.durationFrames - after.durationFrames)
        }
        if (lost.isEmpty()) throw EditRejected("片段已经无法再修剪")
        if (ripple) {
            for ((before, after, frames) in lost) {
                if (frames <= 0) continue
                updated = updated.copy(
                    clips = updated.clips.map { c ->
                        when {
                            c.id == after.id && edge == TrimEdge.START -> c.copy(start = before.start)
                            c.trackId == before.trackId && c.id != after.id && c.start >= before.end -> c.copy(start = c.start - frames)
                            else -> c
                        }
                    },
                )
            }
        }
        val count = lost.size
        val most = lost.maxOf { it.third }
        val what = if (edge == TrimEdge.START) "开头" else "结尾"
        val message = if (ripple) "已将 $count 个片段的${what}波纹修剪到播放头，后面的片段前移 ${Timecode.format(most, fps)}"
        else "已将 $count 个片段的${what}修剪到播放头"
        return EditResult(updated, picked.firstOrNull()?.id, message, selection = picked.map { it.id }.takeIf { it.size > 1 })
    }
}
