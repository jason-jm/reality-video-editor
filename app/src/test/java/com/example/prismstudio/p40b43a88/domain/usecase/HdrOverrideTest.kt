package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.CompositionFactory
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.HdrToneMap
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.SourceColor
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping
import com.example.prismstudio.p40b43a88.domain.model.hdrByOverride
import com.example.prismstudio.p40b43a88.domain.model.toneMapped
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HDR by the input colour override (a file that says SDR, marked HLG or PQ): every player reads its code values as SDR, so
 * the timeline's tone map runs wherever it shows — the monitor, the source monitor, the scopes — and its freeze frames
 * keep the override, as the render does.
 */
class HdrOverrideTest {

    private val color = ColorManagement(ToneMapping.LUMINANCE, 1000)
    private val plain = assetOf("v")
    private val overridden = plain.copy(inputColor = InputColorSpace.HLG)
    private val tagged = plain.copy(color = SourceColor(transfer = ColorTransfer.HLG, bitDepth = 10))

    @Test
    fun `only a file that says SDR but is marked HDR is HDR by the override`() {
        assertTrue(overridden.hdrByOverride)
        assertTrue(plain.copy(inputColor = InputColorSpace.PQ).hdrByOverride)
        assertFalse("SDR stays SDR", plain.hdrByOverride)
        assertFalse("a tagged file is read as HDR by the players", tagged.hdrByOverride)
        assertFalse("a tagged file marked Rec.709", tagged.copy(inputColor = InputColorSpace.REC709).hdrByOverride)
    }

    @Test
    fun `preview and render tone map tagged and manually identified HDR pictures`() {
        assertTrue(CompositionFactory.toneMaps(overridden, color))
        assertTrue(CompositionFactory.toneMaps(tagged, color))
        assertFalse(CompositionFactory.toneMaps(tagged.copy(inputColor = InputColorSpace.REC709), color))
        assertFalse(CompositionFactory.toneMaps(plain, color))
        assertFalse("no colour management, no tone map", CompositionFactory.toneMaps(overridden, null))
        assertFalse("pictures only", CompositionFactory.toneMaps(overridden.copy(kind = MediaKind.IMAGE), color))
    }

    @Test
    fun `a frame is tone mapped pixel for pixel as the render's shader does, its alpha kept`() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFF808080.toInt(), 0x80C0A040.toInt(), 0xFFFFFFFF.toInt())
        val frame = FramePixels(2, 2, pixels)
        val mapped = frame.toneMapped(ColorTransfer.HLG, color)
        for (i in pixels.indices) {
            val p = pixels[i]
            val expect = HdrToneMap.map(floatArrayOf((p shr 16 and 0xFF) / 255f, (p shr 8 and 0xFF) / 255f, (p and 0xFF) / 255f), ColorTransfer.HLG, ToneMapping.LUMINANCE, 1000)
            val q = mapped.pixels[i]
            assertEquals("alpha $i", p ushr 24, q ushr 24)
            assertEquals("red $i", Math.round(expect[0] * 255), q shr 16 and 0xFF)
            assertEquals("green $i", Math.round(expect[1] * 255), q shr 8 and 0xFF)
            assertEquals("blue $i", Math.round(expect[2] * 255), q and 0xFF)
        }
        // An SDR source passes through untouched.
        assertSame(frame, frame.toneMapped(ColorTransfer.SDR, color))
    }

    @Test
    fun `a freeze frame preserves the source transfer from the override or file tag`() {
        val freeze = FreezeFrameUseCase(SequentialIds())
        fun stillOf(asset: com.example.prismstudio.p40b43a88.domain.model.MediaAsset): com.example.prismstudio.p40b43a88.domain.model.MediaAsset {
            val doc = projectOf(timelineOf(clipOf("A", 0, 90, assetId = "v")), media = listOf(asset))
            val request = freeze.request(doc, "A", 30)
            val result = freeze.insert(doc, request, assetOf(request.stillId), 30, 60)
            return result.doc.media.first { it.id == request.stillId }
        }
        assertEquals(InputColorSpace.HLG, stillOf(overridden).inputColor)
        assertTrue(stillOf(overridden).hdrByOverride)
        assertEquals(InputColorSpace.HLG, stillOf(tagged).inputColor)
        assertEquals(InputColorSpace.PQ, stillOf(tagged.copy(color = SourceColor(transfer = ColorTransfer.PQ))).inputColor)
        assertEquals(InputColorSpace.AUTO, stillOf(plain).inputColor)
    }
}
