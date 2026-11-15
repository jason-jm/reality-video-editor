package com.example.prismstudio.p40b43a88.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pictures of the Color page's Gallery stills, by project and file name. */
interface GalleryRepository {
    /** Stores [pixels] as [name] for project [projectId]; false when it could not be written. */
    suspend fun save(projectId: String, name: String, pixels: FramePixels): Boolean

    /** The picture [name] of project [projectId], at most [maxWidth] pixels wide; null when it is missing. */
    suspend fun load(projectId: String, name: String, maxWidth: Int): FramePixels?
}

/** Pictures kept in memory: for tests and previews. */
class InMemoryGalleryRepository : GalleryRepository {
    private val pictures = mutableMapOf<String, FramePixels>()

    override suspend fun save(projectId: String, name: String, pixels: FramePixels): Boolean {
        synchronized(pictures) { pictures["$projectId/$name"] = pixels }
        return true
    }

    override suspend fun load(projectId: String, name: String, maxWidth: Int): FramePixels? = synchronized(pictures) { pictures["$projectId/$name"] }
}

/** Stills as JPEG files in [dir]/<project id>/, decoded down to the size asked for. */
class FileGalleryRepository(private val dir: File) : GalleryRepository {

    override suspend fun save(projectId: String, name: String, pixels: FramePixels): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(dir, projectId).apply { mkdirs() }
            val bitmap = Bitmap.createBitmap(pixels.pixels, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
            val temp = File(folder, "$name.tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            temp.renameTo(File(folder, name))
        }.onFailure { Log.w(TAG, "Cannot save still $name", it) }.getOrDefault(false)
    }

    override suspend fun load(projectId: String, name: String, maxWidth: Int): FramePixels? = withContext(Dispatchers.IO) {
        val file = File(File(dir, projectId), name)
        if (!file.isFile) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val bitmap = if (decoded.width > maxWidth) {
                Bitmap.createScaledBitmap(decoded, maxWidth, (decoded.height.toLong() * maxWidth / decoded.width).toInt().coerceAtLeast(1), true)
            } else decoded
            val out = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            FramePixels(bitmap.width, bitmap.height, out)
        }.onFailure { Log.w(TAG, "Cannot read still $name", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "PrismGallery"
        const val QUALITY = 92
    }
}
