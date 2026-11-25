package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import java.nio.ByteBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Samples tagged HLG/PQ through the same SDR decoder path as the edit monitor. Returns source code values. */
@UnstableApi
@OptIn(markerClass = [ExperimentalApi::class])
internal class HdrFrameReader(private val context: Context) {
    data class Source(val width: Int, val height: Int, val durationMs: Long, val hdr: Boolean)

    private val formats = LruCache<String, Source>(128)

    /** Called off the main thread. A failed open is retried after a grant or a relink. */
    fun source(uri: String): Source? {
        formats.get(uri)?.let { return it.takeIf(Source::hdr) }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri.toUri(), null)
            val video = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return null
            val transfer = video.getInteger(MediaFormat.KEY_COLOR_TRANSFER, 0)
            val rotated = video.getInteger(MediaFormat.KEY_ROTATION, 0) % 180 != 0
            val w = video.getInteger(MediaFormat.KEY_WIDTH, 0)
            val h = video.getInteger(MediaFormat.KEY_HEIGHT, 0)
            val source = Source(if (rotated) h else w, if (rotated) w else h,
                video.getLong(MediaFormat.KEY_DURATION, 0L) / 1000,
                transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084)
            formats.put(uri, source)
            return source.takeIf(Source::hdr)
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    suspend fun frame(uri: String, source: Source, timeMs: Long, widthPx: Int): Bitmap? = withContext(Dispatchers.Main.immediate) {
        val width = widthPx.coerceIn(1, source.width.coerceAtLeast(1))
        val height = (source.height.toLong() * width / source.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val position = timeMs.coerceAtLeast(0).let { if (source.durationMs > 0) it.coerceAtMost(source.durationMs - 1) else it }
        val durationMs = if (source.durationMs > 0) source.durationMs else position + 1000
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveAudio(true).setDurationUs(durationMs * 1000).build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(item).build()).build()
        val player = CompositionPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, FirstAudioTrackExtractorsFactory(readHdrAsSdr = true)))
            .setVideoGraphFactory(StillColors.previewGraph())
            .build()
        var reader: ImageReader? = null
        var frame: Image? = null
        try {
            val output = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = output
            val available = CompletableDeferred<Unit>()
            output.setOnImageAvailableListener({ sourceReader ->
                if (!available.isCompleted) {
                    try {
                        frame = sourceReader.acquireLatestImage()
                        if (frame != null) available.complete(Unit)
                    } catch (failure: RuntimeException) { available.completeExceptionally(failure) }
                }
            }, Handler(Looper.getMainLooper()))
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { available.completeExceptionally(error) }
            })
            player.setVideoSurface(output.surface, Size(width, height))
            CompositionPlayerLeaks.setComposition(player, composition, position)
            player.prepare()
            withTimeoutOrNull(4000) {
                available.await()
                withContext(Dispatchers.Default) { frame?.let(::bitmap) }
            }
        } finally {
            reader?.setOnImageAvailableListener(null, null)
            CompositionPlayerLeaks.release(player)
            frame?.close()
            reader?.close()
        }
    }

    private fun bitmap(image: Image): Bitmap {
        val plane = image.planes.single()
        require(plane.pixelStride == 4)
        val rowBytes = image.width * 4
        val source = plane.buffer
        val pixels = if (plane.rowStride == rowBytes) source else {
            val packed = ByteArray(rowBytes * image.height)
            for (row in 0 until image.height) {
                source.position(row * plane.rowStride)
                source.get(packed, row * rowBytes, rowBytes)
            }
            ByteBuffer.wrap(packed)
        }
        return createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
    }
}
