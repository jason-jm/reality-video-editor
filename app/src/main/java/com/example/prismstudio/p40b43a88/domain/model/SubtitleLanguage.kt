package com.example.prismstudio.p40b43a88.domain.model

/**
 * A subtitle track's language (Resolve: one subtitle track per language). [short] fits the track header, [code] is the
 * BCP 47 tag a delivered file is named with (Name.en.srt).
 */
enum class SubtitleLanguage(val label: String, val short: String, val code: String) {
    ZH_HANS("简体中文", "简中", "zh-Hans"),
    ZH_HANT("繁體中文", "繁中", "zh-Hant"),
    EN("English", "EN", "en"),
    JA("日本語", "日", "ja"),
    KO("한국어", "韩", "ko"),
    FR("Français", "FR", "fr"),
    DE("Deutsch", "DE", "de"),
    ES("Español", "ES", "es"),
    RU("Русский", "RU", "ru");

    companion object {
        /** A track made before languages: Simplified Chinese. */
        val DEFAULT = ZH_HANS

        fun byCode(code: String?): SubtitleLanguage? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /**
         * What language a subtitle file holds: a tag in its name first (movie.en.srt, 片名_chs.ass, …), else the script of
         * its text — kana Japanese, Hangul Korean, Cyrillic Russian, Han Chinese (traditional when its traditional-only
         * characters outnumber the simplified ones), Latin English; null for a file with none of these.
         */
        fun detect(fileName: String, text: String): SubtitleLanguage? = fromName(fileName) ?: fromText(text)

        private fun fromName(fileName: String): SubtitleLanguage? {
            val tokens = fileName.lowercase().substringBeforeLast('.').split('.', '_', '-', ' ', '[', ']', '(', ')').filter { it.isNotEmpty() }
            // Two-part tags first (zh-hans, zh_tw), then single words, the last one winning (movie.zh.en.srt is English).
            val pairs = tokens.zipWithNext { a, b -> "$a-$b" }
            pairs.asReversed().forEach { tag -> NAME_TAGS[tag]?.let { return it } }
            tokens.asReversed().forEach { tag -> NAME_TAGS[tag]?.let { return it } }
            return null
        }

        private fun fromText(text: String): SubtitleLanguage? {
            var kana = 0; var hangul = 0; var cyrillic = 0; var han = 0; var latin = 0; var trad = 0; var simp = 0
            for (ch in text) {
                val c = ch.code
                when {
                    c in 0x3040..0x30FF -> kana++
                    c in 0xAC00..0xD7AF || c in 0x1100..0x11FF -> hangul++
                    c in 0x0400..0x04FF -> cyrillic++
                    c in 0x4E00..0x9FFF -> {
                        han++
                        if (ch in TRADITIONAL) trad++ else if (ch in SIMPLIFIED) simp++
                    }
                    ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
                }
            }
            return when {
                kana > 0 && kana * 5 >= han -> JA
                hangul > 0 && hangul >= han -> KO
                cyrillic > latin && cyrillic > han -> RU
                han > 0 && han * 2 >= latin -> if (trad > simp) ZH_HANT else ZH_HANS
                latin > 0 -> EN
                else -> null
            }
        }

        private val NAME_TAGS: Map<String, SubtitleLanguage> = buildMap {
            listOf("zh-hans", "zh-cn", "zh-sg", "chs", "sc", "gb", "简体", "简中", "中文", "zh", "chi", "zho", "chinese").forEach { put(it, ZH_HANS) }
            listOf("zh-hant", "zh-tw", "zh-hk", "cht", "tc", "big5", "繁體", "繁体", "繁中").forEach { put(it, ZH_HANT) }
            listOf("en", "eng", "english").forEach { put(it, EN) }
            listOf("ja", "jp", "jpn", "japanese", "日本語").forEach { put(it, JA) }
            listOf("ko", "kor", "korean").forEach { put(it, KO) }
            listOf("fr", "fre", "fra", "french").forEach { put(it, FR) }
            listOf("de", "ger", "deu", "german").forEach { put(it, DE) }
            listOf("es", "spa", "spanish").forEach { put(it, ES) }
            listOf("ru", "rus", "russian").forEach { put(it, RU) }
        }

        /** Common characters written differently in the two scripts, traditional and simplified. */
        private const val TRADITIONAL = "們這個說時為會來對國學後點裡麼還動們過開關頭東車長門問間現實電話書見讓從號樣經應聽寫歡謝"
        private const val SIMPLIFIED = "们这个说时为会来对国学后点里么还动们过开关头东车长门问间现实电话书见让从号样经应听写欢谢"
    }
}
