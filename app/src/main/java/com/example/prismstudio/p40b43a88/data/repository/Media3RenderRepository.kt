package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.domain.usecase.FileNames
import com.example.prismstudio.p40b43a88.domain.usecase.RenderErrorText
import com.example.prismstudio.p40b43a88.data.engine.FirstAudioTrackExtractorsFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.prismstudio.p40b43a88.data.engine.StillColors
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AssetLoader
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.prismstudio.p40b43a88.data.engine.AacDelayEncoderFactory
import com.example.prismstudio.p40b43a88.data.engine.WavEncoderFactory
import com.example.prismstudio.p40b43a88.data.engine.WavMuxer
import com.example.prismstudio.p40b43a88.data.engine.CompositionFactory
import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.data.engine.AudioStarts
import com.example.prismstudio.p40b43a88.data.engine.RenderSourceFactory
import com.example.prismstudio.p40b43a88.data.engine.YuvVideoReader
import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.LutSource
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec
import com.example.prismstudio.p40b43a88.domain.usecase.BuildRenderPlanUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CompoundClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EstimateRenderUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.LoudnessUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SubtitleExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.StabilizationUseCase
import android.os.storage.StorageManager
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Exports with Media3 [Transformer] and publishes the file to `Movies/Reality Video Editor`. */
@UnstableApi
class Media3RenderRepository(
    private val context: Context,
    private val reverseCopies: ReverseCopyRepository = ReverseCopyRepository.None,
    private val loudness: LoudnessRepository = LoudnessRepository.None,
    private val luts: LutSource = LutSource.NONE,
    private val priming: AudioPrimingRepository = AudioPrimingRepository.None,
    private val noiseProfiles: NoiseProfileRepository = NoiseProfileRepository.None,
    private val planner: BuildRenderPlanUseCase = BuildRenderPlanUseCase(),
    private val estimator: EstimateRenderUseCase = EstimateRenderUseCase(),
    private val levels: LoudnessUseCase = LoudnessUseCase(),
    private val subtitles: SubtitleExportUseCase = SubtitleExportUseCase(),
    private val stabilization: StabilizationRepository = StabilizationRepository.None,
    private val stabilizer: StabilizationUseCase = StabilizationUseCase(),
    private val sourceMedia: DeviceMediaRepository = MediaStoreDeviceMediaRepository(context),
    private val onSourcesChecked: (String, Map<String, Boolean>) -> Unit = { _, _ -> },
) : RenderRepository {

    private val renderLock = Mutex()

    /** Where the current render's sound items really start, by item (renders run one at a time). */
    private val audioStarts = AudioStarts()

    /** When this run of the app began: older working files in the render cache are a killed render's leftovers. */
    private val startedAt = System.currentTimeMillis()
    private val swept = java.util.concurrent.atomic.AtomicBoolean(false)

    override suspend fun render(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
    ): RenderOutput = renderLock.withLock {
        renderTo(document, settings, rangeStart, rangeEnd, onProgress) { file ->
            withContext(Dispatchers.IO) {
                val output = publish(file, settings.fileName)
                withSubtitles(output, document, settings, rangeStart, rangeEnd)
            }
        }
    }

    /**
     * The frame rendered as an export draws it — a two-frame range from [frame] (one frame can round to none),
     * the best quality, no sound — and its first frame kept as a PNG.
     */
    override suspend fun renderStill(document: ProjectDocument, settings: RenderSettings, frame: Int): RenderOutput = renderLock.withLock {
        val still = settings.copy(
            includeVideo = true, includeAudio = false, quality = RenderQuality.BEST, loudnessTargetLufs = null, exportSubtitles = false,
        )
        val end = (frame + STILL_FRAMES).coerceAtMost(document.durationFrames)
        try {
            renderTo(document, still, frame, end, onProgress = {}) { file -> withContext(Dispatchers.IO) { publishStill(file, settings.fileName) } }
        } catch (e: RenderFailure) {
            // The movie in between is only a carrier (release round R9): when the encoder cannot make the export's codec at
            // this size (H.265 4K on some devices), the frame is still drawn the same way through H.264.
            val code = (e.cause as? ExportException)?.errorCode ?: throw e
            if (code / 1000 != ENCODING_ERRORS || still.codec == VideoCodec.H264) throw e
            val carrier = still.copy(codec = VideoCodec.H264)
            renderTo(document, carrier, frame, end, onProgress = {}) { file -> withContext(Dispatchers.IO) { publishStill(file, settings.fileName) } }
        }
    }

    /** Renders [document] to a file and hands it to [deliver] (the mix alone, Export Video off, publishes itself). */
    @Suppress("LongParameterList")
    private suspend fun renderTo(
        project: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        onProgress: (Float) -> Unit,
        deliver: suspend (File) -> RenderOutput,
    ): RenderOutput {
        // Compound clips render what they hold.
        val expanded = CompoundClipUseCase.expanded(project)
        // A project may have been open while the file was moved, relinked or granted access again.
        // Plan the original sources first so a stale offline flag cannot exclude a recovered file from the check.
        val originals = if (expanded.media.none { it.offline }) expanded else expanded.copy(media = expanded.media.map { it.copy(offline = false) })
        val originalPlan = planner(originals, rangeStart, rangeEnd)
        val required = buildSet {
            if (settings.includeVideo) originalPlan.layers.forEach { layer -> layer.segments.mapNotNullTo(this) { it.assetId } }
            if (settings.includeAudio || !settings.includeVideo) originalPlan.audio.filter { originals.mixer.isAudible(it.trackId) }
                .forEach { lane -> lane.segments.mapNotNullTo(this) { it.assetId } }
        }
        // The mixer still decodes muted lanes. Skip unavailable inputs on those lanes without warning about unheard sound.
        val used = required.toMutableSet()
        if (settings.includeAudio || !settings.includeVideo) originalPlan.audio.forEach { lane -> lane.segments.mapNotNullTo(used) { it.assetId } }
        val usedAssets = originals.media.filter { it.id in used }
        val checked = usedAssets.map { it.uri }.distinct().associateWith { sourceMedia.isAvailable(it) }
        if (checked.isNotEmpty()) onSourcesChecked(project.id, checked)
        val document = originals.withMediaAvailability(checked)
        val plan = if (document === originals) originalPlan else planner(document, rangeStart, rangeEnd)
        if (plan.isEmpty) throw RenderFailure("时间线为空，没有可渲染的内容。")
        val missingNames = usedAssets.filter { it.id in required && checked[it.uri] == false }.map { it.name }.distinct()
        // Reversed clips need their reversed copies first; making them is the first part of the progress.
        val reversedIds = plan.reversedAssetIds().intersect(used)
        val reversed = document.media.filter { it.id in reversedIds }
        val prepareShare = if (reversed.isEmpty()) 0f else PREPARE_SHARE
        val reversedUris = try {
            reverseCopies.awaitReady(reversed) { onProgress(it * prepareShare) }
        } catch (e: ReverseCopyFailure) {
            throw RenderFailure("无法生成倒放素材：${e.message}", e)
        }
        // A private registry freezes the looks at render time; live grading does not leak in.
        val mix = normalized(document, settings, rangeStart, rangeEnd)
        // Stabilized clips need their files' camera paths before the looks are frozen.
        val steadied = stabilizer.wanted(mix)
        if (steadied.isNotEmpty()) stabilization.await(steadied)
        val registry = LiveLookRegistry(luts).also { it.setStabilization(stabilization.paths()); it.update(mix) }
        // The mix alone (Export Video off) always has its sound.
        if (settings.includeAudio || !settings.includeVideo) {
            val ducks = loudness.duckEnvelopes(mix)
            registry.setDucks(ducks)
            if (ducks.isNotEmpty()) Log.i(TAG, "Ducking ${ducks.keys}: down ${ducks.values.sumOf { e -> e.amounts.count { it >= 1f } } * 10} ms")
        }
        // Where each sound file's encoder delay puts its samples, for clips starting inside the file.
        val heard = plan.audio.flatMap { lane -> lane.segments.mapNotNull { it.assetId } }.toSet()
        priming.await(document.media.filter { it.id in heard })
        // The noise floors of the files on lanes whose noise reduction is on.
        val denoised = mix.mixer.channels.filter { it.noiseReduction.isActive }.map { it.trackId }.toSet()
        val noisy = plan.audio.filter { it.trackId in denoised }.flatMap { lane -> lane.segments.mapNotNull { it.assetId } }.toSet()
        if (noisy.isNotEmpty()) noiseProfiles.await(document.media.filter { it.id in noisy })
        // Every sound item of the render lines up to the sample by where its first sample lies (RenderSourceFactory notes it).
        audioStarts.clear()
        val factory = CompositionFactory(registry, priming::delayUs, audioStarts, noiseProfiles::profile)
        val assets = document.media.associateBy { it.id }
        val dir = File(context.cacheDir, "renders").apply { mkdirs() }
        if (swept.compareAndSet(false, true)) {
            val gone = withContext(Dispatchers.IO) { sweepRenderLeftovers(dir, startedAt) }
            if (gone > 0) Log.i(TAG, "Removed $gone working files a killed render left")
        }
        // A WAV mixes at its own rate. Media3's gaps are silence at 44.1 kHz, so a 44.1 kHz mix needs nothing more; at 48 kHz
        // each lane opens with a silent file at that rate instead (CompositionFactory.buildAudio). (At 44.1 kHz that file would
        // be 44 samples, 0.998 ms: Media3 counts it as 0 ms long and divides by it working out the progress.)
        val leadIn = if (settings.wav && settings.wavSampleRate != GAP_RATE) silentLeadIn(dir, settings.wavSampleRate) else null
        if (!settings.includeVideo) {
            val composition = factory.buildAudio(plan, assets, reversedUris, leadIn)
            if (composition == null && missingNames.isNotEmpty()) {
                throw RenderFailure(listOf("音频素材无法读取：${missingNames.joinToString(", ")}", "请恢复读取权限或在媒体页重新链接后重试。").joinToString("\n"))
            }
            return renderAudio(document, settings, rangeStart, rangeEnd, composition, dir) {
                onProgress(prepareShare + it * (1 - prepareShare))
            }.copy(offlineMedia = missingNames)
        }
        val (width, height) = estimator.outputSize(settings, document.format)
        val file = File(dir, "output.mp4").apply { delete() }
        // A layered picture renders one layer per pass, bottom first, each drawn over the last pass.
        val passes = plan.stackingPasses()
        val bitrate = estimator.videoBitrate(settings, width, height)
        val stackBitrate = (bitrate * STACK_BITRATE_FACTOR).toInt().coerceIn(MIN_STACK_BITRATE, MAX_STACK_BITRATE)
        if (passes.size > 1) requireStackSpace(dir, stackBitrate, plan.durationFrames.toDouble() / plan.fps)
        else requireOutputSpace(dir, estimator.spaceNeeded(bitrate, settings.includeAudio, plan.durationFrames.toDouble() / plan.fps))
        val clear = withContext(Dispatchers.IO) { clearFrame(dir) }
        val share = (1 - prepareShare) / passes.size
        val stacked = mutableListOf<File>()
        try {
            var under: File? = null
            passes.forEachIndexed { index, pass ->
                val last = index == passes.lastIndex
                val target = if (last) file else File(dir, "stack-$index.mp4").apply { delete() }.also(stacked::add)
                val output = CompositionFactory.Output(
                    width,
                    height,
                    // Every pass at the delivery's rate: each frame of the top layer finds its underlay frame.
                    frameRate = settings.fps,
                    includeAudio = last && settings.includeAudio,
                    keepAudioClock = false,
                    underlayPath = under?.path,
                    clearFrameUri = Uri.fromFile(clear).toString(),
                    conformFrameRate = true,
                    renderClips = true,
                    colorManagement = document.colorManagement,
                )
                val composition = factory.build(pass, assets, output, reversedUris) ?: throw RenderFailure("时间线为空，没有可渲染的内容。")
                // Passes under the top one are working copies: H.264 at a generous bitrate, so the
                // stacked picture keeps the quality of a single render.
                val mimeType = if (last) settings.codec.mimeType else MimeTypes.VIDEO_H264
                val began = System.nanoTime()
                val encoded = withContext(Dispatchers.Main) {
                    export(composition, target, settings, mimeType, if (last) bitrate else stackBitrate) { onProgress(prepareShare + (index + it) * share) }
                }
                if (encoded.width != width || encoded.height != height || encoded.videoMimeType != mimeType) {
                    val actual = "${encoded.videoMimeType} ${encoded.width}x${encoded.height}"
                    val wanted = "$mimeType ${width}x$height"
                    val failure = ExportException.createForCodec(
                        IllegalStateException("Requested $wanted, received $actual"),
                        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
                        ExportException.CodecInfo(wanted, true, false, encoded.videoEncoderName),
                    )
                    throw RenderFailure(describe(failure, settings), failure)
                }
                Log.i(TAG, "Pass ${index + 1}/${passes.size} (${pass.layers.singleOrNull()?.id ?: "empty"}): ${(System.nanoTime() - began) / 1_000_000} ms")
                under?.delete()
                under = target.takeUnless { last }
            }
            return deliver(file).copy(offlineMedia = missingNames)
        } finally {
            // The working files go whatever happens: a failed or cancelled render used to leave its part-written file (R18).
            withContext(NonCancellable + Dispatchers.IO) { (stacked + file).forEach { it.delete() } }
        }
    }

    /**
     * The first frame of [movie] as a PNG in Pictures/Reality Video Editor; the movie is a working file and goes. The frame
     * is decoded on the CPU with the stream's own colour description (the platform thumbnailer gave black here).
     */
    private fun publishStill(movie: File, name: String): RenderOutput {
        val frame = try {
            YuvVideoReader(movie.path).use { reader ->
                reader.frameAt(0L, STILL_TOLERANCE_US)?.let { f -> Bitmap.createBitmap(reader.colors.argb(f), f.width, f.height, Bitmap.Config.ARGB_8888) }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Still: no frame in ${movie.name}", e)
            null
        } finally {
            movie.delete()
        }
        frame ?: throw RenderFailure("无法读取渲染出的画面。")
        val png = ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        frame.recycle()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName(name)}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${PRODUCT_NAME}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw RenderFailure("无法写入图片库，请检查存储空间。")
        try {
            resolver.openOutputStream(uri)?.use { it.write(png) } ?: throw RenderFailure("无法写入图片库，请检查存储空间。")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) throw RenderFailure("无法写入图片库，请检查存储空间。")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is RenderFailure) e else RenderFailure("保存静帧失败：${e.message}", e)
        }
        return RenderOutput(uri.toString(), png.size.toLong())
    }

    /**
     * Resolve's export with Export Video off: the mix alone — AAC at [RenderSettings.AUDIO_ONLY_KBPS] or lossless WAV at the
     * chosen rate and depth ([WavEncoderFactory]) — saved to Music/Reality Video Editor.
     */
    @Suppress("LongParameterList")
    private suspend fun renderAudio(
        document: ProjectDocument,
        settings: RenderSettings,
        rangeStart: Int?,
        rangeEnd: Int?,
        composition: Composition?,
        dir: File,
        onProgress: (Float) -> Unit,
    ): RenderOutput {
        composition ?: throw RenderFailure("渲染范围内没有声音，无法只导出音频。")
        val file = File(dir, "output.${settings.extension}").apply { delete() }
        val began = System.nanoTime()
        try {
            withContext(Dispatchers.Main) {
                export(
                    composition, file, settings, MimeTypes.VIDEO_H264, 0, audioBitrate = RenderSettings.AUDIO_ONLY_KBPS * 1000,
                    wav = settings.wav, onProgress = onProgress,
                )
            }
            Log.i(TAG, "Audio-only export (${settings.audioOnlyLabel}): ${(System.nanoTime() - began) / 1_000_000} ms, ${file.length()} bytes")
            return withContext(Dispatchers.IO) {
                val output = publish(file, settings.fileName, audio = true, format = settings.audioFormat)
                withSubtitles(output, document, settings, rangeStart, rangeEnd)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
        }
    }

    /**
     * With Normalize Audio, the document whose master gain brings the rendered range to the target
     * loudness (measured from the media's cached loudness profiles); otherwise [document] itself.
     */
    private suspend fun normalized(document: ProjectDocument, settings: RenderSettings, rangeStart: Int?, rangeEnd: Int?): ProjectDocument {
        val target = settings.loudnessTargetLufs ?: return document
        if (!settings.includeAudio && settings.includeVideo) return document
        val used = document.timeline.clips.filter { it.kind == ClipKind.AUDIO }.mapNotNull { it.assetId }.toSet()
        val profiles = document.media.filter { it.id in used && !it.offline }
            .mapNotNull { asset -> loudness.profile(asset.uri)?.let { asset.id to it } }
            .toMap()
        // Ducking dips the mix: measure the program as it will play, dips included.
        val normalized = levels.normalizedForExport(document, profiles, target, rangeStart, rangeEnd, loudness.duckEnvelopes(document))
        if (normalized == null) Log.w(TAG, "Normalize Audio: nothing audible to measure; exporting the mix as it is")
        else Log.i(TAG, "Normalize Audio to $target LUFS: master ${document.mixer.masterVolumeDb} -> ${normalized.mixer.masterVolumeDb} dB")
        return normalized ?: document
    }

    @Suppress("LongParameterList")
    private suspend fun export(
        composition: Composition,
        file: File,
        settings: RenderSettings,
        mimeType: String,
        bitrate: Int,
        /** The AAC bitrate asked of the encoder; null leaves its default (a movie's sound). */
        audioBitrate: Int? = null,
        /** The mix alone as WAV: PCM through [WavEncoderFactory] into [WavMuxer] at the settings' rate and depth. */
        wav: Boolean = false,
        onProgress: (Float) -> Unit,
    ): ExportResult = coroutineScope {
        // Video jobs must keep the requested codec and dimensions. On unsupported devices the default
        // fallback can silently deliver a 512-pixel movie for a 720p job. Audio-only exports keep their fallback.
        val encoders = DefaultEncoderFactory.Builder(context).setEnableFallback(bitrate == 0)
        if (bitrate > 0) encoders.setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
        audioBitrate?.let { encoders.setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(it).build()) }
        val baseEncoders = AacDelayEncoderFactory(encoders.build())
        val transformer = Transformer.Builder(context)
            // A WAV holds no picture: no video type is asked of its muxer (it would refuse one).
            .apply { if (!wav) setVideoMimeType(mimeType) }
            .setAudioMimeType(if (wav) MimeTypes.AUDIO_RAW else MimeTypes.AUDIO_AAC)
            .setEncoderFactory(if (wav) WavEncoderFactory(settings.wavSampleRate, settings.wavBits, baseEncoders) else baseEncoders)
            .apply { if (wav) setMuxerFactory(WavMuxer.Factory()) }
            // A busy GPU (several layers, memory pressure) can hold the first samples back for more than
            // Media3's default 10 s while the export is still progressing; a real hang is still caught.
            .setMaxDelayBetweenMuxerSamplesMs(MUXER_TIMEOUT_MS)
            // A vertical timeline encodes upright. Media3 otherwise lays it on its side with a rotation tag, which the
            // passes and stills read back untagged (YuvVideoReader): a stacked pass's underlay and a still came out turned.
            .setPortraitEncodingEnabled(true)
            .setAssetLoaderFactory(assetLoaderFactory())
            // Stills keep their values (no sRGB → BT.709 transfer conversion), as on the monitor.
            .setVideoFrameProcessorFactory(StillColors.frameProcessors())
            .build()
        val ticker = launch {
            val holder = ProgressHolder()
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                delay(250)
            }
        }
        try {
            val result = suspendCancellableCoroutine { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(exportResult)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.w(TAG, "Export failed", exportException)
                        if (cont.isActive) cont.resumeWithException(RenderFailure(describe(exportException, settings), exportException))
                    }
                })
                transformer.start(composition, file.path)
            }
            onProgress(1f)
            result
        } catch (cancelled: CancellationException) {
            // This scope runs on the encoder's main looper. Finish cancellation before releasing its working files.
            transformer.cancel()
            throw cancelled
        } finally {
            ticker.cancel()
        }
    }

    /** The finished file is written in the render cache and then into the gallery: refuses early when both cannot fit. */
    private fun requireOutputSpace(dir: File, needed: Long) {
        shortOfSpace(dir, needed)?.let { left ->
            val gb = { bytes: Long -> String.format(java.util.Locale.US, "%.1f", bytes / 1e9) }
            throw RenderFailure("渲染大约需要 ${gb(needed)} GB 空间（临时文件和存进相册的文件各一份），设备只剩 ${gb(left)} GB。请清理空间，或缩短渲染范围后重试。")
        }
    }

    /** A layered render keeps two working copies of the picture at once; refuses early when they cannot fit. */
    private fun requireStackSpace(dir: File, bitrate: Int, seconds: Double) {
        val needed = (bitrate / 8.0 * seconds * 2).toLong() + SPACE_MARGIN_BYTES
        shortOfSpace(dir, needed)?.let { left ->
            val gb = { bytes: Long -> String.format(java.util.Locale.US, "%.1f", bytes / 1e9) }
            throw RenderFailure("多层画面需要约 ${gb(needed)} GB 临时空间来逐层合成，设备只剩 ${gb(left)} GB。请清理空间，或缩短渲染范围后重试。")
        }
    }

    /**
     * Null when [needed] bytes can be had in [dir], else the bytes there are: free space, or cache the system clears for the
     * render when asked (StorageManager, as Android wants apps to do: plain free space left out the cache other apps
     * keep, so a headset that only looked full refused renders it could hold, R23). The app's own working files that a
     * render reads (reversed copies, proxies, stills) live outside the cache.
     */
    private fun shortOfSpace(dir: File, needed: Long): Long? {
        if (dir.usableSpace >= needed) return null
        val storage = context.getSystemService(StorageManager::class.java) ?: return dir.usableSpace
        return try {
            val volume = storage.getUuidForPath(dir)
            val allocatable = storage.getAllocatableBytes(volume)
            if (allocatable < needed) allocatable else null.also { storage.allocateBytes(volume, needed) }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Cannot make room for the render", e)
            dir.usableSpace
        }
    }

    /** A silent stereo 16-bit WAV at [rate] exactly [CompositionFactory.LANE_LEAD_IN_US] long (in whole samples), as a file URI. */
    private fun silentLeadIn(dir: File, rate: Int): String {
        val frames = Math.round(CompositionFactory.LANE_LEAD_IN_US * rate / 1_000_000.0).toInt()
        check(frames * 1_000_000L == CompositionFactory.LANE_LEAD_IN_US * rate) { "a $rate Hz lead-in is not a whole number of samples" }
        val file = File(dir, "lead-in-$rate.wav")
        if (!file.exists() || file.length() != WavMuxer.HEADER_BYTES + frames * 4L) {
            file.outputStream().use { out ->
                out.write(WavMuxer.header(rate, 2, 16, frames * 4L))
                out.write(ByteArray(frames * 4))
            }
        }
        return android.net.Uri.fromFile(file).toString()
    }

    /** A 2×2 transparent PNG that fills the gaps of each pass at the timeline's frame rate. */
    private fun clearFrame(dir: File): File = File(dir, "clear.png").apply {
        if (exists() && length() > 0) return@apply
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** Copies the render into the shared library: Movies/Reality Video Editor, or Music/Reality Video Editor for the mix alone ([audio], as [format]). */
    private fun publish(file: File, name: String, audio: Boolean = false, format: AudioExportFormat = AudioExportFormat.AAC): RenderOutput {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName(name)}.${if (audio) format.extension else "mp4"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (audio) format.mimeType else MimeTypes.VIDEO_MP4)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "${Environment.DIRECTORY_MUSIC}/${PRODUCT_NAME}" else "Movies/${PRODUCT_NAME}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw RenderFailure("无法写入相册，请检查存储空间。")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw RenderFailure("无法写入相册，请检查存储空间。")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) throw RenderFailure("无法写入相册，请检查存储空间。")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is RenderFailure) e else RenderFailure("保存到相册失败：${e.message}", e)
        }
        val bytes = file.length()
        file.delete()
        return RenderOutput(uri.toString(), bytes)
    }

    /**
     * With Export Subtitle on, [output] with every subtitle track's file beside it in Download/Reality Video Editor — Name.srt for one,
     * Name.en.srt, Name.zh-Hans.srt … for several (Resolve: a file per language).
     */
    private fun withSubtitles(output: RenderOutput, document: ProjectDocument, settings: RenderSettings, rangeStart: Int?, rangeEnd: Int?): RenderOutput {
        if (!settings.exportSubtitles) return output
        val files = subtitles.files(document, settings.subtitleFormat, rangeStart, rangeEnd)
        val saved = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (f in files) {
            val name = subtitles.fileName(safeName(settings.fileName), f, files.size > 1, settings.subtitleFormat)
            val uri = publishSubtitles(name, f.text, settings.subtitleFormat)
            if (uri == null) missing += name else saved += uri
        }
        return output.copy(subtitlesUri = saved.firstOrNull(), subtitleUris = saved, missingSubtitles = missing)
    }

    private fun publishSubtitles(displayName: String, text: String, format: SubtitleFileFormat): String? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${PRODUCT_NAME}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        try {
            val stream = resolver.openOutputStream(uri) ?: throw java.io.IOException("Missing subtitle output stream")
            stream.use { it.write(("\uFEFF" + text).toByteArray(Charsets.UTF_8)) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) != 1) throw java.io.IOException("Subtitle file was not published")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri.toString()
    }.onFailure { Log.w(TAG, "Cannot save the subtitles $displayName", it) }.getOrNull()

    private fun describe(e: ExportException, settings: RenderSettings): String =
        RenderErrorText.describe(e.errorCode, e.errorCodeName, "${settings.codec.label} ${settings.resolution.label} ${settings.fps} fps")

    private fun safeName(name: String) = FileNames.safe(name)

    /**
     * Transformer's own asset loading (default decoders, sRGB stills), except that clipped video sources
     * come from [RenderSourceFactory] (every frame before a cut, exact item lengths, speed without audio).
     */
    private fun assetLoaderFactory(): AssetLoader.Factory {
        return DefaultAssetLoaderFactory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            RenderSourceFactory(DefaultMediaSourceFactory(context, FirstAudioTrackExtractorsFactory()), audioStarts = audioStarts),
            StillColors.bitmapLoader(context),
        )
    }

    private companion object {
        /** The rate of Media3's gap silence, which a render's sound is mixed at unless a lane opens with sound at another. */
        private const val GAP_RATE = 44_100

        const val TAG = "PrismRender"
        /** Frames rendered for a still: one range frame can round to none. */
        const val STILL_FRAMES = 2

        /** Media3's encoding error family (4xxx). */
        const val ENCODING_ERRORS = 4
        /**
         * How far past 0 the first frame's timestamp may sit. `frameAt` takes the last frame within it, so it must
         * stay under half a frame (here even at 100 fps) or the second frame is taken.
         */
        const val STILL_TOLERANCE_US = 5_000L
        /** Share of the reported progress spent making reversed copies, when an export needs them. */
        const val PREPARE_SHARE = 0.1f
        /** Longest pause between written samples before an export counts as hung. */
        const val MUXER_TIMEOUT_MS = 60_000L
        /** Working copies of a layered render get this much more bitrate than the delivery. */
        const val STACK_BITRATE_FACTOR = 1.5
        const val MIN_STACK_BITRATE = 12_000_000
        const val MAX_STACK_BITRATE = 120_000_000
        const val SPACE_MARGIN_BYTES = 200_000_000L
    }
}

/**
 * Deletes the working files in the render cache [dir] last written before [olderThan] — a render the app was killed in
 * left them, up to the whole film (R18) — keeping the reusable pieces (the clear frame, the silent lead-ins). Returns how many.
 */
internal fun sweepRenderLeftovers(dir: File, olderThan: Long): Int =
    dir.listFiles().orEmpty()
        .filter { it.isFile && it.name != "clear.png" && !it.name.startsWith("lead-in-") && it.lastModified() < olderThan }
        .count { it.delete() }
