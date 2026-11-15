package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The first stage of every audio item (CompositionFactory, release round R16): any PCM to 16-bit, 16-bit untouched — what
 * the app's processors and Media3's audio graph need. Pinned here in case a Media3 upgrade changes it.
 */
class AudioSourceFormatTest {

    @Test
    fun `24-bit, 32-bit, float and 8-bit sources come out as 16-bit`() {
        for (encoding in listOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_8BIT)) {
            val out = ToInt16PcmAudioProcessor().configure(AudioProcessor.AudioFormat(96_000, 2, encoding))
            assertEquals("encoding $encoding", C.ENCODING_PCM_16BIT, out.encoding)
            assertEquals(96_000, out.sampleRate)
            assertEquals(2, out.channelCount)
        }
    }

    @Test
    fun `a 16-bit source passes untouched`() {
        val p = ToInt16PcmAudioProcessor()
        p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        assertFalse(p.isActive)
    }
}
