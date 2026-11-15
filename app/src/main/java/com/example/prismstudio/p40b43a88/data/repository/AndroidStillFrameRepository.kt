package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.data.engine.StillColors
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Captures freeze frames into `files/stills/<id>.mp4` (app storage, so the project keeps them when the
 * source moves). The exact frame comes from [frames], which falls back to Media3's frame extractor where
 * the platform retriever yields nothing. It is kept as a short static video, not an image, so the held
 * frame goes through the same video path as the clip around it (Y′CbCr decoding instead of the sRGB
 * image path) and matches it exactly.
 */
@UnstableApi
class AndroidStillFrameRepository(context: Context, private val frames: MediaPreviewRepository) : StillFrameRepository {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    override suspend fun capture(source: MediaAsset, timeMs: Long, id: String, name: String): MediaAsset? {
        val width = source.width.takeIf { it > 0 }?.coerceAtMost(MAX_WIDTH) ?: DEFAULT_WIDTH
        val frame = frames.framePixels(source.uri, source.kind, timeMs.coerceAtLeast(0), width, exact = true) ?: return null
        val dir = File(appContext.filesDir, STILLS_DIR).apply { mkdirs() }
        val image = File(dir, "$id.png")
        val video = File(dir, "$id.mp4")
        try {
            withContext(Dispatchers.IO) {
                val bitmap = Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            if (!encode(image, video, frame.width, frame.height)) return null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save the still of ${source.uri} at $timeMs ms", e)
            video.delete()
            return null
        } finally {
            image.delete()
        }
        return MediaAsset(
            id = id,
            uri = Uri.fromFile(video).toString(),
            name = name,
            kind = MediaKind.VIDEO,
            durationMs = STILL_MS,
            width = frame.width,
            height = frame.height,
            frameRate = STILL_FPS.toFloat(),
            videoCodec = "H.264",
            audioDescription = "无音频",
            hasAudio = false,
            sizeBytes = video.length(),
            relativePath = "$STILLS_DIR/",
            poster = posterOf(frame),
        )
    }

    /**
     * [image] held for [STILL_MS] as an H.264 video in [output]; false when Media3 refuses. A held frame
     * costs almost nothing after its key frames, so the bitrate is generous: the still stays as sharp as
     * the clip it was taken from.
     */
    private suspend fun encode(image: File, output: File, width: Int, height: Int): Boolean = withContext(Dispatchers.Main) {
        val item = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(Uri.fromFile(image)).setImageDurationMs(STILL_MS).build(),
        ).setFrameRate(STILL_FPS).build()
        val bitrate = (width.toLong() * height * BITS_PER_PIXEL).coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
        val transformer = Transformer.Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            // The frozen frame keeps the shot's values: no sRGB → BT.709 transfer conversion of the still.
            .setVideoFrameProcessorFactory(StillColors.frameProcessors())
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(appContext)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .setEnableFallback(true)
                    .build(),
            )
            .build()
        suspendCancellableCoroutine { cont ->
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.w(TAG, "Cannot encode the still", exportException)
                    if (cont.isActive) cont.resume(false)
                }
            })
            transformer.start(item, output.path)
            cont.invokeOnCancellation { main.post { transformer.cancel() } }
        }
    }

    /** Average colours of the top, middle and bottom thirds (sampled every 8 px). */
    private fun posterOf(frame: FramePixels): PosterColors {
        fun band(from: Int, to: Int): Long {
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            for (y in from until to step 8) for (x in 0 until frame.width step 8) {
                val p = frame.pixels[y * frame.width + x]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
            if (n == 0L) return PosterColors.NEUTRAL.middle
            return (0xFFL shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        val h = frame.height.coerceAtLeast(3)
        return PosterColors(band(0, h / 3), band(h / 3, 2 * h / 3), band(2 * h / 3, frame.height))
    }

    private companion object {
        const val TAG = "PrismStills"
        const val STILLS_DIR = "stills"
        /** Stills keep the source's resolution up to 4K. */
        const val MAX_WIDTH = 3840
        const val DEFAULT_WIDTH = 1920
        /** How long a held frame can be trimmed out to. */
        const val STILL_MS = 10_000L
        const val STILL_FPS = 30
        const val BITS_PER_PIXEL = 12L
        const val MIN_BITRATE = 4_000_000L
        const val MAX_BITRATE = 40_000_000L
    }
}
