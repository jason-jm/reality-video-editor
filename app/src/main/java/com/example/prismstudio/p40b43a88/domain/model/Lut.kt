package com.example.prismstudio.p40b43a88.domain.model

/** A grade's imported LUT: which one ([id], from its content) and its name, kept to say which is missing. */
data class LutRef(val id: String, val name: String)

/** An imported LUT in the library: [size] points per axis, a 3D cube or ([threeD] false) three 1D curves. */
data class LutInfo(val id: String, val name: String, val size: Int, val threeD: Boolean) {
    val ref: LutRef get() = LutRef(id, name)

    /** "3D · 33³" or "1D · 1024", as tiles and toasts describe it. */
    val detail: String get() = if (threeD) "3D · $size³" else "1D · $size"
}

/**
 * A colour lookup table as a `.cube` file holds it: a 3D cube of `size³` RGB entries (red changing
 * fastest, then green, then blue) or ([threeD] false) `size` entries of three 1D curves, over the input
 * domain [domainMin]…[domainMax] per channel. Colours outside the domain clamp to its edge; the output
 * is what the table says (it may leave 0…1; the grade clamps at the end).
 */
class CubeLut(
    val size: Int,
    val threeD: Boolean,
    /** RGB triples, [size]³ (3D) or [size] (1D) of them. */
    val data: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
) {
    init {
        require(size >= 2) { "a LUT needs at least two points per axis" }
        require(data.size == 3 * if (threeD) size * size * size else size) { "a ${if (threeD) "3D" else "1D"} LUT of $size needs its entries" }
    }

    /** Looks [r], [g], [b] up into [out]: trilinear through the cube, or linear along each 1D curve. */
    fun apply(r: Float, g: Float, b: Float, out: FloatArray) {
        val last = size - 1
        val x = position(r, 0) * last
        val y = position(g, 1) * last
        val z = position(b, 2) * last
        if (!threeD) {
            out[0] = curve(x, 0)
            out[1] = curve(y, 1)
            out[2] = curve(z, 2)
            return
        }
        val x0 = x.toInt().coerceAtMost(last - 1)
        val y0 = y.toInt().coerceAtMost(last - 1)
        val z0 = z.toInt().coerceAtMost(last - 1)
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0
        for (c in 0..2) {
            val c00 = lerp(at(x0, y0, z0, c), at(x0 + 1, y0, z0, c), fx)
            val c10 = lerp(at(x0, y0 + 1, z0, c), at(x0 + 1, y0 + 1, z0, c), fx)
            val c01 = lerp(at(x0, y0, z0 + 1, c), at(x0 + 1, y0, z0 + 1, c), fx)
            val c11 = lerp(at(x0, y0 + 1, z0 + 1, c), at(x0 + 1, y0 + 1, z0 + 1, c), fx)
            out[c] = lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz)
        }
    }

    /** Where [v] falls in channel [c]'s domain, 0…1. */
    private fun position(v: Float, c: Int): Float {
        val span = domainMax[c] - domainMin[c]
        return if (span <= 0f) 0f else ((v - domainMin[c]) / span).coerceIn(0f, 1f)
    }

    private fun at(r: Int, g: Int, b: Int, c: Int): Float = data[((b * size + g) * size + r) * 3 + c]

    private fun curve(x: Float, c: Int): Float {
        val i = x.toInt().coerceAtMost(size - 2)
        return lerp(data[i * 3 + c], data[(i + 1) * 3 + c], x - i)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/**
 * Where grades find imported LUTs. [cube] is null for a LUT that is not in the library (the grade then
 * leaves the picture as it is and the Color page says it is missing); [generation] changes whenever the
 * library does, so LUTs baked from grades can be baked again.
 */
fun interface LutSource {
    fun cube(id: String): CubeLut?

    val generation: Int get() = 0

    companion object {
        val NONE = LutSource { null }
    }
}
