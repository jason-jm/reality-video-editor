package com.example.prismstudio.p40b43a88.data.engine

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer

/**
 * Works around a leak in Media3 1.9.3 (release round R20). Every setComposition and release of a [CompositionPlayer]
 * releases its internal ExoPlayers, but their CompositionTrackSelector does not release the DefaultTrackSelector inside
 * it, whose listener on the system Spatializer (API 32+) then keeps the whole released player — the timeline's media
 * source graph — reachable for good. Each preview rebuild after an edit leaked one: about 5 MB on a 30-minute,
 * 1320-clip timeline, and the app ran out of memory a few edits in. Here the inner track selectors of the internal
 * players that a call released are released too (the names read are kept by proguard-rules.pro).
 */
@UnstableApi
// Works on the experimental CompositionPlayer the preview already opts into (Media3PlaybackRepositories).
@OptIn(markerClass = [ExperimentalApi::class])
internal object CompositionPlayerLeaks {
    private const val TAG = "PrismPlayerLeaks"

    private val holdersField by lazy {
        runCatching { CompositionPlayer::class.java.getDeclaredField("playerHolders").apply { isAccessible = true } }
            .onFailure { Log.w(TAG, "No playerHolders: replaced internal players are not cleaned up", it) }
            .getOrNull()
    }

    /** DefaultTrackSelector's record of its playback thread (the only Thread field; found by type, as R8 renames it). */
    private val playbackThreadField by lazy {
        DefaultTrackSelector::class.java.declaredFields.firstOrNull { it.type == Thread::class.java }?.apply { isAccessible = true }
            .also { if (it == null) Log.w(TAG, "No playback thread field: replaced internal players are not cleaned up") }
    }

    /** Replaces the composition, then releases what the internal players it dropped leave registered. */
    fun setComposition(player: CompositionPlayer, composition: Composition, startPositionMs: Long) {
        val internal = internalPlayers(player)
        try {
            player.setComposition(composition, startPositionMs)
        } finally {
            releaseSelectors(internal)
        }
    }

    /** Releases [player], then what its internal players leave registered. */
    fun release(player: CompositionPlayer) {
        val internal = internalPlayers(player)
        try {
            player.release()
        } finally {
            releaseSelectors(internal)
        }
    }

    private fun internalPlayers(player: CompositionPlayer): List<ExoPlayer> = try {
        (holdersField?.get(player) as? List<*>).orEmpty().mapNotNull { holder ->
            holder ?: return@mapNotNull null
            val field = holder.javaClass.declaredFields.firstOrNull { ExoPlayer::class.java.isAssignableFrom(it.type) } ?: return@mapNotNull null
            field.isAccessible = true
            field.get(holder) as? ExoPlayer
        }
    } catch (e: ReflectiveOperationException) {
        Log.w(TAG, "Cannot read the internal players", e)
        emptyList()
    }

    /** The inner selectors of the players that are released now; a player still in use keeps its own. */
    private fun releaseSelectors(players: List<ExoPlayer>) {
        for (p in players) {
            if (!p.isReleased) continue
            try {
                val selector = p.trackSelector?.let(::inner) ?: continue
                // It may only be released on its player's playback thread, which ended with the player.
                playbackThreadField?.set(selector, null)
                selector.release()
            } catch (e: ReflectiveOperationException) {
                Log.w(TAG, "Cannot release a track selector", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Cannot release a track selector", e)
            }
        }
    }

    private fun inner(selector: TrackSelector): DefaultTrackSelector? {
        if (selector is DefaultTrackSelector) return selector
        val field = selector.javaClass.declaredFields.firstOrNull { DefaultTrackSelector::class.java.isAssignableFrom(it.type) } ?: return null
        field.isAccessible = true
        return field.get(selector) as? DefaultTrackSelector
    }
}
