package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind

/** Scriptable [DeviceMediaRepository] for tests. */
class FakeDeviceMediaRepository(
    var items: List<DeviceMediaItem> = emptyList(),
    var granted: Boolean = true,
    var failure: String? = null,
    var missingUris: Set<String> = emptySet(),
    /** Kinds whose permission was refused while others were granted. */
    var refused: Set<MediaKind> = emptySet(),
    var limitedVisualAccess: Boolean = false,
) : DeviceMediaRepository {

    override val permissions: List<String> = listOf("android.permission.READ_MEDIA_VIDEO")

    override fun hasPermission(): Boolean = granted

    override suspend fun browse(): DeviceMediaResult = when {
        !granted -> DeviceMediaResult.PermissionRequired
        failure != null -> DeviceMediaResult.Failed(failure!!)
        else -> DeviceMediaResult.Items(items.filter { it.kind !in refused }, refused, limitedVisualAccess)
    }

    override suspend fun probe(item: DeviceMediaItem, assetId: String): MediaAsset = MediaAsset(
        id = assetId,
        uri = item.uri,
        name = item.name,
        kind = item.kind,
        durationMs = if (item.kind == MediaKind.IMAGE) MediaAsset.IMAGE_DEFAULT_DURATION_MS else item.durationMs,
        width = item.width,
        height = item.height,
        frameRate = if (item.kind == MediaKind.VIDEO) 30f else 0f,
        videoCodec = if (item.kind == MediaKind.VIDEO) "H.264" else "—",
        audioDescription = if (item.kind == MediaKind.IMAGE) "无音频" else "AAC 48 kHz 立体声",
        hasAudio = item.kind != MediaKind.IMAGE,
        sizeBytes = item.sizeBytes,
        relativePath = item.relativePath,
        poster = item.poster,
    )

    override suspend fun isAvailable(uri: String): Boolean = uri !in missingUris

    /** Picked files the test describes (by uri); the device items count too. */
    var picked: Map<String, DeviceMediaItem> = emptyMap()

    override suspend fun describe(uri: String): DeviceMediaItem? = picked[uri] ?: items.firstOrNull { it.uri == uri }
}
