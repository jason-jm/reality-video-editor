package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.roundToInt

/**
 * Resolve's typed timecode (click the viewer's timecode, or press + / − on the timeline): an absolute place, or with a
 * leading + / − an offset — [frames] long, [sign] −1, 0 (absolute) or +1. Digits alone fill from the right in pairs, as
 * Resolve's do: "15" is 15 frames, "100" one second, "10000" one minute; fields may be split by colons ("1:00", "1:02:03:04")
 * and a field past its range carries over ("+90" at 30 fps is three seconds). "2.5秒" / "2.5s" is seconds.
 */
data class TimecodeEntry(val frames: Int, val sign: Int) {
    val relative: Boolean get() = sign != 0

    /** Where it lands from [current]: the absolute place, or [current] moved by the offset; never before 0. */
    fun target(current: Int): Int = (if (relative) current + sign * frames else frames).coerceAtLeast(0)

    companion object {
        private const val MAX_FIELDS = 4

        fun parse(text: String, fps: Int): TimecodeEntry? {
            var t = text.trim().replace('：', ':').replace(';', ':').replace('－', '-').replace('＋', '+')
            val sign = when {
                t.startsWith("+") -> 1
                t.startsWith("-") -> -1
                else -> 0
            }
            if (sign != 0) t = t.substring(1).trim()
            if (t.isEmpty()) return null
            val rate = fps.coerceAtLeast(1)
            val secondsText = t.removeSuffix("秒").removeSuffix("s").removeSuffix("S")
            if (secondsText != t) {
                val seconds = secondsText.trim().toDoubleOrNull()?.takeIf { it >= 0 && it < 24 * 3600 } ?: return null
                return TimecodeEntry((seconds * rate).roundToInt(), sign)
            }
            val fields = if (':' in t) {
                t.split(':').map { part -> part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toInt() ?: return null }
            } else {
                if (!t.all(Char::isDigit) || t.length > MAX_FIELDS * 2) return null
                t.padStart((t.length + 1) / 2 * 2, '0').chunked(2).map { it.toInt() }
            }
            if (fields.size > MAX_FIELDS) return null
            val (h, m, s, f) = List(MAX_FIELDS - fields.size) { 0 } + fields
            return TimecodeEntry(((h * 60 + m) * 60 + s) * rate + f, sign)
        }
    }
}
