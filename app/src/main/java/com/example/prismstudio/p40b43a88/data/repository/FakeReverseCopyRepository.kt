package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [ReverseCopyRepository]: records requests; statuses advance only through [set]. */
class FakeReverseCopyRepository : ReverseCopyRepository {
    private val state = MutableStateFlow<Map<String, ReverseCopy>>(emptyMap())
    override val copies: StateFlow<Map<String, ReverseCopy>> = state.asStateFlow()

    /** Asset ids of each request, with its retry flag. */
    val requests = mutableListOf<Pair<List<String>, Boolean>>()

    override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) {
        requests += assets.map { it.id } to retryFailed
        state.update { map ->
            map + assets.filter { map[it.id] == null || (retryFailed && map[it.id] is ReverseCopy.Failed) }
                .associate { it.id to ReverseCopy.Making(0f) }
        }
    }

    fun set(assetId: String, copy: ReverseCopy) = state.update { it + (assetId to copy) }

    override suspend fun awaitReady(assets: Collection<MediaAsset>, onProgress: (Float) -> Unit): Map<String, String> {
        request(assets, retryFailed = true)
        val map = state.value
        assets.firstNotNullOfOrNull { map[it.id] as? ReverseCopy.Failed }?.let { throw ReverseCopyFailure(it.reason) }
        onProgress(1f)
        return map.readyUris().filterKeys { id -> assets.any { it.id == id } }
    }
}
