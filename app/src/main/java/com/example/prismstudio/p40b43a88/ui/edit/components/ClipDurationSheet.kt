package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.edit.ClipDurationUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `clip-duration-sheet`: Resolve's Change Clip Duration (Ctrl+D) — the length as a timecode or seconds, what it reads as,
 * and whether the rest of the track follows (波纹).
 */
@Composable
fun ClipDurationSheet(
    ui: ClipDurationUi,
    onText: (String) -> Unit,
    onRipple: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("更改片段时长") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("“${ui.name}”的时长", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TextField(
                    value = ui.text,
                    onValueChange = onText,
                    placeholder = { Text(ui.original) },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(520.dp).semantics { contentDescription = "片段时长".localized() }.tabMovesFocus(),
                )
                Text(
                    ui.problem ?: ui.reading,
                    style = PrismType.caption,
                    color = if (ui.problem != null) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelTertiary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(456.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("波纹", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
                    Text(
                        if (ui.ripple) "后面的片段跟着移动，不覆盖也不留空隙" else "后面的片段不动：加长到下一个片段为止，缩短留下空隙",
                        style = PrismType.caption,
                        color = PicoTheme.colorScheme.labelTertiary,
                    )
                }
                Switch(checked = ui.ripple, onCheckedChange = onRipple, modifier = Modifier.semantics { contentDescription = "波纹".localized() })
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
                Button(onClick = onConfirm, enabled = ui.canConfirm, size = ButtonDefaults.Regular) { Text("确定") }
            }
        }
    }
}
