package com.example.prismstudio.p40b43a88.data.engine

import android.opengl.GLES20
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.domain.model.OpticalFlow

/**
 * [OpticalFlow] on the GPU, for the retime stage ([ClipTimingEffect] with optical flow): luma planes of both frames at 1/4
 * and 1/16 size, the motion field between them — a coarse search, a fine one, a 3 × 3 average — worked out once per pair
 * of frames (the in-between frames of one pair share it), and each in-between frame warped along it. The shaders are
 * generated from [OpticalFlow]'s constants, step for step. A flow texel packs each component of the motion into two 8-bit
 * channels ([PACK]): 1/65025 of ±[OpticalFlow.MAX_FLOW] px.
 */
@UnstableApi
internal class OpticalFlowInterpolator {

    private val lumaFromColor = program(DOWN_COLOR)
    private val lumaFromLuma = program(DOWN_LUMA)
    private val coarse = program(COARSE)
    private val fine = program(FINE)
    private val smooth = program(SMOOTH)
    private val warp = program(WARP)

    /** Planes and fields for frames of [width] × [height]. */
    private var width = 0
    private var height = 0
    private val textures = mutableListOf<GlTextureInfo>()
    private lateinit var a4: GlTextureInfo
    private lateinit var b4: GlTextureInfo
    private lateinit var a16: GlTextureInfo
    private lateinit var b16: GlTextureInfo
    private lateinit var flow16: GlTextureInfo
    private lateinit var flow4: GlTextureInfo
    private lateinit var flowSmooth: GlTextureInfo

    /** Which two frames ([CadenceProgram]'s serials) the motion field holds. */
    private var pair: Pair<Long, Long>? = null

    /**
     * Draws the frame [toward] of the way from [a] to [b] into [target] (the frames' size). [aKey] and [bKey] name the
     * frames' contents: the motion is worked out again only for a new pair.
     */
    fun draw(provider: GlObjectsProvider, a: GlTextureInfo, aKey: Long, b: GlTextureInfo, bKey: Long, toward: Float, target: GlTextureInfo) {
        if (a.width != width || a.height != height) allocate(provider, a.width, a.height)
        if (pair != aKey to bKey) {
            estimate(a, b)
            pair = aKey to bKey
        }
        render(warp, target) {
            warp.setSamplerTexIdUniform("uA", a.texId, 0)
            warp.setSamplerTexIdUniform("uB", b.texId, 1)
            warp.setSamplerTexIdUniform("uFlow", flowSmooth.texId, 2)
            warp.setFloatsUniform("uFlowSize", floatArrayOf(flowSmooth.width.toFloat(), flowSmooth.height.toFloat()))
            warp.setFloatsUniform("uFullTexel", floatArrayOf(1f / width, 1f / height))
            warp.setFloatUniform("uToward", toward)
        }
    }

    private fun estimate(a: GlTextureInfo, b: GlTextureInfo) {
        for ((frame, quarter, sixteenth) in listOf(Triple(a, a4, a16), Triple(b, b4, b16))) {
            render(lumaFromColor, quarter) {
                lumaFromColor.setSamplerTexIdUniform("uSource", frame.texId, 0)
                lumaFromColor.setFloatsUniform("uSourceTexel", floatArrayOf(1f / frame.width, 1f / frame.height))
            }
            render(lumaFromLuma, sixteenth) {
                lumaFromLuma.setSamplerTexIdUniform("uSource", quarter.texId, 0)
                lumaFromLuma.setFloatsUniform("uSourceTexel", floatArrayOf(1f / quarter.width, 1f / quarter.height))
            }
        }
        render(coarse, flow16) {
            coarse.setSamplerTexIdUniform("uA", a16.texId, 0)
            coarse.setSamplerTexIdUniform("uB", b16.texId, 1)
            coarse.setFloatsUniform("uTexel", floatArrayOf(1f / a16.width, 1f / a16.height))
        }
        render(fine, flow4) {
            fine.setSamplerTexIdUniform("uA", a4.texId, 0)
            fine.setSamplerTexIdUniform("uB", b4.texId, 1)
            fine.setSamplerTexIdUniform("uCoarse", flow16.texId, 2)
            fine.setFloatsUniform("uTexel", floatArrayOf(1f / a4.width, 1f / a4.height))
            fine.setFloatsUniform("uCoarseSize", floatArrayOf(flow16.width.toFloat(), flow16.height.toFloat()))
        }
        render(smooth, flowSmooth) {
            smooth.setSamplerTexIdUniform("uFlow", flow4.texId, 0)
            smooth.setFloatsUniform("uTexel", floatArrayOf(1f / flow4.width, 1f / flow4.height))
        }
    }

    private fun allocate(provider: GlObjectsProvider, w: Int, h: Int) {
        releaseTextures()
        width = w
        height = h
        val fw = (w + OpticalFlow.FINE - 1) / OpticalFlow.FINE
        val fh = (h + OpticalFlow.FINE - 1) / OpticalFlow.FINE
        val ratio = OpticalFlow.COARSE / OpticalFlow.FINE
        val cw = (fw + ratio - 1) / ratio
        val ch = (fh + ratio - 1) / ratio
        fun make(tw: Int, th: Int): GlTextureInfo {
            val texId = GlUtil.createTexture(tw, th, /* useHighPrecisionColorComponents= */ false)
            return provider.createBuffersForTexture(texId, tw, th).also(textures::add)
        }
        a4 = make(fw, fh)
        b4 = make(fw, fh)
        a16 = make(cw, ch)
        b16 = make(cw, ch)
        flow16 = make(cw, ch)
        flow4 = make(fw, fh)
        flowSmooth = make(fw, fh)
        pair = null
    }

    /** Forgets the motion field (the stream was flushed: the next pair's frames may reuse the same serials' textures). */
    fun reset() {
        pair = null
    }

    fun release() {
        try {
            releaseTextures()
            listOf(lumaFromColor, lumaFromLuma, coarse, fine, smooth, warp).forEach(GlProgram::delete)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun releaseTextures() {
        textures.forEach(GlTextureInfo::release)
        textures.clear()
        width = 0
        height = 0
        pair = null
    }

    /** Draws [program] over all of [target]. */
    private inline fun render(program: GlProgram, target: GlTextureInfo, uniforms: () -> Unit) {
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        program.use()
        uniforms()
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    private companion object {
        fun program(fragmentShader: String): GlProgram {
            val program = try {
                GlProgram(VERTEX_SHADER, fragmentShader)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            return program
        }

        const val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying highp vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        /** Packing a motion (full-resolution px) into a texel and back: two 8-bit channels per component. */
        val PACK = """
const float RANGE = ${2f * OpticalFlow.MAX_FLOW};
vec4 encodeFlow(vec2 v) {
  vec2 u = clamp(v / RANGE + 0.5, 0.0, 0.99998);
  vec2 hi = floor(u * 255.0) / 255.0;
  vec2 lo = fract(u * 255.0);
  return vec4(hi.x, lo.x, hi.y, lo.y);
}
vec2 decodeFlow(vec4 c) {
  return (vec2(c.r + c.g / 255.0, c.b + c.a / 255.0) - 0.5) * RANGE;
}
"""

        /**
         * The 3 × 3 patch difference of [OpticalFlow]'s cost: displacement d (texels of the plane) about c, symmetric about
         * the halfway moment, each patch less its mean, plus the motion cost per full-resolution px.
         */
        fun patchCost(scale: Int) = """
float patchCost(vec2 c, vec2 d) {
  float pa[9];
  float pb[9];
  float ma = 0.0;
  float mb = 0.0;
  for (int i = 0; i < 9; i++) {
    vec2 k = vec2(mod(float(i), 3.0) - 1.0, floor(float(i) / 3.0) - 1.0);
    pa[i] = texture2D(uA, c + (k - d * 0.5) * uTexel).r;
    pb[i] = texture2D(uB, c + (k + d * 0.5) * uTexel).r;
    ma += pa[i];
    mb += pb[i];
  }
  ma /= 9.0;
  mb /= 9.0;
  float sum = 0.0;
  for (int i = 0; i < 9; i++) sum += abs((pa[i] - ma) - (pb[i] - mb));
  return sum + ${OpticalFlow.MOTION_COST} * (abs(d.x) + abs(d.y)) * ${scale.toFloat()};
}
"""

        // Step 1: luma at a quarter of the size — four bilinear taps, one in the middle of each 2 × 2 quarter of the 4 × 4 block.
        const val DOWN_COLOR = """#version 100
precision highp float;
uniform sampler2D uSource;
uniform vec2 uSourceTexel;
varying highp vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
void main() {
  vec2 c = vTexSamplingCoord;
  float s = luma(texture2D(uSource, c + vec2(-1.0, -1.0) * uSourceTexel).rgb)
      + luma(texture2D(uSource, c + vec2(1.0, -1.0) * uSourceTexel).rgb)
      + luma(texture2D(uSource, c + vec2(-1.0, 1.0) * uSourceTexel).rgb)
      + luma(texture2D(uSource, c + vec2(1.0, 1.0) * uSourceTexel).rgb);
  gl_FragColor = vec4(s * 0.25, 0.0, 0.0, 1.0);
}
"""

        const val DOWN_LUMA = """#version 100
precision highp float;
uniform sampler2D uSource;
uniform vec2 uSourceTexel;
varying highp vec2 vTexSamplingCoord;
void main() {
  vec2 c = vTexSamplingCoord;
  float s = texture2D(uSource, c + vec2(-1.0, -1.0) * uSourceTexel).r + texture2D(uSource, c + vec2(1.0, -1.0) * uSourceTexel).r
      + texture2D(uSource, c + vec2(-1.0, 1.0) * uSourceTexel).r + texture2D(uSource, c + vec2(1.0, 1.0) * uSourceTexel).r;
  gl_FragColor = vec4(s * 0.25, 0.0, 0.0, 1.0);
}
"""

        // Step 2: every whole cell of displacement up to COARSE_RADIUS at 1/16.
        val COARSE = """#version 100
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform vec2 uTexel;
varying highp vec2 vTexSamplingCoord;
$PACK
${patchCost(OpticalFlow.COARSE)}
void main() {
  vec2 c = vTexSamplingCoord;
  float best = patchCost(c, vec2(0.0));
  vec2 bd = vec2(0.0);
  for (int y = -${OpticalFlow.COARSE_RADIUS}; y <= ${OpticalFlow.COARSE_RADIUS}; y++) {
    for (int x = -${OpticalFlow.COARSE_RADIUS}; x <= ${OpticalFlow.COARSE_RADIUS}; x++) {
      vec2 d = vec2(float(x), float(y));
      float k = patchCost(c, d);
      if (k < best) { best = k; bd = d; }
    }
  }
  gl_FragColor = encodeFlow(bd * ${OpticalFlow.COARSE.toFloat()});
}
"""

        // Step 3: refined at 1/4 — no motion, whole cells around the coarse estimate, then half cells around the best.
        val FINE = """#version 100
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform sampler2D uCoarse;
uniform vec2 uTexel;
uniform vec2 uCoarseSize;
varying highp vec2 vTexSamplingCoord;
$PACK
${patchCost(OpticalFlow.FINE)}
void main() {
  vec2 c = vTexSamplingCoord;
  vec2 cell = (floor(c * uCoarseSize) + 0.5) / uCoarseSize;
  vec2 s = decodeFlow(texture2D(uCoarse, cell)) / ${OpticalFlow.FINE.toFloat()};
  float best = patchCost(c, vec2(0.0));
  vec2 bd = vec2(0.0);
  for (int y = -${OpticalFlow.FINE_RADIUS}; y <= ${OpticalFlow.FINE_RADIUS}; y++) {
    for (int x = -${OpticalFlow.FINE_RADIUS}; x <= ${OpticalFlow.FINE_RADIUS}; x++) {
      vec2 d = s + vec2(float(x), float(y));
      float k = patchCost(c, d);
      if (k < best) { best = k; bd = d; }
    }
  }
  vec2 w = bd;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      vec2 d = w + vec2(float(x), float(y)) * 0.5;
      float k = patchCost(c, d);
      if (k < best) { best = k; bd = d; }
    }
  }
  gl_FragColor = encodeFlow(clamp(bd * ${OpticalFlow.FINE.toFloat()}, -${OpticalFlow.MAX_FLOW}, ${OpticalFlow.MAX_FLOW}));
}
"""

        // Step 4: the motion field averaged over 3 × 3 cells (decoded first: packed channels do not average).
        val SMOOTH = """#version 100
precision highp float;
uniform sampler2D uFlow;
uniform vec2 uTexel;
varying highp vec2 vTexSamplingCoord;
$PACK
void main() {
  vec2 sum = vec2(0.0);
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) sum += decodeFlow(texture2D(uFlow, vTexSamplingCoord + vec2(float(x), float(y)) * uTexel));
  }
  gl_FragColor = encodeFlow(sum / 9.0);
}
"""

        // Step 5: the frame a uToward of the way along the motion, a cross-fade where the two warped samples disagree.
        val WARP = """#version 100
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform sampler2D uFlow;
uniform vec2 uFlowSize;
uniform vec2 uFullTexel;
uniform float uToward;
varying highp vec2 vTexSamplingCoord;
$PACK
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec2 flowAt(vec2 i) { return decodeFlow(texture2D(uFlow, (i + 0.5) / uFlowSize)); }
void main() {
  vec2 x = vTexSamplingCoord;
  // Bilinear between the four flow texels about x, each decoded first.
  vec2 p = x * uFlowSize - 0.5;
  vec2 i = floor(p);
  vec2 f = p - i;
  vec2 flow = mix(mix(flowAt(i), flowAt(i + vec2(1.0, 0.0)), f.x), mix(flowAt(i + vec2(0.0, 1.0)), flowAt(i + vec2(1.0, 1.0)), f.x), f.y);
  vec2 off = flow * uFullTexel;
  vec4 ca = texture2D(uA, x - uToward * off);
  vec4 cb = texture2D(uB, x + (1.0 - uToward) * off);
  vec4 fade = mix(texture2D(uA, x), texture2D(uB, x), uToward);
  float k = smoothstep(${OpticalFlow.AGREE}, ${OpticalFlow.DISAGREE}, abs(luma(ca.rgb) - luma(cb.rgb)));
  gl_FragColor = mix(mix(ca, cb, uToward), fade, k);
}
"""
    }
}
