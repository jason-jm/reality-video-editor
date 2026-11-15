package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument

/**
 * Resolve's flags, on source media: a flag colour put on or taken off media (picked in the media pool, or through the
 * clips made from it), or every flag cleared. A flag belongs to the media, so all of its clips show it.
 */
class FlagUseCase {

    data class Result(val doc: ProjectDocument, val message: String)

    /** The media of [clipIds], each once, in timeline order; titles and gaps have none. */
    fun mediaOf(doc: ProjectDocument, clipIds: Collection<String>): List<String> {
        if (clipIds.isEmpty()) throw EditRejected("请先在时间线上选择片段")
        val ids = clipIds.toSet()
        val media = doc.timeline.clips.filter { it.id in ids }.sortedBy { it.start }.mapNotNull { it.assetId }.distinct()
            .filter { id -> doc.media.any { it.id == id } }
        return media.ifEmpty { throw EditRejected("所选片段没有素材，无法标记旗标") }
    }

    /** [color] on all of [assetIds] — or, when every one already has it, off all of them. */
    fun toggle(doc: ProjectDocument, assetIds: Collection<String>, color: FlagColor): Result {
        val ids = assetIds.toSet()
        val chosen = doc.media.filter { it.id in ids }
        if (chosen.isEmpty()) throw EditRejected("请先选择素材")
        val remove = chosen.all { color in it.flags }
        val media = doc.media.map { asset ->
            if (asset.id !in ids) asset
            else asset.copy(flags = if (remove) asset.flags - color else (asset.flags + color).distinct().sorted())
        }
        val many = if (chosen.size > 1) " ${chosen.size} 个素材的" else ""
        val message = if (remove) "已移除$many${color.label}色旗标" else if (many.isEmpty()) "已为素材加上${color.label}色旗标" else "已为 ${chosen.size} 个素材加上${color.label}色旗标"
        return Result(doc.copy(media = media), message)
    }

    /** Every flag off [assetIds]. */
    fun clear(doc: ProjectDocument, assetIds: Collection<String>): Result {
        val ids = assetIds.toSet()
        val flagged = doc.media.filter { it.id in ids && it.flags.isNotEmpty() }
        if (flagged.isEmpty()) throw EditRejected("所选素材没有旗标")
        val media = doc.media.map { if (it.id in ids) it.copy(flags = emptyList()) else it }
        return Result(doc.copy(media = media), if (flagged.size > 1) "已清除 ${flagged.size} 个素材的旗标" else "已清除旗标")
    }
}
