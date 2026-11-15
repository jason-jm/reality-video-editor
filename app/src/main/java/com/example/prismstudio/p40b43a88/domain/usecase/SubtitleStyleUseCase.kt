package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate

/** Changes the style of the subtitle track the subtitle [clipId] is on (Resolve's Track Style): every subtitle there follows. */
class SubtitleStyleUseCase {

    operator fun invoke(timeline: Timeline, clipId: String, change: (SubtitleStyle) -> SubtitleStyle): Timeline {
        val clip = timeline.clip(clipId)?.takeIf { it.title?.template == TitleTemplate.SUBTITLE } ?: throw EditRejected("请先选择一条字幕")
        val track = timeline.track(clip.trackId) ?: throw EditRejected("字幕轨道不存在")
        if (track.locked) throw EditRejected("轨道已锁定")
        return timeline.copy(tracks = timeline.tracks.map { if (it.id == track.id) it.copy(subtitleStyle = change(it.subtitleStyle)) else it })
    }
}
