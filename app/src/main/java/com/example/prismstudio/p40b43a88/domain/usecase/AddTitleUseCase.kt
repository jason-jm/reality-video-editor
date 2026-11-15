package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/**
 * Places a title at the playhead, or in the first free slot after it. A three-second title goes on V2
 * (another upper video track when V2 was deleted, or a new one when V1 is the only video track); a
 * two-second subtitle goes on the subtitle track, which is made the first time it is needed.
 */
class AddTitleUseCase(private val ids: IdGenerator) {

    operator fun invoke(timeline: Timeline, template: TitleTemplate, atFrame: Int, fps: Int): EditResult {
        val subtitle = template == TitleTemplate.SUBTITLE
        val track = if (subtitle) {
            // The subtitle track on screen takes a new subtitle (one language at a time shows).
            timeline.tracks.firstOrNull { it.kind == TrackKind.SUBTITLE && it.enabled }
                ?: timeline.tracks.firstOrNull { it.kind == TrackKind.SUBTITLE }
                ?: timeline.nextTrack(TrackKind.SUBTITLE).copy(language = com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage.DEFAULT)
        } else {
            timeline.track(Timeline.V2)
                ?: timeline.tracks.firstOrNull { it.kind == TrackKind.VIDEO && it.id != Timeline.V1 }
                ?: timeline.nextTrack(TrackKind.VIDEO)
        }
        val created = timeline.track(track.id) == null
        val base = if (created) timeline.withTrack(track) else timeline
        val title = place(base, track, template, atFrame, if (subtitle) SUBTITLE_SECONDS * fps else TITLE_SECONDS * fps)
        val what = if (subtitle) "字幕" else "标题"
        return EditResult(
            timeline = base.copy(clips = base.clips + title),
            selectedClipId = title.id,
            message = "已在 ${Timecode.format(title.start, fps)} 添加$what" + if (created) "（新建轨道 ${track.name}）" else "",
        )
    }

    /** The next subtitle right after [afterClipId] (or the first free slot after it), for typing dialogue line by line. */
    fun nextSubtitle(timeline: Timeline, afterClipId: String, fps: Int): EditResult {
        val previous = timeline.clip(afterClipId)?.takeIf { it.title?.template == TitleTemplate.SUBTITLE }
            ?: throw EditRejected("请先选择一条字幕")
        val track = timeline.track(previous.trackId) ?: throw EditRejected("轨道不存在")
        val next = place(timeline, track, TitleTemplate.SUBTITLE, previous.end, SUBTITLE_SECONDS * fps)
        return EditResult(timeline.copy(clips = timeline.clips + next), next.id, "已在 ${Timecode.format(next.start, fps)} 添加下一条字幕")
    }

    private fun place(timeline: Timeline, track: Track, template: TitleTemplate, atFrame: Int, length: Int): Clip {
        timeline.requireTrackUnlocked(track.id)
        var start = atFrame.coerceAtLeast(0)
        for (other in timeline.clipsOn(track.id)) {
            if (start < other.end && start + length > other.start) start = other.end
        }
        // The words a new title starts with are picture content: in the language the studio shows ("Title").
        val text = I18n.translate(template.defaultText)
        return Clip(
            id = ids.next("t"),
            trackId = track.id,
            kind = ClipKind.TITLE,
            name = "${if (template == TitleTemplate.SUBTITLE) "字幕" else "标题"}：$text",
            start = start,
            durationFrames = length,
            title = TitleStyle(text, template.sizePx, template),
        )
    }

    private companion object {
        const val TITLE_SECONDS = 3
        const val SUBTITLE_SECONDS = 2
    }
}
