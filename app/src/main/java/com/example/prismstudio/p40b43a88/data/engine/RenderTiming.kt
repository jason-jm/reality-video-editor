package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.C
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram
import java.util.concurrent.Executor

/** The output's frame grid: slot k shows from k / [frameRate] seconds. */
class FrameSlots(private val frameRate: Int) {

    /** The slot a frame at [timeUs] takes: up to a quarter of a frame early still takes it (sources rarely sit exactly on the grid). */
    fun slotOf(timeUs: Long): Long = Math.floorDiv((timeUs + 250_000L / frameRate) * frameRate, 1_000_000L)

    /** Where slot [slot] starts, in µs — rounded down, as CompositionFactory.framesToUs places clips. */
    fun timeOf(slot: Long): Long = Math.floorDiv(slot * 1_000_000L, frameRate.toLong())
}

/**
 * Keeps the first frame of each output frame slot, so a source faster than [frameRate] plays at the
 * output's rate. It decides frame by frame and never holds one back: Media3's default `FrameDropEffect`
 * keeps the newest frame until the next one arrives and discards it when an input stream ends, which
 * cost every clip its last frame.
 */
class FrameGate(frameRate: Int) {
    private val slots = FrameSlots(frameRate)
    private var lastSlot = Long.MIN_VALUE

    /** Whether the frame at [timeUs] (sequence time) is kept. */
    fun keeps(timeUs: Long): Boolean {
        val slot = slots.slotOf(timeUs)
        if (slot <= lastSlot) return false
        lastSlot = slot
        return true
    }

    fun reset() {
        lastSlot = Long.MIN_VALUE
    }
}

/**
 * Where a clip's decoded frames land in a render: at the clip's [speed] from [startUs] (the item's start
 * in the sequence), and nothing from [untilUs] on — what the decoder read past the clip's end (see
 * [RenderSourceFactory]). The item's window opens [leadUs] of source before the clip's first moment, so the frame on
 * show there arrives too: everything is placed that much earlier, the clip's first moment at [startUs].
 */
class ClipTiming(val startUs: Long, private val speed: Float, val untilUs: Long, private val leadUs: Long = 0L) {

    /** The render time of the frame decoded at [timeUs] (sequence time), or null when it is past the clip. */
    fun place(timeUs: Long): Long? {
        val into = timeUs - startUs - leadUs
        val placed = if (speed == 1f) startUs + into else startUs + (into / speed.toDouble()).toLong()
        return placed.takeIf { it < untilUs - TOLERANCE_US }
    }

    private companion object {
        /** A frame within this of the clip's end belongs to the next frame slot, which is past it. */
        const val TOLERANCE_US = 250L
    }
}

/**
 * A clip of a render at a constant [frameRate], as Resolve renders: every output frame slot of the clip shows exactly
 * one frame, at the slot's start. A frame placed by [timing] takes its slot. A slot no frame reaches — a slowed clip,
 * a source slower than the output, a late first frame, the end of a source that ran short — is filled from the frame
 * before it: shown again (Retime Process Nearest) or, with [blend] (Frame Blend), mixed with the frame after it by
 * where the slot falls between the two; the clip's first and last slots have one neighbour only and repeat it. A
 * second frame for a slot is dropped; nothing goes past the clip. A frame placed before the clip's first slot is the
 * one on show as the clip starts (its window opens early for it, see [ClipTiming]): the latest such is held, and fills
 * the first slots until the clip's own frames come — all of them when none comes before the clip ends (a step of a
 * speed ramp shorter than a source frame).
 *
 * Off [render] (the preview, which plays frames at their own times and holds each until the next) only the slots
 * between two frames are filled; frames keep their times, and nothing is dropped or added at the clip's ends.
 */
class ClipCadence(private val timing: ClipTiming, frameRate: Int, private val blend: Boolean = false, private val render: Boolean = true) {

    /** An output made from the frame shown so far: at [timeUs], blended [toward] of the way to the next frame (0: shown again). */
    data class Fill(val timeUs: Long, val toward: Float = 0f)

    /** A kept frame: the frame shown so far first makes [fillsBefore], then this one shows at [times]. */
    data class Step(val fillsBefore: List<Fill>, val times: List<Long>)

    private val slots = FrameSlots(frameRate)
    private val firstSlot = slots.slotOf(timing.startUs)
    private val endSlot = slots.slotOf(timing.untilUs)
    private var lastSlot = firstSlot - 1

    /** Where the frame shown so far lies (its placed time, before snapping); null before the first. */
    private var lastAt: Long? = null

    /** What the frame decoded at [timeUs] (sequence time) does, or null when it is dropped. */
    fun place(timeUs: Long): Step? {
        val at = timing.place(timeUs) ?: return null
        val shown = lastAt
        if (!render) {
            val fills = if (shown == null) emptyList() else (slots.slotOf(shown) + 1 until slots.slotOf(at)).map { fill(it, shown, at) }
            lastAt = at
            return Step(fills, listOf(at))
        }
        // A frame before the clip's first slot (up to a quarter frame early still takes it) was on show when the clip
        // starts, unless a later one was: held, the latest only, for the slots before the clip's own frames.
        val slot = slots.slotOf(at)
        if (slot < firstSlot) {
            if (lastSlot >= firstSlot) return null
            lastAt = at
            return Step(emptyList(), emptyList())
        }
        if (slot <= lastSlot || slot >= endSlot) return null
        val skipped = lastSlot + 1 until slot
        lastSlot = slot
        lastAt = at
        // A late first frame fills the slots before it too: there is nothing earlier in the clip to show.
        return if (shown == null) Step(emptyList(), skipped.map(slots::timeOf) + slots.timeOf(slot))
        else Step(skipped.map { fill(it, shown, at) }, listOf(slots.timeOf(slot)))
    }

    /** Where the last frame repeats to the clip's end once the source has run out; nothing when no frame came. */
    fun finish(): List<Fill> {
        if (!render || lastAt == null) return emptyList()
        val tail = (lastSlot + 1 until endSlot).map { Fill(slots.timeOf(it)) }
        lastSlot = maxOf(lastSlot, endSlot - 1)
        return tail
    }

    fun reset() {
        lastSlot = firstSlot - 1
        lastAt = null
    }

    /** Slot [slot] between the frames placed at [from] and [to]: the first again, or the two blended by where it falls. */
    private fun fill(slot: Long, from: Long, to: Long): Fill {
        val time = slots.timeOf(slot)
        val toward = if (blend && to > from) ((time - from).toFloat() / (to - from)).coerceIn(0f, 1f) else 0f
        return Fill(time, toward)
    }
}

/** [FrameGate] as a Media3 video effect (composition level). */
@UnstableApi
class FrameRateEffect(private val frameRate: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        val gate = FrameGate(frameRate)
        return Gate(place = { t -> t.takeIf(gate::keeps) }, onFlush = gate::reset)
    }
}

/**
 * [ClipCadence] as a Media3 video effect on a clip's item, before anything that depends on a frame's time (grade
 * keyframes, transitions, titles, the layers beneath), so a repeated or blended frame is graded and composited at its
 * own time.
 */
@UnstableApi
class ClipTimingEffect(
    private val timing: ClipTiming,
    private val frameRate: Int,
    private val blend: Boolean = false,
    private val render: Boolean = true,
    /** Resolve's Optical Flow: the in-between frames follow the motion ([OpticalFlowInterpolator]) instead of a cross-fade. */
    private val opticalFlow: Boolean = false,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        CadenceProgram(ClipCadence(timing, frameRate, blend, render), useHdr, opticalFlow)
}

/** Passes a frame on at the time [place] gives it, or drops it when that is null; nothing is held back. */
@UnstableApi
private class Gate(private val place: (Long) -> Long?, private val onFlush: () -> Unit = {}) : PassthroughShaderProgram() {

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        val at = place(presentationTimeUs)
        if (at != null) {
            super.queueInputFrame(glObjectsProvider, inputTexture, at)
        } else {
            inputListener.onInputFrameProcessed(inputTexture)
            inputListener.onReadyToAcceptInputFrame()
        }
    }

    override fun flush() {
        super.flush()
        onFlush()
    }
}

/**
 * Runs a [ClipCadence]. Each kept frame is drawn once into a texture of its own and goes out at every slot it shows
 * in: a repeat is the same texture again with a later time, once the stages after have given every earlier output of
 * it back. A blended fill is drawn into a texture of its own from the frames on either side. Outputs leave in order
 * through a queue, which waits while every blend texture is still out; the input's texture goes back at once.
 */
@UnstableApi
private class CadenceProgram(private val cadence: ClipCadence, private val highPrecision: Boolean, opticalFlow: Boolean = false) : GlShaderProgram {

    /**
     * A texture this program draws, and how many of its outputs are still out or still queued; [serial] names what it
     * holds (a new number each time a frame is copied in), so the optical flow knows a pair of frames it has seen.
     */
    private class Frame(val texture: GlTextureInfo) {
        var uses = 0
        var serial = 0L
    }

    private val flow = if (opticalFlow) OpticalFlowInterpolator() else null
    private var serials = 0L

    /** A queued output: [frame] at [timeUs], or blended [toward] of the way to [next]. */
    private class Output(val frame: Frame, val timeUs: Long, val next: Frame? = null, val toward: Float = 0f)

    private var inputListener: GlShaderProgram.InputListener = object : GlShaderProgram.InputListener {}
    private var outputListener: GlShaderProgram.OutputListener = object : GlShaderProgram.OutputListener {}
    private var errorListener = GlShaderProgram.ErrorListener {}
    private var errorExecutor = Executor { it.run() }
    private var glObjectsProvider: GlObjectsProvider? = null
    private val copyProgram: GlProgram = program(COPY_SHADER)
    private val blendProgram: GlProgram = program(BLEND_SHADER)

    /** The source frames: the one shown last, which may still repeat or blend, and room for the next. */
    private val copies = mutableListOf<Frame>()

    /** Blended fills, each out once. */
    private val blends = mutableListOf<Frame>()
    private val queue = ArrayDeque<Output>()
    private var shown: Frame? = null
    private var ending = false

    /** Frames the input was told it may send and has not sent yet. */
    private var promised = 0

    override fun setInputListener(inputListener: GlShaderProgram.InputListener) {
        this.inputListener = inputListener
        offerRoom()
    }

    override fun setOutputListener(outputListener: GlShaderProgram.OutputListener) {
        this.outputListener = outputListener
    }

    override fun setErrorListener(executor: Executor, errorListener: GlShaderProgram.ErrorListener) {
        errorExecutor = executor
        this.errorListener = errorListener
    }

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        this.glObjectsProvider = glObjectsProvider
        promised = (promised - 1).coerceAtLeast(0)
        val step = cadence.place(presentationTimeUs)
        if (step != null) {
            try {
                val copy = copyOf(glObjectsProvider, inputTexture)
                shown?.let { last ->
                    step.fillsBefore.forEach { fill -> enqueue(Output(last, fill.timeUs, copy.takeIf { fill.toward > 0f }, fill.toward)) }
                }
                shown = copy
                step.times.forEach { enqueue(Output(copy, it)) }
            } catch (e: GlUtil.GlException) {
                fail(e, presentationTimeUs)
            }
        }
        inputListener.onInputFrameProcessed(inputTexture)
        drain()
        offerRoom()
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        val frame = copies.firstOrNull { it.texture.texId == outputTexture.texId } ?: blends.firstOrNull { it.texture.texId == outputTexture.texId }
        if (frame != null && frame.uses > 0) frame.uses--
        drain()
        offerRoom()
    }

    override fun signalEndOfCurrentInputStream() {
        shown?.let { last -> cadence.finish().forEach { enqueue(Output(last, it.timeUs)) } }
        ending = true
        drain()
    }

    override fun flush() {
        queue.clear()
        copies.forEach { it.uses = 0 }
        blends.forEach { it.uses = 0 }
        shown = null
        ending = false
        cadence.reset()
        flow?.reset()
        promised = 0
        inputListener.onFlush()
        offerRoom()
    }

    override fun release() {
        try {
            (copies + blends).forEach { it.texture.release() }
            copies.clear()
            blends.clear()
            copyProgram.delete()
            blendProgram.delete()
            flow?.release()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun enqueue(output: Output) {
        output.frame.uses++
        output.next?.let { it.uses++ }
        queue.addLast(output)
    }

    /** Sends the queued outputs in order; a blend waits for a texture to draw into, and the stream ends after the last. */
    private fun drain() {
        while (queue.isNotEmpty()) {
            val output = queue.first()
            val next = output.next
            if (next == null) {
                queue.removeFirst()
                outputListener.onOutputFrameAvailable(output.frame.texture, output.timeUs)
                continue
            }
            val target = blendTarget(output.frame.texture) ?: return
            queue.removeFirst()
            try {
                val provider = glObjectsProvider
                if (flow != null && provider != null) {
                    flow.draw(provider, output.frame.texture, output.frame.serial, next.texture, next.serial, output.toward, target.texture)
                } else {
                    draw(blendProgram, target.texture) {
                        blendProgram.setSamplerTexIdUniform("uTexSampler", output.frame.texture.texId, 0)
                        blendProgram.setSamplerTexIdUniform("uNextSampler", next.texture.texId, 1)
                        blendProgram.setFloatUniform("uToward", output.toward)
                    }
                }
            } catch (e: GlUtil.GlException) {
                fail(e, output.timeUs)
            }
            // Drawn: the blend no longer needs its two frames.
            output.frame.uses--
            next.uses--
            target.uses = 1
            outputListener.onOutputFrameAvailable(target.texture, output.timeUs)
        }
        if (ending) {
            ending = false
            outputListener.onCurrentOutputStreamEnded()
        }
    }

    /** A copy the next frame can go into: not the frame shown last, and nothing out or queued of it. */
    private fun isFree(frame: Frame) = frame !== shown && frame.uses == 0

    /** Tells the input it may send a frame for each copy free to take it — once every fill of the last one is out. */
    private fun offerRoom() {
        if (queue.isNotEmpty()) return
        while (copies.count(::isFree) + (CAPACITY - copies.size) > promised) {
            promised++
            inputListener.onReadyToAcceptInputFrame()
        }
    }

    private fun copyOf(glObjectsProvider: GlObjectsProvider, input: GlTextureInfo): Frame {
        val copy = take(copies, glObjectsProvider, input.width, input.height, Int.MAX_VALUE, ::isFree)!!
        draw(copyProgram, copy.texture) { copyProgram.setSamplerTexIdUniform("uTexSampler", input.texId, 0) }
        copy.serial = ++serials
        return copy
    }

    /** A texture for a blended fill the size of [like], or null while all [BLEND_CAPACITY] are out. */
    private fun blendTarget(like: GlTextureInfo): Frame? {
        val provider = glObjectsProvider ?: return null
        return try {
            take(blends, provider, like.width, like.height, BLEND_CAPACITY) { it.uses == 0 }
        } catch (e: GlUtil.GlException) {
            fail(e, C.TIME_UNSET)
            null
        }
    }

    /** A free frame of [pool] the given size: a free one of another size is replaced, and a new one made while under [limit]. */
    private fun take(pool: MutableList<Frame>, provider: GlObjectsProvider, width: Int, height: Int, limit: Int, free: (Frame) -> Boolean): Frame? {
        pool.firstOrNull { free(it) && it.texture.width == width && it.texture.height == height }?.let { return it }
        pool.firstOrNull(free)?.let { resized ->
            resized.texture.release()
            pool.remove(resized)
        }
        if (pool.size >= limit) return null
        val texId = GlUtil.createTexture(width, height, highPrecision)
        return Frame(provider.createBuffersForTexture(texId, width, height)).also(pool::add)
    }

    /** Draws [program] over all of [target]: an exact copy or blend, alpha included (an overlay layer's letterbox stays clear). */
    private inline fun draw(program: GlProgram, target: GlTextureInfo, uniforms: () -> Unit) {
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        program.use()
        uniforms()
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private fun fail(e: GlUtil.GlException, presentationTimeUs: Long) {
        errorExecutor.execute { errorListener.onError(VideoFrameProcessingException(e, presentationTimeUs)) }
    }

    private companion object {
        const val CAPACITY = 2
        const val BLEND_CAPACITY = 2

        fun program(fragmentShader: String): GlProgram {
            val program = try {
                GlProgram(VERTEX_SHADER, fragmentShader)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            return program
        }

        const val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying highp vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        // highp: a texel centre on a 4K raster needs more than mediump's precision to land exactly.
        const val COPY_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
varying highp vec2 vTexSamplingCoord;
void main() {
  gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
}
"""

        // Frame Blend: the frames either side of a missing one, mixed by where it falls between them.
        const val BLEND_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uNextSampler;
uniform float uToward;
varying highp vec2 vTexSamplingCoord;
void main() {
  gl_FragColor = mix(texture2D(uTexSampler, vTexSamplingCoord), texture2D(uNextSampler, vTexSamplingCoord), uToward);
}
"""
    }
}
