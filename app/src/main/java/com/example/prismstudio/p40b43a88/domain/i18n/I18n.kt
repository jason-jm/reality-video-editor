package com.example.prismstudio.p40b43a88.domain.i18n

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import java.util.concurrent.ConcurrentHashMap

/**
 * One language's translations: source templates (Chinese, `{0}`-style placeholders where the code fills in values) to
 * this language's. Composed text is matched against the templates, and the values it captured are translated in turn.
 */
class Catalog(entries: Map<String, String>) {
    /** Every source template to its translation. */
    internal val targets: Map<String, String> = entries
    internal val exact: Map<String, String>
    private val templates: List<Template>
    private val byFirst: Map<Char?, List<Template>>

    init {
        val plain = HashMap<String, String>()
        val list = ArrayList<Template>()
        for ((source, target) in entries) {
            if (PLACEHOLDER.containsMatchIn(source)) list += Template(source, target) else plain[source] = target
            // Fragments the code glues to other text: " 秒" is "{0} 秒", "片段：" is "片段：{0}", "预计 {0} · {1} · " is
            // "预计 {0} · {1} · {2}" (a value in a place the fragment does not number yet).
            val free = (PLACEHOLDER.findAll(source).maxOfOrNull { it.groupValues[1].toInt() } ?: -1) + 1
            if (source.firstOrNull()?.let { it.isWhitespace() || it in LEADING_GLUE } == true) list += Template("{$free}$source", "{$free}$target")
            if (source.lastOrNull()?.let { it.isWhitespace() || it in TRAILING_GLUE } == true) list += Template("$source{$free}", "$target{$free}")
        }
        exact = plain
        // The most specific first: more literal text, fewer guesses.
        templates = list.sortedByDescending { it.literalLength }
        byFirst = templates.groupBy { it.firstChar }
    }

    /** Templates that could match [text], most specific first. */
    internal fun candidates(text: String): List<Template> {
        val first = byFirst[text.firstOrNull()].orEmpty()
        val open = byFirst[null].orEmpty()
        return if (open.isEmpty()) first else (first + open).sortedByDescending { it.literalLength }
    }

    internal class Template(val source: String, val target: String) {
        private val literals = PLACEHOLDER.split(source)
        private val order = PLACEHOLDER.findAll(source).map { it.groupValues[1].toInt() }.toList()
        val literalLength = literals.sumOf { it.length }

        /** Placeholders a line break sets apart ("{0}\n导出时整体 …"): whatever they hold is a message of its own. */
        val messages: Set<Int> = order.filterIndexed { i, _ -> '\n' in literals[i] || '\n' in literals[i + 1] }.toSet() +
            if (source == "失败：{0}") setOf(0) else emptySet()

        // Imported caption names consist of a localized prefix and the user's original subtitle line.
        val verbatim: Set<Int> = if (source == "字幕：{0}") setOf(0) else emptySet()
        val firstChar: Char? = literals.first().firstOrNull()
        // A place at the very end may be empty: the code appends optional text there ("…LUFS$clipping").
        private val regex by lazy {
            val tail = literals.last().isEmpty()
            Regex(
                literals.mapIndexed { i, literal ->
                    Regex.escape(literal) + when {
                        i == literals.lastIndex -> ""
                        tail && i == literals.lastIndex - 1 -> "([\\s\\S]*?)"
                        else -> "([\\s\\S]+?)"
                    }
                }.joinToString(""),
            )
        }

        /** The values [text] holds in this template's places, by placeholder number; null if it does not match. */
        fun match(text: String): Map<Int, String>? {
            if (!text.startsWith(literals.first()) || !text.endsWith(literals.last())) return null
            val m = regex.matchEntire(text) ?: return null
            return order.mapIndexed { i, n -> n to m.groupValues[i + 1] }.toMap()
        }
    }

    companion object {
        internal val PLACEHOLDER = Regex("""\{(\d+)\}""")
        private const val LEADING_GLUE = "·，：；、）)|/"
        private const val TRAILING_GLUE = "：:（(·"

        /** Reads `source<TAB>translation` lines (`\n`, `\t`, `\\` escaped; `#` comments). */
        fun parse(text: String): Catalog {
            val map = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val target = unescape(line.substring(tab + 1))
                if (target.isNotEmpty()) map[unescape(line.substring(0, tab))] = target
            }
            return Catalog(map)
        }

        fun unescape(s: String): String {
            if ('\\' !in s) return s
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(
                        when (s[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> s[i + 1]
                        },
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

/**
 * Translation at display time. The UI's text components, the toasts and the canvas labels pass what they show through
 * [translate]; what the code says stays Chinese (the source and the key), so the editing logic and its tests do not
 * change with the language. Text without Chinese (numbers, timecodes, the user's own Latin names) passes through.
 */
object I18n {
    /** The language shown now (the app sets it from the user's choice or the system's). */
    @Volatile var language: AppLanguage = AppLanguage.ZH_HANS

    private val catalogs = ConcurrentHashMap<AppLanguage, Catalog>()
    private val caches = ConcurrentHashMap<AppLanguage, MutableMap<String, String>>()

    /** Untranslated Chinese seen while showing another language (QA: logged by debug builds). */
    val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Replaces a language's table (tests, and catalogs loaded from elsewhere). */
    fun install(language: AppLanguage, catalog: Catalog) {
        catalogs[language] = catalog
        caches.remove(language)
    }

    fun catalog(language: AppLanguage): Catalog = catalogs.getOrPut(language) {
        val source = if (language == AppLanguage.PSEUDO) AppLanguage.EN else language
        val text = I18n::class.java.getResourceAsStream("/i18n/${source.tag}.tsv")?.bufferedReader()?.use { it.readText() }.orEmpty()
        Catalog.parse(text)
    }

    /** [text] in [language]. */
    fun translate(text: String, language: AppLanguage = this.language): String {
        if (language == AppLanguage.ZH_HANS || !HAN.containsMatchIn(text)) return text
        val cache = caches.getOrPut(language) { ConcurrentHashMap() }
        cache[text]?.let { return it }
        val base = if (language == AppLanguage.PSEUDO) AppLanguage.EN else language
        val translation = translateIn(text, catalog(base), base, depth = 0)
        if (!translation.complete) missing += text
        var result = translation.text
        if (language == AppLanguage.PSEUDO) result = pseudo(result)
        if (cache.size > CACHE_LIMIT) cache.clear()
        cache[text] = result
        return result
    }

    /**
     * [text] said where its usual translation does not fit: a tab's 标题 is "Titles", the word elsewhere "Title". The
     * table's `context|text` entry, else [text] as anywhere.
     */
    fun translate(text: String, context: String?, language: AppLanguage = this.language): String {
        if (context != null && language != AppLanguage.ZH_HANS) {
            val base = if (language == AppLanguage.PSEUDO) AppLanguage.EN else language
            catalog(base).exact["$context|$text"]?.let { return if (language == AppLanguage.PSEUDO) pseudo(it) else it }
        }
        return translate(text, language)
    }

    /**
     * What an edited name stores. A name field starts with the name as shown ([translate]: the app's "时间线 1" reads
     * "Timeline 1"); left as shown, the stored name stays (in the source language, so it keeps following the language
     * shown), else it is what the user typed.
     */
    fun stored(edited: String, original: String, language: AppLanguage = this.language): String =
        if (edited.trim() == translate(original, language).trim()) original else edited

    /** Only the default name and the suffix added by Duplicate belong to the interface. */
    fun projectName(text: String, language: AppLanguage = this.language): String = when {
        text == "未命名项目" -> translate(text, language)
        text.endsWith(" 副本") -> format("{0} 副本", projectName(text.removeSuffix(" 副本"), language), language = language)
        else -> text
    }

    /** [template] (a source template) with [args] in its places, in [language]: for names the app makes (stored as shown). */
    fun format(template: String, vararg args: Any?, language: AppLanguage = this.language): String {
        val base = if (language == AppLanguage.PSEUDO) AppLanguage.EN else language
        val target = if (language == AppLanguage.ZH_HANS) template else catalog(base).targets[template] ?: template
        return fill(target, args.withIndex().associate { (i, v) -> i to v.toString() }, base)
    }

    // A translated Japanese or Traditional Chinese string may contain the exact same Han characters as the source.
    // Track whether each source phrase was resolved, independently of the script of the translated result.
    private data class Translation(val text: String, val complete: Boolean = true)

    private fun translateIn(text: String, catalog: Catalog, language: AppLanguage, depth: Int): Translation {
        if (!HAN.containsMatchIn(text)) return Translation(text)
        catalog.exact[text]?.let { return Translation(it) }
        if (depth > MAX_DEPTH) return Translation(text, false)
        val trimmed = text.trim()
        if (trimmed != text) {
            val result = translateIn(trimmed, catalog, language, depth + 1)
            return result.copy(text = text.replaceRange(text.indexOf(trimmed), text.indexOf(trimmed) + trimmed.length, result.text))
        }
        var partial: Translation? = null
        for (template in catalog.candidates(text)) {
            val values = template.match(text) ?: continue
            val glued = values.any { (n, v) ->
                n !in template.messages && n !in template.verbatim && GLUE.any { it in v && it !in template.source } && catalog.exact[v.trim()] == null
            }
            if (glued) continue
            val translated = values.mapValues { (n, v) ->
                if (n in template.verbatim) Translation(v) else translateIn(v, catalog, language, depth + 1)
            }
            val filled = fill(template.target, translated.mapValues { it.value.text }, language)
            if (translated.values.all { it.complete }) return Translation(filled)
            // Keep a partially resolved template for an unknown value such as a user's clip name.
            if (partial == null) partial = Translation(filled, false)
        }
        // A media filename can itself be a dictionary word ("调色.mp4"). Never translate its basename.
        if (partial == null && MEDIA_FILENAME.matches(text)) return Translation(text)
        val fallback = fallbacks(text, catalog, language, depth)
        return if (fallback.complete) fallback else partial ?: fallback
    }

    private fun fallbacks(text: String, catalog: Catalog, language: AppLanguage, depth: Int): Translation {
        if ('\n' in text) {
            val lines = text.split('\n').map { translateIn(it, catalog, language, depth + 1) }
            return Translation(lines.joinToString("\n") { it.text }, lines.all { it.complete })
        }
        LEAD.find(text)?.let { lead ->
            val rest = text.substring(lead.value.length)
            val translated = translateIn(rest, catalog, language, depth + 1)
            if (translated.complete || translated.text != rest) return translated.copy(text = lead.value + translated.text)
        }
        segments(text, catalog, language, depth)?.let { return it }
        var complete = true
        val replaced = RUN.replace(text) { match ->
            val translated = catalog.exact[match.value]?.let { Translation(it) }
                ?: if (match.value.length < text.length) translateIn(match.value, catalog, language, depth + 1)
                else Translation(match.value, false)
            if (!translated.complete) complete = false
            translated.text
        }
        return Translation(replaced, complete)
    }

    private fun segments(text: String, catalog: Catalog, language: AppLanguage, depth: Int): Translation? {
        val parts = SEPARATORS.split(text)
        if (parts.size < 2) return null
        val separators = SEPARATORS.findAll(text).map { it.value }.toList()
        val out = StringBuilder()
        var resolved = false
        var complete = true
        parts.forEachIndexed { i, part ->
            val separator = separators.getOrNull(i)
            val withSeparator = separator?.let { catalog.exact[part + it] }
            if (withSeparator != null) {
                resolved = true
                out.append(withSeparator)
                if (!language.fullWidthPunctuation && !withSeparator.last().isWhitespace()) out.append(' ')
            } else {
                val translated = translateIn(part, catalog, language, depth + 1)
                if ((translated.complete && HAN.containsMatchIn(part)) || translated.text != part) resolved = true
                if (!translated.complete) complete = false
                out.append(translated.text)
                if (separator != null) out.append(when {
                    separator == "：" -> language.colon
                    separator == "，" -> language.comma
                    language.fullWidthPunctuation -> separator
                    else -> LATIN_PUNCTUATION[separator] ?: separator
                })
            }
        }
        if (!resolved) return null
        val result = out.toString().replace(Regex(" {2,}"), " ").replace(SPACE_BEFORE_PUNCTUATION, "$1").trim()
        return Translation(result, complete)
    }

    /** [target] with its places filled from [values] (by number), plural forms `{n,plural,one{…}other{…}}` resolved. */
    private fun fill(target: String, values: Map<Int, String>, language: AppLanguage): String {
        val out = StringBuilder()
        var i = 0
        while (i < target.length) {
            val c = target[i]
            if (c == '{') {
                val end = matchingBrace(target, i)
                if (end > i) {
                    val inner = target.substring(i + 1, end)
                    val head = inner.substringBefore(',').trim()
                    val n = head.toIntOrNull()
                    if (n != null && inner == head) {
                        out.append(values[n] ?: "")
                        i = end + 1
                        continue
                    }
                    if (n != null && inner.substringAfter(',').trimStart().startsWith("plural")) {
                        out.append(plural(inner.substringAfter("plural").trimStart(',', ' '), values[n] ?: "", language))
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun plural(forms: String, value: String, language: AppLanguage): String {
        val choices = HashMap<String, String>()
        var i = 0
        while (i < forms.length) {
            val open = forms.indexOf('{', i)
            if (open < 0) break
            val key = forms.substring(i, open).trim()
            val close = matchingBrace(forms, open)
            if (close < 0) break
            choices[key] = forms.substring(open + 1, close)
            i = close + 1
        }
        val form = choices[language.pluralCategory(value)] ?: choices["other"]
        return (form ?: value).replace("#", value)
    }

    private fun matchingBrace(s: String, open: Int): Int {
        var depth = 0
        for (j in open until s.length) {
            when (s[j]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return j
            }
        }
        return -1
    }

    /** QA's pseudo-locale: accented, a third longer, bracketed, so clipping shows. */
    private fun pseudo(text: String): String {
        val accented = text.split(PRODUCT_NAME).joinToString(PRODUCT_NAME) { part ->
            part.map { PSEUDO_MAP[it] ?: it }.joinToString("")
        }
        val pad = "·".repeat((text.length / 3).coerceAtLeast(1))
        return "[$accented$pad]"
    }

    val HAN = Regex("[\\u3400-\\u9fff]")
    private val MEDIA_FILENAME = Regex("""[^/\\\n：；，·“”]+(?<!\s)\.(?:mp4|m4v|mov|mkv|avi|webm|mxf|mp3|m4a|wav|flac|aac|ogg|png|jpe?g|heic|webp|gif|srt|vtt|cube)""", RegexOption.IGNORE_CASE)
    private val RUN = Regex("[\\u3400-\\u9fff]+")
    private val SPACE_BEFORE_PUNCTUATION = Regex(" +([;:,!?]|\\.(?=\\s|$))")
    private val LEAD = Regex("^[^\\u3400-\\u9fff]*?[0-9A-Za-z][^\\u3400-\\u9fff]*?\\s+(?=[\\u3400-\\u9fff])")
    private val GLUE = listOf(" · ", "，", "；", "：", "\n")
    private val SEPARATORS = Regex(" · |·|，|：|；|、|（|）|。|！|？|\n| / ")
    private val LATIN_PUNCTUATION = mapOf("，" to ", ", "：" to ": ", "；" to "; ", "、" to ", ", "（" to " (", "）" to ") ", "。" to ". ", "！" to "! ", "？" to "? ")
    private val PSEUDO_MAP = mapOf(
        'a' to 'à', 'e' to 'é', 'i' to 'î', 'o' to 'ö', 'u' to 'û', 'c' to 'ç', 'n' to 'ñ', 's' to 'š', 'y' to 'ý', 'z' to 'ž',
        'A' to 'Å', 'E' to 'É', 'I' to 'Î', 'O' to 'Ö', 'U' to 'Ü', 'C' to 'Ç', 'N' to 'Ñ', 'S' to 'Š', 'Y' to 'Ý', 'D' to 'Đ',
    )
    private const val MAX_DEPTH = 6
    private const val CACHE_LIMIT = 20_000
}

/** This text in the language shown (for code outside composition, such as canvas drawing; composables use `tr`). */
fun String.localized(): String = I18n.translate(this)

/** [parts] said one after another, joined by the shown language's comma; empty parts left out. */
fun spoken(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(I18n.language.comma)

/** "[label]：[value]" in the shown language's punctuation. */
fun labelled(label: String, value: String): String = label + I18n.language.colon + value

/** [items] listed ("红色、蓝色" / "Red, Blue"). */
fun listed(items: List<String>): String = items.joinToString(if (I18n.language.fullWidthPunctuation) "、" else ", ")
