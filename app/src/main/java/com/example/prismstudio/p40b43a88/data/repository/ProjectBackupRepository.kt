package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument

/** A project's backups (Resolve's Project Backups), kept apart from the project itself. */
interface ProjectBackupRepository {
    /** [projectId]'s backups, newest first. */
    suspend fun list(projectId: String): List<ProjectBackup>

    /** [document] as it is, backed up at [takenAtMillis]. */
    suspend fun write(document: ProjectDocument, takenAtMillis: Long): ProjectBackup

    /** The project as backed up at [takenAtMillis]; null when the backup is gone or unreadable. */
    suspend fun read(projectId: String, takenAtMillis: Long): ProjectDocument?

    /** Removes [projectId]'s backups not in [keep] (taken-at times). */
    suspend fun prune(projectId: String, keep: Set<Long>)

    /** Removes every backup of [projectId] (the project was deleted). */
    suspend fun deleteAll(projectId: String)

    object None : ProjectBackupRepository {
        override suspend fun list(projectId: String): List<ProjectBackup> = emptyList()
        override suspend fun write(document: ProjectDocument, takenAtMillis: Long): ProjectBackup =
            ProjectBackup(document.id, takenAtMillis, 0, document.summary().clipCount, document.durationFrames, document.format.fps)
        override suspend fun read(projectId: String, takenAtMillis: Long): ProjectDocument? = null
        override suspend fun prune(projectId: String, keep: Set<Long>) = Unit
        override suspend fun deleteAll(projectId: String) = Unit
    }
}
