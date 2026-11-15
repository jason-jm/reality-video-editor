package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.eqSummary
import com.example.prismstudio.p40b43a88.domain.model.TrackEq
import com.example.prismstudio.p40b43a88.domain.model.EqPreset
import com.example.prismstudio.p40b43a88.domain.model.ClipScaling
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.domain.model.RetimeProcess
import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.ClipEffect
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.CropControl
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.transformAt
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EffectType
import com.example.prismstudio.p40b43a88.domain.model.PlaybackSpeed
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.VolumeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.TitleStyle
import com.example.prismstudio.p40b43a88.domain.model.TitleTemplate
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ClipPropertiesUseCaseTest {

    private val properties = ClipPropertiesUseCase()

    /** V1: A (linked) then B; A1: A's sound then C; V2: a title above. */
    private val timeline = timelineOf(
        clipOf("A", 0, 100, linkId = "L"),
        clipOf("B", 100, 60),
        clipOf("Aa", 0, 100, track = Timeline.A1, linkId = "L"),
        clipOf("C", 150, 50, track = Timeline.A1),
        titleClipOf("T", 120, 60),
    )

    private fun rejection(block: () -> Unit): String? = assertThrows(EditRejected::class.java) { block() }.message

    @Test
    fun `a sound clip's pitch goes up or down to an octave, and a picture has none to shift`() {
        assertEquals(700, properties.pitch(timeline, "Aa", 700).clip("Aa")!!.pitchCents)
        assertEquals(1200, properties.pitch(timeline, "C", 5_000).clip("C")!!.pitchCents)
        assertEquals(-1200, properties.pitch(timeline, "C", -5_000).clip("C")!!.pitchCents)
        assertEquals("只有声音片段可以变调", rejection { properties.pitch(timeline, "B", 100) })
        assertEquals("升高7 个半音（纯五度） · 频率 ×1.498 · 长度和速度不变", properties.pitchNote(700))
        assertEquals("降低12 个半音（八度） · 频率 ×0.500 · 长度和速度不变", properties.pitchNote(-1200))
        assertEquals("升高0.5 个半音 · 频率 ×1.029 · 长度和速度不变", properties.pitchNote(50))
        assertEquals("原调：拖动以升高或降低音调，长度和速度不变", properties.pitchNote(0))
    }

    @Test
    fun `transform values are clamped to the inspector ranges`() {
        val wild = properties.transform(timeline, "A") {
            ClipTransform(scalePercent = 1000, positionX = -5000, positionY = 900, rotationDegrees = 270, opacityPercent = -10)
        }
        assertEquals(ClipTransform(400, -960, 540, 180, 0), wild.clip("A")!!.transform)

        val tame = properties.transform(timeline, "T") { it.copy(scalePercent = 150, positionX = 20) }
        assertEquals(ClipTransform(150, 20, 0, 0, 100), tame.clip("T")!!.transform)
    }

    @Test
    fun `a crop is clamped to the frame, and a keyed clip crops at the playhead's keyframe`() {
        val wild = properties.transform(timeline, "A") { it.copy(cropLeft = 2400, cropRight = 300, cropTop = -20, cropBottom = 1500, cropSoftness = 150) }
        val t = wild.clip("A")!!.transform
        assertEquals(listOf(1920, 300, 0, 1080, 100), CropControl.entries.map { it.read(t) })
        assertEquals("the rest of the transform stays", ClipTransform(cropLeft = 1920, cropRight = 300, cropBottom = 1080, cropSoftness = 100), t)
        // Animated: the crop at frame 30 is keyed there, between keyframes the crop moves with the rest.
        val keys = listOf(TransformKeyframe(0, ClipTransform()), TransformKeyframe(60, ClipTransform(cropLeft = 600, cropSoftness = 40)))
        val keyed = timeline.replaceClip(timeline.clip("A")!!.copy(keyframes = keys))
        assertEquals(300 to 20, keyed.clip("A")!!.transformAt(30).let { it.cropLeft to it.cropSoftness })
        val cut = properties.transform(keyed, "A", 30) { CropControl.TOP.write(it, 200) }
        assertEquals(listOf(0, 30, 60), cut.clip("A")!!.keyframes.map { it.frame })
        assertEquals(ClipTransform(cropLeft = 300, cropTop = 200, cropSoftness = 20), cut.clip("A")!!.keyframes[1].transform)
    }

    @Test
    fun `a cross fade goes on sound clips, up to two seconds and never longer than the clip`() {
        assertEquals(12, properties.crossFade(timeline, "C", 12, 30).clip("C")!!.crossFadeFrames)
        assertEquals("C is 50 frames long", 50, properties.crossFade(timeline, "C", 500, 30).clip("C")!!.crossFadeFrames)
        assertEquals(60, properties.maxCrossFade(timeline.clip("Aa")!!.copy(durationFrames = 300), 30))
        assertEquals(0, properties.crossFade(timeline, "C", -3, 30).clip("C")!!.crossFadeFrames)
        assertEquals("只有声音片段可以交叉淡化", rejection { properties.crossFade(timeline, "A", 10, 30) })
    }

    @Test
    fun `dynamic zoom goes on pictures within 5 to 50 percent and comes off again`() {
        val on = properties.dynamicZoom(timeline, "A", DynamicZoom(ZoomDirection.OUT, 80, ZoomEase.EASE_IN_OUT))
        assertEquals(DynamicZoom(ZoomDirection.OUT, 50, ZoomEase.EASE_IN_OUT), on.clip("A")!!.dynamicZoom)
        assertEquals("the rest of the clip stays", timeline.clip("A")!!.transform, on.clip("A")!!.transform)
        assertEquals(5, properties.dynamicZoom(timeline, "A", DynamicZoom(amountPercent = 1)).clip("A")!!.dynamicZoom!!.amountPercent)
        assertNull(properties.dynamicZoom(on, "A", null).clip("A")!!.dynamicZoom)
        assertEquals("只有画面片段可以设置动态缩放", rejection { properties.dynamicZoom(timeline, "Aa", DynamicZoom()) })
        assertEquals("只有画面片段可以设置动态缩放", rejection { properties.dynamicZoom(timeline, "T", DynamicZoom()) })
    }

    @Test
    fun `a composite mode goes on pictures only and leaves the rest of the clip alone`() {
        val screened = properties.composite(timeline, "A", CompositeMode.SCREEN)
        assertEquals(CompositeMode.SCREEN, screened.clip("A")!!.composite)
        assertEquals(timeline.clip("A")!!.copy(composite = CompositeMode.SCREEN), screened.clip("A"))
        assertEquals(CompositeMode.NORMAL, properties.composite(screened, "A", CompositeMode.NORMAL).clip("A")!!.composite)
        assertEquals("只有画面片段可以设置合成模式", rejection { properties.composite(timeline, "Aa", CompositeMode.MULTIPLY) })
        assertEquals("只有画面片段可以设置合成模式", rejection { properties.composite(timeline, "T", CompositeMode.MULTIPLY) })
    }

    @Test
    fun `a scaling goes on pictures only and leaves the rest of the clip alone`() {
        val filled = properties.scaling(timeline, "A", ClipScaling.FILL)
        assertEquals(timeline.clip("A")!!.copy(scaling = ClipScaling.FILL), filled.clip("A"))
        assertEquals("只有画面片段可以设置缩放方式", rejection { properties.scaling(timeline, "Aa", ClipScaling.STRETCH) })
        assertEquals("只有画面片段可以设置缩放方式", rejection { properties.scaling(timeline, "T", ClipScaling.STRETCH) })
    }

    @Test
    fun `a retime process goes on pictures only and leaves the rest of the clip alone`() {
        val blended = properties.retime(timeline, "A", RetimeProcess.FRAME_BLEND)
        assertEquals(timeline.clip("A")!!.copy(retime = RetimeProcess.FRAME_BLEND), blended.clip("A"))
        assertEquals(RetimeProcess.NEAREST, properties.retime(blended, "A", RetimeProcess.NEAREST).clip("A")!!.retime)
        assertEquals("只有画面片段可以设置变速处理", rejection { properties.retime(timeline, "Aa", RetimeProcess.FRAME_BLEND) })
        assertEquals("只有画面片段可以设置变速处理", rejection { properties.retime(timeline, "T", RetimeProcess.FRAME_BLEND) })
    }

    @Test
    fun `edits on missing clips locked tracks or unsuitable clips are rejected`() {
        assertEquals("请先在时间线上选择片段", rejection { properties.volume(timeline, "ghost", -3f) })
        val locked = timelineOf(*timeline.clips.toTypedArray(), locked = setOf(Timeline.A1))
        assertEquals("轨道已锁定", rejection { properties.volume(locked, "Aa", -3f) })
        assertEquals("轨道已锁定", rejection { properties.fades(locked, "Aa", 30, fadeIn = 1f) })
        assertEquals("音频片段没有画面属性", rejection { properties.transform(timeline, "Aa") { it } })
        assertEquals("所选片段不是标题", rejection { properties.title(timeline, "A", text = "x") })
        assertEquals("标题片段不能变速", rejection { properties.speed(timeline, "T", ClipSpeed.of(PlaybackSpeed.DOUBLE)) })
        assertEquals("标题片段不能倒放", rejection { properties.reverse(timeline, "T", true) })
    }

    @Test
    fun `title edits clamp the size and rename the clip after its text`() {
        val edited = properties.title(timeline, "T", text = "Hi", sizePx = 500).clip("T")!!
        assertEquals(TitleStyle("Hi", 160, TitleTemplate.CENTER), edited.title)
        assertEquals("标题：Hi", edited.name)

        val resized = properties.title(timeline, "T", sizePx = 10).clip("T")!!
        assertEquals(TitleStyle("Title", 24, TitleTemplate.CENTER), resized.title)
        assertEquals("标题：Title", resized.name)

        assertEquals("标题：（空）", properties.title(timeline, "T", text = "  ").clip("T")!!.name)
    }

    @Test
    fun `dragging the volume line moves the level or the keyframes it holds, on the line's scale`() {
        assertEquals(6f, properties.volumeLine(timeline, "Aa", emptyList(), 6f).clip("Aa")!!.volumeDb, 0f)
        assertEquals("the top of the line", 12f, properties.volumeLine(timeline, "Aa", emptyList(), 30f).clip("Aa")!!.volumeDb, 0f)
        assertEquals("tenths of a dB", -3.3f, properties.volumeLine(timeline, "Aa", emptyList(), -3.33f).clip("Aa")!!.volumeDb, 0f)
        // A level below the line is drawn at its bottom, so a drag starts from there.
        val quiet = properties.volume(timeline, "Aa", -45f)
        assertEquals(-25f, properties.volumeLine(quiet, "Aa", emptyList(), 5f).clip("Aa")!!.volumeDb, 0f)
        val keyed = timeline.replaceClip(
            timeline.clip("Aa")!!.copy(volumeKeyframes = listOf(VolumeKeyframe(0, -6f), VolumeKeyframe(30, 0f), VolumeKeyframe(60, -12f))),
        )
        val stretch = properties.volumeLine(keyed, "Aa", listOf(30, 60), 3f).clip("Aa")!!
        assertEquals("the two keyframes either side of the stretch", listOf(-6f, 3f, -9f), stretch.volumeKeyframes.map { it.volumeDb })
        assertEquals("a keyed clip has no one level to drag", keyed, properties.volumeLine(keyed, "Aa", emptyList(), 3f))
        assertEquals("只有音频片段有音量线", rejection { properties.volumeLine(timeline, "A", emptyList(), 3f) })
        val locked = timelineOf(*timeline.clips.toTypedArray(), locked = setOf(Timeline.A1))
        assertEquals("轨道已锁定", rejection { properties.volumeLine(locked, "Aa", emptyList(), 3f) })
    }

    @Test
    fun `volume is clamped and fades never exceed half the clip or five seconds`() {
        assertEquals(12f, properties.volume(timeline, "Aa", 20f).clip("Aa")!!.volumeDb, 0f)
        assertEquals(-60f, properties.volume(timeline, "Aa", -100f).clip("Aa")!!.volumeDb, 0f)
        assertEquals(-3.5f, properties.volume(timeline, "Aa", -3.5f).clip("Aa")!!.volumeDb, 0f)

        // 100 frames at 30 fps: at most 50 frames = 1.67 s per fade.
        val faded = properties.fades(properties.fades(timeline, "Aa", 30, fadeOut = 0.5f), "Aa", 30, fadeIn = 3f).clip("Aa")!!
        assertEquals(100f / 2f / 30f, faded.fadeInSeconds, 1e-6f)
        assertEquals(0.5f, faded.fadeOutSeconds, 0f)
        assertEquals(0f, properties.fades(timeline, "Aa", 30, fadeOut = -1f).clip("Aa")!!.fadeOutSeconds, 0f)

        val long = clipOf("Long", 0, 900, track = Timeline.A2)
        assertEquals(5f, properties.maxFade(long, 30), 0f)
        assertEquals(5f, properties.fades(timelineOf(long), "Long", 30, fadeIn = 8f).clip("Long")!!.fadeInSeconds, 0f)
    }

    @Test
    fun `a picture and a title fade by their own handles, apart from their sound, and subtitles have none`() {
        val faded = properties.fades(properties.fades(timeline, "A", 30, fadeIn = 1f), "A", 30, fadeOut = 9f)
        assertEquals(1f, faded.clip("A")!!.fadeInSeconds, 0f)
        assertEquals("half of the 100 frames", 100f / 2f / 30f, faded.clip("A")!!.fadeOutSeconds, 1e-6f)
        assertEquals("the linked sound keeps its own", 0f, faded.clip("Aa")!!.fadeInSeconds, 0f)
        assertEquals(0.5f, properties.fades(timeline, "T", 30, fadeIn = 0.5f).clip("T")!!.fadeInSeconds, 0f)
        val subtitle = titleClipOf("S", 0, 60).let { it.copy(title = it.title!!.copy(template = TitleTemplate.SUBTITLE)) }
        assertEquals("字幕随对白出现和消失，没有淡入淡出", rejection { properties.fades(timelineOf(subtitle), "S", 30, fadeIn = 1f) })
    }

    @Test
    fun `a sound clip has its own equaliser, a picture none, and a locked track keeps it`() {
        val voice = EqPreset.VOICE.eq
        assertEquals(voice, properties.eq(timeline, "Aa") { voice }.clip("Aa")!!.eq)
        assertEquals("只有声音片段有均衡器", rejection { properties.eq(timeline, "A") { voice } })
        val locked = timeline.copy(tracks = timeline.tracks.map { if (it.id == Timeline.A1) it.copy(locked = true) else it })
        assertEquals("轨道已锁定", rejection { properties.eq(locked, "Aa") { voice } })
        assertEquals("预设：人声清晰", eqSummary(voice))
        assertEquals("平直", eqSummary(TrackEq.FLAT))
        assertEquals("已旁通", eqSummary(voice.copy(enabled = false)))
        assertEquals("低切 100 Hz · 中频 +3 dB @ 2500 Hz", eqSummary(TrackEq(lowCut = true, lowCutHz = 100f, midHz = 2500f, midDb = 3f)))
        assertEquals("高频 −2.5 dB".replace("−", "-"), eqSummary(TrackEq(highDb = -2.5f)))
    }

    @Test
    fun `volume and fades target the clip itself when it is audio and otherwise its linked audio`() {
        assertEquals("Aa", properties.audioTarget(timeline, "Aa")?.id)
        assertEquals("Aa", properties.audioTarget(timeline, "A")?.id)
        assertNull(properties.audioTarget(timeline, "B"))
        assertNull(properties.audioTarget(timeline, "ghost"))
        assertNull(properties.audioTarget(timeline, null))
    }

    @Test
    fun `doubling the speed halves the clip and its linked audio and ripples later clips on those tracks`() {
        val result = properties.speed(timeline, "A", ClipSpeed.of(PlaybackSpeed.DOUBLE))

        for (id in listOf("A", "Aa")) {
            val clip = result.clip(id)!!
            assertEquals(ClipSpeed.of(PlaybackSpeed.DOUBLE), clip.speed)
            assertEquals(50, clip.durationFrames)
            assertEquals(0, clip.start)
        }
        assertEquals(50, result.clip("B")!!.start)
        assertEquals(100, result.clip("C")!!.start)
        assertEquals(120, result.clip("T")!!.start) // V2 is not an affected track
    }

    @Test
    fun `slowing down lengthens the clip and pushes later clips right without overlap`() {
        val result = properties.speed(timeline, "A", ClipSpeed.of(PlaybackSpeed.HALF))

        assertEquals(200, result.clip("A")!!.durationFrames)
        assertEquals(200, result.clip("Aa")!!.durationFrames)
        assertEquals(200, result.clip("B")!!.start)
        assertEquals(250, result.clip("C")!!.start)
        assertEquals(120, result.clip("T")!!.start)
    }

    @Test
    fun `a speed change keeps at least the minimum clip length and the same speed changes nothing`() {
        val short = timelineOf(clipOf("S", 0, 6))
        assertEquals(TrimClipUseCase.MIN_FRAMES, properties.speed(short, "S", ClipSpeed.of(PlaybackSpeed.QUADRUPLE)).clip("S")!!.durationFrames)

        assertSame(timeline, properties.speed(timeline, "A", ClipSpeed.of(PlaybackSpeed.NORMAL)))
    }

    @Test
    fun `reverse flags the clip together with its linked partner`() {
        val linked = properties.reverse(timeline, "A", true)
        assertEquals(listOf(true, true, false), listOf("A", "Aa", "B").map { linked.clip(it)!!.reverse })

        val single = properties.reverse(timeline, "B", true)
        assertEquals(listOf(false, false, true), listOf("A", "Aa", "B").map { single.clip(it)!!.reverse })
    }

    @Test
    fun `effect edits touch only the addressed effect and clamp its amount`() {
        val vignette = ClipEffect("fx-1", EffectType.VIGNETTE)
        val glow = ClipEffect("fx-2", EffectType.GLOW, amount = 30)
        val withEffects = timelineOf(clipOf("A", 0, 100).copy(effects = listOf(vignette, glow)))

        val disabled = properties.effect(withEffects, "A", "fx-1", enabled = false, amount = 150).clip("A")!!.effects
        assertEquals(listOf(vignette.copy(enabled = false, amount = 100), glow), disabled)

        val quiet = properties.effect(withEffects, "A", "fx-2", amount = -5).clip("A")!!.effects
        assertEquals(listOf(vignette, glow.copy(amount = 0)), quiet)
    }
}
