package com.example.prismstudio.p40b43a88.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.FakeProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectRepository
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Track
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue

/** Ids "<prefix>-1", "<prefix>-2", … counted per prefix, so tests can predict every new id. */
class SequentialIds : IdGenerator {
    private val counters = mutableMapOf<String, Int>()

    override fun next(prefix: String): String {
        val n = (counters[prefix] ?: 0) + 1
        counters[prefix] = n
        return "$prefix-$n"
    }
}

/** A clock the test moves by hand (session timestamps, coalescing window, relative labels). */
class TestClock(var now: Long = UiFixtures.NOW) : () -> Long {
    override fun invoke(): Long = now
}

/** Small builders for project documents used by the ViewModel tests. */
object UiFixtures {
    /** 2026-10-04 09:30 UTC; tests format labels in UTC so the expectations do not depend on the host zone. */
    val NOW: Long = Instant.parse("2026-10-04T09:30:00Z").toEpochMilli()

    fun video(
        id: String,
        name: String = "$id.mp4",
        durationMs: Long = 10_000,
        hasAudio: Boolean = true,
        offline: Boolean = false,
        sizeBytes: Long = 1L shl 20,
    ) = MediaAsset(
        id = id,
        uri = "content://media/$id",
        name = name,
        kind = MediaKind.VIDEO,
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        frameRate = 30f,
        videoCodec = "H.264",
        audioDescription = if (hasAudio) "AAC 48 kHz 立体声" else "无音频",
        hasAudio = hasAudio,
        sizeBytes = sizeBytes,
        relativePath = "Movies/",
        offline = offline,
    )

    fun audio(id: String, name: String = "$id.m4a", durationMs: Long = 8_000) = MediaAsset(
        id = id,
        uri = "content://media/$id",
        name = name,
        kind = MediaKind.AUDIO,
        durationMs = durationMs,
        width = 0,
        height = 0,
        frameRate = 0f,
        videoCodec = "—",
        audioDescription = "AAC 48 kHz 立体声",
        hasAudio = true,
        sizeBytes = 512L * 1024,
        relativePath = "Music/",
    )

    fun image(id: String, name: String = "$id.jpg") = MediaAsset(
        id = id,
        uri = "content://media/$id",
        name = name,
        kind = MediaKind.IMAGE,
        durationMs = MediaAsset.IMAGE_DEFAULT_DURATION_MS,
        width = 4000,
        height = 3000,
        frameRate = 0f,
        videoCodec = "—",
        audioDescription = "无音频",
        hasAudio = false,
        sizeBytes = 2L shl 20,
        relativePath = "Pictures/",
    )

    /** A clip; its kind follows the track (A1 / A2 are audio) unless given. */
    fun clip(
        id: String,
        start: Int,
        frames: Int,
        trackId: String = Timeline.V1,
        assetId: String? = null,
        linkId: String? = null,
        kind: ClipKind = if (trackId == Timeline.A1 || trackId == Timeline.A2) ClipKind.AUDIO else ClipKind.VIDEO,
        name: String = id,
    ) = Clip(
        id = id,
        trackId = trackId,
        kind = kind,
        name = name,
        start = start,
        durationFrames = frames,
        assetId = assetId,
        linkId = linkId,
    )

    fun document(
        id: String = "p-test",
        name: String = "Test Project",
        fps: Int = 30,
        width: Int = 1920,
        height: Int = 1080,
        media: List<MediaAsset> = emptyList(),
        clips: List<Clip> = emptyList(),
        markers: List<Marker> = emptyList(),
        tracks: List<Track> = Timeline.defaultTracks(),
        mixer: MixerState = MixerState.forTracks(tracks),
        render: RenderSettings = RenderSettings(fileName = name.replace(' ', '_'), fps = fps),
        jobs: List<RenderJob> = emptyList(),
        modifiedAt: Long = NOW,
    ) = ProjectDocument(
        id = id,
        name = name,
        format = ProjectFormat(width, height, fps),
        createdAtMillis = modifiedAt,
        modifiedAtMillis = modifiedAt,
        media = media,
        timeline = Timeline(tracks, clips, markers),
        mixer = mixer,
        render = render,
        jobs = jobs,
    )
}

/**
 * Opens [doc] in a real [DefaultEditorSessionRepository] whose autosave runs on the test's
 * background scope (virtual time), like the app's session on its application scope.
 */
suspend fun TestScope.openSession(
    doc: ProjectDocument,
    projects: ProjectRepository = FakeProjectRepository(listOf(doc)),
    clock: () -> Long = TestClock(),
): DefaultEditorSessionRepository {
    val session = DefaultEditorSessionRepository(projects, backgroundScope, clock)
    assertTrue("project ${doc.id} should open", session.open(doc.id))
    return session
}

/** Clears [viewModel] the way its ViewModelStore owner does: closes viewModelScope, then onCleared(). */
fun clearViewModel(viewModel: ViewModel) {
    val store = ViewModelStore()
    store.put("under-test", viewModel)
    store.clear()
}
