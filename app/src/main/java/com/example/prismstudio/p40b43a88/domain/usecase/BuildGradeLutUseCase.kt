package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.HueCurves
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.LutSource
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bakes a [ColorGrade] into an N×N×N lookup table (`cube[r][g][b]` = ARGB output), the format the
 * video pipeline applies on the GPU. The same [apply] function grades single colours for scopes and
 * thumbnails, so every surface shows the same result. Imported LUTs come from [luts].
 */
class BuildGradeLutUseCase(private val size: Int = 33, private val luts: LutSource = LutSource.NONE) {

    /** The cube of [grade]'s primary node, followed by [then] when given (a window node's composite). */
    operator fun invoke(grade: ColorGrade, then: ColorGrade? = null): Array<Array<IntArray>> {
        val n = size
        val inv = 1f / (n - 1)
        val out = FloatArray(3)
        return Array(n) { r ->
            Array(n) { g ->
                IntArray(n) { b ->
                    apply(grade, r * inv, g * inv, b * inv, out)
                    if (then != null) apply(then, out[0], out[1], out[2], out)
                    argb(out)
                }
            }
        }
    }

    /** The cube of serial nodes [grades] in turn (the GPU's post LUT, after node 2). */
    fun chain(grades: List<ColorGrade>): Array<Array<IntArray>> {
        val n = size
        val inv = 1f / (n - 1)
        val out = FloatArray(3)
        return Array(n) { r ->
            Array(n) { g ->
                IntArray(n) { b ->
                    serial(grades, r * inv, g * inv, b * inv, out)
                    argb(out)
                }
            }
        }
    }

    /**
     * Serial nodes [grades] in turn as a `.cube`-style table (red fastest), its output clamped to 0…1 as the GPU's post LUT
     * stores it: the CPU surfaces (scopes, generated LUTs) look colours up in it as the GPU does. Null without any.
     */
    fun serialCube(grades: List<ColorGrade>): CubeLut? {
        if (grades.isEmpty()) return null
        val n = size
        val inv = 1f / (n - 1)
        val out = FloatArray(3)
        val data = FloatArray(n * n * n * 3)
        var i = 0
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            serial(grades, r * inv, g * inv, b * inv, out)
            for (c in 0..2) data[i++] = out[c].coerceIn(0f, 1f)
        }
        return CubeLut(n, true, data)
    }

    private fun serial(grades: List<ColorGrade>, r: Float, g: Float, b: Float, out: FloatArray) {
        out[0] = r
        out[1] = g
        out[2] = b
        for (grade in grades) apply(grade, out[0], out[1], out[2], out)
    }

    /** Splines of a curve set: a LUT or a frame grades thousands of colours with one or two grades. */
    private class CurveSplines(val source: ToneCurves) {
        val luma = ToneSpline(source.luma)
        val red = ToneSpline(source.red)
        val green = ToneSpline(source.green)
        val blue = ToneSpline(source.blue)
    }

    /** Splines of a node's hue curves, cached like [CurveSplines]. */
    private class HueSplines(val source: HueCurves) {
        val hueHue = HueSpline(source.hueHue, HueCurve.HUE_HUE.periodic)
        val hueSat = HueSpline(source.hueSat, HueCurve.HUE_SAT.periodic)
        val hueLum = HueSpline(source.hueLum, HueCurve.HUE_LUM.periodic)
        val lumSat = HueSpline(source.lumSat, HueCurve.LUM_SAT.periodic)
    }

    @Volatile private var hueCache: HueSplines? = null
    @Volatile private var previousHue: HueSplines? = null

    private fun hueSplines(curves: HueCurves): HueSplines {
        val cached = hueCache
        if (cached != null && (cached.source === curves || cached.source == curves)) return cached
        val previous = previousHue
        val found = if (previous != null && (previous.source === curves || previous.source == curves)) previous else HueSplines(curves)
        previousHue = cached
        hueCache = found
        return found
    }

    // The last two curve sets used: a window composite alternates between its two nodes' curves.
    @Volatile private var splineCache: CurveSplines? = null
    @Volatile private var previousSplines: CurveSplines? = null

    private fun splines(curves: ToneCurves): CurveSplines {
        val cached = splineCache
        if (cached != null && (cached.source === curves || cached.source == curves)) return cached
        val previous = previousSplines
        val found = if (previous != null && (previous.source === curves || previous.source == curves)) previous else CurveSplines(curves)
        previousSplines = cached
        splineCache = found
        return found
    }

    /** The imported LUT last looked up, while the library stays as it was: a cube or a frame grades thousands of colours with one. */
    private class CubeHit(val id: String, val generation: Int, val cube: CubeLut?)

    @Volatile private var cubeHit: CubeHit? = null

    private fun cube(id: String): CubeLut? {
        val generation = luts.generation
        val hit = cubeHit
        if (hit != null && hit.id == id && hit.generation == generation) return hit.cube
        return luts.cube(id).also { cubeHit = CubeHit(id, generation, it) }
    }

    /** Grades one display-referred colour (0..1 per channel) into [out] with [grade]'s primary node (a window is spatial). */
    fun apply(grade: ColorGrade, r: Float, g: Float, b: Float, out: FloatArray) {
        var cr = r
        var cg = g
        var cb = b

        // Offset — shifts every tone.
        val off = wheelDelta(grade.offset)
        cr += 0.3f * (grade.offset.master + off[0])
        cg += 0.3f * (grade.offset.master + off[1])
        cb += 0.3f * (grade.offset.master + off[2])
        // Lift — raises / lowers blacks while white stays anchored.
        val lift = wheelDelta(grade.lift)
        cr += (grade.lift.master * 0.5f + lift[0]) * (1f - cr)
        cg += (grade.lift.master * 0.5f + lift[1]) * (1f - cg)
        cb += (grade.lift.master * 0.5f + lift[2]) * (1f - cb)
        // Gain — scales highlights.
        val gain = wheelDelta(grade.gain)
        cr *= 1f + grade.gain.master + gain[0]
        cg *= 1f + grade.gain.master + gain[1]
        cb *= 1f + grade.gain.master + gain[2]
        // Gamma — bends mid-tones.
        val gam = wheelDelta(grade.gamma)
        cr = gammaBend(cr, grade.gamma.master + gam[0])
        cg = gammaBend(cg, grade.gamma.master + gam[1])
        cb = gammaBend(cb, grade.gamma.master + gam[2])

        // Exposure in (approximately) linear light.
        if (grade.exposure != 0f) {
            val k = 2f.pow(grade.exposure)
            cr = delinearize(linearize(cr) * k)
            cg = delinearize(linearize(cg) * k)
            cb = delinearize(linearize(cb) * k)
        }
        // Contrast around the pivot (Resolve's 0.435 unless moved): levels below it go darker, above it brighter.
        if (grade.contrast != 1f) {
            val p = grade.pivot
            cr = (cr - p) * grade.contrast + p
            cg = (cg - p) * grade.contrast + p
            cb = (cb - p) * grade.contrast + p
        }
        // Highlights / shadows: luminance-weighted tonal push.
        if (grade.highlights != 0f || grade.shadows != 0f) {
            val y = luma(cr, cg, cb).coerceIn(0f, 1f)
            val hiW = smoothstep(0.45f, 1f, y)
            val shW = 1f - smoothstep(0f, 0.55f, y)
            val d = grade.highlights / 100f * 0.25f * hiW + grade.shadows / 100f * 0.25f * shW
            cr += d; cg += d; cb += d
        }
        // White balance.
        if (grade.temperature != 0f || grade.tint != 0f) {
            val t = grade.temperature / 100f
            val ti = grade.tint / 100f
            cr *= 1f + 0.18f * t + 0.09f * ti
            cb *= 1f - 0.18f * t + 0.09f * ti
            cg *= 1f - 0.18f * ti
        }
        // RGB Mixer: each output a mix of the three inputs (or one grey), luminance kept unless switched off.
        if (!grade.rgbMixer.isIdentity) {
            grade.rgbMixer.apply(cr, cg, cb, out)
            cr = out[0]; cg = out[1]; cb = out[2]
        }
        // Saturation and hue rotation around the neutral axis.
        if (grade.saturation != 1f || grade.hue != 0f) {
            val y = luma(cr, cg, cb)
            cr = y + (cr - y) * grade.saturation
            cg = y + (cg - y) * grade.saturation
            cb = y + (cb - y) * grade.saturation
            if (grade.hue != 0f) rotateHue(cr, cg, cb, grade.hue, out).also {
                cr = out[0]; cg = out[1]; cb = out[2]
            }
        }
        // Color Boost: saturation weighted towards dull colours — full strength on a grey's neighbours, fading out by
        // a chroma of BOOST_FADE — so skin and vivid colours keep their level.
        if (grade.colorBoost != 0f) {
            val chroma = maxOf(cr, cg, cb) - minOf(cr, cg, cb)
            val k = 1f + grade.colorBoost / 100f * (1f - smoothstep(0f, BOOST_FADE, chroma))
            val y = luma(cr, cg, cb)
            cr = y + (cr - y) * k
            cg = y + (cg - y) * k
            cb = y + (cb - y) * k
        }
        // Curves: master luma curve then per channel.
        if (!grade.curves.isIdentity) {
            val c = splines(grade.curves)
            cr = c.red.eval(c.luma.eval(cr.coerceIn(0f, 1f)))
            cg = c.green.eval(c.luma.eval(cg.coerceIn(0f, 1f)))
            cb = c.blue.eval(c.luma.eval(cb.coerceIn(0f, 1f)))
        }
        // Hue curves, keyed on the colour as it reaches them: hue → hue, hue and luma → saturation, hue → luma.
        if (!grade.hueCurves.isFlat) {
            val h = hueSplines(grade.hueCurves)
            val max = maxOf(cr, cg, cb)
            val chroma = max - minOf(cr, cg, cb)
            val hue = hueOf(cr, cg, cb, max, chroma)
            val y = luma(cr, cg, cb).coerceIn(0f, 1f)
            val shift = (h.hueHue.eval(hue) - HueCurves.FLAT) * 2f * HueCurve.MAX_HUE_SHIFT
            if (shift != 0f && chroma > 0f) {
                rotateHue(cr, cg, cb, shift, out)
                cr = out[0]; cg = out[1]; cb = out[2]
            }
            val sat = 2f * h.hueSat.eval(hue) * 2f * h.lumSat.eval(y)
            if (sat != 1f) {
                val l = luma(cr, cg, cb)
                cr = l + (cr - l) * sat
                cg = l + (cg - l) * sat
                cb = l + (cb - l) * sat
            }
            // Up to a stop brighter or darker (the colour scaled, so even a pure primary darkens), only as
            // strongly as the colour is colourful: greys stay.
            val stops = (h.hueLum.eval(hue) - HueCurves.FLAT) * 2f
            if (stops != 0f) {
                val k = 1f + (2f.pow(stops) - 1f) * smoothstep(0f, FULL_CHROMA, chroma)
                cr *= k; cg *= k; cb *= k
            }
        }
        // Color Warper: the chroma plane's grid points moved, luma kept.
        if (!grade.warper.isIdentity) {
            grade.warper.apply(cr, cg, cb, out)
            cr = out[0]; cg = out[1]; cb = out[2]
        }
        // Creative look or imported LUT, blended by strength; a LUT missing from the library changes nothing.
        if (grade.hasLook && grade.lookStrength > 0) {
            val s = grade.lookStrength / 100f
            val ir = cr.coerceIn(0f, 1f)
            val ig = cg.coerceIn(0f, 1f)
            val ib = cb.coerceIn(0f, 1f)
            val lut = grade.lut
            val cube = lut?.let { cube(it.id) }
            when {
                cube != null -> cube.apply(ir, ig, ib, out)
                lut != null -> { out[0] = ir; out[1] = ig; out[2] = ib }
                else -> applyLook(grade.look, ir, ig, ib, out)
            }
            cr += (out[0] - cr) * s
            cg += (out[1] - cg) * s
            cb += (out[2] - cb) * s
        }
        out[0] = cr.coerceIn(0f, 1f)
        out[1] = cg.coerceIn(0f, 1f)
        out[2] = cb.coerceIn(0f, 1f)
    }

    /** RGB readout of a wheel (master + balance projected on R/G/B), as shown under each wheel. */
    fun wheelReadout(value: WheelValue): FloatArray {
        val d = wheelDelta(value)
        return floatArrayOf(value.master + d[0], value.master + d[1], value.master + d[2])
    }

    private fun applyLook(look: Look, r: Float, g: Float, b: Float, out: FloatArray) {
        val y = luma(r, g, b)
        when (look) {
            Look.NONE -> { out[0] = r; out[1] = g; out[2] = b }
            Look.TEAL_ORANGE -> {
                val shadow = 1f - smoothstep(0.1f, 0.6f, y)
                val highlight = smoothstep(0.4f, 0.9f, y)
                out[0] = r - 0.06f * shadow + 0.08f * highlight
                out[1] = g + 0.02f * shadow + 0.02f * highlight
                out[2] = b + 0.08f * shadow - 0.08f * highlight
                saturate(out, 1.15f)
            }
            Look.KODAK_2383 -> {
                out[0] = sCurve(r, 1.12f) + 0.015f
                out[1] = sCurve(g, 1.12f)
                out[2] = sCurve(b, 1.12f) - 0.02f
                saturate(out, 0.92f)
            }
            Look.BLEACH_BYPASS -> {
                out[0] = (r + (y - r) * 0.6f - PIVOT) * 1.25f + PIVOT
                out[1] = (g + (y - g) * 0.6f - PIVOT) * 1.25f + PIVOT
                out[2] = (b + (y - b) * 0.6f - PIVOT) * 1.25f + PIVOT
            }
            Look.MONO -> {
                val m = (y - PIVOT) * 1.15f + PIVOT
                out[0] = m; out[1] = m; out[2] = m
            }
            Look.WARM_FADE -> {
                out[0] = 0.08f + r * 0.9f + 0.03f
                out[1] = 0.08f + g * 0.88f + 0.01f
                out[2] = 0.08f + b * 0.82f
            }
            Look.NIGHT_NEON -> {
                rotateHue(r, g, b, 12f, out)
                saturate(out, 1.35f)
                for (i in 0..2) out[i] = (out[i] - PIVOT) * 1.1f + PIVOT
            }
            Look.LOG_TO_709 -> {
                out[0] = sCurve(r, 1.35f)
                out[1] = sCurve(g, 1.35f)
                out[2] = sCurve(b, 1.35f)
                saturate(out, 1.3f)
            }
        }
    }

    companion object {
        private const val PIVOT = ColorGrade.DEFAULT_PIVOT
        /** Chroma (max − min) past which Color Boost no longer acts. */
        const val BOOST_FADE = 0.6f
        /** Chroma (max − min) from which a colour takes a hue-vs-luma push in full. */
        private const val FULL_CHROMA = 0.15f

        /** Hue of a colour, 0…1 from red through yellow, green, cyan, blue and magenta (0 for a grey). */
        fun hueOf(r: Float, g: Float, b: Float, max: Float = maxOf(r, g, b), chroma: Float = max - minOf(r, g, b)): Float {
            if (chroma <= 0f) return 0f
            val h = when (max) {
                r -> ((g - b) / chroma).let { if (it < 0f) it + 6f else it }
                g -> (b - r) / chroma + 2f
                else -> (r - g) / chroma + 4f
            } / 6f
            return if (h >= 1f) h - 1f else h
        }

        fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

        /** Zero-luma RGB push for a wheel's balance vector; hue angle follows the wheel ring. */
        fun wheelDelta(value: WheelValue): FloatArray {
            val m = hypot(value.x, value.y)
            if (m == 0f) return floatArrayOf(0f, 0f, 0f)
            val theta = atan2(value.y, value.x)
            // Hue directions: red at 0°, green at 120°, blue at 240° (counter-clockwise).
            val r = cos(theta) * m * 0.5f
            val g = cos(theta - (2 * PI / 3).toFloat()) * m * 0.5f
            val b = cos(theta - (4 * PI / 3).toFloat()) * m * 0.5f
            // Remove the push's Rec.709 luma so a balance move changes colour, not brightness.
            val y = luma(r, g, b)
            return floatArrayOf(r - y, g - y, b - y)
        }

        fun argb(rgb: FloatArray): Int {
            val r = (rgb[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val g = (rgb[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val b = (rgb[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** Value of the tone curve through [points] at [x] (see [ToneSpline]). */
        fun evalCurve(points: List<CurvePoint>, x: Float): Float = ToneSpline(points).eval(x)

        private fun gammaBend(v: Float, amount: Float): Float {
            if (amount == 0f) return v
            val exponent = 1f / (1f + amount).coerceAtLeast(0.1f)
            return if (v <= 0f) v else v.pow(exponent)
        }

        private fun linearize(v: Float): Float = if (v <= 0f) v else v.pow(2.2f)
        private fun delinearize(v: Float): Float = if (v <= 0f) v else v.pow(1f / 2.2f)

        private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        private fun sCurve(v: Float, strength: Float): Float {
            val c = (v - 0.5f) * strength
            return 0.5f + c / (1f + kotlin.math.abs(c) * (strength - 1f) * 0.6f)
        }

        private fun saturate(rgb: FloatArray, s: Float) {
            val y = luma(rgb[0], rgb[1], rgb[2])
            for (i in 0..2) rgb[i] = y + (rgb[i] - y) * s
        }

        /** Rotation about the neutral (1,1,1) axis by [degrees]. */
        fun rotateHue(r: Float, g: Float, b: Float, degrees: Float, out: FloatArray) {
            val a = degrees * PI.toFloat() / 180f
            val cosA = cos(a)
            val sinA = sin(a)
            val k = (1f - cosA) / 3f
            val s = sqrt(1f / 3f) * sinA
            out[0] = r * (cosA + k) + g * (k - s) + b * (k + s)
            out[1] = r * (k + s) + g * (cosA + k) + b * (k - s)
            out[2] = r * (k - s) + g * (k + s) + b * (cosA + k)
        }
    }
}
