package com.example.prismstudio.p40b43a88.data.engine

import android.media.metrics.LogSessionId
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Codec

/**
 * Media3 1.9.3 records 1600 samples of encoder delay for Android's own AAC encoder in a render's edit list, but that
 * encoder delays its sound by 2048: in the rev 88 sync check a click 1.000 s into the mix was 2049 samples into the
 * render's sound, so players skipped too little and every render's sound came 9.3 ms after its picture (from a file's
 * start, slowed and sped up alike). The encoder's output format reports the measured delay instead, which the muxer
 * writes to the edit list; other encoders keep what Media3 reports for them.
 */
@UnstableApi
class AacDelayEncoderFactory(private val base: Codec.EncoderFactory) : Codec.EncoderFactory {

    override fun createForAudioEncoding(format: Format, logSessionId: LogSessionId?): Codec {
        val codec = base.createForAudioEncoding(format, logSessionId)
        return if (codec.name == ANDROID_AAC_ENCODER) DelayCorrected(codec) else codec
    }

    override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?): Codec = base.createForVideoEncoding(format, logSessionId)

    override fun audioNeedsEncoding(): Boolean = base.audioNeedsEncoding()

    override fun videoNeedsEncoding(): Boolean = base.videoNeedsEncoding()

    private class DelayCorrected(private val codec: Codec) : Codec by codec {
        override fun getOutputFormat(): Format? = codec.outputFormat?.buildUpon()?.setEncoderDelay(ANDROID_AAC_DELAY_FRAMES)?.build()
    }

    companion object {
        const val ANDROID_AAC_ENCODER = "c2.android.aac.encoder"

        /** The encoder's delay in samples per channel (Media3 assumes 1600). */
        const val ANDROID_AAC_DELAY_FRAMES = 2048
    }
}
