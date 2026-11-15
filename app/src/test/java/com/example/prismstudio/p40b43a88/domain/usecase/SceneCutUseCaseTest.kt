package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.ui.SequentialIds
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SceneCutUseCaseTest {
    private val scenes = SceneCutUseCase()
    private val w = 160
    private val h = 90

    /** A smooth random texture of [size]², values about 40…220 (every seed alike in brightness). */
    private fun texture(seed: Int, size: Int = 256): FloatArray {
        val r = Random(seed)
        val coarse = FloatArray(17 * 17) { r.nextFloat() }
        return FloatArray(size * size) { i ->
            val x = (i % size) / (size / 16f)
            val y = (i / size) / (size / 16f)
            val x0 = x.toInt(); val y0 = y.toInt(); val fx = x - x0; val fy = y - y0
            fun c(a: Int, b: Int) = coarse[b * 17 + a]
            val v = (c(x0, y0) * (1 - fx) + c(x0 + 1, y0) * fx) * (1 - fy) + (c(x0, y0 + 1) * (1 - fx) + c(x0 + 1, y0 + 1) * fx) * fy
            40f + 180f * v
        }
    }

    /** A 160×90 frame of [tex] seen from ([ox], [oy]), [add] brighter, with sensor noise. */
    private fun frame(tex: FloatArray, ox: Int, oy: Int, add: Float, noise: Random): ByteArray = ByteArray(w * h) { i ->
        val x = i % w + ox
        val y = i / w + oy
        (tex[(y % 256) * 256 + (x % 256)] + add + (noise.nextFloat() - 0.5f) * 6f).toInt().coerceIn(0, 255).toByte()
    }

    /** Five shots — a still, a fast pan, one with a two-frame flash, a slow fade, a still — cut at 30, 55, 95, 115. */
    private fun programme(): LumaFrames {
        val noise = Random(1)
        val frames = ArrayList<ByteArray>()
        val a = texture(1); val b = texture(2); val c = texture(3); val d = texture(4); val e = texture(5)
        repeat(30) { frames += frame(a, 10, 10, 0f, noise) }
        repeat(25) { frames += frame(b, 3 + it * 4, 5 + it, 0f, noise) }             // pans 4 px a frame
        repeat(40) { frames += frame(c, 20, 20, if (it == 12 || it == 13) 70f else 0f, noise) } // a flash
        repeat(20) { frames += frame(d, 30, 15, -it * 2f, noise) }                 // fades down
        repeat(35) { frames += frame(e, 0, 0, 0f, noise) }
        return LumaFrames(w, h, 30.0, 0, frames)
    }

    @Test
    fun `hard cuts are found, and pans, flashes and fades are not`() {
        assertEquals(listOf(30, 55, 95, 115), scenes.detect(programme()))
    }

    @Test
    fun `a single shot has no cuts, and shots shorter than the minimum keep only the stronger cut`() {
        val noise = Random(2)
        val tex = texture(9)
        assertEquals(emptyList<Int>(), scenes.detect(LumaFrames(w, h, 30.0, 0, List(60) { frame(tex, it, 0, 0f, noise) })))
        // Shots of 30, 4 and 30 frames: the 4-frame shot is too short, so only one of its cuts stays.
        val a = texture(11); val b = texture(12); val c = texture(13)
        val frames = List(30) { frame(a, 0, 0, 0f, noise) } + List(4) { frame(b, 0, 0, 0f, noise) } + List(30) { frame(c, 0, 0, 0f, noise) }
        assertEquals(1, scenes.detect(LumaFrames(w, h, 30.0, 0, frames)).size)
    }

    @Test
    fun `cuts land on the timeline through the clip's in point and speed`() {
        val frames = LumaFrames(w, h, 30.0, 60, List(150) { ByteArray(1) })
        // File frame 60 + 30 = 90 (3 s); the clip shows the file from 2 s at 30 fps, starting at timeline frame 100.
        val clip = UiFixtures.clip("v", start = 100, frames = 200, assetId = "v1").copy(sourceInMs = 2_000)
        assertEquals(listOf(130, 155), scenes.timelineFrames(clip, frames, listOf(30, 55), 30))
        // At double speed a second of file is half a second of timeline; cuts too near the ends are dropped.
        val fast = clip.copy(speed = ClipSpeed(2f), durationFrames = 100)
        assertEquals(listOf(115), scenes.timelineFrames(fast, frames, listOf(3, 30, 186), 30))
    }

    @Test
    fun `the clip is cut into one piece per shot, its sound with it`() {
        val v = UiFixtures.clip("v", start = 0, frames = 150, assetId = "v1", linkId = "L")
        val a = UiFixtures.clip("a", start = 0, frames = 150, trackId = Timeline.A1, assetId = "v1", linkId = "L")
        val timeline = UiFixtures.document(media = listOf(UiFixtures.video("v1")), clips = listOf(v, a)).timeline
        val result = scenes.split(timeline, "v", listOf(30, 55, 95, 115), SplitClipUseCase(SequentialIds()), 30)
        val pictures = result.timeline.clips.filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
        assertEquals(listOf(0, 30, 55, 95, 115), pictures.map { it.start })
        assertEquals(listOf(30, 25, 40, 20, 35), pictures.map { it.durationFrames })
        assertEquals(5, result.timeline.clips.count { it.trackId == Timeline.A1 })
        assertEquals("检测到 4 处场景剪切，已切成 5 段", result.message)
        try {
            scenes.split(timeline, "v", emptyList(), SplitClipUseCase(SequentialIds()), 30)
            fail("no cuts")
        } catch (e: EditRejected) {
            assertEquals("没有找到场景剪切：片段是一个连续的镜头", e.message)
        }
    }

    @Test
    fun `only footage played forward without speed points can be searched`() {
        val clip = UiFixtures.clip("v", start = 0, frames = 30, assetId = "v1")
        assertNull(scenes.unavailable(clip, UiFixtures.video("v1")))
        assertEquals("只有视频片段可以检测场景剪切", scenes.unavailable(clip, UiFixtures.image("im")))
        assertEquals("倒放的片段不能检测场景剪切", scenes.unavailable(clip.copy(reverse = true), UiFixtures.video("v1")))
    }
}
