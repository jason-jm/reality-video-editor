package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.AudioFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

/**
 * Writes a reversed copy of a media file, so reverse playback is ordinary forward playback of the
 * copy. Picture and sound are mirrored around the pivot (the asset's duration): source time t lands
 * at pivot − t, the mapping the render plan uses (`ReverseMapping`).
 *
 * Video is decoded one GOP at a time from the end of the file, its frames spilled to a scratch file
 * and re-encoded as H.264 in reverse order; audio is decoded to 16-bit PCM, reversed and re-encoded as
 * AAC. Blocking — call from a background thread; [generate] checks `cancelled` between buffers.
 */
class ReverseMediaGenerator(private val context: Context) {

    /** The media cannot be reversed by this generator; [message] is shown to the user. */
    class Unsupported(message: String) : Exception(message)

    fun generate(
        source: Uri,
        pivotUs: Long,
        output: File,
        scratch: File,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        scratch.mkdirs()
        var mediaDurationUs = 0L
        val (videoTrack, audioTrack) = MediaExtractor().run {
            try {
                setDataSource(context, source, null)
                mediaDurationUs = (0 until trackCount).maxOfOrNull { i ->
                    getTrackFormat(i).let { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L }
                } ?: 0L
                trackIndex("video/") to trackIndex("audio/")
            } finally {
                release()
            }
        }
        // Mirror around the asset's duration as the render plan knows it; fall back to the file's own.
        val pivot = if (pivotUs > 0) pivotUs else mediaDurationUs
        if (videoTrack < 0 && audioTrack < 0) throw Unsupported("文件里没有可倒放的画面或声音")
        val audioShare = when {
            audioTrack < 0 -> 0f
            videoTrack < 0 -> 1f
            else -> AUDIO_SHARE
        }
        val audio = if (audioTrack >= 0) {
            ReversedAudio(source, audioTrack, pivot, File(scratch, "audio.pcm"), cancelled) { onProgress(it * audioShare) }.encode()
        } else {
            null
        }
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            if (videoTrack >= 0) {
                val video = ReversedVideo(source, videoTrack, pivot, File(scratch, "frames.yuv"), muxer, audio, cancelled) { p ->
                    onProgress(audioShare + p * (1 - audioShare))
                }
                video.encode()
                started = video.muxerStarted
            } else if (audio != null) {
                val track = muxer.addTrack(audio.format)
                muxer.start()
                started = true
                audio.writeTo(muxer, track)
            }
            if (!started) throw Unsupported("没有解码出任何画面或声音")
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            scratch.listFiles()?.forEach { it.delete() }
        }
    }

    private fun MediaExtractor.trackIndex(mimePrefix: String): Int =
        (0 until trackCount).firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true } ?: -1

    private fun openExtractor(source: Uri, track: Int) = MediaExtractor().apply {
        setDataSource(context, source, null)
        selectTrack(track)
    }

    /** Encoded samples kept in memory until the muxer can start (it needs every track's format first). */
    private class EncodedTrack(val format: MediaFormat, val samples: List<Sample>) {
        fun writeTo(muxer: MediaMuxer, track: Int) {
            val info = MediaCodec.BufferInfo()
            for (sample in samples) {
                info.set(0, sample.data.size, sample.ptsUs, sample.flags)
                muxer.writeSampleData(track, ByteBuffer.wrap(sample.data), info)
            }
        }
    }

    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    /** Decodes the sound to PCM, then encodes it back to AAC from the last sample to the first. */
    private inner class ReversedAudio(
        private val source: Uri,
        private val track: Int,
        private val pivotUs: Long,
        private val pcmFile: File,
        private val cancelled: () -> Boolean,
        private val onProgress: (Float) -> Unit,
    ) {
        private var sampleRate = 0
        private var channels = 0
        private var firstPtsUs = -1L

        fun encode(): EncodedTrack = RandomAccessFile(pcmFile, "rw").use { pcm ->
            pcm.setLength(0)
            val bytes = decodeTo(pcm)
            if (bytes == 0L || sampleRate <= 0 || channels <= 0) throw Unsupported("无法解码这段声音")
            encodeReversed(pcm, bytes)
        }

        private fun decodeTo(pcm: RandomAccessFile): Long {
            val extractor = openExtractor(source, track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            var written = 0L
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val info = MediaCodec.BufferInfo()
                var chunk = ByteArray(0)
                var inputDone = false
                var outputDone = false
                val stall = StallGuard("解码")
                while (!outputDone) {
                    if (cancelled()) throw CancellationException()
                    stall.check()
                    if (!inputDone) inputDone = feed(decoder, extractor)
                    val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index != MediaCodec.INFO_TRY_AGAIN_LATER) stall.progressed()
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val out = decoder.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
                        ) {
                            throw Unsupported("暂不支持这种声音格式的倒放")
                        }
                    } else if (index >= 0) {
                        if (info.size > 0) {
                            if (firstPtsUs < 0) firstPtsUs = info.presentationTimeUs
                            val buffer = decoder.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            if (chunk.size < info.size) chunk = ByteArray(info.size)
                            buffer.get(chunk, 0, info.size)
                            pcm.write(chunk, 0, info.size)
                            written += info.size
                            if (durationUs > 0) onProgress(DECODE_SHARE * (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            } finally {
                runCatching { decoder.stop() }
                decoder.release()
                extractor.release()
            }
            return written
        }

        private fun encodeReversed(pcm: RandomAccessFile, bytes: Long): EncodedTrack {
            val frameBytes = 2 * channels
            val totalFrames = bytes / frameBytes
            val endUs = firstPtsUs.coerceAtLeast(0) + totalFrames * 1_000_000L / sampleRate
            // Mirror around the pivot: silence first if the sound ends before it, else drop what lies past it.
            // The AAC encoder adds priming samples that players do not trim (MediaMuxer records no edit
            // list), so the stream starts that much later in the mirror to land on time.
            val leadFrames = (pivotUs - endUs) * sampleRate / 1_000_000L - AAC_PRIMING_FRAMES
            var silenceLeft = leadFrames.coerceAtLeast(0)
            var readEnd = (totalFrames - (-leadFrames).coerceAtLeast(0)).coerceAtLeast(0)
            val totalOut = (silenceLeft + readEnd).coerceAtLeast(1)

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_FRAMES * frameBytes)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val samples = mutableListOf<Sample>()
            var outputFormat: MediaFormat? = null
            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()
                val chunk = ByteArray(CHUNK_FRAMES * frameBytes)
                val reversed = ByteArray(CHUNK_FRAMES * frameBytes)
                val silence = ByteArray(CHUNK_FRAMES * frameBytes)
                val info = MediaCodec.BufferInfo()
                var written = 0L
                var inputDone = false
                var outputDone = false
                val stall = StallGuard("编码")
                while (!outputDone) {
                    if (cancelled()) throw CancellationException()
                    stall.check()
                    if (!inputDone) {
                        val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = encoder.getInputBuffer(index)!!
                            buffer.clear()
                            val room = minOf(CHUNK_FRAMES, buffer.remaining() / frameBytes)
                            val ptsUs = written * 1_000_000L / sampleRate
                            val frames = when {
                                silenceLeft > 0 -> minOf(room.toLong(), silenceLeft).toInt().also { n ->
                                    buffer.put(silence, 0, n * frameBytes)
                                    silenceLeft -= n
                                }
                                readEnd > 0 -> minOf(room.toLong(), readEnd).toInt().also { n ->
                                    val from = readEnd - n
                                    pcm.seek(from * frameBytes)
                                    pcm.readFully(chunk, 0, n * frameBytes)
                                    for (f in 0 until n) System.arraycopy(chunk, (n - 1 - f) * frameBytes, reversed, f * frameBytes, frameBytes)
                                    buffer.put(reversed, 0, n * frameBytes)
                                    readEnd = from
                                }
                                else -> 0
                            }
                            if (frames > 0) {
                                encoder.queueInputBuffer(index, 0, frames * frameBytes, ptsUs, 0)
                                written += frames
                                onProgress(DECODE_SHARE + (1 - DECODE_SHARE) * written.toFloat() / totalOut)
                            } else {
                                encoder.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }
                    val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index != MediaCodec.INFO_TRY_AGAIN_LATER) stall.progressed()
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = encoder.outputFormat
                    } else if (index >= 0) {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            samples += copySample(encoder.getOutputBuffer(index)!!, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            } finally {
                runCatching { encoder.stop() }
                encoder.release()
            }
            return EncodedTrack(outputFormat ?: throw Unsupported("无法编码倒放的声音"), samples)
        }
    }

    /**
     * Walks the GOPs from last to first: each is decoded on its own (frames spill to a scratch file
     * in presentation order) and its frames are fed to the encoder backwards.
     */
    private inner class ReversedVideo(
        private val source: Uri,
        private val track: Int,
        private val pivotUs: Long,
        private val spillFile: File,
        private val muxer: MediaMuxer,
        private val audio: EncodedTrack?,
        private val cancelled: () -> Boolean,
        private val onProgress: (Float) -> Unit,
    ) {
        var muxerStarted = false
            private set
        private var videoTrack = -1
        private var encoder: MediaCodec? = null
        private var width = 0
        private var height = 0
        private var frame = ByteArray(0)
        private var row = ByteArray(0)
        private var lastQueuedUs = -1L
        private val info = MediaCodec.BufferInfo()

        fun encode() {
            val extractor = openExtractor(source, track)
            val format = extractor.getTrackFormat(track)
            val keyframes = mutableListOf<Long>()
            val times = mutableListOf<Long>()
            while (true) {
                val t = extractor.sampleTime
                if (t < 0) break
                times += t
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) keyframes += t
                extractor.advance()
            }
            keyframes.sort()
            times.sort()
            if (keyframes.isEmpty()) {
                extractor.release()
                throw Unsupported("视频里没有可解码的关键帧")
            }
            val frameUs = frameDuration(times, format)
            val pivot = if (pivotUs > 0) pivotUs else times.last() + frameUs
            if (format.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                RandomAccessFile(spillFile, "rw").use { spill ->
                    // The frame after the last one: the pivot, or one frame past the last picture.
                    var followingUs = minOf(pivot, times.last() + frameUs)
                    var openGop = false
                    for (g in keyframes.indices.reversed()) {
                        val gopStart = keyframes[g]
                        val gopEnd = keyframes.getOrNull(g + 1) ?: Long.MAX_VALUE
                        val decoded = decodeGop(
                            decoder, extractor, spill, gopStart, gopEnd,
                            extraSamples = if (openGop) OPEN_GOP_EXTRA_SAMPLES else 0,
                            flush = g < keyframes.lastIndex,
                        )
                        openGop = openGop || decoded.sawLeadingFrames
                        for (j in decoded.ptsUs.indices.reversed()) {
                            val nextUs = decoded.ptsUs.getOrElse(j + 1) { followingUs }
                            spill.seek(j.toLong() * frame.size)
                            spill.readFully(frame)
                            queueFrame((pivot - nextUs).coerceAtLeast(0))
                        }
                        decoded.ptsUs.firstOrNull()?.let { followingUs = it }
                        onProgress((keyframes.size - g).toFloat() / keyframes.size)
                    }
                }
                finishEncoding()
            } finally {
                runCatching { decoder.stop() }
                decoder.release()
                extractor.release()
                encoder?.let { runCatching { it.stop() }; it.release() }
            }
        }

        private inner class Gop(val ptsUs: List<Long>, val sawLeadingFrames: Boolean)

        /**
         * Decodes the GOP starting at keyframe [startUs] and spills the frames shown before [endUs]. For
         * open GOPs a few samples past the next keyframe are fed too, since its leading pictures belong here.
         * [flush] resets the decoder after the previous GOP's end of stream; it must not run before the
         * first output, which would drop the codec-specific data given at configure time.
         */
        @Suppress("LongParameterList")
        private fun decodeGop(
            decoder: MediaCodec,
            extractor: MediaExtractor,
            spill: RandomAccessFile,
            startUs: Long,
            endUs: Long,
            extraSamples: Int,
            flush: Boolean,
        ): Gop {
            if (flush) decoder.flush()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            spill.setLength(0)
            val pts = mutableListOf<Long>()
            var leading = false
            var inputDone = false
            var outputDone = false
            var pastEnd = 0
            val stall = StallGuard("解码")
            while (!outputDone) {
                if (cancelled()) throw CancellationException()
                stall.check()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val t = extractor.sampleTime
                        if (t >= endUs && (pastEnd > 0 || (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)) pastEnd++
                        if (t < 0 || pastEnd > extraSamples) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                            decoder.queueInputBuffer(index, 0, size, t, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) stall.progressed()
                if (index >= 0) {
                    val p = info.presentationTimeUs
                    if (info.size > 0 && p < startUs) leading = true
                    if (info.size > 0 && p >= startUs && p < endUs) {
                        val image = decoder.getOutputImage(index) ?: throw Unsupported("这台设备无法读取解码后的画面")
                        image.use { spillImage(it, spill) }
                        pts += p
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
                drainEncoder(false)
            }
            return Gop(pts, leading)
        }

        private fun spillImage(image: Image, spill: RandomAccessFile) {
            if (image.format != ImageFormat.YUV_420_888) throw Unsupported("暂不支持这种视频格式的倒放（例如 10 位 HDR）")
            val crop = Rect(image.cropRect)
            if (encoder == null) startEncoder(crop.width() and 1.inv(), crop.height() and 1.inv())
            val w = width
            val h = height
            val planes = image.planes
            copyPlaneOut(planes[0], crop.left, crop.top, w, h, 0)
            copyPlaneOut(planes[1], crop.left / 2, crop.top / 2, w / 2, h / 2, w * h)
            copyPlaneOut(planes[2], crop.left / 2, crop.top / 2, w / 2, h / 2, w * h + (w / 2) * (h / 2))
            spill.write(frame)
        }

        /** Copies one plane of the decoded picture into the packed I420 [frame] at [offset]. */
        @Suppress("LongParameterList")
        private fun copyPlaneOut(plane: Image.Plane, left: Int, top: Int, w: Int, h: Int, offset: Int) {
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (y in 0 until h) {
                buffer.position((top + y) * rowStride + left * pixelStride)
                if (pixelStride == 1) {
                    buffer.get(frame, offset + y * w, w)
                } else {
                    buffer.get(row, 0, (w - 1) * pixelStride + 1)
                    var o = offset + y * w
                    var i = 0
                    repeat(w) {
                        frame[o++] = row[i]
                        i += pixelStride
                    }
                }
            }
        }

        /** Writes one plane of the packed I420 [frame] into the encoder's input picture. */
        private fun copyPlaneIn(plane: Image.Plane, w: Int, h: Int, offset: Int) {
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val span = (w - 1) * pixelStride + 1
            for (y in 0 until h) {
                val at = y * rowStride
                if (pixelStride == 1) {
                    buffer.position(at)
                    buffer.put(frame, offset + y * w, w)
                } else {
                    // Interleaved chroma: read-modify-write keeps the other plane's bytes in the row.
                    buffer.position(at)
                    buffer.get(row, 0, span)
                    var i = 0
                    var o = offset + y * w
                    repeat(w) {
                        row[i] = frame[o++]
                        i += pixelStride
                    }
                    buffer.position(at)
                    buffer.put(row, 0, span)
                }
            }
        }

        private fun startEncoder(w: Int, h: Int) {
            if (w <= 0 || h <= 0) throw Unsupported("视频尺寸无效")
            width = w
            height = h
            frame = ByteArray(w * h * 3 / 2)
            row = ByteArray(w * 2 + 16)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * BITS_PER_PIXEL_FRAME * ENCODE_FPS).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE))
                setInteger(MediaFormat.KEY_FRAME_RATE, ENCODE_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                    it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    it.start()
                }
            } catch (e: Exception) {
                throw Unsupported("无法以 ${w}×$h 编码倒放画面（${e.message}）")
            }
        }

        private fun queueFrame(ptsUs: Long) {
            val codec = encoder ?: return
            val at = if (ptsUs <= lastQueuedUs) lastQueuedUs + 1 else ptsUs
            lastQueuedUs = at
            while (true) {
                if (cancelled()) throw CancellationException()
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    // The whole codec buffer is valid input; its layout (strides, padding) is the image's.
                    val capacity = codec.getInputBuffer(index)?.capacity() ?: frame.size
                    val image = codec.getInputImage(index) ?: throw Unsupported("这台设备的编码器不支持通用 YUV 输入")
                    copyPlaneIn(image.planes[0], width, height, 0)
                    copyPlaneIn(image.planes[1], width / 2, height / 2, width * height)
                    copyPlaneIn(image.planes[2], width / 2, height / 2, width * height + (width / 2) * (height / 2))
                    codec.queueInputBuffer(index, 0, capacity, at, 0)
                    return
                }
                drainEncoder(false)
            }
        }

        private fun finishEncoding() {
            val codec = encoder ?: return
            while (true) {
                if (cancelled()) throw CancellationException()
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, lastQueuedUs.coerceAtLeast(0), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drainEncoder(false)
            }
            drainEncoder(true)
        }

        private fun drainEncoder(untilEnd: Boolean) {
            val codec = encoder ?: return
            val stall = StallGuard("编码")
            while (true) {
                if (cancelled()) throw CancellationException()
                val index = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0)
                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) stall.progressed() else if (untilEnd) stall.check()
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> startMuxer(codec.outputFormat)
                    index >= 0 -> {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxerStarted) throw Unsupported("编码器没有给出画面格式")
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        }

        private fun startMuxer(videoFormat: MediaFormat) {
            if (muxerStarted) return
            videoTrack = muxer.addTrack(videoFormat)
            val audioTrack = audio?.let { muxer.addTrack(it.format) }
            muxer.start()
            muxerStarted = true
            if (audio != null && audioTrack != null) audio.writeTo(muxer, audioTrack)
        }
    }

    /** Fails a codec loop that stops producing output, instead of spinning forever. */
    private class StallGuard(private val what: String) {
        private var last = System.nanoTime()

        fun progressed() {
            last = System.nanoTime()
        }

        fun check() {
            if (System.nanoTime() - last > STALL_LIMIT_NS) throw Unsupported("${what}超时：这台设备的编解码器没有响应")
        }
    }

    private fun feed(decoder: MediaCodec, extractor: MediaExtractor): Boolean {
        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
        return if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            false
        }
    }

    private fun copySample(buffer: ByteBuffer, info: MediaCodec.BufferInfo): Sample {
        val data = ByteArray(info.size)
        buffer.position(info.offset)
        buffer.get(data)
        return Sample(data, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
    }

    /** Typical frame spacing: the median gap between sample times, else the declared frame rate. */
    private fun frameDuration(times: List<Long>, format: MediaFormat): Long {
        val gaps = times.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        if (gaps.isNotEmpty()) return gaps[gaps.size / 2]
        val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
        return 1_000_000L / fps.coerceAtLeast(1)
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val CHUNK_FRAMES = 2048
        const val AUDIO_BITRATE = 192_000
        const val AUDIO_SHARE = 0.15f
        const val DECODE_SHARE = 0.4f
        const val OPEN_GOP_EXTRA_SAMPLES = 8
        const val ENCODE_FPS = 30
        const val BITS_PER_PIXEL_FRAME = 0.4
        /** Priming samples of Android's AAC encoder: 2048, measured in the rev 88 sync check (not AAC's usual 1024). */
        const val AAC_PRIMING_FRAMES = 2048
        const val MIN_BITRATE = 2_000_000
        const val MAX_BITRATE = 40_000_000
        const val STALL_LIMIT_NS = 20_000_000_000L
    }
}
