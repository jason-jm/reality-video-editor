package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.MixerBus
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.Timeline.Companion.A1
import com.example.prismstudio.p40b43a88.domain.model.Timeline.Companion.A2
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Fairlight's submix buses: routing, mute / solo, the fader, the meters, the program loudness and the project file. */
class BusesTest {

    private val buses = BusesUseCase()
    private val timeline = timelineOf(clipOf("X", 0, 300, track = A1, assetId = "half"), clipOf("Y", 0, 300, track = A2, assetId = "half"))
    private val base = MixerState.forTracks(timeline.tracks)
    private val names = mapOf(A1 to "A1", A2 to "A2")

    /** Both tracks on bus 1. */
    private val shared = base.copy(buses = listOf(MixerBus("b1", "总线 1")), channels = base.channels.map { it.copy(bus = "b1") })

    @Test
    fun `buses take the lowest free number and tracks, give them back when deleted, three at most`() {
        val one = buses.add(base)
        assertEquals(listOf("b1", "总线 1", "B1"), one.mixer.buses.single().let { listOf(it.id, it.name, it.short) })
        assertEquals("已添加总线 1：在轨道的名称上选择输出到它", one.message)
        val two = buses.add(one.mixer, A2, "A2")
        assertEquals("已添加总线 2，A2 输出到它", two.message)
        assertEquals("b2", two.mixer.channel(A2).bus)
        val a1 = buses.assign(two.mixer, A1, "A1", "b2")
        assertEquals("A1 输出到总线 2", a1.message)
        assertEquals("A1、A2", buses.membersLabel(a1.mixer, "b2", names))
        assertEquals("没有轨道", buses.membersLabel(a1.mixer, "b1", names))
        assertEquals("A1 已经输出到总线 2", buses.assign(a1.mixer, A1, "A1", "b2").message)
        assertEquals("A1 输出到主输出", buses.assign(a1.mixer, A1, "A1", null).message)
        val removed = buses.remove(a1.mixer, "b2", names)
        assertEquals("已删除总线 2，A1、A2 回到主输出", removed.message)
        assertEquals(listOf(null, null), removed.mixer.channels.map { it.bus })
        // The freed number is taken again; a fourth bus is refused.
        val again = buses.add(removed.mixer)
        assertEquals(listOf("b1", "b2"), again.mixer.buses.map { it.id })
        val three = buses.add(again.mixer).mixer
        assertEquals("最多 3 条总线", assertThrows(EditRejected::class.java) { buses.add(three) }.message)
        assertEquals("这条总线已不存在", assertThrows(EditRejected::class.java) { buses.assign(base, A1, "A1", "b1") }.message)
        assertEquals(listOf(12f, -60f), listOf(99f, -99f).map { db -> buses.update(shared, "b1") { it.copy(volumeDb = db) }.bus("b1")!!.volumeDb })
    }

    @Test
    fun `a bus's mute and solo act on its tracks, its fader multiplies theirs and silences them at the bottom`() {
        val onBus = base.copy(buses = listOf(MixerBus("b1", "总线 1")), channels = base.channels.map { if (it.trackId == A1) it.copy(bus = "b1") else it })
        fun with(change: (MixerBus) -> MixerBus) = buses.update(onBus, "b1", change)
        assertEquals("muted bus", listOf(false, true), listOf(A1, A2).map { with { it.copy(mute = true) }.isAudible(it) })
        assertEquals("soloed bus", listOf(true, false), listOf(A1, A2).map { with { it.copy(solo = true) }.isAudible(it) })
        val trackSolo = onBus.replace(onBus.channel(A2).copy(solo = true))
        assertEquals("a soloed track", listOf(false, true), listOf(A1, A2).map { trackSolo.isAudible(it) })
        assertEquals("both soloed", listOf(true, true), listOf(A1, A2).map { buses.update(trackSolo, "b1") { it.copy(solo = true) }.isAudible(it) })
        val faders = with { it.copy(volumeDb = -6f) }.let { it.replace(it.channel(A1).copy(volumeDb = -6f)) }
        assertEquals(10.0.pow(-12 / 20.0).toFloat(), faders.laneGain(A1), 1e-6f)
        assertEquals("A2 goes straight out", 1f, faders.laneGain(A2), 1e-6f)
        assertEquals("a bus at the bottom", 0f, with { it.copy(volumeDb = -60f) }.laneGain(A1), 0f)
        val silentTrack = with { it.copy(volumeDb = 12f) }.let { it.replace(it.channel(A1).copy(volumeDb = -60f)) }
        assertEquals("a track at the bottom stays silent under a bus at +12", 0f, silentTrack.laneGain(A1), 0f)
    }

    @Test
    fun `a bus meter reads its tracks' sum after its fader, the track meters read before it, the master through it`() {
        val half = Waveform(20, FloatArray(500) { 0.5f })
        val mixer = buses.update(shared, "b1") { it.copy(volumeDb = -6.0206f) }
        val levels = MeterLevelsUseCase()(projectOf(timeline = timeline, mixer = mixer), 30, mapOf("half" to half))
        assertEquals(-6.0206f, levels.channels.getValue(A1), 1e-3f)
        assertEquals("0.5 + 0.5, halved", -6.0206f, levels.buses.getValue("b1"), 1e-3f)
        assertEquals(-6.0206f, levels.master, 1e-3f)
        val muted = MeterLevelsUseCase()(projectOf(timeline = timeline, mixer = buses.update(shared, "b1") { it.copy(mute = true) }), 30, mapOf("half" to half))
        assertEquals(MeterLevelsUseCase.FLOOR_DB, muted.buses.getValue("b1"), 0f)
        assertEquals(MeterLevelsUseCase.FLOOR_DB, muted.master, 0f)
    }

    @Test
    fun `the program loudness follows the bus fader, a muted bus is silent`() {
        val song = timelineOf(clipOf("S", 0, 300, track = A2, assetId = "song"))
        val routed = MixerState.forTracks(song.tracks).let { m ->
            m.copy(buses = listOf(MixerBus("b1", "总线 1", volumeDb = -6f)), channels = m.channels.map { if (it.trackId == A2) it.copy(bus = "b1") else it })
        }
        val profile = LoudnessProfile(100, FloatArray(300) { 10.0.pow((-20.0 + 0.691) / 10).toFloat() }, FloatArray(300) { 0.5f })
        val meter = ProgramLoudnessUseCase()
        assertEquals(-26.0, meter(projectOf(timeline = song, mixer = routed), mapOf("song" to profile)).integratedLufs!!, 0.01)
        assertNull(meter(projectOf(timeline = song, mixer = buses.update(routed, "b1") { it.copy(mute = true) }), mapOf("song" to profile)).integratedLufs)
    }

    @Test
    fun `buses and routing survive a round trip, a track routed to a bus that is gone reads as the main output`() {
        val mixer = buses.update(shared, "b1") { it.copy(volumeDb = -3.5f, mute = true, eq = EqPreset.VOICE.eq) }
        val doc = projectOf(timeline = timeline, mixer = mixer)
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).mixer
        assertEquals(mixer.buses, reread.buses)
        assertEquals(listOf("b1", "b1"), reread.channels.map { it.bus })
        val json = org.json.JSONObject(ProjectJsonCodec.encode(doc))
        json.getJSONObject("mixer").remove("buses")
        val orphan = ProjectJsonCodec.decode(json.toString()).mixer
        assertTrue(orphan.buses.isEmpty())
        assertEquals(listOf(null, null), orphan.channels.map { it.bus })
        assertFalse("an older project has no buses", ProjectJsonCodec.encode(projectOf(timeline = timeline)).contains("\"buses\""))
    }
}
