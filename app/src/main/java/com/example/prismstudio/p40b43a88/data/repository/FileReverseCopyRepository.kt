package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.prismstudio.p40b43a88.data.engine.ReverseMediaGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Makes reversed copies with [ReverseMediaGenerator] into [dir], named after the source uri and
 * duration so a copy outlives the session and is shared by every asset pointing at the same file.
 * All bookkeeping runs on [scope]'s thread (main); only the generation itself runs on IO.
 */
class FileReverseCopyRepository(
    context: Context,
    private val dir: File,
    private val scratchRoot: File,
    private val scope: CoroutineScope,
) : ReverseCopyRepository {

    private val generator = ReverseMediaGenerator(context.applicationContext)
    private val state = MutableStateFlow<Map<String, ReverseCopy>>(emptyMap())
    override val copies: StateFlow<Map<String, ReverseCopy>> = state.asStateFlow()

    /** Assets waiting for (or being turned into) a copy, by file key; one key is made at a time. */
    private val queue = LinkedHashMap<String, MutableList<MediaAsset>>()
    private var worker: Job? = null

    init {
        scope.launch(Dispatchers.IO) { trim() }
    }

    override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) {
        for (asset in assets) {
            if (asset.kind == MediaKind.IMAGE || asset.offline) continue
            val key = keyOf(asset)
            val target = fileFor(key)
            when (val current = state.value[asset.id]) {
                // A copy of another file (the media was relinked) does not count: this file's own is made or found.
                is ReverseCopy.Ready -> if (current.uri == Uri.fromFile(target).toString() && target.exists()) continue
                is ReverseCopy.Making -> continue
                is ReverseCopy.Failed -> if (!retryFailed) continue
                null -> Unit
            }
            if (target.exists()) {
                target.setLastModified(System.currentTimeMillis())
                set(listOf(asset), ReverseCopy.Ready(Uri.fromFile(target).toString()))
                continue
            }
            queue.getOrPut(key) { mutableListOf() }.add(asset)
            set(listOf(asset), ReverseCopy.Making(0f))
        }
        if (queue.isNotEmpty() && worker?.isActive != true) worker = scope.launch { drain() }
    }

    override suspend fun awaitReady(assets: Collection<MediaAsset>, onProgress: (Float) -> Unit): Map<String, String> {
        val wanted = assets.filter { it.kind != MediaKind.IMAGE && !it.offline }
        if (wanted.isEmpty()) return emptyMap()
        withContext(Dispatchers.Main) { request(wanted, retryFailed = true) }
        val done = state.first { map ->
            onProgress(wanted.map { (map[it.id] as? ReverseCopy.Making)?.progress ?: 1f }.average().toFloat())
            wanted.all { map[it.id] is ReverseCopy.Ready } || wanted.any { map[it.id] is ReverseCopy.Failed }
        }
        wanted.firstNotNullOfOrNull { done[it.id] as? ReverseCopy.Failed }?.let { throw ReverseCopyFailure(it.reason) }
        return wanted.associate { it.id to (done[it.id] as ReverseCopy.Ready).uri }
    }

    private suspend fun drain() {
        while (true) {
            val key = queue.keys.firstOrNull() ?: return
            val assets = queue.getValue(key)
            val asset = assets.first()
            val result = make(asset, key) { progress -> set(queue[key].orEmpty(), ReverseCopy.Making(progress)) }
            set(queue.remove(key).orEmpty(), result)
        }
    }

    private suspend fun make(asset: MediaAsset, key: String, onProgress: (Float) -> Unit): ReverseCopy {
        val target = fileFor(key)
        val partial = File(dir, "${target.name}.part")
        val scratch = File(scratchRoot, key)
        return try {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                var lastStep = -1
                generator.generate(
                    source = Uri.parse(asset.uri),
                    pivotUs = asset.durationMs * 1000,
                    output = partial,
                    scratch = scratch,
                    onProgress = { p ->
                        // Coarse steps keep the status flow (and the UI) from churning.
                        val step = (p * 100).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            scope.launch { onProgress(p.coerceIn(0f, 1f)) }
                        }
                    },
                    cancelled = { !isActive },
                )
                if (!partial.renameTo(target)) throw IOException("无法保存倒放素材")
            }
            ReverseCopy.Ready(Uri.fromFile(target).toString())
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reversing ${asset.name} failed", e)
            partial.delete()
            ReverseCopy.Failed(e.message?.takeIf { e is ReverseMediaGenerator.Unsupported || e is IOException } ?: "解码或编码出错")
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** Drops half-written copies and, past [CACHE_LIMIT_BYTES], the copies used least recently. */
    private fun trim() {
        val files = dir.listFiles().orEmpty()
        files.filter { it.name.endsWith(".part") }.forEach { it.delete() }
        var total = 0L
        files.filter { it.name.startsWith("reverse-") && it.name.endsWith(".mp4") }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                total += file.length()
                if (total > CACHE_LIMIT_BYTES) file.delete()
            }
    }

    private fun set(assets: Collection<MediaAsset>, copy: ReverseCopy) {
        if (assets.isEmpty()) return
        state.update { map -> map + assets.associate { it.id to copy } }
    }

    private fun fileFor(key: String) = File(dir, "reverse-$key.mp4")

    private fun keyOf(asset: MediaAsset): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${asset.uri}|${asset.durationMs}|$FORMAT_VERSION".toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "PrismReverse"
        /** Bump when the copy format changes so stale copies are made again. */
        const val FORMAT_VERSION = 3
        const val CACHE_LIMIT_BYTES = 4L shl 30
    }
}
