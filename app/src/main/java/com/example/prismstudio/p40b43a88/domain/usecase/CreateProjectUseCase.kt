package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/** Builds a new, empty project document (four default tracks, neutral mixer, B站 preset). */
class CreateProjectUseCase(private val ids: IdGenerator, private val clock: () -> Long) {

    operator fun invoke(name: String, preset: FormatPreset, fps: Int): ProjectDocument {
        val now = clock()
        val title = name.trim().ifEmpty { DEFAULT_NAME }
        val timeline = Timeline.EMPTY
        return ProjectDocument(
            id = ids.next("p"),
            name = title,
            format = ProjectFormat.of(preset, fps),
            createdAtMillis = now,
            modifiedAtMillis = now,
            media = emptyList(),
            timeline = timeline,
            mixer = MixerState.forTracks(timeline.tracks),
            render = RenderSettings(fileName = title.replace(Regex("\\s+"), "_"), fps = fps),
            jobs = emptyList(),
        )
    }

    /** A copy with a new id and a "副本" suffix. */
    fun duplicate(source: ProjectDocument): ProjectDocument {
        val now = clock()
        return source.copy(
            id = ids.next("p"),
            name = "${source.name} 副本",
            createdAtMillis = now,
            modifiedAtMillis = now,
            jobs = emptyList(),
        )
    }

    companion object {
        const val DEFAULT_NAME = "未命名项目"
    }
}
