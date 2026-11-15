package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.UserBin
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's bins in the media pool: make, file media, rename, delete (media back to the root). */
class MediaBinsUseCaseTest {

    private val use = MediaBinsUseCase(SequentialIds())
    private val doc = UiFixtures.document(media = listOf(UiFixtures.video("v1"), UiFixtures.audio("au"), UiFixtures.image("im")))

    @Test
    fun `new bins are numbered after the highest`() {
        val one = use.create(doc)
        assertEquals(listOf("媒体夹 1"), one.doc.bins.map { it.name })
        assertEquals("已新建媒体夹 1", one.message)
        assertEquals(one.doc.bins.single().id, one.binId)
        val renamed = use.rename(use.create(one.doc).doc, one.binId!!, "采访")
        assertEquals("媒体夹 3", use.create(renamed.doc).doc.bins.last().name)
    }

    @Test
    fun `media are filed in one bin at a time and can go back to the root`() {
        val made = use.create(use.create(doc).doc).doc
        val (a, b) = made.bins.map { it.id }
        val inA = use.move(made, "v1", a)
        assertEquals("已将“v1.mp4”放入“媒体夹 1”", inA.message)
        assertEquals(a, inA.doc.asset("v1")!!.binId)
        val inB = use.move(inA.doc, "v1", b).doc
        assertEquals(listOf(0, 1), listOf(use.count(inB, a), use.count(inB, b)))
        assertEquals("媒体夹 2", use.binOf(inB, "v1")!!.name)
        val out = use.move(inB, "v1", null)
        assertEquals("已将“v1.mp4”移出媒体夹", out.message)
        assertNull(out.doc.asset("v1")!!.binId)
        assertEquals("“v1.mp4”已在“媒体夹 2”里", assertThrows(EditRejected::class.java) { use.move(inB, "v1", b) }.message)
        assertEquals("“au.m4a”不在媒体夹里", assertThrows(EditRejected::class.java) { use.move(inB, "au", null) }.message)
    }

    @Test
    fun `a bin's name must be new, short and not empty`() {
        val made = use.create(use.create(doc).doc).doc
        val (a, b) = made.bins.map { it.id }
        assertEquals("名称不能为空", use.problem(made, a, "  "))
        assertEquals("已有同名的媒体夹", use.problem(made, a, "媒体夹 2"))
        assertEquals("名称最多 20 个字", use.problem(made, a, "长".repeat(21)))
        assertNull(use.problem(made, a, "  B 机位 "))
        val r = use.rename(made, a, "  B 机位 ")
        assertEquals(UserBin(a, "B 机位"), r.doc.bins.first())
        assertEquals("已将“媒体夹 1”重命名为“B 机位”", r.message)
        assertEquals("名称没有变化", assertThrows(EditRejected::class.java) { use.rename(made, b, "媒体夹 2") }.message)
    }

    @Test
    fun `deleting a bin keeps its media, back in the root`() {
        val made = use.create(doc).doc
        val id = made.bins.single().id
        val filed = use.move(use.move(made, "v1", id).doc, "im", id).doc
        val gone = use.delete(filed, id)
        assertEquals("已删除媒体夹“媒体夹 1”，其中 2 个素材回到媒体池", gone.message)
        assertEquals(emptyList<UserBin>(), gone.doc.bins)
        assertEquals(3, gone.doc.media.size)
        assertEquals(listOf(null, null, null), gone.doc.media.map { it.binId })
        assertEquals("已删除媒体夹“媒体夹 1”", use.delete(made, id).message)
        assertThrows(EditRejected::class.java) { use.delete(made, "nope") }
    }
}
