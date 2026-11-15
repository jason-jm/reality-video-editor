package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import com.example.prismstudio.p40b43a88.domain.model.Timecode

/** Media-pool filtering by bin and search text (the name or the clip metadata), plus the pool summary line. */
class FilterMediaUseCase {

    operator fun invoke(items: List<MediaAsset>, bin: MediaBin, query: String): List<MediaAsset> {
        val q = query.trim()
        return items.filter { bin.accepts(it.kind) && (q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.metadata.mentions(q)) }
    }

    fun summary(items: List<MediaAsset>): String {
        val totalMs = items.sumOf { it.durationMs }
        return "${items.size} 个素材 · 共 ${Timecode.shortDuration(totalMs)}"
    }
}

/** Project library ordering (most recently modified first) and search. */
class FilterProjectsUseCase {

    operator fun invoke(projects: List<ProjectSummary>, query: String): List<ProjectSummary> {
        val q = query.trim()
        return projects
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .sortedByDescending { it.modifiedAtMillis }
    }
}
