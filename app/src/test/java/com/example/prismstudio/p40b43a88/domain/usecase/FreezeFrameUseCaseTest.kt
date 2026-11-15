package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Freeze frame: a held still of the playhead frame inside the clip, the rest moved later in sync. */
class FreezeFrameUseCaseTest {

    private val freeze = FreezeFrameUseCase(SequentialIds())
    private val fps = 30

    // V1: A (0–90, a 35 % inset, graded, blurred) with its sound Aa on A1, then B; music on A2 and a title on V2 keep their timing.
    private val placed = ClipTransform(scalePercent = 35, positionX = 576, positionY = 303)
    private val a = clipOf("A", 0, 90, assetId = "v", sourceInMs = 1_000, linkId = "L")
        .copy(transform = placed, grade = ColorGrade(exposure = 0.5f), effects = listOf(ClipEffect("fx", EffectType.GAUSSIAN_BLUR, 40, true)))
    private val aa = clipOf("Aa", 0, 90, track = Timeline.A1, assetId = "v", sourceInMs = 1_000, linkId = "L")
    private val b = clipOf("B", 90, 60, assetId = "v")
    private val music = clipOf("M", 0, 300, track = Timeline.A2, assetId = "s")
    private val title = titleClipOf("T", 60, 60)
    private val still = assetOf("a-1", kind = MediaKind.IMAGE, name = "定格.png")

    private fun project(clips: List<Clip> = listOf(a, aa, b, music, title)): ProjectDocument =
        projectOf(timeline = timelineOf(*clips.toTypedArray()), media = listOf(assetOf("v"), assetOf("s", kind = MediaKind.AUDIO), assetOf("img", kind = MediaKind.IMAGE)))

    private fun rejection(doc: ProjectDocument, clipId: String?, at: Int) =
        assertThrows(EditRejected::class.java) { freeze.request(doc, clipId, at) }.message

    @Test
    fun `the frame at the playhead is captured from the clip's source`() {
        val request = freeze.request(project(), "A", 30)
        assertEquals("v", request.asset.id)
        assertEquals("one second in, from a source in point of one second", 2_000L, request.sourceMs)
        assertEquals("a-1", request.stillId)
        assertEquals("定格 · A · 00:00:01:00", request.stillName)

        val fast = a.copy(speed = ClipSpeed.of(PlaybackSpeed.DOUBLE))
        assertEquals("at 2× a second of timeline is two of source", 3_000L, freeze.request(project(listOf(fast)), "A", 30).sourceMs)
        val reversed = a.copy(reverse = true)
        assertEquals("reversed, the window plays from its end", 1_000L + 59 * 1000 / 30, freeze.request(project(listOf(reversed)), "A", 30).sourceMs)
    }

    @Test
    fun `only a video clip under the playhead, with readable media on unlocked tracks, can freeze`() {
        assertEquals("请先在时间线上选择视频片段", rejection(project(), null, 30))
        assertEquals("只能为视频片段定格", rejection(project(), "T", 60))
        assertEquals("只能为视频片段定格", rejection(project(), "Aa", 30))
        assertEquals("图片本身就是静帧，无需定格", rejection(project(listOf(clipOf("I", 0, 90, assetId = "img"))), "I", 30))
        val offline = projectOf(timeline = timelineOf(a), media = listOf(assetOf("v", offline = true)))
        assertEquals("素材离线，无法定格", rejection(offline, "A", 30))
        assertEquals("请把播放头移到所选片段上", rejection(project(), "A", 90))
        val locked = projectOf(timeline = timelineOf(a, aa, locked = setOf(Timeline.A1)), media = listOf(assetOf("v")))
        assertEquals("链接的片段在已锁定的轨道上", rejection(locked, "A", 30))
    }

    @Test
    fun `the still holds inside the clip and the rest moves later with its sound`() {
        val doc = project()
        val request = freeze.request(doc, "A", 30)

        val result = freeze.insert(doc, request, still, atFrame = 30, holdFrames = 60)

        val clips = result.doc.timeline.clips
        assertEquals(
            listOf(
                a.copy(durationFrames = 30),
                a.copy(id = "c-2", start = 90, durationFrames = 60, sourceInMs = 2_000, linkId = "l-3"),
                aa.copy(durationFrames = 30),
                aa.copy(id = "c-4", start = 90, durationFrames = 60, sourceInMs = 2_000, linkId = "l-3"),
                b.copy(start = 150),
                music,
                title,
            ),
            clips.dropLast(1),
        )
        val frozen = clips.last()
        assertEquals("c-5", frozen.id)
        assertEquals(Timeline.V1 to (30 until 90), frozen.trackId to (frozen.start until frozen.end))
        assertEquals("a-1", frozen.assetId)
        assertEquals("定格 · A", frozen.name)
        assertEquals("it looks like the paused clip", placed, frozen.transform)
        assertEquals(a.grade, frozen.grade)
        assertEquals(listOf(ClipEffect("fx-6", EffectType.GAUSSIAN_BLUR, 40, true)), frozen.effects)
        assertEquals(doc.media + still, result.doc.media)
        assertEquals("c-5", result.selectedClipId)
        assertEquals("已在 00:00:01:00 定格 2 秒，后面的片段顺延", result.message)
    }

    @Test
    fun `at the clip's first frame the still goes before it, and keyframes give the still that moment's placement`() {
        val doc = project()
        val atStart = freeze.insert(doc, freeze.request(doc, "A", 0), still, atFrame = 0, holdFrames = 45).doc.timeline
        assertEquals(listOf(45, 45, 135), listOf(atStart.clip("A")!!.start, atStart.clip("Aa")!!.start, atStart.clip("B")!!.start))
        assertEquals("the still opens the clip", 0, atStart.clips.last().start)

        val moving = a.copy(keyframes = listOf(TransformKeyframe(0, ClipTransform(scalePercent = 100)), TransformKeyframe(60, ClipTransform(scalePercent = 40))))
        val keyed = project(listOf(moving, aa, b))
        val frozen = freeze.insert(keyed, freeze.request(keyed, "A", 30), still, 30, 60).doc.timeline.clips.last()
        assertEquals(70, frozen.transform.scalePercent)
        assertEquals(emptyList<TransformKeyframe>(), frozen.keyframes)
    }

    @Test
    fun `a clip gone while the frame was captured is refused`() {
        val doc = project()
        val request = freeze.request(doc, "A", 30)
        val without = doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.filter { it.id != "A" }))
        assertEquals("片段已不在时间线上", assertThrows(EditRejected::class.java) { freeze.insert(without, request, still, 30, 60) }.message)
    }
}
