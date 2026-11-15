package com.example.prismstudio.p40b43a88.ui.workspace.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.ToggleIconButton
import com.pico.spatial.ui.design.VerticalDivider
import com.pico.spatial.ui.design.windows.Toolbar

/** Transport state shown on the deck for whichever monitor the current stage drives. */
data class DeckTransport(
    val playing: Boolean,
    val readout: String,
    val fps: Int,
)

/**
 * `jog-deck`: the Speed-Editor-style Toolbar ornament under the window — transport, the geared jog
 * dial and the current stage's big edit keys ([tools]).
 */
@Composable
fun JogDeck(
    transport: DeckTransport,
    onPlay: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJog: (Int) -> Unit,
    showDivider: Boolean,
    /** What ⏮ / ⏭ do on this stage (`prev-edit-button` / `next-edit-button`). */
    previousLabel: String = "上一编辑点",
    nextLabel: String = "下一编辑点",
    /** `loop-toggle` (Ctrl+/): shown while the deck drives the program monitor. */
    loop: Boolean? = null,
    onLoop: (Boolean) -> Unit = {},
    tools: @Composable RowScope.() -> Unit,
) {
    // Not focusable: keyboard shortcuts must keep reaching the main window.
    Toolbar(cornerSize = 32.dp, focusable = false) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(painterResource(R.drawable.ic_player_previous), contentDescription = previousLabel)
                }
                ToggleIconButton(checked = transport.playing, onCheckedChange = onPlay) {
                    Icon(
                        painterResource(if (transport.playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                        contentDescription = if (transport.playing) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(painterResource(R.drawable.ic_player_next), contentDescription = nextLabel)
                }
                if (loop != null) {
                    ToggleIconButton(checked = loop, onCheckedChange = onLoop) {
                        Icon(painterResource(R.drawable.ic_loop), contentDescription = if (loop) "循环播放：开（Ctrl+/）" else "循环播放（Ctrl+/）")
                    }
                }
            }
            JogDial(readout = transport.readout, fps = transport.fps, onJog = onJog, modifier = Modifier.size(240.dp, 48.dp))
            if (showDivider) VerticalDivider(Modifier.height(40.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tools,
            )
        }
    }
}
