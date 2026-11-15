package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderQueueUseCaseTest {

    private val queue = RenderQueueUseCase(SequentialIds())
    private val settings = RenderSettings(fileName = " Trip ")

    private fun List<RenderJob>.job(id: String) = single { it.id == id }

    @Test
    fun `enqueue adds queued jobs and numbers repeated file names`() {
        val jobs = queue.enqueue(queue.enqueue(queue.enqueue(emptyList(), settings), settings), settings)

        assertEquals(listOf("j-1", "j-2", "j-3"), jobs.map { it.id })
        assertEquals(listOf("Trip.mp4", "Trip (2).mp4", "Trip (3).mp4"), jobs.map { it.fileName })
        assertEquals(listOf("Trip", "Trip (2)", "Trip (3)"), jobs.map { it.settings.fileName })
        assertEquals(setOf(RenderStatus.QUEUED), jobs.map { it.status }.toSet())
        assertEquals(setOf(0f), jobs.map { it.progress }.toSet())
    }

    @Test
    fun `the mix alone is queued as an m4a, numbered on its own`() {
        val audio = settings.copy(includeVideo = false)
        val jobs = queue.enqueue(queue.enqueue(queue.enqueue(emptyList(), settings), audio), audio)
        assertEquals(listOf("Trip.mp4", "Trip.m4a", "Trip (2).m4a"), jobs.map { it.fileName })
        assertEquals("Trip (2)", jobs.last().settings.fileName)
        assertEquals("仅音频 · AAC 256 kbps", jobs.last().settings.detailLabel)
    }

    @Test
    fun `a blank file name falls back to the app name`() {
        val job = queue.enqueue(emptyList(), RenderSettings(fileName = "   ")).single()

        assertEquals("Reality Video Editor.mp4", job.fileName)
        assertEquals("Reality Video Editor", job.settings.fileName)
    }

    @Test
    fun `jobs go from queued to rendering to done in queue order`() {
        var jobs = queue.enqueue(queue.enqueue(emptyList(), settings), RenderSettings("Other"))
        assertEquals("j-1", queue.next(jobs)?.id)

        jobs = queue.start(jobs, "j-1")
        assertEquals(RenderStatus.RENDERING, jobs.job("j-1").status)
        assertEquals(1f, queue.progress(jobs, "j-1", 1.5f).job("j-1").progress, 0f)
        assertEquals(0f, queue.progress(jobs, "j-1", -0.5f).job("j-1").progress, 0f)
        jobs = queue.progress(jobs, "j-1", 0.4f)
        assertEquals(0.4f, jobs.job("j-1").progress, 0f)
        assertEquals("渲染中 40%", jobs.job("j-1").statusLabel)
        assertEquals(0f, queue.progress(jobs, "j-2", 0.7f).job("j-2").progress, 0f) // not rendering yet

        jobs = queue.complete(jobs, "j-1", "content://out/Trip.mp4", 4_096)
        val done = jobs.job("j-1")
        assertEquals(RenderStatus.DONE, done.status)
        assertEquals(1f, done.progress, 0f)
        assertEquals("content://out/Trip.mp4", done.outputUri)
        assertEquals(4_096L, done.outputBytes)
        assertEquals("j-2", queue.next(jobs)?.id)

        jobs = queue.complete(queue.start(jobs, "j-2"), "j-2", "content://out/Other.mp4", 1)
        assertNull(queue.next(jobs))
    }

    @Test
    fun `a failed job keeps its reason and a retry puts it back in the queue`() {
        var jobs = queue.start(queue.enqueue(emptyList(), settings), "j-1")
        jobs = queue.progress(jobs, "j-1", 0.6f)

        jobs = queue.fail(jobs, "j-1", "磁盘已满")
        assertEquals(RenderStatus.FAILED, jobs.job("j-1").status)
        assertEquals("失败：磁盘已满", jobs.job("j-1").errorMessage)
        assertNull(queue.next(jobs))

        jobs = queue.retry(jobs, "j-1")
        assertEquals(RenderStatus.QUEUED, jobs.job("j-1").status)
        assertEquals(0f, jobs.job("j-1").progress, 0f)
        assertNull(jobs.job("j-1").errorMessage)
        assertEquals("j-1", queue.next(jobs)?.id)

        val done = queue.complete(queue.start(jobs, "j-1"), "j-1", "content://out/Trip.mp4", 10)
        assertEquals(done, queue.retry(done, "j-1"))
    }

    @Test
    fun `an interrupted render goes back to the queue from the start but finished jobs stay done`() {
        var jobs = queue.enqueue(queue.enqueue(emptyList(), settings), settings)
        jobs = queue.progress(queue.start(jobs, "j-1"), "j-1", 0.4f)
        jobs = queue.complete(queue.start(jobs, "j-2"), "j-2", "content://out/2", 10)

        jobs = queue.requeue(queue.requeue(jobs, "j-1"), "j-2")

        assertEquals(RenderStatus.QUEUED, jobs.job("j-1").status)
        assertEquals(0f, jobs.job("j-1").progress)
        assertEquals("j-1", queue.next(jobs)?.id)
        assertEquals(RenderStatus.DONE, jobs.job("j-2").status)
    }

    @Test
    fun `cancel removes queued and rendering jobs but keeps finished ones`() {
        var jobs = (1..4).fold(emptyList<RenderJob>()) { acc, _ -> queue.enqueue(acc, settings) }
        jobs = queue.complete(queue.start(jobs, "j-1"), "j-1", "content://out/1.mp4", 1)
        jobs = queue.fail(queue.start(jobs, "j-2"), "j-2", "编码器错误")
        jobs = queue.start(jobs, "j-3")

        assertEquals(jobs, queue.cancel(jobs, "j-1"))
        assertEquals(jobs, queue.cancel(jobs, "j-2"))
        assertEquals(listOf("j-1", "j-2", "j-4"), queue.cancel(jobs, "j-3").map { it.id })
        assertEquals(listOf("j-1", "j-2", "j-3"), queue.cancel(jobs, "j-4").map { it.id })
    }

    @Test
    fun `a job keeps its timeline, and queueing all timelines skips the empty ones`() {
        val queue = RenderQueueUseCase(com.example.prismstudio.p40b43a88.domain.SequentialIds())
        val job = queue.enqueue(emptyList(), RenderSettings(fileName = "cut"), "tl-v", "竖屏版").single()
        assertEquals("tl-v" to "竖屏版", job.timelineId to job.timelineName)
        val base = com.example.prismstudio.p40b43a88.domain.projectOf(
            timeline = com.example.prismstudio.p40b43a88.domain.timelineOf(com.example.prismstudio.p40b43a88.domain.clipOf("A", 0, 30)),
            name = "Trip",
        )
        val doc = base.copy(
            timelines = listOf(
                com.example.prismstudio.p40b43a88.domain.model.StoredTimeline("tl-b", "a/b: c", base.format, base.timeline, base.mixer),
                com.example.prismstudio.p40b43a88.domain.model.StoredTimeline(
                    "tl-e", "空", base.format, com.example.prismstudio.p40b43a88.domain.model.Timeline.EMPTY, base.mixer,
                ),
            ),
        )
        val all = queue.enqueueAll(doc)
        assertEquals(listOf("Trip_时间线_1.mp4", "Trip_a_b_c.mp4"), all.map { it.fileName })
        assertEquals(listOf("tl-1", "tl-b"), all.map { it.timelineId })
    }
}
