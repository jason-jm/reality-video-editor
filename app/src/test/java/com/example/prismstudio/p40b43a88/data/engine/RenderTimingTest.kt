package com.example.prismstudio.p40b43a88.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a render times frames: one per output frame slot, clips at their speed and nothing past their end. */
class RenderTimingTest {

    private fun kept(gate: FrameGate, timesUs: List<Long>): List<Long> = timesUs.filter(gate::keeps)

    private fun frames(fps: Double, count: Int, offsetUs: Long = 0): List<Long> = (0 until count).map { offsetUs + (it * 1_000_000.0 / fps).toLong() }

    /** Runs [cadence] over source frames decoded at [timesUs]: what goes out, as (time, index of the source frame shown). */
    private fun run(cadence: ClipCadence, timesUs: List<Long>): List<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        var shown = -1
        timesUs.forEachIndexed { index, t ->
            val step = cadence.place(t) ?: return@forEachIndexed
            step.fillsBefore.forEach { out += it.timeUs to shown }
            step.times.forEach { out += it to index }
            shown = index
        }
        cadence.finish().forEach { out += it.timeUs to shown }
        return out
    }

    /** The start of every output frame from [fromUs] until [untilUs] at [fps]: what a constant cadence fills. */
    private fun grid(fps: Int, fromUs: Long, untilUs: Long): List<Long> {
        val slots = FrameSlots(fps)
        return (slots.slotOf(fromUs) until slots.slotOf(untilUs)).map(slots::timeOf)
    }

    @Test
    fun `a source at the output rate keeps every frame, the last one included`() {
        val source = frames(30.0, 90)
        assertEquals("nothing is held back, so the last frame is out as soon as it arrives", source, kept(FrameGate(30), source))
        // Our sources open a millisecond early, so each frame sits 1 ms after its slot.
        val late = frames(30.0, 90, offsetUs = 1_000)
        assertEquals(late, kept(FrameGate(30), late))
    }

    @Test
    fun `a faster source keeps the first frame of each slot`() {
        val kept60 = kept(FrameGate(30), frames(60.0, 120))
        assertEquals("every other frame, on the 30 fps grid", frames(60.0, 120).filterIndexed { i, _ -> i % 2 == 0 }, kept60)
        assertEquals(50, kept(FrameGate(25), frames(50.0, 100)).size)
    }

    @Test
    fun `a slightly slower source loses nothing`() {
        val ntsc = frames(30_000 / 1001.0, 300)
        assertEquals(ntsc, kept(FrameGate(30), ntsc))
    }

    @Test
    fun `a flush starts a new grid`() {
        val gate = FrameGate(30)
        assertTrue(gate.keeps(100_000))
        assertFalse(gate.keeps(100_000))
        gate.reset()
        assertTrue(gate.keeps(100_000))
    }

    @Test
    fun `a clip keeps the frames before its end and drops what the decoder read past it`() {
        val clip = ClipTiming(startUs = 3_000_000, speed = 1f, untilUs = 6_000_000)
        assertEquals(3_001_000L, clip.place(3_001_000))
        assertEquals("its last frame", 5_967_667L, clip.place(5_967_667))
        assertNull("the first frame past the cut", clip.place(6_001_000))
        assertNull("a frame that rounds onto the end is past it", clip.place(5_999_900))
        assertNull(clip.place(6_400_000))
    }

    @Test
    fun `a sped-up or slowed clip is placed at its speed from its start`() {
        // 2×: two seconds of source fill one second of the render.
        val fast = ClipTiming(startUs = 6_000_000, speed = 2f, untilUs = 8_000_000)
        assertEquals(6_000_000L, fast.place(6_000_000))
        assertEquals(6_500_000L, fast.place(7_000_000))
        assertEquals(7_983_333L, fast.place(9_966_667))
        assertNull("source past the clip's four seconds", fast.place(10_000_000))
        // 0.5×: one second of source fills two.
        val slow = ClipTiming(startUs = 1_000_000, speed = 0.5f, untilUs = 3_000_000)
        assertEquals(2_000_000L, slow.place(1_500_000))
        assertNull(slow.place(2_000_000))
        // At 2× four seconds of a 30 fps source come out as 60 frames a second; the rate gate keeps every other one.
        val placed = frames(30.0, 120, offsetUs = 6_000_000).mapNotNull(fast::place)
        assertEquals(120, placed.size)
        assertEquals(60, kept(FrameGate(30), placed).size)
    }

    @Test
    fun `the frame grid puts every slot's start back in its slot`() {
        val slots = FrameSlots(30)
        assertEquals(0L, slots.timeOf(0))
        assertEquals(33_333L, slots.timeOf(1))
        assertEquals(1_000_000L, slots.timeOf(30))
        assertEquals("a quarter of a frame early still takes the slot", 1L, slots.slotOf(33_333 - 8_000))
        assertEquals(0L, slots.slotOf(33_333 - 9_000))
        for (fps in listOf(24, 25, 30, 60)) {
            val grid = FrameSlots(fps)
            for (slot in 0L..600L) assertEquals("$fps fps slot $slot", slot, grid.slotOf(grid.timeOf(slot)))
            // Clips start where CompositionFactory.framesToUs puts them: on the grid of their own rate.
            for (frame in 0..600) assertEquals(CompositionFactory.framesToUs(frame, fps), grid.timeOf(frame.toLong()))
        }
    }

    @Test
    fun `a source at the output rate shows each frame once, at its slot's start`() {
        val clip = ClipTiming(startUs = 3_000_000, speed = 1f, untilUs = 6_000_000)
        // Our sources open a millisecond early, so each decoded frame sits 1 ms after its slot; the decoder reads past the cut.
        val out = run(ClipCadence(clip, 30), frames(30.0, 100, offsetUs = 3_001_000))
        assertEquals(grid(30, 3_000_000, 6_000_000), out.map { it.first })
        assertEquals((0 until 90).toList(), out.map { it.second })
    }

    @Test
    fun `a slowed clip shows each frame again in the slots between, to the clip's end`() {
        // ½×: one second of a 30 fps source fills two seconds of the render.
        val slow = ClipTiming(startUs = 1_000_000, speed = 0.5f, untilUs = 3_000_000)
        val out = run(ClipCadence(slow, 30), frames(30.0, 40, offsetUs = 1_000_000))
        assertEquals("60 frames, one per slot — not 30 frames of 66.7 ms", grid(30, 1_000_000, 3_000_000), out.map { it.first })
        assertEquals((0 until 30).flatMap { listOf(it, it) }, out.map { it.second })
        // ¼×: four slots per source frame.
        val quarter = run(ClipCadence(ClipTiming(0, 0.25f, 1_000_000), 30), frames(30.0, 20))
        assertEquals(30, quarter.size)
        assertEquals((0 until 30).map { it / 4 }, quarter.map { it.second })
    }

    @Test
    fun `a source slower than the output repeats a frame where a slot would be empty`() {
        // 25 fps on a 30 fps output: every sixth slot shows the frame before again.
        val pal = run(ClipCadence(ClipTiming(0, 1f, 2_000_000), 30), frames(25.0, 60))
        assertEquals(grid(30, 0, 2_000_000), pal.map { it.first })
        assertEquals((0 until 50).toList(), pal.map { it.second }.distinct())
        val repeated = pal.indices.filter { it > 0 && pal[it].second == pal[it - 1].second }
        assertEquals((0 until 10).map { 4 + 6 * it }, repeated)
        // 24 fps on 30: 48 frames fill 60 slots.
        val film = run(ClipCadence(ClipTiming(0, 1f, 2_000_000), 30), frames(24.0, 60))
        assertEquals(60, film.size)
        assertEquals((0 until 48).toList(), film.map { it.second }.distinct())
    }

    @Test
    fun `a sped-up clip shows the first frame of each slot`() {
        // 2×: four seconds of source in two; frames placed every 16.7 ms.
        val fast = ClipTiming(startUs = 6_000_000, speed = 2f, untilUs = 8_000_000)
        val out = run(ClipCadence(fast, 30), frames(30.0, 130, offsetUs = 6_000_000))
        assertEquals(grid(30, 6_000_000, 8_000_000), out.map { it.first })
        assertEquals((0 until 120 step 2).toList(), out.map { it.second })
    }

    @Test
    fun `a late first frame also fills the clip's first slots`() {
        val clip = ClipTiming(startUs = 1_000_000, speed = 1f, untilUs = 2_000_000)
        val out = run(ClipCadence(clip, 30), frames(30.0, 40, offsetUs = 1_050_000))
        assertEquals(grid(30, 1_000_000, 2_000_000), out.map { it.first })
        assertEquals("slot 30 has nothing earlier to show", listOf(0, 0, 1, 2), out.take(4).map { it.second })
        assertEquals(28, out.last().second)
        // A frame a little more than a quarter of a frame early is still the clip's first frame, at the clip's start.
        val early = run(ClipCadence(clip, 30), listOf(990_000L) + frames(30.0, 29, offsetUs = 1_033_333))
        assertEquals(1_000_000L to 0, early.first())
        assertEquals(30, early.size)
    }

    @Test
    fun `a window opened early places the clip's first moment at its start`() {
        // The window opens 51 ms of source before the clip's first moment: a frame decoded there lands on the start.
        val clip = ClipTiming(startUs = 1_000_000, speed = 0.25f, untilUs = 2_000_000, leadUs = 51_000)
        assertEquals(1_000_000L, clip.place(1_051_000))
        assertEquals("a frame 10 ms of source before it, 40 ms before the start", 960_000L, clip.place(1_041_000))
        assertEquals(1_400_000L, clip.place(1_151_000))
        assertEquals(1_051_000L, ClipTiming(1_000_000, 1f, 2_000_000, leadUs = 51_000).place(1_102_000))
    }

    @Test
    fun `the frame on show as a clip starts fills its first slots, the latest of the early ones`() {
        // A step of a speed ramp: 3 slots at 0.3×, its first moment 3 ms into source frame A, the window opened 51 ms
        // early, so frames Z and A arrive before the start. B comes only past the clip's end: A fills all three slots.
        val step = ClipTiming(startUs = 0, speed = 0.3f, untilUs = 100_000, leadUs = 51_000)
        val z = 51_000L - 36_333
        val a = 51_000L - 3_000
        val b = 51_000L + 30_333
        assertEquals("Z, then A, held; B is past the clip", List(3) { 1 }, run(ClipCadence(step, 30), listOf(z, a, b)).map { it.second })
        assertEquals(grid(30, 0, 100_000), run(ClipCadence(step, 30), listOf(z, a, b)).map { it.first })
        // Faster, A is less than a quarter frame early, so it takes the first slot itself; B comes in the second.
        val quicker = ClipTiming(startUs = 0, speed = 0.6f, untilUs = 100_000, leadUs = 51_000)
        assertEquals(listOf(1, 2, 2), run(ClipCadence(quicker, 30), listOf(z, a, b)).map { it.second })
        // A frame of the clip's own in the first slot shows there; the early one is not shown at all.
        val onTime = ClipTiming(startUs = 0, speed = 1f, untilUs = 100_000, leadUs = 51_000)
        assertEquals(listOf(1, 2, 3), run(ClipCadence(onTime, 30), listOf(z, 51_000L + 1_000, 51_000L + 34_333, 51_000L + 67_666)).map { it.second })
        // With frame blend the slots before B mix the held A (placed at −10 ms) and B (101.1 ms) by where each falls.
        val blended = ClipCadence(ClipTiming(startUs = 0, speed = 0.3f, untilUs = 200_000, leadUs = 51_000), 30, blend = true)
        assertNotNull(blended.place(z))
        assertEquals("A is held", emptyList<Long>(), blended.place(a)!!.times)
        val toB = blended.place(b)!!
        assertEquals(listOf(0L, 33_333L, 66_666L), toB.fillsBefore.map { it.timeUs })
        listOf(0.090f, 0.390f, 0.690f).zip(toB.fillsBefore).forEach { (want, fill) -> assertEquals(want, fill.toward, 0.001f) }
        assertEquals(listOf(100_000L), toB.times)
    }

    @Test
    fun `a source that runs short or holds one frame fills the clip with its last frame`() {
        val clip = ClipTiming(startUs = 0, speed = 1f, untilUs = 1_000_000)
        val short = run(ClipCadence(clip, 30), frames(30.0, 15))
        assertEquals(grid(30, 0, 1_000_000), short.map { it.first })
        assertEquals((0 until 15).toList() + List(15) { 14 }, short.map { it.second })
        assertEquals("a single decoded frame plays for the whole clip", List(30) { 0 }, run(ClipCadence(clip, 30), listOf(0L)).map { it.second })
        assertEquals("no frame, nothing to repeat", emptyList<Pair<Long, Int>>(), run(ClipCadence(clip, 30), emptyList()))
    }

    @Test
    fun `the cadence follows the output rate, not the timeline's`() {
        // A 30 fps timeline delivered at 60: every frame twice.
        val clip = ClipTiming(startUs = 0, speed = 1f, untilUs = 1_000_000)
        val sixty = run(ClipCadence(clip, 60), frames(30.0, 35, offsetUs = 1_000))
        assertEquals(grid(60, 0, 1_000_000), sixty.map { it.first })
        assertEquals((0 until 30).flatMap { listOf(it, it) }, sixty.map { it.second })
        // Delivered at 24: 24 frames, the first of each slot.
        val film = run(ClipCadence(clip, 24), frames(30.0, 35, offsetUs = 1_000))
        assertEquals(grid(24, 0, 1_000_000), film.map { it.first })
        assertEquals(listOf(0, 1, 3, 4, 5, 6, 8), film.take(7).map { it.second })
    }

    @Test
    fun `consecutive clips share out the output frames exactly`() {
        for (timeline in listOf(24, 25, 30, 60)) for (output in listOf(24, 25, 30, 60)) {
            val cuts = listOf(0, 1, 13, 14, 29, 47, 48, 61, 90, 133, 134, 200).map { CompositionFactory.framesToUs(it, timeline) }
            val shown = cuts.zipWithNext().flatMap { (from, until) ->
                run(ClipCadence(ClipTiming(from, 1f, until), output), frames(timeline.toDouble(), 300).filter { it >= from }).map { it.first }
            }
            assertEquals("$timeline fps timeline at $output fps", grid(output, 0, cuts.last()), shown)
        }
    }

    @Test
    fun `a flushed cadence starts the clip again`() {
        val cadence = ClipCadence(ClipTiming(0, 1f, 1_000_000), 30)
        assertEquals(ClipCadence.Step(emptyList(), listOf(0L)), cadence.place(1_000))
        assertNull("one frame per slot", cadence.place(9_000))
        assertEquals(ClipCadence.Step(listOf(ClipCadence.Fill(33_333L)), listOf(66_666L)), cadence.place(67_000))
        cadence.reset()
        assertEquals(ClipCadence.Step(emptyList(), listOf(0L, 33_333L)), cadence.place(34_000))
    }
    /** The blends a cadence makes: (time, blended part of the next frame) for every fill that mixes two frames. */
    private fun blends(cadence: ClipCadence, timesUs: List<Long>): List<Pair<Long, Float>> =
        timesUs.mapNotNull(cadence::place).flatMap { step -> step.fillsBefore.filter { it.toward > 0f }.map { it.timeUs to it.toward } }

    @Test
    fun `frame blend mixes the frames either side of a missing one by where it falls between them`() {
        // ½× from the clip's start: each in-between slot is half way.
        val half = ClipCadence(ClipTiming(0, 0.5f, 2_000_000), 30, blend = true)
        val halves = blends(half, frames(30.0, 40))
        assertEquals(29, halves.size)
        halves.forEach { (_, toward) -> assertEquals(0.5f, toward, 0.001f) }
        // ¼×: three in-between slots at a quarter, a half and three quarters.
        val quarter = ClipCadence(ClipTiming(0, 0.25f, 1_000_000), 30, blend = true)
        assertEquals(listOf(0.25f, 0.5f, 0.75f), blends(quarter, frames(30.0, 20)).take(3).map { (_, w) -> Math.round(w * 100) / 100f })
        // 25 fps on 30: one slot in six falls a third of the way from frame 3 (120 ms) to frame 4 (160 ms).
        val pal = blends(ClipCadence(ClipTiming(0, 1f, 2_000_000), 30, blend = true), frames(25.0, 60))
        assertEquals(133_333L, pal.first().first)
        assertEquals(1 / 3f, pal.first().second, 0.001f)
        assertEquals("every sixth slot", 10, pal.size)
    }

    @Test
    fun `frame blend leaves the clip's first and last slots and a matching source alone`() {
        val clip = ClipTiming(startUs = 1_000_000, speed = 1f, untilUs = 2_000_000)
        // A late first frame has nothing before it to blend with, a short source nothing after its last frame.
        val cadence = ClipCadence(clip, 30, blend = true)
        val first = cadence.place(1_050_000)!!
        assertEquals("plain repeats before the first frame", listOf(1_000_000L, 1_033_333L), first.times)
        assertEquals(List(28) { 0f }, cadence.finish().map { it.toward })
        // 30 fps footage at 1× has no missing frame: nothing blends.
        assertEquals(emptyList<Pair<Long, Float>>(), blends(ClipCadence(clip, 30, blend = true), frames(30.0, 40, offsetUs = 1_001_000)))
        // Nearest repeats instead of blending.
        assertEquals(emptyList<Pair<Long, Float>>(), blends(ClipCadence(ClipTiming(0, 0.5f, 2_000_000), 30), frames(30.0, 40)))
    }

    @Test
    fun `the preview keeps frames at their own times and only fills between two frames`() {
        // Media3 has already placed a ½× clip's frames 66.7 ms apart (speed 1 here); the preview blends the slot between.
        val preview = ClipCadence(ClipTiming(4_000_000, 1f, 7_000_000), 30, blend = true, render = false)
        val first = preview.place(4_002_000)!!
        assertEquals(ClipCadence.Step(emptyList(), listOf(4_002_000L)), first)
        val second = preview.place(4_068_667)!!
        assertEquals(listOf(4_002_000L + 66_667L), second.times)
        assertEquals(listOf(4_033_333L), second.fillsBefore.map { it.timeUs })
        assertEquals(0.47f, second.fillsBefore.single().toward, 0.01f)
        // A frame in the slot of the one before is kept (no dropping), and nothing is added at the clip's end.
        assertEquals(ClipCadence.Step(emptyList(), listOf(4_080_000L)), preview.place(4_080_000))
        assertEquals(emptyList<ClipCadence.Fill>(), preview.finish())
        // After a seek (a flush) the first frame fills nothing before it.
        preview.reset()
        assertEquals(ClipCadence.Step(emptyList(), listOf(5_500_000L)), preview.place(5_500_000))
    }
}
