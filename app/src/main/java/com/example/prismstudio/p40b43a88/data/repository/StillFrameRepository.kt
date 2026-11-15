package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset

/** Freeze frames: one frame of a video, saved as an image the timeline can hold. */
interface StillFrameRepository {

    /** The frame of [source] at [timeMs], decoded exactly and saved as the image asset [id] named [name]; null when it cannot be read. */
    suspend fun capture(source: MediaAsset, timeMs: Long, id: String, name: String): MediaAsset?

    object None : StillFrameRepository {
        override suspend fun capture(source: MediaAsset, timeMs: Long, id: String, name: String): MediaAsset? = null
    }
}
