package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.edit.TimelineSettingsUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `timeline-settings-sheet`: Resolve's settings for the open timeline — its name, its raster (a landscape cut becomes a
 * vertical or square one, its layout following the frame), the frame rate (fixed: clips sit on its frames) and how
 * media of another shape or size fill the frame for clips that follow the project. 应用 is one undoable step.
 */
@Composable
fun TimelineSettingsSheet(
    ui: TimelineSettingsUi,
    onName: (String) -> Unit,
    onFormat: (Int) -> Unit,
    onScaling: (ClipScaling) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("时间线设置") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("名称", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TextField(
                    value = ui.name,
                    onValueChange = onName,
                    placeholder = { Text("时间线名称") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(520.dp).semantics { contentDescription = "时间线名称".localized() }.tabMovesFocus(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("画面", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                SegmentControl(Modifier.width(520.dp).semantics { contentDescription = "时间线画面".localized() }) {
                    ui.formats.forEachIndexed { index, label ->
                        SegmentItem(
                            selected = index == ui.formatIndex,
                            onClick = { onFormat(index) },
                            title = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "位置、裁切、窗口和标题按画面的比例保存：换成竖屏或方形，版面跟着新画面走",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("帧率", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                    Text(ui.fpsLabel, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
                }
                Text("片段按帧排在时间线上，帧率在新建项目时确定", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("比例不同的素材", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                FlowRow(Modifier.semantics { contentDescription = "比例不同的素材".localized() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClipScaling.entries.forEach { scaling ->
                        ToggleableChip(
                            label = { Text(scaling.label) },
                            isToggleOn = scaling == ui.scaling,
                            onClick = { onScaling(scaling) },
                            chipSize = ChipsDefaults.Small,
                        )
                    }
                }
                Text(ui.note, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                Button(
                    onClick = onClose,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("取消") }
                Button(onClick = onApply, enabled = ui.changed, size = ButtonDefaults.Regular) { Text("应用") }
            }
        }
    }
}
