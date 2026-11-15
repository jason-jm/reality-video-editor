package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset

/** Makes a generator's image (Resolve's Generators) for the media pool. */
interface GeneratorRepository {

    /** [generator] at [width] × [height] as the image asset [id]; null when it cannot be written. */
    suspend fun create(generator: Generator, width: Int, height: Int, id: String): MediaAsset?

    object None : GeneratorRepository {
        override suspend fun create(generator: Generator, width: Int, height: Int, id: String): MediaAsset? = null
    }
}
