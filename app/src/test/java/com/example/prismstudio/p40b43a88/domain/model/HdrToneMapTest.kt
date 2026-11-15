package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.roundToInt

/** HDR sources tone mapped into the Rec.709 timeline: the transfers' light, the curve, the four methods. */
class HdrToneMapTest {

    private fun grey(e: Float, transfer: ColorTransfer, method: ToneMapping = ToneMapping.LUMINANCE, peak: Int = 1000) =
        HdrToneMap.map(floatArrayOf(e, e, e), transfer, method, peak)

    /** A code value as an 8-bit narrow-range luma (what the render's file holds). */
    private fun y8(v: Float) = (16 + 219 * v).roundToInt()

    @Test
    fun `HLG's reference white is 203 nits on a 1000-nit display, PQ's code values are absolute nits`() {
        val scene = HdrToneMap.hlgToScene(0.75f)
        assertEquals(0.2647f, scene, 1e-3f)
        assertEquals("BT.2408: 75 % HLG is reference white", 203f, 1000f * scene.pow(1.2f), 1.5f)
        assertEquals(0f, HdrToneMap.hlgToScene(0f), 0f)
        assertEquals(1f, HdrToneMap.hlgToScene(1f), 1e-4f)
        assertEquals(92.2f, HdrToneMap.pqToNits(0.5f), 0.5f)
        assertEquals(983f, HdrToneMap.pqToNits(0.75f), 5f)
        assertEquals(10000f, HdrToneMap.pqToNits(1f), 1f)
        assertEquals(0f, HdrToneMap.pqToNits(0f), 1e-4f)
    }

    @Test
    fun `the curve is linear to the knee, turns without a kink and meets white at the source's peak`() {
        for (peakNits in listOf(1000f, 2000f, 4000f)) {
            val peak = peakNits / HdrToneMap.REFERENCE_WHITE_NITS
            val tau = HdrToneMap.shoulderTau(peak)
            val k = HdrToneMap.KNEE
            assertEquals(HdrToneMap.GAIN * 0.3f, HdrToneMap.curve(0.3f, peak, tau), 1e-6f)
            val below = (HdrToneMap.curve(k, peak, tau) - HdrToneMap.curve(k - 1e-3f, peak, tau)) / 1e-3f
            val above = (HdrToneMap.curve(k + 1e-3f, peak, tau) - HdrToneMap.curve(k, peak, tau)) / 1e-3f
            assertEquals("the same slope either side of the knee ($peakNits nits)", below, above, 0.01f)
            assertEquals(1f, HdrToneMap.curve(peak, peak, tau), 1e-5f)
            var last = -1f
            for (i in 0..400) {
                val y = HdrToneMap.curve(peak * i / 400f, peak, tau)
                assertTrue(y >= last)
                last = y
            }
        }
        // A brighter master is squeezed harder: reference white comes out darker.
        val w1000 = HdrToneMap.curve(1f, 1000f / 203f, HdrToneMap.shoulderTau(1000f / 203f))
        val w4000 = HdrToneMap.curve(1f, 4000f / 203f, HdrToneMap.shoulderTau(4000f / 203f))
        assertTrue(w4000 < w1000)
    }

    @Test
    fun `greys stay grey, brighter in, brighter out, whatever the method - and SDR passes untouched`() {
        val levels = listOf(0.10f, 0.30f, 0.50f, 0.75f, 0.90f)
        for (transfer in listOf(ColorTransfer.HLG, ColorTransfer.PQ)) {
            val out = levels.map { grey(it, transfer) }
            out.forEach { assertEquals(it[0], it[1], 1e-4f); assertEquals(it[0], it[2], 1e-4f) }
            assertEquals(out.map { it[0] }.sorted(), out.map { it[0] })
            for (method in listOf(ToneMapping.SIMPLE, ToneMapping.HUE)) {
                levels.forEach { assertEquals(grey(it, transfer)[0], grey(it, transfer, method)[0], 1e-4f) }
            }
            // The figures the device render is checked against (8-bit narrow range).
            println("$transfer bands: " + out.map { y8(it[0]) })
        }
        assertEquals("不映射: HLG reference white is SDR white", 1f, grey(0.75f, ColorTransfer.HLG, ToneMapping.NONE)[0], 2e-3f)
        assertArrayEquals(floatArrayOf(0.2f, 0.4f, 0.6f), HdrToneMap.map(floatArrayOf(0.2f, 0.4f, 0.6f), ColorTransfer.SDR, ToneMapping.SIMPLE, 1000), 0f)
        // A 4000-nit PQ master: the same code value comes out darker than from a 1000-nit one.
        assertTrue(grey(0.75f, ColorTransfer.PQ, peak = 4000)[0] < grey(0.75f, ColorTransfer.PQ, peak = 1000)[0])
    }

    @Test
    fun `luminance mapping keeps a bright colour's hue, simple lets it whiten, keeping hue gives up saturation over white`() {
        val red = floatArrayOf(0.92f, 0.35f, 0.30f)
        fun hue(c: FloatArray): Double {
            val l = FloatArray(3) { c[it].pow(2.4f) }
            return atan2(Math.sqrt(3.0) * (l[1] - l[2]), 2.0 * l[0] - l[1] - l[2])
        }
        val simple = HdrToneMap.map(red, ColorTransfer.HLG, ToneMapping.SIMPLE, 1000)
        val hueKept = HdrToneMap.map(red, ColorTransfer.HLG, ToneMapping.HUE, 1000)
        val luminance = HdrToneMap.map(red, ColorTransfer.HLG, ToneMapping.LUMINANCE, 1000)
        // Simple compresses each channel: the red's channels move together, it whitens.
        assertTrue("simple whitens", simple[1] / simple[0] > luminance[1] / luminance[0] - 1e-3f)
        assertTrue(hueKept.all { it <= 1f })
        val reference = hue(HdrToneMap.map(floatArrayOf(0.5f, 0.19f, 0.16f), ColorTransfer.HLG, ToneMapping.LUMINANCE, 1000))
        assertTrue("the hue is kept within a few degrees", abs(hue(hueKept) - reference) < 0.12)
    }

    @Test
    fun `a file's tags give its colour, a hand-set input space overrides them`() {
        val hlg = SourceColor.fromMediaFormat(standard = 6, transfer = 7, tenBit = true)
        assertEquals(SourceColor(ColorPrimaries.BT2020, ColorTransfer.HLG, 10), hlg)
        assertEquals("Rec.2100 HLG · 10 位", hlg.label)
        assertEquals("Rec.2100 PQ（HDR10）· 10 位", SourceColor.fromMediaFormat(6, 6, true).label)
        assertEquals("Rec.709", SourceColor.fromMediaFormat(1, 3, false).label)
        assertEquals("未标记（按 Rec.709）", SourceColor.fromMediaFormat(null, null, false).label)
        val asset = MediaAsset("a", "u", "n", MediaKind.VIDEO, 1000, 640, 360, 30f, "HEVC", "", false, 0, "", color = hlg)
        assertEquals(ColorTransfer.HLG, asset.effectiveTransfer)
        assertEquals(ColorTransfer.SDR, asset.copy(inputColor = InputColorSpace.REC709).effectiveTransfer)
        assertEquals(ColorTransfer.PQ, asset.copy(color = SourceColor.UNKNOWN, inputColor = InputColorSpace.PQ).effectiveTransfer)
    }

    @Test
    fun `colour tags, the input colour space and the project's colour management survive a round trip`() {
        val hlg = com.example.prismstudio.p40b43a88.domain.assetOf("media").copy(color = SourceColor(ColorPrimaries.BT2020, ColorTransfer.HLG, 10), inputColor = InputColorSpace.PQ)
        val doc = com.example.prismstudio.p40b43a88.domain.projectOf(timeline = com.example.prismstudio.p40b43a88.domain.timelineOf(com.example.prismstudio.p40b43a88.domain.clipOf("c", 0, 30, assetId = hlg.id)))
            .let { it.copy(media = listOf(hlg), colorManagement = ColorManagement(ToneMapping.SIMPLE, 2000)) }
        val reread = com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec.decode(com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec.encode(doc))
        assertEquals(hlg.color, reread.media.single().color)
        assertEquals(InputColorSpace.PQ, reread.media.single().inputColor)
        assertEquals(ColorManagement(ToneMapping.SIMPLE, 2000), reread.colorManagement)
        val uses = com.example.prismstudio.p40b43a88.domain.usecase.ColorManagementUseCase()
        assertEquals(mapOf(ColorTransfer.PQ to 1), uses.hdrClips(reread))
        assertEquals(null, uses.note(doc.copy(media = listOf(hlg.copy(inputColor = InputColorSpace.REC709)))))
    }
}
