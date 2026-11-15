package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keys that make keyboard focus visible (release round R13): moving focus, not typing or pressing. */
class FocusVisibilityTest {

    @Test
    fun `tab and the arrows move focus`() {
        listOf(Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight).forEach {
            assertTrue("$it", FocusVisibility.isNavigationKey(it))
        }
    }

    @Test
    fun `typing, enter and the editing shortcuts do not`() {
        listOf(Key.Enter, Key.Spacebar, Key.A, Key.B, Key.Delete, Key.Escape).forEach {
            assertFalse("$it", FocusVisibility.isNavigationKey(it))
        }
    }
}
