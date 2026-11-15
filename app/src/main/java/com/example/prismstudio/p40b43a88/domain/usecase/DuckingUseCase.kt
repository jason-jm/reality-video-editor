package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.LoudnessAnalyzer
import com.example.prismstudio.p40b43a88.domain.model.LoudnessProfile
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.lufsOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Ducking (see [TrackDucking]): the key track's level every 100 ms of the timeline (BS.1770 block
 * loudness of its clips before its fader, from the media's [LoudnessProfile]s) and the envelope it gives
 * the ducked track. Preview, export and the meters all read the same envelope, so what plays is what
 * renders.
 */
class DuckingUseCase {

    /** A ducked track's key track, the key's level every [BLOCK_MS] (dB, [FLOOR_DB] for silence) and the envelope. */
    class Analysis(val keyTrackId: String, val levels: FloatArray, val envelope: DuckEnvelope)

    /** [trackId]'s key track when its ducking is on and keyed from another sound track; null otherwise. */
    fun keyOf(doc: ProjectDocument, trackId: String): String? {
        val ducking = doc.mixer.channel(trackId).ducking
        val key = ducking.keyTrackId ?: return null
        if (!ducking.isOn || key == trackId) return null
        return key.takeIf { doc.timeline.track(it)?.kind == TrackKind.AUDIO }
    }

    /** The sound tracks that duck, with their key tracks. */
    fun ducked(doc: ProjectDocument): Map<String, String> = doc.timeline.tracks
        .filter { it.kind == TrackKind.AUDIO }
        .mapNotNull { track -> keyOf(doc, track.id)?.let { track.id to it } }
        .toMap()

    /** The media whose loudness profiles the keys need. */
    fun keyAssetIds(doc: ProjectDocument): Set<String> {
        val keys = ducked(doc).values.toSet()
        return doc.timeline.clips.filter { it.trackId in keys && it.kind == ClipKind.AUDIO }.mapNotNull { it.assetId }.toSet()
    }

    /**
     * What the envelopes depend on — each ducked track's key, threshold and timing (not its depth, which
     * applies live), its key track's sound clips, the frame rate and which of their profiles are [loaded].
     * Equal inputs give equal envelopes.
     */
    fun inputs(doc: ProjectDocument, loaded: Set<String>): List<Any?> = ducked(doc).toSortedMap().flatMap { (trackId, key) ->
        val d = doc.mixer.channel(trackId).ducking
        val track = doc.timeline.track(key)
        val clips = doc.timeline.clipsOn(key).filter { it.kind == ClipKind.AUDIO }
        listOf(trackId, key, d.thresholdDb, d.attackMs, d.releaseMs, track?.enabled, clips, clips.map { it.assetId in loaded })
    } + listOf(doc.format.fps, doc.durationFrames)

    /**
     * [keyTrackId]'s level every [BLOCK_MS] over the timeline: its enabled sound clips (on an enabled
     * track) with their gain, keyframes and fades, added as uncorrelated sounds. Clips whose media has no
     * entry in [profiles] are silent.
     */
    fun keyLevels(doc: ProjectDocument, keyTrackId: String, profiles: Map<String, LoudnessProfile>): FloatArray {
        val msPerFrame = 1000.0 / doc.format.fps.coerceAtLeast(1)
        val count = ceil(doc.durationFrames * msPerFrame / BLOCK_MS).toInt().coerceAtLeast(0)
        val energy = DoubleArray(count)
        val track = doc.timeline.track(keyTrackId)
        if (track != null && track.enabled) {
            for (clip in doc.timeline.clipsOn(keyTrackId)) {
                if (clip.kind != ClipKind.AUDIO || !clip.enabled) continue
                val profile = clip.assetId?.let(profiles::get) ?: continue
                addClipBlocks(clip, profile, msPerFrame, 0.0, 1.0, 1.0, energy, null)
            }
        }
        return FloatArray(count) { b -> if (energy[b] <= 0.0) FLOOR_DB else lufsOf(energy[b]).toFloat().coerceAtLeast(FLOOR_DB) }
    }

    /**
     * The envelope [ducking] gives for key [levels]: the blocks at or above the threshold are speech
     * (pauses up to [TrackDucking.HOLD_MS] bridged); each run of speech pulls the track fully down, with
     * a ramp of the attack before it and of the release after it — the deepest ramp wins where they meet.
     */
    fun envelope(levels: FloatArray, ducking: TrackDucking): DuckEnvelope {
        val n = levels.size
        val speech = BooleanArray(n) { levels[it] >= ducking.thresholdDb }
        val hold = TrackDucking.HOLD_MS / BLOCK_MS
        var last = -1
        for (b in 0 until n) {
            if (!speech[b]) continue
            if (last >= 0 && b - last - 1 <= hold) for (k in last + 1 until b) speech[k] = true
            last = b
        }
        val attack = ducking.attackMs.toDouble().coerceAtLeast(1.0)
        val release = ducking.releaseMs.toDouble().coerceAtLeast(1.0)
        val steps = ceil((n * BLOCK_MS + release) / STEP_MS).toInt() + 1
        val amounts = FloatArray(steps)
        var b = 0
        while (b < n) {
            if (!speech[b]) {
                b++
                continue
            }
            var e = b
            while (e < n && speech[e]) e++
            val startMs = b * BLOCK_MS.toDouble()
            val endMs = e * BLOCK_MS.toDouble()
            val from = max(0, floor((startMs - attack) / STEP_MS).toInt())
            val until = min(steps - 1, ceil((endMs + release) / STEP_MS).toInt())
            for (i in from..until) {
                val ms = i * STEP_MS.toDouble()
                val amount = when {
                    ms < startMs -> 1.0 - (startMs - ms) / attack
                    ms <= endMs -> 1.0
                    else -> 1.0 - (ms - endMs) / release
                }.coerceIn(0.0, 1.0).toFloat()
                if (amount > amounts[i]) amounts[i] = amount
            }
            b = e
        }
        return DuckEnvelope(STEP_MS * 1000L, amounts)
    }

    /** Every ducked track's analysis, by track id. */
    fun analyses(doc: ProjectDocument, profiles: Map<String, LoudnessProfile>): Map<String, Analysis> {
        val levelsByKey = mutableMapOf<String, FloatArray>()
        return ducked(doc).mapValues { (trackId, key) ->
            val levels = levelsByKey.getOrPut(key) { keyLevels(doc, key, profiles) }
            Analysis(key, levels, envelope(levels, doc.mixer.channel(trackId).ducking))
        }
    }

    companion object {
        /** The key's level is measured every 100 ms (the loudness profiles' blocks). */
        const val BLOCK_MS = LoudnessAnalyzer.BLOCK_MS

        /** The envelope's resolution. */
        const val STEP_MS = 10

        /** Silence, and the bottom of the key level scale. */
        const val FLOOR_DB = -70f
    }
}
