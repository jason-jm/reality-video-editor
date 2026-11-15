package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.roundToInt

/**
 * How the motion arrives at and leaves a keyframe: its speed there relative to moving evenly between
 * the neighbouring keyframes — 1 moves linearly, 0 comes to rest (Resolve's ease in / ease out). A
 * keyframe added or cut inside an eased stretch gets the values that keep the motion unchanged.
 */
data class KeyEase(val slopeIn: Float = 1f, val slopeOut: Float = 1f) {
    /** The motion slows to a stop on its way into the keyframe. */
    val easesIn: Boolean get() = slopeIn == 0f

    /** The motion starts from rest on its way out of the keyframe. */
    val easesOut: Boolean get() = slopeOut == 0f

    /** Easing turned [on] or off on the arriving ([arriving]) or the leaving side. */
    fun with(arriving: Boolean, on: Boolean): KeyEase {
        val slope = if (on) 0f else 1f
        return if (arriving) copy(slopeIn = slope) else copy(slopeOut = slope)
    }

    companion object {
        val LINEAR = KeyEase()

        /** The steepest end a monotonic cubic can have: within it the motion never overshoots. */
        const val MAX_SLOPE = 3f
    }
}

/**
 * The clip's transform [frame] timeline frames after the clip starts. Resolve keys each parameter on its own: the keyframe
 * keys the parameters in [params] (all of them unless said otherwise); [transform] holds the rest at the values they have
 * at this frame. [paramEases] gives a parameter an ease of its own in place of [ease] (the curve editor's).
 */
data class TransformKeyframe(
    val frame: Int,
    val transform: ClipTransform,
    val ease: KeyEase = KeyEase.LINEAR,
    val params: Set<CurveParam> = TransformKeyGroup.ALL,
    val paramEases: Map<CurveParam, KeyEase> = emptyMap(),
) {
    /** How the motion of [param] arrives and leaves here. */
    fun easeOf(param: CurveParam): KeyEase = paramEases[param] ?: ease

    /** Every parameter keyed with the one ease: a keyframe as it was before parameters were keyed apart. */
    val whole: Boolean get() = params.size == CurveParam.entries.size && paramEases.isEmpty()
}

/**
 * The parameters the inspector keys together, as Resolve's keyframe buttons do: 缩放, 位置 (X and Y), 旋转, 不透明度 and
 * 裁切 (the four edges and the soft edge).
 */
enum class TransformKeyGroup(val label: String, val params: Set<CurveParam>) {
    SCALE("缩放", setOf(CurveParam.SCALE)),
    POSITION("位置", setOf(CurveParam.POSITION_X, CurveParam.POSITION_Y)),
    ROTATION("旋转", setOf(CurveParam.ROTATION)),
    OPACITY("不透明度", setOf(CurveParam.OPACITY)),
    CROP("裁切", setOf(CurveParam.CROP_LEFT, CurveParam.CROP_RIGHT, CurveParam.CROP_TOP, CurveParam.CROP_BOTTOM, CurveParam.CROP_SOFTNESS));

    companion object {
        val ALL: Set<CurveParam> = CurveParam.entries.toSet()

        fun of(param: CurveParam): TransformKeyGroup = entries.first { param in it.params }
    }
}

/** A parameter group's keyframe button: no keys (hollow), keys elsewhere (marked), or a key at the playhead (filled). */
enum class ParamKeyState { NONE, ANIMATED, AT_KEY }

/** One parameter's key: its value (in the parameter's own unit) [frame] frames into the clip. */
data class ParamKey(val frame: Int, val value: Float, val ease: KeyEase = KeyEase.LINEAR)

/** The clip's volume (dB) [frame] timeline frames after the clip starts. */
data class VolumeKeyframe(val frame: Int, val volumeDb: Float, val ease: KeyEase = KeyEase.LINEAR)

/** The timeline's volume line on a sound clip (Resolve's audio overlay): −30 dB at the bottom, +12 dB at the top. */
object VolumeLine {
    const val MIN_DB = -30f
    const val MAX_DB = 12f
}

/**
 * What a power window's keyframes animate: its centre, size, aspect, angle and soft edge (the shape,
 * inversion, qualifier and grade stay as they are).
 */
data class WindowGeometry(
    val centerX: Float,
    val centerY: Float,
    val size: Float,
    val aspect: Float,
    val angle: Float,
    val softness: Float,
) {
    fun lerp(to: WindowGeometry, t: Float) = WindowGeometry(
        centerX + (to.centerX - centerX) * t,
        centerY + (to.centerY - centerY) * t,
        size + (to.size - size) * t,
        aspect + (to.aspect - aspect) * t,
        angle + (to.angle - angle) * t,
        softness + (to.softness - softness) * t,
    )
}

/** The window's geometry [frame] timeline frames after the clip starts. */
data class WindowKeyframe(val frame: Int, val geometry: WindowGeometry, val ease: KeyEase = KeyEase.LINEAR)

/** Node 1's correction (a grade without window or keyframes) [frame] timeline frames after the clip starts. */
data class GradeKeyframe(val frame: Int, val grade: ColorGrade, val ease: KeyEase = KeyEase.LINEAR)

/**
 * How node 1 grades the picture at one frame of a keyed grade: the look of the keyframe before ([from])
 * dissolving into the next one's ([to]) by [mix] (0…1, eased) — Resolve's dissolve between keyframes.
 */
data class GradeDissolve(val from: ColorGrade, val to: ColorGrade, val mix: Float)

/**
 * Progress (0..1) at [t] (0..1) of the way between two keyframes, leaving the first at [slopeOut] and
 * reaching the second at [slopeIn] (see [KeyEase]): a cubic Hermite curve, a straight line when both are 1.
 */
fun easedProgress(t: Float, slopeOut: Float, slopeIn: Float): Float {
    if (slopeOut == 1f && slopeIn == 1f) return t
    val t2 = t * t
    val t3 = t2 * t
    return (t3 - 2 * t2 + t) * slopeOut + (3 * t2 - 2 * t3) + (t3 - t2) * slopeIn
}

/** The clip's transform [frame] frames into the clip (see [keyframedTransform]). */
fun Clip.transformAt(frame: Int): ClipTransform = keyframedTransform(transform, keyframes, frame)

/**
 * [base] without keyframes; with [keyframes] (sorted), moving between neighbours as their [KeyEase]
 * says, and held before the first and after the last.
 */
fun keyframedTransform(base: ClipTransform, keyframes: List<TransformKeyframe>, frame: Int): ClipTransform {
    if (keyframes.isEmpty()) return base
    if (keyframes.all { it.whole }) return TRANSFORM_KEYS.valueAt(keyframes, frame, base)
    // Each parameter from its own keys; one without any keeps its value.
    var t = base
    for (p in CurveParam.entries) {
        val track = keyframes.track(p)
        if (track.isNotEmpty()) t = p.write(t, PARAM_KEYS.valueAt(track, frame, p.read(base).toFloat()).roundToInt())
    }
    return t
}

/** [param]'s own keys among these keyframes: those that key it, with its ease there. */
fun List<TransformKeyframe>.track(param: CurveParam): List<ParamKey> =
    filter { param in it.params }.map { ParamKey(it.frame, param.read(it.transform).toFloat(), it.easeOf(param)) }

/** [param]'s value [frame] frames into the clip, as a float between keys (the render moves smoothly between whole units). */
fun List<TransformKeyframe>.paramAt(param: CurveParam, frame: Float, base: ClipTransform): Float {
    val track = track(param)
    if (track.isEmpty()) return param.read(base).toFloat()
    val (a, b, t) = PARAM_KEYS.spanAt(track, frame)
    return a.value + (b.value - a.value) * t
}

/**
 * Keyframes from each parameter's keys ([tracks], parameters without keys left out): one per frame any parameter is keyed
 * at, keying those parameters, the rest at their values there ([base] where they have no keys); the keyed parameters' ease
 * shared when they agree, else the first's with the others' as their own.
 */
fun transformKeyframes(tracks: Map<CurveParam, List<ParamKey>>, base: ClipTransform): List<TransformKeyframe> {
    val frames = tracks.values.flatMap { list -> list.map { it.frame } }.distinct().sorted()
    return frames.map { f ->
        val keyed = CurveParam.entries.filter { p -> tracks[p].orEmpty().any { it.frame == f } }
        var t = base
        for (p in CurveParam.entries) {
            val track = tracks[p].orEmpty()
            val value = track.firstOrNull { it.frame == f }?.value ?: if (track.isEmpty()) p.read(base).toFloat() else PARAM_KEYS.valueAt(track, f, 0f)
            t = p.write(t, value.roundToInt())
        }
        val eases = keyed.associateWith { p -> tracks.getValue(p).first { it.frame == f }.ease }
        val shared = eases.values.first()
        TransformKeyframe(f, t, shared, keyed.toSet(), eases.filterValues { it != shared })
    }
}

/** Each parameter's keys of [keys] (only those with any). */
private fun List<TransformKeyframe>.tracks(): Map<CurveParam, List<ParamKey>> =
    CurveParam.entries.associateWith { track(it) }.filterValues { it.isNotEmpty() }

/**
 * [change] applied to each parameter's keys on its own, the keyframes rebuilt from them; keyframes that key every parameter
 * with one ease change as whole transforms (the same result, faster).
 */
private fun List<TransformKeyframe>.perParam(base: ClipTransform, whole: (List<TransformKeyframe>) -> List<TransformKeyframe>, change: (CurveParam, List<ParamKey>) -> List<ParamKey>): List<TransformKeyframe> {
    if (isEmpty()) return this
    if (all { it.whole }) return whole(this)
    return transformKeyframes(tracks().mapValues { (p, track) -> change(p, track) }.filterValues { it.isNotEmpty() }, base)
}

/**
 * These keyframes with [params] keyed at [frame], each at its value there (the motion unchanged): a new key on its own
 * track, or the one already there kept.
 */
fun List<TransformKeyframe>.withKeysAt(frame: Int, params: Set<CurveParam>, base: ClipTransform): List<TransformKeyframe> {
    if (params.size == CurveParam.entries.size && all { it.whole }) return TRANSFORM_KEYS.withKeyAt(this, frame, keyframedTransform(base, this, frame))
    val tracks = tracks().toMutableMap()
    for (p in params) {
        val track = tracks[p].orEmpty()
        tracks[p] = PARAM_KEYS.withKeyAt(track, frame, if (track.isEmpty()) p.read(base).toFloat() else PARAM_KEYS.valueAt(track, frame, 0f))
    }
    return transformKeyframes(tracks, base)
}

/** These keyframes without [params]' keys at [frame] (a keyframe left keying nothing goes). */
fun List<TransformKeyframe>.withoutKeysAt(frame: Int, params: Set<CurveParam>, base: ClipTransform): List<TransformKeyframe> {
    val tracks = tracks().mapValues { (p, track) -> if (p in params) track.filter { it.frame != frame } else track }.filterValues { it.isNotEmpty() }
    return transformKeyframes(tracks, base)
}

/** The volume (dB) [frame] frames into the clip: [Clip.volumeDb] without volume keyframes, else interpolated in dB. */
fun Clip.volumeAt(frame: Int): Float = VOLUME_KEYS.valueAt(volumeKeyframes, frame, volumeDb)

/** The pan (−1…1) [frame] frames into the clip: [Clip.pan] without pan keyframes, else interpolated. */
fun Clip.panAt(frame: Int): Float = PARAM_KEYS.valueAt(panKeyframes, frame, pan)

fun Clip.panKeyframeAt(frame: Int): ParamKey? = panKeyframes.firstOrNull { it.frame == frame }

/** The pan keyframes with one at [frame], as [keyframesWithKeyAt]. */
fun Clip.panKeyframesWithKeyAt(frame: Int): List<ParamKey> = PARAM_KEYS.withKeyAt(panKeyframes, frame, panAt(frame))

/** The pan keyframes of the stretch [from] until [from] + [length], as [keyframesFor]. */
fun Clip.panKeyframesFor(from: Int, length: Int): List<ParamKey> = PARAM_KEYS.window(panKeyframes, from, length, pan)

/** Pan keyframes of a clip whose length changes from [oldFrames] to [newFrames] with its content (speed). */
fun List<ParamKey>.retimedPan(oldFrames: Int, newFrames: Int): List<ParamKey> = PARAM_KEYS.retime(this, oldFrames, newFrames)

fun Clip.keyframeAt(frame: Int): TransformKeyframe? = keyframes.firstOrNull { it.frame == frame }

fun Clip.volumeKeyframeAt(frame: Int): VolumeKeyframe? = volumeKeyframes.firstOrNull { it.frame == frame }

/**
 * The transform keyframes with one at [frame] — an existing one, or a new one holding the value there
 * that leaves the motion unchanged (an eased curve is split exactly).
 */
fun Clip.keyframesWithKeyAt(frame: Int): List<TransformKeyframe> = keyframes.withKeysAt(frame, TransformKeyGroup.ALL, transform)

/** The volume keyframes with one at [frame], as [keyframesWithKeyAt]. */
fun Clip.volumeKeyframesWithKeyAt(frame: Int): List<VolumeKeyframe> = VOLUME_KEYS.withKeyAt(volumeKeyframes, frame, volumeAt(frame))

fun ClipTransform.lerp(to: ClipTransform, t: Float): ClipTransform = ClipTransform(
    scalePercent = lerp(scalePercent, to.scalePercent, t),
    positionX = lerp(positionX, to.positionX, t),
    positionY = lerp(positionY, to.positionY, t),
    rotationDegrees = lerp(rotationDegrees, to.rotationDegrees, t),
    opacityPercent = lerp(opacityPercent, to.opacityPercent, t),
    cropLeft = lerp(cropLeft, to.cropLeft, t),
    cropRight = lerp(cropRight, to.cropRight, t),
    cropTop = lerp(cropTop, to.cropTop, t),
    cropBottom = lerp(cropBottom, to.cropBottom, t),
    cropSoftness = lerp(cropSoftness, to.cropSoftness, t),
)

private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

/**
 * The transform keyframes of the stretch [from] until [from] + [length] of this clip, rebased to start
 * at 0 — for a clip cut out of this one (split, trim). Where the stretch cuts through the animation a
 * keyframe holding the value there is added, so every frame keeps its value.
 */
fun Clip.keyframesFor(from: Int, length: Int): List<TransformKeyframe> =
    keyframes.perParam(transform, { TRANSFORM_KEYS.window(it, from, length, transform) }) { p, track -> PARAM_KEYS.window(track, from, length, p.read(transform).toFloat()) }

/** The volume keyframes of the stretch [from] until [from] + [length], as [keyframesFor]. */
fun Clip.volumeKeyframesFor(from: Int, length: Int): List<VolumeKeyframe> =
    VOLUME_KEYS.window(volumeKeyframes, from, length, volumeDb)

/** Keyframes of a clip whose length changes from [oldFrames] to [newFrames] with its content (speed). */
fun List<TransformKeyframe>.retimed(oldFrames: Int, newFrames: Int): List<TransformKeyframe> =
    perParam(firstOrNull()?.transform ?: ClipTransform(), { TRANSFORM_KEYS.retime(it, oldFrames, newFrames) }) { _, track -> PARAM_KEYS.retime(track, oldFrames, newFrames) }

fun List<VolumeKeyframe>.retimedVolume(oldFrames: Int, newFrames: Int): List<VolumeKeyframe> =
    VOLUME_KEYS.retime(this, oldFrames, newFrames)

/** The window's geometry as its fields hold it (the value without keyframes). */
val PowerWindow.geometry: WindowGeometry get() = WindowGeometry(centerX, centerY, size, aspect, angle, softness)

fun PowerWindow.withGeometry(g: WindowGeometry): PowerWindow =
    copy(centerX = g.centerX, centerY = g.centerY, size = g.size, aspect = g.aspect, angle = g.angle, softness = g.softness)

/** The window as it stands [frame] frames into the clip: its keyframes interpolated, held outside them. */
fun PowerWindow.at(frame: Int): PowerWindow = if (keyframes.isEmpty()) this else withGeometry(WINDOW_KEYS.valueAt(keyframes, frame, geometry))

fun PowerWindow.keyframeAt(frame: Int): WindowKeyframe? = keyframes.firstOrNull { it.frame == frame }

/** The window keyframes with one at [frame] (an existing one, or a new one that leaves the motion unchanged). */
fun PowerWindow.keyframesWithKeyAt(frame: Int): List<WindowKeyframe> = WINDOW_KEYS.withKeyAt(keyframes, frame, at(frame).geometry)

/** This grade with node 2's window as it stands [frame] frames into the clip (for drawing one frame). */
fun ColorGrade.windowAt(frame: Int): ColorGrade = window?.takeIf { it.keyframes.isNotEmpty() }?.let { copy(window = it.at(frame)) } ?: this

/**
 * The grade as it stands [frame] frames into the clip, for its controls: node 1's keyframes blended
 * setting by setting (held before the first and after the last), node 2 as it is.
 */
fun ColorGrade.at(frame: Int): ColorGrade =
    if (keyframes.isEmpty()) this else GRADE_KEYS.valueAt(keyframes, frame, primary).copy(window = window, spatialNr = spatialNr, midDetail = midDetail, temporalNr = temporalNr, serial = serial)

/** How node 1 grades the picture [frame] frames into the clip (see [GradeDissolve]); null without keyframes. */
fun ColorGrade.dissolveAt(frame: Float): GradeDissolve? {
    val keys = keyframes.takeIf { it.isNotEmpty() } ?: return null
    val (a, b, t) = GRADE_KEYS.spanAt(keys, frame)
    return GradeDissolve(a.grade, b.grade, t)
}

fun ColorGrade.gradeKeyframeAt(frame: Int): GradeKeyframe? = keyframes.firstOrNull { it.frame == frame }

/**
 * Node 1's keyframes with one at [frame] (an existing one, or a new one holding the correction there — its colour only:
 * the noise reduction is not keyframed).
 */
fun ColorGrade.gradeKeyframesWithKeyAt(frame: Int): List<GradeKeyframe> = GRADE_KEYS.withKeyAt(keyframes, frame, at(frame).primary.colorOnly)

/** This grade with its keyed node 1 and node 2's window fixed as they stand [frame] frames into the clip (a held frame). */
fun ColorGrade.frozenAt(frame: Int): ColorGrade {
    val held = if (keyframes.isEmpty()) this else at(frame).copy(keyframes = emptyList())
    return held.copy(window = held.window?.frozenAt(frame))
}

/** This grade for the stretch [from] until [from] + [length] of its clip, as [Clip.keyframesFor] rebases transform keys. */
fun ColorGrade.keysFor(from: Int, length: Int): ColorGrade {
    val graded = if (keyframes.isEmpty()) this else copy(keyframes = GRADE_KEYS.window(keyframes, from, length, primary))
    return graded.copy(window = graded.window?.keysFor(from, length))
}

/** This grade for a clip whose length changes from [oldFrames] to [newFrames] with its content (speed). */
fun ColorGrade.retimedKeys(oldFrames: Int, newFrames: Int): ColorGrade {
    val graded = if (keyframes.isEmpty()) this else copy(keyframes = GRADE_KEYS.retime(keyframes, oldFrames, newFrames))
    return graded.copy(window = graded.window?.retimedKeys(oldFrames, newFrames))
}

/** This window (a grade's or a clip's mask) fixed as it stands [frame] frames into its clip (a held frame). */
fun PowerWindow.frozenAt(frame: Int): PowerWindow = if (keyframes.isEmpty()) this else at(frame).copy(keyframes = emptyList())

/** This window's keyframes for the stretch [from] until [from] + [length] of its clip, as [Clip.keyframesFor]. */
fun PowerWindow.keysFor(from: Int, length: Int): PowerWindow =
    if (keyframes.isEmpty()) this else copy(keyframes = WINDOW_KEYS.window(keyframes, from, length, geometry))

/** This window's keyframes for a clip whose length changes from [oldFrames] to [newFrames] with its content (speed). */
fun PowerWindow.retimedKeys(oldFrames: Int, newFrames: Int): PowerWindow =
    if (keyframes.isEmpty()) this else copy(keyframes = WINDOW_KEYS.retime(keyframes, oldFrames, newFrames))

/** The clip's mask for the stretch [from] until [from] + [length] (a cut or a trim), its keyframes rebased. */
fun Clip.maskFor(from: Int, length: Int): PowerWindow? = mask?.keysFor(from, length)

/** The effect's amount (0–100) [frame] frames into its clip: its keyframes interpolated, held outside them. */
fun ClipEffect.amountAt(frame: Int): Int = if (keyframes.isEmpty()) amount else EFFECT_KEYS.valueAt(keyframes, frame, amount.toFloat()).roundToInt()

fun ClipEffect.keyframeAt(frame: Int): EffectKeyframe? = keyframes.firstOrNull { it.frame == frame }

/** The effect's keyframes with one at [frame] (an existing one, or a new one that leaves the change unchanged). */
fun ClipEffect.keyframesWithKeyAt(frame: Int): List<EffectKeyframe> =
    EFFECT_KEYS.withKeyAt(keyframes, frame, EFFECT_KEYS.valueAt(keyframes, frame, amount.toFloat()))

/** The clip's effects for the stretch [from] until [from] + [length], their keyframes rebased as [Clip.keyframesFor] does. */
fun Clip.effectsFor(from: Int, length: Int): List<ClipEffect> =
    effects.map { if (it.keyframes.isEmpty()) it else it.copy(keyframes = EFFECT_KEYS.window(it.keyframes, from, length, it.amount.toFloat())) }

/** Effects of a clip whose length changes from [oldFrames] to [newFrames] with its content (speed). */
fun List<ClipEffect>.retimedEffects(oldFrames: Int, newFrames: Int): List<ClipEffect> =
    map { if (it.keyframes.isEmpty()) it else it.copy(keyframes = EFFECT_KEYS.retime(it.keyframes, oldFrames, newFrames)) }

/** Effects held as they stand [frame] frames into the clip (a held frame). */
fun List<ClipEffect>.frozenAt(frame: Int): List<ClipEffect> =
    map { if (it.keyframes.isEmpty()) it else it.copy(amount = it.amountAt(frame), keyframes = emptyList()) }

/**
 * This clip with every keyframe — transform, volume, pan, effects, node 1's grade, the window — moved to frame [map] of its
 * frame (keyframes landing on one frame keep the first): for a clip whose stretches change length with their content
 * (a speed section's speed).
 */
fun Clip.keyframesMoved(map: (Int) -> Int): Clip = copy(
    keyframes = keyframes.perParam(transform, { TRANSFORM_KEYS.moved(it, map) }) { _, track -> PARAM_KEYS.moved(track, map) },
    volumeKeyframes = VOLUME_KEYS.moved(volumeKeyframes, map),
    panKeyframes = PARAM_KEYS.moved(panKeyframes, map),
    effects = effects.map { if (it.keyframes.isEmpty()) it else it.copy(keyframes = EFFECT_KEYS.moved(it.keyframes, map)) },
    grade = grade.copy(
        keyframes = GRADE_KEYS.moved(grade.keyframes, map),
        window = grade.window?.let { w -> if (w.keyframes.isEmpty()) w else w.copy(keyframes = WINDOW_KEYS.moved(w.keyframes, map)) },
    ),
    mask = mask?.let { w -> if (w.keyframes.isEmpty()) w else w.copy(keyframes = WINDOW_KEYS.moved(w.keyframes, map)) },
)

/** What the shared keyframe arithmetic needs to know about one kind of keyframe. */
private class KeyKind<K, V>(
    val frameOf: (K) -> Int,
    val valueOf: (K) -> V,
    val easeOf: (K) -> KeyEase,
    val make: (Int, V, KeyEase) -> K,
    val mix: (V, V, Float) -> V,
)

private val TRANSFORM_KEYS = KeyKind<TransformKeyframe, ClipTransform>(
    { it.frame }, { it.transform }, { it.ease }, { f, v, e -> TransformKeyframe(f, v, e) }, { a, b, t -> a.lerp(b, t) },
)

/** One transform parameter's keys (see [TransformKeyframe.params]). */
private val PARAM_KEYS = KeyKind<ParamKey, Float>(
    { it.frame }, { it.value }, { it.ease }, ::ParamKey, { a, b, t -> a + (b - a) * t },
)

private val VOLUME_KEYS = KeyKind<VolumeKeyframe, Float>(
    { it.frame }, { it.volumeDb }, { it.ease }, ::VolumeKeyframe, { a, b, t -> a + (b - a) * t },
)

private val WINDOW_KEYS = KeyKind<WindowKeyframe, WindowGeometry>(
    { it.frame }, { it.geometry }, { it.ease }, ::WindowKeyframe, { a, b, t -> a.lerp(b, t) },
)

private val GRADE_KEYS = KeyKind<GradeKeyframe, ColorGrade>(
    { it.frame }, { it.grade }, { it.ease }, ::GradeKeyframe, { a, b, t -> a.lerp(b, t) },
)

/** Effect amounts are whole percents: a key added between two holds the curve's value there, rounded. */
private val EFFECT_KEYS = KeyKind<EffectKeyframe, Float>(
    { it.frame }, { it.amount.toFloat() }, { it.ease }, { f, v, e -> EffectKeyframe(f, v.roundToInt().coerceIn(0, 100), e) }, { a, b, t -> a + (b - a) * t },
)

/**
 * The keyframes either side of [frame] (the same one twice before the first and after the last) and the
 * eased progress from one to the other.
 */
private fun <K, V> KeyKind<K, V>.spanAt(keys: List<K>, frame: Float): Triple<K, K, Float> {
    val first = keys.first()
    if (frame <= frameOf(first)) return Triple(first, first, 0f)
    val last = keys.last()
    if (frame >= frameOf(last)) return Triple(last, last, 0f)
    val i = keys.indexOfLast { frameOf(it) <= frame }
    val a = keys[i]
    val b = keys[i + 1]
    val t = (frame - frameOf(a)) / (frameOf(b) - frameOf(a))
    return Triple(a, b, easedProgress(t, easeOf(a).slopeOut, easeOf(b).slopeIn))
}

private fun <K, V> KeyKind<K, V>.valueAt(keys: List<K>, frame: Int, base: V): V {
    if (keys.isEmpty()) return base
    val first = keys.first()
    if (frame <= frameOf(first)) return valueOf(first)
    val last = keys.last()
    if (frame >= frameOf(last)) return valueOf(last)
    val i = keys.indexOfLast { frameOf(it) <= frame }
    val a = keys[i]
    val b = keys[i + 1]
    val t = (frame - frameOf(a)).toFloat() / (frameOf(b) - frameOf(a))
    return mix(valueOf(a), valueOf(b), easedProgress(t, easeOf(a).slopeOut, easeOf(b).slopeIn))
}

/** [keys] with one at [frame]: an existing one, or a new one holding [value] that splits the curve there exactly. */
private fun <K, V> KeyKind<K, V>.withKeyAt(keys: List<K>, frame: Int, value: V): List<K> {
    if (keys.any { frameOf(it) == frame }) return keys
    val i = keys.indexOfLast { frameOf(it) < frame }
    val a = keys.getOrNull(i)
    val b = keys.getOrNull(i + 1)
    val m0 = a?.let { easeOf(it).slopeOut.toDouble() } ?: 1.0
    val m1 = b?.let { easeOf(it).slopeIn.toDouble() } ?: 1.0
    // Before the first or after the last keyframe the value holds, and a straight line stays straight.
    if (a == null || b == null || (m0 == 1.0 && m1 == 1.0)) return (keys + make(frame, value, KeyEase.LINEAR)).sortedBy(frameOf)
    // Split the curve at u: each part is the same cubic, rescaled to run 0..1 over its own stretch.
    val u = (frame - frameOf(a)).toDouble() / (frameOf(b) - frameOf(a))
    val f = hermite(u, m0, m1)
    val df = hermiteSlope(u, m0, m1)
    val left = u / f
    val right = (1 - u) / (1 - f)
    val before = make(frameOf(a), valueOf(a), easeOf(a).copy(slopeOut = slopeOf(m0 * left)))
    val cut = make(frame, value, KeyEase(slopeIn = slopeOf(df * left), slopeOut = slopeOf(df * right)))
    val after = make(frameOf(b), valueOf(b), easeOf(b).copy(slopeIn = slopeOf(m1 * right)))
    return keys.subList(0, i) + before + cut + after + keys.subList(i + 2, keys.size)
}

private fun <K, V> KeyKind<K, V>.window(keys: List<K>, from: Int, length: Int, base: V): List<K> {
    if (keys.isEmpty() || length <= 0) return emptyList()
    val end = from + length - 1
    // Both values come from the original curve, so rounding at one cut does not carry into the other.
    val first = valueAt(keys, from, base)
    val last = valueAt(keys, end, base)
    var all = keys
    if (keys.any { frameOf(it) < from }) all = withKeyAt(all, from, first)
    if (keys.any { frameOf(it) > end }) all = withKeyAt(all, end, last)
    return all.filter { frameOf(it) in from..end }.map { make(frameOf(it) - from, valueOf(it), easeOf(it)) }
}

private fun <K, V> KeyKind<K, V>.retime(keys: List<K>, oldFrames: Int, newFrames: Int): List<K> {
    if (keys.isEmpty() || oldFrames <= 0 || oldFrames == newFrames) return keys
    return keys.map { make((frameOf(it).toLong() * newFrames / oldFrames).toInt(), valueOf(it), easeOf(it)) }.distinctBy(frameOf)
}

private fun <K, V> KeyKind<K, V>.moved(keys: List<K>, map: (Int) -> Int): List<K> =
    if (keys.isEmpty()) keys else keys.map { make(map(frameOf(it)), valueOf(it), easeOf(it)) }.distinctBy(frameOf).sortedBy(frameOf)

/** [easedProgress] in double precision, for splitting curves. */
private fun hermite(t: Double, m0: Double, m1: Double): Double {
    val t2 = t * t
    val t3 = t2 * t
    return (t3 - 2 * t2 + t) * m0 + (3 * t2 - 2 * t3) + (t3 - t2) * m1
}

/** The derivative of [hermite]. */
private fun hermiteSlope(t: Double, m0: Double, m1: Double): Double =
    (3 * t * t - 4 * t + 1) * m0 + (6 * t - 6 * t * t) + (3 * t * t - 2 * t) * m1

private fun slopeOf(value: Double): Float = value.toFloat().coerceIn(0f, KeyEase.MAX_SLOPE)
