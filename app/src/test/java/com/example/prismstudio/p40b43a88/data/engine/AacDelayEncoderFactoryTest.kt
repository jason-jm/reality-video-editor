package com.example.prismstudio.p40b43a88.data.engine

import android.media.metrics.LogSessionId
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy

/** Android's AAC encoder reports the delay it really has, so the render's edit list puts its sound on its picture. */
class AacDelayEncoderFactoryTest {

    private val aac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setSampleRate(48_000).setChannelCount(2).build()

    /** A codec that only answers its name and output format (Media3 reports 1600 samples for Android's encoder). */
    private fun codec(name: String, delay: Int): Codec = Proxy.newProxyInstance(Codec::class.java.classLoader, arrayOf(Codec::class.java)) { _, method, _ ->
        when (method.name) {
            "getName" -> name
            "getOutputFormat" -> aac.buildUpon().setEncoderDelay(delay).build()
            else -> null
        }
    } as Codec

    private fun factory(audio: Codec, video: Codec = codec("video", 0)) = AacDelayEncoderFactory(object : Codec.EncoderFactory {
        override fun createForAudioEncoding(format: Format, logSessionId: LogSessionId?) = audio
        override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?) = video
    })

    @Test
    fun `android's AAC encoder reports 2048 samples of delay, other encoders keep theirs`() {
        val corrected = factory(codec("c2.android.aac.encoder", 1600)).createForAudioEncoding(aac, null)
        assertEquals(2048, corrected.outputFormat!!.encoderDelay)
        assertEquals("c2.android.aac.encoder", corrected.name)
        val other = codec("c2.vendor.aac.encoder", 960)
        assertSame(other, factory(other).createForAudioEncoding(aac, null))
        val video = codec("c2.android.avc.encoder", 0)
        assertSame(video, factory(other, video).createForVideoEncoding(aac, null))
    }
}
