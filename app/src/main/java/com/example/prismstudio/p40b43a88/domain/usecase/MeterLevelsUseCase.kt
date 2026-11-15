package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.model.volumeAt
import kotlin.math.log10
import kotlin.math.pow

/**
 * Peak meter readings at a timeline frame, derived from the sources' decoded peaks and the mix
 * (clip gain, fades, the track's dynamics at their steady level, its ducking, fader, mute / solo,
 * its submix bus's fader, master). Silent channels read [FLOOR_DB].
 */
class MeterLevelsUseCase {

    /**
     * [channels] after the fader; [inputs] what each track's dynamics hear (before them, dB) and
     * [reductions] how far they turn it down there (dB, never positive); [ducks] how far each ducking
     * track is dipped at the frame (dB, never positive), sounding or not; [buses] each submix bus after its fader — the
     * sum of its tracks' levels.
     */
    data class Levels(
        val channels: Map<String, Float>,
        val master: Float,
        val inputs: Map<String, Float> = emptyMap(),
        val reductions: Map<String, Float> = emptyMap(),
        val ducks: Map<String, Float> = emptyMap(),
        val buses: Map<String, Float> = emptyMap(),
    )

    /** [envelopes]: the ducking envelopes of the tracks whose ducking is keyed (see [DuckingUseCase]). */
    operator fun invoke(
        doc: ProjectDocument,
        frame: Int,
        waveforms: Map<String, Waveform>,
        envelopes: Map<String, DuckEnvelope> = emptyMap(),
    ): Levels {
        val fps = doc.format.fps.coerceAtLeast(1)
        val frameUs = frame * 1_000_000L / fps
        val ducks = envelopes.mapNotNull { (trackId, envelope) ->
            val ducking = doc.mixer.channel(trackId).ducking
            if (!ducking.isActive) null else trackId to -ducking.depthDb * envelope.amountAt(frameUs)
        }.toMap()
        val master = gain(doc.mixer.masterVolumeDb)
        var sum = 0f
        val busSums = mutableMapOf<String, Float>()
        val inputs = mutableMapOf<String, Float>()
        val reductions = mutableMapOf<String, Float>()
        val channels = doc.timeline.tracks.filter { it.kind == TrackKind.AUDIO }.associate { track ->
            val channel = doc.mixer.channel(track.id)
            val clip = doc.timeline.clipsOn(track.id).firstOrNull { it.kind == ClipKind.AUDIO && it.enabled && frame in it }
            val wave = clip?.assetId?.let(waveforms::get)
            val level = if (clip == null || wave == null || !track.enabled || !doc.mixer.isAudible(track.id)) {
                0f
            } else {
                val atMs = clip.sourceInMs + if (clip.retimed) clip.sourceOffsetMs(frame - clip.start, fps).toLong()
                else ((frame - clip.start) * 1000f / fps * clip.speed.factor).toLong()
                val local = (frame - clip.start).toFloat() / fps
                val untilEnd = (clip.end - frame).toFloat() / fps
                var fade = 1f
                if (clip.fadeInSeconds > 0f) fade = minOf(fade, ClipFades.shape(local / clip.fadeInSeconds, clip.fadeInCurve))
                if (clip.fadeOutSeconds > 0f) fade = minOf(fade, ClipFades.shape(untilEnd / clip.fadeOutSeconds, clip.fadeOutCurve))
                // The clip's level at this frame: its volume keyframes, when it has any.
                val heard = wave.peak(atMs - WINDOW_MS, atMs + WINDOW_MS) * gain(clip.volumeAt(frame - clip.start)) * fade.coerceIn(0f, 1f)
                val dynamics = channel.dynamics
                val change = if (dynamics.isActive && heard > 0f) dynamics.gainChangeDb(20.0 * log10(heard.toDouble())).toFloat() else 0f
                inputs[track.id] = toDb(heard)
                reductions[track.id] = change.coerceAtMost(0f)
                val duck = ducks[track.id] ?: 0f
                heard * 10f.pow((change + duck) / 20f) * gain(channel.volumeDb)
            }
            // A track on a bus reaches the main output through the bus's fader.
            val bus = doc.mixer.busOf(track.id)
            if (bus != null) busSums[bus.id] = (busSums[bus.id] ?: 0f) + level * gain(bus.volumeDb)
            sum += level * (bus?.let { gain(it.volumeDb) } ?: 1f)
            track.id to toDb(level)
        }
        val buses = doc.mixer.buses.associate { it.id to toDb(busSums[it.id] ?: 0f) }
        return Levels(channels, toDb(sum * master), inputs, reductions, ducks, buses)
    }

    private fun gain(db: Float) = if (db <= FLOOR_DB) 0f else 10f.pow(db / 20f)

    private fun toDb(amplitude: Float) = if (amplitude <= 0.001f) FLOOR_DB else (20f * log10(amplitude)).coerceIn(FLOOR_DB, 6f)

    companion object {
        const val FLOOR_DB = -60f
        private const val WINDOW_MS = 60L
    }
}
