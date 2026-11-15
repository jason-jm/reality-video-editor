package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Generator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Generators onto the timeline: the image joins the pool once and goes on top at the playhead. */
class GeneratorUseCaseTest {

    private val use = GeneratorUseCase(SourceEditUseCase(SequentialIds()))
    private val footage = UiFixtures.video("v1", durationMs = 20_000)
    private val doc = UiFixtures.document(media = listOf(footage), clips = listOf(UiFixtures.clip("vA", start = 0, frames = 90, assetId = "v1")))

    private fun image(generator: Generator, w: Int = 1920, h: Int = 1080) = MediaAsset(
        Generator.assetId(generator, w, h), "file:///g.png", generator.assetName(w, h), MediaKind.IMAGE, 5_000, w, h, 0f, "PNG", "无音频", false, 1, "generators/",
    )

    @Test
    fun `a generator goes on top at the playhead for five seconds, its image added to the pool once`() {
        val first = use.place(doc, Generator.BLACK, image(Generator.BLACK), 30)
        val clip = first.doc.timeline.clips.single { it.assetId == "g-black-1920x1080" }
        assertEquals(Timeline.V2 to (30 to 180), clip.trackId to (clip.start to clip.end))
        assertEquals(2, first.doc.media.size)
        assertEquals(clip.id, first.edit.selectedClipId)
        assertEquals(true, first.edit.message.startsWith("已添加黑色纯色："))
        assertEquals("the clip under it stays", 0 to 90, first.doc.timeline.clip("vA")!!.let { it.start to it.end })

        val again = use.place(first.doc, Generator.BLACK, image(Generator.BLACK), 300)
        assertEquals("the same image, not a second", 2, again.doc.media.size)
        assertEquals(2, again.doc.timeline.clips.count { it.assetId == "g-black-1920x1080" })
    }

    @Test
    fun `an image made for another raster is refused`() {
        assertThrows(EditRejected::class.java) { use.place(doc, Generator.BARS, image(Generator.BARS, 1080, 1920), 0) }
    }
}
