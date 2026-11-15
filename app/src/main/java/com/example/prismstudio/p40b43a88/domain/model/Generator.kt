package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's Generators (Effects Library → Generators): pictures made rather than shot — a solid colour, a two-colour
 * gradient, SMPTE colour bars. Each becomes an image in the media pool, so on the timeline it is an ordinary picture clip:
 * it layers by track, takes grades, transitions, transforms and composite modes, and renders like any still.
 */
enum class Generator(val label: String, val detail: String, val top: Int, val bottom: Int = top, val bars: Boolean = false) {
    BLACK("黑色纯色", "纯色 · 黑场、垫底", 0xFF000000.toInt()),
    WHITE("白色纯色", "纯色 · 白场、闪白", 0xFFFFFFFF.toInt()),
    GRAY("中灰纯色", "纯色 · 18% 灰", 0xFF767676.toInt()),
    BLUE("蓝色纯色", "纯色 · 文字背景", 0xFF1F3A93.toInt()),
    GREEN("绿色纯色", "纯色 · 抠像测试", 0xFF00B140.toInt()),
    NIGHT("夜空渐变", "渐变 · 深蓝到黑", 0xFF1B2A6B.toInt(), 0xFF05060C.toInt()),
    SUNSET("暖阳渐变", "渐变 · 橙到紫", 0xFFF39A3D.toInt(), 0xFF5B2A86.toInt()),
    BARS("SMPTE 彩条", "测试图 · 75% 彩条", 0xFFBFBFBF.toInt(), bars = true);

    /** The media pool's name for it at [width] × [height]. */
    fun assetName(width: Int, height: Int) = "$label ${width}×$height"

    /** The image's pixels (ARGB, row by row). */
    fun pixels(width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) out[y * width + x] = if (bars) bar(x, y, width, height) else rowColor(y, height)
        }
        return out
    }

    private fun rowColor(y: Int, height: Int): Int {
        if (top == bottom || height == 1) return top
        val t = y.toFloat() / (height - 1)
        fun mix(shift: Int) = ((top shr shift and 0xFF) * (1 - t) + (bottom shr shift and 0xFF) * t + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * SMPTE RP 219-style 75% bars in three bands: seven colour bars (2/3 of the height), the reverse blue strip (1/12),
     * and −I, 100% white, +Q, black and the PLUGE steps along the bottom (1/4).
     */
    private fun bar(x: Int, y: Int, width: Int, height: Int): Int {
        val seventh = (x * 7 / width).coerceIn(0, 6)
        return when {
            y < height * 2 / 3 -> BARS_TOP[seventh]
            y < height * 3 / 4 -> BARS_MIDDLE[seventh]
            else -> {
                val sixth = x * 6 / width
                if (sixth < 5) BARS_BOTTOM[sixth.coerceIn(0, 4)] else {
                    // The PLUGE: blacker than black, black, a little above black, then black.
                    val pluge = ((x - width * 5 / 6) * 4 / (width - width * 5 / 6).coerceAtLeast(1)).coerceIn(0, 3)
                    PLUGE[pluge]
                }
            }
        }
    }

    companion object {
        private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        /** 75% grey, yellow, cyan, green, magenta, red, blue. */
        val BARS_TOP = intArrayOf(rgb(191, 191, 191), rgb(191, 191, 0), rgb(0, 191, 191), rgb(0, 191, 0), rgb(191, 0, 191), rgb(191, 0, 0), rgb(0, 0, 191))
        val BARS_MIDDLE = intArrayOf(rgb(0, 0, 191), rgb(19, 19, 19), rgb(191, 0, 191), rgb(19, 19, 19), rgb(0, 191, 191), rgb(19, 19, 19), rgb(191, 191, 191))
        val BARS_BOTTOM = intArrayOf(rgb(0, 33, 76), rgb(255, 255, 255), rgb(50, 0, 106), rgb(19, 19, 19), rgb(19, 19, 19))
        val PLUGE = intArrayOf(rgb(9, 9, 9), rgb(19, 19, 19), rgb(29, 29, 29), rgb(19, 19, 19))

        /** A generator's media id at [width] × [height]: the same generator at the same size is one image, made once. */
        fun assetId(generator: Generator, width: Int, height: Int) = "g-${generator.name.lowercase()}-${width}x$height"
    }
}
