package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.dissolveAt
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.GradeDissolve
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ScopeData
import com.example.prismstudio.p40b43a88.domain.model.ScopeMode
import kotlin.math.roundToInt

/** Grades a frame with the same maths as the GPU LUT and measures it. */
class ComputeScopesUseCase(private val grader: BuildGradeLutUseCase = BuildGradeLutUseCase()) {

    /**
     * Applies [grade] to every pixel of a source [frame] (ARGB in, ARGB out): the primary node
     * everywhere, then the window node as far as its window covers the pixel. The window lives in the
     * picture the clip is fitted into (the timeline raster, [pictureAspect] = width / height), as on the GPU.
     * A keyed node 1 grades as it stands [clipFrame] frames into the clip: the two keyframes' looks
     * (and window composites) mixed, as the GPU dissolves between their LUTs.
     */
    fun grade(
        frame: FramePixels,
        grade: ColorGrade,
        pictureAspect: Float = frame.width.toFloat() / frame.height.coerceAtLeast(1),
        clipFrame: Float = 0f,
        /** Where the clip's picture sits in the raster (Resolve's Scaling): where a window lands on it; null fits it. */
        framing: Framing? = null,
    ): FramePixels {
        if (grade.isNeutral) return frame
        val dissolve = grade.dissolveAt(clipFrame)?.takeIf { it.mix > 0f && it.from != it.to }
        val first = grade.dissolveAt(clipFrame)?.from ?: grade
        val window = grade.window?.takeUnless { it.grade.isNeutral }
        // Serial nodes 03 on, after node 2: looked up in their table as the GPU's post LUT is.
        val serial = grader.serialCube(grade.activeSerial)
        val out = IntArray(frame.pixels.size)
        val rgb = FloatArray(3)
        val rgbB = FloatArray(3)
        val windowed = FloatArray(3)
        val windowedB = FloatArray(3)
        // Where the source sits in the raster (centred, fitted / filled / stretched / at its size): its size in raster widths and heights.
        val (spanX, spanY) = framing ?: Framing.fit(frame.width.toFloat() / frame.height.coerceAtLeast(1), pictureAspect)
        // A blurred matte needs every pixel's key first.
        val qualifier = window?.qualifier
        if (window != null && qualifier != null && qualifier.blurRadius > 0f) {
            return gradeBlurred(frame, first, dissolve, window, qualifier, pictureAspect, spanX, spanY, serial)
        }
        for (i in frame.pixels.indices) {
            val p = frame.pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            grader.apply(first, r, g, b, rgb)
            if (dissolve != null) {
                grader.apply(dissolve.to, r, g, b, rgbB)
                // Node 2 composites on each keyframe's look, mixed like the two composite LUTs.
                if (window != null) {
                    grader.apply(window.grade, rgb[0], rgb[1], rgb[2], windowed)
                    grader.apply(window.grade, rgbB[0], rgbB[1], rgbB[2], windowedB)
                    for (c in 0..2) windowed[c] += (windowedB[c] - windowed[c]) * dissolve.mix
                }
                for (c in 0..2) rgb[c] += (rgbB[c] - rgb[c]) * dissolve.mix
            }
            if (window != null) {
                // Pixel centre in the raster, y up (rows run top to bottom).
                val x = 0.5f + ((i % frame.width + 0.5f) / frame.width - 0.5f) * spanX
                val y = 0.5f + (0.5f - (i / frame.width + 0.5f) / frame.height) * spanY
                // The qualifier keys on what node 2 receives: the primary-graded colour.
                val k = window.coverage(x, y, pictureAspect) *
                    (window.qualifier?.key(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f)) ?: 1f)
                if (k > 0f) {
                    if (dissolve == null) grader.apply(window.grade, rgb[0], rgb[1], rgb[2], windowed)
                    for (c in 0..2) rgb[c] += (windowed[c] - rgb[c]) * k
                }
            }
            serial?.apply(rgb[0], rgb[1], rgb[2], rgb)
            out[i] = (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
        }
        return FramePixels(frame.width, frame.height, out)
    }

    /**
     * [grade] on [frame] when node 2's qualifier blurs its matte: node 1 and node 2's colours and the
     * qualifier's key for every pixel, then each key averaged over [HslQualifier.BLUR_TAPS] (the shader's)
     * at the blur radius in this frame's pixels, and the node composited by it.
     */
    @Suppress("LongParameterList")
    private fun gradeBlurred(
        frame: FramePixels,
        first: ColorGrade,
        dissolve: GradeDissolve?,
        window: PowerWindow,
        qualifier: HslQualifier,
        pictureAspect: Float,
        spanX: Float,
        spanY: Float,
        serial: CubeLut?,
    ): FramePixels {
        val w = frame.width
        val n = frame.pixels.size
        val base = FloatArray(n * 3)
        val node2 = FloatArray(n * 3)
        val keys = FloatArray(n)
        val rgb = FloatArray(3)
        val rgbB = FloatArray(3)
        val windowed = FloatArray(3)
        val windowedB = FloatArray(3)
        for (i in 0 until n) {
            val p = frame.pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            grader.apply(first, r, g, b, rgb)
            grader.apply(window.grade, rgb[0], rgb[1], rgb[2], windowed)
            if (dissolve != null) {
                grader.apply(dissolve.to, r, g, b, rgbB)
                grader.apply(window.grade, rgbB[0], rgbB[1], rgbB[2], windowedB)
                for (c in 0..2) {
                    windowed[c] += (windowedB[c] - windowed[c]) * dissolve.mix
                    rgb[c] += (rgbB[c] - rgb[c]) * dissolve.mix
                }
            }
            for (c in 0..2) {
                base[i * 3 + c] = rgb[c]
                node2[i * 3 + c] = windowed[c]
            }
            keys[i] = qualifier.key(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f))
        }
        val radius = qualifier.blurRadius * frame.height
        val out = IntArray(n)
        for (i in 0 until n) {
            val px = i % w
            val py = i / w
            var matte = 0f
            for ((dx, dy, weight) in HslQualifier.BLUR_TAPS) {
                val tx = (px + dx * radius).roundToInt().coerceIn(0, w - 1)
                val ty = (py + dy * radius).roundToInt().coerceIn(0, frame.height - 1)
                matte += weight * keys[ty * w + tx]
            }
            val x = 0.5f + ((px + 0.5f) / w - 0.5f) * spanX
            val y = 0.5f + (0.5f - (py + 0.5f) / frame.height) * spanY
            val k = window.coverage(x, y, pictureAspect) * matte
            for (c in 0..2) rgb[c] = base[i * 3 + c] + (node2[i * 3 + c] - base[i * 3 + c]) * k
            serial?.apply(rgb[0], rgb[1], rgb[2], rgb)
            out[i] = (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
        }
        return FramePixels(frame.width, frame.height, out)
    }

    /**
     * What of [frame] the raster shows when the picture sits there as [framing] says: where it overflows (filled, or
     * larger than the raster at its own size) only its middle, so scopes and stills read what the viewer shows; all of
     * it otherwise.
     */
    fun framed(frame: FramePixels, framing: Framing?): FramePixels {
        if (framing == null) return frame
        val w = (frame.width / framing.spanX.coerceAtLeast(1f)).roundToInt().coerceIn(1, frame.width)
        val h = (frame.height / framing.spanY.coerceAtLeast(1f)).roundToInt().coerceIn(1, frame.height)
        if (w == frame.width && h == frame.height) return frame
        val x0 = (frame.width - w) / 2
        val y0 = (frame.height - h) / 2
        return FramePixels(w, h, IntArray(w * h) { frame.pixels[(y0 + it / w) * frame.width + x0 + it % w] })
    }

    /**
     * The source colour (ARGB, averaged over 3 × 3 pixels) the picture point [u], [v] (0…1 from the left
     * / bottom of the raster) shows, with [frame] centred in a raster of [pictureAspect] as [framing] places it (null:
     * fitted); null outside the picture.
     */
    fun sourcePixel(frame: FramePixels, u: Float, v: Float, pictureAspect: Float, framing: Framing? = null): Int? =
        sourcePixelAt(frame, u, v, pictureAspect, framing)

    operator fun invoke(frame: FramePixels, mode: ScopeMode): ScopeData = when (mode) {
        ScopeMode.WAVEFORM -> waveform(frame, parade = false)
        ScopeMode.PARADE -> waveform(frame, parade = true)
        ScopeMode.VECTORSCOPE -> vectorscope(frame)
        ScopeMode.HISTOGRAM -> histogram(frame)
    }

    private fun waveform(frame: FramePixels, parade: Boolean): ScopeData {
        val columns = minOf(frame.width, MAX_COLUMNS).coerceAtLeast(1)
        val rows = LEVELS
        val planes = List(if (parade) 3 else 1) { FloatArray(columns * rows) }
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            val p = frame.pixels[y * frame.width + x]
            val col = x * columns / frame.width
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (parade) {
                planes[0][row(r) * columns + col] += 1f
                planes[1][row(g) * columns + col] += 1f
                planes[2][row(b) * columns + col] += 1f
            } else {
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt()
                planes[0][row(luma) * columns + col] += 1f
            }
        }
        val perColumn = frame.height.toFloat() * frame.width / columns
        planes.forEach { normalise(it, perColumn * 0.08f) }
        return ScopeData(if (parade) ScopeMode.PARADE else ScopeMode.WAVEFORM, columns, rows, planes)
    }

    private fun vectorscope(frame: FramePixels): ScopeData {
        val size = VECTOR_SIZE
        val grid = FloatArray(size * size)
        for (p in frame.pixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val cb = (b - y) / 1.8556f
            val cr = (r - y) / 1.5748f
            val x = ((cb + 0.5f) * (size - 1)).roundToInt().coerceIn(0, size - 1)
            val v = ((0.5f - cr) * (size - 1)).roundToInt().coerceIn(0, size - 1)
            grid[v * size + x] += 1f
        }
        normalise(grid, frame.pixels.size * 0.01f)
        return ScopeData(ScopeMode.VECTORSCOPE, size, size, listOf(grid))
    }

    private fun histogram(frame: FramePixels): ScopeData {
        val planes = List(3) { FloatArray(BINS) }
        for (p in frame.pixels) {
            planes[0][((p shr 16) and 0xFF) * BINS / 256] += 1f
            planes[1][((p shr 8) and 0xFF) * BINS / 256] += 1f
            planes[2][(p and 0xFF) * BINS / 256] += 1f
        }
        val peak = planes.maxOf { plane -> plane.maxOrNull() ?: 0f }.coerceAtLeast(1f)
        planes.forEach { plane -> for (i in plane.indices) plane[i] = plane[i] / peak }
        return ScopeData(ScopeMode.HISTOGRAM, BINS, 1, planes)
    }

    /** 8-bit value to a row where row 0 is the top (100 IRE). */
    private fun row(value: Int): Int = (LEVELS - 1) - (value.coerceIn(0, 255) * (LEVELS - 1) / 255)

    private fun normalise(plane: FloatArray, saturation: Float) {
        val s = saturation.coerceAtLeast(1f)
        for (i in plane.indices) plane[i] = (plane[i] / s).coerceAtMost(1f)
    }

    private fun channel(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).roundToInt()

    companion object {
        const val LEVELS = 101
        const val MAX_COLUMNS = 160
        const val VECTOR_SIZE = 96
        const val BINS = 64
    }
}

/**
 * The colour (0xAARRGGBB, a 3 × 3 average) of [frame] at the point (u, v) of the picture (0…1 across, from the bottom up),
 * with [frame] in a raster of [pictureAspect] as [framing] places it (null: fitted); null outside the picture. The
 * Color page's eyedropper and the Edit page keyer's share it.
 */
fun sourcePixelAt(frame: FramePixels, u: Float, v: Float, pictureAspect: Float, framing: Framing? = null): Int? {
    val (spanX, spanY) = framing ?: Framing.fit(frame.width.toFloat() / frame.height.coerceAtLeast(1), pictureAspect)
    val sx = (u - 0.5f) / spanX + 0.5f
    val sy = (v - 0.5f) / spanY + 0.5f
    if (sx !in 0f..1f || sy !in 0f..1f) return null
    val cx = (sx * frame.width).toInt().coerceIn(0, frame.width - 1)
    val cy = ((1f - sy) * frame.height).toInt().coerceIn(0, frame.height - 1)
    var r = 0
    var g = 0
    var b = 0
    var n = 0
    for (y in (cy - 1).coerceAtLeast(0)..(cy + 1).coerceAtMost(frame.height - 1)) {
        for (x in (cx - 1).coerceAtLeast(0)..(cx + 1).coerceAtMost(frame.width - 1)) {
            val p = frame.pixels[y * frame.width + x]
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
    }
    return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
}
