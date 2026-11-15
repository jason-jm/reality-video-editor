package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings

/** The published media and the result of saving any requested subtitle files, one per language. */
data class RenderOutput(
    val uri: String,
    val bytes: Long,
    val subtitlesUri: String? = null,
    val subtitleUris: List<String> = listOfNotNull(subtitlesUri),
    val missingSubtitles: List<String> = emptyList(),
    /** Source filenames whose picture or sound is missing from the saved output. */
    val offlineMedia: List<String> = emptyList(),
)

/** Thrown when a render cannot be completed; [message] is shown to the user. */
class RenderFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Encodes the timeline to a video file. */
interface RenderRepository {
    /**
     * Renders [document] (frames [rangeStart] until [rangeEnd], or everything when null) with
     * [settings] and publishes the result. Cancelling the calling coroutine aborts the render.
     */
    suspend fun render(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
    ): RenderOutput

    /**
     * Resolve's Export › Current Frame as Still: [frame] of [document] as an export draws it at [settings]'s raster
     * — grade, effects, titles, every layer — saved as a PNG named [RenderSettings.fileName] in Pictures/Reality Video Editor.
     */
    suspend fun renderStill(document: ProjectDocument, settings: RenderSettings, frame: Int): RenderOutput =
        throw RenderFailure("这里不能导出静帧。")
}
