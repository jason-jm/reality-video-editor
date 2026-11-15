package com.example.prismstudio.p40b43a88.data.engine

/**
 * Y′CbCr → RGB for 8-bit decoded frames sampled as GL texture values (0..1): RGB = [matrix] ×
 * ((Y, Cb, Cr) − [offset]), with [matrix] column-major for a GLSL `mat3`.
 */
class YuvToRgb private constructor(val matrix: FloatArray, val offset: FloatArray) {

    /** [matrix] applied to one pixel, for checks off the GPU. */
    fun rgb(y: Float, cb: Float, cr: Float): FloatArray {
        val d = floatArrayOf(y - offset[0], cb - offset[1], cr - offset[2])
        return FloatArray(3) { row -> (0 until 3).sumOf { col -> (matrix[col * 3 + row] * d[col]).toDouble() }.toFloat() }
    }

    /**
     * A whole decoded [frame] as ARGB pixels (a still export). Chroma sits between luma samples, as the GL path
     * samples it: each pixel blends the two nearest chroma samples on each axis.
     */
    fun argb(frame: YuvVideoReader.Frame): IntArray {
        val w = frame.width
        val h = frame.height
        val cw = frame.chromaWidth
        val (x0, x1, fx) = chromaTaps(w, cw)
        val (y0, y1, fy) = chromaTaps(h, frame.chromaHeight)
        val m = matrix
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val top = y0[y] * cw
            val bottom = y1[y] * cw
            val wy = fy[y]
            for (x in 0 until w) {
                val wx = fx[x]
                val luma = (frame.y[y * w + x].toInt() and 0xFF) / 255f - offset[0]
                val cb = blend(frame.u, top, bottom, x0[x], x1[x], wx, wy) - offset[1]
                val cr = blend(frame.v, top, bottom, x0[x], x1[x], wx, wy) - offset[2]
                out[y * w + x] = (0xFF shl 24) or
                    (channel(m[0] * luma + m[3] * cb + m[6] * cr) shl 16) or
                    (channel(m[1] * luma + m[4] * cb + m[7] * cr) shl 8) or
                    channel(m[2] * luma + m[5] * cb + m[8] * cr)
            }
        }
        return out
    }

    companion object {
        // MediaFormat's COLOR_STANDARD_* and COLOR_RANGE_* values, here so the choice runs on the JVM.
        const val STANDARD_BT709 = 1
        const val STANDARD_BT601_PAL = 2
        const val STANDARD_BT601_NTSC = 4
        const val STANDARD_BT2020 = 6
        const val RANGE_FULL = 1
        const val RANGE_LIMITED = 2

        /**
         * The conversion a decoder's output format describes; an unstated standard follows the usual
         * convention (BT.709 from 720 lines up, BT.601 below) and an unstated range is limited (16–235).
         */
        fun of(standard: Int?, range: Int?, height: Int): YuvToRgb {
            val (kr, kb) = when (standard) {
                STANDARD_BT709 -> BT709
                STANDARD_BT601_PAL, STANDARD_BT601_NTSC -> BT601
                STANDARD_BT2020 -> BT2020
                else -> if (height >= HD_LINES) BT709 else BT601
            }
            val full = range == RANGE_FULL
            val lumaScale = if (full) 1.0 else 255.0 / 219.0
            val chromaScale = if (full) 1.0 else 255.0 / 224.0
            val kg = 1 - kr - kb
            val matrix = doubleArrayOf(
                // Y column, Cb column, Cr column.
                lumaScale, lumaScale, lumaScale,
                0.0, -chromaScale * 2 * kb * (1 - kb) / kg, chromaScale * 2 * (1 - kb),
                chromaScale * 2 * (1 - kr), -chromaScale * 2 * kr * (1 - kr) / kg, 0.0,
            )
            val offset = floatArrayOf(if (full) 0f else 16f / 255f, 128f / 255f, 128f / 255f)
            return YuvToRgb(FloatArray(9) { matrix[it].toFloat() }, offset)
        }

        private const val HD_LINES = 720

        /** For each of [size] luma positions: the two chroma samples around it (of [chroma]) and the weight of the second. */
        private fun chromaTaps(size: Int, chroma: Int): Triple<IntArray, IntArray, FloatArray> {
            val first = IntArray(size)
            val second = IntArray(size)
            val weight = FloatArray(size)
            for (i in 0 until size) {
                val c = ((i + 0.5f) / 2f - 0.5f).coerceIn(0f, (chroma - 1).toFloat())
                first[i] = c.toInt()
                second[i] = minOf(first[i] + 1, chroma - 1)
                weight[i] = c - first[i]
            }
            return Triple(first, second, weight)
        }

        @Suppress("LongParameterList")
        private fun blend(plane: ByteArray, top: Int, bottom: Int, left: Int, right: Int, wx: Float, wy: Float): Float {
            fun at(i: Int) = (plane[i].toInt() and 0xFF) / 255f
            val upper = at(top + left) + (at(top + right) - at(top + left)) * wx
            val lower = at(bottom + left) + (at(bottom + right) - at(bottom + left)) * wx
            return upper + (lower - upper) * wy
        }

        private fun channel(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        private val BT709 = 0.2126 to 0.0722
        private val BT601 = 0.299 to 0.114
        private val BT2020 = 0.2627 to 0.0593
    }
}
