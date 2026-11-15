package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.example.prismstudio.p40b43a88.domain.model.TransitionRole
import com.example.prismstudio.p40b43a88.domain.model.TransitionType
import com.example.prismstudio.p40b43a88.domain.model.easedProgress

/**
 * The per-clip picture pipeline in one GPU pass: transform (scale / position / rotation, Dynamic Zoom) and crop, clip
 * effects, the colour grade as 3D LUTs — the primary node, and a power window's composite mixed in
 * where the window covers the picture — with the colour-page before/after split, opacity, the clip's fade handles and
 * its in-transition. Parameters are read from [registry] on every frame, so edits apply live.
 *
 * Frame timestamps inside a composition are relative to the composition start, so the transition
 * progress is measured from the clip's start (clipStartUs, before the item's start when the plan begins partway through
 * the transition).
 *
 * Everything that is not picture (outside a scaled-down frame, cropped away, outside its mask, opacity, a dissolve or wipe, the bars a
 * fitted picture of another shape leaves, a transparent image's clear parts) is coverage, written according to
 * [alphaMode]: flattened onto black for a single layer, straight alpha for Media3's compositor, premultiplied alpha
 * for a layer composited by the view system.
 */
/** How a clip layer writes its coverage. */
enum class AlphaMode {
    /** The only layer: flattened onto black. */
    OPAQUE,

    /** One input of Media3's compositor, which blends straight alpha. */
    STRAIGHT,

    /** One of several stacked views, which the view system composites with premultiplied alpha. */
    PREMULTIPLIED,
}

@UnstableApi
class ClipLookEffect(
    private val clipId: String,
    private val registry: LiveLookRegistry,
    itemStartUs: Long,
    private val transition: TransitionType?,
    private val transitionDurationUs: Long,
    private val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    /** Composition time of the clip's first frame, which keyframes count from. */
    private val clipStartUs: Long = itemStartUs,
    /** Composition time the opening transition starts at: the clip's start, or before it when the transition leads in. */
    private val transitionStartUs: Long = clipStartUs,
    /** The side of a dip this picture plays (the outgoing clip fading out, the incoming one fading up, or alone). */
    private val transitionRole: TransitionRole = TransitionRole.INCOMING,
    /** The clip whose transition style applies: this one, or the incoming clip of a dip this one fades out in. */
    private val styleClipId: String = clipId,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        Program(clipId, registry, transition, transitionDurationUs, alphaMode, clipStartUs, transitionStartUs, transitionRole, styleClipId)

    @Suppress("LongParameterList")
    private class Program(
        private val clipId: String,
        private val registry: LiveLookRegistry,
        private val transition: TransitionType?,
        private val transitionDurationUs: Long,
        private val alphaMode: AlphaMode,
        private val clipStartUs: Long,
        private val transitionStartUs: Long,
        private val transitionRole: TransitionRole,
        private val styleClipId: String,
    ) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

        private val xform = FloatArray(LiveLookRegistry.TRANSFORM_VALUES)
        private val windowNow = FloatArray(8)
        private val maskNow = FloatArray(8)
        private val span = FloatArray(3)
        private val fxNow = FloatArray(8)
        private val stabNow = FloatArray(3)

        private val program: GlProgram
        private var width = 1
        private var height = 1
        /**
         * LUT textures by the array they hold (the registry shares one array per grade, so identity is the
         * key): a keyed grade binds two keyframes' LUTs at a time and reuses them as the playhead goes back.
         */
        private val textures = object : LinkedHashMap<IntArray, Int>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IntArray, Int>): Boolean {
                if (size <= MAX_LUT_TEXTURES) return false
                GlUtil.deleteTexture(eldest.value)
                return true
            }
        }
        private val identity = GlUtil.create4x4IdentityMatrix()

        init {
            try {
                program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth.coerceAtLeast(1)
            height = inputHeight.coerceAtLeast(1)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val look = registry.videoLook(clipId)
                // Keyframes count timeline frames from the clip's start.
                val clipFrame = (presentationTimeUs - clipStartUs) * registry.frameRate / 1_000_000f
                // A keyed grade dissolves from one keyframe's LUTs to the next one's; a still grade has one of each.
                val keyLuts = look?.keyLuts
                var lut = look?.lutPixels
                var lutB = lut
                var windowLut = look?.windowLutPixels
                var windowLutB = windowLut
                var keyMix = 0f
                if (keyLuts != null) {
                    look.gradeSpanAt(clipFrame, span)
                    val a = span[0].toInt().coerceIn(0, keyLuts.lastIndex)
                    val b = span[1].toInt().coerceIn(0, keyLuts.lastIndex)
                    lut = keyLuts[a]
                    lutB = keyLuts[b]
                    look.keyWindowLuts?.let { windowLut = it[a]; windowLutB = it[b] }
                    keyMix = if (a == b) 0f else span[2]
                }
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // GlProgram requires every sampler to be bound; reuse the input texture when there is no LUT.
                program.setSamplerTexIdUniform("uLut", lut?.let(::texture) ?: inputTexId, 1)
                program.setSamplerTexIdUniform("uWindowLut", windowLut?.let(::texture) ?: inputTexId, 2)
                program.setSamplerTexIdUniform("uLutB", lutB?.let(::texture) ?: inputTexId, 3)
                program.setSamplerTexIdUniform("uWindowLutB", windowLutB?.let(::texture) ?: inputTexId, 4)
                // Serial nodes 03 on: one LUT after node 2.
                val postLut = look?.postLutPixels
                program.setSamplerTexIdUniform("uPostLut", postLut?.let(::texture) ?: inputTexId, 5)
                set("uHasPost", if (postLut != null) 1f else 0f)
                set("uKeyMix", keyMix)
                set("uLutSize", LiveLookRegistry.LUT_SIZE.toFloat())
                set("uHasLut", if (lut != null) 1f else 0f)
                if (look?.window != null && windowLut != null) {
                    look.windowAt(clipFrame, windowNow)
                    set("uWindowA", windowNow[0], windowNow[1], windowNow[2], windowNow[3])
                    set("uWindowB", windowNow[4], windowNow[5], windowNow[6], windowNow[7])
                    set("uHasWindow", 1f)
                } else {
                    set("uHasWindow", 0f)
                }
                // The clip's mask: clear outside its shape, the tracks beneath showing there.
                if (look?.mask != null) {
                    look.maskAt(clipFrame, maskNow)
                    set("uMaskA", maskNow[0], maskNow[1], maskNow[2], maskNow[3])
                    set("uMaskB", maskNow[4], maskNow[5], maskNow[6], maskNow[7])
                    set("uHasMask", 1f)
                } else {
                    set("uHasMask", 0f)
                }
                val qualifier = look?.qualifier
                if (qualifier != null) {
                    set("uQualA", qualifier[0], qualifier[1], qualifier[2], qualifier[3])
                    set("uQualB", qualifier[4], qualifier[5], qualifier[6], qualifier[7])
                    set("uQualC", qualifier[8], qualifier[9], qualifier[10], qualifier[11])
                    set("uQualD", qualifier[12], qualifier[13], qualifier[14], qualifier[15])
                }
                set("uHasQualifier", if (qualifier != null) 1f else 0f)
                set("uHighlight", if (registry.highlightClipId == clipId) 1f else 0f)
                // Resolve's keyer ([ChromaKeyKernel.uniforms]); the matte view shows its matte instead (the monitor only).
                val chroma = look?.chromaKey
                if (chroma != null) {
                    set("uChroma", chroma[0], chroma[1], chroma[2], chroma[3])
                    set("uChromaSpill", chroma[4])
                    set("uChromaFinesse", chroma[5], chroma[6], chroma[7])
                }
                set("uChromaOn", if (chroma != null) 1f else 0f)
                set("uChromaMatte", if (chroma != null && registry.keyMatteClipId == clipId) 1f else 0f)
                set("uTexel", 1f / width, 1f / height)
                set("uAspect", width.toFloat() / height)
                if (look != null) {
                    look.transformAt(clipFrame, xform)
                    // Dynamic Zoom scales the picture (and its crop) about its focus, on top of the transform.
                    look.applyZoom(clipFrame, xform, width.toFloat() / height)
                } else {
                    xform.fill(0f)
                    xform[0] = 1f; xform[4] = 1f
                }
                set("uXform", xform[0], xform[1], xform[2], xform[3])
                // The fade handles take the picture down from its opacity: to black alone, to the tracks beneath over them.
                set("uOpacity", xform[4] * (look?.fadeAt(clipFrame) ?: 1f))
                set("uCrop", xform[5], xform[6], xform[7], xform[8])
                set("uCropSoft", xform[9])
                // Stabilization moves the source against the camera's shake (y turned up for the texture).
                if (look != null) look.stabAt(clipFrame, stabNow) else { stabNow[0] = 0f; stabNow[1] = 0f; stabNow[2] = 1f }
                set("uStab", stabNow[0], -stabNow[1], stabNow[2])
                // Keyframed effects change over the clip.
                val fx = look?.let { it.fxAt(clipFrame, fxNow); fxNow } ?: EMPTY_FX
                set("uFxA", fx[0], fx[1], fx[2], fx[3])
                set("uFxB", fx[4], fx[5], fx[6], fx[7])
                // Spatial NR, a tap per pixel of a 1080-line frame whatever this frame's size.
                val nr = look?.noiseReduction
                if (nr != null) set("uNr", nr[0], nr[1], nr[2], nr[3]) else set("uNr", 0f, 0f, 0f, 0f)
                set("uNrStep", height / (NR_REFERENCE_LINES * width), 1f / NR_REFERENCE_LINES)
                set("uDetail", look?.midDetail ?: 0f)
                set("uTime", (presentationTimeUs % 60_000_000L) / 1_000_000f)
                // From where the transition starts — the clip's start, or before it when it leads in — which lies before the
                // item's when the plan begins partway through it; eased, turned round and edged as the transition's style
                // (live, like the look) says.
                val styled = if (styleClipId == clipId) look else registry.videoLook(styleClipId)
                val style = styled?.transitionStyle ?: LiveLookRegistry.NO_TRANSITION_STYLE
                val t = if (transitionDurationUs <= 0) 1f else ((presentationTimeUs - transitionStartUs).toFloat() / transitionDurationUs).coerceIn(0f, 1f)
                val progress = when {
                    transition == null || transitionDurationUs <= 0 -> 1f
                    // A dip eases each half — fading out, fading up — so its middle stays half-way through it.
                    transition.dips && transitionRole != TransitionRole.ALONE ->
                        if (t < 0.5f) 0.5f * easedProgress(t * 2f, style[0], style[1]) else 0.5f + 0.5f * easedProgress(t * 2f - 1f, style[0], style[1])
                    else -> easedProgress(t, style[0], style[1])
                }
                set("uTransition", transitionCode(transition), progress)
                set("uTransitionRole", transitionRole.ordinal.toFloat())
                set("uTransitionStyle", style[2], style[3], style[4], style[8])
                set("uBorderColor", style[5], style[6], style[7])
                val compare = registry.compareClipId == clipId
                set("uCompare", if (compare) 1f else 0f, registry.compareSplit)
                set("uAlphaMode", alphaMode.ordinal.toFloat())
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        /** The texture holding [pixels], uploaded the first time they are drawn with. */
        private fun texture(pixels: IntArray): Int = textures.getOrPut(pixels) {
            val n = LiveLookRegistry.LUT_SIZE
            val bitmap = Bitmap.createBitmap(pixels, n, n * n, Bitmap.Config.ARGB_8888)
            GlUtil.createTexture(bitmap).also { bitmap.recycle() }
        }

        private fun set(name: String, vararg values: Float) = program.setFloatsUniformIfPresent(name, values)

        override fun release() {
            super.release()
            try {
                textures.values.forEach(GlUtil::deleteTexture)
                textures.clear()
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    companion object {
        private val EMPTY_FX = FloatArray(8)

        /** The frame height the noise reduction's radius counts pixels of. */
        private const val NR_REFERENCE_LINES = 1080f

        /** LUT textures one clip's program keeps: a few keyframes either side of the playhead. */
        private const val MAX_LUT_TEXTURES = 6

        fun transitionCode(type: TransitionType?): Float = when (type) {
            null -> 0f
            TransitionType.CROSS_DISSOLVE -> 1f
            TransitionType.DIP_TO_BLACK -> 2f
            TransitionType.DIP_TO_WHITE -> 3f
            TransitionType.PUSH -> 4f
            TransitionType.WIPE -> 5f
            TransitionType.ZOOM_BLUR -> 6f
            TransitionType.IRIS -> 7f
            TransitionType.CLOCK_WIPE -> 8f
            TransitionType.BARN_DOOR -> 9f
            TransitionType.BLUR_DISSOLVE -> 10f
            TransitionType.DIP_TO_COLOR -> 11f
        }

        private const val VERTEX_SHADER = """#version 100
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

        private const val FRAGMENT_SHADER = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uLut;
uniform sampler2D uWindowLut;
uniform sampler2D uLutB;
uniform sampler2D uWindowLutB;
uniform sampler2D uPostLut;
uniform float uHasPost;
uniform float uKeyMix;
uniform float uLutSize;
uniform float uHasLut;
uniform float uHasWindow;
uniform vec4 uWindowA;
uniform vec4 uWindowB;
uniform float uHasMask;
uniform vec4 uMaskA;
uniform vec4 uMaskB;
uniform float uHasQualifier;
uniform vec4 uQualA;
uniform vec4 uQualB;
uniform vec4 uQualC;
uniform vec4 uQualD;
uniform float uHighlight;
uniform vec4 uChroma;
uniform float uChromaSpill;
uniform float uChromaOn;
uniform float uChromaMatte;
uniform vec3 uChromaFinesse;
uniform vec2 uTexel;
uniform float uAspect;
uniform vec4 uXform;
uniform float uOpacity;
uniform vec4 uCrop;
uniform float uCropSoft;
// Stabilization: the steadied picture shows the source moved by (x, y) — shares of the picture, y up — and enlarged by z.
uniform vec3 uStab;
uniform vec4 uFxA;
uniform vec4 uFxB;
// Spatial NR before node 1: luma and chroma range sigmas, the radius (taps each way) and the amount (0 = off); one tap is
// a pixel of a 1080-line frame (uNrStep, in texture coordinates), so it smooths alike at any size.
uniform vec4 uNr;
uniform vec2 uNrStep;
// Resolve's Mid/Detail (MidDetailKernel), −1…1: 0 off.
uniform float uDetail;
uniform float uTime;
// The opening transition (code by TransitionType order, 0 = none: 1 dissolve, 2 / 3 dip to black / white, 4 push, 5 wipe,
// 6 zoom blur, 7 iris, 8 clock wipe, 9 barn door, 10 blur dissolve, 11 dip to a colour) and its progress 0…1.
uniform vec2 uTransition;
// Its style: reverse (1 = the other way), the edge's softness and the border's width (frame heights), a wipe's travel angle
// (radians clockwise from rightwards), and the border's colour (a colour dip's colour instead: dips have no edge).
uniform vec4 uTransitionStyle;
// The side of a dip this picture plays: 0 the incoming clip (up from the colour after the middle), 1 the outgoing one (to the
// colour by the middle), 2 alone (up from the colour over the whole length).
uniform float uTransitionRole;
uniform vec3 uBorderColor;
uniform vec2 uCompare;
uniform float uAlphaMode;
varying vec2 vTexSamplingCoord;

vec3 applyLut(sampler2D lut, vec3 color) {
  float n = uLutSize;
  float redCoord = color.r * (n - 1.0);
  float redLow = clamp(floor(redCoord), 0.0, n - 2.0);
  float lowerY = (0.5 + redLow * n + color.g * (n - 1.0)) / (n * n);
  float upperY = lowerY + 1.0 / n;
  float x = (0.5 + color.b * (n - 1.0)) / n;
  vec3 lower = texture2D(lut, vec2(x, lowerY)).rgb;
  vec3 upper = texture2D(lut, vec2(x, upperY)).rgb;
  return mix(lower, upper, redCoord - redLow);
}

float fade(float from, float to, float x) {
  if (to - from < 0.0001) return x < (from + to) * 0.5 ? 0.0 : 1.0;
  return smoothstep(from, to, x);
}

// How much of a shape covers picture point p (0..1, y up): PowerWindow.coverage.
// a = (shape, centerX, centerY, size), b = (aspect, angle, softness, invert).
float shapeCoverage(vec4 a, vec4 b, vec2 p) {
  vec2 d = vec2((p.x - 0.5) * 2.0 * uAspect - a.y * uAspect, (p.y - 0.5) * 2.0 - a.z);
  float c = cos(b.y);
  float s = sin(b.y);
  vec2 q = vec2(d.x * c + d.y * s, -d.x * s + d.y * c);
  float soft = b.z;
  float inside;
  if (a.x > 3.5) {
    inside = 1.0;
  } else if (a.x > 2.5) {
    inside = fade(-soft, soft, q.y);
  } else {
    vec2 n = q / vec2(max(a.w * b.x, 0.01), max(a.w, 0.01));
    float r = a.x > 1.5 ? max(abs(n.x), abs(n.y)) : length(n);
    inside = 1.0 - fade(1.0 - soft, 1.0 + soft, r);
  }
  return b.w > 0.5 ? 1.0 - inside : inside;
}

// How much of the window node shows at picture point p.
float windowCoverage(vec2 p) {
  return shapeCoverage(uWindowA, uWindowB, p);
}

// 1 inside low..high, fading to 0 across soft beyond each closed end: HslQualifier.band.
float band(float x, float low, float high, float soft) {
  float above = low <= 0.0 ? 1.0 : fade(low - soft, low, x);
  float below = high >= 1.0 ? 1.0 : 1.0 - fade(high, high + soft, x);
  return above * below;
}

// How selected colour c is by the qualifier: HslQualifier.key. uQualA = (hue centre, width, soft
// in degrees, invert), uQualB = (sat low, high, soft), uQualC = (lum low, high, soft).
float qualifierKey(vec3 c) {
  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float chroma = mx - mn;
  float hueKey = 1.0;
  if (uQualA.y < 180.0) {
    float h = 0.0;
    if (chroma > 0.0) {
      if (mx == c.r) h = mod((c.g - c.b) / chroma + 6.0, 6.0);
      else if (mx == c.g) h = (c.b - c.r) / chroma + 2.0;
      else h = (c.r - c.g) / chroma + 4.0;
    }
    float d = abs(mod(h * 60.0 - uQualA.x + 540.0, 360.0) - 180.0);
    hueKey = 1.0 - fade(uQualA.y, uQualA.y + uQualA.z, d);
  }
  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
  float k = hueKey * band(chroma, uQualB.x, uQualB.y, uQualB.z) * band(luma, uQualC.x, uQualC.y, uQualC.z);
  k = uQualA.w > 0.5 ? 1.0 - k : k;
  // Clean black / white: levels on the matte (uQualD.x black point, uQualD.y white point).
  if (uQualD.x > 0.0 || uQualD.y < 1.0) k = clamp((k - uQualD.x) / max(uQualD.y - uQualD.x, 0.0001), 0.0, 1.0);
  return k;
}

bool inside(vec2 uv) {
  return uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;
}

// One cut edge: d is the distance from that side of the picture, cut how far it is cut back; the picture
// fades in over soft past the cut (nothing outside the cut ever shows).
float cropEdge(float d, float cut, float soft) {
  if (cut <= 0.0) return 1.0;
  if (soft <= 0.0) return d >= cut ? 1.0 : 0.0;
  return smoothstep(cut, cut + soft, d);
}

// Resolve's cropping at source point suv (0..1, y up): uCrop = (left, right, top, bottom) shares of the
// picture, uCropSoft the fade as a share of its height (the same distance across).
float cropCoverage(vec2 suv) {
  float sx = uCropSoft / uAspect;
  return cropEdge(suv.x, uCrop.x, sx) * cropEdge(1.0 - suv.x, uCrop.y, sx)
      * cropEdge(1.0 - suv.y, uCrop.z, uCropSoft) * cropEdge(suv.y, uCrop.w, uCropSoft);
}

vec3 tapClamped(vec2 uv) {
  return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)).rgb;
}

// The keyer's raw matte of colour c (ChromaKeyKernel): 0 within the similarity of the key's Cb Cr, (d / smoothness)^1.5 beyond.
float chromaMatte(vec3 c) {
  c = clamp(c, 0.0, 1.0);
  float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
  float d = distance(vec2((c.b - y) / 1.8556, (c.r - y) / 1.5748), uChroma.xy) - uChroma.z;
  return uChroma.w > 0.0 ? pow(clamp(d / uChroma.w, 0.0, 1.0), 1.5) : step(0.0, d);
}

// The matte at source point suv over its neighbours (遮罩模糊, uChromaFinesse.z pixels of a 1080-line frame): the centre and
// twelve taps on two rings, the inner ring weighted more, then clean black / clean white (ChromaKeyKernel.finesse).
float chromaMatteAt(vec2 suv, float centre) {
  float a = centre;
  if (uChromaFinesse.z > 0.0) {
    vec2 r = vec2(uTexel.x / uTexel.y, 1.0) * (uChromaFinesse.z / 1080.0);
    float sum = centre;
    float total = 1.0;
    for (int i = 0; i < 12; i++) {
      float angle = float(i) * 0.5235988;
      float ring = i < 6 ? 0.5 : 1.0;
      float w = i < 6 ? 0.8 : 0.5;
      sum += w * chromaMatte(tapClamped(suv + vec2(cos(angle), sin(angle)) * r * ring));
      total += w;
    }
    a = sum / total;
  }
  float black = uChromaFinesse.x;
  float white = uChromaFinesse.y;
  if (black > 0.0 || white > 0.0) a = clamp((a - black) / max(1.0 - white - black, 0.0001), 0.0, 1.0);
  return a;
}

vec2 toSource(vec2 uv) {
  vec2 p = uv - 0.5;
  p.x *= uAspect;
  p -= vec2(uXform.y * uAspect, uXform.z);
  p /= max(uXform.x, 0.01);
  float c = cos(uXform.w);
  float s = sin(uXform.w);
  p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
  p.x /= uAspect;
  return p + 0.5;
}

vec3 toYcc(vec3 c) {
  float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
  return vec3(y, (c.b - y) / 1.8556, (c.r - y) / 1.5748);
}

vec3 fromYcc(vec3 v) {
  float r = v.x + 1.5748 * v.z;
  float b = v.x + 1.8556 * v.y;
  return vec3(r, (v.x - 0.2126 * r - 0.0722 * b) / 0.7152, b);
}

// Resolve's Spatial NR at uv (SpatialNrKernel mirrors it): a bilateral filter in BT.709 Y'CbCr over (2r + 1)^2 taps,
// weighted by a Gaussian in distance (sigma 0.5 + r / 2 taps) times a Gaussian in the luma difference for luma and in the
// chroma difference for chroma — noise within the thresholds averages away, edges past them stay — then mixed in by amount.
vec3 denoised(vec2 uv) {
  vec3 c = tapClamped(uv);
  vec3 centre = toYcc(c);
  float r = uNr.z;
  float ss = 0.5 + 0.5 * r;
  float invS = 1.0 / (2.0 * ss * ss);
  float invL = uNr.x > 0.0 ? 1.0 / (2.0 * uNr.x * uNr.x) : 0.0;
  float invC = uNr.y > 0.0 ? 1.0 / (2.0 * uNr.y * uNr.y) : 0.0;
  float sumL = 0.0;
  float wL = 0.0;
  vec2 sumC = vec2(0.0);
  float wC = 0.0;
  for (int j = -3; j <= 3; j++) {
    for (int i = -3; i <= 3; i++) {
      vec2 o = vec2(float(i), float(j));
      if (abs(o.x) > r || abs(o.y) > r) continue;
      vec3 q = toYcc(tapClamped(uv + o * uNrStep));
      float s = dot(o, o) * invS;
      float dl = q.x - centre.x;
      vec2 dc = q.yz - centre.yz;
      float wl = uNr.x > 0.0 ? exp(-s - dl * dl * invL) : (i == 0 && j == 0 ? 1.0 : 0.0);
      float wc = uNr.y > 0.0 ? exp(-s - dot(dc, dc) * invC) : (i == 0 && j == 0 ? 1.0 : 0.0);
      sumL += wl * q.x;
      wL += wl;
      sumC += wc * q.yz;
      wC += wc;
    }
  }
  return mix(c, fromYcc(vec3(sumL / wL, sumC / wC)), uNr.w);
}

// Resolve's Mid/Detail (MidDetailKernel mirrors it): the difference from the local mean — 5 × 5 taps three 1080-line
// pixels apart, binomially weighted (1 4 6 4 1 each way, so fine regular texture does not alias into its negative) —
// weighted to the midtones, added (positive) or faded towards the mean (negative).
float binomial(int i) { return i == 0 ? 6.0 : (i == 1 || i == -1 ? 4.0 : 1.0); }
vec3 detailed(vec2 uv, vec3 c) {
  vec3 mean = vec3(0.0);
  for (int j = -2; j <= 2; j++) {
    for (int i = -2; i <= 2; i++) mean += binomial(i) * binomial(j) * tapClamped(uv + vec2(float(i), float(j)) * uNrStep * 3.0);
  }
  mean /= 256.0;
  float y = clamp(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
  float w = 4.0 * y * (1.0 - y);
  return uDetail > 0.0 ? c + (c - mean) * uDetail * 1.5 * w : c + (mean - c) * (-uDetail) * w;
}

// The source picture at uv, through the noise reduction and Mid/Detail when [nr] (not in the compare view's ungraded half).
vec3 tapSource(vec2 uv, bool nr) {
  if (!nr) return tapClamped(uv);
  vec2 at = clamp(uv, 0.0, 1.0);
  vec3 c = uNr.w > 0.0 ? denoised(at) : tapClamped(uv);
  return uDetail != 0.0 ? detailed(at, c) : c;
}

vec3 sampleSource(vec2 uv, bool nr) {
  if (uFxB.y > 0.0) {
    vec2 cell = uTexel * (1.0 + uFxB.y * 47.0);
    uv = (floor(uv / cell) + 0.5) * cell;
  }
  vec3 color;
  if (uFxB.z > 0.0) {
    vec2 dir = (uv - 0.5) * uFxB.z * 0.025;
    color = vec3(tapSource(uv + dir, nr).r, tapSource(uv, nr).g, tapSource(uv - dir, nr).b);
  } else {
    color = tapSource(uv, nr);
  }
  if (uFxA.x > 0.0) {
    float radius = uFxA.x * 16.0;
    vec3 acc = color;
    float total = 1.0;
    for (int i = 0; i < 16; i++) {
      float angle = float(i) * 0.3926991;
      float ring = mod(float(i), 2.0) < 0.5 ? 1.0 : 0.5;
      float weight = ring < 1.0 ? 0.9 : 0.55;
      acc += tapClamped(uv + vec2(cos(angle), sin(angle)) * uTexel * radius * ring) * weight;
      total += weight;
    }
    color = acc / total;
  }
  if (uFxA.y > 0.0) {
    vec3 around = tapClamped(uv + vec2(uTexel.x, 0.0)) + tapClamped(uv - vec2(uTexel.x, 0.0))
        + tapClamped(uv + vec2(0.0, uTexel.y)) + tapClamped(uv - vec2(0.0, uTexel.y));
    color = color + (color - around * 0.25) * uFxA.y * 2.0;
  }
  return color;
}

// Node 1's colour at source point suv: what the qualifier keys on there.
vec3 nodeOne(vec2 suv) {
  vec3 source = clamp(sampleSource(suv, true), 0.0, 1.0);
  if (uHasLut < 0.5) return source;
  vec3 c = applyLut(uLut, source);
  if (uKeyMix > 0.0) c = mix(c, applyLut(uLutB, source), uKeyMix);
  return c;
}

// The qualifier's matte at suv, where node 1 gives [graded]: when it has a radius (uQualD.z, a share of
// the source's height), blurred over HslQualifier.BLUR_TAPS — 24 taps on a golden-angle disk, each an
// equal share of its area, weighted by a Gaussian with sigma = half the radius.
float qualifierMatte(vec2 suv, vec3 graded) {
  if (uQualD.z <= 0.0) return qualifierKey(clamp(graded, 0.0, 1.0));
  vec2 r = vec2(uQualD.z * uTexel.x / uTexel.y, uQualD.z);
  float sum = 0.0;
  float total = 0.0;
  for (int i = 0; i < 24; i++) {
    float rho = sqrt((float(i) + 0.5) / 24.0);
    float a = float(i) * 2.3999632;
    float w = exp(-2.0 * rho * rho);
    sum += w * qualifierKey(nodeOne(suv + rho * r * vec2(cos(a), sin(a))));
    total += w;
  }
  return sum / total;
}

// A shaped wipe's signed distance from uv to its edge, in frame heights, positive where the incoming picture shows:
// 5 wipe, 7 iris, 8 clock, 9 barn door. The wipe, iris and doors travel [reach] past the frame at both ends, so a soft
// edge or border is gone at the start and the end; the clock's edge cannot leave the frame.
float wipeDistance(vec2 uv, float type, float progress, float reach) {
  bool reversed = uTransitionStyle.x > 0.5;
  vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
  if (type < 5.5) {
    // Travelling at its angle (0 = left to right; texture y points up, so the screen's clockwise turns the other way):
    // the edge crosses the frame between the corners furthest back and furthest ahead along that direction.
    vec2 dir = vec2(cos(uTransitionStyle.w), -sin(uTransitionStyle.w));
    float extent = 0.5 * (uAspect * abs(dir.x) + abs(dir.y));
    return mix(-extent - reach, extent + reach, progress) - dot(d, dir);
  }
  if (type < 7.5) {
    // A circle from the centre out to the corners (reversed, closing in on the centre).
    float corner = length(vec2(uAspect, 1.0) * 0.5);
    float r = length(d);
    return reversed ? r - mix(corner + reach, -reach, progress) : mix(-reach, corner + reach, progress) - r;
  }
  if (type < 8.5) {
    // From twelve o'clock clockwise (anticlockwise reversed; texture y points up): the distances to the start ray and
    // to the hand — inside both while the sweep is under half a turn, inside either beyond.
    if (reversed) d.x = -d.x;
    float r = length(d);
    float a = atan(d.x, d.y);
    float hand = progress * 6.2831853;
    float h = hand - a;
    h -= 6.2831853 * floor((h + 3.1415927) / 6.2831853);
    float fromStart = r * sin(clamp(a, -1.5707963, 1.5707963));
    float toHand = r * sin(clamp(h, -1.5707963, 1.5707963));
    return hand <= 3.1415927 ? min(fromStart, toHand) : max(fromStart, toHand);
  }
  // Barn door: open from the centre line out to both sides (reversed, closing in from them).
  float x = abs(uv.x - 0.5) * uAspect;
  float halfWidth = 0.5 * uAspect;
  return reversed ? x - mix(halfWidth + reach, -reach, progress) : mix(-reach, halfWidth + reach, progress) - x;
}

void main() {
  vec2 uv = vTexSamplingCoord;
  float type = uTransition.x;
  float progress = clamp(uTransition.y, 0.0, 1.0);
  vec2 suv = toSource(uv);
  if (type > 3.5 && type < 4.5) {
    // Push: in from the right (from the left reversed).
    suv.x -= (1.0 - progress) * (uTransitionStyle.x > 0.5 ? -1.0 : 1.0);
  }
  if (type > 5.5 && type < 6.5) {
    float zoom = 1.0 + (1.0 - progress) * 0.45;
    suv = (suv - 0.5) / zoom + 0.5;
  }
  // The crop belongs to the picture as it shows, before the mirror effect turns its source around; so does the mask.
  float crop = cropCoverage(suv);
  if (uHasMask > 0.5) crop *= shapeCoverage(uMaskA, uMaskB, suv);
  if (uFxB.x > 0.5) {
    suv.x = 1.0 - suv.x;
  }
  // Stabilization comes first in the picture's making, so last here: from the steadied picture back to the source.
  suv = (suv - 0.5 - uStab.xy) / max(uStab.z, 0.01) + 0.5;
  bool outside = !inside(suv) || crop <= 0.0;
  // The input's own alpha: 0 in the bars Scaling's fit leaves (Resolve shows the tracks beneath there) and in a
  // transparent image's clear parts. Edge texels arrive premultiplied (mixed with the bars' clear black): undo that.
  float inputAlpha = outside ? 0.0 : texture2D(uTexSampler, clamp(suv, 0.0, 1.0)).a;
  // The compare view's left half shows the source as it came, without the noise reduction either.
  bool graded = !(uCompare.x > 0.5 && uv.x < uCompare.y);
  vec3 color = outside ? vec3(0.0) : sampleSource(suv, graded);
  if (inputAlpha > 0.0 && inputAlpha < 1.0) color /= inputAlpha;
  // Resolve's keyer (ChromaKeyKernel): what lies near the key colour in BT.709 Cb Cr keys out and the spill near it loses
  // its tint — on the source, before the grade colours what is left. Not in the compare view's ungraded half.
  float chromaAlpha = 1.0;
  if (uChromaOn > 0.5 && graded && !outside) {
    vec3 c = clamp(color, 0.0, 1.0);
    float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float d = distance(vec2((c.b - y) / 1.8556, (c.r - y) / 1.5748), uChroma.xy) - uChroma.z;
    chromaAlpha = chromaMatteAt(suv, uChroma.w > 0.0 ? pow(clamp(d / uChroma.w, 0.0, 1.0), 1.5) : step(0.0, d));
    if (uChromaSpill > 0.0) color = mix(vec3(y), c, pow(clamp(d / uChromaSpill, 0.0, 1.0), 1.5));
  }
  bool matte = uChromaMatte > 0.5 && uChromaOn > 0.5;
  if (type > 5.5 && type < 6.5 && progress < 1.0 && !outside) {
    vec3 acc = color;
    for (int i = 1; i <= 6; i++) {
      float k = float(i) / 6.0 * (1.0 - progress) * 0.12;
      acc += tapClamped(mix(suv, vec2(0.5), k));
    }
    color = acc / 7.0;
  }
  // Blur dissolve: out of focus while faint, sharp by the end (twelve taps on two rings, the radius shrinking to nothing).
  if (type > 9.5 && type < 10.5 && progress < 1.0 && !outside) {
    float radius = (1.0 - progress) * 24.0;
    vec3 acc = color;
    for (int i = 0; i < 12; i++) {
      float angle = float(i) * 0.5235988;
      float ring = i < 6 ? 0.5 : 1.0;
      acc += tapClamped(suv + vec2(cos(angle), sin(angle)) * uTexel * radius * ring);
    }
    color = acc / 13.0;
  }
  if (graded && !outside && (uHasLut > 0.5 || uHasWindow > 0.5 || uHasPost > 0.5)) {
    vec3 source = clamp(color, 0.0, 1.0);
    if (uHasLut > 0.5) {
      color = applyLut(uLut, source);
      // A keyed node 1 dissolves into the next keyframe's look.
      if (uKeyMix > 0.0) color = mix(color, applyLut(uLutB, source), uKeyMix);
    }
    float matte = 1.0;
    if (uHasWindow > 0.5) {
      // The qualifier keys on node 2's input: the primary-graded colour.
      float k = windowCoverage(suv);
      if (k > 0.0 && uHasQualifier > 0.5) k *= qualifierMatte(suv, color);
      if (k > 0.0) {
        vec3 node2 = applyLut(uWindowLut, source);
        if (uKeyMix > 0.0) node2 = mix(node2, applyLut(uWindowLutB, source), uKeyMix);
        color = mix(color, node2, k);
      }
      matte = k;
    }
    // Serial nodes 03 on, baked into one LUT: on what nodes 01 and 02 made.
    if (uHasPost > 0.5) color = applyLut(uPostLut, clamp(color, 0.0, 1.0));
    if (uHasWindow > 0.5 && uHighlight > 0.5) color = mix(vec3(0.5), color, matte);
  }
  if (uFxB.w > 0.0 && !outside) {
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 8; i++) {
      float angle = float(i) * 0.7853982;
      bloom += max(tapClamped(suv + vec2(cos(angle), sin(angle)) * uTexel * 10.0) - 0.55, 0.0);
    }
    color += bloom / 8.0 * uFxB.w * 2.4;
  }
  if (uFxA.z > 0.0) {
    vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
    float dist = length(d) / length(vec2(uAspect, 1.0) * 0.5);
    color *= 1.0 - uFxA.z * smoothstep(0.4, 1.05, dist);
  }
  if (uFxA.w > 0.0) {
    float noise = fract(sin(dot(uv * 1000.0 + uTime * 37.0, vec2(12.9898, 78.233))) * 43758.5453);
    color += (noise - 0.5) * uFxA.w * 0.22;
  }
  // Coverage: transparent outside the picture and where it is clear, scaled by opacity and the revealing transitions.
  float alpha = outside ? 0.0 : uOpacity * crop * inputAlpha * (matte ? 1.0 : chromaAlpha);
  if (type > 0.5 && type < 1.5) {
    alpha *= progress;
  } else if ((type > 1.5 && type < 3.5) || (type > 10.5 && type < 11.5)) {
    // Dip to black (2), white (3) or a colour (11), one picture at a time: the outgoing one to the colour by the middle, the
    // incoming one up from it — or, alone, over the whole length.
    vec3 dip = type > 10.5 ? uBorderColor : (type < 2.5 ? vec3(0.0) : vec3(1.0));
    if (uTransitionRole > 1.5) color = mix(dip, color, progress);
    else if (uTransitionRole > 0.5) color = mix(color, dip, clamp(progress * 2.0, 0.0, 1.0));
    else color = mix(dip, color, clamp(progress * 2.0 - 1.0, 0.0, 1.0));
  } else if (type > 9.5 && type < 10.5) {
    alpha *= progress;
  } else if ((type > 4.5 && type < 5.5) || (type > 6.5 && type < 9.5)) {
    // A shaped wipe (the iris in the frame's own proportions, reaching its corners at the end): the incoming picture
    // inside the edge, softened over its softness, and a band of the border colour along it over both pictures.
    float soft = uTransitionStyle.y;
    float band = uTransitionStyle.z;
    if (type > 7.5 && type < 8.5) {
      float taper = clamp(min(progress, 1.0 - progress) * 10.0, 0.0, 1.0);
      soft *= taper;
      band *= taper;
    }
    float sd = wipeDistance(uv, type, progress, (soft + band) * 0.5);
    alpha *= soft > 0.0 ? smoothstep(-soft * 0.5, soft * 0.5, sd) : step(0.0, sd);
    if (band > 0.0) {
      float edge = max(soft, 0.0015);
      float b = 1.0 - smoothstep(band * 0.5 - edge * 0.5, band * 0.5 + edge * 0.5, abs(sd));
      float covered = b + (1.0 - b) * alpha;
      color = covered > 0.0 ? (uBorderColor * b + color * alpha * (1.0 - b)) / covered : uBorderColor;
      alpha = covered;
    }
  }
  color = clamp(color, 0.0, 1.0);
  // The matte view (Resolve's highlight): the key's matte, white where the picture stays and black where it keys out.
  if (matte && !outside) color = vec3(chromaAlpha);
  if (uAlphaMode > 1.5) {
    gl_FragColor = vec4(color * alpha, alpha);  // premultiplied, for view-level compositing
  } else if (uAlphaMode > 0.5) {
    gl_FragColor = vec4(color, alpha);          // straight, for Media3's compositor
  } else {
    gl_FragColor = vec4(color * alpha, 1.0);    // a single layer, flattened onto black
  }
}
"""
    }
}
