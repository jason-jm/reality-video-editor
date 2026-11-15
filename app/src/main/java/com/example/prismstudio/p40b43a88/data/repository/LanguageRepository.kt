package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The interface language the user picked on this headset; null follows the system's. Not part of any project. */
interface LanguageRepository {
    val language: StateFlow<AppLanguage?>

    fun setLanguage(language: AppLanguage?)

    /** Remembers nothing: for previews and tests. */
    class InMemory(initial: AppLanguage? = null) : LanguageRepository {
        private val state = MutableStateFlow(initial)
        override val language: StateFlow<AppLanguage?> = state.asStateFlow()
        override fun setLanguage(language: AppLanguage?) {
            state.value = language
        }
    }
}

/** Kept in the app's shared preferences ("ui"/"language", a language tag; absent = the system's). */
class SharedPrefsLanguageRepository(context: Context) : LanguageRepository {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AppLanguage.fromTag(prefs.getString(KEY, null)))
    override val language: StateFlow<AppLanguage?> = state.asStateFlow()

    override fun setLanguage(language: AppLanguage?) {
        prefs.edit().apply { if (language == null) remove(KEY) else putString(KEY, language.tag) }.apply()
        state.value = language
    }

    private companion object {
        const val KEY = "language"
    }
}
