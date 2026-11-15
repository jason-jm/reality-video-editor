package com.example.prismstudio.p40b43a88.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.prismstudio.p40b43a88.domain.model.FramePixels

/** Converts decoded/graded pixels into an image once per pixel buffer. */
@Composable
fun rememberFrameImage(pixels: FramePixels?): ImageBitmap? = remember(pixels) {
    pixels?.takeIf { it.width > 0 && it.height > 0 }?.let {
        Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
