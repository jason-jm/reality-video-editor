package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Human labels for projects: relative modification time, format line, save status. */
class ProjectLabelsUseCase(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun modified(millis: Long, now: Long): String {
        val then = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            days == 0L -> "今天 ${time.format(then)}"
            days == 1L -> "昨天 ${time.format(then)}"
            then.year == today.year -> "${then.monthValue}月${then.dayOfMonth}日"
            else -> "${then.year}年${then.monthValue}月${then.dayOfMonth}日"
        }
    }

    /** "1080p · 30 fps · 02:14" */
    fun meta(summary: ProjectSummary): String =
        "${summary.format.shortLabel} · ${summary.format.fps} fps · ${Timecode.shortDuration(Timecode.framesToMs(summary.durationFrames, summary.format.fps))}"

    fun count(total: Int, shown: Int, query: String): String = when {
        query.isNotBlank() -> "找到 $shown 个项目 · 共 $total 个"
        else -> "$total 个项目 · 按最近修改排序"
    }

    fun format(format: ProjectFormat): String = format.sizeLabel

    fun saveStatus(savedAt: Long?, pending: Boolean, failed: Boolean): String = when {
        failed -> "保存失败 · 检查存储后可再次返回重试"
        pending -> "正在保存…"
        savedAt != null -> "已自动保存 · ${time.format(Instant.ofEpochMilli(savedAt).atZone(zone))}"
        else -> "尚未保存"
    }
}
