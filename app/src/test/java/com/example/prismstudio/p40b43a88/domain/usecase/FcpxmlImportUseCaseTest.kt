package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import com.example.prismstudio.p40b43a88.ui.UiFixtures.clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Import → Timeline → FCPXML: Prism's own export comes back as it was; another editor's file maps onto tracks. */
class FcpxmlImportUseCaseTest {

    private val import = FcpxmlImportUseCase(SequentialIds())
    private val media = listOf(UiFixtures.video("v1"), UiFixtures.video("v2"), UiFixtures.audio("au"), UiFixtures.image("im"))

    @Test
    fun `an exported timeline imports back as it was`() {
        val doc = UiFixtures.document(
            media = media,
            clips = listOf(
                clip("c1", 0, 60, assetId = "v1", linkId = "L1").copy(sourceInMs = 1000),
                clip("c1a", 0, 60, trackId = Timeline.A1, assetId = "v1", linkId = "L1").copy(sourceInMs = 1000, volumeDb = -6f, fadeInSeconds = 0.5f),
                clip("c2", 90, 60, assetId = "v2").copy(speed = ClipSpeed(2f)),
                clip("c3", 150, 60, assetId = "v1").copy(sourceInMs = 2000, reverse = true, transitionIn = Transition(TransitionType.CROSS_DISSOLVE, 10, alignment = TransitionAlignment.CENTER)),
                clip("c4", 210, 60, assetId = "v2").copy(speedSections = listOf(SpeedSection(30, ClipSpeed.NORMAL), SpeedSection(30, ClipSpeed(2f)))),
                clip("t1", 30, 60, trackId = Timeline.V2, kind = ClipKind.TITLE).copy(title = TitleStyle("片头", sizePx = 96, color = TitleColor.YELLOW)),
                clip("s1", 160, 20, trackId = Timeline.V2, assetId = "im").copy(enabled = false),
                clip("m1", 0, 270, trackId = Timeline.A2, assetId = "au"),
            ),
            markers = listOf(Marker("mk", 100, "鼓点", note = "进副歌", durationFrames = 30)),
        )
        val text = FcpxmlExportUseCase()(doc).text
        val r = import(doc, text, "x.fcpxml")
        assertEquals("已从 FCPXML 导入 8 个片段，新建并打开“时间线 1 (2)”", r.message)
        val back = r.doc.timeline
        fun key(c: com.example.prismstudio.p40b43a88.domain.model.Clip) = listOf(c.trackId, c.kind, c.start, c.durationFrames, c.assetId, c.sourceInMs, c.speed, c.reverse, c.speedSections, c.enabled, c.volumeDb, c.fadeInSeconds, c.title?.text, c.title?.sizePx, c.title?.color)
        assertEquals(doc.timeline.clips.map(::key).sortedBy { it.toString() }, back.clips.map(::key).sortedBy { it.toString() })
        // The picture and its sound are linked again; the dissolve is back, centred on the cut.
        val c1 = back.clips.first { it.trackId == Timeline.V1 && it.start == 0 }
        assertEquals(c1.linkId, back.clips.first { it.trackId == Timeline.A1 && it.start == 0 }.linkId)
        assertTrue(c1.linkId != null)
        val c3 = back.clips.first { it.trackId == Timeline.V1 && it.start == 150 }
        val span = TransitionHandles.span(doc, doc.timeline.clips.first { it.id == "c3" }, 30)!!
        assertEquals(span.frames, c3.transitionIn!!.frames)
        assertEquals(TransitionType.CROSS_DISSOLVE, c3.transitionIn!!.type)
        assertEquals(listOf(Triple(100, "鼓点", 30)), back.markers.map { Triple(it.frame, it.label, it.durationFrames) })
        assertEquals("进副歌", back.markers.single().note)
    }

    @Test
    fun `another editor's file maps onto tracks, with what it cannot take named`() {
        val text = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE fcpxml>
<fcpxml version="1.9">
  <resources>
    <format id="r1" frameDuration="100/3000s" width="1920" height="1080"/>
    <asset id="r2" name="Beach" start="0s" duration="20s" hasVideo="1" hasAudio="1" format="r1"><media-rep kind="original-media" src="file:///Volumes/Media/v1.mp4"/></asset>
    <asset id="r3" name="Song" start="0s" duration="60s" hasAudio="1"><media-rep kind="original-media" src="file:///Volumes/Media/au.m4a"/></asset>
    <asset id="r4" name="Lost" start="0s" duration="20s" hasVideo="1" format="r1"><media-rep kind="original-media" src="file:///Volumes/Media/gone%20away.mov"/></asset>
    <asset id="r5" name="City" start="0s" duration="20s" hasVideo="1" hasAudio="1" format="r1"><media-rep kind="original-media" src="file:///Volumes/Media/v2.mp4"/></asset>
  </resources>
  <library><event name="E"><project name="Edit A">
    <sequence format="r1" duration="12s" tcStart="3600s" tcFormat="NDF">
      <spine>
        <clip name="Beach shot" offset="3600s" start="10s" duration="4s">
          <video ref="r2" offset="0s" start="0s" duration="20s"/>
          <audio ref="r2" lane="-1" offset="10s" start="10s" duration="4s"/>
          <chapter-marker start="11s" duration="1/30s" value="第一章"/>
        </clip>
        <transition name="Cross Dissolve" offset="3603.5s" duration="1s"/>
        <asset-clip ref="r5" offset="3604s" name="City" start="2s" duration="4s" format="r1">
          <adjust-transform scale="1.2 1.2"/>
          <asset-clip ref="r3" lane="-2" offset="2s" name="Song" start="0s" duration="8s"><adjust-volume amount="-9dB"/></asset-clip>
          <asset-clip ref="r4" lane="1" offset="3s" name="Lost" start="0s" duration="1s"/>
        </asset-clip>
        <gap offset="3608s" start="0s" duration="4s">
          <title lane="1" offset="0s" name="T" start="0s" duration="2s"><text><text-style ref="a">Hello</text-style></text><text-style-def id="a"><text-style font="Helvetica" fontSize="60" fontColor="0.95 0.33 0.24 1"/></text-style-def></title>
          <ref-clip ref="r9" lane="2" offset="1s" duration="1s"/>
        </gap>
      </spine>
    </sequence>
  </project></event></library>
</fcpxml>"""
        val doc = UiFixtures.document(media = media)
        val r = import(doc, text, "edit.fcpxml")
        val t = r.doc.timeline
        assertEquals("Edit A", r.doc.timelineName)
        // tcStart 3600 s is the timeline's zero; the clip shows its video from 10 s, its sound on A1 (linked).
        val beach = t.clips.first { it.trackId == Timeline.V1 && it.assetId == "v1" }
        assertEquals(listOf<Any>(0, 120, 10_000L), listOf(beach.start, beach.durationFrames, beach.sourceInMs))
        val beachSound = t.clips.first { it.trackId == Timeline.A1 && it.assetId == "v1" }
        assertEquals(listOf<Any>(0, 120, 10_000L), listOf(beachSound.start, beachSound.durationFrames, beachSound.sourceInMs))
        // City: its own sound comes along on A1, linked; a centred 1 s dissolve into it.
        val city = t.clips.first { it.trackId == Timeline.V1 && it.assetId == "v2" }
        assertEquals(listOf<Any>(120, 120, 2_000L), listOf(city.start, city.durationFrames, city.sourceInMs))
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 30, alignment = TransitionAlignment.CENTER), city.transitionIn)
        assertEquals(city.linkId, t.clips.first { it.trackId == Timeline.A1 && it.assetId == "v2" }.linkId)
        val song = t.clips.first { it.assetId == "au" }
        assertEquals(listOf(Timeline.A2, 120, 240, -9f), listOf(song.trackId, song.start, song.durationFrames, song.volumeDb))
        val title = t.clips.first { it.kind == ClipKind.TITLE }
        assertEquals(listOf<Any?>(Timeline.V2, 240, 60, "Hello", 60, TitleColor.RED), listOf(title.trackId, title.start, title.durationFrames, title.title?.text, title.title?.sizePx, title.title?.color))
        assertEquals(listOf(30 to "第一章"), t.markers.map { it.frame to it.label })
        assertEquals(listOf("1 个素材不在媒体池里（gone away.mov）", "1 个复合或多机位片段", "1 个片段的效果与变换"), r.left)
        assertTrue(r.message.endsWith("；未导入：1 个素材不在媒体池里、1 个复合或多机位片段、1 个片段的效果与变换"))
    }

    @Test
    fun `overlaps move up a track, and files it cannot use are refused`() {
        val text = """<fcpxml version="1.10"><resources>
            <asset id="r2" name="v1.mp4" duration="20s" hasVideo="1"><media-rep src="file:///x/v1.mp4"/></asset></resources>
            <library><event><project name="P"><sequence duration="10s"><spine>
              <asset-clip ref="r2" offset="0s" start="0s" duration="10s">
                <asset-clip ref="r2" lane="1" offset="1s" start="0s" duration="2s"/>
                <asset-clip ref="r2" lane="1" offset="2s" start="0s" duration="2s"/>
                <asset-clip ref="r2" lane="1" offset="3.5s" start="0s" duration="1.5s"/>
                <asset-clip ref="r2" lane="1" offset="2.5s" start="0s" duration="1s"/>
              </asset-clip></spine></sequence></project></event></library></fcpxml>"""
        val r = import(UiFixtures.document(media = media), text, "p.fcpxml")
        assertEquals(listOf("v2", "v3", "v4", "v2"), r.doc.timeline.clips.filter { it.trackId != Timeline.V1 }.sortedBy { it.start }.map { it.trackId })
        assertEquals(emptyList<String>(), r.left)
        assertEquals("v4", r.doc.timeline.tracks.first().id)
        // Past eight tracks of a kind an overlapping clip has nowhere to go.
        val stacked = (0 until 8).joinToString("") { """<asset-clip ref="r2" lane="1" offset="1s" start="0s" duration="2s"/>""" }
        val full = import(UiFixtures.document(media = media), text.replace("""<asset-clip ref="r2" lane="1" offset="1s" start="0s" duration="2s"/>""", stacked), "q.fcpxml")
        assertEquals(listOf("3 个片段（重叠处每类最多 8 条轨道）"), full.left)
        assertEquals("v8", full.doc.timeline.tracks.first().id)
        assertEquals("不是 FCPXML 文件", assertThrows(EditRejected::class.java) { import(UiFixtures.document(media = media), "<xmeml/>", "a.xml") }.message)
        assertEquals("无法读取 FCPXML：文件不是有效的 XML", assertThrows(EditRejected::class.java) { import(UiFixtures.document(media = media), "TITLE: x", "a.edl") }.message)
        val nothing = """<fcpxml version="1.10"><resources><asset id="r2" name="zz.mp4" duration="2s" hasVideo="1"/></resources>
            <library><event><project><sequence><spine><asset-clip ref="r2" offset="0s" duration="1s"/></spine></sequence></project></event></library></fcpxml>"""
        assertEquals("FCPXML 里没有能在媒体池中找到的素材：媒体池里没有“zz.mp4”", assertThrows(EditRejected::class.java) { import(UiFixtures.document(media = media), nothing, "n.fcpxml") }.message)
        assertEquals(1.0 / 30, FcpxmlImportUseCase.time("1001/30030s"), 1e-9)
    }
}
