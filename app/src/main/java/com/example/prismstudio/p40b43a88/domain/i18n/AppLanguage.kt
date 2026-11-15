package com.example.prismstudio.p40b43a88.domain.i18n

import java.util.Locale
import kotlin.math.abs

/** Interface languages, listed by their own names so the picker is readable in any locale. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ZH_HANS("zh-Hans", "简体中文"),
    ZH_HANT("zh-Hant", "繁體中文"),
    EN("en", "English"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    ES("es", "Español"),
    RU("ru", "Русский"),
    PSEUDO("x-pseudo", "Ƥšéûđö");

    internal val fullWidthPunctuation get() = this == ZH_HANS || this == ZH_HANT || this == JA
    internal val comma get() = if (this == JA) "、" else if (fullWidthPunctuation) "，" else ", "
    internal val colon get() = if (fullWidthPunctuation) "：" else if (this == FR) "\u00a0: " else ": "

    /** Cardinal forms used by counts in the catalogs. The underlying editor supplies unformatted numbers. */
    internal fun pluralCategory(value: String): String {
        val number = value.toDoubleOrNull()?.let(::abs) ?: return "other"
        return when (this) {
            ZH_HANS, ZH_HANT, JA, KO -> "other"
            FR -> if (number < 2) "one" else "other"
            RU -> {
                if (number % 1 != 0.0) return "other"
                val last = (number % 10).toInt()
                val lastTwo = (number % 100).toInt()
                when {
                    last == 1 && lastTwo != 11 -> "one"
                    last in 2..4 && lastTwo !in 12..14 -> "few"
                    else -> "many"
                }
            }
            else -> if (number == 1.0) "one" else "other"
        }
    }

    companion object {
        val selectable = entries.filter { it != PSEUDO }

        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

        fun forLocales(locales: List<Locale>): AppLanguage = locales.firstNotNullOfOrNull { locale ->
            if (locale.language == "zh") {
                when (locale.script) {
                    "Hans" -> ZH_HANS
                    "Hant" -> ZH_HANT
                    else -> if (locale.country in setOf("TW", "HK", "MO")) ZH_HANT else ZH_HANS
                }
            } else selectable.firstOrNull { it.tag == locale.language }
        } ?: EN
    }
}
