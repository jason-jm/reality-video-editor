package com.example.prismstudio.p40b43a88.domain.model

/** One of a project's backups (Resolve's Project Backups): when it was taken and what the project held then ([clips] counted as the library counts them: picture and title clips). */
data class ProjectBackup(
    val projectId: String,
    val takenAtMillis: Long,
    val sizeBytes: Long,
    val clips: Int,
    val durationFrames: Int,
    val fps: Int,
)
