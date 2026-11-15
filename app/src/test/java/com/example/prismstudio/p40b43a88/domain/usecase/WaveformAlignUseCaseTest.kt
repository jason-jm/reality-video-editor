package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlin.math.exp
import kotlin.math.log10
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WaveformAlignUseCaseTest {
    private val align = WaveformAlignUseCase()

    /** A take: sound events at random moments (claps, words), each decaying, over a quiet room — power at time t (ms). */
    private class Scene(seed: Int, val lengthMs: Int) {
        private val r = Random(seed)
        private val events = List(lengthMs / 700) { r.nextDouble() * lengthMs to (0.2 + r.nextDouble()) }
        private val decays = List(events.size) { 40.0 + r.nextDouble() * 300 }
        fun power(t: Double): Double {
            var p = 1e-6
            events.forEachIndexed { i, (at, loud) -> if (t >= at) p += loud * exp(-(t - at) / decays[i]) }
            return p
        }
    }

    /** The scene as a file starting [offsetMs] into it, [gainDb] louder, with its own room noise, every 5 ms. */
    private fun envelope(scene: Scene, offsetMs: Double, lengthMs: Int, gainDb: Double, noiseSeed: Int): SoundEnvelope {
        val noise = Random(noiseSeed)
        val g = Math.pow(10.0, gainDb / 10)
        return SoundEnvelope(5, FloatArray(lengthMs / 5) { i ->
            val p = scene.power(offsetMs + i * 5 + 2.5) * g * (0.8 + 0.4 * noise.nextDouble()) + 2e-6 * noise.nextDouble()
            (10 * log10(p)).toFloat()
        })
    }

    @Test
    fun `the lag between two recordings of one take is found to a couple of milliseconds`() {
        val scene = Scene(3, 60_000)
        val camera = envelope(scene, 0.0, 40_000, 0.0, 1)
        // The recorder started 2.347 s later into the take, 9 dB quieter, with its own noise.
        val recorder = envelope(scene, 2_347.0, 40_000, -9.0, 2)
        val m = align.match(camera, 0, 40_000, recorder)!!
        assertEquals(2_347.0, m.lagMs, 2.5)
        assertTrue("score ${m.score}", m.score > 0.5f)
        // A stretch of the camera file finds the same lag; one that started earlier gives a negative lag.
        assertEquals(2_347.0, align.match(camera, 10_000, 20_000, recorder)!!.lagMs, 2.5)
        val early = envelope(scene, 0.0, 30_000, 0.0, 4)
        val late = envelope(scene, 1_203.0, 30_000, 3.0, 5)
        assertEquals(-1_203.0, align.match(late, 0, 30_000, early)!!.lagMs, 2.5)
    }

    @Test
    fun `unrelated sound is not matched`() {
        val a = envelope(Scene(7, 30_000), 0.0, 30_000, 0.0, 1)
        val b = envelope(Scene(8, 30_000), 0.0, 30_000, 0.0, 2)
        assertNull(align.match(a, 0, 30_000, b))
    }

    private val camera = UiFixtures.video("cam", durationMs = 40_000)
    private val recorder = UiFixtures.audio("rec", durationMs = 40_000)
    private val assets = listOf(camera, recorder).associateBy { it.id }

    private fun envelopes(lagMs: Double): Map<String, SoundEnvelope> {
        val scene = Scene(11, 80_000)
        return mapOf(camera.uri to envelope(scene, 0.0, 40_000, 0.0, 1), recorder.uri to envelope(scene, lagMs, 40_000, -6.0, 2))
    }

    @Test
    fun `sound alone lines up to the millisecond, its in point taking the part of a frame`() {
        // The camera picture and its sound at 1 s; the recorder's sound on A2 at 0.
        val v = UiFixtures.clip("v", start = 30, frames = 300, assetId = "cam", linkId = "L")
        val a = UiFixtures.clip("a", start = 30, frames = 300, trackId = Timeline.A1, assetId = "cam", linkId = "L")
        val r = UiFixtures.clip("r", start = 0, frames = 600, trackId = Timeline.A2, assetId = "rec")
        val timeline = UiFixtures.document(media = assets.values.toList(), clips = listOf(v, a, r)).timeline
        val result = align.align(timeline, listOf(listOf(v, a), listOf(r)), assets, envelopes(2_347.0), 30)
        val placed = result.timeline.clip("r")!!
        // In step with the camera: (start − in point) = 1000 ms − 0 + 2347 ms.
        assertEquals(3_347.0, placed.start * 1000.0 / 30 - placed.sourceInMs, 3.0)
        assertTrue(placed.sourceInMs in 0..33)
        assertEquals(listOf("r"), result.moved.map { it.name })
        assertTrue(result.moved.single().deltaMs in 3_340..3_355)
        assertEquals("the reference stays", 30, result.timeline.clip("v")!!.start)
    }

    @Test
    fun `a picture moves with its linked sound by whole frames`() {
        // The recorder is the reference this time; the camera clip and its sound move.
        val r = UiFixtures.clip("r", start = 60, frames = 600, trackId = Timeline.A2, assetId = "rec")
        val v = UiFixtures.clip("v", start = 0, frames = 300, assetId = "cam", linkId = "L")
        val a = UiFixtures.clip("a", start = 0, frames = 300, trackId = Timeline.A1, assetId = "cam", linkId = "L")
        val timeline = UiFixtures.document(media = assets.values.toList(), clips = listOf(v, a, r)).timeline
        val result = align.align(timeline, listOf(listOf(r), listOf(v, a)), assets, envelopes(2_347.0), 30)
        val pv = result.timeline.clip("v")!!
        val pa = result.timeline.clip("a")!!
        assertEquals("picture and sound stay together", pv.start, pa.start)
        // Whole frames only: the trimmed head moves the in point by a whole number of frames.
        val frames = Math.round(pv.sourceInMs * 30 / 1000.0)
        assertEquals(Math.round(frames * 1000.0 / 30), pv.sourceInMs)
        // The camera's time 2347 ms later than the recorder's: (start − in) = 2000 ms − 0 − 2347 ms → before the timeline's start,
        // so the head is trimmed: start 0 and the in point where the clip would have been.
        val offsetMs = pv.start * 1000.0 / 30 - pv.sourceInMs
        assertEquals(2_000.0 - 2_347.0, offsetMs, 1000.0 / 30)
    }

    @Test
    fun `clips that cannot be matched are named, and a move onto a clip that stays is refused`() {
        val v = UiFixtures.clip("v", start = 30, frames = 300, assetId = "cam", linkId = "L")
        val a = UiFixtures.clip("a", start = 30, frames = 300, trackId = Timeline.A1, assetId = "cam", linkId = "L")
        val r = UiFixtures.clip("r", start = 0, frames = 600, trackId = Timeline.A2, assetId = "rec")
        val title = UiFixtures.clip("t", start = 0, frames = 60, trackId = Timeline.V2, kind = ClipKind.TITLE)
        val doc = UiFixtures.document(media = assets.values.toList(), clips = listOf(v, a, r, title))
        val result = align.align(doc.timeline, listOf(listOf(v, a), listOf(title), listOf(r)), assets, envelopes(2_347.0), 30)
        assertEquals(listOf("「${title.name}」没有声音"), result.skipped)
        // A clip sitting where the recorder must go stays, so the move is refused.
        val blocker = UiFixtures.clip("b", start = 120, frames = 30, trackId = Timeline.A2, assetId = "rec")
        try {
            align.align(doc.timeline.copy(clips = doc.timeline.clips + blocker), listOf(listOf(v, a), listOf(r)), assets, envelopes(2_347.0), 30)
            fail("the move lands on a clip that stays")
        } catch (e: EditRejected) {
            assertTrue(e.message!!, e.message!!.contains("重叠"))
        }
        val fast = r.copy(speed = ClipSpeed(2f))
        try {
            align.align(doc.timeline, listOf(listOf(v, a), listOf(fast)), assets, envelopes(2_347.0), 30)
            fail("a retimed clip cannot be matched")
        } catch (e: EditRejected) {
            assertTrue(e.message!!, e.message!!.contains("变速"))
        }
    }
}
