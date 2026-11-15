package com.example.prismstudio.p40b43a88.debug

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import java.lang.ref.WeakReference

/**
 * Debug-only touch injection into the resumed activity's window, in window pixels. Lets QA drive
 * parts of a wide spatial window that lie outside the emulator display's injectable area.
 */
object AutomationTouch : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity>? = null
    private val main = Handler(Looper.getMainLooper())

    /** The resumed activity's window view, for [AutomationBounds]. */
    fun window(): android.view.View? = current?.get()?.window?.decorView

    fun tap(x: Float, y: Float) = gesture(listOf(Triple(x, y, 0L), Triple(x, y, 60L)))

    /** A straight drag over [durationMs]; with [holdMs] the pointer then rests at the end before it lifts. */
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long, holdMs: Long = 0L) {
        val steps = 16
        val path = (0..steps).map { i -> Triple(fromX + (toX - fromX) * i / steps, fromY + (toY - fromY) * i / steps, durationMs * i / steps) }
        gesture(if (holdMs > 0) path + Triple(toX, toY, durationMs + holdMs) else path)
    }

    /** Points with their times (ms from the press): down at the first, up at the last. */
    private fun gesture(points: List<Triple<Float, Float, Long>>) {
        val view = current?.get()?.window?.decorView
        if (view == null) {
            Log.w(TAG, "No resumed activity to touch")
            return
        }
        val down = SystemClock.uptimeMillis() + 16
        points.forEachIndexed { i, (x, y, offset) ->
            val at = down + offset
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                points.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            main.postAtTime({
                val event = MotionEvent.obtain(down, at, action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                view.dispatchTouchEvent(event)
                event.recycle()
            }, at)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private const val TAG = "PrismAutomation"
}
