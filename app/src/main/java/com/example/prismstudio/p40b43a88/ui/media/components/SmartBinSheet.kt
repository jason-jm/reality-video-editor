package com.example.prismstudio.p40b43a88.ui.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.usecase.SmartBinsUseCase
import com.example.prismstudio.p40b43a88.ui.components.CountsAsOpenSheet
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.media.SmartBinSheetUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.example.prismstudio.p40b43a88.ui.components.BackDismissSheet
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/**
 * `smart-bin-sheet`: Resolve's Smart Bin dialog — a name and the rules every piece of media must meet (类型, 标记: a flag and
 * 仅好条, 使用 on the project's timelines, 名称包含, 关键词 from the clip metadata), with how many media they find as they change; 创建 / 保存 once it can be one.
 */
@Composable
fun SmartBinSheet(
    ui: SmartBinSheetUi,
    onRules: ((SmartBinsUseCase.Rules) -> SmartBinsUseCase.Rules) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val rules = ui.rules
    CountsAsOpenSheet()
    Sheet(
        properties = BackDismissSheet,
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onClose,
        title = { Text(ui.title) },
    ) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuleRow("名称") {
                TextField(
                    value = rules.name,
                    onValueChange = { value -> onRules { it.copy(name = value) } },
                    placeholder = { Text("智能媒体夹") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(480.dp).semantics { contentDescription = "智能媒体夹名称".localized() }.tabMovesFocus(),
                )
            }
            RuleRow("类型") {
                SegmentControl(Modifier.width(480.dp)) {
                    SmartBinSheetUi.KINDS.forEachIndexed { index, kind ->
                        SegmentItem(
                            selected = index == ui.kindIndex,
                            onClick = { onRules { it.copy(kind = kind) } },
                            title = { Text(SmartBinSheetUi.KIND_LABELS[index]) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            RuleRow("标记") {
                var open by remember { mutableStateOf(false) }
                Box {
                    ButtonChip(
                        label = { Text(rules.flag.label) },
                        onClick = { open = true },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "旗标条件：${rules.flag.label}，点按更改".localized() },
                    )
                    if (open) {
                        Menu(
                            onDismissRequest = { open = false },
                            positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.below()),
                        ) {
                            val picked = MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                            val plain = MenuItemDefaults.menuItemColors()
                            SmartBinSheetUi.FLAGS.forEach { flag ->
                                MenuItem(
                                    title = { Text(flag.label) },
                                    onClick = { open = false; onRules { it.copy(flag = flag) } },
                                    contentColors = if (flag == rules.flag) picked else plain,
                                )
                            }
                        }
                    }
                }
                // `smart-good-take-chip`: only media marked Good Take in their clip metadata.
                ToggleableChip(
                    label = { Text("仅好条") },
                    isToggleOn = rules.goodTake,
                    onClick = { onRules { it.copy(goodTake = !it.goodTake) } },
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "仅好条：只收集场记里标为好条的素材".localized() },
                )
            }
            RuleRow("使用") {
                SegmentControl(Modifier.width(360.dp)) {
                    SmartBinSheetUi.USAGES.forEach { usage ->
                        SegmentItem(
                            selected = usage.ordinal == ui.usageIndex,
                            onClick = { onRules { it.copy(usage = usage) } },
                            title = { Text(usage.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            RuleRow("名称包含") {
                TextField(
                    value = rules.nameContains,
                    onValueChange = { value -> onRules { it.copy(nameContains = value) } },
                    placeholder = { Text("不限") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(480.dp).semantics { contentDescription = "名称包含".localized() }.tabMovesFocus(),
                )
            }
            RuleRow("关键词") {
                TextField(
                    value = rules.keyword,
                    onValueChange = { value -> onRules { it.copy(keyword = value) } },
                    placeholder = { Text("不限（场记里的关键词）") },
                    singleLine = true,
                    clearable = true,
                    modifier = Modifier.width(480.dp).semantics { contentDescription = "关键词包含".localized() }.tabMovesFocus(),
                )
            }
            Text(
                ui.status,
                style = PrismType.caption,
                color = if (ui.problem != null) PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.labelTertiary,
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
                Button(onClick = onConfirm, enabled = ui.canConfirm, size = ButtonDefaults.Regular) { Text(ui.confirmLabel) }
            }
        }
    }
}

/** A rule: its label in a fixed column, then its control. */
@Composable
private fun RuleRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(80.dp), fit = true)
        content()
    }
}
