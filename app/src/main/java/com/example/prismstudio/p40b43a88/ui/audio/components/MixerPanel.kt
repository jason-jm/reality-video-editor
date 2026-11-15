package com.example.prismstudio.p40b43a88.ui.audio.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.audio.MixerBusUi
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.audio.AudioClipUi
import com.example.prismstudio.p40b43a88.ui.audio.AudioUiState
import com.example.prismstudio.p40b43a88.ui.audio.ChannelEffect
import com.example.prismstudio.p40b43a88.ui.audio.LoudnessUi
import com.example.prismstudio.p40b43a88.ui.audio.MixerChannelUi
import com.example.prismstudio.p40b43a88.ui.components.LevelMeter
import com.example.prismstudio.p40b43a88.ui.components.TrackingSlider
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.pico.spatial.ui.design.SliderDefaults
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.ToggleIconButton
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement

/** `mixer-channels` + `master-row` + `audio-clip-gain-row` + `audio-loudness-block`. */
@Composable
fun MixerPanel(
    channels: List<MixerChannelUi>,
    masterDb: Float,
    masterLevelDb: Float,
    selection: AudioClipUi?,
    loudness: LoudnessUi,
    loudnessTarget: Int,
    onMute: (String, Boolean) -> Unit,
    onSolo: (String, Boolean) -> Unit,
    onPan: (String, Float) -> Unit,
    onVolume: (String, Float) -> Unit,
    onMaster: (Float) -> Unit,
    onClipGain: (Float) -> Unit,
    onLoudnessTarget: (Int) -> Unit,
    onNormalize: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens [trackId]'s EQ sheet. */
    onEq: (String) -> Unit = {},
    /** Opens [trackId]'s dynamics sheet. */
    onDynamics: (String) -> Unit = {},
    /** Opens [trackId]'s ducking sheet. */
    onDucking: (String) -> Unit = {},
    /** Opens [trackId]'s noise reduction sheet. */
    onNoise: (String) -> Unit = {},
    /** Opens [trackId]'s de-esser sheet. */
    onDeEsser: (String) -> Unit = {},
    /** Opens [trackId]'s reverb sheet. */
    onReverb: (String) -> Unit = {},
    /** Opens the selected clip's own EQ in the EQ sheet. */
    onClipEq: () -> Unit = {},
    /** `mixer-view-segment`: 0 the tracks' strips, 1 the submix buses'. */
    view: Int = 0,
    buses: List<MixerBusUi> = emptyList(),
    canAddBus: Boolean = false,
    onView: (Int) -> Unit = {},
    /** A track to a bus (null: the main output). */
    onOutput: (String, String?) -> Unit = { _, _ -> },
    /** A new bus; with a track id that track goes to it. */
    onAddBus: (String?) -> Unit = {},
    onRemoveBus: (String) -> Unit = {},
    onBusMute: (String, Boolean) -> Unit = { _, _ -> },
    onBusSolo: (String, Boolean) -> Unit = { _, _ -> },
    onBusVolume: (String, Float) -> Unit = { _, _ -> },
    onBusEq: (String) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // `mixer-title-row`: Fairlight's mixer shows the tracks' strips and the buses'; here one at a time (the column holds
        // three strips below the monitor), with 添加总线 beside them in the buses' view.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("调音台", style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
            if (view == 1 && canAddBus) {
                Button(
                    onClick = { onAddBus(null) },
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                ) { Text("添加总线") }
            }
            SegmentControl(Modifier.width(200.dp).semantics { contentDescription = "调音台视图".localized() }) {
                listOf("轨道", if (buses.isEmpty()) "总线" else "总线 ${buses.size}").forEachIndexed { index, label ->
                    SegmentItem(selected = index == view, onClick = { onView(index) }, title = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (view == 1) {
            BusStrips(buses, onRemoveBus, onBusMute, onBusSolo, onBusVolume, onBusEq)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            channels.forEach { ch ->
                // Tighter than the other rows: the strip carries its processing chips (narrower pan, fader and meter since rev 97).
                Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutputChip(ch, buses, canAddBus, onOutput, onAddBus)
                    ToggleIconButton(checked = ch.mute, onCheckedChange = { onMute(ch.trackId, it) }) {
                        Icon(painterResource(R.drawable.ic_volume_slash), contentDescription = "静音 ${ch.name}")
                    }
                    ToggleIconButton(checked = ch.solo, onCheckedChange = { onSolo(ch.trackId, it) }) {
                        Icon(painterResource(R.drawable.ic_headphone), contentDescription = "独奏 ${ch.name}")
                    }
                    TrackingSlider(
                        value = ch.pan,
                        onValueChange = { onPan(ch.trackId, if (kotlin.math.abs(it) < 0.04f) 0f else it) },
                        valueRange = -1f..1f,
                        sliderSpec = SliderDefaults.Small,
                        modifier = Modifier.width(52.dp).semantics { contentDescription = "声像 ${ch.name}".localized() },
                    )
                    TrackingSlider(
                        value = ch.volumeDb,
                        onValueChange = { onVolume(ch.trackId, it) },
                        valueRange = -60f..12f,
                        sliderSpec = SliderDefaults.Small,
                        modifier = Modifier.width(72.dp).semantics { contentDescription = "推子 ${ch.name}".localized() },
                    )
                    LevelMeter(ch.levelDb, Modifier.size(32.dp, 12.dp), description = "${ch.name} 电平")
                    // Fairlight's EQ thumbnail: lit while the track's EQ changes the sound; opens its sheet.
                    ToggleableChip(
                        label = { Text("EQ") },
                        isToggleOn = ch.eqActive,
                        onClick = { onEq(ch.trackId) },
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "均衡器 ${ch.name}".localized() },
                    )
                    // Fairlight's dynamics thumbnail, the same way.
                    ToggleableChip(
                        label = { Text("DYN") },
                        isToggleOn = ch.dynamicsActive,
                        onClick = { onDynamics(ch.trackId) },
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "动态 ${ch.name}".localized() },
                    )
                    // Fairlight's Effects slots: ducking, noise reduction, the de-esser and the reverb behind one chip (rev 137: five chips
                    // overflowed the strip and pushed its dB readout out); lit while any of them changes the sound.
                    EffectsChip(ch) { effect ->
                        when (effect) {
                            ChannelEffect.DUCKING -> onDucking(ch.trackId)
                            ChannelEffect.NOISE -> onNoise(ch.trackId)
                            ChannelEffect.DE_ESSER -> onDeEsser(ch.trackId)
                            ChannelEffect.REVERB -> onReverb(ch.trackId)
                        }
                    }
                    Text(
                        formatValue(ch.volumeDb, 1, true, " dB"),
                        style = PrismType.caption,
                        color = PicoTheme.colorScheme.labelPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }
        Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("主输出", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(80.dp), fit = true, fitLines = 2)
            TrackingSlider(
                value = masterDb,
                onValueChange = onMaster,
                valueRange = -60f..12f,
                sliderSpec = SliderDefaults.Small,
                modifier = Modifier.width(260.dp).semantics { contentDescription = "主输出推子".localized() },
            )
            LevelMeter(masterLevelDb, Modifier.size(120.dp, 12.dp), description = "主输出电平")
            Text(
                formatValue(masterDb, 1, true, " dB"),
                style = PrismType.caption,
                color = PicoTheme.colorScheme.labelPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
        ValueSliderRow(
            label = "片段增益",
            value = selection?.gainDb ?: 0f,
            range = -60f..12f,
            valueText = formatValue(selection?.gainDb ?: 0f, 1, true, " dB"),
            onValueChange = onClipGain,
            enabled = selection != null && !selection.locked && !selection.gainBlocked,
            step = 0.5f,
            minSliderWidth = 150.dp,
            labelWidth = 100.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        // `audio-clip-eq-row`: the selected sound's own equaliser (Resolve's clip EQ), in the same sheet as a track's.
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("片段均衡器", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(100.dp), fit = true, fitLines = 2)
            Text(
                selection?.eqSummary ?: "选择一个声音片段",
                style = PrismType.label,
                color = if (selection?.eqActive == true) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onClipEq,
                enabled = selection != null && !selection.locked,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                modifier = Modifier.semantics { contentDescription = "编辑片段均衡器".localized() },
            ) { Text("编辑") }
        }
        LoudnessSection(loudness, loudnessTarget, onLoudnessTarget, onNormalize)
    }
}

/**
 * `audio-loudness-block`: the selected clip's loudness as the mix plays it (BS.1770 integrated
 * loudness and sample peak), and Resolve's Normalize Audio Levels to a chosen target.
 */
@Composable
private fun LoudnessSection(loudness: LoudnessUi, target: Int, onTarget: (Int) -> Unit, onNormalize: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("响度", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(100.dp), fit = true, fitLines = 2)
            Text(loudness.readout, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("目标", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(100.dp), fit = true, fitLines = 2)
            // The segment takes what the label and 标准化 leave ("Normalize" is twice as wide).
            SegmentControl(Modifier.weight(1f).widthIn(max = 330.dp).semantics { contentDescription = "目标响度".localized() }) {
                AudioUiState.LOUDNESS_TARGET_LABELS.forEachIndexed { index, label ->
                    SegmentItem(
                        selected = index == target,
                        onClick = { onTarget(index) },
                        title = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(
                onClick = onNormalize,
                enabled = loudness.canNormalize,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("标准化") }
        }
    }
}

/**
 * `channel-output-chip`: the track's name, and under it the submix bus it goes to (→B1) — nothing for the main output. A tap
 * opens `channel-output-menu`: 主输出, each bus with its tracks, and 新建总线 that the track then goes to.
 */
@Composable
private fun OutputChip(
    ch: MixerChannelUi,
    buses: List<MixerBusUi>,
    canAddBus: Boolean,
    onOutput: (String, String?) -> Unit,
    onAddBus: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val where = buses.firstOrNull { it.busId == ch.busId }?.name ?: "主输出"
    Box(
        Modifier
            .size(40.dp, 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .spatialHoverEffect()
            .clickable { open = true }
            .semantics { contentDescription = "${ch.name} 输出到$where，点按更改".localized() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ch.name, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
            ch.busShort?.let { Text("→$it", style = PrismType.caption, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1) }
        }
        if (open) {
            // Above: the strips sit in the window's lower half.
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
            ) {
                val selected = MenuItemDefaults.menuItemColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                MenuItem(
                    title = { Text("主输出") },
                    onClick = { open = false; if (ch.busId != null) onOutput(ch.trackId, null) },
                    contentColors = if (ch.busId == null) selected else MenuItemDefaults.menuItemColors(),
                )
                buses.forEach { b ->
                    MenuItem(
                        title = { Text(b.name) },
                        subtitle = { Text(b.members) },
                        onClick = { open = false; if (ch.busId != b.busId) onOutput(ch.trackId, b.busId) },
                        contentColors = if (ch.busId == b.busId) selected else MenuItemDefaults.menuItemColors(),
                    )
                }
                if (canAddBus) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MenuItem(title = { Text("新建总线并输出到它") }, onClick = { open = false; onAddBus(ch.trackId) })
                }
            }
        }
    }
}

/**
 * `bus-strips`: each submix bus — its name, mute, solo, fader, meter after the fader, EQ chip, the tracks that go to it and
 * a delete button (its tracks go back to the main output); an empty state says how tracks get onto a bus.
 */
@Composable
private fun BusStrips(
    buses: List<MixerBusUi>,
    onRemove: (String) -> Unit,
    onMute: (String, Boolean) -> Unit,
    onSolo: (String, Boolean) -> Unit,
    onVolume: (String, Float) -> Unit,
    onEq: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (buses.isEmpty()) {
            // `bus-empty-text`
            Text(
                "还没有总线：点「添加总线」，再在轨道视图里点轨道名称，把对白、音乐等轨道输出到同一条总线，一个推子一起调。",
                style = PrismType.label,
                color = PicoTheme.colorScheme.labelTertiary,
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
        buses.forEach { b ->
            Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.width(48.dp), fit = true)
                ToggleIconButton(checked = b.mute, onCheckedChange = { onMute(b.busId, it) }) {
                    Icon(painterResource(R.drawable.ic_volume_slash), contentDescription = "静音 ${b.name}")
                }
                ToggleIconButton(checked = b.solo, onCheckedChange = { onSolo(b.busId, it) }) {
                    Icon(painterResource(R.drawable.ic_headphone), contentDescription = "独奏 ${b.name}")
                }
                TrackingSlider(
                    value = b.volumeDb,
                    onValueChange = { onVolume(b.busId, it) },
                    valueRange = -60f..12f,
                    sliderSpec = SliderDefaults.Small,
                    modifier = Modifier.width(96.dp).semantics { contentDescription = "推子 ${b.name}".localized() },
                )
                LevelMeter(b.levelDb, Modifier.size(32.dp, 12.dp), description = "${b.name} 电平")
                ToggleableChip(
                    label = { Text("EQ") },
                    isToggleOn = b.eqActive,
                    onClick = { onEq(b.busId) },
                    chipSize = ChipsDefaults.Small,
                    modifier = Modifier.semantics { contentDescription = "均衡器 ${b.name}".localized() },
                )
                Text(
                    b.members,
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "${b.name} 的轨道：${b.members}".localized() },
                )
                Text(
                    formatValue(b.volumeDb, 1, true, " dB"),
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelPrimary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(48.dp),
                )
                IconButton(onClick = { onRemove(b.busId) }, size = IconButtonDefaults.Small) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "删除 ${b.name}")
                }
            }
        }
    }
}

/**
 * `channel-effects-chip`: the track's effects behind one chip, 效果 N while N of them change the sound; its menu
 * (`channel-effects-menu`) lists each with what it does and opens its sheet.
 */
@Composable
private fun EffectsChip(ch: MixerChannelUi, onOpen: (ChannelEffect) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToggleableChip(
            label = { Text(ch.effectsLabel) },
            isToggleOn = ch.effectCount > 0,
            onClick = { open = true },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "效果 ${ch.name}：${ch.effectCount} 个在起作用".localized() },
        )
        if (open) {
            Menu(
                onDismissRequest = { open = false },
                positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignEnd(), VerticalPlacement.below()),
            ) {
                ch.effects.forEach { e ->
                    MenuItem(
                        title = { Text(e.effect.title) },
                        subtitle = { Text(e.summary) },
                        onClick = { open = false; onOpen(e.effect) },
                        trailingIcon = {
                            Text(
                                if (e.active) "开" else "关",
                                style = PrismType.caption,
                                color = if (e.active) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                            )
                        },
                        contentColors = MenuItemDefaults.menuItemColors(),
                    )
                }
            }
        }
    }
}
