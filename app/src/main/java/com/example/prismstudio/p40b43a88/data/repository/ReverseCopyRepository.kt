package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a reversed copy of one asset stands. */
sealed interface ReverseCopy {
    data class Making(val progress: Float) : ReverseCopy
    data class Ready(val uri: String) : ReverseCopy
    data class Failed(val reason: String) : ReverseCopy
}

/** Thrown when a reversed copy an export needs cannot be made; [message] is shown to the user. */
class ReverseCopyFailure(message: String) : Exception(message)

/**
 * Reversed copies of media for clips that play backwards: made once in the background, kept on disk
 * and shared by every clip (and project) using the same file.
 */
interface ReverseCopyRepository {
    /** Status by asset id, for the assets requested in this process. */
    val copies: StateFlow<Map<String, ReverseCopy>>

    /**
     * Starts making copies of [assets] that have none yet, one at a time. A failed copy is only tried
     * again when [retryFailed] is set (an explicit user action), so re-requests stay cheap.
     */
    fun request(assets: Collection<MediaAsset>, retryFailed: Boolean = false)

    /** Requests [assets] and suspends until every copy is ready; returns their uris by asset id. */
    suspend fun awaitReady(assets: Collection<MediaAsset>, onProgress: (Float) -> Unit): Map<String, String>

    /** Never makes copies: reversed clips play forwards. For previews and tests without media codecs. */
    object None : ReverseCopyRepository {
        private val empty = MutableStateFlow<Map<String, ReverseCopy>>(emptyMap()).asStateFlow()
        override val copies: StateFlow<Map<String, ReverseCopy>> = empty
        override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) = Unit
        override suspend fun awaitReady(assets: Collection<MediaAsset>, onProgress: (Float) -> Unit): Map<String, String> =
            if (assets.isEmpty()) emptyMap() else throw ReverseCopyFailure("当前环境无法生成倒放素材")
    }
}

/** Ready copies' uris by asset id. */
fun Map<String, ReverseCopy>.readyUris(): Map<String, String> =
    mapNotNull { (id, copy) -> (copy as? ReverseCopy.Ready)?.let { id to it.uri } }.toMap()
