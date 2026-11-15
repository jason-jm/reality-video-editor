package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HSL qualifier (Resolve's Qualifier palette) keying node 2 by colour. */
class HslQualifierTest {

    private val greens = HslQualifier(hueCenter = 120f, hueWidth = 30f, hueSoft = 15f, satLow = 0.2f, satHigh = 1f, satSoft = 0.1f)

    @Test
    fun `hue, chroma and luma follow the usual definitions`() {
        assertEquals(0f, HslQualifier.hue(1f, 0f, 0f), 1e-4f)
        assertEquals(120f, HslQualifier.hue(0f, 1f, 0f), 1e-4f)
        assertEquals(240f, HslQualifier.hue(0f, 0f, 1f), 1e-4f)
        assertEquals(300f, HslQualifier.hue(1f, 0f, 1f), 1e-4f)
        assertEquals("grey has no hue", 0f, HslQualifier.hue(0.5f, 0.5f, 0.5f), 0f)
        assertEquals(0.6f, HslQualifier.chroma(0.8f, 0.2f, 0.5f), 1e-6f)
        assertEquals(0.7152f, HslQualifier.luma(0f, 1f, 0f), 1e-6f)
    }

    @Test
    fun `a hue range selects its hues fully and fades across the soft edge`() {
        assertEquals(1f, greens.key(0.1f, 0.8f, 0.1f), 1e-4f)
        assertEquals("red is far outside", 0f, greens.key(0.8f, 0.1f, 0.1f), 1e-4f)
        // Hue 157.5°: 37.5° from the centre, half way across the 30–45° soft edge.
        val halfway = Color(157.5f)
        assertEquals(0.5f, greens.key(halfway[0], halfway[1], halfway[2]), 1e-3f)
        assertEquals("a grey is below the saturation floor", 0f, greens.key(0.5f, 0.5f, 0.5f), 1e-4f)
    }

    @Test
    fun `the hue range wraps around red and every hue is taken at a half-width of 180°`() {
        val reds = HslQualifier(hueCenter = 350f, hueWidth = 20f, hueSoft = 0f, satLow = 0f)
        assertEquals("5° is 15° from 350°, across 0°", 1f, reds.key(1f, 1f / 12f, 0f), 1e-4f)
        assertEquals("348°", 1f, reds.key(1f, 0f, 0.2f), 1e-4f)
        assertEquals("30° is 40° away", 0f, reds.key(1f, 0.5f, 0f), 1e-4f)
        assertEquals(0f, reds.key(0f, 1f, 0f), 1e-4f)
        val all = reds.copy(hueWidth = 180f)
        assertEquals(1f, all.key(0f, 1f, 0f), 1e-4f)
    }

    @Test
    fun `saturation and luminance ranges are open at 0 and 1 and invert takes the rest`() {
        val darks = HslQualifier(hueWidth = 180f, satLow = 0f, satHigh = 1f, lumLow = 0f, lumHigh = 0.3f, lumSoft = 0.1f)
        assertEquals(1f, darks.key(0f, 0f, 0f), 1e-4f)
        assertEquals(1f, darks.key(0.3f, 0.3f, 0.3f), 1e-4f)
        assertEquals(0.5f, darks.key(0.35f, 0.35f, 0.35f), 1e-3f)
        assertEquals(0f, darks.key(0.9f, 0.9f, 0.9f), 1e-4f)
        assertEquals(1f, darks.copy(invert = true).key(0.9f, 0.9f, 0.9f), 1e-4f)
    }

    @Test
    fun `a picked colour centres the ranges on it, and a picked grey takes every hue`() {
        val picked = HslQualifier.picked(0.1f, 0.7f, 0.2f)
        assertEquals(130f, picked.hueCenter, 0.01f)
        assertEquals(25f, picked.hueWidth, 0f)
        assertEquals(0.35f, picked.satLow, 1e-4f)
        assertEquals(0.85f, picked.satHigh, 1e-4f)
        assertEquals(1f, picked.key(0.1f, 0.7f, 0.2f), 1e-4f)
        assertEquals("a blue sky is not taken", 0f, picked.key(0.2f, 0.4f, 0.9f), 1e-4f)

        val grey = HslQualifier.picked(0.5f, 0.52f, 0.5f)
        assertEquals(HslQualifier.FULL_HUE, grey.hueWidth, 0f)
        assertEquals(0f, grey.satLow, 0f)
        assertTrue(grey.key(0.5f, 0.5f, 0.5f) > 0.99f)
        assertEquals("a saturated colour is not grey", 0f, grey.key(0.1f, 0.7f, 0.2f), 1e-4f)
    }

    @Test
    fun `matte finesse levels the matte and its blur taps make a whole`() {
        val q = HslQualifier(cleanBlack = 40f, cleanWhite = 20f)
        // Black point 0.2, white point 0.9.
        assertEquals(0f, q.clean(0.15f), 0f)
        assertEquals(0.5f, q.clean(0.55f), 1e-6f)
        assertEquals(1f, q.clean(0.95f), 0f)
        assertEquals("untouched without finesse", 0.37f, HslQualifier().clean(0.37f), 0f)
        assertEquals(1f, HslQualifier.BLUR_TAPS.sumOf { it.third.toDouble() }.toFloat(), 1e-5f)
        assertEquals(24, HslQualifier.BLUR_TAPS.size)
        // Across an edge every tap sits at its own distance and none weighs much: many small steps, no bands.
        assertEquals(24, HslQualifier.BLUR_TAPS.map { it.first }.distinct().size)
        assertTrue(HslQualifier.BLUR_TAPS.maxOf { it.third } < 0.1f)
        assertTrue(HslQualifier.BLUR_TAPS.all { (x, y, _) -> x * x + y * y <= 1f })
        assertEquals(0.01f, HslQualifier(blur = 50f).blurRadius, 1e-6f)
        assertEquals(listOf(false, true), listOf(HslQualifier(), HslQualifier(blur = 1f)).map { it.isRefined })
        assertEquals(listOf(QualifierControl.CLEAN_BLACK, QualifierControl.CLEAN_WHITE, QualifierControl.BLUR), QualifierControl.entries.filter { it.finesse })
        assertEquals(100f, QualifierControl.BLUR.write(HslQualifier(), 250f).blur, 0f)
    }

    @Test
    fun `picker add grows each range just enough to take the colour in`() {
        val q = HslQualifier(hueCenter = 120f, hueWidth = 25f, hueSoft = 15f, satLow = 0.3f, satHigh = 0.8f, lumLow = 0.3f, lumHigh = 0.7f, blur = 40f)
        assertNull("a colour selected already adds nothing", q.adding(0.2f, 0.7f, 0.25f))
        // A teal at hue 170 (chroma 0.5, luma 0.59): the hue range's upper end moves to 174, its lower end stays at 95.
        val teal = floatArrayOf(0.2f, 0.7f, 0.6167f)
        assertEquals(170f, HslQualifier.hue(teal[0], teal[1], teal[2]), 0.1f)
        val wide = q.adding(teal[0], teal[1], teal[2])!!
        assertEquals(134.5f, wide.hueCenter, 0.1f)
        assertEquals(39.5f, wide.hueWidth, 0.1f)
        assertEquals(1f, wide.key(teal[0], teal[1], teal[2]), 1e-4f)
        assertEquals("what was selected stays selected", 1f, wide.key(0.25f, 0.7f, 0.2f), 1e-4f)
        assertEquals("the matte finesse is kept", 40f, wide.blur, 0f)
        // A dark green: saturation and luminance reach down to it, a little past.
        val dark = q.adding(0.06f, 0.25f, 0.06f)!!
        assertEquals(0.16f, dark.satLow, 1e-4f)
        assertEquals(HslQualifier.luma(0.06f, 0.25f, 0.06f) - 0.03f, dark.lumLow, 1e-4f)
        assertEquals(1f, dark.key(0.06f, 0.25f, 0.06f), 1e-4f)
        // A grey has no hue: every hue is taken, and the saturation reaches down to it.
        val grey = q.adding(0.5f, 0.5f, 0.5f)!!
        assertEquals(HslQualifier.FULL_HUE, grey.hueWidth, 0f)
        assertEquals(1f, grey.key(0.5f, 0.5f, 0.5f), 1e-4f)
    }

    @Test
    fun `picker subtract cuts the range that keeps the most of itself`() {
        val q = HslQualifier(hueCenter = 120f, hueWidth = 25f, hueSoft = 15f, satLow = 0.3f, satHigh = 0.8f, lumLow = 0.3f, lumHigh = 0.7f)
        // Hue 140, near the top of 95…145: cutting the hue keeps 95…121 (half of it), more than any
        // saturation or luminance cut keeps; its soft edge stops short of 140.
        val edge = floatArrayOf(0.2f, 0.7f, 0.3667f)
        val cut = q.subtracting(edge[0], edge[1], edge[2])!!
        assertEquals(108f, cut.hueCenter, 0.1f)
        assertEquals(13f, cut.hueWidth, 0.1f)
        assertEquals(0f, cut.key(edge[0], edge[1], edge[2]), 0f)
        assertEquals("the other side of the range stays", 1f, cut.key(0.2f, 0.7f, 0.0333f), 1e-3f)
        // A dark shade at the hue's centre, with open saturation and luminance: the luminance's low end rises past it.
        val open = HslQualifier(hueCenter = 120f, hueWidth = 40f, hueSoft = 15f, satLow = 0f, satHigh = 1f, lumLow = 0f, lumHigh = 1f)
        val shade = open.subtracting(0.02f, 0.12f, 0.02f)!!
        assertEquals(HslQualifier.luma(0.02f, 0.12f, 0.02f) + 0.13f, shade.lumLow, 1e-4f)
        assertEquals(0f, shade.key(0.02f, 0.12f, 0.02f), 0f)
        assertEquals("a bright green stays", 1f, shade.key(0.3f, 0.9f, 0.3f), 1e-4f)
        assertNull("a colour not selected has nothing to subtract", q.subtracting(0.1f, 0.1f, 0.8f))
        // Green widened to orange (21…144°), then the saturated orange (hue 25°, chroma 0.99) taken out: the hue
        // cut keeps 81 % of the hue range, a saturation cut 81.5 % of its own — on a near tie the hue is cut.
        val wide = HslQualifier(hueCenter = 82.7f, hueWidth = 61.35f, hueSoft = 15f, satLow = 0.256f, satHigh = 1f, lumLow = 0.113f, lumHigh = 0.613f)
        val orange = floatArrayOf(0.996f, 0.424f, 0.004f)
        val trimmed = wide.subtracting(orange[0], orange[1], orange[2])!!
        assertEquals(1f, trimmed.satHigh, 0f)
        assertEquals(49.8f, trimmed.hueWidth, 0.2f)
        assertEquals(0f, trimmed.key(orange[0], orange[1], orange[2]), 0f)
        // Inverted, add and subtract trade places: adding the edge colour takes it out of the ranges.
        val inverted = q.copy(invert = true)
        assertNull("inverted, the far teal is in the selection already", inverted.adding(0.2f, 0.7f, 0.9f))
        assertEquals(1f, inverted.adding(edge[0], edge[1], edge[2])!!.key(edge[0], edge[1], edge[2]), 0f)
    }

    @Test
    fun `feather add softens an edge just enough to take a colour half in, and feather subtract pulls it back past it`() {
        // Greens: hues 90–150°, soft 15°. A yellow-green at 165° (0, 1, 0.75) sits at the soft edge's far end: unselected.
        val (r, g, b) = Triple(0f, 1f, 0.75f)
        assertEquals(0f, greens.rangeKey(r, g, b), 1e-4f)
        val inside = greens.featheringIn(r, g, b)!!
        assertEquals("half selected", 0.5f, inside.rangeKey(r, g, b), 0.01f)
        assertEquals("the soft edge twice its distance", 30f, inside.hueSoft, 0f)
        assertEquals("the range stays", greens.copy(hueSoft = 30f), inside)
        assertNull("half selected already", inside.featheringIn(r, g, b))
        // Past two ranges (hue 165°, chroma 0.15 under the 0.2 floor), each edge keys it at √½: half in all.
        val pale = Triple(0.4f, 0.55f, 0.5125f)
        val both = greens.featheringIn(pale.first, pale.second, pale.third)!!
        assertEquals(0.5f, both.rangeKey(pale.first, pale.second, pale.third), 0.02f)
        assertTrue(both.hueSoft > 15f && both.satSoft > 0.1f)
        assertNull("a grey has no hue to feather toward", greens.featheringIn(0.5f, 0.5f, 0.5f))
        assertNull("red is beyond any edge", greens.featheringIn(1f, 0f, 0f))

        val out = inside.featheringOut(r, g, b)!!
        assertEquals(0f, out.rangeKey(r, g, b), 1e-4f)
        assertEquals("pulled back to four fifths of its distance", 12f, out.hueSoft, 0f)
        assertNull("a green inside every range is for picker subtract", inside.featheringOut(0f, 1f, 0f))
        assertNull("red is not selected", inside.featheringOut(1f, 0f, 0f))
        assertEquals(0.5f, HslQualifier.inverseSmoothstep(0.5f), 1e-5f)
    }

    @Test
    fun `slider ends never cross and the full-frame window covers everything`() {
        val q = QualifierControl.SAT_LOW.write(HslQualifier(satHigh = 0.5f), 0.8f)
        assertEquals(0.5f, q.satLow, 0f)
        assertEquals(0.4f, QualifierControl.LUM_HIGH.write(HslQualifier(lumLow = 0.4f), 0.1f).lumHigh, 0f)
        assertEquals(1f, PowerWindow(shape = WindowShape.FULL).coverage(0.05f, 0.95f, 16f / 9f), 0f)
        assertEquals(0f, PowerWindow(shape = WindowShape.FULL, invert = true).coverage(0.5f, 0.5f, 16f / 9f), 0f)
        assertTrue(WindowControl.entries.none { it.appliesTo(WindowShape.FULL) })
    }

    /** A fully saturated, full-brightness colour of [hue] degrees. */
    private fun Color(hue: Float): FloatArray {
        val c = 1f
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        return when ((hue / 60f).toInt()) {
            0 -> floatArrayOf(c, x, 0f)
            1 -> floatArrayOf(x, c, 0f)
            2 -> floatArrayOf(0f, c, x)
            3 -> floatArrayOf(0f, x, c)
            4 -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
    }
}
