package com.example.prismstudio.p40b43a88.ui.projects.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.windows.AlertDialog

/** Confirmation of the `delete-project` transition. */
@Composable
fun DeleteProjectDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CountsAsOpenSheet()
    AlertDialog(
        properties = BackDismissSheet,
        onDismissRequest = onDismiss,
        title = { Text("删除这个项目？") },
        content = {
            Text("将永久删除“$projectName”的时间线、调色、混音设置和全部项目备份。设备上的原始素材会保留。")
        },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            }
        },
    )
}
