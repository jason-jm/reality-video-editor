package com.example.prismstudio.p40b43a88.ui.workspace.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.prismstudio.p40b43a88.ui.navigation.WorkspaceStage
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.windows.TabBar

/** `stage-tab-bar`: top-edge stage navigation ornament (icons; labels expand on hover). */
@Composable
fun StageTabBar(stage: WorkspaceStage, onSelect: (WorkspaceStage) -> Unit) {
    TabBar(focusable = false) {
        WorkspaceStage.entries.forEach { entry ->
            item(
                selected = entry == stage,
                onClick = { onSelect(entry) },
                mainContent = { Icon(painterResource(entry.icon), contentDescription = entry.label) },
                supportContent = { Text(entry.label) },
            )
        }
    }
}
