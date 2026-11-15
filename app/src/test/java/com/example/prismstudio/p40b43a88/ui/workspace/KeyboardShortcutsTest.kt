package com.example.prismstudio.p40b43a88.ui.workspace

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardShortcutsTest {

    private fun key(keyCode: Int, shift: Boolean = false, ctrl: Boolean = false) =
        KeyboardShortcuts.resolve(keyCode, shift, ctrl)

    @Test
    fun `plain number keys 1 to 5 open the five stages`() {
        assertEquals(ShortcutAction.STAGE_MEDIA, key(KeyEvent.KEYCODE_1))
        assertEquals(ShortcutAction.STAGE_EDIT, key(KeyEvent.KEYCODE_2))
        assertEquals(ShortcutAction.STAGE_COLOR, key(KeyEvent.KEYCODE_3))
        assertEquals(ShortcutAction.STAGE_AUDIO, key(KeyEvent.KEYCODE_4))
        assertEquals(ShortcutAction.STAGE_DELIVER, key(KeyEvent.KEYCODE_5))
    }

    @Test
    fun `shift with 2 4 6 7 8 opens the stages as in Resolve`() {
        assertEquals(ShortcutAction.STAGE_MEDIA, key(KeyEvent.KEYCODE_2, shift = true))
        assertEquals(ShortcutAction.STAGE_EDIT, key(KeyEvent.KEYCODE_4, shift = true))
        assertEquals(ShortcutAction.STAGE_COLOR, key(KeyEvent.KEYCODE_6, shift = true))
        assertEquals(ShortcutAction.STAGE_AUDIO, key(KeyEvent.KEYCODE_7, shift = true))
        assertEquals(ShortcutAction.STAGE_DELIVER, key(KeyEvent.KEYCODE_8, shift = true))
    }

    @Test
    fun `space and J K L drive the transport`() {
        assertEquals(ShortcutAction.PLAY_PAUSE, key(KeyEvent.KEYCODE_SPACE))
        assertEquals(ShortcutAction.SECOND_BACK, key(KeyEvent.KEYCODE_J))
        assertEquals(ShortcutAction.PAUSE, key(KeyEvent.KEYCODE_K))
        assertEquals(ShortcutAction.PLAY, key(KeyEvent.KEYCODE_L))
    }

    @Test
    fun `arrows step a frame or jump between edit points and shift arrows move a second`() {
        assertEquals(ShortcutAction.STEP_BACK, key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(ShortcutAction.STEP_FORWARD, key(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(ShortcutAction.SECOND_BACK, key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true))
        assertEquals(ShortcutAction.SECOND_FORWARD, key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true))
        assertEquals(ShortcutAction.PREVIOUS_EDIT, key(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(ShortcutAction.NEXT_EDIT, key(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `home and end jump to the start and end of the timeline`() {
        assertEquals(ShortcutAction.GO_TO_START, key(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(ShortcutAction.GO_TO_END, key(KeyEvent.KEYCODE_MOVE_END))
    }

    @Test
    fun `editing keys mark append blade add markers delete toggle snapping and deselect`() {
        assertEquals(ShortcutAction.MARK_IN, key(KeyEvent.KEYCODE_I))
        assertEquals(ShortcutAction.MARK_OUT, key(KeyEvent.KEYCODE_O))
        assertEquals(ShortcutAction.APPEND, key(KeyEvent.KEYCODE_E))
        assertEquals(ShortcutAction.APPEND, key(KeyEvent.KEYCODE_F12, shift = true))
        // Resolve's edit keys: F9 insert, F10 overwrite, F11 replace, F12 place on top.
        assertEquals(ShortcutAction.REPLACE_EDIT, key(KeyEvent.KEYCODE_F11))
        assertEquals(ShortcutAction.FIT_TO_FILL_EDIT, key(KeyEvent.KEYCODE_F11, shift = true))
        assertEquals(ShortcutAction.RIPPLE_OVERWRITE_EDIT, key(KeyEvent.KEYCODE_F10, shift = true))
        assertEquals(ShortcutAction.PREVIOUS_MARKER, key(KeyEvent.KEYCODE_DPAD_UP, shift = true))
        assertEquals(ShortcutAction.NEXT_MARKER, key(KeyEvent.KEYCODE_DPAD_DOWN, shift = true))
        assertEquals("Alt+X clears In / Out", ShortcutAction.CLEAR_MARKS, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_X, shift = false, ctrl = false, alt = true))
        assertEquals("Shift+Alt+C auto colour", ShortcutAction.AUTO_COLOR, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_C, shift = true, ctrl = false, alt = true))
        assertEquals("/ plays around the playhead", ShortcutAction.PLAY_AROUND, key(KeyEvent.KEYCODE_SLASH))
        assertEquals(ShortcutAction.PLAY_IN_TO_OUT, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_SLASH, shift = false, ctrl = false, alt = true))
        assertEquals("Alt+S adds a serial node (Resolve)", ShortcutAction.ADD_SERIAL_NODE, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_S, shift = false, ctrl = false, alt = true))
        assertEquals(ShortcutAction.TOGGLE_LOOP, key(KeyEvent.KEYCODE_SLASH, ctrl = true))
        assertEquals("X marks the clip", ShortcutAction.MARK_CLIP, key(KeyEvent.KEYCODE_X))
        assertEquals("Ctrl+Shift+N a new grade version", ShortcutAction.NEW_GRADE_VERSION, key(KeyEvent.KEYCODE_N, shift = true, ctrl = true))
        assertEquals(ShortcutAction.TOGGLE_SNAPPING, key(KeyEvent.KEYCODE_N))
        assertEquals(ShortcutAction.PREVIOUS_GRADE_VERSION, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, shift = true, ctrl = false, alt = true))
        assertEquals(ShortcutAction.NEXT_GRADE_VERSION, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, shift = true, ctrl = false, alt = true))
        assertEquals(ShortcutAction.INSERT_EDIT, key(KeyEvent.KEYCODE_F9))
        assertEquals(ShortcutAction.OVERWRITE_EDIT, key(KeyEvent.KEYCODE_F10))
        assertEquals(ShortcutAction.PLACE_ON_TOP, key(KeyEvent.KEYCODE_F12))
        assertEquals(ShortcutAction.SPLIT, key(KeyEvent.KEYCODE_B))
        assertEquals(ShortcutAction.SPLIT, key(KeyEvent.KEYCODE_B, ctrl = true))
        assertEquals(ShortcutAction.ADD_MARKER, key(KeyEvent.KEYCODE_M))
        assertEquals("Backspace lifts the clip and leaves the gap, as in Resolve", ShortcutAction.DELETE, key(KeyEvent.KEYCODE_DEL))
        assertEquals(ShortcutAction.RIPPLE_DELETE, key(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(ShortcutAction.RIPPLE_DELETE, key(KeyEvent.KEYCODE_DEL, shift = true))
        assertEquals(ShortcutAction.RIPPLE_DELETE, key(KeyEvent.KEYCODE_FORWARD_DEL, shift = true))
        assertEquals(ShortcutAction.TOGGLE_SNAPPING, key(KeyEvent.KEYCODE_N))
        assertEquals(ShortcutAction.DESELECT, key(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `ctrl Z undoes while ctrl shift Z and ctrl Y redo`() {
        assertEquals(ShortcutAction.UNDO, key(KeyEvent.KEYCODE_Z, ctrl = true))
        assertEquals(ShortcutAction.REDO, key(KeyEvent.KEYCODE_Z, shift = true, ctrl = true))
        assertEquals(ShortcutAction.REDO, key(KeyEvent.KEYCODE_Y, ctrl = true))
        assertEquals(ShortcutAction.COPY, key(KeyEvent.KEYCODE_C, ctrl = true))
        assertEquals(ShortcutAction.CUT, key(KeyEvent.KEYCODE_X, ctrl = true))
        assertEquals(ShortcutAction.PASTE, key(KeyEvent.KEYCODE_V, ctrl = true))
        assertEquals(ShortcutAction.RIPPLE_CUT, key(KeyEvent.KEYCODE_X, shift = true, ctrl = true))
        assertEquals(ShortcutAction.PASTE_INSERT, key(KeyEvent.KEYCODE_V, shift = true, ctrl = true))
        assertNull("plain V is not paste", key(KeyEvent.KEYCODE_V))
        assertEquals("ctrl W toggles the before / after wipe", ShortcutAction.TOGGLE_COMPARE, key(KeyEvent.KEYCODE_W, ctrl = true))
        assertEquals(ShortcutAction.CLIP_TRACK_UP, key(KeyEvent.KEYCODE_DPAD_UP, ctrl = true))
        assertEquals(ShortcutAction.CLIP_TRACK_DOWN, key(KeyEvent.KEYCODE_DPAD_DOWN, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_W))
        assertNull(key(KeyEvent.KEYCODE_Z))
        assertEquals("plain Y selects forward, it does not redo", ShortcutAction.SELECT_FORWARD, key(KeyEvent.KEYCODE_Y))
    }

    @Test
    fun `unmapped keys and unmapped modifier combinations resolve to nothing`() {
        assertNull(key(KeyEvent.KEYCODE_Q))
        assertNull(key(KeyEvent.KEYCODE_6))
        assertNull(key(KeyEvent.KEYCODE_0))
        assertNull(key(KeyEvent.KEYCODE_UNKNOWN))
        assertNull(key(KeyEvent.KEYCODE_1, shift = true))
        assertNull(key(KeyEvent.KEYCODE_3, shift = true))
        assertNull(key(KeyEvent.KEYCODE_M, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_S, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_SPACE, ctrl = true))
    }

    @Test
    fun `T cycles the dial mode and comma or period nudge, faster with Shift`() {
        assertEquals(ShortcutAction.TRIM_MODE, key(KeyEvent.KEYCODE_T))
        assertEquals(ShortcutAction.NUDGE_BACK, key(KeyEvent.KEYCODE_COMMA))
        assertEquals(ShortcutAction.NUDGE_FORWARD, key(KeyEvent.KEYCODE_PERIOD))
        assertEquals(ShortcutAction.NUDGE_BACK_FAST, key(KeyEvent.KEYCODE_COMMA, shift = true))
        assertEquals(ShortcutAction.NUDGE_FORWARD_FAST, key(KeyEvent.KEYCODE_PERIOD, shift = true))
    }

    @Test
    fun `D disables or enables the clip and Alt+V pastes attributes`() {
        assertEquals(ShortcutAction.TOGGLE_CLIP_ENABLED, key(KeyEvent.KEYCODE_D))
        assertEquals("Resolve's Match Frame", ShortcutAction.MATCH_FRAME, key(KeyEvent.KEYCODE_F))
        assertEquals(ShortcutAction.PASTE_ATTRIBUTES, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_V, shift = false, ctrl = false, alt = true))
        assertEquals("Ctrl+V still pastes clips", ShortcutAction.PASTE, key(KeyEvent.KEYCODE_V, ctrl = true))
        assertNull(KeyboardShortcuts.resolve(KeyEvent.KEYCODE_D, shift = false, ctrl = false, alt = true))
        assertEquals("Ctrl+Alt+L links or unlinks", ShortcutAction.TOGGLE_LINK, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_L, shift = false, ctrl = true, alt = true))
        assertNull(KeyboardShortcuts.resolve(KeyEvent.KEYCODE_L, shift = false, ctrl = true, alt = false))
    }

    @Test
    fun `Ctrl+A selects every clip and Y every clip from the playhead on`() {
        assertEquals(ShortcutAction.SELECT_ALL, key(KeyEvent.KEYCODE_A, ctrl = true))
        assertEquals(ShortcutAction.SELECT_FORWARD, key(KeyEvent.KEYCODE_Y))
        assertEquals("Ctrl+Y still redoes", ShortcutAction.REDO, key(KeyEvent.KEYCODE_Y, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `Ctrl+T adds the standard transition, Alt+T on picture, Shift+T on sound`() {
        assertEquals(ShortcutAction.ADD_TRANSITION, key(KeyEvent.KEYCODE_T, ctrl = true))
        assertEquals(ShortcutAction.ADD_VIDEO_TRANSITION, KeyboardShortcuts.resolve(KeyEvent.KEYCODE_T, shift = false, ctrl = false, alt = true))
        assertEquals(ShortcutAction.ADD_AUDIO_TRANSITION, key(KeyEvent.KEYCODE_T, shift = true))
        assertEquals("T alone still cycles the trim mode", ShortcutAction.TRIM_MODE, key(KeyEvent.KEYCODE_T))
        assertNull(key(KeyEvent.KEYCODE_T, shift = true, ctrl = true))
    }

    @Test
    fun `Shift+brackets trim to the playhead, with Ctrl as a ripple trim`() {
        assertEquals(ShortcutAction.TRIM_START_TO_PLAYHEAD, key(KeyEvent.KEYCODE_LEFT_BRACKET, shift = true))
        assertEquals(ShortcutAction.TRIM_END_TO_PLAYHEAD, key(KeyEvent.KEYCODE_RIGHT_BRACKET, shift = true))
        assertEquals(ShortcutAction.RIPPLE_START_TO_PLAYHEAD, key(KeyEvent.KEYCODE_LEFT_BRACKET, shift = true, ctrl = true))
        assertEquals(ShortcutAction.RIPPLE_END_TO_PLAYHEAD, key(KeyEvent.KEYCODE_RIGHT_BRACKET, shift = true, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true))
    }

    @Test
    fun `Ctrl+Shift+comma and period swap the selected clip with its neighbour`() {
        assertEquals(ShortcutAction.SWAP_LEFT, key(KeyEvent.KEYCODE_COMMA, shift = true, ctrl = true))
        assertEquals(ShortcutAction.SWAP_RIGHT, key(KeyEvent.KEYCODE_PERIOD, shift = true, ctrl = true))
        assertEquals("Shift alone still nudges five frames", ShortcutAction.NUDGE_BACK_FAST, key(KeyEvent.KEYCODE_COMMA, shift = true))
        assertNull(key(KeyEvent.KEYCODE_COMMA, ctrl = true))
    }

    @Test
    fun `Ctrl+D changes the clip's duration and D alone still disables it`() {
        assertEquals(ShortcutAction.CLIP_DURATION, key(KeyEvent.KEYCODE_D, ctrl = true))
        assertEquals(ShortcutAction.TOGGLE_CLIP_ENABLED, key(KeyEvent.KEYCODE_D))
    }

    @Test
    fun `= types a timecode, + and minus an offset`() {
        assertEquals(ShortcutAction.TIMECODE_ENTRY, key(KeyEvent.KEYCODE_EQUALS))
        assertEquals("Shift+= is +", ShortcutAction.TIMECODE_FORWARD, key(KeyEvent.KEYCODE_EQUALS, shift = true))
        assertEquals(ShortcutAction.TIMECODE_FORWARD, key(KeyEvent.KEYCODE_NUMPAD_ADD))
        assertEquals(ShortcutAction.TIMECODE_BACK, key(KeyEvent.KEYCODE_MINUS))
        assertEquals(ShortcutAction.TIMECODE_BACK, key(KeyEvent.KEYCODE_NUMPAD_SUBTRACT))
    }
}
