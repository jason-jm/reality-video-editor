package com.example.prismstudio.p40b43a88.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.ui.components.OpenSheets
import com.example.prismstudio.p40b43a88.ui.navigation.AppDestination
import com.example.prismstudio.p40b43a88.ui.projects.ProjectsScreen
import com.example.prismstudio.p40b43a88.ui.workspace.WorkspaceScreen

/**
 * Root of the window content. The window container owns the lifecycle, so the root owns the
 * ViewModel store; the workspace gets its own store per opened project.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun StudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val factory = remember(context) { StudioViewModelFactory(context) }
        val model: StudioViewModel = viewModel(factory = factory)
        val state by model.state.collectAsStateWithLifecycle()
        StudioContent(state, model::onEvent, modifier)
    }
}

@Composable
private fun StudioContent(state: StudioUiState, onEvent: (StudioEvent) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state.destination) {
            AppDestination.LIBRARY -> {
                // A Back pressed while a sheet is still opening ([OpenSheets]) would close the app: it waits for the sheet.
                if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled = OpenSheets.count > 0) {}
                ProjectsScreen()
            }
            AppDestination.WORKSPACE -> key(state.projectId) {
                // The system Back (the controller's back button) closed the whole app from a project: it goes back to the
                // library instead, saving the project as the title bar's back button does — unless a sheet is still
                // opening ([OpenSheets]): that Back was meant for the sheet.
                if (LocalOnBackPressedDispatcherOwner.current != null) {
                    BackHandler { if (OpenSheets.count == 0) onEvent(StudioEvent.CloseProject) }
                }
                WorkspaceScreen(onClose = { onEvent(StudioEvent.CloseProject) })
            }
        }
    }
}
