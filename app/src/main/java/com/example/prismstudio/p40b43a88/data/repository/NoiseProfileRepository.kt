package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import com.example.prismstudio.p40b43a88.data.engine.AudioPcmReader
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.NoiseGate
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfileAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Each sound file's noise floor ([NoiseProfile]), which a track's noise reduction compares its sound with. Measuring a
 * file decodes all of its sound once; the result is kept on disk, so a file is measured only once.
 */
interface NoiseProfileRepository {
    /** The profiles measured so far, by file URI. */
    val profiles: StateFlow<Map<String, NoiseProfile>>

    /** Files being measured now, by URI. */
    val measuring: StateFlow<Set<String>>

    /** [asset]'s profile, once measured (null for a file with no sound to measure). */
    fun profile(asset: MediaAsset): NoiseProfile? = profiles.value[asset.uri]

    /** Measures, in the background, the files not measured yet; [profiles] changes as they arrive. */
    fun request(assets: Collection<MediaAsset>)

    /** Measures the files not measured yet before returning (a render waits for them). */
    suspend fun await(assets: Collection<MediaAsset>)

    object None : NoiseProfileRepository {
        override val profiles: StateFlow<Map<String, NoiseProfile>> = MutableStateFlow(emptyMap())
        override val measuring: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override fun request(assets: Collection<MediaAsset>) = Unit
        override suspend fun await(assets: Collection<MediaAsset>) = Unit
    }
}

/** Measures with [AudioPcmReader], one file at a time, keeping each profile in the cache directory. */
class AndroidNoiseProfileRepository(context: Context, private val scope: CoroutineScope) : NoiseProfileRepository {

    private val reader = AudioPcmReader(context)
    private val dir = File(context.cacheDir, "noise").apply { mkdirs() }
    private val lock = Mutex()
    private val known = MutableStateFlow<Map<String, NoiseProfile>>(emptyMap())
    override val profiles: StateFlow<Map<String, NoiseProfile>> = known.asStateFlow()
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    override val measuring: StateFlow<Set<String>> = busy.asStateFlow()
    /** Files measured without a profile (no sound, or unreadable): not measured again. */
    private val empty = ConcurrentHashMap.newKeySet<String>()

    override fun request(assets: Collection<MediaAsset>) {
        val wanted = unmeasured(assets).filter { it !in busy.value }
        if (wanted.isEmpty()) return
        busy.update { it + wanted }
        scope.launch(Dispatchers.IO) {
            for (uri in wanted) {
                try {
                    measure(uri)
                } finally {
                    busy.update { it - uri }
                }
            }
        }
    }

    override suspend fun await(assets: Collection<MediaAsset>) = withContext(Dispatchers.IO) {
        for (uri in unmeasured(assets)) measure(uri)
    }

    private fun unmeasured(assets: Collection<MediaAsset>): List<String> = assets
        .filter { !it.offline && (it.kind == MediaKind.AUDIO || (it.kind == MediaKind.VIDEO && it.hasAudio)) }
        .map { it.uri }
        .distinct()
        .filter { it !in known.value && it !in empty }

    private suspend fun measure(uri: String) = lock.withLock {
        if (uri in known.value || uri in empty) return@withLock
        val file = File(dir, hash("$uri|$FORMAT_VERSION") + ".bin")
        val profile = readCached(file) ?: analyze(uri)?.also { writeCached(file, it) }
        if (profile != null) known.update { it + (uri to profile) } else empty += uri
    }

    private fun analyze(uri: String): NoiseProfile? = try {
        var analyzer: NoiseProfileAnalyzer? = null
        val began = System.nanoTime()
        reader.read(
            uri,
            // A format change mid-stream (rare) starts over: the floor is the later format's.
            onFormat = { rate, channels -> analyzer = NoiseProfileAnalyzer(rate, channels) },
            onPcm = { samples, frames -> analyzer?.add(samples, frames) },
        )
        analyzer?.finish().also { Log.i(TAG, "Noise floor of $uri: ${(System.nanoTime() - began) / 1_000_000} ms") }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot measure the noise floor of $uri", e)
        null
    }

    private fun readCached(file: File): NoiseProfile? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            val rate = input.readInt()
            val count = input.readInt()
            if (count != NoiseGate.FFT_SIZE / 2 + 1) return null
            NoiseProfile(rate, FloatArray(count) { input.readFloat() })
        }
    }.getOrNull()

    private fun writeCached(file: File, profile: NoiseProfile) {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeInt(profile.sampleRate)
                out.writeInt(profile.floor.size)
                profile.floor.forEach(out::writeFloat)
            }
        }.onFailure { Log.w(TAG, "Cannot keep the noise floor of ${file.name}", it) }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "NoiseProfile"
        const val FORMAT_VERSION = 1
    }
}
