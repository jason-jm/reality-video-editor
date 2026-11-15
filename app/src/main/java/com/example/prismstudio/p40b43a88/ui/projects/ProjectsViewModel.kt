package com.example.prismstudio.p40b43a88.ui.projects

import com.example.prismstudio.p40b43a88.domain.usecase.OpenSourceNoticesUseCase
import com.example.prismstudio.p40b43a88.data.repository.LicenseNoticesRepository
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import com.example.prismstudio.p40b43a88.ui.components.languageMessage
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.data.repository.LanguageRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectBackupRepository
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectRepository
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.ProjectSummary
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.attempt
import com.example.prismstudio.p40b43a88.domain.usecase.CreateProjectUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FilterProjectsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProjectLabelsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Project library: browse, search, create, open, duplicate and delete projects. */
class ProjectsViewModel(
    private val projects: ProjectRepository,
    private val session: EditorSessionRepository,
    private val previews: MediaPreviewRepository,
    private val create: CreateProjectUseCase,
    private val backups: ProjectBackupRepository = ProjectBackupRepository.None,
    private val backupRules: ProjectBackupsUseCase = ProjectBackupsUseCase({ it }),
    private val filter: FilterProjectsUseCase = FilterProjectsUseCase(),
    private val labels: ProjectLabelsUseCase = ProjectLabelsUseCase(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val languages: LanguageRepository = LanguageRepository.InMemory(),
    private val languageChoices: List<AppLanguage> = AppLanguage.selectable,
    private val notices: LicenseNoticesRepository = LicenseNoticesRepository.Bundled,
    private val noticesRules: OpenSourceNoticesUseCase = OpenSourceNoticesUseCase(),
    /** The installed version as the About sheet shows it ("1.0.0 (10000)"). */
    private val version: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    private val frames = mutableMapOf<String, Bitmap>()
    private val requested = mutableSetOf<String>()
    private var backupListJob: Job? = null

    init {
        viewModelScope.launch {
            languages.language.collect { chosen -> _state.update { it.copy(language = chosen, languages = languageChoices) } }
        }
        viewModelScope.launch {
            projects.library.collect { publish() }
        }
        refreshProjects()
    }

    private fun refreshProjects() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val failed = attempt { projects.refresh() }.isFailure
            publish()
            _state.update { it.copy(loading = false, loadFailed = failed) }
        }
    }

    /** The About sheet: the version, and the bundled notices gathered by project (licenses with a text to show). */
    private fun about(): AboutUi {
        val components = noticesRules.parse(notices.libraries())
        val families = noticesRules.families(components)
        return AboutUi(
            version = version,
            families = families.map { f ->
                OssFamilyUi(f.name, f.license.label, f.components.size, f.components.joinToString(" · ") { "${it.artifact} ${it.version}" })
            },
            componentCount = components.size,
            licenses = families.map { it.license }.distinct().filter { it.hasText },
        )
    }

    fun onEvent(event: ProjectsEvent) {
        when (event) {
            ProjectsEvent.RetryLoad -> if (!_state.value.loading && (_state.value.loadFailed || projects.library.value.unreadableIds.isNotEmpty())) refreshProjects()
            ProjectsEvent.OpenAbout -> _state.update { it.copy(about = about()) }
            ProjectsEvent.CloseAbout -> _state.update { it.copy(about = null) }
            is ProjectsEvent.ShowLicense -> _state.update { s ->
                val about = s.about ?: return@update s
                val license = event.license
                s.copy(about = about.copy(licenseTitle = license?.label, licenseText = license?.let { notices.text(it) }))
            }
            is ProjectsEvent.Query -> {
                _state.update { it.copy(query = event.text) }
                publish()
            }
            is ProjectsEvent.Select -> {
                _state.update { it.copy(selectedId = event.id) }
                publish()
            }
            ProjectsEvent.StartNew -> _state.update { it.copy(newProject = NewProjectUi(visible = true)) }
            ProjectsEvent.CancelNew -> _state.update { it.copy(newProject = it.newProject.copy(visible = false)) }
            is ProjectsEvent.NewName -> _state.update { it.copy(newProject = it.newProject.copy(name = event.text.take(MAX_NAME))) }
            is ProjectsEvent.NewFormat -> _state.update {
                it.copy(newProject = it.newProject.copy(formatIndex = event.index.coerceIn(0, FormatPreset.entries.lastIndex)))
            }
            is ProjectsEvent.NewFps -> _state.update {
                it.copy(newProject = it.newProject.copy(fpsIndex = event.index.coerceIn(0, ProjectFormat.FRAME_RATES.lastIndex)))
            }
            ProjectsEvent.Create -> createProject()
            ProjectsEvent.OpenSelected -> openSelected()
            ProjectsEvent.Duplicate -> duplicate()
            ProjectsEvent.OpenBackups -> _state.value.selected?.let { openBackups(BackupsUi(it.id, it.name)) }
            is ProjectsEvent.OpenRecovery -> if (event.projectId in _state.value.unreadableIds) {
                openBackups(BackupsUi(event.projectId, "${event.projectId}.json", recoveryOnly = true))
            }
            ProjectsEvent.RetryBackups -> _state.value.backups?.takeIf { it.loadFailed }?.let { loadBackups(it.projectId) }
            ProjectsEvent.CloseBackups -> {
                backupListJob?.cancel()
                _state.update { it.copy(backups = null) }
            }
            ProjectsEvent.BackupNow -> backupNow()
            is ProjectsEvent.RestoreBackup -> restore(event.takenAtMillis)
            ProjectsEvent.RequestDelete -> _state.update { if (it.busy || it.deleteTarget != null) it else it.copy(deleteTarget = it.selected) }
            ProjectsEvent.DismissDelete -> _state.update { it.copy(deleteTarget = null) }
            ProjectsEvent.ConfirmDelete -> delete()
            ProjectsEvent.ToastShown -> _state.update { it.copy(toast = null) }
            is ProjectsEvent.SetLanguage -> if (event.language != languages.language.value) {
                languages.setLanguage(event.language)
                _state.update { it.copy(toast = languageMessage(event.language)) }
            }
        }
    }

    private fun createProject() {
        val form = _state.value.newProject
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val doc = create(form.name, FormatPreset.entries[form.formatIndex], ProjectFormat.FRAME_RATES[form.fpsIndex])
            attempt { session.start(doc) }
                .onSuccess { _state.update { it.copy(busy = false, newProject = NewProjectUi()) } }
                .onFailure { _state.update { it.copy(busy = false, toast = "无法创建项目，请检查存储空间后重试") } }
        }
    }

    private fun openSelected() {
        val id = _state.value.selectedId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val opened = attempt { session.open(id) }.getOrDefault(false)
            _state.update { it.copy(busy = false, toast = if (opened) null else "无法打开项目：文件已损坏或不存在") }
        }
    }

    private fun duplicate() {
        val id = _state.value.selectedId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val source = attempt { projects.load(id) }.getOrNull()
            if (source == null) {
                _state.update { it.copy(busy = false, toast = "无法读取项目，副本未创建") }
                return@launch
            }
            val copy = create.duplicate(source)
            if (attempt { projects.save(copy) }.isFailure) {
                _state.update { it.copy(busy = false, toast = "无法创建副本，请检查存储空间后重试") }
                return@launch
            }
            _state.update { state ->
                state.copy(
                    busy = false,
                    selectedId = if (state.selectedId == id) copy.id else state.selectedId,
                    toast = "已创建副本：${copy.name}",
                )
            }
            publish()
        }
    }

    private fun openBackups(sheet: BackupsUi) {
        if (_state.value.busy) return
        _state.update { it.copy(backups = sheet) }
        loadBackups(sheet.projectId)
    }

    private fun loadBackups(projectId: String) {
        val sheet = _state.value.backups?.takeIf { it.projectId == projectId } ?: return
        backupListJob?.cancel()
        _state.update { it.copy(backups = sheet.copy(loading = true, loadFailed = false)) }
        backupListJob = viewModelScope.launch {
            val list = attempt { backups.list(projectId) }
            val now = clock()
            _state.update { s ->
                if (s.backups?.projectId != projectId) s
                else s.copy(backups = s.backups.copy(
                    rows = list.getOrNull()?.map { BackupRowUi(it.takenAtMillis, backupRules.title(it), backupRules.detail(it, now)) } ?: s.backups.rows,
                    loading = false,
                    loadFailed = list.isFailure,
                ))
            }
        }
    }

    /** A backup of the stored project now, whatever the schedule. */
    private fun backupNow() {
        val state = _state.value
        val sheet = state.backups ?: return
        if (state.busy || sheet.loading || sheet.loadFailed || sheet.recoveryOnly) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val doc = attempt { projects.load(sheet.projectId) }.getOrNull()
            if (doc == null) {
                _state.update { it.copy(busy = false, toast = "无法读取项目，未备份") }
                return@launch
            }
            val now = clock()
            val ok = attempt { backups.write(doc, now) }.isSuccess
            if (ok) attempt { backups.prune(doc.id, backupRules.keep(backups.list(doc.id).map { it.takenAtMillis }, now)) }
            loadBackups(doc.id)
            _state.update { it.copy(busy = false, toast = if (ok) "已备份“${doc.name}”" else "备份失败：存储空间不足或不可写") }
        }
    }

    /** The backup as a new project beside the original (which stays as it is), picked in the library. */
    private fun restore(takenAt: Long) {
        val state = _state.value
        val sheet = state.backups ?: return
        if (state.busy || sheet.loading || sheet.loadFailed) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val doc = attempt { backups.read(sheet.projectId, takenAt) }.getOrNull()
            if (doc == null) {
                _state.update { it.copy(busy = false, toast = "这个备份已无法读取") }
                return@launch
            }
            val copy = backupRules.restore(doc, takenAt, clock())
            if (attempt { projects.save(copy) }.isFailure) {
                _state.update { it.copy(busy = false, toast = "无法恢复备份，请检查存储空间后重试") }
                return@launch
            }
            _state.update { current ->
                val stillViewingBackup = current.backups?.projectId == sheet.projectId
                val showCopy = stillViewingBackup && (sheet.recoveryOnly || current.selectedId == sheet.projectId)
                current.copy(
                    busy = false,
                    backups = if (stillViewingBackup) null else current.backups,
                    query = if (showCopy && sheet.recoveryOnly) "" else current.query,
                    selectedId = if (showCopy) copy.id else current.selectedId,
                    toast = "已恢复为新项目：${copy.name}",
                )
            }
            publish()
        }
    }

    private fun delete() {
        val target = _state.value.deleteTarget ?: return
        if (_state.value.busy) return
        _state.update { it.copy(deleteTarget = null, busy = true) }
        viewModelScope.launch {
            if (attempt { projects.delete(target.id) }.isFailure) {
                _state.update { it.copy(busy = false, toast = "无法删除“${target.name}”，项目和备份均已保留") }
                return@launch
            }
            val removedBackups = attempt { backups.deleteAll(target.id) }.isSuccess
            frames.remove(target.id)
            _state.update { it.copy(
                busy = false,
                toast = if (removedBackups) "已删除“${target.name}”" else "已删除“${target.name}”，部分备份未能清理",
            ) }
            publish()
        }
    }

    private fun publish() {
        // Read the repository's current list, not a cached copy: a save may emit before its collector runs.
        val library = projects.library.value
        val now = clock()
        val query = _state.value.query
        val visible = filter(library.summaries, query)
        val unreadable = library.unreadableIds.filter { "$it.json".contains(query.trim(), ignoreCase = true) }
        val total = library.summaries.size + library.unreadableIds.size
        val selectedId = _state.value.selectedId?.takeIf { id -> visible.any { it.id == id } } ?: visible.firstOrNull()?.id
        val selected = visible.firstOrNull { it.id == selectedId }
        _state.update { s ->
            s.copy(
                items = visible.map { summary ->
                    ProjectCardUi(summary.id, summary.name, labels.meta(summary), labels.modified(summary.modifiedAtMillis, now), summary.poster, frames[summary.id])
                },
                unreadableIds = unreadable,
                totalCount = total,
                summary = labels.count(total, visible.size + unreadable.size, query),
                selectedId = selectedId,
                selected = selected?.let { detail(it, now) },
            )
        }
        visible.forEach(::requestFrame)
    }

    private fun detail(summary: ProjectSummary, now: Long) = ProjectDetailUi(
        id = summary.id,
        name = summary.name,
        rows = listOf(
            "时间线格式" to labels.format(summary.format),
            "帧率" to "${summary.format.fps} fps",
            "时长" to Timecode.format(summary.durationFrames, summary.format.fps),
            "片段" to "${summary.clipCount} 个片段",
            "修改时间" to labels.modified(summary.modifiedAtMillis, now),
        ),
        poster = summary.poster,
        frame = frames[summary.id],
    )

    private fun requestFrame(summary: ProjectSummary) {
        val source = summary.posterSource ?: return
        if (!requested.add("${summary.id}@${source.uri}@${source.timeMs}")) return
        viewModelScope.launch {
            val bitmap = attempt { previews.thumbnail(source.uri, source.kind, source.timeMs, POSTER_WIDTH) }.getOrNull()
            if (bitmap != null) {
                frames[summary.id] = bitmap
                publish()
            }
        }
    }

    private companion object {
        const val MAX_NAME = 40
        const val POSTER_WIDTH = 640
    }
}
