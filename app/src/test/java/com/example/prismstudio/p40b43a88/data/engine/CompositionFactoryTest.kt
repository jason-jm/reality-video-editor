package com.example.prismstudio.p40b43a88.data.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** The filler that keeps a preview overlay's sequence video only (see [CompositionFactory.Output.fillerUri]). */
class CompositionFactoryTest {

    @Test
    fun `a gap is filled with whole filler clips, then what is left`() {
        assertEquals(listOf(3_000_000L), CompositionFactory.fillerPiecesUs(3_000_000L))
        assertEquals(listOf(60_000_000L), CompositionFactory.fillerPiecesUs(60_000_000L))
        assertEquals(listOf(60_000_000L, 60_000_000L, 5_000_000L), CompositionFactory.fillerPiecesUs(125_000_000L))
        assertEquals("one frame at 24 fps", listOf(41_666L), CompositionFactory.fillerPiecesUs(41_666L))
        assertEquals(emptyList<Long>(), CompositionFactory.fillerPiecesUs(0L))
    }

    @Test
    fun `the bundled filler clip lasts exactly as long as the factory assumes`() {
        val bytes = File("src/main/res/raw/prism_filler.mp4").readBytes()
        val mvhd = String(bytes, Charsets.ISO_8859_1).indexOf("mvhd")
        // Version 0 movie header: version/flags, creation and modification times, then timescale and duration.
        val header = ByteBuffer.wrap(bytes, mvhd + 4, 20)
        assertEquals("version 0 header", 0, header.get().toInt())
        header.position(header.position() + 3 + 8)
        val timescale = header.int.toLong()
        val duration = header.int.toLong()
        assertEquals(CompositionFactory.FILLER_DURATION_US, duration * 1_000_000L / timescale)
    }

    @Test
    fun `an audio window inside a file opens later by the file's encoder delay, never early`() {
        // From the file's start Media3 skips the delay itself: the window is the clip's, with no early edge.
        assertEquals(0L to 2_000_000L, CompositionFactory.audioWindowUs(0, 2_000, 21_333, 6_000))
        // From 0.5 s in, the samples arrive at their stored times: the window opens 1024 samples (48 kHz) later.
        assertEquals(521_333L to 2_521_333L, CompositionFactory.audioWindowUs(500, 2_000, 21_333, 6_000))
        assertEquals("a file without a delay", 500_000L to 2_500_000L, CompositionFactory.audioWindowUs(500, 2_000, 0, 6_000))
        // Near the file's end it moves only as far as there is sound left, keeping its length.
        assertEquals("10 ms left: it moves 10 ms", 4_000_000L to 6_000_000L, CompositionFactory.audioWindowUs(3_990, 2_000, 21_333, 6_000))
        assertEquals("none left: it stays", 4_000_000L to 6_000_000L, CompositionFactory.audioWindowUs(4_000, 2_000, 21_333, 6_000))
    }

    @Test
    fun `a render reads a sound from 150 ms before the clip to 100 ms past it, within the file`() {
        // The processor gives out exactly the item's length; reading past the end keeps the decoder's last frame before
        // it (a cut clip's first half ended in ~23 ms of silence, rev 155).
        assertEquals(371_333L to 2_621_333L, CompositionFactory.renderAudioWindowUs(500, 2_000, 21_333, 6_000, 150_000))
        assertEquals("from the start: no earlier than 0", 0L to 2_100_000L, CompositionFactory.renderAudioWindowUs(0, 2_000, 21_333, 6_000, 150_000))
        assertEquals("near the end: as far as the file goes", 3_850_000L to 6_000_000L, CompositionFactory.renderAudioWindowUs(4_000, 1_950, 0, 6_000, 150_000))
    }

    @Test
    fun `a render opens a picture's window a source frame and a half early`() {
        assertEquals(51L, CompositionFactory.frameLeadMs(30f))
        assertEquals(26L, CompositionFactory.frameLeadMs(60f))
        assertEquals("an unknown rate is taken as the slowest common one", 64L, CompositionFactory.frameLeadMs(0f))
    }
}
