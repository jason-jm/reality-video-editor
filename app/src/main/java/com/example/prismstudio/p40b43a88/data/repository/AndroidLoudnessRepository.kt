package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.prismstudio.p40b43a88.data.engine.AudioPcmReader
import com.example.prismstudio.p40b43a88.domain.model.LoudnessAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.StereoBlocks
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Decodes a file's sound once ([AudioPcmReader]) into a [LoudnessProfile], kept in memory and in
 * cacheDir/loudness so a project reopens without decoding again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLoudnessRepository(context: Context, io: CoroutineDispatcher = Dispatchers.IO) : LoudnessRepository {

    private val reader = AudioPcmReader(context)
    private val dispatcher = io.limitedParallelism(1)
    private val lock = Mutex()
    private val profiles = LruCache<String, LoudnessProfile>(16)
    private val dir = File(context.cacheDir, "loudness").apply { mkdirs() }

    override suspend fun profile(uri: String): LoudnessProfile? {
        profiles.get(uri)?.let { return it }
        return withContext(dispatcher) {
            lock.withLock {
                profiles.get(uri)?.let { return@withLock it }
                val file = File(dir, hash("$uri|$FORMAT_VERSION") + ".bin")
                val profile = readCached(file) ?: analyze(uri)?.also { writeCached(file, it) }
                profile?.also { profiles.put(uri, it) }
            }
        }
    }

    private fun analyze(uri: String): LoudnessProfile? = try {
        // A format change mid-stream (rare) continues in a new analyzer; the parts are joined at the end.
        val parts = mutableListOf<LoudnessAnalyzer>()
        val found = reader.read(
            uri,
            onFormat = { rate, channels -> parts += LoudnessAnalyzer(rate, channels) },
            onPcm = { samples, frames -> parts.lastOrNull()?.add(samples, frames) },
        )
        if (!found || parts.isEmpty()) null else join(parts.map { it.finish() })
    } catch (e: Exception) {
        Log.w(TAG, "Cannot measure the loudness of $uri", e)
        null
    }

    private fun join(parts: List<LoudnessProfile>): LoudnessProfile = if (parts.size == 1) {
        parts.single()
    } else {
        LoudnessProfile(
            parts.first().blockMs,
            parts.map { it.energy }.reduce { a, b -> a + b },
            parts.map { it.truePeak }.reduce { a, b -> a + b },
            // The sides apart only when every part has them (a part of another channel count drops them).
            parts.map { it.stereo }.takeIf { all -> all.all { it != null } }?.map { it!! }?.reduce { a, b -> a + b },
        )
    }

    private fun readCached(file: File): LoudnessProfile? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            val blockMs = input.readInt()
            val count = input.readInt()
            val energy = FloatArray(count) { input.readFloat() }
            val peak = FloatArray(count) { input.readFloat() }
            val stereo = if (input.readBoolean()) {
                val sides = Array(6) { FloatArray(count) { input.readFloat() } }
                StereoBlocks(sides[0], sides[1], sides[2], sides[3], sides[4], sides[5])
            } else {
                null
            }
            LoudnessProfile(blockMs, energy, peak, stereo)
        }
    }.getOrNull()

    private fun writeCached(file: File, profile: LoudnessProfile) {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeInt(profile.blockMs)
                out.writeInt(profile.blocks)
                profile.energy.forEach(out::writeFloat)
                profile.truePeak.forEach(out::writeFloat)
                val st = profile.stereo
                out.writeBoolean(st != null)
                if (st != null) listOf(st.left, st.right, st.cross, st.leftPeak, st.rightPeak, st.monoPeak).forEach { it.forEach(out::writeFloat) }
            }
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "PrismLoudness"
        /** Bump when the analysis changes so cached profiles are measured again (2: true peaks; 3: stereo sides apart). */
        const val FORMAT_VERSION = 3
    }
}
