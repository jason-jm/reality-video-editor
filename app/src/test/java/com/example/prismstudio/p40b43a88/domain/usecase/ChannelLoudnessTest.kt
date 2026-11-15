package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.LoudnessAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/** Loudness as the mix plays a clip's channel mapping: one side on both, a mono fold, from a stereo file's sides kept apart. */
class ChannelLoudnessTest {

    /** 4 s at 48 kHz: [left] and [right] samples of 1 kHz sines (amplitude, sign = phase). */
    private fun profile(left: Double, right: Double, channels: Int = 2): LoudnessProfile {
        val rate = 48_000
        val analyzer = LoudnessAnalyzer(rate, channels)
        val frames = rate * 4
        val buf = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val s = sin(2 * PI * 1000 * i / rate)
            buf[i * channels] = (left * s).toFloat()
            if (channels == 2) buf[i * 2 + 1] = (right * s).toFloat()
        }
        analyzer.add(buf, frames)
        return analyzer.finish()
    }

    private fun lufs(p: LoudnessProfile, channels: AudioChannels): Float {
        val clip = clipOf("S", 0, 120, track = Timeline.A1).copy(channels = channels)
        return (LoudnessUseCase().measure(clip, p, 30) as LoudnessUseCase.Reading.Measured).lufs
    }

    private fun peak(p: LoudnessProfile, channels: AudioChannels): Float {
        val clip = clipOf("S", 0, 120, track = Timeline.A1).copy(channels = channels)
        return (LoudnessUseCase().measure(clip, p, 30) as LoudnessUseCase.Reading.Measured).truePeakDbtp
    }

    @Test
    fun `a recording on the left only reads 3 dB louder on both sides, and the right alone is silent`() {
        val p = profile(0.25, 0.0)
        val stereo = lufs(p, AudioChannels.STEREO)
        assertEquals(stereo, lufs(p, AudioChannels.SWAP), 1e-4f)
        assertEquals(10 * log10(2f), lufs(p, AudioChannels.LEFT) - stereo, 0.02f)
        // Folded to mono: half the amplitude on both sides, half the energy of the left alone.
        assertEquals(-10 * log10(2f), lufs(p, AudioChannels.MONO) - stereo, 0.02f)
        assertTrue(LoudnessUseCase().measure(clipOf("S", 0, 120, track = Timeline.A1).copy(channels = AudioChannels.RIGHT), p, 30) is LoudnessUseCase.Reading.Silent)
        // True peaks follow the side that plays: the left's −12 dBFS sine, the fold half of it.
        assertEquals(20 * log10(0.25f), peak(p, AudioChannels.LEFT), 0.1f)
        assertEquals(20 * log10(0.125f), peak(p, AudioChannels.MONO), 0.1f)
    }

    @Test
    fun `a mono fold keeps two equal sides and cancels opposite ones, and other files keep their own blocks`() {
        val same = profile(0.25, 0.25)
        assertEquals(lufs(same, AudioChannels.STEREO), lufs(same, AudioChannels.MONO), 0.02f)
        val opposite = profile(0.25, -0.25)
        assertTrue(LoudnessUseCase().measure(clipOf("S", 0, 120, track = Timeline.A1).copy(channels = AudioChannels.MONO), opposite, 30) is LoudnessUseCase.Reading.Silent)
        // A mono file has no sides to tell apart: every mapping reads the same.
        val mono = profile(0.25, 0.0, channels = 1)
        assertNull(mono.stereo)
        assertEquals(lufs(mono, AudioChannels.STEREO), lufs(mono, AudioChannels.LEFT), 1e-4f)
        assertEquals(lufs(mono, AudioChannels.STEREO), lufs(mono, AudioChannels.MONO), 1e-4f)
    }
}
