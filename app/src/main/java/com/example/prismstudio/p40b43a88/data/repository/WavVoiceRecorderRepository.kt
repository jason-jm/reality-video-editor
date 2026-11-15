package com.example.prismstudio.p40b43a88.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import com.example.prismstudio.p40b43a88.domain.usecase.VoiceTake
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Takes recorded from the microphone as uncompressed WAV — 48 kHz, 16-bit, mono — into files/recordings/, as Resolve's
 * Fairlight records: every sample sits where it was captured. MediaRecorder's AAC takes began with 2048 samples of encoder
 * priming that nothing marked to skip, and stamped their frames with jittery capture times (1408, 1536, 768… samples for
 * frames of 1024), so a clip starting inside a take played some 20 ms off (rev 88 sync check).
 */
class WavVoiceRecorderRepository(context: Context) : VoiceRecorderRepository {

    private val appContext = context.applicationContext
    private class Capture(val recorder: AudioRecord, val file: File) {
        @Volatile var running = true
        @Volatile var peak = 0
        @Volatile var failure: VoiceRecorderRepository.Failure? = null
        var frames = 0L
        lateinit var thread: Thread
    }
    private var active: Capture? = null
    private var lastFailure: VoiceRecorderRepository.Failure? = null
    override val failure get() = active?.failure ?: lastFailure

    // The sheet asks for RECORD_AUDIO before it starts a take; without it the recorder does not initialise.
    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        if (active != null) return true
        lastFailure = VoiceRecorderRepository.Failure.MICROPHONE
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, BUFFER_BYTES))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open the microphone", e)
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        var target: File? = null
        var opened: RandomAccessFile? = null
        return try {
            lastFailure = VoiceRecorderRepository.Failure.STORAGE
            val dir = File(appContext.filesDir, "recordings")
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create recordings directory")
            val file = File.createTempFile("vo-", ".wav", dir)
            target = file
            val out = RandomAccessFile(file, "rw")
            opened = out
            out.write(WavHeader.bytes(SAMPLE_RATE, CHANNELS, BITS, dataBytes = 0))
            lastFailure = VoiceRecorderRepository.Failure.MICROPHONE
            r.startRecording()
            check(r.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            val take = Capture(r, file)
            take.thread = Thread({ capture(take, out) }, "VoiceTake")
            take.thread.start()
            active = take
            lastFailure = null
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start recording", e)
            runCatching { opened?.close() }
            r.release()
            target?.delete()
            false
        }
    }

    /** Copies the microphone into the file until the take ends, then writes the header's sizes. */
    private fun capture(take: Capture, out: RandomAccessFile) {
        val samples = ShortArray(SAMPLE_RATE / READS_PER_SECOND)
        val bytes = ByteArray(samples.size * 2)
        var written = 0L
        try {
            while (take.running) {
                val n = take.recorder.read(samples, 0, samples.size)
                // Stopping the recorder ends a read that is waiting, possibly with an error code: the take is over.
                if (n < 0) {
                    if (!take.running) break
                    throw IllegalStateException("AudioRecord.read returned $n")
                }
                var loudest = 0
                for (i in 0 until n) {
                    val s = samples[i].toInt()
                    loudest = maxOf(loudest, abs(s))
                    bytes[2 * i] = s.toByte()
                    bytes[2 * i + 1] = (s shr 8).toByte()
                }
                out.write(bytes, 0, n * 2)
                written += n
                if (loudest > take.peak) take.peak = loudest
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recording stopped by an error", e)
            take.failure = if (e is IOException) VoiceRecorderRepository.Failure.STORAGE else VoiceRecorderRepository.Failure.MICROPHONE
        } finally {
            runCatching {
                out.use {
                    it.seek(0)
                    it.write(WavHeader.bytes(SAMPLE_RATE, CHANNELS, BITS, dataBytes = written * 2))
                }
            }.onFailure { take.failure = VoiceRecorderRepository.Failure.STORAGE }
            take.frames = written
        }
    }

    override fun level(): Float {
        val take = active ?: return 0f
        val p = take.peak
        take.peak = 0
        return (p / MAX_AMPLITUDE).coerceIn(0f, 1f)
    }

    override fun stop(): VoiceTake? {
        val take = end() ?: return null
        val target = take.file
        if (failure != null || take.frames == 0L || !target.exists()) {
            target.delete()
            return null
        }
        return VoiceTake(Uri.fromFile(target).toString(), take.frames * 1000 / SAMPLE_RATE, target.length())
    }

    override fun cancel() {
        end()?.file?.delete()
    }

    /** Only a finished writer may publish a WAV. A stalled writer cannot modify a subsequent take. */
    private fun end(): Capture? {
        val take = active ?: return null
        take.running = false
        runCatching { take.recorder.stop() }
        try {
            take.thread.join(JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            take.recorder.release()
            active = null
        }
        lastFailure = if (take.thread.isAlive) VoiceRecorderRepository.Failure.MICROPHONE else take.failure
        return take
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 1
        const val BITS = 16
        /** 20 ms per read: the level meter's pace. */
        const val READS_PER_SECOND = 50
        /** Room for 200 ms in the recorder, so a busy moment loses nothing. */
        const val BUFFER_BYTES = SAMPLE_RATE / 5 * 2
        const val JOIN_TIMEOUT_MS = 2_000L
        const val MAX_AMPLITUDE = 32_767f
    }
}

/** The 44-byte header of a PCM WAV file (RIFF, little-endian). */
internal object WavHeader {
    fun bytes(sampleRate: Int, channels: Int, bitsPerSample: Int, dataBytes: Long): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val out = java.nio.ByteBuffer.allocate(SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((SIZE - 8 + dataBytes).toInt())
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(channels.toShort())
        out.putInt(sampleRate).putInt(sampleRate * blockAlign).putShort(blockAlign.toShort()).putShort(bitsPerSample.toShort())
        out.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes.toInt())
        return out.array()
    }

    const val SIZE = 44
}
