package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.usecase.TransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.ui.components.ColorSwatch
import com.example.prismstudio.p40b43a88.ui.components.FlagToggles
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.domain.usecase.LinkAction
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/**
 * `clip-menu`: the timeline's clip menu (Resolve's right-click menu), opened by holding a press on a
 * clip — the clip colour row, the flag row (Resolve's flags on the clip's media), 选择全部…色片段 for a coloured clip,
 * 剪切 / 波纹剪切 / 复制 / 粘贴 / 插入粘贴 / 粘贴属性… / 创建副本 /
 * 定格帧 / 取消链接 or 链接片段 / 停用片段 / 删除 / 波纹删除 / 选择右侧全部片段 /
 * 选择全部片段 — or on an empty lane, where 粘贴 / 插入粘贴 and the two selections (forward from the playhead) apply.
 * Held on one of several selected clips ([selected] of them) its commands act on all of them. It opens
 * above the press, the timeline being at the bottom of the window.
 */
@Composable
fun ClipMenu(
    onClip: Boolean,
    canPaste: Boolean,
    onEvent: (EditEvent) -> Unit,
    onDismiss: () -> Unit,
    clipEnabled: Boolean = true,
    clipId: String? = null,
    selected: Int = 1,
    /** The held clip's colour (Resolve's clip colour), picked in the menu's first row. */
    clipColor: ClipColor? = null,
    /** `clip-menu-link`: what Link Clips does with the selection (none: no item). */
    link: LinkAction? = null,
    /** The flags on the held clip's media (Resolve's flags), toggled in the menu's second row; null: no media to flag. */
    clipFlags: List<FlagColor>? = null,
    /** `clip-menu-delete-gap`: the picked gap's length (an empty lane's menu), or null when none is picked. */
    gapText: String? = null,
    /** `clip-menu-detect-scene-cuts`: the held clip is footage that can be searched for its shots' cuts. */
    canDetectCuts: Boolean = false,
    /** `clip-menu-open-compound` / `clip-menu-decompose`: the held clip is a compound clip. */
    compound: Boolean = false,
    /** `clip-menu-open-multicam` / `clip-menu-flatten-multicam`: the held clip is a multicam clip. */
    multicam: Boolean = false,
) {
    Menu(
        onDismissRequest = onDismiss,
        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
    ) {
        val act = { event: EditEvent ->
            onDismiss()
            onEvent(event)
        }
        if (onClip) {
            // `clip-color-row`: the clip colour (of every selected clip), or 无 for the kind's own.
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { contentDescription = "片段颜色".localized() },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClipColor.entries.forEach { color ->
                    ColorSwatch(PrismColors.clip(color), "片段颜色：${color.label}", color == clipColor, onClick = { act(EditEvent.SetClipColor(color)) }, size = 28.dp)
                }
                ToggleableChip(
                    label = { Text("无") },
                    isToggleOn = clipColor == null,
                    onClick = { act(EditEvent.SetClipColor(null)) },
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "清除片段颜色".localized() },
                )
            }
            // `clip-flag-row`: Resolve's flags on the media of every selected clip — each toggles, 清除 takes all off.
            clipFlags?.let { flags ->
                FlagToggles(
                    flags = flags,
                    onToggle = { act(EditEvent.ToggleFlag(it)) },
                    onClear = { act(EditEvent.ClearFlags) },
                    size = 28.dp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            clipColor?.let { color -> ClipMenuItem("选择全部${color.label}色片段", null) { act(EditEvent.SelectClipColor(color)) } }
            ClipMenuItem("剪切", "Ctrl+X") { act(EditEvent.CutClip) }
            // `clip-menu-ripple-cut`: the cut closes up behind it.
            ClipMenuItem("波纹剪切", "Ctrl+Shift+X") { act(EditEvent.RippleCut) }
            ClipMenuItem("复制", "Ctrl+C") { act(EditEvent.CopyClip) }
        }
        // Without a copy it still answers (剪贴板是空的), dimmed.
        ClipMenuItem("粘贴", "Ctrl+V", dimmed = !canPaste) { act(EditEvent.PasteClips) }
        // `clip-menu-paste-insert`: the paste makes room instead of covering what is there.
        ClipMenuItem("插入粘贴", "Ctrl+Shift+V", dimmed = !canPaste) { act(EditEvent.PasteInsert) }
        if (onClip) {
            val several = selected > 1
            ClipMenuItem("粘贴属性…", "Alt+V", dimmed = !canPaste) { act(EditEvent.OpenPasteAttributes) }
            ClipMenuItem(if (several) "创建 $selected 个副本" else "创建副本", null) { act(EditEvent.DuplicateClip) }
            if (!several) ClipMenuItem("定格帧", "2 秒") { act(EditEvent.FreezeFrame) }
            // `clip-menu-new-compound`: Resolve's New Compound Clip — the held clips (and their sound) as one clip.
            ClipMenuItem("新建复合片段", null) { act(EditEvent.CreateCompound) }
            // `clip-menu-new-multicam`: Resolve's New Multicam Clip — the held camera clips as angles of one clip.
            if (several) ClipMenuItem("新建多机位片段", null) { act(EditEvent.CreateMulticam) }
            if (!several && multicam && clipId != null) {
                ClipMenuItem("打开多机位片段", null) { act(EditEvent.OpenCompound(clipId)) }
                ClipMenuItem("展平多机位片段", null) { act(EditEvent.DecomposeCompound(clipId)) }
            } else if (!several && compound && clipId != null) {
                ClipMenuItem("打开复合片段", null) { act(EditEvent.OpenCompound(clipId)) }
                ClipMenuItem("分解复合片段", null) { act(EditEvent.DecomposeCompound(clipId)) }
            }
            // Resolve's Detect Scene Cuts: cut a clip of several shots where they change.
            if (!several && canDetectCuts && clipId != null) ClipMenuItem("检测场景剪切", null) { act(EditEvent.DetectSceneCuts(clipId)) }
            // `clip-menu-match-frame`: Resolve's Match Frame from the held clip.
            if (!several && clipId != null) ClipMenuItem("匹配帧", "F") { act(EditEvent.MatchFrame(clipId)) }
            // `clip-menu-mark-clip`: Resolve's Mark Clip — the timeline's In / Out around the held clip.
            if (!several) ClipMenuItem("按片段标记入出点", "X") { act(EditEvent.MarkClip) }
            // `clip-menu-add-transition`: Resolve's Add Transition at the selected clips' ends.
            ClipMenuItem("添加转场", "Ctrl+T") { act(EditEvent.AddTransitions(TransitionScope.BOTH)) }
            // `clip-menu-trim-start` / `-trim-end`: Resolve's Trim Start / End, the clip's edge to the playhead.
            ClipMenuItem("修剪开头到播放头", "Shift+[") { act(EditEvent.TrimToPlayhead(TrimEdge.START, ripple = false)) }
            ClipMenuItem("修剪结尾到播放头", "Shift+]") { act(EditEvent.TrimToPlayhead(TrimEdge.END, ripple = false)) }
            // `clip-menu-rename`: Resolve's clip name, in the rename sheet.
            if (!several && clipId != null) ClipMenuItem("重命名…", null) { act(EditEvent.OpenRenameClip(clipId)) }
            // `clip-menu-duration`: Resolve's Change Clip Duration.
            if (!several && clipId != null) ClipMenuItem("更改时长…", "Ctrl+D") { act(EditEvent.OpenClipDuration(clipId)) }
            // `clip-menu-swap-left` / `-swap-right`: Resolve's Swap Clips with the neighbour before / after.
            if (!several) {
                ClipMenuItem("与左侧片段交换", "Ctrl+Shift+,") { act(EditEvent.SwapClip(right = false)) }
                ClipMenuItem("与右侧片段交换", "Ctrl+Shift+.") { act(EditEvent.SwapClip(right = true)) }
            }
            link?.let { ClipMenuItem(it.label, "Ctrl+Alt+L") { act(EditEvent.ToggleLink) } }
            ClipMenuItem(
                when {
                    several && clipEnabled -> "停用 $selected 个片段"
                    several -> "启用 $selected 个片段"
                    clipEnabled -> "停用片段"
                    else -> "启用片段"
                },
                "D",
            ) { act(EditEvent.ToggleClipEnabled) }
            ClipMenuItem(if (several) "删除 $selected 个片段" else "删除", "Backspace") { act(EditEvent.DeleteClip) }
            ClipMenuItem("波纹删除", "Delete") { act(EditEvent.RippleDelete) }
        }
        // On an empty lane: Resolve's gaps — the picked one, or every one (Delete Gaps).
        if (!onClip) {
            gapText?.let { ClipMenuItem("删除空隙（$it）", "Delete") { act(EditEvent.DeleteGap) } }
            ClipMenuItem("删除所有空隙", null) { act(EditEvent.DeleteAllGaps) }
        }
        // Everything from here on (all tracks): make room, or move the rest of the cut together.
        ClipMenuItem("选择右侧全部片段", "Y") { act(EditEvent.SelectForward(clipId)) }
        ClipMenuItem("选择全部片段", "Ctrl+A") { act(EditEvent.SelectAll) }
    }
}

@Composable
private fun ClipMenuItem(label: String, keys: String?, dimmed: Boolean = false, onClick: () -> Unit) {
    MenuItem(
        title = { Text(label) },
        onClick = onClick,
        trailingIcon = keys?.let { { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) } },
        contentColors = if (dimmed) MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelTertiary) else MenuItemDefaults.menuItemColors(),
    )
}
