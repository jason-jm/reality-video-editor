package com.example.prismstudio.p40b43a88.debug

import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Debug-only layout dump: every labelled Compose node of the resumed window with its bounds in window pixels, logged as
 * `PrismBounds`. uiautomator stops at the emulator display (2160 px), so this is how QA reads a 2700 dp (5400 px) window.
 */
object AutomationBounds {
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
