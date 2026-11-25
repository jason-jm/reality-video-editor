package com.example.prismstudio.p40b43a88.data.repository

import androidx.media3.common.C
import androidx.media3.exoplayer.Renderer
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import androidx.media3.common.Effect
import com.example.prismstudio.p40b43a88.data.engine.HdrToneMapEffect
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.data.engine.HdrPreviewInput
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyMediaUseCase

import com.example.prismstudio.p40b43a88.data.engine.StillColors
import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import com.example.prismstudio.p40b43a88.R
import com.example.prismstudio.p40b43a88.data.engine.AlphaMode
import com.example.prismstudio.p40b43a88.data.engine.CompositionFactory
import com.example.prismstudio.p40b43a88.data.engine.CompositionPlayerLeaks
import androidx.media3.exoplayer.image.ExternallyLoadedImageDecoder
import com.google.common.util.concurrent.Futures
import com.example.prismstudio.p40b43a88.data.engine.FirstAudioTrackExtractorsFactory
import com.example.prismstudio.p40b43a88.data.engine.LiveLookRegistry
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.attempt
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import com.example.prismstudio.p40b43a88.domain.usecase.BuildRenderPlanUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.CompoundClipUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.DuckingUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.StabilizationUseCase
import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Only tagged HDR media takes the raw-input path. SDR clips sharing a decoder can still start and switch normally. */
@UnstableApi
private fun previewSources(context: Context): MediaSource.Factory {
    val ordinary = DefaultMediaSourceFactory(context, FirstAudioTrackExtractorsFactory())
        .setExternalImageLoader { Futures.immediateVoidFuture() }
    val hdr = DefaultMediaSourceFactory(context, FirstAudioTrackExtractorsFactory(readHdrAsSdr = true))
    return object : MediaSource.Factory by ordinary {
        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            (if (mediaItem.localConfiguration?.tag === HdrPreviewInput) hdr else ordinary).createMediaSource(mediaItem)
    }
}

/** Shared transport plumbing: player listener, position ticking and surface routing. Main thread only. */
@UnstableApi
abstract class PlayerMonitor : MonitorPlayback {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable.asStateFlow()
    protected var surface: Surface? = null
    protected var surfaceSize = Size(0, 0)
    private var ticker: Job? = null

    protected abstract val activePlayer: Player?

    protected val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutable.update { it.copy(playing = isPlaying) }
            if (isPlaying) startTicking() else publishPosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            mutable.update { it.copy(ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) }
            if (playbackState == Player.STATE_ENDED) {
                onPlaybackEnded()
                activePlayer?.playWhenReady = false
                mutable.update { it.copy(playing = false, positionMs = it.durationMs) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error", error)
            mutable.update { it.copy(playing = false, error = "预览出错：${error.errorCodeName}") }
        }
    }

    protected open fun onPlaybackEnded() {}

    protected fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }

    private fun startTicking() {
        stopTicking()
        ticker = scope.launch {
            while (isActive) {
                publishPosition()
                delay(33)
            }
        }
    }

    protected fun publishPosition() {
        val p = activePlayer ?: return
        // A container's reported duration can differ from its last decoded timestamp.
        // Keep the completed transport at the endpoint even when the final isPlaying callback arrives.
        mutable.update { state ->
            val position = if (p.playbackState == Player.STATE_ENDED) state.durationMs
                else p.currentPosition.coerceIn(0, state.durationMs.coerceAtLeast(0))
            state.copy(positionMs = position)
        }
        if (!p.isPlaying) stopTicking()
    }

    override fun play() {
        val p = activePlayer ?: return
        val s = mutable.value
        if (s.durationMs > 0 && s.positionMs >= s.durationMs) seekTo(0)
        p.play()
    }

    override fun pause() {
        activePlayer?.pause()
        publishPosition()
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0, maxOf(mutable.value.durationMs, 0))
        activePlayer?.seekTo(target)
        mutable.update { it.copy(positionMs = target) }
    }

    override fun release() {
        ticker?.cancel()
        scope.cancel()
    }

    companion object {
        const val TAG = "PrismMonitor"
    }
}

/**
 * Program monitor. Each picture layer of the plan (V2 above V1, transition handles under their track)
 * plays in its own single-input [CompositionPlayer] and renders into its own stacked view, so the
 * view system composites them exactly as export does. Media3 1.9.3's multi-input player is avoided:
 * it can wait forever on a paused seek and stall a secondary sequence at its next clip.
 *
 * View index 0 is the bottom layer; its player carries the sound and is the clock the others follow.
 */
@UnstableApi
// CompositionPlayer is Media3's experimental composition preview; opted into here, not passed on to the callers.
@OptIn(markerClass = [ExperimentalApi::class])
class Media3ProgramPlaybackRepository(
    private val context: Context,
    private val registry: LiveLookRegistry,
    private val reverseCopies: ReverseCopyRepository = ReverseCopyRepository.None,
    private val planner: BuildRenderPlanUseCase = BuildRenderPlanUseCase(),
    private val loudness: LoudnessRepository = LoudnessRepository.None,
    private val ducking: DuckingUseCase = DuckingUseCase(),
    private val priming: AudioPrimingRepository = AudioPrimingRepository.None,
    private val noiseProfiles: NoiseProfileRepository = NoiseProfileRepository.None,
    private val proxies: ProxyRepository = ProxyRepository.None,
    private val proxyRules: ProxyMediaUseCase = ProxyMediaUseCase(),
    private val stabilization: StabilizationRepository = StabilizationRepository.None,
    private val stabilizer: StabilizationUseCase = StabilizationUseCase(),
) : PlayerMonitor(), ProgramPlaybackRepository {

    private class Layer(val player: CompositionPlayer, val listener: Player.Listener) {
        var signature: List<Any?>? = null
        var pending: Composition? = null
        /** Has a composition: before that CompositionPlayer cannot seek (its internal player is still null). */
        var loaded = false

        /** Seeks a loaded player; one still waiting to load starts at the repository's position anyway. */
        fun seekTo(positionMs: Long) {
            if (loaded) player.seekTo(positionMs)
        }
    }

    private val factory = CompositionFactory(registry, priming::delayUs, noiseProfile = noiseProfiles::profile)
    private val imageLoader = StillColors.bitmapLoader(context)

    /** A decodable transparent frame for gaps in the overlay players. */
    private val fillerUri = "android.resource://${context.packageName}/${R.raw.prism_filler}"
    private val layers = mutableListOf<Layer>()
    private val surfaces = mutableMapOf<Int, Pair<Surface, Size>>()
    override val activePlayer: Player? get() = layers.firstOrNull()?.player
    private var signature: List<Any?>? = null
    /** Edit structure for which stacked layers failed; it is previewed flattened until it changes. */
    private var flattenedSignature: List<Any?>? = null
    /** Edit structure whose preview was already restarted on fresh players after a failure. */
    private var restartedSignature: List<Any?>? = null
    private var recoverJob: Job? = null
    /** A press during the edit debounce, buffering or player replacement still belongs to this timeline. */
    private var playRequested = false
    private var lastDocument: ProjectDocument? = null
    private var lastPreview: Pair<Int, Int>? = null
    private val prepareQueue = ArrayDeque<Int>()
    private var generation = 0
    private var rebuildJob: Job? = null
    private var syncJob: Job? = null
    /** The document last handed to [setProject], re-evaluated when a reversed copy becomes ready. */
    private var current: ProjectDocument? = null
    /** Reversed copies (by asset id) the current build plays. */
    private var reversedUris: Map<String, String> = emptyMap()
    /** What the registry's ducking envelopes were worked out from (see [DuckingUseCase.inputs]). */
    private var duckInputs: List<Any?>? = null
    private var duckJob: Job? = null

    init {
        scope.launch {
            reverseCopies.copies.map { it.readyUris() }.distinctUntilChanged().collect { current?.let(::setProject) }
        }
        // A file's encoder delay, once read, moves the sound of clips that start inside it.
        scope.launch {
            priming.delays.collect { current?.let(::setProject) }
        }
        // A noise floor, once measured, lets that file's clips on a noise-reduced lane be cleaned.
        scope.launch {
            noiseProfiles.profiles.collect { current?.let(::setProject) }
        }
        // A camera path, once analysed, steadies that file's stabilized clips (a live look change, no rebuild).
        scope.launch {
            stabilization.analyses.map { a -> a.filterValues { it is CameraAnalysis.Ready }.keys }.distinctUntilChanged()
                .collect { current?.let(::setProject) }
        }
        // A proxy made (or deleted) while the project prefers proxies changes what the monitor plays.
        scope.launch {
            proxies.proxies.collect { current?.takeIf { it.useProxies }?.let(::setProject) }
        }
    }

    // Compound clips play what they hold: everything below sees their contents in their place.
    override fun setProject(document: ProjectDocument?) = show(document?.let(CompoundClipUseCase::expanded))

    private fun show(document: ProjectDocument?) {
        val changedProject = current?.id != document?.id
        current = document
        if (changedProject || document == null || document.durationFrames == 0) {
            rebuildJob?.cancel()
            recoverJob?.cancel()
            playRequested = false
            generation++
            signature = null
            restartedSignature = null
            flattenedSignature = null
            lastDocument = null
            lastPreview = null
            releaseLayers()
            mutable.value = PlaybackState()
        }
        if (document == null) return
        // Stabilized clips follow their files' camera paths: the missing ones are analysed in the background.
        stabilization.request(stabilizer.wanted(document))
        registry.setStabilization(stabilization.paths())
        val lookChanged = registry.update(document)
        updateDucks(document)
        val plan = planner(document)
        val preview = CompositionFactory.previewSize(document.format.width, document.format.height)
        val reversedIds = plan.reversedAssetIds()
        if (reversedIds.isNotEmpty()) reverseCopies.request(document.media.filter { it.id in reversedIds })
        val ready = reverseCopies.copies.value.readyUris().filterKeys { it in reversedIds }
        priming.request(document.media)
        proxies.refresh(proxyRules.candidates(document))
        if (plan.isEmpty) return
        val proxied = proxyRules.previewUris(document, proxies.proxies.value)
        // HDR by the input colour override is tone mapped in the monitor too: a change of it, or of the method, rebuilds.
        val toneMapped = toneMappedMedia(document)
        val newSignature = plan.structureSignature() + listOf(preview, document.media.map { it.id to it.uri }, ready, delays(document), denoising(document, plan), proxied, toneMapped)
        if (newSignature != signature) {
            signature = newSignature
            reversedUris = ready
            rebuildJob?.cancel()
            rebuildJob = scope.launch {
                delay(REBUILD_DEBOUNCE_MS)
                rebuild(document, preview)
            }
        } else {
            if (lookChanged) redraw()
            // A new composite mode needs no player: the monitor's overlay views just blend differently.
            val state = mutable.value
            val composites = previewPlan(plan).first.layerComposites(state.layers, flattened = false)
            if (composites != state.layerComposites) mutable.update { it.copy(layerComposites = composites) }
        }
    }


    /** The encoder delays read so far of [document]'s files. */
    /** Changing an HDR input's interpretation or the timeline's tone map rebuilds its preview. */
    private fun toneMappedMedia(document: ProjectDocument) =
        document.media.filter { it.effectiveTransfer != ColorTransfer.SDR }.associate { it.id to it.effectiveTransfer }
            .takeIf { it.isNotEmpty() }?.let { it to document.colorManagement }

    private fun delays(document: ProjectDocument): List<Long> = document.media.map { priming.delayUs(it) }

    /**
     * The lanes whose noise reduction is on, with the files on them measured so far: switching it on or off, or a floor
     * arriving, rebuilds the sound (its settings apply live). The floors still missing are asked for.
     */
    private fun denoising(document: ProjectDocument, plan: RenderPlan): List<Any> {
        val lanes = document.mixer.channels.filter { it.noiseReduction.isActive }.map { it.trackId }.toSet()
        if (lanes.isEmpty()) return emptyList()
        val ids = plan.audio.filter { it.trackId in lanes }.flatMap { lane -> lane.segments.mapNotNull { it.assetId } }.toSet()
        val assets = document.media.filter { it.id in ids }
        noiseProfiles.request(assets)
        return listOf(lanes.sorted(), assets.filter { noiseProfiles.profile(it) != null }.map { it.id }.sorted())
    }

    override fun refreshLooks() {
        val document = current ?: return
        if (registry.update(document)) redraw()
    }

    /**
     * Works out the ducking envelopes again when what they depend on changed — a moved dialogue clip, a new
     * threshold — in the background (the first look at a file measures its loudness); the depth applies
     * at once through the registry's lane mix.
     */
    private fun updateDucks(document: ProjectDocument) {
        val inputs = ducking.inputs(document, ducking.keyAssetIds(document))
        if (inputs == duckInputs) return
        duckInputs = inputs
        duckJob?.cancel()
        duckJob = scope.launch {
            val envelopes = attempt { loudness.duckEnvelopes(document, ducking) }
                .onFailure { Log.w(TAG, "Cannot work out the ducking", it) }
                .getOrNull() ?: return@launch
            registry.setDucks(envelopes)
            if (envelopes.isNotEmpty()) Log.i(TAG, "Ducking ${envelopes.keys}: down ${envelopes.values.sumOf { e -> e.amounts.count { it >= 1f } } * 10} ms")
        }
    }

    /**
     * The picture the monitor stacks for [full], and whether it differs from a render: stacking failed for this edit (one
     * flattened picture), or more layers than the decoders take had to share players ([RenderPlan.grouped]; layers that
     * never overlap share one exactly).
     */
    private fun previewPlan(full: RenderPlan): Pair<RenderPlan, Boolean> {
        if (full.isLayered && flattenedSignature == signature) return full.flattened() to true
        val (grouped, hidden) = full.grouped(MAX_PREVIEW_LAYERS)
        return grouped to (hidden > 0)
    }

    private fun rebuild(document: ProjectDocument, preview: Pair<Int, Int>) {
        lastDocument = document
        lastPreview = preview
        val full = planner(document)
        val (plan, approximate) = previewPlan(full)
        if (plan.layers.size < full.layers.size) Log.w(TAG, "Preview stacks ${full.layers.size} layers in ${plan.layers.size}${if (approximate) ", approximate" else ", exactly"}")
        val durationMs = plan.durationFrames * 1000L / plan.fps
        val count = plan.layers.size.coerceAtLeast(1)
        val position = mutable.value.positionMs.coerceIn(0, (durationMs - 1).coerceAtLeast(0))
        if (playRequested) layers.forEach { it.player.pause() }
        resize(count)
        generation++
        prepareQueue.clear()
        val assets = document.media.associateBy { it.id }
        val noise = denoising(document, full)
        val proxied = proxyRules.previewUris(document, proxies.proxies.value)
        val toneMapped = toneMappedMedia(document)
        if (proxied.isNotEmpty()) Log.w(TAG, "Preview plays ${proxied.size} proxies")
        for (view in 0 until count) {
            // The plan lists layers top first; the views stack bottom first.
            val layerPlan = if (plan.layers.isEmpty()) plan else plan.forLayer(count - 1 - view)
            val alpha = if (view == 0) AlphaMode.OPAQUE else AlphaMode.PREMULTIPLIED
            val layer = layers[view]
            // Only the bottom layer plays the sound, so only it follows the encoder delays and the noise reduction.
            val layerSignature = layerPlan.structureSignature() +
                listOf(preview, alpha, document.media.map { it.id to it.uri }, reversedUris, if (view == 0) delays(document) else null, if (view == 0) noise else null, proxied, toneMapped)
            if (layerSignature == layer.signature) {
                layer.seekTo(position)
                // Still waiting to load from an earlier rebuild (whose queue was just cleared).
                if (layer.pending != null) prepareQueue += view
                continue
            }
            layer.signature = layerSignature
            layer.pending = runCatching {
                val output = CompositionFactory.Output(
                    preview.first, preview.second, alphaMode = alpha, fillerUri = if (view > 0) fillerUri else null,
                    colorManagement = document.colorManagement,
                )
                factory.build(layerPlan, assets, output, reversedUris, proxied)
            }.onFailure { Log.w(TAG, "Cannot build layer $view", it) }.getOrNull()
            if (layer.pending == null) layer.signature = null else prepareQueue += view
        }
        // Resolve's Composite Mode: each overlay view blends with the views beneath while its clip plays.
        val composites = plan.layerComposites(count, flattened = false)
        mutable.update {
            it.copy(durationMs = durationMs, positionMs = position, error = null, layers = count, approximate = approximate, layerComposites = composites)
        }
        prepareNext()
        if (playRequested) play()
    }

    /**
     * Prepares the next queued layer. Layers start one at a time (each once the previous is ready, or
     * after a grace period): starting several GL pipelines at once can overrun Media3's 500 ms
     * blocking-call timeout on slow GPUs.
     */
    private fun prepareNext() {
        val view = prepareQueue.removeFirstOrNull() ?: return
        val layer = layers.getOrNull(view) ?: return prepareNext()
        val composition = layer.pending ?: return prepareNext()
        layer.pending = null
        val pass = generation
        try {
            CompositionPlayerLeaks.setComposition(layer.player, composition, mutable.value.positionMs)
            layer.loaded = true
            // A failure reported from inside setComposition has already scheduled a recovery.
            if (pass != generation) return
            layer.player.prepare()
            layer.player.playWhenReady = playRequested
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load layer $view", e)
            if (!recover()) mutable.update { it.copy(error = "预览出错：${e.message ?: e.javaClass.simpleName}") }
            return
        }
        if (prepareQueue.isNotEmpty()) {
            scope.launch {
                delay(PREPARE_GRACE_MS)
                if (pass == generation) prepareNext()
            }
        }
    }

    private fun onLayerReady() {
        if (prepareQueue.isNotEmpty()) prepareNext()
    }

    /**
     * A layer player failed — on slow GPUs typically a player release or GL call that overran Media3's
     * timeouts, which can be reported from inside `setComposition`. Recovery therefore runs once the
     * failing call has unwound, and always on fresh players: first the same preview again, then (for a
     * layered edit) one flattened picture. Returns false when nothing is left to try for this edit.
     */
    private fun recover(): Boolean {
        if (recoverJob?.isActive == true) return true
        val document = lastDocument ?: return false
        val preview = lastPreview ?: return false
        when {
            restartedSignature != signature -> {
                restartedSignature = signature
                Log.w(TAG, "Preview failed; restarting its players")
            }
            layers.size > 1 && flattenedSignature != signature -> {
                flattenedSignature = signature
                Log.w(TAG, "Layered preview failed again; showing a flattened picture")
            }
            else -> return false
        }
        generation++
        prepareQueue.clear()
        // Posted (not Main.immediate): the failing player call must return before its player is released.
        recoverJob = scope.launch(Dispatchers.Main) {
            releaseLayers()
            rebuild(document, preview)
        }
        return true
    }

    /** Grows or shrinks the player stack to [count] layers, keeping the players that stay. */
    private fun resize(count: Int) {
        while (layers.size > count) {
            val removed = layers.removeAt(layers.lastIndex)
            removed.player.removeListener(removed.listener)
            CompositionPlayerLeaks.release(removed.player)
        }
        while (layers.size < count) {
            val view = layers.size
            // The replayable cache lets a paused frame be re-rendered when a grade or effect changes.
            // Stills show with their own values, as renders give them (StillColors).
            val player = CompositionPlayer.Builder(context).experimentalSetEnableReplayableCache(true)
                .setMediaSourceFactory(previewSources(context))
                .setImageDecoderFactory(ExternallyLoadedImageDecoder.Factory { request -> imageLoader.loadBitmap(request.uri) })
                .setVideoGraphFactory(StillColors.previewGraph()).build()
            if (monitorMuted) player.volume = 0f
            val layerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) onLayerReady()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Layer $view playback error", error)
                    // The bottom layer also reports through the shared listener; the restart clears it.
                    recover()
                }
            }
            player.addListener(layerListener)
            if (view == 0) player.addListener(listener)
            surfaces[view]?.let { (surface, size) -> player.setVideoSurface(surface, size) }
            layers += Layer(player, layerListener)
        }
    }

    private fun releaseLayers() {
        syncJob?.cancel()
        stopTicking()
        prepareQueue.clear()
        layers.forEachIndexed { view, layer ->
            layer.player.removeListener(layer.listener)
            if (view == 0) layer.player.removeListener(listener)
            CompositionPlayerLeaks.release(layer.player)
        }
        layers.clear()
    }

    override fun play() {
        val duration = timelineDurationMs()
        if (duration == 0L) return
        playRequested = true
        val s = mutable.value
        if (s.positionMs >= duration) seekTo(0)
        layers.filter { it.loaded }.forEach { it.player.play() }
        startSync()
    }

    override fun pause() {
        playRequested = false
        layers.forEach { it.player.pause() }
        publishPosition()
        // Stop on exactly the same frame in every layer.
        val at = activePlayer?.currentPosition ?: return
        layers.drop(1).forEach { it.seekTo(at) }
    }

    override fun onPlaybackEnded() { playRequested = false }

    private fun timelineDurationMs(): Long = current?.let { it.durationFrames * 1000L / it.format.fps } ?: 0

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0, timelineDurationMs())
        // A drop can seek into the newly inserted clip before the longer timeline has finished preparing.
        if (target <= mutable.value.durationMs) layers.forEach { it.seekTo(target) }
        mutable.update { it.copy(positionMs = target) }
    }

    /**
     * While playing, re-aligns an overlay player that fell behind or ran ahead of the bottom (clock)
     * player — e.g. after it re-buffered. Late frames are normally dropped by each player, so this only
     * acts on real drift, and gives each correction time to settle instead of seeking again and again.
     */
    private fun startSync() {
        syncJob?.cancel()
        if (layers.size < 2) return
        val lastCorrection = LongArray(layers.size)
        syncJob = scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                val master = activePlayer ?: break
                if (!master.isPlaying) continue
                val at = master.currentPosition
                val now = android.os.SystemClock.elapsedRealtime()
                layers.forEachIndexed { view, layer ->
                    if (view == 0 || view >= lastCorrection.size) return@forEachIndexed
                    val p = layer.player
                    val drift = p.currentPosition - at
                    val settled = now - lastCorrection[view] >= SYNC_COOLDOWN_MS
                    if (kotlin.math.abs(drift) > MAX_DRIFT_MS && p.playbackState == Player.STATE_READY && settled) {
                        lastCorrection[view] = now
                        p.seekTo(at + SYNC_LEAD_MS)
                    }
                }
            }
        }
    }

    private fun redraw() {
        layers.forEach { layer ->
            val p = layer.player
            if (p.isPlaying) return@forEach
            runCatching { p.experimentalRedrawLastFrame() }.onFailure {
                Log.w(TAG, "Cannot redraw paused frame; seeking instead", it)
                layer.seekTo(p.currentPosition)
            }
        }
    }

    override fun setCompare(clipId: String?, split: Float) {
        if (registry.compareClipId == clipId && registry.compareSplit == split) return
        registry.compareClipId = clipId
        registry.compareSplit = split
        redraw()
    }

    override fun setKeyMatte(clipId: String?) {
        if (registry.keyMatteClipId == clipId) return
        registry.keyMatteClipId = clipId
        redraw()
    }

    override fun setHighlight(clipId: String?) {
        if (registry.highlightClipId == clipId) return
        registry.highlightClipId = clipId
        redraw()
    }

    /** The monitor's sound is off (see [setMonitorMuted]); new layer players start that way too. */
    private var monitorMuted = false

    override fun setMonitorMuted(muted: Boolean) {
        monitorMuted = muted
        layers.forEach { it.player.volume = if (muted) 0f else 1f }
    }

    override fun attachSurface(surface: Surface, width: Int, height: Int, layer: Int) {
        val size = Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        surfaces[layer] = surface to size
        layers.getOrNull(layer)?.player?.setVideoSurface(surface, size)
    }

    override fun detachSurface(surface: Surface, layer: Int) {
        if (surfaces[layer]?.first !== surface) return
        surfaces.remove(layer)
        // CompositionPlayer only clears the exact surface it was given.
        layers.getOrNull(layer)?.player?.let { p -> runCatching { p.clearVideoSurface(surface) } }
    }

    override fun release() {
        rebuildJob?.cancel()
        recoverJob?.cancel()
        duckJob?.cancel()
        releaseLayers()
        super.release()
    }

    private companion object {
        const val REBUILD_DEBOUNCE_MS = 120L
        const val MAX_PREVIEW_LAYERS = 4
        const val PREPARE_GRACE_MS = 3_000L
        const val SYNC_INTERVAL_MS = 1_000L
        const val MAX_DRIFT_MS = 200L
        const val SYNC_LEAD_MS = 100L
        const val SYNC_COOLDOWN_MS = 3_000L
    }
}

/** Source monitor backed by an [ExoPlayer]; stills are shown by the view, not played. */
@UnstableApi
class ExoSourcePlaybackRepository(private val context: Context) : PlayerMonitor(), SourcePlaybackRepository {

    private var player: ExoPlayer? = null
    override val activePlayer: Player? get() = player
    private var loadedUri: String? = null

    /**
     * The player, with [effects] (the tone map, or none): a player prepared without effects does not take them later, so
     * a change of them builds a new one, the effects set before its surface and its media.
     */
    private fun ensurePlayer(effects: List<Effect>): ExoPlayer {
        player?.let { if (effects == playerEffects) return it }
        player?.release()
        return ExoPlayer.Builder(context, previewSources(context)).build().also {
            it.addListener(listener)
            it.setSeekParameters(SeekParameters.EXACT)
            if (effects.isNotEmpty()) it.setVideoEffects(effects)
            surface?.let { s -> it.setVideoSurface(s) }
            player = it
            playerEffects = effects
            sendOutputSize(it)
        }
    }

    /**
     * With effects the player draws through its own GL stage, which needs the surface's size: a bare [Surface] does not
     * tell it ("Output surface and size not set, dropping frame"), so the video renderer is told directly.
     */
    private fun sendOutputSize(p: ExoPlayer) {
        if (playerEffects.isEmpty() || surface == null || surfaceSize.width <= 0) return
        for (i in 0 until p.rendererCount) {
            if (p.getRendererType(i) != C.TRACK_TYPE_VIDEO) continue
            p.createMessage(p.getRenderer(i)).setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION).setPayload(surfaceSize).send()
        }
    }

    /** The effects the player was built with. */
    private var playerEffects: List<Effect> = emptyList()

    override fun load(asset: MediaAsset?, color: ColorManagement) {
        if (asset == null || asset.kind == MediaKind.IMAGE || asset.offline) {
            player?.stop()
            player?.clearMediaItems()
            loadedUri = null
            mutable.value = PlaybackState(durationMs = asset?.durationMs ?: 0, ready = asset != null && !asset.offline)
            return
        }
        val toneMap = if (CompositionFactory.toneMaps(asset, color)) Triple(asset.effectiveTransfer, color.toneMapping, color.pqPeakNits) else null
        val key = "${asset.uri}|${asset.color.isHdr}|$toneMap"
        if (key == loadedUri) return
        loadedUri = key
        if (toneMap != null) Log.w(TAG, "Source monitor tone maps ${asset.name} as ${toneMap.first}")
        val p = ensurePlayer(toneMapEffects(toneMap))
        mutable.value = PlaybackState(durationMs = asset.durationMs)
        p.setMediaItem(MediaItem.Builder().setUri(asset.uri).apply { if (asset.color.isHdr) setTag(HdrPreviewInput) }.build())
        p.prepare()
    }

    /** The same effect list for the same tone map (so an unchanged one keeps the player). */
    private var lastToneMap: Triple<ColorTransfer, ToneMapping, Int>? = null
    private var lastEffects: List<Effect> = emptyList()

    private fun toneMapEffects(toneMap: Triple<ColorTransfer, ToneMapping, Int>?): List<Effect> {
        if (toneMap == lastToneMap) return lastEffects
        lastToneMap = toneMap
        lastEffects = toneMap?.let { (t, m, peak) -> listOf<Effect>(HdrToneMapEffect(t, m, peak)) } ?: emptyList()
        return lastEffects
    }

    override fun attachSurface(surface: Surface, width: Int, height: Int, layer: Int) {
        this.surface = surface
        surfaceSize = Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
        player?.setVideoSurface(surface)
        player?.let(::sendOutputSize)
    }

    override fun detachSurface(surface: Surface, layer: Int) {
        if (this.surface !== surface) return
        this.surface = null
        player?.clearVideoSurface(surface)
    }

    override fun release() {
        player?.release()
        player = null
        loadedUri = null
        super.release()
    }
}
