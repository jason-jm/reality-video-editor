package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.prismstudio.p40b43a88.domain.usecase.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files the app hands to the person: saved in the shared Download/Reality Video Editor folder, where other apps find them. */
interface DownloadsRepository {
    /**
     * Saves [bytes] as [fileName] (the system may number a clashing name) and says where it went, as
     * "Download/Reality Video Editor/<name>"; null when it could not be saved.
     */
    suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String?

    /** Saves nothing: for previews and tests. */
    object None : DownloadsRepository {
        override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String? = null
    }
}

/** [DownloadsRepository] through MediaStore's Downloads collection (no storage permission needed for the app's own files). */
class AndroidDownloadsRepository(context: Context) : DownloadsRepository {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(fileName))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            // The name it was given, which is numbered when one with the same name was there.
            val saved = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: safeName(fileName)
            "Download/$FOLDER/$saved"
        }.onFailure { Log.w(TAG, "Cannot save $fileName", it) }.getOrNull()
    }

    private fun safeName(name: String) = FileNames.safeWithExtension(name)

    private companion object {
        const val TAG = "PrismDownloads"
        const val FOLDER = PRODUCT_NAME
    }
}
