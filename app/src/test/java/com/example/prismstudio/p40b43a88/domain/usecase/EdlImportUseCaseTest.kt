package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Import → Timeline → EDL. */
class EdlImportUseCaseTest {

    private val media = listOf(
        assetOf("red", kind = MediaKind.IMAGE, name = "red.png"), assetOf("blue", kind = MediaKind.IMAGE, name = "blue.png"),
        assetOf("tone", kind = MediaKind.AUDIO, name = "tone.wav", durationMs = 5000),
        assetOf("film", name = "A001C003.mov", durationMs = 20_000),
    ).map { it.copy(uri = "file:///x/${it.name}") }

    @Test
    fun `an exported EDL comes back as a timeline that plays the same`() {
        val a = clipOf("a", 0, 60, assetId = "red")
        val b = clipOf("b", 60, 60, assetId = "blue").copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER))
        val sa = clipOf("sa", 0, 60, Timeline.A1, assetId = "tone")
        val sb = clipOf("sb", 60, 60, Timeline.A1, assetId = "tone", sourceInMs = 2000).copy(crossFadeFrames = 15)
        val slow = clipOf("slow", 150, 30, assetId = "film", sourceInMs = 1000, speed = PlaybackSpeed.HALF).copy(reverse = true)
        val doc = projectOf(timelineOf(a, b, sa, sb, slow), media = media)
        val text = EdlExportUseCase()(doc).text
        val result = EdlImportUseCase(SequentialIds())(doc, text, "x.edl")
        val t = result.doc.timeline
        assertEquals("a new timeline named by the TITLE, opened", "${doc.timelineName} (2)", result.doc.timelineName)
        assertEquals(2, result.doc.timelineEntries.size)
        val v1 = t.clipsOn(Timeline.V1)
        // The centred dissolve comes back starting at its edit point: the same frames mix.
        assertEquals(listOf(0 to 53, 53 to 120, 150 to 180), v1.map { it.start to it.end })
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.START), v1[1].transitionIn)
        assertEquals(listOf("red", "blue", "film"), v1.map { it.assetId })
        val slowBack = v1[2]
        assertEquals(0.5f to true, slowBack.speed.factor to slowBack.reverse)
        assertEquals(1000L, slowBack.sourceInMs)
        val a1 = t.clipsOn(Timeline.A1)
        assertEquals(listOf(0 to 60, 60 to 120), a1.map { it.start to it.end })
        assertEquals(15, a1[1].crossFadeFrames)
        assertEquals(2000L, a1[1].sourceInMs)
        assertTrue(result.left.isEmpty())
        assertEquals("已从 EDL 导入 5 个片段，新建并打开“${doc.timelineName} (2)”", result.message)
    }

    @Test
    fun `another tool's EDL, whose reels name the media, B is picture and sound, black and unknown sources are left out`() {
        val text = """
            TITLE: 粗剪 v3
            FCM: NON-DROP FRAME

            001  A001C003 B     C        00:00:10:00 00:00:12:00 00:00:00:00 00:00:02:00
            002  BL       V     C        00:00:00:00 00:00:01:00 00:00:02:00 00:00:03:00
            003  ZZZ999   V     C        00:00:00:00 00:00:01:00 00:00:03:00 00:00:04:00
            004  A001C003 A3    C        00:00:00:00 00:00:01:00 00:00:00:00 00:00:01:00
        """.trimIndent()
        val result = EdlImportUseCase(SequentialIds())(projectOf(media = media), text, "cut.edl")
        val t = result.doc.timeline
        assertEquals("粗剪 v3", result.doc.timelineName)
        val v = t.clipsOn(Timeline.V1).single()
        val s = t.clipsOn(Timeline.A1).single()
        assertEquals("film" to 10_000L, v.assetId to v.sourceInMs)
        assertEquals("picture and sound linked", v.linkId, s.linkId)
        assertTrue(v.linkId != null)
        assertEquals(listOf("事件 003：媒体池里没有“ZZZ999”", "事件 004：A3 通道没有对应的轨道"), result.left)
        assertEquals(
            "a file with no events",
            "文件里没有可以识别的 EDL 事件",
            assertThrows(EditRejected::class.java) { EdlImportUseCase(SequentialIds())(projectOf(media = media), "hello", "x.edl") }.message,
        )
        assertNull(EdlImportUseCase(SequentialIds()).parse("FCM: NON-DROP FRAME", 30).first)
    }
}
