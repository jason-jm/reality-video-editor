package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_ID
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks

/** State transitions of the Deliver render queue. Jobs render one at a time, in queue order. */
class RenderQueueUseCase(private val ids: IdGenerator) {

    /**
     * Adds a queued job rendering the timeline [timelineId] ([timelineName] for the queue); a file name already used in
     * the queue gets a numeric suffix.
     */
    fun enqueue(
        jobs: List<RenderJob>,
        settings: RenderSettings,
        timelineId: String = FIRST_TIMELINE_ID,
        timelineName: String = FIRST_TIMELINE_NAME,
        clipId: String? = null,
        queuedRange: TimelineMarks? = null,
    ): List<RenderJob> {
        val base = settings.fileName.trim().ifEmpty { PRODUCT_NAME }
        val taken = jobs.map { it.fileName }.toSet()
        val extension = ".${settings.extension}"
        var name = "$base$extension"
        var n = 2
        while (name in taken) name = "$base ($n)$extension".also { n++ }
        return jobs + RenderJob(
            id = ids.next("j"),
            fileName = name,
            settings = settings.copy(fileName = name.removeSuffix(extension)),
            timelineId = timelineId,
            timelineName = timelineName,
            clipId = clipId,
            queuedRange = queuedRange,
        )
    }

    /** The settings and marked range belonging to the open timeline at queue time. */
    fun enqueue(doc: ProjectDocument): List<RenderJob> = enqueue(
        doc.jobs, doc.render, doc.timelineId, doc.timelineName,
        queuedRange = if (doc.render.range == RenderRange.IN_TO_OUT) doc.render.range.frameLimits(doc.timeline) else null,
    )

    /**
     * Resolve's Individual clips: one queued job per clip [individual] picks on [doc]'s open timeline, named
     * `<name>_<clip>` (a numeric suffix where names meet), each rendering that clip alone.
     */
    fun enqueueIndividual(doc: ProjectDocument, individual: IndividualClipsUseCase = IndividualClipsUseCase()): List<RenderJob> {
        val base = doc.render.fileName.trim().ifEmpty { PRODUCT_NAME }
        return individual.clips(doc, doc.render).fold(doc.jobs) { jobs, clip ->
            enqueue(jobs, doc.render.copy(fileName = "${base}_${safe(clip.name)}"), doc.timelineId, doc.timelineName, clip.id)
        }
    }

    /**
     * One job per timeline of [doc] that has something on it, each with the project's settings and the file name
     * `<name>_<timeline>` — a cut and its vertical version in one go. Empty timelines are left out.
     */
    fun enqueueAll(doc: ProjectDocument): List<RenderJob> {
        val base = doc.render.fileName.trim().ifEmpty { PRODUCT_NAME }
        return doc.timelineEntries.filter { it.durationFrames > 0 && !it.compound }.fold(doc.jobs) { jobs, entry ->
            val timeline = requireNotNull(doc.storedTimeline(entry.id)).timeline
            enqueue(
                jobs, doc.render.copy(fileName = "${base}_${safe(entry.name)}"), entry.id, entry.name,
                queuedRange = if (doc.render.range == RenderRange.IN_TO_OUT) doc.render.range.frameLimits(timeline) else null,
            )
        }
    }

    /** [name] fit for a file name: no path separators or characters galleries refuse. */
    private fun safe(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")

    fun next(jobs: List<RenderJob>): RenderJob? = jobs.firstOrNull { it.status == RenderStatus.QUEUED }

    fun start(jobs: List<RenderJob>, id: String) =
        jobs.update(id) { it.copy(status = RenderStatus.RENDERING, progress = 0f, errorMessage = null) }

    fun progress(jobs: List<RenderJob>, id: String, progress: Float) =
        jobs.update(id) { if (it.status == RenderStatus.RENDERING) it.copy(progress = progress.coerceIn(0f, 1f)) else it }

    fun complete(jobs: List<RenderJob>, id: String, uri: String, bytes: Long, warning: String? = null) =
        jobs.update(id) {
            it.copy(
                status = if (warning == null) RenderStatus.DONE else RenderStatus.PARTIAL,
                progress = 1f,
                outputUri = uri,
                outputBytes = bytes,
                errorMessage = warning,
            )
        }

    fun fail(jobs: List<RenderJob>, id: String, message: String) =
        jobs.update(id) { it.copy(status = RenderStatus.FAILED, errorMessage = "失败：$message") }

    /** Cancelling removes a queued or running job from the queue. */
    fun cancel(jobs: List<RenderJob>, id: String): List<RenderJob> = jobs.filterNot { it.id == id && it.cancellable }

    /** The queue without its finished renders (their files stay where they were saved). */
    fun clearDone(jobs: List<RenderJob>): List<RenderJob> = jobs.filterNot { it.status.mediaSaved }

    fun retry(jobs: List<RenderJob>, id: String): List<RenderJob> {
        val job = jobs.firstOrNull { it.id == id } ?: return jobs
        if (job.status == RenderStatus.FAILED) return jobs.update(id) { it.copy(status = RenderStatus.QUEUED, progress = 0f, errorMessage = null) }
        if (job.status != RenderStatus.PARTIAL || jobs.any { it.retryOf == id && it.status != RenderStatus.FAILED }) return jobs
        val queued = enqueue(jobs, job.settings, job.timelineId, job.timelineName, job.clipId, job.queuedRange).last()
        return jobs + queued.copy(retryOf = id)
    }

    /** A render interrupted by the app (not by the user) goes back to the queue from the start. */
    fun requeue(jobs: List<RenderJob>, id: String) = jobs.update(id) {
        if (it.status == RenderStatus.RENDERING) it.copy(status = RenderStatus.QUEUED, progress = 0f, errorMessage = null) else it
    }

    private fun List<RenderJob>.update(id: String, change: (RenderJob) -> RenderJob) = map { if (it.id == id) change(it) else it }

    companion object {
        /**
         * Every render the project says is running, back in the queue from the start (release round R14): renders only
         * run while a project is open, so a project being opened has none — a job still marked running was cut off when
         * the app was killed mid-render, and sat at its last percentage for good.
         */
        fun requeueInterrupted(jobs: List<RenderJob>): List<RenderJob> =
            jobs.map { if (it.status == RenderStatus.RENDERING) it.copy(status = RenderStatus.QUEUED, progress = 0f, errorMessage = null) else it }
    }
}
