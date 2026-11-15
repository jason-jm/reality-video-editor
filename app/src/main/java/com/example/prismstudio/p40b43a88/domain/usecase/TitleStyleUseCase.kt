package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Changes the look of the title [clipId] — Resolve's Text title inspector: colour, weight, outline,
 * drop shadow, the box and its opacity, and the entrance. The words, size and template stay; a
 * subtitle's look belongs to its track ([SubtitleStyleUseCase]).
 */
class TitleStyleUseCase {

    operator fun invoke(timeline: Timeline, clipId: String, change: (TitleStyle) -> TitleStyle): Timeline {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择标题")
        val style = clip.title ?: throw EditRejected("所选片段不是标题")
        if (style.template == TitleTemplate.SUBTITLE) throw EditRejected("字幕的样式在轨道样式里调整")
        if (timeline.track(clip.trackId)?.locked == true) throw EditRejected("轨道已锁定")
        val next = change(style).copy(text = style.text, sizePx = style.sizePx, template = style.template)
        return timeline.replaceClip(clip.copy(title = next.copy(boxOpacityPercent = next.boxOpacityPercent.coerceIn(0, 100))))
    }
}
