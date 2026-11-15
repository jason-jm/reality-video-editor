package com.example.prismstudio.p40b43a88.data.repository

import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform

/** Decoded previews of source media: poster frames, frame pixels for scopes, audio waveforms. */
interface MediaPreviewRepository {
    /** A frame of [uri] near [timeMs], about [widthPx] wide; null for audio or unreadable files. */
    suspend fun thumbnail(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int): Bitmap?

    /** Pixels of the frame at [timeMs]; [exact] decodes the precise frame instead of the nearest key frame. */
    suspend fun framePixels(uri: String, kind: MediaKind, timeMs: Long, widthPx: Int, exact: Boolean): FramePixels?

    /** Audio peaks of [uri], decoded once and cached. */
    suspend fun waveform(uri: String): Waveform?
}
