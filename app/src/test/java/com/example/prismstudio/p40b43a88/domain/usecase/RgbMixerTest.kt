package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ChannelMix
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.MixerSwap
import com.example.prismstudio.p40b43a88.domain.model.RgbMixer
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's RGB Mixer: output channels mixed from the inputs, luminance kept, monochrome, swaps — inside the grade's LUT. */
class RgbMixerTest {

    private val out = FloatArray(3)
    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
    private fun mixed(m: RgbMixer, r: Float, g: Float, b: Float): FloatArray { m.apply(r, g, b, out); return out.copyOf() }

    @Test
    fun `untouched it changes nothing, and its switches alone keep the grade neutral`() {
        assertTrue(RgbMixer.NEUTRAL.isIdentity)
        assertArrayEquals(floatArrayOf(0.8f, 0.3f, 0.1f), mixed(RgbMixer.NEUTRAL, 0.8f, 0.3f, 0.1f), 1e-6f)
        val switched = ColorGrade(rgbMixer = RgbMixer(preserveLuminance = false))
        assertTrue(switched.isNeutral)
        assertFalse(ColorGrade(rgbMixer = RgbMixer(monochrome = true)).isNeutral)
    }

    @Test
    fun `an output takes the inputs it is given, scaled back to the input's luma unless that is off`() {
        // Red output takes half the green as well.
        val warm = RgbMixer(red = ChannelMix(1f, 0.5f, 0f), preserveLuminance = false)
        assertArrayEquals(floatArrayOf(0.55f, 0.3f, 0.1f), mixed(warm, 0.4f, 0.3f, 0.1f), 1e-6f)
        val kept = mixed(warm.copy(preserveLuminance = true), 0.4f, 0.3f, 0.1f)
        assertEquals(luma(floatArrayOf(0.4f, 0.3f, 0.1f)), luma(kept), 1e-5f)
        // The colour's balance stays the mix's: only its level is scaled.
        assertEquals(0.55f / 0.3f, kept[0] / kept[1], 1e-4f)
        // Black stays black.
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), mixed(warm.copy(preserveLuminance = true), 0f, 0f, 0f), 0f)
    }

    @Test
    fun `swaps trade two outputs, and monochrome makes one grey of the inputs`() {
        val swapped = RgbMixer(preserveLuminance = false).swapped(MixerSwap.RED_GREEN)
        assertEquals(ChannelMix(0f, 1f, 0f), swapped.red)
        assertEquals(ChannelMix(1f, 0f, 0f), swapped.green)
        assertArrayEquals(floatArrayOf(0.3f, 0.8f, 0.1f), mixed(swapped, 0.8f, 0.3f, 0.1f), 1e-6f)
        // Monochrome by Rec.709 luma: a colour turns to its luma, a grey stays itself.
        val mono = RgbMixer(monochrome = true)
        val y = luma(floatArrayOf(0.8f, 0.3f, 0.1f))
        assertArrayEquals(floatArrayOf(y, y, y), mixed(mono, 0.8f, 0.3f, 0.1f), 1e-6f)
        // A red filter's grey (red and green only): divided by the weights' sum while luminance is kept, not when off.
        val filter = RgbMixer(monochrome = true, mono = ChannelMix(1f, 1f, 0f))
        assertArrayEquals(floatArrayOf(0.55f, 0.55f, 0.55f), mixed(filter, 0.8f, 0.3f, 0.1f), 1e-6f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), mixed(filter, 0.5f, 0.5f, 0.5f), 1e-6f)
        assertArrayEquals(floatArrayOf(1.1f, 1.1f, 1.1f), mixed(filter.copy(preserveLuminance = false), 0.8f, 0.3f, 0.1f), 1e-6f)
    }

    @Test
    fun `the grade's LUT holds the mix, keyframes dissolve it, and the project file keeps it`() {
        val grade = ColorGrade(rgbMixer = RgbMixer(preserveLuminance = false).swapped(MixerSwap.RED_BLUE))
        val lut = BuildGradeLutUseCase()
        lut.apply(grade, 0.8f, 0.3f, 0.1f, out)
        assertArrayEquals(floatArrayOf(0.1f, 0.3f, 0.8f), out, 1e-5f)
        // Half way from untouched to the swap: every amount half way.
        val half = ColorGrade().lerp(grade, 0.5f).rgbMixer
        assertEquals(ChannelMix(0.5f, 0f, 0.5f), half.red)
        assertEquals(ChannelMix(0f, 1f, 0f), half.green)
        // Written only once moved off its defaults.
        val plain = projectOf(timeline = timelineOf(clipOf("c", 0, 30)))
        assertFalse(ProjectJsonCodec.encode(plain).contains("\"rgbMixer\""))
        val mono = RgbMixer(monochrome = true, mono = ChannelMix(0.6f, 0.4f, 0f), preserveLuminance = false)
        val graded = plain.copy(timeline = plain.timeline.copy(clips = plain.timeline.clips.map { it.copy(grade = ColorGrade(rgbMixer = mono)) }))
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(graded))
        assertEquals(mono, back.timeline.clips.single().grade.rgbMixer)
    }
}
