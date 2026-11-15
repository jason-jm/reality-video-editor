package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ProjectBackup
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Resolve's Project Backups: when one is due, which are kept as they age, and a backup restored as a new project. */
class ProjectBackupsUseCaseTest {

    private val use = ProjectBackupsUseCase(SequentialIds(), ZoneOffset.UTC)
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `a backup is due with none yet or ten minutes after the last`() {
        assertTrue(use.due(null, 0))
        assertFalse(use.due(1_000_000, 1_000_000 + 9 * minute))
        assertTrue(use.due(1_000_000, 1_000_000 + 10 * minute))
    }

    @Test
    fun `backups thin out with age - all of the last hour, one an hour for a day, one a day for a week, none older`() {
        val now = 30 * day
        val lastHour = (0 until 6).map { now - it * 10 * minute }
        val today = (2 until 6).flatMap { h -> listOf(now - h * hour - 5 * minute, now - h * hour - 25 * minute) }
        val week = listOf(now - 2 * day - hour, now - 2 * day - 3 * hour, now - 5 * day)
        val old = listOf(now - 9 * day)
        val kept = use.keep(lastHour + today + week + old, now)
        assertTrue("the last hour stays whole", kept.containsAll(lastHour))
        assertEquals("one per hour today: the newer of each pair", (2 until 6).map { h -> now - h * hour - 5 * minute }.toSet(), kept.intersect(today.toSet()))
        assertEquals("one per day this week", setOf(now - 2 * day - hour, now - 5 * day), kept.intersect(week.toSet()))
        assertFalse(kept.contains(old.single()))
        assertEquals("the newest always stays, however old", setOf(now - 20 * day), use.keep(listOf(now - 20 * day, now - 25 * day), now))
    }

    @Test
    fun `a restored backup is a new project named after the moment, without render jobs`() {
        val doc = UiFixtures.document(id = "p-old", name = "NightRun")
        val takenAt = 1_760_000_000_000L // 2025-10-09 08:53:20 UTC
        val restored = use.restore(doc, takenAt, 1_760_000_500_000L)
        assertEquals("NightRun（备份 10-09 08:53）", restored.name)
        assertTrue(restored.id != "p-old")
        assertEquals(1_760_000_500_000L, restored.createdAtMillis)
        assertEquals(doc.timeline, restored.timeline)
        assertTrue(restored.jobs.isEmpty())
    }

    @Test
    fun `the list says when, how long ago and what the project held`() {
        val b = ProjectBackup("p", 1_760_000_000_000L, 48 * 1024, 6, 1020, 30)
        assertEquals("10月9日 08:53", use.title(b))
        assertEquals("3 分钟前 · 6 个片段 · 00:00:34:00 · 48 KB", use.detail(b, b.takenAtMillis + 3 * minute))
        assertEquals("刚刚 · 6 个片段 · 00:00:34:00 · 48 KB", use.detail(b, b.takenAtMillis + 10_000))
        assertEquals("2 天前 · 6 个片段 · 00:00:34:00 · 2 MB", use.detail(b.copy(sizeBytes = 2L * 1024 * 1024), b.takenAtMillis + 2 * day + hour))
    }
}
