package com.example.prismstudio.p40b43a88.ui.components

import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.FramePixels
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.toneMapped

/** What [asset]'s frames look like in the timeline under [color]: part of a thumbnail's cache key, so a change reads them again. */
fun timelineLookKey(asset: MediaAsset, color: ColorManagement): String =
    if (asset.effectiveTransfer != ColorTransfer.SDR) "${asset.effectiveTransfer}/${color.toneMapping}/${color.pqPeakNits}" else "sdr"

/** Applies the same project tone map to source code values as the timeline monitor. */
fun timelineLook(bitmap: Bitmap, asset: MediaAsset, color: ColorManagement): Bitmap {
    if (asset.effectiveTransfer == ColorTransfer.SDR) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val mapped = FramePixels(w, h, pixels).toneMapped(asset.effectiveTransfer, color)
    return Bitmap.createBitmap(mapped.pixels, w, h, Bitmap.Config.ARGB_8888)
}
