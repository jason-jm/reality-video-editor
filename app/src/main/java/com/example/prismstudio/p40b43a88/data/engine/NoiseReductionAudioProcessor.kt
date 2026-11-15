package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.domain.model.NoiseGate
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * A lane's noise reduction on one sound item, first in its chain (before [GainFadeAudioProcessor]): a [NoiseGate] against
 * the item's file's [profile], played at the item's [speed]. Its settings are read from the lane's live mix at every step.
 * The gate's latency never shows: the first [NoiseGate.LATENCY] frames out (made of the silence it starts from) are
 * dropped and the last as many are flushed at the end, so every frame leaves as many frames in as it arrived, and the
 * item keeps its length and its place — a render lines its items up to the sample after this.
 */
@UnstableApi
class NoiseReductionAudioProcessor(
    private val registry: LiveLookRegistry,
    private val laneTrackId: String,
    private val profile: NoiseProfile,
    private val speed: Float = 1f,
) : BaseAudioProcessor() {

    private var gate: NoiseGate? = null
    private var samples = FloatArray(0)
    private var toDrop = 0
    private var framesIn = 0L
    private var framesOut = 0L
    /** The input has ended with frames still held: they go out at the next [getOutput]. */
    private var flushPending = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val sampleBytes = if (isFloat) 4 else 2
        val frames = inputBuffer.remaining() / (channels * sampleBytes)
        if (frames == 0) return
        if (samples.size < frames * channels) samples = FloatArray(frames * channels)
        for (i in 0 until frames * channels) samples[i] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / FULL_SCALE
        inputBuffer.position(inputBuffer.limit())
        framesIn += frames
        // At most a step's worth more than came in (what the gate held) goes out.
        val out = replaceOutputBuffer((frames + NoiseGate.HOP) * channels * sampleBytes)
        gate(format.sampleRate, channels).process(samples, frames, settings()) { frame -> give(out, frame, isFloat) }
        out.flip()
    }

    /**
     * The frames still held go out once the pipeline asks for output again ([getOutput]): written now, they would replace
     * the output buffer the pipeline may still be reading from (one buffer is reused), and frames were lost (rev 97).
     */
    override fun onQueueEndOfStream() {
        if (gate != null && framesIn > framesOut) flushPending = true
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining() || !flushPending) return output
        flushPending = false
        giveHeld()
        return super.getOutput()
    }

    override fun isEnded(): Boolean = super.isEnded() && !flushPending

    /** Feeds the gate silence until everything that came in has gone out. */
    private fun giveHeld() {
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val owed = (framesIn - framesOut).toInt().coerceAtLeast(0)
        val gate = gate ?: return
        if (owed == 0) return
        val sampleBytes = if (isFloat) 4 else 2
        val out = replaceOutputBuffer(owed * channels * sampleBytes)
        val silence = FloatArray((NoiseGate.LATENCY + NoiseGate.HOP) * channels)
        gate.process(silence, NoiseGate.LATENCY + NoiseGate.HOP, settings()) { frame -> if (framesOut < framesIn) give(out, frame, isFloat) }
        out.flip()
    }

    private fun give(out: ByteBuffer, frame: FloatArray, isFloat: Boolean) {
        if (toDrop > 0) {
            toDrop--
            return
        }
        for (x in frame) {
            if (isFloat) out.putFloat(x) else out.putShort((x * FULL_SCALE).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        framesOut++
    }

    private fun gate(sampleRate: Int, channels: Int): NoiseGate =
        gate ?: NoiseGate(sampleRate, channels, profile, speed).also {
            gate = it
            toDrop = NoiseGate.LATENCY
        }

    private fun settings(): TrackNoiseReduction =
        registry.laneMix(laneTrackId)?.noiseReduction ?: TrackNoiseReduction.OFF

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        gate = null
        framesIn = 0
        framesOut = 0
        flushPending = false
    }

    override fun onReset() {
        gate = null
        framesIn = 0
        framesOut = 0
        flushPending = false
    }

    private companion object {
        const val FULL_SCALE = 32768f
    }
}
