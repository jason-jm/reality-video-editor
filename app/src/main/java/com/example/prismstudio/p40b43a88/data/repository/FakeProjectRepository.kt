package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [ProjectRepository] used by tests. */
class FakeProjectRepository(initial: List<ProjectDocument> = emptyList()) : ProjectRepository {
    private val documents = LinkedHashMap<String, ProjectDocument>().apply { initial.forEach { put(it.id, it) } }
    private val _library = MutableStateFlow(snapshot())
    override val library: StateFlow<ProjectLibrary> = _library.asStateFlow()

    var saveCount = 0
        private set

    override suspend fun refresh() {
        _library.value = snapshot()
    }

    override suspend fun load(id: String): ProjectDocument? = documents[id]

    override suspend fun save(document: ProjectDocument) {
        documents[document.id] = document
        saveCount++
        refresh()
    }

    override suspend fun delete(id: String) {
        documents.remove(id)
        refresh()
    }

    private fun snapshot() = ProjectLibrary(summaries = documents.values.map { it.summary() })
}
