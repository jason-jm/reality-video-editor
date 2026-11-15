package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import kotlinx.coroutines.flow.StateFlow

data class ProjectLibrary(
    val summaries: List<ProjectSummary> = emptyList(),
    val unreadableIds: List<String> = emptyList(),
)

/** Persistent store of editing projects. */
interface ProjectRepository {
    /** Readable summaries and files that need recovery, published together after each scan or write. */
    val library: StateFlow<ProjectLibrary>

    suspend fun refresh()
    suspend fun load(id: String): ProjectDocument?
    suspend fun save(document: ProjectDocument)
    suspend fun delete(id: String)
}
