package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import kotlin.math.roundToInt

/**
 * Resolve's Change Clip Duration (Ctrl+D): the selected clip made exactly so long, by moving its end as a trim would
 * ([TrimClipUseCase]: linked partners with it, within its media, at least five frames). Without [ripple] it stops at the
 * next clip; with it the rest of each changed track moves by as much, so nothing is covered and no gap opens.
 */
class ClipDurationUseCase(private val trim: TrimClipUseCase = TrimClipUseCase()) {

    @Suppress("LongParameterList")
    operator fun invoke(timeline: Timeline, clipId: String?, frames: Int, ripple: Boolean, assets: (String?) -> MediaAsset?, fps: Int): EditResult {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (frames < TrimClipUseCase.MIN_FRAMES) throw EditRejected("片段至少 ${TrimClipUseCase.MIN_FRAMES} 帧")
        if (frames == clip.durationFrames) throw EditRejected("时长没有变化")
        val group = timeline.linkedGroup(clip)
        val tracks = group.map { it.trackId }.toSet()
        val ends = group.associate { it.trackId to it.end }
        val delta = frames - clip.durationFrames
        val sourceMs = { id: String? -> assets(id)?.takeIf { it.kind != MediaKind.IMAGE }?.durationMs }
        // A ripple that lengthens makes the room first; one that shortens closes it after.
        fun shifted(t: Timeline, by: Int) = if (by == 0) t else t.copy(clips = t.clips.map { c ->
            if (c.trackId in tracks && c.id !in group.map { it.id } && c.start >= (ends[c.trackId] ?: Int.MAX_VALUE)) c.copy(start = c.start + by) else c
        })
        var updated = if (ripple && delta > 0) shifted(timeline, delta) else timeline
        updated = trim(updated, clip.id, TrimEdge.END, clip.start + frames, 0, false, 0, sourceMs(clip.assetId), fps).timeline
        val got = updated.clip(clip.id)!!.durationFrames
        if (ripple) updated = shifted(updated, (got - clip.durationFrames) - if (delta > 0) delta else 0)
        if (got == clip.durationFrames) throw EditRejected(if (delta > 0) "片段无法再加长：素材或后面的片段挡住了" else "片段无法再缩短")
        val short = if (got != frames) "（${if (delta > 0) "素材或后面的片段只够" else "最短"} ${Timecode.format(got, fps)}）" else ""
        return EditResult(updated, clip.id, "“${clip.name}”的时长改为 ${Timecode.format(got, fps)}$short")
    }

    companion object {
        /**
         * Frames for a typed duration: a timecode (HH:MM:SS:FF, its leading fields optional — "3:00" is 3 seconds) or
         * seconds ("2.5"); null when it is neither.
         */
        fun parse(text: String, fps: Int): Int? {
            val t = text.trim().replace('：', ':').replace(';', ':')
            if (t.isEmpty()) return null
            if (':' in t) {
                val parts = t.split(':').map { it.toIntOrNull() ?: return null }
                if (parts.size > 4 || parts.any { it < 0 }) return null
                val (h, m, s, f) = List(4 - parts.size) { 0 } + parts
                if (f >= fps || s >= 60 || m >= 60) return null
                return ((h * 60 + m) * 60 + s) * fps + f
            }
            val seconds = t.removeSuffix("秒").removeSuffix("s").trim().toDoubleOrNull() ?: return null
            if (seconds < 0 || seconds > 24 * 3600) return null
            return (seconds * fps).roundToInt()
        }
    }
}
