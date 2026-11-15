package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * A clipped video item of a render, as the [MediaItem]'s tag: the item takes [lengthUs] of the
 * sequence whatever its source's span and speed (see [RenderSourceFactory]).
 */
data class RenderClip(val lengthUs: Long)

/** A sound item of a render, as the [MediaItem]'s tag: [RenderSourceFactory] notes in [AudioStarts] where its first sample lies. */
data class AudioClip(val key: String)

/**
 * Where each sound item of a render really starts: the time (in the file's sample times) of the first sample its decoder
 * gets. The renderer drops every sample before the item's window, so a window opening inside a compressed frame (1024
 * samples of AAC, 100 ms of WAV) starts with the next one; [GainFadeAudioProcessor] reads this to line the sound up.
 */
class AudioStarts {
    private val firsts = ConcurrentHashMap<String, Long>()

    fun record(key: String, timeUs: Long) {
        firsts.putIfAbsent(key, timeUs)
    }

    /** The first sample [key]'s decoder got, or null before it got one. */
    fun firstSampleUs(key: String): Long? = firsts[key]

    fun clear() = firsts.clear()
}

/**
 * Builds the sources of a render's video clips (items tagged [RenderClip]) so every frame of a clip
 * arrives and the clip keeps its place:
 * - Media3 ends a clipped source at the first sample, in decoding order, at or past the clip's end. In a
 *   source with B-frames the frames shown just before the cut come after that sample and were never
 *   decoded, so the source is read [tailUs] further; `ClipTimingEffect` drops what lies past the end.
 * - The item reports its planned length instead of its source span, so items start on the timeline's
 *   frame grid, and a clip's speed needs no Media3 speed effect (which waits for an audio track a video
 *   item does not have, stalling the render): `ClipTimingEffect` places the frames at the clip's speed.
 */
@UnstableApi
class RenderSourceFactory(
    private val delegate: MediaSource.Factory,
    private val tailUs: Long = TAIL_US,
    /** Where the sound items' first samples are noted (items tagged [AudioClip]). */
    private val audioStarts: AudioStarts? = null,
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val sound = mediaItem.localConfiguration?.tag as? AudioClip
        if (sound != null && audioStarts != null) {
            return FirstSampleSource(delegate.createMediaSource(mediaItem), mediaItem.clippingConfiguration.startPositionUs) {
                audioStarts.record(sound.key, it)
            }
        }
        val clip = mediaItem.localConfiguration?.tag as? RenderClip ?: return delegate.createMediaSource(mediaItem)
        val clipping = mediaItem.clippingConfiguration
        if (clipping.endPositionUs == C.TIME_END_OF_SOURCE) return delegate.createMediaSource(mediaItem)
        val whole = delegate.createMediaSource(mediaItem.buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.UNSET).build())
        // As DefaultMediaSourceFactory clips, only later.
        val clipped = ClippingMediaSource.Builder(whole)
            .setStartPositionUs(clipping.startPositionUs)
            .setEndPositionUs(clipping.endPositionUs + tailUs)
            .setEnableInitialDiscontinuity(!clipping.startsAtKeyFrame)
            .setAllowDynamicClippingUpdates(clipping.relativeToLiveWindow)
            .setRelativeToDefaultPosition(clipping.relativeToDefaultPosition)
            .setAllowUnseekableMedia(clipping.allowUnseekableMedia)
            .build()
        return PlannedLengthSource(clipped, clip.lengthUs)
    }

    /** [source] reporting [lengthUs] as its window's length: the next item starts where the plan says. */
    private class PlannedLengthSource(source: MediaSource, private val lengthUs: Long) : WrappingMediaSource(source) {
        override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
            refreshSourceInfo(
                object : ForwardingTimeline(newTimeline) {
                    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                        super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                        if (window.durationUs != C.TIME_UNSET) window.durationUs = lengthUs
                        return window
                    }
                },
            )
        }
    }

    /**
     * [source] telling [onFirst] the time of the first sample it hands over at or after [startUs] (the clip's start, before
     * which the renderer drops what it reads): where the item's decoded sound really begins.
     */
    private class FirstSampleSource(source: MediaSource, private val startUs: Long, private val onFirst: (Long) -> Unit) : WrappingMediaSource(source) {
        override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
            FirstSamplePeriod(mediaSource.createPeriod(id, allocator, startPositionUs), startUs, onFirst)

        override fun releasePeriod(mediaPeriod: MediaPeriod) {
            mediaSource.releasePeriod((mediaPeriod as FirstSamplePeriod).period)
        }
    }

    /** [period] as it is, its sample streams watched for the first sample at or after [startUs]. */
    private class FirstSamplePeriod(val period: MediaPeriod, private val startUs: Long, private val onFirst: (Long) -> Unit) :
        MediaPeriod, MediaPeriod.Callback {
        private var callback: MediaPeriod.Callback? = null
        @Volatile private var seen = false

        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            this.callback = callback
            period.prepare(this, positionUs)
        }

        override fun onPrepared(mediaPeriod: MediaPeriod) {
            callback?.onPrepared(this)
        }

        override fun onContinueLoadingRequested(source: MediaPeriod) {
            callback?.onContinueLoadingRequested(this)
        }

        override fun maybeThrowPrepareError() = period.maybeThrowPrepareError()
        override fun getTrackGroups(): TrackGroupArray = period.trackGroups
        override fun getStreamKeys(trackSelections: List<ExoTrackSelection>): List<StreamKey> = period.getStreamKeys(trackSelections)

        override fun selectTracks(
            selections: Array<out ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray,
            streams: Array<SampleStream?>,
            streamResetFlags: BooleanArray,
            positionUs: Long,
        ): Long {
            val watched = Array(streams.size) { streams[it] as? WatchedStream }
            val inner = Array(streams.size) { watched[it]?.stream }
            val position = period.selectTracks(selections, mayRetainStreamFlags, inner, streamResetFlags, positionUs)
            for (i in streams.indices) {
                val stream = inner[i]
                streams[i] = when {
                    stream == null -> null
                    watched[i]?.stream === stream -> watched[i]
                    else -> WatchedStream(stream)
                }
            }
            return position
        }

        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = period.discardBuffer(positionUs, toKeyframe)
        override fun readDiscontinuity(): Long = period.readDiscontinuity()
        override fun seekToUs(positionUs: Long): Long = period.seekToUs(positionUs)
        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long =
            period.getAdjustedSeekPositionUs(positionUs, seekParameters)
        override fun getBufferedPositionUs(): Long = period.bufferedPositionUs
        override fun getNextLoadPositionUs(): Long = period.nextLoadPositionUs
        override fun continueLoading(loadingInfo: LoadingInfo): Boolean = period.continueLoading(loadingInfo)
        override fun isLoading(): Boolean = period.isLoading
        override fun reevaluateBuffer(positionUs: Long) = period.reevaluateBuffer(positionUs)

        private inner class WatchedStream(val stream: SampleStream) : SampleStream {
            override fun isReady(): Boolean = stream.isReady
            override fun maybeThrowError() = stream.maybeThrowError()
            override fun skipData(positionUs: Long): Int = stream.skipData(positionUs)

            override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
                val result = stream.readData(formatHolder, buffer, readFlags)
                if (!seen && result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream &&
                    readFlags and SampleStream.FLAG_PEEK == 0 && buffer.timeUs >= startUs
                ) {
                    seen = true
                    onFirst(buffer.timeUs)
                }
                return result
            }
        }
    }

    companion object {
        /** How far past a clip's end its source is read: more than any B-frame run (x264 uses up to 3–4 frames). */
        const val TAIL_US = 500_000L
    }
}
