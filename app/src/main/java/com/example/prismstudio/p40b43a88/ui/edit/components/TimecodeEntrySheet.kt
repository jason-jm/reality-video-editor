package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.edit.TimecodeEntryUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `timecode-entry-sheet`: Resolve's typed timecode — a place to jump the playhead to, or with + / − an offset (which moves
 * the selected clips when there are any), read back as it is typed.
 */
@Composable
fun TimecodeEntrySheet(
    ui: TimecodeEntryUi,
    onText: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    // Typing goes straight into the field (the sheet often opens from the + / − keys).
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("输入时间码") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前 ${ui.current}", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                TextField(
                    value = ui.text,
                    onValueChange = onText,
                    placeholder = { Text("如 1:00、+15、-1:00:00") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(520.dp).focusRequester(focus).semantics { contentDescription = "时间码".localized() }.tabMovesFocus(),
                )
                Text(
                    ui.problem ?: ui.reading,
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
                Button(onClick = onConfirm, enabled = ui.canConfirm, size = ButtonDefaults.Regular) { Text(ui.confirmLabel) }
            }
        }
    }
}
