package com.example.prismstudio.p40b43a88.data.engine

import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import android.graphics.Matrix
import androidx.media3.common.C
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.prismstudio.p40b43a88.domain.model.AudioLane
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.Framing
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.NoiseProfile
import com.example.prismstudio.p40b43a88.domain.model.RenderPlan
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ReverseMapping
import com.example.prismstudio.p40b43a88.domain.model.SOURCE_EDGE_MS
import com.example.prismstudio.p40b43a88.domain.model.VideoLayer
import com.example.prismstudio.p40b43a88.domain.model.VideoSegment
import kotlin.math.ceil

/** A source whose tagged HDR signal is tone mapped by the clip shader in the preview. */
internal object HdrPreviewInput

/** Maps a [RenderPlan] onto a Media3 [Composition] used for both preview and export. */
@UnstableApi
class CompositionFactory(
    private val registry: LiveLookRegistry,
    /** The encoder delay of an asset's sound, in µs ([com.example.prismstudio.p40b43a88.data.repository.AudioPrimingRepository]). */
    private val primingUs: (MediaAsset) -> Long = { 0L },
    /**
     * A render's record of where its sound items really start ([RenderSourceFactory] notes it): with it every sound item
     * of a render lines up to the sample and lasts exactly its planned length (see [AudioAlignment]); null in the preview.
     */
    private val audioStarts: AudioStarts? = null,
    /** Each file's noise floor, for the lanes whose noise reduction is on ([NoiseReductionAudioProcessor]); null until measured. */
    private val noiseProfile: (MediaAsset) -> NoiseProfile? = { null },
) {
    /** Keys of this factory's sound items, unique across its builds (passes and renders). */
    private val keyPrefix = java.util.UUID.randomUUID().toString().take(8)
    private val nextKey = java.util.concurrent.atomic.AtomicInteger()

    data class Output(
        val width: Int,
        val height: Int,
        /** Export frame rate when it differs from the timeline; null keeps the timeline rate. */
        val frameRate: Int? = null,
        val includeAudio: Boolean = true,
        /** Preview keeps a silent lane so the player always has an audio clock. */
        val keepAudioClock: Boolean = true,
        /** Coverage of the layer's picture; drawn over an [underlayPath] it is always straight alpha. */
        val alphaMode: AlphaMode = AlphaMode.OPAQUE,
        /** The picture of the layers beneath, rendered by an earlier pass (see [RenderPlan.stackingPasses]). */
        val underlayPath: String? = null,
        /**
         * A transparent image that fills the layer's gaps at the timeline's frame rate. Media3's own gap
         * frames come at 30 fps whatever the timeline's rate, which shows in a pass that is mostly gaps
         * (an overlay track over its underlay); null keeps Media3's gaps.
         */
        val clearFrameUri: String? = null,
        /**
         * A silent black clip, [FILLER_DURATION_US] long, that fills the layer's gaps drawn fully transparent
         * (and black under [AlphaMode.OPAQUE]). A Media3 1.9.3 preview can stay buffering at the boundary
         * between a native gap and the next picture. Null keeps Media3's gaps (or [clearFrameUri]).
         */
        val fillerUri: String? = null,
        /**
         * Drop source frames that come faster than the output's rate (a 60 fps clip on a 30 fps timeline),
         * so a render keeps one cadence; the preview plays sources at their own rate.
         */
        val conformFrameRate: Boolean = false,
        /**
         * A render: video clips play from [RenderSourceFactory] sources (each item takes exactly its planned
         * length, keeps the frames just before its cut and plays at its speed), and every item — clips,
         * stills, gaps — comes out at one constant frame rate through [ClipTimingEffect], frames repeated
         * where a source is slower (½×, 24 fps on 30). The preview plays plain clipped sources at their own
         * rate with Media3's speed handling.
         */
        val renderClips: Boolean = false,
        /** The project's colour management (HDR clips tone mapped into the Rec.709 timeline); null = no tone mapping. */
        val colorManagement: ColorManagement? = null,
    )

    /**
     * [reversedUris] holds the reversed copies (by asset id) of assets that reversed segments play;
     * while a copy is missing its segments play the same stretch forwards.
     */
    fun build(
        plan: RenderPlan,
        assets: Map<String, MediaAsset>,
        output: Output,
        reversedUris: Map<String, String> = emptyMap(),
        /** Resolve's proxies the monitor plays instead of footage (by asset id): pictures only, never in a render. */
        proxyUris: Map<String, String> = emptyMap(),
    ): Composition? {
        if (plan.isEmpty) return null
        // One decoded picture layer per composition: layered pictures render in passes (export) or
        // as stacked players (preview).
        require(!plan.isLayered) { "Build one picture layer at a time" }
        val fps = plan.fps
        val targetFps = output.frameRate ?: fps
        val alphaMode = if (output.underlayPath != null) AlphaMode.STRAIGHT else output.alphaMode
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val layer = plan.layers.singleOrNull() ?: VideoLayer("v1", listOf(VideoSegment(null, null, false, 0, plan.durationFrames, 0, 1f, null)))
        // A proxy stands in for the picture only: the sound (and a reversed copy) still read the original.
        val pictures = if (proxyUris.isEmpty() || output.renderClips) assets
        else assets.mapValues { (id, asset) -> proxyUris[id]?.let { asset.copy(uri = it) } ?: asset }
        sequences += videoSequence(layer, pictures, reversedUris, output, fps, targetFps, alphaMode)
        if (output.includeAudio) {
            plan.audio.forEach { lane -> audioLane(lane, assets, reversedUris, fps, align = output.renderClips)?.let(sequences::add) }
            if (sequences.size == 1 && output.keepAudioClock) {
                sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    .addGap(timelineBoundaryUs(plan.durationFrames, fps, output.renderClips))
                    .build()
            }
        }
        // Gaps arrive as 1×1 frames that skip the per-clip effects: stretch them over the raster so
        // they fill the picture instead of showing as a centred square.
        val compositionEffects = mutableListOf<Effect>(
            Presentation.createForWidthAndHeight(output.width, output.height, Presentation.LAYOUT_STRETCH_TO_FIT),
        )
        when {
            // The underlay is an earlier pass at the same rate: its frames sit on the same grid.
            output.underlayPath != null ->
                compositionEffects += UnderlayEffect(output.underlayPath, framesToUs(1, targetFps), compositeSpans(layer, fps, targetFps))
            // A lone opaque layer must stay opaque in its gaps too, which skip the per-clip shader.
            alphaMode == AlphaMode.OPAQUE -> compositionEffects += OpaqueFrameEffect()
            else -> Unit
        }
        if (plan.titles.isNotEmpty()) compositionEffects += OverlayEffect(listOf(TitleOverlay(plan.titles, fps, registry)))
        // Not Media3's FrameDropEffect: it holds the newest frame back and drops it at the end of every item.
        if (output.conformFrameRate || targetFps < fps) compositionEffects += FrameRateEffect(targetFps)
        // Export and preview both map HDR in the clip shader. Preview requests the raw signal per media item
        // so the decoder for an ordinary SDR clip on the same timeline is left in its normal mode.
        return Composition.Builder(sequences).setEffects(Effects(emptyList(), compositionEffects))
            .apply { if (output.renderClips) setHdrMode(Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR) }
            .build()
    }

    /**
     * Where the layer's clips composite in a mode other than normal (Resolve's Composite Mode), in µs of the pass:
     * the output frame slots each clip takes at [targetFps], from half a frame before the first, so a frame whose
     * timestamp rounds down still falls in its own clip's stretch.
     */
    private fun compositeSpans(layer: VideoLayer, fps: Int, targetFps: Int): List<UnderlayEffect.CompositeSpan> {
        val slots = FrameSlots(targetFps)
        val half = framesToUs(1, targetFps) / 2
        val at = { frame: Int -> slots.timeOf(slots.slotOf(framesToUs(frame, fps))) - half }
        return layer.segments.filter { it.showsPicture && it.composite != CompositeMode.NORMAL }
            .map { UnderlayEffect.CompositeSpan(at(it.startFrame), at(it.endFrame), it.composite) }
    }

    /**
     * The plan's sound alone, for an audio-only export: its lanes over a silent one as long as the plan, so the
     * file runs the whole range; null when nothing in it sounds.
     */
    /**
     * [plan]'s sound alone. Media3 mixes at the format of the first input it meets, and its gaps are silence at 44.1 kHz:
     * with [leadInUri] (a silent PCM file exactly [LANE_LEAD_IN_US] long at the wanted rate, for a WAV) every lane — and the
     * silent clock lane — opens with that file instead of a gap, so the mix runs at its rate and sound at that rate passes
     * through untouched; without it the lanes open with gaps (the mix at 44.1 kHz, as a movie's sound always was).
     */
    fun buildAudio(plan: RenderPlan, assets: Map<String, MediaAsset>, reversedUris: Map<String, String> = emptyMap(), leadInUri: String? = null): Composition? {
        if (plan.isEmpty) return null
        val lanes = plan.audio.mapNotNull { lane -> audioLane(lane, assets, reversedUris, plan.fps, align = true, leadInUri = leadInUri) }
        if (lanes.isEmpty()) return null
        val total = framesToUs(plan.durationFrames, plan.fps)
        val silence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).apply {
            if (leadInUri != null && total > LANE_LEAD_IN_US) {
                addItem(silentLeadIn(leadInUri))
                addGap(total - LANE_LEAD_IN_US)
            } else addGap(total)
        }.build()
        return Composition.Builder(lanes + silence).build()
    }

    /** The silent lead-in file as an item: as long as the lead-in, so nothing needs clipping. */
    private fun silentLeadIn(uri: String): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).setDurationUs(LANE_LEAD_IN_US).build()

    @Suppress("LongParameterList")
    private fun videoSequence(
        layer: VideoLayer,
        assets: Map<String, MediaAsset>,
        reversedUris: Map<String, String>,
        output: Output,
        fps: Int,
        targetFps: Int,
        alphaMode: AlphaMode,
    ): EditedMediaItemSequence {
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        for (segment in layer.segments) {
            val startUs = framesToUs(segment.startFrame, fps)
            val durationUs = timelineBoundaryUs(segment.endFrame, fps, output.renderClips) -
                timelineBoundaryUs(segment.startFrame, fps, output.renderClips)
            val asset = segment.assetId?.let(assets::get)
            if (asset == null || segment.clipId == null) {
                val clear = output.clearFrameUri
                val filler = output.fillerUri
                when {
                    filler != null -> fillerItems(filler, durationUs).forEach(sequence::addItem)
                    clear != null -> sequence.addItem(clearItem(clear, startUs, durationUs, output, fps, targetFps))
                    else -> sequence.addGap(durationUs)
                }
            } else {
                sequence.addItem(videoItem(segment, asset, reversedUris, durationUs, startUs, output, fps, targetFps, alphaMode))
            }
        }
        return sequence.build()
    }

    @Suppress("LongParameterList")
    private fun videoItem(
        segment: VideoSegment,
        asset: MediaAsset,
        reversedUris: Map<String, String>,
        durationUs: Long,
        startUs: Long,
        output: Output,
        fps: Int,
        targetFps: Int,
        alphaMode: AlphaMode,
    ): EditedMediaItem {
        val transition = segment.transition
        val look = ClipLookEffect(
            clipId = segment.clipId!!,
            registry = registry,
            itemStartUs = startUs,
            transition = transition?.type,
            transitionDurationUs = transition?.let { framesToUs(it.frames, fps) } ?: 0L,
            alphaMode = alphaMode,
            clipStartUs = framesToUs(segment.clipStartFrame, fps),
            transitionStartUs = framesToUs(segment.clipStartFrame - segment.transitionLead, fps),
            transitionRole = segment.transitionRole,
            styleClipId = segment.transitionOwnerId ?: segment.clipId!!,
        )
        val render = output.renderClips
        // Resolve's Scaling: a picture of another shape than the frame fits (bars), fills (cropped) or stretches; at its
        // own size it stretches to the frame, then shrinks or grows to its share of the timeline's raster.
        val fit = Presentation.createForWidthAndHeight(output.width, output.height, segment.scaling.layout())
        val native = if (segment.scaling == ClipScaling.CROP) FramingMatrix(segment.framing) else null
        // Resolve's Frame Blend: the frames footage lacks (a slowed clip, a slower rate) mix their neighbours; Optical Flow
        // makes them along the motion between those neighbours instead.
        val blend = segment.retime != RetimeProcess.NEAREST && asset.kind == MediaKind.VIDEO
        val flow = blend && segment.retime == RetimeProcess.OPTICAL_FLOW
        // A render times the frames itself, at the output's size and before the look (which follows each frame's
        // time): at the clip's speed, one per output frame, nothing past the clip's end. The preview plays frames at
        // their own times (Media3 has placed them at the clip's speed); a blending clip gets its in-between frames.
        // A render's window opens early enough that the frame on show at the clip's first moment arrives (a clip may
        // start partway through a source frame — a step of a speed ramp does), the timing anchored at that moment.
        val sourceMs = (durationUs / 1000.0 * segment.speed).toLong().coerceAtLeast(1)
        val leadMs = if (render && asset.kind == MediaKind.VIDEO) frameLeadMs(asset.frameRate) else SOURCE_EDGE_MS
        val openedUs = if (render) minOf(leadMs, readFrom(asset, segment.reversed, segment.sourceInMs, sourceMs, reversedUris).second) * 1000 else 0L
        val timing = when {
            render -> ClipTimingEffect(ClipTiming(startUs, segment.speed, startUs + durationUs, openedUs), targetFps, blend, opticalFlow = flow)
            blend -> ClipTimingEffect(ClipTiming(startUs, 1f, startUs + durationUs + PREVIEW_SLACK_US), fps, blend = true, render = false, opticalFlow = flow)
            else -> null
        }
        // Resolve's Temporal NR before the grade, on frames in timeline order at the output's size.
        val temporal = if (segment.temporalNr) TemporalNrEffect(segment.clipId!!, registry) else null
        // Convert the clip to the project's Rec.709 timeline before grading, effects and compositing.
        val color = output.colorManagement
        val toneMap = if (color != null && toneMaps(asset, color)) HdrToneMapEffect(asset.effectiveTransfer, color.toneMapping, color.pqPeakNits) else null
        val effects = listOfNotNull(toneMap, fit, native, timing, temporal, look)
        return if (asset.kind == MediaKind.IMAGE) {
            val item = MediaItem.Builder().setUri(asset.uri).setImageDurationMs(durationUs / 1000)
                .setMimeType(if (render) "image/*" else MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
                .build()
            EditedMediaItem.Builder(item)
                .setDurationUs(durationUs)
                .setFrameRate(if (render) targetFps else fps)
                .setEffects(Effects(emptyList(), effects))
                .build()
        } else {
            val item = clippedItem(asset, segment.reversed, segment.sourceInMs, sourceMs, reversedUris, if (render) RenderClip(durationUs) else null, openMs = leadMs)
            EditedMediaItem.Builder(item)
                .setRemoveAudio(true)
                .setDurationUs(asset.durationMs.coerceAtLeast(1) * 1000)
                .setEffects(Effects(emptyList(), effects))
                .apply { if (!render && segment.speed != 1f) setSpeed(ConstantSpeed(segment.speed)) }
                .build()
        }
    }

    /**
     * [durationUs] of nothing from [startUs]: transparent frames (see [Output.clearFrameUri]) at the output's rate,
     * on its frame grid in a render.
     */
    @Suppress("LongParameterList")
    private fun clearItem(uri: String, startUs: Long, durationUs: Long, output: Output, fps: Int, targetFps: Int): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.Builder().setUri(uri).setImageDurationMs(durationUs / 1000).build())
            .setDurationUs(durationUs)
            .setFrameRate(if (output.renderClips) targetFps else fps)
            .apply { if (output.renderClips) setEffects(Effects(emptyList(), listOf(ClipTimingEffect(ClipTiming(startUs, 1f, startUs + durationUs), targetFps)))) }
            .build()

    /** [durationUs] of the filler clip ([Output.fillerUri]) in pieces no longer than it, every frame transparent. */
    private fun fillerItems(uri: String, durationUs: Long): List<EditedMediaItem> = fillerPiecesUs(durationUs).map { piece ->
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionUs(piece).build())
            .build()
        EditedMediaItem.Builder(item)
            .setRemoveAudio(true)
            .setDurationUs(FILLER_DURATION_US)
            .setEffects(Effects(emptyList(), listOf(AlphaScale(0f))))
            .build()
    }

    /**
     * [lane]'s sound items and silences. With [align] (a render) each sound item lines up exactly: its window opens
     * [AUDIO_LEAD_US] early, [RenderSourceFactory] notes its first sample and [GainFadeAudioProcessor] drops what comes
     * before the clip's moment and gives exactly the item's planned length — Media3 neither trims an item that decodes
     * past its end nor keeps the compressed frame its window opens inside, which moved every later item off its picture.
     */
    @Suppress("LongParameterList")
    private fun audioLane(
        lane: AudioLane,
        assets: Map<String, MediaAsset>,
        reversedUris: Map<String, String>,
        fps: Int,
        align: Boolean = false,
        /** A silent PCM file to open the lane with instead of a gap ([buildAudio]). */
        leadInUri: String? = null,
    ): EditedMediaItemSequence? {
        if (lane.segments.none { it.assetId != null }) return null
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        val starts = audioStarts.takeIf { align }
        // Noise reduction goes first in each item's chain while the lane has it on (switching it on rebuilds the sound).
        val denoise = registry.laneMix(lane.trackId)?.noiseReduction != null
        // The lane's reverb rings on from one item into the next where they meet.
        val reverbLink = ReverbLink()
        for ((index, segment) in lane.segments.withIndex()) {
            val durationUs = timelineBoundaryUs(segment.endFrame, fps, align) - timelineBoundaryUs(segment.startFrame, fps, align)
            val asset = segment.assetId?.let(assets::get)
            if (asset == null || asset.kind == MediaKind.IMAGE || segment.clipId == null) {
                builder.addGap(durationUs)
                continue
            }
            val sourceMs = (durationUs / 1000.0 * segment.speed).toLong().coerceAtLeast(1)
            // Media3 starts a lane at its first sample's own time: a lane opening with a sound gets a moment of silence
            // first (its first item then starts at 0 like any other), which the sound skips.
            val leadInUs = if (starts != null && index == 0) LANE_LEAD_IN_US else 0L
            if (leadInUs > 0) {
                if (leadInUri != null) builder.addItem(silentLeadIn(leadInUri)) else builder.addGap(leadInUs)
            }
            val key = starts?.let { "$keyPrefix:${nextKey.getAndIncrement()}" }
            val (uri, from) = readFrom(asset, segment.reversed, segment.sourceInMs, sourceMs, reversedUris)
            val priming = if (uri == asset.uri) primingUs(asset) else 0L
            val item = clippedItem(asset, segment.reversed, segment.sourceInMs, sourceMs, reversedUris, audio = true, openMs = if (key != null) AUDIO_LEAD_US / 1000 else 0L)
                .let { if (key != null) it.buildUpon().setTag(AudioClip(key)).build() else it }
            val alignment = if (starts != null && key != null) {
                AudioAlignment(
                    starts = starts,
                    key = key,
                    wantedUs = from * 1000,
                    primingUs = priming,
                    windowStartUs = item.clippingConfiguration.startPositionUs,
                    speed = segment.speed,
                    lengthUs = durationUs,
                    skipUs = leadInUs,
                )
            } else null
            // A reversed copy has the same spectrum as its file: its noise floor is the file's.
            val noise = if (denoise) noiseProfile(asset)?.let { NoiseReductionAudioProcessor(registry, lane.trackId, it, segment.speed) } else null
            val processor = GainFadeAudioProcessor(
                registry = registry,
                laneTrackId = lane.trackId,
                clipId = segment.clipId,
                clipOffsetUs = segment.clipOffsetMs * 1000,
                clipDurationUs = segment.clipDurationMs * 1000,
                crossInUs = segment.crossInMs * 1000,
                crossOutUs = segment.crossOutMs * 1000,
                joinInUs = framesToUs(segment.joinInFrames, fps),
                joinOutUs = framesToUs(segment.joinOutFrames, fps),
                pieceUs = framesToUs(segment.endFrame - segment.joinOutFrames, fps) - framesToUs(segment.startFrame, fps),
                alignment = alignment,
                laneStartUs = framesToUs(segment.startFrame, fps),
                dryEndUs = if (segment.tailFrames > 0) {
                    framesToUs(segment.endFrame - segment.tailFrames, fps) - framesToUs(segment.startFrame, fps)
                } else Long.MAX_VALUE,
                reverbLink = reverbLink,
            )
            // Resolve's clip pitch, first in the chain, so the gain stage lines up and trims what it gives out as for any other item.
            val pitch = pitchShifter(segment.pitchCents)
            builder.addItem(
                EditedMediaItem.Builder(item)
                    .setRemoveVideo(true)
                    .setDurationUs(asset.durationMs.coerceAtLeast(1) * 1000)
                    // Every source to 16-bit PCM first (release round R16): the app's processors take 16-bit or float, and
                    // after an item's processors the audio graph can resample and remix but not change the sample format —
                    // a 24-bit or float WAV failed the render ("处理声音时出错"). AAC and MP3 decode to 16-bit already.
                    .setEffects(Effects(listOfNotNull(ToInt16PcmAudioProcessor(), pitch, noise, processor), emptyList()))
                    .apply { if (segment.speed != 1f) setSpeed(ConstantSpeed(segment.speed)) }
                    .build(),
            )
        }
        return builder.build()
    }

    /**
     * [sourceMs] of [asset] from [startMs]. A reversed stretch reads the reversed copy, where [startMs]
     * already points; until the copy exists the mirrored stretch of the original plays forwards. An [audio] item's
     * window is placed by [audioWindowUs].
     */
    @Suppress("LongParameterList")
    private fun clippedItem(
        asset: MediaAsset,
        reversed: Boolean,
        startMs: Long,
        sourceMs: Long,
        reversedUris: Map<String, String>,
        render: RenderClip? = null,
        audio: Boolean = false,
        openMs: Long = SOURCE_EDGE_MS,
    ): MediaItem {
        val (uri, from) = readFrom(asset, reversed, startMs, sourceMs, reversedUris)
        val window = MediaItem.ClippingConfiguration.Builder()
        if (audio) {
            // A reversed copy is written without priming to skip (ReverseMediaGenerator starts it early instead). A render
            // opens the window [openMs] early, lining the sound up itself (see audioLane), and closes it [AUDIO_TAIL_US]
            // late: ending at the clip's end, the decoder's last frame before it never came out and a cut clip's first
            // half ended in ~23 ms of silence (rev 155); the processor gives out exactly the item's length either way.
            val priming = if (uri == asset.uri) primingUs(asset) else 0L
            val (startUs, endUs) = if (openMs > 0) renderAudioWindowUs(from, sourceMs, priming, asset.durationMs, openMs * 1000)
            else audioWindowUs(from, sourceMs, priming, asset.durationMs)
            window.setStartPositionUs(startUs).setEndPositionUs(endUs)
        } else if (render != null) {
            // A render's window opens [openMs] early for the frame on show at the start, ending where the clip does
            // (ClipTiming is anchored at the start; RenderSourceFactory reads on past the end).
            window.setStartPositionMs(from - minOf(openMs, from)).setEndPositionMs(from + sourceMs)
        } else {
            // Open the window a hair early (keeping its length) so a frame the start was rounded up past still plays.
            val edge = minOf(SOURCE_EDGE_MS, from)
            window.setStartPositionMs(from - edge).setEndPositionMs(from - edge + sourceMs)
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(window.build())
            .apply {
                if (render != null) setTag(render)
                else if (!audio && asset.color.isHdr) setTag(HdrPreviewInput)
            }
            .build()
    }

    /**
     * The file a stretch of [sourceMs] from [startMs] is read from, and where in it: a reversed stretch reads the reversed
     * copy, where [startMs] already points; until the copy exists the mirrored stretch of the original plays forwards.
     */
    private fun readFrom(asset: MediaAsset, reversed: Boolean, startMs: Long, sourceMs: Long, reversedUris: Map<String, String>): Pair<String, Long> {
        val copy = if (reversed) reversedUris[asset.id] else null
        return when {
            !reversed -> asset.uri to startMs
            copy != null -> copy to startMs
            else -> asset.uri to ReverseMapping.mirrorStart(asset.durationMs, startMs, sourceMs)
        }
    }

    private fun ClipScaling.layout(): Int = when (this) {
        ClipScaling.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
        ClipScaling.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        ClipScaling.STRETCH, ClipScaling.CROP -> Presentation.LAYOUT_STRETCH_TO_FIT
    }

    /**
     * A frame-filling picture scaled about the centre to [framing]'s share of the frame, the frame keeping its size:
     * what lies outside is cut off, what the picture leaves is clear (Resolve's Center crop with no resizing).
     */
    private class FramingMatrix(private val framing: Framing) : MatrixTransformation {
        override fun getMatrix(presentationTimeUs: Long): Matrix = Matrix().apply { setScale(framing.spanX, framing.spanY) }
    }

    /**
     * One speed for the whole item. Not piecewise: Media3 1.9.3 queries a provider from 0 while setting an item up, but
     * hands it the media's own timestamps (past the clipping start) while it plays, so a clipped item with speed changes
     * would get the wrong length — a clip's speed points are separate items (see the render plan).
     */
    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    companion object {
        /** A seek to an edit uses whole milliseconds in both preview lanes. Render keeps the full frame precision. */
        private fun timelineBoundaryUs(frame: Int, fps: Int, render: Boolean): Long {
            val us = framesToUs(frame, fps)
            return if (render) us else us / 1000 * 1000
        }

        internal fun toneMaps(asset: MediaAsset, color: ColorManagement?): Boolean =
            color != null && asset.kind == MediaKind.VIDEO && asset.effectiveTransfer != ColorTransfer.SDR

        /** Sonic shifting a sound's pitch by [cents] at its own length and speed; none for 0. */
        internal fun pitchShifter(cents: Int): SonicAudioProcessor? =
            if (cents == 0) null else SonicAudioProcessor().apply {
                setPitch(Clip.pitchRatio(cents))
                setSpeed(1f)
            }

        /** How far past its planned end a preview item's frames may come (its length follows its source's). */
        private const val PREVIEW_SLACK_US = 1_000_000L

        /** The length of the filler clip (res/raw/prism_filler.mp4: 60 s of black, 128 × 128, 30 fps). */
        const val FILLER_DURATION_US = 60_000_000L

        /** The lengths a gap of [durationUs] is filled with: whole filler clips, then what is left. */
        fun fillerPiecesUs(durationUs: Long): List<Long> = buildList {
            var left = durationUs
            while (left > 0) {
                val piece = minOf(left, FILLER_DURATION_US)
                add(piece)
                left -= piece
            }
        }

        fun framesToUs(frames: Int, fps: Int): Long = frames * 1_000_000L / fps.coerceAtLeast(1)

        /**
         * How early a render opens a sound item's window: more than the longest compressed frame (WAV is read in tenths of
         * a second, AAC in 21–46 ms), so the frame the clip starts in arrives whole, with one before it for the decoder.
         */
        const val AUDIO_LEAD_US = 150_000L

        /** How far past a clip's end a render reads its sound (see clippedItem): a few compressed frames at any rate. */
        const val AUDIO_TAIL_US = 100_000L

        /** The silence a render's lane opening with a sound starts with (its first item skips as much). */
        const val LANE_LEAD_IN_US = 1_000L

        /**
         * How far before a clip's first moment a render opens its window: a source frame and a half at the source's
         * rate (the slowest common rate when it is unknown), so the frame on show at that moment arrives even where the
         * frame times wobble; [ClipCadence] keeps the latest of the early ones.
         */
        fun frameLeadMs(frameRate: Float): Long =
            ceil(EARLY_FRAMES * 1000.0 / (frameRate.takeIf { it > 0f } ?: SLOWEST_SOURCE_FPS)).toLong() + SOURCE_EDGE_MS

        private const val EARLY_FRAMES = 1.5
        private const val SLOWEST_SOURCE_FPS = 24f

        /**
         * The window (µs) an audio item reads: [sourceMs] from [fromMs] of a file [durationMs] long whose sound starts
         * with [primingUs] of encoder delay. From the file's start Media3 skips the delay itself; from inside the file it
         * hands over the samples at their stored times, so the window opens that much later — as far as the file leaves
         * room past its end (the window keeps its length). Without the early edge pictures get: a sound has no frames
         * to round past, and opening early only put it late.
         */
        fun audioWindowUs(fromMs: Long, sourceMs: Long, primingUs: Long, durationMs: Long): Pair<Long, Long> {
            val endUs = (fromMs + sourceMs) * 1000
            val shift = if (fromMs <= 0 || primingUs <= 0) 0L else minOf(primingUs, durationMs * 1000 - endUs).coerceAtLeast(0)
            return fromMs * 1000 + shift to endUs + shift
        }

        /** A render's audio window: [audioWindowUs] opened [leadUs] early and closed [AUDIO_TAIL_US] late, within the file. */
        fun renderAudioWindowUs(fromMs: Long, sourceMs: Long, primingUs: Long, durationMs: Long, leadUs: Long): Pair<Long, Long> {
            val (startUs, endUs) = audioWindowUs(fromMs, sourceMs, primingUs, durationMs)
            val tailUs = minOf(AUDIO_TAIL_US, durationMs * 1000 - endUs).coerceAtLeast(0)
            return (startUs - leadUs).coerceAtLeast(0) to endUs + tailUs
        }

        /** Preview raster: the project raster scaled so its long side is at most [maxLongSide]. */
        fun previewSize(width: Int, height: Int, maxLongSide: Int = 1920): Pair<Int, Int> {
            val scale = minOf(1f, maxLongSide.toFloat() / maxOf(width, height).coerceAtLeast(1))
            return ((width * scale).toInt() and 1.inv()).coerceAtLeast(2) to ((height * scale).toInt() and 1.inv()).coerceAtLeast(2)
        }
    }
}
