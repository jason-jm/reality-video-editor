package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.GradeVersion
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/**
 * Resolve's local grade versions: a clip can hold several grades and show one. A new version starts as a copy of the
 * one shown (to try another look without losing it); taking up another version keeps what was done to the one left;
 * deleting drops the one shown and shows the one before it. Renders and the viewer use the version shown.
 */
class GradeVersionsUseCase {

    /** [clip]'s versions — at least the one it shows — with the shown one's grade current. */
    fun versions(clip: Clip): List<GradeVersion> =
        if (clip.gradeVersions.isEmpty()) listOf(GradeVersion(FIRST, clip.grade))
        else clip.gradeVersions.mapIndexed { i, v -> if (i == clip.gradeVersion) v.copy(grade = clip.grade) else v }

    fun active(clip: Clip): Int = if (clip.gradeVersions.isEmpty()) 0 else clip.gradeVersion.coerceIn(0, clip.gradeVersions.lastIndex)

    fun create(timeline: Timeline, clipId: String?): EditResult {
        val clip = picture(timeline, clipId)
        val all = versions(clip)
        if (all.size >= MAX) throw EditRejected("一个片段最多 $MAX 个调色版本")
        val number = (all.mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: all.size) + 1
        val fresh = GradeVersion("版本 $number", clip.grade)
        val from = all[active(clip)].name
        val updated = clip.copy(gradeVersions = all + fresh, gradeVersion = all.size)
        return EditResult(timeline.replaceClip(updated), clip.id, "已新建${fresh.name}（复制自$from）")
    }

    fun select(timeline: Timeline, clipId: String?, index: Int): EditResult {
        val clip = picture(timeline, clipId)
        val all = versions(clip)
        val target = all.getOrNull(index) ?: throw EditRejected("没有这个调色版本")
        if (index == active(clip)) throw EditRejected("已经是${target.name}")
        val updated = clip.copy(gradeVersions = all, gradeVersion = index, grade = target.grade)
        return EditResult(timeline.replaceClip(updated), clip.id, "已切换到${target.name}")
    }

    fun delete(timeline: Timeline, clipId: String?): EditResult {
        val clip = picture(timeline, clipId)
        val all = versions(clip)
        if (all.size <= 1) throw EditRejected("片段只有一个调色版本")
        val at = active(clip)
        val left = all.filterIndexed { i, _ -> i != at }
        val shown = (at - 1).coerceAtLeast(0)
        val updated = clip.copy(gradeVersions = left, gradeVersion = shown, grade = left[shown].grade)
        return EditResult(timeline.replaceClip(updated), clip.id, "已删除${all[at].name}，显示${left[shown].name}")
    }

    private fun picture(timeline: Timeline, clipId: String?): Clip {
        val clip = timeline.clip(clipId) ?: throw EditRejected("请先选择片段")
        if (clip.kind != ClipKind.VIDEO) throw EditRejected("只有画面片段有调色版本")
        timeline.requireUnlocked(listOf(clip), clip)
        return clip
    }

    companion object {
        const val FIRST = "版本 1"
        const val MAX = 8
        private val NUMBERED = Regex("版本 (\\d+)")
    }
}
