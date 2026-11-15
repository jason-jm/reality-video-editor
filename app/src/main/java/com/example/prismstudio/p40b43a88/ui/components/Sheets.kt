package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties

/**
 * SpatialUI's sheet properties with the system Back closing the sheet (release round R8). PICO's defaults (6.1.9 builds them
 * as DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) ignore Back, so the controller's back button
 * did nothing while a sheet was open. Back runs the sheet's onDismissRequest — what its ✕ button does; the rest is PICO's.
 */
val BackDismissSheet: DialogProperties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)

/** PICO's own sheet properties (Back ignored), for a sheet that must not close on Back right now. */
val BackIgnoringSheet: DialogProperties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/**
 * Studio sheets and dialogs open or opening (release round R9). PICO takes about 2 s to bring a sheet's window up and
 * hand it the controller's Back; until then Back reaches the page behind it, closing the project (or the app, from the
 * library). Counted from the moment a sheet is asked for, so the pages leave Back to the sheet meanwhile.
 */
object OpenSheets {
    var count by mutableIntStateOf(0)
        private set

    internal fun opened() {
        count++
    }

    internal fun closed() {
        count--
    }
}

/** Marks the sheet or dialog shown here as open ([OpenSheets]): call it just before `Sheet` / `AlertDialog`. */
@Composable
fun CountsAsOpenSheet() {
    DisposableEffect(Unit) {
        OpenSheets.opened()
        onDispose { OpenSheets.closed() }
    }
}
