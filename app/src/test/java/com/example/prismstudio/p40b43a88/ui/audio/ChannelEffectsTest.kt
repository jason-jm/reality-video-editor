package com.example.prismstudio.p40b43a88.ui.audio

import com.example.prismstudio.p40b43a88.domain.model.DeEssBand
import com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelEffectsTest {

    @Test
    fun `a fresh track lists its four effects, none on, under a plain chip`() {
        val effects = channelEffects(TrackDucking(), null, TrackNoiseReduction.OFF, TrackDeEsser.OFF)
        assertEquals(listOf(ChannelEffect.DUCKING, ChannelEffect.NOISE, ChannelEffect.DE_ESSER, ChannelEffect.REVERB), effects.map { it.effect })
        assertTrue(effects.none { it.active })
        assertEquals(
            listOf("未开启：另一条轨道有声音时自动压低本轨", "未开启：按测得的底噪逐频段压低噪声", "强度为 0：声音不变", "干湿比为 0：声音不变"),
            effects.map { it.summary },
        )
        val ch = MixerChannelUi("a1", "A1", false, false, 0f, 0f, -60f, effects = effects)
        assertEquals(0, ch.effectCount)
        assertEquals("效果", ch.effectsLabel)
    }

    @Test
    fun `working effects say what they do and are counted on the chip`() {
        val effects = channelEffects(
            TrackDucking(keyTrackId = "a2", depthDb = 12.4f),
            "A2",
            TrackNoiseReduction(enabled = true, reductionDb = 18f),
            TrackDeEsser(amount = 45, band = DeEssBand.HIGH),
            com.example.prismstudio.p40b43a88.domain.model.TrackReverb.OFF.withRoom(com.example.prismstudio.p40b43a88.domain.model.ReverbRoom.CHURCH),
        )
        assertTrue(effects.all { it.active })
        assertEquals(listOf("A2 有声音时压低 12 dB", "底噪压低 18 dB", "8 kHz 以上 · 强度 45", "教堂 · 4.5 秒 · 湿声 30 %"), effects.map { it.summary })
        assertEquals("效果 4", MixerChannelUi("a1", "A1", false, false, 0f, 0f, -60f, effects = effects).effectsLabel)
    }

    @Test
    fun `effects that change nothing are not counted and say why`() {
        val gone = channelEffects(TrackDucking(keyTrackId = "a9"), null, TrackNoiseReduction(enabled = true, reductionDb = 0f), TrackDeEsser(enabled = false, amount = 70))
        assertFalse("its key track was deleted", gone[0].active)
        assertEquals("键轨道已不存在：不起作用", gone[0].summary)
        assertEquals("压低量为 0：声音不变", gone[1].summary)
        assertEquals("未启用：只压低齿音频段", gone[2].summary)
        assertTrue(gone.none { it.active })
        assertEquals("深度为 0：声音不变", channelEffects(TrackDucking(keyTrackId = "a2", depthDb = 0f), "A2", TrackNoiseReduction.OFF, TrackDeEsser.OFF)[0].summary)
        val one = channelEffects(TrackDucking(), null, TrackNoiseReduction(enabled = true), TrackDeEsser.OFF)
        assertEquals("效果 1", MixerChannelUi("a1", "A1", false, false, 0f, 0f, -60f, effects = one).effectsLabel)
    }
}
