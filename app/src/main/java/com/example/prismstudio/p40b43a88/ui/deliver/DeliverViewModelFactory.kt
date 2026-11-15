package com.example.prismstudio.p40b43a88.ui.deliver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the Deliver stage from the app graph. */
@UnstableApi
class DeliverViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DeliverViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return DeliverViewModel(graph.session, graph.program, graph.render, graph.ids, graph.loudness) as T
    }
}
