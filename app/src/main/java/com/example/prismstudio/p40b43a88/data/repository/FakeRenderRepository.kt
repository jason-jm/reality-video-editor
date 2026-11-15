package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import kotlinx.coroutines.delay

/** Renders nothing: reports progress in steps and succeeds, or fails with [failWith]. */
class FakeRenderRepository(var failWith: String? = null, private val stepMs: Long = 10) : RenderRepository {
    val rendered = mutableListOf<String>()
    /** The documents rendered, in order (an individual clip's job renders that clip alone). */
    val documents = mutableListOf<ProjectDocument>()

    override suspend fun render(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
    ): RenderOutput {
        for (step in 1..4) {
            delay(stepMs)
            onProgress(step / 4f)
        }
        failWith?.let { throw RenderFailure(it) }
        rendered += settings.fileName
        documents += document
        return RenderOutput("content://fake/${settings.fileName}.mp4", 1_048_576)
    }

    /** Stills asked for, as (file name, frame). */
    val stills = mutableListOf<Pair<String, Int>>()

    override suspend fun renderStill(document: ProjectDocument, settings: RenderSettings, frame: Int): RenderOutput {
        delay(stepMs)
        failWith?.let { throw RenderFailure(it) }
        stills += settings.fileName to frame
        return RenderOutput("content://fake/${settings.fileName}.png", 2_097_152)
    }
}
