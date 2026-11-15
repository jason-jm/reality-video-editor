package com.example.prismstudio.p40b43a88.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.ui.navigation.AppDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Routes between the library and the workspace from the session's open project. */
class StudioViewModel(private val session: EditorSessionRepository) : ViewModel() {

    val state: StateFlow<StudioUiState> = session.document
        .map { doc ->
            if (doc == null) StudioUiState(AppDestination.LIBRARY, null) else StudioUiState(AppDestination.WORKSPACE, doc.id)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioUiState())

    fun onEvent(event: StudioEvent) {
        when (event) {
            StudioEvent.CloseProject -> viewModelScope.launch { session.close() }
        }
    }
}
