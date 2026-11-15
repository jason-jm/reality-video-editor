package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/** Which clips `edit-index` lists (`edit-index-filter`). */
enum class EditIndexFilter(val label: String) { ALL("全部"), PICTURE("画面"), SOUND("声音") }

/**
 * Resolve's Edit Index: the open timeline's clips as edit events — numbered in timeline order (by where they start, then
 * by track, the top first), each with its track, record in and out, where its source starts and its length — the list an
 * EDL is made of. A tap takes the playhead to the event and selects its clip.
 */
class EditIndexUseCase {

    data class Row(
        val number: Int,
        val clipId: String,
        val name: String,
        val track: String,
        val kind: ClipKind,
        val recordIn: Int,
        val recordOut: Int,
        /** Where the clip's source starts, or what it plays instead of a file (标题, 复合片段 …). */
        val source: String,
        val enabled: Boolean,
    ) {
        val frames: Int get() = recordOut - recordIn
    }

    /** [doc]'s open timeline's events that [filter] keeps, numbered from 1. */
    fun rows(doc: ProjectDocument, filter: EditIndexFilter = EditIndexFilter.ALL): List<Row> {
        val t = doc.timeline
        val fps = doc.format.fps.coerceAtLeast(1)
        val order = t.tracks.withIndex().associate { (i, track) -> track.id to i }
        return t.clips
            .filter { c ->
                val kind = t.track(c.trackId)?.kind
                when (filter) {
                    EditIndexFilter.ALL -> true
                    EditIndexFilter.PICTURE -> kind == TrackKind.VIDEO || kind == TrackKind.SUBTITLE
                    EditIndexFilter.SOUND -> kind == TrackKind.AUDIO
                }
            }
            .sortedWith(compareBy({ it.start }, { order[it.trackId] ?: Int.MAX_VALUE }))
            .mapIndexed { i, c ->
                val source = when {
                    c.compoundId != null -> when (c.compoundId) {
                        in doc.multicamIds -> "多机位 · 机位 ${c.angle + 1}"
                        in doc.compoundIds -> "复合片段"
                        else -> "嵌套时间线"
                    }
                    c.kind == ClipKind.TITLE -> if (t.track(c.trackId)?.kind == TrackKind.SUBTITLE) "字幕" else "标题"
                    c.assetId == null -> "生成"
                    else -> "源 " + Timecode.format(Timecode.nearestFrame(c.sourceInMs, fps), fps)
                }
                Row(i + 1, c.id, c.name, t.track(c.trackId)?.name ?: c.trackId, c.kind, c.start, c.end, source, c.enabled)
            }
    }

    /** `edit-index-summary`: how many events and of which kind, or that there are none. */
    fun summary(doc: ProjectDocument, rows: List<Row>, filter: EditIndexFilter): String {
        if (rows.isEmpty()) return if (doc.timeline.clips.isEmpty()) "时间线上还没有片段" else "没有${filter.label}片段"
        val pictures = rows.count { it.kind != ClipKind.AUDIO }
        val sounds = rows.size - pictures
        val parts = listOfNotNull(pictures.takeIf { it > 0 }?.let { "画面 $it" }, sounds.takeIf { it > 0 }?.let { "声音 $it" })
        return "${rows.size} 个片段 · ${parts.joinToString(" · ")}"
    }
}
