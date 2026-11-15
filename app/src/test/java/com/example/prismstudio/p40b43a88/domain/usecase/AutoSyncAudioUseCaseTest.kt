package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.SyncedAudio
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.ui.UiFixtures
import kotlin.math.exp
import kotlin.math.log10
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncAudioUseCaseTest {
    private val sync = AutoSyncAudioUseCase()
    private val camera = UiFixtures.video("cam", durationMs = 20_000)
    private val recorder = UiFixtures.audio("rec", durationMs = 30_000)

    /** One take's sound — syllable-like events — as a file starting [offsetMs] into it, every 5 ms. */
    private fun envelope(offsetMs: Double, lengthMs: Int, seed: Int): SoundEnvelope {
        val r = Random(31)
        val events = List(300) { Triple(r.nextDouble() * 60_000, 0.2 + r.nextDouble(), 40 + r.nextDouble() * 260) }
        val n = Random(seed)
        return SoundEnvelope(5, FloatArray(lengthMs / 5) { i ->
            val t = offsetMs + i * 5 + 2.5
            val p = 1e-6 + events.sumOf { (at, loud, decay) -> if (t >= at) loud * exp(-(t - at) / decay) else 0.0 }
            (10 * log10(p * (0.8 + 0.4 * n.nextDouble()))).toFloat()
        })
    }

    @Test
    fun `the recorder's offset is found from the two files' sound`() {
        // The recorder started 4.2 s before the camera: the camera's time t is the recorder's t + 4200 ms.
        val offset = sync.offset(camera, envelope(4_200.0, 20_000, 1), envelope(0.0, 30_000, 2))!!
        assertEquals(4_200.0, offset.toDouble(), 3.0)
        assertNull("unrelated sound", sync.offset(camera, envelope(0.0, 20_000, 1), SoundEnvelope(5, FloatArray(6_000) { -60f + (it % 7) })))
    }

    @Test
    fun `the source column says what the video's sound comes from`() {
        val media = listOf(camera, recorder, UiFixtures.image("im"))
        assertEquals(listOf(recorder), sync.candidates(camera, media))
        assertEquals("未同步：使用视频自带的声音", sync.label(camera, media))
        assertEquals("已同步「rec.m4a」：录音比画面早开始 4.200 秒", sync.label(camera.copy(syncedAudio = SyncedAudio("rec", 4_200)), media))
        assertEquals("已同步「rec.m4a」：录音比画面晚开始 0.350 秒", sync.label(camera.copy(syncedAudio = SyncedAudio("rec", -350)), media))
        assertEquals("这个视频没有自带声音，无法按波形同步", sync.unavailable(UiFixtures.video("v", hasAudio = false)))
        assertEquals("只有视频素材可以同步音频", sync.unavailable(recorder))
    }

    @Test
    fun `a synced video edited in brings the recorder's sound, lined up and kept inside its file`() {
        val synced = camera.copy(syncedAudio = SyncedAudio("rec", 4_200))
        val media = listOf(synced, recorder)
        val before = UiFixtures.document(media = media, clips = emptyList()).timeline
        // What an edit made: the picture and its own sound, 10 s from 2 s into the camera file, at frame 30.
        val v = UiFixtures.clip("v", start = 30, frames = 300, assetId = "cam", linkId = "L").copy(sourceInMs = 2_000)
        val a = UiFixtures.clip("a", start = 30, frames = 300, trackId = Timeline.A1, assetId = "cam", linkId = "L").copy(sourceInMs = 2_000)
        val after = before.copy(clips = listOf(v, a))
        val out = sync.resynced(before, after, media, 30)
        val sound = out.clip("a")!!
        assertEquals("rec", sound.assetId)
        assertEquals(6_200L, sound.sourceInMs)
        assertEquals(30 to 300, sound.start to sound.durationFrames)
        assertEquals("the link stays", "L", sound.linkId)
        assertEquals("the picture is untouched", v, out.clip("v"))

        // A recorder that started later: the sound starts where its file begins.
        val late = listOf(camera.copy(syncedAudio = SyncedAudio("rec", -3_000)), recorder)
        val trimmed = sync.resynced(before, after, late, 30).clip("a")!!
        assertEquals(30 + 30, trimmed.start)
        assertEquals(270, trimmed.durationFrames)
        assertEquals(0L, trimmed.sourceInMs)
        // A recorder that ends early: the sound ends with it.
        val short = listOf(synced, recorder.copy(durationMs = 10_000))
        val ended = sync.resynced(before, after, short, 30).clip("a")!!
        assertEquals((10_000 - 6_200) * 30 / 1000, ended.durationFrames)
        // Clips already on the timeline keep their sound.
        assertEquals(a, sync.resynced(after, after, media, 30).clip("a"))
    }

    @Test
    fun `the sync is kept in the project file`() {
        val doc = UiFixtures.document(media = listOf(camera, recorder), clips = emptyList())
        val synced = sync.apply(doc, "cam", SyncedAudio("rec", -1_234))
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(synced))
        assertEquals(SyncedAudio("rec", -1_234), back.asset("cam")!!.syncedAudio)
        assertNull(back.asset("rec")!!.syncedAudio)
        assertNull(sync.apply(synced, "cam", null).asset("cam")!!.syncedAudio)
        assertTrue(back.timeline.clips.none { it.kind == ClipKind.AUDIO })
    }
}
