package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.selected
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.HueCurves
import com.example.prismstudio.p40b43a88.domain.usecase.HueSpline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurveChannel
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.PrimaryControl
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.usecase.BuildGradeLutUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ToneSpline
import com.example.prismstudio.p40b43a88.ui.color.LookPreviewUi
import com.example.prismstudio.p40b43a88.ui.components.ValueSliderRow
import com.example.prismstudio.p40b43a88.ui.components.formatValue
import com.example.prismstudio.p40b43a88.ui.components.rememberFrameImage
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.example.prismstudio.p40b43a88.ui.components.SegmentItem
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.menu.MenuItemDefaults
import com.pico.spatial.ui.design.menu.rememberMenuPositionProvider
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset
import kotlin.math.abs

/** `primaries-panel`: ten primary sliders in a two-column grid (rev 127: Resolve's contrast pivot 支点 and Color Boost 色彩增强). */
@Composable
fun PrimariesPanel(grade: ColorGrade, enabled: Boolean, onChange: (PrimaryControl, Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrimaryControl.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { control ->
                    ValueSliderRow(
                        label = control.label,
                        value = control.read(grade),
                        range = control.min..control.max,
                        valueText = valueText(control, control.read(grade)),
                        onValueChange = { onChange(control, it) },
                        enabled = enabled,
                        step = control.step,
                        minSliderWidth = 110.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun valueText(control: PrimaryControl, v: Float): String = when (control) {
    PrimaryControl.EXPOSURE -> formatValue(v, 2, true, " EV")
    PrimaryControl.CONTRAST, PrimaryControl.SATURATION -> formatValue(v, 2, false)
    PrimaryControl.HUE -> formatValue(v, 0, true, "°")
    PrimaryControl.PIVOT -> formatValue(v, 3, false)
    else -> formatValue(v, 0, true)
}

/** `curves-panel`: the curve — a custom YRGB channel or one of Resolve's hue curves — reset, and its editor. */
@Composable
fun CurvesPanel(
    curves: ToneCurves,
    channel: CurveChannel,
    hueCurves: HueCurves,
    hueCurve: HueCurve?,
    enabled: Boolean,
    /** A curve picked: 0–3 the custom channels, then the hue curves in [HueCurve] order. */
    onPick: (Int) -> Unit,
    onReset: () -> Unit,
    onAdd: (Float, Float) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onRemove: (Int) -> Unit,
    onHueAdd: (Float) -> Unit,
    onHueMove: (Int, Float, Float) -> Unit,
    onHueRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // `curve-channel-segment`: every curve one tap away — the custom luma / R / G / B curves, then the hue
            // curves. A fixed width, as a segment control fills whatever it is given.
            val picked = hueCurve?.let { CurveChannel.entries.size + it.ordinal } ?: channel.ordinal
            SegmentControl(Modifier.width(640.dp).semantics { contentDescription = "曲线".localized() }) {
                (CurveChannel.entries.map { it.label } + HueCurve.entries.map { it.short }).forEachIndexed { index, label ->
                    SegmentItem(
                        selected = index == picked,
                        onClick = { onPick(index) },
                        title = { Text(label) },
                        modifier = Modifier.weight(if (index < CurveChannel.entries.size) 2f else 3f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onReset,
                enabled = enabled,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillTertiary,
                    contentColor = PicoTheme.colorScheme.labelPrimary,
                ),
            ) { Text("重置曲线") }
        }
        val editor = Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp)
        if (hueCurve == null) {
            CurveEditor(curves, channel, enabled, onAdd, onMove, onRemove, editor)
        } else {
            HueCurveEditor(hueCurves[hueCurve], hueCurve, enabled, onHueAdd, onHueMove, onHueRemove, editor)
        }
    }
}

/**
 * The `curve-editor` renderer: tap the curve to add a point, drag points (56 dp hit area) to bend
 * it, double-tap a point to remove it. Other channels are drawn faintly behind the active one.
 */
@Composable
fun CurveEditor(
    curves: ToneCurves,
    channel: CurveChannel,
    enabled: Boolean,
    onAdd: (Float, Float) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points by rememberUpdatedState(curves[channel])
    val add by rememberUpdatedState(onAdd)
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    val grid = PicoTheme.colorScheme.labelQuaternary
    val handle = PicoTheme.colorScheme.labelPrimaryLight
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "曲线：${channel.label}".localized() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { p ->
                    val inset = 12.dp.toPx()
                    val w = size.width - 2 * inset
                    val h = size.height - 2 * inset
                    val index = nearest(points, (p.x - inset) / w, 1f - (p.y - inset) / h, 28.dp.toPx() / w)
                    if (index >= 0) remove(index)
                })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val inset = 12.dp.toPx()
                    val w = size.width - 2 * inset
                    val h = size.height - 2 * inset
                    fun nx(x: Float) = ((x - inset) / w).coerceIn(0f, 1f)
                    fun ny(y: Float) = (1f - (y - inset) / h).coerceIn(0f, 1f)
                    val before = points
                    var index = nearest(points, nx(down.position.x), ny(down.position.y), 28.dp.toPx() / w)
                    if (index < 0) {
                        val x = nx(down.position.x)
                        add(x, BuildGradeLutUseCase.evalCurve(points, x))
                    }
                    val grabX = nx(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if ((c.position - down.position).getDistance() < viewConfiguration.touchSlop && index >= 0) continue
                        c.consume()
                        if (index < 0) index = addedAt(points, before, grabX)
                        if (index >= 0) move(index, nx(c.position.x), ny(c.position.y))
                    }
                }
            },
    ) {
        drawRect(PrismColors.PictureBlack)
        val inset = 12.dp.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        for (i in 0..4) {
            val x = inset + w * i / 4f
            val y = inset + h * i / 4f
            drawLine(grid, Offset(x, inset), Offset(x, inset + h), strokeWidth = 1f)
            drawLine(grid, Offset(inset, y), Offset(inset + w, y), strokeWidth = 1f)
        }
        fun curvePath(list: List<CurvePoint>): Path = Path().apply {
            val steps = 64
            val spline = ToneSpline(list)
            for (s in 0..steps) {
                val x = s / steps.toFloat()
                val y = spline.eval(x).coerceIn(0f, 1f)
                val px = inset + x * w
                val py = inset + (1f - y) * h
                if (s == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        CurveChannel.entries.filter { it != channel }.forEach { other ->
            drawPath(curvePath(curves[other]), curveColor(other), style = Stroke(1.dp.toPx()), alpha = 0.25f)
        }
        drawPath(curvePath(curves[channel]), curveColor(channel), style = Stroke(2.dp.toPx()))
        curves[channel].forEach { p ->
            drawCircle(handle, radius = 8.dp.toPx(), center = Offset(inset + p.x * w, inset + (1f - p.y) * h), alpha = if (enabled) 1f else 0.4f)
        }
    }
}

/**
 * The `curve-editor` renderer for a hue curve: hue runs along the bottom strip (red to red; luma from black
 * to white for luma vs sat), the middle line changes nothing, and the scale's ends are marked. A tap adds a
 * point with its anchors, a drag moves one, a double tap removes it.
 */
@Composable
fun HueCurveEditor(
    points: List<CurvePoint>,
    curve: HueCurve,
    enabled: Boolean,
    onAdd: (Float) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(points)
    val add by rememberUpdatedState(onAdd)
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    val grid = PicoTheme.colorScheme.labelQuaternary
    val handle = PicoTheme.colorScheme.labelPrimaryLight
    val labels = rememberTextMeasurer()
    val caption = PrismType.caption.copy(color = PicoTheme.colorScheme.labelPrimaryLight)
    val (top, bottom) = when (curve) {
        HueCurve.HUE_HUE -> "+${HueCurve.MAX_HUE_SHIFT.toInt()}°" to "−${HueCurve.MAX_HUE_SHIFT.toInt()}°"
        HueCurve.HUE_LUM -> "+1 档" to "−1 档"
        HueCurve.HUE_SAT, HueCurve.LUM_SAT -> "×2" to "×0"
    }
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "曲线：${curve.label}".localized() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { p ->
                    val inset = 12.dp.toPx()
                    val w = size.width - 2 * inset
                    val h = size.height - 2 * inset - STRIP.toPx()
                    val index = nearest(current, (p.x - inset) / w, 1f - (p.y - inset) / h, 28.dp.toPx() / w)
                    if (index >= 0) remove(index)
                })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val inset = 12.dp.toPx()
                    val w = size.width - 2 * inset
                    val h = size.height - 2 * inset - STRIP.toPx()
                    fun nx(x: Float) = ((x - inset) / w).coerceIn(0f, 1f)
                    fun ny(y: Float) = (1f - (y - inset) / h).coerceIn(0f, 1f)
                    val before = current
                    var index = nearest(current, nx(down.position.x), ny(down.position.y), 28.dp.toPx() / w)
                    val grabX = nx(down.position.x)
                    if (index < 0) add(grabX)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if ((c.position - down.position).getDistance() < viewConfiguration.touchSlop && index >= 0) continue
                        c.consume()
                        // The tap's own point: the anchors sit a sixth of the way either side of it.
                        if (index < 0) index = addedAt(current, before, grabX)
                        if (index >= 0) move(index, nx(c.position.x), ny(c.position.y))
                    }
                }
            },
    ) {
        drawRect(PrismColors.PictureBlack)
        val inset = 12.dp.toPx()
        val strip = STRIP.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset - strip
        for (i in 0..6) {
            val x = inset + w * i / 6f
            drawLine(grid, Offset(x, inset), Offset(x, inset + h), strokeWidth = 1f)
        }
        for (i in 0..4) {
            val y = inset + h * i / 4f
            drawLine(grid, Offset(inset, y), Offset(inset + w, y), strokeWidth = if (i == 2) 2f else 1f)
        }
        // What runs along x: hue round the wheel, or luma.
        val stops = if (curve.periodic) (0..12).map { Color.hsv((it * 30f) % 360f, 0.85f, 0.95f) } else listOf(Color(0f, 0f, 0f), Color(1f, 1f, 1f))
        drawRect(Brush.horizontalGradient(stops, startX = inset, endX = inset + w), Offset(inset, inset + h + strip * 0.25f), Size(w, strip * 0.75f))
        drawText(labels, top.localized(), Offset(inset + 4.dp.toPx(), inset), caption)
        drawText(labels, bottom.localized(), Offset(inset + 4.dp.toPx(), inset + h - 16.dp.toPx()), caption)
        // `curve-hint`: how to edit, in the editor's top right corner.
        val hint = labels.measure(HINT.localized(), caption)
        drawText(hint, topLeft = Offset(inset + w - hint.size.width - 4.dp.toPx(), inset), alpha = 0.7f)
        val spline = HueSpline(points, curve.periodic)
        val path = Path().apply {
            val steps = 96
            for (s in 0..steps) {
                val x = s / steps.toFloat()
                val px = inset + x * w
                val py = inset + (1f - spline.eval(x).coerceIn(0f, 1f)) * h
                if (s == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(path, handle, style = Stroke(2.dp.toPx()), alpha = if (enabled) 1f else 0.4f)
        points.forEach { p ->
            drawCircle(handle, radius = 8.dp.toPx(), center = Offset(inset + p.x * w, inset + (1f - p.y) * h), alpha = if (enabled) 1f else 0.4f)
        }
    }
}

/** Height of the strip under a hue curve showing what runs along x. */
private val STRIP = 12.dp

private const val HINT = "点按加点，两侧自动加锚点 · 双击删除"

/**
 * The point a press at [x] added — in [points], not in the list [before] the press — once the list shows it, or -1
 * (release round R10). The list a gesture reads is the one last drawn: a drag past the touch slop before the next frame
 * used to take the nearest point of the old list (the black point at x 0) and bend that instead.
 */
internal fun addedAt(points: List<CurvePoint>, before: List<CurvePoint>, x: Float): Int =
    points.indices.filter { points[it] !in before && abs(points[it].x - x) <= ADDED_REACH }.minByOrNull { abs(points[it].x - x) } ?: -1

/** How far from the press its point may sit: an inner point keeps 0.03 from the curve's ends; a hue curve's anchors sit 1/6 away. */
private const val ADDED_REACH = 0.035f

private fun nearest(points: List<CurvePoint>, x: Float, y: Float, radius: Float): Int =
    points.indices.minByOrNull { maxOf(abs(points[it].x - x), abs(points[it].y - y)) }
        ?.takeIf { maxOf(abs(points[it].x - x), abs(points[it].y - y)) <= radius } ?: -1

private fun curveColor(channel: CurveChannel): Color = when (channel) {
    CurveChannel.LUMA -> PrismColors.ScopeLuma
    CurveChannel.RED -> PrismColors.ScopeRed
    CurveChannel.GREEN -> PrismColors.ScopeGreen
    CurveChannel.BLUE -> PrismColors.ScopeBlue
}

/**
 * `looks-panel`: the LUT gallery previewed on the current frame — the built-in looks, then the LUTs imported
 * into the headset's library (`.cube`, tagged LUT), then `lut-import-tile` — plus strength. A node naming
 * a LUT the library lacks says so (`lut-missing-note`).
 */
@Composable
fun LooksPanel(
    looks: List<LookPreviewUi>,
    selected: Look,
    selectedLut: String?,
    strength: Int,
    enabled: Boolean,
    importing: Boolean,
    missingLut: String?,
    onSelect: (LookPreviewUi) -> Unit,
    onStrength: (Float) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    generating: Boolean = false,
    onGenerate: () -> Unit = {},
    onRemove: (String) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(looks, key = { it.key }) { item ->
                val on = if (item.lut != null) item.lut.id == selectedLut else selectedLut == null && item.look == selected
                LookTile(item, on, enabled, onRemove = item.lut?.let { lut -> { onRemove(lut.id) } }) { onSelect(item) }
            }
            item(key = "lut-import") { ImportLutTile(importing, onImport) }
            item(key = "lut-generate") { GenerateLutTile(enabled, generating, onGenerate) }
        }
        if (missingLut != null) {
            Text(
                "LUT 缺失：$missingLut · 重新导入同一个 .cube 文件即可恢复",
                style = PrismType.caption,
                color = PicoTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ValueSliderRow(
            "强度", strength.toFloat(), 0f..100f, "$strength%", onStrength,
            enabled = enabled && (selected != Look.NONE || selectedLut != null), step = 1f, minSliderWidth = 160.dp,
        )
    }
}

/** `lut-import-tile`: opens the system file picker for a `.cube` file (any project can then use it). */
@Composable
private fun ImportLutTile(importing: Boolean, onClick: () -> Unit) {
    ActionTile(
        icon = R.drawable.ic_plus,
        label = if (importing) "正在导入…" else "导入 LUT…",
        description = if (importing) "正在导入 LUT" else "导入 LUT（.cube 文件）",
        enabled = !importing,
        onClick = onClick,
    )
}

/**
 * `lut-generate-tile`: Resolve's Generate LUT — the selected clip's grade at the playhead as a 33-point
 * `.cube`, added to the library and saved to Download/Reality Video Editor.
 */
@Composable
private fun GenerateLutTile(enabled: Boolean, generating: Boolean, onClick: () -> Unit) {
    ActionTile(
        icon = R.drawable.ic_color_wheel,
        label = if (generating) "正在生成…" else "生成 LUT",
        description = "用当前片段的调色生成 LUT（33³）",
        enabled = enabled && !generating,
        onClick = onClick,
    )
}

/** A gallery tile that does something instead of showing a look: an icon in the picture well and a label. */
@Composable
private fun ActionTile(icon: Int, label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val fill = PicoTheme.colorScheme.fillTertiary
    val tint = if (enabled) PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.labelTertiary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics { contentDescription = description.localized() },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.778f), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint)
        }
        Text(label, style = PrismType.caption, color = tint, maxLines = 1)
    }
}

/** `lut-menu`: held on an imported LUT's tile — take it out of the headset's library (after confirming). */
@Composable
private fun LutTileMenu(onRemove: () -> Unit, onDismiss: () -> Unit) {
    Menu(
        onDismissRequest = onDismiss,
        positionProvider = rememberMenuPositionProvider(HorizontalPlacement.alignStart(), VerticalPlacement.above()),
    ) {
        MenuItem(
            title = { Text("从 LUT 库移除…") },
            onClick = onRemove,
            contentColors = MenuItemDefaults.menuItemColors(titleColor = PicoTheme.colorScheme.error),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookTile(item: LookPreviewUi, selected: Boolean, enabled: Boolean, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val image = rememberFrameImage(item.pixels)
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    var menu by remember { mutableStateOf(false) }
    // An imported LUT's menu opens where its tile is held.
    if (menu && onRemove != null) LutTileMenu(onRemove = { menu = false; onRemove() }, onDismiss = { menu = false })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(z = if (selected) 12.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onRemove?.let { { menu = true } })
            .semantics { this.selected = selected }
            .drawBehind { drawRect(fill) }
            .padding(6.dp)
            .semantics { contentDescription = (item.lut?.let { "导入的 LUT ${it.name}（${it.detail}）" } ?: "LUT 风格 ${item.look.label}").localized() },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.778f)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(PrismColors.PictureBlack) },
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            // Imported LUTs carry a tag, so the colourist's own files stand apart from the built-in looks.
            if (item.lut != null) {
                Text(
                    "LUT",
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelPrimaryLight,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .drawBehind { drawRect(PrismColors.PictureBlack, alpha = 0.72f) }
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(item.label, style = PrismType.caption, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
