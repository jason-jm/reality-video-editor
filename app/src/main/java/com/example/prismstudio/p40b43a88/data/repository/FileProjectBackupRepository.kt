package com.example.prismstudio.p40b43a88.data.repository

import android.util.Log
import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Backups as `<dir>/<project id>/<taken at millis>.json` (the project file's bytes), written through a temporary file. A
 * small `<taken at>.meta` beside each holds what the list shows, so listing does not decode every backup.
 */
class FileProjectBackupRepository(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ProjectBackupRepository {

    override suspend fun list(projectId: String): List<ProjectBackup> = withContext(io) {
        val dir = folder(projectId)
        if (Files.notExists(dir.toPath())) return@withContext emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?: throw IOException("Cannot list project backups")
        files.mapNotNull { file ->
            val takenAt = file.name.removeSuffix(SUFFIX).toLongOrNull() ?: return@mapNotNull null
            readDescription(file, projectId, takenAt)
                ?: runCatching { ProjectJsonCodec.decode(file.readText()) }.getOrNull()?.let { describe(it, takenAt, file.length()) }
        }.sortedByDescending { it.takenAtMillis }
    }

    override suspend fun write(document: ProjectDocument, takenAtMillis: Long): ProjectBackup = withContext(io) {
        val dir = folder(document.id).apply { mkdirs() }
        val target = File(dir, "$takenAtMillis$SUFFIX")
        target.writeProject(document)
        val backup = describe(document, takenAtMillis, target.length())
        // The snapshot is committed. A missing description can be rebuilt from the JSON on the next read.
        try {
            File(dir, "$takenAtMillis$META").writeText("${backup.clips},${backup.durationFrames},${backup.fps}")
        } catch (e: IOException) {
            Log.w(TAG, "Cannot cache backup description ${document.id}/$takenAtMillis", e)
        }
        backup
    }

    override suspend fun read(projectId: String, takenAtMillis: Long): ProjectDocument? = withContext(io) {
        val file = File(folder(projectId), "$takenAtMillis$SUFFIX")
        if (!file.isFile) null else runCatching { ProjectJsonCodec.decode(file.readText()) }
            .onFailure { Log.w(TAG, "Cannot read backup $projectId/$takenAtMillis", it) }
            .getOrNull()
    }

    override suspend fun prune(projectId: String, keep: Set<Long>) = withContext(io) {
        folder(projectId).listFiles().orEmpty().forEach { f ->
            val takenAt = f.name.substringBefore('.').toLongOrNull() ?: return@forEach
            if (takenAt !in keep) f.delete()
        }
    }

    override suspend fun deleteAll(projectId: String) {
        withContext(io) {
            val dir = folder(projectId)
            if (!dir.deleteRecursively() && !Files.notExists(dir.toPath())) throw IOException("Cannot delete project backups")
        }
    }

    private fun folder(projectId: String): File {
        require(projectId.isNotBlank() && projectId != "." && projectId != ".." && File(projectId).name == projectId) {
            "Expected a project ID in one path segment"
        }
        return File(directory, projectId)
    }

    private fun readDescription(file: File, projectId: String, takenAt: Long): ProjectBackup? {
        val meta = File(file.parentFile, "$takenAt$META")
        if (!meta.isFile) return null
        val fields = try {
            meta.readText().split(',')
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read backup description $projectId/$takenAt", e)
            return null
        }
        if (fields.size != 3) return null
        val (clips, duration, fps) = fields.map { it.trim().toIntOrNull() ?: return null }
        if (clips < 0 || duration < 0 || fps <= 0) return null
        return ProjectBackup(projectId, takenAt, file.length(), clips, duration, fps)
    }

    private fun describe(document: ProjectDocument, takenAt: Long, size: Long) =
        ProjectBackup(document.id, takenAt, size, document.summary().clipCount, document.durationFrames, document.format.fps)

    private companion object {
        const val SUFFIX = ".json"
        const val META = ".meta"
        const val TAG = "PrismBackups"
    }
}
