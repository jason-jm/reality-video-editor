package com.example.prismstudio.p40b43a88.ui.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.graphics.BlendMode
import android.graphics.Paint
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.prismstudio.p40b43a88.domain.model.CompositeMode
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme

/** Lifecycle of one of a monitor's video surfaces; [layer] 0 is the bottom view. */
sealed interface SurfaceChange {
    val layer: Int

    data class Ready(val surface: Surface, val width: Int, val height: Int, override val layer: Int = 0) : SurfaceChange
    data class Gone(val surface: Surface, override val layer: Int = 0) : SurfaceChange
}

/** What a monitor shows besides (or instead of) live video. */
data class MonitorContent(
    /** Width / height of the picture (project raster or source clip). */
    val pictureAspect: Float = 16f / 9f,
    /** True while a player is routed to this monitor. */
    val video: Boolean = false,
    /** Picture layers stacked as separate views (bottom first); upper ones are transparent around their picture. */
    val layers: Int = 1,
    /** Still image shown when there is no live video (photos, posters). */
    val still: Bitmap? = null,
    /** Centered status text (empty timeline, offline media). */
    val message: String? = null,
    val messageIsError: Boolean = false,
    /** Small caption in the lower-left corner over the picture (e.g. an approximate preview). */
    val note: String? = null,
    /** Each layer's composite mode at the playhead (Resolve's Composite Mode), bottom first; the bottom never blends. */
    val composites: List<CompositeMode> = emptyList(),
)

/** Shown on the program monitor while layered edits are previewed flattened. */
const val APPROXIMATE_PREVIEW_NOTE = "预览为近似效果 · 画中画与转场在导出时精确合成"

/**
 * The `video-viewer` renderer: a picture-black 16:9 frame (radius 16) holding the player surface
 * letterboxed to the picture's aspect ratio, with optional title-safe guides and before/after split.
 */
@Composable
fun MonitorView(
    content: MonitorContent,
    onSurface: (SurfaceChange) -> Unit,
    modifier: Modifier = Modifier,
    showSafeArea: Boolean = false,
    compareSplit: Float? = null,
    beforeLabel: String = "调色前",
    afterLabel: String = "调色后",
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Drawn over the picture itself (letterboxed to its aspect ratio), e.g. a power window. */
    pictureOverlay: @Composable BoxScope.() -> Unit = {},
    /** With [compareSplit]: a still shown left of the split instead of the ungraded picture (the Gallery wipe). */
    wipe: ImageBitmap? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(modifier.aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
        val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        val aspect = content.pictureAspect.takeIf { it > 0f } ?: (16f / 9f)
        val pictureModifier = if (aspect >= boxAspect) {
            Modifier.size(maxWidth, maxWidth / aspect)
        } else {
            Modifier.size(maxHeight * aspect, maxHeight)
        }
        Box(
            Modifier.matchParentSize().clip(shape).drawBehind { drawRect(PrismColors.PictureBlack) },
            contentAlignment = Alignment.Center,
        ) {
            Box(pictureModifier, contentAlignment = Alignment.Center) {
                if (content.video) {
                    for (layer in 0 until content.layers.coerceAtLeast(1)) {
                        // A new picture shape (the timeline turned vertical or square) gets new surfaces: a resized
                        // TextureView keeps the old buffer under the player's GL surface, squeezing the picture.
                        key(layer, aspect) {
                            VideoSurface(layer, onSurface, Modifier.fillMaxSize().zIndex(layer.toFloat()), content.composites.getOrNull(layer) ?: CompositeMode.NORMAL)
                        }
                    }
                } else if (content.still != null) {
                    Image(
                        bitmap = content.still.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            content.message?.let { text ->
                Text(
                    text = text,
                    style = PrismType.body,
                    color = if (content.messageIsError) PicoTheme.colorScheme.error else PicoTheme.colorScheme.labelPrimaryLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        // Guides, labels and overlays are drawn beside the clipped box that holds the video surfaces,
        // not inside it: in the spatial shell, drawing inside it stays under the video.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Box(pictureModifier) {
                if (showSafeArea) SafeAreaGuides(Modifier.fillMaxSize())
                if (compareSplit != null && wipe != null) WipeStill(wipe, compareSplit)
                if (compareSplit != null) CompareSplit(compareSplit, beforeLabel, afterLabel)
                pictureOverlay()
            }
            content.note?.let { note ->
                Text(
                    text = note,
                    style = PrismType.caption,
                    color = PicoTheme.colorScheme.labelPrimaryLight,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind { drawRect(PrismColors.PictureBlack, alpha = 0.6f) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            overlay()
        }
    }
}

@Composable
private fun VideoSurface(layer: Int, onSurface: (SurfaceChange) -> Unit, modifier: Modifier, composite: CompositeMode) {
    val callback by rememberUpdatedState(onSurface)
    AndroidView(
        modifier = modifier,
        // An overlay layer blends with the views beneath through its layer paint (premultiplied, as the layers draw).
        update = { view ->
            if (view.tag != composite) {
                view.tag = composite
                view.setLayerPaint(composite.blendMode()?.let { mode -> Paint().apply { blendMode = mode } })
            }
        },
        factory = { context ->
            TextureView(context).apply {
                // Upper layers let the layers beneath show through around and behind their picture.
                isOpaque = layer == 0
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(texture).also { callback(SurfaceChange.Ready(it, width, height, layer)) }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        surface?.let { callback(SurfaceChange.Ready(it, width, height, layer)) }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        surface?.let { callback(SurfaceChange.Gone(it, layer)) }
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** The platform's blend for a composite mode; null draws the layer normally (source over). */
private fun CompositeMode.blendMode(): BlendMode? = when (this) {
    CompositeMode.NORMAL -> null
    CompositeMode.DARKEN -> BlendMode.DARKEN
    CompositeMode.MULTIPLY -> BlendMode.MULTIPLY
    CompositeMode.LIGHTEN -> BlendMode.LIGHTEN
    CompositeMode.SCREEN -> BlendMode.SCREEN
    CompositeMode.ADD -> BlendMode.PLUS
    CompositeMode.OVERLAY -> BlendMode.OVERLAY
    CompositeMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    CompositeMode.DIFFERENCE -> BlendMode.DIFFERENCE
    CompositeMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
    CompositeMode.COLOR_BURN -> BlendMode.COLOR_BURN
    CompositeMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
    CompositeMode.EXCLUSION -> BlendMode.EXCLUSION
    CompositeMode.HUE -> BlendMode.HUE
    CompositeMode.SATURATION -> BlendMode.SATURATION
    CompositeMode.COLOR -> BlendMode.COLOR
    CompositeMode.LUMINOSITY -> BlendMode.LUMINOSITY
}

@Composable
private fun SafeAreaGuides(modifier: Modifier) {
    val guide = PicoTheme.colorScheme.labelQuaternary
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        listOf(0.05f, 0.1f).forEach { inset ->
            drawRect(
                color = guide,
                topLeft = Offset(size.width * inset, size.height * inset),
                size = Size(size.width * (1 - 2 * inset), size.height * (1 - 2 * inset)),
                style = Stroke(width = 1.5f, pathEffect = dash),
            )
        }
    }
}

/** The still's part left of [split], over the picture: Resolve's wipe of a Gallery still against the shot. */
@Composable
private fun BoxScope.WipeStill(still: ImageBitmap, split: Float) {
    Canvas(Modifier.matchParentSize()) {
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        clipRect(right = size.width * split) {
            drawImage(still, dstSize = IntSize(w, h))
        }
    }
}

@Composable
private fun BoxScope.CompareSplit(split: Float, before: String, after: String) {
    val line = PicoTheme.colorScheme.labelPrimaryLight
    Canvas(Modifier.matchParentSize()) {
        val x = size.width * split
        drawRoundRect(line, topLeft = Offset(x - 1.5f, 0f), size = Size(3f, size.height), cornerRadius = CornerRadius(1.5f))
    }
    Text(
        text = before,
        style = PrismType.caption,
        color = PicoTheme.colorScheme.labelPrimaryLight,
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
    )
    Text(
        text = after,
        style = PrismType.caption,
        color = PicoTheme.colorScheme.labelPrimaryLight,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
    )
}
