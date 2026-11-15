package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument

/** Backups held in memory, for tests and previews. */
class FakeProjectBackupRepository : ProjectBackupRepository {
    private val stored = LinkedHashMap<Pair<String, Long>, ProjectDocument>()

    /** Every backup written, oldest first. */
    val written = mutableListOf<Pair<String, Long>>()

    override suspend fun list(projectId: String): List<ProjectBackup> = stored.entries
        .filter { it.key.first == projectId }
        .map { (k, d) -> ProjectBackup(projectId, k.second, 1024, d.summary().clipCount, d.durationFrames, d.format.fps) }
        .sortedByDescending { it.takenAtMillis }

    override suspend fun write(document: ProjectDocument, takenAtMillis: Long): ProjectBackup {
        stored[document.id to takenAtMillis] = document
        written += document.id to takenAtMillis
        return list(document.id).first { it.takenAtMillis == takenAtMillis }
    }

    override suspend fun read(projectId: String, takenAtMillis: Long): ProjectDocument? = stored[projectId to takenAtMillis]

    override suspend fun prune(projectId: String, keep: Set<Long>) {
        stored.keys.removeAll { it.first == projectId && it.second !in keep }
    }

    override suspend fun deleteAll(projectId: String) {
        stored.keys.removeAll { it.first == projectId }
    }
}
