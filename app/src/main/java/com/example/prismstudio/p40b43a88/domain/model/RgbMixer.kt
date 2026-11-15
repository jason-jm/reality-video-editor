package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs

/** One output channel of an [RgbMixer]: how much of the red, green and blue input it takes. */
data class ChannelMix(val r: Float, val g: Float, val b: Float) {
    operator fun get(input: MixerInput): Float = when (input) {
        MixerInput.RED -> r
        MixerInput.GREEN -> g
        MixerInput.BLUE -> b
    }

    fun with(input: MixerInput, value: Float): ChannelMix = when (input) {
        MixerInput.RED -> copy(r = value)
        MixerInput.GREEN -> copy(g = value)
        MixerInput.BLUE -> copy(b = value)
    }

    fun of(r: Float, g: Float, b: Float): Float = this.r * r + this.g * g + this.b * b
    val sum: Float get() = r + g + b
    fun lerp(to: ChannelMix, t: Float) = ChannelMix(r + (to.r - r) * t, g + (to.g - g) * t, b + (to.b - b) * t)
}

/** An input channel of the mixer: a slider in each output. */
enum class MixerInput(val label: String) { RED("R"), GREEN("G"), BLUE("B") }

/** An output channel of the mixer (`mixer-output-*`): its three sliders. */
enum class MixerOutput(val label: String) { RED("红色输出"), GREEN("绿色输出"), BLUE("蓝色输出") }

/** Resolve's channel swaps: two outputs trade their mixes (on an untouched mixer, two channels trade places). */
enum class MixerSwap(val label: String, val a: MixerOutput, val b: MixerOutput) {
    RED_GREEN("红 ⇄ 绿", MixerOutput.RED, MixerOutput.GREEN),
    GREEN_BLUE("绿 ⇄ 蓝", MixerOutput.GREEN, MixerOutput.BLUE),
    RED_BLUE("红 ⇄ 蓝", MixerOutput.RED, MixerOutput.BLUE),
}

/**
 * Resolve's RGB Mixer: each output channel a mix of the three inputs (−2…3 each; the identity untouched). [preserveLuminance]
 * scales the mixed colour back to the input's Rec.709 luma, so a mix changes colour and not brightness. [monochrome] makes
 * the picture grey — [mono]'s mix of the inputs, divided by its sum while luminance is preserved (a grey stays the grey it
 * was). Part of the node's colour correction, so it lands in the grade's LUT.
 */
data class RgbMixer(
    val red: ChannelMix = IDENTITY_RED,
    val green: ChannelMix = IDENTITY_GREEN,
    val blue: ChannelMix = IDENTITY_BLUE,
    val preserveLuminance: Boolean = true,
    val monochrome: Boolean = false,
    val mono: ChannelMix = MONO_DEFAULT,
) {
    /** Leaves every colour as it is. */
    val isIdentity: Boolean get() = !monochrome && red == IDENTITY_RED && green == IDENTITY_GREEN && blue == IDENTITY_BLUE

    fun output(output: MixerOutput): ChannelMix = when (output) {
        MixerOutput.RED -> red
        MixerOutput.GREEN -> green
        MixerOutput.BLUE -> blue
    }

    fun withOutput(output: MixerOutput, mix: ChannelMix): RgbMixer = when (output) {
        MixerOutput.RED -> copy(red = mix)
        MixerOutput.GREEN -> copy(green = mix)
        MixerOutput.BLUE -> copy(blue = mix)
    }

    /** [swap]'s two outputs with each other's mix. */
    fun swapped(swap: MixerSwap): RgbMixer = withOutput(swap.a, output(swap.b)).withOutput(swap.b, output(swap.a))

    /** A fraction [t] of the way to [to]: every mix in between, the switches changing half way. */
    fun lerp(to: RgbMixer, t: Float): RgbMixer = RgbMixer(
        red.lerp(to.red, t), green.lerp(to.green, t), blue.lerp(to.blue, t),
        if (t < 0.5f) preserveLuminance else to.preserveLuminance,
        if (t < 0.5f) monochrome else to.monochrome,
        mono.lerp(to.mono, t),
    )

    /** Mixes one display-referred colour into [out]. */
    fun apply(r: Float, g: Float, b: Float, out: FloatArray) {
        if (monochrome) {
            val sum = mono.sum
            val y = mono.of(r, g, b).let { if (preserveLuminance && abs(sum) > MIN_SUM) it / sum else it }
            out[0] = y; out[1] = y; out[2] = y
            return
        }
        var nr = red.of(r, g, b)
        var ng = green.of(r, g, b)
        var nb = blue.of(r, g, b)
        if (preserveLuminance) {
            val before = luma(r, g, b)
            val after = luma(nr, ng, nb)
            if (after > MIN_LUMA) {
                val k = (before / after).coerceAtMost(MAX_GAIN)
                nr *= k; ng *= k; nb *= k
            } else if (before > MIN_LUMA) {
                // Mixed down to black: lift it back to the input's level as a grey.
                nr += before - after; ng += before - after; nb += before - after
            }
        }
        out[0] = nr; out[1] = ng; out[2] = nb
    }

    companion object {
        const val MIN = -2f
        const val MAX = 3f
        const val STEP = 0.01f
        val IDENTITY_RED = ChannelMix(1f, 0f, 0f)
        val IDENTITY_GREEN = ChannelMix(0f, 1f, 0f)
        val IDENTITY_BLUE = ChannelMix(0f, 0f, 1f)

        /** A grey by Rec.709 luma, where a monochrome mix starts. */
        val MONO_DEFAULT = ChannelMix(0.2126f, 0.7152f, 0.0722f)
        val NEUTRAL = RgbMixer()
        private const val MIN_SUM = 1e-3f
        private const val MIN_LUMA = 1e-4f

        /** The most preserving luminance may brighten a colour a mix nearly darkened away. */
        private const val MAX_GAIN = 8f

        private fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
