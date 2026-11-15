package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Makes proxies with Media3's [Transformer] into [dir] — the picture only, its short side [ProxyMediaUseCase.SHORT_SIDE],
 * H.264 at [ProxyMediaUseCase.BITRATE] — named after the source uri and duration, so a proxy outlives the session and is shared
 * by every asset pointing at the same file. One is made at a time; the bookkeeping runs on [scope]'s thread (main), where the
 * Transformer must run too.
 */
@OptIn(UnstableApi::class)
class FileProxyRepository(
    context: Context,
    private val dir: File,
    private val scope: CoroutineScope,
    private val rules: ProxyMediaUseCase = ProxyMediaUseCase(),
) : ProxyRepository {

    private val app = context.applicationContext
    private val state = MutableStateFlow<Map<String, ProxyStatus>>(emptyMap())
    override val proxies: StateFlow<Map<String, ProxyStatus>> = state.asStateFlow()

    /** Assets waiting for (or being given) a proxy, by file key. */
    private val queue = LinkedHashMap<String, MutableList<MediaAsset>>()
    private var worker: Job? = null
    private var making: Pair<String, Job>? = null

    init {
        scope.launch(Dispatchers.IO) { dir.listFiles().orEmpty().filter { it.name.endsWith(".part.mp4") }.forEach { it.delete() } }
    }

    override fun refresh(assets: Collection<MediaAsset>) {
        for (asset in assets.filter(rules::canProxy)) {
            val file = fileFor(keyOf(asset))
            when (val current = state.value[asset.id]) {
                is ProxyStatus.Making -> continue
                // A proxy of another file (the media was relinked) is forgotten: this file's own proxy, if any, takes its place.
                is ProxyStatus.Ready -> if (current.uri == Uri.fromFile(file).toString()) continue else state.update { it - asset.id }
                else -> Unit
            }
            if (file.exists()) set(listOf(asset), ready(asset, file))
        }
    }

    override fun request(assets: Collection<MediaAsset>) {
        for (asset in assets.filter(rules::canProxy)) {
            val key = keyOf(asset)
            val file = fileFor(key)
            when (val current = state.value[asset.id]) {
                is ProxyStatus.Making -> continue
                is ProxyStatus.Ready -> if (file.exists() && current.uri == Uri.fromFile(file).toString()) continue
                else -> Unit
            }
            if (file.exists()) {
                set(listOf(asset), ready(asset, file))
                continue
            }
            queue.getOrPut(key) { mutableListOf() }.add(asset)
            set(listOf(asset), ProxyStatus.Making(0f))
        }
        if (queue.isNotEmpty() && worker?.isActive != true) worker = scope.launch { drain() }
    }

    override fun delete(asset: MediaAsset) {
        val key = keyOf(asset)
        val sharing = queue.remove(key).orEmpty() + asset
        making?.takeIf { it.first == key }?.second?.cancel()
        fileFor(key).delete()
        state.update { m -> m - sharing.map { it.id }.toSet() }
    }

    private suspend fun drain() {
        while (true) {
            val key = queue.keys.firstOrNull() ?: return
            val asset = queue.getValue(key).first()
            val job = scope.launch {
                val result = make(asset, key) { p -> set(queue[key].orEmpty(), ProxyStatus.Making(p)) }
                set(queue.remove(key).orEmpty(), result)
            }
            making = key to job
            job.join()
            making = null
            // A deleted one leaves the queue without a result.
            queue.remove(key)
        }
    }

    private suspend fun make(asset: MediaAsset, key: String, onProgress: (Float) -> Unit): ProxyStatus {
        val target = fileFor(key)
        val partial = File(dir, "proxy-$key.part.mp4")
        return try {
            withContext(Dispatchers.IO) { dir.mkdirs() }
            transform(asset, partial, onProgress)
            withContext(Dispatchers.IO) { if (!partial.renameTo(target)) throw IOException("无法保存代理文件") }
            ready(asset, target)
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Proxy for ${asset.name} failed", e)
            partial.delete()
            ProxyStatus.Failed(if (e is IOException) e.message ?: "无法保存代理文件" else "解码或编码出错")
        }
    }

    /** One Transformer export of [asset]'s picture at proxy size into [file]; on the main thread, as Transformer needs. */
    private suspend fun transform(asset: MediaAsset, file: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.Main) {
        val (w, h) = rules.size(asset)
        // A picture already at or below proxy size keeps its size (no upscaling).
        val effects = if (w < asset.width || h < asset.height) listOf(Presentation.createForShortSide(ProxyMediaUseCase.SHORT_SIDE)) else emptyList()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(asset.uri)))
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), effects))
            .build()
        val encoders = DefaultEncoderFactory.Builder(app)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(ProxyMediaUseCase.BITRATE).build())
            .build()
        val transformer = Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setEncoderFactory(encoders).build()
        val ticker = launch {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                delay(PROGRESS_MS)
            }
        }
        try {
            suspendCancellableCoroutine { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                transformer.start(item, file.path)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        } finally {
            ticker.cancel()
        }
    }

    private fun ready(asset: MediaAsset, file: File): ProxyStatus.Ready {
        val (w, h) = rules.size(asset)
        return ProxyStatus.Ready(Uri.fromFile(file).toString(), w, h, file.length())
    }

    private fun set(assets: Collection<MediaAsset>, status: ProxyStatus) {
        if (assets.isEmpty()) return
        state.update { m -> m + assets.associate { it.id to status } }
    }

    private fun fileFor(key: String) = File(dir, "proxy-$key.mp4")

    private fun keyOf(asset: MediaAsset): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${asset.uri}|${asset.durationMs}|$FORMAT_VERSION".toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "PrismProxy"
        /** Bump when the proxy format changes so stale proxies are made again. */
        const val FORMAT_VERSION = 1
        const val PROGRESS_MS = 300L
    }
}
