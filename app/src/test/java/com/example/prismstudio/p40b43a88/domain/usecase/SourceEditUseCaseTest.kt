package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Three-point editing: the source's marked range onto the timeline at the playhead, or at its end. */
class SourceEditUseCaseTest {

    private val fps = 30
    private val edits = SourceEditUseCase(SequentialIds())
    private val beach = assetOf("b", durationMs = 10_000)
    private val song = assetOf("s", kind = MediaKind.AUDIO, durationMs = 20_000)
    private val still = assetOf("p", kind = MediaKind.IMAGE)

    // V1: A (linked to its sound on A1) 0–90, B 90–150; A2: music 0–300; V2: a title 30–60.
    private val a = clipOf("A", 0, 90, linkId = "L")
    private val aSound = clipOf("As", 0, 90, track = Timeline.A1, linkId = "L")
    private val b = clipOf("B", 90, 60)
    private val music = clipOf("M", 0, 300, track = Timeline.A2)
    private val title = clipOf("T", 30, 30, track = Timeline.V2, kind = ClipKind.TITLE)
    private val timeline = timelineOf(a, aSound, b, music, title)

    private fun Timeline.on(trackId: String): List<Pair<Int, Int>> = clipsOn(trackId).map { it.start to it.end }

    private fun edit(mode: SourceEdit, at: Int, t: Timeline = timeline, inMs: Long = 1_000, outMs: Long = 2_000) =
        edits(t, beach, inMs, outMs, at, mode, fps)

    @Test
    fun `insert splits what plays at the playhead and pushes every unlocked track later`() {
        val result = edit(SourceEdit.INSERT, 60)
        val t = result.timeline
        assertEquals(listOf(0 to 60, 60 to 90, 90 to 120, 120 to 180), t.on(Timeline.V1))
        assertEquals("its sound too", listOf(0 to 60, 60 to 90, 90 to 120), t.on(Timeline.A1))
        assertEquals("the music opens the same gap", listOf(0 to 60, 90 to 330), t.on(Timeline.A2))
        assertEquals("a title ending at the playhead stays", listOf(30 to 60), t.on(Timeline.V2))
        val inserted = t.clip(result.selectedClipId)!!
        assertEquals(60 to 90, inserted.start to inserted.end)
        assertEquals(1_000L, inserted.sourceInMs)
        val pair = t.clips.filter { it.start == 60 && it.assetId == "b" }
        assertEquals("picture and sound linked", 1, pair.map { it.linkId }.distinct().size)
        val rights = t.clips.filter { it.start == 90 && it.trackId in setOf(Timeline.V1, Timeline.A1) }
        assertEquals("the split halves stay linked to each other", 1, rights.map { it.linkId }.distinct().size)
        assertNotEquals("but not to the left halves", "L", rights.first().linkId)
        assertEquals("已在 00:00:02:00 插入“b”（00:00:01:00），后面的片段后移", result.message)
    }

    @Test
    fun `insert leaves a locked track where it is`() {
        val locked = timelineOf(a, aSound, b, music, title, locked = setOf(Timeline.A2))
        val t = edit(SourceEdit.INSERT, 60, locked).timeline
        assertEquals(listOf(0 to 300), t.on(Timeline.A2))
        assertEquals(listOf(0 to 60, 60 to 90, 90 to 120, 120 to 180), t.on(Timeline.V1))
        val onTarget = runCatching { edit(SourceEdit.INSERT, 60, timelineOf(a, aSound, locked = setOf(Timeline.V1))) }.exceptionOrNull()
        assertEquals("目标轨道已锁定", onTarget?.message)
    }

    @Test
    fun `overwrite replaces what is there on the target tracks only`() {
        val result = edit(SourceEdit.OVERWRITE, 75)
        val t = result.timeline
        assertEquals(listOf(0 to 75, 75 to 105, 105 to 150), t.on(Timeline.V1))
        assertEquals(listOf(0 to 75, 75 to 105), t.on(Timeline.A1))
        assertEquals("the music plays on", listOf(0 to 300), t.on(Timeline.A2))
        assertTrue(result.message.endsWith("，覆盖了原有内容"))
        assertEquals("nothing there: nothing overwritten", "已在 00:00:20:00 覆盖“b”（00:00:01:00）", edit(SourceEdit.OVERWRITE, 600).message)
    }

    @Test
    fun `place on top goes to the first free video track above V1, a new one when none is free`() {
        val first = edit(SourceEdit.PLACE_ON_TOP, 90)
        val placed = first.timeline.clip(first.selectedClipId)!!
        assertEquals(Timeline.V2, placed.trackId)
        assertEquals("its sound on the first free audio track (A1 is free at 90)", Timeline.A1, first.timeline.clips.single { it.linkId == placed.linkId && it.kind == ClipKind.AUDIO }.trackId)
        // Over the title on V2 the picture needs a new V3; the sound a new A3 (A1 and A2 busy).
        val second = edit(SourceEdit.PLACE_ON_TOP, 40)
        val top = second.timeline.clip(second.selectedClipId)!!
        assertEquals("v3", top.trackId)
        assertEquals("a3", second.timeline.clips.single { it.linkId == top.linkId && it.kind == ClipKind.AUDIO }.trackId)
        assertTrue(second.timeline.tracks.any { it.id == "v3" && it.kind == TrackKind.VIDEO })
        assertEquals("已叠加“b”到 V3 · 00:00:01:10（00:00:01:00）", second.message)
        // Three busy video tracks: a new V4 (eight of each kind at most, rev 158) …
        val full = second.timeline.copy(clips = second.timeline.clips + clipOf("X", 0, 300, track = Timeline.V2))
        val busy = full.copy(clips = full.clips + clipOf("Y", 0, 300, track = "v3"))
        assertEquals("v4", edit(SourceEdit.PLACE_ON_TOP, 40, busy).let { it.timeline.clip(it.selectedClipId)!!.trackId })
        // … and all eight busy: refused.
        var packed = busy
        for (n in 4..8) {
            packed = packed.withTrack(packed.nextTrack(TrackKind.VIDEO))
            packed = packed.copy(clips = packed.clips + clipOf("Z$n", 0, 300, track = "v$n"))
        }
        assertEquals(8, packed.tracks.count { it.kind == TrackKind.VIDEO })
        assertEquals("没有空闲的视频轨道可以叠加", runCatching { edit(SourceEdit.PLACE_ON_TOP, 40, packed) }.exceptionOrNull()?.message)
    }

    @Test
    fun `sound goes to the music track, a still lasts five seconds, append still adds at the end`() {
        val sound = edits(timeline, song, 0, 4_000, 60, SourceEdit.INSERT, fps)
        val inserted = sound.timeline.clip(sound.selectedClipId)!!
        assertEquals(Timeline.A2 to (60 to 180), inserted.trackId to (inserted.start to inserted.end))
        val image = edits(timeline, still, 0, 0, 30, SourceEdit.OVERWRITE, fps).let { it.timeline.clip(it.selectedClipId)!! }
        assertEquals(150, image.durationFrames)
        assertNull("a still has no sound", image.linkId)
        val appended = edit(SourceEdit.APPEND, 0)
        assertEquals(150, appended.timeline.clip(appended.selectedClipId)!!.start)
        assertEquals("素材离线，无法添加：b.mp4", runCatching { edits(timeline, beach.copy(offline = true), 0, 1_000, 0, SourceEdit.INSERT, fps) }.exceptionOrNull()?.message)
        assertTrue(runCatching { edits(timeline, beach.copy(offline = true), 0, 1_000, 0, SourceEdit.INSERT, fps) }.exceptionOrNull() is EditRejected)
    }

    @Test
    fun `the destination controls send the picture and the sound to the chosen tracks`() {
        val targeted = timeline.copy(targets = EditTargets(video = Timeline.V2, audio = Timeline.A2))
        val over = edit(SourceEdit.OVERWRITE, 75, targeted)
        val picture = over.timeline.clip(over.selectedClipId)!!
        assertEquals(Timeline.V2 to (75 to 105), picture.trackId to (picture.start to picture.end))
        assertEquals("V1 is left alone", timeline.on(Timeline.V1), over.timeline.on(Timeline.V1))
        assertEquals("the sound overwrites the music", listOf(0 to 75, 75 to 105, 105 to 300), over.timeline.on(Timeline.A2))
        assertEquals("A1 is left alone", timeline.on(Timeline.A1), over.timeline.on(Timeline.A1))
        assertEquals(picture.linkId, over.timeline.clips.single { it.trackId == Timeline.A2 && it.start == 75 }.linkId)
        // Automatic sound: a video's own sound on A1 under a picture on V2; sound-only media on the music track.
        val auto = timeline.copy(targets = EditTargets(video = Timeline.V2))
        val inserted = edit(SourceEdit.INSERT, 120, auto)
        assertEquals(Timeline.V2, inserted.timeline.clip(inserted.selectedClipId)!!.trackId)
        assertEquals(listOf(0 to 90, 120 to 150), inserted.timeline.on(Timeline.A1))
        val music = edits(auto, song, 0, 1_000, 0, SourceEdit.OVERWRITE, fps)
        assertEquals(Timeline.A2, music.timeline.clip(music.selectedClipId)!!.trackId)
        // A chosen sound track takes sound-only media too; a target on a track that is gone falls back.
        val onA1 = edits(timeline.copy(targets = EditTargets(audio = Timeline.A1)), song, 0, 1_000, 300, SourceEdit.OVERWRITE, fps)
        assertEquals(Timeline.A1, onA1.timeline.clip(onA1.selectedClipId)!!.trackId)
        val gone = edit(SourceEdit.OVERWRITE, 300, timeline.copy(targets = EditTargets(video = "v9", audio = "a9")))
        assertEquals(setOf(Timeline.V1, Timeline.A1), gone.timeline.clips.filter { it.start == 300 }.map { it.trackId }.toSet())
    }

    @Test
    fun `append and place on top follow the picture target`() {
        val targeted = timeline.copy(targets = EditTargets(video = Timeline.V2))
        val appended = edit(SourceEdit.APPEND, 0, targeted)
        val clip = appended.timeline.clip(appended.selectedClipId)!!
        assertEquals("after the title on V2 and the sound on A1", Timeline.V2 to 90, clip.trackId to clip.start)
        val image = edits(targeted, still, 0, 0, 0, SourceEdit.APPEND, fps)
        assertEquals(Timeline.V2 to 60, image.timeline.clip(image.selectedClipId)!!.let { it.trackId to it.start })
        val sound = edits(timeline.copy(targets = EditTargets(audio = Timeline.A1)), song, 0, 1_000, 0, SourceEdit.APPEND, fps)
        assertEquals(Timeline.A1 to 90, sound.timeline.clip(sound.selectedClipId)!!.let { it.trackId to it.start })
        // Above V2 there is no track yet: place on top makes V3.
        val top = edit(SourceEdit.PLACE_ON_TOP, 90, targeted)
        assertEquals("v3", top.timeline.clip(top.selectedClipId)!!.trackId)
    }

    @Test
    fun `replace swaps the media under the playhead, lining up the source playhead with the timeline's`() {
        val dressed = a.copy(grade = ColorGrade(exposure = 0.5f), transform = ClipTransform(scalePercent = 50), speed = ClipSpeed.of(PlaybackSpeed.DOUBLE), reverse = true)
        val t = timelineOf(dressed, aSound, b, music, title)
        val result = edits(t, beach, 0, 10_000, 30, SourceEdit.REPLACE, fps, syncMs = 4_000)
        val picture = result.timeline.clip(result.selectedClipId)!!
        assertEquals("A", picture.id)
        assertEquals(listOf("b", "b"), listOf(picture.assetId, result.timeline.clip("As")!!.assetId))
        assertEquals("frame 30 (1 s in) plays source 4 s", listOf(3_000L, 3_000L), listOf(picture.sourceInMs, result.timeline.clip("As")!!.sourceInMs))
        assertEquals("place and length stay", 0 to 90, picture.start to picture.end)
        assertEquals("b", picture.name)
        assertEquals("the look stays", dressed.grade to dressed.transform, picture.grade to picture.transform)
        assertEquals("speed and reverse were about the old media", ClipSpeed.NORMAL to false, picture.speed to picture.reverse)
        assertEquals("L", result.timeline.clip("As")!!.linkId)
        assertEquals(t.clips.filterNot { it.id in setOf("A", "As") }, result.timeline.clips.filterNot { it.id in setOf("A", "As") })
        assertEquals("已用“b”替换 V1 上的“A”（00:00:03:00），声音一并替换", result.message)
        assertEquals("no source playhead: the media's start on the clip's", 0L, edits(t, beach, 0, 10_000, 30, SourceEdit.REPLACE, fps).timeline.clip("A")!!.sourceInMs)
        // Picture-only media leaves the old sound.
        val mute = edits(t, beach.copy(hasAudio = false), 0, 10_000, 30, SourceEdit.REPLACE, fps, syncMs = 4_000)
        assertEquals("As", mute.timeline.clip("As")!!.name)
        assertTrue(mute.message.endsWith("，新素材没有声音，原声保留"))
        // Sound-only media replaces on the music track.
        val tune = edits(t, song, 0, 20_000, 100, SourceEdit.REPLACE, fps, syncMs = 5_000)
        assertEquals("M" to 1_667L, tune.selectedClipId to tune.timeline.clip("M")!!.sourceInMs)
        assertEquals("s", tune.timeline.clip("M")!!.assetId)
    }

    @Test
    fun `replace is refused when the media runs out, nothing is there, or the clip cannot take media`() {
        fun refusal(at: Int, syncMs: Long, t: Timeline = timeline, asset: com.example.prismstudio.p40b43a88.domain.model.MediaAsset = beach) =
            (runCatching { edits(t, asset, 0, asset.durationMs, at, SourceEdit.REPLACE, fps, syncMs) }.exceptionOrNull() as EditRejected).message
        assertEquals("“b”在源播放头之前少了 15 帧素材，填不满片段开头：把源播放头往后移", refusal(30, 500))
        assertEquals("“b”在源播放头之后少了 1.5 秒素材，填不满片段结尾：把源播放头往前移", refusal(30, 9_500))
        assertEquals("播放头处的 V1 上没有片段可替换", refusal(400, 0))
        assertEquals("标题片段不能替换为素材", refusal(40, 0, timeline.copy(targets = EditTargets(video = Timeline.V2))))
        assertEquals("链接的片段在已锁定的轨道上", refusal(30, 4_000, timelineOf(a, aSound, b, music, locked = setOf(Timeline.A1))))
        assertEquals("a still fills any length", 0L, edits(timeline, still, 0, 0, 30, SourceEdit.REPLACE, fps, 0).timeline.clip("A")!!.sourceInMs)
    }

    @Test
    fun `timeline in and out take the playhead's place and are used up`() {
        // In and Out: the timeline decides the length (the source runs from its In for 2 s, not 1 s).
        val both = edit(SourceEdit.OVERWRITE, 0, timeline.copy(marks = TimelineMarks(60, 120)), inMs = 1_000, outMs = 2_000)
        val placed = both.timeline.clip(both.selectedClipId)!!
        assertEquals(60 to 120, placed.start to placed.end)
        assertEquals(1_000L, placed.sourceInMs)
        assertEquals("the marks are used up", TimelineMarks(), both.timeline.marks)
        assertTrue(both.message.endsWith("，按时间线入出点放置"))
        // In alone: the source's length from In; Out alone: backtimed to end there.
        val fromIn = edit(SourceEdit.INSERT, 0, timeline.copy(marks = TimelineMarks(inFrame = 90)))
        assertEquals(90 to 120, fromIn.timeline.clip(fromIn.selectedClipId)!!.let { it.start to it.end })
        val toOut = edit(SourceEdit.OVERWRITE, 0, timeline.copy(marks = TimelineMarks(outFrame = 150)))
        assertEquals(120 to 150, toOut.timeline.clip(toOut.selectedClipId)!!.let { it.start to it.end })
        val top = edit(SourceEdit.PLACE_ON_TOP, 0, timeline.copy(marks = TimelineMarks(90, 100)))
        assertEquals(Timeline.V2 to (90 to 100), top.timeline.clip(top.selectedClipId)!!.let { it.trackId to (it.start to it.end) })
        // A replace keeps to the playhead and leaves the marks alone.
        val marked = timeline.copy(marks = TimelineMarks(60, 120))
        val replaced = edits(marked, beach, 0, 10_000, 30, SourceEdit.REPLACE, fps)
        assertEquals("A", replaced.selectedClipId)
        assertEquals(TimelineMarks(60, 120), replaced.timeline.marks)
    }

    @Test
    fun `fit to fill retimes the source range to the timeline's in and out`() {
        // 4 s of source into 2 s of timeline: twice as fast, picture and sound, over what was there.
        val fit = edits(timeline.copy(marks = TimelineMarks(60, 120)), beach, 1_000, 5_000, 0, SourceEdit.FIT_TO_FILL, fps)
        val picture = fit.timeline.clip(fit.selectedClipId)!!
        assertEquals(60 to 120, picture.start to picture.end)
        assertEquals(ClipSpeed.of(PlaybackSpeed.DOUBLE), picture.speed)
        assertEquals(1_000L, picture.sourceInMs)
        assertEquals(4_000L, picture.sourceDurationMs(fps))
        val sound = fit.timeline.clips.single { it.linkId == picture.linkId && it.kind == ClipKind.AUDIO }
        assertEquals(picture.speed, sound.speed)
        assertEquals("used up", TimelineMarks(), fit.timeline.marks)
        assertEquals("已把“b”（00:00:04:00）以 2× 适配填充到 00:00:02:00 → 00:00:04:00，覆盖了原有内容", fit.message)
        // Between the presets: 4 s into 3 s.
        val odd = edits(timeline.copy(marks = TimelineMarks(150, 240)), beach, 1_000, 5_000, 0, SourceEdit.FIT_TO_FILL, fps)
        val oddClip = odd.timeline.clip(odd.selectedClipId)!!
        assertEquals("1.33×", oddClip.speed.label)
        assertEquals(4_000L, oddClip.sourceDurationMs(fps))
        // A still just fills the range.
        val held = edits(timeline.copy(marks = TimelineMarks(300, 450)), still, 0, 0, 0, SourceEdit.FIT_TO_FILL, fps)
        assertEquals(ClipSpeed.NORMAL to 150, held.timeline.clip(held.selectedClipId)!!.let { it.speed to it.durationFrames })
    }

    @Test
    fun `fit to fill needs both marks and a playable speed`() {
        fun refusal(t: Timeline, inMs: Long = 1_000, outMs: Long = 5_000) =
            runCatching { edits(t, beach, inMs, outMs, 0, SourceEdit.FIT_TO_FILL, fps) }.exceptionOrNull()?.message
        assertEquals("适配填充要先在时间线上标记入点和出点", refusal(timeline))
        assertEquals("适配填充要先在时间线上标记入点和出点", refusal(timeline.copy(marks = TimelineMarks(inFrame = 60))))
        assertEquals("要以 300× 播放才能填满入出点，超出 0.1×–10× 的范围", refusal(timeline.copy(marks = TimelineMarks(0, 1)), 0, 10_000))
    }

    @Test
    fun `marks the source cannot fill are refused`() {
        val short = runCatching { edit(SourceEdit.OVERWRITE, 0, timeline.copy(marks = TimelineMarks(0, 300)), inMs = 1_000, outMs = 2_000) }
        assertEquals("“b”从源入点起只有 00:00:09:00，填不满时间线入出点（00:00:10:00）", short.exceptionOrNull()?.message)
        val early = runCatching { edit(SourceEdit.INSERT, 0, timeline.copy(marks = TimelineMarks(outFrame = 20))) }
        assertEquals("时间线出点之前放不下“b”（需要 00:00:01:00）", early.exceptionOrNull()?.message)
        val still = edits(timeline.copy(marks = TimelineMarks(0, 300)), still, 0, 0, 0, SourceEdit.OVERWRITE, fps)
        assertEquals("a still fills any marked length", 300, still.timeline.clip(still.selectedClipId)!!.durationFrames)
    }

    @Test
    fun `ripple overwrite takes the clip under the playhead at the source's length and closes up every unlocked track`() {
        // 1 s of source for A (3 s): picture and sound go in, B follows straight on, and the other tracks lose
        // the last 2 s of A's range — the music jumps there and the title over it goes — so all stays in sync.
        val result = edit(SourceEdit.RIPPLE_OVERWRITE, 45)
        val t = result.timeline
        assertEquals(listOf(0 to 30, 30 to 90), t.on(Timeline.V1))
        assertEquals("the new media, then B", listOf("b", "B"), t.clipsOn(Timeline.V1).map { if (it.assetId == "b") "b" else it.id })
        assertEquals(listOf(0 to 30), t.on(Timeline.A1))
        assertEquals(listOf(0 to 30, 30 to 240), t.on(Timeline.A2))
        assertEquals(emptyList<Pair<Int, Int>>(), t.on(Timeline.V2))
        val placed = t.clip(result.selectedClipId)!!
        assertEquals(1_000L to "b", placed.sourceInMs to placed.assetId)
        val sound = t.clips.single { it.trackId == Timeline.A1 }
        assertTrue("picture and sound linked", placed.linkId != null && sound.linkId == placed.linkId)
        assertEquals("已在 00:00:00:00 波纹覆盖“b”（00:00:01:00，原为 00:00:03:00），后面的片段前移 00:00:02:00，其他轨道剪去了同样长度", result.message)
        // A locked track stays whole (and out of step): lock the music to keep it running.
        val locked = edit(SourceEdit.RIPPLE_OVERWRITE, 45, timelineOf(a, aSound, b, music, title, locked = setOf(Timeline.A2))).timeline
        assertEquals(listOf(0 to 300), locked.on(Timeline.A2))
        assertEquals(listOf(0 to 30, 30 to 90), locked.on(Timeline.V1))
    }

    @Test
    fun `ripple overwrite of the timeline's in and out with a longer source opens every unlocked track`() {
        // 2 s of source for 1 s of B (90–120): B's rest and the music after 120 move a second later.
        val result = edit(SourceEdit.RIPPLE_OVERWRITE, 0, timeline.copy(marks = TimelineMarks(90, 120)), inMs = 1_000, outMs = 3_000)
        val t = result.timeline
        assertEquals(listOf(0 to 90, 90 to 150, 150 to 180), t.on(Timeline.V1))
        assertEquals("B keeps its id and its rest of the media", 1_000L, t.clip("B")!!.sourceInMs)
        assertEquals(listOf(0 to 90, 90 to 150), t.on(Timeline.A1))
        assertEquals("the music is split at the range's end and moves on", listOf(0 to 120, 150 to 330), t.on(Timeline.A2))
        assertEquals(listOf(30 to 60), t.on(Timeline.V2))
        assertEquals("used up", TimelineMarks(), t.marks)
        assertEquals("已在 00:00:03:00 波纹覆盖“b”（00:00:02:00，原为 00:00:01:00），后面的片段后移 00:00:01:00，按时间线入出点替换", result.message)
        // The same length moves nothing.
        val same = edit(SourceEdit.RIPPLE_OVERWRITE, 100, inMs = 1_000, outMs = 3_000)
        assertEquals(listOf(0 to 90, 90 to 150), same.timeline.on(Timeline.V1))
        assertEquals(listOf(0 to 300), same.timeline.on(Timeline.A2))
        assertTrue(same.message.endsWith("，长度相同，后面的片段不动"))
    }

    @Test
    fun `ripple overwrite needs a clip under the playhead or both marks, on unlocked tracks`() {
        fun refusal(t: Timeline, at: Int) = runCatching { edit(SourceEdit.RIPPLE_OVERWRITE, at, t) }.exceptionOrNull()?.message
        assertEquals("播放头处的 V1 上没有片段可波纹覆盖", refusal(timeline, 200))
        assertEquals("波纹覆盖要用时间线的入点和出点：再标记一个点，或清除标记后替换播放头处的片段", refusal(timeline.copy(marks = TimelineMarks(inFrame = 30)), 45))
        assertEquals("目标轨道已锁定", refusal(timelineOf(a, aSound, b, locked = setOf(Timeline.A1)), 45))
        // Sound-only media takes the clip on the sound target's place: the music, here.
        val swapped = edits(timelineOf(music), song, 0, 2_000, 100, SourceEdit.RIPPLE_OVERWRITE, fps)
        assertEquals(listOf(0 to 60), swapped.timeline.on(Timeline.A2))
        assertEquals("s", swapped.timeline.clip(swapped.selectedClipId)!!.assetId)
    }

    private fun Timeline.clip(id: String?): Clip? = clips.firstOrNull { it.id == id }
}
