package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.usecase.MediaMetadataUseCase.Draft
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's clip metadata (Shot & Scene): the slate, keywords, a note, Good Take — saved, labelled, searched, kept in the file. */
class MediaMetadataUseCaseTest {

    private val use = MediaMetadataUseCase()
    private val doc = UiFixtures.document(media = listOf(UiFixtures.video("v1", name = "Beach.mp4"), UiFixtures.audio("au")))

    @Test
    fun `keywords split at commas and enumeration marks, trimmed, each once`() {
        assertEquals(listOf("夜景", "跑步", "Drone"), use.keywords(" 夜景，跑步、Drone; drone ,, "))
        assertEquals(emptyList<String>(), use.keywords("  ， "))
    }

    @Test
    fun `saving the draft is one change with a message, an unchanged draft is refused`() {
        val r = use.apply(doc, "v1", Draft(scene = " 3 ", shot = "2A", take = "4", keywords = "夜景，跑步", notes = " 逆光 ", goodTake = true))
        val meta = r.doc.asset("v1")!!.metadata
        assertEquals(ClipMetadata("3", "2A", "4", listOf("夜景", "跑步"), "逆光", goodTake = true), meta)
        assertEquals("已更新“Beach.mp4”的场记", r.message)
        assertEquals("场 3 · 镜 2A · 条 4 · 好条", use.slateLabel(meta))
        assertEquals(listOf("关键词" to "夜景、跑步", "备注" to "逆光"), use.rows(meta))
        assertEquals(Draft("3", "2A", "4", "夜景，跑步", "逆光", true), Draft.of(meta))
        assertEquals("场记没有变化", assertThrows(EditRejected::class.java) { use.apply(r.doc, "v1", Draft.of(meta)) }.message)
        val cleared = use.apply(r.doc, "v1", Draft())
        assertEquals("已清除“Beach.mp4”的场记", cleared.message)
        assertEquals(ClipMetadata.NONE, cleared.doc.asset("v1")!!.metadata)
        assertEquals("未填写", use.slateLabel(ClipMetadata.NONE))
        assertEquals("无场次", use.slateLabel(ClipMetadata(keywords = listOf("夜景"))))
    }

    @Test
    fun `fields have limits`() {
        assertNull(use.problem(Draft(scene = "1".repeat(12))))
        assertEquals("场次、镜头、条次各最多 12 个字", use.problem(Draft(take = "1".repeat(13))))
        assertEquals("最多 12 个关键词", use.problem(Draft(keywords = (1..13).joinToString(",") { "k$it" })))
        assertEquals("每个关键词最多 20 个字", use.problem(Draft(keywords = "长".repeat(21))))
        assertEquals("备注最多 200 个字", use.problem(Draft(notes = "字".repeat(201))))
        assertEquals("备注最多 200 个字", assertThrows(EditRejected::class.java) { use.apply(doc, "v1", Draft(notes = "字".repeat(201))) }.message)
    }

    @Test
    fun `the pool search finds media by slate, keyword and note too`() {
        val tagged = use.apply(doc, "v1", Draft(scene = "12", keywords = "Night Run", notes = "逆光")).doc
        val filter = FilterMediaUseCase()
        assertEquals(listOf("v1"), filter(tagged.media, MediaBin.ALL, "night").map { it.id })
        assertEquals(listOf("v1"), filter(tagged.media, MediaBin.ALL, "逆光").map { it.id })
        assertEquals(listOf("v1"), filter(tagged.media, MediaBin.ALL, "12").map { it.id })
        assertEquals(emptyList<String>(), filter(tagged.media, MediaBin.AUDIO, "night").map { it.id })
    }

    @Test
    fun `metadata survives the project file, only set fields written`() {
        val tagged = use.apply(doc, "v1", Draft(take = "4", keywords = "夜景，跑步", goodTake = true)).doc
        val text = ProjectJsonCodec.encode(tagged)
        assertEquals(tagged.media, ProjectJsonCodec.decode(text).media)
        val meta = org.json.JSONObject(text).getJSONArray("media").getJSONObject(0).getJSONObject("meta")
        assertEquals(setOf("take", "keywords", "goodTake"), meta.keys().asSequence().toSet())
        assertEquals(false, org.json.JSONObject(text).getJSONArray("media").getJSONObject(1).has("meta"))
    }
}
