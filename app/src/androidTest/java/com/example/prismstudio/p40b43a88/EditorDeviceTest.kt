package com.example.prismstudio.p40b43a88

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.example.prismstudio.p40b43a88.data.repository.MediaStoreDeviceMediaRepository
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.domain.model.*
import com.example.prismstudio.p40b43a88.domain.usecase.AppendMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CreateProjectUseCase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After

/** Every fixture is owned by the test package and removed by its creating test. */
@androidx.annotation.OptIn(UnstableApi::class)
abstract class EditorDeviceTest {
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val app = instrumentation.targetContext
    protected val resolver = app.contentResolver
    protected val imported = mutableListOf<Uri>()
    protected val ids = IdGenerator { prefix -> "$prefix-${UUID.randomUUID()}" }
    protected val append = AppendMediaUseCase(ids)
    protected val library = MediaStoreDeviceMediaRepository(app)

    protected fun importMedia(name: String, mime: String, write: (java.io.OutputStream) -> Unit): MediaAsset {
        val collection = when (mime.substringBefore('/')) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "rve-qa-${UUID.randomUUID()}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values))
        imported += uri
        requireNotNull(resolver.openOutputStream(uri)).use(write)
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return runBlocking { library.probe(requireNotNull(library.describe(uri.toString())), ids.next("m")) }
    }

    protected fun sample(name: String, mime: String = "video/mp4") = importMedia(name, mime) { out ->
        instrumentation.context.assets.open("playback/$name").use { it.copyTo(out) }
    }

    protected fun project(vararg assets: MediaAsset): ProjectDocument {
        val doc = CreateProjectUseCase(ids, System::currentTimeMillis)("Playback QA", FormatPreset.HD_LANDSCAPE, 30)
        return doc.copy(media = assets.toList(), timeline = assets.fold(doc.timeline) { tl, asset ->
            append(tl, asset, 0, asset.durationMs, doc.format.fps).timeline
        })
    }

    protected fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    internal suspend fun withOpenEditor(doc: ProjectDocument, block: suspend (EditorWindow, AppGraph) -> Unit) {
        val graph = AppGraph.from(app)
        val language = graph.language.language.value
        try {
            graph.language.setLanguage(AppLanguage.ZH_HANS)
            graph.projects.save(doc)
            EditorWindow().use { window ->
                app.startActivity(requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                window.click(doc.name, prefix = true)
                SystemClock.sleep(500)
                if (graph.session.document.value?.id != doc.id) window.click("打开项目")
                window.waitFor("open QA project") { graph.session.document.value?.id == doc.id }
                block(window, graph)
            }
        } finally {
            if (graph.session.document.value?.id == doc.id) graph.session.close()
            graph.projects.delete(doc.id)
            graph.backups.deleteAll(doc.id)
            app.filesDir.resolve("gallery").resolve(doc.id).deleteRecursively()
            graph.language.setLanguage(language)
        }
    }

    @After fun removeOnlyOurFiles() { imported.forEach { resolver.delete(it, null, null) } }
}
