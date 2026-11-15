package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loudness profiles of media files' sound, analyzed once per file and cached. */
interface LoudnessRepository {
    /** The profile of the sound in [uri]; null when the file has no decodable sound. */
    suspend fun profile(uri: String): LoudnessProfile?

    /** Measures nothing: for previews and tests without media codecs. */
    object None : LoudnessRepository {
        override suspend fun profile(uri: String): LoudnessProfile? = null
    }
}

/**
 * The ducking envelopes of [document] by track (see [DuckingUseCase]), from the loudness profiles of its
 * key tracks' media; the curves are worked out off the main thread.
 */
suspend fun LoudnessRepository.duckEnvelopes(
    document: ProjectDocument,
    ducking: DuckingUseCase = DuckingUseCase(),
): Map<String, DuckEnvelope> {
    val keyAssets = ducking.keyAssetIds(document)
    if (keyAssets.isEmpty()) return emptyMap()
    val profiles = document.media.filter { it.id in keyAssets && !it.offline }
        .mapNotNull { asset -> profile(asset.uri)?.let { asset.id to it } }
        .toMap()
    return withContext(Dispatchers.Default) { ducking.analyses(document, profiles).mapValues { it.value.envelope } }
}
