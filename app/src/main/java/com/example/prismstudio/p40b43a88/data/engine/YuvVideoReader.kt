package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.Closeable

/**
 * Decodes a video file front to back into 8-bit I420 frames on the CPU (MediaCodec's flexible YUV
 * output). No decoder surface or GL texture is involved, so the frames can be drawn from any GL
 * context — [UnderlayEffect] draws an earlier render pass this way. Not thread-safe: one caller.
 */
class YuvVideoReader(path: String, context: Context? = null) : Closeable {

    /** One decoded picture: tightly packed planes, Y at full size and Cb / Cr at half size (rounded up). */
    class Frame {
        var width = 0
            private set
        var height = 0
            private set
        var ptsUs = 0L
            internal set

        /** Changes whenever the frame is refilled with another picture. */
        var serial = 0L
            internal set
        var y = ByteArray(0)
            private set
        var u = ByteArray(0)
            private set
        var v = ByteArray(0)
            private set
        val chromaWidth: Int get() = (width + 1) / 2
        val chromaHeight: Int get() = (height + 1) / 2

        internal fun resize(w: Int, h: Int) {
            if (w == width && h == height) return
            width = w
            height = h
            y = ByteArray(w * h)
            u = ByteArray(chromaWidth * chromaHeight)
            v = ByteArray(chromaWidth * chromaHeight)
        }
    }

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false
    private var current: Frame? = null
    private var pending: Frame? = null
    private var spare: Frame? = null
    private var serials = 0L
    /** Whether any sample has gone to the decoder (see [seekTo]). */
    private var fed = false

    /** How the decoded samples turn into RGB, from the stream's colour description. */
    var colors: YuvToRgb
        private set

    init {
        val format: MediaFormat
        try {
            // A content:// uri (a picked file) needs the resolver; a file path or file:// uri opens directly.
            if (context != null && path.startsWith("content:")) extractor.setDataSource(context, Uri.parse(path), null)
            else extractor.setDataSource(if (path.startsWith("file:")) Uri.parse(path).path ?: path else path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalArgumentException("No video track in $path")
            extractor.selectTrack(track)
            format = extractor.getTrackFormat(track)
        } catch (e: Exception) {
            extractor.release()
            throw e
        }
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            extractor.release()
            throw e
        }
        colors = colorsOf(format)
    }

    /**
     * The frame showing at [timeUs]: the last one whose timestamp is at most [toleranceUs] later, so
     * timestamps a little off the frame grid still match. Null before the first frame; the last frame
     * holds once the file ends. Calls must not go back in time.
     */
    fun frameAt(timeUs: Long, toleranceUs: Long): Frame? {
        while (true) {
            val next = pending ?: decode() ?: return current
            if (next.ptsUs > timeUs + toleranceUs) {
                pending = next
                return current
            }
            spare = current
            current = next
            pending = null
        }
    }

    /**
     * Starts reading again from the sync frame at or before [timeUs] (frames before [timeUs] still come out of [next]:
     * skip them by their [Frame.ptsUs]). Meant for the start, before reading; a seek after reading began flushes the
     * decoder.
     */
    fun seekTo(timeUs: Long) {
        extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        // Flushing a decoder before its first output loses the codec-specific data it was configured with (it then
        // decodes nothing): a reader that has fed nothing yet only moves the extractor.
        if (fed) codec.flush()
        inputDone = false
        outputDone = false
        pending = null
    }

    /**
     * The next frame in presentation order, or null once the file ends — for reading every frame once; not to be mixed
     * with [frameAt]. The frame is refilled by the call after next.
     */
    fun next(): Frame? {
        val frame = pending ?: decode() ?: return null
        pending = null
        spare = current
        current = frame
        return frame
    }

    /** The next decoded frame, or null at the end of the stream. */
    private fun decode(): Frame? {
        if (outputDone) return null
        val deadline = System.nanoTime() + STALL_NS
        while (System.nanoTime() < deadline) {
            feed()
            val index = codec.dequeueOutputBuffer(info, POLL_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                colors = colorsOf(codec.outputFormat)
                continue
            }
            if (index < 0) continue
            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val frame = if (info.size > 0) codec.getOutputImage(index)?.let { copy(it, info.presentationTimeUs) } else null
            codec.releaseOutputBuffer(index, false)
            if (ended) outputDone = true
            if (frame != null) return frame
            if (ended) return null
        }
        Log.w(TAG, "Decoder stalled; holding the last frame")
        outputDone = true
        return null
    }

    private fun feed() {
        while (!inputDone) {
            val index = codec.dequeueInputBuffer(0)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
                fed = true
            }
        }
    }

    private fun copy(image: Image, ptsUs: Long): Frame {
        val frame = spare ?: Frame()
        spare = null
        val crop = image.cropRect
        frame.resize(crop.width(), crop.height())
        copyPlane(image.planes[0], crop.left, crop.top, frame.width, frame.height, frame.y)
        copyPlane(image.planes[1], crop.left / 2, crop.top / 2, frame.chromaWidth, frame.chromaHeight, frame.u)
        copyPlane(image.planes[2], crop.left / 2, crop.top / 2, frame.chromaWidth, frame.chromaHeight, frame.v)
        frame.ptsUs = ptsUs
        frame.serial = ++serials
        return frame
    }

    private fun copyPlane(plane: Image.Plane, left: Int, top: Int, width: Int, height: Int, out: ByteArray) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val step = plane.pixelStride
        for (row in 0 until height) {
            val start = (top + row) * rowStride + left * step
            if (step == 1) {
                buffer.position(start)
                buffer.get(out, row * width, width)
            } else {
                for (col in 0 until width) out[row * width + col] = buffer.get(start + col * step)
            }
        }
    }

    private fun colorsOf(format: MediaFormat): YuvToRgb = YuvToRgb.of(
        standard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD),
        range = format.intOrNull(MediaFormat.KEY_COLOR_RANGE),
        height = format.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0,
    )

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }

    private companion object {
        const val TAG = "PrismYuvReader"
        const val POLL_US = 10_000L
        /** Longest wait for the next frame before the decoder counts as stuck. */
        const val STALL_NS = 5_000_000_000L
    }
}
