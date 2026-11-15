package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolve's Project Backups: while a project is being edited it is backed up every [INTERVAL_MS] (after an autosave);
 * the backups thin out with age — all of the last hour, the newest of each hour for a day, of each day for a week —
 * and one is restored as a new project beside the original, which stays as it is.
 */
class ProjectBackupsUseCase(private val ids: IdGenerator, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** A backup is due after an autosave when the last one is at least [INTERVAL_MS] old (or there is none). */
    fun due(lastAtMillis: Long?, nowMillis: Long): Boolean = lastAtMillis == null || nowMillis - lastAtMillis >= INTERVAL_MS

    /** Which of the backups taken at [takenAt] to keep at [nowMillis]: the rest go. */
    fun keep(takenAt: List<Long>, nowMillis: Long): Set<Long> {
        val newestFirst = takenAt.distinct().sortedDescending()
        val kept = mutableSetOf<Long>()
        val hours = mutableSetOf<Long>()
        val days = mutableSetOf<Long>()
        for (t in newestFirst) {
            val age = (nowMillis - t).coerceAtLeast(0)
            when {
                age < HOUR_MS -> kept += t
                age < DAY_MS -> if (hours.add(age / HOUR_MS)) kept += t
                age < WEEK_MS -> if (days.add(age / DAY_MS)) kept += t
            }
        }
        newestFirst.firstOrNull()?.let(kept::add)
        return kept
    }

    /** [backup] (taken at [takenAtMillis]) as a new project: its own id, named after the moment, no render jobs. */
    fun restore(backup: ProjectDocument, takenAtMillis: Long, nowMillis: Long): ProjectDocument = backup.copy(
        id = ids.next("p"),
        name = "${backup.name}（备份 ${STAMP.format(Instant.ofEpochMilli(takenAtMillis).atZone(zone))}）",
        createdAtMillis = nowMillis,
        modifiedAtMillis = nowMillis,
        jobs = emptyList(),
    )

    /** A backup's title in the list: "10月14日 17:20". */
    fun title(backup: ProjectBackup): String {
        val at = Instant.ofEpochMilli(backup.takenAtMillis).atZone(zone)
        return "${at.monthValue}月${at.dayOfMonth}日 ${TIME.format(at)}"
    }

    /** What it holds and how old it is: "3 分钟前 · 6 个片段 · 00:00:34:00 · 48 KB". */
    fun detail(backup: ProjectBackup, nowMillis: Long): String {
        val minutes = ((nowMillis - backup.takenAtMillis).coerceAtLeast(0) / 60_000L)
        val age = when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            else -> "${minutes / (24 * 60)} 天前"
        }
        val size = if (backup.sizeBytes >= 1024 * 1024) "${backup.sizeBytes / (1024 * 1024)} MB" else "${(backup.sizeBytes / 1024).coerceAtLeast(1)} KB"
        return "$age · ${backup.clips} 个片段 · ${Timecode.format(backup.durationFrames, backup.fps.coerceAtLeast(1))} · $size"
    }

    companion object {
        const val INTERVAL_MS = 10 * 60_000L
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val WEEK_MS = 7 * DAY_MS
        /** What the backups sheet says the policy is. */
        const val POLICY = "编辑时每 10 分钟自动备份一次；保留最近 1 小时的全部备份、24 小时内每小时一份、7 天内每天一份"
        private val STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    }
}
