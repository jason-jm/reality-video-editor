package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.SourceMarks
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlinx.coroutines.flow.StateFlow

data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
)

/** The steps the history holds, by label: what undo takes back, newest first, and what redo brings back, next first. */
data class HistoryLabels(
    val undo: List<String> = emptyList(),
    val redo: List<String> = emptyList(),
)

data class SaveState(
    val savedAtMillis: Long? = null,
    val pending: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The project that is open in the workspace, shared by every stage: its document, the undo / redo
 * history, autosave status and the clip selection shared by the Edit and Audio stages.
 */
interface EditorSessionRepository {
    val document: StateFlow<ProjectDocument?>
    val history: StateFlow<HistoryState>
    val saveState: StateFlow<SaveState>
    /** The selected clip the inspector shows (the first of [selectedClipIds]), or null. */
    val selectedClipId: StateFlow<String?>

    /**
     * Every selected clip, [selectedClipId] first; empty when nothing is selected. More than one is
     * selected with Ctrl / ⌘ held, in the timeline's 多选 mode or with Ctrl / ⌘+A.
     */
    val selectedClipIds: StateFlow<List<String>>

    /**
     * Resolve's source marks: each media's In and Out as the source viewer marked them (ms into the media), shared by the
     * Media page's source monitor and the Edit page's, and set by Match Frame. They belong to the session, not the
     * project or its history; a media without marks plays and edits whole.
     */
    val sourceMarks: StateFlow<Map<String, SourceMarks>>
        get() = NO_SOURCE_MARKS

    /** Marks [assetId] from [marks]' In to its Out; null clears them. */
    fun setSourceMarks(assetId: String, marks: SourceMarks?) = Unit

    /** Opens a stored project; false when it cannot be read. */
    suspend fun open(id: String): Boolean

    /** Saves a freshly created project and opens it. */
    suspend fun start(document: ProjectDocument)

    /** Returns false and keeps the workspace open if it still has unsaved edits. */
    suspend fun close(): Boolean

    /** Writes an edit not on disk yet (an autosave still due, or one that failed) now, the project staying open. */
    suspend fun flush() {}

    /** Include an in-progress narration in the next explicit save or project close. Autosaves leave it recording. */
    fun beforeNextFlush(finish: () -> Unit)

    fun removeBeforeFlush(finish: () -> Unit)

    /**
     * Applies an undoable edit. Consecutive edits with the same [coalesceKey] made within a short
     * window (a slider drag, a wheel drag) collapse into one history step.
     */
    fun edit(label: String, coalesceKey: String? = null, transform: (ProjectDocument) -> ProjectDocument)

    /** Applies a persisted change that is not part of the edit history (render queue, settings). */
    fun update(transform: (ProjectDocument) -> ProjectDocument)

    /** Reverts the last edit and returns its label, or null when there is nothing to undo. */
    fun undo(): String?

    fun redo(): String?

    /** Resolve's History window: undoes [steps] steps at once (one change), returning their labels, newest first. */
    fun undo(steps: Int): List<String>

    /** Redoes [steps] steps at once, returning their labels in the order they come back. */
    fun redo(steps: Int): List<String>

    /** The labels of every step the history can take back or bring back. */
    val historyLabels: StateFlow<HistoryLabels>

    /** Selects [clipId] alone (null: nothing). */
    fun select(clipId: String?)

    /** Adds [clipId] to the selection, where it becomes the one the inspector shows, or takes it out. */
    fun toggleSelected(clipId: String)

    /** Selects [clipIds] (those on the timeline); the first is the one the inspector shows. */
    fun selectAll(clipIds: List<String>)
}

/** The source marks a session without any reads. */
private val NO_SOURCE_MARKS: StateFlow<Map<String, SourceMarks>> = kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
