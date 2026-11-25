package com.example.prismstudio.p40b43a88

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.prismstudio.p40b43a88.domain.model.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the shipping editor screens against persisted projects and the platform's real media stack. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class WorkspaceFlowTest : EditorDeviceTest() {
    @Test fun mediaLogFavoriteAndSmartBinAreSavedThroughTheirSheets() = runBlocking {
        val shot = sample("sound-avc.mp4")
        val doc = project(shot).copy(name = "Logging ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.click("媒体", byDescription = true)
            window.click(shot.name, prefix = true)
            window.click("场记", prefix = true, byDescription = true)
            window.setText("场次", "12")
            window.setText("关键词", "interview, selects")
            window.click("好条：", prefix = true, byDescription = true)
            window.click("保存")
            window.waitFor("logged take") { graph.session.document.value!!.media.single().metadata.goodTake }
            val metadata = graph.session.document.value!!.media.single().metadata
            assertEquals("12", metadata.scene)
            assertEquals(listOf("interview", "selects"), metadata.keywords)
            window.click("新建智能媒体夹", byDescription = true)
            window.setText("智能媒体夹名称", "Interview selects")
            window.click("仅好条：", prefix = true, byDescription = true)
            window.setText("关键词包含", "interview")
            window.click("创建")
            window.waitFor("smart bin") { graph.session.document.value!!.smartBins.singleOrNull()?.goodTake == true }
            window.bounds("Interview selects（1）")
            window.click("新建媒体夹", byDescription = true)
            window.waitFor("ordinary bin") { graph.session.document.value!!.bins.size == 1 }
            window.waitFor("saved log") { !graph.session.saveState.value.pending }
            val saved = requireNotNull(graph.projects.load(doc.id))
            assertEquals(metadata, saved.media.single().metadata)
            assertEquals("Interview selects", saved.smartBins.single().name)
        }
    }

    @Test fun duplicateTimelineDeliversAPortraitCutAndKeepsTheLandscapeOriginal() = runBlocking {
        val doc = project(sample("silent-avc.mp4")).copy(name = "Versions ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.click("时间线：", prefix = true, byDescription = true)
            window.click("复制当前时间线")
            window.waitFor("independent duplicate") { graph.session.document.value!!.timelineId != doc.timelineId }
            window.click("时间线设置")
            window.click("1080p 竖屏")
            window.click("应用")
            window.waitFor("portrait raster") { graph.session.document.value!!.format.height == 1920 }
            val portrait = graph.session.document.value!!
            val original = portrait.timelines.single { it.id == doc.timelineId }
            assertEquals(doc.format, original.format)
            assertEquals(doc.media, portrait.media)
            assertEquals(doc.timeline.clips.map { it.assetId }, portrait.timeline.clips.map { it.assetId })
            val settings = portrait.render.copy(fileName = "rve-portrait-${UUID.randomUUID()}", resolution = RenderResolution.HD_720, includeAudio = false)
            val uri = Uri.parse(graph.render.render(portrait, settings, 3, 18) {}.uri)
            imported += uri
            MediaMetadataRetriever().use { video ->
                video.setDataSource(app, uri)
                val width = video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                val height = video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                val rotation = video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)!!.toInt()
                assertEquals(720 to 1280, if (rotation % 180 == 0) width to height else height to width)
                assertTrue(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 480L..520L)
            }
            window.click("时间线：", prefix = true, byDescription = true)
            window.focusWindowContaining("复制当前时间线")
            window.click(doc.timelineName)
            window.waitFor("reopen landscape original") {
                val reopened = graph.session.document.value!!
                reopened.timelineId == doc.timelineId && reopened.format == doc.format && reopened.timeline == doc.timeline
            }
        }
    }

    @Test fun audioDragRejectsVideoLaneAndPlaysFromTheChosenAudioLane() = runBlocking {
        val sound = sample("tone.ogg", "audio/ogg")
        val doc = project().copy(name = "Sound drop ${UUID.randomUUID()}", media = listOf(sound))
        withOpenEditor(doc) { window, graph ->
            val tile = window.bounds(sound.name, prefix = true)
            val timeline = window.bounds("时间线")
            val x = timeline.left + timeline.width() * 0.12f
            window.drag(tile, x, timeline.top + timeline.height() * 0.46f)
            instrumentation.waitForIdleSync()
            assertTrue("audio dropped onto picture is rejected", graph.session.document.value!!.timeline.clips.isEmpty())
            window.drag(tile, x, timeline.top + timeline.height() * 0.68f)
            window.waitFor("audio drop on A1") { graph.session.document.value!!.timeline.clips.isNotEmpty() }
            val clip = graph.session.document.value!!.timeline.clips.single()
            assertEquals(Timeline.A1, clip.trackId)
            assertEquals(sound.id, clip.assetId)
            window.click("播放", byDescription = true)
            val start = clip.start * 1000L / doc.format.fps
            try {
                window.waitFor("play audio from its dropped position") { graph.program.state.value.positionMs > start + 300 }
            } catch (failure: IllegalStateException) {
                throw AssertionError("Audio drop at $start ms, length ${sound.durationMs} ms, monitor ${graph.program.state.value}", failure)
            }
            assertTrue(requireNotNull(graph.previews.waveform(sound.uri)).peak(200, 700) > 0.05f)
        }
    }

    @Test fun multiSelectionBuildsAMulticamClipAndUndoRestoresTheTwoAngles() = runBlocking {
        val original = project(sample("silent-avc.mp4"), sample("silent-hevc.mp4"))
        val timeline = original.timeline.copy(clips = original.timeline.clips.mapIndexed { i, clip ->
            if (i == 0) clip else clip.copy(start = 0, trackId = Timeline.V2)
        })
        val doc = original.copy(name = "Two cameras ${UUID.randomUUID()}", timeline = timeline)
        withOpenEditor(doc) { window, graph ->
            window.bounds("时间线")
            window.key(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
            window.waitFor("select the two camera clips") { graph.session.selectedClipIds.value.size == 2 }
            val top = window.bounds("已选择 2 个片段")
            val lower = window.bounds("把所选的 2 组片段收进一个复合片段", prefix = true)
            window.drag(lower, lower.exactCenterX(), top.top.toFloat(), longPress = false)
            window.click("把所选片段新建为多机位片段", byDescription = true)
            window.waitFor("multicam timeline with two angles") { graph.session.document.value!!.multicamIds.isNotEmpty() }
            val multicam = graph.session.document.value!!
            val angleId = multicam.multicamIds.single()
            assertTrue(multicam.timeline.clips.any { it.compoundId == angleId })
            assertEquals(doc.media.map { it.id }.toSet(), multicam.timelines.single { it.id == angleId }.timeline.clips.mapNotNull { it.assetId }.toSet())
            window.key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            window.waitFor("restore both original camera tracks") { graph.session.document.value!!.timeline == doc.timeline }
        }
    }

    @Test fun gradeVersionsSerialNodesAndGallerySaveActualPixels() = runBlocking {
        val doc = project(sample("silent-avc.mp4")).copy(name = "Color session ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.click("调色", byDescription = true)
            window.click("RGB 分量")
            window.bounds("示波器：RGB 分量")
            window.key(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            window.waitFor("two grade versions") { graph.session.document.value!!.timeline.clips.first().gradeVersions.size == 2 }
            window.click("添加串行节点", prefix = true, byDescription = true)
            window.waitFor("serial node") { graph.session.document.value!!.timeline.clips.first().grade.serial.isNotEmpty() }
            window.click("画廊")
            window.click("抓取静帧：", prefix = true, byDescription = true)
            window.waitFor("gallery still") { graph.session.document.value!!.stills.size == 1 }
            val still = graph.session.document.value!!.stills.single()
            val image = requireNotNull(graph.gallery.load(doc.id, still.image, 192))
            val pixel = image.pixels[image.height / 2 * image.width + image.width / 2]
            assertTrue("gallery uses the project's picture", Color.red(pixel) > 180 && Color.blue(pixel) < 70)
            assertEquals(graph.session.document.value!!.timeline.clips.first().grade, still.grade)
        }
    }

    @Test fun mixerMuteSoloAndReverbUseTheSelectedAudioChannel() = runBlocking {
        val doc = project(sample("tone.wav", "audio/wav")).copy(name = "Mix session ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.click("音频", byDescription = true)
            window.click("静音 A1", byDescription = true)
            window.waitFor("muted first track") { graph.session.document.value!!.mixer.channel(Timeline.A1).mute }
            window.click("静音 A1", byDescription = true)
            window.click("独奏 A1", byDescription = true)
            window.waitFor("solo first track") { graph.session.document.value!!.mixer.channel(Timeline.A1).solo }
            window.click("效果 A1", prefix = true, byDescription = true)
            window.click("混响")
            window.click("混响房间：大厅", byDescription = true)
            window.waitFor("enabled hall reverb") { graph.session.document.value!!.mixer.channel(Timeline.A1).reverb.isActive }
            window.waitFor("saved mix") { !graph.session.saveState.value.pending }
            val mixer = requireNotNull(graph.projects.load(doc.id)).mixer
            assertTrue(mixer.channel(Timeline.A1).solo)
            assertEquals(ReverbRoom.HALL.decaySeconds, mixer.channel(Timeline.A1).reverb.decaySeconds, 0.01f)
            assertFalse(mixer.channel(Timeline.A2).reverb.isActive)
        }
    }

    @Test fun voiceoverUsesTheChosenTrackAndSavesPcmWhenRecordingIsEnabled() = runBlocking {
        val doc = project(sample("silent-avc.mp4")).copy(name = "Voiceover ${UUID.randomUUID()}")
        withOpenEditor(doc) { window, graph ->
            window.click("音频", byDescription = true)
            window.click("录制旁白：", prefix = true, byDescription = true)
            window.focusWindowContaining("开始录音")
            window.click("A2")
            if (InstrumentationRegistry.getArguments().getString("qaRecordVoice") != "true") {
                window.click("取消")
                assertEquals(doc.media, graph.session.document.value!!.media)
                return@withOpenEditor
            }
            try {
                window.click("开始录音")
                window.focusWindowContaining("停止并保存")
                SystemClock.sleep(1000)
                window.click("停止并保存")
                window.waitFor("new recorded asset") { graph.session.document.value!!.media.size == doc.media.size + 1 }
                val updated = graph.session.document.value!!
                val sound = updated.media.single { it.id !in doc.media.map(MediaAsset::id) }
                val clip = updated.timeline.clips.single { it.assetId == sound.id }
                assertEquals(Timeline.A2, clip.trackId)
                assertEquals(0, clip.start)
                assertTrue("take duration ${sound.durationMs} ms", sound.durationMs in 300L..6000L)
                val path = java.io.File(requireNotNull(Uri.parse(sound.uri).path))
                assertEquals(app.filesDir.resolve("recordings").canonicalFile, path.parentFile?.canonicalFile)
                val header = path.inputStream().use { it.readNBytes(44) }
                assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
                assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
                val bytes = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                assertEquals(1, bytes.getShort(20).toInt())
                assertEquals(1, bytes.getShort(22).toInt())
                assertEquals(48000, bytes.getInt(24))
                assertEquals(16, bytes.getShort(34).toInt())
                assertEquals(path.length() - 44, bytes.getInt(40).toLong())
            } finally {
                graph.voice.cancel()
                graph.session.document.value?.media?.filter { it.id !in doc.media.map(MediaAsset::id) }?.forEach { take ->
                    val path = Uri.parse(take.uri).path?.let { java.io.File(it) } ?: return@forEach
                    if (path.parentFile?.canonicalFile == app.filesDir.resolve("recordings").canonicalFile) path.delete()
                }
            }
        }
    }

    @Test fun individualQueueRetriesTheReenabledClipAndCancelsALongRender() = runBlocking {
        val base = project(sample("silent-avc.mp4"), sample("silent-hevc.mp4"))
        val doc = base.copy(name = "Individual queue ${UUID.randomUUID()}", render = base.render.copy(
            fileName = "rve-queue-${UUID.randomUUID()}", individualClips = true, resolution = RenderResolution.HD_720, includeAudio = false))
        withOpenEditor(doc) { window, graph ->
            val secondId = doc.timeline.clips.last().id
            fun enableSecond(enabled: Boolean) = main {
                graph.session.edit("edit queued clip") { d ->
                    d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.map { if (it.id == secondId) it.copy(enabled = enabled) else it }))
                }
            }
            window.click("交付", byDescription = true)
            window.click("加入渲染队列")
            window.waitFor("one job per picture clip") { graph.session.document.value!!.jobs.size == 2 }
            val jobIds = graph.session.document.value!!.jobs.map { it.id }
            enableSecond(false)
            window.click("全部渲染")
            try {
                window.waitFor("one saved picture and one unavailable edit") {
                    graph.session.document.value!!.jobs.map { it.status } == listOf(RenderStatus.DONE, RenderStatus.FAILED)
                }
                val failed = graph.session.document.value!!.jobs.last()
                assertEquals(secondId, failed.clipId)
                assertTrue(failed.errorMessage.orEmpty().contains("停用"))
                enableSecond(true)
                window.click("重试")
                window.waitFor("failed clip back in the queue") { graph.session.document.value!!.jobs.last().status == RenderStatus.QUEUED }
                window.click("全部渲染")
                window.waitFor("both individual outputs saved") { graph.session.document.value!!.jobs.all { it.status == RenderStatus.DONE } }
                val finished = graph.session.document.value!!.jobs
                assertEquals(jobIds, finished.map { it.id })
                assertEquals(2, finished.map { it.outputUri }.toSet().size)
                for (job in finished) {
                    val frame = requireNotNull(graph.previews.framePixels(requireNotNull(job.outputUri), MediaKind.VIDEO, 350, 80, true))
                    val pixel = frame.pixels[frame.height / 2 * frame.width + frame.width / 2]
                    if (job.clipId == secondId) assertTrue(Color.blue(pixel) > 180 && Color.red(pixel) < 70)
                    else assertTrue(Color.red(pixel) > 180 && Color.blue(pixel) < 70)
                }
            } finally {
                graph.session.document.value!!.jobs.mapNotNull { it.outputUri }.forEach { imported += Uri.parse(it) }
            }
            window.click("清除已完成")
            window.waitFor("clear the two records") { graph.session.document.value!!.jobs.isEmpty() }
            val photo = sample("still.bmp", "image/bmp")
            val fileName = "rve-cancel-${UUID.randomUUID()}"
            main { graph.session.edit("long video for cancellation") { d ->
                val clip = d.timeline.clips.first().copy(assetId = photo.id, start = 0, durationFrames = 3600)
                d.copy(media = d.media + photo, timeline = d.timeline.copy(clips = listOf(clip)),
                    render = d.render.copy(individualClips = false, fileName = fileName))
            } }
            window.click("加入渲染队列")
            window.click("全部渲染")
            window.waitFor("long render started") { graph.session.document.value!!.jobs.singleOrNull()?.status == RenderStatus.RENDERING }
            window.click("取消")
            window.waitFor("long render removed") { graph.session.document.value!!.jobs.isEmpty() }
            val movies = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            resolver.query(movies, arrayOf("_id"), "_display_name = ?", arrayOf("$fileName.mp4"), null)!!.use {
                assertEquals("a canceled render must not be published", 0, it.count)
            }
        }
    }
}
