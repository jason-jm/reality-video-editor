package com.example.prismstudio.p40b43a88.ui.projects

import com.example.prismstudio.p40b43a88.domain.model.OssLicense
import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat

data class ProjectCardUi(
    val id: String,
    val name: String,
    val meta: String,
    val modified: String,
    val poster: PosterColors,
    val frame: Bitmap?,
)

data class ProjectDetailUi(
    val id: String,
    val name: String,
    val rows: List<Pair<String, String>>,
    val poster: PosterColors,
    val frame: Bitmap?,
)

data class NewProjectUi(
    val visible: Boolean = false,
    val name: String = "",
    val formatIndex: Int = 0,
    val fpsIndex: Int = 2,
) {
    val formats: List<FormatPreset> get() = FormatPreset.entries
    val frameRates: List<Int> get() = ProjectFormat.FRAME_RATES
}

/** `backup-row`: one backup — when, and what the project held then. */
data class BackupRowUi(val takenAtMillis: Long, val title: String, val detail: String)

/** `project-backups-sheet`: the selected project's backups (Resolve's Project Backups), newest first. */
data class BackupsUi(
    val projectId: String,
    val projectName: String,
    val rows: List<BackupRowUi> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val recoveryOnly: Boolean = false,
)

data class ProjectsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val query: String = "",
    val items: List<ProjectCardUi> = emptyList(),
    val unreadableIds: List<String> = emptyList(),
    val totalCount: Int = 0,
    val summary: String = "",
    val selectedId: String? = null,
    val selected: ProjectDetailUi? = null,
    val newProject: NewProjectUi = NewProjectUi(),
    val deleteTarget: ProjectDetailUi? = null,
    val backups: BackupsUi? = null,
    val busy: Boolean = false,
    val toast: String? = null,
    /** `language-button`: the interface language picked (null = the system's) and the languages offered. */
    val language: AppLanguage? = null,
    val languages: List<AppLanguage> = AppLanguage.selectable,
    /** `about-sheet`: open (non-null) with the version and the open-source notices. */
    val about: AboutUi? = null,
) {
    /** No project exists at all (the empty library state). */
    val isEmpty: Boolean get() = !loading && !loadFailed && totalCount == 0
    val hasSelection: Boolean get() = selected != null
}

/** One project's libraries on the About sheet: `about-family-row`. */
data class OssFamilyUi(val name: String, val licenseLabel: String, val count: Int, val components: String)

/**
 * `about-sheet`: the app's version and the open-source software it ships, gathered by project; [licenseText] (with its
 * [licenseTitle]) when a license's full text is shown instead of the list.
 */
data class AboutUi(
    val version: String,
    val families: List<OssFamilyUi>,
    val componentCount: Int,
    val licenses: List<OssLicense>,
    val licenseTitle: String? = null,
    val licenseText: String? = null,
) {
    val showingText: Boolean get() = licenseText != null
}

sealed interface ProjectsEvent {
    data object RetryLoad : ProjectsEvent
    /** `about-button` / `about-close-button`: the About sheet. */
    data object OpenAbout : ProjectsEvent
    data object CloseAbout : ProjectsEvent
    /** `about-license-button` / `about-back-button`: a license's full text, or back to the list (null). */
    data class ShowLicense(val license: OssLicense?) : ProjectsEvent
    /** `language-button`: the interface language (null = the system's). */
    data class SetLanguage(val language: AppLanguage?) : ProjectsEvent
    data class Query(val text: String) : ProjectsEvent
    data class Select(val id: String) : ProjectsEvent
    data object StartNew : ProjectsEvent
    data object CancelNew : ProjectsEvent
    data class NewName(val text: String) : ProjectsEvent
    data class NewFormat(val index: Int) : ProjectsEvent
    data class NewFps(val index: Int) : ProjectsEvent
    data object Create : ProjectsEvent
    data object OpenSelected : ProjectsEvent
    data object Duplicate : ProjectsEvent
    data object RequestDelete : ProjectsEvent
    data object ConfirmDelete : ProjectsEvent
    data object DismissDelete : ProjectsEvent
    data object ToastShown : ProjectsEvent
    /** Project backups: the sheet, a backup now, a backup restored as a new project. */
    data object OpenBackups : ProjectsEvent
    data class OpenRecovery(val projectId: String) : ProjectsEvent
    data object RetryBackups : ProjectsEvent
    data object CloseBackups : ProjectsEvent
    data object BackupNow : ProjectsEvent
    data class RestoreBackup(val takenAtMillis: Long) : ProjectsEvent
}
