package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind

sealed interface DeviceMediaResult {
    /** What could be listed; [missing] the kinds left out because their permission was refused (some were granted). */
    data class Items(
        val items: List<DeviceMediaItem>,
        val missing: Set<MediaKind> = emptySet(),
        val limitedVisualAccess: Boolean = false,
    ) : DeviceMediaResult
    data object PermissionRequired : DeviceMediaResult
    data class Failed(val message: String) : DeviceMediaResult
}

/** Videos, audio files and photos stored on the headset. */
interface DeviceMediaRepository {
    /** Runtime permissions needed to list device media. */
    val permissions: List<String>

    fun hasPermission(): Boolean

    suspend fun browse(): DeviceMediaResult

    /** Reads technical metadata (codec, frame rate, audio) and turns [item] into a project asset. */
    suspend fun probe(item: DeviceMediaItem, assetId: String): MediaAsset

    /** Whether the file behind [uri] still exists. */
    suspend fun isAvailable(uri: String): Boolean

    /**
     * A file the user picked in the system's document picker ([uri], read access already granted): its name, size, kind and
     * folder, ready for [probe]; null when it cannot be read or is not a video, sound or picture.
     */
    suspend fun describe(uri: String): DeviceMediaItem?
}
