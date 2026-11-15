package com.example.prismstudio.p40b43a88.domain.model

import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow

/** Ducking: the key's level, the envelope it gives and the gain the envelope sets. */
class TrackDuckingTest {

    private val ducking = DuckingUseCase()
    private val floor = DuckingUseCase.FLOOR_DB
    private val settings = TrackDucking(keyTrackId = Timeline.A1, thresholdDb = -40f, attackMs = 200f, releaseMs = 800f)

    /** Key levels every 100 ms: silent except where [loud] says. */
    private fun levels(blocks: Int, loud: IntRange, vararg more: IntRange) =
        FloatArray(blocks) { b -> if (b in loud || more.any { b in it }) -20f else floor }

    private fun DuckEnvelope.at(seconds: Double) = amountAt((seconds * 1_000_000).toLong())

    @Test
    fun `speech pulls the track down by the first word and lets it back up after the last`() {
        // Speech from 1.0 s until 2.0 s.
        val envelope = ducking.envelope(levels(50, 10..19), settings)
        assertEquals("before the attack", 0f, envelope.at(0.7), 1e-4f)
        assertEquals("the attack starts 200 ms ahead", 0f, envelope.at(0.8), 1e-4f)
        assertEquals(0.5f, envelope.at(0.9), 1e-4f)
        assertEquals("fully down when the speech starts", 1f, envelope.at(1.0), 1e-4f)
        assertEquals(1f, envelope.at(1.95), 1e-4f)
        assertEquals(1f, envelope.at(2.0), 1e-4f)
        assertEquals("the release takes 800 ms", 0.5f, envelope.at(2.4), 1e-4f)
        assertEquals(0f, envelope.at(2.8), 1e-4f)
        assertEquals(0f, envelope.at(4.0), 1e-4f)
        assertEquals("past the end it is up", 0f, envelope.at(60.0), 0f)
    }

    @Test
    fun `short pauses hold the track down, longer ones let it rise between the ramps`() {
        // 300 ms between the words: bridged.
        val held = ducking.envelope(levels(50, 10..14, 18..22), settings)
        assertEquals(1f, held.at(1.6), 1e-4f)
        // 600 ms between them: the release of the first meets the attack of the second.
        val gap = ducking.envelope(levels(50, 10..14, 21..25), settings)
        assertEquals("on the first one's release", 0.625f, gap.at(1.8), 1e-4f)
        assertEquals("the second one's attack is deeper", 0.5f, gap.at(2.0), 1e-4f)
        assertEquals(1f, gap.at(2.1), 1e-4f)
    }

    @Test
    fun `a key under the threshold leaves the track alone`() {
        val quiet = FloatArray(50) { -45f }
        assertTrue(ducking.envelope(quiet, settings).amounts.all { it == 0f })
        assertTrue("a lower threshold hears it", ducking.envelope(quiet, settings.copy(thresholdDb = -50f)).at(1.0) == 1f)
    }

    @Test
    fun `the dip in dB follows the envelope`() {
        val envelope = DuckEnvelope(10_000, floatArrayOf(0f, 1f, 1f, 0.5f, 0f))
        assertEquals(10f.pow(-12f / 20f), envelope.gainAt(10_000, 12f), 1e-5f)
        assertEquals("half way: half the dB", 10f.pow(-6f / 20f), envelope.gainAt(30_000, 12f), 1e-5f)
        assertEquals("between steps: interpolated", 10f.pow(-6f / 20f), envelope.gainAt(5_000, 12f), 1e-5f)
        assertEquals("no depth, no dip", 1f, envelope.gainAt(10_000, 0f), 0f)
        assertEquals(1f, envelope.gainAt(1_000_000, 12f), 0f)
        assertEquals(1f, envelope.gainAt(-5, 12f), 0f)
    }

    @Test
    fun `controls round and clamp, attack and release slide on a log scale`() {
        assertEquals(12.5f, DuckingControl.DEPTH.write(settings, 12.46f).depthDb, 0f)
        assertEquals(30f, DuckingControl.DEPTH.write(settings, 45f).depthDb, 0f)
        assertEquals(-60f, DuckingControl.THRESHOLD.write(settings, -90f).thresholdDb, 0f)
        assertEquals(123f, DuckingControl.ATTACK.write(settings, 123.4f).attackMs, 0f)
        assertEquals(0f, DuckingControl.ATTACK.position(10f), 1e-6f)
        assertEquals(1f, DuckingControl.ATTACK.position(1000f), 1e-6f)
        assertEquals("a decade per half", 100f, DuckingControl.ATTACK.valueAt(0.5f), 1e-3f)
        assertEquals(800f, DuckingControl.RELEASE.valueAt(DuckingControl.RELEASE.position(800f)), 1e-2f)
    }

    // A1: the dialogue, 0–2 s ("talk"); A2: the music, 0–5 s ("song").
    private val talk = clipOf("talk", 0, 60, track = Timeline.A1, assetId = "t")
    private val song = clipOf("song", 0, 150, track = Timeline.A2, assetId = "s")

    private fun doc(talk: Clip = this.talk, duck: TrackDucking = settings, disabled: Set<String> = emptySet()): ProjectDocument {
        val timeline = timelineOf(talk, song, disabled = disabled)
        val mixer = MixerState.forTracks(timeline.tracks).let { it.replace(it.channel(Timeline.A2).copy(ducking = duck)) }
        return projectOf(timeline = timeline, media = listOf(assetOf("t"), assetOf("s")), mixer = mixer)
    }

    /** 10 s of sound at [lufs] in every 100 ms block. */
    private fun profile(lufs: Double) = LoudnessProfile(100, FloatArray(100) { 10.0.pow((lufs + 0.691) / 10).toFloat() }, FloatArray(100) { 0.5f })

    private val profiles = mapOf("t" to profile(-20.0), "s" to profile(-14.0))

    @Test
    fun `the key is the key track's clips before its fader, as they play`() {
        val levels = ducking.keyLevels(doc(), Timeline.A1, profiles)
        assertEquals("the timeline's 5 s", 50, levels.size)
        assertEquals(-20f, levels[0], 0.01f)
        assertEquals(-20f, levels[19], 0.01f)
        assertEquals("after the clip", floor, levels[20], 0f)
        val quieter = ducking.keyLevels(doc(talk.copy(volumeDb = -6f)), Timeline.A1, profiles)
        assertEquals("its clip gain", -26f, quieter[5], 0.01f)
        val faded = ducking.keyLevels(doc(talk.copy(fadeInSeconds = 1f)), Timeline.A1, profiles)
        assertEquals("its fade (50 ms into a 1 s fade)", -20f + 20f * log10(0.05f), faded[0], 0.01f)
        val withFader = doc().let { d -> d.copy(mixer = d.mixer.replace(d.mixer.channel(Timeline.A1).copy(volumeDb = -20f, mute = true))) }
        assertEquals("not its fader or mute", -20f, ducking.keyLevels(withFader, Timeline.A1, profiles)[5], 0.01f)
        assertTrue("a disabled clip is silent", ducking.keyLevels(doc(talk.copy(enabled = false)), Timeline.A1, profiles).all { it == floor })
        assertTrue("so is a disabled track", ducking.keyLevels(doc(disabled = setOf(Timeline.A1)), Timeline.A1, profiles).all { it == floor })
        assertTrue("and media not measured yet", ducking.keyLevels(doc(), Timeline.A1, emptyMap()).all { it == floor })
    }

    @Test
    fun `only a keyed, switched on ducking from another sound track counts`() {
        assertEquals(Timeline.A1, ducking.keyOf(doc(), Timeline.A2))
        assertNull("off", ducking.keyOf(doc(duck = settings.copy(enabled = false)), Timeline.A2))
        assertNull("no key", ducking.keyOf(doc(duck = settings.copy(keyTrackId = null)), Timeline.A2))
        assertNull("itself", ducking.keyOf(doc(duck = settings.copy(keyTrackId = Timeline.A2)), Timeline.A2))
        assertNull("a picture track", ducking.keyOf(doc(duck = settings.copy(keyTrackId = Timeline.V1)), Timeline.A2))
        assertNull("a deleted track", ducking.keyOf(doc(duck = settings.copy(keyTrackId = "a9")), Timeline.A2))
        assertEquals(setOf("t"), ducking.keyAssetIds(doc()))
        assertEquals(emptySet<String>(), ducking.keyAssetIds(doc(duck = TrackDucking.OFF)))
    }

    @Test
    fun `the envelope is worked out again only when what it depends on changes`() {
        val inputs = ducking.inputs(doc(), setOf("t"))
        assertEquals("the depth applies live", inputs, ducking.inputs(doc(duck = settings.copy(depthDb = 20f)), setOf("t")))
        assertNotEquals(inputs, ducking.inputs(doc(duck = settings.copy(thresholdDb = -30f)), setOf("t")))
        assertNotEquals("a moved dialogue clip", inputs, ducking.inputs(doc(talk.copy(start = 15)), setOf("t")))
        assertNotEquals("its media measured", inputs, ducking.inputs(doc(), emptySet()))
    }

    @Test
    fun `each ducked track gets the envelope of its key`() {
        val analyses = ducking.analyses(doc(), profiles)
        assertEquals(setOf(Timeline.A2), analyses.keys)
        val envelope = analyses.getValue(Timeline.A2).envelope
        assertEquals("down while the dialogue speaks", 1f, envelope.at(1.0), 1e-4f)
        assertEquals("up again 800 ms after it", 0f, envelope.at(2.9), 1e-4f)
        assertEquals(Timeline.A1, analyses.getValue(Timeline.A2).keyTrackId)
    }

    @Test
    fun `a preset sets the four settings and keeps the switch and key, and is found again from them`() {
        val mine = TrackDucking(enabled = false, keyTrackId = Timeline.A1, depthDb = 3f)
        val deep = DuckingPreset.DEEP.apply(mine)
        assertEquals(TrackDucking(enabled = false, keyTrackId = Timeline.A1, depthDb = 24f, thresholdDb = -45f, attackMs = 50f, releaseMs = 1500f), deep)
        assertEquals(DuckingPreset.DEEP, DuckingPreset.of(deep))
        assertNull("settings of its own", DuckingPreset.of(mine))
        assertEquals("the default settings are the narrator's", DuckingPreset.VOICE_OVER, DuckingPreset.of(TrackDucking.OFF))
        DuckingPreset.entries.forEach { p ->
            val d = p.apply(TrackDucking.OFF)
            DuckingControl.entries.forEach { c -> assertEquals("${p.label} ${c.label} within its range", c.read(d), c.read(c.write(d, c.read(d))), 0f) }
        }
        assertEquals("labels distinct", DuckingPreset.entries.size, DuckingPreset.entries.map { it.label }.toSet().size)
    }
}
