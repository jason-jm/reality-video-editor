package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's keyer: what keys out of a green or blue screen, how its edge softens, and how the spill loses its tint. */
class ChromaKeyTest {

    private fun rgb(hex: Int) = Triple(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f)

    private fun keyed(hex: Int, key: ChromaKey = ChromaKey()): FloatArray {
        val (r, g, b) = rgb(hex)
        return ChromaKeyKernel.key(r, g, b, ChromaKeyKernel.uniforms(key))
    }

    @Test
    fun `the screen keys out, a shadow on it too, while skin, white and grey stay whole and untinted`() {
        assertEquals("the screen itself", 0f, keyed(ChromaKey.GREEN)[3], 0f)
        assertEquals("the screen in shadow (its chroma 0.11 away)", 0f, keyed(0x007A2C)[3], 0f)
        for (subject in listOf(0xE0AC8F, 0xFFFFFF, 0x808080, 0xD02121)) {
            val out = keyed(subject)
            assertEquals("${"%06X".format(subject)} stays", 1f, out[3], 0f)
            val (r, g, b) = rgb(subject)
            assertEquals("and keeps its colour", listOf(r, g, b), out.take(3))
        }
        // A blue screen keys blue and keeps green.
        val blue = ChromaKey(color = ChromaKey.BLUE)
        assertEquals(0f, keyed(ChromaKey.BLUE, blue)[3], 0f)
        assertEquals(1f, keyed(ChromaKey.GREEN, blue)[3], 0f)
    }

    @Test
    fun `past the similarity the picture comes back over the smoothness, and the spill near the key loses its tint`() {
        val u = ChromaKeyKernel.uniforms(ChromaKey())
        assertEquals(0.2f, u[2], 1e-6f)
        assertEquals(0.075f, u[3], 1e-6f)
        assertEquals(0.12f, u[4], 1e-6f)
        // A greenish edge (half screen, half skin): its distance from the key decides it.
        val (r, g, b) = rgb(0x70AF68)
        val (cb, cr) = ChromaKeyKernel.cbcr(r, g, b)
        val d = kotlin.math.hypot(cb - u[0], cr - u[1]) - u[2]
        assertTrue("it lies within the soft edge", d > 0f && d < u[3])
        val out = ChromaKeyKernel.key(r, g, b, u)
        assertEquals(Math.pow((d / u[3]).toDouble(), 1.5).toFloat(), out[3], 1e-5f)
        // The spill: pulled towards its own luma by 1 − (d / spill)^1.5.
        val y = ChromaKeyKernel.luma(r, g, b)
        val keep = Math.pow((d / u[4]).toDouble(), 1.5).toFloat()
        assertEquals(y + (g - y) * keep, out[1], 1e-5f)
        assertTrue("less green than it was", out[1] < g)
        assertEquals("luma kept", y, ChromaKeyKernel.luma(out[0], out[1], out[2]), 1e-5f)
        // Without smoothness the edge is hard, without spill the colour is untouched.
        val hard = ChromaKeyKernel.uniforms(ChromaKey(smoothness = 0f, spill = 0f))
        assertEquals(1f, ChromaKeyKernel.key(r, g, b, hard)[3], 0f)
        assertEquals(listOf(r, g, b), ChromaKeyKernel.key(r, g, b, hard).take(3))
    }

    @Test
    fun `the matte finesse clears what is nearly keyed out and makes what is nearly kept solid`() {
        val u = ChromaKeyKernel.uniforms(ChromaKey(cleanBlack = 40f, cleanWhite = 20f, blur = 50f))
        assertEquals(listOf(0.2f, 0.1f, 3f), listOf(u[5], u[6], u[7]))
        assertEquals("under the clean black: clear", 0f, ChromaKeyKernel.finesse(0.15f, u), 0f)
        assertEquals("over 1 − clean white: solid", 1f, ChromaKeyKernel.finesse(0.95f, u), 0f)
        assertEquals("between: stretched", (0.5f - 0.2f) / 0.7f, ChromaKeyKernel.finesse(0.5f, u), 1e-6f)
        assertEquals("none: untouched", 0.37f, ChromaKeyKernel.finesse(0.37f, ChromaKeyKernel.uniforms(ChromaKey())), 0f)
        // A greenish edge the plain key leaves 0.3 visible: the clean black takes it out.
        val (r, g, b) = rgb(0x7BAE6B)
        val plain = ChromaKeyKernel.key(r, g, b, ChromaKeyKernel.uniforms(ChromaKey()))[3]
        val cleaned = ChromaKeyKernel.key(r, g, b, ChromaKeyKernel.uniforms(ChromaKey(cleanBlack = 100f)))[3]
        assertTrue(plain > 0.3f && cleaned == 0f)
        assertEquals(listOf("去黑", "去白", "遮罩模糊"), KeyControl.entries.filter { it.finesse }.map { it.label })
        assertEquals(55f, KeyControl.BLUR.read(KeyControl.BLUR.write(ChromaKey(), 55f)), 0f)
    }

    @Test
    fun `the inspector reads and writes the amounts within 0 to 100 and names the colour`() {
        val key = KeyControl.SPILL.write(KeyControl.SIMILARITY.write(ChromaKey(), 140f), -5f)
        assertEquals(100f to 0f, key.similarity to key.spill)
        assertEquals(30f, KeyControl.SMOOTHNESS.read(key), 0f)
        assertEquals("绿幕", ChromaKey().colorLabel)
        assertEquals(KeyScreen.BLUE, ChromaKey(color = ChromaKey.BLUE).preset)
        assertNull(ChromaKey(color = 0x3A9D5C).preset)
        assertEquals("#3A9D5C", ChromaKey(color = 0x3A9D5C).colorLabel)
    }
}
