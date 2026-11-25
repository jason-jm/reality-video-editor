package com.example.prismstudio.p40b43a88

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Spatial window coordinates, including the portions outside Android's default 2D display. */
internal class EditorWindow : AutoCloseable {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val automation = instrument.uiAutomation
    private val appId = instrument.targetContext.packageName
    private val previousFlags = automation.serviceInfo.flags
    private var lastWindow: Int? = null

    init {
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    fun waitFor(description: String, test: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 12000
        do {
            if (test()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        val snapshot = instrument.targetContext.cacheDir.resolve("qa-ui-${description.hashCode().toUInt()}.txt")
        snapshot.bufferedWriter().use { out ->
            for (root in automation.windows.mapNotNull { it.root }.filter { it.packageName == appId }) {
                val nodes = ArrayDeque(listOf(root))
                while (nodes.isNotEmpty()) {
                    val node = nodes.removeFirst()
                    out.appendLine("${node.windowId}\t${node.className}\t${node.text}\t${node.contentDescription}\t${node.actionList.map { it.id }}")
                    for (i in 0 until node.childCount) node.getChild(i)?.let(nodes::addLast)
                }
            }
        }
        error("Timed out waiting for $description (${snapshot.name})")
    }

    private fun findAction(label: String, prefix: Boolean, byDescription: Boolean, action: Int): AccessibilityNodeInfo? {
        val roots = (listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root })
            .filter { it.packageName == appId }.distinctBy { it.windowId }.sortedBy { it.windowId != lastWindow }
        for (root in roots) {
            val pending = ArrayDeque(listOf(root))
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                val labels = if (byDescription) listOfNotNull(node.contentDescription) else listOfNotNull(node.text, node.contentDescription)
                if (labels.any { if (prefix) it.startsWith(label) else it.toString() == label }) {
                    if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                        val children = ArrayDeque(listOf(node))
                        while (children.isNotEmpty()) {
                            val child = children.removeFirst()
                            if (child.isEnabled && child.actionList.any { it.id == action }) return child
                            for (i in 0 until child.childCount) child.getChild(i)?.let(children::addLast)
                        }
                    }
                    var candidate: AccessibilityNodeInfo? = node
                    while (candidate != null && candidate.actionList.none { it.id == action }) candidate = candidate.parent
                    if (candidate?.isEnabled == true) {
                        lastWindow = candidate.windowId
                        return candidate
                    }
                }
                for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    fun click(label: String, prefix: Boolean = false, byDescription: Boolean = false) {
        waitFor(label) { findAction(label, prefix, byDescription, AccessibilityNodeInfo.ACTION_CLICK)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
    }

    /** Use an item unique to the open menu when the editor underneath also lists the same timeline. */
    fun focusWindowContaining(label: String) {
        waitFor("window containing $label") { findAction(label, false, false, AccessibilityNodeInfo.ACTION_CLICK) != null }
    }

    fun setText(label: String, value: String) {
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        waitFor("edit field $label") {
            findAction(label, false, true, AccessibilityNodeInfo.ACTION_SET_TEXT)
                ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        }
    }

    fun bounds(label: String, prefix: Boolean = false): Rect {
        val result = Rect()
        fun visit(node: SemanticsNode) {
            val labels = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val b = node.boundsInWindow
            if (b.width > result.width() && !b.isEmpty && labels.any { if (prefix) it.startsWith(label) else it == label }) {
                result.set(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            node.children.forEach(::visit)
        }
        fun visitView(view: View) {
            when (view) {
                is RootForTest -> visit(view.semanticsOwner.rootSemanticsNode)
                is ViewGroup -> for (i in 0 until view.childCount) visitView(view.getChildAt(i))
            }
        }
        waitFor("measured $label") {
            instrument.runOnMainSync { visitView(activity().window.decorView) }
            !result.isEmpty
        }
        return result
    }

    private fun activity() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single { it.packageName == appId }

    fun key(code: Int, meta: Int = 0) = instrument.runOnMainSync {
        val activity = activity()
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            activity.dispatchKeyEvent(KeyEvent(now, now, action, code, 0, meta))
        }
    }

    fun drag(from: Rect, x: Float, y: Float, longPress: Boolean = true) {
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, dx: Float, dy: Float) = instrument.runOnMainSync {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, dx, dy, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { activity().window.decorView.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        val sx = from.exactCenterX()
        val sy = from.exactCenterY()
        touch(MotionEvent.ACTION_DOWN, sx, sy)
        if (longPress) SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 220L)
        for (step in 1..16) {
            touch(MotionEvent.ACTION_MOVE, sx + (x - sx) * step / 16, sy + (y - sy) * step / 16)
            SystemClock.sleep(24)
        }
        touch(MotionEvent.ACTION_UP, x, y)
    }

    override fun close() { automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags } }
}
