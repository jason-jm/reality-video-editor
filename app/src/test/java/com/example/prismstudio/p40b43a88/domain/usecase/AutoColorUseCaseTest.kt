package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Auto Color: black and white points per channel onto node 1's Lift and Gain. */
class AutoColorUseCaseTest {

    private val auto = AutoColorUseCase()
    private val engine = BuildGradeLutUseCase()

    private fun argb(r: Float, g: Float, b: Float): Int {
        fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** A horizontal ramp of [steps] tones: channel = [channel](t), t = 0…1. */
    private fun ramp(steps: Int = 256, channel: (Int, Float) -> Float) =
        FramePixels(steps, 1, IntArray(steps) { i -> val t = i / (steps - 1f); argb(channel(0, t), channel(1, t), channel(2, t)) })

    private fun graded(grade: ColorGrade, argb: Int): FloatArray {
        val out = FloatArray(3)
        engine.apply(grade, ((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, out)
        return out
    }

    @Test
    fun `a dull warm picture opens to black and white, its cast removed`() {
        // Tones 0.15…0.75, red raised and blue lowered by 0.06.
        val frame = ramp { c, t -> 0.15f + 0.6f * t + floatArrayOf(0.06f, 0f, -0.06f)[c] }
        val result = auto.analyse(frame)
        val grade = result.applyTo(ColorGrade.NEUTRAL)
        val darkest = graded(grade, frame.pixels.first())
        val brightest = graded(grade, frame.pixels.last())
        darkest.forEach { assertEquals("black", 0f, it, 0.02f) }
        brightest.forEach { assertEquals("white", 1f, it, 0.02f) }
        val mid = graded(grade, frame.pixels[128])
        assertTrue("neutral mid-tones ${mid.toList()}", mid.max() - mid.min() < 0.03f)
        assertTrue(result.message, result.message.startsWith("已自动调色：黑位 16 → 0，白位 76 → 100"))
        assertTrue(result.message.endsWith("，并校正了偏色"))
    }

    @Test
    fun `other settings stay, offset goes back to neutral`() {
        val frame = ramp { _, t -> 0.1f + 0.8f * t }
        val dressed = ColorGrade(offset = WheelValue(0.1f, 0f, 0.2f), saturation = 1.3f, contrast = 1.2f, temperature = 30f)
        val after = auto.analyse(frame).applyTo(dressed)
        assertEquals(WheelValue.NEUTRAL, after.offset)
        assertEquals("a neutral picture needs no white balance", 0f, after.temperature, 0f)
        assertEquals(1.3f to 1.2f, after.saturation to after.contrast)
    }

    @Test
    fun `a balanced full-range picture is left nearly as it was`() {
        val result = auto.analyse(ramp { _, t -> t })
        assertEquals(0f, result.lift.master, 0.01f)
        assertEquals(0f, result.gain.master, 0.01f)
        assertTrue(hypot(result.gain.x, result.gain.y) < 0.01f)
        assertFalse(result.message.contains("偏色"))
    }

    @Test
    fun `a strong cast beyond the gain puck is finished by the white balance`() {
        // Warm light: whites at 80 / 72 / 58 %, blacks at 15 / 11 / 7 % — blue needs ~40 % more than red.
        val frame = ramp { c, t -> floatArrayOf(0.15f, 0.11f, 0.07f)[c] + floatArrayOf(0.65f, 0.61f, 0.51f)[c] * t }
        val result = auto.analyse(frame)
        assertEquals("the puck alone cannot", WheelValue.MAX_BALANCE, hypot(result.gain.x, result.gain.y), 1e-4f)
        assertTrue("cooler: ${result.temperature}", result.temperature < -10f)
        val grade = result.applyTo(ColorGrade.NEUTRAL)
        graded(grade, frame.pixels.last()).forEach { assertEquals("white", 1f, it, 0.03f) }
        graded(grade, frame.pixels.first()).forEach { assertEquals("black", 0f, it, 0.03f) }
        val mid = graded(grade, frame.pixels[128])
        assertTrue("neutral mid-tones ${mid.toList()}", mid.max() - mid.min() < 0.04f)
        assertTrue(result.message, result.message.endsWith("，并校正了偏色"))
    }

    @Test
    fun `a heavy cast is corrected as far as it goes, a flat picture is refused`() {
        // Blue barely moves: it would need about 5× more than the others.
        val starved = auto.analyse(ramp { c, t -> if (c == 2) 0.05f + 0.12f * t else 0.05f + 0.9f * t })
        assertEquals(WheelValue.MAX_BALANCE, hypot(starved.gain.x, starved.gain.y), 1e-4f)
        assertEquals("the white balance at its end", -100f, starved.temperature, 0f)
        assertTrue(starved.message, starved.message.endsWith("，偏色较重，已尽量校正"))
        val flat = runCatching { auto.analyse(FramePixels(4, 4, IntArray(16) { argb(0.5f, 0.5f, 0.5f) })) }.exceptionOrNull()
        assertTrue(flat is EditRejected)
        assertEquals("画面太平或几乎是单色，无法自动调色", flat?.message)
    }

    @Test
    fun `the puck solve inverts the wheel's push`() {
        for ((x, y) in listOf(0.3f to 0f, -0.2f to 0.25f, 0.1f to -0.4f, 0f to 0f)) {
            val push = BuildGradeLutUseCase.wheelDelta(WheelValue(x, y, 0f))
            val puck = AutoColorUseCase.puck(push)
            assertEquals(x, puck[0], 1e-4f)
            assertEquals(y, puck[1], 1e-4f)
        }
        assertTrue(abs(AutoColorUseCase.wheel(3f, floatArrayOf(0f, 0f)).master - 1f) < 1e-6f)
    }
}
