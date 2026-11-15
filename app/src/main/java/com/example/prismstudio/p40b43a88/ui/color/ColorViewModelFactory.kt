package com.example.prismstudio.p40b43a88.ui.color

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the Color stage from the app graph. */
@UnstableApi
class ColorViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ColorViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ColorViewModel(graph.session, graph.program, graph.previews, graph.luts, graph.documents, graph.downloads, graph.gallery, graph.ids, lumaFrames = graph.lumaFrames) as T
    }
}
