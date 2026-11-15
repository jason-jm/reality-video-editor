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
import com.example.prismstudio.p40b43a88.ui.edit.RenameUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `rename-sheet`: a clip's or a track's name (Resolve's clip name / track name) — the field, what the name may be, and
 * 确定 once it is a change that can be used.
 */
@Composable
fun RenameSheet(
    ui: RenameUi,
    onText: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text(ui.title) },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("名称", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TextField(
                    value = ui.text,
                    onValueChange = onText,
                    placeholder = { Text(ui.original) },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(520.dp).semantics { contentDescription = ui.title.localized() }.tabMovesFocus(),
                )
                Text(
                    ui.problem ?: ui.hint,
                    style = PrismType.caption,
                    color = if (ui.problem != null) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelTertiary,
                )
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
