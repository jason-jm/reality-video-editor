package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One grading wheel: [x]/[y] is the colour balance puck (|v| ≤ [MAX_BALANCE]), [master] the tonal ring. */
data class WheelValue(val x: Float = 0f, val y: Float = 0f, val master: Float = 0f) {
    val isNeutral: Boolean get() = x == 0f && y == 0f && master == 0f

    companion object {
        const val MAX_BALANCE = 0.5f
        val NEUTRAL = WheelValue()
    }
}

enum class GradeWheel(val label: String) {
    LIFT("暗部 Lift"),
    GAMMA("中间调 Gamma"),
    GAIN("亮部 Gain"),
    OFFSET("整体 Offset"),
}

data class CurvePoint(val x: Float, val y: Float)

enum class CurveChannel(val label: String) { LUMA("亮度"), RED("红"), GREEN("绿"), BLUE("蓝") }

data class ToneCurves(
    val luma: List<CurvePoint> = IDENTITY,
    val red: List<CurvePoint> = IDENTITY,
    val green: List<CurvePoint> = IDENTITY,
    val blue: List<CurvePoint> = IDENTITY,
) {
    operator fun get(channel: CurveChannel): List<CurvePoint> = when (channel) {
        CurveChannel.LUMA -> luma
        CurveChannel.RED -> red
        CurveChannel.GREEN -> green
        CurveChannel.BLUE -> blue
    }

    fun with(channel: CurveChannel, points: List<CurvePoint>): ToneCurves = when (channel) {
        CurveChannel.LUMA -> copy(luma = points)
        CurveChannel.RED -> copy(red = points)
        CurveChannel.GREEN -> copy(green = points)
        CurveChannel.BLUE -> copy(blue = points)
    }

    val isIdentity: Boolean get() = listOf(luma, red, green, blue).all { it == IDENTITY }

    companion object {
        val IDENTITY = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
    }
}

/**
 * Resolve's curves that key on a colour's hue (or its luma) and push its hue, saturation or luma. A curve's
 * height is the push: the middle (0.5) changes nothing.
 */
enum class HueCurve(val label: String, val short: String, val periodic: Boolean) {
    /** Turns colours of a hue toward their neighbours: ±[MAX_HUE_SHIFT]°. */
    HUE_HUE("色相对色相", "色相·色相", true),
    /** Saturates or greys colours of a hue: ×0…×2. */
    HUE_SAT("色相对饱和度", "色相·饱和", true),
    /** Brightens or darkens colours of a hue, ±1 stop, as strongly as they are colourful (greys stay). */
    HUE_LUM("色相对亮度", "色相·亮度", true),
    /** Saturates or greys by brightness — e.g. pale highlights or clean shadows: ×0…×2. */
    LUM_SAT("亮度对饱和度", "亮度·饱和", false);

    companion object {
        const val MAX_HUE_SHIFT = 60f
    }
}

/**
 * The four [HueCurve]s of a node. Each is its own control points (x: hue 0…1 from red through yellow,
 * green, cyan, blue and magenta back to red — wrapping round — or luma; y: the push); no points is flat.
 */
data class HueCurves(
    val hueHue: List<CurvePoint> = emptyList(),
    val hueSat: List<CurvePoint> = emptyList(),
    val hueLum: List<CurvePoint> = emptyList(),
    val lumSat: List<CurvePoint> = emptyList(),
) {
    operator fun get(curve: HueCurve): List<CurvePoint> = when (curve) {
        HueCurve.HUE_HUE -> hueHue
        HueCurve.HUE_SAT -> hueSat
        HueCurve.HUE_LUM -> hueLum
        HueCurve.LUM_SAT -> lumSat
    }

    fun with(curve: HueCurve, points: List<CurvePoint>): HueCurves = when (curve) {
        HueCurve.HUE_HUE -> copy(hueHue = points)
        HueCurve.HUE_SAT -> copy(hueSat = points)
        HueCurve.HUE_LUM -> copy(hueLum = points)
        HueCurve.LUM_SAT -> copy(lumSat = points)
    }

    /** Nothing pushed: every curve without points or with all of them in the middle. */
    val isFlat: Boolean get() = HueCurve.entries.all { c -> this[c].all { it.y == FLAT } }

    companion object {
        const val FLAT = 0.5f
    }
}

/** Creative looks (built-in LUT presets). */
enum class Look(val id: String, val label: String) {
    NONE("none", "无"),
    TEAL_ORANGE("teal-orange", "青橙电影"),
    KODAK_2383("kodak-2383", "胶片 2383"),
    BLEACH_BYPASS("bleach", "漂白旁路"),
    MONO("mono", "黑白"),
    WARM_FADE("warm-fade", "暖色褪色"),
    NIGHT_NEON("night-neon", "霓虹夜"),
    LOG_TO_709("log-709", "Log → Rec.709");

    companion object {
        fun byId(id: String?): Look = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * A per-clip grade: the primary correction (node 1, the whole picture), an optional power window (node 2, applied after it
 * inside a shape) and serial nodes 03 on ([serial], whole-picture corrections after node 2). Node 1 can be keyframed: then
 * [keyframes] hold its correction at frames of the clip and the fields above are what it was before keyframing.
 */
data class ColorGrade(
    val lift: WheelValue = WheelValue.NEUTRAL,
    val gamma: WheelValue = WheelValue.NEUTRAL,
    val gain: WheelValue = WheelValue.NEUTRAL,
    val offset: WheelValue = WheelValue.NEUTRAL,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    /** Resolve's contrast pivot: the level contrast turns around (0…1, 0.435 by default). */
    val pivot: Float = DEFAULT_PIVOT,
    /** Resolve's Color Boost (−100…100): saturation that lifts dull colours most and leaves vivid ones nearly alone. */
    val colorBoost: Float = 0f,
    val curves: ToneCurves = ToneCurves(),
    /** Hue vs hue / sat / lum and lum vs sat, after the custom curves. */
    val hueCurves: HueCurves = HueCurves(),
    /** Resolve's RGB Mixer: each output channel a mix of the inputs, after the white balance ([RgbMixer]). */
    val rgbMixer: RgbMixer = RgbMixer.NEUTRAL,
    /** Resolve's Color Warper (hue–saturation): the chroma plane's grid points moved, after the hue curves ([ColorWarper]). */
    val warper: ColorWarper = ColorWarper.NEUTRAL,
    val look: Look = Look.NONE,
    /** How much of the look (or [lut]) shows, 0…100 %. */
    val lookStrength: Int = 80,
    /**
     * An imported LUT in the look's place (at the end of the node, [look] then [Look.NONE]): the node's
     * corrections feed it — a Log clip is balanced before its conversion LUT — and node 2 grades after it.
     */
    val lut: LutRef? = null,
    /** Node 2: a power window whose own grade applies after this one; its grade has no window. */
    val window: PowerWindow? = null,
    /**
     * Resolve's dynamic keyframes on node 1: its correction at frames of the clip (counted from the clip's
     * start), the picture dissolving from one keyframe's look to the next — evenly or eased — and held
     * before the first and after the last; empty keeps the correction above. See [ColorGrade.at].
     */
    val keyframes: List<GradeKeyframe> = emptyList(),
    /**
     * Resolve's Spatial NR, before node 1's correction ([SpatialNoiseReduction]). Not keyframed: a keyed node 1 keeps this
     * one; node 2's grade has none.
     */
    val spatialNr: SpatialNoiseReduction = SpatialNoiseReduction.OFF,
    /**
     * Resolve's Mid/Detail (−100…100): local contrast in the midtones — positive brings out texture, negative softens it
     * ([MidDetailKernel]). Spatial, so outside the LUT; like [spatialNr] one value for the clip, kept by keyframes.
     */
    val midDetail: Float = 0f,
    /**
     * Resolve's Temporal NR (0…100): each frame blended towards the frames before it where nothing moves
     * ([TemporalNrKernel]). Across frames, so outside the LUT; like [spatialNr] one value for the clip, kept by keyframes.
     */
    val temporalNr: Int = 0,
    /**
     * Resolve's serial nodes after node 2 (nodes 03 on, at most [GradeNode.MAX_SERIAL]): each corrects the whole picture
     * the nodes before it made, in order. Not keyframed; their grades hold colour only (no window, keyframes or noise
     * reduction).
     */
    val serial: List<SerialNode> = emptyList(),
) {
    /** The colour correction alone, without the noise reductions or Mid/Detail: what a LUT can hold. */
    val colorOnly: ColorGrade
        get() = if (spatialNr == SpatialNoiseReduction.OFF && midDetail == 0f && temporalNr == 0) this
        else copy(spatialNr = SpatialNoiseReduction.OFF, midDetail = 0f, temporalNr = 0)

    /** Node 1 alone, as its fields hold it: this grade without its window, keyframes or serial nodes. */
    val primary: ColorGrade
        get() = if (window == null && keyframes.isEmpty() && serial.isEmpty()) this else copy(window = null, keyframes = emptyList(), serial = emptyList())

    /** The serial nodes that change the picture (switched on, not neutral), in order. */
    val activeSerial: List<ColorGrade> get() = serial.filter { it.active }.map { it.grade }

    /**
     * Nothing changes the picture: a neutral primary without keyframes, no window or one whose grade is neutral, and no
     * active serial node.
     */
    val isNeutral: Boolean
        get() = keyframes.isEmpty() && (window == null || window.grade.isNeutral) && serial.none { it.active } &&
            primary.let {
                // An untouched mixer counts as none whatever its switches say.
                val plain = (if (it.rgbMixer.isIdentity) it.copy(rgbMixer = RgbMixer.NEUTRAL) else it)
                    .let { g -> if (g.warper.isIdentity) g.copy(warper = ColorWarper.NEUTRAL) else g }
                plain == NEUTRAL || plain.copy(lookStrength = NEUTRAL.lookStrength) == NEUTRAL
            }

    /**
     * Node 1's correction a fraction [t] (0…1) of the way to [to]'s: every setting in between; a look, a LUT
     * and a curve with a different number of points switch half way.
     */
    fun lerp(to: ColorGrade, t: Float): ColorGrade {
        fun f(a: Float, b: Float) = a + (b - a) * t
        fun w(a: WheelValue, b: WheelValue) = WheelValue(f(a.x, b.x), f(a.y, b.y), f(a.master, b.master))
        fun c(a: List<CurvePoint>, b: List<CurvePoint>) =
            if (a.size != b.size) (if (t < 0.5f) a else b) else a.zip(b) { p, q -> CurvePoint(f(p.x, q.x), f(p.y, q.y)) }
        return ColorGrade(
            lift = w(lift, to.lift), gamma = w(gamma, to.gamma), gain = w(gain, to.gain), offset = w(offset, to.offset),
            exposure = f(exposure, to.exposure), contrast = f(contrast, to.contrast), saturation = f(saturation, to.saturation),
            hue = f(hue, to.hue), temperature = f(temperature, to.temperature), tint = f(tint, to.tint),
            highlights = f(highlights, to.highlights), shadows = f(shadows, to.shadows),
            pivot = f(pivot, to.pivot), colorBoost = f(colorBoost, to.colorBoost),
            curves = ToneCurves(c(curves.luma, to.curves.luma), c(curves.red, to.curves.red), c(curves.green, to.curves.green), c(curves.blue, to.curves.blue)),
            hueCurves = HueCurves(
                c(hueCurves.hueHue, to.hueCurves.hueHue), c(hueCurves.hueSat, to.hueCurves.hueSat),
                c(hueCurves.hueLum, to.hueCurves.hueLum), c(hueCurves.lumSat, to.hueCurves.lumSat),
            ),
            rgbMixer = rgbMixer.lerp(to.rgbMixer, t),
            warper = warper.lerp(to.warper, t),
            look = if (t < 0.5f) look else to.look,
            lookStrength = (lookStrength + (to.lookStrength - lookStrength) * t).roundToInt(),
            lut = if (t < 0.5f) lut else to.lut,
        )
    }

    /** The grade of [node] (null: node 2 without a window, or a serial node that is not there). */
    fun node(node: GradeNode): ColorGrade? = when (node) {
        GradeNode.PRIMARY -> primary
        GradeNode.WINDOW -> window?.grade
        else -> serial.getOrNull(node.serialIndex)?.grade
    }

    /** This grade with [change] applied to [node]'s grade; unchanged when node 2 has no window or the serial node is not there. */
    fun withNode(node: GradeNode, change: (ColorGrade) -> ColorGrade): ColorGrade = when (node) {
        // Keyframes stay: a keyed node 1 is changed through its keyframe at the playhead (GradeKeyframeUseCase).
        GradeNode.PRIMARY -> change(primary).copy(window = window, keyframes = keyframes, serial = serial)
        GradeNode.WINDOW -> window?.let { copy(window = it.copy(grade = change(it.grade).primary)) } ?: this
        else -> if (node.serialIndex !in serial.indices) this
        else copy(serial = serial.mapIndexed { i, n -> if (i == node.serialIndex) n.copy(grade = change(n.grade).primary.colorOnly) else n })
    }

    fun wheel(which: GradeWheel): WheelValue = when (which) {
        GradeWheel.LIFT -> lift
        GradeWheel.GAMMA -> gamma
        GradeWheel.GAIN -> gain
        GradeWheel.OFFSET -> offset
    }

    fun withWheel(which: GradeWheel, value: WheelValue): ColorGrade = when (which) {
        GradeWheel.LIFT -> copy(lift = value)
        GradeWheel.GAMMA -> copy(gamma = value)
        GradeWheel.GAIN -> copy(gain = value)
        GradeWheel.OFFSET -> copy(offset = value)
    }

    /** A look or an imported LUT colours the node. */
    val hasLook: Boolean get() = look != Look.NONE || lut != null

    companion object {
        /** Resolve's default contrast pivot. */
        const val DEFAULT_PIVOT = 0.435f
        val NEUTRAL = ColorGrade()
    }
}

/**
 * A node of a clip's grade, numbered as Resolve's node editor numbers them: 01 the primary correction, 02 the window node,
 * 03 on the serial nodes after it ([ColorGrade.serial]).
 */
@JvmInline
value class GradeNode(val index: Int) {
    /** "01", "02", "03" … */
    val number: String get() = (index + 1).toString().padStart(2, '0')
    val label: String
        get() = when (index) {
            0 -> "主校色"
            1 -> "窗口"
            else -> "校色"
        }
    val isSerial: Boolean get() = index >= 2

    /** Its place in [ColorGrade.serial]. */
    val serialIndex: Int get() = index - 2

    companion object {
        val PRIMARY = GradeNode(0)
        val WINDOW = GradeNode(1)

        /** Serial nodes a grade holds at most: nodes 03 to 06. */
        const val MAX_SERIAL = 4

        fun serial(i: Int) = GradeNode(i + 2)
    }
}

/**
 * A serial node after node 2 (Resolve's Add Serial Node, nodes 03 on): its own correction of the whole picture — wheels,
 * sliders, curves, mixer, warper, a look or a LUT — on what the nodes before it made. Switched off ([enabled] false) it
 * passes the picture through unchanged (Resolve's Enable / Disable Node), its settings kept.
 */
data class SerialNode(val grade: ColorGrade = ColorGrade.NEUTRAL, val enabled: Boolean = true) {
    val active: Boolean get() = enabled && !grade.isNeutral
}

/** Power window shapes (Resolve's Window palette); [FULL] limits nothing, for a node keyed by its qualifier alone. */
enum class WindowShape(val id: String, val label: String) {
    CIRCLE("circle", "圆形"),
    RECTANGLE("rectangle", "矩形"),
    GRADIENT("gradient", "渐变"),
    FULL("full", "全画面");

    companion object {
        fun byId(id: String?): WindowShape = entries.firstOrNull { it.id == id } ?: CIRCLE
    }
}

/**
 * A power window: [grade] applies on top of the primary grade inside the shape (outside when
 * [invert]), blending in across [softness]. The geometry belongs to the picture as the clip frames it,
 * before its transform, so the window moves with the picture: [centerX] / [centerY] run −1…1 from the
 * left / bottom edge to the right / top edge; [size] is the shape's half-height in picture half-heights,
 * [aspect] its width : height and [angle] its counter-clockwise rotation in degrees. A gradient fades
 * across the line through the centre and covers the side [angle] points to (0° = up); [softness] is the
 * half-width of the fade, in picture half-heights for a gradient and in shape radii otherwise.
 */
data class PowerWindow(
    val shape: WindowShape = WindowShape.CIRCLE,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val size: Float = 0.5f,
    val aspect: Float = 1f,
    val angle: Float = 0f,
    val softness: Float = 0.25f,
    val invert: Boolean = false,
    val grade: ColorGrade = ColorGrade.NEUTRAL,
    /** Limits the node to colours too (multiplying the shape's coverage); null keys on the shape alone. */
    val qualifier: HslQualifier? = null,
    /**
     * Resolve's dynamic keyframes on the window: its geometry animates over the clip (frames from the clip's
     * start); empty keeps the geometry above. See [PowerWindow.at].
     */
    val keyframes: List<WindowKeyframe> = emptyList(),
) {
    /**
     * How much of [grade] shows (0…1) at the picture point [x], [y] (0…1 from the left / bottom edge) of
     * a picture whose width : height is [pictureAspect], before the [qualifier]. The GPU shader computes
     * the same.
     */
    fun coverage(x: Float, y: Float, pictureAspect: Float): Float {
        if (shape == WindowShape.FULL) return if (invert) 0f else 1f
        val a = pictureAspect.coerceAtLeast(0.01f)
        // Picture half-heights from the window centre, then into the window's own axes.
        val dx = (x - 0.5f) * 2f * a - centerX * a
        val dy = (y - 0.5f) * 2f - centerY
        val theta = angle * PI.toFloat() / 180f
        val c = cos(theta)
        val s = sin(theta)
        val qx = dx * c + dy * s
        val qy = -dx * s + dy * c
        val inside = when (shape) {
            WindowShape.GRADIENT -> fade(-softness, softness, qy)
            else -> {
                val w = (size * aspect).coerceAtLeast(MIN_EXTENT)
                val h = size.coerceAtLeast(MIN_EXTENT)
                val r = if (shape == WindowShape.CIRCLE) sqrt((qx / w) * (qx / w) + (qy / h) * (qy / h)) else max(abs(qx) / w, abs(qy) / h)
                1f - fade(1f - softness, 1f + softness, r)
            }
        }
        return if (invert) 1f - inside else inside
    }

    companion object {
        /** Smallest half-extent of a shape (picture half-heights). */
        const val MIN_EXTENT = 0.01f
        /** Narrower soft edges than this are hard. */
        const val HARD_EDGE = 1e-4f

        /** Smoothstep from [from] to [to]; a step at their middle when they (nearly) meet. */
        fun fade(from: Float, to: Float, x: Float): Float {
            if (to - from < HARD_EDGE) return if (x < (from + to) / 2f) 0f else 1f
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/**
 * An HSL qualifier (Resolve's Qualifier palette): node 2's grade limited to colours by hue, saturation
 * and luminance, each a range whose soft edges fade outwards. It keys on the node's input — the
 * primary-graded picture — so picking a colour on the monitor selects what node 2 receives. Hue is in
 * degrees (0 red, 120 green, 240 blue); [hueWidth] is the half-width of the hue range (180 takes every
 * hue). Saturation is chroma (max − min of R, G, B) and luminance Rec. 709 luma, both 0…1; a range
 * reaching 0 or 1 is open on that side. The GPU shader computes the same [key].
 */
data class HslQualifier(
    val hueCenter: Float = 0f,
    val hueWidth: Float = 30f,
    val hueSoft: Float = 15f,
    val satLow: Float = 0.15f,
    val satHigh: Float = 1f,
    val satSoft: Float = 0.1f,
    val lumLow: Float = 0f,
    val lumHigh: Float = 1f,
    val lumSoft: Float = 0.1f,
    val invert: Boolean = false,
    /**
     * Resolve's matte finesse (0…100 each): clean black drops faint selection (noise) by raising the
     * matte's black point, clean white fills a near-full selection by lowering its white point, and blur
     * softens the matte over up to 2 % of the picture's height.
     */
    val cleanBlack: Float = 0f,
    val cleanWhite: Float = 0f,
    val blur: Float = 0f,
) {
    /** How selected (0…1) the colour [r], [g], [b] (0…1) is, after clean black / white (the blur is spatial: see [blurRadius]). */
    fun key(r: Float, g: Float, b: Float): Float {
        val k = hueKey(hue(r, g, b)) * band(chroma(r, g, b), satLow, satHigh, satSoft) * band(luma(r, g, b), lumLow, lumHigh, lumSoft)
        return clean(if (invert) 1f - k else k)
    }

    /** [k] through clean black / white: levels on the matte, its black point up to 0.5, its white point down to 0.5. */
    fun clean(k: Float): Float {
        if (cleanBlack <= 0f && cleanWhite <= 0f) return k
        val black = cleanBlack / 100f * MAX_CLEAN
        val white = 1f - cleanWhite / 100f * MAX_CLEAN
        return ((k - black) / (white - black)).coerceIn(0f, 1f)
    }

    /** The matte blur's radius as a share of the source picture's height (0 = sharp). */
    val blurRadius: Float get() = blur / 100f * MAX_BLUR

    /** The matte finesse at its defaults. */
    val isRefined: Boolean get() = cleanBlack > 0f || cleanWhite > 0f || blur > 0f

    /**
     * Resolve's picker add: the ranges grown just enough that the colour [r], [g], [b] is wholly selected —
     * each end it lies beyond moves out to it, a little past; a grey, which has no hue, takes every hue.
     * Inverted, the colour joins the selection by leaving the ranges. Null when it is selected already.
     */
    fun adding(r: Float, g: Float, b: Float): HslQualifier? = if (invert) narrowed(r, g, b) else widened(r, g, b)

    /**
     * Resolve's picker subtract: the colour [r], [g], [b] leaves the selection — one end of one range moves
     * just past it and its soft edge, whichever cut keeps the most of its range. Inverted, the colour leaves
     * by joining the ranges. Null when it is not selected, or sits so deep in every range that no cut can pass it.
     */
    fun subtracting(r: Float, g: Float, b: Float): HslQualifier? = if (invert) widened(r, g, b) else narrowed(r, g, b)

    /**
     * Resolve's feather add (Add Softness): the soft edges of the ranges the colour [r], [g], [b] lies beyond grow just
     * enough that it is about half selected; the inner ranges stay as they are. With n such ranges each keys it at
     * 0.5^(1/n). Null when it is half selected or more already, when it is a grey (no hue to feather toward), or when
     * no edge can reach it.
     */
    fun featheringIn(r: Float, g: Float, b: Float): HslQualifier? {
        if (rangeKey(r, g, b) >= HALF_SELECTED) return null
        val beyond = beyondCore(r, g, b) ?: return null
        if (beyond.isEmpty()) return null
        val share = inverseSmoothstep(1f - Math.pow(0.5, 1.0 / beyond.size).toFloat())
        var q = this
        for ((range, distance) in beyond) {
            val soft = distance / share
            q = when (range) {
                QualifierRange.HUE -> q.copy(hueSoft = maxOf(q.hueSoft, ceilTo(soft, 1f)).coerceAtMost(MAX_HUE_SOFT))
                QualifierRange.SATURATION -> q.copy(satSoft = maxOf(q.satSoft, ceilTo(soft, 0.01f)).coerceAtMost(MAX_SOFT))
                QualifierRange.LUMINANCE -> q.copy(lumSoft = maxOf(q.lumSoft, ceilTo(soft, 0.01f)).coerceAtMost(MAX_SOFT))
            }
        }
        return q.takeIf { it != this && it.rangeKey(r, g, b) > UNSELECTED }
    }

    /**
     * Resolve's feather subtract (Subtract Softness): the colour [r], [g], [b] leaves the soft edge it lies in — the
     * edge it has crossed the furthest is pulled back to just short of it, the others and the inner ranges staying.
     * Null when it is not selected, or sits inside every inner range (a picker subtract cuts those).
     */
    fun featheringOut(r: Float, g: Float, b: Float): HslQualifier? {
        val k = rangeKey(r, g, b)
        if (k <= UNSELECTED || k >= FULLY_SELECTED) return null
        val edges = (beyondCore(r, g, b) ?: return null).map { (range, distance) -> Triple(range, distance, softOf(range)) }
        val (range, distance, _) = edges.filter { it.third > 0f }.maxByOrNull { it.second / it.third } ?: return null
        val soft = distance * FEATHER_OUT_SHARE
        val q = when (range) {
            QualifierRange.HUE -> copy(hueSoft = floorTo(soft, 1f))
            QualifierRange.SATURATION -> copy(satSoft = floorTo(soft, 0.01f))
            QualifierRange.LUMINANCE -> copy(lumSoft = floorTo(soft, 0.01f))
        }
        return q.takeIf { it.rangeKey(r, g, b) <= UNSELECTED }
    }

    /**
     * How far the colour lies past each inner range it is outside of (degrees of hue, or shares of saturation /
     * luminance); null for a grey outside the hue range, which has no hue to measure.
     */
    private fun beyondCore(r: Float, g: Float, b: Float): List<Pair<QualifierRange, Float>>? = buildList {
        val c = chroma(r, g, b)
        val y = luma(r, g, b)
        if (hueWidth < FULL_HUE) {
            val d = abs(signedHue(hue(r, g, b) - hueCenter)) - hueWidth
            if (d > 0f) {
                if (c < GREY_CHROMA) return null
                add(QualifierRange.HUE to d)
            }
        }
        when {
            satLow > 0f && c < satLow -> add(QualifierRange.SATURATION to satLow - c)
            satHigh < 1f && c > satHigh -> add(QualifierRange.SATURATION to c - satHigh)
        }
        when {
            lumLow > 0f && y < lumLow -> add(QualifierRange.LUMINANCE to lumLow - y)
            lumHigh < 1f && y > lumHigh -> add(QualifierRange.LUMINANCE to y - lumHigh)
        }
    }

    private fun softOf(range: QualifierRange): Float = when (range) {
        QualifierRange.HUE -> hueSoft
        QualifierRange.SATURATION -> satSoft
        QualifierRange.LUMINANCE -> lumSoft
    }

    /** How selected the colour is by the ranges alone, before 反转 and the matte finesse. */
    fun rangeKey(r: Float, g: Float, b: Float): Float =
        hueKey(hue(r, g, b)) * band(chroma(r, g, b), satLow, satHigh, satSoft) * band(luma(r, g, b), lumLow, lumHigh, lumSoft)

    private fun widened(r: Float, g: Float, b: Float): HslQualifier? {
        val c = chroma(r, g, b)
        val y = luma(r, g, b)
        var q = this
        if (hueWidth < FULL_HUE) {
            val d = signedHue(hue(r, g, b) - hueCenter)
            if (c < GREY_CHROMA) {
                q = q.copy(hueWidth = FULL_HUE)
            } else if (abs(d) > hueWidth) {
                // The near end moves out past the hue; the far end stays.
                val reach = abs(d) + ADD_MARGIN_HUE
                val side = if (d > 0f) 1f else -1f
                q = q.copy(hueCenter = wrapHue(hueCenter + side * (reach - hueWidth) / 2f), hueWidth = ((hueWidth + reach) / 2f).coerceAtMost(FULL_HUE))
            }
        }
        if (c < satLow) q = q.copy(satLow = (c - ADD_MARGIN).coerceAtLeast(0f))
        if (c > satHigh) q = q.copy(satHigh = (c + ADD_MARGIN).coerceAtMost(1f))
        if (y < lumLow) q = q.copy(lumLow = (y - ADD_MARGIN).coerceAtLeast(0f))
        if (y > lumHigh) q = q.copy(lumHigh = (y + ADD_MARGIN).coerceAtMost(1f))
        return q.takeIf { it != this }
    }

    private fun narrowed(r: Float, g: Float, b: Float): HslQualifier? {
        if (rangeKey(r, g, b) <= UNSELECTED) return null
        val c = chroma(r, g, b)
        val y = luma(r, g, b)
        // Each cut that leaves the colour out, with the share of its range it keeps. A qualifier is picked by
        // hue, and a hue cut shows on the hue strip: it wins unless another keeps a tenth more of its range.
        val cuts = buildList<Pair<Float, HslQualifier>> {
            if (c >= GREY_CHROMA) hueCut(hue(r, g, b))?.let { (share, q) -> add(share + HUE_CUT_PREFERENCE to q) }
            rangeCut(c, satLow, satHigh, satSoft)?.let { (share, low, high) -> add(share to copy(satLow = low, satHigh = high)) }
            rangeCut(y, lumLow, lumHigh, lumSoft)?.let { (share, low, high) -> add(share to copy(lumLow = low, lumHigh = high)) }
        }
        return cuts.maxByOrNull { it.first }?.second
    }

    /** The hue range cut so [h] lies past its end and soft edge: the larger side kept (every hue taken: all but a wedge). */
    private fun hueCut(h: Float): Pair<Float, HslQualifier>? {
        val gap = hueSoft + ADD_MARGIN_HUE
        if (hueWidth >= FULL_HUE) {
            val width = FULL_HUE - gap
            return if (width < MIN_HUE_WIDTH) null else width / FULL_HUE to copy(hueCenter = wrapHue(h + 180f), hueWidth = width)
        }
        // In degrees from the centre: the side below the hue ends before it, the side above starts after it.
        val d = signedHue(h - hueCenter)
        val belowEnd = minOf(d - gap, hueWidth)
        val aboveStart = maxOf(d + gap, -hueWidth)
        val below = (belowEnd + hueWidth) / 2f
        val above = (hueWidth - aboveStart) / 2f
        val (half, middle) = if (below >= above) below to (belowEnd - hueWidth) / 2f else above to (aboveStart + hueWidth) / 2f
        return if (half < MIN_HUE_WIDTH) null else half / hueWidth to copy(hueCenter = wrapHue(hueCenter + middle), hueWidth = half)
    }

    private fun hueKey(h: Float): Float {
        if (hueWidth >= FULL_HUE) return 1f
        val d = abs(((h - hueCenter) % 360f + 540f) % 360f - 180f)
        return 1f - PowerWindow.fade(hueWidth, hueWidth + hueSoft, d)
    }

    companion object {
        /** A hue half-width that takes every hue. */
        const val FULL_HUE = 180f
        /** How far clean black / white move the matte's black / white point at 100. */
        const val MAX_CLEAN = 0.5f
        /** The blur radius at 100, as a share of the picture's height. */
        const val MAX_BLUR = 0.02f

        /** Taps of the matte blur. */
        const val BLUR_TAP_COUNT = 24

        /** π (3 − √5): consecutive taps turn by it, so no two sit in line. */
        private const val GOLDEN_ANGLE = 2.399963229728653

        /**
         * Offsets (x, y in radii) and weights of the matte blur's taps: [BLUR_TAP_COUNT] on a golden-angle
         * (Vogel) disk, each standing for an equal share of its area and weighted by a Gaussian with σ = half
         * the radius. Seen across an edge every tap sits at its own distance, so a hard key edge ramps in many
         * small steps rather than a few bands. The shader (`ClipLookEffect`) uses the same taps.
         */
        val BLUR_TAPS: List<Triple<Float, Float, Float>> = run {
            val taps = (0 until BLUR_TAP_COUNT).map { i ->
                val rho = sqrt((i + 0.5) / BLUR_TAP_COUNT)
                val a = i * GOLDEN_ANGLE
                Triple(rho * cos(a), rho * sin(a), exp(-2.0 * rho * rho))
            }
            val total = taps.sumOf { it.third }
            taps.map { (x, y, w) -> Triple(x.toFloat(), y.toFloat(), (w / total).toFloat()) }
        }

        fun chroma(r: Float, g: Float, b: Float): Float = maxOf(r, g, b) - minOf(r, g, b)

        fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

        /** Hue in degrees (0 for greys). */
        fun hue(r: Float, g: Float, b: Float): Float {
            val max = maxOf(r, g, b)
            val c = max - minOf(r, g, b)
            if (c <= 0f) return 0f
            val h = when (max) {
                r -> ((g - b) / c).let { if (it < 0f) it + 6f else it }
                g -> (b - r) / c + 2f
                else -> (r - g) / c + 4f
            }
            return h * 60f
        }

        /** 1 inside [low]…[high], fading to 0 across [soft] beyond each closed end. */
        fun band(x: Float, low: Float, high: Float, soft: Float): Float {
            val above = if (low <= 0f) 1f else PowerWindow.fade(low - soft, low, x)
            val below = if (high >= 1f) 1f else 1f - PowerWindow.fade(high, high + soft, x)
            return above * below
        }

        /**
         * The qualifier around a colour picked on the monitor: its hue ± 25°, saturation and luminance
         * ± 0.25. A grey has no hue to pick, so all hues are taken and the low saturations kept.
         */
        fun picked(r: Float, g: Float, b: Float): HslQualifier {
            val c = chroma(r, g, b)
            val y = luma(r, g, b)
            val grey = c < GREY_CHROMA
            return HslQualifier(
                hueCenter = if (grey) 0f else hue(r, g, b),
                hueWidth = if (grey) FULL_HUE else PICK_HUE_WIDTH,
                hueSoft = PICK_HUE_SOFT,
                satLow = if (grey) 0f else (c - PICK_RANGE).coerceAtLeast(0f),
                satHigh = if (grey) c + GREY_CHROMA else (c + PICK_RANGE).coerceAtMost(1f),
                satSoft = PICK_SOFT,
                lumLow = (y - PICK_RANGE).coerceAtLeast(0f),
                lumHigh = (y + PICK_RANGE).coerceAtMost(1f),
                lumSoft = PICK_SOFT,
            )
        }

        private const val GREY_CHROMA = 0.08f
        /** Feather add brings a colour to this key; a key this high counts as wholly selected for feather subtract. */
        private const val HALF_SELECTED = 0.5f
        private const val FULLY_SELECTED = 0.98f
        /** Feather subtract pulls an edge back to this share of the colour's distance past the inner range. */
        private const val FEATHER_OUT_SHARE = 0.8f
        /** The softest edges (the palette's slider ends). */
        private const val MAX_HUE_SOFT = 90f
        private const val MAX_SOFT = 0.5f

        /** Where smoothstep reaches [y] (0…1): its inverse, 0.5 − sin(asin(1 − 2y) / 3). */
        internal fun inverseSmoothstep(y: Float): Float = (0.5 - sin(kotlin.math.asin(1.0 - 2.0 * y.coerceIn(0f, 1f)) / 3.0)).toFloat()

        private fun ceilTo(x: Float, step: Float): Float = kotlin.math.ceil(x / step - 1e-4f) * step
        private fun floorTo(x: Float, step: Float): Float = kotlin.math.floor(x / step + 1e-4f) * step
        /** How far past a colour the picker moves a range's end (saturation / luminance, and hue in degrees). */
        private const val ADD_MARGIN = 0.03f
        private const val ADD_MARGIN_HUE = 4f
        /** The narrowest ranges a subtract may leave. */
        private const val MIN_HUE_WIDTH = 2f
        private const val MIN_RANGE = 0.02f
        /** A key this low counts as not selected. */
        private const val UNSELECTED = 0.02f
        /** How much more of its range a saturation or luminance cut must keep to win over a hue cut. */
        private const val HUE_CUT_PREFERENCE = 0.1f

        /** [d] degrees as the shortest turn, −180…180. */
        private fun signedHue(d: Float): Float = ((d % 360f) + 540f) % 360f - 180f

        private fun wrapHue(h: Float): Float = ((h % 360f) + 360f) % 360f

        /**
         * A range [low]…[high] cut so [x] lies past its end and [soft] edge — the larger side kept — as the
         * share of the range kept and the new ends; null when neither side keeps a usable range.
         */
        private fun rangeCut(x: Float, low: Float, high: Float, soft: Float): Triple<Float, Float, Float>? {
            val span = high - low
            if (span <= 0f) return null
            val gap = soft + ADD_MARGIN
            val top = minOf(x - gap, high)
            val bottom = maxOf(x + gap, low)
            val keptBelow = top - low
            val keptAbove = high - bottom
            return when {
                keptBelow >= keptAbove && keptBelow >= MIN_RANGE -> Triple(keptBelow / span, low, top)
                keptAbove >= MIN_RANGE -> Triple(keptAbove / span, bottom, high)
                else -> null
            }
        }
        private const val PICK_HUE_WIDTH = 25f
        private const val PICK_HUE_SOFT = 15f
        private const val PICK_RANGE = 0.25f
        private const val PICK_SOFT = 0.1f
    }
}

/** The qualifier's three ranges, for the feather pickers. */
enum class QualifierRange { HUE, SATURATION, LUMINANCE }

/** The qualifier palette's sliders (and their ranges). */
enum class QualifierControl(val label: String, val group: String, val min: Float, val max: Float, val step: Float) {
    HUE_CENTER("中心", "色相", 0f, 360f, 1f),
    HUE_WIDTH("范围", "色相", 0f, 180f, 1f),
    HUE_SOFT("柔化", "色相", 0f, 90f, 1f),
    SAT_LOW("下限", "饱和度", 0f, 1f, 0.01f),
    SAT_HIGH("上限", "饱和度", 0f, 1f, 0.01f),
    SAT_SOFT("柔化", "饱和度", 0f, 0.5f, 0.01f),
    LUM_LOW("下限", "亮度", 0f, 1f, 0.01f),
    LUM_HIGH("上限", "亮度", 0f, 1f, 0.01f),
    LUM_SOFT("柔化", "亮度", 0f, 0.5f, 0.01f),
    // Matte finesse: its own page of the palette.
    CLEAN_BLACK("净化黑场", "精修", 0f, 100f, 1f),
    CLEAN_WHITE("净化白场", "精修", 0f, 100f, 1f),
    BLUR("模糊半径", "精修", 0f, 100f, 1f);

    val finesse: Boolean get() = group == "精修"

    fun read(q: HslQualifier): Float = when (this) {
        HUE_CENTER -> q.hueCenter
        HUE_WIDTH -> q.hueWidth
        HUE_SOFT -> q.hueSoft
        SAT_LOW -> q.satLow
        SAT_HIGH -> q.satHigh
        SAT_SOFT -> q.satSoft
        LUM_LOW -> q.lumLow
        LUM_HIGH -> q.lumHigh
        LUM_SOFT -> q.lumSoft
        CLEAN_BLACK -> q.cleanBlack
        CLEAN_WHITE -> q.cleanWhite
        BLUR -> q.blur
    }

    /** [q] with this control at [value]; a low end never passes its high end, nor the other way round. */
    fun write(q: HslQualifier, value: Float): HslQualifier {
        val v = value.coerceIn(min, max)
        return when (this) {
            HUE_CENTER -> q.copy(hueCenter = v)
            HUE_WIDTH -> q.copy(hueWidth = v)
            HUE_SOFT -> q.copy(hueSoft = v)
            SAT_LOW -> q.copy(satLow = v.coerceAtMost(q.satHigh))
            SAT_HIGH -> q.copy(satHigh = v.coerceAtLeast(q.satLow))
            SAT_SOFT -> q.copy(satSoft = v)
            LUM_LOW -> q.copy(lumLow = v.coerceAtMost(q.lumHigh))
            LUM_HIGH -> q.copy(lumHigh = v.coerceAtLeast(q.lumLow))
            LUM_SOFT -> q.copy(lumSoft = v)
            CLEAN_BLACK -> q.copy(cleanBlack = v)
            CLEAN_WHITE -> q.copy(cleanWhite = v)
            BLUR -> q.copy(blur = v)
        }
    }
}

/** The window palette's sliders (and their ranges). */
enum class WindowControl(val label: String, val min: Float, val max: Float, val step: Float) {
    SIZE("大小", 0.05f, 2f, 0.01f),
    ASPECT("宽高比", 0.25f, 4f, 0.01f),
    ANGLE("角度", -180f, 180f, 1f),
    SOFTNESS("柔化", 0f, 1f, 0.01f),
    CENTER_X("水平位置", -1f, 1f, 0.01f),
    CENTER_Y("垂直位置", -1f, 1f, 0.01f);

    /** A gradient has no size or aspect; the full frame has no geometry at all. */
    fun appliesTo(shape: WindowShape): Boolean = shape != WindowShape.FULL && (shape != WindowShape.GRADIENT || (this != SIZE && this != ASPECT))

    fun read(window: PowerWindow): Float = when (this) {
        SIZE -> window.size
        ASPECT -> window.aspect
        ANGLE -> window.angle
        SOFTNESS -> window.softness
        CENTER_X -> window.centerX
        CENTER_Y -> window.centerY
    }

    fun write(window: PowerWindow, value: Float): PowerWindow {
        val v = value.coerceIn(min, max)
        return when (this) {
            SIZE -> window.copy(size = v)
            ASPECT -> window.copy(aspect = v)
            ANGLE -> window.copy(angle = v)
            SOFTNESS -> window.copy(softness = v)
            CENTER_X -> window.copy(centerX = v)
            CENTER_Y -> window.copy(centerY = v)
        }
    }
}

/** Primary sliders exposed on the Color stage (and their ranges). */
enum class PrimaryControl(val label: String, val min: Float, val max: Float, val step: Float) {
    EXPOSURE("曝光", -3f, 3f, 0.05f),
    CONTRAST("对比度", 0f, 2f, 0.01f),
    SATURATION("饱和度", 0f, 2f, 0.01f),
    HUE("色相", -180f, 180f, 1f),
    TEMPERATURE("色温", -100f, 100f, 1f),
    TINT("色调", -100f, 100f, 1f),
    HIGHLIGHTS("高光", -100f, 100f, 1f),
    SHADOWS("阴影", -100f, 100f, 1f),
    PIVOT("支点", 0f, 1f, 0.005f),
    COLOR_BOOST("色彩增强", -100f, 100f, 1f);

    fun read(grade: ColorGrade): Float = when (this) {
        EXPOSURE -> grade.exposure
        CONTRAST -> grade.contrast
        SATURATION -> grade.saturation
        HUE -> grade.hue
        TEMPERATURE -> grade.temperature
        TINT -> grade.tint
        HIGHLIGHTS -> grade.highlights
        SHADOWS -> grade.shadows
        PIVOT -> grade.pivot
        COLOR_BOOST -> grade.colorBoost
    }

    fun write(grade: ColorGrade, value: Float): ColorGrade {
        val v = value.coerceIn(min, max)
        return when (this) {
            EXPOSURE -> grade.copy(exposure = v)
            CONTRAST -> grade.copy(contrast = v)
            SATURATION -> grade.copy(saturation = v)
            HUE -> grade.copy(hue = v)
            TEMPERATURE -> grade.copy(temperature = v)
            TINT -> grade.copy(tint = v)
            HIGHLIGHTS -> grade.copy(highlights = v)
            SHADOWS -> grade.copy(shadows = v)
            PIVOT -> grade.copy(pivot = v)
            COLOR_BOOST -> grade.copy(colorBoost = v)
        }
    }
}
