package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Shot Match against a still: the clip's black, middle and white points onto the still's. */
class ShotMatchUseCaseTest {

    private val engine = BuildGradeLutUseCase()
    private val match = ShotMatchUseCase(engine)

    private fun argb(r: Float, g: Float, b: Float): Int {
        fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** A colourful test card: brightness down the frame, hue across it, a slightly flat camera. */
    private val source = FramePixels(160, 90, IntArray(160 * 90) { i ->
        val x = (i % 160) / 159f
        val y = (i / 160) / 89f
        val v = 0.08f + 0.8f * y
        argb(v * (0.85f + 0.15f * sin(6.28f * x)), v * (0.85f + 0.15f * sin(6.28f * x + 2.1f)), v * (0.85f + 0.15f * sin(6.28f * x + 4.2f)))
    })

    private fun graded(frame: FramePixels, grade: ColorGrade): FramePixels {
        val out = FloatArray(3)
        return FramePixels(frame.width, frame.height, IntArray(frame.pixels.size) { i ->
            val p = frame.pixels[i]
            engine.apply(grade, ((p shr 16) and 0xFF) / 255f, ((p shr 8) and 0xFF) / 255f, (p and 0xFF) / 255f, out)
            BuildGradeLutUseCase.argb(out)
        })
    }

    private fun meanError(a: FramePixels, b: FramePixels): Float {
        var sum = 0.0
        for (i in a.pixels.indices) for (shift in intArrayOf(16, 8, 0)) {
            sum += abs(((a.pixels[i] shr shift) and 0xFF) - ((b.pixels[i] shr shift) and 0xFF)) / 255.0
        }
        return (sum / (a.pixels.size * 3)).toFloat()
    }

    @Test
    fun `a shot graded another way is matched back to it`() {
        // The still: this shot graded warm, with crushed blacks and brighter whites.
        val look = ColorGrade(
            lift = WheelValue(0.1f, -0.05f, -0.2f),
            gamma = WheelValue(0.05f, 0.1f, 0.15f),
            gain = WheelValue(0.15f, 0.05f, 0.25f),
        )
        val still = graded(source, look)
        val result = match.match(source, still, ColorGrade.NEUTRAL)
        val matched = graded(source, result.grade)
        val want = ShotMatchUseCase.percentiles(still)!!
        val got = ShotMatchUseCase.percentiles(matched)!!
        for (c in 0 until 3) for (k in 0 until 3) assertEquals("channel $c point $k", want[c][k], got[c][k], 0.012f)
        assertTrue("pixel error ${meanError(matched, still)}", meanError(matched, still) < 0.02f)
        assertEquals("已匹配到静帧：暗部、中间调、亮部按通道对齐", result.message)
    }

    @Test
    fun `the rest of the node stays and is matched through`() {
        val still = graded(source, ColorGrade(gain = WheelValue(-0.1f, 0f, 0.2f), gamma = WheelValue(0f, 0f, 0.2f)))
        val dressed = ColorGrade(saturation = 1.3f, contrast = 1.1f, offset = WheelValue(0f, 0f, 0.1f))
        val result = match.match(source, still, dressed)
        assertEquals(1.3f to 1.1f, result.grade.saturation to result.grade.contrast)
        assertEquals(WheelValue.NEUTRAL, result.grade.offset)
        val got = ShotMatchUseCase.percentiles(graded(source, result.grade))!!
        val want = ShotMatchUseCase.percentiles(still)!!
        for (c in 0 until 3) for (k in 0 until 3) assertEquals("channel $c point $k", want[c][k], got[c][k], 0.015f)
    }

    @Test
    fun `a strong cast is matched with the white balance's help`() {
        // The still: the card as shot. The clip: the same card with lifted blacks, dim whites and a warm cast.
        val cast = FramePixels(source.width, source.height, IntArray(source.pixels.size) { i ->
            val p = source.pixels[i]
            fun ch(shift: Int, lift: Float, scale: Float) = lift + scale * ((p shr shift) and 0xFF) / 255f
            argb(ch(16, 0.15f, 0.65f), ch(8, 0.11f, 0.6f), ch(0, 0.07f, 0.5f))
        })
        val result = match.match(cast, source, ColorGrade.NEUTRAL)
        assertTrue("the white balance helps: ${result.grade.temperature}", result.grade.temperature < -5f)
        val got = ShotMatchUseCase.percentiles(graded(cast, result.grade))!!
        val want = ShotMatchUseCase.percentiles(source)!!
        for (c in 0 until 3) for (k in 0 until 3) assertEquals("channel $c point $k", want[c][k], got[c][k], 0.02f)
        assertTrue("pixel error ${meanError(graded(cast, result.grade), source)}", meanError(graded(cast, result.grade), source) < 0.025f)
    }

    @Test
    fun `flat pictures cannot be matched`() {
        val flat = FramePixels(4, 4, IntArray(16) { argb(0.4f, 0.4f, 0.4f) })
        assertEquals("静帧太平或几乎是单色，无法匹配", (runCatching { match.match(source, flat, ColorGrade.NEUTRAL) }.exceptionOrNull() as EditRejected).message)
        assertEquals("画面太平或几乎是单色，无法匹配", (runCatching { match.match(flat, source, ColorGrade.NEUTRAL) }.exceptionOrNull() as EditRejected).message)
    }

    @Test
    fun `one channel's three points land where they are aimed`() {
        val (l, g, a) = ShotMatchUseCase.channel(0.1f, 0.4f, 0.8f, 0.02f, 0.5f, 0.95f)
        fun map(c: Float) = ((c + l * (1f - c)) * g).pow(1f / (1f + a))
        assertEquals(0.02f, map(0.1f), 0.003f)
        assertEquals(0.5f, map(0.4f), 0.003f)
        assertEquals(0.95f, map(0.8f), 0.003f)
    }
}
