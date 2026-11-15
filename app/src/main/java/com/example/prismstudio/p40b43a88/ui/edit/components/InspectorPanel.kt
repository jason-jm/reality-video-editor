package com.example.prismstudio.p40b43a88.ui.edit.components

import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.TrackRow
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.ParamKeyState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.model.Placement
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.SubtitleColor
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyleControl
import com.example.prismstudio.p40b43a88.domain.model.TitleAnimation
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.ui.components.ColorSwatch
import com.example.prismstudio.p40b43a88.ui.components.KeyframeUi
import com.example.prismstudio.p40b43a88.ui.components.TrackingSlider
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.components.tabMovesFocus
import com.example.prismstudio.p40b43a88.ui.edit.SpeedPointsUi
import com.example.prismstudio.p40b43a88.ui.edit.CompoundUi
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.edit.EffectRowUi
import com.example.prismstudio.p40b43a88.ui.edit.InspectorUi
import com.example.prismstudio.p40b43a88.ui.edit.SelectionUi
import com.example.prismstudio.p40b43a88.ui.edit.SubtitleFlag
import com.example.prismstudio.p40b43a88.ui.edit.TitleFlag
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.ButtonChip
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.Switch
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.design.ToggleIconButton
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.example.prismstudio.p40b43a88.domain.model.KeyScreen
import com.example.prismstudio.p40b43a88.domain.model.KeyControl
import androidx.compose.foundation.layout.size
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas

/** `inspector-column`: video / audio / speed / effects properties of the selected clip. */
@Composable
fun InspectorPanel(
    tab: Int,
    inspector: InspectorUi?,
    onTab: (Int) -> Unit,
    onEvent: (EditEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** Several clips selected: the batch panel instead of one clip's properties. */
    selection: SelectionUi? = null,
    canPaste: Boolean = false,
) {
    if (selection != null) {
        MultiSelectionPanel(selection, canPaste, onEvent, modifier)
        return
    }
    val multicam = inspector?.multicam
    if (multicam != null) {
        MulticamPanel(inspector, multicam, onEvent, modifier)
        return
    }
    val compound = inspector?.compound
    if (compound != null) {
        CompoundPanel(inspector, compound, onEvent, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentControl(Modifier.fillMaxWidth()) {
            listOf("视频", "音频", "变速", "特效").forEachIndexed { index, label ->
                SegmentItem(selected = index == tab, onClick = { onTab(index) }, title = { Text(label) }, modifier = Modifier.weight(1f))
            }
        }
        if (inspector == null) {
            Text("在时间线上选择一个片段以调整属性；长按片段可剪切、复制、粘贴；按住 Ctrl / Cmd 点按或开启「多选」可选择多个片段。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
            return@Column
        }
        if (!inspector.enabled) DisabledNotice(inspector.locked, onEvent)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tab) {
                0 -> VideoPanel(inspector, onEvent)
                1 -> AudioPanel(inspector, onEvent)
                2 -> SpeedPanel(inspector, onEvent)
                else -> {
                    TransitionInspector(inspector, onEvent)
                    EffectsPanel(inspector.effects, inspector.locked, onEvent)
                }
            }
        }
    }
}

/**
 * `compound-panel`: a compound clip in the inspector (Resolve) — what its timeline holds, 打开 to edit that timeline (every
 * clip of it follows) and 分解 to put its contents back in its place, and the sound part's volume. Its picture is adjusted
 * inside: opened, its clips have the usual inspector, Color page and mixer.
 */
@Composable
private fun CompoundPanel(inspector: InspectorUi, compound: CompoundUi, onEvent: (EditEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(compound.title, style = PrismType.title, color = PicoTheme.colorScheme.labelPrimary)
            // `compound-summary`
            Text(compound.summary, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
        }
        if (!inspector.enabled) DisabledNotice(inspector.locked, onEvent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // `compound-open-button`
            Button(
                onClick = { onEvent(EditEvent.OpenCompound(inspector.clipId)) },
                size = ButtonDefaults.Regular,
                modifier = Modifier.weight(1f).semantics { contentDescription = "打开复合片段 ${compound.name}".localized() },
            ) { Text("打开") }
            // `compound-decompose-button`
            Button(
                onClick = { onEvent(EditEvent.DecomposeCompound(inspector.clipId)) },
                enabled = !inspector.locked,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = "分解复合片段 ${compound.name}".localized() },
            ) { Text("分解") }
        }
        // `compound-volume-row`: the whole contents' sound, louder or softer.
        if (inspector.hasAudio) {
            ValueSliderRow(
                "音量", inspector.volumeDb, -60f..12f, formatValue(inspector.volumeDb, 1, true, " dB"), { onEvent(EditEvent.Volume(it)) },
                enabled = !inspector.locked, step = 0.5f,
            )
        }
        // `compound-hint`
        Text(
            "打开后可以剪辑里面的片段、调色和调整声音，改动会用到每个使用它的片段；分解把里面的片段放回时间线，替换它。",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
        )
    }
}

@Composable
private fun VideoPanel(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    // An animated clip is adjusted at the playhead, which must be inside it.
    val enabled = inspector.hasPicture && !inspector.locked && !inspector.transformKeys.blocked
    if (inspector.isSubtitle) {
        SubtitlePanel(inspector, onEvent)
        return
    }
    if (inspector.isTitle) {
        TextField(
            value = inspector.titleText,
            onValueChange = { onEvent(EditEvent.TitleText(it)) },
            placeholder = { Text("输入标题文字") },
            singleLine = true,
            clearable = true,
            enabled = !inspector.locked,
            modifier = Modifier.width(336.dp).semantics { contentDescription = "标题文字".localized() }.tabMovesFocus(),
        )
        ValueSliderRow("字号", inspector.titleSize.toFloat(), 24f..160f, "${inspector.titleSize}", { onEvent(EditEvent.TitleSize(it)) }, enabled = !inspector.locked, step = 1f)
        inspector.titleStyle?.let { TitleStyleBlock(it, inspector.titleAnimationNote, !inspector.locked, onEvent) }
    }
    if (!inspector.hasPicture) {
        Text("音频片段没有画面属性。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        return
    }
    if (!inspector.isTitle) LayoutSection(inspector, enabled, onEvent)
    if (!inspector.isTitle) ScalingSection(inspector, enabled, onEvent)
    KeyframeSection(
        keys = inspector.transformKeys,
        editable = !inspector.locked,
        onPrevious = { onEvent(EditEvent.PreviousKeyframe) },
        onToggle = { onEvent(EditEvent.ToggleKeyframe) },
        onNext = { onEvent(EditEvent.NextKeyframe) },
        onEase = { onEvent(EditEvent.ToggleEase(it)) },
        // The parameters have buttons of their own: this one keys them all.
        addLabel = "全部添加",
    )
    // Each value with its own keyframe button, as Resolve's inspector; position's X and Y share one group.
    val key = @Composable { group: TransformKeyGroup -> ParamKeyButton(group, inspector, onEvent) }
    ValueSliderRow("缩放", inspector.scale.toFloat(), 10f..400f, "${inspector.scale}%", { onEvent(EditEvent.Scale(it)) }, enabled = enabled, step = 1f, valueWidth = 56.dp, trailing = { key(TransformKeyGroup.SCALE) })
    ValueSliderRow("位置 X", inspector.positionX.toFloat(), -960f..960f, formatValue(inspector.positionX.toFloat(), 0, true), { onEvent(EditEvent.PositionX(it)) }, enabled = enabled, step = 1f, valueWidth = 56.dp, trailing = { key(TransformKeyGroup.POSITION) })
    ValueSliderRow("位置 Y", inspector.positionY.toFloat(), -540f..540f, formatValue(inspector.positionY.toFloat(), 0, true), { onEvent(EditEvent.PositionY(it)) }, enabled = enabled, step = 1f, valueWidth = 56.dp, trailing = { key(TransformKeyGroup.POSITION) })
    ValueSliderRow("旋转", inspector.rotation.toFloat(), -180f..180f, formatValue(inspector.rotation.toFloat(), 0, true, "°"), { onEvent(EditEvent.Rotation(it)) }, enabled = enabled, step = 1f, valueWidth = 56.dp, trailing = { key(TransformKeyGroup.ROTATION) })
    ValueSliderRow("不透明度", inspector.opacity.toFloat(), 0f..100f, "${inspector.opacity}%", { onEvent(EditEvent.Opacity(it)) }, enabled = enabled, step = 1f, valueWidth = 56.dp, trailing = { key(TransformKeyGroup.OPACITY) })
    if (inspector.pictureFades) PictureFadeRows(inspector, onEvent)
    // Titles draw their own text box over the finished picture; the composite mode, crop and Dynamic Zoom belong to pictures.
    if (!inspector.isTitle) {
        CompositeModeRow(inspector.composite, enabled, onEvent)
        if (inspector.keyable) KeySection(inspector, enabled, onEvent)
        CropSection(inspector, enabled, onEvent)
        MaskSection(inspector, enabled, onEvent)
        DynamicZoomSection(inspector.dynamicZoom, inspector.hasPicture && !inspector.locked, onEvent)
        inspector.stabilization?.let { StabilizationSection(it, !inspector.locked, onEvent) }
    }
}

/**
 * `picture-fade-rows`: the picture's fade handles (Resolve's), as on the timeline — its opacity up from nothing over 淡入
 * and down to nothing over 淡出: to black, or to what the tracks beneath show. Its sound fades apart, in the audio tab.
 */
@Composable
private fun PictureFadeRows(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    val enabled = !inspector.locked
    val max = inspector.maxPictureFade.coerceAtLeast(0.1f)
    ValueSliderRow(
        "淡入", inspector.pictureFadeIn, 0f..5f, formatValue(inspector.pictureFadeIn, 1, false, " 秒"),
        { onEvent(EditEvent.PictureFadeIn(it.coerceAtMost(max))) }, enabled = enabled, step = 0.1f,
    )
    ValueSliderRow(
        "淡出", inspector.pictureFadeOut, 0f..5f, formatValue(inspector.pictureFadeOut, 1, false, " 秒"),
        { onEvent(EditEvent.PictureFadeOut(it.coerceAtMost(max))) }, enabled = enabled, step = 0.1f,
    )
    FadeCurveRows(
        inCurve = inspector.pictureFadeInCurve, outCurve = inspector.pictureFadeOutCurve,
        inEnabled = enabled && inspector.pictureFadeIn > 0f, outEnabled = enabled && inspector.pictureFadeOut > 0f,
        onIn = { onEvent(EditEvent.PictureFadeInCurve(it)) }, onOut = { onEvent(EditEvent.PictureFadeOutCurve(it)) },
    )
}

/**
 * `picture-fade-curve-rows` / `fade-curve-rows`: how each fade bends (Resolve's fade curve dot, also dragged on the
 * timeline), −100…100 %: 0 straight, 上弯 in sooner and out later, 下弯 the other way. Off while its fade is nothing.
 */
@Composable
private fun FadeCurveRows(inCurve: Float, outCurve: Float, inEnabled: Boolean, outEnabled: Boolean, onIn: (Float) -> Unit, onOut: (Float) -> Unit) {
    ValueSliderRow("淡入曲线", inCurve * 100f, -100f..100f, ClipFades.curveLabel(inCurve), { onIn(it / 100f) }, enabled = inEnabled, step = 5f)
    ValueSliderRow("淡出曲线", outCurve * 100f, -100f..100f, ClipFades.curveLabel(outCurve), { onOut(it / 100f) }, enabled = outEnabled, step = 5f)
    Text(
        "上弯：淡入更快到位，淡出保持更久再落下；下弯相反。也可以拖时间线上淡化中间的圆点。",
        style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary,
    )
}

/**
 * `key-section`: Resolve's keyer — a green or blue screen keyed out so the tracks beneath show through, on the source
 * before the grade. 绿幕 / 蓝幕 start from the paint's colour and 吸取 picks the screen's own off the monitor; 相似度
 * widens what keys out, 平滑 softens its edge, 溢色抑制 takes the screen's tint off what stays; 查看遮罩 shows the matte.
 */
@Composable
private fun KeySection(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    val key = inspector.chromaKey
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("抠像", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text("去掉绿幕或蓝幕，露出下方轨道的画面", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        Switch(
            checked = key != null,
            onCheckedChange = { onEvent(EditEvent.ChromaKeyOn(it)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "抠像".localized() },
        )
    }
    if (key == null) return
    // `key-screen-row`: the screen's colour — a paint, or picked off the monitor (neither paint lit then).
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("幕布", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(40.dp), fit = true)
        KeyScreen.entries.forEach { screen ->
            ToggleableChip(
                label = { Text(screen.label) },
                isToggleOn = key.preset == screen,
                onClick = { onEvent(EditEvent.ChromaKeyScreen(screen)) },
                enabled = enabled,
                chipSize = ChipsDefaults.Small,
                modifier = Modifier.semantics { contentDescription = "抠像：${screen.label}".localized() },
            )
        }
        ToggleableChip(
            label = { Text("吸取") },
            isToggleOn = inspector.keyPicking,
            onClick = { onEvent(EditEvent.ChromaKeyEyedropper(!inspector.keyPicking)) },
            enabled = enabled,
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "吸取幕布颜色".localized() },
        )
        val fill = Color(0xFF000000.toInt() or key.color)
        Canvas(Modifier.size(20.dp).semantics { contentDescription = "抠像颜色 ${key.colorLabel}".localized() }) {
            drawCircle(fill)
            drawCircle(PrismColors.PictureBlack, style = Stroke(1.dp.toPx()), alpha = 0.4f)
        }
    }
    KeyControl.entries.filter { !it.finesse }.forEach { control -> KeyAmountRow(control, key, enabled, onEvent) }
    // `key-finesse-block`: Resolve's matte finesse — 去黑 clears a noisy screen's speckle, 去白 makes the subject solid,
    // 遮罩模糊 softens the matte's edge.
    Text("遮罩精修", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.padding(top = 4.dp))
    KeyControl.entries.filter { it.finesse }.forEach { control -> KeyAmountRow(control, key, enabled, onEvent) }
    // `key-matte-row`: Resolve's highlight — the matte on the monitor while tuning.
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ToggleableChip(
            label = { Text("查看遮罩") },
            isToggleOn = inspector.keyMatte,
            onClick = { onEvent(EditEvent.ChromaKeyMatte(!inspector.keyMatte)) },
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "查看抠像遮罩".localized() },
        )
        Text("白色保留，黑色被抠掉", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

/** One of the keyer's amounts, 0…100. */
@Composable
private fun KeyAmountRow(control: KeyControl, key: ChromaKey, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    ValueSliderRow(
        control.label, control.read(key), 0f..100f, "${control.read(key).roundToInt()}",
        { onEvent(EditEvent.ChromaKeyAmount(control, it)) }, enabled = enabled, step = 1f,
    )
}

/**
 * `zoom-section`: Resolve's Dynamic Zoom — the picture pushes in, pulls out or pans from the clip's first frame to
 * its last, by 幅度 and with 缓动, on top of the transform. 水平 / 垂直 place the zoomed framing (Resolve's end box):
 * the side or corner a push heads for, or a pan travels to; the diagram shows both boxes.
 */
@Composable
private fun DynamicZoomSection(zoom: DynamicZoom?, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("动态缩放", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text("从片段第一帧到最后一帧平滑推近、拉远或平移，适合照片", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        Switch(
            checked = zoom != null,
            onCheckedChange = { onEvent(EditEvent.DynamicZoomOn(it)) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "动态缩放".localized() },
        )
    }
    if (zoom == null) return
    StyleSegments("方向", ZoomDirection.entries, zoom.direction, { it.label }) { onEvent(EditEvent.DynamicZoomDirection(it)) }
    ValueSliderRow(
        "幅度",
        zoom.amountPercent.toFloat(),
        DynamicZoom.MIN_AMOUNT.toFloat()..DynamicZoom.MAX_AMOUNT.toFloat(),
        "${zoom.amountPercent}%",
        { onEvent(EditEvent.DynamicZoomAmount(it)) },
        enabled = enabled,
        step = 1f,
        labelWidth = 56.dp,
    )
    // `zoom-focus-x-row` / `zoom-focus-y-row`: where the zoomed framing sits.
    StyleSegments("水平", ZoomFocusX.entries, zoom.focusX, { it.label }) { onEvent(EditEvent.DynamicZoomFocusX(it)) }
    StyleSegments("垂直", ZoomFocusY.entries, zoom.focusY, { it.label }) { onEvent(EditEvent.DynamicZoomFocusY(it)) }
    StyleSegments("缓动", ZoomEase.entries, zoom.ease, { it.label }) { onEvent(EditEvent.DynamicZoomEase(it)) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomFramingDiagram(zoom, Modifier.width(112.dp).height(63.dp))
        // `zoom-motion-note`
        Column(Modifier.weight(1f)) {
            Text(zoomMotionText(zoom), style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary)
            Text("绿框是第一帧的画面，红框是最后一帧的", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
    }
}

/**
 * `crop-section`: Resolve's Cropping — each edge of the picture cut back in pixels of the 1920×1080 frame,
 * the cut edges softened; it scales, moves and turns with the picture, and 重置 clears it.
 */
@Composable
private fun CropSection(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("裁切", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text("按 1920×1080 画面的像素裁去各边，被裁去的部分露出下层轨道", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        // One keyframe button for the four edges and the softness.
        ParamKeyButton(TransformKeyGroup.CROP, inspector, onEvent)
        Button(
            onClick = { onEvent(EditEvent.ResetCrop) },
            enabled = enabled && inspector.hasCrop,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
            modifier = Modifier.semantics { contentDescription = "重置裁切".localized() },
        ) { Text("重置") }
    }
    CropControl.entries.forEach { control ->
        val value = inspector.crop[control.ordinal]
        ValueSliderRow(
            label = control.label,
            value = value.toFloat(),
            range = 0f..control.max.toFloat(),
            valueText = "$value",
            onValueChange = { onEvent(EditEvent.Crop(control, it)) },
            enabled = enabled,
            step = 1f,
        )
    }
}

/**
 * `title-style-block`: Resolve's Text title look — colour, weight, outline, drop shadow, the box behind the
 * text and its opacity — and how the title comes in, with what that entrance does.
 */
@Composable
private fun TitleStyleBlock(style: TitleStyle, animationNote: String, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("标题样式", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
                Text("颜色、字重、描边、阴影、背景与入场动画", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Button(
                onClick = { onEvent(EditEvent.ResetTitleStyle) },
                enabled = enabled && !style.isTemplateLook,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("重置") }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("颜色", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.widthIn(min = 56.dp))
            TitleColor.entries.forEach { color ->
                ColorSwatch(
                    PrismColors.title(color),
                    "标题颜色：${color.label}",
                    style.color == color,
                    onClick = { onEvent(EditEvent.TitleStyleColor(color)) },
                    plate = PrismColors.titlePlate(color),
                    enabled = enabled,
                )
            }
        }
        StyleSegments("字重", TitleWeight.entries, style.weight, { it.label }) { onEvent(EditEvent.TitleStyleWeight(it)) }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(TitleFlag.OUTLINE to style.outline, TitleFlag.SHADOW to style.shadow, TitleFlag.BOX to style.box).forEach { (flag, on) ->
                Text(flag.label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(
                    checked = on,
                    onCheckedChange = { onEvent(EditEvent.TitleStyleFlag(flag, it)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "标题${flag.label}".localized() },
                )
            }
        }
        ValueSliderRow(
            "背景",
            style.boxOpacityPercent.toFloat(),
            0f..100f,
            "${style.boxOpacityPercent}%",
            { onEvent(EditEvent.TitleBoxOpacity(it)) },
            enabled = enabled && style.box,
            step = 1f,
            labelWidth = 56.dp,
        )
        StyleSegments("动画", TitleAnimation.entries, style.animation, { it.label }) { onEvent(EditEvent.TitleStyleAnimation(it)) }
        Text(animationNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(start = 64.dp))
    }
}

/** A labelled segment control picking one of [options] (a title's weight, its entrance). */
@Composable
private fun <T> StyleSegments(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(56.dp), fit = true)
        SegmentControl(Modifier.weight(1f).semantics { contentDescription = label.localized() }) {
            options.forEach { option ->
                SegmentItem(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    title = { Text(name(option)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                )
            }
        }
    }
}

/**
 * `subtitle-block`: a subtitle's text and size, and 下一条字幕 — the next two seconds right after it, so
 * dialogue can be typed line by line.
 */
@Composable
private fun SubtitlePanel(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    TextField(
        value = inspector.titleText,
        onValueChange = { onEvent(EditEvent.TitleText(it)) },
        placeholder = { Text("输入字幕") },
        singleLine = true,
        clearable = true,
        enabled = !inspector.locked,
        modifier = Modifier.width(336.dp).semantics { contentDescription = "字幕文字".localized() }.tabMovesFocus(),
    )
    Button(
        onClick = { onEvent(EditEvent.NextSubtitle) },
        enabled = !inspector.locked,
        size = ButtonDefaults.Regular,
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.fillTertiary,
            contentColor = PicoTheme.colorScheme.labelPrimary,
        ),
    ) { Text("下一条字幕") }
    Text("字幕显示在画面底部；导出时会烧录进画面，也可另存 SRT。", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    inspector.subtitleStyle?.let { SubtitleStyleBlock(it, inspector.subtitleTrackName, !inspector.locked, onEvent) }
}

/**
 * `subtitle-style-block`: Resolve's subtitle Track Style — size, colour, weight, outline, the dark box and
 * its opacity, and the distance from the bottom, for every subtitle on the track.
 */
@Composable
private fun SubtitleStyleBlock(style: SubtitleStyle, trackName: String, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("轨道样式 · $trackName", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
                Text("作用于这条轨道上的全部字幕", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
            Button(
                onClick = { onEvent(EditEvent.ResetSubtitleStyle) },
                enabled = enabled && style != SubtitleStyle.DEFAULT,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("重置") }
        }
        StyleSlider(SubtitleStyleControl.SIZE, style, enabled, "${style.sizePx}", onEvent)
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("颜色", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.widthIn(min = 56.dp))
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleColor.entries.forEach { color ->
                    ToggleableChip(
                        label = { Text(color.label) },
                        isToggleOn = style.color == color,
                        onClick = { onEvent(EditEvent.SubtitleStyleColor(color)) },
                        enabled = enabled,
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "字幕颜色 ${color.label}".localized() },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(SubtitleFlag.BOLD to style.bold, SubtitleFlag.OUTLINE to style.outline, SubtitleFlag.BOX to style.box).forEach { (flag, on) ->
                Text(flag.label, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
                Switch(
                    checked = on,
                    onCheckedChange = { onEvent(EditEvent.SubtitleStyleFlag(flag, it)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "字幕${flag.label}".localized() },
                )
            }
        }
        StyleSlider(SubtitleStyleControl.BOX_OPACITY, style, enabled && style.box, "${style.boxOpacityPercent}%", onEvent)
        StyleSlider(SubtitleStyleControl.MARGIN, style, enabled, "${style.marginPercent}%", onEvent)
    }
}

@Composable
private fun StyleSlider(control: SubtitleStyleControl, style: SubtitleStyle, enabled: Boolean, valueText: String, onEvent: (EditEvent) -> Unit) {
    ValueSliderRow(
        control.label,
        control.read(style).toFloat(),
        control.min.toFloat()..control.max.toFloat(),
        valueText,
        { onEvent(EditEvent.SubtitleStyleValue(control, it)) },
        enabled = enabled,
        step = 1f,
        labelWidth = 56.dp,
    )
}

/** `clip-layout-row`: one-tap full frame / picture-in-picture corners, and moving to the other video track. */
@Composable
private fun LayoutSection(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("画面布局", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        // Toggle chips rather than a segment control: a hand-tuned transform matches no preset, and
        // SegmentControl cannot show "nothing selected".
        // A flow row: in a language with longer words ("Bottom right") the chips take a second line, never squeeze.
        FlowRow(
            Modifier.semantics { contentDescription = "画面布局".localized() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Placement.entries.forEachIndexed { index, placement ->
                ToggleableChip(
                    label = { Text(placement.label) },
                    isToggleOn = index == inspector.placementIndex,
                    onClick = { onEvent(EditEvent.Place(placement)) },
                    enabled = enabled,
                    chipSize = ChipsDefaults.Small,
                )
            }
        }
        inspector.trackMoveLabel?.let { label ->
            Button(
                onClick = { onEvent(EditEvent.MoveTrack(inspector.trackMoveUp)) },
                enabled = enabled,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text(label) }
        }
    }
}

/**
 * `clip-scaling-row`: Resolve's Scaling — how a picture of another shape or size than the timeline (a phone's 9:16
 * video, a 4:3 photo, a 720p clip) fills the frame: as the project's timeline settings say, fitted with bars, filled
 * and cropped, stretched, or at its own size; the note says what that does to this clip.
 */
@Composable
private fun ScalingSection(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("缩放方式", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        FlowRow(
            Modifier.semantics { contentDescription = "缩放方式".localized() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf(null) + ClipScaling.entries).forEach { scaling ->
                ToggleableChip(
                    label = { Text(scaling?.label ?: "按项目") },
                    isToggleOn = scaling == inspector.scaling,
                    onClick = { onEvent(EditEvent.Scaling(scaling)) },
                    enabled = enabled,
                    chipSize = ChipsDefaults.Small,
                )
            }
        }
        Text(inspector.scalingNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

/**
 * `clip-keyframe-row` / `volume-keyframe-row`: keyframes of the values below — jump between them, add
 * or remove one at the playhead. Once there are keyframes, `*-keyframe-ease-row` eases the motion into
 * or out of the keyframe at the playhead (toggle chips: both, one or neither side can ease).
 */
@Composable
private fun KeyframeSection(
    keys: KeyframeUi,
    editable: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onEase: (arriving: Boolean) -> Unit,
    addLabel: String = "添加关键帧",
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("关键帧", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(56.dp), fit = true)
            IconButton(onClick = onPrevious, enabled = keys.hasPrevious, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_player_previous), contentDescription = "上一个关键帧")
            }
            Button(
                onClick = onToggle,
                enabled = editable && keys.playheadInside,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text(if (keys.atKeyframe) "删除关键帧" else addLabel) }
            IconButton(onClick = onNext, enabled = keys.hasNext, size = IconButtonDefaults.Small) {
                Icon(painterResource(R.drawable.ic_player_next), contentDescription = "下一个关键帧")
            }
            Text(if (keys.count == 0) "无" else "${keys.count} 个", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        }
        if (keys.count > 0) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = "缓动".localized() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("缓动", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(56.dp), fit = true)
                ToggleableChip(
                    label = { Text("缓入") },
                    isToggleOn = keys.easeIn,
                    onClick = { onEase(true) },
                    enabled = editable && keys.canEaseIn,
                    chipSize = ChipsDefaults.Small,
                )
                ToggleableChip(
                    label = { Text("缓出") },
                    isToggleOn = keys.easeOut,
                    onClick = { onEase(false) },
                    enabled = editable && keys.canEaseOut,
                    chipSize = ChipsDefaults.Small,
                )
            }
        }
        Text(keys.hint, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

/**
 * `param-key-button`: Resolve's keyframe diamond beside a parameter — hollow without keys, marked when the group is keyed
 * elsewhere, filled with a key at the playhead; it adds the group's key there (the motion unchanged) or removes it.
 */
@Composable
private fun ParamKeyButton(group: TransformKeyGroup, inspector: InspectorUi, onEvent: (EditEvent) -> Unit) =
    KeyDiamond(inspector.paramKeys[group] ?: ParamKeyState.NONE, !inspector.locked && inspector.transformKeys.playheadInside, group.label) {
        onEvent(EditEvent.ToggleParamKey(group))
    }

/** A keyframe diamond for [label] in [state]: a press adds the keyframe at the playhead or removes the one there. */
@Composable
private fun KeyDiamond(state: ParamKeyState, enabled: Boolean, label: String, onToggle: () -> Unit) {
    ToggleIconButton(
        checked = state == ParamKeyState.AT_KEY,
        onCheckedChange = { onToggle() },
        enabled = enabled,
    ) {
        Icon(
            painterResource(
                when (state) {
                    ParamKeyState.AT_KEY -> R.drawable.ic_keyframe_fill
                    ParamKeyState.ANIMATED -> R.drawable.ic_keyframe_animated
                    ParamKeyState.NONE -> R.drawable.ic_keyframe
                },
            ),
            contentDescription = if (state == ParamKeyState.AT_KEY) "删除${label}关键帧" else "添加${label}关键帧",
        )
    }
}

/**
 * `mask-section`: the picture's mask — what Resolve does with a window feeding the node tree's alpha output. A shape (圆形,
 * 矩形, 渐变; 无 removes it) keeps the picture inside it and clears the rest, so the tracks beneath show; its position,
 * size, aspect, angle and soft edge follow, 反转 shows the outside instead, and the diamond keys its geometry at the
 * playhead. The monitor draws it and moves it by a drag.
 */
@Composable
private fun MaskSection(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    val mask = inspector.mask
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("遮罩", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
            Text("形状之外变为透明，露出下层轨道；遮罩随画面移动和缩放", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
        KeyDiamond(inspector.maskKey, enabled && mask != null && inspector.transformKeys.playheadInside, "遮罩") { onEvent(EditEvent.ToggleMaskKey) }
    }
    SegmentControl(Modifier.fillMaxWidth().semantics { contentDescription = "遮罩形状".localized() }) {
        val shapes = listOf(null, WindowShape.CIRCLE, WindowShape.RECTANGLE, WindowShape.GRADIENT)
        shapes.forEach { shape ->
            SegmentItem(
                selected = mask?.shape == shape,
                onClick = { if (enabled) onEvent(EditEvent.MaskShape(shape)) },
                title = { Text(shape?.label ?: "无") },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (mask != null) {
        listOf(WindowControl.CENTER_X, WindowControl.CENTER_Y, WindowControl.SIZE, WindowControl.ASPECT, WindowControl.ANGLE, WindowControl.SOFTNESS)
            .forEach { control ->
                val value = control.read(mask)
                ValueSliderRow(
                    label = control.label,
                    value = value,
                    range = control.min..control.max,
                    valueText = maskValueText(control, value),
                    onValueChange = { onEvent(EditEvent.MaskControl(control, it)) },
                    // A gradient has no size or aspect; keyed, the mask changes at the playhead, which must be inside the clip.
                    enabled = enabled && control.appliesTo(mask.shape) && !inspector.maskBlocked,
                    step = control.step,
                )
            }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("反转", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp), fit = true)
            Text("显示形状之外的画面", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
            Switch(
                checked = mask.invert,
                onCheckedChange = { onEvent(EditEvent.MaskInvert(it)) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "反转遮罩：显示形状之外的画面".localized() },
            )
        }
        // `mask-track-row`: Resolve's Tracker — the mask follows a moving subject, keyed along the way.
        TrackRow(
            inspector.maskTrack, enabled, { onEvent(EditEvent.TrackMask) }, { onEvent(EditEvent.CancelMaskTrack) },
            buttonLabel = "跟踪遮罩",
            buttonDescription = "跟踪遮罩：从播放头向前和向后跟随画面",
            idleHint = "从播放头向前、向后跟随遮罩里的画面，沿途添加关键帧",
            rowDescription = "遮罩跟踪",
            onMotion = { onEvent(EditEvent.SetMaskTrackMotion(it)) },
        )
    }
    Text(inspector.maskHint, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
}

private fun maskValueText(control: WindowControl, v: Float): String = when (control) {
    WindowControl.SIZE, WindowControl.ASPECT -> formatValue(v, 2, false)
    WindowControl.ANGLE -> formatValue(v, 0, true, "°")
    WindowControl.SOFTNESS -> formatValue(v * 100f, 0, false, "%")
    WindowControl.CENTER_X, WindowControl.CENTER_Y -> formatValue(v, 2, true)
}

/** `clip-disabled-notice`: the clip is switched off (Disable Clip) — it neither plays nor renders — with 启用. */
@Composable
private fun DisabledNotice(locked: Boolean, onEvent: (EditEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "片段已停用：不播放、不渲染（按 D 切换）",
            style = PrismType.label,
            color = PicoTheme.colorScheme.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onEvent(EditEvent.ToggleClipEnabled) },
            enabled = !locked,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) { Text("启用") }
    }
}

@Composable
private fun AudioPanel(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    val enabled = inspector.hasAudio && !inspector.locked
    if (!inspector.hasAudio) {
        Text("所选片段没有音频。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
    } else {
        KeyframeSection(
            keys = inspector.volumeKeys,
            editable = !inspector.locked,
            onPrevious = { onEvent(EditEvent.PreviousVolumeKeyframe) },
            onToggle = { onEvent(EditEvent.ToggleVolumeKeyframe) },
            onNext = { onEvent(EditEvent.NextVolumeKeyframe) },
            onEase = { onEvent(EditEvent.ToggleVolumeEase(it)) },
        )
    }
    // A keyed volume is adjusted at the playhead, which must be inside the clip.
    ValueSliderRow(
        "音量", inspector.volumeDb, -60f..12f, formatValue(inspector.volumeDb, 1, true, " dB"), { onEvent(EditEvent.Volume(it)) },
        enabled = enabled && !inspector.volumeKeys.blocked, step = 0.5f,
    )
    if (inspector.hasAudio) PanRow(inspector, enabled, onEvent)
    val maxFade = inspector.maxFade.coerceAtLeast(0.1f)
    ValueSliderRow("淡入", inspector.fadeIn, 0f..5f, formatValue(inspector.fadeIn, 1, false, " 秒"), { onEvent(EditEvent.FadeIn(it.coerceAtMost(maxFade))) }, enabled = enabled, step = 0.1f)
    ValueSliderRow("淡出", inspector.fadeOut, 0f..5f, formatValue(inspector.fadeOut, 1, false, " 秒"), { onEvent(EditEvent.FadeOut(it.coerceAtMost(maxFade))) }, enabled = enabled, step = 0.1f)
    FadeCurveRows(
        inCurve = inspector.fadeInCurve, outCurve = inspector.fadeOutCurve,
        inEnabled = enabled && inspector.fadeIn > 0f, outEnabled = enabled && inspector.fadeOut > 0f,
        onIn = { onEvent(EditEvent.FadeInCurve(it)) }, onOut = { onEvent(EditEvent.FadeOutCurve(it)) },
    )
    if (!inspector.hasAudio) return
    ChannelsRow(inspector, enabled, onEvent)
    PitchRow(inspector, enabled, onEvent)
    // `cross-fade-row`: Resolve's Cross Fade +3 dB from the sound that ends where this one starts, a frame at a time.
    ValueSliderRow(
        "交叉淡化",
        inspector.crossFadeFrames.toFloat(),
        0f..inspector.maxCrossFadeFrames.coerceAtLeast(1).toFloat(),
        inspector.crossFadeText,
        { onEvent(EditEvent.CrossFade(it)) },
        enabled = enabled && inspector.canCrossFade,
        step = 1f,
    )
    Text(inspector.crossFadeNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    ClipEqRow(inspector, enabled, onEvent)
}

/**
 * `clip-pan-row`: Resolve's inspector Pan — the sound placed left or right before the track's pan (a balance on stereo), its
 * diamond (`clip-pan-key-button`) keying it at the playhead so it can travel across; keyed, it changes at the playhead.
 */
@Composable
private fun PanRow(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    ValueSliderRow(
        "声像", inspector.pan, -1f..1f, inspector.panText, { onEvent(EditEvent.Pan(it)) },
        enabled = enabled && !inspector.panBlocked, step = 0.01f, valueWidth = 56.dp,
        modifier = Modifier.semantics { contentDescription = "声像".localized() },
        trailing = {
            KeyDiamond(inspector.panKey, enabled && !inspector.panBlocked, "声像") { onEvent(EditEvent.TogglePanKey) }
        },
    )
    if (inspector.panBlocked) {
        Text("播放头不在片段内：移进片段再调整声像", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

/**
 * `clip-channels-row`: Resolve's clip attributes for sound — how the file's left and right channels reach the mix: as they
 * are, swapped, one channel on both sides (a lavalier on one side), or mixed to mono; the note says what the choice does.
 */
@Composable
private fun ChannelsRow(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    // Five choices need the panel's whole width: the label above them.
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("声道", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary)
        SegmentControl(Modifier.fillMaxWidth()) {
            AudioChannels.entries.forEach { option ->
                SegmentItem(
                    selected = option == inspector.channels,
                    onClick = { if (enabled) onEvent(EditEvent.Channels(option)) },
                    title = { Text(option.label, maxLines = 1) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "声道：${option.label}".localized() },
                )
            }
        }
        // `clip-channels-note`
        Text(inspector.channelsNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
    }
}

/**
 * `clip-pitch-row`: Resolve's clip pitch — the sound shifted up or down to an octave either way, a tenth of a semitone at a
 * time, at its own length; the slider as wide as the panel's others. Under it the note naming the interval and 归零, which
 * takes it back to the recorded pitch.
 */
@Composable
private fun PitchRow(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    val semis = inspector.pitchSemitones
    ValueSliderRow(
        "音调", semis, -12f..12f, formatValue(semis, 1, true, " 半音"), { onEvent(EditEvent.Pitch(it)) },
        enabled = enabled, step = 0.1f, modifier = Modifier.semantics { contentDescription = "音调（半音）".localized() },
    )
    // `clip-pitch-note-row`
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // `clip-pitch-note`
        Text(inspector.pitchNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.weight(1f))
        ButtonChip(
            label = { Text("归零") },
            onClick = { onEvent(EditEvent.Pitch(0f)) },
            enabled = enabled && semis != 0f,
            chipSize = ChipsDefaults.Small,
            modifier = Modifier.semantics { contentDescription = "音调归零".localized() },
        )
    }
}

/**
 * `clip-eq-row`: Resolve's clip equaliser — the sound's own EQ, before its track's — what it does and 编辑, which opens the
 * same sheet the Audio page edits a track's EQ in.
 */
@Composable
private fun ClipEqRow(inspector: InspectorUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("均衡器", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp), fit = true)
        Text(
            inspector.eqSummary,
            style = PrismType.label,
            color = if (inspector.eqActive) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onEvent(EditEvent.OpenClipEq) },
            enabled = enabled,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
            modifier = Modifier.semantics { contentDescription = "编辑片段均衡器".localized() },
        ) { Text("编辑") }
    }
}

/**
 * `speed-points-block`: Resolve's speed points — the clip in sections that play at their own speed. The sections' speeds
 * in order; a point added at the playhead, the nearest removed, or all cleared (the clip back at 1×). With points, the
 * speed chips above set the section under the playhead, and `speed-ramp-row` smooths the point nearest it into a ramp.
 */
@Composable
private fun SpeedPointsBlock(points: SpeedPointsUi, enabled: Boolean, onEvent: (EditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("变速点", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
            // `speed-sections-readout`: each section's speed, the one under the playhead marked.
            if (points.sections.isNotEmpty()) {
                Text(
                    "${points.sections.size} 段：" + points.sections.mapIndexed { i, label -> if (i + 1 == points.current) "▸$label" else label }.joinToString(" · "),
                    style = PrismType.label,
                    color = PicoTheme.colorScheme.labelPrimary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonChip(
                label = { Text("在播放头处添加") },
                onClick = { onEvent(EditEvent.AddSpeedPoint) },
                enabled = enabled && points.canAdd,
                chipSize = ChipsDefaults.Small,
            )
            ButtonChip(
                label = { Text("删除最近的") },
                onClick = { onEvent(EditEvent.RemoveSpeedPoint) },
                enabled = enabled && points.canRemove,
                chipSize = ChipsDefaults.Small,
            )
            ButtonChip(
                label = { Text("清除") },
                onClick = { onEvent(EditEvent.ClearSpeedPoints) },
                enabled = enabled && points.canRemove,
                chipSize = ChipsDefaults.Small,
            )
        }
        Text(points.hint, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        if (points.rampPoint > 0) {
            // `speed-ramp-row`: Resolve's speed ramp — the nearest point's change eased over this long, centred on it.
            ValueSliderRow(
                "平滑过渡",
                points.rampSeconds,
                0f..points.rampMaxSeconds.coerceAtLeast(RAMP_STEP_SECONDS),
                points.rampText,
                { onEvent(EditEvent.SpeedRamp(it)) },
                enabled = enabled && points.rampMaxSeconds > 0f,
                step = RAMP_STEP_SECONDS,
            )
            Text(points.rampNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        }
    }
}

/** The ramp slider moves in tenths of a second. */
private const val RAMP_STEP_SECONDS = 0.1f

@Composable
private fun SpeedPanel(inspector: InspectorUi, onEvent: (EditEvent) -> Unit) {
    val enabled = inspector.canChangeSpeed && !inspector.locked
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // `speed-chip-row`: toggle chips rather than a segment control — a Fit to Fill speed (1.33×) is
        // none of the presets, and SegmentControl cannot show "nothing selected".
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("播放速度", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
            // `speed-readout`: the clip's speed, also when it is between the presets.
            Text("当前 ${inspector.speedLabel}", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary)
        }
        FlowRow(Modifier.semantics { contentDescription = "播放速度".localized() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaybackSpeed.entries.forEachIndexed { index, speed ->
                ToggleableChip(
                    label = { Text(speed.label) },
                    isToggleOn = index == inspector.speedIndex,
                    onClick = { onEvent(EditEvent.Speed(index)) },
                    enabled = enabled,
                    chipSize = ChipsDefaults.Small,
                )
            }
        }
        SpeedPointsBlock(inspector.speedPoints, enabled = !inspector.locked, onEvent)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("倒放", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
            Switch(
                checked = inspector.reverse,
                onCheckedChange = { onEvent(EditEvent.Reverse(it)) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "倒放".localized() },
            )
        }
        // `reverse-status`: the reversed copy the clip plays is still being made, or failed.
        inspector.reverseStatus?.let { status ->
            Text(
                status,
                style = PrismType.caption,
                color = if (inspector.reverseFailed) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelSecondary,
            )
        }
        // `retime-row`: Resolve's Retime Process — how the frames a slowed clip or slower footage lacks are made.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("变速处理", style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.weight(1f))
            FlowRow(Modifier.semantics { contentDescription = "变速处理".localized() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RetimeProcess.entries.forEach { process ->
                    ToggleableChip(
                        label = { Text(process.label) },
                        isToggleOn = process == inspector.retime,
                        onClick = { onEvent(EditEvent.Retime(process)) },
                        enabled = inspector.canRetime && !inspector.locked,
                        chipSize = ChipsDefaults.Small,
                    )
                }
            }
        }
        Text(inspector.retimeNote, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        Text("变速后片段时长自动调整，后面的片段随之移动。", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
        // `freeze-frame-button`: CapCut's 定格 — the frame at the playhead held for two seconds.
        Button(
            onClick = { onEvent(EditEvent.FreezeFrame) },
            enabled = inspector.canFreeze && !inspector.locked,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) { Text("在播放头处定格 2 秒") }
    }
}

@Composable
private fun EffectsPanel(effects: List<EffectRowUi>, locked: Boolean, onEvent: (EditEvent) -> Unit) {
    if (effects.isEmpty()) {
        Text("在左侧“特效”库选择效果，然后点“应用到所选片段”。", style = PrismType.label, color = PicoTheme.colorScheme.labelTertiary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        effects.forEach { effect ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(effect.name, style = PrismType.body, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = effect.enabled,
                        onCheckedChange = { onEvent(EditEvent.EffectEnabled(effect.id, it)) },
                        enabled = !locked,
                        modifier = Modifier.semantics { contentDescription = "启用效果".localized() },
                    )
                    IconButton(onClick = { onEvent(EditEvent.RemoveEffect(effect.id)) }, enabled = !locked, size = IconButtonDefaults.Small) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "移除效果")
                    }
                }
                // `effect-keyframe-row`: the amount at the playhead, and Resolve's keyframe diamond with the arrows either side —
                // filled at a keyframe; once the effect has keyframes a change at the playhead keys it there.
                val keys = effect.keys
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackingSlider(
                        value = effect.amount.toFloat(),
                        onValueChange = { onEvent(EditEvent.EffectAmount(effect.id, it)) },
                        valueRange = 0f..100f,
                        enabled = effect.enabled && !locked && !keys.blocked,
                        sliderSpec = SliderDefaults.Small,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "效果强度".localized() },
                    )
                    IconButton(onClick = { onEvent(EditEvent.PreviousEffectKeyframe(effect.id)) }, enabled = keys.hasPrevious, size = IconButtonDefaults.Small) {
                        Icon(painterResource(R.drawable.ic_player_previous), contentDescription = "上一个${effect.name}关键帧")
                    }
                    ToggleIconButton(
                        checked = keys.atKeyframe,
                        onCheckedChange = { onEvent(EditEvent.ToggleEffectKeyframe(effect.id)) },
                        enabled = !locked && keys.playheadInside,
                    ) {
                        Icon(
                            painterResource(if (keys.atKeyframe) R.drawable.ic_keyframe_fill else R.drawable.ic_keyframe),
                            contentDescription = if (keys.atKeyframe) "删除${effect.name}关键帧" else "添加${effect.name}关键帧",
                        )
                    }
                    IconButton(onClick = { onEvent(EditEvent.NextEffectKeyframe(effect.id)) }, enabled = keys.hasNext, size = IconButtonDefaults.Small) {
                        Icon(painterResource(R.drawable.ic_player_next), contentDescription = "下一个${effect.name}关键帧")
                    }
                }
                if (keys.count > 0) Text(keys.hint, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary)
            }
        }
    }
}
