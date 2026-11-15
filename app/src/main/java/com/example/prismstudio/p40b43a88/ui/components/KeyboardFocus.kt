package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Tab and Shift+Tab move on from a text field to the next or previous control (release round R12). Compose's text fields
 * take the key themselves, so a keyboard user's focus stopped at the first field on a page — on Deliver, the file name —
 * and everything after it (codec, resolution, the render queue) could not be reached.
 *
 * Back, once the soft keyboard is down (the keyboard takes the first press itself), leaves the field and goes on to the
 * page — closing a sheet, or the project (release round R22). Compose's text field spent that press only giving up its
 * focus, so after typing a file name the second Back did nothing and a third was needed.
 */
fun Modifier.tabMovesFocus(): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        when {
            event.key == Key.Tab && event.type == KeyEventType.KeyDown -> {
                focus.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                true
            }
            event.key == Key.Back && event.type == KeyEventType.KeyDown -> {
                focus.clearFocus()
                false
            }
            else -> false
        }
    }
}
