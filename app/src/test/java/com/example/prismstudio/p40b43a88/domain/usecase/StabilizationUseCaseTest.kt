package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import com.example.prismstudio.p40b43a88.domain.model.CameraPath
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationUseCaseTest {
    private val rules = StabilizationUseCase()
    private val footage = UiFixtures.video("v1", durationMs = 20_000)
    private val still = UiFixtures.image("im")

    /** 20 s at 30 fps: a slow pan with an alternating ±0.5 % shake. */
    private val path = CameraPath(30.0, FloatArray(600) { it * 0.0005f + if (it % 2 == 0) 0.005f else -0.005f }, FloatArray(600) { if (it % 3 == 0) 0.004f else 0f })

    private fun clip(stab: ClipStabilization? = ClipStabilization(), reverse: Boolean = false, speed: ClipSpeed = ClipSpeed.NORMAL, sourceInMs: Long = 0) =
        UiFixtures.clip("vA", start = 0, frames = 90, assetId = "v1").copy(stabilization = stab, reverse = reverse, speed = speed, sourceInMs = sourceInMs)

    @Test
    fun `only footage played forward can be stabilized, and only its files are analysed`() {
        assertNull(rules.unavailable(clip(), footage))
        assertEquals("只有视频片段可以稳定", rules.unavailable(clip().copy(assetId = "im"), still))
        assertEquals("倒放的片段不能稳定", rules.unavailable(clip(reverse = true), footage))
        val doc = UiFixtures.document(
            media = listOf(footage, still),
            clips = listOf(clip(), UiFixtures.clip("vB", start = 90, frames = 30, assetId = "im").copy(stabilization = ClipStabilization()),
                UiFixtures.clip("vC", start = 120, frames = 30, assetId = "v1").copy(stabilization = ClipStabilization(enabled = false))),
        )
        assertEquals(listOf(footage), rules.wanted(doc))
    }

    @Test
    fun `a clip follows the path over the file it shows, through its speed`() {
        val normal = rules.samples(clip(sourceInMs = 2_000), path, 30)
        assertEquals(91, normal.x.size)
        // Frame 10 of a clip from 2 s shows file frame 70.
        assertEquals(path.x[70], normal.cameraX[10], 1e-6f)
        val fast = rules.samples(clip(speed = ClipSpeed(2f), sourceInMs = 2_000), path, 30)
        assertEquals("twice as fast reads file frame 80 at clip frame 10", path.x[80], fast.cameraX[10], 1e-6f)
        // The steadied path loses the shake: what the picture shows changes little from frame to frame.
        var raw = 0f
        var steady = 0f
        for (f in 20 until 70) {
            raw += kotlin.math.abs(normal.cameraX[f + 1] - normal.cameraX[f])
            steady += kotlin.math.abs(normal.cameraX[f + 1] + normal.x[f + 1] - normal.cameraX[f] - normal.x[f])
        }
        assertTrue("$raw → $steady", steady < raw * 0.2f)
        assertTrue(normal.scale > 1f)
    }

    @Test
    fun `the inspector says how far the analysis is, what it found, or why it failed`() {
        assertEquals("正在分析摄像机运动 0%", rules.status(clip(), null, 30))
        assertEquals("正在分析摄像机运动 45%", rules.status(clip(), CameraAnalysis.Analysing(0.45f), 30))
        assertEquals("分析失败：无法读取这个视频的画面", rules.status(clip(), CameraAnalysis.Failed("无法读取这个视频的画面"), 30))
        val ready = CameraAnalysis.Ready(path)
        val scale = rules.plan(clip(), path, 30).scale
        assertTrue(rules.status(clip(), ready, 30).matches(Regex("画面最多移动 \\d\\.\\d%，放大 ${kotlin.math.round(scale * 100).toInt()}% 遮住边缘")))
        assertTrue(rules.status(clip(ClipStabilization(zoom = false)), ready, 30).endsWith("，边缘会露出"))
        assertEquals("没有需要去掉的抖动", rules.status(clip(), CameraAnalysis.Ready(CameraPath(30.0, FloatArray(600), FloatArray(600))), 30))
        assertEquals("", rules.status(clip(ClipStabilization(enabled = false)), ready, 30))
        assertEquals("自动", rules.zoomLabel(clip(), null, 30))
        assertEquals("不放大", rules.zoomLabel(clip(ClipStabilization(zoom = false)), ready, 30))
        assertEquals("${kotlin.math.round(scale * 100).toInt()}%", rules.zoomLabel(clip(), ready, 30))
    }

    @Test
    fun `the look steadies a stabilized clip once its file's path arrives`() {
        val doc = UiFixtures.document(media = listOf(footage), clips = listOf(clip()))
        val registry = LiveLookRegistry()
        registry.update(doc)
        assertNull("no path yet: unsteadied", registry.videoLook("vA")!!.stabX)
        registry.setStabilization(mapOf(footage.uri to path))
        assertTrue("the path changes the look", registry.update(doc))
        val look = registry.videoLook("vA")!!
        assertNotNull(look.stabX)
        val out = FloatArray(3)
        look.stabAt(10f, out)
        val expected = rules.samples(clip(), path, 30)
        assertEquals(expected.x[10], out[0], 1e-6f)
        assertEquals(expected.scale, out[2], 1e-6f)
        assertFalse("nothing changed", registry.update(doc))
        // Switched off: back to the plain look.
        registry.update(doc.copy(timeline = doc.timeline.replaceClip(clip(ClipStabilization(enabled = false)))))
        registry.videoLook("vA")!!.stabAt(10f, out)
        assertEquals(listOf(0f, 0f, 1f), out.toList())
    }

    @Test
    fun `the project file keeps the settings only when stabilization was set`() {
        val codec = ProjectJsonCodec
        val set = ClipStabilization(enabled = false, smooth = 80, strength = 60, zoom = false)
        val doc = UiFixtures.document(media = listOf(footage), clips = listOf(clip(set), UiFixtures.clip("vB", start = 90, frames = 30, assetId = "v1")))
        val text = codec.encode(doc)
        assertEquals(1, Regex("\"stab\"").findAll(text).count())
        val back = codec.decode(text)
        assertEquals(set, back.timeline.clip("vA")!!.stabilization)
        assertNull(back.timeline.clip("vB")!!.stabilization)
        assertEquals(ClipKind.VIDEO, back.timeline.clip("vA")!!.kind)
    }
}
