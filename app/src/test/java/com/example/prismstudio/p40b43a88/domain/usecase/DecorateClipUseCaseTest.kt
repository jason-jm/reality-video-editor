package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.SequentialIds
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DecorateClipUseCaseTest {

    private val decorate = DecorateClipUseCase(SequentialIds())
    private val timeline = timelineOf(
        clipOf("A", 0, 100),
        clipOf("Short", 100, 10),
        clipOf("Blip", 110, 1),
        clipOf("Sound", 0, 100, track = Timeline.A1),
        titleClipOf("T", 0, 90),
    )

    private fun rejection(block: () -> Unit): String? = assertThrows(EditRejected::class.java) { block() }.message

    @Test
    fun `a transition into the clip lasts half a second but at most half the clip, centred on the cut`() {
        val result = decorate.applyTransition(timeline, "A", TransitionType.CROSS_DISSOLVE, fps = 30)
        assertEquals(Transition(TransitionType.CROSS_DISSOLVE, 15, alignment = TransitionAlignment.CENTER), result.timeline.clip("A")!!.transitionIn)
        assertEquals("A", result.selectedClipId)
        assertEquals("已为“A”应用转场：交叉溶解", result.message)

        assertEquals(5, decorate.applyTransition(timeline, "Short", TransitionType.PUSH, 30).timeline.clip("Short")!!.transitionIn!!.frames)
        assertEquals(1, decorate.applyTransition(timeline, "Blip", TransitionType.WIPE, 30).timeline.clip("Blip")!!.transitionIn!!.frames)
        assertEquals(
            Transition(TransitionType.DIP_TO_BLACK, 12, alignment = TransitionAlignment.CENTER),
            decorate.applyTransition(timeline, "T", TransitionType.DIP_TO_BLACK, 24).timeline.clip("T")!!.transitionIn,
        )
    }

    private val styled = Transition(TransitionType.WIPE, 20, TransitionEase.IN_OUT, reverse = true, softness = 30, border = 40, borderColor = TitleColor.AMBER)
    private val dressed = timeline.replaceClip(timeline.clip("A")!!.copy(transitionIn = styled))

    @Test
    fun `the inspector changes the kind in place, keeping the length and style`() {
        val result = decorate.changeTransitionType(dressed, "A", TransitionType.IRIS)
        assertEquals(styled.copy(type = TransitionType.IRIS), result.timeline.clip("A")!!.transitionIn)
        assertEquals("转场改为：圆形划像", result.message)
        assertEquals("A", result.selectedClipId)

        assertEquals("转场已经是划像", rejection { decorate.changeTransitionType(dressed, "A", TransitionType.WIPE) })
        assertEquals("所选片段没有转场", rejection { decorate.changeTransitionType(timeline, "A", TransitionType.IRIS) })
        assertEquals("请先在时间线上选择片段", rejection { decorate.changeTransitionType(dressed, null, TransitionType.IRIS) })
        val locked = dressed.copy(tracks = dressed.tracks.map { it.copy(locked = it.id == Timeline.V1) })
        assertEquals("轨道已锁定", rejection { decorate.changeTransitionType(locked, "A", TransitionType.IRIS) })
    }

    @Test
    fun `a library transition dropped on one the clip has replaces its kind only`() {
        val result = decorate.applyTransition(dressed, "A", TransitionType.CLOCK_WIPE, fps = 30)
        assertEquals("length 20 and the style stay", styled.copy(type = TransitionType.CLOCK_WIPE), result.timeline.clip("A")!!.transitionIn)
        assertEquals("已将“A”的转场换成：时钟划像", result.message)
    }

    @Test
    fun `restyling keeps kind and length and holds softness and border to 0-100`() {
        val plain = decorate.applyTransition(timeline, "A", TransitionType.BARN_DOOR, fps = 30).timeline
        val eased = decorate.styleTransition(plain, "A") { it.copy(ease = TransitionEase.OUT, reverse = true) }
        assertEquals(
            Transition(TransitionType.BARN_DOOR, 15, TransitionEase.OUT, reverse = true, alignment = TransitionAlignment.CENTER),
            eased.clip("A")!!.transitionIn,
        )

        val edged = decorate.styleTransition(eased, "A") { it.copy(softness = 140, border = -5, borderColor = TitleColor.CYAN) }
        assertEquals(100, edged.clip("A")!!.transitionIn!!.softness)
        assertEquals(0, edged.clip("A")!!.transitionIn!!.border)
        assertEquals(TitleColor.CYAN, edged.clip("A")!!.transitionIn!!.borderColor)

        val sneaky = decorate.styleTransition(edged, "A") { it.copy(type = TransitionType.PUSH, frames = 60, softness = 10) }
        assertEquals("only the style changes here", TransitionType.BARN_DOOR to 15, sneaky.clip("A")!!.transitionIn!!.let { it.type to it.frames })
        assertEquals(10, sneaky.clip("A")!!.transitionIn!!.softness)

        assertEquals("所选片段没有转场", rejection { decorate.styleTransition(timeline, "A") { it.copy(reverse = true) } })
    }

    @Test
    fun `effects are appended with fresh ids and default settings and removed by id`() {
        val first = decorate.applyEffect(timeline, "A", EffectType.VIGNETTE)
        val second = decorate.applyEffect(first.timeline, "A", EffectType.GLOW)
        assertEquals(
            listOf(ClipEffect("fx-1", EffectType.VIGNETTE, 50, true), ClipEffect("fx-2", EffectType.GLOW, 50, true)),
            second.timeline.clip("A")!!.effects,
        )
        assertEquals("已应用特效：发光", second.message)
        assertEquals("A", second.selectedClipId)

        val removed = decorate.removeEffect(second.timeline, "A", "fx-1")
        assertEquals(listOf(ClipEffect("fx-2", EffectType.GLOW)), removed.timeline.clip("A")!!.effects)
        assertEquals("已移除特效：暗角", removed.message)
    }

    @Test
    fun `audio clips missing selections and unknown effects are rejected`() {
        assertEquals("转场只能应用到画面片段", rejection { decorate.applyTransition(timeline, "Sound", TransitionType.WIPE, 30) })
        assertEquals("特效只能应用到画面片段", rejection { decorate.applyEffect(timeline, "Sound", EffectType.MOSAIC) })
        assertEquals("请先在时间线上选择片段", rejection { decorate.applyEffect(timeline, null, EffectType.MOSAIC) })
        assertEquals("请先在时间线上选择片段", rejection { decorate.applyTransition(timeline, "ghost", TransitionType.WIPE, 30) })
        assertEquals("特效不存在", rejection { decorate.removeEffect(timeline, "A", "fx-404") })
    }
}
