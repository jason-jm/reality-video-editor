package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.data.persistence.ProjectJsonCodec
import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.assetOf
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_ID
import com.example.prismstudio.p40b43a88.domain.model.FIRST_TIMELINE_NAME
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.StoredTimeline
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrimEdge
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Resolve's compound clips: made from a selection, played as their contents, opened, decomposed. */
class CompoundClipUseCaseTest {

    private val compounds = CompoundClipUseCase(SequentialIds())

    // V2: title T 60–120 · V1: A 30–90 (linked to Aa on A1), B 90–120, C 150–180.
    private val cut = timelineOf(
        clipOf("A", 30, 60, assetId = "v", sourceInMs = 1000, linkId = "L"),
        clipOf("Aa", 30, 60, track = Timeline.A1, assetId = "v", sourceInMs = 1000, linkId = "L"),
        titleClipOf("T", 60, 60),
        clipOf("B", 90, 30, assetId = "v"),
        clipOf("C", 150, 30, assetId = "v"),
    )
    private val doc = projectOf(timeline = cut, media = listOf(assetOf("v")))

    private fun rejection(block: () -> Unit): String? = try {
        block(); fail("expected a refusal"); null
    } catch (e: EditRejected) {
        e.message
    }

    /** [doc] with A (and so Aa), T and B gathered: the compound's picture and sound clips. */
    private fun made(): Triple<ProjectDocument, String, String> {
        val d = compounds.create(doc, listOf("A", "T", "B")).doc
        val picture = d.timeline.clips.single { it.isCompound && it.kind == ClipKind.VIDEO }
        val sound = d.timeline.clips.single { it.isCompound && it.kind == ClipKind.AUDIO }
        return Triple(d, picture.id, sound.id)
    }

    @Test
    fun `the selected clips and their sound become one compound clip in their place`() {
        val result = compounds.create(doc, listOf("A", "T", "B"))
        val d = result.doc
        assertEquals("已把 4 个片段收进复合片段“复合片段 1”", result.message)
        // In the parent: C, and the compound clip's picture on the lowest video track used and its sound on A1, linked.
        val picture = d.timeline.clips.single { it.isCompound && it.kind == ClipKind.VIDEO }
        val sound = d.timeline.clips.single { it.isCompound && it.kind == ClipKind.AUDIO }
        assertEquals(result.selectedClipId, picture.id)
        assertEquals(listOf(Timeline.V1, 30, 90, "复合片段 1"), listOf(picture.trackId, picture.start, picture.durationFrames, picture.name))
        assertEquals(listOf(Timeline.A1, 30, 90), listOf(sound.trackId, sound.start, sound.durationFrames))
        assertEquals(picture.linkId, sound.linkId)
        assertEquals(setOf("C", picture.id, sound.id), d.timeline.clips.map { it.id }.toSet())
        // Inside: the clips from frame 0, V2 and V1 as they were, the sound on A1, sources untouched.
        val stored = d.storedTimeline(picture.compoundId)!!
        assertEquals(setOf(stored.id), d.compoundIds)
        assertEquals(listOf("V2", "V1", "A1"), stored.timeline.tracks.map { it.name })
        val inside = stored.timeline.clips.associateBy { it.id }
        assertEquals(listOf("v1", 0, 1000L), inside.getValue("A").let { listOf(it.trackId, it.start, it.sourceInMs) })
        assertEquals("v2" to 30, inside.getValue("T").let { it.trackId to it.start })
        assertEquals("v1" to 60, inside.getValue("B").let { it.trackId to it.start })
        assertEquals("a1" to 0, inside.getValue("Aa").let { it.trackId to it.start })
        assertEquals(90, stored.timeline.durationFrames)
        // The media pool lists it, badged; the timeline menu and render-all leave it out.
        assertEquals(listOf(false, true), d.timelineEntries.map { it.compound })
        assertEquals(listOf(false, true), TimelinePoolUseCase()(d).map { it.compound })
        assertEquals("1 条时间线 · 1 个复合片段 · 当前打开“$FIRST_TIMELINE_NAME”", TimelinePoolUseCase().summary(d))
        assertEquals(listOf(FIRST_TIMELINE_ID), RenderQueueUseCase(SequentialIds()).enqueueAll(d).map { it.timelineId })
    }

    @Test
    fun `a clip left between the chosen ones, a lock or a subtitle keeps the compound clip from being made`() {
        assertEquals("V1 在所选片段之间还有没选中的片段：一起选中，或先移开", compounds.unavailable(cut, listOf("A", "C")))
        assertNull(compounds.unavailable(cut, listOf("A", "B", "C")))
        assertEquals("所选片段在已锁定的轨道上", compounds.unavailable(cut.copy(tracks = cut.tracks.map { it.copy(locked = it.id == Timeline.V2) }), listOf("T")))
        assertEquals("请先在时间线上选择片段", compounds.unavailable(cut, emptyList()))
        assertEquals("V1 在所选片段之间还有没选中的片段：一起选中，或先移开", rejection { compounds.create(doc, listOf("A", "C")) })
    }

    @Test
    fun `a compound clip plays its contents in its window, the upper track on a track of its own`() {
        val (made, pictureId, soundId) = made()
        // Trimmed by 10 frames at its head, its sound 6 dB up and A1 inside 2 dB down.
        val trimmed = made.timeline.clips.map {
            when (it.id) {
                pictureId -> it.copy(start = 40, durationFrames = 70, sourceInMs = 333)
                soundId -> it.copy(start = 40, durationFrames = 70, sourceInMs = 333, volumeDb = 6f)
                else -> it
            }
        }
        val id = made.compoundIds.single()
        val inner = made.timelines.single { it.id == id }
        val d = made.copy(
            timeline = made.timeline.copy(clips = trimmed),
            timelines = listOf(inner.copy(mixer = inner.mixer.replace(inner.mixer.channel("a1").copy(volumeDb = -2f)))),
        )
        val played = CompoundClipUseCase.expanded(d)
        assertEquals(listOf("v2", "v1^1", "v1", "a1", "a2"), played.timeline.tracks.map { it.id })
        val clips = played.timeline.clips.associateBy { it.id }
        // Window 10…80 of the contents, at 40 on the timeline.
        assertEquals(listOf("v1", 40, 50, 1333L), clips.getValue("$pictureId/A").let { listOf(it.trackId, it.start, it.durationFrames, it.sourceInMs) })
        assertEquals(listOf("v1^1", 60, 50), clips.getValue("$pictureId/T").let { listOf(it.trackId, it.start, it.durationFrames) })
        assertEquals(listOf("v1", 90, 20), clips.getValue("$pictureId/B").let { listOf(it.trackId, it.start, it.durationFrames) })
        assertEquals(listOf("a1", 40, 50, 4f), clips.getValue("$soundId/Aa").let { listOf(it.trackId, it.start, it.durationFrames, it.volumeDb) })
        assertTrue("C" in clips)
        assertTrue(played.timeline.clips.none { it.isCompound })
        // The plan composites V1 with its contents' upper track; the title rides on top.
        val plan = BuildRenderPlanUseCase()(played)
        assertEquals(listOf("v1"), plan.layers.map { it.id })
        assertEquals(listOf("$pictureId/T"), plan.titles.map { it.clipId })
        // A timeline without compound clips is handed back as it is.
        assertTrue(CompoundClipUseCase.expanded(doc) === doc)
    }

    @Test
    fun `a compound clip's fades and transition reach its contents, while its speed stays as it is`() {
        val (made, pictureId, _) = made()
        val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15)
        val dressed = made.copy(timeline = made.timeline.copy(clips = made.timeline.clips.map {
            if (it.id == pictureId) it.copy(fadeOutSeconds = 0.5f, transitionIn = dissolve) else it
        }))
        val clips = CompoundClipUseCase.expanded(dressed).timeline.clips.associateBy { it.id }
        // A opens the lowest track: the transition is its own; B and T end with the clip: both fade out.
        assertEquals(dissolve, clips.getValue("$pictureId/A").transitionIn)
        assertNull(clips.getValue("$pictureId/T").transitionIn)
        assertEquals(listOf(0f, 0.5f, 0.5f), listOf("A", "B", "T").map { clips.getValue("$pictureId/$it").fadeOutSeconds })
        val props = ClipPropertiesUseCase()
        assertEquals("复合片段不能变速：打开后为里面的片段变速", rejection { props.speed(made.timeline, pictureId, ClipSpeed.of(PlaybackSpeed.DOUBLE)) })
        assertEquals("复合片段不能倒放：打开后为里面的片段倒放", rejection { props.reverse(made.timeline, pictureId, true) })
        assertEquals("复合片段不能变速：打开后为里面的片段变速", rejection { RetimeUseCase().addPoint(made, pictureId, 30) })
    }

    @Test
    fun `a compound sound's pitch shifts its contents on top of their own, its picture's leaves them`() {
        val (made, pictureId, soundId) = made()
        val id = made.compoundIds.single()
        val inner = made.timelines.single { it.id == id }
        val d = made.copy(
            timeline = ClipPropertiesUseCase().pitch(made.timeline, soundId, 500),
            timelines = listOf(inner.copy(timeline = inner.timeline.replaceClip(inner.timeline.clip("Aa")!!.copy(pitchCents = 200)))),
        )
        val clips = CompoundClipUseCase.expanded(d).timeline.clips.associateBy { it.id }
        assertEquals(700, clips.getValue("$soundId/Aa").pitchCents)
        assertEquals(0, clips.getValue("$pictureId/A").pitchCents)
        assertEquals("音调 +5", Clip.pitchBadge(500))
        assertEquals("音调 −3.5", Clip.pitchBadge(-350))
    }

    @Test
    fun `another timeline goes on as a nested clip, at its length in this timeline's frames, never one that plays this one`() {
        // 时间线 2: 4 seconds at 60 fps — a picture on V1 with its sound.
        val other = timelineOf(
            clipOf("P", 0, 240, assetId = "v", linkId = "M"),
            clipOf("Pa", 0, 240, track = Timeline.A1, assetId = "v", linkId = "M"),
        )
        val two = doc.copy(timelines = listOf(StoredTimeline("tl-2", "时间线 2", doc.format.copy(fps = 60), other, doc.mixer)))
        val content = compounds.content(two, "tl-2")
        assertEquals(listOf(Timeline.V1 to ClipKind.VIDEO, Timeline.A1 to ClipKind.AUDIO), content.clips.map { it.trackId to it.kind })
        assertEquals(listOf(120, 120), content.clips.map { it.durationFrames })
        assertEquals(1, content.clips.map { it.linkId }.toSet().size)
        // Appended after C (180): it plays 时间线 2's clips there; the panel calls it a nested timeline.
        val placed = ClipboardUseCase(SequentialIds()).paste(two.timeline, content, two.timeline.durationFrames, 30)
        val nested = two.copy(timeline = placed.timeline)
        val clip = nested.timeline.clip(placed.selectedClipId)!!
        assertEquals(180 to "tl-2", clip.start to clip.compoundId)
        assertEquals("嵌套时间线 · 时间线 2", compounds.info(nested, clip)!!.title)
        assertEquals("嵌套时间线", compounds.kindLabel(nested, "tl-2"))
        val played = CompoundClipUseCase.expanded(nested).timeline.clips.single { it.id == "${clip.id}/P" }
        assertEquals(180 to 120, played.start to played.durationFrames)
        // Never this timeline, nor one that plays it — directly or through a compound clip inside it.
        assertEquals("不能把时间线放进它自己里面", compounds.cannotNest(two, FIRST_TIMELINE_ID))
        val loop = two.copy(timelines = two.timelines.map { it.copy(timeline = it.timeline.copy(clips = it.timeline.clips + clipOf("Q", 300, 30).copy(compoundId = FIRST_TIMELINE_ID))) })
        assertEquals("“时间线 2”里面用到了当前时间线，不能放进来", rejection { compounds.content(loop, "tl-2") })
        val (made, pictureId, _) = made()
        val via = made.copy(timelines = made.timelines.map { t -> t.copy(timeline = t.timeline.copy(clips = t.timeline.clips + clipOf("R", 200, 30).copy(compoundId = FIRST_TIMELINE_ID))) } +
            StoredTimeline("tl-3", "时间线 3", made.format, timelineOf(clipOf("S", 0, 30).copy(compoundId = made.compoundIds.single())), made.mixer))
        assertEquals("“时间线 3”里面用到了当前时间线，不能放进来", compounds.cannotNest(via, "tl-3"))
        assertEquals("复合片段", compounds.kindLabel(made, made.timeline.clip(pictureId)!!.compoundId))
    }

    @Test
    fun `compound clips inside compound clips play too, never a timeline inside itself`() {
        val withC = compounds.create(doc, listOf("C")).doc
        val inner = withC.timeline.clips.single { it.isCompound }
        val outer = compounds.create(withC, listOf(inner.id)).doc
        val outerClip = outer.timeline.clips.single { it.isCompound }
        val played = CompoundClipUseCase.expanded(outer).timeline.clips.single { it.id.endsWith("/C") }
        assertEquals("${outerClip.id}/${inner.id}/C", played.id)
        assertEquals(150 to 30, played.start to played.durationFrames)
        // A timeline holding a clip of itself plays nothing of it instead of looping.
        val loopId = inner.compoundId!!
        val looped = outer.copy(timelines = outer.timelines.map { t ->
            if (t.id == loopId) t.copy(timeline = t.timeline.copy(clips = t.timeline.clips + inner.copy(id = "self", start = 40))) else t
        })
        val clips = CompoundClipUseCase.expanded(looped).timeline.clips
        assertEquals(1, clips.count { it.id.endsWith("/C") })
    }

    @Test
    fun `decompose puts the contents back in the clip's place under new ids, the compound kept in the pool`() {
        val (made, pictureId, _) = made()
        val result = compounds.decompose(made, pictureId)
        val d = result.doc
        assertEquals("已分解“复合片段 1”：放回 4 个片段", result.message)
        assertTrue(d.timeline.clips.none { it.isCompound })
        val placed = d.timeline.clips.filter { it.id != "C" }.sortedWith(compareBy({ it.trackId }, { it.start }))
        assertEquals(
            listOf(Triple("a1", 30, 60), Triple("v1", 30, 60), Triple("v1", 90, 30), Triple("v2", 60, 60)),
            placed.map { Triple(it.trackId, it.start, it.durationFrames) },
        )
        assertTrue(placed.none { it.id in setOf("A", "Aa", "B", "T") })
        // The picture and its sound stay linked, under a link of their own.
        val links = placed.filter { it.assetId == "v" && it.start == 30 }.map { it.linkId }
        assertEquals(2, links.size)
        assertEquals(1, links.toSet().size)
        assertTrue(links.first() != "L")
        assertEquals(made.compoundIds, d.compoundIds)
        // Something on V2 in that time keeps the title from coming back there.
        val blocked = made.copy(timeline = made.timeline.copy(clips = made.timeline.clips + titleClipOf("X", 70, 10)))
        assertEquals("V2 在这段时间里还有其他片段，无法分解“复合片段 1”", rejection { compounds.decompose(blocked, pictureId) })
        // With V2 gone, a video track is added on top for it.
        val noV2 = made.copy(timeline = made.timeline.copy(tracks = made.timeline.tracks.filter { it.id != Timeline.V2 }))
        val added = compounds.decompose(noV2, pictureId).doc.timeline
        assertEquals(listOf("V2", "V1", "A1", "A2"), added.tracks.map { it.name })
        assertEquals("v2", added.clips.single { it.kind == ClipKind.TITLE }.trackId)
    }

    @Test
    fun `open edits the compound clip's timeline, back returns, and it is not deleted from inside`() {
        val (made, pictureId, _) = made()
        val opened = compounds.open(made, pictureId)
        val d = opened.doc
        assertEquals("已打开复合片段“复合片段 1”", opened.message)
        assertTrue(d.inCompound)
        assertEquals(listOf(FIRST_TIMELINE_ID), d.compoundTrail)
        assertEquals(listOf(FIRST_TIMELINE_NAME, "复合片段 1"), compounds.trail(d))
        assertEquals(setOf("A", "Aa", "T", "B"), d.timeline.clips.map { it.id }.toSet())
        val timelines = TimelinesUseCase(SequentialIds())
        assertEquals("复合片段的时间线不能在这里删除：回到上一级后删除复合片段", rejection { timelines.delete(d) })
        assertEquals("复合片段的时间线不能复制：回到上一级后复制复合片段", rejection { timelines.duplicate(d) })
        // Edits inside reach the clip outside: a shorter contents, back on the timeline.
        val edited = d.copy(timeline = d.timeline.copy(clips = d.timeline.clips.filter { it.id != "B" }))
        val back = compounds.back(edited)
        assertEquals("已返回“$FIRST_TIMELINE_NAME”", back.message)
        assertEquals(FIRST_TIMELINE_ID, back.doc.timelineId)
        assertEquals(emptyList<String>(), back.doc.compoundTrail)
        assertEquals(setOf("A", "Aa", "T"), back.doc.timelines.single().timeline.clips.map { it.id }.toSet())
        // The timeline menu forgets the way back.
        assertEquals(emptyList<String>(), timelines.open(d, FIRST_TIMELINE_ID).doc.compoundTrail)
        // A timeline a compound clip plays cannot be deleted.
        val nested = made.copy(timelines = made.timelines + StoredTimeline("tl-2", "时间线 2", made.format, Timeline.EMPTY, made.mixer))
        val user = nested.copy(timelines = nested.timelines.map { t ->
            if (t.id in nested.compoundIds) t.copy(timeline = t.timeline.copy(clips = t.timeline.clips + clipOf("N", 0, 30).copy(compoundId = FIRST_TIMELINE_ID))) else t
        })
        assertEquals("“$FIRST_TIMELINE_NAME”被复合片段使用，不能删除", rejection { timelines.delete(user) })
    }

    @Test
    fun `a compound clip trims only as far as its contents reach, and is kept in the project file`() {
        val (made, pictureId, soundId) = made()
        val picture = made.timeline.clip(pictureId)!!
        assertEquals(3000L, made.sourceLengthMs(picture))
        val trim = TrimClipUseCase()
        val later = trim(made.timeline, pictureId, TrimEdge.START, 40, 0, false, 0, 3000L, 30).timeline
        assertEquals(Triple(40, 80, 333L), later.clip(pictureId)!!.let { Triple(it.start, it.durationFrames, it.sourceInMs) })
        // Back out as far as the contents begin, not past them; nor past their end.
        val earlier = trim(later, pictureId, TrimEdge.START, 0, 0, false, 0, 3000L, 30).timeline
        assertEquals(30 to 0L, earlier.clip(pictureId)!!.let { it.start to it.sourceInMs })
        val longer = trim(earlier, pictureId, TrimEdge.END, 170, 0, false, 0, 3000L, 30).timeline
        assertEquals(120, longer.clip(pictureId)!!.end)
        // The project file keeps the clips' timeline, the compound list and the way back.
        val opened = compounds.open(made, pictureId).doc
        val reread = ProjectJsonCodec.decode(ProjectJsonCodec.encode(opened))
        assertEquals(opened.compoundIds, reread.compoundIds)
        assertEquals(opened.compoundTrail, reread.compoundTrail)
        val back = compounds.back(reread).doc
        assertEquals(made.timeline.clip(soundId)!!.compoundId, back.timeline.clip(soundId)!!.compoundId)
    }
}
