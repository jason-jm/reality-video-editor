package com.example.prismstudio.p40b43a88.ui.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph

/** Assembles the Audio stage from the app graph. */
@UnstableApi
class AudioViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val graph = AppGraph.from(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AudioViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AudioViewModel(
            graph.session, graph.program, graph.previews, graph.reverseCopies, graph.loudness, voice = graph.voice, ids = graph.ids,
            noiseProfiles = graph.noiseProfiles,
        ) as T
    }
}
