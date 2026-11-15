package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Resolve's fade handles: how much of a clip shows at each moment, and which clips have them. */
class ClipFadesTest {

    @Test
    fun `a fade in climbs from nothing at the clip's start, a fade out falls to nothing at its end`() {
        // 300 frames, a second (30 frames) of fade at either end.
        assertEquals(0f, ClipFades.factor(0f, 300f, 30f, 30f), 0f)
        assertEquals(0.5f, ClipFades.factor(15f, 300f, 30f, 30f), 1e-6f)
        assertEquals(1f, ClipFades.factor(30f, 300f, 30f, 30f), 0f)
        assertEquals(1f, ClipFades.factor(150f, 300f, 30f, 30f), 0f)
        assertEquals(0.5f, ClipFades.factor(285f, 300f, 30f, 30f), 1e-6f)
        // The last frame shows at its own time, a frame before the end: a thirtieth is left.
        assertEquals(1f / 30, ClipFades.factor(299f, 300f, 30f, 30f), 1e-6f)
        assertEquals(0f, ClipFades.factor(300f, 300f, 30f, 30f), 0f)
    }

    @Test
    fun `fades that meet in the middle peak there, and outside the clip a fade holds at nothing`() {
        assertEquals(1f, ClipFades.factor(30f, 60f, 30f, 30f), 0f)
        assertEquals(0.5f, ClipFades.factor(45f, 60f, 30f, 30f), 1e-6f)
        // The frames a transition shows before the cut or after it.
        assertEquals(0f, ClipFades.factor(-5f, 60f, 30f, 0f), 0f)
        assertEquals(0f, ClipFades.factor(65f, 60f, 0f, 30f), 0f)
        // A clip without fades shows whole everywhere, those frames too.
        assertEquals(1f, ClipFades.factor(-5f, 60f, 0f, 0f), 0f)
        assertEquals(1f, ClipFades.factor(65f, 60f, 0f, 0f), 0f)
    }

    @Test
    fun `a clip's fades are counted in seconds at the timeline's rate, at most half the clip and five seconds`() {
        val clip = clipOf("V", 100, 90).copy(fadeInSeconds = 1f, fadeOutSeconds = 0.5f)
        assertEquals(0.5f, ClipFades.factor(clip, 15f, 30), 1e-6f)
        assertEquals(1f, ClipFades.factor(clip, 60f, 30), 0f)
        assertEquals(0.5f, ClipFades.factor(clip, 82.5f, 30), 1e-6f)
        assertEquals(1.5f, ClipFades.max(clip, 30), 0f)
        assertEquals(5f, ClipFades.max(clipOf("Long", 0, 30 * 60), 30), 0f)
    }

    @Test
    fun `a fade curve bends a fade - straight at nought, bowed up above it, down below - its dot on the curve's middle`() {
        for (t in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) assertEquals(t, ClipFades.shape(t, 0f), 1e-6f)
        // All the way up the control point is (0, 1): x = s², y = 2s − s², so y = 2√x − x; all the way down, its mirror.
        assertEquals(0.75f, ClipFades.shape(0.25f, 1f), 1e-5f)
        assertEquals(1f - (2 * sqrt(0.75f) - 0.75f), ClipFades.shape(0.25f, -1f), 1e-5f)
        for (c in listOf(-1f, -0.6f, -0.2f, 0.3f, 0.8f, 1f)) {
            var last = 0f
            for (i in 0..100) {
                val t = i / 100f
                val y = ClipFades.shape(t, c)
                assertEquals("the curves bent either way mirror each other", 1f - ClipFades.shape(1f - t, -c), y, 1e-5f)
                assertTrue("it only climbs", y >= last - 1e-6f)
                last = y
            }
            assertEquals(0f, ClipFades.shape(0f, c), 0f)
            assertEquals(1f, ClipFades.shape(1f, c), 0f)
            val (along, up) = ClipFades.dot(c)
            assertEquals("the dot lies on the curve", up, ClipFades.shape(along, c), 1e-5f)
        }
        assertEquals(0.5f to 0.5f, ClipFades.dot(0f))
        assertEquals(0.25f to 0.75f, ClipFades.dot(1f))
        // Halfway through, bowed up has come more than half the way (about the level of an equal-power fade at +0.5).
        assertTrue(ClipFades.shape(0.5f, 0.5f) in 0.72f..0.75f)
        assertTrue(ClipFades.shape(0.5f, -0.5f) in 0.25f..0.28f)
        assertEquals(listOf("线性", "上弯 40%", "下弯 35%", "上弯 100%"), listOf(0f, 0.4f, -0.35f, 3f).map(ClipFades::curveLabel))
    }

    @Test
    fun `a clip's curves bend its fades - the fade in from its start, the fade out back from its end`() {
        val clip = clipOf("V", 0, 300).copy(fadeInSeconds = 1f, fadeOutSeconds = 1f, fadeInCurve = 1f, fadeOutCurve = -1f)
        assertEquals("a quarter in, bowed up", 0.75f, ClipFades.factor(clip, 7.5f, 30), 1e-5f)
        assertEquals("a quarter from the end, bowed down", 1f - (2 * sqrt(0.75f) - 0.75f), ClipFades.factor(clip, 292.5f, 30), 1e-5f)
        assertEquals(1f, ClipFades.factor(clip, 150f, 30), 0f)
        assertEquals(0f, ClipFades.factor(clip, 0f, 30), 0f)
        assertEquals(0f, ClipFades.factor(clip, 300f, 30), 0f)
        assertEquals("bowed up holds a fade out up", ClipFades.factor(clip.copy(fadeOutCurve = 1f), 7.5f, 30), ClipFades.factor(clip.copy(fadeOutCurve = 1f), 292.5f, 30), 1e-5f)
    }

    @Test
    fun `pictures, titles and sounds have fade handles - subtitles, which cut in with the dialogue, do not`() {
        assertTrue(ClipFades.takes(clipOf("V", 0, 90)))
        assertTrue(ClipFades.takes(clipOf("A", 0, 90, track = Timeline.A1)))
        assertTrue(ClipFades.takes(titleClipOf("T", 0, 90)))
        val subtitle = titleClipOf("S", 0, 90).let { it.copy(title = it.title!!.copy(template = TitleTemplate.SUBTITLE)) }
        assertFalse(ClipFades.takes(subtitle))
        assertFalse(ClipFades.fades(subtitle.copy(fadeInSeconds = 1f)))
        assertFalse(ClipFades.fades(clipOf("V", 0, 90)))
        assertTrue(ClipFades.fades(clipOf("V", 0, 90).copy(fadeOutSeconds = 0.2f)))
    }
}
