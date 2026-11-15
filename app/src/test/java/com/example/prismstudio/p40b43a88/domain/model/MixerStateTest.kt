package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerStateTest {

    @Test
    fun `one neutral channel is created per audio track`() {
        val mixer = MixerState.forTracks(Timeline.defaultTracks())

        assertEquals(listOf(MixerChannel(Timeline.A1), MixerChannel(Timeline.A2)), mixer.channels)
        assertEquals(0f, mixer.masterVolumeDb, 0f)
        assertEquals(MixerChannel("a9"), mixer.channel("a9"))
    }

    @Test
    fun `channels are audible unless muted or another channel is soloed`() {
        val plain = MixerState(listOf(MixerChannel("a1"), MixerChannel("a2")))
        assertTrue(plain.isAudible("a1") && plain.isAudible("a2"))

        val muted = plain.replace(MixerChannel("a1", mute = true))
        assertFalse(muted.isAudible("a1"))
        assertTrue(muted.isAudible("a2"))

        val solo = plain.replace(MixerChannel("a2", solo = true))
        assertFalse(solo.isAudible("a1"))
        assertTrue(solo.isAudible("a2"))

        val soloButMuted = solo.replace(MixerChannel("a2", solo = true, mute = true))
        assertFalse(soloButMuted.isAudible("a2"))
        assertFalse(soloButMuted.isAudible("a1"))
    }
}
