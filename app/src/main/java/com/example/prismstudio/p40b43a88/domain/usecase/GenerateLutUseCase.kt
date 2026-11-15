package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.dissolveAt
import com.example.prismstudio.p40b43a88.domain.model.windowAt
import java.util.Locale

/**
 * Resolve's Generate LUT: a clip's grade as it colours the picture [clipFrame] frames into the clip, baked
 * into a cube that other projects, apps, cameras and monitors can load. Node 1 goes in whole — its look or
 * LUT too, and between keyframes the dissolve as it stands there; node 2 only when it covers the whole
 * picture (全画面, its qualifier included, since a qualifier keys on colour alone). A shaped window is
 * about where on the picture, which a LUT cannot hold, so it is left out and [Generated.windowLeftOut] says so.
 */
class GenerateLutUseCase(private val grader: BuildGradeLutUseCase = BuildGradeLutUseCase()) {

    data class Generated(val lut: CubeLut, val windowLeftOut: Boolean, val keyed: Boolean)

    operator fun invoke(clipGrade: ColorGrade, clipFrame: Int, size: Int = SIZE): Generated {
        // The noise reductions and Mid/Detail work on neighbouring pixels and frames, which a LUT cannot see: it holds the colour alone.
        val grade = clipGrade.colorOnly
        if (grade.isNeutral) {
            val spatial = listOfNotNull(
                "降噪".takeIf { clipGrade.spatialNr.isActive },
                "时域降噪".takeIf { clipGrade.temporalNr > 0 },
                "中间调细节".takeIf { clipGrade.midDetail != 0f },
            ).takeIf { it.isNotEmpty() }?.let { if (it.size == 1) it[0] else it.dropLast(1).joinToString("、") + "和" + it.last() }
            throw EditRejected(if (spatial != null) "${spatial}无法写进 LUT；当前片段没有其他调色" else "当前片段没有调色，无需生成 LUT")
        }
        val graded = grade.windowAt(clipFrame)
        val at = clipFrame.toFloat()
        val dissolve = graded.dissolveAt(at)?.takeIf { it.mix > 0f && it.from != it.to }
        val first = graded.dissolveAt(at)?.from ?: graded.primary
        val shaped = graded.window?.takeUnless { it.grade.isNeutral }
        val window = shaped?.takeIf { it.shape == WindowShape.FULL }
        val serial = grader.serialCube(graded.activeSerial)
        if (first.isNeutral && dissolve == null && window == null && serial == null) {
            throw EditRejected("节点 02 的窗口只作用于画面的一部分，无法写进 LUT；节点 01 没有调色")
        }
        // A full-frame node's coverage is the same everywhere: 1, or 0 inverted.
        val coverage = window?.coverage(0.5f, 0.5f, 1f) ?: 0f
        val n = size
        val step = 1f / (n - 1)
        val data = FloatArray(n * n * n * 3)
        val rgb = FloatArray(3)
        val rgbB = FloatArray(3)
        val windowed = FloatArray(3)
        val windowedB = FloatArray(3)
        var i = 0
        // As the scopes and the GPU grade a pixel: node 1 (dissolving between keyframes), then node 2.
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            val cr = r * step
            val cg = g * step
            val cb = b * step
            grader.apply(first, cr, cg, cb, rgb)
            if (dissolve != null) {
                grader.apply(dissolve.to, cr, cg, cb, rgbB)
                if (window != null) {
                    grader.apply(window.grade, rgb[0], rgb[1], rgb[2], windowed)
                    grader.apply(window.grade, rgbB[0], rgbB[1], rgbB[2], windowedB)
                    for (c in 0..2) windowed[c] += (windowedB[c] - windowed[c]) * dissolve.mix
                }
                for (c in 0..2) rgb[c] += (rgbB[c] - rgb[c]) * dissolve.mix
            }
            if (window != null) {
                val k = coverage * (window.qualifier?.key(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f)) ?: 1f)
                if (k > 0f) {
                    if (dissolve == null) grader.apply(window.grade, rgb[0], rgb[1], rgb[2], windowed)
                    for (c in 0..2) rgb[c] += (windowed[c] - rgb[c]) * k
                }
            }
            // Serial nodes 03 on, after node 2.
            serial?.apply(rgb[0], rgb[1], rgb[2], rgb)
            data[i++] = rgb[0].coerceIn(0f, 1f)
            data[i++] = rgb[1].coerceIn(0f, 1f)
            data[i++] = rgb[2].coerceIn(0f, 1f)
        }
        return Generated(CubeLut(n, threeD = true, data = data), windowLeftOut = shaped != null && window == null, keyed = grade.keyframes.isNotEmpty())
    }

    companion object {
        /** Resolve's "33 Point Cube". */
        const val SIZE = 33
    }
}

/** Writes LUTs as `.cube` text Resolve and other tools read: a title, the size, the domain, one triple per line. */
object CubeLutWriter {
    fun write(lut: CubeLut, title: String): String = buildString(lut.data.size * 9 + 128) {
        append("TITLE \"").append(title.replace('"', '\'')).append("\"\n")
        append("# Created by ${PRODUCT_NAME}\n")
        append(if (lut.threeD) "LUT_3D_SIZE " else "LUT_1D_SIZE ").append(lut.size).append('\n')
        append("DOMAIN_MIN ").append(triple(lut.domainMin, 0)).append('\n')
        append("DOMAIN_MAX ").append(triple(lut.domainMax, 0)).append("\n\n")
        for (i in lut.data.indices step 3) append(triple(lut.data, i)).append('\n')
    }

    private fun triple(values: FloatArray, at: Int): String =
        String.format(Locale.ROOT, "%.6f %.6f %.6f", values[at], values[at + 1], values[at + 2])
}
