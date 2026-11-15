package com.example.prismstudio.p40b43a88.ui.studio

import com.example.prismstudio.p40b43a88.ui.navigation.AppDestination

/** Which top-level place is showing: the project library or the workspace of the open project. */
data class StudioUiState(
    val destination: AppDestination = AppDestination.LIBRARY,
    val projectId: String? = null,
)

sealed interface StudioEvent {
    /** Leave the workspace (flushes pending changes). */
    data object CloseProject : StudioEvent
}
