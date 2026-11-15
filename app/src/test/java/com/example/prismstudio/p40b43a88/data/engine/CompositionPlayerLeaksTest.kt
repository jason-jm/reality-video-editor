package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.transformer.CompositionPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * What [CompositionPlayerLeaks] relies on inside Media3 (1.9.3), pinned so an upgrade that changes it fails here rather
 * than leaking quietly again (release round R20). When CompositionTrackSelector releases its inner selector itself, the
 * workaround can go.
 */
class CompositionPlayerLeaksTest {
    private fun media3(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    @Test
    fun `CompositionPlayer keeps its internal players in playerHolders, each holding an ExoPlayer`() {
        assertEquals(List::class.java, CompositionPlayer::class.java.getDeclaredField("playerHolders").type)
        val holder = media3("androidx.media3.transformer.CompositionPlayer\$SequencePlayerHolder")
        assertNotNull(holder.declaredFields.firstOrNull { ExoPlayer::class.java.isAssignableFrom(it.type) })
    }

    @Test
    fun `CompositionTrackSelector still does not release the DefaultTrackSelector inside it`() {
        val selector = media3("androidx.media3.transformer.CompositionTrackSelector")
        assertNotNull(selector.declaredFields.firstOrNull { DefaultTrackSelector::class.java.isAssignableFrom(it.type) })
        assertFalse(
            "Media3 now releases it: CompositionPlayerLeaks is no longer needed",
            selector.declaredMethods.any { it.name == "release" && it.parameterCount == 0 },
        )
    }

    @Test
    fun `DefaultTrackSelector records one playback thread`() {
        assertEquals(1, DefaultTrackSelector::class.java.declaredFields.count { it.type == Thread::class.java })
    }
}
