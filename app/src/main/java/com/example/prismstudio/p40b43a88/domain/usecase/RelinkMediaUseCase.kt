package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Resolve's Relink Media: media whose file was moved, renamed or deleted (offline) found again on the device — by its file
 * name, checked against its length and size, or a renamed copy by its length and size alone — or pointed at a file the user
 * picks. The media keeps its id, so every clip made from it, its flags, bin, slate and audio sync stay; the file's own facts
 * (where it is, size, length, picture, codecs, colour tags) come from the new file.
 */
class RelinkMediaUseCase {

    /** How a device file fits an offline media's: the [sure] ones are taken without asking. */
    enum class Match(val label: String, val sure: Boolean) {
        SAME_FILE("同名，大小和时长一致", true),
        SAME_NAME("同名，时长一致", true),
        RENAMED("改过名，大小和时长一致", true),
        NAME_ONLY("同名，但时长或大小不同", false),
    }

    data class Candidate(val item: DeviceMediaItem, val match: Match) {
        /** Where it is and how it fits: "Movies/Trip/clip.mp4 · 同名，时长一致". */
        val label: String get() = "${item.relativePath}${item.name} · ${match.label}"
    }

    data class Result(val doc: ProjectDocument, val message: String)

    /** The device files that may be [asset]'s, best first: the surest match, then its old folder, then the nearest length. */
    fun candidates(asset: MediaAsset, device: List<DeviceMediaItem>): List<Candidate> = device
        .filter { it.kind == asset.kind && it.uri != asset.uri }
        .mapNotNull { item -> matchOf(asset, item)?.let { Candidate(item, it) } }
        .sortedWith(
            compareBy<Candidate> { it.match.ordinal }
                .thenBy { if (it.item.relativePath == asset.relativePath) 0 else 1 }
                .thenBy { abs(it.item.durationMs - asset.durationMs) },
        )
        .take(MAX_CANDIDATES)

    /** The candidate taken without asking: the first, when its match is a sure one. */
    fun pick(candidates: List<Candidate>): Candidate? = candidates.firstOrNull()?.takeIf { it.match.sure }

    private fun matchOf(asset: MediaAsset, item: DeviceMediaItem): Match? {
        val sameName = item.name.equals(asset.name, ignoreCase = true)
        val sameSize = asset.sizeBytes > 0 && item.sizeBytes == asset.sizeBytes
        // A picture has no length: its size has to agree.
        val sameLength = asset.kind == MediaKind.IMAGE || sameLength(asset.durationMs, item.durationMs)
        return when {
            sameName && sameSize && sameLength -> Match.SAME_FILE
            sameName && sameLength && asset.kind != MediaKind.IMAGE -> Match.SAME_NAME
            !sameName && sameSize && sameLength -> Match.RENAMED
            sameName -> Match.NAME_ONLY
            else -> null
        }
    }

    /** Two lengths of one recording: within 1 % or 50 ms (MediaStore and the file's own header round differently). */
    fun sameLength(a: Long, b: Long): Boolean = abs(a - b) <= max(LENGTH_SLACK_MS, max(a, b) / 100)

    /** [assetId] pointed at [file] (the new file, probed): one undo step, and what to tell the user. */
    fun relink(doc: ProjectDocument, assetId: String, file: MediaAsset): Result {
        val updated = relinked(doc, assetId, file)
        val asset = updated.asset(assetId)!!
        val short = shortfall(updated, asset)
        val where = asset.relativePath.ifEmpty { "所选位置" }
        val message = "已重新链接“${asset.name}”（$where）" + (short?.let { "；注意：新文件比片段用到的短 ${seconds(it)}，超出的部分没有画面和声音" } ?: "")
        return Result(updated, message)
    }

    /** Several media relinked at once ([files] by asset id): one undo step. */
    fun relinkAll(doc: ProjectDocument, files: Map<String, MediaAsset>): Result {
        if (files.isEmpty()) throw EditRejected("没有找到可以重新链接的文件")
        var updated = doc
        files.forEach { (id, file) -> updated = relinked(updated, id, file) }
        val short = files.keys.count { id -> shortfall(updated, updated.asset(id)!!) != null }
        val left = updated.media.count { it.offline }
        val message = buildString {
            append("已重新链接 ${files.size} 个素材")
            if (short > 0) append("，其中 $short 个比片段用到的短")
            if (left > 0) append("；还有 $left 个离线")
        }
        return Result(updated, message)
    }

    private fun relinked(doc: ProjectDocument, assetId: String, file: MediaAsset): ProjectDocument {
        val old = doc.asset(assetId) ?: throw EditRejected("素材已不存在")
        if (file.kind != old.kind) throw EditRejected("类型不同：原素材是${old.kind.label}，所选文件是${file.kind.label}")
        val linked = file.copy(
            id = old.id,
            flags = old.flags,
            binId = old.binId,
            metadata = old.metadata,
            syncedAudio = old.syncedAudio,
            inputColor = old.inputColor,
            offline = false,
        )
        return doc.copy(media = doc.media.map { if (it.id == assetId) linked else it })
    }

    /**
     * How much the clips of [asset] run past the end of its (new) file, in ms of source — null when the file is long
     * enough (a still, or a file at least as long as the furthest any clip in any timeline reaches).
     */
    fun shortfall(doc: ProjectDocument, asset: MediaAsset): Long? {
        if (asset.kind == MediaKind.IMAGE) return null
        val over = usedMs(doc, asset.id) - asset.durationMs
        return over.takeIf { it > LENGTH_SLACK_MS }
    }

    /** The furthest any clip of [assetId], in this timeline or the project's others, reaches into its file (ms). */
    fun usedMs(doc: ProjectDocument, assetId: String): Long {
        val timelines = listOf(doc.timeline to doc.format.fps) +
            doc.timelines.filterIndexed { i, _ -> i != doc.timelineIndex }.map { it.timeline to it.format.fps }
        return timelines.maxOfOrNull { (timeline, fps) ->
            timeline.clips.filter { it.assetId == assetId }.maxOfOrNull { it.sourceInMs + it.sourceDurationMs(fps) } ?: 0L
        } ?: 0L
    }

    companion object {
        /** A shortfall in words: "1.5 秒". */
        fun seconds(ms: Long): String = String.format(Locale.US, "%.1f 秒", ms / 1000.0)

        const val MAX_CANDIDATES = 5
        const val LENGTH_SLACK_MS = 50L
    }
}

private val MediaKind.label: String
    get() = when (this) {
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
    }
