package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlin.math.abs

/**
 * `timeline-settings-sheet`: Resolve's project settings for the timeline — the raster (Master Settings) and how
 * mismatched media fill it (Image Scaling). Transforms, crops, windows and titles are authored relative to the frame,
 * so a layout follows the frame into its new shape (a landscape cut becomes a vertical one); the frame rate stays, as
 * clips sit on its frames.
 */
class TimelineSettingsUseCase {

    data class Result(val doc: ProjectDocument, val message: String)

    /**
     * The open timeline named [name] (trimmed, unique among the project's timelines) on the [preset] raster with
     * [scaling] for clips that follow the project.
     */
    operator fun invoke(doc: ProjectDocument, preset: FormatPreset, scaling: ClipScaling, name: String = doc.timelineName): Result {
        val title = name.trim()
        if (title.isEmpty()) throw EditRejected("时间线名称不能为空")
        if (title != doc.timelineName && doc.timelines.any { it.name == title }) throw EditRejected("项目里已有名为“$title”的时间线")
        val format = doc.format.copy(width = preset.width, height = preset.height, scaling = scaling)
        if (format == doc.format && title == doc.timelineName) throw EditRejected("时间线设置没有变化")
        val changes = buildList {
            if (title != doc.timelineName) add("时间线改名为“$title”")
            if (!preset.matches(doc.format)) add("时间线改为 ${format.width}×${format.height} ${format.orientationLabel}")
            if (scaling != doc.format.scaling) add("比例不同的素材${scaling.label}")
        }
        return Result(doc.copy(format = format, timelineName = title), changes.joinToString("，"))
    }

    /**
     * `timeline-settings-note`: how many picture clips follow the project's scaling, and how many of those [scaling]
     * changes on the [preset] raster (another shape — or, at their own size, another size).
     */
    fun followers(doc: ProjectDocument, preset: FormatPreset, scaling: ClipScaling): String {
        val following = doc.timeline.clips.filter { it.kind == ClipKind.VIDEO && it.scaling == null }
        if (following.isEmpty()) return "没有按项目设置缩放的片段：检查器的缩放方式选“按项目”的片段才会按这里显示"
        val differing = following.count { clip ->
            val asset = doc.asset(clip.assetId) ?: return@count false
            if (asset.kind == MediaKind.AUDIO || asset.width <= 0 || asset.height <= 0) return@count false
            if (scaling == ClipScaling.CROP) {
                asset.width != preset.width || asset.height != preset.height
            } else {
                abs(asset.width.toFloat() / asset.height / (preset.width.toFloat() / preset.height) - 1f) >= SAME_SHAPE
            }
        }
        val head = "${following.size} 个画面片段按项目设置缩放"
        return when {
            differing == 0 && scaling == ClipScaling.CROP -> "$head，都与画面尺寸相同"
            differing == 0 -> "$head，都与画面比例相同"
            scaling == ClipScaling.CROP -> "$head，其中 $differing 个与画面尺寸不同"
            else -> "$head，其中 $differing 个与画面比例不同"
        }
    }

    private companion object {
        /** Shapes within 1 % count as the same (as the inspector's note). */
        const val SAME_SHAPE = 0.01f
    }
}
