package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.UserBin
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator

/**
 * Resolve's bins in the media pool: the user makes bins, files media in them (a piece of media sits in one bin, or in the
 * pool's root), renames and deletes them. Deleting a bin keeps its media: they go back to the root. Each change is the
 * project's (one undo step).
 */
class MediaBinsUseCase(private val ids: IdGenerator) {

    data class Result(val doc: ProjectDocument, val message: String, val binId: String? = null)

    fun create(doc: ProjectDocument): Result {
        if (doc.bins.size >= MAX) throw EditRejected("最多 $MAX 个媒体夹")
        val highest = doc.bins.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val bin = UserBin(ids.next("bin"), "媒体夹 ${highest + 1}")
        return Result(doc.copy(bins = doc.bins + bin), "已新建${bin.name}", bin.id)
    }

    /** Why [text] cannot name bin [id], or null when it can (unchanged counts as fine: nothing to do). */
    fun problem(doc: ProjectDocument, id: String, text: String): String? {
        val name = text.trim()
        return when {
            name.isEmpty() -> "名称不能为空"
            name.length > MAX_NAME -> "名称最多 $MAX_NAME 个字"
            doc.bins.any { it.id != id && it.name == name } -> "已有同名的媒体夹"
            else -> null
        }
    }

    fun rename(doc: ProjectDocument, id: String, text: String): Result {
        val bin = bin(doc, id)
        problem(doc, id, text)?.let { throw EditRejected(it) }
        val name = text.trim()
        if (name == bin.name) throw EditRejected("名称没有变化")
        return Result(doc.copy(bins = doc.bins.map { if (it.id == id) it.copy(name = name) else it }), "已将“${bin.name}”重命名为“$name”", id)
    }

    fun delete(doc: ProjectDocument, id: String): Result {
        val bin = bin(doc, id)
        val inside = count(doc, id)
        val media = doc.media.map { if (it.binId == id) it.copy(binId = null) else it }
        val back = if (inside > 0) "，其中 $inside 个素材回到媒体池" else ""
        return Result(doc.copy(bins = doc.bins.filterNot { it.id == id }, media = media), "已删除媒体夹“${bin.name}”$back")
    }

    /** Files [assetId] in bin [binId] (null: the root). */
    fun move(doc: ProjectDocument, assetId: String, binId: String?): Result {
        val asset = doc.asset(assetId) ?: throw EditRejected("请先选择素材")
        val target = binId?.let { bin(doc, it) }
        if (asset.binId == binId || (target == null && doc.bins.none { it.id == asset.binId })) {
            throw EditRejected(if (target == null) "“${asset.name}”不在媒体夹里" else "“${asset.name}”已在“${target.name}”里")
        }
        val media = doc.media.map { if (it.id == assetId) it.copy(binId = binId) else it }
        val message = if (target == null) "已将“${asset.name}”移出媒体夹" else "已将“${asset.name}”放入“${target.name}”"
        return Result(doc.copy(media = media), message, binId)
    }

    /** The media filed in bin [id]. */
    fun count(doc: ProjectDocument, id: String): Int = doc.media.count { it.binId == id }

    /** The bin [assetId] is filed in, if it still exists. */
    fun binOf(doc: ProjectDocument, assetId: String?): UserBin? = doc.asset(assetId)?.binId?.let { b -> doc.bins.firstOrNull { it.id == b } }

    private fun bin(doc: ProjectDocument, id: String) = doc.bins.firstOrNull { it.id == id } ?: throw EditRejected("媒体夹不存在")

    companion object {
        const val MAX = 20
        const val MAX_NAME = 20
        private val NUMBERED = Regex("媒体夹 (\\d+)")
    }
}
