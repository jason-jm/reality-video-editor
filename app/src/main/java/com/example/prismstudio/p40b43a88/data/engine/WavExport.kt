package com.example.prismstudio.p40b43a88.data.engine

import android.media.MediaCodec
import android.media.metrics.LogSessionId
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.transformer.Codec
import com.google.common.collect.ImmutableList
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A lossless audio-only render (Resolve's Wave export): no system encoder takes raw PCM, so Media3 finds no audio type for
 * it and asks the encoder factory for an unnamed one — [WavEncoderFactory] answers with [PcmPassthrough], whose input
 * format sets the mix's sample rate (the graph resamples to it) and whose output is the same samples as 16-bit or packed
 * 24-bit PCM; [WavMuxer] writes them into a RIFF/WAVE file.
 */
@UnstableApi
class WavEncoderFactory(private val sampleRate: Int, private val bits: Int, private val base: Codec.EncoderFactory) : Codec.EncoderFactory {

    override fun createForAudioEncoding(format: Format, logSessionId: LogSessionId?): Codec =
        if (format.sampleMimeType == null || format.sampleMimeType == MimeTypes.AUDIO_RAW) PcmPassthrough(format, sampleRate, bits)
        else base.createForAudioEncoding(format, logSessionId)

    override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?): Codec = base.createForVideoEncoding(format, logSessionId)

    override fun audioNeedsEncoding(): Boolean = true

    override fun videoNeedsEncoding(): Boolean = base.videoNeedsEncoding()
}

/**
 * The mix's PCM passed through as a "codec". Media3's audio graph gives 16-bit samples whatever input encoding an encoder
 * asks for (asked for float, it sent 16-bit data, read as NaN floats) and mixes in 16 bits: a 16-bit file holds the mix
 * exactly; a 24-bit one holds the same samples ×256 — the depth delivery specs ask for, no more precision. Each queued
 * buffer comes out whole, in order.
 */
@UnstableApi
class PcmPassthrough(private val configuration: Format, sampleRate: Int, private val bits: Int) : Codec {
    private val channels = configuration.channelCount.takeIf { it > 0 } ?: 2
    private val input: Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(sampleRate)
        .setChannelCount(channels)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .build()
    private val output: Format = input.buildUpon().setPcmEncoding(if (bits == 24) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_16BIT).build()
    private val pending = ArrayDeque<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
    private var inputEnded = false
    private var ended = false
    private var started = false

    override fun getConfigurationFormat(): Format = configuration

    override fun getName(): String = NAME

    override fun getInputSurface(): android.view.Surface = throw UnsupportedOperationException("PCM has no input surface")

    override fun maybeDequeueInputBuffer(inputBuffer: DecoderInputBuffer): Boolean {
        if (inputEnded || pending.size >= MAX_PENDING) return false
        inputBuffer.data = ByteBuffer.allocateDirect(INPUT_CAPACITY).order(ByteOrder.nativeOrder())
        inputBuffer.clear()
        return true
    }

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        started = true
        val data = inputBuffer.data
        if (data != null && data.hasRemaining()) {
            val packed = if (bits == 24) pcm16To24(data) else pcm16Copy(data)
            pending.addLast(packed to MediaCodec.BufferInfo().apply { set(0, packed.remaining(), inputBuffer.timeUs, 0) })
        }
        if (inputBuffer.isEndOfStream) inputEnded = true
        if (inputEnded && pending.isEmpty()) ended = true
    }

    override fun signalEndOfInputStream() {
        inputEnded = true
        if (pending.isEmpty()) ended = true
    }

    override fun getInputFormat(): Format = input

    override fun getOutputFormat(): Format? = if (started) output else null

    override fun getOutputBuffer(): ByteBuffer? = pending.firstOrNull()?.first

    override fun getOutputBufferInfo(): MediaCodec.BufferInfo? = pending.firstOrNull()?.second

    override fun releaseOutputBuffer(render: Boolean) {
        pending.removeFirstOrNull()
        if (inputEnded && pending.isEmpty()) ended = true
    }

    override fun releaseOutputBuffer(renderPresentationTimeUs: Long) = releaseOutputBuffer(false)

    override fun isEnded(): Boolean = ended

    override fun release() = pending.clear()

    companion object {
        const val NAME = "prism.pcm.passthrough"
        private const val INPUT_CAPACITY = 256 * 1024
        private const val MAX_PENDING = 8
    }
}

/** [data]'s 16-bit samples (native order) as little-endian 16-bit ones, as a WAV stores them. */
internal fun pcm16Copy(data: ByteBuffer): ByteBuffer {
    val src = data.order(ByteOrder.nativeOrder())
    val out = ByteBuffer.allocateDirect(src.remaining()).order(ByteOrder.LITTLE_ENDIAN)
    while (src.remaining() >= 2) out.putShort(src.getShort())
    out.flip()
    return out
}

/** [data]'s 16-bit samples (native order) as packed little-endian 24-bit ones: s·256, exact. */
internal fun pcm16To24(data: ByteBuffer): ByteBuffer {
    val src = data.order(ByteOrder.nativeOrder())
    val out = ByteBuffer.allocateDirect(src.remaining() / 2 * 3).order(ByteOrder.LITTLE_ENDIAN)
    while (src.remaining() >= 2) {
        val v = src.getShort().toInt() shl 8
        out.put((v and 0xFF).toByte())
        out.put((v shr 8 and 0xFF).toByte())
        out.put((v shr 16 and 0xFF).toByte())
    }
    out.flip()
    return out
}

/** A RIFF/WAVE file of the one PCM track: the header written last, when the data's length is known. */
@UnstableApi
class WavMuxer(path: String) : Muxer {
    private val file = RandomAccessFile(path, "rw").apply {
        setLength(0)
        write(ByteArray(HEADER_BYTES))
    }
    private var format: Format? = null
    private var dataBytes = 0L
    private var scratch = ByteArray(64 * 1024)

    override fun addTrack(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) throw MuxerException("WAV 只能写入 PCM 声音", IllegalArgumentException(format.sampleMimeType))
        if (this.format != null) throw MuxerException("WAV 只有一条声音轨", IllegalStateException("second track"))
        this.format = format
        return 0
    }

    override fun writeSampleData(trackId: Int, data: ByteBuffer, bufferInfo: BufferInfo) {
        val n = data.remaining()
        if (scratch.size < n) scratch = ByteArray(n)
        data.get(scratch, 0, n)
        file.write(scratch, 0, n)
        dataBytes += n
    }

    override fun addMetadataEntry(entry: androidx.media3.common.Metadata.Entry) = Unit

    override fun close() {
        val f = format
        try {
            if (f != null) {
                file.seek(0)
                file.write(header(f.sampleRate, f.channelCount, bitsOf(f.pcmEncoding), dataBytes))
            }
        } finally {
            file.close()
        }
    }

    class Factory : Muxer.Factory {
        override fun create(path: String): Muxer = WavMuxer(path)

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            if (trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of(MimeTypes.AUDIO_RAW) else ImmutableList.of()
    }

    companion object {
        const val HEADER_BYTES = 44

        fun bitsOf(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            else -> 16
        }

        /** The 44-byte canonical header of [dataBytes] of [bits]-bit PCM (WAVE_FORMAT_PCM). */
        fun header(sampleRate: Int, channels: Int, bits: Int, dataBytes: Long): ByteArray {
            val blockAlign = channels * bits / 8
            val data = dataBytes.coerceAtMost(UInt.MAX_VALUE.toLong() - 36).toInt()
            return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + data)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(channels.toShort())
                putInt(sampleRate); putInt(sampleRate * blockAlign); putShort(blockAlign.toShort()); putShort(bits.toShort())
                put("data".toByteArray(Charsets.US_ASCII)); putInt(data)
            }.array()
        }
    }
}
