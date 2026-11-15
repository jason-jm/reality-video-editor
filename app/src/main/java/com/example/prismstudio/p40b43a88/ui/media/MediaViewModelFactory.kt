package com.example.prismstudio.p40b43a88.ui.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the Media stage from the app graph. */
@UnstableApi
class MediaViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MediaViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return MediaViewModel(graph.session, graph.deviceMedia, graph.previews, graph.source, graph.ids, graph.program, proxies = graph.proxies, envelopes = graph.envelopes) as T
    }
}
