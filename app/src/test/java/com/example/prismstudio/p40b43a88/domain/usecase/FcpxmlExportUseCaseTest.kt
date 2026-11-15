package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.UiFixtures.clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Resolve's Export → Timeline → FCPXML: V1 as the storyline, the other tracks connected on lanes, and what FCPXML holds. */
class FcpxmlExportUseCaseTest {

    private val export = FcpxmlExportUseCase()

    /** 30 fps. V1: c1 (0–60, in at 1 s), a gap, c2 (90–150, 2×), c3 (150–210, dissolve in, in at 2 s); V2: a title over 30–90
     *  and a disabled still at 160; A1: c1's sound (−6 dB, half-second fade in); A2: music 0–240; a marker at 100. */
    private val doc = UiFixtures.document(
        media = listOf(UiFixtures.video("v1"), UiFixtures.video("v2"), UiFixtures.audio("au"), UiFixtures.image("im", name = "海报 & 封面.jpg")),
        clips = listOf(
            clip("c1", 0, 60, assetId = "v1", linkId = "L1").copy(sourceInMs = 1000),
            clip("c1a", 0, 60, trackId = Timeline.A1, assetId = "v1", linkId = "L1").copy(sourceInMs = 1000, volumeDb = -6f, fadeInSeconds = 0.5f),
            clip("c2", 90, 60, assetId = "v2").copy(speed = ClipSpeed(2f), transform = ClipTransform(scalePercent = 120)),
            clip("c3", 150, 60, assetId = "v1").copy(sourceInMs = 2000, transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 10)),
            clip("t1", 30, 60, trackId = Timeline.V2, kind = ClipKind.TITLE).copy(title = TitleStyle("你好 & <世界>", color = TitleColor.YELLOW)),
            clip("s1", 160, 20, trackId = Timeline.V2, assetId = "im").copy(enabled = false),
            clip("m1", 0, 240, trackId = Timeline.A2, assetId = "au"),
        ),
        markers = listOf(Marker("mk1", 100, "鼓点", note = "进副歌")),
    )

    private fun parse(text: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream()).documentElement
    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.first(tag: String): Element = getElementsByTagName(tag).item(0) as Element

    @Test
    fun `V1 is the storyline with gaps to the end, and its dissolve sits between its clips`() {
        val r = export(doc)
        val root = parse(r.text)
        assertEquals("1.10", root.getAttribute("version"))
        assertEquals(7, r.clips)
        val sequence = root.first("sequence")
        assertEquals("240/30s", sequence.getAttribute("duration"))
        val format = root.first("resources").kids().first { it.getAttribute("id") == sequence.getAttribute("format") }
        assertEquals(listOf("1/30s", "1920", "1080"), listOf("frameDuration", "width", "height").map { format.getAttribute(it) })
        val spine = root.first("spine").kids()
        assertEquals(listOf("asset-clip", "gap", "asset-clip", "transition", "asset-clip", "gap"), spine.map { it.tagName })
        assertEquals(listOf("0s", "60/30s", "90/30s", "150/30s", "210/30s"), spine.filter { it.tagName != "transition" }.map { it.getAttribute("offset") })
        // The storyline fills the timeline: its items (not the transition) add up to its length.
        assertEquals(240, spine.filter { it.tagName != "transition" }.sumOf { it.getAttribute("duration").removeSuffix("/30s").removeSuffix("s").toInt() })
        // c1 starts 1 s into its file and carries its picture only (its sound is on A1).
        assertEquals(listOf("c1", "30/30s", "video"), listOf("name", "start", "srcEnable").map { spine[0].getAttribute(it) })
        val span = TransitionHandles.span(doc, doc.timeline.clips.first { it.id == "c3" }, 30)!!
        assertEquals("${150 - span.lead}/30s".replace("^0/30s".toRegex(), "0s"), spine[3].getAttribute("offset"))
        assertEquals("${span.frames}/30s", spine[3].getAttribute("duration"))
        assertEquals("Cross Dissolve", spine[3].first("filter-video").getAttribute("name"))
    }

    @Test
    fun `other tracks connect on lanes, offsets in the parent's own time`() {
        val spine = parse(export(doc).text).first("spine").kids()
        val c1 = spine[0].kids()
        // Under c1 (its time starts at 30): the title on V2 (lane 1) at 30 → 60, c1's sound (−1) and the music (−2) at 0 → 30.
        val title = c1.first { it.tagName == "title" }
        assertEquals(listOf("1", "60/30s", "60/30s"), listOf("lane", "offset", "duration").map { title.getAttribute(it) })
        assertEquals("你好 & <世界>", title.first("text-style").textContent)
        assertTrue(title.getElementsByTagName("text-style").item(1).let { it as Element }.getAttribute("fontColor") == "1 0.8471 0.302 1")
        val sound = c1.first { it.getAttribute("name") == "c1a" }
        assertEquals(listOf("-1", "30/30s", "audio", "30/30s"), listOf("lane", "offset", "srcEnable", "start").map { sound.getAttribute(it) })
        val volume = sound.first("adjust-volume")
        assertEquals("-6.0dB", volume.getAttribute("amount"))
        assertEquals("15/30s", volume.first("fadeIn").getAttribute("duration"))
        val music = c1.first { it.getAttribute("name") == "m1" }
        assertEquals(listOf("-2", "30/30s", "240/30s"), listOf("lane", "offset", "duration").map { music.getAttribute(it) })
        // The disabled still at 160 hangs off c3 (at 150, its time starting at 60): 70, switched off.
        val still = spine[4].kids().first { it.getAttribute("name") == "s1" }
        assertEquals(listOf("1", "70/30s", "0"), listOf("lane", "offset", "enabled").map { still.getAttribute(it) })
        // The marker at 100 sits on c2 (at 90, its time starting at 0 under its time map).
        val marker = spine[2].first("marker")
        assertEquals(listOf("10/30s", "1/30s", "鼓点", "进副歌"), listOf("start", "duration", "value", "note").map { marker.getAttribute(it) })
    }

    @Test
    fun `speed, reverse and speed points become time maps`() {
        val spine = parse(export(doc).text).first("spine").kids()
        val points = spine[2].first("timeMap").kids().map { it.getAttribute("time") to it.getAttribute("value") }
        assertEquals(listOf("0s" to "0s", "60/30s" to "120/30s"), points)
        val backwards = doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.map { if (it.id == "c2") it.copy(reverse = true) else it }))
        val reversed = parse(export(backwards).text).first("spine").kids()[2].first("timeMap").kids().map { it.getAttribute("value") }
        assertEquals(listOf("120/30s", "0s"), reversed)
        val sections = doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.map {
            if (it.id == "c2") it.copy(speed = ClipSpeed.NORMAL, speedSections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(30, ClipSpeed(2f)))) else it
        }))
        val ramp = parse(export(sections).text).first("spine").kids()[2].first("timeMap").kids().map { it.getAttribute("time") to it.getAttribute("value") }
        assertEquals(listOf("0s" to "0s", "30/30s" to "30/30s", "60/30s" to "90/30s"), ramp)
    }

    @Test
    fun `media point at their files, and what FCPXML cannot hold is listed`() {
        val r = export(doc)
        val assets = parse(r.text).first("resources").getElementsByTagName("asset")
        val srcs = (0 until assets.length).map { (assets.item(it) as Element).first("media-rep").getAttribute("src") }
        assertTrue(srcs.contains("file:///storage/emulated/0/Movies/v1.mp4"))
        assertTrue(srcs.contains("file:///storage/emulated/0/Pictures/%E6%B5%B7%E6%8A%A5%20&%20%E5%B0%81%E9%9D%A2.jpg"))
        assertEquals(listOf("1 个片段的画面变换与效果"), r.left)
        // A file of the app's own (a still, a generator) keeps its URL; a clip with nothing inside closes itself.
        val still = UiFixtures.image("fz").copy(uri = "file:///data/user/0/app/files/stills/a-1.mp4", relativePath = "stills/")
        assertEquals("file:///data/user/0/app/files/stills/a-1.mp4", FcpxmlExportUseCase.fileUrl(still))
        assertTrue(Regex("<asset-clip ref=\"r\\d+\" lane=\"-2\"[^>]*/>").containsMatchIn(r.text))
        assertEquals(0, export(UiFixtures.document()).clips)
    }
}
