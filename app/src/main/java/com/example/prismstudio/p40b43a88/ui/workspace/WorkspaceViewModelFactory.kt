package com.example.prismstudio.p40b43a88.ui.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the workspace shell from the app graph. */
@UnstableApi
class WorkspaceViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkspaceViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WorkspaceViewModel(graph.session, graph.program, graph.source, graph.deviceMedia, graph.reverseCopies, proxies = graph.proxies, layout = graph.layout, languages = graph.language, languageChoices = graph.languageChoices) as T
    }
}
