package com.example.prismstudio.p40b43a88.ui.projects.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.projects.NewProjectUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/** `new-project-sheet`: name, timeline format and frame rate, with cancel / create actions. */
@Composable
fun NewProjectSheet(
    form: NewProjectUi,
    busy: Boolean,
    onName: (String) -> Unit,
    onFormat: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        onDismissRequest = onCancel,
        title = { Text("新建项目") },
        bottom = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCancel,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("取消") }
                Button(onClick = onCreate, enabled = !busy, size = ButtonDefaults.Regular) { Text("创建项目") }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FormRow("项目名称") {
                TextField(
                    value = form.name,
                    onValueChange = onName,
                    placeholder = { Text("未命名项目") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(584.dp).tabMovesFocus(),
                )
            }
            FormRow("时间线格式") {
                SegmentControl(Modifier.width(584.dp)) {
                    form.formats.forEachIndexed { index, preset ->
                        SegmentItem(
                            selected = index == form.formatIndex,
                            onClick = { onFormat(index) },
                            title = { Text(preset.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            FormRow("帧率") {
                SegmentControl(Modifier.width(584.dp)) {
                    form.frameRates.forEachIndexed { index, fps ->
                        SegmentItem(
                            selected = index == form.fpsIndex,
                            onClick = { onFps(index) },
                            title = { Text("$fps") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        content()
    }
}
