package com.example.prismstudio.p40b43a88.data.engine

import com.example.prismstudio.p40b43a88.domain.model.ZoomEase
import com.example.prismstudio.p40b43a88.domain.model.ZoomDirection
import com.example.prismstudio.p40b43a88.domain.model.DynamicZoom
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusX
import com.example.prismstudio.p40b43a88.domain.model.ZoomFocusY
import com.example.prismstudio.p40b43a88.domain.clipOf
import com.example.prismstudio.p40b43a88.domain.model.ClipTransform
import com.example.prismstudio.p40b43a88.domain.model.ColorGrade
import com.example.prismstudio.p40b43a88.domain.model.GradeKeyframe
import com.example.prismstudio.p40b43a88.domain.model.DuckEnvelope
import com.example.prismstudio.p40b43a88.domain.model.HslQualifier
import com.example.prismstudio.p40b43a88.domain.model.KeyEase
import com.example.prismstudio.p40b43a88.domain.model.MixerState
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.NrRadius
import com.example.prismstudio.p40b43a88.domain.model.SpatialNoiseReduction
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackDucking
import com.example.prismstudio.p40b43a88.domain.model.WindowGeometry
import com.example.prismstudio.p40b43a88.domain.model.WindowKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.TransformKeyframe
import com.example.prismstudio.p40b43a88.domain.model.CurveParam
import com.example.prismstudio.p40b43a88.domain.model.TitleColor
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionEase
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.keyframedTransform
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.projectOf
import com.example.prismstudio.p40b43a88.domain.timelineOf
import com.example.prismstudio.p40b43a88.domain.titleClipOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import com.example.prismstudio.p40b43a88.domain.model.ChromaKeyKernel
import com.example.prismstudio.p40b43a88.domain.model.ChromaKey

/** What the GPU reads for a clip's grade: the primary LUT, and a power window's composite and geometry. */
class LiveLookRegistryTest {

    private val registry = LiveLookRegistry()

    private fun graded(grade: ColorGrade): ProjectDocument = projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(grade = grade)))

    private val window = PowerWindow(
        WindowShape.RECTANGLE, centerX = 0.25f, centerY = -0.5f, size = 0.4f, aspect = 1.5f, angle = 90f, softness = 0.3f, invert = true,
        grade = ColorGrade(exposure = -1f),
    )

    @Test
    fun `a transition's style reaches the shader live - eased slopes, direction, edge and border colour`() {
        val wipe = Transition(TransitionType.WIPE, 15, TransitionEase.IN, reverse = true, softness = 40, border = 50, borderColor = TitleColor.AMBER)
        assertTrue(registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transitionIn = wipe)))))
        // A wipe turns by its angle: its reverse comes through as half a turn, not as the reverse flag.
        assertArrayEquals(
            floatArrayOf(0f, 1f, 0f, 0.4f * Transition.SOFTNESS_SPAN, 0.5f * Transition.BORDER_SPAN, 0xF2 / 255f, 0xB3 / 255f, 0x3D / 255f, Math.PI.toFloat()),
            registry.videoLook("V")!!.transitionStyle,
            1e-6f,
        )
        val diagonal = wipe.copy(reverse = false, angle = 45)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transitionIn = diagonal))))
        assertEquals(Math.PI.toFloat() / 4, registry.videoLook("V")!!.transitionStyle[8], 1e-6f)
        // The shape wipes keep the reverse flag; only the wipe has an angle.
        val iris = wipe.copy(type = TransitionType.IRIS, angle = 45)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transitionIn = iris))))
        assertEquals(listOf(1f, 0f), registry.videoLook("V")!!.transitionStyle.let { listOf(it[2], it[8]) })
        // A cross dissolve has neither direction nor edge: only its ease reaches the shader.
        val dissolve = wipe.copy(type = TransitionType.CROSS_DISSOLVE, ease = TransitionEase.OUT)
        assertTrue("restyling redraws without a new player", registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transitionIn = dissolve)))))
        val style = registry.videoLook("V")!!.transitionStyle
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f), style.copyOfRange(0, 5), 0f)

        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90))))
        assertArrayEquals(LiveLookRegistry.NO_TRANSITION_STYLE, registry.videoLook("V")!!.transitionStyle, 0f)
    }

    @Test
    fun `a colour dip's colour reaches the shader in the border colour's place, and its code is 11`() {
        val dip = Transition(TransitionType.DIP_TO_COLOR, 30, borderColor = TitleColor.CYAN, color = TitleColor.RED)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transitionIn = dip))))
        val style = registry.videoLook("V")!!.transitionStyle
        assertArrayEquals("no direction, no edge", floatArrayOf(0f, 0f, 0f), style.copyOfRange(2, 5), 0f)
        assertArrayEquals("red, not the border's cyan", floatArrayOf(0xF2 / 255f, 0x54 / 255f, 0x3D / 255f), style.copyOfRange(5, 8), 1e-6f)
        assertEquals(11f, ClipLookEffect.transitionCode(TransitionType.DIP_TO_COLOR), 0f)
        assertEquals("every kind has its own code", TransitionType.entries.size, TransitionType.entries.map { ClipLookEffect.transitionCode(it) }.toSet().size)
    }

    @Test
    fun `a window brings its composite LUT and geometry, and moving it reuses the baked LUTs`() {
        registry.update(graded(ColorGrade(saturation = 1.2f, window = window)))
        val look = registry.videoLook("V")!!
        assertNotNull(look.lutPixels)
        assertNotNull(look.windowLutPixels)
        assertArrayEquals(floatArrayOf(2f, 0.25f, -0.5f, 0.4f, 1.5f, (PI / 2).toFloat(), 0.3f, 1f), look.window, 1e-6f)

        registry.update(graded(ColorGrade(saturation = 1.2f, window = window.copy(centerX = -0.5f))))
        val moved = registry.videoLook("V")!!
        assertEquals(-0.5f, moved.window!![1], 0f)
        assertSame("no LUT is re-baked or re-uploaded while the window moves", look.lutPixels, moved.lutPixels)
        assertSame(look.windowLutPixels, moved.windowLutPixels)
    }

    @Test
    fun `a neutral node bakes nothing of its own but keeps its matte for the highlight`() {
        registry.update(graded(ColorGrade(window = window)))
        val windowOnly = registry.videoLook("V")!!
        assertNull("node 1 is neutral", windowOnly.lutPixels)
        assertNotNull(windowOnly.windowLutPixels)

        registry.update(graded(ColorGrade(exposure = 1f, window = PowerWindow(qualifier = HslQualifier(hueCenter = 120f)))))
        val primaryOnly = registry.videoLook("V")!!
        assertNotNull(primaryOnly.lutPixels)
        assertSame("a window without a grade composites to node 1's LUT", primaryOnly.lutPixels, primaryOnly.windowLutPixels)
        assertEquals(8, primaryOnly.window!!.size)
        // The last four: matte black point, white point and blur radius (none), and padding.
        assertArrayEquals(floatArrayOf(120f, 30f, 15f, 0f, 0.15f, 1f, 0.1f, 0f, 0f, 1f, 0.1f, 0f, 0f, 1f, 0f, 0f), primaryOnly.qualifier, 0f)
    }

    @Test
    fun `a keyed window is where its keyframes put it on every frame, as the colour page computes`() {
        val keyed = window.copy(
            keyframes = listOf(
                WindowKeyframe(10, WindowGeometry(-0.5f, 0f, 0.4f, 1f, 0f, 0.1f)),
                WindowKeyframe(40, WindowGeometry(0.5f, 0.2f, 0.8f, 2f, 90f, 0.5f), KeyEase(slopeIn = 0f)),
            ),
        )
        registry.update(graded(ColorGrade(window = keyed)))
        val look = registry.videoLook("V")!!
        val out = FloatArray(8)
        for (frame in listOf(0, 10, 17, 25, 33, 40, 70)) {
            look.windowAt(frame.toFloat(), out)
            val w = keyed.at(frame)
            val expected = floatArrayOf(2f, w.centerX, w.centerY, w.size, w.aspect, (w.angle * PI / 180).toFloat(), w.softness, 1f)
            assertArrayEquals("frame $frame", expected, out, 1e-5f)
        }

        registry.update(graded(ColorGrade(window = window)))
        registry.videoLook("V")!!.windowAt(25f, out)
        assertArrayEquals("without keyframes the window stays put", registry.videoLook("V")!!.window, out, 0f)
    }

    @Test
    fun `a crop reaches the shader as shares of the frame, and a keyed one moves with the transform`() {
        val crop = ClipTransform(cropLeft = 480, cropRight = 240, cropTop = 108, cropBottom = 54, cropSoftness = 50)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transform = crop))))
        val out = FloatArray(LiveLookRegistry.TRANSFORM_VALUES)
        registry.videoLook("V")!!.transformAt(12f, out)
        // Scale, offsets, rotation, opacity; then left, right, top, bottom and the softness (a share of the height).
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0.25f, 0.125f, 0.1f, 0.05f, 0.05f), out, 1e-6f)

        val keys = listOf(
            TransformKeyframe(10, ClipTransform(scalePercent = 50)),
            TransformKeyframe(40, ClipTransform(scalePercent = 100, cropLeft = 960, cropSoftness = 100), KeyEase(slopeIn = 0f)),
        )
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(keyframes = keys))))
        val look = registry.videoLook("V")!!
        for (frame in listOf(0, 10, 18, 25, 33, 40, 70)) {
            look.transformAt(frame.toFloat(), out)
            val t = keyframedTransform(ClipTransform(), keys, frame)
            assertEquals("frame $frame scale", t.scalePercent / 100f, out[0], 0.006f)
            assertEquals("frame $frame crop left", t.cropLeft / 1920f, out[5], 0.0006f)
            assertEquals("frame $frame softness", t.cropSoftness / 1000f, out[9], 0.0006f)
        }

        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90))))
        registry.videoLook("V")!!.transformAt(5f, out)
        assertArrayEquals("no crop", FloatArray(5), out.copyOfRange(5, 10), 0f)
    }

    @Test
    fun `each parameter moves between its own keys in the shader, as the edit page computes`() {
        val base = ClipTransform(opacityPercent = 70, cropTop = 108)
        val keys = listOf(
            TransformKeyframe(0, ClipTransform(scalePercent = 50), params = setOf(CurveParam.SCALE)),
            TransformKeyframe(20, ClipTransform(positionX = 480, positionY = -270), KeyEase(slopeOut = 0f), params = setOf(CurveParam.POSITION_X, CurveParam.POSITION_Y)),
            TransformKeyframe(45, ClipTransform(scalePercent = 150, rotationDegrees = 90), params = setOf(CurveParam.SCALE, CurveParam.ROTATION), paramEases = mapOf(CurveParam.SCALE to KeyEase(slopeIn = 0f))),
            TransformKeyframe(70, ClipTransform(positionX = -480, positionY = 270), params = setOf(CurveParam.POSITION_X, CurveParam.POSITION_Y)),
        )
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(transform = base, keyframes = keys))))
        val look = registry.videoLook("V")!!
        val out = FloatArray(LiveLookRegistry.TRANSFORM_VALUES)
        for (frame in listOf(0, 10, 20, 30, 45, 55, 70, 89)) {
            look.transformAt(frame.toFloat(), out)
            val t = keyframedTransform(base, keys, frame)
            assertEquals("frame $frame scale", t.scalePercent / 100f, out[0], 0.006f)
            assertEquals("frame $frame x", t.positionX / 1920f, out[1], 0.001f)
            assertEquals("frame $frame rotation", t.rotationDegrees.toFloat(), out[3] * 180f / PI.toFloat(), 0.6f)
            assertEquals("frame $frame opacity (never keyed)", 0.7f, out[4], 1e-6f)
            assertEquals("frame $frame crop top (never keyed)", 0.1f, out[7], 1e-6f)
        }
    }

    @Test
    fun `a mask reaches the shader like a window, keyed on every frame, and the full frame masks nothing`() {
        val mask = PowerWindow(shape = WindowShape.RECTANGLE, centerX = 0.2f, size = 0.4f, aspect = 1.5f, angle = 90f, softness = 0.1f, invert = true)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(mask = mask))))
        val look = registry.videoLook("V")!!
        assertArrayEquals(floatArrayOf(2f, 0.2f, 0f, 0.4f, 1.5f, (PI / 2).toFloat(), 0.1f, 1f), look.mask, 1e-6f)
        assertNull("not a grade window", look.window)
        val out = FloatArray(8)
        val keyed = mask.copy(keyframes = listOf(WindowKeyframe(10, WindowGeometry(-0.5f, 0f, 0.4f, 1.5f, 0f, 0.1f)), WindowKeyframe(50, WindowGeometry(0.5f, 0.3f, 0.8f, 1.5f, 45f, 0.2f), KeyEase(slopeIn = 0f))))
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(mask = keyed))))
        for (frame in listOf(0, 10, 25, 40, 50, 80)) {
            registry.videoLook("V")!!.maskAt(frame.toFloat(), out)
            val w = keyed.at(frame)
            assertArrayEquals("frame $frame", floatArrayOf(2f, w.centerX, w.centerY, w.size, w.aspect, (w.angle * PI / 180).toFloat(), w.softness, 1f), out, 1e-4f)
        }
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90).copy(mask = PowerWindow(shape = WindowShape.FULL)))))
        assertNull(registry.videoLook("V")!!.mask)
    }

    @Test
    fun `dynamic zoom scales the picture about its focus, which stays where the transform put it`() {
        val push = DynamicZoom(ZoomDirection.IN, 20, ZoomEase.LINEAR, ZoomFocusX.RIGHT, ZoomFocusY.TOP)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 61).copy(dynamicZoom = push))))
        val out = FloatArray(LiveLookRegistry.TRANSFORM_VALUES)
        val look = registry.videoLook("V")!!
        look.transformAt(60f, out)
        look.applyZoom(60f, out, 16f / 9f)
        // The full picture's top-right corner stays in the frame's: shifted left and down by a tenth of the frame.
        assertArrayEquals(floatArrayOf(1.2f, -0.1f, -0.1f, 0f), out.copyOfRange(0, 4), 1e-6f)
        // Under a scaled, moved and turned picture, the corner stays where the transform put it (o = s·R(−θ)·a + t).
        val aspect = 16f / 9f
        val t = ClipTransform(scalePercent = 60, positionX = 300, positionY = -120, rotationDegrees = 30)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 61).copy(transform = t, dynamicZoom = push))))
        val turned = registry.videoLook("V")!!
        fun corner(x: FloatArray): Pair<Float, Float> {
            val ax = 0.5f * aspect
            val ay = 0.5f
            val c = kotlin.math.cos(x[3])
            val s = kotlin.math.sin(x[3])
            return (x[0] * (c * ax + s * ay) + x[1] * aspect) to (x[0] * (-s * ax + c * ay) + x[2])
        }
        turned.transformAt(60f, out)
        val before = corner(out)
        turned.applyZoom(60f, out, aspect)
        assertEquals(0.72f, out[0], 1e-6f)
        val after = corner(out)
        assertEquals(before.first, after.first, 1e-5f)
        assertEquals(before.second, after.second, 1e-5f)
    }

    @Test
    fun `a picture's fade handles take it down from its opacity over its ends, and a trim moves the fade out`() {
        val faded = clipOf("V", 0, 90).copy(fadeInSeconds = 1f, fadeOutSeconds = 0.5f)
        assertTrue(registry.update(projectOf(timeline = timelineOf(faded))))
        val look = registry.videoLook("V")!!
        assertEquals(listOf(30f, 15f), listOf(look.fadeInFrames, look.fadeOutFrames))
        assertEquals(0f, look.fadeAt(0f), 0f)
        assertEquals(0.5f, look.fadeAt(15f), 1e-6f)
        assertEquals(1f, look.fadeAt(60f), 0f)
        assertEquals(0.5f, look.fadeAt(82.5f), 1e-6f)
        assertTrue("a trim rebuilds a look that fades out", registry.update(projectOf(timeline = timelineOf(faded.copy(durationFrames = 60)))))
        assertEquals(0.5f, registry.videoLook("V")!!.fadeAt(52.5f), 1e-6f)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 90))))
        assertEquals("no fades: the look leaves the opacity alone", 1f, registry.videoLook("V")!!.fadeAt(0f), 0f)
        // A title's look carries its fades too (a subtitle's none).
        val title = titleClipOf("T", 0, 60).copy(fadeInSeconds = 0.5f)
        assertTrue(registry.update(projectOf(timeline = timelineOf(title))))
        assertEquals(15f, registry.titleLook("T")!!.fadeInFrames, 0f)
        assertTrue("a changed fade redraws the title", registry.update(projectOf(timeline = timelineOf(title.copy(fadeInSeconds = 1f)))))
        assertEquals(30f, registry.titleLook("T")!!.fadeInFrames, 0f)
    }

    @Test
    fun `a keyer reaches the shader as its uniforms, and a changed key rebuilds the look`() {
        val clip = clipOf("V", 0, 90)
        registry.update(projectOf(timeline = timelineOf(clip)))
        assertNull(registry.videoLook("V")!!.chromaKey)
        val key = ChromaKey(similarity = 50f)
        assertTrue(registry.update(projectOf(timeline = timelineOf(clip.copy(chromaKey = key)))))
        assertArrayEquals(ChromaKeyKernel.uniforms(key), registry.videoLook("V")!!.chromaKey, 0f)
        assertTrue(registry.update(projectOf(timeline = timelineOf(clip.copy(chromaKey = key.copy(spill = 0f))))))
        assertEquals(0f, registry.videoLook("V")!!.chromaKey!![4], 0f)
    }

    @Test
    fun `fade curves bend a picture's and a title's fades, and a changed curve rebuilds the look`() {
        val faded = clipOf("V", 0, 90).copy(fadeInSeconds = 1f, fadeOutSeconds = 1f)
        registry.update(projectOf(timeline = timelineOf(faded)))
        assertEquals(0.25f, registry.videoLook("V")!!.fadeAt(7.5f), 1e-6f)
        assertTrue(registry.update(projectOf(timeline = timelineOf(faded.copy(fadeInCurve = 1f)))))
        val look = registry.videoLook("V")!!
        assertEquals("a quarter in, bowed all the way up", 0.75f, look.fadeAt(7.5f), 1e-5f)
        assertEquals("the fade out stays straight", 0.25f, look.fadeAt(82.5f), 1e-5f)
        val title = titleClipOf("T", 0, 60).copy(fadeInSeconds = 1f)
        registry.update(projectOf(timeline = timelineOf(title)))
        assertTrue("a changed curve redraws the title", registry.update(projectOf(timeline = timelineOf(title.copy(fadeInCurve = -1f)))))
        assertEquals(-1f, registry.titleLook("T")!!.fadeInCurve, 0f)
    }

    @Test
    fun `spatial noise reduction reaches the shader apart from the LUTs, which it never re-bakes`() {
        val nr = SpatialNoiseReduction(lumaThreshold = 40f, chromaThreshold = 20f, radius = NrRadius.SMALL, amount = 50f)
        assertTrue(registry.update(graded(ColorGrade(spatialNr = nr))))
        val look = registry.videoLook("V")!!
        assertArrayEquals(floatArrayOf(0.06f, 0.02f, 1f, 0.5f), look.noiseReduction, 1e-6f)
        assertNull("noise reduction alone bakes no LUT", look.lutPixels)
        val warm = ColorGrade(temperature = 20f)
        registry.update(graded(warm))
        val baked = registry.videoLook("V")!!.lutPixels
        assertNull(registry.videoLook("V")!!.noiseReduction)
        assertTrue(registry.update(graded(warm.copy(spatialNr = nr))))
        assertSame("the same colour shares its baked LUT", baked, registry.videoLook("V")!!.lutPixels)
    }

    @Test
    fun `dynamic zoom reaches the shader over the clip's length, and a trim respreads it`() {
        val zoomed = clipOf("V", 0, 61).copy(dynamicZoom = DynamicZoom(ZoomDirection.IN, 20, ZoomEase.LINEAR))
        registry.update(projectOf(timeline = timelineOf(zoomed)))
        assertEquals(1f, registry.videoLook("V")!!.zoomAt(0f), 1e-6f)
        assertEquals(1.2f, registry.videoLook("V")!!.zoomAt(60f), 1e-6f)
        assertEquals(true, registry.update(projectOf(timeline = timelineOf(zoomed.copy(durationFrames = 31)))))
        assertEquals("the move now ends on the trimmed clip's last frame", 1.2f, registry.videoLook("V")!!.zoomAt(30f), 1e-6f)
        registry.update(projectOf(timeline = timelineOf(clipOf("V", 0, 61))))
        assertEquals("off", 1f, registry.videoLook("V")!!.zoomAt(60f), 0f)
    }

    @Test
    fun `a ducked lane dips only with its key track there, and its envelope arrives apart`() {
        val timeline = timelineOf(clipOf("talk", 0, 60, track = Timeline.A1), clipOf("song", 45, 120, track = Timeline.A2))
        val duck = TrackDucking(keyTrackId = Timeline.A1, depthDb = 10f)
        fun ducked(d: TrackDucking) = projectOf(
            timeline = timeline,
            mixer = MixerState.forTracks(timeline.tracks).let { it.replace(it.channel(Timeline.A2).copy(ducking = d)) },
        )
        registry.update(ducked(duck))
        assertEquals(10f, registry.laneMix(Timeline.A2)!!.duckDepthDb, 0f)
        assertEquals("the key does not dip", 0f, registry.laneMix(Timeline.A1)!!.duckDepthDb, 0f)
        assertEquals("the clip's place on the timeline, for the envelope", 1_500_000L, registry.clipMix("song")!!.startUs)
        registry.update(ducked(duck.copy(enabled = false)))
        assertEquals("bypassed", 0f, registry.laneMix(Timeline.A2)!!.duckDepthDb, 0f)
        registry.update(ducked(duck.copy(keyTrackId = "a9")))
        assertEquals("its key track deleted", 0f, registry.laneMix(Timeline.A2)!!.duckDepthDb, 0f)
        registry.update(ducked(duck.copy(keyTrackId = Timeline.A2)))
        assertEquals("never keyed from itself", 0f, registry.laneMix(Timeline.A2)!!.duckDepthDb, 0f)

        val envelope = DuckEnvelope(10_000, floatArrayOf(1f))
        registry.setDucks(mapOf(Timeline.A2 to envelope))
        assertSame(envelope, registry.duck(Timeline.A2))
        registry.update(ducked(duck))
        assertSame("an update keeps the envelopes", envelope, registry.duck(Timeline.A2))
        registry.setDucks(emptyMap())
        assertNull(registry.duck(Timeline.A2))
    }

    @Test
    fun `a keyed grade bakes one LUT per keyframe and dissolves between neighbours on every frame`() {
        val warm = ColorGrade(temperature = 20f)
        val cool = ColorGrade(temperature = -20f)
        val keyed = ColorGrade(
            window = PowerWindow(grade = ColorGrade(exposure = 1f)),
            keyframes = listOf(GradeKeyframe(10, warm), GradeKeyframe(40, cool, KeyEase(slopeIn = 0f)), GradeKeyframe(70, warm)),
        )
        registry.update(graded(keyed))
        val look = registry.videoLook("V")!!
        val luts = look.keyLuts!!
        assertEquals(3, luts.size)
        assertSame("equal keyframe grades share a LUT", luts[0], luts[2])
        assertSame(luts[0], look.lutPixels)
        assertEquals(3, look.keyWindowLuts!!.size)
        val span = FloatArray(3)
        look.gradeSpanAt(5f, span)
        assertArrayEquals("before the first: its look alone", floatArrayOf(0f, 0f, 0f), span, 0f)
        look.gradeSpanAt(25f, span)
        assertEquals(0f to 1f, span[0] to span[1])
        assertEquals("easing into the second keyframe", 0.625f, span[2], 1e-5f)
        look.gradeSpanAt(55f, span)
        assertArrayEquals(floatArrayOf(1f, 2f, 0.5f), span, 1e-6f)
        look.gradeSpanAt(80f, span)
        assertArrayEquals("after the last: its look alone", floatArrayOf(2f, 2f, 0f), span, 0f)
        registry.update(graded(ColorGrade(temperature = 20f)))
        assertNull("a still grade has no keyframe LUTs", registry.videoLook("V")!!.keyLuts)
    }
}
