package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes generator images to `files/generators/<id>.png` (app storage, so the project keeps them). A generator at a given
 * size is one file: asked again, the file already there is used.
 */
class AndroidGeneratorRepository(context: Context) : GeneratorRepository {

    private val appContext = context.applicationContext

    override suspend fun create(generator: Generator, width: Int, height: Int, id: String): MediaAsset? = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$id.png")
        val pixels = generator.pixels(width, height)
        try {
            if (!file.exists() || file.length() == 0L) {
                val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                val temp = File(dir, "$id.tmp")
                temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                if (!temp.renameTo(file)) return@withContext null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write the generator image $id", e)
            return@withContext null
        }
        fun at(fraction: Float) = pixels[((height - 1) * fraction).toInt() * width + width / 2].toLong() and 0xFFFFFFFFL
        MediaAsset(
            id = id,
            uri = Uri.fromFile(file).toString(),
            name = generator.assetName(width, height),
            kind = MediaKind.IMAGE,
            durationMs = MediaAsset.IMAGE_DEFAULT_DURATION_MS,
            width = width,
            height = height,
            frameRate = 0f,
            videoCodec = "PNG",
            audioDescription = "无音频",
            hasAudio = false,
            sizeBytes = file.length(),
            relativePath = "$DIR/",
            poster = PosterColors(at(1f / 6), at(0.5f), at(5f / 6)),
        )
    }

    private companion object {
        const val TAG = "PrismGenerators"
        const val DIR = "generators"
    }
}
