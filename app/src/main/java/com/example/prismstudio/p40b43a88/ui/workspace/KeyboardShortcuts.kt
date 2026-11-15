package com.example.prismstudio.p40b43a88.ui.workspace

import android.view.KeyEvent

/** Editing commands reachable from a hardware keyboard (DaVinci Resolve style). */
enum class ShortcutAction {
    STAGE_MEDIA, STAGE_EDIT, STAGE_COLOR, STAGE_AUDIO, STAGE_DELIVER,
    PLAY_PAUSE, PAUSE, PLAY,
    STEP_BACK, STEP_FORWARD, SECOND_BACK, SECOND_FORWARD,
    PREVIOUS_EDIT, NEXT_EDIT, GO_TO_START, GO_TO_END,
    MARK_IN, MARK_OUT, CLEAR_MARKS, APPEND, INSERT_EDIT, OVERWRITE_EDIT, RIPPLE_OVERWRITE_EDIT, REPLACE_EDIT, FIT_TO_FILL_EDIT, PLACE_ON_TOP,
    SPLIT, ADD_MARKER, RIPPLE_DELETE, DELETE, COPY, CUT, RIPPLE_CUT, PASTE, PASTE_INSERT, PASTE_ATTRIBUTES, TOGGLE_CLIP_ENABLED, TOGGLE_SNAPPING, DESELECT,
    SELECT_ALL, SELECT_FORWARD,
    UNDO, REDO, TOGGLE_COMPARE, AUTO_COLOR, CLIP_TRACK_UP, CLIP_TRACK_DOWN, PREVIOUS_MARKER, NEXT_MARKER,
    TRIM_MODE, NUDGE_BACK, NUDGE_FORWARD, NUDGE_BACK_FAST, NUDGE_FORWARD_FAST, TOGGLE_LINK, MATCH_FRAME,
    ADD_TRANSITION, ADD_VIDEO_TRANSITION, ADD_AUDIO_TRANSITION,
    TRIM_START_TO_PLAYHEAD, TRIM_END_TO_PLAYHEAD, RIPPLE_START_TO_PLAYHEAD, RIPPLE_END_TO_PLAYHEAD,
    SWAP_LEFT, SWAP_RIGHT, CLIP_DURATION,
    TIMECODE_ENTRY, TIMECODE_FORWARD, TIMECODE_BACK,
    NEW_GRADE_VERSION, PREVIOUS_GRADE_VERSION, NEXT_GRADE_VERSION,
    PLAY_AROUND, PLAY_IN_TO_OUT, TOGGLE_LOOP, MARK_CLIP,
    ADD_SERIAL_NODE,
}

/**
 * Key map. Pages: Shift+2 / 4 / 6 / 7 / 8 as in Resolve (or plain 1–5); Space, J / K / L transport;
 * ← → one frame, Shift+← → one second, ↑ ↓ edit points, Home / End; I / O marks (the source on the Media
 * stage, the timeline on the Edit stage), Alt+X clears them; F9 insert, F10 overwrite,
 * F12 place on top (the source's marked range at the timeline's playhead), F11 replace (the clip under the
 * playhead, the source's playhead frame on the timeline's), Shift+F10 ripple overwrite (the clip under the
 * playhead, or the timeline's In / Out, replaced at the source's own length, the rest of the timeline moving
 * to suit), Shift+F11 fit to fill (the source's range retimed into the timeline's In / Out), Shift+F12 or E
 * append; B blade;
 * M marker, Shift+↑ ↓ the previous / next marker; Backspace delete (leaves the gap), Delete or Shift+Backspace ripple delete; Ctrl/⌘+C / X / V
 * copy, cut, paste, with Shift ripple cut (closing the gap) and paste insert (making room); Alt+V paste attributes; D disables / enables the clip; N snapping; Esc deselect;
 * Ctrl/⌘+A selects every clip, Y every clip from the playhead on (a tap with Ctrl/⌘ held adds one);
 * Ctrl/⌘+Z undo, +Shift+Z redo;
 * Ctrl/⌘+W before/after wipe on the Color stage, Shift+Alt+C auto colour, Ctrl/⌘+Shift+N a new local grade version,
 * Shift+Alt+[ / ] the previous / next one; on the Color stage Alt+S adds a serial node and Ctrl/⌘+D switches the selected
 * serial node off or on (Resolve's); Ctrl/⌘+↑ ↓ moves the selected clip between tracks;
 * Ctrl/⌘+Alt+L links or unlinks the selected clips; Ctrl/⌘+T adds the standard transition (Alt+T picture only, Shift+T sound only);
 * Shift+[ / ] trims the start / end of the clips under the playhead to it, with Ctrl/⌘ as a ripple trim;
 * Ctrl/⌘+Shift+, / . swaps the selected clip with the one before / after it on its track; Ctrl/⌘+D changes its duration;
 * = types a timecode, + / − (or the keypad's) an offset — on the Color stage = / − apply the grade of the clip one / two prior;
 * / plays around the playhead (2 s each side, then back), Alt+/ plays In to Out, Ctrl+/ loops; X marks the clip;
 * T cycles the dial's playhead / slip / roll mode; , and . nudge one frame (Shift: five) — the trim in
 * slip / roll mode, else the selected clip.
 */
object KeyboardShortcuts {

    fun resolve(keyCode: Int, shift: Boolean, ctrl: Boolean, alt: Boolean = false): ShortcutAction? {
        if (ctrl && alt && !shift) return if (keyCode == KeyEvent.KEYCODE_L) ShortcutAction.TOGGLE_LINK else null
        if (alt && shift && !ctrl) {
            return when (keyCode) {
                KeyEvent.KEYCODE_C -> ShortcutAction.AUTO_COLOR
                KeyEvent.KEYCODE_LEFT_BRACKET -> ShortcutAction.PREVIOUS_GRADE_VERSION
                KeyEvent.KEYCODE_RIGHT_BRACKET -> ShortcutAction.NEXT_GRADE_VERSION
                else -> null
            }
        }
        if (alt && !ctrl && !shift) {
            return when (keyCode) {
                KeyEvent.KEYCODE_V -> ShortcutAction.PASTE_ATTRIBUTES
                KeyEvent.KEYCODE_X -> ShortcutAction.CLEAR_MARKS
                KeyEvent.KEYCODE_T -> ShortcutAction.ADD_VIDEO_TRANSITION
                KeyEvent.KEYCODE_SLASH -> ShortcutAction.PLAY_IN_TO_OUT
                KeyEvent.KEYCODE_S -> ShortcutAction.ADD_SERIAL_NODE
                else -> null
            }
        }
        if (ctrl) {
            return when (keyCode) {
                KeyEvent.KEYCODE_Z -> if (shift) ShortcutAction.REDO else ShortcutAction.UNDO
                KeyEvent.KEYCODE_Y -> ShortcutAction.REDO
                KeyEvent.KEYCODE_A -> ShortcutAction.SELECT_ALL
                KeyEvent.KEYCODE_C -> ShortcutAction.COPY
                KeyEvent.KEYCODE_X -> if (shift) ShortcutAction.RIPPLE_CUT else ShortcutAction.CUT
                KeyEvent.KEYCODE_V -> if (shift) ShortcutAction.PASTE_INSERT else ShortcutAction.PASTE
                KeyEvent.KEYCODE_B -> ShortcutAction.SPLIT
                KeyEvent.KEYCODE_W -> ShortcutAction.TOGGLE_COMPARE
                KeyEvent.KEYCODE_T -> if (shift) null else ShortcutAction.ADD_TRANSITION
                KeyEvent.KEYCODE_LEFT_BRACKET -> if (shift) ShortcutAction.RIPPLE_START_TO_PLAYHEAD else null
                KeyEvent.KEYCODE_COMMA -> if (shift) ShortcutAction.SWAP_LEFT else null
                KeyEvent.KEYCODE_D -> if (shift) null else ShortcutAction.CLIP_DURATION
                KeyEvent.KEYCODE_N -> if (shift) ShortcutAction.NEW_GRADE_VERSION else null
                KeyEvent.KEYCODE_SLASH -> if (shift) null else ShortcutAction.TOGGLE_LOOP
                KeyEvent.KEYCODE_PERIOD -> if (shift) ShortcutAction.SWAP_RIGHT else null
                KeyEvent.KEYCODE_RIGHT_BRACKET -> if (shift) ShortcutAction.RIPPLE_END_TO_PLAYHEAD else null
                KeyEvent.KEYCODE_DPAD_UP -> ShortcutAction.CLIP_TRACK_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> ShortcutAction.CLIP_TRACK_DOWN
                else -> null
            }
        }
        if (shift) {
            return when (keyCode) {
                KeyEvent.KEYCODE_2 -> ShortcutAction.STAGE_MEDIA
                KeyEvent.KEYCODE_4 -> ShortcutAction.STAGE_EDIT
                KeyEvent.KEYCODE_6 -> ShortcutAction.STAGE_COLOR
                KeyEvent.KEYCODE_7 -> ShortcutAction.STAGE_AUDIO
                KeyEvent.KEYCODE_8 -> ShortcutAction.STAGE_DELIVER
                KeyEvent.KEYCODE_DPAD_LEFT -> ShortcutAction.SECOND_BACK
                KeyEvent.KEYCODE_DPAD_RIGHT -> ShortcutAction.SECOND_FORWARD
                KeyEvent.KEYCODE_DPAD_UP -> ShortcutAction.PREVIOUS_MARKER
                KeyEvent.KEYCODE_DPAD_DOWN -> ShortcutAction.NEXT_MARKER
                KeyEvent.KEYCODE_F10 -> ShortcutAction.RIPPLE_OVERWRITE_EDIT
                KeyEvent.KEYCODE_F11 -> ShortcutAction.FIT_TO_FILL_EDIT
                KeyEvent.KEYCODE_F12 -> ShortcutAction.APPEND
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> ShortcutAction.RIPPLE_DELETE
                KeyEvent.KEYCODE_COMMA -> ShortcutAction.NUDGE_BACK_FAST
                KeyEvent.KEYCODE_PERIOD -> ShortcutAction.NUDGE_FORWARD_FAST
                KeyEvent.KEYCODE_T -> ShortcutAction.ADD_AUDIO_TRANSITION
                KeyEvent.KEYCODE_EQUALS -> ShortcutAction.TIMECODE_FORWARD
                KeyEvent.KEYCODE_LEFT_BRACKET -> ShortcutAction.TRIM_START_TO_PLAYHEAD
                KeyEvent.KEYCODE_RIGHT_BRACKET -> ShortcutAction.TRIM_END_TO_PLAYHEAD
                else -> null
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_1 -> ShortcutAction.STAGE_MEDIA
            KeyEvent.KEYCODE_2 -> ShortcutAction.STAGE_EDIT
            KeyEvent.KEYCODE_3 -> ShortcutAction.STAGE_COLOR
            KeyEvent.KEYCODE_4 -> ShortcutAction.STAGE_AUDIO
            KeyEvent.KEYCODE_5 -> ShortcutAction.STAGE_DELIVER
            KeyEvent.KEYCODE_SPACE -> ShortcutAction.PLAY_PAUSE
            KeyEvent.KEYCODE_K -> ShortcutAction.PAUSE
            KeyEvent.KEYCODE_L -> ShortcutAction.PLAY
            KeyEvent.KEYCODE_J -> ShortcutAction.SECOND_BACK
            KeyEvent.KEYCODE_DPAD_LEFT -> ShortcutAction.STEP_BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> ShortcutAction.STEP_FORWARD
            KeyEvent.KEYCODE_DPAD_UP -> ShortcutAction.PREVIOUS_EDIT
            KeyEvent.KEYCODE_DPAD_DOWN -> ShortcutAction.NEXT_EDIT
            KeyEvent.KEYCODE_MOVE_HOME -> ShortcutAction.GO_TO_START
            KeyEvent.KEYCODE_MOVE_END -> ShortcutAction.GO_TO_END
            KeyEvent.KEYCODE_I -> ShortcutAction.MARK_IN
            KeyEvent.KEYCODE_O -> ShortcutAction.MARK_OUT
            KeyEvent.KEYCODE_E -> ShortcutAction.APPEND
            KeyEvent.KEYCODE_F9 -> ShortcutAction.INSERT_EDIT
            KeyEvent.KEYCODE_F10 -> ShortcutAction.OVERWRITE_EDIT
            KeyEvent.KEYCODE_F11 -> ShortcutAction.REPLACE_EDIT
            KeyEvent.KEYCODE_F12 -> ShortcutAction.PLACE_ON_TOP
            KeyEvent.KEYCODE_B -> ShortcutAction.SPLIT
            KeyEvent.KEYCODE_M -> ShortcutAction.ADD_MARKER
            KeyEvent.KEYCODE_D -> ShortcutAction.TOGGLE_CLIP_ENABLED
            KeyEvent.KEYCODE_F -> ShortcutAction.MATCH_FRAME
            KeyEvent.KEYCODE_DEL -> ShortcutAction.DELETE
            KeyEvent.KEYCODE_FORWARD_DEL -> ShortcutAction.RIPPLE_DELETE
            KeyEvent.KEYCODE_N -> ShortcutAction.TOGGLE_SNAPPING
            KeyEvent.KEYCODE_ESCAPE -> ShortcutAction.DESELECT
            KeyEvent.KEYCODE_Y -> ShortcutAction.SELECT_FORWARD
            KeyEvent.KEYCODE_T -> ShortcutAction.TRIM_MODE
            KeyEvent.KEYCODE_EQUALS -> ShortcutAction.TIMECODE_ENTRY
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> ShortcutAction.TIMECODE_FORWARD
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> ShortcutAction.TIMECODE_BACK
            KeyEvent.KEYCODE_COMMA -> ShortcutAction.NUDGE_BACK
            KeyEvent.KEYCODE_PERIOD -> ShortcutAction.NUDGE_FORWARD
            KeyEvent.KEYCODE_SLASH -> ShortcutAction.PLAY_AROUND
            KeyEvent.KEYCODE_X -> ShortcutAction.MARK_CLIP
            else -> null
        }
    }
}
