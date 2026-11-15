package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Copy, cut, paste (overwrite), duplicate and delete of timeline clips. */
class ClipboardUseCaseTest {

    private val clipboard = ClipboardUseCase(SequentialIds())
    private val fps = 30

    // A picture and its sound; the sound starts 10 frames early (a J-cut).
    private val video = clipOf("A", 30, 60, linkId = "L").copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 10))
    private val sound = clipOf("Aa", 20, 70, track = Timeline.A1, linkId = "L")

    @Test
    fun `a copy takes the linked partner, the selected clip first, timed from the earliest`() {
        val content = clipboard.copy(timelineOf(video, sound), "A")
        assertEquals(listOf("A", "Aa"), content.clips.map { it.id })
        assertEquals(listOf(10, 0), content.clips.map { it.start })
        assertEquals(70, content.durationFrames)
        assertEquals(listOf("Aa", "A"), clipboard.copy(timelineOf(video, sound), "Aa").clips.map { it.id })
        assertEquals("请先在时间线上选择片段", assertThrows(EditRejected::class.java) { clipboard.copy(timelineOf(video), "nope") }.message)
    }

    @Test
    fun `pasting puts new clips at the playhead on their tracks, linked to each other, and selects them`() {
        val timeline = timelineOf(video, sound)
        val content = clipboard.copy(timeline, "A")

        val result = clipboard.paste(timeline, content, at = 300, fps = fps)

        assertEquals(
            timeline.clips + listOf(
                video.copy(id = "c-1", start = 310, linkId = "l-2", transitionIn = null),
                sound.copy(id = "c-3", start = 300, linkId = "l-2"),
            ),
            result.timeline.clips,
        )
        assertEquals("c-1", result.selectedClipId)
        assertEquals("已粘贴 2 个片段到 00:00:10:10", result.message)
    }

    @Test
    fun `a paste overwrites what is under it`() {
        // V1: X (0–100, faded) is cut back, Y (100–140) goes, Z (140–240) loses its head.
        val x = clipOf("X", 0, 100, sourceInMs = 1_000).copy(fadeInSeconds = 0.5f, fadeOutSeconds = 0.5f)
        val y = clipOf("Y", 100, 40)
        val z = clipOf("Z", 140, 100).copy(fadeInSeconds = 1f)
        val other = clipOf("O", 0, 300, track = Timeline.A1)
        val pasted = clipOf("P", 0, 100)
        val content = ClipboardUseCase.Content(listOf(pasted))

        val result = clipboard.paste(timelineOf(x, y, z, other), content, at = 50, fps = fps)

        assertEquals(
            listOf(
                x.copy(durationFrames = 50, fadeOutSeconds = 0f),
                z.copy(start = 150, durationFrames = 90, sourceInMs = 333, fadeInSeconds = 0f),
                other,
                pasted.copy(id = "c-1", start = 50),
            ),
            result.timeline.clips,
        )
        assertEquals("已粘贴 1 个片段到 00:00:01:20，覆盖了原有内容", result.message)
    }

    @Test
    fun `a clip split around a paste keeps its head and gets a new tail, linked to its partner's tail`() {
        val w = clipOf("W", 0, 100, linkId = "K")
        val wa = clipOf("Wa", 0, 100, track = Timeline.A1, linkId = "K")
        val content = ClipboardUseCase.Content(listOf(clipOf("P", 0, 20, linkId = "Q"), clipOf("Pa", 0, 20, track = Timeline.A1, linkId = "Q")))

        val clips = clipboard.paste(timelineOf(w, wa), content, at = 30, fps = fps).timeline.clips

        assertEquals(
            listOf(
                w.copy(durationFrames = 30),
                w.copy(id = "c-4", start = 50, durationFrames = 50, sourceInMs = 1_666, linkId = "l-5"),
                wa.copy(durationFrames = 30),
                wa.copy(id = "c-6", start = 50, durationFrames = 50, sourceInMs = 1_666, linkId = "l-5"),
                clipOf("P", 30, 20, linkId = "l-2").copy(id = "c-1"),
                clipOf("Pa", 30, 20, track = Timeline.A1, linkId = "l-2").copy(id = "c-3"),
            ),
            clips,
        )
    }

    @Test
    fun `a clip whose track is gone goes to the base track of its kind`() {
        val content = ClipboardUseCase.Content(
            listOf(
                clipOf("P", 0, 20, track = "v3"),
                clipOf("S", 0, 20, track = "a3"),
                titleClipOf("Sub", 0, 20, track = "st1").copy(title = TitleStyle("台词", 44, TitleTemplate.SUBTITLE)),
            ),
        )

        val result = clipboard.paste(timelineOf(), content, at = 0, fps = fps).timeline

        assertEquals(listOf(Timeline.V1, Timeline.A1, "st1"), result.clips.map { it.trackId })
        assertEquals("a subtitle track is made", TrackKind.SUBTITLE, result.track("st1")?.kind)
    }

    @Test
    fun `locked tracks and an empty clipboard refuse a paste`() {
        val content = ClipboardUseCase.Content(listOf(clipOf("P", 0, 20)))
        assertEquals("目标轨道已锁定", assertThrows(EditRejected::class.java) { clipboard.paste(timelineOf(locked = setOf(Timeline.V1)), content, 0, fps) }.message)
        assertEquals("剪贴板是空的", assertThrows(EditRejected::class.java) { clipboard.paste(timelineOf(), ClipboardUseCase.Content(emptyList()), 0, fps) }.message)
    }

    @Test
    fun `ripple cut copies the clip with its partner and closes the gaps behind them`() {
        val b = clipOf("B", 90, 30)
        val c = clipOf("C", 90, 40, track = Timeline.A1)
        val (content, result) = clipboard.rippleCut(timelineOf(video, sound, b, c), listOf("A"), fps)
        assertEquals(listOf("A", "Aa"), content.clips.map { it.id })
        assertEquals(listOf("B", "C"), result.timeline.clips.map { it.id })
        assertEquals("both tracks close up by the cut picture's length", listOf(30, 30), result.timeline.clips.map { it.start })
        assertEquals("已波纹剪切“A”，后面的片段前移", result.message)
        val (two, both) = clipboard.rippleCut(timelineOf(video, sound, b, c), listOf("A", "B"), fps)
        assertEquals(2, two.groups)
        assertEquals("已波纹剪切 2 个片段，后面的片段前移", both.message)
        assertEquals("链接的片段在已锁定的轨道上", assertThrows(EditRejected::class.java) {
            clipboard.rippleCut(timelineOf(video, sound, locked = setOf(Timeline.A1)), listOf("A"), fps)
        }.message)
    }

    @Test
    fun `paste insert opens every unlocked track at the playhead for the copy, leaving a locked one`() {
        val content = clipboard.copy(timelineOf(video, sound), "A")
        val x = clipOf("X", 0, 100)
        val music = clipOf("M", 0, 200, track = Timeline.A2)
        val result = clipboard.pasteInsert(timelineOf(x, music), content, at = 40, fps = fps)
        val t = result.timeline
        // The copy lasts 70 frames (its sound starts 10 before the picture): X splits at 40 and its rest moves 70 later.
        assertEquals(listOf(0 to 40, 50 to 110, 110 to 170), t.clipsOn(Timeline.V1).map { it.start to it.end })
        assertEquals(listOf(40 to 110), t.clipsOn(Timeline.A1).map { it.start to it.end })
        assertEquals("the music opens the same room", listOf(0 to 40, 110 to 270), t.clipsOn(Timeline.A2).map { it.start to it.end })
        val pasted = t.clip(result.selectedClipId)!!
        assertEquals(50 to "A", pasted.start to pasted.name)
        assertEquals("picture and sound stay linked", 1, t.clips.filter { it.start in 40..50 && it.name.startsWith("A") }.map { it.linkId }.distinct().size)
        assertEquals("已插入粘贴 2 个片段到 00:00:01:10，后面的片段后移 00:00:02:10", result.message)
        val locked = clipboard.pasteInsert(timelineOf(x, music, locked = setOf(Timeline.A2)), content, at = 40, fps = fps).timeline
        assertEquals(listOf(0 to 200), locked.clipsOn(Timeline.A2).map { it.start to it.end })
        assertEquals("剪贴板是空的", assertThrows(EditRejected::class.java) { clipboard.pasteInsert(timelineOf(x), ClipboardUseCase.Content(emptyList()), 0, fps) }.message)
    }

    @Test
    fun `a duplicate follows the original with its partner`() {
        val a = clipOf("A", 0, 60, linkId = "L")
        val aa = clipOf("Aa", 0, 60, track = Timeline.A1, linkId = "L")

        val result = clipboard.duplicate(timelineOf(a, aa), "A", fps)

        assertEquals(listOf(a, aa, a.copy(id = "c-1", start = 60, linkId = "l-2"), aa.copy(id = "c-3", start = 60, linkId = "l-2")), result.timeline.clips)
        assertEquals("c-1", result.selectedClipId)
        assertEquals("已创建副本：“A”（00:00:02:00）", result.message)
    }

    @Test
    fun `delete lifts the clip with its partner and leaves the gap, cut also copies it`() {
        val b = clipOf("B", 100, 50)
        val timeline = timelineOf(video, sound, b)

        val lifted = clipboard.delete(timeline, "A")
        assertEquals("the next clip does not move", listOf(b), lifted.timeline.clips)
        assertEquals("已删除“A”及链接的片段，保留空隙", lifted.message)
        assertEquals(null, lifted.selectedClipId)
        assertEquals("已删除“B”，保留空隙", clipboard.delete(timeline, "B").message)
        assertEquals("链接的片段在已锁定的轨道上", assertThrows(EditRejected::class.java) { clipboard.delete(timelineOf(video, sound, locked = setOf(Timeline.A1)), "A") }.message)

        val (content, cut) = clipboard.cut(timeline, "A")
        assertEquals(listOf("A", "Aa"), content.clips.map { it.id })
        assertEquals(listOf(b), cut.timeline.clips)
        assertEquals("已剪切“A”", cut.message)
    }
}
