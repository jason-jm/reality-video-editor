package com.example.prismstudio.p40b43a88.data.repository

import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform

/** [MediaPreviewRepository] for JVM tests: no bitmaps, a flat mid-grey frame and a fixed waveform. */
class FakeMediaPreviewRepository(
    var frame: FramePixels? = FramePixels(4, 2, IntArray(8) { 0xFF808080.toInt() }),
    var waveform: Waveform? = Waveform(20, FloatArray(500) { 0.5f }),
) : MediaPreviewRepository {
    var frameRequests = 0
        private set

    /** Every frame asked for: its media and whether it had to be exact. */
    val frameCalls = mutableListOf<Pair<String, Boolean>>()

    /** The media every thumbnail was asked of. */
    val thumbnailCalls = mutableListOf<String>()

    override suspend fun thumbnail(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int): Bitmap? {
        thumbnailCalls += uri
        return null
    }

    override suspend fun framePixels(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int, exact: Boolean): FramePixels? {
        frameRequests++
        frameCalls += uri to exact
        return frame
    }

    override suspend fun waveform(uri: String): Waveform? = waveform
}
