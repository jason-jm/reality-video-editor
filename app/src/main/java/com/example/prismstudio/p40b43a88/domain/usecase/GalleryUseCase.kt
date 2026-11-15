package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.GalleryStill
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.frozenAt
import kotlin.math.roundToInt

/**
 * Resolve's Gallery on the Color page: grab a still of a clip at a frame (the picture is stored apart, the
 * grade that made it goes with it, frozen there), grade another clip from a still, delete stills.
 */
class GalleryUseCase {

    /** The still of [clip] grabbed [clipFrame] frames into it, at timeline [timecode], its picture [image]. */
    @Suppress("LongParameterList")
    fun grab(doc: ProjectDocument, clip: Clip, clipFrame: Int, id: String, image: String, width: Int, height: Int, timecode: String): GalleryStill =
        GalleryStill(
            id = id,
            number = (doc.stills.maxOfOrNull { it.number } ?: 0) + 1,
            clipName = clip.name,
            timecode = timecode,
            grade = clip.grade.frozenAt(clipFrame.coerceIn(0, (clip.durationFrames - 1).coerceAtLeast(0))),
            image = image,
            width = width,
            height = height,
        )

    fun add(doc: ProjectDocument, still: GalleryStill): ProjectDocument = doc.copy(stills = doc.stills + still)

    fun remove(doc: ProjectDocument, id: String): ProjectDocument = doc.copy(stills = doc.stills.filterNot { it.id == id })

    /** [clipId] graded as [stillId] was: its whole grade, window node included (keyframes resolved at the grab). */
    fun apply(doc: ProjectDocument, stillId: String, clipId: String): ProjectDocument {
        val still = doc.stills.firstOrNull { it.id == stillId } ?: throw EditRejected("静帧已删除")
        val clip = doc.timeline.clip(clipId) ?: throw EditRejected("请先选择一个画面片段")
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只能把调色应用到画面片段")
        return doc.copy(timeline = doc.timeline.replaceClip(clip.copy(grade = still.grade)))
    }

    companion object {
        /** Width of a still's stored picture: sharp enough to wipe against at monitor size. */
        const val STILL_WIDTH = 960

        /**
         * [frame] in a [width] × [height] picture (the timeline's shape) as [framing] places it — centred, fitted with
         * black bars when null — as the monitor shows it.
         */
        fun fitted(frame: FramePixels, width: Int, height: Int, framing: Framing? = null): FramePixels {
            val (spanX, spanY) = framing ?: Framing.fit(frame.width.toFloat() / frame.height, width.toFloat() / height)
            if (frame.width == width && frame.height == height && spanX == 1f && spanY == 1f) return frame
            val w = (width * spanX).roundToInt().coerceAtLeast(1)
            val h = (height * spanY).roundToInt().coerceAtLeast(1)
            val x0 = (width - w) / 2
            val y0 = (height - h) / 2
            val out = IntArray(width * height) { BLACK }
            for (y in maxOf(0, y0) until minOf(height, y0 + h)) {
                val sy = ((y - y0).toLong() * frame.height / h).toInt().coerceIn(0, frame.height - 1)
                for (x in maxOf(0, x0) until minOf(width, x0 + w)) {
                    val sx = ((x - x0).toLong() * frame.width / w).toInt().coerceIn(0, frame.width - 1)
                    out[y * width + x] = frame.pixels[sy * frame.width + sx]
                }
            }
            return FramePixels(width, height, out)
        }

        private const val BLACK = 0xFF000000.toInt()
    }
}
