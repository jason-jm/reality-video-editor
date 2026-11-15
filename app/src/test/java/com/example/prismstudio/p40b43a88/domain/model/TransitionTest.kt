package com.example.prismstudio.p40b43a88.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve's transition inspector: ease curves, which kinds have a direction or an edge, and the menu's groups. */
class TransitionTest {

    @Test
    fun `ease curves start slow, end slow or both, and always run from 0 to 1`() {
        val t = 0.4f
        assertEquals(0.4f, TransitionEase.LINEAR.apply(t), 1e-6f)
        assertEquals("2t² − t³", 0.256f, TransitionEase.IN.apply(t), 1e-6f)
        assertEquals("t + t² − t³", 0.496f, TransitionEase.OUT.apply(t), 1e-6f)
        assertEquals("3t² − 2t³", 0.352f, TransitionEase.IN_OUT.apply(t), 1e-6f)
        TransitionEase.entries.forEach { ease ->
            assertEquals(0f, ease.apply(0f), 1e-6f)
            assertEquals(1f, ease.apply(1f), 1e-6f)
            assertEquals("held to the transition", 1f, ease.apply(1.5f), 1e-6f)
            val samples = (0..20).map { ease.apply(it / 20f) }
            assertTrue("$ease never turns back", samples.zipWithNext().all { (a, b) -> b >= a })
        }
    }

    @Test
    fun `push and the shape wipes have two directions, the wipe an angle, the wipes an edge, the dissolves neither`() {
        assertEquals("从右推入" to "从左推入", TransitionType.PUSH.directions)
        assertEquals("顺时针" to "逆时针", TransitionType.CLOCK_WIPE.directions)
        assertEquals("从中心展开" to "向中心收拢", TransitionType.IRIS.directions)
        assertEquals(
            setOf(TransitionType.PUSH, TransitionType.IRIS, TransitionType.CLOCK_WIPE, TransitionType.BARN_DOOR),
            TransitionType.entries.filter { it.directional }.toSet(),
        )
        assertEquals("the wipe turns by its angle instead (Resolve's Edge Wipe)", listOf(TransitionType.WIPE), TransitionType.entries.filter { it.angled })
        assertEquals(
            setOf(TransitionType.WIPE, TransitionType.IRIS, TransitionType.CLOCK_WIPE, TransitionType.BARN_DOOR),
            TransitionType.entries.filter { it.edged }.toSet(),
        )
        assertNull(TransitionType.CROSS_DISSOLVE.directions)
        assertFalse(TransitionType.ZOOM_BLUR.edged)
    }

    @Test
    fun `alignment puts all, half or none of the transition before the cut, ordered as on the timeline`() {
        assertEquals(listOf(30, 15, 0), TransitionAlignment.entries.map { it.lead(30) })
        assertEquals("an odd length leaves the extra frame after the cut", 7, TransitionAlignment.CENTER.lead(15))
        assertEquals(listOf("剪辑点前", "居中", "剪辑点后"), TransitionAlignment.entries.map { it.label })
        assertEquals("projects from before alignments start at the cut", TransitionAlignment.START, Transition(TransitionType.WIPE, 10).alignment)
        assertEquals(
            setOf(TransitionType.DIP_TO_BLACK, TransitionType.DIP_TO_WHITE, TransitionType.DIP_TO_COLOR, TransitionType.ZOOM_BLUR),
            TransitionType.entries.filterNot { it.reveals }.toSet(),
        )
        assertEquals("dips show the outgoing picture for their first half", listOf(TransitionType.ZOOM_BLUR), TransitionType.entries.filterNot { it.showsOutgoing })
        assertEquals(
            setOf(TransitionType.DIP_TO_BLACK, TransitionType.DIP_TO_WHITE, TransitionType.DIP_TO_COLOR),
            TransitionType.entries.filter { it.dips }.toSet(),
        )
        assertEquals("only the colour dip takes a colour", listOf(TransitionType.DIP_TO_COLOR), TransitionType.entries.filter { it.colored })
    }

    @Test
    fun `a wipe's angle reads -179 to 180, its old reverse folded in as half a turn`() {
        assertEquals(listOf(0, 90, -90, 180, 180, -179, 1), listOf(0, 90, 270, 180, -180, 181, 361).map { Transition.normalizedAngle(it) })
        assertEquals(0, Transition(TransitionType.WIPE, 10).travelAngle)
        assertEquals("a reversed wipe from before angles goes right to left", 180, Transition(TransitionType.WIPE, 10, reverse = true).travelAngle)
        assertEquals(-135, Transition(TransitionType.WIPE, 10, reverse = true, angle = 45).travelAngle)
    }

    @Test
    fun `the menu groups hold every kind once`() {
        val listed = TransitionType.GROUPS.flatten()
        assertEquals(TransitionType.entries.size, listed.size)
        assertEquals(TransitionType.entries.toSet(), listed.toSet())
    }

    @Test
    fun `a transition starts steady, forward, hard-edged and without a border`() {
        val plain = Transition(TransitionType.IRIS, 15)
        assertEquals(TransitionEase.LINEAR, plain.ease)
        assertFalse(plain.reverse)
        assertEquals(0, plain.softness)
        assertEquals(0, plain.border)
        assertEquals(TitleColor.WHITE, plain.borderColor)
        assertEquals("a colour dip starts amber, neither the black nor the white dip", TitleColor.AMBER, Transition(TransitionType.DIP_TO_COLOR, 15).color)
        assertEquals("it sits with the dissolves", TransitionType.GROUPS.first().last(), TransitionType.DIP_TO_COLOR)
    }
}
