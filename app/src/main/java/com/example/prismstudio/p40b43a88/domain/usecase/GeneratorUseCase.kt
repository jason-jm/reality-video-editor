package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument

/**
 * Resolve's Generators onto the timeline: the generator's image joins the media pool (once per size) and goes on at the
 * playhead on top — the first picture track above the target that is free there (Place on Top, F12), so it covers
 * nothing; a solid meant to sit underneath moves down a track like any clip.
 */
class GeneratorUseCase(private val sourceEdits: SourceEditUseCase) {

    data class Result(val doc: ProjectDocument, val edit: EditResult)

    fun place(doc: ProjectDocument, generator: Generator, asset: MediaAsset, at: Int): Result {
        if (asset.id != Generator.assetId(generator, doc.format.width, doc.format.height)) throw EditRejected("生成器的尺寸与时间线不符")
        val media = if (doc.asset(asset.id) == null) doc.media + asset else doc.media
        val edit = sourceEdits(doc.timeline, asset, 0, asset.durationMs, at, SourceEdit.PLACE_ON_TOP, doc.format.fps)
        return Result(doc.copy(media = media, timeline = edit.timeline), edit.copy(message = "已添加${generator.label}：${edit.message}"))
    }
}
