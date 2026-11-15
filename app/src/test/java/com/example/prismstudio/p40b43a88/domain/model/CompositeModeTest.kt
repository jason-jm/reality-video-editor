package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeModeTest {

    private fun assertBlend(expected: Float, mode: CompositeMode, b: Float, s: Float) =
        assertEquals("$mode($b, $s)", expected, mode.blend(b, s), 1e-6f)

    private fun assertBlend(why: String, mode: CompositeMode, b: Float, s: Float) =
        assertEquals("$why: $mode($b, $s)", if (mode == CompositeMode.COLOR_DODGE) 0f else 1f, mode.blend(b, s), 1e-6f)

    @Test
    fun `each mode combines one channel beneath with the clip's own`() {
        assertBlend(0.5f, CompositeMode.NORMAL, 0.25f, 0.5f)
        assertBlend(0.25f, CompositeMode.DARKEN, 0.25f, 0.5f)
        assertBlend(0.125f, CompositeMode.MULTIPLY, 0.25f, 0.5f)
        assertBlend(0.5f, CompositeMode.LIGHTEN, 0.25f, 0.5f)
        assertBlend(0.625f, CompositeMode.SCREEN, 0.25f, 0.5f)
        assertBlend(0.75f, CompositeMode.ADD, 0.25f, 0.5f)
        assertBlend(1f, CompositeMode.ADD, 0.75f, 0.5f)
        assertBlend(0.25f, CompositeMode.DIFFERENCE, 0.25f, 0.5f)
        // Overlay multiplies dark picture beneath and screens bright, both doubled.
        assertBlend(0.25f, CompositeMode.OVERLAY, 0.25f, 0.5f)
        assertBlend(0.75f, CompositeMode.OVERLAY, 0.75f, 0.5f)
        // Soft light (W3C): darkens with a dark clip colour, lightens towards D(b) with a bright one.
        assertBlend(0.15625f, CompositeMode.SOFT_LIGHT, 0.25f, 0.25f)
        assertBlend(0.375f, CompositeMode.SOFT_LIGHT, 0.25f, 0.75f)
        assertBlend(0.72f, CompositeMode.SOFT_LIGHT, 0.64f, 0.75f)
        assertBlend(0.25f, CompositeMode.SOFT_LIGHT, 0.25f, 0.5f)
    }

    @Test
    fun `screen over black and multiply over white leave the clip as it is`() {
        listOf(0f, 0.3f, 1f).forEach { s ->
            assertBlend(s, CompositeMode.SCREEN, 0f, s)
            assertBlend(s, CompositeMode.MULTIPLY, 1f, s)
            assertBlend(s, CompositeMode.ADD, 0f, s)
        }
    }

    @Test
    fun `the clip's alpha mixes the blended colour over what is beneath`() {
        assertEquals(0.4375f, CompositeMode.SCREEN.over(0.25f, 0.5f, 0.5f), 1e-6f)
        assertEquals(0.375f, CompositeMode.NORMAL.over(0.25f, 0.5f, 0.5f), 1e-6f)
        // Fully transparent, every mode leaves the picture beneath alone.
        val beneath = floatArrayOf(0.25f, 0.5f, 0.75f)
        CompositeMode.entries.forEach { assertArrayEquals(it.name, beneath, it.over(beneath, floatArrayOf(0.9f, 0.1f, 0.4f), 0f), 1e-6f) }
        assertArrayEquals(floatArrayOf(0.4375f, 0.75f, 0.875f), CompositeMode.SCREEN.over(beneath, floatArrayOf(0.5f, 1f, 1f), 0.5f), 1e-6f)
    }

    @Test
    fun `colour dodge and burn brighten or darken by the clip's colour, hard light and exclusion as Photoshop`() {
        assertBlend(0.5f, CompositeMode.COLOR_DODGE, 0.25f, 0.5f)
        assertBlend(1f, CompositeMode.COLOR_DODGE, 0.6f, 0.5f)
        assertBlend("black stays black", CompositeMode.COLOR_DODGE, 0f, 0.9f)
        assertBlend(1f, CompositeMode.COLOR_DODGE, 0.3f, 1f)
        assertBlend(0.5f, CompositeMode.COLOR_BURN, 0.75f, 0.5f)
        assertBlend(0f, CompositeMode.COLOR_BURN, 0.25f, 0.5f)
        assertBlend("white stays white", CompositeMode.COLOR_BURN, 1f, 0.2f)
        assertBlend(0f, CompositeMode.COLOR_BURN, 0.5f, 0f)
        assertBlend(0.125f, CompositeMode.HARD_LIGHT, 0.25f, 0.25f)
        assertBlend(0.625f, CompositeMode.HARD_LIGHT, 0.25f, 0.75f)
        // Hard light is overlay with the two layers swapped.
        listOf(0.1f, 0.5f, 0.8f).forEach { b -> listOf(0.2f, 0.5f, 0.9f).forEach { s -> assertBlend(CompositeMode.OVERLAY.blend(s, b), CompositeMode.HARD_LIGHT, b, s) } }
        assertBlend(0.5f, CompositeMode.EXCLUSION, 0.25f, 0.5f)
        assertBlend(0.7f, CompositeMode.EXCLUSION, 1f, 0.3f)
    }

    @Test
    fun `hue, saturation, colour and luminosity trade one component of the colour beneath for the clip's`() {
        val red = floatArrayOf(1f, 0f, 0f)
        val grey = floatArrayOf(0.5f, 0.5f, 0.5f)
        val blue = floatArrayOf(0.2f, 0.4f, 0.8f) // saturation 0.6, luminosity 0.384
        // Luminosity: red beneath at the grey clip's luminosity (pulled back inside 0…1, keeping its hue).
        assertArrayEquals(floatArrayOf(1f, 0.2857143f, 0.2857143f), CompositeMode.LUMINOSITY.blend(red, grey), 1e-5f)
        // Colour: the red clip's hue and saturation at the grey luminosity beneath.
        assertArrayEquals(floatArrayOf(1f, 0.2857143f, 0.2857143f), CompositeMode.COLOR.blend(grey, red), 1e-5f)
        // Hue: the red clip's hue at the blue's saturation and luminosity.
        assertArrayEquals(floatArrayOf(0.804f, 0.204f, 0.204f), CompositeMode.HUE.blend(blue, red), 1e-5f)
        // Saturation: a grey clip takes the colour out, the luminosity stays.
        assertArrayEquals(floatArrayOf(0.384f, 0.384f, 0.384f), CompositeMode.SATURATION.blend(blue, grey), 1e-5f)
        // A grey clip has no hue: a decoded grey's float noise (far under a code value) must not turn into a colour.
        val noisyGrey = floatArrayOf(0.3960f, 0.3962f, 0.3959f)
        val hue = CompositeMode.HUE.blend(floatArrayOf(0.01f, 0.58f, 0.01f), noisyGrey)
        assertEquals("grey", 0f, hue.max() - hue.min(), 1e-6f)
        val saturation = CompositeMode.SATURATION.blend(floatArrayOf(0.5001f, 0.4999f, 0.5f), red)
        assertEquals("a grey beneath stays grey", 0f, saturation.max() - saturation.min(), 1e-6f)
        assertFalse(CompositeMode.HUE.separable)
        assertTrue(CompositeMode.HARD_LIGHT.separable)
        // Separable modes give the same colour channel by channel.
        assertArrayEquals(floatArrayOf(0.4f, 0.8f, 1f), CompositeMode.SCREEN.blend(blue, floatArrayOf(0.25f, 2 / 3f, 1f)), 1e-5f)
    }

    @Test
    fun `the render's shader picks modes by this order, the inspector shows these names in Photoshop's groups`() {
        assertEquals(
            listOf("NORMAL", "DARKEN", "MULTIPLY", "LIGHTEN", "SCREEN", "ADD", "OVERLAY", "SOFT_LIGHT", "DIFFERENCE", "COLOR_DODGE",
                "COLOR_BURN", "HARD_LIGHT", "EXCLUSION", "HUE", "SATURATION", "COLOR", "LUMINOSITY"),
            CompositeMode.entries.map { it.name },
        )
        assertEquals(
            listOf("正常", "变暗", "正片叠底", "变亮", "滤色", "相加", "叠加", "柔光", "差值", "颜色减淡", "颜色加深", "强光", "排除", "色相", "饱和度", "颜色", "明度"),
            CompositeMode.entries.map { it.label },
        )
        assertEquals("every mode once in the menu", CompositeMode.entries.toSet(), CompositeMode.GROUPS.flatten().toSet())
        assertEquals(CompositeMode.entries.size, CompositeMode.GROUPS.flatten().size)
        assertEquals(listOf("正常", "变暗 正片叠底 颜色加深", "变亮 滤色 颜色减淡 相加", "叠加 柔光 强光", "差值 排除", "色相 饱和度 颜色 明度"),
            CompositeMode.GROUPS.map { g -> g.joinToString(" ") { it.label } })
    }
}
