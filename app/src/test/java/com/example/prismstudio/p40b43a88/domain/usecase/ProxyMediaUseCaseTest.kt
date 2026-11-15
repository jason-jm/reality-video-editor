package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's proxy media: which media get one, its size, what the monitors play, and the words for it. */
class ProxyMediaUseCaseTest {

    private val rules = ProxyMediaUseCase()
    private val hd = UiFixtures.video("hd")
    private val uhd = UiFixtures.video("uhd").copy(width = 3840, height = 2160)
    private val tall = UiFixtures.video("tall").copy(width = 1080, height = 1920)
    private val small = UiFixtures.video("small").copy(width = 640, height = 360)
    private val gone = UiFixtures.video("gone", offline = true)
    private val doc = UiFixtures.document(media = listOf(hd, uhd, tall, small, gone, UiFixtures.audio("au"), UiFixtures.image("im")))
    private val ready = ProxyStatus.Ready("file:///p/hd.mp4", 960, 540, 3L shl 20)

    @Test
    fun `footage online gets a proxy with a 540 short side, never larger than itself`() {
        assertEquals(listOf("hd", "uhd", "tall", "small"), rules.candidates(doc).map { it.id })
        assertEquals(960 to 540, rules.size(hd))
        assertEquals(960 to 540, rules.size(uhd))
        assertEquals(540 to 960, rules.size(tall))
        assertEquals(640 to 360, rules.size(small))
        assertEquals(960 to 540, rules.size(hd.copy(width = 1922, height = 1081)))
    }

    @Test
    fun `the monitors play ready proxies only while the project prefers them`() {
        val statuses = mapOf("hd" to ready, "uhd" to ProxyStatus.Making(0.4f), "im" to ready)
        assertEquals(emptyMap<String, String>(), rules.previewUris(doc, statuses))
        assertEquals(mapOf("hd" to "file:///p/hd.mp4"), rules.previewUris(doc.copy(useProxies = true), statuses))
        assertEquals(1, rules.readyCount(doc, statuses))
        assertEquals("1 / 4 个视频有代理；在媒体页为素材生成代理", rules.handlingNote(doc, statuses))
        assertEquals("已开：1 / 4 个视频用代理播放，渲染始终用原片", rules.handlingNote(doc.copy(useProxies = true), statuses))
        assertEquals("项目里没有需要代理的视频", rules.handlingNote(UiFixtures.document(media = listOf(UiFixtures.image("im"))), statuses))
    }

    @Test
    fun `the chip says where each proxy stands`() {
        assertEquals("无代理", rules.label(hd, ProxyStatus.None))
        assertEquals("生成中 45%", rules.label(hd, ProxyStatus.Making(0.454f)))
        assertEquals("代理 960×540 · 3 MB", rules.label(hd, ready))
        assertEquals("生成失败", rules.label(hd, ProxyStatus.Failed("解码或编码出错")))
        assertEquals("图片无需代理", rules.label(UiFixtures.image("im"), ProxyStatus.None))
        assertEquals("音频无需代理", rules.label(UiFixtures.audio("au"), ProxyStatus.None))
        assertEquals("素材离线", rules.label(gone, ProxyStatus.None))
    }

    @Test
    fun `the project's proxy handling survives its file, written only when on`() {
        assertTrue(ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc.copy(useProxies = true))).useProxies)
        assertFalse(ProjectJsonCodec.encode(doc).contains("useProxies"))
        assertFalse(ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).useProxies)
    }
}
