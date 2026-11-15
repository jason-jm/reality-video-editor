package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.timelineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Add Transition (Ctrl+T): the standard transition at the cuts it reaches. */
class AddTransitionsUseCaseTest {

    private val use = AddTransitionsUseCase()
    private val dissolve = Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER)

    // V1: a 0–90, b 90–150, c 200–260 (a gap before c). A1: sa 0–90, sb 90–150 (b's sound). A2: m 0–300.
    private val timeline = timelineOf(
        clipOf("a", 0, 90), clipOf("b", 90, 60), clipOf("c", 200, 60),
        clipOf("sa", 0, 90, Timeline.A1), clipOf("sb", 90, 60, Timeline.A1),
        clipOf("m", 0, 300, Timeline.A2),
    )

    @Test
    fun `the cut nearest the playhead gets a dissolve on picture and a cross fade on sound`() {
        assertEquals(90, use.nearestCut(timeline, 170, TransitionScope.BOTH))
        assertNull("c follows a gap, m runs through: no other cut", use.nearestCut(timeline.copy(clips = timeline.clips.filter { it.id in setOf("c", "m") }), 0, TransitionScope.BOTH))
        val result = use.add(timeline, setOf(90), TransitionScope.BOTH, 30)
        assertEquals(dissolve, result.timeline.clip("b")!!.transitionIn)
        assertEquals(15, result.timeline.clip("sb")!!.crossFadeFrames)
        assertNull("the clips before keep theirs", result.timeline.clip("a")!!.transitionIn)
        assertEquals("已在 00:00:03:00 添加 1 个交叉叠化、1 个交叉淡化", result.message)
        assertNull("no selection made", result.selectedClipId)
        assertThrows("again: there already is one", EditRejected::class.java) { use.add(result.timeline, setOf(90), TransitionScope.BOTH, 30) }
    }

    @Test
    fun `Alt+T reaches picture only and Shift+T sound only`() {
        val video = use.add(timeline, setOf(90), TransitionScope.VIDEO, 30).timeline
        assertEquals(dissolve, video.clip("b")!!.transitionIn)
        assertEquals(0, video.clip("sb")!!.crossFadeFrames)
        val audio = use.add(timeline, setOf(90), TransitionScope.AUDIO, 30)
        assertNull(audio.timeline.clip("b")!!.transitionIn)
        assertEquals(15, audio.timeline.clip("sb")!!.crossFadeFrames)
        assertEquals("已在 00:00:03:00 添加 1 个交叉淡化", audio.message)
    }

    @Test
    fun `a selection reaches its own ends, half a second or half the shorter clip`() {
        val short = timeline.replaceClip(timeline.clip("a")!!.copy(durationFrames = 20, start = 70))
        val cuts = use.selectionCuts(short, listOf("b"), TransitionScope.VIDEO)
        assertEquals("b's in point only: nothing starts where it ends", setOf(90), cuts)
        val result = use.add(short, cuts, TransitionScope.VIDEO, 30, within = listOf("b"))
        assertEquals("half the 20-frame clip before", 10, result.timeline.clip("b")!!.transitionIn!!.frames)
        assertEquals(listOf("b"), result.selection)
        val tail = use.selectionCuts(timeline, listOf("a"), TransitionScope.VIDEO)
        assertEquals("a's out point is b's cut", setOf(90), tail)
        assertEquals(dissolve, use.add(timeline, tail, TransitionScope.VIDEO, 30, within = listOf("a")).timeline.clip("b")!!.transitionIn)
        assertThrows(EditRejected::class.java) { use.add(timeline, use.selectionCuts(timeline, listOf("c"), TransitionScope.BOTH), TransitionScope.BOTH, 30, within = listOf("c")) }
    }

    @Test
    fun `locked tracks are left alone`() {
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.A1) it.copy(locked = true) else it })
        val result = use.add(locked, setOf(90), TransitionScope.BOTH, 30)
        assertEquals(0, result.timeline.clip("sb")!!.crossFadeFrames)
        assertEquals(dissolve, result.timeline.clip("b")!!.transitionIn)
        val e = assertThrows(EditRejected::class.java) { use.add(locked, setOf(90), TransitionScope.AUDIO, 30) }
        assertEquals("剪辑点所在的轨道已锁定", e.message)
    }
}
