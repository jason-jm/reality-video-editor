package com.example.prismstudio.p40b43a88.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.prismstudio.p40b43a88.data.automation.AutomationBridge

/**
 * Debug-only QA hook: `adb shell am broadcast -a com.example.prismstudio.AUTOMATION
 * -p "$(zsh devtools/tools/appid.sh)" --es cmd "key KEYCODE_2"`. `tap X Y` and
 * `drag X1 Y1 X2 Y2 [ms] [holdMs]` inject touches in window pixels, `bounds [text]` logs labelled nodes' bounds
 * (PrismBounds), `i18n-missing` logs untranslated text seen (PrismI18n), `controls` logs every control with its state
 * (PrismClick), `toasts` the messages shown since the last call (PrismToast), `frame` how long the next frame takes
 * (PrismFrame; the emulator delivers frames once a second while idle); everything else goes to the app.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("cmd") ?: return
        Log.i("PrismAutomation", "command: $command")
        val parts = command.trim().split(Regex("\\s+"))
        val numbers = parts.drop(1).mapNotNull { it.toFloatOrNull() }
        when {
            parts[0] == "tap" && numbers.size == 2 -> AutomationTouch.tap(numbers[0], numbers[1])
            parts[0] == "drag" && numbers.size >= 4 ->
                AutomationTouch.drag(
                    numbers[0], numbers[1], numbers[2], numbers[3],
                    numbers.getOrNull(4)?.toLong() ?: 400L, numbers.getOrNull(5)?.toLong() ?: 0L,
                )
            // QA: the Chinese shown without a translation since the last call (then forgotten), as PrismI18n lines.
            parts[0] == "i18n-missing" -> {
                val missing = com.example.prismstudio.p40b43a88.domain.i18n.I18n.missing
                missing.sorted().forEach { Log.w("PrismI18n", it.replace("\n", "\\n")) }
                Log.w("PrismI18n", "${missing.size} untranslated")
                missing.clear()
            }
            // QA: the interface language by tag (en, zh-Hans, x-pseudo) or "system", as the language menu sets it.
            parts[0] == "lang" && parts.size == 2 -> @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) {
                com.example.prismstudio.p40b43a88.data.AppGraph.from(context).language
                    .setLanguage(com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage.fromTag(parts[1]))
            }
            parts[0] == "bounds" -> AutomationTouch.window()?.let { AutomationBounds.dump(it, parts.getOrNull(1)) }
            parts[0] == "text-layout" && parts.size >= 2 -> AutomationTouch.window()?.let { AutomationBounds.textLayout(it, parts[1], parts.getOrNull(2) == "windows") }
            // QA: every control of the resumed window with its state (PrismClick), and the toasts shown since the last call.
            // QA: how long the next frame takes to come (PrismFrame): the UI's response time when nothing animates.
            parts[0] == "frame" -> {
                val asked = System.nanoTime()
                android.view.Choreographer.getInstance().postFrameCallback {
                    Log.w("PrismFrame", "next frame after ${(System.nanoTime() - asked) / 1_000_000} ms")
                }
            }
            parts[0] == "focus" -> AutomationTouch.window()?.let { AutomationBounds.focus(it) }
            parts[0] == "controls" -> AutomationTouch.window()?.let { AutomationBounds.controls(it) }
            parts[0] == "toasts" -> {
                com.example.prismstudio.p40b43a88.ui.components.ShownToasts.drain().forEach { Log.w("PrismToast", it.replace("\n", "\\n")) }
                Log.w("PrismToast", "end")
            }
            else -> AutomationBridge.post(command)
        }
    }
}
