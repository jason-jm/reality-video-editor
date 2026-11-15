package com.example.prismstudio.p40b43a88.ui.projects.components

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import androidx.compose.ui.res.stringResource
import com.example.prismstudio.p40b43a88.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.OssLicense
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.projects.AboutUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet

/**
 * `about-sheet`: the app's name and version, then the open-source software the release build ships, gathered by project
 * with each project's license (`about-family-row`); a license's full text replaces the list on 全文, and 返回列表 brings
 * the list back. Library names and license texts are never translated.
 */
@Composable
fun AboutSheet(ui: AboutUi, onShowLicense: (OssLicense?) -> Unit, onClose: () -> Unit) {
    val quiet = ButtonDefaults.buttonColors(
        containerColor = PicoTheme.colorScheme.fillTertiary,
        contentColor = PicoTheme.colorScheme.labelPrimary,
    )
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { if (ui.showingText) Text(ui.licenseTitle.orEmpty(), translate = false) else Text("关于 ${PRODUCT_NAME}") },
    ) {
        Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.showingText) {
                // `about-license-text`
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(ui.licenseText.orEmpty(), style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, translate = false)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    Button(onClick = { onShowLicense(null) }, size = ButtonDefaults.Regular, colors = quiet) { Text("返回列表") }
                    Button(onClick = onClose, size = ButtonDefaults.Regular, colors = quiet) { Text("关闭") }
                }
            } else {
                // `about-app-row`
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.app_name), style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary, translate = false)
                    Text("版本 ${ui.version}", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                    Text("PICO 上的视频剪辑、调色与后期软件", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
                }
                HorizontalDivider()
                Text("开源软件许可", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Text(
                    "${PRODUCT_NAME} 使用了下列 ${ui.componentCount} 个组件，它们按各自的许可证分发",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelTertiary,
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(ui.families, key = { it.name + it.licenseLabel }) { family ->
                        // `about-family-row`: the project, how many of its libraries and under which license, then their names.
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(family.name, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary, translate = false, modifier = Modifier.weight(1f))
                                Text("${family.count} 个组件 · ${family.licenseLabel}", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
                            }
                            Text(
                                family.components,
                                style = PrismType.caption,
                                color = PicoTheme.colorScheme.labelTertiary,
                                translate = false,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    // `about-license-button`: one for each license the app carries a text of.
                    ui.licenses.forEach { license ->
                        Button(onClick = { onShowLicense(license) }, size = ButtonDefaults.Regular, colors = quiet) { Text("${license.label} 全文") }
                    }
                    Button(onClick = onClose, size = ButtonDefaults.Regular, colors = quiet) { Text("关闭") }
                }
            }
        }
    }
}
