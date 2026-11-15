package com.example.prismstudio.p40b43a88.ui.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the Edit stage from the app graph. */
@UnstableApi
class EditViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EditViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return EditViewModel(graph.session, graph.program, graph.source, graph.previews, graph.ids, graph.reverseCopies, graph.documents, graph.stills, graph.downloads, graph.generators, graph.stabilization, envelopes = graph.envelopes, lumaFrames = graph.lumaFrames) as T
    }
}
