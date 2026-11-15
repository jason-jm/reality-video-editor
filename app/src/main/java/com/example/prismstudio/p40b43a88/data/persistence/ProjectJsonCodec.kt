package com.example.prismstudio.p40b43a88.data.persistence

import com.example.prismstudio.p40b43a88.domain.model.ParamKey
import com.example.prismstudio.p40b43a88.domain.model.UserBin
import com.example.prismstudio.p40b43a88.domain.model.SmartBin
import com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat
import com.example.prismstudio.p40b43a88.domain.model.TrackDeEsser
import com.example.prismstudio.p40b43a88.domain.model.TrackReverb
import com.example.prismstudio.p40b43a88.domain.model.DeEssBand
import com.example.prismstudio.p40b43a88.domain.model.ClipMetadata
import com.example.prismstudio.p40b43a88.domain.model.FlagRule
import com.example.prismstudio.p40b43a88.domain.model.UsageRule
import com.example.prismstudio.p40b43a88.domain.model.GradeVersion
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.ClipStabilization
import com.example.prismstudio.p40b43a88.domain.model.SyncedAudio
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_ID
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.RgbMixer
import com.example.prismstudio.p40b43a88.domain.model.ColorWarper
import com.example.prismstudio.p40b43a88.domain.model.AudioChannels
import com.example.prismstudio.p40b43a88.domain.model.ChannelMix
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.SerialNode
import com.example.prismstudio.p40b43a88.domain.model.GradeNode
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.model.DuckingControl
import com.example.prismstudio.p40b43a88.domain.model.DynamicsControl
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.EffectKeyframe
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.GalleryStill
import com.example.prismstudio.p40b43a88.domain.model.GradeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.HueCurve
import com.example.prismstudio.p40b43a88.domain.model.HueCurves
import com.example.prismstudio.p40b43a88.domain.model.LutRef
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerBus
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ColorPrimaries
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.domain.model.SourceColor
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.geometry
import com.example.prismstudio.p40b43a88.domain.model.withGeometry
import com.example.prismstudio.p40b43a88.domain.model.WindowControl
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderResolution
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.SubtitleLanguage
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyleControl
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.NoiseReductionControl
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyGroup
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.VideoCodec
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WheelValue
import com.example.prismstudio.p40b43a88.domain.model.WindowGeometry
import com.example.prismstudio.p40b43a88.domain.model.WindowKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON form of a [ProjectDocument]. Unknown or missing fields fall back to defaults so
 * older project files keep opening after the model grows.
 */
object ProjectJsonCodec {
    const val VERSION = 1
    /** A submix bus's id: b1 … b3. */
    private val BUS_ID = Regex("b[1-9]")

    fun encode(doc: ProjectDocument): String = JSONObject().apply {
        put("version", VERSION)
        put("id", doc.id)
        put("name", doc.name)
        put("format", encodeFormat(doc.format))
        put("createdAt", doc.createdAtMillis)
        put("modifiedAt", doc.modifiedAtMillis)
        put("media", JSONArray(doc.media.map(::encodeAsset)))
        put("timeline", encodeTimeline(doc.timeline))
        put("mixer", encodeMixer(doc.mixer))
        put("render", encodeRender(doc.render))
        put("jobs", JSONArray(doc.jobs.map(::encodeJob)))
        if (doc.stills.isNotEmpty()) put("stills", JSONArray(doc.stills.map(::encodeStill)))
        // User bins (older projects have none).
        if (doc.bins.isNotEmpty()) put("bins", JSONArray(doc.bins.map { JSONObject().put("id", it.id).put("name", it.name) }))
        // Smart bins: only the rules that are set.
        if (doc.smartBins.isNotEmpty()) put("smartBins", JSONArray(doc.smartBins.map(::encodeSmartBin)))
        if (doc.useProxies) put("useProxies", true)
        // The open timeline is the document's own; the others wait beside it (absent: a project of one timeline).
        put("timelineId", doc.timelineId)
        put("timelineName", doc.timelineName)
        if (doc.timelines.isNotEmpty()) {
            put("timelineIndex", doc.timelineIndex)
            put("timelines", JSONArray(doc.timelines.map(::encodeStoredTimeline)))
        }
        // Compound clips' timelines (absent: none), and the timeline a compound clip was opened from.
        if (doc.compoundIds.isNotEmpty()) put("compounds", JSONArray(doc.compoundIds.sorted()))
        if (doc.multicamIds.isNotEmpty()) put("multicams", JSONArray(doc.multicamIds.sorted()))
        if (doc.colorManagement != ColorManagement.DEFAULT) {
            put("colorManagement", JSONObject().put("toneMapping", doc.colorManagement.toneMapping.name).put("pqPeak", doc.colorManagement.pqPeakNits))
        }
        if (doc.compoundTrail.isNotEmpty()) put("compoundTrail", JSONArray(doc.compoundTrail))
    }.toString()

    private fun encodeFormat(f: ProjectFormat) = JSONObject().put("width", f.width).put("height", f.height).put("fps", f.fps).apply {
        if (f.scaling != ClipScaling.FIT) put("scaling", f.scaling.name)
    }

    private fun decodeFormat(f: JSONObject) = ProjectFormat(
        f.optInt("width", 1920), f.optInt("height", 1080), f.optInt("fps", 30),
        enumOr(f.optString("scaling"), ClipScaling.FIT),
    )

    private fun encodeStoredTimeline(t: StoredTimeline) = JSONObject()
        .put("id", t.id)
        .put("name", t.name)
        .put("format", encodeFormat(t.format))
        .put("timeline", encodeTimeline(t.timeline))
        .put("mixer", encodeMixer(t.mixer))

    private fun decodeStoredTimeline(o: JSONObject): StoredTimeline? {
        val timeline = o.optJSONObject("timeline")?.let(::decodeTimeline) ?: return null
        return StoredTimeline(
            id = o.optString("id").ifEmpty { return null },
            name = o.optString("name").ifEmpty { FIRST_TIMELINE_NAME },
            format = o.optJSONObject("format")?.let(::decodeFormat) ?: ProjectFormat(1920, 1080, 30),
            timeline = timeline,
            mixer = o.optJSONObject("mixer")?.let(::decodeMixer) ?: MixerState.forTracks(timeline.tracks),
        )
    }

    fun decode(text: String): ProjectDocument {
        val o = JSONObject(text)
        val f = o.getJSONObject("format")
        val timeline = o.optJSONObject("timeline")?.let(::decodeTimeline) ?: Timeline.EMPTY
        val timelineId = o.optString("timelineId").ifEmpty { FIRST_TIMELINE_ID }
        val storedTimelines = o.optJSONArray("timelines").objects().mapNotNull(::decodeStoredTimeline)
        return ProjectDocument(
            id = o.getString("id"),
            name = o.optString("name", "未命名项目"),
            format = decodeFormat(f),
            createdAtMillis = o.optLong("createdAt"),
            modifiedAtMillis = o.optLong("modifiedAt"),
            media = o.optJSONArray("media").objects().map(::decodeAsset),
            timeline = timeline,
            mixer = o.optJSONObject("mixer")?.let(::decodeMixer) ?: MixerState.forTracks(timeline.tracks),
            render = o.optJSONObject("render")?.let(::decodeRender) ?: RenderSettings(fileName = o.optString("name")),
            jobs = o.optJSONArray("jobs").objects().map { item ->
                val job = decodeJob(item)
                // Older queues only stored In-to-Out mode. Use the matching timeline's saved marks on first load.
                if (job.queuedRange == null && job.settings.range == RenderRange.IN_TO_OUT && job.clipId == null && !job.status.mediaSaved) {
                    val source = if (job.timelineId == timelineId) timeline else storedTimelines.firstOrNull { it.id == job.timelineId }?.timeline
                    job.copy(queuedRange = source?.let(job.settings.range::frameLimits))
                } else job
            },
            stills = o.optJSONArray("stills").objects().mapNotNull(::decodeStill),
            bins = o.optJSONArray("bins").objects().mapNotNull { b -> b.optString("id").takeIf { it.isNotEmpty() }?.let { UserBin(it, b.optString("name", "媒体夹")) } }
                .distinctBy { it.id },
            smartBins = o.optJSONArray("smartBins").objects().mapNotNull(::decodeSmartBin).distinctBy { it.id },
            useProxies = o.optBoolean("useProxies"),
            timelineId = timelineId,
            timelineName = o.optString("timelineName").ifEmpty { FIRST_TIMELINE_NAME },
            timelines = storedTimelines,
            timelineIndex = o.optInt("timelineIndex", 0),
            compoundIds = o.optJSONArray("compounds")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } }.toSet() }.orEmpty(),
            compoundTrail = o.optJSONArray("compoundTrail")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } }.orEmpty(),
            multicamIds = o.optJSONArray("multicams")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } }.toSet() }.orEmpty(),
            colorManagement = o.optJSONObject("colorManagement")?.let { c ->
                ColorManagement(
                    toneMapping = enumOr(c.optString("toneMapping"), ToneMapping.LUMINANCE),
                    pqPeakNits = c.optInt("pqPeak", 1000).takeIf { it in ColorManagement.PQ_PEAKS } ?: 1000,
                )
            } ?: ColorManagement.DEFAULT,
        )
    }

    private fun encodeSmartBin(b: SmartBin): JSONObject = JSONObject().put("id", b.id).put("name", b.name).apply {
        b.kind?.let { put("kind", it.name) }
        if (b.flag != FlagRule.ANY) put("flag", b.flag.name)
        if (b.usage != UsageRule.ANY) put("usage", b.usage.name)
        if (b.nameContains.isNotBlank()) put("nameContains", b.nameContains)
        if (b.keyword.isNotBlank()) put("keyword", b.keyword)
        if (b.goodTake) put("goodTake", true)
    }

    private fun decodeSmartBin(o: JSONObject): SmartBin? = SmartBin(
        id = o.optString("id").ifEmpty { return null },
        name = o.optString("name").ifEmpty { "智能媒体夹" },
        kind = o.optString("kind").takeIf { it.isNotEmpty() }?.let { k -> MediaKind.entries.firstOrNull { it.name == k } },
        flag = enumOr(o.optString("flag"), FlagRule.ANY),
        usage = enumOr(o.optString("usage"), UsageRule.ANY),
        nameContains = o.optString("nameContains"),
        keyword = o.optString("keyword"),
        goodTake = o.optBoolean("goodTake"),
    )

    /** A Gallery still: its grade is kept as the clip's grade frozen at the grabbed frame. */
    private fun encodeStill(s: GalleryStill): JSONObject = JSONObject()
        .put("id", s.id).put("number", s.number).put("clip", s.clipName).put("tc", s.timecode)
        .put("grade", encodeGrade(s.grade)).put("image", s.image).put("w", s.width).put("h", s.height)

    private fun decodeStill(o: JSONObject): GalleryStill? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val image = o.optString("image").takeIf { it.isNotBlank() } ?: return null
        return GalleryStill(
            id = id,
            number = o.optInt("number", 1).coerceAtLeast(1),
            clipName = o.optString("clip"),
            timecode = o.optString("tc"),
            grade = o.optJSONObject("grade")?.let(::decodeGrade) ?: ColorGrade(),
            image = image,
            width = o.optInt("w", 0),
            height = o.optInt("h", 0),
        )
    }

    private fun encodePoster(p: PosterColors) = JSONArray().put(p.top).put(p.middle).put(p.bottom)

    private fun decodePoster(a: JSONArray?): PosterColors =
        if (a == null || a.length() < 3) PosterColors.NEUTRAL else PosterColors(a.getLong(0), a.getLong(1), a.getLong(2))

    private fun encodeAsset(a: MediaAsset) = JSONObject().apply {
        put("id", a.id); put("uri", a.uri); put("name", a.name); put("kind", a.kind.name)
        put("durationMs", a.durationMs); put("width", a.width); put("height", a.height)
        put("frameRate", a.frameRate.toDouble()); put("videoCodec", a.videoCodec)
        put("audio", a.audioDescription); put("hasAudio", a.hasAudio); put("size", a.sizeBytes)
        put("path", a.relativePath); put("poster", encodePoster(a.poster))
        if (a.flags.isNotEmpty()) put("flags", JSONArray(a.flags.map { it.name }))
        a.binId?.let { put("bin", it) }
        // Clip metadata: only the fields that are set.
        if (!a.metadata.isEmpty) put("meta", JSONObject().apply {
            val m = a.metadata
            if (m.scene.isNotEmpty()) put("scene", m.scene)
            if (m.shot.isNotEmpty()) put("shot", m.shot)
            if (m.take.isNotEmpty()) put("take", m.take)
            if (m.keywords.isNotEmpty()) put("keywords", JSONArray(m.keywords))
            if (m.notes.isNotEmpty()) put("notes", m.notes)
            if (m.goodTake) put("goodTake", true)
        })
        a.syncedAudio?.let { put("sync", JSONObject().put("asset", it.assetId).put("offset", it.offsetMs)) }
        if (a.color != SourceColor.UNKNOWN) {
            put("color", JSONObject().put("transfer", a.color.transfer.name).put("bits", a.color.bitDepth).apply { a.color.primaries?.let { put("primaries", it.name) } })
        }
        if (a.inputColor != InputColorSpace.AUTO) put("inputColor", a.inputColor.name)
    }

    private fun decodeAsset(o: JSONObject) = MediaAsset(
        id = o.getString("id"),
        uri = o.getString("uri"),
        name = o.optString("name"),
        kind = enumOr(o.optString("kind"), MediaKind.VIDEO),
        durationMs = o.optLong("durationMs"),
        width = o.optInt("width"),
        height = o.optInt("height"),
        frameRate = o.optDouble("frameRate", 0.0).toFloat(),
        videoCodec = o.optString("videoCodec"),
        audioDescription = o.optString("audio"),
        hasAudio = o.optBoolean("hasAudio"),
        sizeBytes = o.optLong("size"),
        relativePath = o.optString("path"),
        poster = decodePoster(o.optJSONArray("poster")),
        flags = o.optJSONArray("flags")?.let { a -> (0 until a.length()).mapNotNull { i -> FlagColor.entries.firstOrNull { it.name == a.optString(i) } } }
            .orEmpty().distinct().sorted(),
        binId = o.optString("bin").takeIf { it.isNotEmpty() },
        syncedAudio = o.optJSONObject("sync")?.let { s -> s.optString("asset").takeIf { it.isNotEmpty() }?.let { SyncedAudio(it, s.optLong("offset")) } },
        color = o.optJSONObject("color")?.let { c ->
            SourceColor(
                primaries = c.optString("primaries").takeIf { it.isNotEmpty() }?.let { n -> ColorPrimaries.entries.firstOrNull { it.name == n } },
                transfer = enumOr(c.optString("transfer"), ColorTransfer.SDR),
                bitDepth = c.optInt("bits", 8).coerceIn(8, 16),
            )
        } ?: SourceColor.UNKNOWN,
        inputColor = enumOr(o.optString("inputColor"), InputColorSpace.AUTO),
        metadata = o.optJSONObject("meta")?.let { m ->
            ClipMetadata(
                scene = m.optString("scene"),
                shot = m.optString("shot"),
                take = m.optString("take"),
                keywords = m.optJSONArray("keywords")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                notes = m.optString("notes"),
                goodTake = m.optBoolean("goodTake"),
            )
        } ?: ClipMetadata.NONE,
    )

    private fun encodeTimeline(t: Timeline) = JSONObject().apply {
        put("tracks", JSONArray(t.tracks.map {
            JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name)
                .put("enabled", it.enabled).put("locked", it.locked)
                .apply { if (it.subtitleStyle != SubtitleStyle.DEFAULT) put("subtitleStyle", encodeSubtitleStyle(it.subtitleStyle)) }
                .apply { it.language?.let { lang -> put("lang", lang.code) } }
        }))
        put("clips", JSONArray(t.clips.map(::encodeClip)))
        put("markers", JSONArray(t.markers.map { m ->
            JSONObject().put("id", m.id).put("frame", m.frame).put("label", m.label).apply {
                if (m.color != MarkerColor.BLUE) put("color", m.color.name)
                if (m.note.isNotEmpty()) put("note", m.note)
                if (m.durationFrames > 0) put("duration", m.durationFrames)
            }
        }))
        if (t.targets != EditTargets()) {
            put("targets", JSONObject().put("video", t.targets.video).apply { t.targets.audio?.let { put("audio", it) } })
        }
        if (!t.marks.isEmpty) {
            put("marks", JSONObject().apply { t.marks.inFrame?.let { put("in", it) }; t.marks.outFrame?.let { put("out", it) } })
        }
    }

    private fun decodeTimeline(o: JSONObject): Timeline {
        val clips = o.optJSONArray("clips").objects().map(::decodeClip)
        val tracks = o.optJSONArray("tracks").objects().map {
            val id = it.getString("id")
            Track(
                id = id,
                name = it.optString("name", id.uppercase()),
                kind = enumOr(it.optString("kind"), TrackKind.VIDEO),
                enabled = it.optBoolean("enabled", true),
                locked = it.optBoolean("locked", false),
                // Before track styles each subtitle had its own size: the track takes its first subtitle's.
                subtitleStyle = it.optJSONObject("subtitleStyle")?.let(::decodeSubtitleStyle)
                    ?: clips.firstOrNull { c -> c.trackId == id && c.title?.template == TitleTemplate.SUBTITLE }
                        ?.let { c -> SubtitleStyle(sizePx = c.title!!.sizePx.coerceIn(SubtitleStyleControl.SIZE.min, SubtitleStyleControl.SIZE.max)) }
                    ?: SubtitleStyle.DEFAULT,
                language = SubtitleLanguage.byCode(it.optNullableString("lang")),
            )
        }.ifEmpty { Timeline.defaultTracks() }
        return Timeline(
            tracks = tracks,
            clips = clips,
            markers = o.optJSONArray("markers").objects().map {
                Marker(
                    it.getString("id"), it.optInt("frame"), it.optString("label"), enumOr(it.optString("color"), MarkerColor.BLUE), it.optString("note"),
                    durationFrames = it.optInt("duration", 0).coerceAtLeast(0),
                )
            },
            targets = o.optJSONObject("targets")?.let {
                EditTargets(video = it.optString("video", Timeline.V1), audio = it.optString("audio").takeIf(String::isNotEmpty))
            } ?: EditTargets(),
            marks = o.optJSONObject("marks")?.let { m ->
                val inFrame = m.optInt("in", -1).takeIf { it >= 0 }
                // An Out at or before In (a hand-edited file) keeps the In alone.
                TimelineMarks(inFrame, m.optInt("out", -1).takeIf { it > (inFrame ?: 0) })
            } ?: TimelineMarks(),
        )
    }

    /** A preset by name (every file before free speeds), or a factor (Fit to Fill) kept within the playable range. */
    /**
     * A clip's speed sections when they add up to its [duration] and number two or more; none otherwise. A section's
     * "ramp" smooths the point before it (none on the first).
     */
    private fun decodeSections(o: JSONObject, duration: Int): List<SpeedSection> {
        val sections = o.optJSONArray("speedSections").objects().mapIndexed { i, it ->
            SpeedSection(it.optInt("frames", 0), decodeSpeed(it.opt("speed")), if (i == 0) 0 else it.optInt("ramp", 0).coerceAtLeast(0))
        }
        return sections.takeIf { list -> list.size >= 2 && list.all { it.frames > 0 } && list.sumOf { it.frames } == duration }.orEmpty()
    }

    private fun decodeSpeed(value: Any?): ClipSpeed = when (value) {
        is Number -> value.toFloat().takeIf { it.isFinite() }?.let { ClipSpeed(it.coerceIn(ClipSpeed.MIN, ClipSpeed.MAX)) } ?: ClipSpeed.NORMAL
        is String -> ClipSpeed.of(enumOr(value, PlaybackSpeed.NORMAL))
        else -> ClipSpeed.NORMAL
    }

    private fun encodeClip(c: Clip) = JSONObject().apply {
        put("id", c.id); put("track", c.trackId); put("kind", c.kind.name); put("name", c.name)
        put("start", c.start); put("duration", c.durationFrames); put("asset", c.assetId ?: JSONObject.NULL)
        put("sourceIn", c.sourceInMs); put("speed", c.speed.preset?.name ?: c.speed.factor.toDouble()); put("reverse", c.reverse)
        // Speed points, only on a clip that has them (older projects have none).
        if (c.retimed) {
            put("speedSections", JSONArray(c.speedSections.map {
                JSONObject().put("frames", it.frames).put("speed", it.speed.preset?.name ?: it.speed.factor.toDouble())
                    .apply { if (it.rampFrames > 0) put("ramp", it.rampFrames) }
            }))
        }
        put("transform", encodeTransform(c.transform))
        if (c.keyframes.isNotEmpty()) {
            put("keyframes", JSONArray(c.keyframes.map { encodeTransformKeyframe(it) }))
        }
        if (c.volumeKeyframes.isNotEmpty()) {
            put("volumeKeys", JSONArray(c.volumeKeyframes.map { JSONObject().put("frame", it.frame).put("db", it.volumeDb.toDouble()).putEase(it.ease) }))
        }
        put("volumeDb", c.volumeDb.toDouble()); put("fadeIn", c.fadeInSeconds.toDouble()); put("fadeOut", c.fadeOutSeconds.toDouble())
        // Pan only when off centre or keyed (older projects have none).
        if (c.pan != 0f) put("pan", c.pan.toDouble())
        if (c.panKeyframes.isNotEmpty()) {
            put("panKeys", JSONArray(c.panKeyframes.map { JSONObject().put("frame", it.frame).put("pan", it.value.toDouble()).putEase(it.ease) }))
        }
        // Fade curves only when bent (older projects have straight fades).
        if (c.fadeInCurve != 0f) put("fadeInCurve", c.fadeInCurve.toDouble())
        if (c.fadeOutCurve != 0f) put("fadeOutCurve", c.fadeOutCurve.toDouble())
        // A clip's own EQ only when it has one (older projects have none).
        if (c.eq != TrackEq.FLAT) put("eq", encodeEq(c.eq))
        c.transitionIn?.let { put("transition", encodeTransition(it)) }
        put("effects", JSONArray(c.effects.map { effect ->
            JSONObject().put("id", effect.id).put("type", effect.type.name).put("amount", effect.amount).put("enabled", effect.enabled).apply {
                // Effect keyframes only where the amount changes over the clip (older projects have none).
                if (effect.keyframes.isNotEmpty()) {
                    put("keys", JSONArray(effect.keyframes.map { JSONObject().put("frame", it.frame).put("amount", it.amount).putEase(it.ease) }))
                }
            }
        }))
        if (c.grade != ColorGrade.NEUTRAL) put("grade", encodeGrade(c.grade))
        // Local grade versions only once the clip has had more than one (older projects have none); the shown one's grade is the live one.
        if (c.gradeVersions.isNotEmpty()) {
            put("gradeVersions", JSONArray(c.gradeVersions.mapIndexed { i, v ->
                JSONObject().put("name", v.name).put("grade", encodeGrade(if (i == c.gradeVersion) c.grade else v.grade))
            }))
            put("gradeVersion", c.gradeVersion)
        }
        // The mask only when set (older projects have none).
        c.mask?.let { put("mask", encodeMask(it)) }
        // The keyer only when on (older projects have none).
        c.chromaKey?.let { k ->
            put("chromaKey", JSONObject().put("color", k.color and 0xFFFFFF).put("similarity", k.similarity.toDouble())
                .put("smoothness", k.smoothness.toDouble()).put("spill", k.spill.toDouble()).apply {
                    // The matte finesse only when used (older projects have none).
                    if (k.cleanBlack > 0f) put("cleanBlack", k.cleanBlack.toDouble())
                    if (k.cleanWhite > 0f) put("cleanWhite", k.cleanWhite.toDouble())
                    if (k.blur > 0f) put("blur", k.blur.toDouble())
                })
        }
        c.title?.let { put("title", encodeTitle(it)) }
        put("link", c.linkId ?: JSONObject.NULL)
        if (!c.enabled) put("enabled", false)
        c.color?.let { put("color", it.name) }
        c.dynamicZoom?.let { zoom ->
            val o = JSONObject().put("direction", zoom.direction.name).put("amount", zoom.amountPercent).put("ease", zoom.ease.name)
            // A centred focus is the default and stays unwritten.
            if (zoom.focusX != ZoomFocusX.CENTER) o.put("focusX", zoom.focusX.name)
            if (zoom.focusY != ZoomFocusY.CENTER) o.put("focusY", zoom.focusY.name)
            put("zoom", o)
        }
        if (c.crossFadeFrames > 0) put("crossFade", c.crossFadeFrames)
        // A sound's channel mapping, only when not as recorded (older projects have none).
        if (c.channels != AudioChannels.STEREO) put("channels", c.channels.name)
        if (c.pitchCents != 0) put("pitch", c.pitchCents)
        if (c.composite != CompositeMode.NORMAL) put("composite", c.composite.name)
        if (c.retime != RetimeProcess.NEAREST) put("retime", c.retime.name)
        // Absent: the clip follows the project's scaling (a project's default, fit, matches what older files meant).
        c.scaling?.let { put("scaling", it.name) }
        c.stabilization?.let { s ->
            put("stab", JSONObject().put("on", s.enabled).put("smooth", s.smooth).put("strength", s.strength).put("zoom", s.zoom))
        }
        // A compound clip: the timeline it plays.
        c.compoundId?.let { put("compound", it) }
        if (c.angle != 0) put("angle", c.angle)
    }

    private fun decodeClip(o: JSONObject): Clip {
        val t = o.optJSONObject("transform")
        return Clip(
            id = o.getString("id"),
            trackId = o.getString("track"),
            kind = enumOr(o.optString("kind"), ClipKind.VIDEO),
            name = o.optString("name"),
            start = o.optInt("start"),
            durationFrames = o.optInt("duration", 1).coerceAtLeast(1),
            assetId = o.optNullableString("asset"),
            sourceInMs = o.optLong("sourceIn"),
            speed = decodeSpeed(o.opt("speed")),
            speedSections = decodeSections(o, o.optInt("duration", 0)),
            reverse = o.optBoolean("reverse"),
            transform = if (t == null) ClipTransform() else decodeTransform(t),
            keyframes = o.optJSONArray("keyframes").objects()
                .mapNotNull(::decodeTransformKeyframe)
                .distinctBy { it.frame }
                .sortedBy { it.frame },
            volumeDb = o.optDouble("volumeDb", 0.0).toFloat(),
            volumeKeyframes = o.optJSONArray("volumeKeys").objects()
                .map { VolumeKeyframe(it.optInt("frame").coerceAtLeast(0), it.optDouble("db", 0.0).toFloat().coerceIn(-60f, 12f), decodeEase(it)) }
                .distinctBy { it.frame }
                .sortedBy { it.frame },
            pan = o.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
            panKeyframes = o.optJSONArray("panKeys").objects()
                .map { ParamKey(it.optInt("frame").coerceAtLeast(0), it.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f), decodeEase(it)) }
                .distinctBy { it.frame }
                .sortedBy { it.frame },
            fadeInSeconds = o.optDouble("fadeIn", 0.0).toFloat(),
            fadeOutSeconds = o.optDouble("fadeOut", 0.0).toFloat(),
            fadeInCurve = o.optDouble("fadeInCurve", 0.0).toFloat().coerceIn(-1f, 1f),
            fadeOutCurve = o.optDouble("fadeOutCurve", 0.0).toFloat().coerceIn(-1f, 1f),
            eq = o.optJSONObject("eq")?.let(::decodeEq) ?: TrackEq.FLAT,
            mask = o.optJSONObject("mask")?.let(::decodeMask),
            chromaKey = o.optJSONObject("chromaKey")?.let { k ->
                ChromaKey(
                    color = k.optInt("color", ChromaKey.GREEN) and 0xFFFFFF,
                    similarity = k.optDouble("similarity", 40.0).toFloat().coerceIn(0f, 100f),
                    smoothness = k.optDouble("smoothness", 30.0).toFloat().coerceIn(0f, 100f),
                    spill = k.optDouble("spill", 30.0).toFloat().coerceIn(0f, 100f),
                    cleanBlack = k.optDouble("cleanBlack", 0.0).toFloat().coerceIn(0f, 100f),
                    cleanWhite = k.optDouble("cleanWhite", 0.0).toFloat().coerceIn(0f, 100f),
                    blur = k.optDouble("blur", 0.0).toFloat().coerceIn(0f, 100f),
                )
            },
            transitionIn = o.optJSONObject("transition")?.let(::decodeTransition),
            effects = o.optJSONArray("effects").objects().map {
                ClipEffect(
                    it.getString("id"), enumOr(it.optString("type"), EffectType.VIGNETTE), it.optInt("amount", 50), it.optBoolean("enabled", true),
                    keyframes = it.optJSONArray("keys").objects()
                        .map { k -> EffectKeyframe(k.optInt("frame").coerceAtLeast(0), k.optInt("amount", 50).coerceIn(0, 100), decodeEase(k)) }
                        .distinctBy { k -> k.frame }
                        .sortedBy { k -> k.frame },
                )
            },
            grade = o.optJSONObject("grade")?.let(::decodeGrade) ?: ColorGrade.NEUTRAL,
            gradeVersions = o.optJSONArray("gradeVersions").objects()
                .map { GradeVersion(it.optString("name", "版本"), it.optJSONObject("grade")?.let(::decodeGrade) ?: ColorGrade.NEUTRAL) }
                .takeIf { it.isNotEmpty() }.orEmpty(),
            gradeVersion = o.optJSONArray("gradeVersions")?.takeIf { it.length() > 0 }
                ?.let { o.optInt("gradeVersion").coerceIn(0, it.length() - 1) } ?: 0,
            title = o.optJSONObject("title")?.let(::decodeTitle),
            crossFadeFrames = o.optInt("crossFade").coerceAtLeast(0),
            dynamicZoom = o.optJSONObject("zoom")?.let {
                DynamicZoom(
                    direction = enumOr(it.optString("direction"), ZoomDirection.IN),
                    amountPercent = it.optInt("amount", DynamicZoom.DEFAULT_AMOUNT).coerceIn(DynamicZoom.MIN_AMOUNT, DynamicZoom.MAX_AMOUNT),
                    ease = enumOr(it.optString("ease"), ZoomEase.LINEAR),
                    focusX = enumOr(it.optString("focusX"), ZoomFocusX.CENTER),
                    focusY = enumOr(it.optString("focusY"), ZoomFocusY.CENTER),
                )
            },
            linkId = o.optNullableString("link"),
            enabled = o.optBoolean("enabled", true),
            color = ClipColor.entries.firstOrNull { it.name == o.optString("color") },
            composite = enumOr(o.optString("composite"), CompositeMode.NORMAL),
            retime = enumOr(o.optString("retime"), RetimeProcess.NEAREST),
            scaling = ClipScaling.entries.firstOrNull { it.name == o.optString("scaling") },
            stabilization = o.optJSONObject("stab")?.let {
                ClipStabilization(
                    enabled = it.optBoolean("on", true),
                    smooth = it.optInt("smooth", ClipStabilization.DEFAULT.smooth).coerceIn(0, 100),
                    strength = it.optInt("strength", ClipStabilization.DEFAULT.strength).coerceIn(0, 100),
                    zoom = it.optBoolean("zoom", true),
                )
            },
            compoundId = o.optNullableString("compound"),
            angle = o.optInt("angle", 0).coerceIn(0, Clip.MAX_ANGLES - 1),
            channels = enumOr(o.optString("channels"), AudioChannels.STEREO),
            pitchCents = o.optInt("pitch", 0).coerceIn(-Clip.MAX_PITCH_CENTS, Clip.MAX_PITCH_CENTS),
        )
    }

    /** A title's words, size and template, and its look only where it differs from the template's (older projects have none). */
    private fun encodeTitle(t: TitleStyle) = JSONObject().put("text", t.text).put("size", t.sizePx).put("template", t.template.name).apply {
        if (t.color != TitleColor.WHITE) put("color", t.color.name)
        if (t.weight != t.template.weight) put("weight", t.weight.name)
        if (t.outline) put("outline", true)
        if (!t.shadow) put("shadow", false)
        if (t.box != t.template.box) put("box", t.box)
        if (t.boxOpacityPercent != TitleStyle.BOX_OPACITY) put("boxOpacity", t.boxOpacityPercent)
        if (t.animation != t.template.animation) put("animation", t.animation.name)
    }

    private fun decodeTitle(o: JSONObject): TitleStyle {
        val template = enumOr(o.optString("template"), TitleTemplate.CENTER)
        return TitleStyle(
            text = o.optString("text"),
            sizePx = o.optInt("size", 72),
            template = template,
            color = enumOr(o.optString("color"), TitleColor.WHITE),
            weight = enumOr(o.optString("weight"), template.weight),
            outline = o.optBoolean("outline", false),
            shadow = o.optBoolean("shadow", true),
            box = o.optBoolean("box", template.box),
            boxOpacityPercent = o.optInt("boxOpacity", TitleStyle.BOX_OPACITY).coerceIn(0, 100),
            animation = enumOr(o.optString("animation"), template.animation),
        )
    }

    /** A transition's kind and length, and its style only where it is set (older projects have none). */
    private fun encodeTransition(t: Transition) = JSONObject().put("type", t.type.name).put("frames", t.frames).apply {
        if (t.ease != TransitionEase.LINEAR) put("ease", t.ease.name)
        if (t.reverse) put("reverse", true)
        if (t.softness > 0) put("softness", t.softness)
        if (t.border > 0) put("border", t.border)
        if (t.borderColor != TitleColor.WHITE) put("borderColor", t.borderColor.name)
        if (t.alignment != TransitionAlignment.START) put("alignment", t.alignment.name)
        if (t.color != Transition.DIP_COLOR) put("color", t.color.name)
        if (t.angle != 0) put("angle", t.angle)
    }

    private fun decodeTransition(o: JSONObject) = Transition(
        type = enumOr(o.optString("type"), TransitionType.CROSS_DISSOLVE),
        frames = o.optInt("frames", 15),
        ease = enumOr(o.optString("ease"), TransitionEase.LINEAR),
        reverse = o.optBoolean("reverse", false),
        softness = o.optInt("softness", 0).coerceIn(0, Transition.MAX_STYLE),
        border = o.optInt("border", 0).coerceIn(0, Transition.MAX_STYLE),
        borderColor = enumOr(o.optString("borderColor"), TitleColor.WHITE),
        // Projects from before alignments start their transitions at the cut.
        alignment = enumOr(o.optString("alignment"), TransitionAlignment.START),
        color = enumOr(o.optString("color"), Transition.DIP_COLOR),
        angle = Transition.normalizedAngle(o.optInt("angle", 0)),
    )

    private fun encodeTransform(t: ClipTransform) = JSONObject().put("scale", t.scalePercent).put("x", t.positionX)
        .put("y", t.positionY).put("rotation", t.rotationDegrees).put("opacity", t.opacityPercent)
        // Cropping [left, right, top, bottom, softness], written only when set (older projects have none).
        .apply {
            if (t.hasCropSettings) {
                put("crop", JSONArray().put(t.cropLeft).put(t.cropRight).put(t.cropTop).put(t.cropBottom).put(t.cropSoftness))
            }
        }

    /**
     * A transform keyframe: its transform, frame and ease, then — only when it keys some parameters apart (rev 162; older
     * projects key all of them) — "params" (the keyed ones by name) and "eases" (a parameter's own ease, by name).
     */
    private fun encodeTransformKeyframe(k: TransformKeyframe): JSONObject = encodeTransform(k.transform).put("frame", k.frame).putEase(k.ease).apply {
        if (k.params != TransformKeyGroup.ALL) put("params", JSONArray(CurveParam.entries.filter { it in k.params }.map { it.name }))
        if (k.paramEases.isNotEmpty()) put("eases", JSONObject().apply { k.paramEases.forEach { (p, e) -> put(p.name, JSONObject().putEase(e)) } })
    }

    private fun decodeTransformKeyframe(o: JSONObject): TransformKeyframe? {
        val names = o.optJSONArray("params")
        val params = if (names == null) TransformKeyGroup.ALL
        else (0 until names.length()).mapNotNull { i -> CurveParam.entries.firstOrNull { it.name == names.optString(i) } }.toSet()
        // A keyframe keying nothing it can read keys nothing: dropped.
        if (params.isEmpty()) return null
        val eases = o.optJSONObject("eases")?.let { e ->
            CurveParam.entries.filter { it in params && e.has(it.name) }.associateWith { decodeEase(e.optJSONObject(it.name) ?: JSONObject()) }
        }.orEmpty()
        return TransformKeyframe(o.optInt("frame").coerceAtLeast(0), decodeTransform(o), decodeEase(o), params, eases)
    }

    /** Keyframe easing, written only where it is not linear (older projects have none). */
    private fun JSONObject.putEase(ease: KeyEase): JSONObject = apply {
        if (ease.slopeIn != 1f) put("slopeIn", ease.slopeIn.toDouble())
        if (ease.slopeOut != 1f) put("slopeOut", ease.slopeOut.toDouble())
    }

    private fun decodeEase(o: JSONObject) = KeyEase(slopeIn = o.slope("slopeIn"), slopeOut = o.slope("slopeOut"))

    private fun JSONObject.slope(key: String): Float =
        optDouble(key, 1.0).toFloat().takeUnless { it.isNaN() }?.coerceIn(0f, KeyEase.MAX_SLOPE) ?: 1f

    private fun decodeTransform(t: JSONObject): ClipTransform {
        val crop = t.optJSONArray("crop")
        fun cropAt(i: Int, control: CropControl) = (crop?.optInt(i) ?: 0).coerceIn(0, control.max)
        return ClipTransform(
            scalePercent = t.optInt("scale", 100),
            positionX = t.optInt("x"),
            positionY = t.optInt("y"),
            rotationDegrees = t.optInt("rotation"),
            opacityPercent = t.optInt("opacity", 100),
            cropLeft = cropAt(0, CropControl.LEFT),
            cropRight = cropAt(1, CropControl.RIGHT),
            cropTop = cropAt(2, CropControl.TOP),
            cropBottom = cropAt(3, CropControl.BOTTOM),
            cropSoftness = cropAt(4, CropControl.SOFTNESS),
        )
    }

    private fun encodeWheel(w: WheelValue) = JSONArray().put(w.x.toDouble()).put(w.y.toDouble()).put(w.master.toDouble())

    private fun decodeWheel(a: JSONArray?): WheelValue =
        if (a == null || a.length() < 3) WheelValue.NEUTRAL
        else WheelValue(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())

    private fun encodeCurve(points: List<CurvePoint>) =
        JSONArray(points.map { JSONArray().put(it.x.toDouble()).put(it.y.toDouble()) })

    private fun decodeCurve(a: JSONArray?): List<CurvePoint> {
        if (a == null || a.length() < 2) return ToneCurves.IDENTITY
        return (0 until a.length()).map { i ->
            val p = a.getJSONArray(i)
            CurvePoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
        }
    }

    private val HUE_CURVE_KEYS = mapOf(
        HueCurve.HUE_HUE to "hueHue", HueCurve.HUE_SAT to "hueSat", HueCurve.HUE_LUM to "hueLum", HueCurve.LUM_SAT to "lumSat",
    )

    /** A hue curve's points in range, in order, one per position (a hue curve's x within the turn). */
    private fun decodeHueCurve(a: JSONArray?, periodic: Boolean): List<CurvePoint> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONArray(i) ?: return@mapNotNull null
            val x = p.optDouble(0, Double.NaN).toFloat()
            val y = p.optDouble(1, Double.NaN).toFloat()
            if (x.isNaN() || y.isNaN() || x < 0f || x > 1f || (periodic && x >= 1f)) null else CurvePoint(x, y.coerceIn(0f, 1f))
        }.distinctBy { it.x }.sortedBy { it.x }
    }

    private fun encodeGrade(g: ColorGrade): JSONObject = JSONObject().apply {
        put("lift", encodeWheel(g.lift)); put("gamma", encodeWheel(g.gamma))
        put("gain", encodeWheel(g.gain)); put("offset", encodeWheel(g.offset))
        put("exposure", g.exposure.toDouble()); put("contrast", g.contrast.toDouble())
        put("saturation", g.saturation.toDouble()); put("hue", g.hue.toDouble())
        put("temperature", g.temperature.toDouble()); put("tint", g.tint.toDouble())
        put("highlights", g.highlights.toDouble()); put("shadows", g.shadows.toDouble())
        // Pivot and Color Boost only when moved (older projects have neither).
        if (g.pivot != ColorGrade.DEFAULT_PIVOT) put("pivot", g.pivot.toDouble())
        if (g.colorBoost != 0f) put("colorBoost", g.colorBoost.toDouble())
        if (g.midDetail != 0f) put("midDetail", g.midDetail.toDouble())
        if (g.temporalNr != 0) put("temporalNr", g.temporalNr)
        put("curves", JSONObject().put("luma", encodeCurve(g.curves.luma)).put("red", encodeCurve(g.curves.red))
            .put("green", encodeCurve(g.curves.green)).put("blue", encodeCurve(g.curves.blue)))
        // Hue curves only where a curve has points (older projects have none).
        if (HueCurve.entries.any { g.hueCurves[it].isNotEmpty() }) {
            put("hueCurves", JSONObject().apply {
                HueCurve.entries.forEach { c -> g.hueCurves[c].takeIf { it.isNotEmpty() }?.let { put(HUE_CURVE_KEYS.getValue(c), encodeCurve(it)) } }
            })
        }
        // The RGB Mixer only once moved off its defaults (older projects have none): rows R/G/B, the switches, the grey's mix.
        if (g.rgbMixer != RgbMixer.NEUTRAL) {
            val m = g.rgbMixer
            fun row(c: ChannelMix) = JSONArray(listOf(c.r.toDouble(), c.g.toDouble(), c.b.toDouble()))
            put("rgbMixer", JSONObject().put("r", row(m.red)).put("g", row(m.green)).put("b", row(m.blue))
                .put("keepLuma", m.preserveLuminance).put("mono", m.monochrome).put("grey", row(m.mono)))
        }
        // The Color Warper's moved points only (older projects have none): [spoke, ring, hue°, saturation].
        if (!g.warper.isIdentity) {
            put("warper", JSONArray(g.warper.moves.map { JSONArray(listOf(it.spoke, it.ring, it.hue.toDouble(), it.sat.toDouble())) }))
        }
        put("look", g.look.id); put("lookStrength", g.lookStrength)
        // An imported LUT by id (its content); the name says which one is missing on another headset.
        g.lut?.let { put("lut", JSONObject().put("id", it.id).put("name", it.name)) }
        g.window?.let { put("window", encodeWindow(it)) }
        // Node 1's keyframes: each its whole correction (without window, keyframes or noise reduction of its own).
        if (g.keyframes.isNotEmpty()) {
            put("keys", JSONArray(g.keyframes.map { JSONObject().put("frame", it.frame).put("grade", encodeGrade(it.grade.primary.colorOnly)).putEase(it.ease) }))
        }
        // Spatial NR only when set (older projects have none).
        if (g.spatialNr != SpatialNoiseReduction.OFF) put("nr", encodeSpatialNr(g.spatialNr))
        // Serial nodes 03 on, only when there are some: each its colour grade and whether it is switched on.
        if (g.serial.isNotEmpty()) {
            put("serial", JSONArray(g.serial.map { JSONObject().put("grade", encodeGrade(it.grade.primary.colorOnly)).put("on", it.enabled) }))
        }
    }

    private fun encodeSpatialNr(n: SpatialNoiseReduction) = JSONObject()
        .put("luma", n.lumaThreshold.toDouble()).put("chroma", n.chromaThreshold.toDouble())
        .put("radius", n.radius.name).put("amount", n.amount.toDouble())

    private fun decodeSpatialNr(o: JSONObject?): SpatialNoiseReduction = if (o == null) SpatialNoiseReduction.OFF else SpatialNoiseReduction(
        lumaThreshold = finiteOr(o.optDouble("luma", 0.0), 0f).coerceIn(0f, 100f),
        chromaThreshold = finiteOr(o.optDouble("chroma", 0.0), 0f).coerceIn(0f, 100f),
        radius = enumOr(o.optString("radius"), NrRadius.MEDIUM),
        amount = finiteOr(o.optDouble("amount", 100.0), 100f).coerceIn(0f, 100f),
    )

    private fun encodeWindow(w: PowerWindow): JSONObject = JSONObject().apply {
        put("shape", w.shape.id)
        put("x", w.centerX.toDouble()); put("y", w.centerY.toDouble())
        put("size", w.size.toDouble()); put("aspect", w.aspect.toDouble())
        put("angle", w.angle.toDouble()); put("softness", w.softness.toDouble())
        put("invert", w.invert)
        put("grade", encodeGrade(w.grade))
        w.qualifier?.let { put("qualifier", encodeQualifier(it)) }
        if (w.keyframes.isNotEmpty()) put("keys", JSONArray(w.keyframes.map { encodeGeometry(it.geometry).put("frame", it.frame).putEase(it.ease) }))
    }

    private fun encodeGeometry(g: WindowGeometry): JSONObject = JSONObject()
        .put("x", g.centerX.toDouble()).put("y", g.centerY.toDouble())
        .put("size", g.size.toDouble()).put("aspect", g.aspect.toDouble())
        .put("angle", g.angle.toDouble()).put("softness", g.softness.toDouble())

    private fun encodeQualifier(q: HslQualifier): JSONObject = JSONObject().apply {
        put("hue", JSONArray(listOf(q.hueCenter.toDouble(), q.hueWidth.toDouble(), q.hueSoft.toDouble())))
        put("sat", JSONArray(listOf(q.satLow.toDouble(), q.satHigh.toDouble(), q.satSoft.toDouble())))
        put("lum", JSONArray(listOf(q.lumLow.toDouble(), q.lumHigh.toDouble(), q.lumSoft.toDouble())))
        put("invert", q.invert)
        // Matte finesse only when used (older projects have none).
        if (q.isRefined) put("finesse", JSONArray(listOf(q.cleanBlack.toDouble(), q.cleanWhite.toDouble(), q.blur.toDouble())))
    }

    private fun decodeQualifier(o: JSONObject): HslQualifier {
        val d = HslQualifier()
        fun triple(key: String, a: Float, b: Float, c: Float): FloatArray {
            val arr = o.optJSONArray(key)
            return floatArrayOf(
                arr?.optDouble(0, a.toDouble())?.toFloat() ?: a,
                arr?.optDouble(1, b.toDouble())?.toFloat() ?: b,
                arr?.optDouble(2, c.toDouble())?.toFloat() ?: c,
            )
        }
        val hue = triple("hue", d.hueCenter, d.hueWidth, d.hueSoft)
        val sat = triple("sat", d.satLow, d.satHigh, d.satSoft)
        val lum = triple("lum", d.lumLow, d.lumHigh, d.lumSoft)
        val finesse = triple("finesse", 0f, 0f, 0f).map { if (it.isNaN()) 0f else it.coerceIn(0f, 100f) }
        return HslQualifier(
            hue[0], hue[1], hue[2], sat[0], sat[1], sat[2], lum[0], lum[1], lum[2], o.optBoolean("invert"),
            cleanBlack = finesse[0], cleanWhite = finesse[1], blur = finesse[2],
        )
    }

    private fun decodeWindow(o: JSONObject): PowerWindow {
        val defaults = PowerWindow()
        return PowerWindow(
            shape = WindowShape.byId(o.optString("shape")),
            centerX = o.optDouble("x", 0.0).toFloat(),
            centerY = o.optDouble("y", 0.0).toFloat(),
            size = o.optDouble("size", defaults.size.toDouble()).toFloat(),
            aspect = o.optDouble("aspect", defaults.aspect.toDouble()).toFloat(),
            angle = o.optDouble("angle", 0.0).toFloat(),
            softness = o.optDouble("softness", defaults.softness.toDouble()).toFloat(),
            invert = o.optBoolean("invert"),
            // A window's grade is one node: a nested window is dropped.
            grade = o.optJSONObject("grade")?.let(::decodeGrade)?.primary ?: ColorGrade.NEUTRAL,
            qualifier = o.optJSONObject("qualifier")?.let(::decodeQualifier),
            keyframes = o.optJSONArray("keys").objects()
                .map { WindowKeyframe(it.optInt("frame").coerceAtLeast(0), decodeGeometry(it, defaults), decodeEase(it)) }
                .distinctBy { it.frame }
                .sortedBy { it.frame },
        )
    }

    /** A clip's mask: a window without a grade or qualifier. */
    private fun encodeMask(w: PowerWindow): JSONObject = encodeWindow(w.copy(grade = ColorGrade.NEUTRAL, qualifier = null)).apply { remove("grade") }

    /** A clip's mask, its values brought into the inspector's ranges; the full frame masks nothing (none). */
    private fun decodeMask(o: JSONObject): PowerWindow? {
        val w = decodeWindow(o).copy(grade = ColorGrade.NEUTRAL, qualifier = null)
        if (w.shape == WindowShape.FULL) return null
        fun clamp(x: PowerWindow) = WindowControl.entries.fold(x) { acc, c -> c.write(acc, c.read(acc)) }
        return clamp(w).copy(keyframes = w.keyframes.map { k -> k.copy(geometry = clamp(w.withGeometry(k.geometry)).geometry) })
    }

    /** A window keyframe's geometry; missing values take [defaults]' (the window's own fields). */
    private fun decodeGeometry(o: JSONObject, defaults: PowerWindow) = WindowGeometry(
        centerX = o.optDouble("x", 0.0).toFloat(),
        centerY = o.optDouble("y", 0.0).toFloat(),
        size = o.optDouble("size", defaults.size.toDouble()).toFloat(),
        aspect = o.optDouble("aspect", defaults.aspect.toDouble()).toFloat(),
        angle = o.optDouble("angle", 0.0).toFloat(),
        softness = o.optDouble("softness", defaults.softness.toDouble()).toFloat(),
    )

    private fun decodeGrade(o: JSONObject): ColorGrade {
        val c = o.optJSONObject("curves")
        return ColorGrade(
            lift = decodeWheel(o.optJSONArray("lift")),
            gamma = decodeWheel(o.optJSONArray("gamma")),
            gain = decodeWheel(o.optJSONArray("gain")),
            offset = decodeWheel(o.optJSONArray("offset")),
            exposure = o.optDouble("exposure", 0.0).toFloat(),
            contrast = o.optDouble("contrast", 1.0).toFloat(),
            saturation = o.optDouble("saturation", 1.0).toFloat(),
            hue = o.optDouble("hue", 0.0).toFloat(),
            temperature = o.optDouble("temperature", 0.0).toFloat(),
            tint = o.optDouble("tint", 0.0).toFloat(),
            highlights = o.optDouble("highlights", 0.0).toFloat(),
            shadows = o.optDouble("shadows", 0.0).toFloat(),
            pivot = o.optDouble("pivot", ColorGrade.DEFAULT_PIVOT.toDouble()).toFloat().coerceIn(0f, 1f),
            colorBoost = o.optDouble("colorBoost", 0.0).toFloat().coerceIn(-100f, 100f),
            midDetail = o.optDouble("midDetail", 0.0).toFloat().coerceIn(-100f, 100f),
            temporalNr = o.optInt("temporalNr", 0).coerceIn(0, 100),
            curves = if (c == null) ToneCurves() else ToneCurves(
                luma = decodeCurve(c.optJSONArray("luma")),
                red = decodeCurve(c.optJSONArray("red")),
                green = decodeCurve(c.optJSONArray("green")),
                blue = decodeCurve(c.optJSONArray("blue")),
            ),
            hueCurves = o.optJSONObject("hueCurves")?.let { h ->
                HueCurve.entries.fold(HueCurves()) { acc, c -> acc.with(c, decodeHueCurve(h.optJSONArray(HUE_CURVE_KEYS.getValue(c)), c.periodic)) }
            } ?: HueCurves(),
            rgbMixer = o.optJSONObject("rgbMixer")?.let { m ->
                fun row(key: String, default: ChannelMix) = m.optJSONArray(key)?.takeIf { it.length() == 3 }?.let {
                    ChannelMix(it.optDouble(0).toFloat(), it.optDouble(1).toFloat(), it.optDouble(2).toFloat())
                } ?: default
                RgbMixer(
                    row("r", RgbMixer.IDENTITY_RED), row("g", RgbMixer.IDENTITY_GREEN), row("b", RgbMixer.IDENTITY_BLUE),
                    m.optBoolean("keepLuma", true), m.optBoolean("mono"), row("grey", RgbMixer.MONO_DEFAULT),
                )
            } ?: RgbMixer.NEUTRAL,
            warper = o.optJSONArray("warper")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONArray(it) }.fold(ColorWarper()) { w, m ->
                    val spoke = m.optInt(0, -1)
                    val ring = m.optInt(1, -1)
                    if (spoke !in 0 until ColorWarper.SPOKES || ring !in 0 until ColorWarper.RINGS) w
                    else w.with(spoke, ring, m.optDouble(2, 0.0).toFloat(), m.optDouble(3, 0.0).toFloat())
                }
            } ?: ColorWarper.NEUTRAL,
            look = Look.byId(o.optString("look")),
            lookStrength = o.optInt("lookStrength", 80),
            lut = o.optJSONObject("lut")?.let { l ->
                l.optString("id").takeIf { it.isNotBlank() }?.let { LutRef(it, l.optString("name").ifBlank { it }) }
            },
            window = o.optJSONObject("window")?.let(::decodeWindow),
            keyframes = o.optJSONArray("keys").objects()
                .mapNotNull { k ->
                    val grade = k.optJSONObject("grade")?.let(::decodeGrade)?.primary ?: return@mapNotNull null
                    GradeKeyframe(k.optInt("frame").coerceAtLeast(0), grade, decodeEase(k))
                }
                .distinctBy { it.frame }
                .sortedBy { it.frame },
            spatialNr = decodeSpatialNr(o.optJSONObject("nr")),
            serial = o.optJSONArray("serial").objects().mapNotNull { n ->
                val grade = n.optJSONObject("grade")?.let(::decodeGrade)?.primary?.colorOnly ?: return@mapNotNull null
                SerialNode(grade, n.optBoolean("on", true))
            }.take(GradeNode.MAX_SERIAL),
        )
    }

    private fun encodeMixer(m: MixerState) = JSONObject().apply {
        put("master", m.masterVolumeDb.toDouble())
        put("channels", JSONArray(m.channels.map {
            JSONObject().put("track", it.trackId).put("mute", it.mute).put("solo", it.solo)
                .put("pan", it.pan.toDouble()).put("volumeDb", it.volumeDb.toDouble())
                .apply { if (it.eq != TrackEq.FLAT) put("eq", encodeEq(it.eq)) }
                .apply { if (it.dynamics != TrackDynamics.OFF) put("dyn", encodeDynamics(it.dynamics)) }
                .apply { if (it.noiseReduction != TrackNoiseReduction.OFF) put("nr", encodeNoiseReduction(it.noiseReduction)) }
                .apply { if (it.ducking != TrackDucking.OFF) put("duck", encodeDucking(it.ducking)) }
                .apply {
                    if (it.deEsser != TrackDeEsser.OFF) put("deEss", JSONObject().put("on", it.deEsser.enabled).put("amount", it.deEsser.amount).put("band", it.deEsser.band.name))
                }
                .apply {
                    val r = it.reverb
                    if (r != TrackReverb.OFF) {
                        put("reverb", JSONObject().put("on", r.enabled).put("decay", Math.round(r.decaySeconds * 100) / 100.0).put("pre", r.preDelayMs)
                            .put("damp", r.damping).put("mix", r.mix))
                    }
                }
                .apply { it.bus?.let { b -> put("bus", b) } }
        }))
        if (m.buses.isNotEmpty()) {
            put("buses", JSONArray(m.buses.map { b ->
                JSONObject().put("id", b.id).put("name", b.name).put("volumeDb", b.volumeDb.toDouble()).put("mute", b.mute).put("solo", b.solo)
                    .apply { if (b.eq != TrackEq.FLAT) put("eq", encodeEq(b.eq)) }
            }))
        }
    }

    private fun encodeDucking(d: TrackDucking) = JSONObject().apply {
        put("on", d.enabled)
        put("key", d.keyTrackId ?: JSONObject.NULL)
        put("depth", d.depthDb.toDouble())
        put("thr", d.thresholdDb.toDouble())
        put("atk", d.attackMs.toDouble())
        put("rel", d.releaseMs.toDouble())
    }

    /** Ducking as written by [encodeDucking]; missing values take the defaults, out-of-range ones are clamped. */
    private fun decodeDucking(o: JSONObject): TrackDucking {
        val d = TrackDucking.OFF
        fun f(name: String, control: DuckingControl): Float {
            val v = o.optDouble(name, control.read(d).toDouble()).toFloat()
            return if (v.isNaN()) control.read(d) else v.coerceIn(control.min, control.max)
        }
        return TrackDucking(
            enabled = o.optBoolean("on", true),
            keyTrackId = if (o.isNull("key")) null else o.optString("key").ifEmpty { null },
            depthDb = f("depth", DuckingControl.DEPTH),
            thresholdDb = f("thr", DuckingControl.THRESHOLD),
            attackMs = f("atk", DuckingControl.ATTACK),
            releaseMs = f("rel", DuckingControl.RELEASE),
        )
    }

    private fun encodeEq(eq: TrackEq) = JSONObject().apply {
        put("enabled", eq.enabled)
        put("lowCut", JSONArray(listOf(eq.lowCut, eq.lowCutHz.toDouble())))
        put("low", JSONArray(listOf(eq.lowHz.toDouble(), eq.lowDb.toDouble())))
        put("mid", JSONArray(listOf(eq.midHz.toDouble(), eq.midDb.toDouble(), eq.midQ.toDouble())))
        put("high", JSONArray(listOf(eq.highHz.toDouble(), eq.highDb.toDouble())))
    }

    private fun decodeEq(o: JSONObject): TrackEq {
        val d = TrackEq.FLAT
        fun JSONArray?.f(i: Int, fallback: Float) = this?.optDouble(i, fallback.toDouble())?.toFloat() ?: fallback
        val cut = o.optJSONArray("lowCut")
        val low = o.optJSONArray("low")
        val mid = o.optJSONArray("mid")
        val high = o.optJSONArray("high")
        return TrackEq(
            enabled = o.optBoolean("enabled", true),
            lowCut = cut?.optBoolean(0) ?: false,
            lowCutHz = cut.f(1, d.lowCutHz),
            lowHz = low.f(0, d.lowHz), lowDb = low.f(1, d.lowDb),
            midHz = mid.f(0, d.midHz), midDb = mid.f(1, d.midDb), midQ = mid.f(2, d.midQ),
            highHz = high.f(0, d.highHz), highDb = high.f(1, d.highDb),
        )
    }

    private fun encodeSubtitleStyle(s: SubtitleStyle) = JSONObject()
        .put("size", s.sizePx).put("color", s.color.name).put("bold", s.bold).put("outline", s.outline)
        .put("box", s.box).put("boxOpacity", s.boxOpacityPercent).put("margin", s.marginPercent)

    private fun decodeSubtitleStyle(o: JSONObject): SubtitleStyle {
        val d = SubtitleStyle.DEFAULT
        fun JSONObject.int(key: String, control: SubtitleStyleControl, fallback: Int) =
            optInt(key, fallback).coerceIn(control.min, control.max)
        return SubtitleStyle(
            sizePx = o.int("size", SubtitleStyleControl.SIZE, d.sizePx),
            color = enumOr(o.optString("color"), d.color),
            bold = o.optBoolean("bold", d.bold),
            outline = o.optBoolean("outline", d.outline),
            box = o.optBoolean("box", d.box),
            boxOpacityPercent = o.int("boxOpacity", SubtitleStyleControl.BOX_OPACITY, d.boxOpacityPercent),
            marginPercent = o.int("margin", SubtitleStyleControl.MARGIN, d.marginPercent),
        )
    }

    private fun encodeNoiseReduction(nr: TrackNoiseReduction) = JSONObject()
        .put("on", nr.enabled).put("reduction", nr.reductionDb.toDouble()).put("threshold", nr.thresholdDb.toDouble())

    /** Noise reduction as written by [encodeNoiseReduction]; missing values take the defaults, out-of-range ones are clamped. */
    private fun decodeNoiseReduction(o: JSONObject): TrackNoiseReduction {
        val d = TrackNoiseReduction.OFF
        return TrackNoiseReduction(
            enabled = o.optBoolean("on", d.enabled),
            reductionDb = finiteOr(o.optDouble("reduction"), d.reductionDb).coerceIn(NoiseReductionControl.REDUCTION.min, NoiseReductionControl.REDUCTION.max),
            thresholdDb = finiteOr(o.optDouble("threshold"), d.thresholdDb).coerceIn(NoiseReductionControl.THRESHOLD.min, NoiseReductionControl.THRESHOLD.max),
        )
    }

    private fun finiteOr(value: Double, fallback: Float): Float = value.takeIf { it.isFinite() }?.toFloat() ?: fallback

    private fun encodeDynamics(d: TrackDynamics) = JSONObject().apply {
        put("enabled", d.enabled)
        put("comp", JSONArray(listOf(d.compressor, d.thresholdDb.toDouble(), d.ratio.toDouble(), d.attackMs.toDouble(), d.releaseMs.toDouble(), d.makeupDb.toDouble())))
        put("limit", JSONArray(listOf(d.limiter, d.ceilingDb.toDouble())))
        // The gate only once it has been touched (older projects have none).
        val off = TrackDynamics.OFF
        if (d.gate || d.gateThresholdDb != off.gateThresholdDb || d.gateRangeDb != off.gateRangeDb || d.gateHoldMs != off.gateHoldMs || d.gateReleaseMs != off.gateReleaseMs) {
            put("gate", JSONArray(listOf(d.gate, d.gateThresholdDb.toDouble(), d.gateRangeDb.toDouble(), d.gateHoldMs.toDouble(), d.gateReleaseMs.toDouble())))
        }
    }

    /** Dynamics as written by [encodeDynamics]; missing values take the defaults, out-of-range ones are clamped. */
    private fun decodeDynamics(o: JSONObject): TrackDynamics {
        val d = TrackDynamics.OFF
        fun JSONArray?.f(i: Int, control: DynamicsControl) =
            (this?.optDouble(i, control.read(d).toDouble())?.toFloat() ?: control.read(d)).takeUnless { it.isNaN() }?.coerceIn(control.min, control.max)
                ?: control.read(d)
        val comp = o.optJSONArray("comp")
        val limit = o.optJSONArray("limit")
        val gate = o.optJSONArray("gate")
        return TrackDynamics(
            enabled = o.optBoolean("enabled", true),
            compressor = comp?.optBoolean(0) ?: false,
            thresholdDb = comp.f(1, DynamicsControl.THRESHOLD),
            ratio = comp.f(2, DynamicsControl.RATIO),
            attackMs = comp.f(3, DynamicsControl.ATTACK),
            releaseMs = comp.f(4, DynamicsControl.RELEASE),
            makeupDb = comp.f(5, DynamicsControl.MAKEUP),
            limiter = limit?.optBoolean(0) ?: false,
            ceilingDb = limit.f(1, DynamicsControl.CEILING),
            gate = gate?.optBoolean(0) ?: false,
            gateThresholdDb = gate.f(1, DynamicsControl.GATE_THRESHOLD),
            gateRangeDb = gate.f(2, DynamicsControl.GATE_RANGE),
            gateHoldMs = gate.f(3, DynamicsControl.GATE_HOLD),
            gateReleaseMs = gate.f(4, DynamicsControl.GATE_RELEASE),
        )
    }

    private fun decodeMixer(o: JSONObject) = MixerState(
        channels = o.optJSONArray("channels").objects().map {
            MixerChannel(
                trackId = it.getString("track"),
                mute = it.optBoolean("mute"),
                solo = it.optBoolean("solo"),
                pan = it.optDouble("pan", 0.0).toFloat(),
                volumeDb = it.optDouble("volumeDb", 0.0).toFloat(),
                eq = it.optJSONObject("eq")?.let(::decodeEq) ?: TrackEq.FLAT,
                dynamics = it.optJSONObject("dyn")?.let(::decodeDynamics) ?: TrackDynamics.OFF,
                noiseReduction = it.optJSONObject("nr")?.let(::decodeNoiseReduction) ?: TrackNoiseReduction.OFF,
                ducking = it.optJSONObject("duck")?.let(::decodeDucking) ?: TrackDucking.OFF,
                deEsser = it.optJSONObject("deEss")?.let { d ->
                    TrackDeEsser(d.optBoolean("on", true), d.optInt("amount").coerceIn(0, 100), enumOr(d.optString("band"), DeEssBand.MID))
                } ?: TrackDeEsser.OFF,
                reverb = it.optJSONObject("reverb")?.let { r ->
                    TrackReverb(
                        r.optBoolean("on", true),
                        r.optDouble("decay", TrackReverb.OFF.decaySeconds.toDouble()).toFloat().takeIf { d -> d.isFinite() } ?: TrackReverb.OFF.decaySeconds,
                        r.optInt("pre", TrackReverb.OFF.preDelayMs),
                        r.optInt("damp", TrackReverb.OFF.damping),
                        r.optInt("mix", 0),
                    ).clamped()
                } ?: TrackReverb.OFF,
                bus = it.optNullableString("bus"),
            )
        },
        masterVolumeDb = o.optDouble("master", 0.0).toFloat(),
        buses = o.optJSONArray("buses").objects().mapNotNull { b ->
            val id = b.optString("id").takeIf { it.matches(BUS_ID) } ?: return@mapNotNull null
            MixerBus(
                id = id,
                name = b.optString("name").ifBlank { "总线 ${id.drop(1)}" },
                volumeDb = b.optDouble("volumeDb", 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(MixerState.FADER_FLOOR_DB, 12f) ?: 0f,
                mute = b.optBoolean("mute"),
                solo = b.optBoolean("solo"),
                eq = b.optJSONObject("eq")?.let(::decodeEq) ?: TrackEq.FLAT,
            )
        }.distinctBy { it.id }.take(MixerState.MAX_BUSES),
    ).let { m -> m.copy(channels = m.channels.map { c -> if (c.bus != null && m.bus(c.bus) == null) c.copy(bus = null) else c }) }

    private fun encodeRender(r: RenderSettings) = JSONObject().apply {
        put("fileName", r.fileName); put("preset", r.preset?.name ?: JSONObject.NULL); put("codec", r.codec.name)
        put("resolution", r.resolution.name); put("fps", r.fps); put("quality", r.quality.name)
        put("audio", r.includeAudio); put("range", r.range.name)
        r.loudnessTargetLufs?.let { put("loudness", it.toDouble()) }
        if (r.exportSubtitles) put("srt", true)
        if (r.subtitleFormat != SubtitleFileFormat.SRT) put("subtitleFormat", r.subtitleFormat.name)
        // Only an audio-only export says so (older projects render movies).
        if (!r.includeVideo) put("video", false)
        // WAV (rev 153): only when chosen, with its rate and depth.
        if (r.audioFormat != AudioExportFormat.AAC) {
            put("audioFormat", r.audioFormat.name); put("wavRate", r.wavSampleRate); put("wavBits", r.wavBits)
        }
        if (r.individualClips) put("individual", true)
    }

    private fun decodeRender(o: JSONObject) = RenderSettings(
        fileName = o.optString("fileName"),
        preset = o.optNullableString("preset")?.let { name -> RenderPreset.entries.firstOrNull { it.name == name } },
        codec = enumOr(o.optString("codec"), VideoCodec.H264),
        resolution = enumOr(o.optString("resolution"), RenderResolution.HD_1080),
        fps = o.optInt("fps", 30),
        quality = enumOr(o.optString("quality"), RenderQuality.HIGH),
        includeAudio = o.optBoolean("audio", true),
        range = enumOr(o.optString("range"), RenderRange.WHOLE_TIMELINE),
        loudnessTargetLufs = o.optDouble("loudness", Double.NaN).toFloat().takeUnless { it.isNaN() }?.coerceIn(-40f, -5f),
        exportSubtitles = o.optBoolean("srt", false),
        subtitleFormat = enumOr(o.optString("subtitleFormat"), SubtitleFileFormat.SRT),
        includeVideo = o.optBoolean("video", true),
        audioFormat = enumOr(o.optString("audioFormat"), AudioExportFormat.AAC),
        wavSampleRate = o.optInt("wavRate", 48_000).takeIf { it in RenderSettings.WAV_RATES } ?: 48_000,
        wavBits = o.optInt("wavBits", 24).takeIf { it in RenderSettings.WAV_BITS } ?: 24,
        individualClips = o.optBoolean("individual", false),
    )

    private fun encodeJob(j: RenderJob) = JSONObject().apply {
        put("id", j.id); put("fileName", j.fileName); put("settings", encodeRender(j.settings))
        put("timelineId", j.timelineId); put("timelineName", j.timelineName)
        j.clipId?.let { put("clip", it) }
        j.retryOf?.let { put("retryOf", it) }
        j.queuedRange?.let { marks ->
            put("queuedRange", JSONObject().apply {
                marks.inFrame?.let { put("in", it) }
                marks.outFrame?.let { put("out", it) }
            })
        }
        put("status", j.status.name); put("progress", j.progress.toDouble())
        put("output", j.outputUri ?: JSONObject.NULL); put("bytes", j.outputBytes)
        put("error", j.errorMessage ?: JSONObject.NULL)
    }

    private fun decodeJob(o: JSONObject): RenderJob {
        val status = enumOr(o.optString("status"), RenderStatus.QUEUED)
        // A render cannot survive a restart: anything that was running is back in the queue.
        val restored = if (status == RenderStatus.RENDERING) RenderStatus.QUEUED else status
        return RenderJob(
            id = o.getString("id"),
            fileName = o.optString("fileName"),
            settings = o.optJSONObject("settings")?.let(::decodeRender) ?: RenderSettings(o.optString("fileName")),
            status = restored,
            progress = if (restored == RenderStatus.QUEUED) 0f else o.optDouble("progress", 0.0).toFloat(),
            outputUri = o.optNullableString("output"),
            outputBytes = o.optLong("bytes"),
            errorMessage = o.optNullableString("error"),
            timelineId = o.optString("timelineId").ifEmpty { FIRST_TIMELINE_ID },
            timelineName = o.optString("timelineName").ifEmpty { FIRST_TIMELINE_NAME },
            clipId = o.optNullableString("clip"),
            retryOf = o.optNullableString("retryOf"),
            queuedRange = o.optJSONObject("queuedRange")?.let { marks ->
                if (!marks.has("in") && !marks.has("out")) return@let TimelineMarks()
                val start = marks.optInt("in", -1)
                val end = marks.optInt("out", -1)
                if (start >= 0 && end > start) TimelineMarks(start, end) else null
            },
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
