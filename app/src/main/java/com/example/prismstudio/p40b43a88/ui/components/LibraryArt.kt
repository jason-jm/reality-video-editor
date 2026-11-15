package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.Canvas
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors

/**
 * The illustration a library tile shows for a transition, a title template or an effect — the design's glyphs: what
 * the item does, drawn on the two sample pictures (a night street, A, and a dusk skyline, B).
 */
enum class LibraryArt {
    DISSOLVE, DIP_BLACK, DIP_WHITE, DIP_COLOR, PUSH, WIPE, ZOOM, IRIS, CLOCK, BARN, BLUR_DISSOLVE,
    TITLE_CENTER, LOWER_THIRD, ROLL, CREDITS, SUBTITLE,
    BLUR, SHARPEN, VIGNETTE, GRAIN, MIRROR, MOSAIC, CHROMA, GLOW,
}

/** [art] filling its box. */
@Composable
fun LibraryArtView(art: LibraryArt, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) { drawLibraryArt(art, measurer) }
}

private val NIGHT = PosterColors.LIBRARY_NIGHT
private val DUSK = PosterColors.LIBRARY_DUSK

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun DrawScope.drawLibraryArt(art: LibraryArt, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val white = PrismColors.TitleWhite
    drawRect(PrismColors.PictureBlack)
    when (art) {
        LibraryArt.DISSOLVE -> {
            picture(NIGHT, 1)
            picture(DUSK, 2, alpha = 0.5f)
        }
        LibraryArt.DIP_BLACK, LibraryArt.DIP_WHITE, LibraryArt.DIP_COLOR -> drawRect(
            Brush.horizontalGradient(
                listOf(
                    PrismColors.argb(DUSK.middle),
                    when (art) {
                        LibraryArt.DIP_BLACK -> PrismColors.PictureBlack
                        LibraryArt.DIP_WHITE -> white
                        else -> PrismColors.title(Transition.DIP_COLOR)
                    },
                    PrismColors.argb(NIGHT.middle),
                ),
            ),
        )
        LibraryArt.PUSH -> {
            clipRect(right = w * 0.55f) { picture(NIGHT, 1) }
            clipRect(left = w * 0.55f) { picture(DUSK, 2) }
            drawRect(white.copy(alpha = 0.9f), Offset(w * 0.55f - 1f, 0f), Size(2f, h))
        }
        LibraryArt.WIPE -> {
            clipRect(right = w * 0.5f) { picture(NIGHT, 1) }
            clipRect(left = w * 0.5f) { picture(DUSK, 2) }
            drawRect(white.copy(alpha = 0.6f), Offset(w * 0.5f - 6f, 0f), Size(12f, h))
        }
        LibraryArt.ZOOM -> {
            picture(NIGHT, 3)
            for (i in 1..3) scale(1f + i * 0.12f) { picture(NIGHT, 3, alpha = 0.35f) }
        }
        LibraryArt.IRIS -> {
            picture(NIGHT, 1)
            val circle = Path().apply { addOval(Rect(Offset(w / 2f, h / 2f), h * 0.42f)) }
            clipPath(circle) { picture(DUSK, 2) }
            drawCircle(white.copy(alpha = 0.85f), h * 0.42f, Offset(w / 2f, h / 2f), style = Stroke(2f))
        }
        LibraryArt.CLOCK -> {
            picture(NIGHT, 1)
            val sweep = Path().apply {
                moveTo(w / 2f, h / 2f)
                arcTo(Rect(Offset(w / 2f, h / 2f), w), -90f, 230f, forceMoveTo = false)
                close()
            }
            clipPath(sweep) { picture(DUSK, 2) }
            val hand = Math.toRadians(-90.0 + 230.0)
            drawLine(white.copy(alpha = 0.9f), Offset(w / 2f, h / 2f), Offset(w / 2f + w * kotlin.math.cos(hand).toFloat(), h / 2f + w * kotlin.math.sin(hand).toFloat()), 2f)
            drawLine(white.copy(alpha = 0.9f), Offset(w / 2f, h / 2f), Offset(w / 2f, 0f), 2f)
        }
        LibraryArt.BARN -> {
            picture(NIGHT, 1)
            clipRect(left = w * 0.3f, right = w * 0.7f) { picture(DUSK, 2) }
            drawRect(white.copy(alpha = 0.85f), Offset(w * 0.3f - 1f, 0f), Size(2f, h))
            drawRect(white.copy(alpha = 0.85f), Offset(w * 0.7f - 1f, 0f), Size(2f, h))
        }
        LibraryArt.BLUR_DISSOLVE -> {
            picture(NIGHT, 1)
            softly(DUSK, 2, alpha = 0.12f)
        }
        LibraryArt.TITLE_CENTER -> {
            picture(DUSK, 4)
            centredText(measurer, "标题", h * 0.28f, FontWeight.Bold, h * 0.5f)
        }
        LibraryArt.LOWER_THIRD -> {
            picture(DUSK, 4)
            drawRect(PrismColors.PictureBlack.copy(alpha = 0.6f), Offset(w * 0.08f, h * 0.66f), Size(w * 0.6f, h * 0.18f))
            drawRect(white, Offset(w * 0.11f, h * 0.71f), Size(w * 0.3f, h * 0.04f))
            drawRect(white, Offset(w * 0.11f, h * 0.77f), Size(w * 0.2f, h * 0.03f))
        }
        LibraryArt.ROLL -> for (i in 0 until 5) {
            drawRect(white, Offset(w * 0.3f, h * (0.15f + i * 0.16f)), Size(w * (0.4f - (i % 2) * 0.12f), h * 0.05f))
        }
        LibraryArt.SUBTITLE -> {
            picture(DUSK, 4)
            drawRect(PrismColors.PictureBlack.copy(alpha = 0.6f), Offset(w * 0.22f, h * 0.72f), Size(w * 0.56f, h * 0.16f))
            drawRect(white, Offset(w * 0.28f, h * 0.78f), Size(w * 0.44f, h * 0.04f))
        }
        LibraryArt.CREDITS -> {
            centredText(measurer, "导演", h * 0.12f, FontWeight.SemiBold, h * 0.36f)
            centredText(measurer, "剪辑", h * 0.12f, FontWeight.SemiBold, h * 0.58f)
        }
        LibraryArt.BLUR -> softly(NIGHT, 5, alpha = 0.2f)
        LibraryArt.SHARPEN -> drawIntoCanvas { canvas ->
            // Contrast 1.6 around mid grey.
            val c = 1.6f
            val t = 0.5f * (1f - c) * 255f
            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))) }
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            picture(NIGHT, 6)
            canvas.restore()
        }
        LibraryArt.VIGNETTE -> {
            picture(DUSK, 7)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, PrismColors.PictureBlack.copy(alpha = 0.85f)), Offset(w / 2f, h / 2f), w * 0.6f))
        }
        LibraryArt.GRAIN -> {
            picture(DUSK, 8)
            val r = Seeded(3)
            repeat((w * h * 0.03f).toInt().coerceAtMost(4000)) { drawRect(white.copy(alpha = 0.25f), Offset(r.next() * w, r.next() * h), Size(1f, 1f)) }
        }
        LibraryArt.MIRROR -> {
            clipRect(right = w / 2f) { picture(NIGHT, 9) }
            clipRect(left = w / 2f) { scale(-1f, 1f) { picture(NIGHT, 9) } }
        }
        LibraryArt.MOSAIC -> {
            val r = Seeded(9)
            val cell = maxOf(6f, w / 12f)
            var x = 0f
            while (x < w) {
                var y = 0f
                while (y < h) {
                    drawRect(Color.hsl(300f + r.next() * 60f, 0.6f, 0.2f + r.next() * 0.35f), Offset(x, y), Size(cell - 1f, cell - 1f))
                    y += cell
                }
                x += cell
            }
        }
        LibraryArt.CHROMA -> {
            picture(NIGHT, 10, alpha = 0.6f)
            translate(left = 4f) { picture(DUSK, 10, alpha = 0.6f, blend = BlendMode.Plus) }
        }
        LibraryArt.GLOW -> {
            softly(NIGHT, 11, alpha = 0.22f)
            picture(NIGHT, 11, alpha = 0.6f)
        }
    }
}

/** The picture drawn a few times slightly apart: out of focus. */
private fun DrawScope.softly(colors: PosterColors, seed: Int, alpha: Float) {
    val r = size.height * 0.035f
    for (dx in -2..2) for (dy in -1..1) translate(dx * r / 2f, dy * r) { picture(colors, seed, alpha = alpha) }
}

private fun DrawScope.centredText(measurer: TextMeasurer, text: String, sizePx: Float, weight: FontWeight, centreY: Float) {
    val style = TextStyle(color = PrismColors.TitleWhite, fontSize = (sizePx / density).sp, fontWeight = weight)
    val layout = measurer.measure(text.localized(), style)
    drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, centreY - layout.size.height / 2f))
}

/**
 * A sample picture in [colors]: a sky graded top to horizon, out-of-focus lights, a skyline with lit windows and the
 * street's shadow — the design preview's `drawFrame`, seeded by [seed] so each item keeps its own skyline.
 */
private fun DrawScope.picture(colors: PosterColors, seed: Int, alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver) {
    val w = size.width
    val h = size.height
    val top = PrismColors.argb(colors.top)
    val middle = PrismColors.argb(colors.middle)
    val bottom = PrismColors.argb(colors.bottom)
    drawRect(Brush.verticalGradient(0f to top, 0.58f to middle, 0.62f to bottom, 1f to bottom), alpha = alpha, blendMode = blend)
    val r = Seeded(seed * 7919 + colors.middle.toInt())
    repeat(9) {
        drawCircle(middle, (0.02f + r.next() * 0.07f) * w, Offset(r.next() * w, h * (0.15f + r.next() * 0.45f)), alpha = 0.35f * alpha, blendMode = blend)
    }
    var x = 0f
    while (x < w) {
        val bw = w * (0.04f + r.next() * 0.09f)
        val bh = h * (0.12f + r.next() * 0.33f)
        drawRect(bottom, Offset(x, h * 0.62f - bh), Size(bw, bh + h), alpha = alpha, blendMode = blend)
        repeat(6) {
            drawRect(
                middle,
                Offset(x + r.next() * bw * 0.8f, h * 0.62f - r.next() * bh),
                Size(maxOf(1f, w * 0.006f), maxOf(1f, h * 0.01f)),
                alpha = 0.55f * alpha,
                blendMode = blend,
            )
        }
        x += bw + w * 0.01f
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, PrismColors.PictureBlack.copy(alpha = 0.45f)), startY = h * 0.62f, endY = h),
        Offset(0f, h * 0.62f),
        Size(w, h * 0.38f),
        alpha = alpha,
        blendMode = blend,
    )
}

/** A small deterministic random sequence (the same skyline every time a tile draws). */
private class Seeded(seed: Int) {
    private var state = seed.toLong() xor 0x5DEECE66DL

    fun next(): Float {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (state ushr 24).toInt() / (1 shl 24).toFloat()
    }
}
