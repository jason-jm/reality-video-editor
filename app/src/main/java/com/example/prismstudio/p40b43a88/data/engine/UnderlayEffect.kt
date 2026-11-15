package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import java.nio.ByteBuffer

/**
 * Draws a layer's picture (straight alpha) over the picture of the layers beneath it — [underlayPath],
 * an earlier pass of a stacked render (see RenderPlan.stackingPasses) — and outputs it opaque. Where
 * [composites] put a clip in another composite mode, its colour first blends with what is beneath
 * (CompositeMode.blend, mirrored in the shader by ordinal).
 *
 * The underlay is decoded on the CPU ([YuvVideoReader]) and uploaded as plain textures: a second
 * decoder surface in the render's GL context is exactly what stacking avoids, since two of them in one
 * context let frames cross between layers. Frames are matched by timestamp, within half a frame of
 * [frameDurationUs].
 */
@UnstableApi
class UnderlayEffect(
    private val underlayPath: String,
    private val frameDurationUs: Long,
    private val composites: List<CompositeSpan> = emptyList(),
) : GlEffect {

    /** A stretch of the layer (µs of the pass) whose clip composites in [mode] instead of normal. */
    data class CompositeSpan(val startUs: Long, val endUs: Long, val mode: CompositeMode)

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        Program(underlayPath, frameDurationUs / 2, composites)

    private class Program(path: String, private val toleranceUs: Long, private val composites: List<CompositeSpan>) :
        BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

        private val reader: YuvVideoReader = try {
            YuvVideoReader(path)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
        private val program: GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: GlUtil.GlException) {
            reader.close()
            throw VideoFrameProcessingException(e)
        }

        // Y, Cb and Cr textures, made with the first frame.
        private val planes = intArrayOf(-1, -1, -1)
        private var planeWidth = 0
        private var planeHeight = 0
        private var uploadedSerial = -1L

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val frame = reader.frameAt(presentationTimeUs, toleranceUs)
                if (frame != null && frame.serial != uploadedSerial) {
                    upload(frame)
                    uploadedSerial = frame.serial
                }
                val under = frame != null
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // GlProgram needs every sampler bound; the input stands in until a frame arrives.
                program.setSamplerTexIdUniform("uY", if (under) planes[0] else inputTexId, 1)
                program.setSamplerTexIdUniform("uU", if (under) planes[1] else inputTexId, 2)
                program.setSamplerTexIdUniform("uV", if (under) planes[2] else inputTexId, 3)
                program.setFloatsUniform("uHasUnder", floatArrayOf(if (under) 1f else 0f))
                program.setFloatsUniform("uYuvToRgb", reader.colors.matrix)
                program.setFloatsUniform("uYuvOffset", reader.colors.offset)
                val mode = composites.firstOrNull { presentationTimeUs >= it.startUs && presentationTimeUs < it.endUs }?.mode
                program.setFloatsUniform("uMode", floatArrayOf((mode ?: CompositeMode.NORMAL).ordinal.toFloat()))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        private fun upload(frame: YuvVideoReader.Frame) {
            val resized = frame.width != planeWidth || frame.height != planeHeight
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            uploadPlane(0, frame.width, frame.height, frame.y, resized)
            uploadPlane(1, frame.chromaWidth, frame.chromaHeight, frame.u, resized)
            uploadPlane(2, frame.chromaWidth, frame.chromaHeight, frame.v, resized)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
            planeWidth = frame.width
            planeHeight = frame.height
        }

        private fun uploadPlane(index: Int, width: Int, height: Int, data: ByteArray, resized: Boolean) {
            if (planes[index] == -1) planes[index] = GlUtil.generateTexture()
            GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, planes[index], GLES20.GL_LINEAR)
            val pixels = ByteBuffer.wrap(data, 0, width * height)
            if (resized) {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, pixels)
            } else {
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, pixels)
            }
            GlUtil.checkGlError()
        }

        override fun release() {
            super.release()
            try {
                planes.filter { it != -1 }.forEach(GlUtil::deleteTexture)
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            } finally {
                reader.close()
            }
        }
    }

    private companion object {
        const val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        // Frame textures keep GL's bottom-up rows; the decoded planes are top-down.
        const val FRAGMENT_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uY;
uniform sampler2D uU;
uniform sampler2D uV;
uniform float uHasUnder;
uniform mat3 uYuvToRgb;
uniform vec3 uYuvOffset;
// CompositeMode by ordinal: normal, darken, multiply, lighten, screen, add, overlay, soft light, difference, colour dodge,
// colour burn, hard light, exclusion, hue, saturation, colour, luminosity (the W3C compositing formulas).
uniform float uMode;
varying vec2 vTexSamplingCoord;
float softLightD(float b) {
  return b <= 0.25 ? ((16.0 * b - 12.0) * b + 4.0) * b : sqrt(b);
}
float dodge(float b, float s) {
  return b <= 0.0 ? 0.0 : (s >= 1.0 ? 1.0 : min(1.0, b / (1.0 - s)));
}
float burn(float b, float s) {
  return b >= 1.0 ? 1.0 : (s <= 0.0 ? 0.0 : 1.0 - min(1.0, (1.0 - b) / s));
}
vec3 hardLight(vec3 b, vec3 s) {
  vec3 s2 = 2.0 * s - 1.0;
  return mix(2.0 * b * s, b + s2 - b * s2, step(0.5, s));
}
float lum(vec3 c) {
  return dot(c, vec3(0.3, 0.59, 0.11));
}
vec3 clipColor(vec3 c) {
  float l = lum(c);
  float n = min(min(c.r, c.g), c.b);
  float x = max(max(c.r, c.g), c.b);
  if (n < 0.0) c = l + (c - l) * l / max(l - n, 1e-6);
  if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 1e-6);
  return c;
}
vec3 setLum(vec3 c, float l) {
  return clipColor(c + (l - lum(c)));
}
float sat(vec3 c) {
  return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
}
// A colour less than a code value from grey counts as grey: W3C's mx > mn would blow decoding noise up to full colour.
vec3 setSat(vec3 c, float s) {
  float mx = max(max(c.r, c.g), c.b);
  float mn = min(min(c.r, c.g), c.b);
  return mx - mn > 1.0 / 255.0 ? (c - mn) * s / (mx - mn) : vec3(0.0);
}
vec3 blendColor(vec3 b, vec3 s) {
  if (uMode < 0.5) return s;
  if (uMode < 1.5) return min(b, s);
  if (uMode < 2.5) return b * s;
  if (uMode < 3.5) return max(b, s);
  if (uMode < 4.5) return b + s - b * s;
  if (uMode < 5.5) return min(b + s, vec3(1.0));
  if (uMode < 6.5) return hardLight(s, b);
  if (uMode < 7.5) {
    vec3 d = vec3(softLightD(b.r), softLightD(b.g), softLightD(b.b));
    return mix(b - (1.0 - 2.0 * s) * b * (1.0 - b), b + (2.0 * s - 1.0) * (d - b), step(0.5, s));
  }
  if (uMode < 8.5) return abs(b - s);
  if (uMode < 9.5) return vec3(dodge(b.r, s.r), dodge(b.g, s.g), dodge(b.b, s.b));
  if (uMode < 10.5) return vec3(burn(b.r, s.r), burn(b.g, s.g), burn(b.b, s.b));
  if (uMode < 11.5) return hardLight(b, s);
  if (uMode < 12.5) return b + s - 2.0 * b * s;
  if (uMode < 13.5) return setLum(setSat(s, sat(b)), lum(b));
  if (uMode < 14.5) return setLum(setSat(b, sat(s)), lum(b));
  if (uMode < 15.5) return setLum(s, lum(b));
  return setLum(b, lum(s));
}
void main() {
  vec4 top = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 under = vec3(0.0);
  if (uHasUnder > 0.5) {
    vec2 uv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
    vec3 yuv = vec3(texture2D(uY, uv).r, texture2D(uU, uv).r, texture2D(uV, uv).r) - uYuvOffset;
    under = clamp(uYuvToRgb * yuv, 0.0, 1.0);
  }
  gl_FragColor = vec4(mix(under, blendColor(under, top.rgb), top.a), 1.0);
}
"""
    }
}
