package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads picked documents through the content resolver, on the IO dispatcher. */
class AndroidDocumentRepository(context: Context) : DocumentRepository {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String, maxBytes: Int): PickedDocument = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val name = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: parsed.lastPathSegment?.substringAfterLast('/') ?: "字幕文件"
        val bytes = try {
            resolver.openInputStream(parsed)?.use { it.readNBytes(maxBytes + 1) } ?: throw DocumentFailure("无法读取文件：$name")
        } catch (e: DocumentFailure) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read $uri", e)
            throw DocumentFailure("无法读取文件：$name")
        }
        if (bytes.size > maxBytes) throw DocumentFailure("文件过大：$name")
        PickedDocument(name, bytes)
    }

    private companion object {
        const val TAG = "PrismDocuments"
    }
}
