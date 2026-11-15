package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The track EQ's bands, as the curve draws them and the mixer hears them (Audio EQ Cookbook biquads). */
class TrackEqTest {

    @Test
    fun `a flat or bypassed EQ changes nothing`() {
        assertTrue(TrackEq.FLAT.isFlat)
        assertFalse(TrackEq.FLAT.isActive)
        assertEquals(0.0, TrackEq.FLAT.responseDb(1000.0), 1e-9)
        val boosted = TrackEq(highDb = 6f)
        assertTrue(boosted.isActive)
        assertFalse(boosted.copy(enabled = false).isActive)
        assertEquals(0.0, boosted.copy(enabled = false).responseDb(15_000.0), 1e-9)
    }

    @Test
    fun `shelves reach their gain beyond the corner and leave the other end alone`() {
        val low = TrackEq(lowHz = 120f, lowDb = 6f)
        assertEquals(6.0, low.responseDb(20.0), 0.3)
        assertEquals("half the gain at the corner", 3.0, low.responseDb(120.0), 0.2)
        assertEquals(0.0, low.responseDb(10_000.0), 0.05)
        val high = TrackEq(highHz = 8000f, highDb = -9f)
        assertEquals(-9.0, high.responseDb(19_000.0), 0.6)
        assertEquals(-4.5, high.responseDb(8000.0), 0.3)
        assertEquals(0.0, high.responseDb(100.0), 0.05)
    }

    @Test
    fun `the bell peaks at its centre, narrower for a higher Q, and the low cut falls 12 dB per octave`() {
        val bell = TrackEq(midHz = 1000f, midDb = 6f, midQ = 1f)
        assertEquals(6.0, bell.responseDb(1000.0), 1e-6)
        assertTrue(bell.responseDb(100.0) < 0.5)
        assertTrue(bell.copy(midQ = 4f).responseDb(1500.0) < bell.responseDb(1500.0))
        val cut = TrackEq(lowCut = true, lowCutHz = 80f)
        assertEquals("Butterworth corner", -3.01, cut.responseDb(80.0), 0.05)
        assertEquals(-24.0, cut.responseDb(20.0), 0.6)
        assertEquals(0.0, cut.responseDb(2000.0), 0.01)
    }

    @Test
    fun `controls round and clamp, and dragging a band moves its frequency and gain`() {
        assertEquals(1234f, EqControl.MID_HZ.write(TrackEq.FLAT, 1234.4f).midHz)
        assertEquals(15f, EqControl.LOW_DB.write(TrackEq.FLAT, 40f).lowDb)
        assertEquals(2.4f, EqControl.HIGH_DB.write(TrackEq.FLAT, 2.44f).highDb)
        val dragged = EqBand.MID.drag(TrackEq.FLAT, 3000f, -4.36f)
        assertEquals(3000f to -4.4f, dragged.midHz to dragged.midDb)
        val cut = EqBand.LOW_CUT.drag(TrackEq.FLAT, 150f, 10f)
        assertTrue("dragging the low cut switches it on", cut.lowCut)
        assertEquals(150f, cut.lowCutHz)
        assertEquals("its handle sits on the curve at the corner", cut.responseDb(150.0).toFloat(), EqBand.LOW_CUT.gainOf(cut), 1e-4f)
    }

    @Test
    fun `each preset stays inside the sheet's ranges and is known again, on or bypassed, until a band moves`() {
        EqPreset.entries.forEach { preset ->
            EqControl.entries.forEach { c ->
                val v = c.read(preset.eq)
                assertTrue("${preset.label} ${c.band}${c.label} = $v", v in c.min..c.max)
            }
            assertTrue("${preset.label} switches the EQ on", preset.eq.enabled && preset.eq.isActive)
            assertEquals(preset, EqPreset.of(preset.eq))
            assertEquals(preset, EqPreset.of(preset.eq.copy(enabled = false)))
        }
        assertEquals("a moved band is a curve of its own", null, EqPreset.of(EqControl.HIGH_DB.write(EqPreset.VOICE.eq, 2.5f)))
        assertEquals("flat is no preset", null, EqPreset.of(TrackEq.FLAT))
        assertEquals(EqPreset.entries.size, EqPreset.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `the presets shape the sound as named`() {
        fun db(p: EqPreset, hz: Double) = p.eq.responseDb(hz)
        assertTrue("rumble gone", db(EqPreset.RUMBLE, 40.0) < -8.0)
        assertEquals("the voice untouched", 0.0, db(EqPreset.RUMBLE, 1000.0), 0.2)
        assertEquals("voice presence", 3.0, db(EqPreset.VOICE, 3000.0), 0.6)
        assertTrue("podcast: the boxy middle down", db(EqPreset.PODCAST, 350.0) < -1.5)
        assertEquals("hiss turned down", -8.0, db(EqPreset.DE_HISS, 14_000.0), 0.6)
        assertTrue("warm: more body, less top", db(EqPreset.WARM, 80.0) > 2.0 && db(EqPreset.WARM, 14_000.0) < -1.5)
        assertTrue("bright: more top", db(EqPreset.BRIGHT, 12_000.0) > 3.0)
        // A phone line: the middle up, lows and highs gone.
        assertTrue(db(EqPreset.TELEPHONE, 1500.0) > 4.0)
        assertTrue(db(EqPreset.TELEPHONE, 100.0) < -15.0)
        assertTrue(db(EqPreset.TELEPHONE, 8000.0) < -10.0)
    }
}
