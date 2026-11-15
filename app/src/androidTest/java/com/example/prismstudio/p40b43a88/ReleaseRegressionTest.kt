package com.example.prismstudio.p40b43a88

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3RenderRepository
import com.example.prismstudio.p40b43a88.data.repository.RenderOutput
import com.example.prismstudio.p40b43a88.domain.model.*
import com.example.prismstudio.p40b43a88.domain.usecase.AddTitleUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CreateProjectUseCase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ReleaseRegressionTest {
    private val app: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val folder = TemporaryFolder(app.cacheDir)
    private val ids = IdGenerator { prefix -> "$prefix-${UUID.randomUUID()}" }

    private fun project() = CreateProjectUseCase(ids, System::currentTimeMillis)("Release check", FormatPreset.HD_LANDSCAPE, 30)

    @Test
    fun closingWithUnwritableStorageRetainsTitleUntilRetry() = runBlocking {
        val dir = folder.newFolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val repo = FileProjectRepository(dir)
        val session = DefaultEditorSessionRepository(repo, scope, autosaveDelayMs = 60_000)
        val doc = project()
        try {
            session.start(doc)
            session.edit("Add title") { it.copy(timeline = AddTitleUseCase(ids)(it.timeline, TitleTemplate.CENTER, 0, 30).timeline) }
            Os.chmod(dir.path, OsConstants.S_IRUSR or OsConstants.S_IXUSR)
            assertFalse(session.close())
            assertTrue(session.saveState.value.failed)
            assertTrue(session.history.value.canUndo)
            assertEquals(1, session.document.value!!.timeline.clips.size)
            assertEquals(0, repo.load(doc.id)!!.timeline.clips.size)
            Os.chmod(dir.path, OsConstants.S_IRWXU)
            assertTrue(session.close())
            assertTrue(session.open(doc.id))
            assertEquals(1, session.document.value!!.timeline.clips.size)
        } finally {
            Os.chmod(dir.path, OsConstants.S_IRWXU)
            scope.cancel()
        }
    }

    private fun layeredProject(): ProjectDocument {
        val image = folder.newFile("source.png")
        val picture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        picture.eraseColor(Color.rgb(45, 170, 75))
        image.outputStream().use { check(picture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        picture.recycle()
        val asset = MediaAsset("image", Uri.fromFile(image).toString(), image.name, MediaKind.IMAGE, 0, 64, 64, 0f, "PNG", "", false, image.length(), "")
        val clip = Clip("c1", Timeline.V1, ClipKind.VIDEO, "Picture", 0, 60, asset.id)
        val doc = project()
        val layers = doc.timeline.copy(clips = listOf(clip, clip.copy(id = "c2", trackId = Timeline.V2)))
        return doc.copy(media = listOf(asset), timeline = AddTitleUseCase(ids)(layers, TitleTemplate.SUBTITLE, 0, 30).timeline)
    }

    @Test(timeout = 180_000)
    fun overlappingRequestsPublishDecodableStillMovieAndSubtitles() = runBlocking {
        val doc = layeredProject()
        // A user-supplied name matching the old intermediate filename must work, too.
        val settings = doc.render.copy(fileName = "stack-0", resolution = RenderResolution.HD_720, includeAudio = false, exportSubtitles = true)
        val engine = Media3RenderRepository(app)
        val created = mutableListOf<String>()
        try {
            lateinit var still: RenderOutput
            val output = coroutineScope {
                launch {
                    still = engine.renderStill(doc, settings, 5)
                    created += still.uri
                }
                engine.render(doc, settings, null, null) {}.also {
                    created += it.uri
                    created += it.subtitleUris
                }
            }
            for ((uri, parent) in listOf(still.uri to "Pictures", output.uri to "Movies", output.subtitleUris.single() to "Download")) {
                val path = app.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use {
                    check(it.moveToFirst())
                    it.getString(0).trimEnd('/')
                }
                assertEquals("$parent/Reality Video Editor", path)
            }
            val frame = app.contentResolver.openInputStream(Uri.parse(still.uri))!!.use { BitmapFactory.decodeStream(it) }
            val pixel = frame.getPixel(frame.width / 2, frame.height / 3)
            assertTrue(Color.green(pixel) > Color.red(pixel) + 30)
            frame.recycle()
            assertTrue(output.missingSubtitles.isEmpty())
            val text = app.contentResolver.openInputStream(Uri.parse(output.subtitleUris.single()))!!.bufferedReader().use { it.readText() }
            assertTrue(text.contains("00:00:00,000 --> 00:00:02,000"))
            MediaMetadataRetriever().use {
                it.setDataSource(app, Uri.parse(output.uri))
                assertEquals("720", it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertTrue(it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 1900)
            }
        } finally {
            created.forEach { app.contentResolver.delete(Uri.parse(it), null, null) }
        }
    }

    @Test(timeout = 180_000)
    fun missingPictureIsReportedAndRestoringTheSameSourceRecoversItsActualPixels() = runBlocking {
        val doc = layeredProject()
        val source = File(Uri.parse(doc.media.single().uri).path!!)
        val moved = File(source.parentFile, "moved.png")
        val tag = "offline-check-${UUID.randomUUID()}"
        val settings = doc.render.copy(fileName = tag, resolution = RenderResolution.HD_720, includeAudio = false, exportSubtitles = true)
        val engine = Media3RenderRepository(app)
        val files = mutableListOf<String>()
        assertTrue(source.renameTo(moved))
        try {
            val movie = engine.render(doc, settings, null, null) {}
            files += movie.uri; files += movie.subtitleUris
            assertEquals(listOf(source.name), movie.offlineMedia)
            assertEquals(1, movie.subtitleUris.size)
            val still = engine.renderStill(doc, settings.copy(fileName = "$tag-frame"), 20)
            files += still.uri
            assertEquals(listOf(source.name), still.offlineMedia)
            fun center(uri: String): Int = app.contentResolver.openInputStream(Uri.parse(uri))!!.use { stream ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2).also { bitmap.recycle() }
            }
            assertTrue("missing picture renders black", Color.green(center(still.uri)) < 15)
            assertTrue(moved.renameTo(source))
            val markedOffline = doc.copy(media = doc.media.map { it.copy(offline = true) })
            val recovered = engine.renderStill(markedOffline, settings.copy(fileName = "$tag-recovered"), 20)
            files += recovered.uri
            assertTrue(recovered.offlineMedia.isEmpty())
            assertTrue("restored source is used on retry", Color.green(center(recovered.uri)) > 105)
            assertTrue("the earlier file is still accessible", Color.green(center(still.uri)) < 15)
        } finally {
            files.forEach { app.contentResolver.delete(Uri.parse(it), null, null) }
        }
    }

    @Test(timeout = 180_000)
    fun deniedSubtitleWritePreservesPublishedMovieAndNamesMissingFile() = runBlocking {
        val doc = layeredProject()
        val name = "subtitle-check-${UUID.randomUUID()}"
        val settings = doc.render.copy(fileName = name, resolution = RenderResolution.HD_720, includeAudio = false, exportSubtitles = true)
        // Once the primary movie is visible, simulate a provider that denies the remaining writes.
        val context = object : ContextWrapper(app) {
            override fun getContentResolver(): ContentResolver {
                val resolver = super.getContentResolver()
                val movies = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
                val visible = resolver.query(movies, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf("$name.mp4"), null)
                    ?.use { it.moveToFirst() } == true
                if (visible) throw SecurityException("Subtitle destination unavailable")
                return resolver
            }
        }
        val output = Media3RenderRepository(context).render(doc, settings, null, null) {}
        try {
            assertEquals(listOf("$name.srt"), output.missingSubtitles)
            assertTrue(output.subtitleUris.isEmpty())
            app.contentResolver.openFileDescriptor(Uri.parse(output.uri), "r")!!.use { assertTrue(it.statSize > 0) }
        } finally {
            (output.subtitleUris + output.uri).forEach { app.contentResolver.delete(Uri.parse(it), null, null) }
        }
    }
}
