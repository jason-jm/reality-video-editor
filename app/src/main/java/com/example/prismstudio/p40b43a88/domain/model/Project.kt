package com.example.prismstudio.p40b43a88.domain.model

/** The timeline a project starts with. */
const val FIRST_TIMELINE_ID = "tl-1"
const val FIRST_TIMELINE_NAME = "时间线 1"

/** Timeline rasters a project is created with, or changed to in the timeline settings. */
enum class FormatPreset(val width: Int, val height: Int, val label: String) {
    HD_LANDSCAPE(1920, 1080, "1080p 横屏"),
    UHD_LANDSCAPE(3840, 2160, "4K 横屏"),
    HD_PORTRAIT(1080, 1920, "1080p 竖屏"),
    HD_SQUARE(1080, 1080, "1080p 方形"),
    ;

    fun matches(format: ProjectFormat): Boolean = format.width == width && format.height == height

    companion object {
        /** The preset [format]'s raster is, if any. */
        fun of(format: ProjectFormat): FormatPreset? = entries.firstOrNull { it.matches(format) }
    }
}

/**
 * The timeline's raster and frame rate, and Resolve's Image Scaling for mismatched media: how clips that follow the
 * project ([Clip.scaling] null) fill the frame when their shape or size differs.
 */
data class ProjectFormat(val width: Int, val height: Int, val fps: Int, val scaling: ClipScaling = ClipScaling.FIT) {
    val orientationLabel: String
        get() = when {
            height > width -> "竖屏"
            height == width -> "方形"
            else -> "横屏"
        }
    val shortLabel: String
        get() = when {
            width >= 3840 || height >= 3840 -> "4K"
            width == 1080 && height == 1920 -> "1080×1920"
            else -> "${minOf(width, height)}p"
        }
    val sizeLabel: String get() = "$width×$height $orientationLabel"

    companion object {
        val FRAME_RATES = listOf(24, 25, 30, 60)
        fun of(preset: FormatPreset, fps: Int) = ProjectFormat(preset.width, preset.height, fps)
    }
}

/** Lightweight row shown in the project library. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val format: ProjectFormat,
    val durationFrames: Int,
    val clipCount: Int,
    val modifiedAtMillis: Long,
    val poster: PosterColors,
    /** Where to decode a real poster frame from, when the project has picture media. */
    val posterSource: PosterSource? = null,
)

/** A frame of a media file that represents a project. */
data class PosterSource(val uri: String, val kind: MediaKind, val timeMs: Long)

/** Three representative colours used when no decoded poster frame is available yet. */
data class PosterColors(val top: Long, val middle: Long, val bottom: Long) {
    companion object {
        val NEUTRAL = PosterColors(0xFF22252E, 0xFF5B6170, 0xFF14161B)

        /** The two sample pictures the library's transition, title and effect illustrations are drawn with (the design's). */
        val LIBRARY_NIGHT = PosterColors(0xFF2A0E3A, 0xFFF04E8C, 0xFF0D1B33)
        val LIBRARY_DUSK = PosterColors(0xFF10203A, 0xFFE89A3C, 0xFF0C1220)
    }
}

/**
 * One of the project's timelines while another is open: Resolve keeps several in a project (a cut, its vertical
 * version, an alternative), each with its own raster, clips and mixer.
 */
data class StoredTimeline(val id: String, val name: String, val format: ProjectFormat, val timeline: Timeline, val mixer: MixerState)

/**
 * A timeline as `timeline-menu` lists it. [compound]: the timeline of a compound clip — the media pool lists it, the
 * timeline menu and the Deliver page do not.
 */
data class TimelineEntry(
    val id: String,
    val name: String,
    val open: Boolean,
    val format: ProjectFormat,
    val durationFrames: Int,
    val compound: Boolean = false,
)

/** The complete, persisted state of one editing project. */
data class ProjectDocument(
    val id: String,
    val name: String,
    val format: ProjectFormat,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
    val media: List<MediaAsset>,
    val timeline: Timeline,
    val mixer: MixerState,
    val render: RenderSettings,
    val jobs: List<RenderJob>,
    /** The Color page's Gallery: stills grabbed in this project, oldest first. */
    val stills: List<GalleryStill> = emptyList(),
    /** The open timeline: its raster is [format], its clips [timeline], its mixer [mixer]. */
    val timelineId: String = FIRST_TIMELINE_ID,
    val timelineName: String = FIRST_TIMELINE_NAME,
    /** The project's other timelines in order, the open one sitting at [timelineIndex] among them. */
    val timelines: List<StoredTimeline> = emptyList(),
    val timelineIndex: Int = 0,
    /** The media pool's user bins, in the order made. */
    val bins: List<UserBin> = emptyList(),
    /** The media pool's smart bins (saved searches), in the order made. */
    val smartBins: List<SmartBin> = emptyList(),
    /** Resolve's Proxy Handling: the monitors play footage's proxies where they are ready; renders use the originals. */
    val useProxies: Boolean = false,
    /** The timelines that hold compound clips' contents (Resolve's compound clips), among [timelines] or the open one. */
    val compoundIds: Set<String> = emptySet(),
    /**
     * Among [compoundIds], the multicam clips' (Resolve's multicam clips): angle n is the timeline's video track Vn and its
     * sound A_n; a clip of it plays only its [Clip.angle].
     */
    val multicamIds: Set<String> = emptySet(),
    /**
     * The timelines compound clips were opened from, outermost first (`compound-return-chip` goes back to the last); empty
     * when the open timeline was opened from the timeline menu or the media pool.
     */
    val compoundTrail: List<String> = emptyList(),
    /** Resolve's Color Management for the whole project: the Rec.709 timeline and how HDR sources are tone mapped into it. */
    val colorManagement: ColorManagement = ColorManagement.DEFAULT,
) {
    val durationFrames: Int get() = timeline.durationFrames

    /**
     * The project as it would be with timeline [id] open — what a render job bound to it renders; this document when it
     * is the open one, null when the project no longer has it. Not a change of the project.
     */
    fun viewing(id: String): ProjectDocument? {
        if (id == timelineId) return this
        val stored = timelines.firstOrNull { it.id == id } ?: return null
        // The open timeline waits in the viewed one's place: compound clips may play it.
        val others = timelines.map { if (it.id == id) StoredTimeline(timelineId, timelineName, format, timeline, mixer) else it }
        return copy(
            timelineId = stored.id, timelineName = stored.name, format = stored.format, timeline = stored.timeline, mixer = stored.mixer,
            timelines = others, compoundTrail = emptyList(),
        )
    }

    /** Update only sources actually probed. A relink that happened during the check has a different URI. */
    fun withMediaAvailability(checked: Map<String, Boolean>): ProjectDocument {
        var changed = false
        val updated = media.map { asset ->
            val available = checked[asset.uri] ?: return@map asset
            val offline = !available
            if (asset.offline == offline) return@map asset
            changed = true
            asset.copy(offline = offline)
        }
        return if (changed) copy(media = updated) else this
    }

    /** Every timeline in order, the open one included. */
    val timelineEntries: List<TimelineEntry>
        get() = timelines.map { TimelineEntry(it.id, it.name, false, it.format, it.timeline.durationFrames, it.id in compoundIds) }
            .toMutableList().apply {
                add(timelineIndex.coerceIn(0, size), TimelineEntry(timelineId, timelineName, true, format, durationFrames, timelineId in compoundIds))
            }

    /** The open timeline is a compound clip's. */
    val inCompound: Boolean get() = timelineId in compoundIds

    /** Timeline [id] of the project, the open one included; null when it has none. */
    fun storedTimeline(id: String?): StoredTimeline? = when (id) {
        null -> null
        timelineId -> StoredTimeline(timelineId, timelineName, format, timeline, mixer)
        else -> timelines.firstOrNull { it.id == id }
    }

    /**
     * How much source [clip] can show, in ms: its file's length, or a compound clip's timeline's; null when it is unbounded
     * (stills, titles, generators) or unknown.
     */
    fun sourceLengthMs(clip: Clip): Long? = when {
        clip.compoundId != null -> storedTimeline(clip.compoundId)?.let { it.timeline.durationFrames * 1000L / it.format.fps.coerceAtLeast(1) }
        else -> asset(clip.assetId)?.takeIf { it.kind != MediaKind.IMAGE }?.durationMs
    }

    fun asset(id: String?): MediaAsset? = media.firstOrNull { it.id == id }

    fun summary(): ProjectSummary = ProjectSummary(
        id = id,
        name = name,
        format = format,
        durationFrames = durationFrames,
        clipCount = timeline.clips.count { it.kind != ClipKind.AUDIO },
        modifiedAtMillis = modifiedAtMillis,
        poster = posterClip()?.let { clip -> asset(clip.assetId)?.poster } ?: media.firstOrNull()?.poster ?: PosterColors.NEUTRAL,
        posterSource = posterSource(),
    )

    /** The picture a project is represented by: the earliest video clip on the timeline. */
    private fun posterClip(): Clip? = timeline.clips.filter { it.kind == ClipKind.VIDEO }.minByOrNull { it.start }

    private fun posterSource(): PosterSource? {
        val clip = posterClip()
        val asset = asset(clip?.assetId) ?: media.firstOrNull { it.kind != MediaKind.AUDIO } ?: return null
        val at = if (clip != null && clip.assetId == asset.id) clip.sourceInMs + 500 else minOf(1_000L, asset.durationMs / 2)
        return PosterSource(asset.uri, asset.kind, at)
    }
}
