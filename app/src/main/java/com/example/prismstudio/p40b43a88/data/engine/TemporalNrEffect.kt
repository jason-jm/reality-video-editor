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
import com.example.prismstudio.p40b43a88.domain.model.TemporalNrKernel

/**
 * Resolve's Temporal NR for one clip ([TemporalNrKernel]): each frame blended towards the result of the frame before —
 * which this pass keeps in a texture of its own — where the local mean luma difference says nothing moved. Its strength is
 * read live from the [registry]; a seek (flush), a step back in time or a jump of more than two frames starts afresh, so a
 * paused frame or a cut into the clip never shows another moment's picture.
 */
@UnstableApi
class TemporalNrEffect(private val clipId: String, private val registry: LiveLookRegistry) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(clipId, registry)

    private class Program(private val clipId: String, private val registry: LiveLookRegistry) :
        BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

        private val program: GlProgram
        private var width = 1
        private var height = 1
        /** The previous frame's result, copied from the output after each draw; 0 until the size is known. */
        private var previous = 0
        private var hasPrevious = false
        private var lastUs = Long.MIN_VALUE

        init {
            try {
                program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val w = inputWidth.coerceAtLeast(1)
            val h = inputHeight.coerceAtLeast(1)
            if (w != width || h != height || previous == 0) {
                try {
                    if (previous != 0) GlUtil.deleteTexture(previous)
                    previous = GlUtil.createTexture(w, h, /* useHighPrecisionColorComponents= */ false)
                } catch (e: GlUtil.GlException) {
                    throw VideoFrameProcessingException(e)
                }
                hasPrevious = false
            }
            width = w
            height = h
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val uniforms = registry.videoLook(clipId)?.temporalNr
                val frameUs = 1_000_000L / registry.frameRate.coerceAtLeast(1)
                // Only the next frame (or one dropped) follows on: anything else is another moment.
                val follows = hasPrevious && presentationTimeUs > lastUs && presentationTimeUs - lastUs <= frameUs * 5 / 2
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setSamplerTexIdUniform("uPrevious", if (follows) previous else inputTexId, 1)
                program.setFloatsUniformIfPresent("uBlend", floatArrayOf(if (follows && uniforms != null) uniforms[0] else 0f))
                program.setFloatsUniformIfPresent("uThreshold", floatArrayOf(uniforms?.get(1) ?: TemporalNrKernel.MAX_THRESHOLD))
                program.setFloatsUniformIfPresent("uTexel", floatArrayOf(1f / width, 1f / height))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                // Keep this result for the next frame: copied from the output framebuffer, still bound.
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previous)
                GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
                GlUtil.checkGlError()
                hasPrevious = true
                lastUs = presentationTimeUs
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun flush() {
            super.flush()
            hasPrevious = false
        }

        override fun release() {
            super.release()
            try {
                if (previous != 0) GlUtil.deleteTexture(previous)
                previous = 0
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
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

        // TemporalNrKernel.step: the weight on the previous result from the 3 × 3 mean of the signed luma difference.
        const val FRAGMENT_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uPrevious;
uniform float uBlend;
uniform float uThreshold;
uniform vec2 uTexel;
varying vec2 vTexSamplingCoord;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
void main() {
  vec4 cur = texture2D(uTexSampler, vTexSamplingCoord);
  if (uBlend <= 0.0) {
    gl_FragColor = cur;
    return;
  }
  vec3 prev = texture2D(uPrevious, vTexSamplingCoord).rgb;
  float sum = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 at = vTexSamplingCoord + vec2(float(i), float(j)) * uTexel;
      sum += dot(texture2D(uTexSampler, at).rgb - texture2D(uPrevious, at).rgb, LUMA);
    }
  }
  float lo = uThreshold * 0.5;
  float w = uBlend * (1.0 - smoothstep(lo, uThreshold, abs(sum / 9.0)));
  gl_FragColor = vec4(mix(cur.rgb, prev, w), cur.a);
}
"""
    }
}
