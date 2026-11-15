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
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.HdrToneMap
import com.example.prismstudio.p40b43a88.domain.model.ToneMapping

/**
 * An HDR clip tone mapped into the Rec.709 timeline, first in its chain ([HdrToneMap] in a shader, constant for constant).
 * Renders read HDR files as SDR ([androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR]):
 * Media3's own HDR input needs the GPU's EXT_YUV_target, which some devices lack (the render failed outright), and this
 * keeps the curve the same on every device. The frame arrives as the file's R′G′B′ code values.
 */
@UnstableApi
class HdrToneMapEffect(private val transfer: ColorTransfer, private val method: ToneMapping, private val pqPeakNits: Int) : GlEffect {

    init {
        require(transfer != ColorTransfer.SDR) { "Only HDR sources are tone mapped" }
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
        val peak = (if (transfer == ColorTransfer.PQ) pqPeakNits.toFloat() else HdrToneMap.HLG_PEAK_NITS) / HdrToneMap.REFERENCE_WHITE_NITS
        return Program(if (transfer == ColorTransfer.PQ) 2 else 1, method.ordinal, peak, HdrToneMap.shoulderTau(peak))
    }

    private class Program(transfer: Int, method: Int, peak: Float, tau: Float) :
        BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {
        private val program: GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            program.setIntUniform("uTransfer", transfer)
            program.setIntUniform("uMethod", method)
            program.setFloatUniform("uPeak", peak)
            program.setFloatUniform("uTau", tau)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
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

        // HdrToneMap, line for line: HLG (1) or PQ (2) light relative to 203 nits, into Rec.709, through the curve.
        val FRAGMENT_SHADER = """#version 100
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform int uTransfer;
uniform int uMethod;
uniform float uPeak;
uniform float uTau;
varying vec2 vTexSamplingCoord;
const float HLG_A = ${HdrToneMap.HLG_A};
const float HLG_B = ${HdrToneMap.HLG_B};
const float HLG_C = ${HdrToneMap.HLG_C};
const float GAIN = ${HdrToneMap.GAIN};
const float KNEE = ${HdrToneMap.KNEE};
float hlgToScene(float e) {
  e = clamp(e, 0.0, 1.0);
  return e <= 0.5 ? e * e / 3.0 : (exp((e - HLG_C) / HLG_A) + HLG_B) / 12.0;
}
float pqToNits(float e) {
  float p = pow(clamp(e, 0.0, 1.0), 1.0 / 78.84375);
  return 10000.0 * pow(max(p - 0.8359375, 0.0) / (18.8515625 - 18.6875 * p), 1.0 / 0.1593017578125);
}
float curve(float x) {
  if (x <= KNEE) return max(x, 0.0) * GAIN;
  if (x >= uPeak) return 1.0;
  float yk = GAIN * KNEE;
  float norm = 1.0 - exp(-(uPeak - KNEE) / uTau);
  return yk + (1.0 - yk) * (1.0 - exp(-(x - KNEE) / uTau)) / norm;
}
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 nits;
  if (uTransfer == 2) {
    nits = vec3(pqToNits(src.r), pqToNits(src.g), pqToNits(src.b));
  } else {
    vec3 s = vec3(hlgToScene(src.r), hlgToScene(src.g), hlgToScene(src.b));
    float ys = dot(s, vec3(0.2627, 0.6780, 0.0593));
    nits = s * ${HdrToneMap.HLG_PEAK_NITS} * pow(max(ys, 1e-6), ${HdrToneMap.HLG_GAMMA} - 1.0);
  }
  vec3 rel = nits / ${HdrToneMap.REFERENCE_WHITE_NITS};
  vec3 lin = vec3(
      dot(rel, vec3(${HdrToneMap.BT2020_TO_BT709[0]}, ${HdrToneMap.BT2020_TO_BT709[1]}, ${HdrToneMap.BT2020_TO_BT709[2]})),
      dot(rel, vec3(${HdrToneMap.BT2020_TO_BT709[3]}, ${HdrToneMap.BT2020_TO_BT709[4]}, ${HdrToneMap.BT2020_TO_BT709[5]})),
      dot(rel, vec3(${HdrToneMap.BT2020_TO_BT709[6]}, ${HdrToneMap.BT2020_TO_BT709[7]}, ${HdrToneMap.BT2020_TO_BT709[8]})));
  vec3 o;
  if (uMethod == 0) {
    o = clamp(lin, 0.0, 1.0);
  } else if (uMethod == 1) {
    o = vec3(curve(lin.r), curve(lin.g), curve(lin.b));
  } else {
    float y = dot(lin, vec3(0.2126, 0.7152, 0.0722));
    float m = curve(y);
    float ratio = y > 1e-6 ? m / y : GAIN;
    vec3 sc = max(lin * ratio, 0.0);
    if (uMethod == 3) {
      float top = max(sc.r, max(sc.g, sc.b));
      if (top > 1.0 && top > m) {
        float k = clamp((1.0 - m) / (top - m), 0.0, 1.0);
        sc = m + (sc - m) * k;
      }
    }
    o = min(sc, 1.0);
  }
  gl_FragColor = vec4(pow(clamp(o, 0.0, 1.0), vec3(1.0 / ${HdrToneMap.DISPLAY_GAMMA})), src.a);
}
"""
    }
}
