package com.example.prismstudio.p40b43a88

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.prismstudio.p40b43a88.ui.components.ProvideAppLanguage
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.example.prismstudio.p40b43a88.domain.model.WorkspaceLayout
import com.example.prismstudio.p40b43a88.ui.components.ProvideFocusRing
import com.example.prismstudio.p40b43a88.ui.components.trackFocusVisibility
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.studio.StudioScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

@androidx.annotation.OptIn(UnstableApi::class)
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        val layout = AppGraph.from(context).layout
        val languages = AppGraph.from(context).language
        DefaultWindowContainer {
            val ultrawide by layout.ultrawide.collectAsState()
            // The interface language: the one picked, else the system's; every text below shows in it.
            val chosen by languages.language.collectAsState()
            // Leaving the ultrawide layout, the window first takes its opening size (from the very first frame, or the
            // standard range would leave it 2400 dp wide), then the standard range again.
            var wasUltrawide by remember { mutableStateOf(ultrawide) }
            LaunchedEffect(ultrawide) {
                if (wasUltrawide && !ultrawide) delay(RESTORE_MS)
                wasUltrawide = ultrawide
            }
            val size = WorkspaceLayout.window(ultrawide, restoring = wasUltrawide && !ultrawide)
            ProvideAppLanguage(chosen) { PicoTheme { ProvideFocusRing {
                StudioScreen(
                    Modifier.trackFocusVisibility().windowConstraints(
                        minWidth = size.minWidth.dp,
                        minHeight = size.minHeight.dp,
                        maxWidth = size.maxWidth.dp,
                        maxHeight = size.maxHeight.dp,
                    ),
                )
            } } }
        }
    }

/** How long the window holds its opening size on the way back from the ultrawide layout. */
private const val RESTORE_MS = 1500L
