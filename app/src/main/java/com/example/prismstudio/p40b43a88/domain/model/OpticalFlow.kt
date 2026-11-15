package com.example.prismstudio.p40b43a88.domain.model

import kotlin.math.abs
import kotlin.math.floor

/**
 * Resolve's Optical Flow retime: a frame the footage lacks is made by following the motion between the source frames either
 * side of it — each warped part of the way along the motion, then mixed — instead of Frame Blend's cross-fade, which shows
 * two faint copies of anything that moves.
 *
 * This is the arithmetic the GPU runs (data/engine/OpticalFlowInterpolator generates its GLSL from these constants):
 * 1. luma planes of both frames at 1/[FINE] and 1/[COARSE] of their size (box averages);
 * 2. at 1/[COARSE], every whole-cell displacement up to [COARSE_RADIUS] tried for each cell, the 3 × 3 patch around it in
 *    one frame against the other's, each less its mean (a fade is not motion) — symmetric about the halfway moment, so the
 *    motion found is the motion through that point halfway; the least difference wins, plus [MOTION_COST] per cell moved
 *    (per pixel of motion, so still or flat areas stay still);
 * 3. at 1/[FINE], that estimate refined: whole cells up to [FINE_RADIUS] around it (and no motion at all, should the coarse
 *    estimate be wrong), then half cells around the best;
 * 4. the motion field averaged over 3 × 3 of its cells;
 * 5. per pixel of the in-between frame a [t] of the way from the first frame to the second: the first frame sampled [t] of
 *    the motion back, the second (1 − [t]) of it on, mixed by [t] — and where those two samples disagree (something
 *    revealed or hidden) a cross-fade instead, fully past [DISAGREE] of a luma difference.
 */
object OpticalFlow {
    const val FINE = 4
    const val COARSE = 16

    /** Whole cells of 1/[COARSE] tried each way: ±64 px of motion between two source frames. */
    const val COARSE_RADIUS = 4

    /** Whole cells of 1/[FINE] tried each way around the coarse estimate: ±8 px. */
    const val FINE_RADIUS = 2

    /**
     * What a pixel of motion costs, in summed (mean-free) luma differences: a displacement must match clearly better than
     * none, so flat or faint areas — where any displacement matches about as well — stay still.
     */
    const val MOTION_COST = 0.0015f

    /** Luma differences of the two warped samples: up to [AGREE] the warp is trusted, from [DISAGREE] a cross-fade. */
    const val AGREE = 0.06f
    const val DISAGREE = 0.16f

    /** The largest motion (full-resolution px) a flow texel holds. */
    const val MAX_FLOW = 128f

    /** One channel of a picture: [width] × [height] values, sampled with texel centres at i + 0.5 as a GPU samples. */
    class Plane(val width: Int, val height: Int, val values: FloatArray) {
        fun at(x: Int, y: Int): Float = values[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

        /** Bilinear at [x], [y] in texels (centres at i + 0.5), clamped to the edge. */
        fun sample(x: Float, y: Float): Float {
            val px = x - 0.5f
            val py = y - 0.5f
            val x0 = floor(px).toInt()
            val y0 = floor(py).toInt()
            val fx = px - x0
            val fy = py - y0
            val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx
            val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx
            return top + (bottom - top) * fy
        }
    }

    /** A motion field at 1/[scale] of the frame: per cell the displacement (full-resolution px) from the first frame to the second. */
    class Flow(val width: Int, val height: Int, val scale: Int, val dx: FloatArray, val dy: FloatArray) {
        /** The motion at full-resolution point [x], [y] (px, centres at i + 0.5): bilinear between cells. */
        fun at(x: Float, y: Float): Pair<Float, Float> =
            Plane(width, height, dx).sample(x / scale, y / scale) to Plane(width, height, dy).sample(x / scale, y / scale)
    }

    fun luma(argb: Int): Float =
        (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f

    fun lumaPlane(frame: FramePixels): Plane = Plane(frame.width, frame.height, FloatArray(frame.pixels.size) { luma(frame.pixels[it]) })

    /** [plane] at 1/[factor] of its size (rounded up): each value the mean of the [factor] × [factor] block it covers. */
    fun downsample(plane: Plane, factor: Int): Plane {
        val w = (plane.width + factor - 1) / factor
        val h = (plane.height + factor - 1) / factor
        return Plane(w, h, FloatArray(w * h) { i ->
            val cx = i % w * factor
            val cy = i / w * factor
            var sum = 0f
            for (y in 0 until factor) for (x in 0 until factor) sum += plane.at(cx + x, cy + y)
            sum / (factor * factor)
        })
    }

    /** The motion from [a] to [b] (frames of one size): steps 1–4. */
    fun estimate(a: FramePixels, b: FramePixels): Flow {
        val la = lumaPlane(a)
        val lb = lumaPlane(b)
        val a4 = downsample(la, FINE)
        val b4 = downsample(lb, FINE)
        val a16 = downsample(a4, COARSE / FINE)
        val b16 = downsample(b4, COARSE / FINE)
        val coarse = coarse(a16, b16)
        return smooth(fine(a4, b4, coarse))
    }

    /**
     * The patch difference of displacement [dx], [dy] (texels) about centre [cx], [cy], symmetric about the halfway moment:
     * 3 × 3 samples of each frame, each patch's mean taken off first so a fade or an exposure change is not taken for motion.
     */
    private fun cost(a: Plane, b: Plane, cx: Float, cy: Float, dx: Float, dy: Float, scale: Int): Float {
        val pa = FloatArray(9)
        val pb = FloatArray(9)
        var ma = 0f
        var mb = 0f
        var k = 0
        for (ky in -1..1) for (kx in -1..1) {
            pa[k] = a.sample(cx + kx - dx / 2, cy + ky - dy / 2)
            pb[k] = b.sample(cx + kx + dx / 2, cy + ky + dy / 2)
            ma += pa[k]
            mb += pb[k]
            k++
        }
        ma /= 9f
        mb /= 9f
        var sum = 0f
        for (i in 0 until 9) sum += abs((pa[i] - ma) - (pb[i] - mb))
        return sum + MOTION_COST * (abs(dx) + abs(dy)) * scale
    }

    private fun coarse(a: Plane, b: Plane): Flow {
        val n = a.width * a.height
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        for (i in 0 until n) {
            val cx = i % a.width + 0.5f
            val cy = i / a.width + 0.5f
            var best = cost(a, b, cx, cy, 0f, 0f, COARSE)
            for (y in -COARSE_RADIUS..COARSE_RADIUS) for (x in -COARSE_RADIUS..COARSE_RADIUS) {
                val c = cost(a, b, cx, cy, x.toFloat(), y.toFloat(), COARSE)
                if (c < best) {
                    best = c
                    dx[i] = x * COARSE.toFloat()
                    dy[i] = y * COARSE.toFloat()
                }
            }
        }
        return Flow(a.width, a.height, COARSE, dx, dy)
    }

    private fun fine(a: Plane, b: Plane, coarse: Flow): Flow {
        val n = a.width * a.height
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        val ratio = COARSE / FINE
        for (i in 0 until n) {
            val col = i % a.width
            val row = i / a.width
            val cx = col + 0.5f
            val cy = row + 0.5f
            // The coarse cell this one lies in, in this plane's texels.
            val c = (row / ratio).coerceAtMost(coarse.height - 1) * coarse.width + (col / ratio).coerceAtMost(coarse.width - 1)
            val sx = coarse.dx[c] / FINE
            val sy = coarse.dy[c] / FINE
            // No motion first: a wrong coarse estimate is not forced on the fine one.
            var bx = 0f
            var by = 0f
            var best = cost(a, b, cx, cy, 0f, 0f, FINE)
            for (y in -FINE_RADIUS..FINE_RADIUS) for (x in -FINE_RADIUS..FINE_RADIUS) {
                val tx = sx + x
                val ty = sy + y
                val cc = cost(a, b, cx, cy, tx, ty, FINE)
                if (cc < best) {
                    best = cc
                    bx = tx
                    by = ty
                }
            }
            val wx = bx
            val wy = by
            for (y in -1..1) for (x in -1..1) {
                if (x == 0 && y == 0) continue
                val tx = wx + x * 0.5f
                val ty = wy + y * 0.5f
                val cc = cost(a, b, cx, cy, tx, ty, FINE)
                if (cc < best) {
                    best = cc
                    bx = tx
                    by = ty
                }
            }
            dx[i] = (bx * FINE).coerceIn(-MAX_FLOW, MAX_FLOW)
            dy[i] = (by * FINE).coerceIn(-MAX_FLOW, MAX_FLOW)
        }
        return Flow(a.width, a.height, FINE, dx, dy)
    }

    private fun smooth(flow: Flow): Flow {
        val px = Plane(flow.width, flow.height, flow.dx)
        val py = Plane(flow.width, flow.height, flow.dy)
        fun box(p: Plane, i: Int): Float {
            val x = i % flow.width
            val y = i / flow.width
            var s = 0f
            for (ky in -1..1) for (kx in -1..1) s += p.at(x + kx, y + ky)
            return s / 9f
        }
        return Flow(flow.width, flow.height, flow.scale, FloatArray(flow.dx.size) { box(px, it) }, FloatArray(flow.dy.size) { box(py, it) })
    }

    /** The in-between frame a [t] (0…1) of the way from [a] to [b] along [flow] (step 5). */
    fun interpolate(a: FramePixels, b: FramePixels, flow: Flow, t: Float): FramePixels {
        val w = a.width
        val channels = { f: FramePixels, shift: Int -> Plane(f.width, f.height, FloatArray(f.pixels.size) { ((f.pixels[it] shr shift) and 0xFF) / 255f }) }
        val ar = channels(a, 16)
        val ag = channels(a, 8)
        val ab = channels(a, 0)
        val br = channels(b, 16)
        val bg = channels(b, 8)
        val bb = channels(b, 0)
        val out = IntArray(a.pixels.size)
        for (i in out.indices) {
            val x = i % w + 0.5f
            val y = i / w + 0.5f
            val (fx, fy) = flow.at(x, y)
            val axp = x - t * fx
            val ayp = y - t * fy
            val bxp = x + (1 - t) * fx
            val byp = y + (1 - t) * fy
            val ca = floatArrayOf(ar.sample(axp, ayp), ag.sample(axp, ayp), ab.sample(axp, ayp))
            val cb = floatArrayOf(br.sample(bxp, byp), bg.sample(bxp, byp), bb.sample(bxp, byp))
            val diff = abs(lumaOf(ca) - lumaOf(cb))
            val k = smoothstep(AGREE, DISAGREE, diff)
            val px = i % w
            val py = i / w
            val fade = floatArrayOf(
                ar.at(px, py) + (br.at(px, py) - ar.at(px, py)) * t,
                ag.at(px, py) + (bg.at(px, py) - ag.at(px, py)) * t,
                ab.at(px, py) + (bb.at(px, py) - ab.at(px, py)) * t,
            )
            var argb = 0xFF shl 24
            for (c in 0..2) {
                val warped = ca[c] + (cb[c] - ca[c]) * t
                val v = warped + (fade[c] - warped) * k
                argb = argb or ((v * 255f + 0.5f).toInt().coerceIn(0, 255) shl (16 - 8 * c))
            }
            out[i] = argb
        }
        return FramePixels(a.width, a.height, out)
    }

    private fun lumaOf(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]

    fun smoothstep(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
