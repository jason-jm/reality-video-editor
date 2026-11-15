package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Bytes
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlin.math.roundToInt

/** Where a piece of media's proxy stands (Resolve's proxy media). */
sealed interface ProxyStatus {
    data object None : ProxyStatus
    data class Making(val progress: Float) : ProxyStatus
    data class Ready(val uri: String, val width: Int, val height: Int, val sizeBytes: Long) : ProxyStatus
    data class Failed(val reason: String) : ProxyStatus
}

/**
 * Resolve's Generate Proxy Media and Proxy Handling: footage gets a small copy — its short side [SHORT_SIDE] pixels, picture
 * only — that the monitors play instead of the camera original while the project prefers proxies, so 4K plays smoothly on
 * the headset. Renders, stills, scopes and sound always use the originals. Stills need no proxy.
 */
class ProxyMediaUseCase {

    /** Media a proxy helps: footage that is online. */
    fun candidates(doc: ProjectDocument): List<MediaAsset> = doc.media.filter(::canProxy)

    fun canProxy(asset: MediaAsset) = asset.kind == MediaKind.VIDEO && !asset.offline

    /** The proxy's size for [asset]: its short side [SHORT_SIDE] (never larger than the original), even numbers. */
    fun size(asset: MediaAsset): Pair<Int, Int> {
        val w = asset.width.coerceAtLeast(2)
        val h = asset.height.coerceAtLeast(2)
        val scale = minOf(1.0, SHORT_SIDE.toDouble() / minOf(w, h))
        fun even(v: Double) = ((v / 2).roundToInt() * 2).coerceAtLeast(2)
        return even(w * scale) to even(h * scale)
    }

    /** What the monitors play instead of the originals: ready proxies by asset id, while the project prefers proxies. */
    fun previewUris(doc: ProjectDocument, statuses: Map<String, ProxyStatus>): Map<String, String> {
        if (!doc.useProxies) return emptyMap()
        return candidates(doc).mapNotNull { a -> (statuses[a.id] as? ProxyStatus.Ready)?.let { a.id to it.uri } }.toMap()
    }

    /** How many of the project's footage have a proxy ready. */
    fun readyCount(doc: ProjectDocument, statuses: Map<String, ProxyStatus>): Int = candidates(doc).count { statuses[it.id] is ProxyStatus.Ready }

    /** `source-proxy-chip`: 无代理 / 生成中 45% / 代理 960×540 · 12 MB / 生成失败 / 图片无需代理. */
    fun label(asset: MediaAsset, status: ProxyStatus): String = when {
        asset.kind == MediaKind.AUDIO -> "音频无需代理"
        asset.kind == MediaKind.IMAGE -> "图片无需代理"
        asset.offline -> "素材离线"
        else -> when (status) {
            ProxyStatus.None -> "无代理"
            is ProxyStatus.Making -> "生成中 ${(status.progress * 100).roundToInt()}%"
            is ProxyStatus.Ready -> "代理 ${status.width}×${status.height} · ${Bytes.label(status.sizeBytes)}"
            is ProxyStatus.Failed -> "生成失败"
        }
    }

    /** The playback menu's line for proxies: whether they are used, and how many there are. */
    fun handlingNote(doc: ProjectDocument, statuses: Map<String, ProxyStatus>): String {
        val all = candidates(doc).size
        val ready = readyCount(doc, statuses)
        return when {
            all == 0 -> "项目里没有需要代理的视频"
            doc.useProxies -> "已开：$ready / $all 个视频用代理播放，渲染始终用原片"
            else -> "$ready / $all 个视频有代理；在媒体页为素材生成代理"
        }
    }

    companion object {
        /** A proxy's short side: 540 lines (960×540 for HD and 4K footage). */
        const val SHORT_SIDE = 540

        /** Its picture bitrate: plenty for a monitor at proxy size. */
        const val BITRATE = 3_000_000
    }
}
