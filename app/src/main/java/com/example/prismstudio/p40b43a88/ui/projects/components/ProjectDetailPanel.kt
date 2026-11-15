package com.example.prismstudio.p40b43a88.ui.projects.components

import com.example.prismstudio.p40b43a88.ui.components.projectDisplayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.MetadataList
import com.example.prismstudio.p40b43a88.ui.components.PosterArt
import com.example.prismstudio.p40b43a88.ui.projects.ProjectDetailUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/** `project-detail-column`: poster at reading size, name, format facts and the project actions. */
@Composable
fun ProjectDetailPanel(
    detail: ProjectDetailUi,
    busy: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** `project-backups-button`: Resolve's Project Backups for this project. */
    onBackups: (() -> Unit)? = null,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PosterArt(detail.poster, detail.frame, radius = 16.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1.778f))
        Text(
            projectDisplayName(detail.name), translate = false,
            style = PrismType.headline,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MetadataList(detail.rows, rowHeight = 32.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen, enabled = !busy, size = ButtonDefaults.Regular) { Text("打开项目") }
            Button(
                onClick = onDuplicate,
                enabled = !busy,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("创建副本") }
            Button(
                onClick = onDelete,
                enabled = !busy,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.error,
                ),
            ) { Text("删除") }
        }
        // On a row of its own: four buttons do not fit the column's width.
        onBackups?.let {
            Button(
                onClick = it,
                enabled = !busy,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("项目备份…") }
        }
    }
}
