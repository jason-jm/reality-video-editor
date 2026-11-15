package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** A failed write or rename must leave the previous project or backup readable. */
internal fun File.writeProject(document: ProjectDocument) {
    val staged = File.createTempFile(".project-", ".tmp", parentFile)
    try {
        staged.writeText(ProjectJsonCodec.encode(document))
        Files.move(staged.toPath(), toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        staged.delete()
    }
}
