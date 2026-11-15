package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_ID
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.model.FormatPreset
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Resolve's timelines in a project: open, new, duplicate, delete, with the open one swapped in and out. */
class TimelinesUseCaseTest {

    private val timelines = TimelinesUseCase(SequentialIds())
    private val cut = timelineOf(
        clipOf("A", 0, 60, assetId = "v").copy(linkId = "L"),
        clipOf("Aa", 0, 60, track = Timeline.A1, assetId = "v").copy(linkId = "L"),
        clipOf("B", 60, 30, assetId = "v"),
    ).copy(markers = listOf(Marker("m", 10, "开场")))
    private val doc = projectOf(timeline = cut, media = listOf(assetOf("v")))

    private fun rejection(block: () -> Unit): String? = try {
        block(); fail("expected a refusal"); null
    } catch (e: EditRejected) {
        e.message
    }

    @Test
    fun `a duplicate opens beside the original with the same cut under new ids`() {
        val result = timelines.duplicate(doc)
        val d = result.doc
        assertEquals("已复制为“时间线 1 副本”", result.message)
        assertEquals("时间线 1 副本", d.timelineName)
        assertEquals(1, d.timelineIndex)
        assertEquals(listOf(FIRST_TIMELINE_NAME to false, "时间线 1 副本" to true), d.timelineEntries.map { it.name to it.open })
        // The original waits unchanged; the copy has new clip, link and marker ids, linked pairs still paired.
        assertEquals(cut, d.timelines.single().timeline)
        assertEquals(listOf(60, 60, 30), d.timeline.clips.map { it.durationFrames })
        assertTrue(d.timeline.clips.none { c -> cut.clips.any { it.id == c.id } })
        val links = d.timeline.clips.mapNotNull { it.linkId }
        assertEquals(2, links.size)
        assertEquals(1, links.toSet().size)
        assertTrue(links.first() != "L")
        assertTrue(d.timeline.markers.single().id != "m")
        // Twice: numbered.
        assertEquals("时间线 1 副本 2", timelines.duplicate(timelines.open(d, d.timelines.single().id).doc).doc.timelineName)
    }

    @Test
    fun `opening another timeline swaps its raster, clips and mixer in and keeps the order`() {
        val vertical = timelines.duplicate(doc).doc.let { it.copy(format = it.format.copy(width = 1080, height = 1920, scaling = ClipScaling.FILL)) }
        val back = timelines.open(vertical, vertical.timelines.single().id)
        assertEquals("已切换到“时间线 1”", back.message)
        assertEquals(doc.format, back.doc.format)
        assertEquals(cut, back.doc.timeline)
        assertEquals(0, back.doc.timelineIndex)
        val stored = back.doc.timelines.single()
        assertEquals(ProjectFormat(1080, 1920, 30, ClipScaling.FILL), stored.format)
        assertEquals(listOf(FIRST_TIMELINE_NAME, "时间线 1 副本"), back.doc.timelineEntries.map { it.name })
        assertEquals("“时间线 1”已经打开", rejection { timelines.open(back.doc, back.doc.timelineId) })
        assertEquals("找不到这条时间线", rejection { timelines.open(back.doc, "nope") })
    }

    @Test
    fun `a view of the project with another timeline open changes nothing stored`() {
        val two = timelines.duplicate(doc).doc.let { it.copy(format = it.format.copy(width = 1080, height = 1920)) }
        val view = two.viewing(FIRST_TIMELINE_ID)!!
        assertEquals(1920, view.format.width)
        assertEquals(cut, view.timeline)
        // The open timeline waits in the viewed one's place (compound clips may play it); the project itself is untouched.
        assertEquals(listOf(two.timelineId), view.timelines.map { it.id })
        assertEquals(two.timeline, view.timelines.single().timeline)
        assertEquals(FIRST_TIMELINE_ID, two.timelines.single().id)
        assertTrue(two.viewing(two.timelineId) === two)
        assertEquals(null, two.viewing("gone"))
    }

    @Test
    fun `a new timeline is empty on the open raster, named after the highest number`() {
        val square = doc.copy(format = ProjectFormat.of(FormatPreset.HD_SQUARE, 25))
        val made = timelines.create(square)
        assertEquals("已新建“时间线 2”", made.message)
        assertEquals(Timeline.EMPTY, made.doc.timeline)
        assertEquals(square.format, made.doc.format)
        assertEquals(listOf(Timeline.A1, Timeline.A2), made.doc.mixer.channels.map { it.trackId })
        // The new id never repeats the first timeline's.
        assertTrue(made.doc.timelineId != square.timelineId)
        assertEquals("时间线 3", timelines.create(made.doc).doc.timelineName)
    }

    @Test
    fun `deleting opens the timeline before, and the last one stays`() {
        val three = timelines.create(timelines.duplicate(doc).doc).doc
        assertEquals(listOf(FIRST_TIMELINE_NAME, "时间线 1 副本", "时间线 2"), three.timelineEntries.map { it.name })
        val gone = timelines.delete(three)
        assertEquals("已删除“时间线 2”，打开“时间线 1 副本”", gone.message)
        val first = timelines.open(gone.doc, gone.doc.timelines.single().id).doc
        val left = timelines.delete(first)
        assertEquals("已删除“时间线 1”，打开“时间线 1 副本”", left.message)
        assertEquals(listOf("时间线 1 副本"), left.doc.timelineEntries.map { it.name })
        assertEquals("项目至少要有一条时间线", rejection { timelines.delete(left.doc) })
    }
}
