package com.example.prismstudio.p40b43a88.data.persistence

import com.example.prismstudio.p40b43a88.domain.model.UserBin
import com.example.prismstudio.p40b43a88.domain.model.GradeVersion
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.TrackNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.EffectKeyframe
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.model.ClipColor
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.CurvePoint
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.Look
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.MarkerColor
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.TrackDynamics
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.GalleryStill
import com.example.prismstudio.p40b43a88.domain.model.GradeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.LutRef
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.HueCurves
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.ProjectFormat
import com.example.prismstudio.p40b43a88.domain.model.RenderJob
import com.example.prismstudio.p40b43a88.domain.model.RenderPreset
import com.example.prismstudio.p40b43a88.domain.model.RenderQuality
import com.example.prismstudio.p40b43a88.domain.model.RenderRange
import com.example.prismstudio.p40b43a88.domain.model.RenderResolution
import com.example.prismstudio.p40b43a88.domain.model.RenderSettings
import com.example.prismstudio.p40b43a88.domain.model.RenderStatus
import com.example.prismstudio.p40b43a88.domain.model.SubtitleColor
import com.example.prismstudio.p40b43a88.domain.model.SubtitleStyle
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.TitleAnimation
import com.example.prismstudio.p40b43a88.domain.model.SubtitleFileFormat
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.TitleWeight
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.model.ToneCurves
import com.example.prismstudio.p40b43a88.domain.model.Track
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey

class ProjectJsonCodecTest {

    private val grade = ColorGrade(
        lift = WheelValue(-0.1f, 0.05f, -0.02f),
        gamma = WheelValue(0.12f, -0.3f, 0.04f),
        gain = WheelValue(0.2f, 0.1f, 0.15f),
        offset = WheelValue(0f, -0.07f, 0.01f),
        exposure = 0.35f,
        contrast = 1.2f,
        saturation = 0.8f,
        hue = -12f,
        temperature = 15f,
        tint = -5f,
        highlights = -20f,
        shadows = 30f,
        curves = ToneCurves(
            luma = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.42f, 0.5f), CurvePoint(1f, 0.97f)),
            red = listOf(CurvePoint(0f, 0f), CurvePoint(0.6f, 0.7f), CurvePoint(1f, 1f)),
        ),
        look = Look.TEAL_ORANGE,
        lookStrength = 65,
    )

    private val media = listOf(
        assetOf("cam", durationMs = 12_345, name = "A001.mov", poster = PosterColors(0xFF112233, 0xFF445566, 0xFF778899))
            .copy(frameRate = 29.97f, sizeBytes = 987_654_321L),
        assetOf("still", kind = MediaKind.IMAGE, name = "poster.png"),
        assetOf("song", kind = MediaKind.AUDIO, durationMs = 181_000, name = "song.m4a"),
    )

    private val clips = listOf(
        Clip(
            id = "c-1", trackId = Timeline.V1, kind = ClipKind.VIDEO, name = "A001", start = 0, durationFrames = 120,
            assetId = "cam", sourceInMs = 1_500, speed = ClipSpeed.of(PlaybackSpeed.HALF), reverse = true,
            transform = ClipTransform(scalePercent = 120, positionX = -40, positionY = 25, rotationDegrees = -15, opacityPercent = 80),
            keyframes = listOf(
                TransformKeyframe(0, ClipTransform(scalePercent = 100)),
                TransformKeyframe(
                    45,
                    ClipTransform(scalePercent = 140, positionX = 200, positionY = -90, rotationDegrees = 12, opacityPercent = 60),
                    KeyEase(slopeIn = 0f, slopeOut = 1.5f),
                ),
            ),
            volumeDb = -3.5f, fadeInSeconds = 0.5f, fadeOutSeconds = 1.25f,
            transitionIn = Transition(TransitionType.WIPE, 12),
            effects = listOf(ClipEffect("fx-1", EffectType.GLOW, 75, enabled = false), ClipEffect("fx-2", EffectType.FILM_GRAIN)),
            grade = grade, linkId = "l-1",
        ),
        Clip(
            id = "c-2", trackId = Timeline.A1, kind = ClipKind.AUDIO, name = "A001", start = 0, durationFrames = 120,
            assetId = "cam", sourceInMs = 1_500, speed = ClipSpeed.of(PlaybackSpeed.HALF), volumeDb = 4f, fadeOutSeconds = 2f, linkId = "l-1",
        ),
        Clip(id = "c-3", trackId = Timeline.V1, kind = ClipKind.VIDEO, name = "poster", start = 150, durationFrames = 150, assetId = "still"),
        Clip(
            id = "t-1", trackId = Timeline.V2, kind = ClipKind.TITLE, name = "标题：Hello", start = 30, durationFrames = 90,
            title = TitleStyle("Hello\nWorld", 88, TitleTemplate.LOWER_THIRD), transform = ClipTransform(positionY = 300),
        ),
        Clip(id = "c-4", trackId = "a3", kind = ClipKind.AUDIO, name = "song", start = 10, durationFrames = 400, assetId = "song", speed = ClipSpeed.of(PlaybackSpeed.QUADRUPLE), volumeKeyframes = listOf(VolumeKeyframe(0, -3f, KeyEase(slopeOut = 0f)), VolumeKeyframe(120, -18.5f))),
    )

    private val tracks = listOf(
        Track(Timeline.V2, "V2", TrackKind.VIDEO, locked = true),
        Track(Timeline.V1, "V1", TrackKind.VIDEO),
        Track(Timeline.A1, "A1", TrackKind.AUDIO),
        Track(Timeline.A2, "A2", TrackKind.AUDIO, enabled = false),
        Track("a3", "Music", TrackKind.AUDIO),
        Track("st1", "ST1", TrackKind.SUBTITLE, enabled = false),
    )

    private val render = RenderSettings(
        fileName = "Trip_4K", preset = RenderPreset.YOUTUBE_4K, codec = VideoCodec.H265, resolution = RenderResolution.UHD_4K,
        fps = 60, quality = RenderQuality.BEST, includeAudio = false, range = RenderRange.IN_TO_OUT, loudnessTargetLufs = -16f,
        exportSubtitles = true, subtitleFormat = SubtitleFileFormat.WEBVTT,
    )

    private val jobs = listOf(
        RenderJob("j-1", "Trip.mp4", RenderSettings("Trip"), RenderStatus.DONE, 1f, "content://media/out/1", 52_428_800),
        RenderJob("j-2", "Trip (2).mp4", render.copy(fileName = "Trip (2)", preset = null), RenderStatus.FAILED, 0.4f,
            errorMessage = "失败：磁盘已满", queuedRange = TimelineMarks(30, 75)),
        RenderJob("j-3", "Trip (3).mp4", RenderSettings("Trip (3)", fps = 24)),
        // The mix alone (Export Video off) keeps saying so.
        RenderJob("j-4", "Trip.m4a", RenderSettings("Trip", includeVideo = false, loudnessTargetLufs = -16f)),
    )

    private val document = ProjectDocument(
        id = "p-1",
        name = "Summer · Trip",
        format = ProjectFormat(3840, 2160, 60),
        createdAtMillis = 1_700_000_000_000L,
        modifiedAtMillis = 1_700_000_123_456L,
        media = media,
        timeline = Timeline(tracks, clips, listOf(Marker("m-1", 30, "标记 1"), Marker("m-2", 240, "Chorus"))),
        mixer = MixerState(
            listOf(
                MixerChannel(
                    Timeline.A1, mute = true, pan = -0.25f, volumeDb = -6f,
                    dynamics = TrackDynamics(compressor = true, thresholdDb = -24.5f, ratio = 3f, attackMs = 2.5f, releaseMs = 220f, makeupDb = 4f, limiter = true, ceilingDb = -1.5f),
                ),
                MixerChannel(
                    Timeline.A2, solo = true, volumeDb = 2.5f,
                    eq = TrackEq(enabled = false, lowCut = true, lowCutHz = 90f, lowDb = 3.5f, midHz = 2500f, midDb = -4f, midQ = 2.2f, highDb = 6f),
                    ducking = TrackDucking(keyTrackId = Timeline.A1, depthDb = 9.5f, thresholdDb = -35f, attackMs = 120f, releaseMs = 1500f),
                ),
                MixerChannel("a3"),
            ),
            masterVolumeDb = -2.5f,
        ),
        render = render,
        jobs = jobs,
    )

    @Test
    fun `a rich project survives an encode decode round trip unchanged`() {
        val decoded = ProjectJsonCodec.decode(ProjectJsonCodec.encode(document))

        assertEquals(document.media, decoded.media)
        assertEquals(document.timeline, decoded.timeline)
        assertEquals(document.mixer, decoded.mixer)
        assertEquals(document.render, decoded.render)
        assertEquals(document.jobs, decoded.jobs)
        assertEquals(document, decoded)
    }

    @Test
    fun `keyframe easing is written only where a keyframe is not linear, and older files load as linear`() {
        val clips = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips")
        val keys = clips.getJSONObject(0).getJSONArray("keyframes")
        assertFalse(keys.getJSONObject(0).has("slopeIn") || keys.getJSONObject(0).has("slopeOut"))
        assertEquals(0.0, keys.getJSONObject(1).getDouble("slopeIn"), 0.0)

        val legacy = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"a1","kind":"AUDIO"}],"clips":[{"id":"c","track":"a1","kind":"AUDIO",
               "volumeKeys":[{"frame":0,"db":-6},{"frame":30,"db":0,"slopeIn":"steep"},{"frame":60,"db":-3,"slopeOut":9}]}]}}""",
        ).timeline.clips.single().volumeKeyframes
        assertEquals(listOf(KeyEase.LINEAR, KeyEase.LINEAR, KeyEase(slopeOut = KeyEase.MAX_SLOPE)), legacy.map { it.ease })
    }

    @Test
    fun `a power window and its grade survive a round trip, sparse windows load with defaults`() {
        val windowed = grade.copy(
            window = PowerWindow(WindowShape.GRADIENT, 0.2f, -0.4f, 0.8f, 1.5f, 30f, 0.6f, invert = true, grade = ColorGrade(exposure = -0.5f, look = Look.MONO)),
        )
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = windowed)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))

        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1",
               "grade":{"window":{"shape":"star","grade":{"saturation":0,"window":{"shape":"circle"}}}}}]}}""",
        ).timeline.clips.single().grade
        assertEquals("unknown shapes are circles; a window's grade has no window", ColorGrade(window = PowerWindow(grade = ColorGrade(saturation = 0f))), sparse)
    }

    @Test
    fun `a duration marker survives a round trip, a point marker writes none, and a negative one loads as a point`() {
        val doc = document.copy(timeline = document.timeline.copy(markers = listOf(Marker("m1", 30, "开场", durationFrames = 90), Marker("m2", 200, "点"))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        val written = org.json.JSONObject(json).getJSONObject("timeline").getJSONArray("markers")
        assertEquals(listOf(90, -1), List(written.length()) { written.getJSONObject(it).optInt("duration", -1) })
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"markers":[{"id":"m","frame":10,"label":"x","duration":-4}]}}""",
        ).timeline.markers.single()
        assertEquals(0, odd.durationFrames)
    }

    @Test
    fun `a keyer survives a round trip, none is written while it is off, and odd values are held in range`() {
        val clip = document.timeline.clips.first()
        val key = ChromaKey(color = 0x3A9D5C, similarity = 35f, smoothness = 12.5f, spill = 60f, cleanBlack = 20f, cleanWhite = 10f, blur = 40f)
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(chromaKey = key)))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("chromaKey"))
        val plain = document.copy(timeline = document.timeline.replaceClip(clip.copy(chromaKey = ChromaKey())))
        assertFalse("no matte finesse written while it is unused", ProjectJsonCodec.encode(plain).contains("cleanBlack"))
        val odd = org.json.JSONObject(json)
        odd.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("chromaKey").put("similarity", 400).put("color", -1)
        val decoded = ProjectJsonCodec.decode(odd.toString()).timeline.clips.first().chromaKey!!
        assertEquals(100f to 0xFFFFFF, decoded.similarity to decoded.color)
    }

    @Test
    fun `a WAV export's settings survive a round trip, an AAC one writes none, and odd values fall back`() {
        val wav = document.render.copy(includeVideo = false, audioFormat = com.example.prismstudio.p40b43a88.domain.model.AudioExportFormat.WAV, wavSampleRate = 44_100, wavBits = 16)
        val doc = document.copy(render = wav)
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse(ProjectJsonCodec.encode(document).contains("audioFormat"))
        val odd = org.json.JSONObject(ProjectJsonCodec.encode(doc))
        odd.getJSONObject("render").put("wavRate", 12_345).put("wavBits", 7)
        val read = ProjectJsonCodec.decode(odd.toString()).render
        assertEquals(48_000 to 24, read.wavSampleRate to read.wavBits)
    }

    @Test
    fun `a track's reverb survives a round trip, none is written while it is off, and odd values are held in range`() {
        val hall = com.example.prismstudio.p40b43a88.domain.model.TrackReverb.OFF.withRoom(com.example.prismstudio.p40b43a88.domain.model.ReverbRoom.HALL)
        val doc = document.copy(mixer = document.mixer.replace(document.mixer.channels.first().copy(reverb = hall)))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("reverb"))
        assertTrue("the decay as set, not as a float widens", json.contains("\"decay\":2.2,"))
        val odd = org.json.JSONObject(json)
        odd.getJSONObject("mixer").getJSONArray("channels").getJSONObject(0).put("reverb", org.json.JSONObject("""{"decay":99,"pre":-5,"damp":300,"mix":"x"}"""))
        val read = ProjectJsonCodec.decode(odd.toString()).mixer.channels.first().reverb
        assertEquals(com.example.prismstudio.p40b43a88.domain.model.TrackReverb(true, 6f, 0, 100, 0), read)
    }

    @Test
    fun `a clip's pitch survives a round trip, none is written at the recorded pitch, and odd values are held in range`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(pitchCents = -350)))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("\"pitch\""))
        val odd = org.json.JSONObject(json)
        odd.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).put("pitch", 9_999)
        assertEquals(1200, ProjectJsonCodec.decode(odd.toString()).timeline.clips.first().pitchCents)
    }

    @Test
    fun `fade curves survive a round trip, straight fades write none, and odd values are held in range`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(fadeInSeconds = 1f, fadeInCurve = 0.45f, fadeOutCurve = -1f)))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("fadeInCurve"))
        assertFalse(ProjectJsonCodec.encode(document).contains("fadeOutCurve"))
        val odd = org.json.JSONObject(json)
        odd.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).put("fadeInCurve", 7.0)
        assertEquals(1f, ProjectJsonCodec.decode(odd.toString()).timeline.clips.first().fadeInCurve, 0f)
    }

    @Test
    fun `a sound clip's own equaliser survives a round trip, and is written only when it has one`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(eq = EqPreset.PODCAST.eq.copy(enabled = false))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("\"eq\":{\"enabled\":true,\"lowCut\":false"))
    }

    @Test
    fun `pivot and colour boost survive a round trip, are written only when moved, and odd values are held in range`() {
        val clip = document.timeline.clips.first { it.kind == ClipKind.VIDEO }
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = ColorGrade(contrast = 1.3f, pivot = 0.3f, colorBoost = 45f))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        val plain = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = ColorGrade(contrast = 1.3f))))
        assertFalse(ProjectJsonCodec.encode(plain).contains("pivot"))
        assertFalse(ProjectJsonCodec.encode(plain).contains("colorBoost"))
        val odd = ProjectJsonCodec.decode(json.replace("\"colorBoost\":45", "\"colorBoost\":400"))
        assertEquals(100f, odd.timeline.clip(clip.id)!!.grade.colorBoost)
    }

    @Test
    fun `mid detail survives a round trip, is written only when set, and odd values are held in range`() {
        val clip = document.timeline.clips.first { it.kind == ClipKind.VIDEO }
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = ColorGrade(midDetail = -35f))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("midDetail"))
        assertEquals(100f, ProjectJsonCodec.decode(json.replace("\"midDetail\":-35", "\"midDetail\":250")).timeline.clip(clip.id)!!.grade.midDetail)
    }

    @Test
    fun `temporal noise reduction survives a round trip, is written only when set, and odd values are held in range`() {
        val clip = document.timeline.clips.first { it.kind == ClipKind.VIDEO }
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = ColorGrade(temporalNr = 65))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("temporalNr"))
        assertEquals(100, ProjectJsonCodec.decode(json.replace("\"temporalNr\":65", "\"temporalNr\":250")).timeline.clip(clip.id)!!.grade.temporalNr)
    }

    @Test
    fun `user bins and the bins media are filed in survive a round trip, older projects load without them`() {
        val bins = listOf(UserBin("bin-1", "采访"), UserBin("bin-2", "空镜"))
        val media = document.media.mapIndexed { i, m -> if (i == 0) m.copy(binId = "bin-2") else m }
        val doc = document.copy(bins = bins, media = media)
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse(ProjectJsonCodec.encode(document).contains("\"bins\""))
        assertFalse(ProjectJsonCodec.encode(document).contains("\"bin\""))
    }

    @Test
    fun `local grade versions survive a round trip, a clip never versioned writes none, and an odd index is held in range`() {
        val clip = document.timeline.clips.first { it.kind == ClipKind.VIDEO }
        val versions = listOf(GradeVersion("版本 1", ColorGrade(temperature = 20f)), GradeVersion("冷调", ColorGrade(saturation = 0.5f)))
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(gradeVersions = versions, gradeVersion = 1, grade = versions[1].grade)))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertFalse(ProjectJsonCodec.encode(document).contains("gradeVersions"))
        val odd = ProjectJsonCodec.decode(json.replace("\"gradeVersion\":1", "\"gradeVersion\":9"))
        assertEquals(1, odd.timeline.clip(clip.id)!!.gradeVersion)
    }

    @Test
    fun `spatial noise reduction survives a round trip, older grades load without it, and odd values are held in range`() {
        val nr = SpatialNoiseReduction(lumaThreshold = 35f, chromaThreshold = 60f, radius = NrRadius.LARGE, amount = 80f)
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = grade.copy(spatialNr = nr))))
        val json = ProjectJsonCodec.encode(doc)
        assertEquals(doc, ProjectJsonCodec.decode(json))
        assertTrue(json.contains("\"nr\""))
        // Without one nothing is written.
        assertFalse(ProjectJsonCodec.encode(document).contains("\"nr\""))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1",
               "grade":{"nr":{"luma":250,"chroma":-4,"radius":"HUGE"}}}]}}""",
        ).timeline.clips.single().grade.spatialNr
        assertEquals(SpatialNoiseReduction(lumaThreshold = 100f, chromaThreshold = 0f, radius = NrRadius.MEDIUM, amount = 100f), odd)
    }

    @Test
    fun `grade keyframes survive a round trip with their easing, and a broken one is skipped`() {
        val keyed = grade.copy(
            keyframes = listOf(
                GradeKeyframe(0, ColorGrade(exposure = 0.5f, temperature = 20f, look = Look.WARM_FADE)),
                GradeKeyframe(60, ColorGrade(exposure = -1f, gain = WheelValue(0.1f, -0.1f, 0.2f)), KeyEase(slopeIn = 0f)),
            ),
        )
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = keyed)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1",
               "grade":{"keys":[{"frame":30,"grade":{"exposure":1}},{"frame":10},{"frame":-3,"grade":{"window":{"shape":"circle"}}}]}}]}}""",
        ).timeline.clips.single().grade
        assertEquals("ordered, clamped, a key without a grade dropped, no window inside a key", listOf(0, 30), sparse.keyframes.map { it.frame })
        assertEquals(ColorGrade(), sparse.keyframes[0].grade)
        assertEquals(1f, sparse.keyframes[1].grade.exposure, 0f)
    }

    @Test
    fun `an imported LUT is kept by id and name, on node 1 and inside keyframes, and a nameless one takes its id`() {
        val lut = LutRef("lut-0123456789ab", "Apple Log → Rec.709")
        val clip = document.timeline.clips.first()
        val keyed = ColorGrade(lut = lut, lookStrength = 100, keyframes = listOf(GradeKeyframe(0, ColorGrade(lut = lut, lookStrength = 60)), GradeKeyframe(30, ColorGrade())))
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = keyed)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse("no LUT, no key", ProjectJsonCodec.encode(document).contains("\"lut\""))
        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1","grade":{"lut":{"id":"lut-x"}}},
               {"id":"d","track":"v1","grade":{"lut":{"name":"no id"}}}]}}""",
        ).timeline.clips
        assertEquals(LutRef("lut-x", "lut-x"), sparse[0].grade.lut)
        assertNull("a LUT without an id is dropped", sparse[1].grade.lut)
    }

    @Test
    fun `gallery stills survive a round trip with their grades, and broken ones are skipped`() {
        val still = GalleryStill("still-1", 3, "Beach", "00:00:01:10", ColorGrade(exposure = -0.5f, look = Look.MONO), "still-1.jpg", 960, 540)
        val doc = document.copy(stills = listOf(still))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse("no stills, no key", ProjectJsonCodec.encode(document).contains("\"stills\""))
        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[],"clips":[]},
               "stills":[{"id":"a","image":"a.jpg"},{"id":"b"},{"image":"c.jpg"}]}""",
        ).stills
        assertEquals("a still without an id or a picture is dropped", listOf("a"), sparse.map { it.id })
        assertEquals(GalleryStill("a", 1, "", "", ColorGrade(), "a.jpg", 0, 0), sparse.single())
    }

    @Test
    fun `window keyframes survive a round trip, sparse ones load in order with defaults`() {
        val tracked = grade.copy(
            window = PowerWindow(
                WindowShape.RECTANGLE, size = 0.6f,
                keyframes = listOf(
                    WindowKeyframe(0, WindowGeometry(-0.3f, 0.1f, 0.5f, 1.5f, 10f, 0.2f)),
                    WindowKeyframe(45, WindowGeometry(0.4f, -0.2f, 0.7f, 1.2f, -25f, 0.35f), KeyEase(slopeIn = 0f)),
                ),
            ),
        )
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = tracked)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))

        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1",
               "grade":{"window":{"shape":"circle","keys":[{"frame":30,"x":0.5},{"frame":-4,"y":0.25},{"frame":30,"x":0.9}]}}}]}}""",
        ).timeline.clips.single().grade.window!!
        val defaults = PowerWindow()
        assertEquals(
            "negative frames clamp to the start, a repeated frame keeps the first",
            listOf(
                WindowKeyframe(0, WindowGeometry(0f, 0.25f, defaults.size, defaults.aspect, 0f, defaults.softness)),
                WindowKeyframe(30, WindowGeometry(0.5f, 0f, defaults.size, defaults.aspect, 0f, defaults.softness)),
            ),
            sparse.keyframes,
        )
        assertFalse("a window without keyframes writes none", JSONObject(ProjectJsonCodec.encode(document)).toString().contains("\"keys\""))
    }

    @Test
    fun `a qualifier on a full-frame node survives a round trip, a sparse one loads with defaults`() {
        val keyed = grade.copy(
            window = PowerWindow(
                shape = WindowShape.FULL,
                grade = ColorGrade(saturation = 0.4f),
                qualifier = HslQualifier(205f, 22f, 12f, 0.3f, 0.9f, 0.05f, 0.2f, 0.8f, 0.15f, invert = true),
            ),
        )
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(grade = keyed)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))

        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1"}],"clips":[{"id":"c","track":"v1",
               "grade":{"window":{"shape":"full","qualifier":{"hue":[40]}}}}]}}""",
        ).timeline.clips.single().grade.window!!
        assertEquals(WindowShape.FULL, sparse.shape)
        assertEquals(HslQualifier(hueCenter = 40f), sparse.qualifier)
    }

    @Test
    fun `a subtitle track style survives a round trip, and older projects take their subtitles' size`() {
        val st = Track("st1", "ST1", TrackKind.SUBTITLE, subtitleStyle = SubtitleStyle(60, SubtitleColor.CYAN, bold = true, outline = true, box = false, boxOpacityPercent = 30, marginPercent = 15))
        val doc = document.copy(timeline = document.timeline.copy(tracks = listOf(st) + document.timeline.tracks))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val old = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"st1","kind":"SUBTITLE"},{"id":"st2","kind":"SUBTITLE"}],"clips":[
               {"id":"c","track":"st1","kind":"TITLE","title":{"text":"你好","size":52,"template":"SUBTITLE"}}]}}""",
        ).timeline
        assertEquals(SubtitleStyle(sizePx = 52), old.track("st1")!!.subtitleStyle)
        assertEquals(SubtitleStyle.DEFAULT, old.track("st2")!!.subtitleStyle)
        val wild = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"st1","kind":"SUBTITLE","subtitleStyle":{"size":999,"color":"PINK","margin":0}}],"clips":[]}}""",
        ).timeline.track("st1")!!.subtitleStyle
        assertEquals(SubtitleStyle(sizePx = 120, marginPercent = 2), wild)
    }

    @Test
    fun `a title's look survives a round trip, only what differs from its template is written, older files take the template's`() {
        val styled = TitleStyle(
            "夜跑", 120, TitleTemplate.LOWER_THIRD, TitleColor.BLACK, TitleWeight.LIGHT,
            outline = true, shadow = false, box = false, boxOpacityPercent = 80, animation = TitleAnimation.TYPEWRITER,
        )
        val plain = TitleStyle("标题", 96, TitleTemplate.CENTER)
        val clips = listOf(
            Clip("t1", Timeline.V2, ClipKind.TITLE, "标题：夜跑", 0, 90, title = styled),
            Clip("t2", Timeline.V2, ClipKind.TITLE, "标题：标题", 90, 90, title = plain),
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = clips))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips")
        val first = written.getJSONObject(0).getJSONObject("title")
        assertEquals(listOf("BLACK", "LIGHT", true, false, false, 80, "TYPEWRITER"),
            listOf(first.get("color"), first.get("weight"), first.get("outline"), first.get("shadow"), first.get("box"), first.get("boxOpacity"), first.get("animation")))
        assertEquals("the template's look writes nothing", listOf("size", "template", "text"), written.getJSONObject(1).getJSONObject("title").keys().asSequence().sorted().toList())
        val old = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v2","kind":"VIDEO"}],"clips":[
               {"id":"c","track":"v2","kind":"TITLE","title":{"text":"人物 · 地点","size":56,"template":"LOWER_THIRD"}},
               {"id":"d","track":"v2","kind":"TITLE","title":{"text":"x","template":"CENTER","color":"PLAID","boxOpacity":400,"animation":"SPIN"}}]}}""",
        ).timeline
        assertEquals(TitleStyle("人物 · 地点", 56, TitleTemplate.LOWER_THIRD), old.clip("c")!!.title)
        assertEquals("the band of an older lower third stays", true, old.clip("c")!!.title!!.box)
        assertEquals(TitleStyle("x", boxOpacityPercent = 100), old.clip("d")!!.title)
    }

    @Test
    fun `hue curves survive a round trip, are written only when they have points, and stray points are dropped`() {
        val curves = HueCurves(
            hueSat = listOf(CurvePoint(0.5f, 0.5f), CurvePoint(0.6666667f, 0f), CurvePoint(0.8333333f, 0.5f)),
            lumSat = listOf(CurvePoint(0.8f, 0.2f)),
        )
        val clip = document.timeline.clips.first()
        val graded = clip.copy(grade = ColorGrade(hueCurves = curves, window = PowerWindow(grade = ColorGrade(hueCurves = HueCurves(hueHue = listOf(CurvePoint(0f, 1f)))))))
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(graded)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val grade = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("grade")
        assertEquals(listOf("hueSat", "lumSat"), grade.getJSONObject("hueCurves").keys().asSequence().sorted().toList())
        val plain = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("grade")
        assertFalse(plain.has("hueCurves"))
        val wild = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO",
               "grade":{"hueCurves":{"hueHue":[[0.9,2],[0.2,0.4],[1.0,0.3],[0.2,0.6],["x",1],[-0.1,0.5]],"lumSat":[[1.0,0.1]]}}}]}}""",
        ).timeline.clip("c")!!.grade.hueCurves
        assertEquals(HueCurves(hueHue = listOf(CurvePoint(0.2f, 0.4f), CurvePoint(0.9f, 1f)), lumSat = listOf(CurvePoint(1f, 0.1f))), wild)
    }

    @Test
    fun `marker colours and notes survive a round trip, defaults are not written and older files load blue`() {
        val markers = listOf(Marker("m-1", 30, "开场", MarkerColor.RED, "音乐在此进入"), Marker("m-2", 90, "标记 2"))
        val doc = document.copy(timeline = document.timeline.copy(markers = markers))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("markers")
        assertEquals("RED", written.getJSONObject(0).getString("color"))
        assertFalse("blue and no note write nothing", written.getJSONObject(1).has("color") || written.getJSONObject(1).has("note"))
    }

    @Test
    fun `a speed between the presets survives a round trip, presets keep their names, wild ones are clamped`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(speed = ClipSpeed(4f / 3f))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val json = JSONObject(ProjectJsonCodec.encode(doc))
        assertEquals(4.0 / 3.0, json.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getDouble("speed"), 1e-6)
        val preset = document.copy(timeline = document.timeline.replaceClip(clip.copy(speed = ClipSpeed.of(PlaybackSpeed.HALF))))
        assertEquals("HALF", JSONObject(ProjectJsonCodec.encode(preset)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getString("speed"))
        json.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).put("speed", 50)
        assertEquals(ClipSpeed(ClipSpeed.MAX), ProjectJsonCodec.decode(json.toString()).timeline.clips.first().speed)
    }

    @Test
    fun `timeline in and out survive a round trip, one alone too, and a reversed pair keeps its in`() {
        val doc = document.copy(timeline = document.timeline.copy(marks = TimelineMarks(60, 150)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val outOnly = document.copy(timeline = document.timeline.copy(marks = TimelineMarks(outFrame = 90)))
        assertEquals(outOnly, ProjectJsonCodec.decode(ProjectJsonCodec.encode(outOnly)))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").has("marks"))
        val json = JSONObject(ProjectJsonCodec.encode(document))
        json.getJSONObject("timeline").put("marks", JSONObject().put("in", 90).put("out", 30))
        assertEquals(TimelineMarks(90, null), ProjectJsonCodec.decode(json.toString()).timeline.marks)
    }

    @Test
    fun `edit destinations survive a round trip and are written only when not the defaults`() {
        val doc = document.copy(timeline = document.timeline.copy(targets = EditTargets(video = Timeline.V2, audio = Timeline.A2)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val auto = document.copy(timeline = document.timeline.copy(targets = EditTargets(video = Timeline.V2)))
        assertEquals(auto, ProjectJsonCodec.decode(ProjectJsonCodec.encode(auto)))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").has("targets"))
        assertEquals("older files: V1 and automatic sound", EditTargets(), ProjectJsonCodec.decode(ProjectJsonCodec.encode(document)).timeline.targets)
    }

    @Test
    fun `a qualifier's matte finesse survives a round trip and is written only when used`() {
        val q = HslQualifier.picked(0.2f, 0.7f, 0.3f).copy(cleanBlack = 30f, cleanWhite = 12f, blur = 55f)
        val clip = document.timeline.clips.first()
        val graded = clip.copy(grade = ColorGrade(window = PowerWindow(shape = WindowShape.FULL, qualifier = q, grade = ColorGrade(saturation = 0f))))
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(graded)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val plain = graded.copy(grade = graded.grade.copy(window = graded.grade.window!!.copy(qualifier = HslQualifier())))
        val written = JSONObject(ProjectJsonCodec.encode(document.copy(timeline = document.timeline.copy(clips = listOf(plain)))))
            .getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("grade").getJSONObject("window").getJSONObject("qualifier")
        assertFalse(written.has("finesse"))
    }

    @Test
    fun `a crop survives a round trip with its keyframes, is written only when set, and wild values are clamped`() {
        val clip = document.timeline.clips.first()
        val crop = ClipTransform(scalePercent = 35, cropLeft = 240, cropRight = 120, cropTop = 54, cropBottom = 27, cropSoftness = 30)
        val keyed = clip.copy(transform = crop, keyframes = listOf(TransformKeyframe(0, crop), TransformKeyframe(30, ClipTransform(cropRight = 960))))
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(keyed)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0)
        assertEquals("[240,120,54,27,30]", written.getJSONObject("transform").getJSONArray("crop").toString())
        val plain = JSONObject(ProjectJsonCodec.encode(document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transform = ClipTransform()))))))
        assertFalse(plain.getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("transform").has("crop"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","transform":{"crop":[5000,-3,10]}}]}}""",
        )
        assertEquals(ClipTransform(cropLeft = 1920, cropTop = 10), odd.timeline.clip("c")!!.transform)
    }

    @Test
    fun `per-parameter keys survive a round trip, whole keyframes are written as before, and odd ones load sanely`() {
        val clip = document.timeline.clips.first()
        val keys = listOf(
            TransformKeyframe(10, ClipTransform(scalePercent = 80), params = setOf(CurveParam.SCALE)),
            TransformKeyframe(
                30, ClipTransform(scalePercent = 120, positionX = 200), KeyEase(slopeIn = 0f),
                params = setOf(CurveParam.SCALE, CurveParam.POSITION_X, CurveParam.POSITION_Y), paramEases = mapOf(CurveParam.SCALE to KeyEase(slopeOut = 2f)),
            ),
            TransformKeyframe(50, ClipTransform(positionX = -200)),
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(keyframes = keys))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONArray("keyframes")
        assertEquals("[\"SCALE\"]", written.getJSONObject(0).getJSONArray("params").toString())
        assertEquals(2.0, written.getJSONObject(1).getJSONObject("eases").getJSONObject("SCALE").getDouble("slopeOut"), 0.0)
        assertFalse("a whole keyframe is written as before", written.getJSONObject(2).has("params") || written.getJSONObject(2).has("eases"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","keyframes":[
                {"frame":0,"scale":50,"params":["SCALE","WOBBLE"],"eases":{"SCALE":{"slopeIn":9},"OPACITY":{"slopeIn":0}}},
                {"frame":20,"scale":90,"params":["WOBBLE"]},
                {"frame":40,"scale":150}]}]}}""",
        ).timeline.clip("c")!!.keyframes
        assertEquals("a keyframe keying nothing readable is dropped", listOf(0, 40), odd.map { it.frame })
        assertEquals(setOf(CurveParam.SCALE), odd[0].params)
        assertEquals("an ease for a parameter it does not key is ignored", setOf(CurveParam.SCALE), odd[0].paramEases.keys)
        assertEquals(KeyEase.MAX_SLOPE, odd[0].easeOf(CurveParam.SCALE).slopeIn, 0f)
        assertTrue("older keyframes key everything", odd[1].whole)
    }

    @Test
    fun `a mask survives a round trip with its keys, is written only when set without a grade, and odd ones load sanely`() {
        val clip = document.timeline.clips.first()
        val mask = PowerWindow(
            shape = WindowShape.RECTANGLE, centerX = 0.25f, centerY = -0.1f, size = 0.4f, aspect = 1.5f, angle = 12f, softness = 0.05f, invert = true,
            keyframes = listOf(WindowKeyframe(0, WindowGeometry(0.25f, -0.1f, 0.4f, 1.5f, 12f, 0.05f)), WindowKeyframe(40, WindowGeometry(-0.5f, 0.2f, 0.6f, 1.5f, -30f, 0.1f), KeyEase(slopeIn = 0f))),
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(mask = mask))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("mask")
        assertEquals("rectangle", written.getString("shape"))
        assertFalse("a mask has no grade", written.has("grade"))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(mask = null))))))
            .getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("mask"))
        fun load(json: String) = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","mask":$json}]}}""",
        ).timeline.clip("c")!!.mask
        assertNull("the full frame masks nothing", load("""{"shape":"full"}"""))
        val odd = load("""{"shape":"circle","x":5,"size":9,"softness":-1,"angle":720,"keys":[{"frame":3,"x":-4,"size":0}]}""")!!
        assertEquals(1f, odd.centerX, 0f)
        assertEquals(2f, odd.size, 0f)
        assertEquals(0f, odd.softness, 0f)
        assertEquals(180f, odd.angle, 0f)
        assertEquals(-1f, odd.keyframes.single().geometry.centerX, 0f)
        assertEquals(0.05f, odd.keyframes.single().geometry.size, 0f)
    }

    @Test
    fun `a cross fade survives a round trip, is written only when set, and a negative one loads as a cut`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(crossFadeFrames = 12))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("crossFade"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"a1","kind":"AUDIO"}],"clips":[{"id":"c","track":"a1","kind":"AUDIO","crossFade":-4}]}}""",
        )
        assertEquals(0, odd.timeline.clip("c")!!.crossFadeFrames)
    }

    @Test
    fun `dynamic zoom survives a round trip, is written only when on, and odd values load sanely`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(dynamicZoom = DynamicZoom(ZoomDirection.OUT, 35, ZoomEase.EASE_OUT)))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("zoom"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","zoom":{"direction":"SIDEWAYS","amount":400}}]}}""",
        )
        assertEquals(DynamicZoom(ZoomDirection.IN, 50, ZoomEase.LINEAR), odd.timeline.clip("c")!!.dynamicZoom)
        // A pan with its focus: written by name, a centred one not at all.
        val pan = DynamicZoom(ZoomDirection.PAN, 25, ZoomEase.EASE_IN_OUT, ZoomFocusX.LEFT, ZoomFocusY.TOP)
        val panned = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(dynamicZoom = pan))))
        assertEquals(panned, ProjectJsonCodec.decode(ProjectJsonCodec.encode(panned)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("zoom")
        assertFalse(written.has("focusX") || written.has("focusY"))
    }

    @Test
    fun `a composite mode survives a round trip, is written only when not normal, and an unknown one loads as normal`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(composite = CompositeMode.SCREEN))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertEquals("SCREEN", JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getString("composite"))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("composite"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","composite":"GLOW"}]}}""",
        )
        assertEquals(CompositeMode.NORMAL, odd.timeline.clip("c")!!.composite)
    }

    @Test
    fun `media flags survive a round trip in palette order, are written only when set, and unknown or repeated ones drop`() {
        val asset = document.media.first()
        val doc = document.copy(media = listOf(asset.copy(flags = listOf(FlagColor.BLUE, FlagColor.RED))) + document.media.drop(1))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONArray("media").getJSONObject(0).getJSONArray("flags")
        assertEquals(listOf("BLUE", "RED"), (0 until written.length()).map { written.getString(it) })
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONArray("media").getJSONObject(0).has("flags"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"media":[{"id":"m","uri":"u","flags":["RED","GOLD","BLUE","RED"]}],"timeline":{"tracks":[],"clips":[]}}""",
        )
        assertEquals(listOf(FlagColor.BLUE, FlagColor.RED), odd.media.single().flags)
    }

    @Test
    fun `a clip's scaling survives a round trip, following the project is left out, and an unknown one follows`() {
        val clip = document.timeline.clips.first()
        ClipScaling.entries.forEach { scaling ->
            val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(scaling = scaling))))
            assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
            assertEquals(scaling.name, JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getString("scaling"))
        }
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("scaling"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","scaling":"ZOOM"}]}}""",
        )
        assertNull(odd.timeline.clip("c")!!.scaling)
    }

    @Test
    fun `the new transitions survive a round trip by name`() {
        val clip = document.timeline.clips.first()
        listOf(TransitionType.IRIS, TransitionType.CLOCK_WIPE, TransitionType.BARN_DOOR, TransitionType.BLUR_DISSOLVE).forEach { type ->
            val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(type, 24)))))
            assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        }
    }

    @Test
    fun `speed points survive a round trip, and sections that do not add up to the clip are dropped`() {
        val clip = document.timeline.clips.first()
        val sections = listOf(SpeedSection(clip.durationFrames - 40, ClipSpeed.NORMAL), SpeedSection(40, ClipSpeed(0.4f)))
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(speedSections = sections))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val plain = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0)
        assertFalse(plain.has("speedSections"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","duration":90,
               "speedSections":[{"frames":30,"speed":"HALF"},{"frames":30,"speed":1.0}]}]}}""",
        )
        assertTrue(odd.timeline.clip("c")!!.speedSections.isEmpty())
    }

    @Test
    fun `a track's noise reduction survives a round trip, written only once it is set`() {
        val nr = TrackNoiseReduction(enabled = true, reductionDb = 18.5f, thresholdDb = 4f)
        val mixer = document.mixer.replace(document.mixer.channels.first().copy(noiseReduction = nr))
        val doc = document.copy(mixer = mixer)
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val channels = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("mixer").getJSONArray("channels")
        assertEquals(listOf(true) + List(channels.length() - 1) { false }, (0 until channels.length()).map { channels.getJSONObject(it).has("nr") })
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"a1","kind":"AUDIO"}],"clips":[]},
               "mixer":{"channels":[{"track":"a1","nr":{"on":true,"reduction":99,"threshold":"x"}}]}}""",
        )
        assertEquals("clamped, defaults for what is missing", TrackNoiseReduction(true, 30f, 6f), odd.mixer.channel("a1").noiseReduction)
    }

    @Test
    fun `speed ramps survive a round trip, written only where a point is smoothed`() {
        val clip = document.timeline.clips.first()
        val sections = listOf(
            SpeedSection(clip.durationFrames - 40, ClipSpeed.NORMAL), SpeedSection(20, ClipSpeed(0.4f), rampFrames = 12), SpeedSection(20, ClipSpeed.NORMAL),
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(speedSections = sections))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONArray("speedSections")
        assertEquals(listOf(false, true, false), (0 until 3).map { written.getJSONObject(it).has("ramp") })
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","duration":90,
               "speedSections":[{"frames":30,"speed":"HALF","ramp":9},{"frames":30,"speed":1.0,"ramp":-4},{"frames":30,"speed":"HALF","ramp":20}]}]}}""",
        )
        assertEquals("none before the first section, none below 0", listOf(0, 0, 20), odd.timeline.clip("c")!!.speedSections.map { it.rampFrames })
    }

    @Test
    fun `effect keyframes survive a round trip, eased ones too, and a fixed effect writes none`() {
        val clip = document.timeline.clips.first()
        val keyed = ClipEffect(
            "fx-k", EffectType.GAUSSIAN_BLUR, 30,
            keyframes = listOf(EffectKeyframe(0, 0), EffectKeyframe(45, 80, KeyEase(slopeIn = 0f)), EffectKeyframe(90, 10)),
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(effects = listOf(keyed, ClipEffect("fx-f", EffectType.GLOW, 40))))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val written = JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONArray("effects")
        assertEquals(3, written.getJSONObject(0).getJSONArray("keys").length())
        assertFalse(written.getJSONObject(1).has("keys"))
    }

    @Test
    fun `a transition's style survives a round trip and a plain one writes none`() {
        val clip = document.timeline.clips.first()
        val styled = Transition(
            TransitionType.CLOCK_WIPE, 24, TransitionEase.OUT, reverse = true, softness = 35, border = 60, borderColor = TitleColor.CYAN,
            alignment = TransitionAlignment.END,
        )
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = styled))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))

        val plain = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(TransitionType.WIPE, 12)))))
        val written = JSONObject(ProjectJsonCodec.encode(plain)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("transition")
        assertEquals(setOf("type", "frames"), written.keys().asSequence().toSet())

        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO",
               "transition":{"type":"IRIS","frames":9,"ease":"BOUNCE","softness":250,"border":-3,"borderColor":"GOLD"}}]}}""",
        )
        assertEquals(
            "unknown names fall back (no alignment: from the cut), numbers are held to 0-100",
            Transition(TransitionType.IRIS, 9, softness = 100),
            odd.timeline.clip("c")!!.transitionIn,
        )
        val centred = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(TransitionType.WIPE, 12, alignment = TransitionAlignment.CENTER)))))
        assertEquals(TransitionAlignment.CENTER, ProjectJsonCodec.decode(ProjectJsonCodec.encode(centred)).timeline.clips.single().transitionIn!!.alignment)
        // A wipe keeps its angle (held to -179..180); level wipes write none.
        val tilted = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(TransitionType.WIPE, 12, angle = -45)))))
        assertEquals(tilted, ProjectJsonCodec.decode(ProjectJsonCodec.encode(tilted)))
        val wrapped = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO",
               "transition":{"type":"WIPE","frames":9,"angle":270}}]}}""",
        )
        assertEquals(-90, wrapped.timeline.clip("c")!!.transitionIn!!.angle)
        // A colour dip keeps its colour; the default one writes none.
        val red = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(TransitionType.DIP_TO_COLOR, 20, color = TitleColor.RED)))))
        assertEquals(red, ProjectJsonCodec.decode(ProjectJsonCodec.encode(red)))
        val amber = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(transitionIn = Transition(TransitionType.DIP_TO_COLOR, 20)))))
        val amberJson = JSONObject(ProjectJsonCodec.encode(amber)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getJSONObject("transition")
        assertEquals(setOf("type", "frames"), amberJson.keys().asSequence().toSet())
    }

    @Test
    fun `a render job keeps the timeline it renders, and older jobs render the first timeline`() {
        val job = RenderJob("j-1", "a.mp4", RenderSettings(fileName = "a"), timelineId = "tl-v", timelineName = "竖屏版")
        val doc = document.copy(jobs = listOf(job))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val old = ProjectJsonCodec.decode("""{"id":"p","format":{},"jobs":[{"id":"j","fileName":"b.mp4"}]}""").jobs.single()
        assertEquals("tl-1" to "时间线 1", old.timelineId to old.timelineName)
    }

    @Test
    fun `a project's other timelines survive a round trip in their order, and a file without them has one`() {
        val vertical = document.format.copy(width = 1080, height = 1920, scaling = ClipScaling.FILL)
        val doc = document.copy(
            timelineId = "tl-b", timelineName = "竖屏版", timelineIndex = 1,
            timelines = listOf(
                StoredTimeline("tl-1", "时间线 1", document.format, document.timeline, document.mixer),
                StoredTimeline("tl-c", "空白", vertical, Timeline.EMPTY, MixerState.forTracks(Timeline.EMPTY.tracks)),
            ),
        )
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertEquals(listOf("时间线 1", "竖屏版", "空白"), ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)).timelineEntries.map { it.name })
        val single = ProjectJsonCodec.decode("""{"id":"p","format":{}}""")
        assertEquals(listOf("时间线 1"), single.timelineEntries.map { it.name })
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).has("timelines"))
    }

    @Test
    fun `the project's scaling for mismatched media survives a round trip and is written only when not fit`() {
        val doc = document.copy(format = document.format.copy(width = 1080, height = 1920, scaling = ClipScaling.FILL))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertEquals("FILL", JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("format").getString("scaling"))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("format").has("scaling"))
        assertEquals(ClipScaling.FIT, ProjectJsonCodec.decode("""{"id":"p","format":{"scaling":"ZOOM"}}""").format.scaling)
    }

    @Test
    fun `a retime process survives a round trip, is written only when not nearest, and an unknown one loads as nearest`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(retime = RetimeProcess.FRAME_BLEND))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val flow = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(retime = RetimeProcess.OPTICAL_FLOW))))
        assertEquals(flow, ProjectJsonCodec.decode(ProjectJsonCodec.encode(flow)))
        assertEquals("FRAME_BLEND", JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getString("retime"))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("retime"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","retime":"SPEED_WARP"}]}}""",
        )
        assertEquals(RetimeProcess.NEAREST, odd.timeline.clip("c")!!.retime)
    }

    @Test
    fun `a clip colour survives a round trip, is written only when set, and an unknown one is dropped`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.copy(clips = listOf(clip.copy(color = ClipColor.NAVY))))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        assertEquals("NAVY", JSONObject(ProjectJsonCodec.encode(doc)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).getString("color"))
        assertFalse(JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips").getJSONObject(0).has("color"))
        val odd = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"v1","kind":"VIDEO"}],"clips":[{"id":"c","track":"v1","kind":"VIDEO","color":"PLAID"}]}}""",
        )
        assertEquals(null, odd.timeline.clip("c")!!.color)
    }

    @Test
    fun `a disabled clip is written as such and older files load every clip enabled`() {
        val clip = document.timeline.clips.first()
        val doc = document.copy(timeline = document.timeline.replaceClip(clip.copy(enabled = false)))
        assertEquals(doc, ProjectJsonCodec.decode(ProjectJsonCodec.encode(doc)))
        val clips = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("timeline").getJSONArray("clips")
        assertFalse("enabled clips write nothing", clips.getJSONObject(0).has("enabled"))
        assertTrue(ProjectJsonCodec.decode(ProjectJsonCodec.encode(document)).timeline.clips.all { it.enabled })
    }

    @Test
    fun `track dynamics are written only when set, and sparse or wild values load sanely`() {
        val mixer = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("mixer").getJSONArray("channels")
        assertTrue(mixer.getJSONObject(0).has("dyn"))
        assertFalse("a track without dynamics writes none", mixer.getJSONObject(2).has("dyn"))
        val sparse = ProjectJsonCodec.decode(
            """{"id":"p","format":{},"timeline":{"tracks":[{"id":"a1","kind":"AUDIO"}],"clips":[]},
               "mixer":{"channels":[{"track":"a1","dyn":{"comp":[true,-80,"x"],"limit":[true]}}]}}""",
        ).mixer.channel("a1").dynamics
        assertEquals(TrackDynamics(compressor = true, thresholdDb = -50f, limiter = true), sparse)
    }

    @Test
    fun `track ducking is written only when set, and sparse or wild values load sanely`() {
        val mixer = JSONObject(ProjectJsonCodec.encode(document)).getJSONObject("mixer").getJSONArray("channels")
        assertEquals(Timeline.A1, mixer.getJSONObject(1).getJSONObject("duck").getString("key"))
        assertFalse("a track without ducking writes none", mixer.getJSONObject(0).has("duck"))
        val decoded = { json: String ->
            ProjectJsonCodec.decode(
                """{"id":"p","format":{},"timeline":{"tracks":[{"id":"a1","kind":"AUDIO"},{"id":"a2","kind":"AUDIO"}],"clips":[]},
                   "mixer":{"channels":[{"track":"a2","duck":$json}]}}""",
            ).mixer.channel("a2").ducking
        }
        assertEquals(TrackDucking(keyTrackId = "a1", depthDb = 30f, thresholdDb = -60f), decoded("""{"key":"a1","depth":99,"thr":-200,"atk":"x"}"""))
        assertEquals("a bypassed ducking without a key", TrackDucking(enabled = false), decoded("""{"on":false,"key":null}"""))
    }

    @Test
    fun `the encoded file records the format version`() {
        assertEquals(ProjectJsonCodec.VERSION, JSONObject(ProjectJsonCodec.encode(document)).getInt("version"))
    }

    @Test
    fun `legacy marked jobs recover the range from their own saved timeline`() {
        val alternate = StoredTimeline("tl-other", "Other", document.format, document.timeline.copy(marks = TimelineMarks(15, 45)), document.mixer)
        val encoded = JSONObject(ProjectJsonCodec.encode(document.copy(timelines = listOf(alternate))))
        val oldJob = encoded.getJSONArray("jobs").getJSONObject(1)
        oldJob.remove("queuedRange")
        oldJob.put("timelineId", alternate.id).put("status", RenderStatus.RENDERING.name)

        val loaded = ProjectJsonCodec.decode(encoded.toString())
        assertEquals(TimelineMarks(15, 45), loaded.jobs[1].queuedRange)
        assertEquals(RenderStatus.QUEUED, loaded.jobs[1].status)
        val changed = loaded.copy(timelines = listOf(alternate.copy(timeline = alternate.timeline.copy(marks = TimelineMarks()))))
        assertEquals(TimelineMarks(15, 45), ProjectJsonCodec.decode(ProjectJsonCodec.encode(changed)).jobs[1].queuedRange)
    }

    @Test
    fun `a render that was running when saved comes back queued from the start`() {
        val running = RenderJob("j-9", "Live.mp4", RenderSettings("Live"), RenderStatus.RENDERING, progress = 0.6f)

        val restored = ProjectJsonCodec.decode(ProjectJsonCodec.encode(document.copy(jobs = listOf(running)))).jobs.single()

        assertEquals(running.copy(status = RenderStatus.QUEUED, progress = 0f), restored)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val decoded = ProjectJsonCodec.decode("""{"id":"p-old","name":"Old","format":{}}""")

        assertEquals("p-old", decoded.id)
        assertEquals("Old", decoded.name)
        assertEquals(ProjectFormat(1920, 1080, 30), decoded.format)
        assertEquals(0L, decoded.createdAtMillis)
        assertEquals(emptyList<Any>(), decoded.media)
        assertEquals(Timeline.EMPTY, decoded.timeline)
        assertEquals(MixerState.forTracks(Timeline.defaultTracks()), decoded.mixer)
        assertEquals(RenderSettings(fileName = "Old"), decoded.render)
        assertEquals(emptyList<Any>(), decoded.jobs)

        assertEquals("未命名项目", ProjectJsonCodec.decode("""{"id":"p-anon","format":{"fps":24}}""").name)
    }

    @Test
    fun `sparse clips tracks and unknown enum names decode to defaults`() {
        val json = """
            {"id":"p-sparse","format":{"width":1280,"height":720,"fps":25},"extra":"ignored",
             "timeline":{"tracks":[{"id":"v1"}],
               "clips":[{"id":"c-min","track":"v1"},
                        {"id":"c-odd","track":"v1","kind":"HOLOGRAM","speed":"WARP","duration":0,
                         "transition":{"type":"SPIN"},"effects":[{"id":"fx","type":"LASER"}],
                         "grade":{"look":"neon-unknown","exposure":0.5},"title":{"text":"Hi"},"asset":null,"link":""}]},
             "render":{"fileName":"Out","codec":"AV1","preset":null},
             "jobs":[{"id":"j-x","status":"PAUSED"}]}
        """.trimIndent()

        val doc = ProjectJsonCodec.decode(json)

        assertEquals(listOf(Track("v1", "V1", TrackKind.VIDEO)), doc.timeline.tracks)
        assertEquals(Clip(id = "c-min", trackId = "v1", kind = ClipKind.VIDEO, name = "", start = 0, durationFrames = 1), doc.timeline.clip("c-min"))
        val odd = doc.timeline.clip("c-odd")!!
        assertEquals(ClipKind.VIDEO, odd.kind)
        assertEquals(ClipSpeed.NORMAL, odd.speed)
        assertEquals(1, odd.durationFrames)
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 15), odd.transitionIn)
        assertEquals(listOf(ClipEffect("fx", EffectType.VIGNETTE, 50, true)), odd.effects)
        assertEquals(ColorGrade(exposure = 0.5f), odd.grade)
        assertEquals(TitleStyle("Hi", 72, TitleTemplate.CENTER), odd.title)
        assertNull(odd.assetId)
        assertNull(odd.linkId)
        assertEquals(RenderSettings(fileName = "Out", preset = null), doc.render)
        assertEquals(RenderJob("j-x", "", RenderSettings(""), RenderStatus.QUEUED), doc.jobs.single())
    }
}
