package com.example.prismstudio.p40b43a88.data

import com.example.prismstudio.p40b43a88.domain.usecase.ProjectBackupsUseCase
import com.example.prismstudio.p40b43a88.domain.i18n.AppLanguage
import com.example.prismstudio.p40b43a88.data.repository.SharedPrefsLanguageRepository
import com.example.prismstudio.p40b43a88.data.repository.LanguageRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProjectBackupRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidStabilizationRepository
import com.example.prismstudio.p40b43a88.data.repository.StabilizationRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidLumaFrameRepository
import com.example.prismstudio.p40b43a88.data.repository.LumaFrameRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidSoundEnvelopeRepository
import com.example.prismstudio.p40b43a88.data.repository.SoundEnvelopeRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectBackupRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidGeneratorRepository
import com.example.prismstudio.p40b43a88.data.repository.GeneratorRepository
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.data.repository.AndroidDocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidDownloadsRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidLoudnessRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidMediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidStillFrameRepository
import com.example.prismstudio.p40b43a88.data.repository.DefaultEditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.DocumentRepository
import com.example.prismstudio.p40b43a88.data.repository.DownloadsRepository
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.ExoSourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.FileGalleryRepository
import com.example.prismstudio.p40b43a88.data.repository.FileLutRepository
import com.example.prismstudio.p40b43a88.data.repository.FileReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.FileProxyRepository
import com.example.prismstudio.p40b43a88.data.repository.ProxyRepository
import com.example.prismstudio.p40b43a88.data.repository.GalleryRepository
import com.example.prismstudio.p40b43a88.data.repository.LoudnessRepository
import com.example.prismstudio.p40b43a88.data.repository.WavVoiceRecorderRepository
import com.example.prismstudio.p40b43a88.data.repository.VoiceRecorderRepository
import com.example.prismstudio.p40b43a88.data.repository.LutRepository
import com.example.prismstudio.p40b43a88.data.repository.LayoutPreferencesRepository
import com.example.prismstudio.p40b43a88.data.repository.SharedPrefsLayoutPreferencesRepository
import com.example.prismstudio.p40b43a88.data.repository.AudioPrimingRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3AudioPrimingRepository
import com.example.prismstudio.p40b43a88.data.repository.AndroidNoiseProfileRepository
import com.example.prismstudio.p40b43a88.data.repository.NoiseProfileRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.Media3RenderRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaStoreDeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.ProjectRepository
import com.example.prismstudio.p40b43a88.data.repository.RenderRepository
import com.example.prismstudio.p40b43a88.data.repository.ReverseCopyRepository
import com.example.prismstudio.p40b43a88.data.repository.SourcePlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.StillFrameRepository
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/** Process-wide composition root for repositories and players. */
@UnstableApi
class AppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val ids: IdGenerator = IdGenerator { prefix -> "$prefix-${UUID.randomUUID().toString().take(12)}" }
    val clock: () -> Long = System::currentTimeMillis
    val projects: ProjectRepository = FileProjectRepository(File(appContext.filesDir, "projects"))
    /** Resolve's Project Backups, kept apart from the projects (files/backups/<project id>/). */
    val backups: ProjectBackupRepository = FileProjectBackupRepository(File(appContext.filesDir, "backups"))
    val session: EditorSessionRepository = DefaultEditorSessionRepository(projects, appScope, clock, backups = backups, backupPolicy = ProjectBackupsUseCase(ids))
    val deviceMedia: DeviceMediaRepository = MediaStoreDeviceMediaRepository(appContext)
    val previews: MediaPreviewRepository = AndroidMediaPreviewRepository(appContext)
    val loudness: LoudnessRepository = AndroidLoudnessRepository(appContext)
    val voice: VoiceRecorderRepository by lazy { WavVoiceRecorderRepository(appContext) }
    val documents: DocumentRepository = AndroidDocumentRepository(appContext)
    val downloads: DownloadsRepository = AndroidDownloadsRepository(appContext)
    // Gallery pictures apart from freeze frames (files/stills).
    val gallery: GalleryRepository = FileGalleryRepository(File(appContext.filesDir, "gallery"))
    val luts: LutRepository = FileLutRepository(File(appContext.filesDir, "luts"), appScope)
    private val registry = LiveLookRegistry(luts)
    val stills: StillFrameRepository = AndroidStillFrameRepository(appContext, previews)
    val generators: GeneratorRepository = AndroidGeneratorRepository(appContext)
    val reverseCopies: ReverseCopyRepository = FileReverseCopyRepository(
        appContext,
        dir = File(appContext.filesDir, "render-cache"),
        scratchRoot = File(appContext.cacheDir, "reverse-scratch"),
        scope = appScope,
    )
    /** Resolve's proxy media: small copies of footage the monitors can play (files/proxies). */
    val proxies: ProxyRepository = FileProxyRepository(appContext, File(appContext.filesDir, "proxies"), appScope)
    private val audioPriming: AudioPrimingRepository = Media3AudioPrimingRepository(appContext, appScope)
    val noiseProfiles: NoiseProfileRepository = AndroidNoiseProfileRepository(appContext, appScope)
    /** Resolve's Stabilization: each video file's camera path, analysed once (cache/stabilization). */
    val stabilization: StabilizationRepository = AndroidStabilizationRepository(appContext, appScope)
    /** Resolve's window Tracker: small luma frames of a clip's stretch of its file. */
    val lumaFrames: LumaFrameRepository = AndroidLumaFrameRepository(appContext)
    /** Resolve's Auto Align Clips: each file's sound level every 5 ms. */
    val envelopes: SoundEnvelopeRepository = AndroidSoundEnvelopeRepository(appContext)
    val program: ProgramPlaybackRepository by lazy {
        Media3ProgramPlaybackRepository(
            appContext, registry, reverseCopies, loudness = loudness, priming = audioPriming, noiseProfiles = noiseProfiles,
            proxies = proxies, stabilization = stabilization,
        )
    }
    val source: SourcePlaybackRepository by lazy { ExoSourcePlaybackRepository(appContext) }
    /** The studio's layout on this headset (the ultrawide timeline), apart from any project. */
    val layout: LayoutPreferencesRepository = SharedPrefsLayoutPreferencesRepository(appContext)
    /** The interface language picked on this headset (null = the system's). */
    val language: LanguageRepository = SharedPrefsLanguageRepository(appContext)
    /** The languages offered; debuggable (QA) builds add the pseudo-locale, which shows clipped and untranslated text. */
    val languageChoices: List<AppLanguage> =
        AppLanguage.selectable + if (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) listOf(AppLanguage.PSEUDO) else emptyList()
    val render: RenderRepository = Media3RenderRepository(
        appContext, reverseCopies, loudness, luts, audioPriming, noiseProfiles, stabilization = stabilization, sourceMedia = deviceMedia,
        onSourcesChecked = { projectId, checked ->
            session.update { current -> if (current.id == projectId) current.withMediaAvailability(checked) else current }
        },
    )

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun from(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context).also { instance = it }
        }
    }
}
