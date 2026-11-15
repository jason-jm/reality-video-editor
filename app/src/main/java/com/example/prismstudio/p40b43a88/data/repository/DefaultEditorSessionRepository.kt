package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RenderQueueUseCase
import com.example.prismstudio.p40b43a88.domain.model.SourceMarks
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** In-memory history over immutable documents with debounced autosave to [projects]. */
class DefaultEditorSessionRepository(
    private val projects: ProjectRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val autosaveDelayMs: Long = 700,
    private val coalesceWindowMs: Long = 1_500,
    /** Resolve's Project Backups: after an autosave, a backup when the last one is old enough ([ProjectBackupsUseCase]). */
    private val backups: ProjectBackupRepository = ProjectBackupRepository.None,
    private val backupPolicy: ProjectBackupsUseCase = ProjectBackupsUseCase({ it }),
) : EditorSessionRepository {

    private data class Entry(val document: ProjectDocument, val label: String)

    private val _document = MutableStateFlow<ProjectDocument?>(null)
    private val _history = MutableStateFlow(HistoryState())
    private val _labels = MutableStateFlow(HistoryLabels())
    private val _saveState = MutableStateFlow(SaveState())
    private val _selected = MutableStateFlow<String?>(null)
    private val _selection = MutableStateFlow<List<String>>(emptyList())
    override val document: StateFlow<ProjectDocument?> = _document.asStateFlow()
    override val history: StateFlow<HistoryState> = _history.asStateFlow()
    override val historyLabels: StateFlow<HistoryLabels> = _labels.asStateFlow()
    override val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    override val selectedClipId: StateFlow<String?> = _selected.asStateFlow()
    override val selectedClipIds: StateFlow<List<String>> = _selection.asStateFlow()
    private val _sourceMarks = MutableStateFlow<Map<String, SourceMarks>>(emptyMap())
    override val sourceMarks: StateFlow<Map<String, SourceMarks>> = _sourceMarks.asStateFlow()

    override fun setSourceMarks(assetId: String, marks: SourceMarks?) {
        _sourceMarks.value = if (marks == null) _sourceMarks.value - assetId else _sourceMarks.value + (assetId to marks)
    }

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()
    private var lastKey: String? = null
    private var lastEditAt = 0L
    private var saveJob: Job? = null
    private val beforeFlush = mutableSetOf<() -> Unit>()
    private val lifecycle = Mutex()
    /** When the open project was last backed up (read from its backups the first time it is needed). */
    private var lastBackupAt: Long? = null
    private var lastBackupKnown = false

    override suspend fun open(id: String): Boolean = lifecycle.withLock {
        if (!closeCurrent()) return@withLock false
        val loaded = projects.load(id) ?: return@withLock false
        // A render cut off by the app being killed is queued again (no render runs before a project is open).
        val jobs = RenderQueueUseCase.requeueInterrupted(loaded.jobs)
        begin(if (jobs == loaded.jobs) loaded else loaded.copy(jobs = jobs))
        true
    }

    override suspend fun start(document: ProjectDocument) = lifecycle.withLock {
        if (!closeCurrent()) throw IOException("The current project has unsaved edits")
        projects.save(document)
        begin(document)
    }

    private fun begin(doc: ProjectDocument) {
        _sourceMarks.value = emptyMap()
        undoStack.clear()
        redoStack.clear()
        lastKey = null
        setSelection(emptyList())
        _document.value = doc
        lastBackupKnown = false
        _saveState.value = SaveState(savedAtMillis = doc.modifiedAtMillis)
        publishHistory()
    }

    override suspend fun flush() = lifecycle.withLock { flushCurrent() }

    override fun beforeNextFlush(finish: () -> Unit) { beforeFlush.add(finish) }

    override fun removeBeforeFlush(finish: () -> Unit) { beforeFlush.remove(finish) }

    private suspend fun flushCurrent() {
        val pending = beforeFlush.toList()
        beforeFlush.clear()
        pending.forEach { it() }
        val scheduled = saveJob
        saveJob = null
        scheduled?.cancelAndJoin()
        if (_saveState.value.let { it.pending || it.failed }) persist()
    }

    override suspend fun close(): Boolean = lifecycle.withLock { closeCurrent() }

    private suspend fun closeCurrent(): Boolean {
        if (_document.value == null) return true
        flushCurrent()
        // An edit may have arrived while the write was suspended. Its autosave and undo history stay live.
        if (_saveState.value.let { it.pending || it.failed }) return false
        _document.value = null
        setSelection(emptyList())
        undoStack.clear()
        redoStack.clear()
        publishHistory()
        return true
    }

    override fun edit(label: String, coalesceKey: String?, transform: (ProjectDocument) -> ProjectDocument) {
        val current = _document.value ?: return
        val next = transform(current)
        if (next == current) return
        val now = clock()
        val coalesce = coalesceKey != null && coalesceKey == lastKey &&
            now - lastEditAt <= coalesceWindowMs && undoStack.isNotEmpty()
        if (!coalesce) {
            undoStack.addLast(Entry(current, label))
            val depth = historyDepth(current.timeline.clips.size)
            while (undoStack.size > depth) undoStack.removeFirst()
        }
        redoStack.clear()
        lastKey = coalesceKey
        lastEditAt = now
        commit(next.copy(modifiedAtMillis = now))
    }

    override fun update(transform: (ProjectDocument) -> ProjectDocument) {
        val current = _document.value ?: return
        val next = transform(current)
        if (next != current) commit(next)
    }

    override fun undo(): String? = undo(1).firstOrNull()

    override fun redo(): String? = redo(1).firstOrNull()

    override fun undo(steps: Int): List<String> = travel(steps, from = undoStack, to = redoStack)

    override fun redo(steps: Int): List<String> = travel(steps, from = redoStack, to = undoStack)

    /** Moves up to [steps] steps from one stack to the other and shows where that lands, as one change. */
    private fun travel(steps: Int, from: ArrayDeque<Entry>, to: ArrayDeque<Entry>): List<String> {
        val current = _document.value ?: return emptyList()
        if (steps <= 0 || from.isEmpty()) return emptyList()
        var state = current
        val labels = mutableListOf<String>()
        repeat(minOf(steps, from.size)) {
            val entry = from.removeLast()
            to.addLast(Entry(state, entry.label))
            state = entry.document
            labels += entry.label
        }
        lastKey = null
        commit(restore(state, current))
        return labels
    }

    /**
     * History covers the edit only; the render queue, delivery settings, the edit destinations and the
     * timeline In / Out marks stay as they are now (a destination on a track the restored timeline lacks
     * falls back to its default).
     */
    private fun restore(target: ProjectDocument, current: ProjectDocument) = target.copy(
        timeline = target.timeline.copy(targets = current.timeline.targets, marks = current.timeline.marks),
        jobs = current.jobs,
        render = current.render,
        modifiedAtMillis = clock(),
    )

    override fun select(clipId: String?) {
        setSelection(listOfNotNull(clipId).filter(::onTimeline))
    }

    override fun toggleSelected(clipId: String) {
        val current = _selection.value
        setSelection(if (clipId in current) current - clipId else listOf(clipId).filter(::onTimeline) + current)
    }

    override fun selectAll(clipIds: List<String>) {
        setSelection(clipIds.distinct().filter(::onTimeline))
    }

    private fun onTimeline(id: String) = _document.value?.timeline?.clip(id) != null

    /** The selection and the clip the inspector shows (its first) change together; the list first. */
    private fun setSelection(ids: List<String>) {
        _selection.value = ids
        _selected.value = ids.firstOrNull()
    }

    private fun commit(doc: ProjectDocument) {
        _document.value = doc
        // Clips an edit removed leave the selection.
        val kept = _selection.value.filter { doc.timeline.clip(it) != null }
        if (kept != _selection.value) setSelection(kept)
        publishHistory()
        scheduleSave()
    }

    private fun publishHistory() {
        _history.value = HistoryState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoLabel = undoStack.lastOrNull()?.label,
            redoLabel = redoStack.lastOrNull()?.label,
        )
        _labels.value = HistoryLabels(undoStack.reversed().map { it.label }, redoStack.reversed().map { it.label })
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        _saveState.update { it.copy(pending = true) }
        saveJob = scope.launch {
            delay(autosaveDelayMs)
            persist()
        }
    }

    private suspend fun persist() {
        val doc = _document.value ?: return
        try {
            projects.save(doc)
        } catch (e: CancellationException) {
            // Superseded by a newer edit or by close(): whoever cancelled saves the newer document.
            throw e
        } catch (e: Exception) {
            _saveState.value = _saveState.value.copy(pending = false, failed = true)
            return
        }
        _saveState.value = if (_document.value === doc) {
            SaveState(savedAtMillis = clock(), pending = false, failed = false)
        } else {
            // A newer edit arrived while writing; its own autosave is still due.
            _saveState.value.copy(savedAtMillis = clock(), failed = false)
        }
        backUpIfDue(doc)
    }

    /** A backup of what was just saved when the last one is old enough; the older ones thinned out. Never fails the save. */
    private suspend fun backUpIfDue(doc: ProjectDocument) {
        try {
            if (!lastBackupKnown) {
                lastBackupAt = backups.list(doc.id).maxOfOrNull { it.takenAtMillis }
                lastBackupKnown = true
            }
            val now = clock()
            if (!backupPolicy.due(lastBackupAt, now)) return
            backups.write(doc, now)
            lastBackupAt = now
            backups.prune(doc.id, backupPolicy.keep(backups.list(doc.id).map { it.takenAtMillis }, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A backup that cannot be written is tried again after the next autosave.
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 200
        private const val MIN_HISTORY = 20
        private const val HISTORY_CLIP_BUDGET = 200_000

        /**
         * Steps the history keeps: [HISTORY_LIMIT], fewer on a very long timeline, where a step can hold a copy of every
         * clip (an insert moves all the clips after it): 200 steps of a 1320-clip timeline took 63 MB of a 192 MB heap (R20).
         */
        internal fun historyDepth(clips: Int): Int = (HISTORY_CLIP_BUDGET / clips.coerceAtLeast(1)).coerceIn(MIN_HISTORY, HISTORY_LIMIT)
    }
}
