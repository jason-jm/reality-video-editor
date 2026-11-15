package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Why a `.cube` file cannot be used, said to the colourist. */
class LutFormatException(message: String) : IllegalArgumentException(message)

/**
 * Reads `.cube` LUT files as Resolve writes and reads them: `TITLE`, `LUT_3D_SIZE` or `LUT_1D_SIZE`,
 * `DOMAIN_MIN` / `DOMAIN_MAX` (or Resolve's `LUT_3D_INPUT_RANGE` / `LUT_1D_INPUT_RANGE`), `#` comments,
 * then one RGB triple per line, red changing fastest. Other keywords (e.g. Resolve's video-range flags)
 * are skipped; a file with both a 1D and a 3D table is refused.
 */
object CubeLutParser {
    /** 65³ is the largest cube Resolve exports; larger ones would take tens of megabytes. */
    const val MAX_3D_SIZE = 65
    const val MAX_1D_SIZE = 65_536

    /** Longest library name kept. */
    private const val MAX_NAME = 40

    /** The LUT, the name it goes by ([TITLE] or the file name) and its id, taken from its content. */
    data class Parsed(val name: String, val lut: CubeLut, val id: String)

    /** The text of a `.cube` file: UTF-8, a byte-order mark dropped. */
    fun decode(bytes: ByteArray): String {
        val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        return if (bom) String(bytes, 3, bytes.size - 3, Charsets.UTF_8) else String(bytes, Charsets.UTF_8)
    }

    fun parse(text: String, fileName: String): Parsed {
        var title: String? = null
        var size3 = 0
        var size1 = 0
        val min = floatArrayOf(0f, 0f, 0f)
        val max = floatArrayOf(1f, 1f, 1f)
        var data: FloatArray? = null
        var expected = 0
        var rows = 0
        var lineNo = 0
        for (raw in text.lineSequence()) {
            lineNo++
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val key = line.takeWhile { !it.isWhitespace() }
            // A keyword starts with a letter; "NaN 0 0" is a (bad) data line.
            if (line[0].isLetter() && key.toFloatOrNull() == null) {
                if (data != null) throw LutFormatException("第 $lineNo 行：数据之后不能再有设置项")
                val rest = line.substring(key.length).trim()
                when (key.uppercase()) {
                    "TITLE" -> title = rest.removeSurrounding("\"").trim()
                    "LUT_3D_SIZE" -> size3 = rest.toIntOrNull() ?: throw LutFormatException("第 $lineNo 行：LUT_3D_SIZE 不是整数")
                    "LUT_1D_SIZE" -> size1 = rest.toIntOrNull() ?: throw LutFormatException("第 $lineNo 行：LUT_1D_SIZE 不是整数")
                    "DOMAIN_MIN" -> numbers(rest, 3, lineNo).copyInto(min)
                    "DOMAIN_MAX" -> numbers(rest, 3, lineNo).copyInto(max)
                    "LUT_3D_INPUT_RANGE", "LUT_1D_INPUT_RANGE" -> numbers(rest, 2, lineNo).let { (lo, hi) -> min.fill(lo); max.fill(hi) }
                }
                continue
            }
            val table = data ?: run {
                expected = tableRows(size1, size3)
                if ((0..2).any { max[it] <= min[it] }) throw LutFormatException("DOMAIN_MAX 必须大于 DOMAIN_MIN")
                FloatArray(expected * 3).also { data = it }
            }
            if (rows >= expected) throw LutFormatException("数据行数过多：应为 $expected 行")
            if (!triple(line, table, rows * 3)) throw LutFormatException("第 $lineNo 行不是三个数值：$line".take(60))
            rows++
        }
        val table = data ?: run {
            tableRows(size1, size3)
            throw LutFormatException("LUT 没有数据")
        }
        if (rows < expected) throw LutFormatException("LUT 数据不完整：应为 $expected 行，只有 $rows 行")
        val threeD = size3 > 0
        val lut = CubeLut(if (threeD) size3 else size1, threeD, table, min, max)
        val name = (title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.').ifBlank { "LUT" }).take(MAX_NAME)
        return Parsed(name, lut, contentId(lut))
    }

    /**
     * An id taken from what the LUT does — size, domain and entries — so the same file imported twice (or
     * on another headset) is the same LUT and a grade that names it finds it again.
     */
    fun contentId(lut: CubeLut): String {
        val bytes = ByteBuffer.allocate(4 * (2 + 6 + lut.data.size))
        bytes.putInt(if (lut.threeD) 3 else 1).putInt(lut.size)
        lut.domainMin.forEach { bytes.putFloat(it) }
        lut.domainMax.forEach { bytes.putFloat(it) }
        lut.data.forEach { bytes.putFloat(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array())
        return "lut-" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** Rows of data the sizes ask for; refuses missing, mixed and oversized tables. */
    private fun tableRows(size1: Int, size3: Int): Int = when {
        size1 > 0 && size3 > 0 -> throw LutFormatException("暂不支持同时含 1D 和 3D 表的 LUT")
        size3 > MAX_3D_SIZE -> throw LutFormatException("LUT 过大：最多支持 $MAX_3D_SIZE³，这个是 $size3³")
        size3 >= 2 -> size3 * size3 * size3
        size1 > MAX_1D_SIZE -> throw LutFormatException("1D LUT 过大：最多 $MAX_1D_SIZE 个点")
        size1 >= 2 -> size1
        size3 != 0 || size1 != 0 -> throw LutFormatException("LUT 尺寸无效")
        else -> throw LutFormatException("不是有效的 .cube LUT：缺少 LUT_3D_SIZE 或 LUT_1D_SIZE")
    }

    private fun numbers(text: String, count: Int, lineNo: Int): FloatArray {
        val out = FloatArray(count)
        if (!parseNumbers(text, out, 0, count)) throw LutFormatException("第 $lineNo 行应有 $count 个数值")
        return out
    }

    private fun triple(line: String, into: FloatArray, at: Int): Boolean = parseNumbers(line, into, at, 3)

    /** Exactly [count] finite numbers separated by white space, written to [into] from [at]. */
    private fun parseNumbers(line: String, into: FloatArray, at: Int, count: Int): Boolean {
        var i = 0
        val length = line.length
        for (n in 0 until count) {
            while (i < length && line[i].isWhitespace()) i++
            if (i >= length) return false
            val start = i
            while (i < length && !line[i].isWhitespace()) i++
            val value = try {
                java.lang.Float.parseFloat(line.substring(start, i))
            } catch (e: NumberFormatException) {
                return false
            }
            if (!value.isFinite()) return false
            into[at + n] = value
        }
        while (i < length && line[i].isWhitespace()) i++
        return i == length
    }
}
