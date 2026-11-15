package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A render's sound items line up to the sample: the head before the clip's moment dropped, exactly the planned length out. */
class AudioAlignmentTest {

    private val starts = AudioStarts()

    private fun alignment(
        key: String,
        firstSampleUs: Long?,
        wantedUs: Long,
        windowStartUs: Long,
        lengthUs: Long,
        speed: Float = 1f,
        primingUs: Long = 0,
        skipUs: Long = 0,
    ): AudioAlignment {
        if (firstSampleUs != null) starts.record(key, firstSampleUs)
        return AudioAlignment(starts, key, wantedUs, primingUs, windowStartUs, speed, lengthUs, skipUs)
    }

    @Test
    fun `the head to drop is the sound from the first decoded sample to the clip's moment, at the item's speed`() {
        // A window opened inside the file at 371.3 ms: the first AAC frame kept starts at 384 ms of stored time, which carries
        // the sound from 362.7 ms (21.3 ms of priming); the clip plays from 500 ms.
        val inside = alignment("a", 384_000, wantedUs = 500_000, windowStartUs = 371_333, lengthUs = 1_000_000, primingUs = 21_333)
        assertEquals("137.3 ms at 48 kHz", 6592L, inside.headFrames(384_000, 48_000))
        assertEquals("slowed to 0.25×: four times as much output", 26368L,
            alignment("b", 384_000, 500_000, 371_333, 1_000_000, speed = 0.25f, primingUs = 21_333).headFrames(384_000, 48_000))
        // A window at the file's start: Media3 skips the priming itself, so the first sample is the sound's first.
        assertEquals(2400L, alignment("c", 0, wantedUs = 50_000, windowStartUs = 0, lengthUs = 1_000_000, primingUs = 21_333).headFrames(0, 48_000))
        assertEquals("a sound starting after the clip's moment is preceded by silence", -2400L,
            alignment("d", 150_000, wantedUs = 100_000, windowStartUs = 0, lengthUs = 1_000_000).headFrames(150_000, 48_000))
        assertEquals("a lane's lead-in skipped too", 2448L,
            alignment("e", 0, wantedUs = 50_000, windowStartUs = 0, lengthUs = 1_000_000, skipUs = 1_000).headFrames(0, 48_000))
    }

    @Test
    fun `an item lasts its frames as Media3 counts a silence, less a lead-in`() {
        val frame = alignment("f", 0, 0, 0, lengthUs = CompositionFactory.framesToUs(1, 30))
        assertEquals("33 333 µs is 1600 samples, rounded up as a gap is", 1600L, frame.lengthFrames(48_000))
        assertEquals(1552L, alignment("g", 0, 0, 0, lengthUs = 33_333, skipUs = 1_000).lengthFrames(48_000))
    }

    /** Runs mono 16-bit samples 0, 1, 2… ([count] of them) through [p] in chunks, then the end: the left channel out. */
    private fun run(p: GainFadeAudioProcessor, count: Int, chunk: Int = 512): List<Int> {
        p.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        p.flush()
        val out = mutableListOf<Int>()
        fun drain() {
            while (true) {
                val o = p.getOutput().order(ByteOrder.nativeOrder())
                if (!o.hasRemaining()) return
                while (o.remaining() >= 4) {
                    out += o.getShort().toInt()
                    o.getShort()
                }
            }
        }
        var at = 0
        while (at < count) {
            val n = minOf(chunk, count - at)
            val input = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            repeat(n) { input.putShort(((at + it) % 30_000).toShort()) }
            input.flip()
            p.queueInput(input)
            drain()
            at += n
        }
        p.queueEndOfStream()
        drain()
        assertTrue(p.isEnded)
        return out
    }

    private fun processor(alignment: AudioAlignment?, joinInUs: Long = 0): GainFadeAudioProcessor {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1))
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline)) }
        return GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000, joinInUs = joinInUs, alignment = alignment)
    }

    @Test
    fun `a lined-up item drops its head and gives exactly its length, what was read past it unheard`() {
        // The first sample 100 frames (2083 µs) before the clip's moment; 1000 frames long; 2000 frames decoded.
        val a = alignment("h", firstSampleUs = 1_000_000, wantedUs = 1_002_083, windowStartUs = 900_000, lengthUs = 20_833)
        val out = run(processor(a), 2000)
        assertEquals(1000, out.size)
        assertEquals("the clip's moment first", 100, out.first())
        assertEquals((100 until 1100).toList(), out)
    }

    @Test
    fun `a short item is filled to its length with silence, and a late one starts with silence`() {
        val short = run(processor(alignment("i", 1_000_000, 1_002_083, 900_000, lengthUs = 20_833)), 300)
        assertEquals(1000, short.size)
        assertEquals((100 until 300).toList() + List(800) { 0 }, short)
        // The sound starts 50 frames after the clip's moment: 50 frames of silence first, then all of it.
        val late = run(processor(alignment("j", 1_001_042, 1_000_000, 0, lengthUs = 20_833)), 2000)
        assertEquals(1000, late.size)
        assertEquals(List(50) { 0 } + (0 until 950).toList(), late)
    }

    @Test
    fun `the preview takes every frame as it comes, and a lined-up item's fades follow its own time`() {
        assertEquals("no alignment, nothing dropped or added", (0 until 1500).toList(), run(processor(null), 1500))
        // A planned 50 ms join fade-in: lined up, it starts at the clip's moment (not the first decoded frame) and lasts 5 ms.
        val faded = run(processor(alignment("k", 1_000_000, 1_002_083, 900_000, lengthUs = 20_833), joinInUs = 50_000), 2000)
        assertEquals(0, faded.first())
        assertEquals("halfway through the 5 ms: sin 45°", Math.round(0.7071 * (100 + 120)).toInt(), faded[120])
        assertEquals("full level from 5 ms on", 100 + 240, faded[240])
        val preview = run(processor(null, joinInUs = 50_000), 2400)
        assertEquals("the preview keeps the planned 50 ms", Math.round(Math.sin(Math.PI / 4) * 1200).toInt(), preview[1200])
    }

    @Test
    fun `without a noted first sample the sound is taken as starting where its window opens`() {
        val out = run(processor(alignment("l", firstSampleUs = null, wantedUs = 1_002_083, windowStartUs = 1_000_000, lengthUs = 20_833)), 2000)
        assertEquals(1000, out.size)
        assertEquals(100, out.first())
    }
}
