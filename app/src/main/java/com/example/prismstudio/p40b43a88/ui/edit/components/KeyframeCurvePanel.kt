package com.example.prismstudio.p40b43a88.ui.edit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import com.example.prismstudio.p40b43a88.domain.model.CurveEase
import com.example.prismstudio.p40b43a88.domain.model.KeyCurve
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.ui.edit.CurvesUi
import com.example.prismstudio.p40b43a88.ui.edit.EditEvent
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.example.prismstudio.p40b43a88.ui.components.ToggleableChip
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * `keyframe-curve-panel`: Resolve's Keyframe Curve Editor in the timeline panel — the selected clip's curves on the left
 * (each with its value at the playhead and its key count), the picked one drawn across the clip's length on the right,
 * with the toolbar for the selected key: what it holds, its ease presets, adding a key at the playhead and deleting it.
 */
@Composable
fun KeyframeCurvePanel(curves: CurvesUi, playheadFrame: Int, onEvent: (EditEvent) -> Unit, modifier: Modifier = Modifier) {
    val hint = curves.hint
    if (hint != null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(hint, style = PrismType.body, color = PicoTheme.colorScheme.labelTertiary)
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // `curve-param-list`
        Column(Modifier.width(248.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(curves.clipName, style = PrismType.label, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            curves.rows.forEach { row ->
                val fill = if (row.selected) PicoTheme.colorScheme.fillSecondary else androidx.compose.ui.graphics.Color.Transparent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .spatialHoverEffect()
                        .clickable { onEvent(EditEvent.CurvePicked(row.key)) }
                        .drawBehind { drawRect(fill) }
                        .padding(horizontal = 12.dp)
                        .semantics { contentDescription = spoken("${row.label.localized()} ${row.value}", "${row.keyCount} 个关键帧".localized()) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.label,
                        style = PrismType.label,
                        color = if (row.animated) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(row.value, style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
                    Text(if (row.keyCount > 0) "◆${row.keyCount}" else "", style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // `curve-toolbar`
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(curves.readout, style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CurveEase.entries.forEach { e ->
                    ToggleableChip(
                        label = { Text(e.label) },
                        isToggleOn = curves.ease == e,
                        onClick = { onEvent(EditEvent.CurveEasePicked(e)) },
                        enabled = curves.canEase,
                        chipSize = ChipsDefaults.Small,
                        modifier = Modifier.semantics { contentDescription = "关键帧缓动：${e.label}".localized() },
                    )
                }
                Button(
                    onClick = { onEvent(EditEvent.CurveAddKey) },
                    enabled = curves.canAdd,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                ) { Text("添加关键帧") }
                Button(
                    onClick = { onEvent(EditEvent.CurveDeleteKey) },
                    enabled = curves.canDelete,
                    size = ButtonDefaults.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                ) { Text("删除关键帧") }
            }
            curves.sharedNote?.let { Text(it, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            curves.curve?.let { curve ->
                CurveGraph(
                    curve = curve,
                    durationFrames = curves.durationFrames,
                    fps = curves.fps,
                    selectedKey = curves.selectedKey,
                    playhead = playheadFrame - curves.clipStart,
                    onEvent = onEvent,
                    onSeek = { local -> onEvent(EditEvent.Seek(curves.clipStart + local)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/**
 * `keyframe-curve-graph`: one curve across the clip's frames. Keys are dots; the selected one shows its control points a
 * third of the way to each neighbour. A press on a key selects it and dragging moves it (in time and value, between its
 * neighbours); dragging a control point sets that side's slope; a tap elsewhere moves the playhead there. The value range
 * fits the curve, and holds while a drag lasts.
 */
@Composable
private fun CurveGraph(
    curve: KeyCurve,
    durationFrames: Int,
    fps: Int,
    selectedKey: Int?,
    playhead: Int,
    onEvent: (EditEvent) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(curve)
    val selected by rememberUpdatedState(selectedKey)
    val send by rememberUpdatedState(onEvent)
    val seek by rememberUpdatedState(onSeek)
    // The range a drag started with, held until it ends (the curve refits afterwards).
    var held by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var drags by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val line = PicoTheme.colorScheme.labelPrimaryLight
    val accent = PicoTheme.colorScheme.interaction
    val grid = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.16f)
    val faint = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.55f)
    val labelStyle = PrismType.caption.copy(color = faint)
    val frames = durationFrames.coerceAtLeast(1)
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "${curve.label}关键帧曲线：拖动关键帧改时间和数值，拖动控制点改快慢，点按空白处移动播放头".localized() }
            .pointerInput(frames) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val axes = CurveAxes(size.width.toFloat(), size.height.toFloat(), frames, held ?: fitRange(current), 16.dp.toPx())
                    val reach = 28.dp.toPx()
                    fun distance(f: Float, v: Float) = hypot(axes.x(f) - down.position.x, axes.y(v) - down.position.y)
                    // The selected key's control points first, then the keys, else a tap on the graph.
                    val sel = selected
                    val handle = sel?.let { k ->
                        listOfNotNull(current.handleIn(k)?.let { true to it }, current.handleOut(k)?.let { false to it })
                            .minByOrNull { (_, p) -> distance(p.first, p.second) }?.takeIf { (_, p) -> distance(p.first, p.second) < reach }
                    }
                    val key = if (handle != null) null else current.keys.indices.minByOrNull { distance(current.keys[it].frame.toFloat(), current.keys[it].value) }
                        ?.takeIf { distance(current.keys[it].frame.toFloat(), current.keys[it].value) < reach }
                    down.consume()
                    if (handle == null && key == null) {
                        // A tap: the playhead goes there when the press lets go without travelling.
                        var travelled = false
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (abs(c.position.x - down.position.x) > 12.dp.toPx()) travelled = true
                            if (!c.pressed) break
                        }
                        if (!travelled) seek(axes.frame(down.position.x))
                        return@awaitEachGesture
                    }
                    held = axes.range
                    val drag = ++drags
                    if (key != null && key != sel) send(EditEvent.CurveKeyPicked(key))
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if (c.position == c.previousPosition) continue
                        c.consume()
                        if (key != null) {
                            send(EditEvent.CurveKeyMoved(key, axes.frame(c.position.x), axes.value(c.position.y), drag))
                        } else if (handle != null && sel != null) {
                            val arriving = handle.first
                            val from = current.keys[sel].value
                            val to = current.keys.getOrNull(if (arriving) sel - 1 else sel + 1)?.value ?: continue
                            // Either side: the control point sits at this key + slope · (neighbour − this key) / 3.
                            KeyCurve.slopeFor(from, to, axes.value(c.position.y))?.let { send(EditEvent.CurveSlope(sel, arriving, it, drag)) }
                        }
                    }
                    held = null
                }
            },
    ) {
        val axes = CurveAxes(size.width, size.height, frames, held ?: fitRange(curve), 16.dp.toPx())
        drawRect(PrismColors.PictureBlack)
        // Value grid: four steps over the range, labelled at the left.
        for (i in 0..4) {
            val v = axes.range.first + (axes.range.second - axes.range.first) * i / 4f
            val y = axes.y(v)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawText(measurer.measure(curve.format(v), labelStyle), topLeft = Offset(4.dp.toPx(), y - 16.dp.toPx()))
        }
        // Time grid: a line a second, labelled with the clip's own time.
        val stepFrames = max(1, fps) * secondsStep(frames / fps.coerceAtLeast(1).toFloat())
        var f = 0
        while (f < frames) {
            val x = axes.x(f.toFloat())
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawText(measurer.measure(Timecode.format(f, fps).substringAfter(':'), labelStyle), topLeft = Offset(x + 4.dp.toPx(), size.height - 18.dp.toPx()))
            f += stepFrames
        }
        // The curve, 2 px per point: held before the first key and after the last (dashed there).
        val keys = curve.keys
        val path = Path()
        val steps = (size.width / 2f).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val frame = (frames - 1) * i / steps.toFloat()
            val p = Offset(axes.x(frame), axes.y(curve.valueAt(frame)))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        if (keys.size < 2) {
            drawPath(path, faint, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        } else {
            drawPath(path, line, style = Stroke(2.dp.toPx()))
        }
        // The playhead, where it is inside the clip.
        if (playhead in 0 until frames) {
            val x = axes.x(playhead.toFloat())
            drawLine(PrismColors.Playhead, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
        // The selected key's control points.
        selectedKey?.takeIf { it in keys.indices }?.let { k ->
            val at = Offset(axes.x(keys[k].frame.toFloat()), axes.y(keys[k].value))
            listOfNotNull(curve.handleIn(k), curve.handleOut(k)).forEach { (hf, hv) ->
                val p = Offset(axes.x(hf), axes.y(hv))
                drawLine(accent, at, p, strokeWidth = 1.5f.dp.toPx())
                drawCircle(accent, radius = 6.dp.toPx(), center = p)
            }
        }
        keys.forEachIndexed { i, key ->
            val p = Offset(axes.x(key.frame.toFloat()), axes.y(key.value))
            if (i == selectedKey) {
                drawCircle(accent, radius = 10.dp.toPx(), center = p)
                drawCircle(line, radius = 5.dp.toPx(), center = p)
            } else {
                drawCircle(line, radius = 7.dp.toPx(), center = p)
            }
        }
    }
}

/** The value range the graph shows: the keys and control points with room around them, at least a few units tall. */
private fun fitRange(curve: KeyCurve): Pair<Float, Float> {
    val values = curve.keys.map { it.value } + curve.keys.indices.flatMap { listOfNotNull(curve.handleIn(it)?.second, curve.handleOut(it)?.second) } + curve.base
    var lo = values.min()
    var hi = values.max()
    val minSpan = if (curve.decimals > 0) 2f else 4f
    if (hi - lo < minSpan) {
        val mid = (hi + lo) / 2
        lo = mid - minSpan / 2
        hi = mid + minSpan / 2
    }
    val pad = (hi - lo) * 0.15f
    return (lo - pad).coerceAtLeast(curve.min) to (hi + pad).coerceAtMost(curve.max)
}

/** Seconds between time grid lines: 1, 2, 5 or 10, so a long clip keeps a readable grid. */
private fun secondsStep(seconds: Float): Int = when {
    seconds <= 12f -> 1
    seconds <= 30f -> 2
    seconds <= 80f -> 5
    else -> 10
}

/** Frames across [width] (inset [pad] each side), values from [range] up a graph [height] tall (inset the same). */
private class CurveAxes(val width: Float, val height: Float, val frames: Int, val range: Pair<Float, Float>, val pad: Float) {
    private val span = (range.second - range.first).takeIf { it > 0f } ?: 1f

    fun x(frame: Float): Float = pad + frame / (frames - 1).coerceAtLeast(1) * (width - 2 * pad)
    fun frame(x: Float): Int = ((x - pad) / (width - 2 * pad) * (frames - 1)).roundToInt().coerceIn(0, frames - 1)
    fun y(value: Float): Float = height - pad - (value - range.first) / span * (height - 2 * pad)
    fun value(y: Float): Float = range.first + (height - pad - y) / (height - 2 * pad) * span
}
