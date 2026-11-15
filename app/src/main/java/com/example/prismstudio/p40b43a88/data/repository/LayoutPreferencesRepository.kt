package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the studio lays itself out on this headset — a viewing preference, not part of any project: the ultrawide layout
 * (the window at its widest, the timeline across it like an ultrawide monitor, two viewers above).
 */
interface LayoutPreferencesRepository {
    val ultrawide: StateFlow<Boolean>

    fun setUltrawide(on: Boolean)

    /** Remembers nothing: for previews and tests. */
    class InMemory(initial: Boolean = false) : LayoutPreferencesRepository {
        private val state = MutableStateFlow(initial)
        override val ultrawide: StateFlow<Boolean> = state.asStateFlow()
        override fun setUltrawide(on: Boolean) {
            state.value = on
        }
    }
}

/** Kept in the app's shared preferences, so the layout survives restarts. */
class SharedPrefsLayoutPreferencesRepository(context: Context) : LayoutPreferencesRepository {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY_ULTRAWIDE, false))
    override val ultrawide: StateFlow<Boolean> = state.asStateFlow()

    override fun setUltrawide(on: Boolean) {
        prefs.edit().putBoolean(KEY_ULTRAWIDE, on).apply()
        state.value = on
    }

    private companion object {
        const val KEY_ULTRAWIDE = "ultrawide"
    }
}
