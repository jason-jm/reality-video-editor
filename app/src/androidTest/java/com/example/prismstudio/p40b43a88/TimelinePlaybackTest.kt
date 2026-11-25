package com.example.prismstudio.p40b43a88

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.ExifInterface
import android.media.ImageReader
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.data.repository.AndroidMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ExoSourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProxyRepository
import com.example.prismstudio.p40b43a88.data.repository.FileReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3RenderRepository
import com.example.prismstudio.p40b43a88.data.repository.MonitorPlayback
import com.example.prismstudio.p40b43a88.data.repository.ProxyRepository
import com.example.prismstudio.p40b43a88.data.repository.RenderFailure
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.UnreadableMediaException
import com.example.prismstudio.p40b43a88.domain.model.*
import com.example.prismstudio.p40b43a88.domain.usecase.ComputeScopesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EdlExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EdlImportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FcpxmlExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FcpxmlImportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FreezeFrameUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus
import com.example.prismstudio.p40b43a88.domain.usecase.SubtitleExportUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SubtitleImportUseCase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same content URIs returned by the device picker, including those with no file extension. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TimelinePlaybackTest : EditorDeviceTest() {
    private fun photo(format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, width: Int = 160, height: Int = 96): MediaAsset {
        val type = when (format) {
            Bitmap.CompressFormat.PNG -> "png"
            Bitmap.CompressFormat.JPEG -> "jpeg"
            else -> "webp"
        }
        return importMedia("still.$type", "image/$type") { out ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GREEN)
            try { check(bitmap.compress(format, 95, out)) } finally { bitmap.recycle() }
        }
    }

    /** Sample the rendered surface independently of the playhead. A healthy clock over a black frame must fail. */
    private class Screen : AutoCloseable {
        private val thread = HandlerThread("Preview sample").apply { start() }
        val reader = ImageReader.newInstance(160, 96, PixelFormat.RGBA_8888, 3)
        @Volatile var color = Color.BLACK
        @Volatile var upper = Color.BLACK
        @Volatile var lower = Color.BLACK
        @Volatile var readFailure: RuntimeException? = null
        init {
            reader.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (e: RuntimeException) {
                    readFailure = e
                    null
                }
                image?.use { frame ->
                    val plane = frame.planes[0]
                    val data = plane.buffer
                    fun at(y: Int): Int {
                        val index = y * plane.rowStride + frame.width / 2 * plane.pixelStride
                        return Color.rgb(data.get(index).toInt() and 255, data.get(index + 1).toInt() and 255, data.get(index + 2).toInt() and 255)
                    }
                    upper = at(frame.height / 4)
                    lower = at(frame.height * 3 / 4)
                    color = at(frame.height / 2)
                }
            }, Handler(thread.looper))
        }
        override fun close() {
            reader.setOnImageAvailableListener(null, null)
            thread.quitSafely()
            thread.join()
            reader.close()
        }
    }

    private fun withMonitor(doc: ProjectDocument, reverse: ReverseCopyRepository = ReverseCopyRepository.None,
                            proxies: ProxyRepository = ProxyRepository.None, block: (Media3ProgramPlaybackRepository, Screen) -> Unit) {
        Screen().use { screen ->
            lateinit var monitor: Media3ProgramPlaybackRepository
            main {
                monitor = Media3ProgramPlaybackRepository(app, LiveLookRegistry(), reverse, proxies = proxies)
                monitor.attachSurface(screen.reader.surface, screen.reader.width, screen.reader.height)
                monitor.setProject(doc)
            }
            try { block(monitor, screen) } finally {
                main { monitor.detachSurface(screen.reader.surface); monitor.release() }
            }
        }
    }

    private fun await(description: String, monitor: MonitorPlayback, screen: Screen?, test: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 8000
        do {
            screen?.readFailure?.let { throw AssertionError("Cannot read the rendered frame", it) }
            if (test()) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < until)
        fail("$description: ${monitor.state.value}, surface=${screen?.color?.toUInt()?.toString(16)}")
    }

    private fun matches(actual: Int, expected: Int): Boolean {
        val components = listOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        val channel = when (expected) { Color.RED -> 0; Color.GREEN -> 1; else -> 2 }
        return components[channel] > 80 && components.indices.all { it == channel || components[channel] > components[it] + 45 }
    }

    private fun verifyPlay(asset: MediaAsset, color: Int, immediately: Boolean = false) = withMonitor(project(asset)) { monitor, screen ->
        if (!immediately) await("prepare ${asset.name}", monitor, screen) { monitor.state.value.durationMs > 0 }
        main { monitor.play() }
        await("play ${asset.name}", monitor, screen) {
            monitor.state.value.positionMs >= 300 && matches(screen.color, color)
        }
        main { monitor.pause() }
        val paused = monitor.state.value.positionMs
        SystemClock.sleep(160)
        assertEquals("pause", paused, monitor.state.value.positionMs)
        main { monitor.seekTo(100); monitor.play() }
        await("replay", monitor, screen) { monitor.state.value.positionMs > 350 && matches(screen.color, color) }
    }

    private fun verifySource(asset: MediaAsset) {
        lateinit var source: ExoSourcePlaybackRepository
        main { source = ExoSourcePlaybackRepository(app) }
        try {
            main { source.load(asset, ColorManagement()); source.play() }
            await("source ${asset.name}", source, null) { source.state.value.positionMs >= 400 }
            main { source.pause(); source.seekTo(200); source.play() }
            await("source resumes after seek", source, null) { source.state.value.positionMs >= 500 }
        } finally { main { source.release() } }
    }

    @Test fun pngOnEmptyTimeline() = verifyPlay(photo(), Color.GREEN)
    @Test fun jpegOnEmptyTimeline() = verifyPlay(photo(Bitmap.CompressFormat.JPEG), Color.GREEN)
    @Test fun webpOnEmptyTimeline() = verifyPlay(photo(Bitmap.CompressFormat.WEBP_LOSSLESS), Color.GREEN)
    @Test fun silentAvcOnEmptyTimeline() = verifyPlay(sample("silent-avc.mp4"), Color.RED)
    @Test fun avcWithSoundOnEmptyTimeline() = verifyPlay(sample("sound-avc.mp4"), Color.RED)
    @Test fun silentHevcOnEmptyTimeline() = verifyPlay(sample("silent-hevc.mp4"), Color.BLUE)
    @Test fun silentWebmOnEmptyTimeline() = verifyPlay(sample("silent-vp9.webm", "video/webm"), Color.BLUE)
    @Test fun playPressedImmediatelyAfterFirstInsert() = verifyPlay(sample("sound-avc.mp4"), Color.RED, immediately = true)
    @Test fun importedHeic() = verifyPlay(sample("still.heic", "image/heic"), Color.GREEN)
    @Test fun importedAvif() = verifyPlay(sample("still.avif", "image/avif"), Color.GREEN)
    @Test fun importedGifAsStill() = verifyPlay(sample("still.gif", "image/gif"), Color.GREEN)
    @Test fun importedBitmapFile() = verifyPlay(sample("still.bmp", "image/bmp"), Color.GREEN)

    private fun verifyMovie(name: String, mime: String, color: Int, portrait: Boolean = false) {
        val asset = sample(name, mime)
        if (portrait) assertTrue("oriented dimensions: ${asset.width} x ${asset.height}", asset.height > asset.width)
        val picture = runBlocking { AndroidMediaPreviewRepository(app).thumbnail(asset.uri, asset.kind, 300, 120) }
        assertNotNull("source thumbnail for $name", picture)
        assertTrue(matches(picture!!.getPixel(picture.width / 2, picture.height / 2), color))
        verifySource(asset)
        verifyPlay(asset, color)
    }

    @Test fun variableRateMov() = verifyMovie("portrait-vfr.mov", "video/quicktime", Color.RED, portrait = true)
    @Test fun rotatedMov() = verifyMovie("rotated-avc.mov", "video/quicktime", Color.RED, portrait = true)
    @Test fun matroskaAvcAac() = verifyMovie("sound-avc.mkv", "video/x-matroska", Color.RED)
    @Test fun webmVp8Opus() = verifyMovie("sound-vp8.webm", "video/webm", Color.BLUE)
    @Test fun av1Mp4() = verifyMovie("silent-av1.mp4", "video/mp4", Color.BLUE)
    @Test fun transportStreamWithSound() = verifyMovie("sound-avc.ts", "video/mp2t", Color.RED)
    @Test fun threeGppH263CameraClip() = verifyMovie("silent-h263.3gp", "video/3gpp", Color.BLUE)

    @Test fun additionalCameraContainersEitherPlayOrAreRejectedDuringImport() {
        val files = listOf(
            Triple("camera-prores.mov", "video/quicktime", Color.RED),
            Triple("camera-mjpeg.avi", "video/x-msvideo", Color.GREEN),
            Triple("camera-part2.mp4", "video/mp4", Color.BLUE),
            Triple("camera-mpeg2.mpg", "video/mpeg", Color.RED),
        )
        val rows = mutableListOf("fixture\tresult")
        val failures = mutableListOf<String>()
        for ((name, mime, color) in files) {
            val asset = try { sample(name, mime) } catch (unsupported: UnreadableMediaException) {
                rows += "$name\trejected on import"
                continue
            }
            try {
                verifySource(asset)
                verifyPlay(asset, color)
                rows += "$name\tsource and timeline playback"
            } catch (failure: AssertionError) {
                failures += "$name: ${failure.message}"
                rows += "$name\taccepted but did not play"
            }
        }
        app.cacheDir.resolve("qa-camera-compatibility.tsv").writeText(rows.joinToString("\n", postfix = "\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("MPEG-4 Part 2 plays on the tested devices", rows.contains("camera-part2.mp4\tsource and timeline playback"))
    }

    @Test fun transparentPngOnTheUpperTrackRevealsTheVideoUnderIt() = runBlocking {
        val video = sample("silent-avc.mp4")
        val overlay = importMedia("overlay.png", "image/png") { stream ->
            val sticker = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sticker)
            canvas.drawRect(0f, 0f, 160f, 180f, android.graphics.Paint().apply { color = Color.GREEN })
            sticker.compress(Bitmap.CompressFormat.PNG, 100, stream)
            sticker.recycle()
        }
        val doc = project(video, overlay)
        val layered = doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.map { clip ->
            if (clip.assetId == overlay.id) clip.copy(start = 0, trackId = Timeline.V2, durationFrames = 30) else clip
        }))
        val graph = AppGraph.from(app)
        val settings = doc.render.copy(fileName = "rve-alpha-${UUID.randomUUID()}", resolution = RenderResolution.HD_720, includeAudio = false)
        val result = graph.render.render(layered, settings, null, null) {}
        imported += Uri.parse(result.uri)
        suspend fun sampleAt(ms: Long) = requireNotNull(graph.previews.framePixels(result.uri, MediaKind.VIDEO, ms, 120, true))
        val over = sampleAt(500)
        fun at(frame: FramePixels, fraction: Int) = frame.pixels[frame.width * frame.height.div(2) + frame.width * fraction / 4]
        assertTrue("opaque half of PNG", matches(at(over, 1), Color.GREEN))
        assertTrue("transparent half shows V1", matches(at(over, 3), Color.RED))
        assertTrue("after the PNG ends V1 still plays", matches(at(sampleAt(1500), 1), Color.RED))
    }

    @Test fun blackLeaderCanBeReadAndFollowedByANonBlackFrame() = runBlocking {
        val asset = sample("black-leader.mp4")
        val previews = AndroidMediaPreviewRepository(app)
        val black = requireNotNull(previews.framePixels(asset.uri, asset.kind, 200, 120, true)) { "valid black leader returned no frame" }
        val pixel = black.pixels[black.height / 2 * black.width + black.width / 2]
        assertTrue("opaque black leader", Color.alpha(pixel) > 240 && maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) <= 8)
        val red = requireNotNull(previews.framePixels(asset.uri, asset.kind, 1450, 120, true))
        assertTrue(matches(red.pixels[red.height / 2 * red.width + red.width / 2], Color.RED))
    }
    @Test fun uhdSixtyFramesPerSecondInput() {
        val asset = sample("uhd-60.mp4")
        assertEquals(3840, asset.width)
        assertEquals(60f, asset.frameRate, 0.1f)
        verifySource(asset)
        verifyPlay(asset, Color.RED)
    }
    @Test fun tenBitHlgInput() {
        val asset = sample("hlg-main10.mp4")
        assertEquals(ColorTransfer.HLG, asset.color.transfer)
        verifySource(asset)
        verifyPlay(asset, Color.BLUE)
    }

    @Test fun tenBitPqInput() {
        val asset = sample("pq-main10.mp4")
        assertEquals(ColorTransfer.PQ, asset.color.transfer)
        verifySource(asset)
        verifyPlay(asset, Color.RED)
    }

    @Test fun hdrThumbnailsAndScopeFramesDecodeWhileTheTimelinePlays() {
        val previews = AndroidMediaPreviewRepository(app)
        for ((file, expected) in listOf("hlg-main10.mp4" to Color.BLUE, "pq-main10.mp4" to Color.RED)) {
            val asset = sample(file)
            withMonitor(project(asset)) { monitor, screen ->
                main { monitor.play() }
                await("timeline playing $file", monitor, screen) {
                    monitor.state.value.positionMs >= 100 && matches(screen.color, expected)
                }
                runBlocking {
                    for (at in listOf(0L, 290L, 800L, 1200L)) {
                        val tile = requireNotNull(previews.thumbnail(asset.uri, asset.kind, at, 144)) { "no thumbnail for $file at $at" }
                        assertTrue("thumbnail for $file at $at", matches(tile.getPixel(tile.width / 2, tile.height / 2), expected))
                        val pixels = requireNotNull(previews.framePixels(asset.uri, asset.kind, at, 96, exact = true))
                        assertEquals(96, pixels.width)
                        assertTrue("scope frame for $file at $at", matches(pixels.pixels[pixels.height / 2 * pixels.width + pixels.width / 2], expected))
                    }
                }
            }
        }
    }

    @Test fun ordinaryHevcAndHdrHevcPlayInTheSameCut() {
        val doc = project(sample("silent-hevc.mp4"), sample("pq-main10.mp4"), sample("silent-hevc.mp4"))
        withMonitor(doc) { monitor, screen ->
            main { monitor.play() }
            for ((at, color) in listOf(300 to Color.BLUE, 2300 to Color.RED, 4300 to Color.BLUE)) {
                await("mixed SDR/HDR at $at", monitor, screen) {
                    monitor.state.value.positionMs in at.toLong()..(at + 1300L) && matches(screen.color, color)
                }
            }
        }
    }

    @Test fun sourceAndAudioOnlyTimelinesCarryDecodedSound() {
        val previews = AndroidMediaPreviewRepository(app)
        for ((file, mime) in listOf("tone.mp3" to "audio/mpeg", "tone.wav" to "audio/wav",
                "tone.flac" to "audio/flac", "tone.m4a" to "audio/mp4", "tone.ogg" to "audio/ogg")) {
            val sound = sample(file, mime)
            assertTrue("$file has a sound track", sound.hasAudio)
            assertTrue("$file has a tone", runBlocking { previews.waveform(sound.uri) }!!.peak(200, 700) > 0.05f)
            verifySource(sound)
            withMonitor(project(sound)) { monitor, screen ->
                main { monitor.play() }
                await("$file on timeline", monitor, screen) { monitor.state.value.positionMs >= 350 }
            }
        }
    }

    @Test fun audioAfterALeadingGapCanStartAtItsFirstFrame() {
        for ((file, mime) in listOf("tone.ogg" to "audio/ogg", "tone.wav" to "audio/wav")) {
            val doc = project(sample(file, mime))
            val moved = doc.timeline.copy(clips = doc.timeline.clips.map { it.copy(start = 277) })
            withMonitor(doc.copy(timeline = moved)) { monitor, screen ->
                main { monitor.seekTo(9233); monitor.play() }
                await("$file after a leading gap", monitor, screen) { monitor.state.value.positionMs > 9600 }
            }
        }
    }

    @Test fun hdrFreezeKeepsThePictureAcrossBothCutsInPlaybackAndExport() = runBlocking {
        val shot = sample("pq-gray.mp4")
        val doc = project(shot).copy(colorManagement = ColorManagement(toneMapping = ToneMapping.SIMPLE))
        val graph = AppGraph.from(app)
        val freeze = FreezeFrameUseCase(ids)
        val request = freeze.request(doc, doc.timeline.clips.first().id, 18)
        val captured = requireNotNull(graph.stills.capture(shot, request.sourceMs, request.stillId, request.stillName))
        val file = java.io.File(requireNotNull(Uri.parse(captured.uri).path))
        try {
            val held = freeze.insert(doc, request, captured, 18, 30).doc
            assertEquals(ColorTransfer.PQ, held.asset(captured.id)!!.effectiveTransfer)
            val positions = listOf(250L, 1100L, 1900L)
            val display = mutableListOf<Int>()
            withMonitor(held) { monitor, screen ->
                main { monitor.play() }
                for (at in positions) {
                    await("HDR freeze playback at $at", monitor, screen) {
                        monitor.state.value.positionMs in at..at + 400 && Color.red(screen.color) > 50
                    }
                    display += Color.red(screen.color)
                }
            }
            assertTrue("monitor before, during and after the hold: $display", display.max() - display.min() <= 16)
            val settings = held.render.copy(fileName = "rve-freeze-${UUID.randomUUID()}", resolution = RenderResolution.HD_720, includeAudio = false)
            val uri = Uri.parse(graph.render.render(held, settings, null, null) {}.uri)
            imported += uri
            val levels = positions.map { at ->
                val frame = requireNotNull(graph.previews.framePixels(uri.toString(), MediaKind.VIDEO, at, 96, true))
                Color.red(frame.pixels[frame.height / 2 * frame.width + frame.width / 2])
            }
            assertTrue("export before, during and after the hold: $levels", levels.min() > 50 && levels.max() - levels.min() <= 16)
        } finally { file.delete() }
    }

    @Test fun renderJpegBetweenTwoVideosAndReadBackBothCuts() = runBlocking {
        val doc = project(sample("sound-avc.mp4"), photo(Bitmap.CompressFormat.JPEG), sample("silent-hevc.mp4"))
        val settings = doc.render.copy(fileName = "rve-qa-${UUID.randomUUID()}", resolution = RenderResolution.HD_720)
        val output = Media3RenderRepository(app).render(doc, settings, null, null) {}
        val uri = Uri.parse(output.uri)
        imported += uri
        assertTrue("all sources online", output.offlineMedia.isEmpty())
        if (InstrumentationRegistry.getArguments().getString("keepMixedExport") == "true") {
            requireNotNull(resolver.openInputStream(uri)).use { input ->
                app.cacheDir.resolve("qa-mixed-export.mp4").outputStream().use { input.copyTo(it) }
            }
        }
        MediaMetadataRetriever().use { frames ->
            frames.setDataSource(app, uri)
            assertEquals("720", frames.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            assertEquals("yes", frames.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val ms = frames.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("duration $ms", ms in 8900..9100)
        }
        val previews = AndroidMediaPreviewRepository(app)
        for ((at, color) in listOf(500L to Color.RED, 3200L to Color.GREEN, 7800L to Color.BLUE)) {
            val frame = requireNotNull(previews.framePixels(uri.toString(), MediaKind.VIDEO, at, 160, exact = true))
            val pixel = frame.pixels[frame.width / 2 + (frame.height / 2) * frame.width]
            assertTrue("export at $at: #${pixel.toUInt().toString(16)}", matches(pixel, color))
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(app, uri, null)
            val types = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            assertTrue("export tracks $types", types.containsAll(listOf("video/avc", "audio/mp4a-latm")))
        } finally { extractor.release() }
    }

    @Test fun repeatedCodecAndPhotoChangesKeepPlayingInTheSameEditor() {
        val sources = listOf(
            sample("sound-avc.mp4") to Color.RED,
            sample("still.heic", "image/heic") to Color.GREEN,
            sample("silent-av1.mp4") to Color.BLUE,
            sample("pq-main10.mp4") to Color.RED,
            sample("still.avif", "image/avif") to Color.GREEN,
            sample("silent-vp9.webm", "video/webm") to Color.BLUE,
        )
        val cuts = sources.map { (asset, color) -> project(asset) to color }
        val base = cuts.first().first
        withMonitor(base) { monitor, screen ->
            repeat(3) { pass ->
                for ((cut, color) in cuts) {
                    val start = 150L + 120L * pass
                    main {
                        monitor.pause()
                        monitor.setProject(base.copy(media = cut.media, timeline = cut.timeline))
                        monitor.seekTo(start)
                        monitor.play()
                    }
                    await("repeat $pass, ${cut.media.single().name}", monitor, screen) {
                        monitor.state.value.positionMs >= start + 200 && matches(screen.color, color)
                    }
                    assertNull(monitor.state.value.error)
                }
            }
        }
    }

    @Test fun relinkSameAssetIdAndEmptyTimelineCannotKeepTheOldMovie() {
        val red = sample("silent-avc.mp4")
        val blue = sample("silent-hevc.mp4")
        val doc = project(red)
        withMonitor(doc) { monitor, screen ->
            main { monitor.play() }
            await("original movie", monitor, screen) { monitor.state.value.positionMs >= 200 && matches(screen.color, Color.RED) }
            main { monitor.setProject(doc.copy(media = listOf(blue.copy(id = red.id)))) }
            await("replacement picture with the same id", monitor, screen) { matches(screen.color, Color.BLUE) }
            main { monitor.setProject(doc.copy(timeline = Timeline.EMPTY)); monitor.play() }
            SystemClock.sleep(200)
            assertEquals(0L, monitor.state.value.durationMs)
            assertFalse(monitor.state.value.playing)
        }
    }

    @Test fun gradeChangesThePausedPictureAndExportedStill() = runBlocking {
        val doc = project(photo())
        val graded = doc.copy(timeline = doc.timeline.copy(clips = doc.timeline.clips.map {
            it.copy(grade = it.grade.copy(saturation = 0f))
        }))
        fun grey(pixel: Int): Boolean {
            val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            return channels.min() > 20 && channels.max() - channels.min() < 12
        }
        withMonitor(doc) { monitor, screen ->
            await("green source", monitor, screen) { matches(screen.color, Color.GREEN) }
            main { monitor.setProject(graded) }
            await("desaturation on a paused frame", monitor, screen) { grey(screen.color) }
            main { monitor.setProject(doc) }
            await("return to neutral grade", monitor, screen) { matches(screen.color, Color.GREEN) }
        }
        val settings = graded.render.copy(fileName = "rve-grade-${UUID.randomUUID()}", resolution = RenderResolution.HD_720)
        val uri = Uri.parse(Media3RenderRepository(app).renderStill(graded, settings, 12).uri)
        imported += uri
        val bitmap = requireNotNull(resolver.openInputStream(uri)).use(android.graphics.BitmapFactory::decodeStream)
        try { assertTrue("graded PNG", grey(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))) } finally { bitmap.recycle() }
    }

    @Test fun mixerFaderAndEqAreAudibleInWavAndAac() = runBlocking {
        val doc = project(sample("tone.wav", "audio/wav"))
        val track = doc.timeline.clips.single().trackId
        val engine = Media3RenderRepository(app)
        val previews = AndroidMediaPreviewRepository(app)
        suspend fun amplitude(project: ProjectDocument, format: AudioExportFormat, rate: Int, bits: Int): Float {
            val settings = project.render.copy(fileName = "rve-mix-${UUID.randomUUID()}", includeVideo = false, includeAudio = true,
                audioFormat = format, wavSampleRate = rate, wavBits = bits, loudnessTargetLufs = null)
            val output = engine.render(project, settings, null, null) {}
            imported += Uri.parse(output.uri)
            if (format == AudioExportFormat.WAV) {
                val bytes = requireNotNull(resolver.openInputStream(Uri.parse(output.uri))).use { it.readBytes() }
                val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
                assertEquals(rate, header.getInt(24))
                assertEquals(bits, header.getShort(34).toInt())
            }
            return requireNotNull(previews.waveform(output.uri)).peak(400, 900)
        }
        val base = amplitude(doc, AudioExportFormat.WAV, 48000, 24)
        assertTrue("original amplitude $base", base > 0.05f)
        val channel = doc.mixer.channel(track)
        val quieter = doc.copy(mixer = doc.mixer.replace(channel.copy(volumeDb = -12f)))
        val faderRatio = amplitude(quieter, AudioExportFormat.WAV, 44100, 16) / base
        assertTrue("−12 dB fader produced ratio $faderRatio", faderRatio in 0.21f..0.30f)
        val notch = channel.copy(eq = TrackEq(enabled = true, midHz = 660f, midDb = -12f))
        val eqRatio = amplitude(doc.copy(mixer = doc.mixer.replace(notch)), AudioExportFormat.AAC, 48000, 16) / base
        assertTrue("−12 dB at the source frequency produced ratio $eqRatio", eqRatio in 0.16f..0.40f)
    }

    @Test fun busGainAndDialogDuckingCanBeHeardInTheExportedMix() = runBlocking {
        val graph = AppGraph.from(app)
        val base = project(sample("tone.wav", "audio/wav"))
        val music = base.timeline.clips.single()
        val guide = music.copy(id = ids.next("guide"), trackId = Timeline.A1, start = 12, durationFrames = 16)
        assertEquals(Timeline.A2, music.trackId)
        val bus = MixerBus("b1", "Music", volumeDb = -6f)
        val channel = base.mixer.channel(music.trackId)
        val mix = base.mixer.copy(buses = listOf(bus)).replace(channel.copy(bus = bus.id,
            ducking = TrackDucking(keyTrackId = guide.trackId, depthDb = 12f, thresholdDb = -50f, attackMs = 10f, releaseMs = 50f)))
            .replace(base.mixer.channel(guide.trackId).copy(mute = true))
        val ducked = base.copy(timeline = base.timeline.copy(clips = listOf(music, guide)), mixer = mix)
        suspend fun levels(document: ProjectDocument): List<Float> {
            val settings = document.render.copy(fileName = "rve-bus-${UUID.randomUUID()}", includeVideo = false,
                includeAudio = true, audioFormat = AudioExportFormat.WAV, wavSampleRate = 48000, wavBits = 16, loudnessTargetLufs = null)
            val result = graph.render.render(document, settings, null, null) {}
            imported += Uri.parse(result.uri)
            val waveform = requireNotNull(graph.previews.waveform(result.uri))
            return listOf(waveform.peak(80, 250), waveform.peak(580, 800))
        }
        val original = levels(base)
        val processed = levels(ducked)
        assertTrue("original levels $original", original.all { it > 0.05f })
        assertTrue("6 dB bus attenuation $processed / $original", processed[0] / original[0] in 0.42f..0.60f)
        assertTrue("12 dB duck under the guide plus 6 dB bus attenuation $processed / $original", processed[1] / original[1] in 0.085f..0.18f)
    }

    @Test fun renderedCrossDissolveAndBurnedCaptionsAppearOnlyAtTheirEditPoints() = runBlocking {
        val original = project(sample("silent-avc.mp4"), sample("silent-hevc.mp4"))
        val first = original.timeline.clips.first().copy(durationFrames = 30)
        val second = original.timeline.clips.last().copy(start = 30, durationFrames = 30,
            transitionIn = Transition(TransitionType.CROSS_DISSOLVE, frames = 15))
        val timeline = original.timeline.copy(clips = listOf(first, second))
        val subs = SubtitleImportUseCase(ids)
        val words = "1\n00:00:00,150 --> 00:00:00,700\nVISIBLE CAPTIONS\n"
        val cut = original.copy(timeline = subs.import(timeline, subs.parse(words), 30, "edit.en.srt").timeline)
        val graph = AppGraph.from(app)
        val settings = cut.render.copy(fileName = "rve-transition-${UUID.randomUUID()}", resolution = RenderResolution.HD_720, includeAudio = false)
        val output = graph.render.render(cut, settings, null, null) {}
        imported += Uri.parse(output.uri)
        if (InstrumentationRegistry.getArguments().getString("keepMixedExport") == "true") {
            requireNotNull(resolver.openInputStream(Uri.parse(output.uri))).use { input ->
                app.cacheDir.resolve("qa-caption-dissolve.mp4").outputStream().use { input.copyTo(it) }
            }
        }
        val caption = cut.timeline.clips.single { it.kind == ClipKind.TITLE }
        assertEquals(5, caption.start)
        assertEquals(21, caption.end)
        suspend fun picture(at: Long) = requireNotNull(graph.previews.framePixels(output.uri, MediaKind.VIDEO, at, 480, true))
        fun subtitlePixels(frame: FramePixels): Int = (frame.height * 2 / 3 * frame.width until frame.pixels.size).count { i ->
            val pixel = frame.pixels[i]
            Color.red(pixel) > 150 && Color.green(pixel) > 150 && Color.blue(pixel) > 150
        }
        val captioned = picture(400)
        val uncaptioned = picture(800)
        assertTrue("caption glyphs rendered", subtitlePixels(captioned) > 35)
        assertEquals("caption disappears at the out mark", 0, subtitlePixels(uncaptioned))
        fun center(frame: FramePixels) = frame.pixels[frame.height / 2 * frame.width + frame.width / 2]
        assertTrue(matches(center(uncaptioned), Color.RED))
        val blended = center(picture(1250))
        assertTrue("cross dissolve mixes both camera shots: ${blended.toUInt().toString(16)}",
            Color.red(blended) > 45 && Color.blue(blended) > 45 && Color.green(blended) < 30)
        assertTrue(matches(center(picture(1760)), Color.BLUE))
    }

    @Test fun undecodableFilesAreRejectedAtImport() {
        for ((name, type) in listOf("bad.jpg" to "image/jpeg", "bad.mp4" to "video/mp4", "bad.mp3" to "audio/mpeg")) {
            try {
                importMedia(name, type) { it.write("broken media".toByteArray()) }
                fail("import accepted $name")
            } catch (_: UnreadableMediaException) { /* rejection is reported to the importing UI */ }
        }
    }

    @Test(timeout = 600_000) fun renderDeclaredCodecSizeRateCombinationsAndInspectTracks() = runBlocking {
        val doc = project(sample("silent-avc.mp4"))
        val engine = Media3RenderRepository(app)
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        val failures = mutableListOf<String>()
        val receipt = app.cacheDir.resolve("qa-export-matrix.tsv")
        receipt.printWriter().use { log ->
            log.println("codec\twidth\theight\tfps\tresult")
            for (codec in VideoCodec.entries) for (resolution in RenderResolution.entries) for (fps in ProjectFormat.FRAME_RATES) {
                val height = resolution.shortSide
                val width = height * 16 / 9
                val key = "${codec.label}\t$width\t$height\t$fps"
                val supported = encoders.filter { codec.mimeType in it.supportedTypes }.any {
                    val caps = it.getCapabilitiesForType(codec.mimeType)
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats &&
                        caps.videoCapabilities?.areSizeAndRateSupported(width, height, fps.toDouble()) == true
                }
                val settings = doc.render.copy(fileName = "rve-format-${UUID.randomUUID()}", codec = codec, resolution = resolution,
                    fps = fps, includeAudio = false, loudnessTargetLufs = null)
                try {
                    val uri = Uri.parse(engine.render(doc, settings, 12, 27) {}.uri)
                    imported += uri
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(app, uri, null)
                        assertEquals(1, extractor.trackCount)
                        val track = extractor.getTrackFormat(0)
                        assertEquals(codec.mimeType, track.getString(MediaFormat.KEY_MIME))
                        assertEquals(width, track.getInteger(MediaFormat.KEY_WIDTH))
                        assertEquals(height, track.getInteger(MediaFormat.KEY_HEIGHT))
                        extractor.selectTrack(0)
                        val timestamps = mutableListOf<Long>()
                        while (extractor.sampleTime >= 0) { timestamps += extractor.sampleTime; extractor.advance() }
                        assertTrue("frames=${timestamps.size}", kotlin.math.abs(timestamps.size - fps * 0.5) <= 1)
                        val intervals = timestamps.sorted().zipWithNext { a, b -> b - a }.sorted()
                        assertTrue("frame interval", kotlin.math.abs(intervals[intervals.size / 2] - 1_000_000.0 / fps) < 1200)
                    } finally { extractor.release() }
                    log.println("$key\t${if (supported) "pass" else "pass despite unadvertised encoder"}")
                } catch (e: RenderFailure) {
                    if (supported || e.message.isNullOrBlank()) failures += "$key: $e"
                    log.println("$key\t${if (supported) "failed" else "unavailable"}: ${e.message?.lineSequence()?.firstOrNull()}")
                } catch (e: Exception) {
                    failures += "$key: $e"
                    log.println("$key\t$e")
                } catch (e: AssertionError) {
                    failures += "$key: $e"
                    log.println("$key\t$e")
                }
                log.flush()
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun documentPickerReadsPhotoDimensionsAndTinyPictures() {
        for ((w, h) in listOf(160 to 96, 1 to 1)) {
            val asset = photo(width = w, height = h)
            assertEquals(w, asset.width)
            assertEquals(h, asset.height)
        }
    }

    @Test fun cameraJpegKeepsItsExifRotationInPreviewAndExport() = runBlocking {
        val file = java.io.File.createTempFile("rve-rotate-", ".jpg", app.cacheDir)
        val shot = try {
            val bitmap = Bitmap.createBitmap(160, 96, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            Canvas(bitmap).drawRect(80f, 0f, 160f, 96f, Paint().apply { color = Color.BLUE })
            try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 97, it)) } } finally { bitmap.recycle() }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            importMedia("portrait.jpg", "image/jpeg") { out -> file.inputStream().use { it.copyTo(out) } }
        } finally { file.delete() }
        assertEquals(96 to 160, shot.width to shot.height)
        val doc = project(shot)
        withMonitor(doc) { monitor, screen ->
            main { monitor.play() }
            await("upright camera photo on the timeline", monitor, screen) {
                monitor.state.value.positionMs > 250 && matches(screen.upper, Color.RED) && matches(screen.lower, Color.BLUE)
            }
        }
        val output = Media3RenderRepository(app).renderStill(doc, doc.render.copy(fileName = "rve-rotate-${UUID.randomUUID()}"), 5)
        imported += Uri.parse(output.uri)
        val frame = requireNotNull(AndroidMediaPreviewRepository(app).framePixels(output.uri, MediaKind.IMAGE, 0, 160, true))
        fun center(y: Int) = frame.pixels[y * frame.width + frame.width / 2]
        assertTrue("top of oriented still", matches(center(frame.height / 4), Color.RED))
        assertTrue("bottom of oriented still", matches(center(frame.height * 3 / 4), Color.BLUE))
    }

    private suspend fun saveAndReadDocument(graph: AppGraph, extension: String, mime: String, text: String): String {
        val name = "rve-qa-${UUID.randomUUID()}.$extension"
        val path = requireNotNull(graph.downloads.save(name, mime, text.toByteArray()))
        val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val id = resolver.query(downloads, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(path.substringAfterLast('/')), null)!!.use {
            check(it.moveToFirst())
            it.getLong(0)
        }
        val uri = ContentUris.withAppendedId(downloads, id)
        imported += uri
        return graph.documents.read(uri.toString(), 1024 * 1024).bytes.decodeToString()
    }

    @Test fun projectBackupAndInterchangePreserveTheCut() = runBlocking {
        val graph = AppGraph.from(app)
        val twoShots = project(sample("silent-avc.mp4"), sample("silent-hevc.mp4"))
        val marked = twoShots.copy(timeline = twoShots.timeline.copy(markers = listOf(Marker(ids.next("m"), 60, "Change of shot"))))
        try {
            val time = System.currentTimeMillis()
            graph.backups.write(marked, time)
            assertEquals(time, graph.backups.list(marked.id).single().takenAtMillis)
            val restored = requireNotNull(graph.backups.read(marked.id, time))
            assertEquals(marked.timeline, restored.timeline)
            val edl = saveAndReadDocument(graph, "edl", "text/plain", EdlExportUseCase()(restored).text)
            val xml = saveAndReadDocument(graph, "fcpxml", "application/xml", FcpxmlExportUseCase()(restored).text)
            val fromXml = FcpxmlImportUseCase(ids)(restored, xml, "cut.fcpxml").doc
            val fromEdl = EdlImportUseCase(ids)(restored, edl, "cut.edl").doc
            for (cut in listOf(fromXml, fromEdl)) {
                val clips = cut.timeline.clips.filter { it.trackId == Timeline.V1 }.sortedBy { it.start }
                assertEquals(restored.media.map { it.id }, clips.map { it.assetId })
                assertEquals(listOf(0, 60), clips.map { it.start })
                assertEquals(listOf(60, 60), clips.map { it.durationFrames })
            }
            assertTrue(fromXml.timeline.markers.any { it.frame == 60 && it.label == "Change of shot" })
        } finally { graph.backups.deleteAll(marked.id) }
    }

    @Test fun localizedCaptionsAndVideoScopesUseActualMedia() = runBlocking {
        val graph = AppGraph.from(app)
        val shot = sample("silent-avc.mp4")
        val doc = project(shot)
        val importer = SubtitleImportUseCase(ids)
        val srt = saveAndReadDocument(graph, "srt", "application/x-subrip", "1\n00:00:00,100 --> 00:00:01,200\nHello\n")
        val vtt = saveAndReadDocument(graph, "vtt", "text/vtt", "WEBVTT\n\n00:00:00.200 --> 00:00:01.500\nこんにちは\n")
        val english = importer.import(doc.timeline, importer.parse(srt), 30, "captions.en.srt")
        val japanese = importer.import(english.timeline, importer.parse(vtt), 30, "captions.ja.vtt")
        val files = SubtitleExportUseCase().files(doc.copy(timeline = japanese.timeline), SubtitleFileFormat.WEBVTT)
        assertEquals(setOf("en", "ja"), files.map { it.language.code }.toSet())
        assertTrue(files.single { it.language.code == "en" }.text.contains("00:00:00.100 --> 00:00:01.200"))
        assertTrue(files.single { it.language.code == "ja" }.text.contains("こんにちは"))
        val frame = requireNotNull(AndroidMediaPreviewRepository(app).framePixels(shot.uri, shot.kind, 400, 160, true))
        for (mode in ScopeMode.entries) {
            val scope = ComputeScopesUseCase()(frame, mode)
            assertEquals(mode, scope.mode)
            assertTrue("empty $mode", scope.planes.all { plane -> plane.any { it > 0 } })
        }
    }

    @Test fun movingFootagePlaysInOrderAndTheReversedClipPlaysBackwards() = runBlocking {
        val shot = sample("changing-avc.mp4")
        val normal = project(shot)
        val dir = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "reverse-test-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val copies = FileReverseCopyRepository(app, dir.resolve("copies"), dir.resolve("scratch"), scope)
            withTimeout(60_000) { copies.awaitReady(listOf(shot)) {} }
            val reversed = normal.copy(timeline = normal.timeline.copy(clips = normal.timeline.clips.map { it.copy(reverse = true) }))
            for ((cut, first, last) in listOf(Triple(normal, Color.RED, Color.BLUE), Triple(reversed, Color.BLUE, Color.RED))) {
                withMonitor(cut, reverse = copies) { monitor, screen ->
                    main { monitor.play() }
                    await("first moving frame", monitor, screen) { monitor.state.value.positionMs in 90..880 && matches(screen.color, first) }
                    await("second moving frame", monitor, screen) { monitor.state.value.positionMs in 1100..1950 && matches(screen.color, last) }
                }
            }
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun generatedProxyPlaysAndDeletingItReturnsToThe4kOriginal() = runBlocking {
        val shot = sample("uhd-60.mp4")
        val dir = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "proxy-test-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val proxies = FileProxyRepository(app, dir, scope)
            main { proxies.request(listOf(shot)) }
            val status = withTimeout(60_000) {
                proxies.proxies.first { it[shot.id] is ProxyStatus.Ready || it[shot.id] is ProxyStatus.Failed }[shot.id]
            }
            assertTrue("proxy status: $status", status is ProxyStatus.Ready)
            val ready = status as ProxyStatus.Ready
            assertEquals(960 to 540, ready.width to ready.height)
            assertTrue(ready.sizeBytes > 0)
            withMonitor(project(shot).copy(useProxies = true), proxies = proxies) { monitor, screen ->
                main { monitor.play() }
                await("proxy playback", monitor, screen) { monitor.state.value.positionMs >= 250 && matches(screen.color, Color.RED) }
                main { monitor.pause(); proxies.delete(shot) }
                assertFalse(java.io.File(requireNotNull(Uri.parse(ready.uri).path)).exists())
                main { monitor.seekTo(200); monitor.play() }
                await("original after proxy deletion", monitor, screen) { monitor.state.value.positionMs >= 500 && matches(screen.color, Color.RED) }
            }
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun fourKHevcDeliversTheRequestedFormatOrReportsFailure() = runBlocking {
        val doc = project(sample("silent-avc.mp4"))
        val settings = doc.render.copy(fileName = "rve-4k-${UUID.randomUUID()}", codec = VideoCodec.H265,
            resolution = RenderResolution.UHD_4K, fps = 60, includeAudio = false)
        val uri = try {
            Uri.parse(Media3RenderRepository(app).render(doc, settings, 0, 12) {}.uri)
        } catch (e: RenderFailure) {
            assertTrue("render error gives the user a reason", !e.message.isNullOrBlank())
            return@runBlocking
        }
        imported += uri
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(app, uri, null)
            val track = extractor.getTrackFormat(0)
            assertEquals(settings.codec.mimeType, track.getString(MediaFormat.KEY_MIME))
            assertEquals(3840, track.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(2160, track.getInteger(MediaFormat.KEY_HEIGHT))
        } finally { extractor.release() }
    }

    @Test fun hlgAndPqProxiesKeepTheirPictureAndRenderFromTheOriginals() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "hdr-proxy-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val proxies = FileProxyRepository(app, dir, scope)
            for ((file, color) in listOf("hlg-main10.mp4" to Color.BLUE, "pq-main10.mp4" to Color.RED)) {
                val shot = sample(file)
                main { proxies.request(listOf(shot)) }
                val status = withTimeout(60_000) {
                    proxies.proxies.first { it[shot.id] is ProxyStatus.Ready || it[shot.id] is ProxyStatus.Failed }[shot.id]
                }
                assertTrue("proxy for $file: $status", status is ProxyStatus.Ready)
                val ready = status as ProxyStatus.Ready
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(app, Uri.parse(ready.uri), null)
                    assertEquals(1, extractor.trackCount)
                    val video = extractor.getTrackFormat(0)
                    assertEquals("video/avc", video.getString(MediaFormat.KEY_MIME))
                    assertEquals(ready.width, video.getInteger(MediaFormat.KEY_WIDTH))
                    assertEquals(ready.height, video.getInteger(MediaFormat.KEY_HEIGHT))
                    assertTrue("HDR transfer still tagged in editing proxy: $video",
                        video.getInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO) !in
                            listOf(MediaFormat.COLOR_TRANSFER_HLG, MediaFormat.COLOR_TRANSFER_ST2084))
                } finally { extractor.release() }

                val doc = project(shot).copy(useProxies = true)
                withMonitor(doc, proxies = proxies) { monitor, screen ->
                    main { monitor.play() }
                    await("proxy picture for $file", monitor, screen) {
                        monitor.state.value.positionMs >= 250 && matches(screen.color, color)
                    }
                }
                val settings = doc.render.copy(fileName = "rve-hdr-${UUID.randomUUID()}", resolution = RenderResolution.HD_720, includeAudio = false)
                val uri = Uri.parse(Media3RenderRepository(app).render(doc, settings, 0, 15) {}.uri)
                imported += uri
                val frame = requireNotNull(AndroidMediaPreviewRepository(app).framePixels(uri.toString(), MediaKind.VIDEO, 200, 160, true))
                val center = frame.pixels[(frame.height / 2) * frame.width + frame.width / 2]
                assertTrue("exported $file: #${center.toUInt().toString(16)}", matches(center, color))
            }
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun hdrProxyMigrationKeepsProjectToneMappingAndSharedReferencesInSync() = runBlocking {
        val shot = sample("pq-gray.mp4")
        val dir = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "legacy-hdr-proxy-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            // v1 could save an HDR HEVC file under the H.264 proxy name. Start with that existing on-disk state.
            val bytes = java.security.MessageDigest.getInstance("SHA-1").digest("${shot.uri}|${shot.durationMs}|1".toByteArray())
            val name = bytes.take(10).joinToString("") { "%02x".format(it) }
            val old = dir.resolve("proxy-$name.mp4")
            requireNotNull(resolver.openInputStream(Uri.parse(shot.uri))).use { input -> old.outputStream().use { input.copyTo(it) } }
            val proxies = FileProxyRepository(app, dir, scope)
            main { proxies.refresh(listOf(shot)) }
            val ready = withTimeout(60_000) { proxies.proxies.first { it[shot.id] is ProxyStatus.Ready }[shot.id] as ProxyStatus.Ready }
            assertFalse(old.exists())

            val original = project(shot)
            fun luminance(doc: ProjectDocument): Int {
                var level = -1
                withMonitor(doc, proxies = proxies) { monitor, screen ->
                    main { monitor.play() }
                    await("gray picture under ${doc.colorManagement.toneMapping}", monitor, screen) {
                        monitor.state.value.positionMs >= 250 && Color.red(screen.color) > 50
                    }
                    level = Color.red(screen.color)
                }
                return level
            }
            val levels = listOf(ToneMapping.NONE, ToneMapping.SIMPLE).map { method ->
                val doc = original.copy(colorManagement = ColorManagement(toneMapping = method))
                val camera = luminance(doc.copy(useProxies = false))
                val proxy = luminance(doc.copy(useProxies = true))
                assertTrue("project $method: original $camera, proxy $proxy", kotlin.math.abs(camera - proxy) <= 14)
                proxy
            }
            assertTrue("tone-map controls change the proxy picture: $levels", kotlin.math.abs(levels[0] - levels[1]) >= 6)
            val alias = shot.copy(id = "shared-copy")
            main { proxies.refresh(listOf(alias)) }
            assertEquals(ready, proxies.proxies.value[alias.id])
            main { proxies.delete(shot) }
            assertNull(proxies.proxies.value[alias.id])
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun pictureBetweenTwoCodecsPlaysAndSeeksAcrossBothCuts() {
        val doc = project(sample("sound-avc.mp4"), photo(), sample("silent-hevc.mp4"))
        withMonitor(doc) { monitor, screen ->
            await("load timeline", monitor, screen) { monitor.state.value.durationMs >= 8900 }
            main { monitor.play() }
            for ((ms, color) in listOf(300 to Color.RED, 2300 to Color.GREEN, 7300 to Color.BLUE)) {
                await("timeline at $ms", monitor, screen) { monitor.state.value.positionMs >= ms && matches(screen.color, color) }
            }
            main { monitor.pause() }
            for ((ms, color) in listOf(3500L to Color.GREEN, 400L to Color.RED, 7500L to Color.BLUE)) {
                main { monitor.seekTo(ms) }
                await("paused seek to $ms", monitor, screen) { matches(screen.color, color) }
            }
        }
    }

    @Test fun mixedEditsPlayAndSeekAtEveryProjectFrameRate() {
        val blueVideo = sample("silent-hevc.mp4")
        val greenStill = sample("still.heic", "image/heic")
        val redVideoWithAudio = sample("sound-avc.mp4")
        val base = project(blueVideo, greenStill, redVideoWithAudio)

        for (fps in ProjectFormat.FRAME_RATES) {
            val firstStart = 7
            val photoStart = firstStart + fps + 1
            val lastStart = photoStart + fps + 3
            val clips = base.timeline.clips.map { clip ->
                when (clip.assetId) {
                    blueVideo.id -> clip.copy(start = firstStart, durationFrames = photoStart - firstStart)
                    greenStill.id -> clip.copy(start = photoStart, durationFrames = lastStart - photoStart)
                    else -> clip.copy(start = lastStart, durationFrames = fps + 1)
                }
            }
            val doc = base.copy(format = base.format.copy(fps = fps), timeline = base.timeline.copy(clips = clips))
            val firstMs = Timecode.frameToSeekMs(firstStart, fps)
            val photoMs = Timecode.frameToSeekMs(photoStart, fps)
            val lastMs = Timecode.frameToSeekMs(lastStart, fps)
            val endMs = Timecode.framesToMs(doc.durationFrames, fps)
            withMonitor(doc) { monitor, screen ->
                main { monitor.seekTo(firstMs); monitor.play() }
                await("$fps fps first shot after a leading gap", monitor, screen) {
                    monitor.state.value.positionMs in firstMs + 100 until photoMs && matches(screen.color, Color.BLUE)
                }
                await("$fps fps natural cut to the still", monitor, screen) {
                    monitor.state.value.positionMs in photoMs until lastMs && matches(screen.color, Color.GREEN)
                }
                main { monitor.pause() }
                val paused = monitor.state.value.positionMs
                SystemClock.sleep(160)
                assertEquals("$fps fps holds the still while paused", paused, monitor.state.value.positionMs)
                main { monitor.play() }
                await("$fps fps resume across the linked picture and audio cut", monitor, screen) {
                    monitor.state.value.positionMs in lastMs + 100..endMs && matches(screen.color, Color.RED)
                }
                main { monitor.pause(); monitor.seekTo(photoMs) }
                await("$fps fps paused on first frame of HEIC", monitor, screen) {
                    monitor.state.value.positionMs == photoMs && matches(screen.color, Color.GREEN)
                }
                main { monitor.seekTo(lastMs); monitor.play() }
                await("$fps fps play from first frame of H.264 with audio", monitor, screen) {
                    monitor.state.value.positionMs in lastMs + 140..endMs && matches(screen.color, Color.RED)
                }
                assertNull("$fps fps playback error", monitor.state.value.error)
            }
        }
    }

    /** A marked frame immediately before the end must play from there. Pressing play after completion replays it. */
    private fun verifyTailAndReplay(monitor: MonitorPlayback, fps: Int) {
        await("prepared to check final frames", monitor, null) {
            monitor.state.value.ready && monitor.state.value.durationMs >= 1000
        }
        val duration = monitor.state.value.durationMs
        val lastFrame = Timecode.msToFrames(duration, fps) - 1
        for (frame in lastFrame - 1..lastFrame) {
            val nearEnd = Timecode.frameToSeekMs(frame, fps)
            var started = 0L
            main {
                monitor.seekTo(nearEnd)
                monitor.play()
                started = monitor.state.value.positionMs
            }
            assertTrue("play from frame $frame at $nearEnd ms, got $started ms", started >= nearEnd)
            await("finished playing from frame $frame", monitor, null) {
                monitor.state.value.positionMs >= duration - 1 && !monitor.state.value.playing
            }
        }
        main { monitor.play() }
        await("replay after reaching the end", monitor, null) {
            monitor.state.value.playing && monitor.state.value.positionMs in 100L until duration / 2
        }
    }

    @Test fun sourceCanPlayItsLastFrameAndReplayWhenFinished() {
        val shot = sample("changing-avc.mp4")
        lateinit var monitor: ExoSourcePlaybackRepository
        main {
            monitor = ExoSourcePlaybackRepository(app)
            monitor.load(shot, ColorManagement())
        }
        try { verifyTailAndReplay(monitor, shot.frameRate.toInt()) } finally { main { monitor.release() } }
    }

    @Test fun sixtyFpsTimelineCanPlayItsLastFrameAndReplayWhenFinished() {
        val shot = sample("silent-avc.mp4")
        val base = project(shot)
        val fps = 60
        val cut = base.copy(format = base.format.copy(fps = fps), timeline = base.timeline.copy(
            clips = base.timeline.clips.map { it.copy(durationFrames = Timecode.msToFrames(shot.durationMs, fps)) },
        ))
        withMonitor(cut) { monitor, _ -> verifyTailAndReplay(monitor, fps) }
    }

    private fun playStillAfterGapAt(start: Long) {
        val populated = project(photo(Bitmap.CompressFormat.JPEG))
        val doc = populated.copy(timeline = populated.timeline.copy(clips = populated.timeline.clips.map { it.copy(start = 277) }))
        withMonitor(doc) { monitor, screen ->
            main { monitor.seekTo(start); monitor.play() }
            await("playing from nine seconds into the timeline", monitor, screen) {
                monitor.state.value.positionMs in start + 250..13200 && matches(screen.color, Color.GREEN)
            }
        }
    }

    @Test fun startAtStillDroppedAfterALeadingGap() = playStillAfterGapAt(9233)
    @Test fun startInsideStillDroppedAfterALeadingGap() = playStillAfterGapAt(10000)
    @Test fun startOneMillisecondIntoStillAfterALeadingGap() = playStillAfterGapAt(9234)

    @Test fun navigateFiveWorkspacesAndRenderFromTheQueue() = runBlocking {
        val name = "Queue QA ${UUID.randomUUID()}"
        val original = project(sample("sound-avc.mp4"))
        val doc = original.copy(name = name, render = original.render.copy(fileName = name, resolution = RenderResolution.HD_720))
        withOpenEditor(doc) { window, graph ->
            window.click("媒体", byDescription = true)
            window.bounds("媒体池")
            window.click("剪辑", byDescription = true)
            window.bounds("时间线")
            window.click("调色", byDescription = true)
            window.click("RGB 分量")
            window.bounds("示波器：RGB 分量")
            window.click("音频", byDescription = true)
            window.bounds("调音台")
            window.click("总线")
            window.click("添加总线")
            window.waitFor("add mix bus") { graph.session.document.value!!.mixer.buses.isNotEmpty() }
            window.click("交付", byDescription = true)
            window.click("加入渲染队列")
            window.waitFor("queue export") { graph.session.document.value!!.jobs.size == 1 }
            window.click("全部渲染")
            window.waitFor("finish render") { graph.session.document.value!!.jobs.single().status == RenderStatus.DONE }
            val job = graph.session.document.value!!.jobs.single()
            val uri = Uri.parse(requireNotNull(job.outputUri))
            imported += uri
            assertTrue(job.outputBytes > 0)
            val frame = requireNotNull(AndroidMediaPreviewRepository(app).framePixels(uri.toString(), MediaKind.VIDEO, 450, 80, true))
            assertTrue(matches(frame.pixels[(frame.height / 2) * frame.width + frame.width / 2], Color.RED))
        }
    }

    @Test fun keyboardMarksTheEditAndUndoesAndRedoesIt() = runBlocking {
        val doc = project(sample("silent-avc.mp4")).copy(name = "Keyboard QA ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.bounds("时间线")
            window.key(KeyEvent.KEYCODE_M)
            window.waitFor("M adds a marker") { graph.session.document.value!!.timeline.markers.size == 1 }
            window.key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            window.waitFor("Ctrl+Z undoes marker") { graph.session.document.value!!.timeline.markers.isEmpty() }
            window.key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            window.waitFor("Ctrl+Shift+Z redoes marker") { graph.session.document.value!!.timeline.markers.size == 1 }
        }
    }

    private fun dragPictureAndPlay(asset: MediaAsset, window: EditorWindow, graph: AppGraph): Clip {
        val tile = window.bounds(asset.name, prefix = true)
        val timeline = window.bounds("时间线")
        window.drag(tile, timeline.left + timeline.width() * 0.12f, timeline.top + timeline.height() * 0.46f)
        window.waitFor("drop ${asset.name} on V1") {
            graph.session.document.value!!.timeline.clips.any { it.assetId == asset.id && it.trackId == Timeline.V1 }
        }
        val doc = requireNotNull(graph.session.document.value)
        val clip = doc.timeline.clips.single { it.assetId == asset.id && it.trackId == Timeline.V1 }
        assertTrue("landed after leading gap", clip.start > 0)
        val startMs = clip.start * 1000L / doc.format.fps
        val endMs = (clip.start + clip.durationFrames) * 1000L / doc.format.fps
        window.click("播放", byDescription = true)
        window.waitFor("transport advancing on ${asset.name} at $startMs") {
            graph.program.state.value.positionMs in (startMs + 250)..endMs
        }
        return clip
    }

    private fun dragSample(file: String, mime: String) = runBlocking {
        val asset = sample(file, mime)
        val doc = project().copy(name = "Drag $file ${UUID.randomUUID()}", media = listOf(asset))
        withOpenEditor(doc) { window, graph ->
            val clip = dragPictureAndPlay(asset, window, graph)
            assertEquals(Timecode.msToFrames(asset.durationMs, doc.format.fps), clip.durationFrames)
        }
    }

    @Test fun dragHevcThenPlayFromTheDrop() = dragSample("silent-hevc.mp4", "video/mp4")
    @Test fun dragAv1ThenPlayFromTheDrop() = dragSample("silent-av1.mp4", "video/mp4")
    @Test fun dragVp9ThenPlayFromTheDrop() = dragSample("silent-vp9.webm", "video/webm")
    @Test fun dragPortraitMovThenPlayFromTheDrop() = dragSample("portrait-vfr.mov", "video/quicktime")
    @Test fun dragHdrThenPlayFromTheDrop() = dragSample("pq-main10.mp4", "video/mp4")
    @Test fun dragHeicThenPlayFromTheDrop() = dragSample("still.heic", "image/heic")
    @Test fun dragAvifThenPlayFromTheDrop() = dragSample("still.avif", "image/avif")

    @Test fun dragPhotoOntoAnEmptyTrackThenPlaySplitUndoAndReopen() = runBlocking {
        val still = photo(Bitmap.CompressFormat.JPEG)
        val doc = project().copy(name = "Drag QA ${UUID.randomUUID()}", media = listOf(still))
        withOpenEditor(doc) { window, graph ->
            val clip = dragPictureAndPlay(still, window, graph)
            assertEquals(150, clip.durationFrames)
            val startMs = clip.start * 1000L / doc.format.fps
            window.click("暂停", byDescription = true)
            window.waitFor("pause transport") { !graph.program.state.value.playing }
            // Choose an exact edit point independently of how long the headset's accessibility scan took.
            main { graph.program.seekTo(startMs + 1000) }
            window.click("在播放头处分割", byDescription = true)
            window.waitFor("split photo at ${graph.program.state.value.positionMs} ms") { graph.session.document.value!!.timeline.clips.size == 2 }
            assertEquals(clip.start + doc.format.fps, graph.session.document.value!!.timeline.clips.maxOf { it.start })
            window.click("撤销", prefix = true)
            window.waitFor("undo split") { graph.session.document.value!!.timeline.clips == listOf(clip) }
            window.click("重做", prefix = true)
            window.waitFor("redo split") { graph.session.document.value!!.timeline.clips.size == 2 }
            window.click("添加标记")
            window.waitFor("timeline marker") { graph.session.document.value!!.timeline.markers.isNotEmpty() }
            window.waitFor("autosave edited timeline") { !graph.session.saveState.value.pending }
            val saved = requireNotNull(graph.projects.load(doc.id))
            assertEquals(graph.session.document.value!!.timeline, saved.timeline)
            window.click("返回项目库", byDescription = true)
            window.waitFor("close saved workspace") { graph.session.document.value == null }
            window.click(doc.name, prefix = true)
            SystemClock.sleep(400)
            if (graph.session.document.value?.id != doc.id) window.click("打开项目")
            window.waitFor("reopen edited timeline") { graph.session.document.value?.timeline == saved.timeline }
        }
    }


}
