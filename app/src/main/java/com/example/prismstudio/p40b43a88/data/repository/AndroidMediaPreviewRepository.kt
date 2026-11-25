package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.inspector.FrameExtractor
import com.example.prismstudio.p40b43a88.data.engine.AudioPcmReader
import com.example.prismstudio.p40b43a88.data.engine.HdrFrameReader
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Frame and waveform decoding with in-memory and on-disk caches. */
@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class AndroidMediaPreviewRepository(
    private val context: Context,
    io: CoroutineDispatcher = Dispatchers.IO,
) : MediaPreviewRepository {

    private val frameDispatcher = io.limitedParallelism(2)
    private val waveDispatcher = io.limitedParallelism(1)
    private val thumbnails = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val waveforms = LruCache<String, Waveform>(32)
    private val waveLock = Mutex()
    private val waveDir = File(context.cacheDir, "waveforms").apply { mkdirs() }
    private val pcm = AudioPcmReader(context)
    private val extractorLock = Mutex()
    private val hdrFrames = HdrFrameReader(context)
    private val extractors = LinkedHashMap<String, FrameExtractor>(4, 0.75f, true)

    override suspend fun thumbnail(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int): Bitmap? {
        if (kind == MediaKind.AUDIO) return null
        val key = "$uri@${timeMs / 200}@$widthPx"
        thumbnails.get(key)?.let { return it }
        return decodeAny(uri, kind, timeMs, widthPx, exact = false)?.also { thumbnails.put(key, it) }
    }

    override suspend fun framePixels(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int, exact: Boolean): FramePixels? {
        if (kind == MediaKind.AUDIO) return null
        val bitmap = decodeAny(uri, kind, timeMs, widthPx, exact) ?: return null
        return withContext(frameDispatcher) {
            val scaled = if (bitmap.width > widthPx) {
                Bitmap.createScaledBitmap(bitmap, widthPx, (bitmap.height.toLong() * widthPx / bitmap.width).toInt().coerceAtLeast(1), true)
            } else bitmap
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            FramePixels(scaled.width, scaled.height, pixels)
        }
    }

    /**
     * Platform frame retrieval first (fast on devices); when it yields nothing — some decoder
     * configurations only render to surfaces — fall back to Media3's GL frame extractor. Never wider
     * than [widthPx]. A blank platform frame is no picture: when the extractor fails too, there is none
     * (callers show their placeholder, and nothing black gets cached).
     */
    private suspend fun decodeAny(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int, exact: Boolean): Bitmap? {
        if (kind == MediaKind.VIDEO) {
            val hdr = withContext(frameDispatcher) { hdrFrames.source(uri) }
            if (hdr != null) {
                val key = "hdr:$uri@$timeMs@$widthPx"
                return extractorLock.withLock {
                    thumbnails.get(key) ?: try {
                        hdrFrames.frame(uri, hdr, timeMs, widthPx)?.also { thumbnails.put(key, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot sample HDR frame at $timeMs", e)
                        null
                    }
                }
            }
        }
        val platform = withContext(frameDispatcher) { decode(uri, kind, timeMs, widthPx, exact) }
        if (platform != null && (kind != MediaKind.VIDEO || !isBlank(platform))) return platform
        if (kind != MediaKind.VIDEO) return null
        // While other players render, the extractor's GL pipeline can time out (seen on the emulator): once more.
        repeat(EXTRACT_ATTEMPTS) { attempt ->
            val extracted = try {
                extractFrame(uri, timeMs, exact)?.let { full ->
                    if (full.width <= widthPx) {
                        full
                    } else {
                        withContext(frameDispatcher) {
                            Bitmap.createScaledBitmap(full, widthPx, (full.height.toLong() * widthPx / full.width).toInt().coerceAtLeast(1), true)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e // a newer request replaced this one: stop, do not report a failure
            } catch (e: Exception) {
                Log.w(TAG, "Frame extractor failed for $uri (attempt ${attempt + 1})", e)
                null
            }
            if (extracted != null) return extracted
            delay(EXTRACT_RETRY_MS)
        }
        return null
    }

    /** True when a sparse sample of the frame is fully transparent or black (failed retrieval). */
    private fun isBlank(bitmap: Bitmap): Boolean {
        for (yi in 1..4) for (xi in 1..4) {
            val p = bitmap.getPixel(bitmap.width * xi / 5, bitmap.height * yi / 5)
            val a = p ushr 24
            val l = ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
            if (a > 0 && l > 12) return false
        }
        return true
    }

    /**
     * The frame at [timeMs] at its own size. Every [FrameExtractor] shares one player in Media3, built with
     * the effects of whichever extractor came first, so a resize effect would stick at the first size asked
     * for (a 192 px scope frame then served a 960 px Gallery still): callers scale the frame instead.
     */
    private suspend fun extractFrame(uri: String, timeMs: Long, exact: Boolean): Bitmap? =
        extractorLock.withLock {
            withContext(Dispatchers.Main) {
                val key = "$uri@$exact"
                val extractor = extractors.getOrPut(key) {
                    FrameExtractor.Builder(context, MediaItem.fromUri(uri))
                        .setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
                        .build()
                }
                while (extractors.size > MAX_EXTRACTORS) {
                    val eldest = extractors.keys.first()
                    extractors.remove(eldest)?.close()
                }
                extractor.getFrame(timeMs.coerceAtLeast(0)).await().bitmap
            }
        }

    private fun decode(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int, exact: Boolean): Bitmap? = try {
        if (kind == MediaKind.IMAGE) {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = widthPx.coerceAtMost(info.size.width).coerceAtLeast(1)
                decoder.setTargetSize(w, (info.size.height.toLong() * w / info.size.width.coerceAtLeast(1)).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 16
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 9
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val (sw, sh) = if (rotation % 180 == 0) w to h else h to w
                val targetW = widthPx.coerceAtLeast(2)
                val targetH = (sh.toLong() * targetW / sw.coerceAtLeast(1)).toInt().coerceAtLeast(2)
                val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                retriever.getScaledFrameAtTime(timeMs.coerceAtLeast(0) * 1000, option, targetW, targetH)
            } finally {
                retriever.release()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot decode frame of $uri", e)
        null
    }

    override suspend fun waveform(uri: String): Waveform? {
        waveforms.get(uri)?.let { return it }
        return withContext(waveDispatcher) {
            waveLock.withLock {
                waveforms.get(uri)?.let { return@withLock it }
                val file = File(waveDir, hash(uri) + ".bin")
                val wave = readCached(file) ?: decodeWaveform(uri)?.also { writeCached(file, it) }
                wave?.also { waveforms.put(uri, it) }
            }
        }
    }

    private fun decodeWaveform(uri: String): Waveform? {
        val peaks = ArrayList<Float>(4096)
        var channels = 1
        var framesPerBucket = 1
        var bucketPeak = 0f
        var bucketFrames = 0
        return try {
            val found = pcm.read(
                uri,
                onFormat = { rate, count ->
                    channels = count
                    framesPerBucket = (rate * BUCKET_MS / 1000).coerceAtLeast(1)
                },
                onPcm = { samples, frames ->
                    var i = 0
                    repeat(frames) {
                        var peak = 0f
                        repeat(channels) {
                            val v = abs(samples[i++])
                            if (v > peak) peak = v
                        }
                        if (peak > bucketPeak) bucketPeak = peak
                        if (++bucketFrames >= framesPerBucket) {
                            peaks += bucketPeak.coerceIn(0f, 1f)
                            bucketPeak = 0f
                            bucketFrames = 0
                        }
                    }
                },
            )
            if (!found) return null
            if (bucketFrames > 0) peaks += bucketPeak
            Waveform(BUCKET_MS, peaks.toFloatArray())
        } catch (e: Exception) {
            Log.w(TAG, "Cannot decode audio of $uri", e)
            null
        }
    }

    private fun readCached(file: File): Waveform? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            val bucket = input.readInt()
            val count = input.readInt()
            Waveform(bucket, FloatArray(count) { input.readUnsignedByte() / 255f })
        }
    }.getOrNull()

    private fun writeCached(file: File, wave: Waveform) {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeInt(wave.bucketMs)
                out.writeInt(wave.peaks.size)
                wave.peaks.forEach { out.writeByte((it * 255).roundToInt().coerceIn(0, 255)) }
            }
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "PrismPreview"
        const val BUCKET_MS = 20
        const val MAX_EXTRACTORS = 3
        const val EXTRACT_ATTEMPTS = 2
        const val EXTRACT_RETRY_MS = 300L
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    Futures.addCallback(
        this,
        object : FutureCallback<T> {
            override fun onSuccess(result: T) = cont.resume(result)
            override fun onFailure(t: Throwable) = cont.resumeWithException(t)
        },
        MoreExecutors.directExecutor(),
    )
    cont.invokeOnCancellation { cancel(false) }
}
