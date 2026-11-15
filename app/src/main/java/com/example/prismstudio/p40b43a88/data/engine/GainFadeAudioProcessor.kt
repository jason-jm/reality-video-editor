package com.example.prismstudio.p40b43a88.data.engine

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.domain.model.Biquad
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.DynamicsGain
import com.example.prismstudio.p40b43a88.domain.model.DeEsserGain
import com.example.prismstudio.p40b43a88.domain.model.ReverbTank
import com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * How a render's sound item lines up to the sample. Its window opened early at [windowStartUs] (the file's sample times),
 * so its first decoded sample — [starts] has its time under [key] — comes before the moment the clip plays from, [wantedUs]
 * of the file's sound (encoder priming taken off: a window opening at the file's start has the priming skipped by Media3,
 * one opening inside it hands the samples over at their stored times, [primingUs] after the sound they carry). What comes
 * before that moment is dropped (at the item's [speed]); then exactly [lengthUs] of sound goes out — what the decoder read
 * past the end is dropped and a shortfall filled with silence — so every item after it on its lane starts where the plan
 * puts it. [skipUs] more is dropped at the head of a lane's first item, which follows a lead-in silence that long.
 */
class AudioAlignment(
    val starts: AudioStarts,
    val key: String,
    val wantedUs: Long,
    val primingUs: Long,
    val windowStartUs: Long,
    val speed: Float,
    val lengthUs: Long,
    val skipUs: Long = 0L,
) {
    /** Output frames to drop at the head (negative: frames of silence to put first) for a first sample at [firstSampleUs]. */
    fun headFrames(firstSampleUs: Long, sampleRate: Int): Long {
        val firstSoundUs = if (windowStartUs <= 0L) firstSampleUs.coerceAtLeast(0) else firstSampleUs - primingUs
        val leadUs = (wantedUs - firstSoundUs) / speed.toDouble() + skipUs
        return (leadUs * sampleRate / 1_000_000.0).roundToLong()
    }

    /** The output frames the item gives at [sampleRate]: its length as Media3 counts a silence's, less the lead-in it follows. */
    fun lengthFrames(sampleRate: Int): Long = frames(lengthUs, sampleRate) - frames(skipUs, sampleRate)

    private fun frames(us: Long, sampleRate: Int): Long = ceil(us * sampleRate / 1_000_000.0).toLong()
}

/**
 * Applies the live mix to one audio item, in a Fairlight channel strip's order: clip gain (or its volume
 * keyframes) with the clip's fade-in / fade-out ramps, the track's equaliser, its dynamics (compressor
 * and limiter, which hear the clip at its own level), then the track's ducking (its envelope read at the
 * timeline time), the lane fader (mute / solo / master folded in) and balance pan. A mono item leaves as
 * stereo — the same signal on both channels, as the mixer would upmix it — so the pan can place it. Last, the equaliser of
 * the submix bus the track goes to: linear, so on each member lane it sums to the bus's EQ of their sum.
 */
@UnstableApi
class GainFadeAudioProcessor(
    private val registry: LiveLookRegistry,
    private val laneTrackId: String,
    private val clipId: String?,
    /** Where this item starts inside its clip, for fades that span a cut. */
    private val clipOffsetUs: Long,
    private val clipDurationUs: Long,
    /** The clip's equal-power cross fade-in over its first [crossInUs] (Resolve's Cross Fade +3 dB). */
    private val crossInUs: Long = 0,
    /**
     * How long the item plays on past the clip's end under the cross fade into the next clip, fading out with equal
     * power over that time. The clip's own fades are done by then: one that faded out stays silent.
     */
    private val crossOutUs: Long = 0,
    /** A speed point's join into this item: an equal-power fade-in over its first [joinInUs] (see [com.example.prismstudio.p40b43a88.domain.model.AudioSegment.joinInFrames]). */
    private val joinInUs: Long = 0,
    /** How long the item plays on past a speed point at [pieceUs], fading out with equal power under the next item's join. */
    private val joinOutUs: Long = 0,
    /** The item's own length, up to that speed point. */
    private val pieceUs: Long = Long.MAX_VALUE,
    /** A render lines the item up to the sample and gives exactly its planned length; null in the preview. */
    private val alignment: AudioAlignment? = null,
    /** Where the item starts on its lane, for handing the reverb on ([reverbLink]). */
    private val laneStartUs: Long = 0,
    /** Item time past which the sound is not heard, only its track's reverb ringing on (see [com.example.prismstudio.p40b43a88.domain.model.AudioSegment.tailFrames]). */
    private val dryEndUs: Long = Long.MAX_VALUE,
    /** The lane's reverb hand-over between items; null keeps each item's room to itself. */
    private val reverbLink: ReverbLink? = null,
) : BaseAudioProcessor() {

    private var positionUs = 0L
    private var framesSinceFlush = 0L

    // Lining up (a render): input frames still to drop at the head, frames of silence still to put first, frames given so
    // far and in all, and the silence still owed once the input has ended.
    private var dropLeft = -1L
    private var padLeft = 0L
    private var given = 0L
    private var target = Long.MAX_VALUE
    private var tailLeft = 0L

    // The clip's own EQ, then the track's; the bus's after everything the track does.
    private val clipEq = EqChain()
    private val laneEq = EqChain()
    private val busEq = EqChain()

    // The track's dynamics and their envelope.
    private var dynamicsSource: TrackDynamics? = null
    private var dynamicsRate = 0
    private var dynamics: DynamicsGain? = null
    // The track's de-esser and its filters and level.
    private var deEsserSource: TrackDeEsser? = null
    private var deEsserKey = 0
    private var deEsser: DeEsserGain? = null
    private var frame = FloatArray(2)
    // The track's reverb: its tank (taken over from the item before when this one starts where that one ended), whether
    // the item has yet to take one, and the item time its next output frame stands for.
    private var tank: ReverbTank? = null
    private var fresh = true
    private var nextItemUs = 0L
    private val pair = FloatArray(2)
    // The bus EQ has yet to take the memory the item before left where this one starts.
    private var busFresh = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 1) return inputAudioFormat
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val mono = channels == 1
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val sampleBytes = if (isFloat) 4 else 2
        val frames = remaining / (channels * sampleBytes)
        val rate = format.sampleRate.coerceAtLeast(1)
        // A render lines the item up: the input's head is dropped, silence goes first where the sound starts late, and
        // nothing goes out past the item's length. The preview takes every frame as it comes.
        val align = alignment
        var skip = 0
        var pad = 0
        var room = frames
        if (align != null) {
            if (dropLeft < 0) startAlignment(align, rate)
            skip = minOf(frames.toLong(), dropLeft).toInt()
            dropLeft -= skip
            pad = minOf(padLeft, target - given).coerceAtLeast(0).toInt()
            padLeft -= pad
            room = minOf((frames - skip).toLong(), target - given - pad).coerceAtLeast(0).toInt()
        }
        val outChannels = if (mono) 2 else channels
        val out = replaceOutputBuffer((pad + room) * outChannels * sampleBytes)
        val lane = registry.laneMix(laneTrackId)
        val firstItemUs = if (align != null) positionUs + (given + skipFrames) * 1_000_000L / rate
        else positionUs + framesSinceFlush * 1_000_000L / rate
        val room0 = prepareReverb(lane?.reverb, rate, firstItemUs)
        busEq.prepare(lane?.busEq, rate, outChannels)
        if (busFresh) {
            busFresh = false
            if (busEq.active) reverbLink?.takeEq(rate, laneStartUs + firstItemUs)?.let(busEq::restore)
        }
        // The silence put first still runs through the room: a tail handed on from the item before rings on in it.
        repeat(pad) { putReverbSilence(out, room0, outChannels, isFloat) }
        val clip = clipId?.let(registry::clipMix)
        val laneGain = lane?.gain ?: 1f
        // The clip's pan (Resolve's inspector Pan) balances first, then the track's: the two side gains multiply.
        val pan = lane?.pan ?: 0f
        val laneLeft = if (pan > 0f) 1f - pan else 1f
        val laneRight = if (pan < 0f) 1f + pan else 1f
        val fixedPan = clip?.takeIf { !it.panMoves }?.pan ?: 0f
        var left = laneLeft * balanceLeft(fixedPan)
        var right = laneRight * balanceRight(fixedPan)
        clipEq.prepare(clip?.eq, rate, channels)
        laneEq.prepare(lane?.eq, rate, channels)
        val dyn = prepareDynamics(lane?.dynamics, rate)
        val ess = prepareDeEsser(lane?.deEsser, rate, channels)
        val duckDepth = lane?.duckDepthDb ?: 0f
        val duck = if (duckDepth > 0f) registry.duck(laneTrackId) else null
        val clipStartUs = clip?.startUs ?: 0L
        if (frame.size < channels) frame = FloatArray(channels)
        val fullScale = if (isFloat) 1.0 else FULL_SCALE_16
        // The dropped head still runs through the EQ and the dynamics' detector, unheard: they meet the clip's first frame
        // warmed up on the sound before it, as an unbroken track would, instead of starting cold under a short join.
        val mapping = clip?.channels ?: AudioChannels.STEREO
        repeat(skip) {
            var peak = 0f
            for (ch in 0 until channels) frame[ch] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort().toFloat()
            mapping.map(frame, channels)
            for (ch in 0 until channels) frame[ch] = equalise(frame[ch], ch)
            ess?.process(frame, fullScale)
            for (ch in 0 until channels) peak = maxOf(peak, abs(frame[ch]))
            dyn?.gain(peak / fullScale)
        }
        for (i in 0 until room) {
            // Item time: by the frames given (a render's lined-up item) or by the frames come in (the preview).
            val itemUs = if (align != null) positionUs + (given + pad + i + skipFrames) * 1_000_000L / rate
            else positionUs + (framesSinceFlush + i) * 1_000_000L / rate
            val timeUs = clipOffsetUs + itemUs
            val dry = if (itemUs >= dryEndUs) 0f else 1f
            val clipGain = (clip?.gainAt(timeUs) ?: 1f) * fade(timeUs, clip) * crossIn(timeUs) * crossOut(timeUs) * join(itemUs) * dry
            if (clip != null && clip.panMoves) {
                val clipPan = clip.panAt(timeUs)
                left = laneLeft * balanceLeft(clipPan)
                right = laneRight * balanceRight(clipPan)
            }
            var peak = 0f
            // The clip's channel mapping first (Resolve's clip attributes), then its EQ and gain.
            for (ch in 0 until channels) frame[ch] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort().toFloat()
            mapping.map(frame, channels)
            for (ch in 0 until channels) frame[ch] = equalise(frame[ch], ch) * clipGain
            // Fairlight's De-Esser after the EQ, before the dynamics, which hear what it leaves.
            ess?.process(frame, fullScale)
            for (ch in 0 until channels) peak = maxOf(peak, abs(frame[ch]))
            var gain = laneGain * (dyn?.gain(peak / fullScale)?.toFloat() ?: 1f)
            if (duck != null) gain *= duck.gainAt(clipStartUs + timeUs, duckDepth)
            if (mono) {
                pair[0] = frame[0] * gain * left
                pair[1] = frame[0] * gain * right
                // Fairlight FX Reverb last, on what the fader and pan give.
                room0?.process(pair)
                put(out, busEq.process(pair[0], 0), isFloat)
                put(out, busEq.process(pair[1], 1), isFloat)
                continue
            }
            for (ch in 0 until channels) {
                frame[ch] *= gain * when (ch) {
                    0 -> left
                    1 -> right
                    else -> 1f
                }
            }
            if (room0 != null) {
                pair[0] = frame[0]
                pair[1] = frame[1]
                room0.process(pair)
                frame[0] = pair[0]
                frame[1] = pair[1]
            }
            for (ch in 0 until channels) put(out, busEq.process(frame[ch], ch), isFloat)
        }
        // What lies past the item's length (a render) goes unheard.
        inputBuffer.position(inputBuffer.limit())
        framesSinceFlush += frames
        given += pad + room
        nextItemUs = if (align != null) positionUs + (given + skipFrames) * 1_000_000L / rate
        else positionUs + framesSinceFlush * 1_000_000L / rate
        out.flip()
    }

    /**
     * The tank for [settings] (null while the track has no reverb): at the item's first frame, at [itemUs], the one the item
     * before left ringing where this one starts, else a silent room.
     */
    private fun prepareReverb(settings: TrackReverb?, rate: Int, itemUs: Long): ReverbTank? {
        if (settings == null) {
            tank = null
            fresh = false
            return null
        }
        var t = tank
        if (t == null || t.sampleRate != rate) {
            t = (if (fresh) reverbLink?.take(rate, laneStartUs + itemUs) else null) ?: ReverbTank(rate)
            tank = t
        }
        fresh = false
        t.tune(settings)
        return t
    }

    /** A frame of silence (all [channels]) through [room] and the bus EQ: what still rings in them, else nothing. */
    private fun putReverbSilence(out: ByteBuffer, room: ReverbTank?, channels: Int, isFloat: Boolean) {
        if (room == null || channels < 2) {
            for (ch in 0 until channels) put(out, busEq.process(0f, ch), isFloat)
            return
        }
        pair[0] = 0f
        pair[1] = 0f
        room.process(pair)
        put(out, busEq.process(pair[0], 0), isFloat)
        put(out, busEq.process(pair[1], 1), isFloat)
        repeat(channels - 2) { put(out, 0f, isFloat) }
    }

    /** The item has given all it will: its room (and its bus EQ's memory) goes on to the item after it on the lane. */
    private fun handOnReverb() {
        if (busEq.active) reverbLink?.putEq(busEq.memory(), inputAudioFormat.sampleRate.coerceAtLeast(1), laneStartUs + nextItemUs)
        val t = tank ?: return
        reverbLink?.put(t, laneStartUs + nextItemUs)
        tank = null
    }

    /** Lead-in frames a lane's first item skips (counted in its item time, so its fades stay on the timeline). */
    private var skipFrames = 0L

    /** Where the item's first decoded sample lies decides how much of its head goes, or how much silence comes first. */
    private fun startAlignment(align: AudioAlignment, rate: Int) {
        val first = align.starts.firstSampleUs(align.key)
        if (first == null) Log.w(TAG, "No first sample noted for ${align.key}: the sound is taken as starting where its window opens")
        val head = align.headFrames(first ?: align.windowStartUs, rate)
        dropLeft = head.coerceAtLeast(0)
        padLeft = (-head).coerceAtLeast(0)
        target = align.lengthFrames(rate)
        skipFrames = Math.round(align.skipUs * rate / 1_000_000.0)
    }

    /**
     * The silence owed when the input ends short of the item's length (a render), given in chunks of at most a second as the
     * pipeline asks for output ([getOutput]) — never here: the output buffer the pipeline may still be reading from is the
     * one a new chunk would be written into (one buffer is reused), and frames were lost (rev 97). The pipeline may say the
     * input has ended more than once; what is owed is worked out afresh each time.
     */
    override fun onQueueEndOfStream() {
        val align = alignment
        if (align == null) {
            handOnReverb()
            return
        }
        if (dropLeft < 0) startAlignment(align, inputAudioFormat.sampleRate.coerceAtLeast(1))
        tailLeft = (target - given).coerceAtLeast(0)
        if (tailLeft == 0L) handOnReverb()
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (!output.hasRemaining() && tailLeft > 0) {
            giveTail()
            return super.getOutput()
        }
        return output
    }

    override fun isEnded(): Boolean = super.isEnded() && tailLeft == 0L

    private fun giveTail() {
        if (tailLeft <= 0) return
        val format = inputAudioFormat
        val channels = if (format.channelCount == 1) 2 else format.channelCount.coerceAtLeast(1)
        val sampleBytes = if (format.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val rate = format.sampleRate.coerceAtLeast(1)
        val chunk = minOf(tailLeft, rate.toLong()).toInt()
        val out = replaceOutputBuffer(chunk * channels * sampleBytes)
        // The owed silence rings with the room, as the input would have.
        val room = tank
        if (room != null || busEq.active) repeat(chunk) { putReverbSilence(out, room, channels, format.encoding == C.ENCODING_PCM_FLOAT) }
        else repeat(chunk * channels * sampleBytes) { out.put(0) }
        out.flip()
        tailLeft -= chunk
        given += chunk
        nextItemUs = positionUs + (given + skipFrames) * 1_000_000L / rate
        if (tailLeft == 0L) handOnReverb()
    }


    /** The de-esser for [settings] at [rate] over [channels]; it carries on through a new but equal instance. */
    private fun prepareDeEsser(settings: TrackDeEsser?, rate: Int, channels: Int): DeEsserGain? {
        val key = rate * 16 + channels
        if (settings === deEsserSource && key == deEsserKey) return deEsser
        val same = settings == deEsserSource && key == deEsserKey
        deEsserSource = settings
        deEsserKey = key
        if (!same) deEsser = settings?.let { DeEsserGain(it, rate, channels) }
        return deEsser
    }

    /** The envelope for [settings] at [rate]; it carries on through a new but equal instance (every live mix update). */
    private fun prepareDynamics(settings: TrackDynamics?, rate: Int): DynamicsGain? {
        if (settings === dynamicsSource && rate == dynamicsRate) return dynamics
        val same = settings == dynamicsSource && rate == dynamicsRate
        dynamicsSource = settings
        dynamicsRate = rate
        if (!same) dynamics = settings?.let { DynamicsGain(it, rate) }
        return dynamics
    }

    /** The clip's EQ, then the track's. */
    private fun equalise(x: Float, channel: Int): Float = laneEq.process(clipEq.process(x, channel), channel)

    private fun put(out: ByteBuffer, value: Float, isFloat: Boolean) {
        if (isFloat) out.putFloat(value) else out.putShort(value.roundToInt().coerceIn(-32768, 32767).toShort())
    }

    /** The clip's fade handles [timeUs] into it, each bent by its curve ([ClipFades.shape]). */
    private fun fade(timeUs: Long, clip: LiveLookRegistry.ClipMix?): Float {
        if (clip == null) return 1f
        var g = 1f
        if (clip.fadeInUs > 0 && timeUs < clip.fadeInUs) g = ClipFades.shape(timeUs.toFloat() / clip.fadeInUs, clip.fadeInCurve)
        val untilEnd = clipDurationUs - timeUs
        if (clip.fadeOutUs > 0 && untilEnd < clip.fadeOutUs) g = minOf(g, ClipFades.shape(untilEnd.toFloat() / clip.fadeOutUs, clip.fadeOutCurve))
        return g.coerceIn(0f, 1f)
    }

    /** The equal-power fade-in of a cross fade: sin(π/2·t), so its square and the handle's add up to one. */
    private fun crossIn(timeUs: Long): Float {
        if (crossInUs <= 0 || timeUs >= crossInUs) return 1f
        return sin(HALF_PI * timeUs.coerceAtLeast(0) / crossInUs).toFloat()
    }

    /**
     * The equal-power fade-out past the clip's end: cos(π/2·t) from there ([fade] already keeps a clip that faded out
     * silent).
     */
    private fun crossOut(timeUs: Long): Float {
        if (crossOutUs <= 0 || timeUs <= clipDurationUs) return 1f
        val t = ((timeUs - clipDurationUs).toDouble() / crossOutUs).coerceIn(0.0, 1.0)
        return cos(HALF_PI * t).toFloat()
    }

    /**
     * A speed point's join, [itemUs] into the item: sin(π/2·t) in, cos(π/2·t) out, so the two powers add up to one. A
     * render's items are lined up to the sample, both sides of a point playing the same moment of sound, so its join is
     * only [ALIGNED_JOIN_US] long: over the planned overlap two steps of a speed ramp — the same sound a little apart in
     * pitch — drifted out of phase and dipped (rev 95, −9 dB on a tone).
     */
    private fun join(itemUs: Long): Float {
        val joinIn = if (alignment != null) minOf(joinInUs, ALIGNED_JOIN_US) else joinInUs
        val joinOut = if (alignment != null) minOf(joinOutUs, ALIGNED_JOIN_US) else joinOutUs
        var g = 1f
        if (joinIn > 0 && itemUs < joinIn) g = sin(HALF_PI * itemUs.coerceAtLeast(0) / joinIn).toFloat()
        if (joinOut > 0 && itemUs > pieceUs) g *= cos(HALF_PI * ((itemUs - pieceUs).toDouble() / joinOut).coerceIn(0.0, 1.0)).toFloat()
        return g
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        positionUs = streamMetadata.positionOffsetUs
        framesSinceFlush = 0
        tank = null
        fresh = true
        nextItemUs = positionUs
        clipEq.clear()
        laneEq.clear()
        busEq.clear()
        busFresh = true
        dynamics?.reset()
        deEsser?.reset()
        resetAlignment()
    }

    override fun onReset() {
        positionUs = 0
        framesSinceFlush = 0
        tank = null
        fresh = true
        nextItemUs = 0
        clipEq.clear()
        laneEq.clear()
        busEq.clear()
        busFresh = true
        dynamics?.reset()
        deEsser?.reset()
        resetAlignment()
    }

    private fun resetAlignment() {
        dropLeft = -1
        padLeft = 0
        given = 0
        target = Long.MAX_VALUE
        tailLeft = 0
        skipFrames = 0
    }

    private companion object {
        const val TAG = "PrismAudio"

        /** 16-bit samples at full scale. */
        const val FULL_SCALE_16 = 32768.0

        /** A lined-up join's length: long enough not to click, too short for the two sides to drift apart. */
        const val ALIGNED_JOIN_US = 5_000L
        const val HALF_PI = Math.PI / 2
    }
}

/** An EQ's biquad sections and their filter memory (x1, x2, y1, y2 per channel and section), run sample by sample. */
internal class EqChain {
    private var source: TrackEq? = null
    private var rate = 0
    private var stages: List<Biquad> = emptyList()
    private var memory = DoubleArray(0)

    /** Sections for [eq] at [sampleRate]; the filter memory survives a new but equal EQ (every live mix update). */
    fun prepare(eq: TrackEq?, sampleRate: Int, channels: Int) {
        if (eq === source && sampleRate == rate) return
        val sameSettings = eq == source && sampleRate == rate
        source = eq
        rate = sampleRate
        val next = eq?.stages(sampleRate).orEmpty()
        if (!sameSettings && next.size != stages.size || memory.size != channels * next.size * 4) memory = DoubleArray(channels * next.size * 4)
        stages = next
    }

    fun process(x: Float, channel: Int): Float {
        if (stages.isEmpty()) return x
        var v = x.toDouble()
        for (s in stages.indices) {
            val q = stages[s]
            val o = (channel * stages.size + s) * 4
            val y = q.b0 * v + q.b1 * memory[o] + q.b2 * memory[o + 1] - q.a1 * memory[o + 2] - q.a2 * memory[o + 3]
            memory[o + 1] = memory[o]
            memory[o] = v
            memory[o + 3] = memory[o + 2]
            memory[o + 2] = y
            v = y
        }
        return v.toFloat()
    }

    /** Forgets the filter memory (a flush). */
    fun clear() {
        memory.fill(0.0)
    }

    /** It filters: some band changes the sound. */
    val active: Boolean get() = stages.isNotEmpty()

    /** A copy of the filter memory, to carry on in another chain ([restore]). */
    fun memory(): DoubleArray = memory.copyOf()

    /** Carries on from [m] (a chain of the same shape's [memory]). */
    fun restore(m: DoubleArray) {
        if (m.size == memory.size) m.copyInto(memory)
    }
}

/** A balance pan's left gain at [pan] (−1…1): whole from the left to the centre, down to nothing at hard right. */
internal fun balanceLeft(pan: Float): Float = if (pan > 0f) 1f - pan.coerceAtMost(1f) else 1f

/** A balance pan's right gain at [pan]: whole from the right to the centre, down to nothing at hard left. */
internal fun balanceRight(pan: Float): Float = if (pan < 0f) 1f + pan.coerceAtLeast(-1f) else 1f
