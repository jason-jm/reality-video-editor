package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage

/** The About sheet reads the same multilingual document that the store links to. */
internal data class PrivacyPolicy(val title: String, val body: String) {
    companion object {
        private val byLanguage by lazy {
            val markdown = requireNotNull(PrivacyPolicy::class.java.getResourceAsStream("/PRIVACY.md"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val link = Regex("""\[([^\]]+)]\([^)]+\)""")
            markdown.split(Regex("(?m)^## ")).drop(1).associate { section ->
                val language = section.substringBefore('\n').trim()
                val content = section.substringAfter('\n').trim()
                val title = content.substringBefore('\n').trim().removeSurrounding("**")
                val body = content.substringAfter('\n').trim().lineSequence().joinToString("\n") { line ->
                    line.removePrefix("### ").replace("**", "").replace(link) { it.groupValues[1] }
                }
                language to PrivacyPolicy(title, body)
            }
        }

        fun forLanguage(language: AppLanguage): PrivacyPolicy =
            byLanguage[language.nativeName] ?: byLanguage.getValue(AppLanguage.EN.nativeName)
    }
}
