package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Export → Timeline → EDL (CMX 3600). */
class EdlExportUseCaseTest {

    private val use = EdlExportUseCase()

    @Test
    fun `cuts, a centred dissolve, a sound cross fade, a speed and what CMX cannot hold`() {
        val a = clipOf("a", 0, 60)
        val b = clipOf("b", 60, 60, sourceInMs = 2000).copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER))
        val sa = clipOf("sa", 0, 60, Timeline.A1)
        val sb = clipOf("sb", 60, 60, Timeline.A1, sourceInMs = 2000).copy(crossFadeFrames = 10)
        val slow = clipOf("slow", 150, 30, speed = PlaybackSpeed.HALF).copy(reverse = true)
        val doc = projectOf(
            timelineOf(a, b, sa, sb, slow, titleClipOf("t", 250, 30, Timeline.V1), clipOf("pip", 0, 30, Timeline.V2), clipOf("off", 200, 30).copy(enabled = false)),
            media = listOf(assetOf("media", durationMs = 10_000)),
        )
        val result = use(doc)
        val lines = result.text.split("\r\n")
        assertEquals("TITLE: ${doc.timelineName}", lines[0])
        assertEquals("FCM: NON-DROP FRAME", lines[1])
        val events = lines.filter { it.matches(Regex("^\\d{3} .*")) }
        assertEquals(
            listOf(
                "001  AX       V     C        00:00:00:00 00:00:01:23 00:00:00:00 00:00:01:23",
                "002  AX       A     C        00:00:00:00 00:00:02:00 00:00:00:00 00:00:02:00",
                "003  AX       V     C        00:00:01:23 00:00:01:23 00:00:01:23 00:00:01:23",
                "003  AX       V     D    015 00:00:01:23 00:00:04:00 00:00:01:23 00:00:04:00",
                "004  AX       A     C        00:00:02:00 00:00:02:00 00:00:02:00 00:00:02:00",
                "004  AX       A     D    010 00:00:02:00 00:00:04:00 00:00:02:00 00:00:04:00",
                "005  AX       V     C        00:00:00:00 00:00:00:15 00:00:05:00 00:00:06:00",
            ),
            events,
        )
        assertEquals(5, result.events)
        assertTrue("the dissolve is named", "* EFFECT NAME: 交叉溶解" in lines)
        assertTrue("clip and file names", "* FROM CLIP NAME: b" in lines && lines.any { it.startsWith("* SOURCE FILE: ") })
        assertTrue("half speed backwards: −15 fps", "M2   AX       -15.0                00:00:00:00" in lines)
        assertEquals(
            listOf("V2 上 1 个片段（CMX 3600 只有一条画面轨和两条声音轨）", "1 个标题（没有源文件）", "1 个停用的片段"),
            result.left,
        )
        assertTrue(result.text.endsWith("\r\n"))
    }

    @Test
    fun `a dissolve into a still keeps source and record lengths equal, starting the still at 0`() {
        val a = clipOf("a", 0, 60, assetId = "red")
        val b = clipOf("b", 60, 60, assetId = "blue").copy(transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER))
        val doc = projectOf(timelineOf(a, b), media = listOf(assetOf("red", kind = MediaKind.IMAGE), assetOf("blue", kind = MediaKind.IMAGE)))
        val events = use(doc).text.split("\r\n").filter { it.matches(Regex("^\\d{3} .*")) }
        assertEquals("003  AX       V     D    015 00:00:00:00 00:00:02:07 00:00:01:23 00:00:04:00".replace("003", "002"), events.last())
        // Every event: source length = record length (all at 1×).
        for (e in events) {
            val tc = e.split(Regex("\\s+")).takeLast(4).map { t -> t.split(':').map(String::toInt).let { (h, m, s, f) -> ((h * 60 + m) * 60 + s) * 30 + f } }
            assertEquals(e, tc[1] - tc[0], tc[3] - tc[2])
        }
    }
}
