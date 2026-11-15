package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProjectLabelsUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val labels = ProjectLabelsUseCase(zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(2026, 10, 4, 15, 30)

    private fun summary(format: ProjectFormat, durationFrames: Int) =
        ProjectSummary("p", "Trip", format, durationFrames, 3, 0, PosterColors.NEUTRAL)

    @Test
    fun `modification time reads today yesterday or a date in the configured zone`() {
        assertEquals("今天 09:05", labels.modified(at(2026, 10, 4, 9, 5), now))
        assertEquals("今天 00:10", labels.modified(at(2026, 10, 4, 0, 10), now))
        assertEquals("昨天 23:59", labels.modified(at(2026, 10, 3, 23, 59), now))
        assertEquals("3月7日", labels.modified(at(2026, 3, 7, 12, 0), now))
        assertEquals("2025年12月31日", labels.modified(at(2025, 12, 31, 8, 0), now))
    }

    @Test
    fun `meta line shows the format short label frame rate and duration`() {
        assertEquals("1080p · 30 fps · 02:14", labels.meta(summary(ProjectFormat(1920, 1080, 30), durationFrames = 134 * 30)))
        assertEquals("4K · 25 fps · 00:00", labels.meta(summary(ProjectFormat(3840, 2160, 25), durationFrames = 0)))
        assertEquals("1080×1920 · 60 fps · 01:15", labels.meta(summary(ProjectFormat(1080, 1920, 60), durationFrames = 75 * 60)))
    }

    @Test
    fun `count and format lines describe the library and the raster`() {
        assertEquals("找到 2 个项目 · 共 5 个", labels.count(total = 5, shown = 2, query = "trip"))
        assertEquals("5 个项目 · 按最近修改排序", labels.count(total = 5, shown = 5, query = "  "))
        assertEquals("1080×1920 竖屏", labels.format(ProjectFormat(1080, 1920, 30)))
        assertEquals("1920×1080 横屏", labels.format(ProjectFormat(1920, 1080, 30)))
    }

    @Test
    fun `save status reports failure first then pending then the last save time`() {
        val savedAt = at(2026, 10, 4, 14, 7)

        assertEquals("保存失败 · 检查存储后可再次返回重试", labels.saveStatus(savedAt, pending = true, failed = true))
        assertEquals("正在保存…", labels.saveStatus(savedAt, pending = true, failed = false))
        assertEquals("已自动保存 · 14:07", labels.saveStatus(savedAt, pending = false, failed = false))
        assertEquals("尚未保存", labels.saveStatus(null, pending = false, failed = false))
    }
}
