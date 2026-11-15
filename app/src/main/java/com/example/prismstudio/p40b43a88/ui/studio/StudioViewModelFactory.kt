package com.example.prismstudio.p40b43a88.ui.studio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the root router from the app graph. */
@UnstableApi
class StudioViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StudioViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return StudioViewModel(graph.session) as T
    }
}
