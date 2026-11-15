package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.Mp4Extractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Previews and renders hear a file's first sound track only (release round R17), as the media info and waveform do. */
class FirstAudioTrackTest {

    private class Recording : ExtractorOutput {
        val tracks = mutableListOf<Pair<Int, Int>>()
        var ended = false

        override fun track(id: Int, type: Int): TrackOutput {
            tracks += id to type
            return DiscardingTrackOutput()
        }

        override fun endTracks() {
            ended = true
        }

        override fun seekMap(seekMap: SeekMap) = Unit
    }

    @Test
    fun `the picture and the first sound track pass, a second sound track is dropped`() {
        val base = Recording()
        val output = FirstAudioTrackOutput(base)
        output.track(0, C.TRACK_TYPE_VIDEO)
        output.track(1, C.TRACK_TYPE_AUDIO)
        val second = output.track(2, C.TRACK_TYPE_AUDIO)
        output.track(3, C.TRACK_TYPE_TEXT)
        output.endTracks()
        assertEquals(listOf(0 to C.TRACK_TYPE_VIDEO, 1 to C.TRACK_TYPE_AUDIO, 3 to C.TRACK_TYPE_TEXT), base.tracks)
        assertTrue(second is DiscardingTrackOutput)
        assertTrue(base.ended)
    }

    @Test
    fun `asking again for a track gives the same output`() {
        val base = Recording()
        val output = FirstAudioTrackOutput(base)
        output.track(5, C.TRACK_TYPE_AUDIO)
        val dropped = output.track(6, C.TRACK_TYPE_AUDIO)
        assertSame(dropped, output.track(6, C.TRACK_TYPE_AUDIO))
        output.track(5, C.TRACK_TYPE_AUDIO)
        assertEquals(listOf(5 to C.TRACK_TYPE_AUDIO, 5 to C.TRACK_TYPE_AUDIO), base.tracks)
    }

    @Test
    fun `every extractor is wrapped and still shows the real one underneath`() {
        val mp4 = Mp4Extractor()
        val wrapped = FirstAudioTrackExtractorsFactory(ExtractorsFactory { arrayOf(mp4) }).createExtractors()
        assertEquals(1, wrapped.size)
        assertTrue(wrapped[0] !is Mp4Extractor)
        assertSame(mp4, wrapped[0].underlyingImplementation)
    }
}
