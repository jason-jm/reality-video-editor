package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Gallery: stills keep the grade that made them, and grade other clips the same way. */
class GalleryUseCaseTest {

    private val gallery = GalleryUseCase()
    private val warm = ColorGrade(exposure = 0.5f, temperature = 20f, window = PowerWindow(grade = ColorGrade(saturation = 0f)))
    private val clip = clipOf("V", 0, 90).copy(grade = warm)
    private val doc = projectOf(timeline = timelineOf(clip, clipOf("W", 90, 60), clipOf("A", 0, 90, track = Timeline.A1)))

    private fun rejected(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `a still keeps the grade as it stood at its frame, numbered after the newest`() {
        val keys = GradeKeyframeUseCase()
        val keyed = keys.adjust(keys.toggle(warm, 10, 90).grade, 70, 90) { ColorGrade(exposure = -1f) }
        val first = gallery.grab(doc, clip.copy(grade = keyed), 40, "s1", "s1.jpg", 960, 540, "00:00:01:10")
        assertEquals("静帧 1", first.label)
        assertTrue("keyframes resolved", first.grade.keyframes.isEmpty())
        assertEquals("half way between the keyframes", -0.25f, first.grade.exposure, 1e-5f)
        assertEquals("node 2 comes along", warm.window, first.grade.window)
        assertEquals("V", first.clipName)
        val one = gallery.add(doc, first)
        val second = gallery.grab(one, clip, 0, "s2", "s2.jpg", 960, 540, "00:00:00:00")
        assertEquals(2, second.number)
        val two = gallery.add(one, second)
        assertEquals(listOf("s1", "s2"), two.stills.map { it.id })
        assertEquals("a deleted still's number is not reused", 3, gallery.grab(gallery.remove(two, "s1"), clip, 0, "s3", "s3.jpg", 960, 540, "").number)
    }

    @Test
    fun `applying a still grades a picture clip as the still was`() {
        val withStill = gallery.add(doc, gallery.grab(doc, clip, 0, "s1", "s1.jpg", 960, 540, ""))
        val graded = gallery.apply(withStill, "s1", "W")
        assertEquals(warm, graded.timeline.clip("W")!!.grade)
        assertEquals("only one clip changes", withStill.timeline.clip("V"), graded.timeline.clip("V"))
        assertEquals("只能把调色应用到画面片段", rejected { gallery.apply(withStill, "s1", "A") })
        assertEquals("静帧已删除", rejected { gallery.apply(withStill, "gone", "W") })
    }

    @Test
    fun `a frame of another aspect is letterboxed into the picture`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val fitted = GalleryUseCase.fitted(FramePixels(2, 2, IntArray(4) { white }), 8, 4)
        assertEquals(8, fitted.width)
        assertEquals(4, fitted.height)
        assertEquals(listOf(black, black, white, white, white, white, black, black), fitted.pixels.take(8))
        val same = FramePixels(8, 4, IntArray(32) { white })
        assertTrue("the right size is kept as it is", GalleryUseCase.fitted(same, 8, 4) === same)
        // Filled, a square picture covers the 2:1 still (its top and bottom cropped); stretched, too.
        val left = 0xFF0000FF.toInt()
        val square = FramePixels(2, 2, intArrayOf(left, white, left, white))
        val fill = Framing.byShape(ClipScaling.FILL, 1f, 2f)
        assertEquals(listOf(left, left, left, left, white, white, white, white), GalleryUseCase.fitted(square, 8, 4, fill).pixels.take(8))
        assertEquals(listOf(left, left, left, left, white, white, white, white), GalleryUseCase.fitted(square, 8, 4, Framing.FULL).pixels.take(8))
        // At its own size a picture as big as the still can still be a part of it: a quarter-width picture, centred.
        val quarter = GalleryUseCase.fitted(same, 8, 4, Framing(0.25f, 1f))
        assertEquals(listOf(black, black, black, white, white, black, black, black), quarter.pixels.take(8))
    }
}
