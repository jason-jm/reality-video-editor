package com.example.prismstudio.p40b43a88.data.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleCue
import com.example.prismstudio.p40b43a88.domain.model.TitleMotion
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import kotlin.math.roundToInt

/**
 * Draws the plan's title cues over the composed picture. The bitmap is only redrawn (and only
 * re-uploaded to the GPU) when the set of visible titles or their animation phase changes.
 *
 * Canvas draws premultiplied colour, but Media3's overlay shader blends straight alpha
 * (rgb · a + video · (1 − a)), so Media3 is handed a non-premultiplied copy: given the premultiplied
 * bitmap itself, every half-transparent pixel — a fade, a light box, a glyph's edge — came out at
 * colour · a² (a white band at 55 % as 30 % grey).
 */
@UnstableApi
class TitleOverlay(
    private val cues: List<TitleCue>,
    private val fps: Int,
    /** Live text, transform and keyframes, so title edits show without rebuilding the player. */
    private val registry: LiveLookRegistry? = null,
) : BitmapOverlay() {

    /** What the titles are drawn into (premultiplied, as Canvas needs). */
    private var bitmap: Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    /** The same picture in straight alpha, for Media3. */
    private var straight: Bitmap = straightBitmap(16, 16)
    private var pixels = IntArray(16 * 16)
    private var lastKey: List<Any>? = null
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        if (bitmap.width != videoSize.width || bitmap.height != videoSize.height) {
            val w = videoSize.width.coerceAtLeast(16)
            val h = videoSize.height.coerceAtLeast(16)
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            straight = straightBitmap(w, h)
            pixels = IntArray(w * h)
            lastKey = null
        }
    }

    private fun straightBitmap(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { isPremultiplied = false }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val frame = frameAtUs(presentationTimeUs, fps)
        val active = cues.filter { frame >= it.startFrame && frame < it.endFrame }.map(::live)
        val key = active.map { cue ->
            listOf(cue.clipId, motion(cue, frame), scroll(cue, frame), cue.style, cue.transformAt(frame), cue.subtitle ?: SubtitleStyle.DEFAULT, cue.fadeAt(frame))
        }
        if (key == lastKey) return straight
        lastKey = key
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        active.forEach { draw(canvas, it, frame) }
        // getPixels un-premultiplies; a non-premultiplied bitmap keeps what setPixels gives it.
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        straight.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return straight
    }

    private fun live(cue: TitleCue): TitleCue {
        val look = registry?.titleLook(cue.clipId) ?: return cue
        return cue.copy(
            style = look.style, transform = look.transform, keyframes = look.keyframes, subtitle = look.subtitle ?: cue.subtitle,
            fadeInFrames = look.fadeInFrames, fadeOutFrames = look.fadeOutFrames, fadeInCurve = look.fadeInCurve, fadeOutCurve = look.fadeOutCurve,
        )
    }

    /** The title's entrance or exit at plan [frame], over the whole clip; subtitles cut in and out with the dialogue. */
    private fun motion(cue: TitleCue, frame: Int): TitleMotion =
        if (cue.style.template == TitleTemplate.SUBTITLE) {
            TitleMotion.STILL
        } else {
            cue.style.animation.motion(frame - cue.clipStartFrame, cue.clipEndFrame - cue.clipStartFrame, fps, cue.style.characters())
        }

    /** How far (0…1) a roll or credits title has scrolled at plan [frame]; 0 for the other templates. */
    private fun scroll(cue: TitleCue, frame: Int): Float = when (cue.style.template) {
        TitleTemplate.ROLL, TitleTemplate.CREDITS ->
            (frame - cue.clipStartFrame).toFloat() / (cue.clipEndFrame - cue.clipStartFrame).coerceAtLeast(1)
        else -> 0f
    }

    private fun draw(canvas: Canvas, cue: TitleCue, frame: Int) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val unit = minOf(w, h) / 1080f
        val t = cue.transformAt(frame)
        val motion = motion(cue, frame)
        // Its entrance or exit, its opacity and its fade handles.
        val alpha = (motion.alpha * t.opacityPercent / 100f * cue.fadeAt(frame)).coerceIn(0f, 1f)
        val lines = cue.style.lines()
        canvas.save()
        canvas.translate(w / 2f + t.positionX / 1920f * w, h / 2f - t.positionY / 1080f * h + motion.dropPx * unit)
        canvas.rotate(t.rotationDegrees.toFloat())
        canvas.scale(t.scalePercent / 100f, t.scalePercent / 100f)
        if (cue.style.template == TitleTemplate.SUBTITLE) {
            drawSubtitle(canvas, cue, lines, alpha, unit, t.scalePercent)
        } else {
            drawTitle(canvas, cue.style, lines, motion, alpha, unit, scroll(cue, frame))
        }
        canvas.restore()
    }

    /** A subtitle in its track's style: size, colour, weight, outline, box and margin. */
    private fun drawSubtitle(canvas: Canvas, cue: TitleCue, lines: List<String>, alpha: Float, unit: Float, scalePercent: Int) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val style = cue.subtitle ?: SubtitleStyle.DEFAULT
        text.textSize = style.sizePx * unit
        text.color = style.color.argb
        text.alpha = (alpha * 255).roundToInt()
        text.setShadowLayer(6f * unit, 0f, 2f * unit, Color.argb((alpha * 150).roundToInt(), 0, 0, 0))
        val lineHeight = text.textSize * 1.25f
        text.typeface = if (style.bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textAlign = Paint.Align.CENTER
        val scale = (scalePercent / 100f).coerceAtLeast(0.1f)
        val wrapped = wrap(lines, w * SUBTITLE_WIDTH / scale)
        val padX = 18f * unit
        val padY = 8f * unit
        val bottom = h / 2f - h * style.marginPercent / 100f
        band.color = Color.argb((alpha * style.boxOpacityPercent * 2.55f).roundToInt(), 0, 0, 0)
        outline.strokeWidth = text.textSize * OUTLINE_SHARE
        outline.textSize = text.textSize
        outline.typeface = text.typeface
        outline.textAlign = Paint.Align.CENTER
        outline.color = Color.argb((alpha * 230).roundToInt(), 0, 0, 0)
        outline.clearShadowLayer()
        wrapped.reversed().forEachIndexed { i, line ->
            val baseline = bottom - i * lineHeight
            val half = text.measureText(line) / 2f
            if (style.box && style.boxOpacityPercent > 0) {
                canvas.drawRoundRect(
                    -half - padX, baseline - text.textSize * 0.95f - padY, half + padX, baseline + text.textSize * 0.25f + padY,
                    8f * unit, 8f * unit, band,
                )
            }
            if (style.outline) canvas.drawText(line, 0f, baseline, outline)
            canvas.drawText(line, 0f, baseline, text)
        }
    }

    /**
     * A title in its own look — colour, weight, outline, drop shadow, box — partway through its entrance:
     * zoomed about its own centre (a lower third about its band), with only the typed characters drawn
     * where the whole lines will stand.
     */
    private fun drawTitle(canvas: Canvas, style: TitleStyle, lines: List<String>, motion: TitleMotion, alpha: Float, unit: Float, scroll: Float) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        text.textSize = style.sizePx * unit
        text.typeface = Typeface.create(Typeface.SANS_SERIF, style.weight.value, false)
        text.textAlign = Paint.Align.LEFT
        text.color = style.color.argb
        text.alpha = (alpha * 255).roundToInt()
        // Dark text takes a light outline and box.
        val contrast = if (style.color.dark) 245 else 0
        val shadow = Color.argb((alpha * 150).roundToInt(), 0, 0, 0)
        outline.strokeWidth = text.textSize * TITLE_OUTLINE_SHARE
        outline.textSize = text.textSize
        outline.typeface = text.typeface
        outline.textAlign = Paint.Align.LEFT
        outline.color = Color.argb((alpha * 230).roundToInt(), contrast, contrast, contrast)
        // The shadow falls from the outermost shape: the outline when there is one.
        text.clearShadowLayer()
        outline.clearShadowLayer()
        if (style.shadow) (if (style.outline) outline else text).setShadowLayer(6f * unit, 0f, 2f * unit, shadow)
        band.color = if (style.color.dark) {
            Color.argb((alpha * style.boxOpacityPercent * 2.55f).roundToInt(), 245, 245, 245)
        } else {
            Color.argb((alpha * style.boxOpacityPercent * 2.55f).roundToInt(), 8, 10, 14)
        }
        val boxed = style.box && style.boxOpacityPercent > 0
        val lineHeight = text.textSize * 1.25f
        // Characters typed so far, line by line in reading order.
        var budget = motion.characters
        val typed = lines.map { line -> minOf(line.codePointCount(0, line.length), budget).also { budget -= it } }
        when (style.template) {
            TitleTemplate.LOWER_THIRD -> {
                val left = -w / 2f + w * 0.07f
                val baseline = h / 2f - h * 0.16f
                val width = lines.maxOf { text.measureText(it) } + 48f * unit
                val bandTop = baseline - (lineHeight * lines.size + 28f * unit) + 14f * unit
                val bandBottom = baseline + 14f * unit
                canvas.scale(motion.scale, motion.scale, left - 24f * unit, (bandTop + bandBottom) / 2f)
                if (boxed) canvas.drawRect(left - 24f * unit, bandTop, left - 24f * unit + width, bandBottom, band)
                band.color = Color.argb((alpha * 255).roundToInt(), 242, 179, 61)
                canvas.drawRect(left - 24f * unit, bandTop, left - 16f * unit, bandBottom, band)
                lines.forEachIndexed { i, line -> drawLine(canvas, line, typed[i], left, baseline - (lines.size - 1 - i) * lineHeight, style.outline) }
            }
            else -> {
                // Centred lines: still in the middle, or rolling up through the picture over the clip.
                val rolling = style.template == TitleTemplate.ROLL || style.template == TitleTemplate.CREDITS
                val first = if (rolling) h / 2f - scroll * (h + lineHeight * lines.size) + text.textSize
                else -lineHeight * (lines.size - 1) / 2f + text.textSize * 0.35f
                val top = first - text.textSize * 0.95f
                val bottom = first + (lines.size - 1) * lineHeight + text.textSize * 0.25f
                canvas.scale(motion.scale, motion.scale, 0f, (top + bottom) / 2f)
                if (boxed) {
                    val half = lines.maxOf { text.measureText(it) } / 2f
                    val padX = text.textSize * BOX_PAD_X
                    val padY = text.textSize * BOX_PAD_Y
                    val radius = text.textSize * BOX_RADIUS
                    canvas.drawRoundRect(-half - padX, top - padY, half + padX, bottom + padY, radius, radius, band)
                }
                lines.forEachIndexed { i, line -> drawLine(canvas, line, typed[i], -text.measureText(line) / 2f, first + i * lineHeight, style.outline) }
            }
        }
    }

    /** The first [typed] characters of [line] where the whole line starts at [left]: its outline first, when it has one. */
    private fun drawLine(canvas: Canvas, line: String, typed: Int, left: Float, baseline: Float, outlined: Boolean) {
        if (typed <= 0) return
        val shown = if (typed >= line.codePointCount(0, line.length)) line else line.substring(0, line.offsetByCodePoints(0, typed))
        if (outlined) canvas.drawText(shown, left, baseline, outline)
        canvas.drawText(shown, left, baseline, text)
    }

    /**
     * [lines] broken to fit [maxWidth] — at the last space for Latin text, between characters otherwise —
     * into as few lines as greedy breaking needs, then evened out so no line is left with a single word.
     */
    private fun wrap(lines: List<String>, maxWidth: Float): List<String> = lines.flatMap { line ->
        val greedy = breakLine(line, maxWidth)
        if (greedy.size < 2) return@flatMap greedy
        // The narrowest width that still needs no more lines than the greedy break.
        val even = text.measureText(line) / greedy.size
        (0..BALANCE_STEPS).asSequence()
            .map { step -> even + (maxWidth - even) * step / BALANCE_STEPS }
            .map { breakLine(line, it) }
            .first { it.size <= greedy.size }
    }

    private fun breakLine(line: String, maxWidth: Float): List<String> {
        if (text.measureText(line) <= maxWidth) return listOf(line)
        val out = mutableListOf<String>()
        var start = 0
        while (start < line.length) {
            var end = start + 1
            while (end < line.length && text.measureText(line, start, end + 1) <= maxWidth) end++
            if (end < line.length) {
                val space = line.lastIndexOf(' ', end - 1)
                if (space > start) end = space + 1
            }
            out += line.substring(start, end).trim()
            start = end
        }
        return out.filter { it.isNotEmpty() }
    }

    private companion object {
        /** Widest a subtitle line may get, as a share of the frame width. */
        const val SUBTITLE_WIDTH = 0.86f
        /** A subtitle outline's width, as a share of the text size. */
        const val OUTLINE_SHARE = 0.12f
        /** A title outline's width, as a share of the text size (titles are larger than subtitles). */
        const val TITLE_OUTLINE_SHARE = 0.08f
        /** A title box's padding beside and above / below the text, and its corner radius, as shares of the text size. */
        const val BOX_PAD_X = 0.45f
        const val BOX_PAD_Y = 0.22f
        const val BOX_RADIUS = 0.2f
        /** Widths tried between an even split and the full width when balancing a wrapped subtitle. */
        const val BALANCE_STEPS = 12
    }
}

/**
 * The frame (at [fps]) a presentation time stands for. Frame times are whole microseconds, rounded either
 * way (frame 1 at 30 fps may come as 33 333 µs), so this rounds to the nearest frame: truncating made every
 * third frame repeat its predecessor — fades stepped and rolling credits juddered.
 */
internal fun frameAtUs(presentationTimeUs: Long, fps: Int): Int = Math.floorDiv(presentationTimeUs * fps + 500_000L, 1_000_000L).toInt()
