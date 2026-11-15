package com.example.prismstudio.p40b43a88.debug

import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only layout dump: every labelled Compose node of the resumed window with its bounds in window pixels, logged as
 * `PrismBounds`. uiautomator stops at the emulator display (2160 px), so this is how QA reads a 2700 dp (5400 px) window.
 */
object AutomationBounds {
    /** Measured glyph layout of visible labels, including ellipsis that accessibility text does not expose. */
    fun textLayout(root: View, request: String, includePopups: Boolean) {
        val windows = if (includePopups) android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it.isShown } + root else listOf(root)
        val surfaces = windows.mapNotNull(::findRoot).distinct()
        var count = 0
        fun walk(node: SemanticsNode, scrollX: Boolean, scrollY: Boolean, surface: Int) {
            val inScrollX = scrollX || node.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
            val inScrollY = scrollY || node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            val bounds = node.boundsInWindow
            val read = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            if (read != null && !bounds.isEmpty) {
                val layouts = mutableListOf<TextLayoutResult>()
                if (read(layouts)) for (layout in layouts) {
                    val record = JSONObject().apply {
                        put("request", request)
                        put("surface", surface)
                        put("text", layout.layoutInput.text.text)
                        put("x", bounds.left.toInt())
                        put("y", bounds.top.toInt())
                        put("width", bounds.width.toInt())
                        put("height", bounds.height.toInt())
                        layout.layoutInput.style.fontSize.value.takeIf { it.isFinite() }?.let { put("fontSp", it.toDouble()) }
                        put("lines", layout.lineCount)
                        val ellipsis = (0 until layout.lineCount).any(layout::isLineEllipsized)
                        val left = (0 until layout.lineCount).minOfOrNull(layout::getLineLeft) ?: 0f
                        val right = (0 until layout.lineCount).maxOfOrNull(layout::getLineRight) ?: 0f
                        put("ellipsis", ellipsis)
                        put("scrollX", inScrollX)
                        put("scrollY", inScrollY)
                        put("ink", JSONArray().apply {
                            if (layout.size.width <= bounds.width + 1f && layout.size.height <= bounds.height + 1f) {
                                for (line in 0 until layout.lineCount) put(JSONArray(listOf(
                                    (bounds.left + layout.getLineLeft(line)).toInt(), (bounds.top + layout.getLineTop(line)).toInt(),
                                    (bounds.left + layout.getLineRight(line)).toInt(), (bounds.top + layout.getLineBottom(line)).toInt(),
                                )))
                            }
                        })
                        put("textWidth", (right - left).toDouble())
                        put("textHeight", layout.multiParagraph.height.toDouble())
                        put("measuredWidth", layout.size.width)
                        put("measuredHeight", layout.size.height)
                        // The fast BasicText semantics path rounds intrinsic line heights differently. A
                        // fraction of a pixel at the baseline is not clipping. Ellipsis is always significant.
                        put("overflow", ellipsis || right - left > (if (inScrollX) layout.size.width.toFloat() else bounds.width) + 1f ||
                            layout.multiParagraph.height > (if (inScrollY) layout.size.height.toFloat() else bounds.height) + 1f)
                    }
                    Log.i("PrismTextLayout", record.toString())
                    count++
                }
            }
            node.children.forEach { walk(it, inScrollX, inScrollY, surface) }
        }
        surfaces.forEachIndexed { index, view -> walk(view.semanticsOwner.unmergedRootSemanticsNode, false, false, index) }
        Log.i("PrismTextLayout", JSONObject().put("request", request).put("count", count).toString())
    }

    fun dump(root: View, filter: String?) {
        val compose = findRoot(root)
        if (compose == null) {
            Log.w(TAG, "no Compose root")
            return
        }
        var count = 0
        fun walk(node: SemanticsNode) {
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            if (label != null && (filter == null || label.contains(filter))) {
                val b = node.boundsInWindow
                Log.w(TAG, "${label.take(40)} [${b.left.toInt()},${b.top.toInt()}][${b.right.toInt()},${b.bottom.toInt()}]")
                count++
            }
            node.children.forEach(::walk)
        }
        walk(compose.semanticsOwner.unmergedRootSemanticsNode)
        Log.w(TAG, "$count nodes, window ${root.width}x${root.height}")
    }

    /**
     * Every node of the merged tree (a control with the words inside it) as `PrismClick` lines:
     * `label | [l,t][r,b] | click | selected | toggle | state | enabled`. Taps are judged by what changes here.
     */
    fun controls(root: View) {
        val compose = findRoot(root)
        if (compose == null) {
            Log.w(CLICK, "no Compose root")
            return
        }
        var count = 0
        fun walk(node: SemanticsNode) {
            val c = node.config
            val label = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
            val click = c.contains(SemanticsActions.OnClick) || c.contains(SemanticsActions.OnLongClick)
            val selected = c.getOrNull(SemanticsProperties.Selected)
            val toggle = c.getOrNull(SemanticsProperties.ToggleableState)
            val state = c.getOrNull(SemanticsProperties.StateDescription)
            val range = c.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current
            val enabled = !c.contains(SemanticsProperties.Disabled)
            val focused = c.getOrNull(SemanticsProperties.Focused) == true
            if (label.isNotEmpty() || click) {
                val b = node.boundsInWindow
                Log.w(CLICK, "${label.replace("|", "/").replace("\n", " ").take(60)} | [${b.left.toInt()},${b.top.toInt()}][${b.right.toInt()},${b.bottom.toInt()}]" +
                    " | ${if (click) "click" else "-"} | ${selected ?: "-"} | ${toggle ?: "-"} | ${state ?: range ?: "-"} | $enabled" +
                    if (focused) " | focused" else "")
                count++
            }
            node.children.forEach(::walk)
        }
        walk(compose.semanticsOwner.rootSemanticsNode)
        Log.w(CLICK, "$count nodes")
    }

    /** The node holding keyboard focus in the unmerged tree (PrismFocus), with its role, label, bounds and parent labels. */
    fun focus(root: View) {
        val compose = findRoot(root) ?: return Log.w("PrismFocus", "no Compose root").let { }
        fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
        var found = 0
        fun walk(node: SemanticsNode, path: List<String>) {
            val here = path + label(node).take(20)
            if (node.config.getOrNull(SemanticsProperties.Focused) == true) {
                val b = node.boundsInWindow
                Log.w("PrismFocus", "focused [${b.left.toInt()},${b.top.toInt()}][${b.right.toInt()},${b.bottom.toInt()}] " +
                    "role=${node.config.getOrNull(SemanticsProperties.Role)} editable=${node.config.contains(SemanticsProperties.EditableText)} " +
                    "path=${here.filter { it.isNotEmpty() }.takeLast(4)}")
                found++
            }
            node.children.forEach { walk(it, here) }
        }
        walk(compose.semanticsOwner.unmergedRootSemanticsNode, emptyList())
        Log.w("PrismFocus", "$found focused, view focus ${root.findFocus()?.javaClass?.simpleName}")
    }

    private fun findRoot(view: View): RootForTest? {
        if (view is RootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findRoot(view.getChildAt(i))?.let { return it }
        return null
    }

    private const val TAG = "PrismBounds"
    private const val CLICK = "PrismClick"
}
