package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Small luma pictures from decoded frames, for analysis (stabilization, window tracking). */
object LumaPictures {

    /** The mean of each [tw] × [th] block of [y] (w × h, 8-bit), 0…255. */
    fun boxed(y: ByteArray, w: Int, h: Int, tw: Int, th: Int): FloatArray {
        val out = FloatArray(tw * th)
        for (ty in 0 until th) {
            val y0 = ty * h / th
            val y1 = maxOf(y0 + 1, (ty + 1) * h / th)
            for (tx in 0 until tw) {
                val x0 = tx * w / tw
                val x1 = maxOf(x0 + 1, (tx + 1) * w / tw)
                var sum = 0
                for (yy in y0 until y1) {
                    val row = yy * w
                    for (xx in x0 until x1) sum += y[row + xx].toInt() and 0xFF
                }
                out[ty * tw + tx] = sum / ((y1 - y0) * (x1 - x0)).toFloat()
            }
        }
        return out
    }

    /** [p] (w × h) turned [rotation] degrees clockwise, as the file is shown; its new width and height. */
    fun turned(p: ByteArray, w: Int, h: Int, rotation: Int): Triple<ByteArray, Int, Int> = when ((rotation % 360 + 360) % 360) {
        90 -> Triple(ByteArray(w * h) { i -> val x = i % h; val y = i / h; p[(h - 1 - x) * w + y] }, h, w)
        180 -> Triple(ByteArray(w * h) { i -> p[w * h - 1 - i] }, w, h)
        270 -> Triple(ByteArray(w * h) { i -> val x = i % h; val y = i / h; p[x * w + (w - 1 - y)] }, h, w)
        else -> Triple(p, w, h)
    }

    /** The clockwise rotation a video file asks to be shown with (0 when unknown). */
    fun rotationOf(context: Context, uri: String): Int = runCatching {
        MediaMetadataRetriever().run {
            try {
                if (uri.startsWith("content:")) setDataSource(context, Uri.parse(uri)) else setDataSource(Uri.parse(uri).path ?: uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally {
                release()
            }
        }
    }.getOrDefault(0)
}
