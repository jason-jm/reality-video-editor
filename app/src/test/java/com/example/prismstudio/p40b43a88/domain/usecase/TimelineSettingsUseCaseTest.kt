package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Resolve's timeline settings: the raster and how mismatched media fill it. */
class TimelineSettingsUseCaseTest {

    private val settings = TimelineSettingsUseCase()
    private val landscape = assetOf("land")
    private val phone = assetOf("phone").copy(width = 1080, height = 1920)
    private val small = assetOf("small").copy(width = 1280, height = 720)
    private val doc = projectOf(
        timeline = timelineOf(
            clipOf("A", 0, 30, assetId = "land"),
            clipOf("B", 30, 30, assetId = "phone"),
            clipOf("C", 60, 30, assetId = "small"),
            clipOf("D", 90, 30, assetId = "phone").copy(scaling = ClipScaling.STRETCH),
            titleClipOf("T", 0, 30),
            clipOf("S", 0, 30, track = Timeline.A1, assetId = "song"),
        ),
        media = listOf(landscape, phone, small, assetOf("song", kind = MediaKind.AUDIO)),
    )

    private fun rejection(block: () -> Unit): String? = try {
        block(); fail("expected a refusal"); null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `a landscape timeline becomes a vertical one that fills, keeping its clips and frame rate`() {
        val result = settings(doc, FormatPreset.HD_PORTRAIT, ClipScaling.FILL)
        assertEquals(ProjectFormat(1080, 1920, 30, ClipScaling.FILL), result.doc.format)
        assertEquals(doc.timeline, result.doc.timeline)
        assertEquals("时间线改为 1080×1920 竖屏，比例不同的素材填充", result.message)
    }

    @Test
    fun `changing only one setting says only that, and changing nothing is refused`() {
        assertEquals("比例不同的素材原尺寸", settings(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.CROP).message)
        assertEquals("时间线改为 1080×1080 方形", settings(doc, FormatPreset.HD_SQUARE, ClipScaling.FIT).message)
        assertEquals("时间线设置没有变化", rejection { settings(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT) })
    }

    @Test
    fun `the open timeline is renamed with its settings, never to nothing or to another timeline's name`() {
        assertEquals("时间线改名为“横屏版”", settings(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT, "  横屏版 ").message)
        assertEquals("横屏版", settings(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT, "横屏版").doc.timelineName)
        assertEquals("时间线名称不能为空", rejection { settings(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT, " ") })
        val two = TimelinesUseCase(com.example.prismstudio.p40b43a88.domain.SequentialIds()).duplicate(doc).doc
        assertEquals("项目里已有名为“时间线 1”的时间线", rejection { settings(two, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT, "时间线 1") })
    }

    @Test
    fun `the note counts the picture clips following the project and those the setting changes`() {
        // A, B and C follow (D chose its own, T is a title, S a sound); on 1080p landscape only the phone video differs in shape.
        assertEquals("3 个画面片段按项目设置缩放，其中 1 个与画面比例不同", settings.followers(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.FIT))
        // At their own size the 720p clip differs too.
        assertEquals("3 个画面片段按项目设置缩放，其中 2 个与画面尺寸不同", settings.followers(doc, FormatPreset.HD_LANDSCAPE, ClipScaling.CROP))
        // On a vertical timeline the landscape clips differ and the phone video matches.
        assertEquals("3 个画面片段按项目设置缩放，其中 2 个与画面比例不同", settings.followers(doc, FormatPreset.HD_PORTRAIT, ClipScaling.FILL))
        val alone = doc.copy(timeline = timelineOf(clipOf("A", 0, 30, assetId = "land")))
        assertEquals("1 个画面片段按项目设置缩放，都与画面比例相同", settings.followers(alone, FormatPreset.UHD_LANDSCAPE, ClipScaling.FIT))
        val none = doc.copy(timeline = timelineOf(clipOf("A", 0, 30, assetId = "land").copy(scaling = ClipScaling.FIT)))
        assertEquals(
            "没有按项目设置缩放的片段：检查器的缩放方式选“按项目”的片段才会按这里显示",
            settings.followers(none, FormatPreset.HD_LANDSCAPE, ClipScaling.FILL),
        )
    }

    @Test
    fun `presets name a format's raster and the square one reads as square`() {
        assertEquals(FormatPreset.HD_PORTRAIT, FormatPreset.of(ProjectFormat(1080, 1920, 25)))
        assertNull(FormatPreset.of(ProjectFormat(1280, 720, 30)))
        assertEquals("1080×1080 方形", ProjectFormat(1080, 1080, 30).sizeLabel)
    }
}
