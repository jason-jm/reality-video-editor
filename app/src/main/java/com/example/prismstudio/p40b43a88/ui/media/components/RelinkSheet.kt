package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.media.RelinkRowUi
import com.example.prismstudio.p40b43a88.ui.media.RelinkSheetUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.Option
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `relink-sheet`: Resolve's Relink Media for the pool's offline media. Each row names the media, where its file was, and the
 * file it will be relinked to — found on the device by name (its length and size checked), chosen among the files found,
 * or picked by hand in the system's file picker; 重新链接 relinks every row that has a file, as one undo step.
 */
@Composable
fun RelinkSheet(
    ui: RelinkSheetUi,
    onChoose: (assetId: String, uri: String) -> Unit,
    onPick: (assetId: String) -> Unit,
    onGrantPermission: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onChangeSelection: () -> Unit = onGrantPermission,
) {
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text("重新链接离线素材", maxLines = 1) },
    ) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // `relink-summary`
            Text(
                ui.summary,
                style = PrismType.caption,
                color = if (ui.permissionRequired || ui.error != null) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelTertiary,
            )
            if (ui.permissionRequired) SmallButton("授予权限", enabled = true, onClick = onGrantPermission)
            if (ui.limitedVisualAccess && !ui.searching) LimitedMediaAccessRow(!ui.relinking, onChangeSelection)
            // `relink-rows`: scrolls past four or so media.
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider()
                    RelinkRow(row, onChoose, onPick)
                }
                if (ui.rows.isEmpty()) Text("没有离线素材", style = PrismType.body, color = PicoTheme.colorScheme.labelSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                SmallButton("取消", enabled = true, onClick = onClose)
                // Small: a disabled Regular button draws its label as bars on the device.
                Button(onClick = onConfirm, enabled = ui.canConfirm, size = ButtonDefaults.Small) { Text(ui.confirmLabel) }
            }
        }
    }
}

/**
 * `relink-row`: the media's name, where it was, what it will be relinked to; `relink-pick-button` picks a file by hand, and
 * with files found the `relink-choice` options pick among them (or 暂不链接).
 */
@Composable
private fun RelinkRow(row: RelinkRowUi, onChoose: (String, String) -> Unit, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.name, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.was, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("选择文件…", enabled = true, description = "为 ${row.name} 手动选择文件") { onPick(row.id) }
        }
        // `relink-status`
        Text(
            row.status,
            style = PrismType.caption,
            color = when {
                row.warning -> PicoTheme.colorScheme.error
                row.found -> PicoTheme.colorScheme.labelSecondary
                else -> PicoTheme.colorScheme.labelTertiary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // With more than one file found, or only an unsure one, the choice is the user's.
        if (row.choices.size > 1 || (row.choices.isNotEmpty() && !row.found)) {
            row.choices.forEach { choice ->
                Option(
                    selected = choice.chosen,
                    onSelectChange = { onChoose(row.id, if (choice.chosen) "" else choice.uri) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "链接到 ${choice.label}".localized() },
                ) { Text(choice.label, style = PrismType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Option(
                selected = !row.found,
                onSelectChange = { onChoose(row.id, "") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${row.name} 暂不链接".localized() },
            ) { Text("暂不链接", style = PrismType.caption) }
        }
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean, description: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        size = ButtonDefaults.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.fillTertiary,
            contentColor = PicoTheme.colorScheme.labelPrimary,
        ),
        modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description.localized() },
    ) { Text(text) }
}
