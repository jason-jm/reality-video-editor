package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import com.example.prismstudio.p40b43a88.domain.model.CameraPath
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.StabilizationPlan
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Resolve's Stabilization, the rules: which clips can be stabilized, which files need their camera analysed, the plan
 * a clip follows over the part of its file it shows, and what the inspector says about it.
 */
class StabilizationUseCase {

    /** Why [clip] cannot be stabilized, or null when it can: footage only, played forward. */
    fun unavailable(clip: Clip, asset: MediaAsset?): String? = when {
        clip.kind != ClipKind.VIDEO || asset?.kind != MediaKind.VIDEO -> "只有视频片段可以稳定"
        clip.reverse -> "倒放的片段不能稳定"
        else -> null
    }

    /** Whether [clip] is stabilized and can be. */
    fun active(clip: Clip, asset: MediaAsset?): Boolean = clip.stabilization?.enabled == true && unavailable(clip, asset) == null

    /** The files [doc]'s stabilized clips need analysed. */
    fun wanted(doc: ProjectDocument): List<MediaAsset> = doc.timeline.clips
        .filter { it.stabilization?.enabled == true }
        .mapNotNull { clip -> doc.asset(clip.assetId)?.takeIf { active(clip, it) } }
        .distinctBy { it.uri }

    /** [clip]'s plan along its file's [path] at [fps]: over the file frames the clip shows, so its zoom answers for them. */
    fun plan(clip: Clip, path: CameraPath, fps: Int): StabilizationPlan {
        val settings = clip.stabilization ?: ClipStabilization.DEFAULT
        val fromMs = clip.sourceInMs.toDouble()
        val untilMs = fromMs + clip.sourceDurationMs(fps)
        val first = (fromMs / 1000 * path.frameRate).toInt()
        val until = ceil(untilMs / 1000 * path.frameRate).toInt() + 1
        return StabilizationPlan.of(settings, path, first, until)
    }

    /**
     * [clip]'s move at each of its frames 0…its length (x, y as shares of the picture) through its speed, and its zoom —
     * what the shader reads.
     */
    fun samples(clip: Clip, path: CameraPath, fps: Int): Samples {
        val plan = plan(clip, path, fps)
        val xs = FloatArray(clip.durationFrames + 1)
        val ys = FloatArray(clip.durationFrames + 1)
        val camX = FloatArray(clip.durationFrames + 1)
        val camY = FloatArray(clip.durationFrames + 1)
        for (f in 0..clip.durationFrames) {
            val seconds = (clip.sourceInMs + clip.sourceOffsetMs(f, fps)) / 1000
            val (x, y) = plan.offsetAt(seconds)
            xs[f] = x
            ys[f] = y
            val i = (seconds * path.frameRate).roundToInt().coerceIn(0, path.frames - 1)
            camX[f] = path.x[i]
            camY[f] = path.y[i]
        }
        return Samples(xs, ys, plan.scale, camX, camY)
    }

    /** A clip's stabilization frame by frame: the moves, the zoom, and the camera path they answer. */
    class Samples(val x: FloatArray, val y: FloatArray, val scale: Float, val cameraX: FloatArray, val cameraY: FloatArray)

    /**
     * What the inspector says about a stabilized clip whose file stands at [analysis]: being analysed (how far), the
     * shake found and what the zoom does, or why it failed.
     */
    fun status(clip: Clip, analysis: CameraAnalysis?, fps: Int): String {
        val settings = clip.stabilization ?: return ""
        if (!settings.enabled) return ""
        return when (analysis) {
            null, is CameraAnalysis.Analysing -> "正在分析摄像机运动 ${((analysis as? CameraAnalysis.Analysing)?.progress ?: 0f).times(100).roundToInt()}%"
            is CameraAnalysis.Failed -> "分析失败：${analysis.reason}"
            is CameraAnalysis.Ready -> {
                val s = samples(clip, analysis.path, fps)
                val shake = s.x.indices.maxOfOrNull { maxOf(kotlin.math.abs(s.x[it]), kotlin.math.abs(s.y[it])) } ?: 0f
                val move = "画面最多移动 ${percent(shake)}"
                when {
                    shake < 0.0005f -> "没有需要去掉的抖动"
                    !settings.zoom -> "$move，边缘会露出"
                    else -> "$move，放大 ${(s.scale * 100).roundToInt()}% 遮住边缘"
                }
            }
        }
    }

    /** The zoom readout beside the 放大 switch: how much the picture is enlarged, once analysed. */
    fun zoomLabel(clip: Clip, analysis: CameraAnalysis?, fps: Int): String {
        val settings = clip.stabilization ?: return ""
        if (!settings.zoom) return "不放大"
        val path = (analysis as? CameraAnalysis.Ready)?.path ?: return "自动"
        return "${(plan(clip, path, fps).scale * 100).roundToInt()}%"
    }

    private fun percent(share: Float): String = "%.1f%%".format(share * 100)
}
