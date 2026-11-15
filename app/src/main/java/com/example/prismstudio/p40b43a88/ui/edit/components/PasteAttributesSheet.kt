package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.edit.AttributeOptionUi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.usecase.ClipAttribute
import com.example.prismstudio.p40b43a88.ui.edit.PasteAttributesUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `paste-attributes-sheet`: Resolve's Paste Attributes — which attributes of the copied clip go onto the
 * selected one. An attribute the copied clip cannot give here (a sound's volume onto a title, a grade
 * from a title) rests switched off; the choice is kept for the next paste.
 */
@Composable
fun PasteAttributesSheet(
    ui: PasteAttributesUi,
    onToggle: (ClipAttribute, Boolean) -> Unit,
    onPaste: () -> Unit,
    onClose: () -> Unit,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("粘贴属性") },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "从“${ui.sourceName}”粘贴到“${ui.targetName}”：关键帧保持各自的时间。",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelSecondary,
            )
            // Two columns, so the seven attributes fit the sheet's height.
            ui.options.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    pair.forEach { option -> AttributeRow(option, onToggle, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
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
                Button(onClick = onPaste, enabled = ui.canPaste, size = ButtonDefaults.Regular) { Text("粘贴") }
            }
        }
    }
}

/** One attribute: its name, what it carries (or why it cannot be pasted here) and its switch. */
@Composable
private fun AttributeRow(option: AttributeOptionUi, onToggle: (ClipAttribute, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.attribute.label,
                style = PrismType.body,
                color = if (option.available) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
            )
            Text(
                if (option.available) option.attribute.detail else "复制的片段不能把这项给到这个片段",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelTertiary,
            )
        }
        Switch(
            checked = option.available && option.selected,
            onCheckedChange = { onToggle(option.attribute, it) },
            enabled = option.available,
            modifier = Modifier.semantics { contentDescription = "粘贴${option.attribute.label}".localized() },
        )
    }
}
