package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/**
 * What the program monitor's playback menu can start: Resolve's Play Around (/), Play In to Out (Alt+/), Loop (Ctrl+/), and
 * its Proxy Handling (proxies or the camera originals).
 */
enum class PlaybackAction { AROUND, IN_TO_OUT, LOOP, PROXIES }

/**
 * `playback-menu-button`: Resolve's Playback menu for the program monitor — play around the playhead (2 s each side, then
 * back to it), play the timeline's In to Out, Loop, and 使用代理播放. The chip reads 循环 while looping, 代理 while proxies play.
 */
@Composable
fun PlaybackMenu(
    loop: Boolean,
    hasRange: Boolean,
    enabled: Boolean,
    onAction: (PlaybackAction) -> Unit,
    useProxies: Boolean = false,
    proxyNote: String = "",
) {
    var open by remember { mutableStateOf(false) }
    // The menu anchors to its direct parent.
    Box {
        ButtonChip(
            label = { Text(listOfNotNull("播放", "循环".takeIf { loop }, "代理".takeIf { useProxies }).joinToString(" · ")) },
            onClick = { open = true },
            enabled = enabled,
            leadingIcon = if (loop) ({ Icon(painterResource(R.drawable.ic_loop), contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(16.dp)) },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = ("播放选项" + if (loop) "：循环播放已开" else "").localized() },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignEnd(), VerticalPlacement.below()),
            ) {
                val act = { action: PlaybackAction ->
                    open = false
                    onAction(action)
                }
                PlaybackItem("播放头前后 2 秒", "/", subtitle = "然后回到播放头") { act(PlaybackAction.AROUND) }
                // Without both marks it still answers (先标记时间线入点和出点), dimmed.
                PlaybackItem("入点到出点", "Alt+/", subtitle = if (hasRange) "停在出点" else "先按 I / O 标记", dimmed = !hasRange) { act(PlaybackAction.IN_TO_OUT) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuItem(
                    title = { Text("循环播放") },
                    subtitle = { Text(if (loop) "已开：播完从头再放" else "播完从头再放（入点到出点或整条时间线）") },
                    onClick = { act(PlaybackAction.LOOP) },
                    trailingIcon = { Text("Ctrl+/", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) },
                    contentColors = if (loop) MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary) else MenuItemDefaults.menuItemColors(),
                )
                // `playback-proxies-item`: Resolve's Proxy Handling — the monitors play footage's proxies; renders never do.
                MenuItem(
                    title = { Text("使用代理播放") },
                    subtitle = { Text(proxyNote) },
                    onClick = { act(PlaybackAction.PROXIES) },
                    contentColors = if (useProxies) MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary) else MenuItemDefaults.menuItemColors(),
                )
            }
        }
    }
}

@Composable
private fun PlaybackItem(label: String, keys: String, subtitle: String, dimmed: Boolean = false, onClick: () -> Unit) {
    MenuItem(
        title = { Text(label) },
        subtitle = { Text(subtitle) },
        onClick = onClick,
        trailingIcon = { Text(keys, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary) },
        contentColors = if (dimmed) MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.labelTertiary) else MenuItemDefaults.menuItemColors(),
    )
}
