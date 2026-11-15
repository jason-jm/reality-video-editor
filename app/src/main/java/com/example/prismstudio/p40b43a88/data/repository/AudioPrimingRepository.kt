package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The encoder delay at the start of each file's sound. AAC begins with priming samples (ffmpeg writes 1024, Apple's
 * tools 2112, Android's own encoder 2048) that the MP4's edit list marks to skip. Media3 skips them only when a file
 * plays from its start: an item clipped to start inside the file gets its samples at their stored times, so its sound
 * came out that much late against its picture (21.3 ms for ffmpeg's AAC, measured in the rev 88 sync check). The
 * composition opens such an audio window that much later ([com.example.prismstudio.p40b43a88.data.engine.CompositionFactory]).
 */
interface AudioPrimingRepository {
    /** Delays read so far, in µs by file URI. */
    val delays: StateFlow<Map<String, Long>>

    /** [asset]'s delay in µs: 0 until it has been read, and for a file without one. */
    fun delayUs(asset: MediaAsset): Long = delays.value[asset.uri] ?: 0L

    /** Reads, in the background, the delays not read yet; [delays] changes as they arrive. */
    fun request(assets: Collection<MediaAsset>)

    /** Reads the delays not read yet before returning (a render waits for them). */
    suspend fun await(assets: Collection<MediaAsset>)

    object None : AudioPrimingRepository {
        override val delays: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override fun request(assets: Collection<MediaAsset>) = Unit
        override suspend fun await(assets: Collection<MediaAsset>) = Unit
    }
}

/**
 * Reads the delay with Media3's own extractors ([MetadataRetriever] over a default [DefaultExtractorsFactory]), so it is
 * exactly the one Media3 skips at a file's start: a file whose edit list Media3 applies by moving its samples instead
 * reports none, and needs no correction.
 */
@UnstableApi
class Media3AudioPrimingRepository(private val context: Context, private val scope: CoroutineScope) : AudioPrimingRepository {

    private val known = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val delays: StateFlow<Map<String, Long>> = known.asStateFlow()
    private val reading = ConcurrentHashMap.newKeySet<String>()

    override fun request(assets: Collection<MediaAsset>) {
        for (uri in unread(assets)) {
            if (!reading.add(uri)) continue
            scope.launch(Dispatchers.IO) {
                try {
                    store(uri, read(uri))
                } finally {
                    reading.remove(uri)
                }
            }
        }
    }

    override suspend fun await(assets: Collection<MediaAsset>) = withContext(Dispatchers.IO) {
        for (uri in unread(assets)) store(uri, read(uri))
    }

    private fun unread(assets: Collection<MediaAsset>): List<String> = assets
        .filter { !it.offline && (it.kind == MediaKind.AUDIO || (it.kind == MediaKind.VIDEO && it.hasAudio)) }
        .map { it.uri }
        .distinct()
        .filter { it !in known.value }

    private fun store(uri: String, delayUs: Long) = known.update { it + (uri to delayUs) }

    /** The delay of [uri]'s first sound track, in µs; 0 when it has none or cannot be read. */
    private fun read(uri: String): Long = try {
        // Not the retriever's own extractors: they skip MP4 sample tables, where Media3 finds the delay in the edit list.
        val sources = DefaultMediaSourceFactory(context, DefaultExtractorsFactory())
        MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).setMediaSourceFactory(sources).build().use { retriever ->
            val groups = retriever.retrieveTrackGroups().get(READ_TIMEOUT_S, TimeUnit.SECONDS)
            val sound = (0 until groups.length).map { groups[it].getFormat(0) }
                .firstOrNull { MimeTypes.isAudio(it.sampleMimeType) && it.sampleRate > 0 }
            val delay = sound?.let { it.encoderDelay * 1_000_000L / it.sampleRate } ?: 0L
            if (delay > 0) Log.i(TAG, "Encoder delay ${sound?.encoderDelay} samples (${delay} µs): $uri")
            delay
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read the encoder delay of $uri", e)
        0L
    }

    private companion object {
        const val TAG = "AudioPriming"
        const val READ_TIMEOUT_S = 10L
    }
}
