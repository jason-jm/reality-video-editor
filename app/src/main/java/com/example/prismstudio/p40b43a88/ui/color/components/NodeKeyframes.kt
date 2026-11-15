package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.KeyframeAction
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip

/**
 * `node-keyframes`: Resolve's dynamic keyframes for the selected node, beside the node cards — node 1's
 * whole correction dissolving from keyframe to keyframe (a grade that follows the light), or node 2's
 * window moving between its keyframes. Jump between them, add or remove one at the playhead, ease the
 * change into or out of the one there; once keyed, adjusting the node records at the playhead.
 */
@Composable
fun NodeKeyframes(
    title: String,
    keys: KeyframeUi,
    enabled: Boolean,
    onAction: (KeyframeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.semantics { contentDescription = "节点关键帧：$title".localized() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            if (keys.count > 0) Text("${keys.count} 个", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(KeyframeAction.PREVIOUS) }, enabled = enabled && keys.hasPrevious, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_player_previous), contentDescription = "上一个关键帧")
            }
            // The room the chips leave: a longer label ("Delete keyframe") fits itself to it.
            Button(
                onClick = { onAction(KeyframeAction.TOGGLE) },
                enabled = enabled && keys.playheadInside,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.weight(1f, fill = false),
            ) { Text(if (keys.atKeyframe) "删除关键帧" else "添加关键帧", fit = true, context = "compact") }
            IconButton(onClick = { onAction(KeyframeAction.NEXT) }, enabled = enabled && keys.hasNext, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_player_next), contentDescription = "下一个关键帧")
            }
            ToggleableChip(
                label = { Text("缓入") },
                isToggleOn = keys.easeIn,
                onClick = { onAction(KeyframeAction.EASE_IN) },
                enabled = enabled && keys.canEaseIn,
                chipSize = ChipsDefaults.Small,
            )
            ToggleableChip(
                label = { Text("缓出") },
                isToggleOn = keys.easeOut,
                onClick = { onAction(KeyframeAction.EASE_OUT) },
                enabled = enabled && keys.canEaseOut,
                chipSize = ChipsDefaults.Small,
            )
        }
        Text(
            if (enabled) keys.hint else "选择窗口形状后，可以添加关键帧",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
