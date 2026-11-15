package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FlagRule
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SmartBin
import com.example.prismstudio.p40b43a88.domain.model.UsageRule

/**
 * Resolve's Smart Bins: saved searches over the media pool. A smart bin shows the media all its rules hold for — a kind,
 * a flag, used or unused on the project's timelines, part of the name, a keyword, Good Take — worked out afresh each time, so it follows imports,
 * flags and edits by itself. Making, changing and deleting one is the project's (one undo step); the media never move.
 */
class SmartBinsUseCase(private val ids: IdGenerator) {

    data class Result(val doc: ProjectDocument, val message: String, val binId: String? = null)

    /** What the smart bin sheet edits: a smart bin without its id. */
    data class Rules(
        val name: String,
        val kind: MediaKind? = null,
        val flag: FlagRule = FlagRule.ANY,
        val usage: UsageRule = UsageRule.ANY,
        val nameContains: String = "",
        val keyword: String = "",
        val goodTake: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = kind == null && flag == FlagRule.ANY && usage == UsageRule.ANY && nameContains.isBlank() && keyword.isBlank() && !goodTake

        fun of(id: String) = SmartBin(id, name.trim(), kind, flag, usage, nameContains.trim(), keyword.trim(), goodTake)

        companion object {
            fun of(bin: SmartBin) = Rules(bin.name, bin.kind, bin.flag, bin.usage, bin.nameContains, bin.keyword, bin.goodTake)
        }
    }

    /**
     * How many times each piece of media is used (Resolve's Usage column): its clips over every timeline of the project, a
     * picture and the sound linked to it counting once.
     */
    fun usage(doc: ProjectDocument): Map<String, Int> =
        timelines(doc).flatMap { (_, clips) -> clips }.groupBy { it.first }.mapValues { (_, uses) -> uses.size }

    /** Where [assetId] is used: each timeline that uses it, with how many times. */
    fun usedIn(doc: ProjectDocument, assetId: String): List<Pair<String, Int>> =
        timelines(doc).mapNotNull { (name, uses) -> uses.count { it.first == assetId }.takeIf { it > 0 }?.let { name to it } }

    /** `source-meta` 使用: "未使用", or "3 次 · 时间线 1 ×2、预告" (the timeline's name, × its count above one). */
    fun usageLabel(doc: ProjectDocument, assetId: String): String {
        val where = usedIn(doc, assetId)
        if (where.isEmpty()) return "未使用"
        return "${where.sumOf { it.second }} 次 · " + where.joinToString("、") { (name, n) -> if (n > 1) "$name ×$n" else name }
    }

    fun matches(rules: Rules, asset: MediaAsset, usage: Map<String, Int>): Boolean {
        val uses = usage[asset.id] ?: 0
        return (rules.kind == null || asset.kind == rules.kind) &&
            rules.flag.accepts(asset.flags) &&
            when (rules.usage) {
                UsageRule.ANY -> true
                UsageRule.USED -> uses > 0
                UsageRule.UNUSED -> uses == 0
            } &&
            (rules.nameContains.isBlank() || asset.name.contains(rules.nameContains.trim(), ignoreCase = true)) &&
            (rules.keyword.isBlank() || asset.metadata.keywords.any { it.contains(rules.keyword.trim(), ignoreCase = true) }) &&
            (!rules.goodTake || asset.metadata.goodTake)
    }

    /** The media [rules] hold for, in pool order. */
    fun media(doc: ProjectDocument, rules: Rules, usage: Map<String, Int> = usage(doc)): List<MediaAsset> =
        doc.media.filter { matches(rules, it, usage) }

    fun media(doc: ProjectDocument, bin: SmartBin, usage: Map<String, Int> = usage(doc)) = media(doc, Rules.of(bin), usage)

    /** 智能媒体夹 N: one past the highest such number in use. */
    fun suggestedName(doc: ProjectDocument): String {
        val highest = doc.smartBins.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "智能媒体夹 ${highest + 1}"
    }

    /** Why [rules] cannot be smart bin [id] (null: a new one), or null when they can. */
    fun problem(doc: ProjectDocument, id: String?, rules: Rules): String? {
        val name = rules.name.trim()
        return when {
            name.isEmpty() -> "名称不能为空"
            name.length > MAX_NAME -> "名称最多 $MAX_NAME 个字"
            doc.smartBins.any { it.id != id && it.name == name } -> "已有同名的智能媒体夹"
            rules.nameContains.trim().length > MAX_NAME -> "名称条件最多 $MAX_NAME 个字"
            rules.keyword.trim().length > MAX_NAME -> "关键词条件最多 $MAX_NAME 个字"
            rules.isEmpty -> "请至少设置一个条件"
            else -> null
        }
    }

    fun create(doc: ProjectDocument, rules: Rules): Result {
        if (doc.smartBins.size >= MAX) throw EditRejected("最多 $MAX 个智能媒体夹")
        problem(doc, null, rules)?.let { throw EditRejected(it) }
        val bin = rules.of(ids.next("smart"))
        val next = doc.copy(smartBins = doc.smartBins + bin)
        return Result(next, "已新建智能媒体夹“${bin.name}”（${media(next, bin).size} 个素材）", bin.id)
    }

    fun update(doc: ProjectDocument, id: String, rules: Rules): Result {
        val old = bin(doc, id)
        problem(doc, id, rules)?.let { throw EditRejected(it) }
        val bin = rules.of(id)
        if (bin == old) throw EditRejected("条件没有变化")
        val next = doc.copy(smartBins = doc.smartBins.map { if (it.id == id) bin else it })
        return Result(next, "已更新智能媒体夹“${bin.name}”（${media(next, bin).size} 个素材）", id)
    }

    fun delete(doc: ProjectDocument, id: String): Result {
        val bin = bin(doc, id)
        return Result(doc.copy(smartBins = doc.smartBins.filterNot { it.id == id }), "已删除智能媒体夹“${bin.name}”，素材仍在媒体池中")
    }

    /** The rules in words: "视频 · 红色旗标 · 未使用 · 名称含“夜”" (全部素材 with none set). */
    fun describe(rules: Rules): String = listOfNotNull(
        rules.kind?.let { KIND_LABELS.getValue(it) },
        rules.flag.takeIf { it != FlagRule.ANY }?.label,
        rules.usage.takeIf { it != UsageRule.ANY }?.label,
        rules.nameContains.trim().takeIf { it.isNotEmpty() }?.let { "名称含“$it”" },
        rules.keyword.trim().takeIf { it.isNotEmpty() }?.let { "关键词“$it”" },
        "好条".takeIf { rules.goodTake },
    ).joinToString(" · ").ifEmpty { "全部素材" }

    fun describe(bin: SmartBin) = describe(Rules.of(bin))

    /** Each timeline's name and its media uses (asset id, link group): a picture and its linked sound are one use. */
    private fun timelines(doc: ProjectDocument): List<Pair<String, List<Pair<String, String>>>> =
        doc.timelineEntries.mapNotNull { entry ->
            val timeline = if (entry.open) doc.timeline else doc.timelines.firstOrNull { it.id == entry.id }?.timeline ?: return@mapNotNull null
            entry.name to timeline.clips.mapNotNull { c -> c.assetId?.let { it to (c.linkId ?: c.id) } }.distinct()
        }

    private fun bin(doc: ProjectDocument, id: String) = doc.smartBins.firstOrNull { it.id == id } ?: throw EditRejected("智能媒体夹不存在")

    companion object {
        const val MAX = 20
        const val MAX_NAME = 20
        val KIND_LABELS = mapOf(MediaKind.VIDEO to "视频", MediaKind.AUDIO to "音频", MediaKind.IMAGE to "图片")
        private val NUMBERED = Regex("智能媒体夹 (\\d+)")
    }
}
