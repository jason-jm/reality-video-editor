package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings

/**
 * Offline media in a render (release round R5): a clip whose file was moved or deleted renders as black (a picture) or
 * silence (a sound) — Resolve warns before it renders, and so does Deliver (`render-offline-note`), naming the clips.
 */
class OfflineRenderUseCase {

    /**
     * The clips in [start, end) (the whole timeline when null) whose media is offline: switched-on clips on switched-on
     * tracks, pictures when the picture is exported and sounds when the sound is.
     */
    fun clips(project: ProjectDocument, settings: RenderSettings, start: Int?, end: Int?): List<Clip> {
        val offline = project.media.filter { it.offline }.map { it.id }.toSet()
        if (offline.isEmpty()) return emptyList()
        val doc = CompoundClipUseCase.expanded(project)
        val timeline = doc.timeline
        val from = start ?: 0
        val until = end ?: doc.durationFrames
        return timeline.clips.filter { c ->
            c.assetId in offline && c.enabled && timeline.track(c.trackId)?.enabled != false && c.start < until && c.end > from &&
                when (c.kind) {
                    ClipKind.VIDEO -> settings.includeVideo
                    ClipKind.AUDIO -> (settings.includeAudio || !settings.includeVideo) && doc.mixer.isAudible(c.trackId)
                    else -> false
                }
        }.sortedBy { it.start }
    }

    /** What Deliver says about [clips]: how many, which (two named), and what they render as; null when none. */
    fun note(clips: List<Clip>): String? {
        if (clips.isEmpty()) return null
        val names = clips.map { it.name }.distinct()
        val named = names.take(2).joinToString("、") + if (names.size > 2) " 等" else ""
        val pictures = clips.any { it.kind == ClipKind.VIDEO }
        val sounds = clips.any { it.kind == ClipKind.AUDIO }
        val becomes = when {
            pictures && sounds -> "画面是黑场、没有声音"
            pictures -> "画面是黑场"
            else -> "没有声音"
        }
        return "${clips.size} 个片段的素材离线（$named），渲染时$becomes：先到媒体页重新链接"
    }
}
