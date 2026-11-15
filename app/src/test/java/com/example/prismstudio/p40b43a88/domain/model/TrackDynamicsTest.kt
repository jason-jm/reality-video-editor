package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow

/** The compressor's knee curve and envelope, the limiter's ceiling, and the sheet's controls. */
class TrackDynamicsTest {

    private val comp = TrackDynamics(compressor = true, thresholdDb = -20f, ratio = 4f)

    private fun db(gain: Double) = 20 * log10(gain)

    @Test
    fun `below the knee nothing changes, above it the level rises by one part in the ratio`() {
        assertEquals(-40.0, comp.outputDb(-40.0), 1e-9)
        assertEquals("3 dB under the threshold the soft knee has not begun", -23.0, comp.outputDb(-23.0), 1e-9)
        // 12 dB over: 12 / 4 = 3 dB over.
        assertEquals(-17.0, comp.outputDb(-8.0), 1e-9)
        // At the threshold the knee has taken off (1/4 − 1)·(W/2)²/(2W) = −0.5625 dB.
        assertEquals(-20.5625, comp.outputDb(-20.0), 1e-9)
        assertEquals(-14.0, comp.copy(makeupDb = 3f).outputDb(-8.0), 1e-9)
        assertEquals("bypassed", -8.0, comp.copy(enabled = false).outputDb(-8.0), 0.0)
    }

    @Test
    fun `the limiter holds the output under its ceiling`() {
        val limited = comp.copy(makeupDb = 10f, limiter = true, ceilingDb = -6f)
        assertEquals(-6.0, limited.outputDb(-2.0), 1e-9)
        assertEquals("quiet passages only get the make-up", -30.0, limited.outputDb(-40.0), 1e-9)
        assertEquals(-3.0 - (-2.0), TrackDynamics(limiter = true, ceilingDb = -3f).gainChangeDb(-2.0), 1e-9)
        assertTrue(limited.isActive)
        assertFalse(TrackDynamics.OFF.isActive)
        assertFalse(limited.copy(enabled = false).isActive)
    }

    @Test
    fun `a steady loud signal settles at the curve, reaching it over the attack`() {
        val rate = 48_000
        val gain = DynamicsGain(comp.copy(attackMs = 10f, releaseMs = 100f), rate)
        val peak = 10.0.pow(-8.0 / 20) // −8 dBFS: the curve takes off 9 dB
        val first10ms = (1..rate / 100).map { gain.gain(peak) }.last()
        assertEquals("one attack time in: 63 % of the way", -9.0 * (1 - Math.E.pow(-1.0)), db(first10ms), 0.05)
        val settled = (1..rate).map { gain.gain(peak) }.last()
        assertEquals(-9.0, db(settled), 0.01)
        // Quiet again: it lets go over the release.
        val quiet = 10.0.pow(-40.0 / 20)
        val after100ms = (1..rate / 10).map { gain.gain(quiet) }.last()
        assertEquals(-9.0 * Math.E.pow(-1.0), db(after100ms), 0.05)
        gain.reset()
        assertEquals(0.0, db(gain.gain(quiet)), 1e-9)
    }

    @Test
    fun `the limiter clamps a peak at once and lets go over 50 ms`() {
        val gain = DynamicsGain(TrackDynamics(limiter = true, ceilingDb = -6f), 48_000)
        assertEquals(-6.0, db(gain.gain(1.0)) + 0.0, 1e-9)
        val after = (1..2400).map { gain.gain(0.1) }.last() // 50 ms of −20 dBFS
        assertEquals(-6.0 * Math.E.pow(-1.0), db(after), 0.05)
        assertEquals("bypassed", 1.0, DynamicsGain(TrackDynamics(enabled = false, limiter = true), 48_000).gain(1.0), 0.0)
    }

    @Test
    fun `controls round and clamp, and times slide on a log scale`() {
        val d = TrackDynamics.OFF
        assertEquals(-18.4f, DynamicsControl.THRESHOLD.write(d, -18.4321f).thresholdDb, 0f)
        assertEquals(20f, DynamicsControl.RATIO.write(d, 99f).ratio, 0f)
        assertEquals(0.4f, DynamicsControl.ATTACK.write(d, 0.43f).attackMs, 0f)
        assertEquals(125f, DynamicsControl.RELEASE.write(d, 124.6f).releaseMs, 0f)
        assertEquals(-20f, DynamicsControl.CEILING.write(d, -50f).ceilingDb, 0f)
        val attack = DynamicsControl.ATTACK
        assertEquals(0f, attack.position(0.1f), 1e-6f)
        assertEquals(1f, attack.position(100f), 1e-6f)
        assertEquals(10f, attack.valueAt(attack.position(10f)), 1e-3f)
        assertEquals("linear controls pass straight through", -12f, DynamicsControl.THRESHOLD.valueAt(-12f), 0f)
    }

    @Test
    fun `each dynamics preset stays inside the sheet's ranges and is known again until a setting moves`() {
        DynamicsPreset.entries.forEach { preset ->
            DynamicsControl.entries.forEach { c ->
                val v = c.read(preset.dynamics)
                assertTrue("${preset.label} ${c.section}${c.label} = $v", v in c.min..c.max)
            }
            assertTrue("${preset.label} is on", preset.dynamics.isActive)
            assertEquals(preset, DynamicsPreset.of(preset.dynamics))
            assertEquals(preset, DynamicsPreset.of(preset.dynamics.copy(enabled = false)))
        }
        assertEquals(null, DynamicsPreset.of(DynamicsControl.RATIO.write(DynamicsPreset.VOICE.dynamics, 3.5f)))
        assertEquals("off is no preset", null, DynamicsPreset.of(TrackDynamics.OFF))
    }

    @Test
    fun `the dynamics presets do what they are named for`() {
        fun out(p: DynamicsPreset, db: Double) = p.dynamics.outputDb(db)
        assertEquals("the limiter leaves quiet sound alone", -20.0, out(DynamicsPreset.LIMITER, -20.0), 1e-9)
        assertEquals("and stops peaks at -1 dB", -1.0, out(DynamicsPreset.LIMITER, 0.0), 1e-9)
        // A 20 dB swing at the input comes out narrower the harder the preset compresses.
        fun swing(p: DynamicsPreset) = out(p, -6.0) - out(p, -26.0)
        assertTrue(swing(DynamicsPreset.HEAVY) < swing(DynamicsPreset.VOICE))
        assertTrue(swing(DynamicsPreset.VOICE) < swing(DynamicsPreset.GENTLE))
        assertTrue(swing(DynamicsPreset.GENTLE) < swing(DynamicsPreset.MUSIC))
        assertTrue(swing(DynamicsPreset.MUSIC) < 20.0)
        listOf(DynamicsPreset.VOICE, DynamicsPreset.HEAVY, DynamicsPreset.BROADCAST).forEach {
            assertTrue("${it.label} never passes its ceiling", out(it, 0.0) <= it.dynamics.ceilingDb + 1e-9)
        }
        assertTrue("quiet speech comes up with the make-up gain", out(DynamicsPreset.HEAVY, -40.0) > -40.0)
    }

    @Test
    fun `the gate turns down what falls below its threshold, four to one, at most its range`() {
        val d = TrackDynamics(gate = true, gateThresholdDb = -50f, gateRangeDb = 30f)
        assertEquals(0.0, d.gating(-40.0), 0.0)
        assertEquals("5 dB under: down 15", -15.0, d.gating(-55.0), 1e-9)
        assertEquals("far under: the range", -30.0, d.gating(-80.0), 1e-9)
        assertEquals(-55.0 - 15.0, d.outputDb(-55.0), 1e-9)
        assertTrue(d.isActive)
        assertFalse("off without gate, compressor or limiter", TrackDynamics(gate = false).isActive)
        // The compressor hears the gated level: quiet sound stays below its threshold.
        val both = d.copy(compressor = true, thresholdDb = -20f, ratio = 4f)
        assertEquals(-70.0, both.outputDb(-55.0), 1e-9)
    }

    @Test
    fun `the gate stays open through a tone's zero crossings, holds, then closes by its range`() {
        val rate = 48_000
        val d = DynamicsPreset.DIALOGUE_GATE.dynamics // −45 dB, range 20, hold 60 ms, release 150 ms
        val gain = DynamicsGain(d, rate)
        val tone = 0.1 // −20 dBFS peak
        val floor = 0.001 // −60 dBFS
        var minDuringTone = 1.0
        val trace = DoubleArray(rate * 2)
        for (i in 0 until rate * 2) {
            val t = i.toDouble() / rate
            val sample = if (t < 0.5) tone * Math.sin(2 * Math.PI * 300 * t) else floor * Math.sin(2 * Math.PI * 300 * t)
            val g = gain.gain(Math.abs(sample))
            trace[i] = g
            if (t in 0.01..0.5) minDuringTone = minOf(minDuringTone, g)
        }
        assertTrue("open through the tone, zero crossings and all: $minDuringTone", minDuringTone > 0.99)
        assertTrue("still open within the hold", trace[(0.53 * rate).toInt()] > 0.95)
        val closedDb = 20 * log10(trace[(1.6 * rate).toInt()])
        assertEquals("down by the range once closed", -20.0, closedDb, 0.5)
        // It opens again at once when sound comes back.
        repeat(rate / 100) { gain.gain(tone) }
        assertTrue(gain.gain(tone) > 0.99)
    }

    @Test
    fun `the gate hears room tone by its level, not by its peaks`() {
        // Room tone at −50 dBFS RMS has peaks around −40: a −45 dB gate must still close on it, and open for speech.
        val rate = 48_000
        val gain = DynamicsGain(DynamicsPreset.DIALOGUE_GATE.dynamics, rate)
        val r = java.util.Random(4)
        var g = 1.0
        repeat(rate) { g = gain.gain(Math.abs(r.nextGaussian() * 0.00316)) }
        // 5 dB under the −45 dB threshold, 4:1: down 15 dB (the 20 dB range is not reached).
        assertEquals("room tone turned down by its own level, not held open by its peaks", -15.0, 20 * log10(g), 1.0)
        var opened = 0.0
        repeat(rate / 10) { opened = gain.gain(Math.abs(0.1 * Math.sin(2 * Math.PI * 200 * it / rate))) }
        assertTrue("a word opens it: $opened", opened > 0.98)
    }

    @Test
    fun `the gate's settings go into the project file only once touched`() {
        val codec = com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
        val set = TrackDynamics(gate = true, gateThresholdDb = -42f, gateRangeDb = 25f, gateHoldMs = 80f, gateReleaseMs = 300f, compressor = true)
        val doc = com.example.prismstudio.p40b43a88.ui.UiFixtures.document(media = emptyList(), clips = emptyList())
        val withGate = doc.copy(mixer = doc.mixer.replace(doc.mixer.channel(Timeline.A1).copy(dynamics = set)))
        assertEquals(set, codec.decode(codec.encode(withGate)).mixer.channel(Timeline.A1).dynamics)
        val compressorOnly = doc.copy(mixer = doc.mixer.replace(doc.mixer.channel(Timeline.A1).copy(dynamics = TrackDynamics(compressor = true))))
        assertFalse("no gate written", codec.encode(compressorOnly).contains("\"gate\""))
    }
}
