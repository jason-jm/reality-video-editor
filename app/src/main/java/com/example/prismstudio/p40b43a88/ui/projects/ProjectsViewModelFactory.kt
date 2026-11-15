package com.example.prismstudio.p40b43a88.ui.projects

import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.example.prismstudio.p40b43a88.domain.usecase.CreateProjectUseCase

/** Assembles the project library from the app graph. */
@UnstableApi
class ProjectsViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)
    private val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" }
    }.getOrDefault("")

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProjectsViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ProjectsViewModel(
            projects = graph.projects,
            session = graph.session,
            previews = graph.previews,
            create = CreateProjectUseCase(graph.ids, graph.clock),
            backups = graph.backups,
            backupRules = ProjectBackupsUseCase(graph.ids),
            clock = graph.clock,
            languages = graph.language,
            languageChoices = graph.languageChoices,
            version = version,
        ) as T
    }
}
