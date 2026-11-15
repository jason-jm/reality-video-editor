package com.example.prismstudio.p40b43a88.data.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser

/**
 * Extractors that pass on a file's first sound track only (release round R17). Given several, Media3's track selector
 * takes the one in the system language, so a file with an English and a Chinese track played and rendered the Chinese
 * one on a Chinese headset, while the media info, the waveform, reversing and the encoder delay all read the first:
 * the previews and renders now hear that same track, whatever the language.
 */
@UnstableApi
class FirstAudioTrackExtractorsFactory(private val base: ExtractorsFactory = DefaultExtractorsFactory()) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = base.createExtractors().map(::FirstAudioTrackExtractor).toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        base.createExtractors(uri, responseHeaders).map(::FirstAudioTrackExtractor).toTypedArray()

    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory =
        apply { base.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled) }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory =
        apply { base.setSubtitleParserFactory(subtitleParserFactory) }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory =
        apply { base.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies) }
}

@UnstableApi
private class FirstAudioTrackExtractor(private val base: Extractor) : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = base.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = base.sniffFailureDetails

    override fun init(output: ExtractorOutput) = base.init(FirstAudioTrackOutput(output))

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = base.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = base.seek(position, timeUs)

    override fun release() = base.release()

    // Media3 looks through to the real extractor (an MP3 stream's seeking, for one).
    override fun getUnderlyingImplementation(): Extractor = base.underlyingImplementation
}

/** Hands [base] the first sound track the extractor declares; the samples of any later one are read and dropped. */
@UnstableApi
internal class FirstAudioTrackOutput(private val base: ExtractorOutput) : ExtractorOutput {
    private var audioId: Int? = null
    private val dropped = HashMap<Int, TrackOutput>()

    override fun track(id: Int, type: Int): TrackOutput {
        if (type == C.TRACK_TYPE_AUDIO) {
            val first = audioId ?: id.also { audioId = it }
            if (id != first) return dropped.getOrPut(id) { DiscardingTrackOutput() }
        }
        return base.track(id, type)
    }

    override fun endTracks() = base.endTracks()

    override fun seekMap(seekMap: SeekMap) = base.seekMap(seekMap)
}
