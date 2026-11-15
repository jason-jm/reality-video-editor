package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.example.prismstudio.p40b43a88.domain.i18n.I18n
import com.pico.spatial.ui.design.windows.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * Shows [message] once through the enclosing `SnackbarHost` and reports it consumed. `show` suspends
 * until the snack is dismissed and cancelling it dismisses the snack, so it runs in the composition's
 * scope: consuming the message changes this effect's key and would otherwise cancel it at once.
 */
@Composable
fun ToastEffect(message: String?, onShown: () -> Unit) {
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val shown by rememberUpdatedState(onShown)
    // Messages are said in the Chinese source: shown in the language shown.
    val language = LocalAppLanguage.current
    LaunchedEffect(message) {
        if (message != null) {
            ShownToasts.add(message)
            scope.launch { snackbar.show(message = I18n.translate(message, language)) }
            shown()
        }
    }
}

/** The last messages shown, in the source language (QA: the debug `toasts` command logs and forgets them). */
object ShownToasts {
    private val recent = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        recent.addLast(message)
        while (recent.size > KEEP) recent.removeFirst()
    }

    @Synchronized
    fun drain(): List<String> = recent.toList().also { recent.clear() }

    private const val KEEP = 20
}
