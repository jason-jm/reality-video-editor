package com.example.prismstudio.p40b43a88.data.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.effect.DefaultGlObjectsProvider
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.SingleInputVideoGraph

/**
 * Stills keep their values, as Resolve keeps them in a Rec.709 timeline. Media3's default SDR working colour space takes a
 * bitmap as sRGB and converts it to video's BT.709 transfer before any effect (sRGB EOTF, then the BT.709 OETF): grey 128
 * arrived as 115, #C8C8C8 rendered as 193, a freeze frame came out darker than the shot it froze. The original working
 * colour space passes SDR input through untouched — stills and footage alike (an SDR video's own transfer was never
 * converted) — while HDR input is still tone-mapped. Every frame processor of the renders, the monitor and freeze frames
 * is made here.
 */
@UnstableApi
object StillColors {

    /** Preview and export decode the same oriented first frame, including from GIF and extensionless content URIs. */
    fun bitmapLoader(context: Context): BitmapLoader {
        val loader = DataSourceBitmapLoader.Builder(context)
            .setBitmapFactoryOptions(BitmapFactory.Options().apply { inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) })
            .setMaximumOutputDimension(4096)
            .build()
        return object : BitmapLoader by loader {
            // The pool already decoded this still at import. Its persisted URI may have no extension or MIME.
            // BitmapFactory reads the actual bytes, including the first frame of an animated GIF.
            override fun supportsMimeType(mimeType: String): Boolean = mimeType == "image/*" || loader.supportsMimeType(mimeType)
        }
    }

    /** A frame processor factory working in each SDR input's own colour space; [replayable] for the monitor's redraws. */
    fun frameProcessors(replayable: Boolean = false): VideoFrameProcessor.Factory =
        DefaultVideoFrameProcessor.Factory.Builder()
            .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
            .setEnableReplayableCache(replayable)
            .setGlObjectsProvider(DefaultGlObjectsProvider())
            .build()

    /** The monitor's video graph: CompositionPlayer's own (one input, replayable cache), working in the original space. */
    fun previewGraph(): SingleInputVideoGraph.Factory = SingleInputVideoGraph.Factory(frameProcessors(replayable = true))
}
