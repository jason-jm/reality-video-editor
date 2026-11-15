package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.log10
import kotlin.math.PI
import kotlin.math.pow

/** The per-item mix: gain and the lane's balance pan, for mono and stereo sound. */
class GainFadeAudioProcessorTest {

    private fun processor(
        pan: Float,
        faderDb: Float = 0f,
        eq: TrackEq = TrackEq.FLAT,
        dynamics: TrackDynamics = TrackDynamics.OFF,
        clipDb: Float = 0f,
        clipEq: TrackEq = TrackEq.FLAT,
    ): GainFadeAudioProcessor {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1).copy(volumeDb = clipDb, eq = clipEq))
        val mixer = MixerState.forTracks(timeline.tracks).let { m ->
            m.replace(m.channel(Timeline.A1).copy(pan = pan, volumeDb = faderDb, eq = eq, dynamics = dynamics))
        }
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }
        return GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000)
    }

    private fun run(p: GainFadeAudioProcessor, channels: Int, vararg samples: Short): List<Short> {
        val out = p.configure(AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT))
        p.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        p.queueInput(input)
        val output = p.getOutput().order(ByteOrder.nativeOrder())
        return List(output.remaining() / 2) { output.getShort() }.also { assertEquals(2, out.channelCount) }
    }

    @Test
    fun `a sound's fade curves bend its level - up comes in sooner, down holds back - as the timeline draws them`() {
        val clip = clipOf("S", 0, 300, track = Timeline.A1).copy(fadeInSeconds = 1f, fadeInCurve = 1f, fadeOutSeconds = 1f, fadeOutCurve = -0.5f)
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timelineOf(clip))) }
        val p = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000)
        val fadeIn = run(p, 1, *ShortArray(48_000) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
        for (t in listOf(0.1, 0.25, 0.5, 0.75)) {
            val expected = 10_000 * com.example.prismstudio.p40b43a88.domain.model.ClipFades.shape(t.toFloat(), 1f)
            assertEquals("in at $t", expected.toDouble(), fadeIn[(t * 48_000).toInt()].toDouble(), 1.5)
        }
        assertEquals("a quarter in, bowed all the way up: three quarters", 7_500.0, fadeIn[12_000].toDouble(), 1.5)
        // The fade out, read back from the clip's end: the last second of the 10 s clip.
        val out = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 9_000_000, clipDurationUs = 10_000_000)
        val fadeOut = run(out, 1, *ShortArray(48_000) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
        for (t in listOf(0.25, 0.5, 0.75)) {
            val untilEnd = 1.0 - t
            val expected = 10_000 * com.example.prismstudio.p40b43a88.domain.model.ClipFades.shape(untilEnd.toFloat(), -0.5f)
            assertEquals("out at $t", expected.toDouble(), fadeOut[(t * 48_000).toInt()].toDouble(), 1.5)
        }
    }

    /** A constant level through a cross fade: the incoming clip's fade-in, or (with [crossOutUs]) the outgoing clip's handle. */
    private fun crossFade(crossInUs: Long = 0, crossOutUs: Long = 0, fadeOutSeconds: Float = 0f): List<Short> {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1).copy(fadeOutSeconds = fadeOutSeconds))
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline)) }
        val handle = crossOutUs > 0
        val p = GainFadeAudioProcessor(
            registry, Timeline.A1, "S",
            clipOffsetUs = if (handle) 10_000_000 else 0, clipDurationUs = 10_000_000, crossInUs = crossInUs, crossOutUs = crossOutUs,
        )
        return run(p, 1, *ShortArray(48_000) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
    }

    @Test
    fun `a cross fade takes the incoming sound up and the outgoing one down with equal power`() {
        val fadeIn = crossFade(crossInUs = 1_000_000)
        val fadeOut = crossFade(crossOutUs = 1_000_000)
        for (i in listOf(0, 6_000, 12_000, 24_000, 36_000, 47_999)) {
            val t = i / 48_000.0
            assertEquals("in at $t", 10_000 * sin(PI / 2 * t), fadeIn[i].toDouble(), 1.0)
            assertEquals("out at $t", 10_000 * kotlin.math.cos(PI / 2 * t), fadeOut[i].toDouble(), 1.0)
            val power = (fadeIn[i] / 10_000.0).pow(2) + (fadeOut[i] / 10_000.0).pow(2)
            assertEquals("+3 dB: the powers add up to one at $t", 1.0, power, 0.001)
        }
        assertEquals("a clip that faded out has nothing to hand over", 0, crossFade(crossOutUs = 1_000_000, fadeOutSeconds = 1f).maxOf { it.toInt() })
    }

    @Test
    fun `the outgoing sound runs through the cut in one piece, at full level until it`() {
        // One second of a clip that ends half way: the last half second is the fade into the next clip.
        val timeline = timelineOf(clipOf("S", 0, 15, track = Timeline.A1))
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline)) }
        val p = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 500_000, crossOutUs = 500_000)
        val left = run(p, 1, *ShortArray(48_000) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
        assertEquals("untouched before the cut", listOf<Short>(10_000), left.take(24_000).distinct())
        for (i in listOf(24_000, 30_000, 36_000, 47_999)) {
            val t = (i - 24_000) / 24_000.0
            assertEquals("cos past the cut at $t", 10_000 * kotlin.math.cos(PI / 2 * t), left[i].toDouble(), 1.0)
        }
    }

    @Test
    fun `a speed point's join hands the sound over with equal power, the piece before playing on past the point`() {
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1))
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline)) }
        // The piece after the point fades in over its first 50 ms; the one before plays on 50 ms past its half second.
        val after = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 500_000, clipDurationUs = 10_000_000, joinInUs = 50_000)
        val before = GainFadeAudioProcessor(
            registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000, joinOutUs = 50_000, pieceUs = 500_000,
        )
        val fadeIn = run(after, 1, *ShortArray(4_800) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
        val tail = run(before, 1, *ShortArray(28_800) { 10_000 }).filterIndexed { i, _ -> i % 2 == 0 }
        assertEquals("untouched until the point", listOf<Short>(10_000), tail.take(24_000).distinct())
        for (i in listOf(0, 600, 1_200, 1_800, 2_399)) {
            val t = i / 2_400.0
            val incoming = fadeIn[i] / 10_000.0
            val outgoing = tail[24_000 + i] / 10_000.0
            assertEquals("in at $t", sin(PI / 2 * t), incoming, 1e-4)
            assertEquals("the powers add up to one at $t", 1.0, incoming.pow(2) + outgoing.pow(2), 0.001)
        }
        assertEquals("full once the join is over", listOf<Short>(10_000), fadeIn.drop(2_400).distinct())
        assertEquals("silent once it has handed over", listOf<Short>(0), tail.drop(26_400).distinct())
    }

    @Test
    fun `a sound's channels are mapped before anything else - swapped, one side on both, or mixed to mono`() {
        fun mapped(channels: AudioChannels, pan: Float = 0f): List<Short> {
            val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1).copy(channels = channels))
            val mixer = MixerState.forTracks(timeline.tracks).let { m -> m.replace(m.channel(Timeline.A1).copy(pan = pan)) }
            val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }
            val p = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000)
            return run(p, 2, 1000, 3000, -2000, 400)
        }
        assertEquals(listOf<Short>(1000, 3000, -2000, 400), mapped(AudioChannels.STEREO))
        assertEquals(listOf<Short>(3000, 1000, 400, -2000), mapped(AudioChannels.SWAP))
        assertEquals(listOf<Short>(1000, 1000, -2000, -2000), mapped(AudioChannels.LEFT))
        assertEquals(listOf<Short>(3000, 3000, 400, 400), mapped(AudioChannels.RIGHT))
        assertEquals(listOf<Short>(2000, 2000, -800, -800), mapped(AudioChannels.MONO))
        // The track's pan comes after: the left side alone, panned hard right, is heard on the right only.
        assertEquals(listOf<Short>(0, 1000, 0, -2000), mapped(AudioChannels.LEFT, pan = 1f))
        // A mono item stays the same on both sides whatever the mapping says.
        val timeline = timelineOf(clipOf("S", 0, 300, track = Timeline.A1).copy(channels = AudioChannels.RIGHT))
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline)) }
        val mono = GainFadeAudioProcessor(registry, Timeline.A1, "S", clipOffsetUs = 0, clipDurationUs = 10_000_000)
        assertEquals(listOf<Short>(1200, 1200), run(mono, 1, 1200))
    }

    @Test
    fun `a mono item leaves as stereo, the same on both sides when centred`() {
        assertEquals(listOf<Short>(1000, 1000, -2000, -2000), run(processor(pan = 0f), 1, 1000, -2000))
    }

    @Test
    fun `the pan places a mono item, turning the other side down`() {
        assertEquals(listOf<Short>(1000, 500, -2000, -1000), run(processor(pan = -0.5f), 1, 1000, -2000))
        assertEquals("hard right", listOf<Short>(0, 1000), run(processor(pan = 1f), 1, 1000))
    }

    @Test
    fun `a stereo item keeps its channels, with the pan and the fader applied`() {
        // Fader at half amplitude; panned right, so the left side is halved again.
        assertEquals(listOf<Short>(250, 400), run(processor(pan = 0.5f, faderDb = -6.0206f), 2, 1000, 800))
    }

    @Test
    fun `the track EQ shapes the sound before the fader, as its curve says`() {
        // One second of a 100 Hz and of a 10 kHz sine through a +12 dB low shelf at 300 Hz.
        val shelf = TrackEq(lowHz = 300f, lowDb = 12f)
        fun rmsGain(hz: Double, eq: TrackEq): Double {
            val n = 48_000
            val input = ShortArray(n) { i -> (2000 * sin(2 * PI * hz * i / 48_000)).toInt().toShort() }
            val out = run(processor(pan = 0f, eq = eq), 1, *input).filterIndexed { i, _ -> i % 2 == 0 }.drop(4800)
            val rms = { xs: List<Double> -> sqrt(xs.sumOf { it * it } / xs.size) }
            return 20 * log10(rms(out.map { it.toDouble() }) / rms(input.drop(4800).map { it.toDouble() }))
        }
        assertEquals(shelf.responseDb(100.0), rmsGain(100.0, shelf), 0.1)
        assertEquals(0.0, rmsGain(10_000.0, shelf), 0.1)
        assertEquals("bypassed", 0.0, rmsGain(100.0, shelf.copy(enabled = false)), 0.01)
    }

    @Test
    fun `a clip's own EQ shapes its sound before the track's, the two adding up`() {
        // A 1 kHz sine through +6 dB peaks at 1 kHz on the clip and on the track: +12 dB; a 100 Hz one stays put.
        val peak = TrackEq(midHz = 1000f, midDb = 6f, midQ = 1f)
        fun rmsGain(hz: Double, clipEq: TrackEq, eq: TrackEq): Double {
            val n = 48_000
            val input = ShortArray(n) { i -> (1000 * sin(2 * PI * hz * i / 48_000)).toInt().toShort() }
            val out = run(processor(pan = 0f, eq = eq, clipEq = clipEq), 1, *input).filterIndexed { i, _ -> i % 2 == 0 }.drop(4800)
            val rms = { xs: List<Double> -> sqrt(xs.sumOf { it * it } / xs.size) }
            return 20 * log10(rms(out.map { it.toDouble() }) / rms(input.drop(4800).map { it.toDouble() }))
        }
        assertEquals(6.0, rmsGain(1000.0, peak, TrackEq.FLAT), 0.1)
        assertEquals(12.0, rmsGain(1000.0, peak, peak), 0.15)
        assertEquals(peak.responseDb(100.0), rmsGain(100.0, peak, TrackEq.FLAT), 0.1)
        assertEquals("a bypassed clip EQ leaves the track's", 6.0, rmsGain(1000.0, peak.copy(enabled = false), peak), 0.1)
    }

    @Test
    fun `the compressor hears the clip at its own level, before the fader`() {
        // A steady −6 dBFS square wave, half a second, through 4:1 over −20 dB with a fast attack.
        val comp = TrackDynamics(compressor = true, thresholdDb = -20f, ratio = 4f, attackMs = 0.1f)
        val amplitude = (32768 * 10.0.pow(-6.0 / 20)).toInt()
        val input = ShortArray(24_000) { i -> (if ((i / 24) % 2 == 0) amplitude else -amplitude).toShort() }
        fun outDb(p: GainFadeAudioProcessor): Double {
            val out = run(p, 1, *input).filterIndexed { i, _ -> i % 2 == 0 }.drop(12_000)
            return 20 * log10(out.maxOf { kotlin.math.abs(it.toInt()) } / 32768.0)
        }
        // 14 dB over the threshold comes out 3.5 dB over it.
        assertEquals(-16.5, outDb(processor(pan = 0f, dynamics = comp)), 0.05)
        assertEquals("the fader comes after: the same compression, 6 dB down", -22.5, outDb(processor(pan = 0f, faderDb = -6f, dynamics = comp)), 0.05)
        assertEquals("clip gain comes before: 6 dB less in is 1.5 dB less out", -18.0, outDb(processor(pan = 0f, clipDb = -6f, dynamics = comp)), 0.05)
        assertEquals("a limiter caps the peaks", -10.0, outDb(processor(pan = 0f, dynamics = TrackDynamics(limiter = true, ceilingDb = -10f))), 0.05)
    }

    @Test
    fun `the ducking dips the lane at the timeline time`() {
        // The song starts 1 s into the timeline; its envelope goes down from 1.0 s to 1.5 s and stays down.
        val timeline = timelineOf(clipOf("talk", 0, 90, track = Timeline.A1), clipOf("S", 30, 90, track = Timeline.A2))
        val mixer = MixerState.forTracks(timeline.tracks).let { m ->
            m.replace(m.channel(Timeline.A2).copy(ducking = TrackDucking(keyTrackId = Timeline.A1, depthDb = 12f)))
        }
        val registry = LiveLookRegistry().also { it.update(projectOf(timeline = timeline, mixer = mixer)) }
        registry.setDucks(mapOf(Timeline.A2 to DuckEnvelope(500_000, floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 1f))))
        val p = GainFadeAudioProcessor(registry, Timeline.A2, "S", clipOffsetUs = 0, clipDurationUs = 3_000_000)
        val out = run(p, 1, *ShortArray(48_000) { 10_000 })
        assertEquals("at 1.0 s: not yet", 10_000, out[0].toInt())
        assertEquals("at 1.25 s: half way down", 10_000 * 10.0.pow(-6.0 / 20), out[2 * 12_000].toDouble(), 2.0)
        assertEquals("at 1.5 s: 12 dB down", 10_000 * 10.0.pow(-12.0 / 20), out[2 * 24_000].toDouble(), 2.0)
        assertEquals("both sides", out[2 * 24_000], out[2 * 24_000 + 1])
    }
}
