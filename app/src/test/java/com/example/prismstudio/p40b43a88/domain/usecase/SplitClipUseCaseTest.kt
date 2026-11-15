package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SplitClipUseCaseTest {

    private val split = SplitClipUseCase(SequentialIds())

    private fun rejection(timeline: Timeline, atFrame: Int): String? =
        assertThrows(EditRejected::class.java) { split(timeline, atFrame, null, 30) }.message

    @Test
    fun `a split keeps the fade in's curve on the left piece and the fade out's on the right`() {
        val clip = clipOf("A", 0, 100).copy(fadeInSeconds = 0.5f, fadeOutSeconds = 0.5f, fadeInCurve = 0.6f, fadeOutCurve = -0.4f)
        val pieces = split(timelineOf(clip), atFrame = 40, selectedClipId = "A", fps = 30).timeline.clips
        assertEquals(listOf(0.6f to 0f, 0f to -0.4f), pieces.map { it.fadeInCurve to it.fadeOutCurve })
        assertEquals(listOf(0.5f to 0f, 0f to 0.5f), pieces.map { it.fadeInSeconds to it.fadeOutSeconds })
    }

    @Test
    fun `splitting the selected clip cuts it with its linked partner and selects the right half`() {
        val video = clipOf("A", 0, 100, sourceInMs = 1_000, linkId = "L")
            .copy(fadeInSeconds = 0.5f, fadeOutSeconds = 0.5f, transitionIn = Transition(TransitionType.DIP_TO_BLACK, 10))
        val sound = clipOf("Aa", 0, 100, track = Timeline.A1, sourceInMs = 1_000, linkId = "L")
            .copy(fadeInSeconds = 0.5f, fadeOutSeconds = 0.5f)
        val title = titleClipOf("T", 0, 200)

        val result = split(timelineOf(video, sound, title), atFrame = 40, selectedClipId = "A", fps = 30)

        fun rightOf(clip: Clip, id: String) = clip.copy(
            id = id, start = 40, durationFrames = 60, sourceInMs = 2_333, transitionIn = null, fadeInSeconds = 0f, linkId = "l-2",
        )
        assertEquals(
            listOf(
                video.copy(durationFrames = 40, fadeOutSeconds = 0f),
                rightOf(video, "c-1"),
                sound.copy(durationFrames = 40, fadeOutSeconds = 0f),
                rightOf(sound, "c-3"),
                title,
            ),
            result.timeline.clips,
        )
        assertEquals("c-1", result.selectedClipId)
        assertEquals("已在 00:00:01:10 分割 2 个片段", result.message)
    }

    @Test
    fun `without a selection under the playhead every unlocked clip there is cut`() {
        val timeline = timelineOf(
            clipOf("A", 0, 100),
            titleClipOf("T", 20, 40),
            clipOf("X", 0, 100, track = Timeline.A1),
            clipOf("Y", 60, 60, track = Timeline.A2),
            locked = setOf(Timeline.A1),
        )

        val result = split(timeline, atFrame = 50, selectedClipId = "Y", fps = 30)

        val spans = result.timeline.clips.map { "${it.id}@${it.start}+${it.durationFrames}" }
        assertEquals(listOf("A@0+50", "c-1@50+50", "T@20+30", "c-2@50+10", "X@0+100", "Y@60+60"), spans)
        assertEquals("c-1", result.selectedClipId)
        assertEquals("已在 00:00:01:20 分割 2 个片段", result.message)
    }

    @Test
    fun `the right half starts later in the source according to the playback speed`() {
        val timeline = timelineOf(clipOf("A", 0, 90, sourceInMs = 500, speed = PlaybackSpeed.DOUBLE))

        val result = split(timeline, atFrame = 30, selectedClipId = "A", fps = 30)

        assertEquals(2_500L, result.timeline.clip(result.selectedClipId)!!.sourceInMs)
        assertEquals(500L, result.timeline.clip("A")!!.sourceInMs)
    }

    @Test
    fun `nothing under the playhead or only locked clips there is rejected`() {
        val timeline = timelineOf(clipOf("A", 0, 100), clipOf("B", 100, 100))

        assertEquals("播放头下没有可分割的片段", rejection(timeline, 100))
        assertEquals("播放头下没有可分割的片段", rejection(timeline, 250))
        assertEquals("播放头下没有可分割的片段", rejection(timelineOf(clipOf("A", 0, 100), locked = setOf(Timeline.V1)), 50))
    }

    @Test
    fun `a reversed clip split plays every frame as before`() {
        val reversed = clipOf("R", 0, 90, sourceInMs = 1_000).copy(reverse = true)

        val (left, right) = split(timelineOf(reversed), atFrame = 30, selectedClipId = "R", fps = 30).timeline.clips

        // The left piece plays the end of the window (backwards), the right piece its beginning.
        assertEquals(3_000L, left.sourceInMs)
        assertEquals(1_000L, right.sourceInMs)
        for (frame in 0 until 90) {
            val piece = if (frame < 30) left else right
            assertEquals("frame $frame", reversed.sourceMsAt(frame, 30).toDouble(), piece.sourceMsAt(frame - piece.start, 30).toDouble(), 1.0)
        }
    }
}
