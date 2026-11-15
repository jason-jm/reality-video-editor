package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Release round R5: Deliver says which clips of a render have offline media, and what they render as. */
class OfflineRenderUseCaseTest {
    private val offline = OfflineRenderUseCase()

    // V1: "Beach" 0–90 (offline file) and "City" 90–210; A1: "Beach" sound 0–90 (offline), "Song" 0–210 (fine); V2 "Bars" 30–60 (offline).
    private val doc = UiFixtures.document(
        name = "Trip",
        media = listOf(UiFixtures.video("v1").copy(offline = true), UiFixtures.video("v2"), UiFixtures.audio("a1")),
        clips = listOf(
            UiFixtures.clip("b", start = 0, frames = 90, assetId = "v1", name = "Beach"),
            UiFixtures.clip("c", start = 90, frames = 120, assetId = "v2", name = "City"),
            UiFixtures.clip("bs", start = 0, frames = 90, trackId = Timeline.A1, assetId = "v1", name = "Beach"),
            UiFixtures.clip("m", start = 0, frames = 210, trackId = Timeline.A2, assetId = "a1", name = "Song"),
            UiFixtures.clip("o", start = 30, frames = 30, trackId = Timeline.V2, assetId = "v1", name = "Bars"),
        ),
    )

    @Test
    fun `the render's offline clips are named, with what they become`() {
        val all = offline.clips(doc, doc.render, null, null)
        assertEquals(listOf("b", "bs", "o"), all.map { it.id })
        assertEquals("3 个片段的素材离线（Beach、Bars），渲染时画面是黑场、没有声音：先到媒体页重新链接", offline.note(all))
        // A range after them: nothing offline in it.
        assertNull(offline.note(offline.clips(doc, doc.render, 100, 200)))
        // Picture only: the sound is not exported, so its offline clip does not count.
        val silentRender = doc.render.copy(includeAudio = false)
        assertEquals("2 个片段的素材离线（Beach、Bars），渲染时画面是黑场：先到媒体页重新链接", offline.note(offline.clips(doc, silentRender, null, null)))
        // Sound only.
        assertEquals("1 个片段的素材离线（Beach），渲染时没有声音：先到媒体页重新链接", offline.note(offline.clips(doc, doc.render.copy(includeVideo = false), null, null)))
    }

    @Test
    fun `offline sounds on a muted or unsoloed mixer channel do not warn about the exported sound`() {
        val audioOnly = doc.render.copy(includeVideo = false)
        val muted = doc.copy(mixer = doc.mixer.replace(doc.mixer.channel(Timeline.A1).copy(mute = true)))
        assertNull(offline.note(offline.clips(muted, audioOnly, null, null)))
        val solo = doc.copy(mixer = doc.mixer.replace(doc.mixer.channel(Timeline.A2).copy(solo = true)))
        assertNull(offline.note(offline.clips(solo, audioOnly, null, null)))
    }

    @Test
    fun `switched-off clips and media found again are not counted`() {
        val off = doc.copy(timeline = doc.timeline.replaceClip(doc.timeline.clip("o")!!.copy(enabled = false)))
        assertEquals(listOf("b", "bs"), offline.clips(off, off.render, null, null).map { it.id })
        val relinked = doc.copy(media = doc.media.map { it.copy(offline = false) })
        assertNull(offline.note(offline.clips(relinked, relinked.render, null, null)))
    }
}
