package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A track's noise reduction (`noise-reduction-sheet`) — Fairlight's Noise Reduction in its automatic mode. Every source
 * file's own noise floor is measured once ([NoiseProfile]): in each frequency band, sound that stays within [thresholdDb]
 * of it counts as noise and is turned down by up to [reductionDb]; sound clearly above it passes untouched (a soft knee of
 * [NoiseGate.KNEE_DB] between). First in the track's chain, before the EQ, on every clip of the track; [enabled] off
 * bypasses it, the settings kept.
 */
data class TrackNoiseReduction(
    val enabled: Boolean = false,
    val reductionDb: Float = 12f,
    val thresholdDb: Float = 6f,
) {
    /** It changes the sound. */
    val isActive: Boolean get() = enabled && reductionDb > 0f

    companion object {
        val OFF = TrackNoiseReduction()
    }
}

/** The noise reduction sheet's sliders and their ranges. */
enum class NoiseReductionControl(val label: String, val min: Float, val max: Float) {
    REDUCTION("降噪量", 0f, 30f),
    THRESHOLD("阈值", 0f, 20f);

    fun read(nr: TrackNoiseReduction): Float = when (this) {
        REDUCTION -> nr.reductionDb
        THRESHOLD -> nr.thresholdDb
    }

    /** [nr] with this control at [value], to 0.5 dB. */
    fun write(nr: TrackNoiseReduction, value: Float): TrackNoiseReduction {
        val v = round(value.coerceIn(min, max) * 2f) / 2f
        return when (this) {
            REDUCTION -> nr.copy(reductionDb = v)
            THRESHOLD -> nr.copy(thresholdDb = v)
        }
    }
}

/**
 * A source file's noise floor, as [NoiseGate] compares against it: the power of each of the [NoiseGate.FFT_SIZE]-point
 * spectrum's bins (sqrt-Hann window, linear power of samples in −1..1) at [sampleRate], where the file is quietest —
 * the 10th percentile of its spectrum smoothed over a few frames and neighbouring bins, which sits close to the noise's
 * average where the file has pauses.
 */
class NoiseProfile(val sampleRate: Int, val floor: FloatArray) {

    /** The floor's power at [hz], between its bins. */
    fun powerAt(hz: Double): Float {
        val bin = hz * NoiseGate.FFT_SIZE / sampleRate
        if (bin <= 0) return floor[0]
        if (bin >= floor.lastIndex) return floor.last()
        val i = bin.toInt()
        val t = (bin - i).toFloat()
        return floor[i] * (1 - t) + floor[i + 1] * t
    }

    /** The floor in dB (full scale = 0) of each bin, for drawing. */
    fun levelsDb(): FloatArray = FloatArray(floor.size) { (10 * log10(max(floor[it], 1e-14f).toDouble())).toFloat() }

    companion object {
        /** The quietest share of a file's frames its floor is taken from. */
        const val PERCENTILE = 0.10
    }
}

/** An in-place radix-2 complex FFT of [size] points (a power of two). */
class Fft(val size: Int) {
    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2 * PI * it / size).toFloat() }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    /** Transforms [re] and [im] in place; [inverse] runs the inverse transform (scaled by 1 / size). */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean = false) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val c = cosTable[k]
                    val s = if (inverse) sinTable[k] else -sinTable[k]
                    val l = j + half
                    val tr = re[l] * c - im[l] * s
                    val ti = re[l] * s + im[l] * c
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
        if (inverse) {
            val scale = 1f / size
            for (i in 0 until size) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}

/**
 * [TrackNoiseReduction] running on interleaved samples of [channels] channels at [sampleRate]: a short-time Fourier transform
 * ([FFT_SIZE] points, a quarter of that per step, sqrt-Hann windows in and out, so unchanged bins come back exactly). Each
 * step's spectrum — the channels' average, smoothed over neighbouring bins and the last few steps — is compared bin by
 * bin with [floor] — the source's noise floor at the frequency each bin plays from ([speed] moves it: a slowed clip plays
 * its noise lower); bins that stay near it are turned down, opening at once when sound rises and closing over
 * [RELEASE_MS], the same gain for every channel. The output is the input [LATENCY] samples later; [Processor] hides that.
 */
class NoiseGate(private val sampleRate: Int, private val channels: Int, private val floor: NoiseProfile, private val speed: Float = 1f) {
    private val fft = Fft(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { sqrt(0.5 - 0.5 * cos(2 * PI * it / FFT_SIZE)).toFloat() }
    private val bins = FFT_SIZE / 2 + 1
    private val input = Array(channels) { FloatArray(FFT_SIZE) }
    private val output = Array(channels) { FloatArray(FFT_SIZE) }
    private val re = Array(channels) { FloatArray(FFT_SIZE) }
    private val im = Array(channels) { FloatArray(FFT_SIZE) }
    private val power = FloatArray(bins)
    private val smoothed = FloatArray(bins)
    private val gainDb = FloatArray(bins)
    private val noise = FloatArray(bins) { k ->
        val hz = k.toDouble() * sampleRate / FFT_SIZE / speed
        // Played at another speed, the source's noise sits at hz / speed and its power spreads over 1 / speed as many bins.
        if (hz >= floor.sampleRate / 2.0) 1e-14f else floor.powerAt(hz) / speed
    }
    private val release = (HOP.toDouble() / sampleRate * 1000 / RELEASE_MS).toFloat()
    private var filled = 0
    private var started = false

    /** How far each bin is turned down at the last step (dB, ≤ 0): what the sheet's curve shows. */
    fun reductionDb(): FloatArray = gainDb.copyOf()

    /**
     * Feeds [frames] interleaved frames from [samples] and hands each output frame (interleaved, [channels] long, reused) to
     * [emit] — [LATENCY] frames behind; [settings] is read at every step, so changes apply at once.
     */
    fun process(samples: FloatArray, frames: Int, settings: TrackNoiseReduction, emit: (FloatArray) -> Unit) {
        val frame = FloatArray(channels)
        for (f in 0 until frames) {
            for (ch in 0 until channels) input[ch][FFT_SIZE - HOP + filled] = samples[f * channels + ch]
            filled++
            if (filled == HOP) {
                step(settings)
                filled = 0
                for (i in 0 until HOP) {
                    for (ch in 0 until channels) frame[ch] = output[ch][i] * OLA_SCALE
                    emit(frame)
                }
                for (ch in 0 until channels) {
                    System.arraycopy(output[ch], HOP, output[ch], 0, FFT_SIZE - HOP)
                    output[ch].fill(0f, FFT_SIZE - HOP, FFT_SIZE)
                }
            }
        }
    }

    /** One step: the newest [FFT_SIZE] samples windowed, their bins scaled, added into the output. */
    private fun step(settings: TrackNoiseReduction) {
        for (ch in 0 until channels) {
            val x = input[ch]
            for (i in 0 until FFT_SIZE) {
                re[ch][i] = x[i] * window[i]
                im[ch][i] = 0f
            }
            fft.transform(re[ch], im[ch])
        }
        // The channels' average, as the floor was measured on the file's mono mix.
        for (k in 0 until bins) {
            var r = 0f
            var i = 0f
            for (ch in 0 until channels) {
                r += re[ch][k]
                i += im[ch][k]
            }
            r /= channels
            i /= channels
            power[k] = r * r + i * i
        }
        val active = settings.isActive
        for (k in 0 until bins) {
            // Neighbouring bins and the last steps together, as the floor was measured.
            val lo = max(0, k - SMOOTH_BINS)
            val hi = min(bins - 1, k + SMOOTH_BINS)
            var sum = 0f
            for (j in lo..hi) sum += power[j]
            val p = sum / (hi - lo + 1)
            smoothed[k] = if (started) TIME_SMOOTHING * smoothed[k] + (1 - TIME_SMOOTHING) * p else p
            val target = if (!active) 0f else gateDb(10 * log10(max(smoothed[k], 1e-20f) / noise[k]), settings)
            // Open at once; close no faster than the release.
            gainDb[k] = if (!started || target >= gainDb[k]) target else max(target, gainDb[k] - release * settings.reductionDb)
        }
        started = true
        for (ch in 0 until channels) {
            for (k in 0 until bins) {
                val g = if (gainDb[k] == 0f) 1f else 10f.pow(gainDb[k] / 20f)
                re[ch][k] *= g
                im[ch][k] *= g
                if (k in 1 until FFT_SIZE / 2) {
                    re[ch][FFT_SIZE - k] *= g
                    im[ch][FFT_SIZE - k] *= g
                }
            }
            fft.transform(re[ch], im[ch], inverse = true)
            val out = output[ch]
            for (i in 0 until FFT_SIZE) out[i] += re[ch][i] * window[i]
            System.arraycopy(input[ch], HOP, input[ch], 0, FFT_SIZE - HOP)
        }
    }

    companion object {
        const val FFT_SIZE = 1024
        const val HOP = FFT_SIZE / 4

        /** How far behind the input the output runs, in frames. */
        const val LATENCY = FFT_SIZE - HOP

        /** sqrt-Hann in and out at a quarter-frame step add up to 2. */
        private const val OLA_SCALE = 0.5f

        /** The soft knee above the threshold: from full reduction to none over this many dB. */
        const val KNEE_DB = 6f

        /** How fast a band closes when its sound falls back to the noise: the full reduction over this long. */
        const val RELEASE_MS = 80.0

        /** Bins either side averaged with each bin. */
        const val SMOOTH_BINS = 2

        /** Share of the previous steps kept in a bin's level. */
        const val TIME_SMOOTHING = 0.5f

        /** The gain (dB, ≤ 0) for a band [marginDb] above its noise floor. */
        fun gateDb(marginDb: Float, settings: TrackNoiseReduction): Float {
            val open = ((marginDb - settings.thresholdDb) / KNEE_DB).coerceIn(0f, 1f)
            return -settings.reductionDb * (1f - open)
        }
    }
}

/**
 * Measures a source's [NoiseProfile] from its decoded sound: the spectrum of each [NoiseGate.FFT_SIZE]-sample frame (half
 * overlapping, channels averaged), smoothed as [NoiseGate] smooths it, gathered per bin into a histogram of quarter-dB
 * steps; the floor is each bin's 10th percentile. Frames of digital silence are left out (a pre-roll of silence would floor
 * at nothing) — unless they are most of the file: a file of silence with a few sounds in it has no noise to take away, so
 * its floor is nothing, and not those sounds.
 */
class NoiseProfileAnalyzer(private val sampleRate: Int, private val channels: Int) {
    private val fft = Fft(NoiseGate.FFT_SIZE)
    private val window = FloatArray(NoiseGate.FFT_SIZE) { sqrt(0.5 - 0.5 * cos(2 * PI * it / NoiseGate.FFT_SIZE)).toFloat() }
    private val bins = NoiseGate.FFT_SIZE / 2 + 1
    private val buffer = FloatArray(NoiseGate.FFT_SIZE)
    private val re = FloatArray(NoiseGate.FFT_SIZE)
    private val im = FloatArray(NoiseGate.FFT_SIZE)
    private val power = FloatArray(bins)
    private val smoothed = FloatArray(bins)
    private val histogram = Array(bins) { IntArray(STEPS) }
    private var filled = 0
    private var frames = 0
    private var silent = 0
    private var started = false

    /** Adds [count] interleaved frames of [samples] (−1..1). */
    fun add(samples: FloatArray, count: Int) {
        val half = NoiseGate.FFT_SIZE / 2
        for (f in 0 until count) {
            var mono = 0f
            for (ch in 0 until channels) mono += samples[f * channels + ch]
            buffer[half + filled] = mono / channels
            filled++
            if (filled == half) {
                frame()
                System.arraycopy(buffer, half, buffer, 0, half)
                filled = 0
            }
        }
    }

    private fun frame() {
        var energy = 0f
        for (i in 0 until NoiseGate.FFT_SIZE) {
            re[i] = buffer[i] * window[i]
            im[i] = 0f
            energy += buffer[i] * buffer[i]
        }
        if (energy / NoiseGate.FFT_SIZE < SILENCE) {
            silent++
            return
        }
        fft.transform(re, im)
        for (k in 0 until bins) power[k] = re[k] * re[k] + im[k] * im[k]
        for (k in 0 until bins) {
            val lo = max(0, k - NoiseGate.SMOOTH_BINS)
            val hi = min(bins - 1, k + NoiseGate.SMOOTH_BINS)
            var sum = 0f
            for (j in lo..hi) sum += power[j]
            val p = sum / (hi - lo + 1)
            // Two analysis frames span what four runtime steps do: the same smoothing time.
            smoothed[k] = if (started) ANALYSIS_SMOOTHING * smoothed[k] + (1 - ANALYSIS_SMOOTHING) * p else p
            val db = 10 * log10(max(smoothed[k], 1e-20f))
            histogram[k][((db - LOWEST_DB) * STEPS_PER_DB).toInt().coerceIn(0, STEPS - 1)]++
        }
        started = true
        frames++
    }

    /** The profile, or null when the sound held no frame above digital silence; mostly silence floors at nothing. */
    fun finish(): NoiseProfile? {
        if (frames == 0) return null
        if (frames < MIN_SOUNDING_SHARE * (frames + silent)) return NoiseProfile(sampleRate, FloatArray(bins) { NO_NOISE })
        val wanted = (frames * NoiseProfile.PERCENTILE).toInt().coerceAtLeast(1)
        val floor = FloatArray(bins) { k ->
            var seen = 0
            var step = 0
            while (step < STEPS - 1) {
                seen += histogram[k][step]
                if (seen >= wanted) break
                step++
            }
            10f.pow((LOWEST_DB + (step + 0.5f) / STEPS_PER_DB) / 10f)
        }
        return NoiseProfile(sampleRate, floor)
    }

    private companion object {
        const val LOWEST_DB = -160f
        const val STEPS_PER_DB = 4f
        val STEPS = (180 * STEPS_PER_DB).toInt()

        /** Mean square below which a frame is digital silence (−100 dBFS). */
        const val SILENCE = 1e-10f

        /** The share of a file's frames that must sound for their quietest tenth to be its noise. */
        const val MIN_SOUNDING_SHARE = 0.2

        /** A floor of no noise at all (−140 dB). */
        const val NO_NOISE = 1e-14f

        /** NoiseGate.TIME_SMOOTHING over twice the step. */
        val ANALYSIS_SMOOTHING = NoiseGate.TIME_SMOOTHING * NoiseGate.TIME_SMOOTHING
    }
}
