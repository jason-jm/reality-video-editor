package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes the first audio track of a file to PCM with MediaExtractor + MediaCodec, for analysis
 * (waveforms, loudness). Samples arrive as interleaved floats in −1..1, after the output format is
 * known; a later format change is announced again before the samples it applies to.
 */
class AudioPcmReader(context: Context) {

    private val context = context.applicationContext

    /**
     * Reads [uri] to the end: [onFormat] with the sample rate and channel count, then [onPcm] with
     * interleaved samples and their frame count (the array is reused between calls). Returns false when
     * the file has no audio track; decoder errors are thrown.
     */
    fun read(uri: String, onFormat: (sampleRate: Int, channels: Int) -> Unit, onPcm: (samples: FloatArray, frames: Int) -> Unit): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var floatPcm = false
            var announced = false
            var samples = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        announced = false
                    }
                    outIndex >= 0 -> {
                        if (!announced) {
                            onFormat(sampleRate, channels)
                            announced = true
                        }
                        val buffer = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val count = info.size / if (floatPcm) 4 else 2
                        if (samples.size < count) samples = FloatArray(count)
                        if (floatPcm) {
                            buffer.asFloatBuffer().get(samples, 0, count)
                        } else {
                            val shorts = buffer.asShortBuffer()
                            for (i in 0 until count) samples[i] = shorts.get(i) / 32768f
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (count > 0) onPcm(samples, count / channels)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            return true
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
