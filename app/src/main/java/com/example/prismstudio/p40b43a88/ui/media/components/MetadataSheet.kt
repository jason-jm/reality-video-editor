package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.example.prismstudio.p40b43a88.ui.media.MetadataSheetUi
import com.example.prismstudio.p40b43a88.domain.usecase.MediaMetadataUseCase
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
 * `metadata-sheet`: Resolve's Metadata panel, Shot & Scene — the selected media's scene, shot and take, its keywords (split
 * at commas), a note and Good Take; 保存 once something changed and it fits.
 */
@Composable
fun MetadataSheet(
    ui: MetadataSheetUi,
    onDraft: ((MediaMetadataUseCase.Draft) -> MediaMetadataUseCase.Draft) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val d = ui.draft
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("场记 · ${ui.assetName}", maxLines = 1) },
    ) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetaRow("场次") {
                SlateField(d.scene, "场次", "如 3") { value -> onDraft { it.copy(scene = value) } }
                Text("镜头", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                SlateField(d.shot, "镜头", "如 2A") { value -> onDraft { it.copy(shot = value) } }
                Text("条次", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                SlateField(d.take, "条次", "如 4") { value -> onDraft { it.copy(take = value) } }
            }
            MetaRow("关键词") {
                TextField(
                    value = d.keywords,
                    onValueChange = { value -> onDraft { it.copy(keywords = value) } },
                    placeholder = { Text("夜景，跑步") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(480.dp).semantics { contentDescription = "关键词".localized() }.tabMovesFocus(),
                )
            }
            MetaRow("备注") {
                TextField(
                    value = d.notes,
                    onValueChange = { value -> onDraft { it.copy(notes = value) } },
                    placeholder = { Text("这条素材的说明") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(480.dp).semantics { contentDescription = "备注".localized() }.tabMovesFocus(),
                )
            }
            MetaRow("好条") {
                Switch(
                    checked = d.goodTake,
                    onCheckedChange = { value -> onDraft { it.copy(goodTake = value) } },
                    modifier = Modifier.semantics { contentDescription = "好条：标记为可用的一条".localized() },
                )
                Text("标记为可用的一条", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Text(
                ui.hint,
                style = PrismType.caption,
                color = if (ui.problem != null) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelTertiary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                Button(
                    onClick = onClose,
                    size = ButtonDefaults.Regular,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillTertiary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) { Text("取消") }
                Button(onClick = onConfirm, enabled = ui.canConfirm, size = ButtonDefaults.Regular) { Text("保存") }
            }
        }
    }
}

/** A field of the slate: short, 96 dp (the sheet gives its content about 516 dp). */
@Composable
private fun SlateField(value: String, description: String, placeholder: String, onValue: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.width(96.dp).semantics { contentDescription = description.localized() }.tabMovesFocus(),
    )
}

/** A metadata field: its label in a fixed column, then its control(s). */
@Composable
private fun MetaRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(80.dp), fit = true)
        content()
    }
}
