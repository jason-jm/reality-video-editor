package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.projectOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolve's clip Scaling: how a picture of another shape or size fills the frame, and what the inspector says about it. */
class ClipScalingTest {

    private fun assertFraming(spanX: Float, spanY: Float, actual: Framing) {
        assertEquals(spanX, actual.spanX, 1e-5f)
        assertEquals(spanY, actual.spanY, 1e-5f)
    }

    @Test
    fun `fit keeps the whole picture with bars, fill crops the overflow, stretch fills both ways`() {
        // A phone's 9:16 video on a 16:9 timeline: fitted it is 0.316 of the width; filled 3.16 of the height.
        assertFraming(0.31640625f, 1f, ClipScaling.FIT.framing(1080, 1920, 1920, 1080))
        assertFraming(1f, 3.1604938f, ClipScaling.FILL.framing(1080, 1920, 1920, 1080))
        assertFraming(1f, 1f, ClipScaling.STRETCH.framing(1080, 1920, 1920, 1080))
        // A 2.39:1 picture on 16:9: bars above and below fitted, the sides cropped filled.
        assertFraming(1f, 0.74384f, Framing.byShape(ClipScaling.FIT, 2.39f, 16f / 9f))
        assertFraming(1.344375f, 1f, Framing.byShape(ClipScaling.FILL, 2.39f, 16f / 9f))
        // The same shape fills exactly when fitted, filled or stretched, whatever its size.
        listOf(ClipScaling.FIT, ClipScaling.FILL, ClipScaling.STRETCH).forEach { assertFraming(1f, 1f, it.framing(3840, 2160, 1920, 1080)) }
    }

    @Test
    fun `at its own size a picture keeps its pixels against the timeline's`() {
        // A 720p clip on a 1080p timeline: two thirds of the frame each way, bars all round.
        assertFraming(2f / 3f, 2f / 3f, ClipScaling.CROP.framing(1280, 720, 1920, 1080))
        // 4K on 1080p: twice the frame, only its middle quarter shows.
        assertFraming(2f, 2f, ClipScaling.CROP.framing(3840, 2160, 1920, 1080))
        // A phone's video: narrower than the frame, taller than it.
        assertFraming(0.5625f, 1.7777778f, ClipScaling.CROP.framing(1080, 1920, 1920, 1080))
        // Sizes unknown: nothing to place it by, the frame is filled; by shape alone it fits.
        assertEquals(Framing.FULL, ClipScaling.CROP.framing(0, 0, 1920, 1080))
        assertFraming(0.31640625f, 1f, Framing.byShape(ClipScaling.CROP, 9f / 16f, 16f / 9f))
        // What the frame keeps of an overflowing picture.
        assertFraming(0.5625f, 1f, ClipScaling.CROP.framing(1080, 1920, 1920, 1080).inFrame)
    }

    @Test
    fun `the note says what the mode does to this picture`() {
        assertEquals("素材与时间线的画面比例相同，适应、填充、拉伸看起来一样", scalingNote(ClipScaling.FILL, ClipScaling.FIT, 3840, 2160, 1920, 1080))
        assertEquals("素材 1080×1920 比画面窄：保留整个画面，左右留黑边", scalingNote(ClipScaling.FIT, ClipScaling.FIT, 1080, 1920, 1920, 1080))
        assertEquals("素材 1080×1920 比画面窄：铺满画面，裁去上下各 34%", scalingNote(ClipScaling.FILL, ClipScaling.FIT, 1080, 1920, 1920, 1080))
        assertEquals("素材 1080×1920 比画面窄：铺满画面，横向拉宽 3.2 倍", scalingNote(ClipScaling.STRETCH, ClipScaling.FIT, 1080, 1920, 1920, 1080))
        assertEquals("素材 2560×1080 比画面宽：保留整个画面，上下留黑边", scalingNote(ClipScaling.FIT, ClipScaling.FIT, 2560, 1080, 1920, 1080))
        assertEquals("素材 2560×1080 比画面宽：铺满画面，裁去左右各 13%", scalingNote(ClipScaling.FILL, ClipScaling.FIT, 2560, 1080, 1920, 1080))
        assertEquals("素材 1440×1080 比画面窄：铺满画面，横向拉宽 1.3 倍", scalingNote(ClipScaling.STRETCH, ClipScaling.FIT, 1440, 1080, 1920, 1080))
        assertEquals(
            "画面比例与时间线不同时：适应留黑边，填充裁去多出的部分，拉伸会变形，原尺寸不缩放",
            scalingNote(ClipScaling.FIT, ClipScaling.FIT, 0, 0, 1920, 1080),
        )
    }

    @Test
    fun `at its own size the note names the bars and the crop`() {
        val crop = ClipScaling.CROP
        assertEquals("素材与时间线同为 1920×1080，原尺寸正好铺满画面", scalingNote(crop, ClipScaling.FIT, 1920, 1080, 1920, 1080))
        assertEquals("素材 1280×720 比画面小：按原尺寸居中，四周留黑边", scalingNote(crop, ClipScaling.FIT, 1280, 720, 1920, 1080))
        assertEquals("素材 1920×800 比画面小：按原尺寸居中，上下留黑边", scalingNote(crop, ClipScaling.FIT, 1920, 800, 1920, 1080))
        assertEquals("素材 3840×2160 比画面大：按原尺寸居中，裁去左右各 25%、上下各 25%", scalingNote(crop, ClipScaling.FIT, 3840, 2160, 1920, 1080))
        assertEquals("素材 1080×1920 按原尺寸居中：左右留黑边，裁去上下各 22%", scalingNote(crop, ClipScaling.FIT, 1080, 1920, 1920, 1080))
        assertEquals("素材 2560×720 按原尺寸居中：上下留黑边，裁去左右各 13%", scalingNote(crop, ClipScaling.FIT, 2560, 720, 1920, 1080))
    }

    @Test
    fun `a clip following the project says so and shows the project's mode`() {
        assertEquals(
            "按项目设置（填充）：素材 1080×1920 比画面窄：铺满画面，裁去上下各 34%",
            scalingNote(null, ClipScaling.FILL, 1080, 1920, 1920, 1080),
        )
        // In a document: the clip's own choice wins; following, the project's applies; no size, no framing.
        val phone = assetOf("phone").copy(width = 1080, height = 1920)
        val base = projectOf(media = listOf(phone, assetOf("unknown").copy(width = 0, height = 0)))
        val doc = base.copy(format = base.format.copy(scaling = ClipScaling.FILL))
        val following = clipOf("a", 0, 30, assetId = "phone")
        val own = following.copy(scaling = ClipScaling.FIT)
        assertEquals(ClipScaling.FILL, doc.scalingOf(following))
        assertEquals(ClipScaling.FIT, doc.scalingOf(own))
        assertFraming(1f, 3.1604938f, doc.framingOf(following)!!)
        assertFraming(0.31640625f, 1f, doc.framingOf(own)!!)
        assertNull(doc.framingOf(clipOf("b", 0, 30, assetId = "unknown")))
        // The decoded picture's own shape places it by shape; at its own size the recorded size counts.
        assertFraming(1f, 1f, doc.framingOf(own, 16f / 9f)!!)
        assertFraming(0.5625f, 1.7777778f, doc.framingOf(following.copy(scaling = ClipScaling.CROP), 16f / 9f)!!)
        // At its own size with no recorded size: it fits, as it renders.
        assertFraming(0.31640625f, 1f, doc.framingOf(clipOf("c", 0, 30, assetId = "unknown").copy(scaling = ClipScaling.CROP), 9f / 16f)!!)
    }
}
