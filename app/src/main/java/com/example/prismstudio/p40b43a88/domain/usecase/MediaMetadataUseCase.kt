package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument

/**
 * Resolve's clip metadata (the Metadata panel's Shot & Scene group) for a piece of media: the slate — scene, shot, take —,
 * keywords, a note and Good Take. The sheet edits a [Draft] (keywords as typed); saving one is the project's (one undo
 * step). The media pool search and smart bins read what is saved.
 */
class MediaMetadataUseCase {

    data class Result(val doc: ProjectDocument, val message: String)

    /** What the sheet edits: the fields as typed, keywords one string. */
    data class Draft(
        val scene: String = "",
        val shot: String = "",
        val take: String = "",
        val keywords: String = "",
        val notes: String = "",
        val goodTake: Boolean = false,
    ) {
        companion object {
            fun of(m: ClipMetadata) = Draft(m.scene, m.shot, m.take, m.keywords.joinToString("，"), m.notes, m.goodTake)
        }
    }

    /** The keywords in [text]: split at commas, 、 and semicolons, trimmed, each once (case ignored), in order. */
    fun keywords(text: String): List<String> =
        text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    /** Why [draft] cannot be saved, or null. */
    fun problem(draft: Draft): String? {
        val words = keywords(draft.keywords)
        return when {
            listOf(draft.scene, draft.shot, draft.take).any { it.trim().length > MAX_SLATE } -> "场次、镜头、条次各最多 $MAX_SLATE 个字"
            words.size > MAX_KEYWORDS -> "最多 $MAX_KEYWORDS 个关键词"
            words.any { it.length > MAX_KEYWORD } -> "每个关键词最多 $MAX_KEYWORD 个字"
            draft.notes.trim().length > MAX_NOTES -> "备注最多 $MAX_NOTES 个字"
            else -> null
        }
    }

    fun metadata(draft: Draft) = ClipMetadata(
        scene = draft.scene.trim(),
        shot = draft.shot.trim(),
        take = draft.take.trim(),
        keywords = keywords(draft.keywords),
        notes = draft.notes.trim(),
        goodTake = draft.goodTake,
    )

    /** Saves [draft] as [assetId]'s metadata. */
    fun apply(doc: ProjectDocument, assetId: String, draft: Draft): Result {
        val asset = doc.asset(assetId) ?: throw EditRejected("请先选择素材")
        problem(draft)?.let { throw EditRejected(it) }
        val meta = metadata(draft)
        if (meta == asset.metadata) throw EditRejected("场记没有变化")
        val media = doc.media.map { if (it.id == assetId) it.copy(metadata = meta) else it }
        val message = if (meta.isEmpty) "已清除“${asset.name}”的场记" else "已更新“${asset.name}”的场记"
        return Result(doc.copy(media = media), message)
    }

    /** `source-slate-chip`: "场 3 · 镜 2A · 条 4 · 好条", "好条", or 未填写. */
    fun slateLabel(m: ClipMetadata): String = listOfNotNull(
        m.scene.takeIf { it.isNotEmpty() }?.let { "场 $it" },
        m.shot.takeIf { it.isNotEmpty() }?.let { "镜 $it" },
        m.take.takeIf { it.isNotEmpty() }?.let { "条 $it" },
        "好条".takeIf { m.goodTake },
    ).joinToString(" · ").ifEmpty { if (m.keywords.isEmpty() && m.notes.isEmpty()) "未填写" else "无场次" }

    /** `source-meta` rows for what is set: 关键词 and 备注. */
    fun rows(m: ClipMetadata): List<Pair<String, String>> = listOfNotNull(
        m.keywords.takeIf { it.isNotEmpty() }?.let { "关键词" to it.joinToString("、") },
        m.notes.takeIf { it.isNotEmpty() }?.let { "备注" to it },
    )

    companion object {
        const val MAX_SLATE = 12
        const val MAX_KEYWORDS = 12
        const val MAX_KEYWORD = 20
        const val MAX_NOTES = 200
        private val SEPARATORS = Regex("[,，、;；]")
    }
}
