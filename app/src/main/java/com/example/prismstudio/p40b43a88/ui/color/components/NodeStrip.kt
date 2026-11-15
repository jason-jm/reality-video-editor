package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.domain.i18n.spoken
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.ui.color.SerialNodeUi
import com.example.prismstudio.p40b43a88.ui.components.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.example.prismstudio.p40b43a88.ui.components.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `node-strip`: the grade's serial nodes, as Resolve's node editor shows them — 01 corrects the whole picture, 02 inside a
 * power window (or a qualifier's colours), after 01, and 03 on ([serialNodes]) the whole picture again, each after the one
 * before; ＋ adds one (Resolve's Add Serial Node). The selected node is the one the wheels, sliders, curves and looks edit;
 * picking 02 on a clip without a window adds a circle window. More nodes than the strip holds scroll sideways.
 */
@Composable
fun NodeStrip(
    node: GradeNode,
    primaryGraded: Boolean,
    window: PowerWindow?,
    enabled: Boolean,
    onSelect: (GradeNode) -> Unit,
    modifier: Modifier = Modifier,
    /** Node 2's name: what limits it (窗口, 限定器 or both). */
    secondaryLabel: String = GradeNode.WINDOW.label,
    /** Node 1 has keyframes: its correction changes over the clip. */
    primaryKeyed: Boolean = false,
    serialNodes: List<SerialNodeUi> = emptyList(),
    canAdd: Boolean = false,
    onAdd: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // The selected node scrolls into view (a node added at the end, or one picked past the strip's edge).
    LaunchedEffect(node, serialNodes.size) {
        val left = when {
            node == GradeNode.PRIMARY -> 0.dp
            node == GradeNode.WINDOW -> MAIN_CARD + GAP
            else -> (MAIN_CARD + GAP) * 2 + (SERIAL_CARD + GAP) * node.serialIndex
        }
        // A card's left edge one card in from the strip's left: the node before it shows too.
        val target = with(density) { (left - SERIAL_CARD - GAP).toPx() }.roundToInt().coerceIn(0, scroll.maxValue)
        if (node.isSerial || scroll.value > target) scroll.animateScrollTo(target)
    }
    Row(
        modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeCard(
            index = "01",
            title = GradeNode.PRIMARY.label,
            summary = if (primaryKeyed) "整个画面 · 动态" else "整个画面",
            selected = node == GradeNode.PRIMARY,
            graded = primaryGraded,
            enabled = enabled,
            onClick = { onSelect(GradeNode.PRIMARY) },
        ) { lit, edge -> drawFrameGlyph(null, lit, edge) }
        NodeCard(
            index = "02",
            title = secondaryLabel,
            summary = window?.let(::secondarySummary) ?: "点按添加窗口",
            selected = node == GradeNode.WINDOW,
            graded = window?.grade?.isNeutral == false,
            enabled = enabled,
            onClick = { onSelect(GradeNode.WINDOW) },
        ) { lit, edge -> drawFrameGlyph(window, lit, edge) }
        // `serial-node-card`: nodes 03 on, the whole picture; one switched off reads faint.
        serialNodes.forEach { ui ->
            NodeCard(
                index = ui.node.number,
                title = ui.node.label,
                summary = ui.summary,
                selected = node == ui.node,
                graded = ui.graded && ui.enabled,
                enabled = enabled,
                onClick = { onSelect(ui.node) },
                width = SERIAL_CARD,
                dimmed = !ui.enabled,
            ) { lit, edge -> drawFrameGlyph(null, lit, edge) }
        }
        // `add-node-card`: Resolve's Add Serial Node (Alt+S), after the last node.
        AddNodeCard(enabled = enabled && canAdd, onClick = onAdd)
    }
}

/** `add-node-card`: ＋ and 节点, a serial node after the last one. */
@Composable
private fun AddNodeCard(enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.spatialHoverEffect() else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { contentDescription = "添加串行节点：调整整个画面，在最后一个节点之后".localized() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelSecondary)
        Text("节点", style = PrismType.caption, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
    }
}

private val SERIAL_CARD = 120.dp
private val MAIN_CARD = 136.dp
private val GAP = 12.dp

/** Where node 2 corrects: inside / outside the shape, and the qualifier's colours. */
private fun secondarySummary(w: PowerWindow): String {
    val area = when (w.shape) {
        WindowShape.FULL -> "全画面"
        // A keyed window moves (Resolve's dynamic keyframes).
        else -> "${w.shape.label} · ${if (w.invert) "窗口外" else "窗口内"}${if (w.keyframes.isNotEmpty()) " · 动态" else ""}"
    }
    val q = w.qualifier ?: return area
    val colours = when {
        q.invert -> "选色以外"
        q.hueWidth >= com.example.prismstudio.p40b43a88.domain.model.HslQualifier.FULL_HUE -> "按亮度"
        else -> "色相 ${q.hueCenter.toInt()}°"
    }
    return "$area · $colours"
}

@Composable
private fun NodeCard(
    index: String,
    title: String,
    summary: String,
    selected: Boolean,
    graded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp = MAIN_CARD,
    /** A node switched off: its card reads faint. */
    dimmed: Boolean = false,
    glyph: DrawScope.(lit: Color, edge: Color) -> Unit,
) {
    val fill = if (selected) PicoTheme.colorScheme.fillSecondary else PicoTheme.colorScheme.fillTertiary
    val bar = PicoTheme.colorScheme.interaction
    // Drawn on a picture-black frame: light roles, as over video.
    val lit = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.55f)
    val edge = PicoTheme.colorScheme.labelPrimaryLight.copy(alpha = 0.25f)
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .offset(z = if (selected) 12.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRect(fill)
                if (selected) drawRoundRect(bar, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
            }
            .then(if (enabled) Modifier.spatialHoverEffect() else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = spoken("节点 $index $title".localized(), summary.localized(), if (graded) "已调色".localized() else "")
            }
            .padding(12.dp),
    ) {
        Column(Modifier.alpha(if (dimmed) DIMMED else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // A small glyph: the 136 dp card (120 dp for a serial node) keeps "01 主校色" whole.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(28.dp, 18.dp)) { glyph(lit, edge) }
                Text("$index $title", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, softWrap = false)
            }
            Text(summary, style = PrismType.caption, color = PicoTheme.colorScheme.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (graded) {
            Canvas(Modifier.align(Alignment.TopEnd).size(8.dp)) { drawCircle(PrismColors.MarkerFlag) }
        }
    }
}

/** A small picture frame with the area the node corrects [lit]: all of it, or the window's shape. */
private fun DrawScope.drawFrameGlyph(window: PowerWindow?, lit: Color, edge: Color) {
    val radius = CornerRadius(4.dp.toPx())
    drawRoundRect(PrismColors.PictureBlack, cornerRadius = radius)
    clipRect {
        when {
            window == null || window.shape == WindowShape.FULL -> drawRoundRect(lit, cornerRadius = radius)
            window.shape == WindowShape.GRADIENT -> rotate(-window.angle) {
                val (from, to) = if (window.invert) Color.Transparent to lit else lit to Color.Transparent
                drawRect(Brush.verticalGradient(listOf(from, to), startY = -size.height * 0.5f, endY = size.height * 1.5f),
                    topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3))
            }
            else -> {
                if (window.invert) drawRoundRect(lit, cornerRadius = radius)
                val h = size.height * 0.5f * window.size.coerceIn(0.2f, 1f)
                val w = (h * window.aspect).coerceAtMost(size.width * 0.45f)
                val shapeColor = if (window.invert) PrismColors.PictureBlack else lit
                rotate(-window.angle) {
                    val topLeft = Offset(center.x - w, center.y - h)
                    if (window.shape == WindowShape.CIRCLE) drawOval(shapeColor, topLeft, Size(2 * w, 2 * h))
                    else drawRect(shapeColor, topLeft, Size(2 * w, 2 * h))
                }
            }
        }
    }
    drawRoundRect(edge, cornerRadius = radius, style = Stroke(1.dp.toPx()))
}

/** How faint a switched-off node's card reads. */
private const val DIMMED = 0.45f

/**
 * `serial-node-actions`: in the keyframes' place while a serial node is selected — what it does (the whole picture, after the
 * nodes before it, no keyframes), `node-enable-button` (Resolve's Enable / Disable Node, Ctrl+D) and `node-delete-button`.
 */
@Composable
fun SerialNodeActions(ui: SerialNodeUi, enabled: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("节点 ${ui.node.number} · ${ui.node.label}", style = PrismType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text(
            if (ui.enabled) "调整整个画面，接在前一个节点之后 · 不设关键帧" else "已停用：画面跳过这个节点，设置保留",
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onToggle,
                enabled = enabled,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                modifier = Modifier.semantics { contentDescription = (if (ui.enabled) "停用节点 ${ui.node.number}（Ctrl+D）" else "启用节点 ${ui.node.number}（Ctrl+D）").localized() },
            ) { Text(if (ui.enabled) "停用节点" else "启用节点") }
            Button(
                onClick = onDelete,
                enabled = enabled,
                size = ButtonDefaults.Small,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
            ) { Text("删除节点") }
        }
    }
}
