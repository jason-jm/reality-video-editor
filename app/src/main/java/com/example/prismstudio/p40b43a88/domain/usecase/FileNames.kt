package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME

/**
 * A name the app writes to the device's storage (renders, stills, subtitle files) made safe (release round R14): the
 * characters file systems refuse and control characters become "_", and the name is cut to [MAX_BYTES] of UTF-8 — a file
 * name may hold 255 bytes, a Chinese character takes 3, and the folder may add " (2)", a clip's or a language's name and
 * the extension. A 322-byte name used to fail the render at its very end, as if the device were out of space.
 */
object FileNames {
    fun safe(name: String): String {
        val cleaned = name.map { c -> if (c in ILLEGAL || c.isISOControl()) '_' else c }.joinToString("").trim()
        val cut = cleaned.cutToBytes(MAX_BYTES).trimEnd(' ', '.')
        return cut.ifBlank { FALLBACK }
    }

    /** [safe] for a whole file name: the extension ("x.edl") is kept, only the name before it is cleaned and cut. */
    fun safeWithExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        val extension = if (dot > 0) fileName.substring(dot + 1) else ""
        if (extension.isEmpty() || extension.length > MAX_EXTENSION || !extension.all { it.isLetterOrDigit() }) return safe(fileName)
        return "${safe(fileName.substring(0, dot))}.$extension"
    }

    /** The longest start of this string within [max] bytes of UTF-8, never splitting a character. */
    private fun String.cutToBytes(max: Int): String {
        var bytes = 0
        var end = 0
        while (end < length) {
            val code = codePointAt(end)
            val size = when {
                code < 0x80 -> 1
                code < 0x800 -> 2
                code < 0x10000 -> 3
                else -> 4
            }
            if (bytes + size > max) break
            bytes += size
            end += Character.charCount(code)
        }
        return substring(0, end)
    }

    const val MAX_BYTES = 180
    private const val MAX_EXTENSION = 8
    private const val FALLBACK = PRODUCT_NAME
    private const val ILLEGAL = "\\/:*?\"<>|"
}
