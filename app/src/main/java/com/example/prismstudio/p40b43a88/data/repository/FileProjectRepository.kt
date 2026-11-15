package com.example.prismstudio.p40b43a88.data.repository

import android.util.Log
import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Stores each project as `<dir>/<id>.json`, written atomically through a temporary file. */
class FileProjectRepository(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ProjectRepository {

    private val _library = MutableStateFlow(ProjectLibrary())
    override val library: StateFlow<ProjectLibrary> = _library.asStateFlow()
    private val lock = Mutex()

    override suspend fun refresh() = lock.withLock {
        _library.value = withContext(io) {
            Files.createDirectories(directory.toPath())
            val files = directory.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
                ?: throw IOException("Cannot list project directory")
            val summaries = mutableListOf<ProjectSummary>()
            val unreadable = mutableListOf<String>()
            for (file in files) {
                try {
                    summaries += readProject(file).summary()
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot index project ${file.name}", e)
                    unreadable += file.nameWithoutExtension
                }
            }
            ProjectLibrary(summaries, unreadable.sorted())
        }
    }

    override suspend fun load(id: String): ProjectDocument? = withContext(io) {
        val file = fileFor(id)
        if (!file.isFile) null else runCatching { readProject(file) }
            .onFailure { Log.w(TAG, "Cannot read project $id", it) }
            .getOrNull()
    }

    override suspend fun save(document: ProjectDocument) {
        lock.withLock {
            withContext(io) {
                directory.mkdirs()
                fileFor(document.id).writeProject(document)
            }
            val summary = document.summary()
            _library.update { it.copy(
                summaries = it.summaries.filterNot { item -> item.id == summary.id } + summary,
                unreadableIds = it.unreadableIds - summary.id,
            ) }
        }
    }

    override suspend fun delete(id: String) = lock.withLock {
        withContext(io) { Files.deleteIfExists(fileFor(id).toPath()) }
        _library.update { it.copy(
            summaries = it.summaries.filterNot { item -> item.id == id },
            unreadableIds = it.unreadableIds - id,
        ) }
    }

    private fun readProject(file: File): ProjectDocument {
        val doc = ProjectJsonCodec.decode(file.readText())
        require(doc.id == file.nameWithoutExtension) { "Project ID does not match its filename" }
        return doc
    }

    private fun fileFor(id: String) = File(directory, "$id$SUFFIX")

    private companion object {
        const val SUFFIX = ".json"
        const val TAG = "PrismProjects"
    }
}
