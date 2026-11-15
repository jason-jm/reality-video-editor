package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Resolve's proxy media: small picture-only copies of footage, made one at a time in the background, kept on disk and
 * shared by every asset (and project) pointing at the same file.
 */
interface ProxyRepository {
    /** Status by asset id, for the assets looked at or asked for in this process. */
    val proxies: StateFlow<Map<String, ProxyStatus>>

    /** Looks for proxies already on disk for [assets] (made in an earlier session) without making any. */
    fun refresh(assets: Collection<MediaAsset>)

    /** Starts making proxies for [assets] that have none (a failed one is tried again: this is the user asking). */
    fun request(assets: Collection<MediaAsset>)

    /** Deletes [asset]'s proxy (and stops making it). */
    fun delete(asset: MediaAsset)

    /** Makes nothing: for previews and tests without media codecs. */
    object None : ProxyRepository {
        override val proxies: StateFlow<Map<String, ProxyStatus>> = MutableStateFlow<Map<String, ProxyStatus>>(emptyMap()).asStateFlow()
        override fun refresh(assets: Collection<MediaAsset>) = Unit
        override fun request(assets: Collection<MediaAsset>) = Unit
        override fun delete(asset: MediaAsset) = Unit
    }
}

/** For tests: requests stay [ProxyStatus.Making] until [finish] says how they ended. */
class FakeProxyRepository : ProxyRepository {
    private val state = MutableStateFlow<Map<String, ProxyStatus>>(emptyMap())
    override val proxies: StateFlow<Map<String, ProxyStatus>> = state.asStateFlow()
    val requested = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override fun refresh(assets: Collection<MediaAsset>) = Unit

    override fun request(assets: Collection<MediaAsset>) {
        assets.forEach { requested += it.id }
        state.update { m -> m + assets.associate { it.id to ProxyStatus.Making(0f) } }
    }

    override fun delete(asset: MediaAsset) {
        deleted += asset.id
        state.update { it - asset.id }
    }

    fun finish(assetId: String, status: ProxyStatus) = state.update { it + (assetId to status) }
}
