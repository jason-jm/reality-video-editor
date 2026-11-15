package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.usecase.PlayRangeUseCase.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Resolve's Play Around Current Frame (/), Play In to Out (Alt+/) and Loop (Ctrl+/). */
class PlayRangeUseCaseTest {

    private val use = PlayRangeUseCase()
    private val none = TimelineMarks()

    @Test
    fun `play around runs two seconds each side of the playhead and returns to it, held inside the timeline`() {
        assertEquals(PlayRangeUseCase.Range(240, 360, returnTo = 300, label = "播放头前后 2 秒"), use.around(300, 900, 30))
        assertEquals(0 to 90, use.around(30, 900, 30).let { it.from to it.to })
        assertEquals(830 to 900, use.around(890, 900, 30).let { it.from to it.to })
        assertThrows(EditRejected::class.java) { use.around(0, 0, 30) }
    }

    @Test
    fun `play in to out needs both marks with frames between them`() {
        assertEquals(PlayRangeUseCase.Range(60, 150, label = "入点到出点"), use.inToOut(TimelineMarks(60, 150), 900))
        assertEquals(900, use.inToOut(TimelineMarks(60, 1200), 900).to)
        assertEquals("先标记时间线入点和出点（I / O）", assertThrows(EditRejected::class.java) { use.inToOut(TimelineMarks(60, null), 900) }.message)
        assertEquals("入点到出点之间没有画面", assertThrows(EditRejected::class.java) { use.inToOut(TimelineMarks(950, 1000), 900) }.message)
    }

    @Test
    fun `a ranged playback stops at its end, on the frame it returns to`() {
        val around = use.around(300, 900, 30)
        assertEquals(Step.Continue, use.step(around, false, none, 900, 300, playing = true, wasPlaying = true))
        assertEquals(Step.Stop(300), use.step(around, false, none, 900, 361, playing = true, wasPlaying = true))
        val inOut = use.inToOut(TimelineMarks(60, 150), 900)
        assertEquals(Step.Stop(150), use.step(inOut, false, none, 900, 150, playing = true, wasPlaying = true))
        assertEquals("not playing: nothing to follow", Step.Continue, use.step(inOut, false, none, 900, 150, playing = false, wasPlaying = false))
    }

    @Test
    fun `loop starts again at In to Out when marked, else the timeline, and at the end of a ranged playback`() {
        assertEquals(Step.Continue, use.step(null, false, none, 900, 899, playing = false, wasPlaying = true))
        assertEquals("the timeline ran out", Step.Restart(0), use.step(null, true, none, 900, 899, playing = false, wasPlaying = true))
        assertEquals(Step.Restart(60), use.step(null, true, TimelineMarks(60, 150), 900, 151, playing = true, wasPlaying = true))
        assertEquals(Step.Continue, use.step(null, true, TimelineMarks(60, 150), 900, 100, playing = true, wasPlaying = true))
        assertEquals("one mark alone loops the timeline", Step.Continue, use.step(null, true, TimelineMarks(60, null), 900, 151, playing = true, wasPlaying = true))
        assertEquals(Step.Restart(240), use.step(use.around(300, 900, 30), true, none, 900, 360, playing = true, wasPlaying = true))
        assertNull(use.loopRange(none, 0))
    }
}
