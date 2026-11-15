package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's Relink Media: offline media found again on the device, or pointed at a picked file, keeping their clips. */
class RelinkMediaUseCaseTest {

    private val relink = RelinkMediaUseCase()

    /** A 10 s, 1 MiB clip that was in DCIM/Camera/ and is gone. */
    private val lost = assetOf("a", name = "Run.mp4", durationMs = 10_000, offline = true)
        .copy(flags = listOf(FlagColor.RED), binId = "b1", metadata = ClipMetadata(scene = "3"), inputColor = InputColorSpace.HLG)

    private fun file(
        name: String,
        path: String = "Movies/Archive/",
        durationMs: Long = 10_000,
        size: Long = 1L shl 20,
        kind: MediaKind = MediaKind.VIDEO,
    ) = DeviceMediaItem("d-$path$name", "content://media/new/$path$name", name, kind, durationMs, size, path, 1920, 1080)

    @Test
    fun `a moved file is found by its name, checked by length and size, the surest first and its old folder before others`() {
        val device = listOf(
            file("run.MP4", path = "Movies/Other/", durationMs = 10_030, size = 999),
            file("Run.mp4"),
            file("Run.mp4", path = "DCIM/Camera/", durationMs = 9_990, size = 5),
            file("Run (1).mp4", path = "Download/"),
            file("Run.mp4", path = "Music/", durationMs = 7_000),
            file("Other.mp4", durationMs = 10_000, size = 4),
            file("Run.mp4", path = "Sounds/", kind = MediaKind.AUDIO),
        )
        val found = relink.candidates(lost, device)
        assertEquals(
            listOf(
                "Movies/Archive/Run.mp4 · 同名，大小和时长一致",
                // Same name and length: its old folder ahead of another.
                "DCIM/Camera/Run.mp4 · 同名，时长一致",
                "Movies/Other/run.MP4 · 同名，时长一致",
                "Download/Run (1).mp4 · 改过名，大小和时长一致",
                "Music/Run.mp4 · 同名，但时长或大小不同",
            ),
            found.map { it.label },
        )
        assertEquals("the surest is taken without asking", "Movies/Archive/", relink.pick(found)!!.item.relativePath)
        assertNull("only the name agrees: the user decides", relink.pick(relink.candidates(lost, listOf(file("Run.mp4", durationMs = 3_000)))))
        assertEquals(emptyList<RelinkMediaUseCase.Candidate>(), relink.candidates(lost, listOf(file("Else.mp4", durationMs = 4_000))))
        // Lengths agree within 1 % or 50 ms.
        assertTrue(relink.sameLength(10_000, 10_090))
        assertFalse(relink.sameLength(10_000, 10_200))
        assertTrue(relink.sameLength(1_000, 1_050))
    }

    @Test
    fun `a picture is matched by its name and size, a common name alone never taken`() {
        val photo = assetOf("p", kind = MediaKind.IMAGE, name = "IMG_0001.jpg", offline = true)
        val found = relink.candidates(photo, listOf(
            file("IMG_0001.jpg", path = "Pictures/", size = 77, kind = MediaKind.IMAGE),
            file("IMG_0001.jpg", path = "DCIM/Old/", kind = MediaKind.IMAGE),
        ))
        assertEquals(listOf(RelinkMediaUseCase.Match.SAME_FILE, RelinkMediaUseCase.Match.NAME_ONLY), found.map { it.match })
    }

    @Test
    fun `relinking keeps the media's id, clips, flags, bin, slate and input colour, and takes the new file's facts`() {
        val doc = projectOf(timeline = timelineOf(clipOf("c", 0, 90, assetId = "a")), media = listOf(lost, assetOf("x")))
        val probed = assetOf("tmp", name = "Run.mp4", durationMs = 10_000).copy(uri = "content://media/new/1", relativePath = "Movies/Archive/", width = 3840, height = 2160)
        val result = relink.relink(doc, "a", probed)
        val asset = result.doc.asset("a")!!
        assertEquals(listOf("content://media/new/1", "Movies/Archive/", "3840×2160"), listOf(asset.uri, asset.relativePath, asset.resolutionLabel))
        assertFalse(asset.offline)
        assertEquals(listOf(FlagColor.RED), asset.flags)
        assertEquals(listOf("b1", "3"), listOf(asset.binId, asset.metadata.scene))
        assertEquals(InputColorSpace.HLG, asset.inputColor)
        assertEquals("a", result.doc.timeline.clip("c")!!.assetId)
        assertEquals("已重新链接“Run.mp4”（Movies/Archive/）", result.message)
        assertEquals("类型不同：原素材是视频，所选文件是音频",
            assertThrows(EditRejected::class.java) { relink.relink(doc, "a", probed.copy(kind = MediaKind.AUDIO)) }.message)
    }

    @Test
    fun `a file shorter than what the clips use is relinked with a warning, counting every timeline's clips`() {
        // The clip reaches 6 s into its file at normal speed (frames 0–180 at 30 fps from 0), the other timeline's to 9 s.
        val here = timelineOf(clipOf("c", 0, 180, assetId = "a"))
        val other = com.example.prismstudio.p40b43a88.domain.model.StoredTimeline(
            "t2", "时间线 2", projectOf().format, timelineOf(clipOf("d", 0, 90, assetId = "a", sourceInMs = 6_000)), projectOf().mixer,
        )
        val doc = projectOf(timeline = here, media = listOf(lost)).copy(timelines = listOf(other.copy(id = "t1", timeline = here), other), timelineIndex = 0)
        assertEquals(9_000, relink.usedMs(doc, "a"))
        val short = assetOf("n", durationMs = 8_000).copy(uri = "content://n", relativePath = "Movies/")
        assertEquals("已重新链接“n.mp4”（Movies/）；注意：新文件比片段用到的短 1.0 秒，超出的部分没有画面和声音", relink.relink(doc, "a", short).message)
        assertNull(relink.shortfall(relink.relink(doc, "a", short.copy(durationMs = 9_000)).doc, short.copy(id = "a", durationMs = 9_000)))
    }

    @Test
    fun `several media relink as one step, saying how many were short and how many stay offline`() {
        val second = assetOf("b", name = "B.mp4", offline = true)
        val third = assetOf("c", name = "C.mp4", offline = true)
        val doc = projectOf(timeline = timelineOf(clipOf("k", 0, 300, assetId = "b", track = Timeline.V1)), media = listOf(lost, second, third))
        val result = relink.relinkAll(doc, mapOf(
            "a" to assetOf("t", name = "Run.mp4").copy(uri = "content://1"),
            "b" to assetOf("t", name = "B.mp4", durationMs = 4_000).copy(uri = "content://2"),
        ))
        assertEquals("已重新链接 2 个素材，其中 1 个比片段用到的短；还有 1 个离线", result.message)
        assertEquals(listOf(false, false, true), result.doc.media.map { it.offline })
        assertEquals("没有找到可以重新链接的文件", assertThrows(EditRejected::class.java) { relink.relinkAll(doc, emptyMap()) }.message)
    }
}
