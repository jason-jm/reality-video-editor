package com.example.prismstudio.p40b43a88.domain.i18n

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The languages the studio speaks. Simplified Chinese is the source: the code says everything in it, and the other
 * languages translate it at display time ([I18n.translate]). Right-to-left scripts are out of scope.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ZH_HANS("zh-Hans", "简体中文"),
    EN("en", "English"),

    /** QA only (debug builds): English, accented and a third longer, so cut-off and untranslated text stand out. */
    PSEUDO("x-pseudo", "Ƥšéûđö");

    companion object {
        /** The languages a user can pick (QA's pseudo-locale apart). */
        val selectable: List<AppLanguage> get() = entries.filter { it != PSEUDO }

        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** The language closest to the system's [locale]: Chinese for any Chinese, else English. */
        fun forLocale(locale: Locale): AppLanguage = if (locale.language == "zh") ZH_HANS else EN
    }
}

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
        val ownLine: Set<Int> = order.filterIndexed { i, _ -> '\n' in literals[i] || '\n' in literals[i + 1] }.toSet()
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
        var result = translateIn(text, catalog(base), depth = 0)
        if (HAN.containsMatchIn(result)) missing += text
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

    /** [template] (a source template) with [args] in its places, in [language]: for names the app makes (stored as shown). */
    fun format(template: String, vararg args: Any?, language: AppLanguage = this.language): String {
        val base = if (language == AppLanguage.PSEUDO) AppLanguage.EN else language
        val target = if (language == AppLanguage.ZH_HANS) template else catalog(base).targets[template] ?: template
        return fill(target, args.withIndex().associate { (i, v) -> i to v.toString() })
    }

    private fun translateIn(text: String, catalog: Catalog, depth: Int): String {
        catalog.exact[text]?.let { return it }
        if (depth > MAX_DEPTH) return text
        // Surrounding blanks are layout, not words.
        val trimmed = text.trim()
        if (trimmed.length != text.length && trimmed.isNotEmpty()) {
            return text.substring(0, text.indexOf(trimmed)) + translateIn(trimmed, catalog, depth + 1) + text.substring(text.indexOf(trimmed) + trimmed.length)
        }
        var partial: String? = null
        for (template in catalog.candidates(text)) {
            val values = template.match(text) ?: continue
            // A value holding phrase separators the template does not have may be several phrases glued together ("已停用
            // · 离线 · 调色" is not "{0} 调色"): those are translated phrase by phrase below...
            // ...unless the value is itself a whole phrase the table knows (a sentence with its own commas).
            val glued = values.any { (n, v) ->
                n !in template.ownLine && GLUE.any { it in v && it !in template.source } && catalog.exact[v.trim()] == null
            }
            if (glued) continue
            val translated = values.mapValues { (_, v) -> if (HAN.containsMatchIn(v)) translateIn(v, catalog, depth + 1) else v }
            val filled = fill(template.target, translated)
            if (!HAN.containsMatchIn(filled)) return filled
            // A value left Chinese: the user's own words ("字幕：夜跑开始了"), or the wrong template ("{0}色" on "01
            // 主校色"). Kept in case nothing below does better.
            if (partial == null) partial = filled
        }
        val fallback = fallbacks(text, catalog, depth)
        return if (partial != null && HAN.containsMatchIn(fallback)) partial else fallback
    }

    /** Composed text no template matches whole. */
    private fun fallbacks(text: String, catalog: Catalog, depth: Int): String {
        // Lines are said apart ("节目响度 … dBTP\n导出时整体 …"): each on its own.
        if ('\n' in text) return text.split('\n').joinToString("\n") { if (HAN.containsMatchIn(it)) translateIn(it, catalog, depth + 1) else it }
        // A number or name the code put before the words ("03  定格 · …", "01 主校色").
        LEAD.find(text)?.let { lead ->
            val rest = text.substring(lead.value.length)
            val translated = translateIn(rest, catalog, depth + 1)
            if (translated != rest) return lead.value + translated
        }
        val glued = segments(text, catalog, depth)
        return if (glued != text) glued else runs(text, catalog, depth)
    }

    /** The last resort: each run of Chinese in mixed text ("1920×1080 横屏") translated on its own where it can be. */
    private fun runs(text: String, catalog: Catalog, depth: Int): String {
        var changed = false
        val out = RUN.replace(text) { m ->
            val t = catalog.exact[m.value] ?: if (m.value.length < text.length) translateIn(m.value, catalog, depth + 1) else m.value
            if (t != m.value) changed = true
            t
        }
        return if (changed) out else text
    }

    /** Text glued from phrases ("圆形 · 窗口外", "片段：3"): each phrase translated, the punctuation made Latin. */
    private fun segments(text: String, catalog: Catalog, depth: Int): String {
        val parts = SEPARATORS.split(text)
        if (parts.size < 2) return text
        val seps = SEPARATORS.findAll(text).map { it.value }.toList()
        val out = StringBuilder()
        var changed = false
        parts.forEachIndexed { i, part ->
            // A phrase the table has with its punctuation ("画面烧录：") is said with the table's.
            val withSeparator = if (i < seps.size) catalog.exact[part + seps[i]] else null
            if (withSeparator != null) {
                changed = true
                out.append(withSeparator)
                if (!withSeparator.last().isWhitespace()) out.append(' ')
                return@forEachIndexed
            }
            val t = if (HAN.containsMatchIn(part)) translateIn(part, catalog, depth + 1) else part
            if (t != part) changed = true
            out.append(t)
            if (i < seps.size) out.append(LATIN_PUNCTUATION[seps[i]] ?: seps[i])
        }
        return if (changed) out.toString().replace(Regex(" {2,}"), " ").replace(SPACE_BEFORE_PUNCTUATION, "$1").trim() else text
    }

    /** [target] with its places filled from [values] (by number), plural forms `{n,plural,one{…}other{…}}` resolved. */
    internal fun fill(target: String, values: Map<Int, String>): String {
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
                        out.append(plural(inner.substringAfter("plural").trimStart(',', ' '), values[n] ?: ""))
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

    private fun plural(forms: String, value: String): String {
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
        val number = value.replace(",", "").toDoubleOrNull()
        val form = if (number == 1.0) choices["one"] ?: choices["other"] else choices["other"]
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

/**
 * Accessibility descriptions said part by part (release round R8): every part already in the language shown, so one
 * part's words — a clip's or a bin's name — never decide how the next is read.
 */
private val saidInChinese get() = I18n.language == AppLanguage.ZH_HANS

/** [parts] said one after another, joined by the shown language's comma; empty parts left out. */
fun spoken(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(if (saidInChinese) "，" else ", ")

/** "[label]：[value]" in the shown language's punctuation. */
fun labelled(label: String, value: String): String = if (saidInChinese) "$label：$value" else "$label: $value"

/** [items] listed ("红色、蓝色" / "Red, Blue"). */
fun listed(items: List<String>): String = items.joinToString(if (saidInChinese) "、" else ", ")
