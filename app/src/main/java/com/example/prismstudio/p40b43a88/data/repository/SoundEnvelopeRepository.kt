package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import com.example.prismstudio.p40b43a88.data.engine.AudioPcmReader
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.usecase.SoundEnvelope
import com.example.prismstudio.p40b43a88.domain.usecase.WaveformAlignUseCase
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Each file's sound level every few milliseconds ([SoundEnvelope]), for lining clips up by their sound. */
interface SoundEnvelopeRepository {
    /** [asset]'s envelope (decoding its sound once; kept while the app runs), or null when it has no sound. */
    suspend fun envelope(asset: MediaAsset): SoundEnvelope?

    object None : SoundEnvelopeRepository {
        override suspend fun envelope(asset: MediaAsset): SoundEnvelope? = null
    }
}

/** Hands out envelopes set by uri, for tests and previews. */
class FakeSoundEnvelopeRepository(private val envelopes: Map<String, SoundEnvelope> = emptyMap()) : SoundEnvelopeRepository {
    override suspend fun envelope(asset: MediaAsset): SoundEnvelope? = envelopes[asset.uri]
}

/** Decodes with [AudioPcmReader]: the RMS of all channels over each [WaveformAlignUseCase.BUCKET_MS], in dB. */
class AndroidSoundEnvelopeRepository(context: Context) : SoundEnvelopeRepository {
    private val reader = AudioPcmReader(context)
    private val cache = ConcurrentHashMap<String, SoundEnvelope>()

    override suspend fun envelope(asset: MediaAsset): SoundEnvelope? {
        cache[asset.uri]?.let { return it }
        return withContext(Dispatchers.Default) {
            val began = System.nanoTime()
            val levels = ArrayList<Float>(1 shl 14)
            var channels = 1
            var perBucket = 1
            var sum = 0.0
            var count = 0
            val found = try {
                reader.read(
                    asset.uri,
                    onFormat = { rate, n ->
                        channels = max(1, n)
                        perBucket = max(1, rate * WaveformAlignUseCase.BUCKET_MS / 1000)
                    },
                    onPcm = { samples, frames ->
                        var i = 0
                        repeat(frames) {
                            repeat(channels) { val v = samples[i++]; sum += v * v }
                            if (++count >= perBucket) {
                                levels += db(sum / (count * channels))
                                sum = 0.0
                                count = 0
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read the sound of ${asset.name}", e)
                false
            }
            if (!found || levels.isEmpty()) return@withContext null
            Log.i(TAG, "Sound envelope of ${asset.name}: ${levels.size} buckets in ${(System.nanoTime() - began) / 1_000_000} ms")
            SoundEnvelope(WaveformAlignUseCase.BUCKET_MS, levels.toFloatArray()).also { cache[asset.uri] = it }
        }
    }

    private fun db(meanSquare: Double): Float = (10 * log10(meanSquare + FLOOR)).toFloat().coerceAtLeast(FLOOR_DB)

    private companion object {
        const val TAG = "PrismAlign"
        const val FLOOR = 1e-8
        const val FLOOR_DB = -80f
    }
}
