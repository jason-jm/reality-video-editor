package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PowerWindow
import com.example.prismstudio.p40b43a88.domain.model.WindowKeyframe
import com.example.prismstudio.p40b43a88.domain.model.WindowShape
import com.example.prismstudio.p40b43a88.domain.model.at
import com.example.prismstudio.p40b43a88.domain.model.geometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where between three evenly spaced costs [a], [b] (the least), [c] the parabola through them is lowest: −½…½ steps. */
private fun parabola(a: Double, b: Double, c: Double): Float {
    val d = a - 2 * b + c
    if (d <= 1e-9) return 0f
    return (0.5 * (a - c) / d).coerceIn(-0.5, 0.5).toFloat()
}

/**
 * Small luma pictures of consecutive frames of a file, for tracking: [width] × [height] values (0…255, row by row, top
 * row first) each, entry i being file frame [first] + i at [frameRate].
 */
class LumaFrames(val width: Int, val height: Int, val frameRate: Double, val first: Int, val frames: List<ByteArray>)

/**
 * Resolve's tracker options beyond Pan / Tilt (always followed): with [zoom] the shape's size follows the subject growing
 * or shrinking in the picture, with [rotate] its angle follows the subject turning.
 */
data class TrackMotion(val zoom: Boolean = false, val rotate: Boolean = false) {
    /** Whether more than the position is followed. */
    val shapes: Boolean get() = zoom || rotate

    companion object {
        /** What Resolve's tracker follows by default: Pan, Tilt, Zoom and Rotate. */
        val ALL = TrackMotion(zoom = true, rotate = true)
    }
}

/**
 * A tracked patch in every frame of [LumaFrames]: its centre ([x], [y], analysis pixels), its size against the frame the
 * track started in ([scale]) and how far it has turned since ([angle], radians, clockwise on the screen).
 */
class TrackPath(val x: FloatArray, val y: FloatArray, val scale: FloatArray, val angle: FloatArray)

/**
 * Resolve's window Tracker (Pan / Tilt, and Zoom / Rotate by [TrackMotion]): the picture under node 2's window (or a picture's
 * mask) at the playhead is followed through the clip, forward to its end and back to its start, and the window's position —
 * and with Zoom its size, with Rotate its angle — is keyed along the way, as few keyframes as keep it within [TOLERANCE]
 * ([SIZE_TOLERANCE], [ANGLE_TOLERANCE]) of the tracked path. Aspect and softness stay as they are.
 */
class WindowTrackUseCase {

    /** Why [clip]'s window cannot be tracked, or null when it can: a shaped window on footage played forward. */
    fun unavailable(clip: Clip, asset: MediaAsset?): String? {
        val window = clip.grade.window
        return when {
            clip.kind != ClipKind.VIDEO || asset?.kind != MediaKind.VIDEO -> "只有视频片段可以跟踪窗口"
            clip.reverse -> "倒放的片段不能跟踪窗口"
            window == null -> "先画一个窗口，再跟踪"
            window.shape == WindowShape.FULL -> "全画面不需要跟踪：先选择圆形、矩形或渐变"
            else -> null
        }
    }

    /**
     * Why [clip]'s mask (the Edit inspector's, rev 163) cannot be tracked, or null when it can. A mask is a window in the
     * same picture coordinates, so it is tracked the same way ([keyframes]).
     */
    fun maskUnavailable(clip: Clip, asset: MediaAsset?): String? = when {
        clip.kind != ClipKind.VIDEO || asset?.kind != MediaKind.VIDEO -> "只有视频片段可以跟踪遮罩"
        clip.reverse -> "倒放的片段不能跟踪遮罩"
        clip.mask == null -> "先为片段选择遮罩形状"
        clip.durationFrames > MAX_FRAMES -> "片段太长：先切短再跟踪"
        else -> null
    }

    /**
     * The window's keyframes once its picture is tracked through [frames] (the part of the file [clip] shows), starting
     * from where the window stands [startFrame] frames into the clip, at [fps] timeline frames a second; [motion] says
     * whether its size and angle follow the subject too.
     */
    fun keyframes(
        window: PowerWindow,
        clip: Clip,
        startFrame: Int,
        frames: LumaFrames,
        fps: Int,
        motion: TrackMotion = TrackMotion(),
        onProgress: (Float) -> Unit = {},
    ): List<WindowKeyframe> {
        val n = frames.frames.size
        require(n > 0) { "no frames" }
        val start = startFrame.coerceIn(0, clip.durationFrames - 1)
        val here = window.at(start)
        val fileIndex = { f: Int -> (((clip.sourceInMs + clip.sourceOffsetMs(f, fps)) / 1000 * frames.frameRate).roundToInt() - frames.first).coerceIn(0, n - 1) }
        // The patch tracked: the middle of the window, in analysis pixels.
        val (x, y) = toPixels(here.centerX, here.centerY, frames)
        val halfH = (here.size * frames.height / 2f * PATCH_SHARE).roundToInt().coerceIn(MIN_HALF, MAX_HALF)
        val halfW = (here.size * here.aspect * frames.height / 2f * PATCH_SHARE).roundToInt().coerceIn(MIN_HALF, MAX_HALF)
        val path = trackMotion(frames, fileIndex(start), x, y, halfW, halfH, motion, onProgress)
        // The window's centre at every clip frame, then as few keyframes as follow it.
        val d = clip.durationFrames
        val cx = FloatArray(d)
        val cy = FloatArray(d)
        val (sx, sy) = toWindow(x, y, frames)
        for (f in 0 until d) {
            val i = fileIndex(f)
            val (wx, wy) = toWindow(path.x[i], path.y[i], frames)
            // The tracked patch moved by this much from where it was at the start; the window moves with it.
            cx[f] = here.centerX + (wx - sx)
            cy[f] = here.centerY + (wy - sy)
        }
        // A light 1-2-1 smoothing takes the tracking's sub-pixel jitter out before keying; size and angle, noisier, get a
        // wider one. Changes smaller than the tracking's noise ([ZOOM_FLOOR], [ROTATE_FLOOR]) leave them as they were.
        val sx2 = smooth3(cx)
        val sy2 = smooth3(cy)
        val sizes = if (!motion.zoom) null else {
            settled(smooth5(FloatArray(d) { path.scale[fileIndex(it)] }), 1f, ZOOM_FLOOR)?.let { s -> FloatArray(d) { here.size * s[it] } }
        }
        val angles = if (!motion.rotate) null else {
            settled(smooth5(FloatArray(d) { path.angle[fileIndex(it)] }), 0f, ROTATE_FLOOR)?.let { t ->
                // Clockwise on the screen (the analysis picture's y runs down) is a smaller window angle.
                FloatArray(d) { here.angle - Math.toDegrees(t[it].toDouble()).toFloat() }
            }
        }
        return simplify(sx2, sy2, TOLERANCE, sizes, angles).map { f ->
            val g = window.at(f).geometry
            WindowKeyframe(f, g.copy(centerX = sx2[f], centerY = sy2[f], size = sizes?.get(f) ?: g.size, angle = angles?.get(f) ?: g.angle))
        }
    }

    /**
     * Where the patch around ([x], [y]) (half [halfW] × [halfH] pixels) in frame [start] lies in every frame of [frames],
     * and — as [motion] asks — how much it grew and turned. Position alone is [track]. With Zoom or Rotate each step first
     * finds the patch where its last move predicts it, at its last size and angle; then the size and angle that match
     * best there ([ZOOM_STEP], [ROTATE_STEP] apart, refined between); then the position once more, close by. The patch's
     * look is kept in its own axes — sampled from each frame through the tracked size and angle — so it compares with
     * the subject as first seen however it has turned.
     */
    fun trackMotion(frames: LumaFrames, start: Int, x: Float, y: Float, halfW: Int, halfH: Int, motion: TrackMotion, onProgress: (Float) -> Unit = {}): TrackPath {
        val n = frames.frames.size
        if (!motion.shapes) {
            val p = track(frames, start, x, y, halfW, halfH, onProgress)
            return TrackPath(FloatArray(n) { p[2 * it] }, FloatArray(n) { p[2 * it + 1] }, FloatArray(n) { 1f }, FloatArray(n))
        }
        val out = TrackPath(FloatArray(n), FloatArray(n), FloatArray(n) { 1f }, FloatArray(n))
        out.x[start] = x
        out.y[start] = y
        val grid = Grid(halfW, halfH, frames.width, frames.height)
        val first = grid.sample(frames.frames[start], x, y, 1f, 0f)
        var done = 0
        for (direction in intArrayOf(1, -1)) {
            var px = x
            var py = y
            var vx = 0f
            var vy = 0f
            var scale = 1f
            var angle = 0f
            var grow = 0f
            var turn = 0f
            val running = first.copyOf()
            val look = FloatArray(first.size)
            var i = start + direction
            while (i in 0 until n) {
                for (k in look.indices) look[k] = (first[k] + running[k]) / 2f
                val f = frames.frames[i]
                // Half the last change of size and angle is expected again (a steady zoom or spin is found sooner).
                val guessScale = scale + grow * 0.5f
                val guessAngle = angle + turn * 0.5f
                val (gx, gy) = grid.place(f, look, px + vx, py + vy, guessScale, guessAngle, SEARCH)
                val (ns, na) = grid.shape(f, look, gx, gy, guessScale, guessAngle, motion)
                val (nx, ny) = grid.place(f, look, gx, gy, ns, na, 1)
                vx = nx - px
                vy = ny - py
                grow = ns - scale
                turn = na - angle
                px = nx
                py = ny
                scale = ns
                angle = na
                out.x[i] = px
                out.y[i] = py
                out.scale[i] = scale
                out.angle[i] = angle
                val seen = grid.sample(f, px, py, scale, angle)
                for (k in running.indices) running[k] = running[k] * (1 - LEARN) + seen[k] * LEARN
                i += direction
                done++
                if (done % PROGRESS_EVERY == 0) onProgress(done.toFloat() / max(1, n - 1))
            }
        }
        return out
    }

    /**
     * The pixels of the ellipse inscribed in a (2·[halfW]+1) × (2·[halfH]+1) patch, as offsets from its centre, sampled
     * from a [width] × [height] frame through a size and an angle.
     */
    private class Grid(halfW: Int, halfH: Int, private val width: Int, private val height: Int) {
        private val dx: FloatArray
        private val dy: FloatArray

        init {
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            for (oy in -halfH..halfH) for (ox in -halfW..halfW) {
                val u = ox / (halfW + 0.5f)
                val v = oy / (halfH + 0.5f)
                if (u * u + v * v <= 1f) { xs.add(ox.toFloat()); ys.add(oy.toFloat()) }
            }
            dx = xs.toFloatArray()
            dy = ys.toFloatArray()
        }

        private val count get() = dx.size

        private fun value(f: ByteArray, x: Float, y: Float): Float {
            val sx = x.coerceIn(0f, (width - 1).toFloat())
            val sy = y.coerceIn(0f, (height - 1).toFloat())
            val x0 = floor(sx).toInt().coerceIn(0, width - 2)
            val y0 = floor(sy).toInt().coerceIn(0, height - 2)
            return lerp4(f, x0, y0, sx - x0, sy - y0)
        }

        private fun lerp4(f: ByteArray, x0: Int, y0: Int, tx: Float, ty: Float): Float {
            val r = y0 * width + x0
            val a = (f[r].toInt() and 0xFF).toFloat()
            val b = (f[r + 1].toInt() and 0xFF).toFloat()
            val c = (f[r + width].toInt() and 0xFF).toFloat()
            val e = (f[r + width + 1].toInt() and 0xFF).toFloat()
            return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + e * tx) * ty
        }

        /** The patch's pixels in frame [f], centred on ([x], [y]), [scale] times as large and turned by [angle]. */
        fun sample(f: ByteArray, x: Float, y: Float, scale: Float, angle: Float): FloatArray {
            val c = cos(angle) * scale
            val s = sin(angle) * scale
            return FloatArray(count) { k -> value(f, x + c * dx[k] - s * dy[k], y + s * dx[k] + c * dy[k]) }
        }

        /** The squared difference between [look] and the patch sampled as [sample] does. */
        fun cost(f: ByteArray, look: FloatArray, x: Float, y: Float, scale: Float, angle: Float): Double {
            val c = cos(angle) * scale
            val s = sin(angle) * scale
            var acc = 0.0
            for (k in 0 until count) {
                val d = value(f, x + c * dx[k] - s * dy[k], y + s * dx[k] + c * dy[k]) - look[k]
                acc += d * d
            }
            return acc
        }

        /**
         * The best centre for [look] within [radius] whole pixels of ([gx], [gy]) at [scale] and [angle], refined to a
         * fraction of a pixel. Every candidate shares the sampling points' fractions, so they are worked out once.
         */
        fun place(f: ByteArray, look: FloatArray, gx: Float, gy: Float, scale: Float, angle: Float, radius: Int): Pair<Float, Float> {
            val c = cos(angle) * scale
            val s = sin(angle) * scale
            val ix = IntArray(count)
            val iy = IntArray(count)
            val tx = FloatArray(count)
            val ty = FloatArray(count)
            for (k in 0 until count) {
                val fx = gx + c * dx[k] - s * dy[k]
                val fy = gy + s * dx[k] + c * dy[k]
                ix[k] = floor(fx).toInt()
                iy[k] = floor(fy).toInt()
                tx[k] = fx - ix[k]
                ty[k] = fy - iy[k]
            }
            fun cost(ox: Int, oy: Int): Double {
                var acc = 0.0
                for (k in 0 until count) {
                    val x0 = (ix[k] + ox).coerceIn(0, width - 2)
                    val y0 = (iy[k] + oy).coerceIn(0, height - 2)
                    val d = lerp4(f, x0, y0, tx[k], ty[k]) - look[k]
                    acc += d * d
                }
                return acc
            }
            var best = Double.MAX_VALUE
            var bx = 0
            var by = 0
            for (oy in -radius..radius) for (ox in -radius..radius) {
                val v = cost(ox, oy)
                if (v < best) { best = v; bx = ox; by = oy }
            }
            val fx = parabola(cost(bx - 1, by), best, cost(bx + 1, by))
            val fy = parabola(cost(bx, by - 1), best, cost(bx, by + 1))
            return (gx + bx + fx).coerceIn(0f, (width - 1).toFloat()) to (gy + by + fy).coerceIn(0f, (height - 1).toFloat())
        }

        /**
         * The size and angle (those [motion] follows) that best match [look] at ([x], [y]), from [scale] and [angle]: a
         * 5 × 5 search [ZOOM_STEP] / [ROTATE_STEP] apart, moved along while the best sits on its edge, then refined.
         */
        fun shape(f: ByteArray, look: FloatArray, x: Float, y: Float, scale: Float, angle: Float, motion: TrackMotion): Pair<Float, Float> {
            val zooms = if (motion.zoom) -2..2 else 0..0
            val turns = if (motion.rotate) -2..2 else 0..0
            var s = scale
            var a = angle
            var best = cost(f, look, x, y, s, a)
            for (round in 0 until SHAPE_ROUNDS) {
                var bi = 0
                var bj = 0
                for (i in zooms) for (j in turns) {
                    if (i == 0 && j == 0) continue
                    val v = cost(f, look, x, y, s * (1 + ZOOM_STEP * i), a + ROTATE_STEP * j)
                    if (v < best) { best = v; bi = i; bj = j }
                }
                s *= 1 + ZOOM_STEP * bi
                a += ROTATE_STEP * bj
                if (abs(bi) < 2 && abs(bj) < 2) break
            }
            if (motion.zoom) {
                s *= 1 + ZOOM_STEP * parabola(cost(f, look, x, y, s * (1 - ZOOM_STEP), a), best, cost(f, look, x, y, s * (1 + ZOOM_STEP), a))
            }
            if (motion.rotate) {
                a += ROTATE_STEP * parabola(cost(f, look, x, y, s, a - ROTATE_STEP), cost(f, look, x, y, s, a), cost(f, look, x, y, s, a + ROTATE_STEP))
            }
            return s.coerceIn(MIN_SCALE, MAX_SCALE) to a
        }
    }

    /**
     * Where the patch around ([x], [y]) (half [halfW] × [halfH] pixels) in frame [start] lies in every frame of [frames]:
     * x, y pairs in pixels. Only the ellipse inscribed in the patch is compared (its corners would hold background, which
     * changes as the subject moves). Each step searches around where the patch's last move predicts it, for the least
     * squared difference to the patch's look (refined to a fraction of a pixel). That look is half the patch as first
     * seen — so the track cannot drift off it — and half a running copy that takes on a little of each new frame, so a
     * subject that slowly turns or changes light is still found.
     */
    fun track(frames: LumaFrames, start: Int, x: Float, y: Float, halfW: Int, halfH: Int, onProgress: (Float) -> Unit = {}): FloatArray {
        val n = frames.frames.size
        val out = FloatArray(2 * n)
        out[2 * start] = x
        out[2 * start + 1] = y
        val mask = ellipse(halfW, halfH)
        val first = patch(frames, start, x, y, halfW, halfH)
        var done = 0
        for (direction in intArrayOf(1, -1)) {
            var px = x
            var py = y
            var vx = 0f
            var vy = 0f
            val running = first.copyOf()
            val look = FloatArray(first.size)
            var i = start + direction
            while (i in 0 until n) {
                for (k in look.indices) look[k] = (first[k] + running[k]) / 2f
                val (nx, ny) = step(frames, i, look, mask, px + vx, py + vy, halfW, halfH)
                vx = nx - px
                vy = ny - py
                px = nx
                py = ny
                out[2 * i] = px
                out[2 * i + 1] = py
                val seen = patch(frames, i, px, py, halfW, halfH)
                for (k in running.indices) running[k] = running[k] * (1 - LEARN) + seen[k] * LEARN
                i += direction
                done++
                if (done % PROGRESS_EVERY == 0) onProgress(done.toFloat() / max(1, n - 1))
            }
        }
        return out
    }

    /** 1 for the patch pixels inside the ellipse inscribed in it, 0 outside. */
    private fun ellipse(halfW: Int, halfH: Int): BooleanArray {
        val out = BooleanArray((2 * halfW + 1) * (2 * halfH + 1))
        var k = 0
        for (dy in -halfH..halfH) for (dx in -halfW..halfW) {
            val u = dx / (halfW + 0.5f)
            val v = dy / (halfH + 0.5f)
            out[k++] = u * u + v * v <= 1f
        }
        return out
    }

    /** The best place for [template] (its [mask]ed pixels) in frame [index] within [SEARCH] pixels of ([gx], [gy]). */
    private fun step(frames: LumaFrames, index: Int, template: FloatArray, mask: BooleanArray, gx: Float, gy: Float, halfW: Int, halfH: Int): Pair<Float, Float> {
        val f = frames.frames[index]
        val w = frames.width
        val h = frames.height
        val cx = gx.roundToInt().coerceIn(halfW, w - 1 - halfW)
        val cy = gy.roundToInt().coerceIn(halfH, h - 1 - halfH)
        fun cost(ox: Int, oy: Int): Double {
            val bx = (cx + ox).coerceIn(halfW, w - 1 - halfW)
            val by = (cy + oy).coerceIn(halfH, h - 1 - halfH)
            var acc = 0.0
            var k = 0
            for (dy in -halfH..halfH) {
                val row = (by + dy) * w
                for (dx in -halfW..halfW) {
                    if (mask[k]) {
                        val d = (f[row + bx + dx].toInt() and 0xFF) - template[k]
                        acc += d * d
                    }
                    k++
                }
            }
            return acc
        }
        var best = Double.MAX_VALUE
        var bx = 0
        var by = 0
        for (oy in -SEARCH..SEARCH) for (ox in -SEARCH..SEARCH) {
            val c = cost(ox, oy)
            if (c < best) { best = c; bx = ox; by = oy }
        }
        val fx = parabola(cost(bx - 1, by), best, cost(bx + 1, by))
        val fy = parabola(cost(bx, by - 1), best, cost(bx, by + 1))
        val x = (cx + bx + fx).coerceIn(halfW.toFloat(), (w - 1 - halfW).toFloat())
        val y = (cy + by + fy).coerceIn(halfH.toFloat(), (h - 1 - halfH).toFloat())
        return x to y
    }

    /** The (2·[halfW]+1) × (2·[halfH]+1) patch of frame [index] around ([x], [y]), sampled between pixels. */
    private fun patch(frames: LumaFrames, index: Int, x: Float, y: Float, halfW: Int, halfH: Int): FloatArray {
        val f = frames.frames[index]
        val w = frames.width
        val h = frames.height
        val out = FloatArray((2 * halfW + 1) * (2 * halfH + 1))
        var k = 0
        for (dy in -halfH..halfH) for (dx in -halfW..halfW) {
            val sx = (x + dx).coerceIn(0f, (w - 1).toFloat())
            val sy = (y + dy).coerceIn(0f, (h - 1).toFloat())
            val x0 = floor(sx).toInt().coerceAtMost(w - 2).coerceAtLeast(0)
            val y0 = floor(sy).toInt().coerceAtMost(h - 2).coerceAtLeast(0)
            val tx = sx - x0
            val ty = sy - y0
            fun p(a: Int, b: Int) = (f[b * w + a].toInt() and 0xFF).toFloat()
            out[k++] = (p(x0, y0) * (1 - tx) + p(x0 + 1, y0) * tx) * (1 - ty) + (p(x0, y0 + 1) * (1 - tx) + p(x0 + 1, y0 + 1) * tx) * ty
        }
        return out
    }

    /**
     * The clip frames to key so that straight lines between them stay within [tolerance] of the path ([cx], [cy]) — and,
     * when given, within [SIZE_TOLERANCE] of the [sizes] and [ANGLE_TOLERANCE] of the [angles] (degrees) — Douglas–Peucker
     * over the frames; the first and last always.
     */
    fun simplify(cx: FloatArray, cy: FloatArray, tolerance: Float = TOLERANCE, sizes: FloatArray? = null, angles: FloatArray? = null): List<Int> {
        val n = cx.size
        if (n <= 2) return (0 until n).toList()
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        fun off(v: FloatArray, i: Int, a: Int, b: Int, t: Float) = abs(v[i] - (v[a] + (v[b] - v[a]) * t))
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to n - 1) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            // The worst frame between, as a share of what each channel may stray (over 1: keyed).
            var worst = -1f
            var at = -1
            for (i in a + 1 until b) {
                val t = (i - a).toFloat() / (b - a)
                var d = hypot(off(cx, i, a, b, t), off(cy, i, a, b, t)) / tolerance
                if (sizes != null) d = max(d, off(sizes, i, a, b, t) / SIZE_TOLERANCE)
                if (angles != null) d = max(d, off(angles, i, a, b, t) / ANGLE_TOLERANCE)
                if (d > worst) { worst = d; at = i }
            }
            if (worst > 1f && at > 0) {
                keep[at] = true
                stack.add(a to at)
                stack.add(at to b)
            }
        }
        return (0 until n).filter { keep[it] }
    }

    /** [v] with each inner value averaged 1-2-1 with its neighbours (the ends kept). */
    private fun smooth3(v: FloatArray): FloatArray =
        FloatArray(v.size) { i -> if (i == 0 || i == v.size - 1) v[i] else (v[i - 1] + 2 * v[i] + v[i + 1]) / 4f }

    /** [v] averaged 1-4-6-4-1 over five values (fewer at the ends, the ends kept). */
    private fun smooth5(v: FloatArray): FloatArray = FloatArray(v.size) { i ->
        if (i < 2 || i > v.size - 3) smooth3(v)[i] else (v[i - 2] + 4 * v[i - 1] + 6 * v[i] + 4 * v[i + 1] + v[i + 2]) / 16f
    }

    /** [v], or null when it never strays [floor] or more from [rest] (a change the tracking's noise could make). */
    private fun settled(v: FloatArray, rest: Float, floor: Float): FloatArray? = if (v.all { abs(it - rest) < floor }) null else v

    /** A window centre (−1…1, up positive) in analysis pixels (down positive). */
    fun toPixels(centerX: Float, centerY: Float, frames: LumaFrames): Pair<Float, Float> =
        (centerX + 1f) / 2f * (frames.width - 1) to (1f - centerY) / 2f * (frames.height - 1)

    /** Analysis pixels as a window centre. */
    fun toWindow(x: Float, y: Float, frames: LumaFrames): Pair<Float, Float> =
        x / (frames.width - 1) * 2f - 1f to 1f - y / (frames.height - 1) * 2f

    /**
     * What else the keys do, for the toast after "N 个关键帧，最多移动画面的 M%": how much the shape grows or shrinks
     * (largest size over smallest) and turns, when [motion] follows that and it changed.
     */
    fun motionNote(keys: List<WindowKeyframe>, motion: TrackMotion): String {
        if (keys.isEmpty()) return ""
        val sizes = keys.map { it.geometry.size }
        val angles = keys.map { it.geometry.angle }
        val grew = ((sizes.max() / sizes.min().coerceAtLeast(1e-4f) - 1f) * 100).roundToInt()
        val turned = (angles.max() - angles.min()).roundToInt()
        return buildString {
            if (motion.zoom && grew > 0) append("，大小最多变化 $grew%")
            if (motion.rotate && turned > 0) append("，最多转动 $turned°")
        }
    }

    /** How far, at most, the window moves across the clip once tracked (shares of the picture's width), for the toast. */
    fun travel(keys: List<WindowKeyframe>): Float {
        if (keys.isEmpty()) return 0f
        val xs = keys.map { it.geometry.centerX }
        val ys = keys.map { it.geometry.centerY }
        return max(abs(xs.max() - xs.min()), abs(ys.max() - ys.min())) / 2f
    }

    companion object {
        /** The tracking picture's width (its height keeps the file's shape). */
        const val WIDTH = 320

        /** Pixels a patch may move between two frames beyond where its last move predicts. */
        const val SEARCH = 8

        /** How much of each new frame's look the running copy of the patch takes on. */
        const val LEARN = 0.15f

        /** The share of the window's extent tracked (its middle: the edge often shows background). */
        const val PATCH_SHARE = 0.6f
        const val MIN_HALF = 6
        const val MAX_HALF = 40

        /** How far (window units, 2 = the picture's height: 0.008 is 0.4 % of it) the keyed path may stray from the tracked one. */
        const val TOLERANCE = 0.008f

        private const val PROGRESS_EVERY = 10

        /** Clips longer than this are not tracked in one go (their frames are held in memory). */
        const val MAX_FRAMES = 5_400

        /** Zoom: sizes tried a step apart (2 %), and how far one frame may go from the last ([SHAPE_ROUNDS] steps of two). */
        const val ZOOM_STEP = 0.02f

        /** Rotate: angles tried a step apart (2°, in radians). */
        const val ROTATE_STEP = (2.0 * Math.PI / 180.0).toFloat()
        const val SHAPE_ROUNDS = 3
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 4f

        /** Smaller changes over the whole clip than these (4 % in size, 1.5°) are the tracking's noise: nothing is keyed. */
        const val ZOOM_FLOOR = 0.04f
        const val ROTATE_FLOOR = (1.5 * Math.PI / 180.0).toFloat()

        /** How far the keyed size may stray from the tracked one (picture half-heights) and the keyed angle (degrees). */
        const val SIZE_TOLERANCE = 0.006f
        const val ANGLE_TOLERANCE = 0.75f
    }
}
